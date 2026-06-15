package org.graphiks.kadre.win32

import org.graphiks.kadre.ffi.win32.GESTUREINFO_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals

class Win32NativeIntegrationTest {

    @Test
    fun `GESTUREINFO_SIZE matches Windows struct`() {
        assertEquals(48, GESTUREINFO_SIZE)
    }
}
