package org.graphiks.kadre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ImeCapabilitiesTest {

    @Test
    fun `ImeCapabilities default constructor has sensible defaults`() {
        val caps = ImeCapabilities()
        assertFalse(caps.enabled)
        assertEquals(emptyList(), caps.purposes)
        assertEquals(emptySet(), caps.capabilities)
    }

    @Test
    fun `ImeCapabilities can be constructed with specific values`() {
        val caps = ImeCapabilities(
            enabled = true,
            purposes = listOf(ImePurpose.Normal, ImePurpose.Password),
            capabilities = setOf(ImeCapability.Composition),
        )
        assertTrue(caps.enabled)
        assertEquals(listOf(ImePurpose.Normal, ImePurpose.Password), caps.purposes)
        assertEquals(setOf(ImeCapability.Composition), caps.capabilities)
    }

    @Test
    fun `ImeCapability enum contains expected entries`() {
        val entries = ImeCapability.entries.map { it.name }.toSet()
        assertEquals(
            setOf("Composition", "Provisional", "Learning", "Password", "Terminal"),
            entries,
        )
    }

    @Test
    fun `Window default imeCapabilities returns disabled with empty purposes and capabilities`() {
        val window = TestWindow()
        val caps = window.imeCapabilities()
        assertFalse(caps.enabled)
        assertEquals(emptyList(), caps.purposes)
        assertEquals(emptySet(), caps.capabilities)
    }

    @Test
    fun `Window imeCapabilities can be overridden by a backend`() {
        val window = OverriddenImeWindow()
        val caps = window.imeCapabilities()
        assertTrue(caps.enabled)
        assertEquals(listOf(ImePurpose.Normal, ImePurpose.Password), caps.purposes)
        assertEquals(setOf(ImeCapability.Composition), caps.capabilities)
    }

    @Test
    fun `ImeCapabilities data class equality works`() {
        val a = ImeCapabilities(enabled = true, purposes = listOf(ImePurpose.Normal), capabilities = setOf(ImeCapability.Composition))
        val b = ImeCapabilities(enabled = true, purposes = listOf(ImePurpose.Normal), capabilities = setOf(ImeCapability.Composition))
        assertEquals(a, b)
    }

    private class TestWindow : Window {
        override val id: WindowId = WindowId(1L)
        override val rawWindowHandle: RawWindowHandle = RawWindowHandle.Web(canvasElementId = "test")
        override val rawDisplayHandle: RawDisplayHandle = RawDisplayHandle.Web
        override val title: String = "test"
        override val innerSize: PhysicalSize<Int> = PhysicalSize(800, 600)
        override val outerSize: PhysicalSize<Int> = innerSize
        override val scaleFactor: Double = 1.0
        override val isVisible: Boolean? = true
        override val isResizable: Boolean = true
        override val isMinimized: Boolean? = false
        override val isMaximized: Boolean = false
        override val isDecorated: Boolean = true
        override val outerPosition: PhysicalPosition<Int> = PhysicalPosition(0, 0)
        override val fullscreen: Fullscreen? = null
        override val theme: Theme? = null
        override fun requestRedraw() {}
        override fun setTitle(title: String) {}
        override fun setVisible(visible: Boolean) {}
        override fun close() {}
        override fun setResizable(resizable: Boolean) {}
        override fun setMinimized(minimized: Boolean) {}
        override fun setMaximized(maximized: Boolean) {}
        override fun setDecorations(decorated: Boolean) {}
        override fun setMinSurfaceSize(size: PhysicalSize<Int>?) {}
        override fun setMaxSurfaceSize(size: PhysicalSize<Int>?) {}
        override fun setOuterPosition(position: PhysicalPosition<Int>) {}
        override fun prePresentNotify() {}
        override fun currentMonitor(): MonitorHandle? = null
        override fun setFullscreen(fullscreen: Fullscreen?) {}
        override fun setCursor(cursor: CursorIcon) {}
        override fun setCursorVisible(visible: Boolean) {}
        override fun setCursorGrab(mode: CursorGrabMode): WindowRequestResult =
            WindowRequestResult.Failure(RequestError.Unsupported("test"))
        override fun setCursorPosition(position: PhysicalPosition<Int>): WindowRequestResult =
            WindowRequestResult.Failure(RequestError.Unsupported("test"))
        override fun setCursorHittest(hittest: Boolean): WindowRequestResult =
            WindowRequestResult.Failure(RequestError.Unsupported("test"))
        override fun setTheme(theme: Theme?) {}
        override fun setWindowLevel(level: WindowLevel) {}
        override fun setTransparent(transparent: Boolean) {}
        override fun setBlur(blur: Boolean) {}
        override fun setWindowIcon(icon: Icon?) {}
        override fun resetDeadKeys() {}
    }

    private class OverriddenImeWindow : Window {
        override val id: WindowId = WindowId(2L)
        override val rawWindowHandle: RawWindowHandle = RawWindowHandle.Web(canvasElementId = "override")
        override val rawDisplayHandle: RawDisplayHandle = RawDisplayHandle.Web
        override val title: String = "override-ime"
        override val innerSize: PhysicalSize<Int> = PhysicalSize(800, 600)
        override val outerSize: PhysicalSize<Int> = innerSize
        override val scaleFactor: Double = 1.0
        override val isVisible: Boolean? = true
        override val isResizable: Boolean = true
        override val isMinimized: Boolean? = false
        override val isMaximized: Boolean = false
        override val isDecorated: Boolean = true
        override val outerPosition: PhysicalPosition<Int> = PhysicalPosition(0, 0)
        override val fullscreen: Fullscreen? = null
        override val theme: Theme? = null
        override fun requestRedraw() {}
        override fun setTitle(title: String) {}
        override fun setVisible(visible: Boolean) {}
        override fun close() {}
        override fun setResizable(resizable: Boolean) {}
        override fun setMinimized(minimized: Boolean) {}
        override fun setMaximized(maximized: Boolean) {}
        override fun setDecorations(decorated: Boolean) {}
        override fun setMinSurfaceSize(size: PhysicalSize<Int>?) {}
        override fun setMaxSurfaceSize(size: PhysicalSize<Int>?) {}
        override fun setOuterPosition(position: PhysicalPosition<Int>) {}
        override fun prePresentNotify() {}
        override fun currentMonitor(): MonitorHandle? = null
        override fun setFullscreen(fullscreen: Fullscreen?) {}
        override fun setCursor(cursor: CursorIcon) {}
        override fun setCursorVisible(visible: Boolean) {}
        override fun setCursorGrab(mode: CursorGrabMode): WindowRequestResult =
            WindowRequestResult.Failure(RequestError.Unsupported("test"))
        override fun setCursorPosition(position: PhysicalPosition<Int>): WindowRequestResult =
            WindowRequestResult.Failure(RequestError.Unsupported("test"))
        override fun setCursorHittest(hittest: Boolean): WindowRequestResult =
            WindowRequestResult.Failure(RequestError.Unsupported("test"))
        override fun setTheme(theme: Theme?) {}
        override fun setWindowLevel(level: WindowLevel) {}
        override fun setTransparent(transparent: Boolean) {}
        override fun setBlur(blur: Boolean) {}
        override fun setWindowIcon(icon: Icon?) {}
        override fun resetDeadKeys() {}
        override fun imeCapabilities(): ImeCapabilities = ImeCapabilities(
            enabled = true,
            purposes = listOf(ImePurpose.Normal, ImePurpose.Password),
            capabilities = setOf(ImeCapability.Composition),
        )
    }
}
