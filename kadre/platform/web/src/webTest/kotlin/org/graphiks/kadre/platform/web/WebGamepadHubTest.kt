package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.policy.DeviceEffectOwnership
import org.graphiks.kadre.policy.GamepadRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The hub is the page's gamepad broker and the port is one session's projection of it, so these
 * tests script the whole story over the two seams the broker consumes: a [FakeGamepadDom] whose
 * poll answers whatever the test staged, and a [ManualFrameScheduler] whose frame the test fires by
 * hand. The runtime is the real observer here: it installs one observer and then reads `gamepads`,
 * and every event it may receive must agree with the snapshot it can read beside it.
 */
class WebGamepadHubTest {
    private class FakeDomGamepad(
        override val index: Int,
        override val domId: String = "pad-$index",
        override val connected: Boolean = true,
        override val mapping: String? = "standard",
        override val buttonValues: List<Double> = List(17) { 0.0 },
        override val axisValues: List<Double> = List(4) { 0.0 },
        override val hapticActuator: WebDomHapticActuator? = null,
    ) : WebDomGamepad

    /** The scripted poll: the test stages the array the browser would answer, holes included. */
    private class FakeGamepadDom : WebGamepadDom {
        var pads: List<WebDomGamepad?> = emptyList()
        var polls = 0
        var registrations = 0
        var registrationCloses = 0
        var closeCalls = 0
        override val secureContext: Boolean = true
        private val appeared = mutableListOf<() -> Unit>()
        private val disappeared = mutableListOf<() -> Unit>()

        override fun getGamepads(): List<WebDomGamepad?> {
            polls += 1
            return pads
        }

        override fun onGamepadAppeared(listener: () -> Unit): AutoCloseable = register(listener, appeared)

        override fun onGamepadDisappeared(listener: () -> Unit): AutoCloseable = register(listener, disappeared)

        override fun close() {
            closeCalls += 1
        }

        fun listenerCount(): Int = appeared.size + disappeared.size

        private fun register(listener: () -> Unit, into: MutableList<() -> Unit>): AutoCloseable {
            registrations += 1
            into += listener
            return AutoCloseable {
                registrationCloses += 1
                into.remove(listener)
            }
        }
    }

    /** The frame cadence by hand: `schedule` captures, [tick] fires, `cancel` withdraws for real. */
    private class ManualFrameScheduler : WebFrameScheduler {
        var schedules = 0
        var cancels = 0
        private var pending: (() -> Unit)? = null

        override fun schedule(frame: () -> Unit): AutoCloseable {
            schedules += 1
            check(pending == null) { "the hub must never hold two pending frames" }
            pending = frame
            return AutoCloseable {
                cancels += 1
                if (pending === frame) pending = null
            }
        }

        /** Fires the one pending frame, exactly as one animation frame would. */
        fun tick() {
            val frame = pending ?: error("no frame was scheduled")
            pending = null
            frame()
        }

        fun pendingCount(): Int = if (pending == null) 0 else 1
    }

    private class Harness {
        val dom = FakeGamepadDom()
        val frames = ManualFrameScheduler()
        val hub = WebGamepadHub(dom, frames)

        fun openObservedPort(events: MutableList<GamepadPortEvent>): Pair<WebGamepadPort, AutoCloseable> {
            val port = hub.openPort()
            val observer = port.installObserver { event -> events += event }
            return port to observer
        }

        fun tick() = frames.tick()
    }

    private fun routed(): GamepadPortRouting = GamepadPortRouting(
        policy = GamepadRouting.AllForegroundSessions,
        foregroundActive = true,
        effectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
    )

    private fun suspended(): GamepadPortRouting = GamepadPortRouting(
        policy = GamepadRouting.AllForegroundSessions,
        foregroundActive = false,
        effectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
    )

    private fun activeOnly(): GamepadPortRouting = GamepadPortRouting(
        policy = GamepadRouting.ActiveSessionOnly,
        foregroundActive = true,
        effectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
    )

    @Test
    fun `poll loop runs only while a port is open`() {
        val harness = Harness()

        val port = harness.hub.openPort()
        assertEquals(2, harness.dom.registrations, "the first open port installs both connect listeners")
        assertEquals(1, harness.dom.polls, "openPort polls immediately, so an already-connected pad is in the first snapshot")
        assertEquals(1, harness.frames.schedules, "the first open port arms the frame loop")

        harness.tick()
        assertEquals(2, harness.dom.polls, "each frame polls the dom once")
        assertEquals(1, harness.frames.pendingCount(), "exactly one frame is pending at any moment")
        assertEquals(0, harness.frames.cancels)

        port.close()
        assertEquals(2, harness.dom.registrationCloses, "the last close withdraws both listeners")
        assertEquals(0, harness.dom.listenerCount())
        assertEquals(0, harness.frames.pendingCount(), "the pending frame is cancelled on the last close")
        assertEquals(1, harness.frames.cancels)
        val pollsAfterClose = harness.dom.polls

        // Reopening restarts everything; a close without an open port withdraws nothing new.
        val second = harness.hub.openPort()
        assertEquals(4, harness.dom.registrations, "reopening re-registers the listeners")
        assertEquals(3, harness.frames.schedules, "reopening re-arms the frame loop")
        assertEquals(pollsAfterClose + 1, harness.dom.polls, "reopening polls immediately again")
        second.close()
        assertEquals(4, harness.dom.registrationCloses)
        assertEquals(2, harness.frames.cancels)
        assertEquals(0, harness.dom.listenerCount())
    }

    @Test
    fun `pad appearing emits Connected with key equal to dom index`() {
        val harness = Harness()
        val port = harness.hub.openPort()
        val events = mutableListOf<GamepadPortEvent>()
        var snapshotDuringEvent: List<GamepadPortGamepad>? = null
        port.installObserver { event ->
            events += event
            if (event is GamepadPortEvent.Connected) snapshotDuringEvent = port.gamepads
        }

        harness.dom.pads = listOf(null, FakeDomGamepad(index = 3, domId = "pad-three"))
        harness.tick()

        val connected = assertIs<GamepadPortEvent.Connected>(events.single())
        assertEquals(3L, connected.gamepad.key, "the key is the DOM gamepad index")
        assertEquals("pad-three", connected.gamepad.descriptor.name)
        // State before event: the pad the event announces is already in the snapshot an observer
        // reading `gamepads` inside the very event would see.
        assertEquals(3L, snapshotDuringEvent?.single()?.key)
        assertEquals(listOf(3L), port.gamepads.map { it.key })
    }

    @Test
    fun `value change emits exactly one StateChanged`() {
        val harness = Harness()
        val port = harness.hub.openPort()
        port.updateRouting(routed())
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        harness.dom.pads = listOf(FakeDomGamepad(index = 0))
        harness.tick()
        assertEquals(1, events.size, "the connection itself is one event")

        harness.dom.pads = listOf(
            FakeDomGamepad(index = 0, buttonValues = List(17) { if (it == 5) 1.0 else 0.0 }),
        )
        harness.tick()
        assertEquals(2, events.size)
        val changed = assertIs<GamepadPortEvent.StateChanged>(events[1])
        assertEquals(0L, changed.key)
        assertEquals(1.0, changed.state.buttons[5].value)
        assertEquals(true, changed.state.buttons[5].pressed)
        assertEquals(0.0, changed.state.buttons[4].value)

        // The next poll observes the same values: no change, no event.
        harness.tick()
        assertEquals(2, events.size)
    }

    @Test
    fun `pad disappearing emits Disconnected and reconnect starts clean`() {
        val harness = Harness()
        val port = harness.hub.openPort()
        port.updateRouting(routed())
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        harness.dom.pads = listOf(FakeDomGamepad(index = 1, domId = "first-id"))
        harness.tick()
        assertIs<GamepadPortEvent.Connected>(events.single())

        harness.dom.pads = emptyList()
        harness.tick()
        assertEquals(1L, assertIs<GamepadPortEvent.Disconnected>(events[1]).key)
        assertTrue(port.gamepads.isEmpty(), "a disconnected pad leaves no projection behind")

        // The same index returns as a new connection: a fresh descriptor frozen from the pad the
        // browser answers with now, never the one the previous connection froze.
        harness.dom.pads = listOf(FakeDomGamepad(index = 1, domId = "second-id"))
        harness.tick()
        val reconnected = assertIs<GamepadPortEvent.Connected>(events[2])
        assertEquals(1L, reconnected.gamepad.key)
        assertEquals("second-id", reconnected.gamepad.descriptor.name)
        assertEquals(listOf(1L), port.gamepads.map { it.key })
    }

    @Test
    fun `holes produce no phantom pads`() {
        val harness = Harness()
        val port = harness.hub.openPort()
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        harness.dom.pads = listOf(null, FakeDomGamepad(index = 1), null)
        harness.tick()

        val connected = assertIs<GamepadPortEvent.Connected>(events.single())
        assertEquals(1L, connected.gamepad.key, "exactly the connected pad, keyed by its own index")
        assertEquals(listOf(1L), port.gamepads.map { it.key })
    }

    @Test
    fun `resume diffs against last observed state`() {
        val harness = Harness()
        val port = harness.hub.openPort()
        port.updateRouting(routed())
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        harness.dom.pads = listOf(FakeDomGamepad(index = 0))
        harness.tick()
        assertIs<GamepadPortEvent.Connected>(events.single())

        // The page hides and the frames stop; the device's values change twice, unobserved, then
        // return to the values the hub last observed. No poll happened, so nothing was recorded.
        harness.dom.pads = listOf(FakeDomGamepad(index = 0, buttonValues = List(17) { if (it == 2) 1.0 else 0.0 }))
        harness.dom.pads = listOf(FakeDomGamepad(index = 0))
        // Frames resume: the first poll diffs against the last OBSERVED state, so a pad whose
        // values are back where the hub left them publishes nothing — no stale burst.
        harness.tick()
        assertEquals(1, events.size, "values back at the last observed state: no event on resume")

        // And one real change after resume is exactly one StateChanged carrying the current values.
        harness.dom.pads = listOf(FakeDomGamepad(index = 0, buttonValues = List(17) { if (it == 4) 0.75 else 0.0 }))
        harness.tick()
        assertEquals(2, events.size)
        val changed = assertIs<GamepadPortEvent.StateChanged>(events[1])
        assertEquals(0.75, changed.state.buttons[4].value)
        assertEquals(0.0, changed.state.buttons[2].value)
    }

    @Test
    fun `routing suspension publishes neutral controls`() {
        val harness = Harness()
        val port = harness.hub.openPort()
        port.updateRouting(routed())
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        harness.dom.pads = listOf(FakeDomGamepad(index = 0, buttonValues = List(17) { if (it == 6) 1.0 else 0.0 }))
        harness.tick()
        val connected = assertIs<GamepadPortEvent.Connected>(events.single())

        port.updateRouting(suspended())
        val suspendedEvent = assertIs<GamepadPortEvent.RoutingChanged>(events[1])
        assertEquals(0L, suspendedEvent.key)
        assertEquals(GamepadRoutingState.Suspended, suspendedEvent.routing)
        assertEquals(
            WebGamepadMapping.neutralState(connected.gamepad.descriptor),
            suspendedEvent.state,
            "the suspended projection publishes the neutral reading of the frozen descriptor",
        )
        val snapshot = port.gamepads.single()
        assertEquals(GamepadRoutingState.Suspended, snapshot.routing)
        assertEquals(
            WebGamepadMapping.neutralState(connected.gamepad.descriptor),
            snapshot.state,
        )

        // Values change while suspended: the fresh polls record them for later but publish nothing
        // — the suspended projection's state stays neutral.
        harness.dom.pads = listOf(FakeDomGamepad(index = 0, buttonValues = List(17) { if (it == 6) 0.25 else 0.0 }))
        harness.tick()
        assertEquals(2, events.size, "a suspended port receives no StateChanged")
        assertEquals(0.0, port.gamepads.single().state.buttons[6].value)

        // Resuming publishes the recorded real values as the fresh state of the same pads.
        port.updateRouting(routed())
        val resumed = assertIs<GamepadPortEvent.RoutingChanged>(events[2])
        assertEquals(GamepadRoutingState.Routed, resumed.routing)
        assertEquals(0.25, resumed.state.buttons[6].value)
        assertEquals(0.25, port.gamepads.single().state.buttons[6].value, "the snapshot carries the recorded reading")
    }

    @Test
    fun `closing the routed port re-arbitrates the surviving one`() {
        val harness = Harness()
        val first = harness.hub.openPort()
        first.updateRouting(activeOnly())
        val second = harness.hub.openPort()
        // The same eligible context as the first port: the survivor is suspended only by the
        // arbitration's order, so the first port leaving must promote it.
        second.updateRouting(activeOnly())
        val firstEvents = mutableListOf<GamepadPortEvent>()
        val secondEvents = mutableListOf<GamepadPortEvent>()
        first.installObserver { firstEvents += it }
        second.installObserver { secondEvents += it }

        harness.dom.pads = listOf(FakeDomGamepad(index = 0, buttonValues = List(17) { if (it == 1) 1.0 else 0.0 }))
        harness.tick()
        // Both ports opened with the same eligible context; the active-session arbitration elected
        // the first, so the survivor is suspended while the first is routed.
        val firstConnected = assertIs<GamepadPortEvent.Connected>(firstEvents.single())
        assertEquals(GamepadRoutingState.Routed, firstConnected.gamepad.routing)
        assertEquals(1.0, firstConnected.gamepad.state.buttons[1].value)
        val secondConnected = assertIs<GamepadPortEvent.Connected>(secondEvents.single())
        assertEquals(GamepadRoutingState.Suspended, secondConnected.gamepad.routing)
        assertEquals(0.0, secondConnected.gamepad.state.buttons[1].value)
        assertEquals(GamepadRoutingState.Suspended, second.gamepads.single().routing)

        first.close()
        // The elected port left: the survivor re-arbitrates, hears one RoutingChanged with the
        // fresh recorded state, and its snapshot agrees with its events again.
        assertEquals(2, secondEvents.size, "the survivor hears the re-arbitration as one RoutingChanged")
        val changed = assertIs<GamepadPortEvent.RoutingChanged>(secondEvents[1])
        assertEquals(0L, changed.key)
        assertEquals(GamepadRoutingState.Routed, changed.routing)
        assertEquals(1.0, changed.state.buttons[1].value)
        assertEquals(GamepadRoutingState.Routed, second.gamepads.single().routing)
        assertEquals(1.0, second.gamepads.single().state.buttons[1].value)
        assertEquals(1, firstEvents.size, "the closed port hears nothing of the re-arbitration")
    }

    @Test
    fun `hostile readings never stream no-op state changes`() {
        val harness = Harness()
        val port = harness.hub.openPort()
        port.updateRouting(routed())
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        harness.dom.pads = listOf(FakeDomGamepad(index = 0, buttonValues = List(17) { if (it == 3) 1.0 else 0.0 }))
        harness.tick()
        assertIs<GamepadPortEvent.Connected>(events.single())

        // The pad starts reporting NaN on every control — a reading that need not equal itself:
        harness.dom.pads = listOf(FakeDomGamepad(index = 0, buttonValues = List(17) { Double.NaN }))
        harness.tick()
        assertEquals(2, events.size, "exactly one neutralizing StateChanged for the hostile reading")
        val neutralized = assertIs<GamepadPortEvent.StateChanged>(events[1])
        assertEquals(0.0, neutralized.state.buttons[3].value)
        assertEquals(false, neutralized.state.buttons[3].pressed)
        // The same hostile reading frame after frame canonicalizes to the state already published:
        harness.tick()
        harness.tick()
        assertEquals(2, events.size, "never a per-frame stream of identical canonical states")

        // An over-window transition that canonicalizes identically (1.5 and 2.0 both read 1.0):
        harness.dom.pads = listOf(FakeDomGamepad(index = 0, buttonValues = List(17) { if (it == 3) 1.5 else 0.0 }))
        harness.tick()
        assertEquals(3, events.size, "1.5 is a real model change away from the neutral state")
        harness.dom.pads = listOf(FakeDomGamepad(index = 0, buttonValues = List(17) { if (it == 3) 2.0 else 0.0 }))
        harness.tick()
        assertEquals(3, events.size, "2.0 canonicalizes to the state 1.5 already published")

        // And a signed-zero flip on an axis canonicalizes to the zero already published:
        harness.dom.pads = listOf(
            FakeDomGamepad(
                index = 0,
                buttonValues = List(17) { if (it == 3) 2.0 else 0.0 },
                axisValues = listOf(-0.0, 0.0, 0.0, 0.0),
            ),
        )
        harness.tick()
        assertEquals(3, events.size, "-0.0 canonicalizes to the +0.0 already published")
    }

    @Test
    fun `teardown is idempotent and frees the dom seam`() {
        val harness = Harness()
        val port = harness.hub.openPort()

        port.close()
        port.close()
        assertEquals(2, harness.dom.registrationCloses, "the listeners are withdrawn exactly once")
        assertEquals(1, harness.frames.cancels)
        assertEquals(0, harness.dom.listenerCount())

        val second = harness.hub.openPort()
        harness.hub.close()
        harness.hub.close()
        assertEquals(1, harness.dom.closeCalls, "the dom seam is closed exactly once")
        assertEquals(0, harness.dom.listenerCount(), "hub teardown withdraws the open port's listeners too")
        assertEquals(0, harness.frames.pendingCount())
        assertTrue(harness.hub.gamepads(second).isEmpty(), "a port closed by the hub projects nothing")

        // Everything the runtime could still call after the hub is gone is a quiet no-op.
        second.close()
        assertEquals(4, harness.dom.registrationCloses)
        assertEquals(1, harness.dom.closeCalls)
        assertFailsWith<IllegalStateException> { harness.hub.openPort() }
    }
}
