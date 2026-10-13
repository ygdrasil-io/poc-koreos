# SDD ledger — plan: docs/superpowers/plans/2026-10-11-ios-tvos-phase-3-swift-facade.md

BASE au démarrage : 9081f923 (origin/master, phase 2 mergée via PR #443 squash 9081f923). Branche : zcode/sess-56645e3f.
Commit plan : 2d9946a7. Phases 0-2 : mergées (#436, #441, #443). Contrats actifs UIKit : BCK-012/013/014/015/016 + WIN-009.

## Rulings pré-vol

- Ruling: **distribution statique** — XCFramework de distribution STATIQUE + framework Kotlin du consumer STATIQUE, liés (zéro embed) dans l'app consumer : une seule copie du runtime (anti-duplication, roadmap l.225), symboles au préfixe KadreUikit conservés. Le driver reste sur son framework DYNAMIQUE embeddé (intouché). Coût si faux : crash de classes dupliquées au lancement du consumer — détecté immédiatement (Task 2 front-load).
- Ruling: factory du consumer externe = **module Kotlin du consumer** (roadmap l.225 « factory produite par son module applicatif Kotlin ») ; la conformité Swift directe du driver reste supportée (non promise, pas interdite — INTEROP §5 l.140).
- Ruling: **audit façade par suppression** — `@HiddenFromObjC` sur `withUIKitView` (INTEROP §2 l.34 pré-autorise ; le driver n'appelle PAS withUIKitView depuis Swift — preuve phase 2 par sonde Kotlin) ; les sondes driver restent VISIBLES et documentées scaffolding (la surface promise §5 n'en contient aucune ; l'en-tête complet est inventorié). Coût si faux : la divergence F1 persiste dans le header — l'audit la re-ouvre.
- Ruling: `integration:swiftui` NON matérialisé (contenu réel absent du module) — le wrapper representable ~10 lignes vit dans le consumer (INTEROP §5 l.139).
- Ruling: profils Swift → `KadrePolicies.Default/Realtime/Recording` (Realtime/Recording peuvent échouer avec UnsupportedPolicy → KadreError, POLICY-PROFILES).
- Ruling: Model Selection inapplicable (hérité).

## Scan pré-vol (paires fichiers/interfaces)

| Paire | Produit ↔ Consomme | Constat |
|---|---|---|
| T1 ↔ T2 | xcframework statique 4 slices → lien consumer (zéro embed) | T2 front-loadé (risque duplication) |
| T2 ↔ T3-T5 | squelette consumer (apps + XCTest + KadreHost par chemin) → façade TDD dedans | KadreHost.swift référencera les symboles via swift_name — indépendant du préfixe Obj-C |
| T3 ↔ T4 | value model fermé → handle/attach | §5 verbatim |
| T4 ↔ T5 | KadreIosHost.attach + handle → controller | |
| T5 ↔ T6 | controller → representable (app-owned ~10 lignes) | |
| T7 ↔ phases 1-2 | @HiddenFromObjC withUIKitView → preuve phase 2 par sonde Kotlin (intacte) | driver n'appelle pas from Swift — vérifié phase 2 |
| T0-merge (9081f923) ↔ T1 | phase 2 livrée : port redraw/apply/lease, publicSurfaceCapabilities=true | T7 audite le header post-phase-2 |

Auto-cohérence : T1 probe l'assemblage AVANT T2 ; T2 consumer-kmp resolves uikit via maven contractTest (uikit publie) ; le sketch ConsumerApplication est en suspend (jamais runBlocking — deadlock) ; T7 la suppression @HiddenFromObjC ne casse pas la preuve phase 2 (sonde Kotlin).
### Task 1
- Faits KGP 2.4.20 (déviations de nommage du plan, à propager T2/T5/sweep) : tasks réels `assembleKadreUikitDebugXCFramework`/`assembleKadreUikitReleaseXCFramework` (parent `assembleKadreUikitXCFramework`) ; sortie `build/XCFrameworks/debug|release/` en LOWERCASE ; binaire de lien `KadreUikitDistribution` mais baseName `KadreUikit` (slices = KadreUikit.framework — préfixe Obj-C préservé) ; pas de classe XCFramework — extension Project.XCFramework(name) → XCFrameworkConfig.
Task 1: complete (commits 2d9946a7..6ebc5b70) — revue à venir.
### Task 1
Task 1: complete (commits 2d9946a7..6ebc5b70, review clean)
- Faits T2 : xcframeworks à `build/XCFrameworks/{debug,release}/KadreUikit.xcframework` (4 slices statiques) ; tasks `assembleKadreUikit{Debug,Release}XCFramework` ; NE PAS câbler `embedAndSignKadreUikitDistributionAppleFrameworkForXcode` dans les consumers (statique = non-embeddable) ; frameworks de sortie disjoints du driver (aucune collision Gradle).
### Task 2
- Faits consommateur (révisés, ÉTABLIS) : KGP 2.4.20 ne permet PAS l'override du préfixe Obj-C (objcExportPrefix supprimé — 3 expériences de contrôle) → le framework du consumer est LUI AUSSI baseName "KadreUikit" (mêmes noms de symboles, classe factory consumer-specific) ; export(foundation) requis avec export(uikit) pour l'identité ; UNE SEULE module Swift visible (le consumer, sur-ensemble) — la distribution xcframework est liée PAR CHEMIN dans OTHER_LDFLAGS (link-only) ; l'attach se fait au premier sceneDidBecomeActive (scène Unattached au willConnectTo sur iPadOS 27) ; le vrai contrat KadreApplication = `fun interface { suspend fun KadreScope.run() }` + create(context).
- Important F1 (embarque en T3) : les commentaires « un doublon serait refusé par ld » sont FAUX (ld garde silencieusement le premier membre définissant) — les vrais gardes : le check runtime du log (build.gradle.kts:120-124) + la recette test-bundle sans lien. Corriger les 2 commentaires en T3.
- F2 (formulation T3-T6) : dire « les deux archives coexistent dans un binaire avec une copie de chaque classe et identité de types à la frontière factory » — pas « l'app exécute l'implémentation de la distribution ».
- Formulation T6 : BCK-014 ET BCK-016 sont iOS-only → tableau iOS = 6 ids (BCK-012/013/014/015/016/WIN-009), tvOS = 4.
Task 2: complete (commits 6ebc5b70..dec701a0, revue clean — F1/F2 embarqués T3, F3 stationné)
### Task 3
Task 3: complete (commits dec701a0..ce5c7d34, review clean — 5 mineurs)
- Arbitrage M3 (propriétaire du plan) : §3 « message n'entre jamais dans l'égalité » vs §5 `KadreError: Equatable` avec message stocké — §5 VERBATIM gagne (implémenté ainsi, 17 textes épinglés par test) ; l'arbitrage est consigné dans le KDoc + test.
- M1 (structurel) : pas d'exhaustivité compile-time possible sur la dispatch Kotlin→Swift (protocole + classes importées, pas de switch scellé) — tail fatalError ; TOUTE diff de header touchant la famille KadreFailure doit étendre le mapper dans le même changement (note Task 4+).
- M5 (Task 4) : remplacer le round-trip sourceLost par une vraie source runtime quand attach/expose le permet.
Task 3: minor (deferred): rapport dit 24 KadreResourceKind, réel 23 (code/tests corrects) ; InteractionFailureReason sans préfixe Kadre (suit la lettre de §3/§6 + TS module) ; audit en-tête = revue de diff à chaque évolution famille failure.
### Task 4
Task 4: complete (commits ce5c7d34..d51a7e38, review clean — 4 mineurs)
- §9 négatif vert (aucun Flow/Scope/Continuation/Throwable/Any/FFI dans la surface publique ; la factory Kotlin pass-through est la signature §5 elle-même) ; main-thread enforced (precondition) ; exactly-once par construction (NSLock 3-états, settle idempotent) + épinglé (outcome-won via awaits non-coursées, cancellation-won 50×, late-completion no-op exercé 50×).
Task 4: minor (deferred): coroutine collect dormante après retrait sur session VIVANTE (coût par changement d'état jusqu'à fin de session ; churn non borné) — le vrai fix = collect annulable côté Kotlin (phase future) ; dormant après terminaison = borné.
Task 4: minor (deferred): boucle de race à sens unique par construction (requestStop+cancel même tour main) — variante cross-thread avec délai aléatoire = durcissement futur.
Task 4: minor (RIDE-ALONG T6): observeState inscrit APRÈS terminaison (première émission = terminated) non épinglé — test bon marché à ajouter en T6.
Task 4: minor (deferred): fuite de ~50 fenêtres par run du race loop (test-only, borné).
### Task 5
Task 5: complete (commits d51a7e38..9c55897c, review clean — 5 mineurs)
- Déviation acceptée : sous-classe privée KadreHostContentView pour didMoveToWindow (UIViewController n'a pas le hook) — une seule override, zéro layout, la view passée à attach EST self.view (prouvé isEqual via leaseProbe + fixture runCount).
Task 5: minor (deferred): close() AVANT premier attach ne consomme pas le budget d'attach (attachState reste .waiting/.retryArmed → un attach ultérieursurprend) — 1 ligne (attachState = .settled dans close()) ; à trier en finale.
Task 5: minor (deferred): disconnect observer retiré seulement au deinit (close() le laisse — weak self + close idempotent, bénin).
Task 5: minor (deferred): ConsumerFixture single-slot (2 sessions concurrentes corrompraient les faits capturés — impossible au layout process actuel, runCount rend le staleness bruyant).
Task 5: minor (deferred): antichambre -only-testing, 1er disjunct sessionObject==nil passe avant le launch attach (course théorique pré-lancement, pratiquement fermée).
### Task 6
Task 6: complete (commits 9c55897c..d1369552, review clean — 3 mineurs)
- Le gated-skip est légitime : assertions pleine force dans les runs gated dédiés (TEST_RUNNER_KADRE_CONSUMER_SWIFTUI) ×2 familles ; le representable est exercé dans TOUS les runs (make-once + identité + attach==1).
- Mutations armées par le prédécesseur tuées en run rouge réel (.id(epoch) → make 4≠2 + identité cassée ; observeState post-terminaison silence → 0 deliveries) puis revertées.
Task 6: minor (deferred): ConsumerSwiftUIRootView.body effectue un effet de bord pendant body (noteEpoch non @Published — pas de boucle) ; chemin brief Sources/ vs apps/Shared (choix correct) ; explication makeCountBefore imprécise (ordre alphabétique).
### Task 7
Task 7: complete (commits d1369552..b0c55c77, review clean — 3 mineurs)
Task 7: minor (deferred): la row distribution cite les tasks Debug+Release mais seul Debug a été réassemblé contre la source auditée (Release jamais reconstruit — même source, preuve locale, row disclaim déjà signed publication).
Task 7: minor (deferred): édition du commentaire de test hors liste de fichiers du brief (déclaration de déviation, comment-only).
Task 7: minor (deferred): skew cosmétique de timestamp du grep capabilities (valeurs reproduites).
- F1 (phase 2) CLOSE par suppression : withUIKitView grep=0 dans les 2 headers simulateur + les 4 slices xcframework ; UIKitSurfaceAccessKt disparu (le fichier n'avait que cette déclaration) ; la règle INTEROP §7 tient désormais pour l'en-tête aussi ; amendement INTEROP §7 verbatim ; sondes visibles-documentées ; en-tête audité = 483 @interface / 101 @protocol / 7197 lignes ; Inventaire d'audit §3 dans capabilities/uikit.md.
### Task 8
Task 8: complete (aucun commit — sweep pur; revue PASS, jalon A MET à b0c55c77)
- Sweep : --rerun-tasks jvm (408/0), natives 83+83, appkit 373, gate uikit SUCCESS (10/10 evidence re-pinnées), consumers 46+46 ×2 familles, gated SwiftUI 1/0 ×2.
- Mineurs sweep : race 0/50 dans le contrat documenté du test (outcome path prouvé non-concurrent) ; BCK-010.json stales récurrents ; pas de preuve Instruments (weak/strong assertions = permis par le brief) ; modules K/N uikit 21+21 up-to-date (même commit, arbre propre — légitime).
- Divergence origin 9/13 : push = contrôleur.
### Revue finale
VERDICT: APPROVED WITH ONE PRE-MERGE CONDITION.
- F-1 (Important, cross-task gap — le plan promettait BCK-017/018 sans tâche de registre) : ruling = ENREGISTRER BCK-017/018 comme `planned` (statut légal sans preuve, miroir WIN-008/APK-016) + note « l'activation attend la pipeline d'evidence consumer (export JUnit des suites consumer + corrélation validateur) » ; scénarios documentés dans la row. Coût si faux : un consumer contract promis jamais activé — visible au registre, pas silencieux.
- F-3 (Important par triage, must-fix) : close() avant premier attach ne consomme pas le budget d'attach — 1 ligne (attachState = .settled) + test (close avant fenêtre → join ultérieur n'attache PAS).
- F-2 (CI consumer gate) : ruling = DEFERRAL enregistrée (capabilities note « le gate consumer tourne localement via l'agrégateur ; câblage CI = follow-up ») — câbler un workflow = changement CI plus large, pas du scope du fix.
- F-4/F-7/F-8 : park (freshness guard recommandation, caveat dynamic+static, Release rebuild avant distribution).
### Revue finale — fix wave
Task 8bis: fix round 1/5 (3 addressed, 0 open; commits b0c55c77..7f5c9b4f : 38c74ab8 F-1 registry planned, de132989 F-3 close-budget, 7f5c9b4f F-2 deferral doc)
- Re-revue : F-1/F-3/F-2 ADDRESSED, new breakage none (nit : un id de scénario paraphrasé dans la row planned BCK-017 — inoffensif sans preuve épinglée).
Phase 3 : complète (12 commits 9081f923..7f5c9b4f). Push + PR = contrôleur.
