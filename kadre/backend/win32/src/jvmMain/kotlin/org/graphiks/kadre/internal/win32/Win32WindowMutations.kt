package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*

internal const val WIN32_CHROME_MASK: Int = 0x00cf0000
private const val WS_CAPTION = 0x00c00000
private const val WS_THICKFRAME = 0x00040000
private const val WS_SYSMENU = 0x00080000
private const val WS_MINMAXBOX = 0x00030000

/** One UI-owned property transaction context per peer. No borrowed native value is retained. */
internal class Win32WindowMutations(
    private val port: Win32NativeHostPort,
    private val hwnd: Long,
    private val isLive: () -> Boolean,
) {
    private var minimum: LogicalSize? = null
    private var maximum: LogicalSize? = null
    private data class WindowedRestore(val style: Int, val bounds: PhysicalRect, val minimum: LogicalSize?, val maximum: LogicalSize?)
    private var restore: WindowedRestore? = null
    private var fullscreen = false
    var nativeCommitStarted: Boolean = false
        private set

    fun initialize(spec: WindowSpec): WindowSpec {
        // The runtime has not committed a public Window yet. All native setup is hidden.
        val before = port.readWindow(hwnd)
        val pixels = spec.contentSize.toPhysical(before.scale)
        spec.minimumSize?.toPhysical(before.scale)
        spec.maximumSize?.toPhysical(before.scale, PixelRounding.Floor)
        require('\u0000' !in spec.title) { "title contains NUL" }
        minimum = spec.minimumSize
        maximum = spec.maximumSize
        port.setWindowStyle(hwnd, chromeStyle(before.style, spec.decorations, spec.systemButtons, spec.resizable))
        val outer = port.outerSizeForClient(hwnd, pixels.width, pixels.height)
        port.setWindowBounds(hwnd, PhysicalRect(spec.outerPosition ?: before.outerBounds.origin, PhysicalSize(outer.first, outer.second)), true)
        val read = port.readWindow(hwnd)
        val content = read.positiveClientSize().toLogical(read.scale)
        reconcileConstraints(content)
        return spec.copy(title = read.title, contentSize = content, minimumSize = minimum, maximumSize = maximum,
            outerPosition = read.outerBounds.origin, decorations = decorations(read.style),
            systemButtons = buttons(read.style), resizable = read.style and WS_THICKFRAME != 0)
    }

    fun apply(update: WindowUpdate, current: WindowState): WindowState {
        nativeCommitStarted = false
        check(isLive()) { "window closed before mutation" }
        val before = port.readWindow(hwnd)
        val requestedFullscreen = (update.fullscreen as? PropertyChange.Set)?.value
        if (requestedFullscreen != null) return transitionFullscreen(requestedFullscreen, before, current)
        val title = update.title.valueOr(current.title)
        require('\u0000' !in title) { "title contains NUL" }
        val size = update.contentSize.valueOr(current.contentSize)
        val nextMinimum = update.minimumSize.optionalOr(minimum)
        val nextMaximum = update.maximumSize.optionalOr(maximum)
        // Preflight every portable number before the first native or constraint write.
        val pixels = size.toPhysical(before.scale)
        nextMinimum?.toPhysical(before.scale)
        nextMaximum?.toPhysical(before.scale, PixelRounding.Floor)
        val targetDecorations = update.decorations.valueOr(current.decorations)
        val targetButtons = update.systemButtons.valueOr(current.systemButtons)
        val style = chromeStyle(before.style, targetDecorations, targetButtons, update.resizable.valueOr(current.resizable))
        val position = update.outerPosition.optionalOr(before.outerBounds.origin) ?: before.outerBounds.origin
        nativeCommitStarted = true
        minimum = nextMinimum
        maximum = nextMaximum
        if (title != before.title) { port.setWindowTitle(hwnd, title); ensureLive() }
        val frameChanged = style != before.style
        if (frameChanged) { port.setWindowStyle(hwnd, style); ensureLive() }
        if (frameChanged || update.contentSize != PropertyChange.Unchanged || update.outerPosition != PropertyChange.Unchanged ||
            update.minimumSize != PropertyChange.Unchanged || update.maximumSize != PropertyChange.Unchanged) {
            val outer = port.outerSizeForClient(hwnd, pixels.width, pixels.height)
            ensureLive()
            port.setWindowBounds(hwnd, PhysicalRect(position, PhysicalSize(outer.first, outer.second)), frameChanged)
            ensureLive()
        }
        return readState(current)
    }

    fun readState(previous: WindowState): WindowState {
        val read = port.readWindow(hwnd)
        // A minimized client rect can be zero; it must not replace the last positive metrics.
        val content = if (read.clientSize.first > 0 && read.clientSize.second > 0)
            read.positiveClientSize().toLogical(read.scale) else previous.contentSize
        reconcileConstraints(content)
        return previous.copy(title = read.title, outerBounds = read.outerBounds, contentSize = content,
            minimumSize = minimum, maximumSize = maximum, resizable = read.style and WS_THICKFRAME != 0,
            decorations = decorations(read.style), systemButtons = buttons(read.style),
            fullscreen = if (fullscreen) FullscreenMode.Borderless else FullscreenMode.Windowed)
    }

    fun trackLimits(lParam: Long) {
        val scale = port.readScaleFactor(hwnd)
        port.applyTrackLimits(hwnd, lParam, minimum?.toPhysical(scale), maximum?.toPhysical(scale, PixelRounding.Floor))
    }

    private fun reconcileConstraints(actual: LogicalSize) {
        if (minimum?.let { actual.width < it.width || actual.height < it.height } == true) minimum = null
        if (maximum?.let { actual.width > it.width || actual.height > it.height } == true) maximum = null
    }

    private fun transitionFullscreen(target: FullscreenMode, before: Win32WindowReadback, current: WindowState): WindowState {
        require(target !is FullscreenMode.Exclusive) { "exclusive fullscreen requires its display broker" }
        check(!before.minimized && !before.maximized) { "restore the native window before changing fullscreen" }
        if (target == FullscreenMode.Borderless && !fullscreen) {
            val monitor = port.monitorBounds(hwnd)
            val saved = WindowedRestore(before.style, before.outerBounds, minimum, maximum)
            restore = saved
            nativeCommitStarted = true
            minimum = null
            maximum = null
            try {
                port.setWindowStyle(hwnd, before.style and WIN32_CHROME_MASK.inv())
                ensureLive()
                port.setWindowBounds(hwnd, monitor, true)
                ensureLive()
                val actual = port.readWindow(hwnd)
                check(actual.outerBounds == monitor && actual.style and WIN32_CHROME_MASK == 0) { "native borderless readback differs from target" }
                fullscreen = true
            } catch (failure: Throwable) {
                if (isLive()) {
                    try {
                        port.setWindowStyle(hwnd, saved.style)
                        ensureLive()
                        port.setWindowBounds(hwnd, saved.bounds, true)
                        ensureLive()
                        minimum = saved.minimum
                        maximum = saved.maximum
                        val recovered = port.readWindow(hwnd)
                        check(recovered.outerBounds == saved.bounds && recovered.style == saved.style) { "fullscreen recovery readback differs" }
                        restore = null
                    } catch (recoveryFailure: Throwable) { throw Win32FullscreenRecoveryFailure(recoveryFailure) }
                }
                throw failure
            }
        } else if (target == FullscreenMode.Windowed && fullscreen) {
            val saved = checkNotNull(restore)
            nativeCommitStarted = true
            // Constraints stay suspended until the original rectangle has been restored.
            try {
                port.setWindowStyle(hwnd, saved.style)
                ensureLive()
                port.setWindowBounds(hwnd, saved.bounds, true)
                ensureLive()
                val actual = port.readWindow(hwnd)
                check(actual.outerBounds == saved.bounds && actual.style == saved.style) { "native restore readback differs" }
                minimum = saved.minimum
                maximum = saved.maximum
                fullscreen = false
                restore = null
            } catch (failure: Throwable) {
                // A failed exit cannot be represented as a completed fullscreen transition.
                throw Win32FullscreenRecoveryFailure(failure)
            }
        }
        return readState(current)
    }
    private fun ensureLive() { check(isLive()) { "window closed during mutation" } }
    private fun Win32WindowReadback.positiveClientSize() = PhysicalSize(clientSize.first, clientSize.second)
}

internal class Win32FullscreenRecoveryFailure(cause: Throwable) : IllegalStateException("native fullscreen restoration failed", cause)

private fun chromeStyle(before: Int, decorations: WindowDecorations, buttons: WindowSystemButtons, resizable: Boolean): Int {
    var style = before and WIN32_CHROME_MASK.inv()
    if (resizable) style = style or WS_THICKFRAME
    if (decorations == WindowDecorations.System) {
        style = style or WS_CAPTION
        if (buttons != WindowSystemButtons.None) style = style or WS_SYSMENU
        if (buttons == WindowSystemButtons.All) style = style or WS_MINMAXBOX
    }
    return style
}
private fun decorations(style: Int) = if (style and WS_CAPTION == WS_CAPTION) WindowDecorations.System else WindowDecorations.Borderless
private fun buttons(style: Int) = when {
    decorations(style) == WindowDecorations.Borderless || style and WS_SYSMENU == 0 -> WindowSystemButtons.None
    style and WS_MINMAXBOX != 0 -> WindowSystemButtons.All
    else -> WindowSystemButtons.CloseOnly
}
private fun <T> PropertyChange<T>.valueOr(previous: T): T = if (this is PropertyChange.Set) value else previous
private fun <T> PropertyChange<T>.optionalOr(previous: T?): T? = when (this) {
    PropertyChange.Clear -> null
    is PropertyChange.Set -> value
    PropertyChange.Unchanged -> previous
}
