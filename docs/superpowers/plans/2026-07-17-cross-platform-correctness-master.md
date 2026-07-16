# Cross-platform Correctness Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct all nineteen reviewed defects and their associated portability and CI gaps outside Windows in one coordinated, contract-first campaign.

**Architecture:** Observable event-loop behavior is specified once in `kadre-test`, then implemented and verified independently by each backend. Eight ordered subplans produce reviewable, testable commits while preserving one integration branch and one final blocking validation matrix.

**Tech Stack:** Kotlin 2.4.0, Kotlin Multiplatform, JVM/FFM on JDK 25, AGP 9.0.0, Android emulator, Kotlin/Native iOS simulator, Kotlin/JS and Wasm, Xvfb, Weston, glibc, musl, Mesa llvmpipe, SwiftShader, GitHub Actions.

## Global Constraints

- Windows and Win32-specific behavior are out of scope.
- Linux support is contractual on representative glibc and musl images.
- Deterministic tests are blocking; hardware-only GPU checks may be explicitly skipped.
- Pull-request CI must keep a critical path at or below 30 minutes.
- Kadre libraries retain `iosX64`; only the Compose sample removes it.
- Approved breaks are limited to the five changes recorded in the design spec.
- Any additional public or behavioral break requires user approval before implementation.
- Every shell command in this repository is prefixed with `rtk`.
- Follow TDD: failing test, observed failure, minimal implementation, passing test, commit.

---

## Source of truth

- Design: `docs/superpowers/specs/2026-07-17-cross-platform-correctness-design.md`
- Baseline commit: `d79814f3`
- Design commit: `756659d8`

## File and responsibility map

| Plan | Responsibility | Primary paths |
| --- | --- | --- |
| 01 | Common observable contracts and reusable assertions | `kadre-core`, `kadre-test` |
| 02 | AGP 9/KMP build foundation | `buildSrc`, `gradle`, KMP build scripts |
| 03 | Android surface, scheduler, close, metrics | `kadre-android`, Android sample instrumentation |
| 04 | Portable POSIX wake-up and Wayland correctness | `ffi/posix`, `ffi/wayland`, `kadre-wayland` |
| 05 | X11 wake-up, redraw, close, cursor, errors | `kadre-x11` |
| 06 | AppKit and UIKit lifecycle, scheduling, cleanup | `kadre-appkit`, `kadre-uikit` |
| 07 | Web coordinate, DPR, scheduling, close, cursor | `kadre-web-common` |
| 08 | Samples, deterministic captures, docs, CI gates | `samples`, `ci/linux`, `scripts`, `docs`, `.github/workflows` |

## Dependency graph

```text
01 contracts
  -> 02 build foundation
      -> 03 Android
      -> 04 POSIX + Wayland -> 05 X11
      -> 06 Apple
      -> 07 Web
03 + 04 + 05 + 06 + 07
  -> 08 samples, CI, docs, release validation
```

## Ordered execution

### Task 1: Execute the contract plan

**Files:**
- Follow: `docs/superpowers/plans/2026-07-17-cross-platform-correctness-01-contracts.md`

**Interfaces:**
- Produces: `ObservedCallback`, `RecordingApplicationHandler`, `assertIterationOrder`, `assertNoEventsAfterClose`, and `assertWakeUpRearms` in `kadre-test`.

- [ ] **Step 1: Execute every checkbox in plan 01**

Run its commands exactly and retain its commits.

- [ ] **Step 2: Run the contract gate**

Run: `rtk ./gradlew :kadre-core:allTests :kadre-test:allTests --no-daemon --stacktrace --console=plain`

Expected: `BUILD SUCCESSFUL`; the contract helpers pass on JVM, JS, Wasm, and available Apple targets.

### Task 2: Execute the build-foundation plan

**Files:**
- Follow: `docs/superpowers/plans/2026-07-17-cross-platform-correctness-02-build-foundation.md`

**Interfaces:**
- Consumes: unchanged public Kadre contracts from plan 01.
- Produces: AGP 9-compatible KMP modules and `androidHostTest`/`androidDeviceTest` source sets.

- [ ] **Step 1: Execute every checkbox in plan 02**

- [ ] **Step 2: Run the build-foundation gate**

Run: `rtk ./gradlew projects :kadre-core:build :kadre-android:build :samples:hello-window-android:assembleDebug --no-daemon --stacktrace --console=plain`

Expected: `BUILD SUCCESSFUL` with no legacy KMP/`com.android.library` compatibility warning and no obsolete Android property warning.

### Task 3: Execute the backend plans

**Files:**
- Follow in order:
  - `docs/superpowers/plans/2026-07-17-cross-platform-correctness-03-android.md`
  - `docs/superpowers/plans/2026-07-17-cross-platform-correctness-04-posix-wayland.md`
  - `docs/superpowers/plans/2026-07-17-cross-platform-correctness-05-x11.md`
  - `docs/superpowers/plans/2026-07-17-cross-platform-correctness-06-apple.md`
  - `docs/superpowers/plans/2026-07-17-cross-platform-correctness-07-web.md`

**Interfaces:**
- Consumes: contract helpers from plan 01 and build/source-set layout from plan 02.
- Produces: conformant in-scope backends with platform regression tests.

- [ ] **Step 1: Execute plans 03, 04, 06, and 07 after the common foundation**

These four backend domains are independent after plans 01–02. Plan 05 starts only
after plan 04 task 1 provides `ffi:posix`. Integrate one green domain at a time and
rerun the shared host gate after each integration.

- [ ] **Step 2: Run the cross-backend host gate**

Run:

```bash
rtk ./gradlew \
  :kadre-core:jvmTest \
  :kadre-test:jvmTest \
  :kadre-appkit:jvmTest \
  :kadre-x11:jvmTest \
  :kadre-wayland:jvmTest \
  :kadre-web-common:allTests \
  :kadre-android:testAndroidHostTest \
  --no-daemon --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL` and no ignored deterministic failure.

### Task 4: Execute samples, CI, and documentation plan

**Files:**
- Follow: `docs/superpowers/plans/2026-07-17-cross-platform-correctness-08-samples-ci-docs.md`

**Interfaces:**
- Consumes: all corrected backends and their final test task names.
- Produces: blocking deterministic workflows, verified captures, support docs, release notes, and API dumps.

- [ ] **Step 1: Execute every checkbox in plan 08**

- [ ] **Step 2: Run the final local matrix**

Run:

```bash
rtk ./gradlew \
  :kadre-core:jvmTest \
  :kadre-core:iosSimulatorArm64Test \
  :kadre-test:allTests \
  :kadre-appkit:jvmTest \
  :kadre-uikit:iosSimulatorArm64Test \
  :kadre-android:build \
  :kadre-web-common:allTests \
  :kadre:build \
  :samples:compose:shared:compileKotlinIosArm64 \
  :samples:compose:shared:compileKotlinIosSimulatorArm64 \
  --no-daemon --stacktrace --console=plain
rtk scripts/test-linux-container.sh glibc
rtk scripts/test-linux-container.sh musl
rtk scripts/android-emulator-test.sh
rtk scripts/test-appkit-runtime.sh
rtk scripts/test-uikit-simulator.sh
rtk scripts/test-web-browsers.sh
```

Expected: every command exits 0; capture verifier reports all mandatory PNGs present, decodable, correctly sized, and non-background.

### Task 5: Audit traceability and repository cleanliness

**Files:**
- Verify: `docs/superpowers/specs/2026-07-17-cross-platform-correctness-design.md`
- Verify: all eight plan documents.

**Interfaces:**
- Consumes: completed implementation and test reports.
- Produces: a 19/19 closure report.

- [ ] **Step 1: Record one test or native validation for every finding**

Use this exact format in the PR description or release report:

```markdown
| Finding | Test or validation | Result |
| ---: | --- | --- |
| 1 | `AndroidSurfaceLifecycleTest.rawHandleIsValidInsideCanCreateSurfaces` | PASS |
```

Repeat rows 1 through 19; include Xlib thread confinement, musl, `safeArea`, UIKit, Compose, and visual-gate rows.

- [ ] **Step 2: Verify no deterministic workflow masks failure**

Run: `rtk rg -n "continue-on-error: true|\|\| true|\|\| echo" .github/workflows scripts`

Expected: matches exist only in hardware-diagnostic steps explicitly documented as non-gating; none exist in deterministic test or capture steps.

- [ ] **Step 3: Verify worktree cleanliness**

Run: `rtk git status --short`

Expected: no output.

- [ ] **Step 4: Commit the final integration metadata**

```bash
rtk git add docs .github/workflows scripts
rtk git commit -m "chore: finalize cross-platform correctness gates"
```

Expected: a commit containing only final traceability, workflow, script, and documentation adjustments.
