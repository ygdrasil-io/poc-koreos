# Deferred and Platform-Limited Items

This is the authoritative index for Kadre behavior that is intentionally
deferred, platform-limited, or verified only partially. Update this file when a
linked feature table changes status.

## Current Web Pointer-Control Status

- Web DOM bridges are implemented for JS and wasmJs; they are no longer stubs.
- Web Pointer Lock and cursor hit-testing are wired through `WebDomBridge`.
- `CursorGrabMode.Locked` submits a browser Pointer Lock request and returns
  `WindowRequestResult.Success` when the request is submitted. Browser-granted
  Pointer Lock remains asynchronous and user-gesture dependent.
- `setCursorHittest(false)` maps to CSS `pointer-events: none`; `true` restores
  `pointer-events: auto`.
- Web `CursorGrabMode.Confined` and cursor warping remain unsupported because
  browsers do not expose those controls for canvas elements.

## Platform-Limited Behavior

- `Fullscreen.Exclusive` falls back to borderless or no-ops on Wayland, Web,
  Android, and UIKit.
- Mobile cursor APIs are no-ops where the OS has no pointer cursor concept.
- Web raw mouse input and direct cursor warping are unavailable by browser
  design.
- AppKit per-window icons are a no-op, matching winit and macOS platform
  behavior.
- AppKit `dragResizeWindow` is unsupported, matching winit.

## Deferred Native Backend Work

- Wayland monitor geometry and video modes are still synthetic until `wl_output`
  geometry/mode storage is wired.
- Wayland optional protocols remain partial: `xdg_activation_v1` for user
  attention, `xdg_toplevel_icon_manager_v1` for icons, compositor blur protocols,
  portal-backed theme detection, and compositor-advertised pointer constraints
  for confined/locked cursor grabs.
- X11 keyboard text fields still need `XLookupString` or equivalent integration.
- X11 dynamic scale-factor changes still need RandR notification handling.
- X11 transparency still needs `_NET_WM_WINDOW_OPACITY` wiring.
- Win32 exclusive fullscreen still needs `ChangeDisplaySettingsExW`.
- Win32 cursor visibility still needs balanced `ShowCursor` counter handling.
- Win32 wide-string reads still need strict null-terminator handling.
- X11 custom cursors are currently a monochrome fallback; ARGB cursor support is
  deferred.
- Wayland custom cursor application uses a `wl_shm` cursor surface when a
  `wl_pointer` enter serial is available; broader compositor/runtime coverage is
  still a verification gap.

## Deferred Event and API Coverage

- Rich IME capability reporting (`Window.ime_capabilities`) is not modeled.
- `Window.request_ime_update` is not exposed; Kadre uses individual IME setters.
- `ActiveEventLoop.ownedDisplayHandle()` may return `null`, unlike winit's
  non-null owned display handle.
- Drag-and-drop event emission remains backend-dependent and partial.
- `Occluded` event emission remains backend-dependent and partial.
- Gesture emission beyond AppKit, Win32, and explicit UIKit recognizers is still
  backend-dependent.
- `TouchpadPressure` remains AppKit-only.
- Some appearance setters are backend-dependent or partial: blur, window icon,
  theme override, user attention, and window level.

## Verification Gaps

- Browser-granted Pointer Lock still needs browser E2E coverage because unit
  tests can verify the request path but cannot grant lock state.
- Native compositor paths that depend on live X11/Wayland/Windows/macOS
  behavior need platform CI or manual runtime verification beyond compilation.
