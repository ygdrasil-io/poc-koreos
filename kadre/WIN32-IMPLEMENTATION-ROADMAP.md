# Kadre — Roadmap d’implémentation complète Win32/JVM

**Statut :** proposition de roadmap architecturale ; les designs et plans détaillés restent séparés par phase. Aucune phase Win32 n’est déclarée livrée par ce document.
**Date :** 5 octobre 2026.
**Base examinée :** `ac261e00`, nouveau Kadre après la phase 5 Web.
**Cible :** Windows desktop sur JVM 25, backend Win32 via KFFI.
**Mode de livraison :** tranches verticales pilotées par le risque, chacune accompagnée de preuves et découpable en plusieurs PRs.

Ce document organise la création de `backend:win32` à partir du runtime actuel. Le premier jalon est une application standalone (autonome) avec fenêtres, surfaces et input. La couverture complète ajoute les domaines avancés et les topologies embedded (embarquées) effectivement intégrées. Kadre reste une bibliothèque d’hébergement et d’interaction : le renderer, les widgets et la boucle publique de l’ancienne API restent hors périmètre.

Les autorités restent :

- [DESIGN.md](DESIGN.md) pour les invariants sémantiques ;
- [PUBLIC-API-CATALOG.md](PUBLIC-API-CATALOG.md) pour l’API publique fermée ;
- [OPERATION-CONTRACTS.md](OPERATION-CONTRACTS.md) pour les failures, outcomes et frontières d’autorité ;
- [BACKEND-CAPABILITIES.md](BACKEND-CAPABILITIES.md) pour les garanties Desktop et les disponibilités fonctionnelles ;
- [PROJECT-ARCHITECTURE.md](PROJECT-ARCHITECTURE.md) pour les composants et dépendances ;
- [POLICY-PROFILES.md](POLICY-PROFILES.md) pour les budgets et stratégies de delivery ;
- [TEST-STRATEGY.md](TEST-STRATEGY.md) pour les preuves, sentinelles et gates CI ;
- [INTEROP-EXPORTS.md](INTEROP-EXPORTS.md) pour les exports et accès natifs bornés ;
- [KFFI-REQUIREMENTS.md](KFFI-REQUIREMENTS.md) pour les gaps de bindings constatés ;
- [MIGRATION-AUDIT.md](MIGRATION-AUDIT.md) pour la frontière avec l’ancien backend.

Une contradiction découverte pendant une phase est résolue dans la spécification concernée avant le code et l’activation du contrat dépendant. Cette roadmap ne constitue pas une autorisation implicite d’étendre les enums ou le Host SPI publics.

## 1. Résultat attendu et base disponible

### 1.1 Définition de la couverture

Chaque domaine public applicable à Win32 doit aboutir à une décision documentée : support prouvé, indisponibilité dynamique représentée par le state prévu, ou `Unsupported` justifié par une absence de primitive conforme. Une fonctionnalité simplement inachevée reste un travail ouvert ; elle ne devient pas une exclusion permanente pour fermer artificiellement la roadmap.

Les garanties structurelles Desktop restent obligatoires : cinq managers présents avant `Running`, `KadrePolicies.Default` accepté, fenêtres `OpenedHere`, surfaces via `Window.surface` et accès natif par `withDesktopHandle`. Une session peut rester headless (sans fenêtre), avec `primarySurface == null` et aucune fenêtre primaire.

La couverture inclut lifecycle, fenêtres, surfaces, interactions, clavier, pointeur, touch, gestures, IME, drop, raw input, displays, devices, gamepads, effets, capture, diagnostics et interop. La disponibilité précise reste propre à l’OS, à la topologie du host et au matériel observé.

### 1.2 État constaté dans le dépôt

| Élément | État au départ | Conséquence |
|---|---|---|
| Runtime commun et Desktop | Sessions, fenêtres, input, displays, devices et capture existent déjà | Réutiliser les machines à états et leurs ports ; adapter seulement les hypothèses propres à AppKit qui bloquent Win32 |
| Sélection Desktop | `DesktopBackend.Win32` et la sélection Windows de `Auto` existent | Ajouter un provider effectif, sans nouvelle façade publique |
| Interop fenêtre | `DesktopNativeWindowHandle.Win32(hwnd)` existe publiquement | Ajouter sa représentation interne et son mapping : `RuntimeDesktopNativeWindowHandle` ne contient actuellement qu’AppKit |
| Graphe Gradle | `backend:win32` est réservé dans l’architecture, absent de `settings.gradle.kts` | Introduire le module avec sa première responsabilité réelle et ses preuves |
| KFFI | L’alias `libs.kffi.win32` existe en `1.0.0-SNAPSHOT` | Auditer l’artifact réellement résolu ; cet alias ne prouve ni les callbacks managés ni les bridges COM nécessaires |
| Tests et CI | La stratégie réserve `windows-contracts`, sans workflow Win32 actuel | Ajouter driver, mappings, génération de preuves et exécution Windows dès la première verticale |
| Ancien backend | `../kadre-old/kadre-win32/` contient les anciens appels Win32 | Référence pour l’audit natif, sans transplantation de son runtime ou de sa FFI |

Les contrats `WIN-*` déjà présents désignent les fenêtres du runtime commun, pas le backend Windows. Les nouveaux contrats natifs devront utiliser un espace distinct, proposé ici comme `W32-*`, à enregistrer avec leurs premières phases. Aucun ID ni statut du registre n’est modifié par cette roadmap.

### 1.3 Matrice initiale proposée

La première qualification vise Windows 11 x64 et JVM 25. Il s’agit d’un choix de périmètre proposé, pas d’un support déjà acquis. La phase 0 fixe le build Windows exact, l’image CI, le Windows SDK utilisé par KFFI et la révision KFFI résolue. Kotlin et Gradle restent alignés sur le dépôt ; cette roadmap n’introduit pas de nouvelle toolchain.

Windows ARM64, Windows 10 et Windows Server ne sont pas déduits du mot « Win32 ». Leur support exige des exécutions dédiées et une décision de matrice. Un runner Windows Server peut apporter une preuve native utile sans prouver à lui seul le support client Windows 11. Wine, un service Windows et une session dépourvue de bureau interactif ne remplacent pas le host de référence.

## 2. Invariants propres au backend

### 2.1 Runtime, providers et bindings

`runtime` possède admission, cancellation, budgets, IDs opaques, révisions, reducers sérialisés, flows, outcomes et ordre de teardown. `backend:win32` possède la traduction Windows, les owners natifs, le marshalling vers le thread propriétaire et l’observation des capabilities. Les types KFFI ne franchissent pas cette frontière.

Le provider se limite à `isAvailable`, `supportedIntegrations`, `attach` et `run`. Sa découverte par `ServiceLoader` ne charge aucune DLL Windows sur macOS/Linux et ne crée aucune fenêtre, aucun thread UI, ni initialisation COM. La sélection est définitive après admission ; une erreur Win32 ne déclenche aucun changement de backend.

KFFI possède toute la FFI : structures ABI, symboles, chaînes natives, callbacks, vtables COM, références WinRT et mémoire étrangère. Kadre n’ajoute ni `Linker`, ni upcall/downcall Panama, ni layout manuel, ni JNA/JNI de secours. Si les bindings générés ne suffisent pas à exprimer un owner sûr, l’adapter managé est livré dans KFFI avant son consommateur Kadre.

### 2.2 Thread UI, messages et réentrance

Le runner public conserve le contrat de `DESIGN.md` : appel bloquant depuis le main thread du processus. Les fenêtres et leurs opérations appartiennent au thread UI qui les crée. Le design de phase 1 doit rendre cette précondition vérifiable sans prendre arbitrairement le premier thread appelant pour le main thread.

La message loop (boucle de messages) traite distinctement les trois résultats de `GetMessageW` : message disponible, `WM_QUIT`, erreur `-1`. Le teardown doit encore disposer de son thread UI pour fermer les ressources avant la sortie finale de la boucle. [Microsoft — GetMessage](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getmessage).

Le réveil des commandes utilise un endpoint natif appartenant au host, avec queue bornée et readiness explicite. Un message privé vers une fenêtre de contrôle est la direction proposée : `PostThreadMessageW` seul ne garantit pas la livraison pendant une boucle modale. La progression pendant déplacement, redimensionnement, menus et callbacks COM doit être prouvée. [Microsoft — PostThreadMessageW](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-postthreadmessagew).

Le chemin ordinaire est :

```text
message ou callback Windows
  -> copie bornée ou transfert explicite d’ownership
  -> stimulus immuable associé à une session et à un owner vivant
  -> reducer runtime sérialisé
  -> snapshot public
  -> événement associé
```

Une `WndProc` ne suspend pas et aucune exception Kotlin ne traverse la frontière native. Les seuls appels applicatifs synchrones sont les handlers explicitement prévus par les contrats d’interaction et de drop ; ils passent par leur garde runtime, avec résultat immédiat et capture des exceptions. Les messages que Kadre ne traite pas suivent le comportement natif par défaut.

La création, destruction et certaines commandes peuvent réentrer dans la `WndProc`. Les associations owner/`HWND` existent dès les premiers messages de création et survivent jusqu’à la destruction native effective. Une adresse `HWND` réutilisée ne réactive jamais un ancien `WindowId` ou une callback tardive.

### 2.3 DPI et coordonnées

La stratégie proposée utilise Per Monitor V2 lorsque le contexte effectif le permet. Kadre ne modifie pas le DPI awareness global d’un processus hôte déjà initialisé. Un changement temporaire du contexte du thread est borné et restauré, y compris après erreur ; un contexte incompatible est déclaré selon les capabilities concernées. [Microsoft — DPI awareness du processus](https://learn.microsoft.com/en-us/windows/win32/hidpi/setting-the-default-dpi-awareness-for-a-process), [SetThreadDpiAwarenessContext](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setthreaddpiawarenesscontext).

Chaque lecture indique son espace : coordonnées client logiques, taille framebuffer en pixels, ou bureau virtuel dans le contrat physique Desktop. Aucun scale unique n’est appliqué à tout le bureau. Les positions négatives, la virtualisation DPI, les bordures non-client et les changements d’écran sont traités explicitement. `WM_DPICHANGED` conduit à un snapshot cohérent relu après application native, avant les événements dépendants.

### 2.4 Ownership partagé et COM

Les registries de fenêtres restent internes et associent des owners explicites ; aucun singleton ne conserve une application ou session « courante ». Un broker ne devient process-wide (partagé par le processus) que lorsqu’une ressource Windows l’impose. Chaque session garde ses propres IDs, budgets, collectors et outcomes.

Les appels COM sont confinés à leur apartment (contexte de concurrence COM), ou passent par un marshalling fourni par KFFI. Pour OLE drop, un thread STA avec `OleInitialize` et une pompe de messages active est nécessaire ; un thread déjà MTA n’est pas converti silencieusement. Chaque initialisation réussie, y compris `S_FALSE`, est équilibrée par la libération correspondante. [Microsoft — OleInitialize](https://learn.microsoft.com/en-us/windows/win32/api/ole2/nf-ole2-oleinitialize), [RegisterDragDrop](https://learn.microsoft.com/en-us/windows/win32/api/ole2/nf-ole2-registerdragdrop).

### 2.5 Teardown et publications

L’ordre normatif reste : fermer l’admission des callbacks et enfants applicatifs ; annuler le job applicatif ; arrêter captures, transferts, interactions et IME ; arrêter les effets ; résoudre ou abandonner les requêtes de fenêtre ; fermer les fenêtres dans l’ordre inverse de création ; détacher surfaces et subscriptions ; fermer les bridges natifs.

L’admission applicative révoquée n’autorise pas à libérer prématurément la `WndProc` encore utilisée par `DestroyWindow`. KFFI conserve les callbacks natifs indispensables jusqu’à leur quiescence (fin des appels en cours), sans accepter de nouveau travail applicatif. Le endpoint de contrôle et la pompe disparaissent après les ressources qu’ils doivent fermer.

Un state effectif précède son événement. Une capability retirée est publiée avant l’événement de perte et les diagnostics associés. Une cancellation après commit ne fabrique aucun rollback ; l’outcome ou le state tardif demeure l’autorité.

## 3. Topologie et fichiers concernés

```text
kadre -> platform:desktop -> runtime
                  |
                  +-- runtimeOnly -> backend:win32 -> KFFI Win32
                                          |
                                          +-- provider et host UI
                                          +-- peers fenêtre/surface/input
                                          +-- ports displays/devices/capture
                                          +-- owners IME/drop/raw input
```

Les unités suivantes sont proposées ; leurs signatures seront fixées par le design de leur phase, sans devenir des APIs publiques :

| Emplacement | Responsabilité |
|---|---|
| `backend/win32/build.gradle.kts` | Variante JVM 25, dépendance KFFI, publication interne, suites Windows |
| `backend/win32/src/jvmMain/kotlin/org/graphiks/kadre/internal/win32/` | `Win32BackendProvider`, host, pump, peers et adapters KFFI séparés par domaine |
| `backend/win32/src/jvmMain/resources/META-INF/services/org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider` | Découverte du provider |
| `backend/win32/src/jvmTest/` | Tests des adapters avec ports contrôlés et preuves natives Windows identifiées séparément |
| `backend/win32/contracts/evidence.tsv` | Mapping des scénarios et sentinelles natifs vers les rapports exécutés |
| `backend/win32/manual/` | Harnesses et cahiers matériels reproductibles |
| `contracts/driver/win32/` | Driver externe O3 ; composant Gradle seulement lorsqu’il possède du code effectif |
| `capabilities/win32.md` | Registre exhaustif des garanties, minima, gates et limites effectivement prouvés |

Les modifications transverses se limitent aux besoins de chaque livraison :

- `../settings.gradle.kts`, `build.gradle.kts` et `platform/desktop/build.gradle.kts` pour inclusion, `runtimeOnly`, publications et consumers ;
- `runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeSessionComponents.kt` et les ports existants pour brancher les adapters ;
- `runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt` et `platform/desktop/src/jvmMain/kotlin/org/graphiks/kadre/platform/desktop/DesktopWindowHandle.kt` pour le handle interne Win32 et son mapping public ;
- `contracts/registry/contracts.tsv`, `contracts/validator/build.gradle.kts` et les validateurs pour sélectionner les preuves Win32 sans confondre les backends JVM ;
- `../.github/workflows/kadre-win32-contracts.yml` pour le futur gate Windows ;
- `consumers/kotlin/`, `consumers/java/` et les samples pour la résolution depuis les artifacts publiés.

Le plan de chaque phase précisera les nouveaux fichiers et les ports à modifier. Aucun nouveau projet `window`, `input`, `capture` ou `ffi` n’est introduit.

## 4. Dépendances KFFI à qualifier

L’ancien backend utilise notamment des arenas, des callbacks et des appels FFM locaux. Ces mécanismes ne respectent pas la frontière actuelle. Les noms d’API suivants servent à cadrer l’audit ; leur présence dans Windows ou dans l’ancien code ne prouve pas une API KFFI managée utilisable.

| Domaine | Besoin de bridge KFFI | Première phase dépendante |
|---|---|---:|
| Messages et fenêtres | `WNDPROC` managée, classe enregistrée, `CreateWindowExW`, destruction, queue, réveil, erreurs Win32 copiées immédiatement | 0–2 |
| Métriques et input | DPI, rectangles, paint, clavier, souris, cursor/capture, payloads de messages copiés dans des valeurs sûres | 3–5 |
| Texte et contacts | IMM/TSF selon couverture, callbacks et buffers UTF-16, pointer/touch/gesture et fermeture des ressources empruntées | 6 |
| OLE drop | `IDropTarget`, `IDataObject`, `IStream`, formats et médias avec références, apartments et libérations gérés | 7 |
| Raw Input | Enregistrement, readback de propriété, payloads `WM_INPUT`, notifications et retrait idempotent | 8 |
| Système | Moniteurs, modes, notifications display/appearance/mémoire et restauration des modes | 9 |
| Périphériques | Inventaire HID, source gamepad choisie et effets réellement exposés | 10 |
| Capture | WinRT/COM Windows Graphics Capture, interop HWND/monitor, async/picker, D3D et frames closeables | 11 |

Pour chaque gap réellement rencontré : documenter le symbole ou owner manquant dans `KFFI-REQUIREMENTS.md`, sa garantie de lifetime, la phase bloquée et le test de réception ; livrer Kextract/KFFI séparément si nécessaire ; consommer ensuite une révision publiée identifiable. Une publication Maven locale peut aider au développement, mais ne constitue pas une dépendance reproductible pour activer un contrat en CI.

## 5. Phases d’implémentation

### Phase 0 — Première frontière native et preuve Windows

**Objectif :** prouver la chaîne Kotlin/JVM → KFFI → queue Windows → callback managée avant de construire le host public.

**Contenu :**

- [ ] Fixer la matrice initiale, auditer l’artifact KFFI réellement résolu et qualifier le bridge `WNDPROC` révocable.
- [ ] Créer `backend:win32` avec une fenêtre de contrôle interne, un message de test et une fermeture réelle ; elle ne devient pas une `Window` publique.
- [ ] Introduire le driver Windows et les premiers contrats `W32-*`, scénarios, sentinelles et mappings sans activer de capability applicative prématurément.
- [ ] Brancher le job `windows-contracts`, les rapports JUnit et la validation des preuves avec un discriminant d’adapter `win32-jvm`.
- [ ] Séparer tests JVM portables et tests natifs obligatoires ; l’absence de Windows ne devient jamais un test natif « réussi ».

**Gate de sortie :** un message injecté à la frontière Windows produit une observation vérifiée, le callback est révoqué avant libération, la fermeture est idempotente et la suppression volontaire du dispatch fait échouer la sentinelle. Le job échoue si son bureau ou ses prérequis natifs sont absents. Le module compile sans charger de DLL Windows sur les autres OS.

### Phase 1 — Provider et standalone headless

**Objectif :** exécuter une application Kadre sur une vraie pompe Windows avec un lifecycle et un outcome conformes.

**Contenu :**

- [ ] Implémenter `Win32BackendProvider` via le SPI existant ; agréger sa publication par `platform:desktop`.
- [ ] Supporter `Auto`/`Win32` sur Windows, refuser les combinaisons incompatibles et conserver une découverte sans effet natif.
- [ ] Installer le dispatcher UI, le endpoint de commandes, la readiness et la boucle avant toute admission nécessitant leur disponibilité.
- [ ] Respecter le prérequis main thread, l’exclusivité du host standalone admis et sa réutilisation séquentielle après fermeture.
- [ ] Alimenter `SessionRuntime` et les cinq managers ; conserver initialement les domaines non livrés dans leur état non supporté exact.
- [ ] Couvrir arrêt explicite depuis un autre thread, cancellation, failure applicative et failure native ; réserver l’arrêt de la pompe à son owner.

**Gate de sortie :** une session reste headless sans arrêt implicite ; son arrêt réveille la boucle ; deux exécutions successives ne partagent ni jobs ni ressources ; les échecs avant admission et les outcomes après admission respectent les contrats. `GetMessageW == -1` est prouvé par un port contrôlé sans être présenté comme une preuve OS. L’embedded n’est pas encore annoncé dans `supportedIntegrations`.

### Phase 2 — Fenêtres, fermeture et handle emprunté

**Objectif :** ouvrir et fermer plusieurs fenêtres top-level dans la même session.

**Contenu :**

- [ ] Adapter les ports du `RuntimeWindowManager` et de `RuntimeSessionComponentsFactory`, avec un peer propriétaire par fenêtre.
- [ ] Implémenter `requestWindow`/`OpenedHere`, budgets, fenêtre primaire et annulation avant/après création native.
- [ ] Traduire `WM_CLOSE` vers la décision de fermeture, puis les messages de destruction vers les transitions terminales ; distinguer refus applicatif et destruction forcée.
- [ ] Détruire toute fenêtre créée mais non remise au runtime lorsqu’une admission échoue ; conserver les routes natives nécessaires pendant les callbacks de création/destruction.
- [ ] Ajouter `RuntimeDesktopNativeWindowHandle.Win32` et son mapping vers le type public déjà existant, uniquement sous le lease (emprunt borné) de `withDesktopHandle`.
- [ ] Appliquer la règle de dernière fenêtre : armement après la première fenêtre admise, fermeture des requêtes encore pending, aucune confusion entre fenêtre publique et fenêtre de contrôle.

**Gate de sortie :** `Closing` → surface détachée → `Closed` → retrait du manager est observé dans l’ordre ; l’état primaire suit l’ordre d’admission ; fermer une fenêtre secondaire n’arrête pas la pompe ; un handle fermé ne peut plus être emprunté ; la réutilisation d’une adresse native ne livre aucun événement à l’ancien owner.

### Phase 3 — Surface, redraw et DPI

**Objectif :** fournir des métriques cohérentes et un redraw request (demande de rafraîchissement) utilisable par un renderer externe.

**Contenu :**

- [ ] Relier chaque fenêtre à une `HostSurface`, avec taille client, taille physique, scale, visibilité, focus et occlusion seulement lorsqu’ils sont réellement observables.
- [ ] Traiter `WM_SIZE`, changements de position et `WM_DPICHANGED` en relisant le résultat effectif avant publication.
- [ ] Implémenter invalidation et `WM_PAINT`, avec validation de la région native via KFFI et coalescing borné des demandes.
- [ ] Maintenir le contrat DPI en création, changement d’écran et retour de minimisation ; restaurer le contexte du thread après chaque opération bornée.
- [ ] Publier `SurfaceCapabilities.platformAccess = Unsupported(PlatformSurfaceAccess)` conformément à Desktop ; l’accès graphique natif passe par la fenêtre.

**Gate de sortie :** aucune boucle de paint permanente au repos, aucune promesse de vsync, aucune taille demandée publiée comme effective. Les tests couvrent taille nulle/minimisation, fractions de scale, coordonnées négatives et séquence métriques puis événement. Un cahier matériel couvre le passage entre deux écrans à DPI différents ; sa validation conditionne les garanties multi-écrans correspondantes.

### Phase 4 — Clavier, pointeur et scroll

**Objectif :** rendre une fenêtre interactive avec le modèle commun d’input.

**Contenu :**

- [ ] Traduire scan codes, touches étendues, position des modificateurs, touches logiques, répétition et changement de layout.
- [ ] Séparer touche physique, interprétation logique et texte ; couvrir dead keys, AltGr et raccourcis système sans générer de texte doublé.
- [ ] Traduire mouvement, entrée/sortie, boutons et wheel horizontal/vertical dans les unités réellement connues ; préserver les deltas fractionnaires.
- [ ] Traiter perte de focus, capture perdue et fermeture par reset atomique des touches/boutons ; dédupliquer les messages souris promus depuis les contacts si plusieurs chemins sont activés.
- [ ] Relier les interactions synchrones au runtime et appliquer `inputDefaultBehavior` uniquement lorsque Windows permet de contrôler le défaut de l’événement concerné.

**Gate de sortie :** traces O2 du snapshot avant événement, touches inconnues représentées honnêtement et aucune touche bloquée après focus perdu. O3 passe par les messages natifs et le chemin public. L’injection contrôlée n’est pas présentée comme une preuve de layout physique, d’IME ou de réception matérielle ; ces scénarios ont leur cahier manuel.

### Phase 5 — Fenêtres avancées et interactions

**Objectif :** couvrir les propriétés persistantes et les actions transitoires sans no-op silencieux.

**Contenu :**

- [ ] Livrer par sous-tranches titre/icône, visibilité, tailles et contraintes, position externe, décorations/boutons système, niveaux, maximisation et minimisation.
- [ ] Relire les propriétés appliquées et corréler les observations à l’opération admise ; garder les événements externes distincts des résultats de commande.
- [ ] Implémenter le fullscreen borderless (plein écran sans bordure), avec sauvegarde puis restauration cohérente des styles et de la géométrie ; réserver le changement exclusif de mode à la phase 9.
- [ ] Qualifier attention, menu système, déplacement/redimensionnement natif, curseurs et hit testing ; appliquer le droit d’attention du host embedded lorsqu’il sera disponible.
- [ ] Distinguer capture, confinement et verrouillage du pointeur : `SetCapture` seul ne constitue pas un pointer lock ; les modes dépendant de raw input attendent la phase 8.
- [ ] Qualifier transparence et protection de contenu uniquement selon le contrat exact et le readback disponible ; aucune garantie de confidentialité universelle n’est inférée d’une API Windows.

**Gate de sortie :** un champ refusé est identifié dans l’outcome prévu ; les styles et rectangles effectifs restent cohérents après failure/cancellation ; aucune action de focus ou d’attention n’est simulée par des frappes injectées. Les tests couvrent fermeture pendant une mutation, boucle modale native et restauration après fullscreen borderless.

### Phase 6 — Text input, IME, touch et gestures

**Objectif :** compléter l’input riche sans confondre composition de texte, contacts et gestes reconnus.

**Contenu :**

- [ ] Auditer IMM32 et TSF face à `TextInputSession` : surrounding text, révisions, remplacement, sélection, composition et rectangle de candidat. Retenir le chemin qui satisfait réellement chaque contrat, avec bridge KFFI préalable.
- [ ] Traduire composition et commit UTF-16 sans double émission entre `WM_CHAR` et `WM_IME_*`, sans normalisation implicite et avec budgets vérifiés avant copie.
- [ ] Refuser les révisions obsolètes et fermer la session texte à la perte de son owner ; retirer callbacks et contextes avant destruction de la fenêtre.
- [ ] Qualifier `WM_POINTER`/touch, identités des contacts, pression et attributs du stylet seulement lorsqu’ils sont fournis ; adapter explicitement les limites du runtime actuel plutôt que promettre du multipointer absent.
- [ ] Installer uniquement les gestures reconnues par Windows et mappables au catalogue ; garder pointer, touch et gestures distincts, sans recognizer universel ajouté au runtime.

**Gate de sortie :** composition active puis perte de focus, caractères hors BMP, dead keys, replacement obsolète et fermeture pendant IME sont couverts. Le cahier manuel contient au moins un IME à composition, un layout non US et chaque catégorie de contact annoncée. Une simple réception de `WM_CHAR` ne suffit pas à activer l’IME complet.

### Phase 7 — Drag-and-drop OLE et transferts bornés

**Objectif :** respecter la décision synchrone OLE tout en livrant des transferts coroutine sûrs.

**Contenu :**

- [ ] Installer un `IDropTarget` managé KFFI par fenêtre, avec initialisation OLE et révocation sur le bon thread.
- [ ] Convertir les offres en descriptors portables bornés, puis utiliser le handler synchrone pour accept/reject avant retour natif.
- [ ] Adapter `RuntimeDropTransfer` : claim unique, timeout, lectures par chunks, replayable/single-use et budgets cumulés des sources de taille inconnue.
- [ ] Copier ou retenir correctement `IDataObject`/médias/streams avant la fin de leur emprunt ; aucune référence COM n’est déplacée vers un worker arbitraire.
- [ ] Déclarer précisément les formats supportés, y compris fichiers virtuels si leur chemin est livré ; une liste de chemins via `WM_DROPFILES` ne représente pas à elle seule le contrat complet.

**Gate de sortie :** deux consumers ne gagnent jamais la même offre, un lecteur annulé libère ses ressources, et un transfert actif est fermé au teardown. O3 vérifie une vraie frontière OLE ; un essai inter-applications couvre le drag physique et les sources différées. Les chemins et contenus n’apparaissent jamais dans les diagnostics assainis.

### Phase 8 — Raw Input et arbitrage du processus

**Objectif :** exposer `RawInputAccess` sans voler l’enregistrement natif d’un autre composant.

Windows ne conserve qu’une fenêtre enregistrée par classe de périphérique Raw Input dans un processus. Microsoft signale explicitement le risque d’interférence pour une bibliothèque. Le broker Kadre doit donc qualifier l’ownership du host ; il ne suffit pas de partager les accès entre sessions Kadre. [Microsoft — RegisterRawInputDevices](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-registerrawinputdevices).

**Contenu :**

- [ ] Livrer un broker d’enregistrement unique et un port de session vers `RawInputCoordinator`, avec admission bornée de chaque accès.
- [ ] Définir dans le design un protocole vérifiable de coexistence : standalone dont Kadre possède l’enregistrement, ou intégration fournissant explicitement cette coordination. Si cette garantie manque, conserver le chemin non supporté.
- [ ] Copier immédiatement les données `WM_INPUT`, respecter leur nettoyage natif et publier les unités raw sans les confondre avec les coordonnées de surface.
- [ ] Appliquer le routage et les budgets par accès ; le raw input ne rejoint jamais implicitement `SurfaceInput.events`.
- [ ] Détecter les pertes d’enregistrement observables, retirer la capability avant terminaison et ne retirer à la fermeture que l’enregistrement encore possédé.
- [ ] Activer les modes de pointeur de phase 5 qui dépendent de cette source seulement avec leurs preuves de restauration sur focus perdu et teardown.

**Gate de sortie :** plusieurs accès sont isolés, la fermeture de l’un ne coupe pas les autres, et le dernier owner libère sa source. Un enregistrement tiers préexistant est préservé. Aucun prompt Input Monitoring propre à macOS n’est inventé sur Windows. La livraison embedded peut rester conditionnée à la phase 12 sans prétendre garantir la coexistence générale de toute bibliothèque native.

### Phase 9 — Displays, signaux système et fullscreen exclusif

**Objectif :** publier un inventaire honnête, observer les changements système et qualifier les changements de mode.

**Sous-tranches :**

- [ ] **Inventaire :** adapter `DisplayPort`, énumérer moniteurs, bounds/work areas et modes réellement connus ; ne pas confondre identité opaque de display/mode et `HMONITOR` réutilisable.
- [ ] **Changements :** reconstruire atomiquement l’inventaire après notifications ; terminaliser le handle retiré avant publication de son retrait ; attribuer un nouvel ID à la reconnexion.
- [ ] **Appearance :** observer thème et contraste via des APIs documentées, publier la paire atomiquement et conserver `Unknown` lorsqu’une composante n’est pas lisible.
- [ ] **Mémoire :** qualifier la primitive Windows et son mapping vers `Moderate`/`Critical` ; ne pas fabriquer un niveau à partir d’un pourcentage libre choisi arbitrairement. Conserver `Unsupported` si aucun mapping conforme n’est établi.
- [ ] **Exclusif :** auditer les opérations natives de changement/restauration de mode face à `ExclusiveFullscreenPort`, avec réservation du display, readback, perte de source, cancellation après commit et restitution au teardown.

**Gate de sortie :** aucun inventaire partiel présenté comme complet, aucune `HostViewport` synthétique sur Desktop et aucun scale global de substitution. Les essais matériels couvrent DPI mixtes et retrait/reconnexion. Le fullscreen exclusif possède un contrat distinct : une modification de résolution ou un borderless ne prouve pas l’exclusivité ; si Windows/KFFI ne garantit pas le contrat, ce mode reste explicitement non supporté. Un runner partagé ne change pas de mode pour produire artificiellement une preuve.

### Phase 10 — Devices, gamepads et effets

**Objectif :** compléter le `DeviceManager` avec des identités de session et des effets sous ownership explicite.

**Contenu :**

- [ ] Livrer un inventaire HID et son lifecycle via `InputDevicePort`, sans exposer rapports bruts, handles ou identifiants persistants.
- [ ] Choisir une première source gamepad selon les bindings, le déploiement et la couverture prouvable. XInput peut fournir une tranche limitée ; GameInput demande sa propre qualification de runtime et de distribution.
- [ ] Définir la couverture exacte de l’inventaire et une stratégie d’absence de doublons entre sources ; aucune fusion par nom, VID/PID ou simple ressemblance n’est admise.
- [ ] Adapter `GamepadPort`, snapshots et routage `ActiveSessionOnly`/`AllForegroundSessions`, avec nouveaux IDs à chaque reconnexion.
- [ ] Livrer les effets réellement garantis, leurs amplitudes/durées/localities, l’arbitrage d’ownership et l’arrêt natif sur cancellation, déconnexion ou fermeture de session.

XInput ne couvre pas tous les périphériques DirectInput et certaines sources se recouvrent ; une tranche XInput ne doit donc pas être présentée comme un inventaire universel. [Microsoft — XInput et DirectInput](https://learn.microsoft.com/en-us/windows/win32/xinput/xinput-and-directinput).

**Gate de sortie :** absence de doublons prouvée pour le périmètre annoncé, publication state puis événement, isolation inter-session et aucun effet survivant à son owner. La CI prouve le contrôle et les branches sans matériel ; un cahier physique prouve input, reconnexion et chacun des effets annoncés. L’absence de manette ne devient pas un succès de vibration.

### Phase 11 — Capture et frames

**Objectif :** implémenter le chemin de capture complet, depuis admission et choix de source jusqu’à la dernière frame libérée.

La direction proposée est Windows Graphics Capture, sous runtime gates et via KFFI. Le chemin Desktop peut résoudre un `HWND` avec `IGraphicsCaptureItemInterop::CreateForWindow` ; le picker est un chemin séparé qui demande un owner UI approprié. La disponibilité du système de capture reste vérifiée à l’exécution. [Microsoft — CreateForWindow](https://learn.microsoft.com/en-us/windows/win32/api/windows.graphics.capture.interop/nf-windows-graphics-capture-interop-igraphicscaptureiteminterop-createforwindow), [Capture d’écran](https://learn.microsoft.com/en-us/windows/apps/develop/media-authoring-processing/screen-capture).

**Sous-tranches :**

- [ ] **Contrôle :** adapter `CapturePort`, permission/capability, inventaire et picker avec décisions distinctes pour `HostChoice`, `Source` et `Surface`.
- [ ] **Admission :** rejeter source stale, surface étrangère ou fermée avant réservation ; respecter les budgets avant allocation native et coordonner les demandes UI.
- [ ] **Streaming :** conserver COM/WinRT/D3D et frame pool dans KFFI ; traiter redimensionnement, source fermée, device loss et échec natif.
- [ ] **Delivery :** adapter les leases de frames et `copyPlanes`, avec format, stride, encodage couleur, timestamps, révision de configuration et backpressure (contre-pression) exacts.
- [ ] **Terminaison :** fermer après cancellation du collector, failure applicative, perte de source, révocation observée et teardown parent ; aucune frame livrée après terminaison.

`CaptureTarget.Surface` ne résout que la fenêtre vivante de la même session ; son `HWND` reste privé. Un host headless n’utilise pas une fenêtre étrangère choisie arbitrairement pour satisfaire le picker. Les chemins picker et capture directe ne partagent pas un faux état de consentement global. Aucune conversion implicite HDR/SDR, de format ou de région n’est ajoutée pour rendre un chemin artificiellement compatible.

**Gate de sortie :** chaque target annoncée possède sa preuve ; la configuration précède les frames qui en dépendent ; une frame n’est valide que pendant son emprunt ; tous les owners sont fermés sur perte de device/source. Le cahier matériel couvre picker/consentement, resize, contenu protégé et configurations couleur annoncées. Aucun fallback GDI/DXGI n’est introduit sans design et contrat propres ; les limites de capture restent visibles.

### Phase 12 — Hosts embedded et intégrations Windows

**Objectif :** attacher des sessions à des boucles déjà possédées par le host, sans seconde pompe cachée.

L’enum public actuel contient `AppKitMainLoop`, `AwtEventDispatchThread` et `JavaFxApplicationThread`. Il ne contient pas de `Win32MessageLoop` générique. Sur Windows, les deux chemins à qualifier sont donc AWT et JavaFX ; `AppKitMainLoop` reste incompatible. Les projets `integration:awt` et `integration:javafx` demeurent optionnels et font l’objet de livraisons séparées, dépendances explicites de cette phase.

**Contenu :**

- [ ] Établir, pour chaque intégration, quel thread possède réellement les fenêtres, comment les messages sont pompés et comment les commandes rejoignent ce thread ; le nom EDT à lui seul ne prouve pas l’ownership Win32.
- [ ] Installer les points d’attachement via les mécanismes supportés du framework et les bridges KFFI nécessaires, sans dépendance AWT/JavaFX imposée au runtime commun.
- [ ] Respecter scope parent, lifecycle, `allowUserAttention`, fermeture explicite de l’intégration et sessions concurrentes.
- [ ] Qualifier DPI, apartment COM, drop, raw input et effets dans le processus déjà initialisé par le host ; publier les restrictions propres à cette topologie.
- [ ] Déclarer une intégration dans `supportedIntegrations` seulement avec son consumer et sa preuve O3 sur la vraie boucle.

**Gate de sortie :** deux sessions ne partagent ni fenêtres ni input ; fermer l’une conserve l’autre et la boucle hôte ; fermer la dernière fenêtre ne stoppe pas automatiquement une session embedded ; détacher Kadre ne poste pas de `WM_QUIT` à la boucle du framework. Une combinaison non admise conserve la failure exacte de la façade Desktop, sans inventer de nouvelle option publique.

Cette phase peut avancer dès que les phases 1 à 4 sont stables. Une livraison standalone peut la précéder, mais ne se présente pas comme une couverture embedded complète.

### Phase 13 — Interop, robustesse et fermeture contractuelle

**Objectif :** rendre le backend maintenable et vérifiable depuis les artifacts destinés aux consommateurs.

**Contenu :**

- [ ] Compléter `capabilities/win32.md`, avec une ligne par feature normative, minima qualifiés, compile/runtime gates, états absents, topologies et tests.
- [ ] Valider les consumers Kotlin et Java via l’umbrella publié, la résolution transitive de KFFI et la découverte des providers sur chaque OS.
- [ ] Ajouter ou adapter les samples standalone, embedded, input riche et capture aux capabilities effectives ; aucune dépendance à un renderer dans le backend.
- [ ] Auditer classes de fenêtres, `HWND`, cursors/icons, enregistrements Raw Input, owners COM, sources, effets, jobs et callbacks sur cycles de création/fermeture répétés.
- [ ] Qualifier réutilisation séquentielle et classloaders : aucune callback ou classe native enregistrée ne retient un ancien classloader sans owner vivant explicite.
- [ ] Mesurer les gates CI, fixer les cahiers de qualification et documenter les limites restantes ; ne retirer les références historiques qu’après audit de couverture.

**Gate de sortie :** chaque domaine et topologie annoncés sont couverts ; aucun type interne/FFI n’est exposé ; les états `Unsupported` ont une justification documentée ; les branches `Supported` possèdent leurs preuves ; les régressions AppKit et Web restent bloquantes pour les changements communs.

## 6. Jalons et dépendances

| Jalon | Phases closes | Résultat observable |
|---|---|---|
| A — Win32 interactif | 0 à 4 | Application standalone, plusieurs fenêtres, DPI/surfaces et input essentiel |
| B — Interactions complètes | 0 à 8 | Fenêtres avancées, IME, touch/gestures, drop et raw input selon capabilities |
| C — Domaines Windows couverts | 0 à 11 | Displays, devices, effets et capture, avec décisions explicites pour les modes non supportables |
| D — Backend qualifié | 0 à 13 | Embedded qualifié, artifacts/consumers, registre et preuves complets |

L’ordre 0 → 1 → 2 → 3 → 4 est strict. Les phases 5 et 6 s’appuient sur ce socle ; la phase 7 dépend aussi du bridge OLE ; la phase 8 possède son propre arbitrage process-wide. La phase 9 dépend des fenêtres et métriques, son exclusif des mutations de phase 5. La phase 10 dépend du lifecycle, du routage de focus et de la coordination des sources de périphériques, pas obligatoirement de tous les accès raw. La phase 11 dépend des fenêtres/surfaces, des ports capture communs et de KFFI WinRT/D3D ; son chemin display requiert l’identité de source correspondante.

Les phases 9 à 12 peuvent avancer séparément une fois leurs prérequis établis. La fermeture 13 attend toutes les décisions de couverture. Chaque sous-tranche peut disposer de contrats distincts pour ne pas bloquer une preuve indépendante ; elle ne doit jamais activer le contrat plus large dont un chemin promis reste absent.

Les jalons ne fixent ni date de release ni estimation en semaines : les gaps KFFI, les mécanismes d’intégration des frameworks et l’accès à un environnement matériel de qualification dominent encore l’incertitude. La phase 0 permet de chiffrer le premier jalon sur des dépendances constatées.

## 7. Preuves, CI et critères d’activation

### 7.1 Niveaux de preuve et essais matériels

| Famille | Preuve attendue | Limite explicite |
|---|---|---|
| O1 et structure | API/ABI, imports autorisés, consumers, absence de FFI locale | Ne prouve pas un comportement natif |
| O2 runtime | Modèle indépendant des états, budgets, cancellation, ordres de publication, races et sentinelles | Un fake n’active pas à lui seul une capability Win32 |
| O3 Windows | Stimulus à la frontière Windows et observation publique Kadre, dans la vraie topologie | Un appel direct au mapper ou un callback Kotlin simulé n’est pas une preuve OS |
| Cahiers matériels | DPI mixtes, IME réel, touch/pen, drop inter-applications, gamepads/effets, exclusif et capture | Les limites et cas non réalisables sont consignés, jamais convertis en succès automatisé |

Les cinq risques prioritaires doivent être attribués aux scénarios des phases concernées :

1. **Réentrance et destruction :** fermeture dans une callback, owner révoqué et réutilisation de `HWND` — phases 0 à 2.
2. **Boucles modales :** commandes/arrêt pendant déplacement, menu ou drop, sans deadlock ni commande perdue — phases 1, 5 et 7.
3. **DPI et espaces :** changement d’écran avec scale fractionnaire et coordonnées négatives — phases 3 et 9.
4. **Coexistence dans le host :** apartment déjà initialisé, Raw Input détenu par un tiers, arrêt d’une seule session — phases 7, 8 et 12.
5. **Perte de ressource pendant un emprunt :** source capture, périphérique ou fenêtre disparus après admission — phases 2, 10 et 11.

### 7.2 Gate Windows

Chaque contrat natif actif exige registre, scénarios requis, sentinelles, rapports exécutés, mapping `evidence.tsv` et `contract-evidence/<contractId>.json` cohérents avec le commit. L’identité JVM seule ne suffit pas : l’adapter et l’environnement observé doivent empêcher qu’une preuve AppKit satisfasse un contrat Win32.

Le futur job Windows s’intègre au gate agrégé prévu par `TEST-STRATEGY.md`. Il possède un watchdog externe qui termine aussi les processus enfants, et refuse skips, retries automatiques, rapports vides, scénarios absents et failures masquées. Une machine incapable d’exécuter une preuve obligatoire fait échouer l’infrastructure ; une capability réellement absente est testée par son outcome exact. Aucun script de phase n’utilise le `:kadre-win32:check` historique, absent du build actuel.

Les budgets existants s’appliquent : p95 d’exécution de chaque job ≤ 8 minutes, p95 du gate complet ≤ 10 minutes sur les 20 derniers succès, timeout initial de 15 minutes par job. Les tests O2 communs ne sont pas inutilement rejoués dans chaque backend ; Windows exécute les scénarios portables via son driver lorsqu’ils prouvent une vraie traduction native.

Les commandes et tâches Win32 exactes seront introduites avec la phase 0. Leur absence actuelle interdit de présenter une commande projetée comme une vérification déjà exécutable. Une preuve locale macOS ne remplace jamais le gate Windows.

## 8. Designs, plans et PRs par phase

Cette roadmap est un document de séquencement, pas un plan exécutable qui fixerait dès maintenant les signatures de tous les bridges futurs. Chaque phase suit le cycle :

1. auditer ses contrats, les ports existants, les hypothèses AppKit et les bindings KFFI ;
2. écrire le design ciblé, avec owners, thread/apartment, états, failures et décisions de capability ;
3. livrer les prérequis KFFI nécessaires avant leur activation dans Kadre ;
4. écrire le plan détaillé avec fichiers exacts, tâches, tests et commandes réellement introduites ;
5. implémenter les invariants runtime O2, puis leur traduction native O3 ;
6. compléter cahiers matériels, registre, preuves et documentation ;
7. activer seulement les contrats dont tous les chemins promis sont fermés.

Une PR garde un périmètre revuable, préserve les contrats actifs et n’introduit aucune refonte commune sans nécessité Win32 démontrée. L’ancien code sert à identifier des appels, constantes et cas limites à revérifier ; ses IDs basés sur `HWND`, callbacks directs, no-ops de compatibilité et helpers FFM ne sont pas réutilisés comme architecture.

## 9. Hors périmètre et fermeture

Restent hors périmètre : renderer D3D/OpenGL/Vulkan public, widgets, API de boucle inspirée de winit, intégration dans une fenêtre étrangère par reparenting implicite, clipboard public absent du catalogue, hooks globaux ou injection de saisie comme fonction produit, service Windows et support Wine. D3D/COM/WinRT entrent uniquement comme dépendances internes nécessaires à une capability prévue, notamment capture.

Les frameworks tiers restent dans leurs projets optionnels. Les détails visuels Win32 historiques non exprimés par le catalogue fermé ne créent pas de nouvelles extensions publiques. L’accès expert déjà prévu reste le lease de handle Desktop.

La roadmap est fermée lorsque :

- chaque garantie structurelle et chaque domaine applicable possède une implémentation prouvée ou une exclusion native justifiée ;
- standalone et chaque intégration embedded annoncée respectent la même autorité runtime, les mêmes budgets et le même modèle d’outcome ;
- les IDs et ressources ne traversent aucune session ou reconnexion involontairement ;
- tout owner possède une fermeture idempotente, un thread/apartment identifié et une place dans le teardown ;
- les callbacks ne peuvent plus admettre de travail après révocation et leurs supports ne sont libérés qu’après quiescence ;
- tous les contrats actifs disposent des preuves applicables, des sentinelles et du gate Windows exécuté ;
- le registre de capabilities, les consumers Kotlin/Java et la matrice Windows qualifiée correspondent au comportement livré ;
- aucune FFI locale, aucun succès fictif ni dépendance à un artifact uniquement local ne subsiste.
