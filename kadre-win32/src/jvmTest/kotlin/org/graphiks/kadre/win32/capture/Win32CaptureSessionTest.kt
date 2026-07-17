package org.graphiks.kadre.win32.capture

import org.graphiks.kadre.core.capture.CaptureConfig
import org.graphiks.kadre.core.capture.CaptureSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Win32CaptureSessionTest {
    @Test
    fun `unsupported frame rates are rejected synchronously with a clear Windows error`() {
        val invalidFrameRates = listOf(Int.MIN_VALUE, -1, 0, 1001, Int.MAX_VALUE)

        val failures = invalidFrameRates.associateWith(::synchronousConstructionFailure)

        failures.forEach { (frameRate, failure) ->
            val error = assertIs<IllegalArgumentException>(
                failure,
                "Windows capture construction should reject frameRate=$frameRate synchronously",
            )
            val message = assertNotNull(error.message)
            assertTrue(message.contains("Windows"), message)
            assertTrue(message.contains("frameRate"), message)
            assertTrue(message.contains("1..1000"), message)
            assertTrue(message.contains(frameRate.toString()), message)
        }
    }

    @Test
    fun `invalid frame rate wins before window capture can start`() {
        val failure = synchronousConstructionFailure(
            frameRate = 0,
            source = CaptureSource.Window(0L),
            hwnd = 0L,
        )

        assertIs<IllegalArgumentException>(failure)
    }

    @Test
    fun `supported boundary frame rates produce the expected millisecond periods`() {
        assertEquals(1000L, win32CapturePeriodMillis(1))
        assertEquals(1L, win32CapturePeriodMillis(1000))
    }

    @Test
    fun `every supported frame rate produces a positive millisecond period`() {
        assertTrue((1..1000).all { win32CapturePeriodMillis(it) >= 1L })
    }

    private fun synchronousConstructionFailure(
        frameRate: Int,
        source: CaptureSource = CaptureSource.Display(0L),
        hwnd: Long? = null,
    ): Throwable? {
        var session: Win32CaptureSession? = null
        return try {
            session = Win32CaptureSession(
                source = source,
                config = CaptureConfig(frameRate = frameRate),
                hwnd = hwnd,
                rect = null,
            )
            null
        } catch (failure: Throwable) {
            failure
        } finally {
            session?.close()
        }
    }
}
