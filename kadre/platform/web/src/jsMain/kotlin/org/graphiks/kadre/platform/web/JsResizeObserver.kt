package org.graphiks.kadre.platform.web

import org.w3c.dom.Element
import org.w3c.dom.Window

/**
 * The browser `ResizeObserver`, which no Kotlin/JS SDK binding currently declares.
 *
 * Resolves to the global `ResizeObserver` of the browsing context.
 */
internal external class ResizeObserver(callback: (Array<dynamic>, ResizeObserver) -> Unit) {
    fun observe(target: Element)
    fun disconnect()
}

internal fun Window.deviceScaleFactor(): Double = devicePixelRatio

/**
 * Registers [callback] for the next animation frame of [browsingWindow].
 *
 * The registration belongs to the browsing context that owns the element rather than to the
 * global object, so an element in a nested document is serviced by its own frames.
 */
internal fun jsScheduleFrame(browsingWindow: Window, callback: () -> Unit): WebFrameHandle {
    val handle = browsingWindow.requestAnimationFrame { callback() }
    return WebFrameHandle { browsingWindow.cancelAnimationFrame(handle) }
}
