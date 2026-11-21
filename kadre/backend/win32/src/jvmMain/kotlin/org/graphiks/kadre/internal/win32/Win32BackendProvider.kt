package org.graphiks.kadre.internal.win32

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
import org.graphiks.kadre.internal.runtime.RuntimeProcessController
import org.graphiks.kadre.internal.runtime.RuntimeBootRequest
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
import org.graphiks.kadre.internal.runtime.desktop.DesktopRunRequest

/**
 * Session standalone (tranche 1) : la pompe tourne sur le thread appelant
 * (D1) ; la règle du dernier conteneur vient du contrôleur de process (une
 * session sans fenêtre ne s'arrête pas faute de conteneur).
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

    /** Discovery never touches the generated init: exact availability, zero native load. */
    override fun isAvailable(): Boolean = availability()

    override fun run(request: DesktopRunRequest): KadreResult<SessionOutcome> {
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
        request: DesktopRunRequest,
        session: Win32HostSession,
    ): KadreResult<SessionOutcome> {
        var ownedDriver: Win32WindowRuntimeDriver? = null
        try {
            val nativeFailure = AtomicReference<Throwable?>(null)
            val driver = Win32WindowRuntimeDriverFactory.create(
                port = session.port,
                resources = request.policy.resources,
                // No driver-side last-window wiring: the controller's last-container rule
                // alone owns the Completed outcome, then the stop handler stops the pump.
                // A pump-side stop here would race terminate(SystemTermination) against
                // the controller watcher's Completed and make the outcome interleaving-
                // dependent (same determinism as AppKit, which wires nothing here).
                onLastWindowClosed = null,
                failureReporter = RuntimeFailureReporter { failure ->
                    nativeFailure.compareAndSet(null, failure)
                    session.pump.requestStop()
                },
            ).also { ownedDriver = it }
            val displayPortRef = java.util.concurrent.atomic.AtomicReference<Win32DisplayPort?>(null)
            driver.attachDisplayRescan { displayPortRef.get()?.onDisplayChanged() }
            val controller = RuntimeProcessController.withComponents(
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
                    SessionPhase.Running,
                    VisibilityState.Background,
                    ActivationState.Inactive,
                ),
                sessionStopHandler = RuntimeSessionStopHandler { _ ->
                    session.pump.requestStop()
                    null
                },
            )
            val booted = controller.boot(
                RuntimeBootRequest(
                    platform = KadrePlatform.Win32,
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
                val step = try { session.pump.runOnce() }
                catch (failure: Exception) { nativeFailure.compareAndSet(null, failure); PumpStep.Stopped }
                catch (failure: LinkageError) { nativeFailure.compareAndSet(null, failure); PumpStep.Stopped }
                outcome = when (step) {
                    PumpStep.Dispatched, PumpStep.Rearm -> null
                    PumpStep.Stopped -> {
                        if (nativeFailure.get() != null) {
                            controller.fail(KadreFailure.PlatformFailure(KadrePlatform.Win32, "input", "native-input-failed"))
                        } else controller.terminate(SessionStopReason.SystemTermination)
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

/**
 * The one process gamepad broker, built lazily over the real KFFI XInput
 * frontier. XInput has no registration handle the OS lets us revoke, so the
 * polling daemon is process-lifetime; each session only opens a detachable
 * view ([Win32GamepadBroker.openSessionView]).
 */
internal val ProcessWin32GamepadBroker: Lazy<Win32GamepadBroker> = lazy {
    Win32GamepadBroker(KffiWin32XInputNative)
}
