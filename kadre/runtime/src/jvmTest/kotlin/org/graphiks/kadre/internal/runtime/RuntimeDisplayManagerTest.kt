package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayConnectionState
import org.graphiks.kadre.display.DisplayEvent
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayManagerState
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.nanoseconds

class RuntimeDisplayManagerTest {
    @Test
    fun requestAccessPublishesTheCompleteSnapshotBeforeTheAddedEvent() = runTest {
        val port = FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L)))
        val manager = manager(port)
        val added = async(start = CoroutineStart.UNDISPATCHED) { manager.events.first() }

        val result = assertIs<KadreResult.Success<DisplayManagerState>>(manager.requestAccess())
        val inventory = assertIs<DisplayInventory.Enumerated>(result.value.inventory)
        val event = assertIs<DisplayEvent.Added>(added.await())

        assertEquals(inventory.displays.single(), event.display)
        assertEquals(event.managerRevision, manager.state.value.revision)
        assertEquals(event.state, event.display.state.value)
    }

    @Test
    fun nativeDisplayReconnectionCreatesANewOpaqueHandleAfterTerminalRemoval() = runTest {
        val port = FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L)))
        val manager = manager(port)
        manager.requestAccess()
        val original = assertIs<DisplayInventory.Enumerated>(manager.state.value.inventory).displays.single()

        val removed = async(start = CoroutineStart.UNDISPATCHED) {
            manager.events.first { it is DisplayEvent.Removed }
        }
        port.publish(snapshot(displayKeys = emptyList()))

        val removal = assertIs<DisplayEvent.Removed>(removed.await())
        assertEquals(DisplayConnectionState.Disconnected, original.state.value.connection)
        assertEquals(original.id, removal.displayId)

        val added = async(start = CoroutineStart.UNDISPATCHED) {
            manager.events.first { it is DisplayEvent.Added }
        }
        port.publish(snapshot(displayKeys = listOf(11L)))

        val reconnected = assertIs<DisplayEvent.Added>(added.await()).display
        assertFalse(reconnected.id == original.id)
    }

    @Test
    fun failedNativeReenumerationWithdrawsThePreviousInventoryInsteadOfClaimingItIsCurrent() = runTest {
        val port = FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L)))
        val manager = manager(port)
        manager.requestAccess()
        val original = assertIs<DisplayInventory.Enumerated>(manager.state.value.inventory).displays.single()
        val failure = KadreFailure.PlatformFailure(KadrePlatform.AppKit, "display", "reconfigure")

        port.publishFailure(failure)

        val unavailable = assertIs<DisplayInventory.Unavailable>(manager.state.value.inventory)
        assertSame(failure, unavailable.failure)
        assertEquals(DisplayConnectionState.Disconnected, original.state.value.connection)
    }

    private fun manager(port: FakeDisplayPort): RuntimeDisplayManager = RuntimeDisplayManager(
        port = port,
        eventStampSource = {
            EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null)
        },
        collectorAllocator = RuntimeEventCollectorAllocator(8),
        maxCollectorsPerFlow = 4,
    )

    private fun snapshot(displayKeys: List<Long>): DisplayPortSnapshot = DisplayPortSnapshot(
        primaryKey = displayKeys.firstOrNull(),
        displays = displayKeys.map { key ->
            DisplayPortDisplay(
                key = key,
                type = DisplayType.Physical,
                name = "Display $key",
                bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1920, 1080)),
                workArea = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1920, 1040)),
                scaleFactor = 2.0,
                currentModeKey = 1,
                modes = listOf(
                    DisplayPortMode(
                        key = 1,
                        physicalSize = PhysicalSize(1920, 1080),
                        refreshRateHz = 60.0,
                        bitDepth = 24,
                    ),
                ),
            )
        },
    )
}

private class FakeDisplayPort(
    private var snapshot: DisplayPortSnapshot,
) : DisplayPort {
    private var observer: ((KadreResult<DisplayPortSnapshot>) -> Unit)? = null

    override val enumerationCapability: Capability<Unit> =
        Capability.Supported(Unit, FeatureAvailability.Available)

    override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> = KadreResult.Success(snapshot)

    override fun installSnapshotObserver(observer: (KadreResult<DisplayPortSnapshot>) -> Unit): AutoCloseable {
        this.observer = observer
        return AutoCloseable { if (this.observer === observer) this.observer = null }
    }

    fun publish(next: DisplayPortSnapshot) {
        snapshot = next
        checkNotNull(observer).invoke(KadreResult.Success(next))
    }

    fun publishFailure(failure: KadreFailure) {
        checkNotNull(observer).invoke(KadreResult.Failure(failure))
    }

    override fun close() = Unit
}
