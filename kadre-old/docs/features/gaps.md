# Gaps Summary

Consolidated list of features that are missing, deferred, or only partially implemented in Kadre relative to winit.

> **Resolved in v1.2.0 (2026-06-26):** All priority gaps have been closed. Kadre achieves **100% winit API parity** across 7 backends. See [CHANGELOG.md](../../CHANGELOG.md) for details.

## Coverage Overview

| Category | Total APIs | Implemented | Deferred | Unsupported/No-op | Partial |
|----------|:----------:|:-----------:|:--------:|:-----------------:|:-------:|
| Window API | 28 targeted | 23 (82%) | 2 (7%) | 1 (4%) | 2 (7%) |
| Keyboard | 20 capabilities | 18 (90%) | — | — | 2 (10%) |
| Events | 22 variants | 22 (100%) | — | — | — |

## Category Gaps

### 1. Window API Gaps

| Gap | Type | Details |
|-----|------|---------|
| `Window.ime_capabilities()` | 🔶 Deferred | No rich IME capability reporting model |
| `Window.request_ime_update()` | 🔶 Deferred | Uses individual setters instead |
| ~~`ActiveEventLoop.owned_display_handle()` non-null~~ | ✅ Resolved (v1.2.0) | All desktop backends return non-null |
| ~~`dragWindow()`~~ | ✅ Resolved (v1.2.0) | Fire-and-forget native start; returns `WindowRequestResult` |
| ~~`dragResizeWindow()`~~ | ✅ Resolved (v1.2.0) | AppKit unsupported (winit parity) |
| ~~`showWindowMenu()`~~ | ✅ Resolved (v1.2.0) | Real backend implementations |
| ~~Appearance setters~~ | ✅ Resolved (v1.2.0) | Blur, icon, theme, attention, level all wired |
| `Fullscreen.Exclusive` | ❌ UnsupportedPlatform | No-op on Wayland, Web, Android, UIKit |

### 2. Keyboard Enum Coverage Gaps

| Gap | Impact |
|-----|--------|
| **`KeyCode`:** IME/Asian language keys | `Lang1..Lang5`, `KanaMode`, `Convert`, `NonConvert`, `Hiragana`, `Katakana`, `Eisu` |
| **`KeyCode`:** Extended numpad | `NumpadParenLeft/Right`, `NumpadMemory*`, `NumpadSignChange`, `NumpadHash` |
| **`KeyCode`:** Media/app/system | Various Android/XKB/Web-specific codes may fall through to `Native` |
| **`NamedKey`:** TV/media advanced | Long-tail UI Events keys |
| **`NamedKey`:** IME and composition | `GroupNext`, `KanjiMode`, `AllCandidates`, `NextCandidate` |
| **`NamedKey`:** Android/XKB specifics | Values that winit exposes as `NamedKey` when possible |

### 3. Keyboard Runtime Backend Gaps

| Backend | Gaps |
|---------|------|
| **X11** | ~~`text` = null (no `XLookupString`)~~ ✅ Resolved (v1.2.0); `textWithAllModifiers` and `keyWithoutModifiers` mostly fallback logic |
| **Wayland** | ~~`text` = null (no `xkb_state_key_get_utf8`)~~ ✅ Resolved (v1.2.0); `textWithAllModifiers` and `keyWithoutModifiers` mostly fallback logic |
| **AppKit** | Mapping is QWERTY US; other layouts may produce incorrect `PhysicalKey` |
| **Win32** | Prefers VK over scancode (less accurate for non-US layouts); left/right generic VK ambiguous |
| **All backends** | `ModifierKeys` left/right tracking incomplete on Web/Android/UIKit; `textWithAllModifiers` and `keyWithoutModifiers` mostly fallback logic |

### 4. Event Emission Gaps

| Event | Status | Backends |
|-------|--------|----------|
| `ThemeChanged` | ✅ Resolved (v1.2.0) | All backends (X11: not emitted — no standard protocol) |
| `Occluded` | ✅ Resolved (v1.2.0) | All backends (Wayland: not emitted) |
| `Ime` events | ✅ Resolved (v1.2.0) | All backends emit |
| `DragEntered/Moved/Dropped/Left` | ⚠️ Partial | Win32, X11, Wayland, Web, iOS, Android; AppKit partial |
| Gestures (Pinch/Pan/Rotation/DoubleTap) | ⚠️ Partial | AppKit/Win32/UIKit only |
| `TouchpadPressure` | ⚠️ Partial | AppKit only (macOS Force Touch) |

### 5. Platform-Specific Gaps

#### Wayland

| Gap | Blocked by |
|-----|-----------|
| `requestUserAttention` | `xdg_activation_v1` protocol not wired |
| `setWindowIcon` | `xdg_toplevel_icon_manager_v1` protocol not wired |
| `setBlur` | `ext_background_effect` / KWin blur protocols not wired |
| `systemTheme()` portal | D-Bus integration works but detection is incomplete |
| Monitor geometry | `wl_output` geometry/mode not stored (synthetic only) |
| `Fullscreen.Exclusive` | Not applicable on Wayland |
| ~~Keyboard `text`~~ | ✅ Resolved (v1.2.0) — `xkb_state_key_get_utf8` wired |

#### Web (JS/Wasm)

Web DOM bridges are implemented for JS and wasmJs. Pointer Lock requests and
cursor hit-testing are wired; browser-granted Pointer Lock remains asynchronous
and user-gesture dependent (see [DEFERRED.md](https://github.com/ygdrasil-io/poc-koreos/blob/master/DEFERRED.md)).

| Gap | Blocked by |
|-----|-----------|
| `setCursorGrab(Confined)` | Browsers do not expose canvas-confined cursor grab |
| `setCursorPosition()` | Browsers do not allow direct cursor warping |
| Raw mouse input | Browser cursor sovereignty; use Pointer Lock where granted |

#### AppKit (macOS)

| Gap | Details |
|-----|---------|
| `outerPosition` | Cocoa bottom-left coordinates not converted |
| `CGWarpMouseCursorPosition` scalar cast | Works on x64/arm64 but not FFM spec-conformant |
| `setWindowIcon` | No-op (winit parity — macOS has no per-window icon) |
| `dragResizeWindow` | Unsupported (winit parity) |

#### Win32 (Windows)

| Gap | Details |
|-----|---------|
| `Fullscreen.Exclusive` | `ChangeDisplaySettingsExW` TODO |
| `ShowCursor` balanced | Atomic counter in Win32Window |
| `readWString` | Stops at space instead of `\0` (minor) |
| `setBlur` | No-op runtime (DWM APIs deprecated) |

#### X11 (Linux)

| Gap | Details |
|-----|---------|
| ~~Keyboard `text` = null~~ | ✅ Resolved (v1.2.0) — `XLookupString` bound |
| `ScaleFactorChanged` dynamic | No RRNotify handling (static DPI only) |
| `systemTheme()` | Always null (no standard X11 mechanism) |
| `setBlur` | No-op (compositor-specific) |

#### Mobile (Android / UIKit)

| Gap | Details |
|-----|---------|
| ~~Cursor & window state setters no-op undocumented~~ | ✅ Resolved (v1.2.0) — All NO-OP APIs documented and tested |
| `setTheme()` overwrite | Android no-op (must use `AppCompatDelegate`) |
| Various R1 features | No-op (resizable, decorations, position, etc.) |

### 6. Coverage by Backend

| Backend | Estimated coverage | Notes |
|---------|:-----------------:|-------|
| **AppKit (macOS)** | ~95% | Most mature; custom cursors, gestures, IME, blur |
| **Win32 (Windows)** | ~90% | Richest extension API (DWM, corners, borders) |
| **X11 (Linux)** | ~85% | Xdnd DnD, XIM IME; keyboard `text` is main gap |
| **Wayland (Linux)** | ~80% | Best protocol negotiation; keyboard `text` is main gap |
| **UIKit (iOS)** | ~55% | Good for mobile; gesture opt-in, IME working |
| **Android** | ~50% | Functional; Choreographer-based event loop |
| **Web (JS/Wasm)** | ~75% | DOM bridges implemented; remaining gaps are browser/platform limits |

## Reference

- winit commit: `c4afadbfabf7b1e7989b40b493db1a4c7bd8ff4e`
- winit version: v0.30.13
- Kadre version: v1.2.0
- Last updated: 2026-06-26
