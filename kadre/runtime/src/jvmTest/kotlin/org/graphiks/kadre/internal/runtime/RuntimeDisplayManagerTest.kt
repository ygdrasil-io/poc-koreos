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
    fun publicDisplayAndModeHandlesRemainDistinctAcrossManagersWithMatchingNativeKeys() = runTest {
        val first = manager(FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L))))
        val second = manager(FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L))))

        first.requestAccess()
        second.requestAccess()

        val firstDisplay = assertIs<DisplayInventory.Enumerated>(first.state.value.inventory).displays.single()
        val secondDisplay = assertIs<DisplayInventory.Enumerated>(second.state.value.inventory).displays.single()

        assertFalse(firstDisplay.id == secondDisplay.id)
        assertFalse(firstDisplay.state.value.modes.single().id == secondDisplay.state.value.modes.single().id)
    }

    @Test
    fun resolvesTheCurrentDisplayModeToItsOpaqueNativeTarget() = runTest {
        val manager = manager(FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L))))
        manager.requestAccess()
        val display = enumerated(manager).single()
        val mode = display.state.value.modes.single()

        val result = assertIs<KadreResult.Success<ExclusiveDisplayTarget>>(
            manager.resolveExclusiveTarget(display.id, mode),
        )

        assertEquals(ExclusiveDisplayTarget(displayKey = 11L, modeKey = 1L), result.value)
    }

    @Test
    fun rejectsAnExclusiveTargetForAnUnknownOrDisconnectedDisplay() = runTest {
        val port = FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L)))
        val manager = manager(port)
        manager.requestAccess()
        val display = enumerated(manager).single()
        val mode = display.state.value.modes.single()

        assertEquals(
            KadreFailure.InvalidRequest("fullscreen"),
            failureOf(manager.resolveExclusiveTarget(org.graphiks.kadre.display.DisplayId(999L), mode)),
        )

        port.publish(snapshot(displayKeys = emptyList()))

        assertEquals(
            KadreFailure.InvalidRequest("fullscreen"),
            failureOf(manager.resolveExclusiveTarget(display.id, mode)),
        )
    }

    @Test
    fun rejectsAnExclusiveTargetWhoseHandleComesFromAnotherManager() = runTest {
        val first = manager(FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L))))
        val second = manager(FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L))))
        first.requestAccess()
        second.requestAccess()
        val foreignDisplay = enumerated(second).single()

        assertEquals(
            KadreFailure.InvalidRequest("fullscreen"),
            failureOf(first.resolveExclusiveTarget(foreignDisplay.id, foreignDisplay.state.value.modes.single())),
        )
    }

    @Test
    fun rejectsAnExclusiveTargetWithAModeFromAnotherDisplayOrADivergentCopy() = runTest {
        val manager = manager(
            FakeDisplayPort(
                snapshot = snapshotOf(
                    displays = listOf(display(key = 11L), display(key = 12L)),
                ),
            ),
        )
        manager.requestAccess()
        val (first, second) = enumerated(manager)
        val mode = first.state.value.modes.single()

        assertEquals(
            KadreFailure.InvalidRequest("fullscreen"),
            failureOf(manager.resolveExclusiveTarget(first.id, second.state.value.modes.single())),
        )
        assertEquals(
            KadreFailure.InvalidRequest("fullscreen"),
            failureOf(manager.resolveExclusiveTarget(first.id, mode.copy(bitDepth = 30))),
        )
    }

    @Test
    fun rejectsAnExclusiveTargetForARemovedModeAndDefersWhileInventoryIsUnavailable() = runTest {
        val port = FakeDisplayPort(snapshot = snapshot(displayKeys = listOf(11L)))
        val manager = manager(port)
        manager.requestAccess()
        val display = enumerated(manager).single()
        val mode = display.state.value.modes.single()

        port.publish(snapshotOf(displays = listOf(display(key = 11L, modes = emptyList(), currentModeKey = null))))

        assertEquals(
            KadreFailure.InvalidRequest("fullscreen"),
            failureOf(manager.resolveExclusiveTarget(display.id, mode)),
        )

        port.publishFailure(KadreFailure.PlatformFailure(KadrePlatform.AppKit, "display", "reconfigure"))

        assertEquals(
            KadreFailure.TemporarilyUnavailable(retryable = true),
            failureOf(manager.resolveExclusiveTarget(display.id, mode)),
        )
    }

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

    private fun enumerated(manager: RuntimeDisplayManager) =
        assertIs<DisplayInventory.Enumerated>(manager.state.value.inventory).displays

    private fun failureOf(result: KadreResult<ExclusiveDisplayTarget>): KadreFailure =
        assertIs<KadreResult.Failure>(result).reason

    private fun snapshot(displayKeys: List<Long>): DisplayPortSnapshot =
        snapshotOf(displays = displayKeys.map(::display))

    private fun snapshotOf(displays: List<DisplayPortDisplay>): DisplayPortSnapshot = DisplayPortSnapshot(
        primaryKey = displays.firstOrNull()?.key,
        displays = displays,
    )

    private fun display(
        key: Long,
        modes: List<DisplayPortMode> = listOf(mode()),
        currentModeKey: Long? = modes.firstOrNull()?.key,
    ): DisplayPortDisplay = DisplayPortDisplay(
        key = key,
        type = DisplayType.Physical,
        name = "Display $key",
        bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1920, 1080)),
        workArea = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1920, 1040)),
        scaleFactor = 2.0,
        currentModeKey = currentModeKey,
        modes = modes,
    )

    private fun mode(): DisplayPortMode = DisplayPortMode(
        key = 1,
        physicalSize = PhysicalSize(1920, 1080),
        refreshRateHz = 60.0,
        bitDepth = 24,
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
