/**
 * Stub iOS Simulator arm64 de la boucle d'événements — non implémenté en M1.
 *
 * L'implémentation réelle est prévue pour M3 (koreos-uikit).
 */
package io.ygdrasil.koreos

actual class EventLoop actual constructor() {

    actual fun runApp(handler: ApplicationHandler) {
        throw UnsupportedOperationException(
            "EventLoop iOS Simulator (arm64) non implémenté — prévu M3."
        )
    }
}
