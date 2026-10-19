package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.internal.runtime.TextInputObservation
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.internal.runtime.TextInputOpenCommand
import org.graphiks.kffi.wayland.managed.WaylandImeEvent
import org.graphiks.kffi.wayland.managed.WaylandTextInput

/**
 * Preuves O2 du port text-input (design phase 6) : l'open marshalle
 * l'état converti (surrounding en octets UTF-8), le done obsolète est
 * ignoré (F1-2), le leave purge la composition (F1-1), et les offsets
 * octets des événements deviennent des indices UTF-16 (D2).
 */
class WaylandTextInputO2Test {


    /** Fake de la face text-input enregistrant les requêtes. */
    private class FakeTextInput : WaylandTextInputFace {
        val enables = java.util.concurrent.atomic.AtomicInteger()
        val surroundCalls = java.util.concurrent.CopyOnWriteArrayList<Triple<String, Int, Int>>()
        val commits = java.util.concurrent.atomic.AtomicInteger()

        override fun enable() { enables.incrementAndGet() }
        override fun disable() {}
        override fun setSurroundingText(text: String, cursorBytes: Int, anchorBytes: Int) {
            surroundCalls.add(Triple(text, cursorBytes, anchorBytes))
        }
        override fun setTextChangeCause(cause: Int) {}
        override fun setContentType(hint: Int, purpose: Int) {}
        override fun setCursorRectangle(x: Int, y: Int, width: Int, height: Int) {}
        override fun commit() { commits.incrementAndGet() }
        override fun addListener(onEvent: (org.graphiks.kffi.wayland.managed.WaylandImeEvent) -> Unit): AutoCloseable =
            AutoCloseable { }
        override fun close() {}
    }

    @Test
    fun openMarshalsConvertedSurroundingState() {
        val fake = FakeTextInput()
        val observations = ArrayList<TextInputObservation>()
        val port = WaylandTextInputPort(SurfaceId(1), fake) { 0x1000 }
        val owner = port.open(
            TextInputOpenCommand(
                surfaceId = SurfaceId(1),
                config = TextInputConfig(surroundingText = "a🎉b", selection = TextRange(2, 2)),
                onObservation = { observations.add(it); true },
            ),
        )
        assertTrue(owner is KadreResult.Success<*>)
        assertEquals(1, fake.enables.get())
        assertEquals(1, fake.commits.get())
        val surround = fake.surroundCalls.single()
        assertEquals("a🎉b", surround.first)
        // l'indice UTF-16 2 tombe DANS la paire surrogate → clamp au début
        // du code point suivant : byte de 'b' = 5
        assertEquals(5, surround.second)
        assertEquals(5, surround.third)
        port.close()
    }

    @Test
    fun obsoleteDoneBatchesAreIgnoredEntirely() {
        val fake = FakeTextInput()
        val observations = ArrayList<TextInputObservation>()
        val port = WaylandTextInputPort(SurfaceId(1), fake) { 0x1000 }
        port.open(
            TextInputOpenCommand(
                surfaceId = SurfaceId(1),
                config = TextInputConfig(),
                onObservation = { observations.add(it); true },
            ),
        )
        // premier done serial 10 : publié
        port.offerForTest(WaylandImeEvent(WaylandImeEvent.Kind.Done, 0, "hi", 2, 2, 0, 0, 10))
        assertEquals(1, observations.size)
        // done obsolète serial 9 : ignoré
        port.offerForTest(WaylandImeEvent(WaylandImeEvent.Kind.Done, 0, "x", 1, 1, 0, 0, 9))
        assertEquals(1, observations.size, "le batch obsolète est ignoré intégralement")
        port.close()
    }

    @Test
    fun leavePurgesTheComposition() {
        val fake = FakeTextInput()
        val observations = ArrayList<TextInputObservation>()
        val port = WaylandTextInputPort(SurfaceId(1), fake) { 0x1000 }
        port.open(
            TextInputOpenCommand(
                surfaceId = SurfaceId(1),
                config = TextInputConfig(),
                onObservation = { observations.add(it); true },
            ),
        )
        port.offerForTest(WaylandImeEvent(WaylandImeEvent.Kind.Leave, 0x1000, null, 0, 0, 0, 0, -1))
        val purge = observations.filterIsInstance<TextInputObservation.CompositionChanged>().single()
        assertEquals(null, purge.range)
        assertEquals("", purge.text)
        port.close()
    }

    @Test
    fun deleteSurroundingBytesBecomeUtf16Ranges() {
        val fake = FakeTextInput()
        val observations = ArrayList<TextInputObservation>()
        val port = WaylandTextInputPort(SurfaceId(1), fake) { 0x1000 }
        port.open(
            TextInputOpenCommand(
                surfaceId = SurfaceId(1),
                config = TextInputConfig(surroundingText = "a🎉b", selection = TextRange(3, 3)),
                onObservation = { observations.add(it); true },
            ),
        )
        // delete 2 bytes before the cursor: cursor is UTF-16 index 3 ('b'),
        // 2 bytes before 'b' clamps to the emoji start = UTF-16 index 1
        port.offerForTest(WaylandImeEvent(WaylandImeEvent.Kind.Done, 0, "", 0, 0, 2, 0, 7))
        val replace = observations.filterIsInstance<TextInputObservation.Replace>().single()
        assertEquals(1, replace.range.startUtf16)
        assertEquals(3, replace.range.endExclusiveUtf16)
        port.close()
    }
}
