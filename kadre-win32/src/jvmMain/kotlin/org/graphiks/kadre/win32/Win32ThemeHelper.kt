/**
 * R3 — Win32 theme helpers.
 *
 * Reads the system theme from the registry and sets the dark-mode title bar
 * via DwmSetWindowAttribute(DWMWA_USE_IMMERSIVE_DARK_MODE).
 */
package org.graphiks.kadre.win32

import org.graphiks.kadre.ffi.win32.*
import org.graphiks.kadre.core.Theme
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal typealias RegGetValueCall = (
    MemorySegment,
    MemorySegment,
    MemorySegment,
    Int,
    MemorySegment,
    MemorySegment,
    MemorySegment,
) -> Int

/**
 * Win32 theme utilities.
 */
internal object Win32ThemeHelper {

    /**
     * Reads the current system theme from the registry.
     *
     * Reads:
     *   HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\AppsUseLightTheme
     *
     * Returns [Theme.Light] when the DWORD value is non-zero (light),
     * [Theme.Dark] when zero (dark), null on failure.
     *
     * Uses the read-only Win32 `RegGetValueW` API.
     */
    fun systemThemeFromRegistry(
        getValue: RegGetValueCall = ::regGetValueW,
    ): Theme? = try {
        Arena.ofConfined().use { arena ->
            val subKey = arena.allocateWString(
                "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            )
            val valueName = arena.allocateWString("AppsUseLightTheme")
            val value = arena.allocate(ValueLayout.JAVA_INT)
            val valueSize = arena.allocate(ValueLayout.JAVA_INT)
            val dwordSize = ValueLayout.JAVA_INT.byteSize().toInt()
            valueSize.set(ValueLayout.JAVA_INT, 0L, dwordSize)

            val status = getValue(
                HKEY_CURRENT_USER,
                subKey,
                valueName,
                RRF_RT_REG_DWORD,
                MemorySegment.NULL,
                value,
                valueSize,
            )
            if (
                status != ERROR_SUCCESS ||
                valueSize.get(ValueLayout.JAVA_INT, 0L) != dwordSize
            ) {
                return@use null
            }
            themeFromAppsUseLightTheme(value.get(ValueLayout.JAVA_INT, 0L))
        }
    } catch (_: Exception) {
        null
    }

    internal fun themeFromAppsUseLightTheme(value: Int?): Theme? = when (value) {
        null -> null
        0 -> Theme.Dark
        else -> Theme.Light
    }

    /**
     * Applies or removes the DWMWA_USE_IMMERSIVE_DARK_MODE attribute on [hwnd].
     *
     * Available since Windows 11 Build 22000; silently fails on older builds.
     * Risk FFM: writes a 4-byte BOOL via a pointer.
     */
    fun setWindowDarkMode(hwnd: MemorySegment, theme: Theme?) {
        try {
            val handle = dwmSetWindowAttribute ?: return
            Arena.ofConfined().use { arena ->
                val boolPtr = arena.allocate(ValueLayout.JAVA_INT, 1L)
                boolPtr.set(ValueLayout.JAVA_INT, 0L, if (theme == Theme.Dark) 1 else 0)
                handle.invokeExact(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, boolPtr, 4) as Int
            }
        } catch (_: Throwable) {}
    }
}
