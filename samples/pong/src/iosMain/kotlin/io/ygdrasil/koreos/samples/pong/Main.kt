package io.ygdrasil.koreos.samples.pong

import io.ygdrasil.koreos.EventLoop

fun main() {
    // iOS: renderer stub (support iOS à implémenter #78)
    EventLoop().runApp(PongGame { _ ->
        object : PongRendererInterface {
            override fun draw(state: GameState) {}
            override fun resize(width: Int, height: Int) {}
            override fun release() {}
        }
    })
}
