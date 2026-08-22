# New Kadre — Architecture cible de l’API publique

**Statut :** snapshot de conception approuvé le 22 août 2026

**Portée :** toute l’API publique de Kadre

**Compatibilité :** breaking changes autorisés pendant l’incubation

**Référence d’exécution :** les plans détaillés dérivés de ce document doivent conserver ses invariants

**Registre de migration :** `new-kadre/API-MIGRATION.md` attribue une décision à chaque famille de symboles de l’ABI actuelle

## 1. Vision

Kadre devient une bibliothèque Kotlin Multiplatform idiomatique de gestion :

- des surfaces de présentation fournies par l’hôte et des fenêtres top-level ;
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
4. **Aucun état global applicatif.** Aucun handler, session ou application courant n’est stocké dans un singleton public ou interne process-wide. Les brokers internes imposés par des ressources OS process-wide restent autorisés, sans notion de session courante.
5. **Capabilities honnêtes.** Une absence de support est explicite ; aucun backend ne retourne un faux succès ou un no-op silencieux.
6. **Temps monotone.** Les durées, délais et timestamps d’événements n’utilisent pas l’heure Unix.
7. **Ownership explicite.** Les ressources natives, buffers de capture et sessions enfants ont une durée de vie définie et testable.
8. **Interop isolée.** Les détails FFI et handles bruts n’apparaissent pas dans l’API commune ordinaire.
9. **Buffers et fan-out bornés.** Aucun flux de production ne dépend d’une file non bornée et le nombre de collectors admis est lui-même limité.
10. **Breaking changes directs.** L’incubation autorise la suppression de l’ancien modèle sans maintenir deux architectures en production.
11. **Surface et fenêtre distinctes.** Un élément DOM, une `View` ou une vue UIKit n’est jamais présenté comme une fenêtre top-level fictive.
12. **Immutabilité profonde.** Toute valeur publiée reste immuable après publication, y compris ses collections et buffers transitifs.

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
├── surface
├── window
├── display
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
    public val restorationToken: RestorationToken?,
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
    public val primarySurface: StateFlow<HostSurface?>
    public val windows: WindowManager
    public val displays: DisplayManager
    public val devices: DeviceManager
    public val capture: CaptureManager
    public val diagnostics: KadreDiagnostics

    public fun requestStop()
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
    HostRequested,
    ApplicationRequested,
    ApplicationCancelled,
    ParentCancelled,
    HostDetached,
}

public value class RestorationToken internal constructor(public val value: String)
```

Contrats :

- `close()` délègue à `requestStop()` ; les deux sont non bloquants, thread-safe et idempotents ;
- `awaitTermination()` est idempotent et retourne toujours le même résultat terminal ;
- appeler `awaitTermination()` depuis un job enfant de la session échoue immédiatement avec `IllegalStateException` au lieu de deadlocker ;
- le retour normal de `KadreApplication.run` produit `SessionOutcome.Completed` et déclenche le teardown ;
- `KadreSession.requestStop()` propose `Stopped(HostRequested)` et `KadreScope.requestStop()` propose `Stopped(ApplicationRequested)` ; le premier motif d’arrêt non fatal accepté fait autorité ;
- une annulation directe du job exposé par `KadreScope.coroutineContext`, alors que le parent et le host restent actifs, produit `Stopped(ApplicationCancelled)` ;
- l’annulation du `parentScope` produit `Stopped(ParentCancelled)` ;
- le détachement définitif de l’hôte produit `Stopped(HostDetached)` ;
- une exception non gérée produit un diagnostic fatal, devient un `KadreFailure.ApplicationFailure` stable et termine la session avec `Failed` ;
- une exception de `KadreApplicationFactory.create` suit exactement le même chemin d’échec ;
- une factory crée exactement une instance de `KadreApplication` par session et peut être invoquée simultanément pour plusieurs scènes ;
- un consumer qui capture de l’état mutable dans la factory reste responsable de sa synchronisation entre sessions.

`KadreScope.coroutineContext` contient le job applicatif ordinaire, jamais le `SupervisorJob` racine de session. Il reprend le contexte du `parentScope` en remplaçant uniquement son `Job` par ce job applicatif. `KadreApplication.run` et les enfants lancés via le receiver utilisent donc le dispatcher du parent ; les appels natifs marshallent séparément vers le thread du host. Le retour de `run` attend tous ses enfants structurés. Un consumer doit utiliser `withContext` pour déplacer ses calculs lourds au lieu de supposer que le dispatcher applicatif est un thread UI ou un worker.

Lorsqu’un scope parent est lui-même possédé par le lifecycle du host, la notification terminale du host est admise avant l’annulation de ce scope et produit déterministement `HostDetached`. `ParentCancelled` est réservé à une annulation externe observée alors que le host reste attaché.

`RestorationToken` est opaque et fourni par le host pour recoller la session à l’état durable détenu par l’application. Kadre ne sérialise, ne persiste et ne restaure aucun état applicatif. Un lancement `HostRestoration` sans token reste autorisé lorsque le host ne possède pas d’identité persistante.

Une erreur non liée à la cancellation, observée avant `Terminated`, surclasse un motif `Stopped` ou `Completed` et produit `Failed`. Les `CancellationException` déclenchées par le teardown et les erreurs secondaires de cleanup ne remplacent pas le résultat ; ces dernières sont attachées au diagnostic fatal principal.

La session passe à `Running` immédiatement avant l’appel de `KadreApplication.run`, une fois lifecycle et managers initialisés. Le code applicatif ne peut donc jamais observer un manager partiellement construit.

Le teardown suit cet ordre normatif : fermeture de l’admission des callbacks, annulation du job applicatif, arrêt des captures, drop transfers, interactions et sessions IME, arrêt des effets de périphériques, fermeture ou abandon des requêtes de fenêtre, fermeture des fenêtres en ordre inverse de création, détachement de la surface primaire et des subscriptions aux brokers, détachement des bridges natifs, puis attente de tous les enfants. Aucun callback ne peut réintroduire une ressource après la fermeture de l’admission.

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

### 6.1 Brokers process-wide internes

Les APIs natives d’inventaire de displays, de gamepads, de permissions et certaines APIs de capture peuvent imposer une seule subscription ou un seul coordinateur par processus. Un backend peut donc utiliser un broker interne process-wide, reference-counted et thread-safe. Ce broker :

- ne conserve aucune notion de session ou application « courante » ;
- exige l’enregistrement explicite de chaque session et retire cet enregistrement pendant son teardown ;
- traduit chaque identité physique en identités opaques limitées à chaque session ;
- route clavier, pointeur et raw input vers la surface autorisée par le host ;
- applique la `DevicePolicy` pour les périphériques partageables et arbitre explicitement l’ownership des effets ;
- ne survit sans subscription native que si le backend documente une contrainte OS l’exigeant.

Un broker constitue un détail d’infrastructure, jamais un service locator public. Deux sessions ne peuvent obtenir un accès croisé à leurs fenêtres, jobs, collectors ou handles par son intermédiaire.

## 7. Lifecycle

```kotlin
public interface KadreLifecycle {
    public val state: StateFlow<LifecycleState>
    public val events: Flow<LifecycleEvent>
    public val signals: Flow<HostSignal>
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

public sealed interface HostSignal {
    public val stamp: EventStamp

    public data class MemoryPressure(
        public val level: MemoryPressureLevel,
        public override val stamp: EventStamp,
    ) : HostSignal
}
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

`signals` porte les notifications non durables du host qui ne sont pas des axes de lifecycle, notamment la pression mémoire. Elles sont capability-driven, hot, sans replay et suivent la delivery policy lifecycle. Kadre ne fabrique pas de signal sur un host qui ne l’expose pas ; une application conserve ses propres seuils de cache et ne dépend pas de la réception d’un signal pour rester correcte.

### 7.1 Contrats normatifs des flux

```kotlin
public data class EventStamp(
    public val sequence: SessionSequence,
    public val timestamp: SessionInstant,
    public val coalescedSpan: CoalescedEventSpan?,
)

public data class CoalescedEventSpan(
    public val firstSequence: SessionSequence,
    public val lastSequence: SessionSequence,
    public val eventCount: Int,
)

public value class SessionSequence internal constructor(public val value: Long)
public value class SessionInstant internal constructor(public val sinceStart: Duration)

public interface KadreDiagnostics {
    public val events: Flow<KadreDiagnostic>
    public val counters: StateFlow<DiagnosticCounters>
}
```

`SessionSequence` est attribuée atomiquement à l’ingress et est strictement croissante dans une session. `SessionInstant` utilise la même origine monotone pour lifecycle, fenêtres, input, gamepads et capture ; deux timestamps de sessions différentes ne sont pas directement comparables. Une valeur non coalescée utilise `coalescedSpan = null`. Une valeur coalescée porte le stamp du dernier événement agrégé et un span couvrant les séquences originales ; les trous de séquence dans une vue filtrée ou coalescée sont donc normaux et observables.

Les IDs de ressource (`SurfaceId`, `WindowId`, `DisplayId`, `DeviceId`, `GamepadId`, `CaptureSourceId`) sont opaques, à constructeur interne et valides uniquement dans leur session. `SessionId` identifie une session pendant la vie du processus. `WindowRequestId` est un ID de corrélation process-wide afin de traverser la création d’un nouveau host, mais ne permet aucun lookup de ressource étrangère.

| Catégorie | Température | Replay | Cardinalité | Règle d’ownership |
|---|---|---|---|---|
| `StateFlow` | hot | dernière valeur | multicast | valeur immuable détenue par Kadre |
| transitions lifecycle/window/device | hot | aucun | multicast | événement immuable estampillé |
| input et gamepad | hot | aucun | multicast | événement immuable estampillé |
| diagnostics | hot | aucun | multicast | les compteurs restent disponibles même si un événement est manqué |
| `collectFrames` | streaming structuré | aucun | un collector actif | lease fermée automatiquement après le bloc collector |

Collecter un flux d’événements ne démarre ni n’arrête sa source. Chaque collector possède une file bornée ; un collector lent est traité selon la policy sans bloquer les callbacks natifs ni les autres collectors. Le nombre de collectors par flux d’événements, par `StateFlow` et par session est borné par la policy. Une subscription refusée se termine immédiatement avec `KadreException(KadreFailure.ResourceLimitExceeded(...))` sans modifier la source ni les autres collectors. Tous les événements publics portent un `EventStamp`. Lorsqu’un événement correspond à un snapshot, le `StateFlow` est mis à jour avant sa publication.

À la fermeture normale d’un owner, ses flux d’événements drainent les éléments déjà admis puis se terminent normalement. Une fermeture due à une failure termine les collectors avec `KadreException` portant la même failure stable. Un collector démarré après la terminaison observe immédiatement cette terminaison, sans replay d’événement. Les `StateFlow` ne se terminent pas : ils conservent indéfiniment leur snapshot terminal. Les implémentations ne sont donc pas contraintes d’utiliser `SharedFlow`, qui ne sait pas représenter une terminaison.

Les frames constituent l’unique exception au modèle `Flow` multicast, car une ressource closeable ne peut pas avoir plusieurs owners implicites. Elles utilisent une opération suspendue structurée décrite en section 11.

## 8. Delivery policies et backpressure

```kotlin
public data class KadrePolicy(
    public val execution: ExecutionPolicy,
    public val lifecycleEvents: EventDeliveryPolicy,
    public val windowEvents: EventDeliveryPolicy,
    public val deviceEvents: EventDeliveryPolicy,
    public val input: InputDeliveryPolicy,
    public val devices: DevicePolicy,
    public val capture: CaptureDeliveryPolicy,
    public val diagnostics: DiagnosticPolicy,
    public val resources: ResourceBudgetPolicy,
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
    public val maxCollectors: Int,
    public val ingressOverflow: IngressOverflowAction,
    public val collectorOverflow: CollectorOverflowAction,
)

public data class ResourceBudgetPolicy(
    public val maxCollectorsPerSession: Int,
    public val maxStateCollectorsPerFlow: Int,
    public val maxPendingWindowRequests: Int,
    public val maxConcurrentCaptureSessions: Int,
    public val maxConcurrentDropTransfers: Int,
    public val maxDropChunkBytes: Int,
)

public data class DevicePolicy(
    public val gamepadRouting: GamepadRouting,
    public val effectOwnership: DeviceEffectOwnership,
)

public enum class GamepadRouting {
    ActiveSessionOnly,
    AllForegroundSessions,
}

public enum class DeviceEffectOwnership {
    ExclusivePerPhysicalDevice,
    SharedWhenSupported,
}

public data class DiagnosticPolicy(
    public val eventBufferCapacity: Int,
    public val eventOverflow: DiagnosticOverflowAction,
    public val dataExposure: DiagnosticDataExposure,
)

public enum class DiagnosticDataExposure {
    Redacted,
    IncludePublicMetadata,
}

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

| Profil | Event/state collectors par flux | Collectors/session | Requêtes fenêtre | Captures | Drop transfers/chunk |
|---|---:|---:|---:|---:|---:|
| `Default` | 16 / 32 | 128 | 16 | 4 | 4 / 256 KiB |
| `Realtime` | 8 / 16 | 64 | 8 | 2 | 2 / 64 KiB |
| `Recording` | 16 / 32 | 128 | 16 | 4 | 8 / 1 MiB |

Pour `Default` et `Realtime`, l’ingress lifecycle utilise `FailSession`, les ingress window/device/input utilisent `CloseSource`, et les collectors utilisent `CancelSlowCollector`. `Recording` utilise `FailSession` à tous les niveaux afin qu’un enregistrement incomplet ne ressemble jamais à un succès.

`Default` utilise `Balanced`, un shutdown de 5 secondes et 256 diagnostics détaillés. `Realtime` utilise `LatencyFirst`, 2 secondes et 64 diagnostics. `Recording` utilise `Throughput`, 30 secondes et 8192 diagnostics. L’overflow des événements de diagnostic supprime le plus ancien événement détaillé, tandis que les compteurs restent exacts.

Les trois profils utilisent `DiagnosticDataExposure.Redacted`. L’opt-in `IncludePublicMetadata` peut inclure les labels déjà exposés par l’API publique, mais jamais le texte IME, les frappes, les chemins de drag-and-drop, les pixels capturés, les tokens d’interaction ou les handles natifs.

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

Les capacités de buffer, limites de collectors, budgets de ressources et `shutdownTimeout` sont strictement positifs et validés à la construction. L’ingress discret choisit `CloseSource` ou `FailSession`; chaque collector peut aussi choisir `CancelSlowCollector`. Les données continues peuvent choisir `DropOldestAndReport`, `DropLatestAndReport`, `CloseSource` ou `FailSession`. Il n’existe pas de variante `Unlimited`.

Chaque source possède une file d’ingress bornée avant le fan-out, puis chaque collector possède sa propre file bornée. `CloseSource` ferme l’owner du flux saturé : la session pour lifecycle, la fenêtre pour ses événements, la branche `SurfaceInput` pour son input, le périphérique pour son flux propre et la `CaptureSession` pour ses frames. `CancelSlowCollector` termine uniquement le collector concerné avec `SlowCollectorCancellationException`.

Les profils fixent aussi des limites finies de collectors et de ressources. `Default` et `Realtime` routent les gamepads vers la session active et utilisent un ownership exclusif des effets. `Recording` peut router vers toutes les sessions foreground, mais ne peut demander un effet partagé que si la capability du périphérique le garantit. Atteindre un budget retourne `KadreFailure.ResourceLimitExceeded`; Kadre n’alloue jamais d’abord pour diagnostiquer ensuite.

`ExecutionPriority` influence uniquement les workers internes et leur cadence de réveil. Les dispatchers UI/main propriétaires restent imposés par le host et ne sont jamais remplaçables par une policy applicative. `shutdownTimeout` borne le teardown ; son dépassement ferme les bridges encore actifs, produit un diagnostic fatal et termine la session en `Failed`.

`Coalesced` a une sémantique propre au type : une position absolue conserve la dernière valeur, un mouvement relatif ou scroll additionne tous les deltas, et une valeur analogique conserve la dernière valeur par contrôle. `Latest` ferme la valeur remplacée lorsqu’elle possède une ressource.

La policy exposée par `KadreScope.policy` est celle effectivement garantie. Un host incapable de l’honorer échoue pendant `attach` avec `UnsupportedPolicy`; aucun profil n’est dégradé silencieusement. `Recording` garantit seulement l’absence de perte ajoutée par Kadre après l’ingress natif ; une coalescence ou perte imposée en amont par l’OS est signalée lorsqu’elle est détectable.

Si une capability dynamique change après `attach` et rend une garantie impossible, le backend met d’abord à jour la capability puis ferme la source concernée ou la session selon l’action d’overflow/failure configurée. Il ne remplace jamais la policy exposée, ne redimensionne pas silencieusement les buffers et ne bascule pas vers un profil plus faible.

Les callbacks natifs ne sont jamais bloqués pour promettre un lossless impossible. Toute perte incrémente synchroniquement un compteur typé dans `KadreDiagnostics.counters`, puis tente d’émettre un diagnostic détaillé. La garantie ne dépend donc jamais de la livraison du flux de diagnostics lui-même.

## 9. Surfaces, displays et fenêtres

### 9.1 HostSurface

```kotlin
public interface HostSurface {
    public val id: SurfaceId
    public val state: StateFlow<SurfaceState>
    public val capabilities: StateFlow<SurfaceCapabilities>
    public val events: Flow<SurfaceEvent>
    public val input: SurfaceInput

    public fun requestRedraw(): KadreResult<Unit>
    public suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome>
}
```

Une `HostSurface` est une région de présentation et d’input dont le layout et le lifetime natif sont possédés par le host. Elle peut représenter un `HTMLElement`, un `View`, la vue racine d’un `UIViewController` ou le contenu d’une fenêtre desktop. Elle n’expose ni renderer ni API de dessin. Les handles nécessaires à un renderer externe restent sous `@KadrePlatformApi`.

`KadreScope.primarySurface` contient au plus une surface racine. Une session headless utilise `null`. Une surface ne prétend jamais posséder un titre, une position dans le bureau, des décorations ou un mode top-level. Lorsqu’une fenêtre possède une zone de contenu distincte, `Window.surface` référence sa `HostSurface`; fermer la fenêtre ferme cette surface. Une surface directement fournie par le host n’est pas fermée comme un objet natif par l’application : le teardown détache uniquement les bridges Kadre.

`SurfaceState` contient au minimum la taille logique et physique, le scale factor, les safe-area insets, la visibilité, l’occlusion connue et un `SurfaceRevision`. Les conversions utilisent la taille et le scale factor d’un même snapshot atomique. Les coordonnées d’input sont relatives à cette surface.

`SurfaceEvent` porte resize, scale change, focus, visibilité/occlusion, theme et `RedrawRequested`. `requestRedraw()` est thread-safe, non bloquant et peut être coalescé par le host ; il ne dessine rien et n’impose aucune cadence. Une surface vivante garantit soit l’admission de la requête, soit une failure explicite ; une surface terminale retourne `Closed`. `SurfaceUpdate` regroupe uniquement les propriétés de la région de contenu, comme cursor visible/custom, pointer capture ou hit-testing, avec la même sémantique partial/accepted et les mêmes règles de révision qu’une mise à jour de fenêtre. Une custom cursor est une ressource closeable appartenant à la session ; son image d’entrée est copiée défensivement.

### 9.2 Displays

```kotlin
public interface DisplayManager {
    public val inventory: StateFlow<DisplayInventory>
    public val events: Flow<DisplayEvent>
    public val capabilities: StateFlow<DisplayCapabilities>

    public suspend fun requestAccess(): KadreResult<DisplayInventory>
}

public sealed interface DisplayInventory {
    public data class Enumerated(
        public val primary: Display?,
        public val displays: List<Display>,
    ) : DisplayInventory

    public data object PermissionRequired : DisplayInventory
    public data class Unavailable(public val failure: KadreFailure) : DisplayInventory
}

public interface Display {
    public val id: DisplayId
    public val state: StateFlow<DisplayState>
}
```

Les displays remplacent `MonitorHandle` et `VideoMode`. Leur inventaire est observable et ne fabrique pas de monitor synthétique pour masquer une absence d’énumération. Un backend peut néanmoins exposer explicitement un display de type `HostViewport` lorsqu’il ne représente que le viewport courant. `DisplayState` décrit ce type, le nom optionnel, les bounds physiques dans l’espace du bureau virtuel, la work area, le scale factor, le mode courant et les modes réellement connus.

Les coordonnées du bureau virtuel sont physiques, peuvent être négatives et n’ont aucune conversion logique globale : la conversion logique/physique utilise toujours le scale factor de la surface ou du display ciblé, avec une règle d’arrondi documentée par l’opération. Une modification d’échelle ou de mode incrémente la révision avant l’événement correspondant.

### 9.3 WindowManager

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

`WindowManager.primary` ne contient qu’une véritable fenêtre top-level appartenant au host. Il reste `null` pour une session attachée uniquement à un élément DOM, une `View`, une vue UIKit embarquée ou un host headless. La surface correspondante reste accessible via `KadreScope.primarySurface`.

```kotlin
public interface WindowRequest : AutoCloseable {
    public val id: WindowRequestId
    public val state: StateFlow<WindowRequestState>

    public override fun close()
    public suspend fun cancel(): KadreResult<WindowCancellationOutcome>
    public suspend fun await(): WindowRequestOutcome
}

public sealed interface WindowRequestState {
    public data object Pending : WindowRequestState
    public data class Terminated(public val outcome: WindowRequestOutcome) : WindowRequestState
}

public sealed interface WindowRequestOutcome {
    public data class OpenedHere(public val window: Window) : WindowRequestOutcome
    public data class OpenedInNewSession(
        public val sessionId: SessionId,
    ) : WindowRequestOutcome
    public data class Rejected(public val failure: KadreFailure) : WindowRequestOutcome
    public data object Cancelled : WindowRequestOutcome
    public data object RequesterDetached : WindowRequestOutcome
}

public sealed interface WindowCancellationOutcome {
    public data object CancelledBeforeCommit : WindowCancellationOutcome
    public data object CancellationRequested : WindowCancellationOutcome
    public data class AlreadyTerminated(
        public val outcome: WindowRequestOutcome,
    ) : WindowCancellationOutcome
    public data object TooLate : WindowCancellationOutcome
}
```

- `await()` attend un état terminal et retourne toujours la même valeur ensuite.
- l’outer `KadreResult` de `requestWindow` échoue uniquement si la requête ne peut pas être admise (spec invalide, session fermée ou budget atteint) ; une décision du host appartient au `WindowRequestOutcome` observable.
- `cancel()` distingue l’annulation avant commit, une demande d’annulation encore en attente, une terminaison déjà connue et un commit natif trop tardif pour être annulé ; `TooLate` laisse la requête en attente de son vrai résultat.
- `cancel()` est idempotent : plusieurs callers observent une décision compatible et une seule transition terminale fait autorité.
- `close()` est non bloquant et abandonne le handle. Il tente une annulation best-effort ; si l’opération native reste irréversible, le handle atteint `RequesterDetached` tandis que le host peut encore créer une session sans réintroduire de ressource dans la session demandeuse.
- Desktop peut atteindre `OpenedHere`.
- UIKit atteint `OpenedInNewSession` lorsque l’OS connecte la nouvelle `UIWindowScene` et que sa session est attachée avec la factory du host.
- La nouvelle application reçoit `KadreLaunchReason.AdditionalHostRequested` et l’`originatingRequestId`.
- Une plateforme sans multi-window crée une requête immédiatement `Rejected(Unsupported)` ; elle ne retourne jamais un faux succès.
- Fermer la session demandeuse appelle `close()` sur ses requêtes encore `Pending`; une opération annulable devient `Cancelled`, une opération native déjà irréversible devient `RequesterDetached`. Une nouvelle session déjà ouverte ou créée ensuite n’est pas fermée.

`WindowManager.windows` ne contient que les fenêtres top-level appartenant à la session courante. Une fenêtre annoncée par `OpenedInNewSession` n’y apparaît jamais. Kadre ne crée jamais automatiquement un élément DOM, une vue, une activité ou une scène pour rendre `primary` non nul.

`OpenedInNewSession` n’expose volontairement aucun `WindowId` étranger : les IDs de ressource ne sont utilisables que dans leur session. La nouvelle application retrouve sa propre fenêtre via son `WindowManager` et corrèle le lancement grâce à `originatingRequestId`.

### 9.4 WindowSpec

`WindowSpec` est un snapshot immuable créé par constructeur ou DSL. Il remplace la longue `data class WindowAttributes` évolutive. Le `WindowSpecBuilder` est éphémère, non thread-safe et ne doit pas être conservé après le retour du bloc.

```kotlin
val result = scope.windows.requestWindow {
    title = "Kadre"
    size = LogicalSize(1280.0, 720.0)
    resizable = true
}
```

### 9.5 Window

```kotlin
public interface Window {
    public val id: WindowId
    public val surface: HostSurface
    public val state: StateFlow<WindowState>
    public val capabilities: StateFlow<WindowCapabilities>
    public val events: Flow<WindowEvent>

    public suspend fun apply(update: WindowUpdate): KadreResult<WindowUpdateOutcome>
    public suspend fun close(): KadreResult<Unit>
}

public sealed interface WindowUpdateOutcome {
    public data class Applied(
        public val operationId: WindowOperationId,
        public val state: WindowState,
    ) : WindowUpdateOutcome
    public data class PartiallyApplied(
        public val operationId: WindowOperationId,
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

`WindowState` est le snapshot effectif, cohérent et atomique ; il contient un `WindowRevision` strictement croissant et ne représente jamais simplement la valeur demandée. Les mutations suspendues effectuent le marshalling vers le thread hôte.

Le snapshot et les updates top-level couvrent titre, géométrie externe, min/max/fullscreen, décorations, boutons système, level, transparence/blur, icon, attention et content protection uniquement lorsque la capability correspondante existe. Les événements close requested, moved et changement de mode restent sur `Window.events`; les événements de contenu et d’input restent sur `Window.surface`.

`Window.apply` n’est pas transactionnel : les plateformes natives ne peuvent pas garantir un rollback atomique d’un lot de propriétés. Toute application partielle énumère les champs rejetés et leurs `KadreFailure`. `Accepted` signifie que le host a accepté une opération visuellement asynchrone ; l’achèvement est observé dans `Window.state` et `Window.events` avec le même `operationId`. Une fermeture est idempotente et rend la fenêtre terminale.

Les appels concurrents à `apply` reçoivent un `WindowOperationId` à l’admission et sont sérialisés dans cet ordre par fenêtre. Une cancellation avant le commit natif retire l’opération ; après commit, elle ne provoque jamais de rollback implicite et le résultat reste observable par son ID. `close` ferme l’admission, fait échouer les opérations non committées avec `KadreFailure.Closed` et attend les opérations déjà committées uniquement dans la limite du shutdown timeout. Les changements externes au processus utilisent `operationId = null` et incrémentent quand même la révision.

Les opérations contextuelles conservent des verbes dédiés au lieu d’être cachées dans `WindowUpdate` : pointer lock, system drag et attention utilisateur.

### 9.6 Interactions transitoires

Certaines APIs exigent une activation utilisateur synchrone ou un serial attaché à l’événement natif : fullscreen et pointer lock Web, ouverture d’un browsing context, system drag AppKit, drag/resize Wayland et menus contextuels natifs. Une livraison différée par `Flow` ne peut pas préserver honnêtement cette autorité.

```kotlin
public fun HostSurface.installInteractionHandler(
    handler: InteractionHandler,
): KadreResult<InteractionRegistration>

public suspend fun HostSurface.armInteraction(
    action: InteractionAction,
): KadreResult<ArmedInteraction>

public interface InteractionRegistration : AutoCloseable {
    public val outcomes: Flow<InteractionActionOutcome>

    public override fun close()
}

public interface ArmedInteraction : AutoCloseable {
    public val state: StateFlow<ArmedInteractionState>

    public override fun close()
    public suspend fun await(): InteractionActionOutcome
}

public fun interface InteractionHandler {
    public fun onInteraction(context: InteractionContext, event: InteractionEvent)
}

public interface InteractionContext {
    public val token: InteractionToken
    public fun request(action: InteractionAction): KadreResult<InteractionRequestId>
}
```

Ce handler non suspendu constitue l’unique escape hatch au modèle coroutine principal. Il s’exécute dans le callback natif, doit retourner immédiatement et ne peut effectuer que les `InteractionAction` bornées exposées par le contexte. `InteractionEvent` reçoit son stamp avant l’appel du handler ; le même input est ensuite publié avec ce stamp dans `SurfaceInput.events`. Le token est opaque, limité à la session et à la surface d’origine, potentiellement single-use et invalide dès le retour du callback. Le conserver est autorisé mais toute réutilisation retourne `KadreFailure.InteractionRequired` avec un motif typé `Missing`, `Expired`, `Consumed` ou `WrongSurface`.

Une surface accepte au maximum un handler actif. `InteractionRegistration.outcomes` corrèle chaque `InteractionRequestId` avec son résultat terminal après le retour du callback ; fermer la registration empêche toute nouvelle invocation et ne révoque pas une action native déjà committée. Une exception du handler est capturée avant de retraverser la frontière native, produit `ApplicationFailure` et déclenche l’arrêt de la session selon les règles applicatives ordinaires.

Une application peut aussi pré-armer une action compatible pour le prochain geste via une requête observable owned par la surface. Le backend l’exécute alors dans le callback sans invoquer de code applicatif arbitraire. Les capabilities indiquent quelles actions supportent ce mode. Aucune API suspendue ordinaire ne prétend prolonger une user activation native.

### 9.7 Capabilities

```kotlin
public sealed interface FeatureAvailability {
    public data object Available : FeatureAvailability
    public data class RequiresPermission(public val permission: KadrePermission) : FeatureAvailability
    public data class RequiresInteraction(
        public val kind: InteractionKind,
    ) : FeatureAvailability
    public data class Unavailable(public val failure: KadreFailure) : FeatureAvailability
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

Chaque champ de `WindowCapabilities` ou `SurfaceCapabilities` utilise une `Capability` avec des contraintes spécifiques : tailles, modes fullscreen, curseurs, décorations ou transparence. `Unsupported` décrit l’absence structurelle ; `Supported` contient les préconditions dynamiques et le domaine accepté. Une fonctionnalité sans contrainte utilise `Unit`. Les capabilities sont prédictives ; le résultat de l’opération reste l’autorité finale.

Les raisons dynamiques restent typées : une application ne branche jamais sur `reason` ou `message`. Les changements de permission, interaction, focus ou disponibilité mettent à jour la capability avant de publier le diagnostic correspondant.

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

Les identifiants de device sont des `value class` opaques avec constructeur interne. Leur unicité et leur validité sont limitées à une `KadreSession`; ils ne servent pas d’identifiants persistants entre lancements. Tous les événements portent l’`EventStamp` de la session.

Un même gamepad physique peut être projeté dans plusieurs sessions avec des `GamepadId` distincts. `ActiveSessionOnly` route ses changements vers la session possédant la surface focalisée la plus récemment activée ; aucune session background n’est éligible et l’absence de session active suspend la livraison. `AllForegroundSessions` duplique explicitement les observations vers chaque session foreground avec un stamp propre à chacune. Le descriptor ne contient aucun identifiant persistant permettant de recoller implicitement deux projections entre sessions.

### 10.1 Input par surface

```kotlin
public interface SurfaceInput {
    public val events: Flow<InputEvent>
    public val keyboardState: StateFlow<KeyboardState>
    public val pointers: StateFlow<List<PointerState>>
    public val modifiers: StateFlow<KeyboardModifiers>
    public val capabilities: StateFlow<InputCapabilities>
}

public sealed interface InputEvent {
    public val stamp: EventStamp
    public val deviceId: DeviceId?

    public data class StateReset(
        public val reason: InputStateResetReason,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
    ) : InputEvent
}

public enum class InputStateResetReason {
    FocusLost,
    DeviceDisconnected,
    SourceOverflow,
    PermissionRevoked,
}
```

Le flux unique conserve l’ordre par `SessionSequence` entre événements clavier, pointeur, tactile et gestes. Des extensions filtrées fournissent les vues spécialisées sans réordonner ni réestampiller les événements. Le snapshot concerné est mis à jour avant l’événement correspondant.

Si la source input est fermée par policy ou erreur native, `events` se termine, les snapshots sont d’abord remis dans un état neutre terminal et les capabilities deviennent `Unavailable`; la surface elle-même reste utilisable.

Les coordonnées portables utilisent l’espace logique de la surface : origine en haut à gauche, axe X vers la droite et axe Y vers le bas. Un événement peut aussi exposer une position physique lorsqu’elle est connue, mais ne remplace jamais silencieusement une unité par l’autre. Les deltas raw conservent leur unité de périphérique et sont typés séparément.

Un événement clavier distingue au minimum la touche physique, la touche logique, la location, l’état press/release, la répétition et les modifiers. Le texte composé provient uniquement de `TextInputSession`; Kadre ne synthétise pas de texte à partir des événements clavier lorsque l’IME est actif.

Une perte de focus, une déconnexion ou une fermeture de source ne synthétise pas une série arbitraire de releases. Kadre remet atomiquement les snapshots concernés à l’état neutre, puis publie un unique `StateReset` estampillé. Les press/release réellement fournis par le host avant ce reset conservent leur ordre. Cette règle empêche les touches, boutons et contacts « bloqués » sans faire passer des événements inventés pour des observations natives.

### 10.2 Gamepad

```kotlin
public interface Gamepad {
    public val id: GamepadId
    public val descriptor: StateFlow<GamepadDescriptor>
    public val connection: StateFlow<DeviceConnectionState>
    public val state: StateFlow<GamepadState>
    public val events: Flow<GamepadEvent>
    public val capabilities: StateFlow<GamepadCapabilities>

    public suspend fun playEffect(effect: GamepadEffect): KadreResult<GamepadEffectSession>
    public suspend fun stopEffects(): KadreResult<Unit>
}

public interface GamepadEffectSession : AutoCloseable {
    public val state: StateFlow<GamepadEffectState>

    public override fun close()
    public fun requestStop()
    public suspend fun awaitTermination(): GamepadEffectOutcome
}
```

Un code natif inconnu reste inconnu. Aucun ordinal invalide n’est transformé en bouton ou axe arbitraire.

`Gamepad.state` est mis à jour avant `Gamepad.events`. Les valeurs analogiques sont normalisées dans un domaine documenté tout en conservant la valeur native optionnelle sous `@KadrePlatformApi`. La déconnexion rend l’objet terminal ; une reconnexion produit un nouveau `GamepadId`.

`playEffect` signifie que l’effet a été accepté et retourne un owner observable ; il ne signifie pas que l’effet est achevé. La déconnexion, le teardown et la perte de la lease exclusive terminent la session d’effet avec un outcome typé. Le broker process-wide applique `DeviceEffectOwnership`; un conflit retourne `AlreadyInUse` au lieu de mélanger silencieusement deux effets.

### 10.3 IME

```kotlin
public suspend fun SurfaceInput.openTextInput(
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

Une surface possède au maximum une session IME active. Une seconde ouverture retourne `KadreFailure.AlreadyInUse`; aucun remplacement silencieux. `TextRange` utilise des offsets UTF-16 dans la `String` Kotlin. Le cursor rect est exprimé dans l’espace logique de la surface et converti par le backend.

Fermer la surface, sa fenêtre propriétaire ou la session ferme la session IME enfant. La perte temporaire de focus publie un état suspendu, sans détruire automatiquement la composition ; le backend peut terminer la composition uniquement lorsque le host natif l’impose et l’annonce par un événement terminal.

### 10.4 Drag-and-drop

```kotlin
public interface DropOffer {
    public val id: DropOfferId
    public val items: List<DropItemDescriptor>
    public val state: StateFlow<DropOfferState>
}

public interface DropTransfer : AutoCloseable {
    public val items: List<DroppedItem>

    public override fun close()
}

public interface DroppedItem {
    public val descriptor: DropItemDescriptor
    public suspend fun collectBytes(
        maxBytes: Long,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit>
}
```

L’entrée d’une offre, ses mouvements, sa sortie et son drop apparaissent dans l’ordre du flux `SurfaceInput.events`. Accepter une offre lorsque le host exige une réponse synchrone utilise `InteractionAction.AcceptDrop`; l’acceptation produit ensuite un `DropTransfer` owned par la session. Une offre expirée retourne `InteractionRequired(Expired)`.

`DropItemDescriptor` expose uniquement les métadonnées réellement portables : nom d’affichage optionnel, taille optionnelle, MIME types et nature text/file/URI. Aucun backend ne fabrique un chemin de fichier sur Web ou un path accessible lorsque le sandbox ne l’accorde pas. `collectBytes` fournit des chunks détenus par l’application, chacun borné par `maxDropChunkBytes`; `maxBytes` borne le transfert total demandé par le consumer. Les handles de fichier ou `Blob` natifs restent sous `@KadrePlatformApi` et suivent le lifetime du `DropTransfer`.

### 10.5 Raw input

Le raw input est demandé explicitement, soumis aux permissions et marqué `@DelicateKadreApi` lorsqu’il contourne les protections ordinaires de focus.

### 10.6 Permissions

```kotlin
public sealed interface PermissionState {
    public data object NotDetermined : PermissionState
    public data object Granted : PermissionState
    public data class Denied(public val canRequestAgain: Boolean) : PermissionState
    public data object Restricted : PermissionState
    public data class Unavailable(public val failure: KadreFailure) : PermissionState
}
```

Chaque manager concerné expose un `StateFlow<PermissionState>` spécialisé par permission. Les changements et révocations sont observables même après un grant. Deux requêtes simultanées pour la même permission partagent une unique requête native ; annuler un waiter n’annule ni le prompt OS ni les autres waiters. Une permission process-wide est coordonnée par le broker, puis projetée dans chaque session. Lorsqu’une requête exige une interaction transitoire, l’opération retourne `InteractionRequired` et utilise le mécanisme de la section 9.6.

## 11. Capture

```kotlin
public interface CaptureManager {
    public val permission: StateFlow<PermissionState>
    public val capabilities: StateFlow<CaptureCapabilities>
    public val sources: StateFlow<CaptureSources>

    public suspend fun requestPermission(): KadreResult<PermissionState>
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
    public suspend fun awaitTermination(): CaptureOutcome
    public suspend fun collectFrames(
        collector: suspend (CaptureFrame) -> Unit,
    ): KadreResult<Unit>
}

public sealed interface CaptureSessionState {
    public data object Ready : CaptureSessionState
    public data class Streaming(
        public val revision: CaptureConfigurationRevision,
    ) : CaptureSessionState
    public data object Stopping : CaptureSessionState
    public data class Terminated(public val outcome: CaptureOutcome) : CaptureSessionState
}

public sealed interface CaptureOutcome {
    public data object SourceCompleted : CaptureOutcome
    public data class Stopped(public val reason: CaptureStopReason) : CaptureOutcome
    public data class Failed(public val failure: KadreFailure) : CaptureOutcome
}

public enum class CaptureStopReason {
    Requested,
    CollectorCancelled,
    CollectorFailed,
    ParentSessionStopping,
    PermissionRevoked,
}
```

`open` valide la requête, effectue le picker éventuel et réserve la source, mais ne commence pas à produire des frames. Une `CaptureSession` accepte exactement un appel réussi à `collectFrames` pendant sa durée de vie. Cet appel démarre la production native et un second appel, simultané ou ultérieur, reçoit `KadreFailure.AlreadyInUse`. La collection se termine avec la source, `requestStop`, la fermeture de la session applicative ou une erreur attendue de capture. Annuler le caller arrête toute la `CaptureSession` avec `CollectorCancelled`. Une exception non-cancellation du collector est propagée sans encapsulation après libération de la frame courante et produit `Stopped(CollectorFailed)` pour la capture ; si le caller ne la traite pas, les règles ordinaires du job applicatif la promeuvent séparément en `SessionOutcome.Failed(ApplicationFailure)`.

`awaitTermination()` est idempotent et retourne toujours le même `CaptureOutcome`. Une révocation de permission ferme la production et produit `Stopped(PermissionRevoked)` ; une perte de source produit `Failed(SourceLost)`. Une failure de capture ne termine pas la `KadreSession`, sauf lorsqu’une policy explicitement choisie demande `FailSession`.

`close()` délègue à `requestStop()` ; les deux sont non bloquants, thread-safe et idempotents. Après un outcome terminal, `awaitTermination()` retourne ce terminal et les autres opérations retournent `KadreFailure.Closed` sans réouvrir la source.

### 11.1 Frames

```kotlin
public interface CaptureFrame : AutoCloseable {
    public val size: PhysicalSize<Int>
    public val format: PixelFormat
    public val planes: List<PixelPlane>
    public val configurationRevision: CaptureConfigurationRevision
    public val stamp: EventStamp
    public val colorEncoding: ColorEncoding
    public val alphaMode: AlphaMode
    public val orientation: CaptureOrientation

    public override fun close()
    public fun copyPlanes(): List<ByteArray>
}

public interface PixelPlane {
    public val width: Int
    public val height: Int
    public val rowStride: Int
    public val pixelStride: Int
    public val byteOffset: Int
    public val byteCount: Int
    public val horizontalSubsampling: Int
    public val verticalSubsampling: Int
}
```

Une frame est une lease valide uniquement pendant l’appel du collector. Kadre la ferme dans un `finally`, que le collector retourne, échoue ou soit annulé. `close()` reste idempotent pour permettre une libération anticipée. `copyPlanes()` produit une copie distincte de chaque plane, détenue par l’application ; après fermeture, `copyPlanes()` échoue avec `IllegalStateException` et les vues de `PixelPlane` sont invalides. Kadre ferme aussi les frames remplacées ou écartées par la delivery policy. Le zero-copy retenable est réservé à `@KadrePlatformApi` avec un owner spécifique au backend.

Le format, les dimensions et subsampling de chaque plane, les strides, le color encoding, l’alpha et l’orientation font partie du contrat de chaque frame. `ColorEncoding` décrit au minimum les primaries, la transfer function, la matrix, le range et les métadonnées HDR connues. `copyPlanes()` copie exactement `byteCount` octets par plane, padding inclus ; aucune conversion implicite de format, packing ou espace colorimétrique n’est effectuée.

Le format, la taille et l’encodage peuvent changer pendant une session. `CaptureSessionState` contient une `CaptureConfigurationRevision`; elle est mise à jour et un événement de reconfiguration est publié avant la première frame portant la nouvelle révision. Chaque frame référence cette révision afin qu’une frame retardée ne soit jamais interprétée avec le snapshot courant incorrect.

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

    public data class Closed(
        public val resource: KadreResourceKind,
        public override val message: String,
    ) : KadreFailure

    public data class ResourceLimitExceeded(
        public val resource: KadreResourceKind,
        public val limit: Int,
        public override val message: String,
    ) : KadreFailure

    public data class InteractionRequired(
        public val reason: InteractionFailureReason,
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

public enum class InteractionFailureReason {
    Missing,
    Expired,
    Consumed,
    WrongSurface,
}

public enum class KadreResourceKind {
    Host,
    Surface,
    Window,
    WindowRequest,
    Display,
    InputDevice,
    Gamepad,
    EventCollector,
    Interaction,
    DropTransfer,
    CustomCursor,
    GamepadEffect,
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
    public val collectorRejections: Long,
    public val resourceLimitHits: Long,
    public val interactionExpirations: Long,
    public val permissionRevocations: Long,
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

Par défaut, les messages et reporters internes remplacent les titres de fenêtre, noms de périphérique, noms de source de capture, identifiants du host et chemins par des catégories stables. Les failures publiques peuvent exposer un message actionnable mais ne recopient jamais du texte saisi ou du contenu capturé. Un host peut installer un sink privé plus détaillé sous opt-in plateforme ; ce sink n’altère ni `KadreDiagnostics.events` ni l’égalité du value model public.

Toutes les valeurs de `StateFlow`, événements, specs, outcomes et capabilities sont profondément immuables après publication. Kadre effectue une copie défensive des collections et buffers mutables reçus à la frontière publique. Les listes retournées ne sont jamais des vues d’un registre mutable interne. Les seules `ByteArray` mutables quittant l’API commune sont des copies explicitement possédées par l’application, comme celles de `copyPlanes()`.

## 14. Structured concurrency et threading

```text
KadreSession Job
├── KadreApplication.run
├── lifecycle bridge
├── HostSurface
├── DisplayManager
├── WindowManager
│   └── branches Window → HostSurface
├── DeviceManager
│   └── branches InputDevice → GamepadEffectSession
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
    parentScope: CoroutineScope = lifecycleScope,
): KadreResult<KadreSession>

public fun ComponentActivity.attachKadre(
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleScope,
    application: KadreApplication,
): KadreResult<KadreSession>

public fun View.attachKadre(
    lifecycleOwner: LifecycleOwner,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleOwner.lifecycleScope,
): KadreResult<KadreSession>

public fun View.attachKadre(
    lifecycleOwner: LifecycleOwner,
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleOwner.lifecycleScope,
    application: KadreApplication,
): KadreResult<KadreSession>
```

- Une même `Activity` ou `View` ne peut avoir qu’une session active ; une seconde tentative retourne `AlreadyInUse`.
- La session est liée au `LifecycleOwner` et se termine sur sa destruction définitive.
- Le `parentScope` contrôle le dispatcher applicatif et son annulation externe ; le lifecycle du host reste terminal même si un scope fourni survit à l’`Activity` ou à la `View`.
- `ComponentActivity` expose sa fenêtre top-level et sa surface de contenu ; `View.attachKadre` expose uniquement une `HostSurface` et laisse `WindowManager.primary` à `null`.
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
- L’adaptateur crée son parent sur le dispatcher main de la scène ; le code partagé déplace explicitement les calculs lourds avec `withContext`.
- Une scène expose sa `UIWindow` comme fenêtre top-level et son contenu comme `HostSurface`; SwiftUI reste propriétaire du layout de cette surface.
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
    windowProvider: WebWindowProvider? = null,
): KadreResult<KadreSession>
```

- Attachement à un `HTMLElement` ou `HTMLCanvasElement` existant.
- L’élément attaché devient `primarySurface`; il n’est jamais exposé comme `Window` et `WindowManager.primary` reste `null`.
- `StopWhenDetached` exige un élément initialement connecté ; sinon `attachKadre` retourne `InvalidRequest`. `Manual` accepte un élément déconnecté avec lifecycle `Attached + Background + Inactive`.
- `StopWhenDetached` vérifie `isConnected` à la livraison du batch `MutationObserver` : un reparenting terminé avant cette livraison ne ferme pas la session ; un élément encore détaché la termine et sa réinsertion exige une nouvelle session.
- `Manual` ignore le détachement DOM et exige un `requestStop` explicite.
- Aucun détournement du titre comme ID DOM.
- Sessions multiples possibles sur une même page.
- Kadre ne crée jamais de `<canvas>`, `<div>` ou popup et ne choisit aucun emplacement dans le DOM. Sans `WebWindowProvider`, `requestWindow` retourne `Unsupported`. Un provider peut demander un nouveau browsing context ; celui-ci devient un nouveau host et produit `OpenedInNewSession`, jamais `OpenedHere`.
- Les opérations exigeant la transient user activation passent par `InteractionContext`. Les requêtes suspendues ordinaires retournent `InteractionRequired` lorsqu’elles arrivent trop tard.
- Même contrat public en JS et Wasm.
- Aucun faux `runApp` bloquant.
- `pagehide` et la destruction du browsing context ferment immédiatement l’admission et libèrent best-effort les bridges synchrones, mais le navigateur ne garantit pas l’achèvement d’un teardown suspendu. `awaitTermination` n’est garanti que tant que le runtime JS reste vivant ; aucune persistence ou requête réseau n’est déclenchée implicitement pendant unload.

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
- Une fenêtre desktop expose séparément son objet top-level et sa `HostSurface` de contenu.
- AppKit refuse explicitement un lancement hors du main thread au lieu de déplacer silencieusement la possession de `NSApplication`.
- Sélection typée AppKit, Win32, X11 ou Wayland.
- `DesktopHostOptions.Embedded` identifie explicitement l’intégration de boucle existante (`AppKit`, `AWT/Compose`, `JavaFX` ou pump natif fourni). `attachKadreDesktop` refuse une combinaison qu’il ne sait pas pomper ; il ne démarre jamais une seconde boucle UI cachée.
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
- `VirtualHostSurface` et `VirtualDisplayController` ;
- `VirtualInputController` ;
- un contrôleur d’interaction transitoire avec expiration déterministe ;
- `VirtualCaptureController` ;
- des scénarios d’overflow déterministes ;
- une suite de contrats réutilisable par tous les backends.

Chaque backend valide les mêmes invariants de lifecycle, threading, capabilities, fermeture, flux, handles, permissions, surface/window et routing process-wide.

Les contract tests couvrent explicitement la température, le replay, la cardinalité, la terminaison tardive des flux, les budgets agrégés, l’ordre state/event, l’isolation des collectors lents, les spans coalescés, l’expiration des interactions, les resets d’input, les races apply/close, la fermeture automatique des frames et les transitions légales du lifecycle. Des consumer tests compilent une intégration minimale Java, Swift, JS et Wasm.

## 18. Performance

Invariants mesurables :

- aucun buffer non borné ;
- aucun fan-out non borné : les allocations maximales dérivent des capacités et limites de collectors publiées par la policy ;
- aucune perte silencieuse de transition discrète ;
- aucune coroutine orpheline après teardown ;
- frames abandonnées toujours fermées ;
- coût mesuré avec zéro, un et plusieurs collectors pour les flux multicast, et avec le collector unique pour la capture ;
- benchmarks pour input haute fréquence, gamepad, état fenêtre et capture.

Les budgets chiffrés de la section 8 sont les valeurs initiales de ce snapshot. La baseline mesurée avant migration peut les faire évoluer par breaking change pendant l’incubation, mais un artifact publié ne les adapte jamais silencieusement au runtime ou au backend.

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
5. toute ressource visible par l’application est rattachée à une session ; son bridge se ferme avec elle même lorsque le host conserve le lifetime natif ;
6. les contract tests sont verts sur tous les backends disponibles ;
7. les dumps ABI ne contiennent aucun type de backend interne ;
8. les samples et la documentation n’utilisent plus l’ancienne API ;
9. `kadre-core` n’est plus importé par les consommateurs ;
10. les avertissements d’opt-in expérimentaux sont traités localement et intentionnellement.
