package org.graphiks.kadre.internal.wayland

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionStopHandler
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kffi.posix.PosixWakeup

/**
 * Provider Wayland tranche 2 : session standalone avec fenêtres réelles.
 * Le driver expose le [RuntimeWindowManager] réel à la place du manager
 * headless de la phase 1 ; `stopWhenLastWindowClosed` passe par l'arming du
 * runtime après la première fenêtre admise.
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

    override val supportedIntegrations: Set<DesktopIntegrationKind> = emptySet()

    override fun isAvailable(): Boolean = WaylandProbe.check(environment()).available

    override fun attach(request: DesktopEmbeddedRequest): KadreResult<KadreSession> {
        return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
    }

    /**
     * Session standalone avec fenêtres réelles (tranche 2) : la pompe tourne
     * sur le thread appelant, la file d'actions marshalle les créations vers
     * ce thread, et `stopWhenLastWindowClosed` s'arme via le runtime après la
     * première fenêtre admise.
     */
    override fun run(request: DesktopStandaloneRequest): KadreResult<SessionOutcome> {
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
        request: DesktopStandaloneRequest,
        session: WaylandHostSession,
    ): KadreResult<SessionOutcome> {
        val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val driver = WaylandWindowRuntimeDriver.create(
                session = session,
                resources = request.policy.resources,
                failureReporter = RuntimeFailureReporter { },
                publicWindowCapabilities = true,
                onLastWindowClosed = { session.pump.requestStop() },
            )
            val controller = RuntimeHostController.withComponents(
                platform = KadrePlatform.Wayland,
                componentsFactory = { _, _ ->
                    RuntimeSessionComponents(windows = driver.manager)
                },
                initialLifecycleState = LifecycleState(
                    AttachmentState.Attached,
                    VisibilityState.Background,
                    ActivationState.Inactive,
                ),
                sessionStopHandler = RuntimeSessionStopHandler { _ ->
                    session.pump.requestStop()
                    null
                },
            )
            val attached = controller.attach(parentScope, request.applicationFactory, request.policy)
            if (attached is KadreResult.Failure) {
                return attached
            }
            val kadreSession = (attached as KadreResult.Success).value
            loopStarted?.invoke(session.pump)
            var outcome: SessionOutcome? = null
            while (outcome == null) {
                session.drainWindowActions()
                outcome = when (session.pump.runOnce()) {
                    PumpStep.Dispatched, PumpStep.Rearm -> null
                    PumpStep.Stopped -> {
                        controller.detach()
                        runBlocking { kadreSession.awaitTermination() }
                    }
                    PumpStep.ConnectionLost -> {
                        controller.fail(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "display", "connection-lost"))
                        runBlocking { kadreSession.awaitTermination() }
                    }
                }
            }
            return KadreResult.Success(outcome)
        } finally {
            parentScope.cancel(CancellationException("wayland standalone host released"))
        }
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
