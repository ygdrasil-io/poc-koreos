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
            return Win32TopLevelWindowHandle(hwnd)
        }
    }

    fun dispatchWindowMessage(hwnd: Long, id: Int, wParam: Long = 0L, lParam: Long = 0L) {
        // Routage manquant = fallthrough silencieux (miroir du pont réel : 0L).
        if (id == 0x000F) { // WM_PAINT : ne se délivre que si invalidé
            if (!invalidated.contains(hwnd)) return
            invalidated.remove(hwnd)
            paintCount.incrementAndGet()
        }
        val handler = topLevelHandlers[hwnd]
        java.nio.file.Files.write(
            java.nio.file.Paths.get("build/win32-integration/dispatch-debug.txt"),
            ("dispatch hwnd=$hwnd id=$id handler=${handler != null}\n").toByteArray(),
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND,
        )
        handler?.invoke(hwnd, id, wParam, lParam)
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
        if (topLevelHandlers.remove(hwnd) != null) {
            destroyedTopLevelWindows.incrementAndGet()
            topLevelHwndOrder.remove(hwnd)
        }
    }

    val defWindowProcCalls = AtomicInteger(0)

    override fun defWindowProc(hwnd: Long, message: Int, wParam: Long, lParam: Long): Long {
        defWindowProcCalls.incrementAndGet()
        return 0L
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

    override fun close() {
        destroyed = true
    }
}
