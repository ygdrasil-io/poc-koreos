package org.graphiks.kadre.platform.android

import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Pont SDK : installe/rete exactement un observer lifecycle + un listener de détachement
 * de la View (roadmap §3.3 — la frontière copie les données avant tout traitement, les
 * callbacks ordinaires ne suspendent pas). Chaque callback copie l'événement observable,
 * le soumet au [AndroidLifecycleReducer] et délègue la décision : le snapshot d'un
 * `Terminate` est publié via [onDecision] AVANT l'événement terminal via [onTerminal].
 *
 * L'installation relit `view.isAttachedToWindow` et `owner.lifecycle.currentState` APRÈS
 * l'enregistrement : si une précondition a sauté pendant l'installation (course
 * validation/détachement), la relecture produit le terminal immédiatement.
 *
 * `teardown()` retire l'observer et le listener et vide le journal d'installation — le
 * zéro-résidu des bridges de la phase 0, désormais encodé dans le binding lui-même (les
 * bridges de phase 0 sont supprimés avec l'API qu'ils servaient). Idempotent ; sans
 * installation préalable (rollback d'admission), il ne reste rien à retirer et le journal
 * est déjà vide.
 */
internal class AndroidLifecycleBinding(
    private val reducer: AndroidLifecycleReducer,
    private val onDecision: (AndroidLifecycleDecision) -> Unit,
    private val onTerminal: () -> Unit,
) {
    private var lifecycle: Lifecycle? = null
    private var view: View? = null
    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var attachListener: View.OnAttachStateChangeListener? = null
    private var tornDown = false

    /** Journal des installations — vidé par [teardown] (vérification zéro-résidu AND-001). */
    var installedCallbacks: List<String> = emptyList()
        private set

    /** Installe sur l'owner et la View ; relit les préconditions après installation (course). */
    fun install(owner: LifecycleOwner, view: View) {
        if (lifecycleObserver != null) return
        val observer = object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                dispatch(AndroidHostEvent.LifecycleCreated)
            }

            override fun onStart(owner: LifecycleOwner) {
                dispatch(AndroidHostEvent.LifecycleStarted)
            }

            override fun onResume(owner: LifecycleOwner) {
                dispatch(AndroidHostEvent.LifecycleResumed)
            }

            override fun onPause(owner: LifecycleOwner) {
                dispatch(AndroidHostEvent.LifecyclePaused)
            }

            override fun onStop(owner: LifecycleOwner) {
                dispatch(AndroidHostEvent.LifecycleStopped)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                dispatch(AndroidHostEvent.LifecycleDestroyed)
            }
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                dispatch(AndroidHostEvent.ViewAttached)
            }

            override fun onViewDetachedFromWindow(v: View) {
                dispatch(AndroidHostEvent.ViewDetached)
            }
        }
        owner.lifecycle.addObserver(observer)
        view.addOnAttachStateChangeListener(listener)
        lifecycle = owner.lifecycle
        this.view = view
        lifecycleObserver = observer
        attachListener = listener
        installedCallbacks = listOf("lifecycle-observer", "view-attach-listener")
        // Relecture post-installation — l'échelle d'admission a validé avant l'installation ;
        // le détachement (contrat §3.1 : view d'abord) ou la destruction du owner (§3.2 :
        // terminal) survenus pendant l'installation produisent le terminal immédiatement.
        if (!view.isAttachedToWindow) {
            dispatch(AndroidHostEvent.ViewDetached)
        } else if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            dispatch(AndroidHostEvent.LifecycleDestroyed)
        }
    }

    /** Retrait au teardown — zéro callback résiduel. Idempotent. */
    fun teardown() {
        if (tornDown) return
        tornDown = true
        val observer = lifecycleObserver
        val installedLifecycle = lifecycle
        if (observer != null && installedLifecycle != null) {
            installedLifecycle.removeObserver(observer)
        }
        val listener = attachListener
        val installedView = view
        if (listener != null && installedView != null) {
            installedView.removeOnAttachStateChangeListener(listener)
        }
        lifecycle = null
        view = null
        lifecycleObserver = null
        attachListener = null
        installedCallbacks = emptyList()
    }

    /**
     * Frontière SDK : l'événement est copié AVANT tout traitement, puis la décision pure
     * du réducteur est déléguée. Après [teardown], aucun callback résiduel n'agit.
     */
    private fun dispatch(event: AndroidHostEvent) {
        if (tornDown) return
        val copied = event
        when (val decision = reducer.onEvent(copied)) {
            is AndroidLifecycleDecision.Ignore -> Unit
            is AndroidLifecycleDecision.Publish -> onDecision(decision)
            is AndroidLifecycleDecision.Terminate -> {
                // Le snapshot canonique est publié AVANT l'événement terminal (roadmap §3.3).
                onDecision(decision)
                onTerminal()
            }
        }
    }
}
