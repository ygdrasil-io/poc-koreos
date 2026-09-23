package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenameNoteTest {
    private open class StubGateway(private val outcome: NoteUpdateOutcome) : TestTourGateway() {
        var calls = 0
        override suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome {
            calls++
            return outcome
        }
    }

    @Test
    fun `a partially applied rename shows applied and rejected separately`() = runTest {
        val store = TourStore()
        val gateway = StubGateway(
            NoteUpdateOutcome.PartiallyApplied(
                applied = listOf("Titre"),
                rejected = listOf(
                    RejectedFieldPresentation("Flou d'arrière-plan", "Non pris en charge par le host courant."),
                ),
            ),
        )

        ActionDispatcher(store, gateway).renameNote(NoteKey(1L), "Nouveau titre")

        val entry = store.state.value.activity.single()
        assertTrue(entry.motif!!.contains("Flou d'arrière-plan"))
        assertTrue(entry.motif!!.contains("Titre"))
    }

    @Test
    fun `a refused rename is rejected and never claims success`() = runTest {
        val store = TourStore()
        val gateway = StubGateway(NoteUpdateOutcome.Refused("Non pris en charge par le host courant."))

        ActionDispatcher(store, gateway).renameNote(NoteKey(1L), "Nouveau titre")

        assertEquals(ActivityStatus.Rejected, store.state.value.activity.single().status)
        assertEquals(1, gateway.calls)
    }

    @Test
    fun `an applied rename is succeeded and reaches the gateway once`() = runTest {
        val store = TourStore()
        val gateway = StubGateway(NoteUpdateOutcome.Applied)

        ActionDispatcher(store, gateway).renameNote(NoteKey(1L), "Nouveau titre")

        assertEquals(ActivityStatus.Succeeded, store.state.value.activity.single().status)
        assertEquals(1, gateway.calls)
    }

    @Test
    fun `an accepted rename is not reported as a confirmed effect`() = runTest {
        val store = TourStore()
        val gateway = StubGateway(NoteUpdateOutcome.Accepted)

        ActionDispatcher(store, gateway).renameNote(NoteKey(1L), "Nouveau titre")

        val entry = store.state.value.activity.single()
        assertEquals(ActivityStatus.Succeeded, entry.status)
        assertTrue(
            entry.motif!!.contains("n'est pas encore confirmé"),
            "une acceptation n'est pas un effet observé : le motif doit le dire",
        )
    }

    @Test
    fun `a rename by a note whose title capability is unsupported never reaches the gateway`() = runTest {
        val store = TourStore()
        val gateway = object : StubGateway(NoteUpdateOutcome.Applied) {
            override fun noteControls(key: NoteKey) = NoteControls(false, true, true, true)
        }

        ActionDispatcher(store, gateway).renameNote(NoteKey(1L), "Nouveau titre")

        assertTrue(store.state.value.activity.isEmpty())
        assertEquals(0, gateway.calls)
    }
}
