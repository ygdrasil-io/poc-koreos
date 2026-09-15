package org.graphiks.kadre.samples.desktour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.samples.desktour.appkit.BridgeProbeContent
import org.graphiks.kadre.samples.desktour.appkit.BridgeProbeState
import org.graphiks.kadre.samples.desktour.appkit.mountComposeAppKit
import org.graphiks.kadre.surface.SurfaceEvent
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
            val probeState = MutableStateFlow(BridgeProbeState.from(window.surface.state.value))
            fun updateText(text: String) { probeState.update { it.copy(text = text) } }
            val bridgeContent: @Composable () -> Unit = {
                val state by probeState.collectAsState()
                BridgeProbeContent(state = state, onTextChanged = ::updateText)
            }
            val mount = when (val result = window.mountComposeAppKit(this, bridgeContent)) {
                is KadreResult.Success -> result.value
                is KadreResult.Failure -> error("Compose bridge unavailable: ${result.reason}")
            }
            val collectors = mutableListOf<Job>()
            val closeRequest = try {
                // These observers inherit Kadre's application context (possibly Default).
                // The mount marshals every scene/native operation to its AppKit executor.
                collectors += launch(start = CoroutineStart.UNDISPATCHED) {
                    window.surface.state.collect { state ->
                        mount.updateSurface(state)
                        probeState.update { it.observing(state) }
                    }
                }
                collectors += launch(start = CoroutineStart.UNDISPATCHED) {
                    window.surface.events.collect { event ->
                        when (val result = mount.surfaceEvent(event)) {
                            is KadreResult.Success -> Unit
                            is KadreResult.Failure -> error("Compose surface event failed: ${result.reason}")
                        }
                        if (event !is SurfaceEvent.RedrawRequested) {
                            probeState.update { it.observing(window.surface.state.value) }
                        }
                    }
                }
                collectors += launch(start = CoroutineStart.UNDISPATCHED) {
                    window.surface.input.events.collect { event ->
                        mount.dispatch(event)
                        probeState.update { it.copy(activity = mount.activity) }
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
