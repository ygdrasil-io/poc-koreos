package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

internal class TourStore {
    private val nextId = AtomicLong(0L)
    private val mutableState = MutableStateFlow(DeskTourState())
    val state: StateFlow<DeskTourState> = mutableState

    fun admit(label: String, apiDetail: String): ActionCorrelationId {
        val id = ActionCorrelationId(nextId.incrementAndGet())
        mutableState.update { current ->
            current.copy(
                activity = current.activity + ActivityEntry(
                    correlationId = id,
                    label = label,
                    status = ActivityStatus.Pending,
                    apiDetail = apiDetail,
                ),
            )
        }
        return id
    }

    fun resolve(id: ActionCorrelationId, status: ActivityStatus, motif: String? = null) {
        mutableState.update { current ->
            val index = current.activity.indexOfFirst { it.correlationId == id }
            if (index < 0 || current.activity[index].status != ActivityStatus.Pending) return@update current
            current.copy(
                activity = current.activity.toMutableList().also {
                    it[index] = it[index].copy(status = status, motif = motif)
                },
            )
        }
    }

    fun setRoute(route: TourRoute) = mutableState.update { it.copy(route = route) }

    fun publishWindows(windows: List<DeskTourWindow>) = mutableState.update { it.copy(windows = windows) }

    fun publishCreateNoteAvailability(availability: CapabilityPresentation) =
        mutableState.update { it.copy(createNote = availability) }

    fun publishNote(note: DeskTourNote) = mutableState.update { current ->
        val index = current.notes.indexOfFirst { it.key == note.key }
        current.copy(
            notes = if (index < 0) {
                current.notes + note
            } else {
                current.notes.toMutableList().also { it[index] = note }
            },
        )
    }

    fun removeNote(key: NoteKey) = mutableState.update { current ->
        current.copy(notes = current.notes.filterNot { it.key == key })
    }

    fun publishDisplays(displays: DisplayPresentation) = mutableState.update { it.copy(displays = displays) }

    fun publishDisplayAccessAvailability(availability: CapabilityPresentation) =
        mutableState.update { it.copy(displayAccess = availability) }

    fun toggleApiDetails() = mutableState.update { it.copy(apiDetailsOpen = !it.apiDetailsOpen) }
}
