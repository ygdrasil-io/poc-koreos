package org.graphiks.kadre.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KadreResultTest {
    @Test
    fun mapPreservesFailureAndDoesNotInvokeTransform() {
        val failure = KadreFailure.Closed(KadreResourceKind.Host)
        var invoked = false
        val result = KadreResult.Failure(failure).map {
            invoked = true
            Unit
        }

        assertEquals(false, invoked)
        assertEquals(KadreResult.Failure(failure), result)
    }

    @Test
    fun getOrThrowKeepsTheFailureIdentity() {
        val failure = KadreFailure.ApplicationFailure

        val exception = assertFailsWith<KadreException> {
            KadreResult.Failure(failure).getOrThrow()
        }

        assertEquals(failure, exception.failure)
    }
}
