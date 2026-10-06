# Kadre — Roadmap d’implémentation iOS, iPadOS et tvOS

**Statut :** proposition de roadmap ; aucune phase iOS ou tvOS déclarée livrée par ce document. Les designs et plans d’exécution détaillés restent séparés par phase.

**Date :** 5 octobre 2026.

**Base inspectée :** `ac261e00`, après la phase 5 Web.

**Cibles proposées :** Kotlin/Native `iosArm64` et `iosSimulatorArm64` pour iOS/iPadOS, `tvosArm64` et `tvosSimulatorArm64` pour Apple TV et son simulateur Apple Silicon ; hosts UIKit et SwiftUI sur les deux familles d’OS.

**Mode de livraison :** tranches verticales, chacune avec son critère de sortie et ses preuves sur la plateforme.

L’objectif est de permettre à une application Kotlin partagée de fonctionner dans une scène iOS, iPadOS ou tvOS fournie par son host (application hôte), avec un lifecycle (cycle de vie) correct, des surfaces et entrées utilisables, puis les autres capabilities (capacités) communes applicables. UIKit et SwiftUI conservent les scènes, les vues, le layout et le rendu. Le premier jalon utile est une session UIKit réelle sur chaque famille d’OS, consommée depuis Swift, qui observe sa surface et se termine sans fuite. Sur tvOS, le parcours interactif comprend la navigation par focus (élément ciblé) et la télécommande.

Cette roadmap ordonne le travail ; elle ne modifie pas à elle seule l’API publique ou les garanties normatives. Les autorités sont [DESIGN.md](DESIGN.md), [PUBLIC-API-CATALOG.md](PUBLIC-API-CATALOG.md), [OPERATION-CONTRACTS.md](OPERATION-CONTRACTS.md), [BACKEND-CAPABILITIES.md](BACKEND-CAPABILITIES.md), [INTEROP-EXPORTS.md](INTEROP-EXPORTS.md), [POLICY-PROFILES.md](POLICY-PROFILES.md), [PROJECT-ARCHITECTURE.md](PROJECT-ARCHITECTURE.md) et [TEST-STRATEGY.md](TEST-STRATEGY.md). Leur extension explicite à tvOS est un livrable de phase 0, préalable à l’activation des contrats tvOS. Le chantier 7 de [PLAN-SNAPSHOT.md](PLAN-SNAPSHOT.md) fournit le périmètre historique ; ses anciens chemins ne désignent pas l’implantation actuelle.

## 1. Point de départ vérifié

| Élément | État dans la base inspectée | Conséquence |
|---|---|---|
| Contrat UIKit et façade Swift | Définis pour iOS dans les documents normatifs ; disponibilité tvOS non fermée | Préserver les signatures iOS et fermer leur extension tvOS avant implémentation |
| `kadre`, `foundation`, `runtime` | Targets JVM, JS et Wasm ; aucune target iOS ou tvOS déclarée | Ajouter et vérifier toute la chaîne Kotlin/Native pour les deux familles |
| Runtime commun | Sessions, surfaces, input, interactions, IME, drops, displays, devices et capture déjà présents | Réutiliser les reducers (réducteurs d’état) et les ports communs |
| Primitives `expect` du runtime | `RuntimeLock`, `IdentityKeyedMap`, `InteractionCallFrame`, `Throwable.isLinkageFailure` | Fournir leurs implémentations natives et tester la concurrence réelle |
| Runtime de fenêtres | `RuntimeWindowManager`, `RuntimeWindowEventFlow`, `WindowCommandPort`, `SurfaceCommandPort`, `MinimalWindowSurface` dans `jvmMain` | Extraire le minimum portable nécessaire avant de promettre une `Window` UIKit |
| Adapter et preuves UIKit | Aucun `platform:uikit`, driver UIKit, consumer Swift ou gate UIKit dans le build courant | Construire une première tranche exécutable avec sa preuve native |
| Ancien support iOS | Sources et script sous `kadre-old/` | Référence pour les conversions SDK ; aucune preuve de conformité au nouveau contrat |
| Anciennes mentions tvOS | Commentaires de handles UIKit, sans target ni adapter tvOS dans les builds inspectés | Ne pas les compter comme support tvOS existant |

La réutilisation des tests JVM/Web est utile, mais ne prouve ni le linking (édition de liens) Kotlin/Native, ni les callbacks UIKit, ni le comportement des coroutines sur le main thread (thread principal).

## 2. Architecture et décisions de départ

```text
application Kotlin partagée / factory exportée
                    |
host UIKit ou SwiftUI — façade Swift KadreHost
                    |
             KadreIos.attach
                    |
          platform:uikit, ports SDK
                    |
        runtime commun + foundation
```

`platform:uikit` utilise les SDK Apple fournis par Kotlin/Native et les objets explicitement reçus du host. Il ne dépend pas de `backend:appkit`, de la JVM ou de KFFI/FFM. Un source set partagé `uikitMain`, à déclarer explicitement dans la hiérarchie KMP, porte seulement l’intersection des APIs iOS/tvOS ; `iosMain` et `tvosMain` portent les différences de disponibilité et d’input. Les primitives du runtime partageables restent dans `appleMain`, sans UIKit. Un simple test runtime ne permet pas de référencer un symbole absent du SDK tvOS. Un besoin de binding additionnel fait l’objet d’une décision explicite ; il ne justifie pas une couche FFI locale.

Le runtime conserve admission, cancellation, IDs, révisions, budgets, delivery (livraison des événements), diagnostics, ownership (responsabilité de durée de vie) et teardown (libération terminale). L’adapter traduit les observations SDK en stimuli immuables et exécute sur le main thread les commandes déjà admises. Aucun sous-projet artificiel `ios-input`, `ios-window` ou `ios-capture` n’est nécessaire.

Trois approches sont possibles : porter l’ancien event loop, réécrire un runtime UIKit autonome, ou brancher des ports UIKit sur le runtime commun. **La troisième est retenue dans cette proposition** : elle préserve les contrats déjà vérifiés et limite le travail propre à iOS aux frontières SDK. Les anciens mappers peuvent inspirer des fixtures ; leurs registries et leur event loop ne sont pas transférés.

### Décisions à fermer dans le design de phase 0

| Décision | Résultat attendu avant activation du contrat concerné |
|---|---|
| Versions iOS/iPadOS/tvOS, Xcode, Swift et runtimes Simulator | Matrice exacte épinglée, compatible avec Kotlin `2.4.20` actuellement déclaré ; minimum supporté et version courante testés séparément pour iOS et tvOS. Aucun numéro de déploiement n’est déduit de l’ancien code |
| Disponibilité publique tvOS | Fermer l’extension des contrats UIKit, de l’attach et des façades Swift à tvOS ; conserver les noms iOS existants, sans ajouter implicitement `KadreTvos` ou une nouvelle valeur de `KadrePlatform` |
| Observation d’une `UIView` arbitraire | Mécanisme démontré pour metrics, focus et callbacks, compatible avec une vue existante ; aucune obligation publique de sous-classe ajoutée silencieusement |
| Fenêtre fournie par le host | Frontière portable minimale extraite de `jvmMain`, fermeture logique distincte de la destruction native et relation primaire/surface explicites |
| Export Kotlin/Swift | Un assemblage cohérent pour la factory applicative, le framework et le module source `KadreHost`, prouvé par un consumer externe |
| Demandes de scènes supplémentaires | Chemin de corrélation entre activation, connexion, refus et discard, sans ajouter un overload public hors catalogue |
| Télécommande et focus tvOS | Mapping explicite des pressions natives vers les types d’input existants, séparation focus/activation, et partage de responsabilité avec le moteur de focus du host |

Le chemin d’interop proposé est un framework Kotlin/Native via Objective-C, accompagné du wrapper source Swift promis. Kotlin distingue les dépendances KMP des exports de framework : une dépendance `api` seule ne suffit pas à exporter un type. La phase 3 doit tester les exports explicites et l’assemblage XCFramework, y compris l’identité de la factory entre modules. [Documentation Kotlin sur les binaires et exports](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html).

Le Swift export direct reste une alternative à évaluer séparément : la documentation consultée le classe encore Alpha et décrit des limites d’intégration. La roadmap ne dépend donc pas de son adoption. [Documentation Swift export](https://kotlinlang.org/docs/native-swift-export.html).

### Matrice iOS et tvOS

Kotlin/Native propose des targets distinctes `tvosArm64` et `tvosSimulatorArm64`. Elles doivent être compilées, publiées et exécutées dans leurs propres environnements ; une preuve iOS ne prouve pas tvOS. [Targets Kotlin/Native](https://kotlinlang.org/docs/native-target-support.html).

| Domaine | iOS / iPadOS | tvOS | Phase responsable |
|---|---|---|---|
| Host et lifecycle | Scène explicite ; multi-scène conditionnel | Scène UIKit explicite, parcours mono-scène ; fermeture/réactivation propres à tvOS | 0–1 |
| Surface | Rotation, resize, safe area et scale | Géométrie de la vue et zone sûre TV ; ne pas déduire le framebuffer de la résolution HDMI annoncée | 2 |
| Distribution Swift | Frameworks appareil/simulateur iOS et wrappers UIKit/SwiftUI | Slices appareil/simulateur tvOS distinctes et consumers UIKit/SwiftUI tvOS | 3 |
| Nouvelle fenêtre | `OpenedInNewSession` sur configuration compatible | `Rejected(Unsupported(RequestWindow))` pour le profil mono-scène, sans appeler une API d’activation indisponible | 4 |
| Input principal | Touch direct, stylet, clavier et pointer selon support | Télécommande, navigation par focus, sélection et boutons reçus ; GameController selon matériel | 5, 8 |
| Touch et gestes | Contacts locaux de la surface | Ne pas présenter le pavé tactile de la télécommande comme des contacts directs sur l’écran ; mapping séparé seulement s’il respecte les unités et le contrat | 5 |
| Texte / IME | Intégration au système de saisie iOS | Parcours de saisie système tvOS, avec validation du focus et de la restitution du résultat ; aucune parité IME supposée | 6 |
| Drop, raw input, capture et effets | Décision par API et environnement | Audit SDK/permissions/matériel propre à tvOS ; état absent exact pour chaque primitive non disponible | 7–9 |

Le moteur de focus UIKit dirige la navigation parmi les éléments du host et dialogue avec `UIFocusEnvironment`. Kadre observe sa propre frontière de surface et les entrées reçues ; il n’introduit pas de moteur de navigation de widgets. [Documentation du focus UIKit](https://developer.apple.com/documentation/uikit/uifocusenvironment).

Le profil tvOS de cette roadmap est mono-scène. La documentation Apple réserve les scènes simultanées aux plateformes et configurations compatibles ; l’existence de `UIWindowScene` n’est pas une promesse de fenêtres multiples sur tvOS. [Disponibilité des scènes multiples](https://developer.apple.com/documentation/visionos/presenting-windows-and-spaces).

## 3. Invariants communs à toutes les phases

### 3.1 Attachement, scène et surface

L’unique entrée Kotlin iOS reste celle-ci. La proposition tvOS réutilise cette surface UIKit et les noms existants, après fermeture de sa disponibilité dans les documents normatifs en phase 0 :

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

- Une seule session vivante par `UIWindowScene`, avec un scope parent possédé par l’adapter sur le dispatcher principal de la scène.
- `window.windowScene === windowScene`, `surfaceView.window === window` et scène connectée sont des préconditions. Les failures utilisent les fields fermés `window` ou `surfaceView`, jamais un nouveau field `windowScene`.
- Un second attach à une scène possédée retourne `AlreadyInUse(Host)` avant toute installation ou invocation de la factory.
- La fenêtre fournie devient `WindowManager.state.value.primary` ; `primarySurface` est exactement la même instance que `primary.surface`. Les overlays ne deviennent pas des fenêtres Kadre.
- Aucun choix de key window, overload `UIApplication`, renderer, remplacement de vue ou singleton de session. Un registre technique de propriétaires peut vérifier l’exclusivité et router des callbacks identifiés ; il ne définit jamais une session « courante ».
- Les cinq managers `windows`, `displays`, `devices`, `capture` et `diagnostics`, avec leurs snapshots initiaux, existent avant `Running`. `KadrePolicies.Default` est toujours accepté.

### 3.2 Lifecycle et terminaison

Les axes attachement, foreground et activation sont dérivés de la scène concernée. `sceneDidBecomeActive`, désactivation, entrée en arrière-plan et reconnexion ne se confondent pas. Le state (état) public est publié avant l’event (événement) associé.

`sceneDidDisconnect` termine la session ; une reconnexion exige une nouvelle session et une nouvelle instance applicative. Un retrait temporaire de la vue du controller SwiftUI ne suffit pas à fermer la scène et ne publie pas prématurément `SurfaceState.Detached`, qui est terminal. L’adapter suspend les observations qui n’ont plus de source valide. Le mapping exact du retrait, de la réinsertion et d’un transfert vers une autre scène doit être fermé dans le design de phase 1.

Apple distingue la déconnexion d’une scène de son simple passage en arrière-plan ; la notification de déconnexion identifie la scène dans son objet. Cela permet un routage explicite, sans utiliser l’état agrégé de `UIApplication` comme état de chaque session. [Documentation Apple sur la déconnexion](https://developer.apple.com/documentation/uikit/uiscene/diddisconnectnotification).

La fermeture révoque d’abord l’admission, puis les observers et callbacks, les interactions et opérations pendantes, les jobs applicatifs et ressources de session ; elle libère enfin les bridges et l’exclusivité. Elle ne détruit pas la `UIWindow` ou la `UIView` fournies par le host. Aucune session ne ressuscite après terminaison. Aucun délai de teardown ne promet de faire exécuter du code lorsque le processus iOS ou tvOS est suspendu ou supprimé.

### 3.3 Concurrence, ressources et capabilities

- Les accès UIKit sont confinés au main thread ; les entrées publiques thread-safe restent utilisables depuis les autres dispatchers. Une fonction d’attach non suspendue ne devient pas un dispatch asynchrone caché.
- Les callbacks ordinaires copient le payload avant retour, ne suspendent pas et n’appellent pas l’application. Le seul chemin applicatif synchrone est le contrat explicite d’`InteractionContext`.
- Une exception ne traverse pas Objective-C. Aucun verrou du runtime ne bloque en attendant une action UIKit qui aurait besoin du même verrou.
- Annuler un waiter (appel en attente) ne ferme pas son owner. Après commit, le state et l’outcome terminal restent l’autorité, même lorsque l’appelant a été annulé.
- Les buffers, offres de drop et frames suivent un transfert de propriété explicite ; aucun `Flow` multicast ne diffuse un owner closeable.
- Chaque capacité est soit réellement supportée, soit temporairement indisponible selon le contrat, soit `Unsupported`. Une implémentation différée reste identifiée comme telle ; elle ne devient pas une absence structurelle définitive par commodité.
- `WindowCapabilities.platformAccess` reste `Unsupported(PlatformWindowAccess)` ; l’accès SDK promis est `HostSurface.withUIKitView`, Kotlin-only, avec `@KadrePlatformApi` et `@DelicateKadreApi`.

### 3.4 Points prioritaires de revue

| Risque | Preuve à livrer |
|---|---|
| Deux scènes partagent des jobs, IDs ou callbacks | Isolation et fermeture indépendante en phases 1 et 4 |
| Retrait temporaire SwiftUI traité comme terminaison | Retrait/réinsertion sans second attach en phases 1 et 3 |
| Cancellation Swift doublement reprise ou propriétaire de la session | Race callback/cancellation et continuation reprise une seule fois en phase 3 |
| Metrics incohérentes pendant rotation ou resize iPad | Snapshot taille/scale/insets atomique et coordonnées locales en phase 2 |
| Callback SDK tardif après teardown | Scénario et sentinelle de révocation dans chaque phase ajoutant une source native |
| Télécommande traitée comme écran tactile ou focus confondu avec activation de scène | Parcours tvOS directions/sélection/retour, transfert de focus et reprise en phase 5 |

## 4. Emplacements et responsabilités

Les chemins futurs ci-dessous sont proposés ; ils n’autorisent pas la création de modules vides.

| Chemin | Travail prévu |
|---|---|
| `settings.gradle.kts`, `kadre/build.gradle.kts`, `kadre/foundation/build.gradle.kts`, `kadre/runtime/build.gradle.kts` | Déclarer les quatre targets, l’agrégation iOS/tvOS, la hiérarchie partagée, les publications et les checks |
| `kadre/runtime/src/appleMain/kotlin/org/graphiks/kadre/internal/runtime/` | Implémentations natives partageables des primitives `expect`, sans dépendance UIKit |
| `kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/` | Extraction ciblée du runtime de fenêtre host-owned et réutilisation des ports existants |
| `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/` | `KadreIos.kt`, `UIKitSurfaceAccess.kt`, surface publique commune validée en phase 0 |
| `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/` | Propriétaires de scènes, lifecycle, fenêtres et surfaces utilisant l’intersection SDK |
| `kadre/platform/uikit/src/iosMain/`, `kadre/platform/uikit/src/tvosMain/` | Ports spécifiques ; touch/IME/drop iOS, focus/télécommande tvOS et autres écarts SDK |
| `kadre/platform/uikit/src/uikitTest/`, `kadre/platform/uikit/src/iosTest/`, `kadre/platform/uikit/src/tvosTest/` | Tests partagés et spécifiques de traduction SDK, ownership et races natives |
| `kadre/platform/uikit/swift/Sources/KadreHost/` | Façade Swift standard et `KadreHostViewController` |
| `kadre/integration/swiftui/` | Packaging optionnel et glue SwiftUI utile ; le wrapper `UIViewControllerRepresentable` reste possédé par l’application |
| `kadre/contracts/driver/uikit/` | Targets hôtes Xcode iOS et tvOS, XCTest/XCUITest, mapping d’evidence, procédures manuelles |
| `kadre/consumers/swift/` | Build autonome contre les artifacts produits ; consumers UIKit/SwiftUI iOS et tvOS et module applicatif KMP minimal |
| `kadre/contracts/registry/contracts.tsv`, `kadre/contracts/validator/` | Contrats UIKit et preuves corrélées Native/XCTest |
| `kadre/capabilities/uikit.md` | Une entrée par feature et famille iOS/tvOS, versions, gates, état absent et tests |
| `scripts/test-uikit-simulator.sh`, `.github/workflows/kadre-uikit-contracts.yml` | Point d’entrée reproductible et gate CI à créer |

## 5. Phases d’implémentation

Chaque phase se termine par un livrable observable. Son plan détaillé nommera fichiers, interfaces internes, tests et commandes exactes ; les étapes ci-dessous servent au suivi de la roadmap, pas de transcript de code.

### Phase 0 — Fondations Kotlin/Native et première preuve UIKit

**But :** lever les risques de compilation, de linking et de preuve avant d’accumuler des fonctionnalités.

- [ ] Fermer le périmètre tvOS dans `DESIGN.md`, `PUBLIC-API-CATALOG.md`, `PROJECT-ARCHITECTURE.md`, `BACKEND-CAPABILITIES.md`, `INTEROP-EXPORTS.md` et `TEST-STRATEGY.md` : disponibilité de l’attach/façade, topologie mono-scène, sources d’input, cibles de publication et preuves. Préciser dans `OPERATION-CONTRACTS.md` tout mapping tvOS qui ne découle pas déjà d’une ligne existante ; préserver les contrats iOS.
- [ ] Épingler la matrice de toolchain et ajouter `iosArm64`, `iosSimulatorArm64`, `tvosArm64` et `tvosSimulatorArm64` aux modules nécessaires ; agréger `platform:uikit` depuis les variantes iOS et tvOS de `kadre` et déclarer les source sets partagés.
- [ ] Implémenter les quatre familles de primitives `expect`. Tester exclusion mutuelle, réentrance, identité référentielle et frame d’interaction partagée par thread ; ne pas copier les no-op des targets Web.
- [ ] Adapter la visibilité entre `foundation` et `runtime` au compilateur Native : les `friendPaths` JVM et options JS actuels ne constituent pas une solution Native. Ne pas rendre le SPI public pour contourner le problème.
- [ ] Créer `platform:uikit` et le driver avec un premier code utile : une application hôte pour chaque famille Simulator crée scène/fenêtre/vue et vérifie une observation UIKit réelle depuis Kotlin.
- [ ] Distinguer les unit tests Kotlin/Native d’un test dans une application UIKit exécutant son vrai lifecycle. Exporter les résultats XCTest vers JUnit en conservant noms, failures, skips et durée.
- [ ] Fermer les décisions du §2 et décrire le minimum de fenêtre à extraire pour la phase 1. Garder l’adapter hors publication officielle tant que ses garanties structurelles ne sont pas satisfaites.

**Fichiers principaux :** builds de l’umbrella, `foundation`, `runtime`, nouveau `platform/uikit`, driver et script Simulator, validateur.

**Critère de sortie :** compilation et linking des quatre targets ; tests des primitives natives exécutés sur les deux simulateurs ; une preuve O3 issue de chaque application UIKit iOS et tvOS démarrée, avec JSON/JUnit corrélés. Un simulateur manquant fait échouer le job correspondant. Ce jalon ne prétend pas livrer l’attach public complet.

### Phase 1 — Attach public, lifecycle et propriétaires par scène

**But :** obtenir une session conforme avec sa fenêtre et sa surface initiales.

- [ ] Extraire le minimum portable de fenêtre et brancher `RuntimeSessionComponentsFactory`, `RuntimePrimarySurface` et le contrôleur commun. Préserver les tests AppKit ; ne pas dupliquer le reducer de fenêtre dans UIKit.
- [ ] Implémenter l’attach exact, les validations, le scope par scène et le rollback de toute installation partielle. La factory n’est appelée qu’après admission.
- [ ] Initialiser la fenêtre primaire et sa surface partagée, leurs métriques initiales, le redraw minimal et les cinq managers avant `Running` ; les domaines non livrés restent explicitement non supportés.
- [ ] Relayer les transitions de `UISceneDelegate`/notifications avec filtrage par identité de scène et un seul chemin d’admission, sans remplacer le delegate de l’application ni doubler les événements.
- [ ] Fermer le mapping du retrait temporaire et du changement de scène de la vue ; implémenter disconnect, arrêt explicite, erreur applicative et teardown partiel.
- [ ] Livrer les scénarios attach invalide, double attach, deux scènes indépendantes, arrêt pendant `Starting`, disconnect concurrent à l’arrêt, callback tardif et nouvel attach après terminaison.
- [ ] Exécuter le parcours mono-scène tvOS : attachement, background/foreground, désactivation lors d’une interruption système, arrêt et reconnexion. Garder la preuve positive à deux scènes sur iPadOS ; la compléter par la branche mono-scène tvOS au lieu de produire un test ignoré.

**Fichiers principaux :** runtime de fenêtres, `KadreIos.kt`, propriétaires et ports lifecycle/fenêtre/surface internes, tests et mapping UIKit.

**Critère de sortie :** deux scènes iPadOS exécutent deux applications distinctes ; déconnecter l’une ne modifie pas l’autre. Le host mono-scène tvOS passe les transitions et le teardown sans résurrection. Aucun objet host-owned n’est détruit. La première tranche publique respecte toutes les garanties structurelles initiales de chaque famille et ses contrats sont activés avec leurs preuves.

### Phase 2 — Surface, géométrie, redraw et accès UIKit

**But :** permettre à un renderer appartenant au consumer d’utiliser correctement la surface.

- [ ] Compléter les observations de taille logique, taille physique, scale, safe-area insets (marges de sécurité), visibilité, focus et appearance ; publier `Unknown` quand une composante ne peut pas être observée.
- [ ] Calculer les conversions depuis un snapshot cohérent et documenter l’arrondi. Distinguer bounds de la vue, zone sûre et dimensions d’un éventuel drawable appartenant au renderer.
- [ ] Couvrir rotation, resize iPad, changement d’écran/scale et thème/contraste. Le layout ne provient pas seulement de l’orientation du device.
- [ ] Couvrir sur tvOS les bounds, safe areas, changements de configuration d’affichage et reprise ; conserver la distinction entre focus dans la surface et activation de la scène. Ne pas appliquer au téléviseur des scénarios d’orientation iPhone.
- [ ] Coalescer `requestRedraw()` sans boucle de rendu permanente ni cadence 60 Hz imposée ; retirer le mécanisme de scheduling à la fermeture.
- [ ] Implémenter `withUIKitView` sur le contexte host, sans réentrance pour la même surface, avec cancellation avant/après entrée et durée de validité bornée au callback.
- [ ] Déclarer champ par champ les possibilités de `HostSurface.apply` ; un champ refusé produit l’outcome fermé prévu, sans faux succès.

**Fichiers principaux :** ports de surface UIKit, `UIKitSurfaceAccess.kt`, driver de metrics et consumers Kotlin iOS/tvOS.

**Critère de sortie :** la vue retournée est celle fournie à l’attach ; state avant event pour chaque changement ; coordonnées d’input prêtes à utiliser les mêmes métriques ; aucune requête redraw ni invocation native après révocation. Les deux opt-ins sont vérifiés par consumers positifs et négatifs.

### Phase 3 — Façade Swift, controller hôte et SwiftUI

**But :** rendre le premier parcours intégrable par des applications iOS et tvOS externes.

- [ ] Produire le module source `KadreHost` et les types exacts de `INTEROP-EXPORTS.md` §5 : `KadreIosHost`, `KadreSessionHandle`, `KadreObservation`, snapshots, outcomes, profils et failures fermées.
- [ ] Traduire les failures directes d’attach en `KadreError`, sans exposer `Flow`, `CoroutineScope`, `Continuation`, managers ou callbacks input dans la façade promise.
- [ ] Livrer `observeState` avec émission immédiate et sérialisée sur `@MainActor`, retrait idempotent, ainsi qu’`awaitTermination` dont la cancellation Swift retire uniquement le waiter et lève `CancellationError`.
- [ ] Implémenter `KadreHostViewController` : attach une seule fois quand sa view appartient à une fenêtre et une scène ; même view, aucun layout Kadre, fermeture par `deinit`, disconnect ou `close` explicite.
- [ ] Ajouter le wrapper SwiftUI minimal dans le consumer et matérialiser `integration:swiftui` seulement avec du contenu réel. Une réévaluation de `body` ou un retrait temporaire ne déclenche pas un nouvel attach.
- [ ] Assembler framework/XCFramework et wrapper source ; compiler un consumer autonome résolvant les artifacts exportés, avec une factory produite par son module applicatif Kotlin. Vérifier l’absence de duplication du runtime et l’identité des types traversant les modules.
- [ ] Inclure les slices tvOS appareil/simulateur dans la distribution et déclarer la plateforme tvOS dans le packaging Swift. Compiler et exécuter les consumers UIKit et SwiftUI tvOS ; les imports, annotations de disponibilité et symboles iOS-only ne doivent pas fuir dans ces builds.

**Fichiers principaux :** `platform/uikit/swift`, `integration/swiftui`, `consumers/swift`, configuration d’exports et tests XCTest.

**Critère de sortie :** les hosts UIKit et SwiftUI externes iOS et tvOS attachent, observent, arrêtent et attendent la terminaison à partir de leurs propres artifacts. La race cancellation/terminaison ne reprend aucune continuation deux fois ; annuler une Task n’arrête pas la session. Aucun cycle de rétention controller/session/observer ne persiste après fermeture.

### Phase 4 — Demandes de scènes et opérations de fenêtre

**But :** terminer le contrat multi-scène iOS/iPadOS et la branche mono-scène tvOS avec des capabilities exactes.

- [ ] Observer la possibilité réelle d’ouvrir une scène selon appareil, configuration du host et état système. Sans support, retourner une `WindowRequest` déjà `Rejected(Unsupported(RequestWindow))`, pas une failure directe de `requestWindow`.
- [ ] Sur tvOS, prouver le refus mono-scène et l’absence d’appel à une primitive de nouvelle scène. Isoler dans `iosMain` toute API d’activation non disponible sur tvOS ; le receiver UIKit partagé ne promet pas sa disponibilité.
- [ ] Corréler chaque demande admise avec la connexion effective de sa scène, en préservant `originatingRequestId`, `AdditionalHostRequested`, la factory et la policy. Une erreur native ou un discard explicite utilise un outcome fermé.
- [ ] Définir le traitement d’une connexion tardive après cancellation ou fermeture du requester : ne jamais résoudre une requête terminale une seconde fois, ni garder une ressource transitoire sans owner.
- [ ] Garder `Pending` en l’absence de signal terminal ; aucun timeout synthétique. Une scène déjà ouverte possède sa propre session, indépendante de la session requérante.
- [ ] Auditer les champs de `Window.apply`, attention et close selon les primitives réellement disponibles. Fermer le mapping entre fermeture logique Kadre et demande de destruction de scène, sans cacher ou détruire arbitrairement une fenêtre du host.
- [ ] Couvrir connexion, refus, discard, absence de callback, deux demandes concurrentes, cancellation avant/après commit, fermeture du requester et préservation des overlays.

**Fichiers principaux :** ports de fenêtre/scène UIKit, composant de corrélation identifié, runtime de requêtes, driver multi-scène et documentation du host.

Apple transmet l’activité associée à une demande d’activation dans les options de connexion. C’est un point d’appui pour la corrélation proposée ; le succès Kadre reste conditionné à l’attach effectif de la nouvelle session. [Documentation de connexion d’une scène](https://developer.apple.com/documentation/uikit/uiscenedelegate/scene(_:willconnectto:options:)).

**Critère de sortie :** `OpenedInNewSession` suit toujours une vraie connexion corrélée. Une nouvelle fenêtre n’est jamais ajoutée à la liste de la session demandeuse. Le parcours positif est démontré sur une configuration iPad multi-scène ; le parcours tvOS prouve `Rejected(Unsupported(RequestWindow))` sans effet natif.

### Phase 5 — Touch, clavier, pointer, gestes et télécommande tvOS

**But :** livrer l’input ordinaire avec les mêmes garanties que le runtime commun.

- [ ] Installer les sources sur la surface concernée, sans interception globale ni modification silencieuse des contrôles natifs du host. Prouver le mécanisme choisi pour une vue fournie arbitrairement.
- [ ] Mapper touch et stylet : identité stable par contact, coordonnées locales, phases de fin/annulation, pression seulement lorsqu’elle est connue et valide ; aucun contact tactile dupliqué en pointer.
- [ ] Mapper clavier physique, modifiers, répétitions, pointer, hover, boutons et scroll quand le SDK et le matériel le permettent ; séparer touches physiques et texte composé.
- [ ] Déclarer les gestes réellement reconnus et leur subset exact. Ne pas fabriquer de gestes à partir du touch si aucun recognizer conforme n’est installé.
- [ ] Installer le chemin de télécommande tvOS via les callbacks de pressions réellement reçus : début, répétition éventuelle, fin et annulation. Fermer le mapping des directions, sélection, retour/Menu et lecture/pause vers les types existants ; une identité physique inconnue reste `PhysicalKey.Unidentified`, jamais un code HID inventé. Ne pas déduire une capability de clavier matériel de la seule présence de la télécommande.
- [ ] Observer les transitions `UIFocusEnvironment` à la frontière de la surface : déplacement entre descendants, sortie, retour et restauration après interruption. Le moteur de focus UIKit/SwiftUI reste propriétaire du parcours ; un changement de descendant focalisé ne désactive pas la scène.
- [ ] Préserver les actions système et les contrôles du host. Ne pas déclencher deux fois une action par la combinaison pression native/GameController ; ne pas convertir un swipe de télécommande en contact tactile direct ou position d’écran fictive.
- [ ] Réutiliser `SurfaceStimulus` et le reducer commun, avec budgets, resets, coalescence et barrières discrètes ; définir précisément les default actions que l’adapter peut supprimer.
- [ ] Tester multitouch, cancellation OS, focus perdu, changement de scène, modifier relâché hors focus, overflow et callback après teardown.
- [ ] Ajouter sur tvOS les scénarios direction/sélection/retour, pression maintenue puis interruption, changement de cible focalisée, sortie/rentrée dans l’application et relâchement après teardown. Les déclencher avec `XCUIRemote` depuis le driver, puis compléter par une Siri Remote réelle et les générations de télécommande déclarées supportées.

**Fichiers principaux :** ports input UIKit, sources tvOS de focus/télécommande, tests de traduction et scénarios XCUITest ; cahiers matériels pour stylet, souris, clavier et Siri Remote.

`XCUIRemote` permet d’envoyer des pressions brèves ou maintenues pendant un test tvOS. Cette frontière fournit la preuve automatisée du parcours de télécommande ; elle ne remplace pas les essais des caractéristiques matérielles. [API de test des pressions de télécommande](https://developer.apple.com/documentation/xcuiautomation/xcuiremote/press(_:)).

**Critère de sortie :** aucune touche, bouton ou contact ne reste actif après reset/fermeture ; snapshots publiés avant événements ; les capacités annoncées correspondent aux sources installées et à leurs limites documentées. Sur tvOS, le consumer se parcourt à la télécommande sans double action, faux touch ou prise de contrôle du moteur de focus.

### Phase 6 — Text input, IME et clavier logiciel

**But :** utiliser l’édition native sans déplacer la logique applicative ou le document dans Swift.

- [ ] Définir le receiver UIKit et le chemin responder (chaîne de réponse) nécessaires au contrat `UITextInput`, en préservant la vue et le focus du host ; annoncer le support seulement si ce chemin est disponible.
- [ ] Brancher le port commun `TextInputPort` et `RuntimeTextInputSession` : surrounding text (texte environnant), sélection, composition, curseur et actions de soumission.
- [ ] Maintenir les révisions acceptées et les conversions UTF-16 ; traiter texte marqué, remplacement, suppression, emoji et sélection sans inventer une frappe clavier par caractère.
- [ ] Couvrir ouverture exclusive, refus de révision périmée, perte/reprise de focus, interruption système, fermeture et callbacks IME tardifs.
- [ ] Ajouter un parcours de saisie réellement produit par UIKit ; compléter les branches déterministes par un cahier manuel pour candidats IME et claviers de langues différentes.
- [ ] Valider séparément le chemin de saisie système tvOS, son entrée/sortie de focus, confirmation et annulation, ainsi que les sources de saisie déportées lorsqu’elles sont disponibles. Ne pas copier des receivers ou symboles IME réservés à iOS ; documenter les limites de composition et la branche `Unsupported` si le contrat ne peut pas être satisfait.

**Fichiers principaux :** port text input UIKit, receivers SDK, tests Native et consumer de saisie.

**Critère de sortie :** une seule session texte active par surface, aucun remplacement fondé sur une révision périmée, aucune composition orpheline après fermeture. Le clavier logiciel ne redimensionne pas implicitement le layout détenu par SwiftUI.

### Phase 7 — Drag-and-drop et interactions synchrones

**But :** recevoir les données natives avec un transfert de propriété et des limites vérifiables.

- [ ] Relier les callbacks de drop aux offres, métadonnées copiées et actions `AcceptDrop`, selon le timing natif effectivement disponible.
- [ ] Réutiliser le moteur commun d’`InteractionContext` : token borné au callback, single-use (usage unique), vérification de surface et admission synchrone ; n’annoncer que les actions réellement exécutables.
- [ ] Relier le chargement asynchrone des données du provider aux `DropTransfer`, avec un seul gagnant du claim (prise de possession), bornes en octets et fermeture idempotente.
- [ ] Tester rejet, sortie, drop effectué, lecture interrompue, taille inconnue, dépassement de budget, fermeture pendant chargement et callback tardif.
- [ ] Séparer les APIs de drop iOS des sources communes ; sur tvOS, prouver l’état absent exact en l’absence de primitive conforme, sans listener factice ni appel à un symbole indisponible. Auditer les interactions synchrones tvOS indépendamment du support de drop.

**Fichiers principaux :** ports drop/interactions UIKit, mapping d’offres, tests et procédure de drag entre applications.

**Critère de sortie :** aucun `NSItemProvider`, objet natif emprunté ou accès fichier non borné ne traverse l’API commune ; les transferts sont fermés une seule fois. Le drag sortant n’est pas ajouté au périmètre sans contrat public correspondant.

### Phase 8 — Displays, signaux système, devices et gamepads

**But :** remplir les managers communs avec des observations honnêtes des environnements iOS et tvOS.

- [ ] Observer les displays et changements de rattachement de scène. Si seul le viewport est connu, publier explicitement un `DisplayType.HostViewport` selon le contrat ; ne pas présenter une liste partielle comme inventaire physique exhaustif.
- [ ] Relayer la pression mémoire réellement applicable aux sessions vivantes, sans transformer une notification non graduée en gravité inventée ; documenter son mapping avant activation.
- [ ] Brancher `InputDevicePort` et `GamepadPort` sur les primitives publiques disponibles : connexion, déconnexion, snapshots, routing (routage) et effets effectivement supportés.
- [ ] Décider séparément chaque domaine : l’observation gamepad ne prouve pas un inventaire HID complet, et son haptique ne prouve pas le support de tous les effets.
- [ ] Sur Apple TV, vérifier découverte et reconnexion de manettes, profils effectivement exposés, coexistence avec la télécommande et effets selon matériel. Ne pas présenter toute Siri Remote comme une manette complète ni promettre ses capteurs ou son haptique à partir d’une autre génération.
- [ ] Conserver `Unsupported(RawInputAccess)` tant qu’aucune primitive publique ne satisfait ce contrat. Ne pas émuler le raw input (entrée brute) avec le flux de touch/pointer ordinaire.
- [ ] Tester disparition/reconnexion, changement de capability, fermeture d’une scène pendant un effet et retrait du dernier abonné d’une source partagée.

**Fichiers principaux :** ports display/device/gamepad UIKit, relais système à ownership explicite, registre de capabilities et cahiers matériels.

**Critère de sortie :** inventaires complets ou états d’absence exacts, nouvel ID après disparition terminale, effets interrompus et callbacks retirés au teardown. Une preuve Simulator de traduction ne vaut pas validation d’un gamepad ou écran externe physique.

### Phase 9 — Capture et permissions

**But :** fermer séparément les capabilities de capture iOS et tvOS sans promettre une source ou une permission que la plateforme ne fournit pas.

- [ ] Examiner séparément `CaptureTarget.HostChoice`, `Source` et `Surface` et leur correspondance avec les APIs publiques disponibles, notamment ReplayKit lorsque pertinent. Un enregistrement de l’application entière ne prouve pas une capture isolée de surface.
- [ ] Compiler et tester le chemin tvOS sur son SDK et sur Apple TV pour les capacités annoncées ; l’existence d’un framework commun ne prouve pas la disponibilité de chaque méthode, picker ou format. Garder explicites les chemins sans primitive conforme.
- [ ] Fixer la sémantique des sources, consentements, interruptions et révocations ; `HostPickerOnly` n’est annoncé que si un vrai parcours conforme existe. Aucune demande de permission implicite à l’énumération.
- [ ] Si une primitive exige un type de permission ou un lifecycle absent du contrat fermé, corriger d’abord le document normatif ou conserver le chemin non supporté ; ne pas détourner une permission existante.
- [ ] Brancher les ports capture communs : formats, plans, strides, orientation, dimensions et colorimétrie réellement connus, cadence demandée, copies et budget total en octets.
- [ ] Tester refus utilisateur, interruption, source perdue, stop concurrent, callback après fermeture et libération des buffers même lorsque le consumer est lent ou annulé.

**Fichiers principaux :** port capture UIKit, owners de buffers, driver et cahier sur appareil pour les parcours non reproductibles sur simulateur.

**Critère de sortie :** chaque target a une décision documentée et prouvée ; aucune frame après stop, aucune mémoire native accessible après invalidation d’une lease (prêt temporaire). Un support conditionnel ne remplace pas sa preuve sur l’environnement concerné.

### Phase 10 — Fermeture du support, distribution et documentation

**But :** rendre les supports iOS et tvOS consommables, reproductibles et auditables.

- [ ] Terminer `capabilities/uikit.md`, une entrée par feature et famille iOS/tvOS de la matrice normative, avec minimum testé, compile gate, runtime gate, état absent exact et preuves. Ne pas fusionner deux disponibilités différentes sous une ligne « UIKit » générique.
- [ ] Compiler les consumers Kotlin iOS/tvOS, Swift UIKit et SwiftUI contre les publications temporaires et frameworks distribuables ; vérifier exports, opt-ins, profils et absence d’API ancienne ou interne dans la façade promise.
- [ ] Exécuter les deux branches iOS/tvOS du gate UIKit sur chaque PR et les intégrer au statut agrégé `kadre-pr-contracts` ; tester les versions minimum/courante de chaque OS dans le nightly (exécution nocturne) et les parcours matériels nécessaires avant release.
- [ ] Vérifier les cycles de rétention Kotlin/ARC, les observers, continuations, scopes, transferts et buffers après ouvertures/fermetures répétées.
- [ ] Livrer les guides UIKit, SwiftUI, multi-scène, installation/packaging, capacités conditionnelles et migration depuis `kadre-old`, avec un sample consommateur minimal utilisant l’API publique.
- [ ] Livrer le guide tvOS et un sample navigable à la télécommande, avec saisie système selon support, manette conditionnelle et limites explicites ; documenter séparément les procédures Simulator et Apple TV physique.
- [ ] Revoir chaque différé : implémentation avec preuve, ou absence structurelle explicitement justifiée. Aucun contrat `planned` ne peut être présenté comme fonctionnalité livrée.

**Critère de sortie :** un consumer neuf peut intégrer le parcours documenté sans dépendance `project(...)` vers les sources Kadre ; toutes les obligations du §8 sont démontrées. La suppression de `kadre-old` reste un chantier distinct.

## 6. Dépendances et jalons

```text
P0 fondations + driver
          |
P1 attach + lifecycle + fenêtre/surface initiales
          |
P2 surface complète + accès UIKit
          |
P3 Swift + SwiftUI + consumer externe  ==> Jalon A
          |
          +--> P4 multi-scène/fenêtres ==> Jalon B
          |
          +--> P5 input + télécommande/focus tvOS
          |             |-> P6 IME ---+--> Jalon C
          |             `-> P7 drop --+
          |
          +--> P8 displays/devices
          `--> P9 capture
                       |
          P10 fermeture et distribution ==> Jalon D
```

- **Jalon A :** intégration mono-scène iOS et tvOS utilisable depuis Kotlin et Swift, avec garanties structurelles, surface réelle et fermeture prouvées sur chaque OS.
- **Jalon B :** parcours multi-scène corrélé et isolé sur une configuration iPad compatible, et refus mono-scène tvOS prouvé.
- **Jalon C :** parcours interactif touch/clavier sur iOS, focus/télécommande sur tvOS, texte et drop selon les capacités propres à chaque OS, avec absences explicites pour les variantes non supportées.
- **Jalon D :** audit complet des domaines communs, distribution et preuves de support officiel iOS et tvOS.

L’ordre numérique est l’ordre de revue recommandé. Après le jalon A, les domaines indépendants peuvent avancer séparément si les interfaces communes sont stabilisées. Aucune tranche ne crée un second lifecycle ou scheduler. Les phases 8 et 9 n’exigent pas que l’IME soit terminé ; le gate final dépend de toutes les phases. Les durées seront estimées après la phase 0, qui résout les principaux risques de portage et de packaging ; cette roadmap ne fixe pas de date de release.

## 7. Stratégie de preuve et CI

| Niveau | Contenu | Limite |
|---|---|---|
| O1 structurel | ABI Kotlin/Native, headers/module Swift, consumers positifs/négatifs, variants et exports | Ne prouve pas le lifecycle UIKit |
| O2 déterministe | Reducers, priorités de races, budgets, révisions, traduction d’outcomes et sentinelles | Ne remplace pas un callback SDK réel |
| O3 plateforme | Applications UIKit sur simulateurs iOS et tvOS démarrés, transitions et entrées produites depuis UIKit/XCTest, télécommande par `XCUIRemote` | Une preuve iOS ne remplace pas tvOS ; ne prouve pas automatiquement les permissions ou le matériel physique |
| Complément matériel | IME système, stylet, Siri Remote, manettes, displays et capture lorsque nécessaire, sur appareils iOS et Apple TV | Procédures reproductibles et résultats explicites ; aucun skip déguisé dans le gate obligatoire |

Les contrats iOS et tvOS utilisent les familles existantes `BCK`, `SES`, `SUR`, `WIN`, `INP`, `DSP`, `GPD`, `CAP`, `INT` et `API`. Les numéros libres sont attribués lors de chaque tranche ; aucun ID Web/AppKit existant n’est réutilisé. Ajouter une target iOS ou tvOS à un contrat actif commun exige de fournir sa preuve dans le même changement. Les scénarios partagés s’exécutent sur les deux familles ; les scénarios de multi-scène positif iPad et ceux de télécommande tvOS ont leurs propres targets requises, sans faux résultat ni skip sur l’autre famille.

La première tranche du driver doit fermer et documenter son format de preuves target-aware (distinguant les cibles). Proposition de racine : `kadre/contracts/driver/uikit/build/contract-evidence/<target>/<configuration>/`, avec `contract-evidence/<contractId>.json`, `test-results/TEST-*.xml` et les métadonnées d’exécution correspondantes. `<configuration>` identifie au minimum la version OS et le device de test. Device et Simulator ne peuvent pas écraser leurs artifacts respectifs.

Chaque activation `planned → active` apporte ensemble le code, le registre, le mapping scénario/test, les sentinelles, le capability snapshot et les preuves JSON/JUnit du même commit. Le validateur rejette absence de test, mauvais SHA/target, doublon, failure, error, skip et désaccord JSON/JUnit. Un `.xcresult` conservé pour diagnostic ne remplace pas les données corrélées attendues par le validateur.

Le gate PR exécute les contrats UIKit actifs sur **`iosSimulatorArm64` et `tvosSimulatorArm64`**, compile et lie également **`iosArm64` et `tvosArm64`**, et compile/exécute les consumers Swift applicables à chaque OS. Les deux jobs de simulateur sont obligatoires et indépendants, avec destinations et artifacts distincts ; aucune condition d’absence de runtime tvOS ne peut les rendre verts. La compilation appareil n’est pas une preuve d’exécution sur appareil. Toute capability dont la preuve nécessite du matériel est liée à un environnement nightly/release déclaré ; le job PR vérifie son chemin observable sans le présenter comme preuve matérielle.

Les budgets restent ceux de `TEST-STRATEGY.md` : p95 d’exécution du job au plus 8 minutes, timeout dur 15 minutes et watchdog des processus enfants ; objectif agrégé p95 au plus 10 minutes sur les 20 derniers gates réussis. Aucun retry automatique, `continue-on-error`, filtre de chemins ou absence de simulateur transformée en succès.

`scripts/test-uikit-simulator.sh` est **à créer** ; seul son ancien équivalent existe sous `kadre-old/scripts/`. Son interface devra sélectionner explicitement la famille iOS ou tvOS et sa destination, vérifier la toolchain, démarrer le simulateur correspondant, lancer les tests, valider les preuves et retourner un code d’échec fidèle. Les commandes exécutables exactes seront livrées avec la phase 0, pas présentées ici comme déjà disponibles.

## 8. Checklist de fermeture

- [ ] La disponibilité tvOS des contrats, noms publics et façades est fermée dans les documents normatifs avant activation.
- [ ] Les quatre variantes iOS/tvOS de `kadre`, `foundation`, `runtime` et `platform:uikit` compilent, se lient et se consomment depuis les artifacts distribués.
- [ ] L’attach exact valide scène/fenêtre/vue, refuse le double owner et initialise les cinq managers avant `Running`.
- [ ] La fenêtre primaire et `primarySurface` partagent la même surface ; overlays, vue, layout et rendu restent possédés par le host.
- [ ] Disconnect termine uniquement la session concernée ; une reconnexion n’en ressuscite aucune.
- [ ] Metrics, redraw, accès UIKit et mutations de surface respectent révisions, main thread et cancellation.
- [ ] La façade `KadreHost` et le controller SwiftUI respectent les signatures, l’observation et la cancellation non propriétaire promises.
- [ ] Les demandes de scènes sont corrélées jusqu’au terminal réel, sans timeout implicite ni session synthétique.
- [ ] Le parcours tvOS mono-scène refuse une nouvelle fenêtre sans effet natif ; ses entrées de télécommande respectent le focus du host et les actions système.
- [ ] Input, IME, drop, displays, devices, gamepads, raw input et capture ont chacun une décision et une preuve adaptées.
- [ ] Aucun contrat actif ne manque de scénario, sentinelle ou rapport corrélé ; le registre de capabilities est complet.
- [ ] Les tests de rétention et de callback tardif couvrent chaque source native et chaque ressource closeable.
- [ ] Les guides et consumers démontrent les hosts UIKit et SwiftUI iOS/tvOS avec une factory Kotlin partagée ; les slices XCFramework des deux OS restent distinctes.
- [ ] Les preuves des deux simulateurs sont obligatoires et séparées ; les essais Siri Remote/manette/capture annoncés sont réalisés sur Apple TV physique.

## 9. Hors périmètre

Cette roadmap n’ajoute ni renderer Metal, widgets, scene graph ou layout Kadre ; ni implémentation métier de `KadreApplication` directement en Swift ; ni ports watchOS, visionOS ou Mac Catalyst. tvOS fait partie du périmètre UIKit décrit ci-dessus. `iosX64` et `tvosX64` ne sont pas des cibles de cette proposition et nécessiteraient un besoin de distribution ainsi qu’une matrice de preuve propres. La persistance applicative, la restauration de documents, les tâches de fond, l’App Store et la suppression globale du legacy ne sont pas des prérequis implicites de l’adapter UIKit.
