package io.ygdrasil.koreos.uikit

import io.ygdrasil.koreos.core.ApplicationHandler

/**
 * Registre global Koreos pour iOS.
 *
 * Stocke l'ApplicationHandler avant le démarrage de UIApplicationMain.
 * Nécessaire car UIApplicationMain instancie KoreosAppDelegate lui-même
 * sans permettre l'injection de dépendances au constructeur.
 *
 * Usage :
 * ```kotlin
 * startKoreosApplication(myHandler)  // stocke dans KoreosRegistry puis lance UIApplicationMain
 * ```
 */
object KoreosRegistry {
    var handler: ApplicationHandler? = null
}
