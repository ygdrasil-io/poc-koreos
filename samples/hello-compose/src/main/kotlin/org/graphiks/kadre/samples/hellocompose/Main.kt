/**
 * Sample hello-compose — interactive Jetpack Compose UI inside a native Kadre window.
 *
 * Opens an 800×600 Kadre window and renders a Material3 Compose UI into its CAMetalLayer
 * via Skiko's Metal backend (see [ComposeMetalRenderer]). Mouse events from Kadre are
 * forwarded to the ComposeScene, so the button below is genuinely clickable.
 *
 * Frame loop (same pattern as hello-triangle):
 *   aboutToWait → requestRedraw → WindowEvent.RedrawRequested → renderer.renderFrame()
 *
 * Usage: ./gradlew :samples:hello-compose:run
 * Requirements: macOS arm64 with JDK 25 (run on the main thread by Gradle).
 */
package org.graphiks.kadre.samples.hellocompose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key as ComposeKey
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.ActiveEventLoop
import org.graphiks.kadre.ApplicationHandler
import org.graphiks.kadre.EventLoop
import org.graphiks.kadre.PhysicalSize
import org.graphiks.kadre.WindowAttributes
import org.graphiks.kadre.WindowId
import org.graphiks.kadre.core.Key as KadreKey
import org.graphiks.kadre.core.MouseButton
import org.graphiks.kadre.core.KeyState
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowEvent

/**
 * The Compose UI shown inside the Kadre window.
 *
 * A counter incremented by a Material3 button — clicking it proves the whole chain works:
 * input forwarding → snapshot apply → recomposition → frame clock → Skia/Metal present.
 */
@androidx.compose.runtime.Composable
fun DemoUi() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var clicks by remember { mutableStateOf(0) }
            var typed by remember { mutableStateOf("") }
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Jetpack Compose in a Kadre window 🪟", style = MaterialTheme.typography.headlineSmall)
                Text("Rendered via Skiko → Metal (CAMetalLayer)", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { clicks++ }) {
                    Text("Clicked $clicks times")
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type here") },
                    singleLine = true,
                )
            }
        }
    }
}

class HelloComposeApp : ApplicationHandler {

    private var window: Window? = null
    private var renderer: ComposeMetalRenderer? = null

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        println("[hello-compose] canCreateSurfaces — creating window + ComposeScene")

        val win = eventLoop.createWindow(
            WindowAttributes(
                title = "Hello Compose — Kadre + Skiko/Metal",
                size = PhysicalSize(width = 800, height = 600),
                visible = true,
                resizable = true,
            ),
        )
        window = win

        val handle = win.rawWindowHandle
        if (handle !is RawWindowHandle.AppKit || handle.nsLayer == 0L) {
            println("[hello-compose] Unsupported platform (CAMetalLayer required): $handle")
            eventLoop.exit()
            return
        }

        val r = ComposeMetalRenderer(handle.nsLayer, win.scaleFactor)
        val inner = win.innerSize
        r.resize(inner.width, inner.height, win.scaleFactor)
        r.setContent { DemoUi() }
        renderer = r

        println("[hello-compose] Ready — ${inner.width}×${inner.height} @ ${win.scaleFactor}x")
    }

    override fun aboutToWait(eventLoop: ActiveEventLoop) {
        // Continuous redraw (~vsync) keeps recomposition/animation pumped — like hello-triangle.
        window?.requestRedraw()
    }

    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: Any) {
        val r = renderer
        when (event) {
            is WindowEvent.RedrawRequested -> r?.renderFrame()

            is WindowEvent.PointerMoved ->
                r?.onPointerMoved(event.position.x, event.position.y)

            is WindowEvent.MouseInput -> {
                val (bit, button) = mapButton(event.button) ?: return
                r?.onPointerButton(bit, event.state == KeyState.Pressed, button)
            }

            is WindowEvent.MouseWheel -> r?.onScroll(event.deltaX, event.deltaY)
            is WindowEvent.PointerEntered -> r?.onPointerEnter()
            is WindowEvent.PointerLeft -> r?.onPointerExit()

            is WindowEvent.KeyboardInput -> {
                val composeKey = toComposeKey(event.key) ?: return
                val m = event.modifiers
                r?.sendKey(
                    key = composeKey,
                    down = event.state == KeyState.Pressed,
                    codePoint = codePointFor(event.key, m.shift),
                    ctrl = m.ctrl,
                    meta = m.meta,
                    alt = m.alt,
                    shift = m.shift,
                )
            }

            is WindowEvent.Resized -> {
                val win = window ?: return
                r?.resize(event.size.width, event.size.height, win.scaleFactor)
            }

            is WindowEvent.ScaleFactorChanged -> {
                val win = window ?: return
                val inner = win.innerSize
                r?.resize(inner.width, inner.height, event.factor)
            }

            is WindowEvent.CloseRequested -> {
                println("[hello-compose] CloseRequested — closing")
                r?.dispose()
                renderer = null
                eventLoop.exit()
            }

            else -> { /* ignore (keyboard forwarding is out of scope for this POC) */ }
        }
    }

    /** Maps a Kadre [MouseButton] to (Compose PointerButtons bit, [PointerButton]). */
    private fun mapButton(button: MouseButton): Pair<Int, PointerButton>? = when (button) {
        MouseButton.Left -> 1 to PointerButton.Primary
        MouseButton.Right -> 2 to PointerButton.Secondary
        MouseButton.Middle -> 4 to PointerButton.Tertiary
        is MouseButton.Other -> null
    }
}

/** Maps a Kadre logical [KadreKey] to its Compose [ComposeKey] equivalent (null if unmapped). */
private fun toComposeKey(key: KadreKey): ComposeKey? = when (key) {
    KadreKey.A -> ComposeKey.A; KadreKey.B -> ComposeKey.B; KadreKey.C -> ComposeKey.C
    KadreKey.D -> ComposeKey.D; KadreKey.E -> ComposeKey.E; KadreKey.F -> ComposeKey.F
    KadreKey.G -> ComposeKey.G; KadreKey.H -> ComposeKey.H; KadreKey.I -> ComposeKey.I
    KadreKey.J -> ComposeKey.J; KadreKey.K -> ComposeKey.K; KadreKey.L -> ComposeKey.L
    KadreKey.M -> ComposeKey.M; KadreKey.N -> ComposeKey.N; KadreKey.O -> ComposeKey.O
    KadreKey.P -> ComposeKey.P; KadreKey.Q -> ComposeKey.Q; KadreKey.R -> ComposeKey.R
    KadreKey.S -> ComposeKey.S; KadreKey.T -> ComposeKey.T; KadreKey.U -> ComposeKey.U
    KadreKey.V -> ComposeKey.V; KadreKey.W -> ComposeKey.W; KadreKey.X -> ComposeKey.X
    KadreKey.Y -> ComposeKey.Y; KadreKey.Z -> ComposeKey.Z
    KadreKey.Digit0 -> ComposeKey.Zero; KadreKey.Digit1 -> ComposeKey.One
    KadreKey.Digit2 -> ComposeKey.Two; KadreKey.Digit3 -> ComposeKey.Three
    KadreKey.Digit4 -> ComposeKey.Four; KadreKey.Digit5 -> ComposeKey.Five
    KadreKey.Digit6 -> ComposeKey.Six; KadreKey.Digit7 -> ComposeKey.Seven
    KadreKey.Digit8 -> ComposeKey.Eight; KadreKey.Digit9 -> ComposeKey.Nine
    KadreKey.F1 -> ComposeKey.F1; KadreKey.F2 -> ComposeKey.F2; KadreKey.F3 -> ComposeKey.F3
    KadreKey.F4 -> ComposeKey.F4; KadreKey.F5 -> ComposeKey.F5; KadreKey.F6 -> ComposeKey.F6
    KadreKey.F7 -> ComposeKey.F7; KadreKey.F8 -> ComposeKey.F8; KadreKey.F9 -> ComposeKey.F9
    KadreKey.F10 -> ComposeKey.F10; KadreKey.F11 -> ComposeKey.F11; KadreKey.F12 -> ComposeKey.F12
    KadreKey.Space -> ComposeKey.Spacebar; KadreKey.Enter -> ComposeKey.Enter
    KadreKey.Escape -> ComposeKey.Escape; KadreKey.Backspace -> ComposeKey.Backspace
    KadreKey.Tab -> ComposeKey.Tab
    KadreKey.ArrowUp -> ComposeKey.DirectionUp; KadreKey.ArrowDown -> ComposeKey.DirectionDown
    KadreKey.ArrowLeft -> ComposeKey.DirectionLeft; KadreKey.ArrowRight -> ComposeKey.DirectionRight
    KadreKey.ShiftLeft -> ComposeKey.ShiftLeft; KadreKey.ShiftRight -> ComposeKey.ShiftRight
    KadreKey.ControlLeft -> ComposeKey.CtrlLeft; KadreKey.ControlRight -> ComposeKey.CtrlRight
    KadreKey.AltLeft -> ComposeKey.AltLeft; KadreKey.AltRight -> ComposeKey.AltRight
    KadreKey.MetaLeft -> ComposeKey.MetaLeft; KadreKey.MetaRight -> ComposeKey.MetaRight
    KadreKey.Unknown -> null
}

/** UTF-16 code point for text-producing keys, so Compose text fields receive typed characters. */
private fun codePointFor(key: KadreKey, shift: Boolean): Int = when {
    key.name.length == 1 && key.name[0] in 'A'..'Z' ->
        (if (shift) key.name[0] else key.name[0].lowercaseChar()).code
    key.name.startsWith("Digit") -> key.name.last().code
    key == KadreKey.Space -> ' '.code
    else -> 0
}

/**
 * Entry point. Must run on the main macOS thread (Gradle adds `-XstartOnFirstThread`).
 *
 * Offscreen capture mode: `--capture <path>` renders [DemoUi] into an offscreen Skia raster
 * surface and writes a PNG, then exits — no window, no GPU, headless-safe (useful for CI).
 */
fun main(args: Array<String>) {
    val captureIndex = args.indexOf("--capture")
    if (captureIndex >= 0) {
        val path = args.getOrNull(captureIndex + 1)
            ?: error("--capture requires a file path: --capture <path>")
        captureDemoUiToPng(path)
        return
    }

    println("[hello-compose] Starting — Compose Multiplatform in a Kadre window")
    EventLoop().runApp(HelloComposeApp())
    println("[hello-compose] Done")
}
