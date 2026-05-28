package io.ygdrasil.koreos.android

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import io.ygdrasil.koreos.core.ApplicationHandler
import io.ygdrasil.koreos.core.PhysicalSize
import io.ygdrasil.koreos.core.WindowEvent

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
 * ## Lifecycle → callbacks Koreos
 * - [onResume]  → [ApplicationHandler.resumed]
 * - [onPause]   → [ApplicationHandler.suspended]
 * - Surface créée   → [ApplicationHandler.canCreateSurfaces]
 * - Surface changée → [ApplicationHandler.windowEvent] ([WindowEvent.Resized])
 * - Surface détruite → [ApplicationHandler.destroySurfaces]
 * - [onDestroy] → guard `destroyed` activé, puis nettoyage
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

    /** Fenêtre Android Koreos créée lors de [surfaceCreated]. */
    lateinit var koreosWindow: AndroidWindow
        private set

    /** Boucle d'événements Android. */
    private lateinit var eventLoop: AndroidEventLoop

    private lateinit var surfaceView: SurfaceView

    /**
     * Guard contre tout dispatch de callback après [onDestroy].
     * Mis à `true` au début de [onDestroy], avant tout nettoyage.
     */
    @Volatile
    private var destroyed = false

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

        // ── Handler + EventLoop ────────────────────────────────────────────────
        handler = createHandler()
        eventLoop = AndroidEventLoop(this)

        // ── SurfaceHolder callbacks (surface lifecycle) ────────────────────────
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (destroyed) return
                println("[KoreosActivity] surfaceCreated → surface available")
                koreosWindow = AndroidWindow(surfaceView)
                koreosWindow.onSurfaceAvailable(holder.surface)
                handler.canCreateSurfaces(eventLoop)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (destroyed) return
                println("[KoreosActivity] surfaceChanged ${width}×${height}")
                if (::koreosWindow.isInitialized) {
                    handler.windowEvent(
                        eventLoop,
                        koreosWindow.id,
                        WindowEvent.Resized(PhysicalSize(width, height)),
                    )
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (destroyed) return
                println("[KoreosActivity] surfaceDestroyed → surface released")
                handler.destroySurfaces(eventLoop)
                if (::koreosWindow.isInitialized) {
                    koreosWindow.onSurfaceReleased()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (destroyed) return
        handler.resumed(eventLoop)
    }

    override fun onPause() {
        super.onPause()
        if (destroyed) return
        handler.suspended(eventLoop)
    }

    override fun onDestroy() {
        destroyed = true
        if (::koreosWindow.isInitialized) {
            koreosWindow.onSurfaceReleased()
        }
        super.onDestroy()
    }
}
