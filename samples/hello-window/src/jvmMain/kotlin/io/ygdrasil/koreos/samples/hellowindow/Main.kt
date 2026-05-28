/**
 * Point d'entrée JVM pour hello-window (macOS via AppKit).
 *
 * Usage : ./gradlew :samples:hello-window:run
 */
package io.ygdrasil.koreos.samples.hellowindow

import io.ygdrasil.koreos.EventLoop

fun main() {
    EventLoop().runApp(HelloApp())
}
