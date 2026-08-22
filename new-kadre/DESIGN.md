# New Kadre — Architecture cible de l’API publique

**Statut :** snapshot de conception approuvé le 22 août 2026

**Portée :** toute l’API publique de Kadre

**Compatibilité :** breaking changes autorisés pendant l’incubation

**Référence d’exécution :** les plans détaillés dérivés de ce document doivent conserver ses invariants

## 1. Vision

Kadre devient une bibliothèque Kotlin Multiplatform idiomatique de gestion :

- des fenêtres et surfaces hôtes ;
- du lifecycle applicatif attaché à un hôte ;
- du clavier, du pointeur, du tactile, des gestes et des gamepads ;
- de la capture d’écran et de fenêtre ;
- de l’interop native strictement nécessaire aux renderers externes.

Kadre doit servir trois familles d’applications avec la même architecture :

- jeux et simulations temps réel ;
- sites et expériences Web embarquées ;
- utilitaires et applications desktop/mobile.

`winit` n’est plus une contrainte de compatibilité. Il a servi de bootstrap fonctionnel, mais ne dicte plus les noms, les structures ou les contrats publics.

## 2. Hors périmètre

Kadre ne fournit pas :

- de moteur de rendu ;
- d’abstraction Vulkan, Metal, OpenGL, WebGPU ou DirectX ;
- de widgets ;
- de layout ;
- de toolkit UI ;
- de moteur de jeu ;
- de boucle de simulation imposée à l’application.

Les handles natifs nécessaires à un renderer externe restent accessibles derrière une API d’opt-in.

## 3. Principes structurants

1. **Embeddable-first.** L’hôte natif possède le processus, la scène, l’`Activity`, la page ou la boucle principale. Kadre y attache une session.
2. **Coroutines-first.** `suspend`, `Flow`, `StateFlow` et structured concurrency constituent l’API commune principale.
3. **Une session, une hiérarchie.** Toute fenêtre, capture et ressource de périphérique appartient à une `KadreSession`.
4. **Aucun état global applicatif.** Aucun handler, session ou application courant n’est stocké dans un singleton public ou interne process-wide.
5. **Capabilities honnêtes.** Une absence de support est explicite ; aucun backend ne retourne un faux succès ou un no-op silencieux.
6. **Temps monotone.** Les durées, délais et timestamps d’événements n’utilisent pas l’heure Unix.
7. **Ownership explicite.** Les ressources natives, buffers de capture et sessions enfants ont une durée de vie définie et testable.
8. **Interop isolée.** Les détails FFI et handles bruts n’apparaissent pas dans l’API commune ordinaire.
9. **Buffers bornés.** Aucun flux de production ne dépend d’une file non bornée.
10. **Breaking changes directs.** L’incubation autorise la suppression de l’ancien modèle sans maintenir deux architectures en production.

## 4. Frontière des modules

### 4.1 Modules publics

- `kadre` : API KMP principale et contracts coroutine.
- `kadre-test` : fake host, horloge virtuelle, périphériques virtuels et contract-test fixtures.
- modules de plateforme : points d’attachement, options documentées, capabilities supplémentaires et interop sous opt-in.

### 4.2 Modules internes

- `kadre-core` devient un moteur interne non consommable directement. Il peut rester un module Gradle, mais ne constitue plus une seconde API.
- les modules FFI restent internes aux backends.
- les event loops, schedulers, registries, mappers et callbacks natifs sont `internal`.

### 4.3 Modules supprimés ou fusionnés

- `kadre-coroutines` est fusionné dans `kadre`.
- les typealiases de façade disparaissent.
- les deux anciennes classes `EventLoop` disparaissent.

### 4.4 Packages publics

```text
org.graphiks.kadre
├── application
├── window
├── input
├── capture
├── policy
├── diagnostics
└── platform
```

L’artifact `kadre-test` expose `org.graphiks.kadre.test`.

## 5. Application, scope et session

```kotlin
public fun interface KadreApplication {
    public suspend fun KadreScope.run()
}

public interface KadreScope : CoroutineScope {
    public val lifecycle: KadreLifecycle
    public val windows: WindowManager
    public val devices: DeviceManager
    public val capture: CaptureManager
    public val diagnostics: Flow<KadreDiagnostic>
}

public interface KadreSession : AutoCloseable {
    public val state: StateFlow<SessionState>

    public override fun close()
    public suspend fun stop(): KadreResult<Unit>
    public suspend fun join()
}
```

Contrats :

- `close()` déclenche une fermeture non bloquante et idempotente ;
- `stop()` attend le teardown et retourne son résultat ;
- `join()` attend uniquement la terminaison ;
- une exception non gérée de `KadreApplication.run` termine la session ;
- fermer la session annule toute sa hiérarchie coroutine.

## 6. Host SPI

```kotlin
public interface KadreHost {
    public val platform: KadrePlatform
    public val capabilities: StateFlow<HostCapabilities>

    public fun attach(
        parentScope: CoroutineScope,
        application: KadreApplication,
        policy: KadrePolicy,
    ): KadreSession
}
```

Les adaptateurs officiels implémentent ce SPI. `kadre-test` l’utilise pour exécuter exactement les mêmes contrats sans backend natif.

## 7. Lifecycle

```kotlin
public interface KadreLifecycle {
    public val state: StateFlow<LifecycleState>
    public val events: Flow<LifecycleEvent>
}

public enum class LifecycleState {
    Attached,
    Foreground,
    Active,
    Inactive,
    Background,
    Detached,
}
```

`state` expose la valeur courante. `events` conserve les transitions. Les backends dédupliquent les notifications natives répétées.

## 8. Delivery policies et backpressure

```kotlin
public data class KadrePolicy(
    public val execution: ExecutionPolicy,
    public val windowEvents: EventDeliveryPolicy,
    public val input: InputDeliveryPolicy,
    public val capture: CaptureDeliveryPolicy,
    public val diagnostics: DiagnosticPolicy,
)

public object KadrePolicies {
    public val Default: KadrePolicy
    public val Realtime: KadrePolicy
    public val Recording: KadrePolicy
}
```

Catégories :

- état durable : `StateFlow`, conflation sur l’état courant ;
- transitions discrètes : ordre préservé, buffer borné, aucune perte silencieuse ;
- données continues : coalescing ou buffer borné selon policy ;
- capture : policy indépendante de l’input.

Les policies publiques expriment des garanties métier (`Latest`, `Buffered`, `Coalesced`, `FailOnOverflow`) et non les détails de `MutableSharedFlow`.

```kotlin
public sealed interface ContinuousDelivery {
    public data object Latest : ContinuousDelivery
    public data object Coalesced : ContinuousDelivery
    public data class Buffered(
        public val capacity: Int,
        public val onOverflow: ContinuousOverflowAction,
    ) : ContinuousDelivery
}

public enum class DiscreteOverflowAction {
    CloseSource,
    FailSession,
}

public enum class ContinuousOverflowAction {
    DropOldestAndReport,
    DropLatestAndReport,
    CloseSource,
    FailSession,
}

public data class InputDeliveryPolicy(
    public val discreteBufferCapacity: Int,
    public val discreteOverflow: DiscreteOverflowAction,
    public val pointerMotion: ContinuousDelivery,
    public val scroll: ContinuousDelivery,
    public val gamepadChanges: ContinuousDelivery,
)

public data class CaptureDeliveryPolicy(
    public val frames: ContinuousDelivery,
)
```

Profils :

- `Default` : états conflated, transitions préservées, mouvements coalescés, capture `Latest` ;
- `Realtime` : latence minimale, transitions prioritaires, conflation agressive des données continues et buffers minimaux ;
- `Recording` : pas de coalescing, buffers bornés plus grands, overflow explicite.

Les trois profils sont des valeurs immuables servant de point de départ. Une application peut les spécialiser avec `copy` sans modifier un singleton global :

```kotlin
val policy = KadrePolicies.Default.copy(
    input = KadrePolicies.Default.input.copy(
        discreteBufferCapacity = 512,
        pointerMotion = ContinuousDelivery.Buffered(
            capacity = 128,
            onOverflow = ContinuousOverflowAction.DropOldestAndReport,
        ),
    ),
    capture = KadrePolicies.Default.capture.copy(
        frames = ContinuousDelivery.Latest,
    ),
)
```

Les capacités de buffer sont strictement positives et validées à la construction. Une transition discrète ne peut choisir que `CloseSource` ou `FailSession` ; les données continues peuvent aussi choisir `DropOldestAndReport` ou `DropLatestAndReport`. Il n’existe pas de variante `Unlimited`.

Les callbacks natifs ne sont jamais bloqués pour promettre un lossless impossible. Toute perte produit un diagnostic typé ou ferme la source selon la policy.

## 9. Fenêtres

```kotlin
public interface WindowManager {
    public val primary: StateFlow<Window?>
    public val windows: StateFlow<List<Window>>
    public val capabilities: StateFlow<WindowManagerCapabilities>

    public suspend fun requestWindow(
        spec: WindowSpec = WindowSpec(),
    ): KadreResult<WindowRequestOutcome>
}

public suspend fun WindowManager.requestWindow(
    configure: WindowSpecBuilder.() -> Unit,
): KadreResult<WindowRequestOutcome>
```

```kotlin
public sealed interface WindowRequestOutcome {
    public data class Opened(public val window: Window) : WindowRequestOutcome
    public data class AcceptedByHost(public val requestId: WindowRequestId) : WindowRequestOutcome
}
```

- Desktop et Web peuvent retourner `Opened`.
- UIKit peut retourner `AcceptedByHost` lorsqu’une nouvelle `UIWindowScene` doit être créée.
- Une plateforme sans multi-window retourne `KadreFailure.Unsupported`.

### 9.1 WindowSpec

`WindowSpec` est un snapshot immuable créé par constructeur ou DSL. Il remplace la longue `data class WindowAttributes` évolutive.

```kotlin
val result = scope.windows.requestWindow {
    title = "Kadre"
    size = LogicalSize(1280.0, 720.0)
    resizable = true
}
```

### 9.2 Window

```kotlin
public interface Window {
    public val id: WindowId
    public val state: StateFlow<WindowState>
    public val capabilities: StateFlow<WindowCapabilities>
    public val events: Flow<WindowEvent>
    public val input: WindowInput

    public suspend fun apply(update: WindowUpdate): KadreResult<WindowUpdateOutcome>
    public suspend fun close(): KadreResult<Unit>
}
```

`WindowState` est un snapshot cohérent et atomique. Les mutations suspendues effectuent le marshalling vers le thread hôte. Les opérations contextuelles conservent des verbes dédiés : pointer lock, system drag, attention utilisateur.

### 9.3 Capabilities

```kotlin
public sealed interface FeatureSupport {
    public data object Supported : FeatureSupport
    public data class Unsupported(public val reason: String) : FeatureSupport
    public data class RequiresPermission(public val permission: KadrePermission) : FeatureSupport
    public data class RequiresUserGesture(public val reason: String) : FeatureSupport
    public data class TemporarilyUnavailable(public val reason: String) : FeatureSupport
}
```

Les capabilities sont prédictives ; le résultat de l’opération reste l’autorité finale.

## 10. Périphériques et input

```kotlin
public interface DeviceManager {
    public val devices: StateFlow<List<InputDevice>>
    public val events: Flow<DeviceLifecycleEvent>
    public val gamepads: StateFlow<List<Gamepad>>

    public fun device(id: DeviceId): InputDevice?
    public fun gamepad(id: GamepadId): Gamepad?
}
```

Les identifiants sont des `value class` opaques avec constructeur interne. Les événements utilisent un timestamp monotone exprimé en `Duration` depuis le début de la session.

### 10.1 Input par fenêtre

```kotlin
public interface WindowInput {
    public val events: Flow<InputEvent>
    public val keyboardState: StateFlow<KeyboardState>
    public val pointers: StateFlow<List<PointerState>>
    public val modifiers: StateFlow<KeyboardModifiers>
    public val capabilities: StateFlow<InputCapabilities>
}
```

Le flux unique conserve l’ordre entre événements clavier, pointeur, tactile et gestes. Des extensions filtrées fournissent les vues spécialisées.

### 10.2 Gamepad

```kotlin
public interface Gamepad {
    public val id: GamepadId
    public val descriptor: StateFlow<GamepadDescriptor>
    public val connection: StateFlow<DeviceConnectionState>
    public val state: StateFlow<GamepadState>
    public val events: Flow<GamepadEvent>
    public val capabilities: StateFlow<GamepadCapabilities>

    public suspend fun playEffect(effect: GamepadEffect): KadreResult<Unit>
    public suspend fun stopEffects(): KadreResult<Unit>
}
```

Un code natif inconnu reste inconnu. Aucun ordinal invalide n’est transformé en bouton ou axe arbitraire.

### 10.3 IME

```kotlin
public suspend fun WindowInput.openTextInput(
    config: TextInputConfig,
): KadreResult<TextInputSession>

public interface TextInputSession : AutoCloseable {
    public val events: Flow<TextInputEvent>
    public val state: StateFlow<TextInputState>

    public suspend fun updateCursor(rect: PhysicalRect<Int>): KadreResult<Unit>
    public suspend fun updateSurroundingText(
        text: String,
        selection: TextRange,
    ): KadreResult<Unit>
}
```

Fermer la fenêtre ou la session ferme les sessions IME enfants.

### 10.4 Raw input

Le raw input est demandé explicitement, soumis aux permissions et marqué `@DelicateKadreApi` lorsqu’il contourne les protections ordinaires de focus.

## 11. Capture

```kotlin
public interface CaptureManager {
    public val permission: StateFlow<CapturePermission>
    public val capabilities: StateFlow<CaptureCapabilities>
    public val sources: StateFlow<CaptureSources>

    public suspend fun requestPermission(): KadreResult<CapturePermission>
    public suspend fun refreshSources(): KadreResult<CaptureSources>
    public suspend fun open(request: CaptureRequest): KadreResult<CaptureSession>
}
```

```kotlin
public interface CaptureSession : AutoCloseable {
    public val source: CaptureSource
    public val state: StateFlow<CaptureSessionState>
    public val frames: Flow<CaptureFrame>
    public val diagnostics: Flow<CaptureDiagnostic>

    public suspend fun stop(): KadreResult<Unit>
}
```

### 11.1 Frames

```kotlin
public interface CaptureFrame : AutoCloseable {
    public val size: PhysicalSize<Int>
    public val format: PixelFormat
    public val planes: List<PixelPlane>
    public val timestamp: Duration
    public val sequence: Long

    public fun copyPixels(): ByteArray
}
```

Une frame remise au consommateur doit être fermée. Kadre ferme automatiquement les frames écartées par une policy. Le zero-copy est réservé à `@KadrePlatformApi`.

## 12. Résultats et erreurs

```kotlin
public sealed interface KadreResult<out T> {
    public data class Success<T>(public val value: T) : KadreResult<T>
    public data class Failure(public val reason: KadreFailure) : KadreResult<Nothing>
}
```

```kotlin
public sealed interface KadreFailure {
    public val message: String

    public data class Unsupported(
        public val operation: String,
        public override val message: String,
    ) : KadreFailure

    public data class PermissionDenied(
        public val permission: KadrePermission,
        public override val message: String,
    ) : KadreFailure

    public data class TemporarilyUnavailable(
        public val retryable: Boolean,
        public override val message: String,
    ) : KadreFailure

    public data class SourceLost(
        public val source: CaptureSourceId,
        public override val message: String,
    ) : KadreFailure

    public data class PlatformFailure(
        public val platform: KadrePlatform,
        public val code: String?,
        public override val message: String,
        public val cause: Throwable? = null,
    ) : KadreFailure
}
```

Les `CancellationException` ne sont jamais encapsulées. Les erreurs de programmation utilisent les exceptions standard Kotlin. Les limites attendues utilisent `KadreResult`.

## 13. Diagnostics

`KadreDiagnostic` matérialise au minimum :

- `EventLoss` ;
- `CapabilityChanged` ;
- `BackendFallback` ;
- `SlowConsumer`.

Aucun module de bibliothèque n’utilise `println` comme mécanisme de diagnostic public.

## 14. Structured concurrency et threading

```text
KadreSession Job
├── KadreApplication.run
├── lifecycle bridge
├── WindowManager
│   └── branches Window
├── DeviceManager
│   └── branches InputDevice
└── CaptureManager
    └── branches CaptureSession
```

- Le job racine est un `Job` ordinaire : une erreur applicative non gérée termine la session.
- Une erreur locale correctement matérialisée ferme seulement la ressource concernée.
- Les fonctions publiques suspendues sont appelables depuis toute coroutine.
- Les backends marshallent vers leur thread propriétaire.
- Le code des collectors s’exécute dans le contexte du collector, jamais arbitrairement dans un callback natif.
- Aucun `GlobalScope` ni job détaché.

## 15. Adaptateurs de plateforme

### 15.1 Android

- `ComponentActivity.attachKadre(...)` et `View.attachKadre(...)`.
- Session liée au `LifecycleOwner`.
- Aucun `AndroidKadreRuntime.currentHandler`.
- Une intégration Compose attache la session sans fournir de widget ni rendu.

### 15.2 UIKit et SwiftUI

- Une session par `UIWindowScene`.
- `UISceneDelegate` alimente lifecycle et fermeture.
- `sceneDidDisconnect` annule la hiérarchie.
- Bridge Swift minimal `KadreIos.attach(windowScene, application)`.
- SwiftUI utilise un `UIViewControllerRepresentable` hôte.
- Une fenêtre iPadOS supplémentaire crée une nouvelle scène et une nouvelle session.

### 15.3 Web

- Attachement à un `HTMLElement` ou `HTMLCanvasElement` existant.
- Aucun détournement du titre comme ID DOM.
- Sessions multiples possibles sur une même page.
- Même contrat public en JS et Wasm.
- Aucun faux `runApp` bloquant.

### 15.4 Desktop

- `attachKadreDesktop(...)` pour le modèle session.
- `runKadreApplication(...)` comme commodité standalone.
- Sélection typée AppKit, Win32, X11 ou Wayland.
- Un échec après démarrage ne déclenche pas un fallback silencieux.

## 16. Interop plateforme

```kotlin
@RequiresOptIn
public annotation class ExperimentalKadreApi

@RequiresOptIn
public annotation class KadrePlatformApi

@RequiresOptIn
public annotation class DelicateKadreApi
```

Les handles :

- ont un constructeur interne ;
- n’exposent aucun `Any` dans l’API commune ;
- sont valides uniquement tant que leur propriétaire est vivant ;
- utilisent des types spécifiques dans les source sets de plateforme ;
- ne laissent jamais fuiter les types FFI générés.

## 17. Testabilité

`kadre-test` fournit :

- `runKadreTest` ;
- `FakeKadreHost` ;
- `VirtualKadreClock` ;
- `VirtualWindow` ;
- `VirtualInputController` ;
- `VirtualCaptureController` ;
- des scénarios d’overflow déterministes ;
- une suite de contrats réutilisable par tous les backends.

Chaque backend valide les mêmes invariants de lifecycle, threading, capabilities, fermeture, flux, handles et permissions.

## 18. Performance

Invariants mesurables :

- aucun buffer non borné ;
- aucune perte silencieuse de transition discrète ;
- aucune coroutine orpheline après teardown ;
- frames abandonnées toujours fermées ;
- coût mesuré avec zéro, un et plusieurs collectors ;
- benchmarks pour input haute fréquence, gamepad, état fenêtre et capture.

Les budgets chiffrés seront dérivés d’une baseline mesurée avant migration.

## 19. Politique d’incubation

Tous les modules publiés utilisent `explicitApi()`.

La migration supprime directement :

- `ApplicationHandler` ;
- les deux `EventLoop` ;
- `WindowAttributes` ;
- les setters et résultats winit-like ;
- les IDs incohérents ;
- les buffers publics mutables ;
- la façade par typealiases ;
- le module `kadre-coroutines` séparé.

Un guide de migration est fourni, sans shim permanent ni maintien d’une v1 parallèle.

## 20. Invariants d’acceptation globaux

La refonte est terminée lorsque :

1. un utilitaire, un jeu et un site Web utilisent la même `KadreApplication` coroutine ;
2. chaque plateforme possède un host adapter honnête ;
3. aucun contrat commun ne change de sémantique selon la cible ;
4. aucune limitation de plateforme n’est un faux succès ;
5. toutes les ressources appartiennent à une session et se ferment avec elle ;
6. les contract tests sont verts sur tous les backends disponibles ;
7. les dumps ABI ne contiennent aucun type de backend interne ;
8. les samples et la documentation n’utilisent plus l’ancienne API ;
9. `kadre-core` n’est plus importé par les consommateurs ;
10. les avertissements d’opt-in expérimentaux sont traités localement et intentionnellement.
