package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.policy.DeviceEffectOwnership
import org.graphiks.kadre.policy.GamepadRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class AppKitGameControllerBrokerTest {
    @Test
    fun lifecyclePublishedWhileTheNativeMonitorOpensIsIncludedInTheInitialProjection() {
        val connected = controller(
            key = 1L,
            inputs = listOf(AppKitGameControllerPhysicalInput.Button(setOf("Button A"), 1.0, true)),
        )
        val native = RecordingGameControllerNative(controllers = emptyList())
        val broker = AppKitGameControllerBroker(
            AppKitGameControllerNativeFactory { listener ->
                listener(AppKitGameControllerNativeEvent.Connected(connected))
                native.open(listener)
            },
        )

        val port = broker.openPort()

        assertEquals(
            listOf(GamepadButtonValue(GamepadButton.South, 1.0, true)),
            port.gamepads.single().state.buttons,
        )
    }

    @Test
    fun activeSessionRoutingPreservesPhysicalLifecycleAndResumesWithTheCurrentSnapshot() {
        val native = RecordingGameControllerNative(
            controllers = listOf(
                controller(
                    key = 1L,
                    inputs = listOf(AppKitGameControllerPhysicalInput.Button(setOf("Button A"), 0.0, false)),
                ),
            ),
        )
        val broker = AppKitGameControllerBroker(AppKitGameControllerNativeFactory(native::open))
        val first = broker.openPort()
        val second = broker.openPort()
        val firstEvents = mutableListOf<GamepadPortEvent>()
        val secondEvents = mutableListOf<GamepadPortEvent>()
        first.installObserver(firstEvents::add)
        second.installObserver(secondEvents::add)
        first.updateRouting(activeSessionRouting(foregroundActive = true))
        second.updateRouting(activeSessionRouting(foregroundActive = true))

        val firstGamepad = first.gamepads.single()
        val secondGamepad = second.gamepads.single()
        assertEquals(GamepadMapping.Standard, firstGamepad.descriptor.mapping)
        assertEquals(GamepadRoutingState.Routed, firstGamepad.routing)
        assertEquals(GamepadRoutingState.Suspended, secondGamepad.routing)
        firstEvents.clear()
        secondEvents.clear()

        native.emitInput(
            key = 1L,
            input = AppKitGameControllerPhysicalInput.Button(setOf("Button A"), 1.0, true),
        )

        assertIs<GamepadPortEvent.StateChanged>(firstEvents.single())
        assertEquals(emptyList(), secondEvents)
        firstEvents.clear()

        first.updateRouting(activeSessionRouting(foregroundActive = false))

        assertEquals(
            GamepadRoutingState.Suspended,
            assertIs<GamepadPortEvent.RoutingChanged>(firstEvents.single()).routing,
        )
        val resumed = assertIs<GamepadPortEvent.RoutingChanged>(secondEvents.single())
        assertEquals(GamepadRoutingState.Routed, resumed.routing)
        assertEquals(
            listOf(GamepadButtonValue(GamepadButton.South, 1.0, true)),
            resumed.state.buttons,
        )
        firstEvents.clear()
        secondEvents.clear()

        native.disconnect(1L)

        assertIs<GamepadPortEvent.Disconnected>(firstEvents.single())
        assertIs<GamepadPortEvent.Disconnected>(secondEvents.single())
    }

    @Test
    fun hapticsAreExclusivePerPhysicalControllerAndStopOnDisconnect() {
        val native = RecordingGameControllerNative(
            controllers = listOf(
                controller(
                    key = 1L,
                    inputs = listOf(AppKitGameControllerPhysicalInput.Button(setOf("Button A"), 0.0, false)),
                ),
            ),
        )
        val broker = AppKitGameControllerBroker(AppKitGameControllerNativeFactory(native::open))
        val first = broker.openPort()
        val second = broker.openPort()
        first.updateRouting(activeSessionRouting(foregroundActive = true))
        second.updateRouting(
            activeSessionRouting(
                foregroundActive = true,
                effectOwnership = DeviceEffectOwnership.SharedWhenSupported,
            ),
        )
        val effect = GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 1.seconds)

        val firstOwner = assertIs<GamepadPortEffect>(
            assertIs<KadreResult.Success<*>>(first.startEffect(1L, effect)).value,
        )
        val conflict = second.startEffect(1L, effect)

        assertEquals(
            KadreFailure.AlreadyInUse(KadreResourceKind.GamepadEffect),
            assertIs<KadreResult.Failure>(conflict).reason,
        )
        assertEquals(1, native.effects.size)

        firstOwner.close()

        val secondOwner = assertIs<GamepadPortEffect>(
            assertIs<KadreResult.Success<*>>(second.startEffect(1L, effect)).value,
        )
        native.disconnect(1L)

        assertEquals(1, native.effects[0].closeCount)
        assertEquals(1, native.effects[1].closeCount)
        secondOwner.close()
    }

    private fun activeSessionRouting(
        foregroundActive: Boolean,
        effectOwnership: DeviceEffectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
    ): GamepadPortRouting = GamepadPortRouting(
        policy = GamepadRouting.ActiveSessionOnly,
        foregroundActive = foregroundActive,
        effectOwnership = effectOwnership,
    )

    private fun controller(
        key: Long,
        inputs: List<AppKitGameControllerPhysicalInput>,
    ): AppKitGameControllerNativeController = AppKitGameControllerNativeController(
        key = key,
        name = "Controller $key",
        profile = AppKitGameControllerProfile.Standard,
        initialInputs = inputs,
        hapticLocalities = setOf(GamepadHapticLocality.Default),
    )
}

private class RecordingGameControllerNative(
    override val controllers: List<AppKitGameControllerNativeController>,
) : AppKitGameControllerNative {
    private lateinit var lifecycle: (AppKitGameControllerNativeEvent) -> Unit
    private val inputs = linkedMapOf<Long, (AppKitGameControllerPhysicalInput) -> Unit>()
    val effects = mutableListOf<RecordingGameControllerEffect>()

    fun open(listener: (AppKitGameControllerNativeEvent) -> Unit): AppKitGameControllerNative {
        lifecycle = listener
        return this
    }

    override fun observeInput(
        key: Long,
        listener: (AppKitGameControllerPhysicalInput) -> Unit,
    ): KadreResult<AutoCloseable> {
        check(inputs.put(key, listener) == null) { "input is already observed" }
        return KadreResult.Success(AutoCloseable { inputs.remove(key) })
    }

    override fun startEffect(
        key: Long,
        effect: GamepadEffect.LocalizedHaptic,
    ): KadreResult<AppKitGameControllerNativeEffect> = KadreResult.Success(
        RecordingGameControllerEffect().also(effects::add),
    )

    fun emitInput(key: Long, input: AppKitGameControllerPhysicalInput) {
        checkNotNull(inputs[key])(input)
    }

    fun disconnect(key: Long) {
        lifecycle(AppKitGameControllerNativeEvent.Disconnected(key))
    }

    override fun close() = Unit
}

private class RecordingGameControllerEffect : AppKitGameControllerNativeEffect {
    var closeCount = 0
        private set

    override fun requestStop(): KadreResult<Unit> = KadreResult.Success(Unit)

    override fun close() {
        closeCount += 1
    }
}
