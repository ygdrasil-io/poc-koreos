package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.KadreFailure
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
}

/** A backend-owned native peer whose release must be idempotent. */
public fun interface WindowPeerOwner : AutoCloseable {
    override public fun close()
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
    public val spec: WindowSpec,
    private val stimulusSink: WindowCommandStimulusSink,
) {
    private val ownerLock = Any()
    private val owners = IdentityHashMap<WindowPeerOwner, ManagedWindowPeerOwner>()

    public fun commit(owner: WindowPeerOwner) {
        val managedOwner = synchronized(ownerLock) {
            owners.getOrPut(owner) { ManagedWindowPeerOwner(owner) }
        }
        stimulusSink.commit(requestId, windowId, managedOwner)
    }

    public fun fail(failure: KadreFailure) {
        stimulusSink.fail(requestId, failure)
    }

    public fun nativeClosed() {
        stimulusSink.nativeClosed(requestId)
    }
}

public data class PendingWindowCancellationCommand(
    public val requestId: WindowRequestId,
)

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
    public data class TemporarilyUnavailable(public val retryable: Boolean) : OpenedWindowCloseOutcome
    public data class PlatformFailure(
        public val failure: KadreFailure.PlatformFailure,
    ) : OpenedWindowCloseOutcome
}

internal interface WindowCommandStimulusSink {
    fun commit(requestId: WindowRequestId, windowId: WindowId, owner: WindowPeerOwner)
    fun fail(requestId: WindowRequestId, failure: KadreFailure)
    fun nativeClosed(requestId: WindowRequestId)
}

internal class ManagedWindowPeerOwner(
    private val delegate: WindowPeerOwner,
) : WindowPeerOwner {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) delegate.close()
    }
}
