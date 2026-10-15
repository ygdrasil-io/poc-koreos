# Audit de révision différée — UIKit/tvOS (phase 10)

**Mandat.** La roadmap (`IOS-IMPLEMENTATION-ROADMAP.md`, phase 10) exige : « Revoir chaque
différé : implémentation avec preuve, ou absence structurelle explicitement justifiée.
Aucun contrat `planned` ne peut être présenté comme fonctionnalité livrée. » Ce document
est le verdict, entrée par entrée, sur l'inventaire des différés établi par la recherche de
la phase 10 (21 entrées, §2), sur les quatre contrats planifiés du registre (§1) et sur les
résidus que la revue de la phase a accumulés (§3). Il porte AUSSI l'index des charters
matériels UIKit/tvOS que la §4 « Gate de release » du registre (`capabilities/uikit.md`)
fait parcourir avant toute coupe : chaque capability promise non exerçable sur simulateur
doit porter sa validation physique (TEST-STRATEGY §11.2), ou la promesse doit être retirée
explicitement — jamais l'inverse. La §5 démontre point par point la checklist de fermeture
§8 de la roadmap (l.396-411) — le second membre du critère de sortie de la phase.

**Règle de lecture.** Décisions, pas des promesses : chaque verdict lie sa preuve (contrat,
scénario, test, commit). Trois états existent — **LIVRÉ/PROUVÉ** (le différé est levé, la
preuve est liée), **ABSENCE STRUCTURELLE** (aucun primitif du SDK ne peut satisfaire la
conduite in-process honnêtement ; la preuve automatisée s'arrête où le SDK s'arrête) et
**DÉCISION NON PRISE** (une route de livraison existe dans le SDK, Kadre ne l'a pas prise —
le chemin d'activation est nommé). Un charter manuel est une preuve de matériel, pas un
aveu d'échec : c'est le complément annoncé d'avance par la colonne `état absent` du
registre, jamais un skip déguisé.

État du registre à la date de l'audit (2026-10-15, branche `zcode/sess-56645e3f`, HEAD
`9f17a4c6`) : **107 contrats actifs, 2 planifiés** (WIN-008, APK-016 — §1 : aucun des deux
n'est un contrat iOS/tvOS). L'édition phase 10 du registre des capabilities compte 30
lignes, une par feature et par famille (`capabilities/uikit.md` §2, commit `2ec5e495`).

## 1. Les quatre contrats planifiés — verdicts

La règle de la phase est appliquée littéralement : aucun contrat `planned` n'est présenté
comme livré, et les deux activations sont prouvées par leurs scénarios consumer, pas
assertées.

| contrat | verdict | preuve |
|---|---|---|
| BCK-018 (façade Swift `KadreHost` — attach, observation, terminaison) | **ACTIF** — livré de facto et activé | commit `2018c8d4` (même commit : registre → `active`, 3 scénarios, 2 sentinelles, 16 lignes d'evidence consumer, validateur + gate). Scénarios prouvés contre l'artefact PUBLIÉ (le consumer résout `org.graphiks.kadre:uikit` du repository contractTest, jamais `project(...)`) : `uikit-facade-attach-observes-to-terminated`, `uikit-facade-await-termination-cancellation-keeps-session`, `uikit-facade-race-never-resumes-twice` (`KadreSessionHandleTests`, JUnit mot pour mot, les deux familles). Sentinelle `uikit-facade-deinit-closes-session` portée par une VRAIE mutation (`close()` retiré du `deinit` → run rouge, cascade `AlreadyInUse(host)`) |
| BCK-019 (consumers UIKit et SwiftUI depuis leurs propres artefacts) | **ACTIF** — livré de facto et activé, avec un résidu porté (§3.3) | même commit `2018c8d4`. Scénario `uikit-swiftui-consumer-attaches-once` (`KadreSwiftUIConsumerTests` — attach exactement une fois sur deux ré-évaluations RÉELLES du body) + `ConsumerAttachTests` (factory traversant les modules, les deux familles) + lancement SwiftUI gated devenu étape obligatoire du gate (export JUnit dédié, zéro skip rapporté). Sentinelle `uikit-consumer-no-runtime-duplication` : la preuve anti-duplication est le lancement lui-même (crash/log « Class … is implemented in both ») + garde `doLast` de l'agrégateur (0 occurrence, 4 runs) |
| WIN-008 (bail fullscreen exclusif du runtime, cible `jvm`) | **HORS PÉRIMÈTRE iOS/tvOS** — reste `planned`, autre train | ligne du registre : source `APPKIT-IMPLEMENTATION-ROADMAP.md` phase 9, `requiredTargets = jvm`, aucune target iOS/tvOS. Aucune action de la phase UIKit — noté ici pour clôturer l'inventaire des `planned` |
| APK-016 (fullscreen exclusif AppKit, cible `jvm`) | **HORS PÉRIMÈTRE iOS/tvOS** — reste `planned`, autre train | même lecture : source AppKit, cible `jvm`. La phase UIKit n'a rien à y livrer |

Le validateur tient l'invariant machine : `validateContractRegistry` vert (registre ↔
mapping, y compris les deux nouveaux actifs), et la preuve négative de T2 (un `testName`
muté dans `evidence.tsv` → « mapped testcase is missing ») montre que les scénarios
déclarés sont cherchés DANS les JUnit produits — un contrat activé sans test réel rougit le
gate, il ne se contente pas d'une ligne de registre.

## 2. L'inventaire des différés — 21 entrées, verdict par entrée

L'inventaire de la recherche de la phase 10. La colonne « état » reprend les trois états
ci-dessus ; la preuve lie le contrat, le scénario, le compteur ou le fait de compile qui
porte l'état ACTUEL — un différé n'est réputé « absence structurelle » que si l'état
présent est prouvé, jamais déclaré.

| # | différé | état | preuve de l'état | chemin d'activation |
|---|---|---|---|---|
| 1 | touch réel (livraison d'un `UITouch`) | ABSENCE STRUCTURELLE + charter | pas synthétisable in-process honnêtement (ruling pré-vol) ; les zéros sont PROUVÉS : `touchEventCount == 0`, `touchStimulusCount == 0` (BCK-022, `KadreUikitInputTests`) ; l'ANNONCE du recognizer additif est livrée (BCK-022) | charter manuel sur device — §4 du registre |
| 2 | clavier physique réel (touches de la route `GCKeyboard`) | ABSENCE STRUCTURELLE + charter | `keyboardEventCount == 0`, `keyboardStimulusCount == 0` prouvés (BCK-023) ; le BINDING est livré et s'attache au VRAI objet d'un clavier Simulator appareillé (`keyboardConnectCount`, scénario BCK-023) ; `LogicalKey` reste `Unidentified(null)` (limite GameController documentée, ligne BCK-023) | charter manuel sur device |
| 3 | gestes réels (iOS double-tap/pan/pinch/rotation ; tvOS double-tap/pan) | ABSENCE STRUCTURELLE + charter | `gestureStimulusCount == 0` prouvé (BCK-023) ; l'ENSEMBLE EXACT par famille est livré et épinglé (recognizers comptés sur la vue, `gestureKinds` = l'ensemble annoncé — sentinelle `uikit-input-gesture-set-exact`) | charter manuel sur device |
| 4 | survol réel (iOS, `UIHoverGestureRecognizer`) | ABSENCE STRUCTURELLE + charter | l'install porte le compteur, jamais un événement (`hoverInstallCount`, jamais de fait de survol — ligne pointeur iOS de BCK-022) ; tvOS : absence structurelle du TYPE dans le klib (ligne dédiée) | charter manuel sur device (iPad/pointeur) |
| 5 | clavier logiciel réel + IME réel (+ purposes non exercés) | ABSENCE STRUCTURELLE + charter | la session texte est LIVRÉE de bout en bout (BCK-024 — receiver, ombre, révisions, write-back, `StaleRevision`, mapping Email/Password sur les brutes du champ, les deux familles) ; le vrai clavier et l'IME vif ne sont pas synthétisables in-process ; les purposes au-delà de Text/Email/Password suivent la table exhaustive épinglée en uikitTest mais ne sont pas exercés sur le receiver vivant (limite portée par la ligne BCK-024) | charter manuel (vrai clavier, langues IME) |
| 6 | télécommande tvOS — binding `GCMicroGamepad` non livré | DÉCISION NON PRISE | « suivi enregistré pour la phase suivante » (BACKEND-CAPABILITIES §6.2) ; le type EXISTE dans le klib tvOS — une décision de livraison, jamais une absence structurelle (contra du drop/hover tvOS) ; le FAIT device micro-only est LIVRÉ (BCK-027 : Siri Remote classée micro-only, jamais full gamepad) ; `controllerPausedHandler` : 0 hit dans `uikitMain` | la phase suivante (suivi enregistré) — le mapping des pressions natives vers les types Kadre |
| 7 | observation des transitions `UIFocusEnvironment` | DÉCISION NON PRISE (hors périmètre v1) | zéro référence dans `uikitMain` (le type SDK existe — une décision de périmètre) ; ligne dédiée du registre : aucun binding, aucun état de focus publié, les hooks texte ne font QUE compter (BCK-024) ; la navigation de la surface reste pilotée par les transitions de scène | une décision de roadmap future — PAS un suivi enregistré (choix documenté, contraste volontaire avec l'entrée 6) |
| 8 | rotation réelle (requestGeometryUpdate) | ABSENCE STRUCTURELLE (sur ce runtime) + charter | `requestGeometryUpdate` REFUSÉ 3 voies sur le simulateur 27.0 (capturé : `UISceneErrorDomain 101` ×2, `BSActionErrorDomain « response-not-possible »` — note « rotation programmatique » du registre) ; le kill de `uikit-rotation-metrics-faithful` est porté par la branche DÉGRADÉE (fidélité géométrique publié == vivant, tracé `DEGRADED` à chaque run — BCK-016) ; la branche réelle du test reste codée | un runtime qui accepte `requestGeometryUpdate` (charter / runtime futur) |
| 9 | destruction FrontBoard réelle | ABSENCE STRUCTURELLE (sur simulateur) + charter | `requestSceneSessionDestruction` est un no-op silencieux sur simulateur (prouvé depuis le bundle de test ET l'app — note « environnement simulateur » du registre) ; la preuve de disconnect passe par le vrai nom de notification (`uikit-disconnect-terminates`, ruling contrôleur) ; BCK-013/BCK-014 prouvent la terminaison par session | charter manuel sur device (destruction réelle par le système) |
| 10 | changement d'échelle d'écran + mode-change tvOS | ABSENCE STRUCTURELLE + charter | pas de stimulus programmatique sur simulateur (ligne BCK-015 : `UIScreenModeDidChangeNotification` observé mais sans levier) | charter manuel sur device |
| 11 | AirPlay / écrans externes | ABSENCE STRUCTURELLE + charter | l'inventaire displays est un miroir honnête de `UIScreen` au moment de lecture (BCK-027), sans AirPlay programmable sur Simulator ; `workArea = null` toujours | charter manuel (AirPlay/écran externe physique) |
| 12 | GameController matériel (BT/USB) + reconnect physique | ABSENCE STRUCTURELLE + charter | l'ÉQUIVALENCE MIROIR est prouvée par famille (inventaire publié == énumération `GCController`/`GCKeyboard`, BCK-027) ; Simulator ne peut pas attacher honnêtement ces matériels | charter manuel sur device |
| 13 | rumble/haptics + déconnexion pendant effet | ABSENCE STRUCTURELLE + charter | effets annoncés seulement si GameController les expose ; `Unsupported(GamepadEffect)` sinon ; teardown interrompt les effets vivants (ligne gamepads BCK-027) | charter manuel sur device (manette réelle) |
| 14 | drag cross-app physique | ABSENCE STRUCTURELLE + charter | aucune API de drag système programmable sur Simulator (note `project.yml` du driver) ; la preuve automatisée est DELEGATE-DRIVEN in-process avec le VRAI delegate et de VRAIS `NSItemProvider` (BCK-026, iOS) ; tvOS : absence structurelle du type (`API_UNAVAILABLE`) | charter manuel (drag physique entre applications) |
| 15 | lecture disque security-scoped du contenu File d'un drop | DÉCISION NON PRISE (exit roadmap) | le fait LIVRÉ est la CHAÎNE de l'URL fichier (genre File de la table fermée UTI→mime — ligne BCK-026) ; la lecture du contenu référencé, que l'adapter AppKit opère (`KffiFileDropItemSource`), est la divergence documentée | exit roadmap documentée (miroir AppKit) |
| 16 | capture `HostChoice` — chemin injoignable + frames jamais livrées Simulator | ABSENCE STRUCTURELLE (picker) + ABSENCE MESURÉE (frames) + charter | `hostPicker` structurellement `Unsupported` (les broadcast extensions sont un process séparé dont les frames n'atteignent jamais l'app) — `open(HostChoice)` répond `Unsupported(CaptureOpen)` AVANT tout fait de port (scénario `uikit-capture-hostchoice-unreachable-honest`, sentinelle `uikit-capture-no-picker-promise`, BCK-028) ; le PORT ReplayKit est availability-gated avec `isAvailable` LU VIVANT (mesuré `true` sur les deux simulateurs) ; les frames ReplayKit ne sont JAMAIS livrées sur Simulator (MESURÉ : complétion de démarrage absente, arrêt `-5829` `RPRecordingErrorAttemptToStopNonRecording`) | frames = charter manuel sur device ; un futur parcours conforme picker = ruling + device (chemin enregistré) |
| 17 | ScreenCaptureKit (dépréciation SDK 27 des formes handler) | DÉCISION NON PRISE (migration = roadmap) | la dépréciation est SURFACÉE : `startCaptureWithHandler`/`stopCaptureWithHandler` compilent et restent appelables des 4 cibles (`RPScreenRecorder.h:80,85`) — ligne BCK-028 ; aucun code cassé aujourd'hui | migration `SCStream` = question de roadmap, pas de phase |
| 18 | capture tvOS runtime (faits runtime de la ligne HostChoice sur Apple TV) | ABSENCE STRUCTURELLE + charter | les faits de COMPILE tvOS sont prouvés (`cameraEnabled`/`setCameraEnabled` 0 hit dans le knm tvOS, `RPSystemBroadcastPickerView` absent du klib et interdit partout — spike T1) ; le runtime tvOS reste charter (ligne BCK-028, colonne compile gate) | charter manuel sur Apple TV physique |
| 19 | audit d'en-tête §3 — scaffolding visible volontaire | LIVRÉ (tolérance documentée + MÉCANISÉE) | le scaffolding (sondes, compteurs — phases 0-9) est listé dans `capabilities/uikit.md` §3, masqué NULLEMENT (les gates Swift le pilotent) ; l'audit est re-confirmé sur l'en-tête régénéré (7765 / 491 / 102, les deux familles, `2ec5e495`) et mécanisé par `scripts/audit-uikit-facade-header.sh` (T4, `d7e05bb2`/`9d126e29` — fuites internal et surface promise en échec dur, inventaire scaffolding recompté) | un drift d'inventaire invite à rafraîchir le §3 — la garde est tenue |
| 20 | F-2 (câblage CI du consumer) | RÉSOLU (T4) — n'est plus un différé | deux points d'entrée exactement définis (`capabilities/uikit.md`, note de gate phase 3) : la tâche racine `:kadre:validateSwiftConsumer` (macOS-only, hors `:kadre:check`, échec explicite ailleurs) et le `run_consumer` du gate par famille ; le choix du runner CI (job dédié vs consolidé) suit le nightly (T5, `73dd1dbe`) | — (résolu) |
| 21 | integration:swiftui non matérialisé | RÉSOLU PAR CONCEPTION | le wrapper `UIViewControllerRepresentable` ~10 lignes est possédé par l'app (INTEROP §5) ; la PREUVE est le gate consumer lui-même : `KadreSwiftUIConsumerTests` attache exactement une fois sur des ré-évaluations réelles du body, et le lancement SwiftUI gated (`uikit-swiftui-consumer-gated-launch-scene-hosts-the-wrapper`) tourne pour de vrai à chaque gate (BCK-019, `2018c8d4`) ; un module `integration:swiftui` ne resterait qu'une duplication de la preuve | — (la porte reste celle d'INTEROP §5) |

Trois entrées de l'inventaire initial restent des chantiers hors phase — verdicts séparés
pour ne rien fondre dans les tableaux ci-dessus :

- **`maxPendingWindowRequests` — borne kernel sans test driver.** La borne est du ressort
  kernel et y est PROUVÉE (`UIKitWindowSceneManagerTest` — admission au-delà de la borne ;
  les cycles de rétention de la phase 10 l'exercent aussi à `maxPending = 1`,
  `UIKitRetentionCyclesTest` famille window-requests). Le driver n'a PAS de scénario de
  borne : choix documenté (la ligne BCK-021 le porte — « la borne de pending est du
  ressort kernel (uikitTest), sans test de borne au driver »). Aucun différé de livraison :
  un scénario driver de borne serait une assertion du kernel rejouée, pas une conduite
  UIKit de plus.
- **`kadre-old` suppression = chantier distinct.** Le critère de sortie de la phase le dit
  lui-même (roadmap, critère phase 10 : « La suppression de `kadre-old` reste un chantier
  distinct ») ; la migration est documentée (`kadre/guides/migration-from-kadre-old.md`,
  `API-MIGRATION.md`, `MIGRATION-AUDIT.md`) et n'engage pas la suppression du legacy.
- **Session texte — cycle « niveau SESSION » au host (BCK-024) reste driver-proven.** Voir
  §3.2 (résidu porté par la revue T3).

## 3. Résidus accumulés par la revue de la phase — portés explicitement

Ces quatre résidus viennent des revues T2-T6 ; ils sont repris ici MOT POUR MOT dans leur
portée jugée, pour qu'aucun ne disparaisse au merge.

### 3.1 BCK-019 — résidu « fuite de symbole de façade » (ruling T4, porté par T8)

La classe DÉTECTABLE est mécanisée : les greps d'en-tête de T4 (`org.graphiks.kadre.internal`
= 0, marqueur `Internal` = 0, règle INTEROP §7 `withUIKitView`/`UIKitSurfaceAccessKt` = 0,
les deux familles, `scripts/audit-uikit-facade-header.sh`). Le résidu NARROWED restant :
**les symboles publics de packages internos que le nommage Obj-C ne laisse pas voir** —
l'export Obj-C porte préfixe + nom simple Kotlin, jamais le package ; un type public d'un
package `internal` au nom simple sans « Internal » n'est détectable par aucun grep
d'en-tête. La garde de ce résidu = l'inventaire scaffolding tenu du §3 du registre (tout
nouveau nom de ce genre doit y être ajouté ou c'est un rejet de revue) + la revue. La
conformité de surface au niveau COMPILATION reste ouverte/optionnelle — le véhicule
nommé serait un test de conformance de surface côté Kotlin (diff de table de symboles) ;
non entrepris, documenté. Ce n'est PAS une retraite complète du risque.

### 3.2 Rétention — la session texte reste driver-proven au niveau SESSION (T3)

Le test de rétention (`UIKitRetentionCyclesTest`, commit `ff4b7aa7`, 48 cycles / 10
méthodes / 8 familles, preuve de mutation exacte) mesure la famille texte AU NIVEAU
RECEIVER : le receiver de production (`UIKitTextInputReceiver`, l'objet `@ExportObjCClass`
retenu par le owner) cyclé install → composition marquée vive → retrait, zéro orphelin.
La contrainte est dure : le binaire de test K/N n'est PAS un process applicatif (pas de
key window, pas de focus — `Signal 5` mesuré à la première itération), donc un cycle
open/close de session AVEC focus n'est pas exercable au niveau host. Le cycle
open/close/termine de SESSION reste prouvé par le DRIVER (BCK-024 tourne sur le vrai hôte
XCTest app, les deux familles) — l'audit le note, ne le fond pas : la couverture de
rétention et la couverture de conduite ne se remplacent pas.

### 3.3 Planificateur mono-slot (T2 phase 2) — résolu par conception, pas un différé ouvert

Le drapeau de la phase 2 (« multi-fenêtres devra passer à des schedulers par surface ») a
été levé par la phase 4 : le port UIKit reste MONO-PRIMAIRE par construction (la primaire
est imposée par le host, une fenêtre enfant n'est jamais une `Window` Kadre — c'est une
session enfant COMPLÈTE avec son propre runtime), donc chaque session porte son scheduler
et les 8 points mono-slot de l'exploration phase 4 ne s'appliquent à aucun demandeur
(vérifié contre `UIKitWindowCommandPort`). Le scheduler `UIKitRedrawScheduler` reste
mono-slot PAR PORT et prouvé (`uikit-redraw-single-per-tick`) ; la réouverture du sujet
exigerait qu'un port héberge plusieurs surfaces — aucune route ne le fait aujourd'hui.

### 3.4 `APPLY_LADDER_STATE` — le chemin de commit réel reste hors périmètre fermé

`apply` juge contre un porteur constant et neutre (`APPLY_LADDER_STATE`,
`UIKitWindowCommandPort.kt` : le port ne possède PAS d'état de surface — le runtime le
possède — et la conversion en `SurfaceUpdateCommandOutcome` abandonne le `current` du
verdict). Sur UIKit les QUATRE champs sont fermés (`Unsupported(UpdateSurface)` — BCK-015),
donc aucun champ accepté n'existe qui exigerait un état commité réel : le porteur constant
est honnête, jamais observable. Le chemin de commit réel naîtrait le jour où une phase
future OUVRIT un champ d'apply UIKit (décision de roadmap) — à ce moment-là l'échelle doit
courir contre l'état publié vivant, pas contre la constante. Aucun test ne peut prouver un
chemin qui n'existe pas ; le différé est une note de conception, portée ici.

### 3.5 Note de nommage — sentinelles et scénarios (vérification T8)

Vérification demandée par la revue T6, résultat : **aucun défaut machine-checké**. Les ids
de SCÉNARIO du registre (`uikit-window-request-tvos-rejected`,
`uikit-window-request-concurrent-pair`) sont les vrais noms des KDoc des tests Swift
(`KadreUikitWindowRequestTests.swift:233`, `KadreUikitWindowRequestIosTests.swift:230`) et
sont validés contre les JUnit produits ; les lignes `evidence.tsv` type `sentinel`
citent les bonnes méthodes de test réelles
(`testWindowRequestWithoutProviderIsRejectedUnsupportedWithNoNativeEffect()`,
`testConcurrentWindowRequestsCorrelateEachSceneToItsOwnRequest()`). Les ids de
SENTINELLE (`uikit-window-request-tvos-refusal-faithful`,
`uikit-window-request-correlation-crossed`) sont cohérents registre ↔ evidence (égalité
d'ensembles vérifiée par le validateur, `ContractEvidenceMapping.compareEvidence`) — le
validateur ne compare PAS un id de sentinelle à un nom de scénario. La « staleness » est
donc purement de WORDING (l'id de sentinelle ne partage pas la tige du nom de scénario) ;
renommer des ids de sentinelles toucherait des fichiers machine-checkés (registre +
evidence) pour un gain de lisibilité — non fait. Les sections de lecture du registre nomment
déjà, pour chaque sentinelle, le scénario et le test qui la tue.

## 4. Index des charters manuels — la passe de release

Pour la §4 du registre et TEST-STRATEGY §11.2 : AVANT toute coupe UIKit/tvOS, chaque
capability promise non exerçable sur simulateur porte sa validation physique, ou la promesse
est retirée explicitement. L'index (entrées §2 concernées) :

| charter (matériel requis) | entrée §2 | capability promise portée par |
|---|---|---|
| touches réelles (iPhone/iPad) | 1 | BCK-022 (annonce livrée ; livraison = charter) |
| clavier physique réel | 2 | BCK-023 |
| gestes réels (double-tap, pan, pinch, rotation) | 3 | BCK-023 |
| survol réel (pointeur iPad) | 4 | BCK-022 (ligne iOS) |
| clavier logiciel réel + IME réel (langues, composition vive) | 5 | BCK-024 |
| rotation réelle (runtime acceptant `requestGeometryUpdate`) | 8 | BCK-016 |
| destruction FrontBoard réelle | 9 | BCK-013/BCK-014 (disconnect) |
| changement d'échelle d'écran + mode-change tvOS | 10 | BCK-015 (état absent de la ligne) |
| AirPlay / écran externe / multi-display physique | 11 | BCK-027 (displays) |
| manette BT/USB réelle, reconnect, rumble/haptics, déconnexion pendant effet | 12-13 | BCK-027 (devices/gamepads) |
| drag cross-app physique | 14 | BCK-026 (iOS) |
| lecture security-scoped du File droppé (si promise un jour) | 15 | exit roadmap — RIEN à valider tant que la promesse n'existe pas |
| frames ReplayKit (`HostChoice`) sur device | 16 | BCK-028 (le port est availability-gated ; les frames ne sont promises QUE par ce charter) |
| capture tvOS runtime (Apple TV physique) | 18 | BCK-028 (les faits tvOS de compile sont prouvés ; le runtime est charter) |
| bout réel Siri Remote (`XCUIRemote` = UI-testing) + stimulus de pression mémoire réel | 6, 18 | BCK-027 (fait device micro-only livré ; binding non livré — rien à valider sur le binding tant qu'aucune promesse) |

Chaque charter est nommé, ligne par ligne, dans la colonne `état absent` du registre §2 —
l'index et le registre se citent mutuellement et ne divergent pas : un charter absent des
deux est un trou, une promesse sans charter ni preuve Simulator est un mensonge. La règle
vaut pour toute release tvOS autant qu'iOS (`kadre/guides/tvos.md` la reprend telle quelle).

## 5. La checklist §8 de la roadmap — démonstration point par point

Critère de sortie de la phase 10 : « toutes les obligations du §8 sont démontrées »
(`IOS-IMPLEMENTATION-ROADMAP.md` l.396-411, 14 items). Démonstration :

| §8 (l.) | obligation | démonstration |
|---|---|---|
| 398 | disponibilité tvOS des contrats, noms publics et façades fermée dans les documents normatifs avant activation | registre 30 lignes PAR FAMILLE (`2ec5e495`) : les lignes tvOS dédiées (pointer, gestes, drop, micro-gamepad, focus, mono-scène) portent la vérité par klib ; INTEROP §7 amendé (règle Kotlin-only valable pour la façade ET l'en-tête) ; BACKEND-CAPABILITIES §6.2 phase par phase |
| 399 | les quatre variantes iOS/tvOS de `kadre`, `foundation`, `runtime`, `platform:uikit` compilent, se lient, se consomment depuis les artefacts distribués | BCK-019 ACTIF (`2018c8d4`) : le consumer résout les publications contractTest (jamais `project(...)`), lie les DEUX archives statiques `KadreUikit.xcframework` (4 slices nommées, ligne distribution du registre) ; les quatre targets natifs compilent (`compileKotlin*`, colonne compile gate) |
| 400 | l'attach exact valide scène/fenêtre/vue, refuse le double owner, initialise les cinq managers avant `Running` | BCK-013 (+ BCK-012, BCK-014) : refus `InvalidRequest`, `AlreadyInUse(Host)`, sentinelles `uikit-scene-single-owner`/`uikit-lifecycle-scene-faithful` ; les CINQ managers vivants à l'attach sont assertés par cycle (rétention T3, famille attach/stop ×10) |
| 401 | fenêtre primaire == `primarySurface` ; overlays/vue/layout/rendu host-owned | WIN-009 : même `SurfaceId` via `isEqual` (sentinelle `uikit-primary-metrics-faithful`) ; overlays surviants prouvés (scénario `uikit-window-request-overlays-preserved`, BCK-021) |
| 402 | disconnect termine uniquement la session concernée ; aucune résurrection à la reconnexion | BCK-014 : la destruction de la scène ne termine que SA session (fallback notification réelle, note simulateur) ; ré-attach de la MÊME identité admis 5 fois de suite (rétention T3, famille re-attach ×5) ; destruction FrontBoard réelle = charter (entrée 9) |
| 403 | metrics, redraw, accès UIKit et mutations respectent révisions, main thread et cancellation | BCK-015 : métriques vivantes aux déclencheurs SDK réels (sentinelle `uikit-metrics-stale-generation`), redraw coalescé un-par-tick (`uikit-redraw-single-per-tick`), `apply` fermé et verdict par champ (kernel), bail natif refusé après close (`uikit-lease-closed-after-terminal`) ; `requestRedraw` post-close = `Closed(Surface)` |
| 404 | la façade `KadreHost` et le controller SwiftUI respectent signatures, observation et cancellation promises | BCK-018 ACTIF (`2018c8d4`) : attach→snapshot→terminated, cancellation du waiter n'annule que le waiter, race 50 itérations reprend EXACTEMENT une fois ; `KadreHostViewControllerTests` (8 tests) + l'audit d'en-tête mécanisé (T4) pour la surface promise |
| 405 | demandes de scènes corrélées jusqu'au terminal réel, sans timeout implicite ni session synthétique | BCK-020/BCK-021 : corrélation par identité (jamais par index — sentinelle `uikit-window-request-correlation-crossed`), cancel terminal (`uikit-window-request-cancel-terminal-faithful`), défense anti-résurrection à deux coutures, enfant = session COMPLÈTE jamais une `Window` Kadre ; la borne `maxPendingWindowRequests` est kernel-prouvée (§2, note) |
| 406 | le parcours tvOS mono-scène refuse une nouvelle fenêtre sans effet natif ; ses entrées respectent focus host et actions système | BCK-020 (refus `Rejected(Unsupported(RequestWindow))` avant toute réservation, les DEUX familles, pureté assertée : compteur provider, corrélations, primaire) ; télécommande : entrées 6-7 du §2 — AUCUN binding Kadre (le focus reste au moteur système, le sample de coexistence T7 le prouve par assertion `canBecomeFocused == false`) |
| 407 | input, IME, drop, displays, devices, gamepads, raw input, capture : chacun une décision et une preuve adaptées | BCK-022/BCK-023/BCK-024/BCK-026/BCK-027/BCK-028 — toutes ACTIVES avec sentinelles et evidence par famille ; raw input refusé avec preuve de non-appel (BCK-027) ; §2 de cet audit pour les charters correspondants |
| 408 | aucun contrat actif sans scénario, sentinelle ou rapport corrélé ; registre de capabilities complet | `validateContractRegistry` vert (T2) ; le validateur refuse scénario absent, test manquant, skip, désaccord JSON/JUnit (preuve négative T2 citée §1) ; registre 30 lignes, 30 PASS / 0 FAIL au cross-check T1 ; 16 contrats uikit gate-verts avec evidence JSON+JUnit corrélés |
| 409 | tests de rétention et de callback tardif couvrent chaque source native et chaque ressource closeable | `UIKitRetentionCyclesTest` (T3, `ff4b7aa7`) : 48 cycles / 8 familles (attach/stop, re-attach, drop, capture, displays/devices/gamepads, pression mémoire, texte-receiver, window requests), preuve de mutation réelle ; callbacks tardifs : scénarios driver (`uikit-capture-late-callback-after-close`, livraisons post-teardown no-op comptées zéro BCK-026, résolution tardive de requête annulée no-op BCK-021) ; résidu session-texte porté §3.2 |
| 410 | guides et consumers démontrent hosts UIKit et SwiftUI iOS/tvOS avec factory Kotlin partagée ; slices XCFramework distinctes | guides T6 (`d8bea43d`, 7 guides + index) ; consumers T2/T7 : factory `ConsumerKadre` traversant les modules (`ConsumerAttachTests`), SwiftUI gated lancé pour de vrai (BCK-019), sample de coexistence tvOS (T7, `baf01cbb`) ; slices : `ios-arm64`/`ios-arm64-simulator`/`tvos-arm64`/`tvos-arm64-simulator` nommées dans la ligne distribution du registre |
| 411 | preuves des deux simulateurs obligatoires et séparées ; essais Siri Remote/manette/capture réalisés sur Apple TV physique | le gate exige les DEUX familles à destinations distinctes (`scripts/test-uikit-simulator.sh`, preuves par `<target>` jamais écrasables) ; nightly 4 jambes famille × {courant, plus-ancien-disponible} (T5, `73dd1dbe`) avec l'écart de plancher dit intégralement (§4 du registre) ; les essais physiques = l'index §4 de cet audit (Siri Remote, manette, capture — Apple TV/iPhone physiques, avant coupe) |

**Verdict de la phase :** l'obligation « Revoir chaque différé » est tenue entrée par
entrée (§1-§3) ; aucun contrat `planned` n'est présenté comme livré — les deux `planned`
restants n'appartiennent pas au train iOS/tvOS et le sont restés dans le même commit qui
activait les deux uikit (§1). Le second membre du critère de sortie (§8 démontré) est
porté par la table ci-dessus, chaque ligne liant sa preuve.

## 6. Sources

- Registre et preuves : `kadre/capabilities/uikit.md` (§2-§4), `kadre/contracts/registry/contracts.tsv`,
  `kadre/contracts/driver/uikit/contracts/evidence.tsv`, `scripts/audit-uikit-facade-header.sh`,
  `scripts/test-uikit-simulator.sh`, `.github/workflows/kadre-uikit-nightly.yml`.
- Normatifs : `IOS-IMPLEMENTATION-ROADMAP.md` (phase 10 l.331-343, §8 l.396-411),
  `BACKEND-CAPABILITIES.md` §6.2, `INTEROP-EXPORTS.md` §1/§2/§5/§7/§9, `TEST-STRATEGY.md`
  §11, `DESIGN.md` §15.2.
- Commits de la phase (branche `zcode/sess-56645e3f`) : `2ec5e495` (registre par
  feature/famille), `2018c8d4` (activation BCK-018/019), `d1675b46`, `ff4b7aa7` (rétention,
  race fix `3c2c122b`), `d7e05bb2`/`9d126e29` (consumer gate + audit façade + F-2),
  `73dd1dbe` (nightly), `d8bea43d` (guides), `baf01cbb`/`9f17a4c6` (samples).
