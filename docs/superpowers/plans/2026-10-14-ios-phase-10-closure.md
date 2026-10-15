# iOS/tvOS Phase 10 — Fermeture du support, distribution et documentation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the iOS/tvOS support consumable, reproducible and auditable: finish `capabilities/uikit.md` per feature × family, activate or justify every deferred item (the registry centerpiece: BCK-018/BCK-019 delivered-de-facto but still `planned`), wire the Swift consumer into CI + a nightly with min/current OS versions, prove ARC retention cycles, and deliver the guides + samples that let a fresh consumer integrate without any `project(...)` dependency.

**Architecture:** Closure phase — no new platform behavior. The already-delivered pieces are assembled and audited: the PR gate exists (both `pull_request` jobs) and `kadre-pr-contracts` is branch-protection settings (nothing to build); the SwiftUI consumer exists in consumer-app; publications go through the existing contractTest maven repository (`-PkadreRepository`/`-PkadreVersion`, never `project(...)`). New artifacts follow the house molds: retention cycles mirror `AndroidLeakCyclesTest` (measurements, no invented contracts); the nightly mirrors `kadre-android-nightly.yml` (min/current matrix); guides follow the charter/manual genre plus README-per-module precedent; the samples mirror `android-kadre-tour` (shared srcDir + published artifacts + thin host).

**Tech Stack:** Kotlin Multiplatform 2.4.20, XcodeGen + xcodebuild, Gradle contractTest repository, GitHub Actions (xcode-27 runners), the existing `KadreUikitSurfaceDriver` counters as retention-measurement infrastructure.

**Spec:** `kadre/IOS-IMPLEMENTATION-ROADMAP.md` Phase 10 l.331-343 + §8 closing checklist l.396-411 (quoted verbatim in the research). Exit criterion l.343: "un consumer neuf peut intégrer le parcours documenté sans dépendance `project(...)` vers les sources Kadre ; toutes les obligations du §8 sont démontrées."

## Global Constraints

- Worktree `/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f` (branch `zcode/sess-56645e3f`, base 8ed94a54). Gradle from inside the worktree; ≤9-min slices, background+poll. **Anti-runaway lesson (phases 8): verify `git log --oneline -1` at task start; unexpected commits = STOP, report BLOCKED.**
- **No new platform behavior.** Any task that finds itself adding UIKit bindings has left scope — stop and report.
- Free ids: BCK-029+ (WIN-010..012 taken by Wayland; next WIN is WIN-013). Registry today: 105 active / 4 planned (BCK-018, BCK-019, WIN-008, APK-016). Phase 10 creates contracts ONLY for the BCK-018/019 activation (same-commit code+registry+scenarios+evidence rule); WIN-008 (Wayland) and APK-016 (AppKit) are other trains' items — note them in the audit as out-of-iOS-scope.
- **BCK-018/019 activation mechanics**: mirror the BCK-025..028 activations (`git show f99ae784 --stat` for the 4-file shape): status→active + scenarios + per-target evidence rows + sentinels + validator/gate wiring in ONE commit; evidence from the CONSUMER tests (published artifacts, not project(...)) — scenario ids = the consumer test names; JUnit strings from produced XMLs word-for-word.
- Consumer mechanics (existing): aggregator `kadre/consumers/swift` (never `project(...)`; `-PkadreRepository`/`-PkadreVersion` required; uikit must be published to contractTest FIRST via `:kadre:platform:uikit:publishAllPublicationsToContractTestRepository`; anti-duplication guard = `TEST SUCCEEDED` + zero "is implemented in both"; beware the stale-repository trap — wipe or `--refresh-dependencies`, phase-7 lesson).
- tvOS sample honesty: the remote binding is NOT delivered (deferred, BACKEND-CAPABILITIES l.298) — the tvOS sample navigates via the SYSTEM focus engine over host-owned focusable controls coexisting with the Kadre surface (the phase-5 coexistence proof), never via a fabricated Kadre remote path. Saisie système per support = the delivered text-input path.
- Nightly honesty: CI runners carry OS 27.0 runtimes; the declared floors are iOS/tvOS 16.0 (uikit.md l.119-120). If older runtimes are unavailable on the runner, the nightly runs the OLDEST AVAILABLE runtime + documents the gap against the declared floor (never fabricates a min-OS proof). Deployment targets stay declared in docs; the nightly matrix = {oldest available, current}.
- Guides are NEW artifacts (no delivery guide exists for ANY platform) — place under `kadre/guides/`; genre: honest integration docs (house register: « décisions, pas des promesses »), cross-referencing capabilities/uikit.md for per-feature truth and the manual charters for hardware truths. No marketing.
- Retention cycles mirror `AndroidLeakCyclesTest`: measurement table in KDoc (cycle family × repetitions × at-rest assertion), bounded polls (never bare sleeps), NO invented registry contracts; the at-rest truth source = the exported driver counters + port-level close-count probes.

## Review Focus

1. **Planned-presented-as-delivered** — any doc claiming BCK-018/019 (or any `planned` contract) as delivered before the same-commit activation lands. Test: T2 registry diff + T8 audit cross-check.
2. **Silent project(...) dependency** — any new consumer/sample resolving Kadre sources instead of published artifacts. Test: grep in T4/T6/T7 deliverables.
3. **Fabricated min-OS proof** — the nightly claiming iOS/tvOS 16.0 coverage without the runtime existing. Test: T5 workflow + docs wording.
4. **Retention assertion restating counters** — a cycle test whose at-rest assert cannot fail (e.g. asserting a counter equals itself). Test: T3 mutation analysis.
5. **Guide drift** — a guide contradicting capabilities/uikit.md (the per-feature truth source). Test: T6 cross-check against the table.

---

### Task 1: capabilities/uikit.md final — per-feature × family

**Files:**
- Modify: `kadre/capabilities/uikit.md` — (a) add the missing tvOS remote/focus row(s) (delivered = NOTHING: GCMicroGamepad binding non livré, UIFocusEnvironment observation hors périmètre v1 — honest rows with the BACKEND-CAPABILITIES l.298 citations); (b) split rows that merge iOS+tvOS under one line while the availabilities differ (the drop precedent l.246/247: one row per family when the truth differs; where a single row is kept, the per-family truth must live in structured columns, not buried in état-absent prose — decide per row and justify each kept merge in the row itself); (c) re-confirm the §3 framework-header audit against the current header (the scaffolding-visible rationale + INTEROP §1/§2 tolerated-generated detail — re-run the check, refresh the counts, keep the F-2 CI note); (d) verify every §2-roadmap domain (l.72-81) and every BACKEND-CAPABILITIES §4 UIKit cell has exactly one reading path in the table.
- [ ] Steps: row inventory vs §2/§4 domains → edits → cross-check script (grep the domain keywords) → commit `docs(uikit): phase 10 final capability table — one entry per feature and family`.

### Task 2: BCK-018/BCK-019 activation (the deferred-revision centerpiece)

**Files:**
- Modify: `kadre/contracts/registry/contracts.tsv` (BCK-018, BCK-019: planned→active with scenarios/evidence ids), `kadre/contracts/validator/build.gradle.kts` (uikitContractIds += both), `scripts/test-uikit-simulator.sh` (arrays), `kadre/contracts/driver/uikit/contracts/evidence.tsv` (new rows).
- First READ both planned rows verbatim + their original intent (BCK-018 = Swift KadreHost facade; BCK-019 = consumers from their own artifacts) and the consumer test suite that constitutes their proof (`kadre/consumers/swift/tests/Shared/KadreUikitFacadeTests.swift`? `KadreSwiftUIConsumerTests.swift` — find the exact classes/methods; the scenario ids MUST be backed by real consumer tests run against PUBLISHED artifacts).
- Mechanics: run the full consumer aggregator (publish uikit to contractTest → consumer-kmp build → consumer-app xcodebuild test both families) → JUnit XMLs → evidence rows word-for-word → SAME-COMMIT activation. Sentinels: derive from what the consumer tests already prove (e.g. the anti-duplication guard = a no-duplication sentinel; the updateUIViewController-contractually-empty test) — honestly named, with the kill story documented (what mutation would each sentinel catch — argue from the existing asserts, run one mutation if cheap).
- If either contract's de-facto delivery does NOT satisfy its row's original scenario intent — do NOT activate; document the gap and record the contract as justified-still-planned in the audit (T8 consumes it).
- [ ] Steps: read rows + consumer tests → full consumer run both families → activation commit `feat(contracts): activate BCK-018 and BCK-019 — the Swift facade and consumer contracts` → validator green + fail-proof quoted.

### Task 3: ARC retention cycles (the uikit AndroidLeakCyclesTest)

**Files:**
- Create: `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitRetentionCyclesTest.kt` — KDoc measurement table (cycle family × repetitions × at-rest assertion); cycle families (mirror Android's 7 + uikit's specifics): (1) attach/stop ×10 (all five managers initialized, counters return to at-rest), (2) re-attach same view/window ×5, (3) drop offer/transfer cycles ×4 (source close, claim, budget), (4) capture surface stream cycles ×4 (open → N frames → stop → slot/inFlight at rest), (5) display/device/gamepad port attach-detach cycles ×4 each (listener registrations released — the counters), (6) memory-pressure claim cycles ×4 (host wired/terminated, source stays until last release), (7) text-input session open/close cycles ×5 (receiver installed/removed, no orphan composition), (8) window-request open/resolve/abandon cycles ×4 (no resurrected sessions). Bounded polls; at-rest asserts from `KadreUikitSurfaceDriver` counters + port close-counts; no registry contracts invented.
- Test mutation: one representative at-rest assert must be shown to fail on a leak-simulating mutation (e.g. skip a teardown call in a fake — record the failure).
- [ ] Steps: table first → tests → mutation proof → both families green → commit `test(uikit): phase 10 retention cycles — repeated open/close returns to at rest`.

### Task 4: Consumer CI wiring + façade audit

**Files:**
- Modify: root `kadre/build.gradle.kts` — add a `validateSwiftConsumer` task (macOS-guarded like the appkit gates) wiring the existing aggregator (publish uikit → consumer-kmp → consumer-app with the anti-duplication guard); NOT into `:kadre:check` (macOS-only, mirrors the appkit precedent) but discoverable + documented.
- Create: the façade audit artifact — verify exports/opt-ins/profiles/absence of stale-or-internal API in the PROMISED facade: script or documented check over the built framework header (`objc-header`/quicklook of the debugFramework) — the §3 header audit mechanized where cheap (theINTEROP-tolerated generated detail listed), the public-API surface diffed against the facade contract (KadreIos/KadreSession/KadreHostViewController/KadreHostView — grep the header for internal-name leaks `org.graphiks.kadre.internal` or omitted exports).
- Modify: `kadre/capabilities/uikit.md` l.256-259 — retire the F-2 follow-up note (the CI wiring now exists; state exactly what runs where).
- [ ] Steps: root task + dry run → header audit evidence → F-2 note retired → commit `feat(consumers): phase 10 swift consumer validation task and facade audit`.

### Task 5: Nightly workflow (min/current OS) + hardware-release charter index

**Files:**
- Create: `.github/workflows/kadre-uikit-nightly.yml` — `schedule` + `workflow_dispatch`; jobs mirror `kadre-android-nightly.yml` shape: (a) both simulator families at CURRENT runtime (27.0) running the gate script; (b) the retention-cycle test isolated by filter (the AndroidLeakCyclesTest pattern); (c) the OLDEST AVAILABLE iOS/tvOS runtime on xcode-27 runners (discover at workflow-authoring time; if only 27.0 exists, run 27.0 and the workflow/docs state the floor gap honestly — never fabricate a min-OS proof); document the deployment-target floors vs the nightly's exercised runtimes in uikit.md (the release-gate section).
- Modify: `kadre/capabilities/uikit.md` — a short "release gate" section: what runs nightly, what the physical-device charter before release requires (the TEST-STRATEGY §11.2 reference + the hardware charter index from the audit T8).
- [ ] Steps: workflow (dispatch-testable) → docs → commit `ci(uikit): phase 10 nightly — current and oldest-available runtimes, retention leg`.

### Task 6: Guides

**Files:**
- Create under `kadre/guides/`: `uikit.md` (attach, session, surface ownership, lifecycle), `swiftui.md` (KadreHostView, the contractually-empty updateUIViewController, gating), `multi-scene.md` (provider, requestWindow, correlation, tvOS mono-scene refusal), `installation.md` (XCFramework slices, contractTest publications, `-PkadreRepository`/`-PkadreVersion`, static-linking + the anti-duplication constraint, Obj-C prefix), `conditional-capabilities.md` (how to read capabilities/uikit.md, unavailable-vs-unsupported, the charter concept), `migration-from-kadre-old.md` (the old API → new mapping: ScreenCapturer→CapturePort, GamepadController→GamepadPort, UiKitWindow→KadreIos... source: API-MIGRATION.md's kadre-uikit rows + the delivered ports), `tvos.md` (mono-scene, remote honesty — system focus coexistence, saisie système per support, Simulator vs Apple TV physique procedures).
- Every guide cross-references capabilities/uikit.md as the per-feature truth source; hardware truths point to the charters. Register: decisions-not-promises.
- [ ] Steps: write guides → cross-check against capabilities/uikit.md (no contradictions — the review-focus lens) → commit `docs(guides): phase 10 iOS/tvOS delivery guides`.

### Task 7: Samples (minimal consumer + tvOS remote-navigable)

**Files:**
- Create: `kadre/samples/uikit-hello/` (or extend `kadre/consumers/swift` per roadmap l.159 — DECIDE: the roadmap names consumers/swift as the consumers home; a samples/ module mirrors android-kadre-tour's independence. Recommend: consumer-app IS the minimal sample — extend its README + a docs pointer instead of duplicating; if a distinct sample is wanted, follow the android-kadre-tour mold: shared srcDir + published artifacts + thin Swift host). Deliverable: the minimal-consumer path is DOCUMENTED end-to-end (a fresh consumer can follow installation.md + uikit.md to a running app with zero project(...)).
- Create: the tvOS remote-navigable sample — either a consumer-app tvOS scene extension or a small tracked sample: UIKit host with focusable native controls + the Kadre surface coexisting, navigable by remote (SYSTEM focus), text input via the delivered system path, controller conditional per hardware (documented). Honest: NO Kadre remote binding (deferred) — the sample proves coexistence, exactly what phase 5 proved.
- Wire: settings.gradle.kts if a new module; xcodegen + committed pbxproj if a new app; out of the root `check` allowlist (sample-driver rule).
- [ ] Steps: decide shape → build → both simulators navigable → README with the honest remote statement → commit `feat(samples): phase 10 minimal consumer path and tvOS coexistence sample`.

### Task 8: Deferred-revision audit

**Files:**
- Create: `kadre/DEFERRED-AUDIT-uikit.md` (or a section in capabilities/uikit.md — decide by house fit; the Android Phase-10 precedent added §5 coverage-audit INTO capabilities/android.md) — EVERY deferral from the research's 21-item inventory gets: implemented-with-proof (link the contract/scenario) OR structural absence justified (why no primitive can satisfy it + the activation path if one exists). Includes: the 4 planned contracts (BCK-018/019 → T2's outcome; WIN-008/APK-016 → out-of-iOS-scope note), the physical charters (touch/keyboard/IME/drop/capture/display/gamepad/rotation/FrontBoard/scale/AirPlay), the design watch items (GCKeyboard mono-consumer, scheduler mono-slot, APPLY_LADDER_STATE real commit path, header audit §3, remote/focus v1 decision, ScreenCaptureKit), F-2 (retired in T4).
- Rule enforced: no `planned` contract presented as delivered (BCK-018/019 must be active-by-then or justified).
- [ ] Steps: inventory → per-item verdicts with links → commit `docs(uikit): phase 10 deferred-revision audit`.

### Task 9: Full gate + fix round

`scripts/test-uikit-simulator.sh all` + both simulator suites + `:kadre:runtime:jvmTest` + `validateContractRegistry` + the FULL consumer aggregator (fresh publish + `--refresh-dependencies`) + `validateSwiftConsumer` dry run. Fix round for anything red (re-run discipline; never skip). Roadmap Phase 10 checkboxes ticked ONLY after gates green.
- [ ] Steps: gates → fixes → roadmap ticks (7 boxes l.333-341) → commit `docs(uikit): phase 10 roadmap ticks` (or fold into the last docs commit if clean).

## Self-Review

- Roadmap bullets l.333-341 → T1 (capabilities final, per-family), T2+T4 (consumers against publications + façade audit), T5 (per-PR gates already exist — verified, stated; nightly min/current), T3 (retention cycles), T6 (guides), T6+T7 (tvOS guide + remote-navigable sample), T8 (deferred audit). §8 checklist → the audit cross-references each item.
- Placeholders: the nightly's oldest-available runtime is a workflow-authoring-time discovery with a pinned honest fallback (document the gap); the BCK-018/019 activation has a pinned alternative (justified-still-planned if intent unsatisfied); the sample shape has a recommendation (extend consumer-app docs vs new module) — decided at task time with evidence.
- Review Focus → tests: planned-as-delivered (T2/T8 cross-check), project(...) (T4/T6/T7 greps), min-OS fabrication (T5 docs wording), restating-counter asserts (T3 mutation), guide drift (T6 cross-check).
