# Cross-platform correctness remediation design

Date: 2026-07-17  
Status: approved design  
Scope: Android, Linux X11/Wayland, AppKit, UIKit, Web JS/Wasm, samples, build and CI  
Out of scope: Windows and Win32-specific behavior

## Context

A static review and a local validation matrix identified one blocking Android
surface-lifecycle defect, eighteen additional backend defects, three portability
or consistency risks, and several CI gaps capable of reporting false success.
The repository builds on its main targets, but those green builds do not prove
that redraw, wake-up, lifecycle, input, close, and visual-rendering contracts work
consistently.

This remediation is one coordinated campaign. It uses a contract-first approach:
define and test the common behavior once, then bring every in-scope backend into
conformance before addressing its platform-specific defects.

## Goals

- Make lifecycle, redraw, wake-up, close, and `ControlFlow` behavior consistent.
- Correct all nineteen findings from the review.
- Cover glibc and musl on Linux.
- Replace false-green visual checks with deterministic blocking checks.
- Keep pull-request CI at or below a 30-minute critical path through parallelism.
- Preserve `iosX64` in the Kadre libraries while removing it from the incompatible
  Compose sample only.
- Make every intentional compatibility break explicit and documented.

## Non-goals

- No Win32 fixes or Windows runtime validation.
- No unrelated API expansion or backend rewrite.
- No requirement for hardware-only GPU captures to gate pull requests.
- No attempt to support every Linux distribution in PR CI; representative glibc
  and musl containers define the portable baseline.

## Approved compatibility changes

The following changes were reviewed and explicitly approved before implementation:

1. Replace Web pointer events that cannot carry coordinates with events containing
   position, source, and pointer identity. This is a source and binary change for
   consumers using `WebWindowEvent` directly.
2. Define `Window.safeArea` in physical pixels. UIKit points and Web CSS pixels are
   converted by the active scale factor.
3. Make an unavailable X11 display or Wayland compositor a descriptive runtime
   error instead of a successful no-op.
4. Enforce one documented `ApplicationHandler` callback order, even if an existing
   handler accidentally depends on a backend's previous ordering.
5. Remove `iosX64` from the Compose sample only. Kadre library targets retain
   `iosX64`.

Any additional public or behavioral break discovered during implementation requires
separate user approval with an explanation of why a compatible solution is not
appropriate.

## Contract architecture

### Shared state model

The common contract defines application, iteration, surface, and window states.
Backend adapters own native resources, but transitions and observable ordering are
shared.

Application lifecycle:

```text
Created -> Active -> Suspended -> Active -> Exiting -> Exited
```

Window lifecycle:

```text
Created -> SurfaceReady -> SurfaceUnavailable -> SurfaceReady -> Closed
```

Iteration order:

```text
wake/deadline -> newEvents -> dispatch queued events -> aboutToWait -> native wait
```

Required invariants:

- `newEvents` occurs before event dispatch in every iteration.
- `aboutToWait` is the final application callback before the native wait.
- A platform surface is valid before `canCreateSurfaces` returns control to the
  application.
- `destroySurfaces` is called before a surface handle becomes unusable to the
  application, unless the operating system has already invalidated it; that exceptional
  case is reported explicitly.
- `requestRedraw()` queues at most one pending `RedrawRequested` and wakes an idle
  loop.
- `EventLoopProxy.wakeUp()` supports unlimited wake-consume-rearm cycles.
- `close()` is idempotent, unregisters the window, invalidates its handles, cancels
  queued events, and prevents future dispatch.
- `WaitUntil` ends at its deadline or at the first event. Its timestamps use one
  documented epoch and unit.

### Internal components

The implementation introduces or extracts four internal responsibilities:

- An event queue with redraw coalescing and closed-window filtering.
- A lifecycle coordinator that validates state transitions and callback order.
- A backend wake-up primitive whose pending state is cleared when consumed.
- A scheduler that maps `Poll`, `Wait`, and `WaitUntil` onto platform timers and
  frame callbacks.

These components remain internal. The shared conformance harness lives in
`kadre-test` and drives both fake schedulers and real backend adapters.

## Backend designs

### Android

`KadreActivity.surfaceCreated` stores the `Surface` before invoking
`canCreateSurfaces`. A window created inside that callback receives the current
surface immediately, so `rawWindowHandle` is valid at the documented point.

One main-thread scheduler coordinates `Handler`, `Choreographer`, redraw requests,
proxy wake-ups, and `WaitUntil` timers. A redraw requested after the loop becomes idle
posts a frame callback. Closing a window cancels callbacks, releases its surface
reference, unregisters it, and emits no later event. Monitor refresh rate comes from
the display refresh rate in millihertz, never from `xdpi`.

### Linux portability layer

Native symbol lookup must not hard-code `libc.so.6`. It first uses the process or
loader lookup available through the Foreign Function and Memory API, then tries
platform names suitable for glibc and musl. Wake-up uses `eventfd` where available
and a non-blocking pipe fallback otherwise. Both implementations share the same
consume-and-rearm contract.

The public Linux tutorial lists the required X11 and Wayland backend dependencies
and no longer promises automatic routing from the facade alone. Backend detection
checks both class availability and the ability to establish a usable native
connection. Failure reports the attempted backend, relevant environment variables,
and native cause.

### X11

The event loop owns a pipe watched alongside X11 activity. Background threads only
write to that pipe; all Xlib calls remain on the X11 thread, avoiding reliance on
`XInitThreads`. `requestRedraw()` queues a redraw and signals the pipe rather than
waiting for an incidental `Expose` event.

Close removes the window before native destruction and clears queued work. Event
ordering follows the shared coordinator, including teardown before `XCloseDisplay`.
Custom cursor pixels are packed at one bit per pixel per scanline, colors populate
the 16-bit RGB fields, and hotspot and maximum-size constraints are validated.

### Wayland

The eventfd or pipe proxy clears its pending flag immediately after the loop drains
the signal. `requestRedraw()` queues a redraw independently of `xdg_toplevel` and
uses compositor frame callbacks only to pace presentation.

The keyboard listener accepts `WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1`, closes the keymap
fd in `finally`, keeps mapped memory alive for keymap construction, and allocates
locale strings only for the duration of the XKB call. The device-event filter is one
shared state observed dynamically by the seat listener.

The registry stores every `wl_output` by registry name, handles `global_remove`, and
tracks `wl_surface.enter/leave` per window. Scale, current monitor, enumeration, and
fullscreen selection use that per-window set. `VideoMode` construction uses named
arguments so refresh rate cannot be assigned to bit depth.

Window close clears callbacks and registries before destroying native proxies.
Connection failure is fatal and descriptive, matching X11.

### AppKit

The AppKit loop adopts the common callback sequence and emits startup, resume,
suspend, surface destruction, and shutdown events exactly once. `WaitUntil` reports
`ResumeTimeReached` only when the deadline actually fires and does not repeatedly
arm an already-expired timer.

A closeable run-loop owner retains the observer and current timer, removes them from
the run loop, invalidates them, and calls `CFRelease`. Window delegates and IME view
registrations are removed when their windows close. Re-running `runApp` in the same
process must not preserve callbacks or registry entries.

### UIKit

Returning from background recreates rendering surfaces for existing windows rather
than calling application code that unconditionally creates additional windows. The
sample handler also retains and reuses its window explicitly. Closed windows leave
the active-loop registry immediately and receive no focus, occlusion, or theme events.

`CADisplayLink` runs only when polling or when work is pending. `WaitUntil` uses a
one-shot main-queue timer. The UIKit suite replaces unsupported Kotlin/Native
reflection with behavioral tests and must compile and run on an arm64 simulator.

Safe-area points are multiplied by the UIKit scale factor and rounded consistently
to physical pixels.

### Web JS and Wasm

Both DOM bridges use one coordinate transform:

```text
physical = (client - canvas.getBoundingClientRect().origin) * devicePixelRatio
```

Pointer move, enter, leave, button, touch, wheel, and drag events use this transform.
Button events contain their position; primary touch is tracked from active pointer
state rather than inferred from identifier zero.

Resize observation multiplies CSS dimensions by the current DPR. A DPR transition
updates the scale and physical size in one queued iteration. `WaitUntil` schedules a
deadline but is cancelled and woken immediately by an event. Reported timestamps use
the same epoch as `requestedResume` rather than the relative RAF timestamp.

Close detaches the bridge, removes the window, and cancels pending animation frames
and timeouts. JS and Wasm call the same canonical pointer-events method. JS cursor
bytes are widened with `and 0xFF` before insertion into `Uint8ClampedArray`.
Safe-area CSS pixels are converted to physical pixels.

## Build, samples, and documentation

- Remove only the Compose sample's `iosX64` target and verify its arm64 device and
  simulator targets.
- Migrate KMP Android libraries to `com.android.kotlin.multiplatform.library`.
- Remove obsolete Android source-set and built-in-Kotlin compatibility flags.
- Keep Android application samples on the supported application plugin model.
- Make `--window-capture` produce and validate a PNG or exit non-zero. It must never
  print an unimplemented warning and return success.
- Update Linux dependency examples, backend error documentation, platform support
  tables, approved behavior changes, and release notes.
- Regenerate API dumps only after approved public changes are final.

## Error handling

Native callback boundaries must not propagate Kotlin exceptions directly through C,
Objective-C, or browser callbacks. Adapters capture the failure, queue it, wake the
loop, perform cleanup at a Kotlin-safe boundary, then throw a descriptive exception.

An optional native capability may degrade only when the API documents that behavior.
Required capabilities such as display connection, surface creation, or wake-up must
fail explicitly. Broad `catch (Throwable)` blocks must either attach context and
propagate, or record a structured failure consumed by the loop.

Every native resource has one owner and an idempotent release path. Cleanup uses
`finally` and runs in reverse ownership order. Diagnostic messages include the
backend and operation without exposing unrelated environment or secret values.

## Test strategy

### Shared conformance suite

`EventLoopConformanceSuite` runs against every backend adapter and a fake scheduler.
It verifies:

- Three complete wake-consume-rearm cycles.
- Redraw after idle and redraw coalescing.
- Exact callback ordering.
- `WaitUntil` interrupted by an event and completed by a deadline.
- Repeated close, invalid handle, and no post-close event.
- Suspend, surface destruction, surface recreation, and resume.
- Absence of a busy loop while waiting.

### Platform regression tests

| Platform | Blocking scenarios |
| --- | --- |
| Android | Activity/Surface instrumentation, immediate raw handle, redraw after idle, proxy wake-up, close, refresh rate |
| X11 glibc | Xvfb connection, redraw, repeated pipe wake-up, cursor packing, close, explicit no-display error |
| X11 musl | Equivalent Alpine smoke and libc lookup |
| Wayland glibc | Weston connection, XKB keymap and fd lifetime, redraw, repeated wake-up, multi-output, device filter, close |
| Wayland musl | Equivalent Alpine connection and eventfd/pipe fallback |
| AppKit | Callback trace, `WaitUntil`, resource release, second `runApp` in one process |
| UIKit | Test compilation, foreground cycles, one retained window, close, `WaitUntil`, physical safe area |
| Web JS/Wasm | Offset canvas, DPR 1 and 2, click without prior move, resize plus DPR, primary touch, close, cursor hit test, RGBA |
| Compose iOS | arm64 and simulator arm64 compile; no Compose `iosX64` target |
| Android build | Modern AGP/KMP configuration without obsolete flags |

The Wayland multi-output test creates two outputs where the headless compositor
supports it. If the selected Weston backend cannot expose two outputs, a protocol
harness injects registry, enter, leave, scale, and removal events deterministically;
the real Weston smoke remains mandatory.

### Deterministic visual checks

- Compose headless raster capture must create a PNG with expected dimensions and
  non-background pixels.
- Wayland GL capture uses Weston and Mesa llvmpipe. Missing or invalid PNG is failure.
- Android Vulkan capture uses SwiftShader and validates pixels.
- Existing macOS capture remains blocking on its supported runner.
- iOS Metal hardware capture is explicitly skipped when no Metal device exists and
  does not count as GPU validation. A deterministic CPU/raster test remains blocking.

No deterministic job may use `continue-on-error`, `|| true`, or a successful early
return for missing output.

## CI topology and budget

Pull-request jobs run in parallel:

| Job | Target maximum |
| --- | ---: |
| Contracts and JVM | 6 minutes |
| Web JS/Wasm | 10 minutes |
| Build, docs, and AGP | 10 minutes |
| Android emulator | 18 minutes |
| iOS simulator | 18 minutes |
| Linux glibc X11/Wayland | 16 minutes |
| Linux musl X11/Wayland | 18 minutes |
| Deterministic visual captures | completes by minute 26 |

The pull-request critical path must remain below 30 minutes. Nightly CI expands the
matrix across additional Android API levels, browsers, JDK patch versions, and Linux
images. Pull-request jobs retain all deterministic contract checks; nightly jobs add
permutations, not missing fundamentals.

Test reports, emulator logs, Weston/Xvfb logs, and captures are uploaded as artifacts.
A skip is accepted only when the test framework records a structured reason for a
hardware-only capability.

## Traceability to the complete review

| Review finding | Design coverage |
| ---: | --- |
| 1. Android handle unavailable in `canCreateSurfaces` | Android surface ordering and instrumentation test |
| 2. `requestRedraw` ineffective | Shared redraw contract plus Android, X11, and Wayland schedulers |
| 3. One-shot or inert event-loop proxies | Shared wake-up primitive and three-cycle conformance test |
| 4. Wayland rejects XKB keymap and leaks fd | Wayland keyboard lifetime design and fd test |
| 5. Close lifecycle violations | Shared close contract and backend registry cleanup |
| 6. UIKit duplicates windows after foreground | UIKit surface recreation and foreground-cycle test |
| 7. Web coordinate space and zero button position | Shared DOM coordinate transform and pointer event change |
| 8. Web resize mixes CSS and physical pixels | DPR-aware resize transaction tests |
| 9. Web `WaitUntil` wake and timestamp errors | Shared scheduler contract and Web deadline tests |
| 10. Wayland single-output model | Output registry map and surface enter/leave tracking |
| 11. X11 cursor encoding and colors | Packed bitmap conversion and color-field tests |
| 12. Incomplete or misordered application lifecycle | Lifecycle coordinator and callback trace suite |
| 13. Mobile `ControlFlow` and AppKit deadline behavior | Shared scheduler plus platform timer implementations |
| 14. AppKit native resource leaks | Closeable run-loop owner and repeat-run tests |
| 15. Wayland device-event filter disconnect | Shared filter state and sink-level test |
| 16. Wayland refresh rate stored as bit depth | Named `VideoMode` fields and value test |
| 17. JS cursor hit-test method mismatch | Canonical bridge method parity test |
| 18. Signed JS RGBA bytes | Unsigned conversion and pixel-array test |
| 19. Android `xdpi` exposed as refresh rate | Display refresh-rate conversion test |

Additional review risks are covered as follows:

- Xlib thread safety: wake-up pipe keeps all Xlib access on the event-loop thread.
- musl portability: Alpine X11 and Wayland jobs exercise libc lookup and pipe fallback.
- `safeArea` units: approved physical-pixel contract with UIKit and Web tests.
- Linux facade mismatch: corrected dependency documentation and a minimal-consumer
  integration test.
- Broken UIKit suite and Compose `iosX64`: explicit build gates.
- False-green visual workflows: required output validation and structured skips.

## Delivery sequence

The work remains one remediation campaign but is committed atomically:

1. Add failing shared conformance tests.
2. Implement the shared state, queue, wake-up, and scheduler contracts.
3. Correct Android.
4. Add Linux portability, then correct Wayland and X11.
5. Correct AppKit and UIKit.
6. Correct Web JS and Wasm.
7. Update samples, AGP/KMP configuration, documentation, and CI.
8. Run the complete local and CI-equivalent matrix and audit every traceability row.

Each stage must pass its new tests and all previously completed stages. A failure is
fixed within its owning stage rather than deferred to a final cleanup commit.

## Acceptance criteria

- Every review finding has a passing regression test or a documented native
  validation tied to its traceability row.
- Android tests use a real Activity surface and access the raw handle from
  `canCreateSurfaces`.
- X11 and Wayland pass on representative glibc and musl images.
- AppKit and UIKit pass lifecycle, wait, close, and cleanup scenarios.
- JS and Wasm pass identical DOM interaction scenarios.
- UIKit tests compile and run; Compose builds only its supported iOS targets.
- Deterministic visual commands fail if their expected capture is absent or invalid.
- Pull-request CI completes within 30 minutes without non-blocking deterministic jobs.
- Approved compatibility changes appear in documentation, release notes, and API
  dumps where applicable.
- The worktree is clean after the full validation matrix.

