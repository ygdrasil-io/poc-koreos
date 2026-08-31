#!/usr/bin/env bash
set -euo pipefail

: "${KADRE_FAKE_GRADLE_TRACE:?KADRE_FAKE_GRADLE_TRACE is required}"
printf '%s\n' "$*" >> "$KADRE_FAKE_GRADLE_TRACE"

if [[ " $* " == *" :kadre:backend:appkit:appKitNativeTests "* ]]; then
    exit "${KADRE_FAKE_TEST_STATUS:-0}"
fi

if [[ " $* " == *" :kadre:contracts:validator:generateAppKitContractEvidence "* ]]; then
    evidence_directory="${KADRE_FAKE_EVIDENCE_DIRECTORY:?KADRE_FAKE_EVIDENCE_DIRECTORY is required}"
    mkdir -p "$evidence_directory"
    for contract_id in APK-001 APK-002 APK-003 APK-004 APK-005 APK-006 APK-007 APK-008 APK-009 APK-010 APK-011 APK-012; do
        if [[ "${KADRE_FAKE_MISSING_EVIDENCE:-}" != "$contract_id" ]]; then
            printf '{"schemaVersion":1}\n' > "$evidence_directory/$contract_id.json"
        fi
    done
fi

if [[ " $* " == *" :kadre:contracts:validator:generateRuntimeContractEvidence "* ]]; then
    runtime_evidence_directory="${KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY:?KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY is required}"
    mkdir -p "$runtime_evidence_directory"
    for contract_id in INP-001 WIN-001 WIN-002 WIN-003 WIN-004 WIN-005 WIN-006 INT-001; do
        if [[ "${KADRE_FAKE_MISSING_EVIDENCE:-}" != "$contract_id" ]]; then
            printf '{"schemaVersion":1}\n' > "$runtime_evidence_directory/$contract_id.json"
        fi
    done
fi
