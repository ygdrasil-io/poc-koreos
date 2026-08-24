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
    private val ownership: AppKitStandaloneOwnership,
    private val availability: () -> Boolean,
) : DesktopBackendProvider {
    public constructor() : this(
        KffiAppKitNativeApplication(),
        ProcessAppKitStandaloneOwnership.value,
        ::isMacOs,
    )

    override val backend: DesktopBackendKind = DesktopBackendKind.AppKit
    override val supportedIntegrations: Set<DesktopIntegrationKind> = emptySet()

    override fun isAvailable(): Boolean = availability()

    override fun attach(request: DesktopEmbeddedRequest): KadreResult<KadreSession> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))

    override fun run(request: DesktopStandaloneRequest): KadreResult<SessionOutcome> {
        if (!isAvailable()) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
        }
        if (!nativeApplication.isMainThread()) {
            return KadreResult.Failure(KadreFailure.InvalidRequest("options"))
        }
        val lease = ownership.tryAcquire()
            ?: return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val nativeLoopReturned = AtomicBoolean(false)
            val host = RuntimeHostController(
                platform = KadrePlatform.AppKit,
                initialLifecycleState = LifecycleState(
                    AttachmentState.Attached,
                    VisibilityState.Background,
                    ActivationState.Inactive,
                ),
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

            try {
                nativeApplication.run()
                nativeLoopReturned.set(true)
                host.detach()
            } catch (cancellation: CancellationException) {
                nativeLoopReturned.set(true)
                throw cancellation
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
            ownership: AppKitStandaloneOwnership,
            availability: () -> Boolean,
        ): AppKitBackendProvider = AppKitBackendProvider(nativeApplication, ownership, availability)

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
    }
}
