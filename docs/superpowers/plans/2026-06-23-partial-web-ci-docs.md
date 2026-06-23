# Partial Web, CI, and Deferred Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the first safe slice of Kadre's partial features by wiring Web pointer controls and aligning CI/docs around the current deferred-feature state.

**Architecture:** Keep `webMain` DOM-free by extending `WebDomBridge` with a pointer-events method and routing `WebWindow` behavior through the bridge. Concrete JS and wasmJs bridges perform target-specific DOM operations. Documentation and CI changes are isolated from runtime code.

**Tech Stack:** Kotlin Multiplatform, Kotlin/JS, Kotlin/Wasm, Gradle, GitHub Actions, Markdown docs.

---

### Task 1: Add Failing Web Pointer Control Tests

**Files:**
- Create: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebWindowCursorTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package org.graphiks.kadre.web

import org.graphiks.kadre.core.CursorGrabMode
import org.graphiks.kadre.core.RequestError
import org.graphiks.kadre.core.WindowRequestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class CursorRecordingBridge : WebDomBridge {
    override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null
    val pointerLockRequests = mutableListOf<String>()
    var exitPointerLockCalls = 0
    val pointerEventsCalls = mutableListOf<Pair<String, String>>()

    override fun attach(targetElementId: String) {}
    override fun detach() {}

    override fun requestPointerLock(canvasId: String) {
        pointerLockRequests += canvasId
    }

    override fun exitPointerLock() {
        exitPointerLockCalls += 1
    }

    override fun setPointerEvents(canvasId: String, pointerEventsValue: String) {
        pointerEventsCalls += canvasId to pointerEventsValue
    }
}

class WebWindowCursorTest {
    @Test
    fun `setCursorGrab Locked requests pointer lock and reports success`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorGrab(CursorGrabMode.Locked)

        assertEquals(WindowRequestResult.Success, result)
        assertEquals(listOf("canvas"), bridge.pointerLockRequests)
    }

    @Test
    fun `setCursorGrab None exits pointer lock and reports success`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorGrab(CursorGrabMode.None)

        assertEquals(WindowRequestResult.Success, result)
        assertEquals(1, bridge.exitPointerLockCalls)
    }

    @Test
    fun `setCursorGrab Confined remains unsupported on Web`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorGrab(CursorGrabMode.Confined)

        val failure = assertIs<WindowRequestResult.Failure>(result)
        assertIs<RequestError.Unsupported>(failure.error)
        assertEquals(emptyList(), bridge.pointerLockRequests)
    }

    @Test
    fun `setCursorHittest false disables pointer events and reports success`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorHittest(false)

        assertEquals(WindowRequestResult.Success, result)
        assertEquals(listOf("canvas" to "none"), bridge.pointerEventsCalls)
    }

    @Test
    fun `setCursorHittest true restores pointer events and reports success`() {
        val bridge = CursorRecordingBridge()
        val window = WebWindow("canvas", bridge)

        val result = window.setCursorHittest(true)

        assertEquals(WindowRequestResult.Success, result)
        assertEquals(listOf("canvas" to "auto"), bridge.pointerEventsCalls)
    }
}
```

- [ ] **Step 2: Verify the tests fail**

Run: `rtk ./gradlew :kadre-web-common:jsTest --no-daemon --stacktrace`

Expected: compilation fails because `WebDomBridge.setPointerEvents` does not exist and `WebWindow.setCursorGrab(Locked)` still returns `Failure`.

### Task 2: Implement Web Pointer Control Behavior

**Files:**
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebDomBridge.kt`
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebWindow.kt`
- Modify: `kadre-web-common/src/jsMain/kotlin/org/graphiks/kadre/web/JsWebDomBridge.kt`
- Modify: `kadre-web-common/src/wasmJsMain/kotlin/org/graphiks/kadre/web/WasmJsWebDomBridge.kt`

- [ ] **Step 1: Extend the bridge API**

Add this method to `WebDomBridge` near the cursor methods:

```kotlin
fun setPointerEvents(canvasId: String, pointerEventsValue: String) { /* no-op by default */ }
```

- [ ] **Step 2: Update WebWindow behavior**

Change `setCursorGrab(CursorGrabMode.Locked)` so it calls `bridge.requestPointerLock(canvasElementId)` and returns `WindowRequestResult.Success`. Keep `Confined` unsupported. Change `setCursorHittest` so `false` delegates `"none"`, `true` delegates `"auto"`, then returns success.

- [ ] **Step 3: Implement JS bridge DOM calls**

In `JsWebDomBridge`, implement:

```kotlin
override fun setCssCursor(canvasId: String, cssCursorValue: String) {
    val el = document.getElementById(canvasId) ?: targetElement ?: return
    el.asDynamic().style.cursor = cssCursorValue
}

override fun setPointerEvents(canvasId: String, pointerEventsValue: String) {
    val el = document.getElementById(canvasId) ?: targetElement ?: return
    el.asDynamic().style.pointerEvents = pointerEventsValue
}

override fun requestPointerLock(canvasId: String) {
    val el = document.getElementById(canvasId) ?: targetElement ?: return
    try {
        val d = el.asDynamic()
        when {
            d.requestPointerLock != null -> d.requestPointerLock()
            d.webkitRequestPointerLock != null -> d.webkitRequestPointerLock()
        }
    } catch (_: Throwable) {}
}

override fun exitPointerLock() {
    try {
        val d = document.asDynamic()
        when {
            d.exitPointerLock != null -> d.exitPointerLock()
            d.webkitExitPointerLock != null -> d.webkitExitPointerLock()
        }
    } catch (_: Throwable) {}
}
```

- [ ] **Step 4: Implement wasmJs bridge DOM calls**

Add `@JsFun` helpers for CSS cursor, pointer-events, request pointer lock, and exit pointer lock, then call them from `WasmJsWebDomBridge` overrides.

- [ ] **Step 5: Verify Web tests pass**

Run: `rtk ./gradlew :kadre-web-common:allTests --no-daemon --stacktrace`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Align CI and Deferred Documentation

**Files:**
- Create: `DEFERRED.md`
- Modify: `.github/workflows/ci.yml`
- Modify: `docs/features/gaps.md`
- Modify: `docs/kadre/index.md`
- Modify: `docs/kadre/specs.md`

- [ ] **Step 1: Add `codex/**` deep CI triggers**

For each deep job condition that already checks `claude/**`, add:

```yaml
startsWith(github.ref, 'refs/heads/codex/') ||
startsWith(github.head_ref, 'codex/') ||
```

Keep existing `master` and `claude/**` behavior.

- [ ] **Step 2: Create root deferred-feature index**

Create `DEFERRED.md` with concise categories for platform-limited behavior, intentionally deferred native backend features, and verification gaps. Include the Web pointer-control status after this PR.

- [ ] **Step 3: Update feature docs**

Update docs so they no longer claim Web DOM bridges are stubs, Web Pointer Lock is unwired, or Web cursor hittest is TODO. Keep native backend gaps such as Wayland monitor geometry and X11 keyboard text.

- [ ] **Step 4: Verify docs references**

Run: `rtk rg -n "DEFERRED\\.md|Pointer Lock|cursor hit-testing|Bridges are stubs" DEFERRED.md docs/features docs/kadre/index.md docs/kadre/specs.md`

Expected: references point to existing `DEFERRED.md`; no stale "Bridges are stubs" statement remains.

### Task 4: Final Verification, Review, PR, and Merge

**Files:**
- No direct source files; this task validates and publishes the branch.

- [ ] **Step 1: Run targeted verification**

Run:

```bash
rtk ./gradlew :kadre-web-common:allTests :kadre-js:compileKotlinJs :kadre-wasm:compileKotlinWasmJs --no-daemon --stacktrace
rtk ./gradlew :kadre-core:jvmTest :kadre-appkit:jvmTest :kadre:jvmTest --no-daemon --stacktrace
```

Expected: both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 2: Request code review**

Dispatch a reviewer sub-agent with the branch diff, this plan, and the spec. Fix Critical and Important findings before pushing.

- [ ] **Step 3: Commit and push**

Stage only the intended files and commit:

```bash
rtk git add DEFERRED.md .github/workflows/ci.yml docs/features/gaps.md docs/kadre/index.md docs/kadre/specs.md docs/superpowers/specs/2026-06-23-partial-web-ci-docs-design.md docs/superpowers/plans/2026-06-23-partial-web-ci-docs.md kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebDomBridge.kt kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebWindow.kt kadre-web-common/src/jsMain/kotlin/org/graphiks/kadre/web/JsWebDomBridge.kt kadre-web-common/src/wasmJsMain/kotlin/org/graphiks/kadre/web/WasmJsWebDomBridge.kt kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebWindowCursorTest.kt
rtk git commit -m "Finish safe partial Web and CI gaps"
rtk git push -u origin codex/complete-partial-web-ci-docs
```

- [ ] **Step 4: Open PR**

Create a draft PR targeting the remote default branch with summary and test plan.

- [ ] **Step 5: Correct PR feedback and merge**

If review or CI reports actionable issues, address them on the branch, rerun relevant checks, push corrections, and merge when checks and review are acceptable.
