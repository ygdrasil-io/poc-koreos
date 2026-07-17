package org.graphiks.kadre.android

import android.view.Surface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidSurfaceLifecycleDeviceTest {
    @Test
    fun rawWindowHandleIsValidDuringCanCreateSurfaces() {
        val result = CompletableFuture<Unit>()
        SurfaceLifecycleTestActivity.handlerFactory = {
            object : ApplicationHandler {
                override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                    try {
                        val window = eventLoop.createWindow(WindowAttributes())
                        val handle = assertIs<RawWindowHandle.Android>(window.rawWindowHandle)
                        val surface = assertIs<Surface>(handle.surface)
                        assertTrue(surface.isValid)
                        result.complete(Unit)
                    } catch (failure: Throwable) {
                        result.completeExceptionally(failure)
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
        try {
            try {
                result.get(5, TimeUnit.SECONDS)
            } catch (failure: ExecutionException) {
                throw failure.cause ?: failure
            }
        } finally {
            scenario.close()
        }
    }
}
