package org.graphiks.kadre.samples.hellotriangle

import io.ygdrasil.webgpu.WGPUInstanceBackend
import org.graphiks.kadre.core.RawWindowHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class InteractiveSurfaceTargetTest {
    @Test
    fun `Win32 handle selects Primary backend with exact native addresses`() {
        val target = interactiveSurfaceTarget(
            RawWindowHandle.Win32(hwnd = 0x1234L, hinstance = 0x5678L),
        )

        val win32 = assertIs<InteractiveSurfaceTarget.Win32>(target)
        assertEquals(0x1234L, win32.hwnd)
        assertEquals(0x5678L, win32.hinstance)
        assertEquals(WGPUInstanceBackend.Primary, win32.backend)
    }

    @Test
    fun `AppKit handle preserves Metal target data`() {
        val target = interactiveSurfaceTarget(
            RawWindowHandle.AppKit(nsView = 11L, nsWindow = 22L, nsLayer = 33L),
        )

        val appKit = assertIs<InteractiveSurfaceTarget.AppKit>(target)
        assertEquals(11L, appKit.nsView)
        assertEquals(33L, appKit.nsLayer)
        assertEquals(WGPUInstanceBackend.Metal, appKit.backend)
    }

    @Test
    fun `unimplemented platform remains unsupported`() {
        val target = interactiveSurfaceTarget(
            RawWindowHandle.Xlib(window = 44L, display = 55L),
        )

        assertSame(InteractiveSurfaceTarget.Unsupported, target)
    }
}
