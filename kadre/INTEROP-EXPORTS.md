# New Kadre — Registre fermé des exports interop

**Statut :** surface source promise fermée pour Kotlin, Java, Swift, JavaScript et Wasm.  
**Principe :** l’ABI produite par un compilateur n’est pas automatiquement une API source supportée.

## 1. Niveaux de surface

| Niveau | Consumers | Garantie |
|---|---|---|
| API principale | Kotlin Multiplatform | tout `PUBLIC-API-CATALOG.md`, coroutines/`Flow`/`StateFlow`, contrats complets |
| façade host | Java Android/Desktop, Swift iOS, JavaScript/Wasm browser | attachement, arrêt, observation de session et outcome terminal uniquement |
| escape hatch plateforme | Kotlin target-specific | accès borné à la vue/surface/window native sous `@KadrePlatformApi` + `@DelicateKadreApi` |
| détail généré | ObjC headers, mangling JVM, glue JS/Wasm, FFI | non supporté sauf symbole explicitement inscrit ci-dessous |

Les applications métier restent écrites en Kotlin partagé. Une façade host reçoit une référence à une `KadreApplicationFactory` créée et exportée par le module applicatif Kotlin ; Java, Swift ou JavaScript ne sont pas promis comme langages d’implémentation directe de `KadreApplication` v1.

## 2. Décision par famille commune

| Famille Kotlin | Kotlin | Java source promise | Swift source promise | JS/Wasm source promise |
|---|---:|---:|---:|---:|
| application/factory/scope | complet | factory ref seulement | factory ref seulement | factory ref opaque seulement |
| `KadrePolicy` et builders/copy | complet | profils intégrés | profils intégrés | profils intégrés |
| session/state/outcome | complet | wrapper | wrapper | wrapper |
| lifecycle | complet | non exporté ; host possède déjà le lifecycle | non exporté ; `UIScene` fait autorité | non exporté ; page/DOM font autorité |
| managers, handles, capabilities | complet | non promis | non promis | non promis |
| `Flow`/`StateFlow` | complet | jamais exposé dans la façade | jamais exposé | jamais exposé |
| `KadreResult` | complet | traduit en retour/`KadreException` | traduit en `throws` | traduit en discriminated union/exception documentée |
| `KadreFailure` | complet | value model lisible | enum Swift fermée | discriminated union fermée |
| suspend functions | complet | aucune signature `Continuation` promise | wrappers `async` listés seulement | `Promise` listées seulement |
| callbacks de streaming | complet | non promis | non promis | non promis |
| artifact `test` | complet Kotlin | non promis | non promis | non promis |
| native handles | target Kotlin | non promis par la façade | non promis par la façade | non promis par la façade |

Les symboles non promis peuvent physiquement apparaître dans un bytecode/header généré tant que le toolchain ne permet pas de les cacher. Les tests interop doivent les ignorer et aucun sample/document ne les utilise. Dès qu’un mécanisme stable existe, les actuals appliquent `@JvmSynthetic`, `@HiddenFromObjC` ou l’absence de `@JsExport` sans ajouter ces annotations à l’API commune.

## 3. Value model interop commun

Les façades partagent les mêmes enums fermées :

- `KadreSessionPhase`: `starting`, `running`, `stopping`, `terminated` ;
- `KadreStopReason`: les cinq valeurs de `SessionStopReason` ;
- `KadreOperation`, `KadrePermission`, `KadrePolicyComponent`, `KadreResourceKind`, `KadrePlatform`, `InteractionFailureReason` : mêmes variantes et même orthographe sémantique que Kotlin ;
- `KadreSessionOutcome`: `completed`, `stopped(reason)`, `failed(failure)` ;
- `KadreFailure`: exactement les variantes de `KadreFailure` Kotlin avec les mêmes champs stables.

Les IDs deviennent des strings opaques dans les façades (`sessionId` notamment). Leur format n’est ni parsable ni stable entre processus. Les `Duration` deviennent des nanoseconds signés `Int64`/`long` dans les DTO de failure ; seules les valeurs finies et positives admises par l’API Kotlin sont exportées. Un message humain dérivé peut être exposé en lecture, mais il n’entre jamais dans l’égalité ou le branching.

## 4. Java — surface source promise

Packages : `org.graphiks.kadre.host.android` et `org.graphiks.kadre.host.desktop`.

```java
public final class KadreAndroid {
    public static KadreSessionHandle attach(
        ComponentActivity activity,
        View surfaceView,
        KadreApplicationFactory applicationFactory,
        KadrePolicy policy
    ) throws KadreException;

    public static KadreSessionHandle attach(
        View view,
        LifecycleOwner lifecycleOwner,
        KadreApplicationFactory applicationFactory,
        KadrePolicy policy
    ) throws KadreException;
}

public final class KadreDesktop {
    public static SessionOutcome run(
        KadreApplicationFactory applicationFactory,
        DesktopHostOptions.Standalone options,
        KadrePolicy policy
    ) throws KadreException;
}

public final class KadreSessionHandle implements AutoCloseable {
    public String id();
    public KadreSessionSnapshot state();
    public AutoCloseable observeState(Consumer<KadreSessionSnapshot> observer);
    public CompletionStage<SessionOutcome> termination();
    public void requestStop();
    @Override public void close();
}
```

`policy` est non-null ; les helpers Java `KadrePolicies.defaultPolicy()`, `realtimePolicy()` et `recordingPolicy()` retournent les trois values exactes. Aucun builder Java custom n’est promis v1 ; un module applicatif Kotlin peut fournir une policy custom déjà construite.

L’overload Activity exige une `surfaceView` déjà attachée à `activity.getWindow().getDecorView()`; la façade ne choisit ni ne crée une vue. Les deux overloads Android terminent le host au premier détachement de la vue fournie, conformément au contrat Kotlin.

`attach` traduit `KadreResult.Failure` en `KadreException`. `termination()` retourne toujours le même `CompletionStage`; annuler ce stage annule uniquement ce waiter et ne stoppe pas la session. `observeState` appelle le consumer immédiatement avec le snapshot courant, puis séquentiellement pour chaque changement ; fermer le token retire l’observer sans stopper la session. Une exception du consumer désinscrit cet observer et est remise à l’`UncaughtExceptionHandler` du dispatcher host, sans terminer Kadre.

Le bytecode brut des fonctions `suspend` et extensions Kotlin n’est pas une surface Java promise. Le consumer compile test interdit les appels directs à un paramètre `Continuation`.

## 5. Swift — module source `KadreHost`

```swift
public enum KadreIosHost {
    public static func attach(
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicyProfile = .default
    ) throws -> KadreSessionHandle
}

public final class KadreHostViewController: UIViewController {
    public init(
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicyProfile = .default
    )
    public private(set) var kadreSession: KadreSessionHandle? { get }
}

public enum KadrePolicyProfile: Sendable {
    case `default`
    case realtime
    case recording
}

public final class KadreSessionHandle: @unchecked Sendable {
    public var id: String { get }
    public var state: KadreSessionSnapshot { get }
    public func observeState(
        _ observer: @escaping @MainActor (KadreSessionSnapshot) -> Void
    ) -> KadreObservation
    public func requestStop()
    public func close()
    public func awaitTermination() async throws -> KadreSessionOutcome
}

public final class KadreObservation: @unchecked Sendable {
    public func close()
}
```

`KadreApplicationFactory` est le type de référence exporté par le framework Kotlin. Swift peut recevoir et repasser une instance produite par le module applicatif, mais l’initialisation ou la conformance Swift directe n’est pas supportée v1. `KadreHostViewController` attache exactement une fois lorsque sa view appartient à une `UIWindow` et une `UIWindowScene`; il utilise cette view comme `surfaceView`, ne la remplace pas et ne crée aucun layout. Un retrait temporaire ne ferme pas la scène ; `deinit`, `sceneDidDisconnect` ou `close` explicite ferment la session. `UIViewControllerRepresentable` reste une dizaine de lignes possédées par l’application SwiftUI autour de ce controller.

`observeState` est `@MainActor`, émet immédiatement le snapshot courant, reste sérialisé et se retire sur `close`. `awaitTermination` utilise une continuation Swift bornée : annuler la `Task` lève `CancellationError` et retire uniquement le waiter. `attach` traduit toute failure directe en `KadreError`.

La surface Swift du value model est fermée :

```swift
public enum KadreSessionSnapshot: Equatable, Sendable {
    case starting
    case running
    case stopping
    case terminated(KadreSessionOutcome)
}

public enum KadreSessionOutcome: Equatable, Sendable {
    case completed
    case stopped(KadreStopReason)
    case failed(KadreFailure)
}

public struct KadreError: Error, Equatable, Sendable {
    public let failure: KadreFailure
    public let message: String
}
```

`KadreFailure` est une enum Swift avec exactement ces cases :

```text
unsupported(operation)
permissionDenied(permission)
userCancelled(operation)
temporarilyUnavailable(retryable)
invalidRequest(field)
alreadyInUse(resource)
closed(resource)
resourceLimitExceeded(resource, limit)
sourceOverflow(resource)
staleRevision(expected, received)
interactionRequired(reason)
unsupportedPolicy(component)
parentScopeCancelled
shutdownTimedOut(timeoutNanoseconds)
sourceLost(sourceId)
applicationFailure
platformFailure(platform, domain, code)
```

Les strings `field`, `domain`, `code` et `sourceId` suivent les règles de redaction et bornes de Kotlin. Il n’existe aucun wrapper Swift des managers, flows, frames ou callbacks input v1.

## 6. JavaScript et Wasm — module `@kadre/host`

Le module applicatif Kotlin produit la référence opaque avec la seule passerelle Kotlin target-specific suivante :

```kotlin
@JsExport
public class KadreApplicationFactoryRef internal constructor()

public fun KadreApplicationFactory.asHostRef(): KadreApplicationFactoryRef
```

La classe conserve en interne la factory, n’expose aucun membre Kotlin public et ne peut être construite depuis JavaScript. Une application exporte par exemple une fonction `@JsExport fun applicationFactory(): KadreApplicationFactoryRef = factory.asHostRef()`. Chaque appel crée un wrapper léger ; plusieurs wrappers peuvent référencer la même factory thread-safe.

La déclaration TypeScript promise est exactement :

```typescript
export type KadrePolicyProfile = "default" | "realtime" | "recording";
export type KadreOperation =
  | "hostAttach" | "requestRedraw" | "displayAccess" | "requestWindow"
  | "updateWindow" | "requestWindowAttention" | "closeWindow" | "respondToCloseRequest" | "updateSurface"
  | "installInteractionHandler" | "armInteraction" | "interaction"
  | "gamepadEffect" | "stopGamepadEffects" | "textInput" | "updateTextInput"
  | "claimDropTransfer" | "readDropItem" | "capturePermission"
  | "captureRefreshSources" | "captureOpen" | "captureCollectFrames"
  | "rawInputAccess" | "platformSurfaceAccess" | "platformWindowAccess";
export type KadrePermission =
  | "displayEnumeration" | "inputMonitoring" | "rawInput" | "captureScreen" | "captureWindow";
export type KadrePolicyComponent =
  | "execution" | "lifecycleEvents" | "hostSignals" | "windowEvents"
  | "deviceEvents" | "inputEvents" | "devicePolicy" | "captureEvents" | "captureFrames"
  | "diagnostics" | "resources";
export type KadreResourceKind =
  | "host" | "surface" | "window" | "windowRequest" | "display"
  | "inputSource" | "inputDevice" | "gamepad" | "eventCollector" | "interaction"
  | "dropTransfer" | "dropItem" | "cursorImage" | "gamepadEffect"
  | "textInputSession" | "captureSource" | "captureSession" | "captureCollector"
  | "captureBuffer" | "retainedPayload" | "imageResource" | "eventSequence";
export type KadrePlatform = "android" | "uikit" | "web" | "appKit" | "win32" | "x11" | "wayland" | "fake";
export type InteractionFailureReason = "missing" | "expired" | "consumed" | "wrongSurface";
export type KadreStopReason =
  | "hostRequested"
  | "applicationRequested"
  | "applicationCancelled"
  | "parentCancelled"
  | "hostDetached";

export type KadreSessionOutcome =
  | { readonly kind: "completed" }
  | { readonly kind: "stopped"; readonly reason: KadreStopReason }
  | { readonly kind: "failed"; readonly failure: KadreFailure };

export type KadreSessionSnapshot =
  | { readonly kind: "starting" }
  | { readonly kind: "running" }
  | { readonly kind: "stopping" }
  | { readonly kind: "terminated"; readonly outcome: KadreSessionOutcome };

export type KadreFailure =
  | { readonly kind: "unsupported"; readonly operation: KadreOperation }
  | { readonly kind: "permissionDenied"; readonly permission: KadrePermission }
  | { readonly kind: "userCancelled"; readonly operation: KadreOperation }
  | { readonly kind: "temporarilyUnavailable"; readonly retryable: boolean }
  | { readonly kind: "invalidRequest"; readonly field: string | null }
  | { readonly kind: "alreadyInUse"; readonly resource: KadreResourceKind }
  | { readonly kind: "closed"; readonly resource: KadreResourceKind }
  | { readonly kind: "resourceLimitExceeded"; readonly resource: KadreResourceKind; readonly limit: bigint }
  | { readonly kind: "sourceOverflow"; readonly resource: KadreResourceKind }
  | { readonly kind: "staleRevision"; readonly expected: bigint; readonly received: bigint }
  | { readonly kind: "interactionRequired"; readonly reason: InteractionFailureReason }
  | { readonly kind: "unsupportedPolicy"; readonly component: KadrePolicyComponent }
  | { readonly kind: "parentScopeCancelled" }
  | { readonly kind: "shutdownTimedOut"; readonly timeoutNanoseconds: bigint }
  | { readonly kind: "sourceLost"; readonly sourceId: string }
  | { readonly kind: "applicationFailure" }
  | { readonly kind: "platformFailure"; readonly platform: KadrePlatform; readonly domain: string; readonly code: string };

export interface KadreApplicationFactoryRef {
  readonly __kadreApplicationFactory: unique symbol;
}

export interface KadreLogicalSize { readonly width: number; readonly height: number }
export interface KadrePhysicalPoint { readonly x: number; readonly y: number }
export interface KadrePhysicalSize { readonly width: number; readonly height: number }
export interface KadreBinaryImage {
  readonly format: "png" | "jpeg" | "webp" | "rgba8";
  readonly bytes: Uint8Array;
  readonly pixelSize: Readonly<KadrePhysicalSize> | null;
}
export type KadreFullscreenMode =
  | { readonly kind: "windowed" }
  | { readonly kind: "borderless" }
  | { readonly kind: "exclusive"; readonly displayId: string; readonly physicalWidth: number; readonly physicalHeight: number; readonly refreshRateHz: number | null; readonly bitDepth: number | null };
export interface KadreWindowSpec {
  readonly title: string;
  readonly contentSize: Readonly<KadreLogicalSize>;
  readonly minimumSize: Readonly<KadreLogicalSize> | null;
  readonly maximumSize: Readonly<KadreLogicalSize> | null;
  readonly outerPosition: Readonly<KadrePhysicalPoint> | null;
  readonly resizable: boolean;
  readonly fullscreen: Readonly<KadreFullscreenMode>;
  readonly decorations: "system" | "borderless";
  readonly systemButtons: "all" | "closeOnly" | "none";
  readonly level: "normal" | "floating" | "modal";
  readonly transparent: boolean;
  readonly blurBehind: boolean;
  readonly icon: Readonly<KadreBinaryImage> | null;
  readonly contentProtection: boolean;
}

export declare class KadreHostError extends Error {
  readonly failure: KadreFailure;
}

export interface KadreSessionHandle {
  readonly id: string;
  readonly state: KadreSessionSnapshot;
  subscribeState(observer: (state: KadreSessionSnapshot) => void): () => void;
  requestStop(): void;
  close(): void;
  awaitTermination(): Promise<KadreSessionOutcome>;
}

export interface KadreWebOptions {
  readonly policy?: KadrePolicyProfile;
  readonly attachmentPolicy?: "stopWhenDetached" | "manual";
  readonly windowProvider?: KadreWebWindowProvider;
}

export interface KadreWebWindowProvider {
  open(requestId: string, spec: Readonly<KadreWindowSpec>): KadreWebWindowOpenResult;
}

export interface KadreWebWindowHost {
  readonly element: HTMLElement;
  readonly attachmentPolicy?: "stopWhenDetached" | "manual";
}

export type KadreWebWindowOpenResult =
  | { readonly kind: "opened"; readonly host: Readonly<KadreWebWindowHost> }
  | { readonly kind: "rejected"; readonly failure: KadreFailure };

export declare const KadreWeb: {
  attach(
    element: HTMLElement,
    applicationFactory: KadreApplicationFactoryRef,
    options?: Readonly<KadreWebOptions>,
  ): KadreSessionHandle;
};
```

Les unions et readonly objects ci-dessus sont générés depuis les enums et le constructeur exact du catalogue ; aucune valeur additionnelle n’est admise. `KadreWebWindowProvider.open` reçoit une copie DTO, y compris une copie de `icon.bytes`, et retourne synchroniquement la discriminated union (union discriminée) ci-dessus. `rejected.failure` accepte exactement le set de `WindowRequestOutcome.Rejected`, dont `parentScopeCancelled` uniquement pour la scope du nouveau host retourné. Une exception callback est capturée et devient `platformFailure(web, "WebWindowProvider", "callback-exception")`; une failure hors set devient le même domain avec le code `"invalid-failure"`. La génération ultérieure est une preuve contre cette spec, pas une source de design.

`KadreWeb.attach` possède un `MainScope` interne au wrapper host et le cancel après terminaison. Cela n’introduit aucun global : chaque handle possède son scope. Un échec d’attach lève une `KadreHostError` dont `failure: KadreFailure`; les opérations asynchrones promise rejetteraient uniquement par cancellation/erreur de programmation, tandis qu’un outcome fonctionnel reste une value résolue.

`subscribeState` appelle synchroniquement l’observer avec `state`, puis sérialise les notifications dans la microtask queue. Une exception observer le désinscrit et est rethrow dans une microtask ; elle ne termine pas Kadre.

JS IR et Wasm produisent le même `.d.ts` et passent le même consumer TypeScript. Une différence de glue interne n’autorise pas une différence de noms, nullabilité ou `bigint`.

## 7. Escape hatches target-specific pour renderers

Kadre ne rend rien, mais un renderer externe peut avoir besoin de la surface native. Les seules APIs publiques de ce domaine sont des callbacks suspendus bornant la validité :

```kotlin
@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> HostSurface.withAndroidView(block: (android.view.View) -> R): KadreResult<R>

@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> HostSurface.withUIKitView(block: (platform.UIKit.UIView) -> R): KadreResult<R>

@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> HostSurface.withWebElement(block: (HTMLElement) -> R): KadreResult<R>

@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> Window.withDesktopHandle(block: (DesktopNativeWindowHandle) -> R): KadreResult<R>
```

```kotlin
public sealed interface DesktopNativeWindowHandle {
    public data class AppKit internal constructor(public val nsWindowAddress: ULong, public val nsViewAddress: ULong) : DesktopNativeWindowHandle
    public data class Win32 internal constructor(public val hwnd: ULong) : DesktopNativeWindowHandle
    public data class X11 internal constructor(public val displayAddress: ULong, public val window: ULong) : DesktopNativeWindowHandle
    public data class Wayland internal constructor(public val displayAddress: ULong, public val surfaceAddress: ULong) : DesktopNativeWindowHandle
}
```

Le callback s’exécute sur le thread host, n’est pas réentrant pour la même ressource et doit retourner avant une recreation/detach native committée. Kadre ne garantit plus la validité du type SDK ou des adresses après le callback. Le callback ne peut pas suspendre, bien que l’opération englobante soit suspendue pour marshaller vers le host. Toute exception callback est propagée telle quelle après libération du verrou de lifetime. Une cancellation avant le callback garantit qu’il n’est pas appelé ; après son début, il finit non-cancellable et son résultat n’est remis que si le waiter reste actif.

Dans une application Kotlin AppKit, le callback peut convertir les deux
adresses garanties non nulles et non zéro de `DesktopNativeWindowHandle.AppKit`
en objets KFFI et installer puis posséder une `NSVisualEffectView`. Elles sont
utilisables uniquement pendant le callback admis : aucune copie du handle ou
des adresses ne doit être retenue ni utilisée après son retour. Kadre attend une
lease déjà admise lors de la fermeture, et un appel ultérieur à
`withDesktopHandle` retourne `Closed(Window)`. Kadre ne possède ni la vue, ni
ses contraintes, ni un renderer; cette possibilité n'ajoute aucun handle aux
exports Swift, Java ou TypeScript.

Ces extensions sont Kotlin-only. Aucun pointer/adresse n’apparaît dans Swift, Java, TypeScript, diagnostics, equality ou snapshots communs.

## 8. Nommage et compatibilité

- Le module Swift s’appelle `KadreHost`; les types publics commencent par `Kadre`.
- Les façades Java utilisent des classes explicites, jamais le nom généré d’un fichier Kotlin `*Kt`.
- Le package npm est `@kadre/host`; aucune global browser variable n’est créée.
- Les noms de cases étrangers suivent lower camel case ; leur mapping vers la variante Kotlin est exhaustif et testé.
- Une modification de `.d.ts`, header Swift ou signature Java promise est traitée comme une modification de l’API publique pendant l’incubation et exige une entrée de migration.

## 9. Consumer compile tests obligatoires

| Consumer | Preuve minimale |
|---|---|
| Kotlin common | application coroutine utilisant lifecycle, surface, window, input et capture fake |
| Java Android | attach, observer state, request stop, await `CompletionStage`, inspect failure |
| Java Desktop | run standalone et inspect outcome |
| Swift UIKit | attach une factory Kotlin, observer state, `awaitTermination`, cancellation du waiter |
| SwiftUI | wrapper `UIViewControllerRepresentable` possédant l’host controller, sans layout Kadre |
| TypeScript JS | attach élément, subscribe/unsubscribe, stop, await outcome, exhaustive switch failure |
| TypeScript Wasm | même fichier source consumer que JS |
| renderer Kotlin par target | callback native valide, detach concurrent, accès interdit après callback |

Le test échoue si un `Flow`, `CoroutineScope`, `Continuation`, `Throwable`, `Any`, type FFI généré ou adresse native apparaît dans une façade host étrangère.

## 10. Audit de fermeture

- [x] Chaque famille du catalogue possède une décision par langage.
- [x] Swift, Java et JS/Wasm ont des façades host exactes.
- [x] Failures et outcomes conservent un mapping exhaustif et typé.
- [x] La factory étrangère est pass-through, pas implémentable par promesse v1.
- [x] Les APIs coroutine/Flow ne deviennent pas accidentellement un contrat étranger.
- [x] Les handles renderer sont bornés à un callback target-specific et absents des exports étrangers.
