package org.graphiks.kadre.contracts

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContractEvidenceTest {
    @Test
    fun activeO2JvmContractCanProduceEvidence() {
        val json = ContractEvidence.create(
            contract = activeRuntimeContract(),
            mappings = completeRuntimeMappings(),
            junit = JUnitEvidence.read(writeReport(VALID_REPORT)),
            commit = "0123456789abcdef",
            adapter = "runtime-jvm",
            os = "Mac OS X",
            runtime = "OpenJDK Runtime Environment",
            toolchain = "25",
        )

        assertEquals("O2", json["scenarios"]!!.jsonArray.single().jsonObject["oracle"]!!.jsonPrimitive.content)
        assertEquals("runtime-jvm", json["adapter"]!!.jsonPrimitive.content)
    }

    @Test
    fun oracleOutsideO2AndO3CannotProduceEvidence() {
        val exception = assertFailsWith<IllegalStateException> {
            ContractEvidence.create(
                contract = activeRuntimeContract().copy(oracle = ContractOracle.O1),
                mappings = completeRuntimeMappings(),
                junit = JUnitEvidence.read(writeReport(VALID_REPORT)),
                commit = "0123456789abcdef",
                adapter = "runtime-jvm",
                os = "Mac OS X",
                runtime = "OpenJDK Runtime Environment",
                toolchain = "25",
            )
        }

        assertContains(exception.message.orEmpty(), "INP-001 must use oracle O2 or O3")
    }

    @Test
    fun evidenceIsBuiltFromPassingJUnitCases() {
        val junit = JUnitEvidence.read(writeReport(VALID_REPORT))

        val json = ContractEvidence.create(
            contract = activeAppKitContract(),
            mappings = completeMappings(),
            junit = junit,
            commit = "0123456789abcdef",
            adapter = "appkit-jvm",
            os = "Mac OS X",
            runtime = "OpenJDK Runtime Environment",
            toolchain = "25",
        )

        assertEquals("1", json["schemaVersion"]!!.jsonPrimitive.content)
        assertEquals("0123456789abcdef", json["commit"]!!.jsonPrimitive.content)
        assertEquals("jvm", json["target"]!!.jsonPrimitive.content)
        assertEquals("appkit-jvm", json["adapter"]!!.jsonPrimitive.content)
        assertEquals("155", json["durationMillis"]!!.jsonPrimitive.content)
        assertEquals("4", json["tests"]!!.jsonObject["tests"]!!.jsonPrimitive.content)
        assertEquals("0", json["tests"]!!.jsonObject["skipped"]!!.jsonPrimitive.content)
        assertEquals(
            listOf(
                "appkit-provider-discovery",
                "appkit-standalone-failure",
                "appkit-standalone-reuse",
                "appkit-standalone-stop",
            ),
            json["scenarios"]!!.jsonArray.map {
                it.jsonObject["scenarioId"]!!.jsonPrimitive.content
            },
        )
        assertEquals(
            listOf("appkit-loop-not-woken", "appkit-off-main-accepted"),
            json["sentinels"]!!.jsonArray.map {
                it.jsonObject["sentinelId"]!!.jsonPrimitive.content
            },
        )
    }

    @Test
    fun mappedTestcaseMustExist() {
        val junit = JUnitEvidence.read(
            writeReport(
                VALID_REPORT.replace(
                    "<testcase name=\"offMain[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"/>",
                    "<testcase name=\"unmapped[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"/>",
                ),
            ),
        )

        val exception = assertFailsWith<IllegalStateException> {
            createEvidence(junit)
        }

        assertContains(exception.message.orEmpty(), "mapped testcase is missing: example.AppKitTest#offMain[jvm]")
    }

    @Test
    fun incompleteEvidenceMappingsAreRejected() {
        val exception = assertFailsWith<IllegalStateException> {
            ContractEvidence.create(
                contract = activeAppKitContract(),
                mappings = completeMappings().dropLast(1),
                junit = JUnitEvidence.read(writeReport(VALID_REPORT)),
                commit = "0123456789abcdef",
                adapter = "appkit-jvm",
                os = "Mac OS X",
                runtime = "OpenJDK Runtime Environment",
                toolchain = "25",
            )
        }

        assertContains(exception.message.orEmpty(), "APK-001: missing sentinel: appkit-loop-not-woken")
    }

    @Test
    fun skippedFailedAndErroredSuitesCannotProducePassingEvidence() {
        val reports = listOf(
            VALID_REPORT
                .replace("skipped=\"0\"", "skipped=\"1\"")
                .replace(
                    "<testcase name=\"offMain[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"/>",
                    "<testcase name=\"offMain[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"><skipped/></testcase>",
                ) to "skipped=1",
            VALID_REPORT
                .replace("failures=\"0\"", "failures=\"1\"")
                .replace(
                    "<testcase name=\"offMain[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"/>",
                    "<testcase name=\"offMain[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"><failure/></testcase>",
                ) to "failures=1",
            VALID_REPORT
                .replace("errors=\"0\"", "errors=\"1\"")
                .replace(
                    "<testcase name=\"offMain[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"/>",
                    "<testcase name=\"offMain[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"><error/></testcase>",
                ) to "errors=1",
        )

        reports.forEach { (report, expected) ->
            val exception = assertFailsWith<IllegalStateException> {
                createEvidence(JUnitEvidence.read(writeReport(report)))
            }
            assertContains(exception.message.orEmpty(), expected)
        }
    }

    @Test
    fun inconsistentSuiteCountersAreRejected() {
        val exception = assertFailsWith<IllegalStateException> {
            JUnitEvidence.read(writeReport(VALID_REPORT.replace("tests=\"4\"", "tests=\"5\"")))
        }

        assertContains(exception.message.orEmpty(), "declares 5 tests but contains 4")
    }

    @Test
    fun duplicateTestcaseIdentityAcrossReportsIsRejected() {
        val directory = writeReport(VALID_REPORT)
        directory.resolve("TEST-duplicate.xml").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Duplicate" tests="1" skipped="0" failures="0" errors="0" time="0.001">
              <testcase name="discovery[jvm]" classname="example.AppKitTest" time="0.001"/>
            </testsuite>
            """.trimIndent(),
        )

        val exception = assertFailsWith<IllegalStateException> {
            JUnitEvidence.read(directory)
        }

        assertContains(exception.message.orEmpty(), "duplicate testcase: example.AppKitTest#discovery[jvm]")
    }

    private fun createEvidence(junit: JUnitSummary) = ContractEvidence.create(
        contract = activeAppKitContract(),
        mappings = completeMappings(),
        junit = junit,
        commit = "0123456789abcdef",
        adapter = "appkit-jvm",
        os = "Mac OS X",
        runtime = "OpenJDK Runtime Environment",
        toolchain = "25",
    )

    private fun completeMappings(): List<EvidenceMapping> = ContractEvidenceMapping.parse(
        "$MAPPING_HEADER\n" +
            "APK-001\tscenario\tappkit-provider-discovery\texample.AppKitTest\tdiscovery[jvm]\n" +
            "APK-001\tscenario\tappkit-standalone-stop\texample.AppKitTest\trealStop[jvm]\n" +
            "APK-001\tscenario\tappkit-standalone-failure\texample.AppKitTest\tnativeFailure[jvm]\n" +
            "APK-001\tscenario\tappkit-standalone-reuse\texample.AppKitTest\trealStop[jvm]\n" +
            "APK-001\tsentinel\tappkit-off-main-accepted\texample.AppKitTest\toffMain[jvm]\n" +
            "APK-001\tsentinel\tappkit-loop-not-woken\texample.AppKitTest\trealStop[jvm]",
    )

    private fun activeAppKitContract(): ContractRecord = ContractRecord(
        contractId = "APK-001",
        status = ContractStatus.Active,
        source = "APPKIT-JVM-FIRST-IMPLEMENTATION.md#6.1",
        subject = "standalone AppKit host",
        risk = "wrong thread ownership or hanging native loop",
        oracle = ContractOracle.O3,
        scenarios = listOf(
            "appkit-provider-discovery",
            "appkit-standalone-stop",
            "appkit-standalone-failure",
            "appkit-standalone-reuse",
        ),
        requiredTargets = listOf("jvm"),
        conditionalCapabilities = emptyList(),
        sentinels = listOf("appkit-off-main-accepted", "appkit-loop-not-woken"),
        retirementRef = null,
    )

    private fun activeRuntimeContract(): ContractRecord = ContractRecord(
        contractId = "INP-001",
        status = ContractStatus.Active,
        source = "TEST-STRATEGY.md#3",
        subject = "runtime input reducer",
        risk = "input events are not evidenced in CI",
        oracle = ContractOracle.O2,
        scenarios = listOf("runtime-input-key-pointer"),
        requiredTargets = listOf("jvm"),
        conditionalCapabilities = emptyList(),
        sentinels = listOf("runtime-input-policy-bypass"),
        retirementRef = null,
    )

    private fun completeRuntimeMappings(): List<EvidenceMapping> = listOf(
        EvidenceMapping(
            contractId = "INP-001",
            target = "jvm",
            kind = EvidenceKind.Scenario,
            evidenceId = "runtime-input-key-pointer",
            testClass = "example.AppKitTest",
            testName = "discovery[jvm]",
        ),
        EvidenceMapping(
            contractId = "INP-001",
            target = "jvm",
            kind = EvidenceKind.Sentinel,
            evidenceId = "runtime-input-policy-bypass",
            testClass = "example.AppKitTest",
            testName = "offMain[jvm]",
        ),
    )

    private fun writeReport(report: String): Path = createTempDirectory("kadre-junit-").also {
        it.resolve("TEST-appkit.xml").writeText(report)
    }

    private companion object {
        const val MAPPING_HEADER = "contractId\tkind\tevidenceId\ttestClass\ttestName"
        val VALID_REPORT =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="AppKitBackendProviderTest[jvm]" tests="4" skipped="0" failures="0" errors="0" time="0.155">
              <testcase name="discovery[jvm]" classname="example.AppKitTest" time="0.025"/>
              <testcase name="realStop[jvm]" classname="example.AppKitTest" time="0.100"/>
              <testcase name="nativeFailure[jvm]" classname="example.AppKitTest" time="0.020"/>
              <testcase name="offMain[jvm]" classname="example.AppKitTest" time="0.010"/>
            </testsuite>
            """.trimIndent()
    }
}
