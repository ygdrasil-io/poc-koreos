package org.graphiks.kadre.internal.win32

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kffi.win32.managed.Win32XInputCapabilitiesValue
import org.graphiks.kffi.win32.managed.Win32XInputStateValue

class Win32GamepadBrokerTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching { it.close() } }
        cleanups.clear()
    }

    private class FakeNative : Win32XInputNative {
        var connected = arrayOf(false, false, false, false)
        val vibrationWrites = mutableListOf<Pair<Int, Pair<Int, Int>>>()
        val statePolls = AtomicInteger(0)

        override fun getState(index: Int): Win32XInputStateValue? {
            statePolls.incrementAndGet()
            if (!connected[index]) return null
            // One pressed button (0x8000 = North) so the copy path is observable.
            return Win32XInputStateValue(
                packetNumber = index + 1,
                buttons = 0x8000,
                leftTrigger = 0, rightTrigger = 0,
                thumbLX = 0, thumbLY = 0, thumbRX = 0, thumbRY = 0,
            )
        }

        override fun setVibration(index: Int, left: Int, right: Int): Boolean {
            if (!connected[index]) return false
            vibrationWrites += index to (left to right)
            return true
        }

        override fun getCapabilities(index: Int): Win32XInputCapabilitiesValue? {
            if (!connected[index]) return null
            return Win32XInputCapabilitiesValue(
                type = 1, subType = 1, flags = 0, buttons = 0xF3FF,
                leftTrigger = 0, rightTrigger = 0,
                thumbLX = 0, thumbLY = 0, thumbRX = 0, thumbRY = 0,
                vibrationLeft = 0, vibrationRight = 0,
            )
        }
    }

    @Test fun emptyHostStaysEmptyWithoutInventingGamepads() {
        val native = FakeNative() // all four slots disconnected
        val broker = Win32GamepadBroker(native, statePollMillis = 1)
        cleanups.add(broker)
        val port = broker.openSessionView()
        val events = CopyOnWriteArrayList<GamepadPortEvent>()
        val registration = port.installObserver { events += it }
        try {
            broker.pollOnce()
            broker.pollOnce()
            assertTrue(events.isEmpty(), "an empty host must stay empty: $events")
            assertTrue(port.gamepads.isEmpty())
        } finally {
            registration.close()
        }
    }

    @Test fun connectedSlotPublishesOnceAndIdenticalStateStaysQuiet() {
        val native = FakeNative() // start all-disconnected: the daemon's first poll observes nothing
        val broker = Win32GamepadBroker(native, statePollMillis = 1)
        cleanups.add(broker)
        val port = broker.openSessionView()
        // Deterministic handshake: the daemon's first poll has run (and published
        // nothing) before the observer exists, so only the explicit polls below
        // can emit — any interleaved daemon poll is a no-op or a duplicate-free
        // Connected, never a second event.
        while (native.statePolls.get() < 1) Thread.yield()
        val events = CopyOnWriteArrayList<GamepadPortEvent>()
        val registration = port.installObserver { events += it }
        try {
            native.connected = arrayOf(true, false, false, false)
            broker.pollOnce()
            broker.pollOnce() // identical state: no duplicate publication
        } finally {
            registration.close()
        }
        assertEquals(1, events.size, "one Connected event, no duplicate StateChanged: $events")
        val connected = assertIs<GamepadPortEvent.Connected>(events.single())
        assertEquals(0L, connected.gamepad.key)
        val pressed = connected.gamepad.state.buttons.filter { it.pressed }.map { it.button }
        assertEquals(listOf(GamepadButton.North), pressed)
    }

    @Test fun disconnectEmitsDisconnectedEventAndGamepadLeavesInventory() {
        val native = FakeNative().apply { connected = arrayOf(true, false, false, false) }
        val broker = Win32GamepadBroker(native, statePollMillis = 1)
        cleanups.add(broker)
        val port = broker.openSessionView()
        val events = CopyOnWriteArrayList<GamepadPortEvent>()
        val registration = port.installObserver { events += it }
        try {
            broker.pollOnce()
            assertEquals(1, port.gamepads.size)
            native.connected = arrayOf(false, false, false, false)
            broker.pollOnce()
        } finally {
            registration.close()
        }
        assertIs<GamepadPortEvent.Disconnected>(events.last())
        assertTrue(port.gamepads.isEmpty())
    }

    @Test fun dualRumbleWritesBothMotorsAndStopZeroesThem() {
        val native = FakeNative().apply { connected = arrayOf(true, false, false, false) }
        val broker = Win32GamepadBroker(native, statePollMillis = 1)
        cleanups.add(broker)
        val port = broker.openSessionView()
        val result = port.startEffect(0L, GamepadEffect.DualRumble(1.0, 0.5, 500.milliseconds))
        val effect = assertIs<KadreResult.Success<GamepadPortEffect>>(result).value
        assertEquals(listOf(0 to (65535 to 32767)), native.vibrationWrites)
        effect.requestStop()
        assertEquals(
            listOf(0 to (65535 to 32767), 0 to (0 to 0)),
            native.vibrationWrites,
            "requestStop zeroes both motors exactly once",
        )
    }

    @Test fun effectOnDisconnectedSlotFailsClosed() {
        val native = FakeNative() // nothing connected
        val broker = Win32GamepadBroker(native, statePollMillis = 1)
        cleanups.add(broker)
        val port = broker.openSessionView()
        val failure = assertIs<KadreResult.Failure>(
            port.startEffect(0L, GamepadEffect.DualRumble(1.0, 1.0, 500.milliseconds)),
        )
        assertIs<KadreFailure.Closed>(failure.reason)
    }

    @Test fun fullButtonMaskMapsEveryStandardButtonInCanonicalOrder() {
        val pressed = xinputButtonsToStandard(0xFFFF)
        assertEquals(
            listOf(
                GamepadButton.DpadUp, GamepadButton.DpadDown, GamepadButton.DpadLeft, GamepadButton.DpadRight,
                GamepadButton.Start, GamepadButton.Select,
                GamepadButton.LeftStick, GamepadButton.RightStick,
                GamepadButton.LeftShoulder, GamepadButton.RightShoulder,
                GamepadButton.South, GamepadButton.East, GamepadButton.West, GamepadButton.North,
            ),
            pressed,
        )
        assertEquals(
            listOf(GamepadButton.South),
            xinputButtonsToStandard(0x1000),
            "each XInput mask bit lands on exactly its Standard button",
        )
    }

    @Test fun canonicalAxesNormalizeThumbsWithInvertedYAndTriggersOver255() {
        val state = standardStateFrom(
            buttons = 0, leftTrigger = 255, rightTrigger = 128,
            thumbLX = -32768, thumbLY = 32767, thumbRX = 16384, thumbRY = -16384,
        )
        val axes = state.axes.associate { it.axis to it.value }
        assertEquals(-1.0, axes.getValue(GamepadAxis.LeftX))
        assertEquals(-1.0, axes.getValue(GamepadAxis.LeftY), "full up (32767) inverts to -1.0")
        assertEquals(16384 / 32767.0, axes.getValue(GamepadAxis.RightX), "positive side divides by 32767")
        assertEquals(0.5, axes.getValue(GamepadAxis.RightY), "negative side divides by 32768, Y inverted")
        assertEquals(1.0, axes.getValue(GamepadAxis.LeftTrigger))
        assertEquals(128 / 255.0, axes.getValue(GamepadAxis.RightTrigger))
    }
}
