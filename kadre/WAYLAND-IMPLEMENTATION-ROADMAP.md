# Kadre — Roadmap d’implémentation complète Wayland/JVM

**Statut :** proposition de roadmap architecturale ; les designs et plans exécutables restent séparés par phase.
**Date :** 5 octobre 2026.
**Cible :** Linux sur JVM 25, glibc et musl, backend Wayland via KFFI.
**Base examinée :** `ac261e00` ; nouvelle architecture `kadre`, distincte de `kadre-old`.
**Mode de livraison :** tranches verticales pilotées par le risque, chacune avec ses preuves et son activation contractuelle.

Cette roadmap organise le passage du contrat Desktop existant à un backend Wayland utilisable, puis à la couverture de tous les domaines applicables. La priorité est une session standalone, une vraie fenêtre et une surface exploitable par un renderer externe, avant les extensions, les intégrations embedded et la capture. Une fonctionnalité n’est livrée que lorsque son comportement public est prouvé sur la frontière native.

Le document propose un ordre de travail ; il ne modifie ni l’API publique, ni le registre de contrats, ni le statut des capabilities. Il suit le niveau de détail des roadmaps [AppKit](APPKIT-IMPLEMENTATION-ROADMAP.md) et [Web](WEB-IMPLEMENTATION-ROADMAP.md), sans figer les signatures des bindings encore à auditer.

## 1. Objectif et autorités

La couverture complète comprend : standalone et embedded, lifecycle, fenêtres et surfaces, input, interactions transitoires, IME, drag-and-drop, displays, devices, gamepads, raw input, capture, interop, diagnostics et teardown. Elle n’impose pas un support universel : chaque fonctionnalité reçoit une décision prouvée de support, d’indisponibilité runtime ou d’absence structurelle.

Les autorités restent :

- [DESIGN.md](DESIGN.md) pour les invariants et la sémantique ;
- [PUBLIC-API-CATALOG.md](PUBLIC-API-CATALOG.md) pour les types et signatures ;
- [OPERATION-CONTRACTS.md](OPERATION-CONTRACTS.md) pour les failures, outcomes et frontières de cancellation ;
- [BACKEND-CAPABILITIES.md](BACKEND-CAPABILITIES.md) pour les garanties Desktop et les disponibilités ;
- [INTEROP-EXPORTS.md](INTEROP-EXPORTS.md) pour les handles et les consumers ;
- [POLICY-PROFILES.md](POLICY-PROFILES.md) pour les budgets et la delivery ;
- [PROJECT-ARCHITECTURE.md](PROJECT-ARCHITECTURE.md) pour les modules et dépendances ;
- [TEST-STRATEGY.md](TEST-STRATEGY.md) pour les oracles et gates CI ;
- [KFFI-REQUIREMENTS.md](KFFI-REQUIREMENTS.md) pour les bindings manquants.

Une contradiction entre une primitive Wayland et un contrat Kadre est résolue dans la spécification concernée avant d’activer le code dépendant. Aucun succès fictif, timeout métier implicite, inventaire partiel présenté comme complet ou fallback silencieux ne ferme une phase.

## 2. État initial vérifié

| Élément | Acquis dans le dépôt | Conséquence |
|---|---|---|
| Orchestration Desktop | `DesktopBackend.Wayland`, discovery par `ServiceLoader` et SPI `DesktopBackendProvider` existent. Sous Linux, `Auto` examine Wayland avant X11. | Réutiliser cette sélection, sans nouvelle façade publique. |
| Runtime | `RuntimeSessionComponentsFactory`, `RuntimeWindowManager`, `WindowCommandPort`, `SurfaceCommandPort` et les ports input/display/capture/device existent. | Adapter les primitives Wayland aux machines à états existantes. |
| Build courant | `settings.gradle.kts` inclut AppKit et Web ; ni `backend:wayland` ni son driver ne sont inclus. | Ajouter ces projets avec leur première responsabilité effective. |
| Bindings | Le catalogue Gradle référence `kffi-wayland` et `kffi-posix` en `1.0.0-SNAPSHOT`. | Une coordonnée déclarée ne prouve ni disponibilité ni conformité des APIs requises. |
| Implémentation historique | `kadre-old/kadre-wayland` contient boucle, registry, input, fenêtres, extensions et capture ; des tests Weston existent. | Réutiliser les cas limites et connaissances protocole après audit, sans porter l’ancienne architecture telle quelle. |
| Preuves actuelles | Aucun producteur de preuves Wayland ni workflow Wayland de la nouvelle architecture. | La présence du code historique n’active aucune capability de `kadre`. |

Les anciens plans sous `docs/superpowers/plans`, notamment le plan POSIX/Wayland du 17 juillet, décrivent une autre topologie. Ils ne justifient pas de recréer `ffi:posix`, `ffi:wayland` ou une event loop publique dans Kadre. Les usages historiques de `java.lang.foreign` sont à remplacer par les APIs KFFI appropriées.

## 3. Choix d’architecture proposés

### 3.1 Frontière runtime et backend

```text
platform:desktop
       |
       v
WaylandBackendProvider ----> SessionRuntime
       |                         |
       v                         v
WaylandHostSession <---- ports internes runtime
       |
       +-- connexion, registry et pompe Wayland
       +-- peers fenêtre/surface et seats
       +-- adapters displays, input, portal et devices
       |
       v
KFFI Wayland / POSIX / autres bindings nécessaires
       |
       v
compositor et services Linux
```

Le runtime possède admission, budgets, IDs, révisions, reducers, flows, outcomes et ownership logique. Le backend possède proxies natifs, négociation des protocoles, conversion des payloads, marshalling et exécution des commandes admises. Les nouveaux composants restent dans `org.graphiks.kadre.internal.*`.

Choix initial : une connexion Wayland dédiée par host, possédée par Kadre, avec un owner de dispatch explicite. Ne pas emprunter la connexion interne d’AWT, JavaFX ou d’un renderer. Un éventuel partage ultérieur exige une preuve d’isolation et ne change pas l’identité publique des sessions. Les brokers process-wide ne sont introduits que pour une ressource réellement partagée, par exemple un service Linux de périphériques.

### 3.2 KFFI et négociation

KFFI demeure l’unique propriétaire des bindings, listeners natifs, layouts ABI, wrappers POSIX et primitives de streaming. Aucun downcall/upcall Panama, marshal Wayland générique, générateur ou XML de génération n’entre dans Kadre pour contourner un manque.

L’audit initial couvre :

| Besoin | Livraison Kadre dépendante |
|---|---|
| Connexion, queues, registry, versions, erreurs et destruction des proxies | host et teardown |
| Owners de listeners révocables, copie de payloads et isolation des exceptions | toute callback native |
| Poll, wake réarmable, fd non bloquants, fermeture et erreurs POSIX sur glibc/musl | pompe et transferts |
| `wl_compositor`, `xdg_wm_base`, surface et handles typés | fenêtres et interop |
| `wl_seat`, XKB, outputs, curseurs et protocoles d’extensions | input, affichage et interactions |
| D-Bus avec réponses asynchrones et transfert de fd, puis PipeWire/SPA | capture portal |
| Observation Linux de devices et effets, avec permissions explicites | inventaires, gamepads et haptique |

Chaque manque confirmé reçoit une entrée dans `KFFI-REQUIREMENTS.md` avec contrat d’ownership, blocage et preuve attendue. Consommer ensuite un artifact identifiable et reproductible ; enregistrer sa révision résolue dans les preuves, sans prétendre qu’un snapshot mutable constitue une baseline stable.

La version bindée est bornée par la version annoncée et celle comprise par le binding. La présence d’un global n’active que les fonctions dont toutes les dépendances sont utilisables. La disparition d’un global révoque l’admission concernée et actualise les capabilities avant tout événement dépendant.

### 3.3 Boucle, callbacks et panne de connexion

La pompe suit le contrat de `libwayland-client` : vider les événements pending, préparer la lecture, flush, attendre les fd, puis terminer chaque préparation réussie par `read_events` ou `cancel_read`. Un flush incomplet avec `EAGAIN` surveille aussi l’écriture ; `EINTR`, réveil applicatif, arrêt et erreurs de fd ont des chemins distincts. Un roundtrip bloquant ne s’exécute pas pendant une lecture préparée. [Référence client officielle](https://wayland.freedesktop.org/docs/html/apb.html).

Le wake doit fonctionner plusieurs fois après drain, même sans fenêtre et sans événement compositor. Les callbacks copient les payloads nécessaires avant retour, ne suspendent pas et n’exposent pas d’exception Kotlin à C. Ils alimentent l’ingress runtime ; le snapshot public précède l’événement associé.

L’exception applicative prévue est le handler d’interaction synchrone défini par `DESIGN.md` §9.6. Il s’exécute dans la callback éligible, avec le moteur commun de tokens, avant l’input ordinaire.

Une perte de connexion après admission termine les ressources avec la failure autorisée puis le `SessionOutcome` approprié. Elle ne provoque ni reconnexion transparente, ni migration vers X11, ni réutilisation d’anciens IDs. Une nouvelle connexion nécessite un nouveau host.

### 3.4 Fenêtres, renderer et état effectif

La création respecte le handshake `xdg_surface` : rôle, commit initial sans buffer, configuration et acquittement avant le premier buffer. Les événements de configuration sont assemblés atomiquement ; un serial ancien ne peut pas être acquitté de nouveau. Les dimensions proposées nulles au sens protocolaire ne deviennent pas une surface Kadre de taille nulle. [Protocole xdg-shell](https://raw.githubusercontent.com/wayland-mirror/wayland-protocols/main/stable/xdg-shell/xdg-shell.xml).

Kadre ne dessine ni contenu ni décorations. Un renderer externe possède ses buffers ; le driver de test fournit son propre contenu contrôlé. Dès la première fenêtre, documenter qui possède `attach`, `commit`, frame callbacks, scale et viewport : deux composants ne doivent pas écrire concurremment l’état pending de la même `wl_surface`.

Le seul accès public reste `Window.withDesktopHandle` avec `DesktopNativeWindowHandle.Wayland(displayAddress, surfaceAddress)`. Les adresses sont valides uniquement dans le callback, sur le thread host, sous lease de lifetime. Un renderer qui doit les retenir après retour ne satisfait pas le contrat actuel : faire approuver un amendement ciblé avant de promettre cette intégration. Ne pas introduire discrètement une API `present`, une lease publique ou un handle permanent.

Une émission native ne prouve pas à elle seule un changement effectif. Le design de chaque propriété précise son autorité : état client effectivement possédé, observation compositor ou absence de primitive conforme. Les demandes différées utilisent les outcomes existants ; `wl_display.sync` n’est pas une preuve de visibilité, de focus ou de présentation.

### 3.5 Limites à traiter explicitement

| Domaine | Décision de départ proposée | Condition d’extension |
|---|---|---|
| Position globale d’une top-level | `outerPosition` unsupported ; `outerBounds = null` lorsque non observable | Une primitive conforme et une preuve indépendante, jamais une origine `(0, 0)` inventée. |
| Décorations `System` par défaut | Bloquant pour l’admission d’une configuration exigeant des décorations système | Négociation effective côté compositor ; sinon rejet exact. Ne pas remplacer silencieusement par `Borderless` et ne pas dessiner de chrome Kadre. |
| Taille, contraintes et `resizable` | Support limité au comportement que le backend peut effectivement garantir | Tests de demandes ignorées/ajustées et corrélation des configurations. |
| Fullscreen | Borderless conditionnel ; exclusif unsupported au départ | Aucun fullscreen ordinaire présenté comme un changement exclusif de mode. |
| Level, boutons système, blur, icône et protection | Unsupported tant que la primitive et sa garantie manquent | Décision par champ ; alpha ne prouve ni blur ni protection du contenu. |
| Focus, activation et attention | Pas de focus forcé ni de succès assimilé à une activation | Protocole et autorité d’interaction compatibles avec le verbe public. |
| Displays | Inventaire complet des outputs exposés au client, si projetable dans le modèle | Aucun `HostViewport` Desktop, écran primaire arbitraire ou mélange d’espaces de coordonnées. |
| Gestures, pen et raw input | Capabilities distinctes de pointer/touch | Extensions et source réellement disponibles ; aucun événement synthétique présenté comme natif. |
| Capture | Portal `HostChoice` prioritaire ; autres targets séparées | Pas de remplacement de `Surface` ou `Source` par un picker non corrélé. |

Ces décisions sont des bornes d’implémentation, pas une matrice de support déjà livrée. `Capability.Supported` n’embarque jamais `FeatureAvailability.Unsupported` ; une opération structurellement absente utilise `Capability.Unsupported` et la failure exacte du catalogue.

### 3.6 Teardown

Fermer l’admission, révoquer handlers et listeners, terminaliser les requêtes/accès, attendre les callbacks et leases déjà admis, puis libérer les ressources natives dans l’ordre de leurs dépendances. Les proxies enfants précèdent leurs parents ; la connexion et le wake sont libérés après arrêt de leur usage. La perte du serveur exige un chemin de nettoyage local qui ne dépend pas d’une réponse distante.

Chaque fd de keymap, de drop ou de capture a un owner unique. Les buffers empruntés sont copiés ou retenus avec un transfert explicite avant réutilisation. Aucun owner closeable n’est transporté directement dans un `Flow` multicast.

## 4. Topologie des changements

Les chemins suivants sont des emplacements de travail prévus, pas des fichiers déjà livrés :

| Emplacement | Responsabilité | Première phase |
|---|---|---:|
| `kadre/backend/wayland/` | provider, host, peers et adapters natifs JVM | 1 |
| `kadre/contracts/driver/wayland/` | stimuli externes, fixtures compositor et observations natives | 1 |
| `kadre/capabilities/wayland.md` | minimums testés, compile/runtime gates et états absents par feature | 1 |
| `kadre/backend/wayland/contracts/evidence.tsv` | correspondance scénarios et tests natifs | 1 |
| `kadre/backend/wayland/manual/` | procédures matérielles et limites non automatisables | selon le domaine |
| `.github/workflows/kadre-wayland-contracts.yml` | exécution Linux et collecte de preuves | 1 |
| `scripts/ci-wayland-runtime.sh` | lancement isolé du compositor et collecte des logs | 1 |

Réutiliser `runtime/src/jvmMain/.../WindowCommandPort.kt`, `SurfaceCommandPort.kt` et `RuntimeWindowManager.kt`, ainsi que les ports de `runtime/src/commonMain`. Une extension de SPI interne est possible si Wayland révèle une hypothèse AppKit ; elle reçoit ses preuves communes et des tests de non-régression AppKit, sans recopier le runtime dans le backend.

Modifier ensemble les includes de `settings.gradle.kts`, dépendances transitives Desktop, publications/consumers de `kadre/build.gradle.kts` et producteurs de `contracts/validator/build.gradle.kts`. Ne créer aucun sous-projet fonctionnel `window`, `input`, `display`, `capture` ou FFI. Les intégrations optionnelles AWT/JavaFX n’entrent dans le build qu’avec leur code et leurs preuves.

## 5. Phases d’implémentation

Toutes les phases ci-dessous sont proposées. Aucun jalon Wayland de la nouvelle architecture n’est déclaré terminé dans ce document.

### Phase 0 — Fermer les prérequis protocolaires et contractuels

**Objectif :** rendre la première verticale implémentable sans hypothèse cachée.

- [ ] Auditer les APIs KFFI publiées : symboles, callbacks managés, POSIX, XKB, ownership et chargement paresseux ; ouvrir uniquement les gaps confirmés.
- [ ] Choisir et enregistrer la baseline Linux/JVM/libc, les versions minimales de protocoles bindées et les images de test reproductibles.
- [ ] Résoudre le défaut `WindowDecorations.System`, la frontière renderer/commit/lease et la représentation du lifecycle sans observer d’occlusion ou de visibilité inventé.
- [ ] Définir le probe `isAvailable()` : libraries, endpoint et protocoles requis, coût borné, cleanup ; traiter le fd transmis par `WAYLAND_SOCKET` sans le consommer au probe puis le réutiliser fermé au run.
- [ ] Écrire le design de première tranche avec failures avant session, panne après admission et fermeture ; identifier les contrats à allouer dans les familles normatives existantes.

**Gate de sortie :** décisions précédentes documentées, bindings minimaux vérifiés ou blocages KFFI identifiés précisément, scénarios positifs/négatifs définis. Un manque de binding bloque sa tranche, pas les domaines indépendants.

### Phase 1 — Provider, standalone headless et première preuve Linux

**Dépendance :** phase 0 et bindings host.

- [ ] Ajouter le provider paresseux et son service descriptor ; aucune charge native Wayland pendant la discovery sur macOS/Windows.
- [ ] Implémenter connexion, registry durable, négociation, dispatch, wake réarmable, arrêt, erreurs et owners révocables.
- [ ] Brancher `runKadreApplication` sur `SessionRuntime`, avec `primarySurface = null` et managers explicites. Une session initialement headless reste `Background + Inactive` et ne s’arrête pas parce qu’elle n’a jamais eu de fenêtre.
- [ ] Introduire le driver, le workflow et le producteur de preuves Wayland dès cette tranche ; utiliser Weston headless pour la première frontière externe.
- [ ] Tester plusieurs cycles start/stop, réveils répétés, arrêt depuis un autre thread, annulation, serveur absent et déconnexion en attente.

**Gate de sortie :** session réelle démarrée/arrêtée sur Linux, sans fuite de fd ni callback tardive ; preuve O3 de la pompe et preuve O2 de l’outcome. Le livrable reste explicitement limité au headless ; la garantie Desktop complète de `requestWindow` ne peut être revendiquée avant la phase 2.

### Phase 2 — Fenêtres, surface minimale et interop renderer

**Dépendance :** phase 1 et décisions renderer/décorations de phase 0.

- [ ] Implémenter `WindowCommandPort` : création, configuration initiale, `OpenedHere`, annulation avant/après commit et destruction partielle sur échec.
- [ ] Installer la surface minimale, ses metrics et `withDesktopHandle` dès cette tranche ; le driver présente un buffer contrôlé pour prouver le mapping réel sans introduire de renderer dans Kadre.
- [ ] Publier uniquement les propriétés initiales conformes. Une option explicitement non supportée suit le rejet normatif, sans remplacement silencieux du `WindowSpec`.
- [ ] Implémenter titre, fermeture native, interception de close, promotion de la première fenêtre et succession déterministe de `primary`.
- [ ] Appliquer `stopWhenLastWindowClosed` seulement après admission de la première fenêtre ; tester requêtes pendantes pendant cette transition.

**Gate de sortie :** deux fenêtres réellement mappées et indépendantes, destruction de l’une sans perte de l’autre, état public cohérent avec les configurations, handles bornés et fermeture concurrente sûre. Tester configurations de taille non imposée, configurations successives, callback tardive, propriété initiale refusée et absence de décorations serveur.

### Phase 3 — Metrics, outputs, scale et redraw

**Dépendance :** phase 2.

- [ ] Assembler les batches d’outputs selon les versions négociées ; suivre ajout/retrait et appartenance d’une surface aux outputs sans conserver d’identité terminale.
- [ ] Produire `DisplayPortSnapshot` complet ou une failure explicite. Garder `primaryKey = null` si aucun primaire n’est observable, `workArea = null` si inconnue ; valider espaces, transformations, modes et métadonnées.
- [ ] Séparer taille logique, taille buffer et scale de surface ; tester échelles mixtes, rotation et migration entre outputs. L’échelle fractionnaire nécessite une coordination explicite avec le viewport et le renderer. [Protocole fractional-scale](https://raw.githubusercontent.com/wayland-mirror/wayland-protocols/main/staging/fractional-scale/fractional-scale-v1.xml).
- [ ] Implémenter `SurfaceCommandPort.requestRedraw` et la coalescence runtime sans dépendre de l’arrivée d’un nouveau configure. Un callback `wl_surface.frame` est un signal de pacing, pas une preuve de présentation ni une horloge garantie pour une surface invisible. [Protocole Wayland core](https://wayland.freedesktop.org/docs/html/apa.html).
- [ ] Préciser le partage des commits de scale/viewport/frame avec le renderer ; ne publier que les valeurs effectives ou possédées dont l’autorité a été fermée en phase 0.

**Gate de sortie :** traces publiques vérifiées à 1×, 2× et à une échelle fractionnaire contrôlée, changement d’output, retrait d’output et redraw répété sans resize. L’inventaire reste `Unavailable` si le modèle public ne peut pas représenter honnêtement les informations obtenues.

### Phase 4 — Seats, clavier, pointeur et scroll

**Dépendance :** phase 3 ; XKB et listeners sûrs disponibles dans KFFI.

- [ ] Gérer plusieurs seats et les changements de capabilities ; associer chaque événement à sa surface propriétaire, sans focus ni seat « courant » global.
- [ ] Lire/remplacer/libérer keymaps et états XKB ; distinguer physical key, logical key, texte, modifiers et répétition négociée. Arrêter la répétition au blur, retrait du clavier et teardown.
- [ ] Implémenter enter/leave, mouvements, boutons, curseurs et unités de scroll ; agréger les frames pointeur selon la version et préserver les fins de séquence.
- [ ] Neutraliser touches/boutons retenus sur perte de focus ou de device conformément au reducer commun ; ne pas transformer un clavier présent en focus acquis.
- [ ] Respecter budgets, stamps, state avant event, overload et isolation entre surfaces/sessions.

**Gate de sortie :** injection depuis le côté compositor ou un dispositif de test externe, observation de l’API Kadre ; layouts distincts, touche morte, repeat désactivé, focus perdu avec touche enfoncée, scroll continu/discret, disparition de seat et deux fenêtres. Le mapper de production n’est jamais son propre oracle.

### Phase 5 — Interactions et fenêtres avancées

**Dépendance :** phase 4.

- [ ] Brancher le moteur d’interactions commun sur les serials réellement éligibles ; implémenter move/resize avec seat et surface d’origine, dans la callback native.
- [ ] Couvrir tokens `Missing`, `Expired`, `Consumed`, `WrongSurface`, callbacks imbriquées, priorité du handler sur l’action armée et budgets des outcomes. N’activer que les triggers réalisables.
- [ ] Ajouter progressivement taille, contraintes, resizable, fullscreen borderless et autres champs conformes ; corréler chaque `WindowOperationId` et publier l’état effectif, y compris en cas d’application partielle.
- [ ] Évaluer séparément `xdg-decoration`, activation, icône, contraintes pointeur et pointeur relatif. Leur présence ne prouve pas la conformité de tous les verbes publics voisins.
- [ ] Tester les branches de refus compositor, perte de capability et cancellation après émission native ; aucune promesse de focus ni d’effet visible pour une simple demande d’attention.

**Gate de sortie :** actions natives observables avec vrais serials, refus déterministes hors contexte et absence de double consommation. Les champs sans primitive conforme restent unsupported ; `Exclusive` n’est pas ajouté au domaine du fullscreen par analogie avec AppKit.

### Phase 6 — Text input, IME, touch, gestures et pen

**Dépendance :** phase 4 ; phase 5 uniquement pour les interactions utilisées.

- [ ] Implémenter le chemin IME choisi, en priorité `text-input-v3` lorsqu’il est exposé : activation par surface, état environnant, sélection, preedit, commit, suppression et regroupement par `done`.
- [ ] Convertir explicitement les offsets UTF-8 en octets vers les indices UTF-16 du modèle Kadre, avec limites de payload et sélection valide. Ne pas couper un code point ; tester emoji, accents combinés et suppression autour du curseur. [Protocole text-input-v3](https://raw.githubusercontent.com/wayland-mirror/wayland-protocols/main/unstable/text-input/text-input-unstable-v3.xml).
- [ ] Empêcher les doubles commits entre chemin clavier/XKB et IME ; purger proprement composition et callbacks à la perte de focus.
- [ ] Ajouter touch et annulation des contacts, puis gestures natives et pen via leurs protocoles propres, en sous-tranches indépendantes.
- [ ] Publier les subsets réellement implémentés ; absence de protocole IME ou gesture ne désactive pas arbitrairement le clavier ou le pointeur.

**Gate de sortie :** cycles enable/disable/focus, batch IME obsolète, composition annulée et contacts interrompus prouvés. Les frontières physiques non injectables reçoivent un cahier manuel versionné ; aucun recognizer universel ni pression fictive n’est introduit.

### Phase 7 — Drag-and-drop et transferts

**Dépendance :** phase 4 et primitives de transfert POSIX.

- [ ] Adapter `wl_data_device` et `wl_data_offer` au modèle de drop public : offres immuables, MIME, acceptation, négociation et terminalisation.
- [ ] Lire les données hors de la callback native via fd non bloquants ; gérer transfert partiel, annulation, limite de payload, source disparue et fermeture du destinataire.
- [ ] Borner le lifetime des offers, transfers et fd ; corréler tout résultat tardif au bon owner, sans réutiliser une offre détruite.
- [ ] Distinguer refus, abandon utilisateur et erreur de transport uniquement selon ce que le protocole permet d’observer.

**Gate de sortie :** un client externe effectue un drop accepté puis refusé ; couverture d’un transfert interrompu, d’un MIME non supporté, de données au-delà du budget et d’un teardown pendant lecture. Pas d’extension implicite à une API clipboard ou drag source absente du catalogue.

### Phase 8 — Embedded et lifecycle du host

**Dépendance :** phases 2 et 4 stabilisées.

- [ ] Concevoir séparément les intégrations `AwtEventDispatchThread` et `JavaFxApplicationThread` déjà nommées par l’API ; vérifier le thread réel d’attachement et l’absence de relocation cachée.
- [ ] Définir une pompe non bloquante pour la boucle UI hôte : éventuelle attente des fd sur un worker, dispatch des callbacks et commandes sur le thread host, sans vol de queue native au toolkit.
- [ ] Vérifier que les interactions synchrones et les leases du renderer restent conformes dans ce modèle. Tant que ce point n’est pas démontré, ne pas annoncer l’intégration dans `supportedIntegrations`.
- [ ] Implémenter détachement, scope parent, agrégat visibilité/activation et plusieurs sessions ; fermer Kadre laisse vivre la boucle du host et les autres sessions.

**Gate de sortie :** tests externes AWT et JavaFX séparés pour attach, input, absence de blocage UI et teardown. Une combinaison non supportée suit `InvalidRequest("options")` ou `Unsupported(HostAttach)` selon la sélection normative ; aucune nouvelle enum publique d’intégration Wayland n’est ajoutée sans révision de l’API.

### Phase 9 — Services Linux, devices, gamepads et raw input

**Dépendance :** phases 1 et 4 ; extensions pointeur de phase 5 selon le chemin choisi.

- [ ] Observer apparence/thème/contraste et pression mémoire uniquement via une source système définie ; publier `Unknown` ou l’indisponibilité prévue lorsqu’elle manque.
- [ ] Distinguer seats logiques et inventaire de périphériques physiques. Une liste de seats ne vaut pas inventaire complet de devices ; définir le périmètre d’une source Linux et les permissions nécessaires avant activation.
- [ ] Brancher `InputDevicePort` et `GamepadPort`, puis effets gamepad, en sous-tranches distinctes ; couvrir hotplug, IDs après reconnexion, routage session et arrêt des effets.
- [ ] Évaluer le pointeur relatif comme source de `RawInputPort` limitée à son contexte réel : unités documentées, disponibilité liée au focus/lock, accès indépendants, suspension et révocation.
- [ ] Ne pas confondre mouvement relatif, comptes physiques de device et observation globale hors focus. Une source globale nécessite sa propre permission, ses bindings et sa preuve ; elle n’est pas activée par simple présence de Wayland.

**Gate de sortie :** absence de service, refus d’accès, retrait/reconnexion et fermeture d’un accès parmi plusieurs couverts. Les deltas raw ne sont jamais réinjectés dans `SurfaceInput.events`. Les preuves matérielles des effets complètent les tests de protocole et restent identifiées comme telles.

### Phase 10 — Capture portal et PipeWire

**Dépendance :** phases 1 à 3 ; bindings D-Bus/PipeWire conformes. Cette phase ne dépend pas de la livraison des gamepads ou d’embedded.

- [ ] Livrer d’abord le contrôle : `CreateSession`, `SelectSources`, `Start`, réponses asynchrones corrélées, fermeture des requêtes/sessions et révocation. Observer les versions/propriétés du portal, sans déduire une disponibilité de la seule présence du service.
- [ ] Publier `CaptureSources.HostPickerOnly` lorsque seul le choix utilisateur est possible. Conserver `CaptureTarget.Source` et `CaptureTarget.Surface` unsupported tant qu’aucun chemin exact vers la cible n’est prouvé.
- [ ] Connecter le fd de `OpenPipeWireRemote` avec `pw_context_connect_fd`, puis créer un stream sur le node autorisé. Ce fd désigne une connexion PipeWire, pas un framebuffer à `mmap`. Le schéma simplifié du document historique de capture ne doit pas être repris. [Contrat ScreenCast officiel](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.ScreenCast.html).
- [ ] Négocier formats, dimensions, stride, couleur et cadence ; recevoir les buffers via le stream. Livrer d’abord un chemin CPU/SHM avec ownership vérifié. DMA-BUF ne suit qu’avec preuve de mapping/synchronisation et représentation publique compatible. [Lifecycle des streams PipeWire](https://docs.pipewire.org/page_streams.html).
- [ ] Brancher `CapturePort`, les budgets de frames, backpressure, fermeture/release et cancellation avant/après handoff. Tester refus, fermeture du picker, restart du portal, node disparu et changement de format.
- [ ] Évaluer ensuite les protocoles compositor de capture pour les autres targets. Ils ne constituent ni une garantie universelle ni un fallback automatique au refus utilisateur du portal.

**Gate de sortie :** pixels d’une source contrôlée reçus par le vrai chemin portal/PipeWire, format/orientation/lifetime vérifiés, absence de fuite de fd et aucun buffer réutilisé après remise. Les variantes portal GNOME/KDE/wlroots déclarées supportées sont testées explicitement ; le seul Weston headless ne ferme pas cette phase.

### Phase 11 — Compatibilité, distribution et fermeture

**Dépendance :** toutes les décisions de support précédentes.

- [ ] Finaliser `capabilities/wayland.md` : une ligne par feature normative, minimum testé, compile gate, runtime gate, état absent exact, scénario et limite connue.
- [ ] Vérifier les familles de compositor visées : Weston pour le gate déterministe initial, puis Mutter, KWin et un compositor wlroots dans une matrice versionnée. Une extension absente doit produire son résultat attendu, pas un skip.
- [ ] Qualifier glibc/musl et les architectures réellement publiées, chargement paresseux hors Linux, erreurs de linkage, absence de portal et protocoles optionnels manquants.
- [ ] Compiler des consumers Kotlin/Java avec l’unique dépendance standard `kadre`, puis vérifier publication transitive, absence d’ancienne API et opt-ins interop.
- [ ] Exécuter stress de queues, slow collectors, budgets, interruption du serveur et teardown répété ; comparer les traces portables aux adapters déjà actifs.

**Gate de sortie :** couverture documentée et prouvée de chaque domaine, aucune capability « prévue » publiée `Supported`, CI reproductible et consommateurs exécutables. Une limite structurelle documentée peut fermer une ligne ; une branche simplement non implémentée ne vaut pas décision définitive de non-support.

## 6. Dépendances et jalons de livraison

```text
0 audit et décisions
└─ 1 host et preuve Linux
   └─ 2 fenêtres + surface minimale + interop
      └─ 3 metrics + outputs + scale + redraw
         ├─ 4 input
         │  ├─ 5 interactions + fenêtres avancées
         │  ├─ 6 IME + touch/gestures/pen
         │  ├─ 7 drop
         │  ├─ 8 embedded
         │  └─ 9 services/devices/raw (5 pour le chemin lock)
         └─ 10 capture portal/PipeWire
                         |
             toutes les branches → 11 fermeture
```

| Jalon | Résultat reviewable | Phases |
|---|---|---|
| M0 | Baseline, obstacles et frontières fermés | 0 |
| M1 | Host headless réel et gate Linux | 1 |
| M2 | Fenêtres et renderer externe contrôlé, metrics et redraw | 2–3 |
| M3 | Application desktop interactive avec support explicite par protocole | 4–7 |
| M4 | Intégrations host et services Linux qualifiés | 8–9 |
| M5 | Capture conforme avec choix utilisateur et frames réelles | 10 |
| M6 | Couverture contractuelle et distribution Wayland | 11 |

M4 et M5 peuvent progresser indépendamment après leurs prérequis. Aucune date ni charge n’est fixée avant l’audit KFFI et les décisions de phase 0 ; les phases sont des unités de livraison, pas des sprints de durée identique.

## 7. Preuves et stratégie de PRs

Chaque phase reçoit un design ciblé puis un plan détaillé. Une PR doit produire une frontière testable : extension runtime et oracle O2, binding KFFI dans son dépôt, puis adapter et oracle O3. Les spécifications, contrats actifs, mappings d’evidence, sentinelles et documentation de capability évoluent dans le même changement que leur activation.

Réutiliser les familles de contrats de `TEST-STRATEGY.md`, notamment `BCK`, `WIN`, `SUR`, `INP`, `DSP`, `GPD`, `CAP` et `INT`. Allouer les numéros au moment du plan en vérifiant le registre ; cette roadmap ne réserve pas de faux IDs `active` et n’introduit pas une famille Wayland sans décision normative.

Le validateur actuel distingue les producteurs et utilise notamment la target `jvm`. Le gate Wayland doit distinguer au minimum adapter, libc, compositor/version et protocoles testés, sans laisser une preuve AppKit `jvm` satisfaire un contrat Wayland. Faire évoluer le schéma/agrégateur avant activation si nécessaire ; empêcher toute fusion écrasant deux environnements dans le même artifact.

Les preuves obligatoires associent rapport JUnit, evidence contractuelle et commit exact. Un test absent, ignoré ou un compositor requis indisponible fait échouer le gate correspondant. Une branche `Unsupported` attendue est un test réussi explicite, pas un skip. Les watchdogs de CI interrompent un test bloqué ; ils n’ajoutent aucun timeout à l’API publique.

Le driver injecte de l’autre côté de la frontière Kadre : client de drop externe, compositor de test, service portal contrôlé, stream PipeWire ou environnement desktop réel. Un mock natif aide les tests O2 ; il ne suffit pas à activer l’adapter. Les tests manuels ciblent uniquement les frontières impossibles à rendre déterministes et ne remplacent pas l’automatisation du chemin logiciel.

Points de revue prioritaires et phases propriétaires :

| Risque | Preuve à obtenir |
|---|---|
| Lecture préparée abandonnée, flush saturé, wake perdu | phase 1 : `EINTR`/`EAGAIN`, stop pendant poll, wake répété et fd fermés |
| Configure ancien ou fermeture pendant création | phase 2 : serials, cancellation et outcome terminal unique |
| Double propriétaire du commit ou handle retenu | phases 0–3 : contrat renderer, traces de commits et course lease/close |
| Seat/focus/serial utilisé par une autre surface | phases 4–6 : deux surfaces, plusieurs seats, perte de focus et token invalide |
| Offer/buffer/fd survivant à son owner | phases 7 et 10 : interruption, révocation et comptabilité de release |

Respecter l’objectif CI existant : gate PR p95 ≤ 10 minutes, job logique Linux p95 ≤ 8 minutes. Le périmètre bloquant suit les contrats actifs ; les variantes supplémentaires et essais matériels sont placés dans les campagnes prévues par la stratégie, avec leurs résultats séparés.

## 8. Hors périmètre

- Écriture d’un compositor, d’un window manager, d’un renderer, de widgets ou de décorations dessinées par Kadre.
- Event loop, `wl_display`, seat, serial, fd ou queue Wayland ajoutés à l’API publique commune.
- Migration mécanique de `kadre-old`, réactivation de ses modules ou bindings FFM locaux.
- Reconnexion transparente, changement de backend après admission et fallback XWayland caché.
- Injection globale de clavier/souris, API clipboard, raccourcis globaux ou extension de l’API de capture sans contrat préalable.
- Promesse de support de tous les protocoles propriétaires, de tous les compositors ou d’un fullscreen exclusif universel.
- Version produit, date de release ou nombre de PRs imposé avant qualification des dépendances.

## 9. Checklist de fermeture

- [ ] Provider Wayland découvert sans charge native indue, sélection définitive et failures d’attach conformes.
- [ ] Standalone et chaque intégration embedded annoncée respectent thread, ownership et arrêt.
- [ ] Fenêtres, surfaces, redraw et renderer externe coopèrent sans concurrence de commits ni fuite de handles.
- [ ] Chaque propriété de fenêtre possède une autorité définie et des branches support/refus prouvées.
- [ ] Input, IME, interactions, drop, outputs, services, devices, gamepads et raw input ont une décision explicite par capability.
- [ ] Capture prouve le contrôle portal, le transport PipeWire, les pixels et le lifetime, séparément pour chaque target promise.
- [ ] Aucun inventaire partiel, focus, écran primaire, permission ou état effectif n’est fabriqué.
- [ ] Chaque contrat actif possède ses scénarios, sentinelles et preuves dans les environnements Linux requis.
- [ ] Les bindings restent dans KFFI ; aucun module vide ni duplication du runtime n’est introduit.
- [ ] Publications, consumers, matrice de compatibilité et limites documentées correspondent au code réellement livré.
