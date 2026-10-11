package org.graphiks.kadre.internal.wayland

import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest

public class WaylandBackendProvider private constructor(
    private val availability: () -> Boolean,
) : DesktopBackendProvider {
    public constructor() : this(::isLinux)

    override val backend: DesktopBackendKind = DesktopBackendKind.Wayland

    override val supportedIntegrations: Set<DesktopIntegrationKind> = emptySet()

    override fun isAvailable(): Boolean = availability()

    override fun attach(request: DesktopEmbeddedRequest): KadreResult<KadreSession> {
        return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
    }

    override fun run(request: DesktopStandaloneRequest): KadreResult<SessionOutcome> {
        return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))
    }

    public companion object {
        internal fun forTesting(availability: () -> Boolean): WaylandBackendProvider =
            WaylandBackendProvider(availability)
    }
}
