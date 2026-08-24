package org.graphiks.kadre.contracts

internal enum class ContractStatus {
    Planned,
    Active,
    Retired;

    companion object {
        fun parse(value: String): ContractStatus = when (value) {
            "planned" -> Planned
            "active" -> Active
            "retired" -> Retired
            else -> error("unknown contract status: $value")
        }
    }
}

internal enum class ContractOracle { O1, O2, O3, O4 }

internal data class ContractRecord(
    val contractId: String,
    val status: ContractStatus,
    val source: String,
    val subject: String,
    val risk: String,
    val oracle: ContractOracle,
    val scenarios: List<String>,
    val requiredTargets: List<String>,
    val conditionalCapabilities: List<String>,
    val sentinels: List<String>,
    val retirementRef: String?,
)

internal object ContractRegistry {
    private const val Header =
        "contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef"
    private val ContractIdPattern = Regex("[A-Z]{3}-[0-9]{3}")

    fun parse(text: String): List<ContractRecord> {
        val lines = text.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") }
            .toList()
        require(lines.firstOrNull() == Header) { "invalid contract registry header" }
        return lines.drop(1).mapIndexed { index, line ->
            val columns = line.split('\t')
            require(columns.size == 11) { "line ${index + 2}: expected 11 columns" }
            ContractRecord(
                contractId = columns[0],
                status = ContractStatus.parse(columns[1]),
                source = columns[2],
                subject = columns[3],
                risk = columns[4],
                oracle = ContractOracle.valueOf(columns[5]),
                scenarios = columns[6].asRegistryList(),
                requiredTargets = columns[7].asRegistryList(),
                conditionalCapabilities = columns[8].asRegistryList(),
                sentinels = columns[9].asRegistryList(),
                retirementRef = columns[10].takeUnless { it == "-" },
            )
        }
    }

    fun validate(records: List<ContractRecord>): List<String> = buildList {
        records.groupingBy(ContractRecord::contractId)
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
            .forEach { contractId -> add("duplicate contractId: $contractId") }

        records.forEach { record ->
            val id = record.contractId
            if (!ContractIdPattern.matches(id)) add("$id: invalid contractId")
            if (record.source.isBlank()) add("$id: source is required")
            if (record.subject.isBlank()) add("$id: subject is required")
            if (record.risk.isBlank()) add("$id: risk is required")
            listOf(
                "scenarios" to record.scenarios,
                "requiredTargets" to record.requiredTargets,
                "conditionalCapabilities" to record.conditionalCapabilities,
                "sentinels" to record.sentinels,
            ).forEach { (field, values) ->
                if (values.any(String::isBlank)) add("$id: $field must not contain blank values")
            }

            when (record.status) {
                ContractStatus.Planned -> {
                    if (record.retirementRef != null) add("$id: planned contract must not define retirementRef")
                }

                ContractStatus.Active -> {
                    if (record.scenarios.isEmpty()) add("$id: active contract requires scenarios")
                    if (record.requiredTargets.isEmpty()) add("$id: active contract requires requiredTargets")
                    if (record.sentinels.isEmpty()) add("$id: active contract requires sentinels")
                    if (record.retirementRef != null) add("$id: active contract must not define retirementRef")
                }

                ContractStatus.Retired -> {
                    if (record.retirementRef.isNullOrBlank()) add("$id: retired contract requires retirementRef")
                    if (record.scenarios.isNotEmpty()) add("$id: retired contract must not define scenarios")
                    if (record.requiredTargets.isNotEmpty()) add("$id: retired contract must not define requiredTargets")
                    if (record.sentinels.isNotEmpty()) add("$id: retired contract must not define sentinels")
                }
            }
        }
    }

    private fun String.asRegistryList(): List<String> =
        if (this == "-") emptyList() else split(',').map(String::trim)
}
