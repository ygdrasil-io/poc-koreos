package org.graphiks.kadre.internal.runtime

import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kadre.surface.SurfaceId

/**
 * The native stdlib has no `kotlin.concurrent.thread` builder on 2.4.20; each Worker is backed by
 * one real native thread, which is all these tests need: parallel mutators, cross-thread lock
 * observation and per-thread frame isolation.
 */
private fun <T> onNativeThread(action: () -> T): T {
    val worker = Worker.start()
    try {
        return worker.execute(TransferMode.SAFE, { action }) { it() }.consume { it }
    } finally {
        worker.requestTermination(processScheduledJobs = false).consume { }
    }
}

class NativePrimitivesConcurrencyTest {

    @Test
    fun lockProvidesRealMutualExclusionAcrossNativeThreads() {
        val lock = RuntimeLock()
        var counter = 0
        val threads = (1..4).map { worker ->
            Worker.start()
        }
        val futures = threads.map { worker ->
            worker.execute(TransferMode.SAFE, { lock to { counter += 1 } }) { (lock, increment) ->
                repeat(10_000) {
                    lock.withLock { increment() }
                }
            }
        }
        futures.forEach { it.consume { } }
        threads.forEach { it.requestTermination(processScheduledJobs = false).consume { } }
        assertEquals(40_000, counter)
    }

    @Test
    fun lockIsReentrantWithinOneThread() {
        val lock = RuntimeLock()
        val nested = lock.withLock {
            lock.withLock { lock.isHeldByCurrentThread() }
        }
        assertTrue(nested)
        lock.withLock { assertTrue(lock.isHeldByCurrentThread()) }
        assertFalse(lock.isHeldByCurrentThread())
    }

    @Test
    fun isHeldByCurrentThreadIsPerThread() {
        val lock = RuntimeLock()
        val captureLock = RuntimeLock()
        val results = mutableListOf<Boolean>()
        lock.withLock {
            val held = onNativeThread { lock.isHeldByCurrentThread() }
            captureLock.withLock { results.add(held) }
        }
        assertEquals(listOf(false), results)
        // libéré : personne ne le tient
        assertFalse(lock.isHeldByCurrentThread())
    }

    @Test
    fun identityKeyedMapKeysByReferenceIdentity() {
        val map = IdentityKeyedMap<String>()
        val a = Any()
        val b = Any()
        map[a] = "a"
        map[b] = "b"
        assertEquals("a", map[a])
        assertEquals("b", map[b])
        // une copie « égale » n'est pas la même clé
        data class Key(val v: Int)
        val k1 = Key(1)
        val k2 = Key(1)
        map[k1] = "k1"
        map[k2] = "k2"
        assertEquals("k1", map[k1])
        assertEquals("k2", map[k2])
        assertSame("k1", map.remove(k1))
        assertNull(map.remove(k1))
        map.clear()
        assertNull(map[a])
    }

    @Test
    fun interactionCallFrameIsPerThread() {
        val frame = InteractionCallFrame()
        // SurfaceId's constructor is internal to foundation; build one through the runtime's own
        // identifier allocator, which is internal to this module and always visible in tests.
        val surface: SurfaceId = RuntimeProcessIds.nextSurfaceId()
        assertNull(frame.current())
        frame.set(surface)
        assertSame(surface, frame.current())
        val seen = onNativeThread { frame.current() }
        val results = listOf<SurfaceId?>(seen)
        assertEquals(listOf(null), results)
        frame.clear()
        assertNull(frame.current())
    }

    @Test
    fun linkageFailureClassificationStaysFalseOnNative() {
        assertFalse(RuntimeException("x").isLinkageFailure())
        assertFalse(NullPointerException().isLinkageFailure())
    }
}
