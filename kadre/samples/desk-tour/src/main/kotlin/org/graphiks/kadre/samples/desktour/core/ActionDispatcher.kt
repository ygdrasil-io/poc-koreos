package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.graphiks.kadre.diagnostics.KadreResult

internal class ActionDispatcher(
    private val store: TourStore,
    private val gateway: TourGateway,
) {
    suspend fun createNote() {
        val id = store.admit(ApiDetailKey.CreateFloatingNote.userAction, "WindowManager.requestWindow")
        try {
            val result = gateway.requestNoteWindow()
            when (result) {
                is KadreResult.Success -> store.recordWindowRequestOutcome(id, result.value.await())
                is KadreResult.Failure ->
                    store.resolve(id, ActivityStatus.Unavailable, motif = result.reason.userMotif())
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { store.resolve(id, ActivityStatus.Cancelled) }
            throw cancellation
        }
    }
}
