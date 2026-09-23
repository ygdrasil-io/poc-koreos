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
