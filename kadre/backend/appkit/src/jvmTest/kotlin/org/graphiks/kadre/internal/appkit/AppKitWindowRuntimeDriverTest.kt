package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.RuntimeDesktopWindowHandleAccess
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppKitWindowRuntimeDriverTest {
    @Test
    fun surfaceCallbacksAfterPeerActivationAndBeforeCommitAreDrainedSequentiallyAfterInitialSnapshot() = runBlocking {
        val initial = deterministicSurfaceSnapshot().copy(focus = SurfaceFocus.Unfocused)
        val resized = deterministicSurfaceSnapshot(
            logicalSize = LogicalSize(480.0, 270.0),
            scaleFactor = 1.5,
        ).metrics
        val port = DeterministicAppKitNativeWindowPort(
            name = "pre-commit-surface-callback",
            initialSurfaceSnapshot = initial,
            afterSurfaceActivationBeforeCommit = { native ->
                native.emitSurfaceFocus("pre-commit", SurfaceFocus.Focused)
                native.emitSurfaceMetrics("pre-commit", resized)
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
            publicSurfaceCapabilities = true,
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "pre-commit"))
                    .successValue()
                    .await(),
            ).window

            val drained = withTimeout(2.seconds) {
                window.surface.state.first { it.revision.value == 2L }
            }
            assertEquals(SurfaceFocus.Focused, drained.focus)
            assertEquals(resized.logicalSize, drained.logicalSize)
            assertEquals(resized.physicalSize, drained.physicalSize)
        } finally {
            driver.close()
        }
    }

    @Test
    fun asynchronousDrainReservationKeepsWorkerAliveUntilCleanupIsSealed() {
        val taskStarted = CountDownLatch(1)
        val allowTaskToFinish = CountDownLatch(1)
        val taskFinished = CountDownLatch(1)
        val cleanupFinished = CountDownLatch(1)
        val queue = AppKitWindowCommandQueue { throw AssertionError(it) }

        assertTrue(queue.submit {
            taskStarted.countDown()
            check(allowTaskToFinish.await(2, TimeUnit.SECONDS))
            taskFinished.countDown()
        })
        assertTrue(taskStarted.await(2, TimeUnit.SECONDS))
        assertFalse(queue.beginMainThreadDrain())

        allowTaskToFinish.countDown()
        assertTrue(taskFinished.await(2, TimeUnit.SECONDS))
        assertTrue(queue.submitFollowUp { cleanupFinished.countDown() })

        queue.finishAsynchronousDrain()

        assertTrue(cleanupFinished.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun closeAbortsPreparedPeerThenClosesCommittedPeersInReverseAdmissionOrder() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort("session")
        val driver = AppKitWindowRuntimeDriverFactory { port }
            .create(KadrePolicies.Default.resources)
        driver.manager.requestWindow(WindowSpec(title = "first")).successValue().awaitOpened()
        driver.manager.requestWindow(WindowSpec(title = "second")).successValue().awaitOpened()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "pending"))
        }

        assertFalse(pending.isCompleted)
        assertEquals(listOf("first", "second", "pending"), port.createdWindowTitles)

        driver.close()
        yield()

        assertEquals(WindowRequestOutcome.RequesterDetached, pending.await().successValue().await())
        assertEquals(listOf("pending", "second", "first"), port.closedWindowTitles)
        assertEquals(emptyList(), driver.manager.state.value.windows)
    }

    @Test
    fun closeAbortsInFlightPendingPeerBeforeCommittedPeers() = runBlocking {
        val preparationStarted = CountDownLatch(1)
        val allowPreparation = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort(
            name = "in-flight-pending",
            beforeCreateWindow = { spec ->
                if (spec.title == "pending") {
                    preparationStarted.countDown()
                    check(allowPreparation.await(2, TimeUnit.SECONDS))
                }
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }
            .create(KadrePolicies.Default.resources)
        driver.manager.requestWindow(WindowSpec(title = "first")).successValue().awaitOpened()
        driver.manager.requestWindow(WindowSpec(title = "second")).successValue().awaitOpened()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "pending"))
        }

        try {
            assertTrue(preparationStarted.await(2, TimeUnit.SECONDS))

            driver.close()
            allowPreparation.countDown()

            assertEquals(WindowRequestOutcome.RequesterDetached, pending.await().successValue().await())
            withTimeout(2.seconds) {
                driver.manager.state.first { state -> state.windows.isEmpty() }
                while (port.closedWindowTitles.size < 3) yield()
            }
            assertEquals(listOf("pending", "second", "first"), port.closedWindowTitles)
        } finally {
            allowPreparation.countDown()
            driver.close()
        }
    }

    @Test
    fun closeReservesCommitIssuedPendingCleanupBeforeCommittedPeers() = runBlocking {
        val commitReserved = CountDownLatch(1)
        val allowCommitDelivery = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort("commit-issued-pending")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            beforeCommitDelivery = { spec ->
                if (spec.title == "pending") {
                    commitReserved.countDown()
                    check(allowCommitDelivery.await(2, TimeUnit.SECONDS))
                }
            },
        )
        driver.manager.requestWindow(WindowSpec(title = "first")).successValue().awaitOpened()
        driver.manager.requestWindow(WindowSpec(title = "second")).successValue().awaitOpened()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "pending"))
        }

        try {
            assertTrue(commitReserved.await(2, TimeUnit.SECONDS))
            val pendingRequest = withTimeout(2.seconds) { pending.await().successValue() }
            assertEquals(
                org.graphiks.kadre.window.WindowCancellationOutcome.TooLate,
                pendingRequest.cancel(),
            )

            driver.close()
            allowCommitDelivery.countDown()

            assertEquals(WindowRequestOutcome.RequesterDetached, pendingRequest.await())
            withTimeout(2.seconds) {
                while (port.closedWindowTitles.size < 3) yield()
            }
            assertEquals(listOf("pending", "second", "first"), port.closedWindowTitles)
        } finally {
            allowCommitDelivery.countDown()
            driver.close()
        }
    }

    @Test
    fun requesterCancellationAfterCommitReservationDoesNotRollBackTheOpenedWindow() = runBlocking {
        val commitReserved = CountDownLatch(1)
        val allowCommitDelivery = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort("commit-issued-cancellation")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
            beforeCommitDelivery = { spec ->
                if (spec.title == "committing") {
                    commitReserved.countDown()
                    check(allowCommitDelivery.await(2, TimeUnit.SECONDS))
                }
            },
        )
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "committing"))
        }

        try {
            assertTrue(commitReserved.await(2, TimeUnit.SECONDS))
            val request = withTimeout(2.seconds) { pending.await().successValue() }

            assertEquals(org.graphiks.kadre.window.WindowCancellationOutcome.TooLate, request.cancel())
            allowCommitDelivery.countDown()

            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                withTimeout(2.seconds) { request.await() },
            ).window
            val access = assertIs<RuntimeDesktopWindowHandleAccess>(window)
            assertEquals(
                KadreResult.Success(Unit),
                access.withDesktopHandle { },
            )
            assertEquals(emptyList(), port.closedWindowTitles)
            assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }
            Unit
        } finally {
            allowCommitDelivery.countDown()
            driver.close()
        }
    }

    @Test
    fun factoryCreatesOneNativePortAndOneManagerForEachDriver() = runBlocking {
        val ports = ArrayDeque(
            listOf(
                DeterministicAppKitNativeWindowPort("first"),
                DeterministicAppKitNativeWindowPort("second"),
            ),
        )
        val createdPorts = mutableListOf<DeterministicAppKitNativeWindowPort>()
        val factory = AppKitWindowRuntimeDriverFactory {
            ports.removeFirst().also(createdPorts::add)
        }
        val first = factory.create(KadrePolicies.Default.resources)
        val second = factory.create(KadrePolicies.Default.resources)

        try {
            first.manager.requestWindow(WindowSpec(title = "only-first")).successValue().awaitOpened()

            assertEquals(2, createdPorts.size)
            assertEquals(listOf("only-first"), createdPorts[0].createdWindowTitles)
            assertEquals(emptyList(), createdPorts[1].createdWindowTitles)
            assertEquals(1, first.manager.state.value.windows.size)
            assertEquals(emptyList(), second.manager.state.value.windows)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun mainThreadNativeCloseCannotDeadlockAConcurrentWindowOpen() = runBlocking {
        val port = OwnerThreadAppKitNativeWindowPort("concurrent")
        val driver = AppKitWindowRuntimeDriverFactory { port }
            .create(KadrePolicies.Default.resources)
        val requestExecutor = newDaemonSingleThreadExecutor("kadre-request-test")
        val callbackEntered = CountDownLatch(1)
        val allowCallback = CountDownLatch(1)
        var completed = false
        var callback: Future<*>? = null
        var secondOpen: Future<*>? = null

        try {
            driver.manager.requestWindow(WindowSpec(title = "first")).successValue().awaitOpened()
            callback = port.submitOnOwnerThread {
                callbackEntered.countDown()
                check(allowCallback.await(2, TimeUnit.SECONDS))
                port.emitNativeClosed("first")
            }
            assertTrue(callbackEntered.await(2, TimeUnit.SECONDS))
            val foreignMainThreadCall = port.observeNextForeignMainThreadCall()
            secondOpen = requestExecutor.submit {
                runBlocking {
                    driver.manager.requestWindow(WindowSpec(title = "second"))
                        .successValue()
                        .awaitOpened()
                }
            }
            assertTrue(foreignMainThreadCall.await(2, TimeUnit.SECONDS))

            allowCallback.countDown()

            callback.get(2, TimeUnit.SECONDS)
            secondOpen.get(2, TimeUnit.SECONDS)
            withTimeout(2.seconds) {
                driver.manager.state.first { state ->
                    state.windows.singleOrNull()?.state?.value?.title == "second"
                }
            }
            completed = true
        } finally {
            allowCallback.countDown()
            callback?.cancel(true)
            secondOpen?.cancel(true)
            requestExecutor.shutdownNow()
            if (completed) driver.close()
            port.close()
        }
    }

    @Test
    fun programmaticClosePublishesOneTerminalNativeCloseAndReleasesTheWindowSlot() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort("programmatic")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources.copy(maxWindowsPerSession = 1),
        )

        try {
            val request = driver.manager.requestWindow(WindowSpec(title = "first")).successValue()
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window

            assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }

            assertEquals(emptyList(), driver.manager.state.value.windows)
            assertEquals(listOf("first"), port.closedWindowTitles)
            driver.manager.requestWindow(WindowSpec(title = "replacement")).successValue().awaitOpened()
        } finally {
            driver.close()
        }
    }

    @Test
    fun openedCleanupFailureStillTerminalisesOnceAndIsNeverPresentedAsReusable() = runBlocking {
        val cleanupFailure = IllegalStateException("native close failed")
        val port = DeterministicAppKitNativeWindowPort(
            name = "cleanup-failure",
            closeFailures = mapOf("failing" to cleanupFailure),
        )
        val reported = mutableListOf<Throwable>()
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            RuntimeFailureReporter(reported::add),
        )

        try {
            val request = driver.manager.requestWindow(WindowSpec(title = "failing")).successValue()
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window

            assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }

            assertEquals(WindowCloseOutcome.Closed, window.close().successValue())
            assertEquals(emptyList(), driver.manager.state.value.windows)
            assertTrue(reported.contains(cleanupFailure))
        } finally {
            driver.close()
        }
    }

    @Test
    fun pendingCleanupFailureDuringDriverCloseDetachesInsteadOfReportingFalseCancellation() = runBlocking {
        val cleanupFailure = IllegalStateException("pending native close failed")
        val port = DeterministicAppKitNativeWindowPort(
            name = "pending-cleanup-failure",
            closeFailures = mapOf("pending" to cleanupFailure),
        )
        val reported = mutableListOf<Throwable>()
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            RuntimeFailureReporter(reported::add),
        )
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "pending"))
        }

        driver.close()
        yield()

        assertEquals(WindowRequestOutcome.RequesterDetached, pending.await().successValue().await())
        assertTrue(reported.contains(cleanupFailure))
        assertEquals(emptyList(), driver.manager.state.value.windows)
    }

    @Test
    fun nativeMainThreadDriverCloseDoesNotJoinAWorkerWaitingOnThatThread() = runBlocking {
        val port = OwnerThreadAppKitNativeWindowPort("main-thread-close")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)
        driver.manager.requestWindow(WindowSpec(title = "owned")).successValue().awaitOpened()
        val close = port.submitOnOwnerThread(driver::close)

        try {
            close.get(2, TimeUnit.SECONDS)
            assertEquals(listOf("owned"), port.closedWindowTitles)
            assertEquals(emptyList(), driver.manager.state.value.windows)
        } finally {
            close.cancel(true)
            port.close()
        }
    }

    @Test
    fun desktopHandleLeaseRunsOnTheOwnerThreadAndDelaysCloseUntilTheCallbackReturns() = runBlocking {
        val port = OwnerThreadAppKitNativeWindowPort("desktop-handle")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
        )
        val callbackStarted = CountDownLatch(1)
        val allowCallbackToReturn = CountDownLatch(1)

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "leased")).successValue().await(),
            ).window
            val access = assertIs<RuntimeDesktopWindowHandleAccess>(window)
            val lease = async(Dispatchers.Default) {
                access.withDesktopHandle { handle ->
                    assertTrue(port.isMainThread())
                    assertEquals(RuntimeDesktopNativeWindowHandle.AppKit(0xA11uL, 0xB22uL), handle)
                    callbackStarted.countDown()
                    check(allowCallbackToReturn.await(2, TimeUnit.SECONDS))
                    "leased"
                }
            }
            assertTrue(callbackStarted.await(2, TimeUnit.SECONDS))

            assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())
            assertEquals(emptyList(), port.closedWindowTitles)

            allowCallbackToReturn.countDown()
            assertEquals(KadreResult.Success("leased"), lease.await())
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }
            assertEquals(listOf("leased"), port.closedWindowTitles)
            assertEquals(
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)),
                access.withDesktopHandle { error("closed windows must not invoke a callback") },
            )
        } finally {
            allowCallbackToReturn.countDown()
            driver.close()
            port.close()
        }
    }
}

internal class DeterministicAppKitNativeWindowPort(
    private val name: String,
    private val closeFailures: Map<String, Throwable> = emptyMap(),
    private val beforeCreateWindow: (WindowSpec) -> Unit = { },
    private val onDelegateRevoked: (String) -> Unit = { },
    private val beforeCloseWindow: (String) -> Unit = { },
    private val initialSurfaceSnapshot: AppKitSurfaceSnapshot = deterministicSurfaceSnapshot(),
    private val afterSurfaceActivationBeforeCommit: (DeterministicAppKitNativeWindowPort) -> Unit = { },
) : AppKitNativeWindowPort {
    private val windows = linkedMapOf<String, RecordingNativeWindowOwner>()
    private val surfaceObservers = linkedMapOf<String, RecordingNativeSurfaceObserver>()
    val createdWindowTitles = CopyOnWriteArrayList<String>()
    val closedWindowTitles = CopyOnWriteArrayList<String>()
    val windowWillCloseTitles = CopyOnWriteArrayList<String>()
    val createdPeerIds = CopyOnWriteArrayList<AppKitWindowPeerId>()
    val requestedSurfaceRedrawGenerations = CopyOnWriteArrayList<Long>()
    private val surfaceActivationHookDelivered = AtomicBoolean(false)

    override fun isMainThread(): Boolean = true

    override fun <T> onMainThread(block: () -> T): T {
        val result = block()
        if (surfaceObservers.isNotEmpty() && surfaceActivationHookDelivered.compareAndSet(false, true)) {
            afterSurfaceActivationBeforeCommit(this)
        }
        return result
    }

    override fun createWindow(spec: WindowSpec): AppKitNativeWindowOwner {
        beforeCreateWindow(spec)
        return RecordingNativeWindowOwner(spec.title).also { window ->
            check(windows.put(spec.title, window) == null) { "$name duplicate test window title" }
            createdWindowTitles += spec.title
        }
    }

    override fun createContentView(spec: WindowSpec): AppKitNativeViewOwner = RecordingNativeViewOwner()

    override fun createDelegate(
        peerId: AppKitWindowPeerId,
        callbacks: AppKitWindowDelegateCallbacks,
    ): AppKitNativeDelegateOwner = RecordingNativeDelegateOwner(
        peerId,
        createdWindowTitles.last(),
        callbacks,
        onDelegateRevoked,
    ).also {
        createdPeerIds += peerId
    }

    override fun attachContentView(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ) {
        window.recordingWindow().contentView = view.recordingView()
    }

    override fun attachDelegate(
        window: AppKitNativeWindowOwner,
        delegate: AppKitNativeDelegateOwner,
    ) {
        window.recordingWindow().delegate = delegate.recordingDelegate()
    }

    override fun present(window: AppKitNativeWindowOwner) {
        window.recordingWindow().presented = true
    }

    override fun observeSurface(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitSurfaceCallbacks,
    ): AppKitNativeSurfaceObserverOwner = RecordingNativeSurfaceObserver(
        callbacks,
        initialSurfaceSnapshot,
        requestedSurfaceRedrawGenerations::add,
    ).also { observer ->
        check(surfaceObservers.put(window.recordingWindow().title, observer) == null) {
            "$name duplicate test surface observer"
        }
    }

    override fun detachDelegate(window: AppKitNativeWindowOwner) {
        window.recordingWindow().delegateAttached = false
    }

    override fun detachContentView(window: AppKitNativeWindowOwner) {
        window.recordingWindow().contentViewAttached = false
    }

    override fun closeWindow(window: AppKitNativeWindowOwner) {
        val recording = window.recordingWindow()
        beforeCloseWindow(recording.title)
        recordNativeClose(recording)
        closeFailures[recording.title]?.let { throw it }
    }

    override fun desktopHandle(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ): RuntimeDesktopNativeWindowHandle.AppKit = RuntimeDesktopNativeWindowHandle.AppKit(0xA11uL, 0xB22uL)

    fun requestNativeClose(title: String): Boolean =
        checkNotNull(windows[title]?.delegate).callbacks.windowShouldClose()

    fun emitNativeClosed(title: String) {
        recordNativeClose(checkNotNull(windows[title]))
    }

    fun emitSurfaceMetrics(title: String, metrics: SurfaceMetrics) {
        checkNotNull(surfaceObservers[title]).emitMetrics(metrics)
    }

    fun emitSurfaceFocus(title: String, focus: SurfaceFocus) {
        checkNotNull(surfaceObservers[title]).emitFocus(focus)
    }

    fun emitSurfaceRedrawConsumed(title: String, generation: Long) {
        checkNotNull(surfaceObservers[title]).emitRedrawConsumed(generation)
    }

    fun forceLateSurfaceMetrics(title: String, metrics: SurfaceMetrics) {
        checkNotNull(surfaceObservers[title]).forceMetrics(metrics)
    }

    private fun recordNativeClose(recording: RecordingNativeWindowOwner) {
        if (!recording.nativeClosed.compareAndSet(false, true)) return
        closedWindowTitles += recording.title
        windowWillCloseTitles += recording.title
        checkNotNull(recording.delegate).callbacks.windowWillClose()
    }

    private class RecordingNativeWindowOwner(
        val title: String,
    ) : AppKitNativeWindowOwner {
        val nativeClosed = AtomicBoolean(false)
        var contentView: RecordingNativeViewOwner? = null
        var delegate: RecordingNativeDelegateOwner? = null
        var contentViewAttached: Boolean = true
        var delegateAttached: Boolean = true
        var presented: Boolean = false
        private val released = AtomicBoolean(false)

        override fun close() {
            released.compareAndSet(false, true)
        }
    }

    private class RecordingNativeViewOwner : AppKitNativeViewOwner {
        private val released = AtomicBoolean(false)

        override fun close() {
            released.compareAndSet(false, true)
        }
    }

    private class RecordingNativeDelegateOwner(
        val peerId: AppKitWindowPeerId,
        private val title: String,
        val callbacks: AppKitWindowDelegateCallbacks,
        private val onRevoked: (String) -> Unit,
    ) : AppKitNativeDelegateOwner {
        private val callbacksRevoked = AtomicBoolean(false)
        private val retained = AtomicBoolean(false)
        private val released = AtomicBoolean(false)

        override fun revokeCallbacks() {
            if (callbacksRevoked.compareAndSet(false, true)) onRevoked(title)
        }

        override fun retainAfterFailedDetachment() {
            retained.set(true)
        }

        override fun close() {
            released.compareAndSet(false, true)
        }
    }

    private class RecordingNativeSurfaceObserver(
        private val callbacks: AppKitSurfaceCallbacks,
        override val initialSnapshot: AppKitSurfaceSnapshot,
        private val recordRedrawRequest: (Long) -> Unit,
    ) : AppKitNativeSurfaceObserverOwner {
        private val accepting = AtomicBoolean(true)

        fun emitMetrics(metrics: SurfaceMetrics) {
            if (accepting.get()) callbacks.metricsChanged(metrics)
        }

        fun emitFocus(focus: SurfaceFocus) {
            if (accepting.get()) callbacks.focusChanged(focus)
        }

        fun emitRedrawConsumed(generation: Long) {
            if (accepting.get()) callbacks.redrawConsumed(generation)
        }

        fun forceMetrics(metrics: SurfaceMetrics) {
            callbacks.metricsChanged(metrics)
        }

        override fun requestRedraw(generation: Long) {
            recordRedrawRequest(generation)
        }

        override fun revokeCallbacks() {
            accepting.set(false)
        }

        override fun close() = Unit
    }

    private fun AppKitNativeWindowOwner.recordingWindow(): RecordingNativeWindowOwner =
        this as? RecordingNativeWindowOwner ?: error("foreign test window owner")

    private fun AppKitNativeViewOwner.recordingView(): RecordingNativeViewOwner =
        this as? RecordingNativeViewOwner ?: error("foreign test view owner")

    private fun AppKitNativeDelegateOwner.recordingDelegate(): RecordingNativeDelegateOwner =
        this as? RecordingNativeDelegateOwner ?: error("foreign test delegate owner")
}

internal class OwnerThreadAppKitNativeWindowPort(
    name: String,
) : AppKitNativeWindowPort, AutoCloseable {
    private val delegate = DeterministicAppKitNativeWindowPort(name)
    private val ownerThread = AtomicReference<Thread?>()
    private val executor = Executors.newSingleThreadExecutor { action ->
        Thread.ofPlatform().daemon().name("kadre-appkit-owner-test").unstarted {
            ownerThread.set(Thread.currentThread())
            action.run()
        }
    }
    private val nextForeignCall = AtomicReference<CountDownLatch?>()

    override fun isMainThread(): Boolean = Thread.currentThread() === ownerThread.get()

    override fun <T> onMainThread(block: () -> T): T {
        if (Thread.currentThread() === ownerThread.get()) return block()
        nextForeignCall.getAndSet(null)?.countDown()
        return executor.submit<T> { block() }.get()
    }

    override fun createWindow(spec: WindowSpec): AppKitNativeWindowOwner = delegate.createWindow(spec)

    override fun createContentView(spec: WindowSpec): AppKitNativeViewOwner = delegate.createContentView(spec)

    override fun createDelegate(
        peerId: AppKitWindowPeerId,
        callbacks: AppKitWindowDelegateCallbacks,
    ): AppKitNativeDelegateOwner = delegate.createDelegate(peerId, callbacks)

    override fun attachContentView(window: AppKitNativeWindowOwner, view: AppKitNativeViewOwner) {
        delegate.attachContentView(window, view)
    }

    override fun attachDelegate(window: AppKitNativeWindowOwner, delegate: AppKitNativeDelegateOwner) {
        this.delegate.attachDelegate(window, delegate)
    }

    override fun present(window: AppKitNativeWindowOwner) {
        delegate.present(window)
    }

    override fun observeSurface(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitSurfaceCallbacks,
    ): AppKitNativeSurfaceObserverOwner = delegate.observeSurface(window, view, callbacks)

    override fun detachDelegate(window: AppKitNativeWindowOwner) {
        delegate.detachDelegate(window)
    }

    override fun detachContentView(window: AppKitNativeWindowOwner) {
        delegate.detachContentView(window)
    }

    override fun closeWindow(window: AppKitNativeWindowOwner) {
        delegate.closeWindow(window)
    }

    override fun desktopHandle(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ): RuntimeDesktopNativeWindowHandle.AppKit = delegate.desktopHandle(window, view)

    fun submitOnOwnerThread(action: () -> Unit): Future<*> = executor.submit(action)

    fun observeNextForeignMainThreadCall(): CountDownLatch = CountDownLatch(1).also { latch ->
        check(nextForeignCall.compareAndSet(null, latch))
    }

    fun emitNativeClosed(title: String) {
        delegate.emitNativeClosed(title)
    }

    fun emitSurfaceMetrics(title: String, metrics: SurfaceMetrics) {
        delegate.emitSurfaceMetrics(title, metrics)
    }

    fun emitSurfaceRedrawConsumed(title: String, generation: Long) {
        delegate.emitSurfaceRedrawConsumed(title, generation)
    }

    val requestedSurfaceRedrawGenerations: List<Long>
        get() = delegate.requestedSurfaceRedrawGenerations

    val closedWindowTitles: List<String>
        get() = delegate.closedWindowTitles

    override fun close() {
        executor.shutdownNow()
    }
}

private fun newDaemonSingleThreadExecutor(name: String): ExecutorService =
    Executors.newSingleThreadExecutor { action -> Thread.ofPlatform().daemon().name(name).unstarted(action) }

internal fun deterministicSurfaceSnapshot(
    logicalSize: LogicalSize = LogicalSize(320.0, 240.0),
    scaleFactor: Double = 2.0,
): AppKitSurfaceSnapshot = AppKitSurfaceSnapshot(
    metrics = SurfaceMetrics(
        logicalSize = logicalSize,
        physicalSize = logicalSize.toPhysical(scaleFactor),
        scaleFactor = scaleFactor,
        safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    ),
    focus = SurfaceFocus.Focused,
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Unknown,
    theme = SurfaceTheme.Light,
)

internal fun <T> KadreResult<T>.appKitSuccessValue(): T = when (this) {
    is KadreResult.Success -> value
    is KadreResult.Failure -> error("expected success, got $reason")
}

private fun <T> KadreResult<T>.successValue(): T = appKitSuccessValue()

private suspend fun WindowRequest.awaitOpened() {
    check(await() is WindowRequestOutcome.OpenedHere) { "expected an AppKit window to open" }
}
