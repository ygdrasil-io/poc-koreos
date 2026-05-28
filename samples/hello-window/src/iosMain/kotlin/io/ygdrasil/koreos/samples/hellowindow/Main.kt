/**
 * Point d'entrée iOS pour hello-window.
 *
 * Appelé par le framework Koreos au démarrage de l'application iOS.
 */
package io.ygdrasil.koreos.samples.hellowindow

import io.ygdrasil.koreos.EventLoop

fun main() {
    EventLoop().runApp(HelloApp())
}
