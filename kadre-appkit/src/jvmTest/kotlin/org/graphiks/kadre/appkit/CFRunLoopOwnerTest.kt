package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CFRunLoopOwnerTest {
    @Test
    fun `pre-run immediate timer waits for first before-waiting phase`() {
        val causes = mutableListOf<StartCause>()
        var aboutToWaitCount = 0
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = causes::add,
            onBeforeWaiting = {
                aboutToWaitCount++
                ControlFlow.Wait
            },
        )
        owner.consumeLaunchIteration()

        try {
            owner.wakeUp()
            val preRunTimer = api.createdTimers.single()

            CFRunLoopOwner.dispatchTimerCallback(preRunTimer)
            assertTrue(causes.isEmpty())
            assertEquals(0, CFRunLoopOwner.registeredTimerCount())

            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            val deliveryTimer = api.createdTimers.last()
            assertFalse(deliveryTimer == preRunTimer)
            assertEquals(1, aboutToWaitCount)

            CFRunLoopOwner.dispatchTimerCallback(deliveryTimer)
            assertEquals(listOf<StartCause>(StartCause.WaitCancelled()), causes)
            assertEquals(0, CFRunLoopOwner.registeredTimerCount())

            val traceAfterDelivery = api.trace.toList()
            CFRunLoopOwner.dispatchTimerCallback(preRunTimer)
            CFRunLoopOwner.dispatchTimerCallback(deliveryTimer)
            assertEquals(traceAfterDelivery, api.trace)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `after waiting cannot consume an immediate timer cause`() {
        val causes = mutableListOf<StartCause>()
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = causes::add,
            onBeforeWaiting = { ControlFlow.Wait },
        )
        owner.consumeLaunchIteration()
        CFRunLoopOwner.dispatchObserverCallback(
            api.createdObserver,
            CFRunLoopOwner.BEFORE_WAITING,
        )

        try {
            owner.wakeUp()
            val immediateTimer = api.createdTimers.single()

            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.AFTER_WAITING,
            )
            assertTrue(causes.isEmpty())

            CFRunLoopOwner.dispatchTimerCallback(immediateTimer)
            assertEquals(listOf<StartCause>(StartCause.WaitCancelled()), causes)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `proxy wake while delivering an iteration preserves the next about-to-wait`() {
        val causes = mutableListOf<StartCause>()
        var aboutToWaitCount = 0
        var wakeAgain = true
        val api = RecordingCFRunLoopApi()
        lateinit var owner: CFRunLoopOwner
        owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = { cause ->
                causes += cause
                if (wakeAgain) {
                    wakeAgain = false
                    owner.wakeUp()
                }
            },
            onBeforeWaiting = {
                aboutToWaitCount++
                ControlFlow.Wait
            },
        )
        owner.consumeLaunchIteration()

        try {
            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            owner.wakeUp()
            val firstTimer = api.createdTimers.single()

            CFRunLoopOwner.dispatchTimerCallback(firstTimer)

            assertEquals(1, api.trace.count { it == "wake-up" })
            assertEquals(1, aboutToWaitCount)
            assertEquals(listOf<StartCause>(StartCause.WaitCancelled()), causes)

            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            val secondTimer = api.createdTimers.last()
            assertFalse(firstTimer == secondTimer)
            assertEquals(2, api.trace.count { it == "wake-up" })
            assertEquals(2, aboutToWaitCount)

            CFRunLoopOwner.dispatchTimerCallback(secondTimer)
            assertEquals(List<StartCause>(2) { StartCause.WaitCancelled() }, causes)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `proxy wake after event delivery waits for the open iteration before-waiting phase`() {
        val causes = mutableListOf<StartCause>()
        var aboutToWaitCount = 0
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = causes::add,
            onBeforeWaiting = {
                aboutToWaitCount++
                ControlFlow.Wait
            },
        )
        owner.consumeLaunchIteration()

        try {
            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            owner.wakeUp()
            val firstTimer = api.createdTimers.single()
            CFRunLoopOwner.dispatchTimerCallback(firstTimer)

            owner.wakeUp()

            assertEquals(1, api.trace.count { it == "wake-up" })
            assertEquals(1, api.createdTimers.size)
            assertEquals(1, aboutToWaitCount)

            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            val secondTimer = api.createdTimers.last()
            assertFalse(firstTimer == secondTimer)
            assertEquals(2, aboutToWaitCount)
            assertEquals(2, api.trace.count { it == "wake-up" })

            CFRunLoopOwner.dispatchTimerCallback(secondTimer)
            assertEquals(List<StartCause>(2) { StartCause.WaitCancelled() }, causes)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `iteration opens atomically before a claimed cause can admit another wake`() {
        val causes = mutableListOf<StartCause>()
        var aboutToWaitCount = 0
        var wakeAtClaim = true
        val api = RecordingCFRunLoopApi()
        lateinit var owner: CFRunLoopOwner
        owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = causes::add,
            onBeforeWaiting = {
                aboutToWaitCount++
                ControlFlow.Wait
            },
            afterIterationClaimed = {
                if (wakeAtClaim) {
                    wakeAtClaim = false
                    owner.wakeUp()
                }
            },
        )
        owner.consumeLaunchIteration()

        try {
            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            owner.wakeUp()
            val firstTimer = api.createdTimers.single()

            CFRunLoopOwner.dispatchTimerCallback(firstTimer)

            assertEquals(1, api.trace.count { it == "wake-up" })
            assertEquals(1, api.createdTimers.size)
            assertEquals(listOf<StartCause>(StartCause.WaitCancelled()), causes)

            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            val secondTimer = api.createdTimers.last()
            assertFalse(firstTimer == secondTimer)
            assertEquals(2, aboutToWaitCount)
            assertEquals(2, api.trace.count { it == "wake-up" })

            CFRunLoopOwner.dispatchTimerCallback(secondTimer)
            assertEquals(List<StartCause>(2) { StartCause.WaitCancelled() }, causes)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `three wake consume cycles use distinct immediate timers`() {
        val causes = mutableListOf<StartCause>()
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = causes::add,
            onBeforeWaiting = { ControlFlow.Wait },
        )
        owner.consumeLaunchIteration()
        CFRunLoopOwner.dispatchObserverCallback(
            api.createdObserver,
            CFRunLoopOwner.BEFORE_WAITING,
        )

        try {
            repeat(3) {
                owner.wakeUp()
                val timer = api.createdTimers.last()
                CFRunLoopOwner.dispatchTimerCallback(timer)
                CFRunLoopOwner.dispatchObserverCallback(
                    api.createdObserver,
                    CFRunLoopOwner.BEFORE_WAITING,
                )
                assertEquals(0, CFRunLoopOwner.registeredTimerCount())
            }

            assertEquals(3, api.createdTimers.distinct().size)
            assertEquals(List<StartCause>(3) { StartCause.WaitCancelled() }, causes)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `external wake replaces a deadline with an immediate timer`() {
        val deadline = 2_000L
        val causes = mutableListOf<StartCause>()
        var controlFlow: ControlFlow = ControlFlow.WaitUntil(deadline)
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = causes::add,
            onBeforeWaiting = { controlFlow },
        )
        owner.consumeLaunchIteration()

        try {
            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            val deadlineTimer = api.createdTimers.single()

            owner.wakeUp()
            val immediateTimer = api.createdTimers.last()
            assertFalse(immediateTimer == deadlineTimer)
            assertEquals(
                listOf(
                    "invalidate-timer-$deadlineTimer",
                    "remove-timer-$deadlineTimer",
                    "release-$deadlineTimer",
                ),
                api.trace.windowed(3).first { it.first() == "invalidate-timer-$deadlineTimer" },
            )

            val traceAfterReplacement = api.trace.toList()
            CFRunLoopOwner.dispatchTimerCallback(deadlineTimer)
            assertEquals(traceAfterReplacement, api.trace)

            controlFlow = ControlFlow.Wait
            CFRunLoopOwner.dispatchTimerCallback(immediateTimer)
            assertEquals(
                listOf<StartCause>(StartCause.WaitCancelled(requestedResume = deadline)),
                causes,
            )
        } finally {
            owner.close()
        }
    }

    @Test
    fun `deadline timer callback delivers without after waiting`() {
        var nowMillis = 1_000L
        val deadline = 2_000L
        val causes = mutableListOf<StartCause>()
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { nowMillis },
            onAfterWaiting = causes::add,
            onBeforeWaiting = { ControlFlow.WaitUntil(deadline) },
        )
        owner.consumeLaunchIteration()

        try {
            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            val timer = api.createdTimers.single()
            nowMillis = 2_007L

            CFRunLoopOwner.dispatchTimerCallback(timer)

            assertEquals(
                listOf<StartCause>(
                    StartCause.ResumeTimeReached(
                        requestedResume = deadline,
                        start = nowMillis,
                    ),
                ),
                causes,
            )
            assertEquals(0, CFRunLoopOwner.registeredTimerCount())
        } finally {
            owner.close()
        }
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
    fun `close releases an immediate timer before observer and ignores stale callback`() {
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = {},
            onBeforeWaiting = { ControlFlow.Wait },
        )
        owner.consumeLaunchIteration()
        owner.wakeUp()
        val immediateTimer = api.createdTimers.single()
        val traceStart = api.trace.size

        owner.close()

        assertEquals(
            listOf(
                "invalidate-timer-$immediateTimer",
                "remove-timer-$immediateTimer",
                "release-$immediateTimer",
                "remove-observer-${api.createdObserver}",
                "release-${api.createdObserver}",
                "close-arena",
            ),
            api.trace.drop(traceStart),
        )
        assertEquals(0, CFRunLoopOwner.registeredObserverCount())
        assertEquals(0, CFRunLoopOwner.registeredTimerCount())

        val traceAfterClose = api.trace.toList()
        CFRunLoopOwner.dispatchTimerCallback(immediateTimer)
        assertEquals(traceAfterClose, api.trace)
    }

    @Test
    fun `poll creates one immediate timer per iteration without synchronous spin`() {
        val causes = mutableListOf<StartCause>()
        var aboutToWaitCount = 0
        var controlFlow: ControlFlow = ControlFlow.Poll
        val api = RecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = causes::add,
            onBeforeWaiting = {
                aboutToWaitCount++
                controlFlow
            },
        )
        owner.consumeLaunchIteration()

        try {
            repeat(3) { cycle ->
                CFRunLoopOwner.dispatchObserverCallback(
                    api.createdObserver,
                    CFRunLoopOwner.BEFORE_WAITING,
                )
                val timer = api.createdTimers.last()
                assertEquals(cycle + 1, api.createdTimers.size)

                CFRunLoopOwner.dispatchObserverCallback(
                    api.createdObserver,
                    CFRunLoopOwner.BEFORE_WAITING,
                )
                assertEquals(cycle + 1, api.createdTimers.size)
                assertEquals(cycle + 1, aboutToWaitCount)

                CFRunLoopOwner.dispatchTimerCallback(timer)
            }

            controlFlow = ControlFlow.Wait
            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )

            assertEquals(List<StartCause>(3) { StartCause.Poll }, causes)
            assertEquals(3, api.createdTimers.distinct().size)
            assertEquals(4, aboutToWaitCount)
            assertEquals(0, CFRunLoopOwner.registeredTimerCount())
        } finally {
            owner.close()
        }
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
        val deadlineTimer = api.createdTimers.first()
        val immediateTimer = api.createdTimers.last()
        val callbackTrace = api.trace.drop(traceStart).toList()
        api.clearFailures()
        owner.close()

        assertSame(addFailure, actual)
        assertEquals(
            listOf(invalidateFailure, removeFailure, releaseFailure),
            actual.suppressed.toList(),
        )
        assertEquals(
            listOf(
                "create-timer-$deadlineTimer-2000",
                "add-timer-$deadlineTimer",
                "invalidate-timer-$deadlineTimer",
                "remove-timer-$deadlineTimer",
                "release-$deadlineTimer",
                "create-immediate-timer-$immediateTimer",
                "add-timer-$immediateTimer",
                "invalidate-timer-$immediateTimer",
                "remove-timer-$immediateTimer",
                "release-$immediateTimer",
                "wake-up",
            ),
            callbackTrace,
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
        val immediateTimer = api.createdTimers.single()
        api.clearFailures()
        owner.close()

        assertSame(callbackFailure, actual)
        assertEquals(listOf(wakeFailure), actual.suppressed.toList())
        assertTrue(api.trace.drop(traceStart).contains("wake-up"))
        assertTrue(api.trace.contains("invalidate-timer-$immediateTimer"))
        assertEquals(0, CFRunLoopOwner.registeredTimerCount())
    }

    @Test
    fun `callback failure remains primary when persistent timer creation fails`() {
        val callbackFailure = IllegalStateException("callback")
        val api = RecordingCFRunLoopApi().apply { returnNullTimer = true }
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 1_000L },
            onAfterWaiting = { throw callbackFailure },
            onBeforeWaiting = { ControlFlow.Wait },
        )

        CFRunLoopOwner.dispatchObserverCallback(api.createdObserver, CFRunLoopOwner.AFTER_WAITING)
        val actual = assertFailsWith<IllegalStateException> {
            owner.throwPendingCallbackFailure()
        }
        owner.close()

        assertSame(callbackFailure, actual)
        assertEquals(1, actual.suppressed.size)
        assertEquals("CFRunLoopTimerCreate returned NULL", actual.suppressed.single().message)
        assertEquals(0, CFRunLoopOwner.registeredTimerCount())
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
        assertEquals(
            listOf(
                "create-timer-0-2000",
                "create-immediate-timer-0",
                "wake-up",
            ),
            callbackTrace,
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
        val persistentTimer = api.createdTimers.last()

        assertEquals(
            listOf(
                "invalidate-timer-$timer",
                "remove-timer-$timer",
                "release-$timer",
                "create-immediate-timer-$persistentTimer",
                "add-timer-$persistentTimer",
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
        api.clearFailures()
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

        override fun createImmediateTimer(): Long =
            (if (returnNullTimer) 0L else nextRef++).also {
                createdTimers += it
                trace += "create-immediate-timer-$it"
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
