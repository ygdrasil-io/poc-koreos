package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowSpec

/**
 * The boot request of the ownership-first runtime: everything a platform boot hands the
 * controller to launch one application in a process Kadre owns.
 *
 * - [platform] names the platform the boot speaks for (diagnostics identity).
 * - [applicationFactory] creates the application the process runs.
 * - [policy] is the session's policy.
 * - [firstWindow] is the spec applied to the first OS container of the boot (the first desktop
 *   window, the delivered mobile scene or activity, the web root). The desktop core leaves it
 *   null when the boot opens no window; the platform boot applies it to the container its own
 *   machinery creates or receives. A controller whose sessions cannot manage windows (see
 *   [RuntimeProcessController.withPrimarySurface]) refuses a non-null spec before creating
 *   anything — a first window that can never exist is an invalid request.
 * - [primarySurface] is reserved for the platform boots whose OS delivers the first container's
 *   surface at boot (the mobile and web plans); the desktop core leaves it null.
 */
public data class RuntimeBootRequest(
    public val platform: KadrePlatform,
    public val applicationFactory: KadreApplicationFactory,
    public val policy: KadrePolicy = KadrePolicies.Default,
    public val firstWindow: WindowSpec? = null,
    public val primarySurface: HostSurface? = null,
)

/**
 * The ownership-first runtime controller: Kadre owns the process, so this controller owns the
 * root scope every session runs in — there is no host `parentScope` to validate and no detach
 * race to lose.
 *
 * Admission is linear: [boot] admits exactly one session at a time; a boot while a session is
 * alive fails with [KadreFailure.AlreadyInUse] and leaves the live session intact, and the next
 * boot after a session terminated is admitted again. Terminations are session-driven: the
 * last-container rule ends the session as [SessionOutcome.Completed] when the session's window
 * list becomes empty after having been non-empty, and [terminate] is the single system
 * termination path, producing `Stopped(SystemTermination)` and cancelling the owned root scope
 * once the session has reached its terminal state.
 *
 * This class is part of the `org.graphiks.kadre.internal` runtime group: technically public for
 * backend integration, consumer-unstable per PROJECT-ARCHITECTURE §4.2.
 */
public class RuntimeProcessController private constructor(
    private val platform: KadrePlatform,
    private val initialLifecycleState: LifecycleState,
    private val initialLifecycleCapabilities: LifecycleCapabilities,
    private val failureReporter: RuntimeFailureReporter,
    private val sessionStopHandler: RuntimeSessionStopHandler,
    private val sessionObserver: RuntimeSessionObserver,
    private val clockFactory: RuntimeClockFactory,
    componentsFactory: RuntimeSessionComponentsFactory,
    private val admitsFirstWindow: Boolean,
) {
    private val lock = RuntimeLock()
    private var liveSession: SessionRuntime? = null
    private var ownedScope: CoroutineScope? = null
    private val sessionComponentsFactory: RuntimeSessionComponentsFactory =
        RuntimeSessionComponentsFactory { sessionId, sessionScope ->
            val components = componentsFactory.create(sessionId, sessionScope)
            installLastContainerRule(sessionId, components.windows, sessionScope)
            components
        }

    /**
     * The root scope owned for the live session's boot
     * (`CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`), or null while no session
     * is live. Internal seam for tests and backend introspection of the owned lifetime.
     */
    internal val ownedRootScope: CoroutineScope?
        get() = lock.withLock { ownedScope }

    /**
     * Admits one session into the owned process and starts it.
     *
     * The request is validated before anything is created: a [RuntimeBootRequest.firstWindow]
     * this controller can never apply is refused as [KadreFailure.InvalidRequest] with no
     * session, no owned scope and no launched coroutine. A boot while a session is alive fails
     * with [KadreFailure.AlreadyInUse] and reserves nothing.
     */
    public fun boot(request: RuntimeBootRequest): KadreResult<KadreSession> {
        if (request.firstWindow != null && !admitsFirstWindow) {
            return KadreResult.Failure(KadreFailure.InvalidRequest("firstWindow"))
        }
        lock.withLock {
            // No `Process` kind exists in the closed KadreResourceKind set; `Host` is the
            // nearest kind (the process lease) and is renamed with the old world in Task 7.
            if (liveSession != null) return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        }

        // The root scope is owned, not inherited: each admitted boot gets its own, so nothing is
        // shared between sequential sessions and no caller scope can leak in.
        val rootScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val clock = clockFactory.start()
        val session = try {
            SessionRuntime(
                id = RuntimeProcessIds.nextSessionId(),
                parentScope = rootScope,
                applicationFactory = request.applicationFactory,
                policy = request.policy,
                initialLifecycleState = initialLifecycleState,
                initialLifecycleCapabilities = initialLifecycleCapabilities,
                clock = clock,
                failureReporter = ::reportFailure,
                onRevoking = {},
                onStopping = ::sessionStopping,
                onTerminated = ::sessionTerminated,
                componentsFactory = sessionComponentsFactory,
                launchInfo = KadreLaunchInfo(KadreLaunchReason.ProcessLaunch, originatingRequestId = null),
            )
        } catch (cause: Throwable) {
            reportFailure(cause)
            rootScope.cancel()
            return KadreResult.Failure(runtimeSessionComponentsFailure())
        }

        val installFailure = lock.withLock {
            if (liveSession != null) {
                // Same resource kind as the fast check: no `Process` kind exists in the closed
                // KadreResourceKind set; `Host` is the nearest kind (renamed with Task 7).
                KadreFailure.AlreadyInUse(KadreResourceKind.Host)
            } else {
                liveSession = session
                ownedScope = rootScope
                null
            }
        }
        if (installFailure != null) {
            session.disposeUnstarted()
            rootScope.cancel()
            return KadreResult.Failure(installFailure)
        }

        session.start()
        return KadreResult.Success(session)
    }

    /**
     * The single system termination path: the process's OS containers are being torn down (loop
     * stopped, scene destroyed, page hidden), so the live session stops as
     * `Stopped(SystemTermination)`. Idempotent: with no live session this is a no-op. The owned
     * root scope of the boot is cancelled when the session reaches its terminal state.
     *
     * Public for backend integration (the desktop providers call it when their OS loop ends),
     * consumer-unstable like the rest of this class.
     */
    public fun terminate(reason: SessionStopReason) {
        require(reason == SessionStopReason.SystemTermination) {
            "RuntimeProcessController.terminate only admits SessionStopReason.SystemTermination"
        }
        val session = lock.withLock { liveSession } ?: return
        session.systemTerminating()
    }

    /**
     * Fails the live session as [SessionOutcome.Failed] — the platform's own machinery broke
     * (pump failure, native input read failure, display connection lost). Idempotent: with no
     * live session this is a no-op.
     *
     * Public for backend integration (the desktop providers own the failure paths of their
     * loops), consumer-unstable like the rest of this class.
     */
    public fun fail(failure: KadreFailure.PlatformFailure) {
        val session = lock.withLock { liveSession } ?: return
        session.hostFailed(failure)
    }

    /**
     * Delivers one OS memory-pressure level to the live session. With no live session this is a
     * no-op. Public for backend integration (the AppKit standalone lease forwards the native
     * dispatch source here), consumer-unstable like the rest of this class.
     */
    public fun emitMemoryPressure(level: MemoryPressureLevel) {
        val session = lock.withLock { liveSession } ?: return
        session.emitMemoryPressure(level)
    }

    private fun installLastContainerRule(
        sessionId: SessionId,
        windows: WindowManager,
        sessionScope: CoroutineScope,
    ) {
        // The watcher lives on the session's own scope, so it dies with the session and never
        // outlives the boot. StateFlow replays the current value at subscription: a session that
        // never had a window is never terminated by this rule. The rule fires exactly once —
        // takeWhile ends the watcher on the empty-after-non-empty state.
        sessionScope.launch {
            var seenWindow = false
            windows.state.takeWhile { managerState ->
                if (managerState.windows.isNotEmpty()) {
                    seenWindow = true
                    true
                } else if (seenWindow) {
                    // A dying watcher must only ever terminate its own session: the fire path
                    // never suspends, so its resumption can run after this session terminated
                    // (cancelled job notwithstanding), when a later boot is already live.
                    lock.withLock { liveSession }
                        ?.takeIf { it.id == sessionId }
                        ?.lastContainerClosed()
                    false
                } else {
                    true
                }
            }.collect { }
        }
    }

    private fun reportFailure(cause: Throwable) {
        runCatching { failureReporter.report(cause) }
    }

    private fun sessionStopping(session: SessionRuntime): KadreFailure.PlatformFailure? =
        try {
            // Functional stop failures must be returned explicitly; thrown adapter bugs remain
            // diagnostic-only so arbitrary platform code cannot escape through session teardown.
            sessionStopHandler.stop(session.id)
        } catch (cause: Throwable) {
            reportFailure(cause)
            null
        }

    private fun sessionTerminated(session: SessionRuntime, outcome: SessionOutcome) {
        val scopeToCancel = lock.withLock {
            if (liveSession !== session) return
            liveSession = null
            val scope = ownedScope
            ownedScope = null
            scope
        }
        // Cancel the owned scope after the session published its terminal state, never before:
        // the session's own teardown coroutines run inside it.
        scopeToCancel?.cancel()
        runCatching { sessionObserver.terminated(session.id, outcome) }
            .exceptionOrNull()
            ?.let(::reportFailure)
    }

    private fun runtimeSessionComponentsFailure(): KadreFailure.PlatformFailure =
        KadreFailure.PlatformFailure(
            platform,
            "runtime-session-components",
            "create-failed",
        )

    public companion object {
        private val DEFAULT_LIFECYCLE_STATE: LifecycleState = LifecycleState(
            AttachmentState.Attached,
            VisibilityState.Foreground,
            ActivationState.Active,
        )
        private val DEFAULT_LIFECYCLE_CAPABILITIES: LifecycleCapabilities =
            LifecycleCapabilities(FeatureAvailability.Unsupported)

        /**
         * Unstable backend SPI for creating a controller with session-owned backend components.
         *
         * This function is technically public only for backend integration. It is not part of
         * Kadre's supported public API and may change without compatibility guarantees.
         */
        public fun withComponents(
            platform: KadrePlatform,
            componentsFactory: RuntimeSessionComponentsFactory,
            initialLifecycleState: LifecycleState = DEFAULT_LIFECYCLE_STATE,
            initialLifecycleCapabilities: LifecycleCapabilities = DEFAULT_LIFECYCLE_CAPABILITIES,
            failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
            sessionStopHandler: RuntimeSessionStopHandler = RuntimeSessionStopHandler { null },
            sessionObserver: RuntimeSessionObserver = RuntimeSessionObserver { _, _ -> },
        ): RuntimeProcessController = RuntimeProcessController(
            platform,
            initialLifecycleState,
            initialLifecycleCapabilities,
            failureReporter,
            sessionStopHandler,
            sessionObserver,
            MonotonicRuntimeClockFactory,
            componentsFactory,
            admitsFirstWindow = true,
        )

        /**
         * Creates a controller whose sessions receive one provided primary surface without a
         * runtime-managed window. Because no window machinery exists in this shape, a boot
         * request carrying a [RuntimeBootRequest.firstWindow] is refused before anything is
         * created.
         */
        public fun withPrimarySurface(
            platform: KadrePlatform,
            initialLifecycleState: LifecycleState = DEFAULT_LIFECYCLE_STATE,
            initialLifecycleCapabilities: LifecycleCapabilities = DEFAULT_LIFECYCLE_CAPABILITIES,
            failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
            sessionStopHandler: RuntimeSessionStopHandler = RuntimeSessionStopHandler { null },
            sessionObserver: RuntimeSessionObserver = RuntimeSessionObserver { _, _ -> },
            primarySurfaceFactory: (SurfaceId) -> RuntimePrimarySurface,
        ): RuntimeProcessController = RuntimeProcessController(
            platform,
            initialLifecycleState,
            initialLifecycleCapabilities,
            failureReporter,
            sessionStopHandler,
            sessionObserver,
            MonotonicRuntimeClockFactory,
            RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(
                    windows = UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId),
                    primarySurface = primarySurfaceFactory(RuntimeProcessIds.nextSurfaceId()),
                )
            },
            admitsFirstWindow = false,
        )
    }
}
