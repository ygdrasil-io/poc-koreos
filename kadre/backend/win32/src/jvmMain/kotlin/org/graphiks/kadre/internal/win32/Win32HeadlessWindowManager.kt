package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowSpec

/**
 * Gestionnaire de fenêtres d'une session headless (tranche 1) : aucune
 * fenêtre, `requestWindow` = `Unsupported(RequestWindow)` — jamais un faux
 * succès ; la garantie Desktop complète de `requestWindow` attend la phase 2.
 */
internal class Win32HeadlessWindowManager : WindowManager {

    private val mutableState = MutableStateFlow(
        WindowManagerState(
            primary = null,
            windows = emptyList(),
            capabilities = WindowManagerCapabilities(
                Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
            ),
            revision = WindowManagerRevision(0),
        ),
    )

    override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

    override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> {
        return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindow))
    }
}
