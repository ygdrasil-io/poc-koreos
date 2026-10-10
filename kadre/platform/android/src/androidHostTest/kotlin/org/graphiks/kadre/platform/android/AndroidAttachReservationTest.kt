package org.graphiks.kadre.platform.android

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidAttachReservationTest {

    @Test
    fun acquireReservesBothIdentitiesAtomically() {
        val ownership = AndroidViewOwnership()
        val reservation = AndroidAttachReservation.acquire(ownership, "activity-1", "view-1")
        assertTrue(reservation != null)
        assertTrue(ownership.isActivityClaimed("activity-1"))
        assertTrue(ownership.isViewClaimed("view-1"))
        assertTrue(reservation.isStillHeld())
    }

    @Test
    fun rollbackAfterLateFailureRestoresReservation() {
        val ownership = AndroidViewOwnership()
        val reservation = AndroidAttachReservation.acquire(ownership, "activity-1", "view-1")!!
        // Échec tardif (controller.attach refuse) : l'état d'avant la demande est restauré.
        reservation.rollback()
        assertFalse(ownership.isActivityClaimed("activity-1"))
        assertFalse(ownership.isViewClaimed("view-1"))
        assertFalse(reservation.isStillHeld())
        // Un re-attach valide réussit immédiatement.
        assertTrue(AndroidAttachReservation.acquire(ownership, "activity-1", "view-1") != null)
    }

    @Test
    fun releaseHappensOnlyAtEffectiveTerminationAndIsIdempotent() {
        val ownership = AndroidViewOwnership()
        val reservation = AndroidAttachReservation.acquire(ownership, null, "view-1")!!
        reservation.release()
        assertFalse(ownership.isViewClaimed("view-1"))
        reservation.release() // idempotent
        assertFalse(reservation.isStillHeld())
    }
}
