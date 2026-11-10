package org.graphiks.kadre.samples.desktour

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.graphiks.kadre.policy.KadrePolicies

class DesktopHostConfigurationTest {
    @Test
    fun `the first runnable host is a Kadre-owned standalone session with the default ownership options`() {
        val options = deskTourRunOptions()

        assertEquals(KadrePolicies.Default, options.policy)
        assertNull(options.firstWindow)
    }
}
