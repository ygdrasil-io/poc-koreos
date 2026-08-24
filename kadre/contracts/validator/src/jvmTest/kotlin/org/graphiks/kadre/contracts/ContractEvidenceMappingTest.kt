package org.graphiks.kadre.contracts

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContractEvidenceMappingTest {
    @Test
    fun mappingCoversEveryDeclaredScenarioAndSentinelExactlyOnce() {
        val mappings = ContractEvidenceMapping.parse(COMPLETE_MAPPING)

        assertEquals(emptyList(), validateMappings(activeAppKitContract(), mappings))
    }

    @Test
    fun missingUnknownAndForeignEvidenceAreRejected() {
        val mappings = ContractEvidenceMapping.parse(
            "$HEADER\n" +
                "APK-001\tscenario\tappkit-provider-discovery\texample.AppKitTest\tdiscovery[jvm]\n" +
                "APK-001\tscenario\tunknown-scenario\texample.AppKitTest\tunknown[jvm]\n" +
                "OTHER-001\tsentinel\tforeign-sentinel\texample.OtherTest\tforeign[jvm]",
        )

        val errors = validateMappings(activeAppKitContract(), mappings)

        assertTrue(errors.any { "missing scenario: appkit-standalone-stop" in it })
        assertTrue(errors.any { "unknown scenario: unknown-scenario" in it })
        assertTrue(errors.any { "missing sentinel: appkit-loop-not-woken" in it })
        assertTrue(errors.any { "unknown contractId: OTHER-001" in it })
    }

    @Test
    fun duplicateEvidenceIdIsRejectedEvenWhenItTargetsAnotherTest() {
        val duplicate = COMPLETE_MAPPING +
            "\nAPK-001\tscenario\tappkit-provider-discovery\texample.OtherTest\totherDiscovery[jvm]"

        val errors = validateMappings(activeAppKitContract(), ContractEvidenceMapping.parse(duplicate))

        assertTrue(errors.any { "duplicate scenario: appkit-provider-discovery" in it })
    }

    @Test
    fun malformedKindAndColumnsAreRejectedWhileParsing() {
        val unknownKind = assertFailsWith<IllegalArgumentException> {
            ContractEvidenceMapping.parse(
                "$HEADER\nAPK-001\tproof\tappkit-provider-discovery\texample.AppKitTest\tdiscovery[jvm]",
            )
        }
        assertContains(unknownKind.message.orEmpty(), "unknown evidence kind: proof")

        val missingColumn = assertFailsWith<IllegalArgumentException> {
            ContractEvidenceMapping.parse(
                "$HEADER\nAPK-001\tscenario\tappkit-provider-discovery\texample.AppKitTest",
            )
        }
        assertContains(missingColumn.message.orEmpty(), "expected 5 columns")

        val blankColumn = assertFailsWith<IllegalArgumentException> {
            ContractEvidenceMapping.parse(
                "$HEADER\nAPK-001\tscenario\t\texample.AppKitTest\tdiscovery[jvm]",
            )
        }
        assertContains(blankColumn.message.orEmpty(), "columns must not be blank")
    }

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

    private companion object {
        const val HEADER = "contractId\tkind\tevidenceId\ttestClass\ttestName"
        const val COMPLETE_MAPPING =
            "$HEADER\n" +
                "APK-001\tscenario\tappkit-provider-discovery\texample.AppKitTest\tdiscovery[jvm]\n" +
                "APK-001\tscenario\tappkit-standalone-stop\texample.AppKitTest\trealStop[jvm]\n" +
                "APK-001\tscenario\tappkit-standalone-failure\texample.AppKitTest\tnativeFailure[jvm]\n" +
                "APK-001\tscenario\tappkit-standalone-reuse\texample.AppKitTest\trealStop[jvm]\n" +
                "APK-001\tsentinel\tappkit-off-main-accepted\texample.AppKitTest\toffMain[jvm]\n" +
                "APK-001\tsentinel\tappkit-loop-not-woken\texample.AppKitTest\trealStop[jvm]"
    }
}
