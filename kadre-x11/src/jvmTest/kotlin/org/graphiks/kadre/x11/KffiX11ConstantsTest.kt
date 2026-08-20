package org.graphiks.kadre.x11

import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kffi.x11.generated.CWOverrideRedirect
import org.graphiks.kffi.x11.generated.KeyPress
import org.graphiks.kffi.x11.generated.KeyPressMask

class KffiX11ConstantsTest {
    @Test
    fun `generated X11 constants keep their protocol values`() {
        if (!System.getProperty("os.name").contains("Linux", ignoreCase = true)) return
        assertEquals(2, KeyPress())
        assertEquals(1L, KeyPressMask())
        assertEquals(1L shl 9, CWOverrideRedirect())
    }
}
