package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure as KadreFailureValue
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadrePolicyComponent
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.input.KadrePermission

/**
 * Target-neutral half of the `@kadre/host` module: the value model and the closed name mappings of
 * `kadre/INTEROP-EXPORTS.md` section 6.
 *
 * Kotlin enums become the lower-camel-case string unions of that section, and failures become a
 * discriminated union keyed by `kind`. The JS and Wasm facades add only the pieces that need their
 * SDK's `HTMLElement` or their own promise type; both compile this model.
 *
 * Nothing here is annotated for JavaScript: Kotlin/Wasm only exports functions, so the model lives
 * in the shared source set and each facade decides what its target can publish.
 */

/**
 * The options accepted by `KadreWeb.attach`, as a JavaScript object literal.
 *
 * Both members are optional and read as the string unions of `kadre/INTEROP-EXPORTS.md` section 6.
 * `windowProvider` is deliberately absent: it is added by the phase that delivers
 * `WebWindowProvider`, so no published option is silently ignored.
 */
public external interface KadreWebOptions {
    public val policy: String?
    public val attachmentPolicy: String?
}

/** Opaque application factory reference handed to JavaScript. Not constructible from JavaScript. */
public class KadreApplicationFactoryRef internal constructor(internal val factory: KadreApplicationFactory)

/** Wraps a Kotlin-owned factory for the host module. Pure: every call creates a light wrapper. */
public fun KadreApplicationFactory.asHostRef(): KadreApplicationFactoryRef = KadreApplicationFactoryRef(this)

/**
 * The closed failure value model of `kadre/INTEROP-EXPORTS.md` section 6, keyed by [kind].
 *
 * Only the members that belong to [kind] are meaningful; the others are `null`. This carrier is what
 * makes the TypeScript discriminated union readable without leaking a Kotlin type.
 */
public class KadreFailure internal constructor(
    public val kind: String,
    public val operation: String? = null,
    public val permission: String? = null,
    public val retryable: Boolean? = null,
    public val field: String? = null,
    public val resource: String? = null,
    public val limit: Long? = null,
    public val expected: Long? = null,
    public val received: Long? = null,
    public val reason: String? = null,
    public val component: String? = null,
    public val timeoutNanoseconds: Long? = null,
    public val sourceId: String? = null,
    public val platform: String? = null,
    public val domain: String? = null,
    public val code: String? = null,
)

/** Terminal session outcome, keyed by [kind]: `completed`, `stopped` or `failed`. */
public class KadreSessionOutcome internal constructor(
    public val kind: String,
    public val reason: String? = null,
    public val failure: KadreFailure? = null,
)

/** The session snapshot published by `KadreSessionHandle.state`. */
public class KadreSessionSnapshot internal constructor(
    public val kind: String,
    public val outcome: KadreSessionOutcome? = null,
)

/**
 * Reports a failed observer out of band.
 *
 * The failure is rethrown on its own scope, so it never reaches the session and never cancels the
 * remaining observers.
 */
internal fun observerFailure(error: Throwable): Unit {
    CoroutineScope(Job()).launch { throw error }
}

internal fun SessionState.toInterop(): KadreSessionSnapshot = when (this) {
    SessionState.Starting -> KadreSessionSnapshot("starting")
    SessionState.Running -> KadreSessionSnapshot("running")
    SessionState.Stopping -> KadreSessionSnapshot("stopping")
    is SessionState.Terminated -> KadreSessionSnapshot("terminated", outcome.toInterop())
}

internal fun SessionOutcome.toInterop(): KadreSessionOutcome = when (this) {
    SessionOutcome.Completed -> KadreSessionOutcome("completed")
    is SessionOutcome.Stopped -> KadreSessionOutcome("stopped", reason.interopName())
    is SessionOutcome.Failed -> KadreSessionOutcome("failed", failure = failure.toInterop())
}

internal fun KadreFailureValue.toInterop(): KadreFailure = when (this) {
    is KadreFailureValue.Unsupported -> KadreFailure(kind = "unsupported", operation = operation.interopName())
    is KadreFailureValue.PermissionDenied -> KadreFailure(kind = "permissionDenied", permission = permission.interopName())
    is KadreFailureValue.UserCancelled -> KadreFailure(kind = "userCancelled", operation = operation.interopName())
    is KadreFailureValue.TemporarilyUnavailable -> KadreFailure(kind = "temporarilyUnavailable", retryable = retryable)
    is KadreFailureValue.InvalidRequest -> KadreFailure(kind = "invalidRequest", field = field)
    is KadreFailureValue.AlreadyInUse -> KadreFailure(kind = "alreadyInUse", resource = resource.interopName())
    is KadreFailureValue.Closed -> KadreFailure(kind = "closed", resource = resource.interopName())
    is KadreFailureValue.ResourceLimitExceeded ->
        KadreFailure(kind = "resourceLimitExceeded", resource = resource.interopName(), limit = limit)
    is KadreFailureValue.SourceOverflow -> KadreFailure(kind = "sourceOverflow", resource = resource.interopName())
    is KadreFailureValue.StaleRevision -> KadreFailure(kind = "staleRevision", expected = expected, received = received)
    is KadreFailureValue.InteractionRequired -> KadreFailure(kind = "interactionRequired", reason = reason.interopName())
    is KadreFailureValue.UnsupportedPolicy -> KadreFailure(kind = "unsupportedPolicy", component = component.interopName())
    KadreFailureValue.ParentScopeCancelled -> KadreFailure(kind = "parentScopeCancelled")
    is KadreFailureValue.ShutdownTimedOut -> KadreFailure(kind = "shutdownTimedOut", timeoutNanoseconds = timeout.inWholeNanoseconds)
    is KadreFailureValue.SourceLost -> KadreFailure(kind = "sourceLost", sourceId = source.toString())
    KadreFailureValue.ApplicationFailure -> KadreFailure(kind = "applicationFailure")
    is KadreFailureValue.PlatformFailure ->
        KadreFailure(
            kind = "platformFailure",
            platform = platform.interopName(),
            domain = domain,
            code = code,
        )
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
