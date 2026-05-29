package io.ygdrasil.koreos.samples.hellotouch

import io.ygdrasil.koreos.ActiveEventLoop
import io.ygdrasil.koreos.ApplicationHandler
import io.ygdrasil.koreos.EventLoop
import io.ygdrasil.koreos.Window
import io.ygdrasil.koreos.WindowAttributes
import io.ygdrasil.koreos.WindowId
import io.ygdrasil.koreos.core.WindowEvent

/**
 * Point d'entrée iOS — lance l'EventLoop avec le handler iOS.
 *
 * La création de fenêtre est spécifique à iOS : sur Android, la fenêtre
 * est gérée par KoreosActivity et n'est pas créée manuellement ici.
 */
fun main() {
    EventLoop().runApp(IosHelloTouchHandler())
}

/**
 * Handler iOS — crée la fenêtre principale et délègue les événements touch.
 *
 * Sur iOS, `canCreateSurfaces` doit appeler `eventLoop.createWindow()` ;
 * ce comportement est propre à la plateforme iOS.
 * La logique de traitement des événements touch est dupliquée depuis
 * [HelloTouchHandler] (commonMain) pour éviter l'héritage d'une classe finale.
 */
private class IosHelloTouchHandler : ApplicationHandler {

    private var window: Window? = null

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        println("[HelloTouch] canCreateSurfaces — surface ready for rendering")
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
