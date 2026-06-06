package org.graphiks.kadre.samples.simulation.scenarios.keyboard

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class BasicKeyboard : KeyboardScenario(
    id = "keyboard-basic",
    title = "Saisie basique",
    description = "Teste la saisie de caractères simples : lettres, chiffres et symboles. Observez les événements key press/release.",
    priority = 100
) {
    private val pressedKeys = mutableSetOf<Key>()

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        onEvent(ScenarioEvent.Message("Saisie basique activée - appuyez sur des touches pour voir les événements", MessageSeverity.INFO))
    }

    override fun onKeyEvent(event: WindowEvent.KeyInput) {
        super.onKeyEvent(event)

        val keyName = event.key.name.lowercase()
        val action = if (event.pressed) "🔽" else "🔼"

        onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = "$action Touche: $keyName (scan: ${event.scancode})",
            data = mapOf(
                "pressed" to event.pressed,
                "key" to keyName,
                "scancode" to event.scancode,
                "total_received" to keyEventsReceived
            )
        )))
    }
}
