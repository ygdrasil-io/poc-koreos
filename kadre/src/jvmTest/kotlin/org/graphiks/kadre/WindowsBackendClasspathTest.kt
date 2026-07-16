package org.graphiks.kadre

import kotlin.test.Test
import kotlin.test.assertNotNull

class WindowsBackendClasspathTest {

    @Test
    fun `win32 backend is available on the runtime classpath`() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return

        val backendClass = Class.forName("org.graphiks.kadre.win32.Win32EventLoopKt")

        assertNotNull(backendClass)
    }
}
