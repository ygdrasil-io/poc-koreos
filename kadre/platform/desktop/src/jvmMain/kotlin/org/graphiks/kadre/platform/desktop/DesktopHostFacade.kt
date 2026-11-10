package org.graphiks.kadre.platform.desktop

import kotlinx.coroutines.asCoroutineDispatcher
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreRunOptions
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopRunRequest
import org.graphiks.kadre.policy.KadrePolicy
import java.util.ServiceConfigurationError
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

internal class DesktopHostFacade(
    private val operatingSystem: DesktopOperatingSystem,
    private val catalog: DesktopProviderCatalog,
) {
    /**
     * The ownership-first run: selects a backend, then boots one application in a process Kadre
     * owns. The facade owns the run's main thread — a dedicated single-thread dispatcher created
     * per call, handed to the provider through [DesktopRunRequest.mainDispatcher] and closed at
     * session teardown (when the provider's run returns), on every path. This is what makes the
     * public entry work on a desktop JVM where no `Dispatchers.Main` implementation exists,
     * without any process-global state (no `Dispatchers.setMain`).
     */
    fun run(
        applicationFactory: KadreApplicationFactory,
        backend: DesktopBackend,
        options: KadreRunOptions,
    ): KadreResult<SessionOutcome> {
        val selection = selectProvider(backend)
        if (selection is ProviderSelection.Failure) return KadreResult.Failure(selection.failure)
        val selected = selection as ProviderSelection.Selected
        val provider = selected.provider

        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kadre-desktop-main").apply { isDaemon = true }
        }
        val mainDispatcher = executor.asCoroutineDispatcher()
        val request = DesktopRunRequest(
            applicationFactory = applicationFactory,
            policy = options.policy,
            firstWindow = options.firstWindow,
            mainDispatcher = mainDispatcher,
        )

        try {
            val result = try {
                provider.run(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: LinkageError) {
                return KadreResult.Failure(providerFailure(selected.backend, "run-exception"))
            } catch (exception: Exception) {
                return KadreResult.Failure(providerFailure(selected.backend, "run-exception"))
            }
            return when (result) {
                is KadreResult.Success -> result
                is KadreResult.Failure -> KadreResult.Failure(
                    normalizeRunFailure(selected.backend, options.policy, result.reason),
                )
            }
        } finally {
            // Session teardown: a provider only returns from run once its session terminated,
            // so the run's owned main thread closes here — success, explicit failure, thrown
            // provider bug and cancellation alike. Nothing process-wide survives the run.
            mainDispatcher.close()
        }
    }

    private fun selectProvider(requested: DesktopBackend): ProviderSelection {
        val candidates = candidatesFor(requested)
            ?: return ProviderSelection.Failure(KadreFailure.InvalidRequest("options"))
        if (candidates.isEmpty()) return ProviderSelection.Failure(KadreFailure.InvalidRequest("options"))

        val providers = try {
            catalog.providers().groupBy(DesktopBackendProvider::backend)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: ServiceConfigurationError) {
            return ProviderSelection.Failure(providerFailure(candidates.first(), "discovery-exception"))
        } catch (error: LinkageError) {
            return ProviderSelection.Failure(providerFailure(candidates.first(), "discovery-exception"))
        } catch (exception: Exception) {
            return ProviderSelection.Failure(providerFailure(candidates.first(), "discovery-exception"))
        }

        for (candidate in candidates) {
            val matching = providers[candidate].orEmpty()
            if (matching.size > 1) {
                return ProviderSelection.Failure(providerFailure(candidate, "duplicate-provider"))
            }
            val provider = matching.singleOrNull() ?: continue
            val available = try {
                provider.isAvailable()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: LinkageError) {
                return ProviderSelection.Failure(providerFailure(candidate, "availability-exception"))
            } catch (exception: Exception) {
                return ProviderSelection.Failure(providerFailure(candidate, "availability-exception"))
            }
            if (available) return ProviderSelection.Selected(candidate, provider)
        }

        return ProviderSelection.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
    }

    private fun candidatesFor(requested: DesktopBackend): List<DesktopBackendKind>? {
        val operatingSystemCandidates = when (operatingSystem) {
            DesktopOperatingSystem.MacOS -> listOf(DesktopBackendKind.AppKit)
            DesktopOperatingSystem.Windows -> listOf(DesktopBackendKind.Win32)
            DesktopOperatingSystem.Linux -> listOf(DesktopBackendKind.Wayland, DesktopBackendKind.X11)
            DesktopOperatingSystem.Unsupported -> emptyList()
        }
        val requestedCandidates = when (requested) {
            DesktopBackend.Auto -> operatingSystemCandidates
            DesktopBackend.AppKit -> listOf(DesktopBackendKind.AppKit)
            DesktopBackend.Win32 -> listOf(DesktopBackendKind.Win32)
            DesktopBackend.X11 -> listOf(DesktopBackendKind.X11)
            DesktopBackend.Wayland -> listOf(DesktopBackendKind.Wayland)
        }
        if (requested != DesktopBackend.Auto && requestedCandidates.single() !in operatingSystemCandidates) return null
        return requestedCandidates
    }

    private fun normalizeRunFailure(
        backend: DesktopBackendKind,
        policy: KadrePolicy,
        failure: KadreFailure,
    ): KadreFailure = if (failure.isValidRunFailure(backend, policy)) {
        failure
    } else {
        providerFailure(backend, "invalid-run-failure")
    }
}

internal val defaultDesktopHostFacade: DesktopHostFacade by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DesktopHostFacade(
        detectDesktopOperatingSystem(System.getProperty("os.name").orEmpty()),
        ServiceLoaderDesktopProviderCatalog,
    )
}

private sealed interface ProviderSelection {
    data class Selected(val backend: DesktopBackendKind, val provider: DesktopBackendProvider) : ProviderSelection
    data class Failure(val failure: KadreFailure) : ProviderSelection
}

private fun providerFailure(backend: DesktopBackendKind, code: String): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(backend.toPlatform(), "desktop-provider", code)

private fun DesktopBackendKind.toPlatform(): KadrePlatform = when (this) {
    DesktopBackendKind.AppKit -> KadrePlatform.AppKit
    DesktopBackendKind.Win32 -> KadrePlatform.Win32
    DesktopBackendKind.X11 -> KadrePlatform.X11
    DesktopBackendKind.Wayland -> KadrePlatform.Wayland
}

private fun KadreFailure.isValidRunFailure(
    backend: DesktopBackendKind,
    policy: KadrePolicy,
): Boolean = when (this) {
    is KadreFailure.Unsupported -> operation == KadreOperation.HostAttach
    is KadreFailure.InvalidRequest -> field == "options"
    is KadreFailure.AlreadyInUse -> resource == org.graphiks.kadre.diagnostics.KadreResourceKind.Host
    is KadreFailure.UnsupportedPolicy -> policy != org.graphiks.kadre.policy.KadrePolicies.Default
    is KadreFailure.TemporarilyUnavailable -> true
    is KadreFailure.PlatformFailure -> platform == backend.toPlatform()
    else -> false
}
