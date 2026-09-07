package org.graphiks.kadre.contracts

internal enum class EvidenceKind {
    Scenario,
    Sentinel,
}

internal data class EvidenceMapping(
    val contractId: String,
    val target: String,
    val kind: EvidenceKind,
    val evidenceId: String,
    val testClass: String,
    val testName: String,
)

internal object ContractEvidenceMapping {
    private const val Header = "contractId\ttarget\tkind\tevidenceId\ttestClass\ttestName"

    fun parse(text: String): List<EvidenceMapping> {
        val lines = text.lineSequence()
            .map(String::trimEnd)
            .filter(String::isNotBlank)
            .filterNot { it.startsWith("#") }
            .toList()
        require(lines.firstOrNull() == Header) { "invalid contract evidence mapping header" }

        return lines.drop(1).mapIndexed { index, line ->
            val columns = line.split('\t')
            require(columns.size == 6) { "line ${index + 2}: expected 6 columns" }
            require(columns.none(String::isBlank)) { "line ${index + 2}: columns must not be blank" }
            EvidenceMapping(
                contractId = columns[0],
                target = columns[1],
                kind = columns[2].toEvidenceKind(),
                evidenceId = columns[3],
                testClass = columns[4],
                testName = columns[5],
            )
        }
    }

    private fun String.toEvidenceKind(): EvidenceKind = when (this) {
        "scenario" -> EvidenceKind.Scenario
        "sentinel" -> EvidenceKind.Sentinel
        else -> throw IllegalArgumentException("unknown evidence kind: $this")
    }
}

internal fun validateMappings(
    contract: ContractRecord,
    mappings: List<EvidenceMapping>,
): List<String> = buildList {
    if (contract.status != ContractStatus.Active) return@buildList

    mappings.asSequence()
        .map(EvidenceMapping::contractId)
        .filter { it != contract.contractId }
        .distinct()
        .sorted()
        .forEach { add("${contract.contractId}: unknown contractId: $it") }

    val relevant = mappings.filter { it.contractId == contract.contractId }
    relevant.asSequence()
        .map(EvidenceMapping::target)
        .filter { it !in contract.requiredTargets }
        .distinct()
        .sorted()
        .forEach { add("${contract.contractId}[$it]: target is not required") }

    contract.requiredTargets.forEach { target ->
        addAll(validateTargetMappings(contract, target, relevant.filter { it.target == target }))
    }
}

internal fun validateTargetMappings(
    contract: ContractRecord,
    target: String,
    mappings: List<EvidenceMapping>,
): List<String> = buildList {
    if (contract.status != ContractStatus.Active) return@buildList
    if (target !in contract.requiredTargets) {
        add("${contract.contractId}[$target]: target is not required")
        return@buildList
    }

    val relevant = mappings.filter {
        it.contractId == contract.contractId && it.target == target
    }
    EvidenceKind.entries.forEach { kind ->
        relevant.asSequence()
            .filter { it.kind == kind }
            .groupingBy(EvidenceMapping::evidenceId)
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
            .forEach { add("${contract.contractId}[$target]: duplicate ${kind.label}: $it") }
    }

    compareEvidence(
        contract = contract,
        target = target,
        kind = EvidenceKind.Scenario,
        expected = contract.scenarios,
        mappings = relevant,
        errors = this,
    )
    compareEvidence(
        contract = contract,
        target = target,
        kind = EvidenceKind.Sentinel,
        expected = contract.sentinels,
        mappings = relevant,
        errors = this,
    )
}

private fun compareEvidence(
    contract: ContractRecord,
    target: String,
    kind: EvidenceKind,
    expected: List<String>,
    mappings: List<EvidenceMapping>,
    errors: MutableList<String>,
) {
    val expectedIds = expected.toSet()
    val actualIds = mappings.asSequence()
        .filter { it.kind == kind }
        .map(EvidenceMapping::evidenceId)
        .toSet()
    (expectedIds - actualIds).sorted().forEach {
        errors += "${contract.contractId}[$target]: missing ${kind.label}: $it"
    }
    (actualIds - expectedIds).sorted().forEach {
        errors += "${contract.contractId}[$target]: unknown ${kind.label}: $it"
    }
}

private val EvidenceKind.label: String
    get() = name.lowercase()
