/**
 * Implémentation Android de la boucle d'événements — délègue à koreos-android.
 *
 * Sur Android, le point d'entrée est l'Activity. [runApp] enregistre le
 * [ApplicationHandler] dans [io.ygdrasil.koreos.android.AndroidKoreosRuntime]
 * pour qu'il soit récupéré par [io.ygdrasil.koreos.android.KoreosActivity].
 */
package io.ygdrasil.koreos

import io.ygdrasil.koreos.android.AndroidKoreosRuntime

actual class EventLoop actual constructor() {

    actual fun runApp(handler: ApplicationHandler) {
        AndroidKoreosRuntime.currentHandler = handler
        // On Android the Activity lifecycle is the entry point.
        // The handler is retrieved by KoreosActivity.createHandler() via AndroidKoreosRuntime.
    }
}
