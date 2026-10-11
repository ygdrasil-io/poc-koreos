package org.graphiks.kadre.platform.android

import android.view.KeyEvent
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeSurfaceInput
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.TextInputCursorCommand
import org.graphiks.kadre.internal.runtime.TextInputDocumentCommand
import org.graphiks.kadre.internal.runtime.TextInputOpenCommand
import org.graphiks.kadre.internal.runtime.TextInputOwner
import org.graphiks.kadre.internal.runtime.TextInputPort
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceState

/**
 * Les touches dont le défaut DOCUMENTAIRE est le scroll du document — le miroir exact des
 * 9 touches `WEB_DOCUMENT_SCROLL_KEYS` (`WebInputTranslation.kt:186-196` : les quatre flèches,
 * les deux touches de page, Home, End et Space), traduites en keycodes Android :
 *
 * | Web           | Android                    |
 * |---------------|----------------------------|
 * | `ArrowUp`     | `KEYCODE_DPAD_UP`          |
 * | `ArrowDown`   | `KEYCODE_DPAD_DOWN`        |
 * | `ArrowLeft`   | `KEYCODE_DPAD_LEFT`        |
 * | `ArrowRight`  | `KEYCODE_DPAD_RIGHT`       |
 * | `PageUp`      | `KEYCODE_PAGE_UP`          |
 * | `PageDown`    | `KEYCODE_PAGE_DOWN`        |
 * | `Home`        | `KEYCODE_MOVE_HOME`        |
 * | `End`         | `KEYCODE_MOVE_END`         |
 * | `Space`       | `KEYCODE_SPACE`            |
 *
 * La policy se keye sur le NATIVE KEYCODE, pas sur la touche logique — l'écart assumé avec
 * le web (qui lit la touche logique, celle que le layout produit) est le fait de la table
 * Android de la phase 3 : `PageUp`/`PageDown`/`Home`/`End` ne sont PAS dans la table des 65
 * touches nommées ([AndroidKeyMapper]) et n'existent donc comme touche logique que comme
 * `Unidentified` — le keycode natif, lui, les porte toujours (review Task 1, carry T4).
 * L'ensemble est FERMÉ : l'étendre est un acte délibéré qui doit passer par cette table.
 */
internal val ANDROID_DOCUMENT_SCROLL_KEYS: Set<Int> = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_PAGE_UP,
    KeyEvent.KEYCODE_PAGE_DOWN,
    KeyEvent.KEYCODE_MOVE_HOME,
    KeyEvent.KEYCODE_MOVE_END,
    KeyEvent.KEYCODE_SPACE,
)

/**
 * Le port texte qui refuse — le miroir structurel inverse de `WebTextInputPort` : la phase 3
 * n'a AUCUN IME (le drop est phase 6, l'IME sa phase propre), et le paramètre
 * `textInputPort` du réducteur runtime n'est pas nullable : le refus `Unsupported(TextInput)`
 * est la seule promesse honnête — la capability dit Unsupported dès la construction, et
 * `openTextInput` du réducteur refuse AVANT même de consulter [open].
 */
internal object RefusingTextInputPort : TextInputPort {
    private val refusal = KadreFailure.Unsupported(KadreOperation.TextInput)

    override val capability: Capability<Unit> = Capability.Unsupported(refusal)

    override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> =
        KadreResult.Failure(refusal)

    override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> =
        KadreResult.Failure(refusal)

    override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> =
        KadreResult.Failure(refusal)
}

/**
 * L'allocateur de collecteurs que le runtime passe au seam est runtime-interne : le SPI le
 * porte comme [Any], la surface le rend inchangé plutôt que l'interpréter — le miroir exact
 * de `sessionCollectorAllocator` (`WebHostSession.kt:1980`). Une implémentation qui ne peut
 * pas est un bug, pas une policy qu'elle aurait le droit de substituer.
 */
internal fun sessionCollectorAllocator(allocator: Any): RuntimeEventCollectorAllocator =
    allocator as? RuntimeEventCollectorAllocator
        ?: error("runtime session collector allocator has an invalid type")

/**
 * Le pont d'observation → delivery (Task 3). L'observer — le miroir de `WebInputObserver` —
 * livre les observations T1 (touches, perte de focus) et T2 (motion) ; la policy ne répond
 * pas ici (elle est DANS le réducteur runtime), les stimuli partent vers
 * [RuntimeSurfaceInput.accept] dans l'ordre d'arrivée.
 *
 * **Pré-installation : bufferisées.** Toute observation qui précède [observationInstalled]
 * attend dans [pending], EN ORDRE — le miroir de `pendingInput` web — puis l'install soumet
 * D'ABORD le stimulus structurel `InputObservationChanged` portant les faits RÉELLEMENT
 * CÂBLÉS (les caps deviennent `Available` seulement là où un observateur vit — un install
 * sans observateur est absorbé par le réducteur, jamais une promesse vide) — et rejoue
 * ENSUITE le buffer, dans l'ordre (ruling P3-T3 : l'installation précède le replay ; les
 * stimuli rejoués portent donc la révision qui déclare déjà l'installation).
 *
 * La livraison, par observation :
 * - une [AndroidInputObservation.Key] DONT LE KEYCODE EST HORS TABLE ([AndroidKeyMapper])
 *   ne livre RIEN — forwarded-only, la décision de la Task 1 : aucun stimulus inventé depuis
 *   une identité native ; une touche mappée livre un `KeyChanged` complet (le mapper porte
 *   physique, logique, position, modificateurs ; l'état press/release voyage dans
 *   l'observation) ;
 * - une [AndroidInputObservation.WindowFocusLost] oublie d'abord les contacts et la pression
 *   du tracker ([AndroidMotionTracker.reset] — sans émettre), puis livre `focusLost()` — le
 *   `StateReset(FocusLost)` in-band du runtime — et suspend la session texte (sans session,
 *   l'appel est un no-op ; le miroir de l'ordre web : l'instantané d'abord, la session ensuite) ;
 * - une motion passe au tracker ([AndroidMotionTracker.onObservation]) avec les bornes
 *   PHYSIQUES de la View et le scale factor de l'état COMMIS de la surface, et chaque
 *   stimulus rendu est `accept()` dans l'ordre.
 *
 * Contrat de thread : la delivery est construite et possédée par
 * `AndroidHostSurface.installSessionConfiguration` (le thread principal de l'attach) et
 * n'est appelée que depuis le thread principal Android — la même discipline que le wrapper
 * T1 et le tracker T2. Aucun verrou : le thread principal sérialise tout.
 */
internal class AndroidInputDelivery(
    private val surfaceId: SurfaceId,
    private val surfaceInput: RuntimeSurfaceInput,
    private val committedState: () -> SurfaceState,
) {
    private val tracker = AndroidMotionTracker(surfaceId)

    /** Les observations qui précèdent l'install — l'ordre d'arrivée est l'ordre de replay. */
    private val pending = ArrayDeque<PendingObservation>()

    private var installed: Boolean = false

    public fun onObservation(observation: AndroidInputObservation) {
        if (!installed) {
            pending.addLast(PendingObservation.Keyed(observation))
            return
        }
        deliver(observation)
    }

    public fun onMotion(
        observation: AndroidMotionObservation,
        width: Int,
        height: Int,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ) {
        if (!installed) {
            pending.addLast(PendingObservation.Motion(observation, width, height, offsetX, offsetY))
            return
        }
        deliverMotion(observation, width, height, offsetX, offsetY)
    }

    /**
     * L'installation de l'observation : le stimulus structurel — PORTANT LES FAITS RÉELLEMENT
     * CÂBLÉS (les caps `Available` SEULEMENT pour un observateur installé ; la sémantique C de
     * BACKEND-CAPABILITIES §4, la même honnêteté que le port texte refusant et le
     * `dragAndDropAvailable = false`) — puis le replay ORDONNÉ du buffer. Un install SANS
     * observateur câblé (`false, false, false`) soumet quand même le stimulus : le réducteur
     * l'absorbe (capabilities déjà Unsupported, révision intouchée — le dédoublonnage du
     * runtime est le contrat), aucune promesse vide n'est publiée. Idempotent : une seconde
     * installation ne rejoue rien — le buffer est vide et le stimulus structurel est absorbé
     * par le réducteur (mêmes capabilities, révision intacte) ; le garde évite le débat.
     */
    public fun observationInstalled(
        keyboardInstalled: Boolean,
        pointerInstalled: Boolean,
        touchInstalled: Boolean,
    ) {
        if (installed) return
        installed = true
        surfaceInput.accept(
            SurfaceStimulus.InputObservationChanged(
                surfaceId = surfaceId,
                keyboardInstalled = keyboardInstalled,
                pointerInstalled = pointerInstalled,
                touchInstalled = touchInstalled,
                gestureKinds = emptySet(),
            ),
        )
        while (pending.isNotEmpty()) {
            when (val buffered = pending.removeFirst()) {
                is PendingObservation.Keyed -> deliver(buffered.observation)
                is PendingObservation.Motion ->
                    deliverMotion(buffered.observation, buffered.width, buffered.height, buffered.offsetX, buffered.offsetY)
            }
        }
    }

    private fun deliver(observation: AndroidInputObservation) {
        when (observation) {
            is AndroidInputObservation.Key -> deliverKey(observation)
            AndroidInputObservation.WindowFocusLost -> deliverFocusLost()
        }
    }

    /** Une touche mappée → `KeyChanged` complet ; hors table → rien (forwarded-only, T1). */
    private fun deliverKey(observation: AndroidInputObservation.Key) {
        if (AndroidKeyMapper.hidUsage(observation.nativeKeyCode) == null) return
        surfaceInput.accept(
            SurfaceStimulus.KeyChanged(
                surfaceId = surfaceId,
                physicalKey = AndroidKeyMapper.physicalKey(observation.nativeKeyCode),
                logicalKey = AndroidKeyMapper.logicalKey(observation.nativeKeyCode, observation.unicodeChar),
                location = AndroidKeyMapper.location(observation.isSystemKey, observation.metaState),
                keyState = observation.keyState,
                // Un repeat est par définition une pression — un chemin OEM qui livrerait un
                // UP porteur de repeatCount ne franchit pas l'invariant du modèle
                // (`repeat ⇒ Pressed`) : le flag tombe avec la relève, le stimulus reste vrai.
                repeat = observation.repeat && observation.keyState == KeyState.Pressed,
                modifiers = AndroidKeyMapper.modifiers(observation.metaState),
            ),
        )
    }

    /**
     * La perte de focus : le tracker oublie ses contacts SANS émettre (le reset est de la
     * livraison), le runtime livre le `StateReset(FocusLost)` in-band, la session texte est
     * suspendue — le miroir de l'ordre web (`acceptInput`, branche `FocusLost`).
     */
    private fun deliverFocusLost() {
        tracker.reset()
        surfaceInput.focusLost()
        surfaceInput.suspendTextInput()
    }

    /**
     * Une motion : le tracker décide, chaque stimulus rendu part dans l'ordre. L'offset
     * ([offsetX]/[offsetY] — l'origine de la View dans l'espace fenêtre, échantillonné par la
     * couture au moment du dispatch) voyage avec l'observation : le tracker convertit les
     * coordonnées fenêtre du dispatch en view-local AVANT ses gates et son mapping.
     */
    private fun deliverMotion(
        observation: AndroidMotionObservation,
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int,
    ) {
        val state = committedState()
        tracker.onObservation(
            observation,
            viewWidth = width,
            viewHeight = height,
            scaleFactor = state.scaleFactor,
            viewOffsetX = offsetX,
            viewOffsetY = offsetY,
        ).forEach(surfaceInput::accept)
    }

    /** Une observation bufferisée : key/focus telle quelle, motion avec ses bornes et son offset copiés. */
    private sealed interface PendingObservation {
        data class Keyed(val observation: AndroidInputObservation) : PendingObservation
        data class Motion(
            val observation: AndroidMotionObservation,
            val width: Int,
            val height: Int,
            val offsetX: Int,
            val offsetY: Int,
        ) : PendingObservation
    }
}
