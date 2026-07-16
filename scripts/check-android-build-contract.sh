#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

legacy="$({
  rg -n 'androidTarget\s*\(' "$root" --glob '*.gradle.kts' --glob '!docs/**' || true
  rg -n 'id\("org\.jetbrains\.kotlin\.android"\)|kotlin\("android"\)' "$root" --glob '*.gradle.kts' --glob '!docs/**' || true
  rg -n '^android\.(builtInKotlin|newDsl)=false|^systemProp\..*android\.(builtInKotlin|newDsl)=false' "$root/gradle.properties" || true
} | sed '/^[[:space:]]*\/\//d')"

if [[ -n "$legacy" ]]; then
  printf '%s\n' "$legacy" >&2
  exit 1
fi
