package org.graphiks.kadre.internal.uikit

import kotlin.math.roundToInt
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize

/**
 * Le kernel PUR des faits display iOS/tvOS (phase 8, Task 1) : les scalaires pré-extraits d'un
 * `UIScreen` entrent, la forme runtime [DisplayPortDisplay] sort — RIEN ici n'importe un type
 * UIKit (le pattern jeton du module, miroir [UIKitDropMapping]) : le port de la Task suivante
 * lit le klib (`UIScreen.screens`, `bounds`, `scale`, `mainScreen`) et appelle ce kernel avec
 * des `Double`/`String?`, ce qui rend la traduction testable dans `uikitTest` sans appareil.
 *
 * Deux propriétés font partie du contrat (héritées des precedents Android `AndroidDisplayPort`
 * et web `WebDisplayPort`) :
 *
 * - **Totalité sans fabrication.** Une géométrie non portable (dimension non finie ou non
 *   positive, échelle non finie ou nulle — `displayScale` vaut 0 hors fenêtre) répond `null` :
 *   un échec que le port traduira en `TemporarilyUnavailable`, JAMAIS un display inventé.
 * - **Le type est un paramètre.** La décision HostViewport/Physical appartient à la couture
 *   (ruling inventaire de la phase 8 : l'écran qui porte la scène de la fenêtre hôte EST le
 *   HostViewport de cette scène — le précédent web `WebDisplayPort` ; les écrans attachés
 *   d'aplomb (AirPlay) sont `Physical` ; tvOS énonce UN seul HostViewport) : le kernel porte le
 *   type VENANT, il ne le déduit pas.
 *
 * `workArea` reste `null` TouJOURS : iOS/tvOS n'ont pas de bureau, un rectangle de travail
 * serait un mensonge (précédent Android `AndroidDisplayPort:236`). Les modes sont LE mode
 * unique de l'écran — le klib ne nomme aucun rafraîchissement ni profondeur DE MODE
 * (`UIScreenMode` ne porte qu'une taille ; `maximumFramesPerSecond` appartient à l'écran, pas
 * au mode) : `refreshRateHz`/`bitDepth` restent `null` — null honnête, jamais inventés.
 */
internal data class UIKitDisplayFacts(
    /**
     * La clé de corrélation du port — l'identité stable que la couture attribue à l'écran
     * (l'ObjectIdentifier du `UIScreen` en production). Le kernel la porte telle quelle.
     */
    val key: Long,
    /** Le nom du klib (`UIScreen.name`), ou null quand il n'en rapporte pas — jamais fabriqué. */
    val name: String?,
    /** La largeur des bounds de l'écran, en POINTS logiques (`UIScreen.bounds.size.width`). */
    val boundsWidth: Double,
    /** La hauteur des bounds de l'écran, en POINTS logiques (`UIScreen.bounds.size.height`). */
    val boundsHeight: Double,
    /** L'échelle observée (`UIScreen.scale`) — les points par pixel physique, > 0. */
    val scale: Double,
    /** L'écran est-il celui de la fenêtre hôte ? (information couture, le type reste un paramètre) */
    val isMain: Boolean,
)

/**
 * La clé du mode unique d'un écran UIKit — le précédent web (`WebDisplayPort:184-191`) : une
 * seule entrée, la clé 0, courante par construction. Le klib ne porte pas d'identité stable de
 * mode transposable (`preferredMode` est un objet sans identité portable) : 0 est LA clé.
 */
internal const val UIKIT_SINGLE_MODE_KEY = 0L

/**
 * L'assemblage d'UN écran depuis ses faits pré-extraits — `null` quand la géométrie n'est pas
 * portable (dimension non finie ou non positive, échelle non finie ou non positive).
 *
 * Les bounds deviennent PHYSIQUES (points × échelle, arrondi vers +∞ pour les égalités —
 * `roundToInt`), l'origine reste (0, 0) — aucune position bureau fabriquée ; le mode unique
 * porte la même taille physique et EST le mode courant (`currentModeKey` = sa clé).
 */
internal fun uikitDisplayPortDisplay(
    facts: UIKitDisplayFacts,
    type: DisplayType,
): DisplayPortDisplay? {
    val measurable = facts.boundsWidth.isFinite() && facts.boundsWidth > 0.0 &&
        facts.boundsHeight.isFinite() && facts.boundsHeight > 0.0 &&
        facts.scale.isFinite() && facts.scale > 0.0
    if (!measurable) return null
    val physicalWidth = (facts.boundsWidth * facts.scale).roundToInt()
    val physicalHeight = (facts.boundsHeight * facts.scale).roundToInt()
    if (physicalWidth <= 0 || physicalHeight <= 0) return null
    val bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(physicalWidth, physicalHeight))
    return DisplayPortDisplay(
        key = facts.key,
        type = type,
        name = facts.name,
        bounds = bounds,
        // iOS/tvOS n'ont pas de bureau : la workArea n'existe pas, elle n'est jamais inventée.
        workArea = null,
        scaleFactor = facts.scale,
        currentModeKey = UIKIT_SINGLE_MODE_KEY,
        modes = listOf(
            DisplayPortMode(
                key = UIKIT_SINGLE_MODE_KEY,
                physicalSize = bounds.size,
                // Rien de mode-prouvé au klib : UIScreenMode ne porte qu'une taille.
                refreshRateHz = null,
                bitDepth = null,
            ),
        ),
    )
}

/**
 * L'assemblage de l'INVENTAIRE complet — le primary est l'écran de clé [primaryKey] (l'écran
 * hôte de la scène), sinon le PREMIER écran de l'inventaire (le miroir Android
 * `translatedInventory:220`). `null` quand l'inventaire est vide : tout-ou-rien, un snapshot
 * réussi ne contient jamais un inventaire partiel — un inventaire vide n'est pas un succès.
 */
internal fun uikitDisplaySnapshotOf(
    inventory: List<DisplayPortDisplay>,
    primaryKey: Long?,
): DisplayPortSnapshot? {
    if (inventory.isEmpty()) return null
    val primary = inventory.firstOrNull { it.key == primaryKey } ?: inventory.first()
    return DisplayPortSnapshot(primaryKey = primary.key, displays = inventory)
}
