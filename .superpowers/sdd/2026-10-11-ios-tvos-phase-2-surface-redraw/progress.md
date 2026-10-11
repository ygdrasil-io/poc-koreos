# SDD ledger — plan: docs/superpowers/plans/2026-10-11-ios-tvos-phase-2-surface-redraw.md

BASE au démarrage : ac4dc4dd (origin/master, phase 1 mergée via PR #441 squash ac4dc4dd). Branche : zcode/sess-56645e3f.
Commit plan : 3476566c. Phase 1 : PR #441 MERGED 2026-10-11, 4/4 checks verts (iOS 16m29 budget 40 min, cache ~/.konan en place).

## Rulings pré-vol

- Ruling: **architecture port-stimulus conservée** — PAS de classe HostSurface à la Android ; les observations alimentent `stimulusSink`, le redraw retient le ticket du manager, `apply` passe à l'échelle fermée, `publicSurfaceCapabilities=true`. Coût si faux : une refonte phase 3 — atténué par le fait que la roadmap phase 2 nomme « ports de surface UIKit » et que le reducer runtime reste l'autorité.
- Ruling: `withUIKitView` route par **`UIKitViewLeaseRegistry` (class instantiable + `shared`, par SurfaceId)** — la `RuntimeWindowSurface` est runtime-owned et ne peut pas implémenter un port uikit (miroir Android impossible) ; la roadmap §3.1 l.109 sanctuarise le « registre technique de propriétaires » qui route des callbacks identifiés. Coût si faux : une API de lease moins directe — la façade reste identique.
- Ruling: **plancher iOS/tvOS 16.0 conservé** — `registerForTraitChanges` (17+) est un push ADDITIONNEL gated runtime (`isOperatingSystemAtLeastVersion`) ; en 16, re-lecture sur notifications de scène. Coût si faux : pas de push theme en 16 — le theme reste relu à chaque observation du binding (chemin partagé testé).
- Ruling: scale change (écran externe) et mode-change tvOS = **manual charter** (aucun stimulus programmeable fiable sur simulateur) — câblés mais non prouvés par test ; documentés dans capabilities/uikit.md. Coût si faux : un canal d'observation non exercé en CI — même statut que le hardware charter Android.
- Ruling: Model Selection inapplicable (pas de champ model) — hérité phases 0-1.

## Scan pré-vol (paires fichiers/interfaces)

| Paire | Produit ↔ Consomme | Constat |
|---|---|---|
| T1 ↔ T3/T4 | scheduler+capabilities+apply dans le port → binding/lease s'y rattachent | Port = point d'entrée natif unique (décision) |
| T2 ↔ T3 | kernel pur `uikitSurfaceApply`/`UIKitSurfaceObservation` → binding publie via kernel | Signatures fixées dans « Produces » T2 ; template verbatim AndroidSurfaceMetrics.kt |
| T3 ↔ T5 | observations KVO/notifications → stimuli réels du driver (resize/rotation/theme) | Review Focus 2 : échec si aucune MetricsChanged (jamais de figé déclaré vert) |
| T4 ↔ T5 | registre+façade lease → tests lease boundaries | Swift : lire le header exporté ; fallback via DriverKadreApplication documenté |
| T5 ↔ T6 | noms figés → evidence.tsv/registry | 8 scénarios partagés BCK-015 + 1 rotation BCK-016 (règle de couverture par requiredTarget — leçon phase 1) |
| T0-merge (ac4dc4dd) ↔ T1 | phase 1 : port avec self-confirm redraw, apply TemporarilyUnavailable, publicSurfaceCapabilities=false | T1 remplace ces postures — conflit de responsabilité identifié et voulu |

Auto-cohérence : T1 scheduler injectable testable ; T2 kernel pur ; T4 registre instantiable (`shared` companion, miroir UIKitSceneRegistry) ; T6 scission BCK-016 anticipée dès le registre. Contraintes globales cohérentes (jamais d'invention, callback borné, révocation = plus rien).
### Task 1
Task 1: complete (commits 3476566c..d0b9292b, review clean — 4 mineurs)
Task 1: minor (deferred): scheduler mono-slot + surfaceId du premier peer — Task 3+/multi-fenêtres devra passer à des schedulers par surface (flag phase 3).
Task 1: minor (deferred): fenêtre d'absorption admission/cancel non atomique — bénigne (surface terminalisée derrière) ; ASSERTER en Task 5 (aucune surface vivante avec ticket absorbé).
Task 1: minor (deferred): APPLY_LADDER_STATE neutre — l'Applied(current) du kernel est jeté à la conversion outcome ; ne pas y faire confiance quand un vrai commit arrive.
Task 1: minor (deferred): branches défensives mortes (fieldOutcome error(), requireRefused) — fail-loud correct tant que les 4 capacités sont Unsupported.
- Faits : foundation ajoutée au friend-modules natif (main+test+metadata — SurfaceRevision internal constructor) — miroir androidMain/webMain ; StaleRevision jugé par le runtime avant le port (MinimalWindowSurface l.264-273) ; Clear intercepté côté port avant le kernel (kernel `check` fail-loud).
### Task 2
Task 2: complete (commits d0b9292b..1e99b7c6, review clean — 3 mineurs embarqués en Task 3)
Task 2: minor (ride-along T3): KDoc de totalité surexploité (2e9 × scale 2 déborde Int dans toPhysical) — adoucir le KDoc ou borner ; Task 3 ne doit PAS s'appuyer sur la totalité du kernel pour la gestion d'erreur.
Task 2: minor (ride-along T3): guards insets right/bottom/left non épinglés (seul top l'est) — ajouter l'assertion.
Task 2: minor (ride-along T3): changement COMBINÉ (taille+scale/insets+theme en une observation) non épinglé — ajouter une assertion (le kernel fusionne par construction).
### Task 3
Task 3: complete (commits 1e99b7c6..f08f8f39, review clean — ride-alongs présents et killants)
- **Découvertes plateforme confirmées au compilateur (revue indépendante)** : KVO inimplémentable en K/N 2.4.20 pur (observeValueForKeyPath = catégorie non formelle, NSKeyValueObservingProtocol absent des klibs, aucun membre surchargeable) ; registerForTraitChanges unreachable (UITraitChangeObservableProtocol non hérité — unresolved sur UIView ET UIWindowScene). Modèle retenu : re-lectures sur triggers SDK visibles (foreground/activate/mode-change + lecture initiale postée) ; push 17+ codé, compile-proof, inerte (seam dormant — s'activera quand K/N exposera la conformance).
- **Ruling (modèle d'observation) : « changements entre triggers observés au prochain trigger » = limite documentée** (précédent web phase 2 : dpr sampled only on observation + manual charter). Task 5 : resize/theme tests déclenchent la re-lecture via la notification (post didActivate sur la scène vivante — même statut que le fallback disconnect de phase 1) ; capabilities/uikit.md porte la limite (état absent).
- Important (plan-level, préexistant devenu déterminant) : failureReporter no-op dans KadreIos.kt:121 — tous les échecs d'observation y funnelt sans signal (parité Android : AndroidAttachDiagnostics). FIX avec Task 5 (wire un vrai reporter).
Task 3: minor (deferred): miroir avance avant stimulusSink (perte de sink = dédup masque la republication) — commenter ou sink-first au prochain toucher du fichier.
Task 3: minor (deferred): onVisibility soumet à chaque re-read même inchangé (dédup runtime porte la correction — vérifiée réelle).
Task 3: minor (deferred): garde single-binding (2e requestOpen = surface vivante sans binding) — documenté KDoc, surprise latente.
### Task 4
Task 4: complete (commits f08f8f39..f7fdd5d2, review clean — 1 Important reporté Task 5 + 2 mineurs)
- Important-1 (fix avec Task 5) : KDoc de UIKitSurfaceAccess.kt:19-20/30 promet Closed(Surface) post-close — l'implémentation répond DÉTERMINISTEMENT Unsupported(PlatformSurfaceAccess) (route de registre retirée au close, l.147-156 avant publication détachée). Les deux outcomes sont dans l'ensemble fermé §7.1. Task 5 doit pinner Unsupported comme assertion post-close steady-state (Closed seulement en race in-flight/port-level) + KDoc amendé.
Task 4: minor (deferred): check de register dupliqué et identité `shared` non testés (assertions 1 ligne).
Task 4: minor (deferred): « re-lecture hors verrou » du brief implémentée comme 2e acquisition de verrou (équivalent ou plus fort — forcé par la layout UIKit, documenté).
- Concurrency posture validée : single-flight atomique sous verrou, ensureActive avant leaseHeld, started-completes ≡ Android, unregister avant publication détachée, pas de cycle de verrous.
### Task 5
Task 5: fix round 1/5 ouvert — Important-1 : fausse déclaration (withUIKitView EST exporté, KadreUikit.h:7075, appelable depuis Swift ; la route sonde reste légitime mais la justification documentée est fausse) → corriger KDoc du test + rapport ; également la phrase façade phase-3 « aucun pointer n'apparaît dans les exports Swift » contredite (UIView* exporté — artefact Task 3, note follow-up).
- Important-2 (mapping Task 6) : le kill de uikit-rotation-metrics-faithful est CONDITIONNEL — aujourd'hui seule la branche dégradée s'exécute (géométrie publiée == live post-aspect-swap porte le kill) ; Task 6 ne doit PAS l'enregistrer comme killed-by-real-rotation.
Task 5: minor (deferred): rotation test — 2e stimulus n'drain pas de second MetricsChanged (state==live garde le kill power, plus faible que resize) ; theme tvOS ambient-Dark : seule la transition →Light publication-prouvée ce run (charter note) ; IntArray capture workaround au folklore inexpliqué en production (sonde opt-in, convention phase-0).
- CI faits : DriverSceneRequest activation gate (bug infra phase 1 : requête 2e scène avant activation → primaire inactive pour toujours) corrigé minimalement ; TEST_RUNNER_* env requis sur la tâche iOS canonique.
### Task 5
Task 5: fix round 1/5 (1 addressed, 0 open; commit 47182561 docs-only)
Task 5: complete (commits f7fdd5d2..47182561, review clean)
- Important-2 (POUR TASK 6) : le kill de uikit-rotation-metrics-faithful est CONDITIONNEL — seule la branche dégradée (aspect-swap, géométrie publiée == live) porte le kill ; NE PAS enregistrer killed-by-real-rotation ; la branche réelle est codée et s'activera sur un runtime qui accepte requestGeometryUpdate (3 refus capturés : 101 ×2, BSActionErrorDomain).
Task 5: minor (deferred): rotation 2e stimulus sans drain de second MetricsChanged (state==live garde le kill) ; theme tvOS ambient-Dark : transition →Light seule publication-prouvée (charter) ; IntArray capture workaround folklore en production (sonde opt-in).
- Phase-1 infra fix (DriverSceneRequest activation gate) : bug réel — requête 2e scène depuis willConnectTo avant l'activation → primaire inactive pour toujours ; corrigé minimalement, multi-scène 10/10 déterministe. TEST_RUNNER_* env requis sur la tâche iOS canonique.
- Faits export : withUIKitView EST exporté (KadreUikitUIKitSurfaceAccessKt, completionHandler-based) — la route sonde reste la preuve retenue (justification corrigée) ; la phrase façade « aucun pointer dans les exports Swift » est contredite (UIView* exporté) — follow-up phase 3.
### Task 6
Task 6: complete (commits 47182561..34a5a594, review clean — 3 mineurs)
Task 6: minor (deferred): arithmétique du brief (9 vs 10 JSONs ; iOS 6 ids pas 5) — résolue par l'implémenteur, listes suivies verbatim.
Task 6: minor (deferred): capabilities compressent quelle phase meurt en premier sous le mutant rotation (full-window equality vs 240×320) — même seam, phrasé légèrement glossé.
Task 6: minor (deferred): stale BCK-010.json récurrent dans build/ (gitignored, hors exact-set du validateur).
- Observation : la moitié scheduler du coalescing n'a PAS de surface de kill vivante (masquée par design par le ticket-gate runtime — defense-in-depth) ; le comptage single-per-tick est couvert en baseline verte + le kill au seam d'admission.
### Task 7
Task 7: complete (commits 34a5a594..87c4f6b5, DESIGN §15.2 + BACKEND-CAPABILITIES §6.2 édités ; CATALOG déjà correct ; INTEROP sans statut livré — rien à éditer)
### Task 8
Task 8: complete (aucun commit — sweep pur; revue PASS, critère de sortie Phase 2 MET à 87c4f6b5)
- Sweep discipline : --rerun forcé partout (UP-TO-DATE trap), fraîcheur vérifiée par timestamps XML + champ commit des JSONs.
- Mineurs sweep : BCK-010.json stale (gitignored, hors exact-set) récurrent ; uikit module K/N tests hors gate CI (observation — candidat gate) ; push = contrôleur.
### Revue finale
VERDICT: MERGE — 1 Important (F1 : KDoc UIKitSurfaceAccess.kt:39 affiche « Kotlin-only / aucun pointer dans Swift » alors que withUIKitView + leaseProbe exportent UIView* dans le header ; résolution phase 3 = audit façade, pré-merge = reword 1 ligne) + F2 mineur (capabilities l.156 « en permanence » vs modèle à déclencheurs — 1 ligne). 20 mineurs triés : 7 CLOSED (vérifiés sur la branche dont failureReporter + absorption assert + mapping rotation), 12 PARK, 1 observation gate-backlog (uikit K/N tests hors CI).
- D3 : requestRedraw non-main SAFE (dispatch_async thread-safe) ; publicSurfaceCapabilities=true = deltas voulus uniquement ; terminalise republish unsupported à la fermeture (aligné Android).
