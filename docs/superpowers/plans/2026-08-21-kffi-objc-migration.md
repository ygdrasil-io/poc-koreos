# kffi-objc Binding Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Kadre’s checked-in Objective-C binding with the published `org.graphiks:kffi-objc:1.0.0-SNAPSHOT` dependency while preserving the AppKit source API.

**Architecture:** `kadre-appkit:jvmMain` will consume `kffi-objc` through the version catalog instead of the local `:ffi:objc` project. The local generated source tree and its regeneration automation will be removed; AppKit imports remain under `org.graphiks.kffi.objc`, with source edits limited to any compile-proven API differences and stale binding comments.

**Tech Stack:** Kotlin Multiplatform, Gradle Kotlin DSL, Gradle version catalogs, JVM 25, Panama FFM, `org.graphiks:kffi-objc:1.0.0-SNAPSHOT`.

**Spec:** `docs/superpowers/specs/2026-08-21-kffi-objc-migration-design.md`

## Global Constraints

- Use `org.graphiks:kffi-objc:1.0.0-SNAPSHOT` through `libs.kffi.objc`.
- Keep the existing Central Portal snapshots repository in `settings.gradle.kts`; do not add a module-local repository.
- Keep the JVM target at JDK 25 and preserve the `org.graphiks.kffi.objc` package imports.
- Do not change Win32, X11, Wayland, POSIX, UIKit, or unrelated AppKit behavior.
- Do not retain a compatibility copy of the deleted `ffi/objc` binding.
- Configuration-only and generated-source deletion work is exempt from introducing a new unit test; every source behavior change must follow the red-green-refactor workflow.

---

### Task 1: Switch `kadre-appkit` to the external dependency

**Files:**
- Modify: `gradle/libs.versions.toml:19-33`
- Modify: `kadre-appkit/build.gradle.kts:1-30`
- Modify: `settings.gradle.kts:100-106`
- Test: Gradle dependency resolution and JVM compilation tasks

**Interfaces:**
- Consumes: the existing `org.graphiks` snapshot repository from `settings.gradle.kts`.
- Produces: the version-catalog alias `libs.kffi.objc` and an AppKit `jvmMain` dependency on `org.graphiks:kffi-objc:1.0.0-SNAPSHOT`.

- [ ] **Step 1: Add the version and library alias**

Insert the following entries in the existing `[versions]` and `[libraries]` sections of `gradle/libs.versions.toml`:

```toml
kffi-objc = "1.0.0-SNAPSHOT"
# kffi Objective-C FFI snapshot
kffi-objc = { module = "org.graphiks:kffi-objc", version.ref = "kffi-objc" }
```

Place the version alongside `kffi-wayland` and the library alias alongside the existing KFFI aliases.

- [ ] **Step 2: Replace the project dependency**

In `kadre-appkit/build.gradle.kts`, replace:

```kotlin
api(project(":ffi:objc"))
```

with:

```kotlin
api(libs.kffi.objc)
```

Keep `api(project(":kadre-core"))` unchanged.

- [ ] **Step 3: Remove the local project registration**

Delete this line from `settings.gradle.kts`:

```kotlin
include(":ffi:objc")
```

Do not remove the remaining `ffi` project registrations.

- [ ] **Step 4: Verify dependency resolution and compilation**

Run:

```bash
./gradlew :kadre-appkit:dependencies --configuration jvmCompileClasspath
./gradlew :kadre-appkit:compileKotlinJvm --stacktrace
```

Expected: the dependency report contains `org.graphiks:kffi-objc:1.0.0-SNAPSHOT`, the old `project :ffi:objc` dependency is absent, and `compileKotlinJvm` ends with `BUILD SUCCESSFUL`. If resolution fails, preserve the requested coordinate and investigate the configured snapshot repository rather than adding a local fallback.

- [ ] **Step 5: Commit the dependency switch**

```bash
git add gradle/libs.versions.toml kadre-appkit/build.gradle.kts settings.gradle.kts
git commit -m "build: consume kffi-objc for AppKit bindings"
```

### Task 2: Remove the checked-in binding and local regeneration automation

**Files:**
- Delete: `ffi/objc/build.gradle.kts`
- Delete: `ffi/objc/src/jvmMain/kotlin/org/graphiks/kffi/objc/**` (the 806 generated Kotlin sources)
- Delete: `scripts/regen-objc-bindings.sh`
- Delete: `.github/workflows/regen-objc-bindings.yml`
- Test: repository reference scan and AppKit compilation

**Interfaces:**
- Consumes: the external dependency produced by Task 1.
- Produces: a repository with no local Objective-C binding module or generator workflow.

- [ ] **Step 1: Remove the local generated module**

Delete the entire `ffi/objc` tree. The deletion must include its Gradle module file and all generated Kotlin sources; no source file from this directory should be copied into AppKit.

- [ ] **Step 2: Remove local regeneration entry points**

Delete `scripts/regen-objc-bindings.sh` and `.github/workflows/regen-objc-bindings.yml`, which only regenerate the deleted `ffi/objc` tree. Keep the Win32 and X11 kextract automation intact.

- [ ] **Step 3: Verify there are no stale local-binding references**

Run:

```bash
rg -n 'ffi:objc|regen-objc-bindings|ffi/objc|org\.graphiks\.kadre\.ffi\.objc' . --glob '!**/build/**' --glob '!docs/kadre/api/**'
```

Expected: no matches. `org.graphiks.kffi.objc` imports are valid and must remain.

- [ ] **Step 4: Re-run the AppKit compile after deletion**

```bash
./gradlew :kadre-appkit:compileKotlinJvm --stacktrace
```

Expected: `BUILD SUCCESSFUL`; the compile must resolve all ObjC wrappers from `kffi-objc` after the local source tree is gone.

- [ ] **Step 5: Commit the removal**

```bash
git add -u ffi/objc scripts/regen-objc-bindings.sh .github/workflows/regen-objc-bindings.yml
git commit -m "build: remove local Objective-C binding generator"
```

### Task 3: Align AppKit documentation and smoke-test wording

**Files:**
- Modify: `kadre-appkit/build.gradle.kts:2-8`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/Placeholder.kt:10-13`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitWindow.kt:1957-1962`
- Modify: `kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/SmokeTest.kt:12-20, 31-42`
- Test: `:kadre-appkit:jvmTest`

**Interfaces:**
- Consumes: the unchanged `org.graphiks.kffi.objc` API from Task 2.
- Produces: comments and test descriptions that identify `kffi-objc` as the binding source instead of the deleted local generator.

- [ ] **Step 1: Update module-level AppKit comments**

Change the `kadre-appkit/build.gradle.kts` description from “generated by kextract” to wording that says the backend consumes the `kffi-objc` Panama FFM bindings.

In `Placeholder.kt`, keep the existing event-loop placeholder but change its
binding reference from `kextract + AppKit FFM` to `AppKit FFM bindings from
kffi-objc`.

- [ ] **Step 2: Update the manual layout comment**

In `AppKitWindow.kt`, replace the reference to `--include-objc-class` and `kextract v0.0.2` with a statement that the manually declared layout is used for ObjC classes not covered by the generated `kffi-objc` wrappers.

- [ ] **Step 3: Update smoke-test descriptions**

In `SmokeTest.kt`, replace “generated by kextract v0.0.0-test6” with “provided by kffi-objc”, and describe the tests as checking the published AppKit classes/protocols and runtime bridge. Do not change the assertions or test setup.

- [ ] **Step 4: Run the AppKit tests**

```bash
./gradlew :kadre-appkit:jvmTest --stacktrace
```

Expected: the task completes successfully. If the host is not macOS and existing native integration tests are environment-gated, report those gates separately from compilation failures; do not reintroduce local bindings to satisfy them.

- [ ] **Step 5: Commit wording updates**

```bash
git add kadre-appkit/build.gradle.kts kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/Placeholder.kt kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitWindow.kt kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/SmokeTest.kt
git commit -m "docs: describe AppKit bindings as kffi-objc"
```

### Task 4: Final regression and acceptance verification

**Files:**
- Inspect: all tracked files changed by Tasks 1-3
- Test: Gradle compile, AppKit tests, reference scans, diff checks

**Interfaces:**
- Consumes: the completed dependency migration and deleted local module.
- Produces: fresh verification evidence for the acceptance criteria in the spec.

- [ ] **Step 1: Run the focused compile and tests together**

```bash
./gradlew :kadre-appkit:compileKotlinJvm :kadre-appkit:jvmTest --stacktrace
```

Expected: both tasks complete with `BUILD SUCCESSFUL` and no unresolved `org.graphiks.kffi.objc` symbols.

- [ ] **Step 2: Confirm only the intended binding references remain**

```bash
rg -n 'org\.graphiks\.kffi\.objc' kadre-appkit/src
rg -n 'ffi:objc|regen-objc-bindings|ffi/objc|generated by kextract|kextract v0\.0\.' kadre-appkit scripts .github gradle settings.gradle.kts --glob '!**/build/**'
```

Expected: the first command lists the existing AppKit imports; the second command returns no matches.

- [ ] **Step 3: Check the diff and worktree**

```bash
git diff --check
git status --short
git diff --stat HEAD~3..HEAD
```

Expected: no whitespace errors, no unrelated modifications, and the diff consists of the dependency switch, local ObjC binding/automation removal, and AppKit wording updates.

- [ ] **Step 4: Record the final verification result**

Report the exact Gradle task results, any macOS-only test gating, the final commit identifiers, and any unresolved external snapshot-repository issue without claiming success beyond the observed output.
