package org.graphiks.kadre.contracts

import java.nio.file.Files
import java.nio.file.Path
import java.io.File
import kotlin.io.path.readText

internal fun generateContractEvidence(
    registryPath: Path,
    mappingPath: Path,
    junitDirectory: Path,
    outputPath: Path,
    commit: String,
    contractId: String,
) = generateContractEvidence(
    registryPath = registryPath,
    mappingPath = mappingPath,
    junitDirectories = listOf(junitDirectory),
    outputPath = outputPath,
    commit = commit,
    contractId = contractId,
)

internal fun generateContractEvidence(
    registryPath: Path,
    mappingPath: Path,
    junitDirectories: List<Path>,
    outputPath: Path,
    commit: String,
    contractId: String,
) {
    require(Files.isRegularFile(registryPath)) { "contract registry does not exist: $registryPath" }
    require(Files.isRegularFile(mappingPath)) { "contract evidence mapping does not exist: $mappingPath" }
    val records = ContractRegistry.parse(registryPath.readText())
    val registryErrors = ContractRegistry.validate(records)
    check(registryErrors.isEmpty()) { registryErrors.joinToString(separator = "\n") }
    val contract = records.singleOrNull { it.contractId == contractId }
        ?: error("expected exactly one $contractId contract")
    val mappings = ContractEvidenceMapping.parse(mappingPath.readText())
    val knownContractIds = records.mapTo(linkedSetOf(), ContractRecord::contractId)
    val unknownMappingContractIds = mappings.asSequence()
        .map(EvidenceMapping::contractId)
        .filter { it !in knownContractIds }
        .distinct()
        .sorted()
        .toList()
    check(unknownMappingContractIds.isEmpty()) {
        "unknown contract evidence mapping: ${unknownMappingContractIds.joinToString()}"
    }
    val junit = JUnitEvidence.read(junitDirectories)
    val evidence = ContractEvidence.create(
        contract = contract,
        mappings = mappings.filter { it.contractId == contractId },
        junit = junit,
        commit = commit,
        os = System.getProperty("os.name").orEmpty(),
        runtime = listOf(
            System.getProperty("java.runtime.name").orEmpty(),
            System.getProperty("java.runtime.version").orEmpty(),
        ).filter(String::isNotBlank).joinToString(separator = " "),
        toolchain = System.getProperty("java.version").orEmpty(),
    )
    ContractEvidence.writeAtomically(outputPath, evidence)
}

public fun main(args: Array<String>) {
    require(args.size == 6) {
        "expected registry, mapping, JUnit directories, output, commit and contractId arguments"
    }
    generateContractEvidence(
        registryPath = Path.of(args[0]),
        mappingPath = Path.of(args[1]),
        junitDirectories = args[2].split(File.pathSeparator).map(Path::of),
        outputPath = Path.of(args[3]),
        commit = args[4],
        contractId = args[5],
    )
}
