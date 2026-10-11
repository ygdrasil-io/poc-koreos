package org.graphiks.kadre.internal.wayland

import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.policy.KadrePolicies

class WaylandBackendProviderTest {

    @Test
    fun discoveryFindsExactlyOneWaylandProvider() {
        val providers = ServiceLoader.load(DesktopBackendProvider::class.java).toList()
        val provider = providers.single { it.backend == DesktopBackendKind.Wayland }
        assertEquals(emptySet(), provider.supportedIntegrations)
        assertEquals(isLinux(), provider.isAvailable())
    }

    @Test
    fun embeddedAttachIsUnsupportedInTrancheOne() {
        val provider = WaylandBackendProvider.forTesting { true }
        val request = DesktopEmbeddedRequest(
            parentScope = CoroutineScope(Job()),
            applicationFactory = KadreApplicationFactory { KadreApplication { } },
            integration = DesktopIntegrationKind.AwtEventDispatchThread,
            policy = KadrePolicies.Default,
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)),
            provider.attach(request),
        )
    }
}
