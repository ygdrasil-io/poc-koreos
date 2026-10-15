@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
)

package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.input.DropOfferTerminationReason
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadLocalizedHapticConstraints
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputPurpose
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.internal.runtime.InputDevicePort
import org.graphiks.kadre.internal.runtime.InputDevicePortDevice
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent
import org.graphiks.kadre.internal.runtime.RuntimeDropItemSource
import org.graphiks.kadre.internal.runtime.RuntimeDropOffer
import org.graphiks.kadre.internal.runtime.RuntimeDropTransferBudget
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeHostSurface
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurface
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponentsFactory
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.platform.uikit.KadreUikitSurfaceDriver
import org.graphiks.kadre.platform.uikit.UIKitWindowSceneProvider
import org.graphiks.kadre.platform.uikit.UIKitWindowSceneRequestResolver
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
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowSpec
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol

/**
 * LES CYCLES DE RÉTENTION Kotlin/ARC — le scénario de MESURE du nightly uikit/tvOS, l'analogue
 * exact d'`AndroidLeakCyclesTest` (roadmap l.336 : « Vérifier les cycles de rétention Kotlin/ARC,
 * les observers, continuations, scopes, transferts et buffers après ouvertures/fermetures
 * répétées » ; §8 l.409 : « Les tests de rétention et de callback tardif couvrent chaque source
 * native et chaque ressource closeable »). Il n'invente AUCUN contrat ni ligne de registre
 * (aucun UIK-xxx) : il RÉPÈTE les cycles de vie RÉELS du module — attach/stop du contrôleur hôte
 * sur les CINQ components de session, ré-attach de LA MÊME identité surface/fenêtre, offres de
 * drop sur les sources de transfert de production, flux capture sur le port RÉEL, ports
 * display/manette/device sur leurs SEAMS comptés, créneaux de la source mémoire process, sessions
 * texte sur le port RÉEL — et affirme, À CHAQUE CYCLE, que le système revient EXACTEMENT à son
 * état de repos : outcome terminal exact, DÉMONTAGE COMPLET (chaque teardown exactement une fois,
 * dans l'ordre du runtime), réservations EN VOL à zéro, compteurs de gel (un tick tardif ne
 * dessine plus), enregistrements SDK rendus, budget rendu, JAMAIS de résurrection (l'enfant
 * tardif d'une demande déjà terminée est détaché, le claim d'une offre terminée répond fermé).
 *
 * | cycle famille | volume | ce qui est mesuré PAR CYCLE au repos |
 * |---------------|--------|---------------------------------------|
 * | attach/stop (les CINQ components de session) | 10 | outcome EXACT `Stopped(HostRequested)` publié une fois ; l'ORDRE COMPLET du démontage — observation display → surface primaire → drain des scènes → close runtime → ports display/capture/manette/device → observateur de terminaison — EXACT une fois par cycle (un teardown sauté ou doublé casse l'égalité) ; les CINQ managers vivants à l'attach (inventaire display 2 écrans, 1 manette + 1 clavier, capture en révision 0, capability mémoire `Available`, surface primaire résolue exactement une fois) ; réservations capture EN VOL à zéro et frames gelées (deltas) ; journal d'attach diagnostics au repos |
 * | ré-attach de LA MÊME identité surface/fenêtre | 5 | LA MÊME surface (même instance, id 0) et LE MÊME manager runtime servent 5 vies COMPLÈTES : l'attach du cycle suivant est accepté (l'identité est rendue à la terminaison — une réservation qui fuite le refuserait), chaque cycle laisse ZÉRO résidu (même ordre de démontage exact), le ré-attach FINAL après le DERNIER close réussit encore |
 * | drop offer/transfer (source, claim, budget) | 4 | la source de transfert de production (`UIKitDropTransferSource`) fermée EXACTEMENT une fois par cycle (les DEUX chemins cleanup : terminaison sans transfert, close du transfert réclamé) ; le claim admet UNE fois puis `AlreadyInUse` (jamais de claim ressuscité) ; le claim d'une offre terminée répond `Closed` ; le budget PARTAGÉ (limite 1) rend son slot à CHAQUE cycle — l'accept du cycle suivant RÉUSSIT (un transfert retenu le rendrait `ResourceLimitExceeded`) |
 * | flux capture surface (open → frames → stop) | 4 | réservation admise (delta « ouvertes » +1), N ticks → N frames FIFO, terminal `Stopped(Requested)` EXACT une fois, ticker désamorcé EXACT une fois, un tick TARDIF ne dessine RIEN (le jeton scripté n'est pas consommé) et ne livre RIEN (frames gelées), EN VOL à zéro au repos (ouvertes == terminées) |
 * | ports display / device / gamepad | 4 chacun | exactement UNE registration SDK au port, ZÉRO au close (la libération du DERNIER port — le miroir comptable du seam) ; le seam display POSSÉDÉ fermé exactement une fois (jamais deux) ; les seams input JAMAIS fermés par le port (la couture d'attach les possède) ; l'inventaire projeté déterministe d'un cycle à l'autre (une table qui fuite grossirait) |
 * | pression mémoire (créneaux host) | 4 | capability `Available` au claim, `Unsupported` au DERNIER libéré ; la source process ne se ré-enregistre JAMAIS dans une vie (les DEUX hosts du cycle à deux créneaux partagent L'UNIQUE registration) ; le token posé est retiré EXACTEMENT une fois par vie de source ; un warning livré HORS pile arrive `Moderate`, JAMAIS `Critical` |
 * | receiver texte (installé/retiré) | 5 | LE receiver de session — l'objet UIKit retenu par l'owner du port — posé dans UNE hiérarchie hôte partagée par les 5 cycles : l'amorçage au document de la config, la composition marquée IME VIVANTE pendant la session, puis le retrait — zéro sous-vue orpheline, la composition meurt AVEC le receiver, RIEN ne s'accumule d'un cycle à l'autre (le hôte K/N n'est pas un process applicatif : l'open de session avec fenêtre/focus est l'affaire du driver, BCK-024, sur l'app réelle) |
 * | demandes de fenêtre (open/resolve/abandon) | 4 | admission pendante dans un budget maxPending=1 ; resolve → `OpenedInNewSession(child)` avec enfant INDÉPENDANT (jamais arrêté par le parent) ; abandon → `Cancelled` ; la résolution tardive d'une demande terminale est un NO-OP et l'enfant TARDIF d'un double resolve est détaché (jamais la réponse — zéro session ressuscitée) ; le slot est rendu à CHAQUE cycle (la demande suivante est admise) |
 *
 * Ce qui est mesuré ICI : les comptes et comportements observables ci-dessus — les closes des
 * sources possédées, les registrations SDK rendues, les slots et budgets retournés, l'ordre du
 * démontage, les gels de compteurs. Ce qui EXIGE Instruments/Leaks (heap ARC) et reste à la
 * décision sample/debug différée (le même partage qu'Android avec LeakCanary) : la survie
 * d'INSTANCES côté tas (UIView capturées, continuations orphelines, buffers de frames retenus
 * au-delà du close, closures d'admission tardive). Ce test en mesure la CONSÉQUENCE observable :
 * chaque ressource closeable ci-dessus est refermée exactement une fois et chaque registre de
 * session est rendu — « compteur gelé / registration à zéro / budget rendu » est la conséquence
 * exacte de « rétention à zéro ».
 *
 * Les attentes sont BORNÉES (poll 25 ms, deadline 10 s — le style du moule Android) ; les soaks
 * d'absence (tick tardif, abandon tardif) sont bornés aussi : on affirme qu'un état NE bouge PAS,
 * jamais un `sleep` nu comme preuve. Les fakes sont les JETONS du module (aucun type UIKit
 * comportemental hors le receiver texte — posé/retiré de la hiérarchie, sans fenêtre ni focus) :
 * la traduction de chaque seam est déjà prouvée par les tests de port ; ICI seule la RÉPÉTITION
 * des cycles est la matière.
 */
class UIKitRetentionCyclesTest {

    // ========================================================================================
    // Les jetons enregistreurs — le MIROIR exact des fakes des tests de port du module.
    // ========================================================================================

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

    /** Le port display enregistreur des cycles contrôleur — publication initiale sync, close compté. */
    private class RecordingDisplayPort(private val recorder: MutableList<String>) : DisplayPort {
        override val enumerationCapability: Capability<Unit> =
            Capability.Supported(Unit, FeatureAvailability.Available)

        override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> =
            KadreResult.Success(snapshot())

        override fun installSnapshotObserver(
            observer: (KadreResult<DisplayPortSnapshot>) -> Unit,
        ): AutoCloseable {
            observer(KadreResult.Success(snapshot()))
            return AutoCloseable {
                recorder += "display-observation-closed"
            }
        }

        override fun close() {
            recorder += "display-port-closed"
        }

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

    /** L'owner natif d'un effet — ne doit JAMAIS être démarré dans ces cycles de repos. */
    private class RecordingEffectOwner(private val recorder: MutableList<String>) : GamepadPortEffect {
        override fun requestStop(): KadreResult<Unit> {
            recorder += "effect-owner-requestStop"
            return KadreResult.Success(Unit)
        }

        override fun close() {
            recorder += "effect-owner-closed"
        }
    }

    /** Le port manette enregistreur des cycles contrôleur — UNE manette, effets disponibles. */
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

    /** Le port device générique enregistreur des cycles contrôleur — un clavier. */
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

    /** La source ReplayKit factice des cycles contrôleur — l'arm HostChoice n'est jamais pris. */
    private class FakeCaptureSource : UIKitCaptureSource {
        override fun recorderAvailable(): Boolean = true

        override fun startCapture(handler: UIKitCaptureStreamHandler) = Unit

        override fun stopCapture(onStopped: (String?) -> Unit) = Unit

        override fun close() = Unit
    }

    /** Le snapshot factice — un raster contrôlé fermé EXACTEMENT UNE FOIS (le second close lève). */
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

    /** La source snapshot factice — chaque lecture rend un raster FRAIS de la même forme. */
    private class FakeSnapshotSource : UIKitSurfaceSnapshotSource {
        var snapshotCount: Int = 0
            private set

        override fun snapshot(view: Any): UIKitSurfaceSnapshotOutcome {
            snapshotCount += 1
            return UIKitSurfaceSnapshotOutcome.Frame(FakeSnapshot())
        }
    }

    /** Le ticker factice — le scheduling est INJECTÉ, le test tire les ticks À LA MAIN. */
    private class FakeTickSource : UIKitSurfaceTickSource {
        var stopCount: Int = 0
            private set
        private var startCount = 0
        private var onTick: (() -> Unit)? = null

        override fun startTicking(interval: kotlin.time.Duration, onTick: () -> Unit) {
            check(startCount == 0) { "the fake tick source was started twice" }
            startCount += 1
            this.onTick = onTick
        }

        override fun stopTicking() {
            stopCount += 1
        }

        fun tick() = checkNotNull(onTick) { "the fake tick source was never armed" }.invoke()
    }

    /** La vue jeton du registre capture — le port ne voit jamais une vraie UIView. */
    private class ViewToken(val label: String) {
        override fun toString(): String = "View($label)"
    }

    /** La matière d'UNE frame capture, copiée dans le listener (la chronologie du test). */
    private class RecordingCaptureListener : CapturePortStreamListener {
        val frames = ArrayList<CapturePortFrame>()
        val terminations = ArrayList<CapturePortTermination>()

        override fun onFrame(frame: CapturePortFrame) {
            frames += frame
        }

        override fun onReconfigured(configuration: org.graphiks.kadre.capture.CaptureConfiguration) = Unit

        override fun onTerminated(termination: CapturePortTermination) {
            terminations += termination
        }
    }

    /**
     * Le lot des components en forme d'attach — le miroir exact de la fabrique de
     * `KadreIos.attachScene` en jetons : manager de scènes réel, provider de surface primaire
     * différé, ports display + manette + device, port capture RÉEL sur ses jetons de seam,
     * closeAction drain de scènes PUIS close runtime. [sharedSurface] et [sharedWindowManager]
     * permettent au test de ré-attach de partager LA MÊME identité surface/fenêtre d'un cycle
     * à l'autre.
     */
    private class Fixture(
        sharedSurface: RuntimeHostSurface? = null,
        sharedWindowManager: RecordingRuntimeManager? = null,
    ) {
        val recorder = mutableListOf<String>()
        val captureSource = FakeCaptureSource()
        val snapshotSource = FakeSnapshotSource()
        val displayPort = RecordingDisplayPort(recorder)
        val gamepadPort = RecordingGamepadPort(recorder)
        val inputDevicePort = RecordingInputDevicePort(recorder)
        var providerRuns = 0

        /** La surface primaire du fixture — partagée quand le cycle exige LA MÊME identité. */
        private val recordingSurface: RuntimeHostSurface = sharedSurface ?: RuntimeHostSurface(
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

        /** Le manager runtime délégué — partagé quand la « fenêtre » du cycle est la même. */
        private val runtimeManager: RecordingRuntimeManager = sharedWindowManager ?: RecordingRuntimeManager()

        fun components(rootScope: CoroutineScope): RuntimeSessionComponents {
            val sceneManager = UIKitWindowSceneManager(
                runtimeManager = runtimeManager,
                provider = null,
                policy = KadrePolicies.Default,
                applicationFactory = KadreApplicationFactory { error("mono-window: no child sessions") },
                failureReporter = { },
                parentScope = rootScope,
                childSessionFactory = { error("mono-window: no provider, no child sessions") },
            )
            val capturePort = UIKitCapturePort(
                source = captureSource,
                surfaces = UIKitHostCaptureSurfaceRegistry(),
                surfaceSnapshots = snapshotSource,
                surfaceTickSources = { FakeTickSource() },
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
                    recorder += "scene-close"
                    sceneManager.close()
                    recorder += "runtime-close"
                },
            )
        }
    }

    /**
     * L'instrument enregistreur AUTOUR du port capture RÉEL — le port réel n'écrit aucune
     * chronologie ; le wrapper délègue TOUT au port et journalise LE fait épinglé : le close
     * du port par les components (le miroir exact de `UIKitCaptureLifecycleTest`).
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

    /** L'ordre EXACT du démontage d'une session sans effet ni flux (lu au runtime réel). */
    private val terminalOrder = listOf(
        "display-observation-closed",
        "primary-surface-closed",
        "scene-close",
        "runtime-close",
        "display-port-closed",
        "capture-port-closed",
        "gamepad-port-closed",
        "input-device-port-closed",
        "host-terminated",
    )

    private fun host(fixture: Fixture): RuntimeHostController = RuntimeHostController.withComponents(
        platform = KadrePlatform.UIKit,
        componentsFactory = RuntimeSessionComponentsFactory { _, rootScope -> fixture.components(rootScope) },
        initialLifecycleCapabilities = LifecycleCapabilities(FeatureAvailability.Available),
        sessionObserver = RuntimeSessionObserver { _, _ -> fixture.recorder += "host-terminated" },
    )

    private fun CoroutineScope.attachedSession(
        controller: RuntimeHostController,
        started: CompletableDeferred<KadreScope>,
    ): KadreSession =
        when (val attached = controller.attach(
            this,
            KadreApplicationFactory { KadreApplication { started.complete(this); awaitCancellation() } },
        )) {
            is KadreResult.Success -> attached.value
            is KadreResult.Failure -> error("expected attached session, got ${attached.reason}")
        }

    /**
     * L'attente BORNÉE (poll 25 ms, deadline 10 s — le style du moule Android) : jamais un
     * sommeil nu comme preuve, jamais une course.
     */
    private suspend fun awaitAtRest(description: String, condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + AWAIT_TIMEOUT_MILLIS.milliseconds
        while (!condition()) {
            if (deadline.hasPassedNow()) {
                error("the system never returned to at rest within ${AWAIT_TIMEOUT_MILLIS}ms: $description")
            }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    /** La surface primaire du provider — la SEULE substitution entre les fixtures. */
    private fun sharedSurface(): RuntimeHostSurface = RuntimeHostSurface(
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

    // ========================================================================================
    // 10× attach/stop — les CINQ components de session, l'ordre de démontage exact.
    // ========================================================================================

    /**
     * Le cycle attach/stop répété : chaque attach initialise les CINQ components (managers de
     * scènes, display, manette, device, capture — la surface primaire en plus), chaque close
     * livre l'outcome EXACT `Stopped(HostRequested)` et démonte TOUT dans l'ordre du runtime,
     * exactement une fois. Une fuite de teardown (un close sauté, un close doublé, un
     * observateur jamais désenregistré) casse l'ÉGALITÉ de la chronologie — CE cycle échoue là.
     */
    @Test
    fun attachStopCyclesReturnToAtRest() = runBlocking {
        val journalBaseline = KadreUikitSurfaceDriver.reportedFailureCount
        val captureBefore = UIKitCaptureDiagnostics.snapshot()

        repeat(ATTACH_STOP_CYCLES) { cycle ->
            val fixture = Fixture()
            var terminations = 0
            val controller = RuntimeHostController.withComponents(
                platform = KadrePlatform.UIKit,
                componentsFactory = RuntimeSessionComponentsFactory { _, rootScope ->
                    fixture.components(rootScope)
                },
                initialLifecycleCapabilities = LifecycleCapabilities(FeatureAvailability.Available),
                sessionObserver = RuntimeSessionObserver { _, _ ->
                    terminations += 1
                    fixture.recorder += "host-terminated"
                },
            )
            val started = CompletableDeferred<KadreScope>()

            val session = attachedSession(controller, started)
            val scope = started.await()

            // LES CINQ MANAGERS VIVANTS : l'inventaire display complet, la manette ET le
            // clavier, la capture en révision 0, la capability mémoire posée, la surface
            // primaire résolue exactement une fois — un component qui fuite ou qui dégrade
            // ne se reconstruit pas neuf au cycle suivant.
            val displays = assertIs<DisplayInventory.Enumerated>(scope.displays.state.value.inventory)
            assertEquals(2, displays.displays.size, "cycle $cycle: the display inventory is enumerated")
            val devices = assertIs<DeviceInventory.Enumerated>(scope.devices.state.value.inventory)
            assertEquals(1, devices.gamepads.size, "cycle $cycle: the gamepad is connected")
            assertEquals(1, devices.devices.size, "cycle $cycle: the keyboard is connected")
            assertEquals(FeatureAvailability.Available, scope.lifecycle.capabilities.value.memoryPressure)
            assertNotNull(scope.primarySurface.value, "cycle $cycle: the primary surface is resolved")
            assertEquals(1, fixture.providerRuns, "cycle $cycle: the deferred provider resolved exactly once")
            assertTrue(fixture.recorder.isEmpty(), "cycle $cycle: a live attach touches no teardown path")

            session.close()
            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
                session.awaitTermination(),
                "cycle $cycle: the close delivers the exact host-requested outcome",
            )
            assertIs<SessionState.Terminated>(session.state.value)
            assertEquals(1, terminations, "cycle $cycle: the termination observer runs exactly once")
            assertEquals(
                terminalOrder,
                fixture.recorder,
                "cycle $cycle: every teardown ran exactly once, in the runtime order",
            )

            // LES DELTAS CAPTURE au repos : aucun bras ouvert par ces cycles — en vol à zéro,
            // frames gelées, et le journal d'attach diagnostics n'a rien reporté.
            val captureAfter = UIKitCaptureDiagnostics.snapshot()
            assertEquals(
                captureBefore.surfaceReservationsOpened,
                captureAfter.surfaceReservationsOpened,
                "cycle $cycle: no capture reservation leaked out of a plain attach/stop",
            )
            assertEquals(0, captureAfter.surfaceReservationsInFlight, "cycle $cycle: nothing in flight at rest")
            assertEquals(
                captureBefore.surfaceFramesDelivered,
                captureAfter.surfaceFramesDelivered,
                "cycle $cycle: no capture frame was ever delivered",
            )
            assertEquals(
                journalBaseline,
                KadreUikitSurfaceDriver.reportedFailureCount,
                "cycle $cycle: a clean cycle reports no attach diagnostic",
            )
        }
    }

    // ========================================================================================
    // 5× ré-attach de LA MÊME identité surface/fenêtre.
    // ========================================================================================

    /**
     * Le cycle de ré-attach sur LA MÊME identité (la sentinelle Android « le ré-attach du cycle
     * suivant réussit ») : LA MÊME surface (même instance, même id 0) et LE MÊME manager runtime
     * servent 5 vies complètes — une identité retenue par une session précédente refuserait ou
     * dégraderait le cycle suivant. Chaque close rend l'identité : l'attach FINAL après le
     * DERNIER close réussit encore.
     */
    @Test
    fun reAttachSameSurfaceIdentityCyclesStayAtRest() = runBlocking {
        val journalBaseline = KadreUikitSurfaceDriver.reportedFailureCount
        val identity = sharedSurface()
        val sharedWindowManager = RecordingRuntimeManager()

        repeat(REATTACH_CYCLES) { cycle ->
            val fixture = Fixture(identity, sharedWindowManager)
            val started = CompletableDeferred<KadreScope>()
            val session = attachedSession(host(fixture), started)
            val scope = started.await()

            assertEquals(
                SurfaceId(0L),
                assertNotNull(scope.primarySurface.value).id,
                "cycle $cycle: the SAME surface identity serves the next life",
            )
            assertEquals(1, fixture.providerRuns, "cycle $cycle: the provider resolved once for THIS life")

            session.close()
            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
                session.awaitTermination(),
                "cycle $cycle: the identity's life ends with the exact host-requested outcome",
            )
            assertEquals(terminalOrder, fixture.recorder, "cycle $cycle: zero residue after THIS life")
            assertEquals(
                journalBaseline,
                KadreUikitSurfaceDriver.reportedFailureCount,
                "cycle $cycle: the re-attach reports no diagnostic",
            )
        }

        // LA PREUVE DE repos FINALE : l'identité est rendue après le DERNIER cycle aussi —
        // l'attach FINAL sur la même surface réussit et se termine proprement.
        val fixture = Fixture(identity, sharedWindowManager)
        val started = CompletableDeferred<KadreScope>()
        val session = attachedSession(host(fixture), started)
        started.await()
        session.close()
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.HostRequested),
            session.awaitTermination(),
            "the final re-attach after the last close still succeeds on the SAME identity",
        )
        assertEquals(terminalOrder, fixture.recorder, "the final life leaves zero residue")
    }

    // ========================================================================================
    // 4× drop offer/transfer — la source close, le claim, le budget PARTAGÉ.
    // ========================================================================================

    /**
     * La source de transfert COMPTEUSE — la source de PRODUCTION (`UIKitDropTransferSource`,
     * les entrées réelles du module) enveloppée d'un compteur de closes : le contrat « la source
     * est owned par le runtime » devient mesurable cycle après cycle.
     */
    private class CountingDropSource : DropTransferSource {
        var closeCount: Int = 0
            private set

        private val item = UIKitDropItem(
            checkNotNull(ukitDropSnapshot(listOf("public.utf8-plain-text"), displayName = "cycle.txt", sizeBytes = 3L)),
        )
        private val source = UIKitDropTransferSource(listOf(item))

        val singleItem: UIKitDropItem get() = item

        override val items: List<DropItemSource>
            get() = source.items

        override fun close() {
            closeCount += 1
            source.close()
        }
    }

    /**
     * Les cycles offre/transfert de drop : le budget PARTAGÉ limite 1 porte la sentinelle de
     * rétention D'UN CYCLE À L'AUTRE — une réservation retenue (un transfert jamais refermé, un
     * cleanup qui saute la déposition) rend l'accept du cycle suivant `ResourceLimitExceeded`.
     * La source compteur porte celle du close : exactement UNE par cycle, sur les deux chemins
     * cleanup (terminaison sans transfert ET close du transfert réclamé).
     */
    @Test
    fun dropOfferTransferCyclesReleaseSourceClaimAndBudget() = runBlocking {
        val sharedBudget = RuntimeDropTransferBudget(DROP_BUDGET_LIMIT)

        repeat(DROP_CYCLES) { cycle ->
            val source = CountingDropSource()
            val offer = RuntimeDropOffer(
                id = DropOfferId(cycle.toLong() + 1L),
                source = source,
                itemSources = listOf(
                    RuntimeDropItemSource(source.singleItem, source.singleItem.descriptor, source.singleItem.readMode),
                ),
                resources = KadrePolicies.Default.resources,
                transferBudget = sharedBudget,
            )

            // L'ACCEPT : le budget partagé rend son slot à CHAQUE cycle — un transfert retenu
            // par le cycle PRÉCÉDENT rendrait cet accept `ResourceLimitExceeded`.
            assertEquals(
                KadreResult.Success(Unit),
                offer.accept(),
                "cycle $cycle: the shared budget slot was released by the previous cycle",
            )

            if (cycle % 2 == 0) {
                // LE CHEMIN TRANSFERT : perform → claim → close du transfert. La source owned
                // est fermée EXACTEMENT UNE FOIS par le close du transfert, et le claim est
                // unique — un second claim ne ressuscite rien.
                val transfer = assertNotNull(
                    offer.perform(scope = null, onTransferClosed = { }),
                    "cycle $cycle: the accepted offer performs a transfer",
                )
                val claimed = assertIs<KadreResult.Success<org.graphiks.kadre.input.DropTransfer>>(
                    offer.claimTransfer(),
                )
                assertEquals(
                    KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.DropTransfer)),
                    offer.claimTransfer(),
                    "cycle $cycle: a second claim is refused — no resurrected claim",
                )
                claimed.value.close()
                assertEquals(
                    1,
                    source.closeCount,
                    "cycle $cycle: the transfer close closed the owned source exactly once",
                )
                assertEquals(
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer)),
                    claimed.value.items.single().collectBytes(8) { },
                    "cycle $cycle: a read after the close is the closed failure — no orphan buffer",
                )
            } else {
                // LE CHEMIN TERMINAISON SANS TRANSFERT : la source est fermée PAR LE CLEANUP
                // (le transfert n'existe pas), exactement une fois, et l'offre terminée ne
                // ressuscite JAMAIS un transfert.
                assertTrue(offer.terminate(DropOfferTerminationReason.LeftSurface), "cycle $cycle: terminated once")
                assertFalse(offer.terminate(DropOfferTerminationReason.LeftSurface), "cycle $cycle: no second terminal")
                assertEquals(
                    1,
                    source.closeCount,
                    "cycle $cycle: the cleanup closed the owned source exactly once",
                )
                assertEquals(
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer)),
                    offer.claimTransfer(),
                    "cycle $cycle: a terminated offer never resurrects a transfer",
                )
            }
        }
    }

    // ========================================================================================
    // 4× flux capture surface — open → N frames → stop → slot/EN VOL au repos.
    // ========================================================================================

    /**
     * Le cycle du flux capture surface sur LE PORT RÉEL (jetons de seam) : la réservation est
     * ADMISE (delta compté), N ticks livrent N frames FIFO, l'arrêt est le terminal EXACT
     * `Stopped(Requested)` une fois, le ticker est désamorcé une fois, un tick TARDIF ne dessine
     * plus rien (le jeton scripté n'est PAS consommé — le compteur de frames GÈLE) et la
     * déposition de la réservation est comptée exactement une fois — EN VOL à zéro au repos de
     * chaque cycle. Une réservation qui fuite son slot ou son ticker échoue ICI.
     */
    @Test
    fun captureSurfaceStreamCyclesReturnToZeroInFlight() = runBlocking {
        repeat(CAPTURE_STREAM_CYCLES) { cycle ->
            val before = UIKitCaptureDiagnostics.snapshot()
            val snapshots = FakeSnapshotSource()
            val tickerCreated = CompletableDeferred<FakeTickSource>()
            val port = UIKitCapturePort(
                source = FakeCaptureSource(),
                surfaces = UIKitHostCaptureSurfaceRegistry(),
                surfaceSnapshots = snapshots,
                surfaceTickSources = { FakeTickSource().also { tickerCreated.complete(it) } },
            )
            port.registerHostCaptureSurface(SurfaceId(0L), ViewToken("cycle-$cycle"))

            val reservation = assertIs<KadreResult.Success<CapturePortReservation>>(
                port.reserve(CapturePortTarget.Surface(SurfaceId(0L)), CaptureRequest()),
                "cycle $cycle: the surface arm admits the reservation",
            ).value
            assertEquals(
                before.surfaceReservationsOpened + 1,
                UIKitCaptureDiagnostics.snapshot().surfaceReservationsOpened,
                "cycle $cycle: the admission is counted exactly once",
            )

            val listener = RecordingCaptureListener()
            val started = async(Dispatchers.Default) { reservation.start(listener, MAX_FRAME_BYTES) }
            val ticker = tickerCreated.await()
            // LE MOLDE T3 : LE PREMIER tick (il stage la payload qui dérive la configuration),
            // puis l'attente BORNÉE du start (le settled), PUIS les ticks suivants — un tick
            // d'avant-installation est un drop COMPTE, jamais une frame.
            ticker.tick()
            awaitAtRest("cycle $cycle: the stream start completed") { started.isCompleted }
            val stream = assertIs<KadreResult.Success<CapturePortStreamStart>>(started.await()).value.stream
            repeat(FRAMES_PER_CYCLE - 1) { ticker.tick() }
            awaitAtRest("cycle $cycle: ${listener.frames.size}/${FRAMES_PER_CYCLE} frames delivered") {
                listener.frames.size == FRAMES_PER_CYCLE
            }
            assertEquals(
                FRAMES_PER_CYCLE.toLong(),
                UIKitCaptureDiagnostics.snapshot().surfaceFramesDelivered - before.surfaceFramesDelivered,
                "cycle $cycle: N ticks delivered exactly N frames",
            )

            stream.close()
            assertEquals(1, listener.terminations.size, "cycle $cycle: exactly one terminal")
            val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
            assertEquals(
                CaptureOutcome.Stopped(CaptureStopReason.Requested),
                termination.outcome,
                "cycle $cycle: the stream stop is the requested stop",
            )
            awaitAtRest("cycle $cycle: ticker stopped") { ticker.stopCount == 1 }

            // LE SOAK D'ABSENCE : un tick TARDIF ne dessine RIEN (le jeton n'est pas consommé)
            // et ne livre RIEN (le compteur de frames GÈLE — l'assertion « zéro frame après
            // l'arrêt »).
            val framesAtTerminal = listener.frames.size
            val deliveredAtTerminal = UIKitCaptureDiagnostics.snapshot().surfaceFramesDelivered
            val snapshotsAtTerminal = snapshots.snapshotCount
            ticker.tick()
            awaitAtRest("cycle $cycle: frozen counters after the late tick") {
                listener.frames.size == framesAtTerminal &&
                    UIKitCaptureDiagnostics.snapshot().surfaceFramesDelivered == deliveredAtTerminal
            }
            assertEquals(
                snapshotsAtTerminal,
                snapshots.snapshotCount,
                "cycle $cycle: the late tick drew nothing — the staged token was never consumed",
            )

            reservation.close()
            port.close()
            val after = UIKitCaptureDiagnostics.snapshot()
            assertEquals(
                before.surfaceReservationsOpened + 1,
                after.surfaceReservationsOpened,
                "cycle $cycle: one reservation per cycle",
            )
            assertEquals(
                0,
                after.surfaceReservationsInFlight,
                "cycle $cycle: the reservation deposition happened exactly once — nothing in flight at rest",
            )
        }
    }

    // ========================================================================================
    // 4× chaque port SDK — display / device / gamepad : registrations rendues, seams propres.
    // ========================================================================================

    /** Le seam display compteur — l'inventaire piloté, les listeners comptés, le close compté. */
    private class CountingDisplaySource : UIKitDisplaySource {
        val registered = ArrayList<() -> Unit>()
        var closedCount: Int = 0
            private set

        private val hostToken = Any()
        private val airplayToken = Any()
        private val facts = mapOf(
            hostToken to UIKitDisplayFacts(1L, "Host screen", 2048.0, 1536.0, 2.0, isMain = true),
            airplayToken to UIKitDisplayFacts(2L, "AirPlay", 1920.0, 1080.0, 1.0, isMain = false),
        )

        fun listenerCount(): Int = registered.size

        fun hostFacts(): UIKitDisplayFacts? = facts[hostToken]

        override fun screens(): List<Any> = listOf(hostToken, airplayToken)

        override fun isMain(screen: Any): Boolean = facts[screen]?.isMain == true

        override fun factsOf(screen: Any): UIKitDisplayFacts? = facts[screen]

        override fun registerListener(listener: () -> Unit) {
            registered.add(listener)
        }

        override fun unregisterListener(listener: () -> Unit) {
            registered.remove(listener)
        }

        override fun close() {
            closedCount += 1
        }
    }

    /** Le port display en cycles : une registration à l'install, zéro au close, seam fermé une fois. */
    @Test
    fun displayPortCyclesReleaseEveryRegistrationAndCloseTheOwnedSeamOnce() {
        val baselines = ArrayList<Int>()
        repeat(PORT_CYCLES) { cycle ->
            val source = CountingDisplaySource()
            val port = UIKitDisplayPort(
                source = source,
                hostScreenResolver = { source.hostFacts() },
                displayTypeRuling = { isHostScreen ->
                    if (isHostScreen) DisplayType.HostViewport else DisplayType.Physical
                },
            )

            // La publication initiale SYNCHRONE de l'install — le contrat du port (assertion
            // directe, aucune course à attendre).
            var published: KadreResult<DisplayPortSnapshot>? = null
            port.installSnapshotObserver { snapshot -> published = snapshot }
            val snapshot = assertIs<KadreResult.Success<DisplayPortSnapshot>>(
                assertNotNull(published, "cycle $cycle: the initial inventory is published synchronously"),
            )
            assertEquals(2, snapshot.value.displays.size, "cycle $cycle: the inventory is the full screen pair")
            assertEquals(1, source.listenerCount(), "cycle $cycle: exactly ONE SDK registration at install")

            port.close()
            assertEquals(0, source.listenerCount(), "cycle $cycle: the close unregistered every SDK listener")
            assertEquals(1, source.closedCount, "cycle $cycle: the owned seam is closed EXACTLY once")

            // L'INVENTAIRE DÉTERMINISTE d'un cycle à l'autre : une table qui fuite grossirait.
            baselines += snapshot.value.displays.size
        }
        assertTrue(baselines.all { it == baselines.first() }, "the inventory is deterministic across cycles")
    }

    /** Le seam contrôleur compteur (le même jeton que les tests manette/device du module). */
    private class CountingControllerSource : UIKitControllerSource {
        private val tokens = linkedMapOf<String, Any>()
        private val facts = linkedMapOf<Any, UIKitControllerFacts>()
        private val names = linkedMapOf<Any, String?>()
        private val listeners = mutableListOf<() -> Unit>()
        var closeCount: Int = 0
            private set

        fun connect(name: String, controllerFacts: UIKitControllerFacts): Any {
            val token = Any()
            tokens[name] = token
            facts[token] = controllerFacts
            names[token] = name
            return token
        }

        fun listenerCount(): Int = listeners.size

        override fun controllers(): List<Any> = tokens.values.toList()

        override fun registerListener(listener: () -> Unit) {
            listeners += listener
        }

        override fun unregisterListener(listener: () -> Unit) {
            listeners.remove(listener)
        }

        override fun profileFacts(controller: Any): UIKitControllerFacts? = facts[controller]

        override fun nameOf(controller: Any): String? = names[controller]

        override fun isExtendedGamepad(controller: Any): Boolean =
            facts[controller]?.isExtendedGamepad == true

        override fun isMicroGamepadOnly(controller: Any): Boolean =
            facts[controller]?.let { it.isMicroGamepad && !it.isExtendedGamepad } == true

        override fun close() {
            closeCount += 1
        }
    }

    /** Le seam d'effets factice — AUCUN effet ne démarre dans ces cycles de repos. */
    private class InertEffectsSeam : UIKitHapticsEffectSeam {
        override fun startEffect(
            controller: Any,
            effect: GamepadEffect.LocalizedHaptic,
        ): KadreResult<UIKitGamepadEffectHandle> = error("no effect ever starts in retention cycles")
    }

    /**
     * Le port manette en cycles : une registration SDK par port vivant, ZÉRO au close (la
     * libération du DERNIER port — le miroir comptable de l'inventaire process), le seam
     * JAMAIS fermé par le port (la couture d'attach le possède), et l'inventaire projeté
     * DÉTERMINISTE d'un cycle à l'autre (une table `pads` qui fuite grossirait).
     */
    @Test
    fun gamepadPortCyclesReleaseTheObservationAndNeverCloseTheSeam() {
        var baselineGamepadName: String? = null
        repeat(PORT_CYCLES) { cycle ->
            val source = CountingControllerSource()
            source.connect("pad", UIKitControllerFacts(isExtendedGamepad = true))
            // LE PORT S'OUVRE PAR L'INVENTAIRE (la voie de production) : c'est l'open qui
            // enregistre le port — une construction directe ne porterait AUCUNE registration.
            val port = UIKitControllerInventory(source, InertEffectsSeam()).openGamepadPort()

            var events = 0
            port.installObserver { events += 1 }
            assertEquals(1, source.listenerCount(), "cycle $cycle: exactly ONE SDK registration")

            val inventory = port.gamepads
            assertEquals(1, inventory.size, "cycle $cycle: the projected inventory stays bounded")
            val name = inventory.single().descriptor.name
            if (baselineGamepadName == null) {
                baselineGamepadName = name
            } else {
                assertEquals(baselineGamepadName, name, "cycle $cycle: the inventory is deterministic across cycles")
            }

            port.close()
            assertEquals(0, source.listenerCount(), "cycle $cycle: the LAST port released the observation")
            assertEquals(0, source.closeCount, "cycle $cycle: the port NEVER closes the owned seam")
            assertEquals(0, events, "cycle $cycle: no event ever fired in a quiet cycle")
        }
    }

    /** Le port device générique en cycles : la même discipline que la manette. */
    @Test
    fun inputDevicePortCyclesReleaseTheObservationAndNeverCloseTheSeam() {
        repeat(PORT_CYCLES) { cycle ->
            val source = CountingControllerSource()
            source.connect("keyboard", UIKitControllerFacts(isKeyboard = true))
            val port = UIKitControllerInventory(source, InertEffectsSeam()).openInputDevicePort()

            port.installObserver { }
            assertEquals(1, source.listenerCount(), "cycle $cycle: exactly ONE SDK registration")
            assertEquals(1, port.devices.size, "cycle $cycle: the live inventory stays bounded")

            port.close()
            assertEquals(0, source.listenerCount(), "cycle $cycle: the LAST port released the observation")
            assertEquals(0, source.closeCount, "cycle $cycle: the port NEVER closes the owned seam")
        }
    }

    // ========================================================================================
    // 4× créneaux de la source mémoire process — wired/terminated, le dernier libéré ferme.
    // ========================================================================================

    /** Le jeton d'observer — le seam factice ne voit jamais un vrai token du centre. */
    private class FakeObserverToken : NSObject(), NSObjectProtocol

    /** Le seam mémoire factice — la registration pilotée, le relais hors pile à drainer. */
    private class FakeMemoryPressureSeam : UIKitMemoryPressureSeam {
        var registeredNames = ArrayList<String>()
            private set
        var unregisteredCount: Int = 0
            private set
        private var observerBlock: (() -> Unit)? = null
        private val posted = ArrayDeque<() -> Unit>()

        override fun observe(name: String, onEvent: () -> Unit): NSObjectProtocol {
            registeredNames.add(name)
            observerBlock = onEvent
            return FakeObserverToken()
        }

        override fun removeObserver(token: NSObjectProtocol) {
            unregisteredCount += 1
        }

        override fun post(block: () -> Unit) {
            posted.addLast(block)
        }

        fun fire() {
            observerBlock?.invoke()
        }

        fun drain() {
            while (posted.isNotEmpty()) posted.removeFirst().invoke()
        }
    }

    /**
     * Les cycles de créneau mémoire : le claim installe la source process (capability
     * `Available`), le DERNIER libéré la referme (capability `Unsupported`, l'EXACT token
     * retiré une fois). Le cycle à DEUX hosts prouve « la source reste jusqu'à la DERNIÈRE
     * relâche » : le premier host terminé ne désenregistre RIEN et la capability reste
     * `Available` — un hold qui fuite garderait la source vivante et se lirait ICI (le cycle
     * suivant ne ré-enregistrerait jamais : `registeredNames` stagnerait, `unregisteredCount`
     * pointerait à côté). Un warning livré HORS pile arrive `Moderate`, JAMAIS `Critical`.
     */
    @Test
    fun memoryPressureClaimCyclesCloseOnlyAtTheLastRelease() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()

        repeat(MEMORY_CLAIM_CYCLES) { cycle ->
            val registrationsAtEntry = seam.registeredNames.size
            val unregisteredAtEntry = seam.unregisteredCount
            val received = ArrayList<MemoryPressureLevel>()

            if (cycle == TWO_HOST_CYCLE) {
                // LE CYCLE À DEUX HOSTS : la source VIVANTE est réutilisée — pas une seconde
                // registration ; le premier libéré ne referme RIEN ; le DERNIER referme.
                val first = owner.newHost(seam)
                val last = owner.newHost(seam)
                assertEquals(
                    registrationsAtEntry + 1,
                    seam.registeredNames.size,
                    "cycle $cycle: the cycle's fresh source registers ONCE — the second host NEVER re-registers",
                )
                first.hostTerminated()
                assertEquals(
                    FeatureAvailability.Available,
                    owner.currentCapabilities.memoryPressure,
                    "cycle $cycle: the source stays until the LAST release",
                )
                assertEquals(unregisteredAtEntry, seam.unregisteredCount, "cycle $cycle: no early unregister")
                last.hostTerminated()
            } else {
                val claim = owner.newHost(seam)
                assertEquals(
                    FeatureAvailability.Available,
                    claim.capabilities.memoryPressure,
                    "cycle $cycle: the fresh source installs Available",
                )
                claim.wire { level -> received.add(level) }
                seam.fire()
                seam.drain()
                assertEquals(
                    listOf(MemoryPressureLevel.Moderate),
                    received,
                    "cycle $cycle: the warning is relayed off-stack, Moderate only",
                )
                assertFalse(
                    received.any { it == MemoryPressureLevel.Critical },
                    "cycle $cycle: Critical is never produced on UIKit/tvOS",
                )
                claim.hostTerminated()
            }

            assertEquals(
                FeatureAvailability.Unsupported,
                owner.currentCapabilities.memoryPressure,
                "cycle $cycle: the capability returns to Unsupported at rest",
            )
            assertEquals(
                unregisteredAtEntry + 1,
                seam.unregisteredCount,
                "cycle $cycle: the last release removed the EXACT token, once",
            )
        }
        assertEquals(MEMORY_CLAIM_CYCLES, seam.unregisteredCount, "one source lifetime per cycle, closed each time")
    }

    // ========================================================================================
    // 5× receiver texte — installé/retiré, zéro composition orpheline.
    // ========================================================================================

    /**
     * Les cycles install/remove du receiver texte RÉEL (l'objet UIKit retenu par l'owner du
     * port — la matière ARC de la session texte) sur UNE SEULE hiérarchie hôte partagée par
     * les cinq cycles : le receiver du cycle est posé (la séquence d'install de l'owner,
     * l'amorçage au document de la config comprise), la composition marquée IME est posée
     * PENDANT la session (l'état vivant), puis le receiver QUITTE la hiérarchie — zéro
     * sous-vue orpheline, la composition meurt AVEC le receiver, et RIEN ne s'accumule d'un
     * cycle à l'autre (un teardown sauté laisserait le receiver du cycle précédent : le compte
     * de sous-vues du cycle suivant échoue, tout comme la sonde `receiverAttached`).
     *
     * LA PORTE de la mesure : le hôte de test K/N n'est pas un process applicatif — ni fenêtre
     * clé, ni focus (l'open de session avec first responder est l'affaire du driver, BCK-024,
     * sur l'app réelle, les deux familles). Ce que CE test mesure est l'unité de rétention du
     * cycle install/remove — le receiver lui-même — avec les sondes exportées du driver comme
     * lecteurs ; la vie COUNTÉE du port (installs/teardowns) reste driver-fed.
     */
    @Test
    fun textInputReceiverCyclesLeaveNoSubviewAndNoOrphanComposition() {
        val hostView = UIView(frame = CGRectMake(0.0, 0.0, 320.0, 120.0))

        repeat(TEXT_INPUT_CYCLES) { cycle ->
            val receiver = UIKitTextInputReceiver(
                config = TextInputConfig(
                    purpose = TextInputPurpose.Text,
                    action = TextInputAction.Default,
                    surroundingText = "cycle",
                    selection = TextRange(0, 0),
                ),
                hooks = UIKitTextInputReceiver.Hooks(
                    onShouldChange = { _, _ -> true },
                    onTextDidChange = { },
                    onReturn = { true },
                    onFirstResponderGained = { },
                    onFirstResponderLost = { },
                ),
            )

            // INSTALLÉ : la séquence d'install de l'owner (l'amorçage au document de la
            // config comprise) — le receiver est L'unique sous-vue du host.
            hostView.addSubview(receiver)
            receiver.writeBack("cycle", 0L, 0L)
            val probe = KadreUikitSurfaceDriver.textInputProbe(hostView)
            assertTrue(probe.receiverAttached, "cycle $cycle: the receiver is attached to the host view")
            assertTrue(probe.receiverInHierarchy, "cycle $cycle: the receiver lives in the hierarchy")
            assertEquals("cycle", probe.fieldText, "cycle $cycle: the config document is sown at install")
            assertEquals(1, hostView.subviews.orEmpty().size, "cycle $cycle: exactly the receiver is installed")

            // LA COMPOSITION VIVANTE : un marqué posé pendant la session (l'état IME réel).
            assertTrue(
                KadreUikitSurfaceDriver.receiverSetMarkedText(hostView, "e", 1, 1),
                "cycle $cycle: the marked composition is set on the live receiver",
            )
            val composing = KadreUikitSurfaceDriver.textInputProbe(hostView)
            assertTrue(
                composing.fieldMarkedLength > 0,
                "cycle $cycle: the composition is live before the removal",
            )

            // RETIRÉ : le receiver quitte la hiérarchie — zéro sous-vue orpheline, la
            // composition marquée meurt AVEC le receiver (rien ne survit au cycle dans le
            // host, rien ne s'accumule pour le suivant).
            receiver.removeFromSuperview()
            val rest = KadreUikitSurfaceDriver.textInputProbe(hostView)
            assertFalse(rest.receiverAttached, "cycle $cycle: no receiver survives the removal")
            assertTrue(
                hostView.subviews.orEmpty().isEmpty(),
                "cycle $cycle: NO orphan subview survives — nothing accumulates across cycles",
            )
        }
    }

    // ========================================================================================
    // 4× demandes de fenêtre — open/resolve/abandon, zéro session ressuscitée.
    // ========================================================================================

    /** Le manager runtime délégué compteur — le close des scènes ne passe JAMAIS par lui. */
    private class CountingRuntimeManager : WindowManager {
        var closeCalls = 0
            private set
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
            error("the scene manager must not delegate requestWindow")

        fun close() {
            closeCalls += 1
        }
    }

    /** Une session enfant factice — les demandes d'arrêt enregistrées (la vie indépendante). */
    private class FakeChildSession(override val id: SessionId) : KadreSession {
        val stops = mutableListOf<String>()
        private val mutableState = MutableStateFlow<SessionState>(SessionState.Running)

        override val state: kotlinx.coroutines.flow.StateFlow<SessionState> = mutableState.asStateFlow()

        override fun close() {
            stops += "close"
        }

        override fun requestStop() {
            stops += "requestStop"
        }

        override suspend fun awaitTermination(): SessionOutcome = SessionOutcome.Completed
    }

    /** Le provider double — enregistre chaque identité admise. */
    private class RecordingProvider : UIKitWindowSceneProvider {
        val opened = mutableListOf<WindowRequestId>()

        override fun open(
            requestId: WindowRequestId,
            spec: WindowSpec,
            resolver: UIKitWindowSceneRequestResolver,
        ) {
            opened += requestId
        }
    }

    private fun outcomeOf(request: WindowRequest): WindowRequestOutcome =
        assertIs<WindowRequestState.Terminated>(request.state.value).outcome

    /**
     * Les cycles de demande de fenêtre, budget maxPending=1 : la demande pend, le resolve
     * attache un enfant INDÉPENDANT (jamais arrêté par le parent), l'abandon termine
     * `Cancelled`, la résolution TARDIVE d'une demande terminale ne recharge rien et l'enfant
     * TARDIF d'un double resolve est détaché (jamais la réponse) — zéro session ressuscitée.
     * Le slot est rendu à CHAQUE cycle : la demande suivante est admise (une entrée pendante
     * qui fuite rendrait la suivante `ResourceLimitExceeded`).
     */
    @Test
    fun windowRequestOpenResolveAbandonCyclesNeverResurrectSessions() = runBlocking {
        val runtime = CountingRuntimeManager()
        var nextChildId = 100L
        val windows = UIKitWindowSceneManager(
            runtimeManager = runtime,
            provider = RecordingProvider(),
            policy = KadrePolicies.Default.copy(
                resources = KadrePolicies.Default.resources.copy(maxPendingWindowRequests = 1),
            ),
            applicationFactory = { error("the cycles attach children through completeResolve") },
            failureReporter = { },
            parentScope = CoroutineScope(Dispatchers.Main),
            childSessionFactory = { KadreResult.Success(FakeChildSession(SessionId(nextChildId++))) },
        )

        repeat(WINDOW_REQUEST_CYCLES) { cycle ->
            val request = assertIs<KadreResult.Success<WindowRequest>>(
                windows.requestWindow(WindowSpec(title = "cycle-$cycle")),
                "cycle $cycle: the request is admitted — the previous cycle returned its slot",
            ).value
            assertEquals(
                WindowRequestState.Pending,
                request.state.value,
                "cycle $cycle: pending until the connection",
            )

            if (cycle % 2 == 0) {
                // LE RESOLVE : l'enfant attaché est INDÉPENDANT — le parent ne l'arrête jamais.
                val child = FakeChildSession(SessionId(nextChildId++))
                val entry = assertIs<UIKitWindowScenePending.Entry>(windows.claimResolve(request.id))
                windows.completeResolve(entry) { KadreResult.Success(child) }
                assertEquals(
                    WindowRequestOutcome.OpenedInNewSession(child.id),
                    outcomeOf(request),
                    "cycle $cycle: the resolve opens the child in a new session",
                )
                assertTrue(child.stops.isEmpty(), "cycle $cycle: the child is independent — never stopped")

                // LE RÉSOLVEUR TARDIF : un second attach de la MÊME entrée est un no-op —
                // l'enfant tardif est détaché, l'outcome publié NE CHANGE JAMAIS.
                val stray = FakeChildSession(SessionId(nextChildId++))
                windows.completeResolve(entry) { KadreResult.Success(stray) }
                assertEquals(
                    listOf("requestStop"),
                    stray.stops,
                    "cycle $cycle: the LATE child is detached — never resurrected as the answer",
                )
                assertEquals(
                    WindowRequestOutcome.OpenedInNewSession(child.id),
                    outcomeOf(request),
                    "cycle $cycle: the outcome stands",
                )
                // L'ABANDON TARDIF d'une demande terminale : un no-op, jamais de réécriture.
                windows.abandon(request.id, "late-abandon")
                assertEquals(
                    WindowRequestOutcome.OpenedInNewSession(child.id),
                    outcomeOf(request),
                    "cycle $cycle: the late abandon never rewrites the terminal",
                )
            } else {
                // L'ABANDON : le discard app-side termine `Cancelled`, AUCUN enfant n'est
                // attaché, et une résolution tardive ne recharge rien (claimResolve null).
                windows.abandon(request.id, "activation-refused-cycle-$cycle")
                assertEquals(
                    WindowRequestOutcome.Cancelled,
                    outcomeOf(request),
                    "cycle $cycle: the abandon cancels the pending request",
                )
                assertNull(
                    windows.claimResolve(request.id),
                    "cycle $cycle: a late resolve after the terminal is refused — no resurrection",
                )
            }
        }

        // La fermeture du manager : idempotent, le runtime partagé n'est JAMAIS fermé par lui.
        windows.close()
        windows.close()
        assertEquals(0, runtime.closeCalls, "the scene manager never closes the shared runtime manager")

        // Le budget final : APRÈS le close, l'admission est close (`Closed(Host)`) — aucune
        // demande ne peut ressusciter derrière le drain.
        assertEquals(
            KadreFailure.Closed(KadreResourceKind.Host),
            assertIs<KadreResult.Failure>(windows.requestWindow(WindowSpec())).reason,
            "the closed manager refuses every further request",
        )
    }

    private companion object {
        /** 10 cycles attach/stop, 5 ré-attach — le volume du moule Android (10 = 5 Kotlin + 5 façade). */
        const val ATTACH_STOP_CYCLES = 10
        const val REATTACH_CYCLES = 5

        /** 4 cycles drop / capture / ports / mémoire / fenêtres ; 5 sessions texte. */
        const val DROP_CYCLES = 4
        const val CAPTURE_STREAM_CYCLES = 4
        const val PORT_CYCLES = 4
        const val MEMORY_CLAIM_CYCLES = 4
        const val TEXT_INPUT_CYCLES = 5
        const val WINDOW_REQUEST_CYCLES = 4

        /** L'index du cycle mémoire à DEUX hosts (« la source reste jusqu'à la dernière relâche »). */
        const val TWO_HOST_CYCLE = 2

        /** Le budget drop PARTAGÉ : limite 1 — la sentinelle de rétention d'un cycle à l'autre. */
        const val DROP_BUDGET_LIMIT = 1

        /** Les frames d'UN cycle capture (N ticks → N frames FIFO). */
        const val FRAMES_PER_CYCLE = 3

        /** La borne de frame d'un start capture (tout le raster du fake passe largement). */
        const val MAX_FRAME_BYTES = 1L shl 20

        /** La discipline d'attente du moule Android : poll 25 ms, deadline 10 s. */
        const val AWAIT_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 25L
    }
}
