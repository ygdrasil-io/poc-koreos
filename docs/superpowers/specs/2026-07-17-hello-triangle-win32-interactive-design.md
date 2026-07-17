# Interactive hello-triangle rendering on Win32

## Context

The interactive `hello-triangle` sample creates a Kadre window on Windows, but its GPU initialization accepts only `RawWindowHandle.AppKit`, creates a Metal instance, and binds a `CAMetalLayer`. A Win32 handle is therefore reported as unsupported after the visible HWND is created. The event loop keeps running, but `surface`, `gpuDevice`, and `pipeline` remain null, so redraw events leave the default gray client area visible.

The existing Windows offscreen capture path already proves that wgpu4k can create a `Primary` instance and bind a surface with `getSurfaceFromWindows(hinstance, hwnd)`.

## Goal

Render the existing interactive RGB triangle in the Kadre-created Win32 window while preserving the current AppKit behavior and the shared render, resize, and cleanup pipeline.

## Scope

- Change only the JVM `samples:hello-triangle` implementation and its tests.
- Support `RawWindowHandle.Win32` in interactive mode.
- Keep the AppKit path functionally unchanged.
- Leave Linux interactive support, Kadre backends, FFI bindings, and offscreen capture behavior unchanged.
- Add the correction to PR #285 as a separate implementation commit after the design and tests are approved.

## Non-goals

- Do not introduce a general cross-platform rendering framework.
- Do not duplicate `HelloTriangleApp` per operating system.
- Do not move wgpu4k surface creation into `kadre-win32`; the backend must remain independent of the sample's GPU library.
- Do not reuse the hidden HWND created by `captureWindows`, because interactive rendering must attach to the visible Kadre window.

## Design

### Pure surface-target selection

Introduce an internal, testable representation of the native target selected from `RawWindowHandle`:

- AppKit target: the existing `nsView`/`nsLayer` data needed by the Metal path.
- Win32 target: the exact `hinstance` and `hwnd` addresses from `RawWindowHandle.Win32`.
- Unsupported target: all other handle variants remain outside this Windows-only change.

The pure selector prevents platform selection from being embedded in native GPU setup and gives the regression test a deterministic boundary.

### Native surface creation

After `ffi.LibraryLoader.load()`, create the native instance and surface according to the selected target:

- AppKit: `WGPUInstanceBackend.Metal`, followed by the existing `getSurfaceFromMetalLayer` call and legacy `nsView` layer fallback.
- Win32: `WGPUInstanceBackend.Primary`, followed by `getSurfaceFromWindows` with `JvmNativeAddress` values wrapping the exact `hinstance` and `hwnd` addresses.

Both branches return the same pair of owned resources: `WGPU` instance and `NativeSurface`. Adapter selection, capability discovery, device creation, surface configuration, shader creation, pipeline creation, rendering, resize handling, and cleanup then remain common.

### Render flow

On Windows the complete interactive path becomes:

1. Kadre creates the visible HWND.
2. `RawWindowHandle.Win32` selects the Win32 surface target.
3. wgpu4k creates a `Primary` instance and a surface bound to that HWND.
4. The existing common pipeline is initialized.
5. `aboutToWait()` calls `requestRedraw()`.
6. Kadre invalidates the HWND and dispatches `WM_PAINT` as `RedrawRequested`.
7. `renderFrame()` acquires the presentation texture, draws three vertices, submits, and presents.
8. Resize events reconfigure the same surface with the new physical size.

### Ownership and failures

- If the handle is unsupported, log the existing unsupported-platform diagnostic and leave other platforms unchanged.
- If instance creation fails, log and stop GPU initialization.
- If surface creation fails, close the newly created instance before returning.
- Store the instance and surface only after successful creation so `releaseResources()` owns every stored resource.
- Keep callback/upcall behavior unchanged; no native exception may be introduced across Kadre's Win32 callback boundary.
- On `CloseRequested`, release pipeline, device, surface, and instance, then exit the event loop as today.

## Testing

### TDD regression

Add JVM tests for the pure selector before production changes:

- `RawWindowHandle.Win32(hwnd, hinstance)` selects a Win32 target carrying the exact two addresses.
- `RawWindowHandle.AppKit` continues to select the AppKit target.
- A non-AppKit/non-Win32 handle remains unsupported.

The first focused run must be RED because the desired selector/target API does not yet exist.

### Automated validation

- Run the new sample test class under JDK 25.
- Build `:samples:hello-triangle`.
- Re-run the existing Windows offscreen `--capture` smoke and verify a non-empty 800x600 PNG.
- Re-run the Win32 redraw tests to ensure the event-to-paint path remains green.

### Interactive Windows validation

Run `:samples:hello-triangle:run` and verify:

- console output identifies a Win32 target, creates the surface/device/pipeline, and reports FPS;
- the RGB triangle is visible instead of a gray client area;
- resizing reconfigures the surface without losing rendering;
- closing releases resources and leaves no residual sample process.

## Acceptance criteria

- The visible Windows sample renders and presents the RGB triangle.
- AppKit behavior and offscreen capture remain unchanged.
- No Kadre backend, generated binding, Linux path, or unrelated sample is modified.
- Focused tests, sample build, offscreen smoke, Win32 redraw tests, and the interactive manual validation all pass.
