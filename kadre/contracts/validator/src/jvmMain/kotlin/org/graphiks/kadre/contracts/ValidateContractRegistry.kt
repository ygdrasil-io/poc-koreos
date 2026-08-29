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

    mappings.asSequence()
        .map(EvidenceMapping::contractId)
        .distinct()
        .sorted()
        .forEach { contractId ->
            val contract = recordsById[contractId] ?: return@forEach
            if (contract.status == ContractStatus.Active && contractId !in requiredEvidenceGateIds) {
                errors += "$contractId: active mapping is outside configured evidence gates"
            }
        }

    requiredEvidenceGateIds.sorted().forEach { contractId ->
        val contract = recordsById[contractId]
        when {
            contract == null -> errors += "configured evidence gate references unknown contract: $contractId"
            contract.status != ContractStatus.Active ->
                errors += "$contractId: configured evidence gate requires an active contract"
            else -> errors += validateMappings(contract, mappings.filter { it.contractId == contractId })
        }
    }

    return errors
}

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
