package org.graphiks.kadre.internal.wayland

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.graphiks.kffi.posix.PosixWakeup

/**
 * Pompe non bloquante pour l'hôte embarqué (design phase 8, D2) : le
 * poll des fd vit sur un worker IO dédié ; chaque réveil poste UN tick
 * borné sur le thread hôte (l'EDT en production), qui draine les
 * événements en attente, les actions marshalées et le flush — jamais de
 * poll sur le thread hôte, jamais de pompage des événements du toolkit.
 */
internal class WaylandEmbeddedPump(
    private val ops: PumpOperations,
    private val wake: PosixWakeup,
    private val postTick: (Runnable) -> Unit,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val ticking = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null

    /** One bounded tick: dispatch → actions → flush, on the host thread. */
    internal fun tick(): Boolean {
        if (closed.get()) return false
        if (!ticking.compareAndSet(false, true)) return true // tick déjà en vol : coalesced
        try {
            if (!ops.dispatchPending()) return false
            return true
        } finally {
            ticking.set(false)
        }
    }

    /** Starts the IO worker: polls the fds and posts ticks to the host. */
    fun start() {
        if (closed.get()) return
        if (worker != null) return
        worker = scope.launch {
            while (!closed.get()) {
                when (ops.poll(wake.readFd, false)) {
                    PollOutcome.DisplayReady, PollOutcome.WakeOnly -> {
                        if (PollOutcome.WakeOnly == ops.poll(wake.readFd, false)) {
                            wake.drain()
                        }
                        postTick(Runnable { tick() })
                    }
                    PollOutcome.Interrupted -> Unit
                    PollOutcome.Lost -> return@launch
                }
            }
        }
    }

    /** Requests a stop: wakes the worker; the host loop observes it. */
    fun requestStop() {
        if (!closed.compareAndSet(false, true)) return
        wake.signal()
        worker?.cancel()
    }

    fun isClosed(): Boolean = closed.get()

    override fun close() {
        requestStop()
        scope.cancel()
    }
}
