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

    private val nativeStore: NativeStore by lazy(::NativeStore)

    fun attach(receiver: MemorySegment): AppKitNativeCallbackToken =
        next().also { nativeStore.attach(receiver, it) }

    fun attachTestAddress(address: Long): AppKitNativeCallbackToken =
        next().also {
            testAddresses += address
            testTokensByAddress[address] = it
        }

    fun read(receiver: MemorySegment): AppKitNativeCallbackToken? {
        val address = receiver.address()
        testTokensByAddress[address]?.let { return it }
        if (address in testAddresses) return null
        return nativeStore.read(receiver)
    }

    fun readTestAddress(address: Long): AppKitNativeCallbackToken? = testTokensByAddress[address]

    fun detachTestAddress(address: Long, token: AppKitNativeCallbackToken) {
        testTokensByAddress.remove(address, token)
    }

    private fun next(): AppKitNativeCallbackToken = AppKitNativeCallbackToken(nextToken.incrementAndGet())

    private class NativeStore {
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

        fun attach(receiver: MemorySegment, token: AppKitNativeCallbackToken) {
            val number = ObjCRuntime.msgSend(
                ValueLayout.ADDRESS,
                ObjCRuntime.getClass("NSNumber"),
                ObjCRuntime.sel("numberWithLongLong:"),
                token.value,
            ) as MemorySegment
            // OBJC_ASSOCIATION_RETAIN_NONATOMIC
            setAssociatedObject.invokeExact(receiver, associationKey, number, 1L)
        }

        fun read(receiver: MemorySegment): AppKitNativeCallbackToken? {
            val number = getAssociatedObject.invokeExact(receiver, associationKey) as MemorySegment
            if (number == MemorySegment.NULL) return null
            val value = ObjCRuntime.msgSend(
                ValueLayout.JAVA_LONG,
                number,
                ObjCRuntime.sel("longLongValue"),
            ) as Long
            return AppKitNativeCallbackToken(value)
        }
    }
}

/** Tracks all native upcalls so a completed run cannot hand its slot to the next run too early. */
internal object AppKitNativeCallbackBoundary {
    private val lock = ReentrantLock()
    private val quiescent = lock.newCondition()
    private var activeCallbacks = 0
    private val callbackDepth = ThreadLocal.withInitial { 0 }

    val isInCallback: Boolean
        get() = callbackDepth.get() > 0

    fun <T> invoke(callback: () -> T): T {
        lock.withLock {
            activeCallbacks += 1
        }
        callbackDepth.set(callbackDepth.get() + 1)
        return try {
            callback()
        } finally {
            callbackDepth.set(callbackDepth.get() - 1)
            lock.withLock {
                activeCallbacks -= 1
                if (activeCallbacks == 0) quiescent.signalAll()
            }
        }
    }

    fun awaitQuiescence() {
        check(!isInCallback) { "Cannot await native callback quiescence from an active upcall" }
        lock.withLock {
            while (activeCallbacks != 0) quiescent.await()
        }
    }
}
