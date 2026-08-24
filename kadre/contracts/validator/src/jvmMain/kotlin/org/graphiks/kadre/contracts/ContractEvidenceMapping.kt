package org.graphiks.kadre.contracts

internal enum class EvidenceKind {
    Scenario,
    Sentinel,
}

internal data class EvidenceMapping(
    val contractId: String,
    val kind: EvidenceKind,
    val evidenceId: String,
    val testClass: String,
    val testName: String,
)

internal object ContractEvidenceMapping {
    private const val Header = "contractId\tkind\tevidenceId\ttestClass\ttestName"

    fun parse(text: String): List<EvidenceMapping> {
        val lines = text.lineSequence()
            .map(String::trimEnd)
            .filter(String::isNotBlank)
            .filterNot { it.startsWith("#") }
            .toList()
        require(lines.firstOrNull() == Header) { "invalid contract evidence mapping header" }

        return lines.drop(1).mapIndexed { index, line ->
            val columns = line.split('\t')
            require(columns.size == 5) { "line ${index + 2}: expected 5 columns" }
            require(columns.none(String::isBlank)) { "line ${index + 2}: columns must not be blank" }
            EvidenceMapping(
                contractId = columns[0],
                kind = columns[1].toEvidenceKind(),
                evidenceId = columns[2],
                testClass = columns[3],
                testName = columns[4],
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
    mappings.asSequence()
        .map(EvidenceMapping::contractId)
        .filter { it != contract.contractId }
        .distinct()
        .sorted()
        .forEach { add("${contract.contractId}: unknown contractId: $it") }

    val relevant = mappings.filter { it.contractId == contract.contractId }
    EvidenceKind.entries.forEach { kind ->
        relevant.asSequence()
            .filter { it.kind == kind }
            .groupingBy(EvidenceMapping::evidenceId)
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
            .forEach { add("${contract.contractId}: duplicate ${kind.label}: $it") }
    }

    compareEvidence(
        contract = contract,
        kind = EvidenceKind.Scenario,
        expected = contract.scenarios,
        mappings = relevant,
        errors = this,
    )
    compareEvidence(
        contract = contract,
        kind = EvidenceKind.Sentinel,
        expected = contract.sentinels,
        mappings = relevant,
        errors = this,
    )
}

private fun compareEvidence(
    contract: ContractRecord,
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
        errors += "${contract.contractId}: missing ${kind.label}: $it"
    }
    (actualIds - expectedIds).sorted().forEach {
        errors += "${contract.contractId}: unknown ${kind.label}: $it"
    }
}

private val EvidenceKind.label: String
    get() = name.lowercase()
