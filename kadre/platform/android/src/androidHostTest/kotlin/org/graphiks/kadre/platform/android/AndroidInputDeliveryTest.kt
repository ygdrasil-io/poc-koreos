package org.graphiks.kadre.platform.android

import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
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
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * La livraison d'entrée du runtime (Task 3) : les six cas épinglés du plan, joués par la voie
 * de PRODUCTION — l'[AndroidHostSurface.installSessionConfiguration] construit le réducteur
 * partagé (`RuntimeSurfaceInput`, miroir exact de la construction web `WebHostSession.kt:967`)
 * et son pont d'observation [AndroidInputDelivery] ; les observations T1 (touches, focus) et T2
 * (motion) entrent par les funnels de la surface, les événements sortent par `surface.input`.
 *
 * Cas 6 (interactions synchrones) : SONDE — le seam Android (`installSessionConfiguration`) ne
 * révèle AUCUN handler d'interactions synchrones (`RuntimeSynchronousInteraction` vit derrière
 * `RuntimeWindowManager.dispatchSynchronousInteraction` + le port d'interaction du port web,
 * sans homologue Android en phase 3) : le différé est ÉPINGLÉ ici — un stimulus ordinaire ne
 * produit que son événement ordinaire, rien ne se tient à sa place. Les interactions armées
 * restent la phase 4.
 *
 * Le débordement (cas 5) est TERMINAL CÔTÉ RUNTIME : l'adaptateur n'a RIEN à faire — la policy
 * de test injecte une capacité d'ingress minuscule et le runtime pose son `SourceOverflow`
 * (snapshot neutre, événements fermés), miroir DESIGN:1223.
 *
 * Aucun type SDK constructible n'est instancié : les observations sont copiées à la main (les
 * constantes `KEYCODE_*`/`ACTION_*`/`TOOL_TYPE_*` sont des constantes de compilation) ; la
 * plomberie réelle `Window.Callback`/`MotionEvent` est prouvée sur émulateur (Task 6).
 */
class AndroidInputDeliveryTest {

    private var sequence = 0L

    /** La source de stamps de session — séquentielle, la même instance pour toute une surface. */
    private fun stampSource(): () -> EventStamp {
        val started = sequence
        return {
            sequence += 1
            EventStamp(SessionSequence(sequence), SessionInstant((sequence - started).nanoseconds), null)
        }
    }

    private fun surface(failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { }): AndroidHostSurface =
        AndroidHostSurface(
            id = RuntimeProcessIds.nextSurfaceId(),
            initialState = testSurfaceState(),
            frameClock = { DeliveryInertFrameClock },
            failureReporter = failureReporter,
        )

    /**
     * L'installation de la configuration de session — les dix paramètres du seam, dont la policy
     * d'entrée du test. L'allocateur de collecteurs est l'instance runtime réelle (le type que le
     * runtime passe au seam, remis inchangé — miroir `sessionCollectorAllocator` web).
     */
    private fun install(
        on: AndroidHostSurface,
        ingressCapacity: Int = 64,
        sessionFailures: MutableList<KadreFailure> = mutableListOf(),
    ): MutableList<KadreFailure> {
        val discrete = EventDeliveryPolicy(
            ingressCapacity = ingressCapacity,
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

    /**
     * La collecte des événements d'entrée — le canal ET le job qui le nourrit : le job DOIT
     * être annulé à la fin du test ([stop]), sinon `runBlocking` joint à jamais un collecteur
     * suspendu dans un flux d'entrée qui ne se termine pas de lui-même.
     */
    private class EventCollector(scope: CoroutineScope, input: SurfaceInput) {
        val channel = Channel<InputEvent>(Channel.UNLIMITED)

        private val job = scope.launch {
            input.events.collect { channel.send(it) }
        }

        /** Laisse le collecteur s'enregistrer (l'enregistrement est synchronisé sur ce thread). */
        suspend fun settle() {
            repeat(8) { yield() }
        }

        /** Annule le collecteur — le flux d'entrée est quitté, le test peut se joindre. */
        fun stop() {
            job.cancel()
        }

        suspend fun next(): InputEvent = withTimeout(5_000) { channel.receive() }

        /** Draine ce qui reste sans bloquer — le compte de l'inattendu. */
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

    private suspend fun CoroutineScope.collectEvents(): EventCollector {
        val collector = EventCollector(this, surfaceUnderCollect.input)
        collector.settle()
        return collector
    }

    // -----------------------------------------------------------------------
    // Cas 1 — une Key observée livre le KeyChanged mappé ; une motion, ses stimuli
    // -----------------------------------------------------------------------

    @Test
    fun runtimeInputReceivesMappedStimuli() = runBlocking {
        val surface = surface()
        surfaceUnderCollect = surface
        install(on = surface)
        surface.observationInstalled(inputWired = true)
        val collector = collectEvents()

        // Une pression de A (meta Shift générique) : le stimulus mappé sort sur le flux.
        surface.onInputObservation(
            AndroidInputObservation.Key(
                nativeKeyCode = KeyEvent.KEYCODE_A,
                repeat = false,
                metaState = KeyEvent.META_SHIFT_ON,
                unicodeChar = 'a'.code,
                isSystemKey = false,
                keyState = KeyState.Pressed,
            ),
        )
        val pressed = assertIs<InputEvent.Key>(collector.next())
        assertEquals(PhysicalKey.Code(usagePage = 0x07, usageId = 0x04), pressed.physicalKey)
        assertEquals(LogicalKey.Character("a"), pressed.logicalKey)
        assertEquals(KeyState.Pressed, pressed.keyState)
        assertFalse(pressed.repeat)
        assertEquals(setOf(ModifierKey.Shift), pressed.modifiers.pressed)
        // L'état porte la touche pressée — pas seulement l'événement.
        assertTrue(surface.input.state.value.keyboard.pressedKeys.contains(PhysicalKey.Code(0x07, 0x04)))

        // La relève : même identité physique, état Released, stamp SÉQUENTIEL du press.
        surface.onInputObservation(
            AndroidInputObservation.Key(
                nativeKeyCode = KeyEvent.KEYCODE_A,
                repeat = false,
                metaState = 0,
                unicodeChar = null,
                isSystemKey = false,
                keyState = KeyState.Released,
            ),
        )
        val released = assertIs<InputEvent.Key>(collector.next())
        assertEquals(PhysicalKey.Code(0x07, 0x04), released.physicalKey)
        assertEquals(KeyState.Released, released.keyState)
        assertTrue(released.stamp.sequence.value > pressed.stamp.sequence.value, "stamps are sequential")
        assertFalse(surface.input.state.value.keyboard.pressedKeys.contains(PhysicalKey.Code(0x07, 0x04)))

        // Une motion hover : le stimulus pointeur correspondant, au scale factor de l'état.
        surface.onMotionObservation(
            motion(MotionEvent.ACTION_HOVER_MOVE, pointers = listOf(Spec(0, 20f, 10f, tool = MotionEvent.TOOL_TYPE_MOUSE))),
            width = 100,
            height = 100,
        )
        val moved = assertIs<InputEvent.PointerMoved>(collector.next())
        assertEquals(org.graphiks.kadre.surface.LogicalPoint(20.0, 10.0), moved.position)

        // Une touche HORS table : forwarded-only, AUCUN stimulus (décision Task 1).
        surface.onInputObservation(
            AndroidInputObservation.Key(
                nativeKeyCode = KeyEvent.KEYCODE_PAGE_UP,
                repeat = false,
                metaState = 0,
                unicodeChar = null,
                isSystemKey = false,
                keyState = KeyState.Pressed,
            ),
        )
        assertEquals(0, collector.drainCount(), "an unmapped keycode delivers no stimulus")
        collector.stop()
    }

    // -----------------------------------------------------------------------
    // Cas 2 — bufferisées jusqu'à l'install ; InputObservationChanged D'ABORD, replay ordonné
    // -----------------------------------------------------------------------

    @Test
    fun observationsBufferedUntilInstalled() = runBlocking {
        val surface = surface()
        surfaceUnderCollect = surface
        install(on = surface)
        val collector = collectEvents()

        // Des observations AVANT l'install : bufferisées, aucune capability n'est Available.
        surface.onInputObservation(
            AndroidInputObservation.Key(
                nativeKeyCode = KeyEvent.KEYCODE_A,
                repeat = false,
                metaState = 0,
                unicodeChar = 'a'.code,
                isSystemKey = false,
                keyState = KeyState.Pressed,
            ),
        )
        surface.onMotionObservation(
            motion(MotionEvent.ACTION_HOVER_MOVE, pointers = listOf(Spec(0, 20f, 10f, tool = MotionEvent.TOOL_TYPE_MOUSE))),
            width = 100,
            height = 100,
        )
        val pre = surface.input.state.value.capabilities
        assertEquals(FeatureAvailability.Unsupported, pre.keyboard)
        assertEquals(FeatureAvailability.Unsupported, pre.pointer)
        assertEquals(FeatureAvailability.Unsupported, pre.touch)

        // L'install : capabilities Available, PUIS le replay ordonné du buffer.
        surface.observationInstalled(inputWired = true)
        val post = surface.input.state.value.capabilities
        assertEquals(FeatureAvailability.Available, post.keyboard)
        assertEquals(FeatureAvailability.Available, post.pointer)
        assertEquals(FeatureAvailability.Available, post.touch)

        // Le replay livre la touche d'abord, la motion ensuite — l'ordre du buffer. La
        // stateRevision de l'événement touche PORTE la preuve de l'ordre : l'observation
        // structurale (InputObservationChanged) a déplacé la révision AVANT le replay.
        val bufferedKey = assertIs<InputEvent.Key>(collector.next())
        val bufferedMotion = assertIs<InputEvent.PointerMoved>(collector.next())
        assertEquals(PhysicalKey.Code(0x07, 0x04), bufferedKey.physicalKey)
        assertEquals(
            surface.input.state.value.revision,
            bufferedMotion.stateRevision,
            "the structural observation moved the revision BEFORE the buffered replay",
        )
        assertEquals(0, collector.drainCount(), "nothing else leaks")
        collector.stop()
    }

    // -----------------------------------------------------------------------
    // Cas 3 — WindowFocusLost → focusLost() : StateReset(FocusLost) + état neutre
    // -----------------------------------------------------------------------

    @Test
    fun windowFocusLostDeliversStateReset() = runBlocking {
        val surface = surface()
        surfaceUnderCollect = surface
        install(on = surface)
        surface.observationInstalled(inputWired = true)
        val collector = collectEvents()

        // Une touche pressée et un contact doigt vivant.
        surface.onInputObservation(
            AndroidInputObservation.Key(
                nativeKeyCode = KeyEvent.KEYCODE_A,
                repeat = false,
                metaState = 0,
                unicodeChar = 'a'.code,
                isSystemKey = false,
                keyState = KeyState.Pressed,
            ),
        )
        assertIs<InputEvent.Key>(collector.next())
        surface.onMotionObservation(
            motion(MotionEvent.ACTION_DOWN, pointers = listOf(Spec(1, 10f, 10f))),
            width = 100,
            height = 100,
        )
        assertIs<InputEvent.TouchChanged>(collector.next())
        assertTrue(surface.input.state.value.touches.isNotEmpty())

        surface.onInputObservation(AndroidInputObservation.WindowFocusLost)

        val reset = assertIs<InputEvent.StateReset>(collector.next())
        assertEquals(org.graphiks.kadre.input.InputStateResetReason.FocusLost, reset.reason)
        val neutral = surface.input.state.value
        assertTrue(neutral.keyboard.pressedKeys.isEmpty(), "no key survives the focus loss")
        assertTrue(neutral.pointers.isEmpty())
        assertTrue(neutral.touches.isEmpty(), "no contact survives the focus loss")
        assertTrue(neutral.modifiers.pressed.isEmpty())
        assertEquals(0, collector.drainCount(), "nothing else leaks")
        collector.stop()
    }

    // -----------------------------------------------------------------------
    // Cas 4 — `input` est le réducteur, pas le stub indisponible
    // -----------------------------------------------------------------------

    @Test
    fun surfaceInputIsTheReducerNotTheUnsupportedStub() = runBlocking {
        val surface = surface()
        // Avant l'install : toutes les capabilities d'entrée indisponibles — et l'instance
        // exposée n'est PAS le stub partagé : l'install la remplacera.
        val pre = surface.input
        val preCapabilities = pre.state.value.capabilities
        assertEquals(FeatureAvailability.Unsupported, preCapabilities.keyboard)
        assertEquals(FeatureAvailability.Unsupported, preCapabilities.pointer)
        assertEquals(FeatureAvailability.Unsupported, preCapabilities.touch)
        assertEquals(FeatureAvailability.Unsupported, preCapabilities.dragAndDrop)
        assertIs<Capability.Unsupported>(preCapabilities.textInput)
        assertIs<Capability.Unsupported>(preCapabilities.rawInput)

        install(on = surface)
        surface.observationInstalled(inputWired = true)

        val post = surface.input
        assertNotSame(pre, post, "the install swaps the pre-install input for the shared reducer")
        // Le réducteur vit : l'observation structurale a rendu le clavier disponible.
        assertEquals(FeatureAvailability.Available, post.state.value.capabilities.keyboard)
        assertEquals(FeatureAvailability.Available, post.state.value.capabilities.pointer)
        assertEquals(FeatureAvailability.Available, post.state.value.capabilities.touch)
        // Le drop est refusé structurellement en phase 3 (phase 6) : pas de promesse.
        assertEquals(FeatureAvailability.Unsupported, post.state.value.capabilities.dragAndDrop)
    }

    // -----------------------------------------------------------------------
    // Cas 4-bis (fix review) — un install SANS observateur câblé ne promet RIEN
    // -----------------------------------------------------------------------

    @Test
    fun unwiredInstallKeepsInputCapabilitiesUnsupported() = runBlocking {
        val surface = surface()
        install(on = surface)

        // Le seam d'un attach sans wrapper (forme View, ou Activity dont window.callback
        // est null) installe l'observation avec les faits au NÉANT : le stimulus structurel
        // (false, false, false) est ABSORBÉ par le réducteur — les caps d'entrée restent à
        // leur refus d'origine, la révision ne bouge pas. Aucune promesse vide.
        surface.observationInstalled(inputWired = false)

        val capabilities = surface.input.state.value.capabilities
        assertEquals(FeatureAvailability.Unsupported, capabilities.keyboard)
        assertEquals(FeatureAvailability.Unsupported, capabilities.pointer)
        assertEquals(FeatureAvailability.Unsupported, capabilities.touch)
        // L'absorption du stimulus « rien n'est câblé » : ni événement, ni mouvement d'état.
        assertEquals(0L, surface.input.state.value.revision.value)
        // Le contraste CÂBLÉ (pré-install Unsupported → post-install Available) reste épinglé
        // par les cas 1, 2 et 4 : les mêmes funnels, les faits d'un observateur réellement
        // installé rendent les caps Available — ici ils y restent refusés, et c'est le fait.
    }

    // -----------------------------------------------------------------------
    // Cas 5 — le débordement d'ingress est TERMINAL CÔTÉ RUNTIME (l'adaptateur n'a rien à faire)
    // -----------------------------------------------------------------------

    @Test
    fun overflowIsTerminalFromTheRuntime() = runBlocking {
        val reported = mutableListOf<Throwable>()
        val surface = surface(failureReporter = RuntimeFailureReporter { reported += it })
        val sessionFailures = install(on = surface, ingressCapacity = 1)
        surface.observationInstalled(inputWired = true)

        // La file d'ingress (capacité 1) ne s'accumule que par RE-ENTRÉE pendant le drain —
        // le même schéma déterministe que le test runtime du même nom : le second et le
        // troisième stimulus partent DEPUIS la livraison du premier, tandis que la file
        // n'a pas fini de se vider. AUCUN collecteur lent : c'est l'INGRESS qui déborde.
        val terminal = CompletableDeferred<KadreFailure?>()
        var injected = false
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            val outcome = runCatching {
                surface.input.events.collect {
                    if (!injected) {
                        injected = true
                        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_B, KeyState.Pressed))
                        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_C, KeyState.Pressed))
                    }
                }
            }
            terminal.complete(outcome.exceptionOrNull()?.let { exception -> assertIs<KadreException>(exception).failure })
        }

        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_A, KeyState.Pressed))

        // Le runtime a terminalisé SA source : l'échec terminal est le sien, pas celui de
        // l'adaptateur — l'adaptateur n'a RIEN à faire (miroir DESIGN:1223).
        assertEquals(KadreFailure.SourceOverflow(KadreResourceKind.InputSource), terminal.await())

        // Snapshot neutre + capabilities indisponibles.
        val state = surface.input.state.value
        val keyboard = state.capabilities.keyboard
        assertIs<FeatureAvailability.Unavailable>(keyboard)
        assertEquals(KadreFailure.SourceOverflow(KadreResourceKind.InputSource), keyboard.failure)
        assertTrue(state.keyboard.pressedKeys.isEmpty(), "the terminal snapshot is neutral")
        assertTrue(state.pointers.isEmpty())
        assertTrue(state.touches.isEmpty())

        // Tout stimulus tardif est refusé : l'état reste neutre.
        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_A, KeyState.Pressed))
        assertTrue(surface.input.state.value.keyboard.pressedKeys.isEmpty(), "a late stimulus is refused")

        // Le flux d'événements est FERMÉ : un collecteur suivant voit l'échec terminal.
        val late = assertFailsWith<KadreException> { surface.input.events.collect {} }
        assertEquals(KadreFailure.SourceOverflow(KadreResourceKind.InputSource), late.failure)

        // La policy de test (CloseSource) ne rate PAS la session ; le diagnostic, lui, est reporté.
        assertTrue(sessionFailures.isEmpty(), "CloseSource terminalises the source without failing the session")
        assertTrue(reported.isNotEmpty(), "the overflow is reported on the failure channel")
    }

    // -----------------------------------------------------------------------
    // Cas 6 — sonde d'interactions synchrones : le seam ne les expose pas (différé phase 4)
    // -----------------------------------------------------------------------

    @Test
    fun ordinaryStimulusDeliversAloneSyncInteractionsDeferred() = runBlocking {
        val surface = surface()
        surfaceUnderCollect = surface
        install(on = surface)
        surface.observationInstalled(inputWired = true)
        val collector = collectEvents()

        // La sonde : le seam `installSessionConfiguration` ne révèle aucun handler
        // d'interactions synchrones (RuntimeSynchronousInteraction vit derrière
        // RuntimeWindowManager.dispatchSynchronousInteraction, sans port Android en phase 3).
        // Épinglé : un stimulus ordinaire ne produit que SON événement ordinaire — rien ne se
        // tient à sa place. Les interactions armées restent la phase 4.
        surface.onInputObservation(keyObservation(KeyEvent.KEYCODE_A, KeyState.Pressed))
        assertIs<InputEvent.Key>(collector.next())
        assertEquals(0, collector.drainCount(), "no interaction stands in for the ordinary stimulus")
        collector.stop()
    }

    // -----------------------------------------------------------------------
    // Le seam de suppression : la lecture de l'état committé (HostDefault jusqu'à T4)
    // -----------------------------------------------------------------------

    @Test
    fun shouldConsumeKeyReadsTheCommittedBehavior() {
        val surface = surface()
        install(on = surface)
        // HostDefault (l'état committé de la phase 3 — le commit est le sujet de T4) : rien
        // n'est consommé, pas même une scroll-key.
        assertEquals(InputDefaultBehavior.HostDefault, surface.state.value.inputDefaultBehavior)
        assertFalse(surface.shouldConsumeKey(KeyEvent.KEYCODE_PAGE_UP))
        assertFalse(surface.shouldConsumeKey(KeyEvent.KEYCODE_SPACE))
        assertFalse(surface.shouldConsumeKey(KeyEvent.KEYCODE_A))

        // L'ensemble des scroll-keys est le miroir des 9 touches web, traduit en keycodes
        // Android — la policy de T4 le lira sous SuppressWhenPossible.
        assertEquals(9, ANDROID_DOCUMENT_SCROLL_KEYS.size)
        assertTrue(ANDROID_DOCUMENT_SCROLL_KEYS.contains(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(ANDROID_DOCUMENT_SCROLL_KEYS.contains(KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue(ANDROID_DOCUMENT_SCROLL_KEYS.contains(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(ANDROID_DOCUMENT_SCROLL_KEYS.contains(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(ANDROID_DOCUMENT_SCROLL_KEYS.contains(KeyEvent.KEYCODE_PAGE_UP))
        assertTrue(ANDROID_DOCUMENT_SCROLL_KEYS.contains(KeyEvent.KEYCODE_PAGE_DOWN))
        assertTrue(ANDROID_DOCUMENT_SCROLL_KEYS.contains(KeyEvent.KEYCODE_MOVE_HOME))
        assertTrue(ANDROID_DOCUMENT_SCROLL_KEYS.contains(KeyEvent.KEYCODE_MOVE_END))
        assertTrue(ANDROID_DOCUMENT_SCROLL_KEYS.contains(KeyEvent.KEYCODE_SPACE))
    }

    // -----------------------------------------------------------------------
    // Outils de test — observations copiées construites à la main
    // -----------------------------------------------------------------------

    private fun keyObservation(code: Int, state: KeyState): AndroidInputObservation.Key =
        AndroidInputObservation.Key(
            nativeKeyCode = code,
            repeat = false,
            metaState = 0,
            unicodeChar = null,
            isSystemKey = false,
            keyState = state,
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
    ): AndroidMotionObservation = AndroidMotionObservation(
        actionMasked = actionMasked,
        actionIndex = 0,
        pointerCount = pointers.size,
        pointerIds = pointers.map { it.id }.toIntArray(),
        xs = pointers.map { it.x }.toFloatArray(),
        ys = pointers.map { it.y }.toFloatArray(),
        pressures = pointers.map { null }.toTypedArray(),
        toolTypes = pointers.map { it.tool }.toIntArray(),
        buttons = 0,
        actionButton = 0,
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
private object DeliveryInertFrameClock : AndroidRedrawScheduler.FrameClock {
    override fun schedule(frame: () -> Unit) = Unit
    override fun cancel() = Unit
}
