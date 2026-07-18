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

    fun attach(receiver: MemorySegment): AppKitNativeCallbackToken =
        next().also { token ->
            nativeStoreLock.withLock { currentNativeStore().attach(receiver, token) }
        }

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

/** Tracks all native upcalls so a completed run cannot hand its slot to the next run too early. */
internal object AppKitNativeCallbackBoundary {
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
    private val quiescent = lock.newCondition()
    private var activeCallbacks = 0
    private var admissionState = AdmissionState.OPEN
    private var admissionEpoch = 0L
    private val callbackDepth = ThreadLocal.withInitial { 0 }

    val isInCallback: Boolean
        get() = callbackDepth.get() > 0

    val hasActiveCallbacks: Boolean
        get() = lock.withLock { activeCallbacks != 0 }

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

    private fun enter(): AdmissionTicket = lock.withLock {
        val synchronousTeardownUpcall = teardownLock.isHeldByCurrentThread
        activeCallbacks += 1
        AdmissionTicket(
            epoch = admissionEpoch,
            admitted = admissionState == AdmissionState.OPEN || synchronousTeardownUpcall,
        )
    }

    private fun leave(ticket: AdmissionTicket) {
        lock.withLock {
            check(ticket.epoch <= admissionEpoch) { "Native callback admission epoch moved backwards" }
            activeCallbacks -= 1
            if (activeCallbacks == 0) quiescent.signalAll()
        }
    }

    fun awaitQuiescence() {
        check(!isInCallback) { "Cannot await native callback quiescence from an active upcall" }
        lock.withLock {
            while (activeCallbacks != 0) quiescent.awaitUninterruptibly()
        }
    }

    fun <T> runExclusive(action: () -> T): T = teardownLock.withLock {
        check(!isInCallback) { "Cannot run native teardown from an active upcall" }
        var pausedHere = false
        lock.withLock {
            if (admissionState == AdmissionState.OPEN) {
                admissionState = AdmissionState.PAUSED
                pausedHere = true
            }
            while (activeCallbacks != 0) quiescent.awaitUninterruptibly()
        }
        try {
            action()
        } finally {
            if (pausedHere) {
                lock.withLock {
                    admissionState = AdmissionState.OPEN
                }
            }
        }
    }

    fun closeAdmissionForTeardown() {
        check(!isInCallback) { "Cannot close callback admission from an active upcall" }
        teardownLock.lock()
        try {
            lock.withLock {
                admissionEpoch += 1
                admissionState = AdmissionState.CLOSED
                while (activeCallbacks != 0) quiescent.awaitUninterruptibly()
            }
        } catch (failure: Throwable) {
            teardownLock.unlock()
            throw failure
        }
    }

    fun finishTeardown(releaseRunSlot: () -> Unit) {
        try {
            lock.withLock {
                try {
                    releaseRunSlot()
                } finally {
                    admissionState = AdmissionState.OPEN
                }
            }
        } finally {
            teardownLock.unlock()
        }
    }
}
