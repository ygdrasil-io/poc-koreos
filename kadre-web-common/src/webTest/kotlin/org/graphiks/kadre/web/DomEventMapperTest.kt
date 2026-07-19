/**
 * Tests for the pure functions of DomEventMapper.
 *
 * These tests live in webTest (intermediate source set): they run
 * both on the js target and on wasmJs. No DOM dependency is required
 * since the tested functions are 100% pure Kotlin.
 */
package org.graphiks.kadre.web

import org.graphiks.kadre.core.ButtonSource
import org.graphiks.kadre.core.DeviceId
import org.graphiks.kadre.core.FingerId
import org.graphiks.kadre.core.KeyState
import org.graphiks.kadre.core.MouseButton
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PointerKind
import org.graphiks.kadre.core.PointerSource
import org.graphiks.kadre.core.TabletToolButton
import org.graphiks.kadre.core.TabletToolKind
import org.graphiks.kadre.core.TouchPhase
import org.graphiks.kadre.core.WindowEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomEventMapperTest {

    // -----------------------------------------------------------------------
    // domCodeToKey — letters
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey maps letters`() {
        assertEquals(WebKey.A, domCodeToKey("KeyA"))
        assertEquals(WebKey.Z, domCodeToKey("KeyZ"))
        assertEquals(WebKey.M, domCodeToKey("KeyM"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — digits
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey maps digits`() {
        assertEquals(WebKey.Digit0, domCodeToKey("Digit0"))
        assertEquals(WebKey.Digit9, domCodeToKey("Digit9"))
        assertEquals(WebKey.Digit5, domCodeToKey("Digit5"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — function keys
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey maps function keys`() {
        assertEquals(WebKey.F1,  domCodeToKey("F1"))
        assertEquals(WebKey.F12, domCodeToKey("F12"))
        assertEquals(WebKey.F6,  domCodeToKey("F6"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — special keys
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey maps special keys`() {
        assertEquals(WebKey.Space,     domCodeToKey("Space"))
        assertEquals(WebKey.Enter,     domCodeToKey("Enter"))
        assertEquals(WebKey.Escape,    domCodeToKey("Escape"))
        assertEquals(WebKey.Backspace, domCodeToKey("Backspace"))
        assertEquals(WebKey.Tab,       domCodeToKey("Tab"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — navigation
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey maps navigation keys`() {
        assertEquals(WebKey.ArrowUp,    domCodeToKey("ArrowUp"))
        assertEquals(WebKey.ArrowDown,  domCodeToKey("ArrowDown"))
        assertEquals(WebKey.ArrowLeft,  domCodeToKey("ArrowLeft"))
        assertEquals(WebKey.ArrowRight, domCodeToKey("ArrowRight"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — modifiers
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey maps modifiers`() {
        assertEquals(WebKey.ShiftLeft,    domCodeToKey("ShiftLeft"))
        assertEquals(WebKey.ShiftRight,   domCodeToKey("ShiftRight"))
        assertEquals(WebKey.ControlLeft,  domCodeToKey("ControlLeft"))
        assertEquals(WebKey.ControlRight, domCodeToKey("ControlRight"))
        assertEquals(WebKey.AltLeft,      domCodeToKey("AltLeft"))
        assertEquals(WebKey.AltRight,     domCodeToKey("AltRight"))
        assertEquals(WebKey.MetaLeft,     domCodeToKey("MetaLeft"))
        assertEquals(WebKey.MetaRight,    domCodeToKey("MetaRight"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — unknown
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey returns Unknown for an unrecognized code`() {
        assertEquals(WebKey.Unknown, domCodeToKey(""))
        assertEquals(WebKey.Unknown, domCodeToKey("NumpadAdd"))
        assertEquals(WebKey.Unknown, domCodeToKey("BrowserBack"))
    }

    // -----------------------------------------------------------------------
    // domModifiers
    // -----------------------------------------------------------------------

    @Test
    fun `domModifiers returns NONE when all flags are false`() {
        val mods = domModifiers(shiftKey = false, ctrlKey = false, altKey = false, metaKey = false)
        assertEquals(WebModifiers.NONE, mods)
    }

    @Test
    fun `domModifiers returns SHIFT when shiftKey is true`() {
        val mods = domModifiers(shiftKey = true, ctrlKey = false, altKey = false, metaKey = false)
        assertTrue(mods.shift)
        assertFalse(mods.ctrl)
        assertFalse(mods.alt)
        assertFalse(mods.meta)
    }

    @Test
    fun `domModifiers returns CTRL when ctrlKey is true`() {
        val mods = domModifiers(shiftKey = false, ctrlKey = true, altKey = false, metaKey = false)
        assertFalse(mods.shift)
        assertTrue(mods.ctrl)
    }

    @Test
    fun `domModifiers returns ALT when altKey is true`() {
        val mods = domModifiers(shiftKey = false, ctrlKey = false, altKey = true, metaKey = false)
        assertTrue(mods.alt)
    }

    @Test
    fun `domModifiers returns META when metaKey is true`() {
        val mods = domModifiers(shiftKey = false, ctrlKey = false, altKey = false, metaKey = true)
        assertTrue(mods.meta)
    }

    @Test
    fun `domModifiers combines multiple modifiers correctly`() {
        val mods = domModifiers(shiftKey = true, ctrlKey = true, altKey = false, metaKey = false)
        assertTrue(mods.shift)
        assertTrue(mods.ctrl)
        assertFalse(mods.alt)
        assertFalse(mods.meta)
        assertTrue(mods.contains(WebModifiers.SHIFT + WebModifiers.CTRL))
    }

    @Test
    fun `domModifiers all modifiers active`() {
        val mods = domModifiers(shiftKey = true, ctrlKey = true, altKey = true, metaKey = true)
        assertTrue(mods.shift)
        assertTrue(mods.ctrl)
        assertTrue(mods.alt)
        assertTrue(mods.meta)
    }

    // -----------------------------------------------------------------------
    // domButtonToMouseButton
    // -----------------------------------------------------------------------

    @Test
    fun `domButtonToMouseButton maps the left button`() {
        assertEquals(WebMouseButton.Left, domButtonToMouseButton(0))
    }

    @Test
    fun `domButtonToMouseButton maps the middle button`() {
        assertEquals(WebMouseButton.Middle, domButtonToMouseButton(1))
    }

    @Test
    fun `domButtonToMouseButton maps the right button`() {
        assertEquals(WebMouseButton.Right, domButtonToMouseButton(2))
    }

    @Test
    fun `domButtonToMouseButton maps extra buttons to Other`() {
        assertEquals(WebMouseButton.Other(3), domButtonToMouseButton(3))
        assertEquals(WebMouseButton.Other(4), domButtonToMouseButton(4))
        assertEquals(WebMouseButton.Other(10), domButtonToMouseButton(10))
    }

    @Test
    fun `DOM pointer button source preserves mouse touch and pen identity`() {
        val tracker = WebPointerTracker()
        assertEquals(
            ButtonSource.Mouse(MouseButton.Right),
            domPointerButtonSource(2, tracker.onStart(5L, "mouse", domPrimary = false)),
        )
        assertEquals(
            ButtonSource.Touch(FingerId(42L)),
            domPointerButtonSource(0, tracker.onStart(42L, "touch", domPrimary = false)),
        )
        assertEquals(
            ButtonSource.TabletTool(TabletToolKind.Pen, TabletToolButton.Barrel),
            domPointerButtonSource(2, tracker.onStart(91L, "pen", domPrimary = false)),
        )
    }

    @Test
    fun `DOM pen button one remains unknown without a documented convention`() {
        val pen = WebPointerTracker().onStart(91L, "pen", domPrimary = true)

        assertEquals(
            ButtonSource.TabletTool(TabletToolKind.Pen, TabletToolButton.Unknown),
            domPointerButtonSource(1, pen),
        )
    }

    // -----------------------------------------------------------------------
    // domKeyStateFromEventType
    // -----------------------------------------------------------------------

    @Test
    fun `domKeyStateFromEventType returns Pressed for keydown`() {
        assertEquals(WebKeyState.Pressed, domKeyStateFromEventType("keydown"))
    }

    @Test
    fun `domKeyStateFromEventType returns Released for keyup`() {
        assertEquals(WebKeyState.Released, domKeyStateFromEventType("keyup"))
    }

    @Test
    fun `domKeyStateFromEventType returns Pressed for pointerdown`() {
        assertEquals(WebKeyState.Pressed, domKeyStateFromEventType("pointerdown"))
    }

    @Test
    fun `domKeyStateFromEventType returns Released for pointerup`() {
        assertEquals(WebKeyState.Released, domKeyStateFromEventType("pointerup"))
    }

    @Test
    fun `domKeyStateFromEventType returns Released for any other value`() {
        assertEquals(WebKeyState.Released, domKeyStateFromEventType(""))
        assertEquals(WebKeyState.Released, domKeyStateFromEventType("click"))
    }

    // -----------------------------------------------------------------------
    // domTouchTypeToPhase
    // -----------------------------------------------------------------------

    @Test
    fun `domTouchTypeToPhase maps DOM types`() {
        assertEquals(WebTouchPhase.Started,   domTouchTypeToPhase("touchstart"))
        assertEquals(WebTouchPhase.Moved,     domTouchTypeToPhase("touchmove"))
        assertEquals(WebTouchPhase.Ended,     domTouchTypeToPhase("touchend"))
        assertEquals(WebTouchPhase.Cancelled, domTouchTypeToPhase("touchcancel"))
    }

    @Test
    fun `domTouchTypeToPhase returns Cancelled for an unknown type`() {
        assertEquals(WebTouchPhase.Cancelled, domTouchTypeToPhase(""))
        assertEquals(WebTouchPhase.Cancelled, domTouchTypeToPhase("click"))
    }

    // -----------------------------------------------------------------------
    // normalizeWheelDelta
    // -----------------------------------------------------------------------

    @Test
    fun `normalizeWheelDelta does not modify the delta in pixel mode (0)`() {
        assertEquals(42.0, normalizeWheelDelta(42.0, deltaMode = 0))
        assertEquals(-10.5, normalizeWheelDelta(-10.5, deltaMode = 0))
    }

    @Test
    fun `normalizeWheelDelta multiplies by 16 in line mode (1)`() {
        assertEquals(48.0, normalizeWheelDelta(3.0, deltaMode = 1))
        assertEquals(-16.0, normalizeWheelDelta(-1.0, deltaMode = 1))
    }

    @Test
    fun `normalizeWheelDelta multiplies by 600 in page mode (2)`() {
        assertEquals(600.0, normalizeWheelDelta(1.0, deltaMode = 2))
        assertEquals(-1200.0, normalizeWheelDelta(-2.0, deltaMode = 2))
    }

    @Test
    fun `normalizeWheelDelta treats an unknown mode as pixel`() {
        assertEquals(5.0, normalizeWheelDelta(5.0, deltaMode = 99))
    }

    @Test
    fun `normalizeWheelDelta handles a zero delta`() {
        assertEquals(0.0, normalizeWheelDelta(0.0, deltaMode = 0))
        assertEquals(0.0, normalizeWheelDelta(0.0, deltaMode = 1))
        assertEquals(0.0, normalizeWheelDelta(0.0, deltaMode = 2))
    }

    // -----------------------------------------------------------------------
    // WebWindowEvent → WindowEvent pointer contracts
    // -----------------------------------------------------------------------

    @Test
    fun `WebWindowEvent KeyInput structural equality`() {
        val keyEvent = domKeyEvent(
            code = "KeyA",
            key = "a",
            eventType = "keydown",
            shiftKey = false,
            ctrlKey = false,
            altKey = false,
            metaKey = false,
            repeat = false,
        )
        val e1 = WebWindowEvent.KeyInput(keyEvent)
        val e2 = WebWindowEvent.KeyInput(keyEvent)
        assertEquals(e1, e2)
    }

    @Test
    fun `mouse movement preserves physical position identity primary and source`() {
        assertEquals(
            WindowEvent.PointerMoved(
                deviceId = DeviceId(5L),
                position = PhysicalPosition(10.25, 20.5),
                primary = true,
                source = PointerSource.Mouse,
            ),
            WebWindowEvent.PointerMoved(
                x = 10.25,
                y = 20.5,
                pointerId = 5L,
                primary = true,
                source = PointerSource.Mouse,
            ).toWindowEvent(),
        )
    }

    @Test
    fun `touch ID 42 movement can be primary without numeric ID inference`() {
        assertEquals(
            WindowEvent.PointerMoved(
                deviceId = DeviceId(42L),
                position = PhysicalPosition(12.0, 34.0),
                primary = true,
                source = PointerSource.Touch(FingerId(42L)),
            ),
            WebWindowEvent.PointerMoved(
                x = 12.0,
                y = 34.0,
                pointerId = 42L,
                primary = true,
                source = PointerSource.Touch(FingerId(42L)),
            ).toWindowEvent(),
        )
    }

    @Test
    fun `second touch movement remains non-primary`() {
        assertEquals(
            WindowEvent.PointerMoved(
                deviceId = DeviceId(7L),
                position = PhysicalPosition(-2.0, 8.5),
                primary = false,
                source = PointerSource.Touch(FingerId(7L)),
            ),
            WebWindowEvent.PointerMoved(
                x = -2.0,
                y = 8.5,
                pointerId = 7L,
                primary = false,
                source = PointerSource.Touch(FingerId(7L)),
            ).toWindowEvent(),
        )
    }

    @Test
    fun `pointer enter and leave preserve exact mouse fields`() {
        assertEquals(
            WindowEvent.PointerEntered(
                deviceId = DeviceId(3L),
                position = PhysicalPosition(1.5, 2.5),
                primary = true,
                kind = PointerKind.Mouse,
            ),
            WebWindowEvent.PointerEntered(
                x = 1.5,
                y = 2.5,
                pointerId = 3L,
                primary = true,
                kind = PointerKind.Mouse,
            ).toWindowEvent(),
        )
        assertEquals(
            WindowEvent.PointerLeft(
                deviceId = DeviceId(3L),
                position = PhysicalPosition(-1.0, 4.0),
                primary = true,
                kind = PointerKind.Mouse,
            ),
            WebWindowEvent.PointerLeft(
                x = -1.0,
                y = 4.0,
                pointerId = 3L,
                primary = true,
                kind = PointerKind.Mouse,
            ).toWindowEvent(),
        )
    }

    @Test
    fun `tablet move enter and leave preserve source kind identity and position`() {
        val source = PointerSource.TabletTool(TabletToolKind.Pen)
        assertEquals(
            WindowEvent.PointerMoved(DeviceId(91L), PhysicalPosition(6.25, 7.75), false, source),
            WebWindowEvent.PointerMoved(6.25, 7.75, 91L, false, source).toWindowEvent(),
        )
        assertEquals(
            WindowEvent.PointerEntered(DeviceId(91L), PhysicalPosition(6.0, 7.0), false, PointerKind.TabletTool),
            WebWindowEvent.PointerEntered(6.0, 7.0, 91L, false, PointerKind.TabletTool).toWindowEvent(),
        )
        assertEquals(
            WindowEvent.PointerLeft(DeviceId(91L), PhysicalPosition(8.0, 9.0), false, PointerKind.TabletTool),
            WebWindowEvent.PointerLeft(8.0, 9.0, 91L, false, PointerKind.TabletTool).toWindowEvent(),
        )
    }

    @Test
    fun `mouse and tablet buttons preserve actual position identity primary source and state`() {
        assertEquals(
            WindowEvent.PointerButton(
                deviceId = DeviceId(5L),
                state = KeyState.Pressed,
                position = PhysicalPosition(30.5, 40.25),
                primary = true,
                button = ButtonSource.Mouse(MouseButton.Right),
            ),
            WebWindowEvent.PointerButton(
                x = 30.5,
                y = 40.25,
                pointerId = 5L,
                primary = true,
                button = ButtonSource.Mouse(MouseButton.Right),
                state = WebKeyState.Pressed,
            ).toWindowEvent(),
        )
        val barrel = ButtonSource.TabletTool(TabletToolKind.Pen, TabletToolButton.Barrel)
        assertEquals(
            WindowEvent.PointerButton(
                deviceId = DeviceId(91L),
                state = KeyState.Released,
                position = PhysicalPosition(3.75, 4.5),
                primary = false,
                button = barrel,
            ),
            WebWindowEvent.PointerButton(3.75, 4.5, 91L, false, barrel, WebKeyState.Released).toWindowEvent(),
        )
    }

    @Test
    fun `wheel deltas pass through unchanged`() {
        assertEquals(
            WindowEvent.MouseWheel(deviceId = null, deltaX = 1.25, deltaY = -2.5, phase = TouchPhase.Moved),
            WebWindowEvent.MouseWheel(deltaX = 1.25, deltaY = -2.5).toWindowEvent(),
        )
    }

    @Test
    fun `WebWindowEvent Resized structural equality`() {
        assertEquals(
            WebWindowEvent.Resized(width = 800, height = 600),
            WebWindowEvent.Resized(width = 800, height = 600),
        )
    }

    @Test
    fun `legacy touch mapping uses explicit primary for non-zero ID`() {
        assertEquals(
            WindowEvent.PointerButton(
                deviceId = DeviceId(42L),
                state = KeyState.Pressed,
                position = PhysicalPosition(12.0, 34.0),
                primary = true,
                button = ButtonSource.Touch(FingerId(42L)),
            ),
            WebWindowEvent.Touch(
                phase = WebTouchPhase.Started,
                x = 12.0,
                y = 34.0,
                id = 42L,
                primary = true,
            ).toWindowEvent(),
        )
        assertEquals(
            WindowEvent.PointerMoved(
                deviceId = DeviceId(7L),
                position = PhysicalPosition(50.0, 60.0),
                primary = false,
                source = PointerSource.Touch(FingerId(7L)),
            ),
            WebWindowEvent.Touch(WebTouchPhase.Moved, 50.0, 60.0, 7L, primary = false).toWindowEvent(),
        )
    }

    @Test
    fun `touch enter leave and button canonical mappings preserve identity`() {
        assertEquals(
            WindowEvent.PointerEntered(DeviceId(42L), PhysicalPosition(4.0, 5.0), true, PointerKind.Touch),
            WebWindowEvent.PointerEntered(4.0, 5.0, 42L, true, PointerKind.Touch).toWindowEvent(),
        )
        assertEquals(
            WindowEvent.PointerLeft(DeviceId(7L), PhysicalPosition(6.0, 7.0), false, PointerKind.Touch),
            WebWindowEvent.PointerLeft(6.0, 7.0, 7L, false, PointerKind.Touch).toWindowEvent(),
        )
        assertEquals(
            WindowEvent.PointerButton(
                DeviceId(42L),
                KeyState.Released,
                PhysicalPosition(8.0, 9.0),
                true,
                ButtonSource.Touch(FingerId(42L)),
            ),
            WebWindowEvent.PointerButton(
                8.0,
                9.0,
                42L,
                true,
                ButtonSource.Touch(FingerId(42L)),
                WebKeyState.Released,
            ).toWindowEvent(),
        )
    }

    @Test
    fun `drag positions and files pass through unchanged`() {
        assertEquals(
            WindowEvent.DragEntered(PhysicalPosition(100.25, 200.5), listOf("image/png")),
            WebWindowEvent.DragEntered(100.25, 200.5, listOf("image/png")).toWindowEvent(),
        )
        assertEquals(
            WindowEvent.DragMoved(PhysicalPosition(-3.5, 4.25)),
            WebWindowEvent.DragMoved(-3.5, 4.25).toWindowEvent(),
        )
        assertEquals(
            WindowEvent.DragDropped(PhysicalPosition(9.75, 10.5), listOf("file.txt")),
            WebWindowEvent.DragDropped(9.75, 10.5, listOf("file.txt")).toWindowEvent(),
        )
    }

    @Test
    fun `WebWindowEvent ScaleFactorChanged structural equality`() {
        assertEquals(
            WebWindowEvent.ScaleFactorChanged(factor = 2.0),
            WebWindowEvent.ScaleFactorChanged(factor = 2.0),
        )
    }

    @Test
    fun `WebWindowEvent CloseRequested and Destroyed are singletons`() {
        assertEquals(WebWindowEvent.CloseRequested, WebWindowEvent.CloseRequested)
        assertEquals(WebWindowEvent.Destroyed, WebWindowEvent.Destroyed)
    }
}
