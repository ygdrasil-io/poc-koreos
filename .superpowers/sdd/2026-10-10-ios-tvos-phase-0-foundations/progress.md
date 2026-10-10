# SDD ledger — plan: docs/superpowers/plans/2026-10-10-ios-tvos-phase-0-foundations.md

BASE au démarrage : a487c101 (origin/master). Branche : zcode/sess-56645e3f (worktree /Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f).
Commit plan : 1ff0ff24.

## Rulings pré-vol

- Ruling: la section « Model Selection » du skill SDD est inapplicable — l'outil Agent de ce harnais n'expose pas de champ model (mémoire subagent-driven-execution) ; tous les sous-agents héritent du modèle de session. Compenser par des briefs plus précis aux re-dispatchs et par le fix-loop (rounds 4–5 = re-dispatch frais, pas de bump de modèle possible). Coût si faux : coût token légèrement supérieur, pas de risque qualité.

## Scan pré-vol (paires fichiers/interfaces)

| Paire | Produit ↔ Consomme | Constat |
|---|---|---|
| T1 ↔ T2 | pattern 4 targets foundation → copié dans runtime | Cohérent (même bloc, T1 le prouve d'abord) |
| T2 ↔ T4 | runtime variants natives → `implementation(project(":kadre:runtime"))` uikitMain | T2 précède T4 ; ok |
| T4 ↔ T5 | `KadreUikitProbe.observe(window:view:): KadreUikitObservation` | Signature identique des deux côtés (enum cases Attached/Detached → .attached/.detached Swift) |
| T5 ↔ T6 | `build/xcresult/{ios,tvos}.xcresult` + méthodes XCTest | T6 re-exécute via Gradle les mêmes invocations (doFirst delete ajouté) ; noms de tests relus dans le XML produit |
| T6 ↔ T7 | noms de tâches Gradle validator | `generateUikitBCK010<Cap>ContractEvidence` / `validate<Cap>UikitContractEvidence` identiques dans les deux tasks ; T7 doit vérifier les noms réels |
| T2 ↔ T3 | verdict sonde friend-paths → PROJECT-ARCHITECTURE | T3 cite le verdict de T2 ; T2 précède |
| T8 ↔ all | sweep exit criteria | T8 re-exécute tout depuis état propre |

Auto-cohérence par task : T2 (SurfaceId constructor à vérifier — instruit), T4 (FQN @KadrePlatformApi à vérifier — instruit), T6 (args validateur par miroir du bloc AppKit lignes 88–158 — instruit). Aucune contradiction plan↔contraintes globales détectée.

## Progression

### Task 1
Task 1: complete (commits 1ff0ff24..ff294dcc, review clean)
- Note (reporter à Task 6) : matrice toolchain VERIFIÉE — Kotlin 2.4.20 + Gradle 9.8.0 ↔ Xcode 27.0 (27A266a), runtimes iOS 27.0-24A434 / tvOS 27.0-24J360, 42/42 tests natifs verts ×2 simulateurs. À consigner dans capabilities/uikit.md (Task 6 step 7).
- ⚠️ reviewer : « simulateur manquant doit échouer » non exercé — prouvé en Task 7 step 3 (override destination). Plan l'a déjà prévu.
### Task 2
- Ruling: verdict sonde visibilité Native = (a) mais NON dormant — runtime commonMain consomme des constructeurs internal de foundation (44 erreurs sans câblage) ; le câblage `-Xfriend-modules` (freeCompilerArgs, pas de propriété friendPaths sur KotlinNativeCompile en KGP 2.4.20) est requis pour compiler. Nuances : klib path = dir module `…/main/klib/<module>` exactement comme l'entrée `-library` de KGP ; arg mono-valeur last-write-wins (le klib main propre doit être répété pour les tests). Coût si faux : le wiring casse silencieusement une autre compile native — les 4 compiles + 2 tests natifs sont le filet.
- Ruling: K/N enveloppe les échecs d'init d'objet dans `kotlin.native.internal.FileFailedToInitializeException` (catchable, cause = originelle) ; classification `isLinkageFailure` = false en native (« never widen »). Le test existant `linkageFailureClassificationIsPlatformExact` devient 3 branches (JVM true / native false / js-wasm false). Un `true` natif serait un changement de politique explicite — reporté note phase 1.
- À vérifier en revue : modification du test commonTest préexistant ; Worker-based helper (kotlin.concurrent.thread absent du stdlib K/N 2.4.20 selon l'implémenteur) ; placement extensions top-level.
Task 2: complete (commits ff294dcc..4ea86dba, review clean — 6 mineurs différés)
Task 2: minor (deferred): owner identity pthread_self().hashCode() consultatif (collision théorique, jamais l'exclusion) — note phase 1.
Task 2: minor (deferred): codes retour pthread_mutex_lock/trylock non vérifiés (EAGAIN/EINVAL inatteignables en pratique).
Task 2: minor (deferred): chemin klib friend-modules codé en dur, silencieux si faux (fail loud à la compile en réalité) — note phase 1.
Task 2: minor (deferred): le test ne pinne pas l'identité du wrapper FileFailedToInitializeException (KDoc seulement).
Task 2: minor (deferred): (1..4).map { Worker.start() } ignore la valeur de range.
Task 2: minor (deferred): assertNull(frame.current()) dépend de l'absence de fuite de frame sur le thread principal des tests.
- Ruling (ack politique): FileFailedToInitializeException classé false — échec d'initialisation, pas de liaison ; confirmé en revue. Un true natif = changement de politique explicite, phase 1+.
- ⚠️ non-vérifiables acceptés: TDD red run (attesté), preuve konanc (corroborée par le précédent JS ligne 105), direction last-write-wins (non falsifiable, commentée), suites js/wasmJs non relancées (analyse de branche sûre), chevauchement réel des 4 workers (design fail-on-broken-lock).
### Task 3
Task 3: complete (commits 4ea86dba..7fe34a57, review clean)
Task 3: minor (deferred): PROJECT-ARCHITECTURE §5 dit « -Xfriend-modules » mais le canal câblé est « -friend-modules » (kadre/runtime/build.gradle.kts:80) — coquille héritée du ruling contrôleur ; le commentaire du build file reste la référence correcte. À corriger en finale si trié.
- J1/J2 (ajout « tvOS » ligne 132 runtime + sujet explicite §6.2) ruled FAITHFUL par le reviewer.
### Task 4
Task 4: complete (commits 7fe34a57..439b8d80, review clean)
- Interface Task 5 CONFIRMÉE par header généré : Swift = `KadreUikitProbe.shared.observe(window:view:)` (`.shared` obligatoire, mapping objet Kotlin) ; enum Swift `.attached`/`.detached` ; init `KadreUikitObservation(windowMembership:sceneConnected:viewBoundsWidth:viewBoundsHeight:displayScale:)`.
- Chemins frameworks réels : `kadre/platform/uikit/build/bin/<target>/debugFramework/KadreUikit.framework` (<target> ∈ iosSimulatorArm64/tvosSimulatorArm64/iosArm64/tvosArm64).
- Ruling: 3 déviations de snippets du plan prouvées compiles — `targets.withType<KotlinNativeTarget>().configureEach { binaries.framework }` (pas d'accesseur extension-level `binaries` en KGP 2.4.20, vérifié javap), `useContents { size.width to size.height }` (CGRect n'a que origin/size), `uikitMain.dependsOn(commonMain)` (canonique). Coût si faux : nul, preuves header/artefacts.
Task 4: minor (deferred): URL publishing `uri(Provider<Directory>)` vs idiom desktop `asFile.toURI()` — cosmétique.
### Task 5
Task 5: complete (commits 439b8d80..c1caabc6 [bfe0f9c4 + c1caabc6], review clean)
- Ruling: `===` Kotlin ne bridge PAS l'identité Obj-C (même UIWindow → deux wrappers, hashCode égaux, isEqual vrai) — probe fixée via `isEqual` (bfe0f9c4, commenté avec la preuve O3). Règle pour tout futur code Kadre comparant des objets host : utiliser isEqual, jamais ===. Coût si faux : un faux Detached sur vue attachée — couvert par le test BCK-010 sur les 2 familles.
- 2 mineurs info : DriverAppDelegate.swift scaffolding mort (mandaté par le plan) ; CODE_SIGN_IDENTITY "iPhone Developer" inert (défaut xcodegen, CODE_SIGNING_ALLOWED=NO domine).
- Task 6 : xcresults à `build/xcresult/{ios,tvos}.xcresult` ; noms figés `KadreUikitDriverTests.testKotlinProbeObservesRealWindow()` / `testProbeReportsViewOutsideWindowAsDetached()` vérifiés DANS les bundles ; -resultBundlePath doit être supprimé avant chaque run (doFirst déjà prévu au plan Task 6).
### Task 6
Task 6: complete (commits c1caabc6..55a98e5f, review clean — 5 mineurs différés)
Task 6: minor (deferred): **M2 le convertisseur traite un `result` inconnu comme Passed (risque faux-POSITIF)** — whitelister {"passed"} et raise sinon ; À TRIER EN REVUE FINALE (le reviewer le ferait passer avant le premier vrai run rouge).
Task 6: minor (deferred): M1 walk() n'hérite pas du `or []` de messages_of pour children=null (fail-loud, incohérent seulement).
Task 6: minor (deferred): M3 document 0-test → 0 fichiers + exit 0 (sans misleading PASS car la graphe échoue à JUnitEvidence, mais un self-check rendrait l'outil honnête standalone).
Task 6: minor (deferred): M4 agrégat generateUikitContractEvidence sans outputs.dir (up-to-date behavior seulement).
Task 6: minor (deferred): M5 registry source = chemin de répertoire vs ancre de doc ailleurs (valeur prescrite par le brief).
- Ruling: JUnit exports sous build/contract-evidence/<target>/test-results/<target>/ — FORCÉ par la règle artifact-relative no-`..` du validateur (ValidateContractEvidence.kt:41-47,196-199) ; conforme à la ligne « Produces » du brief. Coût si faux : nul, vérifié contre le parseur.
- Ruling: converter lit durationInSeconds (duration localisée à virgule "0,018s") + attribut time de suite (JUnitEvidence.kt:130 l'exige).
- Task 7 : noms de tâches confirmés exacts (dry-run) ; familles lancées séquentiellement par le script, indépendance par jobs CI séparés ; relink framework froid ≈10 min à budgéter.
### Task 7
- Ruling (concern implémenteur) : les 2 jobs CI passent sur `runs-on: xcode-27` (preview, runner-images #14404 — Xcode 27 beta 3 par défaut, base macOS 27) ; destinations inchangées OS=27.0. Pourquoi : macos-26 s'arrête à Xcode 26.6/runtimes 26.x (vérifié README image 20260824.0517.1) ; le downgrade de destination est interdit par la roadmap et contredirait TEST-STRATEGY §9 committé. Coût si faux : instabilité preview → job rouge à diagnostiquer (re-run, jamais skip) ; jamais de faux vert (require_destination échoue explicitement).
- Fix round 1 en cours (resume agent Task 7) : runs-on xcode-27 + commentaire preview.
### Task 7
Task 7: fix round 1/5 (1 addressed, 0 open — runs-on xcode-27 + commentaires preview; commit fea394e1)
Task 7: complete (commits 55a98e5f..fea394e1, review clean)
Task 7: minor (deferred): pré-vol destination par sous-chaîne (iPhone 17 matche iPhone 17e ; xcodebuild reste autoritaire — jamais de faux vert).
Task 7: minor (deferred): SIGPIPE théorique grep -q sous pipefail (faux rouge possible seulement, jamais faux vert).
Task 7: minor (deferred): override env KADRE_UIKIT_*_DESTINATION ne couvre que le pré-vol (les destinations Gradle sont codées côté driver) — frontière harnais de diagnostic documentée.
Task 7: minor (deferred): message « simulateur introuvable » si simctl lui-même erreur — cause imprécise, échec quand même explicite.
- ⚠️ non vérifiable : exécution CI réelle sur le label xcode-27 (première exécution PR révélera ; tout manque = job rouge explicite à require_destination).
### Task 8
Task 8: complete (aucun commit — sweep pur; revue clean, 3 coquilles du rapport SDD seulement: 64→80 tests, parenthèse « check JVM uniquement » FAUSSE (KGP câble les tests natifs dans :kadre:runtime:check sur macOS — seuls le gate script et le CI ne les exécutent pas; le ruling de deferral phase 1 tient toujours, périmètre corrigé), 3647 lignes)
- VERDICT FINAL CRITÈRE DE SORTIE PHASE 0 : REMPLI (confirmé indépendamment par le reviewer).
- Ruling corrigé: la note N2 du sweep — l'automatisation CI des tests natifs reste le gap (gate script + workflow), PAS le check local.
### Revue finale
VERDICT: CONDITIONAL PASS — 2 must-fix (F1 converter whitelist passed-only ; F3 doc -friend-modules), 17 park.
- Ruling F2 (umbrella publishContractArtifacts sans uikit → métadonnées Gradle natives en suspens) : COMPORTEMENT MANDATÉ PAR LE PLAN (« hors agrégation officielle phase 0 ») — on garde, note phase 1 : ajouter uikit à publishContractArtifacts au moment de sa publication officielle. Coût si faux : premier consumer natif contractTest bloqué → se verra immédiatement en phase 1.
- F4 (tests natifs hors CI), F5 (volatilité label xcode-27 ; vérif au premier PR réel), F6 (hygiène xcodeproj), F7 (SLO froid >8min, <15min timeout) : park.
