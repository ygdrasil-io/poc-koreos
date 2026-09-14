package org.graphiks.kadre.samples.desktour.appkit

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeMountStateTest {
    @Test
    fun `a mounted renderer closes exactly once`() {
        val lifecycle = ComposeMountLifecycle()

        lifecycle.markMounted()
        lifecycle.beginClose()
        lifecycle.markClosed()
        lifecycle.beginClose()

        assertEquals(ComposeMountState.Closed, lifecycle.state)
    }

    @Test
    fun `a failed mount cannot later report mounted`() {
        val lifecycle = ComposeMountLifecycle()

        lifecycle.markFailed()
        lifecycle.markMounted()

        assertEquals(ComposeMountState.Failed, lifecycle.state)
    }
}
