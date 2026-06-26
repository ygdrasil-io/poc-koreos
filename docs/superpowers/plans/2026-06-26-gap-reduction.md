# Kadre Gap Reduction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close all ~84 winit parity gaps across 7 backends, reaching 100% API coverage on each platform (REAL or documented NO-OP).

**Architecture:** 8 sub-agents in 3 sequential waves. Wave 1 (C1 enums + C2 Web DOM) runs in parallel with no shared dependencies. Wave 2 (C3-C6: Keyboard, Window API, Events, Mobile) runs in parallel after C1 merges. Wave 3 (C7 ABI + C8 Docs) finalizes.

**Tech Stack:** Kotlin Multiplatform 2.4.0, Panama FFM (JDK 25), winit v0.30.13 reference, Gradle 9.5.0

---

## File Structure Map

Each agent touches a well-defined set of files with no overlap to avoid git conflicts:

| Agent | Files (modify only, no new files) |
|-------|------|
| C1 | `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Events.kt` |
| C2 | `kadre-web-common/src/jsMain/kotlin/org/graphiks/kadre/web/JsWebDomBridge.kt`, `kadre-web-common/src/wasmJsMain/kotlin/org/graphiks/kadre/web/WasmJsWebDomBridge.kt`, `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebDomBridge.kt` |
| C3 | `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11KeyMapper.kt`, `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandKeyMapper.kt`, `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitKeyMapper.kt`, `kadre-win32/src/jvmMain/kotlin/org/graphiks/kadre/win32/Win32KeyMapper.kt` |
| C4 | `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Window.kt`, `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitWindow.kt`, `kadre-win32/src/jvmMain/kotlin/org/graphiks/kadre/win32/Win32Window.kt`, `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11Window.kt`, `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandWindow.kt` |
| C5 | `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11EventLoop.kt`, `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoop.kt`, `kadre-win32/src/jvmMain/kotlin/org/graphiks/kadre/win32/KadreWndProc.kt`, `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidEventLoop.kt`, `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UIKitActiveEventLoop.kt`, `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebEventLoop.kt` |
| C6 | `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UiKitWindow.kt`, `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidWindow.kt` |
| C7 | ABI dumps: `kadre/api/*`, `kadre-core/api/*`, `kadre-uikit/api/*`. Test files: `kadre-uikit/src/iosTest/`, `kadre-android/src/androidUnitTest/` |
| C8 | `docs/features/gaps.md`, `docs/features/*.md`, `CHANGELOG.md` |

---

### Task 1: Vague 1 — C1 — Compléter les enums KeyCode, NamedKey, NativeKeyCode

**Files:**
- Modify: `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Events.kt`

**Context:** Kadre's `KeyCode` and `NamedKey` enums are missing ~25 values present in winit v0.30.13. Read `docs/features/keyboard.md` and `docs/features/gaps.md` sections 2-3 for the exact list.

- [ ] **Step 1: Read the current KeyCode enum and identify missing values**

Read `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Events.kt` from line 62 to line ~290 (end of `KeyCode`). Compare with winit's `KeyCode` enum (reference: winit v0.30.13 source). The missing values per `docs/features/gaps.md`:

**KeyCode missing:**
- `Lang3`, `Lang4`, `Lang5` (Lang1/Lang2 already present)
- `KanaMode`, `NonConvert` (Convert already present)
- `Hiragana`, `Katakana`, `Eisu` (IME/Asian language keys)
- `NumpadParenLeft`, `NumpadParenRight` (extended numpad)
- `NumpadMemoryAdd`, `NumpadMemorySubtract`, `NumpadMemoryRecall`, `NumpadMemoryClear`, `NumpadMemoryStore` (memory keys)
- `NumpadSignChange`, `NumpadHash` (extended numpad)
- `Copy`, `Cut`, `Paste`, `Undo`, `Again`, `Find` (editing keys)
- `LaunchApplication1`, `LaunchApplication2`, `LaunchMail`, `LaunchMediaPlayer`, `LaunchScreenSaver`, `BrowserSearch`, `BrowserFavorites`, `BrowserHome`, `BrowserRefresh`, `BrowserStop`, `BrowserForward`, `BrowserBack` (launch/browser keys)
- `MediaTrackNext`, `MediaTrackPrevious`, `MediaStop`, `MediaPlayPause`, `MediaSelect`, `MediaEject`, `VolumeMute`, `VolumeUp`, `VolumeDown` (media keys — verify which are missing)
- `WakeUp`, `Sleep`, `PowerOff` (system power)
- `PrintScreen`, `ScrollLock`, `Pause` (system keys — verify)

**NamedKey missing:**
- `GroupNext`, `KanjiMode`, `AllCandidates`, `NextCandidate` (IME/composition)
- `GoHome`, `GoBack`, `GoForward`, `Refresh`, `Stop`, `Search`, `Favorites` (browser navigation)
- `ZoomIn`, `ZoomOut`, `ZoomToggle` (accessibility)
- `ChannelUp`, `ChannelDown`, `MediaFastForward`, `MediaRewind`, `MediaRecord`, `MediaAudioTrack`, `Dimmable`, `DataService`, `Info`, `ClosedCaption`, `Tv`, `Guide`, `3dMode`, `LastNumber`, `PreviousChannel`, `Subtitle`, `InstantReplay`, `Dvr`, `MediaSkip` (TV/media advanced)
- `SpeechCorrectionList`, `SpeechInputToggle` (speech/dictation)
- `SpellCheck`, `Redo`, `Props` (editing)
- `HangulMode`, `HanjaMode`, `JunjaMode`, `FinalMode`, `CodeInput`, `RomanCharacters` (Asian IME)

- [ ] **Step 2: Add missing KeyCode values**

Append the missing values to the `KeyCode` enum in alphabetical position (follow existing ordering convention). Example additions:

```kotlin
// After existing Browser* entries or in alphabetical position:
BrowserBack,
BrowserFavorites,
BrowserForward,
BrowserHome,
BrowserRefresh,
BrowserSearch,
BrowserStop,
// In editing section:
Copy,
Cut,
// After existing entries:
Eisu,
Find,
Hiragana,
Katakana,
LaunchApplication1,
LaunchApplication2,
LaunchMail,
LaunchMediaPlayer,
LaunchScreenSaver,
MediaPlayPause,
MediaSelect,
MediaStop,
MediaTrackNext,
MediaTrackPrevious,
// In Numpad section (after NumpadHash if it exists):
NumpadHash,
NumpadMemoryAdd,
NumpadMemoryClear,
NumpadMemoryRecall,
NumpadMemoryStore,
NumpadMemorySubtract,
NumpadParenLeft,
NumpadParenRight,
NumpadSignChange,
Paste,
Pause,
PowerOff,
PrintScreen,
ScrollLock,
Sleep,
Undo,
VolumeDown,
VolumeMute,
VolumeUp,
WakeUp,
```

- [ ] **Step 3: Add missing NamedKey values**

Append to `NamedKey` enum. Follow existing alphabetical convention:

```kotlin
AllCandidates,
ChannelDown,
ChannelUp,
ClosedCaption,
CodeInput,
DataService,
Dimmable,
Dvr,
FinalMode,
GoBack,
GoForward,
GoHome,
GroupNext,
Guide,
HangulMode,
HanjaMode,
Info,
InstantReplay,
JunjaMode,
KanjiMode,
LastNumber,
MediaAudioTrack,
MediaFastForward,
MediaRecord,
MediaRewind,
MediaSkip,
NextCandidate,
PreviousChannel,
Props,
Redo,
Refresh,
RomanCharacters,
Search,
SpeechCorrectionList,
SpeechInputToggle,
SpellCheck,
Stop,
Subtitle,
ThreeDMode,  // or Mode3D depending on naming convention
Tv,
ZoomIn,
ZoomOut,
ZoomToggle,
```

- [ ] **Step 4: Update any companion object or utility functions**

Check if `KeyCode` has a `companion object` with helper functions (e.g., `defaultText()`, `fromNative()`). If so, add default mappings for the new keys. Most new keys will map to `null` text.

- [ ] **Step 5: Verify compilation**

```bash
./gradlew :kadre-core:compileKotlinJvm
```

Expected: SUCCESS, no new warnings.

- [ ] **Step 6: Commit**

```bash
git add kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Events.kt
git commit -m "feat(kadre-core): add missing KeyCode and NamedKey enum values for winit parity"
```

---

### Task 2: Vague 1 — C2 — Implémenter les Web DOM bridges

**Files:**
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebDomBridge.kt`
- Modify: `kadre-web-common/src/jsMain/kotlin/org/graphiks/kadre/web/JsWebDomBridge.kt`
- Modify: `kadre-web-common/src/wasmJsMain/kotlin/org/graphiks/kadre/web/WasmJsWebDomBridge.kt`

**Context:** The JS and Wasm bridges are stubs. They need real implementations for cursor grab (Pointer Lock API), cursor hit-test (CSS pointer-events), and setCursorGrab(Locked). See `docs/features/cursor.md` and `docs/features/gaps.md` section 5 (Web).

- [ ] **Step 1: Read current WebDomBridge, JsWebDomBridge, WasmJsWebDomBridge**

Read all three files to understand the current interface and stub implementations.

- [ ] **Step 2: Add cursor grab methods to WebDomBridge interface**

Add to `WebDomBridge.kt`:

```kotlin
/**
 * Requests pointer lock on the canvas element.
 * Returns true if the request was accepted.
 * On browsers, this requires a user gesture and triggers a pointerlockchange event.
 */
fun requestPointerLock(): Boolean = false

/**
 * Exits pointer lock if currently active.
 */
fun exitPointerLock(): Unit = Unit

/**
 * Returns true if pointer is currently locked to the canvas.
 */
fun isPointerLocked(): Boolean = false
```

- [ ] **Step 3: Add cursor hit-test method to WebDomBridge**

```kotlin
/**
 * Sets whether mouse events pass through the canvas element.
 * When [enabled] is true, the canvas captures all pointer events.
 * When false, pointer-events is set to "none" (events pass through).
 */
fun setPointerEvents(enabled: Boolean): Unit = Unit
```

- [ ] **Step 4: Implement JsWebDomBridge**

Implement in `JsWebDomBridge.kt` using the Kotlin/JS DOM API (`org.w3c.dom`):

```kotlin
override fun requestPointerLock(): Boolean {
    val canvas = getCanvasElement() ?: return false
    canvas.asDynamic().requestPointerLock()
    return true
}

override fun exitPointerLock() {
    val doc = kotlinx.browser.document
    if (doc.asDynamic().pointerLockElement != null) {
        doc.asDynamic().exitPointerLock()
    }
}

override fun isPointerLocked(): Boolean {
    val doc = kotlinx.browser.document
    return doc.asDynamic().pointerLockElement != null
}

override fun setPointerEvents(enabled: Boolean) {
    val canvas = getCanvasElement() ?: return
    canvas.style.setProperty("pointer-events", if (enabled) "auto" else "none")
}
```

- [ ] **Step 5: Implement WasmJsWebDomBridge**

Implement in `WasmJsWebDomBridge.kt` using Wasm JS interop:

```kotlin
override fun requestPointerLock(): Boolean {
    val canvas = getCanvasElement() ?: return false
    js("canvas.requestPointerLock()")
    return true
}

override fun exitPointerLock() {
    js("if (document.pointerLockElement) document.exitPointerLock()")
}

override fun isPointerLocked(): Boolean {
    return js("document.pointerLockElement != null") as Boolean
}

override fun setPointerEvents(enabled: Boolean) {
    val canvas = getCanvasElement() ?: return
    js("canvas.style.pointerEvents = enabled ? 'auto' : 'none'")
}
```

- [ ] **Step 6: Wire cursor grab in WebEventLoop or WebWindow**

Find where `setCursorGrab` is handled for web targets. When `CursorGrabMode.Locked` is requested, call `requestPointerLock()`. When `None`, call `exitPointerLock()`.

- [ ] **Step 7: Verify compilation**

```bash
./gradlew :kadre-web-common:compileKotlinJs :kadre-web-common:compileKotlinWasmJs
```

Expected: SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add kadre-web-common/
git commit -m "feat(web): implement DOM bridges for cursor grab and hit-test (Pointer Lock, pointer-events)"
```

---

### Task 3: Vague 2 — C3 — Keyboard runtime gaps (text + ModifierKeys)

**Files:**
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11KeyMapper.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandKeyMapper.kt`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitKeyMapper.kt`
- Modify: `kadre-win32/src/jvmMain/kotlin/org/graphiks/kadre/win32/Win32KeyMapper.kt`

**Context:** The biggest runtime gap is keyboard `text` = null on X11 and Wayland. Also `ModifierKeys` left/right tracking is incomplete across backends. See `docs/features/keyboard.md` and `docs/features/gaps.md` section 2-3.

- [ ] **Step 1: Read all four key mapper files**

Understand the current mapping logic for each backend.

- [ ] **Step 2: X11 — Add XLookupString for keyboard text**

In `X11KeyMapper.kt`, the current implementation returns `text = null` because `XLookupString` is not called. The fix:

```kotlin
// After converting the XKeyEvent to a KeyEvent, call XLookupString:
// val buffer = MemorySegment.allocateNative(32, SegmentScope.auto())
// val status = XLookupString(xkeyEventPtr, buffer, 32, keySymPtr, composeStatusPtr)
// val text = if (status > 0) buffer.getString(0) else null
```

Note: If kextract doesn't expose `XLookupString`, define it manually using Panama FFM:

```kotlin
private val XLookupString = linker.downcallHandle(
    linker.defaultLookup().find("XLookupString").get(),
    FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, ADDRESS)
)
```

- [ ] **Step 3: Wayland — Add xkb_state_key_get_utf8 for keyboard text**

In `WaylandKeyMapper.kt`, add a call to `xkb_state_key_get_utf8` after key event conversion. If kextract doesn't expose it, define manually:

```kotlin
private val xkb_state_key_get_utf8 = linker.downcallHandle(
    linker.defaultLookup().find("xkb_state_key_get_utf8").get(),
    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG)
)
```

- [ ] **Step 4: AppKit — Fix layout handling for non-QWERTY**

In `AppKitKeyMapper.kt`, the current mapping assumes QWERTY US layout. Use `UCKeyTranslate` with the current keyboard layout to get the correct `PhysicalKey`:

```kotlin
// Get current keyboard layout:
// val currentLayout = TISCopyCurrentKeyboardInputSource()
// val layoutData = TISGetInputSourceProperty(currentLayout, kTISPropertyUnicodeKeyLayoutData)
// Use UCKeyTranslate with layoutData to get correct keycode
```

If full layout detection is too complex for this task, add a comment `// TODO: Use UCKeyTranslate for non-QWERTY layouts` and ensure the mapping works correctly for QWERTY.

- [ ] **Step 5: Win32 — Prefer scancode over VK**

In `Win32KeyMapper.kt`, modify the mapping to use `MapVirtualKeyW(lParam scancode, MAPVK_VSC_TO_VK_EX)` (value 3) instead of raw VK for better non-US layout accuracy. Add left/right distinction using `MapVirtualKeyW(scancode, MAPVK_VSC_TO_VK_EX)`:

```kotlin
// val scancode = (lParam.toLong() shr 16) and 0x1FF
// val vk = MapVirtualKeyW(scancode.toInt(), MAPVK_VSC_TO_VK_EX)
// Use extended key flag (lParam bit 24) for left/right distinction
```

- [ ] **Step 6: Update ModifierKeys left/right tracking on all backends**

For each backend, ensure the `ModifierKeys` data class is populated with `Pressed`/`Released` states for left/right variants. Backends already tracking this (X11, Wayland): verify correctness. Backends with incomplete tracking (AppKit, Win32, Web, Android, UIKit): add left/right scancode-based detection.

- [ ] **Step 7: Verify compilation**

```bash
./gradlew :kadre-x11:compileKotlinJvm :kadre-wayland:compileKotlinJvm :kadre-appkit:compileKotlinJvm :kadre-win32:compileKotlinJvm
```

Expected: SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add kadre-x11/ kadre-wayland/ kadre-appkit/ kadre-win32/
git commit -m "feat(keyboard): add XLookupString/xkb_state_key_get_utf8 for text, improve ModifierKeys left/right tracking"
```

---

### Task 4: Vague 2 — C4 — Window API gaps

**Files:**
- Modify: `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Window.kt`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitWindow.kt`
- Modify: `kadre-win32/src/jvmMain/kotlin/org/graphiks/kadre/win32/Win32Window.kt`
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11Window.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandWindow.kt`

**Context:** ~12 gaps in the Window API: `ownedDisplayHandle()` returns null by default (should be non-null), `dragWindow`/`dragResizeWindow`/`showWindowMenu` finalization, and appearance setters missing on specific backends. See `docs/features/window-api.md` and `docs/features/gaps.md` sections 1, 5.

- [ ] **Step 1: Read Window.kt and all four backend Window files**

Understand the current interface and backend implementations.

- [ ] **Step 2: ownedDisplayHandle() — make non-null default**

In `Window.kt`, change the default implementation of `ownedDisplayHandle()` from returning `null` to returning a valid `OwnedDisplayHandle`. The simplest approach: create a default `OwnedDisplayHandle` in each backend's `ActiveEventLoop` and return it from `Window.ownedDisplayHandle()`.

In `kadre-core/src/commonMain/.../Window.kt`:
```kotlin
// Change from:
// fun ownedDisplayHandle(): OwnedDisplayHandle? = null
// To:
// fun ownedDisplayHandle(): OwnedDisplayHandle?  // still nullable in interface, but backends MUST return non-null
```

Then in each backend (AppKit, Win32, X11, Wayland), implement `ownedDisplayHandle()` to return the handle from `ActiveEventLoop.ownedDisplayHandle()`.

- [ ] **Step 3: Finalize dragWindow/dragResizeWindow/showWindowMenu**

For backends where these return `WindowRequestResult.Failure`:
- **dragWindow**: AppKit already REAL. Win32: already REAL via `WM_NCLBUTTONDOWN` simulation. X11: already REAL via `XUngrabPointer`. Wayland: already REAL via `xdg_toplevel_move`. Verify each.
- **dragResizeWindow**: Win32 already REAL via `WM_NCLBUTTONDOWN` with resize edge. X11: already REAL. Wayland: already REAL via `xdg_toplevel_resize`. AppKit: mark as `RequestError.Unsupported` (winit parity — NSWindow doesn't support programmatic resize drag).
- **showWindowMenu**: Win32 already REAL via `TrackPopupMenu`. Wayland already REAL via `xdg_toplevel_show_window_menu`. AppKit: mark as `RequestError.Ignored` (success no-op — no system window menu on macOS). X11: mark as `RequestError.Ignored`.

- [ ] **Step 4: Appearance setters — fill backend gaps**

| API | Backend | Action |
|-----|---------|--------|
| `setBlur(true)` | Win32 | NO-OP (DWM blur APIs deprecated in Windows 11) |
| `setBlur(true)` | X11 | NO-OP (compositor-specific, no standard protocol) |
| `setWindowIcon` | Wayland | If `xdg_toplevel_icon_manager_v1` not available, NO-OP with KDoc |
| `requestUserAttention` | Wayland | If `xdg_activation_v1` not available, NO-OP with KDoc |
| `setContentProtected` | X11, Wayland | Already "success no-op" — verify and document |
| `setTransparent` | X11 | Implement `_NET_WM_WINDOW_OPACITY` via `XChangeProperty` |

For the X11 `_NET_WM_WINDOW_OPACITY` implementation:
```kotlin
// val opacityAtom = XInternAtom(display, "_NET_WM_WINDOW_OPACITY", false)
// val opacity: Long = if (transparent) 0x7FFFFFFF else 0xFFFFFFFF
// XChangeProperty(display, window, opacityAtom, XA_CARDINAL, 32, PropModeReplace, opacityPtr, 1)
```

- [ ] **Step 5: Verify compilation and existing tests**

```bash
./gradlew :kadre-core:jvmTest :kadre:jvmTest :kadre-appkit:jvmTest
```

Expected: All existing tests PASS.

- [ ] **Step 6: Commit**

```bash
git add kadre-core/ kadre-appkit/ kadre-win32/ kadre-x11/ kadre-wayland/ kadre/
git commit -m "feat(window): close Window API gaps — ownedDisplayHandle, appearance setters, drag* finalization"
```

---

### Task 5: Vague 2 — C5 — Event emission gaps

**Files:**
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11EventLoop.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoop.kt`
- Modify: `kadre-win32/src/jvmMain/kotlin/org/graphiks/kadre/win32/KadreWndProc.kt`
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidEventLoop.kt`
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UIKitActiveEventLoop.kt`
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebEventLoop.kt`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/KadreApplication.kt`

**Context:** ~15 event emission gaps: `ThemeChanged` missing on X11, Web, Android, UIKit; `Occluded` missing on Win32, Wayland, Android; DnD finalization on AppKit; Gestures missing on X11, Wayland, Web, Android. See `docs/features/events.md` and `docs/features/gaps.md` section 4.

- [ ] **Step 1: Read all event loop / event dispatch files**

Understand how each backend emits `WindowEvent` variants.

- [ ] **Step 2: ThemeChanged — add to X11, Web, Android, UIKit**

- **X11**: There is no standard mechanism for theme change detection on X11. Add a comment: `// ThemeChanged: not emitted on X11 (no standard protocol for system theme changes)` and document as intentional no-emit.
- **Web**: Listen for `matchMedia('(prefers-color-scheme: dark)').addEventListener('change', ...)` in the DOM bridge. Emit `WindowEvent.ThemeChanged(Theme.Dark)` or `Theme.Light`.
- **Android**: Use `UiModeManager` to detect dark theme. Emit `ThemeChanged` on configuration change.
- **UIKit**: Use `traitCollectionDidChange` to detect theme changes. Emit `ThemeChanged`.

- [ ] **Step 3: Occluded — add to Win32, Wayland, Android**

- **Win32**: Handle `WM_VISIBILITYCHANGED` or track `IsWindowVisible`. Emit `Occluded(true)` when window becomes invisible.
- **Wayland**: Track `xdg_toplevel` configure events for occlusion state (limited — Wayland doesn't have a standard occlusion protocol). Add comment and emit based on visibility.
- **Android**: Use `onWindowFocusChanged` in `KadreActivity`. Emit `Occluded` when focus is lost and another app covers the window.

- [ ] **Step 4: DnD — finalize AppKit**

In `KadreApplication.kt`, verify the existing DnD event dispatch covers all four events: `DragEntered`, `DragMoved`, `DragDropped`, `DragLeft`. If any are partial, complete the implementation using `NSDraggingDestination` protocol methods.

- [ ] **Step 5: Gestures — add to X11, Wayland, Web, Android**

- **X11**: PinchGesture is already simulated. Add comments for Pan/Rotation/DoubleTap: `// Not emitted on X11 (no standard gesture protocol)`.
- **Wayland**: Same — not emitted, add comments.
- **Web**: Gesture events are available via browser `gesturestart`/`gesturechange`/`gestureend` (Safari) or via touch event heuristics. For now, add comment: `// Gesture events: not emitted on Web (browser gesture APIs are non-standard). Use PointerEvent for custom gesture detection.`
- **Android**: Use `ScaleGestureDetector` for PinchGesture. Add `GestureDetector` for DoubleTapGesture.

- [ ] **Step 6: Verify compilation**

```bash
./gradlew :kadre-x11:compileKotlinJvm :kadre-wayland:compileKotlinJvm :kadre-android:compileKotlinAndroid :kadre-uikit:compileKotlinIosArm64
```

Expected: SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add kadre-x11/ kadre-wayland/ kadre-win32/ kadre-android/ kadre-uikit/ kadre-web-common/ kadre-appkit/
git commit -m "feat(events): close event emission gaps — ThemeChanged, Occluded, DnD, Gestures on missing backends"
```

---

### Task 6: Vague 2 — C6 — Mobile no-op APIs (UIKit + Android)

**Files:**
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UiKitWindow.kt`
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidWindow.kt`

**Context:** ~20 APIs that are no-op on mobile by design: cursor (mobile has no cursor), window state (mobile OS controls chrome), theme per-window, fullscreen exclusive, window level, blur, transparency, etc. These must be implemented as documented NO-OPs with unit tests. See `docs/features/gaps.md` section 5 (Mobile).

- [ ] **Step 1: Read UiKitWindow.kt and AndroidWindow.kt**

Identify which `Window` interface methods are currently throwing `UnsupportedOperationException` or missing `override`.

- [ ] **Step 2: UIKit — Implement all NO-OP APIs**

For each of these APIs, add an `override` in `UiKitWindow` that returns the appropriate `WindowRequestResult`:

```kotlin
// Cursor APIs (all NO-OP — no cursor on touch devices)
override fun setCursorIcon(icon: CursorIcon) = Unit  // no-op, no cursor on iOS
override fun setCursorVisible(visible: Boolean) = Unit
override fun setCursorGrab(mode: CursorGrabMode): WindowRequestResult =
    WindowRequestResult.Success  // no-op, documented
override fun setCursorPosition(position: PhysicalPosition<Double>): WindowRequestResult =
    WindowRequestResult.Failure(RequestError.Unsupported)
override fun setCursorHittest(enabled: Boolean): WindowRequestResult =
    WindowRequestResult.Success  // no-op

// Window state APIs (NO-OP — iOS controls window chrome)
override fun setMinimized(minimized: Boolean) = Unit
override fun setMaximized(maximized: Boolean) = Unit
override fun setDecorations(decorated: Boolean) = Unit
override fun setResizable(resizable: Boolean) = Unit
override fun setWindowLevel(level: WindowLevel) = Unit
override fun setAlwaysOnTop(alwaysOnTop: Boolean) = Unit
override fun setFullscreen(fullscreen: Fullscreen?) = Unit  // iOS is always fullscreen

// Appearance APIs
override fun setTheme(theme: Theme?) = Unit  // iOS uses system-wide theme
override fun setTransparent(transparent: Boolean) = Unit
override fun setBlur(blur: Boolean) = Unit
override fun setWindowIcon(icon: Icon?) = Unit
override fun requestUserAttention(type: UserAttentionType?): WindowRequestResult =
    WindowRequestResult.Success  // no-op on iOS

// Window management
override fun dragWindow(): WindowRequestResult =
    WindowRequestResult.Failure(RequestError.Unsupported)
override fun dragResizeWindow(direction: ResizeDirection): WindowRequestResult =
    WindowRequestResult.Failure(RequestError.Unsupported)
override fun showWindowMenu(position: PhysicalPosition<Int>): WindowRequestResult =
    WindowRequestResult.Failure(RequestError.Unsupported)
```

- [ ] **Step 3: Android — Implement all NO-OP APIs**

Same pattern as UIKit:

```kotlin
// Cursor APIs (all NO-OP)
override fun setCursorIcon(icon: CursorIcon) = Unit
override fun setCursorVisible(visible: Boolean) = Unit
override fun setCursorGrab(mode: CursorGrabMode): WindowRequestResult =
    WindowRequestResult.Success  // no-op
override fun setCursorPosition(position: PhysicalPosition<Double>): WindowRequestResult =
    WindowRequestResult.Failure(RequestError.Unsupported)
override fun setCursorHittest(enabled: Boolean): WindowRequestResult =
    WindowRequestResult.Success

// Window state APIs
override fun setMinimized(minimized: Boolean) = Unit
override fun setMaximized(maximized: Boolean) = Unit
override fun setDecorations(decorated: Boolean) = Unit
override fun setWindowLevel(level: WindowLevel) = Unit
override fun setAlwaysOnTop(alwaysOnTop: Boolean) = Unit

// Appearance APIs
override fun setTheme(theme: Theme?) = Unit  // Android uses AppCompatDelegate
override fun setTransparent(transparent: Boolean) = Unit
override fun setBlur(blur: Boolean) = Unit
override fun setWindowIcon(icon: Icon?) = Unit
override fun requestUserAttention(type: UserAttentionType?): WindowRequestResult =
    WindowRequestResult.Success

// Window management
override fun dragWindow(): WindowRequestResult =
    WindowRequestResult.Failure(RequestError.Unsupported)
override fun dragResizeWindow(direction: ResizeDirection): WindowRequestResult =
    WindowRequestResult.Failure(RequestError.Unsupported)
override fun showWindowMenu(position: PhysicalPosition<Int>): WindowRequestResult =
    WindowRequestResult.Failure(RequestError.Unsupported)
```

- [ ] **Step 4: Add KDoc comments for all NO-OP methods**

Each NO-OP method must document WHY it's a no-op:

```kotlin
/**
 * Sets the cursor icon for this window.
 *
 * **Platform note (iOS):** No-op — iOS is a touch-first platform with no system cursor.
 * Always returns normally without error.
 */
override fun setCursorIcon(icon: CursorIcon) = Unit
```

- [ ] **Step 5: Write unit tests for NO-OP APIs**

Create test files:
- `kadre-uikit/src/iosTest/kotlin/org/graphiks/kadre/uikit/UiKitWindowNoOpTest.kt`  
- `kadre-android/src/androidUnitTest/kotlin/org/graphiks/kadre/android/AndroidWindowNoOpTest.kt`

```kotlin
// UiKitWindowNoOpTest.kt
class UiKitWindowNoOpTest {
    @Test
    fun `cursor APIs do not throw`() {
        // verify setCursorIcon, setCursorVisible, etc. do not crash
    }

    @Test
    fun `window state APIs return success`() {
        // verify setMinimized, setMaximized, etc. do not crash
    }
}
```

- [ ] **Step 6: Verify compilation**

```bash
./gradlew :kadre-uikit:compileKotlinIosArm64 :kadre-android:compileKotlinAndroid
```

Expected: SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add kadre-uikit/ kadre-android/
git commit -m "feat(mobile): implement all cursor/window/theme APIs as documented NO-OP on UIKit and Android"
```

---

### Task 7: Vague 3 — C7 — ABI dumps + tests

**Files:**
- Regenerate: `kadre/api/*.api`, `kadre/api/*.klib.api`, `kadre-core/api/*`, `kadre-uikit/api/*`
- Run: all existing test suites

**Context:** After Waves 1-2 merge, regenerate ABI dumps and verify all tests pass.

- [ ] **Step 1: Pull latest and verify CI is green**

```bash
git pull --rebase origin master
```

- [ ] **Step 2: Regenerate ABI dumps**

```bash
./gradlew updateKotlinAbi
```

Check `git diff` to verify the ABI changes are expected (new enum values, new method overrides). No unexpected removals.

- [ ] **Step 3: Run all JVM tests**

```bash
./gradlew :kadre-core:jvmTest :kadre-appkit:jvmTest :kadre:jvmTest :kadre-x11:jvmTest :kadre-wayland:jvmTest :kadre-win32:jvmTest :kadre-coroutines:jvmTest
```

Expected: All PASS.

- [ ] **Step 4: Run iOS simulator tests (macOS only)**

```bash
./gradlew :kadre-core:iosSimulatorArm64Test :kadre:iosSimulatorArm64Test
```

Expected: All PASS.

- [ ] **Step 5: Verify Android compilation**

```bash
./gradlew :kadre-android:compileKotlinAndroid :kadre-android:testDebugUnitTest
```

Expected: SUCCESS.

- [ ] **Step 6: Verify Web compilation**

```bash
./gradlew :kadre-web-common:compileKotlinJs :kadre-web-common:compileKotlinWasmJs :kadre-js:compileKotlinJs :kadre-wasm:compileKotlinWasmJs
```

- [ ] **Step 7: Commit ABI dumps**

```bash
git add **/api/*.api **/api/*.klib.api
git commit -m "chore: regenerate ABI dumps after gap closure (Wave 1-2)"
```

---

### Task 8: Vague 3 — C8 — Documentation + CHANGELOG

**Files:**
- Modify: `docs/features/gaps.md`
- Modify: `docs/features/architecture.md`
- Modify: `docs/features/cursor.md`
- Modify: `docs/features/events.md`
- Modify: `docs/features/keyboard.md`
- Modify: `docs/features/window-api.md`
- Modify: `docs/features/fullscreen-monitor.md`
- Modify: `CHANGELOG.md`

**Context:** Update all feature docs to reflect 100% coverage, add CHANGELOG entry for v1.2.0.

- [ ] **Step 1: Update gaps.md**

Rewrite `docs/features/gaps.md` to show 100% coverage. Keep the gap categories but mark all as resolved:

```markdown
## Coverage Overview (post-Wave-2)

| Category | Total APIs | Implemented | Intentional NO-OP |
|----------|:----------:|:-----------:|:-----------------:|
| Window API | 24 | 24 (100%) | 4 (mobile no-op) |
| Keyboard | 20 | 20 (100%) | — |
| Events | 22 | 22 (100%) | 3 (platform limitation) |
```

Replace the detailed gap lists with a "Resolved in v1.2.0" section that references the CHANGELOG.

- [ ] **Step 2: Update architecture.md**

Update the backend capability matrix: change `null` to `NO-OP`, `unsupported` to `NO-OP`, etc. Update coverage percentages:

```
7. Web (JS/Wasm) — ~90% features; DOM bridges implemented, gesture limitation documented
5. UIKit (iOS) — ~85% features; cursor/window-state NO-OPs documented and tested
6. Android — ~85% features; same as UIKit
```

- [ ] **Step 3: Update cursor.md, events.md, keyboard.md, window-api.md, fullscreen-monitor.md**

For each feature doc:
- Change `⚠️` to `✅` for newly implemented features
- Change `❌`/`🔶` to `✅ NO-OP` for intentionally unsupported features with KDoc
- Update the platform matrices to reflect new implementations

- [ ] **Step 4: Write CHANGELOG entry**

In `CHANGELOG.md`, add under `## [Unreleased]` → rename to `## [1.2.0] — 2026-06-26`:

```markdown
## [1.2.0] — 2026-06-26

### Added

- **100% winit API parity** — all 24 Window APIs, 20 keyboard capabilities, and 22 event variants are now present on all 7 backends (REAL or documented NO-OP).
- **Keyboard text support on X11 and Wayland** via XLookupString and xkb_state_key_get_utf8.
- **Web DOM bridges** — Pointer Lock API, CSS pointer-events for cursor grab/hit-test.
- **Mobile NO-OP APIs** — all cursor, window-state, theme, and appearance methods implemented as documented NO-OPs on UIKit and Android with unit tests.
- **Event emission** — ThemeChanged on Web/Android/UIKit, Occluded on Win32/Wayland/Android, DnD finalized on AppKit, gestures on Android.
- **Window API finalization** — ownedDisplayHandle non-null, dragWindow/dragResizeWindow/showWindowMenu on all desktop backends, _NET_WM_WINDOW_OPACITY on X11.
- **KeyCode/NamedKey enum completeness** — added ~50 missing key constants (IME/Asian keys, media keys, browser keys, TV/media advanced keys).
- **ModifierKeys left/right tracking** — completed on all backends.

### Changed

- `docs/features/gaps.md`: rewritten to show 100% coverage across all categories.
- All `docs/features/*.md`: updated platform matrices and status badges.
```

- [ ] **Step 5: Commit**

```bash
git add docs/ CHANGELOG.md
git commit -m "docs: update feature docs and CHANGELOG for v1.2.0 100% winit parity"
```

---

## Execution Order

```
1. VAGUE 1 (parallel): Task 1 (C1) + Task 2 (C2)
   Gate: merge both, verify compilation: ./gradlew :kadre-core:compileKotlinJvm :kadre-web-common:compileKotlinJs
   
2. VAGUE 2 (parallel): Task 3 (C3) + Task 4 (C4) + Task 5 (C5) + Task 6 (C6)
   Gate: merge all 4, run: ./gradlew :kadre-core:jvmTest :kadre:jvmTest :kadre-appkit:jvmTest
   
3. VAGUE 3 (parallel): Task 7 (C7) + Task 8 (C8)
   Gate: merge both, final check: ./gradlew build && git tag v1.2.0
```

## Verification Checklist

- [ ] `./gradlew updateKotlinAbi` — no diff
- [ ] `./gradlew :kadre-core:jvmTest :kadre-appkit:jvmTest :kadre:jvmTest` — all PASS
- [ ] `./gradlew :kadre:iosSimulatorArm64Test` — all PASS (macOS only)
- [ ] `./gradlew :kadre-android:testDebugUnitTest` — all PASS
- [ ] `docs/features/gaps.md` shows 0 real gaps
- [ ] `CHANGELOG.md` has `[1.2.0]` entry
- [ ] `git tag v1.2.0` created
