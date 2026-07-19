package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class CFRunLoopOwnerTest {
    @Test
    fun `pre-run wake is re-fired before waiting and consumed once`() {
        val causes = mutableListOf<StartCause>()
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = causes::add,
            onBeforeWaiting = { ControlFlow.Wait },
        )
        owner.consumeLaunchIteration()
        val traceStart = api.trace.size

        owner.wakeUp()
        CFRunLoopOwner.dispatchObserverCallback(
            api.createdObserver,
            CFRunLoopOwner.BEFORE_WAITING,
        )
        val traceBeforeAfterWaiting = api.trace.drop(traceStart).toList()

        CFRunLoopOwner.dispatchObserverCallback(
            api.createdObserver,
            CFRunLoopOwner.AFTER_WAITING,
        )
        CFRunLoopOwner.dispatchObserverCallback(
            api.createdObserver,
            CFRunLoopOwner.BEFORE_WAITING,
        )
        val traceAfterConsumption = api.trace.drop(traceStart).toList()
        owner.close()

        assertEquals(listOf("wake-up", "wake-up"), traceBeforeAfterWaiting)
        assertEquals(listOf<StartCause>(StartCause.WaitCancelled()), causes)
        assertEquals(listOf("wake-up", "wake-up"), traceAfterConsumption)
    }

    @Test
    fun `after waiting classifies deadline before timer callback confirms cleanup`() {
        var nowMillis = 1_000L
        var controlFlow: ControlFlow = ControlFlow.WaitUntil(2_000L)
        val order = mutableListOf<String>()
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { nowMillis },
            onAfterWaiting = { cause -> order += "newEvents($cause)" },
            onBeforeWaiting = {
                order += "aboutToWait"
                controlFlow
            },
        )

        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.AFTER_WAITING)
        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.BEFORE_WAITING)
        val timer = api.createdTimers.single()
        order.clear()
        nowMillis = 2_007L
        controlFlow = ControlFlow.Wait

        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.AFTER_WAITING)
        order += "timer"
        CFRunLoopOwner.dispatchTimerCallback(timer)
        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.BEFORE_WAITING)
        val pendingFailure = runCatching { owner.throwPendingCallbackFailure() }.exceptionOrNull()
        owner.close()

        assertNull(pendingFailure)
        assertEquals(
            listOf(
                "newEvents(${StartCause.ResumeTimeReached(2_000L, nowMillis)})",
                "timer",
                "aboutToWait",
            ),
            order,
        )
    }

    @Test
    fun `after waiting without timer or proxy classifies a native wake`() {
        val causes = mutableListOf<StartCause>()
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = causes::add,
            onBeforeWaiting = { ControlFlow.Wait },
        )

        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.AFTER_WAITING)
        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.BEFORE_WAITING)
        causes.clear()
        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.AFTER_WAITING)
        val pendingFailure = runCatching { owner.throwPendingCallbackFailure() }.exceptionOrNull()
        owner.close()

        assertNull(pendingFailure)
        assertEquals(listOf<StartCause>(StartCause.WaitCancelled()), causes)
    }

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
    fun `observer add rollback preserves primary and suppresses every cleanup failure`() {
        val addFailure = IllegalStateException("add observer")
        val invalidateFailure = IllegalStateException("invalidate observer")
        val removeFailure = IllegalStateException("remove observer")
        val releaseFailure = IllegalStateException("release observer")
        val closeFailure = IllegalStateException("close arena")
        val api = RecordingCFRunLoopApi().apply {
            addObserverFailure = addFailure
            invalidateObserverFailure = invalidateFailure
            removeObserverFailure = removeFailure
            this.releaseFailure = releaseFailure
            this.closeFailure = closeFailure
        }

        val actual = assertFailsWith<IllegalStateException> {
            CFRunLoopOwner.install(
                api = api,
                state = AppKitLoopState { 1_000L },
                onAfterWaiting = {},
                onBeforeWaiting = { ControlFlow.Wait },
            )
        }

        assertSame(addFailure, actual)
        assertEquals(
            listOf(invalidateFailure, removeFailure, releaseFailure, closeFailure),
            actual.suppressed.toList(),
        )
        assertEquals(
            listOf(
                "create-observer-${CFRunLoopOwner.OBSERVED_ACTIVITIES}",
                "add-observer-${api.createdObserver}",
                "invalidate-observer-${api.createdObserver}",
                "remove-observer-${api.createdObserver}",
                "release-${api.createdObserver}",
                "close-arena",
            ),
            api.trace,
        )
    }

    @Test
    fun `timer add rollback preserves primary and suppresses every cleanup failure`() {
        val addFailure = IllegalStateException("add timer")
        val invalidateFailure = IllegalStateException("invalidate timer")
        val removeFailure = IllegalStateException("remove timer")
        val releaseFailure = IllegalStateException("release timer")
        val api = RecordingCFRunLoopApi().apply {
            addTimerFailure = addFailure
            invalidateTimerFailure = invalidateFailure
            removeTimerFailure = removeFailure
            this.releaseFailure = releaseFailure
        }
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = {},
            onBeforeWaiting = { ControlFlow.WaitUntil(2_000L) },
        )
        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.AFTER_WAITING)
        val traceStart = api.trace.size

        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.BEFORE_WAITING)
        val actual = assertFailsWith<IllegalStateException> {
            owner.throwPendingCallbackFailure()
        }
        api.clearFailures()
        owner.close()

        assertSame(addFailure, actual)
        assertEquals(
            listOf(invalidateFailure, removeFailure, releaseFailure),
            actual.suppressed.toList(),
        )
        val timer = api.createdTimers.single()
        assertEquals(
            listOf(
                "create-timer-$timer-2000",
                "add-timer-$timer",
                "invalidate-timer-$timer",
                "remove-timer-$timer",
                "release-$timer",
                "wake-up",
            ),
            api.trace.drop(traceStart).take(6),
        )
    }

    @Test
    fun `observer close invalidates after removal failure before release`() {
        val removeFailure = IllegalStateException("remove observer")
        val api = RecordingCFRunLoopApi().apply {
            removeObserverFailure = removeFailure
        }
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = {},
            onBeforeWaiting = { ControlFlow.Wait },
        )
        val traceStart = api.trace.size

        val actual = assertFailsWith<IllegalStateException> { owner.close() }

        assertSame(removeFailure, actual)
        assertEquals(
            listOf(
                "remove-observer-${api.createdObserver}",
                "invalidate-observer-${api.createdObserver}",
                "release-${api.createdObserver}",
                "close-arena",
            ),
            api.trace.drop(traceStart),
        )
    }

    @Test
    fun `callback failure is queued then wakes and wake failure is suppressed`() {
        val callbackFailure = IllegalStateException("callback")
        val wakeFailure = IllegalStateException("wake")
        val api = RecordingCFRunLoopApi().apply { wakeUpFailure = wakeFailure }
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = { throw callbackFailure },
            onBeforeWaiting = { ControlFlow.Wait },
        )
        val traceStart = api.trace.size

        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.AFTER_WAITING)
        val actual = assertFailsWith<IllegalStateException> {
            owner.throwPendingCallbackFailure()
        }
        api.clearFailures()
        owner.close()

        assertSame(callbackFailure, actual)
        assertEquals(listOf(wakeFailure), actual.suppressed.toList())
        assertEquals(listOf("wake-up"), api.trace.drop(traceStart).take(1))
    }

    @Test
    fun `run failure remains primary and queued callback failure is suppressed`() {
        val callbackFailure = IllegalStateException("callback")
        val runFailure = IllegalStateException("run")
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = { throw callbackFailure },
            onBeforeWaiting = { ControlFlow.Wait },
        )
        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.AFTER_WAITING)

        val method = CFRunLoopOwner::class.java.declaredMethods.firstOrNull {
            it.name == "suppressPendingCallbackFailureOnto"
        }
        method?.invoke(owner, runFailure)
        owner.close()

        assertNotNull(method)
        assertEquals(listOf(callbackFailure), runFailure.suppressed.toList())
    }

    @Test
    fun `NULL observer create is rejected before routing or add`() {
        val api = RecordingCFRunLoopApi().apply { returnNullObserver = true }

        val result = runCatching {
            CFRunLoopOwner.install(
                api = api,
                state = AppKitLoopState { 1_000L },
                onAfterWaiting = {},
                onBeforeWaiting = { ControlFlow.Wait },
            )
        }
        result.getOrNull()?.close()

        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(
            listOf(
                "create-observer-${CFRunLoopOwner.OBSERVED_ACTIVITIES}",
                "close-arena",
            ),
            api.trace,
        )
    }

    @Test
    fun `NULL timer create is captured and wakes before Kotlin boundary`() {
        val api = RecordingCFRunLoopApi().apply { returnNullTimer = true }
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = {},
            onBeforeWaiting = { ControlFlow.WaitUntil(2_000L) },
        )
        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.AFTER_WAITING)
        val traceStart = api.trace.size

        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.BEFORE_WAITING)
        val actual = assertFailsWith<IllegalStateException> {
            owner.throwPendingCallbackFailure()
        }
        val callbackTrace = api.trace.drop(traceStart)
        owner.close()

        assertEquals("CFRunLoopTimerCreate returned NULL", actual.message)
        assertEquals(listOf("create-timer-0-2000", "wake-up"), callbackTrace)
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
                "wake-up",
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
        val observerCauses = mutableListOf<StartCause>()

        val owner = CFRunLoopOwner.install(
            api = api,
            state = state,
            onAfterWaiting = observerCauses::add,
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
        CFRunLoopOwner.dispatchObserverCallback(observer, CFRunLoopOwner.AFTER_WAITING)
        CFRunLoopOwner.dispatchTimerCallback(secondTimer)
        assertEquals(
            StartCause.ResumeTimeReached(requestedResume = 3_000L, start = nowMillis),
            observerCauses.last(),
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
        assertEquals(2, observerCauses.size)
    }

    private class RecordingCFRunLoopApi : CFRunLoopApi {
        val trace = mutableListOf<String>()
        val createdTimers = mutableListOf<Long>()
        var createdObserver = 0L
        var returnNullObserver = false
        var returnNullTimer = false
        var createObserverFailure: Throwable? = null
        var addObserverFailure: Throwable? = null
        var removeObserverFailure: Throwable? = null
        var invalidateObserverFailure: Throwable? = null
        var addTimerFailure: Throwable? = null
        var invalidateTimerFailure: Throwable? = null
        var removeTimerFailure: Throwable? = null
        var wakeUpFailure: Throwable? = null
        var releaseFailure: Throwable? = null
        var closeFailure: Throwable? = null
        private var nextRef = 100L

        override fun createObserver(activities: Long): Long {
            trace += "create-observer-$activities"
            createObserverFailure?.let { throw it }
            if (returnNullObserver) return 0L.also { createdObserver = it }
            return nextRef++.also { createdObserver = it }
        }

        override fun addObserver(observer: Long) {
            trace += "add-observer-$observer"
            addObserverFailure?.let { throw it }
        }

        override fun removeObserver(observer: Long) {
            trace += "remove-observer-$observer"
            removeObserverFailure?.let { throw it }
        }

        override fun invalidateObserver(observer: Long) {
            trace += "invalidate-observer-$observer"
            invalidateObserverFailure?.let { throw it }
        }

        override fun createTimer(deadlineEpochMillis: Long): Long =
            (if (returnNullTimer) 0L else nextRef++).also {
            createdTimers += it
            trace += "create-timer-$it-$deadlineEpochMillis"
        }

        override fun addTimer(timer: Long) {
            trace += "add-timer-$timer"
            addTimerFailure?.let { throw it }
        }

        override fun invalidateTimer(timer: Long) {
            trace += "invalidate-timer-$timer"
            invalidateTimerFailure?.let { throw it }
        }

        override fun removeTimer(timer: Long) {
            trace += "remove-timer-$timer"
            removeTimerFailure?.let { throw it }
        }

        override fun wakeUp() {
            trace += "wake-up"
            wakeUpFailure?.let { throw it }
        }

        override fun release(ref: Long) {
            trace += "release-$ref"
            releaseFailure?.let { throw it }
        }

        override fun close() {
            trace += "close-arena"
            closeFailure?.let { throw it }
        }

        fun clearFailures() {
            addObserverFailure = null
            removeObserverFailure = null
            invalidateObserverFailure = null
            addTimerFailure = null
            invalidateTimerFailure = null
            removeTimerFailure = null
            wakeUpFailure = null
            releaseFailure = null
            closeFailure = null
        }
    }
}
