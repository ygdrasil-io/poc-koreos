package org.graphiks.kadre.internal.runtime

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
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.PermissionState

/** Session-owned projection of detached capture-control-plane observations. */
internal class RuntimeCaptureManager(
    private val port: CapturePort,
) : CaptureManager, AutoCloseable {
    private val lock = RuntimeLock()
    private val sourceIdsByKey = linkedMapOf<CapturePortSourceKey, CaptureSourceId>()
    private var closed = false
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

    /** Session opening is deliberately held back until RuntimeCaptureSession owns native teardown. */
    override suspend fun open(request: CaptureRequest): KadreResult<CaptureSession> = lock.withLock {
        if (closed) {
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
        } else {
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
    }

    override fun close() {
        val toClose = lock.withLock {
            if (closed) return
            closed = true
            observation.also { observation = null }
        }
        toClose?.close()
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

    private fun closedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
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
