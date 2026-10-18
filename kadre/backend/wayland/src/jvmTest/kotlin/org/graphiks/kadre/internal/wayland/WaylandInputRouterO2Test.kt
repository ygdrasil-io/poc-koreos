package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kffi.wayland.managed.WaylandKeyboardEvent
import org.graphiks.kffi.wayland.managed.WaylandPointerEvent

/**
 * Preuves O2 du routeur d'entrée phase 4 (design
 * `WAYLAND-PHASE-4-INPUT-DESIGN.md`) : routage par surface propriétaire
 * (D1), batch par frame avec coalescence (D2, F2-1/F2-2), scroll unités et
 * fins de séquence, neutralisation des touches/boutons retenus (SN-1) et
 * refus des touches hors focus (SN-3).
 */
class WaylandInputRouterO2Test {

    private val surfaceA = 0x1000L
    private val surfaceB = 0x2000L
    private val idA = SurfaceId(1L)
    private val idB = SurfaceId(2L)

    private fun router(): WaylandInputRouter = WaylandInputRouter().apply {
        registerSurface(surfaceA, idA)
        registerSurface(surfaceB, idB)
    }

    private fun pointer(
        kind: WaylandPointerEvent.Kind,
        surface: Long = 0L,
        x: Double = 0.0,
        y: Double = 0.0,
        button: Int = 0,
        state: Int = 0,
        axis: Int = 0,
        value: Double = 0.0,
        discrete: Int = 0,
    ): WaylandPointerEvent = WaylandPointerEvent(kind, 0, 0, surface, x, y, button, state, axis, value, discrete)

    private fun key(
        kind: WaylandKeyboardEvent.Kind,
        surface: Long = 0L,
        key: Int = 0,
        state: Int = 0,
        time: Int = 0,
    ): WaylandKeyboardEvent = WaylandKeyboardEvent(
        kind, 0, time, surface, 0, 0, 0, IntArray(0), key, state, 0, 0, 0, 0, 0, 0,
    )

    @Test
    fun pointerEventsCoalesceIntoOneBatchPerFrame() {
        val router = router()
        val batches = ArrayList<WaylandPointerBatch>()
        router.onBatch = { batches.add(it) }
        router.offer(pointer(WaylandPointerEvent.Kind.Enter, surfaceA, 10.0, 10.0))
        router.offer(pointer(WaylandPointerEvent.Kind.Motion, x = 20.0, y = 25.0))
        router.offer(pointer(WaylandPointerEvent.Kind.Motion, x = 30.0, y = 35.0))
        router.offer(pointer(WaylandPointerEvent.Kind.AxisValue120, axis = 0, discrete = 120))
        router.offer(pointer(WaylandPointerEvent.Kind.Button, button = 0x110, state = 1))
        assertTrue(batches.isEmpty(), "nothing publishes before the frame boundary")
        router.offer(pointer(WaylandPointerEvent.Kind.Frame))
        assertEquals(1, batches.size)
        val batch = batches.single()
        assertEquals(idA, batch.surfaceId)
        assertTrue(batch.entered)
        assertEquals(30.0, batch.x, "la dernière position gagne (coalescence)")
        assertEquals(35.0, batch.y)
        assertEquals(listOf(0x110), batch.pressedButtons)
        assertEquals(mapOf(0 to 120), batch.scrollValue120)
    }

    @Test
    fun withoutFrameSupportEachEventIsItsOwnBatch() {
        // F2-1 : sous v5 (pas de frame), chaque événement publie son lot
        val router = WaylandInputRouter(frameAware = false).apply {
            registerSurface(surfaceA, idA)
        }
        val batches = ArrayList<WaylandPointerBatch>()
        router.onBatch = { batches.add(it) }
        router.offer(pointer(WaylandPointerEvent.Kind.Enter, surfaceA, 1.0, 2.0))
        router.offer(pointer(WaylandPointerEvent.Kind.Button, button = 0x110, state = 1))
        assertEquals(2, batches.size, "legacy: un lot par événement")
        router.flush()
        assertEquals(2, batches.size)
    }

    @Test
    fun scrollStopEndsTheSequenceAndClearsContinuousAccumulation() {
        val router = router()
        val batches = ArrayList<WaylandPointerBatch>()
        router.onBatch = { batches.add(it) }
        router.offer(pointer(WaylandPointerEvent.Kind.Enter, surfaceA))
        router.offer(pointer(WaylandPointerEvent.Kind.Axis, axis = 1, value = 12.5))
        router.offer(pointer(WaylandPointerEvent.Kind.Axis, axis = 1, value = 7.5))
        router.offer(pointer(WaylandPointerEvent.Kind.AxisStop, axis = 1))
        router.offer(pointer(WaylandPointerEvent.Kind.Frame))
        val batch = batches.single()
        assertEquals(emptyList(), batch.scrollContinuous.entries.map { it.key }, "l'axis_stop vide l'accumulation continue")
        assertEquals(listOf(1), batch.scrollStopped, "la fin de séquence est préservée, jamais avalée")
    }

    @Test
    fun leaveWithHeldButtonsNeutralizesExactlyTheHeldOnes() {
        // SN-1
        val router = router()
        val batches = ArrayList<WaylandPointerBatch>()
        router.onBatch = { batches.add(it) }
        router.offer(pointer(WaylandPointerEvent.Kind.Enter, surfaceA))
        router.offer(pointer(WaylandPointerEvent.Kind.Button, button = 0x110, state = 1))
        router.offer(pointer(WaylandPointerEvent.Kind.Button, button = 0x111, state = 1))
        router.offer(pointer(WaylandPointerEvent.Kind.Button, button = 0x110, state = 0))
        router.offer(pointer(WaylandPointerEvent.Kind.Leave, surfaceA))
        router.offer(pointer(WaylandPointerEvent.Kind.Frame))
        val batch = batches.single()
        assertTrue(batch.left)
        // 0x110 = relâchement explicite ; 0x111 = libéré par la
        // neutralisation (jamais explicitement relâché — SN-1)
        assertEquals(listOf(0x110, 0x111), batch.releasedButtons)
    }

    @Test
    fun keyboardRoutesByFocusedSurfaceAndKeyOutsideFocusIsDropped() {
        // D1 + SN-3
        val router = router()
        val commands = ArrayList<WaylandKeyCommand>()
        router.onKey = { commands.add(it) }
        router.offer(key(WaylandKeyboardEvent.Kind.Key, key = 30, state = 1))
        assertTrue(commands.isEmpty(), "une touche hors focus n'est jamais routée (ni substituée)")

        router.offer(key(WaylandKeyboardEvent.Kind.Enter, surfaceB))
        router.offer(key(WaylandKeyboardEvent.Kind.Key, key = 30, state = 1, time = 100))
        router.offer(key(WaylandKeyboardEvent.Kind.Key, key = 30, state = 0, time = 110))
        val keyCommands = commands.filterIsInstance<WaylandKeyCommand.Key>()
        assertEquals(2, keyCommands.size)
        assertEquals(idB, keyCommands.first().surfaceId)
        assertTrue(keyCommands.first().pressed)
    }

    @Test
    fun keyboardLeaveWithHeldKeysSynthesizesExactReleases() {
        // SN-1 : leave avec touches enfoncées ⇒ relâchements synthétiques
        val router = router()
        val commands = ArrayList<WaylandKeyCommand>()
        router.onKey = { commands.add(it) }
        router.offer(key(WaylandKeyboardEvent.Kind.Enter, surfaceA))
        router.offer(key(WaylandKeyboardEvent.Kind.Key, key = 46, state = 1))
        router.offer(key(WaylandKeyboardEvent.Kind.Key, key = 37, state = 1))
        router.offer(key(WaylandKeyboardEvent.Kind.Key, key = 46, state = 0))
        router.offer(key(WaylandKeyboardEvent.Kind.Leave, surfaceA))
        val loss = commands.filterIsInstance<WaylandKeyCommand.FocusLost>().single()
        assertEquals(idA, loss.surfaceId)
        assertEquals(listOf(37), loss.synthesizedReleases, "seule la touche encore enfoncée est libérée")
    }

    @Test
    fun surfaceUnregistrationWithdrawsFocusAndNeutralizes() {
        val router = router()
        val commands = ArrayList<WaylandKeyCommand>()
        router.onKey = { commands.add(it) }
        router.offer(key(WaylandKeyboardEvent.Kind.Enter, surfaceA))
        router.offer(key(WaylandKeyboardEvent.Kind.Key, key = 50, state = 1))
        router.unregisterSurface(surfaceA)
        val loss = commands.filterIsInstance<WaylandKeyCommand.FocusLost>().single()
        assertEquals(listOf(50), loss.synthesizedReleases)
        // after unregistration the router holds no focus: further keys drop
        commands.clear()
        router.offer(key(WaylandKeyboardEvent.Kind.Key, key = 51, state = 1))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun twoWindowsRouteIndependentlyByAddress() {
        // P-1 logique : deux surfaces, le routage suit l'adresse du proxy
        val router = router()
        val batches = ArrayList<WaylandPointerBatch>()
        router.onBatch = { batches.add(it) }
        router.offer(pointer(WaylandPointerEvent.Kind.Enter, surfaceA, 5.0, 5.0))
        router.offer(pointer(WaylandPointerEvent.Kind.Frame))
        router.offer(pointer(WaylandPointerEvent.Kind.Enter, surfaceB, 9.0, 9.0))
        router.offer(pointer(WaylandPointerEvent.Kind.Frame))
        assertEquals(2, batches.size)
        assertEquals(idA, batches[0].surfaceId)
        assertEquals(idB, batches[1].surfaceId)
    }
}
