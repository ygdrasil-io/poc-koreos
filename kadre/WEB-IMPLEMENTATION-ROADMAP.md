# Kadre — Roadmap d’implémentation complète Web JS/Wasm

**Statut :** roadmap architecturale approuvée ; chaque phase donnera lieu à un plan d’implémentation et à des PRs empilées (stacked PRs) séparés.
**Date :** 7 septembre 2026.
**Cibles :** navigateurs Web pour Kotlin/JS IR et Kotlin/Wasm-JS.
**Mode de livraison :** tranches verticales pilotées par le risque. Une capability ne devient publique et active qu’avec sa preuve navigateur correspondante.

Ce document organise l’implémentation du support Web complet de Kadre. Il ne remplace pas les spécifications normatives et ne réintroduit ni renderer, ni widgets, ni event loop publique. Kadre attache une session à un élément DOM existant ; l’application hôte conserve le document, le layout, les browsing contexts et le rendu.

Les autorités restent, par ordre de responsabilité :

- `DESIGN.md` pour les invariants sémantiques et l’API d’attachement Web ;
- `PUBLIC-API-CATALOG.md` pour la surface publique exacte ;
- `OPERATION-CONTRACTS.md` pour les failures, outcomes et frontières d’autorité ;
- `BACKEND-CAPABILITIES.md` pour les disponibilités et validations Web ;
- `INTEROP-EXPORTS.md` pour `@kadre/host`, TypeScript et les escape hatches ;
- `POLICY-PROFILES.md` pour les budgets et stratégies de delivery ;
- `PROJECT-ARCHITECTURE.md` pour les projets et dépendances ;
- `TEST-STRATEGY.md` pour les preuves navigateur et leurs gates CI ;
- `PLAN-SNAPSHOT.md` pour le périmètre fonctionnel validé.

En cas de contradiction, la spécification normative concernée est corrigée avant le code, le registre de contrats et l’activation de la capability.

## 1. Objectif et définition de la couverture complète

L’objectif est de rendre Kadre utilisable dans une application Web Kotlin/JS ou Kotlin/Wasm-JS sans lui faire posséder la page :

- attachement à un `HTMLElement` ou `HTMLCanvasElement` déjà créé ;
- lifecycle, ownership et teardown corrects pour les deux politiques d’attachement ;
- surface hôte, redraw piloté par le navigateur et input commun ;
- interactions nécessitant une transient user activation (activation utilisateur transitoire) ;
- sessions multiples et demande de nouvelle session dans un browsing context créé par le host ;
- text input, drag-and-drop, displays, périphériques, gamepads, capture et permissions lorsque les primitives Web permettent un contrat Kadre conforme ;
- interop Kotlin et TypeScript, diagnostics, policies, consumer builds et preuves navigateur.

La couverture complète ne signifie pas que chaque API commune est systématiquement disponible dans chaque navigateur. Pour toute capability publique applicable à Web, l’adapter prend une décision documentée et prouvée parmi :

1. `Supported`, éventuellement conditionnel à une capability navigateur, une permission, un secure context ou une activation utilisateur ;
2. `Unavailable` lorsque l’état observable peut évoluer sans mensonge ;
3. `Unsupported` stable lorsque le navigateur ne fournit pas de primitive permettant de respecter le contrat Kadre.

Une branche non implémentée ne retourne jamais un succès fictif, une liste vide présentée comme exhaustive, un no-op silencieux ou une promesse de popup/iframe. Le statut « complet » signifie que chaque surface applicable est soit réellement prouvée, soit explicitement et durablement non supportée.

## 2. État initial et frontière de responsabilité

La branche de départ contient les spécifications Web, les gates de preuves target-aware et les validateurs JS/Wasm Chromium réservés. Les contrats Web restent `planned` : aucun adapter Web, source set JS/Wasm, driver Playwright, bundle public ou preuve navigateur ne leur donne encore une activation effective.

`runtime` conserve les préoccupations portables :

- sessions, structured concurrency (concurrence structurée), admission et cancellation ;
- reducers sérialisés, IDs, révisions, `StateFlow` et `Flow` ;
- policies, budgets, outcomes et priorité des failures ;
- ownership logique, lifecycle public et ordre de teardown ;
- managers communs de surface, fenêtre, input, devices et capture.

`platform:web` traduit uniquement la réalité browser :

- attachement, détachement et ownership des éléments DOM ;
- callbacks DOM, `MutationObserver`, `ResizeObserver`, `requestAnimationFrame` et lifecycle de page ;
- appels Web/API conditionnels, permissions et transient user activation ;
- conversion vers les stimuli internes immuables déjà définis par le runtime ;
- bridges JS et Wasm distincts derrière le même noyau interne.

Il n’existe ni `backend:web`, ni bridge FFI, ni modification KFFI/Kextract dans cette roadmap. La target Web dépend des SDK DOM Kotlin et des APIs natives du navigateur ; elle ne contourne pas cette frontière par des externals publics copiés dans `foundation`.

## 3. Invariants globaux

### 3.1 Attachement et propriété du DOM

- Kadre attache exclusivement un élément fourni par le host ; il ne crée jamais de `<canvas>`, `<div>`, popup, iframe ou emplacement dans le DOM.
- L’élément attaché est la `primarySurface`, jamais une `Window`. `WindowManager.state.value.primary` reste `null` pour une session Web attachée.
- Un `HTMLElement` n’a qu’un owner Kadre vivant. Un second attach retourne `Busy(Host)` sans listener, changement de capability ni transfert d’ownership.
- Le registry d’ownership est local au browsing context et retire l’élément seulement après la terminaison effective de son owner.
- Aucun titre, attribut DOM, expando JavaScript ou global process-wide ne sert d’identifiant de session.

### 3.2 Deux façades SDK, un noyau sémantique

Les signatures utilisant `HTMLElement` existent distinctement en `jsMain` et `wasmJsMain`, avec les types SDK exacts (`org.w3c.dom.HTMLElement` et `web.html.HTMLElement`). Le code partagé ne publie aucun type DOM.

```text
API publique JS ou Wasm
        |
        v
façade platform:web target-specific
        |
        v
noyau Web interne sans type DOM public
        |
        +--> runtime commun Kadre
        |
        v
ports DOM JS / Wasm
        |
        v
navigateur et élément fournis par le host
```

Une divergence de glue interne ne permet aucune divergence de noms, nullabilité, outcomes, `.d.ts` ou comportement public entre JS et Wasm.

### 3.3 Lifecycle d’attachement

`StopWhenDetached` et `Manual` sont deux contrats différents, jamais deux niveaux de best effort :

- `StopWhenDetached` exige un élément initialement connecté. À la livraison d’un batch `MutationObserver`, un élément encore détaché ou transféré dans un autre document termine la session. Un reparenting fini dans le document initial conserve la session ; une réinsertion après terminaison exige une nouvelle session.
- `Manual` accepte l’élément déconnecté, reste `Attached + Background + Inactive` tant qu’il l’est et n’est arrêté que par une demande explicite. Il utilise `requestAnimationFrame` du browsing context origine pour retrouver une reconnexion tardive, puis réinstalle l’observer sur le `Document` ou le `ShadowRoot` pertinent. Tout transfert inter-document reste terminal.
- `visibilitychange`, `focus`/`blur` du browsing context et `focusin`/`focusout` de la surface alimentent `Foreground` et `Active`. Une surface détachée, un document background ou un autre host focalisé est toujours inactive.
- `pagehide`, y compris `persisted`, ferme immédiatement l’admission et termine la session. `pageshow` ne ressuscite jamais une session ; aucun teardown suspendu, persistence ou requête réseau n’est attendu pendant unload.

### 3.4 Flux entrant, commandes sortantes et callback safety

```text
événement DOM / callback navigateur
  -> copie minimale du payload
  -> stimulus interne immuable identifié par session/surface
  -> ingress runtime thread-safe
  -> reducer sérialisé
  -> snapshot StateFlow
  -> événement Flow associé
```

```text
appel public
  -> validation et capability
  -> admission runtime
  -> appel browser autorisé
  -> readback ou callback terminal
  -> reducer runtime
  -> state, outcome ou failure fermé
```

Une callback DOM ne suspend pas, n’appelle pas l’application directement, ne publie pas dans une surface publique et ne laisse pas traverser une exception Kotlin. Les ressources closeables, notamment transfer de drop et capture, suivent un handoff explicite plutôt qu’un `Flow` multicast.

### 3.5 Interactions et browsing contexts

- Toute action dépendant de la transient user activation passe par `InteractionContext`, consommé dans le handler synchrone du navigateur. Une opération suspendue arrivée trop tard retourne `InteractionRequired`.
- `InteractionAction.OpenWindow` reste `Unsupported` sur Web. Kadre ne crée aucun browsing context.
- `WebWindowProvider` est réservé à l’overload factory. Il retourne synchroniquement un élément que le host a déjà créé dans un browsing context distinct ; Kadre valide son `ownerDocument.defaultView`, crée une nouvelle session et retourne seulement `OpenedInNewSession`.
- L’overload direct est mono-session et ne peut pas recevoir de provider.

### 3.6 Interop et escape hatch

`HostSurface.withWebElement` est l’unique accès Kotlin direct à l’élément. Il est `@KadrePlatformApi` et `@DelicateKadreApi`, s’exécute sur le contexte host, n’est pas réentrant pour la même surface et borne la validité de l’élément au callback. Il permet, par exemple, qu’un consumer installe son renderer DOM/canvas ou ses propres overlays au-dessus de Kadre, sans que Kadre n’en prenne la responsabilité.

Le module `@kadre/host` expose la façade TypeScript promise, y compris la référence opaque `KadreApplicationFactoryRef`. JS IR et Wasm produisent le même `.d.ts` et passent le même consumer TypeScript. Aucun type DOM brut ou type interne Kadre ne fuit dans `foundation` ou dans un export TypeScript.

### 3.7 Teardown

Chaque capability Web respecte cet ordre :

1. fermer l’admission des callbacks et des nouveaux enfants applicatifs ;
2. désarmer interactions, observers, rAF, subscriptions browser et listeners DOM ;
3. annuler le job applicatif et les requêtes en attente ;
4. arrêter text input, drops, capture et effets de périphériques ;
5. fermer les sessions enfant créées par provider sans en prendre abusivement l’ownership ;
6. retirer l’owner DOM et les bridges target-specific best effort.

Une callback déjà admise peut finir son chemin interne ; aucune callback nouvelle ne commence après la révocation de l’owner. La fermeture d’une session demandeuse ne termine jamais une session déjà ouverte dans un autre browsing context.

### 3.8 Activation contractuelle et preuves

Une phase peut compter plusieurs PRs mais n’active aucun contrat Web incomplet. Toute activation apporte dans la même PR :

- l’entrée de registre, ses scénarios et sentinelles ;
- le mapping target-aware JS et Wasm ;
- les preuves O1/O2/O3 applicables, dont une exécution DOM réelle lorsque le comportement dépend du navigateur ;
- les JSON canoniques et les rapports JUnit corrélés ;
- le snapshot de capabilities et la documentation de disponibilité ;
- un gate obligatoire sans skip, retry automatique ni fallback masqué.

Une capability conditionnelle prouve, en PR, la branche réellement observable sur le runner. Lorsque les deux branches sont réalisables, l’ensemble nightly/release couvre au moins un chemin `Supported` et un chemin `Unsupported`. Une simple déclaration de capability ne remplace jamais ce scénario.

Le chemin de preuve browser est immuable :

```text
kadre/contracts/driver/web/build/contract-evidence/<target>/
  contract-evidence/browser/<engine>/<contractId>.json
  test-results/browser/<engine>/TEST-*.xml
```

Une preuve JSON seule n’est jamais recevable. En PR, Chromium est obligatoire pour JS et Wasm ; le nightly exécute Chromium, Firefox et WebKit pour chaque combinaison supportée.

## 4. Topologie Gradle et stratégie de livraison

La topologie réservée est matérialisée seulement avec le premier comportement utile et sa preuve :

```text
kadre/
  platform/web/          # surface publique Web et bridge DOM JS/Wasm
  contracts/driver/web/  # driver Playwright et production des preuves O3
  consumers/typescript/  # build autonome contre les artifacts publiés
```

`platform:web` dépend de `foundation` et `runtime`. Il n’y a pas de `backend:web` : Web est un host adapter, pas un provider desktop interchangeable. `contracts:driver:web` reste non publié ; `consumers/typescript` n’utilise jamais `project(...)` et résout les artifacts depuis un repository temporaire.

Les projets ne sont pas créés vides. La première PR qui les introduit porte aussi un attach DOM réel ou un driver qui produit une preuve utile. Les modifications de `settings.gradle.kts`, les source sets JS/Wasm et la publication ne sont ajoutés qu’au moment de cette première tranche verticale.

Chaque phase peut être une stack de PRs : une fondation runtime/bridge, les tests qui cassent, l’implémentation, puis l’activation du contrat et de sa gate. Une branche suivante ne suppose jamais une API KFFI/Kextract ou un binding natif : aucun de ces dépôts n’est dans le chemin critique Web.

## 5. Phases d’implémentation

### Phase 0 — Première verticale Web : build, attach réel et driver

#### Objectif

Introduire les targets JS et Wasm, la façade Web initiale et le driver navigateur sans créer de composant vide ni promettre prématurément une API complète.

#### Contenu

- matérialiser `platform:web`, ses source sets partagés et ses façades DOM JS/Wasm ;
- matérialiser `contracts:driver:web`, dépendant de `contracts:suite`, avec Playwright, un serveur local déterministe, watchdog des processus enfants et archivage de trace seulement en échec ;
- construire et charger un bundle Kotlin/JS puis Kotlin/Wasm-JS distinct, sans collision de répertoire ou d’artifact ;
- établir l’attach vertical par la façade Kadre publique sur un élément créé par la fixture, avec un parent scope réel et une primary surface sans `Window` ;
- connecter les validateurs déjà réservés au producer browser réel, sans activer un contrat dont tous les scénarios ne sont pas encore présents ;
- ajouter le consumer TypeScript minimal qui charge le package réellement publié dès que la façade exportée devient disponible.

#### Gate de sortie

- les deux bundles sont exécutés dans Chromium par le même driver ;
- aucun module n’est purement structurel : la fixture prouve un attach DOM par l’API publique et un teardown ;
- le driver stimule la façade publique et ne dépend ni d’un mapper, ni d’un owner, ni d’un attach interne ;
- les répertoires de preuve JS et Wasm sont distincts et le validateur refuse leur confusion ;
- aucune capability Web incomplète n’est encore déclarée `Supported` ou activée dans le registre.

### Phase 1 — Attachement public, ownership et lifecycle

#### Objectif

Livrer les deux overloads `HTMLElement.attachKadre` avec un lifecycle complet, multi-session et sans ambiguïté entre `StopWhenDetached` et `Manual`.

#### Contenu

- validation de `parentScope` (présence et activité du `Job`), de l’élément et de la politique demandée ;
- registry d’ownership unitaire par `HTMLElement`, retour `Busy(Host)` non intrusif et libération seulement après terminaison ;
- `StopWhenDetached` : validation initiale, observers `Document`/`ShadowRoot`, reparenting dans le document initial et terminaison inter-document ;
- `Manual` : attach initial déconnecté, lifecycle `Background + Inactive`, boucle de reconnexion rAF, réinstallation d’observer et `requestStop` explicite ;
- réduction déterministe de `visibilitychange`, focus de browsing context et focus de subtree ;
- terminaison immédiate `pagehide`, y compris bfcache, et cleanup best effort ;
- vérification explicite que primary surface n’entraîne ni fenêtre primaire, ni création DOM, ni `runApp` Web.

#### Gate de sortie

- `BCK-001` reste `planned` jusqu’à la phase 4 : son entrée de registre inclut les scénarios obligatoires de `WebWindowProvider`, qui ne peuvent pas être couverts honnêtement avant cette phase ;
- les scénarios d’attach/lifecycle sont toutefois écrits dès cette phase contre l’API publique, afin de devenir une partie des preuves `BCK-001` sans réécriture ni test interne lors de son activation ;
- les scénarios couvrent au minimum élément initialement déconnecté, detach durable, detach/reinsert, transfert inter-document, changement de ShadowRoot, sessions indépendantes, duplicate attach, focus concurrent et pagehide persisted ;
- chaque transition publie state avant event, respecte les policies de delivery et ne produit aucune callback après termination ;
- un cahier manuel browser est introduit ici pour compléter les cas que Playwright ne peut pas rendre fiables (bfcache réel, focus navigateur et Shadow DOM hôte). Il est informatif tant que la cible n’est pas publiquement promise, puis devient une condition de sortie de la capability concernée.

### Phase 2 — Surface hôte, redraw et interop explicite

#### Objectif

Rendre observable et exploitable la surface attachée sans introduire de renderer ni laisser fuir le DOM dans l’API commune.

#### Contenu

- alimenter les métriques atomiques de `HostSurface` depuis les primitives DOM appropriées (`ResizeObserver`, device scale et readbacks bornés) ;
- implémenter `requestRedraw()` avec coalescence et scheduling `requestAnimationFrame` gouverné par policy, sans boucle de rendu Kadre ;
- ordonner resize, redraw et terminaison ; annuler les rAF et observers à la frontière de teardown ;
- exposer `withWebElement` dans les deux façades avec verrou de lifetime, non-réentrance et frontière de cancellation conforme ;
- publier `KadreApplicationFactoryRef`, `asHostRef()` et le module `@kadre/host` uniquement avec la façade TypeScript exacte promise ;
- ajouter les consumer compile tests Kotlin JS/Wasm et le consumer TypeScript commun contre les artifacts réellement produits.

#### Gate de sortie

- une mutation de taille DOM et une demande de redraw sont observées par les deux bundles sans perte, double émission ou callback tardive ;
- `withWebElement` échoue proprement avant l’accès ou après detach, n’appelle pas son bloc après cancellation pré-callback et libère son lease avant propagation d’exception ;
- JS IR et Wasm produisent le même `.d.ts` et exécutent le même scenario TypeScript target-neutre ;
- aucune surface renderer, widget ou layout Kadre n’est créée par le test ou l’implémentation.

### Phase 3 — Input essentiel : clavier, pointeur et scroll

#### Objectif

Traduire les interactions DOM ordinaires vers les modèles input communs, avec state cohérent et sans capturer le comportement navigateur par défaut sans contrat explicite.

#### Contenu

- listeners target-specific de clavier, pointer, wheel, focus et sortie de surface ;
- mapping vers les types communs de key, modifiers, pointer, buttons et scroll ;
- réduction sérialisée de `SurfaceInput.state`, reset de focus et gestion de l’overflow suivant la policy ;
- support de pointer capture uniquement lorsqu’il respecte l’ownership de surface ;
- application de `SurfaceUpdate.inputDefaultBehavior` au bon point de dispatch, sans `preventDefault` global ou inconditionnel ;
- réconciliation des callbacks tardifs, de la perte de focus, des annulations pointer et du teardown.

#### Gate de sortie

- scénarios browser pour ordre state/event, modifiers, multi-pointer, cancel/lost capture, wheel discrete/précis, focus reset et input après terminaison ;
- le consumer ne peut pas observer un button/modifier bloqué après blur, detach ou fermeture ;
- le navigateur conserve son comportement par défaut tant que la policy ne demande pas explicitement son inhibition ;
- toute variante non normalisable entre moteurs est une capability conditionnelle ou `Unsupported`, pas un mapping approximatif caché.

### Phase 4 — Interactions synchrones et fenêtres dans un contexte hôte

#### Objectif

Gérer les opérations browser qui doivent partir d’un handler utilisateur et la création d’une session enfant dans un contexte que le host a déjà ouvert.

#### Contenu

- `InteractionContext`, `installInteractionHandler` et armement/consommation de token selon les actions Web effectivement réalisables ;
- fullscreen, pointer lock ou autres actions activables seulement si leur primitive et leur callback terminal permettent le contrat public ;
- refus explicite `InteractionRequired` après perte de transient activation et `Unsupported` pour `InteractionAction.OpenWindow` ;
- `WebWindowProvider` sur l’overload factory uniquement, avec DTO défensif et capture des exceptions callback ;
- validation `ownerDocument.defaultView`, distinct du contexte origine, validation de la nouvelle parent scope et des politiques d’attachement ;
- création de la nouvelle session et outcome `OpenedInNewSession`, sans popup, iframe ni domaine de lifecycle partagé avec le requester.

#### Gate de sortie

- `BCK-001` devient `active` dans cette phase uniquement, avec tous ses scénarios d’attach/lifecycle de phase 1 et ses scénarios `WebWindowProvider` livrés ici pour JS et Wasm Chromium ;
- les tests distinguent l’exécution synchrone depuis le handler de la demande suspendue tardive ;
- une action non supportée ne touche aucune API navigateur ; les erreurs callback deviennent la failure fermée promise ;
- provider absent, contexte identique, `defaultView` nul, scope absente/inactive, élément invalide ou déconnecté sont tous rejetés avec le code spécifié ;
- une fermeture du requester ne ferme pas la session enfant et aucun duplicate owner n’est possible entre contexts ;
- aucun test ne crée lui-même popup ou iframe pour faire passer la capability : le host de fixture le prépare avant l’appel Kadre.

### Phase 5 — Text input, IME, touch/gestures et drag-and-drop

#### Objectif

Étendre l’input sans dégrader le contrat de lifecycle ou laisser traverser des objets `DataTransfer`, `CompositionEvent` ou fichiers DOM bruts.

#### Contenu

- sessions de text input, composition/IME, sélection et actions de soumission vers les types communs ;
- touch, pen et gestures seulement pour les primitives dont la fidélité peut être déclarée honnêtement ;
- admission, claim et lecture des `DropOffer`/`DropTransfer`, avec snapshots copiés, ownership closeable et nettoyage terminal ;
- politiques de prévention par défaut, de delivery et d’overflow propres à chaque flux ;
- support conditionnel documenté pour les comportements dépendants du navigateur, du type de champ ou du support IME.

#### Gate de sortie

- composition start/update/end, cancellation, focus loss et teardown ne laissent aucune session text active ;
- un drop n’est lisible que par le transfer qui l’a réclamé, est fermé exactement une fois et ne survit pas à la session ;
- les scénarios ne confondent pas un échec de permission/activation navigateur avec une absence de capability Kadre ;
- les cas non automatisables de clavier natif/IME reçoivent une procédure manuelle ciblée, sans remplacer les invariants déterministes automatisés.

### Phase 6 — Displays, devices, gamepads et permissions

#### Objectif

Rendre les managers communs honnêtes face aux APIs navigateur hétérogènes, permissions, hot-plug et sécurité de contexte.

#### Contenu

- inventaire d’affichage et de device basé sur les primitives Web réellement disponibles ;
- si l’inventaire display complet est indisponible, publication obligatoire de `DisplayInventory.Enumerated` contenant l’unique display `HostViewport` plutôt qu’un inventaire vide ou `Unavailable` générique ;
- Gamepad API : découverte, connection/disconnection, snapshots, routing et effets uniquement lorsque le navigateur fournit une primitive compatible ;
- exposition de l’état de permission, du secure context et des préconditions d’activation sans permission prompt implicite ;
- subscriptions annulables, snapshots cohérents et cleanup des polling/rAF éventuels ;
- décision documentée pour raw input, haptics ou toute API navigateur sans équivalent fiable dans le contrat commun.

#### Gate de sortie

- toute capability dépendante d’un matériel ou d’un navigateur est prouvée sur un environnement contrôlé et, si nécessaire, dans le nightly plutôt que simulée dans le gate PR ;
- l’absence de matériel, de secure context ou d’autorisation produit l’état/outcome prévu, jamais un inventaire vide déclaré complet ;
- l’absence d’inventaire display complet produit exactement `Enumerated(primary = viewport, displays = listOf(viewport))` avec `DisplayType.HostViewport` ;
- hot-plug, révocation, déconnexion et fin de session libèrent leurs subscriptions et effets ;
- une capability impossible à standardiser est documentée `Unsupported` et reçoit un scénario de non-appel natif.

### Phase 7 — Capture, permissions et frames

#### Objectif

Intégrer les primitives de capture Web sans masquer leurs contraintes de sécurité, leurs consentements ni leurs coûts de transfert.

#### Contenu

- mapping des sources et permissions Web vers `CaptureManager` lorsque le navigateur permet réellement la capture promise ;
- lorsque le navigateur interdit l’inventaire préalable, `CaptureTarget.Source` est structurellement `Unsupported(CaptureOpen)` ; `HostChoice` avec `HostPickerOnly` et `Surface` conservent leurs chemins et capabilities séparés ;
- ouverture, état, arrêt et teardown des `CaptureSession` ;
- livraison bornée des frames, copies nécessaires des payloads et arrêt immédiat des tracks/streams ;
- distinction explicite entre capture de surface, fenêtre, écran ou caméra lorsque le navigateur ne les garantit pas sous la même sémantique ;
- `Unsupported` stable pour les modes ne disposant pas de primitive conforme ou ne permettant pas les garanties de permissions Kadre.

#### Gate de sortie

- aucune prompt de permission n’est déclenchée implicitement par énumération ou readback ;
- refus utilisateur, révocation, source perdue, fin de track et fermeture de session ont chacun un outcome fermé et des ressources libérées ;
- les preuves distinguent explicitement `Source` sans inventaire, refusé avant tout picker, de `HostChoice`/`HostPickerOnly` et de `Surface` lorsqu’ils sont disponibles ;
- les frames ne sont pas réémises après stop et ne laissent aucun objet DOM/stream accessible à une session terminée ;
- les capacités dépendant d’un vrai écran, d’une permission ou d’un navigateur particulier sont complétées par une preuve nightly et un cahier manuel, jamais par un skip en CI PR.

### Phase 8 — Fermeture Web : compatibilité, CI et exploitation

#### Objectif

Terminer la couverture Web avec les consumers publiés, les matrices navigateur et l’audit des absences explicites.

#### Contenu

- activer progressivement tous les contrats Web dont les scénarios et sentinelles sont réellement livrés ;
- passer les consumers Kotlin JS/Wasm et TypeScript sur les artifacts publiés depuis un repository temporaire ;
- vérifier stabilité des packages, nullabilité, `bigint`, unions discriminées et absence de fuites `internal` dans `.d.ts` ;
- stabiliser le script de test local navigateur, les traces en échec et le cahier manuel par capacité ;
- rendre Chromium JS/Wasm obligatoire en PR, puis Firefox/WebKit JS/Wasm obligatoire en nightly ;
- auditer chaque API du catalogue applicable à Web et documenter `Supported`, `Unavailable` ou `Unsupported` avec son contrat et sa preuve.

#### Gate de sortie

- aucun `contractId` Web actif n’est absent d’une target, d’un moteur obligatoire ou de son rapport JUnit corrélé ;
- le même consumer TypeScript est compilé et exécuté contre JS et Wasm ;
- les comportements conditionnels disposent d’un capability snapshot et d’un scénario de perte/révocation ;
- le cahier manuel couvre les frontières host-owned : navigation/bfcache, focus réel, activation utilisateur, popup préparé par host, permissions et matériel ;
- aucune API Web promise ne dépend d’un renderer Kadre, de `runApp`, d’un global de session, d’une création DOM implicite ou de KFFI.

## 6. Ordre de dépendance et jalons de revue

```text
Phase 0  build + driver + attach réel
   |
Phase 1  attach public + lifecycle + ownership
   |
Phase 2  surface/redraw + interop/consumers
   |
Phase 3  input essentiel
   |
Phase 4  interactions synchrones + provider de session
   |
Phase 5  IME/gestures/drop
   |
Phase 6  displays/devices/gamepads
   |
Phase 7  capture
   |
Phase 8  compatibilité, CI et fermeture
```

Les phases 3, 5, 6 et 7 peuvent contenir des sous-tranches indépendantes, mais aucune ne contourne les invariants établis par les phases précédentes. En particulier, une nouvelle source d’input, de device ou de capture ne crée pas son propre lifecycle, registry d’ownership ou scheduler parallèle.

Chaque revue vérifie prioritairement :

- la frontière Web host-owned et l’absence de création implicite de DOM/contexte ;
- l’absence de type DOM public dans le code partagé ;
- state avant event et exactitude des outcomes/capabilities ;
- cancellation avant admission, avant commit et après effet navigateur ;
- suppression des listeners, observers, rAF et streams au teardown ;
- preuve réelle JS et Wasm, sans skip ni écrasement d’artifacts ;
- cohérence entre catalogue, capability table, registre, TypeScript et consumer builds.

## 7. Hors périmètre et décisions explicitement différées

Cette roadmap ne décide pas :

- un renderer, un scene graph, des widgets, du layout ou un canvas géré par Kadre ;
- la création ou le placement de canvas, div, popup ou iframe ;
- une abstraction publique de boucle d’événements Web ou un équivalent de `runKadreApplication` ;
- la prise de contrôle de l’historique, de la navigation, du service worker ou de la persistence du host ;
- des polyfills silencieux transformant une primitive navigateur absente en pseudo-support ;
- une couche FFI, un changement KFFI ou Kextract ;
- une version, une date de livraison ou la composition d’une release.

Une nouvelle capability Web ne peut entrer dans le scope qu’après ajout ou clarification de son contrat dans les documents normatifs, puis placement dans une phase sans élargir abusivement la responsabilité de Kadre.

## 8. Gate de fermeture

- [ ] `platform:web` expose les mêmes contrats Kotlin en JS et Wasm avec leurs types SDK respectifs.
- [ ] `HTMLElement.attachKadre` respecte les deux politiques d’attachement, les sessions multiples et `pagehide`.
- [ ] Une session Web possède une primary surface, jamais une fausse top-level window.
- [ ] Toutes les primitives DOM restent host-owned ; Kadre ne crée ni DOM ni browsing context.
- [ ] `withWebElement` et `@kadre/host` sont bornés, publiés et compilés par des consumers réels.
- [ ] Input, text/IME, drops, displays, devices, gamepads et capture ont chacun une décision prouvée `Supported`/`Unavailable`/`Unsupported`.
- [ ] `WebWindowProvider` ne crée que des sessions dans des contexts préexistants et ne peut pas devenir une API popup.
- [ ] Chaque contrat Web actif possède les preuves JS et Wasm Chromium, JSON et JUnit corrélés.
- [ ] Le nightly exécute les moteurs supplémentaires déclarés supportés sans skip ni retry.
- [ ] Le cahier manuel cible les seules frontières non déterministes du navigateur et complète, sans les remplacer, les preuves automatisées.
- [ ] Aucun module Web vide, backend Web artificiel, renderer Kadre, global de session ou dépendance KFFI n’a été introduit.
