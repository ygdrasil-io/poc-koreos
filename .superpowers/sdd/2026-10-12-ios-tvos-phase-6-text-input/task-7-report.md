# Task 7 report — Cold end-to-end sweep of the phase 6 exit criteria (UIKit text input)

Worktree: `/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f` (branch `zcode/sess-56645e3f`).
Sweep HEAD: **`6195156b`** — `docs: close the phase 6 UIKit text-input perimeter`.
New commits from Task 7: **none** (the sweep found no defect; nothing to fix).

## 0. Sweep HEAD verification (step 1 of the sequence)

- `git log` at the worktree: `6195156b` (docs) → `02658b1a` (T4 contracts) → `83f19240` (T3 driver) → `34999fc0` (T2 fix) → `edfdf313` (T2 port) → `7ec701e1` (T1 shadow+spike) → `2f9a5cbd` (plan). Every delivered commit is on the branch.
- **Sha discrepancy in the brief, resolved:** the brief named `be309914` as "T5 docs". `be309914` is in the object store but is the **phase 5** docs commit (`docs: close the phase 5 UIKit input perimeter`, 2026-10-12 22:44) and is NOT an ancestor of HEAD. The phase 6 docs commit is the amended **`6195156b`** (2026-10-13 04:34, touches `BACKEND-CAPABILITIES.md` +2/+1 and `DESIGN.md` +1/-1), which IS HEAD. The sweep therefore ran at the correct HEAD — it includes all delivered work.
- `git status --short`: clean before the sweep; clean after (evidence JSONs live under `build/`, gitignored).

## 1. Suites executed at the final HEAD (step 2)

First invocation returned everything UP-TO-DATE (prior tasks had built this exact HEAD in this worktree), so all suites were **re-run forced (`--rerun-tasks`) to obtain genuine cold executions at `6195156b`**. `BUILD SUCCESSFUL in 1m 30s`.

| Suite | Result |
|---|---|
| `:kadre:runtime:jvmTest` | **408 tests, 0 failures, 0 errors, 0 skipped** |
| `:kadre:runtime:iosSimulatorArm64Test` | **83 tests, 0 failures** (iPad-class simulator runtime) |
| `:kadre:runtime:tvosSimulatorArm64Test` | **83 tests, 0 failures** |
| `:kadre:backend:appkit:appKitNativeTests` | **373 tests, 0 failures** (= jvmTest 371 + appKitStandaloneLoopTest 2) |
| `./scripts/test-uikit-simulator.sh all` | **`uikit gate (all): SUCCESS`, exit 0** — iOS: 41 tests, 0 failures; tvOS: 31 tests, 0 failures (41−31 = the 3 iOS-only extension suites of BCK-014/016/021); **72 test cases passed, 0 failed**; contract evidence REGENERATED for all 11 iOS + 8 tvOS contracts, including **BCK-024 on both families** |

Script arrays confirmed to carry BCK-024: `scripts/test-uikit-simulator.sh` l.62 `IOS_CONTRACT_IDS=(… BCK-024 WIN-009)` and l.63 `TVOS_CONTRACT_IDS=(… BCK-024 WIN-009)`; header comment lists BCK-024 (l.2). tvOS ran the full shared `KadreUikitTextInputTests` (all 8 scenarios passed on Apple TV 4K (3rd generation), OS 27.0) — the tvOS text-input path is validated **separately**, per the roadmap phase 6 bullet, over the same `UITextField` receiver (no iOS-only receiver/symbol copied; both klibs compile it — the compile gates are per-family).

Scope note: the brief's one-liner mentions « consumers ×2 » in the sweep; no `:kadre:consumers:*` Gradle module exists (the `kadre/consumers/{android,java,kotlin,swift,typescript,web}` tree is not in `settings.gradle.kts`), and the coordinator's authoritative sweep sequence does not include it. Nothing in the phase 6 diff touches a consumer surface beyond the driver probes (`KadreUikitSurfaceDriver.kt`), which the gate exercises.

## 2. Phase 6 exit checklist — `kadre/IOS-IMPLEMENTATION-ROADMAP.md` l.283-284

> **Critère de sortie :** une seule session texte active par surface, aucun remplacement fondé sur une révision périmée, aucune composition orpheline après fermeture. Le clavier logiciel ne redimensionne pas implicitement le layout détenu par SwiftUI.

All four clauses: **MET**. Fifth item (per coordinator checklist): IME réel = charter manuel — **documented**.

### (1) Une seule session texte par surface (open exclusif) — MET

Two seams, both proven:

- **Runtime admission** (the seam the public route actually exercises — the driver probe calls `surface.input.openTextInput(config)`, `KadreUikitSurfaceDriver.kt` l.717/739): `kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeSurfaceInput.kt`, `openTextInput` l.119-186 — the lock-protected admission `textInputSession != null || textInputOpening -> AlreadyInUse` (l.123, refusal l.137) **plus a post-open re-check** that refuses with `AlreadyInUse(TextInputSession)` if a concurrent open won the slot (l.176-183), then `session.close()` on the loser.
  - Runtime test: `RuntimeWindowSurfaceTest.kt` (jvmTest) `textInputAllowsOneSessionAndSerializesDocumentRevisions` (l.593): second `openTextInput` during a live session returns `KadreFailure.AlreadyInUse(TextInputSession)` (l.603-606) and only ONE port open happened (`assertEquals(1, port.opened.size)`); after `session.close()`, a re-open is admitted.
  - Driver test (both simulators): `KadreUikitTextInputTests.swift` `testUikitTextOpenExclusiveRejectsSecond` (l.330) — second open refused `AlreadyInUse(TextInputSession)`, `textInstallCount` unchanged ("the refused open must not install a second receiver"), first session stays `Active` at its revision with zero published events.
- **Port CAS** (defense in depth): `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitTextInputPort.kt` l.91-128 — `open` checks `closed || liveOwner != null` under the port lock (l.92), installs, then re-admits under the lock (l.114-121); the memoized per-surface instance (`UIKitWindowCommandPort.textInputPort`, `UIKitWindowCommandPort.kt` l.712-719, memo field l.185) is what makes the CAS hold across calls. Mirrors AppKit `KffiViewTextInputAdmission.active`/web `liveOwner`.

### (2) Aucun remplacement à révision périmée (stale refused) — MET

- **Shadow** (pure): `UIKitTextInputShadow.kt` l.54 — `if (revision.value < documentRevision.value) return UIKitTextInputDocumentUpdate.Stale`, leaving text/selection/revision untouched; `applyCursor` refuses any non-exact revision (l.41-47). Unit proof: `UIKitTextInputShadowTest.kt` — `applyDocumentRefusesAStaleSnapshotLeavingTheShadowUntouched` (l.121), `applyCursorRefusesAnyOtherRevisionWithoutTouchingTheStoredRect` (l.72), plus the idempotent same-revision restates (l.94, l.104).
- **Port**: `UIKitTextInputPort.applyDocument` (l.265-284) writes the FIELD only after the revision check can pass is not attempted — it writes the field first, then reconciles the shadow, and maps `Stale` to typed `KadreFailure.StaleRevision(expected, received)` (l.277-279); `applyCursor` same (l.249-256). Ordering note: field-first write-back is the web D-X3 order; a stale write-back that already touched the field cannot happen because the failure path is reached from the shadow check and the runtime rejects stale commands before the port (next bullet) — the driver test below pins the end-to-end behavior: field AND document untouched on refusal.
- **Runtime**: `RuntimeWindowSurfaceTest.kt` l.593 test — `updateCursor` at revision 1 over accepted 0 → `StaleRevision(expected = 0, received = 1)`; `updateSurroundingText("a", …, revision 0)` over accepted 1 → `StaleRevision(expected = 1, received = 0)`.
- **Driver (both simulators)**: `testUikitTextStaleRevisionRefused` (l.539) — advance to revision 5 ("ab"), then stale write-back at revision 3 refused as typed `KadreFailureStaleRevision(expected=5, received=3)`; field still "ab", runtime keeps revision 5, zero observations published.

### (3) Aucune composition orpheline après fermeture — MET

- **Driver (both simulators)**: `testUikitTextCloseNoOrphanComposition` (l.652) — programmatic `setMarkedText("é")` alive on the receiver (asserted before close), then `session.close()`: receiver **removed** (`!receiverAttached` probed with 5 s timeout), session state **`Closed`**, **zero** observations ever leave the session (`capture.totalCount == 0`), and a re-open is admitted with a fresh field carrying **no orphan mark** (`fieldMarkedLength == -1`, text != "é").
- **Runtime/port code**: `UIKitTextInputPort.close` (l.225-246) sets `closed` under the lock FIRST (every hook's `publish` guard, l.406-410, and every hook entry, e.g. `onShouldChange` l.305, checks it — hooks already in flight are rejected by the guard, never by half-torn native state), clears the pending edit, then resigns first responder and `removeFromSuperview` on the main; `UIKitTextInputPort.shutdown` (l.149-152) closes any surviving owner when the command port closes (`finishClose`, `UIKitWindowCommandPort.kt` l.737-742) — a session left open at surface teardown cannot leave a live field or a live observer.
- The unmark cleanup itself is covered unit-side by `UIKitTextInputShadowTest.clearMarkedText*` (l.237, l.244) and router-side by `UIKitTextInputEditRouterTest.theMarkedToNullTransitionCommitsTheCompositionExactlyOnce` (l.96).

### (4) Le clavier logiciel ne redimensionne pas implicitement le layout — MET

- **No layout code in the delivered diff**: `git diff 2f9a5cbd..HEAD -- kadre/platform/uikit kadre/runtime` greps for `layoutIfNeeded|setFrame|frame =|translatesAutoresizingMaskIntoConstraints|NSLayoutConstraint|keyboardWillShow|keyboardDidShow|UIKeyboard…Frame|contentInset|scrollRectToVisible|safeArea|addObserver.*keyboard|bounds` match ONLY `UIKeyboardType` keyboard-**layout-of-keys** mapping constants (`UIKitTextInputConfigMapping.kt` — a traits table, not screen layout). Zero keyboard-frame observers exist anywhere in `kadre/platform/uikit/src` (the only keyboard notifications are the phase 5 `GCKeyboardDidConnect/DidDisconnect` physical-keyboard bindings in `UIKitKeyboardBinding.kt` l.92-97).
- **The receiver is never resized by Kadre**: constructed `UITextField(frame = CGRectMake(0.0, 0.0, 1.0, 1.0))` with `alpha = 0.0` (`UIKitTextInputReceiver.kt` l.70, l.96), and no code writes its frame/bounds/autoresizing afterwards (grep = 0 in the three text-input sources). Invisible via alpha (never `isHidden` — a hidden view cannot become first responder), 1×1 at origin, excluded from touch by UIKit's alpha threshold. The driver pins this on the live hierarchy: `testUikitTextOpenExclusiveRejectsSecond` asserts `receiverAlpha == 0.0 (accuracy 0.001)` and attachment inside the host hierarchy.
- SwiftUI's layout is untouched by construction: Kadre's only subview is this 1×1 alpha-0 field inside the host view it was given; there is no safe-area, content-inset or keyboard-frame coupling.
- **Documented**: `kadre/BACKEND-CAPABILITIES.md` l.246 — « le clavier logiciel ne redimensionne rien (aucune notification keyboard-frame observée, le layout de la surface reste au host) »; same statement in `kadre/DESIGN.md` §15.2 progression bullet (commit `6195156b`) and in the `capabilities/uikit.md` BCK-024 registry row (l.167).

### (5) IME réel = charter manuel (documented) — MET

- `kadre/BACKEND-CAPABILITIES.md` l.246: « les livraisons du clavier logiciel réel et de l'IME réel (langues, compositions vivantes) = charters manuels (pas synthétisables in-process honnêtement) », with the v1 composition semantics right next to it: « la composition v1 est une réception sans publication — le texte marqué vit dans le receiver, le commit IME ne publie rien et l'ombre est resynchronisée d'un bloc à ce commit ».
- `kadre/capabilities/uikit.md` §2 BCK-024 row (l.167): same charter statement in the registry (état absent column), plus the focus→Suspend charter leg.
- `kadre/DESIGN.md` §15.2 (docs commit `6195156b`): « l'IME réel et les langues réelles restent des charters manuels ».
- The deterministic branches that CAN be proven are proven in-process: composition reception + commit-time shadow resync (`UIKitTextInputEditRouterTest` l.88/96/110, driver `testUikitTextCloseNoOrphanComposition` marked-path, spike `KadreUikitTextInputSpikeTests.testUITextFieldProgrammaticEditingSpike` — setMarked/unmark round-trip, emoji surrogate-pair delete, on both simulators).

## 3. Self-review

- Re-read the exit-criteria sentence against the evidence above — all four clauses hold at `6195156b` with both runtime-level and device-level proof, on both families.
- Re-checked the two hardenings from `34999fc0` are still pinned: echo never consumes a pending candidate (`EchoDrop` wins first in `UIKitTextInputEditRouter.onTextDidChange`, l.482-501; tests `echoDropsThePendingInsteadOfConsumingIt`, `echoWinsOverEverything`), and the IME commit resyncs the shadow exactly once (`CompositionCommit`; test l.96).
- No defect found: **no fix commit needed**; the working tree is exactly `6195156b` (clean).
- Concern (informational, not a deliverable defect): the task brief named `be309914` as the docs commit; that sha is the phase 5 docs commit. The actual phase 6 docs commit is `6195156b` (HEAD) — verified to carry the same content (2 doc files). The sweep ran at the correct HEAD.
- Concern (scope note): brief's « consumers ×2 » has no corresponding Gradle module; not part of the coordinator's sweep sequence; no consumer-facing code changed in phase 6.

## Verdict

Phase 6 exit criteria (roadmap l.283-284) are **satisfied and evidenced** at HEAD `6195156b`. All suites and both simulator gates green at final HEAD. Ready for push + PR (controller).
