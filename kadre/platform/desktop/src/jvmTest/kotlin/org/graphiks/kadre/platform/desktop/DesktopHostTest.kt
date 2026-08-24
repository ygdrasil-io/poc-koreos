package org.graphiks.kadre.platform.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class DesktopHostTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val factory = KadreApplicationFactory { KadreApplication { } }

    @Test
    fun autoSelectsAppKitOnMacAndPreservesTheEmbeddedRequest() {
        val provider = RecordingProvider(DesktopBackendKind.AppKit, setOf(DesktopIntegrationKind.AppKitMainLoop))
        var catalogLoads = 0
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) {
            catalogLoads += 1
            listOf(provider)
        }
        val options = DesktopHostOptions.Embedded(DesktopIntegration.AppKitMainLoop)

        val result = facade.attach(scope, factory, options, KadrePolicies.Realtime)

        assertSame(provider.session, assertIs<KadreResult.Success<KadreSession>>(result).value)
        assertEquals(1, catalogLoads)
        val request = provider.embeddedRequests.single()
        assertSame(scope, request.parentScope)
        assertSame(factory, request.applicationFactory)
        assertSame(KadrePolicies.Realtime, request.policy)
        assertEquals(DesktopIntegrationKind.AppKitMainLoop, request.integration)
    }

    @Test
    fun explicitBackendMissingIsUnsupportedWithoutCreatingTheApplication() {
        var factoryInvoked = false
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { emptyList() }
        val result = facade.attach(
            scope,
            KadreApplicationFactory {
                factoryInvoked = true
                KadreApplication { }
            },
            DesktopHostOptions.Embedded(DesktopIntegration.AppKitMainLoop, DesktopBackend.AppKit),
            KadrePolicies.Default,
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)),
            result,
        )
        assertFalse(factoryInvoked)
    }

    @Test
    fun incompatibleOsBackendIsRejectedBeforeProviderDiscovery() {
        var catalogLoaded = false
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) {
            catalogLoaded = true
            listOf(RecordingProvider(DesktopBackendKind.Win32, emptySet()))
        }

        val result = facade.attach(
            scope,
            factory,
            DesktopHostOptions.Embedded(DesktopIntegration.AwtEventDispatchThread, DesktopBackend.Win32),
            KadrePolicies.Default,
        )

        assertEquals(KadreResult.Failure(KadreFailure.InvalidRequest("options")), result)
        assertFalse(catalogLoaded)
    }

    @Test
    fun providerThatCannotPumpTheRequestedIntegrationIsInvalidOptions() {
        val provider = RecordingProvider(DesktopBackendKind.AppKit, setOf(DesktopIntegrationKind.AppKitMainLoop))
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }

        val result = facade.attach(
            scope,
            factory,
            DesktopHostOptions.Embedded(DesktopIntegration.JavaFxApplicationThread),
            KadrePolicies.Default,
        )

        assertEquals(KadreResult.Failure(KadreFailure.InvalidRequest("options")), result)
        assertEquals(0, provider.embeddedRequests.size)
    }

    @Test
    fun linuxAutoPriorityIsStableRegardlessOfDiscoveryOrder() {
        val x11 = RecordingProvider(DesktopBackendKind.X11, setOf(DesktopIntegrationKind.AwtEventDispatchThread))
        val wayland = RecordingProvider(DesktopBackendKind.Wayland, setOf(DesktopIntegrationKind.AwtEventDispatchThread))
        val facade = DesktopHostFacade(DesktopOperatingSystem.Linux) { listOf(x11, wayland) }

        val result = facade.attach(
            scope,
            factory,
            DesktopHostOptions.Embedded(DesktopIntegration.AwtEventDispatchThread),
            KadrePolicies.Default,
        )

        assertIs<KadreResult.Success<KadreSession>>(result)
        assertEquals(1, wayland.embeddedRequests.size)
        assertEquals(0, x11.embeddedRequests.size)
    }

    @Test
    fun autoSkipsAnUnavailableProviderBeforeSelection() {
        val wayland = RecordingProvider(
            DesktopBackendKind.Wayland,
            setOf(DesktopIntegrationKind.AwtEventDispatchThread),
            available = false,
        )
        val x11 = RecordingProvider(DesktopBackendKind.X11, setOf(DesktopIntegrationKind.AwtEventDispatchThread))
        val facade = DesktopHostFacade(DesktopOperatingSystem.Linux) { listOf(wayland, x11) }

        assertIs<KadreResult.Success<KadreSession>>(
            facade.attach(
                scope,
                factory,
                DesktopHostOptions.Embedded(DesktopIntegration.AwtEventDispatchThread),
                KadrePolicies.Default,
            ),
        )
        assertEquals(0, wayland.embeddedRequests.size)
        assertEquals(1, x11.embeddedRequests.size)
    }

    @Test
    fun windowsAutoSelectsWin32() {
        val provider = RecordingProvider(DesktopBackendKind.Win32, setOf(DesktopIntegrationKind.JavaFxApplicationThread))
        val facade = DesktopHostFacade(DesktopOperatingSystem.Windows) { listOf(provider) }

        assertIs<KadreResult.Success<KadreSession>>(
            facade.attach(
                scope,
                factory,
                DesktopHostOptions.Embedded(DesktopIntegration.JavaFxApplicationThread),
                KadrePolicies.Default,
            ),
        )
        assertEquals(1, provider.embeddedRequests.size)
    }

    @Test
    fun operatingSystemDetectionIsClosedAndLocaleIndependent() {
        assertEquals(DesktopOperatingSystem.MacOS, detectDesktopOperatingSystem("Mac OS X"))
        assertEquals(DesktopOperatingSystem.MacOS, detectDesktopOperatingSystem("Darwin"))
        assertEquals(DesktopOperatingSystem.Windows, detectDesktopOperatingSystem("Windows 11"))
        assertEquals(DesktopOperatingSystem.Linux, detectDesktopOperatingSystem("Linux"))
        assertEquals(DesktopOperatingSystem.Unsupported, detectDesktopOperatingSystem("FreeBSD"))
    }

    @Test
    fun unsupportedOperatingSystemIsRejectedBeforeDiscovery() {
        var catalogLoaded = false
        val facade = DesktopHostFacade(DesktopOperatingSystem.Unsupported) {
            catalogLoaded = true
            emptyList()
        }

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            facade.attach(
                scope,
                factory,
                DesktopHostOptions.Embedded(DesktopIntegration.AwtEventDispatchThread),
                KadrePolicies.Default,
            ),
        )
        assertFalse(catalogLoaded)
    }

    @Test
    fun duplicateSelectedProviderIsAStablePlatformFailure() {
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) {
            listOf(
                RecordingProvider(DesktopBackendKind.AppKit, setOf(DesktopIntegrationKind.AppKitMainLoop)),
                RecordingProvider(DesktopBackendKind.AppKit, setOf(DesktopIntegrationKind.AppKitMainLoop)),
            )
        }

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "desktop-provider", "duplicate-provider"),
            ),
            facade.attach(
                scope,
                factory,
                DesktopHostOptions.Embedded(DesktopIntegration.AppKitMainLoop),
                KadrePolicies.Default,
            ),
        )
    }

    @Test
    fun providerExceptionDoesNotFallbackToAnotherBackend() {
        val wayland = RecordingProvider(
            DesktopBackendKind.Wayland,
            setOf(DesktopIntegrationKind.AwtEventDispatchThread),
            attachFailure = IllegalStateException("provider"),
        )
        val x11 = RecordingProvider(DesktopBackendKind.X11, setOf(DesktopIntegrationKind.AwtEventDispatchThread))
        val facade = DesktopHostFacade(DesktopOperatingSystem.Linux) { listOf(x11, wayland) }

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Wayland, "desktop-provider", "attach-exception"),
            ),
            facade.attach(
                scope,
                factory,
                DesktopHostOptions.Embedded(DesktopIntegration.AwtEventDispatchThread),
                KadrePolicies.Default,
            ),
        )
        assertEquals(1, wayland.embeddedRequests.size)
        assertEquals(0, x11.embeddedRequests.size)
    }

    @Test
    fun providerCancellationIsNeverWrapped() {
        val cancellation = CancellationException("cancelled")
        val provider = RecordingProvider(
            DesktopBackendKind.AppKit,
            setOf(DesktopIntegrationKind.AppKitMainLoop),
            attachFailure = cancellation,
        )
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }

        val observed = assertFailsWith<CancellationException> {
            facade.attach(
                scope,
                factory,
                DesktopHostOptions.Embedded(DesktopIntegration.AppKitMainLoop),
                KadrePolicies.Default,
            )
        }
        assertSame(cancellation, observed)
    }

    @Test
    fun providerFailureOutsideHostAttachDomainIsNormalized() {
        val provider = RecordingProvider(DesktopBackendKind.AppKit, setOf(DesktopIntegrationKind.AppKitMainLoop)).apply {
            attachResult = KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        }
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "desktop-provider", "invalid-attach-failure"),
            ),
            facade.attach(
                scope,
                factory,
                DesktopHostOptions.Embedded(DesktopIntegration.AppKitMainLoop),
                KadrePolicies.Default,
            ),
        )
    }

    @Test
    fun standaloneReturnsOutcomeAndThrowsOnlyForPreSessionFailure() {
        val provider = RecordingProvider(DesktopBackendKind.AppKit, setOf(DesktopIntegrationKind.AppKitMainLoop))
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }
        val options = DesktopHostOptions.Standalone(DesktopBackend.AppKit, stopWhenLastWindowClosed = false)

        assertEquals(SessionOutcome.Completed, facade.run(factory, options, KadrePolicies.Recording))
        val request = provider.standaloneRequests.single()
        assertSame(factory, request.applicationFactory)
        assertSame(KadrePolicies.Recording, request.policy)
        assertFalse(request.stopWhenLastWindowClosed)

        provider.runResult = KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        val exception = assertFailsWith<KadreException> {
            facade.run(factory, options, KadrePolicies.Default)
        }
        assertEquals(KadreFailure.AlreadyInUse(KadreResourceKind.Host), exception.failure)
    }

    @Test
    fun providerCatalogIsLoadedLazilyAndCached() {
        var loads = 0
        val provider = RecordingProvider(DesktopBackendKind.AppKit, setOf(DesktopIntegrationKind.AppKitMainLoop))
        val catalog = LazyDesktopProviderCatalog {
            loads += 1
            listOf(provider)
        }

        assertEquals(0, loads)
        assertEquals(listOf(provider), catalog.providers())
        assertEquals(listOf(provider), catalog.providers())
        assertEquals(1, loads)
    }
}

private class RecordingProvider(
    override val backend: DesktopBackendKind,
    override val supportedIntegrations: Set<DesktopIntegrationKind>,
    private val attachFailure: RuntimeException? = null,
    private val available: Boolean = true,
) : DesktopBackendProvider {
    val session = StubSession()
    val embeddedRequests = mutableListOf<DesktopEmbeddedRequest>()
    val standaloneRequests = mutableListOf<DesktopStandaloneRequest>()
    var attachResult: KadreResult<KadreSession> = KadreResult.Success(session)
    var runResult: KadreResult<SessionOutcome> = KadreResult.Success(SessionOutcome.Completed)

    override fun isAvailable(): Boolean = available

    override fun attach(request: DesktopEmbeddedRequest): KadreResult<KadreSession> {
        embeddedRequests += request
        attachFailure?.let { throw it }
        return attachResult
    }

    override fun run(request: DesktopStandaloneRequest): KadreResult<SessionOutcome> {
        standaloneRequests += request
        return runResult
    }
}

private class StubSession : KadreSession {
    override val id: SessionId get() = error("opaque in selection tests")
    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.Starting)

    override fun close() = Unit
    override fun requestStop() = Unit
    override suspend fun awaitTermination(): SessionOutcome = SessionOutcome.Completed
}
