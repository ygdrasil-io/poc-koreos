package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayId
import org.graphiks.kadre.display.DisplayMode
import org.graphiks.kadre.display.DisplayModeId
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.internal.runtime.ExclusiveFullscreenAvailability
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowOperationId
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRevision
import org.graphiks.kadre.window.WindowState
import org.graphiks.kadre.window.WindowSystemButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppKitExclusiveDisplayBrokerTest {
    @Test
    fun competingSessionsReserveOneDisplayBeforeAnyNativeCall() {
        val bridge = RecordingExclusiveDisplayBridge()
        val firstExecutor = QueuedExclusiveExecutor()
        val secondExecutor = QueuedExclusiveExecutor()
        val broker = AppKitExclusiveDisplayBroker(bridge)
        val first = broker.openPort(firstExecutor, UnusedExclusiveWindowPort)
        val second = broker.openPort(secondExecutor, UnusedExclusiveWindowPort)

        assertEquals(KadreResult.Success(Unit), first.reserve(command(1L, 11L, 71L, 701L)))
        assertEquals(
            KadreFailure.TemporarilyUnavailable(retryable = true),
            assertIs<KadreResult.Failure>(second.reserve(command(2L, 22L, 71L, 702L))).reason,
        )
        assertEquals(emptyList(), bridge.opens)

        firstExecutor.runNext()

        assertEquals(listOf(71L to 701L), bridge.opens)
        assertEquals(0, secondExecutor.pendingTaskCount)
    }

    @Test
    fun staleOwnerCleanupCannotReleaseAReassignedDisplayAndTokensNeverRepeat() {
        val bridge = RecordingExclusiveDisplayBridge(openCapturedLeases = true)
        val firstExecutor = QueuedExclusiveExecutor()
        val secondExecutor = QueuedExclusiveExecutor()
        val windowPort = RecordingExclusiveWindowPort()
        val broker = AppKitExclusiveDisplayBroker(bridge)
        val first = broker.openPort(firstExecutor, windowPort)
        val second = broker.openPort(secondExecutor, windowPort)
        val sharedWindowId = identity<WindowId>(7L)
        val firstCommand = command(sharedWindowId, 31L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), first.reserve(firstCommand))
        firstExecutor.runAll()
        first.releaseWindow(sharedWindowId)
        firstExecutor.runAll()

        val secondCommand = command(sharedWindowId, 32L, 71L, 702L)
        assertEquals(KadreResult.Success(Unit), second.reserve(secondCommand))
        secondExecutor.runAll()
        first.releaseWindow(sharedWindowId)
        firstExecutor.runAll()

        assertEquals(2, windowPort.enterRequests.size)
        assertTrue(windowPort.enterRequests[1].token > windowPort.enterRequests[0].token)
        assertEquals(1, bridge.leases[0].releaseCount)
        assertEquals(0, bridge.leases[1].releaseCount)
        assertEquals(listOf(windowState(secondCommand.requestedFullscreen)), secondCommand.completions)
    }

    @Test
    fun selfInducedReconfigurationWaitsForCommitAndReleaseReadbacks() {
        val displayNative = RecordingBrokerDisplayNative(displaySnapshot(71L, 500L))
        val displayBroker = AppKitDisplayBroker(displayNative, ImmediateDisplayDispatcher)
        val bridge = SelfReconfiguringExclusiveBridge(displayNative)
        val executor = QueuedExclusiveExecutor()
        val windowPort = RecordingExclusiveWindowPort()
        val broker = AppKitExclusiveDisplayBroker(bridge, displayBroker)
        val port = broker.openPort(executor, windowPort)
        val losses = mutableListOf<org.graphiks.kadre.internal.runtime.ExclusiveFullscreenDisplayLoss>()
        port.installDisplayLossObserver(losses::add)
        val enter = command(3L, 41L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), port.reserve(enter))
        executor.runAll()

        assertEquals(listOf(windowState(enter.requestedFullscreen)), enter.completions)
        assertEquals(1, windowPort.enterRequests.size)
        assertEquals(0, windowPort.exitRequests.size)
        val exit = RecordingExclusiveCommand(
            windowId = enter.windowId,
            operationId = identity<WindowOperationId>(42L),
            displayKey = enter.displayKey,
            modeKey = enter.modeKey,
            requestedFullscreen = FullscreenMode.Windowed,
        )

        port.release(exit)
        executor.runAll()

        assertEquals(listOf(windowState(FullscreenMode.Windowed)), exit.completions)
        assertEquals(1, windowPort.exitRequests.size)
        assertEquals(4, displayNative.reconfigurationCount)
        assertEquals(emptyList(), losses)
    }

    @Test
    fun capabilityDropPublishesBeforeReservedAndActiveTerminalisation() {
        val displayNative = RecordingBrokerDisplayNative(displaySnapshot(71L, 701L, 72L, 702L))
        val displayBroker = AppKitDisplayBroker(displayNative, ImmediateDisplayDispatcher)
        val bridge = RecordingExclusiveDisplayBridge(openCapturedLeases = true)
        val activeExecutor = QueuedExclusiveExecutor()
        val reservedExecutor = QueuedExclusiveExecutor()
        val windowPort = RecordingExclusiveWindowPort()
        val broker = AppKitExclusiveDisplayBroker(bridge, displayBroker)
        val activePort = broker.openPort(activeExecutor, windowPort)
        val reservedPort = broker.openPort(reservedExecutor, windowPort)
        val activeTrace = mutableListOf<String>()
        val reservedTrace = mutableListOf<String>()
        activePort.installAvailabilityObserver { activeTrace += "availability:$it" }
        activePort.installDisplayLossObserver { activeTrace += "loss:${it.operationId}" }
        reservedPort.installAvailabilityObserver { reservedTrace += "availability:$it" }
        val active = command(4L, 51L, 71L, 701L)
        val reserved = RecordingExclusiveCommand(
            windowId = identity<WindowId>(5L),
            operationId = identity<WindowOperationId>(52L),
            displayKey = 72L,
            modeKey = 702L,
            requestedFullscreen = exclusiveFullscreen(702L),
            trace = reservedTrace,
        )
        assertEquals(KadreResult.Success(Unit), activePort.reserve(active))
        activeExecutor.runAll()
        assertEquals(KadreResult.Success(Unit), reservedPort.reserve(reserved))
        val dropped = KadreFailure.PlatformFailure(KadrePlatform.AppKit, "display", "enumeration-exception")

        displayNative.snapshotFailure = IllegalStateException("inventory unavailable")
        displayNative.emitReconfiguration()

        assertEquals(
            dropped,
            assertIs<KadreResult.Failure>(
                activePort.reserve(command(6L, 53L, 73L, 703L)),
            ).reason,
        )
        assertEquals(emptyList(), activeTrace)
        assertEquals(emptyList(), reservedTrace)
        activeExecutor.runAll()
        reservedExecutor.runAll()

        assertEquals(
            listOf("availability:${ExclusiveFullscreenAvailability.Unavailable(dropped)}", "loss:null"),
            activeTrace,
        )
        assertEquals(
            listOf("availability:${ExclusiveFullscreenAvailability.Unavailable(dropped)}", "failure:$dropped"),
            reservedTrace,
        )
        assertEquals(listOf(71L to 701L), bridge.opens)
        assertEquals(1, bridge.leases.single().releaseCount)
    }

    @Test
    fun unconfirmedReleaseQuarantinesGloballyUntilRecoveryAndHealthyInventory() {
        val displayNative = RecordingBrokerDisplayNative(displaySnapshot(71L, 701L, 72L, 702L))
        val displayBroker = AppKitDisplayBroker(displayNative, ImmediateDisplayDispatcher)
        val lease = RecordingExclusiveDisplayLease(
            displayKey = 71L,
            modeKey = 701L,
            releaseTerminals = ArrayDeque(
                listOf(
                    AppKitExclusiveDisplayTerminal.Unknown,
                    AppKitExclusiveDisplayTerminal.Released(500L),
                ),
            ),
        )
        val bridge = RecordingExclusiveDisplayBridge(leasesToOpen = ArrayDeque(listOf(lease)))
        val recoveryExecutor = QueuedExclusiveExecutor()
        val ownerExecutor = QueuedExclusiveExecutor()
        val observerExecutor = QueuedExclusiveExecutor()
        val broker = AppKitExclusiveDisplayBroker(bridge, displayBroker, recoveryExecutor)
        val ownerPort = broker.openPort(ownerExecutor, RecordingExclusiveWindowPort())
        val observerPort = broker.openPort(observerExecutor, UnusedExclusiveWindowPort)
        val availability = mutableListOf<ExclusiveFullscreenAvailability>()
        observerPort.installAvailabilityObserver(availability::add)
        val enter = command(7L, 61L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), ownerPort.reserve(enter))
        ownerExecutor.runAll()
        val exit = RecordingExclusiveCommand(
            windowId = enter.windowId,
            operationId = identity<WindowOperationId>(62L),
            displayKey = enter.displayKey,
            modeKey = enter.modeKey,
            requestedFullscreen = FullscreenMode.Windowed,
        )
        ownerPort.release(exit)
        ownerExecutor.runAll()
        observerExecutor.runAll()

        assertIs<ExclusiveFullscreenAvailability.Unavailable>(observerPort.availability)
        assertEquals(1, lease.releaseCount)
        assertEquals(0, recoveryExecutor.pendingTaskCount)
        assertTrue(exit.failures.isNotEmpty())
        assertIs<KadreResult.Failure>(
            observerPort.reserve(command(8L, 63L, 72L, 702L)),
        )
        assertEquals(listOf(71L to 701L), bridge.opens)

        ownerPort.close()
        assertEquals(1, recoveryExecutor.pendingTaskCount)
        recoveryExecutor.runAll()
        observerExecutor.runAll()

        assertEquals(2, lease.releaseCount)
        assertEquals(ExclusiveFullscreenAvailability.Available, observerPort.availability)
        assertEquals(
            listOf(
                assertIs<ExclusiveFullscreenAvailability.Unavailable>(availability.first()),
                ExclusiveFullscreenAvailability.Available,
            ),
            availability,
        )
        assertEquals(KadreResult.Success(Unit), observerPort.reserve(command(8L, 64L, 72L, 702L)))
        assertFalse(ownerPort.isOpen())
    }

    private fun command(
        window: Long,
        operation: Long,
        displayKey: Long,
        modeKey: Long,
    ): RecordingExclusiveCommand = command(identity<WindowId>(window), operation, displayKey, modeKey)

    private fun command(
        windowId: WindowId,
        operation: Long,
        displayKey: Long,
        modeKey: Long,
    ): RecordingExclusiveCommand = RecordingExclusiveCommand(
        windowId = windowId,
        operationId = identity<WindowOperationId>(operation),
        displayKey = displayKey,
        modeKey = modeKey,
        requestedFullscreen = exclusiveFullscreen(modeKey),
    )
}

private class RecordingExclusiveDisplayBridge(
    private val openCapturedLeases: Boolean = false,
    private val leasesToOpen: ArrayDeque<RecordingExclusiveDisplayLease> = ArrayDeque(),
) : AppKitExclusiveDisplayBridge {
    val opens = mutableListOf<Pair<Long, Long>>()
    val leases = mutableListOf<RecordingExclusiveDisplayLease>()

    override val availability = AppKitExclusiveBridgeAvailability.Available

    override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
        opens += displayKey to modeKey
        if (leasesToOpen.isNotEmpty()) {
            return AppKitExclusiveDisplayOpenResult.Opened(leasesToOpen.removeFirst().also(leases::add))
        }
        if (openCapturedLeases) {
            return AppKitExclusiveDisplayOpenResult.Opened(
                RecordingExclusiveDisplayLease(displayKey, modeKey).also(leases::add),
            )
        }
        return AppKitExclusiveDisplayOpenResult.FailedBeforeCapture(
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "capture-failed"),
        )
    }
}

private class RecordingExclusiveCommand(
    override val windowId: WindowId,
    override val operationId: WindowOperationId,
    override val displayKey: Long,
    override val modeKey: Long,
    override val requestedFullscreen: FullscreenMode,
    private val trace: MutableList<String>? = null,
) : AppKitExclusiveBrokerCommand {
    val completions = mutableListOf<WindowState>()
    val failures = mutableListOf<Pair<KadreFailure, WindowState?>>()

    override fun captureCommitted(): Boolean = true

    override fun completed(effectiveState: WindowState) {
        completions += effectiveState
    }

    override fun failed(failure: KadreFailure, effectiveState: WindowState?) {
        failures += failure to effectiveState
        trace?.add("failure:$failure")
    }
}

private class RecordingExclusiveDisplayLease(
    override val displayKey: Long,
    private val modeKey: Long,
    private val releaseTerminals: ArrayDeque<AppKitExclusiveDisplayTerminal> = ArrayDeque(),
) : AppKitExclusiveDisplayLease {
    var releaseCount = 0
        private set

    override fun readback(): AppKitExclusiveDisplayReadback =
        AppKitExclusiveDisplayReadback(AppKitExclusiveDisplayTerminal.Captured(modeKey))

    override fun release(): AppKitExclusiveDisplayReleaseResult {
        releaseCount += 1
        return AppKitExclusiveDisplayReleaseResult(
            if (releaseTerminals.isEmpty()) {
                AppKitExclusiveDisplayTerminal.Released(modeKey)
            } else {
                releaseTerminals.removeFirst()
            },
        )
    }
}

private class SelfReconfiguringExclusiveBridge(
    private val native: RecordingBrokerDisplayNative,
) : AppKitExclusiveDisplayBridge {
    override val availability = AppKitExclusiveBridgeAvailability.Available

    override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
        native.snapshot = displaySnapshot(displayKey, 500L)
        native.emitReconfiguration()
        native.snapshot = displaySnapshot(displayKey, modeKey)
        native.emitReconfiguration()
        return AppKitExclusiveDisplayOpenResult.Opened(
            object : AppKitExclusiveDisplayLease {
                override val displayKey: Long = displayKey

                override fun readback(): AppKitExclusiveDisplayReadback =
                    AppKitExclusiveDisplayReadback(AppKitExclusiveDisplayTerminal.Captured(modeKey))

                override fun release(): AppKitExclusiveDisplayReleaseResult {
                    native.snapshot = displaySnapshot(displayKey, modeKey)
                    native.emitReconfiguration()
                    native.snapshot = displaySnapshot(displayKey, 500L)
                    native.emitReconfiguration()
                    return AppKitExclusiveDisplayReleaseResult(AppKitExclusiveDisplayTerminal.Released(500L))
                }
            },
        )
    }
}

private class RecordingExclusiveWindowPort : AppKitExclusiveWindowPort {
    val enterRequests = mutableListOf<AppKitExclusiveWindowRequest>()
    val exitRequests = mutableListOf<AppKitExclusiveWindowRequest>()

    override fun enter(request: AppKitExclusiveWindowRequest): AppKitExclusiveWindowResult {
        enterRequests += request
        return AppKitExclusiveWindowResult.Read(windowState(request.requestedFullscreen))
    }

    override fun exit(request: AppKitExclusiveWindowRequest): AppKitExclusiveWindowResult {
        exitRequests += request
        return AppKitExclusiveWindowResult.Read(windowState(FullscreenMode.Windowed))
    }
}

private class QueuedExclusiveExecutor : AppKitExclusiveExecutor {
    private val tasks = ArrayDeque<() -> Unit>()

    val pendingTaskCount: Int
        get() = tasks.size

    override fun dispatch(task: () -> Unit): Boolean {
        tasks.addLast(task)
        return true
    }

    fun runNext() = tasks.removeFirst().invoke()

    fun runAll() {
        while (tasks.isNotEmpty()) runNext()
    }
}

private object UnusedExclusiveWindowPort : AppKitExclusiveWindowPort

private class RecordingBrokerDisplayNative(
    var snapshot: DisplayPortSnapshot,
) : AppKitDisplayNative {
    private var listener: (() -> Unit)? = null
    var reconfigurationCount = 0
        private set
    var snapshotFailure: Throwable? = null

    override val enumerationCapability = org.graphiks.kadre.diagnostics.Capability.Supported(
        Unit,
        org.graphiks.kadre.diagnostics.FeatureAvailability.Available,
    )

    override fun snapshot(): DisplayPortSnapshot = snapshotFailure?.let { throw it } ?: snapshot

    override fun observeReconfiguration(listener: () -> Unit): AutoCloseable {
        check(this.listener == null)
        this.listener = listener
        return AutoCloseable { if (this.listener === listener) this.listener = null }
    }

    fun emitReconfiguration() {
        reconfigurationCount += 1
        checkNotNull(listener).invoke()
    }

    override fun close() = Unit
}

private object ImmediateDisplayDispatcher : AppKitDisplayReconfigurationDispatcher {
    override fun dispatch(task: () -> Unit) = task()

    override fun close() = Unit
}

private inline fun <reified T : Any> identity(value: Long): T = T::class.java
    .getDeclaredConstructor(Long::class.javaPrimitiveType)
    .apply { isAccessible = true }
    .newInstance(value)

private fun exclusiveFullscreen(seed: Long): FullscreenMode.Exclusive = FullscreenMode.Exclusive(
    displayId = identity<DisplayId>(seed),
    mode = DisplayMode(
        id = identity<DisplayModeId>(seed),
        physicalSize = PhysicalSize(1920, 1080),
        refreshRateHz = 60.0,
        bitDepth = 24,
    ),
)

private fun windowState(fullscreen: FullscreenMode): WindowState = WindowState(
    phase = WindowPhase.Open,
    title = "exclusive",
    outerBounds = null,
    contentSize = LogicalSize(800.0, 600.0),
    minimumSize = null,
    maximumSize = null,
    resizable = true,
    fullscreen = fullscreen,
    decorations = WindowDecorations.System,
    systemButtons = WindowSystemButtons.All,
    level = WindowLevel.Normal,
    transparent = false,
    blurBehind = false,
    icon = null,
    contentProtection = false,
    revision = identity<WindowRevision>(0L),
)

private fun displaySnapshot(displayKey: Long, currentModeKey: Long): DisplayPortSnapshot = DisplayPortSnapshot(
    primaryKey = displayKey,
    displays = listOf(
        DisplayPortDisplay(
            key = displayKey,
            type = DisplayType.Physical,
            name = "Display $displayKey",
            bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1920, 1080)),
            workArea = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1920, 1040)),
            scaleFactor = 1.0,
            currentModeKey = currentModeKey,
            modes = listOf(
                DisplayPortMode(500L, PhysicalSize(1920, 1080), 60.0, 24),
                DisplayPortMode(701L, PhysicalSize(1280, 720), 60.0, 24),
            ),
        ),
    ),
)

private fun displaySnapshot(
    firstDisplayKey: Long,
    firstModeKey: Long,
    secondDisplayKey: Long,
    secondModeKey: Long,
): DisplayPortSnapshot = DisplayPortSnapshot(
    primaryKey = firstDisplayKey,
    displays = listOf(
        displaySnapshot(firstDisplayKey, firstModeKey).displays.single(),
        displaySnapshot(secondDisplayKey, secondModeKey).displays.single(),
    ),
)
