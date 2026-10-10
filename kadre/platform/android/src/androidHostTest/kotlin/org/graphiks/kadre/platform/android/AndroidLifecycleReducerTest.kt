package org.graphiks.kadre.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidLifecycleReducerTest {

    private fun initial() = AndroidHostSnapshot(attachment = true, foreground = true, active = true)

    @Test
    fun detachIsTerminalEvenWithSurvivingScope() {
        val reducer = AndroidLifecycleReducer(initial())
        val decision = reducer.onEvent(AndroidHostEvent.ViewDetached)
        assertTrue(decision is AndroidLifecycleDecision.Terminate)
        // Snapshot canonique Detached + Background + Inactive, publié avant l'événement terminal.
        assertEquals(AndroidHostSnapshot(attachment = false, foreground = false, active = false), decision.snapshot)
        assertTrue(reducer.isTerminal)
        // Après terminal : plus aucune publication.
        assertEquals(AndroidLifecycleDecision.Ignore, reducer.onEvent(AndroidHostEvent.LifecycleResumed))
    }

    @Test
    fun pauseAndStopDoNotTerminate() {
        val reducer = AndroidLifecycleReducer(initial())
        assertTrue(reducer.onEvent(AndroidHostEvent.LifecyclePaused) is AndroidLifecycleDecision.Publish)
        assertTrue(reducer.onEvent(AndroidHostEvent.LifecycleStopped) is AndroidLifecycleDecision.Publish)
        assertFalse(reducer.isTerminal)
    }

    @Test
    fun notificationsAreDeduplicated() {
        val reducer = AndroidLifecycleReducer(initial())
        assertEquals(AndroidLifecycleDecision.Ignore, reducer.onEvent(AndroidHostEvent.LifecycleResumed))
        assertEquals(AndroidLifecycleDecision.Ignore, reducer.onEvent(AndroidHostEvent.LifecycleStarted))
        // Reprendre après pause republie (l'état a réellement changé).
        assertTrue(reducer.onEvent(AndroidHostEvent.LifecyclePaused) is AndroidLifecycleDecision.Publish)
        assertTrue(reducer.onEvent(AndroidHostEvent.LifecycleResumed) is AndroidLifecycleDecision.Publish)
    }

    @Test
    fun terminalPriorityStopVsDestroyVsException() {
        // Le premier signal terminal gagne ; destroy après detach, detach après destroy, etc.
        val first = AndroidLifecycleReducer(initial())
        first.onEvent(AndroidHostEvent.LifecycleDestroyed)
        assertEquals(AndroidLifecycleDecision.Ignore, first.onEvent(AndroidHostEvent.ViewDetached))

        val second = AndroidLifecycleReducer(initial())
        assertTrue(second.onEvent(AndroidHostEvent.ViewDetached) is AndroidLifecycleDecision.Terminate)
        assertEquals(AndroidLifecycleDecision.Ignore, second.onEvent(AndroidHostEvent.LifecycleDestroyed))
    }

    @Test
    fun lifecycleStartedPublishesForegroundSnapshot() {
        val reducer = AndroidLifecycleReducer(
            AndroidHostSnapshot(attachment = true, foreground = false, active = false),
        )
        val decision = reducer.onEvent(AndroidHostEvent.LifecycleStarted)
        assertEquals(AndroidHostSnapshot(attachment = true, foreground = true, active = false), (decision as AndroidLifecycleDecision.Publish).snapshot)
    }
}
