package org.graphiks.kadre.web

import org.graphiks.kadre.core.CursorGrabMode
import org.graphiks.kadre.core.RequestError
import org.graphiks.kadre.core.WindowRequestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class CursorRecordingBridge : WebDomBridge {
    override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null
    val pointerLockRequests = mutableListOf<String>()
    var exitPointerLockCalls = 0
    val pointerEventsCalls = mutableListOf<Pair<String, String>>()

    override fun attach(targetElementId: String) {}
    override fun detach() {}

    override fun requestPointerLock(canvasId: String) {
        pointerLockRequests += canvasId
    }

    override fun exitPointerLock() {
        exitPointerLockCalls += 1
    }

    override fun setPointerEvents(canvasId: String, pointerEventsValue: String) {
        pointerEventsCalls += canvasId to pointerEventsValue
    }
}

class WebWindowCursorTest {
    @Test
    fun `setCursorGrab Locked requests pointer lock and reports success`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorGrab(CursorGrabMode.Locked)

        assertEquals(WindowRequestResult.Success, result)
        assertEquals(listOf("canvas"), bridge.pointerLockRequests)
    }

    @Test
    fun `setCursorGrab None exits pointer lock and reports success`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorGrab(CursorGrabMode.None)

        assertEquals(WindowRequestResult.Success, result)
        assertEquals(1, bridge.exitPointerLockCalls)
    }

    @Test
    fun `setCursorGrab Confined remains unsupported on Web`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorGrab(CursorGrabMode.Confined)

        val failure = assertIs<WindowRequestResult.Failure>(result)
        assertIs<RequestError.Unsupported>(failure.error)
        assertEquals(emptyList(), bridge.pointerLockRequests)
    }

    @Test
    fun `setCursorHittest false disables pointer events and reports success`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorHittest(false)

        assertEquals(WindowRequestResult.Success, result)
        assertEquals(listOf("canvas" to "none"), bridge.pointerEventsCalls)
    }

    @Test
    fun `setCursorHittest true restores pointer events and reports success`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorHittest(true)

        assertEquals(WindowRequestResult.Success, result)
        assertEquals(listOf("canvas" to "auto"), bridge.pointerEventsCalls)
    }
}
