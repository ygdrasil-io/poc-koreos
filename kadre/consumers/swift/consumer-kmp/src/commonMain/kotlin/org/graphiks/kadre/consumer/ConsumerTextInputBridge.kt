package org.graphiks.kadre.consumer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextCapitalization
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputEvent
import org.graphiks.kadre.input.TextInputPurpose
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.input.TextInputState

/**
 * Le pont de SAISIE du consumer (sample tvOS, Task 7) — la route BCK-024 atteinte depuis
 * Swift SANS frontière SDK : la session texte est un verbe KOTLIN
 * (`SurfaceInput.openTextInput`, suspend) que la façade Swift n'enrobe pas — le consumer
 * la rejoint ICI, dans son module applicatif Kotlin (le même qui porte la factory), pour
 * que le sample l'appelle en completion handler depuis la scène de coexistence.
 *
 * La surface vient de [ConsumerFixture.primarySurface] — capturée au run de l'application
 * TENUE (`HeldApplicationFactory` : le sample tvOS est une session longue, la self-stop
 * de `ConsumerApplication` (~500 ms) fermerait la session pendant la navigation). Threading
 * du bridge : TOUT sur `Dispatchers.Main` — les collectors d'événements et les verbes
 * suspend partagent le fil avec les lectures Swift du sample (la même posture que
 * `ConsumerFixture` : un seul fil, aucun verbe atomique).
 *
 * Honnêteté d'échec : les `KadreResult` typés restent la preuve du driver (BCK-024) ;
 * le bridge APLATIT le refus en description lisible (`String?` — null = succès), le même
 * aplatissement que `ConsumerSceneStore.attachFailureDescription` côté Swift. Aucune
 * vérité n'est perdue : la session (ou la raison du refus) reste lisible mot pour mot.
 */
public object ConsumerTextInputBridge {

    /** Le scope propre du bridge — Main uniquement (voir l'en-tête). */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Le scope de la session OUVERTE — annulé au close (collectors compris). */
    private var sessionScope: CoroutineScope? = null

    /** La session texte ouverte — null tant qu'aucun open n'a réussi (open exclusif :
     *  BCK-024, le second open est refusé `AlreadyInUse(TextInputSession)`). */
    private var session: TextInputSession? = null

    /** Le document entretenu par les événements `Replace` de la session (le miroir du
     *  write-back conduit du receiver natif : champ d'abord, ombre ensuite — le bridge
     *  lit les événements, il ne les devine pas). Lu par le sample tvOS pour afficher. */
    private val mutableText = MutableStateFlow("")

    public val text: StateFlow<String>
        get() = mutableText

    /** La description d'état de la session texte — « none » / « Active » / « Suspended » /
     *  « Closed », le même patron de description que `ConsumerSceneStore.stateName`. */
    private val mutableStateDescription = MutableStateFlow("none")

    public val stateDescription: StateFlow<String>
        get() = mutableStateDescription

    /** La description du DERNIER refus (open) — null tant que tout réussit. */
    private val mutableLastFailureDescription = MutableStateFlow<String?>(null)

    public val lastFailureDescription: StateFlow<String?>
        get() = mutableLastFailureDescription

    /**
     * Ouvre la session texte sur la surface primaire du consumer — BCK-024 tel que livré :
     * le receiver natif (UITextField caché) est posé dans la hiérarchie du host, la config
     * est mappée par le host (`purpose`→`keyboardType`, `action`→`returnKeyType`, …).
     * Config du sample : `Text` / `Done` / `Sentences` — le mapping des autres purposes
     * est prouvé par le driver, le sample n'en exhibe qu'un.
     *
     * Retour : null au succès, la description du refus sinon (déjà enregistrée dans
     * [lastFailureDescription]). Le collector d'événements démarre au succès.
     */
    public suspend fun openTextInput(): String? {
        if (session != null) return "text input session already open"
        val surface = ConsumerFixture.primarySurface
            ?: return "no primary surface captured — the held application has not run yet"
        val result = surface.input.openTextInput(
            TextInputConfig(
                purpose = TextInputPurpose.Text,
                action = TextInputAction.Done,
                capitalization = TextCapitalization.Sentences,
            ),
        )
        val opened: TextInputSession = when (result) {
            is KadreResult.Success -> result.value
            is KadreResult.Failure -> {
                val description = "openTextInput failed: ${result.reason}"
                mutableLastFailureDescription.value = description
                return description
            }
        }
        mutableLastFailureDescription.value = null
        mutableText.value = ""
        session = opened
        observe(opened)
        return null
    }

    /** Ferme la session texte (idempotent — le close d'une session déjà close est no-op). */
    public fun closeTextInput() {
        sessionScope?.cancel()
        sessionScope = null
        session?.close()
        session = null
        mutableStateDescription.value = "Closed"
    }

    /** Le collector des événements de session — applique les `Replace` (plages UTF-16
     *  nommées par UIKit, jamais recomposées) au document affiché ; décrit l'état. */
    private fun observe(opened: TextInputSession) {
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        this.sessionScope = sessionScope
        sessionScope.launch {
            opened.state.collect { state ->
                mutableStateDescription.value = when (state) {
                    is TextInputState.Active -> "Active"
                    is TextInputState.Suspended -> "Suspended"
                    TextInputState.Closed -> "Closed"
                }
            }
        }
        sessionScope.launch {
            opened.events.collect { event ->
                when (event) {
                    is TextInputEvent.Replace -> {
                        val current = mutableText.value
                        val start = event.range.startUtf16.coerceIn(0, current.length)
                        val end = event.range.endExclusiveUtf16.coerceIn(start, current.length)
                        mutableText.value =
                            current.substring(0, start) + event.text + current.substring(end)
                    }
                    else -> Unit
                }
            }
        }
    }
}
