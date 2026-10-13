package org.graphiks.kadre.samples.hellowindow

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.android.attachKadre

/**
 * Le sample migré « hello window » (kadre-old `samples/hello-window-android`) : l'attach
 * Kotlin par la forme ACTIVITY d'[attachKadre] sur une [FrameLayout] ordinaire — AUCUNE
 * SurfaceView, le rendu reste la responsabilité du sample (P1 : kadre ne crée jamais de
 * vue) — et une console [TextView] qui journalise le cycle de vie de la fenêtre vu du
 * runtime (attachement, visibilité, focus, dimensions, terminaison).
 *
 * Le contraste kadre-old assumé : PAS d'`EventLoop.runApp()` dans l'Application, PAS de
 * `AndroidKadreRuntime.currentHandler` — l'attach est un appel ordinaire du thread principal
 * qui rend un [KadreResult] (jamais d'exception ordinaire), l'application kadre est une
 * coroutine structurée ([KadreApplication]) qui observe la surface primaire, et la session
 * s'arrête seule quand la vue quitte la fenêtre (`Stopped(HostDetached)`, la couture du
 * binding lifecycle).
 */
class MainActivity : ComponentActivity() {

    /** La console bornée du sample — publiée depuis n'importe quel thread, lue sur le main. */
    private lateinit var console: ConsoleLog

    /** La session vivante, attachée UNE fois (garde des reprises d'[onResume]). */
    private var session: KadreSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La console vit APRÈS attachBaseContext : une View construite dans un initialisateur
        // de propriété lirait un contexte de base encore nul (le piège classique de l'ordre
        // d'initialisation Kotlin des Activity).
        console = ConsoleLog(this)
        // LE host du sample : une View ordinaire. L'échelle d'admission exige une vue réellement
        // attachée à la fenêtre — la création attend l'événement d'attach (le même parti pris
        // que la liaison Compose de phase 9).
        val host = FrameLayout(this)
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(host, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(console.root, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                attachOnce(host)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        console.log("hello-window — waiting for the host view to join the window")
    }

    /** L'attach de la forme ACTIVITY, exactement une fois par Activity vivante. */
    private fun attachOnce(host: FrameLayout) {
        if (session != null) return
        console.log("host attached — attachKadre (Activity form)")
        // La forme ACTIVITY : `ComponentActivity.attachKadre(surfaceView, factory)` — appel du
        // thread principal (contrat de thread de l'attach), refus en `KadreResult.Failure`
        // nommant le field fautif, jamais d'exception ordinaire.
        when (
            val result = attachKadre(host, applicationFactory = helloWindowApplication(console::log))
        ) {
            is KadreResult.Success -> {
                session = result.value
                console.log("session attached: ${result.value.id}")
                watchSession(result.value)
            }

            is KadreResult.Failure -> console.log("attach refused: ${result.reason}")
        }
    }

    /** Le cycle de vie de la SESSION — Starting/Running/Stopping/Terminated — dans la console. */
    private fun watchSession(session: KadreSession) {
        // Le scope lifecycle de l'Activity : l'observation meurt avec l'Activity, la session
        // vit sa propre terminaison (portée par le runtime et le binding lifecycle).
        lifecycleScope.launch {
            session.state.collect { state ->
                when (state) {
                    is SessionState.Terminated -> console.log("session terminated: ${state.outcome}")
                    else -> console.log("session: ${state::class.simpleName}")
                }
            }
        }
    }
}

/**
 * L'application kadre minimale du sample : attendre la surface primaire puis la journailler —
 * chaque état de fenêtre (attachement, visibilité, focus, dimensions) devient une ligne.
 * Le `collect` final suspend pour toujours : l'application vit jusqu'à l'arrêt de la session
 * (l'annulation structurée du runtime libère tout).
 */
private fun helloWindowApplication(log: (String) -> Unit): KadreApplicationFactory =
    KadreApplicationFactory {
        // Le récepteur de `KadreApplication.run` EST le KadreScope — l'application vit dans
        // la coroutine structurée de la session.
        KadreApplication {
            log("application launched: $sessionId")
            val surface = primarySurface.filterNotNull().first()
            log("primary surface acquired")
            surface.state.collect { state ->
                val size =
                    "${state.logicalSize.width.roundToInt()}x${state.logicalSize.height.roundToInt()}"
                log(
                    "window: ${state.attachment} ${state.visibility} " +
                        "${state.focus} $size @${state.scaleFactor}",
                )
            }
        }
    }

/**
 * La console du sample : un [TextView] alimenté par un [Handler] main — les collecteurs kadre
 * tournent sur les threads du runtime, la console reste main-only.
 */
private class ConsoleLog(activity: Activity) {

    private val handler = Handler(Looper.getMainLooper())
    private val lines = ArrayDeque<String>()
    private val text = TextView(activity).apply {
        setTextIsSelectable(true)
        textSize = 12f
    }

    /** La vue racine de la console — ajoutée au layout de l'[MainActivity]. */
    val root: View get() = text

    fun log(message: String) {
        handler.post {
            lines.addLast(message)
            while (lines.size > MAX_LINES) lines.removeFirst()
            text.text = lines.joinToString(separator = "\n")
        }
    }

    private companion object {
        const val MAX_LINES = 24
    }
}
