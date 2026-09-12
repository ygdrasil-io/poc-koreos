package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchContext
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.capture.CaptureManager
import org.graphiks.kadre.diagnostics.KadreDiagnostics
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.display.DisplayManager
import org.graphiks.kadre.input.DeviceManager
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.window.WindowManager
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalCoroutinesApi::class)
internal class SessionRuntime(
    override val id: SessionId,
    private val parentScope: CoroutineScope,
    private val applicationFactory: KadreApplicationFactory,
    private val policy: KadrePolicy,
    initialLifecycleState: LifecycleState,
    initialLifecycleCapabilities: LifecycleCapabilities,
    private val clock: RuntimeClock,
    private val failureReporter: (Throwable) -> Unit,
    private val onRevoking: (SessionRuntime) -> Unit,
    private val onStopping: (SessionRuntime) -> KadreFailure.PlatformFailure?,
    private val onTerminated: (SessionRuntime, SessionOutcome) -> Unit,
    componentsFactory: RuntimeSessionComponentsFactory,
) : KadreSession {
    private val lock = RuntimeLock()
    private val parentJob = checkNotNull(parentScope.coroutineContext[Job])
    private var parentCancellationEnabled = false
    private var parentCancellationPending = false
    private val parentCancellationHandle: DisposableHandle = parentJob.invokeOnCompletion(
        onCancelling = true,
        invokeImmediately = true,
    ) { cause ->
        if (cause != null) parentCancellationObserved()
    }
    private val rootJob = SupervisorJob(parentJob)
    private val baseContext = parentScope.coroutineContext.minusKey(Job)
    private val rootScope = CoroutineScope(baseContext + rootJob)
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Starting)
    private val terminal = CompletableDeferred<SessionOutcome>()
    private val sequenceLock = RuntimeLock()
    private var nextSequence = 0L
    private val marker = SessionMarker(id)
    private val eventCollectorAllocator = RuntimeEventCollectorAllocator(
        policy.resources.maxEventCollectorsPerSession,
    )
    private val runtimeLifecycle = RuntimeLifecycle(
        initialLifecycleState,
        initialLifecycleCapabilities,
        ::nextStamp,
        eventCollectorAllocator,
        policy.resources.maxEventCollectorsPerFlow,
    )
    private val runtimeDiagnostics = RuntimeDiagnostics(
        eventCollectorAllocator,
        policy.resources.maxEventCollectorsPerFlow,
    )
    private val runtimeComponents = try {
        val components = componentsFactory.create(id, rootScope)
        try {
            components.installSessionConfiguration(
                policy.window,
                policy.input,
                ::nextStamp,
                ::eventDeliveryFailed,
                eventCollectorAllocator,
                policy.resources.maxEventCollectorsPerFlow,
                rootScope,
                runtimeDiagnostics::report,
                components.rawInputPort,
            )
        } catch (cause: Throwable) {
            runCatching { components.close() }
            throw cause
        }
        components
    } catch (cause: Throwable) {
        rootJob.cancel()
        parentCancellationHandle.dispose()
        throw cause
    }
    private val runtimeDisplayManager = runtimeComponents.displayPort?.let { port ->
        RuntimeDisplayManager(
            port = port,
            eventStampSource = ::nextStamp,
            collectorAllocator = eventCollectorAllocator,
            maxCollectorsPerFlow = policy.resources.maxEventCollectorsPerFlow,
        )
    }
    private val runtimeDisplays: DisplayManager = runtimeDisplayManager ?: UnsupportedDisplayManager(
        eventCollectorAllocator,
        policy.resources.maxEventCollectorsPerFlow,
    )
    private val runtimeWindows = runtimeComponents.windows.also {
        runtimeDisplayManager?.let(runtimeComponents::installExclusiveDisplayTargetResolver)
    }
    private val runtimeDevices = UnsupportedDeviceManager(
        eventCollectorAllocator,
        policy.resources.maxEventCollectorsPerFlow,
    )
    private val runtimeCaptureManager = runtimeComponents.capturePort?.let { port ->
        RuntimeCaptureManager(port, policy.resources.maxConcurrentCaptureSessions)
    }
    private val runtimeCapture: CaptureManager = runtimeCaptureManager ?: UnsupportedCaptureManager()
    private val mutablePrimarySurface = MutableStateFlow(runtimeComponents.primarySurface)

    private var startupJob: Job? = null
    private var applicationJob: Deferred<Unit>? = null
    private var selectedOutcome: SessionOutcome? = null
    private var targetRevoked = false
    private var stopHandlerStarted = false
    private var finished = false

    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    fun start() {
        val cancellationPending = lock.withLock {
            if (finished) {
                return
            } else {
                parentCancellationEnabled = true
                parentCancellationPending || !parentJob.isActive
            }
        }
        if (cancellationPending) {
            parentCancelled()
            return
        }

        val startup = rootScope.launch(start = CoroutineStart.LAZY) {
            val application = try {
                applicationFactory.create(
                    KadreLaunchContext(id, KadreLaunchReason.InitialHostAttachment, null, null),
                )
            } catch (cause: Throwable) {
                handleApplicationThrowable(cause)
                return@launch
            }

            lateinit var scope: ApplicationScope
            val runner = rootScope.async(context = marker, start = CoroutineStart.LAZY) {
                with(application) { scope.run() }
            }
            scope = ApplicationScope(baseContext + runner + marker)
            runner.invokeOnCompletion(::applicationCompleted)

            val shouldStart = lock.withLock {
                if (finished || selectedOutcome != null || mutableState.value != SessionState.Starting) {
                    false
                } else {
                    applicationJob = runner
                    mutableState.value = SessionState.Running
                    true
                }
            }
            if (shouldStart) runner.start() else runner.cancel()
        }
        lock.withLock {
            if (finished || selectedOutcome != null) {
                startup.cancel()
            } else {
                startupJob = startup
                startup.start()
            }
        }
    }

    override fun close() = requestHostStop()

    override fun requestStop() = requestHostStop()

    override suspend fun awaitTermination(): SessionOutcome {
        check(currentCoroutineContext()[SessionMarker] != marker) {
            "a session child cannot await its own termination"
        }
        return terminal.await()
    }

    private fun requestApplicationStop() {
        requestTermination(SessionOutcome.Stopped(SessionStopReason.ApplicationRequested))
    }

    fun updateLifecycle(state: LifecycleState) {
        if (!isFinished()) runtimeLifecycle.updateState(state)
    }

    fun updateLifecycleCapabilities(capabilities: LifecycleCapabilities) {
        if (!isFinished()) runtimeLifecycle.updateCapabilities(capabilities)
    }

    fun emitMemoryPressure(level: MemoryPressureLevel) {
        if (!isFinished()) runtimeLifecycle.emitMemoryPressure(level)
    }

    fun hostDetached() {
        requestTermination(SessionOutcome.Stopped(SessionStopReason.HostDetached))
    }

    fun hostDetachedImmediately() {
        val final = lock.withLock {
            if (finished) return
            selectedOutcome = selectOutcome(
                selectedOutcome,
                SessionOutcome.Stopped(SessionStopReason.HostDetached),
            )
            mutableState.value = SessionState.Stopping
            revokeTargetResources()
            startupJob?.cancel()
            applicationJob?.cancel()
            checkNotNull(selectedOutcome)
        }
        finish(final)
    }

    fun hostFailed(failure: KadreFailure.PlatformFailure) {
        requestTermination(SessionOutcome.Failed(failure))
    }

    private fun eventDeliveryFailed(failure: KadreFailure) {
        failureReporter(KadreException(failure))
        requestTermination(SessionOutcome.Failed(failure))
    }

    private fun requestHostStop() {
        requestTermination(SessionOutcome.Stopped(SessionStopReason.HostRequested))
    }

    private fun parentCancelled() {
        val outcome = SessionOutcome.Stopped(SessionStopReason.ParentCancelled)
        lock.withLock {
            if (finished) return
            selectedOutcome = selectOutcome(selectedOutcome, outcome)
            mutableState.value = SessionState.Stopping
            revokeTargetResources()
            startupJob?.cancel()
            applicationJob?.cancel()
        }
        finish(outcome)
    }

    private fun parentCancellationObserved() {
        val shouldTerminate = lock.withLock {
            if (parentCancellationEnabled) {
                true
            } else {
                parentCancellationPending = true
                false
            }
        }
        if (shouldTerminate) parentCancelled()
    }

    private fun applicationCompleted(cause: Throwable?) {
        when {
            cause == null -> requestTermination(SessionOutcome.Completed)
            cause is CancellationException -> {
                val alreadySelected = lock.withLock { selectedOutcome }
                if (alreadySelected == null) {
                    val reason = if (parentJob.isActive) {
                        SessionStopReason.ApplicationCancelled
                    } else {
                        SessionStopReason.ParentCancelled
                    }
                    requestTermination(SessionOutcome.Stopped(reason))
                }
            }
            else -> handleApplicationThrowable(cause)
        }
    }

    private fun handleApplicationThrowable(cause: Throwable) {
        if (cause is CancellationException) {
            val selected = lock.withLock { selectedOutcome }
            if (selected == null) {
                requestTermination(SessionOutcome.Stopped(SessionStopReason.ApplicationCancelled))
            }
            return
        }
        failureReporter(cause)
        requestTermination(SessionOutcome.Failed(KadreFailure.ApplicationFailure))
    }

    private fun requestTermination(proposed: SessionOutcome) {
        lock.withLock {
            if (finished) return
            selectedOutcome = selectOutcome(selectedOutcome, proposed)
            mutableState.value = SessionState.Stopping
            revokeTargetResources()
            startupJob?.cancel()
            applicationJob?.cancel()
            if (stopHandlerStarted) return
            stopHandlerStarted = true
        }

        // Run host shutdown before publishing a terminal outcome: a native stop failure must
        // still be able to promote an otherwise successful stop to Failed(PlatformFailure).
        val stopFailure = onStopping(this)
        val termination = lock.withLock {
            if (finished) return@withLock null
            if (stopFailure != null) {
                selectedOutcome = selectOutcome(selectedOutcome, SessionOutcome.Failed(stopFailure))
            }
            checkNotNull(selectedOutcome) to (applicationJob == null)
        }
        if (termination == null) return

        if (termination.second) {
            finish(termination.first)
            return
        }

        rootScope.launch {
            val application = lock.withLock { applicationJob }
            val completed = application == null || withTimeoutOrNull(policy.execution.shutdownTimeout) {
                application.join()
                true
            } == true
            val selected = checkNotNull(lock.withLock { selectedOutcome })
            val final = if (!completed && selected !is SessionOutcome.Failed) {
                SessionOutcome.Failed(KadreFailure.ShutdownTimedOut(policy.execution.shutdownTimeout))
            } else {
                selected
            }
            finish(final)
        }
    }

    private fun finish(outcome: SessionOutcome) {
        val final = lock.withLock {
            if (finished) return
            finished = true
            selectedOutcome = selectOutcome(selectedOutcome, outcome)
            val final = checkNotNull(selectedOutcome)
            final
        }
        parentCancellationHandle.dispose()
        closeRuntimeComponents()
        mutableState.value = SessionState.Terminated(final)
        terminal.complete(final)
        onTerminated(this, final)
        rootJob.cancel()
    }

    fun disposeUnstarted() {
        val shouldDispose = lock.withLock {
            if (finished) {
                false
            } else {
                check(startupJob == null) { "started sessions must terminate normally" }
                check(applicationJob == null) { "started sessions must terminate normally" }
                finished = true
                true
            }
        }
        if (!shouldDispose) return

        parentCancellationHandle.dispose()
        closeRuntimeComponents()
        rootJob.cancel()
    }

    private fun closeRuntimeComponents() {
        runCatching { runtimeCaptureManager?.close() }
            .exceptionOrNull()
            ?.let(failureReporter)
        runCatching { (runtimeDisplays as? AutoCloseable)?.close() }
            .exceptionOrNull()
            ?.let(failureReporter)
        runCatching { runtimeComponents.close() }
            .exceptionOrNull()
            ?.let(failureReporter)
    }

    /** Must be called with [lock] held, before cancelling either session job. */
    private fun revokeTargetResources() {
        if (targetRevoked) return
        targetRevoked = true
        runCatching { onRevoking(this) }
            .exceptionOrNull()
            ?.let(failureReporter)
    }

    private fun isFinished(): Boolean = lock.withLock { finished }

    private fun nextStamp(): EventStamp {
        val sequence = sequenceLock.withLock {
            check(nextSequence < Long.MAX_VALUE) { "session sequence overflow" }
            nextSequence++
        }
        check(sequence >= 0L) { "session sequence overflow" }
        return EventStamp(
            SessionSequence(sequence),
            SessionInstant(clock.elapsedNow()),
            null,
        )
    }

    private fun selectOutcome(current: SessionOutcome?, proposed: SessionOutcome): SessionOutcome = when {
        current == null -> proposed
        current is SessionOutcome.Failed -> current
        proposed is SessionOutcome.Failed -> proposed
        else -> current
    }

    private inner class ApplicationScope(
        override val coroutineContext: CoroutineContext,
    ) : KadreScope {
        override val sessionId: SessionId get() = id
        override val policy: KadrePolicy get() = this@SessionRuntime.policy
        override val lifecycle: RuntimeLifecycle get() = runtimeLifecycle
        override val primarySurface: StateFlow<HostSurface?> = mutablePrimarySurface.asStateFlow()
        override val windows: WindowManager get() = runtimeWindows
        override val displays: DisplayManager get() = runtimeDisplays
        override val devices: DeviceManager get() = runtimeDevices
        override val capture: CaptureManager get() = runtimeCapture
        override val diagnostics: KadreDiagnostics get() = runtimeDiagnostics

        override fun requestStop() = requestApplicationStop()
    }
}

private class SessionMarker(
    val sessionId: SessionId,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SessionMarker>
}
