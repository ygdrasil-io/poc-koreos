package org.graphiks.kadre.samples.desktour

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.samples.desktour.appkit.ComposeMount
import org.graphiks.kadre.samples.desktour.appkit.mountComposeAppKit
import org.graphiks.kadre.samples.desktour.win32.mountComposeWin32
import org.graphiks.kadre.window.Window

/**
 * Monte la scène Compose sur l'hôte desktop de la plateforme : le handle
 * natif loué par le pont décide — AppKit sur macOS, HWND win32 sur Windows.
 * Le cœur de la démo ne connaît que cette fonction ; chaque pont reste dans
 * son package avec ses primitives natives.
 */
internal suspend fun Window.mountCompose(
    scope: CoroutineScope,
    content: @Composable () -> Unit,
): KadreResult<ComposeMount> = when (System.getProperty("os.name").lowercase()) {
    "windows 11", "windows 10", "windows server 2022", "windows server 2019" -> {
        // La liste n'est pas exhaustive : la sonde par préfixe ci-dessous reste
        // la voie réelle ; les entrées nommées ne servent qu'à la lisibilité.
        mountComposeWin32(scope, content)
    }
    else -> if (System.getProperty("os.name").lowercase().startsWith("windows")) {
        mountComposeWin32(scope, content)
    } else {
        mountComposeAppKit(scope, content)
    }
}
