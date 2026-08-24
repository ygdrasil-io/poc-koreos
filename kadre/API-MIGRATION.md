# New Kadre — Registre de migration de l’API publique

**Baseline ABI :** commit `497a2c2e812c7d8234eb3ebbccfb3fe91058a528`

**Portée :** tous les dumps sous `kadre*/api/`, toutes les déclarations publiques des source sets publiés et leurs membres publics

Ce registre rend explicite le devenir de l’API actuelle. Il ne constitue pas une promesse de compatibilité : une décision `replace` autorise un changement total de nom, de package, de forme et de sémantique. Les contrats cibles restent normatifs dans `DESIGN.md`. Ce registre couvre l’ABI source ; le catalogue documentaire fermé exigé par `DESIGN.md` couvre séparément et exhaustivement l’API cible avant toute génération de code. `MIGRATION-AUDIT.md` enregistre la revue humaine nominative de la baseline et ses comptes reproductibles.

## Règles de couverture

Les règles sont évaluées dans l’ordre, de la plus spécifique à la plus générale. Une ligne couvre le symbole nommé, ses membres et ses déclarations imbriquées sauf exception explicite. À partir du chantier 1, une tâche génère l’inventaire des symboles depuis les dumps ABI et échoue si un symbole public :

1. ne correspond à aucune règle ;
2. correspond à plusieurs règles incompatibles ;
3. reste exposé depuis un package déclaré interne ;
4. est supprimé avant que ses consumers internes aient migré.

Le rapport conserve la liste nominative des symboles classés par une règle résiduelle. Une couverture algorithmique par le catch-all ne vaut pas approbation humaine : avant le chantier 11, chaque match résiduel reçoit une règle spécifique ou une validation explicite enregistrée dans le rapport.

Décisions :

- `keep/move` : concept conservé, déplacé vers le package public cible ;
- `replace` : concept utile, API entièrement redessinée ;
- `internalize` : nécessaire au backend, absent de l’API consommateur ;
- `remove` : concept hérité qui ne fait plus partie de Kadre.

## Application et event loop

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `kadre.EventLoop`, `kadre.core.EventLoop` | remove | `KadreHost.attach`, adaptateurs embeddable et `runKadreApplication` desktop |
| `ApplicationHandler` | replace | `KadreApplication`, `KadreApplicationFactory`, `KadreScope`, admission d’enfants fermée au retour de `run`, parent scope actif obligatoire et terminaison logique bornée dès que le runtime reste schedulable |
| `ActiveEventLoop`, `EventLoopProxy` | remove | managers attachés à la session ; aucun proxy de boucle public |
| `ControlFlow`, `StartCause` | remove | scheduling interne et primitives coroutine |
| `kadre.coroutines.EventLoopDispatcher` | internalize | dispatcher/pump du backend |
| `kadre.coroutines.KadreAppScope`, `KadreWindow`, `kadreApplication` | replace | API application et surface commune dans l’artifact principal |
| `FrameTimingTracer` | internalize | instrumentation interne ; Kadre n’impose pas une boucle de rendu |
| `ApplicationHandler.memoryWarning` | replace | `KadreLifecycle.capabilities`, `KadreLifecycle.signals` et `HostSignal.MemoryPressure` |

## Géométrie, temps et identités

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `PhysicalPosition`, `PhysicalSize`, `LogicalPosition`, `LogicalSize`, `Insets` et helpers DPI | keep/move | value types publics dans `surface`/`display`, unités jamais implicites |
| overloads `toLogical`/`toPhysical` | replace | quatre conversions monomorphes, scale explicite, rounding fermé et arithmétique vérifiée |
| `WindowId`, `DisplayId`, `DeviceId`, `FingerId`, `GamepadId` | replace | IDs opaques, constructeurs internes, validité limitée à la session |
| timestamps ou IDs natifs nus | replace | `EventStamp`, `SessionInstant`, `SessionSequence` et IDs opaques |
| listes, maps et `ByteArray` intégrées à des snapshots | replace | value objects et collections immuables, limites explicites de taille/payload, aucune troncature sémantique et copies transférées au consumer ; une collection de handles conserve explicitement des ressources vivantes |
| `MAX_CURSOR_SIZE` | remove | aucune constante globale ; `SurfaceCapabilities.customCursor` fournit la contrainte effective |

## Surface, fenêtre, display et apparence

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `Window` et tous ses getters/setters | replace | `HostSurface`, `Window`, `WindowManagerState` atomique, snapshots `StateFlow`, `Window.apply` et opérations dédiées |
| `WindowAttributes`, `AppKitWindowAttributes`, `AndroidWindowAttributes`, `UiKitWindowAttributes` | replace | `WindowSpec` immuable, DSL et options plateforme typées |
| `WindowRequestResult`, `SurfaceSizeRequestResult`, `RequestError` | replace | `KadreResult`, `WindowRequestOutcome`, `WindowUpdateOutcome`, `WindowCloseOutcome` et réponses close typées |
| `MonitorHandle`, `VideoMode` | replace | `DisplayManagerState` atomique, `DisplayInventory` complet ou terminalement indisponible, `DisplayState` terminal avant retrait et nouvel ID après réapparition |
| `Fullscreen` | replace | modes et contraintes dans `WindowSpec`/`WindowUpdate` et `WindowCapabilities` |
| `Theme`, `WindowLevel`, `WindowButtons` | keep/move | valeurs de `WindowState`, `WindowSpec` ou `WindowUpdate` avec capability typée |
| `ActiveEventLoop.systemTheme` | replace | theme observable de `SurfaceState`; aucune préférence globale fictive pour une session headless |
| `CursorIcon`, `CursorGrabMode`, `CursorImage`, `CustomCursor` | replace | `CursorStyle` et `CursorImage` copiée/owned par la surface ; aucun handle custom cursor public |
| `Icon` | replace | valeur immuable avec copie défensive dans `WindowSpec`/`WindowUpdate` |
| `UserAttentionType`, `ResizeDirection` | keep/move | `Window.requestAttention` et actions dédiées ; token d’interaction lorsque le host l’exige |
| redraw, resize, move, scale, focus, theme, occlusion et close requested de `WindowEvent` | replace | `SurfaceState`/`SurfaceEvent` ou `WindowState`/`WindowEvent`, avec révision, stamp et protocole close accept/reject/forced |
| drag-and-drop de `WindowEvent` à base de chemins `String` | replace | offre/transfer attaché à la surface, état terminal, handoff single-winner avec claim timeout, mode replayable/single-use et lecture bornée ; aucun faux chemin portable ou accès implicite au filesystem |
| transparent, blur, decorations, hit-test, content protection, system menu, drag window/resize | replace | champs ou verbes dédiés avec capabilities et résultats typés ; aucun no-op silencieux |
| `RawWindowHandle`, `RawDisplayHandle`, `OwnedDisplayHandle` | replace | seul `Window.withDesktopHandle` survit comme callback borné ; aucun display handle ni owner brut public v1 |

Un `HTMLElement`, une `View` ou une vue UIKit matche toujours la ligne `HostSurface`, jamais la ligne `Window`. Les implémentations concrètes `AndroidWindow`, AppKit/Win32/X11/Wayland windows et UIKit views deviennent internes.

## Clavier, pointeur, tactile, gestes, raw input et IME

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `WindowEvent` input et `DeviceEvent` | replace | `SurfaceInput.events`, snapshots et `DeviceManagerState` atomique dont les inventaires sont complets ou explicitement indisponibles |
| `KeyEvent`, `RawKeyEvent`, `KeyState`, `KeyLocation`, `KeyboardModifiers`, `KeyboardModifierState`, `ModifierKeys`, `ModifierKeyState` | keep/move | value model portable sous `input`, enrichi d’un stamp au niveau événement |
| `KeyCode`, `NamedKey`, `LogicalKey`, `PhysicalKey`, `MouseButton`, `ButtonSource` | keep/move | types exhaustifs ou variantes `Unknown`; touche physique identifiée par usage page + usage ID HID, aucun fallback ordinal |
| `KeyChord`, `KeyChordModifierMatch` et helpers | remove | composition de raccourcis hors du noyau Kadre ; le consumer compose les événements clavier |
| `NativeKeyCode`, `NativeLogicalKey`, `NativeKeyInfo`, `KeyPlatform`, `PlatformEvent*` | internalize | détails backend sans vue native publique v1 ; l’API portable publie les variantes unknown typées |
| `PointerKind`, `PointerSource`, `TouchPhase`, `TouchForce`, `FingerId`, tablet tool types | keep/move | événements et snapshots de `SurfaceInput` |
| `TabletToolKind`, `TabletToolButton`, `TabletToolData` | replace | `PointerKind.Pen/Eraser`, `PointerButton.Barrel/Eraser` et `PenState` borné |
| pinch, pan, rotation, double tap et touchpad pressure | keep/move | input de surface, activation explicite lorsqu’un recognizer doit être installé |
| `ImePurpose`, `ImeCapability`, `ImeCapabilities`, anciens `WindowEvent.Ime*` | replace | `TextInputSession`, `TextInputState`, `TextInputEvent`, offsets UTF-16 et `TextDocumentRevision` anti-stale |
| `DeviceEvents` et `listenDeviceEvents` | replace | `RawInputAccess`, `DevicePolicy`, permissions et routing de session |
| `InputCapabilities` | replace | snapshot composé `SurfaceInputState.capabilities` et capabilities fermées |
| `defaultLogicalKey`, `defaultText`, `location` | internalize | helpers de mapping backend ; l’API publie directement touche physique, logique et location observées |

La perte de focus, la déconnexion ou la révocation remplace les releases synthétiques par un `SurfaceInputState` neutre suivi de `InputEvent.StateReset`. Un overflow publie directement le snapshot composé terminal et termine le flow avec une failure hors de la lane saturée, sans promettre un dernier événement impossible à admettre. Les APIs nécessitant un serial ou une user activation utilisent `InteractionContext`, pas un événement `Flow` livré trop tard.

Le flux input unifié conserve son ordre grâce à un scheduler borné : une lane FIFO discrète et des lanes continues ne coalescent jamais à travers une barrière discrète. La cancellation d’un simple waiter n’acquiert aucun ownership sur une requête, une interaction ou un effet.

## Gamepads

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `GamepadController`, `Gamepad`, `GamepadState`, `GamepadEvent`, `Axis`, `Button`, `PowerInfo` | replace | `DeviceManager`, snapshots immuables incluant routing `Routed`/`Suspended`, neutralisation explicite, événements estampillés, scheduler discret/continu et collections spécialisées |
| `PlatformGamepad`, `PlatformGamepadBackend`, `PlatformEventType` | internalize | broker natif process-wide et adapters |
| fonctions `fromOrdinal` et fallback vers un contrôle arbitraire | remove | valeur unknown/native explicite |
| rumble/effects retournant `Unit` | replace | `GamepadEffectSession` owned et outcome observable |

## Capture

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `ScreenCapturer` | replace | `CaptureManager` attaché à la session |
| `CapturePermission` | replace | `CaptureManagerState` atomique regroupant permission observable, capabilities, sources, indisponibilité typée et révision |
| `CaptureSource`, `DisplayInfo`, `WindowInfo`, `CaptureRegion`, `CaptureConfig` | replace | descriptor source lié à une révision d’inventaire, request typée, host picker, IDs opaques et `CaptureConfiguration` effective complète avec cadence fixe, variable ou inconnue |
| `CaptureSession` | replace | owner structuré, démarrage au premier `collectFrames`, `CaptureOutcome`, événements sous policy et cancellation des waiters non propriétaire |
| `CaptureFrame`, `PixelFormat` | replace | lease closeable, `PixelPlaneLayout` sans vue native, `CopiedPixelPlane` app-owned, configuration revision autosuffisante sans ordre inter-stream supposé, color encoding complet, horloge média distincte et budget buffer en octets |
| `CaptureError` | replace | `KadreFailure` et outcomes stables |
| `UIKitScreenCapturer`, `UIKitCaptureSession`, `AndroidScreenCapturer`, `AndroidCaptureSession` et équivalents backend | internalize | implémentations derrière `CaptureManager` |
| `AppKitScreenCapturer`, `AppKitCaptureSession`, `CGDisplayCaptureSession` | internalize | implémentations derrière `CaptureManager`, aucune classe backend concrète publique |
| `resolveScreenCapturer` | replace | lookup global supprimé ; `KadreScope.capture` est l’unique entrée |

## Adaptateurs et déclarations de plateforme

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `AndroidKadreRuntime` | remove | aucune session ou handler global |
| `KadreActivity` | replace | extensions `ComponentActivity.attachKadre` et `View.attachKadre` |
| `KadreAppDelegate`, `KadreRegistry` UIKit, `KadreMetalView` | replace/internalize | `KadreIos.attach`, host surface et bridge Swift minimal ; aucun renderer Metal public |
| `AppKitWindow`, delegates AppKit concrets, `KadreWindowDelegate`, `MainThreadCheck` et callbacks publics | internalize | `attachKadreDesktop` et options host typées |
| `ActivationPolicy` | remove | `Standalone` AppKit utilise `Regular`; un host ayant une autre activation policy utilise `Embedded` et la configure lui-même |
| `StatusBarStyle`, `ValidOrientations`, `setPreferredStatusBarStyle`, `setPrefersHomeIndicatorHidden`, `setPrefersStatusBarHidden` | remove | chrome et orientations UIKit possédés par le host controller, pas par une fenêtre Kadre |
| `contentRect` Android/UIKit | replace | `HostSurface.state` fournit les métriques atomiques et les conversions explicites |
| `config` Android | replace | lecture target-specific bornée via `withAndroidView`; aucun snapshot arbitraire de `Configuration` dans le commun |
| `androidApp` | remove | le host possède déjà la `ComponentActivity`; aucun accès process-global depuis une event loop |
| `startKadreApplication` | replace | `KadreIos.attach(windowScene, window, surfaceView, applicationFactory, policy)` |
| tous les packages générés `*.bindings.**`, symboles FFI et types de framework réexportés | remove | KFFI possède les bindings ; aucun binding, générateur ou input de génération n’entre dans `kadre` |
| implémentations backend concrètes non listées comme point d’attachement | internalize | seules les factories/extensions documentées restent publiques |

## Modules backend sans dump ABI complet

Ces règles couvrent également les déclarations Kotlin publiques visibles dans les source sets publiés, même lorsqu’aucun dump n’existe encore :

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `KadreJs`, `KadreWasm` | replace | extension `HTMLElement.attachKadre` Kotlin et façade `@kadre/host` |
| `JsWebEventLoop`, `WasmJsWebEventLoop`, `WebEventLoop`, `JsWebDomBridge`, `WasmJsWebDomBridge`, `WebDomBridge` | internalize | adapters et scheduling derrière le Host SPI |
| `JsEventTarget`, `JsDomEvent`, `JsKeyboardEvent`, `JsPointerEvent`, `JsWheelEvent`, `JsCompositionEvent`, `JsDomRect`, `JsMediaStream`, `JsMediaStreamTrack` | internalize | externals Wasm bruts du bridge DOM/capture ; aucun type JavaScript natif ne fuit dans l’API Kadre |
| `WebWindow`, `WebWindowAttributes` | replace | `HostSurface`, `WindowSpec` et provider de nouvelle session ; aucun faux top-level DOM |
| `PollStrategy`, `WaitUntilStrategy`, `setPollStrategy`, `setWaitUntilStrategy` | internalize | scheduling gouverné par la policy et le host |
| `canvas`, `setPreventDefault`, `createCustomCursorAsync` | replace | callbacks `withWebElement`, `SurfaceUpdate.inputDefaultBehavior` et `CursorStyle.Custom` copié |
| `WebKey`, `WebKeyLocation`, `WebKeyState`, `WebModifiers`, `WebMouseButton`, `WebTouchPhase`, `WebImeEvent`, `WebWindowEvent` | internalize | mapping vers le catalogue input commun |
| `WebScreenCapturer`, `WebCaptureBackend`, `JsCaptureBackend`, `WasmJsCaptureBackend`, `JsCaptureSession`, `WasmJsCaptureSession` | internalize | `CaptureManager` et `CaptureSession` communs |
| `Win32Window`, `WindowEventHandler`, `KadreWndProc`, `Win32ScreenCapturer`, `Win32CaptureSession`, `DxgiOutputDuplicator`, `Win32MonitorRect` | internalize | implémentations derrière les managers communs |
| `hideApplication`, `hideOtherApplications` | remove | commandes process-level AppKit hors du rôle fenêtre/périphérique de Kadre |
| `setHasShadow`, `setMovableByWindowBackground`, `setSimpleFullscreen`, `setTabbingIdentifier`, `setTitlebarTransparent` | internalize | convenience setters AppKit hors noyau v1 ; accès expert borné au handle natif |
| `SystemBackdrop`, `CornerPreference`, `Win32WindowAttributes`, `dwmSetWindowAttribute`, `setBorderColor`, `setCornerPreference`, `setEnabled`, `setSkipTaskbar`, `setSystemBackdrop`, `setTitleBackgroundColor`, `setTitleTextColor`, `setUndecoratedShadow` | internalize | hors noyau v1 ; accès expert via `withDesktopHandle` sans promesse de convenience API |
| `X11Window`, `X11EventLoop`, `X11EventLoopProxy`, `X11DrawMapper`, `X11KeyMapper`, `X11MouseMapper`, `readXftDpi`, `probeConnection`, `X11ScreenCapturer`, `X11CaptureSession` | internalize | implementation et probes backend |
| `WindowType`, `X11WindowAttributes`, `setWindowType`, `setOverrideRedirect` | internalize | hors noyau v1 ; accès expert borné au handle |
| `x11Window`, `isX11` | replace | `Window.withDesktopHandle` et `KadrePlatform` |
| `WaylandWindow`, `WaylandEventLoop`, `WaylandEventLoopProxy`, `mapWaylandKeyEvent`, `mapWaylandKeyboardFocused`, `mapWaylandPointerAxis`, `mapWaylandPointerButton`, `mapWaylandPointerMotion`, `mapWaylandTouchCancel`, `mapWaylandTouchDown`, `mapWaylandTouchMotion`, `mapWaylandTouchUp`, `waylandButtonStateToKeyState`, `waylandKeyStateToKeyState`, `linuxButtonToMouseButton`, `linuxKeycodeToKeyCode`, `linuxKeycodeToPhysicalKey`, `wlFixedToDouble`, `probeConnection`, `WaylandScreenCapturer`, `WaylandCaptureSession` | internalize | implementation, protocol mapping et probes backend |
| `WaylandWindowAttributes`, `setPreferCsd`, `setActivationToken`, `waylandProtocols`, `hasWaylandProtocol` | internalize | hors noyau v1 ; availability exprimée par capabilities, pas par inspection de protocole publique |
| `isWayland`, `xdgToplevel` | replace | `KadrePlatform` et `Window.withDesktopHandle` |
| toutes les fonctions backend `runApp` | replace | `attachKadreDesktop` ou `runKadreApplication` |

## Artifact `kadre-test` actuel

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `ScriptedEventLoop`, `ScriptedWindow`, `ScriptedEvent`, `Callback`, `ScriptBuilder`, `scriptedTest` | replace | `FakeKadreHost`, `VirtualKadreClock` et contrôleurs virtuels du catalogue cible |
| `ObservedCallback`, `RecordingApplicationHandler`, `EventLoopConformanceDriver`, `assertIterationOrder`, `assertNoCallbacksAfter`, `assertWakeUpRearms`, `assertRedrawAfterIdle`, `assertCloseIsTerminal` | replace | contract tests session/lifecycle/manager, sans event loop publique |
| `KeyboardEventFidelity`, `KeyboardValidationScope`, `KeyboardBackend`, `NativeKeyboardInput`, `WebDomKeyboardEventInput`, `Win32KeyboardMessageInput`, `NativeKeyboardInputAdapter`, `KeyboardBackendValidationPolicy`, `KeyboardScenario`, `KeyboardScenarioBuilder`, `KeyboardEventEvidence`, `KeyboardValidationResult`, `KeyboardProofStatus`, `KeyboardProofCoverageKind`, `KeyboardProofGatePolicy`, `KeyboardProofReport`, `KeyboardProofEntry`, `KeyboardProofGateResult`, `ExpectedKeyEvent`, `ExpectedKeyEventBuilder`, `proofEntry`, `nativeKeyboardScenario` | internalize | harness de validation des adapters ; ne fait pas partie de l’artifact consommateur v1 |

## Façades, typealiases et règle résiduelle

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| typealiases de `KadreApi.kt` et façades dupliquant `kadre-core` | remove | une seule surface normative dans `kadre` |
| artifact `kadre-coroutines` | remove | contrats fusionnés dans `kadre` |
| imports consommateurs `org.graphiks.kadre.core` | remove | `kadre-core` devient interne |
| toute déclaration actuelle `org.graphiks.kadre.core.**` non citée plus haut | internalize par défaut | elle ne reste publique que si une règle `keep/move` ou `replace` et un contrat cible explicite sont ajoutés avant migration |
| toute déclaration publique de backend non citée plus haut | internalize par défaut | une exception exige une entrée dans ce registre, un package cible et un consumer compile test |

Les deux règles résiduelles restent un filet mécanique pour détecter les oublis futurs, pas une décision suffisante. La revue humaine de la baseline enregistrée dans `MIGRATION-AUDIT.md` ne laisse aucun match résiduel non approuvé : les 22 propriétaires initialement trouvés ont désormais une règle spécifique et la famille générée `appkit.bindings.**` est explicitement internalisée. Toute nouvelle déclaration publique ajoutée après la baseline doit être enregistrée directement dans sa forme cible ; elle ne peut s’abriter derrière ces règles de cleanup.
