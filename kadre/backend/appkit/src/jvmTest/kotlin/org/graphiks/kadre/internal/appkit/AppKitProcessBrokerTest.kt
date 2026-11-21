package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowOperationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppKitProcessBrokerTest {
    @Test
    fun exclusivePortsFromDifferentSessionsShareOneProcessRegistry() {
        val native = RecordingProcessDisplayNative(DisplayPortSnapshot(null, emptyList()))
        var opens = 0
        val bridge = object : AppKitExclusiveDisplayBridge {
            override val availability = AppKitExclusiveBridgeAvailability.Available

            override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
                opens += 1
                return AppKitExclusiveDisplayOpenResult.FailedBeforeCapture(
                    KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "capture-failed"),
                )
            }
        }
        val broker = AppKitProcessBroker(
            displayBrokerFactory = { AppKitDisplayBroker(native) },
            exclusiveDisplayBridge = bridge,
        )
        val firstExecutor = ProcessQueuedExclusiveExecutor()
        val secondExecutor = ProcessQueuedExclusiveExecutor()
        val first = broker.openExclusiveFullscreenPort(firstExecutor, UnusedProcessExclusiveWindowPort)
        val second = broker.openExclusiveFullscreenPort(secondExecutor, UnusedProcessExclusiveWindowPort)
        val firstCommand = ProcessExclusiveCommand(1L, 11L, 71L, 701L)
        val secondCommand = ProcessExclusiveCommand(2L, 12L, 71L, 701L)

        assertEquals(KadreResult.Success(Unit), first.reserve(firstCommand))
        assertEquals(
            KadreFailure.TemporarilyUnavailable(retryable = true),
            assertIs<KadreResult.Failure>(second.reserve(secondCommand)).reason,
        )
        assertEquals(0, opens)

        firstExecutor.runAll()

        // The failed capture attempt closed nothing by itself: one open was attempted
        // and failed before capture, and the ports close explicitly with the session.
        assertEquals(1, opens)
        first.close()
        second.close()
        assertFalse(first.isOpen())
        assertFalse(second.isOpen())
    }

    @Test
    fun brokerReplacesAndCancelsOnlyTheRequestingWindowsAttention() {
        val broker = AppKitProcessBroker()
        val native = RecordingAttentionNativeApplication()
        val owner = broker.newUserAttentionOwner(native)
        val first = attentionWindowId(1L)
        val second = attentionWindowId(2L)

        assertEquals(KadreResult.Success(Unit), broker.requestUserAttention(owner, first, WindowAttention.Informational))
        assertEquals(KadreResult.Success(Unit), broker.requestUserAttention(owner, second, WindowAttention.Critical))
        assertEquals(KadreResult.Success(Unit), broker.requestUserAttention(owner, first, WindowAttention.Critical))
        assertEquals(KadreResult.Success(Unit), broker.requestUserAttention(owner, first, WindowAttention.None))
        assertEquals(KadreResult.Success(Unit), broker.requestUserAttention(owner, first, WindowAttention.None))

        assertEquals(
            listOf(
                "request:Informational:1",
                "request:Critical:2",
                "cancel:1",
                "request:Critical:3",
                "cancel:3",
            ),
            native.trace,
        )
        owner.close()
        assertEquals(listOf("request:Informational:1", "request:Critical:2", "cancel:1", "request:Critical:3", "cancel:3", "cancel:2"), native.trace)
    }

    @Test
    fun standaloneLeaseCloseReleasesItsOwnersAttentionExactlyOnce() {
        val broker = AppKitProcessBroker()
        val native = RecordingAttentionNativeApplication()

        val standaloneOwner = broker.newUserAttentionOwner(native)
        val standalone = assertNotNull(broker.tryAcquireStandalone(standaloneOwner))
        assertEquals(KadreResult.Success(Unit), broker.requestUserAttention(standaloneOwner, attentionWindowId(11L), WindowAttention.Informational))
        standalone.close()
        standalone.close()

        assertEquals(listOf("cancel:1"), native.trace.filter { it.startsWith("cancel:") })
    }

    @Test
    fun attentionOwnersCannotCancelAnotherSessionsTokenAndNativeFailuresDoNotRecordOne() {
        val broker = AppKitProcessBroker()
        val native = RecordingAttentionNativeApplication()
        val firstOwner = broker.newUserAttentionOwner(native)
        val secondOwner = broker.newUserAttentionOwner(native)
        val firstWindow = attentionWindowId(21L)

        assertEquals(KadreResult.Success(Unit), broker.requestUserAttention(firstOwner, firstWindow, WindowAttention.Informational))
        broker.releaseUserAttention(secondOwner, firstWindow)
        assertEquals(listOf("request:Informational:1"), native.trace)

        native.requestFailure = IllegalStateException("attention unavailable")
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "user-attention", "request-exception"),
            (broker.requestUserAttention(secondOwner, attentionWindowId(22L), WindowAttention.Critical) as KadreResult.Failure).reason,
        )
        native.requestFailure = null
        secondOwner.close()
        firstOwner.close()

        assertEquals(listOf("request:Informational:1", "cancel:1"), native.trace)
    }

    @Test
    fun noneAndRequestsAfterOwnerCloseReturnTypedFailuresWithoutRetainingTokens() {
        val broker = AppKitProcessBroker()
        val native = RecordingAttentionNativeApplication()
        val owner = broker.newUserAttentionOwner(native)
        val window = attentionWindowId(31L)
        assertEquals(KadreResult.Success(Unit), broker.requestUserAttention(owner, window, WindowAttention.Informational))
        native.cancelFailure = IllegalStateException("cancel")
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "user-attention", "cancel-exception"),
            assertIs<KadreResult.Failure>(broker.requestUserAttention(owner, window, WindowAttention.None)).reason,
        )
        owner.close()
        assertEquals(
            KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Host),
            assertIs<KadreResult.Failure>(broker.requestUserAttention(owner, attentionWindowId(32L), WindowAttention.Critical)).reason,
        )
        assertEquals(
            KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Host),
            assertIs<KadreResult.Failure>(broker.requestUserAttention(owner, attentionWindowId(32L), WindowAttention.None)).reason,
        )
    }

    @Test
    fun standaloneOwnershipIsExclusiveAndReusable() {
        val broker = AppKitProcessBroker()

        val standalone = assertNotNull(broker.tryAcquireStandalone())
        assertNull(broker.tryAcquireStandalone())
        standalone.close()
        standalone.close()

        assertNotNull(broker.tryAcquireStandalone()).close()
    }
}

private fun attentionWindowId(value: Long): WindowId = WindowId::class.java
    .getDeclaredConstructor(Long::class.javaPrimitiveType)
    .apply { isAccessible = true }
    .newInstance(value)

private class RecordingProcessDisplayNative(
    private val snapshotValue: DisplayPortSnapshot? = null,
) : AppKitDisplayNative {
    var closeCount: Int = 0
        private set

    override val enumerationCapability: Capability<Unit> = Capability.Supported(Unit, FeatureAvailability.Available)

    override fun snapshot(): DisplayPortSnapshot = checkNotNull(snapshotValue) {
        "the process-broker test does not enumerate"
    }

    override fun observeReconfiguration(listener: () -> Unit): AutoCloseable = AutoCloseable {}

    override fun close() {
        closeCount += 1
    }
}

private class ProcessQueuedExclusiveExecutor : AppKitExclusiveExecutor {
    private val tasks = ArrayDeque<() -> Unit>()

    override fun dispatch(task: () -> Unit): Boolean {
        tasks.addLast(task)
        return true
    }

    fun runAll() {
        while (tasks.isNotEmpty()) tasks.removeFirst().invoke()
    }
}

private object UnusedProcessExclusiveWindowPort : AppKitExclusiveWindowPort

private class ProcessExclusiveCommand(
    window: Long,
    operation: Long,
    override val displayKey: Long,
    override val modeKey: Long,
) : AppKitExclusiveBrokerCommand {
    override val windowId: WindowId = attentionWindowId(window)
    override val operationId: WindowOperationId = WindowOperationId::class.java
        .getDeclaredConstructor(Long::class.javaPrimitiveType)
        .apply { isAccessible = true }
        .newInstance(operation)
    override val requestedFullscreen: FullscreenMode = FullscreenMode.Windowed

    override fun captureCommitted(): Boolean = true

    override fun completed(effectiveState: org.graphiks.kadre.window.WindowState) = Unit

    override fun failed(
        failure: KadreFailure,
        effectiveState: org.graphiks.kadre.window.WindowState?,
    ) = Unit
}

private class RecordingAttentionNativeApplication : AppKitNativeApplication {
    val trace = mutableListOf<String>()
    var requestFailure: Throwable? = null
    var cancelFailure: Throwable? = null
    private var nextToken = 1L

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = true

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        AutoCloseable { }

    override fun requestUserAttention(attention: WindowAttention): Long {
        requestFailure?.let { throw it }
        return nextToken++.also { token -> trace += "request:$attention:$token" }
    }

    override fun cancelUserAttentionRequest(token: Long) {
        cancelFailure?.let { throw it }
        trace += "cancel:$token"
    }

    override fun run() = Unit

    override fun requestStop(): AppKitStopRequest = AppKitStopRequest { AppKitStopResult.Accepted }

    override fun emergencyStop() = Unit
}
