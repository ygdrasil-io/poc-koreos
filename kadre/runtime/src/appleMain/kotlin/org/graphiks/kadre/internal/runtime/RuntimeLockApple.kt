@file:OptIn(ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.runtime

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_trylock
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t
import platform.posix.pthread_mutex_init
import platform.posix.pthread_self
import kotlin.concurrent.AtomicInt

/**
 * Recursive pthread mutex plus consultative owner/depth bookkeeping.
 *
 * The JVM `synchronized` monitor is reentrant and [isHeldByCurrentThread] answers from it; a
 * default `pthread_mutex_t` on Darwin is NOT recursive, so the initializer pins
 * `PTHREAD_MUTEX_RECURSIVE`. The owner identity is the hashCode of the current `pthread_self()` —
 * consultative only, mirroring `Thread.holdsLock`; the mutual exclusion itself is entirely the
 * mutex's. No destroy: the JVM actual never frees its monitor either, so the lifetime is the
 * session's.
 *
 * The fields are `internal` (not `private`) because [withLock] and [isHeldByCurrentThread] are
 * real extensions — the expect declares them as extensions and the actuals must stay extensions,
 * exactly like the jvm and js actuals.
 */
internal actual class RuntimeLock actual constructor() {
    internal val mutex: pthread_mutex_t = nativeHeap.alloc()
    internal val ownerTid = AtomicInt(0)
    internal val depth = AtomicInt(0)

    init {
        memScoped {
            val attr = alloc<pthread_mutexattr_t>()
            pthread_mutexattr_init(attr.ptr)
            pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_RECURSIVE)
            pthread_mutex_init(mutex.ptr, attr.ptr)
            pthread_mutexattr_destroy(attr.ptr)
        }
    }
}

internal actual inline fun <T> RuntimeLock.withLock(action: () -> T): T {
    val self = pthread_self().hashCode()
    if (ownerTid.value == self && depth.value > 0) {
        depth.value += 1
        pthread_mutex_lock(mutex.ptr) // recursive: the reentrancy is real on the pthread side too
    } else {
        pthread_mutex_lock(mutex.ptr)
        ownerTid.value = self
        depth.value = 1
    }
    try {
        return action()
    } finally {
        val d = depth.value - 1
        depth.value = d
        if (d == 0) ownerTid.value = 0
        pthread_mutex_unlock(mutex.ptr)
    }
}

internal actual fun RuntimeLock.isHeldByCurrentThread(): Boolean {
    if (depth.value > 0 && ownerTid.value == pthread_self().hashCode()) return true
    // Trylock probe: a free mutex is taken and immediately released (answer false); a busy mutex
    // means the answer comes from the recorded owner.
    val acquired = pthread_mutex_trylock(mutex.ptr) == 0
    if (acquired) {
        pthread_mutex_unlock(mutex.ptr)
        return false
    }
    return depth.value > 0 && ownerTid.value == pthread_self().hashCode()
}
