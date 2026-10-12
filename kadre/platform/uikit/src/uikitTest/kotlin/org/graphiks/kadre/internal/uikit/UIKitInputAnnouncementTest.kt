package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * L'agrégat d'annonce (`UIKitInputAnnouncement`), prouvé sans appareil : le PORTE-UNIQUE de la
 * publication `InputObservationChanged` du port — chaque binding écrit SON fait (celui qu'il
 * possède), chaque écriture retourne le SNAPSHOT COMPLET et courant des quatre faits, que le
 * binding publie tel quel par le sink. Un snapshot publié est donc VRAI à son instant de
 * publication (les bindings se vivent en séquence main), et le dernier d'un cycle de teardown
 * est le all-false + vide.
 *
 * Cibles de mutation : un setter qui NE toucherait que SON champ dans le snapshot publié
 * (l'annonce perdrait les faits des trois autres sources — le runtime croirait le touch
 * désinstallé dès qu'un geste s'annonce) ; un `gestures` qui unirait au lieu de REMPLACIR
 * (les kinds ne redescendraient jamais : la règle « exactement les recognizers installés ») ;
 * un surfaceId qui ne s'échoirait pas.
 */
class UIKitInputAnnouncementTest {
    private val surfaceId = RuntimeProcessIds.nextSurfaceId()

    // MARK: - État initial

    @Test
    fun initialSnapshotIsAllFalseWithNoGestureKind() {
        val snapshot = UIKitInputAnnouncement(surfaceId).snapshot()
        assertEquals(surfaceId, snapshot.surfaceId)
        assertTrue(!snapshot.keyboardInstalled)
        assertTrue(!snapshot.pointerInstalled)
        assertTrue(!snapshot.touchInstalled)
        assertTrue(snapshot.gestureKinds.isEmpty())
    }

    // MARK: - Un fait par source, snapshot complet à chaque écriture

    @Test
    fun touchWriteCarriesTheOtherFactsIntact() {
        val announcement = UIKitInputAnnouncement(surfaceId)
        val afterTouch = announcement.touch(true)
        assertTrue(afterTouch.touchInstalled)
        assertTrue(!afterTouch.keyboardInstalled)
        assertTrue(!afterTouch.pointerInstalled)
        assertTrue(afterTouch.gestureKinds.isEmpty())

        val afterKeyboard = announcement.keyboard(true)
        assertTrue(afterKeyboard.touchInstalled, "the touch fact survives the keyboard write")
        assertTrue(afterKeyboard.keyboardInstalled)

        val afterGestures = announcement.gestures(setOf(GestureKind.Pan, GestureKind.Pinch))
        assertTrue(afterGestures.touchInstalled, "the touch fact survives the gesture write")
        assertTrue(afterGestures.keyboardInstalled, "the keyboard fact survives the gesture write")
        assertEquals(setOf(GestureKind.Pan, GestureKind.Pinch), afterGestures.gestureKinds)
    }

    @Test
    fun snapshotAlwaysEchoesTheCurrentAggregate() {
        val announcement = UIKitInputAnnouncement(surfaceId)
        announcement.touch(true)
        announcement.pointer(true)
        announcement.gestures(setOf(GestureKind.DoubleTap, GestureKind.Rotation, GestureKind.Pan, GestureKind.Pinch))
        val snapshot = announcement.snapshot()
        assertTrue(snapshot.touchInstalled)
        assertTrue(snapshot.pointerInstalled)
        assertTrue(!snapshot.keyboardInstalled)
        assertEquals(
            setOf(GestureKind.DoubleTap, GestureKind.Rotation, GestureKind.Pan, GestureKind.Pinch),
            snapshot.gestureKinds,
        )
        assertEquals(surfaceId, snapshot.surfaceId)
    }

    // MARK: - La règle de l'ensemble exact

    @Test
    fun gesturesReplaceTheWholeSetNeverUnions() {
        val announcement = UIKitInputAnnouncement(surfaceId)
        announcement.gestures(setOf(GestureKind.Pan, GestureKind.Pinch, GestureKind.Rotation, GestureKind.DoubleTap))
        val revoked = announcement.gestures(emptySet())
        assertTrue(revoked.gestureKinds.isEmpty(), "an empty write REPLACES the set (exact-set rule)")
    }

    // MARK: - Le cycle de teardown

    @Test
    fun teardownWritesEndInTheAllFalseRevocation() {
        val announcement = UIKitInputAnnouncement(surfaceId)
        announcement.touch(true)
        announcement.keyboard(true)
        announcement.pointer(true)
        announcement.gestures(setOf(GestureKind.Pan, GestureKind.Pinch, GestureKind.Rotation, GestureKind.DoubleTap))

        // Ordre du port : touch → keyboard → gestures ; chaque retrait publie le snapshot
        // VRAI de son instant, le DERNIER est le all-false + vide.
        val afterTouch = announcement.touch(false)
        assertTrue(afterTouch.keyboardInstalled, "keyboard still lives while touch revokes")
        assertTrue(afterTouch.pointerInstalled)
        assertEquals(4, afterTouch.gestureKinds.size)

        val afterKeyboard = announcement.keyboard(false)
        assertTrue(afterKeyboard.touchInstalled.not())
        assertTrue(afterKeyboard.pointerInstalled, "pointer still lives while keyboard revokes")

        // Le binding gestes révoque SON fait pointeur (le survol installé) puis remplace
        // l'ensemble des gestes par vide — deux écritures, UNE publication (la dernière).
        announcement.pointer(false)
        val afterGestures = announcement.gestures(emptySet())
        assertTrue(!afterGestures.touchInstalled)
        assertTrue(!afterGestures.keyboardInstalled)
        assertTrue(!afterGestures.pointerInstalled)
        assertTrue(afterGestures.gestureKinds.isEmpty(), "the final revocation is the all-false + empty form")
        assertEquals(surfaceId, afterGestures.surfaceId)
    }
}
