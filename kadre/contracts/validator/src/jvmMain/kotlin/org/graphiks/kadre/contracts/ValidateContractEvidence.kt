package org.graphiks.kadre.contracts

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal sealed interface ExpectedContractExecutions {
    data object JUnit : ExpectedContractExecutions

    data class Browser(val engines: Set<String>) : ExpectedContractExecutions {
        init {
            require(engines.isNotEmpty()) { "expected browser engines must not be empty" }
            require(engines.none(String::isBlank)) { "expected browser engines must not be blank" }
        }
    }
}

internal data class ContractEvidenceIndex(
    val junit: Map<Pair<String, String>, ValidatedContractEvidence>,
    val browser: Map<String, Map<String, Map<String, Map<String, ValidatedContractEvidence>>>>,
)

internal fun validateContractEvidence(
    registryPath: Path,
    mappingPaths: List<Path>,
    expectedCommit: String,
    target: String,
    expectedExecutions: ExpectedContractExecutions,
    gateContractIds: Set<String>,
    artifactDirectories: List<Path>,
    junitReportRelativeDirectories: List<String>,
): ContractEvidenceIndex {
    require(Files.isRegularFile(registryPath)) { "contract registry does not exist: $registryPath" }
    require(ContractEvidence.isGitSha(expectedCommit)) { "expected commit must be a Git SHA" }
    require(target.isNotBlank()) { "target must not be blank" }
    require(mappingPaths.isNotEmpty()) { "contract evidence mappings must not be empty" }
    require(gateContractIds.isNotEmpty()) { "contract evidence gate IDs must not be empty" }
    require(artifactDirectories.isNotEmpty()) { "artifact directories must not be empty" }
    require(junitReportRelativeDirectories.isNotEmpty()) { "JUnit report relative directories must not be empty" }
    junitReportRelativeDirectories.forEach { template ->
        require(template.isNotBlank()) { "JUnit report relative directories must not be blank" }
        val samplePath = Path.of(template.replace(EnginePlaceholder, "engine"))
        require(!samplePath.isAbsolute && !samplePath.normalize().startsWith("..")) {
            "JUnit report directory must be relative to its artifact directory: $template"
        }
    }
    when (expectedExecutions) {
        ExpectedContractExecutions.JUnit -> require(junitReportRelativeDirectories.none { EnginePlaceholder in it }) {
            "JUnit execution report directories must not contain $EnginePlaceholder"
        }
        is ExpectedContractExecutions.Browser -> require(junitReportRelativeDirectories.all { EnginePlaceholder in it }) {
            "browser report directories must contain $EnginePlaceholder"
        }
    }

    val records = ContractRegistry.parse(registryPath.readText())
    val registryErrors = ContractRegistry.validate(records)
    check(registryErrors.isEmpty()) { registryErrors.joinToString(separator = "\n") }
    val recordsById = records.associateBy(ContractRecord::contractId)
    val unknownGateIds = gateContractIds - recordsById.keys
    check(unknownGateIds.isEmpty()) {
        "configured evidence gate references unknown contract: ${unknownGateIds.sorted().joinToString()}"
    }

    val mappings = mappingPaths.flatMap { path ->
        require(Files.isRegularFile(path)) { "contract evidence mapping does not exist: $path" }
        ContractEvidenceMapping.parse(path.readText())
    }
    val unknownMappingIds = mappings.asSequence()
        .map(EvidenceMapping::contractId)
        .filter { it !in recordsById }
        .distinct()
        .sorted()
        .toList()
    check(unknownMappingIds.isEmpty()) {
        "unknown contract evidence mapping: ${unknownMappingIds.joinToString()}"
    }

    val junitIndex = linkedMapOf<Pair<String, String>, ValidatedContractEvidence>()
    val browserIndex = linkedMapOf<BrowserIndexKey, ValidatedContractEvidence>()
    val distinctArtifactDirectories = artifactDirectories.map { it.toAbsolutePath().normalize() }.distinct()

    gateContractIds.asSequence()
        .map(recordsById::getValue)
        .filter { it.status == ContractStatus.Active }
        .sortedBy(ContractRecord::contractId)
        .forEach { contract ->
            check(target in contract.requiredTargets) {
                "${contract.contractId}[$target]: target is not required"
            }
            val targetMappings = mappings.filter {
                it.contractId == contract.contractId && it.target == target
            }
            val mappingErrors = validateTargetMappings(contract, target, targetMappings)
            check(mappingErrors.isEmpty()) { mappingErrors.joinToString(separator = "\n") }

            when (expectedExecutions) {
                ExpectedContractExecutions.JUnit -> {
                    val artifact = requireSingleArtifact(
                        artifactDirectories = distinctArtifactDirectories,
                        relativePath = Path.of("contract-evidence", "${contract.contractId}.json"),
                        missingMessage = "missing evidence artifact for ${contract.contractId}[$target]",
                        duplicateMessage = "duplicate evidence artifacts for ${contract.contractId}[$target]",
                    )
                    val junit = readAssociatedJunit(
                        artifact = artifact,
                        relativeDirectories = junitReportRelativeDirectories,
                        engine = null,
                    )
                    val evidence = ContractEvidence.readAndValidate(
                        path = artifact.path,
                        contract = contract,
                        mappings = targetMappings,
                        junit = junit,
                        expectedCommit = expectedCommit,
                        expectedTarget = target,
                        expectedBrowserEngine = null,
                    )
                    junitIndex[target to contract.contractId] = evidence
                }

                is ExpectedContractExecutions.Browser -> expectedExecutions.engines.sorted().forEach { engine ->
                    val artifact = requireSingleArtifact(
                        artifactDirectories = distinctArtifactDirectories,
                        relativePath = Path.of(
                            "contract-evidence",
                            "browser",
                            engine,
                            "${contract.contractId}.json",
                        ),
                        missingMessage = "missing evidence artifact for ${contract.contractId}[$target]/$engine",
                        duplicateMessage = "duplicate evidence artifacts for ${contract.contractId}[$target]/$engine",
                    )
                    val junit = readAssociatedJunit(
                        artifact = artifact,
                        relativeDirectories = junitReportRelativeDirectories,
                        engine = engine,
                    )
                    val evidence = ContractEvidence.readAndValidate(
                        path = artifact.path,
                        contract = contract,
                        mappings = targetMappings,
                        junit = junit,
                        expectedCommit = expectedCommit,
                        expectedTarget = target,
                        expectedBrowserEngine = engine,
                    )
                    val browser = evidence.execution as ContractEvidenceExecution.Browser
                    browserIndex[BrowserIndexKey(target, contract.contractId, engine, browser.bundleSha256)] = evidence
                }
            }
        }

    return ContractEvidenceIndex(
        junit = junitIndex.toMap(),
        browser = browserIndex.toNestedIndex(),
    )
}

private data class BrowserIndexKey(
    val target: String,
    val contractId: String,
    val engine: String,
    val bundleSha256: String,
)

private data class LocatedArtifact(
    val directory: Path,
    val path: Path,
)

private const val EnginePlaceholder = "{engine}"

private fun requireSingleArtifact(
    artifactDirectories: List<Path>,
    relativePath: Path,
    missingMessage: String,
    duplicateMessage: String,
): LocatedArtifact {
    val candidates = artifactDirectories.asSequence()
        .map { directory -> LocatedArtifact(directory, directory.resolve(relativePath)) }
        .filter { Files.isRegularFile(it.path) }
        .toList()
    check(candidates.isNotEmpty()) { missingMessage }
    check(candidates.size == 1) { duplicateMessage }
    return candidates.single()
}

private fun readAssociatedJunit(
    artifact: LocatedArtifact,
    relativeDirectories: List<String>,
    engine: String?,
): JUnitSummary = runCatching {
    JUnitEvidence.read(
        relativeDirectories.map { template ->
            val relativeDirectory = if (engine == null) template else template.replace(EnginePlaceholder, engine)
            artifact.directory.resolve(relativeDirectory)
        },
    )
}.getOrElse { cause ->
    throw IllegalStateException(cause.message, cause)
}

private fun Map<BrowserIndexKey, ValidatedContractEvidence>.toNestedIndex():
    Map<String, Map<String, Map<String, Map<String, ValidatedContractEvidence>>>> =
    entries.groupBy { it.key.target }.mapValues { (_, targets) ->
        targets.groupBy { it.key.contractId }.mapValues { (_, contracts) ->
            contracts.groupBy { it.key.engine }.mapValues { (_, engines) ->
                engines.associate { it.key.bundleSha256 to it.value }
            }
        }
    }

private fun parseExpectedExecutions(value: String): ExpectedContractExecutions =
    if (value == "junit") {
        ExpectedContractExecutions.JUnit
    } else {
        ExpectedContractExecutions.Browser(value.split(',').toSet())
    }

internal fun validateContractEvidenceCli(args: Array<String>): ContractEvidenceIndex {
    require(args.size == 8) {
        "expected registry, mappings, expected commit, target, executions, gate IDs, artifact directories and JUnit report directories arguments"
    }
    return validateContractEvidence(
        registryPath = Path.of(args[0]),
        mappingPaths = args[1].split(',').filter(String::isNotBlank).map(Path::of),
        expectedCommit = args[2],
        target = args[3],
        expectedExecutions = parseExpectedExecutions(args[4]),
        gateContractIds = args[5].split(',').filter(String::isNotBlank).toSet(),
        artifactDirectories = args[6].split(File.pathSeparator).filter(String::isNotBlank).map(Path::of),
        junitReportRelativeDirectories = args[7].split(File.pathSeparator).filter(String::isNotBlank),
    )
}

public fun main(args: Array<String>) {
    validateContractEvidenceCli(args)
}
