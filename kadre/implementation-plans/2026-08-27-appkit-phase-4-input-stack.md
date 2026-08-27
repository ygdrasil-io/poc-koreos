# AppKit Phase 4 — Input essentiel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Activer clavier, pointeur souris et scroll AppKit dans `HostSurface.input`, avec état révisionné, policies bornées et preuve native O3.

**Architecture:** Les callbacks d’une sous-classe `NSView` produisent des stimuli AppKit sans adresse native. Le driver les sérialise par session et le runtime reste seul propriétaire des IDs, stamps, révisions, réduction de `SurfaceInputState`, coalescing et terminalisation. Deux prérequis KFFI, implémentés dans leurs dépôts respectifs mais planifiés ici seulement, ferment l’acquisition de first responder et l’injection scroll O3 avant l’activation publique.

**Tech Stack:** Kotlin Multiplatform JVM 25, coroutines `Flow`/`StateFlow`, AppKit, KFFI Objective-C public, Gradle, `kotlin.test`.

**Spec:** `kadre/APPKIT-PHASE-4-INPUT-DESIGN.md`; `kadre/DESIGN.md` §10.1; `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md` Phase 4.

## Global Constraints

- Ne pas modifier l’API publique Kotlin : `SurfaceInput`, `InputEvent` et `InputCapabilities` du catalogue fermé sont les seules formes exposées.
- N’activer que `keyboard` et `pointer`; touch, gestures, IME, drop, raw input, devices et pointer capture restent `Unsupported`.
- Kadre n’ajoute ni FFI, ni `Linker`, ni `MemorySegment` natif, ni block Objective-C : KFFI est l’unique owner des bindings.
- `KFFI-OBJC-004` et `KFFI-OBJC-005` doivent être publiés avant `APK-005`; ne jamais contourner un gap par un faux test ou un mapper appelé directement.
- L’état input est publié avant son événement; le reset de focus est unique, atomique et ne synthétise aucune release.
- Les callbacks sont session-local, non bloquants et révoqués avant la destruction de `NSView`.
- Les plans KFFI/Kextract restent dans Kadre; aucun plan n’est ajouté à ces dépôts.
- Tests significatifs uniquement : reducer, ordre, reset, overflow, isolation, callback native et preuve O3. Aucun test de getter ou de délégation tautologique.

---

### Task 0: Fixer les contrats, prérequis et evidence inactive

**Files:**
- Modify: `kadre/KFFI-REQUIREMENTS.md`
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Create: `kadre/APPKIT-PHASE-4-INPUT-DESIGN.md`

**Consumes:** catalogue fermé, policy input, règles O1/O2/O3.

**Produces:** `KFFI-OBJC-004`, `KFFI-OBJC-005`, `INP-001` et `APK-005` réservés mais inactifs, avec scénarios/sentinelles nominaux et frontière scroll explicite. Le registre a une seule source : ses colonnes `scenarios` et `sentinels`; aucun fichier `scenarios.tsv` ou `sentinels.tsv` n'existe.

- [ ] **Step 1: Ajouter les deux lignes `planned` au registre.**

  `INP-001` (O2) réserve `runtime-input-key-pointer`, `runtime-input-scroll-coalescing`, `runtime-input-focus-reset` et `runtime-input-overflow-terminal`, avec les sentinelles `runtime-input-event-before-state`, `runtime-input-synthetic-release`, `runtime-input-scroll-boundary-loss`, `runtime-input-policy-bypass` et `runtime-input-post-terminal-stimulus`. `APK-005` (O3) réserve `appkit-input-public-activation`, `appkit-input-native-key-pointer`, `appkit-input-native-scroll`, `appkit-input-terminal` et `appkit-input-manual-harness`, avec les sentinelles `appkit-input-focus-not-acquired`, `appkit-input-event-before-state`, `appkit-input-stuck-state`, `appkit-input-scroll-boundary-loss`, `appkit-input-late-callback`, `appkit-input-cross-surface` et `appkit-input-policy-bypass`.

- [ ] **Step 2: Ajouter les gaps KFFI, le design Phase 4 et le registre `planned`.**
- [ ] **Step 3: Vérifier que le validator accepte les réservations sans capability active ni mapping de preuve.**
- [ ] **Step 4: Commit.**

### Task 1: Généraliser l'evidence CI aux contrats O2 et O3

**Files:**
- Modify: `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ContractEvidence.kt`
- Modify: `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/GenerateContractEvidence.kt`
- Modify: `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ContractEvidenceTest.kt`
- Modify: `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/GenerateContractEvidenceTest.kt`
- Modify: `kadre/contracts/validator/build.gradle.kts`

**Consumes:** le registre `planned` de Task 0 et `TEST-STRATEGY.md` §3, §9 et §10.

**Produces:** le générateur valide un contrat actif O2 ou O3 et reçoit l'adapter déclaré par son appel Gradle, au lieu de prétendre que toute evidence est O3 et `appkit-jvm`. Les tâches AppKit existantes restent identiques dans leur résultat.

- [ ] **Step 1: Écrire les RED du validateur : accepter une evidence O2 `runtime-jvm`, conserver O3 `appkit-jvm`, et rejeter un mapping ou un testcase manquant.**
- [ ] **Step 2: Faire passer `adapter` explicitement au générateur, supprimer la restriction O3 et conserver le target JVM contrôlé par le contrat.**
- [ ] **Step 3: Exécuter `:kadre:contracts:validator:jvmTest` et `:kadre:contracts:validator:generateAppKitContractEvidence`; vérifier les JSON existants.**
- [ ] **Step 4: Commit.**

### Task 2: Livrer `KFFI-OBJC-004` — retour managé `BOOL(id, SEL)`

**Files:**
- Modify in KFFI only: signature/router/trampoline managed Objective-C et tests ABI macOS.
- Modify in Kadre only after publication: dépendance résolue et compile proof AppKit.

**Consumes:** contrat `KFFI-OBJC-004` de Task 0 et guide `CONTRIBUTING.md` KFFI.

**Produces:** `ObjCMethodSignatures.Boolean` et `onBoolean`, fallback explicite, lifecycle/quiescence identique aux signatures existantes; une vue `NSView` managée répond réellement à `acceptsFirstResponder` sur macOS.

- [ ] **Step 1: Lire intégralement `CONTRIBUTING.md` KFFI et écrire le test ABI/macOS RED.**
- [ ] **Step 2: Implémenter le nouveau chemin KFFI sans modifier Kadre.**
- [ ] **Step 3: Exécuter les tests KFFI, consumer proof et macOS requis; créer la PR KFFI seulement sur demande explicite.**
- [ ] **Step 4: Après publication, rafraîchir Kadre et écrire la compile proof qui utilise la signature publique.**
- [ ] **Step 5: Commit Kadre de la preuve.**

### Task 3: Livrer `KFFI-OBJC-005` — injection scroll native testable

**Files:**
- Modify in Kextract/KFFI only si la source du binding doit évoluer.
- Modify in Kadre only after publication: preuve de disponibilité typée.

**Consumes:** gap `KFFI-OBJC-005` de Task 0 et guide `CONTRIBUTING.md` KFFI/Kextract applicable.

**Produces:** une voie KFFI publique typée qui injecte un scroll discret/précis avec phase et momentum dans `NSApplication`, accompagnée de son contrat ownership/thread et d’une preuve macOS.

- [ ] **Step 1: Identifier la primitive AppKit ou CoreGraphics exacte et écrire le test native RED dans le dépôt propriétaire.**
- [ ] **Step 2: Générer/implémenter le binding en respectant les guides propriétaires.**
- [ ] **Step 3: Prouver l’injection dans la file AppKit et les champs lus par `scrollWheel:`.**
- [ ] **Step 4: Publier KFFI selon l’autorité reçue, puis ajouter le compile proof Kadre.**
- [ ] **Step 5: Commit Kadre de la preuve.**

### Task 4: Construire le pipeline runtime O2 input

**Files:**
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/SurfaceCommandPort.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/MinimalWindowSurface.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/SessionRuntime.kt`
- Create: `kadre/runtime/contracts/evidence.tsv`
- Modify: `kadre/contracts/validator/build.gradle.kts`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Test: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowSurfaceTest.kt`
- Test: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`

**Consumes:** types `SurfaceInput` foundation, `KadrePolicy.input`, le générateur O2 de Task 1 et règles de Task 0.

**Produces:** stimuli input portables, reducer runtime, state-before-event, reset focus, lanes discrete/move/scroll et terminalisation `SourceOverflow`.

- [ ] **Step 1: Écrire les RED O2 pour key/repeat/unknown, pointer, scroll, reset et snapshot avant événement.**
- [ ] **Step 2: Ajouter les stimuli sans importer AppKit et relier `policy.input` à la session.**
- [ ] **Step 3: Implémenter le reducer et le scheduler par lanes, avec fusion additive et barrières phase/momentum.**
- [ ] **Step 4: Prouver overflow, collectors lents, fermeture et absence de release synthétique.**
- [ ] **Step 5: Activer `INP-001`, mapper chaque scénario et sentinelle à un testcase O2 exact dans `runtime/contracts/evidence.tsv`, puis brancher la génération d'evidence runtime au gate `:kadre:check`.**
- [ ] **Step 6: Exécuter les suites runtime ciblées et `:kadre:contracts:validator:generateRuntimeContractEvidence`, puis commit.**

### Task 5: Connecter peer et driver AppKit

**Files:**
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt`
- Test: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitSurfacePeerTest.kt`
- Test: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt`

**Consumes:** Task 2 KFFI API, Task 4 runtime stimulus ingress; Task 3 seulement pour l'injection scroll O3 ultérieure.

**Produces:** callbacks AppKit immutable, mapping native pur, `NSView` first responder/tracking area, route session-local, reset focus et teardown revocable.

- [ ] **Step 1: Écrire les RED peer pour first responder, mapping, unknown preservation, focus reset et revoke.**
- [ ] **Step 2: Installer les overrides/owners KFFI, mapping immédiat du borrowed `NSEvent` et tracking souris.**
- [ ] **Step 3: Router les stimuli via le FIFO pre-ready et serializer existants; étendre les ports déterministes.**
- [ ] **Step 4: Prouver multi-fenêtre, pré-commit, teardown et callback non bloquant.**
- [ ] **Step 5: Exécuter la suite AppKit ciblée puis commit.**

### Task 6: Activer l’API publique, O3 et cahier manuel

**Files:**
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`
- Modify: `kadre/contracts/validator/build.gradle.kts`
- Modify: `scripts/test-kadre-appkit-contracts.sh`
- Create: `kadre/backend/appkit/manual/Phase4InputHarness.kt`
- Create: `kadre/backend/appkit/manual/phase-4-input.md`

**Consumes:** KFFI prerequisites, Task 4 reducer et Task 5 peer/driver.

**Produces:** `InputCapabilities.keyboard/pointer = Available`, `APK-005` active, O3 injection through the AppKit queue, harness versionné; les autres capacités restent `Unsupported`.

- [ ] **Step 1: Écrire les RED publics et O3 qui postent key/mouse/scroll réels dans `NSApplication`.**
- [ ] **Step 2: Activer seulement keyboard/pointer après installation de l’observation native.**
- [ ] **Step 3: Ajouter les sentinelles : focus non acquis, event-before-state, stuck key/button, phase/momentum crossing, discrete coalesced, late callback, cross-surface et policy bypass.**
- [ ] **Step 4: Créer le harness manuel et enregistrer les limites matérielles sans faux pass.**
- [ ] **Step 5: Activer `APK-005`, mapper chaque scénario et sentinelle à un testcase O3 exact, ajouter `APK-005` à la génération Gradle et au script macOS qui exige les artifacts de tous les contrats AppKit actifs.**
- [ ] **Step 6: Exécuter validator, evidence, suite AppKit, runtime, Desktop et `:kadre:check --rerun-tasks`; commit.**

### Task 7: Audit final de la tranche

**Files:**
- Modify only if the audit finds a concrete defect.
- Test: `:kadre:runtime:jvmTest`, `:kadre:platform:desktop:jvmTest`, `:kadre:backend:appkit:jvmTest`, validator/evidence et `:kadre:check --rerun-tasks`.

**Consumes:** Tasks 0–6.

**Produces:** stack reviewable avec evidence complète, gate manuel honnêtement distinct et aucun capability optimistic.

- [ ] **Step 1: Auditer APIs publiques, ownership, callbacks, policies et no-FFI local.**
- [ ] **Step 2: Rejouer la matrice complète sans skip/retry.**
- [ ] **Step 3: Effectuer une review whole-stack et traiter ses findings selon le ledger.**
- [ ] **Step 4: Commit seulement les corrections exigées.**
