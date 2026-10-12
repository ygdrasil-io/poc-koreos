package org.graphiks.kadre.platform.android

import android.os.Handler
import android.os.Looper
import android.view.PointerIcon
import android.view.View
import org.graphiks.kadre.surface.CursorIcon

/**
 * La table TOTALE `CursorIcon` → constante `TYPE_*` de [PointerIcon] — le cœur du commit du
 * champ `cursor` (phase 4, tâche 2). TOTALE : chacun des 23 `CursorIcon` de la fondation est
 * SOIT mappé vers la constante Android documentée, SOIT nommé non-mappable (`null`, avec la
 * raison en commentaire) — jamais approximé. Le `when` couvre toutes les valeurs de l'enum :
 * le compilateur refuse toute valeur nouvelle restée sans décision.
 *
 * Les constantes et leur sémantique sont relevées du android-35 (`android.view.PointerIcon`,
 * API 24+ — le minSdk du module) ; les valeurs sont des `const int` Java, inlinées à la
 * compilation, la table ne dépend donc d'aucun SDK au runtime.
 *
 * Les trois non-mappings, chacun avec sa raison (le refus reste champ par champ
 * `Unsupported(UpdateSurface)` côté admission) :
 *
 * - `Progress` : Android n'a pas de composite « flèche + indicateur d'occupation » — la
 *   promesse même du curseur progress (« l'app répond, mais occupe »). [PointerIcon.TYPE_WAIT]
 *   est le busy SEUL : le mapper mentirait sur la réactivité de l'app (Android dessine le busy
 *   quand l'entrée est bloquée) ;
 * - `ResizeColumn` : aucun glyphe col-resize (la barre de partage + double flèche) dans la
 *   table TYPE_* — [PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW] est déjà la forme de
 *   `ResizeHorizontal` et perdre la barre de partage effacerait la distinction que l'app a
 *   demandée ;
 * - `ResizeRow` : même raison, symétrique (row-resize, barre de partage horizontale).
 *
 * Les deux équivalences documentées qui pourraient surprendre (le glyphe Android est LE même
 * dessin que celui que les plateformes de bureau rendent pour ces icônes) :
 *
 * - `Move` → [PointerIcon.TYPE_ALL_SCROLL] : la flèche à quatre directions — Android n'a pas
 *   de TYPE_MOVE distinct, le glyphe all-scroll EST le déplacement omnidirectionnel ;
 * - `NotAllowed` → [PointerIcon.TYPE_NO_DROP] : le cercle barré — Android n'a pas de
 *   not-allowed distinct, le glyphe no-drop EST l'interdit.
 */
internal object AndroidCursorIcons {

    /**
     * La constante `TYPE_*` de l'icône, ou `null` quand l'icône est nommée non-mappable —
     * auquel cas l'admission refuse le champ, le seam n'est jamais appelé.
     */
    public fun systemIconType(icon: CursorIcon): Int? = when (icon) {
        CursorIcon.Default -> PointerIcon.TYPE_DEFAULT
        CursorIcon.Pointer -> PointerIcon.TYPE_HAND
        CursorIcon.Text -> PointerIcon.TYPE_TEXT
        CursorIcon.Crosshair -> PointerIcon.TYPE_CROSSHAIR
        CursorIcon.Move -> PointerIcon.TYPE_ALL_SCROLL
        CursorIcon.Grab -> PointerIcon.TYPE_GRAB
        CursorIcon.Grabbing -> PointerIcon.TYPE_GRABBING
        CursorIcon.NotAllowed -> PointerIcon.TYPE_NO_DROP
        CursorIcon.Wait -> PointerIcon.TYPE_WAIT
        // Non-mappable : aucun composite flèche+busy dans la table TYPE_* (TYPE_WAIT seul
        // prétendrait l'entrée bloquée) — raison portée par la KDoc de l'objet.
        CursorIcon.Progress -> null
        CursorIcon.Help -> PointerIcon.TYPE_HELP
        CursorIcon.ResizeHorizontal -> PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
        CursorIcon.ResizeVertical -> PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
        // Diagonales directionnelles : NW↔SE est la flèche ancrée en haut à gauche,
        // NE↔SW celle ancrée en haut à droite (les KDoc TYPE_* du SDK).
        CursorIcon.ResizeDiagonalNorthWestSouthEast -> PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
        CursorIcon.ResizeDiagonalNorthEastSouthWest -> PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
        // Non-mappables : aucun glyphe col-resize/row-resize (barre de partage) dans la
        // table TYPE_* — raison portée par la KDoc de l'objet.
        CursorIcon.ResizeColumn -> null
        CursorIcon.ResizeRow -> null
        CursorIcon.ZoomIn -> PointerIcon.TYPE_ZOOM_IN
        CursorIcon.ZoomOut -> PointerIcon.TYPE_ZOOM_OUT
        CursorIcon.ContextMenu -> PointerIcon.TYPE_CONTEXT_MENU
        CursorIcon.Copy -> PointerIcon.TYPE_COPY
        CursorIcon.Alias -> PointerIcon.TYPE_ALIAS
        CursorIcon.Cell -> PointerIcon.TYPE_CELL
    }

    /**
     * L'ensemble EXACT des icônes mappées — ce que la capability `cursor` annonce à
     * l'installation ([AndroidHostSurface.observationInstalled]) : l'annonce et la table ne
     * peuvent pas diverger, l'une est dérivée de l'autre.
     */
    internal val committableIcons: Set<CursorIcon> =
        CursorIcon.entries.filterTo(HashSet()) { systemIconType(it) != null }
}

/**
 * Le seam natif du curseur — LA primitive du champ `cursor` committable, et rien d'autre.
 * L'inertie des champs refusés (`Custom(image)`, et toute icône non-mappable) est
 * STRUCTURELLE : le seam ne déclare aucune primitive d'image, l'admission refuse avant que
 * le commit puisse appeler, les tests hôtes injectent un recorder qui prouve que seul
 * `setPointerIcon` est jamais posé.
 *
 * Le contrat du paramètre : `null` = `CursorStyle.Hidden` (l'icône transparente), un `Int`
 * = le TYPE_* de la table [AndroidCursorIcons]. Le readback du commit est CE TYPE PASSÉ —
 * l'implémentation production pose exactement ce qu'elle reçoit, l'état commis ne peut pas
 * diverger de la View.
 */
internal interface AndroidCursorNative {
    public fun setPointerIcon(type: Int?)
}

/**
 * L'implémentation PRODUCTION du seam du curseur — la View du host, rien d'autre. Câblée par
 * la couture d'attach sur les DEUX formes ([AndroidHostSession.attach] — `pointerIcon` est
 * une propriété de `View`, la forme View possède sa vue exactement comme la forme Activity :
 * contrairement aux commandes de fenêtre, le curseur n'est pas réservé à l'Activity).
 *
 * - `type` non-null → `View.pointerIcon = PointerIcon.getSystemIcon(context, type)` — le
 *   readback du commit ;
 * - `type == null` (`Hidden`) → `PointerIcon.getSystemIcon(context, TYPE_NULL)` — l'icône
 *   système « null », transparente. JAMAIS `view.pointerIcon = null` : un `PointerIcon` null
 *   rend la DÉFAUT (la flèche — la KDoc SDK de `TYPE_DEFAULT` le dit explicitement), ce qui
 *   mentirait sur un `Hidden` ;
 *
 * L'appel est posté au thread principal ([Handler] sur le Looper main) : `apply` tourne sur
 * le dispatcher appelant et le commit tient le verrou de la surface — le corps de commit
 * reste non suspendant, la pose de l'icône (SDK synchrones) part à la boucle suivante, fire-
 * and-forget. Aucun chemin d'échec n'existe à rapporter : `getSystemIcon` ne consulte que le
 * cache système, le type reçu est celui que la table a mappé — le readback ne peut pas
 * diverger. Une pose qui suit la teardown (View démontée) reste inoffensive : poser une
 * propriété sur une View détachée ne rappelle rien.
 */
internal class AndroidViewCursorNative(
    private val view: View,
) : AndroidCursorNative {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun setPointerIcon(type: Int?) {
        mainHandler.post {
            view.pointerIcon = if (type == null) {
                PointerIcon.getSystemIcon(view.context, PointerIcon.TYPE_NULL)
            } else {
                PointerIcon.getSystemIcon(view.context, type)
            }
        }
    }
}
