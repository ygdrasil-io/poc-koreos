package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*

/** Explicit interactive tool, never a test or an automatic contract success. */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
internal object Win32InputManualHarness {
    @JvmStatic fun main(args: Array<String>) {
        println("Win32 input harness: Escape or the close button exits. Output contains the keys you enter.")
        println(runKadreApplication(options = DesktopHostOptions.Standalone(backend = DesktopBackend.Win32),
            application = KadreApplication {
                val request = (windows.requestWindow(WindowSpec(title = "Kadre input — Escape to close")) as KadreResult.Success).value
                val window = (request.await() as WindowRequestOutcome.OpenedHere).window
                if (args.contains("--suppress")) window.surface.apply(SurfaceUpdate(
                    inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible)))
                println("metrics=${window.surface.state.value}")
                val events = launch(start = CoroutineStart.UNDISPATCHED) {
                    window.surface.input.events.collect { event ->
                        println("$event state=${window.surface.input.state.value}")
                        if (event is InputEvent.Key && event.logicalKey == LogicalKey.Named(NamedKey.Escape)) window.close()
                    }
                }
                val closes = launch(start = CoroutineStart.UNDISPATCHED) {
                    window.events.collect { if (it is WindowEvent.CloseRequested) window.close() }
                }
                window.state.first { it.phase == WindowPhase.Closed }
                events.cancelAndJoin()
                closes.cancelAndJoin()
            }))
    }
}
