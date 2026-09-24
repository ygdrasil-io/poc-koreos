package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebSurfaceMetricsTest {
    @Test
    fun resizeObservationPublishesMetricsAndOneEvent() = runTest {
        val scopeReady = CompletableDeferred<KadreScope>()
        val port = RecordingWebHostPort(WebSurfaceMetrics(320.0, 180.0, 2.0))
        val session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(port, WebHostRegistry()).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                KadrePolicies.Default,
            ),
        ).value
        testScheduler.runCurrent()

        val surface = scopeReady.await().primarySurface.value ?: error("a web session exposes a primary surface")
        assertEquals(LogicalSize(320.0, 180.0), surface.state.value.logicalSize)
        assertEquals(PhysicalSize(640, 360), surface.state.value.physicalSize)
        assertEquals(2.0, surface.state.value.scaleFactor)

        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        val revisionBeforeResize = surface.state.value.revision.value
        port.deliverMetrics(WebSurfaceMetrics(400.0, 200.0, 2.0))
        testScheduler.runCurrent()

        assertEquals(LogicalSize(400.0, 200.0), surface.state.value.logicalSize)
        assertEquals(PhysicalSize(800, 400), surface.state.value.physicalSize)
        assertEquals(revisionBeforeResize + 1L, surface.state.value.revision.value)
        assertEquals(1, events.filterIsInstance<SurfaceEvent.MetricsChanged>().size)
        assertEquals(surface.state.value, events.filterIsInstance<SurfaceEvent.MetricsChanged>().single().state)
        assertEquals(
            surface.state.value.revision,
            events.filterIsInstance<SurfaceEvent.MetricsChanged>().single().stateRevision,
            "the event carries the revision of the state it announces",
        )

        collector.cancel()
        session.requestStop()
        testScheduler.runCurrent()
    }

    @Test
    fun identicalMetricsAreDeduplicated() = runTest {
        val scopeReady = CompletableDeferred<KadreScope>()
        val port = RecordingWebHostPort(WebSurfaceMetrics(100.0, 100.0, 1.0))
        val session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(port, WebHostRegistry()).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                KadrePolicies.Default,
            ),
        ).value
        testScheduler.runCurrent()

        val surface = scopeReady.await().primarySurface.value ?: error("a web session exposes a primary surface")
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        val revisionBeforeDuplicate = surface.state.value.revision.value
        port.deliverMetrics(WebSurfaceMetrics(100.0, 100.0, 1.0))
        testScheduler.runCurrent()

        assertEquals(revisionBeforeDuplicate, surface.state.value.revision.value)
        assertEquals(emptyList(), events.filterIsInstance<SurfaceEvent.MetricsChanged>())

        collector.cancel()
        session.requestStop()
        testScheduler.runCurrent()
    }
}
