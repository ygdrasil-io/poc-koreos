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
| Web Wasm `web.html.HTMLElement` | élément DOM + browsing context | `G`, l’élément | toujours `null` | `H + S`; sans provider `Rejected(Unsupported)` | `pagehide`, destruction du context ou policy DOM |
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
| clavier | C | C | C | C | C | C | C | C |
| pointer | C | C | C | C | C | C | C | C |
| touch | C | C | C | C | C | C | C | C |
| gestures reconnues par le host | C | C | C | C | C | C | C | C |
| drag-and-drop | C | C | C | C | C | C | C | C |
| IME / text input | C | C | C | C | C | C | C | C |
| raw input | C | C | C | C | C | C | C | C |
| gamepad observation | C | C | C | C | C | C | C | C |
| effets gamepad | C | C | C | C | C | C | C | C |
| capture target `HostChoice` | C | C | C | C | C | C | C | C |
| capture target `Source` | C | C | C | N(CaptureOpen) si inventaire interdit | C | C | C | C |
| capture target `Surface` | C | C | C | C | C | C | C | C |
| `SurfaceCapabilities.platformAccess` | G, `withAndroidView` | G, `withAndroidView` | G, `withUIKitView` | G, `withWebElement` | N(PlatformSurfaceAccess) | N(PlatformSurfaceAccess) | N(PlatformSurfaceAccess) | N(PlatformSurfaceAccess) |
| `WindowCapabilities.platformAccess` | N(PlatformWindowAccess) | — (aucune `Window`) | N(PlatformWindowAccess) | — (aucune `Window`) | G, `withDesktopHandle` | G, `withDesktopHandle` | G, `withDesktopHandle` | G, `withDesktopHandle` |

L’absence de `Window` sur Android View ou sur le host Web initial ne ferme pas sa surface et ne fabrique aucune `WindowCapabilities`. `WindowManagerState.windows` reste vide ; Android publie `requestWindow = Unsupported(RequestWindow)`, tandis que Web suit son provider. `N(CaptureOpen)` pour `CaptureTarget.Source` n’interdit pas `HostChoice`; `sourceEnumeration`, `hostPicker` et les capabilities de target décrivent séparément ces chemins.

Les gestures sont des observations host-native ou des recognizers installés explicitement par l’adapter. Kadre ne promet aucun recognizer logiciel universel. Un adapter peut supporter pointer/touch tout en publiant gestures `Unsupported`.

Les deux lignes `platformAccess` sont structurelles, pas des probes de permission. Une surface Android/UIKit/Web attachée possède nécessairement le type SDK promis par son extension ; une fenêtre Desktop admise possède nécessairement le `DesktopNativeWindowHandle` correspondant à son backend fixé. Kadre ne publie pas de callback générique pour `UIWindow`, `android.view.Window` ou une surface desktop nue v1 ; leur capability opposée reste donc `Unsupported`, sans faux handle.

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

Les deux targets exposent sémantiquement la même surface mais utilisent leur type SDK natif (`org.w3c.dom.HTMLElement` en JS, `web.html.HTMLElement` en Wasm) :

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

L’overload direct est mono-session, place `application` en dernier paramètre et n’expose donc aucun `windowProvider`. `WebWindowProvider.open` est synchrone afin de pouvoir être invoqué pendant une transient user activation. Un succès transfère à Kadre un élément déjà créé/choisi par le host ; Kadre y attache une nouvelle session avec la même factory et corrèle son `WindowRequestId`. La provider ne reçoit ni token d’interaction réutilisable, ni callback de completion. Un element appartenant au même browsing context ne satisfait pas une création de fenêtre et produit `InvalidRequest("element.ownerDocument")`; un élément invalide/déconnecté sous `StopWhenDetached` produit `InvalidRequest("element")`. Le `parentScope` du host suit exactement le contrat commun d’attach : absence de `Job` → `InvalidRequest("parentScope")`, job inactif → `ParentScopeCancelled`. Failures retournées hors du set fermé et exceptions suivent les codes stables de `OPERATION-CONTRACTS.md`.

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
