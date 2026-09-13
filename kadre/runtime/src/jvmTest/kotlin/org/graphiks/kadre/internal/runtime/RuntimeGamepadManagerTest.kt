package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceConnectionState
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.DeviceLifecycleEvent
import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadEffectOutcome
import org.graphiks.kadre.input.GamepadEffectSession
import org.graphiks.kadre.input.GamepadEffectState
import org.graphiks.kadre.input.GamepadEvent
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadLocalizedHapticConstraints
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.policy.GamepadRouting
import org.graphiks.kadre.policy.DeviceEffectOwnership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.hours

class RuntimeGamepadManagerTest {
    @Test
    fun initialInputDevicesShareTheAtomicDeviceInventoryWithGamepads() = runTest {
        val inputPort = FakeInputDevicePort(
            devices = listOf(inputDevice(key = 3L, name = "Keyboard", kind = InputDeviceKind.Keyboard)),
        )
        val manager = RuntimeGamepadManager(
            port = FakeGamepadPort(initialGamepads = listOf(gamepad(key = 11L))),
            inputPort = inputPort,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )

        val inventory = assertIs<DeviceInventory.Enumerated>(manager.state.value.inventory)

        assertEquals(listOf("Keyboard"), inventory.devices.map { it.descriptor.name })
        assertEquals(listOf("Controller 11"), inventory.gamepads.map { it.state.value.descriptor.name })
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun inputConnectionPublishesTheSnapshotBeforeItsLifecycleEvent() = runTest {
        val inputPort = FakeInputDevicePort()
        val manager = RuntimeGamepadManager(
            port = FakeGamepadPort(),
            inputPort = inputPort,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(8),
            maxCollectorsPerFlow = 4,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        val added = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            manager.events.first { candidate ->
                if (candidate !is DeviceLifecycleEvent.DeviceAdded) return@first false
                val inventory = assertIs<DeviceInventory.Enumerated>(manager.state.value.inventory)
                assertSame(candidate.device, inventory.devices.single())
                assertEquals(candidate.managerRevision, manager.state.value.revision)
                assertEquals(DeviceConnectionState.Connected, candidate.device.connection.value)
                true
            }
        }

        inputPort.connect(inputDevice(key = 13L, name = "Trackpad", kind = InputDeviceKind.Touchpad))

        val event = assertIs<DeviceLifecycleEvent.DeviceAdded>(added.await())
        assertSame(event.device, manager.device(event.device.id))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun inputDisconnectionPublishesTheTerminalHandleBeforeItsRemovalEvent() = runTest {
        val inputPort = FakeInputDevicePort()
        val manager = RuntimeGamepadManager(
            port = FakeGamepadPort(),
            inputPort = inputPort,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(8),
            maxCollectorsPerFlow = 4,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        inputPort.connect(inputDevice(key = 17L, name = "Pen", kind = InputDeviceKind.Pen))
        val device = assertIs<DeviceInventory.Enumerated>(manager.state.value.inventory).devices.single()
        val removed = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            manager.events.first { candidate ->
                if (candidate !is DeviceLifecycleEvent.DeviceRemoved) return@first false
                assertEquals(DeviceConnectionState.Disconnected, device.connection.value)
                assertEquals(candidate.managerRevision, manager.state.value.revision)
                true
            }
        }

        inputPort.disconnect(17L)

        val event = assertIs<DeviceLifecycleEvent.DeviceRemoved>(removed.await())
        assertEquals(device.id, event.deviceId)
        assertEquals(null, manager.device(device.id))
    }

    @Test
    fun lifecycleUpdatesThePortWithTheNormalizedRoutingEligibility() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Inactive),
        )

        manager.updateLifecycle(lifecycle(VisibilityState.Foreground, ActivationState.Active))

        assertEquals(
            listOf(
                GamepadPortRouting(
                    GamepadRouting.AllForegroundSessions,
                    foregroundActive = false,
                    effectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
                ),
                GamepadPortRouting(
                    GamepadRouting.AllForegroundSessions,
                    foregroundActive = true,
                    effectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
                ),
            ),
            port.routingUpdates,
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun portConnectionPublishesTheGamepadSnapshotBeforeItsLifecycleEvent() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(8),
            maxCollectorsPerFlow = 4,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        val added = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            manager.events.first { candidate ->
                if (candidate !is DeviceLifecycleEvent.GamepadAdded) return@first false
                val inventory = assertIs<DeviceInventory.Enumerated>(manager.state.value.inventory)
                assertSame(candidate.gamepad, inventory.gamepads.single())
                assertEquals(candidate.managerRevision, manager.state.value.revision)
                assertEquals(DeviceConnectionState.Connected, candidate.gamepad.state.value.connection)
                true
            }
        }

        port.connect(gamepad(key = 11L))

        val event = assertIs<DeviceLifecycleEvent.GamepadAdded>(added.await())
        assertSame(event.gamepad, manager.gamepad(event.gamepad.id))
        assertEquals(
            listOf(GamepadButtonValue(GamepadButton.South, 0.0, false)),
            event.gamepad.state.value.controls.buttons,
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun routingSuspensionNeutralizesControlsAndResumePublishesTheCurrentPhysicalState() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(8),
            maxCollectorsPerFlow = 4,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L))
        val gamepad = checkNotNull(manager.state.value.inventory.let { inventory ->
            (inventory as DeviceInventory.Enumerated).gamepads.single()
        })
        val suspended = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            gamepad.events.first { it is GamepadEvent.RoutingSuspended }
        }

        port.changeRouting(
            key = 11L,
            routing = GamepadRoutingState.Suspended,
            state = pressedSouth(),
        )

        assertIs<GamepadEvent.RoutingSuspended>(suspended.await())
        assertEquals(GamepadRoutingState.Suspended, gamepad.state.value.routing)
        assertEquals(listOf(GamepadButtonValue(GamepadButton.South, 0.0, false)), gamepad.state.value.controls.buttons)

        val resumed = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            gamepad.events.first { it is GamepadEvent.RoutingResumed }
        }
        port.changeRouting(
            key = 11L,
            routing = GamepadRoutingState.Routed,
            state = pressedSouth(),
        )

        assertIs<GamepadEvent.RoutingResumed>(resumed.await())
        assertEquals(GamepadRoutingState.Routed, gamepad.state.value.routing)
        assertEquals(pressedSouth(), gamepad.state.value.controls)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun stateChangePublishesTheGamepadSnapshotBeforeTheControlEvent() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(8),
            maxCollectorsPerFlow = 4,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L))
        val gamepad = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads.single()
        val changed = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            gamepad.events.first { candidate ->
                if (candidate !is GamepadEvent.ButtonChanged) return@first false
                assertEquals(pressedSouth(), gamepad.state.value.controls)
                assertEquals(candidate.revision, gamepad.state.value.revision)
                true
            }
        }

        port.changeState(11L, pressedSouth())

        val event = assertIs<GamepadEvent.ButtonChanged>(changed.await())
        assertEquals(GamepadButtonValue(GamepadButton.South, 1.0, true), event.value)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun eachGamepadEventFlowHasItsOwnCollectorBudget() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L))
        port.connect(gamepad(key = 12L))
        val gamepads = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads
        val firstGamepad = gamepads.single { it.state.value.descriptor.name == "Controller 11" }
        val secondGamepad = gamepads.single { it.state.value.descriptor.name == "Controller 12" }
        val first = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            firstGamepad.events.first { it is GamepadEvent.ButtonChanged }
        }
        val second = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            secondGamepad.events.first { it is GamepadEvent.ButtonChanged }
        }

        port.changeState(11L, pressedSouth())
        port.changeState(12L, pressedSouth())

        assertIs<GamepadEvent.ButtonChanged>(first.await())
        assertIs<GamepadEvent.ButtonChanged>(second.await())
    }

    @Test
    fun initiallySuspendedGamepadIsProjectedWithNeutralControls() = runTest {
        val port = FakeGamepadPort(
            initialGamepads = listOf(
                gamepad(key = 11L).copy(
                    routing = GamepadRoutingState.Suspended,
                    state = pressedSouth(),
                ),
            ),
        )
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )

        val gamepad = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads.single()

        assertEquals(GamepadRoutingState.Suspended, gamepad.state.value.routing)
        assertEquals(listOf(GamepadButtonValue(GamepadButton.South, 0.0, false)), gamepad.state.value.controls.buttons)
    }

    @Test
    fun supportedEffectReturnsAnObservableSessionThatCompletesAtItsDuration() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L, capabilities = localizedHapticCapabilities()))
        val gamepad = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads.single()

        val result = gamepad.playEffect(
            GamepadEffect.LocalizedHaptic(
                locality = GamepadHapticLocality.Default,
                intensity = 1.0,
                duration = 1.nanoseconds,
            ),
        )

        val session = assertIs<GamepadEffectSession>(assertIs<KadreResult.Success<*>>(result).value)
        assertEquals(1, port.effects.size)
        testScheduler.advanceUntilIdle()
        assertEquals(GamepadEffectOutcome.Completed, session.awaitTermination())
        assertEquals(0, port.effects.single().stopCount)
        assertEquals(1, port.effects.single().closeCount)
    }

    @Test
    fun effectAdmissionRejectsUnsupportedKindLocalityAndDurationBeforeNativeStart() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L, capabilities = localizedHapticCapabilities(maximumDuration = 1.nanoseconds)))
        val gamepad = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads.single()

        val unsupportedKind = gamepad.playEffect(GamepadEffect.DualRumble(1.0, 1.0, 1.nanoseconds))
        val unsupportedLocality = gamepad.playEffect(
            GamepadEffect.LocalizedHaptic(GamepadHapticLocality.All, 1.0, 1.nanoseconds),
        )
        val excessiveDuration = gamepad.playEffect(
            GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 2.nanoseconds),
        )

        assertEquals(KadreFailure.InvalidRequest("effect"), assertIs<KadreResult.Failure>(unsupportedKind).reason)
        assertEquals(KadreFailure.InvalidRequest("effect"), assertIs<KadreResult.Failure>(unsupportedLocality).reason)
        assertEquals(KadreFailure.InvalidRequest("effect.duration"), assertIs<KadreResult.Failure>(excessiveDuration).reason)
        assertEquals(emptyList(), port.effects)
    }

    @Test
    fun gamepadEffectBudgetRejectsTheSecondEffectBeforeNativeAdmission() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 1,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L, capabilities = localizedHapticCapabilities()))
        val gamepad = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads.single()
        val effect = GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 1.hours)

        assertIs<KadreResult.Success<*>>(gamepad.playEffect(effect))
        val second = gamepad.playEffect(effect)

        assertEquals(
            KadreFailure.ResourceLimitExceeded(org.graphiks.kadre.diagnostics.KadreResourceKind.GamepadEffect, 1),
            assertIs<KadreResult.Failure>(second).reason,
        )
        assertEquals(1, port.effects.size)
        manager.close()
    }

    @Test
    fun disconnectionTerminatesTheSessionEffectWithThePhysicalDeviceReason() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L, capabilities = localizedHapticCapabilities()))
        val gamepad = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads.single()
        val session = assertIs<GamepadEffectSession>(
            assertIs<KadreResult.Success<*>>(
                gamepad.playEffect(GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 1.hours)),
            ).value,
        )

        port.disconnect(11L)

        assertEquals(
            GamepadEffectOutcome.Stopped(org.graphiks.kadre.input.GamepadEffectStopReason.DeviceDisconnected),
            session.awaitTermination(),
        )
        assertEquals(1, port.effects.single().stopCount)
        assertEquals(1, port.effects.single().closeCount)
    }

    @Test
    fun stopEffectsStopsOnlyTheEffectsOwnedByThatGamepadProjection() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L, capabilities = localizedHapticCapabilities()))
        port.connect(gamepad(key = 12L, capabilities = localizedHapticCapabilities()))
        val gamepads = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads
        val first = gamepads.single { it.state.value.descriptor.name == "Controller 11" }
        val second = gamepads.single { it.state.value.descriptor.name == "Controller 12" }
        val effect = GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 1.hours)
        val firstSession = assertIs<GamepadEffectSession>(assertIs<KadreResult.Success<*>>(first.playEffect(effect)).value)
        val secondSession = assertIs<GamepadEffectSession>(assertIs<KadreResult.Success<*>>(second.playEffect(effect)).value)

        assertEquals(KadreResult.Success(Unit), first.stopEffects())

        assertEquals(
            GamepadEffectOutcome.Stopped(org.graphiks.kadre.input.GamepadEffectStopReason.Requested),
            firstSession.awaitTermination(),
        )
        assertEquals(1, port.effects[0].stopCount)
        assertEquals(GamepadEffectState.Playing, secondSession.state.value)
        manager.close()
        assertEquals(
            GamepadEffectOutcome.Stopped(org.graphiks.kadre.input.GamepadEffectStopReason.ParentSessionStopping),
            secondSession.awaitTermination(),
        )
        assertEquals(1, port.effects[1].stopCount)
    }

    @Test
    fun sessionManagerTeardownTerminatesActiveEffects() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 1,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L, capabilities = localizedHapticCapabilities()))
        val gamepad = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads.single()
        val session = assertIs<GamepadEffectSession>(
            assertIs<KadreResult.Success<*>>(
                gamepad.playEffect(GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 1.hours)),
            ).value,
        )

        manager.close()

        assertEquals(
            GamepadEffectOutcome.Stopped(org.graphiks.kadre.input.GamepadEffectStopReason.ParentSessionStopping),
            session.awaitTermination(),
        )
        assertEquals(1, port.effects.single().stopCount)
        assertEquals(1, port.effects.single().closeCount)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun disconnectPublishesTheTerminalGamepadStateBeforeTheLifecycleEvent() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(8),
            maxCollectorsPerFlow = 4,
            effectScope = this,
            maxConcurrentEffects = 4,
            gamepadRouting = GamepadRouting.AllForegroundSessions,
            initialLifecycleState = lifecycle(VisibilityState.Foreground, ActivationState.Active),
        )
        port.connect(gamepad(key = 11L))
        val gamepad = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads.single()
        val removed = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            manager.events.first { candidate ->
                if (candidate !is DeviceLifecycleEvent.GamepadRemoved) return@first false
                assertEquals(DeviceConnectionState.Disconnected, gamepad.state.value.connection)
                assertEquals(GamepadRoutingState.Suspended, gamepad.state.value.routing)
                assertEquals(candidate.managerRevision, manager.state.value.revision)
                true
            }
        }

        port.disconnect(11L)

        val event = assertIs<DeviceLifecycleEvent.GamepadRemoved>(removed.await())
        assertEquals(event.gamepadId, gamepad.id)
        assertEquals(null, manager.gamepad(gamepad.id))
    }

    private fun gamepad(
        key: Long,
        capabilities: GamepadCapabilities = GamepadCapabilities(
            effects = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect)),
        ),
    ): GamepadPortGamepad = GamepadPortGamepad(
        key = key,
        descriptor = GamepadDescriptor(
            name = "Controller $key",
            mapping = GamepadMapping.Standard,
            buttons = listOf(GamepadButton.South),
            axes = emptyList(),
        ),
        state = GamepadState(
            buttons = listOf(GamepadButtonValue(GamepadButton.South, 0.0, false)),
            axes = emptyList(),
        ),
        routing = GamepadRoutingState.Routed,
        capabilities = capabilities,
    )

    private fun inputDevice(
        key: Long,
        name: String,
        kind: InputDeviceKind,
    ): InputDevicePortDevice = InputDevicePortDevice(
        key = key,
        descriptor = InputDeviceDescriptor(name = name, kind = kind),
    )

    private fun localizedHapticCapabilities(
        maximumDuration: kotlin.time.Duration? = null,
    ): GamepadCapabilities = GamepadCapabilities(
        effects = Capability.Supported(
            GamepadEffectConstraints(
                kinds = setOf(GamepadEffectKind.LocalizedHaptic),
                localizedHaptics = GamepadLocalizedHapticConstraints(setOf(GamepadHapticLocality.Default)),
                maximumDuration = maximumDuration,
            ),
            FeatureAvailability.Available,
        ),
    )

    private fun pressedSouth(): GamepadState = GamepadState(
        buttons = listOf(GamepadButtonValue(GamepadButton.South, 1.0, true)),
        axes = emptyList(),
    )

    private fun lifecycle(
        visibility: VisibilityState,
        activation: ActivationState,
    ): LifecycleState = LifecycleState(AttachmentState.Attached, visibility, activation)
}

private class FakeGamepadPort(
    initialGamepads: List<GamepadPortGamepad> = emptyList(),
) : GamepadPort {
    private var observer: ((GamepadPortEvent) -> Unit)? = null
    val effects = mutableListOf<RecordingGamepadPortEffect>()
    val routingUpdates = mutableListOf<GamepadPortRouting>()

    override val gamepads: List<GamepadPortGamepad> = initialGamepads

    override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable {
        check(this.observer == null) { "observer already installed" }
        this.observer = observer
        return AutoCloseable { this.observer = null }
    }

    override fun updateRouting(routing: GamepadPortRouting) {
        routingUpdates += routing
    }

    override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> =
        KadreResult.Success(RecordingGamepadPortEffect().also(effects::add))

    fun connect(gamepad: GamepadPortGamepad) {
        checkNotNull(observer)(GamepadPortEvent.Connected(gamepad))
    }

    fun changeRouting(key: Long, routing: GamepadRoutingState, state: GamepadState) {
        checkNotNull(observer)(GamepadPortEvent.RoutingChanged(key, routing, state))
    }

    fun changeState(key: Long, state: GamepadState) {
        checkNotNull(observer)(GamepadPortEvent.StateChanged(key, state))
    }

    fun disconnect(key: Long) {
        checkNotNull(observer)(GamepadPortEvent.Disconnected(key))
    }

    override fun close() = Unit
}

private class FakeInputDevicePort(
    override val devices: List<InputDevicePortDevice> = emptyList(),
) : InputDevicePort {
    private var observer: ((InputDevicePortEvent) -> Unit)? = null

    override fun installObserver(observer: (InputDevicePortEvent) -> Unit): AutoCloseable {
        check(this.observer == null) { "observer already installed" }
        this.observer = observer
        return AutoCloseable { this.observer = null }
    }

    fun connect(device: InputDevicePortDevice) {
        checkNotNull(observer)(InputDevicePortEvent.Connected(device))
    }

    fun disconnect(key: Long) {
        checkNotNull(observer)(InputDevicePortEvent.Disconnected(key))
    }

    override fun close() = Unit
}

private class RecordingGamepadPortEffect : GamepadPortEffect {
    var stopCount = 0
        private set
    var closeCount = 0
        private set

    override fun requestStop(): KadreResult<Unit> {
        stopCount += 1
        return KadreResult.Success(Unit)
    }

    override fun close() {
        closeCount += 1
    }
}
