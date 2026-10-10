package org.graphiks.kadre.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidAttachStateTest {

    @Test
    fun doubleAttachRejectedWithoutStateMutation() {
        val state = AndroidAttachState()
        val view = Any()
        assertTrue(state.tryClaim(view))
        assertFalse(state.tryClaim(view)) // second attach refusé
        assertTrue(state.installedBridges.isEmpty()) // aucune mutation d'état
        assertTrue(state.tryClaim(Any())) // une autre View reste attachable
    }

    @Test
    fun stopClearsBridgesAndBecomesTerminated() {
        val state = AndroidAttachState()
        val view = Any()
        assertTrue(state.tryClaim(view))
        state.recordBridge("touch-listener") // journalisé par le bridge au moment de l'installation
        state.markTerminated()
        assertTrue(state.isTerminated)
        assertTrue(state.installedBridges.isEmpty()) // zéro résidu
        assertFalse(state.release(view)) // release post-terminal ne réanime rien
        assertTrue(state.tryClaim(view)) // l'ownership libéré, un re-attach est possible
    }

    @Test
    fun releaseUnknownViewIsRejected() {
        val state = AndroidAttachState()
        assertFalse(state.release(Any()))
    }

    @Test
    fun preconditionViolationsReportFieldsInNormativeOrder() {
        // Thread principal d'abord, puis attachement, puis ownership.
        assertEquals("mainThread", validateAttachPreconditions(isMainThread = false, isAttachedToWindow = false, alreadyClaimed = false))
        assertEquals("view", validateAttachPreconditions(isMainThread = true, isAttachedToWindow = false, alreadyClaimed = false))
        assertEquals("view", validateAttachPreconditions(isMainThread = true, isAttachedToWindow = true, alreadyClaimed = true))
        assertEquals(null, validateAttachPreconditions(isMainThread = true, isAttachedToWindow = true, alreadyClaimed = false))
    }

    @Test
    fun ownershipRegistryReclaimsAfterRelease() {
        val view = Any()
        assertTrue(AndroidViewOwnership.claim(view))
        assertTrue(AndroidViewOwnership.isClaimed(view))
        assertFalse(AndroidViewOwnership.claim(view)) // cross-sessions : un seul propriétaire
        assertTrue(AndroidViewOwnership.release(view))
        assertFalse(AndroidViewOwnership.isClaimed(view))
        assertTrue(AndroidViewOwnership.claim(view)) // libéré, re-attach possible
        AndroidViewOwnership.release(view)
    }
}
