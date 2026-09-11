@file:OptIn(org.graphiks.kffi.objc.PlatformAvailability::class)

package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kffi.objc.managed.GameControllerDescriptor
import org.graphiks.kffi.objc.managed.GameControllerDevice
import org.graphiks.kffi.objc.managed.GameControllerHapticLocality
import org.graphiks.kffi.objc.managed.GameControllerHaptics
import org.graphiks.kffi.objc.managed.GameControllerLifecycleEvent
import org.graphiks.kffi.objc.managed.GameControllerMonitor
import org.graphiks.kffi.objc.managed.GameControllerPhysicalInput
import org.graphiks.kffi.objc.managed.GameControllerProfile
import java.util.concurrent.atomic.AtomicBoolean

/** Production pointer-free adapter from KFFI's managed GameController monitor to the AppKit broker. */
internal object KffiAppKitGameControllerNativeFactory : AppKitGameControllerNativeFactory {
    override fun open(listener: (AppKitGameControllerNativeEvent) -> Unit): AppKitGameControllerNative =
        KffiAppKitGameControllerNative(listener)
}

private class KffiAppKitGameControllerNative(
    private val listener: (AppKitGameControllerNativeEvent) -> Unit,
) : AppKitGameControllerNative {
    private val lock = Any()
    private val controllersByKey = linkedMapOf<Long, ManagedController>()
    private var nextKey = 1L
    private var closed = false
    private val monitor = GameControllerMonitor.create(::acceptLifecycle)

    init {
        synchronized(lock) {
            monitor.controllers.forEach(::registerLocked)
        }
    }

    override val controllers: List<AppKitGameControllerNativeController>
        get() = synchronized(lock) { controllersByKey.values.map(ManagedController::controller) }

    override fun observeInput(
        key: Long,
        listener: (AppKitGameControllerPhysicalInput) -> Unit,
    ): KadreResult<AutoCloseable> {
        val entry = synchronized(lock) { controllersByKey[key] }
            ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        return monitor.observePhysicalInput(entry.device.id) { event ->
            listener(event.input.toAppKitGameControllerPhysicalInput())
        }.fold(
            onSuccess = { observation -> KadreResult.Success(observation) },
            onFailure = { KadreResult.Failure(gameControllerFailure("input-observation-unavailable")) },
        )
    }

    override fun startEffect(
        key: Long,
        effect: GamepadEffect.LocalizedHaptic,
    ): KadreResult<AppKitGameControllerNativeEffect> {
        val entry = synchronized(lock) { controllersByKey[key] }
            ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        val haptics = monitor.createHaptics(entry.device.id, effect.locality.toKffiHapticLocality())
            .getOrElse {
                return KadreResult.Failure(gameControllerFailure("haptics-create-failed"))
            }
        if (haptics.start().isFailure) {
            runCatching(haptics::close)
            return KadreResult.Failure(gameControllerFailure("haptics-start-failed"))
        }
        if (haptics.playContinuous(effect.intensity.toFloat(), effect.duration).isFailure) {
            runCatching(haptics::close)
            return KadreResult.Failure(gameControllerFailure("haptics-play-failed"))
        }
        return KadreResult.Success(KffiAppKitGameControllerNativeEffect(haptics))
    }

    override fun close() {
        val release = synchronized(lock) {
            if (closed) return
            closed = true
            controllersByKey.clear()
            monitor
        }
        release.close()
    }

    private fun acceptLifecycle(event: GameControllerLifecycleEvent) {
        val mapped = synchronized(lock) {
            if (closed) return
            when (event) {
                is GameControllerLifecycleEvent.Connected -> registerLocked(event.controller)
                    ?.let { entry -> AppKitGameControllerNativeEvent.Connected(entry.controller) }

                is GameControllerLifecycleEvent.Disconnected -> controllersByKey.entries
                    .firstOrNull { (_, entry) -> entry.device.id == event.id }
                    ?.let { (key, _) ->
                        controllersByKey.remove(key)
                        AppKitGameControllerNativeEvent.Disconnected(key)
                    }
            }
        }
        mapped?.let(listener)
    }

    private fun registerLocked(device: GameControllerDevice): ManagedController? {
        if (controllersByKey.values.any { entry -> entry.device.id == device.id }) return null
        val key = nextKey++
        check(key > 0L) { "GameController key space exhausted" }
        return ManagedController(
            device = device,
            controller = device.descriptor.toAppKitGameControllerNativeController(
                key = key,
                initialInputs = device.initialPhysicalInputs,
            ),
        ).also { entry -> controllersByKey[key] = entry }
    }

    private data class ManagedController(
        val device: GameControllerDevice,
        val controller: AppKitGameControllerNativeController,
    )
}

private class KffiAppKitGameControllerNativeEffect(
    private val haptics: GameControllerHaptics,
) : AppKitGameControllerNativeEffect {
    private val closed = AtomicBoolean()

    override fun requestStop(): KadreResult<Unit> = stop()

    override fun close() {
        stop()
    }

    private fun stop(): KadreResult<Unit> {
        if (!closed.compareAndSet(false, true)) return KadreResult.Success(Unit)
        return try {
            haptics.close()
            KadreResult.Success(Unit)
        } catch (_: Exception) {
            KadreResult.Failure(gameControllerFailure("haptics-stop-failed"))
        } catch (_: LinkageError) {
            KadreResult.Failure(gameControllerFailure("haptics-stop-failed"))
        }
    }
}

internal fun GameControllerDescriptor.toAppKitGameControllerNativeController(
    key: Long,
    initialInputs: List<GameControllerPhysicalInput>,
): AppKitGameControllerNativeController = AppKitGameControllerNativeController(
    key = key,
    name = vendorName ?: productCategory,
    profile = when (profile) {
        GameControllerProfile.Standard -> AppKitGameControllerProfile.Standard
        GameControllerProfile.Native -> AppKitGameControllerProfile.Native
    },
    initialInputs = initialInputs.map(GameControllerPhysicalInput::toAppKitGameControllerPhysicalInput),
    hapticLocalities = hapticLocalities.mapTo(linkedSetOf()) { locality -> locality.toKadreHapticLocality() },
)

private fun GameControllerPhysicalInput.toAppKitGameControllerPhysicalInput(): AppKitGameControllerPhysicalInput = when (this) {
    is GameControllerPhysicalInput.Button -> AppKitGameControllerPhysicalInput.Button(
        nativeNames = nativeNames.toSet(),
        value = value.toDouble(),
        pressed = pressed,
    )

    is GameControllerPhysicalInput.Axis -> AppKitGameControllerPhysicalInput.Axis(
        nativeNames = nativeNames.toSet(),
        value = value.toDouble(),
    )

    is GameControllerPhysicalInput.DirectionPad -> AppKitGameControllerPhysicalInput.DirectionPad(
        nativeNames = nativeNames.toSet(),
        x = x.toDouble(),
        y = y.toDouble(),
    )

    is GameControllerPhysicalInput.Other -> AppKitGameControllerPhysicalInput.Other(
        nativeNames = nativeNames.toSet(),
        analog = analog,
    )
}

private fun GameControllerHapticLocality.toKadreHapticLocality(): GamepadHapticLocality = when (this) {
    GameControllerHapticLocality.Default -> GamepadHapticLocality.Default
    GameControllerHapticLocality.All -> GamepadHapticLocality.All
    GameControllerHapticLocality.Handles -> GamepadHapticLocality.Handles
    GameControllerHapticLocality.LeftHandle -> GamepadHapticLocality.LeftHandle
    GameControllerHapticLocality.RightHandle -> GamepadHapticLocality.RightHandle
    GameControllerHapticLocality.Triggers -> GamepadHapticLocality.Triggers
    GameControllerHapticLocality.LeftTrigger -> GamepadHapticLocality.LeftTrigger
    GameControllerHapticLocality.RightTrigger -> GamepadHapticLocality.RightTrigger
}

private fun GamepadHapticLocality.toKffiHapticLocality(): GameControllerHapticLocality = when (this) {
    GamepadHapticLocality.Default -> GameControllerHapticLocality.Default
    GamepadHapticLocality.All -> GameControllerHapticLocality.All
    GamepadHapticLocality.Handles -> GameControllerHapticLocality.Handles
    GamepadHapticLocality.LeftHandle -> GameControllerHapticLocality.LeftHandle
    GamepadHapticLocality.RightHandle -> GameControllerHapticLocality.RightHandle
    GamepadHapticLocality.Triggers -> GameControllerHapticLocality.Triggers
    GamepadHapticLocality.LeftTrigger -> GameControllerHapticLocality.LeftTrigger
    GamepadHapticLocality.RightTrigger -> GameControllerHapticLocality.RightTrigger
}

private fun gameControllerFailure(code: String): KadreFailure.PlatformFailure = KadreFailure.PlatformFailure(
    platform = KadrePlatform.AppKit,
    domain = "game-controller",
    code = code,
)
