package org.graphiks.kadre.x11

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.graphiks.kadre.core.PhysicalSize
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.graphiks.kadre.core.capture.*
import org.graphiks.kadre.x11.binding.*
import org.graphiks.kadre.x11.binding.libX11
import org.graphiks.kadre.x11.binding.capture.*
import org.graphiks.kffi.x11.generated.XShmSegmentInfoCompat
import org.graphiks.kadre.x11.capture.X11CaptureSession
import org.graphiks.kadre.x11.capture.X11ScreenCapturer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class X11CaptureTest {

    @Test
    fun `bgraToRgba swaps red and blue channels`() {
        val bgra = byteArrayOf(
            0x10, 0x20, 0x30, 0xFF.toByte(),
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0x80.toByte(),
            0x00, 0x7F, 0x7F, 0x00,
        )
        val expected = byteArrayOf(
            0x30, 0x20, 0x10, 0xFF.toByte(),
            0xCC.toByte(), 0xBB.toByte(), 0xAA.toByte(), 0x80.toByte(),
            0x7F, 0x7F, 0x00, 0x00,
        )
        val result = bgraToRgba(bgra)
        assertTrue(expected.contentEquals(result), "BGRA→RGBA conversion should swap R and B")
    }

    @Test
    fun `bgraToRgba handles empty array`() {
        val result = bgraToRgba(ByteArray(0))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `bgraToRgba handles single pixel`() {
        val bgra = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        val expected = byteArrayOf(0x03, 0x02, 0x01, 0xFF.toByte())
        assertTrue(expected.contentEquals(bgraToRgba(bgra)))
    }

    @Test
    fun `X11ScreenCapturer returns Granted for permission`() {
        val capturer = X11ScreenCapturer()
        assertEquals(CapturePermission.Granted, capturer.permissionStatus())
    }

    @Test
    fun `X11ScreenCapturer permissionStatus matches requestPermission`() {
        val capturer = X11ScreenCapturer()
        val status = capturer.permissionStatus()
        val requested = runBlocking { capturer.requestPermission() }
        assertEquals(status, requested)
    }

    @Test
    fun `enumerateDisplays returns empty list when libX11 is absent`() {
        if (libX11 != null) return
        val capturer = X11ScreenCapturer()
        val displays = runBlocking { capturer.enumerateDisplays() }
        assertTrue(displays.isEmpty())
    }

    @Test
    fun `enumerateWindows returns empty list when libX11 is absent`() {
        if (libX11 != null) return
        val capturer = X11ScreenCapturer()
        val windows = runBlocking { capturer.enumerateWindows() }
        assertTrue(windows.isEmpty())
    }

    @Test
    fun `FrameRate minimum is 1`() {
        assertEquals(1, CaptureConfig(frameRate = 1).frameRate)
    }
}
