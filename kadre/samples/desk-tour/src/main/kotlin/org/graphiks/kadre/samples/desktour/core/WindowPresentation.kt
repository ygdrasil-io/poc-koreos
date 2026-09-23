package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.surface.SurfaceFocus

/** L'état de focus d'une fenêtre, écrit pour l'utilisateur (spec §8.4 : pas de nom d'énumération). */
internal fun windowFocusLabel(focus: SurfaceFocus): String = when (focus) {
    SurfaceFocus.Focused -> "au premier plan"
    SurfaceFocus.Unfocused -> "en arrière-plan"
}
