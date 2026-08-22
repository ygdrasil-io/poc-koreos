# X11 Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Eliminate cross-thread Xlib calls, make redraw and close deterministic, fix custom cursor encoding, and turn missing-display false success into a descriptive error.

**Architecture:** The X11 thread exclusively owns `Display*` and all Xlib calls. It polls the X connection fd together with the shared `PosixWakeup` fd, converts native events into a Kotlin queue, and applies the common iteration sequence. Windows delegate redraw/close to the loop; the loop removes state before native destruction.

**Tech Stack:** Kotlin/JVM 25, FFM, libX11, XRandR, `ffi:posix`, poll, Xvfb, Kotlin Test.

## Global Constraints

- Complete `01-contracts` and task 1 of `04-posix-wayland` first.
- The proxy thread may call only `PosixWakeup.signal()`; it must never call Xlib.
- Keep all native X11 callbacks and calls on the display-owning loop thread.
- An unavailable display is the approved explicit runtime error.
- Windows are removed from Kotlin registries before `XDestroyWindow` and before `XCloseDisplay`.
- Use `rtk` for shell commands and commit after each green task.

---

### Task 1: Replace `XSendEvent` wake-up with the shared POSIX fd

**Files:**
- Modify: `ffi/x11/build.gradle.kts`
- Modify: `kadre-x11/build.gradle.kts`
- Modify: `ffi/x11/src/jvmMain/kotlin/org/graphiks/kadre/ffi/x11/X11_h.kt`
- Replace: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11EventLoopProxy.kt`
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11EventLoop.kt`
- Replace: `kadre-x11/src/jvmTest/kotlin/org/graphiks/kadre/x11/X11EventLoopSmokeTest.kt`
- Create: `kadre-x11/src/jvmTest/kotlin/org/graphiks/kadre/x11/X11PumpTest.kt`

**Step 1: Write failing proxy and pump tests**

With fake `PosixWakeup`, `XPending`, and event-drain operations, assert:

- three wake-consume-rearm cycles;
- wake works when zero windows exist;
- a background proxy call records no Xlib operation;
- one pump drains all already-pending X events without blocking;
- poll watches both the X connection and wake fd;
- `WaitUntil` returns `WaitCancelled(deadline)` for either fd and `ResumeTimeReached` only at/after the deadline.

Use fake epoch milliseconds and fake poll results; no `Thread.sleep`.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-x11:jvmTest --tests '*X11EventLoopSmokeTest' --tests '*X11PumpTest'
```

Expected: current proxy requires a window and calls `XSendEvent`; current `WaitUntil` sleeps in a loop.

**Step 3: Add the X connection-fd binding**

Expose `XConnectionNumber(Display*) -> int` (or the exported equivalent accepted by the installed libX11) in `X11_h.kt`. `ffi:x11` and `kadre-x11` depend on `:ffi:posix`.

**Step 4: Replace the proxy and dispatch modes**

`X11EventLoopProxy` becomes:

```kotlin
class X11EventLoopProxy internal constructor(
    private val wakeup: PosixWakeup,
) : EventLoopProxy {
    override fun wakeUp() {
        check(wakeup.signal()) { "X11 wake-up failed: wake fd is closed" }
    }
}
```

Create one wake owner in `runApp`, pass it to the loop/proxies, and close it in the loop's outer `finally`. Replace `dispatchWait`, `dispatchWaitUntil`, and the busy 1 ms sleep with one poll-based pump:

```text
XPending/drain -> XFlush -> poll(xConnectionFd, wakeFd, timeout) -> drain wake -> XPending/drain
```

Never invoke `XNextEvent` unless `XPending > 0` or the X fd is readable.

**Step 5: Verify focused tests and forbidden calls**

```bash
rtk ./gradlew :ffi:posix:jvmTest :kadre-x11:jvmTest --tests '*X11EventLoopSmokeTest' --tests '*X11PumpTest'
rtk rg -n 'XSendEvent|xSendEvent' kadre-x11/src/jvmMain
```

Expected: tests pass; no proxy/main-source use of XSendEvent remains (generated binding may remain for unrelated protocol use only).

**Step 6: Commit**

```bash
rtk git add ffi/x11 kadre-x11
rtk git commit -m "fix(x11): wake loop through POSIX poll fd"
```

---

### Task 2: Enforce common iteration, redraw, shutdown, and close contracts

**Files:**
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11EventLoop.kt`
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11Window.kt`
- Create: `kadre-x11/src/jvmTest/kotlin/org/graphiks/kadre/x11/X11LoopContractTest.kt`
- Modify: `kadre-x11/src/jvmTest/kotlin/org/graphiks/kadre/x11/X11WindowTest.kt`

**Step 1: Add failing contract tests with a fake native adapter**

Assert:

- startup trace: `resumed -> newEvents(Init) -> canCreateSurfaces -> aboutToWait`;
- normal trace: `pump cause -> newEvents -> queued events -> aboutToWait -> poll`;
- ten redraw requests coalesce to one event and wake idle mode;
- `close()` removes the window and queued redraw before native destroy, emits one `Destroyed`, and is idempotent;
- `DestroyNotify` after programmatic close does not emit a second event;
- shutdown calls `destroySurfaces -> suspended`, closes every window, then closes display;
- events referencing an unknown/closed XID are discarded.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-x11:jvmTest --tests '*X11LoopContractTest' --tests '*X11WindowTest'
```

Expected: redraw is currently a no-op; close leaves the registry populated; shutdown omits `destroySurfaces`.

**Step 3: Add loop-owned event/redraw queues**

Keep an insertion-ordered pending-redraw ID set and a `ConcurrentLinkedQueue` for proxy/native work. `X11Window.requestRedraw()` calls `loop.requestRedraw(id)`, which wakes only when a new redraw ID is inserted. An actual `Expose` coalesces with/removes the same pending redraw.

After a pump returns:

```kotlin
handler.newEvents(loop, cause)
loop.drainOpenWindowEvents(handler)
handler.aboutToWait(loop)
```

Then compute the next timeout and poll. This makes `aboutToWait` the final callback before native waiting.

**Step 4: Centralize terminal close**

Pass the loop owner into `X11Window`. Its public `close()` calls `loop.closeWindow(id)`. On the loop thread:

1. remove the ID from `windows`, DnD/IME maps, redraw set, and event queue;
2. disable IME and free cached cursors;
3. issue `XDestroyWindow` and flush;
4. emit one `Destroyed` using the retained ID;
5. ignore a later native `DestroyNotify`.

On loop exit call `destroySurfaces`, close all remaining window resources, call `suspended`, then `XCloseDisplay` in `finally`.

**Step 5: Run tests**

```bash
rtk ./gradlew :kadre-x11:jvmTest --tests '*X11LoopContractTest' --tests '*X11WindowTest'
```

Expected: all ordering, redraw, close, and shutdown assertions pass.

**Step 6: Commit**

```bash
rtk git add kadre-x11/src/jvmMain kadre-x11/src/jvmTest
rtk git commit -m "fix(x11): enforce redraw and terminal close contracts"
```

---

### Task 3: Correct monochrome cursor packing and `XColor`

**Files:**
- Modify: `ffi/x11/src/jvmMain/kotlin/org/graphiks/kadre/ffi/x11/X11_h.kt`
- Create: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11CursorBitmap.kt`
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11EventLoop.kt`
- Create: `kadre-x11/src/jvmTest/kotlin/org/graphiks/kadre/x11/X11CursorBitmapTest.kt`

**Step 1: Write exact-byte failing tests**

For `packMonochromeCursor`, assert:

- width 8 uses one byte per row;
- width 9 uses two bytes per row and never spills a row into the next;
- XBM bit order is least-significant bit first (`x=0 -> 0x01`, `x=7 -> 0x80`);
- alpha zero clears the mask, alpha 255 sets it;
- luminance affects source only when mask is set;
- output size is `((width + 7) / 8) * height`, not `width * height`.

For `writeXColor`, assert the LP64 offsets:

```text
pixel@0 ulong, red@8 ushort, green@10 ushort, blue@12 ushort, flags@14 byte, pad@15 byte
```

White is `0xFFFF` in all channels and flags are `DoRed|DoGreen|DoBlue`; black has zero channels with the same flags.

Also test invalid hotspot, integer overflow, and dimensions larger than the server-reported `XQueryBestCursor` maximum.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-x11:jvmTest --tests '*X11CursorBitmapTest'
```

Expected: current implementation writes one byte per pixel and only sets the first byte of `XColor`.

**Step 3: Implement pure packing/validation helpers**

Create:

```kotlin
internal data class PackedCursor(val source: ByteArray, val mask: ByteArray)
internal fun packMonochromeCursor(image: CursorImage, alphaThreshold: Int = 1): PackedCursor
internal fun validateCursorGeometry(image: CursorImage, maxWidth: Int, maxHeight: Int): Boolean
internal fun writeXColor(segment: MemorySegment, red: UShort, green: UShort, blue: UShort)
```

Use checked `Long` arithmetic before allocating. Add the `XQueryBestCursor` binding; if the query itself is unavailable, retain conservative validation against positive `Int`/hotspot bounds but do not submit malformed data.

**Step 4: Use helpers in `createCustomCursor`**

Allocate exactly the packed arrays, fill both complete `XColor` structs, validate before any native allocation, and retain the current `finally` that frees source/mask pixmaps.

**Step 5: Run cursor and backend tests**

```bash
rtk ./gradlew :kadre-x11:jvmTest --tests '*X11CursorBitmapTest' --tests '*X11WindowTest'
```

Expected: exact bytes/layout and existing cursor behavior pass.

**Step 6: Commit**

```bash
rtk git add ffi/x11 kadre-x11
rtk git commit -m "fix(x11): pack cursor bitmaps and colors correctly"
```

---

### Task 4: Fail explicitly without X11 and validate under Xvfb

**Files:**
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11EventLoop.kt`
- Modify: `kadre-x11/src/jvmTest/kotlin/org/graphiks/kadre/x11/X11NativeIntegrationTest.kt`
- Create: `scripts/test-x11-xvfb.sh`

**Step 1: Write the missing-display failure test**

Launch a tiny test process with `DISPLAY=definitely-missing` because libX11 environment resolution is process-global. Assert non-zero exit and an error containing:

```text
backend=X11
operation=XOpenDisplay
DISPLAY=definitely-missing
```

The message may include the native cause but must not dump all environment variables.

**Step 2: Verify current false success**

```bash
rtk env DISPLAY=definitely-missing ./gradlew :kadre-x11:jvmTest --tests '*X11NativeIntegrationTest*missing*'
```

Expected: test fails because `runApp` currently returns successfully.

**Step 3: Replace graceful return with descriptive errors**

If libX11 is unavailable, `XOpenDisplay` is missing, or it returns null, throw `IllegalStateException` with backend/operation/DISPLAY context. Preserve `x11Running.set(false)` in `finally` so a failed attempt does not poison subsequent runs.

**Step 4: Add the Xvfb integration driver**

The script must:

1. allocate a free display number and temporary Xauthority/log directory;
2. start `Xvfb` with a bounded readiness probe using `xdpyinfo`;
3. run the native integration and common conformance adapter tests;
4. kill/wait the exact Xvfb PID in `trap`;
5. return the Gradle exit code.

No fixed sleep is allowed; poll readiness for at most ten seconds.

**Step 5: Run integration**

```bash
rtk chmod +x scripts/test-x11-xvfb.sh
rtk scripts/test-x11-xvfb.sh
```

Expected: exit 0; lifecycle/redraw/wake/close tests use the real X server. The same script is run in glibc and musl containers in plan 08.

**Step 6: Commit**

```bash
rtk git add kadre-x11 scripts/test-x11-xvfb.sh
rtk git commit -m "test(x11): make display failures and Xvfb blocking"
```
