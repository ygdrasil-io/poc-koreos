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

@OptIn(ExperimentalKadreApi::class)
public class RuntimeHostController private constructor(
    override val platform: KadrePlatform,
    initialLifecycleState: LifecycleState,
    initialLifecycleCapabilities: LifecycleCapabilities,
    private val failureReporter: RuntimeFailureReporter,
    private val clockFactory: RuntimeClockFactory,
) : KadreHost {
    public constructor(
        platform: KadrePlatform,
        initialLifecycleState: LifecycleState = DEFAULT_LIFECYCLE_STATE,
        initialLifecycleCapabilities: LifecycleCapabilities = DEFAULT_LIFECYCLE_CAPABILITIES,
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
    ) : this(
        platform,
        initialLifecycleState,
        initialLifecycleCapabilities,
        failureReporter,
        MonotonicRuntimeClockFactory,
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

        val session = synchronized(lock) {
            if (detached) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
            if (!parentJob.isActive) return KadreResult.Failure(KadreFailure.ParentScopeCancelled)

            SessionRuntime(
                id = RuntimeProcessIds.nextSessionId(),
                parentScope = parentScope,
                applicationFactory = applicationFactory,
                policy = policy,
                initialLifecycleState = lifecycleState,
                initialLifecycleCapabilities = lifecycleCapabilities,
                clock = clockFactory.start(),
                failureReporter = ::reportFailure,
                onTerminated = ::sessionTerminated,
            ).also(sessions::add)
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

    private fun reportFailure(cause: Throwable) {
        runCatching { failureReporter.report(cause) }
    }

    private fun sessionTerminated(session: SessionRuntime) {
        synchronized(lock) { sessions.remove(session) }
    }

    internal companion object {
        private val DEFAULT_LIFECYCLE_STATE: LifecycleState = LifecycleState(
            AttachmentState.Attached,
            VisibilityState.Foreground,
            ActivationState.Active,
        )
        private val DEFAULT_LIFECYCLE_CAPABILITIES: LifecycleCapabilities =
            LifecycleCapabilities(FeatureAvailability.Unsupported)

        fun withClock(
            platform: KadrePlatform,
            clockFactory: RuntimeClockFactory,
            initialLifecycleState: LifecycleState = DEFAULT_LIFECYCLE_STATE,
            initialLifecycleCapabilities: LifecycleCapabilities = DEFAULT_LIFECYCLE_CAPABILITIES,
            failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        ): RuntimeHostController = RuntimeHostController(
            platform,
            initialLifecycleState,
            initialLifecycleCapabilities,
            failureReporter,
            clockFactory,
        )
    }
}
