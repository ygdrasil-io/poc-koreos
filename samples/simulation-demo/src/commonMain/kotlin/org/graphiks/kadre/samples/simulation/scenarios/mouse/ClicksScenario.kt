package org.graphiks.kadre.samples.simulation.scenarios.mouse

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class ClicksScenario : MouseScenario(
    id = "mouse-clicks",
    title = "Clics de souris",
    description = "Teste les clics simple, double et triple avec tous les boutons (gauche, droit, milieu).",
    priority = 100
) {
    private var clickCount = 0
    private var lastClickTime = 0L

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        onEvent(ScenarioEvent.Message("Cliquez n'importe où pour tester les événements de clic", MessageSeverity.INFO))
    }

    override fun onMouseEvent(event: WindowEvent.Mouse) {
        super.onMouseEvent(event)

        when (event) {
            is WindowEvent.Mouse.Moved -> {}
            is WindowEvent.Mouse.Pressed -> {
                mouseEventsReceived++
                clickCount++
                val button = event.button?.name?.lowercase() ?: "inconnu"
                val pos = "${event.x.toInt()},${event.y.toInt()}"

                onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                    isRunning = true,
                    message = "🖱️ Clic #$clickCount — bouton: $button à ($pos)",
                    data = mapOf(
                        "click_count" to clickCount,
                        "button" to button,
                        "x" to event.x.toInt(),
                        "y" to event.y.toInt()
                    )
                )))
            }
            is WindowEvent.Mouse.Released -> {
                mouseEventsReceived++
                val button = event.button?.name?.lowercase() ?: "inconnu"
                onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                    isRunning = true,
                    message = "🔄 Relâché: $button",
                    data = mapOf("released" to button)
                )))
            }
            else -> {}
        }
    }
}
