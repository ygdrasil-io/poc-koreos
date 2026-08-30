package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical
import org.graphiks.kadre.window.WindowSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppKitSurfacePeerTest {
    @Test
    fun observationRegistrationFollowsCompleteWindowPreparation() {
        val port = RecordingSurfacePort()

        val peer = AppKitWindowPeer.prepare(PEER_ID, WindowSpec(), port) { }

        assertEquals(
            listOf(
                "create:window",
                "create:view",
                "create:delegate",
                "attach:view",
                "attach:delegate",
                "present",
                "observe:surface",
            ),
            port.trace,
        )
        assertEquals(initialSnapshot(), peer.initialSurfaceSnapshot)
    }

    @Test
    fun callbacksFreezeValuesOnMainThreadAndCollapseDuplicateNativeUpdates() {
        val port = RecordingSurfacePort()
        val stimuli = mutableListOf<AppKitSurfaceStimulus>()
        AppKitWindowPeer.prepare(
            PEER_ID,
            WindowSpec(),
            port,
            acceptSurfaceStimulus = stimuli::add,
        )
        port.trace.clear()

        port.emitMetrics()
        port.surface.nativeLogicalSize = LogicalSize(640.0, 360.0)
        port.surface.nativeScaleFactor = 2.0
        port.emitMetrics()
        port.surface.nativeLogicalSize = LogicalSize(800.0, 450.0)
        port.surface.nativeScaleFactor = 1.0
        port.emitMetrics()
        port.emitFocus(SurfaceFocus.Unfocused)
        port.emitFocus(SurfaceFocus.Unfocused)
        port.emitVisibility(SurfaceVisibility.Hidden, SurfaceOcclusion.Unknown)
        port.emitVisibility(SurfaceVisibility.Hidden, SurfaceOcclusion.Unknown)
        port.emitTheme(SurfaceTheme.Dark)
        port.emitTheme(SurfaceTheme.Dark)

        assertEquals(
            listOf(
                AppKitSurfaceStimulus.MetricsChanged(PEER_ID, metrics(LogicalSize(640.0, 360.0), 2.0)),
                AppKitSurfaceStimulus.MetricsChanged(PEER_ID, metrics(LogicalSize(800.0, 450.0), 1.0)),
                AppKitSurfaceStimulus.FocusChanged(PEER_ID, SurfaceFocus.Unfocused),
                AppKitSurfaceStimulus.VisibilityChanged(
                    PEER_ID,
                    SurfaceVisibility.Hidden,
                    SurfaceOcclusion.Unknown,
                ),
                AppKitSurfaceStimulus.ThemeChanged(PEER_ID, SurfaceTheme.Dark),
            ),
            stimuli,
        )
        assertTrue(port.surface.everyCaptureWasOnMainThread)
    }

    @Test
    fun callbackConsumerRunsWithoutThePeerAdmissionLock() {
        val port = RecordingSurfacePort()
        val closeCompleted = CountDownLatch(1)
        val closeCompletedBeforeConsumerReturned = AtomicBoolean(false)
        lateinit var peer: AppKitWindowPeer
        peer = AppKitWindowPeer.prepare(
            PEER_ID,
            WindowSpec(),
            port,
            acceptSurfaceStimulus = {
                Thread.ofPlatform().start {
                    try {
                        peer.close()
                    } finally {
                        closeCompleted.countDown()
                    }
                }
                closeCompletedBeforeConsumerReturned.set(closeCompleted.await(1, TimeUnit.SECONDS))
            },
        )

        port.emitFocus(SurfaceFocus.Unfocused)

        assertTrue(closeCompleted.await(2, TimeUnit.SECONDS))
        assertTrue(closeCompletedBeforeConsumerReturned.get())
    }

    @Test
    fun redrawCompletionIsImmutableAndGenerationSafe() {
        val port = RecordingSurfacePort()
        val stimuli = mutableListOf<AppKitSurfaceStimulus>()
        val peer = AppKitWindowPeer.prepare(
            PEER_ID,
            WindowSpec(),
            port,
            acceptSurfaceStimulus = stimuli::add,
        )
        port.trace.clear()

        peer.requestRedraw(17L)

        assertEquals(listOf("redraw:17"), port.trace)
        assertEquals(
            listOf<AppKitSurfaceStimulus>(AppKitSurfaceStimulus.RedrawConsumed(PEER_ID, 17L)),
            stimuli,
        )
    }

    @Test
    fun observerRevocationPrecedesEveryNativeReleaseAndRejectsLateCallbacks() {
        val port = RecordingSurfacePort()
        val stimuli = mutableListOf<AppKitSurfaceStimulus>()
        val peer = AppKitWindowPeer.prepare(
            PEER_ID,
            WindowSpec(),
            port,
            acceptSurfaceStimulus = stimuli::add,
        )
        port.trace.clear()

        peer.close()
        port.forceLateFocus(SurfaceFocus.Unfocused)
        port.forceLateTheme(SurfaceTheme.Dark)
        port.forceLateRedraw(91L)

        assertEquals(
            listOf(
                "revoke:surface",
                "release:surface",
                "revoke:delegate",
                "detach:delegate",
                "release:delegate",
                "detach:view",
                "release:view",
                "close:window",
                "release:window",
            ),
            port.trace,
        )
        assertEquals(emptyList(), stimuli)
    }

    private companion object {
        val PEER_ID = AppKitWindowPeerId(211L)
    }
}

private class RecordingSurfacePort : AppKitNativeWindowPort {
    val trace = mutableListOf<String>()
    lateinit var surface: RecordingSurfaceOwner
    private lateinit var delegate: RecordingSurfaceDelegateOwner
    private val mainThread = ThreadLocal.withInitial { false }

    override fun isMainThread(): Boolean = mainThread.get()

    override fun <T> onMainThread(block: () -> T): T {
        val previous = mainThread.get()
        mainThread.set(true)
        return try {
            block()
        } finally {
            mainThread.set(previous)
        }
    }

    override fun createWindow(spec: WindowSpec): AppKitNativeWindowOwner =
        RecordingSurfaceWindowOwner(trace).also { trace += "create:window" }

    override fun createContentView(spec: WindowSpec): AppKitNativeViewOwner =
        RecordingSurfaceViewOwner(trace).also { trace += "create:view" }

    override fun createDelegate(
        peerId: AppKitWindowPeerId,
        callbacks: AppKitWindowDelegateCallbacks,
    ): AppKitNativeDelegateOwner = RecordingSurfaceDelegateOwner(trace).also {
        trace += "create:delegate"
        delegate = it
    }

    override fun attachContentView(window: AppKitNativeWindowOwner, view: AppKitNativeViewOwner) {
        trace += "attach:view"
    }

    override fun attachDelegate(window: AppKitNativeWindowOwner, delegate: AppKitNativeDelegateOwner) {
        trace += "attach:delegate"
    }

    override fun present(window: AppKitNativeWindowOwner) {
        trace += "present"
    }

    override fun observeSurface(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitSurfaceCallbacks,
    ): AppKitNativeSurfaceObserverOwner = RecordingSurfaceOwner(trace, callbacks, ::isMainThread).also {
        trace += "observe:surface"
        surface = it
    }

    override fun detachDelegate(window: AppKitNativeWindowOwner) {
        trace += "detach:delegate"
    }

    override fun detachContentView(window: AppKitNativeWindowOwner) {
        trace += "detach:view"
    }

    override fun closeWindow(window: AppKitNativeWindowOwner) {
        trace += "close:window"
    }

    override fun desktopHandle(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ): RuntimeDesktopNativeWindowHandle.AppKit = RuntimeDesktopNativeWindowHandle.AppKit(1uL, 2uL)

    fun emitMetrics() = onMainThread(surface::emitMetrics)

    fun emitFocus(value: SurfaceFocus) = onMainThread { surface.emitFocus(value) }

    fun emitVisibility(visibility: SurfaceVisibility, occlusion: SurfaceOcclusion) = onMainThread {
        surface.emitVisibility(visibility, occlusion)
    }

    fun emitTheme(value: SurfaceTheme) = onMainThread { surface.emitTheme(value) }

    fun completeRedraw(generation: Long) = onMainThread { surface.completeRedraw(generation) }

    fun forceLateFocus(value: SurfaceFocus) = onMainThread { surface.forceFocusCallback(value) }

    fun forceLateTheme(value: SurfaceTheme) = onMainThread { surface.forceThemeCallback(value) }

    fun forceLateRedraw(generation: Long) = onMainThread { surface.forceRedrawCallback(generation) }
}

private class RecordingSurfaceOwner(
    private val trace: MutableList<String>,
    private val callbacks: AppKitSurfaceCallbacks,
    private val isMainThread: () -> Boolean,
) : AppKitNativeSurfaceObserverOwner {
    override val initialSnapshot: AppKitSurfaceSnapshot = initialSnapshot()
    var nativeLogicalSize: LogicalSize = initialSnapshot.metrics.logicalSize
    var nativeScaleFactor: Double = initialSnapshot.metrics.scaleFactor
    var everyCaptureWasOnMainThread: Boolean = true
        private set
    private var accepting = true

    fun emitMetrics() {
        everyCaptureWasOnMainThread = everyCaptureWasOnMainThread && isMainThread()
        if (accepting) callbacks.metricsChanged(metrics(nativeLogicalSize, nativeScaleFactor))
    }

    fun emitFocus(value: SurfaceFocus) {
        everyCaptureWasOnMainThread = everyCaptureWasOnMainThread && isMainThread()
        if (accepting) callbacks.focusChanged(value)
    }

    fun emitVisibility(visibility: SurfaceVisibility, occlusion: SurfaceOcclusion) {
        everyCaptureWasOnMainThread = everyCaptureWasOnMainThread && isMainThread()
        if (accepting) callbacks.visibilityChanged(visibility, occlusion)
    }

    fun emitTheme(value: SurfaceTheme) {
        everyCaptureWasOnMainThread = everyCaptureWasOnMainThread && isMainThread()
        if (accepting) callbacks.themeChanged(value)
    }

    override fun requestRedraw(generation: Long) {
        trace += "redraw:$generation"
        completeRedraw(generation)
    }

    fun completeRedraw(generation: Long) {
        if (accepting) callbacks.redrawConsumed(generation)
    }

    fun forceFocusCallback(value: SurfaceFocus) {
        callbacks.focusChanged(value)
    }

    fun forceThemeCallback(value: SurfaceTheme) {
        callbacks.themeChanged(value)
    }

    fun forceRedrawCallback(generation: Long) {
        callbacks.redrawConsumed(generation)
    }

    override fun revokeCallbacks() {
        trace += "revoke:surface"
        accepting = false
    }

    override fun close() {
        trace += "release:surface"
    }
}

private class RecordingSurfaceWindowOwner(private val trace: MutableList<String>) : AppKitNativeWindowOwner {
    override fun close() {
        trace += "release:window"
    }
}

private class RecordingSurfaceViewOwner(private val trace: MutableList<String>) : AppKitNativeViewOwner {
    override fun close() {
        trace += "release:view"
    }
}

private class RecordingSurfaceDelegateOwner(private val trace: MutableList<String>) : AppKitNativeDelegateOwner {
    override fun revokeCallbacks() {
        trace += "revoke:delegate"
    }

    override fun retainAfterFailedDetachment() = Unit

    override fun close() {
        trace += "release:delegate"
    }
}

private fun metrics(logicalSize: LogicalSize, scaleFactor: Double): SurfaceMetrics = SurfaceMetrics(
    logicalSize = logicalSize,
    physicalSize = logicalSize.toPhysical(scaleFactor),
    scaleFactor = scaleFactor,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
)

private fun initialSnapshot(): AppKitSurfaceSnapshot = AppKitSurfaceSnapshot(
    metrics = metrics(LogicalSize(320.0, 180.0), 1.0),
    focus = SurfaceFocus.Focused,
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    theme = SurfaceTheme.Light,
)
