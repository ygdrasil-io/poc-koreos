package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionPhase
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeProcessController
import org.graphiks.kadre.internal.runtime.RuntimeBootRequest
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponentsFactory
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.internal.runtime.RuntimeSessionStopHandler
import org.graphiks.kadre.internal.runtime.CapturePort
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.InputDevicePort
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopRunRequest
import org.graphiks.kadre.window.WindowProperty
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

public class AppKitBackendProvider private constructor(
    private val nativeApplication: AppKitNativeApplication,
    private val broker: AppKitProcessBroker,
    private val windowDriverFactory: AppKitWindowRuntimeDriverFactory,
    private val fullscreenAvailability: AppKitFullscreenAvailability,
    private val displayAvailability: AppKitDisplayAvailability,
    private val rawInputPortFactory: () -> RawInputPort?,
    private val displayPortFactory: () -> DisplayPort?,
    private val capturePortFactory: (AppKitCaptureSurfaceRegistry) -> CapturePort?,
    private val gamepadPortFactory: () -> GamepadPort?,
    private val inputDevicePortFactory: () -> InputDevicePort?,
    private val availability: () -> Boolean,
) : DesktopBackendProvider {
    public constructor() : this(
        KffiAppKitNativeApplication(),
        ProcessAppKitProcessBroker.value,
        AppKitWindowRuntimeDriverFactory(),
        AppKitFullscreenAvailability(),
        AppKitDisplayAvailability(),
        { if (isMacOs()) ProcessAppKitProcessBroker.value.openRawInputPort() else null },
        {
            if (isMacOs() && AppKitDisplayAvailability().isAvailable) {
                ProcessAppKitProcessBroker.value.openDisplayPort()
            } else {
                null
            }
        },
        { registry -> if (isMacOs()) AppKitCapturePort(surfaceRegistry = registry) else null },
        { if (isMacOs()) ProcessAppKitProcessBroker.value.openGameControllerPort() else null },
        { if (isMacOs()) ProcessAppKitProcessBroker.value.openHidPort() else null },
        ::isMacOs,
    )

    override val backend: DesktopBackendKind = DesktopBackendKind.AppKit
    override val supportedIntegrations: Set<DesktopIntegrationKind> =
        setOf(DesktopIntegrationKind.AppKitMainLoop)

    override fun isAvailable(): Boolean = availability()

    override fun attach(request: DesktopEmbeddedRequest): KadreResult<KadreSession> {
        if (!isAvailable()) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
        }
        if (request.integration != DesktopIntegrationKind.AppKitMainLoop || !nativeApplication.isMainThread()) {
            return KadreResult.Failure(KadreFailure.InvalidRequest("options"))
        }
        if (!nativeApplication.isRunning()) {
            return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
        }

        val observation = try {
            nativeApplication.startLifecycleObservation(broker::accept)
        } catch (_: Exception) {
            return KadreResult.Failure(lifecycleObservationFailure())
        } catch (_: LinkageError) {
            return KadreResult.Failure(lifecycleObservationFailure())
        }
        val owner = AppKitEmbeddedSessionOwner(observation)
        val attentionOwner = if (request.allowUserAttention) {
            broker.newUserAttentionOwner(nativeApplication)
        } else {
            null
        }
        val memoryPressureAvailability = broker.memoryPressureAvailability()
        val registration = try {
            val hostFactory: (LifecycleState) -> AppKitRuntimeHost = { initial ->
                AppKitRuntimeHost(
                    RuntimeHostController.withComponents(
                        platform = KadrePlatform.AppKit,
                        componentsFactory = windowComponentsFactory(
                            request.policy.resources,
                            attentionOwner = attentionOwner,
                        ),
                        initialLifecycleState = initial,
                        initialLifecycleCapabilities = LifecycleCapabilities(memoryPressureAvailability),
                        sessionObserver = RuntimeSessionObserver { _, _ -> owner.close() },
                    ),
                )
            }
            if (attentionOwner == null) broker.createEmbeddedHost(hostFactory)
            else broker.createEmbeddedHost(attentionOwner, hostFactory)
        } catch (_: Exception) {
            owner.close()
            attentionOwner?.close()
            return KadreResult.Failure(lifecycleObservationFailure())
        } catch (_: LinkageError) {
            owner.close()
            attentionOwner?.close()
            return KadreResult.Failure(lifecycleObservationFailure())
        }
        if (registration == null) {
            owner.close()
            attentionOwner?.close()
            return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        }
        owner.install(registration)

        val attached = registration.host.controller.attach(
            request.parentScope,
            request.applicationFactory,
            request.policy,
        )
        if (attached is KadreResult.Failure) owner.close()
        return attached
    }

    override fun run(request: DesktopRunRequest): KadreResult<SessionOutcome> {
        if (!isAvailable()) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
        }
        if (!nativeApplication.isMainThread()) {
            return KadreResult.Failure(KadreFailure.InvalidRequest("options"))
        }
        val attentionOwner = broker.newUserAttentionOwner(nativeApplication)
        val lease = broker.tryAcquireStandalone(attentionOwner)
            ?: run {
                attentionOwner.close()
                return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
            }

        try {
            val nativeLoopReturned = AtomicBoolean(false)
            val memoryPressureAvailability = broker.memoryPressureAvailability()
            val controller = RuntimeProcessController.withComponents(
                platform = KadrePlatform.AppKit,
                componentsFactory = windowComponentsFactory(
                    request.policy.resources,
                    attentionOwner = attentionOwner,
                    activateProcessOnFirstPresentation = true,
                ),
                initialLifecycleState = LifecycleState(
                    SessionPhase.Running,
                    VisibilityState.Background,
                    ActivationState.Inactive,
                ),
                initialLifecycleCapabilities = LifecycleCapabilities(memoryPressureAvailability),
                // Stop AppKit before committing the terminal outcome so a native stop failure
                // can still become the authoritative SessionOutcome.
                sessionStopHandler = RuntimeSessionStopHandler {
                    if (nativeLoopReturned.get()) {
                        null
                    } else {
                        requestNativeStop()
                    }
                },
            )
            // The broker's dispatch source keeps its standalone channel: memory-pressure
            // levels flow to the process controller's live session.
            lease.installMemoryTarget(object : AppKitLifecycleTarget {
                override fun updateLifecycle(state: LifecycleState) = Unit

                override fun detach() = Unit

                override fun emitMemoryPressure(level: MemoryPressureLevel) {
                    controller.emitMemoryPressure(level)
                }
            })
            val booted = controller.boot(
                RuntimeBootRequest(
                    platform = KadrePlatform.AppKit,
                    applicationFactory = request.applicationFactory,
                    policy = request.policy,
                    firstWindow = request.firstWindow,
                    mainDispatcher = request.mainDispatcher,
                ),
            )
            if (booted is KadreResult.Failure) return booted
            val session = (booted as KadreResult.Success).value

            try {
                nativeApplication.run()
                nativeLoopReturned.set(true)
            } catch (_: CancellationException) {
                nativeLoopReturned.set(true)
                // After admission, cancellation of the native loop means the host disappeared.
                // The session stops as a system termination instead of leaking a bridge
                // implementation exception from the blocking runner.
            } catch (_: Exception) {
                nativeLoopReturned.set(true)
                controller.fail(runFailure())
            } catch (_: LinkageError) {
                nativeLoopReturned.set(true)
                controller.fail(runFailure())
            }
            // The owned OS loop has returned: whatever the session's own outcome, the process
            // is done with its containers.
            controller.terminate(SessionStopReason.SystemTermination)

            return KadreResult.Success(runBlocking { session.awaitTermination() })
        } finally {
            lease.close()
        }
    }

    private fun requestNativeStop(): KadreFailure.PlatformFailure? =
        try {
            // Waiting is intentional: the runtime must know whether AppKit accepted the stop
            // before it makes the session's terminal outcome observable.
            when (nativeApplication.requestStop().await()) {
                AppKitStopResult.Accepted -> null
                is AppKitStopResult.Failed -> stopFailure()
            }
        } catch (_: Exception) {
            emergencyNativeStop()
            stopFailure()
        } catch (_: LinkageError) {
            emergencyNativeStop()
            stopFailure()
        }

    private fun emergencyNativeStop() {
        try {
            // Bypass the selector/event path because a failure there can leave the main run loop
            // asleep forever even though the Kadre session has already stopped.
            nativeApplication.emergencyStop()
        } catch (_: Exception) {
            // The typed stop failure remains authoritative even if the fallback also fails.
        } catch (_: LinkageError) {
            // The typed stop failure remains authoritative even if the fallback also fails.
        }
    }

    internal companion object {
        fun forTesting(
            nativeApplication: AppKitNativeApplication,
            broker: AppKitProcessBroker,
            windowDriverFactory: AppKitWindowRuntimeDriverFactory = AppKitWindowRuntimeDriverFactory(),
            fullscreenAvailability: AppKitFullscreenAvailability = AppKitFullscreenAvailability(),
            displayAvailability: AppKitDisplayAvailability = AppKitDisplayAvailability(),
            rawInputPortFactory: () -> RawInputPort? = { null },
            displayPortFactory: () -> DisplayPort? = { null },
            capturePortFactory: () -> CapturePort? = { null },
            capturePortFactoryWithRegistry: ((AppKitCaptureSurfaceRegistry) -> CapturePort?)? = null,
            gamepadPortFactory: () -> GamepadPort? = { null },
            inputDevicePortFactory: () -> InputDevicePort? = { null },
            availability: () -> Boolean,
        ): AppKitBackendProvider = AppKitBackendProvider(
            nativeApplication,
            broker,
            windowDriverFactory,
            fullscreenAvailability,
            displayAvailability,
            rawInputPortFactory,
            displayPortFactory,
            capturePortFactoryWithRegistry ?: { _ -> capturePortFactory() },
            gamepadPortFactory,
            inputDevicePortFactory,
            availability,
        )

        private fun isMacOs(): Boolean = System.getProperty("os.name", "").let { name ->
            name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
        }

        private fun runFailure(): KadreFailure.PlatformFailure = KadreFailure.PlatformFailure(
            KadrePlatform.AppKit,
            "appkit-host",
            "run-exception",
        )

        private fun stopFailure(): KadreFailure.PlatformFailure = KadreFailure.PlatformFailure(
            KadrePlatform.AppKit,
            "appkit-host",
            "stop-exception",
        )

        private fun lifecycleObservationFailure(): KadreFailure.PlatformFailure = KadreFailure.PlatformFailure(
            KadrePlatform.AppKit,
            "appkit-host",
            "lifecycle-observation-exception",
        )

        private fun fullscreenAvailabilityFailure(): KadreFailure.PlatformFailure =
            KadreFailure.PlatformFailure(
                KadrePlatform.AppKit,
                "fullscreen",
                "os-version-unavailable",
            )
    }

    private fun windowComponentsFactory(
        resources: org.graphiks.kadre.policy.ResourceBudgetPolicy,
        attentionOwner: AppKitProcessBroker.AppKitUserAttentionOwner? = null,
        activateProcessOnFirstPresentation: Boolean = false,
    ): RuntimeSessionComponentsFactory = RuntimeSessionComponentsFactory { _, _ ->
        val captureSurfaceRegistry = AppKitCaptureSurfaceRegistry()
        val driver = windowDriverFactory.create(
            resources = resources,
            publicAppKitCapabilities = true,
            enabledWindowUpdateCapabilities = APPKIT_PUBLIC_WINDOW_UPDATE_CAPABILITIES +
                if (displayAvailability.isAvailable) setOf(WindowProperty.OuterPosition) else emptySet(),
            fullscreenAvailabilityFailure = if (fullscreenAvailability.isAvailable) {
                null
            } else {
                fullscreenAvailabilityFailure()
            },
            publicSurfaceCapabilities = true,
            captureSurfaceRegistry = captureSurfaceRegistry,
            broker = broker,
            attentionOwner = attentionOwner,
            activateProcessOnFirstPresentation = activateProcessOnFirstPresentation,
        )
        RuntimeSessionComponents(
            windows = driver.manager,
            rawInputPort = rawInputPortFactory(),
            displayPort = displayPortFactory(),
            capturePort = capturePortFactory(captureSurfaceRegistry),
            gamepadPort = gamepadPortFactory(),
            inputDevicePort = inputDevicePortFactory(),
            closeAction = driver::close,
        )
    }
}

private val APPKIT_PUBLIC_WINDOW_UPDATE_CAPABILITIES: Set<WindowProperty> = setOf(
    WindowProperty.Title,
    WindowProperty.ContentSize,
    WindowProperty.MinimumSize,
    WindowProperty.MaximumSize,
    WindowProperty.Resizable,
    WindowProperty.Decorations,
    WindowProperty.SystemButtons,
    WindowProperty.Level,
    WindowProperty.Fullscreen,
    WindowProperty.Transparency,
)

private class AppKitEmbeddedSessionOwner(
    private val observation: AutoCloseable,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private var registration: AppKitProcessBroker.EmbeddedRegistration<AppKitRuntimeHost>? = null

    fun install(value: AppKitProcessBroker.EmbeddedRegistration<AppKitRuntimeHost>) {
        val closeImmediately = synchronized(lock) {
            check(registration == null) { "AppKit embedded registration was already installed" }
            if (closed) {
                true
            } else {
                registration = value
                false
            }
        }
        if (closeImmediately) value.close()
    }

    override fun close() {
        val target = synchronized(lock) {
            if (closed) return
            closed = true
            registration
        }
        try {
            observation.close()
        } finally {
            target?.close()
        }
    }
}
