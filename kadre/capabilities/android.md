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

**Phase 2.** Ce registre livre en plus ce que la phase 2 de la roadmap
(`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 2 — Surface, metrics, redraw et accès natif`) possède —
la surface observable `AndroidHostSurface` publiant dimensions physiques/logiques, densité, insets,
apparence, visibilité et focus depuis des observations SDK cohérentes avec `Unknown` préservé, les
causes géométriques distinguées avec rejet par génération des callbacks tardifs, le `requestRedraw`
coalescé à la frame, l’`apply` par champ sur les formes on-row du ruling P1, le bail d’accès natif
`withAndroidView` à double opt-in et `platformAccess` `Supported` sur la surface (§2) — et il
réécrit la ligne différée de phase 1 qui les portait. La frontière de la phase est tenue : la
surface n’a **aucun rendu** (hors périmètre, jamais), le `requestRedraw` n’ajoute ni renderer ni
event loop public, et focus/visibilité restent la propriété de l’observation lifecycle — le
binding métriques ne les invente pas (§2, remarque 2).

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
  capability*. Pour les phases 1 et 2 c’est l’émulateur identifié : la preuve `AND-001`, `AND-002`
  **et** `AND-003` n’existe que branchée à un device réel (`androidContractsCheck` exige un
  émulateur démarré et n’est volontairement pas dans `:kadre:check` — aucun émulateur sur les
  runners actuels, branchement CI = phase 10 ; les trois JSON produits par le même run
  instrumenté, 15 tests).
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
  `KadreConsumerSessionTest` (3 scénarios, 2 sentinelles), `AND-002` sur
  `KadreConsumerAttachContractTest` (6 scénarios, 2 sentinelles) et `AND-003` sur
  `KadreConsumerSurfaceContractTest` (6 scénarios, 3 sentinelles dont une host-pinned),
  résolution de l’umbrella **publié** `org.graphiks.kadre:kadre` depuis le repository contractuel ;
  puis les classes d’hôte `androidHostTest` (sans device) — `AndroidAttachAdmissionTest`,
  `AndroidAttachReservationTest`, `AndroidLifecycleReducerTest`, `AndroidHostLifecyclePropagationTest`,
  `AndroidHostSessionResultTest`, `AndroidWindowTopologyTest`, `AndroidHostSurfaceTest`,
  `AndroidSurfaceMetricsTest`, `AndroidRedrawSchedulerTest`, `AndroidViewLeaseTest` — qui
  asservissent l’échelle, la réservation, le réducteur lifecycle, la propagation au runtime, les
  topologies, la surface observable (identité, `apply`, terminal), le réducteur métriques et la
  génération, le scheduler de redraw et le bail d’accès natif.

## 2. Registre — phases 1 et 2

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| `attachKadre` sur `ComponentActivity` — les deux overloads de `DESIGN.md` §15.1 (`applicationFactory` et `application`), `surfaceView` explicite, `parentScope` défaut `lifecycleScope`, retour `KadreResult<KadreSession>` | `androidJvm` | API 24 | `:kadre:platform:android:compileAndroidMain` — lit `android.view.View`, `android.os.Looper`, `androidx.activity.ComponentActivity`, `androidx.lifecycle.*`, `kotlinx.coroutines.*` ; friend-wiring des classes Android de `foundation` et `runtime` | `AND-001` + `AND-002` / `androidContractsCheck` — un émulateur identifié (`emulator-api-35-arm64-v8a`) doit être démarré ; la preuve est produite à HEAD publié (`--refresh-dependencies`) | hors thread principal = `IllegalStateException("attachKadre must be called on the Android main thread")` avant toute mutation ; View non attachée à une fenêtre ou `surfaceView.rootView !== window.decorView` = `KadreResult.Failure(InvalidRequest("surfaceView"))` ; owner détruit = `Failure(Closed(Host))` ; identité déjà revendiquée = `Failure(AlreadyInUse(Host))` — aucune exception sur ces chemins d’admission | `android-attach-decor-mismatch`, `android-attach-cross-form-conflict`, `android-attach-parent-cancelled`, `android-attach-surviving-scope` ; `AndroidAttachAdmissionTest.decorMismatchIsInvalidSurfaceView`, `.destroyedLifecycleOwnerIsClosedHost`, `.crossFormConflictsReturnBusyWithoutTransfer` ; `AndroidHostSessionResultTest.admissionDenialsBecomeKadreResultsWithoutSession`, `.threadViolationRemainsAProgrammingError` |
| `attachKadre` sur `View` — les deux overloads de `DESIGN.md` §15.1 (`applicationFactory` et `application`), `lifecycleOwner` explicite, `parentScope` défaut `lifecycleScope`, retour `KadreResult<KadreSession>` ; aucune identité Activity à réserver | `androidJvm` | API 24 | idem | idem — les scénarios AND-001 comme AND-002 s’exécutent sur le même device identifié | hors thread principal = même `IllegalStateException` (§3) ; View non attachée = `Failure(InvalidRequest("view"))` — le field suit l’overload ; scope sans `Job` = `Failure(InvalidRequest("parentScope"))`, job inactif = `Failure(ParentScopeCancelled)` ; View déjà revendiquée (toutes formes confondues) = `Failure(AlreadyInUse(Host))` | `android-consumer-session-start`, `android-consumer-double-attach-rejected` (AND-001 relu contre les overloads publics, `AlreadyInUse` au lieu de l’exception de phase 0), `android-attach-parent-cancelled`, `android-attach-surviving-scope` ; `AndroidAttachAdmissionTest.viewFieldFollowsTheOverload`, `.scopeLadderExactFieldsAndReasons` |
| échelle d’admission (`evaluateAndroidAttach`) — ordre normatif thread → scope → view/decor → owner détruit → conflit ; refus **avant toute mutation d’état**, en `KadreResult.Failure` sans exception | `androidJvm` | API 24 | idem | idem | l’ordre des rungs est observé exactement : `mainThread` (exception) → `InvalidRequest("parentScope")` → `ParentScopeCancelled` → `InvalidRequest("view")`/`InvalidRequest("surfaceView")` (attachement, puis mismatch decor pour la forme Activity) → `Closed(Host)` → `AlreadyInUse(Host)` (identité Activity, puis identité View) ; un refus ne réserve rien — l’attach nominal immédiatement après une denial réussit | `android-attach-decor-mismatch` (la denial ne réserve rien), `android-attach-parent-cancelled` (les deux denials scope, puis attach nominal sur la même View) ; `AndroidAttachAdmissionTest.happyPathReturnsNoDenial`, `.scopeLadderExactFieldsAndReasons`, `.viewFieldFollowsTheOverload`, `.decorMismatchIsInvalidSurfaceView`, `.destroyedLifecycleOwnerIsClosedHost`, `.crossFormConflictsReturnBusyWithoutTransfer` |
| ownership et réservation atomique — réservation des identités Activity + View en une étape, rollback intégral sur échec tardif, libération uniquement à la terminaison effective (tous chemins, idempotente) | `androidJvm` | API 24 | idem (`AndroidAttachReservation`, `AndroidViewOwnership`) | idem | conflit d’identité = `Failure(AlreadyInUse(Host))` **sans transfert d’ownership** (cross-form Activity↔View compris) ; un échec tardif de `controller.attach` restitue l’état d’avant la demande — l’attach suivant sur la même View réussit ; après terminaison, `release` post-terminal ne réanime rien et le re-attach est possible | sentinelles `android-attach-rollback-restores-state` (rayon de mutation démontré : `androidAttachParentCancelled` seul), `android-attach-isolation-per-identity` (rayon : `androidAttachRecreationIsolation` seul), `android-double-attach-single-owner` ; scénarios `android-attach-cross-form-conflict`, `android-consumer-double-attach-rejected` ; `AndroidAttachReservationTest.acquireReservesBothIdentitiesAtomically`, `.rollbackAfterLateFailureRestoresReservation`, `.releaseHappensOnlyAtEffectiveTerminationAndIsIdempotent` |
| observation lifecycle et détachement terminal — axes foreground/active publiés au runtime (`Foreground` = au moins `STARTED`, `Active` = au moins `RESUMED`), notifications dédupliquées, premier détachement de la View ou destruction de l’owner **terminal** même avec un scope survivant, avec `Terminated(Stopped(SessionStopReason.HostDetached))` ; le re-parenting exige un nouvel attach et une nouvelle session | `androidJvm` | API 24 | idem (`AndroidLifecycleReducer`, `AndroidLifecycleBinding`) | idem | `—` (livrée) : `RESUMED` publie `Foreground + Active`, `Paused`/`Stopped` publient `Background + Inactive` sans terminer la session ; `onDetachedFromWindow` ou `onDestroy` publient le terminal `Stopped(HostDetached)` (priorité du runtime respectée dans les courses stop/destroy/exception — le réducteur est terminal et verrouillé après le premier signal) ; les publications tardives post-detach sont absorbées sans résurrection ; les IDs, le lifecycle et les jobs de la session d’une seconde Activity sont inchangés par la destruction de la première | `android-attach-surviving-scope`, `android-attach-detach-reparent`, `android-attach-recreation-isolation` ; sentinelles `android-stop-no-resurrection`, `android-attach-isolation-per-identity` ; `AndroidLifecycleReducerTest.detachIsTerminalEvenWithSurvivingScope`, `.pauseAndStopDoNotTerminate`, `.notificationsAreDeduplicated`, `.terminalPriorityDestroyVsDetach`, `.lifecycleStartedPublishesForegroundSnapshot` ; `AndroidHostLifecyclePropagationTest.publicationsReachTheRuntimeInOrder`, `.publicationAfterDetachIsAbsorbedWithoutResurrection`, `.lostTerminalProposalIsReportedThroughTheRuntimeReporter` |
| topologies et fenêtres — forme Activity : `WindowManagerState.primary` ∈ `windows`, la fenêtre primaire partageant **l’instance** de la surface primaire de la session ; forme View : `primary == null` et liste de fenêtres vide ; `requestWindow` dans les deux formes | `androidJvm` | API 24 | idem (`AndroidActivityWindowManager`, `AndroidActivityWindow`) | idem | `requestWindow` : la requête est retournée `Success(WindowRequest)` mais son outcome est déjà terminal `Rejected(Unsupported(RequestWindow))` avant que l’appelant ne la voie (dans les **deux** formes — capability `Unsupported(KadreOperation.RequestWindow)` du manager) ; `await()` ne suspend pas, `cancel()` répond `AlreadyTerminated` ; la fenêtre primaire Activity est la seule `Window` vivante non supportante du dépôt — le manager par défaut du runtime (`UnsupportedWindowManager`) ne crée jamais de fenêtre et n’offre aucun « état par défaut » à un backend sans implémentation — et ses commandes répondent chacune dans la forme exacte que leur ligne `OPERATION-CONTRACTS` §4 admet : `apply` → `Success(PartiallyApplied(…))` (chaque champ demandé rejeté `RejectedWindowField(Unsupported(UpdateWindow))`, aucun champ appliqué, garde `expectedRevision` → `Stale`), `requestAttention` → `Failure(Unsupported(RequestWindowAttention))`, `close` → `Failure(Temporary(retryable = false))` — ruling provisoire d’une fenêtre d’host structurellement non fermable, la ligne n’admets que `Temporary`/`Platform` et la décision normative est parkée phase 4 —, `respondToCloseRequest` → `Failure(InvalidRequest("requestId"))` — aucun `CloseRequested` n’est jamais publié (`events` vide), aucun `requestId` ne peut être valide — ; la table complète des commandes réelles reste différée (§4, phase 4) | `AndroidWindowTopologyTest.activityPrimaryWindowSharesSurfaceIdentity`, `.viewFormHasNullPrimaryAndEmptyWindows`, `.requestWindowIsImmediatelyRejectedUnsupported`, `.windowCommandsAnswerOnContractRowShapes` |
| surface observable — publication métrique continue depuis les observations SDK (`addOnLayoutChangeListener` additif + lecture initiale postée) : dimensions physiques/logiques, `scaleFactor` (densité `resources.displayMetrics`), insets (`rootWindowInsets`), apparence (`uiMode`, masque nuit) ; `Unknown` préservé — une observation manquante (`visible`/`focused`/`nightMode` à `null`) conserve le champ publié, jamais inventé ; focus et visibilité restent publiés par le lifecycle seul (le binding métrique ne les observe pas) ; la même instance de surface est conservée tant que le host demeure attaché, la révision ne bouge que pour un état qui bouge réellement | `androidJvm` | API 24 | idem (`AndroidSurfaceObservationBinding`, noyau pur `androidSurfaceApply`) | idem — les scénarios AND-003 s’exécutent sur le même device identifié | `—` (livrée) : les metrics publiées sont comparées à la `View` réelle (`android-surface-metrics-match-view`) et un redimensionnement de la View se propage sans remplacement de surface (`android-surface-resize-propagates`) ; une publication qui suit le terminal est absorbée sans résurrection ; les collections SDK sont copiées à la frontière — aucun type SDK ne traverse | `android-surface-metrics-match-view`, `android-surface-resize-propagates` ; `AndroidSurfaceMetricsTest.dimensionsAndDensityPublishLogicalAndPhysical`, `.missingObservationsKeepUnknown`, `.insetsPublishSafeArea`, `.identicalObservationIsIgnoredWithoutPublication`, `.surfaceRevisionOnlyMovesOnRealChangeAndInstanceIsPreserved` ; `AndroidHostSurfaceTest.identityPreservedAcrossPublications` |
| causes distinguées et rejet par génération — `rotated`/`densityChanged`/`resized` distingués au sein d’une publication `Metrics` ; un événement typé unique par publication (priorité focus > visibilité > apparence > métriques, chaque événement portant l’instantané entier) ; toute observation étiquetée d’une génération dépassée (`supersedeGeneration` au teardown/recréation de ressource) est rejetée `Stale` sans publication — le callback tardif d’une ressource remplacée ne publie jamais | `androidJvm` | API 24 | idem (`AndroidSurfaceMetricsReducer`, `AndroidSurfaceChange`) | idem | `—` (livrée) : rotation vs redimensionnement vs densité sont distingués (`rotationVsResizeVsDensityAreDistinguished`) ; un callback de génération périmée est rejeté — sentinelle **host-pinned** `android-metrics-generation-stale` : aucun scénario émulateur ne peut produire sincèrement un callback périmé (la View du binding n’est jamais remplacée en cours de session, le détachement est terminal), le kill est démontré par la mutation enregistrée contre `AndroidSurfaceMetricsTest.staleGenerationCallbackRejected`, l’ancre de mapping étant le scénario métriques | `android-surface-metrics-match-view` (ancre), sentinelle `android-metrics-generation-stale` (host-pinned) ; `AndroidSurfaceMetricsTest.rotationVsResizeVsDensityAreDistinguished`, `.staleGenerationCallbackRejected`, `.generationIsReadableToPairTheBindingObservations`, `.focusAndVisibilityAndAppearancePublishTypedEvents` |
| `requestRedraw` coalescé — admission `Success(Unit)` par demande, au plus une frame en attente, **UN** `SurfaceEvent.RedrawRequested` (révision courante + stamp de session) par frame ; annulation du scheduler au terminal — aucun callback de frame zombie après `close`, aucun événement après le terminal ; aucun renderer ni event loop public ajouté (le rendu lui-même reste hors périmètre) | `androidJvm` | API 24 | idem (`AndroidRedrawScheduler`, corps de frame `emitRedrawFrame` sous `RuntimeLock`) | idem | `—` (livrée) : 5 demandes dans une même frame répondent chacune `Success(Unit)` et produisent exactement UN `RedrawRequested` (`android-surface-redraw-coalesced`) ; après `session.close()`, `requestRedraw()` répond `Failure(Closed(Surface))` et aucun événement ne suit (`android-surface-redraw-after-close`) ; la sentinelle `android-redraw-single-per-frame` (un événement par demande, coalescing retiré) ne tue que `androidSurfaceRedrawCoalesced` | `android-surface-redraw-coalesced`, `android-surface-redraw-after-close` ; sentinelle `android-redraw-single-per-frame` ; `AndroidRedrawSchedulerTest.redrawCoalescesToOnePendingFrame`, `.redrawCoalescesToOneEventPerFrame`, `.cancelPreventsAnyFurtherCallback`, `.redrawAfterTerminalEmitsNothing` ; `AndroidHostSurfaceTest.requestRedrawBeforeSchedulingAdmitsOrAnswersOnRow` |
| `apply` de surface — admission par champ sur les formes on-row du ruling P1 : `Closed(Surface)` terminal (et état publié détaché), `StaleRevision` avant toute admission de champ, `InvalidRequest(field)` pour un `Clear` sans valeur non posée, puis admission champ par champ contre la capability publiée — en phase 2 les quatre champs (`cursor`, `pointerCapture`, `hitTesting`, `inputDefaultBehavior`) sont `Unsupported(UpdateSurface)` : un champ posé est rejeté `RejectedSurfaceField(Unsupported(UpdateSurface))`, un champ non posé jamais ; verdict `Applied(current)` / `PartiallyApplied(current, rejected)` — jamais une failure externe pour l’update entier ; aucun chemin de commit, l’état et la révision ne bougent pas | `androidJvm` | API 24 | idem (`admitField` partagé du runtime, garde `requireRefused`) | idem (sans device : décision sous `RuntimeLock`) | champ posé = `Success(PartiallyApplied(current, [RejectedSurfaceField(Unsupported(UpdateSurface))]))` par champ, aucun champ appliqué ; update vide = `Success(Applied(current))` ; `expectedRevision` périmée = `Failure(StaleRevision)` avant l’admission ; `Clear` illégal = `Failure(InvalidRequest("cursor"))` (etc.) ; surface terminale = `Failure(Closed(Surface))` | `AndroidHostSurfaceTest.applyEmptyUpdateIsAppliedCurrentState`, `.applyPosedFieldsArePartiallyAppliedWithUnsupportedPerField`, `.staleRevisionIsRejectedBeforeAdmission`, `.clearOnAFieldWithoutUnsetValueIsInvalidRequest`, `.closeRemovesCapabilitiesBeforePublishingTheDetachedState`, `.eventsFlowCompletesAtTerminal` ; l’instrumenté `android-surface-redraw-after-close` épingle le même `Closed(Surface)` sur `requestRedraw` |
| bail d’accès natif `withAndroidView` — double opt-in `@KadrePlatformApi` + `@DelicateKadreApi`, prête la `View` du host pour la durée du block et pas plus ; bail unique (un second bail en vol = `TemporarilyUnavailable(retryable = true)`, block jamais invoqué) ; block sur le thread principal Android, invocation démarrée achèvée (`NonCancellable`) même si l’appelant est annulé pendant lui ; appelant annulé **avant** l’admission rejeté sous verrou sans rien invoquer ; exception applicative propagée **telle quelle** ; surface terminale = `Closed(Surface)` sans invoquer — la source de la View est effacée au terminal, une View démontée n’est jamais remise ; surface non Android = `Unsupported(PlatformSurfaceAccess)` | `androidJvm` | API 24 | idem (`AndroidViewLeasePort`, `AndroidHostThreadDispatch`, façade `withAndroidView` dans `AndroidViewLease.kt`) | idem | `—` (livrée) : le bail rend la View attachée (`android-surface-lease-boundaries`) ; second bail en vol = `TemporarilyUnavailable(retryable = true)` sans invocation ; après `close()` chaque bail = `Closed(Surface)` sans invoquer ; la sentinelle `android-lease-closed-after-terminal` (bail sautant sa garde d’admission) ne tue que `androidSurfaceLeaseBoundaries` — plus l’hôte `AndroidViewLeaseTest.leaseAfterCloseIsClosedSurfaceWithoutInvoking` | `android-surface-lease-boundaries` ; sentinelle `android-lease-closed-after-terminal` ; `AndroidViewLeaseTest.leaseRunsBlockWithTheViewOnHostThread`, `.secondLeaseWhileInFlightIsTemporaryWithoutInvoking`, `.leaseAfterCloseIsClosedSurfaceWithoutInvoking`, `.applicationExceptionPropagatesAsIs`, `.nonAndroidSurfaceIsUnsupportedPlatformAccess`, `.startedCallbackCompletesOnHostThreadDespiteCancellation`, `.callerCancelledBeforeTheLeaseNeverStartsTheBlock` |
| `SurfaceCapabilities.platformAccess` — `Supported` sur la surface, publié à l’installation de l’observation et du scheduling (le snapshot par défaut tout-refus cède `androidSurfaceCapabilities`), retiré au terminal avant la publication de l’état détaché ; `WindowCapabilities.platformAccess` reste `Unsupported(PlatformWindowAccess)` — aucun handle `android.view.Window` générique n’est exposé, aucun membre public de la surface ne le nomme (la View ne circule qu’en `Any` à travers le bail, typée `View` au seul bord de la façade) | `androidJvm` | API 24 | idem (`androidSurfaceCapabilities`) | idem | `—` (livrée) : le `platformAccess` de la fenêtre reste `Failure(Unsupported(PlatformWindowAccess))` et aucune surface membre ne expose `android.view.Window` (`android-surface-no-window-handle`) ; les capabilities de surface deviennent honnêtes seulement une fois l’observation installée et meurent au plus tard au détached (`capabilitiesBecomeHonestAfterObservationInstalled`, `closeRemovesCapabilitiesBeforePublishingTheDetachedState`) | `android-surface-no-window-handle` ; `AndroidViewLeaseTest.capabilitiesBecomeHonestAfterObservationInstalled` ; `AndroidHostSurfaceTest.closeRemovesCapabilitiesBeforePublishingTheDetachedState` |

Trois remarques de lecture pour que la table reste exacte. D’abord, l’attach de phase 1 construit
toujours une **session Kadre réelle** par la couture runtime (`RuntimeHostController` +
`controller.attach`, friend-wirée sur les internals exactement comme `WebHostSession`), mais la
session **démarre observée** : un binding lifecycle — un par attach, installé exactement une fois —
relit les préconditions après installation (la course validation/détachement produit le terminal
immédiatement) et chaque publication du réducteur devient à la fois l’état lifecycle du runtime
(RUN-004, le modèle de la couture web) et l’état de la surface. Ensuite, depuis la phase 2, la
surface est tenue **courante** pour les métriques : l’instantané initial est copié de la `View` à
l’attach (borné au minimum valide 1×1 quand elle n’est pas encore mesurée, densité lue de
`resources.displayMetrics`, insets de `rootWindowInsets`) — identique à ce que le binding publie —
puis un binding d’observation (`addOnLayoutChangeListener` additif + lecture initiale postée)
relit à chaque passe de layout les dimensions, la densité, les insets et l’apparence (`uiMode`), en
copiant tout à la frontière ; les champs `visible`/`focused` de l’observation restent `null` — le
binding métrique **ne concurrence pas** le lifecycle sur focus et visibilité, qui restent sa
propriété (les scénarios AND-002 `android-attach-surviving-scope` restent verts tels quels) ;
**aucun observer input, interaction ou IME n’est installé** : c’est la livraison de la phase 3
(input), et c’est pourquoi aucune ligne `InputCapabilities` ne figure encore dans ce registre —
`SurfaceCapabilities.platformAccess`, elle, est publiée (§2, la ligne du bail). Enfin, le
conjonctif « fenêtre interactive » de l’axe `Active` de `DESIGN.md` est dérivé du **lifecycle
uniquement** en phase 1 (`Active` ⇔ `RESUMED`) — honnête pour les deux topologies
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
   réservation avant de retourner `Failure(reason)` — zéro résidu observable. Le contrat
   d’exception exact de ce chemin : les refus d’admission et les rejets du contrôleur deviennent
   des `KadreResult` ; les erreurs de programmation et les throwables SDK (installation du
   binding — `addObserver`/`addOnAttachStateChangeListener` —, construction du contrôleur,
   `controller.attach`) se propagent **tels quels**, mais seulement après le rollback intégral —
   jamais avec la réservation encore tenue (la View resterait in-attachable jusqu’à la mort du
   processus) ; épinglé par `AndroidAttachReservationTest.rollbackAfterLateFailureRestoresReservation`,
   `AndroidHostSessionResultTest.throwingInstallStepRollsBackAndRethrowsRaw`,
   `AndroidHostSessionResultTest.controllerRejectionRollsBackAndReturnsFailure` et par la
   sentinelle `android-attach-rollback-restores-state`.
5. **La décision de thread est définitive et n’est pas rouverte** ; la forme du rejet des autres
   admissions est passée de l’exception de phase 0 à la taxonomie `KadreResult` de phase 1 — c’est
   la livraison planifiée, pas une révision de la décision.

## 4. Différées de phase (au regard de §8)

Les lignes restantes de la matrice de `BACKEND-CAPABILITIES.md` §4 applicables à Android ne sont
pas produites par les phases 1 et 2. §8 rappelle qu’« une ligne manquante empêche l’adapter d’être
déclaré supporté » : ces lignes restent donc à produire avant toute déclaration « supported » de
l’adapter Android, chacune par la phase de la roadmap qui la possède. Aucune de ces lignes n’est
ici une décision de capability : la colonne « cellule normative » rappelle ce que la matrice §4
promet à l’adapter Android View/Activity une fois sa phase livrée, pas ce qui est publié
aujourd’hui.

| feature (cellule §4, Android View/Activity) | cellule normative | phase propriétaire | note de lecture phases 1-2 |
|---|---|---|---|
| clavier, pointer, touch (stylet compris) ; les quatre champs `SurfaceUpdate` d’input-delivery (`cursor`, `pointerCapture`, `hitTesting`, `inputDefaultBehavior`) — rejetés par champ `Unsupported(UpdateSurface)` depuis la phase 2, sans appeler aucune primitive | C | **Phase 3** | aucun listener n’est posé sur la View ; aucun champ `SurfaceUpdate` n’a de chemin de commit (§2) |
| actions de redraw en débordement pilotées par les policies de delivery — le coalescing de phase 2 émet au plus un événement par frame et le débordement du flux borné reste un drop documenté, jamais une failure | G | **Phase 3** | la delivery policy du runtime n’est pas consultée par le scheduler (§2) |
| observation focus/visibilité par événements SDK réels — si un besoin est démontré au-delà de l’observation lifecycle (qui les publie aujourd’hui) ; les phases ultérieures décident, la matrice n’est pas rouverte | — | **Phases ultérieures** | le binding métrique laisse `visible`/`focused` à `null` et ne concurrence pas le lifecycle (§2, remarque 2) |
| commandes de fenêtre réelles et interactions transitoires ; affinement multi-fenêtres/dialogues du conjonctif « fenêtre interactive » de `Active` | Activity : C ; View : `— (aucune Window)` ; `WindowCapabilities.platformAccess` = N(PlatformWindowAccess) | **Phase 4** | la fenêtre primaire Activity existe (topologie §2) mais chaque commande répond `Unsupported` de son opération et son `platformAccess` reste `Unsupported(PlatformWindowAccess)` — aucun handle `android.view.Window` ; `requestWindow` est rejeté dans les deux formes — le rejet est livré, les interactions ne le sont pas ; le conjonctif « fenêtre interactive » de `Active` est dérivé du lifecycle seul (§2, remarque 3) |
| IME / text input, gestures reconnues par le host | C | **Phase 5** | aucun port texte ni recognizer |
| drag-and-drop et ownership des transferts | C | **Phase 6** | aucun pont de drag |
| inventaire display complet, fallback `HostViewport`, devices/gamepads (observation et effets), décision raw input, pression mémoire | inventaire/fallback/observation/effets/pression : C ; raw input : C (décision séparée, sinon N(RawInputAccess)) | **Phase 7** | les managers restent à leurs états par défaut non supportés du runtime ; aucun port display/device/gamepad n’est passé |
| capture targets `HostChoice`, `Source`, `Surface` | C | **Phase 8** | aucun chemin de capture, aucun consentement |
| façade Java (`KadreAndroid.attach`, `KadreSessionHandle`), liaison Compose | §6.1 + `INTEROP-EXPORTS.md` | **Phase 9** | la surface publique de phase 1 est Kotlin uniquement |
| gate CI Android obligatoire (job `android-contracts`) | §9 (gate adapter officiel) | **Phase 10** | `androidContractsCheck` est un gate local à émulateur, volontairement hors `:kadre:check` |

Une ligne de frontière, pas une différée : **le rendu lui-même** est hors périmètre de l’adapter
Android — la phase 2 livre une surface observable (`state`/`capabilities`/`events`, `requestRedraw`
coalescé) **sans posséder son rendu** ; aucun renderer, aucune surface de rendu, aucun event loop
public n’est ajouté, et aucune phase de la roadmap n’en ajoutera.

## 5. Références

- [Contrats des adapters et matrice de capabilities](../BACKEND-CAPABILITIES.md) — mandat §8, matrice §4 (colonnes Android), topologie §3/§6.1
- [Roadmap d’implémentation Android](../ANDROID-IMPLEMENTATION-ROADMAP.md) — §3.3 (frontière runtime/SDK, contrat de thread), §Phase 0 (décision de thread), §Phase 1 (gate de sortie), §Phase 2 (gate de sortie — metrics vs View réelle, identité de surface, redraw borné, zéro accès SDK après fermeture)
- [Registre des contrats](../contracts/registry/contracts.tsv) — lignes `AND-001` (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 0`), `AND-002` (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 1`) et `AND-003` (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 2`), preuve `android`
- [Driver du contrat Android](../contracts/driver/android/README.md) — comment relancer le gate localement, étiquetage moteur, limites de phase 2
- [Design Kadre](../DESIGN.md) — §15.1 (les quatre overloads Android que la phase 1 livre), lifecycle à trois axes (`Active` = attaché + foreground + fenêtre interactive)
