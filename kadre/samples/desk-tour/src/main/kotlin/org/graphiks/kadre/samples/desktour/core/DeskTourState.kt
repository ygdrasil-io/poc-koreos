package org.graphiks.kadre.samples.desktour.core

internal enum class TourRoute { Desk, Activity, Screens, Interactions }

internal data class DeskTourWindow(
    val title: String,
    val focusLabel: String,
    val logicalWidth: Double,
    val logicalHeight: Double,
    val physicalWidth: Int,
    val physicalHeight: Int,
)

internal data class DeskTourState(
    val route: TourRoute = TourRoute.Activity,
    val windows: List<DeskTourWindow> = emptyList(),
    val notes: List<DeskTourNote> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val apiDetailsOpen: Boolean = false,
    val createNote: CapabilityPresentation = CapabilityPresentation(enabled = true),
    /** Nul tant qu'aucun inventaire n'a été observé : ce n'est pas un inventaire vide. */
    val displays: DisplayPresentation? = null,
    val displayAccess: CapabilityPresentation = CapabilityPresentation(enabled = true, requestable = true),
    /** Nul tant qu'aucune entrée n'a été observée : ce n'est pas un état vide affiché. */
    val input: InputPresentation? = null,
)
