package io.ygdrasil.koreos.android

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import io.ygdrasil.koreos.core.ApplicationHandler

/**
 * Activity racine Koreos pour Android.
 *
 * Héberge un [SurfaceView] plein écran dont la [android.view.Surface] est
 * exposée via [AndroidWindow.rawWindowHandle] → [RawWindowHandle.Android].
 *
 * ## Usage
 * Sous-classer et implémenter [createHandler] :
 * ```kotlin
 * class MyActivity : KoreosActivity() {
 *     override fun createHandler() = MyApplicationHandler()
 * }
 * ```
 *
 * ## Lifecycle → callbacks Koreos (GRA-149)
 * Le dispatch des événements de lifecycle ([ApplicationHandler.canCreateSurfaces],
 * [ApplicationHandler.resumed], etc.) sera ajouté dans un ticket dédié.
 *
 * ## Plein écran
 * Status bar et navigation bar masquées via `FLAG_FULLSCREEN` et layout cutout-aware.
 */
abstract class KoreosActivity : ComponentActivity() {

    /**
     * Crée le gestionnaire d'application Koreos.
     * Appelé depuis [onCreate] avant toute initialisation de surface.
     */
    abstract fun createHandler(): ApplicationHandler

    /** Handler instancié lors de [onCreate]. */
    lateinit var handler: ApplicationHandler
        private set

    /** Fenêtre Android Koreos créée lors de [onCreate]. */
    lateinit var koreosWindow: AndroidWindow
        private set

    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Plein écran ────────────────────────────────────────────────────────
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Cutout-aware (API 28+) — flag appliqué sans import direct pour compat
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // ── SurfaceView plein écran ────────────────────────────────────────────
        surfaceView = SurfaceView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
        setContentView(surfaceView)

        // ── AndroidWindow ──────────────────────────────────────────────────────
        handler = createHandler()
        koreosWindow = AndroidWindow(surfaceView)

        // ── SurfaceHolder callbacks (surface lifecycle) ────────────────────────
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                println("[KoreosActivity] surfaceCreated → surface available")
                koreosWindow.onSurfaceAvailable(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                println("[KoreosActivity] surfaceChanged ${width}×${height}")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                println("[KoreosActivity] surfaceDestroyed → surface released")
                koreosWindow.onSurfaceReleased()
            }
        })
    }

    override fun onDestroy() {
        koreosWindow.onSurfaceReleased()
        super.onDestroy()
    }
}
