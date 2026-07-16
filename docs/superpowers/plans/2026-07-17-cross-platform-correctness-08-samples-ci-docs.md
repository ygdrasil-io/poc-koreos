# Samples, Deterministic CI, and Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Turn corrected backends into a reproducible local/CI matrix, make mandatory captures fail honestly, validate Linux on glibc and musl, and document every approved contract/break.

**Architecture:** Thin platform driver scripts own emulator/compositor/browser setup and always return the underlying test exit code. Linux runs in pinned glibc/musl containers; platform jobs run in parallel with 25-minute hard caps. Visual checks separate deterministic functional validation (blocking) from hardware-dependent pixel comparison (informational with structured capability status).

**Tech Stack:** Gradle, GitHub Actions, Docker/OCI, Eclipse Temurin JDK 25 glibc/Alpine images, Xvfb, Weston, Android emulator/SwiftShader, iOS arm64 simulator, Chrome Headless, Skia/ImageIO, Kotlin ABI validation, Dokka.

## Global Constraints

- Complete plans 01–07 first.
- Windows workflows and Win32 code are out of scope and remain untouched.
- No deterministic command may use `continue-on-error`, `|| true`, `|| echo`, missing-output ignore, or an unstructured skip.
- Hardware-only pixel identity may remain informational, but build, lifecycle, output production, decode, dimensions, and non-empty content are blocking.
- All PR jobs start independently and set `timeout-minutes: 25`; the critical path target is under 30 minutes.
- The musl contract covers POSIX/X11/Wayland library runtime, not Compose/Skiko binaries that are published for glibc.
- Use `rtk` for shell commands and commit after each green task.

---

### Task 1: Implement and test real Compose `--window-capture`

**Files:**
- Modify: `samples/compose/desktop/build.gradle.kts`
- Modify: `samples/compose/desktop/src/main/kotlin/org/graphiks/kadre/samples/compose/desktop/Main.kt`
- Create: `samples/compose/desktop/src/main/kotlin/org/graphiks/kadre/samples/compose/desktop/PngValidation.kt`
- Create: `samples/compose/desktop/src/test/kotlin/org/graphiks/kadre/samples/compose/desktop/PngValidationTest.kt`
- Create: `samples/compose/desktop/src/test/kotlin/org/graphiks/kadre/samples/compose/desktop/WindowCaptureControllerTest.kt`

**Step 1: Write failing validation/controller tests**

Add `testImplementation(kotlin("test"))`. Test:

- missing path fails;
- zero-byte/non-PNG/truncated PNG fails;
- a solid-color image fails the non-background/diversity assertion;
- a valid 800x600 image with foreground content passes;
- renderer returning false throws and requests loop exit;
- renderer returning true without creating the requested file throws;
- success captures once, validates, disposes renderer, and exits.

Use temporary files and Java `ImageIO`; no native window is required for these tests.

**Step 2: Verify failure**

```bash
rtk ./gradlew :samples:compose:desktop:test --tests '*PngValidationTest' --tests '*WindowCaptureControllerTest'
```

Expected: classes/mode implementation missing.

**Step 3: Implement strict PNG validation**

`validatePng(path, expectedMinWidth, expectedMinHeight)` must assert:

1. regular file and size > 100 bytes;
2. exact eight-byte PNG signature;
3. `ImageIO.read` returns an image;
4. dimensions meet the minimum;
5. sampled pixels contain at least two colors and at least one non-transparent pixel.

Throw `IllegalStateException` with path and failed property; do not print success before all checks pass.

**Step 4: Wire `--window-capture` through the real renderer**

Make `runShowcase(capturePath: String? = null)`. After renderer creation/content/resize, request one redraw. On the first `RedrawRequested` in capture mode:

```kotlin
check(renderer.captureFrameToPng(capturePath)) {
    "window-capture renderer failed: $capturePath"
}
validatePng(capturePath, expectedMinWidth = 640, expectedMinHeight = 480)
renderer.dispose()
exit()
```

Use `try/finally` to dispose on failure. Keep the watchdog only as a deadlock safety net; it exits non-zero and is not the success path. Remove the unimplemented warning/zero exit.

**Step 5: Run host tests and real Weston capture**

```bash
rtk ./gradlew :samples:compose:desktop:test
rtk scripts/ci-wayland-runtime.sh ./gradlew :samples:compose:desktop:run --args="--window-capture samples/compose/desktop/build/visual/compose-desktop.window.png" --no-daemon --stacktrace
```

Expected: tests pass; command exits 0 only with a valid, non-background PNG.

**Step 6: Commit**

```bash
rtk git add samples/compose/desktop
rtk git commit -m "fix(compose): make window capture produce verified PNG"
```

---

### Task 2: Make Linux facade selection test actual connectivity

**Files:**
- Modify: `kadre/src/jvmMain/kotlin/org/graphiks/kadre/LinuxBackendDetector.kt`
- Modify: `kadre/src/jvmMain/kotlin/org/graphiks/kadre/EventLoop.jvm.kt`
- Modify: `kadre/src/jvmTest/kotlin/org/graphiks/kadre/LinuxBackendDetectorTest.kt`
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11EventLoop.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoop.kt`
- Create: `samples/linux-consumer-smoke/build.gradle.kts`
- Create: `samples/linux-consumer-smoke/src/main/kotlin/org/graphiks/kadre/samples/linux/Main.kt`
- Modify: `settings.gradle.kts`

**Step 1: Write failing selector tests with injected probes**

Extract a pure selector taking override/session variables plus `classAvailable` and `probe` functions. Assert:

- auto mode skips a loadable Wayland class whose probe cannot connect and chooses connectable X11;
- the inverse chooses Wayland;
- forced Wayland failure does not silently fall back and reports its native reason;
- neither connectable reports both attempted backends plus only `WAYLAND_DISPLAY`, `DISPLAY`, and `XDG_SESSION_TYPE` context;
- invalid override remains a descriptive argument error.

**Step 2: Verify current failure**

```bash
rtk ./gradlew :kadre:jvmTest --tests '*LinuxBackendDetectorTest'
```

Expected: current detector returns the first loadable class without connecting.

**Step 3: Add non-public stable JVM probes**

In each backend, add an internal top-level function with a stable JVM name:

```kotlin
@JvmName("probeBackend")
internal fun probeBackendForFacade(): String?
```

Return null on success; otherwise return a sanitized operation/cause string. X11 opens and immediately closes a display. Wayland connects and immediately disconnects. Resource cleanup is in `finally`. The facade reflectively calls `probeBackend` after class load.

**Step 4: Unwrap reflection failures correctly**

`EventLoop.runApp` must unwrap `InvocationTargetException`, preserve the backend exception as cause, and not report it as class absence. Auto selection occurs before invoking the chosen loop; forced backend errors remain terminal.

**Step 5: Add a minimal external-consumer sample**

The module depends on:

```kotlin
implementation(project(":kadre"))
runtimeOnly(project(":kadre-x11"))
runtimeOnly(project(":kadre-wayland"))
```

Its handler creates/closes one window and exits. This proves the facade alone does not magically package optional backends and that the documented runtime dependencies are sufficient.

**Step 6: Run selector and both native routes**

```bash
rtk ./gradlew :kadre:jvmTest
rtk scripts/test-x11-xvfb.sh ./gradlew :samples:linux-consumer-smoke:run --no-daemon
rtk scripts/ci-wayland-runtime.sh ./gradlew :samples:linux-consumer-smoke:run --no-daemon
```

Expected: unit selection and both real connection paths exit 0; missing display path exits non-zero descriptively.

**Step 7: Commit**

```bash
rtk git add kadre kadre-x11 kadre-wayland samples/linux-consumer-smoke settings.gradle.kts
rtk git commit -m "fix(linux): select only connectable backends"
```

---

### Task 3: Build the glibc and musl local Linux matrix

**Files:**
- Create: `ci/linux/Dockerfile.glibc`
- Create: `ci/linux/Dockerfile.musl`
- Create: `scripts/test-linux-container.sh`
- Modify: `scripts/ci-wayland-runtime.sh`
- Modify: `scripts/test-x11-xvfb.sh`
- Create: `scripts/verify-linux-libc.sh`

**Step 1: Add a failing libc identity check**

`verify-linux-libc.sh expected` inspects `ldd --version`/loader paths and fails unless the running JVM/process matches exactly `glibc` or `musl`. This prevents two nominal matrix entries from accidentally running the same libc.

**Step 2: Define pinned representative images**

- glibc: `eclipse-temurin:25-jdk` pinned to a reviewed digest at implementation time, with Xvfb, x11-utils, X11/XRandR/Xinerama/Xfixes/XI, Wayland, xkbcommon, Weston, Mesa llvmpipe/EGL, fontconfig, and DejaVu fonts.
- musl: `eclipse-temurin:25-jdk-alpine` pinned to a reviewed digest, with the equivalent Alpine packages (`xvfb`, X11 libs, `wayland-libs-client`, `libxkbcommon`, `weston`, Mesa, fonts, bash).

Do not put Gradle caches or credentials into the images.

**Step 3: Implement one matrix entry point**

Usage:

```bash
scripts/test-linux-container.sh glibc
scripts/test-linux-container.sh musl
```

It builds the selected image, mounts the repository at `/src` read-only, copies it into an ephemeral writable `/work` directory, attaches only writable Gradle cache volumes, and runs from `/work` inside the container. This keeps the checkout immutable while allowing Gradle to create project-local `.gradle` and `build` directories:

```text
verify-linux-libc
check Android-build contract scan (no Android SDK build)
ffi:posix jvmTest
X11 complete jvmTest/conformance under Xvfb
Wayland complete jvmTest/conformance under Weston
facade minimal-consumer smoke on each backend
```

For glibc only, also run Compose headless and real `--window-capture`; the musl baseline intentionally excludes Skiko/Compose native distributions.

**Step 4: Make compositor drivers strict**

Both Xvfb and Weston scripts use bounded readiness probes, capture exact child PIDs, preserve the child command's exit code, and always clean up. Missing socket/binary is failure. They accept an arbitrary command after `--` or as remaining argv; document one consistent syntax and use it everywhere.

**Step 5: Run both containers locally**

```bash
rtk scripts/test-linux-container.sh glibc
rtk scripts/test-linux-container.sh musl
```

Expected: both exit 0 and print different confirmed libc identities; wake tests show eventfd or pipe implementation chosen without SONAME assumptions.

**Step 6: Commit**

```bash
rtk git add ci/linux scripts
rtk git commit -m "test(linux): gate glibc and musl backends"
```

---

### Task 4: Add strict Android, Apple, and browser local drivers

**Files:**
- Create: `scripts/android-emulator-test.sh`
- Modify: `scripts/test-appkit-runtime.sh`
- Modify: `scripts/test-uikit-simulator.sh`
- Modify: `scripts/test-web-browsers.sh`
- Create: `scripts/verify-test-results.py`

**Step 1: Implement result-count verification**

Parse Gradle XML test results and require:

- expected suite glob exists;
- `tests > 0`;
- `failures == 0`, `errors == 0`, `skipped == 0` for deterministic suites.

Hardware-capability suites may supply an explicit allowed-skip file containing a machine-readable reason; absence of either execution or reason is failure.

**Step 2: Implement the Android emulator driver**

When run locally it accepts/uses an existing booted API 35 emulator or creates one if SDK tools are installed. It waits using `adb wait-for-device` plus `getprop sys.boot_completed`, disables animations, then runs:

```bash
rtk ./gradlew \
  :kadre-android:testAndroidHostTest \
  :kadre-android:connectedAndroidTest \
  :samples:hello-triangle-android-capture:connectedDebugAndroidTest \
  --no-daemon --stacktrace
```

Verify host/device/capture XML counts and validate the pulled PNG. Stop only an emulator the script created. Any missing emulator/tool is a descriptive non-zero local precondition failure, not a skip.

**Step 3: Harden Apple/browser drivers**

- AppKit: require macOS, run all AppKit tests twice-capable native lifecycle, verify result count.
- UIKit: bootstatus-based simulator selection, run `kadre-uikit` and iOS sample tests, verify zero skips in deterministic lifecycle/scheduler suites.
- Web: require Chrome/Chromium, run JS and Wasm browser tests, verify both target reports contain tests.

All three install no global dependencies and have a ten-minute per-command timeout.

**Step 4: Run available local drivers**

On the current macOS host:

```bash
rtk scripts/test-appkit-runtime.sh
rtk scripts/test-uikit-simulator.sh
rtk scripts/test-web-browsers.sh
rtk scripts/android-emulator-test.sh
```

Expected: all exit 0 when configured SDK/browser prerequisites exist. If Android prerequisites are absent, install/configure them before claiming full local validation; do not mark complete on a precondition failure.

**Step 5: Commit**

```bash
rtk git add scripts
rtk git commit -m "test: add strict platform runtime drivers"
```

---

### Task 5: Restructure PR CI into parallel blocking jobs under 30 minutes

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/android-visual.yml`
- Modify: `.github/workflows/ios-visual.yml`
- Modify: `.github/workflows/linux-compose-visual.yml`
- Modify: `.github/workflows/linux-visual.yml`
- Modify: `.github/workflows/linux-x11-visual.yml`
- Modify: `.github/workflows/macos-visual.yml`
- Create: `scripts/check-workflow-contract.py`

**Step 1: Write a failing static workflow contract**

Parse YAML/text and fail when an in-scope deterministic step contains:

```text
continue-on-error: true
|| true
|| echo
if-no-files-found: ignore
"skipped" followed by exit 0
```

Also require every blocking PR job to have `timeout-minutes <= 25` and prohibit `needs:` edges between independent platform jobs.

Run:

```bash
rtk scripts/check-workflow-contract.py
```

Expected: failures in current Android/iOS/Linux/macOS visual workflows.

**Step 2: Define the blocking parallel PR jobs**

Restructure in-scope portions of `ci.yml` to these independent jobs:

| Job | Runner | Blocking command | Hard cap |
| --- | --- | --- | ---: |
| `contracts-build` | ubuntu | build contract, core/test JVM, ABI check | 15 min |
| `linux-runtime[glibc,musl]` | ubuntu matrix | container driver | 25 min |
| `android-runtime` | ubuntu | host + API 35 emulator + capture | 25 min |
| `apple-runtime` | macos-15 | AppKit + UIKit arm64 simulator | 25 min |
| `web-runtime` | ubuntu | JS + Wasm Chrome tests | 20 min |

Keep any existing Windows job unchanged and outside this campaign's required status aggregation. Add workflow concurrency cancellation for superseded commits.

**Step 3: Remove false-green visual behavior**

- Linux Compose: compositor readiness, real `--window-capture`, and PNG validation block; remove `continue-on-error`/warning success.
- Linux Wayland/X11 triangle: deterministic software capture production/decode blocks; pixel baseline comparison may remain informational only if clearly named and never substitutes for capture validation.
- Android SwiftShader: emulator test and PNG validation block.
- macOS: offscreen capture production/decode blocks; hardware-varying pixel threshold remains informational.
- iOS: simulator compile/lifecycle is blocking. If Metal is unavailable, record a structured capability result and do not label the job as a successful visual capture.

Never modify `.github/workflows/windows-visual.yml` in this plan.

**Step 4: Validate workflow syntax/contracts**

Run:

```bash
rtk scripts/check-workflow-contract.py
rtk rg -n 'continue-on-error: true|\|\| true|\|\| echo' .github/workflows scripts
```

Expected: the script exits 0. Remaining matches, if any, are only explicitly allowlisted diagnostic/artifact steps, never deterministic execution or output checks.

**Step 5: Commit**

```bash
rtk git add .github/workflows/ci.yml .github/workflows/android-visual.yml .github/workflows/ios-visual.yml .github/workflows/linux-compose-visual.yml .github/workflows/linux-visual.yml .github/workflows/linux-x11-visual.yml .github/workflows/macos-visual.yml scripts/check-workflow-contract.py
rtk git commit -m "ci: make deterministic platform checks blocking"
```

---

### Task 6: Update contracts, Linux dependencies, support tables, and release notes

**Files:**
- Modify: `README.md`
- Modify: `docs/kadre/tutorials/linux-app.md`
- Modify: `docs/kadre/tutorials/linux-app.fr.md`
- Modify: `docs/kadre/specs.md`
- Modify: `docs/kadre/specs.fr.md`
- Modify: `docs/kadre/testing.md`
- Modify: `docs/kadre/testing.fr.md`
- Modify: `docs/kadre/visual-testing.md`
- Modify: `docs/kadre/visual-testing.fr.md`
- Modify: `docs/features/architecture.md`
- Modify: `docs/features/fullscreen-monitor.md`
- Modify: `docs/features/gaps.md`
- Modify: `docs/features/events.md`
- Modify: `CHANGELOG.md`

**Step 1: Correct Linux consumer dependencies**

Both tutorials must show:

```kotlin
jvmMain.dependencies {
    implementation("org.graphiks.kadre:kadre:<version>")
    runtimeOnly("org.graphiks.kadre:kadre-x11:<version>")
    runtimeOnly("org.graphiks.kadre:kadre-wayland:<version>")
}
```

Explain that the facade routes only among backend artifacts actually on the runtime classpath and whose native display connection succeeds. Document forced-backend and attempted-backend errors with relevant environment variables.

**Step 2: Document exact contracts and approved breaks**

Update EN/FR specs/testing docs with:

- iteration sequence and lifecycle diagrams;
- redraw/wake/close invariants;
- `WaitUntil` epoch milliseconds;
- `safeArea` physical pixels;
- Web pointer position/source/identity migration example;
- X11/Wayland display failure behavior;
- Compose sample `iosX64` removal while libraries retain it;
- glibc/musl and emulator/simulator/browser local commands.

**Step 3: Correct feature/support tables**

Wayland output enumeration is no longer synthetic/single-output; update monitor/fullscreen rows. Remove claims that environment variables alone prove backend availability. Keep Windows content unchanged except shared table cells whose common contract wording must match.

**Step 4: Add an Unreleased breaking-change section**

At the top of `CHANGELOG.md`, record the five approved changes, migration notes, deterministic CI policy, AGP 9 migration, and Linux artifact requirements. Do not assign a version/date not requested by the user.

**Step 5: Verify documentation consistency**

Run:

```bash
rtk rg -n 'automatic Linux/Windows/macOS routing|routage automatique Linux/Windows/macOS|synthetic.*wl_output|wl_output.*synthetic' README.md docs --glob '*.md'
rtk ./gradlew :kadre:dokkaHtml :kadre-core:dokkaHtml --no-daemon --stacktrace
```

Expected: first scan returns only historical/changelog context explicitly labelled as such; Dokka generation succeeds.

**Step 6: Commit**

```bash
rtk git add README.md docs CHANGELOG.md
rtk git commit -m "docs: publish corrected cross-platform contracts"
```

---

### Task 7: Regenerate ABI baselines and produce the 19/19 closure report

**Files:**
- Modify: `kadre-core/api/**`
- Modify: `kadre-coroutines/api/**`
- Modify: `kadre-appkit/api/**`
- Modify: `kadre-appkit/build.gradle.kts`
- Modify: `kadre-uikit/api/**`
- Modify: `kadre-android/api/**`
- Modify: `kadre/api/**`
- Create: `docs/kadre/cross-platform-correctness-report.md`

**Step 1: Enable AppKit ABI validation and regenerate only after public changes are final**

`kadre-appkit` already has a checked-in JVM API dump but no active Kotlin ABI validation block. Add the same opt-in used by the other published modules:

```kotlin
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    // existing targets and source sets
}
```

First run `rtk ./gradlew :kadre-appkit:tasks --all | rtk rg 'checkKotlinAbi|updateKotlinAbi'` and require both tasks to exist. Then regenerate all published-module baselines only after the public implementations are final.

Run:

```bash
rtk ./gradlew \
  :kadre:updateKotlinAbi \
  :kadre-android:updateKotlinAbi \
  :kadre-appkit:updateKotlinAbi \
  :kadre-core:updateKotlinAbi \
  :kadre-coroutines:updateKotlinAbi \
  :kadre-uikit:updateKotlinAbi \
  --no-daemon --stacktrace
rtk git diff -- '**/*.api' '**/*.klib.api'
```

Expected: only approved public signature changes appear. If any additional removal/signature break appears, stop and ask the user with compatibility impact before continuing.

**Step 2: Validate ABI and all build contracts**

```bash
rtk ./gradlew \
  :kadre:checkKotlinAbi \
  :kadre-android:checkKotlinAbi \
  :kadre-appkit:checkKotlinAbi \
  :kadre-core:checkKotlinAbi \
  :kadre-coroutines:checkKotlinAbi \
  :kadre-uikit:checkKotlinAbi \
  --no-daemon --stacktrace
rtk scripts/check-android-build-contract.sh
rtk scripts/check-workflow-contract.py
```

Expected: all exit 0.

**Step 3: Write exact finding traceability**

The report contains this table populated with command, test name, environment, and result:

| # | Finding |
| ---: | --- |
| 1 | Android handle valid in `canCreateSurfaces` |
| 2 | redraw effective/coalesced |
| 3 | proxy wake re-arms |
| 4 | Wayland XKB/fd lifetime |
| 5 | terminal close lifecycle |
| 6 | UIKit no duplicate foreground window |
| 7 | Web canvas-relative physical coordinates/button position |
| 8 | Web DPR-aware resize transaction |
| 9 | Web `WaitUntil` cancellation/epoch |
| 10 | Wayland multiple outputs/hotplug/enter-leave |
| 11 | X11 cursor bits/colors |
| 12 | complete ordered lifecycle |
| 13 | mobile `ControlFlow` and AppKit deadline |
| 14 | AppKit CF/delegate resource cleanup/re-run |
| 15 | live Wayland device filter |
| 16 | Wayland refresh rate field |
| 17 | JS pointer-events method parity |
| 18 | unsigned JS RGBA bytes |
| 19 | Android refresh rate not xdpi |

Append risks: Xlib thread confinement, musl, safe-area units, Linux facade dependencies/probe, UIKit suite/Compose target, and false-green workflows.

**Step 4: Run the final local matrix**

```bash
rtk ./gradlew \
  :kadre-core:jvmTest \
  :kadre-test:jvmTest \
  :kadre-appkit:jvmTest \
  :kadre-android:testAndroidHostTest \
  :kadre-web-common:jsBrowserTest \
  :kadre-web-common:wasmJsBrowserTest \
  :kadre:checkKotlinAbi \
  :kadre-android:checkKotlinAbi \
  :kadre-appkit:checkKotlinAbi \
  :kadre-core:checkKotlinAbi \
  :kadre-coroutines:checkKotlinAbi \
  :kadre-uikit:checkKotlinAbi \
  --no-daemon --stacktrace
rtk scripts/test-appkit-runtime.sh
rtk scripts/test-uikit-simulator.sh
rtk scripts/android-emulator-test.sh
rtk scripts/test-web-browsers.sh
rtk scripts/test-linux-container.sh glibc
rtk scripts/test-linux-container.sh musl
```

Expected: every command exits 0; no deterministic skips; the closure report records 19/19 PASS with artifact/log paths.

**Step 5: Commit**

```bash
rtk git add kadre-core/api kadre-coroutines/api kadre-appkit/api kadre-appkit/build.gradle.kts kadre-uikit/api kadre-android/api kadre/api docs/kadre/cross-platform-correctness-report.md
rtk git commit -m "chore: finalize correctness ABI and closure report"
```
