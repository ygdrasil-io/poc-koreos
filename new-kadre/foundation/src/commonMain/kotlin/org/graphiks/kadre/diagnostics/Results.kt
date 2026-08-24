package org.graphiks.kadre.diagnostics

import org.graphiks.kadre.capture.CaptureSourceId
import org.graphiks.kadre.input.KadrePermission
import kotlin.time.Duration

public sealed interface KadreResult<out T> {
    public data class Success<T>(public val value: T) : KadreResult<T>
    public data class Failure(public val reason: KadreFailure) : KadreResult<Nothing>
}

public val KadreResult<*>.isSuccess: Boolean
    get() = this is KadreResult.Success

public val KadreResult<*>.isFailure: Boolean
    get() = this is KadreResult.Failure

public fun <T> KadreResult<T>.getOrNull(): T? = when (this) {
    is KadreResult.Success -> value
    is KadreResult.Failure -> null
}

public fun KadreResult<*>.failureOrNull(): KadreFailure? = when (this) {
    is KadreResult.Success -> null
    is KadreResult.Failure -> reason
}

public fun <T, R> KadreResult<T>.map(transform: (T) -> R): KadreResult<R> = when (this) {
    is KadreResult.Success -> KadreResult.Success(transform(value))
    is KadreResult.Failure -> this
}

public fun <T, R> KadreResult<T>.flatMap(transform: (T) -> KadreResult<R>): KadreResult<R> = when (this) {
    is KadreResult.Success -> transform(value)
    is KadreResult.Failure -> this
}

public fun <T, R> KadreResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (KadreFailure) -> R,
): R = when (this) {
    is KadreResult.Success -> onSuccess(value)
    is KadreResult.Failure -> onFailure(reason)
}

public fun <T> KadreResult<T>.getOrThrow(): T = when (this) {
    is KadreResult.Success -> value
    is KadreResult.Failure -> throw KadreException(reason)
}

public class KadreException(public val failure: KadreFailure) : RuntimeException(failure.message)

public sealed interface KadreFailure {
    public data class Unsupported(public val operation: KadreOperation) : KadreFailure
    public data class PermissionDenied(public val permission: KadrePermission) : KadreFailure
    public data class UserCancelled(public val operation: KadreOperation) : KadreFailure
    public data class TemporarilyUnavailable(public val retryable: Boolean) : KadreFailure

    public data class InvalidRequest(public val field: String?) : KadreFailure {
        init {
            if (field != null) requireStableIdentifier(field, "field")
        }
    }

    public data class AlreadyInUse(public val resource: KadreResourceKind) : KadreFailure
    public data class Closed(public val resource: KadreResourceKind) : KadreFailure

    public data class ResourceLimitExceeded(
        public val resource: KadreResourceKind,
        public val limit: Long,
    ) : KadreFailure {
        init {
            require(limit > 0) { "limit must be positive" }
        }
    }

    public data class SourceOverflow(public val resource: KadreResourceKind) : KadreFailure

    public data class StaleRevision(public val expected: Long, public val received: Long) : KadreFailure {
        init {
            require(expected >= 0 && received >= 0) { "revisions must be non-negative" }
        }
    }

    public data class InteractionRequired(public val reason: InteractionFailureReason) : KadreFailure
    public data class UnsupportedPolicy(public val component: KadrePolicyComponent) : KadreFailure
    public data object ParentScopeCancelled : KadreFailure

    public data class ShutdownTimedOut(public val timeout: Duration) : KadreFailure {
        init {
            require(timeout.isFinite() && timeout.isPositive()) { "timeout must be finite and positive" }
        }
    }

    public data class SourceLost(public val source: CaptureSourceId) : KadreFailure
    public data object ApplicationFailure : KadreFailure

    public data class PlatformFailure(
        public val platform: KadrePlatform,
        public val domain: String,
        public val code: String,
    ) : KadreFailure {
        init {
            requireStableIdentifier(domain, "domain")
            requireStableIdentifier(code, "code")
        }
    }
}

public val KadreFailure.message: String
    get() = when (this) {
        is KadreFailure.Unsupported -> "Operation $operation is unsupported"
        is KadreFailure.PermissionDenied -> "Permission $permission is denied"
        is KadreFailure.UserCancelled -> "Operation $operation was cancelled by the user"
        is KadreFailure.TemporarilyUnavailable -> "Resource is temporarily unavailable"
        is KadreFailure.InvalidRequest -> "Request is invalid${field?.let { ": $it" }.orEmpty()}"
        is KadreFailure.AlreadyInUse -> "Resource $resource is already in use"
        is KadreFailure.Closed -> "Resource $resource is closed"
        is KadreFailure.ResourceLimitExceeded -> "Resource limit exceeded for $resource"
        is KadreFailure.SourceOverflow -> "Source overflow for $resource"
        is KadreFailure.StaleRevision -> "Stale revision: expected $expected, received $received"
        is KadreFailure.InteractionRequired -> "Interaction is required: $reason"
        is KadreFailure.UnsupportedPolicy -> "Policy component $component is unsupported"
        KadreFailure.ParentScopeCancelled -> "Parent scope is cancelled"
        is KadreFailure.ShutdownTimedOut -> "Shutdown timed out"
        is KadreFailure.SourceLost -> "Capture source was lost"
        KadreFailure.ApplicationFailure -> "Application failed"
        is KadreFailure.PlatformFailure -> "Platform operation failed: $platform/$domain/$code"
    }

public enum class KadreOperation {
    HostAttach,
    RequestRedraw,
    DisplayAccess,
    RequestWindow,
    UpdateWindow,
    RequestWindowAttention,
    CloseWindow,
    RespondToCloseRequest,
    UpdateSurface,
    InstallInteractionHandler,
    ArmInteraction,
    Interaction,
    GamepadEffect,
    StopGamepadEffects,
    TextInput,
    UpdateTextInput,
    ClaimDropTransfer,
    ReadDropItem,
    CapturePermission,
    CaptureRefreshSources,
    CaptureOpen,
    CaptureCollectFrames,
    RawInputAccess,
    PlatformSurfaceAccess,
    PlatformWindowAccess,
}

public enum class KadrePolicyComponent {
    Execution,
    LifecycleEvents,
    HostSignals,
    WindowEvents,
    DeviceEvents,
    InputEvents,
    DevicePolicy,
    CaptureEvents,
    CaptureFrames,
    Diagnostics,
    Resources,
}

public enum class InteractionFailureReason { Missing, Expired, Consumed, WrongSurface }

public enum class KadreResourceKind {
    Host,
    Surface,
    Window,
    WindowRequest,
    Display,
    InputSource,
    InputDevice,
    Gamepad,
    EventCollector,
    Interaction,
    DropTransfer,
    DropItem,
    CursorImage,
    GamepadEffect,
    TextInputSession,
    CaptureSource,
    CaptureSession,
    CaptureCollector,
    CaptureBuffer,
    RetainedPayload,
    ImageResource,
    EventSequence,
}

public enum class KadrePlatform { Android, UIKit, Web, AppKit, Win32, X11, Wayland, Fake }

private fun requireStableIdentifier(value: String, name: String) {
    require(value.isNotEmpty() && value.length <= 256 && value.all { it.code in 0x21..0x7e }) {
        "$name must be a non-empty ASCII identifier of at most 256 code units"
    }
}
