package io.ygdrasil.koreos.samples.hellotouchandroid

import io.ygdrasil.koreos.ActiveEventLoop
import io.ygdrasil.koreos.ApplicationHandler
import io.ygdrasil.koreos.WindowId
import io.ygdrasil.koreos.core.WindowEvent

/**
 * Handler de démonstration Hello Touch — identique au sample iOS.
 * Démontre la convergence d'API Koreos entre plateformes.
 */
class HelloTouchHandler : ApplicationHandler {

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        println("[HelloTouch] canCreateSurfaces — surface ready for rendering")
        // On Android the window is managed by KoreosActivity directly.
        // No createWindow() call needed — touch events arrive via windowEvent().
    }

    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: Any) {
        if (event is WindowEvent.Touch) {
            println("[HelloTouch] Touch ${event.phase} id=${event.id} @ (${event.location.x.toInt()}, ${event.location.y.toInt()})")
        }
    }

    override fun resumed(eventLoop: ActiveEventLoop) = println("[HelloTouch] resumed")
    override fun suspended(eventLoop: ActiveEventLoop) = println("[HelloTouch] suspended")
    override fun destroySurfaces(eventLoop: ActiveEventLoop) = println("[HelloTouch] destroySurfaces")
}
