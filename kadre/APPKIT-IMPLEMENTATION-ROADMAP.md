# Kadre — Roadmap d’implémentation complète AppKit/JVM

**Statut :** roadmap architecturale approuvée ; les plans d’implémentation détaillés restent séparés par phase.
**Date :** 24 août 2026.
**Cible :** macOS sur JVM 25, backend AppKit via KFFI.
**Mode de livraison :** tranches verticales pilotées par le risque, découpables en plusieurs PRs revues séparément.

Ce document ordonne la suite de l’implémentation AppKit après le standalone headless. Il ne remplace aucune spécification normative et ne rouvre pas l’API publique fermée.

Les autorités restent, par ordre de responsabilité :

- `DESIGN.md` pour les invariants sémantiques ;
- `PUBLIC-API-CATALOG.md` pour la surface lexicale publique ;
- `OPERATION-CONTRACTS.md` pour les failures, outcomes et frontières d’autorité ;
- `BACKEND-CAPABILITIES.md` pour les garanties et disponibilités AppKit ;
- `POLICY-PROFILES.md` pour les budgets et stratégies de delivery ;
- `PROJECT-ARCHITECTURE.md` pour les projets et dépendances ;
- `TEST-STRATEGY.md` pour les preuves et gates ;
- `KFFI-REQUIREMENTS.md` pour les bindings natifs manquants ;
- `APPKIT-JVM-FIRST-IMPLEMENTATION.md` pour le snapshot du premier livrable AppKit.

En cas de contradiction, la spécification normative concernée est corrigée avant le code et avant l’activation du contrat dépendant.

## 1. Objectif et définition de la couverture complète

L’objectif est de couvrir toute la surface commune et Desktop applicable à une session AppKit :

- standalone et embedded ;
- lifecycle et signaux host ;
- fenêtres, surfaces et interactions transitoires ;
- clavier, pointeur, touch, gestures, IME, drag-and-drop et raw input ;
- displays, devices, gamepads et effets ;
- capture et permissions associées ;
- interop Desktop, diagnostics, policies et teardown.

La couverture complète ne signifie pas que chaque capability annonce systématiquement `Supported`. Pour chaque fonctionnalité publique, le backend doit prendre une décision documentée et prouvée parmi :

1. support structurel avec disponibilité runtime honnête ;
2. indisponibilité temporaire ou conditionnelle exposée par le state prévu ;
3. `Unsupported` stable lorsqu’AppKit/macOS ne fournit pas de primitive conforme.

Une branche non implémentée ne retourne jamais un faux succès, une liste vide présentée comme complète ou un no-op silencieux.

## 2. Base déjà acquise

La roadmap commence après les capacités suivantes, déjà implémentées et couvertes par `APK-001` :

- découverte paresseuse du provider AppKit par `ServiceLoader` ;
- sélection `DesktopBackend.Auto` et `DesktopBackend.AppKit` sur macOS ;
- standalone headless possédant `NSApplication.run()` ;
- vérification du main thread sans relocation silencieuse ;
- ownership standalone exclusif et réutilisable ;
- arrêt et réveil de la boucle sans `terminate:` comme mécanisme ordinaire ;
- conversion des failures et cancellations post-admission vers le `SessionOutcome` autoritaire ;
- gate CI AppKit avec preuve O3 de la boucle active, réutilisation et sentinelles.

`APK-002`, relatif au host embedded, est `active`. Sa preuve associe le routage déterministe de deux sessions (O2) à une observation Objective-C réelle par KFFI (O3) ; elle n’active ni fenêtres ni input, qui restent dans les phases suivantes.

## 3. Invariants globaux

### 3.1 Frontière runtime/AppKit

`runtime` possède les comportements portables :

- admission, cancellation et budgets ;
- machines à états et reducers sérialisés ;
- IDs, révisions et `SessionSequence` ;
- `StateFlow`, `Flow`, backpressure et diagnostics ;
- priorité des failures et outcomes ;
- ownership logique et ordre de teardown.

`backend:appkit` possède uniquement :

- création et destruction des objets AppKit ;
- conversion des payloads natifs vers les types internes Kadre ;
- marshalling vers le main thread ;
- callbacks natifs transformés en stimuli immuables ;
- exécution native de commandes déjà admises ;
- observation des capabilities et permissions macOS ;
- owners KFFI révocables.

Le runtime ne connaît ni AppKit, ni KFFI. Le backend ne réimplémente pas les machines à états portables.

`SessionRuntime` peut recevoir une factory interne de composants de session afin de remplacer un `UnsupportedManager` par un composant réel. Cette extension :

- reste sous `org.graphiks.kadre.internal.*` ;
- ne crée aucun nouveau projet `window`, `surface`, `input`, `capture`, `device` ou `gamepad` ;
- ne modifie pas le Host SPI public ;
- ne fait apparaître aucun type interne dans une signature contractuelle.

### 3.2 Topologie interne

```text
API publique
    |
    v
runtime portable
    |
    v
ports internes de backend
    |
    v
AppKitHostSession
    |-- AppKitWindowManager
    |   `-- AppKitWindowPeer
    |       `-- AppKitSurfacePeer
    |           `-- AppKitInputPeer
    |-- adapters de displays/devices/capture
    `-- owners IME/drop/raw input
            |
            v
brokers AppKit spécialisés
            |
            v
           KFFI
```

`AppKitBackendProvider` reste limité à la disponibilité, la sélection, l’attach et le standalone.

`AppKitProcessBroker` reste centré sur `NSApplication`, le lifecycle du processus et l’enregistrement des hosts. Les domaines réellement process-wide utilisent des brokers spécialisés lorsqu’ils deviennent nécessaires :

- `AppKitDisplayBroker` ;
- `AppKitPermissionBroker` ;
- `AppKitDeviceBroker` ;
- `AppKitRawInputBroker` ;
- `AppKitCaptureBroker`.

Un broker spécialisé n’est créé que si l’API native impose effectivement une subscription ou une coordination process-wide. Aucun broker ne conserve une session, une fenêtre, un collector ou une application « courante ».

### 3.3 Flux entrant

```text
callback AppKit
  -> copie minimale du payload nécessaire
  -> stimulus immuable identifié par owner/session
  -> ingress runtime thread-safe
  -> reducer sérialisé
  -> publication du snapshot
  -> émission de l’événement associé
```

Une callback native ne suspend pas, n’appelle pas l’application, ne publie pas directement dans un flow public et ne laisse jamais une exception Kotlin traverser Objective-C.

Les payloads closeables, notamment drop et capture, utilisent un handoff explicite. Aucun `Flow` multicast ne transporte directement un owner closeable.

### 3.4 Flux sortant

```text
appel public
  -> validation locale
  -> contrôle capability/budget
  -> admission runtime
  -> commande AppKit
  -> marshalling main-thread
  -> commit natif
  -> résultat natif
  -> reducer runtime
  -> state ou outcome public
```

La cancellation suit les trois frontières normatives : aucun effet avant admission, retrait avant commit réversible, puis autorité du state ou de l’outcome tardif après commit.

### 3.5 KFFI

KFFI reste l’unique owner des bindings natifs. Kadre n’ajoute :

- ni downcall ou upcall Panama local ;
- ni wrapper FFI ;
- ni générateur ;
- ni input de génération ;
- ni callback natif construit à la main pour contourner un manque.

Tout binding manquant est enregistré dans `KFFI-REQUIREMENTS.md` et livré dans KFFI avant l’activation de la capability Kadre correspondante.

### 3.6 Teardown normatif

Chaque phase respecte exactement cet ordre :

1. fermeture de l’admission des callbacks et nouveaux enfants applicatifs ;
2. annulation du job applicatif ;
3. arrêt des captures, drop transfers, interactions et sessions IME ;
4. arrêt des effets de périphériques ;
5. fermeture ou abandon des requêtes de fenêtre ;
6. fermeture des fenêtres en ordre inverse de création ;
7. détachement des surfaces et subscriptions aux brokers ;
8. détachement des bridges natifs.

Une callback déjà admise peut terminer. Aucune nouvelle callback ne commence après la révocation de son owner.

### 3.7 Activation contractuelle

Une phase peut contenir plusieurs PRs, mais ses capabilities restent `Unsupported` tant que son comportement public n’est pas intégralement prouvé.

Un contrat passe à `active` uniquement dans une PR qui apporte simultanément :

- tous ses scénarios requis ;
- ses sentinelles ;
- les preuves O1/O2/O3 applicables ;
- son mapping `contract-evidence` ;
- la documentation de capability correspondante ;
- un gate CI obligatoire sans skip ni retry.

## 4. Phases d’implémentation

### Phase 0 — Fondations KFFI — terminée

#### Objectif

Fournir les primitives managées nécessaires aux callbacks AppKit durables sans ajouter de couche FFI dans Kadre.

#### Contenu

- bridge de méthodes Objective-C vers des lambdas Kotlin/JVM ;
- owner révocable avec `close()` idempotent ;
- garantie qu’aucune callback ne commence après fermeture ;
- observation managée de `NSNotificationCenter` ;
- signatures typées pour les enums et scalars AppKit ;
- primitives sûres de dispatch vers le main thread ;
- inventaire des bindings nécessaires aux phases 1 à 4.

#### Gate de sortie — atteint

- `KFFI-OBJC-001`, `KFFI-OBJC-002` et `KFFI-OBJC-003` sont fermés par [KFFI #35](https://github.com/Graphiks-org/kffi/pull/35), [Kextract #50](https://github.com/klang-toolkit/kextract/pull/50) et l'artifact publié `org.graphiks:kffi-objc:1.0.0-SNAPSHOT` ;
- KFFI couvre callback/close concurrent, exception de callback et réutilisation séquentielle ;
- le backend AppKit ne contient aucune primitive FFI locale ;
- aucune capability Kadre dépendante n’est encore activée par cette phase seule.

### Phase 1 — Broker, embedded et lifecycle — active

#### Objectif

Attacher plusieurs sessions Kadre à une boucle AppKit existante sans en prendre le contrôle.

#### Contenu

- `AppKitProcessBroker` reference-counted ;
- enregistrement et retrait explicites des hosts/sessions ;
- `attachKadreDesktop(Embedded(AppKitMainLoop))` ;
- traduction des notifications AppKit vers attachment, visibility et activation ;
- terminaison globale du host traduite en `HostDetached` ;
- fermeture d’une session sans arrêter `NSApplication` ;
- activation policy standalone sans modification de celle d’un host embedded.

#### Gate de sortie — atteinte

- `APK-002` est `active` ;
- deux sessions embedded partagent une boucle mais aucun ID, job, callback ou événement ;
- fermer une session ne ferme ni l’autre session ni la boucle native ;
- le teardown et la révocation des observers sont observés depuis AppKit ;
- standalone et embedded restent verts dans le même gate.

### Phase 2 — Fenêtres fondamentales — active

#### Objectif

Ouvrir, posséder et fermer une vraie fenêtre AppKit depuis `WindowManager.requestWindow`.

#### Contenu

- manager runtime réel et machine à états `WindowRequest` ;
- admission, cancellation, commit natif et handoff public ;
- création transactionnelle de `NSWindow`, `NSView`, delegate et callbacks ;
- fenêtre primaire et ordre stable des fenêtres ;
- fermeture native interceptée et réponse applicative ;
- armement de `stopWhenLastWindowClosed` ;
- `withDesktopHandle` et verrou de lifetime ;
- fermeture inverse pendant le teardown.

#### Gate de sortie — atteinte

- `APK-003` est `active` et son gate O3 traverse `KadreScope.windows`, `Window.events` et
  `Window.withDesktopHandle` avant de créer, rejeter une première fermeture native puis fermer
  une vraie `NSWindow` avec sa `NSView` via KFFI ;
- aucune fenêtre résiduelle après cancellation pré-commit ;
- state/outcome tardif autoritaire après commit ;
- primary et règle dernière fenêtre prouvés avec plusieurs fenêtres ;
- handle natif inaccessible après la fin de sa lease ;
- teardown des requêtes pending puis des fenêtres committées en ordre inverse avant révocation des delegates ;
- `requestWindow = OpenedHere`, l'interception de fermeture, `withDesktopHandle`, l'observation de surface et `requestRedraw()` sont activés ; les mutations de fenêtre, cursor, hit testing, input default behavior et l'input des phases suivantes restent explicitement `Unsupported`.

### Phase 3 — Surface observable et redraw — active ; sortie manuelle en attente

#### Objectif

Rendre la zone de contenu AppKit entièrement observable sans introduire de renderer.

#### Contenu

- métriques logiques et physiques atomiques ;
- scale factor, safe areas, visibilité, occlusion, focus et thème ;
- révisions et ordre snapshot/événement ;
- resize et changements de backing scale ;
- `requestRedraw()` coalescé ;
- cursor, hit testing et default input behavior restent explicitement `Unsupported` tant qu'un backend `apply` AppKit n'est pas prouvé ;
- détachement terminal et fermeture des flows.

#### Gate de sortie

- `APK-004` O3 est activé avec tous ses scénarios et sentinelles, sans skip ni retry ;
- `Window.surface` conserve son snapshot terminal après fermeture ;
- toute opération tardive retourne `Closed(Surface)` ;
- aucun événement ne précède le snapshot qu’il décrit ;
- redraw, resize, visibilité et focus sont provoqués depuis AppKit dans la vraie session publique ;
- sous la frontière O3 publique, le peer natif et le routage déterministe de backing scale et d'occlusion restent prouvés, tandis que leurs transitions matérielles/compositor effectives sont réservées au gate manuel versionné ;
- `SurfaceCapabilities.platformAccess` reste explicitement `Unsupported` sur Desktop conformément à la matrice.

`APK-004` est actif pour l'observation publique et le redraw. Son protocole
manuel AppKit-only et son harness externe sont versionnés dans
`backend/appkit/manual/`. Ils observent les phénomènes visuels et matériels
non réductibles à la CI, notamment les changements réels de backing scale
entre écrans et l'occlusion physique, sans réexécuter les preuves de fenêtres
fondamentales déjà couvertes par `APK-003`. La sortie complète de phase reste
conditionnée aux observations standard-scale et HiDPI consignées dans le cahier.

### Phase 4 — Clavier, pointeur et scroll — active ; complément manuel versionné

#### Objectif

Livrer l’input essentiel d’une application interactive macOS.

#### Contenu

- physical/logical keys, repeat et modifiers ;
- entrée, sortie, mouvement, position et boutons du pointeur ;
- scroll discret/précis, phases et momentum ;
- pointer capture explicitement hors scope et `Unsupported` ;
- reset atomique à la perte de focus ;
- backpressure et coalescing selon la policy ;
- mappers purs séparés de l’admission runtime.

La phase est détaillée par `APPKIT-PHASE-4-INPUT-DESIGN.md`. Les prérequis
`KFFI-OBJC-004` (first responder managé) et `KFFI-OBJC-005` (injection scroll
O3) sont fermés et publiés ; Kadre n'ajoute aucun workaround FFI. `INP-001` et
`APK-005` sont actifs : clavier et pointeur sont `Available` après installation
de l'observation native, tandis que les autres capacités input restent
explicitement `Unsupported`. Le catalogue public fermé demeure inchangé : phase
et momentum de scroll sont des frontières d’ordonnancement internes tant
qu’aucun champ public ne les expose.

#### Gate de sortie

- événements injectés par la file AppKit plutôt que par appel direct aux mappers ;
- aucune touche ni bouton ne reste actif après perte de focus ;
- ordre input/state et budgets prouvés ;
- le harness interactif `phase4InputHarness` consigne séparément le routing réel
  des périphériques et du responder, que l'injection synthétique sans fenêtre ne
  peut pas prouver en CI.

### Phase 5 — Fenêtres avancées et interactions transitoires

#### Objectif

Couvrir toutes les mutations top-level et leurs résultats asynchrones applicables à AppKit.

La première sous-tranche, définie par
`APPKIT-PHASE-5-WINDOW-GEOMETRY-DESIGN.md`, est achevée : AppKit prend en
charge `contentSize`, `minimumSize`, `maximumSize` et `resizable`, avec état
effectif, corrélation d'opération et policy de géométrie. `outerPosition`
reste reportée à la phase 9 : les coordonnées physiques publiques exigent
l'inventaire et le repère multi-écran réellement observables.

La sous-tranche titre, définie par
`APPKIT-PHASE-5-WINDOW-TITLE-DESIGN.md`, est achevée : AppKit prend en charge
`title` avec le même pipeline corrélé, un readback effectif et la policy de
livraison des événements de propriétés.

La sous-tranche chrome, définie par
`APPKIT-PHASE-5-WINDOW-CHROME-DESIGN.md`, est achevée : AppKit prend en
charge `decorations` et `systemButtons` avec canonisation de `Borderless`,
préservation des bits de style non possédés, readback des boutons natifs et
capabilities publiques. Les contrats `WIN-003` et `APK-008` sont actifs ;
le harness `phase5WindowChromeHarness` consigne séparément les quatre
observations visuelles, sans les faire passer artificiellement en CI.

La sous-tranche level, définie par
`APPKIT-PHASE-5-WINDOW-LEVEL-DESIGN.md`, est achevée : AppKit prend en charge
le niveau de z-order `Normal`, `Floating` et `Modal` avec conversion par les
bindings KFFI générés, readback natif et capability publique. Elle reste
indépendante de toute modalité AppKit et du fullscreen. Les contrats `WIN-004`
et `APK-009` sont actifs ; aucune vérification manuelle n'est requise, car le
contrat porte sur une valeur native lisible et entièrement testable en CI.

La sous-tranche suivante, définie par
`APPKIT-PHASE-5-WINDOW-FULLSCREEN-DESIGN.md`, réserve le fullscreen natif
`Borderless` corrélé aux notifications AppKit. `Window.apply` attend la
completion terminale et retourne `Applied` ou une failure corrélée, jamais un
`Accepted` intermédiaire. La sous-tranche garde `Exclusive` hors scope jusqu'à
la phase Display, refuse l'initialisation `Borderless` non corrélable, rejette
l'initialisation `Exclusive` par `RequestWindow`, et réserve `WIN-005` et
`APK-010` en statut `planned` jusqu'à l'activation publique et à ses preuves.

#### Contenu

- position, dimensions et contraintes ;
- resizable, fullscreen, décorations et boutons système ;
- level, transparence, blur, icône et content protection ;
- demande d’attention ;
- opérations différées corrélées par IDs ;
- system move/resize et autres actions exigeant l’autorité d’un événement natif ;
- capabilities conditionnées par version macOS et état courant.

#### Gate de sortie

- chaque champ de `WindowSpec` et `WindowUpdate` est supporté ou refusé explicitement ;
- aucun commit asynchrone n’est présenté comme immédiatement appliqué ;
- fullscreen et options process-wide sont restaurés au teardown ;
- chaque branche `Applied`, `PartiallyApplied`, `Accepted` lorsqu'elle est
  terminale, ou failure est couverte.

### Phase 6 — IME, touch et gestures

#### Objectif

Couvrir les entrées enrichies liées à la vue AppKit et à son focus.

#### Contenu

- bridge `NSTextInputClient` ;
- composition, commit, sélection et surrounding text ;
- cursor rectangle et commandes d’édition ;
- `NSTouch` lorsque disponible ;
- gestures AppKit reconnues nativement : magnify, rotate, swipe et pressure selon disponibilité.

#### Gate de sortie

- une seule session IME active par surface ;
- aucune composition ne survit à la perte de focus ou au teardown ;
- callbacks IME révoqués avant destruction de la vue ;
- touch et gestures restent distincts du pointer ;
- aucune gesture logicielle universelle n’est simulée.

### Phase 7 — Drag-and-drop

#### Objectif

Respecter la décision synchrone de `NSDraggingDestination` tout en exposant un transfert coroutine borné.

#### Contenu

- lecture de `NSPasteboard` et descriptors portables ;
- accept/reject dans la callback native ;
- `DropOffer`, claim unique et timeout ;
- `DropTransfer` et lecture par chunks ;
- items replayable et single-use ;
- ressources sandbox/security-scoped ;
- budgets de métadonnées, transferts et bytes ;
- fermeture de secours par la session.

#### Gate de sortie

- aucune offre partielle ni path non autorisé ;
- exactement un consumer gagne le transfert ;
- cancellation du lecteur sans fuite de ressource ;
- fermeture pendant un transfert actif prouvée ;
- claim timeout et dépassements de budget couverts.

### Phase 8 — Raw input et permissions

#### Objectif

Exposer l’input global uniquement lorsque la capability et la permission macOS le permettent.

#### Contenu

- capability dynamique et permission Input Monitoring ;
- coordination process-wide du prompt ;
- source globale via KFFI ;
- suspension lors de la révocation ;
- routage explicite vers les sessions autorisées ;
- API conservée sous son opt-in délicat.

#### Gate de sortie

- état et failure exacts sans permission ;
- capability publiée avant arrêt lors d’une révocation ;
- plusieurs waiters partagent une seule demande native ;
- aucune subscription globale ne survit à sa dernière session owner ;
- le raw input ne falsifie pas une dépendance au focus ordinaire.

### Phase 9 — Displays, pression mémoire et changements système

#### Objectif

Remplacer les managers et signaux `Unsupported` correspondants par des observations complètes et cohérentes.

#### Contenu

- inventaire via `NSScreen` et CoreGraphics ;
- display primaire, bounds, work area, scale et modes réellement connus ;
- connexion, retrait et modification ;
- changements de thème/contraste propagés aux surfaces ;
- pression mémoire lorsque macOS fournit une primitive fiable ;
- capabilities dynamiques lorsque l’information devient indisponible.

#### Gate de sortie

- aucun inventaire incomplet publié comme `Enumerated` ;
- retrait ordonné : handle terminal, nouvel inventaire, événement ;
- reconnexion avec un nouvel ID ;
- changement de scale coordonné avec les surfaces concernées ;
- aucun signal de pression mémoire synthétique.

### Phase 10 — Devices, gamepads et effets

#### Objectif

Couvrir `DeviceManager`, l’observation des gamepads et les effets disponibles sur macOS.

#### Contenu

- broker process-wide des périphériques ;
- inventaire observable des devices réellement connus ;
- framework GameController via KFFI ;
- connexion, déconnexion et snapshots ;
- routing conforme à `DevicePolicy` ;
- mapping standard et contrôles vendor-specific ;
- vibration/haptics selon capabilities ;
- ownership et arrêt des effets.

#### Gate de sortie

- aucun périphérique ni effet ne fuit entre sessions ;
- reconnexion après état terminal avec nouvelle identité ;
- effet explicitement refusé lorsqu’il n’est pas supporté ;
- teardown et arbitration d’ownership prouvés ;
- les scénarios matériels indisponibles sur le runner ne sont pas remplacés par un faux test O3.

### Phase 11 — Capture complète

#### Objectif

Couvrir permissions, sources, admission, streaming, frames et terminaison de capture.

#### Sous-tranches internes

1. control plane : permissions, capabilities, inventaire, refresh et picker ;
2. admission : targets `HostChoice`, `Source` et `Surface`, budgets et réservation ;
3. streaming : ScreenCaptureKit/CoreGraphics, sessions et configuration ;
4. delivery : frame lease, `copyPlanes`, backpressure, formats et timestamps ;
5. terminaison : source perdue, permission révoquée, collector annulé et teardown parent.

#### Contraintes

- aucune conversion implicite de format ou d’espace colorimétrique ;
- aucune mémoire native dans l’API publique ;
- frame valide uniquement pendant l’appel du collector ;
- source stale rejetée avant réservation ;
- distinctions explicites entre inventaire, host picker et targets ;
- runtime gates pour les APIs dépendantes de la version macOS.

#### Gate de sortie

- captures de sources screen/window, target `Surface` et host picker prouvés selon leurs capabilities ;
- configuration révisionnée avant livraison des frames concernées ;
- révocation, source perdue, cancellation et failure collector couvertes ;
- zéro frame ou ressource native après terminaison ;
- aucun format ou inventaire inventé pour masquer une absence native.

### Phase 12 — Interop, hardening et fermeture contractuelle

#### Objectif

Transformer les tranches fonctionnelles en backend AppKit complet, documenté et maintenable.

#### Contenu

- `capabilities/appkit.md` exhaustif ;
- consumers Kotlin et Java depuis les artifacts publiés ;
- samples standalone, embedded, input riche et capture ;
- isolation de classloaders et réutilisation séquentielle ;
- audits de jobs, owners, callbacks, observers et handles ;
- stress attach/detach/close/callback ;
- tests sur les versions macOS retenues par la matrice ;
- audit d’absence de FFI locale et de fuite `internal.*` ;
- mesure du gate et maintien du SLO défini par `TEST-STRATEGY.md` ;
- retrait des références historiques uniquement lorsqu’elles ne servent plus à l’audit du plumbing natif.

#### Gate de sortie

- chaque ligne AppKit de la matrice possède une décision documentée ;
- chaque contrat actif possède toutes ses preuves et sentinelles ;
- aucun skip, retry automatique, faux succès ou capability optimiste ;
- aucun callback ne commence après le teardown de son owner ;
- CI standalone et embedded exécutées dans leurs topologies réelles ;
- tous les artifacts et consumers applicables sont verts.

## 5. Milestones

Les milestones sont des points d’observation, pas des versions ni des promesses de release.

| Milestone | Phases terminées | Résultat |
|---|---:|---|
| A — AppKit interactif | 0 à 4 | standalone/embedded, fenêtres, surfaces et input essentiel |
| B — Interaction complète | 0 à 8 | fenêtres avancées, IME, touch, gestures, drop et raw input |
| C — Capabilities complètes | 0 à 11 | displays, devices, gamepads et capture inclus |
| D — Backend fermé | 0 à 12 | documentation, interop, ressources et matrice entièrement prouvées |

## 6. Dépendances et ajustabilité

L’ordre 0 → 1 → 2 → 3 → 4 est strict : il construit les callbacks, le host, la fenêtre, la surface puis l’input essentiel.

Après le milestone A :

- les phases 5 à 11 restent dans l’ordre recommandé par le risque ;
- une phase peut sortir temporairement du scope sans rendre une autre capability fausse ;
- une phase indépendante peut avancer si tous ses prérequis runtime/KFFI sont satisfaits ;
- la phase 12 reste obligatoirement la dernière ;
- aucune activation partielle n’est autorisée pour masquer une sous-tranche manquante.

La capture peut être découpée en plusieurs PRs internes, mais son contrat fonctionnel ne devient `active` qu’une fois le chemin promis intégralement utilisable.

## 7. Stratégie de plans et de PRs

Cette roadmap n’est pas un plan d’implémentation exécutable. Chaque phase reçoit son propre design puis son propre plan détaillé, afin de ne pas figer les signatures KFFI ou les structures internes des phases lointaines.

Le cycle ordinaire d’une phase est :

1. audit des contrats publics, des capabilities et des bindings ;
2. amendement préalable des spécifications uniquement si une ambiguïté ou contradiction est découverte ;
3. livraison KFFI séparée lorsque nécessaire ;
4. machines à états runtime et preuves O2 ;
5. peers/adapters AppKit et preuves O3 ;
6. activation des contrats, evidence et documentation de capability ;
7. audit d’ownership, publications, consumers et CI.

Une phase peut utiliser plusieurs PRs. Chaque PR :

- possède un périmètre revuable indépendamment ;
- conserve le build et tous les contrats déjà actifs verts ;
- laisse `Unsupported` toute capability encore incomplète ;
- ne mélange pas migration historique et changement contractuel caché ;
- ne traverse pas plusieurs phases non stabilisées dans une même stack.

## 8. Hors périmètre

Cette roadmap ne couvre pas :

- un renderer, une API graphique ou un système de widgets ;
- Android, UIKit, Web, Win32, X11 ou Wayland ;
- une couche FFI Kadre ;
- une compatibilité avec l’ancienne API inspirée de winit ;
- `integration:compose`, `integration:awt` ou `integration:javafx`, qui restent des projets optionnels séparés ;
- une identité persistante de ressources entre processus ou reconnexions ;
- une conversion implicite de pixels, formats de capture ou espaces colorimétriques.

GameController, ScreenCaptureKit, CoreGraphics et les permissions macOS entrent dans la roadmap lorsqu’ils servent une session AppKit, même s’ils ne sont pas techniquement définis par le framework AppKit lui-même.

## 9. Critères de fermeture de la roadmap

La roadmap est achevée lorsque :

1. chaque domaine public applicable à AppKit possède une implémentation ou un `Unsupported` documenté et justifié ;
2. standalone et embedded respectent le même runtime, les mêmes policies et le même modèle d’outcome ;
3. aucune session ne partage involontairement IDs, jobs, fenêtres, handles, events, collectors ou effets ;
4. chaque owner natif possède une fermeture idempotente et une place explicite dans le teardown ;
5. les callbacks et subscriptions sont révoqués avant libération de leur support natif ;
6. chaque contrat actif possède ses preuves O1/O2/O3 applicables et son evidence CI ;
7. aucun binding ou callback FFI local n’existe dans Kadre ;
8. l’API commune et Desktop reste conforme au catalogue fermé ;
9. les consumers Kotlin et Java résolvent les artifacts publiés sans type interne ;
10. la CI macOS respecte les contraintes d’intégrité et de durée de `TEST-STRATEGY.md`.
