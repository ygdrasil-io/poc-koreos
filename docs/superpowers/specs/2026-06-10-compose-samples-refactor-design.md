# Compose Samples Refactor — Design

> Status: **Approved**
> Restructure Compose samples into `samples/compose/` with a shared infrastructure module and responsive showcase per platform.

## Motivation

The Compose integration infrastructure (`ComposeSceneHost`, `ComposeWindowRenderer`, `GlComposeRenderer`, `MetalComposeRenderer`, `WindowEventBridge`, `NativeFfi`, platform GL/EGL contexts) is currently duplicated across `samples/hello-compose/` and `samples/simulation-demo/`. The `hello-compose` sample mixes low-level infrastructure with feature demos (Capture, KeyTest, Coroutines, NativeFFI) in a desktop-only JVM project.

Goal: extract the shared infrastructure, remove the duplication, and create a platform-per-module showcase with a responsive UI that groups features on large screens and shows them individually on small screens.

`simulation-demo` is **not modified**.

## Structure

```
samples/compose/
├── shared/                        # KMP module (jvm, androidTarget, iosX64/iosArm64/iosSimulatorArm64, js, wasmJs)
│   ├── commonMain/
│   │   ├── infra/                 # expect declarations + common types
│   │   │   ├── ComposeSceneHost.kt
│   │   │   ├── ComposeWindowRenderer.kt
│   │   │   ├── WindowEventBridge.kt
│   │   │   └── NativeFfi.kt
│   │   ├── showcase/              # UI showcase (responsive)
│   │   │   ├── ShowcaseApp.kt     # Root composable, WindowSizeClass dispatch
│   │   │   ├── ShowcaseFeature.kt # Feature interface
│   │   │   ├── FeatureCategory.kt # Enum: Keyboard, Capture, Concurrency, Platform
│   │   │   ├── PlatformContext.kt # expect, provides platform info to features
│   │   │   ├── navigation/        # Desktop drawer + mobile bottom nav
│   │   │   ├── features/          # Feature composables
│   │   │   │   ├── KeyTest.kt
│   │   │   │   ├── Capture.kt     # Dev-only, not shown in UI
│   │   │   │   ├── CoroutinesDemo.kt
│   │   │   │   └── NativeFfi.kt
│   │   │   └── theme/
│   │   │       └── Theme.kt
│   │   └── util/
│   ├── jvmMain/
│   │   ├── infra/                 # actual implementations
│   │   │   ├── ComposeSceneHost.jvm.kt
│   │   │   ├── GlComposeRenderer.kt
│   │   │   ├── MetalComposeRenderer.kt
│   │   │   ├── WindowEventBridge.jvm.kt
│   │   │   ├── NativeFfi.jvm.kt
│   │   │   ├── Win32WglContext.kt
│   │   │   ├── X11GlxContext.kt
│   │   │   └── WaylandEglContext.kt
│   │   └── showcase/
│   │       └── PlatformContext.jvm.kt
│   ├── androidMain/
│   │   └── showcase/
│   │       └── PlatformContext.android.kt
│   ├── iosMain/
│   │   └── showcase/
│   │       └── PlatformContext.ios.kt
│   ├── jsMain/
│   │   └── showcase/
│   │       └── PlatformContext.js.kt
│   └── wasmJsMain/
│       └── showcase/
│           └── PlatformContext.wasmJs.kt
│
├── desktop/                       # JVM module → `main()` → `kadreApplication { Window { ShowcaseApp() } }`
│   └── src/main/kotlin/.../Main.kt
│
├── android/                       # Android app module
│   └── src/main/
│       ├── kotlin/.../MainActivity.kt
│       ├── AndroidManifest.xml
│       └── res/
│
├── ios/                           # KMP module (iOS targets only)
│   └── src/iosMain/kotlin/.../MainViewController.kt
│
└── web/                           # KMP module (js + wasmJs)
    └── src/
        ├── jsMain/kotlin/.../Main.kt
        └── wasmJsMain/kotlin/.../Main.kt
```

## Responsive Showcase UI

Window size classification via `BoxWithConstraints` (cutoff ~840dp):

| Screen width | Layout | Navigation | Feature display |
|---|---|---|---|
| >= 840dp | Desktop | Drawer + group tabs | Grid of cards per category |
| < 840dp | Mobile | Bottom nav | Single feature list, one screen at a time |

### Feature interface

```kotlin
enum class FeatureCategory(val displayName: String) { Keyboard, Capture, Concurrency, Platform }

interface ShowcaseFeature {
    val id: String
    val title: String
    val description: String
    val category: FeatureCategory
    val devOnly: Boolean  // true for Capture — excluded from UI, visible in source
    @Composable fun Content(platformContext: PlatformContext)
}
```

## Features

| Feature | Category | Source origin | Platforms |
|---|---|---|---|
| KeyTest | Keyboard | hello-compose | Desktop, Android, iOS, Web |
| Capture | Capture (dev-only) | hello-compose | Desktop |
| CoroutinesDemo | Concurrency | hello-compose | Desktop, Android, iOS, Web |
| NativeFfi | Platform | hello-compose | Desktop (JNI/FFM) |

Each feature composable:
- Checks `PlatformContext.isFeatureSupported(category)` to show a fallback message on unsupported platforms
- Capture is `devOnly = true` (visible in source/docs only, excluded from showcase navigation)

## Platform integration per module

**desktop** (`kotlin("jvm")`):
- Entry: `main()` → `kadreApplication { Window { ShowcaseApp() } }`
- Uses `ComposeSceneHost` from `shared` to embed Compose in a native Kadre window
- Renderer auto-selected by OS: Metal on macOS, GL on Windows/Linux

**android** (`com.android.application`):
- Entry: `MainActivity` → `setContent { KadreTheme { ShowcaseApp() } }`
- Uses `AndroidComposeScene` for Kadre rendering integration
- Full Android app with manifest

**ios** (`kotlin("multiplatform")` — iOS targets):
- Entry: `MainViewController` → `ComposeUIViewController { ShowcaseApp() }`
- Standard Compose Multiplatform iOS entry point

**web** (`kotlin("multiplatform")` — js, wasmJs):
- Entry: `Main.kt` → `ComposeScene` rendered in Skia canvas
- No GL/Metal renderer — pure Skia

## Build & settings changes

- `samples/compose/shared/build.gradle.kts` — KMP with all targets, Compose plugin, depends on `:kadre` + `:kadre:coroutines`
- `samples/compose/desktop/build.gradle.kts` — `kotlin("jvm")`, depends on `:samples:compose:shared`
- `samples/compose/android/build.gradle.kts` — `com.android.application`, depends on `:samples:compose:shared`
- `samples/compose/ios/build.gradle.kts` — KMP with iOS targets, depends on `:samples:compose:shared`
- `samples/compose/web/build.gradle.kts` — KMP with js/wasmJs, depends on `:samples:compose:shared`
- `settings.gradle.kts` — remove `:samples:hello-compose`, add `:samples:compose:shared`, `:samples:compose:desktop`, `:samples:compose:android`, `:samples:compose:ios`, `:samples:compose:web`

## Migration

1. Create `samples/compose/shared/` — copy infrastructure and feature files from hello-compose, create showcase UI, adapt packages
2. Create `samples/compose/desktop/`
3. Create `samples/compose/android/`
4. Create `samples/compose/ios/`
5. Create `samples/compose/web/`
6. Remove `samples/hello-compose/` entirely (infra → shared, features → shared, entry → desktop)
7. Remove `:samples:hello-compose` from `settings.gradle.kts`
8. Add `:samples:compose:shared`, `:samples:compose:desktop`, `:samples:compose:android`, `:samples:compose:ios`, `:samples:compose:web` to `settings.gradle.kts`
9. `simulation-demo` is **not modified** (keeps its own copy of infra)
