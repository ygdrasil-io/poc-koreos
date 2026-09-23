@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/**
 * The browser `ResizeObserver` as seen from Wasm.
 *
 * The callback crosses the boundary through `@JsFun`, the only place where a Kotlin function type
 * becomes a JavaScript function: a raw `js(...)` snippet sees neither a Kotlin function value nor
 * `JsReference.get()`, which is a compiler intrinsic rather than a JavaScript member.
 */
internal external interface WasmResizeObserver : JsAny {
    fun observe(target: JsAny)
    fun disconnect()
}

@JsFun("(callback) => new ResizeObserver(() => callback())")
internal external fun createWasmResizeObserver(callback: () -> Unit): WasmResizeObserver
