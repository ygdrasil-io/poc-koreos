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
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Les cas épinglés du réducteur de métriques Android (phase 2, tâche 2) : la publication
 * physique/logique à densité déterministe, la distinction des causes géométriques (rotation ≠
 * resize ≠ densité), le rejet par génération (`supersedeGeneration`), la conservation des
 * `Unknown` face aux observations manquantes, les insets vers safe area, l'absence de
 * publication pour une observation identique, et l'émission des événements typés via la
 * couture `publishMetrics` de la surface (tâche 1).
 */
class AndroidSurfaceMetricsTest {

    // ---------------------------------------------------------------- le réducteur pur

    @Test
    fun dimensionsAndDensityPublishLogicalAndPhysical() {
        val reducer = AndroidSurfaceMetricsReducer(initialSurfaceStateForMetricsTest())

        val change = reducer.onObservation(
            observation(width = 1080, height = 2400, density = 2.625f),
        )

        // La densité est le pont : physique tel qu'observé, logique = physique / échelle —
        // la division IEEE de `toLogical`, déterministe par construction.
        assertEquals(PhysicalSize(1080, 2400), reducer.currentState.physicalSize)
        assertEquals(2.625, reducer.currentState.scaleFactor)
        assertEquals(LogicalSize(1080.0 / 2.625, 2400.0 / 2.625), reducer.currentState.logicalSize)
        assertEquals(
            AndroidSurfaceChange.Metrics(rotated = false, densityChanged = true, resized = true),
            change,
        )
        // La publication a déplacé l'état : la révision marque le changement réel.
        assertEquals(SurfaceRevision(1L), reducer.currentState.revision)
    }

    @Test
    fun rotationVsResizeVsDensityAreDistinguished() {
        fun reducer(): AndroidSurfaceMetricsReducer = AndroidSurfaceMetricsReducer(
            initialSurfaceStateForMetricsTest().copy(
                physicalSize = PhysicalSize(1080, 2400),
                logicalSize = LogicalSize(1080.0 / 2.625, 2400.0 / 2.625),
                scaleFactor = 2.625,
            ),
        )

        // Rotation : les dimensions s'échangent à densité constante.
        assertEquals(
            AndroidSurfaceChange.Metrics(rotated = true, densityChanged = false, resized = false),
            reducer().onObservation(observation(width = 2400, height = 1080, density = 2.625f)),
        )
        // Resize : une dimension change sans échange.
        assertEquals(
            AndroidSurfaceChange.Metrics(rotated = false, densityChanged = false, resized = true),
            reducer().onObservation(observation(width = 1080, height = 2000, density = 2.625f)),
        )
        // Densité seule : mêmes dimensions, échelle mue.
        assertEquals(
            AndroidSurfaceChange.Metrics(rotated = false, densityChanged = true, resized = false),
            reducer().onObservation(observation(width = 1080, height = 2400, density = 3.5f)),
        )
    }

    @Test
    fun staleGenerationCallbackRejected() {
        val reducer = AndroidSurfaceMetricsReducer(initialSurfaceStateForMetricsTest())
        // L'observation de la génération courante (0) est publiée.
        assertTrue(reducer.onObservation(observation(generation = 0L)) is AndroidSurfaceChange.Metrics)
        val published = reducer.currentState
        assertEquals(SurfaceRevision(1L), published.revision)

        // Le teardown/recréation invalide la génération courante.
        reducer.supersedeGeneration()

        // Le callback retardé de l'ANCIENNE ressource est rejeté : état inchangé, révision intacte.
        assertEquals(
            AndroidSurfaceChange.Stale,
            reducer.onObservation(observation(width = 999, height = 999, generation = 0L)),
        )
        assertEquals(published, reducer.currentState)

        // La ressource recréée (génération suivante) est admise — le binding, qui possède le
        // compteur, étiquette ses observations avec la génération courante du réducteur.
        assertTrue(
            reducer.onObservation(observation(width = 999, height = 999, generation = 1L))
                is AndroidSurfaceChange.Metrics,
        )
        assertEquals(SurfaceRevision(2L), reducer.currentState.revision)
    }

    @Test
    fun generationIsReadableToPairTheBindingObservations() {
        val reducer = AndroidSurfaceMetricsReducer(initialSurfaceStateForMetricsTest())
        // Le compteur démarre à 0 et appartient au réducteur ; le binding le LIT pour
        // étiqueter ses observations — pas de miroir fragile en lockstep (carry T2+3, m3).
        assertEquals(0L, reducer.generation)
        reducer.supersedeGeneration()
        assertEquals(1L, reducer.generation)

        // Une observation étiquetée avec la génération LUE du réducteur est admise.
        assertTrue(
            reducer.onObservation(
                observation(width = 999, height = 999, generation = reducer.generation),
            ) is AndroidSurfaceChange.Metrics,
        )
    }

    @Test
    fun missingObservationsKeepUnknown() {
        // À l'initial : thème Unknown, visibilité/focus posés par l'instantané de la couture.
        val initial = initialSurfaceStateForMetricsTest()
        assertEquals(SurfaceTheme.Unknown, initial.appearance.theme)
        val reducer = AndroidSurfaceMetricsReducer(initial)

        // Observation sans aucun état de fenêtre, identique à l'instantané : rien ne bouge du
        // tout — aucune valeur inventée, aucune publication non plus.
        assertEquals(
            AndroidSurfaceChange.Ignored,
            reducer.onObservation(
                observation(width = 1, height = 1, density = 1.0f),
            ),
        )
        assertEquals(initial, reducer.currentState)

        // Une observation partielle (un seul champ présent) ne touche que ce champ.
        assertTrue(
            reducer.onObservation(observation(visible = false, focused = false, nightMode = true))
                is AndroidSurfaceChange.Metrics,
        )
        assertEquals(SurfaceVisibility.Hidden, reducer.currentState.visibility)
        assertEquals(SurfaceFocus.Unfocused, reducer.currentState.focus)
        assertEquals(
            SurfaceAppearance(SurfaceTheme.Dark, initial.appearance.contrast),
            reducer.currentState.appearance,
        )
        // Le contraste n'est jamais inventé par uiMode : il garde la valeur posée.
        assertEquals(SurfaceContrast.Unknown, reducer.currentState.appearance.contrast)
    }

    @Test
    fun insetsPublishSafeArea() {
        val reducer = AndroidSurfaceMetricsReducer(initialSurfaceStateForMetricsTest())

        // Les insets observés sont physiques ; la publication les rend logiques (physique/échelle).
        reducer.onObservation(
            observation(density = 2.0f, left = 53, top = 118, right = 21, bottom = 76),
        )

        assertEquals(
            LogicalInsets(top = 59.0, right = 10.5, bottom = 38.0, left = 26.5),
            reducer.currentState.safeAreaInsets,
        )
    }

    @Test
    fun identicalObservationIsIgnoredWithoutPublication() {
        val reducer = AndroidSurfaceMetricsReducer(initialSurfaceStateForMetricsTest())
        val observation = observation(width = 1080, height = 2400, density = 2.625f)
        assertTrue(reducer.onObservation(observation) is AndroidSurfaceChange.Metrics)
        val published = reducer.currentState

        // L'observation identique ne publie pas : la révision ne marque pas les appels.
        assertEquals(AndroidSurfaceChange.Ignored, reducer.onObservation(observation))
        assertEquals(published, reducer.currentState)
    }

    // ------------------------------------------------- la couture surface (événements typés)

    @Test
    fun focusAndVisibilityAndAppearancePublishTypedEvents() = runBlocking {
        coroutineScope {
            val surface = AndroidHostSurface(
                id = RuntimeProcessIds.nextSurfaceId(),
                initialState = initialSurfaceStateForMetricsTest(),
            )
            var sequence = 0L
            installSessionStampsForTest(surface) {
                EventStamp(SessionSequence(sequence), SessionInstant((sequence++).nanoseconds), null)
            }
            val collected = collectEventsUntilForTest(surface, expected = 4) {
                val reducer = AndroidSurfaceMetricsReducer(surface.state.value)

                // Focus : l'observation étiquette FocusChanged.
                publishObservationForTest(
                    surface,
                    reducer,
                    observation(width = 1, height = 1, density = 1.0f, focused = false),
                )
                // Visibilité : VisibilityChanged.
                publishObservationForTest(
                    surface,
                    reducer,
                    observation(width = 1, height = 1, density = 1.0f, visible = false),
                )
                // Apparence : AppearanceChanged (uiMode → thème, jamais le contraste).
                publishObservationForTest(
                    surface,
                    reducer,
                    observation(width = 1, height = 1, density = 1.0f, nightMode = true),
                )
                // Géométrie : MetricsChanged.
                publishObservationForTest(
                    surface,
                    reducer,
                    observation(width = 1080, height = 2400, density = 1.0f),
                )
            }

            // Une publication par cause distinguée, chacune avec l'événement typé et le stamp
            // de session (révision alignée sur l'état publié ; séquence de session strictement
            // croissante — la source du test démarre à 0).
            assertEquals(listOf(1L, 2L, 3L, 4L), collected.map { it.stateRevision.value })
            assertEquals(listOf(0L, 1L, 2L, 3L), collected.map { it.stamp.sequence.value })
            assertIs<SurfaceEvent.FocusChanged>(collected[0])
            assertIs<SurfaceEvent.VisibilityChanged>(collected[1])
            assertIs<SurfaceEvent.AppearanceChanged>(collected[2])
            val metrics = assertIs<SurfaceEvent.MetricsChanged>(collected[3])
            // Chaque événement porte l'instantané publié (celui du flux d'état).
            assertSame(surface.state.value, metrics.state)
        }
    }

    @Test
    fun surfaceRevisionOnlyMovesOnRealChangeAndInstanceIsPreserved() = runBlocking {
        val surface = AndroidHostSurface(
            id = RuntimeProcessIds.nextSurfaceId(),
            initialState = initialSurfaceStateForMetricsTest(),
        )
        val before = surface
        // L'observation par défaut est déjà l'état initial : publication identique.
        val silent = collectedEventsForTest(surface) {
            surface.publishMetrics(
                update = { current -> androidSurfaceApply(current, observation(width = 1, height = 1, density = 1.0f)).candidate },
                eventFor = ::androidSurfaceEvent,
            )
        }

        // Ni événement, ni révision, ni remplacement d'instance : les invariants P1 tiennent
        // sur la couture à événements comme sur la publication muette de la tâche 1.
        assertSame(before, surface)
        assertEquals(SurfaceRevision(0L), surface.state.value.revision)
        assertTrue(silent.isEmpty(), "an identical publication must not emit")
    }
}

// ----------------------------------------------------------------------- aides du fichier

/** L'instantané initial de surface du test — les mêmes valeurs que la couture, base 1×1. */
private fun initialSurfaceStateForMetricsTest(): SurfaceState = SurfaceState(
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

/** Une observation SDK copiée, défauts alignés sur les cas épinglés (génération courante 0). */
private fun observation(
    width: Int = 1080,
    height: Int = 2400,
    density: Float = 2.625f,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    visible: Boolean? = null,
    focused: Boolean? = null,
    nightMode: Boolean? = null,
    generation: Long = 0L,
): AndroidSurfaceObservation = AndroidSurfaceObservation(
    physicalWidth = width,
    physicalHeight = height,
    density = density,
    leftInset = left,
    topInset = top,
    rightInset = right,
    bottomInset = bottom,
    visible = visible,
    focused = focused,
    nightMode = nightMode,
    generation = generation,
)

/**
 * Le branchement du binding (la forme que Task 5 réutilisera) : le réducteur décide et retient
 * l'observation comme cause, la surface publie depuis SON état courant — l'application pure
 * `androidSurfaceApply` est rejouée sur la vérité de la surface, jamais de dérive.
 */
private fun publishObservationForTest(
    surface: AndroidHostSurface,
    reducer: AndroidSurfaceMetricsReducer,
    observation: AndroidSurfaceObservation,
) {
    assertIs<AndroidSurfaceChange.Metrics>(reducer.onObservation(observation))
    surface.publishMetrics(
        update = { current -> androidSurfaceApply(current, observation).candidate },
        eventFor = ::androidSurfaceEvent,
    )
}

/** Installe la configuration de session minimale : la source de stamps du test. */
private fun installSessionStampsForTest(surface: AndroidHostSurface, source: () -> EventStamp) {
    surface.installSessionConfiguration(
        deliveryPolicy = KadrePolicies.Default.window,
        inputDeliveryPolicy = KadrePolicies.Default.input,
        source = source,
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
 * Le collecteur déterministe : souscrit en UNDISPATCHED (le corps court jusqu'à sa première
 * suspension, donc la souscription au flux interne est engagée), draine les quelques sauts de
 * dispatch du channelFlow par [yield], conduit le stimulus, puis ne débloque qu'aux
 * [expected] événements reçus — un déficit sort en timeout, jamais en silence.
 */
private suspend fun collectEventsUntilForTest(
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
private suspend fun collectedEventsForTest(
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
