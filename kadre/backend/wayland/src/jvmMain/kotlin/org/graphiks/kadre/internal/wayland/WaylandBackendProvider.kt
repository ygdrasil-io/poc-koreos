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
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionStopHandler
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kffi.posix.PosixWakeup

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
     * Session standalone headless (tranche 1) : la pompe tourne sur le thread
     * appelant ; `stopWhenLastWindowClosed` n'a pas d'effet tant qu'aucune
     * fenêtre n'existe (la session ne s'arrête pas faute de fenêtre).
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
            val controller = RuntimeHostController.withComponents(
                platform = KadrePlatform.Wayland,
                componentsFactory = { _, _ ->
                    RuntimeSessionComponents(windows = WaylandHeadlessWindowManager())
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
