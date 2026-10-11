package org.graphiks.kadre.platform.android

import android.view.MotionEvent
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.LogicalPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Le mapper motion pur de la phase 3 : les NEUF cas épinglés du plan, joués sur
 * [AndroidMotionTracker] et [AndroidMotionMapping] — des valeurs copiées construites à la main,
 * jamais un `MotionEvent` (le stub android.jar lance à la construction, cf. `AndroidViewLeaseTest` ;
 * l'extraction des primitives est la couture SDK de Task 3, prouvée sur émulateur en Task 6).
 *
 * Les miroirs suivis : `WebTouchContacts` (identités mintées au down, retirées au up/cancel —
 * doublon et événements d'un contact retiré ne répondent rien), `WebPointerMotion` (UN pointeur
 * par surface, D11 — delta mesuré entre les deux dernières positions, `(0, 0)` sans précédent),
 * `WebScrollBoundary` (frontière de coalescing strictement croissante, jamais réutilisée).
 * Les constantes `ACTION_*`/`TOOL_TYPE_*`/`BUTTON_*` sont des constantes de compilation.
 */
class AndroidMotionMapperTest {

    private val surfaceId: org.graphiks.kadre.surface.SurfaceId = RuntimeProcessIds.nextSurfaceId()

    private fun tracker(): AndroidMotionTracker = AndroidMotionTracker(surfaceId)

    // -----------------------------------------------------------------------
    // Cas 1 — le DOWN minte une identité de contact stable sur tout le geste
    // -----------------------------------------------------------------------

    @Test
    fun downMintsStableContactIdentity() {
        val tracker = tracker()
        val down = observation(
            MotionEvent.ACTION_DOWN,
            pointers = listOf(PointerSpec(id = 5, x = 30f, y = 40f, pressure = 0.5f)),
            deviceId = 7,
        )
        val downStimuli = tracker.onObservation(down, viewWidth = 100, viewHeight = 200, scaleFactor = 2.0)
        val started = assertIs<SurfaceStimulus.TouchChanged>(downStimuli.single())
        assertEquals(surfaceId, started.surfaceId)
        assertEquals(TouchPhase.Started, started.phase)
        assertEquals(LogicalPoint(15.0, 20.0), started.position)
        assertEquals(0.5, started.pressure)
        assertEquals(DeviceId(7), started.deviceId)

        // L'array source muté après coup ne change pas le stimulus DÉJÀ émis : la copie a lieu
        // à l'entrée du tracker, avant toute dérivation.
        down.xs[0] = 999f
        assertEquals(LogicalPoint(15.0, 20.0), started.position)

        val move = observation(
            MotionEvent.ACTION_MOVE,
            pointers = listOf(PointerSpec(id = 5, x = 50f, y = 60f)),
            deviceId = 7,
        )
        val movedStimuli = tracker.onObservation(move, viewWidth = 100, viewHeight = 200, scaleFactor = 2.0)
        val moved = assertIs<SurfaceStimulus.TouchChanged>(movedStimuli.single())
        assertEquals(TouchPhase.Moved, moved.phase)
        assertSame(started.nativeIdentity, moved.nativeIdentity, "the MOVE carries the identity minted at DOWN")
        assertEquals(LogicalPoint(25.0, 30.0), moved.position)
    }

    // -----------------------------------------------------------------------
    // Cas 2 — MOVE et CANCEL itèrent TOUS les pointeurs ; POINTER_UP un seul Ended
    // -----------------------------------------------------------------------

    @Test
    fun multiTouchIteratesAllPointersOnMoveAndCancel() {
        val tracker = tracker()
        val down = tracker.onObservation(
            observation(MotionEvent.ACTION_DOWN, pointers = listOf(PointerSpec(1, 10f, 10f))),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val identity1 = assertIs<SurfaceStimulus.TouchChanged>(down.single()).nativeIdentity

        val pointerDown1 = tracker.onObservation(
            observation(
                MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1,
                pointers = listOf(PointerSpec(1, 10f, 10f), PointerSpec(2, 20f, 20f)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val secondStarted = assertIs<SurfaceStimulus.TouchChanged>(pointerDown1.single())
        assertEquals(LogicalPoint(20.0, 20.0), secondStarted.position, "POINTER_UP family delivers the actionIndex pointer")
        val identity2 = secondStarted.nativeIdentity
        assertNotSame(identity1, identity2)

        val pointerDown2 = tracker.onObservation(
            observation(
                MotionEvent.ACTION_POINTER_DOWN, actionIndex = 2,
                pointers = listOf(PointerSpec(1, 10f, 10f), PointerSpec(2, 20f, 20f), PointerSpec(3, 30f, 30f)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val identity3 = assertIs<SurfaceStimulus.TouchChanged>(pointerDown2.single()).nativeIdentity

        // MOVE à trois pointeurs : trois TouchChanged Moved, dans l'ordre des pointeurs.
        val move = tracker.onObservation(
            observation(
                MotionEvent.ACTION_MOVE,
                pointers = listOf(PointerSpec(1, 11f, 11f), PointerSpec(2, 21f, 21f), PointerSpec(3, 31f, 31f)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        assertEquals(3, move.size, "a MOVE iterates ALL pointers, one TouchChanged each")
        val movedIdentities = move.map { assertIs<SurfaceStimulus.TouchChanged>(it) }
        assertEquals(listOf(LogicalPoint(11.0, 11.0), LogicalPoint(21.0, 21.0), LogicalPoint(31.0, 31.0)), movedIdentities.map { it.position })
        assertEquals(listOf(identity1, identity2, identity3), movedIdentities.map { it.nativeIdentity })
        assertTrue(movedIdentities.all { it.phase == TouchPhase.Moved })

        // ACTION_CANCEL : un Cancelled par contact actif, identités retirées ensuite.
        val cancel = tracker.onObservation(
            observation(
                MotionEvent.ACTION_CANCEL,
                pointers = listOf(PointerSpec(1, 11f, 11f), PointerSpec(2, 21f, 21f), PointerSpec(3, 31f, 31f)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        assertEquals(3, cancel.size, "a CANCEL delivers Cancelled for EVERY active contact")
        val cancelled = cancel.map { assertIs<SurfaceStimulus.TouchChanged>(it) }
        assertEquals(listOf(identity1, identity2, identity3), cancelled.map { it.nativeIdentity })
        assertTrue(cancelled.all { it.phase == TouchPhase.Cancelled })

        // Les identités sont retirées : les MOVE suivants ne répondent plus rien.
        assertTrue(
            tracker.onObservation(
                observation(MotionEvent.ACTION_MOVE, pointers = listOf(PointerSpec(1, 12f, 12f))),
                viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
            ).isEmpty(),
            "a move of a retired contact answers nothing",
        )

        // UP partiel : exactement UN Ended, à l'actionIndex — le geste des autres continue.
        val partial = tracker()
        val firstDown = partial.onObservation(
            observation(MotionEvent.ACTION_DOWN, pointers = listOf(PointerSpec(1, 10f, 10f))),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val firstIdentity = assertIs<SurfaceStimulus.TouchChanged>(firstDown.single()).nativeIdentity
        val partialPointerDown = assertIs<SurfaceStimulus.TouchChanged>(
            partial.onObservation(
                observation(
                    MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1,
                    pointers = listOf(PointerSpec(1, 10f, 10f), PointerSpec(2, 20f, 20f)),
                ),
                viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
            ).single(),
        )
        val secondIdentity = partialPointerDown.nativeIdentity
        // Un DOWN doublon d'un contact vivant n'est pas un second contact : il ne répond rien.
        assertTrue(
            partial.onObservation(
                observation(
                    MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1,
                    pointers = listOf(PointerSpec(1, 10f, 10f), PointerSpec(2, 22f, 22f)),
                ),
                viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
            ).isEmpty(),
            "a duplicate down of a live contact answers nothing",
        )
        val up = partial.onObservation(
            observation(
                MotionEvent.ACTION_POINTER_UP, actionIndex = 1,
                pointers = listOf(PointerSpec(1, 10f, 10f), PointerSpec(2, 22f, 22f)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val ended = assertIs<SurfaceStimulus.TouchChanged>(up.single())
        assertEquals(TouchPhase.Ended, ended.phase)
        assertSame(secondIdentity, ended.nativeIdentity, "a POINTER_UP ends exactly the actionIndex contact")
        val surviving = partial.onObservation(
            observation(
                MotionEvent.ACTION_MOVE,
                pointers = listOf(PointerSpec(1, 13f, 13f), PointerSpec(2, 23f, 23f)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        assertEquals(1, surviving.size, "the retired contact no longer answers; the gesture of the survivor continues")
        val survivingMoved = assertIs<SurfaceStimulus.TouchChanged>(surviving.single())
        assertSame(firstIdentity, survivingMoved.nativeIdentity)
        assertEquals(LogicalPoint(13.0, 13.0), survivingMoved.position)
    }

    // -----------------------------------------------------------------------
    // Cas 3 — continuité au-delà des bornes ; un DOWN hors bornes n'est JAMAIS livré
    // -----------------------------------------------------------------------

    @Test
    fun gestureContinuityBeyondBounds() {
        val tracker = tracker()
        tracker.onObservation(
            observation(MotionEvent.ACTION_DOWN, pointers = listOf(PointerSpec(5, 10f, 10f))),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        // Le contact a commencé DANS les bornes : son geste continue hors bornes, livré.
        val beyond = tracker.onObservation(
            observation(MotionEvent.ACTION_MOVE, pointers = listOf(PointerSpec(5, 150f, -20f))),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val movedBeyond = assertIs<SurfaceStimulus.TouchChanged>(beyond.single())
        assertEquals(TouchPhase.Moved, movedBeyond.phase)
        assertEquals(LogicalPoint(75.0, -10.0), movedBeyond.position)
        val upBeyond = tracker.onObservation(
            observation(MotionEvent.ACTION_UP, pointers = listOf(PointerSpec(5, 150f, -20f))),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        assertEquals(TouchPhase.Ended, assertIs<SurfaceStimulus.TouchChanged>(upBeyond.single()).phase)

        // Un DOWN hors bornes ne minte RIEN : son geste entier est muet, pour toujours.
        val outsideDown = tracker.onObservation(
            observation(MotionEvent.ACTION_DOWN, pointers = listOf(PointerSpec(9, 200f, 50f))),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        assertTrue(outsideDown.isEmpty(), "a down outside the bounds is never delivered")
        assertTrue(
            tracker.onObservation(
                observation(MotionEvent.ACTION_MOVE, pointers = listOf(PointerSpec(9, 210f, 60f))),
                viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
            ).isEmpty(),
            "the outside contact has no gesture continuity to deliver",
        )
        assertTrue(
            tracker.onObservation(
                observation(MotionEvent.ACTION_UP, pointers = listOf(PointerSpec(9, 210f, 60f))),
                viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
            ).isEmpty(),
        )

        // Le bord est exclusif : x == viewWidth est HORS bornes. Le POINTER_NEW suit la même
        // règle par contact : un second doigt posé hors bornes n'ouvre pas de contact.
        assertTrue(
            tracker.onObservation(
                observation(MotionEvent.ACTION_DOWN, pointers = listOf(PointerSpec(8, 100f, 50f))),
                viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
            ).isEmpty(),
        )
        tracker.onObservation(
            observation(MotionEvent.ACTION_DOWN, pointers = listOf(PointerSpec(6, 10f, 10f))),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        assertTrue(
            tracker.onObservation(
                observation(
                    MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1,
                    pointers = listOf(PointerSpec(6, 10f, 10f), PointerSpec(7, 120f, 10f)),
                ),
                viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
            ).isEmpty(),
            "a POINTER_DOWN outside the bounds mints nothing",
        )
    }

    // -----------------------------------------------------------------------
    // Cas 4 — souris et stylet : UN SEUL pointeur par surface (D11)
    // -----------------------------------------------------------------------

    @Test
    fun mouseAndPenAreOnePointer() {
        assertEquals(PointerKind.Mouse, AndroidMotionMapping.pointerKind(MotionEvent.TOOL_TYPE_MOUSE))
        assertEquals(PointerKind.Mouse, AndroidMotionMapping.pointerKind(MotionEvent.TOOL_TYPE_FINGER))
        assertEquals(PointerKind.Pen, AndroidMotionMapping.pointerKind(MotionEvent.TOOL_TYPE_STYLUS))
        assertEquals(PointerKind.Eraser, AndroidMotionMapping.pointerKind(MotionEvent.TOOL_TYPE_ERASER))
        assertEquals(PointerKind.Unknown, AndroidMotionMapping.pointerKind(MotionEvent.TOOL_TYPE_UNKNOWN))

        val tracker = tracker()
        val enter = tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_ENTER,
                pointers = listOf(PointerSpec(0, 10f, 20f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val entered = assertIs<SurfaceStimulus.PointerEntered>(enter.single())
        assertEquals(PointerKind.Mouse, entered.kind)
        assertEquals(LogicalPoint(5.0, 10.0), entered.position)

        val move = tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_MOVE,
                pointers = listOf(PointerSpec(0, 12f, 24f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val moved = assertIs<SurfaceStimulus.PointerMoved>(move.single())
        assertEquals(PointerKind.Mouse, moved.kind)
        assertEquals(LogicalPoint(6.0, 12.0), moved.position)
        assertEquals(org.graphiks.kadre.surface.LogicalDelta(1.0, 2.0), moved.delta)
        assertNull(moved.pressure, "a mouse that reported no pressure carries none")
        assertNull(moved.pen, "a mouse never carries a pen state")

        val press = tracker.onObservation(
            observation(
                MotionEvent.ACTION_BUTTON_PRESS, actionButton = MotionEvent.BUTTON_PRIMARY,
                pointers = listOf(PointerSpec(0, 12f, 24f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val pressed = assertIs<SurfaceStimulus.PointerButtonChanged>(press.single())
        assertEquals(PointerKind.Mouse, pressed.kind)
        assertEquals(PointerButton.Primary, pressed.button)
        assertEquals(PointerButtonState.Pressed, pressed.buttonState)

        val exit = tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_EXIT,
                pointers = listOf(PointerSpec(0, 12f, 24f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        assertEquals(PointerKind.Mouse, assertIs<SurfaceStimulus.PointerLeft>(exit.single()).kind)

        // Le stylet suit le MÊME chemin, avec son genre et son état de stylet (tilt non
        // observé par cette observation → champs null, jamais inventés).
        tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_ENTER,
                pointers = listOf(PointerSpec(0, 10f, 20f, pressure = 0.5f, tool = MotionEvent.TOOL_TYPE_STYLUS)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val penMove = tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_MOVE,
                pointers = listOf(PointerSpec(0, 14f, 24f, pressure = 0.5f, tool = MotionEvent.TOOL_TYPE_STYLUS)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val penMoved = assertIs<SurfaceStimulus.PointerMoved>(penMove.single())
        assertEquals(PointerKind.Pen, penMoved.kind)
        assertEquals(LogicalPoint(7.0, 12.0), penMoved.position)
        assertEquals(0.5, penMoved.pressure)
        val penState = penMoved.pen
        assertTrue(penState != null, "a stylus carries a pen state payload")
        assertNull(penState.tiltXDegrees)
        assertNull(penState.tiltYDegrees)
        assertNull(penState.twistRadians)
        assertNull(penState.tangentialPressure)

        // La gomme : le genre Eraser, même chemin.
        val eraserMove = tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_MOVE,
                pointers = listOf(PointerSpec(0, 16f, 24f, tool = MotionEvent.TOOL_TYPE_ERASER)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        assertEquals(PointerKind.Eraser, assertIs<SurfaceStimulus.PointerMoved>(eraserMove.single()).kind)

        // Fusion D11 : souris puis stylet dans le MÊME tracker — le delta du stylet se mesure
        // depuis la DERNIÈRE position du pointeur unique (celle de la gomme), preuve qu'un seul
        // état de pointeur existe pour toute la surface.
        val fusionMove = tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_MOVE,
                pointers = listOf(PointerSpec(0, 20f, 24f, tool = MotionEvent.TOOL_TYPE_STYLUS)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val fusion = assertIs<SurfaceStimulus.PointerMoved>(fusionMove.single())
        assertEquals(org.graphiks.kadre.surface.LogicalDelta(2.0, 0.0), fusion.delta)
        assertEquals(PointerKind.Pen, fusion.kind)
    }

    // -----------------------------------------------------------------------
    // Cas 5 — hover : Entered/Moved/Left, jamais de bouton porté par le hover
    // -----------------------------------------------------------------------

    @Test
    fun hoverDeliversEnteredMovedLeftWithoutButtons() {
        val tracker = tracker()
        val stimuli = listOf(
            MotionEvent.ACTION_HOVER_ENTER to (10f to 10f),
            MotionEvent.ACTION_HOVER_MOVE to (14f to 12f),
            MotionEvent.ACTION_HOVER_EXIT to (14f to 12f),
        ).flatMap { (action, position) ->
            tracker.onObservation(
                observation(
                    action,
                    pointers = listOf(PointerSpec(0, position.first, position.second, tool = MotionEvent.TOOL_TYPE_MOUSE)),
                    buttons = MotionEvent.BUTTON_SECONDARY,
                ),
                viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
            )
        }
        assertEquals(3, stimuli.size, "hover delivers exactly its three phases")
        val kinds = stimuli.map {
            when (it) {
                is SurfaceStimulus.PointerEntered -> "entered"
                is SurfaceStimulus.PointerMoved -> "moved"
                is SurfaceStimulus.PointerLeft -> "left"
                else -> "other"
            }
        }
        assertEquals(listOf("entered", "moved", "left"), kinds, "no button stimulus rides on a hover action")
        val moved = assertIs<SurfaceStimulus.PointerMoved>(stimuli[1])
        assertEquals(org.graphiks.kadre.surface.LogicalDelta(4.0, 2.0), moved.delta)

        // Une ré-entrée mesure depuis SA propre entrée : le left a oublié la position.
        tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_ENTER,
                pointers = listOf(PointerSpec(0, 50f, 50f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val reEntered = tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_MOVE,
                pointers = listOf(PointerSpec(0, 52f, 50f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        assertEquals(org.graphiks.kadre.surface.LogicalDelta(2.0, 0.0), assertIs<SurfaceStimulus.PointerMoved>(reEntered.single()).delta)
    }

    // -----------------------------------------------------------------------
    // Cas 6 — scroll : axes relatifs → Logical, notches → Lines, frontière croissante
    // -----------------------------------------------------------------------

    @Test
    fun scrollDeliversDeltaWithCoalescingBoundary() {
        val tracker = tracker()

        val logical = tracker.onObservation(
            observation(MotionEvent.ACTION_SCROLL, axisXrelative = 3f, axisYrelative = -2f),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val first = assertIs<SurfaceStimulus.Scroll>(logical.single())
        val logicalDelta = assertIs<ScrollDelta.Logical>(first.delta)
        assertEquals(3.0, logicalDelta.x)
        assertEquals(-2.0, logicalDelta.y)
        val firstBoundary = first.coalescingBoundary

        val notched = tracker.onObservation(
            observation(MotionEvent.ACTION_SCROLL, axisHscroll = 1f, axisVscroll = 2f),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val second = assertIs<SurfaceStimulus.Scroll>(notched.single())
        val linesDelta = assertIs<ScrollDelta.Lines>(second.delta)
        assertEquals(1.0, linesDelta.x)
        assertEquals(2.0, linesDelta.y)
        assertTrue(second.coalescingBoundary > firstBoundary, "the frontier strictly increases")

        val notchedAgain = tracker.onObservation(
            observation(MotionEvent.ACTION_SCROLL, axisHscroll = 1f, axisVscroll = 2f),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val third = assertIs<SurfaceStimulus.Scroll>(notchedAgain.single())
        assertTrue(third.coalescingBoundary > second.coalescingBoundary, "a boundary already published is never handed out again")

        // Les deux familles observées sur UN event : un stimulus par famille, la MÊME
        // frontière (une observation native = une unité de livraison).
        val both = tracker.onObservation(
            observation(
                MotionEvent.ACTION_SCROLL,
                axisHscroll = 1f, axisXrelative = 1f,
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        assertEquals(2, both.size)
        assertIs<ScrollDelta.Logical>(assertIs<SurfaceStimulus.Scroll>(both[0]).delta)
        assertIs<ScrollDelta.Lines>(assertIs<SurfaceStimulus.Scroll>(both[1]).delta)
        assertEquals(
            assertIs<SurfaceStimulus.Scroll>(both[0]).coalescingBoundary,
            assertIs<SurfaceStimulus.Scroll>(both[1]).coalescingBoundary,
        )

        // Un scroll dont aucun axe n'est observé reste une observation (miroir web : la
        // frontière avance, le delta porté est le zéro honnête).
        val silent = tracker.onObservation(
            observation(MotionEvent.ACTION_SCROLL),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val last = assertIs<SurfaceStimulus.Scroll>(silent.single())
        val zeroDelta = assertIs<ScrollDelta.Logical>(last.delta)
        assertEquals(0.0, zeroDelta.x)
        assertEquals(0.0, zeroDelta.y)
        assertTrue(last.coalescingBoundary > assertIs<SurfaceStimulus.Scroll>(both[0]).coalescingBoundary)
    }

    // -----------------------------------------------------------------------
    // Cas 7 — les payloads sont copiés, jamais référencés
    // -----------------------------------------------------------------------

    @Test
    fun payloadsAreCopiedNotReferenced() {
        val tracker = tracker()
        val down = observation(
            MotionEvent.ACTION_DOWN,
            pointers = listOf(PointerSpec(id = 7, x = 30f, y = 40f, pressure = 0.5f)),
        )
        val started = assertIs<SurfaceStimulus.TouchChanged>(
            tracker.onObservation(down, viewWidth = 100, viewHeight = 200, scaleFactor = 2.0).single(),
        )

        // Mutation de TOUTES les arrays sources après coup.
        down.xs[0] = 500f
        down.ys[0] = 500f
        down.pointerIds[0] = 99
        down.pressures[0] = 0.9f
        down.toolTypes[0] = MotionEvent.TOOL_TYPE_STYLUS

        assertEquals(LogicalPoint(15.0, 20.0), started.position)
        assertEquals(0.5, started.pressure)

        // Le contact reste clé sur l'identité COPIÉE à l'entrée : un MOVE de l'id 7 d'origine
        // est livré ; un MOVE de l'id 99 (l'array muté) ne répond rien.
        val move = tracker.onObservation(
            observation(MotionEvent.ACTION_MOVE, pointers = listOf(PointerSpec(7, 50f, 60f))),
            viewWidth = 100, viewHeight = 200, scaleFactor = 2.0,
        )
        assertEquals(1, move.size, "the tracker keyed the contact on the copied id, not the mutated array")
        assertTrue(
            tracker.onObservation(
                observation(MotionEvent.ACTION_MOVE, pointers = listOf(PointerSpec(99, 1f, 1f))),
                viewWidth = 100, viewHeight = 200, scaleFactor = 2.0,
            ).isEmpty(),
        )

        // Même preuve sur le lane pointeur : le kind dérivé du toolType copié ne bouge plus.
        val pen = observation(
            MotionEvent.ACTION_HOVER_MOVE,
            pointers = listOf(PointerSpec(0, 10f, 10f, tool = MotionEvent.TOOL_TYPE_STYLUS)),
        )
        val penMoved = assertIs<SurfaceStimulus.PointerMoved>(
            tracker.onObservation(pen, viewWidth = 100, viewHeight = 200, scaleFactor = 2.0).single(),
        )
        pen.toolTypes[0] = MotionEvent.TOOL_TYPE_MOUSE
        assertEquals(PointerKind.Pen, penMoved.kind)
    }

    // -----------------------------------------------------------------------
    // Cas 8 — reset() oublie tout SANS émettre ; le DOWN suivant re-mint du frais
    // -----------------------------------------------------------------------

    @Test
    fun resetClearsActiveContacts() {
        val tracker = tracker()
        val down = tracker.onObservation(
            observation(MotionEvent.ACTION_DOWN, pointers = listOf(PointerSpec(1, 10f, 10f))),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val firstIdentity = assertIs<SurfaceStimulus.TouchChanged>(down.single()).nativeIdentity
        val scroll = tracker.onObservation(
            observation(MotionEvent.ACTION_SCROLL, axisVscroll = 1f),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val boundaryBeforeReset = assertIs<SurfaceStimulus.Scroll>(scroll.single()).coalescingBoundary

        tracker.reset()

        // Le prochain DOWN re-mint une identité FRAÎCHE — jamais l'identité d'avant le reset.
        val renewed = tracker.onObservation(
            observation(MotionEvent.ACTION_DOWN, pointers = listOf(PointerSpec(1, 10f, 10f))),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        val renewedIdentity = assertIs<SurfaceStimulus.TouchChanged>(renewed.single()).nativeIdentity
        assertNotSame(firstIdentity, renewedIdentity, "reset retires every minted identity for good")

        // Le lane pointeur aussi est oublié : un hover move sans précédent mesure (0, 0).
        val orphanMove = tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_MOVE,
                pointers = listOf(PointerSpec(0, 20f, 20f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        assertEquals(
            org.graphiks.kadre.surface.LogicalDelta(0.0, 0.0),
            assertIs<SurfaceStimulus.PointerMoved>(orphanMove.single()).delta,
        )

        // La frontière de scroll ne recule JAMAIS (miroir WebScrollBoundary.clear : le
        // compteur survit, ce qui a été publié ne repart pas de zéro).
        val scrollAfter = tracker.onObservation(
            observation(MotionEvent.ACTION_SCROLL, axisVscroll = 1f),
            viewWidth = 100, viewHeight = 100, scaleFactor = 1.0,
        )
        assertTrue(
            assertIs<SurfaceStimulus.Scroll>(scrollAfter.single()).coalescingBoundary > boundaryBeforeReset,
        )
    }

    // -----------------------------------------------------------------------
    // Cas 9 — les boutons natifs mappent PointerButton ; actionButton pilote le Changed
    // -----------------------------------------------------------------------

    @Test
    fun buttonsMapToPointerButtons() {
        assertEquals(PointerButton.Primary, AndroidMotionMapping.pointerButton(MotionEvent.BUTTON_PRIMARY))
        assertEquals(PointerButton.Secondary, AndroidMotionMapping.pointerButton(MotionEvent.BUTTON_SECONDARY))
        assertEquals(PointerButton.Auxiliary, AndroidMotionMapping.pointerButton(MotionEvent.BUTTON_TERTIARY))
        assertEquals(PointerButton.Back, AndroidMotionMapping.pointerButton(MotionEvent.BUTTON_BACK))
        assertEquals(PointerButton.Forward, AndroidMotionMapping.pointerButton(MotionEvent.BUTTON_FORWARD))
        assertEquals(PointerButton.Barrel, AndroidMotionMapping.pointerButton(MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertEquals(PointerButton.Eraser, AndroidMotionMapping.pointerButton(MotionEvent.BUTTON_STYLUS_SECONDARY))
        assertEquals(PointerButton.Other(0x80), AndroidMotionMapping.pointerButton(0x80), "an unmapped bit is carried as the native code")

        val tracker = tracker()
        tracker.onObservation(
            observation(
                MotionEvent.ACTION_HOVER_ENTER,
                pointers = listOf(PointerSpec(0, 12f, 24f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val press = tracker.onObservation(
            observation(
                MotionEvent.ACTION_BUTTON_PRESS, actionButton = MotionEvent.BUTTON_SECONDARY,
                pointers = listOf(PointerSpec(0, 12f, 24f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val pressed = assertIs<SurfaceStimulus.PointerButtonChanged>(press.single())
        assertEquals(PointerButton.Secondary, pressed.button)
        assertEquals(PointerButtonState.Pressed, pressed.buttonState)
        assertEquals(LogicalPoint(6.0, 12.0), pressed.position)

        val release = tracker.onObservation(
            observation(
                MotionEvent.ACTION_BUTTON_RELEASE, actionButton = MotionEvent.BUTTON_SECONDARY,
                pointers = listOf(PointerSpec(0, 12f, 24f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val released = assertIs<SurfaceStimulus.PointerButtonChanged>(release.single())
        assertEquals(PointerButton.Secondary, released.button)
        assertEquals(PointerButtonState.Released, released.buttonState)

        // Un press sans actionButton lisible retombe sur le bit buttonState (le bit bas).
        val fallback = tracker.onObservation(
            observation(
                MotionEvent.ACTION_BUTTON_PRESS, buttons = MotionEvent.BUTTON_TERTIARY,
                pointers = listOf(PointerSpec(0, 12f, 24f, tool = MotionEvent.TOOL_TYPE_MOUSE)),
            ),
            viewWidth = 100, viewHeight = 100, scaleFactor = 2.0,
        )
        val fallbackPressed = assertIs<SurfaceStimulus.PointerButtonChanged>(fallback.single())
        assertEquals(PointerButton.Auxiliary, fallbackPressed.button)
    }

    // -----------------------------------------------------------------------
    // Outils de test — des observations copiées construites à la main
    // -----------------------------------------------------------------------

    /** Un pointeur d'observation : les primitives telles que la couture Task 3 les lira. */
    private data class PointerSpec(
        val id: Int,
        val x: Float = 0f,
        val y: Float = 0f,
        val pressure: Float? = null,
        val tool: Int = MotionEvent.TOOL_TYPE_FINGER,
    )

    private fun observation(
        actionMasked: Int,
        actionIndex: Int = 0,
        pointers: List<PointerSpec> = listOf(PointerSpec(0)),
        buttons: Int = 0,
        actionButton: Int = 0,
        axisHscroll: Float = 0f,
        axisVscroll: Float = 0f,
        axisXrelative: Float = 0f,
        axisYrelative: Float = 0f,
        deviceId: Int = 0,
    ): AndroidMotionObservation = AndroidMotionObservation(
        actionMasked = actionMasked,
        actionIndex = actionIndex,
        pointerCount = pointers.size,
        pointerIds = pointers.map { it.id }.toIntArray(),
        xs = pointers.map { it.x }.toFloatArray(),
        ys = pointers.map { it.y }.toFloatArray(),
        pressures = pointers.map { it.pressure }.toTypedArray(),
        toolTypes = pointers.map { it.tool }.toIntArray(),
        buttons = buttons,
        actionButton = actionButton,
        axisHscroll = axisHscroll,
        axisVscroll = axisVscroll,
        axisXrelative = axisXrelative,
        axisYrelative = axisYrelative,
        deviceId = deviceId,
        metaState = 0,
    )
}
