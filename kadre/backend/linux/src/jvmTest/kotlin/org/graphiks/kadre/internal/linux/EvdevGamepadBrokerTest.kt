package org.graphiks.kadre.internal.linux

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.policy.DeviceEffectOwnership
import org.graphiks.kadre.policy.GamepadRouting
import org.graphiks.kffi.evdev.EvdevInputEvent

/**
 * O2 proofs of the evdev gamepad service (design D1–D6, F2 matrix):
 * everything through the [EvdevNativeSource] seam — no native stack, no
 * worker thread; [EvdevGamepadBroker.pumpOnce] drives time by hand.
 */
class EvdevGamepadBrokerTest {

    // ------------------------------------------------------------ fakes

    private class FakeEffect : EvdevNativeEffect {
        val plays = ArrayList<Int>()
        val stops = ArrayList<Int>()
        var released = 0

        override fun play(): Boolean {
            plays.add(1)
            return true
        }

        override fun stop(): Boolean {
            stops.add(0)
            return true
        }

        override fun release() {
            released++
        }
    }

    private class FakeNode(
        override val identity: String,
        override val name: String? = "kadre proof pad",
        override val buttonCodes: List<Int> = listOf(0x130, 0x131, 0x136, 0x137, 0x13a, 0x13b),
        override val axisCodes: List<Int> = listOf(0x00, 0x01, 0x02),
        override val syntheticButtonCodes: List<Int> = listOf(0x220, 0x221),
        override val forceFeedback: Boolean = true,
        private val bounds: Map<Int, Pair<Int, Int>> = mapOf(
            0x00 to (-32768 to 32767),
            0x01 to (-32768 to 32767),
            0x02 to (0 to 255),
        ),
    ) : EvdevNativeGamepad {
        private val pending = ArrayDeque<EvdevInputEvent>()
        val closed = AtomicInteger(0)

        override fun axisBounds(code: Int): Pair<Int, Int>? = bounds[code]

        override fun readEvents(): List<EvdevInputEvent> {
            val drained = pending.toList()
            pending.clear()
            return drained
        }

        fun enqueue(type: Int, code: Int, value: Int) {
            pending.add(EvdevInputEvent(type = type, code = code, value = value, timeMicros = 0L))
        }

        override fun uploadRumble(strong: Double, weak: Double, durationMillis: Int): FakeEffect? {
            if (!forceFeedback) return null
            return FakeEffect()
        }

        override fun close() {
            closed.incrementAndGet()
        }
    }

    private class FakeSource : EvdevNativeSource {
        override var enumerationFailure: EvdevEnumerationFailure? = null
        private val plugged = LinkedHashMap<String, FakeNode>()
        private val hotplug = ArrayDeque<EvdevHotplug>()

        override fun listDevNodes(): List<String> = plugged.keys.toList()

        override fun openGamepad(devNode: String): EvdevNativeGamepad? =
            plugged.filterKeys { it.endsWith(devNode.substringAfterLast('/')) }
                .entries.firstOrNull { it.key == devNode }?.value

        override fun pollHotplug(): EvdevHotplug? = hotplug.removeFirstOrNull()

        override fun close() = Unit

        fun plug(devNode: String, node: FakeNode) {
            plugged[devNode] = node
            hotplug.add(EvdevHotplug("add", devNode))
        }

        fun unplug(devNode: String) {
            plugged.remove(devNode)
            hotplug.add(EvdevHotplug("remove", devNode))
        }
    }

    private fun routing(foreground: Boolean) = GamepadPortRouting(
        policy = GamepadRouting.ActiveSessionOnly,
        foregroundActive = foreground,
        effectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
    )

    // ------------------------------------------------------------- proofs

    @Test
    fun onlyGamepadNodesEnterTheInventory() {
        val source = FakeSource()
        val keyboard = FakeNode(
            identity = "1:1:1",
            name = "keyboard",
            buttonCodes = emptyList(), // no BTN_GAMEPAD range → not a gamepad
            axisCodes = emptyList(),
            syntheticButtonCodes = emptyList(),
            forceFeedback = false,
        )
        // the source decides gamepad-ness via kernel capabilities (D1):
        // a node that fails the probe never returns from openGamepad
        val pad = FakeNode(identity = "3:0x1234:0x5678")
        val broker = EvdevGamepadBroker(object : EvdevNativeSource by source {
            override fun openGamepad(devNode: String): EvdevNativeGamepad? =
                if (devNode.endsWith("event3")) source.openGamepad(devNode) else null
        })
        broker.start(spawnWorker = false)
        try {
            val port = broker.openSessionView()
            source.plug("/dev/input/event2", keyboard)
            source.plug("/dev/input/event3", pad)
            broker.pumpOnce()
            val gamepads = port.gamepads
            assertEquals(1, gamepads.size, "the keyboard must not be inventoried: $gamepads")
            assertEquals("kadre proof pad", gamepads.single().descriptor.name)
        } finally {
            broker.close()
        }
    }

    @Test
    fun repluggedDeviceKeepsItsStableKey() {
        val source = FakeSource()
        val node = FakeNode(identity = "3:0x1234:0x5678:serial-42")
        val broker = EvdevGamepadBroker(source)
        broker.start(spawnWorker = false)
        try {
            val port = broker.openSessionView()
            val events = ArrayList<GamepadPortEvent>()
            port.installObserver(events::add)
            source.plug("/dev/input/event5", node)
            broker.pumpOnce()
            val firstKey = port.gamepads.single().key

            source.unplug("/dev/input/event5")
            broker.pumpOnce()
            assertTrue(port.gamepads.isEmpty(), "unplugged device must leave the inventory")

            source.plug("/dev/input/event7", node) // different node number, same identity
            broker.pumpOnce()
            val secondKey = port.gamepads.single().key
            assertEquals(firstKey, secondKey, "identity, not the node number, owns the key (D2)")

            val connected = events.filterIsInstance<GamepadPortEvent.Connected>()
            assertEquals(2, connected.size, "both plugs must announce Connected")
            assertEquals(1, events.count { it is GamepadPortEvent.Disconnected })
        } finally {
            broker.close()
        }
    }

    @Test
    fun stateEventsCarryCanonicalValuesMatchingTheDescriptor() {
        val source = FakeSource()
        val node = FakeNode(identity = "3:0x1234:0x5678")
        val broker = EvdevGamepadBroker(source)
        broker.start(spawnWorker = false)
        try {
            val port = broker.openSessionView()
            port.updateRouting(routing(foreground = true))
            val events = ArrayList<GamepadPortEvent>()
            port.installObserver(events::add)
            source.plug("/dev/input/event4", node)
            broker.pumpOnce()
            events.clear()

            node.enqueue(0x01, 0x130, 1) // KEY BTN_SOUTH pressed
            node.enqueue(0x01, 0x220, 1) // KEY DPAD_UP pressed
            node.enqueue(0x03, 0x00, 32767) // ABS_X at maximum
            node.enqueue(0x03, 0x01, -32768) // ABS_Y at minimum
            node.enqueue(0x03, 0x02, 255) // ABS_Z (left trigger) full
            broker.pumpOnce()

            val changed = events.filterIsInstance<GamepadPortEvent.StateChanged>()
            assertEquals(1, changed.size, "one pass → one state event")
            val state = changed.single().state
            val descriptor = port.gamepads.single().descriptor

            // the runtime validates this exact correspondence on every event
            assertEquals(descriptor.buttons, state.buttons.map { it.button })
            assertEquals(descriptor.axes, state.axes.map { it.axis })

            val south = state.buttons.single { it.button == GamepadButton.South }
            assertTrue(south.pressed && south.value == 1.0)
            val leftX = state.axes.single { it.axis == GamepadAxis.LeftX }
            assertEquals(1.0, leftX.value)
            val leftY = state.axes.single { it.axis == GamepadAxis.LeftY }
            assertEquals(-1.0, leftY.value)
            val trigger = state.axes.single { it.axis == GamepadAxis.LeftTrigger }
            assertEquals(1.0, trigger.value)
            val dpadY = state.axes.single { it.axis == GamepadAxis.DpadY }
            assertEquals(-1.0, dpadY.value, "DPAD_UP synthesizes negative DpadY")

            // releasing the dpad returns the axis to rest without vanishing
            node.enqueue(0x01, 0x220, 0)
            broker.pumpOnce()
            val rested = events.filterIsInstance<GamepadPortEvent.StateChanged>().last().state
            assertEquals(0.0, rested.axes.single { it.axis == GamepadAxis.DpadY }.value)
            assertEquals(descriptor.axes, rested.axes.map { it.axis })
        } finally {
            broker.close()
        }
    }

    @Test
    fun permissionDeniedEnumerationSurfacesHonestly() {
        val source = FakeSource()
        source.enumerationFailure = EvdevEnumerationFailure(permissionDenied = true)
        val broker = EvdevGamepadBroker(source)
        broker.start(spawnWorker = false)
        try {
            val port = broker.openSessionView()
            val failure = port.inventoryFailure
            assertNotNull(failure)
            assertIs<KadreFailure.PermissionDenied>(failure)
        } finally {
            broker.close()
        }
    }

    @Test
    fun forceFeedbackFollowsUploadPlayStopEraseAndOwnership() {
        val source = FakeSource()
        val node = FakeNode(identity = "3:0x1234:0x5678")
        val broker = EvdevGamepadBroker(source)
        broker.start(spawnWorker = false)
        try {
            val port = broker.openSessionView()
            port.updateRouting(routing(foreground = true))
            source.plug("/dev/input/event6", node)
            broker.pumpOnce()

            val capabilities = port.gamepads.single().capabilities.effects
            assertIs<Capability.Supported<*>>(capabilities)

            val effect = GamepadEffect.DualRumble(strong = 0.75, weak = 0.25, duration = kotlin.time.Duration.parse("300ms"))
            val started = port.startEffect(port.gamepads.single().key, effect)
            val owner = assertIs<KadreResult.Success<*>>(started).value as EvdevPortEffect

            val second = port.startEffect(port.gamepads.single().key, effect)
            assertIs<KadreResult.Failure>(second, "exclusive ownership refuses a second live effect")
            assertIs<KadreFailure.AlreadyInUse>(second.reason)

            val stopped = owner.requestStop()
            assertIs<KadreResult.Success<*>>(stopped)
            owner.close()
            owner.close() // idempotent by contract

            // after release the slot frees again
            val resumed = port.startEffect(port.gamepads.single().key, effect)
            assertIs<KadreResult.Success<*>>(resumed)
            (resumed as KadreResult.Success<*>).let { success ->
                val again = success.value as EvdevPortEffect
                again.close()
            }

            // non-rumble kinds are refused without touching the device
            val localized = port.startEffect(
                port.gamepads.single().key,
                GamepadEffect.LocalizedHaptic(
                    org.graphiks.kadre.input.GamepadHapticLocality.LeftHandle,
                    0.5,
                    kotlin.time.Duration.parse("100ms"),
                ),
            )
            assertIs<KadreResult.Failure>(localized)
            assertIs<KadreFailure.InvalidRequest>(localized.reason)
        } finally {
            broker.close()
        }
    }

    @Test
    fun devicesWithoutForceFeedbackAdvertiseUnsupportedEffects() {
        val source = FakeSource()
        val node = FakeNode(identity = "3:0x1234:0x5678", forceFeedback = false)
        val broker = EvdevGamepadBroker(source)
        broker.start(spawnWorker = false)
        try {
            val port = broker.openSessionView()
            source.plug("/dev/input/event6", node)
            broker.pumpOnce()
            val gamepad = port.gamepads.single()
            assertIs<Capability.Unsupported>(gamepad.capabilities.effects)
            val started = port.startEffect(
                gamepad.key,
                GamepadEffect.DualRumble(0.5, 0.5, kotlin.time.Duration.parse("100ms")),
            )
            val failure = assertIs<KadreResult.Failure>(started)
            assertIs<KadreFailure.PlatformFailure>(failure.reason)
        } finally {
            broker.close()
        }
    }

    @Test
    fun suspendedViewsReceiveNoStateUntilTheyResume() {
        val source = FakeSource()
        val node = FakeNode(identity = "3:0x1234:0x5678")
        val broker = EvdevGamepadBroker(source)
        broker.start(spawnWorker = false)
        try {
            val foreground = broker.openSessionView()
            foreground.updateRouting(routing(foreground = true))
            val background = broker.openSessionView()
            background.updateRouting(routing(foreground = false))
            val fgEvents = ArrayList<GamepadPortEvent>()
            val bgEvents = ArrayList<GamepadPortEvent>()
            foreground.installObserver(fgEvents::add)
            background.installObserver(bgEvents::add)

            source.plug("/dev/input/event9", node)
            broker.pumpOnce()
            fgEvents.clear()
            bgEvents.clear()

            node.enqueue(0x01, 0x130, 1)
            broker.pumpOnce()
            assertTrue(fgEvents.any { it is GamepadPortEvent.StateChanged })
            assertTrue(bgEvents.none { it is GamepadPortEvent.StateChanged }, "a suspended view sees no state")
            assertEquals(GamepadRoutingState.Suspended, background.gamepads.single().routing)

            background.updateRouting(routing(foreground = true))
            val resumed = bgEvents.filterIsInstance<GamepadPortEvent.RoutingChanged>()
            assertEquals(1, resumed.size)
            assertEquals(GamepadRoutingState.Routed, resumed.single().routing)
            assertEquals(GamepadRoutingState.Routed, background.gamepads.single().routing)
        } finally {
            broker.close()
        }
    }

    @Test
    fun closingAViewDetachesItWithoutStoppingTheBroker() {
        val source = FakeSource()
        val node = FakeNode(identity = "3:0x1234:0x5678")
        val broker = EvdevGamepadBroker(source)
        broker.start(spawnWorker = false)
        try {
            val first = broker.openSessionView()
            val second = broker.openSessionView()
            source.plug("/dev/input/event8", node)
            broker.pumpOnce()
            assertEquals(1, first.gamepads.size)

            first.close()
            first.close() // idempotent
            assertTrue(first.gamepads.isEmpty())
            val events = ArrayList<GamepadPortEvent>()
            first.installObserver(events::add)
            node.enqueue(0x01, 0x130, 1)
            broker.pumpOnce()
            assertTrue(events.isEmpty(), "a closed view observes nothing")

            assertEquals(1, second.gamepads.size, "the broker serves the surviving view")
        } finally {
            broker.close()
        }
    }

    @Test
    fun brokerCloseReleasesNodes() {
        val source = FakeSource()
        val node = FakeNode(identity = "3:0x1234:0x5678")
        val broker = EvdevGamepadBroker(source)
        broker.start(spawnWorker = false)
        source.plug("/dev/input/event1", node)
        broker.pumpOnce()
        broker.close()
        broker.close() // idempotent
        assertEquals(1, node.closed.get(), "the node fd closes exactly once")
    }
}
