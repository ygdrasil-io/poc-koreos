package org.graphiks.kadre.ffi.win32

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class Win32PaintLookupPolicyTest {

    @Test
    fun `missing user32 library is reported as unavailable`() {
        val result = lookupPaintUser32 {
            throw IllegalArgumentException("user32.dll is unavailable")
        }

        assertNull(result)
    }

    @Test
    fun `unexpected runtime failure propagates`() {
        val failure = IllegalStateException("unexpected lookup failure")

        val thrown = assertFailsWith<IllegalStateException> {
            lookupPaintUser32 { throw failure }
        }

        assertSame(failure, thrown)
    }

    @Test
    fun `fatal lookup failure propagates`() {
        val failure = AssertionError("fatal lookup failure")

        val thrown = assertFailsWith<AssertionError> {
            lookupPaintUser32 { throw failure }
        }

        assertSame(failure, thrown)
    }
}
