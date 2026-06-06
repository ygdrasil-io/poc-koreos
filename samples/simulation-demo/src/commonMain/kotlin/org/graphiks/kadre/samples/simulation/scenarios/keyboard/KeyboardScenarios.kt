package org.graphiks.kadre.samples.simulation.scenarios.keyboard

import org.graphiks.kadre.samples.simulation.*

fun register() {
    val scenarios = listOf(
        BasicKeyboard(),
        ShortcutsScenario(),
        ImeScenario(),
        ModifiersScenario(),
        object : KeyboardScenario(
            id = "keyboard-repeat",
            title = "Répétition de touches",
            description = "Teste la répétition automatique des touches maintenues enfoncées. Observez les événements repeat.",
            priority = 70
        ) {
            override fun onKeyEvent(event: WindowEvent.KeyInput) {
                super.onKeyEvent(event)
                if (event.pressed) {
                    val repeatInfo = if (event.repeat) "🔄 (répétition)" else "🆕 (première pression)"
                    onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                        isRunning = true,
                        message = "$repeatInfo Touche: ${event.key.name.lowercase()}",
                        data = mapOf("repeat" to event.repeat, "key" to event.key.name)
                    )))
                }
            }
        }
    )

    scenarios.forEach { ScenarioRegistry.register(it) }
}
