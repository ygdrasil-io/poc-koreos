# Kadre — Roadmap d’implémentation complète Android

**Statut :** proposition de roadmap architecturale ; aucune phase Android déclarée livrée par ce document. Les plans d’implémentation détaillés restent séparés par phase.

**Date :** 5 octobre 2026.

**Cible :** Android, avec attachement à une `ComponentActivity` ou à une `View` existante.

**Mode de livraison :** tranches verticales pilotées par le risque, chacune avec code, tests, preuves et mise à jour des capabilities.

Ce document organise l’implémentation Android dans le nouveau `kadre`. L’objectif est de permettre à une application hôte Kotlin ou Java d’attacher des sessions indépendantes, sans confier à Kadre la création de ses vues, sa navigation ou son rendu. L’intégration Compose reste optionnelle et possède uniquement la liaison de lifecycle.

Les autorités restent :

- [DESIGN.md](DESIGN.md), notamment §5, §7, §8 et §15.1, pour les invariants et les quatre overloads Android ;
- [PUBLIC-API-CATALOG.md](PUBLIC-API-CATALOG.md) pour les noms et types publics ;
- [OPERATION-CONTRACTS.md](OPERATION-CONTRACTS.md) pour les failures, outcomes et frontières de cancellation ;
- [BACKEND-CAPABILITIES.md](BACKEND-CAPABILITIES.md), notamment §3–6.1, pour la topologie et les disponibilités ;
- [INTEROP-EXPORTS.md](INTEROP-EXPORTS.md) pour la façade Java et `withAndroidView` ;
- [POLICY-PROFILES.md](POLICY-PROFILES.md) pour les budgets et stratégies de delivery ;
- [PROJECT-ARCHITECTURE.md](PROJECT-ARCHITECTURE.md) pour les projets, dépendances et publications ;
- [TEST-STRATEGY.md](TEST-STRATEGY.md) pour les oracles, preuves et gates CI ;
- [PLAN-SNAPSHOT.md](PLAN-SNAPSHOT.md), chantier 6, et [MIGRATION-AUDIT.md](MIGRATION-AUDIT.md) pour la migration de l’ancien Android.

Une contradiction ou un besoin d’API supplémentaire est résolu dans la spécification concernée avant l’implémentation dépendante. Cette roadmap ne crée ni nouvelle API publique, ni promesse de version ou de date de livraison.

## 1. Objectif et définition de la couverture complète

La couverture Android comprend l’attachement, le lifecycle, les surfaces et fenêtres hôtes, les interactions, l’input, le text input/IME, les drops, les displays, les devices/gamepads, la capture, les permissions, l’interop et le teardown.

Chaque fonctionnalité applicable reçoit une décision prouvée :

1. `Capability.Supported` avec contraintes et disponibilité runtime observées ;
2. indisponibilité temporaire portée par le state ou l’availability prévu ;
3. `Unsupported` stable si aucune primitive conforme n’est disponible pour ce host.

Une fonctionnalité encore à développer reste explicitement différée dans le suivi ; son fallback `Unsupported` provisoire ne suffit pas à déclarer Android complet. Une liste vide n’est un inventaire valide que si cet inventaire est réellement complet et vide. Une demande admise n’est jamais assimilée à un effet natif confirmé.

## 2. État initial constaté

Le point de départ est le nouveau build, pas l’ancien backend renommé.

| Élément | État au début de la roadmap | Conséquence |
|---|---|---|
| `settings.gradle.kts` | Aucun `platform:android` ni driver Android inclus | Ajouter chaque composant avec sa première responsabilité réelle |
| `kadre`, `foundation`, `runtime` | Targets JVM, JS et Wasm ; aucune target Android | Ajouter les variantes Android et vérifier leur publication transitive |
| Runtime commun | Sessions, lifecycle, input, interactions, text input, drops, displays, devices et capture déjà structurés autour de ports | Réutiliser les reducers et budgets existants |
| Runtime JVM | `RuntimeWindowManager`, `MinimalWindowSurface`, `WindowCommandPort`, `SurfaceCommandPort` et plusieurs `actual` dans `jvmMain` | Définir le partage utile avec Android sans importer les providers Desktop |
| Toolchain | Catalogue Kotlin `2.4.20`, AGP `9.0.0` ; convention historique `minSdk = 24`, `compileSdk = 35`, bytecode JVM 17 | Valeurs de départ à valider ensemble ; la toolchain Desktop JVM 25 ne fixe pas le bytecode Android |
| Contrats et CI | Registre et validateurs existants, preuves AppKit/Web ; aucun driver Android dans le nouveau build | Une preuve JVM/AppKit ne prouve pas l’exécution Android |
| `kadre-old/kadre-android` | Event loop, singleton `AndroidKadreRuntime.currentHandler`, tests host/device et capture historiques | Réservoir de cas de régression et de traductions SDK, jamais une dépendance du nouveau runtime |

Les commandes historiques du chantier 6 de `PLAN-SNAPSHOT.md` désignent l’ancienne arborescence. Elles ne constituent pas des commandes de validation du nouveau `:kadre:platform:android`.

## 3. Invariants globaux

### 3.1 Attachement et propriété du host

- La surface publique contient exactement les quatre overloads `attachKadre` de `DESIGN.md` §15.1, dans `org.graphiks.kadre.platform.android`. Aucun overload `Context`, `Application` ou singleton n’est ajouté.
- Toutes les formes exigent une `View` déjà attachée. La forme Activity exige en plus `surfaceView.rootView === window.decorView` ; aucune recherche ou création implicite de vue.
- Une Activity et une View ne possèdent chacune qu’une session active ; une View ne peut pas être revendiquée par deux chemins d’attachement. Le conflit retourne `AlreadyInUse(Host)` sans transfert d’ownership.
- Un `parentScope` sans `Job` retourne `InvalidRequest("parentScope")` ; un job inactif retourne `ParentScopeCancelled`. Une View invalide retourne le field exact `"view"` ou `"surfaceView"` selon l’overload.
- L’Activity expose sa fenêtre top-level et sa surface : `primarySurface` et `windows.state.value.primary?.surface` désignent la même instance. Le host View expose seulement `primarySurface`, avec `primary == null` et une liste de fenêtres vide.
- `requestWindow` produit une requête déjà `Rejected(Unsupported(RequestWindow))` dans les deux formes. Une nouvelle Activity est lancée par l’application hôte et reçoit une nouvelle session.
- Fermer la session retire les bridges Kadre ; cela ne signifie pas appeler implicitement `Activity.finish()`, retirer la View ou détruire une surface de rendu appartenant au host.

### 3.2 Lifecycle et isolation

`Foreground` signifie lifecycle au moins `STARTED` ; `Active` exige au moins `RESUMED` et une fenêtre interactive. Un `onPause` ou `onStop` n’est pas à lui seul une terminaison. Le premier détachement de la View ou la destruction du lifecycle owner est terminal, y compris si un scope externe reste vivant.

Le détachement publie le snapshot canonique `Detached + Background + Inactive` et propose `HostDetached`. Réinsérer la même View exige un nouvel attach. Une recréation de configuration crée une nouvelle session et de nouveaux jobs ; l’état durable reste une responsabilité de l’application.

Le registre d’ownership est indexé par identité native et ne conserve aucun « handler courant ». Un éventuel broker process-wide n’existe que pour une subscription SDK réellement partagée, retire ses abonnés au teardown et ne choisit jamais une session globale par défaut.

### 3.3 Frontière runtime et SDK

```text
Activity / View fournie par le host
        |
        v
platform:android : attach, lifecycle, callbacks et commandes SDK
        |
        v
ports internes et stimuli immuables
        |
        v
runtime : admission, reducers, policies, IDs, flows et outcomes
        |
        v
application Kotlin ; façade Java limitée à la session
```

Le runtime conserve la sérialisation, la concurrence structurée, les révisions et les budgets. L’adapter copie les données nécessaires des callbacks, applique les commandes sur le thread requis par Android et rapporte le résultat effectif. `MotionEvent`, `KeyEvent`, `ClipData`, `Image` ou `MediaProjection` ne traversent pas les flows publics.

Le state est publié avant l’événement associé. Les callbacks ordinaires ne suspendent pas et ne lancent pas directement le code applicatif. Les interactions synchrones explicitement prévues utilisent le port commun dans la frame du callback, avec containment des exceptions et révocation de l’autorité à la sortie.

Le contrat du thread d’appel d’`attachKadre`, qui est non suspendu, doit être fixé en phase 0 : aucun blocage main-thread ni relocation implicite ne sera introduit pour contourner une ambiguïté. Le dispatcher applicatif reste celui du `parentScope` ; il ne donne pas le droit d’appeler le SDK UI depuis n’importe quel thread.

### 3.4 Surfaces, accès SDK et permissions

La `HostSurface` représente la View hôte. La disparition d’une surface de rendu `SurfaceView`/`TextureView` alors que la View reste attachée ne vaut pas automatiquement détachement du host. Le renderer continue de posséder ses buffers et son protocole de recréation.

`HostSurface.withAndroidView` fournit un accès borné à la View sur le thread host, avec `@KadrePlatformApi` et `@DelicateKadreApi`. L’objet natif ne devient pas un handle public durable. `WindowCapabilities.platformAccess` reste `Unsupported(PlatformWindowAccess)` sur Android.

Aucune lecture de state, énumération ou installation d’attach ne déclenche implicitement un consentement utilisateur. Une capability dépendant du host Activity peut rester absente sur un host View sans autorité correspondante. Aucun cast heuristique de `view.context` ne crée cette autorité.

## 4. Décisions à fermer avant les phases dépendantes

Ces points sont des décisions de conception localisées, pas des raisons de retarder les phases indépendantes.

| Décision | Livrable exigé | Échéance |
|---|---|---|
| Variantes Android et partage du code JVM | Compilation Android réelle, dépendances sans Desktop/KFFI, bytecode et APIs Java compatibles avec le plancher retenu | Phase 0 |
| Thread d’attach et propriété des callbacks | Précondition et failure admise documentées ; protocole d’installation/retrait qui préserve le comportement du host | Thread d’attach en phase 0 ; callbacks avant leur installation dans chaque domaine |
| Fenêtre de l’Activity | Sémantique explicite de `Window.close`, des demandes de fermeture et du bouton Back ; distinction avec l’arrêt de session | Avant les commandes de phase 4 |
| IME sur View fournie | Preuve d’un bridge conforme pour les classes de View retenues ; sinon capability absente et limite documentée | Phase 5 |
| Consentement et service de capture | Enregistrement Activity Result compatible avec un attach tardif, ownership du service et de la notification, corrélation des résultats après recréation | Avant la capture de phase 8 |
| Intégration Compose | Contrat de liaison à la View exacte, API cataloguée si une API publique est nécessaire | Phase 9 |

L’approche recommandée réutilise le runtime commun avec des adapters SDK localisés. Copier l’ancienne event loop conserverait ses problèmes d’ownership ; traiter Android comme un backend Desktop importerait une topologie et une dépendance JVM 25 inadaptées. Un partage interne JVM/Android reste possible après audit des fichiers concernés.

## 5. Phases de livraison

Chaque phase commence par un plan ciblé qui nomme les fichiers, les signatures internes retenues et les tests. Elle se termine avec les mêmes scénarios exécutés contre la frontière réelle pertinente, les sentinelles associées et la mise à jour de `capabilities/android.md`. Les noms de fichiers nouveaux ci-dessous sont des destinations proposées, pas des composants déjà disponibles.

### Phase 0 — Variante Android et première preuve sur émulateur

**Objectif :** fermer le risque de build, de publication et de liaison au runtime avec une première tranche utilisable.

**Fichiers :** `settings.gradle.kts`, `gradle/libs.versions.toml`, les builds de `kadre`, `foundation` et `runtime`, puis les nouveaux `platform/android/build.gradle.kts` et `contracts/driver/android/build.gradle.kts`. Les chemins sous `kadre/` sont relatifs à ce répertoire.

- [ ] Ajouter les targets Android nécessaires avec `com.android.kotlin.multiplatform.library` et le bloc `kotlin { android { … } }`. Garder les applications de test dans un module Android application distinct, sans combiner les plugins application et KMP dans ce module.
- [ ] Partir de `minSdk = 24`, `compileSdk = 35` et JVM 17 présents dans la convention ; vérifier la compatibilité de toutes les dépendances avant de les fixer pour le nouveau module. Toute hausse de plancher est une décision explicite, pas un effet secondaire d’une dépendance.
- [ ] Fournir les `actual` Android de `RuntimeLock`, `IdentityKeyedMap`, `InteractionCallFrame` et `ThrowableClassification`. Auditer le code fenêtre aujourd’hui dans `jvmMain` ; partager seulement les éléments nécessaires, sans `desktop/DesktopBackendProvider` ni FFM.
- [ ] Activer explicitement `androidHostTest` et `androidDeviceTest`, ainsi que les ressources ou Java sources seulement lorsque nécessaires. La documentation du [plugin Android KMP](https://developer.android.com/kotlin/multiplatform/plugin) décrit ces opt-ins.
- [ ] Livrer un attach View minimal réel, avec arrêt et cleanup, dans le nouveau driver ; les garanties encore absentes restent différées. Ajouter le consumer Android qui résout l’umbrella publié dans le repository temporaire.
- [ ] Ajouter le producteur de preuves Android et son mapping JSON/JUnit au validateur, avec une première ligne contractuelle de portée limitée. Aucun ID Web/AppKit n’est réutilisé pour masquer une preuve Android absente.

**Gate de sortie :** le consumer compile et s’exécute sur un émulateur identifié ; il démarre puis termine une session View. Sa résolution transitive n’inclut aucun backend Desktop ni KFFI. Les preuves Android manquantes font échouer le gate dès l’activation du premier contrat.

### Phase 1 — Attachement public, ownership et lifecycle

**Objectif :** rendre les quatre points d’entrée sûrs pour plusieurs hosts et pour leur recréation.

**Fichiers :** `platform/android/src/androidMain/kotlin/org/graphiks/kadre/platform/android/AndroidAttach.kt`, puis des fichiers internes séparés pour l’ownership, le host et le lifecycle ; leurs tests dans `androidHostTest` et le driver Android.

- [ ] Implémenter les quatre overloads exacts, la validation du scope et de la View, et le refus d’un lifecycle owner déjà détruit selon les failures d’attach admises.
- [ ] Réserver atomiquement les identités Activity/View ; restaurer entièrement l’état en cas d’échec partiel et ne libérer l’ownership vivant qu’à la terminaison effective.
- [ ] Observer lifecycle, attachement et interactivité ; relire les préconditions après installation pour couvrir la course validation/détachement.
- [ ] Fournir les deux topologies promises, dont la fenêtre Activity minimale et sa surface partagée ; conserver `requestWindow` rejeté dans les deux cas.
- [ ] Dédupliquer les notifications, fermer l’admission dès le signal terminal et retirer listeners, observers, callbacks main-thread et références au host au teardown.

**Gate de sortie :** tests de View détachée, mauvais decor, double attach Activity/View croisé, parent annulé, scope survivant, detach/reparent et recréation. Deux Activities possèdent deux sessions indépendantes ; détruire l’une ne modifie ni les IDs, ni le lifecycle, ni les jobs de l’autre. Les courses arrêt/destroy/exception respectent la priorité du runtime.

### Phase 2 — Surface, metrics, redraw et accès natif

**Objectif :** fournir une surface observable sans posséder son rendu.

**Fichiers :** peers de surface et de scheduling dans `platform/android/src/androidMain`, extension `withAndroidView`, tests de surface du driver et consumers Kotlin.

- [ ] Publier dimensions logiques/physiques, densité, insets, visibilité, focus et appearance à partir d’observations SDK cohérentes ; conserver `Unknown` lorsqu’une observation manque.
- [ ] Mapper `requestRedraw` sur l’invalidation/scheduling Android, avec coalescing et cancellation au teardown ; aucun renderer ou event loop public ajouté.
- [ ] Distinguer rotation, redimensionnement multi-window, changement de densité et recréation des surfaces de rendu. Utiliser une génération interne pour rejeter les callbacks tardifs d’une ressource remplacée.
- [ ] Implémenter `withAndroidView` et ses frontières de cancellation : aucun callback après fermeture ou cancellation précoce, callback commencé achevé sur le thread host, exception applicative propagée telle quelle.

**Gate de sortie :** metrics comparées à la View réelle ; identité de surface conservée tant que le host demeure attaché ; redraw borné sans callback zombie ; zéro accès SDK après fermeture. Le consumer exige les deux opt-ins et n’obtient aucun handle `android.view.Window` générique.

### Phase 3 — Clavier, pointeur, touch et stylet

**Objectif :** acheminer l’input ordinaire vers les reducers existants sans perturber l’application hôte.

**Fichiers :** bridge d’input Android, mappers de touches/coordonnées et tests dédiés ; `SurfaceStimulus` et `RuntimeSurfaceInput` seulement lorsqu’un manque portable est démontré.

- [ ] Définir un mécanisme d’observation compatible avec les callbacks déjà installés par le host ; aucun remplacement silencieux de listener, aucun accès réflexif privé pour récupérer l’ancien handler.
- [ ] Mapper touches physiques/logiques, modifiers, repeat, boutons, hover, scroll, contacts multiples et données pen effectivement observables.
- [ ] Copier les payloads avant recyclage SDK ; préserver les identités de contacts pendant un geste et traiter `ACTION_CANCEL`, perte de focus et déconnexion.
- [ ] Appliquer les policies de delivery, overflow et default behavior du runtime. Ne pas déduire l’IME ou du raw input global des seuls événements clavier/pointeur.
- [ ] Publier les capabilities après installation effective du chemin d’observation, avec disponibilité selon focus et matériel.

**Gate de sortie :** événements injectés par la frontière Android, puis assertions sur state et events publics. Scénarios multi-touch, stylet, souris, modifiers, focus loss et overflow ; aucun double delivery, aucune touche/contact restant actif après reset, aucune fuite entre sessions. La consommation de l’événement respecte la policy et le host.

### Phase 4 — Fenêtre Activity et interactions transitoires

**Objectif :** exposer seulement les commandes de fenêtre et de surface que le host et Android peuvent honorer.

**Fichiers :** peer de fenêtre Activity, ports de commandes surface/fenêtre et bridge d’interactions dans `platform/android` ; réutilisation ou adaptation ciblée du runtime fenêtre.

- [ ] Décliner une table par champ : title, géométrie, resize, fullscreen, décorations, boutons, niveau, transparence, blur, icône et content protection. Documenter les champs imposés par Android ou la configuration du host comme tels.
- [ ] Implémenter les mutations supportées avec readback effectif, révisions et `Applied`/`PartiallyApplied`/`Accepted` exacts. Une position de bureau ou un fullscreen exclusif sans primitive conforme reste explicitement non supporté.
- [ ] Fermer la décision `Window.close`/Back avant son code ; préserver la navigation hôte et ne jamais assimiler `session.close()` à une demande de fermeture native.
- [ ] Ajouter cursor, pointer capture et interactions uniquement lorsque leurs préconditions et leur autorité peuvent être prouvées. Le retour d’une requête SDK n’est pas la confirmation de la capture du pointeur.
- [ ] Garder `InteractionAction.OpenWindow` et `requestWindow` conformes à l’absence de création de seconde Activity. Respecter la durée de validité des tokens d’interaction et le subset exact des actions disponibles.

**Gate de sortie :** Activity et View ont des capabilities distinctes ; les champs refusés n’appellent aucune primitive native ; les effets acceptés sont corrélés à leur observation ou outcome. Les tests couvrent révocation, callback tardif, destruction pendant une commande et cancellation avant/après commit.

### Phase 5 — Text input, IME et gestures

**Objectif :** relier les sessions de texte au host réel sans imposer un widget Kadre.

**Fichiers :** port Android de text input, bridge IME et recognizers retenus ; tests du port et cahier manuel du driver.

- [ ] Prouver d’abord le mécanisme d’intégration sur la View fournie. L’existence d’`InputMethodManager` ne permet pas à elle seule d’installer une `InputConnection` sur toute View ; qualifier les classes de hosts réellement compatibles.
- [ ] Si la forme d’attach actuelle ne permet pas une intégration conforme, documenter la capability absente ou proposer une modification normative avant tout nouveau hook public. Ne pas créer un champ de texte caché ou remplacer la View implicitement.
- [ ] Pour les hosts compatibles, mapper composition, commit, sélection, suppression, actions IME et surrounding text vers `TextInputPort`/`RuntimeTextInputSession`, avec indices UTF-16 et révisions vérifiés. Android passe ces échanges par [`InputConnection`](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method).
- [ ] Couvrir clavier logiciel/matériel, mise à jour du curseur, focus loss, suspension et fermeture ; éviter qu’un commit IME soit également publié comme saisie déduite de `KeyEvent`.
- [ ] Activer seulement les gestures reconnues par le host ou par un recognizer explicitement installé. Sans recognizer conforme, conserver gestures non supportées indépendamment du support touch.

**Gate de sortie :** composition multi-étapes, Unicode, sélection, révision périmée et changement de focus ont des résultats déterministes ; aucun éditeur ni callback ne survit au teardown. L’IME système réel et les gestures matérielles ont une procédure manuelle ciblée en complément des tests automatisés.

### Phase 6 — Drag-and-drop et ownership des transferts

**Objectif :** recevoir les drops Android avec un transfert de ressources explicite et borné.

**Fichiers :** port de drop Android, conversion des descripteurs et gestion des permissions URI ; scénarios instrumentés du driver.

- [ ] Traduire les événements de drag vers offers communes ; n’exposer que des snapshots copiés, sans `DragEvent` ou `ClipData` public.
- [ ] Relier l’acceptation au contrat d’interaction synchrone et au claim unique du `DropTransfer`.
- [ ] Acquérir les permissions nécessaires au bon moment, borner les lectures via `ContentResolver`, fermer streams/descripteurs et relâcher les grants avec l’owner.
- [ ] Traiter fin de drag, refus, cancellation pendant lecture, taille inconnue, dépassement de budget et fermeture du host. Une permission absente sur un host View ne devient pas un faux drop lisible.

**Gate de sortie :** deux claimants ne lisent pas la même offre ; chaque ressource est libérée exactement une fois ; lectures et résultats suivent les failures admises par l’opération. Les tests incluent un transfert réel et la sentinelle qui détecte une permission ou un stream conservé après fermeture.

### Phase 7 — Displays, devices, gamepads et pression mémoire

**Objectif :** fournir des inventaires honnêtes et des subscriptions isolées.

**Fichiers :** ports Android `DisplayPort`, `InputDevicePort`, `GamepadPort` et éventuel broker mémoire spécialisé.

- [ ] Observer displays, modes et changements d’affichage avec IDs stables pendant leur durée de vie ; distinguer display, fenêtre Activity et viewport de la View. Ne pas inventer une position commune de bureau à partir de dimensions locales.
- [ ] Publier un inventaire complet seulement s’il est effectivement observable ; sinon utiliser le fallback `HostViewport` ou le state d’indisponibilité admis, selon les données disponibles.
- [ ] Observer connexion/retrait des périphériques, router les gamepads vers la session selon le contrat commun et traiter axes, boutons, focus et neutralisation à la déconnexion.
- [ ] Déclarer les effets gamepad selon le périphérique réellement ciblable ; le vibreur du téléphone ne simule pas les moteurs d’une manette.
- [ ] Décider séparément du raw input : ne pas présenter les événements de la View comme une observation globale. Sans bridge conforme, conserver `Unsupported(RawInputAccess)` et sa preuve de non-appel.
- [ ] Mapper uniquement les notifications mémoire réellement émises et observables sur les versions retenues ; une perte de visibilité ne devient pas une pression mémoire. Le retrait d’une session ne coupe pas les autres abonnés.

**Gate de sortie :** hot-plug, déconnexion pendant effet, changement de display et fermeture concurrente ne laissent ni état pressé, ni effet, ni subscription orpheline. Les cas sans matériel disposent de résultats de capability testés ; les garanties matérielles passent sur le pool de devices déclaré supporté.

### Phase 8 — Capture, consentement et frames

**Objectif :** implémenter des chemins de capture distincts, avec une autorité et une durée de vie explicites.

**Fichiers :** ports et owners de capture Android, driver de consentement, manifest/service seulement après décision de responsabilité, tests des frames et cahier manuel.

- [ ] Distinguer `CaptureTarget.Surface`, `HostChoice` et `Source`. Un inventaire de displays n’est pas un inventaire de fenêtres capturables ; utiliser `HostPickerOnly` lorsque le système choisit la cible.
- [ ] Prouver le chemin de consentement après un attach potentiellement tardif. Le code historique enregistre son launcher dans `requestPermission()` ; ne pas recopier ce schéma sans traiter les contraintes de lifecycle des [Activity Result APIs](https://developer.android.com/training/basics/intents/result).
- [ ] Définir la responsabilité du service, de son arrêt et de sa notification avant activation. Le host View sans autorité de lancement n’hérite pas automatiquement de la capacité de l’Activity.
- [ ] Pour MediaProjection, obtenir un consentement par session, consommer le token une seule fois, respecter les exigences du foreground service `mediaProjection` et traiter `onStop()`. Un résultat tardif d’une ancienne Activity ne peut pas autoriser la session recréée. Ces contraintes sont décrites dans le guide [Media projection](https://developer.android.com/media/grow/media-projection).
- [ ] Évaluer séparément la capture de la surface hôte, par exemple via les primitives de copie adaptées à son type. Une copie de View ne garantit pas la capture de buffers de rendu indépendants ou de contenu protégé.
- [ ] Brancher `CapturePort`, `RuntimeCaptureManager` et `RuntimeCaptureSession` : formats, strides, tailles, rotation, horodatage, leases, copies et backpressure bornés. Libérer chaque `Image`, `ImageReader`, virtual display et subscription avec son owner.

**Gate de sortie :** refus, annulation du picker, révocation système, source perdue, rotation et fermeture pendant une frame produisent les states/outcomes exacts. Aucun nouveau consentement depuis une lecture de state, aucune réutilisation de token, aucune frame après stop et aucun buffer accessible au-delà de sa lease. Les scénarios avec consentement réel complètent les tests déterministes de contrôle et de ressources.

### Phase 9 — Façade Java, Compose et migration des samples

**Objectif :** rendre le support consommable depuis des applications indépendantes du build Kadre.

**Fichiers :** façade `org.graphiks.kadre.host.android` dans `platform/android`, fixtures autonomes sous `consumers/kotlin` et `consumers/java`, `integration/compose` uniquement avec son implémentation effective, nouveaux samples Android.

- [ ] Livrer les deux `KadreAndroid.attach` et `KadreSessionHandle` de `INTEROP-EXPORTS.md` : snapshot, observer closeable, arrêt et `CompletionStage` terminal, sans fuite de `Continuation` ou de flow dans la façade promise.
- [ ] Vérifier les observers immédiats puis séquentiels, leur désinscription en cas d’exception, l’identité du stage terminal et la cancellation du waiter sans arrêt de session. Vérifier les APIs Java disponibles au `minSdk` retenu et le desugaring nécessaire dans le consumer.
- [ ] Ajouter la liaison Compose : attendre l’attachement effectif de la View choisie, garder une session pour ce host, fermer à sa sortie, recréer après détachement terminal. Une recomposition simple ne crée pas de session supplémentaire.
- [ ] Migrer les usages de `hello-window-android`, `hello-touch-android`, `hello-triangle-android-capture`, du sample touch partagé et de Compose à partir de `kadre-old/samples`. Les renderers éventuels restent une responsabilité des samples.
- [ ] Retirer les dépendances à `EventLoop`, `AndroidKadreRuntime.currentHandler` et aux anciens types de fenêtre/capture des nouveaux chemins ; ajouter les consumers négatifs de migration.

**Gate de sortie :** Kotlin et Java compilent contre les artifacts publiés temporairement et exécutent les deux formes de host ; Compose prouve absence de double attach et de fuite après disposal/recréation. Tous les usages Android historiques sont migrés ou leur retrait est explicitement documenté, sans dépendance cachée à `kadre-old`.

### Phase 10 — CI complète, compatibilité et fermeture Android

**Objectif :** rendre la couverture Android vérifiable à chaque livraison.

**Fichiers :** workflow Android, scripts de sélection device et watchdog, driver Android, validateur, registre contractuel et `capabilities/android.md`.

- [ ] Installer le job logique `android-contracts` et le relier à l’aggregate `kadre-pr-contracts` prévu par `TEST-STRATEGY.md`, avec un émulateur épinglé et des artifacts identifiés par commit/environnement.
- [ ] Réutiliser les cas de sélection d’appareil des scripts historiques en les adaptant au nouveau build : choix explicite du serial, refus d’ambiguïté, appareil offline, boot incomplet, timeout et arrêt des processus enfants.
- [ ] Exécuter les host tests et le noyau réel lifecycle/View/input à chaque PR ; étendre le nightly aux versions minimum et courante retenues ainsi qu’aux scénarios permission/matériel.
- [ ] Vérifier publication, ABI et consumers Kotlin/Java ; auditer chaque ligne du catalogue applicable à Android avec sa capability, son contrat, sa preuve et ses limites.
- [ ] Mesurer fuites d’Activity/View, jobs, listeners, callbacks Choreographer, buffers et services sur des cycles répétés d’attach, background, recréation et fermeture.

**Gate de sortie :** aucun contrat actif sans JSON et JUnit corrélés, aucun skip/retry qui transforme une absence de preuve en succès. Les budgets normatifs restent p95 ≤ 8 minutes par job, timeout hard 15 minutes et aggregate PR p95 ≤ 10 minutes. La release exige le commit exact validé et le nightly complet vert de moins de 24 heures ; une capability non exerçable sur émulateur exige la preuve device physique prévue par la stratégie.

## 6. Stratégie de preuve et commandes

Les tests host couvrent validations, mappers, machines à états, budgets, races et teardown déterministes. Ils ne remplacent pas l’oracle O3 du driver Android : celui-ci provoque un événement depuis le SDK, l’instrumentation ou le système, puis observe les APIs Kadre publiques. Un appel direct au mapper testé ne prouve pas le bridge.

Chaque activation contractuelle ajoute dans le même changement : ligne du registre, scénarios, sentinelles, mapping vers les testcases JUnit, production JSON et validation. Le protocole Android doit distinguer API level, ABI, appareil/émulateur et type de suite, afin que deux exécutions ne s’écrasent pas. Les IDs Android nouveaux sont alloués dans le registre au moment de leur introduction ; les IDs communs ne changent de `requiredTargets` qu’avec leurs preuves Android effectives.

Les points d’entrée suivants sont à livrer progressivement : vérification du module, consumer Kotlin, script instrumenté et validation des preuves dès la phase 0 ; consumer Java complet en phase 9. **Les nouvelles commandes Android sont proposées ; elles ne sont pas disponibles dans le build actuel.**

```bash
rtk ./gradlew :kadre:platform:android:check
rtk ./gradlew :kadre:validateAndroidKotlinConsumer :kadre:validateAndroidJavaConsumer
rtk scripts/test-kadre-android-contracts.sh --serial <serial>
rtk ./gradlew :kadre:contracts:validator:check
```

Le script instrumenté assemble, installe, exécute et collecte les rapports avec watchdog ; `check` seul ne doit pas être présenté comme une exécution sur appareil. Les tâches AGP exactes sont relevées avec `tasks --all` une fois la variante créée, puis encapsulées par ce script. Aucun ancien nom de tâche Android n’est supposé valide.

Les preuves manuelles ciblent IME système, gestures matérielles, périphériques externes, multi-window réel et consentement/capture. Elles complètent les tests automatisés ; elles ne remplacent jamais un invariant déterministe ou une preuve obligatoire du gate PR.

## 7. Dépendances et jalons de revue

```text
0 Build, publication et attach View réel
  -> 1 Attach Activity/View, ownership et lifecycle
     -> 2 Surface, redraw et interop native
        -> 3 Input essentiel
           -> 4 Fenêtre Activity et interactions
              -> 5 IME et gestures
              -> 6 Drag-and-drop
        -> 7 Displays, devices et mémoire ; gamepads après 3
        -> 8 Capture, après décision de consentement et 4 si interaction requise
     -> 9 Façade Java, puis Compose et migration selon les domaines utilisés
Tous les domaines -> 10 Audit de couverture et fermeture
```

La phase 0 apporte l’infrastructure de preuve ; la phase 10 la complète. Il n’est pas nécessaire d’attendre toute la roadmap pour disposer d’un gate Android réel. Displays/devices, IME/drop et capture peuvent avancer séparément après stabilisation des interfaces qu’ils consomment, sans dupliquer le scheduler, le lifecycle ou le registre d’ownership.

Trois jalons permettent une revue utile :

- **Socle Android utilisable :** phases 0–3, avec contrat d’attachement fermé, surface observable et input minimal sur un vrai host.
- **Couverture fonctionnelle qualifiée :** phases 4–8, chaque domaine soutenu par une preuve ou une absence structurelle explicitement justifiée.
- **Support consommable et maintenable :** phases 9–10, artifacts Kotlin/Java, intégration Compose, migration et matrices CI/device vérifiés.

Chaque revue vérifie d’abord la propriété des ressources hôtes, les courses de terminaison, l’absence de callbacks zombies, les outcomes exacts et la préservation des autres sessions. Une proposition de nouveau type public impose une revue normative avant son code.

## 8. Hors périmètre

- Renderer, widgets, layout, scène graphique et choix de moteur GPU.
- Création implicite d’Activity, de View ou d’une hiérarchie de vues.
- Navigation, restauration durable ou conservation de jobs entre deux hosts recréés.
- Event loop publique Android, singleton applicatif ou compatibilité silencieuse avec `runApp`.
- Nouveau module fonctionnel `input`, `capture`, `gamepad` ou `ffi` ; le SDK Android suffit aux chemins proposés, tout besoin natif supplémentaire doit respecter la frontière KFFI.
- Support universel de toutes les Views pour l’IME, de tous les modes de capture ou de toutes les manettes sans preuve correspondante.
- Calendrier, version de release et suppression générale de `kadre-old` au-delà des chemins de migration Android.

## 9. Checklist de fermeture

- [ ] Les variantes Android se résolvent depuis l’unique dépendance publique `org.graphiks.kadre:kadre`.
- [ ] Les quatre overloads Kotlin et les deux overloads Java respectent leurs signatures et failures.
- [ ] Les topologies Activity/View, le refus de seconde fenêtre et l’accès borné à la View sont prouvés.
- [ ] Lifecycle, double attach, reparenting, recréation et isolation de deux Activities sont couverts sur Android.
- [ ] Surface/redraw, input, fenêtres, interactions, IME, drop, displays, devices, gamepads, mémoire et capture ont chacun une décision documentée et testée.
- [ ] Aucun différé d’implémentation n’est présenté comme une absence structurelle définitive.
- [ ] Teardown, cancellation et callbacks tardifs ne conservent aucune ressource ni autorité de l’ancien host.
- [ ] Consentement de capture, durée de vie des tokens, frames et services ont des propriétaires et des tests explicites.
- [ ] Compose et les usages Android des samples ont migré sans singleton ni renderer Kadre.
- [ ] Les preuves JSON/JUnit, consumers publiés, matrices PR/nightly et validations physiques requises sont complètes.
