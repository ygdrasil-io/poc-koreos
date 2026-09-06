package org.graphiks.kadre.contracts

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ValidateContractEvidenceTest {
    @Test
    fun canonicalJvmEvidenceIsValidatedAndIndexedByTargetAndContract() {
        val fixture = jvmFixture()
        fixture.writeJvmEvidence(canonicalJvmEvidence())

        val index = fixture.validate()

        val evidence = assertNotNull(index.junit["jvm" to "INT-002"])
        assertEquals(COMMIT, evidence.commit)
        assertEquals(emptyMap(), index.browser)
    }

    @Test
    fun cliAcceptsJunitOrBrowserEnginesAndRequiresItsSevenArguments() {
        val fixture = jvmFixture()
        fixture.writeJvmEvidence(canonicalJvmEvidence())

        val index = validateContractEvidenceCli(
            arrayOf(
                fixture.registry.toString(),
                fixture.mapping.toString(),
                COMMIT,
                "jvm",
                "junit",
                "INT-002",
                fixture.artifactDirectories.single().toString(),
            ),
        )

        assertNotNull(index.junit["jvm" to "INT-002"])

        val browser = browserFixture(target = "js", engines = setOf("chromium", "firefox"))
        browser.writeBrowserEvidence("job-a", "chromium", canonicalBrowserEvidence("js", "chromium", CHROMIUM_BUNDLE))
        browser.writeBrowserEvidence("job-a", "firefox", canonicalBrowserEvidence("js", "firefox", FIREFOX_BUNDLE))
        val browserIndex = validateContractEvidenceCli(
            arrayOf(
                browser.registry.toString(),
                browser.mapping.toString(),
                COMMIT,
                "js",
                "chromium,firefox",
                "BCK-001",
                browser.artifactDirectories.single().toString(),
            ),
        )
        assertNotNull(browserIndex.browser["js"]?.get("BCK-001")?.get("chromium")?.get(CHROMIUM_BUNDLE))
        assertNotNull(browserIndex.browser["js"]?.get("BCK-001")?.get("firefox")?.get(FIREFOX_BUNDLE))

        val exception = assertFailsWith<IllegalArgumentException> {
            validateContractEvidenceCli(arrayOf(fixture.registry.toString()))
        }
        assertContains(exception.message.orEmpty(), "expected registry, mappings, expected commit")
    }

    @Test
    fun schemaOneReaderRejectsWrongVersionScalarExecutionAndUnknownFields() {
        val mutations = listOf(
            canonicalJvmEvidence().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2") to "schemaVersion must be 1",
            canonicalJvmEvidence().replace("\"execution\": { \"kind\": \"junit\" }", "\"execution\": \"junit\"") to "execution must be an object",
            canonicalJvmEvidence().replace("\"adapter\": \"web-consumer-jvm\"", "\"runner\": \"web-consumer-jvm\"") to "unknown field: runner",
        )

        mutations.forEach { (json, expected) ->
            val fixture = jvmFixture()
            fixture.writeJvmEvidence(json)

            val exception = assertFailsWith<IllegalStateException> { fixture.validate() }

            assertContains(exception.message.orEmpty(), expected)
        }
    }

    @Test
    fun evidenceCommitMustBeAValidGitShaEqualToTheExpectedSha() {
        val invalidFixture = jvmFixture()
        invalidFixture.writeJvmEvidence(canonicalJvmEvidence().replace(COMMIT, "not-a-sha"))
        val invalid = assertFailsWith<IllegalStateException> { invalidFixture.validate() }
        assertContains(invalid.message.orEmpty(), "commit must be a Git SHA")

        val staleFixture = jvmFixture()
        staleFixture.writeJvmEvidence(canonicalJvmEvidence().replace(COMMIT, OTHER_COMMIT))
        val stale = assertFailsWith<IllegalStateException> { staleFixture.validate() }
        assertContains(stale.message.orEmpty(), "commit does not match expected Git SHA")

        val expected = assertFailsWith<IllegalArgumentException> {
            staleFixture.validate(expectedCommit = "local")
        }
        assertContains(expected.message.orEmpty(), "expected commit must be a Git SHA")
    }

    @Test
    fun evidenceTargetMustMatchTheJobTarget() {
        val fixture = jvmFixture()
        fixture.writeJvmEvidence(canonicalJvmEvidence().replace("\"target\": \"jvm\"", "\"target\": \"js\""))

        val exception = assertFailsWith<IllegalStateException> { fixture.validate() }

        assertContains(exception.message.orEmpty(), "target js does not match expected target jvm")
    }

    @Test
    fun scenariosMustExactlyMatchTheMappedContractAndIds() {
        val mutations = listOf(
            canonicalJvmEvidence().replace("\"contractId\": \"INT-002\"", "\"contractId\": \"BCK-001\"") to
                "scenario contractId BCK-001 does not match INT-002",
            canonicalJvmEvidence().replace(
                scenarioJson("web-typescript-consumer", "O1"),
                scenarioJson("wrong-consumer", "O1"),
            ) to "missing scenario: web-typescript-consumer",
            canonicalJvmEvidence().replace(
                "${scenarioJson("web-typescript-consumer", "O1")},\n    ${scenarioJson("web-host-common-consumer", "O1")}",
                scenarioJson("web-typescript-consumer", "O1"),
            ) to "missing scenario: web-host-common-consumer",
        )

        mutations.forEach { (json, expected) ->
            val fixture = jvmFixture()
            fixture.writeJvmEvidence(json)

            val exception = assertFailsWith<IllegalStateException> { fixture.validate() }

            assertContains(exception.message.orEmpty(), expected)
        }
    }

    @Test
    fun sentinelsMustExactlyMatchTheTargetMappings() {
        val fixture = jvmFixture()
        fixture.writeJvmEvidence(
            canonicalJvmEvidence().replace(
                sentinelJson("web-consumer-surface-mismatch"),
                sentinelJson("wrong-sentinel"),
            ),
        )

        val exception = assertFailsWith<IllegalStateException> { fixture.validate() }

        assertContains(exception.message.orEmpty(), "missing sentinel: web-consumer-surface-mismatch")
    }

    @Test
    fun junitCountersMustContainTestsWithoutSkippedFailuresOrErrors() {
        val mutations = listOf(
            "\"tests\": 1" to "\"tests\": 0" to "JUnit evidence contains no tests",
            "\"skipped\": 0" to "\"skipped\": 1" to "skipped=1",
            "\"failures\": 0" to "\"failures\": 1" to "failures=1",
            "\"errors\": 0" to "\"errors\": 1" to "errors=1",
        )

        mutations.forEach { (replacement, expected) ->
            val (from, to) = replacement
            val fixture = jvmFixture()
            fixture.writeJvmEvidence(canonicalJvmEvidence().replace(from, to))

            val exception = assertFailsWith<IllegalStateException> { fixture.validate() }

            assertContains(exception.message.orEmpty(), expected)
        }
    }

    @Test
    fun activeContractRequiresExactlyOneArtifactWhilePlannedContractRequiresNone() {
        val active = jvmFixture()
        val missing = assertFailsWith<IllegalStateException> { active.validate() }
        assertContains(missing.message.orEmpty(), "missing evidence artifact for INT-002[jvm]")

        val planned = jvmFixture(status = "planned")
        val index = planned.validate()
        assertEquals(emptyMap(), index.junit)
    }

    @Test
    fun duplicateBrowserArtifactsForTargetContractAndEngineAreRejected() {
        val fixture = browserFixture(target = "js", engines = setOf("chromium"))
        fixture.writeBrowserEvidence("job-a", "chromium", canonicalBrowserEvidence("js", "chromium", CHROMIUM_BUNDLE))
        fixture.writeBrowserEvidence("job-b", "chromium", canonicalBrowserEvidence("js", "chromium", FIREFOX_BUNDLE))

        val exception = assertFailsWith<IllegalStateException> { fixture.validate() }

        assertContains(exception.message.orEmpty(), "duplicate evidence artifacts for BCK-001[js]/chromium")
    }

    @Test
    fun browserEvidenceRequiresExpectedEngineAndCompleteBundleDescriptor() {
        val missingEngine = browserFixture(target = "js", engines = setOf("chromium"))
        missingEngine.writeBrowserEvidence("job-a", "firefox", canonicalBrowserEvidence("js", "firefox", FIREFOX_BUNDLE))
        val missing = assertFailsWith<IllegalStateException> { missingEngine.validate() }
        assertContains(missing.message.orEmpty(), "missing evidence artifact for BCK-001[js]/chromium")

        val wrongEngine = browserFixture(target = "js", engines = setOf("chromium"))
        wrongEngine.writeBrowserEvidence("job-a", "chromium", canonicalBrowserEvidence("js", "firefox", CHROMIUM_BUNDLE))
        val wrong = assertFailsWith<IllegalStateException> { wrongEngine.validate() }
        assertContains(wrong.message.orEmpty(), "browser engine firefox does not match expected engine chromium")

        val missingBundle = browserFixture(target = "js", engines = setOf("chromium"))
        missingBundle.writeBrowserEvidence(
            "job-a",
            "chromium",
            canonicalBrowserEvidence("js", "chromium", CHROMIUM_BUNDLE)
                .replace(", \"bundleSha256\": \"$CHROMIUM_BUNDLE\"", ""),
        )
        val incomplete = assertFailsWith<IllegalStateException> { missingBundle.validate() }
        assertContains(incomplete.message.orEmpty(), "missing required field: bundleSha256")
    }

    @Test
    fun activeWebContractPassesOnlyWithEveryTargetMappingSentinelAndBrowserEngine() {
        val js = browserFixture(target = "js", engines = setOf("chromium", "firefox"))
        js.writeBrowserEvidence("job-a", "chromium", canonicalBrowserEvidence("js", "chromium", CHROMIUM_BUNDLE))
        val missingFirefox = assertFailsWith<IllegalStateException> { js.validate() }
        assertContains(missingFirefox.message.orEmpty(), "missing evidence artifact for BCK-001[js]/firefox")

        js.writeBrowserEvidence("job-a", "firefox", canonicalBrowserEvidence("js", "firefox", FIREFOX_BUNDLE))
        val jsIndex = js.validate()
        assertNotNull(jsIndex.browser["js"]?.get("BCK-001")?.get("chromium")?.get(CHROMIUM_BUNDLE))
        assertNotNull(jsIndex.browser["js"]?.get("BCK-001")?.get("firefox")?.get(FIREFOX_BUNDLE))

        val incompleteWasmMappings = browserFixture(
            target = "wasmJs",
            engines = setOf("chromium"),
            mapping = WEB_MAPPING.lineSequence().filterNot {
                it.contains("wasmJs\tsentinel\tweb-detached-host-retained")
            }.joinToString("\n"),
        )
        incompleteWasmMappings.writeBrowserEvidence(
            "job-a",
            "chromium",
            canonicalBrowserEvidence("wasmJs", "chromium", CHROMIUM_BUNDLE),
        )
        val missingSentinel = assertFailsWith<IllegalStateException> { incompleteWasmMappings.validate() }
        assertContains(missingSentinel.message.orEmpty(), "BCK-001[wasmJs]: missing sentinel: web-detached-host-retained")

        val wasm = browserFixture(target = "wasmJs", engines = setOf("chromium"))
        wasm.writeBrowserEvidence("job-a", "chromium", canonicalBrowserEvidence("wasmJs", "chromium", CHROMIUM_BUNDLE))
        val wasmIndex = wasm.validate()
        assertNotNull(wasmIndex.browser["wasmJs"]?.get("BCK-001")?.get("chromium")?.get(CHROMIUM_BUNDLE))
    }

    @Test
    fun o1EvidenceCanOnlyBeProducedFromItsMappedConsumerReport() {
        val fixture = jvmFixture()
        val contract = ContractRegistry.parse(O1_REGISTRY).single()
        val mappings = ContractEvidenceMapping.parse(O1_MAPPING)
        val expectedReport = """
            <testsuite name="WebTypeScriptConsumerCompileTest" tests="1" skipped="0" failures="0" errors="0" time="0.010">
              <testcase name="consumerCompiles[jvm]" classname="example.WebTypeScriptConsumerCompileTest" time="0.010"/>
            </testsuite>
        """.trimIndent()
        val expectedReportDirectory = createTempDirectory("kadre-expected-consumer-").also {
            it.resolve("TEST-consumer.xml").writeText(expectedReport)
        }
        val expectedEvidence = ContractEvidence.create(
            contract = contract,
            mappings = mappings,
            junit = JUnitEvidence.read(expectedReportDirectory),
            commit = COMMIT,
            target = "jvm",
            adapter = "web-consumer-jvm",
            os = "Linux",
            runtime = "OpenJDK Runtime Environment",
            toolchain = "25",
        )
        fixture.writeJvmEvidence(expectedEvidence.toString())
        fixture.validate()

        val wrongReport = """
            <testsuite name="WrongConsumer" tests="1" skipped="0" failures="0" errors="0" time="0.010">
              <testcase name="wrongConsumer[jvm]" classname="example.WrongConsumerTest" time="0.010"/>
            </testsuite>
        """.trimIndent()
        val reportDirectory = createTempDirectory("kadre-wrong-consumer-").also {
            it.resolve("TEST-wrong.xml").writeText(wrongReport)
        }

        val exception = assertFailsWith<IllegalStateException> {
            ContractEvidence.create(
                contract = contract,
                mappings = mappings,
                junit = JUnitEvidence.read(reportDirectory),
                commit = COMMIT,
                target = "jvm",
                adapter = "web-consumer-jvm",
                os = "Linux",
                runtime = "OpenJDK Runtime Environment",
                toolchain = "25",
            )
        }

        assertContains(
            exception.message.orEmpty(),
            "mapped testcase is missing: example.WebTypeScriptConsumerCompileTest#consumerCompiles[jvm]",
        )
    }

    private fun jvmFixture(status: String = "active"): Fixture = fixture(
        registry = O1_REGISTRY.replace("\tactive\t", "\t$status\t"),
        mapping = O1_MAPPING,
        target = "jvm",
        expectedExecutions = ExpectedContractExecutions.JUnit,
        gateContractIds = setOf("INT-002"),
    )

    private fun browserFixture(
        target: String,
        engines: Set<String>,
        mapping: String = WEB_MAPPING,
    ): Fixture = fixture(
        registry = WEB_REGISTRY,
        mapping = mapping,
        target = target,
        expectedExecutions = ExpectedContractExecutions.Browser(engines),
        gateContractIds = setOf("BCK-001"),
    )

    private fun fixture(
        registry: String,
        mapping: String,
        target: String,
        expectedExecutions: ExpectedContractExecutions,
        gateContractIds: Set<String>,
    ): Fixture {
        val root = createTempDirectory("kadre-validate-evidence-")
        val registryPath = root.resolve("contracts.tsv").also { it.writeText(registry) }
        val mappingPath = root.resolve("evidence.tsv").also { it.writeText(mapping) }
        return Fixture(root, registryPath, mappingPath, target, expectedExecutions, gateContractIds)
    }

    private data class Fixture(
        val root: Path,
        val registry: Path,
        val mapping: Path,
        val target: String,
        val expectedExecutions: ExpectedContractExecutions,
        val gateContractIds: Set<String>,
        val artifactDirectories: MutableList<Path> = mutableListOf(),
    ) {
        fun writeJvmEvidence(json: String) {
            val job = root.resolve("job-${artifactDirectories.size}").also(artifactDirectories::add)
            job.resolve("contract-evidence/INT-002.json").also {
                it.parent.createDirectories()
                it.writeText(json)
            }
        }

        fun writeBrowserEvidence(jobName: String, engine: String, json: String) {
            val job = root.resolve(jobName)
            if (job !in artifactDirectories) artifactDirectories.add(job)
            job.resolve("contract-evidence/browser/$engine/BCK-001.json").also {
                it.parent.createDirectories()
                it.writeText(json)
            }
        }

        fun validate(expectedCommit: String = COMMIT): ContractEvidenceIndex = validateContractEvidence(
            registryPath = registry,
            mappingPaths = listOf(mapping),
            expectedCommit = expectedCommit,
            target = target,
            expectedExecutions = expectedExecutions,
            gateContractIds = gateContractIds,
            artifactDirectories = artifactDirectories.ifEmpty { listOf(root.resolve("absent-job")) },
        )
    }

    private companion object {
        const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
        const val OTHER_COMMIT = "fedcba9876543210fedcba9876543210fedcba98"
        const val CHROMIUM_BUNDLE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val FIREFOX_BUNDLE = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val REGISTRY_HEADER =
            "contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef"
        const val MAPPING_HEADER = "contractId\ttarget\tkind\tevidenceId\ttestClass\ttestName"
        const val O1_REGISTRY =
            "$REGISTRY_HEADER\n" +
                "INT-002\tactive\tINTEROP-EXPORTS.md#7\tTypeScript consumer\tbroken generated declarations\tO1\tweb-typescript-consumer,web-host-common-consumer\tjvm\t-\tweb-consumer-surface-mismatch\t-"
        const val O1_MAPPING =
            "$MAPPING_HEADER\n" +
                "INT-002\tjvm\tscenario\tweb-typescript-consumer\texample.WebTypeScriptConsumerCompileTest\tconsumerCompiles[jvm]\n" +
                "INT-002\tjvm\tscenario\tweb-host-common-consumer\texample.WebTypeScriptConsumerCompileTest\tconsumerCompiles[jvm]\n" +
                "INT-002\tjvm\tsentinel\tweb-consumer-surface-mismatch\texample.WebTypeScriptConsumerCompileTest\tconsumerCompiles[jvm]"
        const val WEB_REGISTRY =
            "$REGISTRY_HEADER\n" +
                "BCK-001\tactive\tDESIGN.md#15.3\tWeb host attachment\timplicit DOM ownership\tO3\tweb-attach-connected,web-document-boundary\tjs,wasmJs\t-\tweb-detached-host-retained\t-"
        const val WEB_MAPPING =
            "$MAPPING_HEADER\n" +
                "BCK-001\tjs\tscenario\tweb-attach-connected\texample.WebContractTest\tattach[js]\n" +
                "BCK-001\tjs\tscenario\tweb-document-boundary\texample.WebContractTest\tdocument[js]\n" +
                "BCK-001\tjs\tsentinel\tweb-detached-host-retained\texample.WebContractTest\tdetached[js]\n" +
                "BCK-001\twasmJs\tscenario\tweb-attach-connected\texample.WebContractTest\tattach[wasmJs]\n" +
                "BCK-001\twasmJs\tscenario\tweb-document-boundary\texample.WebContractTest\tdocument[wasmJs]\n" +
                "BCK-001\twasmJs\tsentinel\tweb-detached-host-retained\texample.WebContractTest\tdetached[wasmJs]"

        fun canonicalJvmEvidence(): String = """
            {
              "schemaVersion": 1,
              "commit": "$COMMIT",
              "target": "jvm",
              "execution": { "kind": "junit" },
              "adapter": "web-consumer-jvm",
              "environment": { "os": "Linux", "runtime": "OpenJDK Runtime Environment", "toolchain": "25" },
              "durationMillis": 10,
              "capabilities": { "initial": [], "transitions": [] },
              "scenarios": [
                ${scenarioJson("web-typescript-consumer", "O1")},
                ${scenarioJson("web-host-common-consumer", "O1")}
              ],
              "sentinels": [${sentinelJson("web-consumer-surface-mismatch")}],
              "tests": { "tests": 1, "skipped": 0, "failures": 0, "errors": 0 }
            }
        """.trimIndent()

        fun canonicalBrowserEvidence(target: String, engine: String, bundleSha256: String): String = """
            {
              "schemaVersion": 1,
              "commit": "$COMMIT",
              "target": "$target",
              "execution": { "kind": "browser", "engine": "$engine", "version": "140.0", "bundleName": "kadre-$target.js", "bundleSha256": "$bundleSha256" },
              "adapter": "web-$target",
              "environment": { "os": "Linux", "runtime": "Playwright", "toolchain": "Kotlin 2.2" },
              "durationMillis": 20,
              "capabilities": { "initial": [], "transitions": [] },
              "scenarios": [
                ${scenarioJson("web-attach-connected", "O3", "BCK-001")},
                ${scenarioJson("web-document-boundary", "O3", "BCK-001")}
              ],
              "sentinels": [${sentinelJson("web-detached-host-retained", "BCK-001")}],
              "tests": { "tests": 3, "skipped": 0, "failures": 0, "errors": 0 }
            }
        """.trimIndent()

        fun scenarioJson(id: String, oracle: String, contractId: String = "INT-002"): String =
            "{ \"contractId\": \"$contractId\", \"scenarioId\": \"$id\", \"result\": \"Passed\", \"oracle\": \"$oracle\" }"

        fun sentinelJson(id: String, contractId: String = "INT-002"): String =
            "{ \"contractId\": \"$contractId\", \"sentinelId\": \"$id\", \"result\": \"Killed\" }"
    }
}
