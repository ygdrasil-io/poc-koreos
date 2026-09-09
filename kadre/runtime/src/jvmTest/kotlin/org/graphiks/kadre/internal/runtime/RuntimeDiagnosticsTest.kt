package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreSubsystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.nanoseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeDiagnosticsTest {
    @Test
    fun reportPublishesTheDiagnosticAndUpdatesItsCounter() = runTest {
        val diagnostics = RuntimeDiagnostics(
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 2,
        )
        val diagnostic = KadreDiagnostic.EventLoss(
            count = 3,
            resource = KadreResourceKind.RawInputAccess,
            subsystem = KadreSubsystem.Input,
            stamp = EventStamp(SessionSequence(1), SessionInstant(1.nanoseconds), null),
        )
        val observed = async { diagnostics.events.first() }
        runCurrent()

        diagnostics.report(diagnostic)

        assertEquals(diagnostic, observed.await())
        assertEquals(3, diagnostics.counters.value.eventLosses)
    }
}
