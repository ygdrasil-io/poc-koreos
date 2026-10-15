package org.graphiks.kadre.internal.win32

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionStopHandler
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest

/**
 * Session standalone headless (tranche 1) : la pompe tourne sur le thread
 * appelant (D1) ; `stopWhenLastWindowClosed` n'a pas d'effet tant qu'aucune
 * fenêtre n'existe (la session ne s'arrête pas faute de fenêtre).
 */
public class Win32BackendProvider private constructor(
    private val availability: () -> Boolean,
    private val portFactory: () -> Win32NativeHostPort,
    private val loopStarted: ((Win32MessagePump) -> Unit)?,
) : DesktopBackendProvider {
    public constructor() : this(
        ::isWindows,
        { KffiWin32HostPort() },
        null,
    )

    override val backend: DesktopBackendKind = DesktopBackendKind.Win32

    override val supportedIntegrations: Set<DesktopIntegrationKind> = emptySet()

    /** Discovery never touches the generated init: exact availability, zero native load. */
    override fun isAvailable(): Boolean = availability()

    override fun attach(request: DesktopEmbeddedRequest): KadreResult<KadreSession> {
        return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
    }

    override fun run(request: DesktopStandaloneRequest): KadreResult<SessionOutcome> {
        if (!availability()) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
        }
        if (!Win32HostLease.acquire()) {
            return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        }
        try {
            val session = try {
                Win32HostSession.start(portFactory())
            } catch (start: Win32SessionStartException) {
                return KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Win32, "start", start.reason))
            } catch (unexpected: Exception) {
                return KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Win32, "start", "start-unexpected"))
            }
            try {
                return runSession(request, session)
            } finally {
                try { session.teardown() }
                finally { session.port.close() }
            }
        } finally {
            Win32HostLease.release()
        }
    }

    private fun runSession(
        request: DesktopStandaloneRequest,
        session: Win32HostSession,
    ): KadreResult<SessionOutcome> {
        val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var ownedDriver: Win32WindowRuntimeDriver? = null
        try {
            val lastWindowStop = Win32LastWindowStopBridge()
            val nativeFailure = AtomicReference<Throwable?>(null)
            val driver = Win32WindowRuntimeDriverFactory.create(
                port = session.port,
                resources = request.policy.resources,
                onLastWindowClosed = if (request.stopWhenLastWindowClosed) lastWindowStop::request else null,
                failureReporter = RuntimeFailureReporter { failure ->
                    nativeFailure.compareAndSet(null, failure)
                    session.pump.requestStop()
                },
            ).also { ownedDriver = it }
            val controller = RuntimeHostController.withComponents(
                platform = KadrePlatform.Win32,
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
            lastWindowStop.install(kadreSession)
            var outcome: SessionOutcome? = null
            while (outcome == null) {
                val step = try { session.pump.runOnce() }
                catch (failure: Exception) { nativeFailure.compareAndSet(null, failure); PumpStep.Stopped }
                catch (failure: LinkageError) { nativeFailure.compareAndSet(null, failure); PumpStep.Stopped }
                outcome = when (step) {
                    PumpStep.Dispatched, PumpStep.Rearm -> null
                    PumpStep.Stopped -> {
                        if (nativeFailure.get() != null) {
                            controller.fail(KadreFailure.PlatformFailure(KadrePlatform.Win32, "input", "native-input-failed"))
                        } else controller.detach()
                        session.port.stopWorkerCommands()
                        runBlocking { kadreSession.awaitTermination() }
                    }
                    PumpStep.PumpFailed -> {
                        controller.fail(
                            KadreFailure.PlatformFailure(
                                KadrePlatform.Win32,
                                "pump",
                                "get-message-failed",
                            ),
                        )
                        session.port.stopWorkerCommands()
                        runBlocking { kadreSession.awaitTermination() }
                    }
                }
            }
            return KadreResult.Success(outcome)
        } finally {
            session.port.stopWorkerCommands()
            ownedDriver?.close()
            parentScope.cancel(CancellationException("win32 standalone host released"))
        }
    }

    public companion object {
        internal fun forTesting(
            availability: () -> Boolean = ::isWindows,
            portFactory: () -> Win32NativeHostPort = { KffiWin32HostPort() },
            loopStarted: ((Win32MessagePump) -> Unit)? = null,
        ): Win32BackendProvider = Win32BackendProvider(availability, portFactory, loopStarted)

        internal fun isWindows(): Boolean =
            System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)
    }
}

/** Bridges the runtime's last-window-closed signal to the standalone session stop (once). */
internal class Win32LastWindowStopBridge {
    private val lock = Any()
    private var session: KadreSession? = null
    private var requested = false

    fun install(value: KadreSession) {
        val stop = synchronized(lock) {
            check(session == null) { "win32 last-window session was already installed" }
            session = value
            requested
        }
        if (stop) value.close()
    }

    fun request() {
        val target = synchronized(lock) {
            if (requested) return
            requested = true
            session
        }
        target?.close()
    }
}
