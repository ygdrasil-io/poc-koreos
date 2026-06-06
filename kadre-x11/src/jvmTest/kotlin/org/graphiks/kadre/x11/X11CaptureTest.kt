package org.graphiks.kadre.x11

import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.capture.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class X11CaptureTest {

    @Test
    fun `bgraToRgba swaps red and blue channels`() {
        val bgra = byteArrayOf(
            0x10, 0x20, 0x30, 0xFF.toByte(),   // B=0x10, G=0x20, R=0x30, A=0xFF
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0x80.toByte(), // B=0xAA, G=0xBB, R=0xCC, A=0x80
            0x00, 0x7F, 0x7F, 0x00,             // B=0x00, G=0x7F, R=0x7F, A=0x00
        )
        val expected = byteArrayOf(
            0x30, 0x20, 0x10, 0xFF.toByte(),   // R=0x30, G=0x20, B=0x10, A=0xFF
            0xCC.toByte(), 0xBB.toByte(), 0xAA.toByte(), 0x80.toByte(), // R=0xCC, G=0xBB, B=0xAA, A=0x80
            0x7F, 0x7F, 0x00, 0x00,             // R=0x7F, G=0x7F, B=0x00, A=0x00
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
        val requested = kotlinx.coroutines.runBlocking { capturer.requestPermission() }
        assertEquals(status, requested)
    }

    @Test
    fun `enumerateDisplays returns empty list when libX11 is absent`() {
        if (libX11 != null) return
        val capturer = X11ScreenCapturer()
        val displays = kotlinx.coroutines.runBlocking { capturer.enumerateDisplays() }
        assertTrue(displays.isEmpty())
    }

    @Test
    fun `enumerateWindows returns empty list when libX11 is absent`() {
        if (libX11 != null) return
        val capturer = X11ScreenCapturer()
        val windows = kotlinx.coroutines.runBlocking { capturer.enumerateWindows() }
        assertTrue(windows.isEmpty())
    }

    @Test
    fun `createSession returns X11CaptureSession for display source`() {
        if (libX11 == null) return
        val capturer = X11ScreenCapturer()
        val session = kotlinx.coroutines.runBlocking {
            capturer.createSession(CaptureSource.Display(0L), CaptureConfig())
        }
        assertNotNull(session)
        assertTrue(session is X11CaptureSession)
        session.close()
    }

    @Test
    fun `X11CaptureSession implements AutoCloseable`() {
        val session = X11CaptureSession(
            source = CaptureSource.Display(0L),
            config = CaptureConfig(),
            displayPtr = 0L,
        )
        session.close()
    }

    @Test
    fun `captureSingle does not emit when display is null`() {
        val session = X11CaptureSession(
            source = CaptureSource.Display(0L),
            config = CaptureConfig(),
            displayPtr = 0L,
        )
        try {
            // No frame should be emitted since there's no display
            val frame = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(500L) { session.captureSingle() }
            }
            assertNull(frame, "Expected no frame with null display")
        } finally {
            session.close()
        }
    }

    @Test
    fun `XImage struct data offset constant matches LP64 layout`() {
        assertEquals(16L, XIMAGE_DATA_OFFSET)
    }

    @Test
    fun `XImage struct bytes_per_line offset constant matches LP64 layout`() {
        assertEquals(44L, XIMAGE_BYTES_PER_LINE_OFFSET)
    }

    @Test
    fun `XShmSegmentInfo struct size constant matches LP64 layout`() {
        assertEquals(24L, XSHM_SEGINFO_SIZE)
    }

    @Test
    fun `XShmSegmentInfo fields offsets match LP64 layout`() {
        assertEquals(0L, XSHM_SHMPIX_OFFSET)
        assertEquals(8L, XSHM_SHMD_OFFSET)
        assertEquals(12L, XSHM_READONLY_OFFSET)
        // shmaddr pointer at offset 16
        assertEquals(16L, XSHM_ADDR_OFFSET)
    }

    @Test
    fun `Xlib_ZPixmap constant is 2`() {
        assertEquals(2, Xlib_ZPixmap)
    }

    @Test
    fun `Xlib_AllPlanes constant is -1 as unsigned long`() {
        assertEquals(-1L, Xlib_AllPlanes)
    }

    @Test
    fun `FrameRate minimum is 1`() {
        assertEquals(1, CaptureConfig(frameRate = 1).frameRate)
    }
}
