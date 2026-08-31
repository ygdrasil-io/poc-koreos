package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.window.RejectedWindowField
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowOperationId
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowState
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowRevision
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

    /**
     * Admits one window update for asynchronous native completion.
     *
     * Implementations must return promptly. They report the effective native snapshot, or a
     * failure before native commit, through [WindowUpdateCommand.applied],
     * [WindowUpdateCommand.partiallyApplied], or [WindowUpdateCommand.rejected].
     */
    public fun requestUpdate(command: WindowUpdateCommand)

    /** Attempts to withdraw an update that has not crossed the native commit boundary. */
    public fun requestUpdateCancellation(
        command: WindowUpdateCancellationCommand,
    ): WindowUpdateCancellationOutcome = WindowUpdateCancellationOutcome.TooLate

    public fun requestPendingCancellation(
        command: PendingWindowCancellationCommand,
    ): PendingWindowCancellationOutcome

    public fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome

    /** Resolves backend-side coalescing when the application tries to keep a native close open. */
    public fun closeRequestRejected(command: OpenedWindowCloseCommand): CloseRequestRejectionOutcome =
        CloseRequestRejectionOutcome.Rejected
}

/**
 * One immutable geometry update admitted by the runtime.
 *
 * The operation ID is allocated before dispatch. The backend must echo its effective native
 * snapshot through this command rather than assuming the requested values became effective.
 */
@ConsistentCopyVisibility
public data class WindowUpdateCommand internal constructor(
    public val windowId: WindowId,
    public val operationId: WindowOperationId,
    public val expectedRevision: WindowRevision?,
    public val update: WindowUpdate,
    public val desiredLevel: WindowLevel,
    private val stimulusSink: WindowUpdateCommandStimulusSink,
    private val fullscreenObservationSink: WindowFullscreenObservationSink,
) {
    public fun applied(state: WindowState) {
        stimulusSink.accept(WindowUpdateCommandStimulus.Applied(operationId, state))
    }

    /** Publishes an authoritative post-commit snapshot with the fields the backend could not apply. */
    public fun partiallyApplied(state: WindowState, rejected: List<RejectedWindowField>) {
        stimulusSink.accept(WindowUpdateCommandStimulus.PartiallyApplied(operationId, state, rejected))
    }

    public fun rejected(error: Throwable) {
        stimulusSink.accept(WindowUpdateCommandStimulus.Rejected(operationId, error))
    }

    public fun failed(
        failure: KadreFailure,
        diagnosticCause: Throwable? = null,
    ) {
        stimulusSink.accept(WindowUpdateCommandStimulus.Failed(operationId, failure, diagnosticCause))
    }

    public fun committedFailure(
        effectiveState: WindowState,
        publicationOperationId: WindowOperationId?,
        failure: KadreFailure,
        rejected: List<RejectedWindowField> = emptyList(),
        diagnosticCause: Throwable? = null,
    ) {
        stimulusSink.accept(
            WindowUpdateCommandStimulus.CommittedFailure(
                operationId,
                effectiveState,
                publicationOperationId,
                failure,
                rejected,
                diagnosticCause,
            ),
        )
    }

    public fun fullscreenWill(target: FullscreenMode) {
        fullscreenObservationSink.accept(windowId, operationId, WindowFullscreenObservation.Will(target))
    }

    /**
     * Requests the runtime-authoritative transition from prepared to selector invocation.
     *
     * A false result is terminal for this native setter attempt: the backend must not invoke the
     * selector, because cancellation or an external fullscreen observation won the arbitration.
     */
    public fun fullscreenSelectorInvoking(): Boolean =
        fullscreenObservationSink.beginSelectorInvocation(windowId, operationId)

    /** Marks the actual return of the native selector after all reentrant callbacks were admitted. */
    public fun fullscreenSelectorReturned(failure: KadreFailure? = null) {
        fullscreenObservationSink.finishSelectorInvocation(windowId, operationId, failure)
    }

    public fun fullscreenDid(
        effectiveState: WindowState,
        rejected: List<RejectedWindowField> = emptyList(),
    ) {
        fullscreenObservationSink.accept(
            windowId,
            operationId,
            WindowFullscreenObservation.Did(effectiveState, rejected),
        )
    }

    public fun fullscreenDidFail(
        target: FullscreenMode,
        effectiveState: WindowState? = null,
        rejected: List<RejectedWindowField> = emptyList(),
        terminalFailure: KadreFailure? = null,
    ) {
        fullscreenObservationSink.accept(
            windowId,
            operationId,
            WindowFullscreenObservation.DidFail(target, effectiveState, rejected, terminalFailure),
        )
    }
}

/** Correlated backend completion for one [WindowUpdateCommand]. */
public sealed interface WindowUpdateCommandStimulus {
    public data class Applied(
        public val operationId: WindowOperationId,
        public val state: WindowState,
    ) : WindowUpdateCommandStimulus

    public data class Rejected(
        public val operationId: WindowOperationId,
        public val error: Throwable,
    ) : WindowUpdateCommandStimulus

    public data class Failed(
        public val operationId: WindowOperationId,
        public val failure: KadreFailure,
        public val diagnosticCause: Throwable? = null,
    ) : WindowUpdateCommandStimulus

    public data class CommittedFailure(
        public val operationId: WindowOperationId,
        public val effectiveState: WindowState,
        public val publicationOperationId: WindowOperationId?,
        public val failure: KadreFailure,
        public val rejected: List<RejectedWindowField> = emptyList(),
        public val diagnosticCause: Throwable? = null,
    ) : WindowUpdateCommandStimulus

    public data class PartiallyApplied(
        public val operationId: WindowOperationId,
        public val state: WindowState,
        public val rejected: List<RejectedWindowField>,
    ) : WindowUpdateCommandStimulus {
        init {
            require(rejected.isNotEmpty()) { "rejected must not be empty" }
        }
    }
}

internal sealed interface WindowFullscreenObservation {
    data class Will(val target: FullscreenMode) : WindowFullscreenObservation
    data class Did(
        val effectiveState: WindowState,
        val rejected: List<RejectedWindowField> = emptyList(),
    ) : WindowFullscreenObservation

    data class DidFail(
        val target: FullscreenMode,
        val effectiveState: WindowState? = null,
        val rejected: List<RejectedWindowField> = emptyList(),
        val terminalFailure: KadreFailure? = null,
    ) : WindowFullscreenObservation
}

/** Unstable backend SPI for one uncorrelated native fullscreen observation. */
public sealed interface RuntimeFullscreenObservation {
    public data class Will(public val target: FullscreenMode) : RuntimeFullscreenObservation
    public data class Did(public val effectiveState: WindowState) : RuntimeFullscreenObservation
    public data class DidFail(public val target: FullscreenMode) : RuntimeFullscreenObservation
}

/** Serialised runtime coordination for fullscreen observations without a local operation ID. */
public fun interface RuntimeFullscreenObservationSink {
    public fun accept(windowId: WindowId, observation: RuntimeFullscreenObservation): Boolean

    /** Returns the runtime-authoritative level to restore after a native fullscreen transition. */
    public fun desiredLevel(windowId: WindowId): WindowLevel? = null
}

/** A correlated request to withdraw a not-yet-committed [WindowUpdateCommand]. */
public data class WindowUpdateCancellationCommand(
    public val operationId: WindowOperationId,
)

/** Immediate backend knowledge about a requested window-update withdrawal. */
public sealed interface WindowUpdateCancellationOutcome {
    public data object CancelledBeforeCommit : WindowUpdateCancellationOutcome
    public data object CancellationRequested : WindowUpdateCancellationOutcome
    public data object TooLate : WindowUpdateCancellationOutcome
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

internal fun interface WindowUpdateCommandStimulusSink {
    fun accept(stimulus: WindowUpdateCommandStimulus)
}

internal interface WindowFullscreenObservationSink {
    fun accept(windowId: WindowId, operationId: WindowOperationId, observation: WindowFullscreenObservation)

    fun beginSelectorInvocation(windowId: WindowId, operationId: WindowOperationId): Boolean

    fun finishSelectorInvocation(
        windowId: WindowId,
        operationId: WindowOperationId,
        failure: KadreFailure?,
    )
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
