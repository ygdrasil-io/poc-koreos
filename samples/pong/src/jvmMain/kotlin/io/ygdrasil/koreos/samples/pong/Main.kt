package io.ygdrasil.koreos.samples.pong

import io.ygdrasil.koreos.EventLoop

fun main() {
    // PongGame est en commonMain, PongRenderer en jvmMain
    // La factory fournit le renderer spécifique à la plateforme
    EventLoop().runApp(PongGame { rawHandle ->
        PongRenderer(rawHandle)
    })
}
