package org.graphiks.kadre.platform.desktop

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import java.util.ServiceConfigurationError
import kotlin.coroutines.cancellation.CancellationException

internal class DesktopHostFacade(
    private val operatingSystem: DesktopOperatingSystem,
    private val catalog: DesktopProviderCatalog,
) {
    fun attach(
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        options: DesktopHostOptions,
        policy: KadrePolicy,
    ): KadreResult<KadreSession> {
        val embedded = options as? DesktopHostOptions.Embedded
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("options"))
        val integration = embedded.integration.toInternal()
        val selection = selectProvider(embedded.backend, integration)
        if (selection is ProviderSelection.Failure) return KadreResult.Failure(selection.failure)
        val selected = selection as ProviderSelection.Selected
        val provider = selected.provider
        val request = DesktopEmbeddedRequest(
            parentScope,
            applicationFactory,
            integration,
            policy,
            embedded.allowUserAttention,
        )

        val result = try {
            provider.attach(request)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: LinkageError) {
            return KadreResult.Failure(providerFailure(selected.backend, "attach-exception"))
        } catch (exception: Exception) {
            return KadreResult.Failure(providerFailure(selected.backend, "attach-exception"))
        }
        return normalizeAttachResult(selected.backend, policy, result)
    }

    fun run(
        applicationFactory: KadreApplicationFactory,
        options: DesktopHostOptions.Standalone,
        policy: KadrePolicy,
    ): SessionOutcome {
        val selection = selectProvider(options.backend, integration = null)
        if (selection is ProviderSelection.Failure) throw KadreException(selection.failure)
        val selected = selection as ProviderSelection.Selected
        val provider = selected.provider
        val request = DesktopStandaloneRequest(
            applicationFactory,
            options.stopWhenLastWindowClosed,
            policy,
        )

        val result = try {
            provider.run(request)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: LinkageError) {
            throw KadreException(providerFailure(selected.backend, "run-exception"))
        } catch (exception: Exception) {
            throw KadreException(providerFailure(selected.backend, "run-exception"))
        }
        return when (result) {
            is KadreResult.Success -> result.value
            is KadreResult.Failure -> throw KadreException(
                normalizeStandaloneFailure(selected.backend, policy, result.reason),
            )
        }
    }

    private fun selectProvider(
        requested: DesktopBackend,
        integration: DesktopIntegrationKind?,
    ): ProviderSelection {
        val candidates = candidatesFor(requested, integration)
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

        var foundAvailableBackend = false
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
            if (!available) continue
            foundAvailableBackend = true
            if (integration == null) return ProviderSelection.Selected(candidate, provider)

            val supportsIntegration = try {
                integration in provider.supportedIntegrations
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: LinkageError) {
                return ProviderSelection.Failure(providerFailure(candidate, "metadata-exception"))
            } catch (exception: Exception) {
                return ProviderSelection.Failure(providerFailure(candidate, "metadata-exception"))
            }
            if (supportsIntegration) return ProviderSelection.Selected(candidate, provider)
        }

        return if (foundAvailableBackend && integration != null) {
            ProviderSelection.Failure(KadreFailure.InvalidRequest("options"))
        } else {
            ProviderSelection.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
        }
    }

    private fun candidatesFor(
        requested: DesktopBackend,
        integration: DesktopIntegrationKind?,
    ): List<DesktopBackendKind>? {
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
        if (integration == DesktopIntegrationKind.AppKitMainLoop) {
            return requestedCandidates.filter { it == DesktopBackendKind.AppKit }.takeIf { it.isNotEmpty() }
        }
        return requestedCandidates
    }

    private fun normalizeAttachResult(
        backend: DesktopBackendKind,
        policy: KadrePolicy,
        result: KadreResult<KadreSession>,
    ): KadreResult<KadreSession> = when (result) {
        is KadreResult.Success -> result
        is KadreResult.Failure -> KadreResult.Failure(
            if (result.reason.isValidAttachFailure(backend, policy)) {
                result.reason
            } else {
                providerFailure(backend, "invalid-attach-failure")
            },
        )
    }

    private fun normalizeStandaloneFailure(
        backend: DesktopBackendKind,
        policy: KadrePolicy,
        failure: KadreFailure,
    ): KadreFailure = if (failure.isValidStandaloneFailure(backend, policy)) {
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

private fun DesktopIntegration.toInternal(): DesktopIntegrationKind = when (this) {
    DesktopIntegration.AppKitMainLoop -> DesktopIntegrationKind.AppKitMainLoop
    DesktopIntegration.AwtEventDispatchThread -> DesktopIntegrationKind.AwtEventDispatchThread
    DesktopIntegration.JavaFxApplicationThread -> DesktopIntegrationKind.JavaFxApplicationThread
}

private fun providerFailure(backend: DesktopBackendKind, code: String): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(backend.toPlatform(), "desktop-provider", code)

private fun DesktopBackendKind.toPlatform(): KadrePlatform = when (this) {
    DesktopBackendKind.AppKit -> KadrePlatform.AppKit
    DesktopBackendKind.Win32 -> KadrePlatform.Win32
    DesktopBackendKind.X11 -> KadrePlatform.X11
    DesktopBackendKind.Wayland -> KadrePlatform.Wayland
}

private fun KadreFailure.isValidAttachFailure(
    backend: DesktopBackendKind,
    policy: KadrePolicy,
): Boolean = when (this) {
    is KadreFailure.Unsupported -> operation == KadreOperation.HostAttach
    is KadreFailure.InvalidRequest -> field == "parentScope" || field == "options"
    is KadreFailure.AlreadyInUse -> resource == KadreResourceKind.Host
    is KadreFailure.Closed -> resource == KadreResourceKind.Host
    KadreFailure.ParentScopeCancelled -> true
    is KadreFailure.UnsupportedPolicy -> policy != KadrePolicies.Default
    is KadreFailure.TemporarilyUnavailable -> true
    is KadreFailure.PlatformFailure -> platform == backend.toPlatform()
    else -> false
}

private fun KadreFailure.isValidStandaloneFailure(
    backend: DesktopBackendKind,
    policy: KadrePolicy,
): Boolean = when (this) {
    is KadreFailure.Unsupported -> operation == KadreOperation.HostAttach
    is KadreFailure.InvalidRequest -> field == "options"
    is KadreFailure.AlreadyInUse -> resource == KadreResourceKind.Host
    is KadreFailure.UnsupportedPolicy -> policy != KadrePolicies.Default
    is KadreFailure.TemporarilyUnavailable -> true
    is KadreFailure.PlatformFailure -> platform == backend.toPlatform()
    else -> false
}
