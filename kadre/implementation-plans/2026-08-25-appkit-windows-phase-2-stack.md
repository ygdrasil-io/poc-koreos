# AppKit Windows Phase 2 — Stacked PR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** allow an AppKit `KadreSession` to admit `WindowManager.requestWindow`, create and own a real `NSWindow` plus its content `NSView`, publish a conforming `Window`, and tear it down without leaking a native handle, callback, request, or session resource.

**Architecture:** delivery is a five-PR stack after this plan. The first PR creates the internal injection seam through which a backend supplies session-owned managers. The second implements the portable request/commit/close state machine against a narrow native command port. The third supplies an AppKit peer and verifies its KFFI boundary. The fourth composes a private AppKit runtime driver and proves its session isolation through deterministic integration tests; it does not install that driver in sessions or expose it publicly. The fifth alone injects the exact driver into standalone and embedded sessions and makes the public capability available only when close interception, `stopWhenLastWindowClosed`, desktop-handle leases, reverse teardown, contract evidence, and real macOS proof are all present.

**Tech Stack:** Kotlin 2.4, kotlinx.coroutines, Kotlin/JVM 25, AppKit, `org.graphiks:kffi-objc:1.0.0-SNAPSHOT`, Gradle, and the Kadre contract registry.

**Spec:** [`../DESIGN.md`](../DESIGN.md), [`../OPERATION-CONTRACTS.md`](../OPERATION-CONTRACTS.md), [`../BACKEND-CAPABILITIES.md`](../BACKEND-CAPABILITIES.md), [`../TEST-STRATEGY.md`](../TEST-STRATEGY.md), [`../APPKIT-IMPLEMENTATION-ROADMAP.md`](../APPKIT-IMPLEMENTATION-ROADMAP.md), [`../APPKIT-JVM-FIRST-IMPLEMENTATION.md`](../APPKIT-JVM-FIRST-IMPLEMENTATION.md), and [`../KFFI-REQUIREMENTS.md`](../KFFI-REQUIREMENTS.md).

## Global constraints

- No public API is added or reshaped. The existing `WindowManager`, `WindowRequest`, `Window`, `HostSurface`, `Window.withDesktopHandle`, failures, outcomes, and capability types are the contract.
- `runtime` owns request admission, policy limits, cancellation boundaries, IDs, revisions, state publication, primary-window selection, and session teardown order. It imports neither AppKit nor KFFI.
- `backend:appkit` owns only main-thread marshalling, native `NSWindow`/`NSView` creation, managed Objective-C delegates, native close stimuli, and the temporary validity of native addresses.
- Kadre contains no raw FFM downcall, upcall, selector construction, generated binding, or hand-built Objective-C callback. Any unavailable KFFI surface blocks the dependent PR; record it in `KFFI-REQUIREMENTS.md` and deliver it upstream before continuing.
- A `WindowRequest` is private to Kadre until its native peer, content view, delegate, callback ownership, and initial snapshot are complete. A cancellation before this commit leaves no native window; after it, `WindowRequest.state`, `await()`, `Window.state`, and the eventual close outcome are authoritative.
- `WindowManagerState.windows` is in stable admission order. Its first live entry is `primary`; a new primary is chosen only when that window closes. Pending requests never enter the list.
- Phase 3 owns rich `HostSurface` observations and redraw. Phase 2 supplies the minimal attached surface required by `Window.surface`, keeps unimplemented surface operations explicitly unsupported, and never advertises Phase 3 capabilities early.
- Native callbacks copy only the necessary stimulus, never suspend or call application code, and contain every Kotlin failure before Objective-C regains control.
- Tests prove observable state, outcomes, lifetime, and native effects. Do not add tests that assert a private helper, source text, or mock call count.
- The user-owned untracked `kadre/implementation-plans/2026-08-25-kffi-objc-foundations.md` is excluded from every branch, commit, and PR in this stack.

## Stack topology

```text
origin/master
  └── PR 0  codex/appkit-phase-2-window-plan
        └── PR 1  codex/appkit-window-runtime-seam
              └── PR 2  codex/appkit-window-request-runtime
                    └── PR 3  codex/appkit-window-native-peer
                                └── PR 4  codex/appkit-window-backend-driver
                                └── PR 5  codex/appkit-window-close-handle
```

Open every PR as a draft against its immediate predecessor. After a predecessor merges, manually rebase and retarget its successor to `master`; do not rely on a hosting-provider automatic retarget. Each PR must remain green on its own tip. PRs 1–4 intentionally leave `WindowManagerCapabilities.requestWindow` unsupported in the session exposed to an application. PR 5 is the only capability activation and contract-status change.

---

## Task 0 — PR 0: record the reviewed phase boundary

**Branch:** `codex/appkit-phase-2-window-plan` from `origin/master`.

**Files:**

- Create: `kadre/implementation-plans/2026-08-25-appkit-windows-phase-2-stack.md`

**Produces:** one reviewable, normative implementation route for Phase 2 without changing runtime behavior.

- [ ] **Step 1: Verify the merged Phase 1 baseline.**

  Run:

  ```bash
  rtk ./gradlew :kadre:backend:appkit:jvmTest
  rtk ./gradlew :kadre:contracts:validator:check
  ```

  Expected: `APK-001` and `APK-002` remain active and all window APIs are still explicitly unsupported.

- [ ] **Step 2: Commit only the implementation plan.**

  ```bash
  git add kadre/implementation-plans/2026-08-25-appkit-windows-phase-2-stack.md
  git commit -m "docs(appkit): plan fundamental window stack"
  ```

---

## Task 1 — PR 1: inject session-owned backend managers without changing capability

**Branch:** `codex/appkit-window-runtime-seam` from PR 0.

**Files:**

- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeSessionComponents.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeHostController.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/SessionRuntime.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeHostControllerTest.kt`
- Create: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeSessionComponentsTest.kt`

**Consumes:** the existing unsupported managers and `RuntimeHostController` attach/termination ownership.

**Produces:** an internal factory, scoped to one `SessionRuntime`, that can replace a default unsupported manager without widening `KadreHost` or leaking any backend type into public signatures.

- [ ] **Step 1: Write failing tests for the session component seam.**

  Construct a controller with an internal `RuntimeSessionComponentsFactory` that supplies a recognisable `WindowManager`. From a real `KadreApplication`, capture `KadreScope.windows` and assert that it is exactly the session-owned instance; attach two sessions and assert that they receive distinct manager instances. Terminate one session and verify that only its closeable component is closed. The default public constructor must still expose `UnsupportedWindowManager`.

- [ ] **Step 2: Run the focused tests and observe the missing injection.**

  ```bash
  rtk ./gradlew :kadre:runtime:jvmTest \
    --tests org.graphiks.kadre.internal.runtime.RuntimeSessionComponentsTest \
    --tests org.graphiks.kadre.internal.runtime.RuntimeHostControllerTest
  ```

  Expected: compilation fails because no session-components factory is accepted or the injected manager is never visible through `KadreScope`.

- [ ] **Step 3: Add the smallest internal seam.**

  Define an internal `RuntimeSessionComponentsFactory` and `RuntimeSessionComponents` whose window component is a `WindowManager` plus an idempotent close action. Have `SessionRuntime` create it after its root coroutine scope exists and close it before `onTerminated` publishes final host detachment. Keep the existing unsupported components as the default factory. Add an internal `RuntimeHostController.withComponents(...)` for tests/backends; do not alter its public constructors or `KadreHost` SPI.

- [ ] **Step 4: Verify the seam and the whole runtime module.**

  ```bash
  rtk ./gradlew :kadre:runtime:jvmTest
  rtk ./gradlew :kadre:runtime:check
  ```

  Expected: the injected component is isolated by session and released exactly once; all ordinary hosts remain unsupported for windows.

- [ ] **Step 5: Commit the runtime-only seam.**

  ```bash
  git add kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeSessionComponents.kt \
    kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeHostController.kt \
    kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/SessionRuntime.kt \
    kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeHostControllerTest.kt \
    kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeSessionComponentsTest.kt
  git commit -m "feat(runtime): inject session-owned managers"
  ```

---

## Task 2 — PR 2: portable window request, commit, and logical closure runtime

**Branch:** `codex/appkit-window-request-runtime` from PR 1.

**Files:**

- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt`
- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/MinimalWindowSurface.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeProcessIds.kt`
- Create: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`

**Consumes:** the session component seam, `KadrePolicy.resources`, and the public window/surface value types.

**Produces:** a serialised runtime manager driven by a narrow command port. It can be exercised with a deterministic port, but is not yet attached to AppKit sessions and does not activate the public capability.

- [ ] **Step 1: Write the red tests as public observations.**

  Use a deterministic `WindowCommandPort` test driver which records admitted commands and completes them only when the test supplies a stimulus. Prove independently that:

  1. a newly admitted request is `Pending`, is absent from `WindowManagerState.windows`, and consumes `maxPendingWindowRequests`;
  2. cancellation before native commit terminalises it as `Cancelled`, removes it from the pending budget, and never creates a logical or native peer;
  3. a successful commit first publishes a `Window` in stable admission order and then terminalises the request as `OpenedHere` with that exact window;
  4. admission reserves one `maxWindowsPerSession` slot before native commit; saturation rejects a later request even while the first request is pending;
  5. pre-commit cancellation or rejection releases that window slot exactly once, successful handoff retains it, and closing a committed window releases it exactly once;
  6. closing the primary chooses the next live window without reordering surviving windows;
  7. `close()` detaches the requester and never changes an already committed window;
  8. a post-commit cancellation returns `TooLate` or `CancellationRequested` as specified, with the terminal state/outcome remaining authoritative; and
  9. closing the manager rejects or closes every uncommitted request, releases every reservation, then closes committed windows in reverse admission order and releases their retained slots.

- [ ] **Step 2: Run the test file and observe the missing runtime manager.**

  ```bash
  rtk ./gradlew :kadre:runtime:jvmTest \
    --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest
  ```

  Expected: compilation fails because `RuntimeWindowManager`, its command port, and its minimal surface do not exist.

- [ ] **Step 3: Implement the state machine, not an AppKit adapter.**

  Add `WindowCommandPort` with one request-open command, one close command, explicit commit/failure/native-close stimuli, and an idempotent closeable owner returned only after native preparation succeeds. `RuntimeWindowManager` serialises reducer transitions under one internal admission lock, allocates IDs and revisions internally, and reserves one `maxWindowsPerSession` slot as part of admission before sending the native-open command. A pre-commit cancellation, native preparation failure, or manager teardown releases that reservation exactly once; a successful `OpenedHere` handoff transfers it to the committed window; window close releases it exactly once; teardown releases all pending reservations before closing committed windows and then releases each retained slot. Saturation is rejected before native preparation. The manager also enforces `maxPendingWindowRequests` and publishes a composed `WindowManagerState` before every terminal request outcome that references a window. `MinimalWindowSurface` is attached while the window is open, has no Phase-3 feature capability, and becomes terminally detached on close. Do not expose native addresses here.

- [ ] **Step 4: Make each window operation honest.**

  During this phase, `Window.apply`, `requestAttention`, and `respondToCloseRequest` return their contractually permitted unsupported/closed results except for the minimal logical `close` path used by the manager. `WindowCapabilities` advertises only the structural capabilities proven by this PR; the manager itself remains unavailable to applications until PR 5.

- [ ] **Step 5: Verify all request interleavings and the runtime gate.**

  ```bash
  rtk ./gradlew :kadre:runtime:jvmTest \
    --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest
  rtk ./gradlew :kadre:runtime:check
  ```

  Expected: every red scenario is green, including the pre-commit cancellation and reverse-teardown assertions.

- [ ] **Step 6: Commit the portable window kernel.**

  ```bash
  git add kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt \
    kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt \
    kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/MinimalWindowSurface.kt \
    kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeProcessIds.kt \
    kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt
  git commit -m "feat(runtime): model window request lifecycle"
  ```

---

## Task 3 — PR 3: AppKit native peer and managed delegate ownership

**Branch:** `codex/appkit-window-native-peer` from PR 2.

**Files:**

- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt`
- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt`
- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt`
- Create: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeerTest.kt`
- Create: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt`
- Modify only if a real gap is found: `kadre/KFFI-REQUIREMENTS.md`

**Consumes:** published KFFI managed Objective-C instances, typed `NSRect`/`NSSize`, `NSWindowStyleMask`, `NSBackingStoreType`, `NSWindow.initWithContentRect_styleMask_backing_defer`, `NSView.initWithFrame`, `setContentView`, `setDelegate`, `makeKeyAndOrderFront`, and `close`.

**Produces:** one fully owned AppKit window peer, with a closeable managed `NSWindowDelegate`, but no connection to a public `KadreScope` yet.

- [ ] **Step 1: Compile the KFFI surface proof before any Kadre implementation.**

  Add a focused compile/runtime test that creates the default resizable titled style mask, builds a typed content rect from `WindowSpec.contentSize`, allocates `NSWindow`/`NSView`, creates an `ObjCManagedClass` implementing `NSWindowDelegate`, installs `windowShouldClose:` and `windowWillClose:`, and closes all owners. It must import only public KFFI APIs.

  ```bash
  rtk ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.KffiAppKitWindowPortMacOsTest.kffiWindowSurfaceCompilesAndClosesOnMacOs
  ```

  If this fails because an API is absent or unsafe, stop this PR: add one precise `KFFI-OBJC-*` row naming the class, selector/signature, ownership, blocked Kadre behavior, and acceptance proof; implement and publish the KFFI change under its `CONTRIBUTING` guide; then resume against the published artifact. Do not add a Kadre FFI workaround.

- [ ] **Step 2: Write the red peer-lifetime tests.**

  With a recording `AppKitNativeWindowPort`, verify a peer creates the window, content view, delegate, and callback owner before it reports itself prepared; a failed preparation releases every already-created resource in reverse order; native `windowShouldClose:` becomes a close-request stimulus but does not close optimistically; native `windowWillClose:` emits exactly one terminal native-close stimulus; and closing the peer first revokes delegate admission, then detaches the native delegate/view, then closes the native window and its KFFI owner.

- [ ] **Step 3: Run the peer tests and observe the absent bridge.**

  ```bash
  rtk ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitWindowPeerTest
  ```

  Expected: compilation fails because the port and peer do not exist.

- [ ] **Step 4: Implement the AppKit-only command port.**

  `KffiAppKitWindowPort` marshals every creation and close command to the AppKit main thread. It creates the `NSWindow`, content `NSView`, and a managed `NSWindowDelegate` before returning an `AppKitWindowPeer`; it never exposes a raw `MemorySegment` outside the backend. The delegate routes only immutable close stimuli by peer ID. Its owner is closed before release and no callback exception crosses Objective-C.

- [ ] **Step 5: Run deterministic and real-native proofs.**

  ```bash
  rtk ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitWindowPeerTest \
    --tests org.graphiks.kadre.internal.appkit.KffiAppKitWindowPortMacOsTest
  rtk ./gradlew :kadre:backend:appkit:check
  ```

  Expected: deterministic teardown ordering is proven, and macOS creates then closes a real `NSWindow` through public KFFI only.

- [ ] **Step 6: Commit the private native peer.**

  ```bash
  git add kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt \
    kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt \
    kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt \
    kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeerTest.kt \
    kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt
  git add kadre/KFFI-REQUIREMENTS.md
  git commit -m "feat(appkit): own native window peers"
  ```

---

## Task 4 — PR 4: build a private AppKit backend driver for integration tests

**Branch:** `codex/appkit-window-backend-driver` from PR 3.

**Files:**

- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt`
- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverFactory.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`
- Create: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt`
- Create: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowSessionIntegrationTest.kt`

**Consumes:** the runtime manager/command port and AppKit peer without broadening process-wide broker ownership or installing a public session component.

**Produces:** an `AppKitWindowRuntimeDriver`/factory that owns one `RuntimeWindowManager` and one native port for deterministic backend integration tests. It is deliberately private to the backend test/integration seam: it is not installed in `RuntimeSessionComponents`, is not returned by `KadreScope.windows`, and cannot change the public unsupported capability. PR 5 is the only task that injects this exact driver manager as the public windows component.

- [ ] **Step 1: Write the red session-isolation tests.**

  Construct two `AppKitWindowRuntimeDriver` instances using a shared process broker and two fake native ports. Drive one driver's internal manager to prepare/commit windows and verify that no state, peer, close stimulus, or teardown reaches the other driver. Repeat for standalone ownership. On driver/session closure, verify that uncommitted peers are aborted and committed peers close in reverse admission order. Add an explicit boundary assertion that the ordinary `KadreScope.windows` remains `UnsupportedWindowManager` and that neither driver is present in `RuntimeSessionComponents`.

- [ ] **Step 2: Run the focused tests and observe the missing factory wiring.**

  ```bash
  rtk ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitWindowSessionIntegrationTest
  ```

  Expected: no backend driver/factory exists yet, so the deterministic integration test cannot construct an AppKit-backed manager; the public runtime remains unsupported.

- [ ] **Step 3: Inject a session-local AppKit window factory.**

  Implement `AppKitWindowRuntimeDriver` and its factory as an explicit backend-owned composition object. It receives its own `AppKitNativeWindowPort`, constructs a `RuntimeWindowManager`, and forwards native stimuli only to that manager's ingress. Keep `AppKitProcessBroker` process-lifecycle-only and free of window/session/current-host references. Do not pass the driver through `RuntimeSessionComponents`, do not modify the public `KadreScope.windows`, and do not wire it into `AppKitBackendProvider` as a live application component. The factory is callable only from deterministic backend integration tests and from the PR 5 adapter that will perform the public injection.

- [ ] **Step 4: Preserve the public capability boundary.**

  The private driver may exercise creation, commit, and teardown, but the application-facing manager continues to return `Unsupported(RequestWindow)` and remains the default `UnsupportedWindowManager`. No intermediate PR may publish a half-conforming `OpenedHere` window or install the driver in `RuntimeSessionComponents`; PR 5 alone connects the exact driver manager to `KadreScope.windows` after all mandatory close/lifetime rules are present.

- [ ] **Step 5: Verify all AppKit regression tests.**

  ```bash
  rtk ./gradlew :kadre:backend:appkit:jvmTest
  rtk ./gradlew :kadre:contracts:validator:check
  ```

  Expected: existing standalone/embedded lifecycle proof stays green and the two-session window routing proof is deterministic.

- [ ] **Step 6: Commit the session wiring.**

  ```bash
  git add kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt \
    kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverFactory.kt \
    kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt \
    kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt \
    kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowSessionIntegrationTest.kt
  git commit -m "feat(appkit): add private window runtime driver"
  ```

---

## Task 5 — PR 5: activate public handoff, native close, leases, and contract proof

**Branch:** `codex/appkit-window-close-handle` from `codex/appkit-window-backend-driver` (PR 4).

**Files:**

- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt`
- Modify: `kadre/platform/desktop/src/jvmMain/kotlin/org/graphiks/kadre/platform/desktop/DesktopWindowHandle.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`
- Modify: `kadre/platform/desktop/src/jvmTest/kotlin/org/graphiks/kadre/platform/desktop/DesktopHostTest.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`
- Modify: `kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md`

**Produces:** the complete Phase 2 behavior, an active `APK-003` contract, and mandatory O2/O3 evidence. The manager's `requestWindow` capability now becomes structurally available for AppKit sessions only.

- [ ] **Step 1: Write the red public-contract tests.**

  Add tests that observe only public APIs after creating a real AppKit session:

  1. `requestWindow` returns `Success(WindowRequest)` and begins `Pending`; the request is cancelled before commit and leaves no native window or manager entry;
  2. a successful request becomes `OpenedHere`, its window is manager `primary`, and two windows retain their admission order while primary moves only after the first closes;
  3. a user native close produces `WindowEvent.CloseRequested`; the first accepted application response wins, reject keeps the native window open, and `windowWillClose:` produces one terminal close sequence;
  4. standalone `stopWhenLastWindowClosed` does not stop a headless session, arms after the first committed window, and proposes `HostRequested` exactly on the later nonempty-to-empty transition;
  5. `withDesktopHandle` receives matching AppKit window/view addresses only during a synchronous callback; a close waits for an active lease and subsequent access returns `Closed(Window)`; and
  6. session teardown closes pending requests, then committed windows in reverse admission order, before revoking their native delegates.

- [ ] **Step 2: Run the focused tests and observe the missing public handoff.**

  ```bash
  rtk ./gradlew :kadre:runtime:jvmTest \
    --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest
  rtk ./gradlew :kadre:platform:desktop:jvmTest \
    --tests org.graphiks.kadre.platform.desktop.DesktopHostTest
  rtk ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest
  ```

  Expected: tests fail because manager availability, close response routing, last-window arming, and handle leasing are not yet implemented.

- [ ] **Step 3: Implement only the contract-complete final handoff.**

  Make the AppKit factory expose its `RuntimeWindowManager` through `KadreScope.windows`. Implement close interception with the managed delegate: `windowShouldClose:` asks the runtime and returns `false` until an admitted `Accept` commits native closing; direct programmatic close follows the same terminal path without synthesising a user request. Add a per-peer lease counter so `withDesktopHandle` runs synchronously on the owner thread, close waits for leases already admitted, and no handle survives its callback. Feed committed-window transitions into standalone last-window arming; never treat pending requests as windows.

- [ ] **Step 4: Activate the capability and document exact non-support.**

  Publish `WindowManagerCapabilities.requestWindow = Available(setOf(OpenedHere))` only for active AppKit sessions. Publish `WindowCapabilities.platformAccess = Available(Unit)` for committed desktop windows. Keep every unimplemented property/update/surface capability explicitly unsupported. Add `APK-003` as `active` with O3 evidence level, enumerate all positive scenarios and sentinels in `contracts.tsv`, map each to its exact test in `evidence.tsv`, and update the two AppKit roadmap documents only for Phase 2.

- [ ] **Step 5: Run the full verification matrix.**

  ```bash
  rtk ./gradlew :kadre:runtime:jvmTest
  rtk ./gradlew :kadre:platform:desktop:jvmTest
  rtk ./gradlew :kadre:backend:appkit:jvmTest
  rtk ./gradlew :kadre:contracts:validator:check
  rtk ./gradlew :kadre:check
  ```

  Expected: no skipped/retried O3 native window proof, no native peer after pre-commit cancellation, no stale desktop handle, and green standalone plus embedded lifecycle regressions.

- [ ] **Step 6: Commit the only public activation.**

  ```bash
  git add kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt \
    kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt \
    kadre/platform/desktop/src/jvmMain/kotlin/org/graphiks/kadre/platform/desktop/DesktopWindowHandle.kt \
    kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt \
    kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt \
    kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt \
    kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt \
    kadre/platform/desktop/src/jvmTest/kotlin/org/graphiks/kadre/platform/desktop/DesktopHostTest.kt \
    kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt \
    kadre/backend/appkit/contracts/evidence.tsv kadre/contracts/registry/contracts.tsv \
    kadre/APPKIT-IMPLEMENTATION-ROADMAP.md kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md
  git commit -m "feat(appkit): open and close native windows"
  ```

## Final verification and PR creation

- [ ] Rebase each branch only onto its immediate merged predecessor; retain this plan and never stage the user-owned KFFI plan.
- [ ] On every branch tip run `rtk ./gradlew :kadre:check` plus the phase-specific focused tests before pushing.
- [ ] Create draft PRs with bases `master`, `codex/appkit-phase-2-window-plan`, `codex/appkit-window-runtime-seam`, `codex/appkit-window-request-runtime`, `codex/appkit-window-native-peer`, and `codex/appkit-window-public-handoff` respectively.
- [ ] In every PR description state its stack position, predecessor, successor, unchanged public capability state, and the exact test/contract evidence it adds.
- [ ] Request a review before merging each PR. Do not start Phase 3 until PR 5 has merged and `APK-003` is green in CI.
