package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.capture.CaptureManager
import org.graphiks.kadre.capture.CaptureManagerRevision
import org.graphiks.kadre.capture.CaptureManagerState
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSession
import org.graphiks.kadre.capture.CaptureSource
import org.graphiks.kadre.capture.CaptureSourceId
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult

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
            is KadreResult.Success -> acceptSnapshot(requested.value)
            is KadreResult.Failure -> acceptFailure(requested.reason)
        }
    }

    override suspend fun refreshSources(): KadreResult<CaptureManagerState> {
        if (isClosed()) return closedFailure()
        return when (val requested = port.refreshSources()) {
            is KadreResult.Success -> acceptSnapshot(requested.value)
            is KadreResult.Failure -> acceptFailure(requested.reason)
        }
    }

    /** Session opening is deliberately held back until RuntimeCaptureSession owns native teardown. */
    override suspend fun open(request: CaptureRequest): KadreResult<CaptureSession> = lock.withLock {
        if (closed) {
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSource))
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
            is KadreResult.Failure -> acceptFailure(observation.reason)
        }
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

    private fun acceptFailure(failure: KadreFailure): KadreResult<CaptureManagerState> = lock.withLock {
        if (closed) return@withLock closedFailure()
        val unavailable = acceptedSnapshot.copy(sources = CapturePortSources.Unavailable(failure))
        if (unavailable != acceptedSnapshot) {
            val nextRevision = nextRevision(mutableState.value.revision)
            mutableState.value = projectSnapshot(unavailable, nextRevision)
            acceptedSnapshot = unavailable
        }
        KadreResult.Failure(failure)
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
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSource))
}
