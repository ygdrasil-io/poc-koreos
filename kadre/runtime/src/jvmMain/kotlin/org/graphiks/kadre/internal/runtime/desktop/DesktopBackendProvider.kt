package org.graphiks.kadre.internal.runtime.desktop

import kotlinx.coroutines.CoroutineDispatcher
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.WindowSpec

public enum class DesktopBackendKind { AppKit, Win32, X11, Wayland }

/**
 * The ownership-first run request: one application, one process Kadre owns. There is no
 * `stopWhenLastWindowClosed` field — the last-container rule is fixed runtime semantics
 * (the process controller ends the session when its last OS container closes).
 *
 * [mainDispatcher] is the main dispatcher the boot runs its owned root scope on. The desktop
 * facade creates and owns a dedicated single-thread dispatcher per run and closes it at session
 * teardown: the public entry must work on a desktop JVM where no `Dispatchers.Main`
 * implementation exists, without installing any process-global state.
 */
public data class DesktopRunRequest(
    public val applicationFactory: KadreApplicationFactory,
    public val policy: KadrePolicy = KadrePolicies.Default,
    public val firstWindow: WindowSpec? = null,
    public val mainDispatcher: CoroutineDispatcher? = null,
)

/**
 * The final desktop backend SPI: one backend, one availability probe, one ownership-first
 * run. Kadre owns the process — there is no embedded attach path anymore.
 */
public interface DesktopBackendProvider {
    public val backend: DesktopBackendKind

    public fun isAvailable(): Boolean

    public fun run(request: DesktopRunRequest): KadreResult<SessionOutcome>
}
