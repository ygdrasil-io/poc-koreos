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
    /**
     * The key of one factory, by factory identity: repeated `asHostRef()` calls on the same factory
     * share it, so the table does not grow per wrapper.
     */
    private val factoryKeys = mutableMapOf<KadreApplicationFactory, String>()
    private val factories = mutableMapOf<String, KadreApplicationFactory>()

    /** Live and released sessions by handle key. A released entry holds no session and no scope. */
    private val sessions = mutableMapOf<Int, KadreWebSession>()

    private val registrations = mutableMapOf<Int, Registration>()
    private var nextFactory = 0
    private var nextHandle = 0
    private var nextRegistration = 0
    private var nextSession = 0

    fun reserveFactoryKey(factory: KadreApplicationFactory): String {
        factoryKeys[factory]?.let { return it }
        val key = "kadre-factory-${nextFactory++}"
        factoryKeys[factory] = key
        factories[key] = factory
        return key
    }

    fun factory(key: String): KadreApplicationFactory? = factories[key]

    /** Reserves the key of a session before its handle exists, so the handle can name itself. */
    fun reserveHandleKey(): Int = nextHandle++

    fun registerHandle(key: Int, session: KadreWebSession) {
        sessions[key] = session
    }

    fun session(key: Int): KadreWebSession =
        sessions[key] ?: error("no @kadre/host session for handle key $key")

    /**
     * Replaces the handle behind [key] with its released record and forgets its subscriptions.
     *
     * The session, its scope and everything they retain (the attached element included) become
     * unreachable here; the registry keeps only what `state`, `id` and the terminal deliveries need.
     * The terminal deliveries have already happened by the time this runs, so the registrations that
     * were still waiting on that handle go with it: a subscription the consumer never unsubscribed
     * would otherwise keep its observer closure — and everything the closure captured — reachable for
     * the lifetime of the page, since the registration table is keyed by a counter that never repeats
     * a key and nothing else would ever remove those entries.
     */
    fun releaseHandle(key: Int, released: KadreWebSession) {
        sessions[key] = released
        val releasedRegistrations = registrations.entries
            .filter { (_, registration) -> registration.handleKey == key }
            .map { (registrationKey, _) -> registrationKey }
        releasedRegistrations.forEach { registrations.remove(it) }
    }

    /** How many subscriptions are still registered for [handleKey]. Internal: for the tests. */
    internal fun registrationCount(handleKey: Int): Int =
        registrations.values.count { it.handleKey == handleKey }

    /** Whether the handle behind [key] is still retained as a live session. Internal: for the tests. */
    internal fun isLiveHandle(key: Int): Boolean = sessions[key] is KadreWebHandle

    /**
     * Registers [observer] for the state of the session behind [handleKey].
     *
     * The observer hears [current] synchronously; a throwing observer is not registered. The first
     * call is the only synchronous one, like the promise of `subscribeState`.
     */
    fun subscribeState(
        handleKey: Int,
        current: String,
        observer: (String) -> Unit,
        report: (Throwable) -> Unit = ::observerFailure,
    ): Int {
        val key = nextRegistration++
        try {
            observer(current)
        } catch (error: Throwable) {
            report(error)
            return key
        }
        registrations[key] = StateRegistration(handleKey, observer, current, report)
        return key
    }

    /** Calls [observer] once with the terminal outcome of the session behind [handleKey]. */
    fun subscribeTermination(handleKey: Int, observer: (String) -> Unit): Int {
        val key = nextRegistration++
        registrations[key] = TerminationRegistration(handleKey, observer)
        return key
    }

    /**
     * Delivers [json] to every state observer of [handleKey] that has not heard it yet.
     *
     * The terminal snapshot travels this path, so it is delivered even though the collector that
     * watches the state flow dies with the session scope.
     */
    fun notifyState(handleKey: Int, json: String) {
        // The pending registrations are read out first: the loop removes the ones whose observer
        // threw, and a map view of a changing map must not be inspected.
        val pending = registrations.entries
            .filter { (_, registration) ->
                registration is StateRegistration &&
                    registration.handleKey == handleKey &&
                    registration.delivered != json
            }
            .map { (key, registration) -> key to (registration as StateRegistration) }
        pending.forEach { (key, state) ->
            state.delivered = json
            try {
                state.observer(json)
            } catch (error: Throwable) {
                registrations.remove(key)
                state.report(error)
            }
        }
    }

    /** Delivers [json] to every termination observer of [handleKey], and forgets them. */
    fun notifyTermination(handleKey: Int, json: String) {
        // The pending registrations are read out first: each one is removed as it is delivered.
        val pending = registrations.entries
            .filter { (_, registration) ->
                registration is TerminationRegistration && registration.handleKey == handleKey
            }
            .map { (key, registration) -> key to (registration as TerminationRegistration) }
        pending.forEach { (key, termination) ->
            registrations.remove(key)
            try {
                termination.observer(json)
            } catch (error: Throwable) {
                observerFailure(error)
            }
        }
    }

    /** Forgets the registration behind [key]. Returns whether it was still registered. */
    fun unsubscribe(key: Int): Boolean = registrations.remove(key) != null

    /** An opaque identifier for one attached session; not the Kotlin `SessionId`. */
    fun nextSessionIdentity(): String = "kadre-host-session-${nextSession++}"

    internal sealed interface Registration {
        val handleKey: Int
    }

    internal class StateRegistration(
        override val handleKey: Int,
        val observer: (String) -> Unit,
        /** The last snapshot this observer heard, so a change is never delivered twice. */
        var delivered: String,
        val report: (Throwable) -> Unit,
    ) : Registration

    internal class TerminationRegistration(
        override val handleKey: Int,
        val observer: (String) -> Unit,
    ) : Registration
}

/**
 * One attached session as the interop layer sees it: the live handle, or the released record that
 * answers for it once the session has terminated.
 */
internal interface KadreWebSession {
    /** Opaque per-handle identifier allocated by the interop layer. It is not the Kotlin `SessionId`. */
    val id: String

    /** The current snapshot, in the encoding the shim parses. */
    val state: String

    /** Registers a state observer, which hears the current snapshot synchronously. */
    fun subscribe(observer: (String) -> Unit): Int

    /** Registers a one-shot terminal-outcome observer. */
    fun subscribeTermination(observer: (String) -> Unit): Int

    fun requestStop(): Unit

    fun close(): Unit
}

/**
 * The live handle over one attached session.
 *
 * The handle owns the `MainScope` of its session. When the session terminates, the terminal snapshot
 * is delivered to the state observers from this path, the outcome is kept for later
 * `subscribeTermination` calls, the registry entry is replaced by a [ReleasedKadreWebSession] and the
 * scope is cancelled.
 */
internal class KadreWebHandle internal constructor(
    private val key: Int,
    private val session: KadreSession,
    private val scope: CoroutineScope,
    override val id: String,
    private val reportObserverFailure: (Throwable) -> Unit = ::observerFailure,
) : KadreWebSession {
    private val termination = CompletableDeferred<String>()

    init {
        scope.launch {
            // One collector for the whole handle: every state subscription is fed from here.
            session.state.collect { state -> KadreWebInterop.notifyState(key, WebInteropJson.encode(state)) }
        }
        scope.launch { terminateSession() }
    }

    /**
     * Publishes the terminal snapshot, then releases the session.
     *
     * The delivery does not go through the state collector: the collector runs in the scope that this
     * very block cancels, so a collector-driven delivery of the terminal snapshot would race with the
     * cancellation and a consumer waiting for `terminated` could hang.
     */
    private suspend fun terminateSession() {
        val outcome = WebInteropJson.encode(session.awaitTermination())
        val terminal = """{"kind":"terminated","outcome":$outcome}"""
        KadreWebInterop.notifyState(key, terminal)
        termination.complete(outcome)
        KadreWebInterop.notifyTermination(key, outcome)
        KadreWebInterop.releaseHandle(key, ReleasedKadreWebSession(key, id, terminal, outcome))
        scope.cancel()
    }

    override val state: String get() = WebInteropJson.encode(session.state.value)

    override fun subscribe(observer: (String) -> Unit): Int =
        KadreWebInterop.subscribeState(key, state, observer, reportObserverFailure)

    // `getCompleted()` reads the terminal outcome that is cached for exactly this purpose: a late
    // awaiter is answered synchronously instead of waiting for a session that is already over.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun subscribeTermination(observer: (String) -> Unit): Int {
        val subscription = KadreWebInterop.subscribeTermination(key, observer)
        // A late awaiter is answered from the outcome itself, immediately when it is already known.
        val outcome = termination.takeIf { it.isCompleted }
        if (outcome != null) {
            KadreWebInterop.notifyTermination(key, outcome.getCompleted())
        }
        return subscription
    }

    override fun requestStop(): Unit = session.requestStop()

    override fun close(): Unit = session.close()
}

/**
 * What the registry keeps for a terminated session: the identifier and the terminal snapshot, so
 * `id`, `state` and a late `subscribeState`/`awaitTermination` keep answering, and nothing else.
 */
internal class ReleasedKadreWebSession(
    private val key: Int,
    override val id: String,
    private val terminalState: String,
    private val terminalOutcome: String,
) : KadreWebSession {
    override val state: String get() = terminalState

    override fun subscribe(observer: (String) -> Unit): Int =
        KadreWebInterop.subscribeState(key, terminalState, observer)

    // `getCompleted()` reads the terminal outcome that is cached for exactly this purpose: a late
    // awaiter is answered synchronously instead of waiting for a session that is already over.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun subscribeTermination(observer: (String) -> Unit): Int {
        val subscription = KadreWebInterop.subscribeTermination(key, observer)
        KadreWebInterop.notifyTermination(key, terminalOutcome)
        return subscription
    }

    override fun requestStop(): Unit = Unit

    override fun close(): Unit = Unit
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
        is KadreResult.Success -> {
            // The handle needs its own key, because it releases itself on the terminal path.
            val key = KadreWebInterop.reserveHandleKey()
            KadreWebInterop.registerHandle(
                key,
                KadreWebHandle(key, attached.value, scope, KadreWebInterop.nextSessionIdentity()),
            )
            "ok|" + key
        }
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
