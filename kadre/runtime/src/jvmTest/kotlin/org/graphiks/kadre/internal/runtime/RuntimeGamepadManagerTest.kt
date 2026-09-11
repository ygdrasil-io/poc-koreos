package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceConnectionState
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.DeviceLifecycleEvent
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEvent
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.nanoseconds

class RuntimeGamepadManagerTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun portConnectionPublishesTheGamepadSnapshotBeforeItsLifecycleEvent() = runTest {
        val port = FakeGamepadPort()
        val manager = RuntimeGamepadManager(
            port = port,
            eventStampSource = { EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
            collectorAllocator = RuntimeEventCollectorAllocator(8),
            maxCollectorsPerFlow = 4,
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
    fun initiallySuspendedGamepadIsProjectedWithNeutralControls() {
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
        )

        val gamepad = (manager.state.value.inventory as DeviceInventory.Enumerated).gamepads.single()

        assertEquals(GamepadRoutingState.Suspended, gamepad.state.value.routing)
        assertEquals(listOf(GamepadButtonValue(GamepadButton.South, 0.0, false)), gamepad.state.value.controls.buttons)
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

    private fun gamepad(key: Long): GamepadPortGamepad = GamepadPortGamepad(
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
        capabilities = GamepadCapabilities(
            effects = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect)),
        ),
    )

    private fun pressedSouth(): GamepadState = GamepadState(
        buttons = listOf(GamepadButtonValue(GamepadButton.South, 1.0, true)),
        axes = emptyList(),
    )
}

private class FakeGamepadPort(
    initialGamepads: List<GamepadPortGamepad> = emptyList(),
) : GamepadPort {
    private var observer: ((GamepadPortEvent) -> Unit)? = null

    override val gamepads: List<GamepadPortGamepad> = initialGamepads

    override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable {
        check(this.observer == null) { "observer already installed" }
        this.observer = observer
        return AutoCloseable { this.observer = null }
    }

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
