# New Kadre — Architecture cible de l’API publique

**Statut :** spécification d’architecture fermée révisée le 23 août 2026 ; validation d’implémentation non commencée

**Portée :** toute l’API publique de Kadre

**Compatibilité :** breaking changes autorisés pendant l’incubation

**Référence d’exécution :** les plans détaillés dérivés de ce document doivent conserver ses invariants

**Registre de migration :** `kadre/API-MIGRATION.md` attribue une décision à chaque famille de symboles de l’ABI actuelle ; `kadre/MIGRATION-AUDIT.md` ferme la revue nominative des résidus.

**Catalogue lexical :** `kadre/PUBLIC-API-CATALOG.md` énumère la surface Kotlin fermée ; les matrices normatives associées complètent ce document sans générer de source Kotlin.

**Contrats d’opération :** `kadre/OPERATION-CONTRACTS.md` ferme résultats, failures, cancellation, commit et handoff de chaque appel public.

**Profils intégrés :** `kadre/POLICY-PROFILES.md` donne la valeur exacte de chaque champ de `Default`, `Realtime` et `Recording`.

**Adapters :** `kadre/BACKEND-CAPABILITIES.md` ferme les topologies host, les fallbacks et les points d’attachement exacts.

**Interop :** `kadre/INTEROP-EXPORTS.md` distingue l’API Kotlin complète, les façades host étrangères et les escape hatches target-specific.

**Preuves :** `kadre/TEST-STRATEGY.md` définit les oracles indépendants, la contract suite, la traçabilité et les gates PR/nightly/release.

**Architecture des projets :** `kadre/PROJECT-ARCHITECTURE.md` ferme la topologie Gradle/KMP, les responsabilités, dépendances, publications et frontières KFFI.

**Niveau de fermeture :** les contrats d’architecture, la forme documentaire de l’API et la stratégie de preuve sont fermés. Les dumps ABI, headers, déclarations TypeScript et consumer compile tests restent des preuves d’implémentation à produire plus tard ; leur absence ne rouvre pas le design.

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
9. **Buffers et fan-out de production bornés.** Aucun flux d’événements ne dépend d’une file non bornée et le nombre de ses collectors admis est limité. Un `StateFlow` conserve un seul snapshot producteur, n’alloue aucune file par collector et respecte la sémantique ordinaire de kotlinx.coroutines sans refuser une collection.
10. **Breaking changes directs.** L’incubation autorise la suppression de l’ancien modèle sans maintenir deux architectures en production.
11. **Surface et fenêtre distinctes.** Un élément DOM, une `View` ou une vue UIKit n’est jamais présenté comme une fenêtre top-level fictive.
12. **Valeurs immuables, ressources vivantes.** Les value objects, événements, outcomes, collections et buffers publiés restent immuables après publication. Une collection peut contenir des handles de ressource vivants comme `Window` ou `Display` : son appartenance est un snapshot immuable, mais l’état futur de chaque handle reste observable par ses propres flows.

## 4. Frontière des projets

`PROJECT-ARCHITECTURE.md` est l’autorité normative de cette section. Le projet principal porte actuellement le nom `kadre` ; aucun de ses enfants ne reprend `kadre` dans son nom.

### 4.1 Composants publics

- `kadre` : umbrella KMP et dépendance principale standard.
- `foundation` : contrats communs et surface lexicale définie par le catalogue.
- `platform:android`, `platform:uikit`, `platform:web` et `platform:desktop` : points d’attachement et APIs target-specific transitives.
- `test` : fake host, horloge virtuelle et périphériques virtuels destinés aux tests consommateurs.
- `integration:compose`, `integration:swiftui`, `integration:awt` et `integration:javafx` : intégrations tierces optionnelles, matérialisées uniquement lorsqu’elles contiennent une implémentation réelle.

### 4.2 Composants internes

- `runtime` possède le moteur commun et les SPI d’implémentation.
- `backend:appkit`, `backend:win32`, `backend:x11` et `backend:wayland` adaptent directement KFFI au SPI desktop.
- `contracts:*`, `consumers`, `samples:*` et `benchmarks:*` fournissent preuves et parcours sans constituer une API publiée.
- Kadre ne possède aucun projet FFI, binding généré ou input de génération ; KFFI en est l’unique owner.

Les symboles de liaison nécessaires entre artifacts sont techniquement publics sous `org.graphiks.kadre.internal.*`, sans stabilité consommateur. Aucun de ces types ne peut apparaître dans une signature contractuelle.

### 4.3 Granularité

Les coroutines et les domaines capture, fenêtres, input, gamepads et devices restent dans `foundation`/`runtime`; aucun enfant `core`, `coroutines`, `capture`, `window`, `input` ou `gamepad` n’est créé. Les anciennes façades, les deux classes `EventLoop` et le module coroutine séparé disparaissent de la surface cible.

### 4.4 Packages publics communs

```text
org.graphiks.kadre
├── application
├── surface
├── window
├── interaction
├── display
├── input
├── capture
├── policy
└── diagnostics
```

L’artifact `test` expose `org.graphiks.kadre.test`. Les packages `org.graphiks.kadre.platform.*` et `org.graphiks.kadre.integration.*` sont possédés exclusivement par les composants listés dans `PROJECT-ARCHITECTURE.md`.

### 4.5 Fermeture documentaire de l’API cible

`DESIGN.md` reste l’autorité sémantique et `PUBLIC-API-CATALOG.md` l’autorité lexicale. Ils ne servent pas de permission pour improviser une déclaration publique pendant l’implémentation. Le catalogue public documentaire est fermé dans ce dossier avec, pour chaque déclaration :

- package, nom, visibilité, généricité et annotations d’opt-in ;
- membres, valeurs par défaut et variantes exhaustives des types sealed/enum ;
- unités, nullabilité, ownership, thread-safety et état après fermeture ;
- température, replay, cardinalité, terminaison et relation snapshot/événement de chaque flow ;
- failures, outcomes et capabilities associés ;
- décision d’export Java, Swift, JS et Wasm.

Une expression ouverte comme « au minimum », une variante publique non listée ou un type seulement nommé interdit de considérer le domaine comme fermé. Le catalogue est désormais l’allowlist complète : tout nom absent est interne. Il reste un artefact Markdown revu avant le code ; un dump ABI cible et les consumer compile tests ne sont produits qu’au chantier d’implémentation. Toute nouvelle déclaration découverte ensuite exige d’abord une modification explicite de la spec, du catalogue et du registre de migration, breaking change compris.

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

public class RestorationToken internal constructor(
    private val encoded: String,
) {
    public override fun equals(other: Any?): Boolean
    public override fun hashCode(): Int
    public override fun toString(): String
}
```

Contrats :

- `close()` délègue à `requestStop()` ; les deux sont non bloquants, thread-safe et idempotents ;
- `awaitTermination()` est idempotent, attend la terminaison logique publique de la session et retourne toujours le même résultat terminal ; il ne prétend pas joindre une coroutine applicative qui ignore la cancellation ;
- appeler `awaitTermination()` depuis un job enfant de la session échoue immédiatement avec `IllegalStateException` au lieu de deadlocker ;
- le retour du corps de `KadreApplication.run` ferme immédiatement l’admission de nouveaux enfants applicatifs ; le job applicatif passe en `Completing`, attend uniquement les enfants déjà admis, puis produit `SessionOutcome.Completed` et déclenche le teardown ;
- `KadreSession.requestStop()` propose `Stopped(HostRequested)` et `KadreScope.requestStop()` propose `Stopped(ApplicationRequested)` ; le premier motif d’arrêt non fatal accepté fait autorité ;
- une annulation directe du job exposé par `KadreScope.coroutineContext`, alors que le parent et le host restent actifs, produit `Stopped(ApplicationCancelled)` ;
- l’annulation du `parentScope` produit `Stopped(ParentCancelled)` ;
- le détachement définitif de l’hôte produit `Stopped(HostDetached)` ;
- une exception non gérée produit un diagnostic fatal, devient un `KadreFailure.ApplicationFailure` stable et termine la session avec `Failed` ;
- une exception de `KadreApplicationFactory.create` suit exactement le même chemin d’échec ;
- une factory crée exactement une instance de `KadreApplication` par session et peut être invoquée simultanément pour plusieurs scènes ;
- un consumer qui capture de l’état mutable dans la factory reste responsable de sa synchronisation entre sessions.

`KadreScope.coroutineContext` contient le job applicatif ordinaire, jamais le `SupervisorJob` racine de session. Il reprend le contexte du `parentScope` en remplaçant uniquement son `Job` par ce job applicatif. `KadreApplication.run` et les enfants lancés via le receiver utilisent donc le dispatcher du parent ; les appels natifs marshallent séparément vers le thread du host. Lorsque le corps de `run` retourne, Kadre complète explicitement le job applicatif : une tentative ultérieure de `launch` via un receiver capturé crée un job immédiatement annulé et ne retarde pas la terminaison. Les enfants admis auparavant restent structurés et doivent tous terminer pour qu’un retour normal devienne `Completed` ; jusque-là, la session reste `Running` mais n’admet plus de nouvel enfant applicatif. Un consumer doit utiliser `withContext` pour déplacer ses calculs lourds au lieu de supposer que le dispatcher applicatif est un thread UI ou un worker.

Lorsqu’un scope parent est lui-même possédé par le lifecycle du host, la notification terminale du host est admise avant l’annulation de ce scope et produit déterministement `HostDetached`. `ParentCancelled` est réservé à une annulation externe observée alors que le host reste attaché.

`RestorationToken` est opaque et fourni par le host pour recoller la session à l’état durable détenu par l’application. Sa représentation n’est pas publique, son `toString()` est redacted et le consumer peut seulement le comparer ou l’utiliser comme clé pendant le lifetime autorisé par le host. Kadre ne sérialise, ne persiste et ne restaure aucun état applicatif. Un lancement `HostRestoration` sans token reste autorisé lorsque le host ne possède pas d’identité persistante.

Une erreur non liée à la cancellation, observée avant `Terminated`, surclasse un motif `Stopped` ou `Completed` et produit `Failed`. La première failure admise devient la failure primaire. Toute failure ultérieure de cleanup, y compris un timeout, reste un diagnostic secondaire et ne remplace pas cette primaire. `ShutdownTimedOut` devient la failure primaire uniquement lorsque le teardown partait encore d’un outcome non fatal. Les `CancellationException` déclenchées par le teardown ne remplacent jamais le résultat.

La session passe à `Running` immédiatement avant l’appel de `KadreApplication.run`, une fois lifecycle et managers initialisés. Le code applicatif ne peut donc jamais observer un manager partiellement construit.

Le teardown suit cet ordre normatif : fermeture de l’admission des callbacks et des nouveaux enfants applicatifs, annulation du job applicatif, arrêt des captures, drop transfers, interactions et sessions IME, arrêt des effets de périphériques, fermeture ou abandon des requêtes de fenêtre, fermeture des fenêtres en ordre inverse de création, détachement de la surface primaire et des subscriptions aux brokers, puis détachement des bridges natifs. Aucun callback ne peut réintroduire une ressource après la fermeture de l’admission.

`ExecutionPolicy.shutdownTimeout` commence à l’entrée dans `Stopping` sur l’horloge monotone de la session. Il borne l’attente coopérative du job applicatif et l’achèvement des coroutines et ressources possédées par Kadre tant que le runtime continue à scheduler les jobs Kadre. À la première opportunité de scheduling suivant l’échéance, Kadre annule ses jobs internes, détache synchroniquement les derniers bridges capables de l’être, révoque tout accès applicatif aux ressources et applique la règle de priorité des failures ci-dessus. La session passe alors à `Terminated` et `awaitTermination()` retourne, même si du code applicatif non coopératif continue à s’exécuter dans son job déjà annulé.

Ce timeout n’est pas une garantie hard real-time : une boucle consumer synchrone qui monopolise l’unique dispatcher UI/JS, un processus suspendu ou un runtime détruit peut empêcher Kadre d’observer l’échéance. Le délai mesure donc du temps monotone, mais sa réaction exige une opportunité de scheduling. `SessionState.Terminated` est une frontière de ressources publique, pas une affirmation que chaque job consumer est `isCompleted`. Un job consumer encore incomplet n’est ni reparenté ni considéré achevé : il reste enfant annulé du scope fourni par le host jusqu’à ce qu’il coopère, sans accès à une ressource Kadre vivante. Kadre garantit ainsi la révocation logique et la fin de ses propres jobs lorsqu’ils peuvent être schedulés, pas la préemption de code arbitraire ni la survie du runtime.

## 6. Host SPI

```kotlin
@ExperimentalKadreApi
public interface KadreHost {
    public val platform: KadrePlatform

    public fun attach(
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
    ): KadreResult<KadreSession>
}
```

`attach` valide synchroniquement le host, le `parentScope` et la policy, puis retourne sans attendre l’exécution applicative. `parentScope.coroutineContext` doit contenir un `Job` actif ; l’absence de `Job` retourne `InvalidRequest` et un job déjà annulé retourne `ParentScopeCancelled`, sans créer de session. Un succès contient une session en état `Starting`; tout échec ultérieur devient son `SessionOutcome`.

L’admission de la session constitue le point de linéarisation entre `attach` et un détachement concurrent du host. Si le host est déjà détaché avant cette admission, `attach` échoue synchroniquement sans session. S’il se détache après, `attach` peut retourner la session `Starting`, qui termine avec `HostDetached`. La transition vers `Running` et l’admission d’un arrêt sont sérialisées : `KadreApplicationFactory.create` et `KadreApplication.run` ne commencent jamais après l’admission du motif terminal. Un `run` déjà commencé est annulé par le teardown ordinaire. Comme `create` est non suspendu, un appel déjà commencé n’est pas préempté ; son résultat tardif est ignoré et `run` n’est pas invoqué. Une exception de factory observée avant `Terminated` conserve la priorité définie en section 5, tandis qu’une exception tardive après cette frontière est seulement reportée comme erreur de cleanup et ne réouvre pas l’outcome.

Les adaptateurs officiels implémentent ce SPI. L’artifact `test` l’utilise pour exécuter exactement les mêmes contrats sans backend natif. L’annotation expérimentale permet les backends tiers pendant l’incubation sans transformer immédiatement le SPI en garantie stable.

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
    public val capabilities: StateFlow<LifecycleCapabilities>
    public val events: Flow<LifecycleEvent>
    public val signals: Flow<HostSignal>
}

public data class LifecycleCapabilities(
    public val memoryPressure: FeatureAvailability,
)

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

public enum class MemoryPressureLevel {
    Moderate,
    Critical,
}
```

Les trois axes sont orthogonaux. `Detached` est terminal et impose le snapshot canonique `Detached + Background + Inactive`. Une transition vers `Background` publie toujours `Inactive` auparavant ou dans le même snapshot atomique. Une transition vers `Active` exige `Attached + Foreground`. Les backends dédupliquent les notifications répétées et ne publient que des snapshots valides.

Les deux `StateFlow` contiennent dès la création le state courant du host, toujours `Attached`, et les capabilities initiales ; le state initial n’est pas rejoué dans `events`. Ces valeurs sont disponibles avant le passage de la session à `Running`.

Mapping normatif :

| Host | `Foreground` | `Active` | `Detached` |
|---|---|---|---|
| Android | `LifecycleOwner` au moins `STARTED` | au moins `RESUMED` et fenêtre interactive | `LifecycleOwner.onDestroy` ou premier détachement de la `View` fournie |
| UIKit | scène en foreground | `sceneDidBecomeActive` | `sceneDidDisconnect` |
| Web | document visible et élément connecté | document actif et élément recevant l’input | détachement selon la policy Web |
| Desktop `Standalone` | au moins une fenêtre de session visible | au moins une fenêtre de session active | arrêt du host standalone |
| Desktop `Embedded` | intégration host attachée et non suspendue, ou fenêtre visible | intégration host active sans fenêtre, ou fenêtre active | fermeture explicite de l’intégration |

`state` est mis à jour avant que `events` rende la transition observable. Un collector peut donc voir un état plus récent, mais jamais un état antérieur à l’événement reçu.

`signals` porte les notifications non durables du host qui ne sont pas des axes de lifecycle, notamment la pression mémoire. Elles sont hot, sans replay et suivent `KadrePolicy.hostSignals`, séparée des transitions lifecycle critiques. `LifecycleCapabilities.memoryPressure` vaut uniquement `Available`, `Unsupported` ou `Unavailable`; dans ce dernier cas sa failure vaut exactement `Closed(Host)`, `TemporarilyUnavailable` ou `PlatformFailure`. Permission et interaction n’ont aucun sens pour ce signal passif. La capability est mise à jour avant son diagnostic `CapabilityChanged(resource = Host, operation = null)`. Les profils ordinaires utilisent `Latest`; remplacer ou dropper un signal reste observable par son span ou les compteurs mais ne termine pas la session. Kadre ne fabrique pas de signal sur un host qui ne l’expose pas ; une application conserve ses propres seuils de cache et ne dépend pas de la réception d’un signal pour rester correcte.

### 7.1 Contrats normatifs des flux

```kotlin
public data class EventStamp(
    public val sequence: SessionSequence,
    public val timestamp: SessionInstant,
    public val deliverySpan: EventDeliverySpan?,
)

public data class EventDeliverySpan(
    public val firstSequence: SessionSequence,
    public val lastSequence: SessionSequence,
    public val eventCount: Long,
)

public value class SessionSequence internal constructor(public val value: Long)
public value class SessionInstant internal constructor(public val sinceStart: Duration)

public interface KadreDiagnostics {
    public val events: Flow<KadreDiagnostic>
    public val counters: StateFlow<DiagnosticCounters>
}
```

`SessionSequence` est attribuée atomiquement à l’ingress et est strictement croissante dans une session. Elle ne wrap jamais : si la prochaine valeur ne peut plus être représentée, Kadre ferme l’admission et termine la session avec `ResourceLimitExceeded(EventSequence, Long.MAX_VALUE)` sans fabriquer un dernier événement estampillé. Chaque flow public sérialise son admission et livre ses événements par séquence croissante. Deux flows distincts peuvent néanmoins être collectés ou schedulés dans un ordre différent : `SessionSequence` fournit corrélation et comparaison, pas une promesse de livraison totale inter-flows. Kadre ne retarde pas un flow pour attendre un éventuel événement d’une autre lane. Une application qui fusionne plusieurs flows accepte donc les inversions de livraison et les trous dus au filtrage ou au coalescing.

`SessionInstant` utilise la même origine monotone pour lifecycle, fenêtres, input, gamepads et arrivée des frames de capture ; deux timestamps de sessions différentes ne sont pas directement comparables. Une valeur non remplacée utilise `deliverySpan = null`. Dès qu’au moins deux événements sont agrégés, remplacés ou écartés au profit du dernier, `Coalesced` comme `Latest` portent le stamp du dernier événement conservé et un `EventDeliverySpan` allant de la première à la dernière séquence concernée, avec le nombre exact d’événements de cette catégorie dans un `Long` strictement supérieur à un. Le constructeur exige `firstSequence < lastSequence`, `eventCount > 1`, `eventCount <= lastSequence.value - firstSequence.value + 1` en arithmétique vérifiée, et `EventStamp.deliverySpan.lastSequence == EventStamp.sequence`. Ce span est un intervalle de corrélation, pas un ensemble : il peut contenir des numéros attribués entre-temps à d’autres flows ou à d’autres catégories du même flow. Il n’autorise jamais à déduire que toutes les séquences de l’intervalle ont été coalescées. Le timestamp média éventuel d’une frame reste distinct de son instant d’arrivée dans la session.

Les IDs de ressource (`SurfaceId`, `WindowId`, `DisplayId`, `DeviceId`, `GamepadId`, `CaptureSourceId`) sont opaques, à constructeur interne et valides uniquement dans leur session. `WindowCloseRequestId` et les operation IDs sont également opaques et limités à leur owner. `SessionId` identifie une session pendant la vie du processus. `WindowRequestId` est un ID de corrélation process-wide afin de traverser la création d’un nouveau host, mais ne permet aucun lookup de ressource étrangère.

| Catégorie | Température | Replay | Cardinalité | Règle d’ownership |
|---|---|---|---|---|
| `StateFlow` | hot | dernière valeur | multicast | value object immuable ou collection immuable de handles vivants |
| transitions lifecycle/window/device | hot | aucun | multicast | événement immuable estampillé |
| input et gamepad | hot | aucun | multicast | événement immuable estampillé |
| diagnostics | hot | aucun | multicast | les compteurs restent disponibles même si un événement est manqué |
| `collectFrames` | streaming structuré | aucun | un collector actif | lease fermée automatiquement après le bloc collector |

Collecter un flux d’événements ne démarre ni n’arrête sa source. Chaque collector d’événements possède une file bornée ; un collector lent est traité selon la policy sans bloquer les callbacks natifs ni les autres collectors. Le nombre de collectors d’événements par flow et par session est borné respectivement par `resources.maxEventCollectorsPerFlow` et `resources.maxEventCollectorsPerSession`, y compris pour les signaux continus et les diagnostics. Une subscription d’événements refusée se termine immédiatement avec `KadreException(KadreFailure.ResourceLimitExceeded(...))` sans modifier la source ni les autres collectors.

Le fan-out ne promet aucune identité d’objet commune entre collectors. Il produit une enveloppe événementielle immuable par collector, car deux collectors de vitesses différentes peuvent recevoir le même dernier `sequence` et le même payload logique avec des `deliverySpan` différents. Les payloads profondément immuables peuvent partager leur backing et leur coût logique selon la règle de la section 8 ; seuls le stamp de livraison et l’enveloppe sont spécifiques au collector. Un événement est entièrement figé avant d’entrer dans le code de ce collector.

Un `StateFlow` Kadre suit le contrat kotlinx.coroutines ordinaire : lire `value` ou appeler `collect` ne requiert aucune admission, une collection n’échoue jamais pour cause de budget Kadre et le flow ne termine pas. Kadre conserve un unique snapshot producteur conflated, sans file propre à chaque collector ; les coroutines et opérateurs créés par l’application restent sous son ownership et son budget. Toute valeur émise par un `Flow` public Kadre expose un `EventStamp`, y compris les host signals, outcomes d’interaction et diagnostics ; les catalogues de domaine doivent matérialiser ce membre. Lorsqu’un événement correspond à un snapshot, le `StateFlow` est mis à jour avant sa publication.

Chaque `StateFlow` constitue une cellule atomique, mais plusieurs `StateFlow` d’un même owner ne forment pas implicitement une transaction. Lorsqu’un invariant public exige de lire plusieurs valeurs ensemble, l’owner expose un snapshot composé révisionné dans un seul `StateFlow`. Sinon, les flows sont explicitement eventually consistent, la capability est mise à jour avant l’état dépendant, et le résultat de l’opération ou l’événement embarque la révision effective à utiliser. Lire un flow différent après réception d’un événement peut révéler une révision plus récente.

À la fermeture normale d’une ressource pendant que la session reste active, ses flux d’événements drainent les éléments déjà admis vers chaque collector dont le job reste actif, puis se terminent normalement. Une fermeture due à une failure termine ces collectors avec `KadreException` portant la même failure stable. La cancellation du contexte d’un collector reste prioritaire : Kadre ne retarde jamais cette cancellation pour drainer sa file.

Le teardown de la session annule d’abord le job applicatif et ne promet donc aucun drain vers ses collectors. Il peut abandonner les événements encore en file après avoir publié les snapshots terminaux et fermé ou libéré tout payload owned. Les `StateFlow` terminaux, et non la réception d’un dernier événement, constituent l’autorité après teardown. Un collector encore actif hors du sous-arbre applicatif observe la terminaison du flow, mais aucun contrat ne lui garantit les événements abandonnés par le shutdown. Un collector démarré après la terminaison observe immédiatement cette terminaison, sans replay d’événement. Les `StateFlow` ne se terminent pas : ils conservent indéfiniment leur snapshot terminal. Les implémentations ne sont donc pas contraintes d’utiliser `SharedFlow`, qui ne sait pas représenter une terminaison.

Tous les `Flow` publics restent multicast et ne transportent aucun owner closeable. Les streams de ressources utilisent des opérations suspendues structurées : `CaptureSession.collectFrames` confine chaque lease au callback unique décrit en section 11, tandis que `DroppedItem.collectBytes` transfère des copies au callback sous l’ownership explicite d’un `DropTransfer`. Aucune de ces opérations ne prétend être un `Flow` multicast.

## 8. Delivery policies et backpressure

```kotlin
public data class KadrePolicy(
    public val execution: ExecutionPolicy,
    public val lifecycleEvents: EventDeliveryPolicy,
    public val hostSignals: ContinuousDelivery,
    public val window: WindowDeliveryPolicy,
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
    public val ingressOverflow: IngressOverflowAction,
    public val collectorOverflow: CollectorOverflowAction,
)

public data class ResourceBudgetPolicy(
    public val maxEventCollectorsPerFlow: Int,
    public val maxEventCollectorsPerSession: Int,
    public val maxWindowsPerSession: Int,
    public val maxPendingWindowRequests: Int,
    public val maxPendingInteractionRequests: Int,
    public val maxConcurrentCaptureSessions: Int,
    public val maxConcurrentGamepadEffects: Int,
    public val maxConcurrentDropTransfers: Int,
    public val maxDropChunkBytes: Int,
    public val dropTransferClaimTimeout: Duration,
    public val maxRetainedPayloadBytesPerSession: Long,
    public val maxTextCodeUnitsPerValue: Int,
    public val maxMetadataCodeUnitsPerValue: Int,
    public val maxCollectionElementsPerValue: Int,
    public val maxImageBytesPerResource: Long,
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

`DiagnosticPolicy.eventBufferCapacity` s’applique séparément à l’ingress des diagnostics et à la file de chaque collector. `eventOverflow` choisit la valeur supprimée à chacun de ces deux niveaux ; cette suppression incrémente les compteurs et ne ferme ni la session ni le subsystem observé. Un overflow du flux de diagnostics ne tente pas d’émettre un second diagnostic à propos de lui-même, ce qui interdit toute récursion ; son compteur `StateFlow` suffit.

Catégories :

- état durable : `StateFlow`, conflation sur l’état courant ;
- transitions discrètes : ordre préservé, buffer borné, aucune perte silencieuse ;
- données continues : coalescing ou buffer borné selon policy ;
- capture : policy indépendante de l’input.

Les policies publiques expriment des garanties métier (`Latest`, `Buffered`, `Coalesced`) et non les détails de `MutableSharedFlow`.

`FrameDelivery` est volontairement distinct de `ContinuousDelivery` : deux frames ne sont jamais fusionnées ou « coalescées ». `Latest` conserve une seule frame en attente et ferme celle qu’elle remplace ; `Buffered` conserve un FIFO borné de frames entières et applique son action d’overflow.

```kotlin
public sealed interface ContinuousDelivery {
    public data object Latest : ContinuousDelivery
    public data object Coalesced : ContinuousDelivery
    public data class Buffered(
        public val capacity: Int,
        public val onOverflow: ContinuousOverflowAction,
    ) : ContinuousDelivery
}

public sealed interface FrameDelivery {
    public data object Latest : FrameDelivery
    public data class Buffered(
        public val capacity: Int,
        public val onOverflow: ContinuousOverflowAction,
    ) : FrameDelivery
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
    public val touchMotion: ContinuousDelivery,
    public val scroll: ContinuousDelivery,
    public val gestureChanges: ContinuousDelivery,
    public val gamepadChanges: ContinuousDelivery,
)

public data class WindowDeliveryPolicy(
    public val discreteEvents: EventDeliveryPolicy,
    public val geometryChanges: ContinuousDelivery,
    public val redrawRequests: ContinuousDelivery,
)

public data class CaptureDeliveryPolicy(
    public val events: EventDeliveryPolicy,
    public val frames: FrameDelivery,
    public val maxBufferedBytesPerSession: Long,
)
```

Mapping normatif exhaustif des flux publics :

| Flux public | Policy de livraison | Owner fermé par `CloseSource` |
|---|---|---|
| `KadreLifecycle.events` | `lifecycleEvents` | `KadreSession` |
| `KadreLifecycle.signals` | `hostSignals` | branche de signaux lifecycle |
| parties discrètes de `HostSurface.events` et `Window.events` | `window.discreteEvents` | surface ou fenêtre concernée |
| resize, move et changements géométriques de `HostSurface.events` et `Window.events` | `window.geometryChanges` | surface ou fenêtre concernée |
| `SurfaceEvent.RedrawRequested` | `window.redrawRequests` | surface concernée |
| `DisplayManager.events` | `window.discreteEvents` | branche `DisplayManager` de la session |
| `InteractionRegistration.outcomes` | `window.discreteEvents` | registration |
| `DeviceManager.events` | `deviceEvents` | branche `DeviceManager` de la session |
| parties discrètes de `SurfaceInput.events` et `Gamepad.events` | `input.discreteEvents` | `SurfaceInput` ou `Gamepad` |
| mouvements pointeur de `SurfaceInput.events` | `input.pointerMotion` | `SurfaceInput` |
| mouvements tactiles de `SurfaceInput.events` | `input.touchMotion` | `SurfaceInput` |
| scroll de `SurfaceInput.events` | `input.scroll` | `SurfaceInput` |
| mises à jour continues de pinch, pan, rotation et pression | `input.gestureChanges` | `SurfaceInput` |
| changements analogiques de `Gamepad.events` | `input.gamepadChanges` | `Gamepad` |
| `TextInputSession.events` | `input.discreteEvents` | `TextInputSession` |
| `CaptureSession.events` | `capture.events` | `CaptureSession` |
| `CaptureSession.collectFrames` | `capture.frames` | `CaptureSession` |
| `RawInputAccess.events` | `input.pointerMotion` | `RawInputAccess` |
| `KadreDiagnostics.events`, `CaptureSession.diagnostics` | `diagnostics` | uniquement le flux de diagnostics concerné |

Cette table est fermée : ajouter un `Flow` public exige d’ajouter son mapping dans la même modification. Les `StateFlow` n’y figurent pas, car leur conflation et leur absence de terminaison suivent la section 7.1. Pour les diagnostics, `DiagnosticPolicy` gouverne la capacité et le drop explicite des détails ; les compteurs correspondants restent exacts jusqu’à leur saturation explicitement signalée. Tous les collectors d’événements, diagnostics inclus, consomment les budgets `maxEventCollectorsPerFlow` et `maxEventCollectorsPerSession`.

`HostSurface.events`, `Window.events`, `SurfaceInput.events` et `Gamepad.events` sont des flows mixtes : ils préservent chacun un ordre unique tout en appliquant plusieurs policies. Leur ingress et chaque collector utilisent un scheduler borné composé d’une lane FIFO discrète et de lanes continues. À l’admission d’un événement discret, tout agrégat continu antérieur est scellé ; aucune géométrie, demande de redraw, mouvement, gesture, scroll ou valeur analogique n’est coalescée à travers cette barrière. Le scheduler choisit ensuite la plus petite `SessionSequence` disponible entre les têtes de lanes. Un événement continu postérieur à la barrière ne peut donc jamais être livré avant elle.

Chaque événement discret en attente délimite un segment de coalescing. Pour une catégorie `Latest` ou `Coalesced`, le scheduler conserve au plus une entrée par segment, soit `discreteCapacity + 1` entrées ; une entrée peut agréger plusieurs champs ou contrôles dont l’ensemble est borné par le descriptor et les budgets de payload. `Buffered(n, ...)` conserve au plus `n` entrées au total pour sa catégorie et n’agrège jamais deux segments ; atteindre cette limite applique son action d’overflow. La taille maximale du scheduler est ainsi calculable depuis la capacité discrète et chaque policy continue, sans file cachée supplémentaire. Un drop continu laisse un trou de séquence et produit le diagnostic prévu sans affecter la lane discrète. Un overflow discret ou une action continue `CloseSource`/`FailSession` applique la terminaison hors file définie pour l’owner ; il n’essaie jamais d’insérer un dernier événement dans la lane saturée.

Profils (résumé ; `POLICY-PROFILES.md` est l’autorité champ par champ) :

| Profil | Transitions discrètes | Données continues | Capture | Overflow |
|---|---|---|---|---|
| `Default` | capacité 256 | géométrie, mouvements, touch, gestes et scroll `Coalesced`; redraw, signaux et gamepad `Latest` | `Latest`, 128 MiB | collector lent annulé explicitement |
| `Realtime` | capacité 64 et scheduling prioritaire | même catégories coalescées à chaque tour du host ; redraw, signaux et gamepad `Latest` | `Latest`, 64 MiB | collector lent annulé explicitement |
| `Recording` | capacité 8192 | window/input `Buffered(8192, FailSession)` ; signaux `Buffered(64, DropOldestAndReport)` | `Buffered(3, CloseSource)`, 512 MiB | aucune perte dans les domaines enregistrables ; arrêt explicite plutôt que drop |

La capacité de transitions du tableau est utilisée pour l’ingress et pour chaque collector des lanes lifecycle, `window.discreteEvents`, device, `input.discreteEvents` et capture events.

| Profil | Collectors flow/session | Fenêtres | Requêtes fenêtre | Interactions | Captures | Effets gamepad | Drop transfers / chunk / claim |
|---|---:|---:|---:|---:|---:|---:|---:|
| `Default` | 16 / 128 | 16 | 16 | 16 | 4 | 16 | 4 / 256 KiB / 30 s |
| `Realtime` | 8 / 64 | 8 | 8 | 8 | 2 | 8 | 2 / 64 KiB / 5 s |
| `Recording` | 16 / 128 | 32 | 16 | 16 | 4 | 32 | 8 / 1 MiB / 60 s |

| Profil | Payload retenu/session | Texte/value | Métadonnée/value | Collection/value | Image/resource |
|---|---:|---:|---:|---:|---:|
| `Default` | 32 MiB | 1 048 576 unités UTF-16 | 4 096 unités UTF-16 | 4 096 éléments | 16 MiB |
| `Realtime` | 8 MiB | 262 144 unités UTF-16 | 2 048 unités UTF-16 | 2 048 éléments | 4 MiB |
| `Recording` | 128 MiB | 4 194 304 unités UTF-16 | 16 384 unités UTF-16 | 16 384 éléments | 64 MiB |

Pour `Default` et `Realtime`, l’ingress lifecycle utilise `FailSession`, les ingress window/device/input/capture events utilisent `CloseSource`, et les collectors utilisent `CancelSlowCollector`. Leurs signaux consultatifs utilisent `Latest` et ne peuvent donc pas terminer la session par saturation. `Recording` utilise `FailSession` pour les domaines enregistrables afin qu’un enregistrement incomplet ne ressemble jamais à un succès ; les signaux consultatifs et diagnostics restent explicitement exclus de cette garantie et peuvent dropper avec compteur exact jusqu’à saturation signalée.

`Default` utilise `Balanced`, un shutdown de 5 secondes et 256 diagnostics détaillés. `Realtime` utilise `LatencyFirst`, 2 secondes et 64 diagnostics. `Recording` utilise `Throughput`, 30 secondes et 8192 diagnostics. L’overflow des événements de diagnostic supprime le plus ancien événement détaillé, tandis que les compteurs restent exacts jusqu’à leur saturation signalée.

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
        frames = FrameDelivery.Latest,
    ),
)
```

Les capacités de buffer, limites de collectors d’événements, budgets de ressources, budgets capture/payload en octets, `dropTransferClaimTimeout` et `shutdownTimeout` sont finis, strictement positifs et validés à la construction. `maxEventCollectorsPerFlow` ne peut pas dépasser `maxEventCollectorsPerSession`. Construire ou `copy` une policy numériquement invalide lève immédiatement `IllegalArgumentException`, conformément aux erreurs de programmation Kotlin ; `attach` vérifie ensuite seulement la compatibilité structurelle du host et ne normalise aucune valeur. L’ingress discret choisit `CloseSource` ou `FailSession`; chaque collector peut aussi choisir `CancelSlowCollector`. Les données continues peuvent choisir `DropOldestAndReport`, `DropLatestAndReport`, `CloseSource` ou `FailSession`. Il n’existe pas de variante `Unlimited` pour une file, une durée d’attente ou une ressource possédée par Kadre.

Chaque source non mixte possède une file d’ingress bornée avant le fan-out, puis chaque collector possède sa propre file bornée. Les flows mixtes utilisent le scheduler borné défini ci-dessus à ces deux niveaux. `CloseSource` ferme exactement l’owner indiqué par la table normative ; `CancelSlowCollector` termine uniquement le collector concerné avec `SlowCollectorCancellationException`.

Les profils fixent aussi des limites finies de collectors d’événements et de ressources. `maxRetainedPayloadBytesPerSession` compte la taille logique des données variables copiées et encore retenues par les files Kadre ou leurs snapshots producteurs : deux octets par unité UTF-16, la taille exacte des `ByteArray` et la somme récursive des payloads de collection, sans prétendre modéliser l’overhead propre au runtime. Un même backing immuable partagé entre plusieurs files est chargé une seule fois tant que Kadre le retient ; deux copies défensives distinctes sont chargées séparément. Les copies dont le handoff à l’application est achevé et que Kadre ne retient plus n’y figurent plus. Une valeur sémantique, comme du texte IME, n’est jamais tronquée : une entrée applicative trop grande retourne `InvalidRequest`, et une entrée native trop grande ferme sa source avec `ResourceLimitExceeded`. Une métadonnée purement décorative trop longue devient absente avec diagnostic plutôt que tronquée. Une collection native dépassant sa limite rend l’inventaire concerné `Unavailable(ResourceLimitExceeded)` ; Kadre ne publie pas un inventaire partiel comme complet.

Tous les calculs de coût utilisent une arithmétique vérifiée : une addition ou multiplication qui dépasserait le type numérique est traitée comme un dépassement de budget avant allocation, jamais comme un wraparound. `maxImageBytesPerResource` couvre la somme des représentations encodées et décodées simultanément retenues par Kadre pour une icône ou un curseur ; ces mêmes octets comptent aussi une seule fois dans le total de payload retenu de la session. Dimensions, strides et nombre de pixels sont validés avec la même arithmétique avant décodage afin qu’une petite entrée compressée ne contourne pas le budget. Les pools de capture restent comptés par `maxBufferedBytesPerSession`, pas une seconde fois comme payload générique.

`maxWindowsPerSession` couvre les fenêtres vivantes, pas seulement les requêtes pendantes. `Default` et `Realtime` routent les gamepads vers la session active ; `Recording` les route vers toutes les sessions foreground. Les trois profils utilisent un ownership exclusif des effets. Seule une policy custom peut demander `SharedWhenSupported`, puis uniquement si le backend garantit cette capability. Atteindre un budget retourne `KadreFailure.ResourceLimitExceeded`; Kadre n’alloue jamais d’abord pour diagnostiquer ensuite.

`ExecutionPriority` influence uniquement les workers internes et leur cadence de réveil. Les dispatchers UI/main propriétaires restent imposés par le host et ne sont jamais remplaçables par une policy applicative. `shutdownTimeout` suit le contrat schedulable et la priorité des failures de la section 5 ; son dépassement produit toujours un diagnostic fatal et devient `ShutdownTimedOut` seulement en l’absence de failure primaire antérieure.

`Coalesced` a une sémantique propre au type : une position, taille, pression ou valeur analogique absolue conserve la dernière valeur par surface/contact/contrôle ; une demande de redraw conserve un marqueur par surface ; les deltas de pointeur, pan, rotation et scroll s’additionnent ; les facteurs de pinch se composent multiplicativement ; des `MemoryPressure` agrégées conservent le niveau le plus sévère observé. `Latest` conserve une valeur par clé logique documentée — kind de signal, surface de redraw, contact tactile ou contrôle gamepad — afin qu’une catégorie ne remplace pas silencieusement une autre ; son `EventDeliverySpan` rend les remplacements observables dès qu’il y en a au moins un. `Latest` ferme aussi la valeur remplacée lorsqu’elle possède une ressource. Toute nouvelle catégorie continue doit définir sa clé et sa réduction `Coalesced` dans le catalogue fermé avant d’être exposée.

Le coalescing peut avoir lieu une première fois à l’ingress puis une seconde fois dans la file d’un collector. Cette composition est aplatie : un événement sans span compte pour un événement source, un événement déjà agrégé contribue son `eventCount`, les comptes sont additionnés avec arithmétique vérifiée et les bornes deviennent la première `firstSequence` et la dernière `lastSequence`. L’enveloppe intermédiaire ne compte jamais comme un événement supplémentaire. Un drop ou compteur de perte utilise de même le nombre d’événements sources représentés, pas seulement le nombre d’entrées de file supprimées.

La policy exposée par `KadreScope.policy` est celle effectivement garantie. `KadrePolicies.Default` constitue le minimum structurel obligatoire de tout host adapter déclaré supporté : son `attach` ne peut pas échouer avec `UnsupportedPolicy`. Ses limites sont des plafonds appliqués paresseusement, pas une préallocation de toute la mémoire ni une promesse qu’une capability native absente ou une requête arbitrairement coûteuse deviendra disponible. `UnsupportedPolicy` à l’attach concerne uniquement l’incapacité structurelle à implémenter un mode de delivery ou d’ownership. Une fenêtre, image ou configuration capture particulière qui dépasse ensuite un budget retourne `ResourceLimitExceeded` à son opération ; elle ne rend pas rétroactivement la policy unsupported. Un profil personnalisé, `Realtime` ou `Recording` structurellement impossible échoue pendant `attach` ; aucun profil n’est dégradé silencieusement. `Recording` garantit seulement l’absence de perte ajoutée par Kadre dans ses domaines enregistrables après l’ingress natif ; une coalescence ou perte imposée en amont par l’OS est signalée lorsqu’elle est détectable.

Si une capability dynamique change après `attach` et rend une garantie impossible, le backend met d’abord à jour la capability puis ferme la source concernée ou la session selon l’action d’overflow/failure configurée. Il ne remplace jamais la policy exposée, ne redimensionne pas silencieusement les buffers et ne bascule pas vers un profil plus faible.

Les callbacks natifs ne sont jamais bloqués pour promettre un lossless impossible. Toute perte incrémente synchroniquement un compteur typé dans `KadreDiagnostics.counters`, puis tente d’émettre un diagnostic détaillé, sauf lorsque la perte concerne précisément le flux de diagnostics. La garantie ne dépend donc jamais de la livraison de ce flow lui-même.

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

`KadreScope.primarySurface` contient au plus une surface racine directement fournie par le host. Une session headless ou un host desktop process-level utilise `null`, y compris après ouverture de fenêtres ; leurs contenus restent accessibles par `Window.surface`. Android Activity et UIKit exposent la même instance de surface par `primarySurface` et `WindowManager.primary.surface`; Android View et Web exposent seulement `primarySurface`. Une surface ne prétend jamais posséder un titre, une position dans le bureau, des décorations ou un mode top-level. Lorsqu’une fenêtre possède une zone de contenu distincte, `Window.surface` référence sa `HostSurface`; fermer la fenêtre ferme cette surface. Une surface directement fournie par le host n’est pas fermée comme un objet natif par l’application : le teardown détache uniquement les bridges Kadre.

Le noyau portable fermé de `SurfaceState` contient l’attachement `Attached` ou `Detached`, la taille logique et physique, le scale factor, les safe-area insets, la visibilité, l’occlusion connue, le focus, le thème, les valeurs effectives de cursor/pointer capture/hit testing/default input behavior et un `SurfaceRevision`. `Detached` est terminal et conserve les dernières valeurs connues ; aucune extension publique commune ne peut ajouter un autre champ sans repasser par la fermeture documentaire. Les conversions utilisent la taille et le scale factor d’un même snapshot atomique. Les coordonnées d’input sont relatives à cette surface.

`SurfaceEvent` porte resize, scale change, focus, visibilité/occlusion, theme et `RedrawRequested`. `requestRedraw()` est thread-safe, non bloquant et peut être coalescé par le host ; il ne dessine rien et n’impose aucune cadence. Une surface vivante garantit soit l’admission de la requête, soit une failure explicite ; une surface terminale retourne `Closed`. `SurfaceUpdate` regroupe uniquement cursor visible/custom, pointer capture, hit-testing et suppression best-effort des default actions natives. Son outcome est `Applied` ou `PartiallyApplied`; contrairement à une fenêtre top-level, une surface host-owned ne publie pas d’operation ID `Accepted` différée. L’image d’un cursor custom est copiée à l’admission, appartient à la surface jusqu’à remplacement ou fermeture et n’introduit aucun handle closeable public.

Le détachement rend d’abord les capabilities indisponibles, publie ensuite `SurfaceState.Detached`, termine `SurfaceInput` et `Surface.events`, puis remplace `KadreScope.primarySurface` par `null` lorsque cette surface était primaire. Un handle conservé, notamment `Window.surface`, reste lisible et expose indéfiniment ce snapshot terminal ; toute opération retourne `KadreFailure.Closed`.

### 9.2 Displays

```kotlin
public interface DisplayManager {
    public val state: StateFlow<DisplayManagerState>
    public val events: Flow<DisplayEvent>

    public suspend fun requestAccess(): KadreResult<DisplayManagerState>
}

public data class DisplayManagerState(
    public val inventory: DisplayInventory,
    public val capabilities: DisplayCapabilities,
    public val revision: DisplayManagerRevision,
)

public sealed interface DisplayInventory {
    public data class Enumerated(
        public val primary: Display?,
        public val displays: List<Display>,
    ) : DisplayInventory

    public data object PermissionRequired : DisplayInventory
    public data class PermissionDenied(public val canRequestAgain: Boolean) : DisplayInventory
    public data class Unavailable(public val failure: KadreFailure) : DisplayInventory
}

public interface Display {
    public val id: DisplayId
    public val state: StateFlow<DisplayState>
}
```

`DisplayManager.state` est l’unique snapshot atomique de l’inventaire, des capabilities d’énumération et de leur révision. Les displays remplacent `MonitorHandle` et `VideoMode`. Leur inventaire est observable et ne fabrique pas de monitor synthétique pour masquer une absence d’énumération. Un backend peut néanmoins exposer explicitement un display de type `HostViewport` lorsqu’il ne représente que le viewport courant. `DisplayState` décrit ce type, le nom optionnel, les bounds physiques dans l’espace du bureau virtuel, la work area, le scale factor, le mode courant et les modes réellement connus.

`Display` est un handle vivant tant qu’il appartient à `DisplayInventory.Enumerated.displays`. Son `DisplayState` contient un `DisplayConnectionState`; une disparition publie d’abord l’état terminal `Disconnected`, retire ensuite le handle de `DisplayManagerState.inventory` dans une nouvelle révision, puis admet `DisplayManager.events.Removed` avec cette révision. Comme le state du handle et celui du manager sont deux cellules distinctes, une lecture concurrente peut brièvement voir le handle terminal dans l’ancien inventaire, mais jamais le nouvel inventaire avec un état non terminal ni l’événement avant les deux mises à jour. Le handle retiré conserve son ID et son dernier snapshot terminal pour les références existantes. Une réapparition après le retrait crée un nouveau `DisplayId`, même si le backend reconnaît le même matériel ; aucune identité persistante cross-session ou cross-connexion n’est inférée.

Les coordonnées du bureau virtuel sont physiques, peuvent être négatives et n’ont aucune conversion logique globale : la conversion logique/physique utilise toujours le scale factor de la surface ou du display ciblé, avec une règle d’arrondi documentée par l’opération. Une modification d’échelle ou de mode incrémente la révision avant l’événement correspondant.

`requestAccess()` retourne `Success(DisplayManagerState)` uniquement lorsque l’inventaire effectivement publié est `Enumerated` après un grant ou `PermissionDenied` après un refus utilisateur ; une décision attendue n’est pas une panne de transport. `Failure(UserCancelled)` représente uniquement un picker que le host permet de fermer sans décision, et les erreurs d’invocation ou de plateforme utilisent leurs failures typées. Une failure persistante appartenant au domaine durable fermé de `OPERATION-CONTRACTS.md` publie d’abord `Unavailable` avec la même reason puis la retourne ; `UserCancelled` et `InteractionRequired` conservent le snapshot précédent. `Unavailable` reste l’état durable d’un manager incapable d’énumérer pour une raison autre qu’un refus permission représentable. Un dépassement de la limite d’inventaire publie `Unavailable(ResourceLimitExceeded)`, rend les displays retirés terminaux puis ferme `DisplayManager.events` avec la même failure ; ce manager ne republie ensuite aucun inventaire partiel ou prétendument récupéré.

### 9.3 WindowManager

```kotlin
public interface WindowManager {
    public val state: StateFlow<WindowManagerState>

    public suspend fun requestWindow(
        spec: WindowSpec = WindowSpec(),
    ): KadreResult<WindowRequest>
}

public data class WindowManagerState(
    public val primary: Window?,
    public val windows: List<Window>,
    public val capabilities: WindowManagerCapabilities,
    public val revision: WindowManagerRevision,
)

public suspend fun WindowManager.requestWindow(
    configure: WindowSpecBuilder.() -> Unit,
): KadreResult<WindowRequest>
```

`WindowManager.state` est l’unique snapshot atomique d’appartenance. `primary`, lorsqu’elle existe, est exactement l’une des fenêtres de `windows`, avec le même handle et le même ID ; les IDs de la liste sont uniques. `windows` est ordonnée par admission croissante dans la session et cet ordre reste stable jusqu’au retrait d’une fenêtre. `primary` reste `null` pour une session attachée uniquement à un élément DOM, une `View`, une vue UIKit embarquée ou un host headless. La surface correspondante reste accessible via `KadreScope.primarySurface`. Toute modification d’appartenance, de fenêtre primaire ou de capabilities incrémente `WindowManagerRevision`; les helpers de lecture éventuels sont dérivés de `state.value` et ne créent pas d’autres `StateFlow`.

```kotlin
public interface WindowRequest : AutoCloseable {
    public val id: WindowRequestId
    public val state: StateFlow<WindowRequestState>

    public override fun close()
    public suspend fun cancel(): WindowCancellationOutcome
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
- annuler uniquement la coroutine qui exécute `await()` annule cette attente, pas la `WindowRequest`; le caller doit invoquer `cancel()` ou `close()` pour agir sur l’opération.
- l’outer `KadreResult` de `requestWindow` échoue uniquement si la requête ne peut pas être admise (spec invalide, session fermée ou budget de requêtes atteint). Lorsqu’`OpenedHere` est une issue possible, un slot provisoire sous `maxWindowsPerSession` accompagne l’admission et est conservé uniquement par cet outcome ; une requête garantie `OpenedInNewSession` n’en consomme pas. La nouvelle session applique son propre budget à sa fenêtre primaire. Une décision du host appartient au `WindowRequestOutcome` observable.
- `cancel()` distingue l’annulation avant commit, une demande d’annulation encore en attente, une terminaison déjà connue et un commit natif trop tardif pour être annulé ; `TooLate` laisse la requête en attente de son vrai résultat.
- `cancel()` est idempotent : plusieurs callers observent une décision compatible et une seule transition terminale fait autorité.
- `close()` est non bloquant et abandonne le handle. Tant que la requête est pending, il tente une annulation best-effort ; si l’opération native reste irréversible, le handle atteint `RequesterDetached` tandis que le host peut encore créer une session sans réintroduire de ressource dans la session demandeuse. Après un outcome terminal, `close()` ne ferme jamais la `Window` ou la nouvelle session déjà transférée par cet outcome.
- Desktop peut atteindre `OpenedHere`.
- UIKit atteint `OpenedInNewSession` lorsque l’OS connecte la nouvelle `UIWindowScene` et que sa session est attachée avec la factory du host.
- La nouvelle application reçoit `KadreLaunchReason.AdditionalHostRequested` et l’`originatingRequestId`.
- Une plateforme sans multi-window crée une requête immédiatement `Rejected(Unsupported)` ; elle ne retourne jamais un faux succès.
- Fermer la session demandeuse appelle `close()` sur ses requêtes encore `Pending`; une opération annulable devient `Cancelled`, une opération native déjà irréversible devient `RequesterDetached`. Une nouvelle session déjà ouverte ou créée ensuite n’est pas fermée.

`WindowManagerState.windows` ne contient que les fenêtres top-level appartenant à la session courante. Une fenêtre annoncée par `OpenedInNewSession` n’y apparaît jamais. Kadre ne crée jamais automatiquement un élément DOM, une vue, une activité ou une scène pour rendre `primary` non nul.

`OpenedInNewSession` n’expose volontairement aucun `WindowId` étranger : les IDs de ressource ne sont utilisables que dans leur session. La nouvelle application retrouve sa propre fenêtre via son `WindowManager` et corrèle le lancement grâce à `originatingRequestId`.

### 9.4 WindowSpec

`WindowSpec` est un snapshot immuable créé par constructeur ou DSL. Il remplace la longue `data class WindowAttributes` évolutive. Le `WindowSpecBuilder` est éphémère, non thread-safe et ne doit pas être conservé après le retour du bloc.

```kotlin
val result = scope.windows.requestWindow {
    title = "Kadre"
    contentSize = LogicalSize(1280.0, 720.0)
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
    public suspend fun requestAttention(attention: WindowAttention): KadreResult<Unit>
    public suspend fun close(): KadreResult<WindowCloseOutcome>
    public suspend fun respondToCloseRequest(
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): KadreResult<WindowCloseResponseOutcome>
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

public enum class WindowProperty {
    Title,
    OuterPosition,
    ContentSize,
    MinimumSize,
    MaximumSize,
    Resizable,
    Fullscreen,
    Decorations,
    SystemButtons,
    Level,
    Transparency,
    Blur,
    Icon,
    ContentProtection,
}

public sealed interface WindowCloseOutcome {
    public data object Closed : WindowCloseOutcome
    public data class Accepted(public val operationId: WindowOperationId) : WindowCloseOutcome
}

public enum class WindowCloseDecision {
    Accept,
    Reject,
}

public sealed interface WindowCloseResponseOutcome {
    public data object KeptOpen : WindowCloseResponseOutcome
    public data class Closing(public val operationId: WindowOperationId) : WindowCloseResponseOutcome
    public data object TooLate : WindowCloseResponseOutcome
    public data object AlreadyResolved : WindowCloseResponseOutcome
}
```

`WindowState` est le snapshot effectif, cohérent et atomique ; il contient toutes les propriétés persistantes mutables de `WindowSpec`/`WindowUpdate`, dont l’icône immuable, un `WindowRevision` strictement croissant et une phase `Open`, `Closing` ou `Closed`. `outerPosition` est observée par `outerBounds.origin` lorsque le host sait publier ses bounds ; une capability d’outer position supportée impose cette observabilité. Le snapshot ne représente jamais simplement la valeur demandée. `Closed` est terminal et conserve le dernier état effectif connu. Les mutations suspendues effectuent le marshalling vers le thread hôte.

Le snapshot et les updates top-level couvrent titre, géométrie externe, min/max/fullscreen, décorations, boutons système, level, transparence/blur, icon et content protection uniquement lorsque la capability correspondante existe. L’attention utilisateur est une commande dédiée non persistante via `requestAttention`. Les événements close requested, moved et changement de mode restent sur `Window.events`; les événements de contenu et d’input restent sur `Window.surface`.

`Window.apply` n’est pas transactionnel : les plateformes natives ne peuvent pas garantir un rollback atomique d’un lot de propriétés. Toute application partielle énumère les champs rejetés et leurs `KadreFailure`. `Accepted` signifie que le host a accepté une opération visuellement asynchrone ; l’achèvement est observé dans `Window.state` et `Window.events` avec le même `operationId`.

`WindowEvent.GeometryChanged` suit `window.geometryChanges`; `PropertiesChanged`, `CloseRequested` et `Closing` suivent `window.discreteEvents`. Une même révision peut produire au plus un événement de chacune des deux premières catégories, dans l’ordre `GeometryChanged` puis `PropertiesChanged`, après publication atomique du state. `CloseRequested` contient un `WindowCloseRequestId`, une raison typée, `canReject`, une deadline monotone optionnelle et son stamp. Le backend a déjà différé la fermeture native avant de publier cet événement. Une fenêtre n’a qu’une requête close pendante ; les répétitions natives sont coalescées jusqu’à sa résolution. `respondToCloseRequest(Accept)` produit `Closing`, tandis que `Reject` produit `KeptOpen` seulement lorsque `canReject`; après commit ou deadline, la réponse produit `TooLate`. Si le host ne peut pas différer la fermeture, il ne publie pas un faux `CloseRequested` : il passe directement à `Closing` avec une raison forcée observable.

`Window.close()` accepte implicitement toute requête pendante. `Closed` signifie que le snapshot est déjà terminal ; `Accepted(operationId)` signifie que le host a committé une fermeture asynchrone dont l’achèvement reste observable dans `Window.state` et `Window.events`. L’appel est idempotent : pendant `Closing`, il retourne le même operation ID, et après `Closed`, il retourne `Closed`. Une impossibilité d’admettre ou de demander la fermeture reste une failure externe de `KadreResult`.

La première décision ou fermeture forcée gagne la race. Répéter la même réponse avant résolution retourne le même outcome ; une décision contradictoire ou tardive retourne `AlreadyResolved` ou `TooLate` sans modifier le host. L’ordre terminal est : `WindowState.Closing`, détachement terminal de `Window.surface`, `WindowState.Closed`, retrait atomique de `WindowManagerState.windows` et de `primary` le cas échéant, puis terminaison de `Window.events`. Un handle `Window` conservé reste lisible avec son dernier snapshot et son ID.

Les appels concurrents à `apply` reçoivent un `WindowOperationId` à l’admission et sont sérialisés dans cet ordre par fenêtre. Une cancellation avant le commit natif retire l’opération ; après commit, elle ne provoque jamais de rollback implicite et le résultat reste observable par son ID. `close` ferme l’admission, fait échouer les opérations non committées avec `KadreFailure.Closed` et attend les opérations déjà committées uniquement dans la limite du shutdown timeout. Les changements externes au processus utilisent `operationId = null` et incrémentent quand même la révision.

`requestAttention(None)` annule best-effort une demande précédemment admise pour cette fenêtre ; les deux autres valeurs demandent l’attention au niveau supporté. Une valeur absente des contraintes de `WindowCapabilities.attention` lorsque cette capability est `Supported` retourne `InvalidRequest("attention")`; une capability structurellement absente retourne `Unsupported(RequestWindowAttention)`. Cette capability n’utilise pas `RequiresPermission`, car aucune `KadrePermission` du catalogue ne représente l’attention utilisateur. `Success(Unit)` signifie uniquement que la commande a été admise par le host, sans promesse que l’OS la rende visible ni qu’un état durable existe. Les opérations contextuelles conservent ainsi des verbes dédiés au lieu d’être cachées dans `WindowUpdate` : pointer lock, system drag et attention utilisateur.

`Transparency` désigne l'opacité et le readback de la fenêtre native. Une
translucidité visible demande un contenu à alpha dessiné par l'application ;
Kadre ne possède pas de renderer et ne fait aucune promesse sur le compositor.
Sur AppKit, `contentProtection` reste
`Unsupported(UpdateWindow)` : `NSWindowSharingNone` est legacy et inutilisable
comme mécanisme de sécurité de capture, donc Kadre ne formule aucune promesse
anti-capture. En standalone AppKit, l'attention est brokerisée au niveau du
processus tout en restant possédée par sa fenêtre ; en embedded elle exige un
opt-in explicite. Dans les deux cas, l'effet visuel reste soumis à l'OS, à
l'utilisateur et au host.

### 9.6 Interactions transitoires

Certaines APIs exigent une activation utilisateur synchrone ou un serial attaché à l’événement natif : fullscreen et pointer lock Web, ouverture d’un browsing context, system drag AppKit, drag/resize Wayland et menus contextuels natifs. Une livraison différée par `Flow` ne peut pas préserver honnêtement cette autorité.

```kotlin
public fun HostSurface.installInteractionHandler(
    handler: InteractionHandler,
): KadreResult<InteractionRegistration>

public suspend fun HostSurface.armInteraction(
    action: InteractionAction,
    options: InteractionArmOptions,
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

@DelicateKadreApi
public fun interface InteractionHandler {
    public fun onInteraction(context: InteractionContext, event: InteractionEvent)
}

public interface InteractionContext {
    public val token: InteractionToken
    public fun request(action: InteractionAction): KadreResult<InteractionRequestId>
}
```

Ce handler non suspendu constitue l’unique escape hatch au modèle coroutine principal. Il s’exécute dans le callback natif, doit retourner immédiatement et ne peut effectuer que les `InteractionAction` bornées exposées par le contexte. Ses invocations sont sérialisées et non réentrantes par surface ; un callback natif imbriqué ne réutilise jamais le handler ni le token courants et son input ordinaire est admis après le retour lorsque le host permet de le différer. `InteractionEvent` reçoit son stamp avant l’appel du handler ; le même input est ensuite publié avec ce stamp dans `SurfaceInput.events`. Le token est opaque, limité à la session et à la surface d’origine, single-use pour les actions qui consomment l’autorité native et invalide dès le retour du callback. Le conserver est autorisé mais toute réutilisation retourne `KadreFailure.InteractionRequired` avec un motif typé `Missing`, `Expired`, `Consumed` ou `WrongSurface`.

Une surface accepte au maximum un handler actif. `InteractionRegistration.outcomes` corrèle chaque `InteractionRequestId` avec son résultat terminal après le retour du callback ; fermer la registration empêche toute nouvelle invocation et ne révoque pas une action native déjà committée. Une exception du handler est capturée avant de retraverser la frontière native, produit `ApplicationFailure` et déclenche l’arrêt de la session selon les règles applicatives ordinaires.

Une application peut aussi pré-armer une action compatible via une requête observable owned par la surface. `InteractionArmOptions` fixe un trigger typé — prochaine activation éligible, pression pointeur filtrée ou pression clavier filtrée — et une expiration monotone strictement positive. Une surface n’accepte qu’un `ArmedInteraction` pendant ; un second reçoit `AlreadyInUse`. Un événement non correspondant ne le consomme pas. Le détachement, la perte durable de capability, l’expiration ou `close()` produisent un outcome terminal explicite.

`ArmedInteraction.await()` est idempotent. La cancellation d’un waiter ne désarme pas l’action et n’affecte aucun autre waiter ; seul `close()`, l’expiration, le détachement ou une transition native documentée modifie son état.

Sur un événement correspondant, le handler installé reçoit d’abord l’interaction. S’il consomme le token, l’action armée reste pendante ; sinon le backend exécute l’unique action armée avant de quitter le callback. Un token ne committe jamais plus d’une action nécessitant une autorité single-use. `InteractionRegistration.outcomes` utilise la policy des événements de fenêtre ; chaque requête acceptée occupe le budget `maxPendingInteractionRequests` jusqu’à son outcome terminal. Les capabilities indiquent quelles actions et quels triggers supportent ce mode. Aucune API suspendue ordinaire ne prétend prolonger une user activation native.

AppKit peut admettre `BeginWindowMove` uniquement depuis une vraie pression
pointeur, pendant le callback natif synchrone qui porte le token single-use.
Le backend transmet alors l'événement à `performWindowDragWithEvent` avant de
publier l'input immuable ordinaire ; un handler absent, un rejet ou une autre
action laisse l'input régulier continuer normalement. Il ne synthétise ni
pression ni déplacement, et les interactions armées restent distinctes de ce
chemin.

### 9.7 Capabilities

```kotlin
public sealed interface FeatureAvailability {
    public data object Unsupported : FeatureAvailability
    public data object Available : FeatureAvailability
    public data class RequiresPermission(public val permission: KadrePermission) : FeatureAvailability
    public data class RequiresInteraction(
        public val kind: InteractionKind,
    ) : FeatureAvailability
    public data class Unavailable(public val failure: KadreFailure) : FeatureAvailability
}

public sealed interface Capability<out Constraints> {
    public data class Unsupported(
        public val failure: KadreFailure.Unsupported,
    ) : Capability<Nothing>
    public data class Supported<Constraints>(
        public val constraints: Constraints,
        public val availability: FeatureAvailability,
    ) : Capability<Constraints>
}

public data class LogicalSizeRange(
    public val minimum: LogicalSize?,
    public val maximum: LogicalSize?,
    public val increments: LogicalSize?,
)
```

Chaque champ typé `Capability` — surface, display, fenêtre, input, gamepad ou capture — contient les contraintes propres à son domaine. `Capability.Unsupported` décrit l’absence structurelle d’une fonctionnalité appelable avec sa failure typée ; `Capability.Supported` contient les préconditions dynamiques et le domaine accepté, et son `availability` ne vaut jamais `FeatureAvailability.Unsupported`. Une fonctionnalité sans contrainte utilise `Unit`. Les capabilities sont prédictives et ne constituent jamais une réservation : chaque opération revalide disponibilité et contraintes à son point d’admission ou de commit, puis son résultat reste l’autorité finale.

`FeatureAvailability.Unsupported` décrit l’absence structurelle d’une observation ou sous-feature qui n’a pas de verbe public propre, par exemple touch, gestures, drag-and-drop, host picker ou capture régionale. `RequiresPermission` et `RequiresInteraction` décrivent une précondition actuellement satisfaisable. `Unavailable.failure` admet uniquement `PermissionDenied`, `TemporarilyUnavailable`, `AlreadyInUse`, `Closed`, `ResourceLimitExceeded`, `SourceLost` ou `PlatformFailure`; `Unsupported`, `UserCancelled`, `InvalidRequest`, `StaleRevision`, `InteractionRequired` et les failures de session n’y sont jamais stockés. Une permission définitivement refusée utilise `Unavailable(PermissionDenied)`; une permission encore demandable utilise `RequiresPermission`.

Les raisons dynamiques restent typées : une application branche sur les variantes et champs stables de `FeatureAvailability` ou `KadreFailure`, jamais sur `message`. Les changements de permission, interaction, focus ou disponibilité mettent à jour la capability avant de publier le diagnostic correspondant.

## 10. Périphériques et input

```kotlin
public interface DeviceManager {
    public val state: StateFlow<DeviceManagerState>
    public val events: Flow<DeviceLifecycleEvent>

    public fun device(id: DeviceId): InputDevice?
    public fun gamepad(id: GamepadId): Gamepad?
}

public data class DeviceManagerState(
    public val inventory: DeviceInventory,
    public val revision: DeviceManagerRevision,
)

public sealed interface DeviceInventory {
    public data class Enumerated(
        public val devices: List<InputDevice>,
        public val gamepads: List<Gamepad>,
    ) : DeviceInventory

    public data object Unsupported : DeviceInventory
    public data class Unavailable(public val failure: KadreFailure) : DeviceInventory
}
```

`DeviceManager.state` est l’unique inventaire atomique des périphériques et gamepads projetés dans la session. `DeviceInventory.Enumerated` contient deux listes complètes pour la révision ; `Unsupported` décrit un adapter sans observation de périphériques et laisse `events` ouvert mais silencieux jusqu’au teardown ; `Unavailable` décrit une source auparavant prévue mais devenue inutilisable et ne transporte aucune liste. Aucun de ces états n’est confondu avec un inventaire complet vide. Les lookups consultent ce snapshot publié et retournent `null` pour un ID absent, `Unsupported` ou `Unavailable`. Chaque `DeviceLifecycleEvent` référence la `DeviceManagerRevision` publiée avant lui. Si une limite d’inventaire est dépassée ou si la source devient inutilisable de manière terminale, le manager publie `Unavailable`, rend les handles retirés terminaux, puis ferme son flux d’événements avec la même failure. Ce manager ne récupère pas ensuite silencieusement ; une nouvelle session est nécessaire pour recréer la projection.

Les identifiants de device sont des `value class` opaques avec constructeur interne. Leur unicité et leur validité sont limitées à une `KadreSession`; ils ne servent pas d’identifiants persistants entre lancements. Tous les événements portent l’`EventStamp` de la session.

Un même gamepad physique peut être projeté dans plusieurs sessions avec des `GamepadId` distincts. `ActiveSessionOnly` route ses changements vers la session possédant la surface focalisée la plus récemment activée ; aucune session background n’est éligible et l’absence de session active suspend la livraison. `AllForegroundSessions` duplique explicitement les observations vers chaque session foreground avec un stamp propre à chacune. Le descriptor ne contient aucun identifiant persistant permettant de recoller implicitement deux projections entre sessions.

### 10.1 Input par surface

```kotlin
public interface SurfaceInput {
    public val events: Flow<InputEvent>
    public val state: StateFlow<SurfaceInputState>
    public suspend fun openTextInput(config: TextInputConfig): KadreResult<TextInputSession>
}

public data class SurfaceInputState(
    public val keyboard: KeyboardState,
    public val pointers: List<PointerState>,
    public val touches: List<TouchState>,
    public val modifiers: KeyboardModifiers,
    public val capabilities: InputCapabilities,
    public val revision: InputStateRevision,
)

public sealed interface InputEvent {
    public val stamp: EventStamp
    public val deviceId: DeviceId?
    public val stateRevision: InputStateRevision

    public data class Key(
        public val physicalKey: PhysicalKey,
        public val logicalKey: LogicalKey,
        public val location: KeyLocation,
        public val keyState: KeyState,
        public val repeat: Boolean,
        public val modifiers: KeyboardModifiers,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class PointerEntered(
        public val pointerId: PointerId,
        public val kind: PointerKind,
        public val position: LogicalPoint,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class PointerLeft(
        public val pointerId: PointerId,
        public val kind: PointerKind,
        public val lastPosition: LogicalPoint?,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class PointerMoved(
        public val pointerId: PointerId,
        public val kind: PointerKind,
        public val position: LogicalPoint,
        public val delta: LogicalDelta,
        public val pressure: Double?,
        public val pen: PenState?,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class PointerButtonChanged(
        public val pointerId: PointerId,
        public val kind: PointerKind,
        public val button: PointerButton,
        public val buttonState: PointerButtonState,
        public val position: LogicalPoint,
        public val pressure: Double?,
        public val pen: PenState?,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class Scrolled(
        public val delta: ScrollDelta,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class TouchChanged(
        public val touchId: TouchId,
        public val phase: TouchPhase,
        public val position: LogicalPoint,
        public val pressure: Double?,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class Gesture(
        public val kind: GestureKind,
        public val phase: TouchPhase,
        public val delta: LogicalDelta?,
        public val scale: Double?,
        public val rotationRadians: Double?,
        public val pressure: Double?,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class DropEntered(
        public val offer: DropOffer,
        public val position: LogicalPoint,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class DropMoved(
        public val offerId: DropOfferId,
        public val position: LogicalPoint,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class DropExited(
        public val offerId: DropOfferId,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class Dropped(
        public val offer: DropOffer,
        public val position: LogicalPoint,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class StateReset(
        public val reason: InputStateResetReason,
        public override val stamp: EventStamp,
        public override val deviceId: DeviceId?,
        public override val stateRevision: InputStateRevision,
    ) : InputEvent
}

public enum class InputStateResetReason {
    FocusLost,
    DeviceDisconnected,
    PermissionRevoked,
}
```

Le flux unique conserve l’ordre par `SessionSequence` entre événements clavier, pointeur, tactile et gestes. `SurfaceInput.state` regroupe clavier, pointers, contacts tactiles, modifiers et capabilities dans une seule cellule atomique. Chaque événement référence la révision effective publiée avant lui ; un collector qui lit ensuite `state.value` peut observer cette révision ou une révision plus récente. Des extensions filtrées fournissent les vues spécialisées et des lectures dérivées de `state`, sans créer de `StateFlow` indépendant ni réordonner ou réestampiller les événements.

Si la source input est fermée par policy ou erreur native, `events` se termine après que `SurfaceInput.state` a publié un snapshot composé neutre terminal dont les capabilities sont `Unavailable` avec la failure stable ; la surface elle-même reste utilisable.

Les coordonnées portables utilisent l’espace logique de la surface : origine en haut à gauche, axe X vers la droite et axe Y vers le bas. Un événement peut aussi exposer une position physique lorsqu’elle est connue, mais ne remplace jamais silencieusement une unité par l’autre. Les deltas raw conservent leur unité de périphérique et sont typés séparément.

Le noyau portable fermé d’un événement clavier distingue la touche physique, la touche logique, la location, l’état press/release, la répétition et les modifiers. Le texte composé provient uniquement de `TextInputSession`; Kadre ne synthétise pas de texte à partir des événements clavier lorsque l’IME est actif.

Une perte de focus, une déconnexion ou une révocation observée alors que la lane accepte encore les événements ne synthétise pas une série arbitraire de releases. Kadre publie atomiquement un `SurfaceInputState` neutre, puis un unique `StateReset` estampillé avec cette révision. Les press/release réellement fournis par le host avant ce reset conservent leur ordre.

Un overflow d’ingress constitue le cas terminal : la lane étant déjà saturée, Kadre ne prétend pas pouvoir y insérer un `StateReset`. Il publie atomiquement un `SurfaceInputState` neutre terminal avec capabilities indisponibles et `KadreFailure.SourceOverflow`, ferme `events` avec la même `KadreException` et incrémente le diagnostic avant toute tentative de détail. Ce signal terminal hors file remplace l’événement impossible à garantir. Cette règle empêche les touches, boutons et contacts « bloqués » sans faire passer des événements inventés pour des observations natives.

### 10.2 Gamepad

```kotlin
public interface Gamepad {
    public val id: GamepadId
    public val state: StateFlow<GamepadSnapshot>
    public val events: Flow<GamepadEvent>

    public suspend fun playEffect(effect: GamepadEffect): KadreResult<GamepadEffectSession>
    public suspend fun stopEffects(): KadreResult<Unit>
}

public data class GamepadSnapshot(
    public val descriptor: GamepadDescriptor,
    public val connection: DeviceConnectionState,
    public val routing: GamepadRoutingState,
    public val controls: GamepadState,
    public val capabilities: GamepadCapabilities,
    public val revision: GamepadRevision,
)

public enum class GamepadRoutingState {
    Routed,
    Suspended,
}

public interface GamepadEffectSession : AutoCloseable {
    public val state: StateFlow<GamepadEffectState>

    public override fun close()
    public fun requestStop()
    public suspend fun awaitTermination(): GamepadEffectOutcome
}
```

Un code natif inconnu reste inconnu. Aucun ordinal invalide n’est transformé en bouton ou axe arbitraire.

`Gamepad.state` est le snapshot atomique du descriptor, de la connexion, du routing, des contrôles et des capabilities ; il est mis à jour avant `Gamepad.events`, dont chaque variante référence la `GamepadRevision` effective. Les valeurs analogiques sont normalisées dans le domaine documenté ; la valeur native brute reste interne v1 et aucune vue `@KadrePlatformApi` additionnelle n’est promise. `Disconnected` impose canoniquement `routing = Suspended` et des contrôles neutres. Une déconnexion publie ce snapshot en une révision ; une reconnexion produit un nouveau `GamepadId`, déjà `Routed` si la session est alors éligible, sinon `Suspended`.

Le routing ne masque jamais le lifecycle physique. Une projection attachée reçoit connexion et déconnexion même lorsqu’elle n’est pas éligible aux changements de contrôles. Perdre l’éligibilité publie atomiquement `routing = Suspended` avec contrôles neutres puis un unique événement discret `RoutingSuspended`; les changements physiques intermédiaires ne sont pas mis en file. La reprise publie `routing = Routed` avec le snapshot physique courant puis `RoutingResumed` avant tout nouvel événement de contrôle. `ActiveSessionOnly` et le passage foreground/background de `AllForegroundSessions` suivent cette même règle, afin qu’aucune session ne conserve un bouton apparemment pressé pendant une suspension de routing.

`playEffect` signifie que l’effet a été accepté et retourne un owner observable ; il ne signifie pas que l’effet est achevé. Chaque owner admis compte dans `maxConcurrentGamepadEffects` jusqu’à son outcome terminal, y compris lorsqu’un backend supporte réellement plusieurs effets partagés. Atteindre cette limite retourne `ResourceLimitExceeded(GamepadEffect, limit)` avant l’admission. La déconnexion, le teardown et la perte de la lease exclusive terminent la session d’effet avec un outcome typé. Le broker process-wide applique `DeviceEffectOwnership`; un conflit retourne `AlreadyInUse` au lieu de mélanger silencieusement deux effets.

`stopEffects()` demande uniquement l’arrêt des `GamepadEffectSession` créées par cette projection dans cette `KadreSession`. Il ne stoppe jamais les effets possédés par une autre session, même en mode partagé. Aucun arrêt physique global public n’existe v1.

`GamepadEffectSession.awaitTermination()` est idempotent. La cancellation d’un waiter ne stoppe pas l’effet ; `requestStop()` ou `close()` exprime cet ownership explicitement.

### 10.3 IME

```kotlin
public interface TextInputSession : AutoCloseable {
    public val events: Flow<TextInputEvent>
    public val state: StateFlow<TextInputState>

    public override fun close()
    public suspend fun updateCursor(
        rect: LogicalRect,
        documentRevision: TextDocumentRevision,
    ): KadreResult<Unit>
    public suspend fun updateSurroundingText(
        text: String,
        selection: TextRange,
        documentRevision: TextDocumentRevision,
    ): KadreResult<Unit>
}
```

Une surface possède au maximum une session IME active. Une seconde ouverture retourne `KadreFailure.AlreadyInUse`; aucun remplacement silencieux. `TextRange` utilise des offsets UTF-16 dans la `String` Kotlin. Le cursor rect est exprimé dans l’espace logique de la surface et converti par le backend.

`TextDocumentRevision` est une révision monotone choisie par l’application et enregistrée avec chaque surrounding text. `TextInputState` contient la dernière révision acceptée. Tout `TextInputEvent` qui propose un edit, une sélection ou une composition porte la révision de base sur laquelle le host l’a calculé. L’application n’applique un edit que si cette révision correspond encore à son document ; sinon elle republie son snapshot courant. Pour `CompositionChanged`, `range` est toujours dans le document de `baseRevision`, tandis que `selection` est dans `text`, donc dans le texte composé proposé ; elle est non nulle exactement quand `range` l’est. Une sélection interne de préédition n’est jamais encodée artificiellement comme `SelectionChanged`, car elle peut dépasser le document de base. Lorsque l’application accepte une composition dans un nouveau snapshot, celui-ci doit être la substitution de `text` dans `range`; le runtime rebase alors `TextInputState.composingRange` sur ce snapshot et refuse autrement `InvalidRequest("text")`. `updateCursor` exige exactement la révision courante et retourne `KadreFailure.StaleRevision` pour toute autre, mais accepte un nouveau rect pour refléter un layout ou scroll sans mutation du document. `updateSurroundingText` accepte une révision supérieure ; une révision inférieure retourne aussi `StaleRevision`, la même révision avec un payload identique est idempotente et avec un texte ou une sélection différente retourne `InvalidRequest`. Une nouvelle révision n’annule pas implicitement une composition : sa poursuite ou sa terminaison reste annoncée par l’état et les événements IME.

Fermer la surface, sa fenêtre propriétaire ou la session ferme la session IME enfant. La perte temporaire de focus publie un état suspendu, sans détruire automatiquement la composition ; le backend peut terminer la composition uniquement lorsque le host natif l’impose et l’annonce par un événement terminal.

### 10.4 Drag-and-drop

```kotlin
public interface DropOffer {
    public val id: DropOfferId
    public val items: List<DropItemDescriptor>
    public val state: StateFlow<DropOfferState>

    public suspend fun claimTransfer(): KadreResult<DropTransfer>
}

public sealed interface DropOfferState {
    public data object Presented : DropOfferState
    public data object Accepted : DropOfferState
    public data object TransferAvailable : DropOfferState
    public data object Claimed : DropOfferState
    public data class Terminated(
        public val reason: DropOfferTerminationReason,
    ) : DropOfferState
}

public sealed interface DropOfferTerminationReason {
    public data object Rejected : DropOfferTerminationReason
    public data object LeftSurface : DropOfferTerminationReason
    public data object OfferExpired : DropOfferTerminationReason
    public data object ClaimTimedOut : DropOfferTerminationReason
    public data object OwnerClosed : DropOfferTerminationReason
    public data class Failed(public val failure: KadreFailure) : DropOfferTerminationReason
}

public interface DropTransfer : AutoCloseable {
    public val items: List<DroppedItem>

    public override fun close()
}

public interface DroppedItem {
    public val descriptor: DropItemDescriptor
    public val readMode: DropItemReadMode
    public suspend fun collectBytes(
        maxBytes: Long,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit>
}

public enum class DropItemReadMode {
    Replayable,
    SingleUse,
}
```

L’entrée d’une offre, ses mouvements, sa sortie et son drop apparaissent dans l’ordre du flux `SurfaceInput.events`. Une surface possède au plus une `DropOffer` active : l’admission d’une nouvelle offre rend d’abord l’ancienne terminale avec `LeftSurface`, ce qui borne le nombre d’offres actives par les surfaces de la session. Accepter une offre lorsque le host exige une réponse synchrone utilise `InteractionAction.AcceptDrop`. `InteractionRegistration.outcomes` ne transporte alors que l’`InteractionRequestId`, le `DropOfferId` et le résultat immuable de l’acceptation ; aucun `Flow` multicast ne transporte un `DropTransfer` ou un autre payload closeable.

`Presented` est l’état initial. `Accepted` signifie seulement que la réponse synchrone au host a accepté le drop ; il n’affirme pas encore que les données sont transférables. Après le drop natif, `DropOffer.state` publie `TransferAvailable` et `claimTransfer()` effectue l’unique handoff à l’application. Plusieurs waiters sont permis, mais exactement un reçoit `Success(DropTransfer)` et provoque `Claimed` ; les autres, ainsi que tout appel ultérieur après `Claimed`, reçoivent `AlreadyInUse(DropTransfer)`. Annuler un waiter ne rejette pas l’offre et ne consomme pas le handoff. Tant que l’offre est `Presented` ou `Accepted`, l’appel attend `TransferAvailable` ou un état terminal. `Terminated(Rejected|LeftSurface|OfferExpired|ClaimTimedOut|OwnerClosed)` produit `Failure(Closed(DropTransfer))`; `Terminated(Failed(failure))` produit cette failure exacte. Si aucun consumer ne claim le transfer dans les `dropTransferClaimTimeout` suivant l’admission de `TransferAvailable`, Kadre le ferme, libère le budget et publie `ClaimTimedOut`. La session reste owner de secours et ferme également tout transfer non claimé pendant son teardown.

`DropItemDescriptor` expose uniquement les métadonnées réellement portables : nom d’affichage optionnel, taille optionnelle, MIME types et nature text/file/URI. Aucun backend ne fabrique un chemin de fichier sur Web ou un path accessible lorsque le sandbox ne l’accorde pas. `DropItemReadMode` indique honnêtement si le backend peut rouvrir l’item ; un item `SingleUse` est consommé dès l’admission de sa première lecture, même si celle-ci est ensuite annulée ou échoue.

Si la collection de descriptors ou son payload sémantique dépasse les budgets avant que l’offre puisse être construite intégralement, Kadre n’admet ni `DropOffer` partielle ni faux item « inconnu ». La branche `SurfaceInput` concernée devient terminale avec `ResourceLimitExceeded`, selon la règle des entrées natives trop grandes ; les métadonnées purement décoratives optionnelles peuvent seules devenir absentes avec diagnostic.

Après le handoff, le consumer gagnant possède le `DropTransfer` et la session reste owner de secours. Le transfer continue de compter dans `maxConcurrentDropTransfers` jusqu’à son `close()` effectif, qu’il soit encore possédé par Kadre ou déjà handoff à l’application. Il accepte au maximum un `collectBytes` actif à la fois, tous items confondus ; une tentative concurrente retourne `AlreadyInUse(DropTransfer)`. Un item `Replayable` accepte plusieurs lectures séquentielles, tandis qu’une seconde lecture d’un item `SingleUse` retourne `Closed(DropItem)`. La cancellation du caller propage sa `CancellationException`, arrête uniquement la lecture courante et ne ferme pas le transfert. Une exception non-cancellation du collector est propagée sans encapsulation après arrêt de la production ; l’item `SingleUse` reste consommé, tandis qu’un item `Replayable` peut être relu. `DropTransfer.close()` interdit toute nouvelle lecture, arrête la production de nouveaux chunks et fait terminer la lecture active avec `Failure(Closed(DropTransfer))` dès que le callback collector courant rend la main ; il ne peut pas préempter du code consumer non coopératif.

`collectBytes` fournit des chunks détenus par l’application, chacun borné par `maxDropChunkBytes`; un `maxBytes` non strictement positif retourne `InvalidRequest` avant de consommer l’item. Sa valeur valide borne le total livré par cet appel. Si la taille connue dépasse la limite, l’appel échoue avec `ResourceLimitExceeded` avant le premier chunk. Si elle est inconnue, Kadre ne livre jamais d’octet au-delà de `maxBytes`, mais peut avoir livré un préfixe avant de retourner la même failure ; le consumer doit considérer ce préfixe invalide lorsque le résultat est un échec. Un succès signifie que l’item complet a été livré. Les handles de fichier et `Blob` natifs restent internes v1 ; `collectBytes` est l’unique lecture publique portable d’un `DroppedItem`.

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

Chaque manager concerné expose la permission dans un `StateFlow` spécialisé ou dans son snapshot composé révisionné lorsque d’autres champs doivent rester cohérents avec elle, comme `CaptureManagerState`. Les changements et révocations sont observables même après un grant. Deux requêtes simultanées pour la même permission partagent une unique requête native ; annuler un waiter n’annule ni le prompt OS ni les autres waiters. Une permission process-wide est coordonnée par le broker, puis projetée dans chaque session. Lorsqu’une requête exige une interaction transitoire, l’opération retourne `InteractionRequired` et utilise le mécanisme de la section 9.6.

Une requête de permission admise retourne `Success` avec le nouveau snapshot pour `Granted`, `Denied` ou `Restricted`. `KadreFailure.PermissionDenied` est réservé à une opération fonctionnelle tentée sans permission, pas à la réponse normale du prompt. `UserCancelled` est utilisé seulement lorsqu’un host distingue explicitement fermeture du picker et refus. Une panne empêchant d’émettre ou d’observer le prompt retourne une `Failure` et publie `Unavailable` avec la même reason lorsque cette indisponibilité persiste ; `Unavailable` n’est jamais emballé dans un faux `Success`.

## 11. Capture

```kotlin
public interface CaptureManager {
    public val state: StateFlow<CaptureManagerState>

    public suspend fun requestPermission(
        scope: CapturePermissionScope,
    ): KadreResult<CaptureManagerState>
    public suspend fun refreshSources(): KadreResult<CaptureManagerState>
    public suspend fun open(request: CaptureRequest): KadreResult<CaptureSession>
}

public data class CaptureManagerState(
    public val permissions: CapturePermissionState,
    public val capabilities: CaptureCapabilities,
    public val sources: CaptureSources,
    public val revision: CaptureManagerRevision,
)

public sealed interface CaptureSources {
    public data class Enumerated(public val values: List<CaptureSource>) : CaptureSources
    public data object HostPickerOnly : CaptureSources
    public data class PermissionRequired(
        public val required: Set<KadrePermission>,
    ) : CaptureSources
    public data class Unavailable(public val failure: KadreFailure) : CaptureSources
}
```

`HostPickerOnly` représente les plateformes qui interdisent une énumération préalable. Dans ce cas, `CaptureRequest` utilise une cible `HostChoice` et l’appel suspendu à `open` attend le choix ou l’annulation utilisateur. Une fermeture explicite du picker retourne `Failure(KadreFailure.UserCancelled(KadreOperation.CaptureOpen))`, distincte de la `CancellationException` du caller. `Unavailable` porte une failure stable lorsque l’énumération ne peut pas fournir un inventaire complet, notamment en cas de dépassement de budget. Contrairement à un flow d’inventaire fermé, `refreshSources()` peut réessayer cette opération suspendue et publier une révision `Enumerated` complète si la cause a disparu. Kadre ne fabrique jamais de faux inventaire vide et ne publie jamais une liste tronquée comme complète.

`refreshSources()` retourne `Success(state)` uniquement après publication d’un `Enumerated` complet ou de `HostPickerOnly`. Une permission manquante retourne `PermissionDenied` et conserve ou publie `PermissionRequired`; une autre failure persistante appartenant au domaine durable fermé de `OPERATION-CONTRACTS.md` publie `Unavailable` avec la même reason avant de la retourner. `InteractionRequired` et une `TemporarilyUnavailable` non persistante conservent le snapshot précédent.

`CaptureSources.PermissionRequired.required` est un set non vide contenant uniquement `CaptureScreen`, `CaptureWindow` ou les deux. Il décrit les permissions nécessaires pour obtenir un inventaire complet, pas nécessairement celles d’une target `Surface` ou d’un picker host-only.

`CaptureSource` est un descriptor immuable, pas un handle vivant. Il contient son `CaptureSourceId`, les métadonnées portables disponibles et la `CaptureManagerRevision` de l’inventaire qui l’a produit. Un refresh conserve l’ID d’une source restée continûment présente ; une source retirée invalide son ID et toute réapparition ultérieure en reçoit un nouveau. `open` avec un descriptor qui n’appartient plus à la révision courante retourne `StaleRevision` avant tout picker ou réservation. `CaptureSession.source` conserve une copie descriptive lisible après la disparition de la source, sans permettre de la rouvrir implicitement.

```kotlin
public interface CaptureSession : AutoCloseable {
    public val source: CaptureSource
    public val state: StateFlow<CaptureSessionState>
    public val events: Flow<CaptureEvent>
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
        public val configuration: CaptureConfiguration,
    ) : CaptureSessionState
    public data object Stopping : CaptureSessionState
    public data class Terminated(public val outcome: CaptureOutcome) : CaptureSessionState
}

public data class CaptureConfiguration(
    public val revision: CaptureConfigurationRevision,
    public val size: PhysicalSize,
    public val format: PixelFormat,
    public val colorEncoding: ColorEncoding,
    public val alphaMode: AlphaMode,
    public val orientation: CaptureOrientation,
    public val cadence: CaptureCadence,
    public val region: CaptureRegion?,
    public val cursorMode: CaptureCursorMode,
)

public sealed interface CaptureCadence {
    public data class Fixed(
        public val frameInterval: Duration,
    ) : CaptureCadence

    public data class Variable(
        public val minimumFrameInterval: Duration?,
        public val maximumFrameInterval: Duration?,
    ) : CaptureCadence

    public data object Unknown : CaptureCadence
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

`CaptureManager.state` est l’unique snapshot atomique des permissions screen/window, capabilities et inventaire. Une révocation publie donc une combinaison cohérente en une seule révision avant de fermer les captures concernées. Les éventuels helpers de lecture sont des propriétés dérivées de `state.value`, jamais des `StateFlow` indépendants. `requestPermission(Screen)` ne modifie pas implicitement la permission `Window`, et réciproquement ; un backend dont le prompt natif couvre les deux publie atomiquement les deux résultats réellement observés.

`open` valide la requête, effectue le picker éventuel et réserve la source, mais ne commence pas à produire des frames. La `CaptureSession` compte dans `maxConcurrentCaptureSessions` de son admission jusqu’à son `CaptureOutcome` terminal ; atteindre la limite échoue avant de réserver une source. Annuler le caller avant son retour détache uniquement ce waiter : Kadre tente d’annuler un picker exclusivement possédé et encore réversible, mais ne ferme jamais un prompt OS partagé, ne perturbe aucun autre waiter et ne crée pas de `CaptureSession` abandonnée lorsque le résultat natif arrive plus tard.

Une `CaptureSession` accepte exactement un appel réussi à `collectFrames` pendant sa durée de vie. Cet appel démarre la production native et un second appel, simultané ou ultérieur, reçoit `KadreFailure.AlreadyInUse`. `collectFrames` retourne `Success(Unit)` pour `SourceCompleted` ou un arrêt demandé par `requestStop`/`close`, `Failure(PermissionDenied)` pour `Stopped(PermissionRevoked)`, et `Failure` avec la même `KadreFailure` pour `CaptureOutcome.Failed`. Annuler le caller propage sa `CancellationException` et arrête toute la `CaptureSession` avec `CollectorCancelled`. Une exception non-cancellation du collector est propagée sans encapsulation après libération de la frame courante et produit `Stopped(CollectorFailed)` pour la capture ; si le caller ne la traite pas, les règles ordinaires du job applicatif la promeuvent séparément en `SessionOutcome.Failed(ApplicationFailure)`. Le teardown parent annule normalement le caller et publie `Stopped(ParentSessionStopping)` sans convertir cette cancellation en `KadreResult`.

`awaitTermination()` est idempotent et retourne toujours le même `CaptureOutcome`. La cancellation d’un waiter ne stoppe pas la capture. L’appeler depuis le collector actif de cette même `CaptureSession` échoue immédiatement avec `IllegalStateException` au lieu de deadlocker. Une révocation de permission ferme la production et produit `Stopped(PermissionRevoked)` ; une perte de source produit `Failed(SourceLost)`. Une failure de capture ne termine pas la `KadreSession`, sauf lorsqu’une policy explicitement choisie demande `FailSession`.

`close()` délègue à `requestStop()` ; les deux sont non bloquants, thread-safe et idempotents. Après un outcome terminal, `awaitTermination()` retourne ce terminal et les autres opérations retournent `KadreFailure.Closed` sans réouvrir la source.

### 11.1 Frames

```kotlin
public interface CaptureFrame : AutoCloseable {
    public val size: PhysicalSize
    public val format: PixelFormat
    public val planes: List<PixelPlaneLayout>
    public val configurationRevision: CaptureConfigurationRevision
    public val stamp: EventStamp
    public val sourceTimestamp: CaptureSourceInstant?
    public val duration: Duration?
    public val discontinuity: CaptureDiscontinuity?
    public val colorEncoding: ColorEncoding
    public val alphaMode: AlphaMode
    public val orientation: CaptureOrientation

    public override fun close()
    public fun copyPlanes(): List<CopiedPixelPlane>
}

public data class PixelPlaneLayout(
    public val width: Int,
    public val height: Int,
    public val rowStride: Int,
    public val pixelStride: Int,
    public val byteCount: Int,
    public val horizontalSubsampling: Int,
    public val verticalSubsampling: Int,
)

public class CopiedPixelPlane internal constructor(
    public val layout: PixelPlaneLayout,
    public val bytes: ByteArray,
)
```

Une frame est une lease valide uniquement pendant l’appel du collector. Kadre la ferme dans un `finally`, que le collector retourne, échoue ou soit annulé. `close()` reste idempotent pour permettre une libération anticipée. `PixelPlaneLayout` ne contient aucune vue mémoire et reste un value object valide après fermeture. `copyPlanes()` produit une entrée par layout, dans le même ordre, dont le `ByteArray` est une copie détenue par l’application ; après fermeture, seul cet appel échoue avec `IllegalStateException`. Kadre ferme aussi les frames remplacées ou écartées par la delivery policy. Aucun owner zero-copy retenable public n’existe v1 ; l’ajouter exigera une nouvelle déclaration au catalogue et un contrat de budget/lifetime propre.

Le format, les dimensions et subsampling de chaque plane, les strides, le color encoding, l’alpha et l’orientation font partie du contrat de chaque frame. `CaptureFrame.size` décrit la grille luma/full-resolution avant orientation. Dans un `PixelPlaneLayout`, `width` et `height` sont le nombre de samples logiques de la plane ; `pixelStride` est la distance en octets entre deux samples d’une même ligne, `rowStride` celle entre deux débuts de ligne, et les deux subsamplings sont relatifs à la grille full-resolution. Les sept entiers sont strictement positifs. En arithmétique `Long` vérifiée, `rowStride >= (width - 1) × pixelStride + 1` et `byteCount >= (height - 1) × rowStride + (width - 1) × pixelStride + 1`; un résultat hors `Int` interdit la frame.

Les layouts des formats portables sont exacts :

| Format | Planes dans l’ordre | Layout obligatoire |
|---|---|---|
| `Rgba8`, `Bgra8`, `Bgrx8` | une plane packed | `width = frame.width`, `height = frame.height`, `pixelStride = 4`, subsamplings `1/1`, `rowStride >= width × 4`, `byteCount >= (height - 1) × rowStride + width × 4` |
| `Nv12` | Y, puis UV interleaved | Y : grille full-resolution, `pixelStride = 1`, subsamplings `1/1`; UV : `width = ceil(frame.width / 2)`, `height = ceil(frame.height / 2)`, `pixelStride = 2`, subsamplings `2/2` |
| `I420` | Y, U, V | Y : grille full-resolution, `pixelStride = 1`, subsamplings `1/1`; U et V : `width = ceil(frame.width / 2)`, `height = ceil(frame.height / 2)`, `pixelStride = 1`, subsamplings `2/2` |
| `Opaque(code, planeCount)` | exactement `planeCount` planes | seulement les invariants génériques ; le consumer branche sur `code` avant toute interprétation supplémentaire |

Pour les planes Y/UV/U/V, `rowStride >= width × pixelStride` et `byteCount >= (height - 1) × rowStride + width × pixelStride`. Les divisions arrondies utilisent une arithmétique vérifiée. Le noyau portable fermé de `ColorEncoding` contient les primaries, la transfer function, la matrix, le range et les métadonnées HDR connues, chaque information inconnue possédant une variante explicite. Chaque `CopiedPixelPlane.bytes` commence à l’octet logique zéro de sa plane et contient exactement `layout.byteCount` octets, padding inclus ; aucun offset vers un backing buffer natif ne fuite dans l’API commune. Aucune conversion implicite de format, packing ou espace colorimétrique n’est effectuée.

`stamp.timestamp` mesure l’arrivée de la frame dans la session. `sourceTimestamp`, lorsqu’il existe, utilise l’horloge média monotone de cette `CaptureSession` et sert au pacing ou à l’encodage ; il n’est comparable ni à `SessionInstant` ni au timestamp d’une autre capture. `duration`, lorsqu’elle existe, est finie et strictement positive et décrit la durée de présentation connue. Une pause, un saut d’horloge, une frame répétée ou une autre rupture détectable renseigne `discontinuity` au lieu de falsifier une continuité.

Le format, la taille, la cadence, la région, le mode cursor, l’orientation et l’encodage peuvent changer pendant une session. `CaptureCadence.Fixed` utilise un intervalle fini strictement positif. `Variable` possède au moins une borne finie strictement positive et, lorsque les deux existent, `minimumFrameInterval <= maximumFrameInterval`; `Unknown` n’invente aucune cadence. `CaptureSessionState.Streaming` contient toujours la `CaptureConfiguration` effective complète, y compris sa révision.

Pour une reconfiguration, Kadre publie d’abord ce snapshot, admet ensuite `CaptureEvent.Reconfigured(configuration)` sous `capture.events`, puis seulement la première frame portant la nouvelle révision. Comme événements et frames sont deux streams distincts, cette règle porte sur l’admission et ne garantit pas que le collector d’événements reçoive `Reconfigured` avant le callback frame. La frame et son `configurationRevision` restent donc autosuffisantes ; le consumer ne doit jamais attendre l’autre flow pour l’interpréter. Chaque frame conserve ses propriétés effectives et sa révision, lesquelles correspondent exactement à la configuration référencée, afin qu’une frame retardée ne soit jamais interprétée avec le snapshot courant plus récent.

`CaptureDeliveryPolicy.maxBufferedBytesPerSession` borne la somme des buffers ingress, des frames en attente, de la lease livrée au collector et du pool natif réservé par Kadre pour une session de capture. Le backend calcule le coût à partir de la configuration négociée avant de démarrer. Une requête qui ne tient pas dans le budget échoue sans démarrer avec `ResourceLimitExceeded`. Si une reconfiguration dépasserait le budget, elle n’est pas publiée comme réussie : la capture se termine avec la même failure. Un buffer opaque dont le backend ne peut pas borner le coût interdit les modes buffered concernés ; aucun nombre de frames ne remplace silencieusement la limite en octets. Avec `maxConcurrentCaptureSessions`, cette borne rend aussi finie la réservation totale possédée par une session Kadre. Les copies produites par `copyPlanes()` sont ensuite sous le budget de l’application et ne restent pas comptées par Kadre.

## 12. Résultats et erreurs

```kotlin
public sealed interface KadreResult<out T> {
    public data class Success<T>(public val value: T) : KadreResult<T>
    public data class Failure(public val reason: KadreFailure) : KadreResult<Nothing>
}

public val KadreResult<*>.isSuccess: Boolean
public val KadreResult<*>.isFailure: Boolean
public fun <T> KadreResult<T>.getOrNull(): T?
public fun KadreResult<*>.failureOrNull(): KadreFailure?
public fun <T, R> KadreResult<T>.map(transform: (T) -> R): KadreResult<R>
public fun <T, R> KadreResult<T>.flatMap(transform: (T) -> KadreResult<R>): KadreResult<R>
public fun <T, R> KadreResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (KadreFailure) -> R,
): R
public fun <T> KadreResult<T>.getOrThrow(): T

public class KadreException(
    public val failure: KadreFailure,
) : RuntimeException(failure.message)
```

```kotlin
public sealed interface KadreFailure {
    public data class Unsupported(
        public val operation: KadreOperation,
    ) : KadreFailure

    public data class PermissionDenied(
        public val permission: KadrePermission,
    ) : KadreFailure

    public data class UserCancelled(
        public val operation: KadreOperation,
    ) : KadreFailure

    public data class TemporarilyUnavailable(
        public val retryable: Boolean,
    ) : KadreFailure

    public data class InvalidRequest(
        public val field: String?,
    ) : KadreFailure

    public data class AlreadyInUse(
        public val resource: KadreResourceKind,
    ) : KadreFailure

    public data class Closed(
        public val resource: KadreResourceKind,
    ) : KadreFailure

    public data class ResourceLimitExceeded(
        public val resource: KadreResourceKind,
        public val limit: Long,
    ) : KadreFailure

    public data class SourceOverflow(
        public val resource: KadreResourceKind,
    ) : KadreFailure

    public data class StaleRevision(
        public val expected: Long,
        public val received: Long,
    ) : KadreFailure

    public data class InteractionRequired(
        public val reason: InteractionFailureReason,
    ) : KadreFailure

    public data class UnsupportedPolicy(
        public val component: KadrePolicyComponent,
    ) : KadreFailure

    public data object ParentScopeCancelled : KadreFailure

    public data class ShutdownTimedOut(
        public val timeout: Duration,
    ) : KadreFailure

    public data class SourceLost(
        public val source: CaptureSourceId,
    ) : KadreFailure

    public data object ApplicationFailure : KadreFailure

    public data class PlatformFailure(
        public val platform: KadrePlatform,
        public val domain: String,
        public val code: String,
    ) : KadreFailure
}

public val KadreFailure.message: String
    get() = kadreFailureMessage(this)

public enum class KadreOperation {
    HostAttach,
    RequestRedraw,
    DisplayAccess,
    RequestWindow,
    UpdateWindow,
    RequestWindowAttention,
    CloseWindow,
    RespondToCloseRequest,
    UpdateSurface,
    InstallInteractionHandler,
    ArmInteraction,
    Interaction,
    GamepadEffect,
    StopGamepadEffects,
    TextInput,
    UpdateTextInput,
    ClaimDropTransfer,
    ReadDropItem,
    CapturePermission,
    CaptureRefreshSources,
    CaptureOpen,
    CaptureCollectFrames,
    RawInputAccess,
    PlatformSurfaceAccess,
    PlatformWindowAccess,
}

public enum class KadrePolicyComponent {
    Execution,
    LifecycleEvents,
    HostSignals,
    WindowEvents,
    DeviceEvents,
    InputEvents,
    DevicePolicy,
    CaptureEvents,
    CaptureFrames,
    Diagnostics,
    Resources,
}

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
    InputSource,
    InputDevice,
    Gamepad,
    EventCollector,
    Interaction,
    DropTransfer,
    DropItem,
    CursorImage,
    GamepadEffect,
    TextInputSession,
    CaptureSource,
    CaptureSession,
    CaptureCollector,
    CaptureBuffer,
    RetainedPayload,
    ImageResource,
    EventSequence,
}
```

Les `CancellationException` ne sont jamais encapsulées. Les erreurs de programmation utilisent les exceptions standard Kotlin. Les limites attendues utilisent `KadreResult`.

Le set de failures admissibles n’est pas implicite : `OPERATION-CONTRACTS.md` possède une ligne normative pour chaque opération, y compris celles qui retournent un outcome sans `KadreResult`. Une nouvelle opération publique modifie obligatoirement cette matrice dans le même changement.

`KadreResult` fournit `isSuccess`, `isFailure`, `getOrNull`, `failureOrNull`, `map`, `flatMap`, `fold` et `getOrThrow`. `getOrThrow` lève `KadreException`; `map` et `flatMap` ne capturent jamais une exception du transformateur.

`message` est une propriété dérivée locale-neutre destinée au diagnostic humain et ne sert jamais au branching. Elle n’entre dans aucun constructeur, `equals`, `hashCode`, snapshot ou export stable. Les sous-types, opérations, permissions, resources, domains et codes constituent l’information structurelle. `InvalidRequest.field`, `PlatformFailure.domain` et `PlatformFailure.code` sont des identifiants ASCII d’au plus 256 unités, définis par l’adapter ou le catalogue public ; cette borne indépendante de la policy reste applicable même si `attach` rejette précisément une policy invalide. Ils ne recopient jamais une entrée utilisateur ni un message natif. Le `Throwable` original et le détail textuel d’une erreur applicative ou plateforme sont conservés pour le reporter interne du host, mais ne font pas partie du value model public, de son égalité ou de son export Swift/JS. Les adapters peuvent localiser une présentation à partir des champs stables sans modifier la failure.

## 13. Diagnostics

```kotlin
public sealed interface KadreDiagnostic {
    public val stamp: EventStamp
    public val severity: DiagnosticSeverity
    public val subsystem: KadreSubsystem
}

public val KadreDiagnostic.message: String
    get() = kadreDiagnosticMessage(this)

public data class DiagnosticCounters(
    public val eventLosses: Long,
    public val slowCollectors: Long,
    public val collectorRejections: Long,
    public val resourceLimitHits: Long,
    public val interactionExpirations: Long,
    public val permissionRevocations: Long,
    public val backendFallbacks: Long,
    public val platformFailures: Long,
    public val saturated: Set<DiagnosticCounter>,
)

public enum class DiagnosticCounter {
    EventLosses,
    SlowCollectors,
    CollectorRejections,
    ResourceLimitHits,
    InteractionExpirations,
    PermissionRevocations,
    BackendFallbacks,
    PlatformFailures,
}
```

Le catalogue commun fermé de `KadreDiagnostic` matérialise :

- `EventLoss` ;
- `SlowConsumer` ;
- `CollectorRejected` ;
- `ResourceLimitHit` ;
- `InteractionExpired` ;
- `PermissionRevoked` ;
- `CapabilityChanged` ;
- `BackendFallback` ;
- `PlatformFailureObserved` ;
- `SessionFailure` ;
- `CleanupFailure`.

Chaque compteur démarre à zéro, est monotone et s’incrémente avec une arithmétique vérifiée. À `Long.MAX_VALUE`, il sature au lieu de wrap et son entrée apparaît atomiquement dans `saturated`; il n’est plus présenté comme exact au-delà de cette frontière. Le set est une copie immuable et sa cardinalité est bornée par l’enum fermé.

`eventLosses` compte des livraisons perdues par overflow ou fermeture, pas une réduction normale choisie par `Latest` ou `Coalesced`, laquelle reste observable par `EventDeliverySpan`. Un drop à l’ingress compte chaque événement source représenté une fois, tandis qu’un drop dans les files de trois collectors compte jusqu’à trois pertes distinctes. Lorsqu’une entrée supprimée possède déjà un span, son `eventCount` est ajouté plutôt que l’enveloppe unique. Ce compteur ne doit donc pas être interprété comme le nombre d’identités natives uniques perdues tous collectors confondus.

Aucun module de bibliothèque n’utilise `println` comme mécanisme de diagnostic public.

`KadreDiagnostic.message`, comme `KadreFailure.message`, est une présentation locale-neutre dérivée des champs stables et ne participe jamais à l’égalité. Par défaut, les messages et reporters internes remplacent les titres de fenêtre, noms de périphérique, noms de source de capture, identifiants du host et chemins par des catégories stables. Les failures publiques peuvent exposer un message actionnable mais ne recopient jamais du texte saisi ou du contenu capturé. Un adapter peut brancher son reporter interne privé sur l’infrastructure du host ; aucun setter ou sink diagnostic public additionnel n’est promis v1, et cette intégration n’altère ni `KadreDiagnostics.events` ni l’égalité du value model public.

Tous les value objects de `StateFlow`, événements, specs, outcomes et capabilities sont profondément immuables après publication. Kadre effectue une copie défensive des collections et buffers mutables reçus à la frontière publique. Les listes retournées ne sont jamais des vues d’un registre mutable interne. Les seules `ByteArray` mutables quittant l’API commune sont des copies explicitement possédées par l’application, comme celles de `copyPlanes()`.

Leur égalité structurelle est déterministe sur toutes les cibles et ne dépend jamais d’un message humain, d’un handle natif ou d’un état mutable. Les snapshots révisionnés incrémentent leur révision à chaque mutation sémantique admise, de sorte que la conflation de `StateFlow` ne masque pas un changement effectif. Les chaînes applicatives sémantiques, notamment IME et titres, sont conservées en UTF-16 sans normalisation Unicode implicite ; les métadonnées décoratives absentes à cause d’un budget restent distinguées d’une chaîne vide.

Sauf contrat contraire explicite, toute `Duration` publique doit être finie. Les timeouts et expirations sont strictement positifs ; timestamps, âges et durées de frame sont positifs ou nuls. `Duration.INFINITE` est rejetée comme `InvalidRequest` dans une entrée applicative et n’est jamais fabriquée pour masquer une valeur native inconnue. Les inconnues utilisent une variante ou `null` documenté.

Les valeurs `Float`/`Double` de géométrie, scale, pression et contrôles sont finies ; `NaN` et les infinis sont refusés ou représentés comme inconnus avant publication. Kadre canonicalise `-0.0` en `0.0`, exige des tailles positives ou nulles selon leur type, un scale strictement positif et les domaines normalisés annoncés par chaque contrôle. Une agrégation de deltas relatifs ou de scroll qui sortirait du domaine fini ferme la source avec `SourceOverflow` au lieu de clamp, wrap ou publier `NaN`. Ces règles stabilisent `equals`, `hashCode`, la conflation et les exports JS/Swift.

Les handles de ressource (`HostSurface`, `Window`, `Display`, `InputDevice`, `Gamepad`, `CaptureSession` et owners analogues) sont les exceptions explicites : leur identité et leur ID restent stables tandis que leurs `StateFlow` évoluent. Une liste de handles est un snapshot immuable d’appartenance, pas une copie profonde de leurs états futurs. Les implémentations utilisent une égalité d’identité stable et ne calculent jamais `equals` ou `hashCode` depuis un état mutable ; l’ID limité à la session reste la clé portable recommandée.

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

Par défaut, annuler une coroutine suspendue dans `await`, `awaitTermination`, une requête de permission ou un picker annule uniquement ce waiter. L’opération observable, le prompt natif partagé et les autres waiters continuent ; le backend peut annuler best-effort une opération exclusivement possédée et encore réversible, mais ne transforme jamais cette tentative en effet implicite sur un owner déjà retourné. Modifier un owner exige son verbe explicite `cancel`, `requestStop` ou `close`. Les seules exceptions sont les opérations qui déclarent posséder leur source pendant tout l’appel, notamment `collectFrames`, et les règles de cancellation y sont documentées localement.

Toute mutation suspendue possède un point d’admission puis, si le host l’exige, un point de commit irréversible. Une cancellation observée avant admission garantit l’absence d’effet. Entre admission et commit, Kadre retire l’opération lorsqu’elle est encore réversible ; après commit, la cancellation détache seulement le waiter et ne provoque aucun rollback implicite. L’état, l’événement ou l’operation ID documenté reste alors l’autorité du résultat tardif. Une méthode peut offrir une garantie locale plus forte, mais jamais contredire cette borne. Cela couvre notamment `Surface.apply`, `Window.apply/close`, `respondToCloseRequest`, `requestAccess`, `refreshSources`, les updates IME et `stopEffects`.

Toute fonction suspendue qui crée et retourne un owner `AutoCloseable` possède un point de handoff unique immédiatement avant son retour normal. Avant ce point, Kadre possède la ressource : une cancellation empêche son admission ou la ferme automatiquement, avec les effets irréversibles décrits par le contrat local, afin qu’aucun owner ni budget ne soit abandonné. Après ce point, l’application possède la ressource et la cancellation ultérieure de la coroutine appelante ne la ferme pas. Un `KadreResult.Failure` ne transfère jamais d’owner.

Tous les managers, handles vivants, getters et méthodes publiques non suspendues sont thread-safe sauf confinement explicitement déclaré. Les lectures de `StateFlow.value` suivent le contrat kotlinx.coroutines ; les mutations concurrentes sont sérialisées par l’owner et aucun callback consumer n’est invoqué sous un lock interne. Les builders, `InteractionContext`, tokens transitoires et accès au contenu des leases limitées à un collector sont les exceptions : ils sont non thread-safe et confinés au bloc documenté ; leur `close()` conserve néanmoins le contrat thread-safe commun. `installInteractionHandler` peut être appelé depuis tout thread, mais le handler installé s’exécute uniquement sur le thread du host, sérialisé et non réentrant comme défini en section 9.6.

Sauf contrat plus restrictif explicitement indiqué, tout `AutoCloseable` public Kadre possède la même sémantique : `close()` est non bloquant, thread-safe et idempotent ; il ferme immédiatement l’admission de nouvelles opérations, marshal le cleanup natif si nécessaire et ne lance pas d’exception pour une failure attendue. La fermeture logique est donc observable immédiatement par les opérations ultérieures. Lorsqu’un consumer a besoin de distinguer cette frontière du cleanup natif asynchrone, le type expose explicitement un state terminal ou `awaitTermination()` ; leur absence, notamment sur `DropTransfer` ou une valeur auxiliaire closeable, signifie qu’aucun join public du cleanup n’est promis. `CaptureFrame.close()` suit aussi ces garanties, mais n’étend jamais la validité de la lease au-delà du callback collector. Après fermeture ou terminaison logique de la session, les snapshots terminaux et IDs restent lisibles ; toute autre opération retourne `KadreFailure.Closed`, sauf opération explicitement documentée comme erreur de programmation après invalidation, telle que `CaptureFrame.copyPlanes()`.

## 15. Adaptateurs de plateforme

### 15.1 Android

```kotlin
public fun ComponentActivity.attachKadre(
    surfaceView: View,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleScope,
): KadreResult<KadreSession>

public fun ComponentActivity.attachKadre(
    surfaceView: View,
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

- Une même `Activity` ou `View` ne peut avoir qu’une session active et une même `View` ne peut appartenir qu’à une session ; une seconde tentative retourne `AlreadyInUse`.
- La session est liée au `LifecycleOwner` et se termine sur sa destruction définitive.
- Les deux formes exigent une `View` déjà attachée. Pour `ComponentActivity.attachKadre`, `surfaceView.rootView === window.decorView`; aucune vue n’est choisie ou créée implicitement. Le premier `onDetachedFromWindow` de la vue fournie termine la session, y compris pour l’overload Activity. Un reparenting ultérieur exige un nouvel attach.
- Le `parentScope` contrôle le dispatcher applicatif et son annulation externe ; le lifecycle du host reste terminal même si un scope fourni survit à l’`Activity` ou à la `View`.
- `ComponentActivity` expose sa fenêtre top-level et la `surfaceView` explicitement fournie ; `View.attachKadre` expose uniquement cette `HostSurface` et laisse `WindowManager.state.value.primary` à `null`.
- Un changement de configuration recrée une session ; Kadre ne conserve pas implicitement les jobs ou fenêtres à travers deux hosts natifs.
- L’état applicatif durable appartient à l’application ou à son architecture de state restoration, pas au backend Kadre.
- Aucun `AndroidKadreRuntime.currentHandler`.
- Une intégration Compose attache la session sans fournir de widget ni rendu.

### 15.2 UIKit et SwiftUI

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

- Une session par `UIWindowScene`.
- L’adaptateur crée son parent sur le dispatcher main de la scène ; le code partagé déplace explicitement les calculs lourds avec `withContext`.
- Le host choisit explicitement la `UIWindow` primaire et la `UIView` de contenu ; aucune sélection implicite dans `UIWindowScene.windows`. La fenêtre devient top-level et la vue devient `HostSurface`; SwiftUI reste propriétaire de son layout.
- `UISceneDelegate` alimente lifecycle et fermeture.
- `sceneDidDisconnect` annule la hiérarchie.
- Bridge Swift minimal `KadreIos.attach(windowScene, window, surfaceView, applicationFactory, policy)`.
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

public fun HTMLElement.attachKadre(
    parentScope: CoroutineScope,
    policy: KadrePolicy = KadrePolicies.Default,
    attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
    application: KadreApplication,
): KadreResult<KadreSession>
```

- Attachement à un `HTMLElement` ou `HTMLCanvasElement` existant.
- L’élément attaché devient `primarySurface`; il n’est jamais exposé comme `Window` et `WindowManager.state.value.primary` reste `null`.
- `StopWhenDetached` exige un élément initialement connecté ; sinon `attachKadre` retourne `InvalidRequest`. `Manual` accepte un élément déconnecté avec lifecycle `Attached + Background + Inactive`.
- `StopWhenDetached` vérifie `isConnected` à la livraison du batch `MutationObserver` : un reparenting terminé avant cette livraison ne ferme pas la session ; un élément encore détaché la termine et sa réinsertion exige une nouvelle session.
- `Manual` ignore le détachement DOM et exige un `requestStop` explicite.
- Aucun détournement du titre comme ID DOM.
- Sessions multiples possibles sur une même page.
- Kadre ne crée jamais de `<canvas>`, `<div>` ou popup et ne choisit aucun emplacement dans le DOM. Sans `WebWindowProvider`, `requestWindow` retourne `Unsupported`. Un provider peut demander un nouveau browsing context ; celui-ci devient un nouveau host et produit `OpenedInNewSession`, jamais `OpenedHere`.
- Les opérations exigeant la transient user activation passent par `InteractionContext`. Les requêtes suspendues ordinaires retournent `InteractionRequired` lorsqu’elles arrivent trop tard.
- Même contrat public en JS et Wasm.
- L’overload direct place `application` en dernier paramètre et n’accepte aucun `windowProvider` : il est mono-session et autorise `element.attachKadre(scope) { … }`. Une intégration qui peut ouvrir un autre browsing context utilise obligatoirement l’overload factory.
- Aucun faux `runApp` bloquant.
- `pagehide` et la destruction du browsing context ferment immédiatement l’admission et libèrent best-effort les bridges synchrones, mais le navigateur ne garantit pas l’achèvement d’un teardown suspendu. `awaitTermination` n’est garanti que tant que le runtime JS reste vivant ; aucune persistence ou requête réseau n’est déclenchée implicitement pendant unload.

### 15.4 Desktop

```kotlin
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

- `attachKadreDesktop` est l’API primaire embarquable et non bloquante. Ses overloads `application` placent la fun-interface en dernier paramètre pour autoriser `scope.attachKadreDesktop(options) { … }`, enveloppent l’instance dans une factory mono-session privée à cet appel et ne la réutilisent jamais pour une seconde session implicite.
- `runKadreApplication` est une commodité Desktop uniquement, bloquante et appelée depuis le main thread du processus ; elle n’établit aucun contrat commun avec Android, UIKit ou Web. Si l’attach standalone échoue avant création d’une session, elle lève `KadreException` avec la failure exacte de `OPERATION-CONTRACTS.md`; après création, elle retourne exclusivement le `SessionOutcome` terminal de cette session.
- `Embedded` conserve la session après fermeture de la dernière fenêtre ; `Standalone(stopWhenLastWindowClosed = true)` s’arme après sa première fenêtre admise, puis la première transition non-vide → vide demande `HostRequested`. Une session standalone qui reste toujours headless ne s’arrête pas pour cette seule raison.
- Une fenêtre desktop expose séparément son objet top-level et sa `HostSurface` de contenu.
- AppKit refuse explicitement un lancement hors du main thread au lieu de déplacer silencieusement la possession de `NSApplication`.
- Sélection typée AppKit, Win32, X11 ou Wayland.
- `DesktopHostOptions.Embedded` identifie explicitement l’intégration de boucle existante (`AppKit`, `AWT/Compose` ou `JavaFX`). Aucun custom pump public n’entre dans v1. `attachKadreDesktop` refuse une combinaison qu’il ne sait pas pomper ; il ne démarre jamais une seconde boucle UI cachée.
- Un échec après démarrage ne déclenche pas un fallback silencieux.

## 16. Interop plateforme

```kotlin
@MustBeDocumented
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalKadreApi

@MustBeDocumented
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class KadrePlatformApi

@MustBeDocumented
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class DelicateKadreApi
```

`ExperimentalKadreApi` permet une adoption progressive du Host SPI pendant l’incubation. `KadrePlatformApi` signale une dépendance target-specific et `DelicateKadreApi` une obligation de lifetime/threading que le compilateur ne peut pas prouver ; cumuler les deux exige les deux opt-ins. Aucune annotation n’est conservée au runtime ni ciblable sur un type-use/paramètre local non listé.

Les handles :

- ont un constructeur interne ;
- n’exposent aucun `Any` dans l’API commune ;
- sont valides uniquement tant que leur propriétaire est vivant ;
- utilisent des types spécifiques dans les source sets de plateforme ;
- ne laissent jamais fuiter les types bruts fournis par KFFI.

L’API Kotlin partagée est la surface normative. Pendant l’incubation initiale, une application est écrite en Kotlin partagé et les hosts Swift ne font qu’attacher une `KadreApplicationFactory` fournie par le module KMP. L’implémentation directe de `KadreApplication` en Swift n’est pas un contrat de cette première surface.

Les adapters étrangers exposent uniquement la façade host fermée par `INTEROP-EXPORTS.md` : session, state, arrêt et outcome. Ils traduisent `KadreResult` vers le modèle typé du langage au lieu de faire de `CoroutineScope`, `Flow`, `Continuation` ou `Throwable` une convention d’intégration. Le lifecycle reste possédé et déjà observable par l’Activity, la scène ou la page ; Kadre ne publie pas un second wrapper étranger divergent. Les exports Java, Swift, JS et Wasm possèdent chacun un consumer compile test. Les headers/frameworks générés et les déclarations TypeScript exportées sont contrôlés comme des artefacts d’API en plus des dumps ABI Kotlin.

## 17. Testabilité

L’artifact `test` fournit :

- `FakeKadreHost` ;
- `VirtualKadreClock` ;
- les six contrôleurs `VirtualSurfaceController`, `VirtualWindowController`, `VirtualDisplayController`, `VirtualInputController`, `VirtualGamepadController` et `VirtualCaptureController` ;
- les seed values défensives fermées par le catalogue.

Les scénarios d’overflow et la suite de contrats réutilisable par les backends restent des tests internes, pas une seconde API publique. Un consumer crée un `FakeKadreHost`, appelle son `attach` ordinaire puis pilote les contrôleurs ; il n’existe aucun runner alternatif qui contourne les vrais contrats session/policy.

Chaque backend valide les mêmes invariants de lifecycle, threading, capabilities, fermeture, flux, handles, permissions, surface/window et routing process-wide.

Les contract tests couvrent explicitement la température, le replay, la cardinalité, la terminaison tardive des flux, le mapping exhaustif `Flow`/policy, les budgets agrégés, l’ordre intra-flow, l’absence de garantie d’ordre inter-flows, les barrières discrètes des schedulers mixtes, l’ordre state/event, la cohérence des snapshots composés, l’isolation des collectors d’événements lents, l’absence de rejet des collectors `StateFlow`, les delivery spans, l’expiration des interactions, les resets et terminaisons d’input, les révisions IME, la cancellation non propriétaire des waiters, le protocole close, la fermeture automatique des frames, les configurations capture révisionnées, les budgets capture en octets, la cardinalité des drop transfers, le retrait terminal des displays et les transitions légales du lifecycle. Un test avec coroutine applicative non coopérative mais scheduler encore disponible valide qu’à la première opportunité suivant `shutdownTimeout`, `awaitTermination()` retourne, l’accès aux ressources est révoqué et seul le job consumer annulé peut rester physiquement incomplet. Un test distinct monopolise volontairement l’unique dispatcher virtuel et vérifie que Kadre ne promet pas d’observer l’échéance avant que ce dispatcher soit de nouveau schedulable. Des consumer tests compilent une intégration minimale Java, Swift, JS et Wasm.

## 18. Performance

Invariants mesurables :

- aucun buffer non borné ;
- aucun fan-out d’événements non borné : ses allocations maximales dérivent des capacités et limites de collectors publiées par la policy ;
- un `StateFlow` ne possède qu’une cellule productrice conflated et aucune file par collector ; le coût O(collectors) des subscriptions et coroutines demandées par l’application est mesuré mais n’est pas transformé en rejet Kadre ;
- aucune perte silencieuse de transition discrète ;
- lorsque le runtime a permis le teardown logique, aucune coroutine interne possédée par Kadre ne reste active ; une coroutine consumer non coopérative peut rester physiquement incomplète uniquement dans son job annulé toujours rattaché au scope du host, sans ressource Kadre vivante ;
- tant que le runtime exécute le cleanup, frames abandonnées toujours fermées et mémoire de capture sous `maxBufferedBytesPerSession` ;
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
10. les avertissements d’opt-in expérimentaux sont traités localement et intentionnellement ;
11. chaque catalogue public documentaire approuvé correspond exactement aux dumps ABI et exports de plateforme ;
12. aucun symbole classé par une règle résiduelle ne reste sans revue nominative ;
13. chaque `Flow` public possède exactement une ligne dans le mapping normatif de delivery, y compris les sous-lanes d’un flow mixte ;
14. les timeouts bornent le teardown Kadre dès que le runtime reste schedulable, sans prétendre préempter le code consumer, et les attentes suspendues n’acquièrent aucun ownership implicite.
