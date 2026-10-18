package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.internal.runtime.*
import org.graphiks.kadre.policy.EventDeliveryPolicy
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.LogicalRect
import org.graphiks.kffi.win32.managed.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** Kotlin values only; the implementation owns every native text service and buffer. */
internal interface Win32TextNativePort {
    val capability: Capability<Unit>
    fun <T> onMainThread(block: () -> T): T
    fun postOnMainThread(block: () -> Unit, onFailure: (Throwable) -> Unit)
    fun createStore(initial: Win32TextSnapshot, configuration: Win32TextConfiguration, limits: Win32TextLimits,
        onChange: (Win32TextChange) -> Boolean, onFailure: (Throwable) -> Unit): Win32NativeTextOwner
}

internal interface Win32NativeTextOwner : AutoCloseable {
    fun activate()
    fun apply(snapshot: Win32TextSnapshot)
    fun updateCursor(rect: LogicalRect, epoch: Long)
    fun setFocused(focused: Boolean)
    fun refreshCursor() {}
    fun handleCharacter(message: Int, wParam: Long, lParam: Long): Long?
    /** Revocation and close are safe from any thread; native ownership defers nonquiescent cleanup. */
    fun revoke()
    override fun close()
}

internal class Win32UnavailableTextInputPort(private val reason: KadreFailure) : TextInputPort {
    override val capability: Capability<Unit> = if (reason is KadreFailure.Unsupported) Capability.Unsupported(reason)
        else Capability.Supported(Unit, FeatureAvailability.Unavailable(reason))
    override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> = KadreResult.Failure(reason)
    override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> = KadreResult.Failure(reason)
    override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> = KadreResult.Failure(reason)
}

internal class Win32TextInputPort(private val native: Win32TextNativePort) : TextInputPort, AutoCloseable {
    private val admitted = AtomicBoolean(true)
    private val current = AtomicReference<Owner?>()
    @Volatile private var lastKnownFocus: Boolean? = null
    override val capability: Capability<Unit> get() = native.capability

    override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> {
        if (!admitted.get()) return KadreResult.Failure(closedFailure())
        val budget = command.payloadBudget ?: return KadreResult.Failure(KadreFailure.InvalidRequest("payloadBudget"))
        return try { native.onMainThread {
            if (!admitted.get()) return@onMainThread KadreResult.Failure(closedFailure())
            val owner = Owner(command.config.documentRevision.value, command.config.action, budget, command.resources,
                command.deliveryPolicy, command.onObservation, command.onTerminated)
            if (!current.compareAndSet(null, owner)) return@onMainThread KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession))
            try {
                val limits = Win32TextLimits(command.resources.maxTextCodeUnitsPerValue, command.deliveryPolicy.ingressCapacity,
                    command.resources.maxCollectionElementsPerValue, Win32PayloadBudget { bytes ->
                        when (val reservation = budget.retain(bytes)) {
                            is KadreResult.Success -> reservation.value
                            is KadreResult.Failure -> null
                        }
                    })
                val store = native.createStore(Win32TextSnapshot(command.config.surroundingText,
                    command.config.selection.win32Range(), command.config.documentRevision.value), win32TextConfiguration(command.config),
                    limits, owner::observe, { owner.nativeFailure(it) })
                owner.attach(store)
                owner.failureOrNull()?.let { KadreResult.Failure(it) } ?: KadreResult.Success(owner)
            } catch (cause: Throwable) { KadreResult.Failure(owner.nativeFailure(cause)) }
        } } catch (cause: Throwable) { KadreResult.Failure(nativeFailure(cause)) }
    }

    override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> {
        val owner = (command.owner as? Owner)?.takeIf { current.get() === it } ?: return KadreResult.Failure(closedFailure())
        return owner.cursor(command.rect, command.documentRevision.value)
    }
    override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> {
        val owner = (command.owner as? Owner)?.takeIf { current.get() === it } ?: return KadreResult.Failure(closedFailure())
        if (command.selection.endExclusiveUtf16 > command.text.length) return KadreResult.Failure(KadreFailure.InvalidRequest("selection"))
        return owner.document(Win32TextSnapshot(command.text, command.selection.win32Range(), command.documentRevision.value))
    }

    fun handleCharacter(message: Int, wParam: Long, lParam: Long): Long? = current.get()?.withStore {
        it.handleCharacter(message, wParam, lParam)
    }
    fun setFocused(focused: Boolean) {
        lastKnownFocus = focused
        current.get()?.withStore { it.setFocused(focused) }
    }
    fun refreshCursor() { current.get()?.withStore { it.refreshCursor() } }

    override fun close() {
        admitted.set(false)
        current.getAndSet(null)?.close()
    }

    private inner class Owner(
        initialRevision: Long,
        private val action: TextInputAction,
        private val budget: RuntimePayloadBudget,
        private val resources: ResourceBudgetPolicy,
        private val policy: EventDeliveryPolicy,
        observation: (TextInputObservation) -> Boolean,
        terminated: (KadreFailure) -> Unit,
    ) : TextInputOwner {
        private val lock = Any()
        private var closed = false
        private var failure: KadreFailure? = null
        private var accepted = initialRevision
        private var pendingRevision: Long? = null
        private var nativeApplied = false
        private var active = false
        private var draining = false
        private var store: Win32NativeTextOwner? = null
        private var observation: ((TextInputObservation) -> Boolean)? = observation
        private var terminated: ((KadreFailure) -> Unit)? = terminated
        private val pending = ArrayDeque<PendingObservation>()
        private val work = linkedSetOf<Win32TextUiWork<*>>()
        private val capacity = minOf(policy.ingressCapacity, resources.maxCollectionElementsPerValue)

        fun failureOrNull(): KadreFailure? = synchronized(lock) { failure ?: if (closed) closedFailure() else null }
        fun attach(value: Win32NativeTextOwner) {
            val retire = synchronized(lock) { if (closed) true else { check(store == null); store = value; false } }
            if (retire) retire(value)
        }

        override fun activate() {
            try { native.onMainThread {
                val handle = synchronized(lock) {
                    check(!closed)
                    active = true
                    if (pending.isNotEmpty()) draining = true
                    checkNotNull(store)
                }
                handle.activate()
                lastKnownFocus?.let { focused -> withStore { it.setFocused(focused) } }
                val flush = synchronized(lock) { draining && pendingRevision == null && !closed }
                if (flush) postFlush()
            } } catch (cause: Throwable) { throw KadreException(nativeFailure(cause)) }
        }

        fun <T> withStore(block: (Win32NativeTextOwner) -> T): T? {
            val handle = synchronized(lock) { store.takeUnless { closed } } ?: return null
            return try { block(handle) } catch (cause: Throwable) { nativeFailure(cause); null }
        }

        fun nativeFailure(cause: Throwable): KadreFailure = this@Win32TextInputPort.nativeFailure(cause).also(::terminate)

        fun observe(change: Win32TextChange): Boolean {
            var queued = false
            var rejected: KadreFailure? = null
            synchronized(lock) {
                if (closed) return false
                when {
                    change.epoch != accepted && change.epoch != pendingRevision -> rejected = platformFailure("unexpected-epoch")
                    change.payloadText.length > resources.maxTextCodeUnitsPerValue -> rejected = payloadFailure()
                    change.epoch == pendingRevision || !active || draining -> {
                        if (pending.size >= capacity) rejected = KadreFailure.SourceOverflow(KadreResourceKind.TextInputSession)
                        else when (val reservation = budget.retain(change.payloadText.length * 2L)) {
                            is KadreResult.Failure -> rejected = reservation.reason
                            is KadreResult.Success -> { pending.addLast(PendingObservation(change, reservation.value)); queued = true }
                        }
                    }
                }
            }
            rejected?.let { terminate(it); return false }
            if (queued) return true
            return deliver(change)
        }

        private fun deliver(change: Win32TextChange): Boolean {
            val receiver = synchronized(lock) { observation.takeUnless { closed } } ?: return false
            return try {
                receiver(change.publicObservation(action)).also { if (!it) terminate(closedFailure()) }
            } catch (_: Throwable) { terminate(platformFailure("observation-failed")); false }
        }

        override fun documentApplied(documentRevision: TextDocumentRevision) {
            val schedule = synchronized(lock) {
                if (closed) return
                if (pendingRevision != documentRevision.value || !nativeApplied) {
                    false
                } else {
                    accepted = documentRevision.value; pendingRevision = null; nativeApplied = false
                    if (pending.isNotEmpty() && !draining) { draining = true; true } else return
                }
            }
            if (schedule) postFlush() else terminate(platformFailure("unexpected-acknowledgment"))
        }

        private fun postFlush() {
            try { native.postOnMainThread(::flush, { nativeFailure(it) }) }
            catch (cause: Throwable) { nativeFailure(cause) }
        }
        private fun flush() {
            while (true) {
                val next = synchronized(lock) {
                    if (closed) return
                    pending.removeFirstOrNull() ?: run { draining = false; return }
                }
                try { if (!deliver(next.change)) return } finally { next.lease.close() }
            }
        }

        suspend fun document(snapshot: Win32TextSnapshot): KadreResult<Unit> {
            if (snapshot.text.length > resources.maxTextCodeUnitsPerValue) return KadreResult.Failure(payloadFailure())
            return enqueue(snapshot, snapshot.text.length * 2L, snapshot.epoch, ::applyDocument)
        }
        suspend fun cursor(rect: LogicalRect, epoch: Long): KadreResult<Unit> {
            val invalid = synchronized(lock) {
                failureOrNull() ?: when {
                    epoch != accepted -> KadreFailure.StaleRevision(accepted, epoch)
                    pendingRevision != null -> KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession)
                    else -> null
                }
            }
            if (invalid != null) return KadreResult.Failure(invalid)
            return enqueue(Cursor(rect, epoch), 0, null) { value ->
                val handle = synchronized(lock) { store.takeUnless { closed } } ?: return@enqueue KadreResult.Failure(closedFailure())
                handle.updateCursor(value.rect, value.epoch)
                failureOrNull()?.let { KadreResult.Failure(it) } ?: KadreResult.Success(Unit)
            }
        }
        private fun applyDocument(snapshot: Win32TextSnapshot): KadreResult<Unit> {
            val handle = synchronized(lock) { store.takeUnless { closed } } ?: return KadreResult.Failure(closedFailure())
            handle.apply(snapshot)
            synchronized(lock) { if (!closed) nativeApplied = true }
            return failureOrNull()?.let { KadreResult.Failure(it) } ?: KadreResult.Success(Unit)
        }

        private suspend fun <T : Any> enqueue(payload: T, bytes: Long, revision: Long?, apply: (T) -> KadreResult<Unit>): KadreResult<Unit> =
            suspendCancellableCoroutine { continuation ->
                val lease = when (val reservation = budget.retain(bytes)) {
                    is KadreResult.Success -> reservation.value
                    is KadreResult.Failure -> { continuation.resume(reservation); return@suspendCancellableCoroutine }
                }
                val item = Win32TextUiWork(payload, lease, continuation, apply, ::nativeFailure) {
                    synchronized(lock) { work.remove(it) }
                }
                val invalid = synchronized(lock) {
                    failureOrNull() ?: when {
                        work.size >= capacity -> KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload, capacity.toLong())
                        revision != null && revision < accepted -> KadreFailure.StaleRevision(accepted, revision)
                        revision != null && revision == accepted -> KadreFailure.InvalidRequest("documentRevision")
                        revision != null && pendingRevision != null -> KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession)
                        else -> {
                            if (revision != null) { pendingRevision = revision; nativeApplied = false }
                            work += item
                            null
                        }
                    }
                }
                if (invalid != null) { item.stop(invalid); return@suspendCancellableCoroutine }
                continuation.invokeOnCancellation {
                    item.stop(closedFailure())
                    terminate(closedFailure())
                }
                try { native.postOnMainThread(item::run, item::fail) }
                catch (cause: Throwable) { item.fail(cause) }
            }

        override fun close() = finish(null)
        private fun terminate(reason: KadreFailure) = finish(reason)
        private fun finish(reason: KadreFailure?) {
            val cleanup = synchronized(lock) {
                if (closed) return
                closed = true; failure = reason
                val callback = terminated.takeIf { reason != null }
                observation = null; terminated = null
                Cleanup(store.also { store = null }, pending.toList().also { pending.clear() },
                    work.toList().also { work.clear() }, callback)
            }
            current.compareAndSet(this, null)
            cleanup.store?.let(::retire)
            cleanup.pending.forEach { it.lease.close() }
            cleanup.work.forEach { it.stop(reason ?: closedFailure()) }
            if (reason != null) cleanup.terminated?.invoke(reason)
        }

        private fun retire(handle: Win32NativeTextOwner) {
            handle.revoke()
            // TSF already owns its STA endpoint and callback quiescence. An extra host-queue
            // hop could otherwise destroy the HWND before its focus association was removed.
            runCatching { handle.close() }
        }
        private fun payloadFailure() = KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload,
            resources.maxTextCodeUnitsPerValue.toLong())
    }

    private data class Cursor(val rect: LogicalRect, val epoch: Long)
    private data class PendingObservation(val change: Win32TextChange, val lease: AutoCloseable)
    private data class Cleanup(val store: Win32NativeTextOwner?, val pending: List<PendingObservation>,
        val work: List<Win32TextUiWork<*>>, val terminated: ((KadreFailure) -> Unit)?)
    private fun closedFailure() = KadreFailure.Closed(KadreResourceKind.TextInputSession)
    private fun platformFailure(code: String) = KadreFailure.PlatformFailure(KadrePlatform.Win32, "text-input", code)
    private fun nativeFailure(cause: Throwable): KadreFailure = win32TextFailure("native", cause)
}
