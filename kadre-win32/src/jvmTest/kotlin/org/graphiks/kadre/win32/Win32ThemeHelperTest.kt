package org.graphiks.kadre.win32

import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.ffi.win32.ERROR_SUCCESS
import org.graphiks.kadre.ffi.win32.HKEY_CURRENT_USER
import org.graphiks.kadre.ffi.win32.RRF_RT_REG_DWORD
import org.graphiks.kadre.ffi.win32.regGetValueW
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Win32ThemeHelperTest {

    @Test
    fun `theme mapping is deterministic for missing dark and light values`() {
        assertNull(Win32ThemeHelper.themeFromAppsUseLightTheme(null))
        assertEquals(Theme.Dark, Win32ThemeHelper.themeFromAppsUseLightTheme(0))
        assertEquals(Theme.Light, Win32ThemeHelper.themeFromAppsUseLightTheme(1))
        assertEquals(Theme.Light, Win32ThemeHelper.themeFromAppsUseLightTheme(Int.MAX_VALUE))
    }

    @Test
    fun `native registry errors return no theme`() {
        assertNull(
            Win32ThemeHelper.systemThemeFromRegistry { _, _, _, _, _, _, _ -> 2 },
        )
    }

    @Test
    fun `unexpected registry byte count returns no theme`() {
        assertNull(
            Win32ThemeHelper.systemThemeFromRegistry { _, _, _, _, _, data, size ->
                data.set(ValueLayout.JAVA_INT, 0L, 1)
                size.set(ValueLayout.JAVA_INT, 0L, 2)
                ERROR_SUCCESS
            },
        )
    }

    @Test
    fun `registry call uses HKCU exact names DWORD flags and null type pointer`() {
        var called = false

        val theme = Win32ThemeHelper.systemThemeFromRegistry { root, subKey, value, flags, type, data, size ->
            called = true
            assertEquals(HKEY_CURRENT_USER.address(), root.address())
            assertEquals(
                "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                readWideString(subKey),
            )
            assertEquals("AppsUseLightTheme", readWideString(value))
            assertEquals(RRF_RT_REG_DWORD, flags)
            assertEquals(0L, type.address())
            assertEquals(4, size.get(ValueLayout.JAVA_INT, 0L))

            data.set(ValueLayout.JAVA_INT, 0L, 0)
            size.set(ValueLayout.JAVA_INT, 0L, 4)
            ERROR_SUCCESS
        }

        assertTrue(called)
        assertEquals(Theme.Dark, theme)
    }

    @Test
    fun `nonzero native DWORD maps to light theme`() {
        val theme = Win32ThemeHelper.systemThemeFromRegistry { _, _, _, _, _, data, size ->
            data.set(ValueLayout.JAVA_INT, 0L, 7)
            size.set(ValueLayout.JAVA_INT, 0L, 4)
            ERROR_SUCCESS
        }

        assertEquals(Theme.Light, theme)
    }

    @Test
    fun `real HKCU theme lookup matches a direct read`() {
        if (!isWindowsHost()) return

        val directRead = directAppsUseLightThemeRead()
        if (directRead.status == ERROR_FILE_NOT_FOUND) {
            assertNull(Win32ThemeHelper.systemThemeFromRegistry())
            return
        }

        assertEquals(ERROR_SUCCESS, directRead.status, "RegGetValueW must read the real HKCU value")
        assertEquals(4, directRead.byteCount)
        assertEquals(
            Win32ThemeHelper.themeFromAppsUseLightTheme(directRead.value),
            Win32ThemeHelper.systemThemeFromRegistry(),
        )
    }
}

private const val ERROR_FILE_NOT_FOUND = 2

private data class DirectRegistryRead(
    val status: Int,
    val byteCount: Int,
    val value: Int,
)

private fun directAppsUseLightThemeRead(): DirectRegistryRead = Arena.ofConfined().use { arena ->
    val subKey = arena.allocateWideString(
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
    )
    val valueName = arena.allocateWideString("AppsUseLightTheme")
    val data = arena.allocate(ValueLayout.JAVA_INT)
    val size = arena.allocate(ValueLayout.JAVA_INT)
    size.set(ValueLayout.JAVA_INT, 0L, 4)

    val status = regGetValueW(
        HKEY_CURRENT_USER,
        subKey,
        valueName,
        RRF_RT_REG_DWORD,
        MemorySegment.NULL,
        data,
        size,
    )
    DirectRegistryRead(
        status = status,
        byteCount = size.get(ValueLayout.JAVA_INT, 0L),
        value = data.get(ValueLayout.JAVA_INT, 0L),
    )
}

private fun Arena.allocateWideString(value: String): MemorySegment {
    val segment = allocate((value.length + 1L) * 2L, 2L)
    value.forEachIndexed { index, character ->
        segment.setAtIndex(ValueLayout.JAVA_CHAR, index.toLong(), character)
    }
    segment.setAtIndex(ValueLayout.JAVA_CHAR, value.length.toLong(), '\u0000')
    return segment
}

private fun readWideString(segment: MemorySegment): String = buildString {
    var index = 0L
    while (true) {
        val character = segment.getAtIndex(ValueLayout.JAVA_CHAR, index++)
        if (character == '\u0000') break
        append(character)
    }
}

private fun isWindowsHost(): Boolean =
    System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
