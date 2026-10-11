package org.graphiks.kadre.platform.android

import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayId
import org.graphiks.kadre.display.DisplayMode
import org.graphiks.kadre.display.DisplayModeId
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionActionOutcome
import org.graphiks.kadre.interaction.InteractionContext
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.interaction.InteractionRegistration
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.EventDeliveryPolicy
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.RawInputDeliveryPolicy
import org.graphiks.kadre.policy.RawInputOverflowAction
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.FullscreenMode
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Le moteur d'interactions transitoires Android (phase 4, tâche 4) — les six cas du plan, joués
 * par la voie de PRODUCTION : l'[AndroidHostSurface.installSessionConfiguration] construit
 * l'[AndroidInteractionEngine] (le miroir web `WebHostSession.kt:1002-1011`), la livraison
 * d'entrée ([AndroidInputDelivery]) dérive les triggers des MÊMES stimuli que le chemin ordinaire
 * et les dispatche AVANT l'accept de chacun (l'ordre AppKit, `DESIGN.md:983-989` — le miroir web
 * « dans le callback DOM, avant le stimulus ordinaire »), et l'override
 * `installInteractionHandler` délègue au moteur commun ([org.graphiks.kadre.internal.runtime.
 * RuntimeInteractionHandler] — le runtime porte le token single-use, la sérialisation et la
 * règle d'unicité).
 *
 * L'ensemble ANNONCÉ est la décision de construction du contrôleur : `{EnterFullscreen,
 * ExitFullscreen}` quand le seam natif de la fenêtre (tâche 1) est câblé, `{LockPointer,
 * UnlockPointer}` en PLUS à partir de l'API 26 (le niveau est un fait de construction).
 * `OpenWindow` n'est PAS annoncé (pas de seconde Activity — roadmap :190),
 * `BeginWindowMove`/`BeginWindowResize` non plus (aucune primitive Activity),
 * `AcceptDrop` non plus (le drop est phase 6). Le mapping `invokeNative` :
 * `EnterFullscreen`/`ExitFullscreen` → le seam fenêtre T1 (`Now` — le readback d'insets confirme
 * ou la demande est rapportée `TemporarilyUnavailable`, le mot de la tâche 1) ;
 * `LockPointer`/`UnlockPointer` → le seam de capture T3 (`Now` — le commit de la capture reste
 * confirmé par le LISTENER du SDK, l'outcome d'interaction porte l'admission de la requête) ;
 * toute action non annoncée → `Now(Failure(Unsupported(Interaction)))` — et l'admission du
 * contexte la refuse AVANT tout appel natif.
 *
 * Aucun type SDK constructible n'est instancié : les deux seams sont des recorders (les tests
 * hôtes n'ont ni Activity ni View ni pointeur), les observations sont copiées à la main.
 */
@OptIn(DelicateKadreApi::class)
class AndroidInteractionEngineTest {

    private var sequence = 0L

    /** La source de stamps de session — séquentielle, la même instance pour toute une surface. */
    private fun stampSource(): () -> EventStamp {
        val started = sequence
        return {
            sequence += 1
            EventStamp(SessionSequence(sequence), SessionInstant((sequence - started).nanoseconds), null)
        }
    }

    /**
     * Le recorder du seam fenêtre (tâche 1) — chaque demande est retenue, et le readback des
     * barres système est la vérité que le test contrôle : `setBorderlessFullscreen(true)` fait
     * disparaître les barres (le readback confirme), comme l'insets controller le ferait.
     */
    private class RecordingWindowNative : AndroidWindowNative {
        val calls = mutableListOf<String>()
        var systemBarVisible: Boolean = true

        override fun setTitle(title: String) {
            calls += "title"
        }

        override fun title(): String = ""

        override fun setContentProtection(enabled: Boolean) {
            calls += "content-protection"
        }

        override fun isContentProtectionEnabled(): Boolean = false

        override fun setBorderlessFullscreen(enabled: Boolean) {
            calls += if (enabled) "fullscreen-on" else "fullscreen-off"
            systemBarVisible = !enabled
        }

        override fun isSystemBarVisible(): Boolean = systemBarVisible
    }

    /**
     * Le recorder du seam de capture (tâche 3) — le listener posé par le seam à sa construction
     * est le chemin par lequel le test simule la réponse du SDK.
     */
    private class RecordingCaptureNative : AndroidPointerCapture.Native {
        val calls = mutableListOf<String>()
        private var recordedListener: ((hasCapture: Boolean) -> Unit)? = null

        val listener: ((hasCapture: Boolean) -> Unit)? get() = recordedListener

        override fun requestCapture() {
            calls += "request"
        }

        override fun releaseCapture() {
            calls += "release"
        }

        override fun setListener(listener: ((hasCapture: Boolean) -> Unit)?) {
            recordedListener = listener
        }
    }

    /** La surface sous test : les DEUX seams recorder, le niveau d'API injecté. */
    private fun surface(
        window: RecordingWindowNative? = RecordingWindowNative(),
        capture: RecordingCaptureNative? = RecordingCaptureNative(),
        apiLevel: Int = 26,
    ): AndroidHostSurface = AndroidHostSurface(
        id = RuntimeProcessIds.nextSurfaceId(),
        initialState = testSurfaceState(),
        frameClock = { EngineInertFrameClock },
        apiLevel = apiLevel,
        pointerCaptureNative = capture,
        windowNative = window,
    )

    /**
     * L'installation de la configuration de session — les dix paramètres du seam. Le moteur
     * d'interactions y est construit (comme le réducteur d'entrée) ; les failures de session
     * sont retenues pour le cas qui les épingle.
     */
    private fun install(
        on: AndroidHostSurface,
        sessionFailures: MutableList<KadreFailure> = mutableListOf(),
    ): MutableList<KadreFailure> {
        val discrete = EventDeliveryPolicy(
            ingressCapacity = 64,
            collectorCapacity = 16,
            ingressOverflow = IngressOverflowAction.CloseSource,
            collectorOverflow = CollectorOverflowAction.CancelSlowCollector,
        )
        on.installSessionConfiguration(
            deliveryPolicy = WindowDeliveryPolicy(
                discreteEvents = discrete,
                geometryChanges = ContinuousDelivery.Coalesced,
                redrawRequests = ContinuousDelivery.Latest,
            ),
            inputDeliveryPolicy = InputDeliveryPolicy(
                discreteEvents = discrete,
                pointerMotion = ContinuousDelivery.Coalesced,
                touchMotion = ContinuousDelivery.Coalesced,
                scroll = ContinuousDelivery.Coalesced,
                gestureChanges = ContinuousDelivery.Coalesced,
                gamepadChanges = ContinuousDelivery.Latest,
                rawInput = RawInputDeliveryPolicy(16, RawInputOverflowAction.DropOldestAndReport),
            ),
            source = stampSource(),
            sessionFailureHandler = { sessionFailures += it },
            collectorAllocator = RuntimeEventCollectorAllocator(64),
            maxCollectorsPerFlow = 4,
            resources = KadrePolicies.Default.resources,
            dropTransferScope = null,
            diagnostics = {},
            rawInputPort = null,
        )
        return sessionFailures
    }

    /** Le collecteur d'événements d'entrée — le miroir du harnais de la delivery. */
    private class InputCollector(scope: CoroutineScope, surface: AndroidHostSurface) {
        val channel = Channel<InputEvent>(Channel.UNLIMITED)

        private val job = scope.launch { surface.input.events.collect { channel.send(it) } }

        suspend fun settle() {
            repeat(8) { yield() }
        }

        fun stop() {
            job.cancel()
        }

        suspend fun next(): InputEvent = withTimeout(5_000) { channel.receive() }

        suspend fun drainCount(): Int {
            var count = 0
            while (true) {
                val extra = channel.tryReceive().getOrNull() ?: break
                count += 1
                assertFalse(true, "unexpected event: $extra")
            }
            return count
        }
    }

    private lateinit var surfaceUnderCollect: AndroidHostSurface

    private suspend fun CoroutineScope.collectInput(): InputCollector {
        val collector = InputCollector(this, surfaceUnderCollect)
        collector.settle()
        return collector
    }

    // -----------------------------------------------------------------------
    // Cas 1 — le token est single-use, et l'interaction précède le stimulus ordinaire
    // -----------------------------------------------------------------------

    @Test
    fun tokenSingleUseAndOrdering() = runBlocking {
        val surface = surface()
        surfaceUnderCollect = surface
        install(on = surface)
        surface.observationInstalled(inputWired = true)
        val input = collectInput()

        val order = mutableListOf<String>()
        var requestId: Any? = null
        var consumedFailure: KadreFailure? = null
        var retainedContext: InteractionContext? = null
        val registration = assertIs<KadreResult.Success<InteractionRegistration>>(
            surface.installInteractionHandler(InteractionHandler { context, event ->
                order += "handler"
                assertIs<InteractionEvent.TouchStarted>(event)
                // Le premier `request` consomme le token : `UnlockPointer` est annoncé (API 26+).
                requestId = assertIs<KadreResult.Success<*>>(
                    context.request(InteractionAction.UnlockPointer),
                ).value
                // Le second `request` du MÊME callback : le token est déjà dépensé.
                consumedFailure = assertIs<KadreResult.Failure>(
                    context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless)),
                ).reason
                retainedContext = context
            }),
        ).value

        // Le collecteur d'outcomes est en place AVANT le dispatch : le `Committed` de la
        // requête est publié dans le `finally` du dispatch.
        val outcomes = async(start = CoroutineStart.UNDISPATCHED) {
            registration.outcomes.take(1).toList()
        }
        val inputEvent = async(start = CoroutineStart.UNDISPATCHED) {
            input.next().also { order += "input" }
        }

        // Le trigger : un DOWN tactile (lane contact) — l'interaction est dispatchée AVANT que
        // le stimulus ordinaire `TouchChanged(Started)` n'atteigne `surfaceInput.events`.
        surface.onMotionObservation(
            motion(MotionEvent.ACTION_DOWN, pointers = listOf(Spec(0, 30f, 40f))),
            width = 100,
            height = 100,
        )

        val touch = assertIs<InputEvent.TouchChanged>(inputEvent.await())
        assertEquals(TouchPhase.Started, touch.phase)
        assertEquals(listOf("handler", "input"), order, "the handler runs before the ordinary stimulus")

        // L'outcome de la requête `Now` : commis, corrélé au requestId alloué.
        val committed = assertIs<InteractionActionOutcome.Committed>(outcomes.await().single())
        assertEquals(requestId, committed.requestId)

        // Le second `request` du callback : refusé Consumed.
        assertEquals(
            KadreFailure.InteractionRequired(InteractionFailureReason.Consumed),
            consumedFailure,
        )
        // Après le retour du handler, le token est mort : le contexte retenu répond Expired
        // (le mot du runtime pour un token invalide après retour — cf. les tests du runtime).
        assertEquals(
            KadreFailure.InteractionRequired(InteractionFailureReason.Expired),
            assertIs<KadreResult.Failure>(
                checkNotNull(retainedContext).request(InteractionAction.UnlockPointer),
            ).reason,
        )
        assertEquals(0, input.drainCount())
        input.stop()
    }

    // -----------------------------------------------------------------------
    // Cas 2 — les actions non annoncées sont refusées Unsupported(Interaction), zéro natif
    // -----------------------------------------------------------------------

    @Test
    fun unadvertisedActionsAreRefusedUnsupported() = runBlocking {
        val window = RecordingWindowNative()
        val capture = RecordingCaptureNative()
        val surface = surface(window = window, capture = capture)
        surfaceUnderCollect = surface
        install(on = surface)
        surface.observationInstalled(inputWired = true)

        var refusals: List<KadreFailure> = emptyList()
        surface.installInteractionHandler(InteractionHandler { context, _ ->
            refusals = listOf(
                // Pas de seconde Activity (roadmap :190) : OpenWindow n'est pas annoncé.
                assertIs<KadreResult.Failure>(context.request(InteractionAction.OpenWindow())).reason,
                // Aucune primitive de déplacement de fenêtre Activity.
                assertIs<KadreResult.Failure>(context.request(InteractionAction.BeginWindowMove)).reason,
            )
        })

        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_A))

        assertEquals(
            listOf(
                KadreFailure.Unsupported(KadreOperation.Interaction),
                KadreFailure.Unsupported(KadreOperation.Interaction),
            ),
            refusals,
            "the admission refuses the un-advertised actions before any native call",
        )
        // Zéro appel natif : le refus d'admission précède `invokeNative`.
        assertEquals(emptyList<String>(), window.calls)
        assertEquals(emptyList<String>(), capture.calls)
    }

    // -----------------------------------------------------------------------
    // Cas 3 — les actions annoncées atteignent les seams natifs T1 et T3
    // -----------------------------------------------------------------------

    @Test
    fun advertisedActionsReachTheNativeSeam() = runBlocking {
        val window = RecordingWindowNative()
        val capture = RecordingCaptureNative()
        val surface = surface(window = window, capture = capture)
        install(on = surface)
        surface.observationInstalled(inputWired = true)

        val registration = assertIs<KadreResult.Success<InteractionRegistration>>(
            surface.installInteractionHandler(InteractionHandler { context, event ->
                when (event) {
                    // Une pression bouton souris (lane pointeur) → EnterFullscreen(Borderless).
                    is InteractionEvent.PointerPressed -> context.request(
                        InteractionAction.EnterFullscreen(FullscreenMode.Borderless),
                    )

                    // Un key down → LockPointer(Locked) — le seul mode que l'action prend (miroir web).
                    is InteractionEvent.KeyPressed -> context.request(
                        InteractionAction.LockPointer(PointerCaptureMode.Locked),
                    )

                    else -> Unit
                }
            }),
        ).value
        val outcomes = async(start = CoroutineStart.UNDISPATCHED) {
            registration.outcomes.take(2).toList()
        }

        // Le dispatch pointeur : la demande plein écran part, le readback des barres confirme.
        surface.onMotionObservation(
            motion(
                MotionEvent.ACTION_BUTTON_PRESS,
                pointers = listOf(Spec(0, 10f, 10f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
                actionButton = MotionEvent.BUTTON_PRIMARY,
            ),
            width = 100,
            height = 100,
        )
        assertTrue(window.calls.contains("fullscreen-on"), "the T1 fullscreen seam is invoked")
        assertTrue(!window.isSystemBarVisible(), "the readback confirms the commit synchronously")

        // Le dispatch clavier : la demande de verrou part, la primitive de capture est posée.
        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_A))
        assertTrue(capture.calls.contains("request"), "the T3 pointer-capture seam is invoked")

        // Les deux requêtes `Now` sont commises (la fullscreen confirmée au readback, le verrou
        // admis — son commit à LUI reste celle du listener).
        assertEquals(2, outcomes.await().count { it is InteractionActionOutcome.Committed })

        // La confirmation du listener commite l'état publié (le mot Task 3 : le commit vit au
        // listener) — la capture d'interaction suit la MÊME machine d'état que le champ.
        capture.listener?.invoke(true)
        assertEquals(PointerCaptureMode.Confined, surface.state.value.pointerCapture)
    }

    /** Le verrou en interaction suit la normalisation de mode du web, et `Exclusive` sans primitive. */
    @Test
    fun lockPointerModeAndExclusiveFullscreenAreRefusedBeforeAnyNativeCall() = runBlocking {
        val window = RecordingWindowNative()
        val capture = RecordingCaptureNative()
        val surface = surface(window = window, capture = capture)
        install(on = surface)
        surface.observationInstalled(inputWired = true)

        // La file des demandes : une par dispatch (le token est single-use).
        val requests = ArrayDeque(
            listOf(
                InteractionAction.LockPointer(PointerCaptureMode.Confined),
                InteractionAction.EnterFullscreen(
                    FullscreenMode.Exclusive(
                        DisplayId(0),
                        DisplayMode(DisplayModeId(0), PhysicalSize(1, 1), null, null),
                    ),
                ),
            ),
        )
        val registration = assertIs<KadreResult.Success<InteractionRegistration>>(
            surface.installInteractionHandler(InteractionHandler { context, _ ->
                context.request(requests.removeFirst())
            }),
        ).value
        val outcomes = async(start = CoroutineStart.UNDISPATCHED) {
            registration.outcomes.take(2).toList()
        }

        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_A))
        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_B))

        // `Confined` reste le champ de la phase 4 — le verrou d'interaction ne prend que
        // `Locked` (le miroir `normaliseLockPointerMode` web) ; `Exclusive` n'a pas de primitive.
        val rejected = outcomes.await().map { assertIs<InteractionActionOutcome.Rejected>(it).failure }
        assertEquals(
            listOf(
                KadreFailure.InvalidRequest("action.mode"),
                KadreFailure.Unsupported(KadreOperation.Interaction),
            ),
            rejected,
        )
        // Zéro primitive : le `Confined` comme l'`Exclusive` sont refusés avant tout appel.
        assertEquals(emptyList<String>(), capture.calls)
        assertEquals(emptyList<String>(), window.calls)
    }

    @Test
    fun unlockPointerReachesTheReleaseSeam() = runBlocking {
        val capture = RecordingCaptureNative()
        val surface = surface(capture = capture)
        install(on = surface)
        surface.observationInstalled(inputWired = true)

        val registration = assertIs<KadreResult.Success<InteractionRegistration>>(
            surface.installInteractionHandler(InteractionHandler { context, _ ->
                context.request(InteractionAction.UnlockPointer)
            }),
        ).value
        val outcomes = async(start = CoroutineStart.UNDISPATCHED) {
            registration.outcomes.take(1).toList()
        }

        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_A))
        assertTrue(capture.calls.contains("release"), "the T3 release seam is invoked")
        assertIs<InteractionActionOutcome.Committed>(outcomes.await().single())

        // Le retour à `None` publié suit la perte que le listener rapporte (le commit vit au
        // listener, le même canal que le champ) — le pont du test simule le rapport du SDK.
        capture.listener?.invoke(false)
        assertEquals(PointerCaptureMode.None, surface.state.value.pointerCapture)
    }

    @Test
    fun belowApi26LockPointerIsNotAdvertised() = runBlocking {
        val capture = RecordingCaptureNative()
        val surface = surface(window = RecordingWindowNative(), capture = capture, apiLevel = 24)
        surfaceUnderCollect = surface
        install(on = surface)
        surface.observationInstalled(inputWired = true)

        val supported = assertIs<Capability.Supported<Set<InteractionKind>>>(
            surface.capabilities.value.handlerInteractions,
        )
        // Sous l'API 26 : les fullscreen SEULS — LockPointer/UnlockPointer ne sont pas annoncés.
        assertEquals(
            setOf(InteractionKind.EnterFullscreen, InteractionKind.ExitFullscreen),
            supported.constraints,
        )

        var refusal: KadreFailure? = null
        surface.installInteractionHandler(InteractionHandler { context, _ ->
            refusal = assertIs<KadreResult.Failure>(
                context.request(InteractionAction.LockPointer(PointerCaptureMode.Locked)),
            ).reason
        })
        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_A))

        assertEquals(
            KadreFailure.Unsupported(KadreOperation.Interaction),
            refusal,
            "an un-advertised action is refused by the admission, zero native calls",
        )
        assertEquals(emptyList<String>(), capture.calls)
    }

    // -----------------------------------------------------------------------
    // Cas 4 — un second handler pendant qu'un premier vit : AlreadyInUse(Interaction)
    // -----------------------------------------------------------------------

    @Test
    fun secondHandlerIsAlreadyInUse() = runBlocking {
        val surface = surface()
        install(on = surface)
        surface.observationInstalled(inputWired = true)

        val first = assertIs<KadreResult.Success<InteractionRegistration>>(
            surface.installInteractionHandler(InteractionHandler { _, _ -> }),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Interaction)),
            surface.installInteractionHandler(InteractionHandler { _, _ -> }),
            "one handler per surface — the engine's own rule",
        )
        // La registration fermée libère la place (le mot du runtime, miroir de ses tests).
        first.value.close()
        val reinstalled = surface.installInteractionHandler(InteractionHandler { _, _ -> })
        assertTrue(reinstalled is KadreResult.Success<*>, "a closed registration frees the slot")
    }

    // -----------------------------------------------------------------------
    // Cas 5 — un handler qui jette : ApplicationFailure par le chemin de session du runtime
    // -----------------------------------------------------------------------

    @Test
    fun handlerExceptionFailsSessionThroughTheRuntime() = runBlocking {
        val surface = surface()
        val sessionFailures = install(on = surface)
        surface.observationInstalled(inputWired = true)
        surface.installInteractionHandler(InteractionHandler { _, _ ->
            throw IllegalStateException("the handler's own bug")
        })

        // Le dispatch capture l'exception AVANT la frontière native, la reporte et échoue la
        // session (DESIGN §9.6 :978-981) — jamais la delivery : le stimulus ordinaire continue.
        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_A))

        assertEquals(
            listOf<KadreFailure>(KadreFailure.ApplicationFailure),
            sessionFailures,
            "the handler exception fails the session through the runtime's sessionFailureHandler",
        )
    }

    // -----------------------------------------------------------------------
    // Cas 6 — la capability suit l'installation : pre-install refusée, annoncée câblée, retirée
    // -----------------------------------------------------------------------

    @Test
    fun capabilityFollowsInstall() = runBlocking {
        val surface = surface()
        install(on = surface)

        // Pre-install : le refus d'origine — personne ne peut encore installer.
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler)),
            surface.capabilities.value.handlerInteractions,
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler)),
            surface.installInteractionHandler(InteractionHandler { _, _ -> }),
            "pre-install, the override refuses with the word its capability describes",
        )
        // armedInteractions reste à son refus : AUCUNE plateforme n'arme (le miroir web).
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.ArmInteraction)),
            surface.capabilities.value.armedInteractions,
        )

        // Câblé : l'annonce exacte du moteur — les quatre actions à l'API 26+.
        surface.observationInstalled(inputWired = true)
        assertEquals(
            Capability.Supported(
                setOf(
                    InteractionKind.EnterFullscreen,
                    InteractionKind.ExitFullscreen,
                    InteractionKind.LockPointer,
                    InteractionKind.UnlockPointer,
                ),
                FeatureAvailability.Available,
            ),
            surface.capabilities.value.handlerInteractions,
        )

        // SANS observateur câblé : pas d'autorité d'interaction — l'annonce ne revient pas.
        val unwired = surface()
        install(on = unwired)
        unwired.observationInstalled(inputWired = false)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler)),
            unwired.capabilities.value.handlerInteractions,
        )

        // Au close : l'annonce est retirée.
        surface.close()
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler)),
            surface.capabilities.value.handlerInteractions,
        )
    }

    // -----------------------------------------------------------------------
    // Outils de test — observations copiées construites à la main
    // -----------------------------------------------------------------------

    private fun keyObservation(code: Int): AndroidInputObservation.Key =
        AndroidInputObservation.Key(
            nativeKeyCode = code,
            repeat = false,
            metaState = 0,
            unicodeChar = null,
            isSystemKey = false,
            keyState = KeyState.Pressed,
        )

    /** Un pointeur d'observation : les primitives telles que la couture les lit au dispatch. */
    private data class Spec(
        val id: Int,
        val x: Float,
        val y: Float,
        val tool: Int = MotionEvent.TOOL_TYPE_FINGER,
    )

    private fun motion(
        actionMasked: Int,
        pointers: List<Spec>,
        actionButton: Int = 0,
    ): AndroidMotionObservation = AndroidMotionObservation(
        actionMasked = actionMasked,
        actionIndex = 0,
        pointerCount = pointers.size,
        pointerIds = pointers.map { it.id }.toIntArray(),
        xs = pointers.map { it.x }.toFloatArray(),
        ys = pointers.map { it.y }.toFloatArray(),
        pressures = pointers.map { null }.toTypedArray(),
        toolTypes = pointers.map { it.tool }.toIntArray(),
        buttons = actionButton,
        actionButton = actionButton,
        axisHscroll = 0f,
        axisVscroll = 0f,
        axisXrelative = 0f,
        axisYrelative = 0f,
        deviceId = 0,
        metaState = 0,
    )
}

/** L'état initial de surface du test — le même instantané honnête que les autres tests hôtes. */
private fun testSurfaceState(): SurfaceState = SurfaceState(
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

/** La clock inerte de CE test : les frames y sont posées et n'y courent jamais (aucun SDK). */
private object EngineInertFrameClock : AndroidRedrawScheduler.FrameClock {
    override fun schedule(frame: () -> Unit) = Unit
    override fun cancel() = Unit
}
