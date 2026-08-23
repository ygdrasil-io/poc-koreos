# New Kadre — Registre de migration de l’API publique

**Baseline ABI :** commit `497a2c2e812c7d8234eb3ebbccfb3fe91058a528`

**Portée :** tous les dumps sous `kadre*/api/`, toutes les déclarations publiques des source sets publiés et leurs membres publics

Ce registre rend explicite le devenir de l’API actuelle. Il ne constitue pas une promesse de compatibilité : une décision `replace` autorise un changement total de nom, de package, de forme et de sémantique. Les contrats cibles restent normatifs dans `DESIGN.md`. Ce registre couvre l’ABI source ; le catalogue documentaire fermé exigé par `DESIGN.md` couvre séparément et exhaustivement l’API cible avant toute génération de code.

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
| `kadre.coroutines.KadreAppScope`, `KadreWindow`, `kadreApplication` | replace | API application et surface commune dans l’artifact `kadre` |
| `FrameTimingTracer` | internalize | instrumentation interne ; Kadre n’impose pas une boucle de rendu |
| `ApplicationHandler.memoryWarning` | replace | `KadreLifecycle.signals` et `HostSignal.MemoryPressure` |

## Géométrie, temps et identités

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `PhysicalPosition`, `PhysicalSize`, `LogicalPosition`, `LogicalSize`, `Insets` et helpers DPI | keep/move | value types publics dans `surface`/`display`, unités jamais implicites |
| `WindowId`, `DeviceId`, `FingerId`, `GamepadId` | replace | IDs opaques, constructeurs internes, validité limitée à la session |
| timestamps ou IDs natifs nus | replace | `EventStamp`, `SessionInstant`, `SessionSequence` et IDs opaques |
| listes, maps et `ByteArray` intégrées à des snapshots | replace | value objects et collections immuables, limites explicites de taille/payload, aucune troncature sémantique et copies transférées au consumer ; une collection de handles conserve explicitement des ressources vivantes |

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
| `CursorIcon`, `CursorGrabMode`, `CursorImage`, `CustomCursor` | replace | état/capabilities de surface, ressource custom cursor owned et actions d’interaction lorsque requises |
| `Icon` | replace | valeur immuable avec copie défensive dans `WindowSpec`/`WindowUpdate` |
| `UserAttentionType`, `ResizeDirection` | keep/move | opérations dédiées de fenêtre ; token d’interaction lorsque le host l’exige |
| redraw, resize, move, scale, focus, theme, occlusion et close requested de `WindowEvent` | replace | `SurfaceState`/`SurfaceEvent` ou `WindowState`/`WindowEvent`, avec révision, stamp et protocole close accept/reject/forced |
| drag-and-drop de `WindowEvent` à base de chemins `String` | replace | offre/transfer attaché à la surface, état terminal, handoff single-winner avec claim timeout, mode replayable/single-use et lecture bornée ; aucun faux chemin portable ou accès implicite au filesystem |
| transparent, blur, decorations, hit-test, content protection, system menu, drag window/resize | replace | champs ou verbes dédiés avec capabilities et résultats typés ; aucun no-op silencieux |
| `RawWindowHandle`, `RawDisplayHandle`, `OwnedDisplayHandle` | replace | handles owner-scoped sous `@KadrePlatformApi` dans les source sets concernés |

Un `HTMLElement`, une `View` ou une vue UIKit matche toujours la ligne `HostSurface`, jamais la ligne `Window`. Les implémentations concrètes `AndroidWindow`, AppKit/Win32/X11/Wayland windows et UIKit views deviennent internes.

## Clavier, pointeur, tactile, gestes, raw input et IME

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `WindowEvent` input et `DeviceEvent` | replace | `SurfaceInput.events`, snapshots et `DeviceManagerState` atomique dont les inventaires sont complets ou explicitement indisponibles |
| `KeyEvent`, `RawKeyEvent`, `KeyState`, `KeyLocation`, `KeyboardModifiers`, `KeyboardModifierState`, `ModifierKeys`, `ModifierKeyState` | keep/move | value model portable sous `input`, enrichi d’un stamp au niveau événement |
| `KeyCode`, `NamedKey`, `LogicalKey`, `PhysicalKey`, `MouseButton`, `ButtonSource`, `KeyChord` et helpers | keep/move | types exhaustifs ou variantes `Unknown`; aucun fallback ordinal |
| `NativeKeyCode`, `NativeLogicalKey`, `NativeKeyInfo`, `KeyPlatform`, `PlatformEvent*` | internalize | détails backend ; vue optionnelle strictement sous `@KadrePlatformApi` si nécessaire |
| `PointerKind`, `PointerSource`, `TouchPhase`, `TouchForce`, `FingerId`, tablet tool types | keep/move | événements et snapshots de `SurfaceInput` |
| pinch, pan, rotation, double tap et touchpad pressure | keep/move | input de surface, activation explicite lorsqu’un recognizer doit être installé |
| `ImePurpose`, `ImeCapability`, `ImeCapabilities`, anciens `WindowEvent.Ime*` | replace | `TextInputSession`, `TextInputState`, `TextInputEvent`, offsets UTF-16 et `TextDocumentRevision` anti-stale |
| `DeviceEvents` et `listenDeviceEvents` | replace | `RawInputAccess`, `DevicePolicy`, permissions et routing de session |

La perte de focus, la déconnexion ou la révocation remplace les releases synthétiques par un `SurfaceInputState` neutre suivi de `InputEvent.StateReset`. Un overflow publie directement le snapshot composé terminal et termine le flow avec une failure hors de la lane saturée, sans promettre un dernier événement impossible à admettre. Les APIs nécessitant un serial ou une user activation utilisent `InteractionContext`, pas un événement `Flow` livré trop tard.

Le flux input unifié conserve son ordre grâce à un scheduler borné : une lane FIFO discrète et des lanes continues ne coalescent jamais à travers une barrière discrète. La cancellation d’un simple waiter n’acquiert aucun ownership sur une requête, une interaction ou un effet.

## Gamepads

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `GamepadController`, `Gamepad`, `GamepadState`, `GamepadEvent`, `Axis`, `Button`, `PowerInfo` | replace | `DeviceManager`, snapshots immuables incluant routing `Routed`/`Suspended`, neutralisation explicite, événements estampillés, scheduler discret/continu et collections spécialisées |
| `PlatformGamepad`, `PlatformGamepadBackend` | internalize | broker natif process-wide et adapters |
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

## Adaptateurs et déclarations de plateforme

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| `AndroidKadreRuntime` | remove | aucune session ou handler global |
| `KadreActivity` | replace | extensions `ComponentActivity.attachKadre` et `View.attachKadre` |
| `KadreAppDelegate`, `KadreRegistry` UIKit, `KadreMetalView` | replace/internalize | `KadreIos.attach`, host surface et bridge Swift minimal ; aucun renderer Metal public |
| delegates AppKit concrets et callbacks publics | internalize | `attachKadreDesktop` et options host typées |
| `ActivationPolicy`, `StatusBarStyle`, `ValidOrientations` et autres options réellement consommables | replace | options de plateforme ciblées sous opt-in, uniquement si un contract test prouve leur support |
| tous les packages générés `*.bindings.**`, symboles FFI et types de framework réexportés | internalize | aucune déclaration FFI générée dans l’ABI des artifacts Kadre |
| implémentations backend concrètes non listées comme point d’attachement | internalize | seules les factories/extensions documentées restent publiques |

## Façades, typealiases et règle résiduelle

| Surface actuelle | Décision | Cible ou justification |
|---|---|---|
| typealiases de `KadreApi.kt` et façades dupliquant `kadre-core` | remove | une seule surface normative dans `kadre` |
| artifact `kadre-coroutines` | remove | contrats fusionnés dans `kadre` |
| imports consommateurs `org.graphiks.kadre.core` | remove | `kadre-core` devient interne |
| toute déclaration actuelle `org.graphiks.kadre.core.**` non citée plus haut | internalize par défaut | elle ne reste publique que si une règle `keep/move` ou `replace` et un contrat cible explicite sont ajoutés avant migration |
| toute déclaration publique de backend non citée plus haut | internalize par défaut | une exception exige une entrée dans ce registre, un package cible et un consumer compile test |

Les deux règles résiduelles ferment la couverture mécanique de la baseline sans transformer les accidents ABI en fonctionnalités à préserver. Elles ne ferment pas la revue humaine : leur rapport nominatif doit être vide ou explicitement approuvé avant la suppression finale. Toute nouvelle déclaration publique ajoutée après la baseline doit être enregistrée directement dans sa forme cible ; elle ne peut s’abriter derrière ces règles de cleanup.
