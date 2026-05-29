/**
 * Implémentation JS (stub) de la boucle d'événements koreos-core.
 *
 * Ticket #28 : ajout des cibles JS/wasmJs à koreos-core pour permettre
 * à la façade `koreos` d'exposer EventLoop aux cibles navigateur.
 * L'implémentation complète sera réalisée dans le ticket #24 (WebEventLoop).
 */
package io.ygdrasil.koreos.core

/**
 * Implémentation JS de [EventLoop].
 *
 * Stub provisoire — l'implémentation réelle sera apportée dans le ticket #24.
 */
actual class EventLoop actual constructor() {

    /**
     * Démarre la boucle d'événements et délègue les rappels au gestionnaire fourni.
     *
     * @param handler Gestionnaire du cycle de vie et des événements de l'application.
     * @throws UnsupportedOperationException Toujours — implémentation complète dans #24.
     */
    actual fun runApp(handler: ApplicationHandler) {
        throw UnsupportedOperationException(
            "EventLoop JS non implémenté — en attente du ticket #24 (WebEventLoop)."
        )
    }
}
