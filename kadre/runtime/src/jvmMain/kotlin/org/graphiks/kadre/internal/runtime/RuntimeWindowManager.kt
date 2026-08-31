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
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical
import org.graphiks.kadre.window.FullscreenKind
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.LogicalSizeRange
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
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowLevel
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
import org.graphiks.kadre.window.WindowSystemButtons
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
    private val enabledWindowUpdateCapabilities: Set<WindowProperty> = emptySet(),
    @Suppress("EXPOSED_PARAMETER_TYPE")
    private val attentionPort: WindowAttentionPort? = null,
    private val acceptedAttention: Set<WindowAttention> = emptySet(),
    private val fullscreenAvailabilityFailure: KadreFailure.PlatformFailure? = null,
    private val publicSurfaceCapabilities: Boolean = false,
    private val enabledSurfaceCapabilities: SurfaceCapabilities = unsupportedSurfaceCapabilities(),
    private val onLastWindowClosed: (() -> Unit)? = null,
) : WindowManager, AutoCloseable, RuntimeFullscreenObservationSink {
    private val lock = Any()
    private val pending = linkedMapOf<WindowRequestId, PendingWindow>()
    private val committed = linkedMapOf<WindowRequestId, CommittedWindow>()
    private val dispatchedWindowUpdates = linkedMapOf<WindowOperationId, RuntimeWindow>()
    private val pendingAttentionReleases = mutableListOf<WindowId>()
    internal var beforeWindowUpdateRegistration: (RuntimeWindow, PendingWindowUpdate) -> Unit = { _, _ -> }
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
    private val fullscreenObservationSink = object : WindowFullscreenObservationSink {
        override fun accept(
            windowId: WindowId,
            operationId: WindowOperationId,
            observation: WindowFullscreenObservation,
        ) {
            acceptCorrelatedWindowFullscreenObservation(windowId, operationId, observation)
        }

        override fun beginSelectorInvocation(windowId: WindowId, operationId: WindowOperationId): Boolean =
            beginCorrelatedWindowFullscreenSelectorInvocation(windowId, operationId)

        override fun finishSelectorInvocation(
            windowId: WindowId,
            operationId: WindowOperationId,
            failure: KadreFailure?,
        ) {
            finishCorrelatedWindowFullscreenSelectorInvocation(windowId, operationId, failure)
        }
    }

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
        if (unsupportedInitialWindowProperty(spec) != null) {
            return synchronized(lock) {
                if (closed) {
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
                } else {
                    KadreResult.Success(rejectedWindowRequest())
                }
            }
        }
        if (spec.fullscreen != FullscreenMode.Windowed) {
            return synchronized(lock) {
                if (closed) return@synchronized KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
                when (spec.fullscreen) {
                    FullscreenMode.Borderless -> KadreResult.Failure(KadreFailure.InvalidRequest("fullscreen"))
                    is FullscreenMode.Exclusive -> {
                        KadreResult.Success(rejectedWindowRequest())
                    }
                    FullscreenMode.Windowed -> error("windowed fullscreen was handled before admission")
                }
            }
        }
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
        val portToClose = synchronized(lock) {
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
            attentionPort
        }
        drainAttentionReleases()
        portToClose?.let(::safeCloseAttentionPort)
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
        drainAttentionReleases()
        drainLastWindowStopProposal()
    }

    internal fun dispatchWindowUpdate(window: RuntimeWindow, pending: PendingWindowUpdate) {
        beforeWindowUpdateRegistration(window, pending)
        val command = WindowUpdateCommand(
            windowId = window.id,
            operationId = pending.operationId,
            expectedRevision = pending.expectedRevision,
            update = pending.update,
            desiredLevel = window.desiredLevel(),
            stimulusSink = updateStimulusSink,
            fullscreenObservationSink = fullscreenObservationSink,
        )
        val dispatch = synchronized(lock) {
            if (!window.beginNativeUpdateDispatch(pending.operationId)) return
            dispatchedWindowUpdates[pending.operationId] = window
            guardPort("window-update-exception") { commandPort.requestUpdate(command) }
        }
        if (dispatch is GuardedCall.Failure) {
            synchronized(lock) { dispatchedWindowUpdates.remove(pending.operationId) }
            window.rejectDispatchedUpdate(pending.operationId, dispatch.failure)
        }
    }

    internal fun withdrawWindowUpdate(window: RuntimeWindow, operationId: WindowOperationId) {
        val outcome = synchronized(lock) {
            guardPort("window-update-cancellation-exception") {
                commandPort.requestUpdateCancellation(WindowUpdateCancellationCommand(operationId))
            }.also { guarded ->
                if (
                    guarded is GuardedCall.Success &&
                    guarded.value == WindowUpdateCancellationOutcome.CancelledBeforeCommit
                ) {
                    dispatchedWindowUpdates.remove(operationId)
                }
            }
        }
        if (outcome is GuardedCall.Success) {
            when (outcome.value) {
                WindowUpdateCancellationOutcome.CancelledBeforeCommit -> {
                    window.withdrawDispatchedUpdate(operationId)
                }
                WindowUpdateCancellationOutcome.CancellationRequested -> Unit
                WindowUpdateCancellationOutcome.TooLate -> window.detachDispatchedUpdateWaiter(operationId)
            }
        }
    }

    private fun acceptWindowUpdateStimulus(stimulus: WindowUpdateCommandStimulus) {
        routeWindowUpdateStimulus(stimulus, bufferFullscreenTerminal = true, retainDispatch = false)
    }

    internal fun acceptBufferedWindowUpdateStimulus(stimulus: WindowUpdateCommandStimulus) {
        routeWindowUpdateStimulus(stimulus, bufferFullscreenTerminal = false, retainDispatch = true)
    }

    private fun routeWindowUpdateStimulus(
        stimulus: WindowUpdateCommandStimulus,
        bufferFullscreenTerminal: Boolean,
        retainDispatch: Boolean,
    ) {
        val operationId = stimulus.operationId()
        val window = synchronized(lock) { dispatchedWindowUpdates[operationId] } ?: return
        if (bufferFullscreenTerminal && window.bufferFullscreenTerminal(stimulus)) return
        if (!retainDispatch) synchronized(lock) { dispatchedWindowUpdates.remove(operationId) }
        when (stimulus) {
            is WindowUpdateCommandStimulus.Applied -> window.applyNativeUpdate(stimulus.operationId, stimulus.state)
            is WindowUpdateCommandStimulus.CommittedFailure -> window.applyCommittedFailure(
                stimulus.operationId,
                stimulus.effectiveState,
                stimulus.publicationOperationId,
                stimulus.failure,
                stimulus.rejected,
                stimulus.diagnosticCause,
            )
            is WindowUpdateCommandStimulus.Failed -> window.rejectDispatchedUpdate(
                stimulus.operationId,
                stimulus.failure,
                stimulus.diagnosticCause,
            )
            is WindowUpdateCommandStimulus.PartiallyApplied -> window.applyNativeUpdate(
                stimulus.operationId,
                stimulus.state,
                stimulus.rejected,
            )
            is WindowUpdateCommandStimulus.Rejected -> {
                safeReport(stimulus.error)
                window.rejectDispatchedUpdate(stimulus.operationId, platformFailure("window-update-rejected"))
            }
        }
    }

    internal fun acceptWindowFullscreenObservation(
        windowId: WindowId,
        observation: WindowFullscreenObservation,
    ): Boolean {
        val window = synchronized(lock) {
            committed.values.firstOrNull { it.window.id == windowId }?.window
        } ?: return false
        val acceptance = window.acceptFullscreenObservation(observation, operationId = null)
        acceptance.terminalOperationId?.let { operationId ->
            synchronized(lock) { dispatchedWindowUpdates.remove(operationId) }
        }
        return acceptance.accepted
    }

    override fun accept(windowId: WindowId, observation: RuntimeFullscreenObservation): Boolean =
        acceptWindowFullscreenObservation(
            windowId,
            when (observation) {
                is RuntimeFullscreenObservation.Will -> WindowFullscreenObservation.Will(observation.target)
                is RuntimeFullscreenObservation.Did -> WindowFullscreenObservation.Did(observation.effectiveState)
                is RuntimeFullscreenObservation.DidFail -> WindowFullscreenObservation.DidFail(observation.target)
            },
        )

    override fun desiredLevel(windowId: WindowId): WindowLevel? =
        synchronized(lock) {
            committed.values.firstOrNull { it.window.id == windowId }?.window
        }?.desiredLevel()

    private fun acceptCorrelatedWindowFullscreenObservation(
        windowId: WindowId,
        operationId: WindowOperationId,
        observation: WindowFullscreenObservation,
    ) {
        val window = synchronized(lock) { dispatchedWindowUpdates[operationId] }
            ?.takeIf { it.id == windowId } ?: return
        val acceptance = window.acceptFullscreenObservation(observation, operationId)
        if (acceptance.terminalOperationId != null) {
            synchronized(lock) { dispatchedWindowUpdates.remove(acceptance.terminalOperationId) }
        }
    }

    private fun beginCorrelatedWindowFullscreenSelectorInvocation(
        windowId: WindowId,
        operationId: WindowOperationId,
    ): Boolean {
        val window = synchronized(lock) { dispatchedWindowUpdates[operationId] }
            ?.takeIf { it.id == windowId } ?: return false
        return window.beginFullscreenSelectorInvocation(operationId)
    }

    private fun finishCorrelatedWindowFullscreenSelectorInvocation(
        windowId: WindowId,
        operationId: WindowOperationId,
        failure: KadreFailure?,
    ) {
        val window = synchronized(lock) { dispatchedWindowUpdates[operationId] }
            ?.takeIf { it.id == windowId } ?: return
        if (window.finishFullscreenSelectorInvocation(operationId, failure)) {
            synchronized(lock) { dispatchedWindowUpdates.remove(operationId) }
        }
    }

    internal fun reportDetachedWindowUpdateFailure(
        failure: KadreFailure,
        diagnosticCause: Throwable? = null,
    ) {
        val diagnostic = KadreException(failure)
        diagnosticCause?.let(diagnostic::addSuppressed)
        safeReport(diagnostic)
    }

    internal suspend fun requestAttention(
        window: RuntimeWindow,
        attention: WindowAttention,
    ): KadreResult<Unit> {
        val port = synchronized(lock) {
            if (window.currentState().phase != WindowPhase.Open) {
                return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            }
            val configuredPort = attentionPort
                ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention))
            if (attention !in acceptedAttention) {
                return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention))
            }
            configuredPort
        }
        return try {
            port.request(window.id, attention)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Exception) {
            safeReport(cause)
            KadreResult.Failure(platformFailure("window-attention-exception"))
        } catch (cause: LinkageError) {
            safeReport(cause)
            KadreResult.Failure(platformFailure("window-attention-exception"))
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
        drainAttentionReleases()
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
                                    ?.let(dispatchedWindowUpdates::remove)
                                KadreResult.Success(WindowCloseOutcome.Accepted(operationId))
                            }

                            OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted -> {
                                val reason = if (window.activeCloseRequest != null) {
                                    WindowCloseReason.User
                                } else {
                                    WindowCloseReason.System
                                }
                                window.beginClosing(operationId, reason, nextEventStamp())
                                    ?.let(dispatchedWindowUpdates::remove)
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
        drainAttentionReleases()
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
        drainAttentionReleases()
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
                        )?.let(dispatchedWindowUpdates::remove)
                    }
                    record.window.markNativeCloseCommitted()
                    record.window.finishClosing()
                    surfaces.remove(record.window.surface.id)
                    releaseWindowSlotsLocked(1)
                    pendingAttentionReleases += record.window.id
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
            drainAttentionReleases()
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
                        ?.let(dispatchedWindowUpdates::remove)
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
            )?.let(dispatchedWindowUpdates::remove)
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
        pendingAttentionReleases += record.window.id
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
            platform = platform,
            publicWindowCapabilities = publicWindowCapabilities,
            enabledWindowUpdateCapabilities = enabledWindowUpdateCapabilities,
            acceptedAttention = if (attentionPort == null) null else acceptedAttention,
            fullscreenAvailabilityFailure = fullscreenAvailabilityFailure,
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
            drainAttentionReleases()
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

    private fun drainAttentionReleases() {
        val releases = synchronized(lock) {
            val port = attentionPort ?: return@synchronized null
            val windowIds = pendingAttentionReleases.toList()
            pendingAttentionReleases.clear()
            windowIds to port
        } ?: return
        val (windowIds, port) = releases
        windowIds.forEach { windowId ->
            try {
                port.release(windowId)
            } catch (cause: Exception) {
                safeReport(cause)
            } catch (cause: LinkageError) {
                safeReport(cause)
            }
        }
    }

    private fun safeCloseAttentionPort(port: WindowAttentionPort) {
        try {
            port.close()
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

    private fun rejectedWindowRequest(): RuntimeWindowRequest = RuntimeWindowRequest(
        RuntimeProcessIds.nextWindowRequestId(),
        RuntimeProcessIds.nextWindowId(),
        this,
    ).also { request ->
        request.terminate(WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow)))
        request.markHandoffDelivered()
    }

    private fun unsupportedInitialWindowProperty(spec: WindowSpec): WindowProperty? {
        if (!publicWindowCapabilities) return null
        fun unsupported(property: WindowProperty): Boolean = property !in enabledWindowUpdateCapabilities
        return when {
            spec.title.isNotEmpty() && unsupported(WindowProperty.Title) -> WindowProperty.Title
            spec.contentSize != DEFAULT_WINDOW_CONTENT_SIZE && unsupported(WindowProperty.ContentSize) -> WindowProperty.ContentSize
            spec.minimumSize != null && unsupported(WindowProperty.MinimumSize) -> WindowProperty.MinimumSize
            spec.maximumSize != null && unsupported(WindowProperty.MaximumSize) -> WindowProperty.MaximumSize
            spec.outerPosition != null && unsupported(WindowProperty.OuterPosition) -> WindowProperty.OuterPosition
            !spec.resizable && unsupported(WindowProperty.Resizable) -> WindowProperty.Resizable
            spec.fullscreen is FullscreenMode.Exclusive -> WindowProperty.Fullscreen
            spec.fullscreen is FullscreenMode.Borderless && unsupported(WindowProperty.Fullscreen) -> WindowProperty.Fullscreen
            spec.decorations != WindowDecorations.System && unsupported(WindowProperty.Decorations) -> WindowProperty.Decorations
            spec.systemButtons != WindowSystemButtons.All && unsupported(WindowProperty.SystemButtons) -> WindowProperty.SystemButtons
            spec.level != WindowLevel.Normal && unsupported(WindowProperty.Level) -> WindowProperty.Level
            spec.transparent && unsupported(WindowProperty.Transparency) -> WindowProperty.Transparency
            spec.blurBehind && unsupported(WindowProperty.Blur) -> WindowProperty.Blur
            spec.icon != null && unsupported(WindowProperty.Icon) -> WindowProperty.Icon
            spec.contentProtection && unsupported(WindowProperty.ContentProtection) -> WindowProperty.ContentProtection
            else -> null
        }
    }

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
    private val platform: KadrePlatform,
    publicWindowCapabilities: Boolean,
    enabledWindowUpdateCapabilities: Set<WindowProperty>,
    acceptedAttention: Set<WindowAttention>?,
    private val fullscreenAvailabilityFailure: KadreFailure.PlatformFailure?,
    eventCollectorGate: RuntimeEventCollectorGate,
    deliveryPolicy: WindowDeliveryPolicy,
    private val eventStampSource: () -> EventStamp,
    failureReporter: RuntimeFailureReporter,
    sessionFailureHandler: (KadreFailure) -> Unit,
) : Window, RuntimeDesktopWindowHandleAccess {
    private val initialState = initialWindowState(spec)
    private val mutableState = MutableStateFlow(initialState)
    private val mutableCapabilities = MutableStateFlow(
        windowCapabilities(
            publicWindowCapabilities,
            enabledWindowUpdateCapabilities,
            acceptedAttention,
            fullscreenAvailabilityFailure,
        ),
    )
    private val supportedWindowUpdateProperties =
        DEFAULT_RUNTIME_WINDOW_UPDATE_PROPERTIES + enabledWindowUpdateCapabilities
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
    private var desiredLevel: WindowLevel = spec.level
    private var fullscreenBarrier: FullscreenBarrier? = null
    private var fullscreenTombstone: FullscreenTombstone? = null
    private var eventDeliveryClosePending = false
    private var eventPublicationsInFlight = 0
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
        manager.requestAttention(this, attention)

    override suspend fun close(): KadreResult<WindowCloseOutcome> = manager.closeWindow(this)

    override suspend fun respondToCloseRequest(
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): KadreResult<WindowCloseResponseOutcome> = manager.respondToCloseRequest(this, requestId, decision)

    override suspend fun <R> withDesktopHandle(
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): KadreResult<R> = manager.withDesktopHandle(this, block)

    fun currentState(): WindowState = mutableState.value

    fun desiredLevel(): WindowLevel = synchronized(updateLock) { desiredLevel }

    suspend fun applyUpdate(update: WindowUpdate): KadreResult<WindowUpdateOutcome> {
        currentCoroutineContext().ensureActive()
        val pending: PendingWindowUpdate
        val immediate: KadreResult<WindowUpdateOutcome>?
        synchronized(updateLock) {
            val current = mutableState.value
            if (current.phase != WindowPhase.Open) {
                return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            }
            if (
                update.fullscreen !is PropertyChange.Unchanged &&
                changedProperties(update).any { it != WindowProperty.Fullscreen }
            ) {
                return KadreResult.Failure(KadreFailure.InvalidRequest("fullscreen"))
            }
            invalidRequiredClearField(update, supportedWindowUpdateProperties)?.let { field ->
                return KadreResult.Failure(KadreFailure.InvalidRequest(field))
            }
            val supportedUpdate = supportedMutationOnly(update, supportedWindowUpdateProperties)
            invalidChromeField(supportedUpdate, current)?.let { field ->
                return KadreResult.Failure(KadreFailure.InvalidRequest(field))
            }
            val canonicalUpdate = canonicalMutationUpdate(supportedUpdate, current)
            val candidate = candidateFor(canonicalUpdate, current)
                ?: return KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints"))
            update.expectedRevision?.let { expected ->
                if (expected != current.revision) {
                    return KadreResult.Failure(KadreFailure.StaleRevision(expected.value, current.revision.value))
                }
            }
            val operationId = RuntimeProcessIds.nextWindowOperationId()
            val exclusive = (supportedUpdate.fullscreen as? PropertyChange.Set)?.value as? FullscreenMode.Exclusive
            if (exclusive != null) {
                return KadreResult.Success(
                    WindowUpdateOutcome.PartiallyApplied(
                        operationId,
                        current,
                        listOf(
                            RejectedWindowField(
                                WindowProperty.Fullscreen,
                                KadreFailure.Unsupported(KadreOperation.UpdateWindow),
                            ),
                        ),
                    ),
                )
            }
            if (supportedUpdate.fullscreen is PropertyChange.Set && fullscreenAvailabilityFailure != null) {
                return KadreResult.Failure(fullscreenAvailabilityFailure)
            }
            if (
                supportedUpdate.fullscreen is PropertyChange.Set &&
                fullscreenBarrier?.phase == FullscreenPhase.External
            ) {
                return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
            }
            val rejected = changedProperties(update)
                .filterNot(supportedWindowUpdateProperties::contains)
                .map { RejectedWindowField(it, KadreFailure.Unsupported(KadreOperation.UpdateWindow)) }
            val mutationChanged = mutationChanged(current, candidate)
            val requestedLevel = (canonicalUpdate.level as? PropertyChange.Set)?.value
            val deferBehindFullscreenBarrier = fullscreenBarrier != null
            if (!mutationChanged && !deferBehindFullscreenBarrier) {
                update.expectedRevision?.let { expected ->
                    if (expected != current.revision) {
                        return KadreResult.Failure(KadreFailure.StaleRevision(expected.value, current.revision.value))
                    }
                }
                if (requestedLevel != null && requestedLevel != desiredLevel) desiredLevel = requestedLevel
                immediate = KadreResult.Success(updateOutcome(operationId, current, rejected))
                pending = PendingWindowUpdate(operationId, null, canonicalUpdate, rejected)
            } else {
                immediate = null
                pending = PendingWindowUpdate(
                    operationId = operationId,
                    expectedRevision = update.expectedRevision,
                    update = canonicalUpdate,
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

    fun applyNativeUpdate(
        operationId: WindowOperationId,
        state: WindowState,
        backendRejected: List<RejectedWindowField> = emptyList(),
    ) {
        var publication: WindowStatePublication? = null
        var completion: PendingWindowUpdate? = null
        var outcome: KadreResult<WindowUpdateOutcome>? = null
        synchronized(updateLock) {
            val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return
            dispatchedUpdate = null
            fullscreenBarrier?.takeIf { it.operationId == operationId }?.let { barrier ->
                completeFullscreenBarrierLocked(
                    barrier,
                    FullscreenTombstone(fullscreenTarget(pending.update), FullscreenTerminalKind.Did),
                )
            }
            eventPublicationsInFlight += 1
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
            updateDesiredLevelAfterReadback(pending, effective, backendRejected)
            publication = WindowStatePublication(lifecycle, effective, operationId)
            completion = pending
            outcome = KadreResult.Success(updateOutcome(operationId, effective, pending.rejected + backendRejected))
        }
        try {
            publishStatePublication(checkNotNull(publication))
        } finally {
            checkNotNull(completion).result.complete(checkNotNull(outcome))
            val closeEventDelivery = synchronized(updateLock) {
                check(eventPublicationsInFlight > 0) { "window event publication accounting underflow" }
                eventPublicationsInFlight -= 1
                takePendingEventDeliveryCloseLocked()
            }
            if (closeEventDelivery) eventFlow.close()
            dispatchNextUpdate()
        }
    }

    fun applyCommittedFailure(
        operationId: WindowOperationId,
        state: WindowState,
        publicationOperationId: WindowOperationId?,
        failure: KadreFailure,
        backendRejected: List<RejectedWindowField> = emptyList(),
        diagnosticCause: Throwable? = null,
    ) {
        var publication: WindowStatePublication? = null
        var completion: PendingWindowUpdate? = null
        synchronized(updateLock) {
            val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return
            dispatchedUpdate = null
            fullscreenBarrier?.takeIf { it.operationId == operationId }?.let { barrier ->
                completeFullscreenBarrierLocked(
                    barrier,
                    FullscreenTombstone(fullscreenTarget(pending.update), FullscreenTerminalKind.Did),
                )
            }
            val lifecycle = mutableState.value
            val candidate = state.copy(phase = lifecycle.phase, revision = lifecycle.revision)
            if (candidate != lifecycle) {
                val effective = candidate.copy(revision = WindowRevision(lifecycle.revision.value + 1L))
                mutableState.value = effective
                eventPublicationsInFlight += 1
                publication = WindowStatePublication(
                    lifecycle,
                    effective,
                    publicationOperationId,
                    forcedChanged = if (pending.update.fullscreen is PropertyChange.Set) {
                        setOf(WindowProperty.Fullscreen)
                    } else {
                        emptySet()
                    },
                )
            }
            updateDesiredLevelAfterReadback(pending, candidate, backendRejected)
            completion = pending
        }
        try {
            publication?.let(::publishStatePublication)
        } finally {
            val pending = checkNotNull(completion)
            pending.result.complete(KadreResult.Failure(failure))
            if (pending.waiterDetached) {
                manager.reportDetachedWindowUpdateFailure(failure, diagnosticCause)
            }
            val closeEventDelivery = synchronized(updateLock) {
                if (publication != null) eventPublicationsInFlight -= 1
                takePendingEventDeliveryCloseLocked()
            }
            if (closeEventDelivery) eventFlow.close()
            dispatchNextUpdate()
        }
    }

    fun acceptFullscreenObservation(
        observation: WindowFullscreenObservation,
        operationId: WindowOperationId?,
    ): FullscreenObservationAcceptance = acceptFullscreenObservation(
        observation,
        operationId,
        drainAfter = true,
        bufferFullscreenTerminal = true,
    )

    fun bufferFullscreenTerminal(stimulus: WindowUpdateCommandStimulus): Boolean = synchronized(updateLock) {
        val barrier = fullscreenBarrier?.takeIf { it.operationId == stimulus.operationId() }
            ?: return@synchronized false
        if (
            barrier.phase != FullscreenPhase.InvokingSelector &&
            barrier.phase != FullscreenPhase.DrainingTerminals
        ) {
            return@synchronized false
        }
        barrier.terminalCallbacks.addLast(FullscreenTerminalCallback.Stimulus(stimulus))
        true
    }

    private fun acceptFullscreenObservation(
        observation: WindowFullscreenObservation,
        operationId: WindowOperationId?,
        drainAfter: Boolean,
        bufferFullscreenTerminal: Boolean,
    ): FullscreenObservationAcceptance {
        val resolution = synchronized(updateLock) {
            val actualBarrier = fullscreenBarrier
            if (
                operationId != null &&
                actualBarrier != null &&
                actualBarrier.operationId != operationId
            ) {
                return@synchronized FullscreenResolution.Rejected
            }
            if (
                bufferFullscreenTerminal &&
                actualBarrier != null &&
                (
                    actualBarrier.phase == FullscreenPhase.DrainingTerminals ||
                        actualBarrier.phase == FullscreenPhase.InvokingSelector &&
                        observation !is WindowFullscreenObservation.Will
                    )
            ) {
                actualBarrier.terminalCallbacks.addLast(FullscreenTerminalCallback.Observation(observation))
                return@synchronized FullscreenResolution.Accepted
            }
            val currentBarrier = actualBarrier?.takeUnless {
                it.phase == FullscreenPhase.DrainingTerminals && it.terminalResolved
            }
            when (observation) {
                is WindowFullscreenObservation.Will -> acceptFullscreenWillLocked(
                    currentBarrier,
                    observation.target,
                    operationId,
                )
                is WindowFullscreenObservation.Did -> acceptFullscreenDidLocked(
                    currentBarrier,
                    observation.effectiveState,
                    operationId,
                    observation.rejected,
                )
                is WindowFullscreenObservation.DidFail -> acceptFullscreenDidFailLocked(
                    currentBarrier,
                    observation.target,
                    operationId,
                    observation.effectiveState,
                    observation.rejected,
                    observation.terminalFailure,
                )
            }
        }
        executeFullscreenResolution(resolution, drainAfter)
        return FullscreenObservationAcceptance(resolution.accepted, resolution.terminalOperationId)
    }

    fun finishFullscreenSelectorInvocation(
        operationId: WindowOperationId,
        dispatchFailure: KadreFailure?,
    ): Boolean {
        val drainingBarrier = synchronized(updateLock) {
            fullscreenBarrier?.takeIf { it.operationId == operationId }?.also { barrier ->
                if (barrier.phase == FullscreenPhase.InvokingSelector) {
                    barrier.phase = FullscreenPhase.DrainingTerminals
                }
            }
        }
        while (drainingBarrier != null) {
            val callback = synchronized(updateLock) {
                if (drainingBarrier.terminalCallbacks.isNotEmpty()) {
                    drainingBarrier.terminalCallbacks.removeFirst()
                } else {
                    if (fullscreenBarrier === drainingBarrier) {
                        if (drainingBarrier.terminalResolved) {
                            fullscreenBarrier = null
                        } else {
                            drainingBarrier.phase = FullscreenPhase.AwaitingLocal
                        }
                    }
                    null
                }
            } ?: break
            when (callback) {
                is FullscreenTerminalCallback.Observation -> acceptFullscreenObservation(
                    callback.observation,
                    operationId,
                    drainAfter = false,
                    bufferFullscreenTerminal = false,
                )
                is FullscreenTerminalCallback.Stimulus -> manager.acceptBufferedWindowUpdateStimulus(callback.stimulus)
            }
        }
        val selectorFailure = synchronized(updateLock) {
            val barrier = fullscreenBarrier?.takeIf { it.operationId == operationId }
            if (dispatchFailure != null && barrier != null && !barrier.willObserved) {
                failLocalFullscreenLocked(
                    barrier,
                    dispatchFailure,
                )
            } else {
                null
            }
        }
        if (selectorFailure != null) executeFullscreenResolution(selectorFailure, drainAfter = false)
        val terminal = synchronized(updateLock) { dispatchedUpdate?.operationId != operationId }
        if (terminal) dispatchNextUpdate()
        return terminal
    }

    private fun acceptFullscreenWillLocked(
        barrier: FullscreenBarrier?,
        target: FullscreenMode,
        operationId: WindowOperationId?,
    ): FullscreenResolution {
        if (!target.isNativeFullscreenTarget()) return FullscreenResolution.Rejected
        fullscreenTombstone = null
        if (barrier == null) {
            if (operationId != null) return FullscreenResolution.Rejected
            fullscreenBarrier = FullscreenBarrier(null, target, FullscreenPhase.External)
            return FullscreenResolution.Accepted
        }
        return when (barrier.phase) {
            FullscreenPhase.PreparedLocal -> {
                val pending = dispatchedUpdate?.takeIf { it.operationId == barrier.operationId }
                dispatchedUpdate = null
                fullscreenBarrier = FullscreenBarrier(null, target, FullscreenPhase.External)
                FullscreenResolution(
                    completion = pending,
                    result = KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
                    terminalOperationId = barrier.operationId,
                )
            }
            FullscreenPhase.InvokingSelector,
            FullscreenPhase.DrainingTerminals,
            FullscreenPhase.AwaitingLocal,
            -> {
                barrier.willObserved = true
                if (target != barrier.target) barrier.conflictTarget = target
                FullscreenResolution.Accepted
            }
            FullscreenPhase.External -> {
                barrier.target = target
                FullscreenResolution.Accepted
            }
        }
    }

    private fun acceptFullscreenDidLocked(
        barrier: FullscreenBarrier?,
        effectiveState: WindowState,
        operationId: WindowOperationId?,
        backendRejected: List<RejectedWindowField>,
    ): FullscreenResolution {
        val target = effectiveState.fullscreen
        if (!target.isNativeFullscreenTarget()) return FullscreenResolution.Rejected
        if (barrier == null) {
            if (fullscreenTombstone?.matches(target, FullscreenTerminalKind.Did) == true) {
                return FullscreenResolution.Accepted
            }
            val publication = prepareFullscreenPublicationLocked(
                effectiveState,
                operationId = null,
                forcedChanged = setOf(WindowProperty.Fullscreen),
            )
            fullscreenTombstone = FullscreenTombstone(target, FullscreenTerminalKind.Did)
            return FullscreenResolution(publication = publication)
        }
        if (barrier.phase == FullscreenPhase.External) {
            fullscreenBarrier = null
            fullscreenTombstone = FullscreenTombstone(target, FullscreenTerminalKind.Did)
            val diagnostic = if (target != barrier.target) unexpectedFullscreenFailure() else null
            return FullscreenResolution(
                publication = prepareFullscreenPublicationLocked(
                    effectiveState,
                    operationId = null,
                    forcedChanged = setOf(WindowProperty.Fullscreen),
                ),
                diagnostics = listOfNotNull(diagnostic),
                drain = true,
            )
        }
        if (barrier.phase == FullscreenPhase.PreparedLocal && operationId == null) {
            val pending = dispatchedUpdate?.takeIf { it.operationId == barrier.operationId }
            dispatchedUpdate = null
            fullscreenBarrier = null
            fullscreenTombstone = FullscreenTombstone(target, FullscreenTerminalKind.Did)
            return FullscreenResolution(
                publication = prepareFullscreenPublicationLocked(
                    effectiveState,
                    operationId = null,
                    forcedChanged = setOf(WindowProperty.Fullscreen),
                ),
                completion = pending,
                result = KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
                terminalOperationId = barrier.operationId,
                drain = true,
            )
        }
        val localSuccess = target == barrier.target
        val armedConflict = target == barrier.conflictTarget
        if (!localSuccess && !armedConflict) {
            if (fullscreenTombstone?.matches(target, FullscreenTerminalKind.Did) == true) {
                return FullscreenResolution.Accepted
            }
            val publication = prepareFullscreenPublicationLocked(
                effectiveState,
                operationId = null,
                forcedChanged = setOf(WindowProperty.Fullscreen),
            )
            fullscreenTombstone = FullscreenTombstone(target, FullscreenTerminalKind.Did)
            return FullscreenResolution(publication = publication)
        }
        val pending = dispatchedUpdate?.takeIf { it.operationId == barrier.operationId }
            ?: return FullscreenResolution.Rejected
        dispatchedUpdate = null
        completeFullscreenBarrierLocked(
            barrier,
            FullscreenTombstone(target, FullscreenTerminalKind.Did),
        )
        val publicationOperationId = if (localSuccess) barrier.operationId else null
        val publication = prepareFullscreenPublicationLocked(
            effectiveState,
            publicationOperationId,
            forcedChanged = setOf(WindowProperty.Fullscreen),
        )
        val effective = publication?.effective ?: mutableState.value
        updateDesiredLevelAfterReadback(pending, effective, backendRejected)
        return FullscreenResolution(
            publication = publication,
            completion = pending,
            result = if (localSuccess) {
                KadreResult.Success(
                    updateOutcome(pending.operationId, effective, pending.rejected + backendRejected),
                )
            } else {
                KadreResult.Failure(unexpectedFullscreenFailure())
            },
            terminalOperationId = barrier.operationId,
            drain = true,
        )
    }

    private fun acceptFullscreenDidFailLocked(
        barrier: FullscreenBarrier?,
        target: FullscreenMode,
        operationId: WindowOperationId?,
        effectiveState: WindowState?,
        backendRejected: List<RejectedWindowField>,
        terminalFailure: KadreFailure?,
    ): FullscreenResolution {
        if (!target.isNativeFullscreenTarget()) return FullscreenResolution.Rejected
        if (barrier == null) {
            if (fullscreenTombstone?.matches(target, FullscreenTerminalKind.DidFail) == true) {
                return FullscreenResolution.Accepted
            }
            fullscreenTombstone = FullscreenTombstone(target, FullscreenTerminalKind.DidFail)
            if (operationId != null) return FullscreenResolution.Accepted
            return FullscreenResolution(diagnostics = listOf(fullscreenCallbackFailure(target)))
        }
        if (barrier.phase == FullscreenPhase.External) {
            fullscreenBarrier = null
            fullscreenTombstone = FullscreenTombstone(target, FullscreenTerminalKind.DidFail)
            return FullscreenResolution(
                diagnostics = listOf(fullscreenCallbackFailure(target)),
                drain = true,
            )
        }
        if (barrier.phase == FullscreenPhase.PreparedLocal && operationId == null) {
            fullscreenTombstone = FullscreenTombstone(target, FullscreenTerminalKind.DidFail)
            return FullscreenResolution(diagnostics = listOf(fullscreenCallbackFailure(target)))
        }
        if (target != barrier.target && target != barrier.conflictTarget) {
            if (fullscreenTombstone?.matches(target, FullscreenTerminalKind.DidFail) == true) {
                return FullscreenResolution.Accepted
            }
            fullscreenTombstone = FullscreenTombstone(target, FullscreenTerminalKind.DidFail)
            return FullscreenResolution(diagnostics = listOf(fullscreenCallbackFailure(target)))
        }
        val localFailure = target == barrier.target
        val failure = if (localFailure) {
            terminalFailure ?: fullscreenCallbackFailure(target)
        } else {
            unexpectedFullscreenFailure()
        }
        val resolution = failLocalFullscreenLocked(
            barrier,
            failure,
            target,
            effectiveState,
            backendRejected,
        ) ?: return FullscreenResolution.Rejected
        return if (localFailure) {
            resolution
        } else {
            resolution.copy(diagnostics = listOf(fullscreenCallbackFailure(target)))
        }
    }

    private fun failLocalFullscreenLocked(
        barrier: FullscreenBarrier,
        failure: KadreFailure,
        tombstoneTarget: FullscreenMode = barrier.target,
        effectiveState: WindowState? = null,
        backendRejected: List<RejectedWindowField> = emptyList(),
    ): FullscreenResolution? {
        val pending = dispatchedUpdate?.takeIf { it.operationId == barrier.operationId } ?: return null
        dispatchedUpdate = null
        completeFullscreenBarrierLocked(
            barrier,
            FullscreenTombstone(tombstoneTarget, FullscreenTerminalKind.DidFail),
        )
        val publicationOperationId = if (tombstoneTarget == barrier.target) barrier.operationId else null
        val publication = effectiveState?.let {
            prepareFullscreenPublicationLocked(it, publicationOperationId)
        }
        if (effectiveState != null) {
            updateDesiredLevelAfterReadback(
                pending,
                publication?.effective ?: mutableState.value,
                backendRejected,
            )
        }
        return FullscreenResolution(
            publication = publication,
            completion = pending,
            result = KadreResult.Failure(failure),
            terminalOperationId = barrier.operationId,
            drain = true,
        )
    }

    private fun prepareFullscreenPublicationLocked(
        state: WindowState,
        operationId: WindowOperationId?,
        forcedChanged: Set<WindowProperty> = emptySet(),
    ): WindowStatePublication? {
        val lifecycle = mutableState.value
        if (lifecycle.phase != WindowPhase.Open) return null
        val candidate = state.copy(phase = lifecycle.phase, revision = lifecycle.revision)
        if (candidate == lifecycle) return null
        val effective = candidate.copy(revision = WindowRevision(lifecycle.revision.value + 1L))
        mutableState.value = effective
        eventPublicationsInFlight += 1
        return WindowStatePublication(lifecycle, effective, operationId, forcedChanged)
    }

    private fun completeFullscreenBarrierLocked(
        barrier: FullscreenBarrier,
        tombstone: FullscreenTombstone,
    ) {
        if (barrier.phase == FullscreenPhase.DrainingTerminals) {
            barrier.terminalResolved = true
        } else if (fullscreenBarrier === barrier) {
            fullscreenBarrier = null
        }
        fullscreenTombstone = tombstone
    }

    private fun executeFullscreenResolution(resolution: FullscreenResolution, drainAfter: Boolean) {
        try {
            resolution.publication?.let(::publishStatePublication)
        } finally {
            resolution.completion?.let { pending ->
                checkNotNull(resolution.result)
                pending.result.complete(resolution.result)
                val failure = (resolution.result as? KadreResult.Failure)?.reason
                if (failure != null && pending.waiterDetached) manager.reportDetachedWindowUpdateFailure(failure)
            }
            resolution.diagnostics.forEach(manager::reportDetachedWindowUpdateFailure)
            val closeEventDelivery = synchronized(updateLock) {
                if (resolution.publication != null) eventPublicationsInFlight -= 1
                takePendingEventDeliveryCloseLocked()
            }
            if (closeEventDelivery) eventFlow.close()
            if (drainAfter && resolution.drain) dispatchNextUpdate()
        }
    }

    private fun fullscreenCallbackFailure(target: FullscreenMode): KadreFailure.PlatformFailure =
        KadreFailure.PlatformFailure(
            platform,
            "fullscreen",
            if (target == FullscreenMode.Windowed) "exit-failed" else "enter-failed",
        )

    private fun unexpectedFullscreenFailure(): KadreFailure.PlatformFailure =
        KadreFailure.PlatformFailure(platform, "fullscreen", "unexpected-transition")

    private fun updateDesiredLevelAfterReadback(
        pending: PendingWindowUpdate,
        effective: WindowState,
        backendRejected: List<RejectedWindowField>,
    ) {
        val requested = (pending.update.level as? PropertyChange.Set)?.value ?: return
        if (backendRejected.none { it.field == WindowProperty.Level } && effective.level == requested) {
            desiredLevel = requested
        }
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

    fun rejectDispatchedUpdate(
        operationId: WindowOperationId,
        failure: KadreFailure,
        diagnosticCause: Throwable? = null,
    ) {
        var closeEventDelivery = false
        var reportDetached = false
        synchronized(updateLock) {
            val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return
            dispatchedUpdate = null
            fullscreenBarrier?.takeIf { it.operationId == operationId }?.let { barrier ->
                completeFullscreenBarrierLocked(
                    barrier,
                    FullscreenTombstone(fullscreenTarget(pending.update), FullscreenTerminalKind.DidFail),
                )
            }
            pending.result.complete(KadreResult.Failure(failure))
            reportDetached = pending.waiterDetached
            closeEventDelivery = takePendingEventDeliveryCloseLocked()
        }
        if (reportDetached) manager.reportDetachedWindowUpdateFailure(failure, diagnosticCause)
        if (closeEventDelivery) eventFlow.close()
        dispatchNextUpdate()
    }

    fun withdrawDispatchedUpdate(operationId: WindowOperationId) {
        var closeEventDelivery = false
        synchronized(updateLock) {
            val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return
            dispatchedUpdate = null
            pending.cancelled = true
            fullscreenBarrier?.takeIf {
                it.operationId == operationId && it.phase == FullscreenPhase.PreparedLocal
            }?.let { fullscreenBarrier = null }
            closeEventDelivery = takePendingEventDeliveryCloseLocked()
        }
        if (closeEventDelivery) eventFlow.close()
        dispatchNextUpdate()
    }

    fun detachDispatchedUpdateWaiter(operationId: WindowOperationId) = synchronized(updateLock) {
        dispatchedUpdate
            ?.takeIf { it.operationId == operationId }
            ?.let { it.waiterDetached = true }
    }

    private fun dispatchNextUpdate() {
        var next: PendingWindowUpdate? = null
        synchronized(updateLock) {
            if (dispatchedUpdate != null) return
            if (fullscreenBarrier != null) return
            while (pendingUpdates.isNotEmpty()) {
                val candidate = pendingUpdates.removeFirst()
                if (candidate.cancelled) continue
                val current = mutableState.value
                if (current.phase != WindowPhase.Open) {
                    candidate.result.complete(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)))
                    continue
                }
                invalidChromeField(candidate.update, current)?.let { field ->
                    candidate.result.complete(KadreResult.Failure(KadreFailure.InvalidRequest(field)))
                    continue
                }
                candidate.update = canonicalMutationUpdate(candidate.update, current)
                val effectiveCandidate = candidateFor(candidate.update, current)
                if (effectiveCandidate == null) {
                    candidate.result.complete(KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints")))
                    continue
                }
                val expected = candidate.expectedRevision
                if (expected != null && expected != current.revision) {
                    candidate.result.complete(
                        KadreResult.Failure(KadreFailure.StaleRevision(expected.value, current.revision.value)),
                    )
                    continue
                }
                if (!mutationChanged(current, effectiveCandidate)) {
                    val requestedLevel = (candidate.update.level as? PropertyChange.Set)?.value
                    if (requestedLevel != null && requestedLevel != desiredLevel) desiredLevel = requestedLevel
                    candidate.result.complete(KadreResult.Success(updateOutcome(candidate.operationId, current, candidate.rejected)))
                    continue
                }
                if (candidate.result.isCompleted) continue
                dispatchedUpdate = candidate
                fullscreenTarget(candidate.update)?.let { target ->
                    fullscreenBarrier = FullscreenBarrier(
                        operationId = candidate.operationId,
                        target = target,
                        phase = FullscreenPhase.PreparedLocal,
                    )
                    fullscreenTombstone = null
                }
                next = candidate
                break
            }
        }
        next?.let { manager.dispatchWindowUpdate(this, it) }
    }

    private fun cancelPendingUpdate(pending: PendingWindowUpdate) {
        var closeEventDelivery = false
        var drainNext = false
        var withdrawDispatched = false
        synchronized(updateLock) {
            if (dispatchedUpdate === pending) {
                if (pending.isFullscreenUpdate() && pending.nativeDispatchStarted) {
                    pending.waiterDetached = true
                    return
                }
                if (!pending.backendRegistrationEntered) {
                    dispatchedUpdate = null
                    pending.cancelled = true
                    fullscreenBarrier?.takeIf {
                        it.operationId == pending.operationId && it.phase == FullscreenPhase.PreparedLocal
                    }?.let { fullscreenBarrier = null }
                    closeEventDelivery = takePendingEventDeliveryCloseLocked()
                    drainNext = true
                    return@synchronized
                }
                if (pending.cancellationRequested) return
                pending.cancellationRequested = true
                withdrawDispatched = true
                return@synchronized
            }
            pending.cancelled = true
            pendingUpdates.remove(pending)
        }
        if (closeEventDelivery) eventFlow.close()
        if (drainNext) dispatchNextUpdate()
        if (withdrawDispatched) manager.withdrawWindowUpdate(this, pending.operationId)
    }

    fun beginNativeUpdateDispatch(operationId: WindowOperationId): Boolean = synchronized(updateLock) {
        val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return@synchronized false
        if (mutableState.value.phase == WindowPhase.Open) {
            pending.backendRegistrationEntered = true
            if (!pending.isFullscreenUpdate()) pending.nativeDispatchStarted = true
            true
        } else {
            dispatchedUpdate = null
            pending.result.complete(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)))
            false
        }
    }

    fun beginFullscreenSelectorInvocation(operationId: WindowOperationId): Boolean = synchronized(updateLock) {
        val pending = dispatchedUpdate?.takeIf { it.operationId == operationId } ?: return@synchronized false
        val barrier = fullscreenBarrier?.takeIf {
            it.operationId == operationId && it.phase == FullscreenPhase.PreparedLocal
        } ?: return@synchronized false
        if (
            mutableState.value.phase != WindowPhase.Open ||
            pending.cancelled ||
            pending.cancellationRequested
        ) {
            return@synchronized false
        }
        pending.nativeDispatchStarted = true
        barrier.phase = FullscreenPhase.InvokingSelector
        true
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
    ): WindowOperationId? {
        var fullscreenOperationId: WindowOperationId? = null
        val closingState = synchronized(updateLock) {
            val current = mutableState.value
            if (current.phase != WindowPhase.Open) return null
            closeOperationId = operationId
            while (pendingUpdates.isNotEmpty()) {
                pendingUpdates.removeFirst().result.complete(
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)),
                )
            }
            dispatchedUpdate?.takeIf { !it.nativeDispatchStarted || it.isFullscreenUpdate() }?.let { pending ->
                dispatchedUpdate = null
                if (pending.isFullscreenUpdate()) fullscreenOperationId = pending.operationId
                pending.result.complete(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)))
            }
            fullscreenBarrier = null
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
        return fullscreenOperationId
    }

    fun finishClosing() {
        val closeEventDelivery = synchronized(updateLock) {
            val current = mutableState.value
            if (current.phase == WindowPhase.Closed) return
            check(current.phase == WindowPhase.Closing) { "window must enter Closing before its terminal close" }
            mutableState.value = current.copy(
                phase = WindowPhase.Closed,
                revision = WindowRevision(current.revision.value + 1L),
            )
            if (dispatchedUpdate == null && eventPublicationsInFlight == 0) {
                true
            } else {
                eventDeliveryClosePending = true
                false
            }
        }
        if (closeEventDelivery) eventFlow.close()
    }

    private fun takePendingEventDeliveryCloseLocked(): Boolean {
        if (!eventDeliveryClosePending || dispatchedUpdate != null || eventPublicationsInFlight != 0) return false
        eventDeliveryClosePending = false
        return true
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
        val changedProperties = buildSet {
            addAll(publication.forcedChanged)
            if (before.title != effective.title) add(WindowProperty.Title)
            if (before.resizable != effective.resizable) add(WindowProperty.Resizable)
            if (before.decorations != effective.decorations) add(WindowProperty.Decorations)
            if (before.systemButtons != effective.systemButtons) add(WindowProperty.SystemButtons)
            if (before.fullscreen != effective.fullscreen) add(WindowProperty.Fullscreen)
            if (before.level != effective.level) add(WindowProperty.Level)
        }
        if (changedProperties.isNotEmpty()) {
            publish(
                WindowEvent.PropertiesChanged(
                    state = effective,
                    changed = changedProperties,
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
    val forcedChanged: Set<WindowProperty> = emptySet(),
)

internal data class PendingWindowUpdate(
    val operationId: WindowOperationId,
    val expectedRevision: WindowRevision?,
    var update: WindowUpdate,
    val rejected: List<RejectedWindowField>,
    val result: CompletableDeferred<KadreResult<WindowUpdateOutcome>> = CompletableDeferred(),
    var cancelled: Boolean = false,
    var cancellationRequested: Boolean = false,
    var backendRegistrationEntered: Boolean = false,
    var nativeDispatchStarted: Boolean = false,
    var waiterDetached: Boolean = false,
)

private enum class FullscreenPhase { PreparedLocal, InvokingSelector, DrainingTerminals, AwaitingLocal, External }

private data class FullscreenBarrier(
    val operationId: WindowOperationId?,
    var target: FullscreenMode,
    var phase: FullscreenPhase,
    val terminalCallbacks: ArrayDeque<FullscreenTerminalCallback> = ArrayDeque(),
    var conflictTarget: FullscreenMode? = null,
    var willObserved: Boolean = false,
    var terminalResolved: Boolean = false,
)

private sealed interface FullscreenTerminalCallback {
    data class Observation(val observation: WindowFullscreenObservation) : FullscreenTerminalCallback
    data class Stimulus(val stimulus: WindowUpdateCommandStimulus) : FullscreenTerminalCallback
}

private enum class FullscreenTerminalKind { Did, DidFail }

private data class FullscreenTombstone(
    val target: FullscreenMode?,
    val kind: FullscreenTerminalKind,
) {
    fun matches(candidate: FullscreenMode, candidateKind: FullscreenTerminalKind): Boolean =
        target == candidate && kind == candidateKind
}

internal data class FullscreenObservationAcceptance(
    val accepted: Boolean,
    val terminalOperationId: WindowOperationId?,
)

private data class FullscreenResolution(
    val accepted: Boolean = true,
    val publication: WindowStatePublication? = null,
    val completion: PendingWindowUpdate? = null,
    val result: KadreResult<WindowUpdateOutcome>? = null,
    val diagnostics: List<KadreFailure> = emptyList(),
    val terminalOperationId: WindowOperationId? = null,
    val drain: Boolean = false,
) {
    companion object {
        val Accepted = FullscreenResolution()
        val Rejected = FullscreenResolution(accepted = false)
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

private fun windowCapabilities(
    publicWindowCapabilities: Boolean,
    enabledWindowUpdateCapabilities: Set<WindowProperty>,
    acceptedAttention: Set<WindowAttention>?,
    fullscreenAvailabilityFailure: KadreFailure.PlatformFailure?,
): WindowCapabilities = WindowCapabilities(
    title = if (publicWindowCapabilities) {
        enabledWindowUpdateCapabilities.capability(WindowProperty.Title, Unit)
    } else {
        unsupported(KadreOperation.UpdateWindow)
    },
    outerPosition = unsupported(KadreOperation.UpdateWindow),
    contentSize = enabledWindowUpdateCapabilities.capability(
        WindowProperty.ContentSize,
        LogicalSizeRange(null, null, null),
    ),
    minimumSize = enabledWindowUpdateCapabilities.capability(
        WindowProperty.MinimumSize,
        LogicalSizeRange(null, null, null),
    ),
    maximumSize = enabledWindowUpdateCapabilities.capability(
        WindowProperty.MaximumSize,
        LogicalSizeRange(null, null, null),
    ),
    resizable = enabledWindowUpdateCapabilities.capability(WindowProperty.Resizable, Unit),
    fullscreen = if (publicWindowCapabilities) {
        if (WindowProperty.Fullscreen in enabledWindowUpdateCapabilities) {
            Capability.Supported(
                setOf(FullscreenKind.Borderless),
                fullscreenAvailabilityFailure
                    ?.let(FeatureAvailability::Unavailable)
                    ?: FeatureAvailability.Available,
            )
        } else {
            unsupported(KadreOperation.UpdateWindow)
        }
    } else {
        unsupported(KadreOperation.UpdateWindow)
    },
    decorations = if (publicWindowCapabilities) {
        enabledWindowUpdateCapabilities.capability(
            WindowProperty.Decorations,
            setOf(WindowDecorations.System, WindowDecorations.Borderless),
        )
    } else {
        unsupported(KadreOperation.UpdateWindow)
    },
    systemButtons = if (publicWindowCapabilities) {
        enabledWindowUpdateCapabilities.capability(
            WindowProperty.SystemButtons,
            setOf(WindowSystemButtons.All, WindowSystemButtons.CloseOnly, WindowSystemButtons.None),
        )
    } else {
        unsupported(KadreOperation.UpdateWindow)
    },
    level = if (publicWindowCapabilities) {
        enabledWindowUpdateCapabilities.capability(
            WindowProperty.Level,
            setOf(WindowLevel.Normal, WindowLevel.Floating, WindowLevel.Modal),
        )
    } else {
        unsupported(KadreOperation.UpdateWindow)
    },
    transparency = enabledWindowUpdateCapabilities.capability(WindowProperty.Transparency, Unit),
    blurBehind = unsupported(KadreOperation.UpdateWindow),
    icon = unsupported(KadreOperation.UpdateWindow),
    attention = acceptedAttention?.let {
        Capability.Supported(it, FeatureAvailability.Available)
    } ?: unsupported(KadreOperation.RequestWindowAttention),
    contentProtection = enabledWindowUpdateCapabilities.capability(WindowProperty.ContentProtection, Unit),
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

private fun <T> Set<WindowProperty>.capability(
    property: WindowProperty,
    supported: T,
): Capability<T> = if (property in this) {
    Capability.Supported(supported, FeatureAvailability.Available)
} else {
    unsupported(KadreOperation.UpdateWindow)
}

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

internal val DEFAULT_RUNTIME_WINDOW_UPDATE_PROPERTIES: Set<WindowProperty> = setOf(
    WindowProperty.ContentSize,
    WindowProperty.MinimumSize,
    WindowProperty.MaximumSize,
    WindowProperty.Resizable,
)

private val DEFAULT_WINDOW_CONTENT_SIZE: LogicalSize = LogicalSize(800.0, 600.0)

private fun candidateFor(
    update: WindowUpdate,
    current: WindowState,
): WindowState? = try {
    current.copy(
        title = resolveTitle(update.title, current.title),
        contentSize = resolveContentSize(update.contentSize, current.contentSize),
        minimumSize = resolveOptionalSize(update.minimumSize, current.minimumSize),
        maximumSize = resolveOptionalSize(update.maximumSize, current.maximumSize),
        resizable = resolveResizable(update.resizable, current.resizable),
        fullscreen = resolveFullscreen(update.fullscreen, current.fullscreen),
        decorations = resolveDecorations(update.decorations, current.decorations),
        systemButtons = resolveSystemButtons(update.systemButtons, current.systemButtons),
        level = resolveLevel(update.level, current.level),
    )
} catch (_: IllegalArgumentException) {
    null
}

private fun resolveContentSize(
    change: PropertyChange<org.graphiks.kadre.surface.LogicalSize>,
    current: org.graphiks.kadre.surface.LogicalSize,
): org.graphiks.kadre.surface.LogicalSize = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear -> current
    PropertyChange.Unchanged -> current
}

private fun resolveTitle(
    change: PropertyChange<String>,
    current: String,
): String = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear,
    PropertyChange.Unchanged,
    -> current
}

private fun resolveOptionalSize(
    change: PropertyChange<org.graphiks.kadre.surface.LogicalSize>,
    current: org.graphiks.kadre.surface.LogicalSize?,
): org.graphiks.kadre.surface.LogicalSize? = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear -> null
    PropertyChange.Unchanged -> current
}

private fun resolveResizable(
    change: PropertyChange<Boolean>,
    current: Boolean,
): Boolean = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear -> current
    PropertyChange.Unchanged -> current
}

private fun resolveDecorations(
    change: PropertyChange<WindowDecorations>,
    current: WindowDecorations,
): WindowDecorations = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear,
    PropertyChange.Unchanged,
    -> current
}

private fun resolveSystemButtons(
    change: PropertyChange<WindowSystemButtons>,
    current: WindowSystemButtons,
): WindowSystemButtons = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear,
    PropertyChange.Unchanged,
    -> current
}

private fun resolveLevel(
    change: PropertyChange<WindowLevel>,
    current: WindowLevel,
): WindowLevel = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear,
    PropertyChange.Unchanged,
    -> current
}

private fun resolveFullscreen(
    change: PropertyChange<FullscreenMode>,
    current: FullscreenMode,
): FullscreenMode = when (change) {
    is PropertyChange.Set -> change.value
    PropertyChange.Clear,
    PropertyChange.Unchanged,
    -> current
}

private fun invalidRequiredClearField(
    update: WindowUpdate,
    supportedProperties: Set<WindowProperty>,
): String? = when {
    WindowProperty.Title in supportedProperties && update.title is PropertyChange.Clear -> "title"
    WindowProperty.ContentSize in supportedProperties && update.contentSize is PropertyChange.Clear -> "contentSize"
    WindowProperty.Resizable in supportedProperties && update.resizable is PropertyChange.Clear -> "resizable"
    WindowProperty.Fullscreen in supportedProperties && update.fullscreen is PropertyChange.Clear -> "fullscreen"
    WindowProperty.Decorations in supportedProperties && update.decorations is PropertyChange.Clear -> "decorations"
    WindowProperty.SystemButtons in supportedProperties && update.systemButtons is PropertyChange.Clear -> "systemButtons"
    WindowProperty.Level in supportedProperties && update.level is PropertyChange.Clear -> "level"
    else -> null
}

private fun invalidChromeField(
    update: WindowUpdate,
    current: WindowState,
): String? {
    val requestedButtons = (update.systemButtons as? PropertyChange.Set)?.value ?: return null
    if (requestedButtons == WindowSystemButtons.None) return null
    val effectiveDecorations = resolveDecorations(update.decorations, current.decorations)
    return if (
        effectiveDecorations == WindowDecorations.Borderless &&
        update.decorations !is PropertyChange.Set<WindowDecorations>
    ) {
        "systemButtons"
    } else {
        null
    }
}

/**
 * Produces the complete effective chrome target for one native mutation.
 *
 * A borderless window has no system buttons. Returning to a system title bar without an
 * explicit button value restores [WindowSystemButtons.All], rather than retaining an invisible
 * preference from the borderless state.
 */
private fun canonicalMutationUpdate(
    update: WindowUpdate,
    current: WindowState,
): WindowUpdate {
    val decorations = resolveDecorations(update.decorations, current.decorations)
    val requestedButtons = resolveSystemButtons(update.systemButtons, current.systemButtons)
    val effectiveButtons = when {
        decorations == WindowDecorations.Borderless -> WindowSystemButtons.None
        current.decorations == WindowDecorations.Borderless &&
            decorations == WindowDecorations.System &&
            update.systemButtons is PropertyChange.Unchanged -> WindowSystemButtons.All
        else -> requestedButtons
    }
    val buttonChange = if (
        update.systemButtons is PropertyChange.Unchanged &&
        effectiveButtons == current.systemButtons
    ) {
        PropertyChange.Unchanged
    } else {
        PropertyChange.Set(effectiveButtons)
    }
    return update.copy(systemButtons = buttonChange)
}

private fun mutationChanged(current: WindowState, candidate: WindowState): Boolean =
    current.title != candidate.title ||
        current.contentSize != candidate.contentSize ||
        current.minimumSize != candidate.minimumSize ||
        current.maximumSize != candidate.maximumSize ||
        current.resizable != candidate.resizable ||
        current.fullscreen != candidate.fullscreen ||
        current.decorations != candidate.decorations ||
        current.systemButtons != candidate.systemButtons ||
        current.level != candidate.level

private fun supportedMutationOnly(
    update: WindowUpdate,
    supportedProperties: Set<WindowProperty>,
): WindowUpdate = WindowUpdate(
    title = update.title.whenSupported(WindowProperty.Title, supportedProperties),
    contentSize = update.contentSize.whenSupported(WindowProperty.ContentSize, supportedProperties),
    minimumSize = update.minimumSize.whenSupported(WindowProperty.MinimumSize, supportedProperties),
    maximumSize = update.maximumSize.whenSupported(WindowProperty.MaximumSize, supportedProperties),
    resizable = update.resizable.whenSupported(WindowProperty.Resizable, supportedProperties),
    fullscreen = update.fullscreen.whenSupported(WindowProperty.Fullscreen, supportedProperties),
    decorations = update.decorations.whenSupported(WindowProperty.Decorations, supportedProperties),
    systemButtons = update.systemButtons.whenSupported(WindowProperty.SystemButtons, supportedProperties),
    level = update.level.whenSupported(WindowProperty.Level, supportedProperties),
    expectedRevision = update.expectedRevision,
)

private fun <T> PropertyChange<T>.whenSupported(
    property: WindowProperty,
    supportedProperties: Set<WindowProperty>,
): PropertyChange<T> = if (property in supportedProperties) this else PropertyChange.Unchanged

private fun updateOutcome(
    operationId: WindowOperationId,
    state: WindowState,
    rejected: List<RejectedWindowField>,
): WindowUpdateOutcome = if (rejected.isEmpty()) {
    WindowUpdateOutcome.Applied(operationId, state)
} else {
    WindowUpdateOutcome.PartiallyApplied(operationId, state, rejected)
}

private fun PendingWindowUpdate.isFullscreenUpdate(): Boolean =
    update.fullscreen is PropertyChange.Set

private fun WindowUpdateCommandStimulus.operationId(): WindowOperationId = when (this) {
    is WindowUpdateCommandStimulus.Applied -> operationId
    is WindowUpdateCommandStimulus.CommittedFailure -> operationId
    is WindowUpdateCommandStimulus.Failed -> operationId
    is WindowUpdateCommandStimulus.PartiallyApplied -> operationId
    is WindowUpdateCommandStimulus.Rejected -> operationId
}

private fun fullscreenTarget(update: WindowUpdate): FullscreenMode? =
    (update.fullscreen as? PropertyChange.Set)?.value

private fun FullscreenMode.isNativeFullscreenTarget(): Boolean =
    this == FullscreenMode.Windowed || this == FullscreenMode.Borderless

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
