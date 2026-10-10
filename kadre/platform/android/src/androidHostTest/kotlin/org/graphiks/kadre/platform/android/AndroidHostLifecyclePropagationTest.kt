package org.graphiks.kadre.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter

/**
 * La propagation lifecycle de la couture vers le runtime (RUN-004) : les publications du
 * réducteur deviennent l'état lifecycle du runtime — le modèle exact de la couture web
 * (`WebLifecycleReducer.Update` → `controller.updateLifecycle`, `Terminate` → `detach`) —
 * et la fenêtre post-detach est absorbée (`updateLifecycle` exige un host non détaché).
 *
 * Les tests conduisent la couture avec le modèle du require du runtime : un `update` qui
 * rejette après détachement, comme `RuntimeHostController.updateLifecycle` le fait.
 */
class AndroidHostLifecyclePropagationTest {

    @Test
    fun publicationsReachTheRuntimeInOrder() {
        val published = mutableListOf<LifecycleState>()
        listOf(
            // Started → Resumed → Paused → Stopped, l'ordre normatif du lifecycle.
            AndroidHostSnapshot(attachment = true, foreground = true, active = false),
            AndroidHostSnapshot(attachment = true, foreground = true, active = true),
            AndroidHostSnapshot(attachment = true, foreground = true, active = false),
            AndroidHostSnapshot(attachment = true, foreground = false, active = false),
        ).forEach { snapshot ->
            AndroidHostSession.propagateToRuntime(snapshot) { state -> published += state }
        }
        assertEquals(
            listOf(
                LifecycleState(AttachmentState.Attached, VisibilityState.Foreground, ActivationState.Inactive),
                LifecycleState(AttachmentState.Attached, VisibilityState.Foreground, ActivationState.Active),
                LifecycleState(AttachmentState.Attached, VisibilityState.Foreground, ActivationState.Inactive),
                LifecycleState(AttachmentState.Attached, VisibilityState.Background, ActivationState.Inactive),
            ),
            published,
        )
    }

    @Test
    fun publicationAfterDetachIsAbsorbedWithoutResurrection() {
        var detached = false
        val published = mutableListOf<LifecycleState>()
        val runtime: (LifecycleState) -> Unit = { state ->
            require(!detached) { "host is detached" } // le require RÉEL de `updateLifecycle` (IAE)
            published += state
        }
        // Une publication ordinaire atteint le runtime.
        AndroidHostSession.propagateToRuntime(
            AndroidHostSnapshot(attachment = true, foreground = true, active = true),
            runtime,
        )
        // Le terminal a détaché le host ; une publication tardive ne doit ni lancer ni
        // ressusciter l'host — le require est absorbé par la couture.
        detached = true
        AndroidHostSession.propagateToRuntime(
            AndroidHostSnapshot(attachment = true, foreground = false, active = false),
            runtime,
        )
        assertEquals(1, published.size)
        assertEquals(
            LifecycleState(AttachmentState.Attached, VisibilityState.Foreground, ActivationState.Active),
            published.single(),
        )
    }

    @Test
    fun lostTerminalProposalIsReportedThroughTheRuntimeReporter() {
        val recorded = mutableListOf<Throwable>()
        val reporter = RuntimeFailureReporter { cause -> recorded += cause }
        val loss = IllegalStateException("detach failed")
        // Si la proposition d'arrêt terminal est perdue, la perte est visible sur le canal
        // de reporting du runtime — jamais avalée en silence.
        AndroidHostSession.proposeHostStop(detach = { throw loss }, reporter = reporter)
        assertEquals(1, recorded.size)
        assertTrue(recorded.single() === loss)
        // Chemin heureux : la proposition traverse, rien n'est enregistré.
        var proposed = false
        AndroidHostSession.proposeHostStop(detach = { proposed = true }, reporter = reporter)
        assertTrue(proposed)
        assertEquals(1, recorded.size)
    }
}
