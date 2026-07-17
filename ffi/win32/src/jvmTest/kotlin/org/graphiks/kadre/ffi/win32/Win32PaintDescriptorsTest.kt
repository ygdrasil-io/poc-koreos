package org.graphiks.kadre.ffi.win32

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class Win32PaintDescriptorsTest {

    @Test
    fun `paint descriptors exactly match Win64 ABI`() {
        assertEquals(
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
            invalidateRectDescriptor,
        )
        assertEquals(
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
            beginPaintDescriptor,
        )
        assertEquals(
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
            endPaintDescriptor,
        )
    }
}
