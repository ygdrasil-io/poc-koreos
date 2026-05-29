package io.ygdrasil.koreos.samples.hellotouch

import io.ygdrasil.koreos.ActiveEventLoop
import io.ygdrasil.koreos.ApplicationHandler
import io.ygdrasil.koreos.WindowId
import io.ygdrasil.koreos.core.WindowEvent

/**
 * Handler de démonstration Hello Touch — partagé entre Android et iOS.
 *
 * Démontre la convergence d'API Koreos sur toutes les plateformes :
 * - [canCreateSurfaces] : signal que le système est prêt pour le rendu
 * - [windowEvent] : réception et log des événements tactiles
 * - [resumed] / [suspended] : cycle de vie de l'application
 * - [destroySurfaces] : libération des surfaces de rendu
 *
 * Sur Android, la fenêtre est gérée par KoreosActivity — pas d'appel
 * à `createWindow()` nécessaire ici.
 */
class HelloTouchHandler : ApplicationHandler {

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        println("[HelloTouch] canCreateSurfaces — surface ready for rendering")
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
