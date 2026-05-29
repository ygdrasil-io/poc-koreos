# Changelog

All notable changes to Koreos are documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
Versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.1] — 2026-05-29

Patch de corrections Sprint 0 — 10 PRs de stabilisation post-release v0.1.0.

### Fixed

- **Docs** — Rebrand MkDocs Koreos + navigation API reference (PR #50, fixes #49)
- **`koreos-appkit`** — `KoreosApplication.eventLoop` statique → instance scopée (PR #51, fixes #41)
- **`koreos-appkit`** — Suppression commentaire stub trompeur dans `AppKitEventLoop` (PR #52, fixes #42)
- **Docs** — README racine actualisé pour Koreos v0.1.0 (PR #53, fixes #45)
- **Docs** — Documentation `sprint-review-v0.1.md` ajoutée (PR #54, fixes #38)
- **CI** — Jobs `ios-build` / `android-build` déclenchés sur branches `claude/**` (PR #55, fixes #39)
- **Docs** — Post-mortem M2 : FPS 60→120 + note PR #25 (PR #56, fixes #46)
- **`koreos-android`** — `AndroidEventLoop.createWindow` retourne une `AndroidWindow` réelle (PR #57, fixes #47)
- **Samples** — Refactor samples Android — `HelloApp` partagé en `commonMain` (PR #58, fixes #44)
- **`koreos-core`** — `RawWindowHandle.Win32` + `RawDisplayHandle.Win32` (PR #59, fixes #16)

### Artifacts

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.ygdrasil.koreos:koreos:0.1.1")
}
```

[0.1.1]: https://github.com/ygdrasil-io/poc-koreos/releases/tag/v0.1.1

---

## [0.1.0] — 2026-05-28

First public release of Koreos — Kotlin Multiplatform windowing and event-loop library.

### Added

#### M1 — macOS Foundation (GRA-120 → GRA-136)

- **`koreos-core`** — Pure KMP interfaces: `EventLoop`, `Window`, `ApplicationHandler`, `WindowEvent`, `DeviceEvent`
- **`koreos-appkit`** — macOS backend via Panama FFM (zero JNA/Rococoa):
  - `KoreosApplication` (NSApplication subclass) + `KoreosAppDelegate`
  - `AppKitWindow` — NSWindow + CAMetalLayer layer-backed content view
  - `KoreosWindowDelegate` — `windowShouldClose` → `CloseRequested`
  - `AppKitEventLoop.runApp` + `ActiveEventLoop` + `EventLoopProxy.wakeUp` (thread-safe)
  - `CFRunLoopObserver` → `RedrawRequested` + `aboutToWait` callbacks
  - `WindowEvent.Resized` (drawableSize), `ScaleFactorChanged`, `RedrawRequested`
  - `ControlFlow` effective (Poll / Wait)
- **`koreos`** — KMP façade: `expect`/`actual` `EventLoop` + re-exports of `koreos-core`
- **`samples/hello-metal`** — macOS sample: NSWindow + CAMetalLayer @ 60 fps
- **CI** — `macos-build` job (Fast-Track JVM / Deep-Testing allTests)

#### M2 — GPU Rendering (GRA-137 → GRA-140)

- **`samples/hello-triangle`** — RGB rotating triangle via wgpu4k 0.1.1 (Metal backend, WGSL shaders)
- Resize swap-chain on `Resized`/`ScaleFactorChanged`
- AppKit bindings via kextract v0.0.0-test6

#### M3 — Multi-platform (GRA-141 → GRA-161)

- **`koreos-uikit`** — iOS backend (Kotlin/Native cinterop, UIKit):
  - `KoreosAppDelegate`, `KoreosViewController`, `UiKitWindow` (UIWindow + UIView + CAMetalLayer full-screen)
  - `WindowEvent.Touch` multi-touch (UIResponder)
  - Lifecycle: background/foreground events
- **`koreos-android`** — Android backend (SurfaceView, API 24+):
  - `KoreosActivity` abstract Activity + `AndroidWindow` (SurfaceView full-screen)
  - `WindowEvent.Touch` multi-touch (MotionEvent)
  - `Choreographer` frame timing → `RedrawRequested`
  - Lifecycle dispatch: `canCreateSurfaces`, `destroySurfaces`, `suspended`, `resumed`
  - `AndroidKoreosRuntime` global singleton bridging `EventLoop.runApp()` to `KoreosActivity`
- **`koreos`** — `actual EventLoop` for iOS (→ koreos-uikit) and Android (→ koreos-android)
- **`samples/hello-touch`** — iOS sample (iosSimulatorArm64 / iosArm64)
- **`samples/hello-touch-android`** — Android APK sample (minSdk 24)
- **`samples/hello-window`** — Cross-platform sample (JVM + iOS + Android) with shared `HelloApp`
- **`samples/hello-window-android`** — Android APK for hello-window
- **AppKit input** (GRA-153–156):
  - Multi-window support: `windowWillClose` cleanup + `exit()` closes all
  - Keyboard input: `KeyboardInput` + `isRepeat`
  - Mouse input: clicks, motion, scroll, enter/exit → `WindowEvent` + `DeviceEvent`
  - `DeviceEvent` dispatch before `WindowEvent` (ordering guarantee)
- **KDoc + MkDocs** — API reference (Dokka GFM) integrated into `docs/koreos/api/`
- **Maven Central** — `kmp-publish` convention plugin with GPG in-memory signing; `docs/koreos/release-process.md`
- **CI multi-platform** — 4-job matrix:
  - `build-and-test` (macos-15): Fast-Track JVM / Deep-Testing JVM + iosSimulatorArm64
  - `macos-build` (macos-latest): AppKit + samples
  - `ios-build` (macos-15, master only): all iOS targets + simulator tests
  - `android-build` (ubuntu-latest, master only): Android modules + APK samples

### Breaking Changes

None — this is the first public release.

### Known Issues

- `koreos-appkit` requires JDK 25 (`--enable-native-access=ALL-UNNAMED`); JDK 17/21 are not supported.
- `samples/hello-triangle` requires wgpu4k 0.1.1 (Metal backend); GPU tests are not run in CI.
- Maven Central publication requires manual GPG key + Sonatype credentials setup (see `docs/koreos/release-process.md`).

### Artifacts

Group ID: `io.ygdrasil.koreos`

| Module            | Artifact ID        | Targets                          |
|-------------------|--------------------|----------------------------------|
| `koreos-core`     | `koreos-core`      | jvm, iosX64, iosArm64, iosSimulatorArm64, android |
| `koreos`          | `koreos`           | jvm, iosX64, iosArm64, iosSimulatorArm64, android |
| `koreos-appkit`   | `koreos-appkit`    | jvm (macOS only)                 |
| `koreos-uikit`    | `koreos-uikit`     | iosX64, iosArm64, iosSimulatorArm64 |
| `koreos-android`  | `koreos-android`   | android                          |

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.ygdrasil.koreos:koreos:0.1.0")
}
```

[0.1.0]: https://github.com/ygdrasil-io/poc-koreos/releases/tag/v0.1.0
