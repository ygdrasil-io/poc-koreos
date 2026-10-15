package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureManagerRevision
import org.graphiks.kadre.capture.CaptureManagerState
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSession
import org.graphiks.kadre.capture.CaptureSessionState
import org.graphiks.kadre.capture.CaptureSourceId
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.capture.CaptureTarget
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadEffectOutcome
import org.graphiks.kadre.input.GamepadEffectSession
import org.graphiks.kadre.input.GamepadEffectState
import org.graphiks.kadre.input.GamepadEffectStopReason
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadLocalizedHapticConstraints
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.internal.runtime.InputDevicePort
import org.graphiks.kadre.internal.runtime.InputDevicePortDevice
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeHostSurface
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurface
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponentsFactory
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
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
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowSpec

/**
 * Le round-trip d'ATTACH de la couture capture uikit phase 9 (Task 5) — le prolongement direct
 * du round-trip phase 8 ([UIKitAttachPortsLifecycleTest]) : les MÊMES components en forme
 * d'attach (manager de scènes réel sur un delegate enregistreur, provider de surface PRIMAIRE
 * différé, ports display + manette + device de session, closeAction drain de scènes PUIS close
 * runtime) avec LE PORT CAPTURE EN PLUS — le [UIKitCapturePort] RÉEL sur ses jetons de seam
 * (source ReplayKit factice disponible, snapshots renderer scriptés, ticker manuel — le
 * pattern jeton du module), porté par le TROISIÈME constructeur sanctionné de
 * [RuntimeSessionComponents] (le créneau capturePort propre, délégué positionnellement entre
 * display et manette) et nourri du registre des surfaces comme la couture d'attach réelle :
 * `KadreIos.attachScene` enregistre la surface primaire dans SA closure d'admission différée ;
 * le fixture, mono-fenêtre SANS wrapper différé (le miroir phase 8 — inventer un
 * `RuntimeWindowManager` réel serait un second fixture), fait l'enregistrement équivalent au
 * montage des components. Le fait porté est le même : le registre ne survit pas à la session
 * et est rempli UNE FOIS, avant tout open consommateur (le check du registre refuse un second
 * enregistrement d'une AUTRE vue).
 *
 * Trois épingles :
 *
 * 1. LA SURFACE CONSOMMATRICE CAPTURE — le MÊME accès public que le contrat consommateur
 *    Android (`KadreConsumerCaptureContractTest`) : `KadreScope.capture` expose la table de
 *    vérité HONNÊTE de la plateforme (écran `Supported(Bgra8, EmbeddedWhenAvailable, région
 *    Unsupported)` à disponibilité LUE, fenêtre/énumération/picker structurellement
 *    refusés, surface `Supported(Rgba8, Hidden, région Unsupported)`), les permissions
 *    issues de la disponibilité (écran Granted SANS dialogue, fenêtre jamais obtenable),
 *    `requestPermission(Screen)` répétable et idempotent qui ne présente JAMAIS d'interface
 *    (le journal du seam ne porte QUE des lectures de disponibilité), et les ouvertures
 *    refusées honnêtement (Source sans inventaire, HostChoice refusé à l'admission AVANT tout
 *    fait de seam, surface inconnue introuvable dans LE registre de CETTE session) — puis LE
 *    round-trip complet : open de la surface primaire ENREGISTRÉE → réservation (zéro fait
 *    de seam) → pompe → frames réelles (configuration révision 0 dérivée de la première
 *    copie, contenu dessiné intact) → `Stopped(Requested)` au stop de l'application ;
 * 2. LE CHEMIN TERMINAL (critère de sortie roadmap : la scène qui se ferme PENDANT une
 *    capture active) — un flux surface EN VOLS et un effet manette en vol sont interrompus
 *    (`Stopped(ParentSessionStopping)` l'un et l'autre, JAMAIS `Requested`/`Completed`), et
 *    l'ordre complet du démontage est épinglé contre L'ORDRE RÉEL du runtime (lu à
 *    `SessionRuntime.closeRuntimeComponents` et `RuntimeSessionComponents.close` — PAS une
 *    tautologie du fixture) : capture (le manager de capture ferme EN PREMIER — l'arrêt de
 *    session parente scelle la pompe et désamorce le ticker exactement une fois) →
 *    observation display → interruption de l'effet (owner natif stoppé puis refermé) →
 *    surface primaire → `sceneManager.close()` → `runtime.close()` → ports
 *    display/capture/manette/device (fermés PAR les components, jamais par le closeAction) →
 *    sessionObserver de terminaison en DERNIER ;
 * 3. LA PROJECTION PUBLIQUE RESTE INTACTE — displays et provider différé répondent
 *    exactement comme le round-trip phase 8 : l'arrivée du port capture n'a rien retranché
 *    des autres surfaces consommateur.
 */
class UIKitCaptureLifecycleTest {

    // -- les seams capture (pattern jeton — le port ne voit jamais un type ReplayKit/UIView) ---

    /**
     * Le seam ReplayKit factice : la disponibilité pilotée (LUE à chaque lecture), le JOURNAL
     * de chaque appel — la matière du pin « ne prompt jamais » (un appel qui ne serait pas une
     * lecture de disponibilité est visible) et du pin « l'admission refusée ne touche aucun
     * seam ».
     */
    private class FakeCaptureSource : UIKitCaptureSource {
        var available: Boolean = true
        val calls = ArrayList<String>()
        var closedCount: Int = 0
            private set

        override fun recorderAvailable(): Boolean {
            calls += "recorderAvailable"
            return available
        }

        override fun startCapture(handler: UIKitCaptureStreamHandler) {
            calls += "startCapture"
        }

        override fun stopCapture(onStopped: (String?) -> Unit) {
            calls += "stopCapture"
        }

        override fun close() {
            closedCount += 1
        }
    }

    /**
     * Le jeton snapshot renderer — un « raster natif » ByteArray contrôlé au contenu CONNU
     * (le pin « la capture ne fabrique rien » : les octets livrés au collector sont LES
     * octets du faux renderer), fermé EXACTEMENT UNE FOIS.
     */
    private class FakeSnapshot(
        override val width: Int = 64,
        override val height: Int = 48,
        override val bytesPerRow: Int = 256,
        override val format: PixelFormat = PixelFormat.Rgba8,
    ) : UIKitSurfaceSnapshot {
        val native = ByteArray(height * bytesPerRow) { index -> (index * 31 + 7).toByte() }
        var closeCount: Int = 0
            private set

        override fun copyInto(destination: ByteArray, byteCount: Int) {
            native.copyInto(destination, 0, 0, byteCount)
        }

        override fun close() {
            check(closeCount == 0) { "the surface snapshot was closed twice" }
            closeCount += 1
        }
    }

    /**
     * Le seam snapshot factice — chaque lecture rend un raster FRAIS de la même forme (le
     * renderer réel re-dessine à chaque copie) et JOURNALISE le fait dans la chronologie
     * partagée (« surface-snapshotted » — la matière de l'épingle d'ordre).
     */
    private class FakeSnapshotSource(private val recorder: MutableList<String>) : UIKitSurfaceSnapshotSource {
        var snapshotCount: Int = 0
            private set

        override fun snapshot(view: Any): UIKitSurfaceSnapshotOutcome {
            snapshotCount += 1
            recorder += "surface-snapshotted"
            return UIKitSurfaceSnapshotOutcome.Frame(FakeSnapshot())
        }
    }

    /**
     * Le ticker factice — le ruling cadence (décision 5 du spike) : le scheduling est INJECTÉ,
     * le test tire les ticks À LA MAIN. L'armement et le désamorçage sont JOURNALISÉS dans la
     * chronologie partagée (« surface-tick-armed » / « surface-tick-stopped ») — le
     * désamorçage EXACTEMENT UNE FOIS est porté par le pump (release), l'épingle d'ordre le
     * rend visible.
     */
    private class FakeTickSource(private val recorder: MutableList<String>) : UIKitSurfaceTickSource {
        private var startCount = 0
        private var onTick: (() -> Unit)? = null

        override fun startTicking(interval: Duration, onTick: () -> Unit) {
            check(startCount == 0) { "the fake tick source was started twice" }
            startCount += 1
            this.onTick = onTick
            recorder += "surface-tick-armed"
        }

        override fun stopTicking() {
            recorder += "surface-tick-stopped"
        }

        /** LE geste du test : un tick, SYNCHRONE — le pump consomme et enfile. */
        fun tick() = checkNotNull(onTick) { "the fake tick source was never armed" }.invoke()
    }

    // -- les ports enregistreurs (le MIROIR du fixture de UIKitAttachPortsLifecycleTest) ------

    /** Le delegate du manager de scènes — un état figé ; le close des scènes ne passe PAS par lui. */
    private class RecordingRuntimeManager : WindowManager {
        private val mutableState = MutableStateFlow(
            WindowManagerState(
                primary = null,
                windows = emptyList(),
                capabilities = WindowManagerCapabilities(
                    Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
                ),
                revision = WindowManagerRevision(0),
            ),
        )

        override val state: MutableStateFlow<WindowManagerState> = mutableState

        override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> =
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindow))
    }

    /** Le port display enregistreur — l'inventaire uikit (HostViewport + AirPlay Physical). */
    private class RecordingDisplayPort(private val recorder: MutableList<String>) : DisplayPort {
        override val enumerationCapability: Capability<Unit> =
            Capability.Supported(Unit, FeatureAvailability.Available)

        override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> =
            KadreResult.Success(snapshot())

        override fun installSnapshotObserver(
            observer: (KadreResult<DisplayPortSnapshot>) -> Unit,
        ): AutoCloseable {
            // La publication initiale SYNCHRONE de l'install — le contrat du port réel.
            observer(KadreResult.Success(snapshot()))
            return AutoCloseable {
                recorder += "display-observation-closed"
            }
        }

        override fun close() {
            recorder += "display-port-closed"
        }

        /** L'inventaire uikit : l'écran de LA fenêtre (HostViewport) + un écran AirPlay (Physical). */
        private fun snapshot(): DisplayPortSnapshot = DisplayPortSnapshot(
            primaryKey = 1L,
            displays = listOf(
                DisplayPortDisplay(
                    key = 1L,
                    type = DisplayType.HostViewport,
                    name = null,
                    bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(2048, 1536)),
                    workArea = null,
                    scaleFactor = 2.0,
                    currentModeKey = 1L,
                    modes = listOf(DisplayPortMode(1L, PhysicalSize(2048, 1536), 60.0, null)),
                ),
                DisplayPortDisplay(
                    key = 2L,
                    type = DisplayType.Physical,
                    name = null,
                    bounds = PhysicalRect(PhysicalPoint(-1920, 0), PhysicalSize(1920, 1080)),
                    workArea = null,
                    scaleFactor = 1.0,
                    currentModeKey = 2L,
                    modes = listOf(DisplayPortMode(2L, PhysicalSize(1920, 1080), 60.0, null)),
                ),
            ),
        )
    }

    /** L'owner natif d'un effet — enregistre le stop et le close du chemin terminal. */
    private class RecordingEffectOwner(private val recorder: MutableList<String>) : GamepadPortEffect {
        override fun requestStop(): KadreResult<Unit> {
            recorder += "effect-owner-requestStop"
            return KadreResult.Success(Unit)
        }

        override fun close() {
            recorder += "effect-owner-closed"
        }
    }

    /** Le port manette enregistreur — UNE manette connectée, effets LocalizedHaptic disponibles. */
    private class RecordingGamepadPort(private val recorder: MutableList<String>) : GamepadPort {
        override val gamepads: List<GamepadPortGamepad> = listOf(
            GamepadPortGamepad(
                key = 11L,
                descriptor = GamepadDescriptor(
                    name = "Controller 11",
                    mapping = GamepadMapping.Standard,
                    buttons = listOf(GamepadButton.South),
                    axes = emptyList(),
                ),
                state = GamepadState(
                    buttons = listOf(GamepadButtonValue(GamepadButton.South, 0.0, false)),
                    axes = emptyList(),
                ),
                routing = GamepadRoutingState.Routed,
                capabilities = GamepadCapabilities(
                    effects = Capability.Supported(
                        GamepadEffectConstraints(
                            kinds = setOf(GamepadEffectKind.LocalizedHaptic),
                            localizedHaptics = GamepadLocalizedHapticConstraints(
                                setOf(GamepadHapticLocality.Default),
                            ),
                            maximumDuration = null,
                        ),
                        FeatureAvailability.Available,
                    ),
                ),
            ),
        )

        override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable =
            AutoCloseable { }

        override fun updateRouting(routing: GamepadPortRouting) = Unit

        override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> {
            recorder += "effect-native-start"
            return KadreResult.Success(RecordingEffectOwner(recorder))
        }

        override fun close() {
            recorder += "gamepad-port-closed"
        }
    }

    /** Le port device générique enregistreur — un clavier (le fait seul, hors livraison). */
    private class RecordingInputDevicePort(private val recorder: MutableList<String>) : InputDevicePort {
        override val devices: List<InputDevicePortDevice> = listOf(
            InputDevicePortDevice(21L, InputDeviceDescriptor("Keyboard", InputDeviceKind.Keyboard)),
        )

        override fun installObserver(observer: (InputDevicePortEvent) -> Unit): AutoCloseable =
            AutoCloseable { }

        override fun close() {
            recorder += "input-device-port-closed"
        }
    }

    /** La vue jeton du registre capture — le port ne voit jamais une vraie UIView. */
    private class ViewToken(val label: String) {
        override fun toString(): String = "View($label)"
    }

    /**
     * L'instrument enregistreur AUTOUR du port capture RÉEL — le port réel n'écrit aucune
     * chronologie (les fakes phase 8 enregistraient dans LEUR close) ; le wrapper délègue
     * TOUT au port et journalise LE fait épinglé : le close du port par les components.
     */
    private class RecordingCapturePort(
        private val delegate: UIKitCapturePort,
        private val recorder: MutableList<String>,
    ) : org.graphiks.kadre.internal.runtime.CapturePort by delegate {
        override fun close() {
            recorder += "capture-port-closed"
            delegate.close()
        }
    }

    /** La matière livrée au test : les faits d'UNE frame, copiés DANS le collector (la lease). */
    private class FrameRecord(val revision: Long, val bytes: ByteArray)

    /** Le lot des components en forme d'attach — un seul jeu de ports par fixture. */
    private class Fixture {
        val recorder = mutableListOf<String>()
        val captureSource = FakeCaptureSource()
        val snapshotSource = FakeSnapshotSource(recorder)

        /** Le ticker créé par le start du pump — le test l'attend puis tire ses ticks. */
        val tickerCreated = CompletableDeferred<FakeTickSource>()
        val displayPort = RecordingDisplayPort(recorder)
        val gamepadPort = RecordingGamepadPort(recorder)
        val inputDevicePort = RecordingInputDevicePort(recorder)
        var providerRuns = 0

        /** La surface primaire factice du provider différé — l'id que le registre porte. */
        private val recordingSurface: RuntimeHostSurface = RuntimeHostSurface(
            SurfaceId(0L),
            SurfaceState(
                attachment = SurfaceAttachmentState.Attached,
                logicalSize = LogicalSize(1.0, 1.0),
                physicalSize = PhysicalSize(1, 1),
                scaleFactor = 1.0,
                safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
                visibility = SurfaceVisibility.Visible,
                occlusion = SurfaceOcclusion.Visible,
                focus = SurfaceFocus.Unfocused,
                appearance = SurfaceAppearance(SurfaceTheme.Light, SurfaceContrast.Normal),
                cursor = CursorStyle.System(CursorIcon.Default),
                pointerCapture = PointerCaptureMode.None,
                hitTesting = HitTestingMode.Enabled,
                inputDefaultBehavior = InputDefaultBehavior.HostDefault,
                revision = SurfaceRevision(0L),
            ),
        )

        fun components(rootScope: CoroutineScope): RuntimeSessionComponents {
            val sceneManager = UIKitWindowSceneManager(
                runtimeManager = RecordingRuntimeManager(),
                provider = null,
                policy = KadrePolicies.Default,
                applicationFactory = KadreApplicationFactory { error("mono-window: no child sessions") },
                failureReporter = { },
                parentScope = rootScope,
                childSessionFactory = { error("mono-window: no provider, no child sessions") },
            )
            // LE port capture RÉEL de CETTE session (le miroir de KadreIos.attachScene) — sur
            // des jetons de seam pour l'hôte de test. Le registre est rempli UNE FOIS au
            // montage (la closure d'admission différée du réel — même fait, même
            // enregistrer-une-fois), l'id étant la surface primaire fixée du fixture.
            val capturePort = UIKitCapturePort(
                source = captureSource,
                surfaces = UIKitHostCaptureSurfaceRegistry(),
                surfaceSnapshots = snapshotSource,
                surfaceTickSources = { FakeTickSource(recorder).also { tickerCreated.complete(it) } },
            )
            capturePort.registerHostCaptureSurface(SurfaceId(0L), ViewToken("primary"))
            return RuntimeSessionComponents(
                windows = sceneManager,
                primarySurfaceProvider = {
                    providerRuns += 1
                    RuntimePrimarySurface(recordingSurface) { recorder += "primary-surface-closed" }
                },
                displayPort = displayPort,
                gamepadPort = gamepadPort,
                inputDevicePort = inputDevicePort,
                capturePort = RecordingCapturePort(capturePort, recorder),
                closeAction = {
                    // L'ordre du closeAction uikit (KadreIos.attachScene) : le drain des demandes
                    // de scènes PUIS le close du runtime — épinglé par le test du chemin terminal.
                    recorder += "scene-close"
                    sceneManager.close()
                    recorder += "runtime-close"
                },
            )
        }
    }

    private fun host(
        fixture: Fixture,
        onTerminated: () -> Unit = { },
    ): RuntimeHostController = RuntimeHostController.withComponents(
        platform = KadrePlatform.UIKit,
        componentsFactory = RuntimeSessionComponentsFactory { _, rootScope -> fixture.components(rootScope) },
        // La capability que l'attach uikit pose quand la source mémoire process s'installe.
        initialLifecycleCapabilities = LifecycleCapabilities(FeatureAvailability.Available),
        sessionObserver = RuntimeSessionObserver { _, _ -> onTerminated() },
    )

    private fun CoroutineScope.attachedSession(
        controller: RuntimeHostController,
        started: CompletableDeferred<KadreScope>,
    ): org.graphiks.kadre.application.KadreSession =
        when (val attached = controller.attach(
            this,
            KadreApplicationFactory { KadreApplication { started.complete(this); awaitCancellation() } },
        )) {
            is KadreResult.Success -> attached.value
            is KadreResult.Failure -> error("expected attached session, got ${attached.reason}")
        }

    /**
     * Ouvre la session capture SURFACE par la SURFACE PUBLIQUE (le même accès que le
     * consommateur Android : `KadreScope.capture.open`) et rend la session vivante ;
     * l'appelant lance SON collector puis attend le ticker créé par le start.
     */
    private suspend fun openActiveSurfaceStream(scope: KadreScope): CaptureSession {
        val primary = assertNotNull(scope.primarySurface.value, "the deferred primary surface is exposed")
        val capture = assertIs<KadreResult.Success<CaptureSession>>(
            scope.capture.open(CaptureRequest(target = CaptureTarget.Surface(primary.id))),
            "the open of the registered primary surface succeeds",
        ).value
        assertEquals(CaptureSourceKind.HostSurface, capture.source.kind, "the surface arm names its kind")
        return capture
    }

    // -- la surface consommatrice capture -------------------------------------------------------

    @Test
    fun theAttachedSessionExposesTheHonestCaptureConsumerSurface() = runBlocking {
        val fixture = Fixture()
        val controller = host(fixture)
        val started = CompletableDeferred<KadreScope>()

        val session = attachedSession(controller, started)
        val scope = started.await()
        val manager = scope.capture

        // LA PROJECTION PUBLIQUE RESTE INTACTE : l'inventaire display répond comme le
        // round-trip phase 8 — l'arrivée du port capture n'a rien retranché.
        val displays = assertIs<DisplayInventory.Enumerated>(scope.displays.state.value.inventory)
        assertEquals(2, displays.displays.size, "the full UIScreen inventory is enumerated")
        assertEquals(DisplayType.HostViewport, assertNotNull(displays.primary).state.value.type)

        // LA TABLE DE VÉRITÉ HONNÊTE (rulings 1-5) — lisible du consommateur au premier
        // regard, AVANT toute demande : écran Supported à disponibilité LUE (le fixture
        // déclare le recorder disponible), fenêtre/énumération/picker structurellement
        // refusés, surface Supported sur le format MESURÉ de la voie renderer.
        val state = manager.state.value
        assertEquals(CaptureManagerRevision(0), state.revision, "the initial projection is revision 0")
        assertEquals(PermissionState.Granted, state.permissions.screen, "ReplayKit screens need no consent dialog")
        assertEquals(
            PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
            state.permissions.window,
            "the window permission can never become granted on iOS/tvOS",
        )
        val screen = assertIs<Capability.Supported<CaptureTargetConstraints>>(state.capabilities.screen)
        assertEquals(
            CaptureTargetConstraints(
                formats = setOf(PixelFormat.Bgra8),
                cursorModes = setOf(CaptureCursorMode.EmbeddedWhenAvailable),
                region = FeatureAvailability.Unsupported,
            ),
            screen.constraints,
            "the screen arm is the full display, system cursor composed",
        )
        assertEquals(FeatureAvailability.Available, screen.availability)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            state.capabilities.window,
            "no primitive enumerates capturable windows",
        )
        val surface = assertIs<Capability.Supported<CaptureTargetConstraints>>(state.capabilities.surface)
        assertEquals(
            CaptureTargetConstraints(
                formats = setOf(PixelFormat.Rgba8),
                cursorModes = setOf(CaptureCursorMode.Hidden),
                region = FeatureAvailability.Unsupported,
            ),
            surface.constraints,
            "the surface arm captures the view's own rendered buffer",
        )
        assertEquals(FeatureAvailability.Available, surface.availability)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
            state.capabilities.sourceEnumeration,
            "no primitive enumerates capture sources",
        )
        assertEquals(FeatureAvailability.Unsupported, state.capabilities.hostPicker, "never a picker promise")
        assertEquals(
            CaptureSources.Unavailable(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
            state.sources,
            "the inventory is honestly unavailable — never HostPickerOnly",
        )

        // REQUESTPERMISSION NE PROMPT JAMAIS (ruling 5) : le journal du seam ne porte QUE des
        // lectures de disponibilité ; la demande est répétable et IDEMPOTENTE (deux demandes
        // relisent le MÊME état — même snapshot, même révision).
        val first = assertIs<KadreResult.Success<CaptureManagerState>>(
            manager.requestPermission(CapturePermissionScope.Screen),
        ).value
        val second = assertIs<KadreResult.Success<CaptureManagerState>>(
            manager.requestPermission(CapturePermissionScope.Screen),
        ).value
        assertEquals(state, first, "the permission request returns the read availability state")
        assertEquals(first, second, "the request is repeatable and idempotent")
        assertEquals(
            listOf("recorderAvailable", "recorderAvailable", "recorderAvailable"),
            fixture.captureSource.calls,
            "the seam journal holds availability reads only — no prompt-shaped call",
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
            manager.requestPermission(CapturePermissionScope.Window),
            "the window scope is refused structurally",
        )

        // LES OUVERTURES REFUSÉES HONNÊTEMENT :
        // - Source : AUCUNE source énumérée n'existe (l'inventaire est indisponible) ;
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("request.target")),
            manager.open(CaptureRequest(target = CaptureTarget.Source(CaptureSourceId(1L), state.revision))),
        )
        // - HostChoice : refusé À L'ADMISSION (le picker est structurellement Unsupported) —
        //   AVANT tout fait de seam (le journal du recorder ne grandit pas).
        val seamCallsBeforeOpen = fixture.captureSource.calls.size
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            manager.open(CaptureRequest(target = CaptureTarget.HostChoice)),
            "the host picker admission is structurally unsupported on iOS/tvOS",
        )
        assertEquals(seamCallsBeforeOpen, fixture.captureSource.calls.size, "the refused open touched no seam")
        // - Surface INCONNUE : le registre de CETTE session ne connaît que la surface primaire.
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("request.target")),
            manager.open(CaptureRequest(target = CaptureTarget.Surface(SurfaceId(999L)))),
        )
        assertEquals(0, fixture.snapshotSource.snapshotCount, "reserving touches no snapshot seam")

        // LE ROUND-TRIP COMPLET — la surface primaire ENREGISTRÉE streame de VRAIES frames.
        val delivered = CompletableDeferred<FrameRecord>()
        val capture = openActiveSurfaceStream(scope)

        // LE COLLECTEUR D'ABORD : le runtime ne démarre le flux que dans collectFrames — le
        // start arme le ticker injecté, un tick manuel met la première frame stagée, la
        // configuration (révision 0) en est dérivée AVANT toute frame au collector. La COPIE
        // vit DANS le collector (la lease est fermée au retour — le contrat §11.1).
        val collector = async {
            capture.collectFrames { frame ->
                delivered.complete(FrameRecord(frame.configurationRevision.value, frame.copyPlanes().single().bytes))
            }
        }
        val ticker = fixture.tickerCreated.await()
        ticker.tick()
        val streaming = assertIs<CaptureSessionState.Streaming>(
            capture.state.first { it is CaptureSessionState.Streaming },
        )
        assertEquals(0L, streaming.configuration.revision.value, "revision 0")
        assertEquals(PhysicalSize(64, 48), streaming.configuration.size, "the first copy derives the real size")
        assertEquals(PixelFormat.Rgba8, streaming.configuration.format, "the renderer format is read, never assumed")

        val frame = delivered.await()
        assertEquals(0L, frame.revision, "revision 0 carried by the frame")
        // LE CONTENU RÉEL : les octets livrés sont CEUX du renderer — la capture ne fabrique
        // rien (12288 = 48 rangées × 256 octets, le raster entier du snapshot).
        assertEquals(12288, frame.bytes.size)
        assertEquals((0 * 31 + 7).toByte(), frame.bytes[0])
        assertEquals((12287 * 31 + 7).toByte(), frame.bytes[12287])
        assertEquals(1, fixture.providerRuns, "the deferred provider resolved exactly once")

        // L'arrêt propre de l'application : `Stopped(Requested)`, et rien ne suit — le tick
        // d'après-terminal tombe sur la garde du pump (compté, jamais dessiné ni livré).
        val snapshotsAtTerminal = fixture.snapshotSource.snapshotCount
        capture.requestStop()
        assertEquals(
            CaptureOutcome.Stopped(CaptureStopReason.Requested),
            capture.awaitTermination(),
            "the app-initiated stop is the requested stop",
        )
        assertEquals(KadreResult.Success(Unit), collector.await(), "the collector returns the requested stop")
        ticker.tick()
        assertEquals(
            snapshotsAtTerminal,
            fixture.snapshotSource.snapshotCount,
            "the sealed pump draws nothing after the terminal",
        )

        // La fermeture de la session capture n'a PAS fermé les ports de la session : le close
        // de SESSION reste le seul chemin terminal des ports.
        session.close()
        session.awaitTermination()
        assertIs<SessionState.Terminated>(session.state.value)
        assertEquals(1, fixture.recorder.count { it == "display-port-closed" })
        assertEquals(1, fixture.recorder.count { it == "capture-port-closed" })
        assertEquals(1, fixture.recorder.count { it == "gamepad-port-closed" })
        assertEquals(1, fixture.recorder.count { it == "input-device-port-closed" })
    }

    // -- le chemin terminal ----------------------------------------------------------------------

    @Test
    fun theTerminalPathInterruptsAnActiveCaptureAndAnEffectThenClosesSceneRuntimeThenPorts() = runBlocking {
        val fixture = Fixture()
        var observedTerminations = 0
        val controller = host(fixture) {
            observedTerminations += 1
            fixture.recorder += "host-terminated"
        }
        val started = CompletableDeferred<KadreScope>()

        val session = attachedSession(controller, started)
        val scope = started.await()

        // UN FLUX SURFACE EN VOLS (le même accès consommateur que le round-trip ci-dessus).
        val delivered = CompletableDeferred<Unit>()
        val capture = openActiveSurfaceStream(scope)
        val collector = async {
            capture.collectFrames { frame ->
                frame.copyPlanes() // la lecture honnête dans la fenêtre de lease
                delivered.complete(Unit)
            }
        }
        val ticker = fixture.tickerCreated.await()
        ticker.tick()
        delivered.await()
        assertIs<CaptureSessionState.Streaming>(capture.state.first { it is CaptureSessionState.Streaming })

        // UN EFFET MANETTE EN VOLS, démarré par la SURFACE PUBLIQUE (le miroir phase 8).
        val gamepad = assertIs<DeviceInventory.Enumerated>(scope.devices.state.value.inventory).gamepads.single()
        val effect = assertIs<KadreResult.Success<GamepadEffectSession>>(
            gamepad.playEffect(GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 60.seconds)),
        ).value
        assertEquals(GamepadEffectState.Playing, effect.state.value)
        assertEquals(
            listOf("surface-tick-armed", "surface-snapshotted", "effect-native-start"),
            fixture.recorder,
            "the pre-terminal chronology: the stream armed, one snapshot drawn, the effect started",
        )

        // LA scène se ferme PENDANT la capture active ET l'effet (critère de sortie roadmap) :
        // les DEUX vivants sont interrompus par la terminaison de la session parente — jamais
        // `Requested` (ce n'est pas l'app qui arrête), jamais `Completed`.
        session.close()
        assertEquals(
            CaptureOutcome.Stopped(CaptureStopReason.ParentSessionStopping),
            capture.awaitTermination(),
            "the active capture is interrupted by the parent session stopping",
        )
        assertIs<CaptureSessionState.Terminated>(capture.state.value)
        assertEquals(
            KadreResult.Success(Unit),
            collector.await(),
            "the collector returns the parent-session stop as a success (not a permission denial)",
        )
        val outcome = assertIs<GamepadEffectOutcome.Stopped>(effect.awaitTermination())
        assertEquals(GamepadEffectStopReason.ParentSessionStopping, outcome.reason)
        assertIs<GamepadEffectState.Terminated>(effect.state.value)

        session.awaitTermination()
        assertIs<SessionState.Terminated>(session.state.value)

        // L'ORDRE COMPLET DU DÉMONTAGE, épinglé contre l'ordre RÉEL du runtime — lu à
        // `SessionRuntime.closeRuntimeComponents` (le manager de capture ferme EN PREMIER,
        // AVANT les displays, la manette et les components) et à
        // `RuntimeSessionComponents.close` (surface primaire → closeAction → ports dans
        // l'ordre display/capture/manette/device). `surface-tick-stopped` en tête du chemin
        // terminal EST le fait « l'arrêt de session parente scelle la pompe » : le ticker est
        // désamorcé par la chaîne captureManager.close → stopFromParent → finish → stream
        // close → pump release, AVANT toute autre mort. Les ports ferment PAR les components
        // — le closeAction ne les nomme jamais — et le sessionObserver termine en DERNIER.
        assertEquals(
            listOf(
                "surface-tick-armed",
                "surface-snapshotted",
                "effect-native-start",
                "surface-tick-stopped",
                "display-observation-closed",
                "effect-owner-requestStop",
                "effect-owner-closed",
                "primary-surface-closed",
                "scene-close",
                "runtime-close",
                "display-port-closed",
                "capture-port-closed",
                "gamepad-port-closed",
                "input-device-port-closed",
                "host-terminated",
            ),
            fixture.recorder,
        )
        assertEquals(1, observedTerminations, "the termination observer runs exactly once")
    }
}
