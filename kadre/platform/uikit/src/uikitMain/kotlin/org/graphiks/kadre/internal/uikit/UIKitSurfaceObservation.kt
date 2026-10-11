package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.toPhysical

/**
 * Observation UIKit copiée (frontière : jamais le type UIKit lui-même — `UIView`, `UITraitCollection`
 * ou tout `CGRect` restent de l'autre côté ; ce ne sont ici que des valeurs copiées par le binding,
 * Task 3). Toutes les observations sont nullable : `null` = observation manquante, l'état
 * correspondant est CONSERVÉ — jamais inventé. Les dimensions et le scale sont en points logiques
 * UIKit (`bounds.size`, `displayScale`) : l'observation est logique d'abord, le physique est DÉRIVÉ
 * par le kernel (miroir inversé d'Android, dont l'observation est physique d'abord).
 *
 * [theme] est DÉJÀ mappé par l'appelant (`UIUserInterfaceStyle` → [SurfaceTheme]) : le kernel ne
 * voit aucun type SDK. Un `theme` non nul remplace le thème, un `theme` nul laisse l'apparence
 * intacte — et le contraste n'est JAMAIS déduit du thème (miroir Android : « le contraste n'est
 * jamais déduit de `uiMode` »).
 *
 * [generation] est l'étiqette de la ressource observée : les callbacks (`layoutSubviews`, traits,
 * safe area) d'une vue remplacée peuvent arriver après sa recréation ; seule l'observation dont la
 * génération est la génération COURANTE est admise.
 */
internal data class UIKitSurfaceObservation(
    val logicalWidth: Double?,
    val logicalHeight: Double?,
    val scaleFactor: Double?,
    val safeAreaTop: Double?,
    val safeAreaRight: Double?,
    val safeAreaBottom: Double?,
    val safeAreaLeft: Double?,
    val theme: SurfaceTheme?,
    val generation: Long,
)

/**
 * Ce que l'observation change, causes distinguées (roadmap §Phase 2). [Metrics] signifie
 * « l'observation a publié un état nouveau » : elle porte à la fois les métriques suivantes ET
 * l'apparence suivante (le thème traverse le même kernel ; le contraste reste ce que `current`
 * avait — Unknown à la première lecture). `Ignored` : le candidat est identique au courant, ou
 * l'observation est non mesurable (valeurs non finies / <= 0) → pas de publication. `Stale` : la
 * génération de l'observation n'est pas la courante → rejeté, état inchangé.
 */
internal sealed interface UIKitSurfaceReduction {
    public data class Metrics(
        public val metrics: SurfaceMetrics,
        public val appearance: SurfaceAppearance,
    ) : UIKitSurfaceReduction

    public data object Ignored : UIKitSurfaceReduction
    public data object Stale : UIKitSurfaceReduction
}

/**
 * Le noyau pur de l'observation métrique UIKit → [SurfaceMetrics] (aucun type UIKit : l'observation
 * est une copie, cf. [UIKitSurfaceObservation]). Miroir direct d'`androidSurfaceApply`
 * (AndroidSurfaceMetrics.kt:78–120) avec le pont INVERSÉ : Android observe le physique et divise
 * (`toLogical`, division IEEE) ; UIKit observe le logique et multiplie (`toPhysical`, arrondi Ceil
 * par défaut de foundation — le même appel que l'invariant de [SurfaceMetrics], donc le candidat le
 * satisfait par construction). `null` = champ inchangé ; une observation identique au courant est
 * [UIKitSurfaceReduction.Ignored] ; une génération non courante est [UIKitSurfaceReduction.Stale]
 * (gate PREMIÈRE, miroir du réducteur Android l.172 — le paramètre [currentGeneration] tient la
 * place du compteur que le réducteur Android possède ; le réducteur UIKit de la Task 3 lira
 * [currentGeneration] de la même façon).
 *
 * Contrat de pureté : zéro import UIKit, aucune mutation, jamais d'exception — une observation non
 * mesurable (dimension non finie ou <= 0 ; scale non fini ou <= 0 ; inset non fini ou négatif,
 * contrat de [LogicalInsets]) ne publie RIEN ([UIKitSurfaceReduction.Ignored]), précédent Android :
 * « une vue non mesurée ne publie rien ». Le rejet est entier, jamais partiel.
 */
internal object UIKitSurfaceObservationKernel {
    public fun apply(
        current: SurfaceMetrics,
        appearance: SurfaceAppearance,
        currentGeneration: Long,
        observation: UIKitSurfaceObservation,
    ): UIKitSurfaceReduction {
        if (observation.generation != currentGeneration) return UIKitSurfaceReduction.Stale

        // Validité d'abord (toute l'observation, jamais un champ) : une valeur non mesurable
        // rend l'observation entière non publiable.
        val width = observation.logicalWidth
        val height = observation.logicalHeight
        val scale = observation.scaleFactor
        if (width != null && !(width.isFinite() && width > 0.0)) return UIKitSurfaceReduction.Ignored
        if (height != null && !(height.isFinite() && height > 0.0)) return UIKitSurfaceReduction.Ignored
        if (scale != null && !(scale.isFinite() && scale > 0.0)) return UIKitSurfaceReduction.Ignored
        val top = observation.safeAreaTop
        val right = observation.safeAreaRight
        val bottom = observation.safeAreaBottom
        val left = observation.safeAreaLeft
        if (top != null && !(top.isFinite() && top >= 0.0)) return UIKitSurfaceReduction.Ignored
        if (right != null && !(right.isFinite() && right >= 0.0)) return UIKitSurfaceReduction.Ignored
        if (bottom != null && !(bottom.isFinite() && bottom >= 0.0)) return UIKitSurfaceReduction.Ignored
        if (left != null && !(left.isFinite() && left >= 0.0)) return UIKitSurfaceReduction.Ignored

        // Les `null` conservent la valeur courante : le candidat est reconstruit champ par champ.
        val logical = LogicalSize(
            width = width ?: current.logicalSize.width,
            height = height ?: current.logicalSize.height,
        )
        val effectiveScale = scale ?: current.scaleFactor
        // L'invariant de `SurfaceMetrics` (`physical == logical.toPhysical(scale)`) : que seul le
        // scale change, seul le logique change ou seuls les insets changent, le physique est
        // recalculé du couple effectif (logique, scale) — jamais repris tel quel du courant.
        val candidate = SurfaceMetrics(
            logicalSize = logical,
            physicalSize = logical.toPhysical(effectiveScale),
            scaleFactor = effectiveScale,
            safeAreaInsets = LogicalInsets(
                top = top ?: current.safeAreaInsets.top,
                right = right ?: current.safeAreaInsets.right,
                bottom = bottom ?: current.safeAreaInsets.bottom,
                left = left ?: current.safeAreaInsets.left,
            ),
        )
        // Le thème traverse le kernel, déjà mappé par l'appelant ; `null` = apparence inchangée,
        // et `copy(theme = …)` ne touche JAMAIS le contraste.
        val nextAppearance = when (observation.theme) {
            null -> appearance
            else -> appearance.copy(theme = observation.theme)
        }
        // Dédup (miroir Android l.174) : candidat identique au courant → pas de publication.
        if (candidate == current && nextAppearance == appearance) return UIKitSurfaceReduction.Ignored
        return UIKitSurfaceReduction.Metrics(metrics = candidate, appearance = nextAppearance)
    }
}
