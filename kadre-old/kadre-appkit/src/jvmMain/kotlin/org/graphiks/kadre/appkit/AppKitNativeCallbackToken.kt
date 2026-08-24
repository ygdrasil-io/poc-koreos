package org.graphiks.kadre.appkit

import org.graphiks.kffi.objc.ObjCRuntime
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Stable callback identity stored on the native ObjC receiver, never derived from its address. */
@JvmInline
internal value class AppKitNativeCallbackToken(val value: Long)

internal interface AppKitNativeTokenStore {
    fun attach(receiver: MemorySegment, token: AppKitNativeCallbackToken)
    fun read(receiver: MemorySegment): AppKitNativeCallbackToken?
    fun detach(receiver: MemorySegment, token: AppKitNativeCallbackToken)
}

/**
 * Allocates process-unique callback tokens and associates them with ObjC objects.
 *
 * Tests use [attachTestAddress] because fake addresses cannot be sent to libobjc;
 * production callbacks always read the token retained by the native receiver.
 */
internal object AppKitNativeCallbackTokens {
    private val nextToken = AtomicLong(0)
    private val testTokensByAddress = ConcurrentHashMap<Long, AppKitNativeCallbackToken>()
    private val testAddresses = ConcurrentHashMap.newKeySet<Long>()
    private val nativeStoreLock = ReentrantLock()

    @Volatile
    private var nativeStoreOverride: AppKitNativeTokenStore? = null

    private val nativeStore: NativeStore by lazy(::NativeStore)

    fun allocate(): AppKitNativeCallbackToken = next()

    fun attach(receiver: MemorySegment, token: AppKitNativeCallbackToken) {
        nativeStoreLock.withLock { currentNativeStore().attach(receiver, token) }
    }

    fun attach(receiver: MemorySegment): AppKitNativeCallbackToken =
        allocate().also { token -> attach(receiver, token) }

    fun attachTestAddress(address: Long): AppKitNativeCallbackToken =
        next().also {
            testAddresses += address
            testTokensByAddress[address] = it
        }

    fun read(receiver: MemorySegment): AppKitNativeCallbackToken? {
        val address = receiver.address()
        testTokensByAddress[address]?.let { return it }
        if (address in testAddresses) return null
        return nativeStoreLock.withLock { currentNativeStore().read(receiver) }
    }

    fun detach(receiver: MemorySegment, token: AppKitNativeCallbackToken) {
        nativeStoreLock.withLock { currentNativeStore().detach(receiver, token) }
    }

    internal fun <T> withNativeStoreForTest(store: AppKitNativeTokenStore, block: () -> T): T =
        nativeStoreLock.withLock {
            check(nativeStoreOverride == null) { "A native token store override is already installed" }
            nativeStoreOverride = store
            try {
                block()
            } finally {
                nativeStoreOverride = null
            }
        }

    fun readTestAddress(address: Long): AppKitNativeCallbackToken? = testTokensByAddress[address]

    fun detachTestAddress(address: Long, token: AppKitNativeCallbackToken) {
        testTokensByAddress.remove(address, token)
    }

    private fun next(): AppKitNativeCallbackToken = AppKitNativeCallbackToken(nextToken.incrementAndGet())

    private fun currentNativeStore(): AppKitNativeTokenStore = nativeStoreOverride ?: nativeStore

    private class NativeStore : AppKitNativeTokenStore {
        private val arena = Arena.global()
        private val linker = Linker.nativeLinker()
        private val lookup = run {
            val loader = SymbolLookup.loaderLookup()
            if (loader.find("objc_setAssociatedObject").isPresent) loader
            else SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", arena)
        }
        private val associationKey = arena.allocate(1)
        private val setAssociatedObject = linker.downcallHandle(
            lookup.find("objc_setAssociatedObject").orElseThrow {
                UnsatisfiedLinkError("objc_setAssociatedObject not found in libobjc")
            },
            FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
            ),
        )
        private val getAssociatedObject = linker.downcallHandle(
            lookup.find("objc_getAssociatedObject").orElseThrow {
                UnsatisfiedLinkError("objc_getAssociatedObject not found in libobjc")
            },
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

        override fun attach(receiver: MemorySegment, token: AppKitNativeCallbackToken) {
            val number = ObjCRuntime.msgSend(
                ValueLayout.ADDRESS,
                ObjCRuntime.getClass("NSNumber"),
                ObjCRuntime.sel("numberWithLongLong:"),
                token.value,
            ) as MemorySegment
            // OBJC_ASSOCIATION_RETAIN_NONATOMIC
            setAssociatedObject.invokeExact(receiver, associationKey, number, 1L)
        }

        override fun read(receiver: MemorySegment): AppKitNativeCallbackToken? {
            val number = getAssociatedObject.invokeExact(receiver, associationKey) as MemorySegment
            if (number == MemorySegment.NULL) return null
            val value = ObjCRuntime.msgSend(
                ValueLayout.JAVA_LONG,
                number,
                ObjCRuntime.sel("longLongValue"),
            ) as Long
            return AppKitNativeCallbackToken(value)
        }

        override fun detach(receiver: MemorySegment, token: AppKitNativeCallbackToken) {
            if (read(receiver) != token) return
            setAssociatedObject.invokeExact(receiver, associationKey, MemorySegment.NULL, 1L)
        }
    }
}

internal data class AppKitNativeCallbackRouteAcquisition<N : Any, T : Any>(
    val native: N,
    val token: T,
)

internal fun <N : Any, T : Any> appKitAcquireNativeCallbackRouteTransaction(
    allocateNative: () -> N,
    initializeNative: (N) -> N,
    allocateToken: () -> T,
    attachToken: (N, T) -> Unit,
    insertRoute: (T) -> Unit,
    removeRoute: (T) -> Unit,
    detachToken: (N, T) -> Unit,
    releaseNative: (N) -> Unit,
): AppKitNativeCallbackRouteAcquisition<N, T> {
    var allocatedNative: N? = null
    var initializedNative: N? = null
    var token: T? = null
    var tokenAttachAttempted = false
    var routeInsertAttempted = false
    try {
        val allocated = allocateNative()
        allocatedNative = allocated
        val initialized = initializeNative(allocated)
        initializedNative = initialized
        val allocatedToken = allocateToken()
        token = allocatedToken
        tokenAttachAttempted = true
        attachToken(initialized, allocatedToken)
        routeInsertAttempted = true
        insertRoute(allocatedToken)
        return AppKitNativeCallbackRouteAcquisition(initialized, allocatedToken)
    } catch (primary: Throwable) {
        fun cleanup(step: () -> Unit) {
            try {
                step()
            } catch (cleanupFailure: Throwable) {
                if (cleanupFailure !== primary) primary.addSuppressed(cleanupFailure)
            }
        }
        if (routeInsertAttempted) token?.let { cleanup { removeRoute(it) } }
        if (tokenAttachAttempted) {
            val native = initializedNative
            val attachedToken = token
            if (native != null && attachedToken != null) cleanup { detachToken(native, attachedToken) }
        }
        (initializedNative ?: allocatedNative)?.let { cleanup { releaseNative(it) } }
        throw primary
    }
}

/** Tracks all native upcalls so a completed run cannot hand its slot to the next run too early. */
internal object AppKitNativeCallbackBoundary {
    private const val RELEASE_GATE = Long.MIN_VALUE
    private const val COUNT_MASK = 0x0000_0000_FFFF_FFFFL
    private const val EPOCH_MASK = 0x7FFF_FFFF_0000_0000L
    private const val EPOCH_INCREMENT = 0x0000_0001_0000_0000L

    private data class AdmissionTicket(
        val epoch: Long,
        val admitted: Boolean,
    )

    private enum class AdmissionState {
        OPEN,
        PAUSED,
        CLOSED,
    }

    private val lock = ReentrantLock()
    private val teardownLock = ReentrantLock()
    private val ownershipGateLock = ReentrantLock(true)
    private val quiescent = lock.newCondition()
    private val ticketGateChanged = lock.newCondition()
    private val ticketState = AtomicLong(0L)
    private var admissionState = AdmissionState.OPEN
    private val callbackDepth = ThreadLocal.withInitial { 0 }

    val isInCallback: Boolean
        get() = callbackDepth.get() > 0

    val hasActiveCallbacks: Boolean
        get() = ticketCount(ticketState.get()) != 0L

    fun invoke(callback: () -> Unit) {
        invoke(callback, onRejected = {})
    }

    internal fun invoke(callback: () -> Unit, onRejected: () -> Unit) {
        val ticket = enter()
        callbackDepth.set(callbackDepth.get() + 1)
        try {
            if (ticket.admitted) callback() else onRejected()
        } finally {
            callbackDepth.set(callbackDepth.get() - 1)
            leave(ticket)
        }
    }

    fun <T> invokeOrDefault(defaultValue: T, callback: () -> T): T {
        val ticket = enter()
        callbackDepth.set(callbackDepth.get() + 1)
        return try {
            if (ticket.admitted) callback() else defaultValue
        } finally {
            callbackDepth.set(callbackDepth.get() - 1)
            leave(ticket)
        }
    }

    private fun enter(): AdmissionTicket {
        var state = ticketState.get()
        val observedEpoch = epochOf(state)
        while (true) {
            val synchronousReleaseUpcall = ownershipGateLock.isHeldByCurrentThread
            if (!isReleaseGateClosed(state) || synchronousReleaseUpcall) {
                check(ticketCount(state) != COUNT_MASK) { "Native callback ticket count overflow" }
                if (ticketState.compareAndSet(state, state + 1L)) break
            } else {
                lock.withLock {
                    while (isReleaseGateClosed(ticketState.get()) &&
                        !ownershipGateLock.isHeldByCurrentThread
                    ) {
                        ticketGateChanged.awaitUninterruptibly()
                    }
                }
            }
            state = ticketState.get()
        }
        return lock.withLock {
            val currentEpoch = epochOf(ticketState.get())
            val synchronousTeardownUpcall = teardownLock.isHeldByCurrentThread
            AdmissionTicket(
                epoch = currentEpoch,
                admitted = synchronousTeardownUpcall ||
                    (admissionState == AdmissionState.OPEN && observedEpoch == currentEpoch),
            )
        }
    }

    private fun leave(ticket: AdmissionTicket) {
        val previousState = ticketState.getAndDecrement()
        check(ticketCount(previousState) != 0L) { "Native callback ticket count underflow" }
        lock.withLock {
            check(ticket.epoch <= epochOf(ticketState.get())) { "Native callback admission epoch moved backwards" }
            if (ticketCount(ticketState.get()) == 0L) quiescent.signalAll()
        }
    }

    fun awaitQuiescence() {
        check(!isInCallback) { "Cannot await native callback quiescence from an active upcall" }
        lock.withLock {
            awaitZeroTickets()
        }
    }

    fun <T> runExclusive(action: () -> T): T = teardownLock.withLock {
        check(!isInCallback) { "Cannot run native teardown from an active upcall" }
        var pausedHere = false
        lock.withLock {
            if (admissionState == AdmissionState.OPEN) {
                advanceEpoch()
                admissionState = AdmissionState.PAUSED
                pausedHere = true
            }
            awaitZeroTickets()
        }
        try {
            action()
        } finally {
            if (pausedHere) {
                lock.withLock {
                    awaitZeroTickets()
                    advanceEpoch()
                    admissionState = AdmissionState.OPEN
                }
            }
        }
    }

    fun <T> releaseWhenQuiescent(releaseOwnership: () -> T): T {
        check(!isInCallback) { "Cannot release native ownership from an active upcall" }
        return withClosedTicketGate(releaseOwnership)
    }

    fun closeAdmissionForTeardown() {
        check(!isInCallback) { "Cannot close callback admission from an active upcall" }
        teardownLock.lock()
        try {
            lock.withLock {
                advanceEpoch()
                admissionState = AdmissionState.CLOSED
                awaitZeroTickets()
            }
        } catch (failure: Throwable) {
            teardownLock.unlock()
            throw failure
        }
    }

    fun finishTeardown(releaseRunSlot: () -> Unit) {
        try {
            withClosedTicketGate {
                lock.withLock {
                    awaitZeroTickets()
                    try {
                        releaseRunSlot()
                    } finally {
                        advanceEpoch()
                        admissionState = AdmissionState.OPEN
                    }
                }
            }
        } finally {
            teardownLock.unlock()
        }
    }

    private fun awaitZeroTickets() {
        while (ticketCount(ticketState.get()) != 0L) quiescent.awaitUninterruptibly()
    }

    private fun advanceEpoch() {
        ticketState.updateAndGet { state ->
            val nextEpoch = ((state and EPOCH_MASK) + EPOCH_INCREMENT) and EPOCH_MASK
            (state and (RELEASE_GATE or COUNT_MASK)) or nextEpoch
        }
    }

    private fun <T> withClosedTicketGate(action: () -> T): T = ownershipGateLock.withLock {
        val gateWasAlreadyClosed = isReleaseGateClosed(ticketState.get())
        if (!gateWasAlreadyClosed) {
            ticketState.updateAndGet { state -> state or RELEASE_GATE }
        }
        try {
            lock.withLock { awaitZeroTickets() }
            action()
        } finally {
            if (!gateWasAlreadyClosed) {
                ticketState.updateAndGet { state -> state and RELEASE_GATE.inv() }
                lock.withLock { ticketGateChanged.signalAll() }
            }
        }
    }

    private fun epochOf(state: Long): Long = state and EPOCH_MASK

    private fun ticketCount(state: Long): Long = state and COUNT_MASK

    private fun isReleaseGateClosed(state: Long): Boolean = state and RELEASE_GATE != 0L
}
