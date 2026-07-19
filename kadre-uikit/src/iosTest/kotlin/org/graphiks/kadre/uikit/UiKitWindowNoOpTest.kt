package org.graphiks.kadre.uikit

import org.graphiks.kadre.core.CursorGrabMode
import org.graphiks.kadre.core.CursorIcon
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.RequestError
import org.graphiks.kadre.core.ResizeDirection
import org.graphiks.kadre.core.UserAttentionType
import org.graphiks.kadre.core.WindowRequestResult
import org.graphiks.kadre.core.WindowLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UiKitWindowNoOpTest {
    @Test
    fun `stateless UIKit capability requests are successful no-ops`() {
        assertEquals(Unit, UIKitWindowCapabilities.setCursor(CursorIcon.Crosshair))
        assertEquals(Unit, UIKitWindowCapabilities.setCursorVisible(false))
        assertEquals(Unit, UIKitWindowCapabilities.setWindowLevel(WindowLevel.AlwaysOnTop))
        assertEquals(Unit, UIKitWindowCapabilities.setTransparent(true))
        assertEquals(Unit, UIKitWindowCapabilities.setBlur(true))
        assertEquals(Unit, UIKitWindowCapabilities.setWindowIcon(null))
        assertEquals(Unit, UIKitWindowCapabilities.setCustomCursor(CustomCursor(41L)))
    }

    @Test
    fun `unsupported cursor requests return exact failures`() {
        assertEquals(
            unsupported("iOS has no system cursor"),
            UIKitWindowCapabilities.setCursorGrab(CursorGrabMode.Locked),
        )
        assertEquals(
            unsupported("iOS has no cursor to warp"),
            UIKitWindowCapabilities.setCursorPosition(PhysicalPosition(17, 29)),
        )
        assertEquals(
            unsupported("iOS has no system cursor"),
            UIKitWindowCapabilities.setCursorHittest(false),
        )
    }

    @Test
    fun `platform window requests return exact no-op and unsupported results`() {
        assertEquals(
            WindowRequestResult.Success,
            UIKitWindowCapabilities.requestUserAttention(UserAttentionType.Critical),
        )
        assertEquals(
            WindowRequestResult.Success,
            UIKitWindowCapabilities.requestUserAttention(null),
        )
        assertEquals(
            unsupported("Content protection is unsupported on iOS"),
            UIKitWindowCapabilities.setContentProtected(true),
        )
        assertEquals(
            unsupported("Window menu is unsupported on iOS"),
            UIKitWindowCapabilities.showWindowMenu(PhysicalPosition(3, 5)),
        )
        assertEquals(
            unsupported("Window dragging is unsupported on iOS"),
            UIKitWindowCapabilities.dragWindow(),
        )
        assertEquals(
            unsupported("Window resizing is unsupported on iOS"),
            UIKitWindowCapabilities.dragResizeWindow(ResizeDirection.SouthEast),
        )
    }

    @Test
    fun `unsupported state mutations preserve UIKit fixed state`() {
        UIKitWindowCapabilities.setResizable(true)
        UIKitWindowCapabilities.setMinimized(true)
        UIKitWindowCapabilities.setMaximized(true)
        UIKitWindowCapabilities.setDecorations(true)

        assertFalse(UIKitWindowCapabilities.isResizable)
        assertNull(UIKitWindowCapabilities.isMinimized)
        assertFalse(UIKitWindowCapabilities.isMaximized)
        assertFalse(UIKitWindowCapabilities.isDecorated)
    }

    private fun unsupported(message: String): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported(message))
}
