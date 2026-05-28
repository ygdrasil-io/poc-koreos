/**
 * Stub Android de la boucle d'événements — non implémenté en M1.
 *
 * L'implémentation réelle est prévue pour M3 (koreos-android).
 */
package io.ygdrasil.koreos

actual class EventLoop actual constructor() {

    actual fun runApp(handler: ApplicationHandler) {
        throw UnsupportedOperationException(
            "EventLoop Android non implémenté — prévu M3."
        )
    }
}
