package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical
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
    private val surfaceCommandPort: SurfaceCommandPort = UnsupportedSurfaceCommandPort,
    private val platform: KadrePlatform,
    private val failureReporter: RuntimeFailureReporter,
    private val publicWindowCapabilities: Boolean = false,
    private val publicSurfaceCapabilities: Boolean = false,
    private val enabledSurfaceCapabilities: SurfaceCapabilities = unsupportedSurfaceCapabilities(),
    private val onLastWindowClosed: (() -> Unit)? = null,
) : WindowManager, AutoCloseable {
    private val lock = Any()
    private val pending = linkedMapOf<WindowRequestId, PendingWindow>()
    private val committed = linkedMapOf<WindowRequestId, CommittedWindow>()
    private val dispatchedWindowUpdates = linkedMapOf<WindowOperationId, RuntimeWindow>()
    private val surfaces = linkedMapOf<SurfaceId, RuntimeWindowSurface>()
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
    private var windowDeliveryPolicy: WindowDeliveryPolicy = KadrePolicies.Default.window
    private var surfaceDeliveryPolicy: WindowDeliveryPolicy = KadrePolicies.Default.window
    private var surfaceInputDeliveryPolicy: InputDeliveryPolicy = KadrePolicies.Default.input
    private val isolatedEventCollectorAllocator = lazy {
        RuntimeEventCollectorAllocator(resources.maxEventCollectorsPerSession)
    }
    private var sessionEventCollectorAllocator: RuntimeEventCollectorAllocator? = null
    private var sessionMaxCollectorsPerFlow = resources.maxEventCollectorsPerFlow
    private var surfaceSessionFailureHandler: (KadreFailure) -> Unit = { failure ->
        safeReport(KadreException(failure))
    }
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
            initialSurfaceSnapshot: SurfaceInitialSnapshot?,
            owner: WindowPeerOwner,
            onSurfaceReady: () -> Unit,
        ) {
            acceptCommit(requestId, windowId, effectiveSpec, initialSurfaceSnapshot, owner, onSurfaceReady)
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
    private val updateStimulusSink = WindowUpdateCommandStimulusSink(::acceptWindowUpdateStimulus)

    override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

    // Narrow module-internal seam for deterministic ownership-race tests.
    internal fun requestForTesting(requestId: WindowRequestId): RuntimeWindowRequest? = synchronized(lock) {
        pending[requestId]?.request ?: committed[requestId]?.request
    }

    internal fun installSessionConfiguration(
        deliveryPolicy: WindowDeliveryPolicy,
        inputDeliveryPolicy: InputDeliveryPolicy,
        source: () -> EventStamp,
        sessionFailureHandler: (KadreFailure) -> Unit,
        collectorAllocator: RuntimeEventCollectorAllocator,
        maxCollectorsPerFlow: Int,
    ) {
        synchronized(lock) {
            check(sessionEventStampSource == null) { "window event stamp source was already installed" }
            check(pending.isEmpty() && committed.isEmpty()) { "window event stamp source must be installed before admission" }
            sessionEventStampSource = source
            windowDeliveryPolicy = deliveryPolicy
            surfaceDeliveryPolicy = deliveryPolicy
            surfaceInputDeliveryPolicy = inputDeliveryPolicy
            surfaceSessionFailureHandler = sessionFailureHandler
            sessionEventCollectorAllocator = collectorAllocator
            sessionMaxCollectorsPerFlow = maxCollectorsPerFlow
        }
    }

    /**
     * Unstable backend SPI accepting one immutable observation or acknowledgement.
     *
     * Returning `false` means the value was duplicate, unknown or terminal and requires no
     * backend retry.
     */
    public fun acceptSurfaceStimulus(stimulus: SurfaceStimulus): Boolean {
        val surface = synchronized(lock) { surfaces[stimulus.surfaceId] } ?: return false
        return surface.accept(stimulus)
    }

    /**
     * Admits an uncorrelated native geometry observation for one live window.
     *
     * The runtime remains authoritative for revision assignment and event publication.
     */
    public fun acceptWindowGeometryObservation(windowId: WindowId, state: WindowState): Boolean {
        val window = synchronized(lock) {
            committed.values.firstOrNull { it.window.id == windowId }?.window
        } ?: return false
        return window.observeNativeUpdate(state)
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
                    val surfaceId = RuntimeProcessIds.nextSurfaceId()
                    request = RuntimeWindowRequest(requestId, windowId, this)
                    val record = PendingWindow(
                        request = request,
                        surfaceId = surfaceId,
                        spec = spec,
                        admissionOrder = nextAdmissionOrder++,
                    )
                    pending[requestId] = record
                    reservedWindowSlots += 1
                    when (
                        val dispatch = guardPort("request-open-exception") {
                            commandPort.requestOpen(
                                WindowOpenCommand(requestId, windowId, surfaceId, spec, stimulusSink),
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
    ): KadreResult<WindowUpdateOutcome> = window.applyUpdate(update)

    internal fun terminaliseWindowEventDelivery(window: RuntimeWindow) {
        synchronized(lock) {
            committed[window.requestId]?.let(::forceCloseLocked)
        }
        drainLastWindowStopProposal()
    }

    internal fun dispatchWindowUpdate(window: RuntimeWindow, pending: PendingWindowUpdate) {
        val command = WindowUpdateCommand(
            windowId = window.id,
            operationId = pending.operationId,
            expectedRevision = pending.expectedRevision,
            update = pending.update,
            stimulusSink = updateStimulusSink,
        )
        val dispatchFailure = synchronized(lock) {
            if (!window.beginNativeUpdateDispatch(pending.operationId)) return
            dispatchedWindowUpdates[pending.operationId] = window
            when (val result = guardPort("window-update-exception") { commandPort.requestUpdate(command) }) {
                is GuardedCall.Success -> null
                is GuardedCall.Failure -> {
                    dispatchedWindowUpdates.remove(pending.operationId)
                    result.failure
                }
            }
        }
        dispatchFailure?.let { failure ->
            window.rejectDispatchedUpdate(pending.operationId, failure)
        }
    }

    internal fun withdrawWindowUpdate(window: RuntimeWindow, operationId: WindowOperationId) {
        val outcome = guardPort("window-update-cancellation-exception") {
            commandPort.requestUpdateCancellation(WindowUpdateCancellationCommand(operationId))
        }
        if (outcome is GuardedCall.Success && outcome.value == WindowUpdateCancellationOutcome.CancelledBeforeCommit) {
            synchronized(lock) { dispatchedWindowUpdates.remove(operationId) }
            window.withdrawDispatchedUpdate(operationId)
        }
    }

    private fun acceptWindowUpdateStimulus(stimulus: WindowUpdateCommandStimulus) {
        val operationId = when (stimulus) {
            is WindowUpdateCommandStimulus.Applied -> stimulus.operationId
            is WindowUpdateCommandStimulus.Rejected -> stimulus.operationId
        }
        val window = synchronized(lock) { dispatchedWindowUpdates.remove(operationId) } ?: return
        when (stimulus) {
            is WindowUpdateCommandStimulus.Applied -> window.applyNativeUpdate(stimulus.operationId, stimulus.state)
            is WindowUpdateCommandStimulus.Rejected -> {
                safeReport(stimulus.error)
                window.rejectDispatchedUpdate(stimulus.operationId, platformFailure("window-update-rejected"))
            }
        }
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
        initialSurfaceSnapshot: SurfaceInitialSnapshot?,
        owner: WindowPeerOwner,
        onSurfaceReady: () -> Unit,
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
                        record.preparedInitialSurfaceSnapshot = initialSurfaceSnapshot
                        record.preparedSurfaceReady = onSurfaceReady
                    } else if (record.preparedOwner !== owner) {
                        closeOwner = owner
                    }
                    return@synchronized
                }
                commitPendingLocked(
                    record,
                    windowId,
                    effectiveSpec,
                    initialSurfaceSnapshot,
                    owner,
                    onSurfaceReady,
                )
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
                    surfaces.remove(record.window.surface.id)
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
        surfaces.remove(record.window.surface.id)
        releaseWindowSlotsLocked(1)
        publishMembershipLocked()
        safeCloseOwner(record.owner)
    }

    private fun finishOpenDispatchLocked(record: PendingWindow) {
        if (pending[record.request.id] !== record) return
        record.openDispatching = false
        val owner = record.preparedOwner ?: return
        val effectiveSpec = checkNotNull(record.preparedEffectiveSpec)
        val initialSurfaceSnapshot = record.preparedInitialSurfaceSnapshot
        val onSurfaceReady = checkNotNull(record.preparedSurfaceReady)
        record.preparedOwner = null
        record.preparedEffectiveSpec = null
        record.preparedInitialSurfaceSnapshot = null
        record.preparedSurfaceReady = null
        commitPendingLocked(
            record,
            record.request.windowId,
            effectiveSpec,
            initialSurfaceSnapshot,
            owner,
            onSurfaceReady,
        )
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
        initialSurfaceSnapshot: SurfaceInitialSnapshot?,
        owner: WindowPeerOwner,
        onSurfaceReady: () -> Unit,
    ) {
        if (publicSurfaceCapabilities && initialSurfaceSnapshot == null) {
            removePendingLocked(record)
            safeCloseOwner(owner)
            val failure = platformFailure("missing-initial-surface-snapshot")
            safeReport(KadreException(failure))
            record.request.terminate(WindowRequestOutcome.Rejected(failure))
            return
        }
        if (pending.remove(record.request.id) !== record) {
            safeCloseOwner(owner)
            return
        }
        val collectorAllocator = sessionEventCollectorAllocator ?: isolatedEventCollectorAllocator.value
        val surface = RuntimeWindowSurface(
            id = record.surfaceId,
            initialSnapshot = initialSurfaceSnapshot ?: fallbackSurfaceSnapshot(effectiveSpec),
            commandPort = surfaceCommandPort,
            commandsEnabled = publicSurfaceCapabilities,
            enabledCapabilities = enabledSurfaceCapabilities,
            eventStampSource = ::nextEventStamp,
            platform = platform,
            failureReporter = failureReporter,
            deliveryPolicy = surfaceDeliveryPolicy,
            inputDeliveryPolicy = surfaceInputDeliveryPolicy,
            maxCollectorsPerFlow = sessionMaxCollectorsPerFlow,
            collectorAllocator = collectorAllocator,
            sessionFailureHandler = surfaceSessionFailureHandler,
        )
        val window = RuntimeWindow(
            requestId = record.request.id,
            id = windowId,
            spec = effectiveSpec,
            surface = surface,
            manager = this,
            publicWindowCapabilities = publicWindowCapabilities,
            eventCollectorGate = collectorAllocator.newGate(sessionMaxCollectorsPerFlow),
            deliveryPolicy = windowDeliveryPolicy,
            eventStampSource = ::nextEventStamp,
            failureReporter = failureReporter,
            sessionFailureHandler = surfaceSessionFailureHandler,
        )
        committed[record.request.id] = CommittedWindow(
            request = record.request,
            window = window,
            owner = owner,
            admissionOrder = record.admissionOrder,
        )
        check(surfaces.put(surface.id, surface) == null) { "duplicate runtime surface" }
        publishMembershipLocked()
        record.request.terminate(WindowRequestOutcome.OpenedHere(window))
        onSurfaceReady()
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
        val surfaceId: SurfaceId,
        val spec: WindowSpec,
        val admissionOrder: Long,
        var cancellationOutcome: WindowCancellationOutcome? = null,
        var pendingCancellationIssued: Boolean = false,
        var openDispatching: Boolean = true,
        var preparedOwner: WindowPeerOwner? = null,
        var preparedEffectiveSpec: WindowSpec? = null,
        var preparedInitialSurfaceSnapshot: SurfaceInitialSnapshot? = null,
        var preparedSurfaceReady: (() -> Unit)? = null,
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

private fun fallbackSurfaceSnapshot(effectiveSpec: WindowSpec): SurfaceInitialSnapshot = SurfaceInitialSnapshot(
    metrics = SurfaceMetrics(
        logicalSize = effectiveSpec.contentSize,
        physicalSize = effectiveSpec.contentSize.toPhysical(1.0),
        scaleFactor = 1.0,
        safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    ),
    focus = SurfaceFocus.Unfocused,
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Unknown,
    theme = SurfaceTheme.Unknown,
)

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
    override val surface: RuntimeWindowSurface,
    private val manager: RuntimeWindowManager,
    publicWindowCapabilities: Boolean,
    eventCollectorGate: RuntimeEventCollectorGate,
    deliveryPolicy: WindowDeliveryPolicy,
    private val eventStampSource: () -> EventStamp,
    failureReporter: RuntimeFailureReporter,
    sessionFailureHandler: (KadreFailure) -> Unit,
) : Window, RuntimeDesktopWindowHandleAccess {
    private val initialState = initialWindowState(spec)
    private val mutableState = MutableStateFlow(initialState)
    private val mutableCapabilities = MutableStateFlow(windowCapabilities(publicWindowCapabilities))
    private val eventFlow = RuntimeWindowEventFlow(
        policy = deliveryPolicy,
        eventCollectorGate = eventCollectorGate,
        failureReporter = failureReporter,
        sessionFailureHandler = sessionFailureHandler,
        closeWindow = { manager.terminaliseWindowEventDelivery(this) },
    )
    private val updateLock = Any()
    private val pendingUpdates = ArrayDeque<PendingWindowUpdate>()
    private var dispatchedUpdate: PendingWindowUpdate? = null
    internal var activeCloseRequest: RuntimeCloseRequest? = null
        private set
    private var resolvedCloseRequest: ResolvedCloseRequest? = null

    override val state: StateFlow<WindowState> = mutableState.asStateFlow()
    override val capabilities: StateFlow<WindowCapabilities> = mutableCapabilities.asStateFlow()
    override val events: Flow<WindowEvent> = eventFlow.events
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

    suspend fun applyUpdate(update: WindowUpdate): KadreResult<WindowUpdateOutcome> {
        currentCoroutineContext().ensureActive()
        val pending: PendingWindowUpdate
        val immediate: KadreResult<WindowUpdateOutcome>?
        synchronized(updateLock) {
            val current = mutableState.value
            if (current.phase != WindowPhase.Open) {
                return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            }
            val operationId = RuntimeProcessIds.nextWindowOperationId()
            val candidate = candidateFor(update, current, initialState)
                ?: return KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints"))
            val rejected = changedProperties(update)
                .filterNot(::isRuntimeGeometryProperty)
                .map { RejectedWindowField(it, KadreFailure.Unsupported(KadreOperation.UpdateWindow)) }
            val geometryChanged = geometryChanged(current, candidate)
            if (!geometryChanged) {
                update.expectedRevision?.let { expected ->
                    if (expected != current.revision) {
                        return KadreResult.Failure(KadreFailure.StaleRevision(expected.value, current.revision.value))
                    }
                }
                immediate = KadreResult.Success(updateOutcome(operationId, current, rejected))
                pending = PendingWindowUpdate(operationId, null, update, rejected)
            } else {
                immediate = null
                pending = PendingWindowUpdate(
                    operationId = operationId,
                    expectedRevision = update.expectedRevision,
                    update = geometryOnly(update),
                    rejected = rejected,
                )
                pendingUpdates.addLast(pending)
            }
        }
        immediate?.let { return it }
        dispatchNextUpdate()
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) cancelPendingUpdate(pending)
        }
        return try {
            pending.result.await()
        } catch (cancelled: CancellationException) {
            cancelPendingUpdate(pending)
            throw cancelled
        } finally {
            cancellationHandle?.dispose()
        }
    }

    fun applyNativeUpdate(operationId: WindowOperationId, state: WindowState) {
        var publication: WindowStatePublication? = null
        var completion: PendingWindowUpdate? = null
        var outcome: KadreResult<WindowUpdateOutcome>? = null
        synchronized(updateLock) {
            val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return
            dispatchedUpdate = null
            val lifecycle = mutableState.value
            val effective = if (lifecycle.phase == WindowPhase.Open) state.copy(
                revision = WindowRevision(lifecycle.revision.value + 1L),
            ) else {
                state.copy(
                    phase = lifecycle.phase,
                    revision = WindowRevision(lifecycle.revision.value + 1L),
                )
            }
            mutableState.value = effective
            publication = WindowStatePublication(lifecycle, effective, operationId)
            completion = pending
            outcome = KadreResult.Success(updateOutcome(operationId, effective, pending.rejected))
        }
        publishStatePublication(checkNotNull(publication))
        checkNotNull(completion).result.complete(checkNotNull(outcome))
        dispatchNextUpdate()
    }

    /** Accepts an uncorrelated native observation after the peer has filtered its own setters. */
    fun observeNativeUpdate(state: WindowState): Boolean {
        val publication = synchronized(updateLock) {
            val lifecycle = mutableState.value
            if (lifecycle.phase != WindowPhase.Open) return@synchronized null
            val candidate = try {
                lifecycle.copy(
                    contentSize = state.contentSize,
                    minimumSize = state.minimumSize,
                    maximumSize = state.maximumSize,
                    resizable = state.resizable,
                )
            } catch (_: IllegalArgumentException) {
                return@synchronized null
            }
            if (candidate == lifecycle) return@synchronized null
            val effective = candidate.copy(revision = WindowRevision(lifecycle.revision.value + 1L))
            mutableState.value = effective
            WindowStatePublication(lifecycle, effective, operationId = null)
        } ?: return false
        publishStatePublication(publication)
        return true
    }

    fun rejectDispatchedUpdate(operationId: WindowOperationId, failure: KadreFailure) {
        synchronized(updateLock) {
            val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return
            dispatchedUpdate = null
            pending.result.complete(KadreResult.Failure(failure))
        }
        dispatchNextUpdate()
    }

    fun withdrawDispatchedUpdate(operationId: WindowOperationId) {
        synchronized(updateLock) {
            val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return
            dispatchedUpdate = null
            pending.cancelled = true
        }
        dispatchNextUpdate()
    }

    private fun dispatchNextUpdate() {
        var next: PendingWindowUpdate? = null
        synchronized(updateLock) {
            if (dispatchedUpdate != null) return
            while (pendingUpdates.isNotEmpty()) {
                val candidate = pendingUpdates.removeFirst()
                if (candidate.cancelled) continue
                val current = mutableState.value
                if (current.phase != WindowPhase.Open) {
                    candidate.result.complete(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)))
                    continue
                }
                val expected = candidate.expectedRevision
                if (expected != null && expected != current.revision) {
                    candidate.result.complete(
                        KadreResult.Failure(KadreFailure.StaleRevision(expected.value, current.revision.value)),
                    )
                    continue
                }
                val effectiveCandidate = candidateFor(candidate.update, current, initialState)
                if (effectiveCandidate == null) {
                    candidate.result.complete(KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints")))
                    continue
                }
                if (!geometryChanged(current, effectiveCandidate)) {
                    candidate.result.complete(KadreResult.Success(updateOutcome(candidate.operationId, current, candidate.rejected)))
                    continue
                }
                if (candidate.result.isCompleted) continue
                dispatchedUpdate = candidate
                next = candidate
                break
            }
        }
        next?.let { manager.dispatchWindowUpdate(this, it) }
    }

    private fun cancelPendingUpdate(pending: PendingWindowUpdate) {
        var withdrawDispatched = false
        synchronized(updateLock) {
            if (dispatchedUpdate === pending) {
                if (pending.cancellationRequested) return
                pending.cancellationRequested = true
                withdrawDispatched = true
                return@synchronized
            }
            pending.cancelled = true
            pendingUpdates.remove(pending)
        }
        if (withdrawDispatched) manager.withdrawWindowUpdate(this, pending.operationId)
    }

    fun beginNativeUpdateDispatch(operationId: WindowOperationId): Boolean = synchronized(updateLock) {
        val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return@synchronized false
        if (mutableState.value.phase == WindowPhase.Open) {
            pending.nativeDispatchStarted = true
            true
        } else {
            dispatchedUpdate = null
            pending.result.complete(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)))
            false
        }
    }

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
        eventFlow.publish(event)
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
        val closingState = synchronized(updateLock) {
            val current = mutableState.value
            if (current.phase != WindowPhase.Open) return
            closeOperationId = operationId
            while (pendingUpdates.isNotEmpty()) {
                pendingUpdates.removeFirst().result.complete(
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)),
                )
            }
            dispatchedUpdate?.takeIf { !it.nativeDispatchStarted }?.let { pending ->
                dispatchedUpdate = null
                pending.result.complete(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)))
            }
            current.copy(
                phase = WindowPhase.Closing,
                revision = WindowRevision(current.revision.value + 1L),
            ).also { mutableState.value = it }
        }
        surface.detach()
        publish(
            WindowEvent.Closing(
                reason = reason,
                stateRevision = closingState.revision,
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
        eventFlow.close()
    }

    private fun publishStatePublication(publication: WindowStatePublication) {
        val before = publication.before
        val effective = publication.effective
        if (
            before.contentSize != effective.contentSize ||
            before.minimumSize != effective.minimumSize ||
            before.maximumSize != effective.maximumSize
        ) {
            publish(
                WindowEvent.GeometryChanged(
                    state = effective,
                    operationId = publication.operationId,
                    stamp = eventStampSource(),
                ),
            )
        }
        if (before.resizable != effective.resizable) {
            publish(
                WindowEvent.PropertiesChanged(
                    state = effective,
                    changed = setOf(WindowProperty.Resizable),
                    operationId = publication.operationId,
                    stamp = eventStampSource(),
                ),
            )
        }
    }
}

private data class WindowStatePublication(
    val before: WindowState,
    val effective: WindowState,
    val operationId: WindowOperationId?,
)

internal data class PendingWindowUpdate(
    val operationId: WindowOperationId,
    val expectedRevision: WindowRevision?,
    val update: WindowUpdate,
    val rejected: List<RejectedWindowField>,
    val result: CompletableDeferred<KadreResult<WindowUpdateOutcome>> = CompletableDeferred(),
    var cancelled: Boolean = false,
    var cancellationRequested: Boolean = false,
    var nativeDispatchStarted: Boolean = false,
)

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

private fun isRuntimeGeometryProperty(property: WindowProperty): Boolean = property in setOf(
    WindowProperty.ContentSize,
    WindowProperty.MinimumSize,
    WindowProperty.MaximumSize,
    WindowProperty.Resizable,
)

private fun candidateFor(
    update: WindowUpdate,
    current: WindowState,
    initial: WindowState,
): WindowState? = try {
    current.copy(
        contentSize = resolveContentSize(update.contentSize, current.contentSize, initial.contentSize),
        minimumSize = resolveOptionalSize(update.minimumSize, current.minimumSize, initial.minimumSize),
        maximumSize = resolveOptionalSize(update.maximumSize, current.maximumSize, initial.maximumSize),
        resizable = resolveResizable(update.resizable, current.resizable, initial.resizable),
    )
} catch (_: IllegalArgumentException) {
    null
}

private fun resolveContentSize(
    change: PropertyChange<org.graphiks.kadre.surface.LogicalSize>,
    current: org.graphiks.kadre.surface.LogicalSize,
    initial: org.graphiks.kadre.surface.LogicalSize,
): org.graphiks.kadre.surface.LogicalSize = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear -> initial
    PropertyChange.Unchanged -> current
}

private fun resolveOptionalSize(
    change: PropertyChange<org.graphiks.kadre.surface.LogicalSize>,
    current: org.graphiks.kadre.surface.LogicalSize?,
    initial: org.graphiks.kadre.surface.LogicalSize?,
): org.graphiks.kadre.surface.LogicalSize? = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear -> initial
    PropertyChange.Unchanged -> current
}

private fun resolveResizable(
    change: PropertyChange<Boolean>,
    current: Boolean,
    initial: Boolean,
): Boolean = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear -> initial
    PropertyChange.Unchanged -> current
}

private fun geometryChanged(current: WindowState, candidate: WindowState): Boolean =
    current.contentSize != candidate.contentSize ||
        current.minimumSize != candidate.minimumSize ||
        current.maximumSize != candidate.maximumSize ||
        current.resizable != candidate.resizable

private fun geometryOnly(update: WindowUpdate): WindowUpdate = WindowUpdate(
    contentSize = update.contentSize,
    minimumSize = update.minimumSize,
    maximumSize = update.maximumSize,
    resizable = update.resizable,
    expectedRevision = update.expectedRevision,
)

private fun updateOutcome(
    operationId: WindowOperationId,
    state: WindowState,
    rejected: List<RejectedWindowField>,
): WindowUpdateOutcome = if (rejected.isEmpty()) {
    WindowUpdateOutcome.Applied(operationId, state)
} else {
    WindowUpdateOutcome.PartiallyApplied(operationId, state, rejected)
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
