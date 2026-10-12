package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les règles de VIE d'un contact, prouvées sans appareil : `UIKitTouchAdapter` est le miroir
 * pur d'`AppKitTouchAdapter` (une identité par contact, position invalide perdue, fin à la
 * dernière position valide) — le recognizer UIKit-typed ([UIKitTouchRecognizer]) et le
 * binding ([UIKitTouchBinding]) sont, eux, prouvés par le driver (pattern phase 2).
 */
class UIKitTouchAdapterTest {
    private val surfaceId = RuntimeProcessIds.nextSurfaceId()
    private val adapter = UIKitTouchAdapter(surfaceId)

    // MARK: - Aides

    private fun primitive(
        token: Int,
        phase: UIKitTouchPhase,
        x: Double = 10.0,
        y: Double = 20.0,
        pressure: Double? = null,
        isStylus: Boolean = false,
    ) = UIKitTouchPrimitive(
        identityToken = token,
        phase = phase,
        x = x,
        y = y,
        pressure = pressure,
        isStylus = isStylus,
    )

    private fun startContact(token: Int = 1, x: Double = 10.0, y: Double = 20.0) =
        adapter.convert(primitive(token, UIKitTouchPhase.Began, x, y))

    // MARK: - Started

    @Test
    fun startedPublishesContactAtReportedPosition() {
        val changed = startContact(token = 7, x = 3.5, y = -2.25)
        assertEquals(TouchPhase.Started, changed?.phase)
        assertEquals(7, changed?.nativeIdentity)
        assertEquals(LogicalPoint(3.5, -2.25), changed?.position)
        assertEquals(surfaceId, changed?.surfaceId)
    }

    @Test
    fun startedTwiceForSameTokenLosesSecondContact() {
        assertTrue(startContact(token = 3) != null)
        assertNull(adapter.convert(primitive(3, UIKitTouchPhase.Began, 40.0, 40.0)))
    }

    @Test
    fun startedAtInvalidPositionAdmitsNothing() {
        assertNull(adapter.convert(primitive(4, UIKitTouchPhase.Began, x = Double.NaN)))
        // Le contact n'existe pas : le Moved suivant est perdu, jamais résurrecté.
        assertNull(adapter.convert(primitive(4, UIKitTouchPhase.Moved, 1.0, 1.0)))
    }

    // MARK: - Moved

    @Test
    fun movedPublishesNewPosition() {
        assertTrue(startContact(token = 5) != null)
        val changed = adapter.convert(primitive(5, UIKitTouchPhase.Moved, 15.0, 25.0))
        assertEquals(TouchPhase.Moved, changed?.phase)
        assertEquals(5, changed?.nativeIdentity)
        assertEquals(LogicalPoint(15.0, 25.0), changed?.position)
    }

    @Test
    fun movedForUnknownTokenIsLost() {
        assertNull(adapter.convert(primitive(6, UIKitTouchPhase.Moved, 1.0, 1.0)))
    }

    @Test
    fun movedAtInvalidPositionPublishesNothingAndKeepsLastValid() {
        assertTrue(startContact(token = 8, x = 2.0, y = 3.0) != null)
        assertNull(adapter.convert(primitive(8, UIKitTouchPhase.Moved, x = Double.POSITIVE_INFINITY)))
        // Le contact vit toujours : un Moved valide suivant publie, et une fin invalide
        // retombera sur la DERNIÈRE position valide (celle du Started ici, pas l'infini).
        assertEquals(LogicalPoint(9.0, 9.0), adapter.convert(
            primitive(8, UIKitTouchPhase.Moved, 9.0, 9.0),
        )?.position)
        val ended = adapter.convert(primitive(8, UIKitTouchPhase.Ended, y = Double.NaN))
        assertEquals(TouchPhase.Cancelled, ended?.phase)
        assertEquals(LogicalPoint(9.0, 9.0), ended?.position)
    }

    // MARK: - Ended / Cancelled

    @Test
    fun endedPublishesEndedAtPositionAndRevokesIdentity() {
        assertTrue(startContact(token = 9) != null)
        val ended = adapter.convert(primitive(9, UIKitTouchPhase.Ended, 11.0, 12.0))
        assertEquals(TouchPhase.Ended, ended?.phase)
        assertEquals(LogicalPoint(11.0, 12.0), ended?.position)
        assertNull(adapter.convert(primitive(9, UIKitTouchPhase.Moved, 1.0, 1.0)))
    }

    @Test
    fun cancelledPublishesCancelledAtPositionAndRevokesIdentity() {
        assertTrue(startContact(token = 10) != null)
        val cancelled = adapter.convert(primitive(10, UIKitTouchPhase.Cancelled, 13.0, 14.0))
        assertEquals(TouchPhase.Cancelled, cancelled?.phase)
        assertEquals(LogicalPoint(13.0, 14.0), cancelled?.position)
        assertNull(adapter.convert(primitive(10, UIKitTouchPhase.Moved, 1.0, 1.0)))
    }

    @Test
    fun endedForUnknownTokenIsLost() {
        assertNull(adapter.convert(primitive(11, UIKitTouchPhase.Ended, 1.0, 1.0)))
    }

    // MARK: - Pression (stylet seul, > 0, validée par le kernel)

    @Test
    fun stylusPressureIsCarried() {
        val changed = adapter.convert(primitive(12, UIKitTouchPhase.Began, pressure = 0.42, isStylus = true))
        assertEquals(0.42, changed?.pressure)
    }

    @Test
    fun fingerPressureIsNeverCarried() {
        val changed = adapter.convert(primitive(13, UIKitTouchPhase.Began, pressure = 0.42, isStylus = false))
        assertNull(changed?.pressure)
    }

    @Test
    fun zeroOrInvalidStylusPressureIsNeverCarried() {
        assertNull(adapter.convert(primitive(14, UIKitTouchPhase.Began, pressure = 0.0, isStylus = true))?.pressure)
        assertNull(adapter.convert(primitive(15, UIKitTouchPhase.Began, pressure = 1.7, isStylus = true))?.pressure)
        assertNull(adapter.convert(primitive(16, UIKitTouchPhase.Began, pressure = Double.NaN, isStylus = true))?.pressure)
        assertNull(adapter.convert(primitive(17, UIKitTouchPhase.Began, pressure = null, isStylus = true))?.pressure)
    }

    // MARK: - Lots

    @Test
    fun lotConvertsEachPrimitiveIndependently() {
        val changes = adapter.convert(
            listOf(
                primitive(18, UIKitTouchPhase.Began, 1.0, 1.0),
                primitive(19, UIKitTouchPhase.Began, Double.NaN, 1.0), // perdue
                primitive(18, UIKitTouchPhase.Began, 2.0, 2.0), // doublon perdu
                primitive(20, UIKitTouchPhase.Moved, 3.0, 3.0), // inconnue perdue
            ),
        )
        assertEquals(1, changes.size)
        assertEquals(TouchPhase.Started, changes.single().phase)
        assertEquals(18, changes.single().nativeIdentity)
    }

    // MARK: - Teardown

    @Test
    fun clearRevokesEveryContact() {
        assertTrue(startContact(token = 21) != null)
        adapter.clear()
        assertNull(adapter.convert(primitive(21, UIKitTouchPhase.Moved, 1.0, 1.0)))
        assertNull(adapter.convert(primitive(21, UIKitTouchPhase.Ended, 1.0, 1.0)))
    }

    // MARK: - Identité

    @Test
    fun nativeIdentityIsTheStableTokenBetweenEvents() {
        val started = startContact(token = 22)
        val moved = adapter.convert(primitive(22, UIKitTouchPhase.Moved, 5.0, 5.0))
        val ended = adapter.convert(primitive(22, UIKitTouchPhase.Ended, 6.0, 6.0))
        assertEquals(started?.nativeIdentity, moved?.nativeIdentity)
        assertEquals(moved?.nativeIdentity, ended?.nativeIdentity)
    }
}
