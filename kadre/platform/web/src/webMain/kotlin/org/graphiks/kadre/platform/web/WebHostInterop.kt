package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadrePolicyComponent
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy

/**
 * The `@kadre/host` interop layer, shared by the JS and Wasm targets.
 *
 * Kotlin/Wasm cannot export a class, an object or a class type in a signature, so the module's
 * JavaScript surface is a set of top-level functions over primitives, `String`, function types and
 * `JsAny` values. The promised `KadreWeb` surface is presented by the hand-written `index.mjs` shim
 * that each target's package ships on top of them.
 *
 * This file owns the target-neutral half: the factory and handle registries, the opaque keys and
 * identifiers, the option resolution, and the encoding the shim parses. The facades add only
 * `kadreWebAttach`, whose element parameter needs their SDK type.
 *
 * Encoding contract (internal glue, not the published contract):
 * - a call that can refuse returns `"ok|<value>"` or `"failed|<failure JSON>"`;
 * - snapshots, outcomes and failures are JSON objects whose discriminant is `kind`;
 * - every Kotlin `Long` is encoded as a JSON string, because Kotlin `Long` is an object on JS and a
 *   `BigInt` on Wasm; the shim converts those fields with `BigInt(...)`, so the consumer sees the
 *   `bigint` the promise declares on both targets.
 */

/** Opaque application factory reference for Kotlin applications. Not constructible from JavaScript. */
public class KadreApplicationFactoryRef internal constructor(
    internal val factory: KadreApplicationFactory,
    internal val key: String,
)

/** Wraps a Kotlin-owned factory for the host module. Pure: every call creates a light wrapper. */
public fun KadreApplicationFactory.asHostRef(): KadreApplicationFactoryRef =
    KadreApplicationFactoryRef(this, KadreWebInterop.reserveFactoryKey(this))

/**
 * The opaque key JavaScript holds for this reference.
 *
 * It is an interop-layer token: not the Kotlin factory, and not a `SessionId`. The reference itself
 * never crosses the boundary, because Kotlin/Wasm cannot export a class.
 */
public val KadreApplicationFactoryRef.hostKey: String get() = key

internal object KadreWebInterop {
    private val factories = mutableMapOf<String, KadreApplicationFactory>()
    private val handles = mutableMapOf<Int, KadreWebHandle>()
    private val subscriptions = mutableMapOf<Int, () -> Unit>()
    private var nextFactory = 0
    private var nextHandle = 0
    private var nextSubscription = 0
    private var nextSession = 0

    fun reserveFactoryKey(factory: KadreApplicationFactory): String {
        val key = "kadre-factory-${nextFactory++}"
        factories[key] = factory
        return key
    }

    fun factory(key: String): KadreApplicationFactory? = factories[key]

    fun registerHandle(handle: KadreWebHandle): Int {
        val key = nextHandle++
        handles[key] = handle
        return key
    }

    fun handle(key: Int): KadreWebHandle =
        handles[key] ?: error("no @kadre/host session handle for key $key")

    fun forgetHandle(key: Int): Unit {
        handles.remove(key)
    }

    /**
     * Registers [cancel] under a fresh subscription key, removed when it is unsubscribed.
     *
     * The key doubles as the unsubscribe token JavaScript holds.
     */
    fun subscribe(cancel: () -> Unit): Int {
        val key = nextSubscription++
        subscriptions[key] = cancel
        return key
    }

    /** Runs the registration behind [key] and forgets it. */
    fun unsubscribe(key: Int): Boolean {
        val cancel = subscriptions.remove(key) ?: return false
        cancel()
        return true
    }

    /** Forgets the registration behind [key] without running it. */
    fun forgetSubscription(key: Int): Boolean = subscriptions.remove(key) != null

    /** Attaches the cancellation behind an existing [key], once the registration it cancels exists. */
    fun registerCancellation(key: Int, cancel: () -> Unit) {
        if (subscriptions.containsKey(key)) {
            subscriptions[key] = cancel
        }
    }

    /** An opaque identifier for one attached session; not the Kotlin `SessionId`. */
    fun nextSessionIdentity(): String = "kadre-host-session-${nextSession++}"
}

/**
 * One attached session as the interop layer sees it.
 *
 * The handle owns the `MainScope` of its session: the scope is cancelled once the session publishes
 * its terminal outcome, and the terminal outcome stays available afterwards.
 */
internal class KadreWebHandle internal constructor(
    private val session: KadreSession,
    private val scope: CoroutineScope,
    /** Opaque per-handle identifier allocated by the interop layer. It is not the Kotlin `SessionId`. */
    val id: String,
    private val reportObserverFailure: (Throwable) -> Unit = ::observerFailure,
) {
    private val termination = CompletableDeferred<String>()

    init {
        scope.launch {
            termination.complete(WebInteropJson.encode(session.awaitTermination()))
            // The session is over: release the scope that hosted its observers and waiters.
            scope.cancel()
        }
    }

    /** The current snapshot, in the encoding the shim parses. */
    val state: String get() = WebInteropJson.encode(session.state.value)

    /**
     * Calls [observer] synchronously with the current snapshot, then once per state change.
     *
     * A throwing observer is unsubscribed and its failure is reported out of band; it never
     * terminates the session.
     */
    fun subscribe(observer: (String) -> Unit): Int {
        var delivered = session.state.value
        try {
            observer(WebInteropJson.encode(delivered))
        } catch (error: Throwable) {
            reportObserverFailure(error)
            return KadreWebInterop.subscribe { }
        }
        val job = scope.launch {
            session.state.collect { state ->
                if (state == delivered) return@collect
                delivered = state
                observer(WebInteropJson.encode(state))
            }
        }
        return KadreWebInterop.subscribe { job.cancel() }
    }

    /**
     * Calls [observer] once with the terminal outcome, or as soon as the session terminates.
     *
     * The delivery hangs off the completion of the outcome itself, not off a coroutine of the session
     * scope: the scope is cancelled when the outcome is published, which would cancel a waiter before
     * it could deliver.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun subscribeTermination(observer: (String) -> Unit): Int {
        // `invokeOnCompletion` invokes the handler immediately when the outcome is already published,
        // so a late awaiter is answered synchronously instead of waiting for a cancelled scope.
        val subscription = KadreWebInterop.subscribe { }
        val registration = termination.invokeOnCompletion { cause ->
            if (cause == null) {
                KadreWebInterop.forgetSubscription(subscription)
                observer(termination.getCompleted())
            }
        }
        if (!termination.isCompleted) {
            KadreWebInterop.registerCancellation(subscription) { registration.dispose() }
        }
        return subscription
    }

    fun requestStop(): Unit = session.requestStop()

    fun close(): Unit = session.close()
}

/** Reports a failed observer out of band: it never reaches the session and never cancels the others. */
internal fun observerFailure(error: Throwable): Unit {
    CoroutineScope(Job()).launch { throw error }
}

/**
 * Resolves the options of `KadreWeb.attach` and attaches [element] with a fresh session scope.
 *
 * The body is shared; only the element type and the `attachKadre` overload are target-specific.
 */
internal inline fun <E> attachSession(
    element: E,
    factoryKey: String,
    policy: String,
    attachmentPolicy: String,
    attach: (
        element: E,
        factory: KadreApplicationFactory,
        policy: KadrePolicy,
        attachmentPolicy: WebAttachmentPolicy,
        scope: CoroutineScope,
    ) -> KadreResult<KadreSession>,
): String {
    val factory = KadreWebInterop.factory(factoryKey)
        ?: return refused(KadreFailure.InvalidRequest("factoryKey"))
    val resolvedPolicy = when (policy) {
        "default" -> KadrePolicies.Default
        "realtime" -> KadrePolicies.Realtime
        "recording" -> KadrePolicies.Recording
        // The union is closed: an unknown profile is refused instead of silently replaced.
        else -> return refused(KadreFailure.InvalidRequest("options.policy"))
    }
    val resolvedAttachment = when (attachmentPolicy) {
        "stopWhenDetached" -> WebAttachmentPolicy.StopWhenDetached
        "manual" -> WebAttachmentPolicy.Manual
        else -> return refused(KadreFailure.InvalidRequest("options.attachmentPolicy"))
    }

    val scope = MainScope()
    return when (val attached = attach(element, factory, resolvedPolicy, resolvedAttachment, scope)) {
        is KadreResult.Success -> "ok|" + KadreWebInterop.registerHandle(
            KadreWebHandle(attached.value, scope, KadreWebInterop.nextSessionIdentity()),
        )
        is KadreResult.Failure -> {
            scope.cancel()
            refused(attached.reason)
        }
    }
}

internal fun refused(failure: KadreFailure): String = "failed|" + WebInteropJson.encode(failure)

/** The JSON encoding of the value model, shared by both facades. */
internal object WebInteropJson {
    fun encode(state: SessionState): String = when (state) {
        SessionState.Starting -> """{"kind":"starting"}"""
        SessionState.Running -> """{"kind":"running"}"""
        SessionState.Stopping -> """{"kind":"stopping"}"""
        is SessionState.Terminated -> """{"kind":"terminated","outcome":${encode(state.outcome)}}"""
    }

    fun encode(outcome: SessionOutcome): String = when (outcome) {
        SessionOutcome.Completed -> """{"kind":"completed"}"""
        is SessionOutcome.Stopped -> """{"kind":"stopped","reason":"${outcome.reason.interopName()}"}"""
        is SessionOutcome.Failed -> """{"kind":"failed","failure":${encode(outcome.failure)}}"""
    }

    fun encode(failure: KadreFailure): String = buildString {
        append("""{"kind":"""").append(failure.interopKind()).append('"')
        when (failure) {
            is KadreFailure.Unsupported -> field("operation", failure.operation.interopName())
            is KadreFailure.PermissionDenied -> field("permission", failure.permission.interopName())
            is KadreFailure.UserCancelled -> field("operation", failure.operation.interopName())
            is KadreFailure.TemporarilyUnavailable -> rawField("retryable", failure.retryable.toString())
            is KadreFailure.InvalidRequest -> field("field", failure.field)
            is KadreFailure.AlreadyInUse -> field("resource", failure.resource.interopName())
            is KadreFailure.Closed -> field("resource", failure.resource.interopName())
            is KadreFailure.ResourceLimitExceeded -> {
                field("resource", failure.resource.interopName())
                // Kotlin Long: a JSON string, converted back with BigInt by the shim.
                field("limit", failure.limit.toString())
            }
            is KadreFailure.SourceOverflow -> field("resource", failure.resource.interopName())
            is KadreFailure.StaleRevision -> {
                field("expected", failure.expected.toString())
                field("received", failure.received.toString())
            }
            is KadreFailure.InteractionRequired -> field("reason", failure.reason.interopName())
            is KadreFailure.UnsupportedPolicy -> field("component", failure.component.interopName())
            KadreFailure.ParentScopeCancelled -> Unit
            is KadreFailure.ShutdownTimedOut -> field("timeoutNanoseconds", failure.timeout.inWholeNanoseconds.toString())
            is KadreFailure.SourceLost -> field("sourceId", failure.source.toString())
            KadreFailure.ApplicationFailure -> Unit
            is KadreFailure.PlatformFailure -> {
                field("platform", failure.platform.interopName())
                field("domain", failure.domain)
                field("code", failure.code)
            }
        }
        append('}')
    }

    fun interopKind(failure: KadreFailure): String = when (failure) {
        is KadreFailure.Unsupported -> "unsupported"
        is KadreFailure.PermissionDenied -> "permissionDenied"
        is KadreFailure.UserCancelled -> "userCancelled"
        is KadreFailure.TemporarilyUnavailable -> "temporarilyUnavailable"
        is KadreFailure.InvalidRequest -> "invalidRequest"
        is KadreFailure.AlreadyInUse -> "alreadyInUse"
        is KadreFailure.Closed -> "closed"
        is KadreFailure.ResourceLimitExceeded -> "resourceLimitExceeded"
        is KadreFailure.SourceOverflow -> "sourceOverflow"
        is KadreFailure.StaleRevision -> "staleRevision"
        is KadreFailure.InteractionRequired -> "interactionRequired"
        is KadreFailure.UnsupportedPolicy -> "unsupportedPolicy"
        KadreFailure.ParentScopeCancelled -> "parentScopeCancelled"
        is KadreFailure.ShutdownTimedOut -> "shutdownTimedOut"
        is KadreFailure.SourceLost -> "sourceLost"
        KadreFailure.ApplicationFailure -> "applicationFailure"
        is KadreFailure.PlatformFailure -> "platformFailure"
    }

    private fun KadreFailure.interopKind(): String = interopKind(this)

    /** Appends `,"name":"value"`, or `,"name":null` when the payload is absent. */
    private fun StringBuilder.field(name: String, value: String?): StringBuilder {
        append(",\"").append(name).append("\":")
        if (value == null) append("null") else append('"').append(escape(value)).append('"')
        return this
    }

    /** Appends `,"name":value` for a payload that is already JSON (a boolean or a string). */
    private fun StringBuilder.rawField(name: String, value: String): StringBuilder =
        append(",\"").append(name).append("\":").append(value)

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') append("\\u").append(character.code.toString(16).padStart(4, '0')) else append(character)
            }
        }
    }
}

/** The closed `KadreOperation` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun KadreOperation.interopName(): String = when (this) {
    KadreOperation.HostAttach -> "hostAttach"
    KadreOperation.RequestRedraw -> "requestRedraw"
    KadreOperation.DisplayAccess -> "displayAccess"
    KadreOperation.RequestWindow -> "requestWindow"
    KadreOperation.UpdateWindow -> "updateWindow"
    KadreOperation.RequestWindowAttention -> "requestWindowAttention"
    KadreOperation.CloseWindow -> "closeWindow"
    KadreOperation.RespondToCloseRequest -> "respondToCloseRequest"
    KadreOperation.UpdateSurface -> "updateSurface"
    KadreOperation.InstallInteractionHandler -> "installInteractionHandler"
    KadreOperation.ArmInteraction -> "armInteraction"
    KadreOperation.Interaction -> "interaction"
    KadreOperation.GamepadEffect -> "gamepadEffect"
    KadreOperation.StopGamepadEffects -> "stopGamepadEffects"
    KadreOperation.TextInput -> "textInput"
    KadreOperation.UpdateTextInput -> "updateTextInput"
    KadreOperation.ClaimDropTransfer -> "claimDropTransfer"
    KadreOperation.ReadDropItem -> "readDropItem"
    KadreOperation.CapturePermission -> "capturePermission"
    KadreOperation.CaptureRefreshSources -> "captureRefreshSources"
    KadreOperation.CaptureOpen -> "captureOpen"
    KadreOperation.CaptureCollectFrames -> "captureCollectFrames"
    KadreOperation.RawInputAccess -> "rawInputAccess"
    KadreOperation.GestureInput -> "gestureInput"
    KadreOperation.PlatformSurfaceAccess -> "platformSurfaceAccess"
    KadreOperation.PlatformWindowAccess -> "platformWindowAccess"
}

/** The closed `KadrePermission` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun KadrePermission.interopName(): String = when (this) {
    KadrePermission.DisplayEnumeration -> "displayEnumeration"
    KadrePermission.InputMonitoring -> "inputMonitoring"
    KadrePermission.RawInput -> "rawInput"
    KadrePermission.CaptureScreen -> "captureScreen"
    KadrePermission.CaptureWindow -> "captureWindow"
}

/** The closed `KadrePolicyComponent` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun KadrePolicyComponent.interopName(): String = when (this) {
    KadrePolicyComponent.Execution -> "execution"
    KadrePolicyComponent.LifecycleEvents -> "lifecycleEvents"
    KadrePolicyComponent.HostSignals -> "hostSignals"
    KadrePolicyComponent.WindowEvents -> "windowEvents"
    KadrePolicyComponent.DeviceEvents -> "deviceEvents"
    KadrePolicyComponent.InputEvents -> "inputEvents"
    KadrePolicyComponent.DevicePolicy -> "devicePolicy"
    KadrePolicyComponent.CaptureEvents -> "captureEvents"
    KadrePolicyComponent.CaptureFrames -> "captureFrames"
    KadrePolicyComponent.Diagnostics -> "diagnostics"
    KadrePolicyComponent.Resources -> "resources"
}

/** The closed `KadreResourceKind` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun KadreResourceKind.interopName(): String = when (this) {
    KadreResourceKind.Host -> "host"
    KadreResourceKind.Surface -> "surface"
    KadreResourceKind.Window -> "window"
    KadreResourceKind.WindowRequest -> "windowRequest"
    KadreResourceKind.Display -> "display"
    KadreResourceKind.InputSource -> "inputSource"
    KadreResourceKind.RawInputAccess -> "rawInputAccess"
    KadreResourceKind.InputDevice -> "inputDevice"
    KadreResourceKind.Gamepad -> "gamepad"
    KadreResourceKind.EventCollector -> "eventCollector"
    KadreResourceKind.Interaction -> "interaction"
    KadreResourceKind.DropTransfer -> "dropTransfer"
    KadreResourceKind.DropItem -> "dropItem"
    KadreResourceKind.CursorImage -> "cursorImage"
    KadreResourceKind.GamepadEffect -> "gamepadEffect"
    KadreResourceKind.TextInputSession -> "textInputSession"
    KadreResourceKind.CaptureSource -> "captureSource"
    KadreResourceKind.CaptureSession -> "captureSession"
    KadreResourceKind.CaptureCollector -> "captureCollector"
    KadreResourceKind.CaptureBuffer -> "captureBuffer"
    KadreResourceKind.RetainedPayload -> "retainedPayload"
    KadreResourceKind.ImageResource -> "imageResource"
    KadreResourceKind.EventSequence -> "eventSequence"
}

/** The closed `KadrePlatform` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun KadrePlatform.interopName(): String = when (this) {
    KadrePlatform.Android -> "android"
    KadrePlatform.UIKit -> "uikit"
    KadrePlatform.Web -> "web"
    KadrePlatform.AppKit -> "appKit"
    KadrePlatform.Win32 -> "win32"
    KadrePlatform.X11 -> "x11"
    KadrePlatform.Wayland -> "wayland"
    KadrePlatform.Fake -> "fake"
}

/** The closed `InteractionFailureReason` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun InteractionFailureReason.interopName(): String = when (this) {
    InteractionFailureReason.Missing -> "missing"
    InteractionFailureReason.Expired -> "expired"
    InteractionFailureReason.Consumed -> "consumed"
    InteractionFailureReason.WrongSurface -> "wrongSurface"
}

/** The closed `KadreStopReason` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun SessionStopReason.interopName(): String = when (this) {
    SessionStopReason.HostRequested -> "hostRequested"
    SessionStopReason.ApplicationRequested -> "applicationRequested"
    SessionStopReason.ApplicationCancelled -> "applicationCancelled"
    SessionStopReason.ParentCancelled -> "parentCancelled"
    SessionStopReason.HostDetached -> "hostDetached"
}
