package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.RejectedWindowField
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowCapabilities
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowCloseReason
import org.graphiks.kadre.window.WindowCloseRequestId
import org.graphiks.kadre.window.WindowCloseResponseOutcome
import org.graphiks.kadre.window.WindowCreationMode
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowOperationId
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowRevision
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowState
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Unstable backend SPI implementing the portable window-request state machine.
 *
 * Constructing this manager does not install it in any runtime session. Backends may compose it
 * privately until their complete public window contract is ready for activation.
 */
public class RuntimeWindowManager public constructor(
    private val resources: ResourceBudgetPolicy,
    private val commandPort: WindowCommandPort,
    private val platform: KadrePlatform,
    private val failureReporter: RuntimeFailureReporter,
    private val publicWindowCapabilities: Boolean = false,
    private val onLastWindowClosed: (() -> Unit)? = null,
) : WindowManager, AutoCloseable {
    private val lock = Any()
    private val pending = linkedMapOf<WindowRequestId, PendingWindow>()
    private val committed = linkedMapOf<WindowRequestId, CommittedWindow>()
    private var nextAdmissionOrder = 0L
    private var reservedWindowSlots = 0
    private var managerRevision = 0L
    private var lastWindowPolicyArmed = false
    private var lastWindowStopProposed = false
    private var lastWindowStopProposalPending = false
    private var closed = false
    private val eventSequence = AtomicLong(0L)
    private val eventClockOrigin = System.nanoTime()
    private var sessionEventStampSource: (() -> EventStamp)? = null
    private val mutableState = MutableStateFlow(
        WindowManagerState(
            primary = null,
            windows = emptyList(),
            capabilities = WindowManagerCapabilities(
                if (publicWindowCapabilities) {
                    Capability.Supported(
                        setOf(WindowCreationMode.OpenedHere),
                        FeatureAvailability.Available,
                    )
                } else {
                    unsupported(KadreOperation.RequestWindow)
                },
            ),
            revision = WindowManagerRevision(0L),
        ),
    )
    private val stimulusSink = object : WindowCommandStimulusSink {
        override fun commit(
            requestId: WindowRequestId,
            windowId: WindowId,
            effectiveSpec: WindowSpec,
            owner: WindowPeerOwner,
        ) {
            acceptCommit(requestId, windowId, effectiveSpec, owner)
        }

        override fun fail(requestId: WindowRequestId, failure: KadreFailure) {
            acceptFailure(requestId, failure)
        }

        override fun nativeClosed(requestId: WindowRequestId) {
            acceptNativeClose(requestId)
        }

        override fun closeRequested(requestId: WindowRequestId) {
            acceptCloseRequest(requestId)
        }
    }

    override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

    // Narrow module-internal seam for deterministic ownership-race tests.
    internal fun requestForTesting(requestId: WindowRequestId): RuntimeWindowRequest? = synchronized(lock) {
        pending[requestId]?.request ?: committed[requestId]?.request
    }

    internal fun installSessionEventStampSource(source: () -> EventStamp) {
        synchronized(lock) {
            check(sessionEventStampSource == null) { "window event stamp source was already installed" }
            check(pending.isEmpty() && committed.isEmpty()) { "window event stamp source must be installed before admission" }
            sessionEventStampSource = source
        }
    }

    override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> {
        currentCoroutineContext().ensureActive()
        lateinit var request: RuntimeWindowRequest
        val admissionFailure = synchronized(lock) {
            when {
                closed -> KadreFailure.Closed(KadreResourceKind.Host)
                pending.size >= resources.maxPendingWindowRequests -> KadreFailure.ResourceLimitExceeded(
                    KadreResourceKind.WindowRequest,
                    resources.maxPendingWindowRequests.toLong(),
                )

                reservedWindowSlots >= resources.maxWindowsPerSession -> KadreFailure.ResourceLimitExceeded(
                    KadreResourceKind.Window,
                    resources.maxWindowsPerSession.toLong(),
                )

                else -> {
                    val requestId = RuntimeProcessIds.nextWindowRequestId()
                    val windowId = RuntimeProcessIds.nextWindowId()
                    request = RuntimeWindowRequest(requestId, windowId, this)
                    val record = PendingWindow(
                        request = request,
                        spec = spec,
                        admissionOrder = nextAdmissionOrder++,
                    )
                    pending[requestId] = record
                    reservedWindowSlots += 1
                    when (
                        val dispatch = guardPort("request-open-exception") {
                            commandPort.requestOpen(
                                WindowOpenCommand(requestId, windowId, spec, stimulusSink),
                            )
                        }
                    ) {
                        is GuardedCall.Success -> Unit
                        is GuardedCall.Failure -> terminaliseOpenDispatchFailureLocked(record, dispatch.failure)
                    }
                    null
                }
            }
        }
        if (admissionFailure != null) return KadreResult.Failure(admissionFailure)

        val result = suspendCancellableCoroutine<KadreResult<WindowRequest>> { continuation ->
            continuation.invokeOnCancellation {
                abandonBeforeHandoff(request)
            }
            // Make resume and delivery distinct so prompt cancellation can revoke pre-handoff ownership.
            CoroutineScope(continuation.context).launch(start = CoroutineStart.DEFAULT) {
                continuation.resume(KadreResult.Success(request)) { _, _, _ ->
                    abandonBeforeHandoff(request)
                }
                synchronized(lock) {
                    pending[request.id]?.let(::finishOpenDispatchLocked)
                }
            }
        }
        request.markHandoffDelivered()
        return result
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true

            val pendingAtClose = pending.values.sortedBy(PendingWindow::admissionOrder)
            pending.clear()
            releaseWindowSlotsLocked(pendingAtClose.size)

            pendingAtClose.forEach { record ->
                val closeOutcome = if (
                    !record.pendingCancellationIssued ||
                    record.cancellationOutcome == WindowCancellationOutcome.TooLate
                ) {
                    record.pendingCancellationIssued = true
                    guardPort("pending-close-exception") {
                        commandPort.requestPendingCancellation(
                            PendingWindowCancellationCommand(
                                record.request.id,
                                PendingWindowCancellationIntent.OwnershipRelease,
                            ),
                        )
                    }
                } else {
                    null
                }
                record.preparedOwner?.let(::safeCloseOwner)
                record.preparedOwner = null
                val outcome = if (
                    closeOutcome is GuardedCall.Success &&
                    closeOutcome.value == PendingWindowCancellationOutcome.CancelledBeforeCommit
                ) WindowRequestOutcome.Cancelled else WindowRequestOutcome.RequesterDetached
                record.request.terminate(outcome)
            }

            committed.values
                .sortedByDescending(CommittedWindow::admissionOrder)
                .toList()
                .forEach(::forceCloseLocked)
        }
    }

    internal suspend fun cancelRequest(request: RuntimeWindowRequest): WindowCancellationOutcome = synchronized(lock) {
        request.terminalOutcome()?.let { return@synchronized WindowCancellationOutcome.AlreadyTerminated(it) }
        val record = pending[request.id]
            ?: return@synchronized WindowCancellationOutcome.AlreadyTerminated(
                checkNotNull(request.terminalOutcome()),
            )
        record.cancellationOutcome?.let { return@synchronized it }
        if (record.pendingCancellationIssued) return@synchronized WindowCancellationOutcome.CancellationRequested

        record.pendingCancellationIssued = true
        val portOutcome = guardPort("pending-close-exception") {
            commandPort.requestPendingCancellation(
                PendingWindowCancellationCommand(
                    request.id,
                    PendingWindowCancellationIntent.RequesterCancellation,
                ),
            )
        }
        request.terminalOutcome()?.let { return@synchronized WindowCancellationOutcome.AlreadyTerminated(it) }
        if (pending[request.id] !== record) {
            return@synchronized WindowCancellationOutcome.AlreadyTerminated(
                checkNotNull(request.terminalOutcome()),
            )
        }
        when (portOutcome) {
            is GuardedCall.Failure -> {
                removePendingLocked(record)
                record.preparedOwner?.let(::safeCloseOwner)
                record.preparedOwner = null
                val outcome = WindowRequestOutcome.RequesterDetached
                request.terminate(outcome)
                WindowCancellationOutcome.AlreadyTerminated(outcome)
            }

            is GuardedCall.Success -> when (portOutcome.value) {
                PendingWindowCancellationOutcome.CancelledBeforeCommit -> {
                    removePendingLocked(record)
                    record.preparedOwner?.let(::safeCloseOwner)
                    record.preparedOwner = null
                    request.terminate(WindowRequestOutcome.Cancelled)
                    WindowCancellationOutcome.CancelledBeforeCommit
                }

                PendingWindowCancellationOutcome.CancellationRequested -> {
                    WindowCancellationOutcome.CancellationRequested.also { record.cancellationOutcome = it }
                }

                PendingWindowCancellationOutcome.TooLate -> {
                    WindowCancellationOutcome.TooLate.also { record.cancellationOutcome = it }
                }
            }
        }
    }

    internal fun detachRequest(request: RuntimeWindowRequest) {
        synchronized(lock) {
            if (request.terminalOutcome() != null) return
            val record = pending[request.id] ?: return
            val issueCancellation =
                !record.pendingCancellationIssued ||
                    record.cancellationOutcome == WindowCancellationOutcome.TooLate
            record.pendingCancellationIssued = true
            removePendingLocked(record)
            val portOutcome = if (issueCancellation) {
                guardPort("pending-close-exception") {
                    commandPort.requestPendingCancellation(
                        PendingWindowCancellationCommand(
                            request.id,
                            PendingWindowCancellationIntent.OwnershipRelease,
                        ),
                    )
                }
            } else {
                null
            }
            record.preparedOwner?.let(::safeCloseOwner)
            record.preparedOwner = null
            request.terminate(
                if (
                    portOutcome is GuardedCall.Success &&
                    portOutcome.value == PendingWindowCancellationOutcome.CancelledBeforeCommit
                ) WindowRequestOutcome.Cancelled else WindowRequestOutcome.RequesterDetached,
            )
        }
    }

    internal suspend fun applyWindow(
        window: RuntimeWindow,
        update: WindowUpdate,
    ): KadreResult<WindowUpdateOutcome> = synchronized(lock) {
        val current = window.currentState()
        if (current.phase != WindowPhase.Open) {
            return@synchronized KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        }
        update.expectedRevision?.let { expected ->
            if (expected != current.revision) {
                return@synchronized KadreResult.Failure(
                    KadreFailure.StaleRevision(expected.value, current.revision.value),
                )
            }
        }
        val operationId = RuntimeProcessIds.nextWindowOperationId()
        val rejected = changedProperties(update).map { property ->
            RejectedWindowField(property, KadreFailure.Unsupported(KadreOperation.UpdateWindow))
        }
        KadreResult.Success(
            if (rejected.isEmpty()) {
                WindowUpdateOutcome.Applied(operationId, current)
            } else {
                WindowUpdateOutcome.PartiallyApplied(operationId, current, rejected)
            },
        )
    }

    internal suspend fun requestAttention(window: RuntimeWindow): KadreResult<Unit> = synchronized(lock) {
        if (window.currentState().phase != WindowPhase.Open) {
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        } else {
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention))
        }
    }

    internal suspend fun respondToCloseRequest(
        window: RuntimeWindow,
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): KadreResult<WindowCloseResponseOutcome> {
        val result = synchronized(lock) {
            window.closeResponseFor(requestId, decision)?.let { known ->
                return@synchronized KadreResult.Success(known)
            }
            if (window.currentState().phase == WindowPhase.Closed) {
                return@synchronized KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            }
            val record = committed[window.requestId]
                ?: return@synchronized KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            val closeRequest = window.activeCloseRequest
            if (closeRequest == null || closeRequest.id != requestId) {
                return@synchronized KadreResult.Failure(KadreFailure.InvalidRequest("requestId"))
            }
            when (decision) {
                WindowCloseDecision.Reject -> {
                    when (
                        val portOutcome = guardPort("close-reject-exception") {
                            commandPort.closeRequestRejected(record.closeCommand())
                        }
                    ) {
                        is GuardedCall.Failure -> KadreResult.Failure(portOutcome.failure)
                        is GuardedCall.Success -> when (portOutcome.value) {
                            CloseRequestRejectionOutcome.Rejected -> {
                                window.resolveCloseRequest(
                                    closeRequest,
                                    decision,
                                    WindowCloseResponseOutcome.KeptOpen,
                                    committed = false,
                                )
                                KadreResult.Success(WindowCloseResponseOutcome.KeptOpen)
                            }

                            CloseRequestRejectionOutcome.TooLate ->
                                KadreResult.Success(WindowCloseResponseOutcome.TooLate)
                        }
                    }
                }

                WindowCloseDecision.Accept -> acceptCloseResponseLocked(record, closeRequest)
            }
        }
        drainLastWindowStopProposal()
        return result
    }

    internal suspend fun closeWindow(window: RuntimeWindow): KadreResult<WindowCloseOutcome> {
        val result = synchronized(lock) {
            when (window.currentState().phase) {
                WindowPhase.Closed -> KadreResult.Success(WindowCloseOutcome.Closed)
                WindowPhase.Closing -> KadreResult.Success(
                    WindowCloseOutcome.Accepted(checkNotNull(window.closeOperationId)),
                )

                WindowPhase.Open -> {
                    val record = committed[window.requestId]
                        ?: return@synchronized KadreResult.Success(WindowCloseOutcome.Closed)
                    val operationId = RuntimeProcessIds.nextWindowOperationId()
                    window.prepareClose(operationId)
                    record.closeCommandSent = true
                    val portOutcome = guardPort("opened-close-exception") {
                        commandPort.requestOpenedClose(record.closeCommand())
                    }
                    if (committed[window.requestId] !== record || window.currentState().phase == WindowPhase.Closed) {
                        return@synchronized KadreResult.Success(WindowCloseOutcome.Closed)
                    }
                    when (portOutcome) {
                        is GuardedCall.Failure -> {
                            forceCloseLocked(record)
                            KadreResult.Failure(portOutcome.failure)
                        }

                        is GuardedCall.Success -> when (val outcome = portOutcome.value) {
                            OpenedWindowCloseOutcome.Accepted -> {
                                val reason = if (window.activeCloseRequest != null) {
                                    WindowCloseReason.User
                                } else {
                                    WindowCloseReason.System
                                }
                                window.activeCloseRequest?.let { closeRequest ->
                                    window.resolveCloseRequest(
                                        closeRequest,
                                        WindowCloseDecision.Accept,
                                        WindowCloseResponseOutcome.Closing(operationId),
                                        committed = true,
                                    )
                                }
                                window.beginClosing(operationId, reason, nextEventStamp())
                                KadreResult.Success(WindowCloseOutcome.Accepted(operationId))
                            }

                            OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted -> {
                                val reason = if (window.activeCloseRequest != null) {
                                    WindowCloseReason.User
                                } else {
                                    WindowCloseReason.System
                                }
                                window.beginClosing(operationId, reason, nextEventStamp())
                                KadreResult.Success(WindowCloseOutcome.Accepted(operationId))
                            }

                            is OpenedWindowCloseOutcome.TemporarilyUnavailable -> {
                                record.closeCommandSent = false
                                window.cancelPreparedClose(operationId)
                                KadreResult.Failure(KadreFailure.TemporarilyUnavailable(outcome.retryable))
                            }

                            is OpenedWindowCloseOutcome.PlatformFailure -> {
                                record.closeCommandSent = false
                                window.cancelPreparedClose(operationId)
                                KadreResult.Failure(outcome.failure)
                            }
                        }
                    }
                }
            }
        }
        drainLastWindowStopProposal()
        return result
    }

    internal suspend fun <R> withDesktopHandle(
        window: RuntimeWindow,
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): KadreResult<R> {
        if (!publicWindowCapabilities) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess))
        }
        val access = synchronized(lock) {
            if (window.currentState().phase != WindowPhase.Open) return@synchronized null
            committed[window.requestId]?.owner as? RuntimeDesktopWindowHandleAccess
        } ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        return access.withDesktopHandle(block)
    }

    private fun abandonBeforeHandoff(request: RuntimeWindowRequest) {
        if (!request.claimPreHandoffCancellation()) return
        synchronized(lock) {
            val pendingRecord = pending[request.id]
            if (pendingRecord != null) {
                val issueCancellation =
                    !pendingRecord.pendingCancellationIssued ||
                        pendingRecord.cancellationOutcome == WindowCancellationOutcome.TooLate
                pendingRecord.pendingCancellationIssued = true
                removePendingLocked(pendingRecord)
                val portOutcome = if (issueCancellation) {
                    guardPort("pending-close-exception") {
                        commandPort.requestPendingCancellation(
                            PendingWindowCancellationCommand(
                                request.id,
                                PendingWindowCancellationIntent.OwnershipRelease,
                            ),
                        )
                    }
                } else {
                    null
                }
                pendingRecord.preparedOwner?.let(::safeCloseOwner)
                pendingRecord.preparedOwner = null
                request.finishPreHandoffCancellation(
                    outcome = if (
                        portOutcome is GuardedCall.Success &&
                        portOutcome.value == PendingWindowCancellationOutcome.CancelledBeforeCommit
                    ) WindowRequestOutcome.Cancelled else WindowRequestOutcome.RequesterDetached,
                )
                return@synchronized
            }
            committed[request.id]?.let { record ->
                forceCloseLocked(record)
            }
            request.finishPreHandoffCancellation(WindowRequestOutcome.RequesterDetached)
        }
        drainLastWindowStopProposal()
    }

    private fun acceptCommit(
        requestId: WindowRequestId,
        windowId: WindowId,
        effectiveSpec: WindowSpec,
        owner: WindowPeerOwner,
    ) {
        try {
            var closeOwner: WindowPeerOwner? = null
            synchronized(lock) {
                val record = pending[requestId]
                if (record == null) {
                    if (committed[requestId]?.owner !== owner) closeOwner = owner
                    return@synchronized
                }
                if (record.openDispatching) {
                    if (record.preparedOwner == null) {
                        record.preparedOwner = owner
                        record.preparedEffectiveSpec = effectiveSpec
                    } else if (record.preparedOwner !== owner) {
                        closeOwner = owner
                    }
                    return@synchronized
                }
                commitPendingLocked(record, windowId, effectiveSpec, owner)
            }
            closeOwner?.let(::safeCloseOwner)
        } catch (cause: Exception) {
            recoverCallbackFailure(requestId, owner, cause)
        } catch (cause: LinkageError) {
            recoverCallbackFailure(requestId, owner, cause)
        }
    }

    private fun acceptFailure(requestId: WindowRequestId, failure: KadreFailure) {
        try {
            val normalised = normaliseRejection(failure)
            synchronized(lock) {
                val record = pending[requestId] ?: return
                removePendingLocked(record)
                record.preparedOwner?.let(::safeCloseOwner)
                record.preparedOwner = null
                record.request.terminate(
                    if (record.cancellationOutcome == WindowCancellationOutcome.CancellationRequested) {
                        WindowRequestOutcome.Cancelled
                    } else {
                        WindowRequestOutcome.Rejected(normalised)
                    },
                )
            }
        } catch (cause: Exception) {
            recoverCallbackFailure(requestId, null, cause)
        } catch (cause: LinkageError) {
            recoverCallbackFailure(requestId, null, cause)
        }
    }

    private fun acceptCloseRequest(requestId: WindowRequestId) {
        try {
            synchronized(lock) {
                val record = committed[requestId] ?: return@synchronized null
                if (record.window.currentState().phase != WindowPhase.Open) return@synchronized null
                record.window.createCloseRequest(nextEventStamp())?.let { (_, event) ->
                    record.window.publish(event)
                }
            }
        } catch (cause: Exception) {
            recoverCallbackFailure(requestId, null, cause)
        } catch (cause: LinkageError) {
            recoverCallbackFailure(requestId, null, cause)
        }
    }

    private fun acceptNativeClose(requestId: WindowRequestId) {
        try {
            var owner: WindowPeerOwner? = null
            synchronized(lock) {
                val record = committed.remove(requestId)
                if (record != null) {
                    if (record.window.currentState().phase == WindowPhase.Open) {
                        record.window.beginClosing(
                            RuntimeProcessIds.nextWindowOperationId(),
                            if (record.window.activeCloseRequest != null) {
                                WindowCloseReason.User
                            } else {
                                WindowCloseReason.System
                            },
                            nextEventStamp(),
                        )
                    }
                    record.window.markNativeCloseCommitted()
                    record.window.finishClosing()
                    releaseWindowSlotsLocked(1)
                    publishMembershipLocked()
                    owner = record.owner
                    return@synchronized
                }
                val pendingRecord = pending[requestId] ?: return
                removePendingLocked(pendingRecord)
                owner = pendingRecord.preparedOwner
                pendingRecord.preparedOwner = null
                val failure = platformFailure("closed-before-handoff")
                safeReport(KadreException(failure))
                pendingRecord.request.terminate(WindowRequestOutcome.Rejected(failure))
            }
            owner?.let(::safeCloseOwner)
        } catch (cause: Exception) {
            recoverCallbackFailure(requestId, null, cause)
        } catch (cause: LinkageError) {
            recoverCallbackFailure(requestId, null, cause)
        } finally {
            drainLastWindowStopProposal()
        }
    }

    private fun acceptCloseResponseLocked(
        record: CommittedWindow,
        closeRequest: RuntimeCloseRequest,
    ): KadreResult<WindowCloseResponseOutcome> {
        val operationId = RuntimeProcessIds.nextWindowOperationId()
        record.window.prepareClose(operationId)
        record.closeCommandSent = true
        val portOutcome = guardPort("opened-close-exception") {
            commandPort.requestOpenedClose(record.closeCommand())
        }
        if (committed[record.request.id] !== record || record.window.currentState().phase == WindowPhase.Closed) {
            return KadreResult.Success(WindowCloseResponseOutcome.TooLate)
        }
        return when (portOutcome) {
            is GuardedCall.Failure -> {
                forceCloseLocked(record)
                KadreResult.Failure(portOutcome.failure)
            }

            is GuardedCall.Success -> when (val outcome = portOutcome.value) {
                OpenedWindowCloseOutcome.Accepted -> {
                    val response = WindowCloseResponseOutcome.Closing(operationId)
                    record.window.resolveCloseRequest(
                        closeRequest,
                        WindowCloseDecision.Accept,
                        response,
                        committed = true,
                    )
                    record.window.beginClosing(operationId, WindowCloseReason.User, nextEventStamp())
                    KadreResult.Success(response)
                }

                OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted ->
                    KadreResult.Success(WindowCloseResponseOutcome.TooLate)

                is OpenedWindowCloseOutcome.TemporarilyUnavailable -> {
                    record.closeCommandSent = false
                    record.window.cancelPreparedClose(operationId)
                    KadreResult.Failure(KadreFailure.TemporarilyUnavailable(outcome.retryable))
                }

                is OpenedWindowCloseOutcome.PlatformFailure -> {
                    record.closeCommandSent = false
                    record.window.cancelPreparedClose(operationId)
                    KadreResult.Failure(outcome.failure)
                }
            }
        }
    }

    private fun forceCloseLocked(record: CommittedWindow) {
        if (committed.remove(record.request.id) == null) return
        if (record.window.currentState().phase == WindowPhase.Open) {
            record.window.beginClosing(
                RuntimeProcessIds.nextWindowOperationId(),
                if (closed) WindowCloseReason.SessionStopping else WindowCloseReason.System,
                nextEventStamp(),
            )
        }
        if (!record.closeCommandSent) {
            record.closeCommandSent = true
            val close = guardPort("opened-close-exception") {
                commandPort.requestOpenedClose(record.closeCommand())
            }
            when (close) {
                is GuardedCall.Success -> when (val outcome = close.value) {
                    OpenedWindowCloseOutcome.Accepted,
                    OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted,
                    -> Unit
                    is OpenedWindowCloseOutcome.TemporarilyUnavailable -> safeReport(
                        KadreException(KadreFailure.TemporarilyUnavailable(outcome.retryable)),
                    )

                    is OpenedWindowCloseOutcome.PlatformFailure -> safeReport(KadreException(outcome.failure))
                }

                is GuardedCall.Failure -> Unit
            }
        }
        record.window.markNativeCloseCommitted()
        record.window.finishClosing()
        releaseWindowSlotsLocked(1)
        publishMembershipLocked()
        safeCloseOwner(record.owner)
    }

    private fun finishOpenDispatchLocked(record: PendingWindow) {
        if (pending[record.request.id] !== record) return
        record.openDispatching = false
        val owner = record.preparedOwner ?: return
        val effectiveSpec = checkNotNull(record.preparedEffectiveSpec)
        record.preparedOwner = null
        record.preparedEffectiveSpec = null
        commitPendingLocked(record, record.request.windowId, effectiveSpec, owner)
    }

    private fun terminaliseOpenDispatchFailureLocked(
        record: PendingWindow,
        failure: KadreFailure.PlatformFailure,
    ) {
        if (pending[record.request.id] !== record) return
        removePendingLocked(record)
        record.openDispatching = false
        record.preparedOwner?.let(::safeCloseOwner)
        record.preparedOwner = null
        record.request.terminate(WindowRequestOutcome.Rejected(failure))
    }

    private fun commitPendingLocked(
        record: PendingWindow,
        windowId: WindowId,
        effectiveSpec: WindowSpec,
        owner: WindowPeerOwner,
    ) {
        if (pending.remove(record.request.id) !== record) {
            safeCloseOwner(owner)
            return
        }
        val surface = MinimalWindowSurface(RuntimeProcessIds.nextSurfaceId(), effectiveSpec.contentSize)
        val window = RuntimeWindow(
            requestId = record.request.id,
            id = windowId,
            spec = effectiveSpec,
            surface = surface,
            manager = this,
            publicWindowCapabilities = publicWindowCapabilities,
        )
        committed[record.request.id] = CommittedWindow(
            request = record.request,
            window = window,
            owner = owner,
            admissionOrder = record.admissionOrder,
        )
        publishMembershipLocked()
        record.request.terminate(WindowRequestOutcome.OpenedHere(window))
    }

    private fun recoverCallbackFailure(
        requestId: WindowRequestId,
        callbackOwner: WindowPeerOwner?,
        cause: Throwable,
    ) {
        safeReport(cause)
        try {
            synchronized(lock) {
                val pendingRecord = pending[requestId]
                if (pendingRecord != null) {
                    removePendingLocked(pendingRecord)
                    pendingRecord.preparedOwner?.let(::safeCloseOwner)
                    pendingRecord.preparedOwner = null
                    pendingRecord.request.terminate(
                        WindowRequestOutcome.Rejected(platformFailure("callback-exception")),
                    )
                }
                val committedRecord = committed[requestId]
                if (committedRecord != null) forceCloseLocked(committedRecord)
            }
        } catch (recoveryCause: Exception) {
            safeReport(recoveryCause)
        } catch (recoveryCause: LinkageError) {
            safeReport(recoveryCause)
        } finally {
            drainLastWindowStopProposal()
        }
        callbackOwner?.let(::safeCloseOwner)
    }

    private fun normaliseRejection(failure: KadreFailure): KadreFailure {
        val valid = when (failure) {
            is KadreFailure.Unsupported -> failure.operation == KadreOperation.RequestWindow
            is KadreFailure.InvalidRequest -> failure.field in REQUEST_WINDOW_INVALID_REQUEST_FIELDS
            is KadreFailure.InteractionRequired -> true
            is KadreFailure.Closed -> failure.resource == KadreResourceKind.Host
            is KadreFailure.TemporarilyUnavailable -> true
            is KadreFailure.ResourceLimitExceeded -> failure.resource == KadreResourceKind.Window
            is KadreFailure.PlatformFailure -> true
            else -> false
        }
        if (valid) return failure
        return platformFailure("invalid-rejection").also { safeReport(KadreException(it)) }
    }

    private inline fun <T> guardPort(code: String, call: () -> T): GuardedCall<T> = try {
        GuardedCall.Success(call())
    } catch (cause: Exception) {
        safeReport(cause)
        GuardedCall.Failure(platformFailure(code))
    } catch (cause: LinkageError) {
        safeReport(cause)
        GuardedCall.Failure(platformFailure(code))
    }

    private fun safeCloseOwner(owner: WindowPeerOwner) {
        try {
            owner.close()
        } catch (cause: Exception) {
            safeReport(cause)
        } catch (cause: LinkageError) {
            safeReport(cause)
        }
    }

    private fun safeReport(cause: Throwable) {
        try {
            failureReporter.report(cause)
        } catch (_: Exception) {
            // Diagnostics must never destabilise window ownership cleanup.
        } catch (_: LinkageError) {
            // Diagnostics must never destabilise window ownership cleanup.
        }
    }

    private fun platformFailure(code: String): KadreFailure.PlatformFailure =
        KadreFailure.PlatformFailure(platform, "window-command-port", code)

    private fun removePendingLocked(record: PendingWindow) {
        if (pending.remove(record.request.id) != null) releaseWindowSlotsLocked(1)
    }

    private fun releaseWindowSlotsLocked(count: Int) {
        check(count >= 0 && count <= reservedWindowSlots) { "window reservation accounting underflow" }
        reservedWindowSlots -= count
    }

    private fun publishMembershipLocked() {
        val previousCount = mutableState.value.windows.size
        val windows = committed.values
            .sortedBy(CommittedWindow::admissionOrder)
            .map(CommittedWindow::window)
        managerRevision += 1L
        mutableState.value = mutableState.value.copy(
            primary = windows.firstOrNull(),
            windows = windows,
            revision = WindowManagerRevision(managerRevision),
        )
        if (onLastWindowClosed != null && windows.isNotEmpty()) lastWindowPolicyArmed = true
        if (
            !closed &&
            onLastWindowClosed != null &&
            lastWindowPolicyArmed &&
            !lastWindowStopProposed &&
            previousCount > 0 &&
            windows.isEmpty()
        ) {
            lastWindowStopProposed = true
            lastWindowStopProposalPending = true
        }
    }

    private fun drainLastWindowStopProposal() {
        val callback = synchronized(lock) {
            if (!lastWindowStopProposalPending) {
                null
            } else {
                lastWindowStopProposalPending = false
                onLastWindowClosed
            }
        } ?: return
        try {
            callback()
        } catch (cause: Exception) {
            safeReport(cause)
        } catch (cause: LinkageError) {
            safeReport(cause)
        }
    }

    private fun nextEventStamp(): EventStamp {
        sessionEventStampSource?.let { return it() }
        val sequence = eventSequence.getAndIncrement()
        check(sequence >= 0L) { "window event sequence exhausted" }
        val elapsed = (System.nanoTime() - eventClockOrigin).coerceAtLeast(0L).nanoseconds
        return EventStamp(SessionSequence(sequence), SessionInstant(elapsed), null)
    }

    private data class PendingWindow(
        val request: RuntimeWindowRequest,
        val spec: WindowSpec,
        val admissionOrder: Long,
        var cancellationOutcome: WindowCancellationOutcome? = null,
        var pendingCancellationIssued: Boolean = false,
        var openDispatching: Boolean = true,
        var preparedOwner: WindowPeerOwner? = null,
        var preparedEffectiveSpec: WindowSpec? = null,
    )

    private data class CommittedWindow(
        val request: RuntimeWindowRequest,
        val window: RuntimeWindow,
        val owner: WindowPeerOwner,
        val admissionOrder: Long,
        var closeCommandSent: Boolean = false,
    ) {
        fun closeCommand(): OpenedWindowCloseCommand =
            OpenedWindowCloseCommand(request.id, window.id, owner)
    }

    private sealed interface GuardedCall<out T> {
        data class Success<T>(val value: T) : GuardedCall<T>
        data class Failure(val failure: KadreFailure.PlatformFailure) : GuardedCall<Nothing>
    }
}

internal class RuntimeWindowRequest(
    override val id: WindowRequestId,
    val windowId: WindowId,
    private val manager: RuntimeWindowManager,
) : WindowRequest {
    private val outcomeLock = Any()
    private val mutableState = MutableStateFlow<WindowRequestState>(WindowRequestState.Pending)
    private var preHandoffOwnership: PreHandoffOwnership? = PreHandoffOwnership.Held

    override val state: StateFlow<WindowRequestState> = mutableState.asStateFlow()

    override fun close() {
        manager.detachRequest(this)
    }

    override suspend fun cancel(): WindowCancellationOutcome = manager.cancelRequest(this)

    override suspend fun await(): WindowRequestOutcome =
        state.filterIsInstance<WindowRequestState.Terminated>().first().outcome

    fun terminate(outcome: WindowRequestOutcome) {
        synchronized(outcomeLock) {
            if (mutableState.value == WindowRequestState.Pending) {
                mutableState.value = WindowRequestState.Terminated(outcome)
            }
        }
    }

    fun terminalOutcome(): WindowRequestOutcome? = synchronized(outcomeLock) {
        (mutableState.value as? WindowRequestState.Terminated)?.outcome
    }

    fun claimPreHandoffCancellation(): Boolean = synchronized(outcomeLock) {
        if (preHandoffOwnership != PreHandoffOwnership.Held) return@synchronized false
        preHandoffOwnership = PreHandoffOwnership.Abandoning
        true
    }

    fun finishPreHandoffCancellation(outcome: WindowRequestOutcome) {
        synchronized(outcomeLock) {
            if (preHandoffOwnership != PreHandoffOwnership.Abandoning) return
            mutableState.value = when (val current = mutableState.value) {
                WindowRequestState.Pending -> WindowRequestState.Terminated(outcome)
                is WindowRequestState.Terminated -> if (current.outcome is WindowRequestOutcome.OpenedHere) {
                    WindowRequestState.Terminated(WindowRequestOutcome.RequesterDetached)
                } else {
                    current
                }
            }
            preHandoffOwnership = null
        }
    }

    fun markHandoffDelivered() {
        synchronized(outcomeLock) {
            if (preHandoffOwnership == PreHandoffOwnership.Held) preHandoffOwnership = null
        }
    }

    private enum class PreHandoffOwnership {
        Held,
        Abandoning,
    }
}

internal class RuntimeWindow(
    val requestId: WindowRequestId,
    override val id: WindowId,
    spec: WindowSpec,
    override val surface: MinimalWindowSurface,
    private val manager: RuntimeWindowManager,
    publicWindowCapabilities: Boolean,
) : Window, RuntimeDesktopWindowHandleAccess {
    private val mutableState = MutableStateFlow(initialWindowState(spec))
    private val mutableCapabilities = MutableStateFlow(windowCapabilities(publicWindowCapabilities))
    private val mutableEvents = MutableSharedFlow<WindowEvent>(extraBufferCapacity = 16)
    internal var activeCloseRequest: RuntimeCloseRequest? = null
        private set
    private var resolvedCloseRequest: ResolvedCloseRequest? = null

    override val state: StateFlow<WindowState> = mutableState.asStateFlow()
    override val capabilities: StateFlow<WindowCapabilities> = mutableCapabilities.asStateFlow()
    override val events: Flow<WindowEvent> = mutableEvents.asSharedFlow()
    var closeOperationId: WindowOperationId? = null
        private set

    override suspend fun apply(update: WindowUpdate): KadreResult<WindowUpdateOutcome> =
        manager.applyWindow(this, update)

    override suspend fun requestAttention(attention: WindowAttention): KadreResult<Unit> =
        manager.requestAttention(this)

    override suspend fun close(): KadreResult<WindowCloseOutcome> = manager.closeWindow(this)

    override suspend fun respondToCloseRequest(
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): KadreResult<WindowCloseResponseOutcome> = manager.respondToCloseRequest(this, requestId, decision)

    override suspend fun <R> withDesktopHandle(
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): KadreResult<R> = manager.withDesktopHandle(this, block)

    fun currentState(): WindowState = mutableState.value

    fun prepareClose(operationId: WindowOperationId) {
        if (mutableState.value.phase == WindowPhase.Open) closeOperationId = operationId
    }

    fun cancelPreparedClose(operationId: WindowOperationId) {
        if (mutableState.value.phase == WindowPhase.Open && closeOperationId == operationId) {
            closeOperationId = null
        }
    }

    fun createCloseRequest(stamp: EventStamp): Pair<RuntimeWindow, WindowEvent.CloseRequested>? {
        if (mutableState.value.phase != WindowPhase.Open || activeCloseRequest != null) return null
        val closeRequest = RuntimeCloseRequest(RuntimeProcessIds.nextWindowCloseRequestId())
        activeCloseRequest = closeRequest
        return this to WindowEvent.CloseRequested(
            requestId = closeRequest.id,
            reason = WindowCloseReason.User,
            canReject = true,
            deadline = null,
            stateRevision = mutableState.value.revision,
            stamp = stamp,
        )
    }

    fun publish(event: WindowEvent) {
        check(mutableEvents.tryEmit(event)) { "window event publication failed" }
    }

    fun closeResponseFor(
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): WindowCloseResponseOutcome? {
        if (activeCloseRequest?.id == requestId) return null
        val resolved = resolvedCloseRequest?.takeIf { it.requestId == requestId } ?: return null
        if (
            mutableState.value.phase == WindowPhase.Closed ||
            (!resolved.committed && mutableState.value.phase != WindowPhase.Open)
        ) {
            return WindowCloseResponseOutcome.TooLate
        }
        return if (resolved.decision == decision) resolved.outcome else WindowCloseResponseOutcome.AlreadyResolved
    }

    fun resolveCloseRequest(
        request: RuntimeCloseRequest,
        decision: WindowCloseDecision,
        outcome: WindowCloseResponseOutcome,
        committed: Boolean,
    ) {
        if (activeCloseRequest !== request) return
        activeCloseRequest = null
        resolvedCloseRequest = ResolvedCloseRequest(request.id, decision, outcome, committed)
    }

    fun markNativeCloseCommitted() {
        activeCloseRequest?.let { pending ->
            resolvedCloseRequest = ResolvedCloseRequest(
                pending.id,
                null,
                WindowCloseResponseOutcome.TooLate,
                committed = true,
            )
            activeCloseRequest = null
        }
    }

    fun beginClosing(
        operationId: WindowOperationId,
        reason: WindowCloseReason,
        stamp: EventStamp,
    ) {
        val current = mutableState.value
        if (current.phase != WindowPhase.Open) return
        closeOperationId = operationId
        mutableState.value = current.copy(
            phase = WindowPhase.Closing,
            revision = WindowRevision(current.revision.value + 1L),
        )
        surface.detach()
        publish(
            WindowEvent.Closing(
                reason = reason,
                stateRevision = mutableState.value.revision,
                operationId = operationId,
                stamp = stamp,
            ),
        )
    }

    fun finishClosing() {
        val current = mutableState.value
        if (current.phase == WindowPhase.Closed) return
        check(current.phase == WindowPhase.Closing) { "window must enter Closing before its terminal close" }
        mutableState.value = current.copy(
            phase = WindowPhase.Closed,
            revision = WindowRevision(current.revision.value + 1L),
        )
    }
}

internal data class RuntimeCloseRequest(
    val id: WindowCloseRequestId,
)

private data class ResolvedCloseRequest(
    val requestId: WindowCloseRequestId,
    val decision: WindowCloseDecision?,
    val outcome: WindowCloseResponseOutcome,
    val committed: Boolean,
)

private fun initialWindowState(spec: WindowSpec): WindowState = WindowState(
    phase = WindowPhase.Open,
    title = spec.title,
    outerBounds = null,
    contentSize = spec.contentSize,
    minimumSize = spec.minimumSize,
    maximumSize = spec.maximumSize,
    resizable = spec.resizable,
    fullscreen = spec.fullscreen,
    decorations = spec.decorations,
    systemButtons = spec.systemButtons,
    level = spec.level,
    transparent = spec.transparent,
    blurBehind = spec.blurBehind,
    icon = spec.icon,
    contentProtection = spec.contentProtection,
    revision = WindowRevision(0L),
)

private fun windowCapabilities(publicWindowCapabilities: Boolean): WindowCapabilities = WindowCapabilities(
    title = unsupported(KadreOperation.UpdateWindow),
    outerPosition = unsupported(KadreOperation.UpdateWindow),
    contentSize = unsupported(KadreOperation.UpdateWindow),
    minimumSize = unsupported(KadreOperation.UpdateWindow),
    maximumSize = unsupported(KadreOperation.UpdateWindow),
    resizable = unsupported(KadreOperation.UpdateWindow),
    fullscreen = unsupported(KadreOperation.UpdateWindow),
    decorations = unsupported(KadreOperation.UpdateWindow),
    systemButtons = unsupported(KadreOperation.UpdateWindow),
    level = unsupported(KadreOperation.UpdateWindow),
    transparency = unsupported(KadreOperation.UpdateWindow),
    blurBehind = unsupported(KadreOperation.UpdateWindow),
    icon = unsupported(KadreOperation.UpdateWindow),
    attention = unsupported(KadreOperation.RequestWindowAttention),
    contentProtection = unsupported(KadreOperation.UpdateWindow),
    closeInterception = if (publicWindowCapabilities) {
        Capability.Supported(Unit, FeatureAvailability.Available)
    } else {
        unsupported(KadreOperation.RespondToCloseRequest)
    },
    platformAccess = if (publicWindowCapabilities) {
        Capability.Supported(Unit, FeatureAvailability.Available)
    } else {
        unsupported(KadreOperation.PlatformWindowAccess)
    },
)

private fun changedProperties(update: WindowUpdate): List<WindowProperty> = buildList {
    if (update.title !is PropertyChange.Unchanged) add(WindowProperty.Title)
    if (update.outerPosition !is PropertyChange.Unchanged) add(WindowProperty.OuterPosition)
    if (update.contentSize !is PropertyChange.Unchanged) add(WindowProperty.ContentSize)
    if (update.minimumSize !is PropertyChange.Unchanged) add(WindowProperty.MinimumSize)
    if (update.maximumSize !is PropertyChange.Unchanged) add(WindowProperty.MaximumSize)
    if (update.resizable !is PropertyChange.Unchanged) add(WindowProperty.Resizable)
    if (update.fullscreen !is PropertyChange.Unchanged) add(WindowProperty.Fullscreen)
    if (update.decorations !is PropertyChange.Unchanged) add(WindowProperty.Decorations)
    if (update.systemButtons !is PropertyChange.Unchanged) add(WindowProperty.SystemButtons)
    if (update.level !is PropertyChange.Unchanged) add(WindowProperty.Level)
    if (update.transparency !is PropertyChange.Unchanged) add(WindowProperty.Transparency)
    if (update.blurBehind !is PropertyChange.Unchanged) add(WindowProperty.Blur)
    if (update.icon !is PropertyChange.Unchanged) add(WindowProperty.Icon)
    if (update.contentProtection !is PropertyChange.Unchanged) add(WindowProperty.ContentProtection)
}

private fun <T> unsupported(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))

private val REQUEST_WINDOW_INVALID_REQUEST_FIELDS: Set<String> = setOf(
    "title",
    "contentSize",
    "minimumSize",
    "maximumSize",
    "sizeConstraints",
    "fullscreen",
    "icon",
    "element",
    "element.ownerDocument",
    "parentScope",
)
