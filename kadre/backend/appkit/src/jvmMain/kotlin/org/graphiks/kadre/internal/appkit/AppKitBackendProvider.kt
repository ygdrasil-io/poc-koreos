package org.graphiks.kadre.internal.appkit

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
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponentsFactory
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.internal.runtime.RuntimeSessionStopHandler
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

public class AppKitBackendProvider private constructor(
    private val nativeApplication: AppKitNativeApplication,
    private val broker: AppKitProcessBroker,
    private val windowDriverFactory: AppKitWindowRuntimeDriverFactory,
    private val availability: () -> Boolean,
) : DesktopBackendProvider {
    public constructor() : this(
        KffiAppKitNativeApplication(),
        ProcessAppKitProcessBroker.value,
        AppKitWindowRuntimeDriverFactory(),
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
        val registration = try {
            broker.createEmbeddedHost { initial ->
                AppKitRuntimeHost(
                    RuntimeHostController.withComponents(
                        platform = KadrePlatform.AppKit,
                        componentsFactory = windowComponentsFactory(request.policy.resources),
                        initialLifecycleState = initial,
                        sessionObserver = RuntimeSessionObserver { _, _ -> owner.close() },
                    ),
                )
            }
        } catch (_: Exception) {
            owner.close()
            return KadreResult.Failure(lifecycleObservationFailure())
        } catch (_: LinkageError) {
            owner.close()
            return KadreResult.Failure(lifecycleObservationFailure())
        }
        if (registration == null) {
            owner.close()
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

    override fun run(request: DesktopStandaloneRequest): KadreResult<SessionOutcome> {
        if (!isAvailable()) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
        }
        if (!nativeApplication.isMainThread()) {
            return KadreResult.Failure(KadreFailure.InvalidRequest("options"))
        }
        val lease = broker.tryAcquireStandalone()
            ?: return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val nativeLoopReturned = AtomicBoolean(false)
            val lastWindowStop = AppKitLastWindowStopBridge()
            val host = RuntimeHostController.withComponents(
                platform = KadrePlatform.AppKit,
                componentsFactory = windowComponentsFactory(
                    request.policy.resources,
                    if (request.stopWhenLastWindowClosed) lastWindowStop::request else null,
                ),
                initialLifecycleState = LifecycleState(
                    AttachmentState.Attached,
                    VisibilityState.Background,
                    ActivationState.Inactive,
                ),
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
            val attached = host.attach(parentScope, request.applicationFactory, request.policy)
            if (attached is KadreResult.Failure) return attached
            val session = (attached as KadreResult.Success).value
            lastWindowStop.install(session)

            try {
                nativeApplication.run()
                nativeLoopReturned.set(true)
                host.detach()
            } catch (_: CancellationException) {
                nativeLoopReturned.set(true)
                // After admission, cancellation of the native loop means the host disappeared.
                // Detach so the session's HostDetached outcome remains authoritative instead of
                // leaking a bridge implementation exception from the blocking runner.
                host.detach()
            } catch (_: Exception) {
                nativeLoopReturned.set(true)
                host.fail(runFailure())
            } catch (_: LinkageError) {
                nativeLoopReturned.set(true)
                host.fail(runFailure())
            }

            return KadreResult.Success(runBlocking { session.awaitTermination() })
        } finally {
            parentScope.cancel(CancellationException("AppKit standalone host released"))
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
            availability: () -> Boolean,
        ): AppKitBackendProvider = AppKitBackendProvider(
            nativeApplication,
            broker,
            windowDriverFactory,
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
    }

    private fun windowComponentsFactory(
        resources: org.graphiks.kadre.policy.ResourceBudgetPolicy,
        onLastWindowClosed: (() -> Unit)? = null,
    ): RuntimeSessionComponentsFactory = RuntimeSessionComponentsFactory { _, _ ->
        val driver = windowDriverFactory.create(
            resources = resources,
            publicAppKitCapabilities = true,
            publicSurfaceCapabilities = true,
            onLastWindowClosed = onLastWindowClosed,
        )
        RuntimeSessionComponents(driver.manager, driver::close)
    }
}

private class AppKitLastWindowStopBridge {
    private val lock = Any()
    private var session: KadreSession? = null
    private var requested = false

    fun install(value: KadreSession) {
        val stop = synchronized(lock) {
            check(session == null) { "AppKit last-window session was already installed" }
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
