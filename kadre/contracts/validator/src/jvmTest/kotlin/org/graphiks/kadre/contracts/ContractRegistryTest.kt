package org.graphiks.kadre.contracts

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
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
    fun plannedContractWithDeclaredWebTargetsPassesStructuralValidationWithoutMappings() {
        val records = ContractRegistry.parse(
            "$HEADER\n" +
                "BCK-001\tplanned\tDESIGN.md#15.3\tweb attachment\tmissing browser evidence\tO2\tweb-attach-connected\tjs,wasmJs\t-\tweb-lease-bounded\t-",
        )

        assertEquals(emptyList(), ContractRegistry.validate(records))
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

    @Test
    fun activeContractOutsideExplicitGateIsNotInferredFromItsPrefix() {
        val fixture = createTempDirectory("kadre-explicit-contract-gate-")
        val registry = fixture.resolve("contracts.tsv").also {
            it.writeText(
                "$HEADER\n" +
                    "APK-099\tactive\tDESIGN.md#explicit-gate\tnon-gated contract\trisk\tO2\tscenario\tjvm\t-\tsentinel\t-",
            )
        }
        val mapping = fixture.resolve("evidence.tsv").also { it.writeText(EMPTY_MAPPING) }

        assertEquals(emptyList(), validateContractRegistry(registry, listOf(mapping), emptySet()))
    }

    @Test
    fun activeMappingOutsideExplicitGateIsRejectedRegardlessOfContractPrefix() {
        val fixture = createTempDirectory("kadre-explicit-contract-gate-")
        val registry = fixture.resolve("contracts.tsv").also {
            it.writeText(
                "$HEADER\n" +
                    "SES-099\tactive\tDESIGN.md#explicit-gate\tnon-prefixed contract\trisk\tO2\tscenario\tjvm\t-\tsentinel\t-",
            )
        }
        val mapping = fixture.resolve("evidence.tsv").also {
            it.writeText(
                "$MAPPING_HEADER\n" +
                    "SES-099\tjvm\tscenario\tscenario\torg.graphiks.kadre.SessionTest\tscenario[jvm]\n" +
                    "SES-099\tjvm\tsentinel\tsentinel\torg.graphiks.kadre.SessionTest\tsentinel[jvm]",
            )
        }

        val errors = validateContractRegistry(registry, listOf(mapping), emptySet())

        assertEquals(listOf("SES-099: active mapping is outside configured evidence gates"), errors)
    }

    @Test
    fun plannedContractInExplicitGateDoesNotRequireMapping() {
        val fixture = createTempDirectory("kadre-planned-contract-gate-")
        val registry = fixture.resolve("contracts.tsv").also { it.writeText(PLANNED_GATE_REGISTRY) }
        val mapping = fixture.resolve("evidence.tsv").also { it.writeText(EMPTY_MAPPING) }

        assertEquals(
            emptyList(),
            validateContractRegistry(registry, listOf(mapping), setOf("SES-098")),
        )
    }

    @Test
    fun activatingAPlannedGateImmediatelyRequiresCompleteMapping() {
        val fixture = createTempDirectory("kadre-active-contract-gate-")
        val registry = fixture.resolve("contracts.tsv").also {
            it.writeText(PLANNED_GATE_REGISTRY.replace("SES-098\tplanned", "SES-098\tactive"))
        }
        val mapping = fixture.resolve("evidence.tsv").also { it.writeText(EMPTY_MAPPING) }

        val errors = validateContractRegistry(registry, listOf(mapping), setOf("SES-098"))

        assertTrue(errors.any { it == "SES-098[jvm]: missing scenario: scenario" })
        assertTrue(errors.any { it == "SES-098[jvm]: missing sentinel: sentinel" })
    }

    @Test
    fun activeWindowContractsRequireCompleteMappingsAndConfiguredEvidenceGates() {
        val fixture = createTempDirectory("kadre-window-contracts-")
        val registry = fixture.resolve("contracts.tsv").also { it.writeText(WINDOW_REGISTRY) }
        val incompleteMapping = fixture.resolve("incomplete-evidence.tsv").also { it.writeText(INCOMPLETE_WINDOW_MAPPING) }
        val completeMapping = fixture.resolve("complete-evidence.tsv").also { it.writeText(WINDOW_MAPPING) }

        val incompleteErrors = validateContractRegistry(
            registry,
            listOf(incompleteMapping),
            setOf("WIN-001", "APK-006"),
        )
        assertTrue(incompleteErrors.any { "WIN-001[jvm]: missing sentinel: runtime-window-geometry-policy-bypass" in it })

        val outsideGateErrors = validateContractRegistry(
            registry,
            listOf(completeMapping),
            setOf("WIN-001"),
        )
        assertTrue(outsideGateErrors.any { "APK-006: active mapping is outside configured evidence gates" in it })

        assertEquals(
            emptyList(),
            validateContractRegistry(
                registry,
                listOf(completeMapping),
                setOf("WIN-001", "APK-006"),
            ),
        )
    }

    @Test
    fun activeContractWithoutMappingCanRemainOutsideExplicitGate() {
        val fixture = createTempDirectory("kadre-window-contracts-")
        val registry = fixture.resolve("contracts.tsv").also { it.writeText(WINDOW_REGISTRY) }
        val mapping = fixture.resolve("evidence.tsv").also { it.writeText(WIN_ONLY_MAPPING) }

        assertEquals(
            emptyList(),
            validateContractRegistry(
                registry,
                listOf(mapping),
                setOf("WIN-001"),
            ),
        )
    }

    private companion object {
        const val HEADER =
            "contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef"
        const val MAPPING_HEADER = "contractId\ttarget\tkind\tevidenceId\ttestClass\ttestName"
        const val EMPTY_MAPPING = MAPPING_HEADER
        const val PLANNED_GATE_REGISTRY =
            "$HEADER\n" +
                "SES-098\tplanned\tDESIGN.md#explicit-gate\tplanned gate\trisk\tO2\tscenario\tjvm\t-\tsentinel\t-"
        const val WINDOW_REGISTRY =
            "$HEADER\n" +
                "WIN-001\tactive\tAPPKIT-PHASE-5-WINDOW-GEOMETRY-DESIGN.md#Preuves\truntime geometry\tmissed delivery\tO2\truntime-window-geometry-validation\tjvm\t-\truntime-window-geometry-policy-bypass\t-\n" +
                "APK-006\tactive\tAPPKIT-PHASE-5-WINDOW-GEOMETRY-DESIGN.md#Preuves\tAppKit geometry\tmissed activation\tO3\tappkit-window-geometry-public-activation\tjvm\tWindowCapabilities.contentSize\tappkit-window-geometry-policy-bypass\t-"
        const val WINDOW_MAPPING =
            "$MAPPING_HEADER\n" +
                "WIN-001\tjvm\tscenario\truntime-window-geometry-validation\torg.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest\twindowUpdateValidatesCombinedSizeConstraintsBeforeDispatch[jvm]\n" +
                "WIN-001\tjvm\tsentinel\truntime-window-geometry-policy-bypass\torg.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest\twindowGeometryEventsFollowConfiguredDeliveryPolicy[jvm]\n" +
                "APK-006\tjvm\tscenario\tappkit-window-geometry-public-activation\torg.graphiks.kadre.internal.appkit.AppKitBackendProviderTest\tpublicAppKitWindowGeometryActivatesOnlyTheFourProvenCapabilitiesOnMacOs[jvm]\n" +
                "APK-006\tjvm\tsentinel\tappkit-window-geometry-policy-bypass\torg.graphiks.kadre.internal.appkit.AppKitBackendProviderTest\tpublicAppKitWindowGeometryEventsFollowSessionPolicyOnMacOs[jvm]"
        const val INCOMPLETE_WINDOW_MAPPING =
            "$MAPPING_HEADER\n" +
                "WIN-001\tjvm\tscenario\truntime-window-geometry-validation\torg.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest\twindowUpdateValidatesCombinedSizeConstraintsBeforeDispatch[jvm]\n" +
                "APK-006\tjvm\tscenario\tappkit-window-geometry-public-activation\torg.graphiks.kadre.internal.appkit.AppKitBackendProviderTest\tpublicAppKitWindowGeometryActivatesOnlyTheFourProvenCapabilitiesOnMacOs[jvm]\n" +
                "APK-006\tjvm\tsentinel\tappkit-window-geometry-policy-bypass\torg.graphiks.kadre.internal.appkit.AppKitBackendProviderTest\tpublicAppKitWindowGeometryEventsFollowSessionPolicyOnMacOs[jvm]"
        const val WIN_ONLY_MAPPING =
            "$MAPPING_HEADER\n" +
                "WIN-001\tjvm\tscenario\truntime-window-geometry-validation\torg.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest\twindowUpdateValidatesCombinedSizeConstraintsBeforeDispatch[jvm]\n" +
                "WIN-001\tjvm\tsentinel\truntime-window-geometry-policy-bypass\torg.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest\twindowGeometryEventsFollowConfiguredDeliveryPolicy[jvm]"
    }
}
