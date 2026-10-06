package org.graphiks.kadre.platform.web

import kotlinx.browser.window
import org.w3c.dom.Window
import org.w3c.dom.events.Event
import kotlin.js.Promise

/**
 * The browser `Gamepad` as this realization reads it, which no Kotlin/JS DOM SDK binding declares —
 * the gap this file declares itself, the way `ResizeObserver` was declared for the surface. The
 * members are exactly the ones the structural seam copies, so a member the model does not carry
 * cannot be read by accident. `mapping` is nullable because a browser may omit it; the buttons
 * array is dense (the DOM always answers with one button object per control) while the pads array
 * of [JsGamepadNavigator.getGamepads] is not — its entries are nullable, and the nulls are the
 * holes Chromium leaves at the indices of absent gamepads. The haptic actuator is *not* declared
 * here on purpose: it is absent on Firefox/WebKit, and a declared member would be read unguarded —
 * the extraction below checks the browser's own members instead.
 */
private external interface JsGamepad {
    val index: Int
    val id: String
    val connected: Boolean
    val mapping: String?
    val buttons: Array<JsGamepadButton>
    val axes: Array<Double>
}

private external interface JsGamepadButton {
    val value: Double
}

/**
 * The gamepad members of the navigator: the poll, and — declared apart, below — the event
 * registration the DOM delivers connection announcements through. The registration members are not
 * declared here on purpose: the engines disagree about whether the navigator is an event target at
 * all (Chromium's is not — probed 2026-10-05, `navigator.addEventListener` is `undefined` there
 * while `getGamepads` is a function), and a member declared on this interface would be *called*
 * detached from its receiver, which strict-mode EventTargets refuse. The registration goes through
 * the JavaScript helpers below, which check the member where it lives and call it as a member.
 */
private external interface JsGamepadNavigator {
    fun getGamepads(): Array<JsGamepad?>?
}

/**
 * Registers [listener] for [type] on the navigator, where the navigator accepts listeners at all.
 * Engines that fire the connection events at their navigator implement EventTarget for it; the
 * check is the browser's own fact, not an error, and a navigator without the member is simply not
 * listened to — the announcement is heard on the window. The call stays a member call inside the
 * snippet, bound to the navigator: an extracted function value would lose its receiver and the
 * strict-mode target would refuse it.
 */
private fun jsAddNavigatorGamepadListener(navigator: JsGamepadNavigator, type: String, listener: (Event) -> Unit) {
    js(
        """(function () {
             if (typeof navigator.addEventListener === 'function') { navigator.addEventListener(type, listener); }
           }())""",
    )
}

/** Withdraws what [jsAddNavigatorGamepadListener] registered, with the same tolerance. */
private fun jsRemoveNavigatorGamepadListener(navigator: JsGamepadNavigator, type: String, listener: (Event) -> Unit) {
    js(
        """(function () {
             if (typeof navigator.removeEventListener === 'function') { navigator.removeEventListener(type, listener); }
           }())""",
    )
}

/** One poll's copy of one browser pad; the browser object stays behind the seam. */
private class JsDomGamepad(pad: JsGamepad) : WebDomGamepad {
    override val index: Int = pad.index
    override val domId: String = pad.id
    override val connected: Boolean = pad.connected
    override val mapping: String? = pad.mapping
    override val buttonValues: List<Double> = pad.buttons.map { it.value }
    override val axisValues: List<Double> = pad.axes.map { it }
    override val hapticActuator: WebDomHapticActuator? = jsVibrationActuator(pad)?.let(::JsDomHapticActuator)
}

/**
 * The browser `GamepadHapticActuator` as this realization reads it: exactly the members the seam
 * copies, declared over the promise form `playEffect(type, effectParameters)` answers with. The
 * promise's resolved value (a `GamepadHapticsResult` on browsers that provide one) is never read —
 * the synchronous outcome is the seam's whole contract, and a rejection is reported, not mapped.
 */
private external interface JsGamepadHapticActuator {
    fun playEffect(type: String, effectParameters: JsGamepadEffectParameters): Promise<Unit>
    fun reset(): Promise<Unit>
}

/**
 * The `effectParameters` dictionary the browser's own `playEffect` defines (`duration`,
 * `strongMagnitude`, `weakMagnitude`, and the trigger-rumble members the dictionary carries when the
 * browser does). The realization builds it; the browser reads it; nothing is read back.
 */
private external interface JsGamepadEffectParameters

/**
 * Builds the effectParameters dictionary. An omitted trigger magnitude is literally absent — the
 * spread adds the member only where the model stated it, which WebIDL reads as "not present" — never
 * a zero that would masquerade as a stated magnitude of none.
 */
private fun jsEffectParameters(
    durationMs: Int,
    strongMagnitude: Double,
    weakMagnitude: Double,
    leftTriggerMagnitude: Double?,
    rightTriggerMagnitude: Double?,
): JsGamepadEffectParameters =
    js(
        """({
             duration: durationMs,
             strongMagnitude: strongMagnitude,
             weakMagnitude: weakMagnitude,
             ...(leftTriggerMagnitude == null ? { } : { leftTriggerMagnitude: leftTriggerMagnitude }),
             ...(rightTriggerMagnitude == null ? { } : { rightTriggerMagnitude: rightTriggerMagnitude })
           })""",
    )

/**
 * Extracts the pad's haptic actuator, only where the browser's own members exist. Every access is
 * guarded because the actuator is optional (`vibrationActuator` is absent on Firefox/WebKit) and on
 * newer Chromium the `GamepadHapticActuator` itself fills the role — either way, the browser object
 * that answers is the one `playEffect` can be called on; anything else is `null`, and a null
 * actuator is an honest Unsupported capability, not a guessed one.
 */
private fun jsVibrationActuator(pad: JsGamepad): JsGamepadHapticActuator? =
    if (js(
            "pad.vibrationActuator !== undefined && pad.vibrationActuator !== null && " +
                "typeof pad.vibrationActuator.playEffect === 'function'",
        ).unsafeCast<Boolean>()
    ) {
        js("pad.vibrationActuator").unsafeCast<JsGamepadHapticActuator>()
    } else {
        null
    }

/**
 * Reads the actuator's own declaration of what it accepts (`GamepadHapticActuator.effects` on newer
 * Chromium), only where it is an array; anything else — absent, a non-array, an older Chromium — is
 * "undeclared", and the caller probes once instead of guessing.
 */
private fun jsDeclaredEffectTypes(actuator: JsGamepadHapticActuator): List<String>? =
    if (js("Array.isArray(actuator.effects)").unsafeCast<Boolean>()) {
        js("actuator.effects").unsafeCast<Array<String>>().toList()
    } else {
        null
    }

/** Whether the browsing context is a secure context — the browser's own word, guarded like every member. */
private fun jsIsSecureContext(context: Window): Boolean =
    js("context.isSecureContext === true").unsafeCast<Boolean>()

/** The browser's own name for a synchronous refusal, as far as one is offered; `refused` otherwise. */
private fun jsRefusalCode(failure: Throwable): String {
    val candidate = failure.asDynamic().name as? String ?: return "refused"
    return if (candidate.isNotEmpty() && candidate.all { it.code in 0x21..0x7e }) candidate else "refused"
}

/**
 * One pad's haptic actuator as the browser answers it.
 *
 * `playEffect` and `reset` hand back a promise; the seam's verdict is the synchronous outcome only —
 * the call reached the browser (`Accepted`) or it was refused before any promise existed
 * (`Refused`), e.g. the member vanished or the engine rejected the call outright. Every promise gets
 * a `catch` that reports the rejection on the wiring's reporter: a promise that rejects later has no
 * honest synchronous outcome left, so it becomes a report and nothing pretends otherwise.
 */
private class JsDomHapticActuator(private val actuator: JsGamepadHapticActuator) : WebDomHapticActuator {
    override val effects: List<String>? = jsDeclaredEffectTypes(actuator)

    override fun playEffect(
        type: String,
        durationMs: Int,
        strongMagnitude: Double,
        weakMagnitude: Double,
        leftTriggerMagnitude: Double?,
        rightTriggerMagnitude: Double?,
    ): WebEffectLaunch {
        val effectParameters = jsEffectParameters(durationMs, strongMagnitude, weakMagnitude, leftTriggerMagnitude, rightTriggerMagnitude)
        return launch { target -> target.playEffect(type, effectParameters) }
    }

    override fun reset(): WebEffectLaunch = launch { target -> target.reset() }

    private fun launch(call: (JsGamepadHapticActuator) -> Promise<Unit>): WebEffectLaunch =
        try {
            call(actuator).catch { reason -> WebGamepadEffectReporting.reporter.report(reason) }
            WebEffectLaunch.Accepted
        } catch (failure: Throwable) {
            WebEffectLaunch.Refused(jsRefusalCode(failure))
        }
}

/**
 * The Gamepad API seam of the browsing context, read through the navigator of [browsingWindow].
 *
 * A poll that cannot reach the API — the browsing context reports none before a user interacts with
 * the page (Chrome answers `null`), or the seam is closed — observes nothing rather than guessing.
 * The two connection listeners are registered on **both** the window and the navigator, each where
 * its target accepts listeners: the spec (and every engine) fires them at the window, and engines
 * that also fire them at their navigator implement EventTarget there while Chromium's navigator
 * does not. The hub treats the announcement as "poll again" only, so a browser that fires both
 * delivers one redundant poll and nothing else. `close` withdraws every registration handed out.
 */
internal class JsWebGamepadDom(private val browsingWindow: Window = window) : WebGamepadDom {
    private val navigator: JsGamepadNavigator = browsingWindow.navigator.unsafeCast<JsGamepadNavigator>()
    private val registrations = mutableListOf<AutoCloseable>()
    private var closed: Boolean = false

    override val secureContext: Boolean
        get() = jsIsSecureContext(browsingWindow)

    override fun getGamepads(): List<WebDomGamepad?> {
        if (closed) return emptyList()
        val pads = navigator.getGamepads() ?: return emptyList()
        // Positional, holes and all: entry i is the pad whose DOM index is i.
        return List(pads.size) { index -> pads[index]?.let(::JsDomGamepad) }
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
        val navigatorListener: (Event) -> Unit = { listener() }
        browsingWindow.addEventListener(type, windowListener)
        jsAddNavigatorGamepadListener(navigator, type, navigatorListener)
        var withdrawn = false
        val registration = AutoCloseable {
            if (!withdrawn) {
                withdrawn = true
                browsingWindow.removeEventListener(type, windowListener)
                jsRemoveNavigatorGamepadListener(navigator, type, navigatorListener)
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
internal class JsWebFrameScheduler(private val browsingWindow: Window = window) : WebFrameScheduler {
    override fun schedule(frame: () -> Unit): AutoCloseable {
        val handle = jsScheduleFrame(browsingWindow, frame)
        return AutoCloseable { handle.cancel() }
    }
}

internal actual fun webGamepadDom(): WebGamepadDom = JsWebGamepadDom()

internal actual fun webFrameScheduler(): WebFrameScheduler = JsWebFrameScheduler()
