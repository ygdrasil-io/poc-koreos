package org.graphiks.kadre.platform.desktop

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
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
): KadreResult<KadreSession> = unsupportedHostAttach()

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
): SessionOutcome = throw KadreException(UNSUPPORTED_HOST_ATTACH)

public fun runKadreApplication(
    options: DesktopHostOptions.Standalone = DesktopHostOptions.Standalone(),
    policy: KadrePolicy = KadrePolicies.Default,
    application: KadreApplication,
): SessionOutcome = runKadreApplication(
    applicationFactory = KadreApplicationFactory { application },
    options = options,
    policy = policy,
)

private val UNSUPPORTED_HOST_ATTACH: KadreFailure.Unsupported =
    KadreFailure.Unsupported(KadreOperation.HostAttach)

private fun unsupportedHostAttach(): KadreResult<KadreSession> =
    KadreResult.Failure(UNSUPPORTED_HOST_ATTACH)
