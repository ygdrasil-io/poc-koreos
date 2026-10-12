# SDD ledger — plan: docs/superpowers/plans/2026-10-12-ios-tvos-phase-5-input.md

BASE : 5251cb8b (origin/master, phase 4 mergée PR #449). Branche : zcode/sess-56645e3f. Commit plan : 0c258fe4.
Phases 0-4 mergées. Contrats actifs UIKit : BCK-012/013/014/015/016/020/021 + WIN-009 ; planned BCK-018/019. Famille Wayland : BCK-017, SES-001, SUR-001. Libre : BCK-022+, INP-004+, WIN-010+.

## Rulings pré-vol

- Ruling: **touch sans sous-classe host** — recognizer Kadre-owned (subclass UIGestureRecognizer, Meta ×2 familles) ajouté additivement, `cancelsTouchesInView = false` (le host garde ses touches — décision tracée KDoc) ; SPIKE T2 : override des handlers propres (touchesBegan etc.) doit compiler/fire, sinon repli Pan-composite documenté au contrôleur.
- Ruling: **clavier physique = GCKeyboard.keyChangedHandler** (additive, notification-driven, pas de responder chain) ; addKeyCommand (shortcuts, first-responder) hors v1 ; le clavier intégré iPad (non-GameController) = limite documentée jusqu'à preuve du contraire.
- Ruling: **télécommande = GCMicroGamepad/GCController closures** (additives) ; XCUIRemote = UI-testing (hors bundle unit) → charter manuel pour le bout réel.
- Ruling: hover/pointer = UIHoverGestureRecognizer iOS-only (tvOS klib = 0) ; pas de route pointer tvOS v1 (documenté).
- Ruling: focus moteur = hors périmètre v1 (le focus surface reste piloté par les transitions de scène phase 1 ; le détournement du moteur de focus est interdit par roadmap l.259).
- Ruling: tests driver = probe counters (pattern phase 2) + mappers kernel ; touch réel/multitouch/IME = charter manuel (le touch n'est pas synthétisable in-process honnêtement).
- Ruling: Model Selection inapplicable (hérité).

## Scan pré-vol

| Paire | Produit ↔ Consomme | Constat |
|---|---|---|
| T1 ↔ T2/T3 | mappers purs → bindings UIKit-typed | pattern kernel/binding phase 2 |
| T2 ↔ T4 | binding touch → probe counters driver | compteurs = pattern phase 2 (UIKitObservationCount) |
| T3 ↔ T4 | bindings keyboard/gesture/hover → contracts | split BCK-022/023 selon requiredTargets réels (leçon phase 1) |
| T0-merge (5251cb8b) ↔ T2 | phase 4 : requestWindow/pending — T2 touche le PORT (stimulusSink + peer lifecycle) | l'installation au peer lifecycle suit le pattern d'observation phase 2 (install après commit, teardown à requestOpenedClose/beginClose) |
Auto-cohérence : annonce InputObservationChanged après install, révoquée au teardown (web l.1028-1036, AppKit inputObservationRevoked) ; mappers purs uikitTest ; recognizers additifs retirés au teardown ; cancelsTouchesInView=false tracé.
### Task 1
Task 1: complete (commits 0c258fe4..2cadc337, review clean — 2 mineurs)
- Faits T2/T3 (renames à réconcilier dans les briefs) : `uikitHidKey(page, usage)` (identité numérique — pas de table 0x07, UIKit donne déjà les paires ; Unknown → Unidentified(null)) ; `uikitLogicalKey(characters)` (keyCode retiré, non pertinent) ; `uikitGesturePhase` fusionné dans `uikitTouchPhase` ; + mappers bonus scroll/keyState/modifiers (miroirs web vérifiés) ; SDK : UITouchTypeStylus = alias déprécié de Pencil (raw 2), raw 3 = IndirectPointer.
- NSEvent function keys 0xF700-0xF72D vérifiés vs NSEvent.h (24 named-key rows correctes).
Task 1: minor (ride-along T2/T3): test borne 256/257 du token natif manquant (mutation <=256→<=255 survit) — ajouter 1 ligne.
### Task 2
Task 2: fix round 1/5 ouvert — I1 : tokenFor ne retire JAMAIS les entrées à Ended/Cancelled (table + KDoc + rapport mentent sur la révocation) — fuite monotone de refs UITouch fortes pour la vie de la session ; fix ~2 lignes (remove aux branches Ended/Cancelled) + corriger KDoc/rapport. I2 : respondsToSelector est non-discriminant (toute UIGestureRecognizer répond) — assertion discriminante requise (class_copyMethodList ou contrôle base UIGestureRecognizer qui doit échouer).
Task 2: minor (deferred): M1 double-install orphan (check après addGestureRecognizer) ; M2 compteurs sur-comptent (contacts observés vs publiés) ; M3 inventaire §3 capabilities stale (faire en Task 4/5) ; M4 position = locationInView logique direct (pas de ×scale) non listé dans les déviations.
- Spike prouvé par audit binaire indépendant (sélecteurs exacts dans le framework lié, zéro forme mal segmentée).
### Task 2
Task 2: fix round 1/5 (2 addressed, 0 open; commit 971a6844 — token table pure bornée (read-then-revoke, terminal porte le token), sonde IMP-diff discriminante + contrôle base, tripwire avant mutation, M4 déviations)
Task 2: complete (commits 2cadc337..971a6844, re-revue ADDRESSED ×2, no new breakage)
- Spike recette validée : @ExportObjCClass + @ObjCAction (NSSet, UIEvent?) — sélecteurs dans le binaire, IMP-diff discriminant.
- Real-touch delivery = charter manuel Task 4 (ruled pré-vol) ; compteurs probe embarqués.
- M5 restant : inventaire §3 capabilities à compléter (Task 4/5).
### Task 3
Task 3: complete (commits 971a6844..7806339d, review clean — 2 Important doc-fix ride-alongs Task 5 + 4 mineurs)
- Vérifications indépendantes du reviewer : pinch/rotation = API_UNAVAILABLE(tvos) dans le SDK Apple (UIPinchGestureRecognizer.h:19, UIRotationGestureRecognizer.h:19) — PAS un trou K/N ; le KDoc fautif doit être corrigé (sinon « retry après upgrade toolchain » à tort). GCKeyboard : NSObject (pas GCController), coalescedKeyboard avec note de coalescing verbatim ; handler (GCKeyboardInput*, GCDeviceButtonInput*, GCKeyCode, BOOL).
- Aggregate announcement : 1 UIKitInputAnnouncement par attach threadé aux 3 bindings, publication true à l'instant de chaque install, dernier snapshot = agrégat complet ; teardown ordre identique, dernier snapshot = tout-faux. Unsynchronized mais main-queue sérialisé (sondé).
- Gesture Cancelled/Failed loss : sûr (RuntimeSurfaceInput.kt:621 — Gesture = notification sans état, rien de retenu) ; obligation Task 5 = DESIGN §15.2 documente la perte.
Task 3: minor (deferred): test tautologique recognizedAliasEqualsEndedRawValue (expression comparée à elle-même) ; assert GTE vs message « must have moved » ; provenance 0x07 « vérifié nom pour nom dans le SDK » surexploitée (noms sans valeurs numériques) ; partial-install leak théorique.
### Task 4
Task 4: complete (commits 7806339d..2bbbb266, review clean — 4 mineurs)
- Split final validé : BCK-020 (tvos-rejected + sentinelle, 2 familles) / BCK-021 (iOS-only : 5 corrélés + cancel-before-connect + 2 sentinelles) — le cancel n'a pas de testcase tvOS (provider design-forbidden tvOS), machine-impossible autrement ; ghost KDoc BCK-020 corrigé.
- Obligations Task 5 confirmées ouvertes : (1) DESIGN §15.2 loss-policy gesture Cancelled/Failed (capabilities y forward-référence) ; (2) KDoc fautif UIKitGestureBindings.kt:28-31 (« trou de mapping K/N » → vérité = API_UNAVAILABLE(tvos) SDK).
Task 4: minor (deferred): rapport 16 rows pas 18 / 17 compteurs pas 18 ; « why tvOS » non argumenté (même seam partagé, suite plus courte) ; sentinel Killed écrit inconditionnellement par le générateur (posture phase 2, preuve = citations rapport) ; BCK-022 pointe vers un test nommé GestureAndKeyboard (cross-contract légal) ; INP-004 non enregistré (intentionnel ?).
### Task 5
Task 5: complete (commits 2bbbb266..be309914 — DESIGN §15.2 loss-policy + BACKEND-CAPABILITIES §6.2 livré phase 5 + KDoc corrigés (GestureBindings + tvosMain actual))
- Ride-along signalé (informationnel) : UIKitWindowCommandPort.kt:416 porte une affirmation analogue « API absente du mapping K/N » pour la phase 2 appearance-push — non vérifiée, hors scope, audit ultérieur.
### Task 6
Task 6: complete (be309914 — sweep pure, aucun commit ; revue à venir)
- Sweep : runtime jvm 408/0 (clean+rerun forcé), natives 83+83, appkit 371+2, gate all SUCCESS (BCK-022/023 régénérés à be309914), consumers 47+47/0.
- DESIGN §15.2 signature OK (windowSceneProvider déjà dans le bloc normatif phase 4) ; drift restant : la prose bullet l.1904 cite l'attach sans le param — pré-phase-5, hors périmètre, signalé rapport §4.
### Revue finale
VERDICT: Do not merge as-is — une petite fix round requise (reviewer: « items 3-4 I would not waive »).
- F1 (Important, phase-4 reachability) : GCKeyboard handler process-global vs binding per-session — le 2e attach vole la livraison, le teardown du 1er tue le clavier du survivant, les annonces continuent de dire keyboardInstalled=true. Fix minimum : documenter la limite last-attach-wins (§15.2/§6.2/capabilities) ou arbitrer process-wide.
- F2 (Important) : Siri Remote GCMicroGamepad — JAMAIS implémenté et JAMAIS descoped au ledger (la decision table du plan le disait livré) ; §6.2 confond binding et charter. Ruling requis : descopage explicite en follow-up phase (record ledger) + §6.2 sépare binding/charter.
- Fixes 3-4 requis (reviewer : « would not waive ») : triage 1 = test borne 256/257 token ; triage 6 = test tautologique reconnu==ended remplacé/supprimé.
- Docs 1-ligne : M-A (tvOS clavier GC — déviation vs plan « klib=0 » non flaggée) ; M-B (focus descoping non documenté §15.2/§6.2).
- F-2 CI consumers, F-4 freshness guard, F-7/F-8 : park/suivis.
### Revue finale — fix wave
Task 6bis: fix round (5 findings ADDRESSED; commits be309914..8c114286 : 3d83be90 F1, 88687dfb F2+F5, 8c114286 F3+F4)
Phase 5 : complète (11 commits 5251cb8b..8c114286). Push + PR = contrôleur.
