package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure

internal data class DisplayModeLabel(
    val widthPixels: Int,
    val heightPixels: Int,
    val refreshRateHz: Double?,
)

internal data class ScreenEntry(
    val name: String,
    val widthPixels: Int,
    val heightPixels: Int,
    val scaleFactor: Double,
    val modeLabel: String?,
    val isPrimary: Boolean,
)

/**
 * L'inventaire des écrans tel que la démo le présente. Les états non énumérés sont des
 * états à part entière : le §4.3 exige qu'une liste vide ne les remplace jamais.
 */
internal sealed interface DisplayPresentation {
    data class Enumerated(val entries: List<ScreenEntry>) : DisplayPresentation
    data object NeedsPermission : DisplayPresentation
    data class Denied(val canRequestAgain: Boolean) : DisplayPresentation
    data class Unavailable(val motif: String) : DisplayPresentation
}

internal fun screenEntryOf(
    name: String?,
    width: Int,
    height: Int,
    scale: Double,
    mode: DisplayModeLabel?,
    isPrimary: Boolean,
): ScreenEntry = ScreenEntry(
    name = name?.takeIf { it.isNotBlank() } ?: "Écran sans nom",
    widthPixels = width,
    heightPixels = height,
    scaleFactor = scale,
    modeLabel = mode?.let { label ->
        label.refreshRateHz?.let { rate -> "${label.widthPixels} × ${label.heightPixels} @ ${rate.toInt()} Hz" }
            ?: "${label.widthPixels} × ${label.heightPixels}"
    },
    isPrimary = isPrimary,
)

internal fun displayPresentationFor(failure: KadreFailure): DisplayPresentation =
    DisplayPresentation.Unavailable(failure.userMotif())
