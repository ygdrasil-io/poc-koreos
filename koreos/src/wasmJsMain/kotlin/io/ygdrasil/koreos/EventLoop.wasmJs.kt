/**
 * Implémentation wasmJs de la boucle d'événements koreos.
 *
 * Délègue à [io.ygdrasil.koreos.web.WasmJsWebEventLoop] (koreos-web-common), qui
 * orchestre la boucle `requestAnimationFrame` et le dispatch des événements DOM
 * via interop JS Wasm.
 *
 * Redmine #28 : façade koreos — cibles jsMain + wasmJsMain.
 * Redmine #22/#24 : câblage de la façade vers le WebEventLoop réel.
 */
package io.ygdrasil.koreos

/**
 * Implémentation wasmJs de [EventLoop] — délègue à [io.ygdrasil.koreos.web.WasmJsWebEventLoop].
 */
actual class EventLoop actual constructor() {

    /**
     * Démarre la boucle d'événements côté navigateur (wasmJs).
     *
     * @param handler Gestionnaire du cycle de vie et des événements de l'application.
     */
    actual fun runApp(handler: ApplicationHandler) {
        io.ygdrasil.koreos.web.WasmJsWebEventLoop().runApp(handler)
    }
}
