package org.graphiks.kadre.samples.desktour

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kadre.platform.desktop.DesktopBackend

class DesktopHostConfigurationTest {
    @Test
    fun `the first runnable host is a Kadre-owned standalone AppKit session`() {
        val options = deskTourHostOptions()

        assertEquals(DesktopBackend.AppKit, options.backend)
        assertTrue(options.stopWhenLastWindowClosed)
    }
}
