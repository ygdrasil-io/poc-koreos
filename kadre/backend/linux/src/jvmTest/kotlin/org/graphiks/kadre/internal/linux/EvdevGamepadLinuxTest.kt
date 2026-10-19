package org.graphiks.kadre.internal.linux

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.policy.DeviceEffectOwnership
import org.graphiks.kadre.policy.GamepadRouting
import org.graphiks.kffi.evdev.EvdevEvent
import org.graphiks.kffi.evdev.UinputVirtualGamepad

/**
 * O3 proofs of the evdev gamepad service on the real Linux input stack
 * (design P-1..P-4), against the uinput oracle (D6): a virtual gamepad
 * the proofs create, drive and destroy. Linux-only — the dedicated
 * `linuxEvdevTests` task runs it under a privileged container with
 * /dev/uinput and /dev/input.
 */
class EvdevGamepadLinuxTest {

    private class Oracle(
        val virtual: UinputVirtualGamepad,
        val devNode: String,
        val pumping: AtomicBoolean,
        val pumper: Thread,
    ) {
        fun close() {
            pumping.set(false)
            pumper.join(1_000)
            virtual.close()
        }

        companion object {
            /** Creates a virtual pad and materializes its event node. */
            fun openPad(ffEffectsMax: Int, name: String = "kadre o3 pad", gamepadButtons: Boolean = true): Oracle? {
                val virtual = UinputVirtualGamepad.open(
                    name = name,
                    bustype = 0x03,
                    vendor = 0x1a2b,
                    product = 0x3c4d + ffEffectsMax,
                    // plain keys make the node a non-gamepad for detection
                    buttons = if (gamepadButtons) {
                        listOf(0x130, 0x131, 0x136, 0x137, 0x13a, 0x13b, 0x13c, 0x13d, 0x13e)
                    } else {
                        listOf(0x1e, 0x1f, 0x20, 0x21) // KEY_A..KEY_D
                    },
                    axes = listOf(
                        UinputVirtualGamepad.Axis(0x00, -32768, 32767),
                        UinputVirtualGamepad.Axis(0x01, -32768, 32767),
                        UinputVirtualGamepad.Axis(0x02, 0, 255),
                    ),
                    ffEffectsMax = ffEffectsMax,
                ) ?: return null
                val devNode = discoverEventNode(virtual) ?: run {
                    virtual.close()
                    return null
                }
                val pumping = AtomicBoolean(true)
                val pumper = Thread {
                    while (pumping.get()) {
                        if (!virtual.pumpFfRequest()) Thread.sleep(5)
                    }
                }.apply { isDaemon = true; start() }
                return Oracle(virtual, devNode, pumping, pumper)
            }

            /**
             * The kernel registers the node in devtmpfs; a container's
             * /dev is a static snapshot, so read major/minor from sysfs
             * and mknod it ourselves.
             */
            private fun discoverEventNode(virtual: UinputVirtualGamepad): String? {
                val sysName = virtual.sysName() ?: return null
                val sysDir = File("/sys/class/input/$sysName")
                val eventDir = waitFor(2_000) {
                    sysDir.listFiles().orEmpty().firstOrNull { it.name.startsWith("event") }
                } ?: return null
                val node = File("/dev/input/${eventDir.name}")
                if (!node.exists()) {
                    val majorMinor = File(eventDir, "dev").readText().trim()
                    val parts = majorMinor.split(":")
                    ProcessBuilder("mknod", node.absolutePath, "c", parts[0], parts[1])
                        .start().waitFor()
                }
                return waitFor(2_000) { node.takeIf { it.exists() } }?.absolutePath
            }

            private fun <T> waitFor(timeoutMillis: Long, probe: () -> T?): T? {
                val deadline = System.currentTimeMillis() + timeoutMillis
                while (System.currentTimeMillis() < deadline) {
                    probe()?.let { return it }
                    Thread.sleep(50)
                }
                return null
            }
        }
    }

    private fun brokerWithWorker(): Pair<EvdevGamepadBroker, EvdevGamepadPort> {
        val broker = EvdevGamepadBroker(KffiEvdevNativeSource())
        broker.start(spawnWorker = true)
        val port = broker.openSessionView()
        port.updateRouting(
            GamepadPortRouting(
                policy = GamepadRouting.ActiveSessionOnly,
                foregroundActive = true,
                effectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
            ),
        )
        return broker to port
    }

    private fun <T> await(timeoutMillis: Long, probe: () -> T?): T? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            probe()?.let { return it }
            Thread.sleep(25)
        }
        return null
    }

    @Test
    fun p1_inventoryListsTheVirtualPadAndExcludesNonGamepads() {
        val keyboard = Oracle.openPad(ffEffectsMax = 0, name = "kadre o3 keyboard", gamepadButtons = false) ?: return
        val pad = Oracle.openPad(ffEffectsMax = 0) ?: return
        val (broker, port) = brokerWithWorker()
        try {
            val gamepad = await(5_000) {
                port.gamepads.firstOrNull { it.descriptor.name == "kadre o3 pad" }
            }
            assertNotNull(gamepad, "the virtual pad never reached the inventory")
            assertTrue(
                port.gamepads.none { it.descriptor.name == "kadre o3 keyboard" },
                "a node without gamepad buttons must stay out of the inventory",
            )
            // identity from the kernel id (D2): bustype:vendor:product
            assertTrue(gamepad.key >= 0)
            val south = gamepad.descriptor.buttons.contains(GamepadButton.South)
            assertTrue(south)
            println("EVDEV-O3-P1 gamepads=${port.gamepads.size} pad=${gamepad.descriptor.name} key=${gamepad.key}")
        } finally {
            broker.close()
            pad.close()
            keyboard.close()
        }
    }

    @Test
    fun p2_destroyAndRecreateKeepsTheStableKey() {
        val first = Oracle.openPad(ffEffectsMax = 0) ?: return
        val (broker, port) = brokerWithWorker()
        try {
            val connected = ArrayList<GamepadPortEvent.Connected>()
            val disconnected = ArrayList<GamepadPortEvent.Disconnected>()
            port.installObserver { event ->
                when (event) {
                    is GamepadPortEvent.Connected -> connected.add(event)
                    is GamepadPortEvent.Disconnected -> disconnected.add(event)
                    else -> Unit
                }
            }
            val initial = await(5_000) { port.gamepads.firstOrNull()?.takeIf { it.descriptor.name == "kadre o3 pad" } }
            assertNotNull(initial, "the pad never connected")

            first.close() // destroy: the worker sees ENODEV and retires it
            await(5_000) { if (port.gamepads.isEmpty()) true else null }
            assertTrue(port.gamepads.isEmpty(), "the destroyed pad must leave the inventory")
            await(5_000) { disconnected.firstOrNull() }

            // recreate with the same identity: the node number may differ,
            // the key must not
            val second = Oracle.openPad(ffEffectsMax = 0)
            assertNotNull(second, "recreating the oracle failed")
            try {
                val back = await(5_000) { port.gamepads.firstOrNull()?.takeIf { it.descriptor.name == "kadre o3 pad" } }
                assertNotNull(back, "the recreated pad never reconnected")
                assertEquals(initial.key, back.key, "identity, not the node, owns the key (D2)")
                // the cold-plug inventory itself carries no events (the
                // snapshot is truth); the reconnect after destroy does
                assertTrue(connected.isNotEmpty(), "the reconnect must announce Connected")
                println("EVDEV-O3-P2 key=${initial.key} stable-across-replug devNode=${second.devNode}")
            } finally {
                second.close()
            }
        } finally {
            broker.close()
            first.close()
        }
    }

    @Test
    fun p3_injectedEventsArriveAsCanonicalState() {
        val pad = Oracle.openPad(ffEffectsMax = 0) ?: return
        val (broker, port) = brokerWithWorker()
        try {
            val gamepad = await(5_000) { port.gamepads.firstOrNull()?.takeIf { it.descriptor.name == "kadre o3 pad" } }
            assertNotNull(gamepad)

            pad.virtual.inject(EvdevEvent.KEY, 0x130, 1) // BTN_SOUTH
            pad.virtual.inject(EvdevEvent.ABS, 0x00, 32767) // ABS_X max
            pad.virtual.inject(EvdevEvent.ABS, 0x01, -32768) // ABS_Y min
            pad.virtual.inject(EvdevEvent.ABS, 0x02, 255) // left trigger full

            val state = await(5_000) {
                val latest = port.gamepads.firstOrNull() ?: return@await null
                val south = latest.state.buttons.firstOrNull { it.button == GamepadButton.South }
                if (south?.pressed == true) latest else null
            }
            assertNotNull(state, "the injected press never arrived")
            assertEquals(1.0, state.state.axes.single { it.axis == GamepadAxis.LeftX }.value)
            assertEquals(-1.0, state.state.axes.single { it.axis == GamepadAxis.LeftY }.value)
            assertEquals(1.0, state.state.axes.single { it.axis == GamepadAxis.LeftTrigger }.value)
            // the snapshot contract the runtime validates on every event
            assertEquals(state.descriptor.buttons, state.state.buttons.map { it.button })
            assertEquals(state.descriptor.axes, state.state.axes.map { it.axis })
            println("EVDEV-O3-P3 key=${state.key} buttons=${state.state.buttons.size} axes=${state.state.axes.size}")
        } finally {
            broker.close()
            pad.close()
        }
    }

    @Test
    fun p4_forceFeedbackCyclesThroughUploadPlayStopErase() {
        val pad = Oracle.openPad(ffEffectsMax = 4) ?: return
        val (broker, port) = brokerWithWorker()
        try {
            val gamepad = await(5_000) { port.gamepads.firstOrNull()?.takeIf { it.descriptor.name == "kadre o3 pad" } }
            assertNotNull(gamepad)
            assertIsSupported(gamepad.capabilities.effects)

            val started = port.startEffect(
                gamepad.key,
                GamepadEffect.DualRumble(strong = 0.8, weak = 0.4, duration = kotlin.time.Duration.parse("PT0.3S")),
            )
            val owner = (started as KadreResult.Success).value

            // the play reaches the uinput owner: { code = effect id, value = 1 }
            val played = await(5_000) { pad.virtual.pollForwardedRequest()?.takeIf { it.value == 1 } }
            assertNotNull(played, "the uinput owner never observed the play")

            val stopped = owner.requestStop()
            assertTrue(stopped is KadreResult.Success, "stop failed: $stopped")
            val stopSeen = await(5_000) { pad.virtual.pollForwardedRequest()?.takeIf { it.value == 0 } }
            assertNotNull(stopSeen, "the uinput owner never observed the stop")

            owner.close() // erase: the kernel completes the cycle
            val second = port.startEffect(
                gamepad.key,
                GamepadEffect.DualRumble(strong = 0.1, weak = 0.1, duration = kotlin.time.Duration.parse("PT0.1S")),
            )
            assertTrue(second is KadreResult.Success, "a released slot must admit a new effect: $second")
            (second as KadreResult.Success).value.close()
            println("EVDEV-O3-P4 key=${gamepad.key} playEffectId=${played.code} stopEffectId=${stopSeen.code}")
        } finally {
            broker.close()
            pad.close()
        }
    }

    private fun assertIsSupported(effects: org.graphiks.kadre.diagnostics.Capability<*>) {
        assertTrue(effects is Capability.Supported<*>, "an FF-capable pad must advertise DualRumble support")
    }
}
