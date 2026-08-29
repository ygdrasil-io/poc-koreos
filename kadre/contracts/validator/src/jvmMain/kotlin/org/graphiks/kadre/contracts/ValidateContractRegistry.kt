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
        .filter { it.status == ContractStatus.Active && it.requiresEvidenceGate() }
        .sortedBy(ContractRecord::contractId)
        .forEach { contract ->
            when {
                contract.contractId !in requiredEvidenceGateIds ->
                    errors += if (mappings.any { it.contractId == contract.contractId }) {
                        "${contract.contractId}: active mapping is outside configured evidence gates"
                    } else {
                        "${contract.contractId}: active contract has no configured evidence gate"
                    }
                else -> errors += validateMappings(
                    contract,
                    mappings.filter { it.contractId == contract.contractId },
                )
            }
        }

    requiredEvidenceGateIds.sorted()
        .filter { it !in recordsById }
        .forEach { errors += "configured evidence gate references unknown contract: $it" }

    requiredEvidenceGateIds.sorted()
        .mapNotNull(recordsById::get)
        .filter { it.status != ContractStatus.Active }
        .forEach { errors += "${it.contractId}: configured evidence gate requires an active contract" }

    return errors
}

private fun ContractRecord.requiresEvidenceGate(): Boolean =
    contractId.startsWith("APK-") ||
        contractId.startsWith("INP-") ||
        contractId.startsWith("WIN-")

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
