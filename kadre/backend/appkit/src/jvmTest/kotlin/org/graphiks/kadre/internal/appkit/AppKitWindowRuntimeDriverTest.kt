package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.policy.KadrePolicies
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
}

internal class DeterministicAppKitNativeWindowPort(
    private val name: String,
    private val closeFailures: Map<String, Throwable> = emptyMap(),
    private val beforeCreateWindow: (WindowSpec) -> Unit = { },
) : AppKitNativeWindowPort {
    private val windows = linkedMapOf<String, RecordingNativeWindowOwner>()
    val createdWindowTitles = CopyOnWriteArrayList<String>()
    val closedWindowTitles = CopyOnWriteArrayList<String>()
    val createdPeerIds = CopyOnWriteArrayList<AppKitWindowPeerId>()

    override fun isMainThread(): Boolean = true

    override fun <T> onMainThread(block: () -> T): T = block()

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
    ): AppKitNativeDelegateOwner = RecordingNativeDelegateOwner(peerId, callbacks).also {
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

    override fun detachDelegate(window: AppKitNativeWindowOwner) {
        window.recordingWindow().delegateAttached = false
    }

    override fun detachContentView(window: AppKitNativeWindowOwner) {
        window.recordingWindow().contentViewAttached = false
    }

    override fun closeWindow(window: AppKitNativeWindowOwner) {
        val recording = window.recordingWindow()
        if (recording.nativeClosed.compareAndSet(false, true)) {
            closedWindowTitles += recording.title
            closeFailures[recording.title]?.let { throw it }
        }
    }

    fun requestNativeClose(title: String): Boolean =
        checkNotNull(windows[title]?.delegate).callbacks.windowShouldClose()

    fun emitNativeClosed(title: String) {
        checkNotNull(windows[title]?.delegate).callbacks.windowWillClose()
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
        val callbacks: AppKitWindowDelegateCallbacks,
    ) : AppKitNativeDelegateOwner {
        private val callbacksRevoked = AtomicBoolean(false)
        private val retained = AtomicBoolean(false)
        private val released = AtomicBoolean(false)

        override fun revokeCallbacks() {
            callbacksRevoked.set(true)
        }

        override fun retainAfterFailedDetachment() {
            retained.set(true)
        }

        override fun close() {
            released.compareAndSet(false, true)
        }
    }

    private fun AppKitNativeWindowOwner.recordingWindow(): RecordingNativeWindowOwner =
        this as? RecordingNativeWindowOwner ?: error("foreign test window owner")

    private fun AppKitNativeViewOwner.recordingView(): RecordingNativeViewOwner =
        this as? RecordingNativeViewOwner ?: error("foreign test view owner")

    private fun AppKitNativeDelegateOwner.recordingDelegate(): RecordingNativeDelegateOwner =
        this as? RecordingNativeDelegateOwner ?: error("foreign test delegate owner")
}

private class OwnerThreadAppKitNativeWindowPort(
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

    override fun detachDelegate(window: AppKitNativeWindowOwner) {
        delegate.detachDelegate(window)
    }

    override fun detachContentView(window: AppKitNativeWindowOwner) {
        delegate.detachContentView(window)
    }

    override fun closeWindow(window: AppKitNativeWindowOwner) {
        delegate.closeWindow(window)
    }

    fun submitOnOwnerThread(action: () -> Unit): Future<*> = executor.submit(action)

    fun observeNextForeignMainThreadCall(): CountDownLatch = CountDownLatch(1).also { latch ->
        check(nextForeignCall.compareAndSet(null, latch))
    }

    fun emitNativeClosed(title: String) {
        delegate.emitNativeClosed(title)
    }

    val closedWindowTitles: List<String>
        get() = delegate.closedWindowTitles

    override fun close() {
        executor.shutdownNow()
    }
}

private fun newDaemonSingleThreadExecutor(name: String): ExecutorService =
    Executors.newSingleThreadExecutor { action -> Thread.ofPlatform().daemon().name(name).unstarted(action) }

internal fun <T> KadreResult<T>.appKitSuccessValue(): T = when (this) {
    is KadreResult.Success -> value
    is KadreResult.Failure -> error("expected success, got $reason")
}

private fun <T> KadreResult<T>.successValue(): T = appKitSuccessValue()

private suspend fun WindowRequest.awaitOpened() {
    check(await() is WindowRequestOutcome.OpenedHere) { "expected an AppKit window to open" }
}
