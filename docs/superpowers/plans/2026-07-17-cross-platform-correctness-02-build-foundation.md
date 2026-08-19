# AGP 9 and Build Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Replace the legacy Android/KMP configuration with the supported AGP 9 DSL, remove compatibility switches that mask the real build, and make every Android host/device test task discoverable before backend work starts.

**Architecture:** The `ygdrasil.conventions.kmp-library` convention remains the single owner of common KMP targets and Android defaults. It adopts `com.android.kotlin.multiplatform.library`; every consumer supplies only its namespace and module-specific Android test opt-ins inside `kotlin { android { ... } }`. Regular Android applications use AGP 9 built-in Kotlin and keep their existing Android source layout.

**Tech Stack:** Gradle Kotlin DSL, AGP 9.0.0, Kotlin 2.4.0, Java 25 toolchain, Android Host Test and Device Test components.

## Global Constraints

- Keep `iosX64` in the convention and library modules.
- Do not change runtime behavior in this plan.
- Treat every deprecation, obsolete compatibility flag, or missing Android task as a blocking build defect.
- Use `rtk` for every shell command.
- Commit after each green task.

---

### Task 1: Add a build-level regression check for obsolete Android configuration

**Files:**
- Create: `scripts/check-android-build-contract.sh`
- Test: `scripts/check-android-build-contract.sh`

**Step 1: Write the failing check**

Create an executable shell check that fails when any of these legacy constructs exists in an active build file:

```bash
#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

legacy="$({
  rg -n 'androidTarget\s*\(' "$root" --glob '*.gradle.kts' --glob '!docs/**' || true
  rg -n 'id\("org\.jetbrains\.kotlin\.android"\)|kotlin\("android"\)' "$root" --glob '*.gradle.kts' --glob '!docs/**' || true
  rg -n '^android\.(builtInKotlin|newDsl)=false|^systemProp\..*android\.(builtInKotlin|newDsl)=false' "$root/gradle.properties" || true
} | sed '/^[[:space:]]*\/\//d')"

if [[ -n "$legacy" ]]; then
  printf '%s\n' "$legacy" >&2
  exit 1
fi
```

Make the file executable:

```bash
rtk chmod +x scripts/check-android-build-contract.sh
```

**Step 2: Run it to verify failure**

Run:

```bash
rtk scripts/check-android-build-contract.sh
```

Expected: exit 1, listing `androidTarget()`, explicit Kotlin Android plugins, and the AGP compatibility flags.

**Step 3: Commit the red build contract**

```bash
rtk git add scripts/check-android-build-contract.sh
rtk git commit -m "test(build): detect legacy AGP configuration"
```

---

### Task 2: Migrate the KMP library convention to the AGP 9 plugin

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `buildSrc/src/main/kotlin/ygdrasil/conventions/kmp-library.gradle.kts`

**Step 1: Expose the supported plugin in the version catalog**

Add:

```toml
android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
```

The convention plugin applies plugin IDs directly, so the catalog alias documents and makes the same plugin available to standalone sample modules.

**Step 2: Replace the legacy convention DSL**

Use this structure:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(25)

    android {
        compileSdk = 35
        minSdk = 24
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}
```

Delete the top-level `android {}` block and `androidTarget()` call. JVM bytecode stays at 17 for Android runtime compatibility even though Gradle runs with JDK 25.

**Step 3: Verify convention compilation**

Run:

```bash
rtk ./gradlew :buildSrc:compileKotlin --warning-mode=fail
```

Expected: `BUILD SUCCESSFUL`; no reference to `com.android.library`, `androidTarget`, or the legacy Android extension.

**Step 4: Commit**

```bash
rtk git add gradle/libs.versions.toml buildSrc/src/main/kotlin/ygdrasil/conventions/kmp-library.gradle.kts
rtk git commit -m "build: adopt AGP 9 KMP library plugin"
```

---

### Task 3: Migrate convention consumers and Android host tests

**Files:**
- Modify: `kadre-core/build.gradle.kts`
- Modify: `kadre-test/build.gradle.kts`
- Modify: `kadre-android/build.gradle.kts`
- Modify: `kadre/build.gradle.kts`
- Modify: `samples/hello-touch/build.gradle.kts`
- Modify: `samples/pong/build.gradle.kts`
- Move: `kadre-android/src/androidUnitTest/**` to `kadre-android/src/androidHostTest/**`

**Step 1: Move the renamed test source set**

Run:

```bash
rtk mkdir -p kadre-android/src/androidHostTest
rtk git mv kadre-android/src/androidUnitTest/kotlin kadre-android/src/androidHostTest/kotlin
```

Expected: Git records a source-set rename, not duplicate tests.

**Step 2: Move each namespace into the KMP Android target**

Delete every top-level `android { namespace = ... }`. Put the namespace at the start of the existing `kotlin` block:

```kotlin
kotlin {
    android {
        namespace = "org.graphiks.kadre.core"
    }
    // existing targets, compiler options, and source sets
}
```

Apply the corresponding existing namespace in all six listed consumers.

**Step 3: Enable and wire `kadre-android` host tests**

Inside `kadre-android`'s `kotlin { android { ... } }` block add:

```kotlin
withHostTest {
    isIncludeAndroidResources = true
}
```

Replace the source-set lookup:

```kotlin
androidHostTest.dependencies {
    implementation(kotlin("test"))
}
```

Keep its publication exclusions, but update publication-name predicates only if the AGP 9 task report proves that the Android component name changed.

**Step 4: Verify the exact test component and all KMP compilations**

Run:

```bash
rtk ./gradlew :kadre-android:tasks --all | rtk rg 'testAndroidHostTest|androidHostTest'
rtk ./gradlew :kadre-core:allTests :kadre-test:allTests :kadre-android:testAndroidHostTest :kadre:allTests --warning-mode=fail
```

Expected: task report includes `testAndroidHostTest`; all commands exit 0. If AGP exposes a differently cased generated task, use the task report's exact name consistently in all later plans and CI.

**Step 5: Commit**

```bash
rtk git add kadre-core kadre-test kadre-android kadre samples/hello-touch samples/pong
rtk git commit -m "build: migrate KMP consumers to AGP 9 DSL"
```

---

### Task 4: Migrate the Compose shared library and remove only its Intel simulator target

**Files:**
- Modify: `samples/compose/shared/build.gradle.kts`
- Modify: `samples/compose/ios/build.gradle.kts`

**Step 1: Migrate the standalone KMP library plugin**

In `samples/compose/shared/build.gradle.kts`:

- replace `id("com.android.library")` with `alias(libs.plugins.android.kmp.library)`;
- delete the top-level `android {}` block;
- replace `androidTarget { publishLibraryVariants("release") }` with the AGP 9 `android { namespace; compileSdk; minSdk }` target;
- retain JVM, Apple ARM, JS, Wasm, Compose, dependencies, and compiler flags.

Use:

```kotlin
kotlin {
    jvm()
    android {
        namespace = "org.graphiks.kadre.samples.compose.shared"
        compileSdk = 35
        minSdk = 24
    }
    iosArm64()
    iosSimulatorArm64()
    // no iosX64 in this Compose sample
}
```

AGP 9 KMP libraries publish their Android library component without `publishLibraryVariants`.

**Step 2: Remove `iosX64()` from the Compose iOS launcher only**

Delete `iosX64()` from `samples/compose/ios/build.gradle.kts`. Do not touch `kadre-uikit`, `kadre`, `kadre-core`, or the convention's `iosX64` target.

**Step 3: Prove the scope of the accepted break**

Run:

```bash
rtk ./gradlew :samples:compose:shared:assemble :samples:compose:shared:compileKotlinIosSimulatorArm64 :kadre-core:compileKotlinIosX64 :kadre-uikit:compileKotlinIosX64
rtk rg -n 'iosX64\(\)' kadre-core kadre-uikit kadre buildSrc --glob '*.gradle.kts'
```

Expected: the Compose shared module, its Apple Silicon simulator target, and both library `iosX64` compilations succeed; `iosX64()` is absent only from the Compose sample files.

**Step 4: Commit**

```bash
rtk git add samples/compose/shared/build.gradle.kts samples/compose/ios/build.gradle.kts
rtk git commit -m "build(compose): migrate Android target and drop sample iosX64"
```

---

### Task 5: Adopt AGP 9 built-in Kotlin in regular Android modules

**Files:**
- Modify: `samples/compose/android/build.gradle.kts`
- Modify: `samples/hello-touch-android/build.gradle.kts`
- Modify: `samples/hello-window-android/build.gradle.kts`
- Modify: `samples/hello-triangle-android-capture/build.gradle.kts`

**Step 1: Remove the obsolete Kotlin Android plugins**

Remove `id("org.jetbrains.kotlin.android")` and `kotlin("android")`. Keep `com.android.application` or `com.android.library`; AGP 9 supplies Kotlin support.

**Step 2: Normalize compiler/toolchain configuration**

Keep Android `compileOptions` at Java 17 and add the AGP 9 built-in Kotlin compiler block where needed:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

Do not nest `kotlin {}` inside `android {}`.

**Step 3: Verify every regular Android module**

Run:

```bash
rtk ./gradlew :samples:compose:android:assembleDebug :samples:hello-touch-android:assembleDebug :samples:hello-window-android:assembleDebug :samples:hello-triangle-android-capture:assembleDebugAndroidTest --warning-mode=fail
```

Expected: all four artifacts build with no explicit Kotlin Android plugin.

**Step 4: Commit**

```bash
rtk git add samples/compose/android samples/hello-touch-android samples/hello-window-android samples/hello-triangle-android-capture
rtk git commit -m "build(android): use AGP 9 built-in Kotlin"
```

---

### Task 6: Remove masking compatibility flags and close the build contract

**Files:**
- Modify: `gradle.properties`
- Modify: `scripts/check-android-build-contract.sh`

**Step 1: Delete obsolete properties**

Remove:

```properties
kotlin.mpp.androidSourceSetLayoutVersion=2
android.builtInKotlin=false
android.newDsl=false
systemProp.android.builtInKotlin=false
systemProp.android.newDsl=false
systemProp.org.gradle.project.android.builtInKotlin=false
systemProp.org.gradle.project.android.newDsl=false
```

Keep `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, and unrelated Dokka/publication properties.

**Step 2: Make the check reject old top-level KMP Android blocks**

Extend the script with a targeted assertion for the six convention consumers: their namespace must appear inside `kotlin { android { ... } }`, and `com.android.library` must not be applied alongside `org.jetbrains.kotlin.multiplatform`.

**Step 3: Run the deterministic build gate**

Run:

```bash
rtk scripts/check-android-build-contract.sh
rtk ./gradlew help --warning-mode=fail
rtk ./gradlew :kadre-core:allTests :kadre-android:testAndroidHostTest :samples:compose:shared:allTests :samples:hello-window-android:assembleDebug --warning-mode=fail
```

Expected: all exit 0 and no warning requests restoration of `android.builtInKotlin=false` or `android.newDsl=false`.

**Step 4: Commit**

```bash
rtk git add gradle.properties scripts/check-android-build-contract.sh
rtk git commit -m "build: remove obsolete Android compatibility flags"
```
