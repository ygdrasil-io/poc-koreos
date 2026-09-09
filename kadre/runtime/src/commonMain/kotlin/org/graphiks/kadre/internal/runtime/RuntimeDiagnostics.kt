package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.graphiks.kadre.diagnostics.DiagnosticCounters
import org.graphiks.kadre.diagnostics.DiagnosticCounter
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreDiagnostics

internal class RuntimeDiagnostics(
    collectorAllocator: RuntimeEventCollectorAllocator,
    maxCollectorsPerFlow: Int,
) : KadreDiagnostics {
    private val mutableEvents = MutableSharedFlow<KadreDiagnostic>(extraBufferCapacity = 16)
    private val mutableCounters = MutableStateFlow(ZERO_COUNTERS)

    override val events: Flow<KadreDiagnostic> = mutableEvents.asSharedFlow().withEventCollectorAdmission(
        collectorAllocator.newGate(maxCollectorsPerFlow),
    )
    override val counters: StateFlow<DiagnosticCounters> = mutableCounters.asStateFlow()

    /** Records one runtime diagnostic without allowing an observer failure to affect the source. */
    internal fun report(diagnostic: KadreDiagnostic) {
        mutableEvents.tryEmit(diagnostic)
        mutableCounters.update { counters -> counters.record(diagnostic) }
    }

    private companion object {
        val ZERO_COUNTERS: DiagnosticCounters = DiagnosticCounters(
            eventLosses = 0,
            slowCollectors = 0,
            collectorRejections = 0,
            resourceLimitHits = 0,
            interactionExpirations = 0,
            permissionRevocations = 0,
            backendFallbacks = 0,
            platformFailures = 0,
            saturated = emptySet(),
        )
    }
}

private fun DiagnosticCounters.record(diagnostic: KadreDiagnostic): DiagnosticCounters = when (diagnostic) {
    is KadreDiagnostic.EventLoss -> increment(DiagnosticCounter.EventLosses, eventLosses, diagnostic.count) {
        copy(eventLosses = it)
    }

    is KadreDiagnostic.SlowConsumer -> increment(DiagnosticCounter.SlowCollectors, slowCollectors, diagnostic.droppedCount) {
        copy(slowCollectors = it)
    }

    is KadreDiagnostic.CollectorRejected -> increment(DiagnosticCounter.CollectorRejections, collectorRejections, 1) {
        copy(collectorRejections = it)
    }

    is KadreDiagnostic.ResourceLimitHit -> increment(DiagnosticCounter.ResourceLimitHits, resourceLimitHits, 1) {
        copy(resourceLimitHits = it)
    }

    is KadreDiagnostic.InteractionExpired -> increment(DiagnosticCounter.InteractionExpirations, interactionExpirations, 1) {
        copy(interactionExpirations = it)
    }

    is KadreDiagnostic.PermissionRevoked -> increment(DiagnosticCounter.PermissionRevocations, permissionRevocations, 1) {
        copy(permissionRevocations = it)
    }

    is KadreDiagnostic.BackendFallback -> increment(DiagnosticCounter.BackendFallbacks, backendFallbacks, 1) {
        copy(backendFallbacks = it)
    }

    is KadreDiagnostic.PlatformFailureObserved -> increment(DiagnosticCounter.PlatformFailures, platformFailures, 1) {
        copy(platformFailures = it)
    }

    is KadreDiagnostic.CapabilityChanged,
    is KadreDiagnostic.SessionFailure,
    is KadreDiagnostic.CleanupFailure,
    -> this
}

private inline fun DiagnosticCounters.increment(
    counter: DiagnosticCounter,
    current: Long,
    increment: Long,
    update: (Long) -> DiagnosticCounters,
): DiagnosticCounters {
    val next = if (Long.MAX_VALUE - current < increment) Long.MAX_VALUE else current + increment
    return update(next).let { updated ->
        if (next == Long.MAX_VALUE) updated.copy(saturated = updated.saturated + counter) else updated
    }
}
