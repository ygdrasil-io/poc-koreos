package org.graphiks.kadre.platform.android

import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TouchId
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.interaction.InteractionRegistration
import org.graphiks.kadre.internal.runtime.NativeInteractionOutcome
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorGate
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeInteractionHandler
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.window.FullscreenMode

/**
 * Le moteur d'interactions transitoires de la surface Android (phase 4, tâche 4) — le miroir web
 * (`WebHostSession.kt:1002-1043` et `:1065-1116`, lui-même aligné sur le noyau
 * `MinimalWindowSurface.dispatchSynchronousInteraction :440-471`) : le runtime porte le token
 * single-use, la sérialisation et la règle d'unicité ([RuntimeInteractionHandler] — « la cible
 * ne possède aucune mécanique de token propre », décision D1 du plan web) ; cette classe ne fait
 * que la configuration de session, le mapping trigger→événement et le mapping
 * action→primitives natives ([invokeNative]).
 *
 * L'ensemble ANNONCÉ est une décision de CONSTRUCTION — le niveau d'API et la présence des seams
 * sont des faits fixes du processus (la même discipline que `cursorNative`,
 * `pointerCaptureNative` et le `inputWired` de l'entrée : on n'annonce jamais un commit qui ne
 * peut pas arriver) :
 *
 * - `{EnterFullscreen, ExitFullscreen}` quand le seam natif de la fenêtre (tâche 1) est câblé —
 *   la forme Activity seule (une View n'a pas de fenêtre à passer plein écran) ;
 * - `{LockPointer, UnlockPointer}` en PLUS à partir de l'API 26 —
 *   `requestPointerCapture`/`releasePointerCapture` n'existent pas avant (le minSdk du module
 *   est 24), et le canal d'état du SDK qui CONFIRME le verrou (`Window.Callback.
 *   onPointerCaptureChanged`) vit sur le même enchaînement.
 *
 * Ne sont PAS annoncés, chacun avec sa raison : `OpenWindow` (aucune seconde Activity —
 * roadmap :190), `BeginWindowMove`/`BeginWindowResize` (aucune primitive de fenêtre Activity —
 * host-imposed), `AcceptDrop` (le drop est phase 6). L'admission du contexte refuse ces actions
 * AVANT tout appel natif ; le bras [invokeNative] qui les refuse est la totalité du `when`, pas
 * un chemin attendu.
 *
 * Le mapping `invokeNative`, décision du contrôleur :
 * - `EnterFullscreen`/`ExitFullscreen` → le seam fenêtre T1 (`setBorderlessFullscreen` +
 *   readback `isSystemBarVisible`), en `Now` : le commit d'insets est SYNCHRONE côté plateforme
 *   quand il a lieu, et le readback de la tâche 1 est la vérité publiée — un readback qui ne
 *   confirme pas (fenêtre pas encore attachée, API < 30) rapporte
 *   `TemporarilyUnavailable(retryable = true)`, le mot on-row de la tâche 1 ;
 * - `LockPointer`/`UnlockPointer` → le seam de capture T3, en `Now` : l'outcome d'interaction
 *   porte l'ADMISSION de la requête — le commit de la capture, lui, reste confirmé par le
 *   LISTENER du SDK (le mot de la tâche 3 : « le retour d'une requête SDK n'est pas la
 *   confirmation de la capture du pointeur »), exactement comme pour le champ ;
 * - toute autre action → `Now(Failure(Unsupported(Interaction)))`.
 *
 * Contrat de thread : construit et fermé sur le thread principal de l'attach ;
 * [dispatchInteraction] n'est appelé que depuis le thread principal (le chemin d'entrée) ;
 * `install` peut être appelé depuis tout thread (le verrou du runtime sérialise). Le handler
 * applicatif s'exécute sur le thread appelant de [dispatchInteraction] — le thread principal
 * Android, le thread du callback natif, comme le web dans son callback DOM.
 */
@OptIn(DelicateKadreApi::class)
internal class AndroidInteractionEngine(
    private val surfaceId: SurfaceId,
    /** Le seam natif de la fenêtre (tâche 1) — les primitives fullscreen, `null` sans fenêtre. */
    private val windowNative: AndroidWindowNative?,
    /** Le seam de capture (tâche 3) — les primitives de verrou, `null` sans View câblée. */
    private val pointerCapture: AndroidPointerCapture?,
    /** Le niveau d'API de la plateforme — le fait qui décide des kinds de capture (API 26+). */
    apiLevel: Int,
    deliveryPolicy: WindowDeliveryPolicy,
    eventCollectorGate: RuntimeEventCollectorGate,
    failureReporter: RuntimeFailureReporter,
    sessionFailureHandler: (KadreFailure) -> Unit,
    maxPendingInteractionRequests: Int,
    /** La source de stamps de session — LA MÊME que le réducteur d'entrée de la surface. */
    private val stampSource: () -> EventStamp,
) {

    /**
     * L'ensemble annoncé — écrit kind par kind (une capability est une promesse, la promesse est
     * épelée), dans l'ordre du contrôleur : les fullscreen d'abord, la capture ensuite.
     */
    val advertised: Set<InteractionKind> = buildSet {
        if (windowNative != null) {
            add(InteractionKind.EnterFullscreen)
            add(InteractionKind.ExitFullscreen)
        }
        if (apiLevel >= 26 && pointerCapture != null) {
            add(InteractionKind.LockPointer)
            add(InteractionKind.UnlockPointer)
        }
    }

    /**
     * Le moteur commun du runtime — le token, la sérialisation, l'unicité, le budget des
     * requêtes pendentives et le chemin d'échec de session (un handler qui jette produit
     * `ApplicationFailure` à travers [sessionFailureHandler], DESIGN §9.6 :978-981).
     */
    private val runtime = RuntimeInteractionHandler(
        surfaceId = surfaceId,
        advertised = advertised,
        deliveryPolicy = deliveryPolicy,
        eventCollectorGate = eventCollectorGate,
        failureReporter = failureReporter,
        sessionFailureHandler = sessionFailureHandler,
        maxPendingInteractionRequests = maxPendingInteractionRequests,
    )

    /**
     * Le terminal du moteur, posé par [AndroidHostSurface.close] — après lui, plus aucun
     * dispatch ne prend de stamp ni n'informe quiconque (le miroir de la garde
     * `admissionClosed` web : une surface terminale ne dispatche rien), et la registration
     * vivante est fermée par [RuntimeInteractionHandler.close] (les requêtes pendentives
     * abandonnées `Closed(Interaction)`). Volatile : écrit une fois sur le thread principal,
     * lu par le chemin d'entrée — le même thread, mais le modificateur porte la discipline.
     */
    @Volatile
    private var closed: Boolean = false

    /**
     * Admet l'unique handler de la surface — la délégation est toute l'histoire (la règle
     * d'unicité `AlreadyInUse(Interaction)` est celle du runtime). Les gardes d'admission et
     * d'installation vivent sur la surface : ici, le moteur est toujours installé.
     */
    fun install(handler: InteractionHandler): KadreResult<InteractionRegistration> =
        runtime.install(handler)

    /**
     * Le demi-dispatch du seam : un trigger que le chemin d'entrée a observé, dispatché
     * SYNCHRONIQUEMENT — AVANT que le stimulus ordinaire du même fait n'atteigne le réducteur
     * (l'ordre AppKit, `DESIGN.md:983-989` ; le callback DOM du miroir web). L'événement reçoit
     * son stamp de session AVANT l'appel du handler ; le handler court, son `finally`
     * publie l'outcome éventuel et invalide le token — et la delivery accepte ensuite le
     * stimulus ordinaire, sans rien savoir de ce qui vient de se passer.
     *
     * Sans handler installé, le dispatch du runtime sort tôt : le stamp est consommé (le miroir
     * exact de l'ordre web, qui mappe l'événement avant que `dispatch` ne filtre), le handler
     * n'est jamais invoqué, et le stimulus ordinaire coule seul — la règle du plan.
     *
     * Divergence web DÉLIBÉRÉE et bénigne : le web garde la PRÉSENCE du handler AVANT le
     * mapping (un trigger sans handler ne mappe donc rien et ne consomme aucun stamp), quand
     * Android mappe le trigger depuis son stimulus puis sort tôt du dispatch — un stamp de
     * session est consommé PAR TRIGGER DÉLIVRÉ même sans handler. La séquence reste monotone
     * et unique (une consommation par trigger, jamais partagée), aucun handler ne peut la
     * voir, et aucun garde-fou du runtime n'en dépend : consommer sans témoin est exactement
     * ce que le web fait d'un mapping dont le dispatch est filtré.
     */
    fun dispatchInteraction(trigger: RuntimeSynchronousInteraction) {
        if (closed) return
        val event = when (trigger) {
            is RuntimeSynchronousInteraction.PointerPressed -> InteractionEvent.PointerPressed(
                trigger.button,
                trigger.position,
                stampSource(),
            )

            is RuntimeSynchronousInteraction.KeyPressed -> InteractionEvent.KeyPressed(
                trigger.physicalKey,
                stampSource(),
            )

            is RuntimeSynchronousInteraction.TouchStarted -> InteractionEvent.TouchStarted(
                trigger.touchId,
                trigger.position,
                stampSource(),
            )
        }
        runtime.dispatch(event, advertised, ::invokeNative)
    }

    /**
     * Le terminal — la surface fermée ne dispatche plus rien et sa registration vivante est
     * fermée (le miroir de `MinimalWindowSurface.detach :494-499`, qui ferme le handler au
     * détachement). Idempotent par le drapeau et par le runtime.
     */
    fun close() {
        closed = true
        runtime.close()
    }

    /**
     * Le mapping action → primitives natives, décision du contrôleur (cf. la KDoc de la
     * classe). TOUT est `Now` sur Android : les deux seams ont un verrou de plateforme qui
     * décide dans l'appel (les insets du readback, l'admission de la capture), et l'outcome
     * d'interaction n'attend aucune confirmation asynchrone — le commit RÉEL de la capture
     * arrive par le listener du SDK, qui publie l'état de la surface sans passe par ici.
     */
    private fun invokeNative(action: InteractionAction): NativeInteractionOutcome = when (action) {
        is InteractionAction.EnterFullscreen -> when (action.mode) {
            // Pas de primitive : le changement de mode d'affichage discret n'est pas
            // atteignable depuis la fenêtre d'une Activity — aucun appel natif (le mot T1).
            is FullscreenMode.Exclusive -> refused()
            else -> fullscreen(targetBorderless = action.mode == FullscreenMode.Borderless)
        }

        InteractionAction.ExitFullscreen -> fullscreen(targetBorderless = false)

        is InteractionAction.LockPointer -> when (val seam = pointerCapture) {
            // Inatteignable par la voie publique : la kind n'est annoncée qu'avec le seam, et
            // l'admission refuse le reste avant tout appel — la totalité du when, pas un chemin.
            null -> refused()
            else -> when (action.mode) {
                // Le SEUL mode que l'action prend (le miroir `normaliseLockPointerMode` web) :
                // `Confined` reste le champ de la phase 4, les deux mécanismes ne se
                // confondent pas. L'outcome porte l'admission ; la confirmation est celle du
                // listener, qui publiera la capture sans passe par ici.
                PointerCaptureMode.Locked -> seam.requestLock()
                    ?.let { NativeInteractionOutcome.Now(KadreResult.Failure(it)) }
                    ?: NativeInteractionOutcome.Now(KadreResult.Success(Unit))

                else -> NativeInteractionOutcome.Now(
                    KadreResult.Failure(KadreFailure.InvalidRequest("action.mode")),
                )
            }
        }

        InteractionAction.UnlockPointer -> when (val seam = pointerCapture) {
            null -> refused()
            else -> {
                seam.releaseLock()
                NativeInteractionOutcome.Now(KadreResult.Success(Unit))
            }
        }

        // Non annoncées : OpenWindow (pas de seconde Activity — roadmap :190),
        // BeginWindowMove/BeginWindowResize (aucune primitive de fenêtre Activity),
        // AcceptDrop (le drop est phase 6). L'admission les refuse déjà — ce bras est la
        // totalité du when, jamais un chemin attendu.
        InteractionAction.BeginWindowMove,
        is InteractionAction.BeginWindowResize,
        is InteractionAction.AcceptDrop,
        is InteractionAction.OpenWindow,
        -> refused()
    }

    /**
     * La demande plein écran par le seam fenêtre T1 — `setBorderlessFullscreen` PUIS le
     * readback des barres (la vérité publiée de la tâche 1) : des barres encore visibles après
     * une demande de hide (fenêtre pas encore attachée, API < 30) ne sont pas un commit —
     * `TemporarilyUnavailable(retryable = true)`, le mot on-row de la tâche 1, jamais un
     * mensonge sur un commit qui n'a pas eu lieu.
     */
    private fun fullscreen(targetBorderless: Boolean): NativeInteractionOutcome {
        val seam = windowNative ?: return refused()
        seam.setBorderlessFullscreen(targetBorderless)
        return if (seam.isSystemBarVisible() == targetBorderless) {
            NativeInteractionOutcome.Now(KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)))
        } else {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }
    }

    /** Le refus des actions hors annonce — `Unsupported(Interaction)`, zéro appel natif. */
    private fun refused(): NativeInteractionOutcome =
        NativeInteractionOutcome.Now(KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.Interaction)))
}

/**
 * L'identité tactile d'un trigger d'interaction — le miroir exact de `WebTouchInteractions`
 * (`WebInputTracking.kt:160-190`) : un `TouchStarted` est dispatché AVANT le stimulus ordinaire
 * du même down (l'ordre AppKit), et rien d'autre n'a encore alloué d'identité — le trigger en
 * porte une, allouée une par trigger délivré, monotone. L'identité du trigger et celle du
 * réducteur sont deux allocations du même type opaque, faites dans deux lanes, et rien ne
 * prétend qu'elles coïncident : le handler lit celle de l'événement d'interaction, le
 * consommateur du flux d'entrée lit celle du réducteur — la promesse est l'unicité DANS la lane
 * d'interaction de la surface (un trigger, une identité, jamais réutilisée).
 */
internal class AndroidTouchInteractionTriggers {
    private var nextTouchId = 0L

    /** Construit le trigger `TouchStarted` d'un down de contact à [position]. */
    fun started(position: LogicalPoint): RuntimeSynchronousInteraction.TouchStarted {
        check(nextTouchId != Long.MAX_VALUE) { "touch interaction identity space exhausted" }
        val touchId = TouchId(nextTouchId)
        nextTouchId += 1L
        return RuntimeSynchronousInteraction.TouchStarted(touchId = touchId, position = position)
    }
}
