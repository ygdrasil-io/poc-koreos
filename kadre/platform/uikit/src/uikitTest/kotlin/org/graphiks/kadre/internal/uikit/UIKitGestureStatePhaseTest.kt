package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La route état-de-recognizer → phase du modèle (`uikitGesturePhaseForState`), prouvée sans
 * appareil. Les valeurs brutes sont celles d'`UIGestureRecognizerState` (UIKitCore, vérifiées
 * dans le SDK) : `Possible` = 0, `Began` = 1, `Changed` = 2, `Ended` = 3 (avec `Recognized`,
 * son alias des gestes discrets, ÉGAL à `Ended`), `Cancelled` = 4, `Failed` = 5 — le klib
 * livre l'état en `Long` brut (même forme que `UITouch.type`, phase 5).
 *
 * La politique des gestes (décision tracée du plan) : seules les observations PORTANT une
 * phase du modèle passent — `Began`/`Changed`/`Ended`. `Possible` n'est pas une observation
 * (l'état de repos), `Cancelled`/`Failed` n'ont pas de chemin (le geste est perdu plutôt que
 * publié en `Cancelled` — la limite est documentée au KDoc du mapper et au rapport : le
 * runtime ne retient AUCUN état de geste actif, il ne peut donc rien rester « suspendu »).
 * Cible de mutation : mapper 4 sur `Cancelled` (le chemin absent réapparaîtrait), ou mapper
 * 0 sur `Began` (le repos deviendrait une observation).
 */
class UIKitGestureStatePhaseTest {
    @Test
    fun beganRawValueMapsToBegan() {
        assertEquals(UIKitTouchPhase.Began, uikitGesturePhaseForState(1L))
    }

    @Test
    fun changedRawValueMapsToMoved() {
        assertEquals(UIKitTouchPhase.Moved, uikitGesturePhaseForState(2L))
    }

    @Test
    fun endedRawValueMapsToEnded() {
        assertEquals(UIKitTouchPhase.Ended, uikitGesturePhaseForState(3L))
    }

    @Test
    fun recognizedAliasEqualsEndedRawValue() {
        // `UIGestureRecognizerStateRecognized = UIGestureRecognizerStateEnded` (header UIKit) :
        // l'alias des gestes discrets EST la valeur 3 — un tap n'archive jamais Began/Changed,
        // il arrive `Ended`. L'assert porte la valeur documentée (3L → `Ended`), là où le test
        // comparait f(3) à f(3) : un mutant qui déporte l'entrée 3 de la table (Began, Moved ou
        // null) échoue ICI, exactement comme sur `endedRawValueMapsToEnded`.
        assertEquals(UIKitTouchPhase.Ended, uikitGesturePhaseForState(3L))
    }

    @Test
    fun possibleIsNotAnObservation() {
        assertNull(uikitGesturePhaseForState(0L))
    }

    @Test
    fun cancelledHasNoPathForGestures() {
        // La décision tracée : aucune phase `Cancelled` pour les gestes — l'observation est
        // perdue explicitement (jamais publiée en Cancelled, jamais suspendue côté runtime).
        assertNull(uikitGesturePhaseForState(4L))
    }

    @Test
    fun failedHasNoPath() {
        assertNull(uikitGesturePhaseForState(5L))
    }

    @Test
    fun unknownAndHostileRawValuesAreLost() {
        assertNull(uikitGesturePhaseForState(6L))
        assertNull(uikitGesturePhaseForState(-1L))
        assertNull(uikitGesturePhaseForState(Long.MIN_VALUE))
        assertNull(uikitGesturePhaseForState(Long.MAX_VALUE))
    }
}
