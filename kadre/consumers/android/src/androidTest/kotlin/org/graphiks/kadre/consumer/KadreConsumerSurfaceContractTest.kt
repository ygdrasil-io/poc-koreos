@file:OptIn(KadrePlatformApi::class, DelicateKadreApi::class)

package org.graphiks.kadre.consumer

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.platform.android.withAndroidView
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceState
import org.junit.runner.RunWith

/**
 * Les six scénarios instrumentés du contrat AND-003 (surface observable, metrics, redraw et
 * accès natif borné) sur l'API publique résolue depuis l'umbrella PUBLIÉ. Chaque nom de
 * méthode est un `testName` contractuel du mapping AND-003 — la correspondance :
 *
 * | scenario id (registre)              | méthode                        | sentinelle gardée                    |
 * |-------------------------------------|--------------------------------|--------------------------------------|
 * | android-surface-metrics-match-view  | androidSurfaceMetricsMatchView | android-metrics-generation-stale (*) |
 * | android-surface-resize-propagates   | androidSurfaceResizePropagates | -                                    |
 * | android-surface-redraw-coalesced    | androidSurfaceRedrawCoalesced  | android-redraw-single-per-frame      |
 * | android-surface-redraw-after-close  | androidSurfaceRedrawAfterClose | -                                    |
 * | android-surface-lease-boundaries    | androidSurfaceLeaseBoundaries  | android-lease-closed-after-terminal  |
 * | android-surface-no-window-handle    | androidSurfaceNoWindowHandle   | -                                    |
 *
 * (*) `android-metrics-generation-stale` est épinglé côté HÔTE
 * (`AndroidSurfaceMetricsTest.staleGenerationCallbackRejected`, Task 2) : aucun scénario
 * émulateur ne peut causer un callback réellement périmé — le binding étiquette chaque
 * observation avec la génération courante du réducteur, et sa View n'est jamais remplacée en
 * cours de session (un détachement est terminal). Le run de mutation qui démontre le kill est
 * consigné dans le rapport de la tâche ; la ligne sentinelle du registre reste démontrée par
 * ce run de mutation hôte.
 *
 * Chaque scénario est autonome (ActivityScenario `use{}` → la destruction du host termine
 * toute session vivante) et ferme en plus sa session explicitement. Les états sont attendus
 * par observation bornée sur le thread d'instrumentation — le looper main publie ; les
 * mutations de View et les `close()` passent par le thread main.
 *
 * Le double opt-in du fichier est le contrat de la frontière (`withAndroidView`) : un appel
 * sans [KadrePlatformApi] ni [DelicateKadreApi] ne compile pas — le contre-exemple est la
 * compilation elle-même, il ne peut pas vivre dans ce module.
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerSurfaceContractTest {

    /**
     * android-surface-metrics-match-view — après attach, l'état publié de la surface est
     * aligné sur les valeurs RÉELLES de la View : dimensions physiques (`view.width/height`),
     * échelle (`resources.displayMetrics.density`), taille logique (physique/échelle) et
     * insets cohérents avec `rootWindowInsets`.
     */
    @Test
    fun androidSurfaceMetricsMatchView() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            activity.consumerView,
                            scopeCapturingFactory(captured),
                        ),
                    ),
                )
            }
            val surface = awaitPrimarySurface(captured)
            val view = AtomicReference<View?>(null)
            scenario.onActivity { view.set(it.consumerView) }
            val attachedView = ConsumerTestSupport.notNull(view)

            // Les métriques publiées rejoignent la View réelle (le binding a publié ses
            // observations) — observation bornée, jamais une lecture en course.
            var last = "no coherent state yet"
            var coherent: SurfaceState? = null
            val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
            while (System.currentTimeMillis() < deadline && coherent == null) {
                val state = surface.state.value
                if (stateMatchesView(state, attachedView)) {
                    coherent = state
                } else {
                    last = "$state"
                    Thread.sleep(POLL_INTERVAL_MILLIS)
                }
            }
            assertNotNull(coherent, "surface state never matched the view; last=$last")

            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    /**
     * android-surface-resize-propagates — changer la taille de la View (layout params) produit
     * un `MetricsChanged` sur `surface.events` portant la nouvelle taille, et l'identité de la
     * surface est PRÉSERVÉE (la même instance `HostSurface`, la révision qui marque le
     * changement — les invariants P1).
     */
    @Test
    fun androidSurfaceResizePropagates() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            activity.consumerView,
                            scopeCapturingFactory(captured),
                        ),
                    ),
                )
            }
            val surface = awaitPrimarySurface(captured)
            val view = AtomicReference<View?>(null)
            scenario.onActivity { view.set(it.consumerView) }
            val attachedView = ConsumerTestSupport.notNull(view)
            awaitStateMatchesView(surface, attachedView)
            val revisionBefore = surface.state.value.revision

            val target = PhysicalSize(
                maxOf(1, attachedView.width / 2),
                maxOf(1, attachedView.height / 2),
            )
            val recorder = EventRecorder(surface.events, this)
            // Le drain de souscription (le miroir des tests hôte) : la chaîne
            // channelFlow → SharedFlow n'accepte un essai d'émission qu'une fois ses
            // coroutines enfants dispatchées — un stimulus plus tôt serait tombé.
            repeat(8) { yield() }
            try {
                ConsumerTestSupport.onMain {
                    attachedView.layoutParams = FrameLayout.LayoutParams(target.width, target.height)
                    attachedView.requestLayout()
                }
                // Observation bornée AVEC diagnostic : au timeout, le scénario nomme ce qu'il
                // a vu (événements collectés, dimensions courantes de la View, état publié).
                val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
                var matched: SurfaceEvent.MetricsChanged? = null
                while (matched == null && System.currentTimeMillis() < deadline) {
                    matched = recorder.snapshot()
                        .filterIsInstance<SurfaceEvent.MetricsChanged>()
                        .firstOrNull { it.state.physicalSize == target }
                    if (matched == null) delay(POLL_INTERVAL_MILLIS)
                }
                val event = assertNotNull(
                    matched,
                    "no MetricsChanged carried the resized view; target=$target, " +
                        "view=${attachedView.width}x${attachedView.height}, " +
                        "state=${surface.state.value}, events=${recorder.snapshot()}",
                )
                // Identité préservée : la MÊME instance, une révision qui a bougé.
                assertSame(
                    surface,
                    ConsumerTestSupport.notNull(captured).primarySurface.value,
                    "the resize must not replace the surface instance",
                )
                assertTrue(
                    event.state.revision.value > revisionBefore.value,
                    "the published state must move the revision",
                )
            } finally {
                recorder.cancel()
            }
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    /**
     * android-surface-redraw-coalesced — 5 `requestRedraw()` dans la même frame répondent
     * chacun `Success(Unit)` et produisent EXACTEMENT UN `RedrawRequested` sur la fenêtre
     * d'événements : le coalescing monoframe n'émet qu'une frame par rafale.
     *
     * Sentinelle gardée : `android-redraw-single-per-frame` (chaque request publiant son
     * événement immédiatement, la fenêtre compte 5 événements — lui seul collecte des redraw).
     */
    @Test
    fun androidSurfaceRedrawCoalesced() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            activity.consumerView,
                            scopeCapturingFactory(captured),
                        ),
                    ),
                )
            }
            val surface = awaitPrimarySurface(captured)

            val recorder = EventRecorder(surface.events, this)
            // Le drain de souscription (le miroir des tests hôte) : la chaîne
            // channelFlow → SharedFlow n'accepte un essai d'émission qu'une fois ses
            // coroutines enfants dispatchées — un stimulus plus tôt serait tombé.
            repeat(8) { yield() }
            try {
                // Les cinq demandes de la même frame : chacune admise, `Success(Unit)`.
                repeat(5) { request ->
                    assertEquals(
                        KadreResult.Success(Unit),
                        surface.requestRedraw(),
                        "request #$request must be admitted by the live surface",
                    )
                }
                // La frame coalescée arrive ; puis la fenêtre se referme : aucune seconde
                // frame ne peut exister sans nouvelle demande (borné à ~20 frames).
                withTimeout(DEFAULT_TIMEOUT_MILLIS) {
                    while (recorder.redraws().isEmpty()) delay(POLL_INTERVAL_MILLIS)
                }
                delay(FRAME_WINDOW_MILLIS)
            } finally {
                recorder.cancel()
            }
            assertEquals(
                1,
                recorder.redraws().size,
                "five requests in the same frame must coalesce into ONE RedrawRequested, " +
                    "got ${recorder.snapshot()}",
            )
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    /**
     * android-surface-redraw-after-close — `session.close()` puis `requestRedraw()` répond
     * `Failure(Closed(Surface))` et AUCUN événement supplémentaire n'est publié : le
     * terminal ferme l'admission et annule la frame pendante (zombie interdit).
     */
    @Test
    fun androidSurfaceRedrawAfterClose() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            activity.consumerView,
                            scopeCapturingFactory(captured),
                        ),
                    ),
                )
            }
            val surface = awaitPrimarySurface(captured)

            val recorder = EventRecorder(surface.events, this)
            // Le drain de souscription (le miroir des tests hôte) : la chaîne
            // channelFlow → SharedFlow n'accepte un essai d'émission qu'une fois ses
            // coroutines enfants dispatchées — un stimulus plus tôt serait tombé.
            repeat(8) { yield() }
            try {
                ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
                ConsumerTestSupport.awaitTerminal(
                    ConsumerTestSupport.notNull(session),
                    SessionOutcome.Stopped(SessionStopReason.HostRequested),
                )
                // Le terminal DE LA SURFACE (les capabilities retombées) : la porte
                // d'admission est fermée partout, la réponse `Closed` est déterministe.
                awaitSurfaceTerminal(surface)

                assertEquals(
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
                    surface.requestRedraw(),
                    "a closed surface refuses every redraw",
                )
                delay(FRAME_WINDOW_MILLIS)
            } finally {
                recorder.cancel()
            }
            assertTrue(
                recorder.redraws().isEmpty(),
                "no redraw event may follow the terminal, got ${recorder.snapshot()}",
            )
        }
    }

    /**
     * android-surface-lease-boundaries — le bail d'accès natif : le block reçoit LA View du
     * host (`Success(true)` sur l'identité) ; un second bail pendant qu'un autre est en vol
     * répond `TemporarilyUnavailable(retryable = true)` sans invoquer son block ; après
     * `session.close()` tout bail répond `Closed(Surface)` sans invoquer.
     *
     * Sentinelle gardée : `android-lease-closed-after-terminal` (un bail qui n'interroge plus
     * son admission remet la View d'une surface fermée — lui seul, car aucun autre scénario
     * ne loue après le terminal).
     */
    @Test
    fun androidSurfaceLeaseBoundaries() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            activity.consumerView,
                            scopeCapturingFactory(captured),
                        ),
                    ),
                )
            }
            val surface = awaitPrimarySurface(captured)
            val view = AtomicReference<View?>(null)
            scenario.onActivity { view.set(it.consumerView) }
            val attachedView = ConsumerTestSupport.notNull(view)

            // 1. Le bail nominal : le block reçoit la View du host elle-même.
            val nominal = withTimeout(DEFAULT_TIMEOUT_MILLIS) {
                surface.withAndroidView { leased -> leased is View && leased == attachedView }
            }
            assertEquals(
                KadreResult.Success(true),
                nominal,
                "the lease hands the attached View to its block",
            )

            // 2. Le bail unique : un second bail pendant qu'un autre est en vol répond
            //    `TemporarilyUnavailable(retryable = true)` sans invoquer son block. Le
            //    premier block se gare sur un latch (borné) pour tenir le bail en vol.
            val started = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            val holder = launch(Dispatchers.IO) {
                surface.withAndroidView { _ ->
                    started.complete(Unit)
                    release.await(LEASE_HOLD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                }
            }
            try {
                withTimeout(DEFAULT_TIMEOUT_MILLIS) { started.await() }
                var secondInvoked = false
                val second = surface.withAndroidView { secondInvoked = true }
                assertEquals(
                    KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
                    second,
                    "a lease started while another is in flight is a retryable temporary failure",
                )
                assertFalse(secondInvoked, "the refused lease never invokes its block")
            } finally {
                release.countDown()
                withTimeout(DEFAULT_TIMEOUT_MILLIS) { holder.join() }
            }

            // 3. Après `session.close()` : `Closed(Surface)`, le block jamais invoqué.
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
            ConsumerTestSupport.awaitTerminal(
                ConsumerTestSupport.notNull(session),
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
            )
            awaitSurfaceTerminal(surface)
            var invokedAfterClose = false
            val closed = surface.withAndroidView { invokedAfterClose = true }
            assertEquals(
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
                closed,
                "a closed surface lends nothing",
            )
            assertFalse(invokedAfterClose, "a closed surface never invokes the block")
        }
    }

    /**
     * android-surface-no-window-handle — la frontière native : `WindowCapabilities.platformAccess`
     * de la fenêtre primaire vaut `Unsupported(PlatformWindowAccess)`, aucun membre public de la
     * surface n'expose `android.view.Window` (réflexion sur les signatures publiques), et le
     * bail remet la View elle-même — jamais un handle générique.
     *
     * Le contre-exemple de compilation (un `withAndroidView` sans les DEUX opt-ins) ne tient
     * pas dans ce module : c'est la compilation elle-même qui le prouve — les annotations sont
     * portées par la déclaration publique, ce fichier les porte en tête.
     */
    @Test
    fun androidSurfaceNoWindowHandle() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            activity.consumerView,
                            scopeCapturingFactory(captured),
                        ),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            val surface = awaitPrimarySurface(captured)

            // La fenêtre primaire (forme Activity) n'annonce AUCUN accès natif de fenêtre.
            val primaryWindow = assertNotNull(
                scope.windows.state.value.primary,
                "the activity form exposes its primary window",
            )
            assertEquals(
                Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess)),
                primaryWindow.capabilities.value.platformAccess,
                "the android window never claims generic window access",
            )

            // Aucun membre public de la surface (interface HostSurface + classe runtime) ne
            // prend ou ne rend un android.view.Window.
            val windowType = android.view.Window::class.java
            val offending = (HostSurface::class.java.methods + surface.javaClass.methods)
                .filter { method ->
                    windowType.isAssignableFrom(method.returnType) ||
                        method.parameterTypes.any(windowType::isAssignableFrom)
                }
            assertTrue(
                offending.isEmpty(),
                "the public surface must never expose android.view.Window, found: $offending",
            )

            // Le bail remet la View elle-même — le seul objet natif qui traverse. Le type
            // STATIQUE du block est View : un `android.view.Window` (qui n'est pas une View)
            // ne peut même pas typé traverser cette frontière — le contre-exemple est le
            // système de types lui-même.
            val handed = withTimeout(DEFAULT_TIMEOUT_MILLIS) {
                surface.withAndroidView { it }
            }
            assertIs<KadreResult.Success<View>>(handed)
            assertIs<View>(handed.value)
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    // ----------------------------------------------------------------------- aides du fichier

    /** La factory qui capture le [KadreScope] de la session pour l'observation du test. */
    private fun scopeCapturingFactory(captured: AtomicReference<KadreScope?>): KadreApplicationFactory =
        KadreApplicationFactory {
            KadreApplication {
                captured.set(this)
                awaitCancellation()
            }
        }

    /** La surface primaire de la session — publiée par le looper main, attendue par polling. */
    private fun awaitPrimarySurface(captured: AtomicReference<KadreScope?>): HostSurface {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            captured.get()?.primarySurface?.value?.let { return it }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the primary surface was never published within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /**
     * L'état publié rejoint la View réelle : dimensions, échelle, taille logique, insets.
     * (Les accesseurs `systemWindowInset*` sont déprécés depuis l'API 29 mais restent les
     * seuls disponibles sur le plancher — le côté binding lit les MÊMES accesseurs, la
     * comparaison est exactement symétrique.)
     */
    @Suppress("DEPRECATION")
    private fun stateMatchesView(state: SurfaceState, view: View): Boolean {
        val density = view.resources.displayMetrics.density
        if (!density.isFinite() || density <= 0f) return false
        val scale = density.toDouble()
        val insets = view.rootWindowInsets ?: return false
        if (view.width <= 0 || view.height <= 0) return false
        return state.physicalSize == PhysicalSize(view.width, view.height) &&
            state.scaleFactor == scale &&
            state.logicalSize == LogicalSize(view.width / scale, view.height / scale) &&
            state.safeAreaInsets == LogicalInsets(
                top = insets.systemWindowInsetTop / scale,
                right = insets.systemWindowInsetRight / scale,
                bottom = insets.systemWindowInsetBottom / scale,
                left = insets.systemWindowInsetLeft / scale,
            )
    }

    /** Observation bornée de la cohérence métriques ↔ View (le scénario match-view). */
    private fun awaitStateMatchesView(surface: HostSurface, view: View) {
        var last = "no coherent state yet"
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val state = surface.state.value
            if (stateMatchesView(state, view)) return
            last = "$state"
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("surface state never matched the view within ${DEFAULT_TIMEOUT_MILLIS}ms; last=$last")
    }

    /**
     * Le terminal DE LA SURFACE : les capabilities retombées (`platformAccess` n'est plus
     * `Supported`). Signal public que `close()` a tourné — la réponse `Closed` de toute
     * admission suivante est déterministe.
     */
    private suspend fun awaitSurfaceTerminal(surface: HostSurface) {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (surface.capabilities.value.platformAccess !is Capability.Supported<*>) return
            delay(POLL_INTERVAL_MILLIS)
        }
        error("the surface never reached its terminal within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /**
     * Le collecteur d'événements du test : souscrit en UNDISPATCHED (la souscription est
     * engagée au retour), accumule sous verrou — les publications arrivent du looper main.
     */
    private class EventRecorder(flow: Flow<SurfaceEvent>, scope: CoroutineScope) {
        private val lock = Any()
        private val events = mutableListOf<SurfaceEvent>()
        private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { event -> synchronized(lock) { events += event } }
        }

        fun snapshot(): List<SurfaceEvent> = synchronized(lock) { events.toList() }

        fun redraws(): List<SurfaceEvent.RedrawRequested> =
            snapshot().filterIsInstance<SurfaceEvent.RedrawRequested>()

        fun cancel() {
            job.cancel()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 25L
        const val FRAME_WINDOW_MILLIS = 400L
        const val LEASE_HOLD_TIMEOUT_MILLIS = 10_000L
    }
}
