package org.graphiks.kadre.internal.win32

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Deterministic, OS-independent host port: session and pump semantics are
 * proven against this fake everywhere; the KFFI port proves the real frontier
 * on the Windows runner.
 */
internal open class DeterministicWin32HostPort : Win32NativeHostPort {
    val createdTopLevelWindows = AtomicInteger(0)
    val destroyedTopLevelWindows = AtomicInteger(0)
    val postedCloses = AtomicInteger(0)
    private val topLevelHandlers = java.util.concurrent.ConcurrentHashMap<Long, (Long, Int, Long, Long) -> Long>()
    val scriptedClientSize = java.util.concurrent.ConcurrentHashMap<Long, Pair<Int, Int>>()
    val scriptedScaleFactor = java.util.concurrent.ConcurrentHashMap<Long, Double>()
    val windowTitles = java.util.concurrent.ConcurrentHashMap<Long, String>()
    val windowStyles = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    val floatingWindows = mutableSetOf<Long>()
    val icons = mutableMapOf<Long, org.graphiks.kadre.surface.BinaryImage>()
    val protectedWindows = mutableSetOf<Long>()
    val cursors = mutableMapOf<Long, org.graphiks.kadre.surface.CursorStyle>()
    val confinedWindows = mutableSetOf<Long>()
    val confinementObservers = mutableMapOf<Long, () -> Unit>()
    val nativeDrags = mutableListOf<Triple<Int, Int, Int>>()
    var onNativeDrag: (() -> Unit)? = null
    val attentionRequests = mutableListOf<Pair<Long, org.graphiks.kadre.window.WindowAttention>>()
    var confinementAvailable = true
    val windowBounds = java.util.concurrent.ConcurrentHashMap<Long, org.graphiks.kadre.surface.PhysicalRect>()
    var beforeTitleWrite: (() -> Unit)? = null
    var afterStyleWrite: (() -> Unit)? = null
    var titleReadback: ((String) -> String)? = null
    var beforeBoundsWrite: (() -> Unit)? = null
    val invalidateCount = AtomicInteger(0)
    val validateCount = AtomicInteger(0)
    val paintCount = AtomicInteger(0)
    private val invalidated = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Long, Boolean>())
    private val topLevelHwndOrder = java.util.concurrent.ConcurrentLinkedQueue<Long>()
    private var nextTopLevelHwnd = 10_000L
    val createdWindows = AtomicInteger(0)
    val destroyedWindows = AtomicInteger(0)
    val wakeCount = AtomicInteger(0)
    val postedStops = AtomicInteger(0)
    val dispatchCount = AtomicInteger(0)
    val createdHandlers = AtomicReference<((Int, Long, Long) -> Long)?>(null)
    val hwnds = ConcurrentLinkedQueue<Long>()

    /** When non-null, the next waitForMessage returns this instead of queued messages (consumed for Error). */
    val forcedWaitResult = AtomicReference<Win32MessageWait?>(null)

    private val monitor = Object()
    private val messages = ConcurrentLinkedQueue<Win32MessageWait.Message>()
    private var nextHwnd = 1L
    private var destroyed = false

    override fun isMainThread(): Boolean = true

    override fun <T> onMainThread(block: () -> T): T = block()
    override fun postOnMainThread(block: () -> Unit, onFailure: (Throwable) -> Unit) {
        try { block() } catch (failure: Throwable) { onFailure(failure) }
    }
    override fun stopWorkerCommands() = Unit
    override fun deferWindowMutation(block: () -> Unit, onFailure: (Throwable) -> Unit) = postOnMainThread(block, onFailure)

    override fun createControlWindow(
        messageHandler: (id: Int, wParam: Long, lParam: Long) -> Long,
    ): Win32ControlWindowHandle {
        synchronized(monitor) {
            check(!destroyed) { "host port already closed" }
            createdHandlers.set(messageHandler)
            createdWindows.incrementAndGet()
            val hwnd = nextHwnd++
            hwnds.add(hwnd)
            return Win32ControlWindowHandle(hwnd)
        }
    }

    override fun postWake(hwnd: Long): Boolean {
        wakeCount.incrementAndGet()
        // Mirrors the real frontier: the wake reaches the pump as a window
        // message dispatched to the control window.
        messages.add(Win32MessageWait.Message(Win32PumpMessage.Wake.ordinal, 0L, 0L))
        synchronized(monitor) { monitor.notifyAll() }
        return true
    }

    override fun requestStopFromAnyone(hwnd: Long) {
        postedStops.incrementAndGet()
        messages.add(Win32MessageWait.Message(Win32PumpMessage.StopRequest.ordinal, 0L, 0L))
        synchronized(monitor) { monitor.notifyAll() }
    }

    fun enqueueMessage(id: Int, wParam: Long = 0L, lParam: Long = 0L) {
        messages.add(Win32MessageWait.Message(id, wParam, lParam))
        synchronized(monitor) { monitor.notifyAll() }
    }

    override fun waitForMessage(timeoutMillis: Long): Win32MessageWait {
        forcedWaitResult.get()?.let { forced ->
            if (forced is Win32MessageWait.Error) forcedWaitResult.set(null)
            return forced
        }
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            messages.poll()?.let { return it }
            synchronized(monitor) {
                if (System.currentTimeMillis() >= deadline) return Win32MessageWait.Idle
                monitor.wait(25)
            }
        }
    }

    override fun translateAndDispatch(wait: Win32MessageWait.Message) {
        dispatchCount.incrementAndGet()
    }

    override fun destroyControlWindow(hwnd: Long) {
        createdHandlers.set(null)
        if (hwnds.remove(hwnd)) destroyedWindows.incrementAndGet()
    }

    override fun createTopLevelWindow(
        title: String,
        decorations: Win32WindowDecorations,
        width: Int,
        height: Int,
        messageHandler: (hwnd: Long, id: Int, wParam: Long, lParam: Long) -> Long,
    ): Win32TopLevelWindowHandle {
        synchronized(this) {
            check(!destroyed) { "host port already closed" }
            createdTopLevelWindows.incrementAndGet()
            val hwnd = nextTopLevelHwnd++
            topLevelHandlers[hwnd] = messageHandler
            topLevelHwndOrder.add(hwnd)
            windowTitles[hwnd] = title
            windowStyles[hwnd] = 0x00cf0000
            windowBounds[hwnd] = org.graphiks.kadre.surface.PhysicalRect(org.graphiks.kadre.surface.PhysicalPoint(0, 0), org.graphiks.kadre.surface.PhysicalSize(width + 16, height + 39))
            return Win32TopLevelWindowHandle(hwnd)
        }
    }

    fun dispatchWindowMessage(hwnd: Long, id: Int, wParam: Long = 0L, lParam: Long = 0L): Long {
        // Routage manquant = fallthrough silencieux (miroir du pont réel : 0L).
        if (id == 0x000F) { // WM_PAINT : ne se délivre que si invalidé
            if (!invalidated.contains(hwnd)) return 0L
            invalidated.remove(hwnd)
            paintCount.incrementAndGet()
        }
        return topLevelHandlers[hwnd]?.invoke(hwnd, id, wParam, lParam) ?: 0L
    }

    fun errorIfWindowUnknown(hwnd: Long) {
        check(topLevelHandlers.containsKey(hwnd)) { "no live top-level window routes hwnd $hwnd" }
    }

    override fun requestCloseWindow(hwnd: Long) {
        postedCloses.incrementAndGet()
        // Miroir du réel : WM_CLOSE est POSTÉ dans la file ; la livraison
        // n'a lieu que lorsqu'un pump draine et dispatche.
        messages.add(Win32MessageWait.Message(0x0010, 0L, 0L))
        synchronized(monitor) { monitor.notifyAll() }
    }



    fun lastTopLevelHwnd(): Long = topLevelHwndOrder.lastOrNull() ?: 0L

    fun topLevelHwndAt(index: Int): Long = topLevelHwndOrder.elementAtOrNull(index) ?: 0L

    override fun destroyWindow(hwnd: Long) {
        confinedWindows.remove(hwnd); icons.remove(hwnd); cursors.remove(hwnd); confinementObservers.remove(hwnd)
        if (topLevelHandlers.remove(hwnd) != null) {
            destroyedTopLevelWindows.incrementAndGet()
            topLevelHwndOrder.remove(hwnd)
        }
    }

    val defWindowProcCalls = AtomicInteger(0)
    var defaultResult = 0L
    var keyInput: Win32KeyInput? = null
    var wheelSettings = Win32WheelSettings(3, 1)
    var leaveArms = 0
    override fun showWindow(hwnd: Long) = Unit
    override fun readKeyboardInput(hwnd: Long, message: Int, wParam: Long, lParam: Long): Win32KeyInput? = keyInput
    override fun trackMouseLeave(hwnd: Long) { leaveArms++ }
    override fun cancelMouseLeave(hwnd: Long) = Unit
    override fun readWheelSettings(): Win32WheelSettings = wheelSettings

    override fun defWindowProc(hwnd: Long, message: Int, wParam: Long, lParam: Long): Long {
        defWindowProcCalls.incrementAndGet()
        return defaultResult
    }

    override fun invalidateRect(hwnd: Long) {
        invalidateCount.incrementAndGet()
        invalidated.add(hwnd)
    }

    override fun validateRect(hwnd: Long) {
        validateCount.incrementAndGet()
        invalidated.remove(hwnd)
    }

    override fun readClientPhysicalSize(hwnd: Long): Pair<Int, Int> =
        scriptedClientSize[hwnd] ?: (800 to 600)

    override fun readScaleFactor(hwnd: Long): Double = scriptedScaleFactor[hwnd] ?: 1.0

    override fun readWindow(hwnd: Long): Win32WindowReadback {
        errorIfWindowUnknown(hwnd)
        return Win32WindowReadback(windowTitles.getValue(hwnd), windowBounds.getValue(hwnd), readClientPhysicalSize(hwnd), windowStyles.getValue(hwnd), readScaleFactor(hwnd),
            floating = hwnd in floatingWindows, icon = icons[hwnd], contentProtection = hwnd in protectedWindows)
    }
    override fun setWindowTitle(hwnd: Long, title: String) {
        beforeTitleWrite?.invoke()
        errorIfWindowUnknown(hwnd)
        windowTitles[hwnd] = titleReadback?.invoke(title) ?: title
    }
    override fun setWindowStyle(hwnd: Long, style: Int) {
        errorIfWindowUnknown(hwnd)
        windowStyles[hwnd] = style
        afterStyleWrite?.invoke()
    }
    override fun setWindowBounds(hwnd: Long, bounds: org.graphiks.kadre.surface.PhysicalRect, frameChanged: Boolean) {
        beforeBoundsWrite?.invoke()
        errorIfWindowUnknown(hwnd)
        windowBounds[hwnd] = bounds
        val frame = frame(hwnd)
        scriptedClientSize[hwnd] = (bounds.size.width - frame.first) to (bounds.size.height - frame.second)
        dispatchWindowMessage(hwnd, WM_SIZE)
    }
    private fun frame(hwnd: Long, style: Int? = null): Pair<Int, Int> = if ((style ?: windowStyles.getValue(hwnd)) and 0x00c00000 != 0) 16 to 39 else 0 to 0
    override fun outerSizeForClient(hwnd: Long, width: Int, height: Int, style: Int?): Pair<Int, Int> {
        val frame = frame(hwnd, style)
        val outerWidth = width.toLong() + frame.first
        val outerHeight = height.toLong() + frame.second
        require(outerWidth in 1..Int.MAX_VALUE.toLong() && outerHeight in 1..Int.MAX_VALUE.toLong()) { "outer size overflows Windows LONG" }
        return outerWidth.toInt() to outerHeight.toInt()
    }
    override fun monitorBounds(hwnd: Long) = org.graphiks.kadre.surface.PhysicalRect(org.graphiks.kadre.surface.PhysicalPoint(-1920, 0), org.graphiks.kadre.surface.PhysicalSize(1920, 1080))
    override fun applyTrackLimits(hwnd: Long, lParam: Long, minimum: org.graphiks.kadre.surface.PhysicalSize?, maximum: org.graphiks.kadre.surface.PhysicalSize?) = Unit

    override fun setWindowLevel(hwnd: Long, floating: Boolean) { if (floating) floatingWindows.add(hwnd) else floatingWindows.remove(hwnd) }
    override fun setWindowIcon(hwnd: Long, image: org.graphiks.kadre.surface.BinaryImage?) { if (image == null) icons.remove(hwnd) else icons[hwnd] = image }
    override fun setContentProtection(hwnd: Long, enabled: Boolean) { if (enabled) protectedWindows.add(hwnd) else protectedWindows.remove(hwnd) }
    override fun requestAttention(hwnd: Long, attention: org.graphiks.kadre.window.WindowAttention) { attentionRequests += hwnd to attention }
    override fun setCursorStyle(hwnd: Long, style: org.graphiks.kadre.surface.CursorStyle) { cursors[hwnd] = style }
    override fun applyClientCursor(hwnd: Long) = Unit
    override fun setPointerConfinement(hwnd: Long, enabled: Boolean): Boolean {
        if (!enabled) { confinedWindows.remove(hwnd); return true }
        if (!confinementAvailable) return false
        confinedWindows += hwnd; return true
    }
    override fun refreshPointerConfinement(hwnd: Long): Boolean = hwnd in confinedWindows
    override fun observeConfinementLoss(hwnd: Long, onLost: () -> Unit) { confinementObservers[hwnd] = onLost }
    override fun beginMoveResize(hwnd: Long, hitTest: Int, clientX: Int, clientY: Int) { nativeDrags += Triple(hitTest, clientX, clientY); onNativeDrag?.invoke() }

    // Phase 7 drop: the fake records the observed callbacks; tests drive them directly.
    var dropAvailable = false
    var dropRevocations = AtomicInteger(0)
        private set
    val dropObservers = java.util.concurrent.ConcurrentHashMap<Long, Win32DropCallbacks>()
    override val dropTargetsAvailable: Boolean get() = dropAvailable
    override fun observeDrop(hwnd: Long, budgets: Win32DropBudgets, onFailure: (Throwable) -> Unit, callbacks: Win32DropCallbacks): AutoCloseable {
        check(dropAvailable) { "drop targets unavailable on this fake" }
        dropObservers[hwnd] = callbacks
        return AutoCloseable {
            if (dropObservers.remove(hwnd) != null) dropRevocations.incrementAndGet()
        }
    }

    /** Drives the observed callbacks like the native adapter would after its own position conversion. */
    fun dispatchDropEnter(hwnd: Long, source: org.graphiks.kadre.internal.runtime.DropTransferSource, x: Double, y: Double): Boolean =
        dropObservers[hwnd]?.entered(source, org.graphiks.kadre.surface.LogicalPoint(x, y)) ?: false
    fun dispatchDropMoved(hwnd: Long, x: Double, y: Double) {
        dropObservers[hwnd]?.moved(org.graphiks.kadre.surface.LogicalPoint(x, y))
    }
    fun dispatchDropExited(hwnd: Long) {
        dropObservers[hwnd]?.exited()
    }
    fun dispatchDropPerformed(hwnd: Long, x: Double, y: Double) {
        dropObservers[hwnd]?.performed(org.graphiks.kadre.surface.LogicalPoint(x, y))
    }

    // Phase 8 raw input: activation routing and copied native motions.
    var rawInputForeignRegistration = false
    val rawInputRegisterCalls = AtomicInteger(0)
    private var rawInputActivator: ((Long) -> Unit)? = null
    private var rawInputMotionSink: ((deltaX: Int, deltaY: Int, device: Long) -> Unit)? = null
    private var rawInputActiveHwnd: Long = 0L
    override fun attachRawInputActivator(activator: (Long) -> Unit) { rawInputActivator = activator }
    override fun activateRawInput(hwnd: Long) { rawInputActivator?.invoke(hwnd) }
    override fun hasForeignRawInputRegistration(): Boolean = rawInputForeignRegistration

    /** Broker-side registration entry (called by the test-injected native seam). */
    fun registerRawInputOnTarget(hwnd: Long, onMotion: (deltaX: Int, deltaY: Int, device: Long) -> Unit): Boolean {
        if (rawInputForeignRegistration) return false
        rawInputRegisterCalls.incrementAndGet()
        rawInputActiveHwnd = hwnd
        rawInputMotionSink = onMotion
        return true
    }

    override fun forwardRawInput(hwnd: Long, lParam: Long): Boolean {
        if (hwnd != rawInputActiveHwnd) return false
        val sink = rawInputMotionSink ?: return true
        sink(lParam.toInt(), ((lParam shr 16) and 0xffffL).toInt(), 0L)
        return true
    }

    /** Delivers one copied native motion through the registered sink. */
    fun dispatchRawInputMotion(deltaX: Int, deltaY: Int, device: Long = 0L) {
        rawInputMotionSink?.invoke(deltaX, deltaY, device)
    }

    override fun close() {
        destroyed = true
    }
}
