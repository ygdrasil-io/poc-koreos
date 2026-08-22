# POSIX Portability and Wayland Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Provide a libc-name-independent Linux wake primitive and make Wayland wake, keyboard, output, redraw, close, and failure behavior conform on both glibc and musl.

**Architecture:** A new JVM-only `ffi:posix` module owns process symbol lookup, poll descriptors, eventfd/pipe wake-ups, and close/read/write calls. Wayland owns a durable registry object with all outputs and a single mutable device filter. Its loop queues redraw independently from compositor configure events and filters all work through an open-window registry.

**Tech Stack:** Kotlin/JVM 25, Foreign Function and Memory API (FFM), libc, eventfd, pipe2/fcntl fallback, libwayland-client, xkbcommon, headless Weston, JUnit/Kotlin Test.

## Global Constraints

- Complete `01-contracts` first.
- Never hard-code `libc.so.6` outside an ordered optional candidate list.
- Never let an exception cross a native Wayland upcall boundary.
- A missing compositor, required protocol, or wake primitive is an error, not a no-op success.
- Every fd, mapping, arena, proxy, and callback has one idempotent owner.
- Use `rtk` for shell commands and commit after each green task.

---

### Task 1: Create the reusable POSIX symbol and wake layer

**Files:**
- Modify: `settings.gradle.kts`
- Create: `ffi/posix/build.gradle.kts`
- Create: `ffi/posix/src/jvmMain/kotlin/org/graphiks/kadre/ffi/posix/PosixSymbols.kt`
- Create: `ffi/posix/src/jvmMain/kotlin/org/graphiks/kadre/ffi/posix/PosixWakeup.kt`
- Create: `ffi/posix/src/jvmMain/kotlin/org/graphiks/kadre/ffi/posix/PollFd.kt`
- Create: `ffi/posix/src/jvmTest/kotlin/org/graphiks/kadre/ffi/posix/PosixSymbolsTest.kt`
- Create: `ffi/posix/src/jvmTest/kotlin/org/graphiks/kadre/ffi/posix/PosixWakeupTest.kt`

**Step 1: Add the module and failing tests**

Add `include(":ffi:posix")`. Create a JVM 25 KMP module with `kotlin("test")` in `jvmTest`.

Tests must assert:

- `PosixSymbols.find("close")` resolves on Linux without asserting a particular SONAME;
- three `signal -> poll readable -> drain` cycles succeed;
- ten concurrent signals coalesce without blocking and a later post-drain signal remains usable;
- `close()` is idempotent and `signal()` after close returns a typed failure/false;
- forcing `eventfd` unavailable selects the pipe fallback;
- both pipe descriptors are non-blocking and close-on-exec.

Inject symbol lookup and syscall operations into `PosixWakeup.open` so the fallback is deterministic without changing the host libc.

**Step 2: Verify failure**

```bash
rtk ./gradlew :ffi:posix:jvmTest
```

Expected: project or classes do not exist.

**Step 3: Implement ordered symbol lookup**

Use one lookup chain:

```kotlin
object PosixSymbols {
    private val lookup: SymbolLookup by lazy {
        val loader = SymbolLookup.loaderLookup()
        val libc = sequenceOf("libc.so.6", "libc.so")
            .mapNotNull { name -> runCatching { SymbolLookup.libraryLookup(name, Arena.global()) }.getOrNull() }
            .firstOrNull()
        if (libc == null) loader else SymbolLookup { symbol ->
            loader.find(symbol).or { libc.find(symbol) }
        }
    }

    fun find(name: String): MemorySegment? = lookup.find(name).orElse(null)
}
```

`loaderLookup`/already-loaded process symbols are always tried before SONAME candidates. Do not catch lookup failures at individual call sites.

**Step 4: Implement `PosixWakeup` ownership**

Expose only:

```kotlin
interface PosixWakeup : AutoCloseable {
    val readFd: Int
    fun signal(): Boolean
    fun drain(): Boolean
    override fun close()
}
```

Prefer `eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC)`. Fall back to `pipe2(O_NONBLOCK | O_CLOEXEC)`, then `pipe` plus `fcntl` if `pipe2` is absent. An `AtomicBoolean` coalesces signals and is cleared only after a successful drain. Handle `EAGAIN` as coalesced/empty, retry `EINTR`, and report other errno values with the operation name.

**Step 5: Run tests on the host**

```bash
rtk ./gradlew :ffi:posix:jvmTest
```

Expected on Linux: all tests pass. Expected on macOS: symbol-resolution test is guarded by `os.name == Linux`; pure fallback/ownership tests still pass.

**Step 6: Commit**

```bash
rtk git add settings.gradle.kts ffi/posix
rtk git commit -m "feat(posix): add portable wake and symbol layer"
```

---

### Task 2: Replace Wayland's hard-coded libc and stuck wake proxy

**Files:**
- Modify: `ffi/wayland/build.gradle.kts`
- Modify: `kadre-wayland/build.gradle.kts`
- Modify: `ffi/wayland/src/jvmMain/kotlin/org/graphiks/kadre/ffi/wayland/Wayland_h.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/portal/XdpPipeWire.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoopProxy.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoop.kt`
- Replace: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandEventLoopSmokeTest.kt`

**Step 1: Write the failing proxy conformance test**

Construct `WaylandEventLoopProxy` with a fake `PosixWakeup`. Reuse `EventLoopConformanceAssertions.assertWakeUpRearms` and assert exactly three signal/drain pairs. Add tests that drain failure preserves retryability and close prevents signals.

Delete tests that define absent libc/wake as an acceptable no-op; required loop wake is no longer optional.

**Step 2: Verify current failure**

```bash
rtk ./gradlew :kadre-wayland:jvmTest --tests '*WaylandEventLoopSmokeTest'
```

Expected: new constructor/portable wake API missing or second/third cycle cannot be observed.

**Step 3: Use `ffi:posix` everywhere**

- Add `api(project(":ffi:posix"))` to `ffi:wayland` and `implementation(project(":ffi:posix"))` to `kadre-wayland`.
- Replace each `SymbolLookup.libraryLookup("libc.so.6", ...)` in `Wayland_h.kt` and `XdpPipeWire.kt` with `PosixSymbols.find`/the shared downcall helper.
- Change `WaylandEventLoopProxy` to delegate `wakeUp()` to `PosixWakeup.signal()`.
- Change `pumpOnce` to poll `wakeup.readFd` and call `wakeup.drain()` when readable. The drain resets the pending flag inside the owner; remove `clearPending` from the proxy.
- Create the wake owner before the event loop and close it in the outer `finally` after the loop stops but before disconnecting the display.

**Step 4: Run focused tests and hard-code scan**

```bash
rtk ./gradlew :ffi:posix:jvmTest :kadre-wayland:jvmTest --tests '*WaylandEventLoopSmokeTest'
rtk rg -n 'libc\.so\.6' ffi/wayland kadre-wayland
```

Expected: tests pass; scan returns no result.

**Step 5: Commit**

```bash
rtk git add ffi/wayland kadre-wayland
rtk git commit -m "fix(wayland): use portable rearming wake primitive"
```

---

### Task 3: Correct XKB keymap lifetime and the live device filter

**Files:**
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandSeat.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoop.kt`
- Create: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandDeviceFilter.kt`
- Create: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandKeyboardLifecycleTest.kt`
- Create: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandDeviceFilterTest.kt`

**Step 1: Add deterministic failing tests around injected native operations**

Extract a small `WaylandKeymapLoader` whose operations can be faked. Test this trace for format `1` (`WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1`):

```text
mmap -> context -> keymap while mapping open -> state -> compose while locale arena open -> munmap -> close(fd)
```

Also assert:

- format `0` is rejected but fd is still closed;
- every exception path closes fd and unmaps only a successful mapping;
- keymap/compose resources from a previous keymap are released once;
- a listener created while mode is `WhenFocused` immediately observes later `Never` and `Always` changes.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-wayland:jvmTest --tests '*WaylandKeyboardLifecycleTest' --tests '*WaylandDeviceFilterTest'
```

Expected: current code rejects format 1, passes a closed locale segment, and captures the initial filter value.

**Step 3: Implement minimal lifetime owners**

- Accept only `format == 1`.
- Wrap fd ownership in `try/finally { close(fd) }`.
- Keep the mmap segment valid until `xkb_keymap_new_from_string` returns, then unmap in `finally`.
- Allocate the locale and invoke `xkb_compose_table_new_from_locale` inside the same `Arena.ofConfined().use` block.
- Queue native callback failures for the Kotlin loop to throw after cleanup; do not throw from the upcall.
- Replace the copied `DeviceEvents` parameter with `WaylandDeviceFilter.current`, read at event-dispatch time.

**Step 4: Run tests**

```bash
rtk ./gradlew :kadre-wayland:jvmTest --tests '*WaylandKeyboardLifecycleTest' --tests '*WaylandDeviceFilterTest'
```

Expected: all lifetime and dynamic-filter traces pass.

**Step 5: Commit**

```bash
rtk git add kadre-wayland/src/jvmMain kadre-wayland/src/jvmTest
rtk git commit -m "fix(wayland): repair XKB lifetimes and device filtering"
```

---

### Task 4: Track all outputs and each window's entered-output set

**Files:**
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandRegistry.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandOutputInfo.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandSeat.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoop.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandWindow.kt`
- Create: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandOutputRegistryTest.kt`
- Modify: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandWindowTest.kt`

**Step 1: Write state-level failing tests**

Use fake bind/destroy callbacks and test:

- two `wl_output` globals are both bound and enumerated;
- `global_remove(name)` destroys/removes only that output;
- a window entering output B reports B as current even when A was registered first;
- leave B falls back to the remaining entered output, then null/primary policy when none remain;
- scale follows the selected per-window output;
- `VideoMode(size=1920x1080, bitDepth=null, refreshRateMilliHz=60_000)` does not put 60,000 in `bitDepth`.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-wayland:jvmTest --tests '*WaylandOutputRegistryTest' --tests '*WaylandWindowTest'
```

Expected: only the first output is represented, remove is ignored, and the current mode argument order test fails.

**Step 3: Introduce a durable registry owner**

Replace scalar `outputName/outputPtr/outputVersion` with entries keyed by registry name:

```kotlin
internal data class BoundOutput(
    val registryName: Int,
    val proxy: Long,
    val version: Int,
    val info: WaylandOutputInfo,
)
```

`WaylandRegistryOwner : AutoCloseable` retains the registry proxy, listener arena, collector, and bound globals. It handles future `global` and `global_remove` callbacks for the connection lifetime and closes child proxies before the registry.

**Step 4: Install per-output and per-surface listeners**

- Install one output listener for every `BoundOutput`.
- Install `wl_surface.enter`/`leave` listeners and maintain `enteredOutputProxies: LinkedHashSet<Long>` in `WaylandWindow`.
- Resolve `currentMonitor`, scale, fullscreen output, and `availableMonitors` from the registry plus this set.
- Use named arguments for every `VideoMode` construction.

**Step 5: Run tests**

```bash
rtk ./gradlew :kadre-wayland:jvmTest --tests '*WaylandOutputRegistryTest' --tests '*WaylandWindowTest'
```

Expected: multi-output, hot-unplug, enter/leave, scale, and video-mode tests pass.

**Step 6: Commit**

```bash
rtk git add kadre-wayland/src/jvmMain kadre-wayland/src/jvmTest
rtk git commit -m "fix(wayland): track output lifecycle per window"
```

---

### Task 5: Make redraw independent and close terminal

**Files:**
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoop.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandWindow.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandXdg.kt`
- Create: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandLoopContractTest.kt`
- Modify: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandWindowTest.kt`

**Step 1: Add failing fake-adapter tests**

Against injected queue/wake/frame callbacks, assert:

- redraw after idle queues one `RedrawRequested` and signals the POSIX wake;
- repeated redraw coalesces until dispatch, then can rearm;
- iteration callback order is `newEvents -> queued window events -> aboutToWait`;
- `close()` removes the window before native proxy destruction, clears its redraw/frame callback, and is idempotent;
- compositor `xdg_toplevel.close` follows the same close path;
- no event for the ID is dispatched after `Destroyed`.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-wayland:jvmTest --tests '*WaylandLoopContractTest' --tests '*WaylandWindowTest'
```

Expected: xdg redraw is currently a no-op and queued events precede `newEvents`.

**Step 3: Implement queue/coalescing and strict ordering**

- Add `requestRedraw(windowId)` to the loop: insert into a pending-ID set and signal wake only on the first insertion.
- Dispatch the redraw from the Kotlin iteration; compositor frame callbacks only pace when the renderer can present again.
- Call `newEvents` immediately after `pumpOnce`, then drain all valid queued events, then call `aboutToWait`, then compute/enter the next wait.
- Filter every queue item through `windows[windowId]`.
- Centralize close in the loop and destroy in child-to-parent order: frame callback, cursor/blur/xdg_toplevel/xdg_surface, `wl_surface`.

**Step 4: Make connection failure descriptive**

Wrap initial connection/discovery errors as one `IllegalStateException` containing backend `Wayland`, `WAYLAND_DISPLAY` presence/value, operation, and native cause. Do not include unrelated environment variables. Required compositor and `xdg_wm_base` absence must fail before `resumed`.

**Step 5: Run unit and headless integration tests**

```bash
rtk ./gradlew :kadre-wayland:jvmTest
rtk env WAYLAND_DISPLAY=definitely-missing ./gradlew :kadre-wayland:jvmTest --tests '*WaylandNativeIntegrationTest*missing*'
```

Expected: all unit tests pass; missing-display test asserts a non-zero/descriptive failure. The Weston-backed glibc/musl runs are added in plan 08.

**Step 6: Commit**

```bash
rtk git add kadre-wayland
rtk git commit -m "fix(wayland): enforce redraw lifecycle and failures"
```
