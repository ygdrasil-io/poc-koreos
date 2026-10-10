package org.graphiks.kadre.platform.android

import android.os.Handler
import android.os.Looper
import android.view.Choreographer

/**
 * Planificateur de frame coalescé : au plus UNE frame en attente ; chaque demande reçue dans la
 * même frame n'admet qu'un seul [onFrame] (une demande qui rejoint la frame pendante est
 * admise, sans re-planifier). Après l'achèvement d'une frame, une nouvelle demande rouvre une
 * frame. [cancel] est le teardown : après son retour, plus jamais de callback — toute demande
 * suivante est refusée, et un corps de frame qui aurait été déçu par la clock (course du SDK)
 * est absorbé par le garde du scheduler lui-même.
 *
 * La clock de frame est injectée (Choreographer en production, directe en test) — la couture
 * SDK seule est injectée, la logique de coalescing est testée ici. Les actions vivent sur le
 * thread principal Android (le contrat de la surface) ; le moniteur interne ne sert qu'à
 * rendre le trio request/cancel/frame inoffensif si une course de threads survenait quand même.
 * [onFrame] est invoqué HORS du moniteur — il ne tient jamais le verrou du scheduler.
 */
internal class AndroidRedrawScheduler(
    private val frameClock: FrameClock,
    private val onFrame: () -> Unit,
) {
    private val guard = Any()
    private var pending: Boolean = false
    private var cancelled: Boolean = false

    /**
     * Demande une frame : `true` si la demande a ouvert (ou rejoint) la frame — l'admission du
     * coalescing ; `false` seulement après [cancel] (le teardown).
     */
    public fun request(): Boolean = synchronized(guard) {
        if (cancelled) return false
        if (pending) return true
        pending = true
        frameClock.schedule(::runFrame)
        true
    }

    /** Teardown : plus jamais de callback après le retour ; toute demande suivante est refusée. */
    public fun cancel() {
        synchronized(guard) {
            cancelled = true
            pending = false
        }
        frameClock.cancel()
    }

    /** Le corps de frame : désarme d'abord, n'invoque [onFrame] que si le scheduler vit encore. */
    private fun runFrame() {
        val run = synchronized(guard) {
            pending = false
            !cancelled
        }
        if (run) onFrame()
    }

    /** La couture SDK seule : poser et déposer une frame. */
    public interface FrameClock {
        public fun schedule(frame: () -> Unit)
        public fun cancel()
    }
}

/**
 * La clock de production : `Choreographer.postFrameCallback` sur le Looper main,
 * `removeFrameCallback` pour le cancel. Le Choreographer est résolu au moment de l'appel, sur
 * le thread principal (via [Handler] si l'appelant n'y est pas — la surface vit sur le main, la
 * délégation n'est là que pour un appelant mal placé). Aucun appel SDK à la construction : les
 * host tests qui n'injectent pas de clock manuelle ne touchent jamais le Choreographer, et un
 * cancel sans frame posée est un no-op.
 */
internal class ChoreographerFrameClock : AndroidRedrawScheduler.FrameClock {
    private val main = Handler(Looper.getMainLooper())
    private var scheduled: Choreographer.FrameCallback? = null

    override fun schedule(frame: () -> Unit) {
        check(scheduled == null) { "the redraw frame clock already holds a frame" }
        onMain {
            val callback = Choreographer.FrameCallback {
                scheduled = null
                frame()
            }
            scheduled = callback
            Choreographer.getInstance().postFrameCallback(callback)
        }
    }

    override fun cancel() {
        onMain {
            val callback = scheduled
            scheduled = null
            if (callback != null) {
                Choreographer.getInstance().removeFrameCallback(callback)
            }
        }
    }

    private inline fun onMain(crossinline action: () -> Unit) {
        if (Looper.myLooper() == main.looper) action() else main.post { action() }
    }
}
