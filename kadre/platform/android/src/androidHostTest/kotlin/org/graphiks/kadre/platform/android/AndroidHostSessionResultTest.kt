package org.graphiks.kadre.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kadre.diagnostics.KadreFailure

class AndroidHostSessionResultTest {

    @Test
    fun admissionDenialsBecomeKadreResultsWithoutSession() {
        // La couture commune : une denial de l'échelle devient Failure SANS exception ni session.
        val failure = AndroidHostSession.toFailure(AndroidAttachDenial(KadreFailure.InvalidRequest("view")))
        assertEquals(KadreFailure.InvalidRequest("view"), failure)
    }

    @Test
    fun threadViolationRemainsAProgrammingError() {
        // Décision de phase 0 maintenue : la violation de thread n'est PAS un KadreResult.
        assertTrue(runCatching {
            evaluateAndroidAttach(
                AndroidAttachRequest(
                    form = AndroidAttachForm.View, isMainThread = false, view = Any(),
                    isViewAttachedToWindow = true, viewRootIdentity = null, activityIdentity = null,
                    decorRootIdentity = null, ownerState = 4, parentScopeJob = Any(),
                    parentScopeJobIsActive = true,
                ),
                AndroidViewOwnership(),
            )
        }.exceptionOrNull() is IllegalStateException)
    }
}
