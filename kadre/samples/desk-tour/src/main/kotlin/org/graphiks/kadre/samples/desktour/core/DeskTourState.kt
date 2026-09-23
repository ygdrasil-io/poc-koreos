package org.graphiks.kadre.samples.desktour.core

internal enum class TourRoute { Desk, Activity }

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
    val activity: List<ActivityEntry> = emptyList(),
    val apiDetailsOpen: Boolean = false,
)
