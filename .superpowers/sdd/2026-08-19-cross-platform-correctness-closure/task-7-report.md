# Task 7 — integration verification report

Run date: 2026-08-19 (Europe/Paris)
Commit under test: `56818a5feb23ecc7c5e530ac26787aa2ea229f71`
Base ref after fetch: `origin/codex/cross-platform-correctness-design` = `9bdc7f1eeaf6c94c48cb9228d4146b56288984a1`

## Host and browser matrix

| Command | Result | Evidence / notable output |
| --- | --- | --- |
| `./gradlew :kadre-core:jvmTest :kadre-test:allTests :kadre-appkit:jvmTest :kadre-x11:jvmTest :kadre-wayland:jvmTest :kadre-android:testAndroidHostTest :kadre-web-common:allTests :kadre:build --no-daemon --stacktrace --console=plain` | PASS (exit 0) | `BUILD SUCCESSFUL in 29s`; 316 actionable tasks. |
| `scripts/test-web-browsers.sh` | PASS (exit 0) | Chrome selected at `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`; JS: 141 tests, 0 skipped/failures/errors; Wasm: 141 tests, 0 skipped/failures/errors. |
| `scripts/test-workflow-contract.sh` | PASS (exit 0) | JUnit: 2 tests, 0 skipped/failures/errors; workflow and report contracts enforced. |
| `git diff --check origin/codex/cross-platform-correctness-design...HEAD` | FAIL (exit 2) | Four trailing-whitespace diagnostics in `docs/superpowers/specs/2026-08-19-cross-platform-correctness-closure-design.md` (lines 3–6) and `new blank line at EOF` in `docs/superpowers/plans/2026-08-19-cross-platform-correctness-closure.md:425`. |

The diff-check failure is a release-gate failure. It is not a host limitation. It predates this verification report and lies in the closure plan/spec documentation; no out-of-scope cleanup was applied by this integration task.

## Native integrations

| Command | Classification | Exact outcome |
| --- | --- | --- |
| `scripts/test-appkit-runtime.sh` | PASS | Exit 0. Both `AppKit preparation: passed` and `AppKit runtime: passed`. |
| `scripts/test-uikit-simulator.sh` | PASS | Exit 0. Existing iPhone 16e simulator `901F34EA-BC7F-47DE-AFDC-B5CC439CBEB4` booted; `:kadre-uikit:iosSimulatorArm64Test` and `:samples:hello-triangle-ios:iosSimulatorArm64Test` completed with `BUILD SUCCESSFUL in 30s`. |
| `scripts/test-x11-xvfb.sh` | HOST LIMITATION — not a pass | Exit 1 before test execution: `[test-x11-xvfb] ERROR: Xvfb is not installed`. The macOS host does not provide the required Xvfb runner; the corresponding Linux CI job remains required. |
| `scripts/test-linux-container.sh glibc` | HOST LIMITATION — not a pass | Exit 1 before container execution: `Cannot connect to the Docker daemon at unix:///Users/chaos/.docker/run/docker.sock. Is the docker daemon running?` The Docker daemon is unavailable; the glibc CI job remains required. |
| `scripts/test-linux-container.sh musl` | HOST LIMITATION — not a pass | Exit 1 before container execution: `Cannot connect to the Docker daemon at unix:///Users/chaos/.docker/run/docker.sock. Is the docker daemon running?` The Docker daemon is unavailable; the musl CI job remains required. |
| `scripts/android-emulator-test.sh` | FAIL — real test failure | Exit 1. The runnable `Kadre_API_29(AVD) - 10` executed `capturesTriangle` and failed with `java.lang.IllegalStateException: Failed to acquire Adapter` at `HelloTriangleCapture.kt:81`. `Medium_Phone(AVD) - 16` passed the same test, so this is not an unavailable-emulator limitation. The aggregate report is 2 tests / 1 failure / 0 errors (50%). |

Android evidence: `samples/hello-triangle-android-capture/build/outputs/androidTest-results/connected/debug/TEST-Kadre_API_29(AVD) - 10-_samples_hello-triangle-android-capture-.xml` records one failure; the corresponding `Medium_Phone(AVD) - 16` XML records one pass. This requires investigation by the Android/runtime task owner before publication; no speculative integration-layer fix was made.

## Traceability audit (findings 1–19)

Audit command: `python3 scripts/verify-test-results.py --report docs/kadre/cross-platform-correctness-report.md`
Result: exit 0 — `Report evidence: docs/kadre/cross-platform-correctness-report.md contains 19 complete traceability rows`.

Each numbered row below has a unique ordinal, non-empty Finding, Test/command, Environment, Result, and Proof path field. “Complete” here validates the evidence contract; it does not turn planned/CI-only evidence into a local PASS.

| # | Finding | Command/environment/evidence audit | Result-field audit |
| --- | --- | --- | --- |
| 1 | canonical shared iteration boundaries | Gradle `:kadre-test:allTests`; host JVM 25; `kadre-test/build/reports/tests/` | Complete; planned local command. |
| 2 | X11 terminal close ordering | Gradle `X11LoopContractTest`; host JVM 25; X11 JVM report | Complete; planned local command. |
| 3 | Wayland terminal close ordering | Gradle `WaylandLoopContractTest`; host JVM 25; Wayland JVM report | Complete; planned local command. |
| 4 | discovered-empty Wayland output snapshot | Gradle `WaylandLoopContractTest`; host JVM 25; Wayland JVM report | Complete; planned local command. |
| 5 | immutable Web `WaitUntil` deadline | Gradle `:kadre-web-common:allTests`; Node JS/Wasm runtime; web report | Complete; planned local command. |
| 6 | immutable X11 `WaitUntil` deadline | Gradle `WaitUntil`; host JVM 25; X11 JVM report | Complete; planned local command. |
| 7 | immutable Wayland `WaitUntil` deadline | Gradle `WaitUntil`; host JVM 25; Wayland JVM report | Complete; planned local command. |
| 8 | canonical UIKit startup/background traces | `scripts/test-uikit-simulator.sh`; macos-15 iOS simulator; `ios-simulator-contracts` Actions log | Complete; CI evidence path. |
| 9 | UIKit terminal cleanup after callback failure | `scripts/test-uikit-simulator.sh`; macos-15 iOS simulator; `ios-simulator-contracts` Actions log | Complete; CI evidence path. |
| 10 | Linux automatic Wayland-to-X11 fallback | Gradle `LinuxBackendDetectorTest`; JVM 25; Kadre JVM report | Complete; planned local command. |
| 11 | strict forced Linux backend override | Gradle `LinuxBackendLaunchTest`; JVM 25; Kadre JVM report | Complete; planned local command. |
| 12 | native Linux launch-error preservation | Gradle `LinuxBackendLaunchTest`; JVM 25; Kadre JVM report | Complete; planned local command. |
| 13 | JS browser result counts/deterministic skips | `scripts/test-web-browsers.sh`; ubuntu Chrome; `web-browser-contracts` Actions log | Complete; CI evidence path. |
| 14 | Wasm browser result counts/deterministic skips | `scripts/test-web-browsers.sh`; ubuntu Chrome; `web-browser-contracts` Actions log | Complete; CI evidence path. |
| 15 | Android emulator JUnit and PNG evidence | `scripts/android-emulator-test.sh`; ubuntu API 34 SwiftShader; Android output path | Complete; CI evidence path. |
| 16 | glibc Linux X11/Wayland runtime contracts | `scripts/test-linux-container.sh glibc`; Docker JDK 25 glibc; Actions log | Complete; CI evidence path. |
| 17 | musl Linux X11/Wayland runtime contracts | `scripts/test-linux-container.sh musl`; Docker JDK 25 Alpine musl; Actions log | Complete; CI evidence path. |
| 18 | deterministic decoded raster contract | `verify-test-results.py --png ... --png-target compose-raster`; ubuntu software raster; artifact `deterministic-compose-raster` | Complete; CI artifact path. |
| 19 | aggregate cannot mask failed matrix job | `scripts/test-workflow-contract.sh`; local Python fixture; workflow path | Complete; local proof is command-produced. |

## Integration and publication gate

`git fetch origin codex/cross-platform-correctness-design` completed (`ok fetched (1 new refs)`). `git merge-base --is-ancestor origin/codex/cross-platform-correctness-design HEAD` exited 0, so HEAD is fast-forward compatible with the fetched base. The intended range lists commits from `71bbf631` through `56818a5f`.

The worktree was clean before verification. It now has the Gradle-generated untracked file `kotlin-js-store/wasm/yarn.lock` from the browser/build matrix. It was not removed by this task. No branch change, rebase, force operation, or push was performed.

Publication is blocked pending: (1) correction of the `git diff --check` diagnostics, (2) a passing Android emulator integration on every targeted runnable emulator, (3) the unavailable Xvfb/Docker CI integrations, and (4) the independent read-only review required by the closure brief.
