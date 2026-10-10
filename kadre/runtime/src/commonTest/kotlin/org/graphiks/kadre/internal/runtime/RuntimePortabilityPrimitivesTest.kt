package org.graphiks.kadre.internal.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract of the portability primitives the ordinary-input pipeline needs once it lives in
 * `commonMain`: identity keying, checked arithmetic, linkage classification and the shared lock.
 *
 * Each test runs on every target that has a test task, so the assertions only rely on behaviour
 * that is observable on jvm, js and wasmJs alike.
 */
class RuntimePortabilityPrimitivesTest {
    @Test
    fun identityKeyedMapKeysByReferenceNotEquality() {
        val map = IdentityKeyedMap<String>()
        val first = ValueEqualKey(1)
        val second = ValueEqualKey(1)
        assertEquals(first, second, "the two keys must be equal by value for this test to mean anything")
        assertFalse(first === second)

        assertNull(map[first], "an absent key reads back as null")

        map[first] = "first"
        map[second] = "second"
        assertEquals("first", map[first])
        assertEquals("second", map[second], "equal-but-distinct keys keep their own values")
        assertNull(map[ValueEqualKey(2)], "an absent key stays absent")

        assertEquals("first", map.remove(first), "remove returns the value held by that reference")
        assertNull(map[first], "the removed reference is gone")
        assertNull(map.remove(first), "a second remove of the same reference reports absence")
        assertEquals("second", map[second], "remove only drops the reference it was given")

        map[second] = "second-replaced"
        assertEquals("second-replaced", map[second], "a value is replaceable for the same reference")

        map.clear()
        assertNull(map[second])
    }

    @Test
    fun checkedAddReportsOverflowInsteadOfWrapping() {
        assertEquals(7L, checkedAdd(3L, 4L))
        assertEquals(-7L, checkedAdd(-3L, -4L))
        assertEquals(0L, checkedAdd(0L, 0L))
        assertEquals(4L, checkedAdd(9L, -5L))
        assertEquals(Long.MAX_VALUE, checkedAdd(Long.MAX_VALUE, 0L))
        assertEquals(Long.MIN_VALUE, checkedAdd(Long.MIN_VALUE, 0L))
        assertEquals(Long.MAX_VALUE, checkedAdd(Long.MAX_VALUE - 1L, 1L))
        assertEquals(Long.MIN_VALUE, checkedAdd(Long.MIN_VALUE + 1L, -1L))

        assertEquals(Long.MIN_VALUE, Long.MAX_VALUE + 1L, "the silent wrap the helper exists to prevent")

        val overflow = assertFailsWith<IllegalStateException> { checkedAdd(Long.MAX_VALUE, 1L) }
        assertEquals("checked add space exhausted: 9223372036854775807 + 1", overflow.message)

        assertFailsWith<IllegalStateException> { checkedAdd(Long.MIN_VALUE, -1L) }
        assertFailsWith<IllegalStateException> { checkedAdd(Long.MAX_VALUE, Long.MAX_VALUE) }
        assertFailsWith<IllegalStateException> { checkedAdd(Long.MIN_VALUE, Long.MIN_VALUE) }
    }

    @Test
    fun linkageFailureClassificationIsPlatformExact() {
        assertFalse(IllegalStateException("ordinary failure").isLinkageFailure())
        assertFalse(RuntimeException("ordinary failure").isLinkageFailure())
        assertFalse(Error("ordinary error").isLinkageFailure())
        assertFalse(LinkageProbe.original.isLinkageFailure())

        // A class initialiser that fails is the portable stand-in for a linkage-level failure,
        // and each family surfaces it in its own way:
        // - the JVM wraps the original failure in its own `LinkageError`, which classifies true;
        // - js/wasmJs, which have no `LinkageError` type at all, surface the original ordinary
        //   exception, which classifies false;
        // - Kotlin/Native wraps the original failure in its own initialiser-failure wrapper
        //   (`kotlin.native.internal.FileFailedToInitializeException`, an `Error` whose cause is
        //   the original). It is an initialisation failure, not a linkage failure — the code it
        //   ran was present and simply threw — so the native actual classifies it false, exactly
        //   like js/wasmJs, instead of widening to "an Error".
        val surfaced = assertNotNull(runCatching { LinkageProbe.FailingInitialiser.toString() }.exceptionOrNull())

        if (surfaced === LinkageProbe.original) {
            assertFalse(surfaced.isLinkageFailure(), "a target without LinkageError reports false for everything")
        } else {
            assertTrue(surfaced.cause === LinkageProbe.original, "the target's own wrapper must carry the original as its cause")
            // On the JVM the wrapper is `ExceptionInInitializerError` (a `LinkageError` subclass —
            // or a bare `LinkageError` depending on how the initialiser fails), which classifies
            // true. Kotlin/Native's wrapper is `FileFailedToInitializeException`, an
            // initialisation failure — not a linkage failure — and classifies false.
            if (surfaced::class.simpleName in setOf("LinkageError", "ExceptionInInitializerError")) {
                assertTrue(surfaced.isLinkageFailure(), "the JVM classifies its own linkage error as a linkage failure")
            } else {
                assertFalse(
                    surfaced.isLinkageFailure(),
                    "a non-JVM wrapper (native's initialiser-failure error) is not a linkage failure and must classify false",
                )
            }
        }
    }

    @Test
    fun runtimeLockNestsReentrantly() {
        val lock = RuntimeLock()

        assertEquals("innermost", lock.withLock { lock.withLock { lock.withLock { "innermost" } } })

        val innerReturn = lock.withLock {
            val inner = lock.withLock { return@withLock "inner" }
            "outer+$inner"
        }
        assertEquals("outer+inner", innerReturn, "an inner return@withLock only leaves the inner block")

        val outerReturn = lock.withLock {
            lock.withLock { }
            return@withLock "outer"
        }
        assertEquals("outer", outerReturn)

        var reenteredFromCall = false
        fun reenter(): String = lock.withLock { "reentered" }
        val fromCall = lock.withLock {
            val value = reenter()
            reenteredFromCall = true
            value
        }
        assertEquals("reentered", fromCall, "the same lock is re-entered through a plain call")
        assertTrue(reenteredFromCall)
    }
}

/** A key whose equality is deliberately value-based, so identity keying is the only difference left. */
private class ValueEqualKey(private val id: Int) {
    override fun equals(other: Any?): Boolean = other is ValueEqualKey && other.id == id

    override fun hashCode(): Int = id

    override fun toString(): String = "ValueEqualKey($id)"
}

private object LinkageProbe {
    val original = IllegalStateException("initialiser failure")

    /** Touching this object fails its class initialiser on every target, in the target's own way. */
    object FailingInitialiser {
        init {
            throw original
        }
    }
}
