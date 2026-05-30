package io.ygdrasil.koreos.samples.pong

import io.ygdrasil.koreos.EventLoop

fun main() {
    // Android: renderer stub (support Android à implémenter)
    EventLoop().runApp(PongGame { _ ->
        object : PongRendererInterface {
            override fun draw(state: GameState) {}
            override fun resize(width: Int, height: Int) {}
            override fun release() {}
        }
    })
}
