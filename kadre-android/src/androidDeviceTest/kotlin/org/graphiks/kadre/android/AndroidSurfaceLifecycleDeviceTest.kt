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
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidSurfaceLifecycleDeviceTest {
    @Test
    fun rawWindowHandleIsValidAcrossActivityRecreation() {
        val lifecycle = CopyOnWriteArrayList<String>()
        val handlerIndex = AtomicInteger(0)
        val observations = List(2) { CompletableFuture<HandleObservation>() }
        SurfaceLifecycleTestActivity.handlerFactory = {
            val index = handlerIndex.getAndIncrement()
            check(index in observations.indices) { "Unexpected extra Activity instance: $index" }
            object : ApplicationHandler {
                override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                    lifecycle += "canCreateSurfaces"
                    try {
                        val window = eventLoop.createWindow(WindowAttributes())
                        val handle = assertIs<RawWindowHandle.Android>(window.rawWindowHandle)
                        val surface = assertIs<Surface>(handle.surface)
                        assertTrue(surface.isValid)
                        lifecycle += "readable rawWindowHandle"
                        observations[index].complete(HandleObservation(handle = handle))
                    } catch (failure: Throwable) {
                        observations[index].complete(HandleObservation(failure = failure))
                    }
                }

                override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                    lifecycle += "destroySurfaces"
                }

                override fun windowEvent(
                    eventLoop: ActiveEventLoop,
                    windowId: WindowId,
                    event: WindowEvent,
                ) = Unit
            }
        }

        val scenario = ActivityScenario.launch(SurfaceLifecycleTestActivity::class.java)
        try {
            val initial = observations[0].get(5, TimeUnit.SECONDS)
            scenario.recreate()
            val recreated = observations[1].get(5, TimeUnit.SECONDS)

            initial.failure?.let { throw it }
            recreated.failure?.let { throw it }

            val initialSurface = assertIs<Surface>(checkNotNull(initial.handle).surface)
            val recreatedSurface = assertIs<Surface>(checkNotNull(recreated.handle).surface)
            assertFalse(initialSurface.isValid)
            assertNotSame(initialSurface, recreatedSurface)
            assertTrue(recreatedSurface.isValid)
        } finally {
            scenario.close()
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
        val handle: RawWindowHandle.Android? = null,
        val failure: Throwable? = null,
    )
}
