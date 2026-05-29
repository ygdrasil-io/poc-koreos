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
 * ## Cycle de vie de la fenêtre — pattern "pending window"
 *
 * Sur Android, la [android.view.Surface] n'est disponible qu'après le callback
 * [android.view.SurfaceHolder.Callback.surfaceCreated] ; elle peut être libérée
 * et recréée (ex. : rotation, onPause/onResume). Le pattern "pending window"
 * dissocie la création de l'objet [AndroidWindow] de la disponibilité de la surface :
 *
 * 1. **[createWindow]** — appelé depuis [io.ygdrasil.koreos.core.ApplicationHandler.canCreateSurfaces] :
 *    crée immédiatement un [AndroidWindow] lié au [SurfaceView] de l'Activity **avant**
 *    que la surface soit disponible. La fenêtre est stockée dans [pendingWindow].
 *
 * 2. **[onSurfaceCreated]** — appelé par [KoreosActivity] lors de `surfaceCreated` :
 *    transfère la surface vers le [AndroidWindow] via [AndroidWindow.onSurfaceAvailable].
 *    À partir de cet instant, [AndroidWindow.rawWindowHandle] est valide.
 *
 * 3. **[onSurfaceDestroyed]** — appelé par [KoreosActivity] lors de `surfaceDestroyed` :
 *    invalide la surface via [AndroidWindow.onSurfaceReleased].
 *
 * ## Contrat de timing pour [AndroidWindow.rawWindowHandle]
 *
 * [AndroidWindow.rawWindowHandle] lance [IllegalStateException] si la surface n'est pas
 * encore disponible (entre [createWindow] et [onSurfaceCreated]). Les renderers doivent
 * n'accéder au handle que dans ou après [ApplicationHandler.canCreateSurfaces].
 *
 * ## Scheduling des frames
 *
 * Le timing des frames est géré par [Choreographer] : [scheduleFrameIfNeeded]
 * programme un callback vsync qui dispatche [WindowEvent.RedrawRequested]
 * si [AndroidWindow.needsRedraw] est positionné, puis [io.ygdrasil.koreos.core.ApplicationHandler.aboutToWait].
 * En mode [ControlFlow.Poll], le callback se reprogramme automatiquement.
 *
 * [exit] termine l'Activity parente via [ComponentActivity.finish].
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

    /**
     * Fenêtre créée via [createWindow] et en attente de surface.
     *
     * Nulle avant le premier appel à [createWindow], non nulle ensuite.
     * La surface elle-même est disponible uniquement après [onSurfaceCreated].
     */
    @Volatile
    internal var pendingWindow: AndroidWindow? = null
        private set

    /**
     * Crée un [AndroidWindow] lié au [SurfaceView] de l'Activity.
     *
     * Retourne immédiatement un [AndroidWindow] valide **avant** que la
     * [android.view.Surface] ne soit disponible (pattern "pending window").
     * [AndroidWindow.rawWindowHandle] n'est accessible qu'après [onSurfaceCreated].
     *
     * Peut être appelé plusieurs fois : chaque appel remplace la référence
     * [pendingWindow] (cas rare — une seule fenêtre par Activity est la norme).
     *
     * @param attributes Attributs de fenêtre (titre, taille, etc.).
     *                   Sur Android, titre et redimensionnement sont ignorés.
     * @return Un [AndroidWindow] dont la surface sera disponible après [onSurfaceCreated].
     */
    override fun createWindow(attributes: WindowAttributes): Window {
        val koreosActivity = activity as KoreosActivity
        val window = AndroidWindow(koreosActivity.surfaceView)
        pendingWindow = window
        return window
    }

    /**
     * Transfère la [android.view.Surface] vers la fenêtre en attente.
     *
     * Appelé par [KoreosActivity] lors de `surfaceCreated`. Après cet appel,
     * [AndroidWindow.rawWindowHandle] retourne un [io.ygdrasil.koreos.core.RawWindowHandle.Android]
     * valide. Si aucune fenêtre n'a encore été créée via [createWindow], le
     * [holder] est ignoré (la surface sera fournie lors du prochain [createWindow]).
     *
     * @param surface La surface Android fraîchement créée par le SurfaceHolder.
     */
    internal fun onSurfaceCreated(surface: android.view.Surface) {
        pendingWindow?.onSurfaceAvailable(surface)
    }

    /**
     * Invalide la surface de la fenêtre active.
     *
     * Appelé par [KoreosActivity] lors de `surfaceDestroyed`. Après cet appel,
     * [AndroidWindow.rawWindowHandle] lance [IllegalStateException] jusqu'à la
     * prochaine invocation de [onSurfaceCreated].
     */
    internal fun onSurfaceDestroyed() {
        pendingWindow?.onSurfaceReleased()
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
