package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import kotlin.test.*

class RuntimeAdvancedWindowTest {
    private fun image(value: Int = 1) = BinaryImage(ByteArray(16) { value.toByte() }, ImageFormat.Rgba8, PhysicalSize(2, 2))
    @Test fun imageBudgetCountsIdentityAndReleasesBothPendingAndCurrentLeases() {
        val budget = RuntimeImageBudget(KadrePolicies.Default.resources.copy(maxImageBytesPerResource = 16, maxRetainedPayloadBytesPerSession = 32, maxDropChunkBytes = 16))
        val first = image(); val second = image(2)
        val a = assertIs<KadreResult.Success<RuntimeImageLease>>(budget.retain(first)).value
        val a2 = assertIs<KadreResult.Success<RuntimeImageLease>>(budget.retain(first)).value
        val b = assertIs<KadreResult.Success<RuntimeImageLease>>(budget.retain(second)).value
        assertIs<KadreResult.Failure>(budget.retain(image(3)))
        a.close(); assertIs<KadreResult.Failure>(budget.retain(image(3)))
        a2.close(); val c = assertIs<KadreResult.Success<RuntimeImageLease>>(budget.retain(image(3))).value
        b.close(); c.close(); c.close()
        assertIs<KadreResult.Success<RuntimeImageLease>>(budget.retain(image(4))).value.close()
    }

    @Test fun iconOptInPublishesSetAndClearAndRestrictsNativeLevelConstraints() = runBlocking<Unit> {
        lateinit var manager: RuntimeWindowManager
        val opens = mutableListOf<WindowOpenCommand>()
        val port = object : WindowCommandPort {
            override fun requestOpen(command: WindowOpenCommand) { opens += command; command.commit(WindowPeerOwner {}) }
            override fun requestUpdate(command: WindowUpdateCommand) {
                val current = manager.state.value.windows.single().state.value
                val icon = when(val change = command.update.icon) { is PropertyChange.Set -> change.value; PropertyChange.Clear -> null; else -> current.icon }
                command.applied(current.copy(icon = icon))
            }
            override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome { opens.single().nativeClosed(); return OpenedWindowCloseOutcome.Accepted }
            override fun requestPendingCancellation(command: PendingWindowCancellationCommand) = PendingWindowCancellationOutcome.CancelledBeforeCommit
        }
        manager = RuntimeWindowManager(KadrePolicies.Default.resources, port, platform = KadrePlatform.Win32, failureReporter = RuntimeFailureReporter {},
            publicWindowCapabilities = true, enabledWindowUpdateCapabilities = setOf(WindowProperty.Icon, WindowProperty.Level),
            acceptedWindowLevels = setOf(WindowLevel.Normal, WindowLevel.Floating),
            windowIconConstraints = ImageConstraints(PhysicalSize(256, 256), setOf(ImageFormat.Rgba8)))
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(manager.requestWindow(WindowSpec(icon = image()))).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            assertEquals(setOf(WindowLevel.Normal, WindowLevel.Floating), assertIs<Capability.Supported<Set<WindowLevel>>>(window.capabilities.value.level).constraints)
            val events = mutableListOf<WindowEvent>()
            val collecting = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { window.events.collect { events += it } }
            val next = image(2)
            val applied = assertIs<KadreResult.Success<WindowUpdateOutcome>>(window.apply(WindowUpdate(icon = PropertyChange.Set(next))))
            assertIs<WindowUpdateOutcome.Applied>(applied.value)
            assertEquals(next, window.state.value.icon)
            assertEquals(setOf(WindowProperty.Icon), events.filterIsInstance<WindowEvent.PropertiesChanged>().single().changed)
            assertIs<KadreResult.Success<WindowUpdateOutcome>>(window.apply(WindowUpdate(icon = PropertyChange.Clear)))
            assertNull(window.state.value.icon)
            collecting.cancel()
            window.close()
        } finally { manager.close() }
    }

    @Test fun captureLossPublishesNoneAndRevokesAnInflightConfinedAcknowledgement() = runBlocking {
        val id = RuntimeProcessIds.nextSurfaceId()
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val capabilities = unsupportedSurfaceCapabilities().copy(pointerCapture = Capability.Supported(
            setOf(PointerCaptureMode.None, PointerCaptureMode.Confined), FeatureAvailability.Available))
        val surface = RuntimeWindowSurface(id, SurfaceInitialSnapshot(SurfaceMetrics(LogicalSize(100.0, 100.0), PhysicalSize(100, 100), 1.0, LogicalInsets(0.0,0.0,0.0,0.0)),
            SurfaceFocus.Focused, SurfaceVisibility.Visible, SurfaceOcclusion.Unknown, SurfaceAppearance(SurfaceTheme.Light, SurfaceContrast.Normal)),
            commandPort = object : SurfaceCommandPort {
                override fun requestRedraw(command: SurfaceRedrawCommand) = KadreResult.Success(Unit)
                override suspend fun apply(command: SurfaceUpdateCommand): KadreResult<SurfaceUpdateCommandOutcome> {
                    started.complete(Unit); release.await()
                    return KadreResult.Success(SurfaceUpdateCommandOutcome(pointerCapture = SurfaceFieldOutcome.Applied(PointerCaptureMode.Confined)))
                }
            }, commandsEnabled = true, enabledCapabilities = capabilities,
            eventStampSource = { org.graphiks.kadre.application.EventStamp(org.graphiks.kadre.application.SessionSequence(1), org.graphiks.kadre.application.SessionInstant(kotlin.time.Duration.ZERO), null) })
        val pending = async(start = CoroutineStart.UNDISPATCHED) { surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))) }
        started.await()
        assertTrue(surface.accept(SurfaceStimulus.PointerCaptureChanged(id, PointerCaptureMode.None)))
        release.complete(Unit)
        val outcome = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(pending.await()).value
        assertIs<SurfaceUpdateOutcome.PartiallyApplied>(outcome)
        assertEquals(PointerCaptureMode.None, surface.state.value.pointerCapture)
        assertEquals(SurfaceFocus.Focused, surface.state.value.focus, "capture loss does not invent focus loss")
        surface.detach()
        assertFalse(surface.accept(SurfaceStimulus.PointerCaptureChanged(id, PointerCaptureMode.Confined)))
    }

    @Test fun queuedIconCancellationAndCloseReturnTheSessionImageBudget() = runBlocking<Unit> {
        lateinit var manager: RuntimeWindowManager
        val opens = mutableListOf<WindowOpenCommand>(); val updates = mutableListOf<WindowUpdateCommand>()
        val port = object : WindowCommandPort {
            override fun requestOpen(command: WindowOpenCommand) { opens += command; command.commit(WindowPeerOwner {}) }
            override fun requestUpdate(command: WindowUpdateCommand) { updates += command }
            override fun requestUpdateCancellation(command: WindowUpdateCancellationCommand) = WindowUpdateCancellationOutcome.CancelledBeforeCommit
            override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome { opens.first { it.windowId == command.windowId }.nativeClosed(); return OpenedWindowCloseOutcome.Accepted }
            override fun requestPendingCancellation(command: PendingWindowCancellationCommand) = PendingWindowCancellationOutcome.CancelledBeforeCommit
        }
        manager = RuntimeWindowManager(KadrePolicies.Default.resources.copy(maxImageBytesPerResource = 16, maxRetainedPayloadBytesPerSession = 32, maxDropChunkBytes = 16), port,
            platform = KadrePlatform.Win32, failureReporter = RuntimeFailureReporter {}, publicWindowCapabilities = true,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Icon), windowIconConstraints = ImageConstraints(PhysicalSize(256, 256), setOf(ImageFormat.Rgba8)))
        try {
            suspend fun open(icon: BinaryImage): Window = assertIs<WindowRequestOutcome.OpenedHere>(assertIs<KadreResult.Success<WindowRequest>>(manager.requestWindow(WindowSpec(icon = icon))).value.await()).window
            val window = open(image())
            val pending = async(start = CoroutineStart.UNDISPATCHED) { window.apply(WindowUpdate(icon = PropertyChange.Set(image(2)))) }
            assertEquals(1, updates.size)
            assertIs<KadreResult.Failure>(manager.requestWindow(WindowSpec(icon = image(3))))
            pending.cancelAndJoin()
            val second = open(image(3))
            window.close(); second.close()
            open(image(4)).close()
        } finally { manager.close() }
    }

    @Test fun unexpectedInitialNativeImageCannotBypassBudgetOrLeaveAnUnfinishedRequest() = runBlocking<Unit> {
        val port = object : WindowCommandPort {
            override fun requestOpen(command: WindowOpenCommand) { command.commit(WindowPeerOwner {}, command.spec.copy(icon = image(2))) }
            override fun requestUpdate(command: WindowUpdateCommand) = Unit
            override fun requestOpenedClose(command: OpenedWindowCloseCommand) = OpenedWindowCloseOutcome.Accepted
            override fun requestPendingCancellation(command: PendingWindowCancellationCommand) = PendingWindowCancellationOutcome.CancelledBeforeCommit
        }
        RuntimeWindowManager(KadrePolicies.Default.resources.copy(maxImageBytesPerResource = 16, maxRetainedPayloadBytesPerSession = 16, maxDropChunkBytes = 16), port,
            platform = KadrePlatform.Win32, failureReporter = RuntimeFailureReporter {}, publicWindowCapabilities = true,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Icon), windowIconConstraints = ImageConstraints(PhysicalSize(256, 256), setOf(ImageFormat.Rgba8))).use { manager ->
            val request = assertIs<KadreResult.Success<WindowRequest>>(manager.requestWindow(WindowSpec(icon = image()))).value
            assertIs<WindowRequestOutcome.Rejected>(withTimeout(500) { request.await() })
        }
    }

    @Test fun iconsAndCursorsShareOneSessionBudgetAndDetachReleasesBoth() = runBlocking<Unit> {
        val opens = mutableListOf<WindowOpenCommand>()
        val port = object : WindowCommandPort {
            override fun requestOpen(command: WindowOpenCommand) {
                opens += command
                command.commit(WindowPeerOwner {}, initialSurfaceSnapshot = SurfaceInitialSnapshot(
                    SurfaceMetrics(LogicalSize(100.0, 100.0), PhysicalSize(100, 100), 1.0, LogicalInsets(0.0, 0.0, 0.0, 0.0)),
                    SurfaceFocus.Focused, SurfaceVisibility.Visible, SurfaceOcclusion.Unknown, SurfaceAppearance(SurfaceTheme.Light, SurfaceContrast.Normal)))
            }
            override fun requestUpdate(command: WindowUpdateCommand) = Unit
            override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome { opens.first { it.windowId == command.windowId }.nativeClosed(); return OpenedWindowCloseOutcome.Accepted }
            override fun requestPendingCancellation(command: PendingWindowCancellationCommand) = PendingWindowCancellationOutcome.CancelledBeforeCommit
        }
        val surfacePort = object : SurfaceCommandPort {
            override fun requestRedraw(command: SurfaceRedrawCommand) = KadreResult.Success(Unit)
            override suspend fun apply(command: SurfaceUpdateCommand) = KadreResult.Success(SurfaceUpdateCommandOutcome(
                cursor = (command.cursor as? PropertyChange.Set)?.let { SurfaceFieldOutcome.Applied(it.value) } ?: SurfaceFieldOutcome.Unchanged))
        }
        val resources = KadrePolicies.Default.resources.copy(maxImageBytesPerResource = 16, maxRetainedPayloadBytesPerSession = 32, maxDropChunkBytes = 16)
        RuntimeWindowManager(resources, port, surfacePort, KadrePlatform.Win32, RuntimeFailureReporter {}, publicWindowCapabilities = true,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Icon), windowIconConstraints = ImageConstraints(PhysicalSize(256, 256), setOf(ImageFormat.Rgba8)),
            publicSurfaceCapabilities = true, enabledSurfaceCapabilities = unsupportedSurfaceCapabilities().copy(
                customCursor = Capability.Supported(ImageConstraints(PhysicalSize(128, 128), setOf(ImageFormat.Rgba8)), FeatureAvailability.Available))).use { manager ->
            val window = assertIs<WindowRequestOutcome.OpenedHere>(assertIs<KadreResult.Success<WindowRequest>>(manager.requestWindow(WindowSpec(icon = image()))).value.await()).window
            val cursor = CursorStyle.Custom(CursorImage(image(2), PhysicalPoint(0, 0)))
            assertIs<SurfaceUpdateOutcome.Applied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(cursor)))).value)
            assertIs<KadreResult.Failure>(manager.requestWindow(WindowSpec(icon = image(3))))
            val rejected = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(
                cursor = PropertyChange.Set(CursorStyle.Custom(CursorImage(image(3), PhysicalPoint(0, 0))))))).value)
            assertIs<KadreFailure.ResourceLimitExceeded>(rejected.rejected.single().failure)
            window.close()
            val reopened = assertIs<WindowRequestOutcome.OpenedHere>(assertIs<KadreResult.Success<WindowRequest>>(manager.requestWindow(WindowSpec(icon = image(4)))).value.await()).window
            assertIs<SurfaceUpdateOutcome.Applied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(reopened.surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(cursor)))).value)
        }
    }

    @Test fun anUnexpectedUpdateImageFailsTheCommandInsteadOfAbandoningItsWaiter() = runBlocking<Unit> {
        lateinit var manager: RuntimeWindowManager
        var opened: WindowOpenCommand? = null
        val diagnostics = mutableListOf<Throwable>()
        val port = object : WindowCommandPort {
            override fun requestOpen(command: WindowOpenCommand) { opened = command; command.commit(WindowPeerOwner {}) }
            override fun requestUpdate(command: WindowUpdateCommand) { command.applied(manager.state.value.windows.single().state.value.copy(icon = image(3))) }
            override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome { opened!!.nativeClosed(); return OpenedWindowCloseOutcome.Accepted }
            override fun requestPendingCancellation(command: PendingWindowCancellationCommand) = PendingWindowCancellationOutcome.CancelledBeforeCommit
        }
        manager = RuntimeWindowManager(KadrePolicies.Default.resources.copy(maxImageBytesPerResource = 16, maxRetainedPayloadBytesPerSession = 32, maxDropChunkBytes = 16), port,
            platform = KadrePlatform.Win32, failureReporter = RuntimeFailureReporter { diagnostics += it }, publicWindowCapabilities = true,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Icon), windowIconConstraints = ImageConstraints(PhysicalSize(256, 256), setOf(ImageFormat.Rgba8)))
        try {
            val initial = image()
            val window = assertIs<WindowRequestOutcome.OpenedHere>(assertIs<KadreResult.Success<WindowRequest>>(manager.requestWindow(WindowSpec(icon = initial))).value.await()).window
            assertIs<KadreResult.Failure>(withTimeout(1000) { window.apply(WindowUpdate(icon = PropertyChange.Set(image(2)))) })
            assertEquals(initial, window.state.value.icon); assertTrue(diagnostics.isNotEmpty())
        } finally { manager.close() }
    }
}
