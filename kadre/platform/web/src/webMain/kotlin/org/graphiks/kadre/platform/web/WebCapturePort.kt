package org.graphiks.kadre.platform.web

import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.internal.runtime.CapturePort
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock

/** The failure domain of the capability probe's picker refusals, carrying the documented causes. */
private const val CAPABILITY_DOMAIN = "capture-capability"

/** The failure domain the plan's decision 4 names for every consent-flow error that is not one of its table rows. */
private const val PERMISSION_DOMAIN = "capture-permission"

/**
 * The web capture control plane: one honest snapshot of what this browsing context can promise
 * about capture, probed once here and never improved by a guess.
 *
 * **The probe is presence-based and runs once, at construction.** Secure context, `getDisplayMedia`,
 * the `MediaStreamTrackProcessor` constructor and the attach element's canvas kind are read through
 * the seam and frozen into the capabilities ([WebCaptureDom.canvasForSurface] is asked by the
 * session that wires this port — the boolean is that probe's frozen answer). The permission
 * readback is the one construction fact the browser answers asynchronously: the synchronous
 * [WebCaptureDom.queryDisplayCapturePermission] answers what has settled, and the port registers
 * [WebCaptureDom.readDisplayCapturePermission] at construction, so the settled answer republishes
 * the snapshot through the observer — the decision-3 readback surfaces, at the cost of a transient
 * `Unavailable(Unsupported(CapturePermission))` initial state that the settled answer corrects
 * (the browser's query is promise-based; there is no synchronous readback to be had).
 *
 * **Sources are always [CapturePortSources.HostPickerOnly]** (decision 1): no browser enumerates
 * capturable screens or windows before a consent, so `refreshSources` is an honest no-op — it
 * returns the current snapshot object untouched and never reaches the consent machinery. The
 * mutation the contract kills is a prompt during readback: one seam pick outside the explicit
 * request path is a test failure.
 *
 * **`requestPermission` is the one explicit consent path, and both scopes run the same flow**
 * (decision 4's mirrored consent): one `getDisplayMedia`, the picked stream stopped and released
 * immediately — the consent verdict is what the port keeps, never the stream. Picked → `Granted`
 * on both scopes; `NotAllowedError`/`AbortError` → `Denied(canRequestAgain = true)`;
 * `NotFoundError` → the retryable temporary unavailability of a browser with no source to offer;
 * anything else → `PlatformFailure` in the `capture-permission` domain. The failure rows return a
 * failure rather than a snapshot: the runtime's own check forbids a "successful" permission request
 * whose targeted permission is unresolved, and the manager publishes the persistent rows itself.
 *
 * **`reserve` refuses `Source` structurally** (decision 2) — with zero seam interaction, before
 * anything browser-facing — and answers `HostChoice`/`Surface` with declared `Unsupported` stubs:
 * Tasks 3 and 4 replace those two rows with the real reservations; the `Source` row is permanent.
 *
 * The port is session-scoped: the runtime installs exactly one observer and closes the port with
 * the session components, which closes the seam with it.
 */
internal class WebCapturePort(
    private val dom: WebCaptureDom,
    private val primarySurfaceElementIsCanvas: Boolean,
) : CapturePort {
    private val lock = RuntimeLock()
    private var closed = false
    private var observer: ((KadreResult<CapturePortSnapshot>) -> Unit)? = null

    /** The capability truth table of decision 5, probed once — presence-based, never guessed. */
    private val probedCapabilities: CaptureCapabilities = probeCapabilities()

    override val initialSnapshot: CapturePortSnapshot =
        snapshotFor(permissionState(dom.queryDisplayCapturePermission()))

    /** The snapshot the port currently stands behind; every later fact replaces it whole. */
    private var current: CapturePortSnapshot = initialSnapshot

    init {
        // The readback is the one construction fact that settles late; the settled answer republishes.
        dom.readDisplayCapturePermission(::acceptReadback)
    }

    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot> {
        if (isClosed()) return closedFailure()
        // Mirrored consent (decision 4): the scope changes nothing — the browser conflates consent
        // with picking, so both scopes run this one flow and mirror its verdict. The discard flow
        // states no hint: the stream is dropped immediately, so the picker's preview words are
        // nobody's promise here (the reserve path of Task 3 is where a request's hints travel).
        return when (val pick = dom.pickDisplayMedia(cursorHint = null, frameRateHint = null)) {
            is WebDisplayMediaPick.Picked -> {
                discard(pick.track)
                val snapshot = snapshotFor(PermissionState.Granted)
                accept(snapshot)
                KadreResult.Success(snapshot)
            }

            is WebDisplayMediaPick.Refused -> when (pick.code) {
                "NotAllowedError", "AbortError" -> {
                    val snapshot = snapshotFor(PermissionState.Denied(canRequestAgain = true))
                    accept(snapshot)
                    KadreResult.Success(snapshot)
                }

                "NotFoundError" ->
                    KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))

                else ->
                    KadreResult.Failure(
                        KadreFailure.PlatformFailure(KadrePlatform.Web, PERMISSION_DOMAIN, pick.code),
                    )
            }
        }
    }

    override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> {
        if (isClosed()) return closedFailure()
        // Decision 1's honest no-op: the same snapshot object, readback and capabilities included,
        // and not one seam call — a browser cannot enumerate what it has not been consented for.
        return KadreResult.Success(lock.withLock { current })
    }

    override suspend fun reserve(
        target: CapturePortTarget,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        if (isClosed()) return closedFailure()
        return when (target) {
            // Decision 2: the web has no pre-consent inventory, so a source target is refused
            // structurally — here, before any picker — with nothing reserved and nothing asked.
            is CapturePortTarget.Source -> KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))

            // Declared stubs. Task 3 routes HostChoice through the picker and the frame pump; Task 4
            // routes Surface through the canvas stream. Both replace the refusal; neither exists yet.
            CapturePortTarget.HostChoice -> KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
            is CapturePortTarget.Surface -> KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
    }

    override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable {
        val movedPastInitial = lock.withLock {
            check(!closed) { "the web capture port is closed" }
            check(this.observer == null) { "the web capture port observer is already installed" }
            this.observer = observer
            // An install over a port that already moved past its initial snapshot (a readback that
            // settled before anyone listened) delivers that current answer once — the observer sees
            // the truth from the moment it exists. A port still at its initial snapshot delivers
            // nothing: the runtime already holds exactly that from the construction read.
            if (current !== initialSnapshot) current else null
        }
        movedPastInitial?.let { observer(KadreResult.Success(it)) }
        var withdrawn = false
        return AutoCloseable {
            lock.withLock {
                if (withdrawn) return@withLock
                withdrawn = true
                if (this.observer === observer) this.observer = null
            }
        }
    }

    override fun close() {
        val closeSeam = lock.withLock {
            if (closed) {
                false
            } else {
                closed = true
                observer = null
                true
            }
        }
        if (closeSeam) dom.close()
    }

    /** Accepts the settled readback and republishes it when it moved the port's permissions. */
    private fun acceptReadback(answer: WebCapturePermissionQueryResult?) {
        val replacement = permissionState(answer)
        val unchanged = lock.withLock { closed || replacement == current.permissions }
        if (!unchanged) snapshotFor(replacement).also(::accept)
    }

    /** Replaces the current snapshot and hands it to the installed observer, if any and still open. */
    private fun accept(snapshot: CapturePortSnapshot) {
        val installed = lock.withLock {
            if (closed) return
            current = snapshot
            observer
        }
        installed?.invoke(KadreResult.Success(snapshot))
    }

    /** The decision-5 gate chain: secure context, then the picker, then the pump, then the canvas. */
    private fun probeCapabilities(): CaptureCapabilities {
        val secure = dom.isSecureContext()
        val displayMedia = dom.hasDisplayMedia()
        val processor = dom.processorFactory()
        val screenAndWindow = when {
            !secure -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
            !displayMedia -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
            processor == null -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
            else -> Capability.Supported(
                CaptureTargetConstraints(
                    formats = setOf(PixelFormat.Rgba8, PixelFormat.I420, PixelFormat.Nv12),
                    cursorModes = setOf(
                        CaptureCursorMode.Hidden,
                        CaptureCursorMode.Embedded,
                        CaptureCursorMode.EmbeddedWhenAvailable,
                    ),
                    // Decision 8, in the AppKit refusal form: the browser picks its own bounds, so
                    // region on a screen/window target is not advertised at all.
                    region = FeatureAvailability.Unsupported,
                ),
                FeatureAvailability.Available,
            )
        }
        val surface = when {
            !secure -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
            !displayMedia -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
            processor == null -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
            !primarySurfaceElementIsCanvas ->
                Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))

            else -> Capability.Supported(
                CaptureTargetConstraints(
                    formats = setOf(PixelFormat.Rgba8),
                    cursorModes = setOf(CaptureCursorMode.Hidden),
                    // The canvas surface is the one web target a region can honestly crop (the
                    // visibleRect path decision 8 stages), so its region availability is real.
                    region = FeatureAvailability.Available,
                ),
                FeatureAvailability.Available,
            )
        }
        return CaptureCapabilities(
            screen = screenAndWindow,
            // Decision 5's documented limit: one browser consent governs whatever its picker offers,
            // so the window capability mirrors the screen capability — the same constraints, word
            // for word, with no distinct window guarantee behind them.
            window = screenAndWindow,
            surface = surface,
            // Decision 1, unconditional: the inventory is the host picker, and the enumeration
            // capability says so whether or not any other primitive exists.
            sourceEnumeration = Capability.Supported(Unit, FeatureAvailability.Available),
            hostPicker = when {
                !secure -> FeatureAvailability.Unavailable(
                    KadreFailure.PlatformFailure(KadrePlatform.Web, CAPABILITY_DOMAIN, "secure-context"),
                )

                !displayMedia -> FeatureAvailability.Unavailable(
                    KadreFailure.PlatformFailure(KadrePlatform.Web, CAPABILITY_DOMAIN, "no-get-display-media"),
                )

                else -> FeatureAvailability.Available
            },
        )
    }

    /** The discard half of the consent flow: the browser effect once, the handle release once. */
    private fun discard(track: WebDomVideoTrack) {
        track.stop()
        track.close()
    }

    /** One complete snapshot with [permissions] mirrored across both scopes (decision 3's limit). */
    private fun snapshotFor(permissions: PermissionState): CapturePortSnapshot = CapturePortSnapshot(
        permissions = CapturePermissionState(permissions, permissions),
        capabilities = probedCapabilities,
        sources = CapturePortSources.HostPickerOnly,
    )

    private fun isClosed(): Boolean = lock.withLock { closed }

    private fun closedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
}

/**
 * The decision-3 mapping: the seam's readback answer becomes the permission state both scopes
 * mirror. A `null` answer — the API absent, the name unknown, the query rejected — is exactly the
 * `Unavailable(Unsupported(CapturePermission))` the decision prescribes.
 */
private fun permissionState(answer: WebCapturePermissionQueryResult?): PermissionState = when (answer) {
    WebCapturePermissionQueryResult.Granted -> PermissionState.Granted
    WebCapturePermissionQueryResult.Denied -> PermissionState.Denied(canRequestAgain = true)
    WebCapturePermissionQueryResult.NotDetermined -> PermissionState.NotDetermined
    null -> PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission))
}
