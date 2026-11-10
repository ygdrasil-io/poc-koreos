package org.graphiks.kadre.internal.runtime.desktop

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.WindowSpec

public enum class DesktopBackendKind { AppKit, Win32, X11, Wayland }

public enum class DesktopIntegrationKind { AppKitMainLoop, AwtEventDispatchThread, JavaFxApplicationThread }

public class DesktopEmbeddedRequest(
    public val parentScope: CoroutineScope,
    public val applicationFactory: KadreApplicationFactory,
    public val integration: DesktopIntegrationKind,
    public val policy: KadrePolicy,
    public val allowUserAttention: Boolean = false,
)

/**
 * The ownership-first run request: one application, one process Kadre owns. There is no
 * `stopWhenLastWindowClosed` field — the last-container rule is fixed runtime semantics
 * (the process controller ends the session when its last OS container closes).
 */
public data class DesktopRunRequest(
    public val applicationFactory: KadreApplicationFactory,
    public val policy: KadrePolicy = KadrePolicies.Default,
    public val firstWindow: WindowSpec? = null,
)

public interface DesktopBackendProvider {
    public val backend: DesktopBackendKind
    public val supportedIntegrations: Set<DesktopIntegrationKind>

    public fun isAvailable(): Boolean

    public fun attach(request: DesktopEmbeddedRequest): KadreResult<KadreSession>

    public fun run(request: DesktopRunRequest): KadreResult<SessionOutcome>
}
