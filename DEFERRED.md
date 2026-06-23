# Kadre Deferred Items

This document tracks public API areas that are intentionally partial, backend-dependent, or unsupported in Kadre 1.0.0. It is the canonical target for the `DEFERRED.md` links used by the docs.

## Still Partial

| Area | Current status |
|------|----------------|
| Rich IME capabilities | `setImeAllowed`, `setImeCursorArea`, `setImePurpose`, and `WindowEvent.Ime` are wired by the current backends, but Kadre does not expose a rich capability-reporting model equivalent to every native IME stack. |
| Drag and drop fidelity | DnD events are emitted by the desktop, web, Android, and UIKit backends, but payload fidelity varies: Web exposes file names, UIKit currently emits empty path lists, and Win32 primarily reports completed drops. |
| Gestures | AppKit and UIKit cover the primary gesture events; Win32 has gesture plumbing; other backends remain limited or unsupported. |
| Occluded | Emitted by AppKit, X11, Web, Android, and UIKit. Win32 and Wayland do not currently emit a dedicated occlusion event. |
| Keyboard richness | Modifier transitions are emitted across the active backends, but `text`, `textWithAllModifiers`, `keyWithoutModifiers`, and left/right modifier fidelity remain backend-dependent. |
| Wayland monitors | `availableMonitors()` returns synthetic data until `wl_output` geometry and mode events are stored during registry binding. |
| Wayland optional protocols | User attention, icon, blur, and some pointer-constraint paths depend on optional compositor protocols and may no-op when unavailable. |
| Web pointer features | Pointer Lock for `CursorGrabMode.Locked` and cursor hit-testing via CSS `pointer-events` are not wired yet. |
| Android/UIKit window chrome | Many desktop window-state setters are documented no-ops because mobile OSes own window chrome, resizing, positioning, and cursor behavior. |

## Unsupported By Platform Design

| Area | Reason |
|------|--------|
| `Fullscreen.Exclusive` on Wayland, Web, Android, UIKit | These platforms do not expose winit-style exclusive video mode switching. Kadre falls back to borderless/fullscreen behavior where possible. |
| Custom cursors on Android and UIKit | Touch-first platforms do not expose a general desktop cursor model. |
| Cursor warping on Wayland, Web, Android, UIKit | These platforms deliberately do not expose global pointer warping. |
| AppKit per-window icon | macOS does not support per-window icons in the same way as Win32/X11. |

## Maintenance Notes

- CI validates many compile and unit-test paths, but Linux Wayland runtime coverage is still limited because the workflow does not start a real compositor.
- The CI deep-testing branch allowlist should include `codex/**` branches in addition to the historical `claude/**` prefix.
- Gradle/Android/Dokka build warnings should be addressed before AGP 10 and future Dokka removals.
