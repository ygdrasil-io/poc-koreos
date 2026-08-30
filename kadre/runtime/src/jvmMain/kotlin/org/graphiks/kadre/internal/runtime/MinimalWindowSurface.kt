package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.graphiks.kadre.application.EventDeliverySpan
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateResetReason
import org.graphiks.kadre.input.InputStateRevision
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.KeyboardState
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerId
import org.graphiks.kadre.input.PointerState
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.SlowCollectorCancellationException
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.surface.SurfaceVisibility

internal class RuntimeWindowSurface(
    override val id: SurfaceId,
    initialSnapshot: SurfaceInitialSnapshot,
    private val commandPort: SurfaceCommandPort,
    private val commandsEnabled: Boolean,
    enabledCapabilities: SurfaceCapabilities,
    private val eventStampSource: () -> EventStamp,
    private val platform: KadrePlatform = KadrePlatform.Fake,
    private val failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
    private val deliveryPolicy: WindowDeliveryPolicy = KadrePolicies.Default.window,
    private val inputDeliveryPolicy: InputDeliveryPolicy = KadrePolicies.Default.input,
    private val maxCollectorsPerFlow: Int = KadrePolicies.Default.resources.maxEventCollectorsPerFlow,
    private val collectorAllocator: RuntimeEventCollectorAllocator = RuntimeEventCollectorAllocator(
        KadrePolicies.Default.resources.maxEventCollectorsPerSession,
    ),
    private val sessionFailureHandler: (KadreFailure) -> Unit = {},
) : HostSurface {
    private val lock = Any()
    private val updateMutex = Mutex()
    private var currentState = initialState(initialSnapshot)
    private var redrawTicket: SurfaceRedrawGeneration? = null
    private var nextRedrawTicket = 0L
    private val publications = BoundedSurfaceScheduler(
        discreteCapacity = deliveryPolicy.discreteEvents.ingressCapacity,
        geometryDelivery = deliveryPolicy.geometryChanges,
        redrawDelivery = deliveryPolicy.redrawRequests,
        classify = { checkNotNull(it.event).lane() },
        stamp = { checkNotNull(it.event).stamp },
        coalesce = ::coalescePublication,
    )
    private var publicationDrainActive = false
    private var terminalPublication: SurfacePublication? = null
    private val mutableState = MutableStateFlow(currentState)
    private val liveCapabilities = if (commandsEnabled) enabledCapabilities else unsupportedSurfaceCapabilities()
    private val mutableCapabilities = MutableStateFlow(liveCapabilities)
    private val eventCollectorGate = collectorAllocator.newGate(maxCollectorsPerFlow)
    private val surfaceInput = RuntimeSurfaceInput(
        deliveryPolicy = inputDeliveryPolicy,
        eventStampSource = eventStampSource,
        eventCollectorGate = collectorAllocator.newGate(maxCollectorsPerFlow),
        failureReporter = ::safeReport,
        sessionFailureHandler = sessionFailureHandler,
    )
    private val eventSubscribersLock = Any()
    private val eventSubscribers = linkedMapOf<SurfaceEventSubscriber, RuntimeEventCollectorLease>()
    private var eventsTerminal: FlowTerminal? = null

    override val state: StateFlow<SurfaceState> = mutableState.asStateFlow()
    override val capabilities: StateFlow<SurfaceCapabilities> = mutableCapabilities.asStateFlow()
    override val events: Flow<SurfaceEvent> = flow {
        val subscriber = SurfaceEventSubscriber(deliveryPolicy)
        when (val registration = registerEventSubscriber(subscriber)) {
            SubscriberRegistration.Registered -> Unit
            SubscriberRegistration.Closed -> return@flow
            is SubscriberRegistration.Failed -> throw KadreException(registration.failure)
        }
        try {
            while (true) {
                val event = subscriber.next() ?: break
                emit(event)
            }
        } finally {
            unregisterEventSubscriber(subscriber)
        }
    }
    override val input: SurfaceInput = surfaceInput

    override fun requestRedraw(): KadreResult<Unit> {
        var admittedTicket: SurfaceRedrawGeneration? = null
        val immediate = synchronized(lock) {
            when {
                currentState.attachment == SurfaceAttachmentState.Detached ->
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))

                !commandsEnabled -> KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = false))
                redrawTicket != null -> KadreResult.Success(Unit)
                else -> {
                    check(nextRedrawTicket < Long.MAX_VALUE) { "surface redraw ticket space exhausted" }
                    val ticket = SurfaceRedrawGeneration(nextRedrawTicket++)
                    redrawTicket = ticket
                    admittedTicket = ticket
                    null
                }
            }
        }
        if (immediate != null) return immediate

        val portOutcome = try {
            commandPort.requestRedraw(SurfaceRedrawCommand(id, checkNotNull(admittedTicket)))
        } catch (cause: Exception) {
            portFailure("redraw-exception", cause)
        } catch (cause: LinkageError) {
            portFailure("redraw-exception", cause)
        }
        var adapterFailure: KadreFailure.PlatformFailure? = null
        val outcome = when (portOutcome) {
            is KadreResult.Success -> portOutcome
            is KadreResult.Failure -> {
                val normalised = normaliseRedrawFailure(portOutcome.reason)
                adapterFailure = normalised.adapterFailure
                KadreResult.Failure(normalised.failure)
            }
        }
        if (outcome is KadreResult.Failure) {
            synchronized(lock) {
                if (redrawTicket == admittedTicket) redrawTicket = null
            }
        }
        adapterFailure?.let(::reportAdapterFailure)
        return outcome
    }

    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> = updateMutex.withLock {
        val admission = synchronized(lock) {
            if (currentState.attachment == SurfaceAttachmentState.Detached) {
                return@withLock KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
            }
            update.expectedRevision?.let { expected ->
                if (expected != currentState.revision) {
                    return@withLock KadreResult.Failure(
                        KadreFailure.StaleRevision(expected.value, currentState.revision.value),
                    )
                }
            }
            invalidClearField(update)?.let { field ->
                return@withLock KadreResult.Failure(KadreFailure.InvalidRequest(field))
            }
            prepareUpdateLocked(update)
        }
        if (admission.command == null) {
            val snapshot = currentStateSnapshot()
            publishStateAtLeast(snapshot)
            return@withLock successfulUpdateOutcome(snapshot, admission.rejected)
        }

        val portResult = try {
            commandPort.apply(admission.command)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            portFailure("update-exception", cause)
        } catch (cause: LinkageError) {
            portFailure("update-exception", cause)
        }
        val backend = when (val result = portResult) {
            is KadreResult.Failure -> {
                val normalised = normaliseUpdateFailure(result.reason)
                normalised.adapterFailure?.let(::reportAdapterFailure)
                return@withLock KadreResult.Failure(normalised.failure)
            }
            is KadreResult.Success -> result.value
        }

        var stateToPublish: SurfaceState? = null
        var adapterFailures: List<KadreFailure.PlatformFailure> = emptyList()
        val result = synchronized(lock) {
            if (currentState.attachment == SurfaceAttachmentState.Detached) {
                return@synchronized KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
            }
            val committed = commitUpdateLocked(admission, backend)
            if (committed.state != currentState) {
                currentState = committed.state
            }
            stateToPublish = currentState
            adapterFailures = committed.adapterFailures
            successfulUpdateOutcome(currentState, committed.rejected)
        }
        stateToPublish?.let(::publishStateAtLeast)
        adapterFailures.forEach(::reportAdapterFailure)
        result
    }

    internal fun accept(stimulus: SurfaceStimulus): Boolean {
        if (stimulus.surfaceId != id) return false
        if (stimulus is SurfaceStimulus.Detached) return detach()
        if (stimulus.isInputStimulus()) {
            return synchronized(lock) {
                currentState.attachment != SurfaceAttachmentState.Detached && surfaceInput.accept(stimulus)
            }
        }

        var admission: PublicationAdmission? = null
        var resetInputForFocusLoss = false
        val accepted = synchronized(lock) {
            if (currentState.attachment == SurfaceAttachmentState.Detached) return@synchronized false
            val publication = when (stimulus) {
                is SurfaceStimulus.MetricsChanged -> metricsPublicationLocked(stimulus.metrics)
                is SurfaceStimulus.FocusChanged -> focusPublicationLocked(stimulus.focus).also { publication ->
                    resetInputForFocusLoss = publication != null && stimulus.focus == SurfaceFocus.Unfocused
                }
                is SurfaceStimulus.VisibilityChanged -> visibilityPublicationLocked(
                    stimulus.visibility,
                    stimulus.occlusion,
                )

                is SurfaceStimulus.ThemeChanged -> themePublicationLocked(stimulus.theme)
                is SurfaceStimulus.RedrawConsumed -> redrawPublicationLocked(stimulus.generation)
                is SurfaceStimulus.KeyChanged,
                is SurfaceStimulus.InputObservationChanged,
                is SurfaceStimulus.PointerEntered,
                is SurfaceStimulus.PointerMoved,
                is SurfaceStimulus.PointerButtonChanged,
                is SurfaceStimulus.PointerLeft,
                is SurfaceStimulus.Scroll,
                -> error("input stimuli are handled before surface publication")
                is SurfaceStimulus.Detached -> error("handled before lock")
            } ?: return@synchronized false
            admission = enqueuePublicationLocked(publication)
            true
        }
        if (resetInputForFocusLoss) surfaceInput.focusLost()
        admission?.let(::finishPublicationAdmission)
        return accepted
    }

    internal fun detach(): Boolean {
        var admission: PublicationAdmission? = null
        val detached = synchronized(lock) {
            if (currentState.attachment == SurfaceAttachmentState.Detached) return@synchronized false
            admission = terminaliseLocked(failure = null, failSession = false)
            true
        }
        admission?.let(::finishPublicationAdmission)
        return detached
    }

    private fun prepareUpdateLocked(update: SurfaceUpdate): UpdateAdmission {
        val rejected = mutableListOf<RejectedSurfaceField>()
        val cursor = admitCursor(update.cursor, rejected)
        val pointerCapture = admitField(
            update.pointerCapture,
            SurfaceProperty.PointerCapture,
            liveCapabilities.pointerCapture,
            rejected,
        )
        val hitTesting = admitField(
            update.hitTesting,
            SurfaceProperty.HitTesting,
            liveCapabilities.hitTesting,
            rejected,
        )
        val inputDefaultBehavior = admitField(
            update.inputDefaultBehavior,
            SurfaceProperty.InputDefaultBehavior,
            liveCapabilities.inputDefaultBehavior,
            rejected,
        )
        val command = SurfaceUpdateCommand(id, cursor, pointerCapture, hitTesting, inputDefaultBehavior)
            .takeUnless(SurfaceUpdateCommand::isEmpty)
        return UpdateAdmission(command, rejected)
    }

    private fun admitCursor(
        change: PropertyChange<CursorStyle>,
        rejected: MutableList<RejectedSurfaceField>,
    ): PropertyChange<CursorStyle> {
        if (change is PropertyChange.Unchanged) return change
        val capability = if (change is PropertyChange.Set && change.value is CursorStyle.Custom) {
            liveCapabilities.customCursor
        } else {
            liveCapabilities.cursor
        }
        val failure = capabilityFailure(capability)
        if (failure != null) {
            rejected += RejectedSurfaceField(SurfaceProperty.Cursor, failure)
            return PropertyChange.Unchanged
        }
        if (
            change is PropertyChange.Set &&
            change.value is CursorStyle.System &&
            capability is Capability.Supported<*> &&
            capability.constraints is Set<*> &&
            change.value.icon !in capability.constraints
        ) {
            rejected += RejectedSurfaceField(
                SurfaceProperty.Cursor,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            )
            return PropertyChange.Unchanged
        }
        return change
    }

    private fun <T> admitField(
        change: PropertyChange<T>,
        property: SurfaceProperty,
        capability: Capability<Set<T>>,
        rejected: MutableList<RejectedSurfaceField>,
    ): PropertyChange<T> {
        if (change is PropertyChange.Unchanged) return change
        val failure = capabilityFailure(capability)
        if (failure != null) {
            rejected += RejectedSurfaceField(property, failure)
            return PropertyChange.Unchanged
        }
        if (
            change is PropertyChange.Set &&
            capability is Capability.Supported &&
            change.value !in capability.constraints
        ) {
            rejected += RejectedSurfaceField(property, KadreFailure.Unsupported(KadreOperation.UpdateSurface))
            return PropertyChange.Unchanged
        }
        return change
    }

    private fun commitUpdateLocked(
        admission: UpdateAdmission,
        backend: SurfaceUpdateCommandOutcome,
    ): CommittedUpdate {
        val command = checkNotNull(admission.command)
        val rejected = admission.rejected.toMutableList()
        val adapterFailures = mutableListOf<KadreFailure.PlatformFailure>()
        var next = currentState
        next = commitField(
            command.cursor,
            backend.cursor,
            SurfaceProperty.Cursor,
            rejected,
            adapterFailures,
            next,
        ) { state, value -> state.copy(cursor = value) }
        next = commitField(
            command.pointerCapture,
            backend.pointerCapture,
            SurfaceProperty.PointerCapture,
            rejected,
            adapterFailures,
            next,
        ) { state, value -> state.copy(pointerCapture = value) }
        next = commitField(
            command.hitTesting,
            backend.hitTesting,
            SurfaceProperty.HitTesting,
            rejected,
            adapterFailures,
            next,
        ) { state, value -> state.copy(hitTesting = value) }
        next = commitField(
            command.inputDefaultBehavior,
            backend.inputDefaultBehavior,
            SurfaceProperty.InputDefaultBehavior,
            rejected,
            adapterFailures,
            next,
        ) { state, value -> state.copy(inputDefaultBehavior = value) }
        if (next != currentState) next = next.copy(revision = currentState.revision.next())
        return CommittedUpdate(next, rejected, adapterFailures)
    }

    private fun <T> commitField(
        requested: PropertyChange<T>,
        outcome: SurfaceFieldOutcome<T>,
        property: SurfaceProperty,
        rejected: MutableList<RejectedSurfaceField>,
        adapterFailures: MutableList<KadreFailure.PlatformFailure>,
        state: SurfaceState,
        applyValue: (SurfaceState, T) -> SurfaceState,
    ): SurfaceState {
        if (requested is PropertyChange.Unchanged) return state
        return when (outcome) {
            is SurfaceFieldOutcome.Applied -> applyValue(state, outcome.value)
            is SurfaceFieldOutcome.Rejected -> state.also {
                val normalised = normaliseFieldFailure(property, outcome.failure)
                rejected += RejectedSurfaceField(
                    property,
                    normalised.failure,
                )
                normalised.adapterFailure?.let(adapterFailures::add)
            }

            SurfaceFieldOutcome.Unchanged -> state.also {
                rejected += RejectedSurfaceField(property, KadreFailure.Unsupported(KadreOperation.UpdateSurface))
            }
        }
    }

    private fun metricsPublicationLocked(metrics: SurfaceMetrics): SurfacePublication? {
        if (
            currentState.logicalSize == metrics.logicalSize &&
            currentState.physicalSize == metrics.physicalSize &&
            currentState.scaleFactor == metrics.scaleFactor &&
            currentState.safeAreaInsets == metrics.safeAreaInsets
        ) return null
        currentState = currentState.copy(
            logicalSize = metrics.logicalSize,
            physicalSize = metrics.physicalSize,
            scaleFactor = metrics.scaleFactor,
            safeAreaInsets = metrics.safeAreaInsets,
            revision = currentState.revision.next(),
        )
        return SurfacePublication(
            state = currentState,
            event = SurfaceEvent.MetricsChanged(currentState, eventStampSource()),
        )
    }

    private fun focusPublicationLocked(focus: SurfaceFocus): SurfacePublication? {
        if (currentState.focus == focus) return null
        currentState = currentState.copy(focus = focus, revision = currentState.revision.next())
        return SurfacePublication(
            state = currentState,
            event = SurfaceEvent.FocusChanged(currentState, eventStampSource()),
        )
    }

    private fun visibilityPublicationLocked(
        visibility: SurfaceVisibility,
        occlusion: SurfaceOcclusion,
    ): SurfacePublication? {
        if (currentState.visibility == visibility && currentState.occlusion == occlusion) return null
        currentState = currentState.copy(
            visibility = visibility,
            occlusion = occlusion,
            revision = currentState.revision.next(),
        )
        return SurfacePublication(
            state = currentState,
            event = SurfaceEvent.VisibilityChanged(currentState, eventStampSource()),
        )
    }

    private fun themePublicationLocked(theme: SurfaceTheme): SurfacePublication? {
        if (currentState.theme == theme) return null
        currentState = currentState.copy(theme = theme, revision = currentState.revision.next())
        return SurfacePublication(
            state = currentState,
            event = SurfaceEvent.ThemeChanged(currentState, eventStampSource()),
        )
    }

    private fun redrawPublicationLocked(generation: SurfaceRedrawGeneration): SurfacePublication? {
        if (redrawTicket != generation) return null
        redrawTicket = null
        return SurfacePublication(
            state = currentState,
            event = SurfaceEvent.RedrawRequested(currentState.revision, eventStampSource()),
        )
    }

    private fun enqueuePublicationLocked(publication: SurfacePublication): PublicationAdmission {
        return when (val offered = publications.offer(publication)) {
            QueueOfferResult.Accepted -> PublicationAdmission(shouldDrain = ensurePublicationDrainLocked())
            is QueueOfferResult.Dropped -> PublicationAdmission(
                shouldDrain = ensurePublicationDrainLockedIfNeeded(),
                stateToPublish = if (offered.latestWasDropped) publication.state else null,
                reportOverflow = true,
            )

            QueueOfferResult.DiscreteOverflow -> terminaliseLocked(
                failure = KadreFailure.SourceOverflow(KadreResourceKind.Surface),
                failSession = deliveryPolicy.discreteEvents.ingressOverflow ==
                    org.graphiks.kadre.policy.IngressOverflowAction.FailSession,
            )

            is QueueOfferResult.ContinuousOverflow -> when (offered.action) {
                ContinuousOverflowAction.DropOldestAndReport -> error("drop handled by scheduler")
                ContinuousOverflowAction.DropLatestAndReport -> error("drop handled by scheduler")
                ContinuousOverflowAction.CloseSource -> terminaliseLocked(
                    failure = KadreFailure.SourceOverflow(KadreResourceKind.Surface),
                    failSession = false,
                )

                ContinuousOverflowAction.FailSession -> terminaliseLocked(
                    failure = KadreFailure.SourceOverflow(KadreResourceKind.Surface),
                    failSession = true,
                )
            }
        }
    }

    private fun drainPublications() {
        while (true) {
            val publication = synchronized(lock) {
                publications.poll() ?: terminalPublication?.also { terminalPublication = null } ?: run {
                    publicationDrainActive = false
                    return
                }
            }
            publication.capabilities?.let { mutableCapabilities.value = it }
            publication.state?.let(::publishStateAtLeast)
            publication.event?.let(::publishEvent)
            if (publication.terminal) {
                surfaceInput.close(publication.failure)
                closeEvents(publication.failure)
            }
        }
    }

    private fun publishEvent(event: SurfaceEvent) {
        val subscribers = synchronized(eventSubscribersLock) {
            if (eventsTerminal != null) emptyList() else eventSubscribers.keys.toList()
        }
        var closeSource = false
        var failSession = false
        subscribers.forEach { subscriber ->
            when (subscriber.offer(event.copyForDelivery())) {
                SubscriberOfferResult.Accepted -> Unit
                SubscriberOfferResult.Dropped -> safeReport(
                    KadreException(KadreFailure.SourceOverflow(KadreResourceKind.Surface)),
                )
                SubscriberOfferResult.CloseSource -> closeSource = true
                SubscriberOfferResult.FailSession -> failSession = true
            }
        }
        if (closeSource || failSession) closeFromDeliveryOverflow(failSession)
    }

    private fun closeEvents(failure: KadreFailure?) {
        val subscribers = synchronized(eventSubscribersLock) {
            if (eventsTerminal != null) return
            eventsTerminal = failure?.let(FlowTerminal::Failed) ?: FlowTerminal.Closed
            eventSubscribers.keys.toList()
        }
        subscribers.forEach { subscriber -> subscriber.terminate(failure?.let(::KadreException), drain = true) }
    }

    private fun registerEventSubscriber(subscriber: SurfaceEventSubscriber): SubscriberRegistration =
        synchronized(eventSubscribersLock) {
            when (val terminal = eventsTerminal) {
                FlowTerminal.Closed -> SubscriberRegistration.Closed
                is FlowTerminal.Failed -> SubscriberRegistration.Failed(terminal.failure)
                null -> when (val admission = eventCollectorGate.tryAcquire()) {
                    is KadreResult.Failure -> SubscriberRegistration.Failed(admission.reason)
                    is KadreResult.Success -> {
                        check(eventSubscribers.put(subscriber, admission.value) == null)
                        SubscriberRegistration.Registered
                    }
                }
            }
        }

    private fun unregisterEventSubscriber(subscriber: SurfaceEventSubscriber) {
        val lease = synchronized(eventSubscribersLock) { eventSubscribers.remove(subscriber) }
        lease?.close()
        subscriber.dispose()
    }

    private fun terminaliseLocked(
        failure: KadreFailure?,
        failSession: Boolean,
    ): PublicationAdmission {
        redrawTicket = null
        currentState = currentState.copy(
            attachment = SurfaceAttachmentState.Detached,
            revision = currentState.revision.next(),
        )
        terminalPublication = SurfacePublication(
            state = currentState,
            capabilities = unsupportedSurfaceCapabilities(),
            terminal = true,
            failure = failure,
        )
        return PublicationAdmission(
            shouldDrain = ensurePublicationDrainLocked(),
            capabilitiesToPublish = unsupportedSurfaceCapabilities(),
            stateToPublish = currentState,
            reportOverflow = failure != null,
            failSession = if (failSession) failure else null,
        )
    }

    private fun ensurePublicationDrainLocked(): Boolean {
        if (publicationDrainActive) return false
        publicationDrainActive = true
        return true
    }

    private fun ensurePublicationDrainLockedIfNeeded(): Boolean =
        if (publications.isEmpty() && terminalPublication == null) false else ensurePublicationDrainLocked()

    private fun finishPublicationAdmission(admission: PublicationAdmission) {
        admission.capabilitiesToPublish?.let { mutableCapabilities.value = it }
        admission.stateToPublish?.let(::publishStateAtLeast)
        if (admission.reportOverflow) {
            safeReport(KadreException(KadreFailure.SourceOverflow(KadreResourceKind.Surface)))
        }
        if (admission.shouldDrain) drainPublications()
        admission.failSession?.let(::safeFailSession)
    }

    private fun closeFromDeliveryOverflow(failSession: Boolean) {
        val admission = synchronized(lock) {
            if (currentState.attachment == SurfaceAttachmentState.Detached) return
            terminaliseLocked(
                failure = KadreFailure.SourceOverflow(KadreResourceKind.Surface),
                failSession = failSession,
            )
        }
        finishPublicationAdmission(admission)
    }

    private fun safeFailSession(failure: KadreFailure) {
        try {
            sessionFailureHandler(failure)
        } catch (cause: Exception) {
            safeReport(cause)
        } catch (cause: LinkageError) {
            safeReport(cause)
        }
    }

    private fun currentStateSnapshot(): SurfaceState = synchronized(lock) { currentState }

    private fun publishStateAtLeast(snapshot: SurfaceState) {
        while (true) {
            val visible = mutableState.value
            if (visible.revision.value >= snapshot.revision.value) return
            if (mutableState.compareAndSet(visible, snapshot)) return
        }
    }

    private fun <T> portFailure(code: String, cause: Throwable): KadreResult<T> {
        safeReport(cause)
        return KadreResult.Failure(
            KadreFailure.PlatformFailure(platform, "surface-command-port", code),
        )
    }

    private fun normaliseRedrawFailure(failure: KadreFailure): NormalisedPortFailure =
        if (
            failure == KadreFailure.Closed(KadreResourceKind.Surface) ||
            failure is KadreFailure.TemporarilyUnavailable ||
            failure is KadreFailure.PlatformFailure
        ) {
            NormalisedPortFailure(failure)
        } else {
            invalidPortFailure("invalid-redraw-failure")
        }

    private fun normaliseUpdateFailure(failure: KadreFailure): NormalisedPortFailure =
        if (
            failure is KadreFailure.InvalidRequest && failure.field in SURFACE_UPDATE_FIELDS ||
            failure == KadreFailure.Closed(KadreResourceKind.Surface) ||
            failure is KadreFailure.StaleRevision ||
            failure is KadreFailure.ResourceLimitExceeded &&
            failure.resource in SURFACE_UPDATE_LIMIT_RESOURCES ||
            failure is KadreFailure.TemporarilyUnavailable ||
            failure is KadreFailure.PlatformFailure
        ) {
            NormalisedPortFailure(failure)
        } else {
            invalidPortFailure("invalid-update-failure")
        }

    private fun normaliseFieldFailure(
        property: SurfaceProperty,
        failure: KadreFailure,
    ): NormalisedPortFailure =
        if (
            failure == KadreFailure.Unsupported(KadreOperation.UpdateSurface) ||
            failure is KadreFailure.InteractionRequired ||
            failure is KadreFailure.InvalidRequest && failure.field == property.fieldName ||
            failure is KadreFailure.ResourceLimitExceeded &&
            failure.resource in SURFACE_UPDATE_LIMIT_RESOURCES ||
            failure is KadreFailure.TemporarilyUnavailable ||
            failure is KadreFailure.PlatformFailure
        ) {
            NormalisedPortFailure(failure)
        } else {
            invalidPortFailure("invalid-field-failure")
        }

    private fun invalidPortFailure(code: String): NormalisedPortFailure {
        val failure = KadreFailure.PlatformFailure(platform, "surface-command-port", code)
        return NormalisedPortFailure(failure, failure)
    }

    private fun reportAdapterFailure(failure: KadreFailure.PlatformFailure) {
        safeReport(KadreException(failure))
    }

    private fun safeReport(cause: Throwable) {
        try {
            failureReporter.report(cause)
        } catch (_: Exception) {
            // Diagnostics cannot destabilise the surface command boundary.
        } catch (_: LinkageError) {
            // Diagnostics cannot destabilise the surface command boundary.
        }
    }

    private fun coalescePublication(
        previous: SurfacePublication,
        latest: SurfacePublication,
    ): SurfacePublication = latest.copy(
        event = coalesceSurfaceEvent(checkNotNull(previous.event), checkNotNull(latest.event)),
    )

    private data class UpdateAdmission(
        val command: SurfaceUpdateCommand?,
        val rejected: List<RejectedSurfaceField>,
    )

    private data class CommittedUpdate(
        val state: SurfaceState,
        val rejected: List<RejectedSurfaceField>,
        val adapterFailures: List<KadreFailure.PlatformFailure>,
    )

    private data class NormalisedPortFailure(
        val failure: KadreFailure,
        val adapterFailure: KadreFailure.PlatformFailure? = null,
    )

    private data class SurfacePublication(
        val state: SurfaceState? = null,
        val event: SurfaceEvent? = null,
        val capabilities: SurfaceCapabilities? = null,
        val terminal: Boolean = false,
        val failure: KadreFailure? = null,
    )

    private data class PublicationAdmission(
        val shouldDrain: Boolean,
        val capabilitiesToPublish: SurfaceCapabilities? = null,
        val stateToPublish: SurfaceState? = null,
        val reportOverflow: Boolean = false,
        val failSession: KadreFailure? = null,
    )

    private companion object {
        val SURFACE_UPDATE_FIELDS = setOf(
            "cursor",
            "pointerCapture",
            "hitTesting",
            "inputDefaultBehavior",
        )
        val SURFACE_UPDATE_LIMIT_RESOURCES = setOf(
            KadreResourceKind.ImageResource,
            KadreResourceKind.RetainedPayload,
        )
    }
}

private enum class SurfaceEventLane { Discrete, Geometry, Redraw }

private sealed interface QueueOfferResult {
    data object Accepted : QueueOfferResult
    data class Dropped(val latestWasDropped: Boolean) : QueueOfferResult
    data object DiscreteOverflow : QueueOfferResult
    data class ContinuousOverflow(val action: ContinuousOverflowAction) : QueueOfferResult
}

private class BoundedSurfaceScheduler<T>(
    private val discreteCapacity: Int,
    private val geometryDelivery: ContinuousDelivery,
    private val redrawDelivery: ContinuousDelivery,
    private val classify: (T) -> SurfaceEventLane,
    private val stamp: (T) -> EventStamp,
    private val coalesce: (T, T) -> T,
) {
    private val entries = mutableListOf<T>()

    fun offer(value: T): QueueOfferResult = when (val lane = classify(value)) {
        SurfaceEventLane.Discrete -> {
            if (entries.count { classify(it) == SurfaceEventLane.Discrete } >= discreteCapacity) {
                QueueOfferResult.DiscreteOverflow
            } else {
                entries += value
                QueueOfferResult.Accepted
            }
        }

        SurfaceEventLane.Geometry,
        SurfaceEventLane.Redraw,
        -> offerContinuous(value, lane, if (lane == SurfaceEventLane.Geometry) geometryDelivery else redrawDelivery)
    }

    fun poll(): T? {
        if (entries.isEmpty()) return null
        var nextIndex = 0
        var nextSequence = stamp(entries[0]).sequence.value
        for (index in 1 until entries.size) {
            val sequence = stamp(entries[index]).sequence.value
            if (sequence < nextSequence) {
                nextIndex = index
                nextSequence = sequence
            }
        }
        return entries.removeAt(nextIndex)
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    fun clear() {
        entries.clear()
    }

    private fun offerContinuous(
        value: T,
        lane: SurfaceEventLane,
        delivery: ContinuousDelivery,
    ): QueueOfferResult = when (delivery) {
        ContinuousDelivery.Latest,
        ContinuousDelivery.Coalesced,
        -> {
            val lastBarrier = entries.asSequence()
                .filter { classify(it) == SurfaceEventLane.Discrete }
                .maxOfOrNull { stamp(it).sequence.value }
                ?: -1L
            val existingIndex = entries.indexOfFirst {
                classify(it) == lane && stamp(it).sequence.value > lastBarrier
            }
            if (existingIndex >= 0) {
                entries[existingIndex] = coalesce(entries[existingIndex], value)
            } else {
                entries += value
            }
            QueueOfferResult.Accepted
        }

        is ContinuousDelivery.Buffered -> {
            val matching = entries.indices.filter { classify(entries[it]) == lane }
            if (matching.size < delivery.capacity) {
                entries += value
                QueueOfferResult.Accepted
            } else {
                when (delivery.onOverflow) {
                    ContinuousOverflowAction.DropOldestAndReport -> {
                        val oldest = matching.minBy { stamp(entries[it]).sequence.value }
                        entries.removeAt(oldest)
                        entries += value
                        QueueOfferResult.Dropped(latestWasDropped = false)
                    }

                    ContinuousOverflowAction.DropLatestAndReport ->
                        QueueOfferResult.Dropped(latestWasDropped = true)

                    ContinuousOverflowAction.CloseSource,
                    ContinuousOverflowAction.FailSession,
                    -> QueueOfferResult.ContinuousOverflow(delivery.onOverflow)
                }
            }
        }
    }
}

private sealed interface SubscriberRegistration {
    data object Registered : SubscriberRegistration
    data object Closed : SubscriberRegistration
    data class Failed(val failure: KadreFailure) : SubscriberRegistration
}

private sealed interface SubscriberOfferResult {
    data object Accepted : SubscriberOfferResult
    data object Dropped : SubscriberOfferResult
    data object CloseSource : SubscriberOfferResult
    data object FailSession : SubscriberOfferResult
}

private sealed interface SubscriberTerminal {
    data object Closed : SubscriberTerminal
    data class Failed(val cause: Throwable) : SubscriberTerminal
}

private class SurfaceEventSubscriber(
    private val policy: WindowDeliveryPolicy,
) {
    private val lock = Any()
    private val signal = Channel<Unit>(capacity = 1)
    private val scheduler = BoundedSurfaceScheduler(
        discreteCapacity = policy.discreteEvents.collectorCapacity,
        geometryDelivery = policy.geometryChanges,
        redrawDelivery = policy.redrawRequests,
        classify = SurfaceEvent::lane,
        stamp = SurfaceEvent::stamp,
        coalesce = ::coalesceSurfaceEvent,
    )
    private var terminal: SubscriberTerminal? = null

    fun offer(event: SurfaceEvent): SubscriberOfferResult {
        val result = synchronized(lock) {
            if (terminal != null) return SubscriberOfferResult.Accepted
            when (val offered = scheduler.offer(event)) {
                QueueOfferResult.Accepted -> SubscriberOfferResult.Accepted
                is QueueOfferResult.Dropped -> SubscriberOfferResult.Dropped
                QueueOfferResult.DiscreteOverflow -> when (policy.discreteEvents.collectorOverflow) {
                    CollectorOverflowAction.CancelSlowCollector -> {
                        scheduler.clear()
                        terminal = SubscriberTerminal.Failed(
                            SlowCollectorCancellationException("surface event collector exceeded its policy capacity"),
                        )
                        SubscriberOfferResult.Accepted
                    }

                    CollectorOverflowAction.CloseSource -> SubscriberOfferResult.CloseSource
                    CollectorOverflowAction.FailSession -> SubscriberOfferResult.FailSession
                }

                is QueueOfferResult.ContinuousOverflow -> when (offered.action) {
                    ContinuousOverflowAction.DropOldestAndReport,
                    ContinuousOverflowAction.DropLatestAndReport,
                    -> SubscriberOfferResult.Dropped

                    ContinuousOverflowAction.CloseSource -> SubscriberOfferResult.CloseSource
                    ContinuousOverflowAction.FailSession -> SubscriberOfferResult.FailSession
                }
            }
        }
        signal.trySend(Unit)
        return result
    }

    suspend fun next(): SurfaceEvent? {
        while (true) {
            val terminalSnapshot = synchronized(lock) {
                scheduler.poll()?.let { return it }
                terminal
            }
            when (terminalSnapshot) {
                SubscriberTerminal.Closed -> return null
                is SubscriberTerminal.Failed -> throw terminalSnapshot.cause
                null -> signal.receive()
            }
        }
    }

    fun terminate(cause: Throwable?, drain: Boolean) {
        synchronized(lock) {
            if (terminal != null) return
            if (!drain) scheduler.clear()
            terminal = cause?.let(SubscriberTerminal::Failed) ?: SubscriberTerminal.Closed
        }
        signal.trySend(Unit)
    }

    fun dispose() {
        synchronized(lock) { scheduler.clear() }
        signal.cancel()
    }
}

private sealed interface FlowTerminal {
    data object Closed : FlowTerminal
    data class Failed(val failure: KadreFailure) : FlowTerminal
}

private fun SurfaceEvent.lane(): SurfaceEventLane = when (this) {
    is SurfaceEvent.MetricsChanged -> SurfaceEventLane.Geometry
    is SurfaceEvent.RedrawRequested -> SurfaceEventLane.Redraw
    is SurfaceEvent.FocusChanged,
    is SurfaceEvent.VisibilityChanged,
    is SurfaceEvent.ThemeChanged,
    -> SurfaceEventLane.Discrete
}

private fun SurfaceEvent.copyForDelivery(): SurfaceEvent = withStamp(stamp.copy())

private fun coalesceSurfaceEvent(previous: SurfaceEvent, latest: SurfaceEvent): SurfaceEvent {
    check(previous.lane() == latest.lane() && latest.lane() != SurfaceEventLane.Discrete)
    return latest.withStamp(coalescedStamp(previous.stamp, latest.stamp))
}

private fun SurfaceEvent.withStamp(value: EventStamp): SurfaceEvent = when (this) {
    is SurfaceEvent.MetricsChanged -> copy(stamp = value)
    is SurfaceEvent.FocusChanged -> copy(stamp = value)
    is SurfaceEvent.VisibilityChanged -> copy(stamp = value)
    is SurfaceEvent.ThemeChanged -> copy(stamp = value)
    is SurfaceEvent.RedrawRequested -> copy(stamp = value)
}

private fun coalescedStamp(previous: EventStamp, latest: EventStamp): EventStamp {
    val first = previous.deliverySpan?.firstSequence ?: previous.sequence
    val previousCount = previous.deliverySpan?.eventCount ?: 1L
    val latestCount = latest.deliverySpan?.eventCount ?: 1L
    val eventCount = Math.addExact(previousCount, latestCount)
    return latest.copy(
        deliverySpan = EventDeliverySpan(first, latest.sequence, eventCount),
    )
}

private class RuntimeSurfaceInput(
    private val deliveryPolicy: InputDeliveryPolicy,
    private val eventStampSource: () -> EventStamp,
    private val eventCollectorGate: RuntimeEventCollectorGate,
    private val failureReporter: (Throwable) -> Unit,
    private val sessionFailureHandler: (KadreFailure) -> Unit,
) : SurfaceInput {
    private val lock = Any()
    private var currentState = unsupportedInputState()
    private var nextPointerIdentity = 0L
    private var mousePointerId: PointerId? = null
    private val publications = BoundedInputScheduler(
        discreteCapacity = deliveryPolicy.discreteEvents.ingressCapacity,
        pointerDelivery = deliveryPolicy.pointerMotion,
        scrollDelivery = deliveryPolicy.scroll,
    )
    private var publicationDrainActive = false
    private var terminal: FlowTerminal? = null
    private var terminalNotificationPending = false
    private val mutableState = MutableStateFlow(currentState)
    private val subscribers = linkedMapOf<InputEventSubscriber, RuntimeEventCollectorLease>()

    override val state: StateFlow<SurfaceInputState> = mutableState.asStateFlow()
    override val events: Flow<InputEvent> = flow {
        val subscriber = InputEventSubscriber(deliveryPolicy)
        when (val registration = registerSubscriber(subscriber)) {
            InputCollectorRegistration.Closed -> return@flow
            is InputCollectorRegistration.Failed -> throw KadreException(registration.failure)
            is InputCollectorRegistration.Registered -> Unit
        }
        try {
            while (true) {
                val event = subscriber.next() ?: break
                emit(event)
            }
        } finally {
            unregisterSubscriber(subscriber)
        }
    }

    fun accept(stimulus: SurfaceStimulus): Boolean {
        var admission: InputPublicationAdmission? = null
        val accepted = synchronized(lock) {
            if (terminal != null) return@synchronized false
            if (stimulus is SurfaceStimulus.InputObservationChanged) {
                return@synchronized updateObservationCapabilitiesLocked(stimulus)
            }
            val publication = reduceLocked(stimulus) ?: return@synchronized false
            admission = enqueuePublicationLocked(publication)
            true
        }
        admission?.let(::finishPublicationAdmission)
        return accepted
    }

    fun focusLost(): Boolean {
        var admission: InputPublicationAdmission? = null
        val accepted = synchronized(lock) {
            if (terminal != null) return@synchronized false
            val neutral = currentState.copy(
                keyboard = KeyboardState(emptySet()),
                pointers = emptyList(),
                touches = emptyList(),
                modifiers = KeyboardModifiers(emptySet()),
                revision = currentState.revision.next(),
            )
            setStateLocked(neutral)
            admission = enqueuePublicationLocked(
                InputPublication(
                    InputEvent.StateReset(
                        InputStateResetReason.FocusLost,
                        eventStampSource(),
                        deviceId = null,
                        stateRevision = currentState.revision,
                    ),
                ),
            )
            true
        }
        admission?.let(::finishPublicationAdmission)
        return accepted
    }

    fun close(failure: KadreFailure?) {
        val admission = synchronized(lock) {
            if (terminal != null) return
            terminal = failure?.let(FlowTerminal::Failed) ?: FlowTerminal.Closed
            terminalNotificationPending = true
            InputPublicationAdmission(ensurePublicationDrainLocked())
        }
        finishPublicationAdmission(admission)
    }

    private fun reduceLocked(stimulus: SurfaceStimulus): InputPublication? = when (stimulus) {
        is SurfaceStimulus.KeyChanged -> {
            val pressed = currentState.keyboard.pressedKeys.toMutableSet()
            when (stimulus.keyState) {
                KeyState.Pressed -> pressed += stimulus.physicalKey
                KeyState.Released -> pressed -= stimulus.physicalKey
            }
            updateStateLocked(
                keyboard = KeyboardState(pressed),
                modifiers = stimulus.modifiers,
            )
            InputPublication(
                InputEvent.Key(
                    physicalKey = stimulus.physicalKey,
                    logicalKey = stimulus.logicalKey,
                    location = stimulus.location,
                    keyState = stimulus.keyState,
                    repeat = stimulus.repeat,
                    modifiers = stimulus.modifiers,
                    stamp = eventStampSource(),
                    deviceId = stimulus.deviceId,
                    stateRevision = currentState.revision,
                ),
            )
        }

        is SurfaceStimulus.PointerEntered -> {
            val pointer = pointerStateLocked(
                kind = stimulus.kind,
                position = stimulus.position,
                pressure = null,
                pen = null,
            )
            updateStateLocked(pointers = replacePointer(currentState.pointers, pointer))
            InputPublication(
                InputEvent.PointerEntered(
                    pointer.id,
                    stimulus.kind,
                    stimulus.position,
                    eventStampSource(),
                    stimulus.deviceId,
                    currentState.revision,
                ),
            )
        }

        is SurfaceStimulus.PointerMoved -> {
            val pointer = pointerStateLocked(
                kind = stimulus.kind,
                position = stimulus.position,
                pressure = stimulus.pressure,
                pen = stimulus.pen,
            )
            updateStateLocked(pointers = replacePointer(currentState.pointers, pointer))
            InputPublication(
                InputEvent.PointerMoved(
                    pointer.id,
                    stimulus.kind,
                    stimulus.position,
                    stimulus.delta,
                    stimulus.pressure,
                    stimulus.pen,
                    eventStampSource(),
                    stimulus.deviceId,
                    currentState.revision,
                ),
            )
        }

        is SurfaceStimulus.PointerButtonChanged -> {
            val id = mousePointerIdLocked()
            val existing = currentState.pointers.firstOrNull { it.id == id }
            val buttons = (existing?.pressedButtons ?: emptySet()).toMutableSet()
            when (stimulus.buttonState) {
                PointerButtonState.Pressed -> buttons += stimulus.button
                PointerButtonState.Released -> buttons -= stimulus.button
            }
            val pointer = PointerState(
                id = id,
                kind = stimulus.kind,
                position = stimulus.position,
                pressedButtons = buttons,
                pressure = stimulus.pressure,
                pen = stimulus.pen,
            )
            updateStateLocked(pointers = replacePointer(currentState.pointers, pointer))
            InputPublication(
                InputEvent.PointerButtonChanged(
                    id,
                    stimulus.kind,
                    stimulus.button,
                    stimulus.buttonState,
                    stimulus.position,
                    stimulus.pressure,
                    stimulus.pen,
                    eventStampSource(),
                    stimulus.deviceId,
                    currentState.revision,
                ),
            )
        }

        is SurfaceStimulus.PointerLeft -> {
            val id = mousePointerId ?: return null
            val existing = currentState.pointers.firstOrNull { it.id == id } ?: return null
            InputPublication(
                InputEvent.PointerLeft(
                    id,
                    stimulus.kind,
                    existing.position,
                    eventStampSource(),
                    stimulus.deviceId,
                    currentState.revision,
                ),
                afterDelivery = InputStateAfterDelivery.RemovePointer(id, existing),
            )
        }

        is SurfaceStimulus.Scroll -> InputPublication(
            InputEvent.Scrolled(
                stimulus.delta,
                eventStampSource(),
                stimulus.deviceId,
                currentState.revision,
            ),
            scrollBoundary = stimulus.coalescingBoundary,
        )

        is SurfaceStimulus.MetricsChanged,
        is SurfaceStimulus.FocusChanged,
        is SurfaceStimulus.VisibilityChanged,
        is SurfaceStimulus.ThemeChanged,
        is SurfaceStimulus.RedrawConsumed,
        is SurfaceStimulus.InputObservationChanged,
        is SurfaceStimulus.Detached,
        -> error("surface observations cannot enter the input reducer")
    }

    private fun updateObservationCapabilitiesLocked(
        stimulus: SurfaceStimulus.InputObservationChanged,
    ): Boolean {
        val capabilities = currentState.capabilities.copy(
            keyboard = if (stimulus.keyboardInstalled) {
                FeatureAvailability.Available
            } else {
                FeatureAvailability.Unsupported
            },
            pointer = if (stimulus.pointerInstalled) {
                FeatureAvailability.Available
            } else {
                FeatureAvailability.Unsupported
            },
        )
        if (capabilities == currentState.capabilities) return false
        setStateLocked(
            currentState.copy(
                capabilities = capabilities,
                revision = currentState.revision.next(),
            ),
        )
        return true
    }

    private fun pointerStateLocked(
        kind: org.graphiks.kadre.input.PointerKind,
        position: org.graphiks.kadre.surface.LogicalPoint,
        pressure: Double?,
        pen: org.graphiks.kadre.input.PenState?,
    ): PointerState {
        val id = mousePointerIdLocked()
        val existing = currentState.pointers.firstOrNull { it.id == id }
        return PointerState(
            id = id,
            kind = kind,
            position = position,
            pressedButtons = existing?.pressedButtons ?: emptySet(),
            pressure = pressure,
            pen = pen,
        )
    }

    private fun mousePointerIdLocked(): PointerId = mousePointerId ?: run {
        check(nextPointerIdentity < Long.MAX_VALUE) { "pointer identity space exhausted" }
        PointerId(nextPointerIdentity++).also { mousePointerId = it }
    }

    private fun updateStateLocked(
        keyboard: KeyboardState = currentState.keyboard,
        pointers: List<PointerState> = currentState.pointers,
        modifiers: KeyboardModifiers = currentState.modifiers,
    ) {
        if (
            keyboard == currentState.keyboard &&
            pointers == currentState.pointers &&
            modifiers == currentState.modifiers
        ) return
        setStateLocked(
            currentState.copy(
                keyboard = keyboard,
                pointers = pointers,
                modifiers = modifiers,
                revision = currentState.revision.next(),
            ),
        )
    }

    private fun setStateLocked(value: SurfaceInputState) {
        currentState = value
        mutableState.value = value
    }

    private fun enqueuePublicationLocked(publication: InputPublication): InputPublicationAdmission =
        when (val offered = publications.offer(publication)) {
            QueueOfferResult.Accepted -> InputPublicationAdmission(ensurePublicationDrainLocked())
            is QueueOfferResult.Dropped -> InputPublicationAdmission(
                shouldDrain = ensurePublicationDrainLockedIfNeeded(),
                reportOverflow = true,
            )

            QueueOfferResult.DiscreteOverflow -> terminaliseLocked(
                failure = KadreFailure.SourceOverflow(KadreResourceKind.InputSource),
                failSession = deliveryPolicy.discreteEvents.ingressOverflow == IngressOverflowAction.FailSession,
            )

            is QueueOfferResult.ContinuousOverflow -> when (offered.action) {
                ContinuousOverflowAction.DropOldestAndReport,
                ContinuousOverflowAction.DropLatestAndReport,
                -> error("drop is reported by the scheduler")

                ContinuousOverflowAction.CloseSource -> terminaliseLocked(
                    failure = KadreFailure.SourceOverflow(KadreResourceKind.InputSource),
                    failSession = false,
                )

                ContinuousOverflowAction.FailSession -> terminaliseLocked(
                    failure = KadreFailure.SourceOverflow(KadreResourceKind.InputSource),
                    failSession = true,
                )
            }
        }

    private fun terminaliseLocked(
        failure: KadreFailure,
        failSession: Boolean,
    ): InputPublicationAdmission {
        if (terminal != null) return InputPublicationAdmission(shouldDrain = false)
        val neutral = currentState.copy(
            keyboard = KeyboardState(emptySet()),
            pointers = emptyList(),
            touches = emptyList(),
            modifiers = KeyboardModifiers(emptySet()),
            capabilities = unavailableInputCapabilities(failure),
            revision = currentState.revision.next(),
        )
        setStateLocked(neutral)
        terminal = FlowTerminal.Failed(failure)
        terminalNotificationPending = true
        return InputPublicationAdmission(
            shouldDrain = ensurePublicationDrainLocked(),
            reportOverflow = true,
            failSession = if (failSession) failure else null,
        )
    }

    private fun finishPublicationAdmission(admission: InputPublicationAdmission) {
        if (admission.reportOverflow) safeReport(KadreException(KadreFailure.SourceOverflow(KadreResourceKind.InputSource)))
        if (admission.shouldDrain) drainPublications()
        admission.failSession?.let(::safeFailSession)
    }

    private fun drainPublications() {
        while (true) {
            val publication = synchronized(lock) {
                publications.poll() ?: if (terminalNotificationPending) {
                    terminalNotificationPending = false
                    null
                } else {
                    publicationDrainActive = false
                    return
                }
            }
            if (publication == null) {
                closeSubscribers(checkNotNull(synchronized(lock) { terminal }))
            } else {
                publishEvent(publication)
            }
        }
    }

    private fun publishEvent(publication: InputPublication) {
        val currentSubscribers = synchronized(lock) { subscribers.keys.toList() }
        var closeSource = false
        var failSession = false
        currentSubscribers.forEach { subscriber ->
            when (subscriber.offer(publication.copyForDelivery())) {
                InputSubscriberOffer.Accepted -> Unit
                InputSubscriberOffer.Dropped -> safeReport(
                    KadreException(KadreFailure.SourceOverflow(KadreResourceKind.InputSource)),
                )

                InputSubscriberOffer.CloseSource -> closeSource = true
                InputSubscriberOffer.FailSession -> failSession = true
            }
        }
        publication.afterDelivery?.let(::applyAfterDelivery)
        if (closeSource || failSession) closeFromDeliveryOverflow(failSession)
    }

    private fun applyAfterDelivery(effect: InputStateAfterDelivery) {
        synchronized(lock) {
            when (effect) {
                is InputStateAfterDelivery.RemovePointer -> {
                    val currentPointer = currentState.pointers.firstOrNull { it.id == effect.id }
                    if (currentPointer == effect.expected) {
                        updateStateLocked(pointers = currentState.pointers.filterNot { it.id == effect.id })
                    }
                }
            }
        }
    }

    private fun closeFromDeliveryOverflow(failSession: Boolean) {
        val admission = synchronized(lock) {
            terminaliseLocked(
                failure = KadreFailure.SourceOverflow(KadreResourceKind.InputSource),
                failSession = failSession,
            )
        }
        finishPublicationAdmission(admission)
    }

    private fun closeSubscribers(terminalSnapshot: FlowTerminal) {
        val subscribersToClose = synchronized(lock) { subscribers.keys.toList() }
        val cause = (terminalSnapshot as? FlowTerminal.Failed)?.failure?.let(::KadreException)
        subscribersToClose.forEach { it.terminate(cause, drain = true) }
    }

    private fun registerSubscriber(subscriber: InputEventSubscriber): InputCollectorRegistration = synchronized(lock) {
        when (val terminalSnapshot = terminal) {
            FlowTerminal.Closed -> InputCollectorRegistration.Closed
            is FlowTerminal.Failed -> InputCollectorRegistration.Failed(terminalSnapshot.failure)
            null -> when (val admission = eventCollectorGate.tryAcquire()) {
                is KadreResult.Success -> {
                    check(subscribers.put(subscriber, admission.value) == null)
                    InputCollectorRegistration.Registered(admission.value)
                }

                is KadreResult.Failure -> InputCollectorRegistration.Failed(admission.reason)
            }
        }
    }

    private fun unregisterSubscriber(subscriber: InputEventSubscriber) {
        val lease = synchronized(lock) { subscribers.remove(subscriber) }
        lease?.close()
        subscriber.dispose()
    }

    private fun ensurePublicationDrainLocked(): Boolean {
        if (publicationDrainActive) return false
        publicationDrainActive = true
        return true
    }

    private fun ensurePublicationDrainLockedIfNeeded(): Boolean =
        if (publications.isEmpty() && !terminalNotificationPending) false else ensurePublicationDrainLocked()

    private fun safeReport(cause: Throwable) {
        try {
            failureReporter(cause)
        } catch (_: Exception) {
            // Input diagnostics cannot destabilise the surface runtime.
        } catch (_: LinkageError) {
            // Input diagnostics cannot destabilise the surface runtime.
        }
    }

    private fun safeFailSession(failure: KadreFailure) {
        try {
            sessionFailureHandler(failure)
        } catch (cause: Exception) {
            safeReport(cause)
        } catch (cause: LinkageError) {
            safeReport(cause)
        }
    }
}

private data class InputPublication(
    val event: InputEvent,
    val scrollBoundary: Long? = null,
    val afterDelivery: InputStateAfterDelivery? = null,
)

private sealed interface InputStateAfterDelivery {
    data class RemovePointer(
        val id: PointerId,
        val expected: PointerState,
    ) : InputStateAfterDelivery
}

private data class InputPublicationAdmission(
    val shouldDrain: Boolean,
    val reportOverflow: Boolean = false,
    val failSession: KadreFailure? = null,
)

private enum class InputEventLane { Discrete, PointerMotion, Scroll }

private class BoundedInputScheduler(
    private val discreteCapacity: Int,
    private val pointerDelivery: ContinuousDelivery,
    private val scrollDelivery: ContinuousDelivery,
) {
    private val entries = mutableListOf<InputPublication>()

    fun offer(value: InputPublication): QueueOfferResult = when (val lane = value.lane()) {
        InputEventLane.Discrete -> {
            if (entries.count { it.lane() == InputEventLane.Discrete } >= discreteCapacity) {
                QueueOfferResult.DiscreteOverflow
            } else {
                entries += value
                QueueOfferResult.Accepted
            }
        }

        InputEventLane.PointerMotion,
        InputEventLane.Scroll,
        -> offerContinuous(value, lane, if (lane == InputEventLane.PointerMotion) pointerDelivery else scrollDelivery)
    }

    fun poll(): InputPublication? {
        if (entries.isEmpty()) return null
        val next = entries.indices.minBy { entries[it].event.stamp.sequence.value }
        return entries.removeAt(next)
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    fun clear() {
        entries.clear()
    }

    private fun offerContinuous(
        value: InputPublication,
        lane: InputEventLane,
        delivery: ContinuousDelivery,
    ): QueueOfferResult = when (delivery) {
        ContinuousDelivery.Latest,
        ContinuousDelivery.Coalesced,
        -> {
            val lastBarrier = entries.asSequence()
                .filter { it.lane() == InputEventLane.Discrete }
                .maxOfOrNull { it.event.stamp.sequence.value }
                ?: -1L
            val existingIndex = entries.indices
                .filter { entries[it].lane() == lane && entries[it].event.stamp.sequence.value > lastBarrier }
                .lastOrNull()
            if (existingIndex != null && entries[existingIndex].canCoalesceWith(value)) {
                entries[existingIndex] = coalesceInputPublication(entries[existingIndex], value)
            } else {
                entries += value
            }
            QueueOfferResult.Accepted
        }

        is ContinuousDelivery.Buffered -> {
            val matching = entries.indices.filter { entries[it].lane() == lane }
            if (matching.size < delivery.capacity) {
                entries += value
                QueueOfferResult.Accepted
            } else {
                when (delivery.onOverflow) {
                    ContinuousOverflowAction.DropOldestAndReport -> {
                        entries.removeAt(matching.minBy { entries[it].event.stamp.sequence.value })
                        entries += value
                        QueueOfferResult.Dropped(latestWasDropped = false)
                    }

                    ContinuousOverflowAction.DropLatestAndReport -> QueueOfferResult.Dropped(latestWasDropped = true)
                    ContinuousOverflowAction.CloseSource,
                    ContinuousOverflowAction.FailSession,
                    -> QueueOfferResult.ContinuousOverflow(delivery.onOverflow)
                }
            }
        }
    }
}

private sealed interface InputSubscriberOffer {
    data object Accepted : InputSubscriberOffer
    data object Dropped : InputSubscriberOffer
    data object CloseSource : InputSubscriberOffer
    data object FailSession : InputSubscriberOffer
}

private sealed interface InputSubscriberTerminal {
    data object Closed : InputSubscriberTerminal
    data class Failed(val cause: Throwable) : InputSubscriberTerminal
}

private class InputEventSubscriber(
    policy: InputDeliveryPolicy,
) {
    private val lock = Any()
    private val signal = Channel<Unit>(capacity = 1)
    private val scheduler = BoundedInputScheduler(
        discreteCapacity = policy.discreteEvents.collectorCapacity,
        pointerDelivery = policy.pointerMotion,
        scrollDelivery = policy.scroll,
    )
    private val discreteOverflow = policy.discreteEvents.collectorOverflow
    private var terminal: InputSubscriberTerminal? = null

    fun offer(event: InputPublication): InputSubscriberOffer {
        val result = synchronized(lock) {
            if (terminal != null) return InputSubscriberOffer.Accepted
            when (val offered = scheduler.offer(event)) {
                QueueOfferResult.Accepted -> InputSubscriberOffer.Accepted
                is QueueOfferResult.Dropped -> InputSubscriberOffer.Dropped
                QueueOfferResult.DiscreteOverflow -> when (discreteOverflow) {
                    CollectorOverflowAction.CancelSlowCollector -> {
                        scheduler.clear()
                        terminal = InputSubscriberTerminal.Failed(
                            SlowCollectorCancellationException("input event collector exceeded its policy capacity"),
                        )
                        InputSubscriberOffer.Accepted
                    }

                    CollectorOverflowAction.CloseSource -> InputSubscriberOffer.CloseSource
                    CollectorOverflowAction.FailSession -> InputSubscriberOffer.FailSession
                }

                is QueueOfferResult.ContinuousOverflow -> when (offered.action) {
                    ContinuousOverflowAction.DropOldestAndReport,
                    ContinuousOverflowAction.DropLatestAndReport,
                    -> InputSubscriberOffer.Dropped

                    ContinuousOverflowAction.CloseSource -> InputSubscriberOffer.CloseSource
                    ContinuousOverflowAction.FailSession -> InputSubscriberOffer.FailSession
                }
            }
        }
        signal.trySend(Unit)
        return result
    }

    suspend fun next(): InputEvent? {
        while (true) {
            val terminalSnapshot = synchronized(lock) {
                scheduler.poll()?.let { return it.event }
                terminal
            }
            when (terminalSnapshot) {
                InputSubscriberTerminal.Closed -> return null
                is InputSubscriberTerminal.Failed -> throw terminalSnapshot.cause
                null -> signal.receive()
            }
        }
    }

    fun terminate(cause: Throwable?, drain: Boolean) {
        synchronized(lock) {
            if (terminal != null) return
            if (!drain) scheduler.clear()
            terminal = cause?.let(InputSubscriberTerminal::Failed) ?: InputSubscriberTerminal.Closed
        }
        signal.trySend(Unit)
    }

    fun dispose() {
        synchronized(lock) { scheduler.clear() }
        signal.cancel()
    }
}

private fun InputPublication.lane(): InputEventLane = when (event) {
    is InputEvent.PointerMoved -> InputEventLane.PointerMotion
    is InputEvent.Scrolled -> InputEventLane.Scroll
    else -> InputEventLane.Discrete
}

private fun InputPublication.canCoalesceWith(latest: InputPublication): Boolean =
    lane() == latest.lane() &&
        when (lane()) {
            InputEventLane.PointerMotion -> true
            InputEventLane.Scroll -> {
                val previousEvent = event as InputEvent.Scrolled
                val latestEvent = latest.event as InputEvent.Scrolled
                scrollBoundary == latest.scrollBoundary &&
                    previousEvent.deviceId == latestEvent.deviceId &&
                    previousEvent.delta.hasSameUnitAs(latestEvent.delta)
            }
            InputEventLane.Discrete -> false
        }

private fun coalesceInputPublication(previous: InputPublication, latest: InputPublication): InputPublication {
    check(previous.canCoalesceWith(latest))
    val stamp = coalescedStamp(previous.event.stamp, latest.event.stamp)
    return when (val latestEvent = latest.event) {
        is InputEvent.PointerMoved -> {
            val previousEvent = previous.event as InputEvent.PointerMoved
            latest.copy(
                event = latestEvent.copy(
                    delta = LogicalDelta(
                        previousEvent.delta.x + latestEvent.delta.x,
                        previousEvent.delta.y + latestEvent.delta.y,
                    ),
                    stamp = stamp,
                ),
            )
        }

        is InputEvent.Scrolled -> {
            val previousEvent = previous.event as InputEvent.Scrolled
            val delta = previousEvent.delta.plusSameUnit(latestEvent.delta) ?: return latest
            latest.copy(event = latestEvent.copy(delta = delta, stamp = stamp))
        }

        else -> error("only continuous input events can coalesce")
    }
}

private fun InputPublication.copyForDelivery(): InputPublication = copy(event = event.copyInputEvent())

private fun InputEvent.copyInputEvent(): InputEvent = when (this) {
    is InputEvent.Key -> copy(stamp = stamp.copy())
    is InputEvent.PointerEntered -> copy(stamp = stamp.copy())
    is InputEvent.PointerLeft -> copy(stamp = stamp.copy())
    is InputEvent.PointerMoved -> copy(stamp = stamp.copy())
    is InputEvent.PointerButtonChanged -> copy(stamp = stamp.copy())
    is InputEvent.Scrolled -> copy(stamp = stamp.copy())
    is InputEvent.StateReset -> copy(stamp = stamp.copy())
    else -> error("inactive input event entered the essential input runtime")
}

private fun ScrollDelta.hasSameUnitAs(other: ScrollDelta): Boolean =
    (this is ScrollDelta.Lines && other is ScrollDelta.Lines) ||
        (this is ScrollDelta.Logical && other is ScrollDelta.Logical)

private fun ScrollDelta.plusSameUnit(other: ScrollDelta): ScrollDelta? = when (this) {
    is ScrollDelta.Lines -> {
        val next = other as? ScrollDelta.Lines ?: return null
        ScrollDelta.Lines(x + next.x, y + next.y)
    }

    is ScrollDelta.Logical -> {
        val next = other as? ScrollDelta.Logical ?: return null
        ScrollDelta.Logical(x + next.x, y + next.y)
    }
}

private fun replacePointer(existing: List<PointerState>, pointer: PointerState): List<PointerState> =
    existing.filterNot { it.id == pointer.id } + pointer

private fun unsupportedInputState(): SurfaceInputState = SurfaceInputState(
    keyboard = KeyboardState(emptySet()),
    pointers = emptyList(),
    touches = emptyList(),
    modifiers = KeyboardModifiers(emptySet()),
    capabilities = unsupportedInputCapabilities(),
    revision = InputStateRevision(0L),
)

private fun unsupportedInputCapabilities(): InputCapabilities = InputCapabilities(
    keyboard = FeatureAvailability.Unsupported,
    pointer = FeatureAvailability.Unsupported,
    touch = FeatureAvailability.Unsupported,
    gestures = FeatureAvailability.Unsupported,
    dragAndDrop = FeatureAvailability.Unsupported,
    textInput = unsupported(KadreOperation.TextInput),
    rawInput = unsupported(KadreOperation.RawInputAccess),
)

private fun unavailableInputCapabilities(failure: KadreFailure): InputCapabilities = InputCapabilities(
    keyboard = FeatureAvailability.Unavailable(failure),
    pointer = FeatureAvailability.Unavailable(failure),
    touch = FeatureAvailability.Unavailable(failure),
    gestures = FeatureAvailability.Unavailable(failure),
    dragAndDrop = FeatureAvailability.Unavailable(failure),
    textInput = unsupported(KadreOperation.TextInput),
    rawInput = unsupported(KadreOperation.RawInputAccess),
)

private sealed interface InputCollectorRegistration {
    data class Registered(val lease: RuntimeEventCollectorLease) : InputCollectorRegistration
    data object Closed : InputCollectorRegistration
    data class Failed(val failure: KadreFailure) : InputCollectorRegistration
}

private fun initialState(snapshot: SurfaceInitialSnapshot): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = snapshot.metrics.logicalSize,
    physicalSize = snapshot.metrics.physicalSize,
    scaleFactor = snapshot.metrics.scaleFactor,
    safeAreaInsets = snapshot.metrics.safeAreaInsets,
    visibility = snapshot.visibility,
    occlusion = snapshot.occlusion,
    focus = snapshot.focus,
    theme = snapshot.theme,
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)

internal fun unsupportedSurfaceCapabilities(): SurfaceCapabilities = SurfaceCapabilities(
    cursor = unsupported(KadreOperation.UpdateSurface),
    customCursor = unsupported(KadreOperation.UpdateSurface),
    pointerCapture = unsupported(KadreOperation.UpdateSurface),
    hitTesting = unsupported(KadreOperation.UpdateSurface),
    inputDefaultBehavior = unsupported(KadreOperation.UpdateSurface),
    handlerInteractions = unsupported(KadreOperation.InstallInteractionHandler),
    armedInteractions = unsupported(KadreOperation.ArmInteraction),
    platformAccess = unsupported(KadreOperation.PlatformSurfaceAccess),
)

private fun capabilityFailure(capability: Capability<*>): KadreFailure? = when (capability) {
    is Capability.Unsupported -> capability.failure
    is Capability.Supported -> when (val availability = capability.availability) {
        FeatureAvailability.Available -> null
        FeatureAvailability.Unsupported -> KadreFailure.Unsupported(KadreOperation.UpdateSurface)
        is FeatureAvailability.Unavailable -> availability.failure
        is FeatureAvailability.RequiresInteraction ->
            KadreFailure.InteractionRequired(InteractionFailureReason.Missing)

        is FeatureAvailability.RequiresPermission -> KadreFailure.Unsupported(KadreOperation.UpdateSurface)
    }
}

private fun SurfaceUpdateCommand.isEmpty(): Boolean =
    cursor is PropertyChange.Unchanged &&
        pointerCapture is PropertyChange.Unchanged &&
        hitTesting is PropertyChange.Unchanged &&
        inputDefaultBehavior is PropertyChange.Unchanged

private fun SurfaceRevision.next(): SurfaceRevision {
    check(value < Long.MAX_VALUE) { "surface revision space exhausted" }
    return SurfaceRevision(value + 1L)
}

private fun InputStateRevision.next(): InputStateRevision {
    check(value < Long.MAX_VALUE) { "input state revision space exhausted" }
    return InputStateRevision(value + 1L)
}

private fun SurfaceStimulus.isInputStimulus(): Boolean = when (this) {
    is SurfaceStimulus.KeyChanged,
    is SurfaceStimulus.InputObservationChanged,
    is SurfaceStimulus.PointerEntered,
    is SurfaceStimulus.PointerMoved,
    is SurfaceStimulus.PointerButtonChanged,
    is SurfaceStimulus.PointerLeft,
    is SurfaceStimulus.Scroll,
    -> true

    is SurfaceStimulus.MetricsChanged,
    is SurfaceStimulus.FocusChanged,
    is SurfaceStimulus.VisibilityChanged,
    is SurfaceStimulus.ThemeChanged,
    is SurfaceStimulus.RedrawConsumed,
    is SurfaceStimulus.Detached,
    -> false
}

private fun successfulUpdateOutcome(
    state: SurfaceState,
    rejected: List<RejectedSurfaceField>,
): KadreResult<SurfaceUpdateOutcome> = KadreResult.Success(
    if (rejected.isEmpty()) {
        SurfaceUpdateOutcome.Applied(state)
    } else {
        SurfaceUpdateOutcome.PartiallyApplied(state, rejected)
    },
)

private fun invalidClearField(update: SurfaceUpdate): String? = when {
    update.cursor is PropertyChange.Clear -> "cursor"
    update.pointerCapture is PropertyChange.Clear -> "pointerCapture"
    update.hitTesting is PropertyChange.Clear -> "hitTesting"
    update.inputDefaultBehavior is PropertyChange.Clear -> "inputDefaultBehavior"
    else -> null
}

private val SurfaceProperty.fieldName: String
    get() = when (this) {
        SurfaceProperty.Cursor -> "cursor"
        SurfaceProperty.PointerCapture -> "pointerCapture"
        SurfaceProperty.HitTesting -> "hitTesting"
        SurfaceProperty.InputDefaultBehavior -> "inputDefaultBehavior"
    }

private fun <T> unsupported(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))
