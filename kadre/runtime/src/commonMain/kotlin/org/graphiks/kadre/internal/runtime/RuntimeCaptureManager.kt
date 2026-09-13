package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.capture.CaptureManager
import org.graphiks.kadre.capture.CaptureManagerRevision
import org.graphiks.kadre.capture.CaptureManagerState
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSession
import org.graphiks.kadre.capture.CaptureSource
import org.graphiks.kadre.capture.CaptureSourceId
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.capture.CaptureTarget
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.PermissionState
import kotlin.time.Duration.Companion.ZERO

/** Session-owned projection of detached capture-control-plane observations. */
internal class RuntimeCaptureManager(
    private val port: CapturePort,
    private val maxConcurrentSessions: Int = 1,
    private val eventStampSource: () -> EventStamp = RuntimeCaptureStamps::next,
) : CaptureManager, AutoCloseable {
    init {
        require(maxConcurrentSessions > 0) { "maxConcurrentSessions must be positive" }
    }

    private val lock = RuntimeLock()
    private val sourceIdsByKey = linkedMapOf<CapturePortSourceKey, CaptureSourceId>()
    private var closed = false
    private var occupiedSessionSlots = 0
    private val sessions = mutableSetOf<RuntimeCaptureSession>()
    private var acceptedSnapshot: CapturePortSnapshot = port.initialSnapshot
    private var observation: AutoCloseable? = null
    private val mutableState = MutableStateFlow(projectSnapshot(acceptedSnapshot, CaptureManagerRevision(0)))

    override val state: StateFlow<CaptureManagerState> = mutableState.asStateFlow()

    init {
        observation = port.installObserver(::acceptObservation)
    }

    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CaptureManagerState> {
        if (isClosed()) return closedFailure()
        return when (val requested = port.requestPermission(scope)) {
            is KadreResult.Success -> acceptPermissionSnapshot(scope, requested.value)
            is KadreResult.Failure -> acceptPermissionFailure(scope, requested.reason)
        }
    }

    override suspend fun refreshSources(): KadreResult<CaptureManagerState> {
        if (isClosed()) return closedFailure()
        return when (val requested = port.refreshSources()) {
            is KadreResult.Success -> acceptSnapshot(requested.value)
            is KadreResult.Failure -> acceptRefreshFailure(requested.reason)
        }
    }

    override suspend fun open(request: CaptureRequest): KadreResult<CaptureSession> {
        val admission = lock.withLock {
            if (closed) return@withLock CaptureAdmission.Failure(closedFailure())
            resolveTarget(request).also { result ->
                if (result is CaptureAdmission.ReservedTarget) {
                    if (occupiedSessionSlots >= maxConcurrentSessions) {
                        return@withLock CaptureAdmission.Failure(
                            KadreResult.Failure(
                                KadreFailure.ResourceLimitExceeded(
                                    KadreResourceKind.CaptureSession,
                                    maxConcurrentSessions.toLong(),
                                ),
                            ),
                        )
                    }
                    occupiedSessionSlots += 1
                }
            }
        }
        val target = when (admission) {
            is CaptureAdmission.Failure -> return admission.result
            is CaptureAdmission.ReservedTarget -> admission
        }

        val reservation = try {
            when (val result = port.reserve(target.target, request)) {
                is KadreResult.Success -> result.value
                is KadreResult.Failure -> {
                    releaseSessionSlot()
                    return result
                }
            }
        } catch (cause: Throwable) {
            releaseSessionSlot()
            throw cause
        }

        var handedOff: RuntimeCaptureSession? = null
        try {
            currentCoroutineContext().ensureActive()
            val source = lock.withLock { sourceForReservation(target.source, reservation.source) }
            val session = RuntimeCaptureSession(source, reservation, eventStampSource, ::onSessionTerminated)
            val accepted = lock.withLock {
                if (closed) {
                    false
                } else {
                    sessions += session
                    true
                }
            }
            if (!accepted) {
                session.stopFromParent()
                releaseSessionSlot()
                return closedFailure()
            }
            handedOff = session
            currentCoroutineContext().ensureActive()
            return KadreResult.Success(session)
        } catch (cause: Throwable) {
            val session = handedOff
            if (session == null) {
                reservation.close()
                releaseSessionSlot()
            } else {
                session.close()
            }
            throw cause
        }
    }

    override fun close() {
        val toClose = lock.withLock {
            if (closed) return
            closed = true
            val registered = observation.also { observation = null }
            registered to sessions.toList()
        }
        toClose.first?.close()
        toClose.second.forEach(RuntimeCaptureSession::stopFromParent)
    }

    private fun acceptObservation(observation: KadreResult<CapturePortSnapshot>) {
        when (observation) {
            is KadreResult.Success -> acceptSnapshot(observation.value)
            is KadreResult.Failure -> acceptRefreshFailure(observation.reason)
        }
    }

    private fun acceptPermissionSnapshot(
        scope: CapturePermissionScope,
        snapshot: CapturePortSnapshot,
    ): KadreResult<CaptureManagerState> {
        check(snapshot.permissions.forScope(scope).isResolvedPermissionResult()) {
            "successful capture permission requests must resolve their targeted permission"
        }
        return acceptSnapshot(snapshot)
    }

    private fun acceptSnapshot(snapshot: CapturePortSnapshot): KadreResult<CaptureManagerState> = lock.withLock {
        if (closed) return@withLock closedFailure()
        if (snapshot != acceptedSnapshot) {
            val nextRevision = nextRevision(mutableState.value.revision)
            mutableState.value = projectSnapshot(snapshot, nextRevision)
            acceptedSnapshot = snapshot
        }
        KadreResult.Success(mutableState.value)
    }

    private fun acceptPermissionFailure(
        scope: CapturePermissionScope,
        failure: KadreFailure,
    ): KadreResult<CaptureManagerState> = lock.withLock {
        if (closed) return@withLock closedFailure()
        if (failure.isPersistentPermissionFailure()) {
            val updatedPermissions = acceptedSnapshot.permissions.withUnavailable(scope, failure)
            publishSnapshot(acceptedSnapshot.copy(permissions = updatedPermissions))
        }
        KadreResult.Failure(failure)
    }

    private fun acceptRefreshFailure(failure: KadreFailure): KadreResult<CaptureManagerState> = lock.withLock {
        if (closed) return@withLock closedFailure()
        val replacement = when {
            failure is KadreFailure.PermissionDenied && failure.permission.isCapturePermission() -> {
                CapturePortSources.PermissionRequired(setOf(failure.permission))
            }

            failure.isPersistentInventoryFailure() -> CapturePortSources.Unavailable(failure)
            else -> null
        }
        if (replacement != null) publishSnapshot(acceptedSnapshot.copy(sources = replacement))
        KadreResult.Failure(failure)
    }

    private fun publishSnapshot(snapshot: CapturePortSnapshot) {
        if (snapshot != acceptedSnapshot) {
            val nextRevision = nextRevision(mutableState.value.revision)
            mutableState.value = projectSnapshot(snapshot, nextRevision)
            acceptedSnapshot = snapshot
        }
    }

    private fun projectSnapshot(
        snapshot: CapturePortSnapshot,
        revision: CaptureManagerRevision,
    ): CaptureManagerState = CaptureManagerState(
        permissions = snapshot.permissions,
        capabilities = snapshot.capabilities,
        sources = when (val sources = snapshot.sources) {
            is CapturePortSources.Enumerated -> CaptureSources.Enumerated(
                projectSources(sources.values, revision),
            )

            CapturePortSources.HostPickerOnly -> {
                sourceIdsByKey.clear()
                CaptureSources.HostPickerOnly
            }

            is CapturePortSources.PermissionRequired -> {
                sourceIdsByKey.clear()
                CaptureSources.PermissionRequired(sources.required)
            }

            is CapturePortSources.Unavailable -> {
                sourceIdsByKey.clear()
                CaptureSources.Unavailable(sources.failure)
            }
        },
        revision = revision,
    )

    private fun projectSources(
        values: List<CapturePortSource>,
        revision: CaptureManagerRevision,
    ): List<CaptureSource> {
        val incomingKeys = values.map(CapturePortSource::key).toSet()
        sourceIdsByKey.keys.retainAll(incomingKeys)
        return values.map { source ->
            CaptureSource(
                id = sourceIdsByKey.getOrPut(source.key, RuntimeProcessIds::nextCaptureSourceId),
                kind = source.kind,
                name = source.name,
                size = source.size,
                managerRevision = revision,
            )
        }
    }

    private fun nextRevision(current: CaptureManagerRevision): CaptureManagerRevision {
        check(current.value < Long.MAX_VALUE) { "capture manager revision space exhausted" }
        return CaptureManagerRevision(current.value + 1L)
    }

    private fun isClosed(): Boolean = lock.withLock { closed }

    private fun resolveTarget(request: CaptureRequest): CaptureAdmission = when (val target = request.target) {
        CaptureTarget.HostChoice -> hostChoiceAdmission()
        is CaptureTarget.Source -> sourceAdmission(target)
        is CaptureTarget.Surface -> capabilityAdmission(acceptedSnapshot.capabilities.surface, CapturePortTarget.Surface(target.id))
    }

    private fun sourceAdmission(target: CaptureTarget.Source): CaptureAdmission {
        val current = mutableState.value
        if (target.managerRevision != current.revision) {
            return CaptureAdmission.Failure(
                KadreResult.Failure(
                    KadreFailure.StaleRevision(current.revision.value, target.managerRevision.value),
                ),
            )
        }
        val source = (current.sources as? CaptureSources.Enumerated)
            ?.values
            ?.singleOrNull { it.id == target.id }
            ?: return CaptureAdmission.Failure(KadreResult.Failure(KadreFailure.InvalidRequest("request.target")))
        val portSource = (acceptedSnapshot.sources as? CapturePortSources.Enumerated)
            ?.values
            ?.singleOrNull { it.key == sourceIdsByKey.entries.singleOrNull { it.value == source.id }?.key }
            ?: return CaptureAdmission.Failure(KadreResult.Failure(KadreFailure.InvalidRequest("request.target")))
        return CaptureAdmission.ReservedTarget(CapturePortTarget.Source(portSource.key), source)
    }

    private fun hostChoiceAdmission(): CaptureAdmission = when (val availability = acceptedSnapshot.capabilities.hostPicker) {
        FeatureAvailability.Available -> CaptureAdmission.ReservedTarget(CapturePortTarget.HostChoice, null)
        FeatureAvailability.Unsupported -> CaptureAdmission.Failure(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
        )

        is FeatureAvailability.RequiresPermission -> CaptureAdmission.Failure(
            KadreResult.Failure(KadreFailure.PermissionDenied(availability.permission)),
        )

        is FeatureAvailability.RequiresInteraction -> CaptureAdmission.Failure(
            KadreResult.Failure(KadreFailure.InteractionRequired(org.graphiks.kadre.diagnostics.InteractionFailureReason.Missing)),
        )

        is FeatureAvailability.Unavailable -> CaptureAdmission.Failure(KadreResult.Failure(availability.failure))
    }

    private fun capabilityAdmission(
        capability: Capability<*>,
        target: CapturePortTarget,
    ): CaptureAdmission = when (capability) {
        is Capability.Unsupported -> CaptureAdmission.Failure(KadreResult.Failure(capability.failure))
        is Capability.Supported -> when (val availability = capability.availability) {
            FeatureAvailability.Available -> CaptureAdmission.ReservedTarget(target, null)
            FeatureAvailability.Unsupported -> error("supported capability cannot be unsupported")
            is FeatureAvailability.RequiresPermission -> CaptureAdmission.Failure(
                KadreResult.Failure(KadreFailure.PermissionDenied(availability.permission)),
            )

            is FeatureAvailability.RequiresInteraction -> CaptureAdmission.Failure(
                KadreResult.Failure(
                    KadreFailure.InteractionRequired(org.graphiks.kadre.diagnostics.InteractionFailureReason.Missing),
                ),
            )

            is FeatureAvailability.Unavailable -> CaptureAdmission.Failure(KadreResult.Failure(availability.failure))
        }
    }

    private fun sourceForReservation(
        selected: CaptureSource?,
        reserved: CapturePortSource,
    ): CaptureSource = selected ?: CaptureSource(
        id = sourceIdsByKey.getOrPut(reserved.key, RuntimeProcessIds::nextCaptureSourceId),
        kind = reserved.kind,
        name = reserved.name,
        size = reserved.size,
        managerRevision = mutableState.value.revision,
    )

    private fun onSessionTerminated(session: RuntimeCaptureSession) {
        lock.withLock {
            if (sessions.remove(session)) {
                check(occupiedSessionSlots > 0) { "capture session slot accounting underflow" }
                occupiedSessionSlots -= 1
            }
        }
    }

    private fun releaseSessionSlot() {
        lock.withLock {
            check(occupiedSessionSlots > 0) { "capture session slot accounting underflow" }
            occupiedSessionSlots -= 1
        }
    }

    private fun closedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
}

private sealed interface CaptureAdmission {
    public data class ReservedTarget(
        val target: CapturePortTarget,
        val source: CaptureSource?,
    ) : CaptureAdmission

    public data class Failure(val result: KadreResult.Failure) : CaptureAdmission
}

private object RuntimeCaptureStamps {
    private val lock = RuntimeLock()
    private var nextSequence = 0L

    fun next(): EventStamp = lock.withLock {
        check(nextSequence < Long.MAX_VALUE) { "capture event sequence space exhausted" }
        EventStamp(SessionSequence(nextSequence++), SessionInstant(ZERO), null)
    }
}

private fun CapturePermissionState.forScope(scope: CapturePermissionScope): PermissionState = when (scope) {
    CapturePermissionScope.Screen -> screen
    CapturePermissionScope.Window -> window
}

private fun CapturePermissionState.withUnavailable(
    scope: CapturePermissionScope,
    failure: KadreFailure,
): CapturePermissionState = when (scope) {
    CapturePermissionScope.Screen -> copy(screen = PermissionState.Unavailable(failure))
    CapturePermissionScope.Window -> copy(window = PermissionState.Unavailable(failure))
}

private fun PermissionState.isResolvedPermissionResult(): Boolean = when (this) {
    PermissionState.Granted, PermissionState.Restricted, is PermissionState.Denied -> true
    PermissionState.NotDetermined, is PermissionState.Unavailable -> false
}

private fun KadrePermission.isCapturePermission(): Boolean =
    this == KadrePermission.CaptureScreen || this == KadrePermission.CaptureWindow

private fun KadreFailure.isPersistentPermissionFailure(): Boolean = when (this) {
    is KadreFailure.Unsupported -> operation == KadreOperation.CapturePermission
    is KadreFailure.Closed -> resource == KadreResourceKind.Host
    is KadreFailure.TemporarilyUnavailable -> !retryable
    is KadreFailure.PlatformFailure -> true
    else -> false
}

private fun KadreFailure.isPersistentInventoryFailure(): Boolean = when (this) {
    is KadreFailure.Unsupported -> operation == KadreOperation.CaptureRefreshSources
    is KadreFailure.Closed -> resource == KadreResourceKind.Host
    is KadreFailure.ResourceLimitExceeded ->
        resource == KadreResourceKind.CaptureSource || resource == KadreResourceKind.RetainedPayload
    is KadreFailure.TemporarilyUnavailable -> !retryable
    is KadreFailure.PlatformFailure -> true
    else -> false
}
