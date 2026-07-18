package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CFRunLoopOwnerTest {
    @Test
    fun `observer dispatches after waiting before before waiting`() {
        val callbacks = mutableListOf<String>()
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = { cause -> callbacks += "newEvents($cause)" },
            onBeforeWaiting = {
                callbacks += "aboutToWait"
                ControlFlow.Wait
            },
        )

        CFRunLoopOwner.dispatchObserverCallback(
            api.createdObserver,
            CFRunLoopOwner.OBSERVED_ACTIVITIES,
        )

        assertEquals(
            listOf("newEvents(${StartCause.Init})", "aboutToWait"),
            callbacks,
        )
        owner.close()
    }

    @Test
    fun `observer creation failure closes callback arena before ownership transfer`() {
        val expectedFailure = IllegalStateException("create observer")
        val api = RecordingCFRunLoopApi().apply {
            createObserverFailure = expectedFailure
        }

        assertSame(
            expectedFailure,
            assertFailsWith<IllegalStateException> {
                CFRunLoopOwner.install(
                    api = api,
                    state = AppKitLoopState { 1_000L },
                    onAfterWaiting = {},
                    onBeforeWaiting = { ControlFlow.Wait },
                )
            },
        )
        assertEquals(
            listOf(
                "create-observer-${CFRunLoopOwner.OBSERVED_ACTIVITIES}",
                "close-arena",
            ),
            api.trace,
        )
    }

    @Test
    fun `timer cleanup completes and callback failure waits for Kotlin boundary`() {
        var controlFlow: ControlFlow = ControlFlow.WaitUntil(2_000L)
        val state = AppKitLoopState { 1_000L }
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = state,
            onAfterWaiting = {},
            onBeforeWaiting = { controlFlow },
        )
        CFRunLoopOwner.dispatchObserverCallback(
            api.createdObserver,
            CFRunLoopOwner.AFTER_WAITING,
        )
        CFRunLoopOwner.dispatchObserverCallback(
            api.createdObserver,
            CFRunLoopOwner.BEFORE_WAITING,
        )
        val timer = api.createdTimers.single()
        val expectedFailure = IllegalStateException("invalidate timer")
        api.invalidateTimerFailure = expectedFailure
        controlFlow = ControlFlow.Wait
        val cleanupStart = api.trace.size

        CFRunLoopOwner.dispatchObserverCallback(
            api.createdObserver,
            CFRunLoopOwner.BEFORE_WAITING,
        )

        assertEquals(
            listOf(
                "invalidate-timer-$timer",
                "remove-timer-$timer",
                "release-$timer",
            ),
            api.trace.drop(cleanupStart),
        )
        assertSame(
            expectedFailure,
            assertFailsWith<IllegalStateException> { owner.throwPendingCallbackFailure() },
        )
        val traceAfterFailure = api.trace.toList()
        CFRunLoopOwner.dispatchTimerCallback(timer)
        assertEquals(traceAfterFailure, api.trace)
        owner.close()
    }

    @Test
    fun `owner installs and releases observer and timers in reverse ownership order`() {
        var nowMillis = 1_000L
        var controlFlow: ControlFlow = ControlFlow.WaitUntil(2_000L)
        val state = AppKitLoopState { nowMillis }
        val api = RecordingCFRunLoopApi()
        var observerCallbacks = 0

        val owner = CFRunLoopOwner.install(
            api = api,
            state = state,
            onAfterWaiting = { observerCallbacks++ },
            onBeforeWaiting = { controlFlow },
        )

        val observer = api.createdObserver
        assertEquals(
            listOf(
                "create-observer-${CFRunLoopOwner.OBSERVED_ACTIVITIES}",
                "add-observer-$observer",
            ),
            api.trace,
        )

        CFRunLoopOwner.dispatchObserverCallback(observer, CFRunLoopOwner.AFTER_WAITING)
        CFRunLoopOwner.dispatchObserverCallback(observer, CFRunLoopOwner.BEFORE_WAITING)
        val firstTimer = api.createdTimers.single()

        controlFlow = ControlFlow.WaitUntil(3_000L)
        CFRunLoopOwner.dispatchObserverCallback(observer, CFRunLoopOwner.BEFORE_WAITING)
        val secondTimer = api.createdTimers.last()
        assertEquals(
            listOf(
                "invalidate-timer-$firstTimer",
                "remove-timer-$firstTimer",
                "release-$firstTimer",
            ),
            api.trace.subList(4, 7),
        )

        nowMillis = 3_007L
        CFRunLoopOwner.dispatchTimerCallback(secondTimer)
        assertEquals(
            StartCause.ResumeTimeReached(requestedResume = 3_000L, start = nowMillis),
            state.beginIteration(),
        )

        controlFlow = ControlFlow.WaitUntil(4_000L)
        CFRunLoopOwner.dispatchObserverCallback(observer, CFRunLoopOwner.BEFORE_WAITING)
        val finalTimer = api.createdTimers.last()
        val traceBeforeClose = api.trace.size

        owner.close()
        assertEquals(
            listOf(
                "invalidate-timer-$finalTimer",
                "remove-timer-$finalTimer",
                "release-$finalTimer",
                "remove-observer-$observer",
                "release-$observer",
                "close-arena",
            ),
            api.trace.drop(traceBeforeClose),
        )

        val traceAfterClose = api.trace.toList()
        owner.close()
        CFRunLoopOwner.dispatchObserverCallback(observer, CFRunLoopOwner.AFTER_WAITING)
        CFRunLoopOwner.dispatchTimerCallback(finalTimer)

        assertEquals(traceAfterClose, api.trace)
        assertEquals(1, observerCallbacks)
    }

    private class RecordingCFRunLoopApi : CFRunLoopApi {
        val trace = mutableListOf<String>()
        val createdTimers = mutableListOf<Long>()
        var createdObserver = 0L
        var createObserverFailure: Throwable? = null
        var invalidateTimerFailure: Throwable? = null
        private var nextRef = 100L

        override fun createObserver(activities: Long): Long {
            trace += "create-observer-$activities"
            createObserverFailure?.let { throw it }
            return nextRef++.also { createdObserver = it }
        }

        override fun addObserver(observer: Long) {
            trace += "add-observer-$observer"
        }

        override fun removeObserver(observer: Long) {
            trace += "remove-observer-$observer"
        }

        override fun invalidateObserver(observer: Long) {
            trace += "invalidate-observer-$observer"
        }

        override fun createTimer(deadlineEpochMillis: Long): Long = nextRef++.also {
            createdTimers += it
            trace += "create-timer-$it-$deadlineEpochMillis"
        }

        override fun addTimer(timer: Long) {
            trace += "add-timer-$timer"
        }

        override fun invalidateTimer(timer: Long) {
            trace += "invalidate-timer-$timer"
            invalidateTimerFailure?.let { throw it }
        }

        override fun removeTimer(timer: Long) {
            trace += "remove-timer-$timer"
        }

        override fun wakeUp() {
            trace += "wake-up"
        }

        override fun release(ref: Long) {
            trace += "release-$ref"
        }

        override fun close() {
            trace += "close-arena"
        }
    }
}
