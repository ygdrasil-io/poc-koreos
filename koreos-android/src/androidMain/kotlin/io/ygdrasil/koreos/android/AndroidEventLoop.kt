package io.ygdrasil.koreos.android

import androidx.activity.ComponentActivity
import io.ygdrasil.koreos.core.ActiveEventLoop
import io.ygdrasil.koreos.core.ControlFlow
import io.ygdrasil.koreos.core.EventLoopProxy
import io.ygdrasil.koreos.core.Window
import io.ygdrasil.koreos.core.WindowAttributes

/**
 * Implémentation Android de [ActiveEventLoop].
 *
 * La création de fenêtres est gérée directement par [KoreosActivity] via la
 * [android.view.Surface] du [android.view.SurfaceView] ; elle n'est donc pas
 * supportée via [createWindow].
 *
 * [exit] termine l'Activity parente via [ComponentActivity.finish].
 */
internal class AndroidEventLoop(
    private val activity: ComponentActivity,
) : ActiveEventLoop {

    @Volatile
    override var controlFlow: ControlFlow = ControlFlow.Wait
        private set

    override fun createWindow(attributes: WindowAttributes): Window {
        throw UnsupportedOperationException(
            "La création de fenêtre via l'EventLoop n'est pas supportée sur Android. " +
            "La surface est gérée automatiquement par KoreosActivity."
        )
    }

    override fun setControlFlow(controlFlow: ControlFlow) {
        this.controlFlow = controlFlow
    }

    override fun exit() {
        activity.finish()
    }

    override val isExiting: Boolean
        get() = activity.isFinishing

    override fun createProxy(): EventLoopProxy = object : EventLoopProxy {
        override fun wakeUp() {
            // No-op : Android gère son propre scheduling via le Looper/Handler
        }
    }
}
