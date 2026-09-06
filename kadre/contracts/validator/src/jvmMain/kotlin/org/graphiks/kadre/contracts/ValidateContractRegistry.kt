package org.graphiks.kadre.contracts

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal fun validateContractRegistry(path: Path): List<String> {
    require(Files.isRegularFile(path)) { "contract registry does not exist: $path" }
    return ContractRegistry.validate(ContractRegistry.parse(path.readText()))
}

internal fun validateContractRegistry(
    registryPath: Path,
    mappingPaths: List<Path>,
    requiredEvidenceGateIds: Set<String>,
): List<String> {
    val records = ContractRegistry.parse(registryPath.readText())
    val errors = ContractRegistry.validate(records).toMutableList()
    val mappings = mappingPaths.flatMap { path ->
        require(Files.isRegularFile(path)) { "contract evidence mapping does not exist: $path" }
        ContractEvidenceMapping.parse(path.readText())
    }
    val recordsById = records.associateBy(ContractRecord::contractId)

    mappings.asSequence()
        .map(EvidenceMapping::contractId)
        .filter { it !in recordsById }
        .distinct()
        .sorted()
        .forEach { errors += "unknown contract evidence mapping: $it" }

    records.asSequence()
        .filter { it.status == ContractStatus.Active }
        .sortedBy(ContractRecord::contractId)
        .forEach { contract ->
            val contractMappings = mappings.filter { it.contractId == contract.contractId }
            if (contract.contractId in requiredEvidenceGateIds) {
                errors += validateMappings(
                    contract,
                    contractMappings,
                )
            } else if (contractMappings.isNotEmpty()) {
                errors += "${contract.contractId}: active mapping is outside configured evidence gates"
            }
        }

    requiredEvidenceGateIds.sorted()
        .filter { it !in recordsById }
        .forEach { errors += "configured evidence gate references unknown contract: $it" }

    requiredEvidenceGateIds.sorted()
        .mapNotNull(recordsById::get)
        .filter { it.status == ContractStatus.Retired }
        .forEach { errors += "${it.contractId}: configured evidence gate references a retired contract" }

    records.asSequence()
        .filter { it.status != ContractStatus.Retired }
        .filter(ContractRecord::requiresBrowserEvidenceGate)
        .filter { it.contractId !in requiredEvidenceGateIds }
        .sortedBy(ContractRecord::contractId)
        .forEach { errors += "${it.contractId}: browser contract is outside configured evidence gates" }

    return errors
}

private fun ContractRecord.requiresBrowserEvidenceGate(): Boolean =
    requiredTargets.any { it == "js" || it == "wasmJs" }

public fun main(args: Array<String>) {
    require(args.size == 1 || args.size == 3) {
        "expected the contract registry path, optionally followed by evidence mappings and configured gate IDs"
    }
    val errors = if (args.size == 1) {
        validateContractRegistry(Path.of(args.single()))
    } else {
        validateContractRegistry(
            registryPath = Path.of(args[0]),
            mappingPaths = args[1].split(',').filter(String::isNotBlank).map(Path::of),
            requiredEvidenceGateIds = args[2].split(',').filter(String::isNotBlank).toSet(),
        )
    }
    check(errors.isEmpty()) { errors.joinToString(separator = "\n") }
}
