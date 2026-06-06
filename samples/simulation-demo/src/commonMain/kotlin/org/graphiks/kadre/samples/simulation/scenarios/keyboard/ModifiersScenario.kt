package org.graphiks.kadre.samples.simulation.scenarios.keyboard

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class ModifiersScenario : KeyboardScenario(
    id = "keyboard-modifiers",
    title = "État des modifieurs",
    description = "Visualise l'état des touches modifieurs (Shift, Ctrl, Alt, Meta/Cmd) en temps réel.",
    priority = 80
) {
    private val activeModifiers = mutableSetOf<KeyModifier>()
    private val allModifiers = KeyModifier.values().toList()

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        onEvent(ScenarioEvent.Message("Appuyez sur Shift, Ctrl, Alt ou Cmd pour voir leur état", MessageSeverity.INFO))
    }

    override fun onKeyEvent(event: WindowEvent.KeyInput) {
        super.onKeyEvent(event)

        activeModifiers.clear()
        activeModifiers.addAll(event.modifiers)

        val modStates = allModifiers.joinToString(" | ") { mod ->
            val active = activeModifiers.contains(mod)
            "${mod.name}: ${if (active) "🔵" else "⚪"}"
        }

        onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = modStates,
            data = activeModifiers.associate { it.name to true }
        )))
    }
}
