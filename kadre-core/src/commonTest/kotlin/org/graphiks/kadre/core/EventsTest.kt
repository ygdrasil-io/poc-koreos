/**
 * Unit tests for the kadre-core event model.
 */
package org.graphiks.kadre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventsTest {

    // -----------------------------------------------------------------------
    // KeyboardModifiers - bit logic
    // -----------------------------------------------------------------------

    @Test
    fun `SHIFT plus CTRL contains SHIFT`() {
        val mods = KeyboardModifiers.Shift + KeyboardModifiers.Ctrl
        assertTrue(mods.contains(KeyboardModifiers.Shift), "SHIFT+CTRL must contain SHIFT")
    }

    @Test
    fun `SHIFT plus CTRL contains CTRL`() {
        val mods = KeyboardModifiers.Shift + KeyboardModifiers.Ctrl
        assertTrue(mods.contains(KeyboardModifiers.Ctrl), "SHIFT+CTRL must contain CTRL")
    }

    @Test
    fun `SHIFT plus CTRL does not contain ALT`() {
        val mods = KeyboardModifiers.Shift + KeyboardModifiers.Ctrl
        assertFalse(mods.contains(KeyboardModifiers.Alt), "SHIFT+CTRL must not contain ALT")
    }

    @Test
    fun `NONE contains no keyboard modifier`() {
        val mods = KeyboardModifiers.NONE
        assertFalse(mods.shift, "NONE.shift must be false")
        assertFalse(mods.ctrl, "NONE.ctrl must be false")
        assertFalse(mods.alt, "NONE.alt must be false")
        assertFalse(mods.meta, "NONE.meta must be false")
        assertFalse(mods.altGraph, "NONE.altGraph must be false")
        assertFalse(mods.capsLock, "NONE.capsLock must be false")
        assertFalse(mods.numLock, "NONE.numLock must be false")
        assertFalse(mods.symbol, "NONE.symbol must be false")
    }

    @Test
    fun `minus removes a modifier`() {
        val mods = KeyboardModifiers.Shift + KeyboardModifiers.Ctrl - KeyboardModifiers.Shift
        assertFalse(mods.shift)
        assertTrue(mods.ctrl)
    }

    // -----------------------------------------------------------------------
    // KeyCode and NamedKey coverage
    // -----------------------------------------------------------------------

    @Test
    fun `physical key codes include letters digits navigation and F35`() {
        val entries = KeyCode.entries.map { it.name }.toSet()
        assertTrue("KeyA" in entries)
        assertTrue("KeyZ" in entries)
        assertTrue("Digit0" in entries)
        assertTrue("Digit9" in entries)
        assertTrue("ArrowUp" in entries)
        assertTrue("NumpadEnter" in entries)
        assertTrue("F35" in entries)
    }

    @Test
    fun `named keys include text navigation modifiers and media`() {
        val entries = NamedKey.entries.map { it.name }.toSet()
        assertTrue("Enter" in entries)
        assertTrue("ArrowDown" in entries)
        assertTrue("AltGraph" in entries)
        assertTrue("MediaPlayPause" in entries)
        assertTrue("F35" in entries)
    }

    // -----------------------------------------------------------------------
    // KeyEvent - rich keyboard contract
    // -----------------------------------------------------------------------

    @Test
    fun `KeyEvent separates physical and logical key`() {
        val event = KeyEvent(
            physicalKey = PhysicalKey.Code(KeyCode.KeyW),
            logicalKey = LogicalKey.Character("z"),
            state = KeyState.Pressed,
            modifiers = KeyboardModifiers.NONE,
            text = "z",
            keyWithoutModifiers = LogicalKey.Character("z"),
            native = NativeKeyInfo(platform = KeyPlatform.Web, keyCode = "KeyW", keyValue = "z"),
        )

        assertEquals(PhysicalKey.Code(KeyCode.KeyW), event.physicalKey)
        assertEquals(LogicalKey.Character("z"), event.logicalKey)
        assertEquals("z", event.character)
        assertTrue(event.isPressed)
        assertFalse(event.isReleased)
        assertEquals(KeyPlatform.Web, event.native.platform)
    }

    @Test
    fun `LogicalKey Dead is distinct from printable character`() {
        assertFalse(LogicalKey.Dead("^") == LogicalKey.Character("^"))
    }

    @Test
    fun `Native physical key keeps platform code`() {
        val key = PhysicalKey.Native(KeyPlatform.AppKit, 126)
        assertEquals(KeyPlatform.AppKit, key.platform)
        assertEquals(126, key.code)
    }

    @Test
    fun `KeyChord can match physical bindings independent of layout`() {
        val chord = KeyChord(physicalKey = PhysicalKey.Code(KeyCode.KeyW))
        val event = KeyEvent(
            physicalKey = PhysicalKey.Code(KeyCode.KeyW),
            logicalKey = LogicalKey.Character("z"),
            state = KeyState.Pressed,
            modifiers = KeyboardModifiers.NONE,
        )

        assertTrue(chord.matches(event))
    }

    @Test
    fun `KeyChord rejects repeat by default`() {
        val chord = KeyChord(logicalKey = LogicalKey.Named(NamedKey.Enter))
        val event = KeyEvent(
            physicalKey = PhysicalKey.Code(KeyCode.Enter),
            logicalKey = LogicalKey.Named(NamedKey.Enter),
            state = KeyState.Pressed,
            modifiers = KeyboardModifiers.NONE,
            repeat = true,
        )

        assertFalse(chord.matches(event))
    }

    @Test
    fun `KeyChord can match logical shortcuts with modifiers`() {
        val chord = KeyChord(
            logicalKey = LogicalKey.Character("s"),
            modifiers = KeyboardModifiers.Ctrl,
        )
        val event = KeyEvent(
            physicalKey = PhysicalKey.Code(KeyCode.KeyS),
            logicalKey = LogicalKey.Character("s"),
            state = KeyState.Pressed,
            modifiers = KeyboardModifiers.Ctrl + KeyboardModifiers.Shift,
        )

        assertTrue(chord.matches(event))
    }

    // -----------------------------------------------------------------------
    // KeyState
    // -----------------------------------------------------------------------

    @Test
    fun `KeyState has exactly Pressed and Released`() {
        val names = KeyState.entries.map { it.name }.toSet()
        assertEquals(setOf("Pressed", "Released"), names)
    }

    // -----------------------------------------------------------------------
    // TouchPhase
    // -----------------------------------------------------------------------

    @Test
    fun `TouchPhase has exactly the four phases`() {
        val names = TouchPhase.entries.map { it.name }.toSet()
        assertEquals(setOf("Started", "Moved", "Ended", "Cancelled"), names)
    }

    // -----------------------------------------------------------------------
    // MouseButton - equality and structure
    // -----------------------------------------------------------------------

    @Test
    fun `MouseButton Left is a singleton`() {
        assertTrue(MouseButton.Left === MouseButton.Left)
    }

    @Test
    fun `MouseButton Other keeps its index`() {
        val button = MouseButton.Other(5)
        assertEquals(5, button.button)
    }

    @Test
    fun `two MouseButton Other with same indices are equal`() {
        assertEquals(MouseButton.Other(3), MouseButton.Other(3))
    }

    // -----------------------------------------------------------------------
    // WindowEvent - when exhaustiveness (without else) + variant construction
    // -----------------------------------------------------------------------

    private fun classifyWindowEvent(event: WindowEvent): String = when (event) {
        WindowEvent.CloseRequested -> "CloseRequested"
        is WindowEvent.Resized -> "Resized"
        is WindowEvent.Moved -> "Moved"
        is WindowEvent.ScaleFactorChanged -> "ScaleFactorChanged"
        is WindowEvent.Focused -> "Focused"
        is WindowEvent.KeyInput -> "KeyInput"
        is WindowEvent.PointerMoved -> "PointerMoved"
        WindowEvent.PointerEntered -> "PointerEntered"
        WindowEvent.PointerLeft -> "PointerLeft"
        is WindowEvent.MouseInput -> "MouseInput"
        is WindowEvent.MouseWheel -> "MouseWheel"
        is WindowEvent.Touch -> "Touch"
        is WindowEvent.ModifiersChanged -> "ModifiersChanged"
        WindowEvent.RedrawRequested -> "RedrawRequested"
        WindowEvent.Destroyed -> "Destroyed"
    }

    @Test
    fun `WindowEvent KeyInput keeps rich event`() {
        val keyEvent = KeyEvent(
            physicalKey = PhysicalKey.Code(KeyCode.KeyA),
            logicalKey = LogicalKey.Character("a"),
            state = KeyState.Pressed,
            modifiers = KeyboardModifiers.Shift,
            text = "A",
        )
        val event = WindowEvent.KeyInput(keyEvent)
        assertEquals("KeyInput", classifyWindowEvent(event))
        assertEquals(keyEvent, event.event)
    }

    @Test
    fun `WindowEvent ModifiersChanged keeps logical and physical state`() {
        val state = KeyboardModifierState(
            logical = KeyboardModifiers.Shift,
            physical = ModifierKeys(leftShift = ModifierKeyState.Pressed),
        )
        val event = WindowEvent.ModifiersChanged(state)

        assertEquals("ModifiersChanged", classifyWindowEvent(event))
        assertTrue(event.state.logical.shift)
        assertEquals(ModifierKeyState.Pressed, event.state.physical.leftShift)
    }

    @Test
    fun `WindowEvent Resized keeps the size`() {
        val size = PhysicalSize(1920, 1080)
        val event = WindowEvent.Resized(size)
        assertEquals("Resized", classifyWindowEvent(event))
        assertEquals(size, event.size)
    }

    @Test
    fun `WindowEvent PointerMoved keeps the position`() {
        val pos = PhysicalPosition(123.4, 567.8)
        val event = WindowEvent.PointerMoved(pos)
        assertEquals("PointerMoved", classifyWindowEvent(event))
        assertEquals(pos, event.position)
    }

    // -----------------------------------------------------------------------
    // DeviceEvent - when exhaustiveness (without else) + variant construction
    // -----------------------------------------------------------------------

    private fun classifyDeviceEvent(event: DeviceEvent): String = when (event) {
        is DeviceEvent.PointerMotion -> "PointerMotion"
        is DeviceEvent.Button -> "Button"
        is DeviceEvent.Key -> "Key"
    }

    @Test
    fun `DeviceEvent Key keeps the scancode and the state`() {
        val event = DeviceEvent.Key(0x1E, KeyState.Released)
        assertEquals("Key", classifyDeviceEvent(event))
        assertEquals(0x1E, event.scancode)
        assertEquals(KeyState.Released, event.state)
    }
}
