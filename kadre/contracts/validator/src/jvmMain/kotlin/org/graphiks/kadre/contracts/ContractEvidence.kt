package org.graphiks.kadre.contracts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal object ContractEvidence {
    private val jsonFormat = Json {
        prettyPrint = true
    }

    fun create(
        contract: ContractRecord,
        mappings: List<EvidenceMapping>,
        junit: JUnitSummary,
        commit: String,
        target: String,
        adapter: String,
        os: String,
        runtime: String,
        toolchain: String,
    ): JsonObject {
        check(contract.status == ContractStatus.Active) { "${contract.contractId} is not active" }
        check(target in contract.requiredTargets) { "${contract.contractId}[$target]: target is not required" }
        check(commit.matches(GitSha)) { "commit must be a Git SHA" }
        require(adapter.isNotBlank()) { "adapter must not be blank" }
        check(os.isNotBlank()) { "os must not be blank" }
        check(runtime.isNotBlank()) { "runtime must not be blank" }
        check(toolchain.isNotBlank()) { "toolchain must not be blank" }

        val relevantMappings = mappings.filter {
            it.contractId == contract.contractId && it.target == target
        }
        val mappingErrors = validateTargetMappings(contract, target, relevantMappings)
        check(mappingErrors.isEmpty()) { mappingErrors.joinToString(separator = "\n") }
        check(junit.tests > 0) { "JUnit evidence contains no tests" }
        check(junit.skipped == 0) { "skipped=${junit.skipped}" }
        check(junit.failures == 0) { "failures=${junit.failures}" }
        check(junit.errors == 0) { "errors=${junit.errors}" }

        relevantMappings.forEach { mapping ->
            val identity = mapping.testClass to mapping.testName
            val testCase = junit.cases[identity]
                ?: error("mapped testcase is missing: ${mapping.testClass}#${mapping.testName}")
            check(testCase.status == JUnitStatus.Passed) {
                "mapped testcase did not pass: ${mapping.testClass}#${mapping.testName} (${testCase.status})"
            }
        }

        return buildJsonObject {
            put("schemaVersion", 1)
            put("commit", commit)
            put("target", target)
            put("execution", "junit")
            put("adapter", adapter)
            put("environment", buildJsonObject {
                put("os", os)
                put("runtime", runtime)
                put("toolchain", toolchain)
            })
            put("durationMillis", junit.durationMillis)
            put("capabilities", buildJsonObject {
                put("initial", buildJsonArray { })
                put("transitions", buildJsonArray { })
            })
            put("scenarios", buildJsonArray {
                relevantMappings.asSequence()
                    .filter { it.kind == EvidenceKind.Scenario }
                    .sortedBy(EvidenceMapping::evidenceId)
                    .forEach { mapping ->
                        add(buildJsonObject {
                            put("contractId", contract.contractId)
                            put("scenarioId", mapping.evidenceId)
                            put("result", "Passed")
                            put("oracle", contract.oracle.name)
                        })
                    }
            })
            put("sentinels", buildJsonArray {
                relevantMappings.asSequence()
                    .filter { it.kind == EvidenceKind.Sentinel }
                    .sortedBy(EvidenceMapping::evidenceId)
                    .forEach { mapping ->
                        add(buildJsonObject {
                            put("contractId", contract.contractId)
                            put("sentinelId", mapping.evidenceId)
                            put("result", "Killed")
                        })
                    }
            })
            put("tests", buildJsonObject {
                put("tests", junit.tests)
                put("skipped", junit.skipped)
                put("failures", junit.failures)
                put("errors", junit.errors)
            })
        }
    }

    private val GitSha = Regex("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")

    fun writeAtomically(path: Path, evidence: JsonObject) {
        val parent = requireNotNull(path.toAbsolutePath().parent) { "evidence output requires a parent directory" }
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".${path.fileName}.", ".tmp")
        try {
            Files.writeString(
                temporary,
                jsonFormat.encodeToString(JsonObject.serializer(), evidence) + "\n",
            )
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (cause: Throwable) {
            Files.deleteIfExists(temporary)
            throw cause
        }
    }
}
