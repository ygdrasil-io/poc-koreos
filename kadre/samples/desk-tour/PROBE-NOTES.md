# Probe notes — Compose-wasm in a host container, coexisting with `attachKadre`

**Date:** 2026-10-10 · **Task:** demo-desk-tour-web plan, Task 1 (probe).
**Method:** a throwaway gradle module (`kadre/samples/desk-tour-probe/`, wired temporarily in `settings.gradle.kts`, removed before this note was committed) built with the plan's toolchain and driven headless with Playwright (chromium, `playwright-core` + the machine's Playwright browser cache) against `wasmJsBrowserDistribution` output served by `python3 -m http.server`. Every trace of the module is gone; this note is the only artifact.

**Toolchain verified (not assumed):** Kotlin 2.4.20, Compose Multiplatform plugin 1.11.0 → `org.jetbrains.compose.*:1.11.0` web artifacts → `androidx.compose.*:1.11.1` internals + `org.jetbrains.skiko:skiko-wasm-js:0.144.6`. The API facts below were read from the `ui-1.11.1` sources jar actually resolved by this build and confirmed by compiling and running against it.

## Q1 — The viewport API: WORKS

The exact API that compiles and runs (`androidx.compose.ui.window`, `@OptIn(ExperimentalComposeUiApi::class)`):

```kotlin
fun ComposeViewport(
    viewportContainer: Element,                       // org.w3c.dom.Element — the host container div
    configure: ComposeViewportConfiguration.() -> Unit = {},
    content: @Composable () -> Unit = { },
)   // top-level function, returns Unit — there is NO handle and NO public dispose
```

An id-based overload exists (`ComposeViewport(viewportContainerId: String? = null, …)`) — the Element overload is the one the demo needs. `ComposeViewportConfiguration` offers `isA11YEnabled` (default `true`) and `isClearFocusOnMouseDownEnabled`.

Verified behavior:
- The canvas appears as a child of the plain host div and renders Material3 content. **DOM structure created inside the container:** `container → div(position:relative) → [ div(position:relative, carries an OPEN shadow root: style + div → canvas[tabindex=0][role=generic] + a11y mirror div), div(interop container) ]`.
- **`ComposeViewport` calls `viewportContainer.clear()` on creation** — it wipes the container's children (not its attributes/listeners). Host content must live OUTSIDE the container or be installed after boot.
- The canvas is NOT a light-DOM child: `container.querySelector('canvas')` finds nothing; automation must pierce the open shadow root (`positioning.firstElementChild.shadowRoot.querySelector('canvas')`).
- Boot is asynchronous (`onDomReady` + `onSkikoReady` internally); zero `pageerror` at boot and at rest (the only console noise: one benign `favicon.ico` 404 and a headless WebGL `WEBGL_debug_renderer_info` warning).

## Q2 — `attachKadre` on the same container: WORKS

`HTMLElement.attachKadre(parentScope, applicationFactory, policy, attachmentPolicy, windowProvider)` (wasmJs twin of `WebDriverFixture.kt`'s pattern) on the SAME element the viewport was booted into:
- (a) Session attaches (`success`, `InitialHostAttachment`), and **metrics follow the container**: the kadre surface reported exactly the container's client box (`640x400@1.0`, revision bumping on resize — the port uses a ResizeObserver on the container).
- (b) Compose keeps rendering; no interference either way.
- (c) **The input crux: clicks land in BOTH stacks.** A Playwright click at canvas coordinates incremented the Compose state (visible via a composition side-effect attribute) AND arrived in kadre's `surface.input.events` with the exact container-relative position (`320,66` for a click 68px into a 2px-bordered box). Mechanism, named: Compose listens ON the canvas it created (pointer/wheel/key listeners on the canvas element); kadre listens ON the container; the events hit the canvas first and **bubble up** to the container. Neither stack calls `stopPropagation`; default-suppression is opt-in on both sides (kadre's `InputDefaultBehavior.HostDefault`, Compose only prevents defaults for keys it consumes, contextmenu, and focused text-input touch). Keys pass the same way after the canvas takes focus (it has `tabindex=0`).
- (d) Resize flows, with **one caveat**: kadre follows a container-only resize (ResizeObserver). Compose does NOT — it re-reads the container box only on WINDOW `resize` events (plus dpr media-query changes). A layout-driven container resize (flex change, JS style change, note element appearing/disappearing around it) leaves the Compose canvas at its stale size until the window itself resizes. Consequence for the demo: size the containers at boot and on window resize, or drive container resizes through window resizes.
- **Attach order is free:** kadre-before-Compose works identically — the container's `clear()` removes children, not listeners on the container itself; the ResizeObserver and listeners survive Compose boot.

## Q3 — Multiple viewports + disposal: WORKS-WITH-CAVEAT (disposal)

- Main container + a dynamically created note div, each with its own `ComposeViewport`, render simultaneously and independently (verified visually and by per-viewport canvas facts). Zero pageerrors.
- Removing the note element (`element.remove()`) is observationally clean: no console errors, no pageerrors, the main viewport keeps receiving input, and a later WINDOW resize (which wakes the dead viewport's listener with a detached container) also errors nothing.
- **The caveat is structural: there is NO public dispose API for a `ComposeViewport`.** The function returns `Unit`; `ComposeWindow` and its `dispose()` are `internal` (the sources carry an explicit `// TODO: need to call .dispose() on window close.`). Removing the element hides the viewport but leaks the composition scene, the Skia layer and the window-level resize listener for the page's lifetime. Honest reading: notes close = element removal (idempotent), with a bounded, invisible per-note residue — acceptable for a demo, documented as a limit.

## Q4 — Minimal servable output: WORKS

`./gradlew :kadre:samples:desk-tour-probe:wasmJsBrowserDistribution` → `build/dist/wasmJs/productionExecutable/`:

| file | size |
|---|---|
| `index.html` (from `src/wasmJsMain/resources`) | ~1 KB |
| `<module>.js` (webpack loader, self-hosts the wasm loading) | ~540 KB |
| `<hash>.wasm` (Kotlin/Wasm binary) | ~8.6 MB |
| `<hash>.wasm` (skiko) | ~1.8 MB |
| + `.LICENSE.txt`, `.js.map` | |

Served by `python3 -m http.server` with zero dev-server magic; no `skiko.js` script tag, no CORS headers, no COOP/COEP needed (WebGL path). The only console 404 is the browser's own `favicon.ico` request.

## Corrections to the plan's pinned decisions

1. **Decision 1 (attach target = the host container) — CONFIRMED, mechanism corrected.** The container is the right attach target and everything coexists. But the plan's wording "input events reach the canvas through the container's listeners (bubbling)" has the direction backwards: the events land on the **canvas** (Compose's listeners, the event target) and reach **kadre** by bubbling UP to the container's listeners. Compose creates the canvas inside an open shadow root under the container — so `platformAccess`/`withWebElement` yields the container which is genuinely NOT a canvas, and the honest `surface-not-a-canvas` capture state holds by construction (static reading; not re-driven in the probe).
2. **Decision 2 (notes = provider-served same-document elements) — REFUTED, one half replaced.** A `windowProvider` whose `open` offers a div of the SAME document is refused by the kadre validation ladder: `requestWindow` terminates with `Rejected(InvalidRequest("element.ownerDocument"))` (the probe reproduced it end-to-end; the ladder requires a distinct browsing context — "the contract's whole point"). The provider OPTION itself arms fine (capability `supported[OpenedInNewSession]`, session stays healthy). Consequence for Task 3: the notes CANNOT be kadre child sessions on same-document divs. The demo must own its notes: webMain creates the note div, boots its own `ComposeViewport(noteElement)`, and close = idempotent element removal (see the Q3 disposal caveat). The gateway's `createNote` flow (decision 3's `scope.windows.requestWindow` on web) cannot ride kadre's window manager and needs a web-specific note host in the demo's own code.
3. **Decision 5 (mirror the fixture's attach pattern) — CONFIRMED.** `attachKadre(parentScope, applicationFactory = …, windowProvider = …)` is the working Kotlin shape (wasmJs overloads in `WasmWebAttach.kt`; `WebWindowHost(element, scope)` is the offer type). Only its provider half is narrowed by correction 2.
