package org.graphiks.kadre.win32

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Win32LifecycleTest {

    @BeforeTest
    fun isolateGlobals() {
        KadreWndProc.uninstall()
        win32Running.set(false)
    }

    @AfterTest
    fun cleanupGlobals() {
        KadreWndProc.uninstall()
        win32Running.set(false)
    }

    @Test
    fun `resumed failure stays primary while both cleanups are attempted`() {
        val primary = LifecycleFailure("resumed")
        val suspendedFailure = LifecycleFailure("suspended")
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(
            resumedFailure = primary,
            suspendedFailure = suspendedFailure,
            destroySurfacesFailure = destroyFailure,
        )

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ -> error("loop must not run") })
        }

        assertSame(primary, thrown)
        assertEquals(listOf(suspendedFailure, destroyFailure), thrown.suppressed.toList())
        assertEquals(listOf("resumed", "suspended", "destroySurfaces"), handler.calls)
        assertCleanAndReusable(handler)
    }

    @Test
    fun `canCreateSurfaces failure stays primary while both cleanups are attempted`() {
        val primary = LifecycleFailure("canCreateSurfaces")
        val suspendedFailure = LifecycleFailure("suspended")
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(
            canCreateSurfacesFailure = primary,
            suspendedFailure = suspendedFailure,
            destroySurfacesFailure = destroyFailure,
        )

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ -> error("loop must not run") })
        }

        assertSame(primary, thrown)
        assertEquals(listOf(suspendedFailure, destroyFailure), thrown.suppressed.toList())
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "suspended", "destroySurfaces"),
            handler.calls,
        )
        assertCleanAndReusable(handler)
    }

    @Test
    fun `message loop failure stays primary while both cleanups are attempted`() {
        val primary = LifecycleFailure("messageLoop")
        val suspendedFailure = LifecycleFailure("suspended")
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(
            suspendedFailure = suspendedFailure,
            destroySurfacesFailure = destroyFailure,
        )

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ ->
                handler.calls += "messageLoop"
                throw primary
            })
        }

        assertSame(primary, thrown)
        assertEquals(listOf(suspendedFailure, destroyFailure), thrown.suppressed.toList())
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "messageLoop", "suspended", "destroySurfaces"),
            handler.calls,
        )
        assertCleanAndReusable(handler)
    }

    @Test
    fun `destroySurfaces is attempted after suspended failure and becomes suppressed`() {
        val suspendedFailure = LifecycleFailure("suspended")
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(
            suspendedFailure = suspendedFailure,
            destroySurfacesFailure = destroyFailure,
        )

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ -> handler.calls += "messageLoop" })
        }

        assertSame(suspendedFailure, thrown)
        assertEquals(listOf(destroyFailure), thrown.suppressed.toList())
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "messageLoop", "suspended", "destroySurfaces"),
            handler.calls,
        )
        assertCleanAndReusable(handler)
    }

    @Test
    fun `destroySurfaces failure propagates after successful suspended`() {
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(destroySurfacesFailure = destroyFailure)

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ -> handler.calls += "messageLoop" })
        }

        assertSame(destroyFailure, thrown)
        assertEquals(emptyList(), thrown.suppressed.toList())
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "messageLoop", "suspended", "destroySurfaces"),
            handler.calls,
        )
        assertCleanAndReusable(handler)
    }

    private fun assertCleanAndReusable(handler: LifecycleRecordingHandler) {
        assertFalse(win32Running.get(), "win32Running must reset after every path")
        handler.assertLifecycleCallbacksAtMostOnce()

        val eventsBeforeProbe = handler.windowEventCalls
        KadreWndProc.dispatch(0x4321L, WM_SIZE, 0L, 0L)
        assertEquals(eventsBeforeProbe, handler.windowEventCalls, "WndProc handler must be uninstalled")

        val secondHandler = LifecycleRecordingHandler()
        runApp(secondHandler, messageLoop = { _, _ -> secondHandler.calls += "messageLoop" })
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "messageLoop", "suspended", "destroySurfaces"),
            secondHandler.calls,
        )
        assertFalse(win32Running.get(), "second runApp must also reset the lock")
    }
}

private class LifecycleFailure(message: String) : RuntimeException(message)

private class LifecycleRecordingHandler(
    private val resumedFailure: Throwable? = null,
    private val canCreateSurfacesFailure: Throwable? = null,
    private val suspendedFailure: Throwable? = null,
    private val destroySurfacesFailure: Throwable? = null,
) : ApplicationHandler {
    val calls = mutableListOf<String>()
    var windowEventCalls = 0
        private set

    override fun resumed(eventLoop: ActiveEventLoop) {
        calls += "resumed"
        resumedFailure?.let { throw it }
    }

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        calls += "canCreateSurfaces"
        canCreateSurfacesFailure?.let { throw it }
    }

    override fun suspended(eventLoop: ActiveEventLoop) {
        calls += "suspended"
        suspendedFailure?.let { throw it }
    }

    override fun destroySurfaces(eventLoop: ActiveEventLoop) {
        calls += "destroySurfaces"
        destroySurfacesFailure?.let { throw it }
    }

    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
        windowEventCalls++
    }

    fun assertLifecycleCallbacksAtMostOnce() {
        listOf("resumed", "canCreateSurfaces", "suspended", "destroySurfaces").forEach { callback ->
            assertTrue(calls.count { it == callback } <= 1, "$callback must be invoked at most once")
        }
    }
}
