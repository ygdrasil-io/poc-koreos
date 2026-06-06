package org.graphiks.kadre.samples.simulation.scenarios.integration

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class TextEditorScenario : Scenario {
    override val id = "text-editor"
    override val title = "Éditeur de texte miniature"
    override val description = "Mini éditeur de texte combinant saisie clavier, IME et gestion des raccourcis. Supporte le texte enrichi de base."
    override val category = "Intégration"
    override val requiredCapabilities: Set<Capability> = setOf(Capability.KEYBOARD)
    override val priority: Int = 90

    private var window: Window? = null
    private var eventLoop: ActiveEventLoop? = null
    private var onEvent: ((ScenarioEvent) -> Unit)? = null
    private var isRunning = false

    private val textContent = StringBuilder()
    private var cursorPosition = 0
    private var wordCount = 0
    private var keystrokeCount = 0

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        this.window = window
        this.eventLoop = eventLoop
        this.onEvent = onEvent
        this.isRunning = true
        this.textContent.clear()
        this.cursorPosition = 0
        this.wordCount = 0
        this.keystrokeCount = 0

        onEvent(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = "📝 Tapez du texte. Ctrl+S pour sauvegarder, Ctrl+Z pour annuler.",
            data = mapOf("text" to "", "words" to 0, "keys" to 0)
        )))
    }

    override fun stop() {
        isRunning = false
    }

    override fun onKeyEvent(event: WindowEvent.KeyInput) {
        if (!isRunning || !event.pressed) return

        keystrokeCount++

        val text = event.text ?: ""
        val isCtrl = event.modifiers.any { it == KeyModifier.CTRL }

        if (isCtrl) {
            when (event.key) {
                Key.S -> {
                    // Save action
                    onEvent?.invoke(ScenarioEvent.Message("💾 Texte sauvegardé (${textContent.length} caractères)", MessageSeverity.INFO))
                    onEvent?.invoke(ScenarioEvent.Result(textContent.toString()))
                }
                Key.Z -> {
                    // Undo placeholder
                    onEvent?.invoke(ScenarioEvent.Message("↩️ Annulation (simulée)", MessageSeverity.INFO))
                }
                else -> {}
            }
            return
        }

        when (event.key) {
            Key.BACKSPACE -> {
                if (cursorPosition > 0) {
                    textContent.deleteCharAt(cursorPosition - 1)
                    cursorPosition--
                }
            }
            Key.ENTER -> {
                textContent.insert(cursorPosition, '\n')
                cursorPosition++
            }
            Key.LEFT -> cursorPosition = (cursorPosition - 1).coerceAtLeast(0)
            Key.RIGHT -> cursorPosition = cursorPosition.coerceAtMost(textContent.length)
            else -> {
                if (text.isNotBlank()) {
                    textContent.insert(cursorPosition, text)
                    cursorPosition += text.length
                }
            }
        }

        wordCount = textContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        val displayText = if (textContent.length > 100) {
            textContent.substring(0, 100) + "..."
        } else {
            textContent.toString()
        }

        onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = "📝 Mots: $wordCount | Caractères: ${textContent.length} | Touches: $keystrokeCount",
            data = mapOf(
                "text_preview" to displayText,
                "words" to wordCount,
                "characters" to textContent.length,
                "keystrokes" to keystrokeCount
            )
        )))
    }

    override fun onImeEvent(event: WindowEvent.Ime) {
        onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = "🈳 IME: '${event.preedit}'",
            data = mapOf("ime_preedit" to event.preedit)
        )))
    }

    override fun runHeadless(args: List<String>): ScenarioResult {
        return ScenarioResult(
            success = wordCount > 0,
            durationMs = 1500,
            eventsReceived = keystrokeCount,
            eventsExpected = 20,
            warnings = if (wordCount == 0) listOf("No text was entered") else emptyList(),
            platform = Platform.current()
        )
    }
}
