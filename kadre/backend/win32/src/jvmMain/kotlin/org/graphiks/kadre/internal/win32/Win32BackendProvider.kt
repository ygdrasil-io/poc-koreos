package org.graphiks.kadre.internal.win32

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.CapturePort
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kffi.win32.managed.Win32Displays
import org.graphiks.kffi.win32.managed.Win32RawInput
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
    private val rawInputPortFactory: (() -> RawInputPort?)?,
    private val displayPortFactory: (() -> DisplayPort?)?,
    private val gamepadPortFactory: (() -> GamepadPort?)?,
    private val capturePortFactory: (() -> CapturePort?)?,
) : DesktopBackendProvider {
    public constructor() : this(
        ::isWindows,
        { KffiWin32HostPort() },
        null,
        { ProcessWin32RawInputBroker.value.openPort() },
        { Win32DisplayPort { Win32Displays.snapshot() } },
        { ProcessWin32GamepadBroker.value.openSessionView() },
        { Win32CapturePort() },
    )

    override val backend: DesktopBackendKind = DesktopBackendKind.Win32

    // Fermeture phase 12 : l'intégration EDT est annoncée avec son consumer
    // (kadre/integration/awt) et sa preuve O3 sur la vraie boucle (gate Windows).
    override val supportedIntegrations: Set<DesktopIntegrationKind> =
        setOf(DesktopIntegrationKind.AwtEventDispatchThread)

    /** Discovery never touches the generated init: exact availability, zero native load. */
    override fun isAvailable(): Boolean = availability()

    override fun attach(request: DesktopEmbeddedRequest): KadreResult<KadreSession> {
        // D1 (design phase 12) : seule l'intégration EDT est conçue ; toute
        // autre suit le refus normatif Unsupported(HostAttach).
        if (request.integration != DesktopIntegrationKind.AwtEventDispatchThread) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
        }
        // Pas de relocation cachée : le thread appelant DOIT être l'EDT.
        if (!awtDispatchThreadProbe()) {
            return KadreResult.Failure(KadreFailure.InvalidRequest("options"))
        }
        if (!availability()) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
        }
        if (!Win32HostOwnership.tryRegisterEmbedded()) {
            return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        }
        val registration = AutoCloseable { Win32HostOwnership.releaseEmbedded() }
        return try {
            attachSession(request, registration)
        } catch (start: Win32SessionStartException) {
            registration.close()
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Win32, "start", start.reason))
        } catch (unexpected: Exception) {
            registration.close()
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Win32, "start", "start-unexpected"))
        }
    }

    /**
     * Session embarquée (design phase 12, §4) : le port est créé sur l'EDT
     * (thread UI enregistré), les fenêtres sont réelles, et la pompe est
     * drainée par des ticks EDT bornés — jamais de boucle bloquante, jamais
     * de WM_QUIT vers la boucle hôte.
     */
    private fun attachSession(
        request: DesktopEmbeddedRequest,
        registration: AutoCloseable,
    ): KadreResult<KadreSession> {
        val parentScope = request.parentScope
        val port = portFactory()
        SessionRawInputTarget.port = port as? KffiWin32HostPort
        port.attachRawInputActivator(ProcessWin32RawInputBroker.value::activateOn)
        val session = Win32HostSession.start(port)
        var ownedEmbeddedDriver: Win32WindowRuntimeDriver? = null
        val released = AtomicBoolean(false)
        fun releaseOnce() {
            if (!released.compareAndSet(false, true)) return
            if (SessionRawInputTarget.port === port) SessionRawInputTarget.port = null

            runCatching { session.port.stopWorkerCommands() }
            runCatching { ownedEmbeddedDriver?.close() }
            runCatching { session.teardown() }
            runCatching { port.close() }
            registration.close()
        }
        try {
            val nativeFailure = AtomicReference<Throwable?>(null)
            var controllerRef: RuntimeHostController? = null
            val driver = Win32WindowRuntimeDriverFactory.create(
                port = session.port,
                resources = request.policy.resources,
                onLastWindowClosed = null, // gate embarquée : le dernier fenêtre n'arrête jamais la session
                failureReporter = RuntimeFailureReporter { failure ->
                    nativeFailure.compareAndSet(null, failure)
                    controllerRef?.fail(KadreFailure.PlatformFailure(KadrePlatform.Win32, "input", "native-input-failed"))
                },
            ).also { ownedEmbeddedDriver = it }
            val embeddedPump = Win32EmbeddedPump(
                pump = session.pump,
                postTick = { runnable -> runCatching { awtEventQueuePost(runnable) } },
            )
            val controller = RuntimeHostController.withComponents(
                platform = KadrePlatform.Win32,
                componentsFactory = { _, _ ->
                    val displayPort = (displayPortFactory?.invoke()
                        ?: Win32DisplayPort(inventoryProvider = { Win32Displays.snapshot() })) as Win32DisplayPort
                    RuntimeSessionComponents(
                        windows = driver.manager,
                        rawInputPort = rawInputPortFactory?.invoke(),
                        displayPort = displayPort,
                        gamepadPort = gamepadPortFactory?.invoke(),
                        capturePort = capturePortFactory?.invoke(),
                    )
                },
                initialLifecycleState = LifecycleState(
                    AttachmentState.Attached,
                    VisibilityState.Background,
                    ActivationState.Inactive,
                ),
                failureReporter = RuntimeFailureReporter { failure ->
                    nativeFailure.compareAndSet(null, failure)
                },
                sessionStopHandler = RuntimeSessionStopHandler {
                    embeddedPump.close()
                    null
                },
                sessionObserver = { _, _ -> releaseOnce() },
            ).also { controllerRef = it }
            embeddedPump.start()
            val attached = controller.attach(parentScope, request.applicationFactory, request.policy)
            if (attached is KadreResult.Failure) {
                embeddedPump.close()
                releaseOnce()
                return attached
            }
            parentScope.coroutineContext[Job]?.invokeOnCompletion { releaseOnce() }
            nativeFailure.get()?.let { failure ->
                controllerRef?.fail(KadreFailure.PlatformFailure(KadrePlatform.Win32, "input", "native-input-failed"))
            }
            return attached
        } catch (failure: Throwable) {
            releaseOnce()
            throw failure
        }
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
            val nativePort = session.port
            SessionRawInputTarget.port = nativePort as? KffiWin32HostPort
            nativePort.attachRawInputActivator(ProcessWin32RawInputBroker.value::activateOn)
            try {
                return runSession(request, session)
            } finally {
                SessionRawInputTarget.port = null
                try { session.teardown() }
                finally { nativePort.close() }
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
            val displayPortRef = java.util.concurrent.atomic.AtomicReference<Win32DisplayPort?>(null)
            driver.attachDisplayRescan { displayPortRef.get()?.onDisplayChanged() }
            val controller = RuntimeHostController.withComponents(
                platform = KadrePlatform.Win32,
                componentsFactory = { _, _ ->
                    val displayPort = (displayPortFactory?.invoke() ?: Win32DisplayPort(
                        inventoryProvider = { Win32Displays.snapshot() },
                    )) as Win32DisplayPort
                    displayPortRef.set(displayPort)
                    RuntimeSessionComponents(
                        windows = driver.manager,
                        rawInputPort = rawInputPortFactory?.invoke(),
                        displayPort = displayPort,
                        gamepadPort = gamepadPortFactory?.invoke(),
                        capturePort = capturePortFactory?.invoke(),
                    )
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
            rawInputPortFactory: (() -> RawInputPort?)? = null,
            displayPortFactory: (() -> DisplayPort?)? = null,
            gamepadPortFactory: (() -> GamepadPort?)? = null,
            capturePortFactory: (() -> CapturePort?)? = null,
        ): Win32BackendProvider = Win32BackendProvider(availability, portFactory, loopStarted, rawInputPortFactory, displayPortFactory, gamepadPortFactory, capturePortFactory)

        internal fun isWindows(): Boolean =
            System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)

        // Ponts AWT par réflexion : zéro dépendance AWT dans le runtime commun.
        internal var awtDispatchThreadProbe: () -> Boolean = ::isAwtDispatchThread
        internal var awtEventQueuePost: (Runnable) -> Unit = ::postToAwtEventQueue

        private fun isAwtDispatchThread(): Boolean = runCatching {
            Class.forName("java.awt.EventQueue").getMethod("isDispatchThread").invoke(null) as Boolean
        }.getOrDefault(false)

        private fun postToAwtEventQueue(runnable: Runnable) {
            Class.forName("java.awt.EventQueue").getMethod("invokeLater", Runnable::class.java).invoke(null, runnable)
        }
    }
}

/** Bridges the process broker to the session's live KFFI host port. */
internal object SessionRawInputTarget {
    @Volatile internal var port: KffiWin32HostPort? = null
}

/** The one process broker, built lazily over the real KFFI frontier on the UI owner. */
internal val ProcessWin32RawInputBroker: Lazy<Win32RawInputBroker> = lazy {
    Win32RawInputBroker(
        native = object : Win32RawInputNative {
            override fun hasForeignRegistration(): Boolean = Win32RawInput.hasRegistration()
            override fun register(
                targetHwnd: Long,
                onMotion: (deltaX: Int, deltaY: Int, device: Long) -> Unit,
                onFailure: (Throwable) -> Unit,
            ): AutoCloseable {
                val port = checkNotNull(SessionRawInputTarget.port) { "no session port for raw input" }
                return port.registerRawInput(targetHwnd, onMotion, onFailure)
            }
        },
    )
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

/**
 * The one process gamepad broker, built lazily over the real KFFI XInput
 * frontier. XInput has no registration handle the OS lets us revoke, so the
 * polling daemon is process-lifetime; each session only opens a detachable
 * view ([Win32GamepadBroker.openSessionView]).
 */
internal val ProcessWin32GamepadBroker: Lazy<Win32GamepadBroker> = lazy {
    Win32GamepadBroker(KffiWin32XInputNative)
}
