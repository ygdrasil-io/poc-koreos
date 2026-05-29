package io.ygdrasil.koreos.samples.hellotouch

import io.ygdrasil.koreos.EventLoop
import io.ygdrasil.koreos.WindowAttributes

/**
 * Point d'entrée iOS — lance l'EventLoop avec [HelloTouchHandler].
 *
 * La création de fenêtre est spécifique à iOS : sur Android, la fenêtre
 * est gérée par KoreosActivity et n'est pas créée manuellement ici.
 */
fun main() {
    EventLoop().runApp(IosTouchHandler())
}

/**
 * Handler iOS — étend [HelloTouchHandler] pour créer la fenêtre principale.
 *
 * Sur iOS, `canCreateSurfaces` doit appeler `eventLoop.createWindow()` ;
 * ce comportement est propre à la plateforme et ne fait pas partie du
 * handler commun.
 */
private class IosTouchHandler : HelloTouchHandler() {

    private var window: io.ygdrasil.koreos.Window? = null

    override fun canCreateSurfaces(eventLoop: io.ygdrasil.koreos.ActiveEventLoop) {
        super.canCreateSurfaces(eventLoop)
        window = eventLoop.createWindow(WindowAttributes(title = "Hello Touch"))
        println("[HelloTouch] window created id=${window?.id?.value}")
    }
}
