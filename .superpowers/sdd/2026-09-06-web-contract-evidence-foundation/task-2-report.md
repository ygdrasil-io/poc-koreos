# Task 2 — Target-specific contract evidence

## Changes

- Added an explicit `target` parameter to `ContractEvidence.create` and both `generateContractEvidence` overloads.
- Validated that the target is required, validated only that target's mappings with `validateTargetMappings`, and required only its mapped JUnit cases.
- Emitted the supplied target and `execution: "junit"`; no browser metadata is emitted.
- Accepted active O1, O2, and O3 contracts when their mapped JUnit evidence passes.
- Rejected commit values unless they are 40- or 64-character hexadecimal Git SHAs.
- Changed the CLI boundary to eight arguments: registry, mapping, JUnit directories, output, commit, contractId, target, adapter.
- Kept validation before the existing atomic write, so invalid evidence does not replace an existing output file.

## RED / GREEN

- RED observed with the target-aware tests: Kotlin compilation failed because `target` was absent from `ContractEvidence.create` and `generateContractEvidence`.
- GREEN:
  - `rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ContractEvidenceTest --console=plain`
  - `rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.GenerateContractEvidenceTest --console=plain`
  - `rtk ./gradlew :kadre:contracts:validator:jvmTest --console=plain`

All three commands completed successfully.

## Files

- `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ContractEvidence.kt`
- `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/GenerateContractEvidence.kt`
- `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ContractEvidenceTest.kt`
- `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/GenerateContractEvidenceTest.kt`

## Self-review

- Confirmed no production Web/JS/Wasm/API module was introduced.
- Confirmed the evidence JSON contains `execution: "junit"` and no `browser` field.
- Confirmed mappings and required JUnit cases are filtered to the selected target.
- Confirmed invalid execution preserves the prior output file.
- Ran `git diff --check`; no whitespace errors.

## Concerns

- The existing Gradle `JavaExec` callers still use the pre-existing seven-argument shape and default `local` commit. The brief assigns their repository-wide update to task 4, so this task intentionally leaves `build.gradle.kts` untouched.

## Review fix

- Restored the evidence-oracle gate after review: O1, O2 and O3 remain eligible for JUnit-backed evidence, while O4 is rejected with an actionable message that differential evidence is required.
- RED: `activeO4ContractRequiresDifferentialEvidence` initially failed because O4 emitted evidence.
- GREEN: the targeted evidence tests, generator tests and the complete validator JVM suite now pass.
- Deferred by controller decision: no direct CLI seven-argument test was added in this fix round.
