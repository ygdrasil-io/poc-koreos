package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.internal.runtime.InputDevicePort
import org.graphiks.kadre.internal.runtime.InputDevicePortDevice
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent

/** Opens one pointer-free HID bridge for the process-wide AppKit broker. */
internal fun interface AppKitHidNativeFactory {
    /** Lifecycle callbacks emitted while opening are folded into the initial port projection. */
    fun open(listener: (AppKitHidNativeEvent) -> Unit): AppKitHidNative
}

/** Pointer-free HID bridge used only by the AppKit process broker. */
internal interface AppKitHidNative : AutoCloseable {
    val devices: List<AppKitHidNativeDevice>

    override fun close()
}

/** Detached HID metadata entering the AppKit backend after KFFI has discarded native references. */
internal data class AppKitHidNativeDevice(
    val key: Long,
    val descriptor: InputDeviceDescriptor,
) {
    init {
        require(key >= 0L) { "HID device key must be non-negative" }
    }
}

internal sealed interface AppKitHidNativeEvent {
    data class Connected(val device: AppKitHidNativeDevice) : AppKitHidNativeEvent
    data class Disconnected(val key: Long) : AppKitHidNativeEvent {
        init {
            require(key >= 0L) { "HID device key must be non-negative" }
        }
    }
}

internal data class AppKitHidPortDelivery(
    val observer: (InputDevicePortEvent) -> Unit,
    val event: InputDevicePortEvent,
)

/**
 * Process-wide HID owner with session-owned [AppKitHidPort] projections.
 *
 * Lifecycle is fanned out to every open session. The native owner is released as soon as the
 * final port closes; no AppKit session retains a raw IOKit reference.
 */
internal class AppKitHidBroker(
    private val nativeFactory: AppKitHidNativeFactory,
) : AutoCloseable {
    private val lock = Any()
    private val ports = linkedSetOf<AppKitHidPort>()
    private val devices = linkedMapOf<Long, AppKitHidNativeDevice>()
    private val openingEvents = mutableListOf<AppKitHidNativeEvent>()
    private var native: AppKitHidNative? = null
    private var openingNative = false
    private var closed = false

    fun openPort(): AppKitHidPort {
        var release: AppKitHidNative? = null
        try {
            return synchronized(lock) {
                check(!closed) { "AppKit HID broker is closed" }
                AppKitHidPort(this).also { opened ->
                    check(ports.add(opened)) { "AppKit HID port is already registered" }
                    try {
                        ensureNativeLocked()
                        opened.installInitialLocked(devices.values.map(::sourceFor))
                    } catch (failure: Throwable) {
                        ports.remove(opened)
                        release = releaseNativeLocked()
                        throw failure
                    }
                }
            }
        } finally {
            release?.close()
        }
    }

    override fun close() {
        val release = synchronized(lock) {
            if (closed) return
            closed = true
            ports.forEach(AppKitHidPort::closeFromBrokerLocked)
            ports.clear()
            releaseNativeLocked()
        }
        release?.close()
    }

    internal fun devices(port: AppKitHidPort): List<InputDevicePortDevice> = synchronized(lock) {
        if (!port.isOpenLocked()) emptyList() else devices.values.map(::sourceFor)
    }

    internal fun installObserver(
        port: AppKitHidPort,
        observer: (InputDevicePortEvent) -> Unit,
    ): AutoCloseable = synchronized(lock) {
        check(port.isOpenLocked()) { "AppKit HID port is closed" }
        port.installObserverLocked(observer)
    }

    internal fun closePort(port: AppKitHidPort) {
        val release = synchronized(lock) {
            if (!ports.remove(port)) return
            port.closeFromBrokerLocked()
            if (ports.isEmpty()) releaseNativeLocked() else null
        }
        release?.close()
    }

    private fun acceptNativeEvent(event: AppKitHidNativeEvent) {
        val deliveries = synchronized(lock) {
            if (closed) return
            if (openingNative) {
                openingEvents += event
                return
            }
            if (native == null) return
            when (event) {
                is AppKitHidNativeEvent.Connected -> connectLocked(event.device)
                is AppKitHidNativeEvent.Disconnected -> disconnectLocked(event.key)
            }
        }
        deliver(deliveries)
    }

    private fun ensureNativeLocked() {
        if (native != null) return
        check(!openingNative) { "AppKit HID native monitor is already opening" }
        openingNative = true
        try {
            val opened = nativeFactory.open(::acceptNativeEvent)
            native = opened
            opened.devices.forEach(::connectInitialLocked)
            openingEvents.forEach { event ->
                when (event) {
                    is AppKitHidNativeEvent.Connected -> connectInitialLocked(event.device)
                    is AppKitHidNativeEvent.Disconnected -> disconnectInitialLocked(event.key)
                }
            }
        } finally {
            openingEvents.clear()
            openingNative = false
        }
    }

    private fun connectInitialLocked(device: AppKitHidNativeDevice) {
        devices.putIfAbsent(device.key, device)
    }

    private fun disconnectInitialLocked(key: Long) {
        devices.remove(key)
    }

    private fun connectLocked(device: AppKitHidNativeDevice): List<AppKitHidPortDelivery> {
        if (devices.putIfAbsent(device.key, device) != null) return emptyList()
        val source = sourceFor(device)
        return ports.mapNotNull { port -> port.connectedLocked(source) }
    }

    private fun disconnectLocked(key: Long): List<AppKitHidPortDelivery> {
        if (devices.remove(key) == null) return emptyList()
        return ports.mapNotNull { port -> port.disconnectedLocked(key) }
    }

    private fun releaseNativeLocked(): AppKitHidNative? {
        devices.clear()
        return native.also { native = null }
    }

    private fun deliver(deliveries: List<AppKitHidPortDelivery>) {
        deliveries.forEach { delivery -> delivery.observer(delivery.event) }
    }

    private fun sourceFor(device: AppKitHidNativeDevice): InputDevicePortDevice = InputDevicePortDevice(
        key = device.key,
        descriptor = device.descriptor,
    )
}

/** One session-owned port into [AppKitHidBroker]. */
internal class AppKitHidPort internal constructor(
    private val broker: AppKitHidBroker,
) : InputDevicePort {
    private var closed = false
    private var observer: ((InputDevicePortEvent) -> Unit)? = null
    private val known = linkedSetOf<Long>()

    override val devices: List<InputDevicePortDevice>
        get() = broker.devices(this)

    override fun installObserver(observer: (InputDevicePortEvent) -> Unit): AutoCloseable =
        broker.installObserver(this, observer)

    override fun close() {
        broker.closePort(this)
    }

    internal fun isOpenLocked(): Boolean = !closed

    internal fun installInitialLocked(devices: List<InputDevicePortDevice>) {
        devices.forEach { device -> known += device.key }
    }

    internal fun installObserverLocked(observer: (InputDevicePortEvent) -> Unit): AutoCloseable {
        check(this.observer == null) { "HID observer is already installed" }
        this.observer = observer
        return AutoCloseable {
            if (this.observer === observer) this.observer = null
        }
    }

    internal fun connectedLocked(source: InputDevicePortDevice): AppKitHidPortDelivery? {
        if (!known.add(source.key)) return null
        return observer?.let { target -> AppKitHidPortDelivery(target, InputDevicePortEvent.Connected(source)) }
    }

    internal fun disconnectedLocked(key: Long): AppKitHidPortDelivery? {
        if (!known.remove(key)) return null
        return observer?.let { target -> AppKitHidPortDelivery(target, InputDevicePortEvent.Disconnected(key)) }
    }

    internal fun closeFromBrokerLocked() {
        closed = true
        observer = null
        known.clear()
    }
}
