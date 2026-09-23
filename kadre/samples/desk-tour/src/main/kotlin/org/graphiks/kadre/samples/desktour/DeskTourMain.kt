package org.graphiks.kadre.samples.desktour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.samples.desktour.appkit.ComposeMount
import org.graphiks.kadre.samples.desktour.appkit.mountComposeAppKit
import org.graphiks.kadre.samples.desktour.core.ActionDispatcher
import org.graphiks.kadre.samples.desktour.core.KadreTourGateway
import org.graphiks.kadre.samples.desktour.core.NoteKey
import org.graphiks.kadre.samples.desktour.core.TourStore
import org.graphiks.kadre.samples.desktour.ui.DeskTourApp
import org.graphiks.kadre.samples.desktour.ui.NoteContent
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseResponseOutcome
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec

internal fun deskTourHostOptions(): DesktopHostOptions.Standalone =
    DesktopHostOptions.Standalone(
        backend = DesktopBackend.AppKit,
        stopWhenLastWindowClosed = true,
    )

public fun main() {
    val outcome = runKadreApplication(
        options = deskTourHostOptions(),
        application = KadreApplication {
            val request = when (val result = windows.requestWindow(WindowSpec(title = "Kadre Desk Tour"))) {
                is KadreResult.Success -> result.value
                is KadreResult.Failure -> error("Unable to request the Desk Tour window: ${result.reason}")
            }
            val window = when (val outcome = request.await()) {
                is WindowRequestOutcome.OpenedHere -> outcome.window
                else -> error("Desk Tour window did not open: $outcome")
            }
            // The store, the dispatcher and the gateway know nothing about Compose or AppKit;
            // only this host file binds them to the current renderer.
            val store = TourStore()
            val gateway = KadreTourGateway(this)
            val dispatcher = ActionDispatcher(store, gateway)
            store.publishCreateNoteAvailability(gateway.createNoteAvailability())
            val uiState = store.state
            val bridgeContent: @Composable () -> Unit = {
                val state by uiState.collectAsState()
                DeskTourApp(
                    state = state,
                    onCreateNote = { launch { dispatcher.createNote() } },
                    onRenameNote = { key, title -> launch { dispatcher.renameNote(key, title) } },
                    onSelectRoute = { store.setRoute(it) },
                    onToggleApiDetails = { store.toggleApiDetails() },
                )
            }
            val mount = when (val result = window.mountComposeAppKit(this, bridgeContent)) {
                is KadreResult.Success -> result.value
                is KadreResult.Failure -> error("Compose bridge unavailable: ${result.reason}")
            }
            val mounted = mutableMapOf<NoteKey, ComposeMount>()
            val collectors = mutableListOf<Job>()
            val closeRequest = try {
                // These observers inherit Kadre's application context (possibly Default).
                // The mount marshals every scene/native operation to its AppKit executor.
                collectors += launch(start = CoroutineStart.UNDISPATCHED) {
                    window.surface.state.collect { state -> mount.updateSurface(state) }
                }
                collectors += launch(start = CoroutineStart.UNDISPATCHED) {
                    window.surface.events.collect { event ->
                        when (val result = mount.surfaceEvent(event)) {
                            is KadreResult.Success -> Unit
                            is KadreResult.Failure -> error("Compose surface event failed: ${result.reason}")
                        }
                    }
                }
                collectors += launch(start = CoroutineStart.UNDISPATCHED) {
                    window.surface.input.events.collect { event -> mount.dispatch(event) }
                }
                collectors += launch(start = CoroutineStart.UNDISPATCHED) {
                    gateway.observeWindow(window).collect { store.publishWindows(listOf(it)) }
                }
                // Chaque note reçoit sa propre scène Compose, montée une seule fois. L'hôte
                // observe le store plutôt que de s'accrocher à l'ouverture : c'est le seul
                // endroit qui connaît Compose, `core` reste renderer-agnostique.
                collectors += launch(start = CoroutineStart.UNDISPATCHED) {
                    store.state.map { state -> state.notes }.distinctUntilChanged().collect { notes ->
                        notes.forEach { note ->
                            if (note.key in mounted) return@forEach
                            val noteWindow = gateway.noteWindow(note.key) ?: return@forEach
                            when (val result = noteWindow.mountComposeAppKit(this, { NoteContent(note) })) {
                                is KadreResult.Success -> mounted[note.key] = result.value
                                is KadreResult.Failure -> store.publishNote(note.withMountFailure())
                            }
                        }
                    }
                }
                when (val redraw = window.surface.requestRedraw()) {
                    is KadreResult.Success -> Unit
                    is KadreResult.Failure -> error("Initial redraw rejected: ${redraw.reason}")
                }
                window.events.filterIsInstance<WindowEvent.CloseRequested>().first()
            } finally {
                withContext(NonCancellable) {
                    collectors.forEach(Job::cancel)
                    collectors.joinAll()
                    // Await owner cleanup, owned coroutine finalizers, and native quiescence.
                    mounted.values.forEach { it.close() }
                    mount.close()
                }
            }
            withContext(NonCancellable) {
                when (val result = window.respondToCloseRequest(closeRequest.requestId, WindowCloseDecision.Accept)) {
                    is KadreResult.Failure -> error("Unable to accept Desk Tour close request: ${result.reason}")
                    is KadreResult.Success -> when (result.value) {
                        is WindowCloseResponseOutcome.Closing -> Unit
                        WindowCloseResponseOutcome.KeptOpen -> error("Accepted Desk Tour close request kept the window open")
                        WindowCloseResponseOutcome.TooLate -> error("Desk Tour close request expired before acceptance")
                        WindowCloseResponseOutcome.AlreadyResolved -> error("Desk Tour close request was already resolved")
                    }
                }
            }
            awaitCancellation()
        },
    )
    if (outcome is SessionOutcome.Failed) error("Kadre Desk Tour failed: ${outcome.failure}")
}
