package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class AppKitMemoryPressureBrokerTest {
    @Test
    fun sourceCreationFailureIsPublishedAsUnavailableWithoutSynthesizingASignal() {
        val delivered = mutableListOf<MemoryPressureLevel>()
        val broker = AppKitMemoryPressureBroker(
            object : AppKitMemoryPressureNative {
                override fun open(listener: (MemoryPressureLevel) -> Unit): AutoCloseable =
                    error("source unavailable")
            },
            delivered::add,
        )

        assertEquals(
            FeatureAvailability.Unavailable(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "memory-pressure", "source-exception"),
            ),
            broker.activate(),
        )
        assertEquals(emptyList(), delivered)
        broker.close()
    }

    @Test
    fun activatedSourceDefersNativePressureAndStopsDeliveryWhenClosed() {
        val native = RecordingMemoryPressureNative()
        val dispatcher = QueuedMemoryPressureDispatcher()
        val delivered = mutableListOf<MemoryPressureLevel>()
        val broker = AppKitMemoryPressureBroker(native, delivered::add, dispatcher)

        assertEquals(FeatureAvailability.Available, broker.activate())

        native.emit(MemoryPressureLevel.Critical)
        assertEquals(emptyList(), delivered)
        assertEquals(1, dispatcher.pendingTaskCount)

        dispatcher.runNext()
        assertEquals(listOf(MemoryPressureLevel.Critical), delivered)

        broker.close()
        native.emit(MemoryPressureLevel.Moderate)
        assertEquals(listOf(MemoryPressureLevel.Critical), delivered)
        assertEquals(1, native.closeCount)
    }

    @Test
    fun queuedNativePressureIsDiscardedWhenBrokerClosesBeforeDispatch() {
        val native = RecordingMemoryPressureNative()
        val dispatcher = QueuedMemoryPressureDispatcher(retainTasksAfterClose = true)
        val delivered = mutableListOf<MemoryPressureLevel>()
        val broker = AppKitMemoryPressureBroker(native, delivered::add, dispatcher)

        assertEquals(FeatureAvailability.Available, broker.activate())
        native.emit(MemoryPressureLevel.Critical)
        assertEquals(1, dispatcher.pendingTaskCount)

        broker.close()
        dispatcher.runNext()

        assertEquals(emptyList(), delivered)
        assertEquals(1, native.closeCount)
    }

    @Test
    fun signalDeliveredDuringNativeSourceOpeningIsRelayedAfterCapabilityAdmission() {
        val dispatcher = QueuedMemoryPressureDispatcher()
        val delivered = mutableListOf<MemoryPressureLevel>()
        val broker = AppKitMemoryPressureBroker(
            native = ImmediateMemoryPressureNative(MemoryPressureLevel.Moderate),
            deliver = delivered::add,
            dispatcher = dispatcher,
        )

        assertEquals(FeatureAvailability.Available, broker.activate())
        assertEquals(1, dispatcher.pendingTaskCount)
        assertEquals(emptyList(), delivered)

        dispatcher.runNext()
        assertEquals(listOf(MemoryPressureLevel.Moderate), delivered)
        broker.close()
    }

    @Test
    fun openingFailureDropsCallbackRaisedBeforeSourceAdmission() {
        val dispatcher = QueuedMemoryPressureDispatcher()
        val delivered = mutableListOf<MemoryPressureLevel>()
        val broker = AppKitMemoryPressureBroker(
            native = object : AppKitMemoryPressureNative {
                override fun open(listener: (MemoryPressureLevel) -> Unit): AutoCloseable {
                    listener(MemoryPressureLevel.Critical)
                    error("source unavailable")
                }
            },
            deliver = delivered::add,
            dispatcher = dispatcher,
        )

        assertEquals(
            FeatureAvailability.Unavailable(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "memory-pressure", "source-exception"),
            ),
            broker.activate(),
        )
        assertEquals(0, dispatcher.pendingTaskCount)
        assertEquals(emptyList(), delivered)
        broker.close()
    }
}

private class RecordingMemoryPressureNative : AppKitMemoryPressureNative {
    private var listener: ((MemoryPressureLevel) -> Unit)? = null
    var closeCount: Int = 0
        private set

    override fun open(listener: (MemoryPressureLevel) -> Unit): AutoCloseable {
        check(this.listener == null)
        this.listener = listener
        return AutoCloseable {
            closeCount += 1
            this.listener = null
        }
    }

    fun emit(level: MemoryPressureLevel) = listener?.invoke(level)
}

private class ImmediateMemoryPressureNative(
    private val initialLevel: MemoryPressureLevel,
) : AppKitMemoryPressureNative {
    override fun open(listener: (MemoryPressureLevel) -> Unit): AutoCloseable {
        listener(initialLevel)
        return AutoCloseable { }
    }
}

private class QueuedMemoryPressureDispatcher(
    private val retainTasksAfterClose: Boolean = false,
) : AppKitMemoryPressureDispatcher {
    private val tasks = ArrayDeque<() -> Unit>()

    val pendingTaskCount: Int
        get() = tasks.size

    override fun dispatch(task: () -> Unit) {
        tasks.addLast(task)
    }

    fun runNext() = tasks.removeFirst().invoke()

    override fun close() {
        if (!retainTasksAfterClose) tasks.clear()
    }
}
