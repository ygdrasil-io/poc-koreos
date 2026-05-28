/**
 * Implémentation JVM (stub) de la boucle d'événements.
 *
 * Cette implémentation est un stub provisoire. L'implémentation complète
 * sera fournie par le module koreos-appkit dans le cadre du jalon M1.
 */
package io.ygdrasil.koreos.core

/**
 * Implémentation JVM de [EventLoop].
 *
 * Stub provisoire — l'implémentation réelle via AppKit FFM sera apportée
 * dans le module koreos-appkit (ticket M1).
 */
actual class EventLoop actual constructor() {

    /**
     * Démarre la boucle d'événements et délègue les rappels au gestionnaire fourni.
     *
     * @param handler Gestionnaire du cycle de vie et des événements de l'application.
     */
    actual fun runApp(handler: ApplicationHandler) {
        throw UnsupportedOperationException(
            "EventLoop JVM non implémenté — en attente du module koreos-appkit (M1)."
        )
    }
}
