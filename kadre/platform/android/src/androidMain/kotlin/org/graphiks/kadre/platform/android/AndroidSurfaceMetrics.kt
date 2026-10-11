package org.graphiks.kadre.platform.android

import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toLogical

/**
 * Observation SDK copiée (frontière : jamais le type SDK lui-même — la View, la Configuration
 * ou tout `Rect`/`Configuration` du SDK restent de l'autre côté ; ce ne sont ici que des
 * valeurs copiées). Les trois observations de fenêtre sont nullable : `null` = observation
 * manquante, l'état correspondant est CONSERVÉ — jamais inventé.
 *
 * [generation] est l'étiqette de la ressource observée : les callbacks (layout, insets,
 * `uiMode`) d'une ressource remplacée peuvent arriver après sa recréation ; seule l'observation
 * dont la génération est la génération COURANTE du réducteur est admise.
 */
internal data class AndroidSurfaceObservation(
    val physicalWidth: Int,
    val physicalHeight: Int,
    val density: Float,
    val leftInset: Int,
    val topInset: Int,
    val rightInset: Int,
    val bottomInset: Int,
    val visible: Boolean?,
    val focused: Boolean?,
    val nightMode: Boolean?,
    val generation: Long,
)

/**
 * Ce que l'observation change, causes distinguées (roadmap §Phase 2). `Metrics` signifie
 * « l'observation a publié un état nouveau » : ses trois booléens distinguent les causes
 * géométriques parmi elles (un mouvement qui n'est pas géométrique — focus, visibilité,
 * apparence — sort aussi `Metrics`, tous booléens à faux). `Ignored` : l'état candidat est
 * identique au courant → pas de publication. `Stale` : la génération de l'observation est
 * dépassée → rejeté, état inchangé.
 */
internal sealed interface AndroidSurfaceChange {
    public data class Metrics(
        public val rotated: Boolean,
        public val densityChanged: Boolean,
        public val resized: Boolean,
    ) : AndroidSurfaceChange

    public data object Ignored : AndroidSurfaceChange
    public data object Stale : AndroidSurfaceChange
}

/**
 * Le résultat pur de l'application d'une observation sur un état : le candidat (révision
 * inchangée — c'est la publication qui décide du bump) et les causes géométriques observées.
 * C'est le noyau partagé du réducteur et du branchement surface : le même noyau pur rejoué sur
 * l'état courant de la surface, jamais de dérive entre les deux.
 */
internal data class AndroidSurfaceApplication(
    val candidate: SurfaceState,
    val rotated: Boolean,
    val densityChanged: Boolean,
    val resized: Boolean,
)

/**
 * Applique purement une observation sur un état courant. La densité est le pont : le physique
 * est pris tel qu'observé, le logique et les insets sont la division IEEE de `toLogical`
 * (déterministe par construction). `visible`/`focused`/`nightMode` à `null` laissent le champ
 * correspondant intact — une observation manquante n'invente jamais de valeur (le contraste
 * n'est jamais déduit de `uiMode` non plus).
 */
internal fun androidSurfaceApply(
    current: SurfaceState,
    observation: AndroidSurfaceObservation,
): AndroidSurfaceApplication {
    val scale = observation.density.toDouble()
    val physical = PhysicalSize(observation.physicalWidth, observation.physicalHeight)
    val candidate = current.copy(
        logicalSize = physical.toLogical(scale),
        physicalSize = physical,
        scaleFactor = scale,
        safeAreaInsets = LogicalInsets(
            top = observation.topInset / scale,
            right = observation.rightInset / scale,
            bottom = observation.bottomInset / scale,
            left = observation.leftInset / scale,
        ),
        visibility = when (observation.visible) {
            null -> current.visibility
            true -> SurfaceVisibility.Visible
            false -> SurfaceVisibility.Hidden
        },
        focus = when (observation.focused) {
            null -> current.focus
            true -> SurfaceFocus.Focused
            false -> SurfaceFocus.Unfocused
        },
        appearance = when (observation.nightMode) {
            null -> current.appearance
            true -> current.appearance.copy(theme = SurfaceTheme.Dark)
            false -> current.appearance.copy(theme = SurfaceTheme.Light)
        },
    )
    val dimsChanged = physical != current.physicalSize
    val rotated = dimsChanged &&
        physical.width == current.physicalSize.height &&
        physical.height == current.physicalSize.width
    return AndroidSurfaceApplication(
        candidate = candidate,
        rotated = rotated,
        densityChanged = scale != current.scaleFactor,
        resized = dimsChanged && !rotated,
    )
}

/**
 * L'événement typé d'une publication : UNE cause par publication (le miroir de la surface web,
 * qui émet exactement un événement par publication), choisi par différence avant/après, avec
 * la priorité focus > visibilité > apparence > métriques. Chaque événement porte l'instantané
 * publié entier, donc un mouvement masqué par la priorité reste lisible sur l'événement émis —
 * un domaine n'est jamais étiqueté sous le mauvais type.
 */
internal fun androidSurfaceEvent(
    before: SurfaceState,
    after: SurfaceState,
    stamp: EventStamp,
): SurfaceEvent = when {
    before.focus != after.focus -> SurfaceEvent.FocusChanged(after, stamp)
    before.visibility != after.visibility -> SurfaceEvent.VisibilityChanged(after, stamp)
    before.appearance != after.appearance -> SurfaceEvent.AppearanceChanged(after, stamp)
    else -> SurfaceEvent.MetricsChanged(after, stamp)
}

/**
 * Le réducteur pur des métriques Android → [SurfaceState] (aucun type SDK : l'observation est
 * une copie, cf. [AndroidSurfaceObservation]). La génération est l'étiqette de la ressource :
 * le compteur démarre à 0 et n'appartient qu'au réducteur — le binding qui recrée la ressource
 * appelle [supersedeGeneration] (bump) et étiquette ses observations suivantes avec la
 * génération suivante, en pas jumelés. Une observation dont la génération n'est pas la
 * courante est [AndroidSurfaceChange.Stale] : rejetée, état inchangé (le callback tardif d'une
 * ressource remplacée ne publie jamais).
 *
 * Contrat de thread : le réducteur appartient au binding, qui le possède et ne l'appelle que
 * depuis le thread principal Android ([onObservation], [supersedeGeneration] inclus).
 */
internal class AndroidSurfaceMetricsReducer(initial: SurfaceState) {
    private var state: SurfaceState = initial
    private var currentGeneration: Long = 0L

    /** L'état courant du réducteur — le miroir de ce qu'il a publié. */
    public val currentState: SurfaceState get() = state

    /**
     * La génération courante — l'étiqette que le binding LIT pour étiqueter ses observations
     * après une recréation de ressource : le jumelage se fait sur cette lecture, jamais sur un
     * miroir du compteur maintenu en lockstep à côté du réducteur.
     */
    public val generation: Long get() = currentGeneration

    /**
     * Applique l'observation si sa génération est courante ; retourne la décision. La révision
     * du candidat ne bouge que pour un état qui bouge réellement (le candidat est construit à
     * révision constante, comparé, puis publié avec le bump).
     */
    public fun onObservation(observation: AndroidSurfaceObservation): AndroidSurfaceChange {
        if (observation.generation != currentGeneration) return AndroidSurfaceChange.Stale
        val application = androidSurfaceApply(state, observation)
        if (application.candidate == state) return AndroidSurfaceChange.Ignored
        check(state.revision.value < Long.MAX_VALUE) { "surface revision space exhausted" }
        state = application.candidate.copy(
            revision = SurfaceRevision(state.revision.value + 1L),
        )
        return AndroidSurfaceChange.Metrics(
            rotated = application.rotated,
            densityChanged = application.densityChanged,
            resized = application.resized,
        )
    }

    /**
     * Invalide la génération courante (teardown / recréation de ressource) : les callbacks
     * tardifs de l'ancienne ressource deviennent [AndroidSurfaceChange.Stale].
     */
    public fun supersedeGeneration() {
        currentGeneration += 1L
    }
}
