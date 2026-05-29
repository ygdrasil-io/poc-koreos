/**
 * Implémentation JS de la boucle d'événements koreos — stub initial.
 *
 * Délègue vers koreos-web-common (WebDomBridge). L'implémentation complète,
 * incluant la boucle requestAnimationFrame et la gestion des événements DOM,
 * sera réalisée dans le ticket #24 (WebEventLoop).
 *
 * Redmine #28 : façade koreos — cibles jsMain + wasmJsMain.
 */
package io.ygdrasil.koreos

/**
 * Implémentation JS de [EventLoop].
 *
 * Stub minimal : lève [NotImplementedError] à l'exécution.
 * L'implémentation complète est prévue dans le ticket #24.
 */
actual class EventLoop actual constructor() {

    /**
     * Démarre la boucle d'événements côté navigateur (JS/IR).
     *
     * @throws NotImplementedError Toujours — implémentation complète dans #24.
     */
    actual fun runApp(handler: ApplicationHandler) {
        // Stub minimal : délègue au DOM bridge via koreos-web-common
        // Implémentation complète dans ticket #24 (WebEventLoop)
        throw NotImplementedError("WebEventLoop sera implémenté dans le ticket #24")
    }
}
