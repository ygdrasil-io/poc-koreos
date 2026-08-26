# AppKit Phase 3 Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose a complete, observable AppKit `Window.surface` and coalesced redraw without adding rendering or input.

**Architecture:** Keep snapshot/revision and redraw state in the portable runtime. The AppKit peer captures native values on the main thread and emits immutable surface stimuli; the session-local driver serializes them into the runtime. Public activation and `APK-004` occur only after the native path is complete. A small external AppKit harness records manual-only observations.

**Tech Stack:** Kotlin/JVM, coroutines `StateFlow`/`Flow`, AppKit through public KFFI, Gradle contract validator.

**Spec:** `kadre/APPKIT-PHASE-3-SURFACE-DESIGN.md`

## Global Constraints

- AppKit only; do not add Android, Web or UIKit columns, APIs or tests.
- No renderer, widgets, keyboard, pointer, scroll, IME, touch, drag-and-drop or advanced window mutation.
- AppKit callbacks never publish to flows or call consumers under an internal lock.
- The process broker remains lifecycle-only; every surface peer and serializer is session-local.
- Native KFFI access uses public typed APIs only; a missing safe API stops the task and creates one `KFFI-OBJC-*` requirement rather than a Kadre FFI workaround.
- A capability is `Available` only after its complete native effect and terminal behavior are proven. Everything else remains `Unsupported`.
- Never stage `kadre/implementation-plans/2026-08-25-kffi-objc-foundations.md`.

---

### Task 0: Establish Phase 3 evidence and the AppKit-only manual protocol

**Files:**
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`
- Modify: `kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md`
- Create: `kadre/backend/appkit/manual/phase-3-surface.md`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`

**Consumes:** Phase 2 `APK-003` conventions.

**Produces:** Reserved `APK-004` O3 scenario/sentinel identifiers and a non-duplicative AppKit manual checklist.

- [ ] **Step 1: Write the manual protocol first**

Document the six approved scenarios exactly: interactive resize; mixed-scale screen migration when available; focus/minimize/restore/occlusion; light/dark appearance; redraw bursts during resize/occlusion; and close during resize/redraw. Require macOS, architecture, display model, resolution, scale factor, appearance, build id, pass/fail/not-applicable and observation. Define unavailable second display/theme/visibility control as `not applicable`.

- [ ] **Step 2: Reserve evidence names**

Add inactive `APK-004` with O3 level. Reserve scenarios `appkit-surface-snapshot`, `appkit-surface-native-stimulus`, `appkit-surface-redraw`, `appkit-surface-terminal`, and `appkit-surface-manual-harness`; reserve sentinels for stale snapshot, event-before-state, redraw-not-coalesced, post-close callback and unsupported-capability activation.

- [ ] **Step 3: Validate documentation and registry**

Run: `rtk ./gradlew :kadre:contracts:validator:check`

Expected: GREEN; `APK-004` remains inactive and no Phase 3 capability is public.

- [ ] **Step 4: Commit**

```text
git add kadre/APPKIT-IMPLEMENTATION-ROADMAP.md kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md kadre/backend/appkit/manual/phase-3-surface.md kadre/contracts/registry/contracts.tsv kadre/backend/appkit/contracts/evidence.tsv
git commit -m "docs(appkit): define phase 3 surface evidence"
```

### Task 1: Implement the portable surface state machine and redraw ticket

**Files:**
- Replace: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/MinimalWindowSurface.kt`
- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/SurfaceCommandPort.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`
- Create: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowSurfaceTest.kt`

**Consumes:** `HostSurface`, `SurfaceState`, `SurfaceEvent`, `SurfaceCapabilities`, `WindowCommandPort`.

**Produces:** `RuntimeWindowSurface` and typed `SurfaceStimulus` ingress usable by one backend command port.

- [ ] **Step 1: Write failing runtime tests**

Cover initial effective metrics; one revision for an atomic metrics change; state visible before event; deduplication of equal stimuli; redraw coalescing until a port acknowledgement; redraw after detach returning `Closed(Surface)`; and terminal snapshot/closed operations. Test that cursor/hit-testing/default input are unsupported until a successful port outcome exists.

- [ ] **Step 2: Run RED**

Run: `rtk ./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeWindowSurfaceTest`

Expected: compilation failure for `RuntimeWindowSurface`/`SurfaceStimulus`.

- [ ] **Step 3: Implement the closed state machine**

Create `RuntimeWindowSurface` with a private lock, state flow, event flow and one `redrawPending` bit. Accept immutable stimuli only through the manager/port seam; set state/revision before emitting event; ignore equal/late/terminal stimuli. `requestRedraw()` sets the bit and calls `SurfaceCommandPort.requestRedraw(surfaceId)` exactly once until `redrawConsumed(surfaceId)` clears it. On detachment clear the ticket, close ingress and preserve the final state.

- [ ] **Step 4: Verify runtime behavior**

Run: `rtk ./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeWindowSurfaceTest --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest`

Expected: GREEN.

- [ ] **Step 5: Commit**

```text
git add kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime
git commit -m "feat(runtime): model surface snapshots and redraw"
```

### Task 2: Add the AppKit native surface peer and compile proof

**Files:**
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt`
- Create: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitSurfacePeerTest.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt`

**Consumes:** Task 1 typed surface stimuli and public KFFI `NSWindow`/`NSView`/notification APIs.

**Produces:** `AppKitSurfaceStimulus` emitted by one peer without direct runtime access.

- [ ] **Step 1: Compile the public KFFI proof before production code**

Write a macOS test which reads `NSView`/`NSWindow` content size, backing scale and effective appearance, installs/removes each needed observer, requests display/redraw, then closes owners. If any binding is absent or unsafe, add a precise KFFI requirement and stop.

- [ ] **Step 2: Write deterministic RED peer tests**

Use a recording native port to prove observer registration follows complete peer preparation; callbacks freeze values on main thread; callbacks only enqueue immutable stimuli; duplicate native updates collapse; observer revocation precedes view/window release; and callbacks after revocation have no effect.

- [ ] **Step 3: Implement peer observation**

Extend the peer with a closeable surface-observer owner. Map AppKit resize, backing scale, focus, visibility/occlusion and appearance changes to immutable stimulus values. Add one native redraw scheduler callback that returns completion to Task 1; revoke it before surface/view destruction.

- [ ] **Step 4: Verify native and deterministic proofs**

Run: `rtk ./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitSurfacePeerTest --tests org.graphiks.kadre.internal.appkit.KffiAppKitWindowPortMacOsTest`

Expected: GREEN on macOS; no raw native address leaves the backend.

- [ ] **Step 5: Commit**

```text
git add kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit
git commit -m "feat(appkit): observe native window surfaces"
```

### Task 3: Route surface stimuli through the session-local AppKit driver

**Files:**
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverFactory.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowSessionIntegrationTest.kt`

**Consumes:** Tasks 1–2.

**Produces:** one session-local surface ingress and ordered shutdown with no broker ownership.

- [ ] **Step 1: Write failing integration tests**

Prove two drivers cannot exchange surface stimuli or redraw acknowledgements; resize then close produces no late event; an in-flight redraw does not delay teardown; and native observation work never holds the runtime lock or joins the AppKit owner thread.

- [ ] **Step 2: Run RED**

Run: `rtk ./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitWindowSessionIntegrationTest`

Expected: FAIL because no surface routing exists.

- [ ] **Step 3: Implement driver routing**

Give each peer entry its surface id and route only its immutable surface/redraw stimuli through the existing session serializer. On teardown reserve surface cleanup with its peer, revoke native observers before the peer owner and reject every late stimulus. Do not add surface references to `AppKitProcessBroker`.

- [ ] **Step 4: Verify**

Run: `rtk ./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitWindowRuntimeDriverTest --tests org.graphiks.kadre.internal.appkit.AppKitWindowSessionIntegrationTest`

Expected: GREEN.

- [ ] **Step 5: Commit**

```text
git add kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverFactory.kt kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowSessionIntegrationTest.kt
git commit -m "feat(appkit): route session-local surface stimuli"
```

### Task 4: Activate the public surface contract, O3 proof and harness

**Files:**
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`
- Modify: `kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md`
- Create: `kadre/backend/appkit/manual/Phase3SurfaceHarness.kt`
- Modify: `kadre/backend/appkit/manual/phase-3-surface.md`

**Consumes:** complete runtime/peer/driver behavior from Tasks 1–3.

**Produces:** active `APK-004`, O3 evidence and a manual AppKit harness; no Phase 4 capability.

- [ ] **Step 1: Write public RED contract tests**

From a public AppKit session and real window, assert state before each resize/focus/redraw event; one redraw event for a burst; terminal state/no late events after close; and unsupported phase-4 capabilities. Add a real O3 macOS test that causes resize and redraw through AppKit, without accessing the driver or a fake port.

- [ ] **Step 2: Implement final capability activation**

Publish only complete surface capabilities. Register `APK-004` as active and map every scenario/sentinel to an exact test. The harness records all required manual metadata and shows snapshots/events; it must remain an external AppKit executable, not Kadre rendering.

- [ ] **Step 3: Run the complete matrix**

Run: `rtk ./gradlew :kadre:runtime:jvmTest && rtk ./gradlew :kadre:platform:desktop:jvmTest && rtk ./gradlew :kadre:backend:appkit:jvmTest && rtk ./gradlew :kadre:contracts:validator:check && rtk ./gradlew :kadre:contracts:validator:generateAppKitContractEvidence && rtk ./gradlew :kadre:check`

Expected: GREEN; `APK-004` has no skip/failure/error; `APK-003` stays green.

- [ ] **Step 4: Execute and record the manual checklist**

Run the harness on one supported macOS configuration. Record each scenario with all mandatory metadata; mark environment-limited cases `not applicable`; attach failures to an issue/test before declaring the phase complete.

- [ ] **Step 5: Commit**

```text
git add kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt kadre/backend/appkit/contracts/evidence.tsv kadre/contracts/registry/contracts.tsv kadre/APPKIT-IMPLEMENTATION-ROADMAP.md kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md kadre/backend/appkit/manual
git commit -m "feat(appkit): activate observable window surfaces"
```

## Self-review

- Surface model, native peer, session routing, public activation, evidence and manual protocol each have a distinct task and review boundary.
- No task activates a capability before Task 4, and Task 4 explicitly retains every later capability as unsupported.
- The only native API discovery is Task 2 and its stop/upstream-KFFI rule is explicit.
- The plan contains no placeholder task or unspecified test command.
