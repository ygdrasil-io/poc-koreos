#!/usr/bin/env bash
# UIKit contract gate (phase 0). Usage: test-uikit-simulator.sh [ios|tvos|all]
# Sélectionne explicitement la famille et sa destination, vérifie la toolchain,
# lance les tests, valide les preuves et retourne un code d'échec fidèle.
set -euo pipefail

FAMILY="${1:?usage: test-uikit-simulator.sh [ios|tvos|all]}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

IOS_DESTINATION="${KADRE_UIKIT_IOS_DESTINATION:-platform=iOS Simulator,name=iPhone 17,OS=27.0}"
TVOS_DESTINATION="${KADRE_UIKIT_TVOS_DESTINATION:-platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0}"

command -v xcodebuild >/dev/null || { echo "FAIL: xcodebuild indisponible" >&2; exit 1; }
command -v xcrun >/dev/null || { echo "FAIL: xcrun indisponible" >&2; exit 1; }
[[ -x ./gradlew ]] || { echo "FAIL: gradlew non exécutable: $ROOT/gradlew" >&2; exit 1; }

# La vérification porte sur le NOM de l'appareil (`name=…`) ET le runtime (`OS=…`) déclarés
# par la destination : un simulateur ou un runtime manquant doit faire échouer la gate
# avant tout travail Gradle (critère de sortie roadmap §7 — échec explicite, jamais un
# downgrade silencieux de destination).
require_destination() {
  local destination="$1" platform="$2" name os
  name="$(sed -n 's/.*name=\([^,]*\).*/\1/p' <<<"$destination")"
  if [[ -z "$name" ]]; then
    echo "FAIL: destination $platform sans 'name=' : '$destination'" >&2
    exit 1
  fi
  os="$(sed -n 's/.*OS=\([^,]*\).*/\1/p' <<<"$destination")"
  if [[ -z "$os" ]]; then
    echo "FAIL: destination $platform sans 'OS=' : '$destination'" >&2
    exit 1
  fi
  if ! xcrun simctl list devices available | grep -qF "$name"; then
    echo "FAIL: simulateur '$name' ($platform) introuvable — le job correspondant doit échouer." >&2
    exit 1
  fi
  if ! xcrun simctl list runtimes available | grep -qF "$platform $os"; then
    echo "FAIL: runtime $platform $os introuvable pour '$name' — le job correspondant doit échouer." >&2
    exit 1
  fi
  echo "OK: destination $destination"
}

run_gradle() {
  ./gradlew --console=plain "$@"
}

run_family() {
  case "$1" in
    ios)
      require_destination "$IOS_DESTINATION" "iOS"
      run_gradle \
        ":kadre:contracts:validator:generateUikitBCK012IosSimulatorArm64ContractEvidence" \
        ":kadre:contracts:validator:validateIosSimulatorArm64UikitContractEvidence"
      ;;
    tvos)
      require_destination "$TVOS_DESTINATION" "tvOS"
      run_gradle \
        ":kadre:contracts:validator:generateUikitBCK012TvosSimulatorArm64ContractEvidence" \
        ":kadre:contracts:validator:validateTvosSimulatorArm64UikitContractEvidence"
      ;;
    *) echo "FAIL: famille inconnue '$1'" >&2; exit 1;;
  esac
}

case "$FAMILY" in
  all)
    run_family ios
    run_family tvos
    # Les slices appareil compilent et se lient (pas une preuve d'exécution — roadmap §7).
    run_gradle ":kadre:platform:uikit:linkDebugFrameworkIosArm64" ":kadre:platform:uikit:linkDebugFrameworkTvosArm64"
    for target in iosSimulatorArm64 tvosSimulatorArm64; do
      artifact="kadre/contracts/driver/uikit/build/contract-evidence/$target/contract-evidence/BCK-012.json"
      [[ -f "$artifact" ]] || { echo "FAIL: artifact manquant: $artifact" >&2; exit 1; }
      echo "OK: $artifact"
    done
    ;;
  ios|tvos) run_family "$FAMILY" ;;
  *) echo "usage: test-uikit-simulator.sh [ios|tvos|all]" >&2; exit 1;;
esac

echo "uikit gate ($FAMILY): SUCCESS"
