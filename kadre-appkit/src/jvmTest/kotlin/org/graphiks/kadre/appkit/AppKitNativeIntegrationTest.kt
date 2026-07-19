package org.graphiks.kadre.appkit

import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class AppKitNativeIntegrationTest {

    private fun isMacOs(): Boolean =
        System.getProperty("os.name", "").contains("Mac", ignoreCase = true) ||
        System.getProperty("os.name", "").contains("macOS", ignoreCase = true) ||
        System.getProperty("os.name", "").contains("Darwin", ignoreCase = true)

    @Test
    fun `ObjC runtime resolves on macOS`() {
        if (!isMacOs()) return
        assertNotNull(ObjCRuntime.sel("alloc"))
        assertNotNull(ObjCRuntime.getClass("NSObject"))
    }

    @Test
    fun `NSDraggingDestination selectors resolve on macOS`() {
        if (!isMacOs()) return
        assertNotNull(ObjCRuntime.sel("draggingEntered:"))
        assertNotNull(ObjCRuntime.sel("draggingUpdated:"))
        assertNotNull(ObjCRuntime.sel("draggingExited:"))
        assertNotNull(ObjCRuntime.sel("performDragOperation:"))
        assertNotNull(ObjCRuntime.sel("draggingEnded:"))
    }

    @Test
    fun `registerForDraggedTypes selector resolves on macOS`() {
        if (!isMacOs()) return
        assertNotNull(ObjCRuntime.sel("registerForDraggedTypes:"))
    }

    @Test
    fun `NSWindowDelegate selectors resolve on macOS`() {
        if (!isMacOs()) return
        assertNotNull(ObjCRuntime.sel("windowDidMiniaturize:"))
        assertNotNull(ObjCRuntime.sel("windowDidDeminiaturize:"))
    }

    @Test
    fun `NSScreen and safeArea selectors resolve on macOS`() {
        if (!isMacOs()) return
        assertNotNull(ObjCRuntime.sel("mainScreen"))
        assertNotNull(ObjCRuntime.sel("safeAreaInsets"))
        assertNotNull(ObjCRuntime.sel("contentLayoutRect"))
    }

    @Test
    fun `real AppKit lifecycle is repeatable and releases every callback registry`() {
        if (!isMacOs()) return

        repeat(2) { index ->
            val handler = NativeLifecycleHandler(index + 1)

            runApp(handler)

            assertEquals(1, handler.lifecycle.count { it == "newEvents(Init)" })
            assertEquals(EXPECTED_LIFECYCLE, handler.lifecycle)
            assertEquals(3, handler.proxyWakeCount)
            assertEquals(0, KadreAppDelegate.registeredDelegateCount())
            assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
            assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
            assertEquals(0, CFRunLoopOwner.registeredObserverCount())
            assertEquals(0, CFRunLoopOwner.registeredTimerCount())
            assertFalse(appKitRunning.get())
        }
    }

    private class NativeLifecycleHandler(
        private val run: Int,
    ) : ApplicationHandler {
        val lifecycle = mutableListOf<String>()
        var proxyWakeCount = 0
            private set

        private lateinit var window: Window
        private var redrawRequestedAfterIdle = false

        override fun resumed(eventLoop: ActiveEventLoop) {
            lifecycle += "resumed"
        }

        override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
            lifecycle += "newEvents(${startCause.label()})"
            if (startCause is StartCause.WaitCancelled && !redrawRequestedAfterIdle) {
                redrawRequestedAfterIdle = true
                window.requestRedraw()
                eventLoop.createProxy().wakeUp()
                proxyWakeCount++
            }
        }

        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
            lifecycle += "canCreateSurfaces"
            (eventLoop as AppKitEventLoop).installTerminationRequest(::stopApplicationRunLoop)
            window = eventLoop.createWindow(WindowAttributes(title = "Kadre native lifecycle $run"))
            val proxy = eventLoop.createProxy()
            repeat(2) {
                proxy.wakeUp()
                proxyWakeCount++
            }
        }

        override fun aboutToWait(eventLoop: ActiveEventLoop) {
            lifecycle += "aboutToWait"
        }

        override fun windowEvent(
            eventLoop: ActiveEventLoop,
            windowId: WindowId,
            event: WindowEvent,
        ) {
            when (event) {
                WindowEvent.RedrawRequested -> {
                    lifecycle += "RedrawRequested"
                    window.close()
                }

                WindowEvent.Destroyed -> {
                    lifecycle += "Destroyed"
                    eventLoop.exit()
                }

                else -> Unit
            }
        }

        override fun destroySurfaces(eventLoop: ActiveEventLoop) {
            lifecycle += "destroySurfaces"
        }

        override fun suspended(eventLoop: ActiveEventLoop) {
            lifecycle += "suspended"
        }

        private fun stopApplicationRunLoop() {
            val applicationClass = ObjCRuntime.getClass("NSApplication")
            val application = ObjCRuntime.msgSend(
                ValueLayout.ADDRESS,
                applicationClass,
                ObjCRuntime.sel("sharedApplication"),
            ) as MemorySegment
            ObjCRuntime.msgSend(
                null,
                application,
                ObjCRuntime.sel("stop:"),
                MemorySegment.NULL,
            )
        }
    }

    private companion object {
        val EXPECTED_LIFECYCLE = listOf(
            "resumed",
            "newEvents(Init)",
            "canCreateSurfaces",
            "aboutToWait",
            "newEvents(WaitCancelled)",
            "RedrawRequested",
            "Destroyed",
            "aboutToWait",
            "destroySurfaces",
            "suspended",
        )

        fun StartCause.label(): String = when (this) {
            StartCause.Init -> "Init"
            StartCause.Poll -> "Poll"
            is StartCause.ResumeTimeReached -> "ResumeTimeReached"
            is StartCause.WaitCancelled -> "WaitCancelled"
        }
    }
}
