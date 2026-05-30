package io.ygdrasil.koreos.samples.pong

import io.ygdrasil.koreos.core.Key
import io.ygdrasil.koreos.core.KeyState
import io.ygdrasil.koreos.core.PhysicalSize
import io.ygdrasil.koreos.core.TouchPhase
import io.ygdrasil.koreos.core.WindowEvent

/**
 * Adaptateur d'entrée cross-platform pour Pong.
 * Mappe clavier (desktop/web) et touch (mobile/web) vers PaddleInput.
 *
 * Redmine #76.
 */
class InputAdapter {

    var playerInput: PaddleInput = PaddleInput.NONE
        private set

    fun onKey(event: WindowEvent.KeyboardInput) {
        playerInput = when {
            event.key == Key.ArrowUp && event.state == KeyState.Pressed -> PaddleInput.UP
            event.key == Key.ArrowDown && event.state == KeyState.Pressed -> PaddleInput.DOWN
            (event.key == Key.ArrowUp || event.key == Key.ArrowDown) && event.state == KeyState.Released -> PaddleInput.NONE
            else -> playerInput
        }
    }

    /**
     * Gère un contact tactile.
     *
     * Zone de jeu : côté droit de l'écran (x > width/2).
     * Moitié supérieure → UP, moitié inférieure → DOWN.
     * Touch ended/cancelled → NONE.
     */
    fun onTouch(event: WindowEvent.Touch, screenSize: PhysicalSize<Int>) {
        playerInput = when (event.phase) {
            TouchPhase.Started, TouchPhase.Moved -> {
                if (event.location.x > screenSize.width / 2.0) {
                    if (event.location.y < screenSize.height / 2.0) PaddleInput.UP
                    else PaddleInput.DOWN
                } else playerInput
            }
            TouchPhase.Ended, TouchPhase.Cancelled -> PaddleInput.NONE
        }
    }
}
