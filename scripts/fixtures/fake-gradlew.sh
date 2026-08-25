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
        printf '{"schemaVersion":1}\n' > "$evidence_directory/APK-001.json"
        printf '{"schemaVersion":1}\n' > "$evidence_directory/APK-002.json"
        ;;
esac
