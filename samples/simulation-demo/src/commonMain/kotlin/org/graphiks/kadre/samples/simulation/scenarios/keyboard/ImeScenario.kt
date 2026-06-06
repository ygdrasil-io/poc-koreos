package org.graphiks.kadre.samples.simulation.scenarios.keyboard

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class ImeScenario : KeyboardScenario(
    id = "keyboard-ime",
    title = "Saisie internationale (IME)",
    description = "Teste la saisie avec Input Method Editor pour les caractères accentués, le CJK et autres systèmes d'écriture.",
    priority = 50
) {
    override val requiredCapabilities: Set<Capability> = setOf(Capability.KEYBOARD, Capability.IME)
    private val inputBuffer = StringBuilder()

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        inputBuffer.clear()
        onEvent(ScenarioEvent.Message("IME activé - tapez du texte avec des caractères accentués (é, ü, ñ, ç...)", MessageSeverity.INFO))
    }

    override fun onKeyEvent(event: WindowEvent.KeyInput) {
        super.onKeyEvent(event)

        if (event.pressed) {
            val text = event.text ?: ""
            if (text.isNotBlank()) {
                inputBuffer.append(text)
            }

            if (event.key == Key.ENTER) {
                onEvent?.invoke(ScenarioEvent.Result(inputBuffer.toString()))
                inputBuffer.clear()
            }

            onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                isRunning = true,
                message = "Buffer: \"$inputBuffer\"",
                data = mapOf("buffer" to inputBuffer.toString())
            )))
        }
    }

    override fun onImeEvent(event: WindowEvent.Ime) {
        onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = "IME: preedit='${event.preedit}' cursor=${event.cursorAnchor}",
            data = mapOf("preedit" to event.preedit, "cursorAnchor" to event.cursorAnchor)
        )))
    }
}
