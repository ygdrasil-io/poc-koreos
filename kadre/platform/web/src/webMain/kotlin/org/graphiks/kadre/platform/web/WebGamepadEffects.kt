package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.message
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import kotlin.time.Duration

/**
 * The web's gamepad effect rules, in the role of the AppKit broker's capability construction: what
 * one pad's haptic actuator may promise, and what starting one effect maps onto.
 *
 * **The primitive the browser ships is dual-rumble.** The browser's `vibrationActuator` accepts
 * `playEffect("dual-rumble", ...)` (Chrome; on newer Chromium the `GamepadHapticActuator` itself);
 * `LocalizedHaptic` has no browser primitive at all and is never advertised, never launched —
 * `startEffect` refuses it before any actuator is consulted. A browser that declares
 * `trigger-rumble` in its own `effects` list gets that kind advertised and launched as itself, its
 * trigger magnitudes riding the seam into the effectParameters dictionary — an advertised kind is
 * never launched as a lesser one. What the
 * capability advertises is the browser's own word: the actuator's declared `effects` list when the
 * browser declares one, and otherwise one zero-duration, zero-magnitude probe (`playEffect` with
 * duration 0 is side-effect-free per spec) — a probe result frozen at connection, exactly like the
 * descriptor. `maximumDuration` stays `null`: the browser clamps, and the constraint records no
 * bound it did not measure.
 *
 * **The synchronous verdict is the whole contract.** `playEffect`/`reset` hand back a promise; the
 * seam returns what the call itself did (accepted, or refused before any promise existed). A promise
 * that later rejects has no honest synchronous outcome — it is reported on
 * [WebGamepadEffectReporting]'s reporter and nothing pretends the effect stopped or failed here.
 * A `Refused` launch is the one honest platform failure (`refused`), the phase-4 rule: the browser's
 * own exception name is its business, not a Kadre failure code. That honesty is the launch's alone,
 * and the asymmetry with the stop is deliberate — an owner's `requestStop` propagates the browser's
 * own code, because the stop's verdict travels into the effect session's terminal state, which has
 * no launch constant to preserve.
 */
internal object WebGamepadEffects {
    private const val EFFECT_DOMAIN = "gamepad-effect"
    private const val DUAL_RUMBLE = "dual-rumble"
    private const val TRIGGER_RUMBLE = "trigger-rumble"

    /**
     * Probes once per connection; result frozen into the pad's GamepadCapabilities.
     *
     * The preconditions are structural and ordered: an insecure context refuses every effect before
     * anything is probed (reason `secure-context`), a missing actuator refuses everything after that
     * (`actuator-unavailable`), and only a secure context with a live actuator is asked what it can
     * play. An actuator that answers nothing this model can express (no declared kind, or a probe it
     * refuses) is the same `actuator-unavailable` verdict — the primitive is unavailable, whatever
     * object the browser handed over.
     *
     * The refusal rides on `KadreFailure.Unsupported(GamepadEffect)` — the one form
     * `Capability.Unsupported` carries, as the Task 3 stub and the AppKit broker already state. The
     * per-branch reason words above are this object's own vocabulary (the plan pins them as
     * `PlatformFailure` codes, which the foundation's `Capability.Unsupported` cannot hold); they
     * stay observable where the type system allows — on the launch and stop failures below.
     */
    fun capabilities(pad: WebDomGamepad, actuator: WebDomHapticActuator?, secureContext: Boolean): GamepadCapabilities {
        if (!secureContext) {
            return GamepadCapabilities(Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect)))
        }
        if (actuator == null) {
            return GamepadCapabilities(Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect)))
        }
        val kinds = advertisedKinds(actuator)
        if (kinds.isEmpty()) {
            return GamepadCapabilities(Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect)))
        }
        return GamepadCapabilities(
            Capability.Supported(
                GamepadEffectConstraints(kinds = kinds, localizedHaptics = null, maximumDuration = null),
                FeatureAvailability.Available,
            ),
        )
    }

    /**
     * Starts one already-admitted effect for the pad [key] names, onto [actuator] — the actuator of
     * the pad the hub resolved from [key]; the key itself is the runtime's bookkeeping, the launch
     * rides on the actuator.
     *
     * Admission (the runtime's, against the frozen capability) has already filtered unadvertised
     * kinds; the port still defends: a `TriggerRumble` the actuator never declared and a
     * `LocalizedHaptic` — always — are refused as `InvalidRequest("effect")` before anything reaches
     * the browser, and an actuator that refuses the launch fails with the one honest code. When the
     * browser declares no `effects` list, dual-rumble is the kind the connection-time probe admitted;
     * a pad whose probe refused advertises nothing and admission refuses every effect before the port
     * sees one.
     */
    fun startEffect(key: Long, effect: GamepadEffect, actuator: WebDomHapticActuator?): KadreResult<GamepadPortEffect> {
        if (actuator == null) return KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
        return when (effect) {
            is GamepadEffect.LocalizedHaptic -> KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
            is GamepadEffect.TriggerRumble ->
                if (TRIGGER_RUMBLE in actuator.effects.orEmpty()) {
                    launch(
                        actuator,
                        TRIGGER_RUMBLE,
                        effect.duration,
                        effect.strong,
                        effect.weak,
                        leftTriggerMagnitude = effect.leftTrigger,
                        rightTriggerMagnitude = effect.rightTrigger,
                    )
                } else {
                    KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
                }

            is GamepadEffect.DualRumble -> {
                val declared = actuator.effects
                if (declared == null || DUAL_RUMBLE in declared) {
                    launch(
                        actuator,
                        DUAL_RUMBLE,
                        effect.duration,
                        effect.strong,
                        effect.weak,
                        leftTriggerMagnitude = null,
                        rightTriggerMagnitude = null,
                    )
                } else {
                    KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
                }
            }
        }
    }

    /** The kinds the actuator's own word advertises; the undeclared case is answered by one probe. */
    private fun advertisedKinds(actuator: WebDomHapticActuator): Set<GamepadEffectKind> {
        val declared = actuator.effects ?: return if (probeAccepts(actuator)) setOf(GamepadEffectKind.DualRumble) else emptySet()
        return buildSet {
            if (DUAL_RUMBLE in declared) add(GamepadEffectKind.DualRumble)
            if (TRIGGER_RUMBLE in declared) add(GamepadEffectKind.TriggerRumble)
        }
    }

    /** The side-effect-free probe: a zero-duration, zero-magnitude dual-rumble the browser only accepts or refuses. */
    private fun probeAccepts(actuator: WebDomHapticActuator): Boolean =
        actuator.playEffect(
            DUAL_RUMBLE,
            durationMs = 0,
            strongMagnitude = 0.0,
            weakMagnitude = 0.0,
            leftTriggerMagnitude = null,
            rightTriggerMagnitude = null,
        ) is WebEffectLaunch.Accepted

    private fun launch(
        actuator: WebDomHapticActuator,
        type: String,
        duration: Duration,
        strongMagnitude: Double,
        weakMagnitude: Double,
        leftTriggerMagnitude: Double?,
        rightTriggerMagnitude: Double?,
    ): KadreResult<GamepadPortEffect> = when (
        val outcome = actuator.playEffect(
            type,
            durationMs(duration),
            strongMagnitude,
            weakMagnitude,
            leftTriggerMagnitude,
            rightTriggerMagnitude,
        )
    ) {
        is WebEffectLaunch.Accepted -> KadreResult.Success(WebGamepadEffect(actuator))
        is WebEffectLaunch.Refused -> KadreResult.Failure(platformFailure("refused"))
    }

    /** The duration as the browser reads it: whole milliseconds, at least one, and no more than an Int carries. */
    private fun durationMs(duration: Duration): Int =
        duration.inWholeMilliseconds.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    private fun platformFailure(code: String): KadreFailure.PlatformFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Web, EFFECT_DOMAIN, code)
}

/**
 * Where an asynchronous effect promise rejection lands.
 *
 * The seam's realizations attach a `catch` to every promise the browser hands back; the rejection
 * arrives when no synchronous verdict can exist any more, so it is reported and nothing else — no
 * state changes, no failure mapped onto an outcome that was already answered. The holder is
 * page-global because the effect path is (one page, one broker, one set of actuators); the session
 * wiring installs the runtime's own reporter at attach, and the last wiring owns the page's reports —
 * a recorded limit, the price of a rejection that lands in the target realization that made the call.
 *
 * Lifecycle, stated plainly: the initial reporter is a no-op — until the wiring sets one, reports go
 * nowhere (an effect cannot start before a session attaches, so nothing is lost in practice) — and
 * the holder is never unset on session close; closing a session leaves the last wired reporter in
 * place, because the holder is the page's, not the session's.
 */
internal object WebGamepadEffectReporting {
    var reporter: RuntimeFailureReporter = RuntimeFailureReporter { }
}

/**
 * One launched effect's owner — the web form of the AppKit broker's native effect owner.
 *
 * The launch already happened (the actuator answered `Accepted`); what the owner owes the runtime is
 * one stop, exactly once: [requestStop] resets the actuator and reports the browser's verdict, and
 * every call after the first — stop, close, or the hub's revocation — is a quiet no-op that touches
 * the actuator no more. A refused reset is the browser's own reason word as the failure code; a reset
 * whose promise later rejects was already reported at the rejection, and the stop it asked for stays
 * requested — no honest synchronous outcome exists to add.
 *
 * Recorded limit: the browser stops an effect by itself when its duration elapses or the pad
 * disconnects, so a stop that arrives late is a reset of an already-quiet actuator — harmless by
 * spec, and reported nowhere.
 */
internal class WebGamepadEffect(private val actuator: WebDomHapticActuator) : GamepadPortEffect {
    private var stopped: Boolean = false

    override fun requestStop(): KadreResult<Unit> {
        if (stopped) return KadreResult.Success(Unit)
        stopped = true
        return when (val outcome = actuator.reset()) {
            is WebEffectLaunch.Accepted -> KadreResult.Success(Unit)
            is WebEffectLaunch.Refused -> KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, EFFECT_DOMAIN, outcome.code),
            )
        }
    }

    /** Stops the effect once; the second close, like every later one, resets nothing. */
    override fun close() {
        requestStop()
    }

    /**
     * The hub's revocation — the port closed (the runtime is terminating the session) while the
     * effect ran. The same one reset as [requestStop], with the verdict nobody reads: a refusal is
     * reported, not returned, because the revocation's caller is teardown.
     */
    internal fun revoke() {
        when (val outcome = requestStop()) {
            is KadreResult.Success -> Unit
            is KadreResult.Failure -> WebGamepadEffectReporting.reporter.report(
                RuntimeException("revoked gamepad effect reset was refused: ${outcome.reason.message}"),
            )
        }
    }

    private companion object {
        const val EFFECT_DOMAIN = "gamepad-effect"
    }
}
