# Koreos — Spécifications techniques v0.2

> Statut : **Draft pour relecture**
> Document de référence pour l'implémentation des Sprints 0 → 5 décrits dans [plan-v0.2](./plan-v0.2.md).
> Document précédent : [specs v0.1](./specs.md) — référence pour macOS, iOS, Android (déjà livré).

Ce document **complète** specs v0.1 — il ne remplace pas. Les sections inchangées (§3 API publique, §4 modèle d'événements, §5 boucle d'événements, §7 threading model) sont valides telles quelles. Seules les nouveautés v0.2 sont décrites ci-après.

---

## 1. Architecture v0.2 — mise à jour modulaire

### 1.1 Diagramme des modules étendu

```mermaid
graph TD
    Sample[samples/pong<br/>commonMain]
    Facade[koreos<br/>facade KMP]
    Core[koreos-core<br/>commonMain]

    AppKit[koreos-appkit<br/>JVM 25 + FFM]
    UIKit[koreos-uikit<br/>K/N cinterop]
    Android[koreos-android<br/>Android SDK]

    WebCommon[koreos-web-common<br/>JS+Wasm shared]
    Js[koreos-js<br/>Kotlin/JS Canvas]
    Wasm[koreos-wasm<br/>Kotlin/Wasm]

    Win32[koreos-win32<br/>JVM 25 + FFM]
    X11[koreos-x11<br/>JVM 25 + FFM]
    Wayland[koreos-wayland<br/>JVM 25 + FFM]

    Sample --> Facade
    Facade --> Core

    Facade -.jvmMain.-> AppKit
    Facade -.iosMain.-> UIKit
    Facade -.androidMain.-> Android
    Facade -.jsMain.-> Js
    Facade -.wasmJsMain.-> Wasm
    Facade -.jvmMain-win.-> Win32
    Facade -.jvmMain-linux.-> X11
    Facade -.jvmMain-linux.-> Wayland

    Js --> WebCommon
    Wasm --> WebCommon
    WebCommon --> Core

    AppKit --> Core
    UIKit --> Core
    Android --> Core
    Win32 --> Core
    X11 --> Core
    Wayland --> Core

    style Core fill:#e1f5ff
    style Facade fill:#fff3e0
    style WebCommon fill:#fffacd
    style Js fill:#fffacd
    style Wasm fill:#fffacd
    style Win32 fill:#d4f1f9
    style X11 fill:#e0ffe0
    style Wayland fill:#e0ffe0
```

### 1.2 Stratégies de binding v0.2

| Module | Cibles KMP | Binding | Lib native ? |
|--------|------------|---------|--------------|
| `koreos-web-common` | jsMain, wasmJsMain | — (Kotlin pur) | non |
| `koreos-js` | jsMain (browser) | JS DOM via `kotlin-wrappers-browser` ou similaire | non |
| `koreos-wasm` | wasmJsMain (browser) | JS interop Wasm vers DOM | non |
| `koreos-win32` | jvm (Windows-specific) | kextract FFM Win32 (User32, Gdi32, Kernel32) | non |
| `koreos-x11` | jvm (Linux-specific) | kextract FFM Xlib + XInput2 | non |
| `koreos-wayland` | jvm (Linux-specific) | kextract FFM libwayland-client + xdg_shell | non |
| `koreos` (facade) | toutes (6 plateformes) | expect/actual | non |

**Découplage Linux** : `koreos-x11` et `koreos-wayland` sont deux **modules séparés**, comme `koreos-appkit` et `koreos-uikit`. La facade contient une **logique de sélection runtime** dans le sourceSet `linuxMain` qui choisit le backend au démarrage.

---

## 2. API publique — ajouts v0.2

### 2.1 Nouveaux variants `RawWindowHandle`

```kotlin
sealed interface RawWindowHandle {
    // Existants v0.1
    data class AppKit(val nsView: Long, val nsWindow: Long, val nsLayer: Long) : RawWindowHandle
    data class UiKit(val uiView: Long, val uiViewController: Long?) : RawWindowHandle
    data class Android(val surface: Any) : RawWindowHandle

    // Nouveaux v0.2
    /** Web : id du canvas HTML auquel attacher la wgpu.Surface. */
    data class Web(val canvasElementId: String) : RawWindowHandle

    /** Windows : HWND + HINSTANCE en Long. */
    data class Win32(val hwnd: Long, val hinstance: Long) : RawWindowHandle

    /** Linux X11 : Window handle (XID) + Display pointer. */
    data class Xlib(val window: Long, val display: Long) : RawWindowHandle

    /** Linux Wayland : wl_surface + wl_display pointers. */
    data class Wayland(val surface: Long, val display: Long) : RawWindowHandle
}
```

### 2.2 Nouveaux variants `RawDisplayHandle`

```kotlin
sealed interface RawDisplayHandle {
    // Existants
    object AppKit : RawDisplayHandle
    object UiKit : RawDisplayHandle
    object Android : RawDisplayHandle

    // Nouveaux
    object Web : RawDisplayHandle
    data class Win32(val hinstance: Long) : RawDisplayHandle
    data class Xlib(val display: Long) : RawDisplayHandle
    data class Wayland(val display: Long) : RawDisplayHandle
}
```

### 2.3 Rétro-compatibilité

- **Aucune** signature d'interface existante n'est modifiée.
- Seules des sealed interface **variants** sont ajoutés (extension safe pour les consommateurs qui font `when` exhaustif → ils devront recompiler mais leur code restera valide après ajout des branches manquantes).
- Le tag `v0.1.x` reste compatible source avec `v0.2.x` sauf pour les consommateurs qui font un `when` exhaustif sur `RawWindowHandle` (cas attendu : renderer wgpu4k).

---

## 3. Considérations spécifiques par nouvelle plateforme

### 3.1 Web (koreos-js + koreos-wasm + koreos-web-common)

#### 3.1.1 Architecture commune

`koreos-web-common` héberge :
- Mapping `DOMEvent → WindowEvent` (PointerEvent, KeyboardEvent, etc.)
- Lifecycle DOM (`visibilitychange`, `pagehide`/`pageshow`)
- Gestion du `<canvas>` HTML (resize via ResizeObserver, devicePixelRatio)
- Interface `WebDomBridge` abstraite (actual JS / actual Wasm)

`koreos-js` et `koreos-wasm` n'implémentent que la **bridge** DOM (interop JS spécifique).

#### 3.1.2 Boucle d'événements Web

Il n'y a pas de "main thread" sur Web — le JS runtime est mono-thread par défaut. La boucle d'événements est :

```mermaid
sequenceDiagram
    actor User
    participant DOM as DOM (browser)
    participant EL as KoreosEventLoop (web)
    participant Handler as ApplicationHandler

    User->>EL: EventLoop().runApp(handler)
    EL->>DOM: addEventListener('pointerdown', ...)
    EL->>DOM: addEventListener('resize', ...)
    EL->>DOM: addEventListener('visibilitychange', ...)
    EL->>Handler: canCreateSurfaces() (immédiat)
    Handler->>EL: createWindow(attrs) → WebWindow

    Note over DOM: User clicks canvas
    DOM->>EL: pointerdown event
    EL->>Handler: deviceEvent(Button)
    EL->>Handler: windowEvent(PointerMoved, MouseInput)

    Note over EL: requestAnimationFrame loop
    EL->>EL: requestAnimationFrame(tick)
    EL->>Handler: aboutToWait()
    Handler->>EL: window.requestRedraw()
    EL->>Handler: windowEvent(RedrawRequested)
```

**`runApp()` ne bloque pas sur Web** : il enregistre les listeners DOM + démarre le `requestAnimationFrame` loop, puis retourne. La page reste vivante via le `requestAnimationFrame` loop.

**Implication** : sur Web, `runApp` n'a pas la même sémantique que sur Desktop. Documenter explicitement.

#### 3.1.3 ControlFlow sur Web

- `Wait` : seulement les events DOM réveillent (pas de polling continu)
- `Poll` : `requestAnimationFrame` continu (60Hz cap navigateur)
- `WaitUntil` : `setTimeout` avec deadline → puis `requestAnimationFrame`

`EventLoopProxy.wakeUp()` côté Web : poste un event custom dans la queue via `setTimeout(0)` ou `queueMicrotask`. Coalescing via flag.

#### 3.1.4 Mapping events Web

| DOM event | Koreos event |
|-----------|--------------|
| `pointerdown`/`pointerup` | `WindowEvent.MouseInput` (mouse) OU `WindowEvent.Touch` (touch) selon `pointerType` |
| `pointermove` | `WindowEvent.PointerMoved` |
| `keydown`/`keyup` | `WindowEvent.KeyboardInput` (mapping `code` → `Key` enum) |
| `wheel` | `WindowEvent.MouseWheel` |
| `resize` (window) | `WindowEvent.Resized` (via ResizeObserver sur canvas) |
| `visibilitychange` | `suspended` (hidden) / `resumed` (visible) |
| `pagehide` | `suspended` |

#### 3.1.5 DPI (devicePixelRatio)

- `Window.scaleFactor()` → `window.devicePixelRatio` (typiquement 1.0, 2.0, 3.0)
- `Window.innerSize()` retourne **physical pixels** = `canvas.clientWidth × devicePixelRatio`
- Canvas attribut `width`/`height` doit être ajusté en physical pour éviter le blur
- Changement de zoom navigateur → `WindowEvent.ScaleFactorChanged`

#### 3.1.6 Sample `samples/hello-triangle-web`

- Page HTML statique avec `<canvas id="koreos-canvas">`
- Bundle Kotlin/JS ou Kotlin/Wasm chargé via `<script>`
- wgpu4k attache sa Surface au canvas via le `RawWindowHandle.Web("koreos-canvas")`
- Build : Gradle task `:samples:hello-triangle-web:browserDistribution` produit un dossier statique servable
- CI : upload du dossier sur GitHub Pages pour démos live

#### 3.1.7 Limitations Web

- Pas de multi-fenêtre (un canvas par page, multi-tabs = multi-instances de la lib)
- Pas de `setTitle()` direct (option : `document.title`)
- Pas de raw input mouse (la souverainté curseur appartient au navigateur)
- IME : si nécessaire post-v0.2, via `<input>` hidden overlay

---

### 3.2 Windows (koreos-win32)

#### 3.2.1 Stack

- kextract FFM JVM 25 sur `user32.dll`, `gdi32.dll`, `kernel32.dll`, `dwmapi.dll`
- Pattern Win32 standard : `RegisterClassExW` + `CreateWindowExW` + WndProc + message pump (`GetMessage`/`TranslateMessage`/`DispatchMessage`)
- Subclassing : pas applicable Win32 ; on instancie une `WNDCLASSEXW` custom avec notre WndProc

#### 3.2.2 Boucle d'événements

```mermaid
sequenceDiagram
    actor User
    participant EL as Win32EventLoop (JVM)
    participant WndProc as KoreosWndProc
    participant Handler as ApplicationHandler

    User->>EL: EventLoop().runApp(handler)
    EL->>EL: assertMainThread
    EL->>EL: RegisterClassExW (avec KoreosWndProc)
    EL->>Handler: canCreateSurfaces
    Handler->>EL: createWindow(attrs)
    EL->>EL: CreateWindowExW → HWND
    EL->>EL: ShowWindow + UpdateWindow

    loop GetMessage loop
        EL->>EL: GetMessage(msg) [blocking]
        EL->>EL: TranslateMessage(msg)
        EL->>EL: DispatchMessage(msg)
        EL->>WndProc: WM_PAINT / WM_SIZE / WM_KEYDOWN / WM_MOUSEMOVE / ...
        WndProc->>Handler: windowEvent(...)
    end

    Note over EL: Pre-wait
    EL->>Handler: aboutToWait
```

#### 3.2.3 Mapping messages

| Message Win32 | Koreos event |
|---------------|--------------|
| `WM_PAINT` | `WindowEvent.RedrawRequested` |
| `WM_SIZE` | `WindowEvent.Resized(PhysicalSize)` |
| `WM_DPICHANGED` | `WindowEvent.ScaleFactorChanged` |
| `WM_KEYDOWN`/`WM_KEYUP` | `WindowEvent.KeyboardInput` |
| `WM_LBUTTONDOWN`/`WM_LBUTTONUP` | `WindowEvent.MouseInput(Left)` |
| `WM_MOUSEMOVE` | `WindowEvent.PointerMoved` |
| `WM_MOUSEWHEEL` | `WindowEvent.MouseWheel` |
| `WM_DESTROY` | `WindowEvent.Destroyed` puis `eventLoop.exit()` candidat |
| `WM_CLOSE` | `WindowEvent.CloseRequested` |
| `WM_SETFOCUS`/`WM_KILLFOCUS` | `WindowEvent.Focused` |
| `WM_INPUT` (raw input) | `DeviceEvent.*` (post-v0.2 optionnel) |

#### 3.2.4 DPI awareness

- Manifest application : `dpiAwareness = PerMonitorV2` (via `SetProcessDpiAwarenessContext` au démarrage)
- `Window.scaleFactor()` → `GetDpiForWindow(hwnd) / 96.0`
- `WM_DPICHANGED` reconfigure layer + dispatch ScaleFactorChanged

#### 3.2.5 RawWindowHandle

```kotlin
fun rawWindowHandle(): RawWindowHandle = RawWindowHandle.Win32(
    hwnd = hwndValue,
    hinstance = hInstanceValue
)
```

#### 3.2.6 EventLoopProxy.wakeUp Windows

- Thread-safe : `PostMessage(hwnd, WM_USER_WAKEUP, 0, 0)` depuis tout thread
- Le message custom est interpreté dans WndProc comme un no-op qui réveille la queue
- Coalescing : flag atomic, on ignore les wakeups si une wakeup est déjà en queue

---

### 3.3 Linux X11 (koreos-x11)

#### 3.3.1 Stack

- kextract FFM Xlib + XInput2 (pour multi-touch et raw input si présent)
- Pattern : `XOpenDisplay` + `XCreateWindow` + `XSelectInput` + `XNextEvent` loop

#### 3.3.2 Boucle d'événements

`XNextEvent` est bloquant. `EventLoopProxy.wakeUp` utilise `XSendEvent` avec un atom custom `_KOREOS_WAKEUP`.

#### 3.3.3 Mapping events

| X11 event | Koreos event |
|-----------|--------------|
| `Expose` | `WindowEvent.RedrawRequested` |
| `ConfigureNotify` | `WindowEvent.Resized` + `Moved` selon delta |
| `KeyPress`/`KeyRelease` | `WindowEvent.KeyboardInput` (via XLookupString pour le mapping) |
| `ButtonPress`/`ButtonRelease` | `WindowEvent.MouseInput` |
| `MotionNotify` | `WindowEvent.PointerMoved` |
| `EnterNotify`/`LeaveNotify` | `WindowEvent.PointerEntered`/`PointerLeft` |
| `FocusIn`/`FocusOut` | `WindowEvent.Focused` |
| `ClientMessage` (WM_DELETE_WINDOW) | `WindowEvent.CloseRequested` |
| `DestroyNotify` | `WindowEvent.Destroyed` |

#### 3.3.4 RawWindowHandle

```kotlin
fun rawWindowHandle(): RawWindowHandle = RawWindowHandle.Xlib(
    window = windowXid,
    display = displayPointer
)
```

#### 3.3.5 DPI

X11 ne gère pas le DPI scaling au niveau protocole. Lecture du DPI :
- `Xft.dpi` resource via `XGetDefault` → fallback heuristique 96
- Sample n'expose qu'un seul `scaleFactor` global (pas de per-monitor)

---

### 3.4 Linux Wayland (koreos-wayland)

#### 3.4.1 Stack

- kextract FFM `libwayland-client`
- Protocoles : `wl_display`, `wl_registry`, `wl_compositor`, `wl_surface`, `xdg_shell` (xdg_wm_base + xdg_surface + xdg_toplevel), `xdg_decoration_unstable_v1`
- Bindings xdg via wayland-scanner (.xml → C → kextract → Kotlin)

#### 3.4.2 Boucle d'événements

Wayland est event-driven asynchrone : `wl_display_dispatch` (bloquant) consomme les events.

#### 3.4.3 Mapping events

| Wayland event | Koreos event |
|---------------|--------------|
| `xdg_surface.configure` | `WindowEvent.Resized` + ack configure |
| `xdg_toplevel.close` | `WindowEvent.CloseRequested` |
| `wl_pointer.motion` | `WindowEvent.PointerMoved` |
| `wl_pointer.button` | `WindowEvent.MouseInput` |
| `wl_pointer.axis` | `WindowEvent.MouseWheel` |
| `wl_keyboard.key` | `WindowEvent.KeyboardInput` (via libxkbcommon mapping) |
| `wl_keyboard.enter`/`leave` | `WindowEvent.Focused` |
| `wl_touch.down`/`up`/`motion` | `WindowEvent.Touch` |
| `wl_output.scale` | `WindowEvent.ScaleFactorChanged` (per-output scale) |

#### 3.4.4 RawWindowHandle

```kotlin
fun rawWindowHandle(): RawWindowHandle = RawWindowHandle.Wayland(
    surface = wlSurfacePointer,
    display = wlDisplayPointer
)
```

#### 3.4.5 Décorations

- `xdg_decoration_unstable_v1` pour demander des server-side decorations
- Si non supporté → client-side decorations minimales (titre bar simple) ou fallback "no decorations" + raccourci clavier pour close

---

### 3.5 Détection automatique X11 vs Wayland

Au démarrage de la facade sur cible Linux :

```kotlin
// koreos/jvmMain (cible linux)
actual class EventLoop {
    actual fun runApp(handler: ApplicationHandler) {
        val backend = detectBackend()
        backend.runApp(handler)
    }
}

private fun detectBackend(): EventLoop {
    val override = System.getenv("KOREOS_LINUX_BACKEND")
    if (override == "x11") return X11EventLoop()
    if (override == "wayland") return WaylandEventLoop()

    // Auto-détection
    val xdgSessionType = System.getenv("XDG_SESSION_TYPE")
    if (xdgSessionType == "wayland") return tryWayland() ?: X11EventLoop()
    if (xdgSessionType == "x11") return X11EventLoop()

    // Fallback : tenter Wayland (plus moderne), retomber X11
    return tryWayland() ?: X11EventLoop()
}
```

---

## 4. Architecture du sample Pong (Sprint 5)

### 4.1 Structure

```
samples/pong/
├── build.gradle.kts (KMP avec 6 targets)
├── src/
│   ├── commonMain/kotlin/.../
│   │   ├── PongGame.kt           # ApplicationHandler principal
│   │   ├── GameState.kt          # Data classes : Paddle, Ball, Score
│   │   ├── PongAi.kt             # IA simple
│   │   ├── PongRenderer.kt       # Rendu wgpu4k (quads + texte)
│   │   ├── InputAdapter.kt       # Mapping WindowEvent → action paddle (clavier / touch)
│   │   └── BitmapFont.kt         # Petit bitmap font hardcodé pour le score
│   ├── jvmMain/   (entry point Desktop : macOS, Windows, Linux)
│   ├── iosMain/   (entry point UIApplicationMain)
│   ├── androidMain/  (entry point Activity)
│   ├── jsMain/    (entry point JS : window load)
│   └── wasmJsMain/  (entry point Wasm)
```

### 4.2 PongGame en commonMain

```kotlin
class PongGame : ApplicationHandler {
    private var window: Window? = null
    private var renderer: PongRenderer? = null
    private var state = GameState.initial()
    private val ai = PongAi(reactionLagMs = 80)
    private val inputAdapter = InputAdapter()
    private var lastFrameTime = 0L

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        window = eventLoop.createWindow(WindowAttributes(title = "Koreos Pong"))
        renderer = PongRenderer(window!!.rawWindowHandle())
        eventLoop.setControlFlow(ControlFlow.Poll)
    }

    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
        when (event) {
            is WindowEvent.CloseRequested -> eventLoop.exit()
            is WindowEvent.RedrawRequested -> draw()
            is WindowEvent.Resized -> renderer?.resize(event.size)
            is WindowEvent.KeyboardInput -> inputAdapter.onKey(event)
            is WindowEvent.Touch -> inputAdapter.onTouch(event, window!!.innerSize())
            else -> {}
        }
    }

    override fun aboutToWait(eventLoop: ActiveEventLoop) {
        val now = currentTimeNanos()
        val dt = (now - lastFrameTime).coerceIn(0, 50_000_000) / 1e9  // sec, capé 50ms
        lastFrameTime = now
        state = state.tick(dt, inputAdapter.playerInput, ai.suggest(state, dt))
        window?.requestRedraw()
    }

    private fun draw() {
        renderer?.draw(state)
    }
}
```

### 4.3 IA simple

```kotlin
class PongAi(private val reactionLagMs: Long) {
    private var lastTargetY = 0.0
    private var lastUpdate = 0L

    fun suggest(state: GameState, dt: Double): PaddleInput {
        val now = currentTimeNanos()
        if ((now - lastUpdate) / 1_000_000 > reactionLagMs) {
            lastTargetY = state.ball.y
            lastUpdate = now
        }
        val paddle = state.aiPaddle
        return when {
            paddle.y < lastTargetY - 0.05 -> PaddleInput.Down
            paddle.y > lastTargetY + 0.05 -> PaddleInput.Up
            else -> PaddleInput.None
        }
    }
}
```

### 4.4 Mapping input cross-platform

```kotlin
class InputAdapter {
    var playerInput = PaddleInput.None
        private set

    fun onKey(event: WindowEvent.KeyboardInput) {
        playerInput = when (event.key to event.state) {
            Key.ArrowUp to KeyState.Pressed -> PaddleInput.Up
            Key.ArrowDown to KeyState.Pressed -> PaddleInput.Down
            else -> if (event.state == KeyState.Released) PaddleInput.None else playerInput
        }
    }

    fun onTouch(event: WindowEvent.Touch, screenSize: PhysicalSize<Int>) {
        // Zone droite de l'écran : touch en haut = up, en bas = down
        val rightZone = event.location.x > screenSize.width / 2.0
        if (!rightZone) return
        playerInput = when (event.phase) {
            TouchPhase.Started, TouchPhase.Moved -> {
                if (event.location.y < screenSize.height / 2.0) PaddleInput.Up
                else PaddleInput.Down
            }
            TouchPhase.Ended, TouchPhase.Cancelled -> PaddleInput.None
        }
    }
}
```

### 4.5 Rendu wgpu4k

- Pipeline 2D simple : 1 vertex shader (transform position), 1 fragment shader (couleur uniforme)
- 5 draw calls par frame :
  - 2 quads pour les raquettes (couleur blanche)
  - 1 quad pour la balle (couleur blanche)
  - N quads pour les chiffres du score (bitmap font, blocs blancs)
  - 1 quad pour la ligne pointillée du milieu (option)
- Clear color noir
- Présentation à `surface.present()`

### 4.6 Frame timing

- `ControlFlow.Poll` → `aboutToWait` à chaque tick
- `dt` calculé en commonMain via `currentTimeNanos()` (expect/actual : `System.nanoTime` JVM, `performance.now()` Web, `mach_absolute_time` Apple, `clock_gettime` Linux/Android)
- Capé à 50ms pour éviter les sauts énormes au resume

### 4.7 Considérations par plateforme

| Plateforme | Spécifique |
|------------|-----------|
| Desktop (macOS/Windows/Linux) | Flèches ↑↓. Window 800×600. |
| Mobile (iOS/Android) | Touch zone droite. Window plein écran. |
| Web | Flèches ↑↓ (clavier) **+** touch zone droite (tactile). Canvas plein conteneur. |

---

## 5. Stratégie CI v0.2

### 5.1 Jobs ajoutés

| Job | Runner | Tâches |
|-----|--------|--------|
| `web-build` | `ubuntu-latest` + Node | `:koreos-js:build`, `:koreos-wasm:build`, `:samples:hello-triangle-web:browserProductionWebpack` |
| `windows-build` | `windows-latest` | `:koreos-win32:build`, `:samples:hello-triangle:run` (smoke test) |
| `linux-x11-build` | `ubuntu-latest` + Xvfb | `:koreos-x11:build`, `:samples:hello-triangle:build` |
| `linux-wayland-build` | `ubuntu-latest` + weston headless | `:koreos-wayland:build`, sample smoke |

### 5.2 Workflow conditionnel

- **Fast-Track JVM** (branches secondaires) : `:koreos-core:jvmTest` uniquement, < 10s.
- **Deep-Testing** (PR vers master) : tous les jobs ci-dessus.
- **Release** (tag `v*`) : Deep-Testing + Maven Central publish.

---

## 6. Roadmap d'implémentation v0.2 (résumé)

```mermaid
gantt
    title Koreos v0.2 — Roadmap par sprint
    dateFormat YYYY-MM-DD
    section Rémédiation
    v0.1.1 :s0, 2026-05-29, 14d
    section Web
    koreos-js MVP :s1, after s0, 14d
    koreos-wasm + samples web :s2, after s1, 14d
    section Windows
    koreos-win32 :s3, after s2, 14d
    section Linux
    koreos-x11 + wayland :s4, after s3, 21d
    section Pong
    samples/pong + v0.2.0 :s5, after s4, 14d
```

---

## 7. Limitations connues v0.2

- Pas de multi-fenêtre Web (un canvas par instance lib)
- Pas de multi-touch X11 sans XInput2 — à activer si présent
- Wayland nécessite `xdg_shell` v3+ (compositors >=2020)
- Pas de gamepad input (post-v0.2)
- Pas d'IME / composition (post-v0.2)
- Pong : pas d'audio, pas de réseau, IA basique

---

## 8. Annexes

### Mapping winit → Koreos v0.2

| winit (Rust) | Koreos v0.2 |
|--------------|------------------|
| `RawWindowHandle::Web` | `RawWindowHandle.Web(canvasElementId: String)` |
| `RawWindowHandle::Win32` | `RawWindowHandle.Win32(hwnd, hinstance)` |
| `RawWindowHandle::Xlib` | `RawWindowHandle.Xlib(window, display)` |
| `RawWindowHandle::Wayland` | `RawWindowHandle.Wayland(surface, display)` |
| `winit-web` crate | `koreos-js` + `koreos-wasm` + `koreos-web-common` |
| `winit-win32` crate | `koreos-win32` |
| `winit-x11` crate | `koreos-x11` |
| `winit-wayland` crate | `koreos-wayland` |

### Références externes additionnelles

- [WebGPU spec](https://www.w3.org/TR/webgpu/)
- [Kotlin/Wasm browser interop](https://kotlinlang.org/docs/wasm-overview.html)
- [Win32 API — Window classes](https://learn.microsoft.com/en-us/windows/win32/winmsg/window-classes)
- [Xlib programming manual](https://www.x.org/releases/X11R7.7/doc/libX11/libX11/libX11.html)
- [Wayland protocol](https://wayland.app/protocols/wayland)
- [xdg-shell unstable](https://wayland.app/protocols/xdg-shell)
- [libxkbcommon (Linux keymap)](https://xkbcommon.org/)

### Documents associés

- [Plan projet v0.2](./plan-v0.2.md)
- [Plan v0.1 (livré)](./plan.md)
- [Specs v0.1 (livrées)](./specs.md)
