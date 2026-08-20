# Web JS and Wasm Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Give Web input exact physical coordinates and identity, make DPR/resize state coherent, fix deadline/wake/close scheduling, and eliminate JS/Wasm bridge divergence.

**Architecture:** Pure `webMain` components own coordinate conversion, pointer-primary state, queued metrics, and scheduler state. JS and Wasm provide thin DOM/timer adapters only. One browser scheduler owns RAF/timeout IDs and generations; `WebEventLoop` owns window closure and filters every queued event through its live registry.

**Tech Stack:** Kotlin/JS IR, Kotlin/Wasm JS, browser DOM, Pointer Events, ResizeObserver, requestAnimationFrame, setTimeout, Karma/Chrome Headless, Kotlin Test.

## Global Constraints

- Complete `01-contracts` first.
- The approved `WebWindowEvent` pointer change is the only public break in this plan.
- Keep compatibility shims for unrelated `WebDomBridge` methods; do not create an additional bridge API break.
- Every position exposed to Kadre is physical canvas-relative pixels.
- Every scheduler timestamp is Unix epoch milliseconds; RAF's page-origin timestamp is never used as `StartCause.start`.
- JS and Wasm must call the same conceptual bridge methods and pass the same event fields.
- Use `rtk` for shell commands and commit after each green task.

---

### Task 1: Define physical coordinate and pointer-primary contracts

**Files:**
- Create: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/DomCoordinateTransform.kt`
- Create: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebPointerTracker.kt`
- Create: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/DomCoordinateTransformTest.kt`
- Create: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebPointerTrackerTest.kt`

**Step 1: Write failing pure tests**

For:

```kotlin
data class CanvasMetrics(
    val leftCss: Double,
    val topCss: Double,
    val widthCss: Double,
    val heightCss: Double,
    val devicePixelRatio: Double,
)
```

assert:

- `(clientX=110, clientY=70, left=10, top=20, dpr=2)` -> `(200, 100)`;
- negative canvas-relative positions are retained (pointer capture outside canvas);
- fractional origins/DPR retain `Double` precision;
- non-finite or non-positive DPR normalizes to 1.0;
- physical canvas size is `round(widthCss*dpr), round(heightCss*dpr)`.

For `WebPointerTracker`, assert:

- the first active touch is primary even when its DOM ID is 42;
- a second touch is not primary;
- after primary end, the oldest remaining active touch becomes primary;
- mouse is always primary and does not alter touch state;
- cancel/leave clears the right ID and close clears all state.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest --tests '*DomCoordinateTransformTest' --tests '*WebPointerTrackerTest'
```

Expected: missing components.

**Step 3: Implement pure helpers**

Expose internal functions:

```kotlin
internal fun CanvasMetrics.toPhysical(clientX: Double, clientY: Double): PhysicalPosition<Double>
internal fun CanvasMetrics.physicalSize(): PhysicalSize<Int>
```

`WebPointerTracker` uses insertion-ordered active touch IDs and returns a snapshot containing `pointerId`, `primary`, and mapped source/kind. It contains no DOM imports.

**Step 4: Run both target tests**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest :kadre-web-common:wasmJsBrowserTest --tests '*DomCoordinateTransformTest' --tests '*WebPointerTrackerTest'
```

Expected: identical pure tests pass in JS and Wasm.

**Step 5: Commit**

```bash
rtk git add kadre-web-common/src/webMain kadre-web-common/src/webTest
rtk git commit -m "test(web): define coordinates and pointer identity"
```

---

### Task 2: Apply the approved `WebWindowEvent` pointer model

**Files:**
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebTypes.kt`
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/DomEventMapper.kt`
- Modify: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/DomEventMapperTest.kt`

**Step 1: Replace old tests with failing semantic mappings**

Test exact mapping for mouse, touch, and pen/tablet-like sources:

- enter/leave preserve position, pointer ID, primary, and kind;
- movement preserves position and `PointerSource`;
- button preserves position, pointer ID/primary, and `ButtonSource`;
- touch ID 42 can be primary; no `id == 0` inference remains;
- wheel and drag positions are already physical and pass through unchanged.

**Step 2: Change only the approved event variants**

Use models equivalent to:

```kotlin
data class PointerMoved(
    val x: Double,
    val y: Double,
    val pointerId: Long,
    val primary: Boolean,
    val source: PointerSource,
) : WebWindowEvent

data class PointerEntered(
    val x: Double,
    val y: Double,
    val pointerId: Long,
    val primary: Boolean,
    val kind: PointerKind,
) : WebWindowEvent

data class PointerLeft(/* same position/id/primary/kind */) : WebWindowEvent

data class PointerButton(
    val x: Double,
    val y: Double,
    val pointerId: Long,
    val primary: Boolean,
    val button: ButtonSource,
    val state: WebKeyState,
) : WebWindowEvent
```

Remove `MouseInput`. Add explicit `primary` to the existing `Touch` variant or route touch PointerEvents through the canonical pointer variants; in either case the mapper must never derive primary from numeric ID zero. Keep DnD/gesture/keyboard variants otherwise source-compatible.

**Step 3: Update mapper exhaustively**

Construct `WindowEvent.PointerMoved`, `PointerEntered`, `PointerLeft`, and `PointerButton` from every stored field. No placeholder `(0,0)`, null position, forced mouse source, or forced `primary=true` remains.

**Step 4: Run mapper tests**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest :kadre-web-common:wasmJsBrowserTest --tests '*DomEventMapperTest'
```

Expected: all exact mapping tests pass in both targets.

**Step 5: Commit**

```bash
rtk git add kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebTypes.kt kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/DomEventMapper.kt kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/DomEventMapperTest.kt
rtk git commit -m "feat(web): carry pointer position source and identity"
```

---

### Task 3: Make JS and Wasm DOM input/metrics equivalent

**Files:**
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebDomBridge.kt`
- Modify: `kadre-web-common/src/jsMain/kotlin/org/graphiks/kadre/web/JsWebDomBridge.kt`
- Modify: `kadre-web-common/src/wasmJsMain/kotlin/org/graphiks/kadre/web/WasmJsWebDomBridge.kt`
- Create: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebBridgeContractTest.kt`

**Step 1: Write a target-neutral bridge event script**

Model one canvas at CSS rect `(left=10, top=20, width=300, height=150)`, DPR 2. Script move/enter/leave/down/up, two touches with IDs 42/7, wheel, drag enter/move/drop, resize, DPR 3, and detach.

The shared assertion expects:

- every coordinate uses `(client - rect.origin) * DPR`;
- button coordinates equal their actual DOM event location;
- first touch ID 42 is primary;
- wheel/pinch center and drag coordinates use the same transform;
- resize physical size is 600x300 at DPR 2;
- DPR 3 transition reports coherent scale 3 and size 900x450 in the same queued iteration;
- no event after detach.

JS and Wasm test adapters feed the same script through their bridge-specific DOM wrappers.

**Step 2: Verify current failures**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest :kadre-web-common:wasmJsBrowserTest --tests '*WebBridgeContractTest'
```

Expected: viewport coordinates are unadjusted, resize is CSS-sized, enter/leave/button lack position, and touch primary assumes ID zero.

**Step 3: Add one metrics read per DOM event**

Both bridges obtain `getBoundingClientRect()` and current DPR, create `CanvasMetrics`, then call the pure transform. Do this for pointer, touch, wheel/pinch center, and drag events. A target adapter may use dynamic JS or `@JsFun`, but transformation logic remains in `webMain`.

ResizeObserver callbacks send physical dimensions. On DPR change, recompute current physical size and enqueue scale plus resize together. `WebEventLoop` applies both cached values before dispatching either callback, using an internal queued-metrics item rather than adding another public sealed `WebWindowEvent` subtype.

**Step 4: Unify pointer-events bridge method compatibly**

Implement `setPointerEvents(canvasId, value)` in JS exactly as Wasm does. Retain the legacy public `setCursorHittest(canvasId, Boolean)` as deprecated compatibility:

```kotlin
fun setPointerEvents(canvasId: String, value: String) {
    setCursorHittest(canvasId, value != "none")
}

@Deprecated("Use setPointerEvents")
fun setCursorHittest(canvasId: String, hittest: Boolean) { }
```

Concrete JS/Wasm implementations override the canonical method. This fixes JS hit-testing without introducing an unapproved bridge break.

**Step 5: Run both suites**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest :kadre-web-common:wasmJsBrowserTest --tests '*WebBridgeContractTest' --tests '*WebWindowCursorTest' --tests '*WebWindowSizeTest'
```

Expected: target traces are identical and metrics are physical/coherent.

**Step 6: Commit**

```bash
rtk git add kadre-web-common
rtk git commit -m "fix(web): normalize DOM input and DPR metrics"
```

---

### Task 4: Replace RAF/timeout duplication with one deterministic scheduler

**Files:**
- Create: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/BrowserScheduler.kt`
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebEventLoop.kt`
- Modify: `kadre-web-common/src/jsMain/kotlin/org/graphiks/kadre/web/JsWebEventLoop.kt`
- Modify: `kadre-web-common/src/wasmJsMain/kotlin/org/graphiks/kadre/web/WasmJsWebEventLoop.kt`
- Replace: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebEventLoopTest.kt`

**Step 1: Write failing fake-scheduler tests**

Inject operations:

```kotlin
interface BrowserSchedulingApi {
    fun epochNowMillis(): Long
    fun requestAnimationFrame(callback: () -> Unit): Int
    fun cancelAnimationFrame(id: Int)
    fun setTimeout(delayMillis: Int, callback: () -> Unit): Int
    fun clearTimeout(id: Int)
}
```

Assert:

- startup order is `resumed -> newEvents(Init) -> canCreateSurfaces -> aboutToWait`;
- `Wait` schedules nothing until an event/wake;
- an event before `WaitUntil(10_000)` clears timeout and yields `WaitCancelled(10_000)` immediately;
- a deadline callback at 10_005 yields `ResumeTimeReached(10_000, 10_005)`;
- RAF's relative timestamp is never used;
- rearming cancels previous timeout/RAF IDs and stale generations do nothing;
- three proxy wake cycles work after consumption;
- redraw coalesces and wakes idle mode;
- `exit`/close cancel every outstanding ID.

No real timers or browser sleeps are allowed.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest :kadre-web-common:wasmJsBrowserTest --tests '*WebEventLoopTest'
```

Expected: current timeout is not cancellable; event wake under `WaitUntil` is delayed; RAF-relative timestamp becomes epoch `start`.

**Step 3: Implement scheduler state in `webMain`**

`BrowserScheduler` owns one RAF ID, one timeout ID, a generation, requested deadline, and pending wake cause. `signalEvent` cancels the deadline before scheduling an immediate RAF. `onDeadline` samples `epochNowMillis`; `onAnimationFrame` never accepts a timestamp.

The loop's one iteration is:

```kotlin
handler.newEvents(this, scheduler.takeStartCause(controlFlow))
applyQueuedMetricsAtomically()
drainEventsForLiveWindows(handler)
handler.aboutToWait(this)
scheduler.arm(controlFlow, hasPendingWork)
```

**Step 4: Make JS/Wasm thin adapters**

- JS implements `epochNowMillis` with `Date.now()`, and uses `cancelAnimationFrame`/`clearTimeout`.
- Wasm declares all five browser functions with explicit JS interop, including cancellation.
- Remove duplicated `rafPending`, timeout logic, and pending-handler fields.

**Step 5: Run scheduler tests**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest :kadre-web-common:wasmJsBrowserTest --tests '*WebEventLoopTest'
```

Expected: identical deterministic traces pass in both targets.

**Step 6: Commit**

```bash
rtk git add kadre-web-common
rtk git commit -m "fix(web): cancel and rearm browser scheduling"
```

---

### Task 5: Make Web close terminal and safe area physical

**Files:**
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebEventLoop.kt`
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebWindow.kt`
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebDomBridge.kt`
- Modify: `kadre-web-common/src/jsMain/kotlin/org/graphiks/kadre/web/JsWebDomBridge.kt`
- Modify: `kadre-web-common/src/wasmJsMain/kotlin/org/graphiks/kadre/web/WasmJsWebDomBridge.kt`
- Create: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebCloseAndSafeAreaTest.kt`

**Step 1: Write failing tests**

- `close()` twice detaches once, removes the window, drops queued events/redraw, emits one `Destroyed`, and cancels RAF/timeout when last window closes.
- A bridge callback after detach/close does nothing.
- safe-area CSS values `(10.25, 5, 0, 1.5)` at DPR 2 become physical `(21,10,0,3)` using `roundToInt`.
- DPR 3 immediately changes safe-area conversion without recreating the window.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest :kadre-web-common:wasmJsBrowserTest --tests '*WebCloseAndSafeAreaTest'
```

Expected: window remains registered, close can detach repeatedly, and CSS insets are returned unscaled/truncated.

**Step 3: Route closure through loop ownership**

`WebEventLoop` constructs `WebWindow` with an internal close callback while preserving its existing public constructors. `closeWindow(id)` marks/removes first, nulls bridge callbacks, detaches, filters queues, emits one `Destroyed`, and stops scheduler when no live windows remain. `pagehide` calls the same path.

**Step 4: Return physical safe area**

Bridge implementations parse CSS pixels as `Double` and return/forward them to a shared conversion with the active DPR. Ensure the temporary JS measurement element is removed in `finally`; Wasm follows the same edge order and rounding.

**Step 5: Run close/safe-area and full Web tests**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest :kadre-web-common:wasmJsBrowserTest
```

Expected: every Web test passes in both browser targets.

**Step 6: Commit**

```bash
rtk git add kadre-web-common
rtk git commit -m "fix(web): make close terminal and scale safe area"
```

---

### Task 6: Correct JS RGBA widening and run real browser smoke tests

**Files:**
- Modify: `kadre-web-common/src/jsMain/kotlin/org/graphiks/kadre/web/JsWebDomBridge.kt`
- Create: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebCursorRgbaTest.kt`
- Create: `scripts/test-web-browsers.sh`

**Step 1: Write the failing RGBA test**

Feed bytes `[0x00, 0x7F, 0x80, 0xFF]` through a pure widening helper and assert integers `[0,127,128,255]`. Also create a 1x1 cursor in the JS browser test and sample its ImageData pixel.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-web-common:jsBrowserTest --tests '*WebCursorRgbaTest'
```

Expected: signed `Byte` values for 0x80/0xFF are passed as -128/-1.

**Step 3: Widen explicitly**

Use:

```kotlin
data[i] = rgba[i].toInt() and 0xFF
```

Validate cursor dimensions/hotspot and checked byte count before allocating ImageData.

**Step 4: Add the browser gate**

`scripts/test-web-browsers.sh` locates Chrome/Chromium, sets `CHROME_BIN`, then runs JS and Wasm browser tests with a 10-minute external timeout. It must fail if either target is skipped, reports zero executed tests, or the browser process crashes.

**Step 5: Run and commit**

```bash
rtk chmod +x scripts/test-web-browsers.sh
rtk scripts/test-web-browsers.sh
rtk git add kadre-web-common scripts/test-web-browsers.sh
rtk git commit -m "test(web): verify RGBA and JS Wasm browser parity"
```
