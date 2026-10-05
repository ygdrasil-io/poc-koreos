@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlinx.browser.window
import org.w3c.dom.Window
import org.w3c.dom.events.Event

/**
 * The browser `Gamepad` as this realization reads it. kotlinx-browser 0.5.0 declares nothing for the
 * Gamepad API, so the shape is declared here — the phase-4 `requestPointerLock` precedent: exactly
 * the members the structural seam copies, no more. `mapping` is nullable because a browser may omit
 * it. The buttons array is dense (the DOM always answers with one button object per control); the
 * pads array the navigator returns is not — its entries are nullable, and the nulls are the holes
 * Chromium leaves at the indices of absent gamepads.
 */
internal external interface WasmGamepad : JsAny {
    val index: Int
    val id: String
    val connected: Boolean
    val mapping: String?
    val buttons: JsArray<WasmGamepadButton>
    val axes: JsArray<JsNumber>
}

internal external interface WasmGamepadButton : JsAny {
    val value: Double
}

/**
 * The gamepad poll of the navigator.
 *
 * The navigator crosses as [JsAny] because the borrowed object needs no type of its own here, and a
 * browser without a poll to answer (Chrome answers `null` before the user interacts with the page)
 * is the `null` the realization reports as "observes nothing".
 */
@JsFun("(navigator) => navigator.getGamepads()")
internal external fun wasmNavigatorGamepads(navigator: JsAny): JsArray<WasmGamepad?>?

/**
 * The navigator's own event registration, which kotlinx-browser 0.5.0 does not declare either — and
 * which a browser may not implement at all: the engines disagree about whether the navigator is an
 * event target. Chromium's is not (probed 2026-10-05: `navigator.addEventListener` is `undefined`
 * there, while `getGamepads` is a function) and the spec fires the connection events at the window,
 * while engines that do fire them at the navigator implement EventTarget for it. The guard is the
 * browser fact, not an error: a navigator without the member is simply not listened to — the
 * announcement is heard on the window.
 */
@JsFun("(navigator, type, listener) => { if (typeof navigator.addEventListener === 'function') { navigator.addEventListener(type, listener); } }")
internal external fun wasmAddNavigatorGamepadListener(navigator: JsAny, type: String, listener: () -> Unit)

@JsFun("(navigator, type, listener) => { if (typeof navigator.removeEventListener === 'function') { navigator.removeEventListener(type, listener); } }")
internal external fun wasmRemoveNavigatorGamepadListener(navigator: JsAny, type: String, listener: () -> Unit)

/** One poll's copy of one browser pad; the borrowed object stays behind the seam. */
private class WasmDomGamepad(pad: WasmGamepad) : WebDomGamepad {
    override val index: Int = pad.index
    override val domId: String = pad.id
    override val connected: Boolean = pad.connected
    override val mapping: String? = pad.mapping
    override val buttonValues: List<Double> = List(pad.buttons.length) { index -> pad.buttons[index]?.value ?: 0.0 }
    override val axisValues: List<Double> = List(pad.axes.length) { index -> pad.axes[index]?.toDouble() ?: 0.0 }
}

/**
 * The Gamepad API seam of the browsing context, read through the navigator of [browsingWindow].
 *
 * A poll that cannot reach the API observes nothing rather than guessing. The two connection
 * listeners are registered on **both** the window (whose registration is a declared DOM member) and
 * the navigator (bridged through `@JsFun`, the one place where Kotlin/Wasm and JavaScript meet),
 * each where its target accepts listeners: the spec (and every engine) fires them at the window,
 * and engines that also fire them at their navigator implement EventTarget there while Chromium's
 * navigator does not. The hub treats the announcement as "poll again" only, so a browser that fires
 * both delivers one redundant poll and nothing else. `close` withdraws every registration handed
 * out.
 */
internal class WasmWebGamepadDom(private val browsingWindow: Window = window) : WebGamepadDom {
    private val navigator: JsAny = browsingWindow.navigator
    private val registrations = mutableListOf<AutoCloseable>()
    private var closed: Boolean = false

    override fun getGamepads(): List<WebDomGamepad?> {
        if (closed) return emptyList()
        val pads = wasmNavigatorGamepads(navigator) ?: return emptyList()
        // Positional, holes and all: entry i is the pad whose DOM index is i, and the null read of a
        // hole is the `JsArray.get` contract itself.
        return List(pads.length) { index -> pads[index]?.let(::WasmDomGamepad) }
    }

    override fun onGamepadAppeared(listener: () -> Unit): AutoCloseable = register("gamepadconnected", listener)

    override fun onGamepadDisappeared(listener: () -> Unit): AutoCloseable = register("gamepaddisconnected", listener)

    override fun close() {
        val remaining = registrations.toList()
        registrations.clear()
        closed = true
        remaining.forEach(AutoCloseable::close)
    }

    /** One live registration: the same announcement heard on the window and, where it exists, the navigator. */
    private fun register(type: String, listener: () -> Unit): AutoCloseable {
        val windowListener: (Event) -> Unit = { listener() }
        browsingWindow.addEventListener(type, windowListener)
        wasmAddNavigatorGamepadListener(navigator, type, listener)
        var withdrawn = false
        val registration = AutoCloseable {
            if (!withdrawn) {
                withdrawn = true
                browsingWindow.removeEventListener(type, windowListener)
                wasmRemoveNavigatorGamepadListener(navigator, type, listener)
            }
        }
        if (closed) {
            registration.close()
            return registration
        }
        registrations += registration
        return AutoCloseable {
            registration.close()
            registrations.remove(registration)
        }
    }
}

/**
 * The hub's frame cadence over [browsingWindow], reusing the one animation-frame registration the
 * target already owns (the redraw coalescer's) instead of a second rAF path.
 */
internal class WasmWebFrameScheduler(private val browsingWindow: Window = window) : WebFrameScheduler {
    override fun schedule(frame: () -> Unit): AutoCloseable {
        val handle = wasmScheduleFrame(browsingWindow, frame)
        return AutoCloseable { handle.cancel() }
    }
}

internal actual fun webGamepadDom(): WebGamepadDom = WasmWebGamepadDom()

internal actual fun webFrameScheduler(): WebFrameScheduler = WasmWebFrameScheduler()
