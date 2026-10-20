package org.graphiks.kadre.internal.win32

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Non-blocking pump for the AWT-embedded host (design phase 12, §2): ticks
 * run on the EDT through the host-posted runnable and drain at most 64
 * pending messages each with a zero-timeout pump pass — never a blocking
 * GetMessage on the EDT (it would starve AWT's Java events), never a pump
 * on another thread (message delivery is thread-affine). Idle periods back
 * off to [idleMillis]; load re-ticks immediately. The pump exits by flag —
 * no WM_QUIT is ever posted to the host loop.
 */
internal class Win32EmbeddedPump(
    private val pump: Win32MessagePump,
    private val postTick: (Runnable) -> Unit,
    private val idleMillis: Long = 8,
    private val drainBudget: Int = 64,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "kadre-win32-embedded-tick").apply { isDaemon = true }
    }

    fun start() {
        schedule(0)
    }

    private fun schedule(delayMillis: Long) {
        if (closed.get()) return
        try {
            scheduler.schedule({
                if (!closed.get()) postTick { tick() }
            }, delayMillis, TimeUnit.MILLISECONDS)
        } catch (rejected: java.util.concurrent.RejectedExecutionException) {
            // Scheduler already shut down by close(): the session is ending.
        }
    }

    /** One bounded EDT tick: drain up to the budget, then reschedule. */
    internal fun tick(): Boolean {
        if (closed.get()) return false
        var dispatched = 0
        while (dispatched < drainBudget) {
            when (pump.runOnce(0)) {
                PumpStep.Dispatched -> dispatched++
                PumpStep.Rearm -> {
                    schedule(idleMillis)
                    return true
                }
                PumpStep.Stopped, PumpStep.PumpFailed -> return false
            }
        }
        schedule(0)
        return true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scheduler.shutdownNow()
    }
}
