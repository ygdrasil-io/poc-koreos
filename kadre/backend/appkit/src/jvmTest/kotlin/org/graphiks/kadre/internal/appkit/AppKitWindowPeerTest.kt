package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.window.WindowSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppKitWindowPeerTest {
    @Test
    fun preparationReturnsOnlyAfterEveryNativeOwnerIsInstalled() {
        val port = RecordingAppKitNativeWindowPort()

        AppKitWindowPeer.prepare(PEER_ID, WindowSpec(), port) { }

        assertEquals(
            listOf(
                "main:start",
                "create:window",
                "create:view",
                "create:delegate",
                "attach:view",
                "attach:delegate",
                "present",
                "main:end",
            ),
            port.trace,
        )
    }

    @Test
    fun failedPreparationReleasesCreatedResourcesInReverseOwnershipOrder() {
        val port = RecordingAppKitNativeWindowPort(failAt = "present")

        assertFailsWith<IllegalStateException> {
            AppKitWindowPeer.prepare(PEER_ID, WindowSpec(), port) { }
        }

        assertEquals(
            listOf(
                "main:start",
                "create:window",
                "create:view",
                "create:delegate",
                "attach:view",
                "attach:delegate",
                "present",
                "revoke:delegate",
                "detach:delegate",
                "release:delegate",
                "detach:view",
                "release:view",
                "close:window",
                "release:window",
                "main:failure",
            ),
            port.trace,
        )
    }

    @Test
    fun failedDelegateAttachmentIsDetachedBeforeItsReceiverIsReleased() {
        val port = RecordingAppKitNativeWindowPort(failAt = "attach:delegate")

        assertFailsWith<IllegalStateException> {
            AppKitWindowPeer.prepare(PEER_ID, WindowSpec(), port) { }
        }

        assertEquals(
            listOf(
                "main:start",
                "create:window",
                "create:view",
                "create:delegate",
                "attach:view",
                "attach:delegate",
                "revoke:delegate",
                "detach:delegate",
                "release:delegate",
                "detach:view",
                "release:view",
                "close:window",
                "release:window",
                "main:failure",
            ),
            port.trace,
        )
    }

    @Test
    fun nativeShouldCloseEmitsAStimulusWithoutClosingOptimistically() {
        val port = RecordingAppKitNativeWindowPort()
        val stimuli = mutableListOf<AppKitWindowStimulus>()
        AppKitWindowPeer.prepare(
            PEER_ID,
            WindowSpec(),
            port,
            acceptStimulus = { stimulus -> stimuli += stimulus },
        )
        port.trace.clear()

        val accepted = port.delegate.windowShouldClose()

        assertFalse(accepted)
        assertEquals(listOf<AppKitWindowStimulus>(AppKitWindowStimulus.CloseRequested(PEER_ID)), stimuli)
        assertEquals(emptyList(), port.trace)
    }

    @Test
    fun nativeWillCloseEmitsOneTerminalStimulus() {
        val port = RecordingAppKitNativeWindowPort()
        val stimuli = mutableListOf<AppKitWindowStimulus>()
        AppKitWindowPeer.prepare(
            PEER_ID,
            WindowSpec(),
            port,
            acceptStimulus = { stimulus -> stimuli += stimulus },
        )

        port.delegate.windowWillClose()
        port.delegate.windowWillClose()

        assertEquals(listOf<AppKitWindowStimulus>(AppKitWindowStimulus.NativeClosed(PEER_ID)), stimuli)
    }

    @Test
    fun closeRevokesAdmissionBeforeDetachingAndReleasingNativeOwnership() {
        val port = RecordingAppKitNativeWindowPort()
        val peer = AppKitWindowPeer.prepare(PEER_ID, WindowSpec(), port) { }
        port.trace.clear()

        peer.close()
        peer.close()

        assertEquals(
            listOf(
                "main:start",
                "revoke:delegate",
                "detach:delegate",
                "release:delegate",
                "detach:view",
                "release:view",
                "close:window",
                "release:window",
                "main:end",
            ),
            port.trace,
        )
    }

    @Test
    fun closeRetainsTheDelegateWhenNativeDetachmentFails() {
        val expected = IllegalStateException("delegate detachment")
        val port = RecordingAppKitNativeWindowPort(detachDelegateFailure = expected)
        val peer = AppKitWindowPeer.prepare(PEER_ID, WindowSpec(), port) { }
        port.trace.clear()

        val actual = assertFailsWith<IllegalStateException> { peer.close() }

        assertSame(expected, actual)
        assertEquals(
            listOf(
                "main:start",
                "revoke:delegate",
                "detach:delegate",
                "retain:delegate",
                "detach:view",
                "release:view",
                "close:window",
                "release:window",
                "main:failure",
            ),
            port.trace,
        )
    }

    @Test
    fun failedAttachmentRetainsTheDelegateWhenRollbackCannotProveDetachment() {
        val detachFailure = IllegalStateException("delegate detachment")
        val port = RecordingAppKitNativeWindowPort(
            failAt = "attach:delegate",
            detachDelegateFailure = detachFailure,
        )

        val actual = assertFailsWith<IllegalStateException> {
            AppKitWindowPeer.prepare(PEER_ID, WindowSpec(), port) { }
        }

        assertEquals("native attach:delegate failure", actual.message)
        assertEquals(1, actual.suppressed.size)
        assertSame(detachFailure, actual.suppressed.single())
        assertEquals(
            listOf(
                "main:start",
                "create:window",
                "create:view",
                "create:delegate",
                "attach:view",
                "attach:delegate",
                "revoke:delegate",
                "detach:delegate",
                "retain:delegate",
                "detach:view",
                "release:view",
                "close:window",
                "release:window",
                "main:failure",
            ),
            port.trace,
        )
    }

    @Test
    fun cleanupContinuesWhenFailuresReuseTheSameThrowableInstance() {
        val expected = IllegalStateException("shared cleanup failure")
        val port = RecordingAppKitNativeWindowPort(cleanupFailure = expected)
        val peer = AppKitWindowPeer.prepare(PEER_ID, WindowSpec(), port) { }
        port.trace.clear()

        val actual = assertFailsWith<IllegalStateException> { peer.close() }

        assertSame(expected, actual)
        assertEquals(emptyList(), expected.suppressed.toList())
        assertEquals(
            listOf(
                "main:start",
                "revoke:delegate",
                "detach:delegate",
                "retain:delegate",
                "detach:view",
                "release:view",
                "close:window",
                "release:window",
                "main:failure",
            ),
            port.trace,
        )
    }

    @Test
    fun callbackConsumerCanWaitForConcurrentCloseWithoutLockInversion() {
        val port = RecordingAppKitNativeWindowPort()
        val closeCompleted = CountDownLatch(1)
        val closeCompletedBeforeConsumerReturned = AtomicBoolean(false)
        lateinit var peer: AppKitWindowPeer
        peer = AppKitWindowPeer.prepare(
            PEER_ID,
            WindowSpec(),
            port,
            acceptStimulus = {
                Thread.ofPlatform().start {
                    try {
                        peer.close()
                    } finally {
                        closeCompleted.countDown()
                    }
                }
                closeCompletedBeforeConsumerReturned.set(
                    closeCompleted.await(1, TimeUnit.SECONDS),
                )
            },
        )

        port.delegate.windowShouldClose()

        assertTrue(closeCompleted.await(2, TimeUnit.SECONDS))
        assertTrue(closeCompletedBeforeConsumerReturned.get())
    }

    private companion object {
        val PEER_ID: AppKitWindowPeerId = AppKitWindowPeerId(41L)
    }
}

private class RecordingAppKitNativeWindowPort(
    private val failAt: String? = null,
    private val cleanupFailure: Throwable? = null,
    private val detachDelegateFailure: Throwable? = null,
) : AppKitNativeWindowPort {
    val trace = mutableListOf<String>()
    lateinit var delegate: RecordingDelegateOwner

    override fun isMainThread(): Boolean = true

    override fun <T> onMainThread(block: () -> T): T {
        trace += "main:start"
        return try {
            block().also { trace += "main:end" }
        } catch (failure: Throwable) {
            trace += "main:failure"
            throw failure
        }
    }

    override fun createWindow(spec: WindowSpec): AppKitNativeWindowOwner {
        trace += "create:window"
        failIfRequested("create:window")
        return RecordingWindowOwner(trace, cleanupFailure)
    }

    override fun createContentView(spec: WindowSpec): AppKitNativeViewOwner {
        trace += "create:view"
        failIfRequested("create:view")
        return RecordingViewOwner(trace, cleanupFailure)
    }

    override fun createDelegate(
        peerId: AppKitWindowPeerId,
        callbacks: AppKitWindowDelegateCallbacks,
    ): AppKitNativeDelegateOwner {
        trace += "create:delegate"
        failIfRequested("create:delegate")
        return RecordingDelegateOwner(trace, callbacks, cleanupFailure).also { delegate = it }
    }

    override fun attachContentView(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ) {
        trace += "attach:view"
        failIfRequested("attach:view")
    }

    override fun attachDelegate(
        window: AppKitNativeWindowOwner,
        delegate: AppKitNativeDelegateOwner,
    ) {
        trace += "attach:delegate"
        failIfRequested("attach:delegate")
    }

    override fun present(window: AppKitNativeWindowOwner) {
        trace += "present"
        failIfRequested("present")
    }

    override fun detachDelegate(window: AppKitNativeWindowOwner) {
        trace += "detach:delegate"
        detachDelegateFailure?.let { throw it }
        cleanupFailure?.let { throw it }
    }

    override fun detachContentView(window: AppKitNativeWindowOwner) {
        trace += "detach:view"
        cleanupFailure?.let { throw it }
    }

    override fun closeWindow(window: AppKitNativeWindowOwner) {
        trace += "close:window"
        cleanupFailure?.let { throw it }
    }

    private fun failIfRequested(operation: String) {
        if (failAt == operation) error("native $operation failure")
    }
}

private class RecordingWindowOwner(
    private val trace: MutableList<String>,
    private val cleanupFailure: Throwable?,
) : AppKitNativeWindowOwner {
    override fun close() {
        trace += "release:window"
        cleanupFailure?.let { throw it }
    }
}

private class RecordingViewOwner(
    private val trace: MutableList<String>,
    private val cleanupFailure: Throwable?,
) : AppKitNativeViewOwner {
    override fun close() {
        trace += "release:view"
        cleanupFailure?.let { throw it }
    }
}

private class RecordingDelegateOwner(
    private val trace: MutableList<String>,
    private val callbacks: AppKitWindowDelegateCallbacks,
    private val cleanupFailure: Throwable?,
) : AppKitNativeDelegateOwner {
    private var accepting = true
    private var retained = false

    override fun revokeCallbacks() {
        trace += "revoke:delegate"
        accepting = false
        cleanupFailure?.let { throw it }
    }

    override fun close() {
        if (retained) return
        trace += "release:delegate"
        cleanupFailure?.let { throw it }
    }

    override fun retainAfterFailedDetachment() {
        if (retained) return
        retained = true
        trace += "retain:delegate"
    }

    fun windowShouldClose(): Boolean = if (accepting) callbacks.windowShouldClose() else false

    fun windowWillClose() {
        if (accepting) callbacks.windowWillClose()
    }
}
