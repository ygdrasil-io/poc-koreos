package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputEvent
import org.graphiks.kadre.input.TextInputState

/** L'état de la session de saisie telle que l'utilisateur la vit. */
internal enum class TextInputSessionState { Active, Suspended, Closed }

internal data class TextInputPresentation(
    val open: Boolean,
    val stateLabel: String,
    val lastEvent: String?,
)

internal data class TextInputEventSummary(val kind: String, val detail: String?)

internal fun textInputStateLabel(state: TextInputSessionState): String = when (state) {
    TextInputSessionState.Active -> "active"
    TextInputSessionState.Suspended -> "suspendue"
    TextInputSessionState.Closed -> "fermée"
}

internal fun textInputSessionStateOf(state: TextInputState): TextInputSessionState = when (state) {
    is TextInputState.Active -> TextInputSessionState.Active
    is TextInputState.Suspended -> TextInputSessionState.Suspended
    TextInputState.Closed -> TextInputSessionState.Closed
}

internal fun renderTextInputEvent(summary: TextInputEventSummary): String {
    val detail = summary.detail?.takeIf { it.isNotBlank() } ?: return summary.kind
    return "${summary.kind} — $detail"
}

internal fun textInputActionLabel(action: TextInputAction): String = when (action) {
    TextInputAction.Default -> "action par défaut"
    TextInputAction.Done -> "terminé"
    TextInputAction.Go -> "valider"
    TextInputAction.Next -> "suivant"
    TextInputAction.Search -> "rechercher"
    TextInputAction.Send -> "envoyer"
}

/**
 * Extraction par variante : les événements de saisie portent des `EventStamp` et des
 * `TextDocumentRevision` dont le sample ne peut pas fabriquer les exemplaires, donc seule
 * la mise en forme ci-dessus est testable — le `when` exhaustif protège de l'oubli.
 */
internal fun describeTextInputEvent(event: TextInputEvent): TextInputEventSummary = when (event) {
    is TextInputEvent.Replace -> TextInputEventSummary("Texte reçu", event.text)
    is TextInputEvent.SelectionChanged -> TextInputEventSummary("Sélection déplacée", null)
    is TextInputEvent.CompositionChanged ->
        TextInputEventSummary("Composition en cours", event.text.takeIf { it.isNotBlank() })
    is TextInputEvent.Action -> TextInputEventSummary("Action", textInputActionLabel(event.action))
}
