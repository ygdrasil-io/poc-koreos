package org.graphiks.kadre.android

import android.view.Surface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidSurfaceLifecycleDeviceTest {
    @Test
    fun rawWindowHandleIsValidAcrossActivityRecreation() {
        val lifecycle = CopyOnWriteArrayList<String>()
        val handlerIndex = AtomicInteger(0)
        val observations = List(2) { CompletableFuture<HandleObservation>() }
        val destructions = List(2) { CompletableFuture<HandleObservation>() }
        SurfaceLifecycleTestActivity.handlerFactory = {
            val index = handlerIndex.getAndIncrement()
            check(index in observations.indices) { "Unexpected extra Activity instance: $index" }
            var activeWindow: Window? = null
            var activeHandle: RawWindowHandle.Android? = null
            var activeSurface: Surface? = null
            object : ApplicationHandler {
                override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                    lifecycle += "canCreateSurfaces"
                    try {
                        val replacedWindow = eventLoop.createWindow(WindowAttributes())
                        val replacedHandle =
                            assertIs<RawWindowHandle.Android>(replacedWindow.rawWindowHandle)
                        val replacedSurface = assertIs<Surface>(replacedHandle.surface)
                        assertTrue(replacedSurface.isValid)

                        val currentWindow = eventLoop.createWindow(WindowAttributes())
                        val currentHandle =
                            assertIs<RawWindowHandle.Android>(currentWindow.rawWindowHandle)
                        val currentSurface = assertIs<Surface>(currentHandle.surface)
                        activeWindow = currentWindow
                        activeHandle = currentHandle
                        activeSurface = currentSurface
                        assertSame(replacedSurface, currentSurface)
                        assertTrue(currentSurface.isValid)
                        assertFailsWith<IllegalStateException> {
                            replacedWindow.rawWindowHandle
                        }
                        lifecycle += "readable rawWindowHandle"
                        observations[index].complete(
                            HandleObservation(window = currentWindow, handle = currentHandle),
                        )
                    } catch (failure: Throwable) {
                        observations[index].complete(
                            HandleObservation(
                                window = activeWindow,
                                handle = activeHandle,
                                failure = failure,
                            ),
                        )
                    }
                }

                override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                    lifecycle += "destroySurfaces"
                    try {
                        val window = checkNotNull(activeWindow)
                        val expectedSurface = checkNotNull(activeSurface)
                        val handle = assertIs<RawWindowHandle.Android>(window.rawWindowHandle)
                        val surface = assertIs<Surface>(handle.surface)
                        assertSame(expectedSurface, surface)
                        assertTrue(surface.isValid)
                        destructions[index].complete(
                            HandleObservation(window = window, handle = handle),
                        )
                    } catch (failure: Throwable) {
                        destructions[index].complete(
                            HandleObservation(
                                window = activeWindow,
                                handle = activeHandle,
                                failure = failure,
                            ),
                        )
                    }
                }

                override fun windowEvent(
                    eventLoop: ActiveEventLoop,
                    windowId: WindowId,
                    event: WindowEvent,
                ) = Unit
            }
        }

        val scenario = ActivityScenario.launch(SurfaceLifecycleTestActivity::class.java)
        lateinit var initial: HandleObservation
        lateinit var recreated: HandleObservation
        try {
            initial = observations[0].get(5, TimeUnit.SECONDS)
            scenario.recreate()
            val initialDestruction = destructions[0].get(5, TimeUnit.SECONDS)
            recreated = observations[1].get(5, TimeUnit.SECONDS)

            initial.failure?.let { throw it }
            initialDestruction.failure?.let { throw it }
            recreated.failure?.let { throw it }

            val initialSurface = assertIs<Surface>(checkNotNull(initial.handle).surface)
            val recreatedSurface = assertIs<Surface>(checkNotNull(recreated.handle).surface)
            assertFalse(initialSurface.isValid)
            assertFailsWith<IllegalStateException> {
                checkNotNull(initial.window).rawWindowHandle
            }
            assertNotSame(initialSurface, recreatedSurface)
            assertTrue(recreatedSurface.isValid)
        } finally {
            scenario.close()
        }

        val recreatedDestruction = destructions[1].get(5, TimeUnit.SECONDS)
        recreatedDestruction.failure?.let { throw it }
        assertFailsWith<IllegalStateException> {
            checkNotNull(recreated.window).rawWindowHandle
        }

        assertEquals(
            listOf(
                "canCreateSurfaces",
                "readable rawWindowHandle",
                "destroySurfaces",
                "canCreateSurfaces",
                "readable rawWindowHandle",
                "destroySurfaces",
            ),
            lifecycle,
        )
        assertNull(SurfaceLifecycleTestActivity.handlerFactory)
    }

    private data class HandleObservation(
        val window: Window? = null,
        val handle: RawWindowHandle.Android? = null,
        val failure: Throwable? = null,
    )
}
