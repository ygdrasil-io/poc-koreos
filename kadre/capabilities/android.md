# Registre versionné d’implémentation — adapter Android (`org.graphiks.kadre.platform.android`)

**Mandat.** `BACKEND-CAPABILITIES.md` §8 impose à chaque adapter de produire, avec son
implémentation, un fichier public `capabilities/<adapter>.md` contenant exactement une ligne par
feature de la matrice de sa section 4, avec les colonnes `feature`, `target`, `minimum déclaré`,
`compile gate`, `runtime gate`, `état absent` et `tests`.

**Première édition, phase 0.** Ce registre naît avec la phase 0 de la roadmap Android
(`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 0 — Variante Android et première preuve sur émulateur`) :
il couvre exactement ce que la phase 0 livre — l’attach View minimal, sa session et son teardown —
et liste **différées de phase** (§4) toutes les features de la matrice §4 applicables à Android que
les phases suivantes possèdent. Chaque phase ultérieure réécrit les lignes qu’elle livre, comme le
registre Web l’a fait phase après phase. Une feature différée n’est **jamais** une « absence
structurelle » : la matrice normative n’est pas réouverte, aucune capability finale n’est décidée
avant sa phase, et la colonne `état absent` des lignes livrées décrit le rejet de phase 0, pas un
verdict sur l’adapter.

**Portée de lecture.** Un seul target publie cette variante : `androidJvm` — l’attribut
`org.jetbrains.kotlin.platform.type = androidJvm` des variantes AGP-KMP des modules publiés
(`kadre`, `kadre-android`, `foundation`, `foundation-android`, `runtime`, `runtime-android`,
`kadre-platform-android`). La toolchain est celle de la convention du dépôt : `compileSdk = 35`,
`minSdk = 24`, bytecode JVM 17 (`kadre/platform/android/build.gradle.kts`, miroir de l’umbrella) ;
le compilateur Kotlin est celui du catalogue (2.4.20) et le consumer épinglé du driver le lit avec
KGP 2.4.0. Le moteur de la preuve est l’émulateur **AVD `Kadre_API_35`** (image système
**arm64-v8a**, **API 35**), étiqueté par le driver depuis le device réellement attaché —
`adb shell getprop ro.build.version.sdk` + `ro.product.cpu.abi`, jamais codé en dur — ce qui donne
l’identifiant exact **`emulator-api-35-arm64-v8a`** (`AND-001.json`, champ `adapter:
"android-emulator"`). Aucun autre moteur n’est déclaré, et un moteur non relevé ne se déduit pas
d’un passage manuel. Cette passe de preuve a été produite sur un hôte **macOS arm64**, qui est le
contexte d’exécution de la preuve et non un minimum : la gate CI obligatoire (job
`android-contracts`) est la phase 10 de la roadmap et n’existe pas encore ; le plancher déclaré de
ce registre est l’API level (`minSdk = 24`), l’OS de l’hôte n’y entre pas.

## 1. Lecture des colonnes

- **feature** — le nom du membre public, tel que la surface de phase 0 le déclare
  (`attachKadre`, `KadreAndroidAttach.kt`) ou la ligne de la matrice §4 concernée.
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
  capability*. Pour la phase 0 c’est l’émulateur identifié : la preuve `AND-001` n’existe que
  branchée à un device réel (`androidContractsCheck` exige un émulateur démarré et n’est
  volontairement pas dans `:kadre:check` — aucun émulateur sur les runners actuels, branchement CI
  = phase 10).
- **état absent** — la valeur exacte observée quand la feature est refusée, sans paraphrase. La
  phase 0 rejette par exception `IllegalStateException` nommant le champ fautif **avant toute
  mutation d’état** (décision de phase 0, §3 ci-dessous) ; la taxonomie complète des failures
  d’admission (`InvalidRequest("parentScope")`, `ParentScopeCancelled`, `AlreadyInUse(Host)`)
  arrive en phase 1 avec les quatre overloads de `DESIGN.md` §15.1. Pour une feature **différée de
  phase**, la colonne ne décrit aucune valeur publiée — il n’existe aucun chemin public qui la
  lise à ce stade — et renvoie à la phase propriétaire (§4) : « différée » est un état de
  livraison de la roadmap, jamais une capability `Unsupported` décidée.
- **tests** — les identifiants de tests qui portent la ligne, en deux familles : les
  `evidenceId` de `kadre/contracts/driver/android/contracts/evidence.tsv`, c’est-à-dire des
  scénarios instrumentés réellement exécutés sur l’émulateur
  (`KadreConsumerSessionTest`, runner `AndroidJUnit4`, résolution de l’umbrella **publié**
  `org.graphiks.kadre:kadre` depuis le repository contractuel) ; puis la classe d’hôte
  `AndroidAttachStateTest` (`androidHostTest`, sans device) qui asservit la machine d’état
  d’attach. Les sentinelles de `AND-001`
  (`android-stop-no-resurrection`, `android-double-attach-single-owner`) sont portées par le
  scénario `android-consumer-double-attach-rejected`, comme le mapping `evidence.tsv` l’enregistre.

## 2. Registre — phase 0

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| `attachKadre(view, parentScope)` — attach View minimal, opt-in (`@OptIn(DelicateKadreApi::class)`, KDoc « phase 0 uniquement ») | `androidJvm` | API 24 | `:kadre:platform:android:compileAndroidMain` — lit `android.view.View`, `android.os.Looper`, `kotlinx.coroutines.CoroutineScope` ; friend-wiring des classes Android de `foundation` et `runtime` | `AND-001` / `androidContractsCheck` — un émulateur identifié (`emulator-api-35-arm64-v8a`) doit être démarré ; la preuve est produite à HEAD publié (`--refresh-dependencies`) | violation de précondition = `IllegalStateException("attachKadre precondition violated (field: …)")` levée **avant toute mutation d’état**, champ nommé dans l’ordre normatif `mainThread` → `view` (attachement) → `view` (ownership) ; rejet post-admission (`parentScope` sans Job actif ou annulé) = `IllegalStateException("attachKadre could not start the session (…)")` après libération zéro-résidu des ponts et de l’ownership ; la taxonomie `InvalidRequest`/`ParentScopeCancelled`/`AlreadyInUse(Host)` est différée de phase 1 (§4) | `android-consumer-session-start`, `android-consumer-double-attach-rejected`, sentinelles `android-double-attach-single-owner` ; `AndroidAttachStateTest.preconditionViolationsReportFieldsInNormativeOrder`, `.ownershipRegistryReclaimsAfterRelease`, `.doubleAttachRejectedWithoutStateMutation` |
| session lifecycle start/stop (View) — `KadreAndroidViewSession.stop()`/`close()` idempotents, `AutoCloseable` | `androidJvm` | API 24 | idem | idem — le scénario de stop s’exécute sur le même device identifié | `—` (livrée) : une session retournée a démarré (`Starting` → `Running` sur `session.state : StateFlow<SessionState>`) et `stop()` publie `Stopping` puis `Terminated(SessionOutcome.Stopped(SessionStopReason.HostRequested))` de façon synchrone ; un second `stop()` est absorbé (`stopped` guard) ; après stop, `release` post-terminal ne réanime rien et l’ownership libéré rend le re-attach possible | `android-consumer-session-start`, `android-consumer-session-stop-cleanup`, `android-consumer-double-attach-rejected`, sentinelles `android-stop-no-resurrection`, `android-double-attach-single-owner` ; `AndroidAttachStateTest.stopClearsBridgesAndBecomesTerminated`, `.releaseUnknownViewIsRejected` |

Deux remarques de lecture pour que la table reste exacte. D’abord, l’attach de phase 0 construit
une **session Kadre réelle** — la couture `RuntimeHostController.withPrimarySurface` +
`controller.attach`, friend-wirée sur les internals du runtime exactement comme `WebHostSession`
(`KadreAndroidAttach.kt:128-169`) — avec une application vide qui suspend
(`KadreApplication { awaitCancellation() }`) et une surface primaire minimale
(`RuntimeHostSurface` adossé à l’instantané initial de la `View`, fermé en `Detached`). L’état de
surface publié est un instantané figé copié de la `View` à l’attach (dimensions bornées au
minimum valide 1×1 quand la View n’est pas encore mesurée, densité lue de
`resources.displayMetrics`) : **aucun observer lifecycle, input, interaction ou IME n’est
installé sur la View** — l’observation réelle arrive en phase 1, et c’est pourquoi aucune ligne
`SurfaceCapabilities`/`InputCapabilities` ne figure dans ce registre à ce stade. Ensuite, le
journal zéro-résidu est le mécanisme de preuve du teardown : `AndroidViewBridge.install` journalise
chaque pont dans `AndroidAttachState` et `teardown()` le vide (`view.tag = null`, placeholder de
phase 0 — aucun listener résiduel ne doit subsister), ce que les sentinelles de `AND-001`
asservissent depuis l’émulateur.

## 3. Le contrat de thread d’attach — décision de phase 0

La roadmap (`ANDROID-IMPLEMENTATION-ROADMAP.md` §3.3 et §4, ligne « Thread d’attach et propriété
des callbacks ») exige que le contrat d’appel d’`attachKadre` soit **fixé en phase 0**, avant toute
phase dépendante : « aucun blocage main-thread ni relocation implicite ne sera introduit pour
contourner une ambiguïté ». La décision livrée, consignée dans le KDoc d’`attachKadre`
(`KadreAndroidAttach.kt:44-51`) et asservie par `AndroidAttachStateTest` :

1. **Thread principal Android uniquement.** L’appel vérifie
   `Looper.myLooper() == Looper.getMainLooper()` ; un appel hors thread principal est rejeté
   `mainThread` avant toute autre vérification et avant toute mutation d’état.
2. **Non suspendant, sans blocage ni relocation.** `attachKadre` ne suspend pas, ne bloque pas le
   caller et ne déplace jamais l’appel vers un autre thread ; le dispatcher applicatif reste celui
   du `parentScope`, ce qui ne donne pas le droit d’appeler le SDK UI depuis n’importe quel
   thread.
3. **Ordre normatif des champs fautifs** (`validateAttachPreconditions`,
   `AndroidAttachState.kt:36-45`) : `mainThread` → `view` (View non attachée à une fenêtre) →
   `view` (View déjà revendiquée). Tous rungs en échec, le champ rapporté est le premier de cet
   ordre — épinglé par
   `AndroidAttachStateTest.preconditionViolationsReportFieldsInNormativeOrder`.
4. **Rejet avant toute mutation d’état.** La validation précède `AndroidViewOwnership.claim`,
   `AndroidAttachState.tryClaim` et l’installation des ponts ; un rejet post-admission (échec de
   `controller.attach`) libère ponts et ownership **avant** de lever — zéro résidu observable.
5. **Forme du rejet en phase 0 : exception.** `IllegalStateException` nommant le champ fautif.
   La taxonomie complète (`InvalidRequest("parentScope")`, `ParentScopeCancelled`,
   `AlreadyInUse(Host)`, champs `"surfaceView"` des overloads Activity) est la livraison de la
   phase 1 avec les quatre overloads — la décision de thread, elle, est définitive et n’est pas
   rouverte.

## 4. Différées de phase (au regard de §8)

Les lignes restantes de la matrice de `BACKEND-CAPABILITIES.md` §4 applicables à Android ne sont
pas produites par la phase 0. §8 rappelle qu’« une ligne manquante empêche l’adapter d’être déclaré
supporté » : ces lignes restent donc à produire avant toute déclaration « supported » de l’adapter
Android, chacune par la phase de la roadmap qui la possède. Aucune de ces lignes n’est ici une
décision de capability : la colonne « cellule normative » rappelle ce que la matrice §4 promet à
l’adapter Android View une fois sa phase livrée, pas ce qui est publié aujourd’hui.

| feature (cellule §4, Android View) | cellule normative | phase propriétaire | note de lecture phase 0 |
|---|---|---|---|
| les quatre overloads `attachKadre` de `DESIGN.md` §15.1 (`ComponentActivity` ×2, `View` + `LifecycleOwner`) et la taxonomie d’admission (`InvalidRequest("parentScope")`, `ParentScopeCancelled`, `AlreadyInUse(Host)`) | §3 de `BACKEND-CAPABILITIES.md` + §6.1 | **Phase 1** — Attachement public, ownership et lifecycle | la phase 0 n’expose que l’attach View minimal opt-in (§2) ; l’entrée publique à quatre overloads la remplace |
| observation lifecycle (axes foreground/active, détachement terminal, recréation de configuration) | `lifecycle à trois axes` = G | **Phase 1** | la session phase 0 démarre et s’arrête (§2) ; aucun axe n’est observé sur la View |
| surface metrics + redraw request (`SurfaceUpdate`, `requestRedraw`) | G | **Phase 2** | l’instantané initial est figé (`initialSurfaceState`, aucune relecture) |
| `SurfaceCapabilities.platformAccess` — lease `withAndroidView` | G, `withAndroidView` | **Phase 2** | aucun accès borné à la View n’existe en phase 0 |
| clavier, pointer, touch (stylet compris) | C | **Phase 3** | aucun listener n’est posé sur la View |
| fenêtre de l’Activity et interactions transitoires (cursor, pointer capture, interactions) | Activity : C ; View : `— (aucune Window)` ; `WindowCapabilities.platformAccess` = N(PlatformWindowAccess) | **Phase 4** | la cellule View de la matrice enregistre déjà qu’il n’y a rien à produire côté `Window` sur un host View ; les interactions sont différées avec le domaine |
| IME / text input, gestures reconnues par le host | C | **Phase 5** | aucun port texte ni recognizer |
| drag-and-drop et ownership des transferts | C | **Phase 6** | aucun pont de drag |
| inventaire display complet, fallback `HostViewport`, devices/gamepads (observation et effets), décision raw input, pression mémoire | inventaire/fallback/observation/effets/pression : C ; raw input : C (décision séparée, sinon N(RawInputAccess)) | **Phase 7** | les managers restent à leurs états par défaut non supportés du runtime ; aucun port display/device/gamepad n’est passé |
| capture targets `HostChoice`, `Source`, `Surface` | C | **Phase 8** | aucun chemin de capture, aucun consentement |
| façade Java (`KadreAndroid.attach`, `KadreSessionHandle`), liaison Compose | §6.1 + `INTEROP-EXPORTS.md` | **Phase 9** | la surface publique de phase 0 est Kotlin uniquement |
| gate CI Android obligatoire (job `android-contracts`) | §9 (gate adapter officiel) | **Phase 10** | `androidContractsCheck` est un gate local à émulateur, volontairement hors `:kadre:check` |

## 5. Références

- [Contrats des adapters et matrice de capabilities](../BACKEND-CAPABILITIES.md) — mandat §8, matrice §4 (colonnes Android), topologie §3/§6.1
- [Roadmap d’implémentation Android](../ANDROID-IMPLEMENTATION-ROADMAP.md) — §3.3 (frontière runtime/SDK, contrat de thread), §4 (décision « Thread d’attach en phase 0 »), §Phase 0 (gate de sortie)
- [Registre des contrats](../contracts/registry/contracts.tsv) — ligne `AND-001` (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 0`, preuve `android`)
- [Driver du contrat Android](../contracts/driver/android/README.md) — comment relancer le gate localement, étiquetage moteur, limites de phase 0
- [Design Kadre](../DESIGN.md) — §15.1 (les quatre overloads Android que la phase 1 livre)
