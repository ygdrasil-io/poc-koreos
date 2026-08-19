# Final Sol fix report

Commit under review: `6c6da47` (`fix: close cross-platform correctness review findings`).

## RED → GREEN evidence

- **RED:** `./gradlew :kadre:jvmTest --tests '*LinuxBackendDetectorTest*' --no-daemon --console=plain` exited 1: the new allowlist regression found `XDG_SESSION_TYPE` in the exhausted-selection error.
- **GREEN:** the same command exited 0 after the diagnostic context was restricted to `KADRE_LINUX_BACKEND`, `WAYLAND_DISPLAY`, and `DISPLAY`.
- **RED:** `scripts/test-workflow-contract.sh` exited 1 because the missing-musl fixture was accepted and the Android device-selection script was not a required PR command; the empty 19-row report fixture was likewise accepted by the prior validator.
- **GREEN:** `scripts/test-workflow-contract.sh` exited 0 after the structural matrix, Android command, capture-job, and non-empty report-field checks were added.

## Important findings resolved

1. **Blocking deterministic capture matrix:** `cross-platform-correctness.yml` now contains real `linux-compose-windowed-capture` (Weston/software Mesa) and `macos-offscreen-capture` jobs, each limited to 25 minutes. Both are aggregate `needs` with explicit success assertions and use scripts that validate decoded PNG output. The hardware diagnostics remain outside this dependency graph.
2. **libc and Android regression contract:** the checker requires exactly `strategy.matrix.libc: [glibc, musl]`. The Android emulator job runs `scripts/test-android-device-selection.sh` before the real emulator runner, and a negative fixture proves removal is rejected.
3. **Authoritative 19 findings:** `docs/kadre/cross-platform-correctness-report.md` now maps the original 2026-07-17 findings 1–19. `verify-test-results.py` extracts each required field and rejects blank values; its all-empty 19-row fixture is exercised by the workflow-contract test. Result wording distinguishes defined/pending external CI from local execution.
4. **Approved compatibility documentation:** EN/FR specifications and the changelog document the five approved original changes (Web pointer shape, physical-pixel safe area, fatal unavailable display/compositor, callback order, Compose-sample-only `iosX64` removal) plus final-known-Wayland-output removal. Each has a migration note. EN/FR Linux tutorials additionally document the startup-error migration and diagnostic allowlist.
5. **Linux diagnostic allowlist:** `XDG_SESSION_TYPE` is no longer quoted in exhausted-selection errors. The Kotlin regression asserts all three allowed values and rejects that identifier.

## Validation completed

- `./gradlew :kadre:jvmTest --tests '*LinuxBackendDetectorTest*' --no-daemon --console=plain` — PASS.
- `scripts/test-workflow-contract.sh` — PASS (workflow fixtures, empty-field report fixture, JUnit and PNG fixtures, real workflow/report validation).
- `bash scripts/test-android-device-selection.sh` — PASS.
- `bash -n scripts/test-workflow-contract.sh scripts/test-android-device-selection.sh scripts/test-linux-compose-windowed-capture.sh scripts/test-macos-offscreen-capture.sh scripts/test-linux-container.sh` — PASS.
- `python3 -m py_compile scripts/check-workflow-contract.py scripts/verify-test-results.py` — PASS.
- PyYAML parse of every `.github/workflows/*.yml` — PASS.
- `git diff --check` — PASS before commit.
- `scripts/test-appkit-runtime.sh` — PASS.
- `scripts/test-uikit-simulator.sh` — PASS on the local iPhone 16e simulator.
- `scripts/android-emulator-test.sh` — PASS on the local Medium_Phone API 16 emulator; JUnit reported 1 test with zero skips/failures/errors and the Android PNG validator passed.
- `bash scripts/test-macos-offscreen-capture.sh` — PASS; baseline diff was 0.000% and the decoded 800×600 PNG passed.
- `scripts/test-web-browsers.sh` — PASS; JS and Wasm each reported 141 browser tests with zero skips/failures/errors.

## Environment limitations

This report does not claim GitHub Actions execution. `scripts/test-x11-xvfb.sh` could not run because Xvfb is not installed, and `scripts/test-linux-container.sh glibc` could not run because the local Docker daemon is unavailable; the glibc/musl and deterministic Weston gates therefore remain CI responsibilities. The local Android emulator, Chrome browser, iOS simulator, AppKit runtime, and macOS offscreen capture were run successfully, but they do not substitute for their separate GitHub Actions evidence.

## Minor findings triage

1. **Negative-clock `WaitUntil` overflow:** deferred. It remains limited to an injected/non-production negative epoch; the existing observed-time guard still prevents premature `ResumeTimeReached`. It is unrelated to the five Important findings and changing scheduler arithmetic here would broaden this focused closure.
2. **Direct two-window reentrant UIKit `ThemeChanged` test:** deferred. The shared terminal guard is already covered through the analogous focused/occluded two-window tests; adding the narrow duplicate test is useful future hardening but does not alter runtime behavior and would dilute this review-fix commit.
