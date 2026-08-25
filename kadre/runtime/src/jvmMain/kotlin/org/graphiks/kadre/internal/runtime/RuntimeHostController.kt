package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreHost
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.ExperimentalKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy

public fun interface RuntimeFailureReporter {
    public fun report(cause: Throwable)
}

public fun interface RuntimeSessionObserver {
    public fun terminated(sessionId: SessionId, outcome: SessionOutcome)
}

public fun interface RuntimeSessionStopHandler {
    public fun stop(sessionId: SessionId): KadreFailure.PlatformFailure?
}

@OptIn(ExperimentalKadreApi::class)
public class RuntimeHostController private constructor(
    override val platform: KadrePlatform,
    initialLifecycleState: LifecycleState,
    initialLifecycleCapabilities: LifecycleCapabilities,
    private val failureReporter: RuntimeFailureReporter,
    private val sessionStopHandler: RuntimeSessionStopHandler,
    private val sessionObserver: RuntimeSessionObserver,
    private val clockFactory: RuntimeClockFactory,
    private val componentsFactory: RuntimeSessionComponentsFactory,
) : KadreHost {
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
        sessionStopHandler,
        sessionObserver,
        MonotonicRuntimeClockFactory,
        UnsupportedRuntimeSessionComponentsFactory,
    )

    private val lock = Any()
    private val sessions = linkedSetOf<SessionRuntime>()
    private var lifecycleState = initialLifecycleState
    private var lifecycleCapabilities = initialLifecycleCapabilities
    private var detached = initialLifecycleState.attachment == AttachmentState.Detached

    override fun attach(
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy,
    ): KadreResult<KadreSession> {
        val parentJob = parentScope.coroutineContext[Job]
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("parentScope"))
        if (!parentJob.isActive) return KadreResult.Failure(KadreFailure.ParentScopeCancelled)

        val initialLifecycle = synchronized(lock) {
            if (detached) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
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
                onStopping = ::sessionStopping,
                onTerminated = ::sessionTerminated,
                componentsFactory = componentsFactory,
            )
        } catch (cause: Exception) {
            reportFailure(cause)
            return KadreResult.Failure(runtimeSessionComponentsFailure())
        } catch (cause: LinkageError) {
            reportFailure(cause)
            return KadreResult.Failure(runtimeSessionComponentsFailure())
        }

        val installFailure = synchronized(lock) {
            when {
                detached -> KadreFailure.Closed(KadreResourceKind.Host)
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
        val targets = synchronized(lock) {
            require(!detached) { "host is detached" }
            if (lifecycleState == state) return lifecycleState
            lifecycleState = state
            if (state.attachment == AttachmentState.Detached) detached = true
            sessions.toList()
        }
        targets.forEach { it.updateLifecycle(state) }
        if (state.attachment == AttachmentState.Detached) {
            targets.forEach(SessionRuntime::hostDetached)
        }
        return state
    }

    public fun updateLifecycleCapabilities(capabilities: LifecycleCapabilities) {
        val targets = synchronized(lock) {
            require(!detached) { "host is detached" }
            if (lifecycleCapabilities == capabilities) return
            lifecycleCapabilities = capabilities
            sessions.toList()
        }
        targets.forEach { it.updateLifecycleCapabilities(capabilities) }
    }

    public fun emitMemoryPressure(level: MemoryPressureLevel) {
        val targets = synchronized(lock) {
            require(!detached) { "host is detached" }
            require(lifecycleCapabilities.memoryPressure == FeatureAvailability.Available) {
                "memory pressure is unavailable"
            }
            sessions.toList()
        }
        targets.forEach { it.emitMemoryPressure(level) }
    }

    public fun detach() {
        val detachedState = LifecycleState(
            AttachmentState.Detached,
            VisibilityState.Background,
            ActivationState.Inactive,
        )
        val targets = synchronized(lock) {
            if (detached) return
            detached = true
            lifecycleState = detachedState
            sessions.toList()
        }
        targets.forEach { it.updateLifecycle(detachedState) }
        targets.forEach(SessionRuntime::hostDetached)
    }

    public fun fail(failure: KadreFailure.PlatformFailure) {
        val targets = synchronized(lock) { sessions.toList() }
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
        } catch (cause: Exception) {
            reportFailure(cause)
            null
        } catch (cause: LinkageError) {
            reportFailure(cause)
            null
        }

    private fun sessionTerminated(session: SessionRuntime, outcome: SessionOutcome) {
        synchronized(lock) { sessions.remove(session) }
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
            sessionStopHandler,
            sessionObserver,
            MonotonicRuntimeClockFactory,
            componentsFactory,
        )

    }
}
