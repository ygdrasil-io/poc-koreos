package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.KeyCode
import org.graphiks.kadre.core.KeyState
import org.graphiks.kadre.core.KeyboardModifiers
import org.graphiks.kadre.core.PhysicalKey
import org.graphiks.kadre.core.WindowEvent
import kotlin.test.Test
import kotlin.test.assertEquals
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
