#!/usr/bin/env bash
set -euo pipefail

: "${KADRE_FAKE_GRADLE_TRACE:?KADRE_FAKE_GRADLE_TRACE is required}"
printf '%s\n' "$*" >> "$KADRE_FAKE_GRADLE_TRACE"

case " $* " in
    *" :kadre:backend:appkit:jvmTest "*)
        exit "${KADRE_FAKE_TEST_STATUS:-0}"
        ;;
    *" :kadre:contracts:validator:generateAppKitContractEvidence "*)
        evidence="${KADRE_FAKE_EVIDENCE_PATH:?KADRE_FAKE_EVIDENCE_PATH is required}"
        mkdir -p "$(dirname "$evidence")"
        printf '{"schemaVersion":1}\n' > "$evidence"
        ;;
esac
