package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionDispatcherTest {
    private class ScriptedGateway(
        private val gate: CompletableDeferred<KadreResult<WindowRequest>> = CompletableDeferred(),
    ) : TourGateway {
        var calls = 0
        override fun lifecycleSummary(): Flow<String> = flowOf("Session")
        override fun observeWindow(window: Window): Flow<DeskTourWindow> = flowOf()
        override suspend fun requestNoteWindow(): KadreResult<WindowRequest> {
            calls++
            return gate.await()
        }
    }

    @Test
    fun `an admitted action stays pending until its public outcome arrives`() = runTest {
        val store = TourStore()
        val gateway = ScriptedGateway()
        val dispatcher = ActionDispatcher(store, gateway)

        val job = launch { dispatcher.createNote() }
        withTimeout(1_000) { while (store.state.value.activity.isEmpty()) delay(1) }

        assertEquals(ActivityStatus.Pending, store.state.value.activity.single().status)
        job.cancelAndJoin()
    }

    @Test
    fun `an unsupported request leaves the journal unavailable and never succeeded`() = runTest {
        val store = TourStore()
        val gateway = ScriptedGateway(
            CompletableDeferred(KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindow))),
        )
        ActionDispatcher(store, gateway).createNote()

        val entry = store.state.value.activity.single()
        assertEquals(ActivityStatus.Unavailable, entry.status)
        assertTrue(entry.motif!!.isNotBlank())
    }

    @Test
    fun `an in-flight action cancelled by session shutdown resolves as cancelled`() = runTest {
        val store = TourStore()
        val gateway = ScriptedGateway()
        val dispatcher = ActionDispatcher(store, gateway)

        val job: Job = launch { dispatcher.createNote() }
        withTimeout(1_000) { while (store.state.value.activity.isEmpty()) delay(1) }
        job.cancelAndJoin()

        assertEquals(ActivityStatus.Cancelled, store.state.value.activity.single().status)
    }

    @Test
    fun `two rapid identical intents produce two correlated entries not one silent duplicate`() = runTest {
        val store = TourStore()
        val gateway = ScriptedGateway()
        val dispatcher = ActionDispatcher(store, gateway)

        val first = launch { dispatcher.createNote() }
        val second = launch { dispatcher.createNote() }
        withTimeout(1_000) { while (store.state.value.activity.size < 2) delay(1) }

        assertEquals(2, store.state.value.activity.size)
        assertEquals(2, store.state.value.activity.map { it.correlationId }.toSet().size)
        first.cancelAndJoin()
        second.cancelAndJoin()
    }
}
