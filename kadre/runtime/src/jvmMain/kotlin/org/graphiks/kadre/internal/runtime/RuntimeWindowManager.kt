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
import org.graphiks.kadre.diagnostics.Capability
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
import org.graphiks.kadre.window.WindowCloseRequestId
import org.graphiks.kadre.window.WindowCloseResponseOutcome
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
) : WindowManager, AutoCloseable {
    private val lock = Any()
    private val pending = linkedMapOf<WindowRequestId, PendingWindow>()
    private val committed = linkedMapOf<WindowRequestId, CommittedWindow>()
    private var nextAdmissionOrder = 0L
    private var reservedWindowSlots = 0
    private var managerRevision = 0L
    private var closed = false
    private val mutableState = MutableStateFlow(
        WindowManagerState(
            primary = null,
            windows = emptyList(),
            capabilities = WindowManagerCapabilities(
                unsupported(KadreOperation.RequestWindow),
            ),
            revision = WindowManagerRevision(0L),
        ),
    )
    private val stimulusSink = object : WindowCommandStimulusSink {
        override fun commit(requestId: WindowRequestId, windowId: WindowId, owner: WindowPeerOwner) {
            acceptCommit(requestId, windowId, owner)
        }

        override fun fail(requestId: WindowRequestId, failure: KadreFailure) {
            acceptFailure(requestId, failure)
        }

        override fun nativeClosed(requestId: WindowRequestId) {
            acceptNativeClose(requestId)
        }
    }

    override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

    // Narrow module-internal seam for deterministic ownership-race tests.
    internal fun requestForTesting(requestId: WindowRequestId): RuntimeWindowRequest? = synchronized(lock) {
        pending[requestId]?.request ?: committed[requestId]?.request
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
                val closeOutcome = if (!record.pendingCancellationIssued) {
                    record.pendingCancellationIssued = true
                    guardPort("pending-close-exception") {
                        commandPort.requestPendingCancellation(
                            PendingWindowCancellationCommand(record.request.id),
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
            commandPort.requestPendingCancellation(PendingWindowCancellationCommand(request.id))
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
            val issueCancellation = !record.pendingCancellationIssued
            record.pendingCancellationIssued = true
            removePendingLocked(record)
            val portOutcome = if (issueCancellation) {
                guardPort("pending-close-exception") {
                    commandPort.requestPendingCancellation(PendingWindowCancellationCommand(request.id))
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
    ): KadreResult<WindowCloseResponseOutcome> = synchronized(lock) {
        if (window.currentState().phase != WindowPhase.Open) {
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        } else {
            KadreResult.Failure(KadreFailure.InvalidRequest("requestId"))
        }
    }

    internal suspend fun closeWindow(window: RuntimeWindow): KadreResult<WindowCloseOutcome> = synchronized(lock) {
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
                            window.beginClosing(operationId)
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

    private fun abandonBeforeHandoff(request: RuntimeWindowRequest) {
        if (!request.claimPreHandoffCancellation()) return
        synchronized(lock) {
            val pendingRecord = pending[request.id]
            if (pendingRecord != null) {
                val issueCancellation = !pendingRecord.pendingCancellationIssued
                pendingRecord.pendingCancellationIssued = true
                removePendingLocked(pendingRecord)
                val portOutcome = if (issueCancellation) {
                    guardPort("pending-close-exception") {
                        commandPort.requestPendingCancellation(PendingWindowCancellationCommand(request.id))
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
                return
            }
            committed[request.id]?.let { record ->
                forceCloseLocked(record)
            }
            request.finishPreHandoffCancellation(WindowRequestOutcome.RequesterDetached)
        }
    }

    private fun acceptCommit(requestId: WindowRequestId, windowId: WindowId, owner: WindowPeerOwner) {
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
                    } else if (record.preparedOwner !== owner) {
                        closeOwner = owner
                    }
                    return@synchronized
                }
                commitPendingLocked(record, windowId, owner)
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

    private fun acceptNativeClose(requestId: WindowRequestId) {
        try {
            var owner: WindowPeerOwner? = null
            synchronized(lock) {
                val record = committed.remove(requestId)
                if (record != null) {
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
        }
    }

    private fun forceCloseLocked(record: CommittedWindow) {
        if (committed.remove(record.request.id) == null) return
        if (record.window.currentState().phase == WindowPhase.Open) {
            record.window.beginClosing(RuntimeProcessIds.nextWindowOperationId())
        }
        if (!record.closeCommandSent) {
            record.closeCommandSent = true
            val close = guardPort("opened-close-exception") {
                commandPort.requestOpenedClose(record.closeCommand())
            }
            when (close) {
                is GuardedCall.Success -> when (val outcome = close.value) {
                    OpenedWindowCloseOutcome.Accepted -> Unit
                    is OpenedWindowCloseOutcome.TemporarilyUnavailable -> safeReport(
                        KadreException(KadreFailure.TemporarilyUnavailable(outcome.retryable)),
                    )

                    is OpenedWindowCloseOutcome.PlatformFailure -> safeReport(KadreException(outcome.failure))
                }

                is GuardedCall.Failure -> Unit
            }
        }
        record.window.finishClosing()
        releaseWindowSlotsLocked(1)
        publishMembershipLocked()
        safeCloseOwner(record.owner)
    }

    private fun finishOpenDispatchLocked(record: PendingWindow) {
        if (pending[record.request.id] !== record) return
        record.openDispatching = false
        val owner = record.preparedOwner ?: return
        record.preparedOwner = null
        commitPendingLocked(record, record.request.windowId, owner)
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

    private fun commitPendingLocked(record: PendingWindow, windowId: WindowId, owner: WindowPeerOwner) {
        if (pending.remove(record.request.id) !== record) {
            safeCloseOwner(owner)
            return
        }
        val surface = MinimalWindowSurface(RuntimeProcessIds.nextSurfaceId(), record.spec.contentSize)
        val window = RuntimeWindow(
            requestId = record.request.id,
            id = windowId,
            spec = record.spec,
            surface = surface,
            manager = this,
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
        val windows = committed.values
            .sortedBy(CommittedWindow::admissionOrder)
            .map(CommittedWindow::window)
        managerRevision += 1L
        mutableState.value = mutableState.value.copy(
            primary = windows.firstOrNull(),
            windows = windows,
            revision = WindowManagerRevision(managerRevision),
        )
    }

    private data class PendingWindow(
        val request: RuntimeWindowRequest,
        val spec: WindowSpec,
        val admissionOrder: Long,
        var cancellationOutcome: WindowCancellationOutcome? = null,
        var pendingCancellationIssued: Boolean = false,
        var openDispatching: Boolean = true,
        var preparedOwner: WindowPeerOwner? = null,
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
) : Window {
    private val mutableState = MutableStateFlow(initialWindowState(spec))
    private val mutableCapabilities = MutableStateFlow(unsupportedWindowCapabilities())
    private val mutableEvents = MutableSharedFlow<WindowEvent>()

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
    ): KadreResult<WindowCloseResponseOutcome> = manager.respondToCloseRequest(this)

    fun currentState(): WindowState = mutableState.value

    fun prepareClose(operationId: WindowOperationId) {
        if (mutableState.value.phase == WindowPhase.Open) closeOperationId = operationId
    }

    fun cancelPreparedClose(operationId: WindowOperationId) {
        if (mutableState.value.phase == WindowPhase.Open && closeOperationId == operationId) {
            closeOperationId = null
        }
    }

    fun beginClosing(operationId: WindowOperationId) {
        val current = mutableState.value
        if (current.phase != WindowPhase.Open) return
        closeOperationId = operationId
        mutableState.value = current.copy(
            phase = WindowPhase.Closing,
            revision = WindowRevision(current.revision.value + 1L),
        )
        surface.detach()
    }

    fun finishClosing() {
        val current = mutableState.value
        if (current.phase == WindowPhase.Closed) return
        if (current.phase == WindowPhase.Open) {
            beginClosing(RuntimeProcessIds.nextWindowOperationId())
        }
        val closing = mutableState.value
        mutableState.value = closing.copy(
            phase = WindowPhase.Closed,
            revision = WindowRevision(closing.revision.value + 1L),
        )
    }
}

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

private fun unsupportedWindowCapabilities(): WindowCapabilities = WindowCapabilities(
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
    closeInterception = unsupported(KadreOperation.RespondToCloseRequest),
    platformAccess = unsupported(KadreOperation.PlatformWindowAccess),
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
