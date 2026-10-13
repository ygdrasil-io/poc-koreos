# SDD ledger — plan: docs/superpowers/plans/2026-10-12-ios-tvos-phase-4-window-requests.md

BASE : b8433e5a (origin/master, phase 3 mergée PR #446). Branche : zcode/sess-56645e3f. Commit plan : 8a48cb2f.
Phases 0-3 mergées. Contrats actifs UIKit : BCK-012/013/014/015/016 + WIN-009 ; planned : BCK-018 (façade), BCK-019 (consumers).

## Rulings pré-vol

- Ruling: **modèle Web-shape async** (pas AppKit same-session) — scènes enfants = sessions complètes (leur propre UIKitWindowRuntime) ; le scene-manager host-side supplante requestWindow du demandeur ; pending async réel sans timeout (roadmap l.240). Le manager runtime garde la primaire (phases 1-3 intactes).
- Ruling: provider **app-owned** (`UIKitWindowSceneProvider`) — l'adaptateur n'appelle AUCUNE API d'activation (roadmap l.237 « isoler dans iosMain » = rien à isoler) ; tvOS = apps sans provider → chemin null-provider → Rejected(Unsupported(RequestWindow)) avant réservation.
- Ruling: child attach = runtime enfant propre + KadreLaunchInfo(AdditionalHostRequested, requestId) + factory/policy du REQUÉRANT (RuntimeHostController.attach param optionnel, web l.612-614).
- Ruling: BCK-020 (lifecycle requests, 2 familles) + BCK-021 (corrélation iPadOS, iOS-only) ; WINDOW-apply/attention/close hors périmètre (fenêtres enfants ≠ Window Kadre).
- Ruling: Model Selection inapplicable (hérité).

## Scan pré-vol

| Paire | Produit ↔ Consomme | Constat |
|---|---|---|
| T1 ↔ T2 | scene-manager + provider → câblage attach | param default null (tvOS/apps inchangées) |
| T1 ↔ T3 | manager/provider/resolver → provider Swift + scénarios | noms figés listés T3 Step 2 |
| T2 ↔ T3 | capabilities OpenedInNewSession → scénarios | |
| T3 ↔ T4 | noms figés → evidence | rows par requiredTargets (leçon phase 1) |
| T0-merge (b8433e5a) ↔ T1 | phase 3 : port mono-primaire, facade, consumer-kmp | T1 n'exige PAS de chirurgie multi-fenêtres du port (enfants = runtimes propres) — simplification clé vérifiée contre UIKitWindowCommandPort (les 8 points mono-slot de l'exploration ne s'appliquent pas au demandeur) |
Auto-cohérence : T1 kernel injectable (childSessionFactory faux en test) ; pending async sans timeout ; les 5 Review Focus ont leurs tests.
### Task 1
Task 1: fix round 1/5 ouvert — Critical : provider-throw → terminate(Rejected) SANS removal de l'entrée → resolve tardif attache un enfant non-owned (le path provider-throw est le SEUL terminal sans removal). Fix : terminate en CAS (retourne won/lost) + completeResolve traite lost comme raced-signal (stoppe l'enfant). + 2 tests manquants (slot retention sur provider throw ; resolve après provider throw asserte l'arrêt enfant).
- Important : terminate check-then-act non atomique (writers hors verrou : provider-throw, drain forEach, abandon) — même fix CAS (le web-verbatim était single-threaded, K/N non).
Task 1: minor (deferred): slot-retention sur provider throw non épinglé + asymétrie cancel-vs-abandon sur terminal-at-handoff ; dead code test (nextTestSessionId, closeCalls non asserté) ; lateinit request (closed hierarchy — nullable equally cheap).
### Task 1
Task 1: fix round 1/5 (2 addressed, 0 open; commit 47d64cc1 — terminate CAS Pending→Terminated, claimResolve refuse les entrées terminales avant attach, raced = settled==null || !terminateWon, 3 nouveaux tests dont 2-act provider-throw→late-resolve)
Task 1: complete (commits 8a48cb2f..47d64cc1, re-revue ADDRESSED ×2, no new breakage)
- Residual : provider-throw gagnant le CAS sur un cancel → resolver non abandonné à l'app (cohérent early-return ; late resolve refusé sans risque).
- Task 3 doit pinner : KadreLaunchInfo threading (aucune couverture exécutable en T1 — fake impossible sans objets UIKit vivants).
### Task 2
Task 2: complete (commits 47d64cc1..5ad3b699, review clean)
- Faits Task 3 : (1) static frameworks = pas de default-arg Swift → 2 call sites Swift passent windowSceneProvider: nil explicitement ; (2) les scènes willConnectTo sont Unattached → la résolution provider doit se faire à un point de lifecycle post-attach (didActivate) ou la validation Unattached à KadreIos.kt:149 doit être revisitée pour l'enfant — DÉCISION TASK 3 ; (3) le child attach passe par attachScene(..., launch) — le chemin ordinaire réentré.
- Le scene-manager implémente et forward RuntimeSessionWindowManager (seam casté par RuntimeSessionComponents — sinon l'admission primaire ne tourne jamais silencieusement) ; 2 tests de seam.
- closeAction requester = sceneManager.close() (drain pendings UNIQUEMENT — les enfants résolus intacts) + runtime.close() ; enfant clos par (a) disconnect scène enfant (b) terminaison session enfant ; (c) PAS par close du requester ✓.
Task 2: minor (deferred): parentScope du scene manager déclaré jamais utilisé.
### Task 3
Task 3: complete (commits 5ad3b699..2bae2a4, review clean — 2 Important évidence + 5 mineurs)
- Wiring fix validé par trace complète : l'admission primaire (admitPrimaryWindow → runtime.manager.requestWindow) ne traverse JAMAIS le scene manager (référence directe au runtime manager) — le refus null-provider ne s'applique qu'au chemin externe scope.windows.requestWindow. Aucune régression phase 1-3.
Task 3: Important (report Task 4/6) : cancel-before-connect passe dans LES DEUX branches sans les distinguer (lateResolve false → assertions d'identité skippées silencieusement) — ajouter assert activationErrors.isEmpty ou fail si aucune branche ne se matérialise.
Task 3: Important (report Task 4/6) : tvOS « no scene activation » prouvé structurellement seulement — ajouter XCTAssertEqual(DriverFixture.shared.connected.count, ...) fermerait le trou à pas cher.
Task 3: minor (deferred): pumpNextActivation ne re-pompe pas quand isBusy se libère (non atteint dans les scénarios figés) ; FIFO par position (corrélation désynchronisée si connexion non liée dans la fenêtre — charter phase 10) ; nom figé requester-close-detaches-child = enfant SURVIT au close (suit le brief l.240 — documenter la sentinelle Task 4 « detach = détaché du destin du requester ») ; helper duplication attach/window-request (délibéré).
### Task 4
Task 4: complete (commits 2bae2a4..daddcb8, review clean — 2 mineurs)
- Split machine-forcé validé : BCK-020 (refus, 2 targets, 1 scenario + 1 sentinelle) / BCK-021 (iPadOS corrélé, iOS-only, 6 scenarios + 2 sentinelles) ; le brief original était machine-impossible (cancel-before-connect n'a pas de testcase tvOS).
- Renforcement cancel (reviewer T3) implémenté précisément : +1 assertion (lateResolve || !activationErrors.isEmpty), rien retiré ; non-satisfiable vacuument (errors clearées au tearDown).
- Mutant M2 (pending jamais libéré) skippé légitimement : borne 16 au driver = 16 scènes inatteignables — couverture kernel réelle citée (3 tests kernel).
Task 4: minor (deferred): rapport dit 60 tests manager, réel 28 @Test ; assert cancel chargé (15s vs 30s interne — échec honnête sur runner chargé, à surveiller CI).
### Task 5
Task 5: complete (commits daddcb8d..72abe375 — DESIGN §15.2 progression + BACKEND-CAPABILITIES §6.2 livré phase 4 + CATALOG row provider/resolver)
- Parked drift : le bloc signature §15.2 n'affiche pas windowSceneProvider (param réel phase 4) — 1 ligne doc, à faire en Task 6.
### Task 6
Task 6: complete (90550a15 DESIGN signature one-liner committé AVANT le sweep ; revue PASS — jalon B MET à 90550a15)
- Flake consumer iOS (32/47 « host app never finished launching », re-run 47/0 même HEAD) : classification environmental raisonnable (diff = 1 ligne doc, binaire identique au state vert T3/T5) — MAIS nouvelle signature de flake à tracker (launch precondition du harness consumer) ; CI = retry par destination recommandé.
- BCK-010.json stale : récurrent, hors gate, purge quand pratique.
### Revue finale
VERDICT: APPROVE MERGE, condition = finding C (la hardening tvOS connected.count reportée T4/6 jamais landée — 1 ligne). + A (main-thread non enforce sur provider.open) et B (reset ordering driver) = phase-5 hygiene. 15 triés : 3 CLOSED, 12 PARK.
- F-2 CI consumers : toujours pas de workflow consumers/swift (suivi enregistré).
