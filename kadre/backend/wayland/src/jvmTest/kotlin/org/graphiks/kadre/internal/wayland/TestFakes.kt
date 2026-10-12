package org.graphiks.kadre.internal.wayland

import org.graphiks.kffi.posix.PosixWakeup

/** Wake injectable pour les tests O2. */
internal class SessionFakeWakeup : PosixWakeup {
    override val readFd: Int = 42
    var signalCount = 0
    var closed = false

    override fun signal(): Boolean {
        signalCount++
        return true
    }

    override fun drain(): Boolean = true

    override fun close() {
        closed = true
    }
}

/** Pont injectable pour les tests O2 — jamais de FFM. */
internal class FakeBridge(
    var globals: List<WaylandGlobal> = listOf(
        WaylandGlobal("wl_compositor", 6),
        WaylandGlobal("xdg_wm_base", 2),
    ),
) : WaylandNativeBridge {
    var closed = false
    var cancelCount = 0
    var flushCount = 0
    var dispatchPendingResult = true
    var pollOutcome = PollOutcome.DisplayReady
    var beforePoll: (() -> Unit)? = null
    var fakeWindow: WaylandNativeWindow? = null
    val createWindowingCalls = java.util.concurrent.atomic.AtomicInteger(0)

    override val displayFd: Int = 7

    override fun globals(): List<WaylandGlobal> = globals

    override fun roundtrip(): Boolean = true

    override fun dispatchPending(): Boolean = dispatchPendingResult

    override fun prepareRead(): Int = 0

    override fun readEvents(): Boolean = true

    override fun cancelRead() {
        cancelCount++
    }

    override fun flush(): WaylandFlushOutcome {
        flushCount++
        return WaylandFlushOutcome.Done
    }

    override fun pollEvents(wakeFd: Int, watchWrite: Boolean): PollOutcome {
        beforePoll?.invoke()
        return pollOutcome
    }

    override fun connectionLost(): Boolean = false

    override fun createWindowing(width: Int, height: Int): WaylandNativeWindow {
        createWindowingCalls.incrementAndGet()
        return fakeWindow ?: throw UnsupportedOperationException("no windowing in this fake")
    }

    override fun close() {
        closed = true
    }
}
