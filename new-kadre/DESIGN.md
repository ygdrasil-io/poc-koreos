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

public fun interface KadreApplicationFactory {
    public fun create(context: KadreLaunchContext): KadreApplication
}

public data class KadreLaunchContext(
    public val sessionId: SessionId,
    public val reason: KadreLaunchReason,
    public val originatingRequestId: WindowRequestId?,
)

public enum class KadreLaunchReason {
    InitialHostAttachment,
    AdditionalHostRequested,
    HostRestoration,
}

public interface KadreScope : CoroutineScope {
    public val sessionId: SessionId
    public val policy: KadrePolicy
    public val lifecycle: KadreLifecycle
    public val windows: WindowManager
    public val devices: DeviceManager
    public val capture: CaptureManager
    public val diagnostics: KadreDiagnostics
}

public interface KadreSession : AutoCloseable {
    public val id: SessionId
    public val state: StateFlow<SessionState>

    public override fun close()
    public fun requestStop()
    public suspend fun awaitTermination(): SessionOutcome
}

public sealed interface SessionState {
    public data object Starting : SessionState
    public data object Running : SessionState
    public data object Stopping : SessionState
    public data class Terminated(public val outcome: SessionOutcome) : SessionState
}

public sealed interface SessionOutcome {
    public data object Completed : SessionOutcome
    public data class Stopped(public val reason: SessionStopReason) : SessionOutcome
    public data class Failed(public val failure: KadreFailure) : SessionOutcome
}

public enum class SessionStopReason {
    Requested,
    ParentCancelled,
    HostDetached,
}
```

Contrats :

- `close()` délègue à `requestStop()` ; les deux sont non bloquants, thread-safe et idempotents ;
- `awaitTermination()` est idempotent et retourne toujours le même résultat terminal ;
- appeler `awaitTermination()` depuis un job enfant de la session échoue immédiatement avec `IllegalStateException` au lieu de deadlocker ;
- le retour normal de `KadreApplication.run` produit `SessionOutcome.Completed` et déclenche le teardown ;
- `requestStop()` propose `Stopped(Requested)` ; le premier motif d’arrêt non fatal accepté fait autorité ;
- l’annulation du `parentScope` produit `Stopped(ParentCancelled)` ;
- le détachement définitif de l’hôte produit `Stopped(HostDetached)` ;
- une exception non gérée produit un diagnostic fatal, devient un `KadreFailure.ApplicationFailure` stable et termine la session avec `Failed` ;
- une exception de `KadreApplicationFactory.create` suit exactement le même chemin d’échec ;
- une factory crée exactement une instance de `KadreApplication` par session et peut être invoquée simultanément pour plusieurs scènes ;
- un consumer qui capture de l’état mutable dans la factory reste responsable de sa synchronisation entre sessions.

Une erreur non liée à la cancellation, observée avant `Terminated`, surclasse un motif `Stopped` ou `Completed` et produit `Failed`. Les `CancellationException` déclenchées par le teardown et les erreurs secondaires de cleanup ne remplacent pas le résultat ; ces dernières sont attachées au diagnostic fatal principal.

La session passe à `Running` immédiatement avant l’appel de `KadreApplication.run`, une fois lifecycle et managers initialisés. Le code applicatif ne peut donc jamais observer un manager partiellement construit.

Le teardown suit cet ordre normatif : fermeture de l’admission des callbacks, annulation du job applicatif, arrêt des captures et sessions IME, arrêt des effets de périphériques, annulation des requêtes de fenêtre, fermeture des fenêtres en ordre inverse de création, détachement des bridges natifs, puis attente de tous les enfants. Aucun callback ne peut réintroduire une ressource après la fermeture de l’admission.

## 6. Host SPI

```kotlin
@ExperimentalKadreApi
public interface KadreHost {
    public val platform: KadrePlatform
    public val capabilities: StateFlow<HostCapabilities>

    public fun attach(
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
    ): KadreResult<KadreSession>
}
```

`attach` valide synchroniquement le host, le `parentScope` et la policy, puis retourne sans attendre l’exécution applicative. Un succès contient une session en état `Starting`; tout échec ultérieur devient son `SessionOutcome`. Un parent déjà annulé retourne `KadreFailure.ParentScopeCancelled` sans créer de session.

Les adaptateurs officiels implémentent ce SPI. `kadre-test` l’utilise pour exécuter exactement les mêmes contrats sans backend natif. L’annotation expérimentale permet les backends tiers pendant l’incubation sans transformer immédiatement le SPI en garantie stable.

Les adaptateurs mono-session offrent une surcharge acceptant directement un `KadreApplication`. Les hosts capables de créer une scène ou activité supplémentaire exigent une `KadreApplicationFactory`; Kadre ne réutilise jamais silencieusement une instance applicative dans deux sessions.

## 7. Lifecycle

```kotlin
public interface KadreLifecycle {
    public val state: StateFlow<LifecycleState>
    public val events: Flow<LifecycleEvent>
}

public data class LifecycleState(
    public val attachment: AttachmentState,
    public val visibility: VisibilityState,
    public val activation: ActivationState,
)

public enum class AttachmentState {
    Attached,
    Detached,
}

public enum class VisibilityState {
    Foreground,
    Background,
}

public enum class ActivationState {
    Active,
    Inactive,
}

public data class LifecycleEvent(
    public val previous: LifecycleState,
    public val current: LifecycleState,
    public val stamp: EventStamp,
)
```

Les trois axes sont orthogonaux. `Detached` est terminal et impose le snapshot canonique `Detached + Background + Inactive`. Une transition vers `Background` publie toujours `Inactive` auparavant ou dans le même snapshot atomique. Une transition vers `Active` exige `Attached + Foreground`. Les backends dédupliquent les notifications répétées et ne publient que des snapshots valides.

Le `StateFlow` contient dès la création le snapshot courant du host, toujours `Attached`; cette valeur initiale n’est pas rejouée dans `events`. Elle est disponible avant le passage de la session à `Running`.

Mapping normatif :

| Host | `Foreground` | `Active` | `Detached` |
|---|---|---|---|
| Android | `LifecycleOwner` au moins `STARTED` | au moins `RESUMED` et fenêtre interactive | `LifecycleOwner.onDestroy` |
| UIKit | scène en foreground | `sceneDidBecomeActive` | `sceneDidDisconnect` |
| Web | document visible et élément connecté | document actif et élément recevant l’input | détachement selon la policy Web |
| Desktop | au moins une fenêtre de session visible, ou host embarqué déclaré visible | au moins une fenêtre de session active | fermeture explicite du host |

`state` est mis à jour avant que `events` rende la transition observable. Un collector peut donc voir un état plus récent, mais jamais un état antérieur à l’événement reçu.

### 7.1 Contrats normatifs des flux

```kotlin
public data class EventStamp(
    public val sequence: SessionSequence,
    public val timestamp: SessionInstant,
)

public value class SessionSequence internal constructor(public val value: Long)
public value class SessionInstant internal constructor(public val sinceStart: Duration)

public interface KadreDiagnostics {
    public val events: Flow<KadreDiagnostic>
    public val counters: StateFlow<DiagnosticCounters>
}
```

`SessionSequence` est strictement croissante dans une session. `SessionInstant` utilise la même origine monotone pour lifecycle, fenêtres, input, gamepads et capture ; deux timestamps de sessions différentes ne sont pas directement comparables.

| Catégorie | Température | Replay | Cardinalité | Règle d’ownership |
|---|---|---|---|---|
| `StateFlow` | hot | dernière valeur | multicast | valeur immuable détenue par Kadre |
| transitions lifecycle/window/device | hot | aucun | multicast | événement immuable estampillé |
| input et gamepad | hot | aucun | multicast | événement immuable estampillé |
| diagnostics | hot | aucun | multicast | les compteurs restent disponibles même si un événement est manqué |
| `collectFrames` | streaming structuré | aucun | un collector actif | lease fermée automatiquement après le bloc collector |

Collecter un flux d’événements ne démarre ni n’arrête sa source. Chaque collector possède une file bornée ; un collector lent est traité selon la policy sans bloquer les callbacks natifs ni les autres collectors. Tous les événements publics portent un `EventStamp`. Lorsqu’un événement correspond à un snapshot, le `StateFlow` est mis à jour avant sa publication.

Les frames constituent l’unique exception au modèle `Flow` multicast, car une ressource closeable ne peut pas avoir plusieurs owners implicites. Elles utilisent une opération suspendue structurée décrite en section 11.

## 8. Delivery policies et backpressure

```kotlin
public data class KadrePolicy(
    public val execution: ExecutionPolicy,
    public val lifecycleEvents: EventDeliveryPolicy,
    public val windowEvents: EventDeliveryPolicy,
    public val deviceEvents: EventDeliveryPolicy,
    public val input: InputDeliveryPolicy,
    public val capture: CaptureDeliveryPolicy,
    public val diagnostics: DiagnosticPolicy,
)

public object KadrePolicies {
    public val Default: KadrePolicy
    public val Realtime: KadrePolicy
    public val Recording: KadrePolicy
}

public data class ExecutionPolicy(
    public val priority: ExecutionPriority,
    public val shutdownTimeout: Duration,
)

public enum class ExecutionPriority {
    Balanced,
    LatencyFirst,
    Throughput,
}

public data class EventDeliveryPolicy(
    public val ingressCapacity: Int,
    public val collectorCapacity: Int,
    public val ingressOverflow: IngressOverflowAction,
    public val collectorOverflow: CollectorOverflowAction,
)

public data class DiagnosticPolicy(
    public val eventBufferCapacity: Int,
    public val eventOverflow: DiagnosticOverflowAction,
)

public enum class DiagnosticOverflowAction {
    DropOldestEvent,
    DropLatestEvent,
}
```

Catégories :

- état durable : `StateFlow`, conflation sur l’état courant ;
- transitions discrètes : ordre préservé, buffer borné, aucune perte silencieuse ;
- données continues : coalescing ou buffer borné selon policy ;
- capture : policy indépendante de l’input.

Les policies publiques expriment des garanties métier (`Latest`, `Buffered`, `Coalesced`) et non les détails de `MutableSharedFlow`.

```kotlin
public sealed interface ContinuousDelivery {
    public data object Latest : ContinuousDelivery
    public data object Coalesced : ContinuousDelivery
    public data class Buffered(
        public val capacity: Int,
        public val onOverflow: ContinuousOverflowAction,
    ) : ContinuousDelivery
}

public enum class IngressOverflowAction {
    CloseSource,
    FailSession,
}

public enum class CollectorOverflowAction {
    CancelSlowCollector,
    CloseSource,
    FailSession,
}

public enum class ContinuousOverflowAction {
    DropOldestAndReport,
    DropLatestAndReport,
    CloseSource,
    FailSession,
}

public class SlowCollectorCancellationException internal constructor(
    message: String,
) : CancellationException(message)

public data class InputDeliveryPolicy(
    public val discreteEvents: EventDeliveryPolicy,
    public val pointerMotion: ContinuousDelivery,
    public val scroll: ContinuousDelivery,
    public val gamepadChanges: ContinuousDelivery,
)

public data class CaptureDeliveryPolicy(
    public val frames: ContinuousDelivery,
)
```

Profils :

| Profil | Transitions discrètes | Données continues | Capture | Overflow |
|---|---|---|---|---|
| `Default` | capacité 256 | mouvements et scroll coalescés, gamepad `Latest` | `Latest` | collector lent annulé explicitement |
| `Realtime` | capacité 64 et scheduling prioritaire | coalescing à chaque tour du host, gamepad `Latest` | `Latest` | collector lent annulé explicitement |
| `Recording` | capacité 8192 | `Buffered(8192, FailSession)` | `Buffered(3, CloseSource)` | aucune perte ajoutée par Kadre ; arrêt explicite plutôt que drop |

La capacité de transitions du tableau est utilisée pour l’ingress et pour chaque collector des lanes lifecycle, window, device et input.

Pour `Default` et `Realtime`, l’ingress lifecycle utilise `FailSession`, les ingress window/device/input utilisent `CloseSource`, et les collectors utilisent `CancelSlowCollector`. `Recording` utilise `FailSession` à tous les niveaux afin qu’un enregistrement incomplet ne ressemble jamais à un succès.

`Default` utilise `Balanced`, un shutdown de 5 secondes et 256 diagnostics détaillés. `Realtime` utilise `LatencyFirst`, 2 secondes et 64 diagnostics. `Recording` utilise `Throughput`, 30 secondes et 8192 diagnostics. L’overflow des événements de diagnostic supprime le plus ancien événement détaillé, tandis que les compteurs restent exacts.

Les trois profils sont des valeurs immuables servant de point de départ. Une application peut les spécialiser avec `copy` sans modifier un singleton global :

```kotlin
val policy = KadrePolicies.Default.copy(
    input = KadrePolicies.Default.input.copy(
        discreteEvents = KadrePolicies.Default.input.discreteEvents.copy(
            collectorCapacity = 512,
        ),
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

Les capacités de buffer et `shutdownTimeout` sont strictement positifs et validés à la construction. L’ingress discret choisit `CloseSource` ou `FailSession`; chaque collector peut aussi choisir `CancelSlowCollector`. Les données continues peuvent choisir `DropOldestAndReport`, `DropLatestAndReport`, `CloseSource` ou `FailSession`. Il n’existe pas de variante `Unlimited`.

Chaque source possède une file d’ingress bornée avant le fan-out, puis chaque collector possède sa propre file bornée. `CloseSource` ferme l’owner du flux saturé : la session pour lifecycle, la fenêtre pour ses événements, la branche `WindowInput` pour son input, le périphérique pour son flux propre et la `CaptureSession` pour ses frames. `CancelSlowCollector` termine uniquement le collector concerné avec `SlowCollectorCancellationException`.

`ExecutionPriority` influence uniquement les workers internes et leur cadence de réveil. Les dispatchers UI/main propriétaires restent imposés par le host et ne sont jamais remplaçables par une policy applicative. `shutdownTimeout` borne le teardown ; son dépassement ferme les bridges encore actifs, produit un diagnostic fatal et termine la session en `Failed`.

`Coalesced` a une sémantique propre au type : une position absolue conserve la dernière valeur, un mouvement relatif ou scroll additionne tous les deltas, et une valeur analogique conserve la dernière valeur par contrôle. `Latest` ferme la valeur remplacée lorsqu’elle possède une ressource.

La policy exposée par `KadreScope.policy` est celle effectivement garantie. Un host incapable de l’honorer échoue pendant `attach` avec `UnsupportedPolicy`; aucun profil n’est dégradé silencieusement. `Recording` garantit seulement l’absence de perte ajoutée par Kadre après l’ingress natif ; une coalescence ou perte imposée en amont par l’OS est signalée lorsqu’elle est détectable.

Les callbacks natifs ne sont jamais bloqués pour promettre un lossless impossible. Toute perte incrémente synchroniquement un compteur typé dans `KadreDiagnostics.counters`, puis tente d’émettre un diagnostic détaillé. La garantie ne dépend donc jamais de la livraison du flux de diagnostics lui-même.

## 9. Fenêtres

```kotlin
public interface WindowManager {
    public val primary: StateFlow<Window?>
    public val windows: StateFlow<List<Window>>
    public val capabilities: StateFlow<WindowManagerCapabilities>

    public suspend fun requestWindow(
        spec: WindowSpec = WindowSpec(),
    ): KadreResult<WindowRequest>
}

public suspend fun WindowManager.requestWindow(
    configure: WindowSpecBuilder.() -> Unit,
): KadreResult<WindowRequest>
```

```kotlin
public interface WindowRequest : AutoCloseable {
    public val id: WindowRequestId
    public val state: StateFlow<WindowRequestState>

    public override fun close()
    public fun cancel()
    public suspend fun await(): WindowRequestState
}

public sealed interface WindowRequestState {
    public data object Pending : WindowRequestState
    public data class OpenedHere(public val window: Window) : WindowRequestState
    public data class OpenedInNewSession(
        public val sessionId: SessionId,
        public val windowId: WindowId,
    ) : WindowRequestState
    public data class Rejected(public val failure: KadreFailure) : WindowRequestState
    public data object Cancelled : WindowRequestState
}
```

- `await()` attend un état terminal et retourne toujours la même valeur ensuite.
- `close()` délègue à `cancel()` ; annuler une requête déjà terminée est un no-op.
- Desktop et Web peuvent atteindre `OpenedHere`.
- UIKit atteint `OpenedInNewSession` lorsque l’OS connecte la nouvelle `UIWindowScene` et que sa session est attachée avec la factory du host.
- La nouvelle application reçoit `KadreLaunchReason.AdditionalHostRequested` et l’`originatingRequestId`.
- Une plateforme sans multi-window crée une requête immédiatement `Rejected(Unsupported)` ; elle ne retourne jamais un faux succès.
- Fermer la session demandeuse annule ses requêtes encore `Pending`, sans fermer une nouvelle session déjà ouverte.

`WindowManager.windows` ne contient que les fenêtres appartenant à la session courante. Une fenêtre annoncée par `OpenedInNewSession` n’y apparaît jamais. `primary` désigne la fenêtre ou surface fournie par le host courant et peut rester `null` pour une session headless ; Kadre ne crée pas automatiquement une fenêtre pour rendre ce champ non nul.

### 9.1 WindowSpec

`WindowSpec` est un snapshot immuable créé par constructeur ou DSL. Il remplace la longue `data class WindowAttributes` évolutive. Le `WindowSpecBuilder` est éphémère, non thread-safe et ne doit pas être conservé après le retour du bloc.

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

public sealed interface WindowUpdateOutcome {
    public data class Applied(public val state: WindowState) : WindowUpdateOutcome
    public data class PartiallyApplied(
        public val state: WindowState,
        public val rejected: List<RejectedWindowField>,
    ) : WindowUpdateOutcome
    public data class Accepted(public val operationId: WindowOperationId) : WindowUpdateOutcome
}

public data class RejectedWindowField(
    public val field: WindowProperty,
    public val failure: KadreFailure,
)

public value class WindowProperty(public val value: String)
```

`WindowState` est le snapshot effectif, cohérent et atomique ; il ne représente jamais simplement la valeur demandée. Les mutations suspendues effectuent le marshalling vers le thread hôte.

`Window.apply` n’est pas transactionnel : les plateformes natives ne peuvent pas garantir un rollback atomique d’un lot de propriétés. Toute application partielle énumère les champs rejetés et leurs `KadreFailure`. `Accepted` signifie que le host a accepté une opération visuellement asynchrone ; l’achèvement est observé dans `Window.state` et `Window.events` avec le même `operationId`. Une fermeture est idempotente et rend la fenêtre terminale.

Les opérations contextuelles conservent des verbes dédiés au lieu d’être cachées dans `WindowUpdate` : pointer lock, system drag et attention utilisateur.

### 9.3 Capabilities

```kotlin
public sealed interface FeatureAvailability {
    public data object Available : FeatureAvailability
    public data class RequiresPermission(public val permission: KadrePermission) : FeatureAvailability
    public data class RequiresUserGesture(public val reason: String) : FeatureAvailability
    public data class Unavailable(
        public val reason: String,
        public val retryable: Boolean,
    ) : FeatureAvailability
}

public sealed interface Capability<out Constraints> {
    public data class Unsupported(public val reason: String) : Capability<Nothing>
    public data class Supported<Constraints>(
        public val constraints: Constraints,
        public val availability: FeatureAvailability,
    ) : Capability<Constraints>
}

public data class WindowSizeConstraints(
    public val minimum: LogicalSize<Double>?,
    public val maximum: LogicalSize<Double>?,
    public val increments: LogicalSize<Double>?,
)
```

Chaque champ de `WindowCapabilities` utilise une `Capability` avec des contraintes spécifiques : tailles, modes fullscreen, curseurs, décorations ou transparence. `Unsupported` décrit l’absence structurelle ; `Supported` contient les préconditions dynamiques et le domaine accepté. Une fonctionnalité sans contrainte utilise `Unit`. Les capabilities sont prédictives ; le résultat de l’opération reste l’autorité finale.

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

Les identifiants sont des `value class` opaques avec constructeur interne. Leur unicité et leur validité sont limitées à une `KadreSession`; ils ne servent pas d’identifiants persistants entre lancements. Tous les événements portent l’`EventStamp` de la session.

### 10.1 Input par fenêtre

```kotlin
public interface WindowInput {
    public val events: Flow<InputEvent>
    public val keyboardState: StateFlow<KeyboardState>
    public val pointers: StateFlow<List<PointerState>>
    public val modifiers: StateFlow<KeyboardModifiers>
    public val capabilities: StateFlow<InputCapabilities>
}

public sealed interface InputEvent {
    public val stamp: EventStamp
    public val deviceId: DeviceId?
}
```

Le flux unique conserve l’ordre par `SessionSequence` entre événements clavier, pointeur, tactile et gestes. Des extensions filtrées fournissent les vues spécialisées sans réordonner ni réestampiller les événements. Le snapshot concerné est mis à jour avant l’événement correspondant.

Si la source input est fermée par policy ou erreur native, `events` se termine, les snapshots conservent leur dernière valeur terminale et les capabilities deviennent `Unavailable`; la fenêtre elle-même reste utilisable.

Les coordonnées portables utilisent l’espace logique du contenu de la fenêtre : origine en haut à gauche, axe X vers la droite et axe Y vers le bas. Un événement peut aussi exposer une position physique lorsqu’elle est connue, mais ne remplace jamais silencieusement une unité par l’autre. Les deltas raw conservent leur unité de périphérique et sont typés séparément.

Un événement clavier distingue au minimum la touche physique, la touche logique, la location, l’état press/release, la répétition et les modifiers. Le texte composé provient uniquement de `TextInputSession`; Kadre ne synthétise pas de texte à partir des événements clavier lorsque l’IME est actif.

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

`Gamepad.state` est mis à jour avant `Gamepad.events`. Les valeurs analogiques sont normalisées dans un domaine documenté tout en conservant la valeur native optionnelle sous `@KadrePlatformApi`. La déconnexion rend l’objet terminal ; une reconnexion produit un nouveau `GamepadId`.

### 10.3 IME

```kotlin
public suspend fun WindowInput.openTextInput(
    config: TextInputConfig,
): KadreResult<TextInputSession>

public interface TextInputSession : AutoCloseable {
    public val events: Flow<TextInputEvent>
    public val state: StateFlow<TextInputState>

    public override fun close()
    public suspend fun updateCursor(rect: LogicalRect<Double>): KadreResult<Unit>
    public suspend fun updateSurroundingText(
        text: String,
        selection: TextRange,
    ): KadreResult<Unit>
}
```

Une fenêtre possède au maximum une session IME active. Une seconde ouverture retourne `KadreFailure.AlreadyInUse`; aucun remplacement silencieux. `TextRange` utilise des offsets UTF-16 dans la `String` Kotlin. Le cursor rect est exprimé dans l’espace logique du contenu de la fenêtre et converti par le backend.

Fermer la fenêtre ou la session ferme la session IME enfant. La perte temporaire de focus publie un état suspendu, sans détruire automatiquement la composition ; le backend peut terminer la composition uniquement lorsque le host natif l’impose et l’annonce par un événement terminal.

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

public sealed interface CaptureSources {
    public data class Enumerated(public val values: List<CaptureSource>) : CaptureSources
    public data object HostPickerOnly : CaptureSources
    public data object PermissionRequired : CaptureSources
}
```

`HostPickerOnly` représente les plateformes qui interdisent une énumération préalable. Dans ce cas, `CaptureRequest` utilise une cible `HostChoice` et l’appel suspendu à `open` attend le choix ou l’annulation utilisateur. Kadre ne fabrique jamais de faux inventaire vide.

```kotlin
public interface CaptureSession : AutoCloseable {
    public val source: CaptureSource
    public val state: StateFlow<CaptureSessionState>
    public val diagnostics: Flow<CaptureDiagnostic>

    public override fun close()
    public fun requestStop()
    public suspend fun awaitTermination(): KadreResult<Unit>
    public suspend fun collectFrames(
        collector: suspend (CaptureFrame) -> Unit,
    ): KadreResult<Unit>
}
```

Une `CaptureSession` accepte un seul `collectFrames` actif. Un second collector reçoit `KadreFailure.AlreadyInUse`. La collection se termine avec la source, `requestStop`, la fermeture de la session applicative ou une erreur attendue de capture. Une exception du collector est propagée sans encapsulation après libération de la frame courante.

### 11.1 Frames

```kotlin
public interface CaptureFrame : AutoCloseable {
    public val size: PhysicalSize<Int>
    public val format: PixelFormat
    public val planes: List<PixelPlane>
    public val stamp: EventStamp
    public val colorSpace: ColorSpace
    public val alphaMode: AlphaMode
    public val orientation: CaptureOrientation

    public override fun close()
    public fun copyPlanes(): List<ByteArray>
}

public interface PixelPlane {
    public val rowStride: Int
    public val pixelStride: Int
    public val byteCount: Int
}
```

Une frame est une lease valide uniquement pendant l’appel du collector. Kadre la ferme dans un `finally`, que le collector retourne, échoue ou soit annulé. `close()` reste idempotent pour permettre une libération anticipée. `copyPlanes()` produit une copie distincte de chaque plane, détenue par l’application ; après fermeture, `copyPlanes()` échoue avec `IllegalStateException` et les vues de `PixelPlane` sont invalides. Kadre ferme aussi les frames remplacées ou écartées par la delivery policy. Le zero-copy retenable est réservé à `@KadrePlatformApi` avec un owner spécifique au backend.

Le format, les strides, le color space, l’alpha et l’orientation font partie du contrat de chaque frame. Aucune conversion implicite de format ou d’espace colorimétrique n’est effectuée par `copyPlanes()`.

## 12. Résultats et erreurs

```kotlin
public sealed interface KadreResult<out T> {
    public data class Success<T>(public val value: T) : KadreResult<T>
    public data class Failure(public val reason: KadreFailure) : KadreResult<Nothing>
}

public class KadreException(
    public val failure: KadreFailure,
) : RuntimeException(failure.message)
```

```kotlin
public sealed interface KadreFailure {
    public val message: String

    public data class Unsupported(
        public val operation: KadreOperation,
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

    public data class InvalidRequest(
        public val field: String?,
        public override val message: String,
    ) : KadreFailure

    public data class AlreadyInUse(
        public val resource: KadreResourceKind,
        public override val message: String,
    ) : KadreFailure

    public data class UnsupportedPolicy(
        public val component: String,
        public override val message: String,
    ) : KadreFailure

    public data class ParentScopeCancelled(
        public override val message: String,
    ) : KadreFailure

    public data class SourceLost(
        public val source: CaptureSourceId,
        public override val message: String,
    ) : KadreFailure

    public data class ApplicationFailure(
        public override val message: String,
    ) : KadreFailure

    public data class PlatformFailure(
        public val platform: KadrePlatform,
        public val domain: String,
        public val code: String,
        public override val message: String,
    ) : KadreFailure
}

public value class KadreOperation(public val value: String)

public enum class KadreResourceKind {
    Host,
    Window,
    WindowRequest,
    InputDevice,
    TextInputSession,
    CaptureSession,
    CaptureCollector,
}
```

Les `CancellationException` ne sont jamais encapsulées. Les erreurs de programmation utilisent les exceptions standard Kotlin. Les limites attendues utilisent `KadreResult`.

`KadreResult` fournit `isSuccess`, `isFailure`, `getOrNull`, `failureOrNull`, `map`, `flatMap`, `fold` et `getOrThrow`. `getOrThrow` lève `KadreException`; `map` et `flatMap` ne capturent jamais une exception du transformateur.

`message` est destiné au diagnostic humain et ne sert jamais au branching. Les sous-types, opérations, permissions, resources, domains et codes constituent l’information stable. Le `Throwable` original d’une erreur applicative ou plateforme est conservé pour le reporter interne du host, mais ne fait pas partie du value model public, de son égalité ou de son export Swift/JS.

## 13. Diagnostics

```kotlin
public sealed interface KadreDiagnostic {
    public val stamp: EventStamp
    public val severity: DiagnosticSeverity
    public val subsystem: KadreSubsystem
    public val message: String
}

public data class DiagnosticCounters(
    public val eventLosses: Long,
    public val slowCollectors: Long,
    public val backendFallbacks: Long,
    public val platformFailures: Long,
)
```

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

- Le job racine est un `SupervisorJob(parentScope.job)` : l’annulation du parent ferme la session, mais un échec Kadre ne cancelle jamais arbitrairement le scope du host.
- le sous-arbre de `KadreApplication.run` utilise un `Job` ordinaire : l’échec non géré de n’importe quel enfant applicatif termine toute la session ;
- une erreur locale correctement matérialisée ferme seulement la ressource concernée ; une erreur backend inattendue est promue en échec de session ;
- Les fonctions publiques suspendues sont appelables depuis toute coroutine.
- Les backends marshallent vers leur thread propriétaire.
- Le code des collectors s’exécute dans le contexte du collector, jamais arbitrairement dans un callback natif.
- Aucun `GlobalScope` ni job détaché.

## 15. Adaptateurs de plateforme

### 15.1 Android

```kotlin
public fun ComponentActivity.attachKadre(
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
): KadreResult<KadreSession>

public fun View.attachKadre(
    lifecycleOwner: LifecycleOwner,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
): KadreResult<KadreSession>
```

- Une même `Activity` ou `View` ne peut avoir qu’une session active ; une seconde tentative retourne `AlreadyInUse`.
- La session est liée au `LifecycleOwner` et se termine sur sa destruction définitive.
- Un changement de configuration recrée une session ; Kadre ne conserve pas implicitement les jobs ou fenêtres à travers deux hosts natifs.
- L’état applicatif durable appartient à l’application ou à son architecture de state restoration, pas au backend Kadre.
- Aucun `AndroidKadreRuntime.currentHandler`.
- Une intégration Compose attache la session sans fournir de widget ni rendu.

### 15.2 UIKit et SwiftUI

```kotlin
public object KadreIos {
    public fun attach(
        windowScene: UIWindowScene,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
    ): KadreResult<KadreSession>
}
```

- Une session par `UIWindowScene`.
- `UISceneDelegate` alimente lifecycle et fermeture.
- `sceneDidDisconnect` annule la hiérarchie.
- Bridge Swift minimal `KadreIos.attach(windowScene, applicationFactory, policy)`.
- SwiftUI utilise un `UIViewControllerRepresentable` hôte.
- Une fenêtre iPadOS supplémentaire crée une nouvelle scène, une nouvelle session et une nouvelle application via la factory du host.
- Le host, et non un singleton Kadre, conserve la factory et corrèle le `WindowRequestId` avec les options de connexion de scène.

### 15.3 Web

```kotlin
public fun HTMLElement.attachKadre(
    parentScope: CoroutineScope,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
): KadreResult<KadreSession>
```

- Attachement à un `HTMLElement` ou `HTMLCanvasElement` existant.
- `StopWhenDetached` exige un élément initialement connecté ; sinon `attachKadre` retourne `InvalidRequest`. `Manual` accepte un élément déconnecté avec lifecycle `Attached + Background + Inactive`.
- `StopWhenDetached` vérifie `isConnected` à la livraison du batch `MutationObserver` : un reparenting terminé avant cette livraison ne ferme pas la session ; un élément encore détaché la termine et sa réinsertion exige une nouvelle session.
- `Manual` ignore le détachement DOM et exige un `requestStop` explicite.
- Aucun détournement du titre comme ID DOM.
- Sessions multiples possibles sur une même page.
- Même contrat public en JS et Wasm.
- Aucun faux `runApp` bloquant.

### 15.4 Desktop

```kotlin
public fun CoroutineScope.attachKadreDesktop(
    applicationFactory: KadreApplicationFactory,
    options: DesktopHostOptions = DesktopHostOptions.Embedded,
    policy: KadrePolicy = KadrePolicies.Default,
): KadreResult<KadreSession>

public fun runKadreApplication(
    applicationFactory: KadreApplicationFactory,
    options: DesktopHostOptions = DesktopHostOptions.Standalone,
    policy: KadrePolicy = KadrePolicies.Default,
): SessionOutcome
```

- `attachKadreDesktop` est l’API primaire embarquable et non bloquante.
- `runKadreApplication` est une commodité Desktop uniquement, bloquante et appelée depuis le main thread du processus ; elle n’établit aucun contrat commun avec Android, UIKit ou Web.
- `Embedded` conserve la session après fermeture de la dernière fenêtre ; `Standalone` demande son arrêt.
- AppKit refuse explicitement un lancement hors du main thread au lieu de déplacer silencieusement la possession de `NSApplication`.
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

L’API Kotlin partagée est la surface normative. Pendant l’incubation initiale, une application est écrite en Kotlin partagé et les hosts Swift ne font qu’attacher une `KadreApplicationFactory` fournie par le module KMP. L’implémentation directe de `KadreApplication` en Swift n’est pas un contrat de cette première surface.

Les adapters Swift exposent des wrappers idiomatiques pour la session et le lifecycle, et traduisent `KadreResult` vers un résultat/erreur Swift typé, au lieu de faire de `CoroutineScope`, `Flow` ou `Throwable` une convention d’intégration Swift. Les exports Java, Swift, JS et Wasm possèdent chacun un consumer compile test. Les headers/frameworks générés et les déclarations TypeScript exportées sont contrôlés comme des artefacts d’API en plus des dumps ABI Kotlin.

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

Les contract tests couvrent explicitement la température, le replay, la cardinalité, l’ordre state/event, l’isolation des collectors lents, les séquences monotones, la fermeture automatique des frames et les transitions légales du lifecycle. Des consumer tests compilent une intégration minimale Java, Swift, JS et Wasm.

## 18. Performance

Invariants mesurables :

- aucun buffer non borné ;
- aucune perte silencieuse de transition discrète ;
- aucune coroutine orpheline après teardown ;
- frames abandonnées toujours fermées ;
- coût mesuré avec zéro, un et plusieurs collectors pour les flux multicast, et avec le collector unique pour la capture ;
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
