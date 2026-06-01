package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.KeyCode
import org.graphiks.kadre.core.KeyState
import org.graphiks.kadre.core.KeyboardModifiers
import org.graphiks.kadre.core.PhysicalKey
import org.graphiks.kadre.core.TouchPhase
import org.graphiks.kadre.core.WindowEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaylandMapperTest {
    @Test
    fun `linux keycodes map to physical KeyCode`() {
        assertEquals(KeyCode.KeyA, linuxKeycodeToKeyCode(30))
        assertEquals(KeyCode.KeyZ, linuxKeycodeToKeyCode(44))
        assertEquals(KeyCode.KeyQ, linuxKeycodeToKeyCode(16))
        assertEquals(KeyCode.KeyM, linuxKeycodeToKeyCode(50))
        assertEquals(KeyCode.Digit1, linuxKeycodeToKeyCode(2))
        assertEquals(KeyCode.Digit0, linuxKeycodeToKeyCode(11))
        assertEquals(KeyCode.Enter, linuxKeycodeToKeyCode(28))
        assertEquals(KeyCode.Escape, linuxKeycodeToKeyCode(1))
        assertEquals(KeyCode.ArrowUp, linuxKeycodeToKeyCode(103))
        assertEquals(KeyCode.ArrowDown, linuxKeycodeToKeyCode(108))
        assertEquals(KeyCode.ShiftLeft, linuxKeycodeToKeyCode(42))
        assertEquals(KeyCode.MetaRight, linuxKeycodeToKeyCode(126))
        assertNull(linuxKeycodeToKeyCode(999))
    }

    @Test
    fun `wayland state maps to key state`() {
        assertEquals(KeyState.Released, waylandKeyStateToKeyState(WL_KEY_RELEASED))
        assertEquals(KeyState.Pressed, waylandKeyStateToKeyState(WL_KEY_PRESSED))
        assertEquals(KeyState.Pressed, waylandKeyStateToKeyState(WL_KEY_REPEATED))
    }

    @Test
    fun `mapWaylandKeyEvent builds KeyInput`() {
        val mods = KeyboardModifiers.Shift + KeyboardModifiers.Ctrl
        val event = assertIs<WindowEvent.KeyInput>(
            mapWaylandKeyEvent(30, WL_KEY_REPEATED, mods),
        ).event

        assertEquals(PhysicalKey.Code(KeyCode.KeyA), event.physicalKey)
        assertEquals(KeyState.Pressed, event.state)
        assertTrue(event.repeat)
        assertEquals(mods, event.modifiers)
    }

    @Test
    fun `unknown keycode preserves native physical key`() {
        val event = mapWaylandKeyEvent(999, WL_KEY_PRESSED).event
        assertEquals(PhysicalKey.Native(org.graphiks.kadre.core.KeyPlatform.Wayland, 999), event.physicalKey)
    }
}

// ============================================================================
// WaylandTouchMapper tests
// ============================================================================

class WaylandTouchMapperTest {

    // ── mapWaylandTouchDown ───────────────────────────────────────────────────

    @Test
    fun `mapWaylandTouchDown produces Touch with Started phase`() {
        val event = mapWaylandTouchDown(id = 0, xFixed = 256, yFixed = 512)
        assertTrue(event is WindowEvent.Touch)
        assertEquals(TouchPhase.Started, event.phase)
    }

    @Test
    fun `mapWaylandTouchDown converts wl_fixed coordinates correctly`() {
        // x = 10.0 → wl_fixed = 10 * 256 = 2560; y = 20.0 → 5120
        val event = mapWaylandTouchDown(id = 1, xFixed = 2560, yFixed = 5120)
        assertEquals(10.0, event.location.x)
        assertEquals(20.0, event.location.y)
    }

    @Test
    fun `mapWaylandTouchDown preserves touch id`() {
        val event = mapWaylandTouchDown(id = 3, xFixed = 0, yFixed = 0)
        assertEquals(3L, event.id)
    }

    // ── mapWaylandTouchUp ─────────────────────────────────────────────────────

    @Test
    fun `mapWaylandTouchUp produces Touch with Ended phase`() {
        val event = mapWaylandTouchUp(id = 0)
        assertTrue(event is WindowEvent.Touch)
        assertEquals(TouchPhase.Ended, event.phase)
    }

    @Test
    fun `mapWaylandTouchUp preserves touch id`() {
        val event = mapWaylandTouchUp(id = 5)
        assertEquals(5L, event.id)
    }

    // ── mapWaylandTouchMotion ─────────────────────────────────────────────────

    @Test
    fun `mapWaylandTouchMotion produces Touch with Moved phase`() {
        val event = mapWaylandTouchMotion(id = 0, xFixed = 256, yFixed = 256)
        assertTrue(event is WindowEvent.Touch)
        assertEquals(TouchPhase.Moved, event.phase)
    }

    @Test
    fun `mapWaylandTouchMotion converts wl_fixed coordinates correctly`() {
        // x = 5.0 → 1280; y = 7.5 → 1920
        val event = mapWaylandTouchMotion(id = 2, xFixed = 1280, yFixed = 1920)
        assertEquals(5.0, event.location.x)
        assertEquals(7.5, event.location.y)
    }

    // ── mapWaylandTouchCancel ─────────────────────────────────────────────────

    @Test
    fun `mapWaylandTouchCancel produces Touch with Cancelled phase`() {
        val event = mapWaylandTouchCancel(id = 0)
        assertTrue(event is WindowEvent.Touch)
        assertEquals(TouchPhase.Cancelled, event.phase)
    }

    @Test
    fun `mapWaylandTouchCancel preserves touch id`() {
        val event = mapWaylandTouchCancel(id = 7)
        assertEquals(7L, event.id)
    }
}

// ============================================================================
// seatHasCapability tests (WaylandSeat)
// ============================================================================

class WaylandSeatCapabilityTest {

    // Capability bit values as defined by the Wayland protocol:
    //   POINTER  = 1
    //   KEYBOARD = 2
    //   TOUCH    = 4

    @Test
    fun `seatHasCapability returns true when bit is set`() {
        // caps = KEYBOARD | TOUCH = 6
        assertTrue(seatHasCapability(caps = 6, capBit = 2)) // KEYBOARD
        assertTrue(seatHasCapability(caps = 6, capBit = 4)) // TOUCH
    }

    @Test
    fun `seatHasCapability returns false when bit is absent`() {
        // caps = KEYBOARD | TOUCH = 6, no POINTER
        assertFalse(seatHasCapability(caps = 6, capBit = 1)) // POINTER absent
    }

    @Test
    fun `seatHasCapability returns false for zero capabilities`() {
        assertFalse(seatHasCapability(caps = 0, capBit = 1))
        assertFalse(seatHasCapability(caps = 0, capBit = 2))
        assertFalse(seatHasCapability(caps = 0, capBit = 4))
    }

    @Test
    fun `seatHasCapability handles full bitmask`() {
        // All three capabilities present (1 | 2 | 4 = 7)
        assertTrue(seatHasCapability(caps = 7, capBit = 1))
        assertTrue(seatHasCapability(caps = 7, capBit = 2))
        assertTrue(seatHasCapability(caps = 7, capBit = 4))
    }

    @Test
    fun `seatHasCapability pointer-only seat`() {
        // Only pointer available (typical desktop mouse-only setup)
        assertTrue(seatHasCapability(caps = 1, capBit = 1))  // POINTER present
        assertFalse(seatHasCapability(caps = 1, capBit = 2)) // KEYBOARD absent
        assertFalse(seatHasCapability(caps = 1, capBit = 4)) // TOUCH absent
    }
}
