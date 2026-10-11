package org.graphiks.kadre.platform.android

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.RejectedWindowField
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowCapabilities
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowCloseResponseOutcome
import org.graphiks.kadre.window.WindowCloseRequestId
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowRevision
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowState
import org.graphiks.kadre.window.WindowSystemButtons
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome

/**
 * Fenêtre primaire Activity (forme Activity uniquement) : `primary` exposée, surface partagée
 * par identité avec la surface primaire, `requestWindow` immédiatement
 * `Rejected(Unsupported(RequestWindow))`. La forme View n'installe PAS ce port : le manager
 * par défaut du runtime (`UnsupportedWindowManager`, celui que
 * `RuntimeHostController.withPrimarySurface` installe) laisse `primary == null` et la liste de
 * fenêtres vide.
 *
 * Le miroir exact du port que `RuntimeHostController.withComponents` consomme — le port
 * `WindowManager` de `WebHostWindowManager` — sur le modèle du web sans provider
 * (`WebHostWindowManager.resolveOutcome`, premier échelon) et du manager non supporté du
 * runtime (`UnsupportedWindowManager`) :
 *
 * - une fenêtre primaire unique, créée à la construction (le moment d'attach du manager) :
 *   sa `surface` est l'instance de `HostSurface` de la surface primaire, LA MÊME INSTANCE —
 *   l'assertion d'identité de la topologie (`WindowManagerState.primary ∈ windows`, invariant
 *   de `WindowManagerState`) ;
 * - aucune commande de fenêtre supportée en phase 1 : `requestWindow` admet une requête dont
 *   l'outcome est déjà terminal `Rejected(Unsupported(RequestWindow))` avant que l'appelant
 *   ne la voie (le degenerate shape du modèle web, rejet synchrone sans primitive native) ;
 *   les autres commandes de la fenêtre primaire répondent chacune dans la forme exacte que
 *   la ligne `OPERATION-CONTRACTS` §4 admet — il n'existe aucun « état par défaut du
 *   runtime » pour un backend sans implémentation (`UnsupportedWindowManager` ne crée jamais
 *   de fenêtre) et cette classe est la seule `Window` vivante non supportante du dépôt :
 *   `apply` répond `Success(PartiallyApplied(...))` avec chaque champ demandé rejeté
 *   `Unsupported(UpdateWindow)` (aucun champ appliqué), `requestAttention` reste
 *   `Unsupported(RequestWindowAttention)`, `close` répond `Temporary(retryable = false)`
 *   (ruling provisoire, voir [AndroidActivityWindow.close]) et `respondToCloseRequest`
 *   répond `Invalid("requestId")`. La table complète des commandes réelles est la phase 4.
 *
 * Task 5 (composition) installe ce manager dans les composants de session de la forme
 * Activity, à côté de la même surface primaire ; la forme View continue d'utiliser
 * `withPrimarySurface` et ne voit jamais cette classe.
 */
internal class AndroidActivityWindowManager(
    primarySurface: HostSurface,
    private val nextRequestId: () -> WindowRequestId = RuntimeProcessIds::nextWindowRequestId,
) : WindowManager {

    private val primaryWindow: Window = AndroidActivityWindow(primarySurface)

    private val mutableState = MutableStateFlow(
        WindowManagerState(
            primary = primaryWindow,
            windows = listOf(primaryWindow),
            capabilities = WindowManagerCapabilities(
                Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
            ),
            revision = WindowManagerRevision(0),
        ),
    )

    override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

    override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> {
        // Le même degenerate shape que le web sans provider et que `UnsupportedWindowManager` :
        // la requête est admise, mais son outcome est publié avant que l'appelant ne la voie —
        // rejet synchrone `Unsupported(RequestWindow)`, aucun budget, aucune primitive native.
        val request = AndroidActivityWindowRequest(nextRequestId())
        return KadreResult.Success(request)
    }
}

/**
 * La fenêtre primaire unique de la forme Activity : l'Activity elle-même vue comme fenêtre.
 *
 * Sa `surface` est l'instance de la surface primaire passée à la construction — l'identité
 * partagée que la topologie Activity garantit. Chaque sous-système de fenêtre reste
 * explicitement non supporté en phase 1, mais chaque commande répond dans la forme exacte
 * que sa ligne `OPERATION-CONTRACTS` §4 admet (voir les KDoc de commande ci-dessous), et
 * l'instantané initial est dérivé de la surface primaire au lieu d'inventer une géométrie.
 */
private class AndroidActivityWindow(
    surface: HostSurface,
) : Window {

    override val id: WindowId = RuntimeProcessIds.nextWindowId()

    /** LA MÊME INSTANCE que la surface primaire de la session — l'assertion de topologie. */
    override val surface: HostSurface = surface

    private val mutableState = MutableStateFlow(initialState(surface))

    override val state: StateFlow<WindowState> = mutableState.asStateFlow()

    override val capabilities: StateFlow<WindowCapabilities> =
        MutableStateFlow(unsupportedWindowCapabilities())

    override val events: Flow<WindowEvent> = emptyFlow()

    override suspend fun apply(update: WindowUpdate): KadreResult<WindowUpdateOutcome> {
        // Contrat §4 (`Window.apply`) : la forme admise pour un champ valide mais non supporté
        // est le succès `PartiallyApplied`, jamais une failure externe. Aucun champ n'est
        // appliqué en phase 1 : chaque champ réellement demandé est rejeté individuellement
        // `RejectedWindowField(Unsupported(UpdateWindow))` — le miroir exact de
        // `RuntimeWindowManager.changedProperties` — et, la fenêtre n'étant pas mutée, l'état
        // rendu est l'instantané courant : aucune révision, aucun événement. Une mise à jour
        // intégralement `Unchanged` ne demande rien : `Applied` sur l'état courant (le même
        // no-op que le runtime rend pour un rejet vide). La garde `expectedRevision` reste
        // honnête : l'appelant qui exige une révision non courante reçoit `Stale`.
        val current = mutableState.value
        update.expectedRevision?.let { expected ->
            if (expected != current.revision) {
                return KadreResult.Failure(
                    KadreFailure.StaleRevision(expected.value, current.revision.value),
                )
            }
        }
        val rejected = changedWindowProperties(update)
            .map { RejectedWindowField(it, KadreFailure.Unsupported(KadreOperation.UpdateWindow)) }
        val operationId = RuntimeProcessIds.nextWindowOperationId()
        return KadreResult.Success(
            if (rejected.isEmpty()) {
                WindowUpdateOutcome.Applied(operationId, current)
            } else {
                WindowUpdateOutcome.PartiallyApplied(operationId, current, rejected)
            },
        )
    }

    override suspend fun requestAttention(attention: WindowAttention): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention))

    /**
     * Ruling du contrôleur (phase 1, décision normative parkée) : la fenêtre d'host Activity
     * est structurellement non fermable — l'Activity possède son cycle de vie, Kadre ne peut
     * ni la fermer d'ici la phase 4 ni répondre `Unsupported(CloseWindow)`, failure que la
     * ligne `Window.close` n'admets PAS (elle n'admets exactement que `Temporary` et
     * `Platform`). La réponse provisoire on-row est donc `Temporary(retryable = false)` :
     * la seule failure de la ligne qui dit honnêtement « la commande ne peut pas aboutir ici,
     * et réessayer ne changera rien » sans réviser le contrat. Idempotente : chaque appel
     * reçoit la même réponse. La décision normative (sémantiques `Window.close`/Back) est
     * explicitement différée à la phase 4 (roadmap §4, « Fermer la décision `Window.close`/
     * Back avant son code »).
     */
    override suspend fun close(): KadreResult<WindowCloseOutcome> =
        KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = false))

    /**
     * Véridique et terminal : `events` est vide — aucun événement `CloseRequested` n'est
     * jamais publié — donc aucun `requestId` ne peut jamais être valide. `Invalid("requestId")`
     * est le field exact que le registre `InvalidRequest` d'`OPERATION-CONTRACTS` réserve à
     * cette opération, et la ligne l'admets.
     */
    override suspend fun respondToCloseRequest(
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): KadreResult<WindowCloseResponseOutcome> =
        KadreResult.Failure(KadreFailure.InvalidRequest("requestId"))

    /** L'instantané initial, dérivé de la surface primaire — aucune géométrie inventée. */
    private fun initialState(surface: HostSurface): WindowState {
        val snapshot = surface.state.value
        return WindowState(
            phase = WindowPhase.Open,
            title = "",
            outerBounds = null,
            contentSize = snapshot.logicalSize,
            minimumSize = null,
            maximumSize = null,
            resizable = false,
            fullscreen = FullscreenMode.Windowed,
            decorations = WindowDecorations.System,
            systemButtons = WindowSystemButtons.All,
            level = WindowLevel.Normal,
            transparent = false,
            blurBehind = false,
            icon = null,
            contentProtection = false,
            revision = WindowRevision(0),
        )
    }
}

/** Les capacités de fenêtre de la phase 1 : toutes non supportées, chaque opération nommée. */
private fun unsupportedWindowCapabilities(): WindowCapabilities {
    val unsupportedUpdate = KadreFailure.Unsupported(KadreOperation.UpdateWindow)
    return WindowCapabilities(
        title = Capability.Unsupported(unsupportedUpdate),
        outerPosition = Capability.Unsupported(unsupportedUpdate),
        contentSize = Capability.Unsupported(unsupportedUpdate),
        minimumSize = Capability.Unsupported(unsupportedUpdate),
        maximumSize = Capability.Unsupported(unsupportedUpdate),
        resizable = Capability.Unsupported(unsupportedUpdate),
        fullscreen = Capability.Unsupported(unsupportedUpdate),
        decorations = Capability.Unsupported(unsupportedUpdate),
        systemButtons = Capability.Unsupported(unsupportedUpdate),
        level = Capability.Unsupported(unsupportedUpdate),
        transparency = Capability.Unsupported(unsupportedUpdate),
        blurBehind = Capability.Unsupported(unsupportedUpdate),
        icon = Capability.Unsupported(unsupportedUpdate),
        contentProtection = Capability.Unsupported(unsupportedUpdate),
        attention = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention)),
        closeInterception = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RespondToCloseRequest)),
        platformAccess = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess)),
    )
}

/**
 * La requête que `requestWindow` rend : son outcome est déjà terminal au rejet
 * `Unsupported(RequestWindow)` avant que l'appelant ne la voie, donc les sémantiques de
 * référence sont triviales — `cancel()` répond `AlreadyTerminated`, `await()` ne suspend pas
 * et `close()` ne libère rien (aucun budget n'a été consommé).
 */
private class AndroidActivityWindowRequest(
    override val id: WindowRequestId,
) : WindowRequest {
    private val outcome = WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow))
    private val mutableState = MutableStateFlow<WindowRequestState>(WindowRequestState.Terminated(outcome))

    override val state: StateFlow<WindowRequestState> = mutableState.asStateFlow()

    override fun close() = Unit

    override suspend fun cancel(): WindowCancellationOutcome =
        WindowCancellationOutcome.AlreadyTerminated(outcome)

    override suspend fun await(): WindowRequestOutcome = outcome
}

/**
 * Les champs réellement demandés par une mise à jour — le miroir exact de
 * `RuntimeWindowManager.changedProperties` : seuls les champs non `Unchanged` figurent dans
 * le rejet, jamais un champ que l'appelant n'a pas posé.
 */
private fun changedWindowProperties(update: WindowUpdate): List<WindowProperty> = buildList {
    if (update.title !is PropertyChange.Unchanged) add(WindowProperty.Title)
    if (update.outerPosition !is PropertyChange.Unchanged) add(WindowProperty.OuterPosition)
    if (update.contentSize !is PropertyChange.Unchanged) add(WindowProperty.ContentSize)
    if (update.minimumSize !is PropertyChange.Unchanged) add(WindowProperty.MinimumSize)
    if (update.maximumSize !is PropertyChange.Unchanged) add(WindowProperty.MaximumSize)
    if (update.resizable !is PropertyChange.Unchanged) add(WindowProperty.Resizable)
    if (update.fullscreen !is PropertyChange.Unchanged) add(WindowProperty.Fullscreen)
    if (update.decorations !is PropertyChange.Unchanged) add(WindowProperty.Decorations)
    if (update.systemButtons !is PropertyChange.Unchanged) add(WindowProperty.SystemButtons)
    if (update.level !is PropertyChange.Unchanged) add(WindowProperty.Level)
    if (update.transparency !is PropertyChange.Unchanged) add(WindowProperty.Transparency)
    if (update.blurBehind !is PropertyChange.Unchanged) add(WindowProperty.Blur)
    if (update.icon !is PropertyChange.Unchanged) add(WindowProperty.Icon)
    if (update.contentProtection !is PropertyChange.Unchanged) add(WindowProperty.ContentProtection)
}
