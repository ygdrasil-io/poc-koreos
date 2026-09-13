package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppKitHidBrokerTest {
    @Test
    fun lifecycleObservedWhileNativeMonitorOpensIsFoldedIntoTheInitialProjection() {
        val connected = device(1L, "Opening keyboard", InputDeviceKind.Keyboard)
        val native = RecordingHidNative(emptyList())
        val broker = AppKitHidBroker(
            AppKitHidNativeFactory { listener ->
                listener(AppKitHidNativeEvent.Connected(connected))
                native.open(listener)
            },
        )

        val port = broker.openPort()

        assertEquals(listOf("Opening keyboard"), port.devices.map { it.descriptor.name })
    }

    @Test
    fun lifecycleIsFannedOutToEverySessionPortAndTheInventoryStaysCurrent() {
        val native = RecordingHidNative(listOf(device(1L, "Keyboard", InputDeviceKind.Keyboard)))
        val broker = AppKitHidBroker(AppKitHidNativeFactory(native::open))
        val first = broker.openPort()
        val second = broker.openPort()
        val firstEvents = mutableListOf<InputDevicePortEvent>()
        val secondEvents = mutableListOf<InputDevicePortEvent>()
        first.installObserver(firstEvents::add)
        second.installObserver(secondEvents::add)

        native.connect(device(2L, "Trackpad", InputDeviceKind.Touchpad))

        assertEquals(listOf("Keyboard", "Trackpad"), first.devices.map { it.descriptor.name })
        assertEquals(listOf("Keyboard", "Trackpad"), second.devices.map { it.descriptor.name })
        assertEquals("Trackpad", assertIs<InputDevicePortEvent.Connected>(firstEvents.single()).device.descriptor.name)
        assertEquals("Trackpad", assertIs<InputDevicePortEvent.Connected>(secondEvents.single()).device.descriptor.name)
        firstEvents.clear()
        secondEvents.clear()

        native.disconnect(1L)

        assertEquals(listOf("Trackpad"), first.devices.map { it.descriptor.name })
        assertEquals(listOf("Trackpad"), second.devices.map { it.descriptor.name })
        assertEquals(1L, assertIs<InputDevicePortEvent.Disconnected>(firstEvents.single()).key)
        assertEquals(1L, assertIs<InputDevicePortEvent.Disconnected>(secondEvents.single()).key)
    }

    @Test
    fun lastSessionPortReleasesTheProcessWideNativeMonitor() {
        val native = RecordingHidNative(emptyList())
        val broker = AppKitHidBroker(AppKitHidNativeFactory(native::open))
        val first = broker.openPort()
        val second = broker.openPort()

        first.close()
        assertEquals(0, native.closeCount)

        second.close()

        assertEquals(1, native.closeCount)
        assertTrue(native.observersClosed)
    }

    @Test
    fun failedInitialEnumerationReleasesTheNativeMonitorBeforeTheNextPortRetries() {
        val failed = FailingEnumerationHidNative()
        val retried = RecordingHidNative(listOf(device(7L, "Retry keyboard", InputDeviceKind.Keyboard)))
        var attempts = 0
        val broker = AppKitHidBroker(
            AppKitHidNativeFactory { listener ->
                if (attempts++ == 0) failed.open(listener) else retried.open(listener)
            },
        )

        assertFailsWith<IllegalStateException> { broker.openPort() }
        assertEquals(1, failed.closeCount)

        val port = broker.openPort()

        assertEquals(listOf("Retry keyboard"), port.devices.map { it.descriptor.name })
        port.close()
        assertEquals(1, retried.closeCount)
    }

    private fun device(
        key: Long,
        name: String,
        kind: InputDeviceKind,
    ): AppKitHidNativeDevice = AppKitHidNativeDevice(
        key = key,
        descriptor = InputDeviceDescriptor(name, kind),
    )
}

private class RecordingHidNative(
    override val devices: List<AppKitHidNativeDevice>,
) : AppKitHidNative {
    private lateinit var lifecycle: (AppKitHidNativeEvent) -> Unit
    var closeCount = 0
        private set
    var observersClosed = false
        private set

    fun open(listener: (AppKitHidNativeEvent) -> Unit): AppKitHidNative {
        lifecycle = listener
        return this
    }

    fun connect(device: AppKitHidNativeDevice) {
        lifecycle(AppKitHidNativeEvent.Connected(device))
    }

    fun disconnect(key: Long) {
        lifecycle(AppKitHidNativeEvent.Disconnected(key))
    }

    override fun close() {
        closeCount += 1
        observersClosed = true
    }
}

private class FailingEnumerationHidNative : AppKitHidNative {
    var closeCount = 0
        private set

    override val devices: List<AppKitHidNativeDevice>
        get() = error("HID inventory unavailable")

    fun open(listener: (AppKitHidNativeEvent) -> Unit): AppKitHidNative = this

    override fun close() {
        closeCount += 1
    }
}
