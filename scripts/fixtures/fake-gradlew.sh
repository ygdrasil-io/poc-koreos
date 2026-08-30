#!/usr/bin/env bash
set -euo pipefail

: "${KADRE_FAKE_GRADLE_TRACE:?KADRE_FAKE_GRADLE_TRACE is required}"
printf '%s\n' "$*" >> "$KADRE_FAKE_GRADLE_TRACE"

case " $* " in
    *" :kadre:backend:appkit:appKitNativeTests "*)
        exit "${KADRE_FAKE_TEST_STATUS:-0}"
        ;;
    *" :kadre:contracts:validator:generateAppKitContractEvidence "*)
        evidence_directory="${KADRE_FAKE_EVIDENCE_DIRECTORY:?KADRE_FAKE_EVIDENCE_DIRECTORY is required}"
        mkdir -p "$evidence_directory"
        for contract_id in APK-001 APK-002 APK-003 APK-004 APK-005 APK-006 APK-007 APK-008; do
            if [[ "${KADRE_FAKE_MISSING_EVIDENCE:-}" != "$contract_id" ]]; then
                printf '{"schemaVersion":1}\n' > "$evidence_directory/$contract_id.json"
            fi
        done
        ;;
esac
