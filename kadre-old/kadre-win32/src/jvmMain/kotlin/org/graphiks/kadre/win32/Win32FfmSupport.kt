/**
 * Win32 constants and helper functions (extracted from the old Win32_h.kt).
 *
 * This file contains:
 *  - Constant values (WS_*, SW_*, HT*, RECT_*, POINT_*, …)
 *  - Helper functions (allocateWString, currentWin32ThreadId, enablePerMonitorV2DpiAwareness)
 *  - MethodHandle bindings required by the helper functions above
 *    (minimal: getCurrentThreadIdHandle, setProcessDpiAwarenessContext)
 *
 * The bulk of the Win32 function bindings live in:
 *   org.graphiks.kffi.win32.generated.win32_all_h
 */
package org.graphiks.kadre.win32

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

// ── Lazy loading of libraries ────────────────────────────────────────────────

private val kernel32: SymbolLookup? by lazy {
    try { SymbolLookup.libraryLookup("kernel32.dll", Arena.global()) } catch (_: Throwable) { null }
}

private val user32: SymbolLookup? by lazy {
    try { SymbolLookup.libraryLookup("user32.dll", Arena.global()) } catch (_: Throwable) { null }
}

private fun SymbolLookup?.downcall(name: String, desc: FunctionDescriptor): MethodHandle? {
    this ?: return null
    return this.find(name).map { Linker.nativeLinker().downcallHandle(it, desc) }.orElse(null)
}

// ── Thread helpers ───────────────────────────────────────────────────────────

val getCurrentThreadIdHandle: MethodHandle? by lazy {
    kernel32.downcall("GetCurrentThreadId", FunctionDescriptor.of(ValueLayout.JAVA_INT))
}

fun currentWin32ThreadId(): Int =
    try {
        val handle = getCurrentThreadIdHandle ?: return 0
        handle.invokeExact() as Int
    } catch (_: Throwable) {
        0
    }

// ── DPI awareness ────────────────────────────────────────────────────────────

/** DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 — Win32 pseudo-handle value. */
const val DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2: Long = -4L

val setProcessDpiAwarenessContext: MethodHandle? by lazy {
    user32.downcall("SetProcessDpiAwarenessContext", FunctionDescriptor.of(
        ValueLayout.JAVA_INT,   // BOOL
        ValueLayout.ADDRESS,    // DPI_AWARENESS_CONTEXT (pointer-sized)
    ))
}

fun enablePerMonitorV2DpiAwareness(): Boolean {
    val handle = setProcessDpiAwarenessContext ?: return false
    return try {
        val context = MemorySegment.ofAddress(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2)
        (handle.invokeExact(context) as Int) != 0
    } catch (_: Throwable) {
        false
    }
}

// ── String allocation ──────────────────────────────────────────────────────────

fun Arena.allocateWString(value: String): MemorySegment {
    val seg = this.allocate((value.length + 1) * 2L, 2L)
    for (i in value.indices) {
        seg.setAtIndex(ValueLayout.JAVA_SHORT, i.toLong(), value[i].code.toShort())
    }
    seg.setAtIndex(ValueLayout.JAVA_SHORT, value.length.toLong(), 0)
    return seg
}

// ═══════════════════════════════════════════════════════════════════════════════
// Win32 constants
// ═══════════════════════════════════════════════════════════════════════════════

// ── Window styles ─────────────────────────────────────────────────────────────

const val WS_BORDER: Int       = 0x00800000
const val WS_CAPTION: Int      = 0x00C00000
const val WS_CHILD: Int        = 0x40000000
const val WS_MINIMIZEBOX: Int  = 0x00020000
const val WS_MAXIMIZEBOX: Int  = 0x00010000
const val WS_SYSMENU: Int      = 0x00080000
const val WS_THICKFRAME: Int   = 0x00040000
const val WS_VISIBLE: Int      = 0x10000000
const val WS_POPUP: Int        = 0x80000000.toInt()
const val WS_OVERLAPPEDWINDOW: Int = 0x00CF0000

// ── Extended window styles ────────────────────────────────────────────────────

const val WS_EX_APPWINDOW: Int   = 0x00040000
const val WS_EX_TOOLWINDOW: Int  = 0x00000080
const val WS_EX_LAYERED: Int     = 0x00080000
const val WS_EX_TRANSPARENT: Int = 0x00000020

// ── Class styles ───────────────────────────────────────────────────────────────

const val CS_HREDRAW_VREDRAW: Int = 0x0003

// ── ShowWindow ─────────────────────────────────────────────────────────────────

const val SW_HIDE: Int      = 0
const val SW_SHOW: Int      = 5
const val SW_MINIMIZE: Int  = 6
const val SW_MAXIMIZE: Int  = 3
const val SW_RESTORE: Int   = 9

// ── SetWindowPos flags ─────────────────────────────────────────────────────────

const val SWP_NOSIZE: Int       = 0x0001
const val SWP_NOMOVE: Int       = 0x0002
const val SWP_NOZORDER: Int     = 0x0004
const val SWP_NOACTIVATE: Int   = 0x0010
const val SWP_FRAMECHANGED: Int = 0x0020

// ── GetWindowLong / SetWindowLong indices ──────────────────────────────────────

const val GWL_STYLE: Int   = -16
const val GWL_EXSTYLE: Int = -20

// ── HWND Z-order pseudo-handles ────────────────────────────────────────────────

val HWND_TOP: MemorySegment      = MemorySegment.ofAddress(0L)
val HWND_TOPMOST: MemorySegment  = MemorySegment.ofAddress(-1L)
val HWND_NOTOPMOST: MemorySegment = MemorySegment.ofAddress(-2L)
val HWND_BOTTOM: MemorySegment   = MemorySegment.ofAddress(1L)

// ── Window messages (WM_) — subset required by Kadre that is not in
//    kadre-win32/Win32Constants.kt (which defines WM_CLOSE, WM_SIZE, etc.
//    as internal const val). ────────────────────────────────────────────────────

const val WM_DESTROY: Int                  = 0x0002
const val WM_NCLBUTTONDOWN: Int            = 0x00A1
const val WM_SYSCOMMAND: Int               = 0x0112
const val WM_APP: Int                      = 0x8000
const val WM_KADRE_NON_CLIENT_DRAG: Int    = WM_APP + 0x4D1
const val WM_SETICON: Int                  = 0x0080

// ── TrackPopupMenu flags ───────────────────────────────────────────────────────

const val TPM_RETURNCMD: Int  = 0x0100
const val TPM_LEFTALIGN: Int  = 0x0000
const val TPM_TOPALIGN: Int   = 0x0000

// ── System-scope (SC_) command ids ─────────────────────────────────────────────

const val SC_SIZE: Int     = 0xF000
const val SC_MOVE: Int     = 0xF010
const val SC_MINIMIZE: Int = 0xF020
const val SC_MAXIMIZE: Int = 0xF030
const val SC_CLOSE: Int    = 0xF060
const val SC_RESTORE: Int  = 0xF120

// ── Menu / EnableMenuItem flags ────────────────────────────────────────────────

const val MF_BYCOMMAND: Int = 0x0000
const val MFS_ENABLED: Int  = 0x0000
const val MFS_DISABLED: Int = 0x0003

// ── Hit-test (HT) codes ────────────────────────────────────────────────────────

const val HTCAPTION: Long       = 2L
const val HTLEFT: Long          = 10L
const val HTRIGHT: Long         = 11L
const val HTTOP: Long           = 12L
const val HTTOPLEFT: Long       = 13L
const val HTTOPRIGHT: Long      = 14L
const val HTBOTTOM: Long        = 15L
const val HTBOTTOMLEFT: Long    = 16L
const val HTBOTTOMRIGHT: Long   = 17L

// ── RECT byte layout ───────────────────────────────────────────────────────────

const val RECT_SIZE: Long           = 16L
const val RECT_ALIGN: Long          = 4L
const val RECT_OFFSET_LEFT: Long    = 0L
const val RECT_OFFSET_TOP: Long     = 4L
const val RECT_OFFSET_RIGHT: Long   = 8L
const val RECT_OFFSET_BOTTOM: Long  = 12L

// ── POINT byte layout ──────────────────────────────────────────────────────────

const val POINT_SIZE: Long          = 8L
const val POINT_ALIGN: Long         = 4L
const val POINT_OFFSET_X: Long      = 0L
const val POINT_OFFSET_Y: Long      = 4L

// ── Cursor resource ids (IDC_*) ────────────────────────────────────────────────

const val IDC_ARROW: Long       = 32512L
const val IDC_IBEAM: Long       = 32513L
const val IDC_WAIT: Long        = 32514L
const val IDC_CROSS: Long       = 32515L
const val IDC_SIZEALL: Long     = 32646L
const val IDC_NO: Long          = 32648L
const val IDC_HAND: Long        = 32649L
const val IDC_APPSTARTING: Long = 32650L
const val IDC_HELP: Long        = 32651L
const val IDC_SIZENS: Long      = 32645L
const val IDC_SIZEWE: Long      = 32644L
const val IDC_SIZENWSE: Long    = 32642L
const val IDC_SIZENESW: Long    = 32643L

// ── WM_SETICON ─────────────────────────────────────────────────────────────────

const val ICON_SMALL: Long = 0L
const val ICON_BIG: Long   = 1L

// ── INPUT structure (for SendInput / keyboard injection) ──────────────────────

const val INPUT_KEYBOARD: Int = 1
const val INPUT_SIZE: Long = 40L
const val INPUT_ALIGN: Long = 8L
const val INPUT_OFFSET_TYPE: Long = 0L
const val INPUT_OFFSET_KI_WVK: Long = 8L
const val INPUT_OFFSET_KI_WSCAN: Long = 10L
const val INPUT_OFFSET_KI_DWFLAGS: Long = 12L
const val INPUT_OFFSET_KI_TIME: Long = 16L
const val INPUT_OFFSET_KI_DWEXTRAINFO: Long = 24L

const val MAPVK_VK_TO_VSC: Int = 0
const val KEYEVENTF_EXTENDEDKEY: Int = 0x0001
const val KEYEVENTF_KEYUP: Int = 0x0002

// ── DWM constants (DwmSetWindowAttribute) ─────────────────────────────────────

const val DWMWA_USE_IMMERSIVE_DARK_MODE: Int = 20
const val DWMWA_MICA: Int = 19
const val DWMWA_WINDOW_CORNER_PREFERENCE: Int = 33
const val DWMWA_BORDER_COLOR: Int = 34
const val DWMWA_CAPTION_COLOR: Int = 35
const val DWMWA_TEXT_COLOR: Int = 36
const val DWMWA_SYSTEMBACKDROP_TYPE: Int = 38

// ── DWM Blur Behind ───────────────────────────────────────────────────────────

const val DWM_BB_ENABLE: Int = 0x00000001
const val DWM_BB_BLURREGION: Int = 0x00000002

const val DWM_BLURBEHIND_SIZE: Long = 24L
const val DWM_BLURBEHIND_ALIGN: Long = 8L
const val DWM_BLURBEHIND_OFFSET_DW_FLAGS: Long = 0L
const val DWM_BLURBEHIND_OFFSET_F_ENABLE: Long = 4L
const val DWM_BLURBEHIND_OFFSET_H_RGN_BLUR: Long = 8L
const val DWM_BLURBEHIND_OFFSET_F_TRANSITION_ON_MAXIMIZED: Long = 16L

// ── FLASHWINFO layout + flags ─────────────────────────────────────────────────

const val FLASHWINFO_SIZE: Long = 32L
const val FLASHWINFO_ALIGN: Long = 8L
const val FLASHWINFO_CB_SIZE_OFFSET: Long = 0L
const val FLASHWINFO_HWND_OFFSET: Long = 8L
const val FLASHWINFO_FLAGS_OFFSET: Long = 16L
const val FLASHWINFO_COUNT_OFFSET: Long = 20L
const val FLASHWINFO_TIMEOUT_OFFSET: Long = 24L

const val FLASHW_STOP: Int = 0x00000000
const val FLASHW_CAPTION: Int = 0x00000001
const val FLASHW_TRAY: Int = 0x00000002
val FLASHW_ALL: Int = FLASHW_CAPTION or FLASHW_TRAY
const val FLASHW_TIMERNOFG: Int = 0x0000000C

// ── Window Display Affinity (WDA_*) ────────────────────────────────────────────

const val WDA_NONE: Int = 0x00000000
const val WDA_EXCLUDEFROMCAPTURE: Int = 0x00000011

// ── BITMAPINFOHEADER layout ────────────────────────────────────────────────────

const val BMIH_SIZE: Long = 40L
const val BMIH_BI_SIZE_OFFSET: Long = 0L
const val BMIH_BI_WIDTH_OFFSET: Long = 4L
const val BMIH_BI_HEIGHT_OFFSET: Long = 8L
const val BMIH_BI_PLANES_OFFSET: Long = 12L
const val BMIH_BI_BIT_COUNT_OFFSET: Long = 14L
const val BMIH_BI_COMPRESSION_OFFSET: Long = 16L
const val BMIH_BI_SIZE_IMAGE_OFFSET: Long = 20L
const val BI_RGB: Int = 0

// ── BitBlt / capture constants ─────────────────────────────────────────────────

const val SRCCOPY: Int = 0x00CC0020
const val DIB_RGB_COLORS: Int = 0
const val PW_RENDERFULLCONTENT: Int = 2

// ── GetDeviceCaps indices ──────────────────────────────────────────────────────

const val HORZRES: Int = 8
const val VERTRES: Int = 10
const val DESKTOPHORZRES: Int = 118
const val DESKTOPVERTRES: Int = 117

// ── GetSystemMetrics indices ───────────────────────────────────────────────────

const val SM_CMONITORS: Int = 80
const val SM_XVIRTUALSCREEN: Int = 76
const val SM_YVIRTUALSCREEN: Int = 77
const val SM_CXVIRTUALSCREEN: Int = 78
const val SM_CYVIRTUALSCREEN: Int = 79

// ── Process access flags ───────────────────────────────────────────────────────

const val PROCESS_QUERY_INFORMATION: Int = 0x0400
const val PROCESS_VM_READ: Int = 0x0010
const val PROCESS_QUERY_LIMITED_INFORMATION: Int = 0x1000

// ── GetWindow / enumeration ────────────────────────────────────────────────────

const val GW_CHILD: Int = 5
const val GW_HWNDNEXT: Int = 2

// ── IME constants ──────────────────────────────────────────────────────────────

const val IME_CMODE_NATIVE: Int = 0x0001
const val IME_CMODE_ALPHANUMERIC: Int = 0x0000
const val IME_SMODE_NONE: Int = 0x0000

// ── TrackMouseEvent ────────────────────────────────────────────────────────────

/**
 * TME_LEAVE — dwFlags: receive WM_MOUSELEAVE when the cursor leaves the specified window.
 */
const val TME_LEAVE: Int = 0x00000002

/**
 * Size of the TRACKMOUSEEVENT structure in bytes on Win64.
 * Layout: DWORD cbSize (4) + DWORD dwFlags (4) + HWND hwndTrack (8) + DWORD dwHoverTime (4) + padding (4) = 24.
 */
const val TRACKMOUSEEVENT_SIZE: Int = 24

// ── GestureInfo layout ────────────────────────────────────────────────────────


// ── ChangeDisplaySettings flags ────────────────────────────────────────────────

const val CDS_FULLSCREEN: Int = 4
const val CDS_TEST: Int = 2
const val CDS_SET_PRIMARY: Int = 0x10

// ── ChangeDisplaySettings return values ────────────────────────────────────────

const val DISP_CHANGE_SUCCESSFUL: Int = 0
const val DISP_CHANGE_RESTART: Int = 1
const val DISP_CHANGE_FAILED: Int = -1
const val DISP_CHANGE_BADMODE: Int = -2
const val DISP_CHANGE_BADPARAM: Int = -7

// ── DEVMODE dmFields flags ─────────────────────────────────────────────────────

const val DM_BITSPERPEL: Int = 0x00040000
const val DM_PELSWIDTH: Int = 0x00080000
const val DM_PELSHEIGHT: Int = 0x00100000
const val DM_DISPLAYFREQUENCY: Int = 0x00400000

// ── EnumDisplaySettings mode indices ───────────────────────────────────────────


// ═══════════════════════════════════════════════════════════════════════════════
// Re-exported typealiases from the generated bindings
// ═══════════════════════════════════════════════════════════════════════════════

typealias ATOM = org.graphiks.kffi.win32.generated.ATOM
typealias BOOL = org.graphiks.kffi.win32.generated.BOOL
typealias DWORD = org.graphiks.kffi.win32.generated.DWORD
typealias LONG = org.graphiks.kffi.win32.generated.LONG
typealias LONG_PTR = org.graphiks.kffi.win32.generated.LONG_PTR
typealias LPARAM = org.graphiks.kffi.win32.generated.LPARAM
typealias LRESULT = org.graphiks.kffi.win32.generated.LRESULT
typealias UINT = org.graphiks.kffi.win32.generated.UINT
typealias ULONG_PTR = org.graphiks.kffi.win32.generated.ULONG_PTR
typealias WORD = org.graphiks.kffi.win32.generated.WORD
typealias WPARAM = org.graphiks.kffi.win32.generated.WPARAM

// ── Handle typealiases (originally from Win32Types.kt) ────────────────────────
// These are plain Long in the old convention, matching win32_all_h's use of
// MemorySegment for actual handle parameters.

typealias HWND = Long
typealias HINSTANCE = Long
typealias HMODULE = Long
typealias HDC = Long
typealias HMENU = Long
typealias HICON = Long
typealias HCURSOR = Long
typealias HBRUSH = Long

