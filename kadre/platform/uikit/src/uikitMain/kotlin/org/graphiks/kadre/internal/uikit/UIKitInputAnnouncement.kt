package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.SurfaceId

/**
 * Le PORTE-UNIQUE de l'annonce d'observation d'input d'un attach (`SurfaceStimulus.
 * InputObservationChanged`) — la coordination tracée de la Task 3 : UNE route de publication pour
 * l'agrégat des quatre faits (clavier, pointeur, touch, gestes), jamais des annonces parcellaires
 * qui se contrediraient (une annonce touch-only publiée APRÈS l'installation des gestes ferait
 * croire au runtime que les gestes n'existent pas — le runtime écrit les quatre champs en absolu à
 * chaque annonce).
 *
 * La règle de coordination : chaque binding possède LA VÉRITÉ de son propre fait et l'écrit ICI au
 * moment exact où elle devient vraie (l'ajout du recognizer à la vue, la pose du handler
 * GameController) ou cesse de l'être (le teardown) ; chaque écriture retourne le SNAPSHOT COMPLET
 * et courant des quatre faits, que le binding publie tel quel par le sink. Un snapshot publié est
 * donc VRAI à son instant de publication — les bindings se vivent en séquence sur la file
 * principale (l'install du port, les handlers de notifications sur la main queue), deux écritures
 * ne se croisent jamais — et le DERNIER snapshot d'un cycle de teardown est le all-false + vide.
 *
 * Aucune deduplication locale : le runtime déduplique déjà un état de capabilities inchangé
 * (`updateObservationCapabilitiesLocked` répond faux sans révision), et un compteur d'annonces qui
 * ne bougerait pas sur une annonce publiée mentirait sur la publication. Pure et sans type UIKit :
 * testée sans appareil ([UIKitInputAnnouncementTest]).
 */
internal class UIKitInputAnnouncement(private val surfaceId: SurfaceId) {
    private var keyboard: Boolean = false
    private var pointer: Boolean = false
    private var touch: Boolean = false
    private var gestures: Set<GestureKind> = emptySet()

    /** L'état courant, sans écriture — le snapshot de l'instant. */
    fun snapshot(): SurfaceStimulus.InputObservationChanged = build()

    /** Le fait clavier écrit (le binding GCKeyboard possède cette vérité), snapshot complet rendu. */
    fun keyboard(installed: Boolean): SurfaceStimulus.InputObservationChanged {
        keyboard = installed
        return build()
    }

    /** Le fait pointeur écrit (le recognizer de survol — iOS seul), snapshot complet rendu. */
    fun pointer(installed: Boolean): SurfaceStimulus.InputObservationChanged {
        pointer = installed
        return build()
    }

    /** Le fait touch écrit (le recognizer touch de la Task 2), snapshot complet rendu. */
    fun touch(installed: Boolean): SurfaceStimulus.InputObservationChanged {
        touch = installed
        return build()
    }

    /**
     * L'ensemble des genres de geste écrit — REMPLACÉ, jamais uni : l'annonce dit EXACTEMENT ce
     * qui est installé (la règle de l'ensemble exact), un remplacement par `emptySet()` est donc
     * la révocation. Snapshot complet rendu.
     */
    fun gestures(installed: Set<GestureKind>): SurfaceStimulus.InputObservationChanged {
        gestures = installed
        return build()
    }

    private fun build(): SurfaceStimulus.InputObservationChanged =
        SurfaceStimulus.InputObservationChanged(
            surfaceId = surfaceId,
            keyboardInstalled = keyboard,
            pointerInstalled = pointer,
            touchInstalled = touch,
            gestureKinds = gestures,
        )
}
