package io.ygdrasil.koreos.samples.hellotouch

import io.ygdrasil.koreos.*
import io.ygdrasil.koreos.core.WindowEvent

/**
 * Démontre le pipeline touch events iOS :
 *   - canCreateSurfaces : crée la fenêtre principale
 *   - windowEvent(Touch) : log la phase et les coordonnées
 *   - resumed / suspended : log le cycle de vie
 */
fun main() {
    EventLoop().runApp(HelloTouchHandler())
}

private class HelloTouchHandler : ApplicationHandler {

    private var window: Window? = null

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        println("[HelloTouch] canCreateSurfaces")
        window = eventLoop.createWindow(WindowAttributes(title = "Hello Touch"))
        println("[HelloTouch] window created id=${window?.id?.value}")
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
