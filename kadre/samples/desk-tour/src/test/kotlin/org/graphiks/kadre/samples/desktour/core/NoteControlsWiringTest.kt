package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteControlsWiringTest {
    private class Recorder(private val controls: NoteControls) : TestTourGateway() {
        var attentionCalls = 0
        var decorationCalls = 0
        override fun noteControls(key: NoteKey): NoteControls = controls
        override suspend fun requestNoteAttention(key: NoteKey): NoteUpdateOutcome {
            attentionCalls++
            return NoteUpdateOutcome.Applied
        }
        override suspend fun toggleNoteDecorations(key: NoteKey): NoteUpdateOutcome {
            decorationCalls++
            return NoteUpdateOutcome.Applied
        }
    }

    @Test
    fun `an unsupported attention control never reaches the gateway`() = runTest {
        val store = TourStore()
        val gateway = Recorder(NoteControls(true, canRequestAttention = false, true, true))

        ActionDispatcher(store, gateway).requestNoteAttention(NoteKey(1L))

        assertEquals(0, gateway.attentionCalls)
        assertTrue(store.state.value.activity.isEmpty())
    }

    @Test
    fun `an unsupported decoration control never reaches the gateway`() = runTest {
        val store = TourStore()
        val gateway = Recorder(NoteControls(true, true, canChangeDecorations = false, true))

        ActionDispatcher(store, gateway).toggleNoteDecorations(NoteKey(1L))

        assertEquals(0, gateway.decorationCalls)
        assertTrue(store.state.value.activity.isEmpty())
    }

    @Test
    fun `a supported attention control reaches the gateway exactly once and is journaled`() = runTest {
        val store = TourStore()
        val gateway = Recorder(NoteControls(true, true, true, true))

        ActionDispatcher(store, gateway).requestNoteAttention(NoteKey(1L))

        assertEquals(1, gateway.attentionCalls)
        assertEquals(ActivityStatus.Succeeded, store.state.value.activity.single().status)
    }

    @Test
    fun `a refused decoration change is rejected and never claims success`() = runTest {
        val store = TourStore()
        val gateway = object : TestTourGateway() {
            override suspend fun toggleNoteDecorations(key: NoteKey) =
                NoteUpdateOutcome.Refused("Non pris en charge par le host courant.")
        }

        ActionDispatcher(store, gateway).toggleNoteDecorations(NoteKey(1L))

        assertEquals(ActivityStatus.Rejected, store.state.value.activity.single().status)
    }
}
