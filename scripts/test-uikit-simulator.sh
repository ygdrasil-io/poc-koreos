#!/usr/bin/env bash
# UIKit contract gate (BCK-012, BCK-013, BCK-014, BCK-015, BCK-016, BCK-020, BCK-021, BCK-022, BCK-023, BCK-024, BCK-026, BCK-027, BCK-028, WIN-009). Usage: test-uikit-simulator.sh [ios|tvos|all]
# Sélectionne explicitement la famille et sa destination, vérifie la toolchain,
# lance les tests, valide les preuves et retourne un code d'échec fidèle.
set -euo pipefail

FAMILY="${1:?usage: test-uikit-simulator.sh [ios|tvos|all]}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Destination iOS = iPad : le multi-scène (demande de scènes supplémentaires) est refusé par
# l'idiome téléphone (« The current device does not support multiple scenes. ») — la preuve
# multi-scène (Task 5, tests 1/5/6) exige un iPad. Les scènes secondaires sont demandées par
# l'APP au lancement : passer TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=2 à xcodebuild test.
# iPhone 17 reste atteignable localement via KADRE_UIKIT_IOS_DESTINATION (7 tests partagés seulement).
IOS_DESTINATION="${KADRE_UIKIT_IOS_DESTINATION:-platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0}"
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

# Contrats actifs du gate UIKit, par famille — miroir des requiredTargets du registre
# (colonne 8 de kadre/contracts/registry/contracts.tsv) et de la liste
# uikitRequiredTargetsByContractId de kadre/contracts/validator/build.gradle.kts :
# BCK-014 (multi-scène iPadOS), BCK-016 (rotation iPadOS) et BCK-021 (scènes corrélées
# iPadOS) n'exigent QUE iosSimulatorArm64, la suite tvOS reste mono-scène ; BCK-020
# (refus de requestWindow sans provider), BCK-022/BCK-023 (sources d'entrée annoncées :
# touch/pointeur, clavier/gestes), BCK-024 (la session texte sur le receiver caché) et
# BCK-026 (la destination de drop native : flux iOS + sonde d'absence tvOS), BCK-027
# (displays/devices/gamepads/pression mémoire, avec sentinelle gamepad portée par uikitTest)
# et BCK-028 (la capture : flux de surface borné, disponibilité honnête, cellule écran
# structurellement inatteignable, avec sentinelles portées par uikitTest et le driver)
# courent sur les DEUX familles.
IOS_CONTRACT_IDS=(BCK-012 BCK-013 BCK-014 BCK-015 BCK-016 BCK-020 BCK-021 BCK-022 BCK-023 BCK-024 BCK-026 BCK-027 BCK-028 WIN-009)
TVOS_CONTRACT_IDS=(BCK-012 BCK-013 BCK-015 BCK-020 BCK-022 BCK-023 BCK-024 BCK-026 BCK-027 BCK-028 WIN-009)

# Une SEULE invocation Gradle par famille : les tâches generate partagent le même
# dependOn (export JUnit → xcodebuild) — une invocation exécute la suite simulateur UNE fois,
# des invocations successives la rejoueraient à chaque fois (Exec sans sorties déclarées).
run_family() {
  local ids generates=() target id
  case "$1" in
    ios)
      require_destination "$IOS_DESTINATION" "iOS"
      ids=("${IOS_CONTRACT_IDS[@]}")
      target="IosSimulatorArm64"
      ;;
    tvos)
      require_destination "$TVOS_DESTINATION" "tvOS"
      ids=("${TVOS_CONTRACT_IDS[@]}")
      target="TvosSimulatorArm64"
      ;;
    *) echo "FAIL: famille inconnue '$1'" >&2; exit 1;;
  esac
  for id in "${ids[@]}"; do
    generates+=(":kadre:contracts:validator:generateUikit${id//-/}${target}ContractEvidence")
  done
  run_gradle "${generates[@]}" ":kadre:contracts:validator:validate${target}UikitContractEvidence"
}

assert_artifacts() {
  local target="$1"; shift
  local id artifact
  for id in "$@"; do
    artifact="kadre/contracts/driver/uikit/build/contract-evidence/$target/contract-evidence/$id.json"
    [[ -f "$artifact" ]] || { echo "FAIL: artifact manquant: $artifact" >&2; exit 1; }
    echo "OK: $artifact"
  done
}

case "$FAMILY" in
  all)
    run_family ios
    run_family tvos
    # Les slices appareil compilent et se lient (pas une preuve d'exécution — roadmap §7).
    run_gradle ":kadre:platform:uikit:linkDebugFrameworkIosArm64" ":kadre:platform:uikit:linkDebugFrameworkTvosArm64"
    # Tests natifs du runtime sur les deux simulateurs (mineur N2 de phase 0, promu phase 1) :
    # les rapports XML sont régénérés à chaque gate.
    run_gradle ":kadre:runtime:iosSimulatorArm64Test"
    run_gradle ":kadre:runtime:tvosSimulatorArm64Test"
    assert_artifacts iosSimulatorArm64 "${IOS_CONTRACT_IDS[@]}"
    assert_artifacts tvosSimulatorArm64 "${TVOS_CONTRACT_IDS[@]}"
    ;;
  ios|tvos) run_family "$FAMILY" ;;
  *) echo "usage: test-uikit-simulator.sh [ios|tvos|all]" >&2; exit 1;;
esac

echo "uikit gate ($FAMILY): SUCCESS"
