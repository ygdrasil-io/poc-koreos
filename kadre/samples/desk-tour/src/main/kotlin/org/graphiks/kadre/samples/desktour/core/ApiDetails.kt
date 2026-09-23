package org.graphiks.kadre.samples.desktour.core

internal enum class ApiDetailKey(val userAction: String, val publicSurface: List<String>) {
    CreateFloatingNote(
        "Créer une note flottante",
        listOf("WindowManager.requestWindow", "WindowRequest", "WindowRequestOutcome"),
    ),
    ModifyWindow("Modifier une fenêtre", listOf("Window.apply", "WindowUpdate", "WindowUpdateOutcome")),
    TrackDisplays("Suivre les écrans", listOf("DisplayManager.state", "DisplayManager.events")),
    TrackSurface("Suivre la surface", listOf("HostSurface.state", "HostSurface.events", "HostSurface.apply")),
    TypeOrDrop("Saisir ou déposer", listOf("HostSurface.input", "SurfaceInput.events", "DropOffer")),
    DiagnoseOption("Diagnostiquer une option", listOf("Capability", "KadreResult", "KadreDiagnostics")),
}
