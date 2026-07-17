package org.graphiks.kadre.win32

import org.graphiks.kadre.core.WindowEvent
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KadreWndProcPaintTest {

    @Test
    fun `paint lifecycle begins before redraw and ends afterwards`() {
        val calls = mutableListOf<String>()
        var paintStructAddress: Long? = null

        val result = KadreWndProc.dispatchPaint(
            hwnd = TEST_PAINT_HWND,
            beginPaint = { hwnd, paintStruct ->
                assertEquals(TEST_PAINT_HWND, hwnd.address())
                assertTrue(paintStruct.byteSize() > 0L)
                paintStructAddress = paintStruct.address()
                calls += "begin"
                MemorySegment.NULL
            },
            emitEvent = { hwnd, event ->
                assertEquals(TEST_PAINT_HWND, hwnd)
                assertIs<WindowEvent.RedrawRequested>(event)
                calls += "emit"
            },
            endPaint = { hwnd, paintStruct ->
                assertEquals(TEST_PAINT_HWND, hwnd.address())
                assertTrue(paintStruct.byteSize() > 0L)
                assertEquals(paintStructAddress, paintStruct.address())
                calls += "end"
                1
            },
        )

        assertEquals(0L, result)
        assertEquals(listOf("begin", "emit", "end"), calls)
    }

    @Test
    fun `paint lifecycle ends when redraw delivery throws`() {
        val calls = mutableListOf<String>()
        val deliveryFailure = IllegalStateException("redraw delivery failed")

        val thrown = assertFailsWith<IllegalStateException> {
            KadreWndProc.dispatchPaint(
                hwnd = TEST_PAINT_HWND,
                beginPaint = { _, _ ->
                    calls += "begin"
                    MemorySegment.NULL
                },
                emitEvent = { _, _ ->
                    calls += "emit"
                    throw deliveryFailure
                },
                endPaint = { _, _ ->
                    calls += "end"
                    1
                },
            )
        }

        assertSame(deliveryFailure, thrown)
        assertEquals(listOf("begin", "emit", "end"), calls)
    }
}

private const val TEST_PAINT_HWND: Long = 0x1234_5678L
