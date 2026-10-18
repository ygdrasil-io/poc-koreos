package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import kotlin.test.*

class RuntimeRetainedPayloadBudgetTest {
    @Test fun imageAndTextReservationsCompeteForOneSessionLimit() {
        val budget = RuntimeRetainedPayloadBudget(16)
        val resources = KadrePolicies.Default.resources.copy(maxRetainedPayloadBytesPerSession=16,
            maxImageBytesPerResource=16, maxDropChunkBytes=8)
        val images = RuntimeImageBudget(resources, budget)
        val image = BinaryImage(ByteArray(12), ImageFormat.Png)
        val first = assertIs<KadreResult.Success<RuntimeImageLease>>(images.retain(image)).value
        val alias = assertIs<KadreResult.Success<RuntimeImageLease>>(images.retain(image)).value
        assertEquals(12L, budget.used)
        assertEquals(KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload, 16)), budget.retain(6))
        first.close(); first.close(); assertEquals(12L, budget.used)
        alias.close(); assertEquals(0L, budget.used)
        val text = assertIs<KadreResult.Success<AutoCloseable>>(budget.retain(6)).value
        assertEquals(6L, budget.used)
        text.close(); text.close(); assertEquals(0L, budget.used)
    }
    @Test fun reservationsRejectOverflowAndNegativeBytesWithoutChangingTheLimit() {
        val budget = RuntimeRetainedPayloadBudget(Long.MAX_VALUE)
        val all = assertIs<KadreResult.Success<AutoCloseable>>(budget.retain(Long.MAX_VALUE)).value
        assertIs<KadreResult.Failure>(budget.retain(1))
        assertIs<KadreResult.Failure>(budget.retain(-1))
        assertEquals(Long.MAX_VALUE, budget.used)
        all.close(); assertEquals(0L, budget.used)
    }
}
