# Task 6 report — Cold sweep + phase 5 exit checklist

Status: **DONE** (sweep fully green at the final HEAD; no defect found — **no commit produced**; the mandated fix was resolved to NONE, see §5)
Worktree: `/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f` — branch `zcode/sess-56645e3f`, HEAD at sweep time `be309914` (`docs: close the phase 5 UIKit input perimeter`), working tree clean before and after the sweep (report file itself is gitignored).
Commits: **none** (nothing to fix; see Self-review).

## 1. Cold sweep — execution and results

All runs at HEAD `be309914`, 2026-10-12 evening (22:44–22:53 CEST). Every command backgrounded/polled per execution discipline; no command exceeded the 8-minute wall.

### 1.1 Gradle suites — forced COLD re-run

The first invocation (22:40) reported BUILD SUCCESSFUL but with the three runtime suites UP-TO-DATE (results stamped 16:46–16:48, Task 5's sweep of the identical tree). To honor « sweep à FROID », a second invocation chained the `clean*Test` tasks before each suite — all three then genuinely executed (results stamped 22:47):

| Suite | Command | Result | Freshness |
|---|---|---|---|
| runtime JVM | `:kadre:runtime:cleanJvmTest` + `:kadre:runtime:jvmTest` | **24 suites, 408 tests, 0 failures, 0 errors** | 22:47 |
| runtime iOS sim | `:kadre:runtime:cleanIosSimulatorArm64Test` + `:kadre:runtime:iosSimulatorArm64Test` | **13 suites, 83 tests, 0 failures, 0 errors** | 22:47 |
| runtime tvOS sim | `:kadre:runtime:cleanTvosSimulatorArm64Test` + `:kadre:runtime:tvosSimulatorArm64Test` | **13 suites, 83 tests, 0 failures, 0 errors** | 22:47 |
| AppKit natives | `:kadre:backend:appkit:appKitNativeTests` (= `jvmTest` + `appKitStandaloneLoopTest`) | **25 suites, 371 tests, 0 f/0 e** + standalone loop **2 tests, 0 f/0 e** | 22:45–22:46 (executed, not up-to-date) |

`BUILD SUCCESSFUL`, exit 0 on both invocations.

### 1.2 UIKit contract gate — `./scripts/test-uikit-simulator.sh all`

`uikit gate (all): SUCCESS`, exit 0. Evidence regenerated AT THE FINAL HEAD — every JSON carries `"commit": "be30991440f49197c0d61ae0cfb37bf75039e98f"`:

- iOS (`iosSimulatorArm64/contract-evidence/`): BCK-012, 013, 014, 015, 016, 020, 021, 022, 023, WIN-009 — all `OK`. BCK-022 JSON: 3 scenarios **Passed** (`uikit-input-pointer-source-per-family`, `uikit-input-touch-recognizer-additive`, `uikit-input-touch-recognizer-revoked`), 2 sentinels **Killed** (`uikit-input-announcement-revoked-at-close`, `uikit-input-touch-additive-policy`), tests **32/0 skipped/0 failures/0 errors**.
- tvOS (`tvosSimulatorArm64/contract-evidence/`): BCK-012, 013, 015, 020, 022, 023, WIN-009 — all `OK` (014/016/021 are iOS-only rows in `evidence.tsv`). BCK-023 JSON: scenario **Passed** (`uikit-input-gesture-keyboard-sources-announced`), sentinels **Killed** (`uikit-input-gesture-set-exact`, `uikit-input-keyboard-binding-live`), tests **22/0/0/0**.

### 1.3 Consumers ×2 (`kadre/consumers/swift`)

- Prerequisite: `./gradlew :kadre:publishContractArtifacts :kadre:platform:uikit:publishAllPublicationsToContractTestRepository` → **PUBLISH_EXIT=0** (contract repository re-published at HEAD `be309914`).
- `cd kadre/consumers/swift && ../../../gradlew -PkadreRepository=<racine>/build/kadre-contract-repository -PkadreVersion=1.0.0 check` → **CONSUMER_EXIT=0** (2m38s):
  - **iOS** (`iosSimulatorTests`, iPad Pro 11-inch M5): `** TEST SUCCEEDED **` at 22:53 — **47 tests, 1 skipped, 0 failures**; grep `is implemented in both` → **0 hits** (anti-duplication proof intact with the two static frameworks).
  - **tvOS** (`tvosSimulatorTests`, Apple TV 4K 3rd gen): `** TEST SUCCEEDED **` at 22:53 — **47 tests, 1 skipped, 0 failures**; duplication grep → **0 hits**.

## 2. Phase 5 exit checklist (IOS-IMPLEMENTATION-ROADMAP.md, critère de sortie phase 5, l.268-269)

« **Critère de sortie :** aucune touche, bouton ou contact ne reste actif après reset/fermeture ; snapshots publiés avant événements ; les capacités annoncées correspondent aux sources installées et à leurs limites documentées. Sur tvOS, le consumer se parcourt à la télécommande sans double action, faux touch ou prise de contrôle du moteur de focus. »

### 2.1 « Aucune touche, bouton ou contact ne reste actif après reset/fermeture » — **TENU**

- **Contract (les deux familles)** : `uikit-input-touch-recognizer-revoked` **Passed** + sentinelle `uikit-input-announcement-revoked-at-close` **Killed** (evidence JSON à `be309914`). Source `kadre/contracts/driver/uikit/tests/Shared/KadreUikitInputTests.swift:269` `testTouchRecognizerRemovedAndAnnouncementRevokedAtClose` : après `stopAndAwaitTermination` — le recognizer Kadre est GONE de la vue (`probe.recognizerAttached == false`), la vue du host retrouve EXACTEMENT ses recognizers d'origine (`totalGestureRecognizers == baseline - kadreRecognizerCount`, « nothing residual »), les compteurs de teardown touch/clavier/gestes/survol ont bougé, l'annonce de révocation all-false a été publiée, et les compteurs d'événements/stimuli sont à **zéro résiduel** (`touchEventCount == 0`, `touchStimulusCount == 0`, `keyboardEventCount == 0`, `gestureStimulusCount == 0` — « never fabricated »).
- **Port (ordre de teardown)** : `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitWindowCommandPort.kt:446-473` `teardownSurfaceObservation()` — retrait des cinq branchements sous verrou puis teardown séquencé touch → clavier → gestes/survol ; idempotent et convergent depuis `requestOpenedClose`/`beginClose`/`finishClose` (l.525, l.697, l.703-713 avec défense en profondeur si un chemin oubliait `beginClose`) ; KDoc l.439-444 : « zéro observer résiduel … la DERNIÈRE annonce de révocation est le all-false + vide ».
- **Identité de contact (table de jetons)** : `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitTouchTokenTableTest.kt` — `endedDeliversSameTokenThenRevokesIt` / `cancelledDeliversSameTokenThenRevokesIt` (l'événement terminal porte encore le jeton PUIS l'entrée disparaît : plus aucun `Moved`/`Ended`/`Cancelled` ne résout), `aNewContactAfterRevocationGetsAFreshTokenNeverTheRevokedValue`, `clearEmptiesEverything` (le teardown vide tout).
- **Agrégat d'annonce** : `UIKitInputAnnouncementTest.teardownWritesEndInTheAllFalseRevocation` — le cycle de teardown se termine EXACTEMENT sur `!touch && !keyboard && !pointer && gestureKinds.isEmpty()`, chaque retrait intermédiaire publiant le snapshot VRAI de son instant.
- **Côté reset (runtime)** : `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowSurfaceTest.kt:1370` `focusLossPublishesOneNeutralSnapshotAndResetWithoutSyntheticKeyRelease` — le reset publie un `StateReset(FocusLost)` unique, aucune release synthétique, et l'état post-reset est neutre (`pressedKeys` vide, `pointers` vide, modifiers vides). La branche touch du runtime retire l'identité à l'événement terminal et place l'identité en « retiring » (`RuntimeSurfaceInput.kt:688-734` `reduceTouchLocked`).

### 2.2 « Snapshots publiés avant événements » — **TENU**

- **Producteur (structure main-queue)** : `UIKitTouchBinding.kt:337-345` (KDoc) — toute la vie du binding vit sur la file principale (l'appelant d'`install` EST sur le main, les handlers `UITouch` aussi) : AUCUN stimulus ne peut être produit entre la création du recognizer (inerte tant qu'UIKit n'a pas livré un tour de run loop) et l'annonce qui suit DANS LA MÊME EXÉCUTION. Le replay-before-announce du web n'a pas d'équivalent nécessaire — « l'absence est la vérité ». Ordres effectifs : touch — pose sur la vue puis `publish(announcement.touch(true))` (`install`, l.364-389) ; gestes/survol — pose puis `publish(announcement.gestures(...))` (`UIKitGestureBindings.kt:163-169`) ; clavier — observers posés, puis announcement UNIQUEMENT si une source réelle s'attache (`UIKitKeyboardBinding.kt:106-110`, `attachToConnectedKeyboard()`).
- **Runtime (stamping)** : `kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeSurfaceInput.kt:318-332` — `InputObservationChanged` est appliqué SYNCHRONEment sous verrou (`updateObservationCapabilitiesLocked`, l.745-781 : capabilities réécrites en absolu + `revision.next()`), tandis que toute publication d'événement porte `stateRevision = currentState.revision` (`reduceTouchLocked`, l.711) : un événement ne peut jamais référencer des capabilities plus récentes que le snapshot annoncé à son instant d'acceptation.
- **Preuve** : `RuntimeWindowSurfaceTest.inputCapabilitiesBecomeAvailableOnlyAfterTheirNativeObservationIsStructurallyInstalled` (l.1453) — les capabilities basculent EXACTEMENT avec les annonces (révisions 1 → 2 → 3, all-unsupported → clavier seul → les quatre faits → all-false), idempotence vérifiée (une annonce identique est rejetée).

### 2.3 « Les capacités annoncées correspondent aux sources installées » — **TENU**

- **Un agrégat, quatre faits** : `UIKitInputAnnouncement` — chaque binding écrit SON fait et publie le snapshot COMPLET (une annonce touch-only ferait croire au runtime que les gestes n'existent pas) ; `gestures` REMPLACE l'ensemble, jamais n'unit. Prouvé sans appareil : `UIKitInputAnnouncementTest` (`touchWriteCarriesTheOtherFactsIntact`, `snapshotAlwaysEchoesTheCurrentAggregate`, `gesturesReplaceTheWholeSetNeverUnions`, `teardownWritesEndInTheAllFalseRevocation`).
- **`gestureKinds` == recognizers posés** : `UIKitGestureBindings.kt:37-39` « La liste rendue EST la vérité de ce qui sera installé — l'annonce `gestureKinds` la porte telle quelle (la règle de l'ensemble exact) » ; l'install publie `announcement.gestures(gestures.map { it.second }.toSet())` (l.169), le teardown `gestures(emptySet())` (l.200).
- **Ensemble EXACT par famille** : `KadreUikitInputTests.swift:139-146` — `expectedRecognizerClasses` : iOS = tap/pan/pinch/rotation + hover + `UIKitTouchRecognizer` ; tvOS = tap/pan + touch SEULS. Assertion `XCTAssertEqual(Set(probe.recognizerClassNames), Self.expectedRecognizerClasses)` (l.213) — scénario `uikit-input-gesture-keyboard-sources-announced` **Passed**, sentinelle `uikit-input-gesture-set-exact` **Killed** sur les deux familles.
- **Hover tvOS absent (sans faux écrit)** : la fabrique tvOS répond `null` (`UIKitGestureBindings.kt:52-63` — `API_UNAVAILABLE(tvos)`, le klib tvOS ne porte pas la classe) → `pointerInstalled` reste à faux ; le test contractuel l'épingle par famille : `hoverInstallCount > avant == expectedRecognizerClasses.contains("UIHoverGestureRecognizer")` (l.220-222), miroir au teardown (l.298-299). tvOS klib : pinch/rotation/hover absents = frontière de plateforme Apple (`API_UNAVAILABLE(tvos)`), PAS un trou de mapping K/N (KDoc Task 5).
- **Clavier honnête** : `keyboard(true)` n'est publié QUE lorsqu'une source réelle s'attache (scan initial trouvé ou notification de connexion — `UIKitKeyboardBinding.kt:106-110` et `onConnect` l.145-150) ; sur suite automatisée zéro connexion → pas de capability clavier faussement annoncée, et le test documente le cas légitime du simulateur avec clavier physique appairé (l'attach à un VRAI `GCKeyboard` coalescé « is a true fact, not a fabrication », l.231-236). Sentinelle `uikit-input-keyboard-binding-live` **Killed**.

### 2.4 « … et à leurs limites documentées » — **TENU**

Les quatre limites demandées sont écrites, au présente-de-livré, dans les trois registres :

| Limite | capabilities/uikit.md | DESIGN §15.2 (l.1907) | BACKEND-CAPABILITIES §6.2 (« Livré en phase 5 ») |
|---|---|---|---|
| Text input hors périmètre (phase 6) | :154 « IME (texte = phase 6) » ; :155 « le texte est la route UIKeyInput, phase 6 » | « le text input reste hors périmètre (phase 6) » | « Le text input (route UIKeyInput) reste hors périmètre (phase 6) » |
| Hover tvOS absent | :154 « le survol est iOS seul (`API_UNAVAILABLE(tvos)` … tvOS reste `pointerInstalled == false` sans faux écrit) » | « hover (iOS seul) » | « Le hover est iOS seul … tvOS reste `pointerInstalled == false`, sans faux écrit » |
| Clavier GC : `LogicalKey` `Unidentified`, modifiers vides, clavier intégré iPad non couvert, `addKeyCommand` hors v1 | :155 « `LogicalKey` toujours `Unidentified(null)` et modifiers toujours vides … le clavier intégré iPad (non-GameController) : couverture non prouvée » | « `LogicalKey` reste `Unidentified` et les modifiers restent vides (limites de GameController …) ; le clavier intégré iPad … n'a pas de couverture prouvée » | « `addKeyCommand` (shortcuts, first-responder) reste hors périmètre v1, `LogicalKey` reste `Unidentified` …, et le clavier intégré iPad … n'a pas de couverture prouvée » |
| Télécommande tvOS : charter manuel du bout réel | :155 « la télécommande (bout réel) : charter manuel (XCUIRemote = UI-testing) » | « télécommande (charter manuel pour le bout réel — `XCUIRemote` est de l'UI-testing) » | « La télécommande tvOS n'a pas de binding livré : la livraison du bout réel reste au charter manuel (`XCUIRemote` est de l'UI-testing) » |
| Perte des gestes `Cancelled`/`Failed` (politique livrée) | :155 « PERDUS par politique explicite (aucun état de geste retenu — DESIGN §15.2 le documente) » | « un geste `Cancelled`/`Failed` n'est jamais publié … la notification terminale est explicitement perdue » | « … n'est jamais publié : … explicitement perdue (politique livrée, pas un défaut) » |

Vérification grep : plus aucune occurrence résiduelle des fausses claims (« trou de mapping », « présents dans les headers ») hors les négations corrigées Task 5 (seul hit : la négation `UIKitGestureBindings.kt:32`). Aucun TODO/FIXME dans les fichiers phase 5.

### 2.5 tvOS : « le consumer se parcourt à la télécommande sans double action, faux touch ou prise de contrôle du moteur de focus » — **TENU (structurellement ; le bout réel reste charter, comme tracé)**

- **Aucun binding télécommande livré en phase 5** — décision tracée du plan (T4 : « real-touch = charter » ; XCUIRemote est de l'UI-testing, hors bundle unit, cf. roadmap l.264) et documentée (§2.4). Le parcours au bout réel reste le charter manuel enregistré.
- **Non-prise de contrôle du moteur de focus (structurel)** : la route clavier livrée est la closure `GCKeyboard` notification-driven — AUCUN `addKeyCommand`, AUCUN override `pressesBegan` (KDoc `UIKitKeyboardBinding.kt:20` « additive, notification-driven, SANS responder chain ni `pressesBegan` ») : la chaîne de first responder du host reste intacte, le focus reste au moteur UIKit/SwiftUI. Hover : absent sur tvOS (§2.3) — aucune source pointeur fabriquée.
- **Pas de faux touch** : le recognizer touch et les recognizers de geste sont ADDITIFS (`cancelsTouchesInView == false` partout, prouvé par famille — scénario `uikit-input-touch-recognizer-additive` et assertion `probe.cancelsTouchesInViewAll`, l.215) : le host garde ses touches, Kadre ne fait que lire ; le pan porte la translation telle quelle comme `Gesture(Pan)` — jamais converti en contact tactile direct ni en position d'écran fictive (roadmap l.261). Pas de double action : Kadre ne délivre la pression clavier que par la closure GC (une seule route), et le touch/geste ne consomme rien du host.
- **Le consumer lui-même n'est pas touché par la phase 5** (`git diff 5251cb8b..be309914` : zéro fichier sous `consumers/`) — sa navigabilité tvOS est celle prouvée par le gate consumer ci-dessus (47/47 verts sur Apple TV 4K, lancement et attach OK sur le bundle statique deux-frameworks).

## 3. Sanctioned doc fix — verdict : AUCUN

Conformément au mandat : vérification du drift de `KadreIos.attach` au DESIGN §15.2.

- `git diff 5251cb8b..be309914 -- KadreIos.kt` → **vide** : la phase 5 n'a ajouté AUCUN paramètre public à `attach`.
- Le bloc signature §15.2 (`kadre/DESIGN.md:1885-1893`) porte EXACTEMENT la vérité : `attach(windowScene, window, surfaceView, applicationFactory, policy = …, windowSceneProvider = null)` — le paramètre phase 4 y figure.
- Le bulletin de progression §15.2 (l.1907) reflète la livraison input (Task 5) : touch additif + identité bornée, hover iOS seul, gestes par famille avec `API_UNAVAILABLE(tvos)`, closures `GCKeyboard`, charter télécommande, politique de perte des gestes, limites GC et phase 6. Rien à corriger.

**Conclusion : aucun commit de fix — la branche reste à `be309914`, l'arbre de travail propre.**

## 4. Observation hors périmètre (flag, non corrigé)

`kadre/DESIGN.md:1904` — le bulletin « Bridge Swift minimal `KadreIos.attach(windowScene, window, surfaceView, applicationFactory, policy)` » (phrase de synthèse datée phase 0/1) n'énumère pas le paramètre optionnel `windowSceneProvider` ajouté en phase 4. Ce n'est PAS un drift de la signature imprimée (le bloc normatif dix lignes au-dessus est exact, cf. §3), ni un défaut de la livraison phase 5 : présent tel quel à travers le sweep et les revues de phase 4, il est hors du périmètre de ce sweep (même posture que le flag `UIKitWindowCommandPort.kt:416` du rapport Task 5). Recommandation : qu'une future tâche docs either énumère le paramètre ou reformule la ligne comme synthèse minimale assumée.

## 5. Self-review

- **Froid réel** : le premier passage Gradle (22:40) a laissé les trois suites runtime UP-TO-DATE (résultats 16:46-16:48 du même arbre) — détecté aux timestamps, corrigé par chaînage `clean*Test` → les trois suites ont RÉEXÉCUTÉ (22:47). AppKit et le gate uikit avaient déjà réellement exécuté. L'evidence contractuelle est régénérée au HEAD final (`"commit": "be309914…"` dans les JSON).
- **Chaque critère porte une preuve citée** (fichier:ligne + statut Passed/Killed dans l'evidence fraîche), pas une affirmation.
- **Aucun fichier produit hors du rapport** (gitignored) ; aucune édition de code/docs — le verdict « aucun défaut » repose sur les §2-3, pas sur une décision d'éviter le travail.
- Le sweep n'a trouvé AUCUN défaut de la livraison phase 5 : toutes les suites vertes à froid, les quatre critères de sortie tenus avec preuves, la clause tvOS tenue structurellement avec le charter documenté, et aucun drift DESIGN à corriger.
- Limites du sweep (inhérentes, déjà tracées) : la livraison réelle d'un touch/touche/geste/bout télécommande n'est pas prouvable in-process (compteurs à zéro PROUVÉS dans les tests, charter manuel enregistré) ; les slices device arm64 ne sont pas exercées (simulateurs seuls).

## 6. Fix round (revue finale — post-sweep, 2026-10-12)

Application des findings de la revue finale sur la branche `zcode/sess-56645e3f` (base `be309914`). Trois commits, arbre propre, aucune édition hors périmètre.

**F-2 ruling formel (ledger)** : Siri Remote binding = **follow-up phase explicite** — enregistré dans les docs (F2 ci-dessous) et ICI au ledger du fix : le binding `GCMicroGamepad` (`valueChangedHandler`/`controllerPausedHandler`) mentionné par le plan n'a jamais été implémenté et n'avait jamais été descopé ; il est désormais formellement suivi en phase suivante, la preuve du bout réel restant au charter manuel (`XCUIRemote` = UI-testing).

### Commit 1 — `3d83be90` fix(uikit): document the gckeyboard single-consumer limit (F1)

Limite mono-consumateur du handler `keyChangedHandler` (process-global : une seconde session attachée prend la livraison des touches, le teardown de la première la coupe pour la suivante ; les annonces peuvent alors sur-déclarer `keyboardInstalled` ; arbitrage process-wide = follow-up) documentée en 4 endroits, sans changement de code :

- `UIKitKeyboardBinding.kt` — paragraphe « LIMITE MONO-CONSUMATEUR » au KDoc de classe (entre la section honnêteté payload et ANNONCE) ;
- `DESIGN.md` §15.2 — progression phase 5 (après la limite iPad integrated-keyboard) ;
- `BACKEND-CAPABILITIES.md` §6.2 — paragraphe « Livré en phase 5 » ;
- `capabilities/uikit.md` — ligne BCK-023, cellule « état absent ».

### Commit 2 — `88687dfb` fix(uikit): record the siri remote follow-up and close the phase 5 input docs (F2 + F5)

- **F2** : `BACKEND-CAPABILITIES.md` §6.2 — le binding micro-gamepad que mentionnait le plan n'est pas livré en phase 5, suivi enregistré pour la phase suivante, preuve du bout réel au charter manuel ; `capabilities/uikit.md` BCK-023 état-absent — item explicite « binding micro-gamepad (Siri Remote — `valueChangedHandler`/`controllerPausedHandler`) : non livré (suivi phase suivante) ».
- **F5a** : §6.2 — le binding `GCKeyboard` est livré sur tvOS aussi : le « pas de notifications clavier (klib = 0) » du plan visait les notifications UIKit du cadre clavier (keyboard-frame), la route GameController existant dans les klibs des deux familles (écart au texte du plan, vérifié contre les klibs).
- **F5b** : §6.2 — l'observation des transitions `UIFocusEnvironment` (roadmap IOS l.259) est hors périmètre v1 (décision du plan, non livrée) ; le focus de la surface reste piloté par les transitions de scène.

### Commit 3 — `8c114286` test(uikit): pin the native token length bound and the gesture state mapping (F3 + F4)

- **F3 (triage 1)** : `UIKitInputMappingTest.nativeTokenLengthBoundIsTwoHundredFiftySixCodeUnits` — sémantique vérifiée d'abord à la lecture (`uikitNativeToken`, `UIKitInputMapping.kt:130-131` : non vide, `length <= 256`, ASCII `0x21..0x7e`) ; le test épingle 256 caractères → identité préservée (`uikitNativeToken` ET `uikitLogicalKey` → `Unidentified(token)`), 257 → `uikitNativeToken == null` et `Unidentified(null)` — jamais une troncature. Rouge/vert prouvé par mutation (voir §6.1).
- **F4 (triage 6)** : `UIKitGestureStatePhaseTest.recognizedAliasEqualsEndedRawValue` comparait `f(3)` à `f(3)` (tautologie, aveugle à toute mutation). Remplacé par l'assert de la valeur documentée : `assertEquals(UIKitTouchPhase.Ended, uikitGesturePhaseForState(3L))` — l'alias `Recognized` des gestes discrets EST la valeur 3 (un tap arrive `Ended`) ; un mutant qui déporte l'entrée 3 échoue désormais.

### 6.1 Vérifications

- **Rouge/vert (mutation)** : deux mutations temporaires de `UIKitInputMapping.kt` (borne `<= 256` → `< 256` ; entrée `3L -> Ended` → `3L -> Began`), run filtré sur `iosSimulatorArm64` → EXACTEMENT les trois échecs attendus : `nativeTokenLengthBoundIsTwoHundredFiftySixCodeUnits`, `recognizedAliasEqualsEndedRawValue`, `endedRawValueMapsToEnded` (couture partagée) ; l'ANCIEN test tautologique fût resté vert sous le même mutant. Mutations intégralement revertées (diff de `UIKitInputMapping.kt` vs HEAD : vide).
- **Suites module (après revert, vert)** : `./gradlew :kadre:platform:uikit:iosSimulatorArm64Test :kadre:platform:uikit:tvosSimulatorArm64Test` → `BUILD SUCCESSFUL`, **13 suites / 124 tests / 0 échec / 0 erreur sur CHACUNE des deux destinations** ; `nativeTokenLengthBoundIsTwoHundredFiftySixCodeUnits` et `recognizedAliasEqualsEndedRawValue` présents et verts dans les deux XML (`build/test-results/{ios,tvos}SimulatorArm64Test/`).
- **Greps docs (pas de sur-déclaration)** : « mono-consumateur » présent dans les 4 fichiers ; « micro-gamepad » présent dans `BACKEND-CAPABILITIES.md` + `capabilities/uikit.md` (toujours en « non livré ») ; notes tvOS-GC et focus présentes ; grep d'overstatement (« arbitrage livré », « micro-gamepad livré ») → 0 hit.
- **Portée par commit** : commit 1 = 4 fichiers (KDoc + 3 docs) ; commit 2 = 2 docs ; commit 3 = 2 fichiers de test. Arbre propre après chaque commit.
