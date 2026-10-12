package org.graphiks.kadre.contracts.driver.win32

import kotlin.test.Test
import kotlin.test.assertTrue

class Win32MessageInjectorOffWindowsTest {
    @Test
    fun unsupportedPlatformFailsActionably() {
        if (Win32MessageInjector.isSupported()) return
        val failure = runCatching { Win32MessageInjector() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "got $failure")
        assertTrue(failure?.message?.contains("Windows") == true)
    }
}
