# SDD ledger — plan: docs/superpowers/plans/2026-10-10-ios-tvos-phase-1-attach-lifecycle.md

BASE au démarrage : 303c9d67 (origin/master, phase 0 mergée via PR #436 squash 303c9d67). Branche : zcode/sess-56645e3f (worktree /Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f).
Commit plan : ca4e5fed. Phase 0 : PR #436 MERGED 2026-10-10, tous checks verts.

## Rulings pré-vol

- Ruling: Model Selection SDD inapplicable (pas de champ model sur l'Agent de ce harnais) — tous les sous-agents héritent du modèle de session ; fix-loop = re-dispatch frais avec brief plus précis. (hérité de phase 0)
- Ruling: source de lifecycle = NSNotificationCenter filtré par `object = scene` (pas de conformité UISceneDelegate Kotlin — le host garde son delegate) ; les tests postent les notifications réelles sur la scène vivante (frontière SDK exacte de l'adapter) ; iOS prouve en plus le disconnect RÉEL par requestSceneSessionDestruction. Coût si faux : un parcours tvOS « trop synthétique » — atténué par le parcours hardware en cahier manuel et par la preuve iOS réelle.
- Ruling: scènes iOS-only (`uikit-two-scenes-independent`, `uikit-disconnect-terminates`) : rows evidence iOS-only si le validateur accepte une couverture partielle par scenario, sinon scission BCK-014 — Task 6 Step 2 tranche EN LISENT ContractEvidenceMapping et reporte.
- Ruling: comparaison Swift `===` sur objets Kotlin originaires (primarySurface === primary.surface) tenue pour fiable (objets du tas Kotlin = objets Obj-C natifs ; l'inversion du pont — objets HOST re-wrappés — est le seul piège prouvé phase 0). L'implémenteur Task 5 re-dérive et reporte si surprise.

## Scan pré-vol (paires fichiers/interfaces)

| Paire | Produit ↔ Consomme | Constat |
|---|---|---|
| T1 ↔ T2 | ports/flux en commonMain → RuntimeWindowManager/Surface en dépendent | Ordre d'extraction respecté (3.4→3.3→3.2 puis 3.5→3.1) |
| T2 ↔ T4 | RuntimeWindowManager + RuntimeWindowSurface communs → composition UIKitWindowRuntime | Signatures = note phase 0 (task-8-report §3.1/3.5, verbatim) |
| T3 ↔ T4 | registry/ownership/lifecycle source → ladder KadreIos.attach | Signatures fixes dans « Produces » T3 |
| T4 ↔ T5 | KadreIos.attach + protocoles exportés → Swift DriverApplication/tests | Noms Swift à relire dans le .h généré (instruit) |
| T5 ↔ T6 | méthodes XCTest figées → evidence.tsv/registry | Noms figés listés dans T5 Step 3 ; mapping lu depuis le XML produit |
| T6 ↔ T7 | gate asserts 3 ids → mode `all` complet | Script boucle les ids |
| T0-merge (303c9d67) ↔ T1 | master a déjà les fichiers en jvmMain ; Web phase 7 + Android phase 0 déjà fusionnés | RAS — aucun des 5 fichiers touché par web/android |

Auto-cohérence : T2 decision tree ThreadLocal/ReentrantLock bornée et à reporter ; T4 sketch avec adaptations obligatoires marquées (noms réels à lire) ; T6 règle de couverture scenario/target à lire dans le validateur. Contraintes globales cohérentes (pas de duplication reducer, AlreadyInUse avant installation, Detached terminal, isEqual).
### Task 1
- Ruling: `kotlin.concurrent.AtomicBoolean` n'existe pas en 2.4.20 → `kotlin.concurrent.atomics.AtomicBoolean` + `@OptIn(ExperimentalAtomicApi::class)` (traitement fermé du plan pour AtomicLong, généralisé). `.get()` → `.load()` (pas de `.value`).
- Ruling: `ExperimentalAtomicApi` SANS cible FILE → l'OptIn va sur la classe (Task 2 doit faire pareil — la prescription `@file:OptIn` du plan est illégale).
- Ruling: 3 constructs JVM supplémentaires portabilisés selon le tableau de décisions : moniteurs synchronized (13 sites) → RuntimeLock.withLock ; `catch (LinkageError)` ×2 → `isLinkageFailure()` else-rethrow (primitive phase 0) ; `Math.addExact` → checkedAdd. Champ gardé sous son nom `owners` (renommage non mécanique refusé).
### Task 1
Task 1: complete (commits ca4e5fed..5e7b2ea2, review clean — 3 mineurs)
Task 1: minor (deferred): rapport dit 12 sites synchronized, vrai compte 13+1 (comptage texte seulement, 0 site manqué).
Task 1: minor (deferred): checkedAdd lève IllegalStateException là où Math.addExact levait ArithmeticException (idiome phase 0 ratifié, aucun handler en amont ne distingue).
- ⚠️ non-vérifiables acceptés: compiles natifs/wasmJs (attestés, plausibles — primitives avec actuals existants), tests non-cold (up-to-date avec hash d'inputs liés au commit).
### Task 2
- Ruling (ratifié par revue) : **fait contraignant mis à jour** — l'actual JVM de RuntimeLock est désormais `synchronized(this)` / `Thread.holdsLock(this)` (moniteur = l'instance, plus d'objet interne). Justifié par le test lock-order reflectif RuntimeWindowManagerTest.kt:1978 ; aucun user ne peut observer l'identité du moniteur autrement que par réflexion. Tasks 3+ héritent de cette forme.
- Ruling: ThreadLocal → branche (b) : nouveau `internal expect class RuntimeCallFrame<T>` (jvm ThreadLocal / apple @ThreadLocal map par instance / js-wasm champs) + 3 tests natifs nouveaux, verts sur les 2 simulateurs.
- Ruling: await ReentrantLock+Condition → exécution directe sous RuntimeLock (raisonnement thread-par-thread vérifié par le reviewer : un seul thread de dispatch en production, handoff mutex ≡ signalAll/recheck, deferral drainé dans le finally).
- Ruling: `elapsedNanos()` n'existe pas en kotlin.time commun → markNow/elapsedNow + coerceAtLeast(ZERO) (sémantique monotone conservée).
Task 2: complete (commits 5e7b2ea2..b19f2cf3, review clean — 5 mineurs)
Task 2: minor (deferred): comptes de tests du rapport inexacts (83 réels, pas 82).
Task 2: minor (deferred): non-collision à deux instances de l'apple RuntimeCallFrame garantie par construction, pas directement testée.
Task 2: minor (deferred): wedge latent préexistant conservé (active flag coincé si acceptInteraction lève dans le finally) — parité, pas régression ; future task.
- ⚠️ non-vérifiable accepté: XML natifs antérieurs au fix jvmCommon (jvmCommon n'est pas un input apple — le sweep Task 8 relancera sur l'arbre final).
### Task 3
Task 3: fix round 1/5 ouvert — Finding 1 (Important) : reducer latch/merge (copy) sur 3 branches au lieu du remplacement absolu ; DidActivate depuis (Bg,Inactive) produit (Background,Active) qui viole l'invariant LifecycleState (init throw). Fix : 4 branches absolues + test renforcé qui tue la mutation latch.
Task 3: minor (deferred): race d'enregistrement du token dans UIKitLifecycleSource.observe (close() dans la fenêtre) — main-queue only en pratique ; commentaire ou enregistrement sous lock.
Task 3: minor (deferred): flag `released` de UIKitSceneReservation sans synchronisation (idempotence portée par le registry) — miroir WebHostReservation, informationnel.
Task 3: fix round 1/5 (1 addressed, 0 open — reducer absolu + test renforcé tuant la mutation latch; commits 6cae62fb..f276c9b8)
Task 3: complete (commits b19f2cf3..f276c9b8, review clean)
- Faits interop pour Task 4 : UISceneActivationState = typedef Long + const vals top-level ; constantes de notification = String? (!!) ; NSObjectProtocol dans platform.darwin ; axes foundation réels = VisibilityState{Foreground,Background} + ActivationState{Active,Inactive} (Lifecycle.kt:39-40) ; friend-modules metadata 3-klib déjà en place (compileUikitMainKotlinMetadata).
### Task 4
Task 4: complete (commits f276c9b8..b7987602, review clean — 3 mineurs)
- Ruling (revue) : runBlocking pontant requestWindow est SÛR conditionnellement — la reprise passe par le BlockingEventLoop du runBlocking (pas Dispatchers.Main) qui est pompé par joinBlocking ; INVARIANT À SURVEILLER : aucun withContext(Dispatchers.Main) ne doit jamais entrer dans le chemin d'admission du manager (deadlock sinon). Watchlist Task 5+.
- Ruling: export(project(":kadre:foundation")) requis (KadreSession absente du header sinon) ; tire TOUTE l'API publique foundation dans le header Obj-C (7049 lignes) — à ne pas confondre avec la façade promise (audit phase 3).
- Ruling: UIKitSceneRegistry.shared (singleton process) est la forme correcte — le registre par attach du plan n'aurait jamais détecté le double-attach.
- Ruling: requestWindow retourne Success même pour un rejet post-admission → double check manager.state.primary != null fail-closed, correct.
Task 4: minor (deferred): pas d'assertion de thread principal à l'entrée de attach (check(Dispatchers.Main.isCurrent()) recommandé) — à trier en finale.
Task 4: minor (deferred): détail de diagnostic perdu sur le chemin commit-failure (aplani en create-failed) ; failureReporter no-op — reporter réel en phase 2.
Task 4: minor (deferred): windowStateSink/resources du port volontairement morts en phase 1 (symétrie AppKit, phase 2 en hérite).
- ⚠️ non-vérifiables acceptés: builds (artefacts frais + header), comportement runtime du pont runBlocking (preuve source-level ; Task 5 doit le prouver sur device), énumération complète des chemins terminaux SessionRuntime.
- Faits pour Task 5 : header exporté — KadreIos.shared.attach(windowScene:window:surfaceView:applicationFactory:policy:) ; protocoles KadreApplication (l.233), KadreApplicationFactory (l.244), KadreSession (l.330) ; KadreResultSuccess<T> avec .value ; l'api requestOpenedClose retourne Accepted (pas de Closed dans l'outcome).
### Task 5
- Ruling (blocker 1) : le défaut Task 4 est réel et sa remédiation uikit-only est la bonne — admission primaire DIFFÉRÉE : un délégué `RuntimeSessionWindowManager` dans la factory intercepte `installSessionConfiguration` (forward puis admission `requestWindow` — toujours via le reducer, toujours avant Running, surface même instance). Autorisation EXPRESSE de modifier le Kotlin de Task 4 dans ce seul but (le diff sera re-revu).
- Ruling (blocker 2) : **destination d'evidence iOS = iPad Pro 11-inch (M5)** — l'iPhone (idiom téléphone) ne peut jamais exécuter le multi-scène (« The current device does not support multiple scenes ») ; la roadmap exige « une configuration iPad multi-scène ». Le script gagne l'iPad comme destination iOS d'evidence (iPhone reste dispo via override env pour les checks locaux). L'activation XPC iPad depuis le test host : itérer — (a) activation pilotée par l'APP au lancement via flag d'environnement de lancement (pas depuis le bundle de test), (b) délai après foreground-active, (c) si l'environnement sim iOS 27 échoue encore : STOP + rapport aux contrôleur avec les erreurs exactes des deux tentatives (escalade, pas de faux/skip/affaiblissement).
### Task 5 — fix round 1 (suite)
- Ruling (blocker restant) : **fallback notification-postée ACCEPTÉ pour testSceneDisconnectionTerminatesOnlyItsSession** dans cet environnement (requestSceneSessionDestruction = no-op silencieux sim+XCTest, prouvé des deux contextes). Conditions : (1) le test garde son nom et TOUTES ses assertions (session 2 Terminated, session 1 intacte, objets host intacts, re-attach possible) ; (2) le fallback est documenté dans le commentaire du test + le rapport + capabilities/uikit.md (preuve de destruction réelle = cahier manuel sur device, phase 10) ; (3) cohérent avec le précédent phase 0 (notifications = frontière SDK de l'adapter). Le routage par identité (object=scene2) reste pleinement exercé.
- Constat implémenteur retenu : SurfaceId.description est rédigé (<redacted>) côté Kotlin → les fixtures comparent par isEqual (durcissement d'evidence bien venu).
- État : tvOS 9/9 vert ; iOS iPad 9/10 (le test 6 rouge en attente du fallback) ; remédiation Kotlin 1384f1fe (délégué RuntimeDeferredAdmissionWindowManager, RuntimeSessionWindowManager devenu public, primarySurface lazy provider) + a809c978.
### Task 5 — fix round 1 clos
Task 5: fix round 1/5 (3 rulings addressed, 0 open; commits b7987602..25b21be2 : 1384f1fe remédiation Kotlin, a809c978 driver, 25b21be2 fallback disconnect)
Task 5: complete (review clean)
- Ruling exécuté : fallback disconnect (notification réelle object=scene2, tentative réelle d'abord, documenté test+rapport+capabilities) ; destination evidence iOS = iPad Pro 11-inch (M5).
- Découverte implémenteur (validée revue) : SurfaceId/SessionId .description RÉDIGÉS côté Kotlin (comparaisons par string vides !) — equality = value-based equals/isEqual côté Swift ; l'ancien XCTAssertNotEqual sur descriptions était cassé (deux fois la même chaîne rédigée). Comparaisons maintenant isEqual/===.
- ⚠️ POUR TASK 6 : TROIS scénarios iOS-only (uikit-two-scenes-independent, uikit-disconnect-terminates, uikit-attach-window-mismatch) — rows evidence iOS-only à écrire pour les trois ; lire la règle de couverture du validateur (ContractEvidenceMapping) avant d'écrire ; scinder BCK-014 si nécessaire.
Task 5: minor (deferred): paramètre timeout mort destroySceneSession (15s vs inverted-wait 5s codé en dur) ; SceneFixture.fixture(connectedAfter:) inutilisé.
### Task 6
Task 6: complete (commits 25b21be2..6bec0820, review clean — 5 mineurs d'entretien)
- Découvertes : la règle de couverture du validateur (ContractEvidenceMapping.kt:71-138) exige chaque scenario sur CHAQUE requiredTarget → scission BCK-014 « UIKit iPadOS multi-scene » (iOS-only, 3 scenarios + sentinelle uikit-window-scene-owner) ; BCK-013 garde 6 scénarios partagés ×2 targets + 2 sentinelles ; WIN-009 2×2 + 1 sentinelle. 26 rows d'evidence, 7 JSONs (4 iOS / 3 tvOS).
- Prise de contrôle après dispatch tué : audit des 26 rows contre les vraies méthodes XCTest ; 2 défauts du prédécesseur corrigés (gate 4× xcodebuild/famille → 1 invocation ; commentaires BCK-013 périmés → BCK-014) ; la mutation de sentinelle laissée dans KadreIos.kt a été revertée.
- 7 preuves can-fail citées et revertées ; cascade 17-échecs de uikit-window-scene-owner jugée acceptable (débris de l'invariant violé, kill sur sa propre assertion d'abord).
Task 6: minor (deferred): le commit d'activation déplace la suite BCK-012 (phase 0) sur l'iPad (documenté dans capabilities) ; message de commit omet BCK-014 (message prescrit par le plan) ; stale BCK-010.json dans build/ (gitignored) ; JSONs épinglés au SHA parent (re-épinglés au prochain gate) ; assert_artifacts seulement en mode all (comportement phase 0 préservé).
### Task 7
Task 7: complete (commits 6bec0820..2f5e1be8, review clean — mineurs : commentaire warm-cache exact seulement sur run froid/CI ; apostrophes typographiques ; KadreIos→KadreIos.attach dans le catalog)
- Gate `all` vert de bout en bout : XCTest iOS 12 / tvOS 9, 166 tests natifs runtime (83×2), 7 preuves BCK-012/013/014/WIN-009 validées, links device, exit 0.
### Task 8
Task 8: complete (aucun commit — sweep pur; revue clean)
- CRITÈRE DE SORTIE PHASE 1 : REMPLI à HEAD 2f5e1be8 (confirmé indépendamment).
- Minor inexactitude rapport : 25 suites AppKit XML, pas 30 (le décisif 371/0 est juste).
- Mineurs sweep : stale BCK-010.json gitignored (purge au gate = pass future) ; fallback destruction sim ratifié (charter hardware phase 10) ; warning uikitTest dependsOn(commonTest) cosmique.
### Revue finale
VERDICT: MERGE — approve, 1 fix pré-merge (Finding 1 : check(Dispatchers.Main.isCurrent()) à l'entrée de KadreIos.attach — protège l'invariant runBlocking + la prémisse main-queue du lifecycle source + toutes les lectures UIKit). 20 mineurs parkés (triage complet au rapport).
- Watch items phase 2 : (a) premier run CI réel de la suite iPad (timing vs 15 min), (b) couverture CI des tests natifs runtime (aujourd'hui local `all` seulement), (c) audit façade phase 3 vs l'export foundation intégral.
- D3 vérifié : pas de risque js/wasm (RuntimeCallFrame par instance, manager par session) ; RuntimeLock synchronized(this) inobservable hors réflexion ; uikit hors agrégats officiels.
### CI PR #441 — premier run
- uikit-tvos ✅ 7m28, appkit ✅, web-contracts ✅ ; **uikit-ios ❌ 15m56 (timeout dur)** avec les 12 tests en échec « scene readiness not reached in 10.0s » (window=nil, willConnectTo jamais délivré sur l'iPad headless du runner xcode-27 ; la même suite passe 3× en local iPad). Le timeout a tué la QUEUE (junit+validators après les échecs), pas la cause.
- Ruling (budget) : timeout-minutes du job iOS 15 → 25 (édition normative TEST-STRATEGY §9 avec justification : run à froid mesuré >15 min ; le SLO p95 8 min reste celui des gates chauds).
- Ruling (debug) : instrumenter d'abord (os_log des callbacks de scène côté app + état DriverSceneRequest dans le dump du fixture), puis itérer les hypothèses via runs CI (~16 min/run, max 4 itérations) : H1 la demande de scène 2 au lancement casse la connexion de la scène 1 sur headless (la rendre conditionnelle à un ready-handshake explicite du test) ; H2 premier boot du device iPad frais sur le runner (pré-boot dans le workflow avant le script) ; H3 particularité headless iPad (le précédent tvOS headless passe).
### CI-debug (post-revue finale)
- Commits 88a39ec8 (budget 25 min + TEST-STRATEGY §9.1 note normative datée + instrumentation scène) et 8757de3f (pré-boot iPad avant le gate) — revus : Approved, MERGE.
- Cause racine CI (prouvée) : premier boot du device runner ~1m24s (MobileGestaltMigrator) — fenêtre où la scène 1 se connecte sans jamais s'activer ; H1 (demande scène 2) réfutée PAR TRACE ; H2 pré-boot corrige. iOS 12/12 vert sur CI (12m49).
- CI PR #441 : 4/4 verts sur 8757de3f (ios 12m49, tvos 6m42, web 7m50, appkit 7m03 après re-run d'un flake AppKit close-retry documenté — propriétaire distinct).
- Mineurs parkés : stderr de simctl boot jeté (2>/dev/null) ; nom d'iPad dupliqué workflow/script (env var partagée serait robuste) ; flake AppKit close-retry = watch item propriétaire AppKit.
- 3 adaptations SDK iOS 27 dans le driver Swift (UISceneConfiguration.delegateClass propriété, persistentIdentifier String, UIScene.session non-optionnel) — sémantique inchangée, vertes 2 familles.
### CI PR #441 — second conflit + budget
- Conflit registre avec Android Phase 2 (#439, AND-003) résolu — purement additif, aucune collision (04f4954c).
- Le job iOS a de nouveau buté (annulé à 25 min, xcodebuild en cours) : coûts fixes à froid variables — pré-boot 2m30 + `simctl list` ~4m30 (enregistrement devices) + configuration Gradle ~4m30 avant premier compile. Fix : budget iOS 40 min (TEST-STRATEGY §9.1 exception mise à jour) + cache actions/cache ~/.konan sur les 2 jobs UIKit. CI verte : iOS 16m29 / tvOS 6m05 / web 7m41 / appkit 5m21 (65f78515).
