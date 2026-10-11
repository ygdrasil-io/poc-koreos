package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.toPhysical

/**
 * Les six sémantiques du noyau pur d'observation UIKit (miroir d'`androidSurfaceApply`,
 * AndroidSurfaceMetrics.kt:78–193) : resize propagé, scale seul recalcule le physique,
 * safe-area seule touche les insets, thème seul bouge l'apparence sans jamais déduire le
 * contraste, champs `null` n'inventent rien (dédup → `Ignored`), génération périmée → `Stale`,
 * valeurs non finies / <= 0 → `Ignored`.
 */
class UIKitSurfaceObservationTest {
    // La génération de référence n'est PAS 0 : une mutation qui confondrait « génération
    // courante » et « génération littérale 0 » serait tuée ici.
    private val generation = 7L

    // L'état courant des tests : l'invariant `physical == logical.toPhysical(scale)` est
    // respecté par construction (800×2=1600, 600×2=1200, produit exactement représentable).
    private val current = SurfaceMetrics(
        logicalSize = LogicalSize(800.0, 600.0),
        physicalSize = PhysicalSize(1600, 1200),
        scaleFactor = 2.0,
        safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    )

    // « Unknown on first read » : le kernel ne touche JAMAIS le contraste, donc un premier
    // mouvement de thème doit le laisser Unknown tel quel.
    private val appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown)

    @Test
    fun resizePropagatesLogicalAndDerivesPhysical() {
        val reduction = UIKitSurfaceObservationKernel.apply(
            current,
            appearance,
            generation,
            observation(logicalWidth = 390.0, logicalHeight = 844.0),
        )
        val metrics = assertIs<UIKitSurfaceReduction.Metrics>(reduction).metrics
        assertEquals(LogicalSize(390.0, 844.0), metrics.logicalSize)
        // Le physique est DÉRIVÉ (390×2=780, 844×2=1688) — pas observé tel quel comme Android :
        // l'invariant de `SurfaceMetrics` (`physical == logical.toPhysical(scale)`) l'exige.
        assertEquals(PhysicalSize(780, 1688), metrics.physicalSize)
        assertEquals(2.0, metrics.scaleFactor)
        assertEquals(current.safeAreaInsets, metrics.safeAreaInsets)
        assertEquals(appearance, assertIs<UIKitSurfaceReduction.Metrics>(reduction).appearance)

        // Le pont logique→physique est le `toPhysical` de foundation, arrondi Ceil par défaut :
        // 100.25×2 = 200.5 exactement — Ceil donne 201, Floor/TowardZero 200, NearestTiesToEven
        // 200 (tie vers le pair). Le 201 fige le mode d'arrondi de l'invariant.
        val fractional = assertIs<UIKitSurfaceReduction.Metrics>(
            UIKitSurfaceObservationKernel.apply(
                current,
                appearance,
                generation,
                observation(logicalWidth = 100.25, logicalHeight = 100.25),
            ),
        ).metrics
        assertEquals(LogicalSize(100.25, 100.25), fractional.logicalSize)
        assertEquals(PhysicalSize(201, 201), fractional.physicalSize)
    }

    @Test
    fun scaleOnlyChangeRecomputesPhysical() {
        val reduction = UIKitSurfaceObservationKernel.apply(
            current,
            appearance,
            generation,
            observation(scaleFactor = 3.0),
        )
        val metrics = assertIs<UIKitSurfaceReduction.Metrics>(reduction).metrics
        // Le logique ne bouge pas…
        assertEquals(LogicalSize(800.0, 600.0), metrics.logicalSize)
        // …mais le physique DOIT suivre le scale (800×3=2400, 600×3=1800) : garder
        // `current.physicalSize` violerait l'invariant de `SurfaceMetrics` (constructor throw).
        assertEquals(PhysicalSize(2400, 1800), metrics.physicalSize)
        assertEquals(3.0, metrics.scaleFactor)
        assertEquals(current.safeAreaInsets, metrics.safeAreaInsets)
    }

    @Test
    fun safeAreaOnlyChangeLeavesSizeAndScaleUntouched() {
        val reduction = UIKitSurfaceObservationKernel.apply(
            current,
            appearance,
            generation,
            observation(safeAreaTop = 59.0, safeAreaRight = 0.0, safeAreaBottom = 34.0, safeAreaLeft = 0.0),
        )
        val metrics = assertIs<UIKitSurfaceReduction.Metrics>(reduction).metrics
        // Taille et scale INTACTS — seuls les insets bougent.
        assertEquals(current.logicalSize, metrics.logicalSize)
        assertEquals(current.physicalSize, metrics.physicalSize)
        assertEquals(current.scaleFactor, metrics.scaleFactor)
        assertEquals(LogicalInsets(top = 59.0, right = 0.0, bottom = 34.0, left = 0.0), metrics.safeAreaInsets)
    }

    @Test
    fun themeOnlyChangeMovesAppearanceAndNeverContrast() {
        // Depuis (Unknown, Unknown) — première lecture : le thème bouge, le contraste reste
        // Unknown (jamais déduit du thème, miroir Android « le contraste n'est jamais déduit
        // de `uiMode` »).
        val reduction = UIKitSurfaceObservationKernel.apply(
            current,
            appearance,
            generation,
            observation(theme = SurfaceTheme.Dark),
        )
        val result = assertIs<UIKitSurfaceReduction.Metrics>(reduction)
        assertEquals(current, result.metrics)
        assertEquals(SurfaceAppearance(SurfaceTheme.Dark, SurfaceContrast.Unknown), result.appearance)

        // Depuis (Light, High) : Dark ne réinitialise pas le contraste à Normal ni ne le
        // retourne à High/whatever — il reste ce que `current` avait.
        val lightHigh = SurfaceAppearance(SurfaceTheme.Light, SurfaceContrast.High)
        val flipped = assertIs<UIKitSurfaceReduction.Metrics>(
            UIKitSurfaceObservationKernel.apply(
                current,
                lightHigh,
                generation,
                observation(theme = SurfaceTheme.Dark),
            ),
        )
        assertEquals(SurfaceAppearance(SurfaceTheme.Dark, SurfaceContrast.High), flipped.appearance)
    }

    @Test
    fun nullFieldsPublishNothingAndDuplicatesAreIgnored() {
        // Tout-`null` : rien n'est inventé, le candidat est identique au courant → `Ignored`.
        assertEquals(
            UIKitSurfaceReduction.Ignored,
            UIKitSurfaceObservationKernel.apply(current, appearance, generation, observation()),
        )
        // Observation répétant exactement l'état courant (tous champs fournis, thème courant) :
        // le candidat reconstruit est égal → dédup `Ignored` (miroir Android l.174).
        val duplicate = observation(
            logicalWidth = 800.0,
            logicalHeight = 600.0,
            scaleFactor = 2.0,
            safeAreaTop = 0.0,
            safeAreaRight = 0.0,
            safeAreaBottom = 0.0,
            safeAreaLeft = 0.0,
            theme = SurfaceTheme.Unknown,
        )
        assertEquals(
            UIKitSurfaceReduction.Ignored,
            UIKitSurfaceObservationKernel.apply(current, appearance, generation, duplicate),
        )
        // Un champ `null` parmi d'autres garde la valeur courante : insets Top identique au
        // courant, tout le reste `null` → aucun mouvement → `Ignored`.
        val sameTop = observation(safeAreaTop = 0.0)
        assertEquals(
            UIKitSurfaceReduction.Ignored,
            UIKitSurfaceObservationKernel.apply(current, appearance, generation, sameTop),
        )
    }

    @Test
    fun staleGenerationIsRejected() {
        // Génération postérieure (ressource recréée, observation étiquetée trop tôt) → `Stale`.
        assertEquals(
            UIKitSurfaceReduction.Stale,
            UIKitSurfaceObservationKernel.apply(
                current,
                appearance,
                generation,
                observation(logicalWidth = 390.0, logicalHeight = 844.0, generation = generation + 1L),
            ),
        )
        // Génération antérieure (callback tardif d'une ressource remplacée) → `Stale` aussi :
        // c'est une inégalité, pas une borne.
        assertEquals(
            UIKitSurfaceReduction.Stale,
            UIKitSurfaceObservationKernel.apply(
                current,
                appearance,
                generation,
                observation(logicalWidth = 390.0, logicalHeight = 844.0, generation = generation - 1L),
            ),
        )
        // La génération gate AVANT la validité (miroir Android l.172 : premier test du
        // réducteur) — une observation à la fois périmée et invalide est `Stale`.
        assertEquals(
            UIKitSurfaceReduction.Stale,
            UIKitSurfaceObservationKernel.apply(
                current,
                appearance,
                generation,
                observation(logicalWidth = -1.0, generation = generation + 1L),
            ),
        )
    }

    @Test
    fun nonFiniteOrNonPositiveValuesPublishNothing() {
        // Dimensions : 0, négatif, NaN, ±Infini → `Ignored` (précédent Android : une vue non
        // mesurée ne publie rien).
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(
                UIKitSurfaceReduction.Ignored,
                UIKitSurfaceObservationKernel.apply(current, appearance, generation, observation(logicalWidth = bad)),
                "logicalWidth=$bad",
            )
            assertEquals(
                UIKitSurfaceReduction.Ignored,
                UIKitSurfaceObservationKernel.apply(current, appearance, generation, observation(logicalHeight = bad)),
                "logicalHeight=$bad",
            )
        }
        // Scale : mêmes gardes.
        for (bad in listOf(0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(
                UIKitSurfaceReduction.Ignored,
                UIKitSurfaceObservationKernel.apply(current, appearance, generation, observation(scaleFactor = bad)),
                "scaleFactor=$bad",
            )
        }
        // Insets : `LogicalInsets` exige fini et >= 0 — un noyau total ne doit jamais jeter,
        // une observation non mesurable ne publie rien.
        for (bad in listOf(-0.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(
                UIKitSurfaceReduction.Ignored,
                UIKitSurfaceObservationKernel.apply(current, appearance, generation, observation(safeAreaTop = bad)),
                "safeAreaTop=$bad",
            )
        }
        // Rejet ENTIÈREMENT (pas d'application partielle) : un champ valide à côté d'un champ
        // invalide laisse l'état intact.
        assertEquals(
            UIKitSurfaceReduction.Ignored,
            UIKitSurfaceObservationKernel.apply(
                current,
                appearance,
                generation,
                observation(logicalWidth = 100.0, scaleFactor = 0.0),
            ),
        )
    }

    private fun observation(
        logicalWidth: Double? = null,
        logicalHeight: Double? = null,
        scaleFactor: Double? = null,
        safeAreaTop: Double? = null,
        safeAreaRight: Double? = null,
        safeAreaBottom: Double? = null,
        safeAreaLeft: Double? = null,
        theme: SurfaceTheme? = null,
        generation: Long = this.generation,
    ): UIKitSurfaceObservation = UIKitSurfaceObservation(
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight,
        scaleFactor = scaleFactor,
        safeAreaTop = safeAreaTop,
        safeAreaRight = safeAreaRight,
        safeAreaBottom = safeAreaBottom,
        safeAreaLeft = safeAreaLeft,
        theme = theme,
        generation = generation,
    )
}
