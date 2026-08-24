package org.graphiks.kadre.platform.desktop

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy

public enum class DesktopBackend { Auto, AppKit, Win32, X11, Wayland }

public enum class DesktopIntegration { AppKitMainLoop, AwtEventDispatchThread, JavaFxApplicationThread }

public sealed interface DesktopHostOptions {
    public data class Embedded(
        public val integration: DesktopIntegration,
        public val backend: DesktopBackend = DesktopBackend.Auto,
    ) : DesktopHostOptions

    public data class Standalone(
        public val backend: DesktopBackend = DesktopBackend.Auto,
        public val stopWhenLastWindowClosed: Boolean = true,
    ) : DesktopHostOptions
}

public fun CoroutineScope.attachKadreDesktop(
    applicationFactory: KadreApplicationFactory,
    options: DesktopHostOptions,
    policy: KadrePolicy = KadrePolicies.Default,
): KadreResult<KadreSession> = defaultDesktopHostFacade.attach(this, applicationFactory, options, policy)

public fun CoroutineScope.attachKadreDesktop(
    options: DesktopHostOptions,
    policy: KadrePolicy = KadrePolicies.Default,
    application: KadreApplication,
): KadreResult<KadreSession> = attachKadreDesktop(
    applicationFactory = KadreApplicationFactory { application },
    options = options,
    policy = policy,
)

public fun runKadreApplication(
    applicationFactory: KadreApplicationFactory,
    options: DesktopHostOptions.Standalone = DesktopHostOptions.Standalone(),
    policy: KadrePolicy = KadrePolicies.Default,
): SessionOutcome = defaultDesktopHostFacade.run(applicationFactory, options, policy)

public fun runKadreApplication(
    options: DesktopHostOptions.Standalone = DesktopHostOptions.Standalone(),
    policy: KadrePolicy = KadrePolicies.Default,
    application: KadreApplication,
): SessionOutcome = runKadreApplication(
    applicationFactory = KadreApplicationFactory { application },
    options = options,
    policy = policy,
)
