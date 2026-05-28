/**
 * Implémentation JVM de la boucle d'événements koreos — délègue directement à koreos-appkit.
 *
 * Contrairement à la délégation par réflexion de koreos-core, cet `actual` appelle
 * directement `io.ygdrasil.koreos.appkit.runApp()` puisque koreos-appkit est une
 * dépendance directe de koreos/jvmMain.
 *
 * GRA-129 : façade KMP — actual jvmMain.
 */
package io.ygdrasil.koreos

/**
 * Implémentation JVM de [EventLoop].
 *
 * Délègue directement à [io.ygdrasil.koreos.appkit.runApp] (koreos-appkit),
 * sans indirection par réflexion.
 */
actual class EventLoop actual constructor() {

    /**
     * Démarre la boucle d'événements AppKit et délègue les rappels au gestionnaire fourni.
     *
     * Bloquant — ne retourne qu'à la fermeture de l'application.
     *
     * @param handler Gestionnaire du cycle de vie et des événements de l'application.
     */
    actual fun runApp(handler: ApplicationHandler) {
        io.ygdrasil.koreos.appkit.runApp(handler)
    }
}
