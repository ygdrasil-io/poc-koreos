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
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayMode
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeHostSurface
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.BinaryImage
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.ImageFormat
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.FullscreenKind
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.RejectedWindowField
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRevision
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Les commits réels de la fenêtre primaire Activity (phase 4, Task 1) : `title`,
 * `contentProtection` et `fullscreen` `Borderless` COMMITTENT par le seam natif injecté
 * ([AndroidWindowNative]) avec readback effectif, révision bumpée et événement
 * `PropertiesChanged` stampé ; les onze champs host-imposed restent rejetés champ par champ
 * `Unsupported(UpdateWindow)` ; les formes P1 (stale d'abord, vide → `Applied` courant,
 * unsupported-seul → `PartiallyApplied` sans révision ni événement) restent inchangées ;
 * `close()` reste `Temporary(retryable = false)` — ruling fermé : la fenêtre d'host Activity
 * est structurellement non fermable par Kadre, Back est host-owned.
 */
class AndroidActivityWindowTest {

    @Test
    fun titleCommitsWithReadbackAndEvent() = runBlocking {
        val native = RecordingWindowNative(title = "")
        val manager = manager(native)
        val stamps = installSessionStampsForTest(manager)
        val window = primaryWindow(manager)
        val events = collectEventsUntil(window, expected = 1) {
            window.apply(WindowUpdate(title = PropertyChange.Set("Tour")))
        }
        // Le commit : l'état publié porte le READBACK du natif (pas la valeur demandée).
        assertEquals("Tour", window.state.value.title)
        assertEquals(WindowRevision(1), window.state.value.revision)
        // L'événement : PropertiesChanged({Title}), stampé par la source de session, portant
        // l'état publié et l'operationId de l'update.
        val event = events.single()
        assertTrue(event is WindowEvent.PropertiesChanged, "expected PropertiesChanged, got $event")
        assertEquals(setOf(WindowProperty.Title), event.changed)
        assertEquals(WindowRevision(1), event.stateRevision)
        assertEquals(stamps.single(), event.stamp)
        // Le recorder : exactement une primitive posée — setTitle. Le readback n'est pas une
        // pose.
        assertEquals(listOf("setTitle"), native.calls)
        assertEquals(listOf("Tour"), native.titles)
    }

    @Test
    fun contentProtectionCommitsViaFlagReadback() = runBlocking {
        val native = RecordingWindowNative(title = "")
        val manager = manager(native)
        installSessionStampsForTest(manager)
        val window = primaryWindow(manager)
        val events = collectEventsUntil(window, expected = 1) {
            window.apply(WindowUpdate(contentProtection = PropertyChange.Set(true)))
        }
        assertEquals(true, window.state.value.contentProtection)
        assertEquals(WindowRevision(1), window.state.value.revision)
        val event = events.single()
        assertTrue(event is WindowEvent.PropertiesChanged, "expected PropertiesChanged, got $event")
        assertEquals(setOf(WindowProperty.ContentProtection), event.changed)
        assertEquals(listOf("setContentProtection"), native.calls)
        assertEquals(listOf(true), native.contentProtections)
    }

    @Test
    fun borderlessFullscreenCommitsAndExclusiveIsPerFieldRejected() = runBlocking {
        val native = RecordingWindowNative(title = "")
        val manager = manager(native)
        installSessionStampsForTest(manager)
        val window = primaryWindow(manager)
        // Borderless commite : hide des system bars, readback « barres cachées », un événement.
        val seen = collectEventsUntilTerminal(manager, window) {
            val borderless = window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            assertTrue(borderless is KadreResult.Success && borderless.value is WindowUpdateOutcome.Applied)
            // Exclusive n'a AUCUNE primitive : rejet champ par champ, aucun appel natif.
            val exclusive = window.apply(
                WindowUpdate(
                    fullscreen = PropertyChange.Set(
                        FullscreenMode.Exclusive(
                            RuntimeProcessIds.nextDisplayId(),
                            DisplayMode(
                                RuntimeProcessIds.nextDisplayModeId(),
                                PhysicalSize(1, 1),
                                60.0,
                                null,
                            ),
                        ),
                    ),
                ),
            )
            val partially = when (exclusive) {
                is KadreResult.Success -> exclusive.value as WindowUpdateOutcome.PartiallyApplied
                is KadreResult.Failure ->
                    throw AssertionError("Exclusive must be per-field rejected, got $exclusive")
            }
            assertEquals(
                listOf(
                    RejectedWindowField(
                        WindowProperty.Fullscreen,
                        KadreFailure.Unsupported(KadreOperation.UpdateWindow),
                    ),
                ),
                partially.rejected,
            )
            // AUCUN commit pour Exclusive : révision inchangée, état inchangé, aucun appel
            // natif de plus (le seam ne déclare structurellement aucune primitive exclusive).
            assertEquals(WindowRevision(1), partially.state.revision)
            assertEquals(FullscreenMode.Borderless, partially.state.fullscreen)
        }
        assertEquals(FullscreenMode.Borderless, window.state.value.fullscreen)
        assertEquals(WindowRevision(1), window.state.value.revision)
        assertEquals(listOf("setBorderlessFullscreen"), native.calls)
        assertEquals(listOf(true), native.borderlessFullscreen)
        assertTrue(!native.systemBarsVisible, "the borderless commit must hide the system bars")
        // Un seul événement : le commit Borderless. Exclusive n'en publie pas.
        val event = seen.single()
        assertTrue(event is WindowEvent.PropertiesChanged, "expected PropertiesChanged, got $event")
        assertEquals(setOf(WindowProperty.Fullscreen), event.changed)
    }

    @Test
    fun refusedFieldsCallNoPrimitive() = runBlocking {
        val native = RecordingWindowNative(title = "")
        val manager = manager(native)
        installSessionStampsForTest(manager)
        val window = primaryWindow(manager)
        val seen = collectEventsUntilTerminal(manager, window) {
            val result = window.apply(
                WindowUpdate(
                    outerPosition = PropertyChange.Set(PhysicalPoint(0, 0)),
                    level = PropertyChange.Set(WindowLevel.Floating),
                    icon = PropertyChange.Set(BinaryImage(byteArrayOf(1), ImageFormat.Png, PhysicalSize(1, 1))),
                    title = PropertyChange.Set("Mixed"),
                ),
            )
            val partially = when (result) {
                is KadreResult.Success -> result.value as WindowUpdateOutcome.PartiallyApplied
                is KadreResult.Failure ->
                    throw AssertionError("mixed update must be PartiallyApplied, got $result")
            }
            assertEquals(
                listOf(
                    RejectedWindowField(WindowProperty.OuterPosition, KadreFailure.Unsupported(KadreOperation.UpdateWindow)),
                    RejectedWindowField(WindowProperty.Level, KadreFailure.Unsupported(KadreOperation.UpdateWindow)),
                    RejectedWindowField(WindowProperty.Icon, KadreFailure.Unsupported(KadreOperation.UpdateWindow)),
                ),
                partially.rejected,
            )
            // Le champ supporté du MÊME update commite : un rejet ne bloque jamais ses frères.
            assertEquals("Mixed", partially.state.title)
            assertEquals(WindowRevision(1), partially.state.revision)
        }
        // Le recorder n'a reçu QUE la primitive du champ supporté — les champs refusés n'ont
        // AUCUNE primitive à appeler : le seam injecté ([AndroidWindowNative]) ne déclare que
        // les six méthodes des champs supportés (structurellement inertie).
        assertEquals(listOf("setTitle"), native.calls)
        val event = seen.single()
        assertTrue(event is WindowEvent.PropertiesChanged, "expected PropertiesChanged, got $event")
        assertEquals(setOf(WindowProperty.Title), event.changed)
    }

    @Test
    fun unsupportedOnlyUpdateProducesNoRevisionNoEvent() = runBlocking {
        val native = RecordingWindowNative(title = "")
        val manager = manager(native)
        installSessionStampsForTest(manager)
        val window = primaryWindow(manager)
        val seen = collectEventsUntilTerminal(manager, window) {
            val result = window.apply(
                WindowUpdate(
                    outerPosition = PropertyChange.Set(PhysicalPoint(0, 0)),
                    resizable = PropertyChange.Set(true),
                ),
            )
            val partially = when (result) {
                is KadreResult.Success -> result.value as WindowUpdateOutcome.PartiallyApplied
                is KadreResult.Failure ->
                    throw AssertionError("unsupported-only update must be PartiallyApplied, got $result")
            }
            assertEquals(
                listOf(
                    RejectedWindowField(WindowProperty.OuterPosition, KadreFailure.Unsupported(KadreOperation.UpdateWindow)),
                    RejectedWindowField(WindowProperty.Resizable, KadreFailure.Unsupported(KadreOperation.UpdateWindow)),
                ),
                partially.rejected,
            )
            // CONTRAT :106-110 — un update ne portant QUE du valid-but-unsupported est un
            // succès sans révision et sans événement ; l'état rendu est l'instantané courant.
            assertSame(window.state.value, partially.state)
            assertEquals(WindowRevision(0), partially.state.revision)
        }
        assertTrue(seen.isEmpty(), "an unsupported-only update must not publish any event")
        assertTrue(native.calls.isEmpty(), "refused fields must not call any primitive")
    }

    @Test
    fun staleAndEmptyShapesPreserved() = runBlocking {
        val native = RecordingWindowNative(title = "")
        val manager = manager(native)
        installSessionStampsForTest(manager)
        val window = primaryWindow(manager)
        val seen = collectEventsUntilTerminal(manager, window) {
            // Stale d'abord — la garde de révision reste la première admission (forme P1).
            val stale = window.apply(WindowUpdate(expectedRevision = WindowRevision(7)))
            assertEquals(
                KadreFailure.StaleRevision(7L, 0L),
                (stale as KadreResult.Failure).reason,
            )
            // Vide → Applied sur l'état courant, sans révision, sans événement, sans primitive.
            val empty = window.apply(WindowUpdate())
            assertTrue(empty is KadreResult.Success && empty.value is WindowUpdateOutcome.Applied)
            assertEquals(WindowRevision(0), (empty.value as WindowUpdateOutcome.Applied).state.revision)
        }
        assertTrue(seen.isEmpty(), "neither a stale nor an empty update may publish an event")
        assertTrue(native.calls.isEmpty())
    }

    @Test
    fun closeStaysTemporaryAndBackIsNeverIntercepted() = runBlocking {
        val native = RecordingWindowNative(title = "")
        val manager = manager(native)
        installSessionStampsForTest(manager)
        val window = primaryWindow(manager)
        // Ruling FERMÉ (phase 4) : la fenêtre d'host Activity est structurellement non
        // fermable par Kadre — `close()` garde `Temporary(retryable = false)`, la seule
        // failure on-row (la ligne n'admets exactement que Temporary/Platform) qui dit
        // « sans espoir ici ». `session.close()` n'est jamais une demande de fermeture
        // native (invariant P1) et Back reste host-owned : le seam natif ne déclare ni
        // primitive de fermeture, ni callback Back, ni finish().
        val closed = window.close()
        assertEquals(
            KadreFailure.TemporarilyUnavailable(retryable = false),
            (closed as KadreResult.Failure).reason,
        )
        // Aucune primitive n'a été posée pour fermer : le recorder est resté vide.
        assertTrue(native.calls.isEmpty())
        // Et le registre des close-requests reste invalide : aucun CloseRequested n'est
        // jamais publié, aucun `requestId` ne peut être valide.
        assertEquals(
            KadreFailure.InvalidRequest("requestId"),
            (
                window.respondToCloseRequest(
                    RuntimeProcessIds.nextWindowCloseRequestId(),
                    WindowCloseDecision.Accept,
                ) as KadreResult.Failure
                ).reason,
        )
    }

    @Test
    fun capabilitiesAnnounceExactlyTheSupportedFields() {
        val manager = manager(RecordingWindowNative(title = ""))
        val capabilities = primaryWindow(manager).capabilities.value
        // Les trois champs committables sont annoncés, disponibles.
        assertEquals(Capability.Supported(Unit, FeatureAvailability.Available), capabilities.title)
        assertEquals(Capability.Supported(Unit, FeatureAvailability.Available), capabilities.contentProtection)
        assertEquals(
            Capability.Supported(setOf(FullscreenKind.Borderless), FeatureAvailability.Available),
            capabilities.fullscreen,
        )
        // Les onze champs host-imposed / sans primitive restent refusés champ par champ
        // (les raisons vivent dans le KDoc de `androidWindowCapabilities`).
        val unsupportedUpdate = KadreFailure.Unsupported(KadreOperation.UpdateWindow)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.outerPosition)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.contentSize)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.minimumSize)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.maximumSize)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.resizable)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.decorations)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.systemButtons)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.level)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.transparency)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.blurBehind)
        assertEquals(Capability.Unsupported(unsupportedUpdate), capabilities.icon)
        // Et les refus hors update de la phase 1 ne sont pas affaiblis.
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention)),
            capabilities.attention,
        )
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RespondToCloseRequest)),
            capabilities.closeInterception,
        )
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess)),
            capabilities.platformAccess,
        )
    }

    // ------------------------------------------------------------- l'outillage du test

    /** Le recorder natif : le SEAM ENTIER ([AndroidWindowNative]) — rien d'autre à enregistrer. */
    private class RecordingWindowNative(
        title: String,
    ) : AndroidWindowNative {
        val calls = mutableListOf<String>()
        val titles = mutableListOf<String>()
        val contentProtections = mutableListOf<Boolean>()
        val borderlessFullscreen = mutableListOf<Boolean>()
        var systemBarsVisible: Boolean = true
        private var currentTitle: String = title

        override fun setTitle(title: String) {
            calls += "setTitle"
            titles += title
            currentTitle = title
        }

        override fun title(): String = currentTitle

        override fun setContentProtection(enabled: Boolean) {
            calls += "setContentProtection"
            contentProtections += enabled
        }

        override fun isContentProtectionEnabled(): Boolean = contentProtections.lastOrNull() ?: false

        override fun setBorderlessFullscreen(enabled: Boolean) {
            calls += "setBorderlessFullscreen"
            borderlessFullscreen += enabled
            systemBarsVisible = !enabled
        }

        override fun isSystemBarVisible(): Boolean = systemBarsVisible
    }

    private fun manager(native: RecordingWindowNative): AndroidActivityWindowManager =
        AndroidActivityWindowManager(
            primarySurface = primarySurface(),
            nextRequestId = RuntimeProcessIds::nextWindowRequestId,
            native = native,
            // Dispatch INLINE : le corps de `apply` court sur le thread d'appel comme avant —
            // la production passe par AndroidMainThreadDispatch (les primitives du seam touchent
            // la hiérarchie de vues, main-thread), les tests hôtes n'ont ni Looper ni vues.
            hostThread = object : AndroidHostThreadDispatch {
                override suspend fun <R> dispatch(block: suspend () -> R): R = block()
            },
        )

    private fun primaryWindow(manager: AndroidActivityWindowManager): Window =
        manager.state.value.windows.single()

    /** La surface primaire du test : le `RuntimeHostSurface` minimal de la phase 0, sans View. */
    private fun primarySurface(): RuntimeHostSurface =
        RuntimeHostSurface(RuntimeProcessIds.nextSurfaceId(), initialSurfaceState())

    /**
     * L'installation de la configuration de session — la même source de stamps que la surface
     * (le runtime l'installe par `RuntimeSessionWindowManager`, le miroir du test métriques).
     * Rend les stamps posés pour les assertions d'égalité.
     */
    private fun installSessionStampsForTest(
        manager: AndroidActivityWindowManager,
    ): List<EventStamp> {
        val stamps = mutableListOf<EventStamp>()
        var sequence = 0L
        manager.installSessionConfiguration(
            deliveryPolicy = KadrePolicies.Default.window,
            inputDeliveryPolicy = KadrePolicies.Default.input,
            source = {
                EventStamp(SessionSequence(sequence), SessionInstant((sequence++).nanoseconds), null)
                    .also(stamps::add)
            },
            sessionFailureHandler = {},
            collectorAllocator = RuntimeEventCollectorAllocator(64),
            maxCollectorsPerFlow = 8,
            dropTransferScope = null,
            diagnostics = {},
            rawInputPort = null,
        )
        return stamps
    }

    /**
     * Le collecteur déterministe (le miroir du test métriques) : souscrit en UNDISPATCHED,
     * draine les sauts de dispatch du channelFlow par [yield], conduit le stimulus, ne
     * débloque qu'aux [expected] événements — un déficit sort en timeout.
     */
    private suspend fun collectEventsUntil(
        window: Window,
        expected: Int,
        emit: suspend () -> Unit,
    ): List<WindowEvent> = coroutineScope {
        val seen = mutableListOf<WindowEvent>()
        val done = CompletableDeferred<List<WindowEvent>>()
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            window.events.collect { event ->
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

    /**
     * Le collecteur terminal : même souscription, puis le terminal du manager (le hook que la
     * couture branche sur la teardown de session) achève le flux — la collecte rend TOUT ce
     * qui a été publié, y compris rien.
     */
    private suspend fun collectEventsUntilTerminal(
        manager: AndroidActivityWindowManager,
        window: Window,
        emit: suspend () -> Unit,
    ): List<WindowEvent> = coroutineScope {
        val seen = mutableListOf<WindowEvent>()
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            window.events.collect { event -> seen += event }
        }
        repeat(8) { yield() }
        emit()
        repeat(8) { yield() }
        manager.terminalise()
        withTimeout(10_000) { collector.join() }
        seen
    }

    /**
     * L'instantané initial de surface, les mêmes valeurs que `initialSurfaceState` de la phase 0
     * bornées à leur minimum valide (base 1x1) — aucun type Android requis.
     */
    private fun initialSurfaceState(): SurfaceState = SurfaceState(
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
}
