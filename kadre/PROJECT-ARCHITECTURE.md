# New Kadre — Architecture normative des projets

**Statut :** architecture de projet fermée ; aucun sous-projet ni build script généré par cette spécification.  
**Portée :** topologie Gradle/KMP, responsabilités, dépendances, publications, source sets, tests et intégrations.  
**Projet principal actuel :** `kadre`, destiné à porter ultérieurement le nom `kadre`.  
**Autorités associées :** `DESIGN.md` pour la sémantique, `PUBLIC-API-CATALOG.md` pour les déclarations publiques et `TEST-STRATEGY.md` pour les preuves.

Cette architecture ne fixe ni version, ni ordre d’implémentation, ni composition d’une livraison. Un nom réservé ci-dessous n’autorise pas la création d’un sous-projet vide : un composant n’entre physiquement dans le build que lorsqu’il possède une responsabilité réelle, du code et les preuves associées.

La coexistence avec les anciens projets, leur suppression, les modifications transitoires de `settings.gradle.kts` et la mécanique du futur renommage sont hors périmètre. La seule contrainte de nommage anticipée est qu’aucun enfant de `kadre` ne reprend `kadre` dans son propre nom.

## 1. Principes

1. `kadre` est le projet KMP principal et l’unique dépendance requise pour un usage standard.
2. Les contrats communs, le runtime, les hosts SDK et les backends desktop ont des frontières physiques distinctes.
3. Les adapters standards sont agrégés automatiquement par la bonne variante KMP ; le consumer ne choisit pas un artifact bas niveau pour démarrer.
4. Les frameworks tiers restent dans des intégrations optionnelles.
5. Les domaines fonctionnels — fenêtres, input, gamepads ou capture — sont des packages cohésifs, pas des artifacts artificiellement indépendants.
6. KFFI possède intégralement les bindings natifs ; Kadre ne possède aucune couche FFI.
7. L’infrastructure normative de test reste distincte de l’artifact public destiné aux tests applicatifs.
8. Les artifacts internes peuvent être publiés pour la résolution transitive, sans devenir des APIs supportées directement.
9. Les composants d’un même build sont alignés exactement ; aucun mélange de révisions internes n’est supporté.

## 2. Arborescence logique réservée

```text
kadre/
├── build.gradle.kts
├── foundation/
├── runtime/
├── platform/
│   ├── android/
│   ├── uikit/
│   ├── web/
│   └── desktop/
├── backend/
│   ├── appkit/
│   ├── win32/
│   ├── x11/
│   └── wayland/
├── test/
├── integration/
│   ├── compose/
│   ├── swiftui/
│   ├── awt/
│   └── javafx/
├── contracts/
│   ├── registry/
│   ├── model/
│   ├── suite/
│   ├── validator/
│   └── driver/
│       ├── fake/
│       ├── android/
│       ├── uikit/
│       ├── web/
│       ├── appkit/
│       ├── win32/
│       ├── x11/
│       └── wayland/
├── consumers/
│   ├── kotlin/
│   ├── java/
│   ├── swift/
│   └── typescript/
├── samples/
└── benchmarks/
```

`platform`, `backend`, `integration`, `contracts`, `samples` et `benchmarks` sont des namespaces d’organisation, sans artifact propre. Seuls leurs enfants contenant une implémentation effective deviennent des composants Gradle. `registry` est un répertoire de données normatives, pas nécessairement un sous-projet. `consumers` contient des builds autonomes, jamais des enfants du build principal.

## 3. Catalogue des composants

| Projet | Rôle | Publication | Dépendances Kadre autorisées |
|---|---|---|---|
| `kadre` | umbrella KMP et point d’entrée de dépendance | publique principale | `foundation`, puis `platform:*` selon la target |
| `foundation` | contrats, types, outcomes, policies et Host SPI publics | transitive contractuelle | aucune |
| `runtime` | sessions, coroutines, ownership, flows, budgets et SPI d’implémentation | transitive interne | `foundation` |
| `platform:android` | attach `Activity`/`View` et traduction du lifecycle Android | transitive contractuelle | `foundation`, `runtime` |
| `platform:uikit` | attach `UIWindowScene`/`UIViewController` et scènes UIKit | transitive contractuelle | `foundation`, `runtime` |
| `platform:web` | attach DOM partagé JS/Wasm et traduction browser | transitive contractuelle | `foundation`, `runtime` |
| `platform:desktop` | hosts desktop, boucle embedded/standalone et sélection de backend | transitive contractuelle | `foundation`, `runtime`, backends en runtime |
| `backend:appkit` | provider desktop AppKit | transitive interne | `runtime`, KFFI AppKit/ObjC |
| `backend:win32` | provider desktop Win32 | transitive interne | `runtime`, KFFI Win32 |
| `backend:x11` | provider desktop X11 | transitive interne | `runtime`, KFFI X11 |
| `backend:wayland` | provider desktop Wayland | transitive interne | `runtime`, KFFI Wayland |
| `test` | fake host, horloge et contrôleurs virtuels publics | publique optionnelle | `foundation`, `runtime` |
| `integration:*` | glue de lifecycle pour un framework tiers | publique optionnelle | `kadre`, framework concerné |
| `contracts:*` | oracles, scénarios, drivers et validation CI | non publiée | selon la section 9 |
| `samples:*` | applications consommatrices réelles | non publiée | `kadre` ou une intégration |
| `benchmarks:*` | mesures ciblées, sans verdict fonctionnel | non publiée | composant mesuré |

`kadre` peut être un umbrella mince : les classes publiques sont fournies transitivement par `foundation` et les composants de plateforme. Il reste le seul artifact principal documenté et évite le cycle impossible où un adapter dépendrait du projet qui doit lui-même l’agréger.

## 4. Graphe de dépendances

```text
application
    |
    v
kadre ------------------------------+
    |                                   |
    +--> foundation                     +--> platform:android
    |        ^                          +--> platform:uikit
    |        |                          +--> platform:web
    |     runtime <---------------------+--> platform:desktop
    |        ^                                   |
    |        |                                   +-- runtimeOnly --> backend:appkit --> KFFI
    |        |                                   +-- runtimeOnly --> backend:win32  --> KFFI
    |        |                                   +-- runtimeOnly --> backend:x11    --> KFFI
    |        |                                   +-- runtimeOnly --> backend:wayland --> KFFI
    |        |
    +--------+
```

Le graphe respecte les invariants suivants :

- aucune dépendance ne remonte d’un composant inférieur vers `kadre` ;
- `foundation` ne dépend d’aucun autre projet de l’arborescence ;
- `runtime` ne connaît ni SDK host, ni integration tierce, ni KFFI ;
- les modules `platform:*` ne se dépendent jamais mutuellement ;
- les backends desktop n’importent pas `platform:desktop` : ils implémentent un SPI JVM défini par `runtime` ;
- `platform:desktop` découvre paresseusement les providers backend et ne charge KFFI qu’après sélection ;
- aucune intégration ne contourne le point d’attachement de sa plateforme.

## 5. Variantes KMP et source sets

| Projet | Targets/source sets prévus |
|---|---|
| `kadre` | metadata commune et variantes correspondant aux composants agrégés |
| `foundation` | `commonMain` principalement, sans type SDK |
| `runtime` | `commonMain`, avec spécialisations Android, iOS, JS, Wasm et JVM seulement si le runtime l’exige |
| `platform:android` | Android |
| `platform:uikit` | iOS Kotlin/Native |
| `platform:web` | source set partagé Web, puis `jsMain` et `wasmJsMain` pour les types DOM distincts |
| `platform:desktop` | JVM desktop pour macOS, Windows et Linux |
| `backend:*` | JVM |
| `test` | les mêmes familles de targets que `foundation` |
| `integration:compose` | uniquement les targets réellement implémentées par cette intégration |
| `integration:swiftui` | iOS et wrapper Swift associé |
| `integration:awt` | JVM |
| `integration:javafx` | JVM |

Cette table ferme la topologie, pas le calendrier. Une target ou une intégration réservée n’est ni créée ni publiée avant de posséder une implémentation utile.

## 6. Publications et visibilité

### 6.1 Dépendances documentées aux consommateurs

```text
org.graphiks.kadre:kadre
org.graphiks.kadre:test
org.graphiks.kadre:compose
org.graphiks.kadre:swiftui
org.graphiks.kadre:awt
org.graphiks.kadre:javafx
```

Seul `kadre` est requis pour les hosts SDK standards. `test` et les quatre intégrations sont des choix explicites.

### 6.2 Composants transitifs contractuels

```text
org.graphiks.kadre:foundation
org.graphiks.kadre:android
org.graphiks.kadre:uikit
org.graphiks.kadre:web
org.graphiks.kadre:desktop
```

Leur ABI documentée appartient au contrat Kadre, même si la documentation d’installation ne recommande pas de les déclarer directement.

### 6.3 Composants transitifs internes

```text
org.graphiks.kadre.internal:runtime
org.graphiks.kadre.internal:appkit
org.graphiks.kadre.internal:win32
org.graphiks.kadre.internal:x11
org.graphiks.kadre.internal:wayland
```

Kotlin ne permet pas à une déclaration `internal` de servir de SPI entre artifacts indépendants. Les symboles de liaison strictement nécessaires sont donc techniquement `public` dans `org.graphiks.kadre.internal.*`, sans être une API consommateur :

- ils sont absents du catalogue et de la documentation publics ;
- aucun type interne ne peut apparaître dans une signature contractuelle ;
- ils n’offrent aucune compatibilité source ou binaire entre builds différents ;
- leurs artifacts utilisent exactement la même révision que l’umbrella ;
- leur visibilité explicite est revue pour empêcher toute déclaration publique accidentelle supplémentaire.

`explicitApi()` s’applique à `foundation`, aux surfaces contractuelles de `platform:*`, à `test` et aux intégrations publiées. Les dumps ABI des artifacts internes servent au diagnostic de liaison, pas à promettre leur stabilité.

Les artifact IDs sont les noms terminaux `foundation`, `android`, `runtime` ou `compose`, jamais une concaténation redondante du chemin Gradle. Aucun enfant ne porte `kadre` dans son nom.

## 7. Ownership des packages et granularité

`foundation` possède les packages communs :

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

Les autres surfaces publiques sont possédées exactement par :

| Projet | Package public |
|---|---|
| `platform:android` | `org.graphiks.kadre.platform.android` |
| `platform:uikit` | `org.graphiks.kadre.platform.uikit` |
| `platform:web` | `org.graphiks.kadre.platform.web` |
| `platform:desktop` | `org.graphiks.kadre.platform.desktop` |
| `test` | `org.graphiks.kadre.test` |
| `integration:compose` | `org.graphiks.kadre.integration.compose` |
| `integration:awt` | `org.graphiks.kadre.integration.awt` |
| `integration:javafx` | `org.graphiks.kadre.integration.javafx` |
| `integration:swiftui` | façade Kotlin/Swift dédiée de l’intégration |

Les domaines `coroutines`, `capture`, `window`, `input`, `gamepad` et `devices` ne deviennent pas des sous-projets. Ils partagent la session et le même arbre d’ownership ; les séparer créerait une fragmentation sans frontière de publication utile. Il n’existe notamment aucun nouvel enfant `core`, `coroutines`, `capture` ou `gamepad`.

Un nouveau sous-projet n’est justifié que par une ou plusieurs des frontières fermées suivantes : target différente, SDK ou dépendance tierce isolable, publication distincte, backend interchangeable ou driver de preuve indépendant.

## 8. Frontière KFFI

KFFI possède exclusivement :

- la génération des bindings ;
- les headers, descriptions de protocoles et inputs de génération ;
- les symboles et wrappers natifs bruts ;
- les tests intrinsèques de correction de ces bindings ;
- leur publication.

`kadre` ne contient donc aucun projet `ffi`, aucun binding copié, aucun générateur et aucun input de génération KFFI. Un backend dépend directement des artifacts KFFI dont il a besoin et limite leur usage à son implémentation.

Le SPI entre `runtime` et `backend:*` n’utilise aucun type KFFI. La CI Kadre prouve l’adaptation entre un backend et le contrat Kadre ; elle ne duplique pas les tests internes de KFFI.

## 9. Architecture de test

### 9.1 Artifact consommateur `test`

`test` expose uniquement le fake host, l’horloge virtuelle, les périphériques virtuels et les contrôleurs documentés par `PUBLIC-API-CATALOG.md`. Il dépend de `foundation` et de la liaison minimale de `runtime`, jamais des adapters natifs. Il ne contient ni registre normatif, ni driver CI, ni oracle privé.

### 9.2 Infrastructure normative interne

| Composant | Dépendances et interdictions |
|---|---|
| `contracts:registry` | données `contractId/scenarioId/target`, sans code de production |
| `contracts:model` | modèle indépendant ; aucune dépendance vers `runtime`, `platform:*` ou `backend:*` |
| `contracts:suite` | API publique, `test` et modèle ; aucune inspection interne |
| `contracts:validator` | registre et preuves `contract-evidence/<contractId>.json` |
| `contracts:driver:fake` | suite et controllers publics de `test` |
| `contracts:driver:<target>` | suite, API host/SDK de la target et stimulus externe |

Les tests internes d’un backend complètent le diagnostic mais ne remplacent jamais le driver black-box `O3` défini dans `TEST-STRATEGY.md`.

### 9.3 Consumers externes

Les dossiers `consumers/kotlin`, `java`, `swift` et `typescript` sont des builds autonomes. Ils ne reçoivent aucune dépendance `project(...)` et résolvent les artifacts produits depuis un repository temporaire, un framework/XCFramework ou un package généré. Ils valident ainsi le packaging réel en plus des signatures.

Les imports positifs couvrent seulement les coordonnées et packages documentés. Les contrôles négatifs interdisent l’ancienne API et toute fuite d’un type `org.graphiks.kadre.internal.*` dans une signature publique ; ils ne prétendent pas rendre techniquement inimportables les symboles de liaison d’un artifact transitif.

## 10. Intégrations optionnelles

Les intégrations adaptent un framework tiers à un host Kadre existant. Elles ne définissent ni capability supplémentaire, ni renderer, ni widget, ni layout.

- `compose` fournit uniquement la glue de lifecycle Compose ; sur JVM il peut utiliser `awt` comme dépendance d’implémentation target-specific.
- `swiftui` fournit le `UIViewControllerRepresentable` et les sorties Kotlin/Native plus Swift Package/XCFramework nécessaires ; SwiftUI conserve le layout et la scène.
- `awt` relie un host desktop à l’Event Dispatch Thread et aux objets AWT existants.
- `javafx` relie un host desktop au JavaFX Application Thread et aux objets JavaFX existants.

Aucun type de ces frameworks ne fuit dans `foundation`. Une intégration s’attache toujours via `platform:*` et ferme la session avec le lifecycle de son host.

La réservation d’un nom ou d’une coordonnée ne constitue pas une promesse de disponibilité. Avant de matérialiser une intégration, sa surface exacte doit être ajoutée à `PUBLIC-API-CATALOG.md` et aux contrats interop concernés.

## 11. Flux d’exécution et erreurs

```text
application
    | API publique
    v
platform:<target>
    | commande host normalisée
    v
runtime
    | session, admission, commit, ownership et publication
    v
foundation : StateFlow / Flow / outcome public
```

Un événement suit le chemin SDK/OS → `platform:*` ou `backend:*` → commande interne normalisée → `runtime` → snapshot puis événement public. Une opération applicative suit le chemin inverse après validation et admission par `runtime`.

Sur Desktop, une erreur KFFI/OS devient d’abord une `BackendFailure` interne. `platform:desktop` ajoute le contexte fonctionnel, puis `runtime` produit uniquement la `KadreFailure` ou l’outcome fermé autorisé par `OPERATION-CONTRACTS.md`. Aucun type ni exception KFFI ne traverse cette frontière.

La cancellation et l’ownership restent gouvernés par `runtime`. Un backend signale seulement les jalons internes nécessaires — non admis, admis, committé ou transféré — et ne réinterprète pas le contrat coroutine.

La sélection desktop est paresseuse. Un registre process-wide peut connaître les providers disponibles, mais ne possède aucune session courante et ne charge pas un backend ou KFFI avant sa sélection.

## 12. Hors périmètre de cette architecture

Ce document ne décide pas :

- l’ordre d’implémentation ;
- le contenu d’une version ou d’une livraison ;
- le moment où un module réservé entre ou sort du scope ;
- la coexistence avec les anciens projets ;
- le processus de suppression ou de renommage ;
- les aliases ou coordonnées temporaires de migration ;
- la création d’une infrastructure Gradle destinée uniquement à contrôler les dépendances entre générations.

Ces décisions exigent un plan séparé. Elles ne modifient pas les responsabilités et dépendances internes fixées ici.

## 13. Gate de fermeture

- [x] `kadre` est l’artifact principal sans enfant au nom redondant.
- [x] Le cycle umbrella/adapters est supprimé par `foundation`.
- [x] Runtime, plateformes et backends possèdent des responsabilités distinctes.
- [x] Les hosts standards restent accessibles avec une seule dépendance principale.
- [x] Les intégrations tierces sont optionnelles et ne contaminent pas l’API commune.
- [x] Aucun domaine fonctionnel cohésif n’est artificiellement extrait en artifact.
- [x] Aucune couche FFI ne subsiste dans Kadre ; KFFI possède les bindings.
- [x] Les publications transitives internes et contractuelles sont distinguées.
- [x] Les tests consommateurs, la contract suite et l’artifact `test` sont séparés.
- [x] Les targets Web partagent un projet tout en conservant leurs types DOM propres.
- [x] Desktop sépare orchestration host et providers AppKit/Win32/X11/Wayland.
- [x] L’architecture ne fige ni version, ni ordre, ni mécanique de migration.
