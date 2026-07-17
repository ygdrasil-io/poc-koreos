package org.graphiks.kadre.win32

import org.graphiks.kadre.ffi.win32.PaintStructLayout
import org.graphiks.kadre.ffi.win32.allocatePaintStruct
import org.graphiks.kadre.ffi.win32.beginPaint
import org.graphiks.kadre.ffi.win32.endPaint
import org.graphiks.kadre.ffi.win32.invalidateRect
import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals

class Win32PaintBindingsTest {

    @Test
    fun `PAINTSTRUCT layout matches Win64 ABI`() {
        assertEquals(72, PaintStructLayout.SIZEOF)
        assertEquals(8, PaintStructLayout.ALIGN)
        assertEquals(72L, PaintStructLayout.LAYOUT.byteSize())
        assertEquals(8L, PaintStructLayout.LAYOUT.byteAlignment())
        assertEquals(0L, offsetOf("hdc"))
        assertEquals(8L, offsetOf("fErase"))
        assertEquals(12L, offsetOf("rcPaint_left"))
        assertEquals(16L, offsetOf("rcPaint_top"))
        assertEquals(20L, offsetOf("rcPaint_right"))
        assertEquals(24L, offsetOf("rcPaint_bottom"))
        assertEquals(28L, offsetOf("fRestore"))
        assertEquals(32L, offsetOf("fIncUpdate"))
        assertEquals(36L, offsetOf("rgbReserved"))

        Arena.ofConfined().use { arena ->
            val paintStruct = arena.allocatePaintStruct()
            assertEquals(72L, paintStruct.byteSize())
        }
    }

    @Test
    fun `paint wrappers expose Win32 ABI signatures`() {
        val invalidate: (MemorySegment, MemorySegment, Int) -> Int = ::invalidateRect
        val begin: (MemorySegment, MemorySegment) -> MemorySegment = ::beginPaint
        val end: (MemorySegment, MemorySegment) -> Int = ::endPaint

        @Suppress("UNUSED_VARIABLE")
        val signatures = listOf(invalidate, begin, end)
    }

    private fun offsetOf(fieldName: String): Long =
        PaintStructLayout.LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement(fieldName))
}
