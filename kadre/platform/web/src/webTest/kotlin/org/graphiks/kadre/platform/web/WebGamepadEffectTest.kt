package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.microseconds

/**
 * The browser's haptic actuator is the one effect primitive the web has, and it speaks only
 * dual-rumble: these tests pin what the seam may promise (`WebGamepadEffects.capabilities`), what a
 * launch maps onto (`WebGamepadEffects.startEffect`), and what the owner of one launch owes the
 * runtime (one reset, exactly once, whatever the order of stop, close and revocation). Every browser
 * moment is a [FakeActuator] whose verdicts the test stages, so a refusal is a staged fact, not an
 * engine's mood.
 */
class WebGamepadEffectTest {
    /** One actuator the test stages: what it declares, and what each browser call answers. */
    private class FakeActuator(
        override val effects: List<String>? = null,
        var launchOutcome: WebEffectLaunch = WebEffectLaunch.Accepted,
        var resetOutcome: WebEffectLaunch = WebEffectLaunch.Accepted,
    ) : WebDomHapticActuator {
        data class Launch(val type: String, val durationMs: Int, val strongMagnitude: Double, val weakMagnitude: Double)

        val launches = mutableListOf<Launch>()
        var resets: Int = 0
            private set

        override fun playEffect(type: String, durationMs: Int, strongMagnitude: Double, weakMagnitude: Double): WebEffectLaunch {
            launches += Launch(type, durationMs, strongMagnitude, weakMagnitude)
            return launchOutcome
        }

        override fun reset(): WebEffectLaunch {
            resets += 1
            return resetOutcome
        }
    }

    private class FakeDomGamepad(
        override val index: Int = 0,
        override val hapticActuator: WebDomHapticActuator? = null,
    ) : WebDomGamepad {
        override val domId: String = "pad-$index"
        override val connected: Boolean = true
        override val mapping: String? = "standard"
        override val buttonValues: List<Double> = List(17) { 0.0 }
        override val axisValues: List<Double> = List(4) { 0.0 }
    }

    /** The staged poll: whatever pads the test staged, holes included. */
    private class FakeGamepadDom(
        var pads: List<WebDomGamepad?> = emptyList(),
        override val secureContext: Boolean = true,
    ) : WebGamepadDom {
        override fun getGamepads(): List<WebDomGamepad?> = pads
        override fun onGamepadAppeared(listener: () -> Unit): AutoCloseable = AutoCloseable { }
        override fun onGamepadDisappeared(listener: () -> Unit): AutoCloseable = AutoCloseable { }
        override fun close() { }
    }

    /** A hub whose frames never fire: test j needs one connection poll and no cadence. */
    private class UnanimatedFrames : WebFrameScheduler {
        override fun schedule(frame: () -> Unit): AutoCloseable = AutoCloseable { }
    }

    private fun startedOwner(actuator: FakeActuator): GamepadPortEffect = assertIs<KadreResult.Success<GamepadPortEffect>>(
        WebGamepadEffects.startEffect(0L, GamepadEffect.DualRumble(strong = 1.0, weak = 1.0, duration = 100.milliseconds), actuator),
    ).value

    @Test
    fun `dual-rumble actuator advertises dual rumble only`() {
        // An older Chromium declares nothing: one zero-duration probe answers for the actuator —
        // side-effect-free per spec, so the probe is the only launch the probing ever makes.
        val undeclared = FakeActuator(effects = null)
        val probed = WebGamepadEffects.capabilities(FakeDomGamepad(hapticActuator = undeclared), undeclared, secureContext = true)
        val probedSupported = assertIs<Capability.Supported<GamepadEffectConstraints>>(probed.effects)
        assertEquals(setOf(GamepadEffectKind.DualRumble), probedSupported.constraints.kinds)
        assertNull(probedSupported.constraints.localizedHaptics, "no browser primitive localizes a haptic, so none is promised")
        assertNull(probedSupported.constraints.maximumDuration, "the browser clamps durations; the constraint records no bound")
        assertEquals(FeatureAvailability.Available, probedSupported.availability)
        assertEquals(
            listOf(FakeActuator.Launch("dual-rumble", 0, 0.0, 0.0)),
            undeclared.launches,
            "exactly one zero-duration, zero-magnitude probe",
        )

        // A declared list is read, never probed:
        val declared = FakeActuator(effects = listOf("dual-rumble"))
        val read = WebGamepadEffects.capabilities(FakeDomGamepad(hapticActuator = declared), declared, secureContext = true)
        assertEquals(
            probedSupported.constraints,
            assertIs<Capability.Supported<GamepadEffectConstraints>>(read.effects).constraints,
            "the declared dual-rumble actuator advertises the same dual-rumble-only capability",
        )
        assertEquals(0, declared.launches.size, "a declared list answers without a launch")
    }

    @Test
    fun `trigger rumble joins kinds when reported and localized haptic never does`() {
        val actuator = FakeActuator(effects = listOf("dual-rumble", "trigger-rumble"))
        val capabilities = WebGamepadEffects.capabilities(FakeDomGamepad(hapticActuator = actuator), actuator, secureContext = true)
        val supported = assertIs<Capability.Supported<GamepadEffectConstraints>>(capabilities.effects)
        assertEquals(
            setOf(GamepadEffectKind.DualRumble, GamepadEffectKind.TriggerRumble),
            supported.constraints.kinds,
            "the browser's own word for trigger-rumble joins the kinds",
        )
        assertNull(supported.constraints.localizedHaptics, "LocalizedHaptic never joins: no browser primitive can play one")
    }

    @Test
    fun `missing actuator is an unsupported capability`() {
        val capabilities = WebGamepadEffects.capabilities(FakeDomGamepad(hapticActuator = null), actuator = null, secureContext = true)
        val unsupported = assertIs<Capability.Unsupported>(capabilities.effects)
        // The plan pins this refusal as PlatformFailure(Web, "gamepad-effect", "actuator-unavailable"),
        // but the foundation's Capability.Unsupported carries only KadreFailure.Unsupported — the form
        // Task 3's stub and the AppKit broker already state. The reason word lives on
        // WebGamepadEffects' vocabulary, not in the capability, until the plan or type moves.
        assertEquals(KadreFailure.Unsupported(KadreOperation.GamepadEffect), unsupported.failure)
    }

    @Test
    fun `insecure context refuses effects structurally`() {
        val actuator = FakeActuator(effects = listOf("dual-rumble"))
        val capabilities = WebGamepadEffects.capabilities(FakeDomGamepad(hapticActuator = actuator), actuator, secureContext = false)
        val unsupported = assertIs<Capability.Unsupported>(capabilities.effects)
        // Same plan-vs-foundation note as the missing actuator above: the pinned
        // PlatformFailure(Web, "gamepad-effect", "secure-context") cannot ride on this type.
        assertEquals(KadreFailure.Unsupported(KadreOperation.GamepadEffect), unsupported.failure)
        assertEquals(0, actuator.launches.size, "a structurally refused context is never probed")
    }

    @Test
    fun `dual rumble maps onto playEffect with the effect parameters`() {
        val actuator = FakeActuator(effects = listOf("dual-rumble"))
        val started = WebGamepadEffects.startEffect(
            0L,
            GamepadEffect.DualRumble(strong = 1.0, weak = 0.25, duration = 500.milliseconds),
            actuator,
        )
        assertIs<KadreResult.Success<GamepadPortEffect>>(started)
        assertEquals(
            listOf(FakeActuator.Launch("dual-rumble", 500, 1.0, 0.25)),
            actuator.launches,
            "type, duration and magnitudes pass through verbatim",
        )

        // A sub-millisecond duration still reaches the browser as at least one millisecond:
        WebGamepadEffects.startEffect(
            0L,
            GamepadEffect.DualRumble(strong = 1.0, weak = 1.0, duration = 500.microseconds),
            actuator,
        )
        assertEquals(1, actuator.launches.last().durationMs, "a sub-millisecond duration reads at least one millisecond")
    }

    @Test
    fun `unadvertised trigger rumble is refused as invalid request`() {
        val actuator = FakeActuator(effects = listOf("dual-rumble"))
        val refused = WebGamepadEffects.startEffect(
            0L,
            GamepadEffect.TriggerRumble(strong = 1.0, weak = 1.0, leftTrigger = 1.0, rightTrigger = 1.0, duration = 100.milliseconds),
            actuator,
        )
        assertEquals(KadreFailure.InvalidRequest("effect"), assertIs<KadreResult.Failure>(refused).reason)
        assertEquals(0, actuator.launches.size, "the defense precedes the launch: nothing reached the actuator")
    }

    @Test
    fun `localized haptic is refused always`() {
        val actuator = FakeActuator(effects = listOf("dual-rumble", "trigger-rumble"))
        val localized = GamepadEffect.LocalizedHaptic(GamepadHapticLocality.LeftHandle, intensity = 1.0, duration = 100.milliseconds)
        val withActuator = WebGamepadEffects.startEffect(0L, localized, actuator)
        assertEquals(KadreFailure.InvalidRequest("effect"), assertIs<KadreResult.Failure>(withActuator).reason)
        val withoutActuator = WebGamepadEffects.startEffect(0L, localized, actuator = null)
        assertEquals(KadreFailure.InvalidRequest("effect"), assertIs<KadreResult.Failure>(withoutActuator).reason)
        assertEquals(0, actuator.launches.size, "a localized haptic never reaches the actuator, whatever it advertises")
    }

    @Test
    fun `browser refusal is one honest platform failure`() {
        val actuator = FakeActuator(effects = listOf("dual-rumble"), launchOutcome = WebEffectLaunch.Refused("NotSupportedError"))
        val refused = WebGamepadEffects.startEffect(
            0L,
            GamepadEffect.DualRumble(strong = 1.0, weak = 1.0, duration = 100.milliseconds),
            actuator,
        )
        val failure = assertIs<KadreResult.Failure>(refused)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.Web, "gamepad-effect", "refused"),
            failure.reason,
            "one honest code: the browser's own refusal word is not propagated",
        )
        assertEquals(1, actuator.launches.size, "the refused call did reach the browser — the refusal is the browser's verdict")
    }

    @Test
    fun `effect owner stops and closes idempotently`() {
        val actuator = FakeActuator(effects = listOf("dual-rumble"))
        val owner = startedOwner(actuator)

        assertEquals(KadreResult.Success(Unit), owner.requestStop())
        assertEquals(1, actuator.resets, "requestStop reset exactly once")

        owner.close()
        owner.close()
        assertEquals(1, actuator.resets, "close after the stop resets nothing more")

        // A close on an owner still playing stops it once, and stays idempotent:
        val second = startedOwner(actuator)
        second.close()
        second.close()
        assertEquals(2, actuator.resets, "the close on a running effect reset exactly once more")
        assertEquals(KadreResult.Success(Unit), second.requestStop(), "a stop after the close is a quiet success")
    }

    @Test
    fun `stop after port close terminates exactly once`() {
        val actuator = FakeActuator(effects = listOf("dual-rumble"))
        val dom = FakeGamepadDom(pads = listOf(FakeDomGamepad(index = 0, hapticActuator = actuator)))
        val port = WebGamepadHub(dom, UnanimatedFrames()).openPort()
        assertEquals(listOf(0L), port.gamepads.map { it.key }, "the pad connected in the first poll")

        val owner = assertIs<KadreResult.Success<GamepadPortEffect>>(
            port.startEffect(0L, GamepadEffect.DualRumble(strong = 1.0, weak = 1.0, duration = 500.milliseconds)),
        ).value
        assertEquals(1, actuator.launches.size, "the effect launched through the port onto the pad's actuator")

        // The runtime terminating the session closes the port while the effect runs: the hub revokes
        // the owner — one reset — and every later stop or close of the revoked owner is a quiet
        // no-op. Exactly once, whatever the runtime asks afterwards.
        port.close()
        assertEquals(1, actuator.resets, "the revocation reset exactly once")
        assertEquals(KadreResult.Success(Unit), owner.requestStop(), "a stop after the revocation is a no-op success")
        owner.close()
        assertEquals(1, actuator.resets, "exactly once, whatever the order of revocation, stop and close")
    }
}
