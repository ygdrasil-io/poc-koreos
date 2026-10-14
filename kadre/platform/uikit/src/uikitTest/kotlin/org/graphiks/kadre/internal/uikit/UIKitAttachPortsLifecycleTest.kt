package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.HostSignal
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayManagerState
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadEffectSession
import org.graphiks.kadre.input.GamepadEffectState
import org.graphiks.kadre.input.GamepadEffectOutcome
import org.graphiks.kadre.input.GamepadEffectStopReason
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadLocalizedHapticConstraints
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
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
 * Le round-trip d'ATTACH de la couture uikit phase 8 (Task 5) — l'intégration que les tests de
 * port individuels ne donnent pas : des components en forme EXACTE d'attach (miroir de la
 * fabrique de `KadreIos.attachScene` — manager de scènes réel sur un delegate enregistreur,
 * provider de surface PRIMAIRE différé, ports display + manette + device de session, closeAction
 * drain de scènes PUIS close runtime), attachés par le contrôleur hôte, et la surface
 * CONSOMMATRICE qui en sort : `KadreScope.displays`, `KadreScope.devices`, `KadreScope.lifecycle`
 * — le même accès public que le contrat consommateur Android
 * (`KadreConsumerDisplaysContractTest`), tout porté par les types foundation.
 *
 * Deux épingles :
 *
 * 1. LA SURFACE CONSOMMATRICE — l'inventaire display (HostViewport de la fenêtre + écran
 *    extérieur Physical, workArea null), l'inventaire device (manette + clavier), la capability
 *    mémoire `Available` posée par l'attach, la surface primaire du provider différé, et le
 *    signal mémoire de bout en bout (le relais factice — la MÊME lambda que `claim.wire` pose —
 *    → `KadreLifecycle.signals`) ;
 * 2. LE CHEMIN TERMINAL (critère de sortie roadmap : la scène qui se ferme PENDANT un effet) —
 *    un effet manette en vol est INTERROMPU (`Stopped(ParentSessionStopping)`, jamais
 *    `Completed`) et l'ordre complet du démontage est épinglé : observation display →
 *    interruption de l'effet (owner natif stoppé puis refermé) → surface primaire →
 *    `sceneManager.close()` → `runtime.close()` → ports display/manette/device — le
 *    sessionObserver de terminaison en DERNIER. Les ports sont fermés PAR les components
 *    (RuntimeSessionComponents.close), jamais par le closeAction — l'ordre lu à
 *    RuntimeSessionComponents.close et épinglé ICI.
 */
class UIKitAttachPortsLifecycleTest {

    // -- la forme d'attach enregistrante -------------------------------------------------------

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

    /** Le lot des components en forme d'attach — un seul jeu de ports par fixture. */
    private class Fixture {
        val recorder = mutableListOf<String>()
        val displayPort = RecordingDisplayPort(recorder)
        val gamepadPort = RecordingGamepadPort(recorder)
        val inputDevicePort = RecordingInputDevicePort(recorder)
        var providerRuns = 0

        /** La surface primaire factice du provider différé — la forme du double jvm des components. */
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
            return RuntimeSessionComponents(
                windows = sceneManager,
                primarySurfaceProvider = {
                    providerRuns += 1
                    RuntimePrimarySurface(recordingSurface) { recorder += "primary-surface-closed" }
                },
                displayPort = displayPort,
                gamepadPort = gamepadPort,
                inputDevicePort = inputDevicePort,
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
        // La capability que l'attach uikit pose quand la source mémoire process s'installe
        // (le créneau de `KadreIos.attachScene`, Task 3) : le memoryPressure devient Available.
        initialLifecycleCapabilities = LifecycleCapabilities(FeatureAvailability.Available),
        sessionObserver = RuntimeSessionObserver { _, _ -> onTerminated() },
    )

    private fun CoroutineScope.attachedSession(
        controller: RuntimeHostController,
        started: kotlinx.coroutines.CompletableDeferred<KadreScope>,
    ): org.graphiks.kadre.application.KadreSession =
        when (val attached = controller.attach(
            this,
            KadreApplicationFactory { KadreApplication { started.complete(this); awaitCancellation() } },
        )) {
            is KadreResult.Success -> attached.value
            is KadreResult.Failure -> error("expected attached session, got ${attached.reason}")
        }

    // -- la surface consommatrice --------------------------------------------------------------

    @Test
    fun theAttachedSessionExposesTheDisplayDeviceAndLifecycleConsumerSurface() = runBlocking {
        val fixture = Fixture()
        val controller = host(fixture)
        val started = CompletableDeferred<KadreScope>()

        val session = attachedSession(controller, started)
        val scope = started.await()

        // DISPLAYS : l'inventaire uikit projeté — le HostViewport de LA fenêtre primaire, l'écran
        // extérieur Physical, la workArea null (iOS/tvOS n'ont pas de bureau). `requestAccess`
        // répond le succès portant le même inventaire (la voie applicative du port).
        val displayState = scope.displays.state.value
        val displays = assertIs<DisplayInventory.Enumerated>(displayState.inventory)
        assertEquals(2, displays.displays.size, "the full UIScreen inventory is enumerated")
        val primary = assertNotNull(displays.primary, "the host screen is the primary")
        assertEquals(DisplayType.HostViewport, primary.state.value.type)
        assertNull(primary.state.value.workArea, "iOS/tvOS have no desktop: no invented work area")
        val external = displays.displays.single { it !== primary }
        assertEquals(DisplayType.Physical, external.state.value.type)
        val access = assertIs<KadreResult.Success<DisplayManagerState>>(scope.displays.requestAccess())
        assertIs<DisplayInventory.Enumerated>(access.value.inventory)

        // DEVICES : la manette (profil étendu) ET le clavier du port générique — un seul
        // DeviceManager pour les deux ports (miroir Android, les deux projections session).
        val devices = assertIs<DeviceInventory.Enumerated>(scope.devices.state.value.inventory)
        assertEquals(1, devices.gamepads.size)
        assertEquals("Controller 11", devices.gamepads.single().state.value.descriptor.name)
        assertEquals(1, devices.devices.size)
        assertEquals(InputDeviceKind.Keyboard, devices.devices.single().descriptor.kind)

        // LIFECYCLE : la capability posée par l'attach, la surface primaire du provider différé,
        // et le signal mémoire de bout en bout — le relais factice (la MÊME lambda que
        // `pressureClaim.wire` pose sur l'attach réussi) → `KadreScope.lifecycle.signals`.
        assertEquals(
            FeatureAvailability.Available,
            scope.lifecycle.capabilities.value.memoryPressure,
            "the memory pressure source is installed by the attach",
        )
        assertNotNull(scope.primarySurface.value, "the deferred primary surface is exposed after attach")
        assertEquals(1, fixture.providerRuns, "the deferred provider resolved exactly once")
        val relay: (MemoryPressureLevel) -> Unit = { level ->
            runCatching { controller.emitMemoryPressure(level) }
        }
        val signal = async(start = CoroutineStart.UNDISPATCHED) { scope.lifecycle.signals.first() }
        relay(MemoryPressureLevel.Moderate)
        val observed = assertIs<HostSignal.MemoryPressure>(signal.await())
        assertEquals(MemoryPressureLevel.Moderate, observed.level)

        session.close()
        session.awaitTermination()
        assertIs<SessionState.Terminated>(session.state.value)

        // Les ports de la session sont fermés PAR les components, exactement une fois chacun.
        assertEquals(1, fixture.recorder.count { it == "display-port-closed" })
        assertEquals(1, fixture.recorder.count { it == "gamepad-port-closed" })
        assertEquals(1, fixture.recorder.count { it == "input-device-port-closed" })
    }

    // -- le chemin terminal ---------------------------------------------------------------------

    @Test
    fun theTerminalPathInterruptsARunningEffectThenClosesSceneRuntimeThenPorts() = runBlocking {
        val fixture = Fixture()
        var observedTerminations = 0
        val controller = host(fixture) {
            observedTerminations += 1
            fixture.recorder += "host-terminated"
        }
        val started = CompletableDeferred<KadreScope>()

        val session = attachedSession(controller, started)
        val scope = started.await()

        // Un effet manette en vol, démarré par la SURFACE PUBLIQUE (le même accès que le
        // consommateur : `DeviceManager.gamepads` → `Gamepad.playEffect`).
        val gamepad = assertIs<DeviceInventory.Enumerated>(scope.devices.state.value.inventory)
            .gamepads.single()
        val effect = assertIs<KadreResult.Success<GamepadEffectSession>>(
            gamepad.playEffect(GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 60.seconds)),
        ).value
        assertEquals(GamepadEffectState.Playing, effect.state.value)
        assertEquals(
            listOf("effect-native-start"),
            fixture.recorder.filter { it.startsWith("effect") },
            "only the native start ran: nothing may stop an effect before the terminal path",
        )

        // LA scène se ferme PENDANT l'effet (critère de sortie roadmap) : l'effet est interrompu —
        // jamais Completed, la raison exacte est la terminaison de la session parente.
        session.close()
        val outcome = assertIs<GamepadEffectOutcome.Stopped>(effect.awaitTermination())
        assertEquals(GamepadEffectStopReason.ParentSessionStopping, outcome.reason)
        assertIs<GamepadEffectState.Terminated>(effect.state.value)

        session.awaitTermination()
        assertIs<SessionState.Terminated>(session.state.value)

        // L'ordre complet du démontage (lu à RuntimeSessionComponents.close et à
        // SessionRuntime.closeRuntimeComponents) : l'observation display meurt d'abord, l'effet
        // est interrompu (owner natif stoppé PUIS refermé), la surface primaire se détache, le
        // closeAction uikit draine les scènes PUIS ferme le runtime, et les ports ferment EN
        // DERNIER — display, manette, device — avant le sessionObserver de terminaison.
        // `effect-native-start` ouvre la liste : c'est la SEULE écriture d'effet avant le chemin
        // terminal (rien n'arrête un effet vivant avant la fermeture de SA session).
        assertEquals(
            listOf(
                "effect-native-start",
                "display-observation-closed",
                "effect-owner-requestStop",
                "effect-owner-closed",
                "primary-surface-closed",
                "scene-close",
                "runtime-close",
                "display-port-closed",
                "gamepad-port-closed",
                "input-device-port-closed",
                "host-terminated",
            ),
            fixture.recorder,
        )
        assertEquals(1, observedTerminations, "the termination observer runs exactly once")
    }
}
