# New Kadre — Contrats des adapters et matrice de capabilities

**Statut :** baseline normative fermée.  
**Portée :** Android, UIKit, Web JS/Wasm, AppKit, Win32, X11 et Wayland.

## 1. Deux niveaux de garantie

La spec sépare strictement :

- la **garantie structurelle** de l’adapter : point d’attachement, ownership du host, surface/fenêtre primaire, chemin de `requestWindow`, managers toujours présents et support de `KadrePolicies.Default` ;
- la **disponibilité fonctionnelle** : une capability publiée à runtime selon version OS, permissions, protocoles, matériel, navigateur et focus.

Une case `Capability` ci-dessous n’est ni une promesse de support ni une permission de no-op. Elle impose l’un de ces deux résultats exacts :

1. `Capability.Supported(constraints, availability)` avec une availability autre que `FeatureAvailability.Unsupported`, puis succès ou failure admise par `OPERATION-CONTRACTS.md` ;
2. `Capability.Unsupported(KadreFailure.Unsupported(operation))`, puis la même failure au point fonctionnel concerné.

Un champ passif typé directement `FeatureAvailability`, sans opération fonctionnelle propre, utilise `FeatureAvailability.Unsupported` pour l’absence structurelle. Il n’invente jamais une valeur de `KadreOperation` seulement pour remplir un snapshot.

Les checks runtime sont l’autorité. Une table statique de versions ne peut jamais surclasser une capability plus restrictive observée sur la machine réelle.

## 2. Légende

| Code | Contrat exact |
|---|---|
| `G` | garanti par tout adapter officiellement supporté de cette ligne |
| `C` | disponibilité pilotée à runtime : `Capability.Supported/Unsupported` pour un verbe, ou `FeatureAvailability.Available/Unsupported/Unavailable` pour une observation passive |
| `N` | fallback ou observation passive absent ; aucun objet synthétique n’est publié pour le simuler |
| `N(op)` | structurellement absent ; capability `Unsupported(op)` et opération sans faux succès |
| `S` | la ressource est créée dans une nouvelle `KadreSession` |
| `H` | dépend d’un provider fourni par le host ; sans provider, `N(op)` pour l’opération de la ligne |

## 3. Matrice d’attachement et de topologie

| Adapter officiel | Host possédé par l’application | `primarySurface` initiale | `WindowManager.primary` initiale | `requestWindow` | terminaison du host |
|---|---|---|---|---|---|
| Android `ComponentActivity` | `ComponentActivity` + `LifecycleOwner` | `G`, `surfaceView` fournie | `G`, fenêtre de l’Activity | `N(RequestWindow)` ; requête immédiatement `Rejected` | premier détachement de `surfaceView` ou `LifecycleOwner.onDestroy` |
| Android `View` | `View` + `LifecycleOwner` | `G`, la View attachée | toujours `null` | `N(RequestWindow)` ; requête immédiatement `Rejected` | premier `View.onDetachedFromWindow` ou destruction du lifecycle owner |
| UIKit `UIWindowScene` | la scène | `G`, `surfaceView` fournie | `G`, `UIWindow` de la scène | `C + S`, via activation d’une nouvelle scène | `sceneDidDisconnect` |
| Web JS `org.w3c.dom.HTMLElement` | élément DOM + browsing context | `G`, l’élément | toujours `null` | `H + S`; sans provider `Rejected(Unsupported)` | `pagehide`, destruction du context ou policy DOM |
| Web Wasm `org.w3c.dom.HTMLElement` | élément DOM + browsing context | `G`, l’élément | toujours `null` | `H + S`; sans provider `Rejected(Unsupported)` | `pagehide`, destruction du context ou policy DOM |
| Desktop `Embedded` | boucle UI identifiée dans les options | toujours `null`; surfaces accessibles via `Window.surface` | `null` jusqu’à première fenêtre de la session | `G`, `OpenedHere` | fermeture explicite du host ou arrêt de l’intégration |
| Desktop `Standalone` | processus/loop lancé par la commodité | toujours `null`; surfaces accessibles via `Window.surface` | `null` jusqu’à première fenêtre | `G`, `OpenedHere` | arrêt demandé ; fermeture dernière fenêtre si option activée |

Une session desktop peut rester headless pendant toute sa vie. La première fenêtre `OpenedHere` devient `primary`; sa fermeture choisit la première fenêtre vivante dans l’ordre d’admission stable de `WindowManagerState.windows`, ou `null`.

`Standalone(stopWhenLastWindowClosed = true)` ne stoppe pas une session initialement headless. La règle s’arme après l’admission de sa première `Window` et la première transition ultérieure de `WindowManagerState.windows` de non vide à vide propose `SessionStopReason.HostRequested`. Les `WindowRequest` encore pending ne comptent pas comme fenêtres et sont fermées par le teardown si elles n’ouvrent pas une fenêtre avant cette transition sérialisée.

Un host `Standalone` headless publie `Background + Inactive` jusqu’à sa première fenêtre visible/active. Un host `Embedded` sans fenêtre publie `Foreground + Active` tant que son intégration UI est attachée et non suspendue ; dès qu’il possède une fenêtre, visibilité et activation suivent l’agrégat de ses fenêtres. Aucun booléen applicatif ne falsifie ces axes.

Android ne crée jamais une seconde `Activity` ou `View` au nom de l’application. Un host Android qui veut une nouvelle Activity lance lui-même son composant et attache une nouvelle session ; ce mécanisme n’est pas présenté comme `requestWindow` v1.

UIKit ne retourne `OpenedInNewSession` qu’après corrélation de la scène effectivement connectée. Un refus ou discard signalé par le host produit `Rejected`, une annulation admise produit `Cancelled`, et aucun de ces cas ne crée de session synthétique. Kadre n’ajoute aucun timeout implicite : sans signal host, `cancel()` ou fermeture de l’owner, la `WindowRequest` reste `Pending`.

## 4. Matrice des domaines

Toutes les lignes possèdent les managers communs `windows`, `displays`, `devices`, `capture` et `diagnostics`, même lorsque leur state est `Unavailable`/`Unsupported`. Une manager absent/null est interdit.

| Domaine | Android Activity | Android View | UIKit Scene | Web JS/Wasm | AppKit | Win32 | X11 | Wayland |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| lifecycle à trois axes | G | G | G | G | G | G | G | G |
| signal de pression mémoire | C | C | C | C | C | C | C | C |
| surface metrics + redraw request | G | G | G | G | G | G | G | G |
| fenêtre top-level mutable | C | — (aucune `Window`) | C | — (aucune `Window`) | C | C | C | C |
| inventaire display complet | C | C | C | C | C | C | C | C |
| fallback `HostViewport` | C | C | C | G si inventaire complet absent | N | N | N | N |
| clavier | C | C | C | C, `Available` après installation | C | C | C | C |
| pointer | C | C | C | C, `Available` après installation | C | C | C | C |
| touch | C | C | C | C, `Available` après installation | C | C | C | C |
| gestures reconnues par le host | C | C | C | N(GestureInput) | C | C | C | C |
| drag-and-drop | C | C | C | C, `Available` après installation | C | C | C | C |
| IME / text input | C | C | C | C, `Supported` à l’installation | C | C | C | C |
| raw input | C | C | C | N(RawInputAccess) | C | C | C | C |
| gamepad observation | C | C | C | G, sondée (§6.3) | C | C | C | C |
| effets gamepad | C | C | C | G, gouvernée (§6.3) | C | C | C | C |
| capture target `HostChoice` | C | C | C | C | C | C | C | C |
| capture target `Source` | C | C | C | N(CaptureOpen) si inventaire interdit | C | C | C | C |
| capture target `Surface` | C | C | C | C | C | C | C | C |
| `SurfaceCapabilities.platformAccess` | G, `withAndroidView` | G, `withAndroidView` | G, `withUIKitView` | G, `withWebElement` | N(PlatformSurfaceAccess) | N(PlatformSurfaceAccess) | N(PlatformSurfaceAccess) | N(PlatformSurfaceAccess) |
| `WindowCapabilities.platformAccess` | N(PlatformWindowAccess) | — (aucune `Window`) | N(PlatformWindowAccess) | — (aucune `Window`) | G, `withDesktopHandle` | G, `withDesktopHandle` | G, `withDesktopHandle` | G, `withDesktopHandle` |

`raw input` est une capability dynamique. AppKit publie un bridge global
listen-only lorsque le check de version et Input Monitoring le permettent :
`Supported(RequiresPermission(InputMonitoring))`, `Supported(Available)` ou
`Supported(Unavailable(PermissionDenied(RawInput)))` selon le readback courant.
Les autres adapters conservent `Unsupported(RawInputAccess)` tant qu’ils ne
fournissent pas leur bridge et son contrat de permission. Sur Web, la décision
est livrée et prouvée : la capability reste
`Unsupported(RawInputAccess)` dans tous les états et le scénario
`web-gamepad-raw-input-unsupported` est le contrat de non-appel natif de la
ligne — la cellule de capability relue et zéro registration d’`EventTarget` sur
la page autour de la requête. Tous les adapters
doivent appliquer les budgets par accès, sans injecter ces événements dans
`SurfaceInput.events`.

Sur Web, `gamepad observation` vaut `G, sondée` : chaque session attachée
possède le manager et son inventaire toujours `Enumerated`, diff par index DOM
du poll `navigator.getGamepads()` — la seule source d’état que la spec donne,
lu à la cadence des animation frames tant qu’au moins un port de session est
ouvert, les événements DOM `gamepadconnected`/`gamepaddisconnected` (écoutés à
la fenêtre partout, au navigator seulement là où le navigateur l’accepte — le
navigator de Chromium n’est pas une event target) ne déclenchant qu’une
re-lecture immédiate. Un page insecure réel n’expose aucun pad
(`getGamepads` est secure-context-only) : l’inventaire reste énuméré, vide et
complet — jamais `Unavailable` déguisé. La liste `devices` reste vide parce que
le navigateur n’offre aucune primitive d’inventaire de périphériques
génériques, et rien n’est fabriqué. La porte de confidentialité de Chromium —
les pads réels invisibles à `getGamepads()` tant que l’utilisateur n’a pas
interagi avec la page — n’est pas contournée : elle fait partie des faits que
le poll rapporte, et sa preuve sur du vrai matériel (connexion/déconnexion par
l’OS, rumble réel) est l’affaire du cahier manuel
`kadre/contracts/driver/web/manual/phase-6-displays-devices.md` ; les preuves
de contrat utilisent la source synthétique scriptable (précédent D10),
stipulé dans les entêtes des specs. `effets gamepad` vaut `G, gouvernée` : le
chemin d’effet est garanti pour chaque pad, et la capability gelée par connexion
dit exactement ce que ce pad peut — les genres annoncés sont ceux de la liste
`effects` de l’actuateur propre du pad, sondés par un dual-rumble de durée nulle
quand le navigateur ne déclare rien ; `LocalizedHaptic` n’est jamais annoncé
(aucune primitive navigateur) ; `TriggerRumble` est porté avec ses magnitudes de
gâchette quand le navigateur le déclare ; `maximumDuration` reste `null` (le
navigateur serre lui-même la borne). Un lancement accepté a remis sa promesse au
navigateur, et une promesse qui rejette ensuite est rapportée sur le reporter de
failures, jamais représentée synchronement ; un pad déconnecté refuse
`Closed(Gamepad)` avant tout appel d’actuateur, un pad connecté mais suspendu
lance son effet (sémantique livrée alignée AppKit : un effet est une action
initiée par l’application, pas une livraison d’input). Ce qui varie par pad
(actuateur, secure context) est écrit dans la capability du pad elle-même,
jamais deviné hors d’elle.

L’inventaire AppKit utilise l’association publique `NSScreen.CGDirectDisplayID`,
introduite par macOS 26. Avant cette version, `DisplayCapabilities.enumeration`
reste `Unsupported(DisplayAccess)` : Kadre ne rapproche jamais `NSScreen` et
CoreGraphics par une heuristique de géométrie ou une clé privée. Lorsqu’il est
disponible, les bounds et work areas proviennent de CoreGraphics dans l’espace
physique du bureau virtuel ; `WindowCapabilities.outerPosition` reste toutefois
`Unsupported` jusqu’à une preuve matérielle séparée sur des écrans à échelles
mixtes.

Cet espace est publié tel quel : sur un écran HiDPI, `DisplayState.bounds` et la
work area valent la géométrie du bureau virtuel en points (`CGDisplayBounds`,
identique à `NSScreen.frame`) et ne sont jamais multipliés par le
`scaleFactor`. Le `physicalSize` d’un `DisplayMode` désigne au contraire la
taille framebuffer du mode en pixels : sur un écran 2×, il vaut le double des
bounds. La même règle vaut pour `WindowState.outerBounds`, qui égale champ à
champ `kCGWindowBounds` sans réduction ni multiplication par le backing scale ;
aucun espace global en pixels n’existe sur un bureau à échelles mixtes.

Sur AppKit, la pression mémoire est une source `Dispatch` unique pour le
processus. Elle est installée à la première session et rend
`LifecycleCapabilities.memoryPressure = Available` seulement après succès de
cette installation. Un échec de linkage ou de création publie
`Unavailable(PlatformFailure(AppKit, "memory-pressure", "source-exception"))`;
il ne fabrique aucun signal. `WARN` devient `Moderate` et `CRITICAL` devient
`Critical`. Le callback natif est relayé hors de sa pile, puis diffusé aux
sessions vivantes : fermer une session ne ferme pas la source process-wide,
alors que la terminaison du host la ferme avant tout nouveau relayage.
`backend/appkit/manual/phase-9-memory-pressure.md` décrit le stress matériel
des niveaux réels ; l'absence de notification système y reste `not-applicable`.

L’AppKit adapter lit l’`appearance` d’une surface comme un seul snapshot : le
thème vient de l’`effectiveAppearance` de la `NSView` et le contraste de
`NSWorkspace.accessibilityDisplayShouldIncreaseContrast`. Le callback local de
changement d’appearance de la view et la notification workspace des options
d’accessibilité reconstruisent ce même snapshot ; un changement de contraste
seul publie donc `SurfaceEvent.AppearanceChanged`. La notification est observée
sur le `notificationCenter` de ce `NSWorkspace`, puis relayée sur le main thread
avant toute lecture AppKit. Si une composante ne peut pas être lue, elle vaut
`Unknown` et aucune valeur n’est devinée.

L’absence de `Window` sur Android View ou sur le host Web initial ne ferme pas sa surface et ne fabrique aucune `WindowCapabilities`. `WindowManagerState.windows` reste vide ; Android publie `requestWindow = Unsupported(RequestWindow)`, tandis que Web suit son provider. `N(CaptureOpen)` pour `CaptureTarget.Source` n’interdit pas `HostChoice`; `sourceEnumeration`, `hostPicker` et les capabilities de target décrivent séparément ces chemins.

Les gestures sont des observations host-native ou des recognizers installés explicitement par l’adapter. Kadre ne promet aucun recognizer logiciel universel. Un adapter peut supporter pointer/touch tout en publiant gestures `Unsupported`.

Sur AppKit, le touch indirect est installé à partir de macOS 10.6. AppKit ne
fournit pas une position de contact trackpad dans les coordonnées de la fenêtre :
Kadre projette donc `NSTouch.normalizedPosition` dans les bounds logiques courants
de la surface au moment du callback. Cette valeur est une projection de
coordonnées device, pas une mesure native window-local. L'axe Y est inversé de
l'origine AppKit en bas à gauche vers l'origine Kadre en haut à gauche. Les phases `None`,
`Stationary` et `MayBegin` ne produisent aucun événement.

`InputCapabilities.gestures` expose sur AppKit un subset exact : `Pan`, `Pinch`
et `Rotation` à partir de macOS 10.7, puis `TouchpadPressure` à partir de 10.10.3.
`DoubleTap` n'est jamais fabriqué. `Pinch` utilise `1 + magnification`, la
rotation native en degrés est convertie en radians, `Swipe` devient `Pan` et la
pression n'est publiée que dans `[0,1]`. Le support n'est publié qu'après
l'installation du receiver et de l'admission touch ; il certifie le chemin
logiciel, pas la présence d'un trackpad compatible ni l'arrivée d'un événement
matériel. La révocation ferme d'abord l'admission native, retire les capabilities,
puis libère la vue, et aucun de ces événements n'entre dans le flux pointer.

Les deux lignes `platformAccess` sont structurelles, pas des probes de permission. Une surface Android/UIKit/Web attachée possède nécessairement le type SDK promis par son extension ; une fenêtre Desktop admise possède nécessairement le `DesktopNativeWindowHandle` correspondant à son backend fixé. Kadre ne publie pas de callback générique pour `UIWindow`, `android.view.Window` ou une surface desktop nue v1 ; leur capability opposée reste donc `Unsupported`, sans faux handle.

Les **interactions transitoires** et la **demande de fenêtre** du Web (phase 4) figurent dans la matrice par leurs points d’attachement §3 (`H + S` pour `requestWindow`) et dans le registre `capabilities/web.md` (§2, quatre lignes interaction/fenêtres ; §3.7-3.8) plutôt que par des lignes de ce tableau : l’état des autres adapters sur ces domaines est la décision de chacun, et seule la colonne Web est livrée ici. Sur Web, `SurfaceCapabilities.handlerInteractions` vaut `Supported({EnterFullscreen, ExitFullscreen, LockPointer, UnlockPointer}, Available)` après l’installation structurelle (`Unsupported(InstallInteractionHandler)` avant et au snapshot terminal) et `WindowManagerCapabilities.requestWindow` vaut `Supported({OpenedInNewSession}, Available)` ssi un `WebWindowProvider` est fourni, sinon `Unsupported(RequestWindow)` — `primary` restant `null` et aucun `Window` n’étant créé, la session enfant d’`OpenedInNewSession` n’est pas une `Window` de la session requérante.

## 5. Formes d’inventaire obligatoires

| Situation runtime | State public obligatoire |
|---|---|
| display(s) énumérables intégralement | `DisplayInventory.Enumerated(primary, displays)` |
| seulement le viewport du host est connu | `Enumerated(primary = viewport, displays = listOf(viewport))` avec `DisplayType.HostViewport` |
| permission d’énumération non demandée | `DisplayInventory.PermissionRequired` |
| permission refusée | `DisplayInventory.PermissionDenied(canRequestAgain)` |
| inventaire incomplet ou backend cassé | `DisplayInventory.Unavailable(failure)` |
| sources capture énumérables intégralement | `CaptureSources.Enumerated(values)` |
| picker obligatoire sans inventaire préalable | `CaptureSources.HostPickerOnly` |
| permission capture requise avant inventaire | `CaptureSources.PermissionRequired(required)` avec set non vide de `CaptureScreen`/`CaptureWindow` |
| inventaire capture incomplet ou backend cassé | `CaptureSources.Unavailable(failure)` |
| device inventory intégral | `DeviceInventory.Enumerated(devices, gamepads)` |
| observation de devices structurellement absente | `DeviceInventory.Unsupported` |
| broker/input source inutilisable | `DeviceInventory.Unavailable(failure)` |

Une liste vide dans `Enumerated` signifie « inventaire complet et vide ». Elle ne remplace jamais `PermissionRequired`, `HostPickerOnly` ou `Unavailable`.

## 6. Points d’attachement exacts

### 6.1 Android (`org.graphiks.kadre.platform.android`)

Les quatre overloads de la section 15.1 de `DESIGN.md` sont la surface complète. Il n’existe aucun `Application`, `Context` ou singleton overload. Les deux overloads `ComponentActivity` exigent une `surfaceView: View` explicite. `ComponentActivity` et `View` restent des types SDK Android et ces fonctions n’existent que dans `androidMain`.

`parentScope` doit contenir un `Job` actif : son absence retourne `InvalidRequest("parentScope")` et un job inactif retourne `ParentScopeCancelled`. Le défaut `lifecycleScope` est évalué à l’appel. Une même instance d’Activity ou View n’accepte qu’une session active et une View ne peut appartenir à deux hosts. Toutes les formes exigent `view.isAttachedToWindow`; sinon `InvalidRequest("view")` pour le receiver View ou `InvalidRequest("surfaceView")` pour l’Activity. L’Activity exige aussi `surfaceView.rootView === window.decorView`; sinon `InvalidRequest("surfaceView")`. Le premier `onDetachedFromWindow` est terminal et produit `HostDetached`, même si la View est ensuite reparentée ; cette réinsertion exige un nouvel attach et une nouvelle session. Le lifecycle owner détruit reste terminal avec la même priorité de race que la section 5 de `DESIGN.md`.

### 6.2 UIKit (`org.graphiks.kadre.platform.uikit`)

```kotlin
public object KadreIos {
    public fun attach(
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
    ): KadreResult<KadreSession>
}
```

La scope parent est créée et possédée par l’adapter sur le main dispatcher de la scène. L’attach exige `window.windowScene === windowScene`, `surfaceView.window === window` et une scène connectée ; sinon il retourne `InvalidRequest("window")` ou `InvalidRequest("surfaceView")` sans session. Une scène n’accepte qu’une session et les éventuelles autres UIWindows overlay restent host-owned. Il n’existe ni overload `UIApplication`, ni sélection de key window, ni session globale, ni implémentation Swift promise de `KadreApplication` v1.

### 6.3 Web (`org.graphiks.kadre.platform.web`)

Les deux targets exposent sémantiquement la même surface et utilisent le type SDK `org.w3c.dom.HTMLElement` fourni par leurs toolchains respectives :

```kotlin
public enum class WebAttachmentPolicy { StopWhenDetached, Manual }

public data class WebWindowHost(
    public val element: HTMLElement,
    public val parentScope: CoroutineScope,
    public val attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
)

public fun interface WebWindowProvider {
    public fun open(requestId: WindowRequestId, spec: WindowSpec): KadreResult<WebWindowHost>
}

@JsExport
public class KadreApplicationFactoryRef internal constructor()
public fun KadreApplicationFactory.asHostRef(): KadreApplicationFactoryRef

public fun HTMLElement.attachKadre(
    parentScope: CoroutineScope,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
    windowProvider: WebWindowProvider? = null,
): KadreResult<KadreSession>

public fun HTMLElement.attachKadre(
    parentScope: CoroutineScope,
    policy: KadrePolicy = KadrePolicies.Default,
    attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
    application: KadreApplication,
): KadreResult<KadreSession>
```

L’overload direct est mono-session, place `application` en dernier paramètre et n’expose donc aucun `windowProvider`. `WebWindowProvider.open` est synchrone, mais ne reçoit ni token d’interaction réutilisable, ni callback de completion : il choisit uniquement un élément déjà créé par le host dans un browsing context distinct. Un succès transfère cet élément à Kadre, qui y attache une nouvelle session avec la même factory et corrèle son `WindowRequestId`; `OpenedInNewSession` décrit cette session, jamais la création du contexte. Le provider ne peut créer ni popup, ni iframe, ni browsing context : Kadre ne fournit pas cette opération sur Web. Un `ownerDocument.defaultView` nul ou identique au browsing context origine produit `InvalidRequest("element.ownerDocument")`; un élément invalide/déconnecté sous `StopWhenDetached` produit `InvalidRequest("element")`. Le `parentScope` du host suit exactement le contrat commun d’attach : absence de `Job` → `InvalidRequest("parentScope")`, job inactif → `ParentScopeCancelled`. Failures retournées hors du set fermé et exceptions suivent les codes stables de `OPERATION-CONTRACTS.md`.

`SurfaceState.metrics` vient de la seule boîte de mise en page de l’élément hôte, livrée par l’observation du document, et du device pixel ratio du browsing context relu à chaque observation : la taille logique, la taille physique et le `scaleFactor` en dérivent, sans passer par une fenêtre, un viewport ou une lecture inventée. `Surface.requestRedraw` est admis tant que la surface est attachée et coalescé en un seul `SurfaceEvent.RedrawRequested` par animation frame sous `policy.window.redrawRequests` ; une capacité `ContinuousDelivery.Buffered` borne alors les admissions par animation frame, pas une file d’événements non délivrés : `Latest` et `Coalesced` sont indiscernables au niveau du publisher, et l’action `FailSession` d’un profil `Recording` n’est atteinte que par une rafale dans la même frame dépassant la capacité déclarée, là où la référence JVM borne une file d’événements non délivrés. Une fois la surface terminale, aucune frame n’est plus programmée et la demande retourne `Closed(Surface)`. `SurfaceCapabilities.platformAccess` vaut `G, withWebElement` dans les deux façades : le callback reçoit l’élément hôte du host, le temps d’une lease admise et sans handle public, et la fermeture de la surface publie `Unsupported(PlatformSurfaceAccess)`.

**Input.** Les listeners `keydown`, `keyup`, `pointerenter`, `pointermove`, `pointerdown`, `pointerup`, `pointerleave`, `pointercancel`, `lostpointercapture` et `wheel` sont posés sur l’élément attaché et sur lui seul, aucun sur le `Document`, la `Window` ou un ancêtre : `InputCapabilities.keyboard` et `InputCapabilities.pointer` passent à `Available` à la fin de l’installation structurelle de la session, et rien ne les déclare plus tôt. La capability décrit cette installation, pas l’arrivée d’un événement : rendre l’élément focusable est la responsabilité du host, qui seul peut écrire un `tabindex`, et un élément non focusable ne reçoit aucune frappe bien que `keyboard` reste `Available`. `InputCapabilities.touch` devient `Available` par la même observation structurelle que `keyboard`/`pointer` — les contacts de `pointerType == "touch"` sont livrés comme des `TouchState` par contact, sur les tout mêmes listeners pointer, avec une identité de contact stable par geste que le reducer commun clé dans ses propres `TouchId` ; `pointers` et `touches` restent disjoints, aucun listener `touchstart`/`touchmove`/`touchend` n’est posé, et un contact que le navigateur révoque pour un scroll natif que le `touch-action` du host a laissé passer est livré `TouchPhase.Cancelled` tel quel — sur le bras terminal d’overflow d’ingress, `touch` redevient `Unavailable(SourceOverflow(InputSource))` comme `keyboard`, `pointer` et `dragAndDrop`. `InputCapabilities.gestures` reste `Unsupported(GestureInput)` dans tous les états : aucun recognizer n’existe chez cet adapter et le navigateur n’offre aucune primitive de reconnaissance. `capabilities/web.md` §2.1 porte le détail de ce bras et les deux valeurs exactes, et §3.9 le détail du touch. Le clavier et le pointeur alimentent le reducer commun, sans lane, coalescence ni révision propres au Web. Le scroll est précis ou discret selon le `deltaMode` du navigateur : `DOM_DELTA_PIXEL` devient `ScrollDelta.Logical` et `DOM_DELTA_LINE` devient `ScrollDelta.Lines`, dans les unités du DOM et sans conversion ; `DOM_DELTA_PAGE` ne produit aucun `Scrolled`, faute d’une taille de page que Kadre posséderait. La frontière de coalescence est la frame d’animation, puisque le DOM n’expose ni phase ni momentum : elle s’ouvre pour le premier wheel d’une nouvelle frame, quand le `deltaMode` change ou quand l’état des boutons du pointeur change, et jamais entre deux frontières distinctes. `SurfaceCapabilities.pointerCapture` vaut `Supported({None, Confined}, Available)` : une capture `Confined` n’est admise que pour un pointeur que la surface détient déjà, sinon `InteractionRequired(Missing)`, et `Locked` est refusé comme champ `Unsupported(UpdateSurface)` parce que le Pointer Lock API appartient à `InteractionAction.LockPointer` (`DESIGN.md` §9.6). `SurfaceCapabilities.inputDefaultBehavior` vaut `Supported({HostDefault, SuppressWhenPossible}, Available)`, appliqué au point de dispatch : sous `HostDefault` aucun défaut navigateur n’est supprimé, sous `SuppressWhenPossible` seuls le wheel et la pression des neuf touches de défilement documentaire le sont — ces neuf touches étant reconnues par leur clé logique (`NamedKey`) et non par leur position physique, parce que le défaut du navigateur se définit sur la clé que le layout a produite —, et toute autre catégorie garde son défaut. `cursor`, `customCursor` et `hitTesting` restent `Unsupported(UpdateSurface)` après ces phases, comme tous les champs au snapshot terminal. `capabilities/web.md` porte le registre de ces features au format de la section 8, y compris la divergence de capture sur le bras « perte d’activation » et le statut différé des champs non activés.

**Text input.** La surface publie `InputCapabilities.textInput = Capability.Supported(Unit, Available)` à l’installation structurelle — l’éditabilité de l’élément est la frontière du host, et une session ouverte sur un élément que le contrat v1 n’adresse pas (`<input>` et `<textarea>` seuls) s’ouvre, n’observe rien et refuse son écriture-retour `Closed(TextInputSession)` sans toucher l’élément. Le port maintient un document-shadow (texte, sélection, révision acceptée, composition) et calcule chaque observation contre lui, stampée à la révision acceptée ; `updateSurroundingText` applique le snapshot accepté à l’élément (la seule écriture Kadre sur le DOM du host, sondée par le `instanceof` du navigateur) et au shadow ; `updateCursor` est accepté à la révision exacte et stocké sans effet navigateur (le navigateur dessine son propre caret). Les types `beforeinput` delete non computables (grapèmes) ne publient aucune observation, `deleteByCut` restant mappé à l’étendue exacte de la sélection ; la touche de soumission publie l’`Action` de la config et jamais de texte (`DESIGN.md:1219`) ; une composition est livrée start/update/end avec sa cancellation réelle (un `CompositionEvent` ne peut pas porter `null` — `data=""`, le retrait que le navigateur a exécuté est rapporté puis refusé par le test d’étendue du runtime, et la terminale `CompositionChanged(null, "", null)` clôt) ; une perte de focus suspend la session en préservant la composition, le regain la reprend. Le contrat d’admission et les failures admises sont ceux d’`OPERATION-CONTRACTS.md` §7 (`AlreadyInUse(TextInputSession)` pour une seconde session, `StaleRevision` pour une révision périmée) ; `capabilities/web.md` §3.10 porte le détail et les limites enregistrées.

**Drag-and-drop.** La surface publie `InputCapabilities.dragAndDrop = FeatureAvailability.Available` à la même installation structurelle, avec les listeners `dragenter`/`dragover`/`dragleave`/`drop` sur l’élément attaché et sur lui seul. Un `dragenter` snapshotte les descripteurs du magasin du navigateur — mimes canoniques seuls, les non-canoniques ignorés jamais fabriqués, `File`/`Blob` restés internes aux closures du port — et la surface présente l’offre par le seam synchrone (`presentDrop`), une seule active, la précédente terminale `LeftSurface` ; si une offre est présentée, `InteractionEvent.DropEntered` est dispatché dans la frame du callback et le handler l’accepte par `InteractionAction.AcceptDrop` (dans l’ensemble publié, répondu `Now` par le reducer) ou la laisse rejetée, le navigateur gardant son défaut. Un `dragover` sans offre active re-snapshotte et re-présente par le même seam (le churn `enter`/`leave` des enfants DOM absorbé) ; avec une offre il devient `DropMoved`, un `dragleave` un `DropExited`, un `drop` un `DropPerformed` qui rend le transfer claimable — un seul gagnant de claim, lectures bornées copiées par `collectBytes`, fermeture de la surface publiant `OwnerClosed` et fermant les transfers exactement une fois. `preventDefault` n’est appelé sur `dragover`/`drop` que pendant que la surface tient l’offre du drag en main — l’activation de la cible, jamais une suppression de défaut — et l’ensemble fermé `SUPPRESSED_INPUT_DEFAULTS` n’est pas étendu. Kadre n’est jamais la source d’un drag. Le contrat de l’offre et du transfer est celui de `DESIGN.md` §10.4 et d’`OPERATION-CONTRACTS.md` §7 ; `capabilities/web.md` §3.11 porte le détail et la limite enregistrée des lectures d’items de taille inconnue.

**Interactions.** La surface installe le moteur d’interaction commun du runtime — le même que la référence, sans token-machine propre au Web — et publie `SurfaceCapabilities.handlerInteractions = Supported({EnterFullscreen, ExitFullscreen, LockPointer, UnlockPointer}, Available)` au même moment structural que l’installation ordinaire de l’input, `armedInteractions` restant `Unsupported(ArmInteraction)` dans tous les états. Le dispatch part des listeners `pointerdown`/`keydown`, synchronement, avant l’admission du stimulus ordinaire : le handler s’exécute dans le callback DOM même, la frame dont la transient activation est l’autorité de ces opérations. Le token est single-use et expire au retour du callback — un contexte retenu reçoit `InteractionRequired(Expired)`, un callback imbriqué d’une autre surface `WrongSurface`, un second `request` du même callback `Consumed` — et une action hors de l’ensemble publié est refusée `Unsupported(Interaction)` avant tout appel navigateur. Les primitives fullscreen et pointer lock sont émises dans cette frame et leur outcome terminal est publié aux callbacks du navigateur : `fullscreenchange`/`pointerlockchange` confirment `Committed`, et un refus — `fullscreenerror`, `pointerlockerror`, promesse rejetée, ou une émission impossible — porte le seul code que le DOM expose, `PlatformFailure(Web, "fullscreen"|"pointer-lock", "refused")`, sans discrimination de raison. Chaque requête différée occupe le budget `maxPendingInteractionRequests` jusqu’à son callback terminal ; aucun timeout synthétique ne la termine, et la terminaison de la surface abandonne chaque pending avec `Closed(Interaction)`. Une fermeture de la registration pendant l’appel natif refuse la requête `Closed(Interaction)` sur les deux chemins. `LockPointer` n’admet que `PointerCaptureMode.Locked` ; tout autre mode est `InvalidRequest("action.mode")` avant toute primitive. `InteractionAction.OpenWindow` reste `Unsupported` sur Web même avec provider (`DESIGN.md:1933`). Kadre ne lit pas `navigator.userActivation` : les événements appariés sont déjà des événements *trusted* porteurs d’activation, et un refus réel du navigateur se manifeste comme l’outcome `Rejected` ci-dessus — le navigateur reste l’arbitre de ses primitives. `PointerCaptureMode.Locked` reste hors de `SurfaceCapabilities.pointerCapture`, où le verrou n’est jamais un champ.

**Fenêtres.** Sans provider, `requestWindow` reste un `WindowRequest` déjà terminal `Rejected(Unsupported(RequestWindow))`, inchangé. Avec un `WebWindowProvider`, `WindowManagerCapabilities.requestWindow` vaut `Supported({OpenedInNewSession}, Available)` — `OpenedHere` n’est jamais promis — et la requête admise est résolue synchronement : le provider reçoit une copie du `WindowSpec`, y compris les octets de l’icône, et son host est validé par l’échelle d’`OPERATION-CONTRACTS.md` §4 — ordre livré : `InvalidRequest("element")` (élément déconnecté sous `StopWhenDetached`), puis `InvalidRequest("element.ownerDocument")` (`defaultView` nul ou égal au contexte d’origine), puis `InvalidRequest("parentScope")`, puis `ParentScopeCancelled`, ce dernier ne décrivant que la scope du nouveau host. Une exception du callback devient `PlatformFailure(Web, "WebWindowProvider", "callback-exception")` et une failure retournée hors de l’ensemble fermé le même domain avec le code `"invalid-failure"` — des outcomes de la requête admise, jamais des failures de l’appel `requestWindow`. La session enfant passe par le chemin d’attach ordinaire : même factory, même policy, registre global d’ownership partagé — un élément possédé par une session vivante produit `Busy(Host)` — et launch context `AdditionalHostRequested` portant l’`originatingRequestId` de la requête ; la fermeture du requester n’atteint jamais l’enfant, dont la scope n’est jamais un descendant de la sienne. Divergence enregistrée de comptage : la requête terminale tient son créneau `maxPendingWindowRequests` jusqu’au `close()` de son requester — là où la référence évict au handoff — parce qu’un provider synchrone publie l’outcome avant que le caller voie la requête, et que seule cette lecture rend `Limit(WindowRequest)` exécutable ; `cancel()` répond `AlreadyTerminated`, `await()` ne suspend pas et `close()` ne fait que libérer le créneau.

**Displays.** Aucun navigateur n’énumère les écrans derrière sa fenêtre, et la primitive qui le
prometrait — la Window Management API (`navigator.getScreenDetails`) — n’est appelée aucune fois
par cet adapter : elle exige une permission à prompt (qu’aucune énumération ni readback ne
déclenche implicitement), ne vit qu’au top-level du browsing context et reste inégale d’un moteur
à l’autre. La forme obligatoire de la §5 est donc livrée par son second bras, inconditionnel pour
toute session attachée : `Enumerated(primary = viewport, displays = listOf(viewport))` avec
`DisplayType.HostViewport`, mesuré depuis les quatre faits que le browsing context expose
(`innerWidth`/`innerHeight`/`devicePixelRatio`/`screen.colorDepth`) et publié par l’install de
l’observateur du manager — une session qui s’est attachée sans rien demander a déjà l’inventaire
exact, jamais un inventaire vide, jamais un second display. `bounds` et work area valent le
layout viewport en pixels physiques `round(w·dpr) × round(h·dpr)`, `scaleFactor` vaut le
`devicePixelRatio` et `bitDepth` le `screen.colorDepth` ; `refreshRateHz` n’est jamais publié
(aucune primitive honnête) et l’inventaire porte un seul mode de la même taille physique.
Un `resize` et une requête de résolution `(resolution: <dpr>dppx)` ré-enregistrée à chaque feu
republishent le snapshot ; le dpr est relu à chaque mesure, si bien qu’un navigateur qui ne
tirerait jamais la requête échantillonnerait le ratio au prochain `resize`. Un viewport
inmesurable répond `TemporarilyUnavailable(retryable)` et le runtime retire l’inventaire échoué.
La preuve est `BCK-007` (quatre scénarios et quatre sentinelles par target,
`playwright/web-display.spec.mjs`).

**Devices et gamepads.** L’inventaire publié est toujours
`DeviceInventory.Enumerated(devices = [], gamepads = …)` : la liste `devices` reste vide parce
que le navigateur n’offre aucune primitive d’inventaire de périphériques d’entrée génériques et
rien n’est fabriqué pour la remplir ; la liste `gamepads` est le diff par index DOM du poll
`navigator.getGamepads()` — trous compris sans pad fantôme ni renumérotation, descripteur gelé à
la connexion depuis le mot `mapping` du navigateur (`"standard"` nomme les 17 boutons et 4 axes
du layout standard en ordre DOM, tout autre mot ne promet rien et produit des contrôles natifs au
compte rapporté), état apparié positionnellement et canonisé vers les fenêtres du modèle, un pad
hostile ne publiant que les événements que ses états canoniques diffèrent. La boucle de poll —
une animation frame en attente à la fois — ne tourne que tant qu’au moins un port de session est
ouvert ; la dernière fermeture annule la frame et retire les listeners, et un page caché ne lit
pas du tout. Le routage suit le broker AppKit : foreground-actif + policy, une projection
suspendue publie le neutre et enregistre les vraies lectures pour la reprise, qui livre ce que le
pad lit maintenant. Les effets sont gouvernés par la capability gelée par connexion décrite à la
ligne `effets gamepad` ci-dessus (§4) ; un pad déconnecté refuse `Closed(Gamepad)` avant tout
appel d’actuateur, un pad connecté mais suspendu lance son effet. La preuve est `BCK-008`
(inventaire, connexions, poll, déconnexion, routage, teardown — six scénarios et quatre
sentinelles par target, `playwright/web-devices.spec.mjs`) et `BCK-009` pour les effets et leurs
préconditions (cinq scénarios et quatre sentinelles par target,
`playwright/web-gamepad-effects.spec.mjs`, dont le scénario insecure-context du second projet
Playwright, `--host-resolver-rules=MAP insecure.kadre.invalid 127.0.0.1`, qui n’asserte que des
observables : `isSecureContext` faux, pad découvert, effets `Unsupported`, zéro prompt).

### 6.4 Desktop (`org.graphiks.kadre.platform.desktop`)

```kotlin
public enum class DesktopBackend { Auto, AppKit, Win32, X11, Wayland }
public enum class DesktopIntegration { AppKitMainLoop, AwtEventDispatchThread, JavaFxApplicationThread }

public sealed interface DesktopHostOptions {
    public data class Embedded(
        public val integration: DesktopIntegration,
        public val backend: DesktopBackend = DesktopBackend.Auto,
    ) : DesktopHostOptions

    public data class Standalone(
        public val backend: DesktopBackend = DesktopBackend.Auto,
        public val stopWhenLastWindowClosed: Boolean = true,
    ) : DesktopHostOptions
}

public fun CoroutineScope.attachKadreDesktop(
    applicationFactory: KadreApplicationFactory,
    options: DesktopHostOptions,
    policy: KadrePolicy = KadrePolicies.Default,
): KadreResult<KadreSession>

public fun CoroutineScope.attachKadreDesktop(
    options: DesktopHostOptions,
    policy: KadrePolicy = KadrePolicies.Default,
    application: KadreApplication,
): KadreResult<KadreSession>

public fun runKadreApplication(
    applicationFactory: KadreApplicationFactory,
    options: DesktopHostOptions.Standalone = DesktopHostOptions.Standalone(),
    policy: KadrePolicy = KadrePolicies.Default,
): SessionOutcome

public fun runKadreApplication(
    options: DesktopHostOptions.Standalone = DesktopHostOptions.Standalone(),
    policy: KadrePolicy = KadrePolicies.Default,
    application: KadreApplication,
): SessionOutcome
```

`attachKadreDesktop` n’a volontairement pas d’option par défaut : une intégration embarquée doit nommer la boucle réellement possédée par le host. `runKadreApplication` n’accepte que `Standalone`, ce qui rend impossible une combinaison bloquante/embedded incohérente. Dans les overloads directs, `application` est le dernier paramètre pour conserver la trailing lambda idiomatique ; ces overloads sont mono-session et partagent exactement policy, failures et lifecycle avec les overloads factory.

Un échec de l’attach interne du runner, avant toute `KadreSession`, lève `KadreException` avec la failure d’attachement. Dès qu’une session existe, le runner ne lève plus de failure fonctionnelle et retourne son unique `SessionOutcome` terminal.

`DesktopBackend.Auto` choisit une fois le backend compatible disponible avant attachement. Après admission, aucune bascule de backend n’est autorisée. Une combinaison OS/backend/integration incompatible retourne `InvalidRequest("options")`; un backend demandé mais indisponible retourne `Unsupported(HostAttach)`.

## 7. Capability changes

L’ordre obligatoire pour une capability dynamique est :

1. publier le snapshot composé contenant la nouvelle capability ;
2. neutraliser ou terminaliser l’état dépendant dans ce même snapshot lorsqu’il en fait partie ;
3. admettre l’événement fonctionnel éventuel ;
4. admettre `KadreDiagnostic.CapabilityChanged` best-effort.

Une permission révoquée utilise `FeatureAvailability.RequiresPermission` ou `Unavailable` avant de terminer la source. Un reconnect matériel crée un nouvel ID si le handle précédent avait atteint son état terminal.

## 8. Registre versionné d’implémentation

La matrice centrale ne fige pas des suppositions fragiles du type « API X existe sur toute version future ». Chaque adapter doit produire, avec son implémentation, un fichier public de documentation `capabilities/<adapter>.md` contenant exactement une ligne par feature du tableau de section 4 :

| Colonne obligatoire | Contenu |
|---|---|
| feature | nom du champ de capability public |
| target | target Kotlin/OS/browser |
| minimum déclaré | version OS, API level, protocole ou browser testé |
| compile gate | symbole/SDK requis ou `none` |
| runtime gate | permission, protocole, matériel, secure context, focus ou `none` |
| état absent | `Unsupported`, `RequiresPermission`, `RequiresInteraction` ou `Unavailable` exact |
| tests | identifiants des contract/consumer tests |

Ce registre est une preuve d’implémentation à produire plus tard, pas une réouverture du contrat. Une ligne manquante empêche l’adapter d’être déclaré supporté.

## 9. Gate adapter officiel

Un adapter ne peut être marqué « supported » que si :

- il accepte `KadrePolicies.Default` ;
- les cinq managers et tous leurs snapshots initiaux sont disponibles avant `Running` ;
- chaque case `G` passe les contract tests communs ;
- chaque case `C` possède les deux tests `Supported` et `Unsupported` ou une justification de runtime gate ;
- chaque case `N(op)` retourne la failure exacte, sans no-op, et chaque case passive `N` s’abstient de publier un fallback synthétique ;
- lifecycle et teardown passent les races attach/detach/cancellation ;
- le registre versionné est complet ;
- le consumer compile test de son interop cible passe.

La preuve de ces points suit `TEST-STRATEGY.md` : driver opposé à la frontière Kadre, oracle `O3` minimum, aucun skip transformant une absence d’infrastructure en succès et preuve d’exécution de chaque `contractId/scenarioId/target` obligatoire.
