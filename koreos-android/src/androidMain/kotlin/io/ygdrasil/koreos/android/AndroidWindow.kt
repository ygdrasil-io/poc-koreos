package io.ygdrasil.koreos.android

import android.view.SurfaceView
import io.ygdrasil.koreos.core.*

/**
 * Implémentation Android de [Window].
 *
 * Encapsule un [SurfaceView] plein écran et expose la [android.view.Surface]
 * brute via [rawWindowHandle] (Strategy A — zéro JNI).
 *
 * La surface est disponible uniquement après le callback [onSurfaceAvailable].
 */
class AndroidWindow internal constructor(
    internal val surfaceView: SurfaceView,
) : Window {

    override val id: WindowId = WindowId(surfaceView.hashCode().toLong())

    private var _surface: android.view.Surface? = null

    /**
     * Appelé par [KoreosActivity] quand la surface est créée/disponible.
     */
    internal fun onSurfaceAvailable(surface: android.view.Surface) {
        _surface = surface
    }

    /**
     * Appelé par [KoreosActivity] quand la surface est détruite.
     */
    internal fun onSurfaceReleased() {
        _surface = null
    }

    override val rawWindowHandle: Any
        get() = RawWindowHandle.Android(
            surface = _surface ?: error("Surface Android non disponible (surfaceCreated pas encore appelé)")
        )

    override val rawDisplayHandle: Any
        get() = RawDisplayHandle.Android

    @Volatile
    internal var needsRedraw: Boolean = false

    override fun requestRedraw() {
        needsRedraw = true
    }

    override fun setTitle(title: String) {
        // No-op : les SurfaceView n'ont pas de titre ; l'Activity parente gère le titre
    }

    override val innerSize: PhysicalSize<Int>
        get() = PhysicalSize(surfaceView.width, surfaceView.height)

    override val outerSize: PhysicalSize<Int>
        get() = PhysicalSize(surfaceView.width, surfaceView.height)

    override val scaleFactor: Double
        get() = surfaceView.resources.displayMetrics.density.toDouble()

    override fun setVisible(visible: Boolean) {
        surfaceView.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun close() {
        // No-op au niveau library ; la fermeture est à l'app
    }
}
