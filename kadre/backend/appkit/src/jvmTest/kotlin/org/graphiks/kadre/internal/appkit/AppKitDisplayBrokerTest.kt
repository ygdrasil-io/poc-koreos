package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppKitDisplayBrokerTest {
    @Test
    fun reconfigurationDefersNativeEnumerationUntilAfterTheNativeCallbackReturns() = runBlocking {
        val native = RecordingAppKitDisplayNative(snapshot = displaySnapshot(17L))
        val dispatcher = QueuedAppKitDisplayReconfigurationDispatcher()
        val broker = AppKitDisplayBroker(native, dispatcher)
        val port = broker.openPort()
        val observations = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        port.installSnapshotObserver(observations::add)

        native.snapshot = displaySnapshot(29L)
        native.emitReconfiguration()

        assertEquals(emptyList(), observations)
        assertEquals(1, dispatcher.pendingTaskCount)

        dispatcher.runNext()

        assertEquals(
            listOf<KadreResult<DisplayPortSnapshot>>(KadreResult.Success(displaySnapshot(29L))),
            observations,
        )
        broker.close()
    }

    @Test
    fun reconfigurationFanoutNeverExposesPartialInventoryAndStopsItsNativeOwnerAfterTheLastPort() = runBlocking {
        val native = RecordingAppKitDisplayNative(snapshot = displaySnapshot(17L))
        val dispatcher = QueuedAppKitDisplayReconfigurationDispatcher()
        val broker = AppKitDisplayBroker(native, dispatcher)
        val first = broker.openPort()
        val second = broker.openPort()
        val firstObservations = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        val secondObservations = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        first.installSnapshotObserver(firstObservations::add)
        second.installSnapshotObserver(secondObservations::add)

        assertEquals(displaySnapshot(17L), assertIs<KadreResult.Success<DisplayPortSnapshot>>(first.requestSnapshot()).value)

        native.snapshot = displaySnapshot(29L)
        native.emitReconfiguration()
        dispatcher.runNext()

        val expected = listOf<KadreResult<DisplayPortSnapshot>>(KadreResult.Success(displaySnapshot(29L)))
        assertEquals(expected, firstObservations)
        assertEquals(expected, secondObservations)
        first.close()
        assertEquals(0, native.observerCloseCount)
        second.close()
        assertEquals(1, native.observerCloseCount)
    }

    @Test
    fun failedReenumerationIsDeliveredToEverySessionInsteadOfLeavingAStaleInventory() {
        val native = RecordingAppKitDisplayNative(snapshot = displaySnapshot(17L))
        val dispatcher = QueuedAppKitDisplayReconfigurationDispatcher()
        val broker = AppKitDisplayBroker(native, dispatcher)
        val first = broker.openPort()
        val second = broker.openPort()
        val firstObservations = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        val secondObservations = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        first.installSnapshotObserver(firstObservations::add)
        second.installSnapshotObserver(secondObservations::add)

        native.snapshotFailure = IllegalStateException("native inventory failed")
        native.emitReconfiguration()
        dispatcher.runNext()

        val expected: KadreResult<DisplayPortSnapshot> = KadreResult.Failure(
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "display", "enumeration-exception"),
        )
        assertEquals(listOf(expected), firstObservations)
        assertEquals(listOf(expected), secondObservations)
        broker.close()
    }
}

private class RecordingAppKitDisplayNative(
    var snapshot: DisplayPortSnapshot,
) : AppKitDisplayNative {
    private var listener: (() -> Unit)? = null
    var observerCloseCount = 0
        private set
    var snapshotFailure: Throwable? = null

    override val enumerationCapability: Capability<Unit> = Capability.Supported(Unit, FeatureAvailability.Available)

    override fun snapshot(): DisplayPortSnapshot = snapshotFailure?.let { throw it } ?: snapshot

    override fun observeReconfiguration(listener: () -> Unit): AutoCloseable {
        check(this.listener == null)
        this.listener = listener
        return AutoCloseable {
            if (this.listener != null) {
                this.listener = null
                observerCloseCount += 1
            }
        }
    }

    fun emitReconfiguration() = checkNotNull(listener).invoke()

    override fun close() = Unit
}

private class QueuedAppKitDisplayReconfigurationDispatcher : AppKitDisplayReconfigurationDispatcher {
    private val tasks = ArrayDeque<() -> Unit>()

    val pendingTaskCount: Int
        get() = tasks.size

    override fun dispatch(task: () -> Unit) {
        tasks.addLast(task)
    }

    fun runNext() = tasks.removeFirst().invoke()

    override fun close() {
        tasks.clear()
    }
}

private fun displaySnapshot(displayKey: Long): DisplayPortSnapshot = DisplayPortSnapshot(
    primaryKey = displayKey,
    displays = listOf(
        DisplayPortDisplay(
            key = displayKey,
            type = DisplayType.Physical,
            name = "Display $displayKey",
            bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1920, 1080)),
            workArea = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1920, 1040)),
            scaleFactor = 2.0,
            currentModeKey = 0,
            modes = listOf(DisplayPortMode(0, PhysicalSize(1920, 1080), 60.0, 24)),
        ),
    ),
)
