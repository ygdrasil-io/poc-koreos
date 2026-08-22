package org.graphiks.kadre.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.Fullscreen
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.test.ObservedCallback
import org.graphiks.kadre.test.assertIterationOrder

class UIKitLifecycleTest {

    @Test
    fun `scheduler records a complete shared iteration`() {
        val trace = mutableListOf<ObservedCallback>()
        val operations = LifecycleSchedulerOperations()
        val scheduler = UIKitScheduler(
            operations = operations,
            controlFlow = { org.graphiks.kadre.core.ControlFlow.Poll },
            newEvents = { trace += ObservedCallback.NewEvents },
            redraw = { trace += ObservedCallback.RedrawRequested },
            aboutToWait = { trace += ObservedCallback.AboutToWait },
        )
        scheduler.registerWindow(WindowId(1L))

        operations.fireDisplayLink()

        assertIterationOrder(trace)
    }

    @Test
    fun initialCreationAppliesSupportedAttributes() {
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
        val loop = UIKitActiveEventLoop(handler)
        val fullscreen = Fullscreen.Borderless()
        val window = loop.createWindow(
            WindowAttributes(
                title = "initial-title",
                visible = false,
                fullscreen = fullscreen,
            ),
        )

        try {
            assertEquals("initial-title", window.title)
            assertEquals(fullscreen, window.fullscreen)
        } finally {
            window.close()
        }
    }

    @Test
    fun startupAndBackgroundForegroundCallbacksFollowCanonicalTracesAndIgnoreDuplicates() {
        val trace = mutableListOf<String>()
        val createdWindows = mutableListOf<Window>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                trace += "canCreate"
                createdWindows += eventLoop.createWindow(
                    WindowAttributes(
                        title = "window-${createdWindows.size + 1}",
                        visible = false,
                    ),
                )
            }

            override fun resumed(eventLoop: ActiveEventLoop) {
                trace += "resumed"
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                trace += when (startCause) {
                    StartCause.Init -> "newEvents(Init)"
                    is StartCause.WaitCancelled -> "newEvents(WaitCancelled)"
                    else -> error("Unexpected lifecycle cause: $startCause")
                }
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                trace += "aboutToWait"
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                when (event) {
                    is WindowEvent.Focused -> trace += "focused${if (event.gained) "+" else "-"}"
                    is WindowEvent.Occluded -> trace += "occluded${if (event.occluded) "+" else "-"}"
                    else -> Unit
                }
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        val lifecycle = UIKitLifecycleOrchestrator(loop)

        try {
            lifecycle.didFinishLaunching()
            lifecycle.didFinishLaunching()
            lifecycle.willEnterForeground()
            lifecycle.didEnterBackground()
            lifecycle.didBecomeActive()
            lifecycle.willResignActive()
            lifecycle.willResignActive()
            lifecycle.didEnterBackground()
            lifecycle.didEnterBackground()
            lifecycle.didBecomeActive()
            lifecycle.willEnterForeground()
            lifecycle.willEnterForeground()
            lifecycle.didBecomeActive()
            lifecycle.didBecomeActive()

            assertSame(createdWindows[0], createdWindows[1])
            assertEquals(createdWindows[0].id, createdWindows[1].id)
            assertEquals("window-2", createdWindows[1].title)
            assertEquals(
                listOf(
                    "resumed",
                    "newEvents(Init)",
                    "canCreate",
                    "aboutToWait",
                    "newEvents(WaitCancelled)",
                    "focused-",
                    "suspended",
                    "aboutToWait",
                    "newEvents(WaitCancelled)",
                    "occluded+",
                    "destroySurfaces",
                    "aboutToWait",
                    "newEvents(WaitCancelled)",
                    "occluded-",
                    "canCreate",
                    "aboutToWait",
                    "resumed",
                    "newEvents(WaitCancelled)",
                    "focused+",
                    "aboutToWait",
                ),
                trace,
            )
        } finally {
            createdWindows.distinct().forEach(Window::close)
        }
    }

    @Test
    fun terminationFollowsCanonicalTraceAndIgnoresDuplicates() {
        val trace = mutableListOf<String>()
        val createdWindows = mutableListOf<Window>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                trace += "canCreate"
                createdWindows += eventLoop.createWindow(WindowAttributes(visible = false))
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                trace += when (startCause) {
                    StartCause.Init -> "newEvents(Init)"
                    is StartCause.WaitCancelled -> "newEvents(WaitCancelled)"
                    else -> error("Unexpected lifecycle cause: $startCause")
                }
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                trace += "aboutToWait"
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event == WindowEvent.Destroyed) trace += "destroyed"
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        val lifecycle = UIKitLifecycleOrchestrator(loop)

        try {
            lifecycle.didFinishLaunching()
            trace.clear()

            lifecycle.willTerminate()
            lifecycle.willTerminate()

            assertEquals(
                listOf(
                    "newEvents(WaitCancelled)",
                    "destroySurfaces",
                    "destroyed",
                    "suspended",
                    "aboutToWait",
                ),
                trace,
            )
            assertTrue(loop.isExiting)
        } finally {
            createdWindows.distinct().forEach(Window::close)
        }
    }

    @Test
    fun terminalIterationRunsEveryStageAndPreservesEveryFailureAfterNewEventsFails() {
        val trace = mutableListOf<String>()
        val newEventsFailure = IllegalStateException("newEvents")
        val destroySurfacesFailure = IllegalArgumentException("destroySurfaces")
        val destroyedFailure = AssertionError("destroyed")
        val suspendedFailure = IllegalStateException("suspended")
        val aboutToWaitFailure = IllegalArgumentException("aboutToWait")
        var terminating = false
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                eventLoop.createWindow(WindowAttributes(visible = false))
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (terminating) {
                    trace += "newEvents"
                    throw newEventsFailure
                }
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                if (terminating) {
                    trace += "aboutToWait"
                    throw aboutToWaitFailure
                }
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
                throw destroySurfacesFailure
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
                throw suspendedFailure
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event == WindowEvent.Destroyed) {
                    trace += "destroyed"
                    throw destroyedFailure
                }
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        val lifecycle = UIKitLifecycleOrchestrator(loop)

        lifecycle.didFinishLaunching()
        terminating = true
        val thrown = assertFailsWith<IllegalStateException> {
            lifecycle.willTerminate()
        }

        assertSame(newEventsFailure, thrown)
        assertEquals(
            listOf("newEvents", "destroySurfaces", "destroyed", "suspended", "aboutToWait"),
            trace,
        )
        assertEquals(
            listOf(destroySurfacesFailure, aboutToWaitFailure),
            thrown.suppressedExceptions,
        )
        assertEquals(
            listOf(destroyedFailure, suspendedFailure),
            destroySurfacesFailure.suppressedExceptions,
        )
        assertTrue(loop.isExiting)
    }

    @Test
    fun resignationRunsFocusedSuspendedAndAboutToWaitAfterEarlierFailures() {
        val trace = mutableListOf<String>()
        val newEventsFailure = IllegalStateException("newEvents")
        val focusedFailure = IllegalArgumentException("focused")
        val suspendedFailure = AssertionError("suspended")
        val aboutToWaitFailure = IllegalStateException("aboutToWait")
        var failResignation = false
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                eventLoop.createWindow(WindowAttributes(visible = false))
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (failResignation) {
                    trace += "newEvents"
                    throw newEventsFailure
                }
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                if (failResignation) {
                    trace += "aboutToWait"
                    throw aboutToWaitFailure
                }
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
                throw suspendedFailure
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.Focused && !event.gained) {
                    trace += "focused"
                    throw focusedFailure
                }
            }
        }
        val lifecycle = UIKitLifecycleOrchestrator(UIKitActiveEventLoop(handler))

        lifecycle.didFinishLaunching()
        failResignation = true
        val thrown = assertFailsWith<IllegalStateException> {
            lifecycle.willResignActive()
        }

        assertSame(newEventsFailure, thrown)
        assertEquals(listOf("newEvents", "focused", "suspended", "aboutToWait"), trace)
        assertEquals(listOf(focusedFailure, aboutToWaitFailure), thrown.suppressedExceptions)
        assertEquals(listOf(suspendedFailure), focusedFailure.suppressedExceptions)
    }

    @Test
    fun backgroundRunsOcclusionSurfaceDestructionAndAboutToWaitAfterEarlierFailures() {
        val trace = mutableListOf<String>()
        val newEventsFailure = IllegalStateException("newEvents")
        val occludedFailure = IllegalArgumentException("occluded")
        val destroySurfacesFailure = AssertionError("destroySurfaces")
        val aboutToWaitFailure = IllegalStateException("aboutToWait")
        var failBackground = false
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                eventLoop.createWindow(WindowAttributes(visible = false))
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (failBackground) {
                    trace += "newEvents"
                    throw newEventsFailure
                }
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                if (failBackground) {
                    trace += "aboutToWait"
                    throw aboutToWaitFailure
                }
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
                throw destroySurfacesFailure
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.Occluded && event.occluded) {
                    trace += "occluded"
                    throw occludedFailure
                }
            }
        }
        val lifecycle = UIKitLifecycleOrchestrator(UIKitActiveEventLoop(handler))

        lifecycle.didFinishLaunching()
        lifecycle.willResignActive()
        failBackground = true
        val thrown = assertFailsWith<IllegalStateException> {
            lifecycle.didEnterBackground()
        }

        assertSame(newEventsFailure, thrown)
        assertEquals(listOf("newEvents", "occluded", "destroySurfaces", "aboutToWait"), trace)
        assertEquals(listOf(occludedFailure, aboutToWaitFailure), thrown.suppressedExceptions)
        assertEquals(listOf(destroySurfacesFailure), occludedFailure.suppressedExceptions)
    }

    @Test
    fun reentrantLifecycleNotificationWaitsForTheCurrentIterationToFinish() {
        val trace = mutableListOf<String>()
        val createdWindows = mutableListOf<Window>()
        lateinit var lifecycle: UIKitLifecycleOrchestrator
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                trace += "canCreate"
                createdWindows += eventLoop.createWindow(WindowAttributes(visible = false))
            }

            override fun resumed(eventLoop: ActiveEventLoop) {
                trace += "resumed"
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                when (startCause) {
                    StartCause.Init -> {
                        trace += "newEvents(Init)"
                        lifecycle.willResignActive()
                    }

                    is StartCause.WaitCancelled -> trace += "newEvents(WaitCancelled)"
                    else -> error("Unexpected lifecycle cause: $startCause")
                }
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                trace += "aboutToWait"
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.Focused && !event.gained) trace += "focused-"
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        lifecycle = UIKitLifecycleOrchestrator(loop)

        try {
            lifecycle.didFinishLaunching()

            assertEquals(
                listOf(
                    "resumed",
                    "newEvents(Init)",
                    "canCreate",
                    "aboutToWait",
                    "newEvents(WaitCancelled)",
                    "focused-",
                    "suspended",
                    "aboutToWait",
                ),
                trace,
            )
        } finally {
            createdWindows.distinct().forEach(Window::close)
        }
    }

    @Test
    fun reentrantTerminationFromResumedPreventsEveryPostExitNonTerminalCallback() {
        val trace = mutableListOf<String>()
        val resumedFailure = IllegalStateException("resumed")
        lateinit var lifecycle: UIKitLifecycleOrchestrator
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                trace += "canCreate"
            }

            override fun resumed(eventLoop: ActiveEventLoop) {
                trace += "resumed"
                lifecycle.willTerminate()
                throw resumedFailure
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                trace += when (startCause) {
                    StartCause.Init -> "newEvents(Init)"
                    is StartCause.WaitCancelled -> "newEvents(WaitCancelled)"
                    else -> error("Unexpected lifecycle cause: $startCause")
                }
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                trace += "aboutToWait"
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
        val loop = UIKitActiveEventLoop(handler)
        lifecycle = UIKitLifecycleOrchestrator(loop)

        val thrown = assertFailsWith<IllegalStateException> {
            lifecycle.didFinishLaunching()
        }
        assertSame(resumedFailure, thrown)
        assertEquals(
            listOf(
                "resumed",
                "newEvents(WaitCancelled)",
                "suspended",
                "aboutToWait",
            ),
            trace,
        )
        assertTrue(loop.isExiting)

        lifecycle.didBecomeActive()
        lifecycle.willResignActive()
        lifecycle.didEnterBackground()
        lifecycle.willEnterForeground()
        lifecycle.willTerminate()

        assertEquals(
            listOf(
                "resumed",
                "newEvents(WaitCancelled)",
                "suspended",
                "aboutToWait",
            ),
            trace,
        )
    }

    @Test
    fun reentrantTerminationEvictsQueuedNonTerminalLifecycleWork() {
        val trace = mutableListOf<String>()
        lateinit var lifecycle: UIKitLifecycleOrchestrator
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                trace += "canCreate"
            }

            override fun resumed(eventLoop: ActiveEventLoop) {
                trace += "resumed"
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                when (startCause) {
                    StartCause.Init -> {
                        trace += "newEvents(Init)"
                        lifecycle.willResignActive()
                        lifecycle.willTerminate()
                    }

                    is StartCause.WaitCancelled -> trace += "newEvents(WaitCancelled)"
                    else -> error("Unexpected lifecycle cause: $startCause")
                }
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                trace += "aboutToWait"
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.Focused) trace += "focused"
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        lifecycle = UIKitLifecycleOrchestrator(loop)

        lifecycle.didFinishLaunching()

        assertEquals(
            listOf(
                "resumed",
                "newEvents(Init)",
                "newEvents(WaitCancelled)",
                "suspended",
                "aboutToWait",
            ),
            trace,
        )
        assertTrue(loop.isExiting)
    }

    @Test
    fun reentrantTerminationDuringFocusedStopsDispatchBeforeTheSecondWindow() {
        val trace = mutableListOf<String>()
        val createdIds = mutableListOf<WindowId>()
        val focusedIds = mutableListOf<WindowId>()
        val destroyedIds = mutableListOf<WindowId>()
        var terminateOnFocus = false
        lateinit var lifecycle: UIKitLifecycleOrchestrator
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                repeat(2) {
                    createdIds += eventLoop.createWindow(WindowAttributes(visible = false)).id
                }
            }

            override fun resumed(eventLoop: ActiveEventLoop) {
                if (terminateOnFocus) trace += "resumed"
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (terminateOnFocus) trace += "newEvents"
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                if (terminateOnFocus) trace += "aboutToWait"
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                when {
                    event is WindowEvent.Focused && event.gained -> {
                        focusedIds += windowId
                        trace += "focused:${windowId.value}"
                        if (terminateOnFocus && focusedIds.size == 1) lifecycle.willTerminate()
                    }

                    event == WindowEvent.Destroyed -> {
                        destroyedIds += windowId
                        trace += "destroyed:${windowId.value}"
                    }
                }
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        lifecycle = UIKitLifecycleOrchestrator(loop)

        lifecycle.didFinishLaunching()
        lifecycle.willResignActive()
        trace.clear()
        terminateOnFocus = true
        lifecycle.didBecomeActive()

        assertEquals(listOf(createdIds.first()), focusedIds)
        assertEquals(createdIds, destroyedIds)
        assertEquals(
            listOf(
                "resumed",
                "newEvents",
                "focused:${createdIds.first().value}",
                "newEvents",
                "destroySurfaces",
                "destroyed:${createdIds[0].value}",
                "destroyed:${createdIds[1].value}",
                "suspended",
                "aboutToWait",
            ),
            trace,
        )
        assertTrue(loop.isExiting)
    }

    @Test
    fun reentrantTerminationDuringOccludedStopsDispatchBeforeTheSecondWindow() {
        val trace = mutableListOf<String>()
        val createdIds = mutableListOf<WindowId>()
        val occludedIds = mutableListOf<WindowId>()
        val destroyedIds = mutableListOf<WindowId>()
        var terminateOnOcclusion = false
        lateinit var lifecycle: UIKitLifecycleOrchestrator
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                repeat(2) {
                    createdIds += eventLoop.createWindow(WindowAttributes(visible = false)).id
                }
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (terminateOnOcclusion) trace += "newEvents"
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                if (terminateOnOcclusion) trace += "aboutToWait"
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                when {
                    event is WindowEvent.Occluded && event.occluded -> {
                        occludedIds += windowId
                        trace += "occluded:${windowId.value}"
                        if (terminateOnOcclusion && occludedIds.size == 1) lifecycle.willTerminate()
                    }

                    event == WindowEvent.Destroyed -> {
                        destroyedIds += windowId
                        trace += "destroyed:${windowId.value}"
                    }
                }
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        lifecycle = UIKitLifecycleOrchestrator(loop)

        lifecycle.didFinishLaunching()
        lifecycle.willResignActive()
        trace.clear()
        terminateOnOcclusion = true
        lifecycle.didEnterBackground()

        assertEquals(listOf(createdIds.first()), occludedIds)
        assertEquals(createdIds, destroyedIds)
        assertEquals(
            listOf(
                "newEvents",
                "occluded:${createdIds.first().value}",
                "newEvents",
                "destroySurfaces",
                "destroyed:${createdIds[0].value}",
                "destroyed:${createdIds[1].value}",
                "suspended",
                "aboutToWait",
            ),
            trace,
        )
        assertTrue(loop.isExiting)
    }

    @Test
    fun resumedFailureDoesNotSkipTheRemainingStartupIteration() {
        val trace = mutableListOf<String>()
        val resumedFailure = IllegalStateException("resumed")
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                trace += "canCreate"
            }

            override fun resumed(eventLoop: ActiveEventLoop) {
                trace += "resumed"
                throw resumedFailure
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                assertEquals(StartCause.Init, startCause)
                trace += "newEvents(Init)"
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                trace += "aboutToWait"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
        val lifecycle = UIKitLifecycleOrchestrator(UIKitActiveEventLoop(handler))

        val thrown = assertFailsWith<IllegalStateException> {
            lifecycle.didFinishLaunching()
        }

        assertSame(resumedFailure, thrown)
        assertEquals(
            listOf("resumed", "newEvents(Init)", "canCreate", "aboutToWait"),
            trace,
        )
    }

    @Test
    fun surfaceDestructionOccursOncePerBackgroundCycle() {
        var destroyCount = 0
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                destroyCount += 1
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
        val loop = UIKitActiveEventLoop(handler)
        val lifecycle = UIKitLifecycleOrchestrator(loop)

        lifecycle.didFinishLaunching()
        lifecycle.willResignActive()
        lifecycle.didEnterBackground()
        lifecycle.willEnterForeground()
        lifecycle.willResignActive()
        lifecycle.didEnterBackground()
        lifecycle.willTerminate() // No duplicate destruction after background.

        assertEquals(2, destroyCount)
    }

    @Test
    fun terminationClosesEveryWindowDestroysSurfacesAndPreservesFailures() {
        val trace = mutableListOf<String>()
        val firstDestroyedFailure = IllegalStateException("first destroyed")
        val secondDestroyedFailure = IllegalArgumentException("second destroyed")
        val destroySurfacesFailure = AssertionError("destroy surfaces")
        var firstId: WindowId? = null
        var admissionFailure: Throwable? = null
        var destroySurfacesCount = 0
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
                destroySurfacesCount += 1
                throw destroySurfacesFailure
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event != WindowEvent.Destroyed) return
                if (windowId == firstId) {
                    trace += "destroyed-first"
                    admissionFailure = runCatching {
                        eventLoop.createWindow(WindowAttributes(visible = false))
                    }.exceptionOrNull()
                    throw firstDestroyedFailure
                }
                trace += "destroyed-second"
                throw secondDestroyedFailure
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        val lifecycle = UIKitLifecycleOrchestrator(loop)
        val first = loop.createWindow(WindowAttributes(visible = false))
        val second = loop.createWindow(WindowAttributes(visible = false))
        firstId = first.id
        lifecycle.didFinishLaunching()

        val thrown = assertFailsWith<AssertionError> {
            lifecycle.willTerminate()
        }

        assertSame(destroySurfacesFailure, thrown)
        assertEquals(
            listOf(firstDestroyedFailure),
            thrown.suppressedExceptions,
        )
        assertEquals(listOf(secondDestroyedFailure), firstDestroyedFailure.suppressedExceptions)
        assertEquals(
            listOf("destroySurfaces", "destroyed-first", "destroyed-second"),
            trace,
        )
        assertEquals(1, destroySurfacesCount)
        assertIs<IllegalStateException>(admissionFailure)
        assertFailsWith<IllegalStateException> {
            loop.createWindow(WindowAttributes(visible = false))
        }

        first.close()
        second.close()
        assertEquals(2, trace.count { it.startsWith("destroyed-") })
    }

    @Test
    fun terminationDisposesTheLoopSchedulerOnceEvenWhenWindowDestructionFails() {
        val operations = LifecycleSchedulerOperations()
        val destroyedFailure = IllegalStateException("destroyed")
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event == WindowEvent.Destroyed) throw destroyedFailure
            }
        }
        val loop = UIKitActiveEventLoop(handler, schedulerOperations = operations)
        val lifecycle = UIKitLifecycleOrchestrator(loop)
        loop.createWindow(WindowAttributes(visible = false))
        lifecycle.didFinishLaunching()
        loop.setControlFlow(org.graphiks.kadre.core.ControlFlow.Poll)

        val thrown = assertFailsWith<IllegalStateException> {
            lifecycle.willTerminate()
        }

        assertSame(destroyedFailure, thrown)
        assertEquals(1, operations.displayLinkDisposals)
        lifecycle.willTerminate()
        assertEquals(1, operations.displayLinkDisposals)
        assertFailsWith<IllegalStateException> {
            loop.createWindow(WindowAttributes(visible = false))
        }
    }

    @Test
    fun delegateTerminationClearsLifecycleAndRegistryAfterFailure() {
        val trace = mutableListOf<String>()
        val orchestratorFailure = IllegalStateException("orchestrator")

        val thrown = assertFailsWith<IllegalStateException> {
            runUIKitDelegateTermination(
                terminateLifecycle = {
                    trace += "terminateLifecycle"
                    throw orchestratorFailure
                },
                clearLifecycle = { trace += "clearLifecycle" },
                clearRegistry = { trace += "clearRegistry" },
            )
        }

        assertSame(orchestratorFailure, thrown)
        assertEquals(
            listOf("terminateLifecycle", "clearLifecycle", "clearRegistry"),
            trace,
        )
    }

    @Test
    fun windowCloseRunsEveryStageAndPreservesFailuresInOrder() {
        val trace = mutableListOf<String>()
        val invalidateFailure = IllegalStateException("invalidate")
        val destroyedFailure = IllegalArgumentException("destroyed")
        val hideFailure = AssertionError("hide")

        val thrown = assertFailsWith<IllegalStateException> {
            runUIKitWindowCloseStages(
                invalidateResources = {
                    trace += "invalidate"
                    throw invalidateFailure
                },
                dispatchDestroyed = {
                    trace += "destroyed"
                    throw destroyedFailure
                },
                hideAndResign = {
                    trace += "hide"
                    throw hideFailure
                },
            )
        }

        assertSame(invalidateFailure, thrown)
        assertEquals(listOf(destroyedFailure, hideFailure), thrown.suppressedExceptions)
        assertEquals(listOf("invalidate", "destroyed", "hide"), trace)
    }

    @Test
    fun closedWindowIsDestroyedOnceUnregisteredAndNeverReused() {
        val createdWindows = mutableListOf<Window>()
        val events = mutableListOf<Pair<WindowId, WindowEvent>>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                createdWindows += eventLoop.createWindow(WindowAttributes(visible = false))
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                events += windowId to event
                if (event == WindowEvent.Destroyed) {
                    (eventLoop as UIKitActiveEventLoop).dispatchWindowFocused(gained = true)
                }
            }
        }
        val loop = UIKitActiveEventLoop(handler)

        try {
            loop.recreateSurfaces { handler.canCreateSurfaces(this) }
            val closed = createdWindows.single()

            closed.close()
            closed.close()

            assertEquals(
                1,
                events.count { (id, event) -> id == closed.id && event == WindowEvent.Destroyed },
            )
            assertEquals(
                0,
                events.count { (id, event) -> id == closed.id && event is WindowEvent.Focused },
            )

            loop.recreateSurfaces { handler.canCreateSurfaces(this) }
            val replacement = createdWindows.last()

            assertNotSame(closed, replacement)
            assertNotEquals(closed.id, replacement.id)
        } finally {
            createdWindows.distinct().forEach(Window::close)
        }
    }

    @Test
    fun windowIdsAreLoopOwnedMonotonicAndNeverReuseAClosedNativeHandleIdentity() {
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
        val loop = UIKitActiveEventLoop(handler)
        val first = loop.createWindow(WindowAttributes(visible = false))
        val second = loop.createWindow(WindowAttributes(visible = false))
        first.close()
        val third = loop.createWindow(WindowAttributes(visible = false))

        try {
            assertEquals(
                listOf(WindowId(1L), WindowId(2L), WindowId(3L)),
                listOf(first.id, second.id, third.id),
            )
            var nativeAllocationRan = false
            assertFailsWith<IllegalStateException> {
                createUIKitWindowWithLogicalId(Long.MAX_VALUE) {
                    nativeAllocationRan = true
                    Any()
                }
            }
            assertFalse(nativeAllocationRan)
        } finally {
            first.close()
            second.close()
            third.close()
        }
    }

    @Test
    fun recreationCreatesEveryWindowBeyondTheExistingCount() {
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
        val loop = UIKitActiveEventLoop(handler)
        val original = loop.createWindow(WindowAttributes(visible = false))
        val recreated = mutableListOf<Window>()

        try {
            loop.recreateSurfaces {
                repeat(3) {
                    recreated += createWindow(WindowAttributes(visible = false))
                }
            }

            assertSame(original, recreated[0])
            assertNotSame(recreated[0], recreated[1])
            assertNotSame(recreated[1], recreated[2])
            assertEquals(3, recreated.map(Window::id).distinct().size)
        } finally {
            (listOf(original) + recreated).distinct().forEach(Window::close)
        }
    }

    @Test
    fun dispatchSnapshotSkipsAWindowClosedByAnEarlierCallback() {
        val events = mutableListOf<Pair<WindowId, WindowEvent>>()
        var firstId: WindowId? = null
        var secondWindow: Window? = null
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                events += windowId to event
                if (event is WindowEvent.Focused && windowId == firstId) {
                    secondWindow?.close()
                }
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        val first = loop.createWindow(WindowAttributes(visible = false))
        val second = loop.createWindow(WindowAttributes(visible = false))
        firstId = first.id
        secondWindow = second

        try {
            loop.dispatchWindowFocused(gained = true)

            assertEquals(
                1,
                events.count { (id, event) -> id == second.id && event == WindowEvent.Destroyed },
            )
            assertEquals(
                0,
                events.count { (id, event) -> id == second.id && event is WindowEvent.Focused },
            )
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun recreationSessionClearsItsCursorWhenTheCallbackFails() {
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
        val loop = UIKitActiveEventLoop(handler)
        val original = loop.createWindow(WindowAttributes(visible = false))
        var createdOutsideSession: Window? = null

        try {
            assertFailsWith<IllegalStateException> {
                loop.recreateSurfaces { error("surface callback failed") }
            }

            createdOutsideSession = loop.createWindow(WindowAttributes(visible = false))
            assertNotSame(original, createdOutsideSession)
            assertNotEquals(original.id, createdOutsideSession.id)
        } finally {
            original.close()
            createdOutsideSession?.close()
        }
    }

    @Test
    fun nestedRecreationIsRejectedWithoutChangingTheOuterCursor() {
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
        val loop = UIKitActiveEventLoop(handler)
        val first = loop.createWindow(WindowAttributes(visible = false))
        val second = loop.createWindow(WindowAttributes(visible = false))
        var nestedBlockRan = false
        var firstReuse: Window? = null
        var secondReuse: Window? = null

        try {
            loop.recreateSurfaces {
                firstReuse = createWindow(WindowAttributes(visible = false))
                assertFailsWith<IllegalStateException> {
                    recreateSurfaces {
                        nestedBlockRan = true
                    }
                }
                secondReuse = createWindow(WindowAttributes(visible = false))
            }

            assertFalse(nestedBlockRan)
            assertSame(first, firstReuse)
            assertSame(second, secondReuse)
        } finally {
            first.close()
            second.close()
            firstReuse?.close()
            secondReuse?.close()
        }
    }

    @Test
    fun terminalTeardownClosesAdmissionButOrdinaryDestroyedAllowsReplacement() {
        val createdWindows = mutableListOf<Window>()
        var terminalTeardown = false
        var createOnDestroyed = true
        var terminalCallbackFailure: Throwable? = null
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event != WindowEvent.Destroyed || !createOnDestroyed) return
                val creation = runCatching {
                    eventLoop.createWindow(WindowAttributes(visible = false))
                }
                if (terminalTeardown) {
                    terminalCallbackFailure = creation.exceptionOrNull()
                    creation.getOrNull()?.let(createdWindows::add)
                } else {
                    createdWindows += creation.getOrThrow()
                }
            }
        }
        val loop = UIKitActiveEventLoop(handler)
        val lifecycle = UIKitLifecycleOrchestrator(loop)
        createdWindows += loop.createWindow(WindowAttributes(visible = false))

        try {
            val original = createdWindows.single()
            original.close()
            val ordinaryReplacement = createdWindows.last()

            assertNotSame(original, ordinaryReplacement)
            assertNotEquals(original.id, ordinaryReplacement.id)

            terminalTeardown = true
            lifecycle.didFinishLaunching()
            lifecycle.willTerminate()
            val afterTermination = runCatching {
                loop.createWindow(WindowAttributes(visible = false))
            }
            afterTermination.getOrNull()?.let(createdWindows::add)

            assertIs<IllegalStateException>(terminalCallbackFailure)
            assertIs<IllegalStateException>(afterTermination.exceptionOrNull())
        } finally {
            createOnDestroyed = false
            createdWindows.distinct().forEach(Window::close)
        }
    }

    @Test
    fun attributeApplicationThatClosesCandidateCreatesLiveReplacement() {
        val original = Any()
        val replacement = Any()
        val liveRegistry = mutableSetOf(original)
        var reusableAvailable = true

        val selected = reuseOrCreateUIKitWindow(
            takeReusable = {
                if (reusableAvailable) {
                    reusableAvailable = false
                    original
                } else {
                    null
                }
            },
            isLive = liveRegistry::contains,
            applyAttributes = { liveRegistry.remove(it) },
            create = { replacement.also(liveRegistry::add) },
        )

        assertSame(replacement, selected)
        assertFalse(original in liveRegistry)
    }

    @Test
    fun initialWindowClosedDuringAttributeApplicationThrowsWithoutRetry() {
        val candidate = Any()
        val liveRegistry = mutableSetOf<Any>()
        var structureCount = 0
        var applyCount = 0
        var cleanupCount = 0
        var rollbackCount = 0

        assertFailsWith<IllegalStateException> {
            createRegisteredUIKitWindow(
                createStructure = {
                    structureCount += 1
                    candidate
                },
                register = liveRegistry::add,
                applyInitialAttributes = {
                    applyCount += 1
                    if (liveRegistry.remove(it)) cleanupCount += 1
                },
                isLive = liveRegistry::contains,
                rollback = {
                    rollbackCount += 1
                    liveRegistry.remove(it)
                },
            )
        }

        assertEquals(1, structureCount)
        assertEquals(1, applyCount)
        assertEquals(1, cleanupCount)
        assertEquals(0, rollbackCount)
        assertFalse(candidate in liveRegistry)
    }

    @Test
    fun initialAttributeFailureRollsBackAndPreservesTheOriginalCause() {
        val candidate = Any()
        val liveRegistry = mutableSetOf<Any>()
        val applyFailure = IllegalStateException("initial attributes")
        val rollbackFailure = IllegalArgumentException("rollback")
        var rollbackCount = 0

        val thrown = assertFailsWith<IllegalStateException> {
            createRegisteredUIKitWindow(
                createStructure = { candidate },
                register = liveRegistry::add,
                applyInitialAttributes = { throw applyFailure },
                isLive = liveRegistry::contains,
                rollback = {
                    rollbackCount += 1
                    liveRegistry.remove(it)
                    throw rollbackFailure
                },
            )
        }

        assertSame(applyFailure, thrown)
        assertEquals(listOf(rollbackFailure), thrown.suppressedExceptions)
        assertEquals(1, rollbackCount)
        assertFalse(candidate in liveRegistry)
    }

    @Test
    fun registrationFailureRollsBackTheCreatedStructureAndPreservesFailures() {
        val candidate = Any()
        val liveRegistry = mutableSetOf<Any>()
        val registrationFailure = IllegalStateException("registration")
        val rollbackFailure = IllegalArgumentException("rollback")
        var rollbackCount = 0

        val thrown = assertFailsWith<IllegalStateException> {
            createRegisteredUIKitWindow(
                createStructure = { candidate },
                register = {
                    liveRegistry.add(it)
                    throw registrationFailure
                },
                applyInitialAttributes = { error("attributes must not run") },
                isLive = liveRegistry::contains,
                rollback = {
                    rollbackCount += 1
                    liveRegistry.remove(it)
                    throw rollbackFailure
                },
            )
        }

        assertSame(registrationFailure, thrown)
        assertEquals(listOf(rollbackFailure), thrown.suppressedExceptions)
        assertEquals(1, rollbackCount)
        assertFalse(candidate in liveRegistry)
    }

    @Test
    fun schedulerRegistrationFailureRemovesAndClosesTheNativeWindowCandidate() {
        val operations = LifecycleSchedulerOperations()
        val schedulingFailure = IllegalStateException("schedule registration")
        val destroyedIds = mutableListOf<WindowId>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event == WindowEvent.Destroyed) destroyedIds += windowId
            }
        }
        val loop = UIKitActiveEventLoop(handler, schedulerOperations = operations)
        loop.setControlFlow(org.graphiks.kadre.core.ControlFlow.Poll)
        operations.startDisplayLinkFailure = schedulingFailure

        val thrown = assertFailsWith<IllegalStateException> {
            loop.createWindow(WindowAttributes(visible = false))
        }

        assertSame(schedulingFailure, thrown)
        assertEquals(1, destroyedIds.size)
        operations.startDisplayLinkFailure = null
        loop.createWindow(WindowAttributes(visible = false)).close()
    }

    @Test
    fun schedulerCloseFailureStillInvalidatesDispatchesDestroyedAndPreservesSuppressedFailures() {
        val operations = LifecycleSchedulerOperations()
        val schedulingFailure = IllegalStateException("stop display link")
        val destroyedFailure = IllegalArgumentException("destroyed")
        var destroyedCount = 0
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event == WindowEvent.Destroyed) {
                    destroyedCount += 1
                    throw destroyedFailure
                }
            }
        }
        val loop = UIKitActiveEventLoop(handler, schedulerOperations = operations)
        val window = loop.createWindow(WindowAttributes(visible = false))
        operations.stopDisplayLinkFailure = schedulingFailure

        val thrown = assertFailsWith<IllegalStateException> {
            window.close()
        }

        assertSame(schedulingFailure, thrown)
        assertEquals(listOf(destroyedFailure), thrown.suppressedExceptions)
        assertEquals(1, destroyedCount)
        window.close()
        assertEquals(1, destroyedCount)
    }

    @Test
    fun windowMutationPolicySkipsClosedWindowsAndStopsAfterSynchronousClose() {
        val mutations = mutableListOf<String>()
        var live = true

        applyUIKitWindowMutationsWhileLive(
            isLive = { live },
            mutations = arrayOf(
                { mutations += "title" },
                {
                    mutations += "visible"
                    live = false
                },
                { mutations += "resizable" },
            ),
        )
        applyUIKitWindowMutationsWhileLive(
            isLive = { live },
            mutations = arrayOf({ mutations += "already-closed" }),
        )

        assertEquals(listOf("title", "visible"), mutations)
    }
}

private class LifecycleSchedulerOperations : UIKitSchedulerOperations {
    var displayLinkDisposals = 0
    var startDisplayLinkFailure: Throwable? = null
    var stopDisplayLinkFailure: Throwable? = null
    private var onDisplayLink: (() -> Unit)? = null

    override fun nowMillis(): Long = 1_000L

    override fun startDisplayLink(onFrame: () -> Unit) {
        startDisplayLinkFailure?.let { throw it }
        onDisplayLink = onFrame
    }

    fun fireDisplayLink() = checkNotNull(onDisplayLink).invoke()

    override fun stopDisplayLink() {
        stopDisplayLinkFailure?.let { throw it }
    }

    override fun disposeDisplayLink() {
        displayLinkDisposals += 1
    }

    override fun scheduleDeadline(deadlineMillis: Long, onDeadline: () -> Unit) = Unit
}
