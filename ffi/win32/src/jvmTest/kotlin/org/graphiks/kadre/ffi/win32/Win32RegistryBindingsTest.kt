package org.graphiks.kadre.ffi.win32

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class Win32RegistryBindingsTest {

    @Test
    fun `RegGetValueW descriptor exactly matches Win64 ABI`() {
        assertEquals(
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
            regGetValueWDescriptor,
        )
    }

    @Test
    fun `registry constants preserve their native values`() {
        assertEquals(-2147483647L, HKEY_CURRENT_USER.address())
        assertEquals(0x10, RRF_RT_REG_DWORD)
        assertEquals(0, ERROR_SUCCESS)
    }

    @Test
    fun `RegGetValueW exposes a lower camel Kotlin wrapper`() {
        val wrapper: (
            MemorySegment,
            MemorySegment,
            MemorySegment,
            Int,
            MemorySegment,
            MemorySegment,
            MemorySegment,
        ) -> Int = ::regGetValueW

        assertEquals("regGetValueW", ::regGetValueW.name)
        assertEquals(wrapper, ::regGetValueW)
    }
}
