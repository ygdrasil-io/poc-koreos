package org.graphiks.kadre.contracts

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal fun validateContractRegistry(path: Path): List<String> {
    require(Files.isRegularFile(path)) { "contract registry does not exist: $path" }
    return ContractRegistry.validate(ContractRegistry.parse(path.readText()))
}

public fun main(args: Array<String>) {
    require(args.size == 1) { "expected the contract registry path" }
    val errors = validateContractRegistry(Path.of(args.single()))
    check(errors.isEmpty()) { errors.joinToString(separator = "\n") }
}
