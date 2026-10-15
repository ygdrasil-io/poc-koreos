package org.graphiks.kadre.internal.win32

import kotlin.test.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.surface.LogicalPoint

class Win32InputMappingTest {
    private fun key(scan: Int, vk: Int = 0x41, extended: Boolean = false, text: String? = "a") =
        Win32KeyInput(vk, scan, extended, true, false, 1, KeyboardModifiers(emptySet()), text, false, 1L)

    @Test fun scanCodesArePhysicalAndNeverInferredFromCharacters() {
        assertEquals(PhysicalKey.Code(7, 4), win32PhysicalKey(key(0x1e, text = "q")))
        assertEquals(PhysicalKey.Code(7, 0x64), win32PhysicalKey(key(0x56)))
        assertIs<PhysicalKey.Unidentified>(win32PhysicalKey(key(0xff)))
        assertEquals(LogicalKey.Character("€"), win32LogicalKey(key(0x12, text = "€")))
        assertIs<LogicalKey.Unidentified>(win32LogicalKey(key(0x29).copy(deadKey = true)))
        assertEquals(LogicalKey.Character("\uD83D\uDE00"), win32LogicalKey(key(0x1e, text = "\uD83D\uDE00")))
    }

    @Test fun extendedKeysKeepSideAndNumpadIdentity() {
        assertEquals(PhysicalKey.Code(7, 0x53), win32PhysicalKey(key(0x45, 0x90, true)))
        assertEquals(KeyLocation.Numpad, win32KeyLocation(key(0x45, 0x90, true)))
        assertEquals(PhysicalKey.Code(7, 0xe0), win32PhysicalKey(key(0x1d, 0x11)))
        assertEquals(PhysicalKey.Code(7, 0xe4), win32PhysicalKey(key(0x1d, 0x11, true)))
        assertEquals(KeyLocation.Left, win32KeyLocation(key(0x1d, 0x11)))
        assertEquals(KeyLocation.Right, win32KeyLocation(key(0x1d, 0x11, true)))
        assertEquals(PhysicalKey.Code(7, 0x58), win32PhysicalKey(key(0x1c, 0x0d, true)))
        assertEquals(KeyLocation.Numpad, win32KeyLocation(key(0x1c, 0x0d, true)))
        assertEquals(PhysicalKey.Code(7, 0x5c), win32PhysicalKey(key(0x4b, 0x25)))
        assertEquals(PhysicalKey.Code(7, 0x50), win32PhysicalKey(key(0x4b, 0x25, true)))
        assertEquals(PhysicalKey.Code(7, 0x48), win32PhysicalKey(key(0x45, 0x13)))
        assertEquals(PhysicalKey.Code(7, 0x46), win32PhysicalKey(key(0x37, 0x2c, true)))
    }

    @Test fun coordinatesAreSignedAndUseTheWindowScale() {
        val packed = ((-25 and 0xffff).toLong() shl 16) or (-10 and 0xffff).toLong()
        assertEquals(LogicalPoint(-8.0, -20.0), win32ClientPosition(packed, 1.25))
    }

    @Test fun wheelRetainsFractionsSignsAndSystemTextIncrements() {
        val half = 60L shl 16
        val negative = (-30 and 0xffff).toLong() shl 16
        assertEquals(ScrollDelta.Lines(0.0, -1.5), win32WheelDelta(false, half, Win32WheelSettings(3, 2)))
        assertEquals(ScrollDelta.Lines(-0.5, 0.0), win32WheelDelta(true, negative, Win32WheelSettings(3, 2)))
        assertNull(win32WheelDelta(false, half, Win32WheelSettings(null, 2)))
        assertNull(win32WheelDelta(false, half, Win32WheelSettings(0, 2)))
    }
}
