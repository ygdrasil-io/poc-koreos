package org.graphiks.kadre.samples.hellotouch

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
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.platform.android.attachKadre

/**
 * Le sample migré « hello touch » (kadre-old `samples/hello-touch-android` + le cœur
 * partagé `samples/hello-touch`) : le même attach Kotlin forme ACTIVITY que
 * `android-hello-window` sur une [FrameLayout] ordinaire, plus l'observation du touch —
 * les événements [SurfaceInput][org.graphiks.kadre.input.SurfaceInput] du runtime
 * ([InputEvent]) journalisés dans une console simple.
 *
 * Le contraste kadre-old assumé : PAS de `MotionEvent` surchargé ni de `GestureDetector`
 * maison — le sample N'OBSERVE que ce que le runtime publie (`surface.input.events`, les
 * contacts avec leur phase et leur identité stable), la réduction d'état et la livraison
 * appartiennent à la plateforme.
 */
class MainActivity : ComponentActivity() {

    /** La console bornée du sample — publiée depuis n'importe quel thread, lue sur le main. */
    private lateinit var console: ConsoleLog

    /** La session vivante, attachée UNE fois (garde des reprises d'[onResume]). */
    private var session: KadreSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La console vit APRÈS attachBaseContext : une View construite dans un initialisateur
        // de propriété lirait un contexte de base encore nul (l'ordre d'initialisation Kotlin).
        console = ConsoleLog(this)
        // LE host du sample : une View ordinaire qui reçoit les touches Android réelles —
        // le runtime les observe et les publie réduites dans `surface.input`.
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
        console.log("hello-touch — touch the upper half of the screen")
    }

    /** L'attach de la forme ACTIVITY, exactement une fois par Activity vivante. */
    private fun attachOnce(host: FrameLayout) {
        if (session != null) return
        console.log("host attached — attachKadre (Activity form)")
        when (
            val result = attachKadre(host, applicationFactory = helloTouchApplication(console::log))
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
 * L'application kadre du sample : attendre la surface primaire puis collecter ses événements
 * d'entrée — chaque touch (phase, identité, position), chaque touche, chaque scroll devient
 * une ligne. Le `collect` final suspend pour toujours : l'application vit jusqu'à l'arrêt de
 * la session (l'annulation structurée du runtime libère tout).
 */
private fun helloTouchApplication(log: (String) -> Unit): KadreApplicationFactory =
    KadreApplicationFactory {
        // Le récepteur de `KadreApplication.run` EST le KadreScope — l'application vit dans
        // la coroutine structurée de la session.
        KadreApplication {
            log("application launched: $sessionId")
            val surface = primarySurface.filterNotNull().first()
            log("primary surface acquired — touch it")
            surface.input.events.collect { event -> log(describe(event)) }
        }
    }

/** La ligne de console d'un événement d'entrée — l'observation brute, réduite par le runtime. */
private fun describe(event: InputEvent): String = when (event) {
    is InputEvent.TouchChanged -> "touch ${event.phase} #${event.touchId} @(${fmt(event.position.x)}, ${fmt(event.position.y)})"
    is InputEvent.Key -> "key ${event.logicalKey} ${event.keyState}"
    is InputEvent.Scrolled -> "scroll ${event.delta}"
    else -> "input ${event::class.simpleName}"
}

private fun fmt(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
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
