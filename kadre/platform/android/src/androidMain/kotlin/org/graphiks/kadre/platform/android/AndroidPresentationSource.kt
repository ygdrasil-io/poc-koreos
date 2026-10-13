package org.graphiks.kadre.platform.android

import android.app.Presentation
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock

/** État observable du média présentation — piloté par le seam, jamais déduit côté port. */
internal sealed interface AndroidPresentationState {
    /** Pas encore montrée, ou cachée par STOP de l'hôte (le média existe, la session vit). */
    data object Hidden : AndroidPresentationState

    /** Montrée sur l'écran [displayKey] (displayId SDK, clé de corrélation du DisplayPort). */
    data class Shown(val displayKey: Long) : AndroidPresentationState

    /** L'écran [displayKey] a été retiré du système — état FINAL, ni hide ni show ultérieur. */
    data class Removed(val displayKey: Long) : AndroidPresentationState
}

/**
 * Le seam SDK de la présentation secondaire — le vrai couple (Presentation, DisplayListener)
 * en production, un fake pilotable en test hôte. Les méthodes reçoivent et rendent des jetons
 * opaques (`Any`) : le port ne nomme jamais `android.app.Presentation` ni `android.view.Display`.
 */
internal interface AndroidPresentationSource : AutoCloseable {
    val state: StateFlow<AndroidPresentationState>

    /** L'inventaire de faits du média, lu à la construction. */
    val facts: AndroidPresentationFacts

    /** Montre la présentation. Échec si l'état est Removed ou si le show SDK échoue. */
    fun show(): KadreResult<Unit>

    /** Cache la présentation (STOP hôte) — idempotent, sans effet si Hidden. */
    fun hide()

    /**
     * La fenêtre SDK du média montré, ou null tant que Hidden — jeton opaque que la Task 3
     * passe à l'installateur de chaîne input. Stable pour la vie du média (la fenêtre d'une
     * Presentation survit à hide/show).
     */
    fun windowToken(): Any?
}

internal data class AndroidPresentationFacts(
    /** displayId SDK — sert de clé de réservation (Task 2) et de corrélation DisplayPort. */
    val displayKey: Long,
    /** realWidth/realHeight de l'écran au moment de la construction — état initial de surface. */
    val realWidth: Int,
    val realHeight: Int,
    /** densityDpi/160 de l'écran cible. */
    val scaleFactor: Double,
)

/**
 * La machine d'état du média présentation — SDK-FREE à dessein : elle ne connaît du couple
 * (Presentation, DisplayListener) que les gestes qu'on lui injecte à CHAQUE appel, donc le
 * test hôte la prouve via son fake sans android.jar (le stub lance sur les types Presentation
 * — la traduction SDK reste vérifiée sur émulateur, device tests Task 4), et la couture
 * d'attach (Task 2) réutilise cette même machine derrière son propre fake.
 *
 * Les transitions : Hidden→Shown→Hidden au fil des show/hide de l'hôte (le média SURVIT à
 * hide — le show suivant réutilise le même couple (Presentation, display), et le jeton
 * fenêtre retenu au premier show réussi ne bouge plus), et *→Removed au retrait de l'écran —
 * FINAL : ni hide ni show ultérieur, le show d'un média retiré répond la `PlatformFailure`
 * Android du domaine présentation (la famille des autres ports), sans re-geste. Le show
 * injecté peut lancer : la machine TRADUIT (show → `PlatformFailure` portant la classe de la
 * cause) et ABSORBE (hide, dismiss — leurs canaux ne portent pas d'échec, et un écran retiré
 * demeure un fait SDK quoi qu'il en soit du dismiss qui l'accompagne).
 *
 * Elle n'a pas de verrou propre : son porteur l'appelle sous le sien (le `RuntimeLock` du
 * module — [AndroidSdkPresentationSource] en production, jamais `synchronized`).
 */
internal class AndroidPresentationStateMachine(private val displayKey: Long) {
    private val mutableState = MutableStateFlow<AndroidPresentationState>(AndroidPresentationState.Hidden)

    /** L'état observable du média — la seule voix de la machine, jamais déduite ailleurs. */
    val state: StateFlow<AndroidPresentationState> = mutableState.asStateFlow()

    /** Le jeton fenêtre retenu au PREMIER show réussi — vit jusqu'au retrait de l'écran. */
    private var retainedWindow: Any? = null

    /** Vrai dès qu'un show réussi a lu le jeton — lu une fois, jamais relu (stable à vie). */
    private var windowCaptured: Boolean = false

    /** Le dismiss a-t-il déjà couru (retrait OU close) — il ne court JAMAIS deux fois. */
    private var dismissed: Boolean = false

    /** [close] a-t-elle déjà couru — idempotente. */
    private var closed: Boolean = false

    /**
     * Le show de l'hôte : la garde d'abord (un média Removed est final — l'échec est la
     * `PlatformFailure(Android, "presentation", "display-removed")`, la famille des refus
     * plateforme des autres ports), puis le geste SDK injecté (toute exception devient
     * `PlatformFailure(Android, "presentation", "show-exception:<classe>")` — le miroir du
     * capture), et seulement alors `Shown(displayKey)` — avec, au premier show réussi, la
     * capture UNE fois du jeton fenêtre, stable ensuite pour toute la vie du média.
     */
    fun show(showSdk: () -> Unit, windowSdk: () -> Any?): KadreResult<Unit> {
        if (mutableState.value is AndroidPresentationState.Removed) {
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Android, "presentation", "display-removed"),
            )
        }
        return try {
            showSdk()
            if (!windowCaptured) {
                retainedWindow = windowSdk()
                windowCaptured = true
            }
            mutableState.value = AndroidPresentationState.Shown(displayKey)
            KadreResult.Success(Unit)
        } catch (cause: Throwable) {
            KadreResult.Failure(
                KadreFailure.PlatformFailure(
                    KadrePlatform.Android,
                    "presentation",
                    "show-exception:${cause::class.java.simpleName}",
                ),
            )
        }
    }

    /**
     * Le hide de l'hôte (STOP) — idempotent et sans effet si Hidden (le geste ne court que
     * pour un média montré) ni si Removed (final). Le geste n'a pas de canal d'échec : une
     * exception éventuelle est absorbée, l'état passe Hidden — le média vit encore.
     */
    fun hide(hideSdk: () -> Unit) {
        if (mutableState.value !is AndroidPresentationState.Shown) return
        try {
            hideSdk()
        } catch (cause: Throwable) {
            // Le hide SDK est un no-op par construction — un lancement inattendu ne doit pas
            // traverser le STOP de l'hôte, qui ne porte pas de canal d'échec.
        }
        mutableState.value = AndroidPresentationState.Hidden
    }

    /**
     * Le retrait de l'écran — le fait FINAL du SDK : le geste dismiss court UNE fois (retrait
     * OU close, jamais les deux), le jeton fenêtre disparaît avec la fenêtre, et l'état
     * `Removed(displayKey)` interdit tout show ultérieur. Un dismiss qui lance n'y change
     * rien : l'écran est parti, l'état dit le fait.
     */
    fun displayRemoved(dismissSdk: () -> Unit) {
        if (mutableState.value is AndroidPresentationState.Removed) return
        dismissOnce(dismissSdk)
        retainedWindow = null
        mutableState.value = AndroidPresentationState.Removed(displayKey)
    }

    /**
     * La fermeture du seam — idempotente : le geste dismiss ne court que s'il n'a JAMAIS
     * couru (un retrait l'a déjà fait), le reste de la fermeture (désenregistrement du
     * listener SDK) appartient au porteur. Elle ne touche pas à l'état du média : la
     * fermeture accompagne toujours la fin de la session qui le possédait.
     */
    fun close(dismissSdk: () -> Unit) {
        if (closed) return
        closed = true
        dismissOnce(dismissSdk)
    }

    /** Le jeton retenu au premier show réussi — null avant, rappelé à null au retrait. */
    fun windowToken(): Any? = retainedWindow

    private fun dismissOnce(dismissSdk: () -> Unit) {
        if (dismissed) return
        dismissed = true
        try {
            dismissSdk()
        } catch (cause: Throwable) {
            // Le dismiss accompagne un retrait ou une fermeture — aucun canal d'échec ne
            // pourrait le desservir ; le fait SDK (écran parti, seam fermé) tient.
        }
    }
}

/**
 * La réalisation production du seam : le vrai couple (Presentation, DisplayListener) sur
 * l'exact écran cible. Le contentView de l'hôte est posé UNE fois dans la [KadrePresentation]
 * à la construction ; le listener du DisplayManager du contexte externe ne garde que le
 * retrait du displayKey visé (`onDisplayRemoved(id)` où `id == displayKey` → dismiss + état
 * Removed final) et il est retiré dans [close] (la symétrie exacte de
 * `AndroidDisplaySource.unregisterListener`) ; l'état, les gardes et le dismiss-une-fois
 * vivent dans la [AndroidPresentationStateMachine], appelée sous le `RuntimeLock` du module —
 * les fires du listener arrivent sur le main looper comme les show/hide de la couture
 * d'attach, le verrou couvre ce qu'un autre thread pourrait faire.
 */
internal class AndroidSdkPresentationSource(
    outer: Context,
    display: Display,
    contentView: View,
) : AndroidPresentationSource {
    private val lock = RuntimeLock()

    /** Les faits lus à la construction — la même lecture que `AndroidDisplayManagerSource`. */
    override val facts: AndroidPresentationFacts = run {
        val real = Point()
        display.getRealSize(real)
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)
        AndroidPresentationFacts(
            displayKey = display.displayId.toLong(),
            realWidth = real.x,
            realHeight = real.y,
            scaleFactor = metrics.densityDpi / 160.0,
        )
    }

    private val machine = AndroidPresentationStateMachine(facts.displayKey)

    private val presentation = KadrePresentation(outer, display, contentView)

    private val displayManager =
        outer.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) {
            if (displayId.toLong() != facts.displayKey) return
            lock.withLock { machine.displayRemoved { presentation.dismiss() } }
        }
    }

    /** Le listener est posé à la construction — [close] le retirera exactement une fois. */
    private var listenerRegistered: Boolean = false

    init {
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        listenerRegistered = true
    }

    override val state: StateFlow<AndroidPresentationState> = machine.state

    override fun show(): KadreResult<Unit> = lock.withLock {
        machine.show(
            showSdk = { presentation.show() },
            windowSdk = { presentation.window },
        )
    }

    override fun hide() {
        lock.withLock { machine.hide { presentation.hide() } }
    }

    override fun windowToken(): Any? = lock.withLock { machine.windowToken() }

    /**
     * Dismiss la présentation (sauf si le retrait de l'écran l'a déjà fait) et retire
     * exactement le listener posé à la construction — le dismiss vit sous le verrou, le
     * désenregistrement hors du verrou (le miroir de `AndroidDisplayPort.close`). Idempotente :
     * un second close ne re-retire ni ne re-dismiss rien.
     */
    override fun close() {
        val live = lock.withLock {
            if (!listenerRegistered) return@withLock false
            listenerRegistered = false
            machine.close { presentation.dismiss() }
            true
        }
        if (live) displayManager.unregisterDisplayListener(listener)
    }
}

/**
 * La Presentation du média — le contentView de l'hôte posé UNE fois à la construction : Kadre
 * ne monte jamais un second média, la fenêtre (et son jeton) appartient à CE couple
 * (contentView, display) pour toute sa vie.
 */
private class KadrePresentation(
    outer: Context,
    display: Display,
    contentView: View,
) : Presentation(outer, display) {
    init {
        setContentView(contentView)
    }
}
