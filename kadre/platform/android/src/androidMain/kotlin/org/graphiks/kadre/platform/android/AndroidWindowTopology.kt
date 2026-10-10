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
 *   les mutations `Window.apply` et les autres commandes suivent les états par défaut du
 *   runtime pour un backend sans implémentation — l'échec `Unsupported` de leur opération,
 *   comme `RuntimeHostSurface` nomme les siens.
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
 * explicitement non supporté en phase 1 : les commandes répondent `Unsupported` de leur
 * opération (les états par défaut du runtime pour un backend sans implémentation), et
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

    override suspend fun apply(update: WindowUpdate): KadreResult<WindowUpdateOutcome> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.UpdateWindow))

    override suspend fun requestAttention(attention: WindowAttention): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention))

    override suspend fun close(): KadreResult<WindowCloseOutcome> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CloseWindow))

    override suspend fun respondToCloseRequest(
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): KadreResult<WindowCloseResponseOutcome> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RespondToCloseRequest))

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
