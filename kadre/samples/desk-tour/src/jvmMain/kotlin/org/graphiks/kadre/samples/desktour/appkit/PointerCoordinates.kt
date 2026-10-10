package org.graphiks.kadre.samples.desktour.appkit

import androidx.compose.ui.geometry.Offset
import org.graphiks.kadre.surface.LogicalPoint

/** AppKit logical points start at bottom-left; Compose physical pixels start at top-left. */
internal fun appKitPointToCompose(position: LogicalPoint, logicalHeight: Double, scaleFactor: Double): Offset =
    Offset((position.x * scaleFactor).toFloat(), ((logicalHeight - position.y) * scaleFactor).toFloat())
