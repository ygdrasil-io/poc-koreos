package org.graphiks.kadre.x11

import org.graphiks.kadre.core.KeyCode
import org.graphiks.kadre.core.KeyPlatform
import org.graphiks.kadre.core.KeyState
import org.graphiks.kadre.core.KeyboardModifiers
import org.graphiks.kadre.core.PhysicalKey
import org.graphiks.kadre.core.WindowEvent
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class X11MapperTest {
    @AfterTest
    fun reset() {
        X11KeyMapper.resetState()
        X11LiveRepeatTracker.reset()
    }

    @Test
    fun `keysym table maps common keys to KeyCode`() {
        assertEquals(KeyCode.KeyA, KEYSYM_TABLE[0x61])
        assertEquals(KeyCode.KeyZ, KEYSYM_TABLE[0x5A])
        assertEquals(KeyCode.Digit0, KEYSYM_TABLE[0x30])
        assertEquals(KeyCode.Enter, KEYSYM_TABLE[0xFF0D])
        assertEquals(KeyCode.ArrowLeft, KEYSYM_TABLE[0xFF51])
        assertEquals(KeyCode.F12, KEYSYM_TABLE[0xFFC9])
        assertEquals(KeyCode.MetaRight, KEYSYM_TABLE[0xFFEC])
    }

    @Test
    fun `stateToModifiers decodes modifier mask`() {
        val mods = stateToModifiers(0x01 or 0x04 or 0x08 or 0x40)
        assertEquals(KeyboardModifiers(KeyboardModifiers.SHIFT or KeyboardModifiers.CTRL or KeyboardModifiers.ALT or KeyboardModifiers.META), mods)
    }

    @Test
    fun `live x11StateToModifiers decodes meta modifier mask`() {
        val mods = x11StateToModifiers(0x40)
        assertEquals(KeyboardModifiers.Meta, mods)
    }

    @Test
    fun `fromXEvent maps key press to KeyInput`() {
        val event = assertIs<WindowEvent.KeyInput>(
            X11KeyMapper.fromXEvent(xEvent(state = 0x01, keycode = 38), KeyPress, keysym = 0x61),
        ).event

        assertEquals(PhysicalKey.Code(KeyCode.KeyA), event.physicalKey)
        assertEquals(KeyState.Pressed, event.state)
        assertEquals(KeyboardModifiers.Shift, event.modifiers)
    }

    @Test
    fun `fromXEvent detects repeats`() {
        X11KeyMapper.fromXEvent(xEvent(keycode = 38), KeyPress, keysym = 0x61)
        val repeat = X11KeyMapper.fromXEvent(xEvent(keycode = 38), KeyPress, keysym = 0x61)!!.event
        assertTrue(repeat.repeat)
    }

    @Test
    fun `live repeat tracker marks second press as repeat and resets on release`() {
        assertEquals(false, X11LiveRepeatTracker.update(38, KeyState.Pressed))
        assertEquals(true, X11LiveRepeatTracker.update(38, KeyState.Pressed))
        assertEquals(false, X11LiveRepeatTracker.update(38, KeyState.Released))
        assertEquals(false, X11LiveRepeatTracker.update(38, KeyState.Pressed))
    }

    @Test
    fun `unknown keysym preserves native keycode`() {
        val event = X11KeyMapper.fromXEvent(xEvent(keycode = 255), KeyPress, keysym = 0)!!.event
        assertEquals(PhysicalKey.Native(KeyPlatform.X11, 255), event.physicalKey)
    }
}

private fun xEvent(state: Int = 0, keycode: Int): MemorySegment {
    val segment = MemorySegment.ofArray(LongArray(12))
    segment.set(ValueLayout.JAVA_INT, 64L, state)
    segment.set(ValueLayout.JAVA_INT, 68L, keycode)
    return segment
}
