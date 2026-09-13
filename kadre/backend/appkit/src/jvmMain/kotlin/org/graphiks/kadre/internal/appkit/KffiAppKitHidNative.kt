package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kffi.objc.managed.HidDevice as KffiHidDevice
import org.graphiks.kffi.objc.managed.HidDeviceKind as KffiHidDeviceKind
import org.graphiks.kffi.objc.managed.HidDeviceLifecycleEvent
import org.graphiks.kffi.objc.managed.HidManager

/** Production pointer-free adapter from KFFI's managed HID monitor to the AppKit broker. */
internal object KffiAppKitHidNativeFactory : AppKitHidNativeFactory {
    override fun open(listener: (AppKitHidNativeEvent) -> Unit): AppKitHidNative =
        KffiAppKitHidNative(listener)
}

private class KffiAppKitHidNative(
    private val listener: (AppKitHidNativeEvent) -> Unit,
) : AppKitHidNative {
    private val lock = Any()
    private val devicesByKey = linkedMapOf<Long, ManagedDevice>()
    private var nextKey = 1L
    private var closed = false
    private val manager = HidManager.create(::acceptLifecycle)

    init {
        synchronized(lock) {
            manager.devices.forEach(::registerLocked)
        }
    }

    override val devices: List<AppKitHidNativeDevice>
        get() = synchronized(lock) { devicesByKey.values.map(ManagedDevice::device) }

    override fun close() {
        val release = synchronized(lock) {
            if (closed) return
            closed = true
            devicesByKey.clear()
            manager
        }
        release.close()
    }

    private fun acceptLifecycle(event: HidDeviceLifecycleEvent) {
        val mapped = synchronized(lock) {
            if (closed) return
            when (event) {
                is HidDeviceLifecycleEvent.Connected -> registerLocked(event.device)
                    ?.let { entry -> AppKitHidNativeEvent.Connected(entry.device) }

                is HidDeviceLifecycleEvent.Disconnected -> devicesByKey.entries
                    .firstOrNull { (_, entry) -> entry.kffi.id == event.id }
                    ?.let { (key, _) ->
                        devicesByKey.remove(key)
                        AppKitHidNativeEvent.Disconnected(key)
                    }
            }
        }
        mapped?.let(listener)
    }

    private fun registerLocked(device: KffiHidDevice): ManagedDevice? {
        if (devicesByKey.values.any { entry -> entry.kffi.id == device.id }) return null
        val key = nextKey++
        check(key > 0L) { "HID key space exhausted" }
        return ManagedDevice(
            kffi = device,
            device = AppKitHidNativeDevice(
                key = key,
                descriptor = InputDeviceDescriptor(
                    name = device.descriptor.name,
                    kind = device.descriptor.kind.toKadreInputDeviceKind(),
                ),
            ),
        ).also { entry -> devicesByKey[key] = entry }
    }

    private data class ManagedDevice(
        val kffi: KffiHidDevice,
        val device: AppKitHidNativeDevice,
    )
}

private fun KffiHidDeviceKind.toKadreInputDeviceKind(): InputDeviceKind = when (this) {
    KffiHidDeviceKind.Keyboard -> InputDeviceKind.Keyboard
    KffiHidDeviceKind.Mouse -> InputDeviceKind.Mouse
    KffiHidDeviceKind.Touchscreen -> InputDeviceKind.Touchscreen
    KffiHidDeviceKind.Touchpad -> InputDeviceKind.Touchpad
    KffiHidDeviceKind.Pen -> InputDeviceKind.Pen
    KffiHidDeviceKind.Other -> InputDeviceKind.Other
}
