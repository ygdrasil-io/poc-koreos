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
    fun failedBeforeCaptureRestoresThePreparedPresentationLease() {
        val executor = QueuedExclusiveExecutor()
        val windowPort = RecordingExclusiveWindowPort()
        val broker = AppKitExclusiveDisplayBroker(RecordingExclusiveDisplayBridge())
        val port = broker.openPort(executor, windowPort)

        assertEquals(KadreResult.Success(Unit), port.reserve(command(89L, 891L, 71L, 701L)))
        executor.runAll()

        assertEquals(1, windowPort.exitRequests.size)
    }

    @Test
    fun failedBeforeCaptureTerminalizesAnUnrestorablePreparedLeaseAndReportsItsTypedFailure() {
        val executor = QueuedExclusiveExecutor()
        val windowPort = RecordingExclusiveWindowPort(failExit = true)
        val broker = AppKitExclusiveDisplayBroker(RecordingExclusiveDisplayBridge())
        val port = broker.openPort(executor, windowPort)
        val command = command(87L, 871L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), port.reserve(command))
        executor.runAll()

        assertEquals(listOf(command.windowId), windowPort.terminalized)
        assertEquals(
            listOf(KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "restore-failed")),
            windowPort.diagnostics,
        )
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "capture-failed"),
            command.failures.single().first,
        )
    }

    @Test
    fun unrepresentablePresentationRestoreTerminalizesTheNativePeerBeforeFailureCompletion() {
        val executor = QueuedExclusiveExecutor()
        val windowPort = RecordingExclusiveWindowPort(failEnter = true, failExit = true)
        val bridge = RecordingExclusiveDisplayBridge(openCapturedLeases = true)
        val broker = AppKitExclusiveDisplayBroker(bridge)
        val port = broker.openPort(executor, windowPort)
        val command = command(88L, 881L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), port.reserve(command))
        executor.runAll()

        assertEquals(listOf(command.windowId), windowPort.terminalized)
        assertEquals(1, bridge.leases.single().releaseCount)
        assertEquals(
            setOf("present-failed", "restore-failed"),
            windowPort.diagnostics.map { it.code }.toSet(),
        )
    }

    @Test
    fun failedPresentationKeepsCoreGraphicsReleaseExceptionAsTypedDiagnostic() {
        val executor = QueuedExclusiveExecutor()
        val releaseFailure = IllegalStateException("release exploded")
        val lease = RecordingExclusiveDisplayLease(
            displayKey = 71L,
            modeKey = 701L,
            releaseFailure = releaseFailure,
        )
        val windowPort = RecordingExclusiveWindowPort(failEnter = true)
        val broker = AppKitExclusiveDisplayBroker(
            RecordingExclusiveDisplayBridge(leasesToOpen = ArrayDeque(listOf(lease))),
        )
        val port = broker.openPort(executor, windowPort)
        val command = command(85L, 851L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), port.reserve(command))
        executor.runAll()

        assertEquals("present-failed", (command.failures.single().first as KadreFailure.PlatformFailure).code)
        assertTrue(windowPort.diagnostics.any { it.code == "coregraphics-release-exception" })
        assertEquals(1, lease.releaseCount)
    }

    @Test
    fun ordinaryReleaseReportsTheTypedCoreGraphicsExceptionInsteadOfFlatteningIt() {
        val executor = QueuedExclusiveExecutor()
        val lease = RecordingExclusiveDisplayLease(
            displayKey = 71L,
            modeKey = 701L,
            releaseFailure = IllegalStateException("release exploded"),
        )
        val windowPort = RecordingExclusiveWindowPort()
        val broker = AppKitExclusiveDisplayBroker(
            RecordingExclusiveDisplayBridge(leasesToOpen = ArrayDeque(listOf(lease))),
        )
        val port = broker.openPort(executor, windowPort)
        val enter = command(83L, 831L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), port.reserve(enter))
        executor.runAll()

        val exit = RecordingExclusiveCommand(
            windowId = enter.windowId,
            operationId = identity<WindowOperationId>(832L),
            displayKey = enter.displayKey,
            modeKey = enter.modeKey,
            requestedFullscreen = FullscreenMode.Windowed,
        )
        port.release(exit)
        executor.runAll()

        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "coregraphics-release-exception"),
            exit.failures.single().first,
        )
        assertEquals(1, lease.releaseCount)
        assertIs<ExclusiveFullscreenAvailability.Unavailable>(port.availability)
    }

    @Test
    fun failedAfterCaptureWithoutItsEntryQuarantinesAThrownRecoveryInsteadOfEscapingOrLosingIt() {
        val executor = QueuedExclusiveExecutor()
        val recoveryExecutor = QueuedExclusiveExecutor()
        var throwOnRelease = true
        val recovery = object : AppKitExclusiveDisplayLease {
            override val displayKey: Long = 71L

            override fun readback(): AppKitExclusiveDisplayReadback =
                AppKitExclusiveDisplayReadback(AppKitExclusiveDisplayTerminal.Captured(701L))

            override fun release(): AppKitExclusiveDisplayReleaseResult {
                if (throwOnRelease) throw IllegalStateException("orphaned release exploded")
                return AppKitExclusiveDisplayReleaseResult(AppKitExclusiveDisplayTerminal.Released(500L))
            }
        }
        lateinit var broker: AppKitExclusiveDisplayBroker
        val bridge = object : AppKitExclusiveDisplayBridge {
            override val availability = AppKitExclusiveBridgeAvailability.Available

            override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
                @Suppress("UNCHECKED_CAST")
                val entries = broker.javaClass.getDeclaredField("entries").apply { isAccessible = true }
                    .get(broker) as MutableMap<Long, Any>
                entries.clear()
                return AppKitExclusiveDisplayOpenResult.FailedAfterCapture(
                    terminal = AppKitExclusiveDisplayTerminal.Unknown,
                    cleanup = AppKitExclusiveDisplayReleaseResult(AppKitExclusiveDisplayTerminal.Unknown),
                    recovery = recovery,
                    failure = KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "capture-failed"),
                )
            }
        }
        broker = AppKitExclusiveDisplayBroker(bridge, recoveryExecutor = recoveryExecutor)
        val port = broker.openPort(executor, RecordingExclusiveWindowPort())

        assertEquals(KadreResult.Success(Unit), port.reserve(command(85L, 852L, 71L, 701L)))
        executor.runAll()

        assertIs<ExclusiveFullscreenAvailability.Unavailable>(port.availability)
        assertEquals(1, recoveryExecutor.pendingTaskCount)
        throwOnRelease = false
        recoveryExecutor.runAll()
        executor.runAll()

        assertEquals(ExclusiveFullscreenAvailability.Available, port.availability)
    }

    @Test
    fun closingPortRetriesAQuarantinedOrphanOnceAfterItsInitialRecoveryFailed() {
        val executor = QueuedExclusiveExecutor()
        val recoveryExecutor = QueuedExclusiveExecutor()
        val recovery = RecordingExclusiveDisplayLease(
            displayKey = 71L,
            modeKey = 701L,
            releaseFailureSequence = ArrayDeque(
                listOf(
                    IllegalStateException("orphaned first release failed"),
                    IllegalStateException("orphaned scheduled release failed"),
                    null,
                ),
            ),
        )
        lateinit var broker: AppKitExclusiveDisplayBroker
        val bridge = object : AppKitExclusiveDisplayBridge {
            override val availability = AppKitExclusiveBridgeAvailability.Available

            override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
                @Suppress("UNCHECKED_CAST")
                val entries = broker.javaClass.getDeclaredField("entries").apply { isAccessible = true }
                    .get(broker) as MutableMap<Long, Any>
                entries.clear()
                return AppKitExclusiveDisplayOpenResult.FailedAfterCapture(
                    terminal = AppKitExclusiveDisplayTerminal.Unknown,
                    cleanup = AppKitExclusiveDisplayReleaseResult(AppKitExclusiveDisplayTerminal.Unknown),
                    recovery = recovery,
                    failure = KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "capture-failed"),
                )
            }
        }
        broker = AppKitExclusiveDisplayBroker(bridge, recoveryExecutor = recoveryExecutor)
        val port = broker.openPort(executor, RecordingExclusiveWindowPort())

        assertEquals(KadreResult.Success(Unit), port.reserve(command(185L, 1852L, 71L, 701L)))
        executor.runAll()
        assertEquals(1, recoveryExecutor.pendingTaskCount)

        recoveryExecutor.runAll()

        assertEquals(2, recovery.releaseCount)
        assertEquals(0, recoveryExecutor.pendingTaskCount)
        assertEquals(listOf("coregraphics-release-exception"), broker.orphanedRecoveryFailureCodes())
        assertIs<ExclusiveFullscreenAvailability.Unavailable>(port.availability)

        port.close()

        assertEquals(1, recoveryExecutor.pendingTaskCount)
        recoveryExecutor.runAll()
        val observer = broker.openPort(QueuedExclusiveExecutor(), UnusedExclusiveWindowPort)

        assertEquals(3, recovery.releaseCount)
        assertEquals(emptyList(), broker.orphanedRecoveryFailureCodes())
        assertEquals(ExclusiveFullscreenAvailability.Available, observer.availability)
        observer.close()
    }

    @Test
    fun closingBrokerRetriesAQuarantinedOrphanOnceAfterItsInitialRecoveryFailed() {
        val executor = QueuedExclusiveExecutor()
        val recoveryExecutor = QueuedExclusiveExecutor()
        val recovery = RecordingExclusiveDisplayLease(
            displayKey = 71L,
            modeKey = 701L,
            releaseFailureSequence = ArrayDeque(
                listOf(
                    IllegalStateException("orphaned first release failed"),
                    IllegalStateException("orphaned scheduled release failed"),
                    null,
                ),
            ),
        )
        lateinit var broker: AppKitExclusiveDisplayBroker
        val bridge = object : AppKitExclusiveDisplayBridge {
            override val availability = AppKitExclusiveBridgeAvailability.Available

            override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
                @Suppress("UNCHECKED_CAST")
                val entries = broker.javaClass.getDeclaredField("entries").apply { isAccessible = true }
                    .get(broker) as MutableMap<Long, Any>
                entries.clear()
                return AppKitExclusiveDisplayOpenResult.FailedAfterCapture(
                    terminal = AppKitExclusiveDisplayTerminal.Unknown,
                    cleanup = AppKitExclusiveDisplayReleaseResult(AppKitExclusiveDisplayTerminal.Unknown),
                    recovery = recovery,
                    failure = KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "capture-failed"),
                )
            }
        }
        broker = AppKitExclusiveDisplayBroker(bridge, recoveryExecutor = recoveryExecutor)
        val port = broker.openPort(executor, RecordingExclusiveWindowPort())

        assertEquals(KadreResult.Success(Unit), port.reserve(command(186L, 1862L, 71L, 701L)))
        executor.runAll()
        recoveryExecutor.runAll()
        assertEquals(2, recovery.releaseCount)
        assertEquals(0, recoveryExecutor.pendingTaskCount)
        assertIs<ExclusiveFullscreenAvailability.Unavailable>(port.availability)

        broker.close()

        assertEquals(1, recoveryExecutor.pendingTaskCount)
        recoveryExecutor.runAll()

        assertEquals(3, recovery.releaseCount)
        assertEquals(emptyList(), broker.orphanedRecoveryFailureCodes())
    }

    @Test
    fun capturedWithoutRuntimeQuarantinesAReleaseExceptionUntilScheduledRecoveryCertifiesRelease() {
        val executor = QueuedExclusiveExecutor()
        val recoveryExecutor = QueuedExclusiveExecutor()
        val firstFailure = IllegalStateException("captured without runtime release exploded")
        val lease = RecordingExclusiveDisplayLease(
            displayKey = 71L,
            modeKey = 701L,
            releaseFailureSequence = ArrayDeque(listOf(firstFailure, null)),
        )
        lateinit var broker: AppKitExclusiveDisplayBroker
        val bridge = object : AppKitExclusiveDisplayBridge {
            override val availability = AppKitExclusiveBridgeAvailability.Available

            override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
                @Suppress("UNCHECKED_CAST")
                val entries = broker.javaClass.getDeclaredField("entries").apply { isAccessible = true }
                    .get(broker) as Map<Long, Any>
                checkNotNull(entries[displayKey]).javaClass.getDeclaredField("command").apply { isAccessible = true }
                    .set(entries.getValue(displayKey), null)
                return AppKitExclusiveDisplayOpenResult.Opened(lease)
            }
        }
        broker = AppKitExclusiveDisplayBroker(bridge = bridge, recoveryExecutor = recoveryExecutor)
        val port = broker.openPort(executor, RecordingExclusiveWindowPort())
        val command = RecordingExclusiveCommand(
            windowId = identity<WindowId>(86L),
            operationId = identity<WindowOperationId>(862L),
            displayKey = 71L,
            modeKey = 701L,
            requestedFullscreen = exclusiveFullscreen(701L),
        )

        assertEquals(KadreResult.Success(Unit), port.reserve(command))
        executor.runAll()

        assertIs<ExclusiveFullscreenAvailability.Unavailable>(port.availability)
        assertEquals(emptyList(), command.failures)
        port.close()
        assertEquals(1, recoveryExecutor.pendingTaskCount)
        recoveryExecutor.runAll()

        assertEquals(2, lease.releaseCount)
    }

    @Test
    fun earlyReleaseKeepsThePresentationFailurePrimaryAndReportsTheReleaseExceptionAsDiagnostic() {
        val executor = QueuedExclusiveExecutor()
        val releaseFailure = IllegalStateException("early release exploded")
        val lease = RecordingExclusiveDisplayLease(
            displayKey = 71L,
            modeKey = 701L,
            releaseFailure = releaseFailure,
        )
        lateinit var broker: AppKitExclusiveDisplayBroker
        val bridge = object : AppKitExclusiveDisplayBridge {
            override val availability = AppKitExclusiveBridgeAvailability.Available

            override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
                @Suppress("UNCHECKED_CAST")
                val entries = broker.javaClass.getDeclaredField("entries").apply { isAccessible = true }
                    .get(broker) as Map<Long, Any>
                val entry = checkNotNull(entries[displayKey])
                entry.javaClass.getDeclaredField("releaseRequested").apply { isAccessible = true }
                    .setBoolean(entry, true)
                return AppKitExclusiveDisplayOpenResult.Opened(lease)
            }
        }
        broker = AppKitExclusiveDisplayBroker(bridge)
        val windowPort = RecordingExclusiveWindowPort(failExit = true)
        val port = broker.openPort(executor, windowPort)
        val command = command(87L, 872L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), port.reserve(command))
        executor.runAll()

        assertEquals("restore-failed", (command.failures.single().first as KadreFailure.PlatformFailure).code)
        assertTrue(windowPort.diagnostics.any { it.code == "coregraphics-release-exception" })
        assertIs<ExclusiveFullscreenAvailability.Unavailable>(port.availability)
        assertEquals(1, lease.releaseCount)
    }

    @Test
    fun lateQuarantineKeepsAReleaseExceptionRecoverableInsteadOfDiscardingTheCapturedLease() {
        val executor = QueuedExclusiveExecutor()
        val recoveryExecutor = QueuedExclusiveExecutor()
        val lease = RecordingExclusiveDisplayLease(
            displayKey = 71L,
            modeKey = 701L,
            releaseFailureSequence = ArrayDeque(listOf(IllegalStateException("late release exploded"), null)),
        )
        lateinit var broker: AppKitExclusiveDisplayBroker
        val bridge = object : AppKitExclusiveDisplayBridge {
            override val availability = AppKitExclusiveBridgeAvailability.Available

            override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
                @Suppress("UNCHECKED_CAST")
                val entries = broker.javaClass.getDeclaredField("entries").apply { isAccessible = true }
                    .get(broker) as Map<Long, Any>
                val entry = checkNotNull(entries[displayKey])
                val state = entry.javaClass.getDeclaredField("state").apply { isAccessible = true }
                state.set(
                    entry,
                    state.type.enumConstants.single { (it as Enum<*>).name == "Quarantined" },
                )
                return AppKitExclusiveDisplayOpenResult.Opened(lease)
            }
        }
        broker = AppKitExclusiveDisplayBroker(bridge, recoveryExecutor = recoveryExecutor)
        val port = broker.openPort(executor, RecordingExclusiveWindowPort())

        assertEquals(KadreResult.Success(Unit), port.reserve(command(88L, 873L, 71L, 701L)))
        executor.runAll()

        assertIs<ExclusiveFullscreenAvailability.Unavailable>(port.availability)
        port.close()
        assertEquals(1, recoveryExecutor.pendingTaskCount)
        recoveryExecutor.runAll()
        assertEquals(2, lease.releaseCount)
    }

    @Test
    fun failedPresentationAggregatesReadbackExitAndReportedReleaseDiagnostics() {
        val executor = QueuedExclusiveExecutor()
        val reportedRelease = KadreFailure.PlatformFailure(
            KadrePlatform.AppKit,
            "exclusive-fullscreen",
            "coregraphics-release-capture",
        )
        val lease = RecordingExclusiveDisplayLease(
            displayKey = 71L,
            modeKey = 701L,
            readbackFailure = IllegalStateException("readback exploded"),
            releaseFailures = listOf(reportedRelease),
        )
        val windowPort = RecordingExclusiveWindowPort(failEnter = true, failExit = true)
        val broker = AppKitExclusiveDisplayBroker(
            RecordingExclusiveDisplayBridge(leasesToOpen = ArrayDeque(listOf(lease))),
        )
        val port = broker.openPort(executor, windowPort)
        val command = command(84L, 841L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), port.reserve(command))
        executor.runAll()

        assertEquals("present-failed", (command.failures.single().first as KadreFailure.PlatformFailure).code)
        assertEquals(
            setOf(
                "present-failed",
                "coregraphics-readback-exception",
                "restore-failed",
                "coregraphics-release-capture",
            ),
            windowPort.diagnostics.map { it.code }.toSet(),
        )
        assertEquals(listOf(command.windowId), windowPort.terminalized)
    }

    @Test
    fun failedAfterCaptureRestoresPresentationForEveryCoreGraphicsTerminal() {
        listOf(
            AppKitExclusiveDisplayTerminal.Captured(701L),
            AppKitExclusiveDisplayTerminal.Released(701L),
            AppKitExclusiveDisplayTerminal.Unknown,
        ).forEachIndexed { index, terminal ->
            val executor = QueuedExclusiveExecutor()
            val recoveryExecutor = QueuedExclusiveExecutor()
            val windowPort = RecordingExclusiveWindowPort()
            val failure = KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "capture-$index")
            val cleanupFailure = KadreFailure.PlatformFailure(
                KadrePlatform.AppKit,
                "exclusive-fullscreen",
                "cleanup-$index",
            )
            val recovery = if (terminal is AppKitExclusiveDisplayTerminal.Released) {
                null
            } else {
                RecordingExclusiveDisplayLease(
                    displayKey = 71L,
                    modeKey = 701L,
                    releaseTerminals = ArrayDeque(listOf(AppKitExclusiveDisplayTerminal.Released(500L))),
                )
            }
            val bridge = object : AppKitExclusiveDisplayBridge {
                override val availability = AppKitExclusiveBridgeAvailability.Available
                override fun open(displayKey: Long, modeKey: Long) = AppKitExclusiveDisplayOpenResult.FailedAfterCapture(
                    terminal = terminal,
                    cleanup = AppKitExclusiveDisplayReleaseResult(terminal, listOf(cleanupFailure)),
                    recovery = recovery,
                    failure = failure,
                )
            }
            val broker = AppKitExclusiveDisplayBroker(bridge, recoveryExecutor = recoveryExecutor)
            val port = broker.openPort(executor, windowPort)
            val command = command(86L + index, 861L + index, 71L, 701L)

            assertEquals(KadreResult.Success(Unit), port.reserve(command))
            executor.runAll()

            assertEquals(1, windowPort.exitRequests.size)
            assertEquals(failure, command.failures.single().first)
            assertTrue(windowPort.diagnostics.contains(cleanupFailure))
            if (recovery == null) {
                assertEquals(ExclusiveFullscreenAvailability.Available, port.availability)
            } else {
                assertIs<ExclusiveFullscreenAvailability.Unavailable>(port.availability)
                assertEquals(0, recovery.releaseCount)
                port.close()
                assertEquals(1, recoveryExecutor.pendingTaskCount)
                recoveryExecutor.runAll()
                assertEquals(1, recovery.releaseCount)
            }
            port.close()
        }
    }

    @Test
    fun entryPreparesPresentationBeforeCaptureAndPresentsOnlyAfterCapture() {
        val trace = mutableListOf<String>()
        val bridge = RecordingExclusiveDisplayBridge(openCapturedLeases = true, trace = trace)
        val executor = QueuedExclusiveExecutor()
        val broker = AppKitExclusiveDisplayBroker(bridge)
        val port = broker.openPort(executor, RecordingExclusiveWindowPort(trace))

        assertEquals(KadreResult.Success(Unit), port.reserve(command(90L, 901L, 71L, 701L)))
        executor.runAll()

        assertEquals(listOf("prepare", "capture", "present", "presentation-readback", "coregraphics-readback"), trace)
    }

    @Test
    fun exitReleasesCoreGraphicsBeforeRestoringPresentation() {
        val trace = mutableListOf<String>()
        val bridge = RecordingExclusiveDisplayBridge(openCapturedLeases = true, trace = trace)
        val executor = QueuedExclusiveExecutor()
        val broker = AppKitExclusiveDisplayBroker(bridge)
        val port = broker.openPort(executor, RecordingExclusiveWindowPort(trace))
        val enter = command(91L, 911L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), port.reserve(enter))
        executor.runAll()
        port.release(
            RecordingExclusiveCommand(
                windowId = enter.windowId,
                operationId = identity<WindowOperationId>(912L),
                displayKey = enter.displayKey,
                modeKey = enter.modeKey,
                requestedFullscreen = FullscreenMode.Windowed,
            ),
        )
        executor.runAll()

        assertEquals(
            listOf("prepare", "capture", "present", "presentation-readback", "coregraphics-readback", "release", "restore"),
            trace,
        )
    }

    @Test
    fun closingLastIdlePortReleasesObservationAndFutureOpenInstallsFreshSnapshot() {
        val displayNative = RecordingBrokerDisplayNative(displaySnapshot(71L, 500L))
        val displayBroker = AppKitDisplayBroker(displayNative, ImmediateDisplayDispatcher)
        val broker = AppKitExclusiveDisplayBroker(RecordingExclusiveDisplayBridge(), displayBroker)

        val first = broker.openPort(QueuedExclusiveExecutor(), UnusedExclusiveWindowPort)
        assertEquals(1, displayNative.observationOpenCount)
        assertEquals(1, displayNative.snapshotCount)

        first.close()

        assertEquals(1, displayNative.observationCloseCount)
        val second = broker.openPort(QueuedExclusiveExecutor(), UnusedExclusiveWindowPort)
        assertEquals(2, displayNative.observationOpenCount)
        assertEquals(2, displayNative.snapshotCount)

        second.close()
        assertEquals(2, displayNative.observationCloseCount)
    }

    @Test
    fun closingLastPortAfterConfirmedReleaseReleasesObservationExactlyOnce() {
        val displayNative = RecordingBrokerDisplayNative(displaySnapshot(71L, 701L))
        val displayBroker = AppKitDisplayBroker(displayNative, ImmediateDisplayDispatcher)
        val bridge = RecordingExclusiveDisplayBridge(openCapturedLeases = true)
        val executor = QueuedExclusiveExecutor()
        val broker = AppKitExclusiveDisplayBroker(bridge, displayBroker)
        val port = broker.openPort(executor, RecordingExclusiveWindowPort())
        val enter = command(20L, 201L, 71L, 701L)
        assertEquals(KadreResult.Success(Unit), port.reserve(enter))
        executor.runAll()
        port.release(
            RecordingExclusiveCommand(
                windowId = enter.windowId,
                operationId = identity<WindowOperationId>(202L),
                displayKey = enter.displayKey,
                modeKey = enter.modeKey,
                requestedFullscreen = FullscreenMode.Windowed,
            ),
        )
        executor.runAll()

        assertEquals(0, displayNative.observationCloseCount)
        port.close()

        assertEquals(1, displayNative.observationCloseCount)
        port.close()
        assertEquals(1, displayNative.observationCloseCount)
    }

    @Test
    fun activeEntryRetainsObservationAfterLastPortClosesUntilReleaseTerminal() {
        val displayNative = RecordingBrokerDisplayNative(displaySnapshot(71L, 701L))
        val displayBroker = AppKitDisplayBroker(displayNative, ImmediateDisplayDispatcher)
        val bridge = RecordingExclusiveDisplayBridge(openCapturedLeases = true)
        val executor = QueuedExclusiveExecutor()
        val broker = AppKitExclusiveDisplayBroker(bridge, displayBroker)
        val port = broker.openPort(executor, RecordingExclusiveWindowPort())
        assertEquals(KadreResult.Success(Unit), port.reserve(command(21L, 211L, 71L, 701L)))
        executor.runAll()

        port.close()

        assertEquals(0, displayNative.observationCloseCount)
        executor.runAll()
        assertEquals(1, bridge.leases.single().releaseCount)
        assertEquals(1, displayNative.observationCloseCount)
    }

    @Test
    fun quarantinedEntryRetainsObservationUntilRecoveryConfirmsRelease() {
        val displayNative = RecordingBrokerDisplayNative(displaySnapshot(71L, 701L))
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
        val recoveryExecutor = QueuedExclusiveExecutor()
        val executor = QueuedExclusiveExecutor()
        val broker = AppKitExclusiveDisplayBroker(
            RecordingExclusiveDisplayBridge(leasesToOpen = ArrayDeque(listOf(lease))),
            displayBroker,
            recoveryExecutor,
        )
        val port = broker.openPort(executor, RecordingExclusiveWindowPort())
        val enter = command(22L, 221L, 71L, 701L)
        assertEquals(KadreResult.Success(Unit), port.reserve(enter))
        executor.runAll()
        port.release(
            RecordingExclusiveCommand(
                windowId = enter.windowId,
                operationId = identity<WindowOperationId>(222L),
                displayKey = enter.displayKey,
                modeKey = enter.modeKey,
                requestedFullscreen = FullscreenMode.Windowed,
            ),
        )
        executor.runAll()

        port.close()

        assertEquals(0, displayNative.observationCloseCount)
        assertEquals(1, recoveryExecutor.pendingTaskCount)
        recoveryExecutor.runAll()
        assertEquals(2, lease.releaseCount)
        assertEquals(1, displayNative.observationCloseCount)
    }

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

private fun AppKitExclusiveDisplayBroker.orphanedRecoveryFailureCodes(): List<String> {
    @Suppress("UNCHECKED_CAST")
    val recoveries = javaClass.getDeclaredField("orphanedRecoveries").apply { isAccessible = true }
        .get(this) as Map<Any, Any>
    return recoveries.values.map { recovery ->
        (recovery.javaClass.getDeclaredField("terminalFailure").apply { isAccessible = true }
            .get(recovery) as KadreFailure.PlatformFailure).code
    }
}

private class RecordingExclusiveDisplayBridge(
    private val openCapturedLeases: Boolean = false,
    private val leasesToOpen: ArrayDeque<RecordingExclusiveDisplayLease> = ArrayDeque(),
    private val trace: MutableList<String>? = null,
) : AppKitExclusiveDisplayBridge {
    val opens = mutableListOf<Pair<Long, Long>>()
    val leases = mutableListOf<RecordingExclusiveDisplayLease>()

    override val availability = AppKitExclusiveBridgeAvailability.Available

    override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
        trace?.add("capture")
        opens += displayKey to modeKey
        if (leasesToOpen.isNotEmpty()) {
            return AppKitExclusiveDisplayOpenResult.Opened(leasesToOpen.removeFirst().also(leases::add))
        }
        if (openCapturedLeases) {
            return AppKitExclusiveDisplayOpenResult.Opened(
                RecordingExclusiveDisplayLease(displayKey, modeKey, trace = trace).also(leases::add),
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
    private val captureCommittedValue: Boolean = true,
) : AppKitExclusiveBrokerCommand {
    val completions = mutableListOf<WindowState>()
    val failures = mutableListOf<Pair<KadreFailure, WindowState?>>()

    override fun captureCommitted(): Boolean = captureCommittedValue

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
    private val trace: MutableList<String>? = null,
    private val releaseFailure: Throwable? = null,
    private val releaseFailureSequence: ArrayDeque<Throwable?> = ArrayDeque(),
    private val readbackFailure: Throwable? = null,
    private val releaseFailures: List<KadreFailure.PlatformFailure> = emptyList(),
) : AppKitExclusiveDisplayLease {
    var releaseCount = 0
        private set

    override fun readback(): AppKitExclusiveDisplayReadback {
        readbackFailure?.let { throw it }
        return AppKitExclusiveDisplayReadback(AppKitExclusiveDisplayTerminal.Captured(modeKey)).also {
            trace?.add("coregraphics-readback")
        }
    }

    override fun release(): AppKitExclusiveDisplayReleaseResult {
        trace?.add("release")
        releaseCount += 1
        if (releaseFailureSequence.isNotEmpty()) releaseFailureSequence.removeFirst()?.let { throw it }
        releaseFailure?.let { throw it }
        return AppKitExclusiveDisplayReleaseResult(
            if (releaseTerminals.isEmpty()) {
                AppKitExclusiveDisplayTerminal.Released(modeKey)
            } else {
                releaseTerminals.removeFirst()
            },
            releaseFailures,
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

private class RecordingExclusiveWindowPort(
    private val trace: MutableList<String>? = null,
    private val failEnter: Boolean = false,
    private val failExit: Boolean = false,
) : AppKitExclusiveWindowPort {
    val enterRequests = mutableListOf<AppKitExclusiveWindowRequest>()
    val exitRequests = mutableListOf<AppKitExclusiveWindowRequest>()
    val terminalized = mutableListOf<WindowId>()
    val diagnostics = mutableListOf<KadreFailure.PlatformFailure>()

    override fun prepare(request: AppKitExclusiveWindowRequest): AppKitExclusiveWindowPreparation {
        trace?.add("prepare")
        return AppKitExclusiveWindowPreparation.Prepared
    }

    override fun enter(request: AppKitExclusiveWindowRequest): AppKitExclusiveWindowResult {
        trace?.add("present")
        enterRequests += request
        if (failEnter) return AppKitExclusiveWindowResult.Failed(
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "present-failed"),
            null,
        )
        trace?.add("presentation-readback")
        return AppKitExclusiveWindowResult.Read(windowState(request.requestedFullscreen))
    }

    override fun exit(request: AppKitExclusiveWindowRequest): AppKitExclusiveWindowResult {
        trace?.add("restore")
        exitRequests += request
        if (failExit) return AppKitExclusiveWindowResult.Failed(
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "restore-failed"),
            null,
        )
        return AppKitExclusiveWindowResult.Read(windowState(FullscreenMode.Windowed))
    }

    override fun terminalize(windowId: WindowId) {
        terminalized += windowId
    }

    override fun reportDiagnostic(failure: KadreFailure.PlatformFailure) {
        diagnostics += failure
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
    var observationOpenCount = 0
        private set
    var observationCloseCount = 0
        private set
    var snapshotCount = 0
        private set
    var reconfigurationCount = 0
        private set
    var snapshotFailure: Throwable? = null

    override val enumerationCapability = org.graphiks.kadre.diagnostics.Capability.Supported(
        Unit,
        org.graphiks.kadre.diagnostics.FeatureAvailability.Available,
    )

    override fun snapshot(): DisplayPortSnapshot {
        snapshotCount += 1
        return snapshotFailure?.let { throw it } ?: snapshot
    }

    override fun observeReconfiguration(listener: () -> Unit): AutoCloseable {
        check(this.listener == null)
        observationOpenCount += 1
        this.listener = listener
        return AutoCloseable {
            if (this.listener === listener) {
                this.listener = null
                observationCloseCount += 1
            }
        }
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
