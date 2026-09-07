package org.graphiks.kadre.contracts

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContractEvidenceMappingTest {
    @Test
    fun mappingCoversEveryDeclaredScenarioAndSentinelExactlyOnceForEveryRequiredTarget() {
        val mappings = ContractEvidenceMapping.parse(COMPLETE_WEB_MAPPING)

        assertEquals(emptyList(), validateMappings(activeWebContract(), mappings))
    }

    @Test
    fun missingEvidenceOnOneTargetIsRejectedWithContractAndTarget() {
        val mappings = ContractEvidenceMapping.parse(
            "$HEADER\n" +
                "BCK-001\tjs\tscenario\tweb-attach-connected\texample.WebTest\tattach[js]\n" +
                "BCK-001\twasmJs\tscenario\tweb-attach-connected\texample.WebTest\tattach[wasmJs]\n" +
                "BCK-001\twasmJs\tsentinel\tweb-lease-bounded\texample.WebTest\tlease[wasmJs]",
        )

        val errors = validateMappings(activeWebContract(), mappings)

        assertContains(errors.joinToString(), "BCK-001[js]: missing sentinel: web-lease-bounded")
    }

    @Test
    fun mappingTargetNotDeclaredByContractIsRejected() {
        val errors = validateMappings(
            activeWebContract(),
            ContractEvidenceMapping.parse(
                "$HEADER\n" +
                    "BCK-001\tjvm\tscenario\tweb-attach-connected\texample.WebTest\tattach[jvm]",
            ),
        )

        assertContains(errors.joinToString(), "BCK-001[jvm]: target is not required")
    }

    @Test
    fun duplicateEvidenceIdIsRejectedPerTargetKindAndEvidenceId() {
        val duplicate = COMPLETE_WEB_MAPPING +
            "\nBCK-001\tjs\tscenario\tweb-attach-connected\texample.OtherTest\totherAttach[js]"

        val errors = validateMappings(activeWebContract(), ContractEvidenceMapping.parse(duplicate))

        assertContains(errors.joinToString(), "BCK-001[js]: duplicate scenario: web-attach-connected")
    }

    @Test
    fun unknownContractIdErrorRemainsCompatible() {
        val errors = validateMappings(
            activeWebContract(),
            ContractEvidenceMapping.parse(
                "$HEADER\n" +
                    "OTHER-001\tjs\tsentinel\tforeign-sentinel\texample.OtherTest\tforeign[js]",
            ),
        )

        assertContains(errors.joinToString(), "BCK-001: unknown contractId: OTHER-001")
    }

    @Test
    fun targetValidationChecksOnlyTheRequestedTargetSubset() {
        val mappings = ContractEvidenceMapping.parse(COMPLETE_WEB_MAPPING)

        assertEquals(emptyList(), validateTargetMappings(activeWebContract(), "js", mappings))
    }

    @Test
    fun plannedContractDoesNotRequireMappings() {
        assertEquals(emptyList(), validateMappings(plannedWebContract(), emptyList()))
    }

    @Test
    fun malformedKindAndColumnsAreRejectedWhileParsing() {
        val unknownKind = assertFailsWith<IllegalArgumentException> {
            ContractEvidenceMapping.parse(
                "$HEADER\nBCK-001\tjs\tproof\tweb-attach-connected\texample.WebTest\tattach[js]",
            )
        }
        assertContains(unknownKind.message.orEmpty(), "unknown evidence kind: proof")

        val missingColumn = assertFailsWith<IllegalArgumentException> {
            ContractEvidenceMapping.parse(
                "$HEADER\nBCK-001\tjs\tscenario\tweb-attach-connected\texample.WebTest",
            )
        }
        assertContains(missingColumn.message.orEmpty(), "expected 6 columns")

        val blankColumn = assertFailsWith<IllegalArgumentException> {
            ContractEvidenceMapping.parse(
                "$HEADER\nBCK-001\t\tscenario\tweb-attach-connected\texample.WebTest\tattach[js]",
            )
        }
        assertContains(blankColumn.message.orEmpty(), "columns must not be blank")
    }

    private fun activeWebContract(): ContractRecord = webContract(ContractStatus.Active)

    private fun plannedWebContract(): ContractRecord = webContract(ContractStatus.Planned)

    private fun webContract(status: ContractStatus): ContractRecord = ContractRecord(
        contractId = "BCK-001",
        status = status,
        source = "DESIGN.md#15.3",
        subject = "web attachment",
        risk = "missing browser evidence",
        oracle = ContractOracle.O2,
        scenarios = listOf(
            "web-attach-connected",
        ),
        requiredTargets = listOf("js", "wasmJs"),
        conditionalCapabilities = emptyList(),
        sentinels = listOf("web-lease-bounded"),
        retirementRef = null,
    )

    private companion object {
        const val HEADER = "contractId\ttarget\tkind\tevidenceId\ttestClass\ttestName"
        const val COMPLETE_WEB_MAPPING =
            "$HEADER\n" +
                "BCK-001\tjs\tscenario\tweb-attach-connected\texample.WebTest\tattach[js]\n" +
                "BCK-001\tjs\tsentinel\tweb-lease-bounded\texample.WebTest\tlease[js]\n" +
                "BCK-001\twasmJs\tscenario\tweb-attach-connected\texample.WebTest\tattach[wasmJs]\n" +
                "BCK-001\twasmJs\tsentinel\tweb-lease-bounded\texample.WebTest\tlease[wasmJs]"
    }
}
