package org.graphiks.kadre.win32

import kotlin.test.Test
import kotlin.test.assertEquals

class Win32NativeIntegrationTest {

    @Test
    fun `GESTUREINFO_SIZE matches Windows struct`() {
        assertEquals(48, GESTUREINFO_SIZE)
    }
}
