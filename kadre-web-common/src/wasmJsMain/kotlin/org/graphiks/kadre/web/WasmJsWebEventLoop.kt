/** wasmJs browser adapters for the target-neutral web event loop. */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.web

@JsFun("() => Date.now()")
private external fun jsEpochNowMillis(): Double

@JsFun("(fn) => requestAnimationFrame(fn)")
private external fun jsRequestAnimationFrame(callback: () -> Unit): Int

@JsFun("(id) => cancelAnimationFrame(id)")
private external fun jsCancelAnimationFrame(id: Int)

@JsFun("(delay, fn) => setTimeout(fn, delay)")
private external fun jsSetTimeout(delayMillis: Int, callback: () -> Unit): Int

@JsFun("(id) => clearTimeout(id)")
private external fun jsClearTimeout(id: Int)

private object WasmBrowserSchedulingApi : BrowserSchedulingApi {
    override fun epochNowMillis(): Long = jsEpochNowMillis().toLong()

    override fun requestAnimationFrame(callback: () -> Unit): Int =
        jsRequestAnimationFrame(callback)

    override fun cancelAnimationFrame(id: Int) {
        jsCancelAnimationFrame(id)
    }

    override fun setTimeout(delayMillis: Int, callback: () -> Unit): Int =
        jsSetTimeout(delayMillis, callback)

    override fun clearTimeout(id: Int) {
        jsClearTimeout(id)
    }
}

/** wasmJs event loop backed by the browser's five scheduling operations. */
class WasmJsWebEventLoop : WebEventLoop(WasmBrowserSchedulingApi) {
    override fun createDomBridge(): WebDomBridge = WasmJsWebDomBridge()
}
