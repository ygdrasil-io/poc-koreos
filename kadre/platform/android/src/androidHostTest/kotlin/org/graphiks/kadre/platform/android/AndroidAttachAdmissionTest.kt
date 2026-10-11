package org.graphiks.kadre.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind

class AndroidAttachAdmissionTest {

    private fun request(
        form: AndroidAttachForm = AndroidAttachForm.View,
        isMainThread: Boolean = true,
        isViewAttached: Boolean = true,
        ownerState: Int = 4, // RESUMED
        jobPresent: Boolean = true,
        jobActive: Boolean = true,
        decorMatches: Boolean = true,
    ) = AndroidAttachRequest(
        form = form,
        isMainThread = isMainThread,
        view = "view-1",
        isViewAttachedToWindow = isViewAttached,
        viewRootIdentity = if (form == AndroidAttachForm.Activity) "decor-1" else "view-root",
        activityIdentity = if (form == AndroidAttachForm.Activity) "activity-1" else null,
        decorRootIdentity = if (form == AndroidAttachForm.Activity) (if (decorMatches) "decor-1" else "decor-other") else null,
        ownerState = ownerState,
        parentScopeJob = if (jobPresent) Any() else null,
        parentScopeJobIsActive = jobActive,
    )

    @Test
    fun happyPathReturnsNoDenial() {
        assertNull(evaluateAndroidAttach(request(), AndroidViewOwnership()))
    }

    @Test
    fun scopeLadderExactFieldsAndReasons() {
        assertEquals(
            KadreFailure.InvalidRequest("parentScope"),
            evaluateAndroidAttach(request(jobPresent = false), AndroidViewOwnership())!!.failure,
        )
        assertEquals(
            KadreFailure.ParentScopeCancelled,
            evaluateAndroidAttach(request(jobActive = false), AndroidViewOwnership())!!.failure,
        )
    }

    @Test
    fun viewFieldFollowsTheOverload() {
        assertEquals(
            KadreFailure.InvalidRequest("view"),
            evaluateAndroidAttach(request(isViewAttached = false), AndroidViewOwnership())!!.failure,
        )
        assertEquals(
            KadreFailure.InvalidRequest("surfaceView"),
            evaluateAndroidAttach(
                request(form = AndroidAttachForm.Activity, isViewAttached = false),
                AndroidViewOwnership(),
            )!!.failure,
        )
    }

    @Test
    fun decorMismatchIsInvalidSurfaceView() {
        assertEquals(
            KadreFailure.InvalidRequest("surfaceView"),
            evaluateAndroidAttach(request(form = AndroidAttachForm.Activity, decorMatches = false), AndroidViewOwnership())!!.failure,
        )
    }

    @Test
    fun destroyedLifecycleOwnerIsClosedHost() {
        // Lifecycle.State.DESTROYED ordinal = 0 ; aucun field "lifecycleOwner" au registre §1.1.
        assertEquals(
            KadreFailure.Closed(KadreResourceKind.Host),
            evaluateAndroidAttach(request(ownerState = 0), AndroidViewOwnership())!!.failure,
        )
    }

    @Test
    fun crossFormConflictsReturnBusyWithoutTransfer() {
        val ownership = AndroidViewOwnership()
        assertNull(evaluateAndroidAttach(request(), ownership))
        // Réservation réussie puis seconde demande : identités en conflit, ownership intact.
        assertTrue(ownership.reserve(activityIdentity = null, viewIdentity = "view-1"))
        val busy = evaluateAndroidAttach(request(), ownership)!!.failure
        assertEquals(KadreFailure.AlreadyInUse(KadreResourceKind.Host), busy)
        // La forme Activity entre aussi en conflit sur la même View (identité native partagée).
        assertEquals(
            KadreFailure.AlreadyInUse(KadreResourceKind.Host),
            evaluateAndroidAttach(request(form = AndroidAttachForm.Activity), ownership)!!.failure,
        )
    }
}
