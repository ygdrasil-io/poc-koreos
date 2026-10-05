package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.math.roundToInt

/** One measurement of the browsing context's viewport, in the units the DOM reports it in. */
internal data class WebViewportMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val devicePixelRatio: Double,
    val colorDepth: Int,
)

/**
 * Seam over the browsing context's viewport. Test doubles implement this; each target has one real
 * realization.
 *
 * The realization owns the DOM facts — `innerWidth`/`innerHeight`/`devicePixelRatio`/`colorDepth` —
 * and nothing else: it filters what cannot be a measurement (a dpr that is non-finite or not
 * positive, sizes that are not) into the `null` of [current], and it answers [observe] with the
 * moments any of those facts may have changed. It never interprets them; that is the port's work.
 */
internal interface WebDisplaySource : AutoCloseable {
    /** Latest metrics, or null while the browsing context exposes none (not yet laid out, invalid dpr). */
    fun current(): WebViewportMetrics?

    /** Registers [listener] for every moment the metrics may have changed. */
    fun observe(listener: () -> Unit): AutoCloseable
}

/**
 * The per-target realization of [WebDisplaySource] over the session's own browsing context.
 *
 * Web code cannot name a DOM type — this seam is structural — so the one function each target
 * actualizes is how the shared session obtains the viewport of the window it runs in.
 */
internal expect fun hostDisplaySource(): WebDisplaySource

/**
 * The mandatory display inventory of the web platform, read from the browsing context's viewport.
 *
 * A browser cannot enumerate the displays behind its window, and the gate the roadmap states admits
 * no improvisation: absent a real enumeration, the inventory is EXACTLY the primary viewport
 * published as one `HostViewport` display — never an empty inventory, never a generic
 * "unavailable". So this port has no enumeration of its own to perform: it measures the viewport
 * ([WebDisplaySource]) and dresses the measurement in the exact snapshot shape the gate mandates,
 * with the physical bounds the CSS viewport scaled by the dpr and rounded.
 *
 * A measurement it cannot trust is a failure, not a guess: `TemporarilyUnavailable(retryable)`,
 * both from [requestSnapshot] and through the observer — the runtime's reconciliation withdraws a
 * failed inventory, which is the honest state of a viewport nobody can measure right now, and never
 * the lie of a partial or empty one. The source realizations filter the unmeasurable into `null`
 * already; the port still defends the same way against whatever reaches it.
 *
 * The port is session-scoped: the runtime installs exactly one observer and closes the port with the
 * session, which unsubscribes from the source and closes the source it owns.
 *
 * @param source the viewport seam this port measures; owned by the port and closed with it.
 */
internal class WebDisplayPort(private val source: WebDisplaySource) : DisplayPort {
    private val lock = RuntimeLock()
    private var closed: Boolean = false
    private var observer: ((KadreResult<DisplayPortSnapshot>) -> Unit)? = null
    private var subscription: AutoCloseable? = null

    override val enumerationCapability: Capability<Unit> =
        Capability.Supported(Unit, FeatureAvailability.Available)

    override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> = snapshotResult()

    /**
     * Installs the session-runtime observer and re-emits the current answer on every source fire.
     *
     * One observer at a time: the runtime installs exactly one, and a second install replaces the
     * first — the port withdraws the source subscription it had created for it and subscribes anew.
     * Nothing else is closed silently: the `AutoCloseable` of a replaced install stays callable and
     * merely becomes a no-op, and a close of a stale handle never unsubscribes the install that
     * replaced it. The install itself publishes nothing; the runtime requests its initial inventory
     * through [requestSnapshot], and the fires of the source drive everything after.
     */
    override fun installSnapshotObserver(
        observer: (KadreResult<DisplayPortSnapshot>) -> Unit,
    ): AutoCloseable {
        var replaced: AutoCloseable? = null
        val registration = lock.withLock {
            check(!closed) { "the web display port is closed" }
            replaced = subscription
            this.observer = observer
            source.observe { emitCurrent() }.also { subscription = it }
        }
        replaced?.close()
        var withdrawn = false
        return AutoCloseable {
            val current = lock.withLock {
                if (withdrawn) return@withLock null
                withdrawn = true
                if (subscription === registration) {
                    subscription = null
                    this.observer = null
                }
                registration
            }
            current?.close()
        }
    }

    /**
     * Unsubscribes from the source, closes the source this port owns, and forgets the observer.
     *
     * Idempotent: every later call, like every source fire after the first, publishes nothing. The
     * runtime closes the port with the session's components, so the DOM listeners of the real
     * sources die with the session that installed them.
     */
    override fun close() {
        val registration = lock.withLock {
            if (closed) return
            closed = true
            val registration = subscription
            subscription = null
            observer = null
            registration
        }
        registration?.close()
        source.close()
    }

    /** Delivers the current answer to the installed observer, whatever the viewport measures now. */
    private fun emitCurrent() {
        val installed = lock.withLock { observer } ?: return
        installed(snapshotResult())
    }

    private fun snapshotResult(): KadreResult<DisplayPortSnapshot> {
        val metrics = measuredMetrics()
            ?: return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
        return KadreResult.Success(snapshotFor(metrics))
    }

    /** The source's measurement, re-filtered: the port never trusts what its source should have. */
    private fun measuredMetrics(): WebViewportMetrics? {
        val metrics = source.current() ?: return null
        val dpr = metrics.devicePixelRatio
        val measurable = metrics.widthPx > 0 && metrics.heightPx > 0 && dpr.isFinite() && dpr > 0.0
        return if (measurable) metrics else null
    }

    private fun snapshotFor(metrics: WebViewportMetrics): DisplayPortSnapshot {
        val physicalWidth = (metrics.widthPx * metrics.devicePixelRatio).roundToInt()
        val physicalHeight = (metrics.heightPx * metrics.devicePixelRatio).roundToInt()
        val bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(physicalWidth, physicalHeight))
        return DisplayPortSnapshot(
            primaryKey = 0L,
            displays = listOf(
                DisplayPortDisplay(
                    key = 0L,
                    type = DisplayType.HostViewport,
                    name = null,
                    bounds = bounds,
                    workArea = bounds,
                    scaleFactor = metrics.devicePixelRatio,
                    currentModeKey = 0L,
                    modes = listOf(
                        DisplayPortMode(
                            key = 0L,
                            physicalSize = bounds.size,
                            refreshRateHz = null,
                            bitDepth = metrics.colorDepth,
                        ),
                    ),
                ),
            ),
        )
    }
}
