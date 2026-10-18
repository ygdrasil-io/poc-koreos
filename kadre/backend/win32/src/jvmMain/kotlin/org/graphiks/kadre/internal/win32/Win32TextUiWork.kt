package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult

/** The posted closure owns this envelope, never the document or continuation directly. */
@OptIn(InternalCoroutinesApi::class)
internal class Win32TextUiWork<T : Any>(
    payload: T,
    lease: AutoCloseable,
    continuation: CancellableContinuation<KadreResult<Unit>>,
    private val apply: (T) -> KadreResult<Unit>,
    private val onFailure: (Throwable) -> KadreFailure,
    private val onFinished: (Win32TextUiWork<T>) -> Unit,
) {
    private val lock = Any()
    private var payload: T? = payload
    private var lease: AutoCloseable? = lease
    private var continuation: CancellableContinuation<KadreResult<Unit>>? = continuation
    private var running = false
    private var done = false
    private var stopped: KadreFailure? = null

    fun run() {
        val value = synchronized(lock) {
            if (done || running) return
            checkNotNull(payload).also { payload = null; running = true }
        }
        val result = try { apply(value) } catch (cause: Throwable) { KadreResult.Failure(onFailure(cause)) }
        val completion = synchronized(lock) {
            running = false; done = true
            val resultToSend = stopped?.let { KadreResult.Failure(it) } ?: result
            Triple(continuation.also { continuation = null }, lease.also { lease = null }, resultToSend)
        }
        onFinished(this)
        try { completion.first?.send(completion.third) } finally { completion.second?.close() }
    }

    fun stop(failure: KadreFailure) {
        val completion = synchronized(lock) {
            if (done) return
            stopped = failure
            if (running) {
                if (continuation?.isCancelled == true) continuation = null
                return
            }
            done = true; payload = null
            continuation.also { continuation = null } to lease.also { lease = null }
        }
        onFinished(this)
        try { completion.first?.send(KadreResult.Failure(failure)) } finally { completion.second?.close() }
    }

    fun fail(cause: Throwable) = stop(onFailure(cause))

    private fun CancellableContinuation<KadreResult<Unit>>.send(value: KadreResult<Unit>) {
        val token = tryResume(value) ?: return
        completeResume(token)
    }
}
