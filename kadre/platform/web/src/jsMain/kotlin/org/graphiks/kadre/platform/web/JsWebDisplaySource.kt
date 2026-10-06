package org.graphiks.kadre.platform.web

import kotlinx.browser.window
import org.w3c.dom.MediaQueryList
import org.w3c.dom.Window
import org.w3c.dom.events.Event

/**
 * The viewport seam of the browsing context the session runs in, read from the DOM directly.
 *
 * `current` copies the one measurement the browser exposes for the viewport — its CSS size, its
 * device pixel ratio, its colour depth — and filters what cannot be a measurement into `null`: a
 * dpr that is non-finite or not positive, sizes that are not positive. A viewport not yet laid out
 * measures nothing, and `null` is the honest report of that.
 *
 * `observe` registers the two moments the measurement may change: the `resize` of the window, and
 * the device pixel ratio through a `matchMedia("(resolution: <dpr>dppx)")` query — a query listener
 * survives exactly one ratio change, so the query is re-registered for the new ratio the moment the
 * old one fires (the standard dpr trick). The registration closes both listeners; [close] closes
 * every registration this source handed out.
 */
internal class JsWebDisplaySource(private val browsingWindow: Window = window) : WebDisplaySource {
    private val registrations = mutableListOf<AutoCloseable>()

    override fun current(): WebViewportMetrics? {
        val width = browsingWindow.innerWidth
        val height = browsingWindow.innerHeight
        val dpr = browsingWindow.devicePixelRatio
        if (width <= 0 || height <= 0 || !dpr.isFinite() || dpr <= 0.0) return null
        return WebViewportMetrics(
            widthPx = width,
            heightPx = height,
            devicePixelRatio = dpr,
            colorDepth = browsingWindow.screen.colorDepth,
        )
    }

    override fun observe(listener: () -> Unit): AutoCloseable {
        val registration = register(listener)
        registrations += registration
        return AutoCloseable {
            registration.close()
            registrations.remove(registration)
        }
    }

    override fun close() {
        val remaining = registrations.toList()
        registrations.clear()
        remaining.forEach(AutoCloseable::close)
    }

    /** One live registration: the `resize` listener plus the dpr query of the ratio in force. */
    private fun register(listener: () -> Unit): AutoCloseable {
        val resizeListener: (Event) -> Unit = { listener() }
        browsingWindow.addEventListener("resize", resizeListener)
        var resolutionQuery: MediaQueryList? = null
        var resolutionListener: ((Event) -> Unit)? = null

        fun withdrawResolutionListener() {
            val query = resolutionQuery
            val changeListener = resolutionListener
            if (query != null && changeListener != null) {
                query.removeEventListener("change", changeListener)
            }
            resolutionQuery = null
            resolutionListener = null
        }

        fun installResolutionListener() {
            val query = browsingWindow.matchMedia("(resolution: ${browsingWindow.devicePixelRatio}dppx)")
            val changeListener: (Event) -> Unit = {
                // The query that fired described the ratio that is gone; re-register for the new one
                // before the observation is delivered, or the next ratio change goes unnoticed.
                withdrawResolutionListener()
                installResolutionListener()
                listener()
            }
            query.addEventListener("change", changeListener)
            resolutionQuery = query
            resolutionListener = changeListener
        }
        installResolutionListener()

        var withdrawn = false
        return AutoCloseable {
            if (!withdrawn) {
                withdrawn = true
                browsingWindow.removeEventListener("resize", resizeListener)
                withdrawResolutionListener()
            }
        }
    }
}

internal actual fun hostDisplaySource(): WebDisplaySource = JsWebDisplaySource()
