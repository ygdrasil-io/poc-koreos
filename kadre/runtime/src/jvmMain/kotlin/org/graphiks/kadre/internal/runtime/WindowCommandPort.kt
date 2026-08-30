package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowSpec
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unstable backend SPI used by the runtime window state machine.
 *
 * Implementations marshal commands to their native owner thread. All methods must return
 * promptly and must translate backend failures into explicit command outcomes or stimuli rather
 * than throw; native completion is reported through the [WindowOpenCommand] stimulus methods.
 */
public interface WindowCommandPort {
    public fun requestOpen(command: WindowOpenCommand)

    public fun requestPendingCancellation(
        command: PendingWindowCancellationCommand,
    ): PendingWindowCancellationOutcome

    public fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome

    /** Resolves backend-side coalescing when the application tries to keep a native close open. */
    public fun closeRequestRejected(command: OpenedWindowCloseCommand): CloseRequestRejectionOutcome =
        CloseRequestRejectionOutcome.Rejected
}

/** A backend-owned native peer whose release must be idempotent. */
public fun interface WindowPeerOwner : AutoCloseable {
    override public fun close()
}

/** Unstable backend value used only while a bounded desktop-handle callback is executing. */
public sealed interface RuntimeDesktopNativeWindowHandle {
    public data class AppKit(
        public val nsWindowAddress: ULong,
        public val nsViewAddress: ULong,
    ) : RuntimeDesktopNativeWindowHandle
}

/**
 * Unstable backend SPI for a synchronous, owner-thread desktop handle lease.
 *
 * This type is technically public only so the desktop facade and backend modules can share the
 * lease boundary. It is not part of Kadre's supported public API.
 */
public interface RuntimeDesktopWindowHandleAccess {
    public suspend fun <R> withDesktopHandle(
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): KadreResult<R>
}

/**
 * One admitted native-open command and its narrow stimulus ingress.
 *
 * [commit] may be called only after native preparation has produced a complete peer owner.
 * Late or duplicate stimuli are accepted so native callbacks can race safely with teardown.
 */
public class WindowOpenCommand internal constructor(
    public val requestId: WindowRequestId,
    public val windowId: WindowId,
    public val surfaceId: SurfaceId,
    public val spec: WindowSpec,
    private val stimulusSink: WindowCommandStimulusSink,
) {
    private val ownerLock = Any()
    private val owners = IdentityHashMap<WindowPeerOwner, ManagedWindowPeerOwner>()

    /**
     * Commits one fully prepared peer and its atomic initial surface snapshot.
     *
     * A null [initialSurfaceSnapshot] exists only for pre-surface bootstrap compositions. A
     * manager exposing surface commands rejects it instead of publishing synthetic state.
     * [onSurfaceReady] runs only after the runtime has installed the surface and can accept
     * observations for its [surfaceId].
     */
    public fun commit(
        owner: WindowPeerOwner,
        effectiveSpec: WindowSpec = spec,
        initialSurfaceSnapshot: SurfaceInitialSnapshot? = null,
        onSurfaceReady: () -> Unit = {},
    ) {
        val managedOwner = synchronized(ownerLock) {
            owners.getOrPut(owner) { ManagedWindowPeerOwner(owner) }
        }
        stimulusSink.commit(
            requestId,
            windowId,
            effectiveSpec,
            initialSurfaceSnapshot,
            managedOwner,
            onSurfaceReady,
        )
    }

    public fun fail(failure: KadreFailure) {
        stimulusSink.fail(requestId, failure)
    }

    public fun nativeClosed() {
        stimulusSink.nativeClosed(requestId)
    }

    public fun closeRequested() {
        stimulusSink.closeRequested(requestId)
    }
}

public data class PendingWindowCancellationCommand(
    public val requestId: WindowRequestId,
    public val intent: PendingWindowCancellationIntent = PendingWindowCancellationIntent.RequesterCancellation,
)

public enum class PendingWindowCancellationIntent {
    RequesterCancellation,
    OwnershipRelease,
}

/** Immediate knowledge available for a pending request cancellation. */
public sealed interface PendingWindowCancellationOutcome {
    public data object CancelledBeforeCommit : PendingWindowCancellationOutcome
    public data object CancellationRequested : PendingWindowCancellationOutcome
    public data object TooLate : PendingWindowCancellationOutcome
}

public data class OpenedWindowCloseCommand(
    public val requestId: WindowRequestId,
    public val windowId: WindowId,
    public val owner: WindowPeerOwner,
)

/** Outcomes valid when asking the backend to close an already opened window. */
public sealed interface OpenedWindowCloseOutcome {
    public data object Accepted : OpenedWindowCloseOutcome
    public data object NativeCloseAlreadyCommitted : OpenedWindowCloseOutcome
    public data class TemporarilyUnavailable(public val retryable: Boolean) : OpenedWindowCloseOutcome
    public data class PlatformFailure(
        public val failure: KadreFailure.PlatformFailure,
    ) : OpenedWindowCloseOutcome
}

/** Immediate backend knowledge when rejecting an intercepted native close request. */
public sealed interface CloseRequestRejectionOutcome {
    public data object Rejected : CloseRequestRejectionOutcome
    public data object TooLate : CloseRequestRejectionOutcome
}

internal interface WindowCommandStimulusSink {
    fun commit(
        requestId: WindowRequestId,
        windowId: WindowId,
        effectiveSpec: WindowSpec,
        initialSurfaceSnapshot: SurfaceInitialSnapshot?,
        owner: WindowPeerOwner,
        onSurfaceReady: () -> Unit,
    )
    fun fail(requestId: WindowRequestId, failure: KadreFailure)
    fun nativeClosed(requestId: WindowRequestId)
    fun closeRequested(requestId: WindowRequestId)
}

internal class ManagedWindowPeerOwner(
    private val delegate: WindowPeerOwner,
) : WindowPeerOwner, RuntimeDesktopWindowHandleAccess {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) delegate.close()
    }

    override suspend fun <R> withDesktopHandle(
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): KadreResult<R> {
        if (closed.get()) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        val access = delegate as? RuntimeDesktopWindowHandleAccess
            ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess))
        return access.withDesktopHandle(block)
    }
}
