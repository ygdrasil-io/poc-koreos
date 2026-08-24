package org.graphiks.kadre.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.capture.CaptureManager
import org.graphiks.kadre.diagnostics.ExperimentalKadreApi
import org.graphiks.kadre.diagnostics.KadreDiagnostics
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayManager
import org.graphiks.kadre.input.DeviceManager
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowRequestId

public fun interface KadreApplication {
    public suspend fun KadreScope.run()
}

public fun interface KadreApplicationFactory {
    public fun create(context: KadreLaunchContext): KadreApplication
}

public data class KadreLaunchContext(
    public val sessionId: SessionId,
    public val reason: KadreLaunchReason,
    public val originatingRequestId: WindowRequestId?,
    public val restorationToken: RestorationToken?,
)

public enum class KadreLaunchReason { InitialHostAttachment, AdditionalHostRequested, HostRestoration }

public interface KadreScope : CoroutineScope {
    public val sessionId: SessionId
    public val policy: KadrePolicy
    public val lifecycle: KadreLifecycle
    public val primarySurface: StateFlow<HostSurface?>
    public val windows: WindowManager
    public val displays: DisplayManager
    public val devices: DeviceManager
    public val capture: CaptureManager
    public val diagnostics: KadreDiagnostics

    public fun requestStop()
}

public interface KadreSession : AutoCloseable {
    public val id: SessionId
    public val state: StateFlow<SessionState>

    override fun close()
    public fun requestStop()
    public suspend fun awaitTermination(): SessionOutcome
}

public sealed interface SessionState {
    public data object Starting : SessionState
    public data object Running : SessionState
    public data object Stopping : SessionState
    public data class Terminated(public val outcome: SessionOutcome) : SessionState
}

public sealed interface SessionOutcome {
    public data object Completed : SessionOutcome
    public data class Stopped(public val reason: SessionStopReason) : SessionOutcome
    public data class Failed(public val failure: KadreFailure) : SessionOutcome
}

public enum class SessionStopReason {
    HostRequested,
    ApplicationRequested,
    ApplicationCancelled,
    ParentCancelled,
    HostDetached,
}

public class RestorationToken internal constructor(private val encoded: String) {
    init { require(encoded.isNotEmpty()) { "encoded token must not be empty" } }
    override fun equals(other: Any?): Boolean = other is RestorationToken && encoded == other.encoded
    override fun hashCode(): Int = encoded.hashCode()
    override fun toString(): String = "RestorationToken(<redacted>)"
}

@ExperimentalKadreApi
public interface KadreHost {
    public val platform: KadrePlatform

    public fun attach(
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
    ): KadreResult<KadreSession>
}
