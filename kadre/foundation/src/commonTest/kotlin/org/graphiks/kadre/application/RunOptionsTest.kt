package org.graphiks.kadre.application

import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RunOptionsTest {
    @Test
    fun defaultsToDefaultPolicyWithoutFirstWindow() {
        val options = KadreRunOptions()

        assertEquals(KadrePolicies.Default, options.policy)
        assertEquals(null, options.firstWindow)
    }

    @Test
    fun copyWithFirstWindowConstructs() {
        val options = KadreRunOptions().copy(
            firstWindow = WindowSpec(contentSize = LogicalSize(640.0, 480.0)),
        )

        val firstWindow = assertNotNull(options.firstWindow)
        assertEquals(LogicalSize(640.0, 480.0), firstWindow.contentSize)
        assertEquals(KadrePolicies.Default, options.policy)
    }
}
