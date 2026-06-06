package org.graphiks.kadre.samples.simulation.scenarios.window

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class FullscreenScenario : WindowScenario(
    id = "window-fullscreen",
    title = "Plein écran",
    description = "Teste le passage en plein écran. Appuyez sur F pour basculer, Échap pour quitter.",
    priority = 90
) {
    private var isFullscreen = false

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        onEvent(ScenarioEvent.Message("Appuyez sur F pour le plein écran, Échap pour quitter", MessageSeverity.INFO))
    }

    override fun onKeyEvent(event: WindowEvent.KeyInput) {
        if (event.pressed && event.key == Key.F) {
            isFullscreen = !isFullscreen
            window?.update(WindowAttributes(fullscreen = isFullscreen))
            onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                isRunning = true,
                message = if (isFullscreen) "🖥️ Plein écran activé" else "🖥️ Plein écran désactivé",
                data = mapOf("fullscreen" to isFullscreen)
            )))
        }
    }
}
