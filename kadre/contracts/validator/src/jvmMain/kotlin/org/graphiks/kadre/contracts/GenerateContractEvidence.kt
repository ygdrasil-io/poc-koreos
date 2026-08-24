package org.graphiks.kadre.contracts

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal fun generateContractEvidence(
    registryPath: Path,
    mappingPath: Path,
    junitDirectory: Path,
    outputPath: Path,
    commit: String,
) {
    require(Files.isRegularFile(registryPath)) { "contract registry does not exist: $registryPath" }
    require(Files.isRegularFile(mappingPath)) { "contract evidence mapping does not exist: $mappingPath" }
    val records = ContractRegistry.parse(registryPath.readText())
    val registryErrors = ContractRegistry.validate(records)
    check(registryErrors.isEmpty()) { registryErrors.joinToString(separator = "\n") }
    val contract = records.singleOrNull { it.contractId == APPKIT_CONTRACT_ID }
        ?: error("expected exactly one $APPKIT_CONTRACT_ID contract")
    val mappings = ContractEvidenceMapping.parse(mappingPath.readText())
    val junit = JUnitEvidence.read(junitDirectory)
    val evidence = ContractEvidence.create(
        contract = contract,
        mappings = mappings,
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
    require(args.size == 5) {
        "expected registry, mapping, JUnit directory, output and commit arguments"
    }
    generateContractEvidence(
        registryPath = Path.of(args[0]),
        mappingPath = Path.of(args[1]),
        junitDirectory = Path.of(args[2]),
        outputPath = Path.of(args[3]),
        commit = args[4],
    )
}

private const val APPKIT_CONTRACT_ID = "APK-001"
