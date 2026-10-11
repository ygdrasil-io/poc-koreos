package org.graphiks.kadre.platform.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.test.Test
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les cas épinglés du redraw coalescé par frame (phase 2, tâche 3) : au plus UNE frame en
 * attente (les demandes qui la rejoignent sont admises sans re-planifier), une nouvelle frame
 * après l'achèvement de la précédente, un cancel qui interdit tout callback à jamais, et — au
 * niveau surface — `Success(Unit)` à l'admission, `Closed(Surface)` au terminal, aucun
 * événement après le terminal (zombie interdit). La fin du flux d'événements au terminal est
 * épinglée dans `AndroidHostSurfaceTest.eventsFlowCompletesAtTerminal` (dette routée, commit
 * Task 2). La clock est manuelle en host test — la production branche `ChoreographerFrameClock`.
 */
class AndroidRedrawSchedulerTest {

    // ---------------------------------------------------------------- le scheduler pur

    @Test
    fun redrawCoalescesToOnePendingFrame() {
        val clock = ManualFrameClock()
        var frames = 0
        val scheduler = AndroidRedrawScheduler(clock) { frames += 1 }

        // Cinq demandes dans la même frame : TOUTES admises, UNE seule frame planifiée.
        repeat(5) { assertTrue(scheduler.request(), "a request joining a pending frame admits") }
        assertEquals(1, clock.scheduleCount)
        clock.runPending()
        assertEquals(1, frames)
        assertNull(clock.pending)
    }

    @Test
    fun frameOpensAgainAfterPreviousFrameCompletes() {
        val clock = ManualFrameClock()
        var frames = 0
        val scheduler = AndroidRedrawScheduler(clock) { frames += 1 }

        assertTrue(scheduler.request())
        clock.runPending()
        assertEquals(1, frames)

        // La frame achevée, une nouvelle demande ROUVRE une frame.
        assertTrue(scheduler.request())
        assertEquals(2, clock.scheduleCount)
        clock.runPending()
        assertEquals(2, frames)
    }

    @Test
    fun cancelPreventsAnyFurtherCallback() {
        val clock = ManualFrameClock()
        var frames = 0
        val scheduler = AndroidRedrawScheduler(clock) { frames += 1 }

        assertTrue(scheduler.request())
        scheduler.cancel()
        // Désarmement immédiat : plus de frame en attente, la clock a été annulée.
        assertNull(clock.pending)
        assertEquals(1, clock.cancelCount)
        // Toute demande suivante est refusée.
        assertEquals(false, scheduler.request())
        // Même un corps de frame capturé AVANT le cancel (un callback qui aurait été déçu par
        // la clock) ne lance jamais onFrame : le garde du scheduler est le dernier rempart.
        clock.runCapturedFrameIgnoringPending()
        assertEquals(0, frames)
    }

    // ------------------------------------------------------------ la couture surface

    @Test
    fun redrawCoalescesToOneEventPerFrame() = runBlocking {
        coroutineScope {
            val clock = ManualFrameClock()
            val surface = redrawSurfaceForTest(clock)
            installSessionStampsForRedrawTest(surface)
            val collected = collectRedrawEventsForTest(surface, expected = 1) {
                repeat(5) {
                    val result = surface.requestRedraw()
                    assertIs<KadreResult.Success<Unit>>(result)
                }
                // Les cinq demandes n'ont planifié qu'UNE frame.
                assertEquals(1, clock.scheduleCount)
                clock.runPending()
            }

            // Un seul RedrawRequested, au revision courant, avec le stamp de session.
            assertEquals(1, collected.size)
            val redraw = assertIs<SurfaceEvent.RedrawRequested>(collected.single())
            assertEquals(surface.state.value.revision, redraw.stateRevision)
            assertEquals(0L, redraw.stamp.sequence.value)
        }
    }

    @Test
    fun surfaceFrameEmitsAgainAfterPreviousFrameCompletes() = runBlocking {
        coroutineScope {
            val clock = ManualFrameClock()
            val surface = redrawSurfaceForTest(clock)
            installSessionStampsForRedrawTest(surface)
            val collected = collectRedrawEventsForTest(surface, expected = 2) {
                assertIs<KadreResult.Success<Unit>>(surface.requestRedraw())
                clock.runPending()
                assertNull(clock.pending)
                // La frame précédente achevée, la nouvelle demande rouvre une frame.
                assertIs<KadreResult.Success<Unit>>(surface.requestRedraw())
                clock.runPending()
            }

            assertEquals(2, collected.size)
            // Deux RedrawRequested, chacun avec son stamp (la révision n'a pas bougé : aucun
            // changement d'état entre les deux frames).
            assertEquals(
                listOf(surface.state.value.revision, surface.state.value.revision),
                collected.map { it.stateRevision },
            )
            assertEquals(listOf(0L, 1L), collected.map { it.stamp.sequence.value })
        }
    }

    @Test
    fun redrawAfterTerminalEmitsNothing() = runBlocking {
        val clock = ManualFrameClock()
        val surface = redrawSurfaceForTest(clock)
        installSessionStampsForRedrawTest(surface)
        surface.close()

        // La porte d'admission répond Closed(Surface) au terminal.
        val result = surface.requestRedraw()
        val failure = assertIs<KadreResult.Failure>(result)
        assertEquals(KadreFailure.Closed(KadreResourceKind.Surface), failure.reason)
        // Le terminal a désarmé le scheduler : plus aucune frame en attente.
        assertNull(clock.pending)

        // Même un corps de frame capturé AVANT le terminal ne produit jamais d'événement.
        val events = collectRedrawEventsWithoutExpectationForTest(surface) {
            clock.runCapturedFrameIgnoringPending()
        }
        assertTrue(events.isEmpty(), "no event can follow the terminal")
    }
}

// ----------------------------------------------------------------------- aides du fichier

/**
 * La clock manuelle du host test — la couture SDK seule (production : Choreographer, qui ne
 * peut pas courir ici). Retient le corps de frame capturé séparément de la frame en attente,
 * pour simuler un callback que le cancel n'aurait pas atteint.
 */
private class ManualFrameClock : AndroidRedrawScheduler.FrameClock {
    var scheduleCount = 0
        private set
    var cancelCount = 0
        private set
    var pending: (() -> Unit)? = null
        private set
    var captured: (() -> Unit)? = null
        private set

    override fun schedule(frame: () -> Unit) {
        scheduleCount += 1
        pending = frame
        captured = frame
    }

    override fun cancel() {
        cancelCount += 1
        pending = null
    }

    /** Exécute la frame en attente (une fois). */
    fun runPending() {
        val frame = pending ?: return
        pending = null
        frame()
    }

    /** Simule un callback zombie : exécute le dernier corps capturé, même après cancel. */
    fun runCapturedFrameIgnoringPending() {
        captured?.invoke()
    }
}

/** L'instantané initial de surface du test — les mêmes valeurs que la couture, base 1×1. */
private fun initialSurfaceStateForRedrawTest(): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(1.0, 1.0),
    physicalSize = PhysicalSize(1, 1),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    focus = SurfaceFocus.Focused,
    appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)

/** La surface sous test : clock manuelle injectée (aucun Choreographer en host test). */
private fun redrawSurfaceForTest(clock: ManualFrameClock): AndroidHostSurface = AndroidHostSurface(
    id = RuntimeProcessIds.nextSurfaceId(),
    initialState = initialSurfaceStateForRedrawTest(),
    frameClock = { clock },
)

/** Installe la configuration de session minimale : la source de stamps du test. */
private fun installSessionStampsForRedrawTest(surface: AndroidHostSurface) {
    var sequence = 0L
    surface.installSessionConfiguration(
        deliveryPolicy = KadrePolicies.Default.window,
        inputDeliveryPolicy = KadrePolicies.Default.input,
        source = {
            EventStamp(SessionSequence(sequence), SessionInstant((sequence++).nanoseconds), null)
        },
        sessionFailureHandler = {},
        collectorAllocator = RuntimeEventCollectorAllocator(64),
        maxCollectorsPerFlow = 8,
        resources = KadrePolicies.Default.resources,
        dropTransferScope = null,
        diagnostics = {},
        rawInputPort = null,
    )
}

/**
 * Le collecteur déterministe des événements de redraw : souscription UNDISPATCHED, drain des
 * sauts de dispatch du channelFlow, stimulus, attente des [expected] événements sous borne de
 * temps (un déficit sort en timeout, jamais en silence).
 */
private suspend fun collectRedrawEventsForTest(
    surface: AndroidHostSurface,
    expected: Int,
    emit: suspend () -> Unit,
): List<SurfaceEvent> = coroutineScope {
    val seen = mutableListOf<SurfaceEvent>()
    val done = CompletableDeferred<List<SurfaceEvent>>()
    val collector = async(start = CoroutineStart.UNDISPATCHED) {
        surface.events.collect { event ->
            seen += event
            if (seen.size == expected) done.complete(seen.toList())
        }
    }
    repeat(8) { yield() }
    emit()
    val collected = withTimeout(10_000) { done.await() }
    collector.cancel()
    collected
}

/** Collecte bornée autour d'un stimulus — pour épingler l'ABSENCE d'événements. */
private suspend fun collectRedrawEventsWithoutExpectationForTest(
    surface: AndroidHostSurface,
    emit: suspend () -> Unit,
): List<SurfaceEvent> = coroutineScope {
    val seen = mutableListOf<SurfaceEvent>()
    val collector = async(start = CoroutineStart.UNDISPATCHED) {
        surface.events.collect { event -> seen += event }
    }
    repeat(8) { yield() }
    emit()
    repeat(8) { yield() }
    collector.cancel()
    seen
}
