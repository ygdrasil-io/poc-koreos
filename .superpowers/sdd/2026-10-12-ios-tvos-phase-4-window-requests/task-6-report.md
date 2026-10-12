# Task 6 Report — Sweep à froid du jalon B + fix DESIGN sanctionné

**Status: DONE** (un flake de lancement simulateur sur le consumer iOS, disqualifié comme
environnemental par re-run vert à HEAD identique — détails §2.6 ; aucune blocage)
**Commit: `90550a15`** — `docs(design): show the phase 4 windowSceneProvider in the UIKit attach signature`
Worktree `/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f` (branch `zcode/sess-56645e3f`,
base de départ `72abe375`, arbre propre avant toute intervention)

---

## 1. Le fix DESIGN.md sanctionné (fait EN PREMIER, puis sweep à ce HEAD)

Sanction Task 5 (une ligne) : le bloc de code `KadreIos.attach` de DESIGN.md §15.2
précédait le paramètre phase 4. Une SEULE ligne insérée, calquée sur la signature réelle
(`kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreIos.kt`,
`public fun attach(...)`, paramètre `windowSceneProvider: UIKitWindowSceneProvider? = null`
en dernière position) :

```diff
--- a/kadre/DESIGN.md
         surfaceView: UIView,
         applicationFactory: KadreApplicationFactory,
         policy: KadrePolicy = KadrePolicies.Default,
+        windowSceneProvider: UIKitWindowSceneProvider? = null,
     ): KadreResult<KadreSession>
```

- `git diff --stat` avant commit : `kadre/DESIGN.md | 1 +` — portée doc-only vérifiée
  (aucun autre fichier, aucune autre ligne).
- Commit `90550a153bf58efee218af8eea0bfa04fb237ec4`, arbre propre ensuite.
- Conséquence voulue : TOUTE la preuve du sweep (y compris les JSON d'evidence du gate,
  qui embarquent le hash du commit — vérifié §2.5 : `"commit": "90550a153bf58e…"`) est
  produite au HEAD final unique `90550a15`.

## 2. Le sweep à froid — commandes et sorties décisives

Toutes les commandes depuis la racine du worktree ; Gradle `--rerun-tasks` partout (froid
réel, 17 à 36 tâches « executed » par invocation, zéro up-to-date sur les tâches de test).

### 2.1 `./gradlew --console=plain :kadre:runtime:jvmTest --rerun-tasks`
**BUILD SUCCESSFUL in 22s** (17 actionable tasks: 17 executed). XML régénérés
(`kadre/runtime/build/test-results/jvmTest/`, 16:46) : **408 tests, 0 failures, 0 errors**.

### 2.2 `./gradlew --console=plain :kadre:backend:appkit:appKitNativeTests --rerun-tasks`
**BUILD SUCCESSFUL in 43s** (29 actionable tasks: 29 executed). XML : jvmTest appkit
**371 tests, 0 failures** + `appKitStandaloneLoopTest` **2 tests, 0 failures**
(preuves process-owning du standalone loop macOS).

### 2.3 `./gradlew --console=plain :kadre:runtime:iosSimulatorArm64Test :kadre:runtime:tvosSimulatorArm64Test --rerun-tasks`
**BUILD SUCCESSFUL in 48s** (36 actionable tasks: 36 executed). XML horodatés 16:47:52
(au HEAD `90550a15`) : **iosSimulatorArm64 83 tests, 0 failures** ; **tvosSimulatorArm64
83 tests, 0 failures**.

### 2.4 `./scripts/test-uikit-simulator.sh all` (background + polling)
**`uikit gate (all): SUCCESS`, exit 0.** Destinations vérifiées avant tout travail :
`OK: destination platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0` ;
`OK: destination platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0`.
- Driver iOS (`KadreUikitDriverIosTests.xctest`) : **28 tests, 0 failures** —
  Attach 10, Driver 2, Surface 9, **WindowRequest 7** ; `** TEST SUCCEEDED **`.
- Driver tvOS (`KadreUikitDriverTvOsTests.xctest`) : **18 tests, 0 failures** —
  Attach 7, Driver 2, Surface 8, **WindowRequest 1** (le refus partagé) ; `** TEST SUCCEEDED **`.
- Links slices appareil (`linkDebugFrameworkIosArm64`/`TvosArm64`) : BUILD SUCCESSFUL.
- `:kadre:runtime:iosSimulatorArm64Test`/`tvosSimulatorArm64Test` ré-invoqués par le gate :
  up-to-date (les XML frais 16:47 du §2.3 font foi — la même exécution servirait à rejouer).
- `assert_artifacts` : **13/13 OK** (iOS : BCK-012, 013, 014, 015, 016, 020, 021, WIN-009 ;
  tvOS : BCK-012, 013, 015, 020, WIN-009) sous
  `kadre/contracts/driver/uikit/build/contract-evidence/<target>/contract-evidence/`.

### 2.5 Fraîcheur des evidence (preuve du HEAD)
Chaque JSON régénéré par le gate porte le hash du commit courant :
`"commit": "90550a153bf58efee218af8eea0bfa04fb237ec4"` sur les 12 artifacts des contrats
courants (BCK-010, resté à `fea394e1`, est hors gate phase 4 — pas régénéré par le script,
ni requis par `assert_artifacts`). Horodatage : 16:49:29. Bilan oracle par cible :
- iOS : **scénarios BCK-021 ×6 Passed + BCK-020 ×1 Passed**, sentinelles toutes **Killed**
  (`uikit-window-request-correlation-crossed`, `uikit-window-request-cancel-terminal-faithful`,
  `uikit-window-request-tvos-refusal-faithful`) — `tests: 28/0f/0e`.
- tvOS : **BCK-020 ×1 Passed**, sentinelle `uikit-window-request-tvos-refusal-faithful`
  **Killed** — `tests: 18/0f/0e`.

### 2.6 Consumers ×2 destinations (`kadre/consumers/swift`)
Prérequis : `./gradlew --console=plain publishContractArtifacts :kadre:platform:uikit:publishAllPublicationsToContractTestRepository`
→ **PUBLISH_EXIT=0** (repo contract re-publié au HEAD `90550a15`), puis
`cd kadre/consumers/swift && ../../../gradlew -PkadreRepository=<racine>/build/kadre-contract-repository -PkadreVersion=1.0.0 <tâches>` :
- **iOS** (`iosSimulatorTests`, destination iPad Pro 11-inch M5) : premier run **ÉCHOUÉ**
  (32/47 tests, signature unique « no attach within 25s — the host app never attached its
  primary scene » / « hostSceneUnavailable — the app did not finish launching » ; 0
  duplication Obj-C, 0 crash — le prérequis scène de l'app hôte n'est jamais arrivé).
  **Re-run à HEAD identique : `** TEST SUCCEEDED **`, 47 exécutés, 1 skipped, 0 failures**
  (Attach 1, Mapping 29, ViewController 8, SessionHandle 6, SwiftUI 3) — échec
  environnemental du test host (le simulateur venait d'enchaîner les 46 boots du gate),
  pas une régression : la voie d'attach consumer est la même que celle des 28 tests
  driver iOS verts 3 minutes plus tôt au même HEAD.
- **tvOS** (`tvosSimulatorTests`, destination Apple TV 4K 3rd gen) :
  `** TEST SUCCEEDED **`, **47 exécutés, 1 skipped, 0 failures**, zéro
  « is implemented in both » (la preuve anti-duplication deux-frameworks statiques).
- Les deux destinations lient la façade KadreHost modifiée phase 4
  (`KadreIosHost` → `windowSceneProvider: nil` explicite, commit `5ad3b699`) — premier
  EXÉCUTÉ du consumer contre la signature étendue (Task 3 note de suivi §6.1 close).

## 3. Checklist jalon B (roadmap l.248 — critère de sortie phase 4) : les cinq verdicts

| # | Critère (roadmap l.248) | Verdict | Preuves |
|---|---|---|---|
| 1 | `OpenedInNewSession` suit toujours une vraie connexion corrélée | **TENU** | Scenario `uikit-window-request-opens-correlated-scene` **Passed** (evidence `iosSimulatorArm64/…/BCK-021.json`, commit 90550a15) ; source `tests/Ios/KadreUikitWindowRequestIosTests.swift` : la demande reste `Pending` jusqu'à la résolution (l.109-111), le provider reçoit l'identité exacte (`call.requestId.isEqual(request.id)`, l.49), l'outcome est `OpenedInNewSession` (l.51-52), les objets host résolus sont CEUX du fixture de la scène corrélée (`child.resolved.window === child.fixture.window`, l.119-122) et la scène enfant est une NOUVELLE scène (l.121). Renfort : `uikit-window-request-concurrent-pair` **Passed** — corrélations NON croisées par identité (`first.resolved.requestId.isEqual(firstRequest.id)`, scènes deux à deux distinctes, l.245-257) ; la sentinelle de mutation `uikit-window-request-correlation-crossed` est **Killed** (l'evidence meurt si la corrélation croise). |
| 2 | Une nouvelle fenêtre n'est JAMAIS ajoutée à la liste de la session demandeuse | **TENU** | Même scénario : `XCTAssertEqual(stateAfterChild.windows.count, 1, "the child must never join the requester's window list")` (l.126), primaire intacte (l.129-130), aucune ré-exécution applicative du requérant (l.131-132) ; ré-asserté dans la paire concurrente (l.270) et les overlays (l.308 — « the overlay must never become a Kadre window »). |
| 3 | Parcours positif démontré sur une configuration iPad multi-scène | **TENU** | Les **6 scénarios** de `KadreUikitWindowRequestIosTests` (extension iOS-only de `KadreUikitWindowRequestTests`, destination iPadOS 27 — le gate refuse explicitement toute destination non multi-scène, cf. l'en-tête du script) tous **Passed** dans `BCK-021.json` : opens-correlated-scene, child-session-independent, requester-close-detaches-child, concurrent-pair, overlays-preserved, cancel-before-connect ; BCK-014 (`uikit-two-scenes-independent`) Passed en renfort multi-scènes. |
| 4 | Le parcours tvOS prouve `Rejected(Unsupported(RequestWindow))` sans effet natif | **TENU** | Scenario `uikit-window-request-tvos-rejected` **Passed** sur tvOS ET iOS (scénario partagé) ; source `tests/Shared/KadreUikitWindowRequestTests.swift` l.239-278 : capability `CapabilityUnsupported` sur `requestWindow` (l.247-250), demande DÉJÀ terminale `Rejected(Unsupported(...))` (l.252-258), **compteur provider inchangé** (`openCallCount` avant==après, l.240+265), seconde demande refusée pareil (l.275-276), primaire intacte (`stateAfter.windows.count == 1`, l.278). Sentinelle `uikit-window-request-tvos-refusal-faithful` **Killed** sur les deux cibles. |
| 5 | (Brief T6) Cancellation sans double résolution | **TENU** | Scenario `uikit-window-request-cancel-before-connect` **Passed** ; source l.324-397 : gate `holdActivations` → provider appelé EXACTEMENT une fois (l.336), aucune corrélation pendant la retenue (l.338-340), `cancel` → `CancelledBeforeCommit` + outcome terminal `Cancelled` (l.343-349) ; à la relâche, la résolution TARDIVE d'une demande déjà terminale est un no-op : un seul enregistrement nouveau (le requérant, `fixtureBound`), **aucun enfant** (l.367-371), l'outcome reste `Cancelled` pour toujours (l.372-374), et le renforcement Task 4 exige que la gate relâchée matérialise un événement réel (résolution OU refus d'activation explicite — pas de succès vacuent, l.357-362). Sentinelle `uikit-window-request-cancel-terminal-faithful` **Killed**. |

**Verdict global du jalon B : les cinq critères sont tenus à froid, au HEAD unique
`90550a15`, sur les deux simulateurs, avec evidence JSON/JUnit corrélées au commit.**

## 4. Issues rencontrées et résolues

1. **Flake consumer iOS** (§2.6) — premier run rouge sur le prérequis « scène de l'app
   hôte » (32/47), re-run vert 47/0 au même HEAD, sans aucune modification. Diagnostic :
   échec de lancement du test host (SpringBoard) après l'enchaînement des boots du gate ;
   la même voie d'attach passe 28/28 chez le driver 3 minutes plus tôt. Classé
   environnemental ; noté pour la CI (un retry par destination est la parade standard).
2. **Gradle `--rerun-tasks` au lieu de la vérification XML** — retenu pour un froid
   démontrable : chaque invocation exécute toutes ses tâches (17/29/36 « executed »), les
   XML régénérés portent l'horodatage du sweep (16:46-16:48).
3. **Le gate re-invoque les tests runtime simulateur** — up-to-date lors du passage gate
   (16:49) puisque déjà exécutés froid à 16:47 au même HEAD ; les XML du §2.3 font foi
   (le script ne passe pas `--rerun-tasks`, comportement inchangé des phases précédentes).

## 5. Self-review

- **Ordre imposé respecté** : fix DESIGN → commit `90550a15` → sweep. Les 12 evidence JSON
  des contrats actifs embedment le hash final — un seul HEAD propre, aucune preuve au
  hash intermédiaire.
- **Portée** : le diff total de la tâche est UNE ligne de doc (§1). Aucun code, aucun test,
  aucune config touchés. `git status` propre après le sweep.
- **Froid réel** : `--rerun-tasks` sur les 4 invocations Gradle (zéro up-to-date) ; le gate
  et les consumers régénèrent leurs artifacts et repos (publish contract re-exécuté).
- **Aucun contournement** : le flake consumer n'a été disqualifié que par re-run à HEAD
  identique (47/0), jamais par dégradation de destination ni saut de vérification ; les
  destinations iPad/Apple TV 4K OS 27 sont vérifiées par le gate avant tout travail.
- **Verdicts fondés sur des faits cités** : chaque case du tableau §3 pointe un JSON
  d'evidence horodaté au commit ET la ligne de test qui porte l'assertion décisive ; les
  sentinelles Killed prouvent que les scénarios meurent si la garantie saute.
- **Limites honnêtes** : le critère « sans effet natif » tvOS est prouvé par le compteur
  provider et l'absence de corrélation/réservation côté app (pas par l'instrumentation de
  FrontBoard) — c'est l'observabilité dont dispose le driver, identique à Task 3 ; la
  corrélation FIFO reste un invariant simulateur (charter phase 10 : matériel physique
  non couvert par ce sweep).

## 6. État final

- HEAD : `90550a15` (`docs(design): show the phase 4 windowSceneProvider in the UIKit
  attach signature`), parent `72abe375` ; arbre propre.
- Totaux du sweep : runtime JVM 408 + appkit 371+2 + runtime iOS sim 83 + runtime tvOS sim
  83 + driver iOS 28 + driver tvOS 18 + consumers iOS 47 + consumers tvOS 47 —
  **0 failure, 0 error** partout (2 skips prévus chez les consumers).
- Reste au contrôleur : push + PR (hors périmètre Task 6, brief l.5).

## 7. Fix final-review (Finding C)

- **Finding** : le test de refus tvOS
  (`testWindowRequestWithoutProviderIsRejectedUnsupportedWithNoNativeEffect`) ne
  s'assurait JAMAIS qu'aucune scène supplémentaire ne s'est connectée — la preuve
  « aucune scène activée » (roadmap l.236) restait structurelle (compteur provider,
  corrélation, réservation, état du manager) sans clore la liste de connexions.
- **Fix** : une assertion, dans le bloc « sans effet natif » (après la seconde demande
  refusée et l'état du manager inchangé) :
  `XCTAssertEqual(DriverFixture.shared.connected.count, 1, "no additional scene may
  connect on the refusal path")` — `connected` est le store des fixtures indexé par
  identité de scène, alimenté au `willConnectTo` (`DriverSceneDelegate`) ; la valeur
  attendue est 1 (la scène primaire uniquement).
- **Vérification** : `xcodebuild test -scheme KadreUikitDriverTvOs` (destination
  `platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0`) →
  `Executed 18 tests, with 0 failures (0 unexpected)` ; le test ciblé passe
  (0.047 s). Logs : `/tmp/fixc-tvos-build.log`, bundle
  `build/xcresult/fix-c-tvos.xcresult`.
- **Portée** : 1 fichier, +2 lignes — commit `723f7b80`
  (`test(uikit): assert no extra scene connects on the tvOS refusal path`), parent
  `90550a15` ; arbre propre.
