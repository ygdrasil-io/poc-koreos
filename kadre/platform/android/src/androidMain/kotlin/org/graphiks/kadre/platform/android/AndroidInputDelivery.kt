package org.graphiks.kadre.platform.android

import android.view.KeyEvent
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeSurfaceInput
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
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
 * **Les interactions synchrones (phase 4, Task 4)** — l'ordre AppKit (`DESIGN.md:983-989`), le
 * miroir web « dans le callback DOM, avant le stimulus ordinaire » : CHAQUE stimulus porteur
 * d'un fait déclencheur — une pression bouton du lane pointeur (`PointerButtonChanged` Pressed,
 * `ACTION_BUTTON_PRESS` d'une souris comme la pression `Primary` d'un stylet), un down de
 * contact (`TouchChanged` Started), un key down (`KeyChanged` d'une touche en pression, les
 * auto-repeats du même voyage que le web : chaque keydown dispatch) — est PRÉCÉDÉ de la
 * dispatch de son trigger ([AndroidInteractionEngine.dispatchInteraction]) : le handler
 * applicatif est informé, son token consommé éventuellement, PUIS le stimulus ordinaire du
 * MÊME fait est accepté dans le réducteur. Les triggers sont dérivés des STIMULI mêmes — les
 * réductions du tracker (down doublon, contact hors bornes, geste muet) décident donc aussi
 * du trigger, les deux lanes ne peuvent pas diverger sur l'événement observé (le mot du miroir
 * web : « the contact table is what the ordinary path reads right after »). Une touche hors
 * table ne déclenche rien : il n'y a pas de stimulus ordinaire à précéder. Sans handler
 * installé, la dispatch du moteur sort tôt — le stimulus ordinaire coule seul (le miroir web
 * : la dispatch ne court que derrière un handler vivant).
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
    /**
     * Le moteur d'interactions (Task 4) — construit avec la MÊME configuration de session par
     * `installSessionConfiguration`, présent dès que cette delivery existe : la dispatch d'un
     * trigger n'informe que derrière un handler vivant (le filtre du runtime), jamais derrière
     * une promesse.
     */
    private val interactions: AndroidInteractionEngine,
) {
    private val tracker = AndroidMotionTracker(surfaceId)

    /** Les observations qui précèdent l'install — l'ordre d'arrivée est l'ordre de replay. */
    private val pending = ArrayDeque<PendingObservation>()

    /**
     * L'allocateur d'identités tactiles des triggers (Task 4) — un `TouchStarted` est
     * dispatché avant le stimulus ordinaire du même down, rien d'autre n'a alloué d'identité
     * (le miroir `WebTouchInteractions`).
     */
    private val touchTriggers = AndroidTouchInteractionTriggers()

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

    /**
     * Une touche mappée → `KeyPressed` en interaction PUIS `KeyChanged` complet ; hors table →
     * rien (forwarded-only, T1). La relation physique est celle que le stimulus ordinaire du
     * MÊME down porte — le trigger ne lit jamais une identité que l'ordinaire ne porterait pas.
     * Une RELÈVE ne déclenche pas (le miroir web : keydown seul) ; un auto-repeat, oui — chaque
     * keydown dispatch, le voyage du web est le même.
     */
    private fun deliverKey(observation: AndroidInputObservation.Key) {
        val physicalKey = AndroidKeyMapper.physicalKey(observation.nativeKeyCode)
        if (AndroidKeyMapper.hidUsage(observation.nativeKeyCode) == null) return
        if (observation.keyState == KeyState.Pressed) {
            // L'interaction informe le handler AVANT que le stimulus ordinaire du même down
            // n'atteigne le réducteur (l'ordre AppKit, le miroir web).
            interactions.dispatchInteraction(RuntimeSynchronousInteraction.KeyPressed(physicalKey))
        }
        surfaceInput.accept(
            SurfaceStimulus.KeyChanged(
                surfaceId = surfaceId,
                physicalKey = physicalKey,
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
     * Une motion : le tracker décide, chaque stimulus rendu part dans l'ordre — PRÉCÉDÉ, quand
     * il porte un fait déclencheur ([triggerFor]), de la dispatch de son interaction. L'offset
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
        ).forEach { stimulus ->
            // L'interaction informe le handler AVANT que le stimulus ordinaire du MÊME fait
            // n'atteigne le réducteur (l'ordre du noyau : interaction d'abord, stimulus
            // ordinaire ensuite). Sans handler vivant, la dispatch du moteur sort tôt et le
            // stimulus ordinaire coule seul.
            val trigger = triggerFor(stimulus)
            if (trigger != null) interactions.dispatchInteraction(trigger)
            surfaceInput.accept(stimulus)
        }
    }

    /**
     * Le trigger d'interaction d'un stimulus ordinaire, ou `null` — la dérivation lit le
     * STIMULUS même, jamais l'observation brute : les réductions du tracker (down doublon,
     * contact hors bornes, geste muet, position non finie) décident aussi du trigger, les deux
     * lanes ne peuvent pas diverger sur l'événement observé (le miroir web : « the contact
     * table is what the ordinary path reads right after »). Les deux familles du plan :
     *
     * - une pression bouton du lane pointeur (`ACTION_BUTTON_PRESS` d'une souris comme la
     *   pression `Primary` d'un stylet) → `PointerPressed(button, position, pressure)` — la
     *   pression du modèle voyage TELLE QUELLE, jamais narquée ;
     * - un down de contact (le lane touch) → `TouchStarted(touchId, position)` — l'identité
     *   tactile est celle du trigger ([AndroidTouchInteractionTriggers]), allouée ici car rien
     *   d'autre n'a encore alloué la sienne.
     */
    private fun triggerFor(stimulus: SurfaceStimulus): RuntimeSynchronousInteraction? = when {
        stimulus is SurfaceStimulus.PointerButtonChanged &&
            stimulus.buttonState == PointerButtonState.Pressed ->
            RuntimeSynchronousInteraction.PointerPressed(
                button = stimulus.button,
                position = stimulus.position,
                pressure = stimulus.pressure,
            )

        stimulus is SurfaceStimulus.TouchChanged && stimulus.phase == TouchPhase.Started ->
            touchTriggers.started(stimulus.position)

        else -> null
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
