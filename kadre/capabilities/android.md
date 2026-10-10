# Registre versionné d’implémentation — adapter Android (`org.graphiks.kadre.platform.android`)

**Mandat.** `BACKEND-CAPABILITIES.md` §8 impose à chaque adapter de produire, avec son
implémentation, un fichier public `capabilities/<adapter>.md` contenant exactement une ligne par
feature de la matrice de sa section 4, avec les colonnes `feature`, `target`, `minimum déclaré`,
`compile gate`, `runtime gate`, `état absent` et `tests`.

**Deuxième édition, phase 1.** Ce registre couvre désormais ce que la phase 1 de la roadmap
(`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 1 — Attachement public, ownership et lifecycle`) livre —
les quatre overloads publics de `DESIGN.md` §15.1 retournant `KadreResult<KadreSession>`, l’échelle
d’admission complète, la réservation atomique des identités avec rollback intégral, l’observation
lifecycle publiée au runtime avec détachement terminal, les deux topologies et le `requestWindow`
rejeté — et il **remplace** la ligne de l’attach View minimal de phase 0 (l’API jetante de phase 0
n’existe plus ; les scénarios `AND-001` restent verts contre la surface publique nouvelle, mêmes
noms, mécanisme migré). Il liste **différées de phase** (§4) toutes les features de la matrice §4
applicables à Android que les phases suivantes possèdent. Chaque phase ultérieure réécrit les
lignes qu’elle livre, comme le registre Web l’a fait phase après phase. Une feature différée n’est
**jamais** une « absence structurelle » : la matrice normative n’est pas rouverte, aucune capability
finale n’est décidée avant sa phase, et la colonne `état absent` des lignes livrées décrit le refus
exact observé, pas un verdict sur l’adapter.

**Portée de lecture.** Un seul target publie cette variante : `androidJvm` — l’attribut
`org.jetbrains.kotlin.platform.type = androidJvm` des variantes AGP-KMP des modules publiés
(`kadre`, `kadre-android`, `foundation`, `foundation-android`, `runtime`, `runtime-android`,
`kadre-platform-android`). La toolchain est celle de la convention du dépôt : `compileSdk = 35`,
`minSdk = 24`, bytecode JVM 17 (`kadre/platform/android/build.gradle.kts`, miroir de l’umbrella) ;
le compilateur Kotlin est celui du catalogue (2.4.20) et le consumer épinglé du driver le lit avec
KGP 2.4.0. Le moteur de la preuve est l’émulateur **AVD `Kadre_API_35`** (image système
**arm64-v8a**, **API 35**), étiqueté par le driver depuis le device réellement attaché —
`adb shell getprop ro.build.version.sdk` + `ro.product.cpu.abi`, jamais codé en dur — ce qui donne
l’identifiant exact **`emulator-api-35-arm64-v8a`** (`AND-001.json` et `AND-002.json`, champ
`adapter: "android-emulator"`). Aucun autre moteur n’est déclaré, et un moteur non relevé ne se
déduit pas d’un passage manuel. Cette passe de preuve a été produite sur un hôte **macOS arm64**,
qui est le contexte d’exécution de la preuve et non un minimum : la gate CI obligatoire (job
`android-contracts`) est la phase 10 de la roadmap et n’existe pas encore ; le plancher déclaré de
ce registre est l’API level (`minSdk = 24`), l’OS de l’hôte n’y entre pas.

## 1. Lecture des colonnes

- **feature** — le nom du membre public, tel que la surface de phase 1 le déclare (les quatre
  overloads `attachKadre` de `KadreAndroidAttach.kt`) ou la ligne de la matrice §4 concernée.
- **target** — `androidJvm`, le seul target de cette variante ; les `actual` Android du runtime
  (`RuntimeLock`, `IdentityKeyedMap`, `InteractionCallFrame`, `ThrowableClassification`) vivent
  sous le même target et n’ouvrent aucune seconde déclinaison.
- **minimum déclaré** — l’API level réellement exercé : **API 24** (`minSdk = 24`), l’émulateur de
  preuve tournant API 35. Kadre ne déclare aucune version au-delà de ce plancher ; l’OS hôte de la
  passe de preuve (macOS arm64) appartient à la « Portée de lecture », pas à ce minimum.
- **compile gate** — la tâche de compilation ou le symbole SDK dont la compilation dépend.
  `compileAndroidMain` est la compilation `androidMain` de `:kadre:platform:android` (AGP 9.0.0
  nomme ainsi les compilations KMP-Android — relevé `tasks --all`, wiring friend documenté dans
  `kadre/platform/android/build.gradle.kts`) ; `none` signifie qu’aucun symbole conditionnel
  n’est requis au-delà des types SDK déjà lus par la ligne.
- **runtime gate** — le device, le thread ou l’état du host dont dépend la *publication de la
  capability*. Pour la phase 1 c’est l’émulateur identifié : la preuve `AND-001` **et** `AND-002`
  n’existe que branchée à un device réel (`androidContractsCheck` exige un émulateur démarré et
  n’est volontairement pas dans `:kadre:check` — aucun émulateur sur les runners actuels,
  branchement CI = phase 10 ; les deux JSON produits par le même run instrumenté).
- **état absent** — la valeur exacte observée quand la feature est refusée, sans paraphrase. La
  phase 1 publie la taxonomie complète : la violation de thread reste une erreur de programmation
  `IllegalStateException` levée avant toute mutation d’état (décision de phase 0, §3 ci-dessous) et
  n’est **pas** un `KadreResult` ; toute autre admission refusée est un `KadreResult.Failure` sans
  exception, dans l’ordre normatif de l’échelle — `InvalidRequest("parentScope")` (scope sans
  `Job`), `ParentScopeCancelled` (job inactif), `InvalidRequest("view")` (forme View non attachée)
  / `InvalidRequest("surfaceView")` (forme Activity non attachée ou `rootView !== decorView`),
  `Closed(Host)` (owner détruit), `AlreadyInUse(Host)` (identité Activity ou View déjà
  revendiquée). Pour une feature **différée de phase**, la colonne ne décrit aucune valeur
  publiée — il n’existe aucun chemin public qui la lise à ce stade — et renvoie à la phase
  propriétaire (§4) : « différée » est un état de livraison de la roadmap, jamais une capability
  `Unsupported` décidée.
- **tests** — les identifiants de tests qui portent la ligne, en deux familles : les
  `evidenceId` de `kadre/contracts/driver/android/contracts/evidence.tsv`, c’est-à-dire des
  scénarios instrumentés réellement exécutés sur l’émulateur — `AND-001` sur la classe
  `KadreConsumerSessionTest` (3 scénarios, 2 sentinelles) et `AND-002` sur
  `KadreConsumerAttachContractTest` (6 scénarios, 2 sentinelles), résolution de l’umbrella
  **publié** `org.graphiks.kadre:kadre` depuis le repository contractuel ; puis les classes d’hôte
  `androidHostTest` (sans device) — `AndroidAttachAdmissionTest`, `AndroidAttachReservationTest`,
  `AndroidLifecycleReducerTest`, `AndroidHostLifecyclePropagationTest`,
  `AndroidHostSessionResultTest`, `AndroidWindowTopologyTest` — qui asservissent l’échelle, la
  réservation, le réducteur lifecycle, la propagation au runtime et les topologies.

## 2. Registre — phase 1

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| `attachKadre` sur `ComponentActivity` — les deux overloads de `DESIGN.md` §15.1 (`applicationFactory` et `application`), `surfaceView` explicite, `parentScope` défaut `lifecycleScope`, retour `KadreResult<KadreSession>` | `androidJvm` | API 24 | `:kadre:platform:android:compileAndroidMain` — lit `android.view.View`, `android.os.Looper`, `androidx.activity.ComponentActivity`, `androidx.lifecycle.*`, `kotlinx.coroutines.*` ; friend-wiring des classes Android de `foundation` et `runtime` | `AND-001` + `AND-002` / `androidContractsCheck` — un émulateur identifié (`emulator-api-35-arm64-v8a`) doit être démarré ; la preuve est produite à HEAD publié (`--refresh-dependencies`) | hors thread principal = `IllegalStateException("attachKadre must be called on the Android main thread")` avant toute mutation ; View non attachée à une fenêtre ou `surfaceView.rootView !== window.decorView` = `KadreResult.Failure(InvalidRequest("surfaceView"))` ; owner détruit = `Failure(Closed(Host))` ; identité déjà revendiquée = `Failure(AlreadyInUse(Host))` — aucune exception sur ces chemins d’admission | `android-attach-decor-mismatch`, `android-attach-cross-form-conflict`, `android-attach-parent-cancelled`, `android-attach-surviving-scope` ; `AndroidAttachAdmissionTest.decorMismatchIsInvalidSurfaceView`, `.destroyedLifecycleOwnerIsClosedHost`, `.crossFormConflictsReturnBusyWithoutTransfer` ; `AndroidHostSessionResultTest.admissionDenialsBecomeKadreResultsWithoutSession`, `.threadViolationRemainsAProgrammingError` |
| `attachKadre` sur `View` — les deux overloads de `DESIGN.md` §15.1 (`applicationFactory` et `application`), `lifecycleOwner` explicite, `parentScope` défaut `lifecycleScope`, retour `KadreResult<KadreSession>` ; aucune identité Activity à réserver | `androidJvm` | API 24 | idem | idem — les scénarios AND-001 comme AND-002 s’exécutent sur le même device identifié | hors thread principal = même `IllegalStateException` (§3) ; View non attachée = `Failure(InvalidRequest("view"))` — le field suit l’overload ; scope sans `Job` = `Failure(InvalidRequest("parentScope"))`, job inactif = `Failure(ParentScopeCancelled)` ; View déjà revendiquée (toutes formes confondues) = `Failure(AlreadyInUse(Host))` | `android-consumer-session-start`, `android-consumer-double-attach-rejected` (AND-001 relu contre les overloads publics, `AlreadyInUse` au lieu de l’exception de phase 0), `android-attach-parent-cancelled`, `android-attach-surviving-scope` ; `AndroidAttachAdmissionTest.viewFieldFollowsTheOverload`, `.scopeLadderExactFieldsAndReasons` |
| échelle d’admission (`evaluateAndroidAttach`) — ordre normatif thread → scope → view/decor → owner détruit → conflit ; refus **avant toute mutation d’état**, en `KadreResult.Failure` sans exception | `androidJvm` | API 24 | idem | idem | l’ordre des rungs est observé exactement : `mainThread` (exception) → `InvalidRequest("parentScope")` → `ParentScopeCancelled` → `InvalidRequest("view")`/`InvalidRequest("surfaceView")` (attachement, puis mismatch decor pour la forme Activity) → `Closed(Host)` → `AlreadyInUse(Host)` (identité Activity, puis identité View) ; un refus ne réserve rien — l’attach nominal immédiatement après une denial réussit | `android-attach-decor-mismatch` (la denial ne réserve rien), `android-attach-parent-cancelled` (les deux denials scope, puis attach nominal sur la même View) ; `AndroidAttachAdmissionTest.happyPathReturnsNoDenial`, `.scopeLadderExactFieldsAndReasons`, `.viewFieldFollowsTheOverload`, `.decorMismatchIsInvalidSurfaceView`, `.destroyedLifecycleOwnerIsClosedHost`, `.crossFormConflictsReturnBusyWithoutTransfer` |
| ownership et réservation atomique — réservation des identités Activity + View en une étape, rollback intégral sur échec tardif, libération uniquement à la terminaison effective (tous chemins, idempotente) | `androidJvm` | API 24 | idem (`AndroidAttachReservation`, `AndroidViewOwnership`) | idem | conflit d’identité = `Failure(AlreadyInUse(Host))` **sans transfert d’ownership** (cross-form Activity↔View compris) ; un échec tardif de `controller.attach` restitue l’état d’avant la demande — l’attach suivant sur la même View réussit ; après terminaison, `release` post-terminal ne réanime rien et le re-attach est possible | sentinelles `android-attach-rollback-restores-state` (rayon de mutation démontré : `androidAttachParentCancelled` seul), `android-attach-isolation-per-identity` (rayon : `androidAttachRecreationIsolation` seul), `android-double-attach-single-owner` ; scénarios `android-attach-cross-form-conflict`, `android-consumer-double-attach-rejected` ; `AndroidAttachReservationTest.acquireReservesBothIdentitiesAtomically`, `.rollbackAfterLateFailureRestoresReservation`, `.releaseHappensOnlyAtEffectiveTerminationAndIsIdempotent` |
| observation lifecycle et détachement terminal — axes foreground/active publiés au runtime (`Foreground` = au moins `STARTED`, `Active` = au moins `RESUMED`), notifications dédupliquées, premier détachement de la View ou destruction de l’owner **terminal** même avec un scope survivant, avec `Terminated(Stopped(SessionStopReason.HostDetached))` ; le re-parenting exige un nouvel attach et une nouvelle session | `androidJvm` | API 24 | idem (`AndroidLifecycleReducer`, `AndroidLifecycleBinding`) | idem | `—` (livrée) : `RESUMED` publie `Foreground + Active`, `Paused`/`Stopped` publient `Background + Inactive` sans terminer la session ; `onDetachedFromWindow` ou `onDestroy` publient le terminal `Stopped(HostDetached)` (priorité du runtime respectée dans les courses stop/destroy/exception — le réducteur est terminal et verrouillé après le premier signal) ; les publications tardives post-detach sont absorbées sans résurrection ; les IDs, le lifecycle et les jobs de la session d’une seconde Activity sont inchangés par la destruction de la première | `android-attach-surviving-scope`, `android-attach-detach-reparent`, `android-attach-recreation-isolation` ; sentinelles `android-stop-no-resurrection`, `android-attach-isolation-per-identity` ; `AndroidLifecycleReducerTest.detachIsTerminalEvenWithSurvivingScope`, `.pauseAndStopDoNotTerminate`, `.notificationsAreDeduplicated`, `.terminalPriorityStopVsDestroyVsException`, `.lifecycleStartedPublishesForegroundSnapshot` ; `AndroidHostLifecyclePropagationTest.publicationsReachTheRuntimeInOrder`, `.publicationAfterDetachIsAbsorbedWithoutResurrection`, `.lostTerminalProposalIsReportedThroughTheRuntimeReporter` |
| topologies et fenêtres — forme Activity : `WindowManagerState.primary` ∈ `windows`, la fenêtre primaire partageant **l’instance** de la surface primaire de la session ; forme View : `primary == null` et liste de fenêtres vide ; `requestWindow` dans les deux formes | `androidJvm` | API 24 | idem (`AndroidActivityWindowManager`, `AndroidActivityWindow`) | idem | `requestWindow` : la requête est retournée `Success(WindowRequest)` mais son outcome est déjà terminal `Rejected(Unsupported(RequestWindow))` avant que l’appelant ne la voie (dans les **deux** formes — capability `Unsupported(KadreOperation.RequestWindow)` du manager) ; `await()` ne suspend pas, `cancel()` répond `AlreadyTerminated` ; les commandes de la fenêtre (`apply`, `requestAttention`, `close`, `respondToCloseRequest`) répondent `Failure(Unsupported(…))` de leur opération, chaque opération nommée — les interactions transitoires restent différées (§4, phase 4) | `AndroidWindowTopologyTest.activityPrimaryWindowSharesSurfaceIdentity`, `.viewFormHasNullPrimaryAndEmptyWindows`, `.requestWindowIsImmediatelyRejectedUnsupported` |

Trois remarques de lecture pour que la table reste exacte. D’abord, l’attach de phase 1 construit
toujours une **session Kadre réelle** par la couture runtime (`RuntimeHostController` +
`controller.attach`, friend-wirée sur les internals exactement comme `WebHostSession`), mais la
session **démarre observée** : un binding lifecycle — un par attach, installé exactement une fois —
relit les préconditions après installation (la course validation/détachement produit le terminal
immédiatement) et chaque publication du réducteur devient à la fois l’état lifecycle du runtime
(RUN-004, le modèle de la couture web) et l’état de la surface. Ensuite, l’état de surface publié
reste un **instantané honnête mais figé** pour les métriques : dimensions copiées de la `View` à
l’attach (bornées au minimum valide 1×1 quand elle n’est pas encore mesurée, densité lue de
`resources.displayMetrics`), champs observés (attachment/visibility/focus) tenus courants par le
réducteur — **aucune relecture des metrics, aucun observer input, interaction ou IME n’est
installé** : c’est la livraison de la phase 2 (metrics/redraw) et de la phase 3 (input), et c’est
pourquoi aucune ligne `SurfaceCapabilities`/`InputCapabilities` ne figure encore dans ce registre.
Enfin, le conjonctif « fenêtre interactive » de l’axe `Active` de `DESIGN.md` est dérivé du
**lifecycle uniquement** en phase 1 (`Active` ⇔ `RESUMED`) — honnête pour les deux topologies
livrées (une `ComponentActivity` au premier plan au moins `RESUMED` possède une fenêtre
interactive ; la forme View n’a aucune `Window` du reste, la matrice §3 l’enregistre) —
l’affinement multi-fenêtres/dialogues de ce conjonctif appartient à la phase 4 (§4) ; aucune
publication n’en prétend davantage.

## 3. Le contrat de thread d’attach — décision de phase 0 maintenue en phase 1

La roadmap (`ANDROID-IMPLEMENTATION-ROADMAP.md` §3.3 et §4, ligne « Thread d’attach et propriété
des callbacks ») exige que le contrat d’appel d’`attachKadre` soit **fixé en phase 0**, avant toute
phase dépendante : « aucun blocage main-thread ni relocation implicite ne sera introduit pour
contourner une ambiguïté ». La décision livrée, consignée dans le KDoc d’`attachKadre`
(`KadreAndroidAttach.kt`) et asservie par `AndroidHostSessionResultTest` :

1. **Thread principal Android uniquement.** L’appel vérifie
   `Looper.myLooper() == Looper.getMainLooper()` ; un appel hors thread principal est rejeté
   `IllegalStateException` avant toute autre vérification et avant toute mutation d’état. C’est
   l’unique chemin lançant de la surface publique de phase 1 — une erreur de programmation, pas un
   `KadreResult` (épinglé par
   `AndroidHostSessionResultTest.threadViolationRemainsAProgrammingError`).
2. **Non suspendant, sans blocage ni relocation.** `attachKadre` ne suspend pas, ne bloque pas le
   caller et ne déplace jamais l’appel vers un autre thread ; le dispatcher applicatif reste celui
   du `parentScope`, ce qui ne donne pas le droit d’appeler le SDK UI depuis n’importe quel
   thread.
3. **Ordre normatif des refus** (`evaluateAndroidAttach`, `AndroidAttachAdmission.kt`) :
   thread (exception) → `parentScope` (scope sans `Job` → `InvalidRequest("parentScope")`, job
   inactif → `ParentScopeCancelled`) → view/decor (forme View `InvalidRequest("view")`, forme
   Activity `InvalidRequest("surfaceView")` — attachement, puis mismatch `rootView !== decorView`)
   → owner détruit (`Closed(Host)`) → conflit d’identité (`AlreadyInUse(Host)`, identité Activity
   puis identité View). Tous rungs en échec, la denial rapportée est la première de cet ordre —
   épinglé par `AndroidAttachAdmissionTest.scopeLadderExactFieldsAndReasons`.
4. **Refus avant toute mutation d’état — ou rollback intégral.** Les denials d’échelle précèdent
   `AndroidAttachReservation.acquire` et l’installation du binding ; un échec **tardif** (échec de
   `controller.attach` après réservation) déclenche le teardown du binding et le rollback de la
   réservation avant de retourner `Failure(reason)` — zéro résidu observable, plus jamais
   d’exception sur ce chemin (le gap de sûreté d’exception de la phase 0 est comblé ; épinglé par
   `AndroidAttachReservationTest.rollbackAfterLateFailureRestoresReservation` et par la sentinelle
   `android-attach-rollback-restores-state`).
5. **La décision de thread est définitive et n’est pas rouverte** ; la forme du rejet des autres
   admissions est passée de l’exception de phase 0 à la taxonomie `KadreResult` de phase 1 — c’est
   la livraison planifiée, pas une révision de la décision.

## 4. Différées de phase (au regard de §8)

Les lignes restantes de la matrice de `BACKEND-CAPABILITIES.md` §4 applicables à Android ne sont
pas produites par la phase 1. §8 rappelle qu’« une ligne manquante empêche l’adapter d’être déclaré
supporté » : ces lignes restent donc à produire avant toute déclaration « supported » de l’adapter
Android, chacune par la phase de la roadmap qui la possède. Aucune de ces lignes n’est ici une
décision de capability : la colonne « cellule normative » rappelle ce que la matrice §4 promet à
l’adapter Android View/Activity une fois sa phase livrée, pas ce qui est publié aujourd’hui.

| feature (cellule §4, Android View/Activity) | cellule normative | phase propriétaire | note de lecture phase 1 |
|---|---|---|---|
| surface metrics + redraw request (`SurfaceUpdate`, `requestRedraw`) et accès natif borné — `SurfaceCapabilities.platformAccess`, lease `withAndroidView` | G ; `withAndroidView` | **Phase 2** | l’instantané initial reste figé aux dimensions copiées à l’attach (§2, remarque 2) ; les champs attachment/visibility/focus, eux, sont déjà tenus courants par le réducteur |
| clavier, pointer, touch (stylet compris) | C | **Phase 3** | aucun listener n’est posé sur la View |
| commandes de fenêtre réelles et interactions transitoires (cursor, pointer capture, interactions) ; affinement multi-fenêtres/dialogues du conjonctif « fenêtre interactive » de `Active` | Activity : C ; View : `— (aucune Window)` ; `WindowCapabilities.platformAccess` = N(PlatformWindowAccess) | **Phase 4** | la fenêtre primaire Activity existe (topologie §2) mais chaque commande répond `Unsupported` de son opération ; `requestWindow` est rejeté dans les deux formes — le rejet est livré, les interactions ne le sont pas ; le conjonctif « fenêtre interactive » de `Active` est dérivé du lifecycle seul (§2, remarque 3) |
| IME / text input, gestures reconnues par le host | C | **Phase 5** | aucun port texte ni recognizer |
| drag-and-drop et ownership des transferts | C | **Phase 6** | aucun pont de drag |
| inventaire display complet, fallback `HostViewport`, devices/gamepads (observation et effets), décision raw input, pression mémoire | inventaire/fallback/observation/effets/pression : C ; raw input : C (décision séparée, sinon N(RawInputAccess)) | **Phase 7** | les managers restent à leurs états par défaut non supportés du runtime ; aucun port display/device/gamepad n’est passé |
| capture targets `HostChoice`, `Source`, `Surface` | C | **Phase 8** | aucun chemin de capture, aucun consentement |
| façade Java (`KadreAndroid.attach`, `KadreSessionHandle`), liaison Compose | §6.1 + `INTEROP-EXPORTS.md` | **Phase 9** | la surface publique de phase 1 est Kotlin uniquement |
| gate CI Android obligatoire (job `android-contracts`) | §9 (gate adapter officiel) | **Phase 10** | `androidContractsCheck` est un gate local à émulateur, volontairement hors `:kadre:check` |

## 5. Références

- [Contrats des adapters et matrice de capabilities](../BACKEND-CAPABILITIES.md) — mandat §8, matrice §4 (colonnes Android), topologie §3/§6.1
- [Roadmap d’implémentation Android](../ANDROID-IMPLEMENTATION-ROADMAP.md) — §3.3 (frontière runtime/SDK, contrat de thread), §Phase 0 (décision de thread), §Phase 1 (gate de sortie)
- [Registre des contrats](../contracts/registry/contracts.tsv) — lignes `AND-001` (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 0`) et `AND-002` (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 1`), preuve `android`
- [Driver du contrat Android](../contracts/driver/android/README.md) — comment relancer le gate localement, étiquetage moteur, limites de phase 1
- [Design Kadre](../DESIGN.md) — §15.1 (les quatre overloads Android que la phase 1 livre), lifecycle à trois axes (`Active` = attaché + foreground + fenêtre interactive)
