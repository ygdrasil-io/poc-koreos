package org.graphiks.kadre.policy

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PolicyValidationTest {
    @Test
    fun capacitiesAndBudgetsMustBePositive() {
        assertFailsWith<IllegalArgumentException> {
            EventDeliveryPolicy(0, 1, IngressOverflowAction.CloseSource, CollectorOverflowAction.CancelSlowCollector)
        }
        assertFailsWith<IllegalArgumentException> {
            DiagnosticPolicy(0, DiagnosticOverflowAction.DropOldestEvent, DiagnosticDataExposure.Redacted)
        }
        assertFailsWith<IllegalArgumentException> {
            ContinuousDelivery.Buffered(0, ContinuousOverflowAction.CloseSource)
        }
        assertFailsWith<IllegalArgumentException> {
            FrameDelivery.Buffered(0, ContinuousOverflowAction.CloseSource)
        }
        assertFailsWith<IllegalArgumentException> {
            KadrePolicies.Default.resources.copy(maxRetainedPayloadBytesPerSession = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            KadrePolicies.Default.capture.copy(maxBufferedBytesPerSession = 0)
        }
    }

    @Test
    fun collectorAndPayloadRelationshipsAreValidated() {
        assertFailsWith<IllegalArgumentException> {
            KadrePolicies.Default.resources.copy(
                maxEventCollectorsPerFlow = 129,
                maxEventCollectorsPerSession = 128,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            KadrePolicies.Default.resources.copy(
                maxDropChunkBytes = 1024,
                maxRetainedPayloadBytesPerSession = 512,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            KadrePolicies.Default.resources.copy(
                maxImageBytesPerResource = 1024,
                maxRetainedPayloadBytesPerSession = 512,
            )
        }
    }

    @Test
    fun timeoutsMustBeFiniteAndPositive() {
        assertFailsWith<IllegalArgumentException> {
            ExecutionPolicy(ExecutionPriority.Balanced, Duration.INFINITE)
        }
        assertFailsWith<IllegalArgumentException> {
            ExecutionPolicy(ExecutionPriority.Balanced, Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            KadrePolicies.Default.resources.copy(dropTransferClaimTimeout = (-1).seconds)
        }
    }
}
