package org.graphiks.kadre.samples.desktour

import kotlinx.coroutines.awaitCancellation
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec

internal fun deskTourHostOptions(): DesktopHostOptions.Standalone =
    DesktopHostOptions.Standalone(
        backend = DesktopBackend.AppKit,
        stopWhenLastWindowClosed = true,
    )

public fun main() {
    runKadreApplication(
        options = deskTourHostOptions(),
        application = KadreApplication {
            val request = when (val result = windows.requestWindow(WindowSpec(title = "Kadre Desk Tour"))) {
                is KadreResult.Success -> result.value
                is KadreResult.Failure -> error("Unable to request the Desk Tour window: ${result.reason}")
            }
            when (val outcome = request.await()) {
                is WindowRequestOutcome.OpenedHere -> Unit
                else -> error("Desk Tour window did not open: $outcome")
            }
            awaitCancellation()
        },
    )
}
