package io.ygdrasil.koreos.uikit

import io.ygdrasil.koreos.core.*

/**
 * ActiveEventLoop UIKit — proxy léger vers ApplicationHandler.
 *
 * Sur iOS, UIKit possède la boucle d'événements (UIApplicationMain).
 * Cette implémentation expose le contrat ActiveEventLoop aux callbacks
 * de KoreosAppDelegate sans dupliquer la boucle.
 */
internal class UIKitActiveEventLoop(internal val handler: ApplicationHandler) : ActiveEventLoop {

    private var _controlFlow: ControlFlow = ControlFlow.Wait
    private var _isExiting = false

    override fun createWindow(attributes: WindowAttributes): Window =
        throw UnsupportedOperationException("UIKitWindow pas encore implémenté — GRA-143")

    override fun setControlFlow(controlFlow: ControlFlow) { _controlFlow = controlFlow }
    override val controlFlow: ControlFlow get() = _controlFlow
    override fun exit() { _isExiting = true }
    override val isExiting: Boolean get() = _isExiting

    override fun createProxy(): EventLoopProxy =
        throw UnsupportedOperationException("EventLoopProxy UIKit non implémenté — post-M3")
}
