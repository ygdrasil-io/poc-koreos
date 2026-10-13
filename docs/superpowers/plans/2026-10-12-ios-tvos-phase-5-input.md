# iOS/tvOS Phase 5 — Touch, clavier physique, pointer, gestes et télécommande tvOS — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer l'input ordinaire UIKit/tvOS — touch/stylet (`TouchChanged`), pointer/hover (`Pointer*`), clavier physique via GameController (`KeyChanged`), gestes reconnus (`Gesture`), télécommande Siri (`KeyChanged`/Presses via micro-gamepad) — annoncés par `InputObservationChanged`, mappés vers le reducer commun sans toucher aux contrôles du host — jalon intermédiaire avant phase 6 (text input).

**Architecture:** Zéro sous-classement des objets UIKit du host : recognizers et sources Kadre-owned ajoutés additivement à la vue fournie (pattern kadre-old : `NSObject` proxy + target/action + delegate, prouvé compilé) ; la SKIN des stimuli passe par un **kernel de mapping pur** (miroir `WebInputMapping` : primitives → stimuli, `PhysicalKey.Code` HID 0x07 pour les codes clavier, `Unidentified(nativeToken)` sinon, jamais inventé). Annonce : `SurfaceStimulus.InputObservationChanged` après installation (miroir web l.1028-1036 : replay avant annonce). Le clavier physique n'est PAS atteignable additivement via UIKit (`pressesBegan` = chaîne de first responder) — la route honnête = `GCKeyboard.keyChangedHandler` (closure additive, notification-driven) ; `addKeyCommand` = shortcuts only, hors périmètre v1.

**Tech Stack:** Kotlin 2.4.20 (uikitMain), `UIGestureRecognizer` subclass Kadre-owned (Meta présent ×2 familles ; spike de confirmation en Task 1), `UIHoverGestureRecognizer`, `UIPencilInteraction`, `GCKeyboard`/`GCController`/`GCMicroGamepad` (GameController klib ×2 familles), XCTest hosted.

**Spec:** `kadre/IOS-IMPLEMENTATION-ROADMAP.md` §5 Phase 5 (l.250–269, checklist et critère de sortie verbatim) ; précédents : WebInputMapping.kt (mapping HID), AppKitTouchAdapter (identité par contact, Cancelled synthétique), WebHostSession l.1028-1036 (annonce).

## Décisions fermées par ce plan

| Décision | Résultat fermé ici |
|---|---|
| Touch sans sous-classe host | `UIGestureRecognizer` Kadre-owned (subclass — `UIGestureRecognizerMeta` présent ×2 familles ; spike T2 confirme l'override des handlers propres) ajouté additivement à la vue ; contacts mappés par identité (token par UITouch, miroir AppKitTouchAdapter : fin à position invalide → `Cancelled` à la dernière position valide). |
| Stylus | `UITouchTypePencil/Stylus` → stimuli avec `PointerKind.Pen` sur les Pointer* et pressure si valide ; `UIPencilInteraction` (squeeze) = hors périmètre v1 (charter). |
| Clavier physique | `GCKeyboard.keyChangedHandler` (additive, sans responder chain) → `KeyChanged` (`PhysicalKey.Code` HID via usagePage/usageId du `GCKey`) ; annonce `keyboardInstalled=true` quand un GCKeyboard est connecté ; `addKeyCommand` (shortcuts) hors périmètre v1. |
| tvOS télécommande | `GCController`/`GCMicroGamepad.valueChangedHandler` + `controllerPausedHandler` (additives) → directions/pause mappées `KeyChanged` ; bouton A/select via `GCControllerButtonInput` ; XCUIRemote = UI-testing, hors portée du bundle unit — charter manuel pour le bout réel. |
| Gestes | Reconnaissances additives explicites : Tap (DoubleTap), Pan (Pan+delta), Pinch (Pinch+scale), Rotation (Rotation+radians) — `GestureKind` announce = exactement ce qui est installé (web : gestes non installés = union sans membre Gesture). |
| Pointer/hover | `UIHoverGestureRecognizer` (iOS-only ; tvOS = 0 hits) → `PointerEntered/Moved/Left(Mouse)` ; boutons souris pendant drag : via le recognizer touch (button mask `UIEvent.ButtonMask`). |
| Focus tvOS/iOS | Observation MINIMALE : aucun détournement du moteur de focus (roadmap l.259) — le focus surface reste piloté par les transitions de scène (phase 1). Focus moteur = charter/hors périmètre v1 documenté. |
| Annonce | `SurfaceStimulus.InputObservationChanged` publié APRÈS installation des sources (miroir web : stimuli en attente rejoués avant l'annonce) ; retiré au teardown (miroir AppKit `inputObservationRevoked`). |
| Contracts | `BCK-022` « UIKit touch and pointer delivery » (2 familles où les mécanismes existent : touch iOS-only? NON — touch existe sur tvOS aussi : UITouchMeta ×2) + `INP-004` « UIKit physical keyboard via GameController » (INP = runtime input family ; ou BCK-023) — split final en Task 4 selon requiredTargets réels. |

## Global Constraints

- Aucun détournement des contrôles natifs du host : recognizers additifs retirés au teardown ; `cancelsTouchesInView = false` par défaut sauf décision tracée (le host garde ses touches).
- Mapping honnête : touche/clé inconnue → `Unidentified(nativeToken)` (jamais de HID inventé) ; pas de capability annoncée sans source installée ; `force` seulement si valide.
- Le mapper est PUR (primitives → stimuli, zéro UIKit) et testé en uikitTest — le binding UIKit-typed est prouvé par driver/probe (pattern phase 2).
- `InputObservationChanged` publié après install, révoqué au teardown ; les stimuli en attente avant l'annonce sont rejoués avant (miroir web).
- Preuves séparées par famille ; gate hors `:kadre:check` ; commandes < 8 min ou background+polling.
- tvOS : pas de `UIHoverGestureRecognizer`, pas de notifications clavier (klib = 0) — pas de route pointer/hover tvOS v1 (documenté) ; télécommande via GameController uniquement.

## Review Focus

1. **Recognition sans fuite** — recognizers/proxy retirés au teardown ; callbacks après teardown rejetés (Stale pattern) ; la vue host garde ses propres touches (cancelsTouchesInView policy tracée).
2. **Identité de contact** — un UITouch = un `nativeIdentity` stable (jamais ré-alloué entre events) ; fin/cancel à la dernière position valide.
3. **Mapping exhaustif sans invention** — tout code/usage inconnu → `Unidentified`, jamais approximé ; les enums d'annonce (gestureKinds) == exactement les recognizers installés.
4. **Le spike UIGestureRecognizer-subclass** — si l'override des handlers propres ne compile/ne fire pas, le repli documenté = target/action recognizers standard (Pan/Tap par type) avec perte de granularité des phases — décision à reporter, jamais de simulation.
5. **Annonce après install, révoquée au teardown** — test compteur (pattern phase 2) ; pas d'annonce avant que les sources soient vivantes.

---

### Task 1: Kernel de mapping input (pur) + table HID

**Files:**
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitInputMapping.kt`
- Test: `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitInputMappingTest.kt`

**Interfaces:**
- Produces: mappers purs `uikitKeyState(...)`, `uikitPhysicalKey(usagePage, usageId)`, `uikitLogicalKeyGC(keyCharacters, keyCode)`, `uikitTouchPhase(UITouchPhase-like enum param)`, `uikitGesturePhase`, `uikitPointerKind`, `uikitScrollDelta`, `UIKitInputPrimitives` (data classes de primitives sans UIKit) — LIRE `WebInputMapping.kt` (template HID 0x07 complet) et `AppKitTouchAdapter` (identité/cancel) avant.
- Consumes: `SurfaceStimulus` payloads (TouchPhase, GestureKind, PhysicalKey, LogicalKey, NamedKey, KeyLocation, KeyState, KeyboardModifiers, PointerKind, PointerButton, PointerButtonState, PenState, ScrollDelta, GestureKind).

- [ ] **Step 1: Lire WebInputMapping.kt (table HID 0x07 complète l.53-56) + AppKitTouchAdapter (l.843-899) + les payload enums de foundation (SurfaceInput.kt l.37-272).**
- [ ] **Step 2: Tests d'abord** — table HID (codes connus → Code(0x07, usage), inconnu → Unidentified(token sane)), phases touch 4/4, gesture phases + invariants de payload (Pan⇒delta, Pinch⇒scale>0, DoubleTap⇒null), pressure hors [0,1] → null, modifiers set vide/multiple.
- [ ] **Step 3: Implémentation + vert** (`iosSimulatorArm64Test` + `tvosSimulatorArm64Test`).
- [ ] **Step 4: Commit** — `feat(uikit): add the pure input mapping kernel`.

---

### Task 2: Touch binding (recognizer Kadre-owned) + annonce

**Files:**
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitTouchBinding.kt`
- Modify: `UIKitWindowCommandPort.kt` (wiring : installe/retire au peer lifecycle comme l'observation phase 2 ; publie InputObservationChanged + stimuli touch)
- Test: driver probe counters + mapper déjà testé (le binding est UIKit-typed, prouvé par driver Task 4)

**Interfaces:**
- Produces: `internal class UIKitTouchBinding(view: UIView, publish: (SurfaceStimulus) -> Boolean)` — `install()`, `teardown()` ; recognizers : un recognizer touch Kadre-owned par fenêtre (subclasse — spike) OU pan+long-press composites ; `cancelsTouchesInView = false` (le host garde ses touches — décision tracée, KDoc).

- [ ] **Step 1: SPIKE (avant tout commit de code)** — prouver qu'une subclass Kotlin de `UIGestureRecognizer` override `touchesBegan(touches:Set<*>, withEvent:UIEvent?)` et reçoit des events (probe local). Si NON → repli documenté : Pan/ScreenEdgePan composites (phases via velocity) — report au contrôleur avec la preuve compile/run.
- [ ] **Step 2: Le binding** — recognizer(s) avec `cancelsTouchesInView = false` ; chaque UITouch → `TouchChanged(nativeIdentity = token stable par touch, phase Started/Moved/Ended/Cancelled, position = locationInView(view) → LogicalPoint via scale, pressure si >0 et type stylus)` ; fin/cancel à position invalide → `Cancelled` à la dernière valide (miroir AppKitTouchAdapter) ; teardown retire les recognizers.
- [ ] **Step 3: Annonce** — après install : `InputObservationChanged(touchInstalled = true, gestureKinds = installés)` via stimulusSink (replay-before-announce : les stimuli touch en attente avant l'annonce sont publia par le sink d'abord — miroir web l.1014-1036) ; teardown → all-false + emptySet.
- [ ] **Step 4: Compiles + suites vertes. Commit** — `feat(uikit): deliver additive touch recognition with honest announcement`.

---

### Task 3: Clavier physique (GCKeyboard), gestes, pointer/hover

**Files:**
- Create: `UIKitKeyboardBinding.kt` (GCKeyboard connect notification + keyChangedHandler → KeyChanged), `UIKitGestureBindings.kt` (tap/pinch/rotation/pan + hover iOS-only), 
- Modify: `UIKitWindowCommandPort.kt` (wiring + annonce gestureKinds/keyboard/pointer)
- Test: mappers kernel déjà verts ; driver probe en Task 4.

**Interfaces:**
- Produces: `internal class UIKitKeyboardBinding(publish)` — observe `GCKeyboardDidConnectNotification` + scan initial `GCController.controllers()` ; `keyChangedHandler = { keyboard, key, pressed }` → `KeyChanged(PhysicalKey.Code(key.usagePage, key.usageCode), LogicalKey(Character(key.characters) ou Named si mappable), location = Standard, pressed, repeat=false, modifiers=lus)` ; teardown = handler nil + annonce false. `UIKitGestureBindings(view, publish)` — Tap double (DoubleTap), Pan (Pan delta), Pinch (scale), Rotation (radians), Hover iOS-only (PointerEntered/Moved/Left Mouse).
- [ ] **Step 1: Les bindings** (chaque source : install/teardown + annonce) ; gestureKinds == exactement les recognizers installés.
- [ ] **Step 2: Compiles ×4 targets + suites vertes. Commit** — `feat(uikit): deliver game-controller keyboard, gestures and hover bindings`.

---

### Task 4: Driver proof + contrats BCK-022/023 + INP-004?

**Files:** driver apps (probe counters étendus : touch/keyboard/gesture counts + dernier stimulus par kind), tests noms figés (`uikit-input-touch-mapped-from-real-recognizer` — via le probe counter déclenché par le binding lui-même à l'install? NON : le touch n'est stimulable en-process — les tests driver prouvent l'ANNONCE (InputObservationChanged après install, révoqué au teardown) + les mappers kernel couvrent la traduction ; touch réel = charter), registry (BCK-022 touch/pointer + BCK-023 clavier/gestes/télécommande — split par requiredTargets réel : touch/hover/gestes iOS-only? touch existe tvOS — vérifier par XML), evidence, validator, script asserts, capabilities.
- [ ] Scénarios : `uikit-input-sources-announced-after-install` (2 familles : annonce après install, révocation au teardown) ; `uikit-input-keyboard-gc-maps-keys` (mapper kernel via probe : GCKey simulé → KeyChanged, les deux cibles si GCKeyboard existe tvOS) ; `uikit-input-gestures-announce-exact-set` ; sentinelles : annonce-mute (mutation = teardown n'annonce pas la révocation), mapping-unknown (mutation = code inconnu approximé en HID → Unidentified test échoue).
- [ ] Can-fail cités (mutation seule, blast radius), gate vert, **UN commit d'activation** — `feat(uikit): activate input source contracts with both-families evidence`.

---

### Task 5: Docs closure

- DESIGN §15.2 (input livré phase 5 : touch/hover/gestes/clavier GC/télécommande GC ; clavier physique intégré = limite documentée ; text input = phase 6), BACKEND-CAPABILITIES §6.2 (livré + limites : addKeyCommand hors v1, XCUIRemote charter, press-route = first-responder non additive), PUBLIC-API-CATALOG (rien de nouveau public attendu — vérifier).
- Commit : `docs: close the phase 5 UIKit input perimeter`.

---

### Task 6: Sweep + PR

- Sweep à froid (runtime jvmTest, natives ×2, appkit, gate uikit all, consumers ×2 — background+polling).
- Checklist sortie phase 5 (roadmap l.268-269) : aucune touche/bouton/contact actif après reset/fermeture ; snapshots avant événements ; capacités == sources installées et limites documentées ; tvOS : consumer parcourable (charter pour le bout réel — XCUIRemote hors bundle unit).
- Rapport + push + PR (contrôleur).

---

## Séquencement et estimation

Tasks 1→2→3→4→5→6. T2 porte le spike (subclass recognizer) ; T3 = GameController ; T4 = preuves (probe+kernel, real-touch = charter). Budget attendu : ~30-40M tokens.
