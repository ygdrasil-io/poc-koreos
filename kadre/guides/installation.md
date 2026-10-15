# Guide — Installer la distribution UIKit/tvOS

**Vérité par feature :** [`kadre/capabilities/uikit.md`](../capabilities/uikit.md) —
ligne distribution XCFramework statique (phase 3), §3 audit de l'en-tête, §4 gate de
release, et la note de gate phase 3 (F-2). Registre : des décisions, pas des promesses.

## L'artefact : un XCFramework STATIQUE, quatre slices

`./gradlew :kadre:platform:uikit:assembleKadreUikitDebugXCFramework` (ou `Release`)
assemble `build/XCFrameworks/<buildType>/KadreUikit.xcframework` — quatre slices listées
dans l'`Info.plist` (`AvailableLibraries`) :

- `ios-arm64`, `ios-arm64-simulator` ;
- `tvos-arm64`, `tvos-arm64-simulator`.

Le binaire est **statique** (`isStatic = true`, baseName `KadreUikit`,
`kadre/platform/uikit/build.gradle.kts:44-75`) et exporte la foundation — sans cet
export, la session rendue par `attach` serait inutilisable depuis Swift (le
`KadreResult<KadreSession>` s'efface en `id<KadreResult>` côté Obj-C).

Décisions portées par l'artefact (à connaître avant d'intégrer) :

- **pas de publication binaire signée** : l'xcframework est un artefact de preuve local
  (`build/` non commité) ; les slices **device** sont assemblables mais non exercées —
  les preuves de ce dépôt sont Simulator-scope (colonne `runtime gate` du registre) ;
- **l'en-tête audité** : `withUIKitView` (le bail natif) n'apparaît dans AUCUN en-tête ni
  slice (`@HiddenFromObjC`, grep re-confirmé = 0) ; l'audit est mécanisé —
  `scripts/audit-uikit-facade-header.sh` rejoue les greps (fuites internal, présence des
  symboles promise, inventaire scaffolding) sur les en-têtes des deux familles.

## La consommation : publications contractTest, jamais `project(...)`

Un consumer de ce dépôt ne lie JAMAIS les modules par `project(...)` — il résout les
artefacts PUBLIÉS du repository contractTest, comme un consumer externe le ferait :

```bash
# 1. Publier (racine du dépôt) : les publications communes + uikit + foundation
./gradlew publishContractArtifacts \
          :kadre:platform:uikit:publishAllPublicationsToContractTestRepository \
          :kadre:foundation:publishAllPublicationsToContractTestRepository

# 2. Builder le consumer
./gradlew :kadre:validateSwiftConsumer -PkadreRepository=... -PkadreVersion=...
```

En pratique, le point d'entrée unique fait les deux :
`./gradlew :kadre:validateSwiftConsumer` publie AU PRÉALABLE le repository contractTest
puis pilote le `check` de l'agrégateur `kadre/consumers/swift` avec
`-PkadreRepository`/`-PkadreVersion` (`kadre/build.gradle.kts:155-177`). Le consumer
reçoit les deux propriétés **requises** (`error(...)` si absentes —
`kadre/consumers/swift/build.gradle.kts:27-32`) et vérifie que le repository porte bien
`org/graphiks/kadre/uikit/<version>/uikit-<version>.module`. `--refresh-dependencies` est
armé : aucun repository périmé ne passe inaperçu.

La tâche est **macOS-only, volontairement hors `:kadre:check`** (xcodebuild,
simulateurs, xcodegen — miroir des gates AppKit) : sur un hôte non-macOS l'échec est
explicite, jamais un skip silencieux. Le `check` enchaîne xcodegen, les QUATRE runs
xcodebuild (standard + lancement gated SwiftUI, iOS et tvOS — destinations iPad Pro
11-inch (M5) / iOS 27.0 et Apple TV 4K (3rd generation) / tvOS 27.0), leurs exports JUnit
(`tools/xctest_to_junit.py`) et la garde anti-duplication ci-dessous. Le gate par famille
`scripts/test-uikit-simulator.sh [ios|tvos|all]` pilote le même agrégateur
(`run_consumer`) aux côtés des preuves du driver.

Rien n'empêche un consumer HORS de ce dépôt de consommer le même repository publié avec
les mêmes `-P` : c'est la forme d'intégration prévue ; une publication officielle
signée reste une décision future, non prise.

**Le sample minimal EST cette app** : le consumer `kadre/consumers/swift` est l'intégration
de référence runnable — la décision de forme (pas de module `samples/` dupliqué) et le
sample tvOS de coexistence (contrôles natifs focusables autour de la surface, focus
SYSTÈME, saisie BCK-024, manette conditionnelle par matériel) sont documentés dans
[`kadre/consumers/swift/README.md`](../consumers/swift/README.md) ; le récit tvOS,
dans [`tvos.md`](tvos.md).

## Lien statique et la contrainte anti-duplication

L'app consumer lie DEUX archives statiques : le framework du module applicatif (la
factory) ET le `KadreUikit.xcframework` de distribution, **tous deux sans embed** (un
framework statique ne s'embarque pas dans le bundle) — le xcframework est passé au lieur
PAR CHEMIN dans `OTHER_LDFLAGS`, pas comme dépendance framework (sinon Xcode résoudrait
le module Swift `KadreUikit` depuis la distribution, qui ne porte pas la factory) ;
`kadre/consumers/swift/consumer-app/project.yml` documente le câblage exact.

Sémantique du lien : à sémantique d'archive paresseuse, **ld ne refuse PAS un doublon**
— il garde silencieusement le PREMIER membre définissant chaque symbole. Les vraies
gardes sont :

1. **au lancement**, une classe Obj-C définie deux fois est loggée/crashée par le
   runtime (« Class … is implemented in both ») — le `doLast` des tâches xcodebuild du
   consumer refuse tout log qui en contient
   (`kadre/consumers/swift/build.gradle.kts:147`, garde répétée :199 pour le run gated) ;
2. **dans les bundles de test**, les search paths sont posés SANS liaison
   (`BUNDLE_LOADER` fournit les symboles depuis l'app hôte) pour ne jamais relier les
   archives dans le process de test.

Corollaire d'intégration : ne liez les archives qu'UNE fois par app et ne mélangez
jamais un framework dynamique de Kadre avec la distribution statique — la contrainte
« une seule copie du runtime et de chaque classe Obj-C dans le binaire » est la
décision, sa violation ne se voit qu'au lancement.

## Le préfixe Obj-C : un seul, `KadreUikit`

Le préfixe Obj-C exporté est dérivé du NOM DU FRAMEWORK — aucun override n'existe
(l'option `objcExportPrefix` est supprimée du toolchain 2.4.20). Conséquence normative :

- TOUT fragment Kotlin qui doit être typable contre la distribution exporte ses classes
  avec le MÊME préfixe exact `KadreUikit` — donc un framework dont `baseName` vaut
  `KadreUikit` (c'est le choix du module applicatif du consumer ;
  `consumer-kmp/build.gradle.kts`, en-tête « POURQUOI baseName = KadreUikit »). Un
  préfixe différent produirait deux familles de classes Obj-C distinctes pour les MÊMES
  types Kotlin et casserait l'identité de types au passage de la factory ;
- la foundation doit être **exportée aussi** par chaque fragment : un module transitif
  non exporté verrait ses classes préfixées `KadreUikitFoundation*` au lieu du nom plat
  `KadreUikit*` — deux noms pour les mêmes classes, l'identité de symboles brisée ;
- la distribution elle-même garde `baseName = "KadreUikit"` sur les DEUX familles — un
  seul préfixe, jamais un lien des deux dans une même app.

## Récapitulatif d'intégration

1. `assembleKadreUikit{Debug,Release}XCFramework` → `KadreUikit.xcframework` (4 slices).
2. Votre module applicatif Kotlin : framework statique `baseName = "KadreUikit"`,
   `isStatic = true`, `export(uikit)` + `export(foundation)`.
3. Votre app : lie les deux archives sans embed (xcframework par chemin `OTHER_LDFLAGS`),
   compile les sources `KadreHost` (`KadreIosHost`, `KadreHostViewController`…) dans
   l'app.
4. Vérifiez le lancement : zéro « Class … is implemented in both » — c'est LE garde.
5. Preuve d'intégration runnable : `./gradlew :kadre:validateSwiftConsumer`.
