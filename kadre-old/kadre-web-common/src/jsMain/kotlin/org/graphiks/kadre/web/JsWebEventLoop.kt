/** JS browser adapters for the target-neutral web event loop. */
package org.graphiks.kadre.web

import kotlinx.browser.window
import kotlin.js.Date

private object JsBrowserSchedulingApi : BrowserSchedulingApi {
    override fun epochNowMillis(): Long = Date.now().toLong()

    override fun requestAnimationFrame(callback: () -> Unit): Int =
        window.requestAnimationFrame { callback() }

    override fun cancelAnimationFrame(id: Int) {
        window.cancelAnimationFrame(id)
    }

    override fun setTimeout(delayMillis: Int, callback: () -> Unit): Int =
        window.setTimeout(callback, delayMillis)

    override fun clearTimeout(id: Int) {
        window.clearTimeout(id)
    }
}

/** JS event loop backed by the browser's five scheduling operations. */
class JsWebEventLoop : WebEventLoop(JsBrowserSchedulingApi) {
    override fun createDomBridge(): WebDomBridge = JsWebDomBridge()
}
