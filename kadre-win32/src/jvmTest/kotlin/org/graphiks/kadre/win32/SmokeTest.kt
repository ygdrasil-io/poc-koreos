package org.graphiks.kadre.win32

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smoke tests for the Win32 type surface re-exported from kffi.
 *
 * These tests intentionally avoid native calls; they verify that the aliases
 * used by the backend remain available with the published kffi binding.
 */
class SmokeTest {

    @Test
    fun `the Win32 type aliases compile and are assignable`() {
        val dword: DWORD = -1
        val word: WORD = -1
        val uint: UINT = 0
        val ulongPtr: ULONG_PTR = 0L
        val long: LONG = -1
        val longPtr: LONG_PTR = -1L
        val lresult: LRESULT = 0L
        val wparam: WPARAM = 0L
        val lparam: LPARAM = 0L
        val bool: BOOL = 0
        val hwnd: HWND = 0L
        val hinstance: HINSTANCE = 0L
        val hmodule: HMODULE = 0L
        val hdc: HDC = 0L
        val hmenu: HMENU = 0L
        val atom: ATOM = 0

        assertEquals(-1, dword)
        assertEquals(-1, word)
        assertEquals(0, uint)
        assertEquals(0L, ulongPtr)
        assertEquals(-1, long)
        assertEquals(-1L, longPtr)
        assertEquals(0L, lresult)
        assertEquals(0L, wparam)
        assertEquals(0L, lparam)
        assertEquals(0, bool)
        assertEquals(0L, hwnd)
        assertEquals(0L, hinstance)
        assertEquals(0L, hmodule)
        assertEquals(0L, hdc)
        assertEquals(0L, hmenu)
        assertEquals(0, atom)
    }

    @Test
    fun `Win32 BOOL semantics use zero for false and one for true`() {
        val winFalse: BOOL = 0
        val winTrue: BOOL = 1

        assertEquals(0, winFalse)
        assertEquals(1, winTrue)
    }
}
