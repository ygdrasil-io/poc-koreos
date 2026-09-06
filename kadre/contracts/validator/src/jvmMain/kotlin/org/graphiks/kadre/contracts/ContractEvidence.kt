package org.graphiks.kadre.contracts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal sealed interface ContractEvidenceExecution {
    data object JUnit : ContractEvidenceExecution

    data class Browser(
        val engine: String,
        val version: String,
        val bundleName: String,
        val bundleSha256: String,
    ) : ContractEvidenceExecution
}

internal data class ValidatedContractEvidence(
    val commit: String,
    val target: String,
    val execution: ContractEvidenceExecution,
    val adapter: String,
    val document: JsonObject,
)

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
        check(contract.oracle in setOf(ContractOracle.O1, ContractOracle.O2, ContractOracle.O3)) {
            if (contract.oracle == ContractOracle.O4) {
                "${contract.contractId} uses O4 and requires differential evidence"
            } else {
                "${contract.contractId} must use oracle O1, O2 or O3"
            }
        }
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
            put("execution", buildJsonObject {
                put("kind", "junit")
            })
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
    private val Sha256 = Regex("[0-9a-fA-F]{64}")

    fun isGitSha(value: String): Boolean = value.matches(GitSha)

    fun readAndValidate(
        path: Path,
        contract: ContractRecord,
        mappings: List<EvidenceMapping>,
        expectedCommit: String,
        expectedTarget: String,
        expectedBrowserEngine: String?,
    ): ValidatedContractEvidence {
        val document = runCatching {
            jsonFormat.parseToJsonElement(Files.readString(path)).asObject("evidence")
        }.getOrElse { cause ->
            throw IllegalStateException("invalid evidence JSON at $path: ${cause.message}", cause)
        }
        document.requireFields(
            "schemaVersion",
            "commit",
            "target",
            "execution",
            "adapter",
            "environment",
            "durationMillis",
            "capabilities",
            "scenarios",
            "sentinels",
            "tests",
        )

        check(document.requiredInt("schemaVersion") == 1) { "schemaVersion must be 1" }
        val commit = document.requiredString("commit")
        check(isGitSha(commit)) { "commit must be a Git SHA" }
        check(commit == expectedCommit) { "commit does not match expected Git SHA: $commit" }

        val target = document.requiredString("target")
        check(target == expectedTarget) {
            "target $target does not match expected target $expectedTarget"
        }
        check(target in contract.requiredTargets) { "${contract.contractId}[$target]: target is not required" }

        val execution = document.requiredObject("execution").toExecution(expectedBrowserEngine)
        val adapter = document.requiredString("adapter")
        check(adapter.isNotBlank()) { "adapter must not be blank" }

        document.requiredObject("environment").also { environment ->
            environment.requireFields("os", "runtime", "toolchain")
            listOf("os", "runtime", "toolchain").forEach { field ->
                check(environment.requiredString(field).isNotBlank()) { "$field must not be blank" }
            }
        }
        check(document.requiredLong("durationMillis") >= 0) { "durationMillis must not be negative" }
        document.requiredObject("capabilities").also { capabilities ->
            capabilities.requireFields("initial", "transitions")
            capabilities.requiredArray("initial")
            capabilities.requiredArray("transitions")
        }

        val declaredMappings = buildList {
            document.requiredArray("scenarios").forEachIndexed { index, element ->
                val scenario = element.asObject("scenarios[$index]")
                scenario.requireFields("contractId", "scenarioId", "result", "oracle")
                val declaredContractId = scenario.requiredString("contractId")
                check(declaredContractId == contract.contractId) {
                    "scenario contractId $declaredContractId does not match ${contract.contractId}"
                }
                check(scenario.requiredString("result") == "Passed") {
                    "scenario ${scenario.requiredString("scenarioId")} did not pass"
                }
                check(scenario.requiredString("oracle") == contract.oracle.name) {
                    "scenario ${scenario.requiredString("scenarioId")} has wrong oracle"
                }
                add(
                    EvidenceMapping(
                        contractId = declaredContractId,
                        target = target,
                        kind = EvidenceKind.Scenario,
                        evidenceId = scenario.requiredString("scenarioId"),
                        testClass = "artifact",
                        testName = "artifact",
                    ),
                )
            }
            document.requiredArray("sentinels").forEachIndexed { index, element ->
                val sentinel = element.asObject("sentinels[$index]")
                sentinel.requireFields("contractId", "sentinelId", "result")
                val declaredContractId = sentinel.requiredString("contractId")
                check(declaredContractId == contract.contractId) {
                    "sentinel contractId $declaredContractId does not match ${contract.contractId}"
                }
                check(sentinel.requiredString("result") == "Killed") {
                    "sentinel ${sentinel.requiredString("sentinelId")} was not killed"
                }
                add(
                    EvidenceMapping(
                        contractId = declaredContractId,
                        target = target,
                        kind = EvidenceKind.Sentinel,
                        evidenceId = sentinel.requiredString("sentinelId"),
                        testClass = "artifact",
                        testName = "artifact",
                    ),
                )
            }
        }
        val configuredErrors = validateTargetMappings(contract, target, mappings)
        check(configuredErrors.isEmpty()) { configuredErrors.joinToString(separator = "\n") }
        val declaredErrors = validateTargetMappings(contract, target, declaredMappings)
        check(declaredErrors.isEmpty()) { declaredErrors.joinToString(separator = "\n") }

        document.requiredObject("tests").also { tests ->
            tests.requireFields("tests", "skipped", "failures", "errors")
            check(tests.requiredInt("tests") > 0) { "JUnit evidence contains no tests" }
            listOf("skipped", "failures", "errors").forEach { field ->
                val count = tests.requiredInt(field)
                check(count == 0) { "$field=$count" }
            }
        }

        return ValidatedContractEvidence(
            commit = commit,
            target = target,
            execution = execution,
            adapter = adapter,
            document = document,
        )
    }

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

    private fun JsonObject.toExecution(expectedBrowserEngine: String?): ContractEvidenceExecution {
        val kind = requiredString("kind")
        return when (kind) {
            "junit" -> {
                requireFields("kind")
                check(expectedBrowserEngine == null) { "expected browser execution for $expectedBrowserEngine" }
                ContractEvidenceExecution.JUnit
            }

            "browser" -> {
                requireFields("kind", "engine", "version", "bundleName", "bundleSha256")
                check(expectedBrowserEngine != null) { "expected junit execution" }
                val engine = requiredString("engine")
                val version = requiredString("version")
                val bundleName = requiredString("bundleName")
                val bundleSha256 = requiredString("bundleSha256")
                check(engine == expectedBrowserEngine) {
                    "browser engine $engine does not match expected engine $expectedBrowserEngine"
                }
                check(version.isNotBlank()) { "browser version must not be blank" }
                check(bundleName.isNotBlank()) { "browser bundleName must not be blank" }
                check(bundleSha256.matches(Sha256)) { "browser bundleSha256 must be a SHA-256" }
                ContractEvidenceExecution.Browser(engine, version, bundleName, bundleSha256)
            }

            else -> error("unknown execution kind: $kind")
        }
    }

    private fun JsonObject.requireFields(vararg expected: String) {
        val expectedFields = expected.toSet()
        val unknown = (keys - expectedFields).sorted()
        check(unknown.isEmpty()) { "unknown field: ${unknown.first()}" }
        val missing = (expectedFields - keys).sorted()
        check(missing.isEmpty()) { "missing required field: ${missing.first()}" }
    }

    private fun JsonObject.requiredString(field: String): String {
        val primitive = this[field] as? JsonPrimitive
        check(primitive != null && primitive.isString) { "$field must be a string" }
        return primitive.content
    }

    private fun JsonObject.requiredInt(field: String): Int {
        val primitive = this[field] as? JsonPrimitive
        check(primitive != null && !primitive.isString && primitive.intOrNull != null) { "$field must be an integer" }
        return requireNotNull(primitive.intOrNull)
    }

    private fun JsonObject.requiredLong(field: String): Long {
        val primitive = this[field] as? JsonPrimitive
        check(primitive != null && !primitive.isString && primitive.longOrNull != null) { "$field must be an integer" }
        return requireNotNull(primitive.longOrNull)
    }

    private fun JsonObject.requiredObject(field: String): JsonObject {
        val value = this[field]
        check(value is JsonObject) { "$field must be an object" }
        return value
    }

    private fun JsonObject.requiredArray(field: String): JsonArray {
        val value = this[field]
        check(value is JsonArray) { "$field must be an array" }
        return value
    }

    private fun JsonElement.asObject(label: String): JsonObject {
        check(this is JsonObject) { "$label must be an object" }
        return this
    }
}
