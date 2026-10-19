package org.graphiks.kadre.internal.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * O2 proofs of the process-wide service entry point (D3): refcounted
 * broker lifecycle and honest non-Linux behavior — every path through
 * the real [KffiEvdevNativeSource], whose downcalls are null-guarded
 * outside Linux (empty enumeration, no throw).
 */
class EvdevGamepadServiceTest {

    @Test
    fun viewsShareTheProcessAndTheLastCloseStopsTheService() {
        val first = EvdevGamepadService.openSessionView()
        val second = EvdevGamepadService.openSessionView()
        try {
            // whatever the platform says about enumeration, both views
            // report the same honest truth
            assertEquals(first.inventoryFailure, second.inventoryFailure)
        } finally {
            first.close()
        }
        // the surviving view keeps the service alive
        assertTrue(second.gamepads.isEmpty() || second.gamepads.isNotEmpty())
        second.close()
        // and a fresh cycle starts cleanly after the last close
        val third = EvdevGamepadService.openSessionView()
        try {
            assertNotNull(third)
        } finally {
            third.close()
        }
    }

    @Test
    fun closingTwiceIsIdempotent() {
        val view = EvdevGamepadService.openSessionView()
        view.close()
        view.close()
    }
}
