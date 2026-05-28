/**
 * Implémentation JVM de la boucle d'événements — délègue à AppKitEventLoop.
 *
 * Ce module (koreos-core) fournit la déclaration `actual` ; l'implémentation
 * concrète est dans koreos-appkit via la fonction top-level `runApp`.
 *
 * L'indirection par réflexion évite une dépendance directe de koreos-core
 * vers koreos-appkit, conformément à l'architecture modulaire du projet.
 */
package io.ygdrasil.koreos.core

/**
 * Implémentation JVM de [EventLoop].
 *
 * Délègue à `io.ygdrasil.koreos.appkit.AppKitEventLoopKt.runApp` via
 * réflexion pour éviter un couplage direct de koreos-core → koreos-appkit.
 * Cette délégation est résolue à l'exécution : koreos-appkit doit être sur
 * le classpath.
 */
actual class EventLoop actual constructor() {

    /**
     * Démarre la boucle d'événements AppKit et délègue les rappels au gestionnaire fourni.
     *
     * Bloquant — ne retourne qu'à la fermeture de l'application.
     *
     * @param handler Gestionnaire du cycle de vie et des événements de l'application.
     * @throws UnsupportedOperationException si koreos-appkit n'est pas sur le classpath.
     */
    actual fun runApp(handler: ApplicationHandler) {
        val klass = try {
            Class.forName("io.ygdrasil.koreos.appkit.AppKitEventLoopKt")
        } catch (e: ClassNotFoundException) {
            throw UnsupportedOperationException(
                "koreos-appkit introuvable sur le classpath. " +
                "Ajoutez la dépendance implementation(project(\":koreos-appkit\")).",
                e,
            )
        }
        val method = klass.getMethod("runApp", ApplicationHandler::class.java)
        method.invoke(null, handler)
    }
}
