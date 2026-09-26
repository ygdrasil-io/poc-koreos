package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.graphiks.kadre.application.EventDeliverySpan
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.input.DropOfferTerminationReason
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateResetReason
import org.graphiks.kadre.input.InputStateRevision
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.KeyboardState
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerId
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.PointerState
import org.graphiks.kadre.input.RawInputAccess
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.input.TouchId
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.input.TouchState
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.policy.SlowCollectorCancellationException
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId

internal class RuntimeSurfaceInput(
    private val surfaceId: SurfaceId,
    private val deliveryPolicy: InputDeliveryPolicy,
    private val eventStampSource: () -> EventStamp,
    private val eventCollectorGate: RuntimeEventCollectorGate,
    private val textInputPort: TextInputPort,
    private val rawInputCoordinator: RawInputCoordinator?,
    rawInputCapability: Capability<Unit>,
    private val dragAndDropAvailable: Boolean,
    private val resources: ResourceBudgetPolicy,
    private val dropTransferBudget: RuntimeDropTransferBudget,
    private val dropTransferScope: kotlinx.coroutines.CoroutineScope?,
    private val textInputEventCollectorGate: RuntimeEventCollectorGate,
    private val failureReporter: (Throwable) -> Unit,
    private val sessionFailureHandler: (KadreFailure) -> Unit,
) : SurfaceInput {
    private val lock = RuntimeLock()
    private var currentState = unsupportedInputState(
        textInput = textInputPort.capability,
        dragAndDrop = if (dragAndDropAvailable) FeatureAvailability.Available else FeatureAvailability.Unsupported,
        rawInput = rawInputCapability,
    )
    private var nextPointerIdentity = 0L
    private var nextTouchIdentity = 0L
    private val touchIdsByNativeIdentity = IdentityKeyedMap<TouchId>()
    private val retiringTouchIdsByNativeIdentity = IdentityKeyedMap<TouchId>()
    private var nextDropOfferIdentity = 0L
    private var mousePointerId: PointerId? = null
    private var activeDrop: RuntimeDropOffer? = null
    private val activeDropTransfers = linkedSetOf<RuntimeDropTransfer>()
    private val publications = BoundedInputScheduler(
        discreteCapacity = deliveryPolicy.discreteEvents.ingressCapacity,
        pointerDelivery = deliveryPolicy.pointerMotion,
        touchDelivery = deliveryPolicy.touchMotion,
        scrollDelivery = deliveryPolicy.scroll,
        gestureDelivery = deliveryPolicy.gestureChanges,
    )
    private var publicationDrainActive = false
    private var terminal: FlowTerminal? = null
    private var terminalNotificationPending = false
    private var textInputSession: RuntimeTextInputSession? = null
    private var textInputOpening = false
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

    override suspend fun openTextInput(config: TextInputConfig): KadreResult<TextInputSession> {
        val admitted = lock.withLock {
            when {
                terminal != null -> TextInputOpenAdmission.Closed
                textInputSession != null || textInputOpening -> TextInputOpenAdmission.AlreadyInUse
                currentState.capabilities.textInput is Capability.Unsupported -> TextInputOpenAdmission.Unsupported(
                    (currentState.capabilities.textInput as Capability.Unsupported).failure,
                )

                else -> {
                    textInputOpening = true
                    TextInputOpenAdmission.Admitted
                }
            }
        }
        when (admitted) {
            TextInputOpenAdmission.Closed -> return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
            TextInputOpenAdmission.AlreadyInUse -> {
                return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession))
            }

            is TextInputOpenAdmission.Unsupported -> return KadreResult.Failure(admitted.failure)
            TextInputOpenAdmission.Admitted -> Unit
        }

        // The holder belongs to this call, not to the surface: the callback below can only ever
        // reach the session this call opens, so an observation that a port delivers after that
        // session closed is rejected by the session's own guard rather than routed into a later
        // session that may now own the surface.
        val observationTarget = TextInputObservationTarget()
        val owner = when (
            val opened = textInputPort.open(
                TextInputOpenCommand(
                    surfaceId = surfaceId,
                    config = config,
                    onObservation = { observation ->
                        lock.withLock { observationTarget.session }?.acceptObservation(observation) ?: false
                    },
                ),
            )
        ) {
            is KadreResult.Failure -> {
                lock.withLock { textInputOpening = false }
                return opened
            }

            is KadreResult.Success -> opened.value
        }
        val session = RuntimeTextInputSession(
            owner = owner,
            initialConfig = config,
            port = textInputPort,
            eventStampSource = eventStampSource,
            eventCollectorGate = textInputEventCollectorGate,
            failureReporter = failureReporter,
            onClosed = { closed -> clearTextInputSession(closed, observationTarget) },
        )
        lock.withLock { observationTarget.session = session }
        val result = lock.withLock {
            textInputOpening = false
            when {
                terminal != null -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
                textInputSession != null -> KadreResult.Failure(
                    KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession),
                )

                else -> {
                    textInputSession = session
                    KadreResult.Success(session)
                }
            }
        }
        if (result is KadreResult.Failure) session.close()
        return result
    }

    @OptIn(DelicateKadreApi::class)
    override suspend fun requestRawInput(): KadreResult<RawInputAccess> {
        if (lock.withLock { terminal != null }) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
        }
        return rawInputCoordinator
            ?.requestAccess(surfaceId)
            ?: KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess))
    }

    fun updateRawInputCapability(capability: Capability<Unit>) {
        lock.withLock {
            if (terminal != null || currentState.capabilities.rawInput == capability) return
            setStateLocked(
                currentState.copy(
                    capabilities = currentState.capabilities.copy(rawInput = capability),
                    revision = currentState.revision.next(),
                ),
            )
        }
    }

    fun presentDrop(
        source: DropTransferSource,
        position: LogicalPoint,
        stamp: EventStamp,
    ): RuntimeDropOffer? {
        val itemSources = when (val validation = snapshotDropItems(source, resources)) {
            is DropItemSnapshot.Valid -> validation.items
            is DropItemSnapshot.Invalid -> {
                val admission = lock.withLock {
                    if (terminal != null) null else terminaliseLocked(validation.failure, failSession = false)
                }
                try {
                    source.close()
                } finally {
                    admission?.let(::finishPublicationAdmission)
                }
                return null
            }
        }
        var admission: InputPublicationAdmission? = null
        val offer = lock.withLock {
            if (terminal != null || currentState.capabilities.dragAndDrop != FeatureAvailability.Available) {
                return@withLock null
            }
            check(nextDropOfferIdentity < Long.MAX_VALUE) { "drop offer identity space exhausted" }
            activeDrop?.terminate(DropOfferTerminationReason.LeftSurface)
            val next = RuntimeDropOffer(
                id = DropOfferId(nextDropOfferIdentity++),
                source = source,
                itemSources = itemSources,
                resources = resources,
                transferBudget = dropTransferBudget,
            )
            activeDrop = next
            admission = enqueuePublicationLocked(
                InputPublication(
                    InputEvent.DropEntered(
                        offer = next,
                        position = position,
                        stamp = stamp,
                        deviceId = null,
                        stateRevision = currentState.revision,
                    ),
                ),
            )
            next
        }
        admission?.let(::finishPublicationAdmission)
        if (offer == null) source.close()
        return offer
    }

    private fun snapshotDropItems(
        source: DropTransferSource,
        resources: ResourceBudgetPolicy,
    ): DropItemSnapshot {
        val sources = source.items.toList()
        if (sources.size > resources.maxCollectionElementsPerValue) {
            return DropItemSnapshot.Invalid(retainedPayloadLimit(resources.maxCollectionElementsPerValue))
        }
        val snapshots = ArrayList<RuntimeDropItemSource>(sources.size)
        for (item in sources) {
            val descriptor = item.descriptor
            if (descriptor.mimeTypes.size > resources.maxCollectionElementsPerValue) {
                return DropItemSnapshot.Invalid(retainedPayloadLimit(resources.maxCollectionElementsPerValue))
            }
            if (descriptor.mimeTypes.any { it.length > resources.maxMetadataCodeUnitsPerValue }) {
                return DropItemSnapshot.Invalid(retainedPayloadLimit(resources.maxMetadataCodeUnitsPerValue))
            }
            snapshots += RuntimeDropItemSource(
                source = item,
                descriptor = descriptor.copy(
                    displayName = descriptor.displayName?.takeIf {
                        it.length <= resources.maxMetadataCodeUnitsPerValue
                    },
                    mimeTypes = descriptor.mimeTypes.toList(),
                ),
                readMode = item.readMode,
            )
        }
        return DropItemSnapshot.Valid(snapshots)
    }

    private fun retainedPayloadLimit(limit: Int): KadreFailure.ResourceLimitExceeded =
        KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload, limit.toLong())

    private sealed interface DropItemSnapshot {
        data class Valid(val items: List<RuntimeDropItemSource>) : DropItemSnapshot
        data class Invalid(val failure: KadreFailure.ResourceLimitExceeded) : DropItemSnapshot
    }

    fun acceptDrop(offerId: DropOfferId): KadreResult<Unit> = lock.withLock {
        activeDrop?.takeIf { it.id == offerId }?.accept()
            ?: KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer))
    }

    fun rejectDrop(offerId: DropOfferId): Boolean {
        val offer = lock.withLock {
            activeDrop?.takeIf { it.id == offerId }?.also { activeDrop = null }
        } ?: return false
        return offer.terminate(DropOfferTerminationReason.Rejected)
    }

    fun accept(stimulus: SurfaceStimulus): Boolean {
        var admission: InputPublicationAdmission? = null
        val accepted = lock.withLock {
            if (terminal != null) return@withLock false
            if (stimulus is SurfaceStimulus.InputObservationChanged) {
                return@withLock updateObservationCapabilitiesLocked(stimulus)
            }
            val publication = reduceLocked(stimulus) ?: return@withLock false
            admission = enqueuePublicationLocked(publication)
            true
        }
        admission?.let(::finishPublicationAdmission)
        return accepted
    }

    fun acceptInteraction(event: InteractionEvent, pointerPressure: Double? = null): Boolean = when (event) {
        is InteractionEvent.PointerPressed -> accept(
            SurfaceStimulus.PointerButtonChanged(
                surfaceId = surfaceId,
                kind = PointerKind.Mouse,
                button = event.button,
                buttonState = PointerButtonState.Pressed,
                position = event.position,
                pressure = pointerPressure,
                pen = null,
            ),
            event.stamp,
        )

        is InteractionEvent.KeyPressed -> accept(
            SurfaceStimulus.KeyChanged(
                surfaceId = surfaceId,
                physicalKey = event.physicalKey,
                logicalKey = LogicalKey.Unidentified("interaction-key"),
                location = KeyLocation.Standard,
                keyState = KeyState.Pressed,
                repeat = false,
                modifiers = KeyboardModifiers(emptySet()),
            ),
            event.stamp,
        )

        is InteractionEvent.TouchStarted -> acceptInteractionTouch(event)

        // A drop entry is published when its runtime-owned offer is created, before this
        // interaction callback is dispatched. It must not be reinjected as ordinary input.
        is InteractionEvent.DropEntered -> false
    }

    private fun acceptInteractionTouch(event: InteractionEvent.TouchStarted): Boolean {
        var admission: InputPublicationAdmission? = null
        val accepted = lock.withLock {
            if (terminal != null) return@withLock false
            val touch = TouchState(event.touchId, event.position, pressure = null)
            updateStateLocked(touches = currentState.touches.filterNot { it.id == touch.id } + touch)
            admission = enqueuePublicationLocked(
                InputPublication(
                    InputEvent.TouchChanged(
                        touchId = event.touchId,
                        phase = TouchPhase.Started,
                        position = event.position,
                        pressure = null,
                        stamp = event.stamp,
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

    private fun accept(stimulus: SurfaceStimulus, stamp: EventStamp): Boolean {
        var admission: InputPublicationAdmission? = null
        val accepted = lock.withLock {
            if (terminal != null) return@withLock false
            val publication = reduceLocked(stimulus, stamp) ?: return@withLock false
            admission = enqueuePublicationLocked(publication)
            true
        }
        admission?.let(::finishPublicationAdmission)
        return accepted
    }

    fun focusLost(): Boolean {
        var admission: InputPublicationAdmission? = null
        val accepted = lock.withLock {
            if (terminal != null) return@withLock false
            val neutral = currentState.copy(
                keyboard = KeyboardState(emptySet()),
                pointers = emptyList(),
                touches = emptyList(),
                modifiers = KeyboardModifiers(emptySet()),
                revision = currentState.revision.next(),
            )
            touchIdsByNativeIdentity.clear()
            retiringTouchIdsByNativeIdentity.clear()
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

    fun suspendTextInput() {
        val session = lock.withLock { textInputSession }
        session?.suspend()
    }

    fun resumeTextInput() {
        val session = lock.withLock { textInputSession }
        session?.resume()
    }

    fun close(failure: KadreFailure?) {
        var activeTextInput: RuntimeTextInputSession? = null
        var dropToClose: RuntimeDropOffer? = null
        var transfersToClose: List<RuntimeDropTransfer> = emptyList()
        val admission = lock.withLock {
            if (terminal != null) return
            terminal = failure?.let(FlowTerminal::Failed) ?: FlowTerminal.Closed
            terminalNotificationPending = true
            activeTextInput = textInputSession
            textInputSession = null
            dropToClose = activeDrop
            activeDrop = null
            transfersToClose = activeDropTransfers.toList()
            activeDropTransfers.clear()
            touchIdsByNativeIdentity.clear()
            retiringTouchIdsByNativeIdentity.clear()
            InputPublicationAdmission(ensurePublicationDrainLocked())
        }
        activeTextInput?.close()
        dropToClose?.terminate(DropOfferTerminationReason.OwnerClosed)
        transfersToClose.forEach(RuntimeDropTransfer::close)
        rawInputCoordinator?.closeOwner(surfaceId)
        finishPublicationAdmission(admission)
    }

    private fun clearTextInputSession(
        session: RuntimeTextInputSession,
        observationTarget: TextInputObservationTarget,
    ) {
        lock.withLock {
            if (observationTarget.session === session) observationTarget.session = null
            if (textInputSession === session) textInputSession = null
        }
    }

    private sealed interface TextInputOpenAdmission {
        data object Admitted : TextInputOpenAdmission
        data object AlreadyInUse : TextInputOpenAdmission
        data object Closed : TextInputOpenAdmission
        data class Unsupported(val failure: KadreFailure.Unsupported) : TextInputOpenAdmission
    }

    private fun reduceLocked(
        stimulus: SurfaceStimulus,
        stamp: EventStamp = eventStampSource(),
    ): InputPublication? = when (stimulus) {
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
                    stamp = stamp,
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
                    stamp,
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
                    stamp,
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
                    stamp,
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
                    stamp,
                    stimulus.deviceId,
                    currentState.revision,
                ),
                afterDelivery = InputStateAfterDelivery.RemovePointer(id, existing),
            )
        }

        is SurfaceStimulus.Scroll -> InputPublication(
            InputEvent.Scrolled(
                stimulus.delta,
                stamp,
                stimulus.deviceId,
                currentState.revision,
            ),
            scrollBoundary = stimulus.coalescingBoundary,
        )

        is SurfaceStimulus.TouchChanged -> reduceTouchLocked(stimulus, stamp)

        is SurfaceStimulus.Gesture -> try {
            InputPublication(
                InputEvent.Gesture(
                    kind = stimulus.kind,
                    phase = stimulus.phase,
                    delta = stimulus.delta,
                    scale = stimulus.scale,
                    rotationRadians = stimulus.rotationRadians,
                    pressure = stimulus.pressure,
                    stamp = stamp,
                    deviceId = stimulus.deviceId,
                    stateRevision = currentState.revision,
                ),
            )
        } catch (_: IllegalArgumentException) {
            null
        }

        is SurfaceStimulus.DropMoved -> {
            val offer = activeDrop?.takeIf { it.id == stimulus.offerId } ?: return null
            InputPublication(
                InputEvent.DropMoved(
                    offerId = offer.id,
                    position = stimulus.position,
                    stamp = stamp,
                    deviceId = stimulus.deviceId,
                    stateRevision = currentState.revision,
                ),
            )
        }

        is SurfaceStimulus.DropExited -> {
            val offer = activeDrop?.takeIf { it.id == stimulus.offerId } ?: return null
            activeDrop = null
            offer.terminate(DropOfferTerminationReason.LeftSurface)
            InputPublication(
                InputEvent.DropExited(
                    offerId = offer.id,
                    stamp = stamp,
                    deviceId = stimulus.deviceId,
                    stateRevision = currentState.revision,
                ),
            )
        }

        is SurfaceStimulus.DropPerformed -> {
            val offer = activeDrop?.takeIf { it.id == stimulus.offerId } ?: return null
            val transfer = offer.perform(dropTransferScope) { closed ->
                lock.withLock { activeDropTransfers.remove(closed) }
            } ?: return null
            activeDrop = null
            activeDropTransfers += transfer
            InputPublication(
                InputEvent.Dropped(
                    offer = offer,
                    position = stimulus.position,
                    stamp = stamp,
                    deviceId = stimulus.deviceId,
                    stateRevision = currentState.revision,
                ),
            )
        }

        is SurfaceStimulus.MetricsChanged,
        is SurfaceStimulus.FocusChanged,
        is SurfaceStimulus.VisibilityChanged,
        is SurfaceStimulus.AppearanceChanged,
        is SurfaceStimulus.RedrawConsumed,
        is SurfaceStimulus.InputObservationChanged,
        is SurfaceStimulus.Detached,
        -> error("surface observations cannot enter the input reducer")
    }

    private fun reduceTouchLocked(
        stimulus: SurfaceStimulus.TouchChanged,
        stamp: EventStamp,
    ): InputPublication? {
        if (retiringTouchIdsByNativeIdentity[stimulus.nativeIdentity] != null) return null
        val existingId = touchIdsByNativeIdentity[stimulus.nativeIdentity]
        val touchId = when (stimulus.phase) {
            TouchPhase.Started -> {
                if (existingId != null) return null
                check(nextTouchIdentity < Long.MAX_VALUE) { "touch identity space exhausted" }
                TouchId(nextTouchIdentity)
            }

            TouchPhase.Moved,
            TouchPhase.Ended,
            TouchPhase.Cancelled,
            -> existingId ?: return null
        }
        val touch = try {
            TouchState(touchId, stimulus.position, stimulus.pressure)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (stimulus.phase == TouchPhase.Started) {
            nextTouchIdentity += 1L
            touchIdsByNativeIdentity[stimulus.nativeIdentity] = touchId
        }
        updateStateLocked(touches = replaceTouch(currentState.touches, touch))
        val terminal = stimulus.phase == TouchPhase.Ended || stimulus.phase == TouchPhase.Cancelled
        if (terminal) {
            touchIdsByNativeIdentity.remove(stimulus.nativeIdentity)
            retiringTouchIdsByNativeIdentity[stimulus.nativeIdentity] = touchId
        }
        return InputPublication(
            event = InputEvent.TouchChanged(
                touchId = touchId,
                phase = stimulus.phase,
                position = stimulus.position,
                pressure = stimulus.pressure,
                stamp = stamp,
                deviceId = stimulus.deviceId,
                stateRevision = currentState.revision,
            ),
            afterDelivery = if (terminal) {
                InputStateAfterDelivery.RemoveTouch(stimulus.nativeIdentity, touchId, touch)
            } else {
                null
            },
        )
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
            touch = if (stimulus.touchInstalled) {
                FeatureAvailability.Available
            } else {
                FeatureAvailability.Unsupported
            },
            gestures = if (stimulus.gestureKinds.isEmpty()) {
                unsupported(KadreOperation.GestureInput)
            } else {
                Capability.Supported(stimulus.gestureKinds.toSet(), FeatureAvailability.Available)
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
        touches: List<TouchState> = currentState.touches,
        modifiers: KeyboardModifiers = currentState.modifiers,
    ) {
        if (
            keyboard == currentState.keyboard &&
            pointers == currentState.pointers &&
            touches == currentState.touches &&
            modifiers == currentState.modifiers
        ) return
        setStateLocked(
            currentState.copy(
                keyboard = keyboard,
                pointers = pointers,
                touches = touches,
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
        val activeTextInput = textInputSession
        textInputSession = null
        val dropOfferToClose = activeDrop
        activeDrop = null
        val dropTransfersToClose = activeDropTransfers.toList()
        activeDropTransfers.clear()
        val neutral = currentState.copy(
            keyboard = KeyboardState(emptySet()),
            pointers = emptyList(),
            touches = emptyList(),
            modifiers = KeyboardModifiers(emptySet()),
            capabilities = unavailableInputCapabilities(currentState.capabilities, failure),
            revision = currentState.revision.next(),
        )
        touchIdsByNativeIdentity.clear()
        retiringTouchIdsByNativeIdentity.clear()
        setStateLocked(neutral)
        terminal = FlowTerminal.Failed(failure)
        terminalNotificationPending = true
        return InputPublicationAdmission(
            shouldDrain = ensurePublicationDrainLocked(),
            reportOverflow = true,
            terminalFailure = failure,
            failSession = if (failSession) failure else null,
            textInputToClose = activeTextInput,
            dropOfferToClose = dropOfferToClose,
            dropTransfersToClose = dropTransfersToClose,
        )
    }

    private fun finishPublicationAdmission(admission: InputPublicationAdmission) {
        admission.textInputToClose?.close()
        admission.dropOfferToClose?.terminate(DropOfferTerminationReason.OwnerClosed)
        admission.dropTransfersToClose.forEach(RuntimeDropTransfer::close)
        if (admission.reportOverflow) {
            safeReport(
                KadreException(admission.terminalFailure ?: KadreFailure.SourceOverflow(KadreResourceKind.InputSource)),
            )
        }
        if (admission.shouldDrain) drainPublications()
        admission.failSession?.let(::safeFailSession)
    }

    private fun drainPublications() {
        while (true) {
            val publication = lock.withLock {
                publications.poll() ?: if (terminalNotificationPending) {
                    terminalNotificationPending = false
                    null
                } else {
                    publicationDrainActive = false
                    return
                }
            }
            if (publication == null) {
                closeSubscribers(checkNotNull(lock.withLock { terminal }))
            } else {
                publishEvent(publication)
            }
        }
    }

    private fun publishEvent(publication: InputPublication) {
        val currentSubscribers = lock.withLock { subscribers.keys.toList() }
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
        lock.withLock {
            when (effect) {
                is InputStateAfterDelivery.RemovePointer -> {
                    val currentPointer = currentState.pointers.firstOrNull { it.id == effect.id }
                    if (currentPointer == effect.expected) {
                        updateStateLocked(pointers = currentState.pointers.filterNot { it.id == effect.id })
                    }
                }

                is InputStateAfterDelivery.RemoveTouch -> {
                    val currentId = retiringTouchIdsByNativeIdentity[effect.nativeIdentity]
                    val currentTouch = currentState.touches.firstOrNull { it.id == effect.id }
                    if (currentId == effect.id) {
                        retiringTouchIdsByNativeIdentity.remove(effect.nativeIdentity)
                        if (currentTouch == effect.expected) {
                            updateStateLocked(touches = currentState.touches.filterNot { it.id == effect.id })
                        }
                    }
                }
            }
        }
    }

    private fun closeFromDeliveryOverflow(failSession: Boolean) {
        val admission = lock.withLock {
            terminaliseLocked(
                failure = KadreFailure.SourceOverflow(KadreResourceKind.InputSource),
                failSession = failSession,
            )
        }
        finishPublicationAdmission(admission)
    }

    private fun closeSubscribers(terminalSnapshot: FlowTerminal) {
        val subscribersToClose = lock.withLock { subscribers.keys.toList() }
        val cause = (terminalSnapshot as? FlowTerminal.Failed)?.failure?.let(::KadreException)
        subscribersToClose.forEach { it.terminate(cause, drain = true) }
    }

    private fun registerSubscriber(subscriber: InputEventSubscriber): InputCollectorRegistration = lock.withLock {
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
        val lease = lock.withLock { subscribers.remove(subscriber) }
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
        } catch (throwable: Throwable) {
            if (throwable.isLinkageFailure()) {
                // Input diagnostics cannot destabilise the surface runtime.
            } else {
                throw throwable
            }
        }
    }

    private fun safeFailSession(failure: KadreFailure) {
        try {
            sessionFailureHandler(failure)
        } catch (cause: Exception) {
            safeReport(cause)
        } catch (cause: Throwable) {
            if (cause.isLinkageFailure()) {
                safeReport(cause)
            } else {
                throw cause
            }
        }
    }
}

/**
 * The one observation callback admitted by a single `openTextInput` call.
 *
 * The holder belongs to that call and is captured by that call's `onObservation` lambda, so the
 * lambda can only ever reach its own session. A port may deliver an observation long after the
 * session that admitted it closed (the AppKit queued port defers every observation through its
 * queue): with a call-scoped holder such a late observation arrives at the closed session and is
 * rejected by its `closed` guard, instead of being routed into whichever session now owns the
 * surface. All reads and writes happen under the reducer's lock.
 */
private class TextInputObservationTarget {
    var session: RuntimeTextInputSession? = null
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

    data class RemoveTouch(
        val nativeIdentity: Any,
        val id: TouchId,
        val expected: TouchState,
    ) : InputStateAfterDelivery
}

private data class InputPublicationAdmission(
    val shouldDrain: Boolean,
    val reportOverflow: Boolean = false,
    val terminalFailure: KadreFailure? = null,
    val failSession: KadreFailure? = null,
    val textInputToClose: RuntimeTextInputSession? = null,
    val dropOfferToClose: RuntimeDropOffer? = null,
    val dropTransfersToClose: List<RuntimeDropTransfer> = emptyList(),
)

private enum class InputEventLane { Discrete, PointerMotion, TouchMotion, Scroll, GestureChanges }

private class BoundedInputScheduler(
    private val discreteCapacity: Int,
    private val pointerDelivery: ContinuousDelivery,
    private val touchDelivery: ContinuousDelivery,
    private val scrollDelivery: ContinuousDelivery,
    private val gestureDelivery: ContinuousDelivery,
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
        InputEventLane.TouchMotion,
        InputEventLane.Scroll,
        InputEventLane.GestureChanges,
        -> offerContinuous(
            value,
            lane,
            when (lane) {
                InputEventLane.PointerMotion -> pointerDelivery
                InputEventLane.TouchMotion -> touchDelivery
                InputEventLane.Scroll -> scrollDelivery
                InputEventLane.GestureChanges -> gestureDelivery
                InputEventLane.Discrete -> error("discrete input cannot use continuous delivery")
            },
        )
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
                entries[existingIndex] = when (delivery) {
                    ContinuousDelivery.Latest -> replaceInputPublication(entries[existingIndex], value)
                    ContinuousDelivery.Coalesced -> coalesceInputPublication(entries[existingIndex], value)
                    is ContinuousDelivery.Buffered -> error("buffered delivery cannot replace a pending event")
                }
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
    private val lock = RuntimeLock()
    private val signal = Channel<Unit>(capacity = 1)
    private val scheduler = BoundedInputScheduler(
        discreteCapacity = policy.discreteEvents.collectorCapacity,
        pointerDelivery = policy.pointerMotion,
        touchDelivery = policy.touchMotion,
        scrollDelivery = policy.scroll,
        gestureDelivery = policy.gestureChanges,
    )
    private val discreteOverflow = policy.discreteEvents.collectorOverflow
    private var terminal: InputSubscriberTerminal? = null

    fun offer(event: InputPublication): InputSubscriberOffer {
        val result = lock.withLock {
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
            val terminalSnapshot = lock.withLock {
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
        lock.withLock {
            if (terminal != null) return
            if (!drain) scheduler.clear()
            terminal = cause?.let(InputSubscriberTerminal::Failed) ?: InputSubscriberTerminal.Closed
        }
        signal.trySend(Unit)
    }

    fun dispose() {
        lock.withLock { scheduler.clear() }
        signal.cancel()
    }
}

private fun InputPublication.lane(): InputEventLane = when (event) {
    is InputEvent.PointerMoved -> InputEventLane.PointerMotion
    is InputEvent.TouchChanged -> if (event.phase == TouchPhase.Moved) {
        InputEventLane.TouchMotion
    } else {
        InputEventLane.Discrete
    }
    is InputEvent.Scrolled -> InputEventLane.Scroll
    is InputEvent.Gesture -> if (event.phase == TouchPhase.Moved) {
        InputEventLane.GestureChanges
    } else {
        InputEventLane.Discrete
    }
    else -> InputEventLane.Discrete
}

private fun InputPublication.canCoalesceWith(latest: InputPublication): Boolean =
    lane() == latest.lane() &&
        when (lane()) {
            InputEventLane.PointerMotion -> true
            InputEventLane.TouchMotion -> {
                val previousEvent = event as InputEvent.TouchChanged
                val latestEvent = latest.event as InputEvent.TouchChanged
                previousEvent.touchId == latestEvent.touchId && previousEvent.deviceId == latestEvent.deviceId
            }
            InputEventLane.Scroll -> {
                val previousEvent = event as InputEvent.Scrolled
                val latestEvent = latest.event as InputEvent.Scrolled
                scrollBoundary == latest.scrollBoundary &&
                    previousEvent.deviceId == latestEvent.deviceId &&
                    previousEvent.delta.hasSameUnitAs(latestEvent.delta)
            }
            InputEventLane.GestureChanges -> {
                val previousEvent = event as InputEvent.Gesture
                val latestEvent = latest.event as InputEvent.Gesture
                previousEvent.kind == latestEvent.kind && previousEvent.deviceId == latestEvent.deviceId
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

        is InputEvent.TouchChanged -> latest.copy(event = latestEvent.copy(stamp = stamp))

        is InputEvent.Gesture -> {
            val previousEvent = previous.event as InputEvent.Gesture
            latest.copy(event = latestEvent.coalescedWith(previousEvent, stamp))
        }

        else -> error("only continuous input events can coalesce")
    }
}

private fun replaceInputPublication(previous: InputPublication, latest: InputPublication): InputPublication {
    check(previous.canCoalesceWith(latest))
    val stamp = coalescedStamp(previous.event.stamp, latest.event.stamp)
    return latest.copy(
        event = when (val latestEvent = latest.event) {
            is InputEvent.PointerMoved -> latestEvent.copy(stamp = stamp)
            is InputEvent.Scrolled -> latestEvent.copy(stamp = stamp)
            is InputEvent.TouchChanged -> latestEvent.copy(stamp = stamp)
            is InputEvent.Gesture -> latestEvent.copy(stamp = stamp)
            else -> error("only continuous input events can replace a pending value")
        },
    )
}

private fun InputPublication.copyForDelivery(): InputPublication = copy(event = event.copyInputEvent())

private fun InputEvent.copyInputEvent(): InputEvent = when (this) {
    is InputEvent.Key -> copy(stamp = stamp.copy())
    is InputEvent.PointerEntered -> copy(stamp = stamp.copy())
    is InputEvent.PointerLeft -> copy(stamp = stamp.copy())
    is InputEvent.PointerMoved -> copy(stamp = stamp.copy())
    is InputEvent.PointerButtonChanged -> copy(stamp = stamp.copy())
    is InputEvent.Scrolled -> copy(stamp = stamp.copy())
    is InputEvent.TouchChanged -> copy(stamp = stamp.copy())
    is InputEvent.Gesture -> copy(stamp = stamp.copy())
    is InputEvent.DropEntered -> copy(stamp = stamp.copy())
    is InputEvent.DropMoved -> copy(stamp = stamp.copy())
    is InputEvent.DropExited -> copy(stamp = stamp.copy())
    is InputEvent.Dropped -> copy(stamp = stamp.copy())
    is InputEvent.StateReset -> copy(stamp = stamp.copy())
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

private fun InputEvent.Gesture.coalescedWith(
    previous: InputEvent.Gesture,
    stamp: EventStamp,
): InputEvent.Gesture = when (kind) {
    GestureKind.Pan -> {
        val previousDelta = checkNotNull(previous.delta)
        val latestDelta = checkNotNull(delta)
        val combinedX = previousDelta.x + latestDelta.x
        val combinedY = previousDelta.y + latestDelta.y
        copy(
            delta = if (combinedX.isFinite() && combinedY.isFinite()) {
                LogicalDelta(combinedX, combinedY)
            } else {
                latestDelta
            },
            stamp = stamp,
        )
    }

    GestureKind.Pinch -> {
        val latestScale = checkNotNull(scale)
        val combined = checkNotNull(previous.scale) * latestScale
        copy(scale = combined.takeIf { it.isFinite() && it > 0.0 } ?: latestScale, stamp = stamp)
    }

    GestureKind.Rotation -> {
        val latestRotation = checkNotNull(rotationRadians)
        val combined = checkNotNull(previous.rotationRadians) + latestRotation
        copy(rotationRadians = combined.takeIf(Double::isFinite) ?: latestRotation, stamp = stamp)
    }

    GestureKind.DoubleTap,
    GestureKind.TouchpadPressure,
    -> copy(stamp = stamp)
}

private fun replacePointer(existing: List<PointerState>, pointer: PointerState): List<PointerState> =
    existing.filterNot { it.id == pointer.id } + pointer

private fun replaceTouch(existing: List<TouchState>, touch: TouchState): List<TouchState> =
    existing.filterNot { it.id == touch.id } + touch

private fun unsupportedInputState(
    textInput: Capability<Unit> = unsupported(KadreOperation.TextInput),
    dragAndDrop: FeatureAvailability = FeatureAvailability.Unsupported,
    rawInput: Capability<Unit> = unsupported(KadreOperation.RawInputAccess),
): SurfaceInputState = SurfaceInputState(
    keyboard = KeyboardState(emptySet()),
    pointers = emptyList(),
    touches = emptyList(),
    modifiers = KeyboardModifiers(emptySet()),
    capabilities = unsupportedInputCapabilities(textInput, dragAndDrop, rawInput),
    revision = InputStateRevision(0L),
)

private fun unsupportedInputCapabilities(
    textInput: Capability<Unit> = unsupported(KadreOperation.TextInput),
    dragAndDrop: FeatureAvailability = FeatureAvailability.Unsupported,
    rawInput: Capability<Unit> = unsupported(KadreOperation.RawInputAccess),
): InputCapabilities = InputCapabilities(
    keyboard = FeatureAvailability.Unsupported,
    pointer = FeatureAvailability.Unsupported,
    touch = FeatureAvailability.Unsupported,
    gestures = unsupported(KadreOperation.GestureInput),
    dragAndDrop = dragAndDrop,
    textInput = textInput,
    rawInput = rawInput,
)

private fun unavailableInputCapabilities(
    current: InputCapabilities,
    failure: KadreFailure,
): InputCapabilities = InputCapabilities(
    keyboard = FeatureAvailability.Unavailable(failure),
    pointer = FeatureAvailability.Unavailable(failure),
    touch = FeatureAvailability.Unavailable(failure),
    gestures = when (val gestures = current.gestures) {
        is Capability.Supported -> gestures.copy(availability = FeatureAvailability.Unavailable(failure))
        is Capability.Unsupported -> gestures
    },
    dragAndDrop = FeatureAvailability.Unavailable(failure),
    textInput = unsupported(KadreOperation.TextInput),
    rawInput = unsupported(KadreOperation.RawInputAccess),
)

private sealed interface InputCollectorRegistration {
    data class Registered(val lease: RuntimeEventCollectorLease) : InputCollectorRegistration
    data object Closed : InputCollectorRegistration
    data class Failed(val failure: KadreFailure) : InputCollectorRegistration
}

private fun InputStateRevision.next(): InputStateRevision {
    check(value < Long.MAX_VALUE) { "input state revision space exhausted" }
    return InputStateRevision(value + 1L)
}

internal fun coalescedStamp(previous: EventStamp, latest: EventStamp): EventStamp {
    val first = previous.deliverySpan?.firstSequence ?: previous.sequence
    val previousCount = previous.deliverySpan?.eventCount ?: 1L
    val latestCount = latest.deliverySpan?.eventCount ?: 1L
    val eventCount = checkedAdd(previousCount, latestCount)
    return latest.copy(
        deliverySpan = EventDeliverySpan(first, latest.sequence, eventCount),
    )
}
