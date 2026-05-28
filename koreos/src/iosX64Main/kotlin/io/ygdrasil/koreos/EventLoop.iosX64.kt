/**
 * Implémentation iOS x64 de la boucle d'événements — délègue à koreos-uikit.
 */
package io.ygdrasil.koreos

actual class EventLoop actual constructor() {
    actual fun runApp(handler: ApplicationHandler) {
        io.ygdrasil.koreos.uikit.startKoreosApplication(handler)
    }
}
