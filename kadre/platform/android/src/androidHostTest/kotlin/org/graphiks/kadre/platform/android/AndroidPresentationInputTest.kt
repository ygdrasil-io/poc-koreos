package org.graphiks.kadre.platform.android

import android.view.KeyEvent
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.policy.KadrePolicies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * L'entrée de la fenêtre présentation (Task 3, phase 11) : la chaîne `Window.Callback`
 * s'installe sur la fenêtre du média AU PREMIER SHOW RÉUSSI — une seule fois pour la vie du
 * média (la fenêtre d'une Presentation survit à hide/show, la repose à chaque show doublerait
 * les événements) — l'input observé alimente la session PRÉSENTATION seule, et la fermeture
 * du média restitue la callback d'origine EXACTE (la discipline « no listener replacement »).
 *
 * L'installation vit derrière la couture injectée `inputChainFactory` (le modèle des étapes
 * Task 1/2 : la production y passe la fenêtre réelle du jeton, les tests hôtes une fake
 * pilotable sans aucun type SDK). Le journal d'une session est le wrap de SON câblage — le
 * patron du `Dispatch` des tests d'input existants : l'observation est enregistrée AVANT de
 * nourrir le funnel de production de la surface de CETTE session. La session « principale »
 * du cas cross-talk passe la MÊME couture (la forme Activity n'est pas conduisible sans
 * android.jar — la propriété épinglée est le routage PAR SESSION du câblage, ce que la
 * composition construit).
 */
class AndroidPresentationInputTest {

    // ------------------------------------------------------------------ les cas du brief

    @Test
    fun `la chaine s'installe une seule fois apres le premier show`() {
        val host = PresentationInputHost()
        val fakeSource = host.attachWithFakeSource(display(7L)).let { host.fakeSource!! }
        fakeSource.show()
        fakeSource.hide()
        fakeSource.show()
        // Le média survit à hide/show et sa fenêtre avec lui : la chaîne est posée au
        // PREMIER show seul — PAS 2.
        assertEquals(1, fakeSource.windowCallbackInstallCount)
    }

    @Test
    fun `un key event fenetre presentation alimente la session presentation seule`() {
        val host = PresentationInputHost()
        val main = host.attachSurrogateMainSession(mainView = Any(), factory = hangingApplication())
        val cast = host.attachWithFakeSource(display(7L))
        val fakeSource = host.fakeSource!!
        fakeSource.show()
        fakeSource.dispatchKeyToPresentationWindow(KeyEvent.KEYCODE_A)

        assertEquals(listOf(keyObserved(KeyEvent.KEYCODE_A)), host.castInputJournal(cast))
        assertTrue(host.mainInputJournal(main).isEmpty()) // cross-talk = échec
    }

    /**
     * Le show du RATTRAPAGE lifecycle pendant l'install (phase 11, Task 4) : sur un hôte
     * RESUMED, `addObserver` livre ON_CREATE puis ON_START au nouvel observer — et ce START
     * level-triggered montre le média PENDANT l'étape d'installation du binding, AVANT
     * `controller.attach` : la chaîne d'entrée est posée et `observationInstalled(true)`
     * publié alors que la delivery d'entrée N'EXISTE PAS ENCORE (elle naît à
     * `installSessionConfiguration`, à l'attach du runtime). Le fait câblé doit SURVIVRE à
     * la configuration — la surface le retient et le rejoue sur la delivery qu'elle
     * construit (le parti pris `gamepadRoute`) : sans lui, la delivery naîtrait non
     * installée et bufferait chaque observation pour toujours (caps Unsupported, zéro
     * événement — le relevé device de la phase 11). L'oracle : les capabilities d'entrée de
     * LA session deviennent honnêtes après la configuration, et l'observation d'UNE touche
     * par la chaîne posée pendant l'install atteint le journal de session.
     */
    @Test
    fun `le show du rattrapage lifecycle pendant l install livre le fait cable a la configuration`() {
        val host = PresentationInputHost()
        val captured = AtomicReference<KadreScope?>(null)
        val session = host.attachWithCatchupShow(display(9L), captured)
        val scope = awaitCapturedScope(captured)

        // Le fait câblé a survécu à la configuration : les caps d'entrée de LA session sont
        // honnêtes (Available derrière un observateur réellement installé — sémantique C).
        awaitInputCapability(scope) { caps -> caps.touch == FeatureAvailability.Available }

        // Et la chaîne posée PENDANT l'install délivre : une touche atteint le journal.
        host.fakeSource!!.dispatchKeyToPresentationWindow(KeyEvent.KEYCODE_A)
        assertEquals(listOf(keyObserved(KeyEvent.KEYCODE_A)), host.castInputJournal(session))
    }

    @Test
    fun `fermeture du media restitue la callback d origine`() {
        val host = PresentationInputHost()
        host.attachWithFakeSource(display(7L))
        val fakeSource = host.fakeSource!!
        fakeSource.show()
        val original = fakeSource.originalWindowCallback
        // La chaîne est posée PAR-DESSUS l'original — jamais un remplacement silencieux.
        assertTrue(original !== fakeSource.currentWindowCallback)
        fakeSource.simulateDisplayRemoved()
        // La chaîne est retirée : l'instance d'origine EXACTE est remise — pas une autre.
        assertSame(original, fakeSource.currentWindowCallback)
    }

    /**
     * La discipline never-throw du collecteur `mediaWatch` (phase 11, durcissement de la
     * vague finale) : la fabrique de chaîne et sa pose vivent DANS le collecteur d'état du
     * média (Unconfined) — une fabrique lançante ne doit PAS tuer la coroutine
     * d'observation, sinon le `Removed` d'un écran retiré ne serait plus jamais observé :
     * session jamais terminale, réservation silencieusement tenue. L'échec est absorbé,
     * SIGNALÉ sur le canal de diagnostic, le fait câblé reste `false` (aucune chaîne
     * posée) — et le retrait d'écran qui suit termine la session et rend les deux clés.
     */
    @Test
    fun `une fabrique de chaine lancante ne tue pas le collecteur du media`() {
        val host = PresentationInputHost()
        val boom = IllegalStateException("input chain factory failed")
        val session = host.attachWithThrowingChain(display(7L), boom)
        val fakeSource = host.fakeSource!!
        // Le premier show livre `Shown` au collecteur : la fabrique jette DANS le collect.
        fakeSource.show()
        // Absorbé : RIEN n'est posé sur la fenêtre (le fait câblé reste `false`), et
        // l'échec est signalé — jamais avalé en silence.
        assertNull(fakeSource.originalWindowCallback)
        assertTrue(AndroidAttachDiagnostics.failures().any { it === boom })
        // Le retrait d'écran est ENCORE observé : session TERMINALE, réservations libérées.
        fakeSource.simulateDisplayRemoved()
        assertTrue(session.state.value is SessionState.Terminated)
        assertTrue(host.registry.isEmpty())
    }

    // ------------------------------------------------------------------ outillage

    /** Un écran identifié par sa clé — la displayId des faits, sans type SDK. */
    private data class FakeDisplay(val key: Long)

    private fun display(displayKey: Long): FakeDisplay = FakeDisplay(displayKey)

    /** Le scope capturé par l'application suspendue — polling borné, jamais une lecture en course. */
    private fun awaitCapturedScope(captured: AtomicReference<KadreScope?>): KadreScope {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            captured.get()?.let { return it }
            Thread.sleep(10L)
        }
        error("the session's KadreScope was never captured")
    }

    /**
     * Les caps d'entrée de LA session — polling borné sur l'état publié du SurfaceInput :
     * le stimulus structurel traverse le réducteur runtime (coroutine de session) avant la
     * publication, l'attente est bornée, jamais un sleep aveugle.
     */
    private fun awaitInputCapability(
        scope: KadreScope,
        predicate: (InputCapabilities) -> Boolean,
    ): InputCapabilities {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            val caps = scope.primarySurface.value?.input?.state?.value?.capabilities
            if (caps != null && predicate(caps)) return caps
            Thread.sleep(10L)
        }
        error("the input capabilities never satisfied the predicate")
    }

    /** L'observation attendue d'une touche pressée — les primitives du dispatch de test. */
    private fun keyObserved(nativeKeyCode: Int) = AndroidInputObservation.Key(
        nativeKeyCode = nativeKeyCode,
        repeat = false,
        metaState = 0,
        unicodeChar = null,
        isSystemKey = false,
        keyState = KeyState.Pressed,
    )

    /**
     * La fenêtre du média — le jeton opaque que [`AndroidPresentationSource.windowToken`]
     * rend après le premier show réussi. Le slot callback vit en `Any?` : la paire get/set
     * est la seule chose que la chaîne réelle demande à une fenêtre.
     */
    private class FakePresentationWindow {
        /** Le callback POSÉ — l'original à la construction, la chaîne après install. */
        var currentCallback: Any? = Any()

        /** Le callback d'origine capturé À l'install — null tant que la chaîne n'est pas posée. */
        var originalCallback: Any? = null

        var installCount: Int = 0
            private set

        /**
         * Le focus du contentView de la présentation — OBSERVÉ par la porte de focus du cœur
         * réel au dispatch, jamais supposé (une présentation ne prend pas le focus par décret).
         */
        var viewHasFocus: Boolean = true

        fun installChain(chain: Any) {
            originalCallback = currentCallback
            currentCallback = chain
            installCount += 1
        }

        fun restoreChain(chain: Any) {
            // Une callback étrangère n'est jamais écrasée — la garde du forwarder réel.
            if (currentCallback !== chain) return
            currentCallback = originalCallback
        }
    }

    /**
     * La chaîne fake — le cœur RÉEL [AndroidInputWindowCallback] (SDK-free) câblé au câblage
     * de session que la couture lui remet : le dispatch de test traverse exactement la porte
     * de focus et l'ordre observe-puis-décide de la production. Le journal (le patron
     * `Dispatch` des tests d'input existants) enregistre l'observation AVANT de nourrir le
     * funnel — c'est le journal d'entrée de CETTE session.
     */
    private class FakeInputChain(
        private val window: FakePresentationWindow,
        private val wiring: AndroidSessionInputWiring,
        private val journal: MutableList<AndroidInputObservation>,
    ) : AndroidWindowInputChain {
        private var installed = false

        private val core = AndroidInputWindowCallback(
            isTargetFocused = { window.viewHasFocus },
            onObservation = { observation ->
                journal += observation
                wiring.onObservation(observation)
            },
            shouldConsumeKey = { nativeKeyCode -> wiring.shouldConsumeKey(nativeKeyCode) },
            forwardKey = { _, _, _ -> true },
            forwardFocusLost = {},
        )

        override fun install() {
            window.installChain(this)
            installed = true
        }

        override fun restoreOrReport() {
            if (!installed) return
            installed = false
            window.restoreChain(this)
        }

        /** Une pression de touche à la fenêtre — la porte d'entrée du dispatch réel. */
        fun dispatchKey(nativeKeyCode: Int): Boolean = core.dispatchKeyEvent(
            nativeKeyCode = nativeKeyCode,
            repeat = false,
            metaState = 0,
            unicodeChar = null,
            isSystemKey = false,
            keyState = KeyState.Pressed,
        )
    }

    /**
     * Le fake du seam Task 1 — l'état du média piloté sans android.jar, sa fenêtre en plus :
     * le jeton n'existe qu'après le premier show réussi (le miroir de la machine réelle, qui
     * retient le jeton une seule fois sous le verrou du média).
     */
    private class FakePresentationInputSource(
        override val facts: AndroidPresentationFacts,
        private val window: FakePresentationWindow,
    ) : AndroidPresentationSource {
        private val mutableState = MutableStateFlow<AndroidPresentationState>(AndroidPresentationState.Hidden)
        override val state: StateFlow<AndroidPresentationState> = mutableState.asStateFlow()

        private var shownOnce = false

        override fun show(): KadreResult<Unit> {
            if (mutableState.value is AndroidPresentationState.Removed) {
                return KadreResult.Failure(
                    KadreFailure.PlatformFailure(KadrePlatform.Android, "presentation", "display-removed"),
                )
            }
            // Le jeton d'abord : le collecteur d'état de la couture livre le fait SHOWN au
            // thread qui l'émet (Unconfined) et lit le jeton DANS ce dispatch.
            shownOnce = true
            mutableState.value = AndroidPresentationState.Shown(facts.displayKey)
            return KadreResult.Success(Unit)
        }

        override fun hide() {
            mutableState.value = AndroidPresentationState.Hidden
        }

        override fun windowToken(): Any? = if (shownOnce) window else null

        fun simulateDisplayRemoved() {
            mutableState.value = AndroidPresentationState.Removed(facts.displayKey)
        }

        override fun close() = Unit

        // ---- Les points d'observation de la fenêtre du média. ----

        val windowCallbackInstallCount: Int get() = window.installCount
        val originalWindowCallback: Any? get() = window.originalCallback
        val currentWindowCallback: Any? get() = window.currentCallback

        /** Dispatch une touche à la fenêtre — à la chaîne posée, via le cœur réel. */
        fun dispatchKeyToPresentationWindow(nativeKeyCode: Int): Boolean =
            (window.currentCallback as? FakeInputChain)?.dispatchKey(nativeKeyCode) ?: false
    }

    /**
     * Le host de test — le modèle du `SecondaryDisplayHost` Task 2 : registre frais, scope
     * Unconfined (le collecteur d'état du média livre le fait au thread qui l'émet), et la
     * couture input injectée. L'hôte attache à CREATED — rien n'est montré à l'attach, les
     * tests pilotent le média par la source DIRECTEMENT (le premier show est explicite).
     */
    private class PresentationInputHost {
        val registry = AndroidViewOwnership()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var fakeSource: FakePresentationInputSource? = null
            private set

        /** Un journal d'observations par session — rempli par LA chaîne de CETTE session. */
        private val journals = mutableMapOf<KadreSession, MutableList<AndroidInputObservation>>()

        private fun facts(displayKey: Long) = AndroidPresentationFacts(
            displayKey = displayKey,
            realWidth = 1920,
            realHeight = 1080,
            scaleFactor = 2.0,
        )

        fun attachWithFakeSource(display: FakeDisplay, view: Any = Any()): KadreSession {
            val journal = mutableListOf<AndroidInputObservation>()
            val window = FakePresentationWindow()
            val source = FakePresentationInputSource(facts(display.key), window)
            fakeSource = source
            val session = attach(display, view, source, hangingApplication()) { windowToken, wiring ->
                (windowToken as? FakePresentationWindow)?.let { FakeInputChain(it, wiring, journal) }
            }.session()
            journals[session] = journal
            return session
        }

        /**
         * L'attach du RATTRAPAGE (phase 11, Task 4) : un hôte RESUMED dont l'étape
         * d'installation reproduit le rattrapage de l'observer lifecycle SDK — `addObserver`
         * sur un lifecycle RESUMED livre ON_CREATE puis ON_START au nouvel observer, et ce
         * START montre le média PENDANT l'install, avant `controller.attach` : la chaîne
         * d'entrée et son fait câblé précèdent la configuration de session — l'ORDRE exact
         * du device, celui que le relevé de la phase 11 a pris en défaut.
         */
        fun attachWithCatchupShow(
            display: FakeDisplay,
            captured: AtomicReference<KadreScope?>,
        ): KadreSession {
            val journal = mutableListOf<AndroidInputObservation>()
            val window = FakePresentationWindow()
            val source = FakePresentationInputSource(facts(display.key), window)
            fakeSource = source
            val factory = KadreApplicationFactory {
                KadreApplication {
                    captured.set(this)
                    awaitCancellation()
                }
            }
            val session = attach(
                display,
                Any(),
                source,
                factory,
                ownerState = 4, // RESUMED : le rattrapage de l'observer montre le média
                installHostLifecycle = { lifecycle ->
                    // Le rattrapage du LifecycleRegistry SDK : ON_CREATE puis ON_START livrés
                    // au nouvel observer DANS l'étape d'installation — le START montre le média
                    // avant `controller.attach` (l'ordre exact du device).
                    lifecycle.dispatch(AndroidHostEvent.LifecycleCreated)
                    lifecycle.dispatch(AndroidHostEvent.LifecycleStarted)
                },
                inputChainFactory = { windowToken, wiring ->
                    (windowToken as? FakePresentationWindow)?.let { FakeInputChain(it, wiring, journal) }
                },
            ).session()
            journals[session] = journal
            return session
        }

        /**
         * L'attach de la fabrique lançante : la couture input jette au premier `Shown` —
         * DANS le collecteur `mediaWatch`, sur le thread qui émet le fait (Unconfined).
         * Le durcissement never-throw du collect est l'objet du test qui pilote ce
         * attach : la fabrique lançante ne doit PAS tuer l'observation du média.
         */
        fun attachWithThrowingChain(display: FakeDisplay, boom: Throwable): KadreSession {
            val window = FakePresentationWindow()
            val source = FakePresentationInputSource(facts(display.key), window)
            fakeSource = source
            return attach(display, Any(), source, hangingApplication()) { _, _ -> throw boom }.session()
        }

        /**
         * La session « principale » du cas cross-talk — la MÊME couture, un autre écran, SON
         * journal : la forme Activity n'est pas conduisible sur hôte (fenêtre SDK), la
         * propriété épinglée est le routage par session du câblage que la composition
         * construit — le journal de CETTE session en est le témoin muet attendu.
         */
        fun attachSurrogateMainSession(
            mainView: Any,
            factory: KadreApplicationFactory,
        ): KadreSession {
            val journal = mutableListOf<AndroidInputObservation>()
            val window = FakePresentationWindow()
            val source = FakePresentationInputSource(facts(displayKey = 0L), window)
            val session = attach(FakeDisplay(0L), mainView, source, factory) { windowToken, wiring ->
                (windowToken as? FakePresentationWindow)?.let { FakeInputChain(it, wiring, journal) }
            }.session()
            journals[session] = journal
            return session
        }

        private fun attach(
            display: FakeDisplay,
            view: Any,
            source: FakePresentationInputSource,
            factory: KadreApplicationFactory,
            ownerState: Int = 2,
            installHostLifecycle: (AndroidPresentationLifecycle) -> Unit = { },
            inputChainFactory: (Any?, AndroidSessionInputWiring) -> AndroidWindowInputChain?,
        ): KadreResult<KadreSession> = AndroidHostSession.attachSecondaryDisplay(
            request = AndroidPresentationAttachRequest(
                isMainThread = true,
                contentView = view,
                isContentViewAttachedToWindow = false,
                displayKey = display.key,
                isDisplayResolvable = true,
                ownerState = ownerState, // CREATED par défaut : le média attend le premier START
                parentScopeJob = scope.coroutineContext[Job],
                parentScopeJobIsActive = true,
            ),
            parentScope = scope,
            applicationFactory = factory,
            policy = KadrePolicies.Default,
            sourceFactory = { source },
            installHostLifecycle = installHostLifecycle, // le pilotage lifecycle n'est pas conduit ici
            registry = registry,
            displayPortFactory = { null },
            inputChainFactory = inputChainFactory,
        )

        private fun KadreResult<KadreSession>.session(): KadreSession =
            assertIs<KadreResult.Success<KadreSession>>(this).value

        fun castInputJournal(session: KadreSession): List<AndroidInputObservation> = inputJournal(session)

        fun mainInputJournal(session: KadreSession): List<AndroidInputObservation> = inputJournal(session)

        private fun inputJournal(session: KadreSession): List<AndroidInputObservation> =
            checkNotNull(journals[session]) { "no input journal for session ${session.id}" }
    }
}

/** L'application qui ne termine pas — la session reste `Running` pour la vie du test. */
private fun hangingApplication(): KadreApplicationFactory = KadreApplicationFactory {
    KadreApplication { awaitCancellation() }
}
