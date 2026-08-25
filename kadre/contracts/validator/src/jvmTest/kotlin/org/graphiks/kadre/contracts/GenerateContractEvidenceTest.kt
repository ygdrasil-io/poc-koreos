package org.graphiks.kadre.contracts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GenerateContractEvidenceTest {
    @Test
    fun cliBoundaryWritesACompleteCanonicalEvidenceFile() {
        val fixture = createFixture(VALID_REPORT)

        generateContractEvidence(
            registryPath = fixture.registry,
            mappingPath = fixture.mapping,
            junitDirectory = fixture.reports,
            outputPath = fixture.output,
            commit = "0123456789abcdef",
            contractId = "APK-001",
        )

        val json = Json.parseToJsonElement(fixture.output.readText()).jsonObject
        assertEquals("1", json["schemaVersion"]!!.jsonPrimitive.content)
        assertEquals("0123456789abcdef", json["commit"]!!.jsonPrimitive.content)
        assertEquals("jvm", json["target"]!!.jsonPrimitive.content)
        assertEquals("appkit-jvm", json["adapter"]!!.jsonPrimitive.content)
        assertEquals(4, json["scenarios"]!!.jsonArray.size)
        assertEquals(2, json["sentinels"]!!.jsonArray.size)
        assertEquals("0", json["tests"]!!.jsonObject["skipped"]!!.jsonPrimitive.content)
        assertEquals("0", json["tests"]!!.jsonObject["failures"]!!.jsonPrimitive.content)
        assertEquals("0", json["tests"]!!.jsonObject["errors"]!!.jsonPrimitive.content)
    }

    @Test
    fun invalidExecutionDoesNotReplaceAnExistingEvidenceFile() {
        val invalidReport = VALID_REPORT
            .replace("skipped=\"0\"", "skipped=\"1\"")
            .replace(
                "<testcase name=\"offMain[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"/>",
                "<testcase name=\"offMain[jvm]\" classname=\"example.AppKitTest\" time=\"0.010\"><skipped/></testcase>",
            )
        val fixture = createFixture(invalidReport)
        fixture.output.parent.createDirectories()
        fixture.output.writeText("previous valid evidence\n")

        assertFailsWith<IllegalStateException> {
            generateContractEvidence(
                registryPath = fixture.registry,
                mappingPath = fixture.mapping,
                junitDirectory = fixture.reports,
                outputPath = fixture.output,
                commit = "fedcba9876543210",
                contractId = "APK-001",
            )
        }

        assertEquals("previous valid evidence\n", fixture.output.readText())
    }

    @Test
    fun selectedContractIgnoresMappingsForOtherKnownContracts() {
        val fixture = createFixture(VALID_REPORT)
        fixture.registry.writeText(
            "$REGISTRY\n" +
                "APK-002\tactive\tAPPKIT-JVM-FIRST-IMPLEMENTATION.md#6.2\tembedded AppKit host\trouting\tO3\tembedded\tjvm\t-\tembedded-refusal\t-",
        )
        fixture.mapping.writeText(
            "$MAPPING\n" +
                "APK-002\tscenario\tembedded\texample.AppKitTest\tembedded[jvm]\n" +
                "APK-002\tsentinel\tembedded-refusal\texample.AppKitTest\tembeddedRefusal[jvm]",
        )
        fixture.reports.resolve("TEST-embedded.xml").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="EmbeddedAppKitTest[jvm]" tests="2" skipped="0" failures="0" errors="0" time="0.020">
              <testcase name="embedded[jvm]" classname="example.AppKitTest" time="0.010"/>
              <testcase name="embeddedRefusal[jvm]" classname="example.AppKitTest" time="0.010"/>
            </testsuite>
            """.trimIndent(),
        )

        generateContractEvidence(
            registryPath = fixture.registry,
            mappingPath = fixture.mapping,
            junitDirectory = fixture.reports,
            outputPath = fixture.output,
            commit = "0123456789abcdef",
            contractId = "APK-001",
        )

        val json = Json.parseToJsonElement(fixture.output.readText()).jsonObject
        assertEquals(4, json["scenarios"]!!.jsonArray.size)
        assertEquals(2, json["sentinels"]!!.jsonArray.size)
    }

    private fun createFixture(report: String): Fixture {
        val root = createTempDirectory("kadre-evidence-cli-")
        val registry = root.resolve("contracts.tsv")
        val mapping = root.resolve("evidence.tsv")
        val reports = root.resolve("reports").also { it.createDirectories() }
        registry.writeText(REGISTRY)
        mapping.writeText(MAPPING)
        reports.resolve("TEST-appkit.xml").writeText(report)
        return Fixture(
            registry = registry,
            mapping = mapping,
            reports = reports,
            output = root.resolve("output/contract-evidence.json"),
        )
    }

    private data class Fixture(
        val registry: java.nio.file.Path,
        val mapping: java.nio.file.Path,
        val reports: java.nio.file.Path,
        val output: java.nio.file.Path,
    )

    private companion object {
        const val REGISTRY =
            "contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef\n" +
                "APK-001\tactive\tAPPKIT-JVM-FIRST-IMPLEMENTATION.md#6.1\tstandalone AppKit host\thanging native loop\tO3\tappkit-provider-discovery,appkit-standalone-stop,appkit-standalone-failure,appkit-standalone-reuse\tjvm\t-\tappkit-off-main-accepted,appkit-loop-not-woken\t-"
        const val MAPPING =
            "contractId\tkind\tevidenceId\ttestClass\ttestName\n" +
                "APK-001\tscenario\tappkit-provider-discovery\texample.AppKitTest\tdiscovery[jvm]\n" +
                "APK-001\tscenario\tappkit-standalone-stop\texample.AppKitTest\trealStop[jvm]\n" +
                "APK-001\tscenario\tappkit-standalone-failure\texample.AppKitTest\tnativeFailure[jvm]\n" +
                "APK-001\tscenario\tappkit-standalone-reuse\texample.AppKitTest\trealStop[jvm]\n" +
                "APK-001\tsentinel\tappkit-off-main-accepted\texample.AppKitTest\toffMain[jvm]\n" +
                "APK-001\tsentinel\tappkit-loop-not-woken\texample.AppKitTest\trealStop[jvm]"
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
