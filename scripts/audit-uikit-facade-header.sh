#!/usr/bin/env bash
# Audit façade du framework KadreUikit (phase 10 — mécanisation de l'audit §3 de
# kadre/capabilities/uikit.md). Usage : audit-uikit-facade-header.sh [--rebuild]
#
# Ce que le script VÉRIFIE (tout échec = code de sortie non nul), sur l'en-tête Obj-C
# régénéré des DEUX familles (iosSimulatorArm64 et tvosSimulatorArm64, debugFramework —
# les mêmes en-têtes que l'audit §3) :
#
#   1. ABSENCE de fuite interne :
#      - le littéral de package `org.graphiks.kadre.internal` (un opt-in interne, une
#        string de KDoc ou un symbole qui le porterait) ;
#      - tout nom exporté portant `Internal` (un Kotlin `internal` n'est JAMAIS exporté ;
#        une fuite visible porte donc le marqueur dans son nom simple) ;
#      - la règle INTEROP §7 (surface-access) : `withUIKitView` ET sa classe porteuse
#        `UIKitSurfaceAccessKt` absents de l'en-tête (le grep de re-confirmation du §3).
#
#   2. PRÉSENCE de la surface promise (INTEROP §5 + §2) : KadreIos (le host et son seam
#      attach), la factory (référence pass-through), la session, la policy et ses profils
#      intégrés, le value model fermé (KadreResult, outcome 3 cases, 4 phases,
#      KadreStopReason) et les 17 cases exactes de KadreFailure. Les enums FERMÉS
#      (outcome, phases, profils, cases) sont vérifiés à l'ÉGALITÉ STRICTE sur préfixe —
#      une case ou un profil AJOUTÉ est un élargissement de façade et fait échouer
#      l'audit (revue T4-F1), pas seulement une case retirée.
#
#   3. INVENTAIRE scaffolding (toléré INTEROP §1 « détail généré » / §2) — COMPTEURS
#      AFFICHÉS, jamais un échec : un drift invite à rafraîchir le §3 (lignes /
#      @interface / @protocol, sondes driver documentées, annotations opt-in visibles).
#
# Et sur les SOURCES du module Swift façade (platform/uikit/swift/Sources/KadreHost) :
#
#   4. les types promise présents (KadreIosHost, KadreHostViewController, KadreSessionHandle,
#      KadreObservation, KadrePolicyProfile, KadreFailure et ses 17 cases) et AUCUN type
#      coroutine/Flow/Continuation/Throwable sur une déclaration `public` (règle INTEROP §9).
#
# Limite HONNÊTE du contrôle 1 (frontière BCK-019 « facade symbol leak ») : le nommage
# Obj-C (préfixe framework + nom simple) ne porte PAS le package Kotlin — un type PUBLIC
# d'un package `…internal…` dont le nom simple ne contient pas « Internal » n'est PAS
# détectable dans l'en-tête. Ces types sont exactement le scaffolding DOCUMENTÉ du §3
# (les sondes driver, opt-in `@KadrePlatformApi`) : l'inventaire 3 est la garde de cette
# tolérance — toute nouvelle entrée y doit être listée, sinon c'est un rejet de revue.
set -euo pipefail

REBUILD=0
[[ "${1:-}" == "--rebuild" ]] && REBUILD=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

HEADER_REL="KadreUikit.framework/Headers/KadreUikit.h"
IOS_HEADER="kadre/platform/uikit/build/bin/iosSimulatorArm64/debugFramework/$HEADER_REL"
TVOS_HEADER="kadre/platform/uikit/build/bin/tvosSimulatorArm64/debugFramework/$HEADER_REL"
FACADE_DIR="kadre/platform/uikit/swift/Sources/KadreHost"

if [[ $REBUILD -eq 1 || ! -f "$IOS_HEADER" || ! -f "$TVOS_HEADER" ]]; then
  [[ -x ./gradlew ]] || { echo "FAIL: gradlew non exécutable: $ROOT/gradlew" >&2; exit 1; }
  echo "== génération des en-têtes debugFramework (lien Kotlin/Native) =="
  ./gradlew --console=plain \
    ":kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64" \
    ":kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64"
fi

FAILURES=0

check_zero() { # header label pattern description
  local header="$1" label="$2" pattern="$3" description="$4" count
  count="$(grep -cE "$pattern" "$header" || true)"
  if [[ "$count" -ne 0 ]]; then
    echo "FAIL: [$label] $description — '$pattern' trouvé $count fois (attendu 0)" >&2
    FAILURES=$((FAILURES + 1))
  else
    echo "OK: [$label] $description — '$pattern' = 0"
  fi
}

check_present() { # header label pattern description [minimum]
  local header="$1" label="$2" pattern="$3" description="$4" minimum="${5:-1}" count
  count="$(grep -cE "$pattern" "$header" || true)"
  if [[ "$count" -lt "$minimum" ]]; then
    echo "FAIL: [$label] $description — '$pattern' = $count (attendu >= $minimum)" >&2
    FAILURES=$((FAILURES + 1))
  else
    echo "OK: [$label] $description — '$pattern' = $count"
  fi
}

# Les enums FERMÉS de la surface promise (revue T4-F1) : un check_present à `>= N` et un
# motif qui n'énumère que les noms connus laisseraient PASSER une case AJOUTÉE (une 4e
# case d'outcome, un 4e profil) — or un élargissement de surface EST la diff de contrat
# que l'audit doit attraper. Ces contrôles comptent donc sur le PRÉFIXE (tout nom de case,
# connu ou non) et exigent l'ÉGALITÉ stricte — le mécanisme des 17 cases KadreFailure.
check_exact() { # header label pattern description expected
  local header="$1" label="$2" pattern="$3" description="$4" expected="$5" count
  count="$(grep -cE "$pattern" "$header" || true)"
  if [[ "$count" -ne "$expected" ]]; then
    echo "FAIL: [$label] $description — '$pattern' = $count (attendu EXACTEMENT $expected)" >&2
    FAILURES=$((FAILURES + 1))
  else
    echo "OK: [$label] $description — '$pattern' = $count (exact)"
  fi
}

count_distinct() { # header pattern -> stdout
  # `|| true` : sous pipefail, un grep sans match (exit 1) tuerait sinon la substitution.
  { grep -oE "$2" "$1" || true; } | sort -u | wc -l | tr -d ' '
}

report_inventory() { # header label
  local header="$1" label="$2" lines interfaces protocols
  lines="$(wc -l < "$header" | tr -d ' ')"
  interfaces="$(grep -cE '^@interface ' "$header" || true)"
  protocols="$(grep -cE '^@protocol ' "$header" || true)"
  echo "INFO: [$label] inventaire en-tête — lignes=$lines @interface=$interfaces @protocol=$protocols" \
       "(référence §3 au 2026-10-15 : 7765 / 491 / 102 — un drift invite à rafraîchir le §3, pas un échec)"
}

audit_header() {
  local header="$1" label="$2"

  # 1. Absence de fuite interne (et règle INTEROP §7).
  check_zero "$header" "$label" 'org\.graphiks\.kadre\.internal' \
    "aucun littéral de package internal"
  check_zero "$header" "$label" 'Internal' \
    "aucun nom exporté portant le marqueur Internal"
  check_zero "$header" "$label" 'withUIKitView' \
    "règle INTEROP §7 : surface-access masqué"
  check_zero "$header" "$label" 'UIKitSurfaceAccessKt' \
    "règle INTEROP §7 : la classe porteuse absente"

  # 2. Présence de la surface promise (INTEROP §5 + §2).
  check_present "$header" "$label" '@interface KadreUikitKadreIos :' "le host KadreIos"
  check_present "$header" "$label" 'attachWindowScene:\(UIWindowScene \*\)windowScene' \
    "le seam attach(windowScene:window:surfaceView:applicationFactory:policy:…)"
  check_present "$header" "$label" '@protocol KadreUikitKadreApplicationFactory' \
    "la factory (référence pass-through du module applicatif)"
  check_present "$header" "$label" '@protocol KadreUikitKadreSession' "la session"
  check_present "$header" "$label" '@interface KadreUikitKadrePolicy :' "la policy"
  check_present "$header" "$label" '@interface KadreUikitKadrePolicies :' "les profils intégrés"
  check_present "$header" "$label" '@protocol KadreUikitKadreFailure' "le protocole KadreFailure"
  check_present "$header" "$label" 'KadreUikitKadreResult(Success|Failure)' \
    "le value model KadreResult" 2
  # Égalité stricte sur PRÉFIXE (revue T4-F1) : une case/phase AJOUTÉE passerait un
  # motif qui n'énumère que les noms connus — l'élargissement doit échouer.
  check_exact "$header" "$label" '^@interface KadreUikitSessionOutcome' \
    "l'outcome fermé (exactement 3 cases — une case ajoutée est un élargissement de façade)" 3
  check_exact "$header" "$label" '^@interface KadreUikitSessionState' \
    "les 4 phases de session (exactement — une phase ajoutée est un élargissement de façade)" 4
  check_present "$header" "$label" '^@interface KadreUikitSessionStopReason :' "KadreStopReason"

  local failure_cases
  failure_cases="$(count_distinct "$header" 'KadreUikitKadreFailure[A-Z][A-Za-z]*')"
  if [[ "$failure_cases" -ne 17 ]]; then
    echo "FAIL: [$label] les cases KadreFailure — $failure_cases noms distincts (attendu 17, INTEROP §5)" >&2
    FAILURES=$((FAILURES + 1))
  else
    echo "OK: [$label] les cases KadreFailure — 17 noms distincts (INTEROP §5)"
  fi

  # 3. Inventaire scaffolding (toléré INTEROP §1/§2) — compteurs affichés.
  report_inventory "$header" "$label"
  check_present "$header" "$label" 'KadreUikitKadreUikitSurfaceDriver' \
    "scaffolding driver : la sonde de conduite (§3)" 1
  check_present "$header" "$label" 'openTextInputProbe|touchRecognitionProbe|gestureRecognitionProbe|captureLifecycleSignals|captureSourceRefusalProbe' \
    "scaffolding driver : les conduites de sonde (§3)" 5
  check_present "$header" "$label" 'ExperimentalKadreApi|DelicateKadreApi|KadrePlatformApi' \
    "les opt-in documentés (annotation comments)" 3
}

echo "== audit façade KadreUikit — en-têtes Obj-C régénérés =="
audit_header "$IOS_HEADER" "iosSimulatorArm64"
audit_header "$TVOS_HEADER" "tvosSimulatorArm64"

echo "== module Swift façade (KadreHost) — surface promise INTEROP §5 =="
# Répertoire : le grep est RECURSIF (un grep plat sur le répertoire serait une erreur
# silencieuse — compté « 0 » sans avoir lu un seul fichier).
forbidden_public="$({ grep -rE --include='*.swift' 'public.*(Flow|CoroutineScope|Continuation|Throwable)' "$FACADE_DIR" || true; } | wc -l | tr -d ' ')"
if [[ "$forbidden_public" -ne 0 ]]; then
  echo "FAIL: [swift-facade] types coroutine/Flow/Continuation/Throwable sur une déclaration public — $forbidden_public occurrence(s) (INTEROP §9)" >&2
  grep -rnE 'public.*(Flow|CoroutineScope|Continuation|Throwable)' "$FACADE_DIR" >&2
  FAILURES=$((FAILURES + 1))
else
  echo "OK: [swift-facade] aucun type coroutine/Flow/Continuation/Throwable sur une déclaration public (INTEROP §9)"
fi
check_present "$FACADE_DIR/KadreIosHost.swift" "swift-facade" 'public enum KadreIosHost' "KadreIosHost"
check_present "$FACADE_DIR/KadreIosHost.swift" "swift-facade" 'public static func attach\(' "l'attach §5"
check_present "$FACADE_DIR/KadreHostViewController.swift" "swift-facade" 'public final class KadreHostViewController' "KadreHostViewController"
check_present "$FACADE_DIR/KadreHostViewController.swift" "swift-facade" 'public private\(set\) var kadreSession' "la session exposée du controller"
check_present "$FACADE_DIR/KadreSessionHandle.swift" "swift-facade" 'public final class KadreSessionHandle' "KadreSessionHandle"
check_present "$FACADE_DIR/KadreSessionHandle.swift" "swift-facade" \
  'public func (observeState|requestStop|close|awaitTermination)\(' \
  "les quatre membres §5 (observeState/requestStop/close/awaitTermination)" 4
check_present "$FACADE_DIR/KadreObservation.swift" "swift-facade" 'public final class KadreObservation' "KadreObservation"
check_present "$FACADE_DIR/KadreObservation.swift" "swift-facade" 'public func close\(\)' "la retrait de l'observation"
# Égalité stricte sur le bloc de l'enum (revue T4-F1) : compter TOUTES les cases du bloc
# `public enum KadrePolicyProfile` — un 4e profil ajouté doit échouer, pas passer en >= 3.
profile_cases="$(sed -n '/^public enum KadrePolicyProfile/,/^}/p' "$FACADE_DIR/KadrePolicyProfile.swift" | grep -cE '^    case ' || true)"
if [[ "$profile_cases" -ne 3 ]]; then
  echo "FAIL: [swift-facade] les 3 profils de policy — $profile_cases cases dans le bloc (attendu EXACTEMENT 3, INTEROP §5)" >&2
  FAILURES=$((FAILURES + 1))
else
  echo "OK: [swift-facade] les 3 profils de policy — 3 cases dans le bloc (exact, INTEROP §5)"
fi
# Le fichier porte AUSSI les enums compagnon (KadreOperation, KadrePermission…) : le
# comptage des cases est SCOPÉ au bloc `public enum KadreFailure` — pas au fichier entier.
facade_failure_cases="$(sed -n '/^public enum KadreFailure/,/^}/p' "$FACADE_DIR/KadreFailure.swift" | grep -cE '^    case ' || true)"
if [[ "$facade_failure_cases" -ne 17 ]]; then
  echo "FAIL: [swift-facade] les cases KadreFailure — $facade_failure_cases (attendu 17, INTEROP §5)" >&2
  FAILURES=$((FAILURES + 1))
else
  echo "OK: [swift-facade] les cases KadreFailure — 17 cases (INTEROP §5)"
fi

# Le wrapper SwiftUI est possédé par l'APP (INTEROP §5 : ~10 lignes autour du controller),
# jamais par le framework — présence vérifiée côté consumer, hors surface promise.
if [[ -f kadre/consumers/swift/consumer-app/apps/Shared/ConsumerRepresentable.swift ]] \
  && grep -q 'struct KadreHostView: UIViewControllerRepresentable' \
    kadre/consumers/swift/consumer-app/apps/Shared/ConsumerRepresentable.swift; then
  echo "INFO: [consumer] le representable SwiftUI KadreHostView est possédé par l'app consumer (INTEROP §5)"
else
  echo "FAIL: [consumer] le representable SwiftUI KadreHostView est introuvable côté consumer-app" >&2
  FAILURES=$((FAILURES + 1))
fi

if [[ "$FAILURES" -ne 0 ]]; then
  echo "FAIL: audit façade — $FAILURES contrôle(s) en échec" >&2
  exit 1
fi
echo "OK: audit façade KadreUikit — en-têtes (2 familles) + module Swift façade conformes à la surface promise"
