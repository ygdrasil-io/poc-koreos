# New Kadre — Snapshot de la première implémentation AppKit/JVM

**Statut :** design approuvé ; aucun code généré par ce document.  
**Date :** 23 août 2026.  
**Cible initiale :** macOS sur JVM 25, backend AppKit via KFFI.  
**Mode de livraison :** PRs empilées (*stacked PRs*), chacune testable et revue séparément.

Ce document capture les décisions prises après le merge du plan général. Il cadre la première implémentation sans remplacer les spécifications normatives de `DESIGN.md`, `PUBLIC-API-CATALOG.md`, `OPERATION-CONTRACTS.md`, `BACKEND-CAPABILITIES.md`, `INTEROP-EXPORTS.md`, `POLICY-PROFILES.md`, `PROJECT-ARCHITECTURE.md` et `TEST-STRATEGY.md`.

En cas de contradiction, les spécifications normatives font autorité. Une contradiction découverte pendant l'implémentation est corrigée dans la spécification avant d'être contournée dans le code.

## 1. Objectif du premier livrable

Le premier livrable est une tranche verticale réellement utilisable sur macOS/JVM :

```text
run/attach
  -> session structurée
  -> lifecycle
  -> requestWindow
  -> NSWindow + HostSurface
  -> clavier/pointeur
  -> fermeture
  -> SessionOutcome
```

Le code commun est conçu pour Kotlin Multiplatform, mais seule la target JVM est configurée, compilée, testée et supportée dans ce chantier. Les autres targets n'entrent pas prématurément dans le build.

Toute l'API commune et Desktop du catalogue doit compiler sur JVM. Les capacités AppKit hors de la tranche fonctionnelle existent sous leur forme publique, mais annoncent honnêtement `Unsupported` et retournent les failures ou outcomes normatifs correspondants. Aucun faux succès, no-op silencieux ou capability optimiste n'est admis.

## 2. Périmètre fonctionnel actif

La première tranche implémente réellement :

- `KadreApplication`, `KadreApplicationFactory`, `KadreScope` et `KadreSession` ;
- structured concurrency, cancellation, arrêt et teardown de session ;
- lifecycle Desktop à trois axes ;
- modes AppKit standalone et embedded ;
- `WindowManager.requestWindow`, fenêtre primaire et fermeture ;
- `NSWindow`, sa content `NSView` et la `HostSurface` associée ;
- titre, taille de contenu, visibilité, focus et scale factor ;
- interception de fermeture native selon le contrat public ;
- redraw de surface ;
- clavier, modifiers, pointeur, boutons, mouvement et scroll ;
- reset atomique de l'input lors de la perte de focus ;
- `withDesktopHandle` dans sa fenêtre de lifetime autorisée ;
- diagnostics, révisions, séquences et outcomes nécessaires à ces comportements.

Restent fonctionnellement hors de cette tranche :

- inventaire complet des displays et pression mémoire ;
- touch, gestures, drag-and-drop, IME et raw input ;
- inventaire de périphériques, gamepads et effets ;
- capture ;
- propriétés avancées de fenêtre non nécessaires au noyau ;
- Android, UIKit, Web, Win32, X11 et Wayland ;
- intégrations Compose, SwiftUI, AWT et JavaFX.

Ces domaines ne sont pas simulés par des succès. Leur disponibilité et leurs opérations restent explicitement non supportées jusqu'à activation d'un contrat et de ses preuves.

## 3. Architecture physique initiale

Seuls les composants possédant du code et des preuves réelles entrent dans le build :

```text
kadre
|-- foundation
|-- runtime
|-- platform
|   `-- desktop
|-- backend
|   `-- appkit
|-- test
|-- contracts
|   |-- registry
|   |-- model
|   |-- suite
|   `-- driver
|       |-- fake
|       `-- appkit
|-- consumers
|   |-- kotlin
|   `-- java
`-- samples
    `-- hello-window
```

Responsabilités :

- `kadre` est l'umbrella consommateur ;
- `foundation` contient le catalogue commun dans `commonMain`, avec une target JVM initiale ;
- `runtime` contient le moteur interne de session, sans SDK host ni KFFI ;
- `platform:desktop` contient l'API Desktop, la sélection de backend et les points d'entrée ;
- `backend:appkit` implémente le SPI interne et dépend de `kffi-objc` ;
- `test` contient le fake public quand ses promesses sont effectivement tenues ;
- `contracts:model` reste indépendant de la production ;
- `contracts:suite` observe uniquement l'API publique ;
- les drivers fake et AppKit fournissent les stimuli depuis la frontière opposée ;
- les consumers Kotlin et Java sont des builds autonomes résolvant des artifacts publiés dans un repository temporaire.

Les projets réservés aux autres plateformes ou intégrations ne sont pas créés tant qu'ils n'ont ni responsabilité réelle ni preuve.

## 4. Graphe et sélection du backend

```text
application
    |
    v
kadre
    |-- foundation
    `-- platform:desktop
              |
              v
           runtime
              ^
              |
backend:appkit ----> kffi-objc
```

Le backend AppKit est enregistré comme provider JVM et découvert paresseusement par `ServiceLoader`. `platform:desktop` ne dépend pas à la compilation du backend et ne charge pas KFFI avant la sélection.

Sur macOS :

- `DesktopBackend.Auto` choisit AppKit si son provider compatible est présent ;
- `DesktopBackend.AppKit` utilise le même provider ;
- un provider demandé mais absent retourne `Unsupported(HostAttach)` ;
- une combinaison OS/backend/integration incohérente retourne `InvalidRequest("options")` ;
- aucune bascule de backend n'est possible après admission.

## 5. Runtime et modèle de concurrence

Le runtime repose sur un `SessionRuntime` interne par session :

```text
parentScope
`-- sessionRootJob
    |-- applicationJob -> KadreApplication.run()
    |-- manager jobs
    |-- delivery jobs
    `-- teardown coordinator
```

Invariants :

1. `attach` valide synchroniquement le host, le scope parent et la policy, puis retourne une session `Starting`.
2. Une frontière d'admission sérialisée arbitre attach, detach, stop et failure concurrents.
3. Tous les managers et snapshots initiaux existent avant la transition vers `Running`.
4. `KadreApplication.run` reprend le dispatcher du `parentScope` ; AppKit est marshallé séparément vers son main thread.
5. Les callbacks natifs deviennent des stimuli immuables. Ils n'appellent jamais directement une application ou un collector.
6. Un processeur sérialisé par session ordonne mutations, révisions et `SessionSequence`.
7. Un snapshot est publié avant l'événement qui référence sa révision.
8. Les flux respectent les queues et budgets de la policy ; les transitions critiques de lifecycle et de teardown ne sont jamais droppées.
9. Le fake utilise exactement le même runtime avec horloge et scheduler virtuels injectés.
10. Aucun `GlobalScope`, session courante ou job détaché n'est autorisé.

Le teardown suit l'ordre normatif : fermeture de l'admission, annulation applicative, fermeture des ressources par domaine, requêtes de fenêtre, fenêtres en ordre inverse de création, surfaces, subscriptions puis bridges natifs. La session publie un seul `SessionOutcome` terminal.

## 6. Topologies AppKit

Le backend est séparé en quatre responsabilités :

```text
AppKitBackendProvider
`-- AppKitProcessBroker
    `-- AppKitHostSession
        `-- AppKitWindowPeer
```

`AppKitProcessBroker` encapsule uniquement les contraintes process-wide de `NSApplication`. Il est reference-counted, thread-safe et ne possède aucune notion de session courante. Chaque notification, fenêtre et callback est routé vers un owner explicite.

### 6.1 Standalone

- `runKadreApplication` exige le main thread JVM et JDK 25 ;
- un seul owner standalone AppKit est admis dans le processus ;
- le second reçoit `Busy(Host)` ;
- le runner initialise `NSApplication`, attache la session puis entre dans la main loop ;
- la terminaison de la session arrête et réveille la boucle sans employer `terminate:` comme mécanisme ordinaire ;
- après création de la session, le runner retourne exclusivement son `SessionOutcome`.

Une session standalone commence `Background + Inactive` tant qu'elle reste headless. Après création de fenêtres, visibilité et activation suivent leur agrégat. `stopWhenLastWindowClosed` suit exactement la règle d'armement de la spécification.

### 6.2 Embedded

**État actuel :** `APK-002` est actif pour l’attach, le lifecycle et le teardown embedded. `APK-003` active les fenêtres fondamentales dans les sessions AppKit standalone et embedded. `APK-004` active les snapshots de surface observables et `requestRedraw()` coalescé. Le gate O3 traverse l'API publique jusqu'à une vraie `NSWindow`/`NSView` KFFI, y compris resize, redraw, handle borné et interception Reject puis Accept. Les propriétés de fenêtre, cursor, hit testing, input default behavior et l'input restent explicitement `Unsupported` jusqu’à leurs preuves dédiées.

- `attachKadreDesktop(Embedded(AppKitMainLoop))` exige le main thread et une boucle AppKit existante ;
- l'attach ne remplace pas le delegate de l'application ;
- il ne lance, ne stoppe et ne termine pas `NSApplication` ;
- plusieurs sessions embedded sont autorisées ;
- `KadreSession.close()` détache uniquement la session concernée ;
- la terminaison réelle du host AppKit produit `HostDetached`.

Une session embedded sans fenêtre commence `Foreground + Active` tant que son intégration reste attachée et non suspendue.

## 7. Frontière fenêtre et input

**État Phases 2–3 :** `WindowManager.requestWindow` admet `OpenedHere`, publie `primary` et l'ordre stable des fenêtres, puis route les fermetures natives et programmatiques jusqu'au terminal. `stopWhenLastWindowClosed` s'arme seulement après la première fenêtre committée. `Window.withDesktopHandle` expose ses adresses AppKit uniquement dans un callback synchrone exécuté sur le main thread et protégé par une lease de lifetime. `Window.surface` publie les métriques et événements AppKit ordonnés, conserve son snapshot terminal et admet un seul redraw natif pour une rafale. Les autres mutations de fenêtre, cursor, hit testing, input default behavior et l'input restent non activés et retournent leurs failures `Unsupported` explicites.

**Activation Phase 3 :** `APK-004` est `active`. Ses scénarios et sentinelles
sont reliés aux tests publics et au test AppKit O3 process-owning. Le protocole
et le harness de vérification manuelle AppKit-only sont dans
`backend/appkit/manual/` ; la sortie manuelle standard-scale + HiDPI reste un
gate distinct qui ne doit pas être inféré des preuves automatisées.

Une `WindowRequest` admise marshal sa création vers le main thread. Le commit natif intervient seulement après création cohérente de `NSWindow`, de la content `NSView`, du delegate et des callbacks. Avant le handoff public, Kadre reste owner et ferme toute ressource en cas de cancellation ou failure.

Chaque `AppKitWindowPeer` possède :

- son `NSWindow` et sa `NSView` ;
- son delegate ;
- ses observers et callback tokens ;
- son verrou de lifetime pour `withDesktopHandle` ;
- son routage explicite vers une session et une fenêtre Kadre.

Le code AppKit traduit les coordonnées Cocoa vers l'espace logique Kadre, maintient l'ordre state/event et neutralise l'état input lors de la perte de focus. Les fonctionnalités non actives sont absentes des capabilities et refusées par les outcomes normatifs.

## 8. Réutilisation de l'ancien backend

La nouvelle implémentation peut adapter :

- les appels KFFI déjà validés ;
- les mappers clavier et pointeur ;
- les transactions de création et libération de `NSWindow`/`NSView` ;
- les delegates et tokens de callbacks ;
- les helpers CFRunLoop.

Elle ne reprend pas :

- `ApplicationHandler`, `ActiveEventLoop` ou `ControlFlow` ;
- les registres ou handlers implicites ;
- l'ancien ownership ou son ordre de teardown ;
- l'API publique et les conventions héritées de winit ;
- un adapter de compatibilité enveloppant l'ancien backend.

La réutilisation est donc limitée au plumbing natif prouvé. Le runtime, le SPI et les contrats publics sont nouveaux.

## 9. Frontière KFFI

KFFI possède exclusivement les bindings natifs. Kadre n'ajoute ni wrapper FFM local, ni générateur, ni couche FFI.

Tout binding manquant est ajouté au registre `kadre/KFFI-REQUIREMENTS.md` avec :

- framework, classe, selector ou symbole ;
- signature et conventions d'ownership attendues ;
- thread, callback et lifetime concernés ;
- fonctionnalité Kadre bloquée ;
- priorité ;
- scénario d'acceptation ;
- lien vers l'issue ou la PR KFFI lorsqu'il existe.

Un appel Objective-C générique déjà fourni par KFFI peut être utilisé temporairement. Le besoin de binding typé reste néanmoins enregistré jusqu'à sa résolution dans KFFI.

## 10. Stratégie de preuve

### 10.1 Runtime commun — O2

`contracts:model` contient une machine à états indépendante. Les scénarios exécutés contre le fake couvrent notamment :

- admission attach/detach/stop ;
- lifecycle et structured concurrency ;
- cancellation et priorité des outcomes ;
- ordre state/event ;
- requête, cancellation et fermeture de fenêtre ;
- teardown et isolation de sessions ;
- branches `Unsupported` ;
- interleavings aux frontières admission, commit et handoff.

Le fake fournit uniquement les stimuli. Il ne calcule jamais l'expected.

### 10.2 AppKit réel — O3

Le gate PR utilise un runner macOS standard avec session graphique :

- JVM démarrée avec `-XstartOnFirstThread` et `--enable-native-access=ALL-UNNAMED` ;
- rejet vérifié hors main thread ;
- modes standalone et embedded exercés ;
- vraie `NSWindow` créée ;
- événements clavier/pointeur injectés via la file AppKit ;
- focus, resize et fermeture déclenchés depuis AppKit ;
- deux sessions embedded testées sans fuite ;
- teardown observé depuis la frontière native ;
- capabilities et résultats `Unsupported` vérifiés.

Les scénarios embedded partagent une boucle contrôlée. Les scénarios standalone qui possèdent la boucle tournent dans des JVM isolées. Aucun sleep arbitraire, retry automatique, test ignoré ou golden image n'est admis.

### 10.3 Consumers

Des builds autonomes Kotlin et Java résolvent les artifacts depuis un repository temporaire. Ils prouvent les imports, overloads, trailing lambdas, opt-ins et l'absence de fuite d'un type `org.graphiks.kadre.internal.*`.

### 10.4 Activation progressive

Le registre contractuel distingue :

- `planned` : contrat spécifié mais non promis par le livrable courant ;
- `active` : preuve obligatoire et bloquante ;
- `retired` : contrat supprimé avec justification.

Un contrat `planned` ne peut jamais correspondre à une capability `Supported`. Son opération reste explicitement non supportée, ou son artifact n'est pas publié.

Ce statut constitue un amendement préalable nécessaire à `TEST-STRATEGY.md`. Il ne crée aucune notion de version.

Le gate PR exige 100 % des scénarios `active`, zéro skip/retry, tous les consumer tests applicables, toutes les sentinelles actives tuées et un p95 inférieur ou égal à dix minutes.

`FakeCapabilities.All` ne devient une promesse publiable que lorsque tous ses domaines sont effectivement implémentés et prouvés. Le module peut être construit auparavant, mais ce profil ne doit pas être livré comme surface consommable incomplète.

## 11. Stratégie de PRs empilées

Chaque PR dépend de la précédente, reste revue séparément et laisse son propre périmètre vert. Les branches exactes seront fixées par le plan d'implémentation.

| PR | Contenu | Preuve de sortie |
|---|---|---|
| 1 — build et registre | topologie initiale, conventions, statuts contractuels, JDK 25 | configuration et checks structurels |
| 2 — foundation | catalogue commun/Desktop compilable, invariants et policies | ABI + consumers Kotlin/Java |
| 3 — runtime | session kernel, lifecycle, teardown, managers unsupported, fake minimal | scénarios O2 actifs |
| 4 — Desktop host | façade Desktop, provider discovery et sélection déterministe | tests de sélection et failures |
| 5 — AppKit host | process broker, main thread et standalone headless ; l'embedded lifecycle est actif grâce à KFFI-OBJC-001/003, fenêtres et input restent en Phase 2+ | tests O2/O3 standalone et lifecycle embedded |
| 6 — window/surface | requête, NSWindow/NSView, state, redraw, close et primary | tests O2/O3 fenêtre |
| 7 — input | clavier, modifiers, pointeur, scroll et focus reset | tests O2/O3 input |
| 8 — interop et livraison | handle Desktop, sample, consumers finaux, audit KFFI/ressources | gate complet du livrable |

Une PR ne mélange pas migration de l'ancien code et changement contractuel caché. Tout ajustement normatif découvert est isolé et revu avant son implémentation dépendante.

## 12. Critères de sortie du premier livrable

Le chantier est terminé lorsque :

1. le sample standalone ouvre une fenêtre, observe clavier/pointeur et retourne le bon `SessionOutcome` ;
2. un host AppKit existant attache et ferme une session sans arrêter sa boucle ;
3. deux sessions embedded n'échangent ni fenêtres, IDs, événements, handles ni jobs ;
4. toute l'API commune et Desktop compile sur JVM ;
5. chaque capacité hors tranche est explicitement non supportée ;
6. chaque contrat `active` possède une preuve O2 ou O3 ;
7. aucun binding FFM local n'existe dans Kadre ;
8. chaque manque KFFI est documenté ;
9. les callbacks et ressources AppKit sont révoqués pendant le teardown ;
10. le gate macOS respecte l'objectif p95 de dix minutes.

## 13. Décisions fermées

- Cible initiale JVM/macOS uniquement.
- JDK 25 minimum.
- API commune et Desktop complète à la compilation.
- Capabilities hors tranche explicitement non supportées.
- Standalone et embedded AppKit dans le même livrable.
- Architecture risk-first en tranche verticale.
- Réutilisation sélective du plumbing natif historique.
- Aucun wrapper de compatibilité avec l'ancienne API.
- Aucun binding local ; besoins documentés pour KFFI.
- Tests AppKit réels bloquants sur runner macOS graphique.
- Livraison par PRs empilées.

Le prochain document à produire après revue de ce snapshot est le plan d'implémentation TDD détaillé, PR par PR et tâche par tâche.
