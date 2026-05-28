package io.ygdrasil.koreos.android

import android.view.Choreographer
import androidx.activity.ComponentActivity
import io.ygdrasil.koreos.core.ActiveEventLoop
import io.ygdrasil.koreos.core.ControlFlow
import io.ygdrasil.koreos.core.EventLoopProxy
import io.ygdrasil.koreos.core.Window
import io.ygdrasil.koreos.core.WindowAttributes
import io.ygdrasil.koreos.core.WindowEvent

/**
 * Implémentation Android de [ActiveEventLoop].
 *
 * La création de fenêtres est gérée directement par [KoreosActivity] via la
 * [android.view.Surface] du [android.view.SurfaceView] ; elle n'est donc pas
 * supportée via [createWindow].
 *
 * [exit] termine l'Activity parente via [ComponentActivity.finish].
 *
 * Le timing des frames est géré par [Choreographer] : [scheduleFrameIfNeeded]
 * programme un callback vsync qui dispatche [WindowEvent.RedrawRequested]
 * si [AndroidWindow.needsRedraw] est positionné, puis [ApplicationHandler.aboutToWait].
 * En mode [ControlFlow.Poll], le callback se reprogramme automatiquement.
 */
internal class AndroidEventLoop(
    private val activity: ComponentActivity,
) : ActiveEventLoop {

    @Volatile
    override var controlFlow: ControlFlow = ControlFlow.Wait
        private set

    private val choreographer = Choreographer.getInstance()

    @Volatile
    private var frameCallbackScheduled = false

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

    /**
     * Programme le prochain callback vsync si ce n'est pas déjà fait.
     * Doit être appelé depuis le thread principal.
     */
    internal fun scheduleFrameIfNeeded(window: AndroidWindow) {
        if (!frameCallbackScheduled) {
            frameCallbackScheduled = true
            choreographer.postFrameCallback { frameTimeNanos ->
                frameCallbackScheduled = false
                onFrame(frameTimeNanos, window)
            }
        }
    }

    private fun onFrame(frameTimeNanos: Long, window: AndroidWindow) {
        if (activity.isDestroyed) return
        val koreosActivity = activity as KoreosActivity
        if (koreosActivity.destroyed) return

        // Dispatch RedrawRequested si needsRedraw est positionné
        if (window.needsRedraw) {
            window.needsRedraw = false
            koreosActivity.handler.windowEvent(this, window.id, WindowEvent.RedrawRequested)
        }

        // Dispatch aboutToWait
        koreosActivity.handler.aboutToWait(this)

        // Re-programmer si mode Poll ou si needsRedraw a été repositionné
        if (controlFlow == ControlFlow.Poll || window.needsRedraw) {
            scheduleFrameIfNeeded(window)
        }
    }
}
