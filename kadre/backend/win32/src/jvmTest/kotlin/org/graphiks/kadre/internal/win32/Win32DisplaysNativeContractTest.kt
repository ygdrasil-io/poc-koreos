package org.graphiks.kadre.internal.win32

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.display.*
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.*

/**
 * Real session through the real provider: the display inventory enumerates at
 * least the primary monitor with non-degenerate bounds and real modes.
 * Multi-monitor topology, DPI changes and hotplug stay manual receptions.
 */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32DisplaysNativeContractTest {
    @Test fun realInventoryEnumeratesThePrimaryWithRealBoundsAndModes() {
        val observed = CompletableDeferred<DisplayInventory>()
        runKadreApplication(
            options = DesktopHostOptions.Standalone(backend = DesktopBackend.Win32),
            application = KadreApplication {
                val requested = windows.requestWindow(WindowSpec(title = "Kadre native displays proof",
                    contentSize = LogicalSize(320.0, 240.0)))
                val window = ((requested as? KadreResult.Success)?.value?.await() as? WindowRequestOutcome.OpenedHere)?.window
                    ?: error("Window did not open")
                launch(start = CoroutineStart.UNDISPATCHED) {
                    displays.state.collect { state -> observed.complete(state.inventory) }
                }
                val inventory = withTimeout(5_000) { observed.await() }
                val enumerated = assertIs<DisplayInventory.Enumerated>(inventory)
                assertTrue(enumerated.displays.isNotEmpty(), "at least one display must enumerate")
                val display = enumerated.primary ?: enumerated.displays.first()
                val state = display.state.value
                assertTrue(state.bounds.size.width > 0 && state.bounds.size.height > 0,
                    "bounds must be non-degenerate: ${state.bounds}")
                assertTrue(state.scaleFactor > 0.0, "scale must be positive: ${state.scaleFactor}")
                assertTrue(state.modes.isNotEmpty(), "modes must be enumerated")
                val current = assertNotNull(state.currentMode, "a current mode must be resolved")
                assertTrue(state.modes.contains(current), "the current mode must be among the modes")
                requestStop()
            },
        )
    }
}
