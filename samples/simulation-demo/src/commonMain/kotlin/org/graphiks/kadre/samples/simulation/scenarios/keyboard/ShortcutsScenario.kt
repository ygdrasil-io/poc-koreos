package org.graphiks.kadre.samples.simulation.scenarios.keyboard

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class ShortcutsScenario : KeyboardScenario(
    id = "keyboard-shortcuts",
    title = "Raccourcis clavier",
    description = "Teste les raccourcis clavier (Ctrl+C, Ctrl+V, etc.). Les événements sont filtrés par modifieurs.",
    priority = 90
) {
    private val knownShortcuts = mapOf(
        "ctrl+c" to "Copier",
        "ctrl+v" to "Coller",
        "ctrl+x" to "Couper",
        "ctrl+z" to "Annuler",
        "ctrl+s" to "Sauvegarder",
        "ctrl+a" to "Tout sélectionner",
        "shift+ctrl+c" to "Ouvrir les outils développeur"
    )
    private val currentModifiers = mutableSetOf<KeyModifier>()

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        onEvent(ScenarioEvent.Message("Raccourcis activés - essayez Ctrl+C, Ctrl+V, Ctrl+S...", MessageSeverity.INFO))
    }

    override fun onKeyEvent(event: WindowEvent.KeyInput) {
        super.onKeyEvent(event)

        if (event.pressed) {
            val isCtrl = event.modifiers.any { it == KeyModifier.CTRL }
            val isShift = event.modifiers.any { it == KeyModifier.SHIFT }

            if (isCtrl) {
                val shortcutKey = buildString {
                    if (isShift) append("shift+")
                    append("ctrl+")
                    append(event.key.name.lowercase())
                }

                val action = knownShortcuts[shortcutKey]
                if (action != null) {
                    onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                        isRunning = true,
                        message = "✅ Raccourci détecté : $shortcutKey → $action",
                        data = mapOf("shortcut" to shortcutKey, "action" to action)
                    )))
                } else {
                    onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                        isRunning = true,
                        message = "Raccourci non reconnu : $shortcutKey",
                        data = mapOf("shortcut" to shortcutKey)
                    )))
                }
            } else {
                onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                    isRunning = true,
                    message = "Utilisez un modifieur (Ctrl/Shift) pour tester les raccourcis"
                )))
            }
        }
    }
}
