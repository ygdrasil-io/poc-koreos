package org.graphiks.kadre.samples.compose.desktop

import org.graphiks.kadre.PhysicalSize
import org.graphiks.kadre.WindowAttributes
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.coroutines.kadreApplication
import org.graphiks.kadre.samples.compose.infra.ComposeWindowRenderer
import org.graphiks.kadre.samples.compose.infra.KeyForwarder
import org.graphiks.kadre.samples.compose.infra.applyWindowEvent
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseApp

private fun runShowcase() = kadreApplication {
    val keys = KeyForwarder()
    val win = createWindow(
        WindowAttributes("Compose Showcase", PhysicalSize(900, 700), visible = true, resizable = true),
    )
    val handle = win.window.rawWindowHandle as? RawWindowHandle ?: run { exit(); return@kadreApplication }
    val renderer = ComposeWindowRenderer.create(handle, win.window.scaleFactor, dispatcher).getOrElse {
        println("[compose-showcase] Cannot create renderer: ${it.message}")
        exit(); return@kadreApplication
    }
    val inner = win.window.innerSize
    renderer.resize(inner.width, inner.height, win.window.scaleFactor)
    renderer.setContent { ShowcaseApp(PlatformContext()) }

    var lastRenderNanos = 0L
    val frameIntervalNanos = 50_000_000L

    win.events.collect { event ->
        when (event) {
            is WindowEvent.CloseRequested -> {
                renderer.dispose(); exit()
            }
            is WindowEvent.RedrawRequested -> {
                val now = System.nanoTime()
                if (now - lastRenderNanos >= frameIntervalNanos) {
                    lastRenderNanos = now
                    renderer.applyWindowEvent(event, win.window, keys)
                }
            }
            else -> renderer.applyWindowEvent(event, win.window, keys)
        }
    }
}

fun main() {
    println("[compose-showcase] Starting Compose Showcase")
    runShowcase()
    println("[compose-showcase] Done")
}
