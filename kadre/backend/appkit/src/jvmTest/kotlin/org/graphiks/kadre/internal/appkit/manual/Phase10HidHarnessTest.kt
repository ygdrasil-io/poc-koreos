package org.graphiks.kadre.internal.appkit.manual

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Phase10HidHarnessTest {
    @Test
    fun explicitRecordingOptionsDoNotDependOnTheCurrentCheckout() {
        val options = Phase10HidOptions.parse(
            arrayOf("--record=build/manual/hid.tsv", "--build-id=artifact-42"),
        )

        assertEquals(Path.of("build/manual/hid.tsv"), options.recordPath)
        assertEquals("artifact-42", options.buildId)
    }

    @Test
    fun scenarioResultRequiresOneOfTheHidManualCasesAndAnOperatorNote() {
        assertEquals(
            Phase10HidScenarioResult("H2", "not-applicable", "controller exposed by GameController"),
            Phase10HidScenarioResult.parse("result H2 not-applicable controller exposed by GameController"),
        )
        assertFailsWith<IllegalArgumentException> {
            Phase10HidScenarioResult.parse("result M2 pass wrong phase")
        }
        assertFailsWith<IllegalArgumentException> {
            Phase10HidScenarioResult.parse("result H3 pass")
        }
    }
}
