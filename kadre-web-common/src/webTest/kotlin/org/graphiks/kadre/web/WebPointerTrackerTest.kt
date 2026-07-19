package org.graphiks.kadre.web

import org.graphiks.kadre.core.FingerId
import org.graphiks.kadre.core.PointerKind
import org.graphiks.kadre.core.PointerSource
import org.graphiks.kadre.core.TabletToolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebPointerTrackerTest {
    @Test
    fun `first active touch is primary regardless of DOM ID`() {
        val tracker = WebPointerTracker()

        val touch = tracker.onStart(pointerId = 42L, pointerType = "touch", domPrimary = false)

        assertEquals(42L, touch.pointerId)
        assertTrue(touch.primary)
        assertEquals(PointerSource.Touch(FingerId(42L)), touch.source)
        assertEquals(PointerKind.Touch, touch.kind)
    }

    @Test
    fun `second touch is not primary`() {
        val tracker = WebPointerTracker()
        tracker.onStart(42L, "touch", domPrimary = false)

        assertFalse(tracker.onStart(7L, "touch", domPrimary = false).primary)
    }

    @Test
    fun `oldest remaining touch becomes primary after primary end`() {
        val tracker = WebPointerTracker()
        tracker.onStart(42L, "touch", domPrimary = false)
        tracker.onStart(7L, "touch", domPrimary = false)
        tracker.onStart(9L, "touch", domPrimary = false)

        assertTrue(tracker.onEnd(42L, "touch", domPrimary = false).primary)
        assertTrue(tracker.onMove(7L, "touch", domPrimary = false).primary)
        assertFalse(tracker.onMove(9L, "touch", domPrimary = false).primary)
    }

    @Test
    fun `mouse is always primary and does not alter touch state`() {
        val tracker = WebPointerTracker()
        tracker.onStart(42L, "touch", domPrimary = false)

        val mouse = tracker.onStart(1L, "mouse", domPrimary = false)

        assertTrue(mouse.primary)
        assertEquals(PointerSource.Mouse, mouse.source)
        assertEquals(PointerKind.Mouse, mouse.kind)
        assertTrue(tracker.onMove(42L, "touch", domPrimary = false).primary)
    }

    @Test
    fun `cancel clears only the cancelled touch`() {
        val tracker = WebPointerTracker()
        tracker.onStart(42L, "touch", domPrimary = false)
        tracker.onStart(7L, "touch", domPrimary = false)

        assertFalse(tracker.onCancel(7L, "touch", domPrimary = false).primary)
        assertFalse(tracker.onStart(9L, "touch", domPrimary = false).primary)
        assertTrue(tracker.onMove(42L, "touch", domPrimary = false).primary)
    }

    @Test
    fun `leave clears the leaving touch and promotes the oldest remaining touch`() {
        val tracker = WebPointerTracker()
        tracker.onStart(42L, "touch", domPrimary = false)
        tracker.onStart(7L, "touch", domPrimary = false)

        assertTrue(tracker.onLeave(42L, "touch", domPrimary = false).primary)
        assertTrue(tracker.onMove(7L, "touch", domPrimary = false).primary)
    }

    @Test
    fun `close clears all touch state`() {
        val tracker = WebPointerTracker()
        tracker.onStart(42L, "touch", domPrimary = false)
        tracker.onStart(7L, "touch", domPrimary = false)

        tracker.close()

        assertTrue(tracker.onStart(9L, "touch", domPrimary = false).primary)
    }

    @Test
    fun `pen and unknown pointer types map without changing touch state`() {
        val tracker = WebPointerTracker()
        tracker.onStart(42L, "touch", domPrimary = false)

        val pen = tracker.onMove(8L, "pen", domPrimary = false)
        val unknown = tracker.onMove(11L, "something-else", domPrimary = false)

        assertFalse(pen.primary)
        assertEquals(PointerSource.TabletTool(TabletToolKind.Pen), pen.source)
        assertEquals(PointerKind.TabletTool, pen.kind)
        assertFalse(unknown.primary)
        assertEquals(PointerSource.Unknown, unknown.source)
        assertEquals(PointerKind.Unknown, unknown.kind)
        assertTrue(tracker.onMove(42L, "touch", domPrimary = false).primary)
    }
}
