package org.graphiks.kadre.platform.android

import android.content.res.Configuration
import android.view.View

/**
 * Le branchement SDK des métriques (phase 2, tâche 5) : la couture minimale entre la View du
 * host et la surface observable — les listeners View posés ici publient leurs observations via
 * le [AndroidSurfaceMetricsReducer] (Task 2) dans la surface (Task 1).
 *
 * Sources d'observation, minimal et honnête :
 * - `OnLayoutChangeListener` (additif — jamais un remplacement silencieux du handler du host) :
 *   chaque passe layout relit dimensions, densité, insets (`rootWindowInsets`, lecture directe —
 *   aucun `OnApplyWindowInsetsListener` qui écraserait celui du host) et `uiMode` ;
 * - une lecture initiale POSTÉE sur la file du thread principal : une View attachée mais pas
 *   encore repassée par layout publie quand même ses dimensions courantes — et la lecture ne
 *   court qu'une fois la surface vivante (cf. [install]) ;
 * - visibilité/focus restent aux événements lifecycle déjà observés (P1) : les champs
 *   `visible`/`focused` des observations partent `null` — une observation manque plutôt que
 *   d'inventer (le focus d'une View sans focus donnerait `Unfocused` contre le lifecycle) ;
 * - `Unknown` reste `Unknown` : `uiMode` indéchiffrable laisse l'apparence intacte.
 *
 * Génération : chaque observation est étiquetée avec la génération LUE du réducteur
 * ([AndroidSurfaceMetricsReducer.generation], le getter du carry T4 — jamais un miroir
 * lockstep) ; [teardown] appelle [AndroidSurfaceMetricsReducer.supersedeGeneration] — les
 * callbacks tardifs d'une ressource remplacée ne publieraient jamais.
 *
 * Une View non mesurée (dimensions nulles) ou une densité indécente ne publient rien : le
 * modèle de surface exige des tailles strictement positives, une observation partielle vaut
 * mieux qu'un mensonge. Après [teardown], aucun callback résiduel n'agit (le listener est
 * retiré, le drapeau court-circuite) — le zéro-résidu du binding lifecycle, même discipline.
 */
internal class AndroidSurfaceObservationBinding(
    private val view: View,
    private val reducer: AndroidSurfaceMetricsReducer,
    private val surface: () -> AndroidHostSurface?,
) {
    private var layoutListener: View.OnLayoutChangeListener? = null
    private var tornDown = false

    /**
     * Pose le listener de layout puis fait la lecture initiale. Idempotent.
     *
     * La lecture initiale est POSTÉE sur la file du thread principal (`view.post`) : elle ne
     * peut courir qu'APRÈS le tour d'attach — la surface n'existe pas encore pendant
     * l'installation (la factory de composants tourne dans `controller.attach`), et une
     * observation consommée par le réducteur sans jamais atteindre la surface diviserait les
     * deux (l'apparence lue à l'install, perdue pour la surface, reclasserait la publication
     * suivante). Un runnable posté qui suit un teardown est absorbé par le drapeau.
     */
    fun install() {
        if (layoutListener != null) return
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> observe() }
        view.addOnLayoutChangeListener(listener)
        layoutListener = listener
        view.post { if (!tornDown) observe() }
    }

    /** Retrait au teardown — zéro listener résiduel, génération invalidée. Idempotent. */
    fun teardown() {
        if (tornDown) return
        tornDown = true
        val listener = layoutListener
        if (listener != null) {
            view.removeOnLayoutChangeListener(listener)
        }
        layoutListener = null
        reducer.supersedeGeneration()
    }

    /**
     * La frontière SDK : l'observation est COPIÉE (jamais un type SDK qui traverse), soumise au
     * réducteur, et seule une publication réelle atteint la surface — le replay de l'application
     * pure [androidSurfaceApply] sur la vérité de la surface, le noyau partagé du branchement.
     *
     * Les accesseurs `systemWindowInset*` sont déprécés depuis l'API 29 mais restent les SEULS
     * disponibles sur le plancher (minSdk 24) sans décision de découpage par version — le choix
     * est explicite, pas un oubli.
     */
    @Suppress("DEPRECATION")
    private fun observe() {
        if (tornDown) return
        // Pas de surface, pas d'observation : la consommer ne ferait que diverger le réducteur
        // d'avec la surface — la passe layout suivante (ou la lecture initiale postée)
        // réobservera les mêmes faits quand la surface vivra.
        val host = surface() ?: return
        val width = view.width
        val height = view.height
        // Une View non mesurée ne publie rien : le modèle exige du strictement positif.
        if (width <= 0 || height <= 0) return
        val density = view.resources.displayMetrics.density
        if (!density.isFinite() || density <= 0f) return
        val insets = view.rootWindowInsets
        val nightMode = when (
            view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO -> false
            else -> null
        }
        val observation = AndroidSurfaceObservation(
            physicalWidth = width,
            physicalHeight = height,
            density = density,
            leftInset = insets?.systemWindowInsetLeft ?: 0,
            topInset = insets?.systemWindowInsetTop ?: 0,
            rightInset = insets?.systemWindowInsetRight ?: 0,
            bottomInset = insets?.systemWindowInsetBottom ?: 0,
            // Visibilité/focus : les événements lifecycle déjà observés les portent (P1) ;
            // une observation qui manque conserve le champ courant — jamais inventé.
            visible = null,
            focused = null,
            nightMode = nightMode,
            generation = reducer.generation,
        )
        if (reducer.onObservation(observation) !is AndroidSurfaceChange.Metrics) return
        host.publishMetrics(
            update = { current -> androidSurfaceApply(current, observation).candidate },
            eventFor = ::androidSurfaceEvent,
        )
    }
}
