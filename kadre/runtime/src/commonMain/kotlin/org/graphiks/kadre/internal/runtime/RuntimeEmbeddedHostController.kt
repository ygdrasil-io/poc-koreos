package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionPhase
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.SurfaceId

/**
 * supprimé par la Task 8 — chemins embedded.
 *
 * This is the OLD world's host controller, relocated: the public host-attach contract it
 * implemented is gone (Task 7 deleted the interface from foundation), but the desktop
 * backends' EMBEDDED paths (`DesktopBackendProvider.attach` — appkit/wayland/win32) still
 * assemble their sessions through `withComponents`/`withPrimarySurface` + `attach` until
 * Task 8 removes the embedded desktop surface entirely. The class keeps its name so those
 * providers compile unchanged; it implements nothing, publishes on the phase axis, and
 * carries no public API standing beyond the embedded seams.
 */
public class RuntimeHostController private constructor(
    public val platform: KadrePlatform,
    initialLifecycleState: LifecycleState,
    initialLifecycleCapabilities: LifecycleCapabilities,
    private val failureReporter: RuntimeFailureReporter,
    private val sessionRevocationHandler: RuntimeSessionRevocationHandler,
    private val sessionStopHandler: RuntimeSessionStopHandler,
    private val sessionObserver: RuntimeSessionObserver,
    private val clockFactory: RuntimeClockFactory,
    private val componentsFactory: RuntimeSessionComponentsFactory,
) {
    public constructor(
        platform: KadrePlatform,
        initialLifecycleState: LifecycleState = DEFAULT_LIFECYCLE_STATE,
        initialLifecycleCapabilities: LifecycleCapabilities = DEFAULT_LIFECYCLE_CAPABILITIES,
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        sessionStopHandler: RuntimeSessionStopHandler = RuntimeSessionStopHandler { null },
        sessionObserver: RuntimeSessionObserver = RuntimeSessionObserver { _, _ -> },
    ) : this(
        platform,
        initialLifecycleState,
        initialLifecycleCapabilities,
        failureReporter,
        NO_SESSION_REVOCATION,
        sessionStopHandler,
        sessionObserver,
        MonotonicRuntimeClockFactory,
        UnsupportedRuntimeSessionComponentsFactory,
    )

    private val lock = RuntimeLock()
    private val sessions = linkedSetOf<SessionRuntime>()
    private var lifecycleState = initialLifecycleState
    private var lifecycleCapabilities = initialLifecycleCapabilities
    private var terminated = initialLifecycleState.phase == SessionPhase.Terminated

    public fun attach(
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
    ): KadreResult<KadreSession> = attach(parentScope, applicationFactory, policy, launch = null)

    internal fun attach(
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy,
        launch: KadreLaunchInfo?,
    ): KadreResult<KadreSession> {
        val parentJob = parentScope.coroutineContext[Job]
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("parentScope"))
        if (!parentJob.isActive) return KadreResult.Failure(KadreFailure.ParentScopeCancelled)

        val initialLifecycle = lock.withLock {
            if (terminated) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
            if (!parentJob.isActive) return KadreResult.Failure(KadreFailure.ParentScopeCancelled)
            lifecycleState to lifecycleCapabilities
        }
        val clock = clockFactory.start()
        val session = try {
            SessionRuntime(
                id = RuntimeProcessIds.nextSessionId(),
                parentScope = parentScope,
                applicationFactory = applicationFactory,
                policy = policy,
                initialLifecycleState = initialLifecycle.first,
                initialLifecycleCapabilities = initialLifecycle.second,
                clock = clock,
                failureReporter = ::reportFailure,
                onRevoking = ::sessionRevoking,
                onStopping = ::sessionStopping,
                onTerminated = ::sessionTerminated,
                componentsFactory = componentsFactory,
                launchInfo = launch,
            )
        } catch (cause: Throwable) {
            reportFailure(cause)
            return KadreResult.Failure(runtimeSessionComponentsFailure())
        }

        val installFailure = lock.withLock {
            when {
                terminated -> KadreFailure.Closed(KadreResourceKind.Host)
                !parentJob.isActive -> KadreFailure.ParentScopeCancelled
                else -> {
                    session.updateLifecycle(lifecycleState)
                    session.updateLifecycleCapabilities(lifecycleCapabilities)
                    sessions += session
                    null
                }
            }
        }
        if (installFailure != null) {
            session.disposeUnstarted()
            return KadreResult.Failure(installFailure)
        }

        session.start()
        return KadreResult.Success(session)
    }

    public fun updateLifecycle(state: LifecycleState): LifecycleState {
        val targets = lock.withLock {
            require(!terminated) { "embedded host is terminated" }
            if (lifecycleState == state) return lifecycleState
            lifecycleState = state
            if (state.phase == SessionPhase.Terminated) terminated = true
            sessions.toList()
        }
        targets.forEach { it.updateLifecycle(state) }
        if (state.phase == SessionPhase.Terminated) {
            targets.forEach(SessionRuntime::hostDetached)
        }
        return state
    }

    public fun updateLifecycleCapabilities(capabilities: LifecycleCapabilities) {
        val targets = lock.withLock {
            require(!terminated) { "embedded host is terminated" }
            if (lifecycleCapabilities == capabilities) return
            lifecycleCapabilities = capabilities
            sessions.toList()
        }
        targets.forEach { it.updateLifecycleCapabilities(capabilities) }
    }

    public fun emitMemoryPressure(level: MemoryPressureLevel) {
        val targets = lock.withLock {
            require(!terminated) { "embedded host is terminated" }
            require(lifecycleCapabilities.memoryPressure == FeatureAvailability.Available) {
                "memory pressure is unavailable"
            }
            sessions.toList()
        }
        targets.forEach { it.emitMemoryPressure(level) }
    }

    public fun detach() {
        val terminatedState = LifecycleState(
            SessionPhase.Terminated,
            VisibilityState.Background,
            ActivationState.Inactive,
        )
        val targets = lock.withLock {
            if (terminated) return
            terminated = true
            lifecycleState = terminatedState
            sessions.toList()
        }
        targets.forEach { it.updateLifecycle(terminatedState) }
        targets.forEach(SessionRuntime::hostDetached)
    }

    /**
     * Immediately publishes termination for a runtime that may be destroyed without a
     * cooperative shutdown opportunity, such as browser pagehide.
     */
    internal fun detachImmediately() {
        val terminatedState = LifecycleState(
            SessionPhase.Terminated,
            VisibilityState.Background,
            ActivationState.Inactive,
        )
        val targets = lock.withLock {
            if (!terminated) {
                terminated = true
                lifecycleState = terminatedState
            }
            sessions.toList()
        }
        targets.forEach { it.updateLifecycle(terminatedState) }
        targets.forEach(SessionRuntime::hostDetachedImmediately)
    }

    public fun fail(failure: KadreFailure.PlatformFailure) {
        val targets = lock.withLock { sessions.toList() }
        targets.forEach { it.hostFailed(failure) }
    }

    private fun reportFailure(cause: Throwable) {
        runCatching { failureReporter.report(cause) }
    }

    private fun sessionStopping(session: SessionRuntime): KadreFailure.PlatformFailure? =
        try {
            // Functional stop failures must be returned explicitly; thrown adapter bugs remain
            // diagnostic-only so arbitrary host code cannot escape through session teardown.
            sessionStopHandler.stop(session.id)
        } catch (cause: Throwable) {
            reportFailure(cause)
            null
        }

    private fun sessionRevoking(session: SessionRuntime) {
        sessionRevocationHandler.revoke(session.id)
    }

    private fun sessionTerminated(session: SessionRuntime, outcome: SessionOutcome) {
        lock.withLock { sessions.remove(session) }
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
            SessionPhase.Running,
            VisibilityState.Foreground,
            ActivationState.Active,
        )
        private val DEFAULT_LIFECYCLE_CAPABILITIES: LifecycleCapabilities =
            LifecycleCapabilities(FeatureAvailability.Unsupported)

        internal fun withClock(
            platform: KadrePlatform,
            clockFactory: RuntimeClockFactory,
            initialLifecycleState: LifecycleState = DEFAULT_LIFECYCLE_STATE,
            initialLifecycleCapabilities: LifecycleCapabilities = DEFAULT_LIFECYCLE_CAPABILITIES,
            failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
            sessionStopHandler: RuntimeSessionStopHandler = RuntimeSessionStopHandler { null },
            sessionObserver: RuntimeSessionObserver = RuntimeSessionObserver { _, _ -> },
        ): RuntimeHostController = RuntimeHostController(
            platform,
            initialLifecycleState,
            initialLifecycleCapabilities,
            failureReporter,
            NO_SESSION_REVOCATION,
            sessionStopHandler,
            sessionObserver,
            clockFactory,
            UnsupportedRuntimeSessionComponentsFactory,
        )

        /**
         * Unstable backend SPI for creating a host with session-owned backend components.
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
        ): RuntimeHostController = RuntimeHostController(
            platform,
            initialLifecycleState,
            initialLifecycleCapabilities,
            failureReporter,
            NO_SESSION_REVOCATION,
            sessionStopHandler,
            sessionObserver,
            MonotonicRuntimeClockFactory,
            componentsFactory,
        )

        /**
         * Creates a host with session-owned components and the revocation handler the embedded
         * owners need: the public [withComponents] has no such parameter, and a host whose components
         * hold target-owned bridges must release them when its session is revoked.
         */
        internal fun withComponents(
            platform: KadrePlatform,
            sessionRevocationHandler: RuntimeSessionRevocationHandler,
            componentsFactory: RuntimeSessionComponentsFactory,
            initialLifecycleState: LifecycleState = DEFAULT_LIFECYCLE_STATE,
            initialLifecycleCapabilities: LifecycleCapabilities = DEFAULT_LIFECYCLE_CAPABILITIES,
            failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
            sessionStopHandler: RuntimeSessionStopHandler = RuntimeSessionStopHandler { null },
            sessionObserver: RuntimeSessionObserver = RuntimeSessionObserver { _, _ -> },
        ): RuntimeHostController = RuntimeHostController(
            platform,
            initialLifecycleState,
            initialLifecycleCapabilities,
            failureReporter,
            sessionRevocationHandler,
            sessionStopHandler,
            sessionObserver,
            MonotonicRuntimeClockFactory,
            componentsFactory,
        )

        /**
         * Creates a host whose sessions receive one host-provided primary surface without a
         * runtime-managed window.
         */
        public fun withPrimarySurface(
            platform: KadrePlatform,
            initialLifecycleState: LifecycleState = DEFAULT_LIFECYCLE_STATE,
            initialLifecycleCapabilities: LifecycleCapabilities = DEFAULT_LIFECYCLE_CAPABILITIES,
            failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
            sessionStopHandler: RuntimeSessionStopHandler = RuntimeSessionStopHandler { null },
            sessionObserver: RuntimeSessionObserver = RuntimeSessionObserver { _, _ -> },
            primarySurfaceFactory: (SurfaceId) -> RuntimePrimarySurface,
        ): RuntimeHostController = withPrimarySurface(
            platform = platform,
            sessionRevocationHandler = NO_SESSION_REVOCATION,
            initialLifecycleState = initialLifecycleState,
            initialLifecycleCapabilities = initialLifecycleCapabilities,
            failureReporter = failureReporter,
            sessionStopHandler = sessionStopHandler,
            sessionObserver = sessionObserver,
            primarySurfaceFactory = primarySurfaceFactory,
        )

        internal fun withPrimarySurface(
            platform: KadrePlatform,
            sessionRevocationHandler: RuntimeSessionRevocationHandler,
            initialLifecycleState: LifecycleState = DEFAULT_LIFECYCLE_STATE,
            initialLifecycleCapabilities: LifecycleCapabilities = DEFAULT_LIFECYCLE_CAPABILITIES,
            failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
            sessionStopHandler: RuntimeSessionStopHandler = RuntimeSessionStopHandler { null },
            sessionObserver: RuntimeSessionObserver = RuntimeSessionObserver { _, _ -> },
            primarySurfaceFactory: (SurfaceId) -> RuntimePrimarySurface,
            displayPort: DisplayPort? = null,
            gamepadPort: GamepadPort? = null,
            inputDevicePort: InputDevicePort? = null,
            capturePort: CapturePort? = null,
        ): RuntimeHostController = RuntimeHostController(
            platform = platform,
            initialLifecycleState = initialLifecycleState,
            initialLifecycleCapabilities = initialLifecycleCapabilities,
            failureReporter = failureReporter,
            sessionRevocationHandler = sessionRevocationHandler,
            sessionStopHandler = sessionStopHandler,
            sessionObserver = sessionObserver,
            clockFactory = MonotonicRuntimeClockFactory,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(
                    windows = UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId),
                    primarySurface = primarySurfaceFactory(RuntimeProcessIds.nextSurfaceId()),
                    displayPort = displayPort,
                    gamepadPort = gamepadPort,
                    inputDevicePort = inputDevicePort,
                    capturePort = capturePort,
                )
            },
        )

        private val NO_SESSION_REVOCATION: RuntimeSessionRevocationHandler =
            RuntimeSessionRevocationHandler { }
    }
}

/**
 * The launch identity an embedded boot carries into the application's
 * `KadreLaunchContext`: why the process was launched and which window request caused it,
 * when the embedder states one. A `null` launch reports [KadreLaunchReason.ProcessLaunch].
 */
internal data class KadreLaunchInfo(
    val reason: KadreLaunchReason,
    val originatingRequestId: org.graphiks.kadre.window.WindowRequestId?,
)

internal fun interface RuntimeSessionRevocationHandler {
    fun revoke(sessionId: SessionId)
}
