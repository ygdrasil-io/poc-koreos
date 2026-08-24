package org.graphiks.kadre.appkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AppKitTestStopCoordinatorTest {
    @Test
    fun `one request stops then creates and posts one application-defined event at start`() {
        val event = Any()
        val operations = RecordingOperations(event)
        val coordinator = AppKitTestStopCoordinator(operations)

        coordinator.requestStop()

        assertEquals(
            listOf("stop", "create-application-defined-event", "post-event-at-start"),
            operations.trace,
        )
        assertSame(event, operations.postedEvent)
    }

    @Test
    fun `repeated request is idempotent while a fresh coordinator performs a fresh cycle`() {
        val first = RecordingOperations(Any())
        val firstCoordinator = AppKitTestStopCoordinator(first)

        firstCoordinator.requestStop()
        firstCoordinator.requestStop()

        assertEquals(
            listOf("stop", "create-application-defined-event", "post-event-at-start"),
            first.trace,
        )

        val second = RecordingOperations(Any())
        AppKitTestStopCoordinator(second).requestStop()

        assertEquals(
            listOf("stop", "create-application-defined-event", "post-event-at-start"),
            second.trace,
        )
    }

    @Test
    fun `null synthetic event is a hard deterministic failure`() {
        val operations = RecordingOperations<Any>(event = null)
        val coordinator = AppKitTestStopCoordinator(operations)

        val firstFailure = assertFailsWith<IllegalStateException> {
            coordinator.requestStop()
        }
        val repeatedFailure = assertFailsWith<IllegalStateException> {
            coordinator.requestStop()
        }

        assertEquals("NSEventTypeApplicationDefined creation returned null", firstFailure.message)
        assertSame(firstFailure, repeatedFailure)
        assertEquals(listOf("stop", "create-application-defined-event"), operations.trace)
    }

    @Test
    fun `stop failure remains the primary error and is not retried`() {
        val primary = IllegalArgumentException("stop failed")
        val operations = RecordingOperations(Any(), stopFailure = primary)
        val coordinator = AppKitTestStopCoordinator(operations)

        val firstFailure = assertFailsWith<IllegalArgumentException> {
            coordinator.requestStop()
        }
        val repeatedFailure = assertFailsWith<IllegalArgumentException> {
            coordinator.requestStop()
        }

        assertSame(primary, firstFailure)
        assertSame(primary, repeatedFailure)
        assertEquals(listOf("stop"), operations.trace)
    }

    @Test
    fun `event creation failure remains the primary error and is not retried`() {
        val primary = IllegalStateException("event creation failed")
        val operations = RecordingOperations(Any(), createFailure = primary)
        val coordinator = AppKitTestStopCoordinator(operations)

        val firstFailure = assertFailsWith<IllegalStateException> {
            coordinator.requestStop()
        }
        val repeatedFailure = assertFailsWith<IllegalStateException> {
            coordinator.requestStop()
        }

        assertSame(primary, firstFailure)
        assertSame(primary, repeatedFailure)
        assertEquals(listOf("stop", "create-application-defined-event"), operations.trace)
    }

    @Test
    fun `event post failure remains the primary error and is not retried`() {
        val primary = IllegalStateException("event post failed")
        val operations = RecordingOperations(Any(), postFailure = primary)
        val coordinator = AppKitTestStopCoordinator(operations)

        val firstFailure = assertFailsWith<IllegalStateException> {
            coordinator.requestStop()
        }
        val repeatedFailure = assertFailsWith<IllegalStateException> {
            coordinator.requestStop()
        }

        assertSame(primary, firstFailure)
        assertSame(primary, repeatedFailure)
        assertEquals(
            listOf("stop", "create-application-defined-event", "post-event-at-start"),
            operations.trace,
        )
    }

    private class RecordingOperations<Event : Any>(
        private val event: Event?,
        private val stopFailure: Throwable? = null,
        private val createFailure: Throwable? = null,
        private val postFailure: Throwable? = null,
    ) : AppKitTestStopCoordinator.Operations<Event> {
        val trace = mutableListOf<String>()
        var postedEvent: Event? = null
            private set

        override fun stop() {
            trace += "stop"
            stopFailure?.let { throw it }
        }

        override fun createApplicationDefinedEvent(): Event? {
            trace += "create-application-defined-event"
            createFailure?.let { throw it }
            return event
        }

        override fun postEventAtStart(event: Event) {
            trace += "post-event-at-start"
            postedEvent = event
            postFailure?.let { throw it }
        }
    }
}
