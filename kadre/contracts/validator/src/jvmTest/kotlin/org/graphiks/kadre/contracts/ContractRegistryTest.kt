package org.graphiks.kadre.contracts

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContractRegistryTest {
    @Test
    fun realRegistryDeclaresTheExactPlannedWebContracts() {
        val recordsById = ContractRegistry.parse(repositoryFile("kadre/contracts/registry/contracts.tsv").readText())
            .associateBy(ContractRecord::contractId)

        assertEquals(
            mapOf(
                "BCK-001" to ContractRecord(
                    contractId = "BCK-001",
                    status = ContractStatus.Planned,
                    source = "DESIGN.md#15.3",
                    subject = "DOM host attach and lifecycle",
                    risk = "surface/window confusion, lifecycle loss, cross-session leak or false capability",
                    oracle = ContractOracle.O3,
                    scenarios = listOf(
                        "web-attach-connected",
                        "web-attach-detached-rejected",
                        "web-attach-manual-detached",
                        "web-manual-initial-shadow-reinsert",
                        "web-detach-reparent-batch",
                        "web-shadow-root-detach-reparent",
                        "web-shadow-root-late-reinsert",
                        "web-detach-cross-document-terminal",
                        "web-manual-detach-and-stop",
                        "web-visibility-focus",
                        "web-focus-transfer-between-hosts",
                        "web-attach-detach-admission-race",
                        "web-detach-terminal",
                        "web-multi-session-isolation",
                        "web-attach-same-element-busy",
                        "web-no-implicit-window",
                        "web-window-provider-new-session",
                        "web-window-provider-same-context",
                        "web-window-provider-no-context",
                        "web-window-provider-invalid-element",
                        "web-window-provider-invalid-scope",
                        "web-window-provider-owned-element",
                        "web-window-provider-callback-failure",
                        "web-pagehide-admission-close",
                        "web-pagehide-navigation",
                        "web-pagehide-no-resurrection",
                    ),
                    requiredTargets = listOf("js", "wasmJs"),
                    conditionalCapabilities = listOf("WindowManagerCapabilities.requestWindow"),
                    sentinels = listOf(
                        "web-surface-never-window",
                        "web-no-implicit-dom",
                        "web-cross-session-isolation",
                        "web-host-single-owner",
                        "web-detach-no-resurrection",
                        "web-shadow-root-observation",
                        "web-provider-no-same-document-window",
                        "web-provider-owned-element-rejected",
                        "web-active-gate-requires-js-and-wasm",
                    ),
                    retirementRef = null,
                ),
                "INT-002" to plannedWebContract(
                    contractId = "INT-002",
                    source = "INTEROP-EXPORTS.md#6",
                    subject = "JS and Wasm host facade structural exports",
                    risk = "foreign API drift or leaked coroutine types",
                    oracle = ContractOracle.O1,
                    scenarios = listOf("web-typescript-consumer"),
                    sentinels = listOf("web-host-no-coroutine-leak", "web-host-common-consumer"),
                ),
                "INT-003" to plannedWebContract(
                    contractId = "INT-003",
                    source = "INTEROP-EXPORTS.md#6",
                    subject = "JS and Wasm host facade runtime",
                    risk = "incorrect host outcome, notification ordering or ownership",
                    oracle = ContractOracle.O3,
                    scenarios = listOf(
                        "web-host-attach-failure",
                        "web-host-state-subscription",
                        "web-host-observer-exception",
                        "web-host-stop-close-outcome",
                        "web-host-provider",
                    ),
                    sentinels = listOf(
                        "web-host-microtask-order",
                        "web-host-callback-isolation",
                        "web-host-outcome-rejection",
                    ),
                ),
                "INT-004" to plannedWebContract(
                    contractId = "INT-004",
                    source = "INTEROP-EXPORTS.md#7",
                    subject = "Web element escape hatch",
                    risk = "invalid retained element access or lease/teardown race",
                    oracle = ContractOracle.O3,
                    scenarios = listOf("web-element-lease", "web-element-lease-concurrent-close"),
                    conditionalCapabilities = listOf("SurfaceCapabilities.platformAccess"),
                    sentinels = listOf("web-element-lease-boundary", "web-element-lease-close-order"),
                ),
            ),
            recordsById.filterKeys { it in WEB_CONTRACT_IDS },
        )
    }

    @Test
    fun realRegistryRequiresEveryWebContractInTheExplicitGate() {
        val errors = validateContractRegistry(
            registryPath = repositoryFile("kadre/contracts/registry/contracts.tsv"),
            mappingPaths = repositoryMappingFiles(),
            requiredEvidenceGateIds = WEB_CONTRACT_IDS - "INT-004",
        )

        assertContains(errors, "INT-004: browser contract is outside configured evidence gates")
    }

    @Test
    fun plannedWebGatesAcceptMissingMappingsAndArtifactDirectoriesForBothTargets() {
        listOf("js", "wasmJs").forEach { target ->
            val index = validateContractEvidence(
                registryPath = repositoryFile("kadre/contracts/registry/contracts.tsv"),
                mappingPaths = repositoryMappingFiles(),
                expectedCommit = COMMIT,
                target = target,
                expectedExecutions = ExpectedContractExecutions.Browser(setOf("chromium")),
                gateContractIds = WEB_CONTRACT_IDS,
                artifactDirectories = listOf(createTempDirectory("kadre-absent-$target-browser-evidence-").resolve("missing")),
                junitReportRelativeDirectories = listOf("test-results/browser/{engine}"),
            )

            assertEquals(ContractEvidenceIndex(emptyMap(), emptyMap()), index)
        }
    }

    @Test
    fun activatingAWebGateRequiresMappingsForBothTargets() {
        val fixture = createTempDirectory("kadre-active-web-gate-")
        val registry = fixture.resolve("contracts.tsv").also {
            it.writeText(
                repositoryFile("kadre/contracts/registry/contracts.tsv").readText()
                    .replace("BCK-001\tplanned", "BCK-001\tactive"),
            )
        }

        listOf("js", "wasmJs").forEach { target ->
            val exception = assertFailsWith<IllegalStateException> {
                validateContractEvidence(
                    registryPath = registry,
                    mappingPaths = repositoryMappingFiles(),
                    expectedCommit = COMMIT,
                    target = target,
                    expectedExecutions = ExpectedContractExecutions.Browser(setOf("chromium")),
                    gateContractIds = setOf("BCK-001"),
                    artifactDirectories = listOf(fixture.resolve(target)),
                    junitReportRelativeDirectories = listOf("test-results/browser/{engine}"),
                )
            }

            assertContains(exception.message.orEmpty(), "BCK-001[$target]: missing scenario: web-attach-connected")
        }
    }

    @Test
    fun activeWebGateWithCompleteMappingsRequiresBrowserArtifactForBothTargets() {
        val fixture = createTempDirectory("kadre-active-web-artifact-")
        val registry = fixture.resolve("contracts.tsv").also {
            it.writeText(
                repositoryFile("kadre/contracts/registry/contracts.tsv").readText()
                    .replace("INT-002\tplanned", "INT-002\tactive"),
            )
        }
        val mapping = fixture.resolve("evidence.tsv").also {
            it.writeText(
                "$MAPPING_HEADER\n" +
                    "INT-002\tjs\tscenario\tweb-typescript-consumer\texample.WebConsumerTest\tconsumer[js]\n" +
                    "INT-002\tjs\tsentinel\tweb-host-no-coroutine-leak\texample.WebConsumerTest\tnoCoroutines[js]\n" +
                    "INT-002\tjs\tsentinel\tweb-host-common-consumer\texample.WebConsumerTest\tcommonConsumer[js]\n" +
                    "INT-002\twasmJs\tscenario\tweb-typescript-consumer\texample.WebConsumerTest\tconsumer[wasmJs]\n" +
                    "INT-002\twasmJs\tsentinel\tweb-host-no-coroutine-leak\texample.WebConsumerTest\tnoCoroutines[wasmJs]\n" +
                    "INT-002\twasmJs\tsentinel\tweb-host-common-consumer\texample.WebConsumerTest\tcommonConsumer[wasmJs]",
            )
        }

        listOf("js", "wasmJs").forEach { target ->
            val exception = assertFailsWith<IllegalStateException> {
                validateContractEvidence(
                    registryPath = registry,
                    mappingPaths = listOf(mapping),
                    expectedCommit = COMMIT,
                    target = target,
                    expectedExecutions = ExpectedContractExecutions.Browser(setOf("chromium")),
                    gateContractIds = setOf("INT-002"),
                    artifactDirectories = listOf(fixture.resolve(target)),
                    junitReportRelativeDirectories = listOf("test-results/browser/{engine}"),
                )
            }

            assertContains(exception.message.orEmpty(), "missing evidence artifact for INT-002[$target]/chromium")
        }
    }

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

    private fun repositoryMappingFiles(): List<Path> = listOf(
        repositoryFile("kadre/runtime/contracts/evidence.tsv"),
        repositoryFile("kadre/backend/appkit/contracts/evidence.tsv"),
    )

    private fun repositoryFile(relativePath: String): Path {
        var candidate = Path.of("").toAbsolutePath()
        while (candidate.parent != null) {
            val resolved = candidate.resolve(relativePath)
            if (resolved.toFile().exists()) return resolved
            candidate = candidate.parent
        }
        error("repository file not found: $relativePath")
    }

    private fun plannedWebContract(
        contractId: String,
        source: String,
        subject: String,
        risk: String,
        oracle: ContractOracle,
        scenarios: List<String>,
        conditionalCapabilities: List<String> = emptyList(),
        sentinels: List<String>,
    ): ContractRecord = ContractRecord(
        contractId = contractId,
        status = ContractStatus.Planned,
        source = source,
        subject = subject,
        risk = risk,
        oracle = oracle,
        scenarios = scenarios,
        requiredTargets = listOf("js", "wasmJs"),
        conditionalCapabilities = conditionalCapabilities,
        sentinels = sentinels,
        retirementRef = null,
    )

    private companion object {
        const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
        val WEB_CONTRACT_IDS = setOf("BCK-001", "INT-002", "INT-003", "INT-004")
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
