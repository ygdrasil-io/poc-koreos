package org.graphiks.kadre.platform.android

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.surface.PointerCaptureMode

/**
 * Le commit EN DEUX TEMPS du champ `pointerCapture` (phase 4, tâche 3) — le miroir Android du
 * couple `admitCaptureAttempt`/`commitPointerCapture` web, à la différence que le SDK Android ne
 * répond PAS à l'appel : `View.requestPointerCapture()` ne rend rien, et la SEULE confirmation
 * est le `OnPointerCaptureListener` — « le retour d'une requête SDK n'est pas la confirmation de
 * la capture du pointeur ». La mécanique :
 *
 * - [applyMode] tente la primitive — `Confined` (API 26+) pose `requestCapture` et enregistre la
 *   tentative ; le retour `null` dit « tentative lancée », JAMAIS « capture prise ». `None` pose
 *   `releaseCapture` quand il y a quelque chose à rendre — le release est SYNCHRONE côté SDK,
 *   la surface commite donc `None` immédiatement (la même passe de commit que le web). `Locked`
 *   et `Confined` sous l'API 26 sont refusés champ par champ (`Unsupported(UpdateSurface)`),
 *   zéro appel natif — l'admission de la surface porte les MÊMES règles (le web : « stated here
 *   rather than assumed away ») ; ici elles sont la ceinture de sécurité du seam ;
 * - [listenerChanged] est le SEUL moment du commit : `true` (la capture confirmée par le SDK)
 *   appelle [onCommitted] — la surface publie `Confined` à une révision nouvelle ; `false` (la
 *   perte — focus perdu, événement incapturable) appelle [onLost] — la surface revient à `None`
 *   et publie, SANS rien demander au seam (un `releasePointerCapture` pour un pointeur qui ne
 *   capture plus est un appel sans décision derrière lui, le mot du `reconcilePointerCapture`
 *   web) ;
 * - [focusLost] est le pont P1 : la perte de focus fenêtre que la couture observe déjà (phase 3,
 *   `WindowFocusLost`) emprunte le MÊME chemin que la perte rapportée par le listener — le SDK
 *   libère la capture de lui-même à la perte de focus et le listener le rapportera ; le pont
 *   couvre la cohérence du seam propre.
 *
 * Une capture demandée qui n'est jamais confirmée ne commite rien : l'état dit `None`, la
 * vérité publiée reste celle du SDK. Une confirmation que plus personne n'attend (le gain
 * tardif d'une tentative annulée par `Set(None)`, un gain sans demande) est absorbée — un
 * commit suit toujours une demande vivante.
 *
 * La concurrence est celle de la couture : [applyMode] tourne sous le verrou de la surface
 * (le corps de `apply`) ; [listenerChanged] arrive du thread principal (le listener du SDK).
 * Les deux booléens de tenue sont gardés par un monétaire interne qui ne porte JAMAIS un
 * callback : les callbacks ([onCommitted]/[onLost]) partent APRÈS la section critique, de sorte
 * qu'aucun ordre verrou-seam ne puisse boucler (le thread d'`apply` tient le verrou de surface
 * puis prend le monétaire ; le thread principal prend le monétaire, le rend, PUIS prend le
 * verrou via la publication).
 */
internal class AndroidPointerCapture(
    private val apiLevel: Int,
    private val native: Native,
    private val onCommitted: (PointerCaptureMode) -> Unit,
    private val onLost: () -> Unit,
) {

    /**
     * Le seam natif de la capture — LES primitives du champ `pointerCapture` committable, et
     * rien d'autre. L'inertie des valeurs refusées (`Locked`, tout le champ sous l'API 26) est
     * STRUCTURELLE : le seam ne déclare aucun moyen de verrouiller un pointeur, l'admission
     * refuse avant que le commit puisse appeler, les tests hôtes injectent un recorder qui
     * prouve que seuls `requestCapture`/`releaseCapture`/`setListener` sont jamais posés.
     *
     * Le listener se pose UNE FOIS ([AndroidPointerCapture] l'enregistre à sa construction) ;
     * `null` le retire — la production ne le retire pas : un rapport qui suit la teardown est
     * absorbé par la surface (les publications d'un état détaché ne font rien).
     */
    public interface Native {
        public fun requestCapture()

        public fun releaseCapture()

        public fun setListener(listener: ((hasCapture: Boolean) -> Unit)?)
    }

    /** Le monétaire des booléens de tenue — jamais tenu à travers un callback. */
    private val gate = Any()

    /** Une demande `Confined` est en vol — lancée, pas encore confirmée ni perdue. */
    private var pending: Boolean = false

    /** La capture est CONFIRMÉE et l'état publié la porte (`Confined`). */
    private var held: Boolean = false

    init {
        // Le listener est le SEUL canal de confirmation : il se pose à la construction, avant
        // que la première demande puisse partir — une réponse ne peut jamais trouver le seam
        // sourd.
        native.setListener(::listenerChanged)
    }

    /**
     * Tente le mode demandé, ou rapporte le refus champ par champ — `null` dit « tentative
     * lancée » (ou « rien à faire : la valeur est déjà en effet »), jamais « capture prise ».
     * Appelé sous le verrou de la surface, à la admission du champ — l'admission LANCE la
     * tentative (le mot du brief), de sorte qu'une confirmation que le SDK délivrerait dans la
     * foulée de l'appel atterrisse avant l'instantané du verdict (le verrou JVM est réentrant).
     */
    public fun applyMode(mode: PointerCaptureMode): KadreFailure? = synchronized(gate) {
        when (mode) {
            // D13 : `Locked` appartient à `InteractionAction.LockPointer` (transient
            // activation, phase 4, tâche 4) — le seam ne saurait le poser : refus champ par
            // champ, zéro appel, tout niveau d'API.
            PointerCaptureMode.Locked -> KadreFailure.Unsupported(KadreOperation.UpdateSurface)

            PointerCaptureMode.Confined -> when {
                // Aucune primitive de capture sur cette révision de plateforme — zéro appel.
                apiLevel < 26 -> KadreFailure.Unsupported(KadreOperation.UpdateSurface)
                // Déjà en effet, ou tentative déjà en vol : rien de nouveau à demander — un
                // second `requestCapture` serait un appel sans nouvelle décision derrière lui.
                held || pending -> null
                else -> {
                    native.requestCapture()
                    pending = true
                    null
                }
            }

            // Le release est SYNCHRONE côté SDK : la surface commite `None` immédiatement (la
            // même passe de commit que le web). Rien à rendre — état `None`, aucune tentative
            // en vol — aucun appel : la valeur demandée est déjà celle en effet.
            PointerCaptureMode.None -> {
                if (held || pending) {
                    native.releaseCapture()
                    pending = false
                    held = false
                }
                null
            }
        }
    }

    /**
     * Le rapport du `OnPointerCaptureListener` — le SEUL moment du commit d'une capture :
     * `true` commit `Confined` (révision + publication, par [onCommitted]) ; `false` revient à
     * `None` et publie (par [onLost]), sans rien demander au seam. Les rapports qui ne suivent
     * aucune demande vivante (un gain sans demande, le gain tardif d'une tentative annulée, une
     * perte quand rien n'est tenu) sont absorbés — la publication de la surface dédoublonne de
     * toute façon un état qui ne bouge pas.
     */
    public fun listenerChanged(hasCapture: Boolean) {
        // La décision sous le monétaire, les callbacks HORS section critique (l'ordre des
        // verrous ne doit jamais pouvoir boucler — cf. la KDoc de la classe).
        var confirmed: PointerCaptureMode? = null
        var lost = false
        synchronized(gate) {
            if (hasCapture) {
                if (!pending && !held) return
                pending = false
                if (!held) {
                    held = true
                    confirmed = PointerCaptureMode.Confined
                }
            } else {
                lost = held
                pending = false
                held = false
            }
        }
        confirmed?.let(onCommitted)
        if (lost) onLost()
    }

    /**
     * Le pont P1 : la perte de focus fenêtre observée par la couture (phase 3,
     * `WindowFocusLost`) — le MÊME chemin que la perte rapportée par le listener.
     */
    public fun focusLost() {
        listenerChanged(hasCapture = false)
    }
}

/**
 * L'implémentation PRODUCTION du seam de la capture — la View du host et le canal d'état de la
 * plateforme. Câblée par la couture d'attach sur les DEUX formes ([KadreAndroidAttach] — la
 * capture est une propriété de `View`, la forme View la possède exactement comme la forme
 * Activity : le miroir du curseur).
 *
 * **Le canal d'état, relevé du SDK (`android-35`, valable 26–37) : il n'existe AUCUN
 * enregistrement de listener de capture sur `View`** — `requestPointerCapture`/
 * `releasePointerCapture`/`hasPointerCapture` sont des méthodes, la notification d'état est la
 * surcharge `View.onPointerCaptureChange(boolean)` (dispatchée à la vue focalisée par
 * `View.dispatchPointerCaptureChanged`), inaccessible sur une View fournie par le host, et le
 * seul listener enregistrable — `View.setOnCapturedPointerListener` — ne livre que les
 * ÉVÉNEMENTS pointeur capturés, pas les transitions d'état. Le canal d'état que le SDK offre à
 * une fenêtre est `Window.Callback.onPointerCaptureChanged(boolean)` — la couture d'attach
 * l'ENCHAÎNE déjà sur la forme Activity ([AndroidWindowCallbackForwarder], le même wrapper que
 * l'observation d'entrée) et le livre ICI par [dispatchCaptureState] : le listener posé par le
 * seam ([AndroidPointerCapture] l'enregistre à sa construction) est donc exactement le chemin
 * de production. La forme View n'a pas de fenêtre enchaînée à cette phase (le même fait que
 * l'observation d'entrée, `inputForwarder = null`) : la surface ne peut pas y annoncer le
 * champ — la capability suit le fait câblé (`inputWired`), le refus y reste.
 *
 * La garde d'API vit AU SEAM : `requestPointerCapture`/`releasePointerCapture` et la
 * notification d'état sont API 26+ — le minSdk du module est 24 — chaque primitive est
 * conditionnée par `Build.VERSION.SDK_INT` au moment de l'appel (le motif reconnu par le lint
 * `NewApi`). Les appels de primitive sont postés au thread principal ([Handler] sur le Looper
 * main) : `apply` tourne sur le dispatcher appelant et l'admission tient le verrou de la
 * surface — la demande part à la boucle suivante, fire-and-forget ; le rapport d'état arrive
 * sur le MÊME thread, par le listener du SDK. Un appel qui suit la teardown (View démontée)
 * reste inoffensif — une capture demandée d'une vue détachée ne rappelle rien.
 */
internal class AndroidViewPointerCaptureNative(
    private val view: View,
) : AndroidPointerCapture.Native {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Le listener du seam — posé à sa construction, AVANT que le premier rapport puisse partir. */
    @Volatile
    private var reported: ((hasCapture: Boolean) -> Unit)? = null

    override fun requestCapture() {
        mainHandler.post {
            if (Build.VERSION.SDK_INT >= 26) view.requestPointerCapture()
        }
    }

    override fun releaseCapture() {
        mainHandler.post {
            if (Build.VERSION.SDK_INT >= 26) view.releasePointerCapture()
        }
    }

    override fun setListener(listener: ((hasCapture: Boolean) -> Unit)?) {
        reported = listener
    }

    /**
     * Le canal d'état de la plateforme : la notification `Window.Callback.
     * onPointerCaptureChanged` du SDK, enchaînée par la couture d'attach, livrée ICI dans le
     * listener du seam — le MÊME chemin que le recorder des tests. Thread principal (le
     * dispatch `Window.Callback`), comme le listener du SDK.
     */
    internal fun dispatchCaptureState(hasCapture: Boolean) {
        reported?.invoke(hasCapture)
    }
}
