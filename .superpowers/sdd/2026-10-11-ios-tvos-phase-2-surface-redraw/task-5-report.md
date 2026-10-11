# Task 5 Report — Driver : stimulation réelle (resize, rotation, thème, reprise) et lease

**Statut : DONE_WITH_CONCERNS** (concernes mineures, toutes documentées ci-dessous — aucune
bloquante, aucun comportement runtime falsifié, aucun vert truqué). Worktree
`/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f`, branche `zcode/sess-56645e3f`.

**Commits :**
- `a272cf8d` — `fix(uikit): document the post-close lease outcome` (le KDoc façade, Task-4-Important-1)
- `b940a471` — `test(uikit): prove dynamic surface observations, redraw and the native lease on both families`

**Preuves : `** TEST SUCCEEDED **` ×2** — iOS 21 tests / 0 failures, tvOS 17 tests / 0 failures.

---

## 1. Audit du travail partiel des deux prédécesseurs (non commité)

Les deux prédécesseurs ont été tués par la fenêtre d'inactivité (~10 min) pendant des
xcodebuild longs ; ils ont laissé un travail DÉJÀ BIEN AVANCÉ et cohérent. Verdict par fichier :

| Fichier | Verdict d'audit | Action |
|---|---|---|
| `apps/Ios/Info.plist` + `project.yml` | Les 4 orientations (BCK-016) : correct. MAIS la clé `UIPreferredDefaultWindowingStyle: UIWindowSceneWindowingStyleFullscreen` (essai TN3192 du prédécesseur pour débloquer la rotation) s'est révélée INEFFICACE : ce run a capturé le refus 101 AVEC la clé dans l'Info.plist construit (vérifié dans le .app : la clé y était). Clé retirée (plist + yml), le commentaire yml documente les TROIS refus capturés (voir §4 rotation). | Gardé orientations ; clé TN3192 **retirée** |
| `apps/Shared/DriverFixture.swift` | `primarySurface` typé recapturé à chaque `run` + `waitUntilSurfaceState` (échec embarquant le dernier état) : correct, nécessaire aux preuves. | Gardé tel quel |
| `project.pbxproj` | Régénération xcodegen cohérente : `KadreUikitSurfaceTests.swift` dans les DEUX cibles de tests, `KadreUikitSurfaceIosTests.swift` dans la seule cible iOS — miroir exact des globs `project.yml`. | Gardé tel quel |
| `UIKitSurfaceObservationBinding.kt` | Seul ajout : `UIKitObservationCount.recordReread()` en tête de `reread()` (après la garde teardown) + object interne compteur. Code revu Task 3 — increment strictement diagnostique, aucune sémantique touchée. Légitime. | Gardé tel quel |
| `KadreIos.kt` | `failureReporter = UIKitAttachDiagnostics` (miroir d'`AndroidAttachDiagnostics`, `KadreAndroidAttach.kt` l.551) filé au runtime ET au port (le port enveloppe install/publications dans `runCatching().onFailure(failureReporter::report)` — vérifié l.246-249/266/301-309). C'est le follow-up Task 3 annoncé. Légitime. | Gardé tel quel |
| `UIKitSurfaceAccess.kt` | KDoc uniquement : la réponse steady post-close est `Unsupported(PlatformSurfaceAccess)` (route du registre retirée au close), `Closed(Surface)` n'étant que la course en vol. Rien de code. | Gardé, **commité séparément** (`a272cf8d`) |
| `KadreUikitSurfaceDriver.kt` (nouveau) | Sonde de conduite : `leaseProbe` (bail nominal + second bail DANS la callback du premier), `closedLeaseProbe` (verdict steady post-close), `captureEvents` (collecteur Kotlin du `Flow` d'événements, compteurs par type sous `RuntimeLock`), lecture des diagnostics (compteur de re-lectures, journal du reporter). Même posture que `KadreUikitProbe` (phase 0) : opt-in `KadrePlatformApi`, hors API contractuelle. Légitime. | Gardé tel quel |
| `tests/Shared/KadreUikitSurfaceTests.swift` + `tests/Ios/KadreUikitSurfaceIosTests.swift` (nouveaux) | Les 8 scénarios partagés + la rotation iOS : tous présents, mécanique de déclenchement conforme au ruling (re-post de `UIScene.didActivateNotification` sur la scène vivante, JAMAIS d'assertion non déclenchée). Complets — **aucun scénario manquant**. | Gardés, corrigés (§3) |

Le brief demandait « AUDIT, keep the good, fix the wrong, complete the missing » : rien n'a dû
être complété côté scénarios (les 9 noms figés étaient couverts) ; les corrections portent sur
la ROBUSTESSE de l'exécution réelle (§3) et deux défets d'infrastructure découverts au run
(§3.1 activation des scènes, §3.2 suppression de la clé TN3192).

## 2. Voie retenue pour `withUIKitView` (le brief demande de documenter la voie)

**Voie Kotlin par la sonde** (`KadreUikitSurfaceDriver.leaseProbe`/`closedLeaseProbe`,
`runBlocking` sur le thread principal — le thread de l'attach, contrat du port qui court le
block en inline sur main) : le header généré n'expose PAS l'extension suspend `withUIKitView`
à Swift (Kotlin-only par INTEROP §7 — vérifié dans `KadreUikit.h` : aucun symbole
`withUIKitView`), donc le test passe par l'application Kotlin / la sonde, comme prévu par le
brief (« si l'extension n'est pas appelable depuis Swift, le test passe par l'application
Kotlin »). Les faits remontent en objets copies (`KadreUikitLeaseProbe`,
`KadreUikitClosedLeaseProbe`) — jamais les types SDK traverser.

## 3. Corrections apportées au travail des prédécesseurs (avec causes racines capturées)

### 3.1 La course de lancement multi-scènes (apps, phase 1) — `DriverSceneRequest.swift` + `DriverSceneDelegate.swift`

**Symptôme** : avec `TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=2` (l'environnement de
lancement canonique du gate — passé par
`kadre/contracts/driver/uikit/build.gradle.kts` l.71, que mes premières invocations n'avaient
pas : les 3 tests multi-scènes échouaient « secondary scene never connected », déterministe),
un run a perdu une course : `sceneConnected()` (hook du `willConnectTo`) déclenchait la
demande #1 à +00.27s ALORS que la scène primaire n'avait pas encore été activée par le
lancement (+00.23s connect) — FrontBoard a porté foreground-active à la DERNIÈRE scène
connectée et la primaire est restée ForegroundInactive à jamais (« scene readiness not
reached » ×N, trace capturée). Le run sain de comparaison : primaire activée à +00.09s AVANT
la demande à +00.27s.

**Fix (fidèle au design documenté du fichier lui-même : « chaque demande part APRÈS
l'activation de la première scène »)** : un garde `activationSeen` posé par le délégué
(`sceneDidBecomeActive` → `DriverSceneRequest.sceneActivated()`) ; `requestNextIfNeeded` ne
part jamais avant. Le chemin NotificationCenter reste en renfort (l'activation réelle de
lancement n'y est pas toujours livrée — constaté : aucun « reçu » à +00.09s dans le run sain —
le délégué, lui, l'est toujours). Résultat : les 3 tests multi-scènes passent de façon
déterministe (10/10 attach iOS au run final).

### 3.2 Clé TN3192 `UIPreferredDefaultWindowingStyle` — retirée

Elle n'achète rien (101 capturé AVEC elle, cf. §4) et ajoute une interaction non prouvée avec
le multi-scènes. Le plist construit a été vérifié (la clé y était) avant de conclure.

### 3.3 Le test de thème — faux vert ambiant éliminé + repli documenté

L'expérience instrumentée a établi les FAITS (tous capturés dans les logs) :
1. **L'état initial du runtime porte un thème** (Dark) et le thème AMBIANT du simulateur tvOS
   varie d'un run à l'autre (Light sur certains boots, Dark sur d'autres) ;
2. Sur tvOS, l'override posé sur la FENÊTRE peut ne pas atteindre la traitCollection de la
   vue observée (windowTrait bascule, vue non — capturé : `windowTrait=2 view=style=1`,
   zéro publication, zéro drop — `droppedReports=0`) ;
3. L'ancienne versionmatcher « theme == Dark » pouvait passer sur l'état AMBIANT Dark@rev0
   sans qu'aucune re-lecture n'ait rien publié : un **faux vert** (et le compteur d'événements
   ≥2 échouait honnêtement — le prédécesseur n'a jamais eu tvOS theme vert).

**Nouvelle mécanique** (honnête dans les deux ambients) :
- SOL = la VÉRITÉ VIVANTE de la vue (`state.theme === viewTruthTheme()`) ;
- chaque transition EXIGE une publication réelle : thème visé ET `revision > base.revision` ;
- repli DOCUMENTÉ (pattern du repli rotation du brief Step 2) : après une fenêtre bornée (3s)
  sans publication par la route fenêtre, le MÊME override est posé sur la VUE (dont la
  traitCollection est gouvernée par son propre override, contrat UIKit) — tracé (NSLog +
  DriverTrace) et rapporté ; jamais un vert fabriqué ;
- Dark→Light est TOUJOURS exigé avec publication + événement (`drain` borné synchronisé sur
  le canal des événements) ; la publication retour vers Dark n'est exigée que si le sol
  n'était pas déjà Dark (dédup kernel = comportement ruled, aucun événement fabriqué) ;
- resets des DEUX overrides (fenêtre ET vue) en fin de test.

Résultats des routes au run final : iOS `route: WINDOW override` ; tvOS `route: WINDOW
override` (l'ambiant tvOS était revenu à Light ; le repli vue reste codé pour les boots Dark).

### 3.4 Synchronisations de canal événement (resize/rotation/thème)

Le STATE est publié sur le main (StateFlow) ; l'ÉVÉNEMENT voyage par la file des lanes vers
le collecteur de fond (Dispatchers.Default) — il peut atterrir quelques battements APRÈS
l'état (capturé : `metricsCount=0` lu pendant que `recentDescriptions` montrait déjà le
`MetricsChanged`). Les assertions de publication sont désormais précédées d'un `drain` borné
qui synchronise sur le canal — MÊME FORCE d'assertion, zéro course de lecture, zéro
affaiblissement.

## 4. Mécanique de déclenchement par scénario (ruling : re-lectures sur déclencheurs SDK
visibles SEULEMENT — post de `UIScene.didActivateNotification` sur la scène vivante, PUIS
assertion ; jamais de vert non déclenché)

| Scénario (nom figé) | Stimulus réel | Déclencheur | Assertions |
|---|---|---|---|
| `uikit-surface-metrics-match-view` (×2 familles) | attach réel | re-lecture initiale postée à l'attach | logical == bounds Swift (accuracy 0.001), physical == ceil(logical × scale), scale == displayScale, safeArea == insets Swift ; zéro observation abandonnée (`reportedFailureCount == 0`) |
| `uikit-surface-resize-propagates` (×2) | `view.frame = 480×200` + `layoutIfNeeded` | didActivate posté (re-posté à chaque itération bornée) | state == nouvelles bounds ; `MetricsChanged` publié par différence (drain borné) ; tue `uikit-metrics-stale-generation` |
| `uikit-surface-rotation-propagates` (iOS) | `scene.requestGeometryUpdate(.iOS(interfaceOrientations: .landscapeRight))` — orientation déclarée dans le plist ; vue passée pleine fenêtre (masks flexibles posés PAR LE TEST, retirés après) | didActivate + attente bornée de la géométrie paysage | si rotation réelle : fenêtre échangée (sentinelle `uikit-rotation-metrics-faithful`, dims publiées == fenêtre tournée, `landscapeRight`) ; sinon REPLI DOCUMENTÉ (ci-dessous) |
| `uikit-surface-theme-propagates` (×2) | `window.overrideUserInterfaceStyle` Dark puis Light (repli vue si la fenêtre ne publie pas — §3.3) | didActivate posté | Dark puis Light PUBLIÉS (révision qui avance) ; `AppearanceChanged` par transition réelle (drain borné) |
| `uikit-surface-redraw-coalesced` (×2) | rafale 5 × `requestRedraw()` puis 3 × | drain du collecteur (pumping run loop) | EXACTEMENT 1 par tick (`priming+1`, `priming+2`), fenêtre de quiescence 0.5s — tue `uikit-redraw-single-per-tick` |
| `uikit-surface-redraw-after-close` (×2) | `requestStop` + `awaitTermination` réels | — | surface TERMINALE (`attachment === detached`, Task-1-M2) ; `requestRedraw` → `Closed(Surface)` (`resource === surface`) ; quiescence : aucun événement post-close |
| `uikit-surface-apply-fields-refused` (×2) | `apply(SurfaceUpdate(cursor: Set, …))` puis tout-Unchanged | callback d'apply réelle | `PartiallyApplied` avec EXACTEMENT 1 rejeté (`cursor`), `Unsupported(UpdateSurface)` (`operation === updatesurface`) ; tout-Unchanged → `Applied` |
| `uikit-lease-boundaries` (×2) | bail 1 admis ; bail 2 tenté DANS la callback du 1 ; bail post-close | callbacks réelles du port (inline main) | vue louée `isEqual` surfaceView ; 2e bail `TemporarilyUnavailable(retryable=true)`, block jamais invoqué ; post-close `Unsupported(PlatformSurfaceAccess)` déterministe (tue `uikit-lease-closed-after-terminal`) |
| `uikit-surface-reprise-rereads` (×2) | `didEnterBackgroundNotification` réel puis `willEnterForeground` + `didActivate` | notifications réelles (patron phase 1) | lifecycle Kadre suit (background/inactive → foreground/active, `===` snapshots) ; le compteur de re-lectures S'INCRÉMENTE (chemin de re-lecture prouvé) ; state cohérent avec les bounds vivantes |

**Rotation — repli documenté (le brief l'exige, JAMAIS de faux vert)** : la rotation
programmatique est refusée par ce runtime simulateur quel que soit le levier — TROIS refus
capturés, un par configuration : (1) windowing libre par défaut → `UISceneErrorDomain 101`
« The current windowing mode does not allow for programmatic changes to interface
orientation » (captures prédécesseur ET ce run) ; (2) avec la clé TN3192
`UIPreferredDefaultWindowingStyle=UIWindowSceneWindowingStyleFullscreen` dans l'Info.plist
construit (vérifié) → même 101 ; (3) avec `UIRequiresFullScreen=true` →
`BSActionErrorDomain « response-not-possible »` (clé essayée puis retirée). Le stimulus
retenu est le resize à changement d'aspect documenté au brief (240×320 ; ici : vue pleine
fenêtre puis resize), tracé `KADRE-DRIVER … DEGRADED` (NSLog + DriverTrace) à chaque run, et
les métriques publiées sont assertées == la géométrie VIVANTE post-stimulus (`wantWidth ≠
beforeWidth`). Le refus exact est embarqué dans la trace du run.

**tvOS mode-change (`UIScreenModeDidChangeNotification`)** : observation câblée en Task 3,
aucun stimulus programmatique sur simulateur — manual charter (capabilities/uikit.md), aucun
test factice écrit, conformément au brief Step 6.

## 5. Formes de header utilisées (lues dans `KadreUikit.h` iosSimulatorArm64 fraîchement lié)

- `KadreUikitSurfaceDriver.shared` (object → `.shared`) ; `captureEvents(surface:)`,
  `leaseProbe(surface:expectedView:)`, `closedLeaseProbe(surface:)`,
  `reportedFailureCount`, `recentFailureDescriptions`, `observationCount` ;
- `surface.apply(update:completionHandler:)`, `surface.requestRedraw()`,
  `surface.state.value`, `surface.events` (Flow) ;
- `SurfaceUpdate(init(cursor:pointerCapture:hitTesting:inputDefaultBehavior:expectedRevision:))`,
  `PropertyChangeSet(value:)`, `PropertyChangeUnchanged()`, `CursorStyleSystem(icon:)`,
  `CursorIcon.default_` ;
- `SurfaceUpdateOutcomePartiallyApplied.rejected: [RejectedSurfaceField]` (`.field`,
  `.failure`), `SurfaceUpdateOutcomeApplied` ;
- `KadreFailureClosed.resource === KadreResourceKind.surface`,
  `KadreFailureUnsupported.operation === KadreOperation.updatesurface` ;
- enums : `SurfaceAttachmentState.detached`, `SurfaceTheme.light/dark/unknown`,
  `SurfaceProperty.cursor` ; `state.revision` exporte en `Int64`.

## 6. XCTest — sommes citées (runs finaux, sources exactement commitées)

iOS (`iPad Pro 11-inch (M5), OS=27.0`, `TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=2`) :

```
Test Suite 'KadreUikitSurfaceTests' passed — Executed 9 tests, with 0 failures (0 unexpected) in 6.095 (6.102) seconds
Test Suite 'KadreUikitAttachTests' passed — Executed 10 tests, with 0 failures (0 unexpected) in 10.856 (10.862) seconds
Test Suite 'KadreUikitDriverTests' passed — Executed 2 tests, with 0 failures (0 unexpected) in 0.002 (0.003) seconds
Test Suite 'All tests' passed — Executed 21 tests, with 0 failures (0 unexpected) in 16.953 (16.973) seconds
** TEST SUCCEEDED **
```

tvOS (`Apple TV 4K (3rd generation), OS=27.0`) :

```
Test Suite 'KadreUikitSurfaceTests' passed — Executed 8 tests, with 0 failures (0 unexpected) in 7.971 (7.977) seconds
Test Suite 'KadreUikitAttachTests' passed — Executed 7 tests, with 0 failures (0 unexpected) in 0.606 (0.611) seconds
Test Suite 'KadreUikitDriverTests' passed — Executed 2 tests, with 0 failures (0 unexpected) in 0.003 (0.004) seconds
Test Suite 'All tests' passed — Executed 17 tests, with 0 failures (0 unexpected) in 8.579 (8.593) seconds
** TEST SUCCEEDED **
```

## 7. Carte des mutations (chaque test tue sa sentinelle)

| Sentinelle | Test qui la tue | Mécanique de mise à mort |
|---|---|---|
| `uikit-metrics-stale-generation` | `testSurfaceResizePropagatesMetrics` | des métriques figées à la génération d'attach ne porteraient jamais 480×200 après le resize réel |
| `uikit-redraw-single-per-tick` | `testSurfaceRedrawBurstCoalescesToOnePerTick` | un drain non coalescé émettrait 5 (puis 3) au lieu de 1+1 — comptes EXACTS + quiescence |
| `uikit-lease-closed-after-terminal` | `testLeaseBoundariesHonoursFlightAndClose` | le verdict steady post-close est épinglé `Unsupported(PlatformSurfaceAccess)` ; un port qui répondrait autrement (ex. `TemporarilyUnavailable` menteur, ou `Closed` non ruled) échoue l'assert |
| `uikit-rotation-metrics-faithful` | `testSurfaceRotationPropagatesMetrics` (branche rotation réelle) | les dims publiées doivent être la géométrie fidèle de la fenêtre tournée (échange exact avec le portrait de référence) ; en repli dégradé, la fidélité est assertée sur la géométrie vivante du resize |

## 8. Toutes les commandes et leur issue (discipline arrière-plan respectée : aucun
commandement de premier plan > 8 min, chaque xcodebuild en `run_in_background` + polling
`sleep`/`tail`)

| Commande | Issue |
|---|---|
| `./gradlew :kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64` | BUILD SUCCESSFUL (exit 0) |
| `./gradlew :kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64` | BUILD SUCCESSFUL (exit 0) |
| `xcodebuild … KadreUikitDriverIos … test` (1re, sans env TEST_RUNNER, clé TN3192 présente) | **FAILED** — 6 failures : 3 multi-scènes (« secondary scene never connected » — env var manquante) ; les 9 scénarios surface VERDS (rotation en repli dégradé tracé) |
| `xcodebuild … -only-testing …AttachTests` (sans la clé TN3192) | **FAILED** — mêmes 3 multi-scènes ⇒ la clé TN3192 est hors de cause ; cause racine : env var manquante |
| `simctl shutdown + erase + boot` (iPad) puis rerun des 3 tests | **FAILED** idem ⇒ pas un état de simulateur ; la trace révèle `KADRE_DRIVER_REQUEST_SECOND_SCENE=<unset>` |
| `xcodebuild … test` AVEC `TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=2` | **FAILED** — 3 nouveaux échecs surface (resize: course de canal événement ; theme: propagation des traits) ; multi-scènes ✅ |
| idem après `drain` de synchronisation + `layoutIfNeeded` | **FAILED** — course de lancement multi-scènes (demandes parties avant l'activation de la primaire ; FrontBoard active la dernière scène) |
| `DriverSceneRequest` garde d'activation + rerun | **TEST SUCCEEDED** — 21 tests, 0 failures |
| expérience `UIRequiresFullScreen=true` (rotation seule) | refus `BSActionErrorDomain « response-not-possible »` — clé retirée, repli dégradé maintenu |
| `xcodebuild … KadreUikitDriverTvOs … test` (1re) | **FAILED** — 1 failure (theme : compte 0 vs ≥2, événements en retard de canal) |
| idem après drain theme | **FAILED** — drain 5s non atteint ⇒ diagnostic enrichi : `alive=true, droppedReports=0` puis dump `liveState` : révision figée, thème ambiant ⇒ faits §3.3 |
| thème réécrit (§3.3) + série de runs theme-only | **PASSED** (0.066s, route WINDOW) |
| `xcodebuild … KadreUikitDriverTvOs … test` (finale) | **TEST SUCCEEDED** — 17 tests, 0 failures |
| `xcodebuild … KadreUikitDriverIos … test` (finale, sources commitées) | **TEST SUCCEEDED** — 21 tests, 0 failures |

## 9. Self-review

- **F1 (corrigé en vol)** : ma première ré-insertion du drain rotation a écrasé les lignes
  `portraitWindowWidth/Height` (référencées plus bas) — détecté à la relecture immédiate,
  réparé avant tout run.
- **F2 (corrigé en vol)** : un splice python a dupliqué la queue du fichier de tests —
  détecté au grep de structure, réparé (un seul exemplaire de chaque test, vérifié).
- **F3 (faux vert éliminé)** : l'ancien test de thème pouvait passer sur l'état AMBIANT
  (Dark@rev0 sur tvOS) sans publication — la nouvelle mécanique exige thème visé + révision
  qui avance + événement drainé. Le prédécesseur n'avait jamais eu tvOS theme vert : la suite
  complète « les deux familles vertes » est atteinte pour la première fois ici.
- **F4 (repli rotation)** : trois refus capturés (101 ×2, BSActionErrorDomain ×1) — le repli
  aspect-swap du brief est appliqué, tracé à chaque run, et n'a JAMAIS été présenté comme une
  rotation réelle. La branche `rotatedReal` (fidèle) reste codée et s'exécutera si un runtime
  futur accepte `requestGeometryUpdate`.
- **F5 (concerne mineure, ouverte)** : le thème AMBIANT du simulateur tvOS varie selon les
  boots (constaté Light puis Dark puis Light) — le test s'adapte honnêtement aux deux et
  trace la route ; à documenter dans le charter si un run CI tombe sur l'ambiant Dark (le
  message « Dark already ambient » au log est NORMAL, pas un skip).
- **F6 (concerne mineure)** : la course de lancement multi-scènes était latente dans
  l'infrastructure de phase 1 (`sceneConnected` pouvait déclencher la première demande avant
  l'activation) — le fix est minimal et conforme au design documenté du fichier, mais il
  touche du code phase 1 : à relire par le contrôleur.
- **F7 (aucun affaiblissement)** : les assertions de comptes d'événements restent ≥1 par
  transition réelle (synchronisées par drain borné, pas supprimées) ; les assertions d'état
  exigent désormais PLUS (révision qui avance) qu'avant.
- **F8** : `xcodebuild` refuse un `-resultBundlePath` existant — purge avant chaque run
  (miroir du build.gradle.kts canonique).

## 10. Noms figés livrés à Task 6 (inchangés)

`uikit-surface-metrics-match-view`, `uikit-surface-resize-propagates`,
`uikit-surface-theme-propagates`, `uikit-surface-redraw-coalesced`,
`uikit-surface-redraw-after-close`, `uikit-surface-apply-fields-refused`,
`uikit-lease-boundaries`, `uikit-surface-reprise-rereads` (partagés ×2 familles) +
`uikit-surface-rotation-propagates` (extension iOS).

---

## Fix round 1 (append) — correction de la revendication d'export `withUIKitView`

### Correction de §2 (note ajoutée — le §2 original ci-dessus est laissé tel quel pour la trace)

La revendication « vérifié dans `KadreUikit.h` : aucun symbole `withUIKitView` » était
**FAUSSE**. Vérification faite (header fraîchement lié, religné depuis les sources commitées) :
`KadreUikit.h` l.7038 déclare `@interface KadreUikitUIKitSurfaceAccessKt` et l.7075 exporte
`+ (void)withUIKitView:(id<KadreUikitHostSurface>)receiver block:(...) completionHandler:(...)`
avec `swift_name("withUIKitView(_:block:completionHandler:)")` — appelable depuis Swift comme
méthode statique. L'erreur vient de ma vérification initiale : je n'avais listé que les
membres du protocole `KadreUikitHostSurface` et les symboles `apply`/`requestRedraw`, pas les
classes fichier des extensions (`*Kt`). La route par la sonde Kotlin RESTE légitime et reste
en place (elle appelle le vrai `withUIKitView` de production, et l'exigence de bail concurrent
DANS la callback du premier bail est matériellement plus propre en Kotlin : closures +
vérification single-flight), mais la justification documentée était fausse et est corrigée.

Corrigé :
1. KDoc du test `uikit-lease-boundaries`
   (`tests/Shared/KadreUikitSurfaceTests.swift`) — la fausse revendication remplacée par :
   « `withUIKitView` EST exporté vers Swift (KadreUikit.h, `KadreUikitUIKitSurfaceAccessKt`) ;
   la preuve passe par la sonde Kotlin car l'exigence de bail concurrent DANS le premier
   callback est matériellement plus propre en Kotlin (closures + vérification single-flight),
   pas parce que l'API est inexportable. »
2. Le §2 du présent rapport n'est PAS édité silencieusement — cette note en est la correction.

### Re-vérification

```
xcodebuild -project KadreUikitDriver.xcodeproj -scheme KadreUikitDriverIos \
  -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0' \
  -only-testing:KadreUikitDriverIosTests/KadreUikitSurfaceTests/testLeaseBoundariesHonoursFlightAndClose \
  TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=2 CODE_SIGNING_ALLOWED=NO test
Test Case '-[KadreUikitDriverIosTests.KadreUikitSurfaceTests testLeaseBoundariesHonoursFlightAndClose]' passed (0.043 seconds).
Executed 1 test, with 0 failures (0 unexpected) in 0.043 (0.045) seconds
** TEST SUCCEEDED **
```

Changement documentaire uniquement (aucune ligne de code de test ni de runtime modifiée) — le
passage vert était attendu et obtenu.

### Acknowledgment Important-2 (mapping Task 6, aucune action)

Le kill de la sentinelle `uikit-rotation-metrics-faithful` est CONDITIONNEL : sur ce runtime,
seule la branche dégradée s'exécute (trois refus `requestGeometryUpdate` capturés, §4), donc
le kill est porté par la fidélité géométrique de la branche dégradée (publié == vivant après
le swap d'aspect), jamais par une rotation réelle. La branche réelle (fidèle, échange exact
avec le portrait de référence) reste codée et s'exécutera si un runtime futur accepte
`requestGeometryUpdate`. Acknowledgé pour la carte Task 6.
