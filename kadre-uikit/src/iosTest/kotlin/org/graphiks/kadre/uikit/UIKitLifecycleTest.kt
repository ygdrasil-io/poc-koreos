package org.graphiks.kadre.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.Fullscreen
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId

class UIKitLifecycleTest {

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
    fun foregroundSurfaceRecreationReusesTheWindowInLifecycleOrder() {
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
            lifecycle.didBecomeActive()
            lifecycle.willResignActive()
            lifecycle.didEnterBackground()
            lifecycle.willEnterForeground()
            lifecycle.didBecomeActive()

            assertSame(createdWindows[0], createdWindows[1])
            assertEquals(createdWindows[0].id, createdWindows[1].id)
            assertEquals("window-2", createdWindows[1].title)
            assertEquals(
                listOf(
                    "canCreate",
                    "resumed",
                    "focused+",
                    "focused-",
                    "suspended",
                    "occluded+",
                    "destroySurfaces",
                    "occluded-",
                    "canCreate",
                    "resumed",
                    "focused+",
                ),
                trace,
            )
        } finally {
            createdWindows.distinct().forEach(Window::close)
        }
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
        lifecycle.didEnterBackground()
        lifecycle.willEnterForeground()
        lifecycle.didEnterBackground()
        lifecycle.willTerminate() // No duplicate destruction after background.

        assertEquals(2, destroyCount)
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
}
