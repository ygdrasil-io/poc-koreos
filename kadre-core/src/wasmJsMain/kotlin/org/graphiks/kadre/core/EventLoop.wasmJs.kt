/**
 * wasmJs actual implementation of [EventLoop] (internal kadre-core stub).
 *
 * Ticket #28: adding the JS/wasmJs targets to kadre-core so the `kadre`
 * facade can expose EventLoop to browser targets.
 *
 * This is an internal `expect`/`actual` placeholder required by KMP for
 * the `kadre-core` module. Do NOT use `kadre-core.EventLoop` directly.
 * Use the `kadre` aggregator module which delegates to the concrete
 * platform backends (kadre-uikit, kadre-android, kadre-web-common, …).
 */
package org.graphiks.kadre.core

/**
 * wasmJs implementation of [EventLoop].
 *
 * Internal placeholder — the real entry point is the `kadre` module
 * which delegates to the web (kadre-web-common) backend at runtime.
 */
actual class EventLoop actual constructor() {

    /**
     * Starts the event loop and delegates callbacks to the provided handler.
     *
     * @param handler Handler for the application lifecycle and events.
     * @throws UnsupportedOperationException Always — pending ticket #24.
     */
    actual fun runApp(handler: ApplicationHandler) {
        throw UnsupportedOperationException(
            "kadre-core.EventLoop is an internal expect/actual placeholder. " +
            "Use the `kadre` module which delegates to the wasmJs (kadre-web-common) backend."
        )
    }
}
