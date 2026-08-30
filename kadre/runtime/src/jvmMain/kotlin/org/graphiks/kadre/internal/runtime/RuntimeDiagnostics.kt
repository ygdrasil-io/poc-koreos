package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.diagnostics.DiagnosticCounters
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
