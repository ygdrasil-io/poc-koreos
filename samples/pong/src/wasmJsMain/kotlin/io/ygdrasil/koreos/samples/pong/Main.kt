package io.ygdrasil.koreos.samples.pong

import io.ygdrasil.koreos.EventLoop

fun main() {
    // Web: renderer stub (wgpu4k web à implémenter #27)
    EventLoop().runApp(PongGame { _ ->
        object : PongRendererInterface {
            override fun draw(state: GameState) {}
            override fun resize(width: Int, height: Int) {}
            override fun release() {}
        }
    })
}
