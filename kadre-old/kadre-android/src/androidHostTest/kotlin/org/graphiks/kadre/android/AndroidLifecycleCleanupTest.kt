package org.graphiks.kadre.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidLifecycleCleanupTest {
    @Test
    fun `cleanup runs every stage and suppresses failures after the first`() {
        val calls = mutableListOf<String>()
        val closeFailure = IllegalStateException("close")
        val releaseFailure = IllegalArgumentException("release")
        val superFailure = AssertionError("super")

        val actual = assertFailsWith<IllegalStateException> {
            runAllCleanupStages(
                {
                    calls += "close"
                    throw closeFailure
                },
                {
                    calls += "release"
                    throw releaseFailure
                },
                { calls += "destroyed" },
                {
                    calls += "super"
                    throw superFailure
                },
            )
        }

        assertSame(closeFailure, actual)
        assertEquals(listOf(releaseFailure, superFailure), actual.suppressed.toList())
        assertEquals(listOf("close", "release", "destroyed", "super"), calls)
    }

    @Test
    fun `KadreActivity onDestroy uses the cleanup sequencer`() {
        val classLoader = checkNotNull(KadreActivity::class.java.classLoader)
        val classBytes = checkNotNull(
            classLoader.getResourceAsStream(
                "org/graphiks/kadre/android/KadreActivity.class",
            ),
        ).use { it.readBytes().toString(Charsets.ISO_8859_1) }

        assertTrue(
            classBytes.contains("runAllCleanupStages"),
            "onDestroy must sequence every cleanup stage before rethrowing",
        )
    }
}
