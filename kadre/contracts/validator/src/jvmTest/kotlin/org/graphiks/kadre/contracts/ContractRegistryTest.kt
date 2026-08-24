package org.graphiks.kadre.contracts

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContractRegistryTest {
    @Test
    fun parsesAnActiveContractWithEvidenceLists() {
        val text =
            "contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef\n" +
                "SES-001\tactive\tDESIGN.md#5\tKadreSession\tterminal race\tO2\tstop-wins,failure-wins\tjvm\t-\tlate-resource\t-"

        val record = ContractRegistry.parse(text).single()

        assertEquals("SES-001", record.contractId)
        assertEquals(ContractStatus.Active, record.status)
        assertEquals(listOf("stop-wins", "failure-wins"), record.scenarios)
        assertEquals(listOf("jvm"), record.requiredTargets)
        assertEquals(emptyList(), record.conditionalCapabilities)
    }

    @Test
    fun rejectsAnUnknownStatusWhileParsing() {
        val exception = assertFailsWith<IllegalStateException> {
            ContractRegistry.parse(
                "$HEADER\n" +
                    "SES-001\tdraft\tDESIGN.md#5\tKadreSession\tterminal race\tO2\tstop-wins\tjvm\t-\tlate-resource\t-",
            )
        }

        assertContains(exception.message.orEmpty(), "unknown contract status: draft")
    }

    @Test
    fun activeContractRequiresExecutableEvidence() {
        val records = ContractRegistry.parse(
            "$HEADER\n" +
                "SES-001\tactive\tDESIGN.md#5\tKadreSession\tterminal race\tO2\t-\t-\t-\t-\t-",
        )

        val errors = ContractRegistry.validate(records)

        assertTrue(errors.any { "SES-001" in it && "scenarios" in it })
        assertTrue(errors.any { "SES-001" in it && "requiredTargets" in it })
        assertTrue(errors.any { "SES-001" in it && "sentinels" in it })
    }

    @Test
    fun blankListCellsAreNotAcceptedAsEvidence() {
        val records = ContractRegistry.parse(
            "$HEADER\n" +
                "SES-001\tactive\tDESIGN.md#5\tKadreSession\tterminal race\tO2\t\t\t\t\t-",
        )

        val errors = ContractRegistry.validate(records)

        assertTrue(errors.any { "SES-001" in it && "scenarios" in it })
        assertTrue(errors.any { "SES-001" in it && "requiredTargets" in it })
        assertTrue(errors.any { "SES-001" in it && "conditionalCapabilities" in it })
        assertTrue(errors.any { "SES-001" in it && "sentinels" in it })
    }

    @Test
    fun duplicateContractIdsAreRejected() {
        val records = ContractRegistry.parse(
            "$HEADER\n" +
                "SES-001\tplanned\tDESIGN.md#5\tKadreSession\tterminal race\tO2\t-\t-\t-\t-\t-\n" +
                "SES-001\tplanned\tDESIGN.md#6\tKadreScope\tcancellation race\tO2\t-\t-\t-\t-\t-",
        )

        val errors = ContractRegistry.validate(records)

        assertTrue(errors.any { "duplicate contractId: SES-001" in it })
    }

    @Test
    fun retiredContractRequiresAReferenceAndForbidsExecutableEvidence() {
        val records = ContractRegistry.parse(
            "$HEADER\n" +
                "SES-001\tretired\tDESIGN.md#5\tKadreSession\tterminal race\tO2\told-scenario\tjvm\t-\told-sentinel\t-",
        )

        val errors = ContractRegistry.validate(records)

        assertTrue(errors.any { "SES-001" in it && "retirementRef" in it })
        assertTrue(errors.any { "SES-001" in it && "scenarios" in it })
        assertTrue(errors.any { "SES-001" in it && "requiredTargets" in it })
        assertTrue(errors.any { "SES-001" in it && "sentinels" in it })
    }

    @Test
    fun missingRegistryFileIsRejected() {
        val exception = assertFailsWith<IllegalArgumentException> {
            validateContractRegistry(Path.of("does-not-exist", "contracts.tsv"))
        }

        assertContains(exception.message.orEmpty(), "does not exist")
    }

    private companion object {
        const val HEADER =
            "contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef"
    }
}
