package org.graphiks.kadre.internal.wayland

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.SessionPhase
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeProcessController
import org.graphiks.kadre.internal.runtime.RuntimeBootRequest
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionStopHandler
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopRunRequest
import org.graphiks.kffi.posix.PosixWakeup

/**
 * Provider Wayland tranche 2 : session standalone avec fenêtres réelles.
 * Le driver expose le [RuntimeWindowManager] réel à la place du manager
 * headless de la phase 1 ; la règle du dernier conteneur vient du
 * contrôleur de process (fin de session quand les fenêtres se vident).
 */
public class WaylandBackendProvider private constructor(
    private val availability: () -> Boolean,
    private val environment: () -> WaylandProbeEnvironment,
    private val bridgeFactory: (String?) -> WaylandNativeBridge,
    private val wakeFactory: () -> PosixWakeup,
    private val loopStarted: ((WaylandEventPump) -> Unit)?,
) : DesktopBackendProvider {
    public constructor() : this(
        ::isLinux,
        { WaylandProbeEnvironment.fromSystem() },
        { name -> KffiWaylandBridge.connect(name) },
        { PosixWakeup.open() },
        null,
    )

    override val backend: DesktopBackendKind = DesktopBackendKind.Wayland

    override fun isAvailable(): Boolean = WaylandProbe.check(environment()).available

    /**
     * Session standalone avec fenêtres réelles (tranche 2) : la pompe tourne
     * sur le thread appelant, la file d'actions marshalle les créations vers
     * ce thread, et le contrôleur de process termine la session quand sa
     * dernière fenêtre se ferme.
     */
    override fun run(request: DesktopRunRequest): KadreResult<SessionOutcome> {
        if (!availability()) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
        }
        if (!hostLease.compareAndSet(false, true)) {
            return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        }
        try {
            val session = try {
                WaylandHostSession.start(bridgeFactory, socketName(), environment(), wakeFactory)
            } catch (start: WaylandSessionStartException) {
                return KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "start", start.code))
            } catch (unexpected: Exception) {
                return KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "start", "start-unexpected"))
            }
            try {
                return runSession(request, session)
            } finally {
                session.teardown()
            }
        } finally {
            hostLease.set(false)
        }
    }

    private fun runSession(
        request: DesktopRunRequest,
        session: WaylandHostSession,
    ): KadreResult<SessionOutcome> {
        val driver = WaylandWindowRuntimeDriver.create(
            session = session,
            resources = request.policy.resources,
            failureReporter = RuntimeFailureReporter { },
            publicWindowCapabilities = true,
            // No driver-side last-window wiring: the controller's last-container rule
            // alone owns the Completed outcome, then the stop handler stops the pump.
            // A pump-side stop here would race terminate(SystemTermination) against
            // the controller watcher's Completed and make the outcome interleaving-
            // dependent (same determinism as AppKit, which wires nothing here).
            onLastWindowClosed = null,
        )
        val controller = RuntimeProcessController.withComponents(
            platform = KadrePlatform.Wayland,
            componentsFactory = { _, _ ->
                val displayPort = WaylandDisplayPort(
                    outputsProvider = session.bridge::outputSnapshots,
                    rescan = session.bridge::onOutputsChanged,
                )
                val devicePort = WaylandInputDevicePort(session.bridge::seatDeviceSnapshots)
                session.bridge.onSeatProjectionChanged { devicePort.seatInventoryChanged() }
                RuntimeSessionComponents(
                    windows = driver.manager,
                    displayPort = displayPort,
                    inputDevicePort = devicePort,
                    // the evdev service replaces the phase-9 honest
                    // refusal: Wayland itself carries no gamepad
                    // protocol, evdev serves them process-wide (D6)
                    gamepadPort = org.graphiks.kadre.internal.linux.EvdevGamepadService.openSessionView(),
                    // capture rides the portal control plane (phase 10)
                    capturePort = org.graphiks.kadre.internal.linux.capture.LinuxCaptureService.create(),
                )
            },
            initialLifecycleState = LifecycleState(
                SessionPhase.Running,
                VisibilityState.Background,
                ActivationState.Inactive,
            ),
            sessionStopHandler = RuntimeSessionStopHandler { _ ->
                session.pump.requestStop()
                null
            },
        )
        val inputRouter = WaylandInputRouter()
        session.bridge.attachSeat(inputRouter)
        val booted = controller.boot(
            RuntimeBootRequest(
                platform = KadrePlatform.Wayland,
                applicationFactory = request.applicationFactory,
                policy = request.policy,
                firstWindow = request.firstWindow,
                mainDispatcher = request.mainDispatcher,
            ),
        )
        if (booted is KadreResult.Failure) {
            return booted
        }
        val kadreSession = (booted as KadreResult.Success).value
        loopStarted?.invoke(session.pump)
        var outcome: SessionOutcome? = null
        while (outcome == null) {
            session.drainWindowActions()
            outcome = when (session.pump.runOnce()) {
                PumpStep.Dispatched, PumpStep.Rearm -> null
                PumpStep.Stopped -> {
                    controller.terminate(SessionStopReason.SystemTermination)
                    runBlocking { kadreSession.awaitTermination() }
                }
                PumpStep.ConnectionLost -> {
                    controller.fail(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "display", "connection-lost"))
                    runBlocking { kadreSession.awaitTermination() }
                }
            }
        }
        return KadreResult.Success(outcome)
    }

    private fun socketName(): String? = environment().waylandDisplay?.takeIf { it.isNotEmpty() }

    public companion object {
        private val hostLease = AtomicBoolean(false)

        internal fun forTesting(
            availability: () -> Boolean = ::isLinux,
            environment: () -> WaylandProbeEnvironment = { WaylandProbeEnvironment.fromSystem() },
            bridgeFactory: (String?) -> WaylandNativeBridge = { name -> KffiWaylandBridge.connect(name) },
            wakeFactory: () -> PosixWakeup = { PosixWakeup.open() },
            loopStarted: ((WaylandEventPump) -> Unit)? = null,
        ): WaylandBackendProvider = WaylandBackendProvider(availability, environment, bridgeFactory, wakeFactory, loopStarted)
    }
}
