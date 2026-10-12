# Task 1 report — SPIKE receiver + shadow document (iOS/tvOS Phase 6)

Branche `zcode/sess-56645e3f` (base 12cd03cb, plan 2f9a5cbd). Statut : **DONE**. Commit : `feat(uikit): add the text input document shadow with the receiver spike report`.

## 1. Livrables

| Fichier | Nature |
|---|---|
| `kadre/contracts/driver/uikit/tests/Shared/KadreUikitTextInputSpikeTests.swift` | **GARDÉ** (décision documentée §3.4) — le probe XCTest du spike, partagé ×2 familles |
| `kadre/contracts/driver/uikit/KadreUikitDriver.xcodeproj/project.pbxproj` | Câblage du spike (fileRef + groupe tests/Shared + Sources des deux targets de test) |
| `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitTextInputShadow.kt` | Le shadow document (pur, zéro UIKit) + `UIKitNsRange`/`UIKIT_NS_NOT_FOUND` + `UIKitTextInputDocumentUpdate` |
| `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitTextInputShadowTest.kt` | 20 tests purs, sans appareil |

## 2. Le SPIKE — méthode

Un seul test XCTest hébergé (`KadreUikitTextInputSpikeTests.testUITextFieldProgrammaticEditingSpike`), compilé dans les DEUX bundles de test (Shared). Il crée un `UITextField` nu (autocorrect/capitalisation off), l'ajoute à la fenêtre du fixture primaire (aucune session Kadre — le comportement UIKit NU, pas l'intégration), puis exécute la séquence : `becomeFirstResponder()` → `insertText("ab")` → `insertText("😀")` → `deleteBackward()` (sur la paire surrogate) → `deleteBackward()` (sur `b`) → `setMarkedText("é", selectedRange: (1,0))` → `unmarkText()` → `resignFirstResponder()` → second aller-retour de focus complet. Un delegate enregistre `shouldChangeCharactersInRange`/`textFieldDidChangeSelection`/`didBegin`/`didEnd` ; un observer compte `UITextField.textDidChangeNotification` ; une action compte `.editingChanged`. Chaque fait est NSLogué (`KADRE-SPIKE`, visible console xcodebuild) ET asserté quand la décision en dépend.

## 3. Les réponses (a)–(d), avec preuves citées

### (a) `setMarkedText` programmatique produit-il un état marqué ? — **OUI, pleinement exercable**

iOS (iPad Pro 11-inch M5, OS 27.0) :
```
KADRE-SPIKE (a) after setMarkedText('é', selectedRange:(1,0)) text='aé' utf16=2 scalars=2 markedTextRange=(1,1) selectedTextRange=(2,0) isFirstResponder=true textDidChangeNotification=5
KADRE-SPIKE (a) after unmarkText text='aé' utf16=2 scalars=2 markedTextRange=<nil> selectedTextRange=(2,0) isFirstResponder=true
```
tvOS (Apple TV 4K 3rd generation, OS 27.0) : valeurs IDENTIQUES (`markedTextRange=(1,1)`, `unmarkText` → `<nil>`, texte committé `aé`).

Asserts du test : `XCTAssertNotNil(field.markedTextRange, "(a) programmatic setMarkedText must produce a MARKED state …")` et `XCTAssertEqual(field.text ?? "", "aé", "(a) unmarkText must COMMIT the marked text, leaving no orphan composition…")` — passés ×2 familles. **La plage marquée est exactement (1,1)** (début au point d'insertion, longueur du marqué) et **la sélection demandée dans le marqué est mappée** (`selectedRange (1,0)` dans `"é"` → caret absolue (2,0)) — le même mapping que l'ombre (`markedStart + selected`). **Décision receiver : `UITextField` CONFIRMÉ, pas de bascule `UITextView`.**

### (b) `deleteBackward` sur un emoji supprime-t-il la paire entière ? — **OUI, la paire entière**

```
KADRE-SPIKE (b) after insertText('😀') text='ab😀' utf16=4 scalars=3 markedTextRange=<nil> selectedTextRange=(4,0) isFirstResponder=true
KADRE-SPIKE (b) after deleteBackward over '😀' text='ab' utf16=2 scalars=2 markedTextRange=<nil> selectedTextRange=(2,0) isFirstResponder=true textDidChangeNotification=3 editingChanged=3 shouldChangeCalls=0
```
Assert : `XCTAssertEqual(field.text?.utf16.count, 2, "(b) deleteBackward over the surrogate pair must remove the WHOLE character…")` — 4 → 2 unités UTF-16, ×2 familles. Aucune moitié de surrogate n'a jamais été observée.

### (c) `becomeFirstResponder`/`resignFirstResponder` déterministes sous XCTest ? — **OUI (2 allers-retours complets ×2 familles)**

```
KADRE-SPIKE (c) becomeFirstResponder -> true … isFirstResponder=true
KADRE-SPIKE (c) resignFirstResponder -> true … isFirstResponder=false
KADRE-SPIKE (c) second becomeFirstResponder -> true … isFirstResponder=true
KADRE-SPIKE (c) second resignFirstResponder -> true … isFirstResponder=false
KADRE-SPIKE totals … didBegin=2 didEnd=2
```
Retours `true` à chaque appel, `textFieldDidBeginEditing`/`DidEndEditing` en paires exactes, `isFirstResponder` fidèle, sur iPad ET Apple TV. (Sur tvOS la présentation éventuelle de l'écran de saisie système n'a pas gêné le focus programmatique.)

### (d) `textDidChange` se déclenche-t-il sur `insertText` programmatique ? — **OUI (les trois canaux), et `shouldChangeCharactersInRange` JAMAIS**

```
KADRE-SPIKE (d) after insertText('ab') text='ab' … textDidChangeNotification=1 editingChanged=1 didChangeSelection=1 shouldChangeCalls=0
KADRE-SPIKE totals textDidChangeNotification=6 editingChanged=6 didChangeSelection=7 shouldChangeCalls=0 didBegin=2 didEnd=2
```
Identique sur les deux familles. Chaque édition programmatique (`insertText`, `deleteBackward`, `setMarkedText`) incrémente `textDidChangeNotification`, `.editingChanged` ET `textFieldDidChangeSelection` ; **`shouldChangeCharactersInRange` est resté à ZÉRO appel sur TOUTE la séquence programmatique** (0/6 éditions, compteur final `shouldChangeCalls=0`).

### 3.4 Conséquences verrouillées pour Task 2/3

1. **Receiver** : `UITextField` (le ruling pré-vol tient). Le marked text programmatique est réel, l'IME mapping `CompositionChanged(range, text, selected)` est viable tel quel.
2. **Deux canaux d'édition distincts** (le fait discriminant du spike) :
   - *Édition UTILISATEUR (vrai clavier)* : `shouldChangeCharactersInRange(range, replacement)` nomme le RANGE honnête calculé par UIKit (frontières de graphèmes) AVANT la modification — le mapping `Replace(range→TextRange, replacement)` du ruling s'applique là ET SEULEMENT LÀ ;
   - *Édition PROGRAMMATIQUE (stimulus driver Task 3, appels UIKeyInput ordinaires)* : `shouldChangeCharactersInRange` NE PART JAMAIS ; le hook est `textDidChange` (notification/`.editingChanged`) + `textFieldDidChangeSelection`, APRÈS la modification. Le port doit donc y réconcilier contre le document du shadow (le stimulus Task 3 pilotera par `insertText:`/`deleteBackward:` — la plage de l'observation Replace y sera calculée par le port contre l'état du shadow, pas nommée par UIKit).
3. **Sémantique du `selectedRange` de `setMarkedText`** : UIKit le mappe DANS le marqué (caret (1,0) dans `"é"` → position absolue 2) — exactement `markedStart + selected` de l'ombre (validé par l'assert (a)).
4. **Le spike est GARDÉ** (le brief autorise « jetable ou gardé ») : ~3 s/famille, zéro session Kadre, jamais rouge sur les deux destinations, il ne fabrique aucun événement (les appels UIKeyInput programmatiques sont des méthodes ordinaires — Tech Stack du plan lui-même). Il reste la sentinelle continue des quatres faits UIKit dont Task 2/3 dépendent : si une future release iOS/tvOS casse le marqué programmatique, supprime la paire entière, ou déclenche `shouldChangeCharactersInRange` sur les appels programmatiques, CE test échoue avant que le port Kotlin ne mente mystérieusement. Il est nommé « Spike » honnêtement et documenté comme tel.

## 4. Le shadow — `UIKitTextInputShadow`

Miroir **verbatim** d'`AppKitTextInputShadow` (`kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt` l.2136–2210, y compris l'enum à 4 variantes `Applied/Stale/CompositionActive/Closed`), croisé avec `WebTextInputShadow` (`kadre/platform/web/src/webMain/kotlin/org/graphiks/kadre/platform/web/WebTextInputPort.kt` l.424–496) — les deux corps sont identiques chez AppKit et Web, l'ombre UIKit reprend ce corps exact. Adaptations types foundation : `TextInputConfig`/`TextRange`/`TextDocumentRevision` (`TextDropRaw.kt`) et `LogicalRect` (`Geometry.kt`).

- Champs : `text`, `selection`, `documentRevision`, `cursorRect`, `markedRange` (setters privés) ;
- `acceptsCursor` + `applyCursor(rect, revision)` : la révision doit être ÉGALE ;
- `applyDocument(text, selection, revision)` → `Stale` si `revision.value < documentRevision.value` ; `CompositionActive` si `markedRange != null && text != this.text` ; sinon Applied (texte+sélection+révision avancés, le marqué survit) ;
- `replaceText(range, replacement)` : borne `isWithin`, sélection → **fin d'insertion**, `markedRange` effacé ;
- `setMarkedText(replacementRange, markedText, selectedRange)` : **deux** bornes (replacement dans le texte, selected dans le marqué), marqué posé à `[replacementStart, replacementStart + markedText.length]`, sélection mappée `markedStart + selected` ;
- `clearMarkedText()` : `false` quand rien n'est marqué ;
- `resolveRange(nsRange)` : `toTextRangeOrNull(text) ?: selection.takeIf { location == UIKIT_NS_NOT_FOUND }` (NOT_FOUND → sélection ; hors bornes/invalide → null) ;
- **`UIKitNsRange(location: Long, length: Long)`** : la forme PURE de `NSRange` (zéro UIKit) — Task 2 convertira le `NSRange` du klib (champs `ULong`) vers cette valeur avant d'interroger l'ombre ; `UIKIT_NS_NOT_FOUND = Long.MAX_VALUE` (miroir d'`APPKIT_NS_NOT_FOUND`).

Zéro import `platform.*` : le fichier ne compile qu'avec foundation — prouvé par les tests uikitTest qui tournent sans toucher UIKit.

## 5. TDD — journal et liste des tests

Le test est écrit AVANT l'implémentation ; 5 exécutions Gradle ont été nécessaires (1 rouge compilation, 3 rouges assertion — tous des bugs du TEST, pas du shadow, détaillés §7), puis vert. Liste des 20 tests (`UIKitTextInputShadowTest`) :

1. `initialStateMirrorsTheConfigWithNoCursorAndNoMarkedRange`
2. `applyCursorAcceptsTheExactRevisionAndStoresTheRect`
3. `applyCursorRefusesAnyOtherRevisionWithoutTouchingTheStoredRect`
4. `applyDocumentAppliesANewerSnapshot`
5. `applyDocumentAcceptsTheSameRevisionAsAnIdempotentRestate`
6. `applyDocumentRefusesAStaleSnapshotLeavingTheShadowUntouched`
7. `applyDocumentRefusesATextChangeWhileACompositionIsActive`
8. `applyDocumentAcceptsASameTextSnapshotWhileACompositionIsActive`
9. `replaceTextReplacesSelectsTheInsertionEndAndClearsTheMarkedRange`
10. `replaceTextAtTheEndOfTheDocumentInserts`
11. `replaceTextRefusesAnOutOfRangeRangeLeavingTheShadowUntouched`
12. `setMarkedTextMarksTheSpanAndSelectsWithinIt`
13. `setMarkedTextAtTheEndOfTheDocumentStartsAComposition`
14. `setMarkedTextRefusesAnOutOfRangeReplacementLeavingTheShadowUntouched`
15. `setMarkedTextRefusesASelectionBeyondTheMarkedText`
16. `clearMarkedTextReportsFalseWhenNothingIsMarked`
17. `clearMarkedTextClearsTheMarkedRangeAndReportsTrueLeavingTheTextCommitted`
18. `resolveRangeMapsARangeWithinTheText`
19. `resolveRangeFallsBackToTheSelectionOnNotFound`
20. `resolveRangeReturnsNullForOutOfRangeOrInvalidRanges`

Cas aux bornes couverts : révision périmée (Stale, état intact), révision égale (idempotent), document changé sous composition (CompositionActive, marqué survit), document À TEXTE ÉGAL sous composition (Applied, marqué survit), remplacement hors bornes haut/bas, remplacement refusé qui garde la composition, insertion en fin de document, sélection au-delà du marqué refusée, `clearMarkedText` sans marqué (false ×2), resolveRange NOT_FOUND avec longueur non nulle (pas de plage fantôme), locations/négatifs invalides → null.

## 6. Mutations (6/6 tuées, chacune par le test attendu)

| Mutant | Changement | Verdict | Tueur attendu (vérifié) |
|---|---|---|---|
| M1 | Garde Stale `<` → `!=` (refuse la révision égale) | **KILLED** | `applyDocumentAcceptsTheSameRevisionAsAnIdempotentRestate` |
| M2 | Garde `CompositionActive` supprimée | **KILLED** | `applyDocumentRefusesATextChangeWhileACompositionIsActive` |
| M3 | Borne de `replaceText` supprimée | **KILLED** | `replaceTextRefusesAnOutOfRangeRangeLeavingTheShadowUntouched` |
| M4 | Sélection du marqué sans le décalage `markedStart` | **KILLED** | `setMarkedTextMarksTheSpanAndSelectsWithinIt` |
| M5 | Repli `NOT_FOUND` de `resolveRange` supprimé | **KILLED** | `resolveRangeFallsBackToTheSelectionOnNotFound` |
| M6 | `clearMarkedText` retourne toujours true | **KILLED** | `clearMarkedTextReportsFalseWhenNothingIsMarked` |

Harness : `build/tmp/run-shadow-mutations.sh` (une substitution perl par mutant, run `iosSimulatorArm64Test`, vérification que le fichier a bien changé avant chaque run, restauration vérifiée `cmp` après chaque run ; M5 relancé séparément après correction du motif `??`→`?:`). Un premier passage du harness a tourné depuis le mauvais cwd (le checkout principal — tous « SURVIVED » fictifs) : corrigé (`cd "$ROOT"` + garde anti-substitution-silencieuse) avant de compter tout résultat.

## 7. Commandes et issues (chronologique)

| # | Commande | Issue |
|---|---|---|
| 1 | `./gradlew :kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64 :kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64 --console=plain -q` (background) | exit 0 — frameworks du driver |
| 2 | `xcodebuild -project KadreUikitDriver.xcodeproj -scheme KadreUikitDriverIos -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0' -resultBundlePath build/spike-ios.xcresult -only-testing:KadreUikitDriverIosTests/KadreUikitTextInputSpikeTests CODE_SIGNING_ALLOWED=NO test` (+ `TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=2`, background) | exit 0 — `Test Case … testUITextFieldProgrammaticEditingSpike' passed (2.923 seconds)` |
| 3 | idem tvOS : `-scheme KadreUikitDriverTvOs -destination 'platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0' … -only-testing:KadreUikitDriverTvOsTests/…` | exit 0 — `passed (3.585 seconds)` |
| 4 | `./gradlew :kadre:platform:uikit:iosSimulatorArm64Test :kadre:platform:uikit:tvosSimulatorArm64Test` (run 1) | ROUGE compilation — `Argument type mismatch: actual 'UIKitTextInputDocumentUpdate', but 'Boolean' was expected` (le test wrappait l'enum dans `assertTrue`) → test corrigé (`assertEquals(Applied, …)`) |
| 5 | (run 2) | ROUGE ×6 — `IllegalArgumentException: text range is invalid` : le test appelait `setMarkedText(…, TextRange(1, 0))` en pensant `NSRange(location, length)` ; `TextRange` est start/end-exclusif → le caret dans le marqué est `TextRange(1, 1)` → test corrigé. **Fait documenté pour Task 2 : le port convertira `NSRange(location,length)` d'UIKit en `TextRange(location, location+length)` AVANT d'appeler l'ombre.** |
| 6 | (run 3) | ROUGE ×2 — (i) `Expected <heX!o>, actual <heX!oé>` : le texte composé FAIT PARTIE du texte de l'ombre (l'assertion l'avait oublié) ; (ii) le refus `(4,6)` était devenu dans les bornes après l'ajout du `É` (texte 6 unités) → attendus corrigés |
| 7 | (run 4) | ROUGE ×1 — le `(4,6)` corrigé en `(4,8)` manquait encore (résidu du run 3 dans la même méthode) → corrigé |
| 8 | (run 5) | **VERT — BUILD SUCCESSFUL, 20/20 ×2 familles (suite uikitTest complète : 144 tests)** |
| 9 | Harness de mutations (passage corrigé + M5 séparé) | **6/6 KILLED** (table §6), shadow restauré `cmp`-identique |
| 10 | `./gradlew :kadre:platform:uikit:iosSimulatorArm64Test :kadre:platform:uikit:tvosSimulatorArm64Test` (final) | **VERT — BUILD SUCCESSFUL ; `UIKitTextInputShadowTest` : tests=20 failures=0 ×2 familles** |

## 8. Auto-revue

- **Fidélité verbatim** : chaque corps de méthode est ligne-à-ligne celui d'AppKit (noms de paramètres et gardes inclus) ; les helpers privés (`toTextRangeOrNull`, `isWithin`, `String.replace`) reproduisent les définitions AppKit (l.2241–2252). La variante `Closed` de l'enum est conservée (précédent AppKit — elle sert au PORT, pas à l'ombre).
- **Zéro UIKit** : aucun import platform dans le shadow ni dans le test ; le test tourne dans uikitTest pur.
- **Le hook `textDidChange` côté Kotlin** : le plan Task 2 dit « UITextFieldDelegate textDidChange » — rappel factuel (spike) : le delegate `UITextFieldDelegateProtocol` n'a pas de `textFieldDidChange` (c'est UITextView) ; les canaux réels sont la notification `UITextField.textDidChangeNotification`, l'événement `.editingChanged` et `textFieldDidChangeSelection(_:)` — tous trois prouvés vivants sur édition programmatique. Task 2 branchera l'un d'eux (la notification est la plus directe depuis un subclass).
- **Limites assumées** : (i) le spike n'exerce pas `shouldChangeCharactersInRange` (impossible honnêtement sans vrai clavier — charter, cohérent avec le ruling Task 3) ; le ruling « deletions honnêtes » reste valable pour l'édition utilisateur, et Task 3 prouvera le chemin programmatique contre le shadow ; (ii) le `updateCursor` reste sans effet UIKit (limite déjà tracée au plan, miroir web) ; (iii) tvOS : présentation éventuelle de l'écran de saisie système = runtime behavior, non assertée (conforme au ruling tvOS).
- **Risque de flake spike** : faible (pumping RunLoop borné, pas de sommeil nu, 2/2 familles vertes au premier essai) ; le tearDown retire le champ et resign proprement.
- **Projet Xcode** : pbxproj édité à la main (fileRef `2A0CB0FEB0C0FFEE000000F1`, buildFiles `…B1`/`…B2`, groupe tests/Shared, deux Sources phases) — build ×2 familles OK.

## 9. Résidus

Aucun fichier temporaire hors arbre commitable : `build/tmp/run-shadow-mutations.sh` et les logs de mutants vivent sous `build/` (ignoré). Les xcresult du spike sous `kadre/contracts/driver/uikit/build/` (ignoré). Le shadow est dans son état original (vérifié `cmp` contre la copie pristine après mutations).
