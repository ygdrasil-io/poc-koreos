package org.graphiks.kadre.internal.win32

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Deterministic, OS-independent host port: session and pump semantics are
 * proven against this fake everywhere; the KFFI port proves the real frontier
 * on the Windows runner.
 */
internal class DeterministicWin32HostPort : Win32NativeHostPort {
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
        if (hwnds.remove(hwnd)) destroyedWindows.incrementAndGet()
    }

    override fun close() {
        destroyed = true
    }
}
