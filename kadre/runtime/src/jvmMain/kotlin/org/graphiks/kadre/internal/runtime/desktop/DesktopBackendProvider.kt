package org.graphiks.kadre.internal.runtime.desktop

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicy

public enum class DesktopBackendKind { AppKit, Win32, X11, Wayland }

public enum class DesktopIntegrationKind { AppKitMainLoop, AwtEventDispatchThread, JavaFxApplicationThread }

public class DesktopEmbeddedRequest(
    public val parentScope: CoroutineScope,
    public val applicationFactory: KadreApplicationFactory,
    public val integration: DesktopIntegrationKind,
    public val policy: KadrePolicy,
)

public class DesktopStandaloneRequest(
    public val applicationFactory: KadreApplicationFactory,
    public val stopWhenLastWindowClosed: Boolean,
    public val policy: KadrePolicy,
)

public interface DesktopBackendProvider {
    public val backend: DesktopBackendKind
    public val supportedIntegrations: Set<DesktopIntegrationKind>

    public fun isAvailable(): Boolean

    public fun attach(request: DesktopEmbeddedRequest): KadreResult<KadreSession>

    public fun run(request: DesktopStandaloneRequest): KadreResult<SessionOutcome>
}
