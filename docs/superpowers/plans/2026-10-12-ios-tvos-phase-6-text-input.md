# iOS/tvOS Phase 6 — Text input, IME et clavier logiciel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer la saisie de texte native — un receiver UIKit adapter-owned branché sur le `TextInputPort` commun (surrounding text, sélection, composition/marqué, curseur, actions de soumission), ouvert exclusivement par `SurfaceInput.openTextInput`, avec les révisions acceptées tenues par un shadow document — prouvé par le driver sur les deux familles, contrat BCK-024 activé.

**Architecture:** Le miroir fidèle du pattern web phase 5 (`WebTextInputPort`) : un **shadow document pur** (`UIKitTextInputShadow`, le miroir d'`AppKitTextInputShadow`/`WebTextInputShadow`) tient le texte/sélection/révision ; le port calcule chaque observation contre le shadow et ne publie JAMAIS une révision de son cru ; le **write-back** (le champ d'abord, le shadow ensuite) garde le receiver et l'application au même document (D-X3 web). Le receiver est un **`UITextField` Kadre-owned** ajouté en sous-vue de la surfaceView à l'ouverture (retiré à la fermeture) — `cancelsTouchesInView` non applicable, aucun layout Kadre, la vue host n'est jamais sous-classée. Focus : `becomeFirstResponder` du receiver somme le clavier logiciel (iOS) ; les notifications clavier ne pilotent AUCUN layout ; la suspension/reprise passe par les stimuli `FocusChanged` existants (phase 1).

**Tech Stack:** Kotlin 2.4.20 (uikitMain), `UITextField` subclass + `UITextFieldDelegateProtocol` (klib ×2 familles ; spike marked-text/IME en Task 1), XCTest hosted avec `insertText:`/`deleteBackward:` in-process (méthodes UIKeyInput ordinaires, appelables programmatically), pipeline evidence (gabarit BCK-022/023).

**Spec:** `kadre/IOS-IMPLEMENTATION-ROADMAP.md` §6 (l.271–284, checklist et critère de sortie) ; précédents : `WebTextInputPort.kt` (seam/shadow/révisions, verbatim), `AppKitTextInputShadow` (KffiAppKitWindowPort.kt l.2136–2210), Android `AndroidImeEventMapper` (mapping IME déterministe testé).

## Décisions fermées par ce plan

| Décision | Résultat fermé ici |
|---|---|
| Receiver | `UITextField` Kadre-owned (subclass — `UITextFieldMeta` ×2 familles ; UIKeyInput complet, `markedTextRange`/`setMarkedText:` partiels mais présents) ; delegate = le receiver lui-même (`UITextFieldDelegateProtocol`) ; ajouté en sous-vue cachée de la surfaceView à l'open (frame zéro, alpha géré — jamais de layout Kadre), retiré au close. Spike Task 1 : becomeFirstResponder + saisie in-process + marked text sur simulateur, AVANT tout autre commit de code. |
| Shadow document | `UIKitTextInputShadow` — miroir exact d'`AppKitTextInputShadow`/`WebTextInputShadow` : text/selection/documentRevision/cursorRect/markedRange ; `applyCursor` (révision exacte), `applyDocument` (Stale/CompositionActive/Closed), `replaceText`/`setMarkedText`/`clearMarkedText` avec bornes. |
| Révisions | Chaque observation est timbrée de la révision du shadow (celle acceptée par l'application — jamais une révision propre au port) ; le write-back (champ puis shadow) restaure l'accord ; refus `StaleRevision` côté runtime. |
| Deletions | `UITextField` délègue la suppression au système : `shouldChangeCharactersInRange` porte le RANGE complet du remplacement UIKit (frontières de graphèmes calculées par UIKit) → `Replace(range, "")` honnête — PAS de calcul d'un-code-unit (la limite web ne s'applique pas : UIKit nomme la frontière). |
| Marqué/IME | `setMarkedText:` → `CompositionChanged(range, text, selected)` ; `unmarkText` → terminal `CompositionChanged(null, "", null)` ; limites documentées (marked text sur UITextField est partiel — spike Task 1, si insuffisant basculer sur UITextView, décision reportée avec preuve). |
| Purpose/config → UIKit | `TextInputPurpose` → `UIKeyboardType` (Email→emailAddress, Number/Decimal→numbersAndDecimal…), `TextInputAction` → `UIReturnKeyType` (Done/Go/Next/Search/Send), `Password` → `isSecureTextEntry`, `autocorrectionType`, `capitalization` → `autocapitalizationType` — mapping table testée. |
| tvOS | Même port (UITextField ×2 klibs) ; la présentation de l'écran de saisie système tvOS au first-responder est un comportement runtime validé par driver test (roadmap l.283) ; si l'écran n'apparaît pas en environnement de test, le chemin reste prouvé par les callbacks programmatiques + charter manuel. Pas de symbole IME iOS-only copié. |
| Contracts | `BCK-024` « UIKit text input session lifecycle » (2 familles : open exclusif, révision périmée, focus suspend/resume, fermeture, mapping replace/delete/action) + `INP-004` reporté — les sentinelles réelles selon la couverture. |

## Global Constraints

- Une seule session texte active par surface (`RuntimeSurfaceInput` refuse `AlreadyInUse` ; le port CAS un seul `liveOwner` — miroir web l.119-120).
- Chaque observation timbrée de la révision du SHADOW (l'application dernière acceptée) — jamais une révision propre.
- Write-back : champ d'abord, shadow ensuite ; le champ n'est JAMAIS écrit sans que le shadow suive (et réciproquement l'observation est calculée contre la révision que le runtime peut admettre).
- Aucune frappe inventée par caractère (UIKit nomme les frontières via `shouldChangeCharactersInRange`).
- Le receiver n'est jamais la vue host ; aucun layout Kadre ; retiré proprement à la fermeture ; callbacks après fermeture rejetés (call-scoped holder runtime + closed guard port).
- Le clavier logiciel ne redimensionne aucun layout Kadre (aucun observer de frame ne modifie la vue ; les notifications clavier ne font QUE nourrir suspend/resume si branchées).
- Gradle < 8 min/commande, longues en background+polling ; preuves séparées par famille ; activation same-commit.

## Review Focus

1. **Accord champ↔shadow** — tout chemin où le champ et le shadow divergent silencieusement (write-back raté, observation calculée contre un shadow périmé) = document mensonger. Test : séquence insert/delete/composition avec assertions sur champ.text ET shadow.text à chaque pas.
2. **Exclusive open CAS** — deuxième `open` pendant une session vivante → `AlreadyInUse` sans casser la première ; open-close-open rapides → aucune fuite de receiver (un seul UITextField vivant).
3. **Callbacks tardifs** — observation livrée après close de SA session → rejetée par le guard closed (call-scoped holder) ; après close du receiver → no-op. Test : close puis insertText programmatique → rien.
4. **Composition orpheline** — fermeture pendant un marqué actif → `discardMarkedText`-équivalent (le champ vidée de son marqué), aucune composition résiduelle dans le document final.
5. **Mapping purpose/config** — chaque `TextInputPurpose`/`Action`/`Capitalization` a sa valeur UIKit exacte (table testée ; une valeur/UIKit manquante = fatal au premier usage réel).

---

### Task 1: SPIKE receiver + shadow document

**Files:**
- Create (spike jetable ou gardé): probe driver minimal
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitTextInputShadow.kt`
- Test: `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitTextInputShadowTest.kt`

**Interfaces:**
- Produces: `internal class UIKitTextInputShadow(config: TextInputConfig)` — champs `text/selection/documentRevision/cursorRect/markedRange` ; `applyCursor(rect, revision): Boolean`, `applyDocument(text, selection, revision): UIKitTextInputDocumentUpdate { Applied, Stale, CompositionActive, Closed }`, `replaceText(range, replacement): Boolean`, `setMarkedText(replacementRange, markedText, selectedRange): Boolean`, `clearMarkedText(): Boolean`, `resolveRange(nsRange): TextRange?`.

- [ ] **Step 1: SPIKE (premier livrable)** — driver app : un probe test qui (a) crée un `UITextField` Kotlin-side ? NON — le spike se fait en Swift d'abord ? NON PLUS : le receiver sera Kotlin (Task 2). Le spike minimal : en Swift, dans le driver, créer un `UITextField`, l'ajouter à la fenêtre du fixture, `becomeFirstResponder()`, puis programmatique `insertText("ab")`, `deleteBackward()`, `setMarkedText("é", selectedRange:)` — assert `text` == "aé" — et `resignFirstResponder`. Si le marked text programmatique ne produit pas l'état attendu, la décision receiver passe à `UITextView` (klib ×2 familles, `setMarkedText` présent) — documenter avec preuves. Ce spike valide le chemin AVANT que le port Kotlin soit écrit.
- [ ] **Step 2: Shadow TDD** — mirror verbatim d'`AppKitTextInputShadow` (KffiAppKitWindowPort.kt l.2136-2210) adapté aux types foundation : tests (Stale, CompositionActive, replaceText bornes + sélection → insertion end + markedRange clear, setMarkedText bornes + sélection dans le marqué, clearMarkedText false si rien, resolveRange NOT_FOUND → sélection).
- [ ] **Step 3: Vert ×2 destinations + Commit** — `feat(uikit): add the text input document shadow and receiver spike report`.

---

### Task 2: `UIKitTextInputPort` + receiver + mapping config

**Files:**
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitTextInputReceiver.kt` (UITextField subclass, delegate = self)
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitTextInputPort.kt`
- Modify: `UIKitWindowRuntime.kt` (textInputPortFactory = { commandPort.textInputPort(it) } via le port, miroir AppKit l.139)
- Modify: `UIKitWindowCommandPort.kt` (`fun textInputPort(surfaceId): TextInputPort` + lifecycle receiver dans les points phase 2/5)
- Test: driver (Task 3) ; uikitTest : mapping config→UIKit (table pure testable)

**Interfaces:**
- Produces: port `TextInputPort` complet (`capability = Supported` structural — miroir web D-X2 ; open exclusif CAS liveOwner ; install-listeners failure → `PlatformFailure(UIKit, "text-input", "install-listeners-failed")`) ; receiver : delegate callbacks → observations (shouldChangeCharactersInRange → Replace, UITextFieldDelegate textDidChange → sync, tokenizer non requis v1 pour UITextField) ; `updateCursor` accepté au shadow sans effet UIKit (limite documentée) ; `updateDocument` → write-back champ puis shadow, non-addressable → owner close + `Closed(TextInputSession)`.
- Mapping config (pure, uikitTest) : purpose → UIKeyboardType/secureEntry, action → UIReturnKeyType, capitalization → autocapitalizationType, autocorrect → autocorrectionType.

- [ ] **Step 1: Mapping table TDD (pure, uikitTest)** — chaque purpose/action/capitalization → sa valeur UIKit (raw values vérifiées header), inconnu → fatal/Unidentified.
- [ ] **Step 2: Le receiver** — subclass `UITextField` (kadre-old precedent), `delegate = self`, `UITextFieldDelegateProtocol` conform ; `shouldChangeCharactersInRange` → calcule l'observation Replace contre le shadow (le champ applique APRÈS shouldChange=true — ordre : observation calculée, champ modifié par UIKit, shadow reconcilié au `textDidChange` ; LIRE la sémantique exacte et documenter) ; `textFieldShouldReturn` → `Action(config.action)`.
- [ ] **Step 3: Le port** — liveOwner CAS ; open → receiver créé (config mappée), ajouté en sous-vue cachée de surfaceView (le receiver doit être dans la hiérarchie pour devenir first responder), becomeFirstResponder ; install failure → owner.close() + PlatformFailure ; updateCursor/updateDocument contre le shadow ; close → resign + remove de la superview + counters.
- [ ] **Step 4: Wiring** — `UIKitWindowRuntime` manager construction : `textInputPortFactory = TextInputPortFactory(commandPort::textInputPort)` (miroir AppKit l.139) ; le port résout par surfaceId (mono-surface phase 4 : le primaire).
- [ ] **Step 5: Compiles + suites vertes + Commit** — `feat(uikit): deliver the text input port over a hidden native receiver`.

---

### Task 3: Driver proof — la saisie réelle par UIKit

**Files:**
- Modify: driver tests (`KadreUikitTextInputTests.swift`, noms figés) + probe (`KadreUikitTextInputProbe` : receiver discovery, shadow/state facts)
- Modify: ConsumerSceneStore si besoin (capture des faits texte)

**Interfaces:**
- Consumes: Tasks 1-2 ; fixture `primarySurface` → `surface.input.openTextInput(config)` appelé côté Kotlin (probe/application) → events drainés en faits Swift.
- Produces: noms figés pour Task 4 — `uikit-text-open-exclusive-rejects-second`, `uikit-text-replace-mapped-from-ukit-range`, `uikit-text-delete-grapheme-honest`, `uikit-text-stale-revision-refused`, `uikit-text-focus-suspend-resume`, `uikit-text-close-no-orphan-composition`, `uikit-text-actions-mapped` (+ tvOS : même shared suite).

- [ ] **Step 1: Les scénarios** (le stimulus = appels programmatiques UIKeyInput sur le receiver découvert via probe — pas de synthèse d'événement système, comme ruled) :
  (1) open exclusif : openTextInput ×2 → second `AlreadyInUse` ; (2) `insertText("ab")` → Replace publié, champ "ab", shadow accord ; (3) `deleteBackward()` après "ab😀" → Replace range = la PAIRE surrogate entière (UIKit nomme la frontière — le contrat honnête UIKit) ; (4) `updateSurroundingText` avec révision périmée → `StaleRevision` ; (5) focus : resign → Suspended (via FocusChanged ou receiver), re-first-responder → Active ; (6) close pendant marqué actif → champ marqué vidé, document sans composition orpheline, session Closed, receiver retiré de la superview ; (7) `textFieldShouldReturn` → `Action(config.action)` ; (8) config mapping (Email → keyboardType, Password → secureEntry — asserté sur le receiver).
- [ ] **Step 2: tvOS** — même suite shared tourne sur Apple TV : le champ devient first responder (l'écran de saisie système tvOS peut se présenter — runtime behavior ; les assertions portent sur les événements, pas sur la présentation visuelle). Documenter ce qui est prouvé vs charter.
- [ ] **Step 3: Vert ×2 destinations (aggregator, background+poll). Commit** — `test(uikit): prove the text input session against the live uikit receiver`.

---

### Task 4: Contrat BCK-024 + evidence

**Files:** registry (+1 BCK-024 active), evidence.tsv, validator (uikitContractIds), script asserts, capabilities/uikit.md (+1 row + état absent : IME réel/charter, purpose non couverts si tel).
- [ ] BCK-024 : scenarios = les 8 noms figés (les deux familles — la suite shared tourne partout), sentinelles = 2-3 mappées à de vrais tests avec mutations réelles (exclusive-open CAS mutant → open-exclusif échoue ; stale-refusal mutant → stale test échoue ; orphan-composition mutant → close test échoue).
- [ ] Can-fail cités (radii), gate vert, **UN commit d'activation** — `feat(uikit): activate BCK-024 with text-input evidence`.

---

### Task 5: Docs closure

- DESIGN §15.2 (text input livré phase 6 : receiver caché, shadow/révisions, exclusivité ; limites : composition IME réelle = charter, clavier ne redimensionne rien), BACKEND-CAPABILITIES §6.2 (livré phase 6 + limites), INTEROP §7 si withUIKitView interplay (non — rien), catalog (rien de nouveau public).
- Commit : `docs: close the phase 6 UIKit text-input perimeter`.

---

### Task 7: Sweep + PR

- Sweep à froid (runtime jvmTest, natives ×2, appkit, gate uikit all, consumers ×2).
- Checklist sortie (roadmap l.283-284) : une seule session texte par surface (open exclusif testé) ; aucun remplacement à révision périmée (stale refusé) ; aucune composition orpheline après fermeture (close test) ; clavier logiciel sans redimensionnement Kadre (aucun code de layout dans le diff, receiver frame non modifié par Kadre).
- Rapport + push + PR (contrôleur).

---

## Séquencement et estimation

Tasks 1→2→3→4→5→6→7. T1 porte le spike receiver (marqué text programmatique) ; T2 le port/shadow ; T3 la preuve réelle ; T4-T6 preuves/docs. Le mapping des phases : **cette phase = roadmap Phase 6** (l.271-284). Budget attendu : ~30-40M tokens.
