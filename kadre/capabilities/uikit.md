# Registre d'implémentation — adapter UIKit (`org.graphiks.kadre.platform.uikit`, phase 1)

**Mandat.** `BACKEND-CAPABILITIES.md` §8 impose à chaque adapter de produire, avec son
implémentation, un fichier public `capabilities/<adapter>.md` à sept colonnes : `feature`,
`target`, `minimum déclaré`, `compile gate`, `runtime gate`, `état absent` et `tests`. Le
format est celui de `kadre/capabilities/web.md` ; la première édition (phase 0) ne portait
qu'une ligne — la preuve O3 qu'un hôte UIKit réel peut transmettre des objets
`UIWindow`/`UIView` à du code Kotlin/Native et que celui-ci en publie une lecture immédiate,
copiée et honnête. L'édition phase 1 a ajouté la livraison d'`KadreIos.attach` :
une session par `UIWindowScene`, le cycle de vie conduit par les notifications de scène
réelles, le multi-scène iPadOS et l'identité de la fenêtre primaire — chacune portée par sa
ligne, avec ses sentinelles. L'édition phase 3 ajoute la façade Swift `KadreHost`
(contrat §5 d'INTEROP-EXPORTS), le `KadreHostViewController`, la distribution XCFramework
statique et l'audit de l'en-tête framework (§3 ci-dessous) — l'inventaire qui clôt la
divergence F1 de la phase 2 par suppression (`@HiddenFromObjC` sur `withUIKitView`).
L'édition phase 4 ajoute la demande de fenêtre `requestWindow` : le refus sans provider
(`Rejected(Unsupported(RequestWindow))` avant toute réservation — le chemin tvOS et tout
app mono-fenêtre, BCK-020) et la corrélation multi-scènes iPadOS via le provider app-owned
(`OpenedInNewSession` d'une session enfant COMPLÈTE et indépendante, BCK-021) — les
fenêtres enfants ne sont jamais des `Window` Kadre : `Window.apply`/`attention`/`close`
restent hors périmètre. L'édition phase 5 ajoute l'ANNONCE des sources d'entrée : le
recognizer de touch Kadre-owned additif (BCK-022), le fait pointeur par famille (survol
iOS seul), le clavier physique par la route `GCKeyboard.coalescedKeyboard` et l'ensemble
EXACT des gestes par famille (BCK-023) — quatre faits publiés dans UN agrégat
`InputObservationChanged` par attach, révoqué au teardown ; les LIVRAISONS réelles
(touch, touches clavier, gestes, survol) restent des charters manuels — aucune n'est
synthétisable in-process honnêtement (ruling pré-vol), les compteurs du driver le prouvent
(`touchEventCount == 0`, `keyboardEventCount == 0`, `gestureStimulusCount == 0`).
L'édition phase 6 ajoute la SESSION TEXTE (BCK-024) : un receiver natif Kadre
(`UITextField` caché, alpha 0 — jamais `isHidden`, posé dans la hiérarchie du host à
l'open, retiré au close), l'ombre immédiate et ses révisions, l'open exclusif (le refus
nomme `TextInputSession`), le write-back conduit et son refus `StaleRevision` typé, le
`Replace` mappé depuis la PLAGE UIKit honnête (frontière de graphème nommée par UIKit),
l'action de la config, le mapping Email/Password sur les brutes du champ — le tout prouvé
sur les DEUX familles ; le clavier réel et l'IME réel restent des charters manuels.
L'édition phase 7 ajoute la DESTINATION DE DROP native (BCK-026), PAR FAMILLE : iOS porte
l'interaction `UIDropInteraction` Kadre (posée additivement sur la vue du host à l'attach,
retirée au teardown — zéro résiduel), la capacité `handlerInteractions =
Supported({AcceptDrop})` — l'ensemble EXACT, aucun autre kind n'existe sur UIKit et le drag
SORTANT (`UIDragInteraction`) n'est pas annoncé (sortie roadmap, aucun contrat public) — et
le flux drop conduit par le dispatch synchrone du runtime : l'entrée du drag présente
l'offre (`InteractionEvent.DropEntered` dispatché DANS la callback du delegate),
l'`AcceptDrop` du consumer est la décision d'admission, la proposition native vaut `.copy`
tant qu'une offre est tenue (`.cancel` sinon, JAMAIS `.move`), la sortie et la fin de
session terminent l'offre, et le drop performé rend le transfert claimable — un gagnant de
claim unique, lectures bornées, chargement paresseux du `NSItemProvider` sous les budgets de
la policy. La preuve est Simulator-scope, STRUCTURELLE et DELEGATE-DRIVEN in-process (le
VRAI delegate installé par l'attach, piloté hook par hook) : un drag CROSS-APP n'est PAS
synthétisable honnêtement sur Simulator (aucune API de drag système programmable — note
`project.yml` du driver), le drag physique entre applications reste le charter manuel.
tvOS NE PORTE RIEN : `UIDropInteraction*` est `API_UNAVAILABLE(tvos)` (0 symbole dans le
klib), l'actual `tvosMain` n'installe rien et la capacité garde son refus — la compilation
de la source partagée, qui ne nomme jamais le type, est la preuve structurelle.
L'édition phase 8 ajoute BCK-027 : inventaire displays honnête (écran de la scène host en
`HostViewport`, écrans attachés hors host en `Physical`, tvOS mono-`HostViewport`,
`workArea = null` toujours), inventaire devices/gamepads miroir de GameController au moment
de lecture (zéro fabrication ; les scénarios vides épinglent l'ÉQUIVALENCE MIROIR — inventaire
publié == énumération SDK `GCController`/`UIScreen`, le littéral zéro est rouge par design
sur les simulateurs Xcode 27 qui pontent des manettes du Mac), Siri Remote micro-only comme
fact device mais jamais full gamepad, raw input maintenu `Unsupported(RawInputAccess)`, et
pression mémoire `UIApplicationDidReceiveMemoryWarningNotification` → `Moderate` seulement
(jamais `Critical`, jamais depuis visibilité/lifecycle). AirPlay/écrans externes et validation
GameController matérielle restent des charters manuels : Simulator ne peut pas attacher
honnêtement ces matériels.
L'édition phase 9 ajoute la CAPTURE (BCK-028), DÉCIDÉE PAR CIBLE — la décision de phase
tout entière tient dans la correspondance honnête entre les trois `CaptureTarget` et ce
que les SDK iOS/tvOS offrent réellement. La cible `Surface` est LIVRÉE et PROUVÉE DE BOUT
EN BOUT SUR SIMULATOR avec de VRAIES frames : une pompe de snapshots de la vue du host
(`drawViewHierarchyInRect`, repli `layer.render(in:)`, readback bitmap info PAR frame),
streamée par un ticker injecté sous le budget par frame — et le STIMULUS DE CONTENU fait
partie de la preuve : une vue VIDE produit des rasters MENTEURS (bitmapInfo `0x1` — une
étiquette rgba8 dans la table — portée par `bytesPerRow = 2·w`) que le kernel d'honnêteté
refuse du terminal `Failed(..., "frame-format-out-of-table")` ; une vue avec contenu rendu
livre le raster cohérent rgba8 (les deux familles). La cible `HostChoice` est le chemin
ReplayKit in-app, availability-gated : `RPScreenRecorder.isAvailable` LU VIVANT (jamais
« Simulator ⇒ indisponible » codé en dur — mesuré `true` sur les deux simulateurs 27.0 au
moment du gate), `requestPermission` ne prompte JAMAIS (aucun dialogue de consentement
n'existe pour la capture in-app — la permission écran est dérivée de la disponibilité),
micro/caméra jamais activés — MAIS CE CHEMIN EST ACTUELLEMENT INJOIGNABLE PAR TOUT TARGET
PUBLIC : `hostPicker` est structurellement `Unsupported` (les broadcast extensions sont un
process séparé dont les frames n'atteignent jamais l'app — hors du contrat fermé) et
l'admission runtime d'un open `HostChoice` exige un lifecycle de picker qu'iOS/tvOS
n'offrent pas ; le refus `Unsupported(CaptureOpen)` avant tout fait de port est prouvé
(scenario dédié), la roadmap l.323 dit « conserver le chemin non supporté », et les frames
ReplayKit ne sont DE PLUS JAMAIS livrées sur Simulator (MESURÉ : la complétion du démarrage
ne vient pas, l'arrêt répond `-5829` AttemptToStopNonRecording) — la preuve de frames est
le charter manuel sur device ; la dépréciation SDK 27 de `startCaptureWithHandler`/
`stopCaptureWithHandler` au profit de ScreenCaptureKit est SURFACÉE (compile et appelable
des 4 cibles ; la migration SCStream est une question de roadmap). Les cibles `Source`/
fenêtre et l'énumération sont les absences EXACTES : aucune primitive in-process
d'énumération des surfaces capturables n'existe — `window` `Unsupported(CaptureOpen)`,
`sourceEnumeration` `Unsupported(CaptureRefreshSources)`, inventaire `Unavailable(même
échec)` — JAMAIS `HostPickerOnly`. La phase a aussi produit UN fix runtime SANCTIONNÉ :
`RuntimeCaptureSession.offerFrameLocked` incrémente SON compteur AVANT le send (un
collector unconfined — le pont Swift K/N — reprend INLINE dans le send ; l'ancien ordre
sous-fluait l'invariant `capture queued-byte accounting` de façon process-fatale) ; le
filet deterministic commonTest porte la régression et le prédicat de budget est revenu à
sa baseline algébrique (`queued + leased > maxBufferedBytesPerSession`, pins de borne) —
le fix concerne tout collector unconfined FUTUR, UIKit n'en est que la manifestation
actuelle.

**Portée de lecture.** Deux targets Kotlin partagent la sonde (`KadreUikitProbe`,
`uikitMain`) : `iosSimulatorArm64` et `tvosSimulatorArm64`. La preuve O3 court dans des
applications hôte Xcode réelles (`kadre/contracts/driver/uikit/KadreUikitDriver.xcodeproj`,
schemes `KadreUikitDriverIos` et `KadreUikitDriverTvOs`) qui lient le framework
`KadreUikit` produit par `:kadre:platform:uikit` et exécutent `KadreUikitDriverTests`
(phase 0) et `KadreUikitAttachTests` (phase 1, extension iOS-only
`tests/Ios/KadreUikitAttachIosTests.swift` compilée DANS la même classe XCTest) sur les
simulateurs. La matrice toolchain **relevée** de cette édition — ce sont
les versions qui ont produit les documents de preuve `BCK-012` à `WIN-009`, pas des minima
au-delà d'eux-mêmes :

- Kotlin **2.4.20** (Kotlin/Native), Gradle **9.8.0** ;
- Xcode **27.0 (27A266a)**, `xcodebuild` + `xcresulttool` du même Xcode ;
- simulateurs réellement exercés : **iPhone 17 / iOS 27.0 (build 24A434)** (phase 0),
  **iPad Pro 11-inch (M5) / iOS 27.0** (phase 1 — le multi-scène est refusé par l'idiome
  téléphone) et **Apple TV 4K (3rd generation) / tvOS 27.0 (build 24J360)** (relevés
  `devices` des bundles `build/xcresult/{ios,tvos}.xcresult`) ;
- planchers **déclarés** de l'adapter : **iOS 16.0 / tvOS 16.0** — déclarés, pas exercés :
  aucune passe de preuve ne court sous 27.0 à ce stade (voir la colonne `minimum déclaré`).

**Règle d'identité du pont Obj-C (relevé Task 5, 2026-10-10).** `===` ne traverse PAS le
pont Kotlin/Obj-C : pour la même instance `UIWindow` reçue du hôte, le pont produit deux
wrappers Kotlin distincts (`===` faux) alors que `hashCode` coïncide. Toute comparaison
d'identité Objective-C côté hôte passe donc par `NSObject.isEqual` — c'est la lecture
qu'implémente `KadreUikitProbe.observe` (`view.window.isEqual(window)`), et c'est la
règle que la sentinelle de `BCK-012` protège (`uikit-driver-window-membership-sentinel`).

**Note environnement simulateur (relevé Task 5 fix round, 2026-10-11).** Sous XCTest,
`requestSceneSessionDestruction` est un no-op silencieux sur simulateur (errorHandler jamais
appelé, session jamais invalidée — prouvé depuis le bundle de test ET le code de l'app ;
`requestSceneSessionActivation` n'est honoré par FrontBoard que depuis le lancement de l'app) :
la preuve de disconnect du driver passe par le post du vrai nom de notification
(`uikit-disconnect-terminates`, fallback ruling contrôleur). La preuve de DESTRUCTION réelle
par FrontBoard est un charter manuel sur matériel — matériel de la phase 10.

**Note rotation programmatique (relevé Task 5, 2026-10-11 — portée par BCK-016).**
`UIScene.requestGeometryUpdate(.iOS(interfaceOrientations:))` est REFUSÉ par ce runtime
simulateur quel que soit le levier — TROIS refus capturés, un par configuration :
(1) windowing libre par défaut → `UISceneErrorDomain 101` « The current windowing mode does
not allow for programmatic changes to interface orientation » ; (2) avec la clé TN3192
`UIPreferredDefaultWindowingStyle=UIWindowSceneWindowingStyleFullscreen` dans l'Info.plist
construit (vérifié présent) → même 101 ; (3) avec `UIRequiresFullScreen=true` →
`BSActionErrorDomain « response-not-possible »`. Le stimulus retenu est le resize à
changement d'aspect (240×320) — DÉGRADÉ, tracé `KADRE-DRIVER … DEGRADED` à chaque run
(NSLog + DriverTrace), jamais présenté comme une rotation réelle. La branche de rotation
RÉELLE reste codée dans le test (fidélité exacte : largeur tournée == hauteur portrait,
échange vérifié contre le portrait de référence) et s'exécutera sur un runtime qui accepte
`requestGeometryUpdate` — charter manuel / runtime futur. Le kill de la sentinelle
`uikit-rotation-metrics-faithful` est PORTÉ PAR LA BRANCHE DÉGRADÉE (fidélité géométrique :
publié == vivant après le changement de géométrie), jamais par une rotation réelle.

## 1. Lecture des colonnes

- **feature** — la conduite livrée, nommée comme le fait le registre Web : le membre
  public ou, à ce stade, la sonde de conduite du driver.
- **target** — les deux targets simulateurs du framework (`iosSimulatorArm64`,
  `tvosSimulatorArm64`). Aucun target device n'est déclaré : la phase 0 ne prouve que la
  conduite sur simulateur, un target device exigerait une signature que cette phase ne
  possède pas.
- **minimum déclaré** — le plancher que le code déclare (iOS 16.0 / tvOS 16.0,
  `kadre/platform/uikit/build.gradle.kts`), la toolchain ci-dessus étant ce qui a
  réellement exercé la preuve.
- **compile gate** — le symbole dont la compilation dépend : ici aucun symbole
  conditionnel ; le framework compile pour les quatre targets natifs et la sonde ne lit
  que des membres toujours présents (`UIView.window`, `UIWindow.windowScene`,
  `UIView.bounds`, `UITraitCollection.displayScale`).
- **runtime gate** — ce dont dépend la *publication* : un run xcodebuild sur le
  simulateur de chaque famille (les preuves `BCK-012` à `WIN-009` puis `BCK-015`/`BCK-016`,
  `BCK-020`/`BCK-021`, `BCK-022`/`BCK-023`, `BCK-024`, `BCK-026` et
  `BCK-027`/`BCK-028`),
  jamais une permission hôte.
- **état absent** — ce qui n'est pas livré à ce stade, dit intégralement : depuis la
  phase 1, l'attach (`KadreIos.attach`), le cycle de vie par scène, le multi-scène iPadOS
  et l'identité de la fenêtre primaire sont livrés ; depuis la phase 2, les observations
  dynamiques de surface (métriques, thème, visibilité — re-lectures sur déclencheurs SDK
  réels), le redraw coalescé, l'`apply` fermé, le bail d'accès natif `withUIKitView` et la
  rotation iPadOS (stimulus dégradé, cf. note ci-dessus) sont livrés ; depuis la phase 3,
  la façade Swift `KadreHost` (contrat §5), le `KadreHostViewController`, la distribution
  XCFramework statique et l'audit de l'en-tête framework sont livrés — et l'appel Swift du
  bail RESTE ABSENT par décision : `withUIKitView` est Kotlin-only strict, masqué des
  exports Obj-C/Swift (`@HiddenFromObjC`, audit §3) ; depuis la phase 4, la demande de
  fenêtre `requestWindow` est livrée — refus pur sans provider (BCK-020) et corrélation
  multi-scènes via provider app-owned (BCK-021) — mais une fenêtre enfant n'est JAMAIS une
  `Window` Kadre : `Window.apply`/`attention`/`close` et toute opération sur l'enfant
  restent hors périmètre (l'enfant est sa propre session UIKit complète) ; depuis la
  phase 5, les sources d'entrée sont ANNONCÉES — le recognizer de touch additif, le fait
  pointeur par famille (survol iOS seul), le clavier physique (`GCKeyboard.coalescedKeyboard`)
  et l'ensemble exact des gestes par famille, dans un agrégat `InputObservationChanged`
  publié après l'installation et révoqué au teardown (BCK-022/BCK-023) — mais les
  LIVRAISONS réelles restent des charters manuels : un touch `UITouch`, une touche
  `GCKeyCode`, un geste ou un survol ne sont pas synthétisables in-process honnêtement
  (ruling pré-vol ; les compteurs driver prouvent les zéros — `touchEventCount == 0`,
  `keyboardEventCount == 0`, `gestureStimulusCount == 0`) ; depuis la phase 6, la session
  texte est LIVRÉE (BCK-024 — receiver natif caché, ombre et révisions, open exclusif,
  write-back conduit, action et mapping de config) avec ses limites portées par sa ligne :
  le clavier logiciel réel et l'IME réel restent des charters manuels ; depuis la phase 7,
  la destination de drop est LIVRÉE sur iOS (BCK-026 — sa ligne dédiée) et ABSENTE sur tvOS
  (0 symbole `UIDropInteraction*` dans le klib, même ligne) ; la capture porte désormais
  ses états PAR CIBLE en BCK-028 — `Surface` LIVRÉE et prouvée Simulator, `HostChoice`
  port ReplayKit availability-gated mais INJOIGNABLE via tout target public
  (`hostPicker` structurellement absent), `Source`/fenêtre/picker/énumération absences
  exactes (trois lignes dédiées) ; displays, devices/gamepads, raw input et pression mémoire portent désormais
  leurs états honnêtes en BCK-027.
  Le CHANGEMENT D'ÉCHELLE d'écran (`displayScale`) et le
  mode-change tvOS (`UIScreenModeDidChangeNotification` — observé mais sans stimulus
  programmatique sur simulateur) n'ont pas de preuve sur simulateur : charter manuel
  (matériel de la phase 10). Aucun module applicatif Kadre ne référence
  `:kadre:platform:uikit` en dehors de la preuve.
- **tests** — la classe Xcode qui porte la ligne (`KadreUikitDriverTests` phase 0,
  `KadreUikitAttachTests` phase 1, `KadreUikitSurfaceTests` phase 2 — extension iOS-only
  `tests/Ios/KadreUikitSurfaceIosTests.swift` compilée DANS la même classe XCTest pour la
  rotation, `KadreUikitWindowRequestTests` phase 4 — extension iOS-only
  `tests/Ios/KadreUikitWindowRequestIosTests.swift` compilée DANS la même classe XCTest
  pour les scénarios multi-scènes, `KadreUikitInputTests` phase 5 — classe partagée, la
  vérité par famille est assertée DANS les tests, `KadreUikitTextInputTests` phase 6 —
  classe partagée, même posture, `KadreUikitDropIosTests` phase 7 — iOS SEULE (garde
  `!os(tvOS)` : le protocole `UIDropSession` n'existe pas dans le klib tvOS, le fichier
  doit en rester invisible), `KadreUikitDropAbsenceTests` phase 7 — classe partagée, la
  vérité par famille (absence tvOS + contrôle iOS qui prouve la sonde discriminante)
  assertée DANS le même test, `KadreUikitCaptureTests` phase 9 — classe partagée (les faits
  capture sont identiques iOS/tvOS, zéro os-guard ; le readback renderer VIVANT et la table
  de décodage bitmapInfo y vivent aux côtés des huit scénarios BCK-028)), exécutée par
  `./gradlew :kadre:contracts:driver:uikit:simulatorTests` et consommée par le validateur
  via l'export JUnit (`tools/xctest_to_junit.py`).

## 2. Registre

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| observation de conduite (sonde driver) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-012 (2 simulateurs) | tout le reste historique : input livré/limité par BCK-022/BCK-023, IME réel en charter manuel (session texte BCK-024), drop iOS livré/tvOS absent par BCK-026, displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitDriverTests` |
| `KadreIos.attach` — session par scène et cycle de vie | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-013 (2 simulateurs) | input livré/limité par BCK-022/BCK-023 ; IME réel en charter manuel (session texte BCK-024) ; drop iOS livré/tvOS absent par BCK-026 ; displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitAttachTests` |
| multi-scène iPadOS (une session par `UIWindowScene` supplémentaire) | iosSimulatorArm64 | iOS 16.0 (iPad) | `:kadre:platform:uikit:compileKotlin*` | BCK-014 (iPad Pro 11-inch (M5)) | tvOS mono-scène par design (DESIGN.md §15.2) ; input livré/limité par BCK-022/BCK-023 ; IME réel en charter manuel (session texte BCK-024) ; drop iOS livré/tvOS absent par BCK-026 ; displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitAttachTests` (extension `tests/Ios`) |
| fenêtre primaire et identité de surface | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | WIN-009 (2 simulateurs) | toute autre fenêtre/surface : non livré (le host impose la primaire) ; input livré/limité par BCK-022/BCK-023 ; IME réel en charter manuel (session texte BCK-024) ; drop iOS livré/tvOS absent par BCK-026 ; displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitAttachTests` |
| observations dynamiques de surface, redraw coalescé et `apply` fermé | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-015 (2 simulateurs) | occlusion (restera `Visible`), changement d'échelle d'écran et mode-change tvOS : pas de stimulus programmatique sur simulateur (charter manuel) ; IME réel (charter manuel — la session texte est livrée : ligne BCK-024), drop (iOS livré : ligne BCK-026 — tvOS absent, 0 symbole klib), displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitSurfaceTests` |
| bail d'accès natif `withUIKitView` (platformAccess) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-015 (2 simulateurs) | la UIView n'est valide qu'au retour du block (jamais retenue) ; après close : `Unsupported(PlatformSurfaceAccess)` déterministe ; IME réel (charter manuel — la session texte est livrée : ligne BCK-024), drop (iOS livré : ligne BCK-026 — tvOS absent, 0 symbole klib), displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitSurfaceTests` |
| rotation iPadOS de la surface | iosSimulatorArm64 | iOS 16.0 (iPad) | `:kadre:platform:uikit:compileKotlin*` | BCK-016 (iPad Pro 11-inch (M5)) | rotation RÉELLE non exercée : `requestGeometryUpdate` refusé 3 voies sur ce runtime — kill porté par la branche DÉGRADÉE (resize à changement d'aspect, fidélité géométrique publié == vivant) ; rotation réelle = runtime qui accepte `requestGeometryUpdate` (charter manuel / runtime futur) ; tvOS : hors sujet (pas de rotation) ; IME réel (charter manuel — la session texte est livrée : ligne BCK-024), drop (iOS livré : ligne BCK-026 — tvOS absent, 0 symbole klib), displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitSurfaceTests` (extension `tests/Ios`) |
| façade Swift `KadreHost` (contrat INTEROP §5 : `KadreIosHost.attach`, `KadreSessionHandle`, value model fermé) + inventaire d'audit de l'en-tête (§3) | iosSimulatorArm64, tvosSimulatorArm64 (sources Swift `platform/uikit/swift/Sources/KadreHost`, compilées dans l'app consumer) | iOS 16.0 / tvOS 16.0 | compile du consumer `KadreSwiftConsumer.xcodeproj` (les symboles Kotlin traversent par `swift_name`, xcframework lié sans embed) | consumer gate (`iosSimulatorTests`/`tvosSimulatorTests` — la factory du module Kotlin applicatif traverse jusqu'à l'attach ; pré-absence de `Flow`/`Continuation`/`Throwable` dans la façade épinglée) | `withUIKitView` depuis Swift : NON promis — Kotlin-only strict (`@HiddenFromObjC`, audit phase 3, §3) ; module `integration:swiftui` : non matérialisé (le wrapper `UIViewControllerRepresentable` ~10 lignes est possédé par l'app, INTEROP §5) ; managers/flows/frames : non promis | suites consumer-app (`KadreHostMappingTests`, `KadreSessionHandleTests`, `ConsumerAttachTests`) |
| `KadreHostViewController` (attach exactement une fois quand sa view appartient à une `UIWindow`/`UIWindowScene` ; la view passée EST `self.view`) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | compile du consumer `KadreSwiftConsumer.xcodeproj` | consumer gate (`iosSimulatorTests`/`tvosSimulatorTests`) | zéro layout Kadre (promesse §5) ; re-attach après `close` hors promesse (budget d'attach unique) ; déconnexion observer seulement au `deinit` (close la laisse — bénin, weak self) | suite consumer-app (`KadreHostViewControllerTests`) |
| distribution XCFramework STATIQUE `KadreUikit.xcframework` (4 slices : `ios-arm64`, `ios-arm64-simulator`, `tvos-arm64`, `tvos-arm64-simulator` — `AvailableLibraries` du Info.plist) | iosArm64, iosSimulatorArm64, tvosArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `assembleKadreUikit{Debug,Release}XCFramework` (l'en-tête audité de §3 est celui des slices) | l'app consumer SE LANCE avec les DEUX archives statiques liées sans embed — toute duplication Obj-C au lancement (« Class … is implemented in both ») fait échouer le run | pas de publication binaire signée (artefact local de preuve, `build/` non commité) ; slices device (arm64) non exercées — simulateurs seuls ; l'en-tête ne porte aucun symbole surface-access (§3) | consumer gate (`iosSimulatorTests`/`tvosSimulatorTests`, garde `doLast` anti-duplication) |
| `requestWindow` sans provider — refus `Rejected(Unsupported(RequestWindow))` avant toute réservation, aucun effet natif (chemin tvOS et tout app mono-fenêtre) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-020 (2 simulateurs) | le chemin provider (corrélation de scène, `OpenedInNewSession`) : hors sujet sans provider (BCK-021, iOS seul) ; `Window.apply`/`attention`/`close` : hors périmètre (une fenêtre enfant n'est jamais une `Window` Kadre) ; IME réel (charter manuel — la session texte est livrée : ligne BCK-024), drop (iOS livré : ligne BCK-026 — tvOS absent, 0 symbole klib), displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitWindowRequestTests` |
| `requestWindow` corrélé multi-scènes — provider app-owned, `OpenedInNewSession` d'une session enfant COMPLÈTE et indépendante (sa propre scène/surface/lifecycle ; le close du requérant détache l'enfant du destin du requérant — l'enfant survit) | iosSimulatorArm64 | iOS 16.0 (iPad) | `:kadre:platform:uikit:compileKotlin*` | BCK-021 (iPad Pro 11-inch (M5)) | tvOS : mono-scène par design (DESIGN.md §15.2) — le refus BCK-020 y porte le contrat ; `Window.apply`/`attention`/`close` sur une fenêtre enfant : hors périmètre (l'enfant n'est jamais une `Window` Kadre) ; IME réel (charter manuel — la session texte est livrée : ligne BCK-024), drop (iOS livré : ligne BCK-026 — tvOS absent, 0 symbole klib), displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitWindowRequestTests` (extension `tests/Ios`) |
| sources d'entrée touch et pointeur — recognizer de touch Kadre-owned additif (`cancelsTouchesInView == false`), fait pointeur par famille (survol iOS seul), annonce agrégée publiée après l'install et révoquée au teardown | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-022 (2 simulateurs) | la LIVRAISON réelle d'un touch (`UITouch`) : charter manuel (pas synthétisable in-process — `touchEventCount == 0` prouvé) ; le survol est iOS seul (`API_UNAVAILABLE(tvos)`, klib sans la classe — tvOS reste `pointerInstalled == false` sans faux écrit) ; IME réel (charter manuel — la session texte est livrée : ligne BCK-024), drop (iOS livré : ligne BCK-026 — tvOS absent, 0 symbole klib), displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitInputTests` |
| sources d'entrée clavier et gestes — binding `GCKeyboard.coalescedKeyboard` (notifications de connexion + scan d'install, handler `keyChangedHandler`), ensemble EXACT des gestes par famille (iOS tap/pan/pinch/rotation, tvOS tap/pan), annonce agrégée publiée après l'install et révoquée au teardown | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-023 (2 simulateurs) | la LIVRAISON réelle de touches : charter manuel (`keyboardEventCount == 0`, `gestureStimulusCount == 0` prouvés) ; `LogicalKey` toujours `Unidentified(null)` et modifiers toujours vides (la route GC ne porte ni `characters` ni FlagsChanged — le texte est la route UIKeyInput, livrée phase 6 : ligne BCK-024) ; le clavier intégré iPad (non-GameController) : couverture non prouvée (limite documentée) ; handler `keyChangedHandler` process-global : limite mono-consumateur documentée (une seconde session attachée prend la livraison des touches, le teardown de la première la coupe pour la suivante — les annonces peuvent alors sur-déclarer `keyboardInstalled` ; arbitrage process-wide = follow-up) ; pinch/rotation : iOS seuls (absents du klib tvOS) ; `Cancelled`/`Failed` de geste : PERDUS par politique explicite (aucun état de geste retenu — DESIGN §15.2 le documente) ; la télécommande (bout réel) : charter manuel (XCUIRemote = UI-testing) ; Siri Remote/micro-gamepad : honnêteté BCK-027 — fact device via `InputDevicePort` quand le SDK l'énumère, jamais full gamepad sans `extendedGamepad` ; IME réel (charter manuel — la session texte est livrée : ligne BCK-024), drop (iOS livré : ligne BCK-026 — tvOS absent, 0 symbole klib), displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitInputTests` |
| session texte — receiver natif Kadre caché (`UITextField` posé alpha 0 — jamais `isHidden` —, retiré au close), ombre immédiate et révisions, open exclusif (`AlreadyInUse(TextInputSession)`), `Replace` mappé depuis la PLAGE UIKit honnête (frontière de graphème nommée par UIKit), write-back conduit (le document du runtime écrit dans le champ sous la garde d'écho) et refus `StaleRevision` typé, action de la config (`Action(config.action)`), mapping Email/Password vérifié sur les brutes du champ | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-024 (2 simulateurs) | le COUPLAGE focus→Suspend n'est pas livré côté UIKit : le snapshot ne publie jamais `Focused` (UIKit n'a pas de focus surface) et les hooks de focus du receiver ne font QUE compter — la session reste `Active` à la perte et au retour du first responder ; les jambes `Suspended`/resume = charter (vrai clavier détaché, ou un binding UIKit futur qui publierait le focus) ; les édits programmatiques purs (`insertText:`/`deleteBackward:` SANS hook utilisateur) sont INOBSERVÉS par délivrance (verdict `Nothing` du routeur : un fait non attribué ne publie rien) — la route champ→Kotlin est routée pour la saisie utilisateur seule ; le clavier logiciel réel et l'IME réel = charters manuels (pas synthétisables in-process honnêtement) ; le refus du second open observé est l'admission du RUNTIME — le CAS `liveOwner` du port reste la défense en profondeur, non joignable depuis la route publique du driver ; le commit IME lui-même ne publie rien (limite v1 du port, l'ombre est resynchronisée au commit) ; les purposes au-delà de Text/Email/Password (Name, Url, Telephone, Number, Decimal) suivent la même table de mapping (exhaustive, épinglée en uikitTest) mais ne sont pas exercés sur le receiver vivant : non couverts côté driver ; le champ marqué n'est pas lisible APRÈS le close (le retrait EST la preuve) — l'absence d'orphelin est prouvée CÔTÉ RUNTIME (session `Closed`, zéro fait, ré-ouverture admise sans marqué reporté) ; drop (iOS livré : ligne BCK-026 — tvOS absent, 0 symbole klib), displays/devices/gamepads/raw input/mémoire décidés par BCK-027 ; capture : Surface LIVRÉE, HostChoice ReplayKit availability-gated mais INJOIGNABLE via tout target public (hostPicker structurellement absent), Source/fenêtre/picker/énumération absences exactes — BCK-028, lignes dédiées | `KadreUikitTextInputTests` |
| destination de drop native (iOS) — interaction `UIDropInteraction` Kadre posée ADDITIVEMENT sur la vue du host à l'attach (retirée au teardown, zéro résiduel), capacité `handlerInteractions = Supported({AcceptDrop}, Available)` — l'ensemble EXACT (aucun primitif fullscreen/pointer-lock n'existe sur UIKit ; le drag SORTANT `UIDragInteraction` n'est JAMAIS annoncé : sortie roadmap, aucun contrat public) ; flux drop : `canHandle` = sonde BON MARCHÉ aux types enregistrés (au moins un provider qu'une table fermée sait décrire), l'entrée présente l'offre par le dispatch synchrone du runtime (`InteractionEvent.DropEntered` dispatché DANS la callback du delegate, position logique de la vue — l'espace de la vue EST l'espace logique), l'`AcceptDrop` du consumer commis par `context.request` (le chemin `Now` du port), l'update propose `.copy` SI ET SEULEMENT SI une offre est tenue (`.cancel` sinon — JAMAIS `.move`), la sortie ET `sessionDidEnd` (le « you may now forget it » du header — drag annulé en plein survol) terminent l'offre `LeftSurface`, le perform rend le transfert claimable (AU PLUS UNE offre active : une entrée qui remplace termine la précédente `LeftSurface`) ; transfert : chargement PARESSEUX du `NSItemProvider` (`loadItemForTypeIdentifier` sous `suspendCancellableCoroutine`, completion hors thread principal, l'item ENTIER mis en cache à la première lecture — relecture `Replayable` sans recharger), claim à gagnant UNIQUE (second claim `AlreadyInUse(DropTransfer)`), lectures bornées (`ResourceLimitExceeded(DropItem, maxBytes)`), budgets de la policy (`maxConcurrentDropTransfers` = 4, `maxDropChunkBytes` = 262 144, `dropTransferClaimTimeout` = 30 s, `maxRetainedPayloadBytesPerSession` = 32 Mio, défauts `KadrePolicies.Default`), close du transfert exactement une fois ; table fermée UTI→(mime, genre) : `public.utf8-plain-text`→`text/plain` (Text), `public.url`→`text/uri-list` (Uri), `public.file-url`→`application/octet-stream` (File) — précédence File > Uri > Text, une UTI hors table est PERDUE jamais approximée ; le genre Uri lit la chaîne ABSOLUE de l'`NSURL` livrée (le contenu que `text/uri-list` annonce) ; le genre File lit la CHAÎNE de l'URL fichier — le contenu disque security-scoped reste l'exit roadmap (divergence AppKit documentée, miroir `KffiFileDropItemSource`) ; `sizeBytes` toujours `null` avant livraison (aucun entier de provider lisible avant le chargement) ; close EN PLEIN offre → `OwnerClosed`, livraisons TARDIVES post-teardown = no-ops comptés zéro (garde stale du delegate) | iosSimulatorArm64 | iOS 16.0 | `:kadre:platform:uikit:compileKotlinIosSimulatorArm64` (la couture `UIDropInteraction`/`UIDropSessionProtocol` n'existe que dans le klib iOS) | BCK-026 (iPad Pro 11-inch (M5) / iOS 27.0) | la preuve est Simulator-scope, STRUCTURELLE et DELEGATE-DRIVEN : le VRAI delegate installé par l'attach (fetché de la vue, `view.interactions`) est appelé hook par hook dans l'ordre UIKit (`canHandle` → enter → update → exit/perform) avec une fausse de session conforme au protocole `UIDropSession` autour de VRAIS `NSItemProvider` — le canal delegate RÉEL d'UIKit, jamais un événement fabriqué ; un drag CROSS-APP n'est PAS synthétisable in-process honnêtement (aucune API de drag système programmable sur Simulator — note `project.yml` du driver) : le drag physique entre applications = charter manuel ; la lecture du contenu disque d'un File = exit roadmap (la chaîne de l'URL est le fait livré) | `KadreUikitDropIosTests` |
| destination de drop native (tvOS) — ABSENCE structurelle : `UIDropInteraction`, `UIDropInteractionDelegateProtocol`, `UIDropSessionProtocol` et `UIDragDropSessionProtocol` sont `API_UNAVAILABLE(tvos)` (UIDragSession.h:19 — 0 symbole dans le klib tvOS 2.4.20, vérifié au compile ET dans le linkdata), l'actual `tvosMain` de la couture répond `null`/ensemble vide (le précédent `UIKitGestureRecognizersTvos` — jamais un binding de substitution qui ne dropperait rien) | tvosSimulatorArm64 | tvOS 16.0 | la COMPILATION du klib tvOS est la preuve structurelle (`:kadre:platform:uikit:compileKotlinTvosSimulatorArm64` — la source partagée ne nomme jamais le type ; les fichiers driver drop tvOS sont gardés `!os(tvOS)`) | BCK-026 (Apple TV 4K (3rd generation) / tvOS 27.0) | zéro interaction drop sur la vue du host avant ET après l'attach ET après le close (sonde STRUCTURALE par NOM de classe — jamais une référence au type, interdit à la compilation tvOS), compteurs `dropInstallCount`/`dropTeardownCount`/`dropEventCount` figés (aucun binding à livrer), capacité `handlerInteractions` REFUSÉE sur le snapshot de capabilities exporté (`Unsupported(InstallInteractionHandler)` — l'ensemble des kinds est vide), zéro livraison après pompe (aucun delegate à livrer) ; le CONTRÔLE iOS du MÊME test prouve la sonde discriminante (elle VOIT l'interaction installée, les compteurs bouger et la capacité `Supported` porter exactement `AcceptDrop` côté iOS) | `KadreUikitDropAbsenceTests` |
| inventaire displays UIKit — écran de la scène host publié `DisplayType.HostViewport`, écrans attachés hors host publiés `Physical`, relecture complète sur notifications `UIScreenDidConnect`/`UIScreenDidDisconnect`/`UIScreenModeDidChange`, accès au SDK marshalé main-thread | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` (`UIScreen`, notifications écran présentes dans les deux klibs) | BCK-027 (2 simulateurs) | tvOS : un seul `HostViewport` ; iOS Simulator : inventaire miroir de `UIScreen` au moment de lecture, sans AirPlay/écran externe programmable ; `workArea = null` toujours (pas de bureau) ; AirPlay/écrans externes, changements de mode réels et multi-display physique = charter manuel | `KadreUikitDisplaysTests` |
| inventaire devices UIKit — miroir GameController/GCKeyboard/GCController au moment de lecture, aucune fabrication, reconnexion = id public neuf après retrait terminal | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` (`GameController`) | BCK-027 (2 simulateurs) | ÉQUIVALENCE MIROIR obligatoire : inventaire publié == énumération SDK `GCController`/`GCKeyboard` à l'instant de lecture ; scénarios driver vides = référence controller-free (both-empty), mais le littéral zéro est rouge par design sur Xcode 27 Simulators si des contrôleurs host sont bridgés ; GameController matériel réel (BT/USB), AirPlay/Apple TV et reconnect physique = charter manuel | `KadreUikitDevicesGamepadsTests`, `UIKitInputDevicePortTest` |
| gamepads UIKit, effets et raw input — seuls les contrôleurs `extendedGamepad` deviennent des `Gamepad`; effets annoncés seulement si GameController les expose ; raw input reste refusé | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` (`GCController`, `GCExtendedGamepad`, haptics si exposé par le SDK) | BCK-027 (2 simulateurs) | Siri Remote/micro-only : fact device dans `InputDevicePort`, jamais full gamepad ; effets absents → `Unsupported(GamepadEffect)` et teardown interrompt les effets vivants ; `RawInputAccess` reste `Unsupported(RawInputAccess)` avec preuve de non-appel ; validation manette réelle, déconnexion pendant effet et rumble/haptics = charter manuel | `KadreUikitDevicesGamepadsTests`, `UIKitGamepadPortTest` |
| pression mémoire UIKit — observer process-wide `UIApplicationDidReceiveMemoryWarningNotification`, relayé hors pile vers les sessions vivantes | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` (`UIApplicationDidReceiveMemoryWarningNotification` présent iOS et tvOS) | BCK-027 (2 simulateurs) | matrice normative unique : notification mémoire → `MemoryPressureLevel.Moderate` SEULEMENT ; `Critical` jamais produit ; événements visibilité/lifecycle jamais mappés en pression ; capability `Available` seulement après registration, sinon `Unsupported`/`Unavailable` honnête selon le seam | `KadreUikitMemoryPressureTests` |
| capture cible `HostChoice` — bras ReplayKit in-app, availability-gated : `RPScreenRecorder.isAvailable()` LU VIVANT (la FONCTION klib) à la construction, à chaque `requestPermission(Screen)` et à chaque `reserve` — JAMAIS « Simulator ⇒ indisponible » codé en dur ; région refusée STRUCTURELLEMENT avant tout fait de seam ; démarrage `startCaptureWithHandler` (handler → fenêtre fact+copy lock read-only → kernel fourcc → budget AVANT la copie → memcpy bloc), terminal exactement une fois, frames après stop jamais ; erreur du recorder au démarrage = `Failed(PlatformFailure(UIKit, "replaykit", "start:<code>"))`, en flux vivant = `Failed(..., "handler:<code>")` — `SourceLost` JAMAIS émis par ce bras (réservé à la surface détachée) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` (`RPScreenRecorder` porte `isAvailable`/`startCaptureWithHandler`/`stopCaptureWithHandler` dans les DEUX klibs ; les ABSENCES tvOS sont aussi des faits de compile : `cameraEnabled`/`setCameraEnabled` = 0 hit dans le knm tvOS, `RPSystemBroadcastPickerView` = absent du klib tvOS et interdit PARTOUT — vérifiés au spike T1, strings knm ; le compile tvOS est un fait, le runtime tvOS reste charter) | BCK-028 (2 simulateurs) | INJOIGNABLE via tout target public aujourd'hui : `hostPicker` est structurellement `Unsupported` (les broadcast extensions sont un PROCESS SÉPARÉ dont les frames n'atteignent jamais l'app — hors du contrat fermé ; aucun lifecycle de picker in-process n'existe sur iOS/tvOS) et l'admission runtime d'un open `HostChoice` l'exige — `open(HostChoice)` répond `Unsupported(CaptureOpen)` AVANT tout fait de port, delta de réservation zéro, zéro frame (scenario `uikit-capture-hostchoice-unreachable-honest`, sentinelle `uikit-capture-no-picker-promise`) ; la cellule `screen` reste `Supported(Bgra8, EmbeddedWhenAvailable, region Unsupported)` availability-gated — elle décrit le PORT (la vérité du primitif ReplayKit), l'absence vit dans la cellule `hostPicker` où elle appartient (ruling T5, porté par la colonne risque de la ligne de contrat) ; les frames ReplayKit ne sont JAMAIS livrées sur Simulator (MESURÉ : `isAvailable = true` mais la complétion du démarrage ne vient pas sous bornes, l'arrêt répond `-5829` `RPRecordingErrorAttemptToStopNonRecording` — il n'y a jamais eu d'enregistrement, zéro frame, jamais maquillé) : la preuve de frames = charter manuel sur device ; `requestPermission` ne prompte JAMAIS (aucun dialogue n'existe pour la capture in-app — permission écran = la disponibilité elle-même, `Granted` / `Unavailable(TemporarilyUnavailable(retryable = true))`, fenêtre `Unavailable(Unsupported(CapturePermission))` ; les journaux de seam ne portent QUE des lectures de disponibilité) ; micro/caméra jamais activés (l'audio hors du modèle de frames — les samples non vidéo sont comptés, jamais livrés ; sur tvOS les boutons caméra n'existent MÊME PAS dans le klib — 0 hit `cameraEnabled`/`setCameraEnabled`, spike T1 : une absence de COMPILE, pas de politique) ; dépréciation SDK 27 des deux formes au profit de ScreenCaptureKit (`RPScreenRecorder.h:80,85`) : SURFACÉE — compile et appelable des 4 cibles, la migration SCStream est une question de ROADMAP | `KadreUikitCaptureTests` |
| capture cible `Surface` — pompe de snapshots de la vue du host (`drawViewHierarchyInRect` primaire, repli `layer.render(in:)`, readback `bitmapInfo` + `bytesPerRow` PAR frame — le format n'est JAMAIS supposé), streamée par un ticker injecté (production : `dispatch_after` auto-reschedulé main-queue, main-confiné — CADisplayLink reste le repli documenté du spike) ; `duration` = la période armée, cadence publiée `Fixed(minimumFrameInterval)` ou `Unknown` (repli de période 1/30 s = fait de CONFIGURATION documenté) ; reconfiguration publiée AVANT la frame qui la porte (taille ET format — la voie de rendu peut changer entre deux snapshots) ; budget par frame vérifié AVANT la copie (`ResourceLimitExceeded(CaptureBuffer, max)`, zéro octet copié — ordre mutation-épinglé) ; vue hors fenêtre lue AVANT tout dessin → `SourceLost` exactement une fois ; format hors table persistant → terminal `Failed(PlatformFailure(UIKit, "capture-stream", "frame-format-out-of-table"))` — JAMAIS un skip silencieux ; close du port et du runtime terminent la réservation vivante, ticker désamorcé exactement une fois | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-028 (2 simulateurs) | LIVRÉE et PROUVÉE DE BOUT EN BOUT SUR SIMULATOR avec de VRAIES frames (les deux familles, 640×480, format rgba8 LU) — LE STIMULUS DE CONTENU fait partie du contrat de preuve : une vue VIDE produit des rasters MENTEURS (bitmapInfo `0x1` — une étiquette rgba8 dans la table — portée par `bytesPerRow = 2·w`, une rangée qui ne tient pas un pixel complet du contrat prétendu) que le kernel d'honnêteté refuse du terminal `frame-format-out-of-table` (mesuré au premier run rouge, documenté dans l'en-tête du scénario et la note `project.yml`, jamais maquillé) ; avec du contenu rendu, la voie de repli `layer.render(in:)` livre le raster cohérent ; `Rgba8, Hidden, region Unsupported` — la région est refusée STRUCTURELLEMENT `Unsupported(CaptureOpen)` avant même la résolution du registre ; id inconnu → `InvalidRequest("request.target")` APRÈS UNE résolution ; le runner de test K/N émet des rasters incohérents (fait d'HÔTE documenté — 16 bpp étiquetés `0x1`, refusés par le plancher de stride du kernel) : la preuve de frames réelles vit dans l'hôte XCTest Swift ; `sourceTimestamp = null` (un snapshot ne porte aucun PTS — le fait global d'une horloge n'est pas un fait de frame) | `KadreUikitCaptureTests` |
| capture `Source`, fenêtre, picker et énumération — LES ABSENCES EXACTES : aucune primitive in-process d'énumération des surfaces capturables n'existe sur iOS/tvOS ; `window` = `Capability.Unsupported(CaptureOpen)`, `sourceEnumeration` = `Unsupported(CaptureRefreshSources)`, inventaire `sources` = `Unavailable(même échec)` — JAMAIS `HostPickerOnly` (le web l'annonce parce qu'un VRAI picker navigateur existe derrière un consentement ; ici aucun parcours conforme n'existe) ; `hostPicker` = `FeatureAvailability.Unsupported` — les broadcast extensions sont un process séparé dont les frames n'atteignent jamais l'app, hors du contrat fermé ; `requestPermission(Window)` refusé avant tout fait de seam (delta nul) ; `refreshSources` répond l'inventaire sans toucher le seam | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-028 (2 simulateurs) | rien à livrer tant qu'aucun parcours conforme n'existe : toute apparition d'un picker ou d'un inventaire serait une fabrication — les compteurs driver (`captureSurfaceReservationsOpened` delta 0 derrière le refus d'admission HostChoice, `captureSurfaceFramesDelivered` figé après terminal) et les journaux de seam au delta nul prouvent les zéros ; depuis le consumer, `open(Source)` répond `InvalidRequest("request.target")` (aucune source énumérée — la forme CONSUMER-VISIBLE que la ligne de contrat cite, le port gardant son `Unsupported(CaptureOpen)` en profondeur) ; un futur parcours conforme (ruling + device) est le chemin d'activation enregistré | `KadreUikitCaptureTests` |

Note de gate des lignes phase 3 (façade Swift, `KadreHostViewController`, distribution
XCFramework) : le gate consumer (`consumers/swift`, 2 familles) tourne localement via
l'agrégateur `kadre/consumers/swift` ; le câblage CI (job xcode-27 dédié ou job macOS
consolidé) est un follow-up enregistré (revue finale F-2).

La ligne phase 0 se lit : `KadreUikitProbe.observe(window:view:)` publie une
`KadreUikitObservation` copiée (appartenance à la fenêtre via `isEqual`, connexion de la
scène, dimensions `bounds` et `displayScale`) et ne retient aucune référence native — le
scénario `uikit-driver-observes-real-window` prouve que le Kotlin lit les mêmes valeurs que
le test Swift sur les mêmes objets, le scénario `uikit-driver-reports-detached-view` prouve
qu'une vue hors fenêtre est rapportée `Detached`, et la sentinelle
`uikit-driver-window-membership-sentinel` (le même test détaché, muté) prouve que la
lecture d'appartenance est réellement exercée : supprimer le test d'appartenance tue
exactement ce scénario sur les deux familles sans toucher le préambule
(`testKotlinProbeObservesRealWindow` reste vert).

La ligne BCK-013 se lit : `KadreIos.attach(windowScene:window:surfaceView:applicationFactory:policy:)`
refuse (`InvalidRequest`) une vue hors de la fenêtre fournie, réserve la scène AVANT toute
installation (`AlreadyInUse(Host)` sur un second attach, première session intacte), termine
exactement une fois sous un arrêt pendant un arrêt, conduit le lifecycle par les
notifications de scène RÉELLES et ignore les callbacks natifs post-terminaison. La
sentinelle `uikit-scene-single-owner` (mutation : ignorer l'échec de
`UIKitSceneRegistry.reserve`) est tuée par `testSecondAttachOnTheSameSceneIsAlreadyInUse`
sur les deux familles ; la sentinelle `uikit-lifecycle-scene-faithful` (mutation : fabricater
l'état réduit d'`DidEnterBackground`) est tuée par
`testLifecycleTransitionsFollowTheScene` sur les deux familles. La table
notification→lifecycle est le réducteur normatif `UIKitLifecycleReducer` ; les callbacks
natifs post-terminaison sont neutralisés en profondeur (tokens retirés, routage tardif
inerte, `updateLifecycle` dédoublonné puis no-op sur session terminée) — voir le rapport de
preuve Task 6 pour les rayons d'explosion exacts.

La ligne BCK-014 se lit : sur iPad, une fenêtre d'une AUTRE scène est refusée
(`InvalidRequest("window")`), deux scènes attachées tournent des sessions indépendantes
(ids, surfaces, lifecycles distincts), et la destruction de la session de scène d'une
scène ne termine que SA session (fallback ruling contrôleur : post du vrai nom de
notification — voir la note environnement ci-dessus). La sentinelle
`uikit-window-scene-owner` (mutation : supprimer la comparaison `windowScene.isEqual`)
est tuée par `testAttachRejectsWindowFromAnotherScene` sur la famille iOS seule.

La ligne WIN-009 se lit : la surface primaire EST la surface de la fenêtre primaire (même
`SurfaceId` via `isEqual`, même instance via `===` à travers le pont) et les métriques
initiales valent la lecture Swift des `bounds` de la vue. La sentinelle
`uikit-primary-metrics-faithful` (mutation : fabricater le `contentSize` du
`WindowSpec` d'admission) est tuée par `testPrimaryWindowAndSurfaceIdentity` sur les deux
familles.

La ligne BCK-015 « observations dynamiques, redraw et apply » se lit : à chaque déclencheur
SDK, les métriques publiées valent la lecture VIVANTE de la vue ; un changement entre deux
déclencheurs est observé au déclencheur suivant (resize réel 480×200 propagé,
thème conduit par les overrides réels fenêtre puis vue — repli documenté, §3.3 du rapport
Task 5 —, reprise background/foreground prouvée par le compteur de re-lectures), le redraw
coalesce une rafale de 5 (puis 3) requêtes en EXACTEMENT UN événement par tick (fenêtre de
quiescence), l'`apply` refuse fermé tout champ posé (`PartiallyApplied` à exactement un
rejeté, `Unsupported(UpdateSurface)`) et accepte tout-`Unchanged` (`Applied`), et un
`requestRedraw` post-close répond `Closed(Surface)` sans aucun événement résiduel. Trois
sentinelles : `uikit-metrics-stale-generation` (mutation : le binding republie la géométrie
gelée à la génération d'attach) est tuée par `testSurfaceResizePropagatesMetrics` — des
métriques figées ne portent jamais 480×200 — preuve citée sur tvOS (échec SEUL, rayon au
rapport Task 6 ; même chemin binaire sur iOS) ;
`uikit-redraw-single-per-tick` (mutation : une requête de rafale — ticket en vol — est
REFUSÉE au lieu d'être absorbée par le coalescing ; le gate scheduler seul est MASQUÉ par
le ticket du runtime, cf. rapport Task 6) est tuée par
`testSurfaceRedrawBurstCoalescesToOnePerTick` (« every redraw of the burst must be
admitted » — échec SEUL, preuve citée sur tvOS) ;
`uikit-lease-closed-after-terminal` est la sentinelle de la ligne suivante.

La ligne BCK-015 « bail d'accès natif » se lit : le premier bail est accordé (la vue louée
`isEqual` la surfaceView), un bail DANS la callback du premier répond
`TemporarilyUnavailable(retryable = true)` sans jamais invoquer son block, et le verdict
STEADY post-close est `Unsupported(PlatformSurfaceAccess)` déterministe — la route du
registre (`UIKitViewLeaseRegistry`) est retirée au close AVANT la publication de l'état
détaché. La sentinelle `uikit-lease-closed-after-terminal` (mutation : retirer
l'`unregister` du close — la route survit à la terminaison) est tuée par
`testLeaseBoundariesHonoursFlightAndClose` : le bail post-terminaison retombe alors sur le
port résiduel et répond `Closed(Surface)` (course en vol), jamais le `Unsupported` ruled —
l'assert du verdict steady échoue SEUL (preuve citée sur tvOS, rayon au rapport Task 6).

La ligne BCK-016 se lit : après un changement de géométrie réel de la vue (pleine fenêtre
puis resize à changement d'aspect 240×320 — stimulus DÉGRADÉ, cf. la note rotation
ci-dessus), les métriques publiées valent la géométrie VIVANTE post-stimulus
(`wantWidth ≠ beforeWidth`, published == live). La sentinelle
`uikit-rotation-metrics-faithful` (mutation : le kernel sert la géométrie du miroir
courant — un cache périmé — au lieu de l'observation vivante) est tuée par
`testSurfaceRotationPropagatesMetrics` sur la famille iOS seule : l'égalité géométrique
post-changement (publié == vivant) échoue — l'état ne quitte jamais les dimensions
d'attach, le test meurt à l'attente bornée de la géométrie post-stimulus (rayon exact au
rapport Task 6 : sous ce mutant, exactement `testSurfaceResizePropagatesMetrics` et
`testSurfaceRotationPropagatesMetrics` échouent, la même couture de fidélité géométrique
portant les deux morts). Le kill est PORTÉ PAR LA FIDÉLITÉ GÉOMÉTRIQUE DU CHEMIN DÉGRADÉ
(published == vivant après un changement réel de géométrie), jamais par une rotation
réelle : la rotation réelle est réservée à un runtime qui accepte
`requestGeometryUpdate` (charter manuel / runtime futur) — la branche réelle du test
(fidélité exacte de l'échange portrait↔paysage) reste codée et s'exécutera alors.

La ligne BCK-020 se lit : sur une session SANS provider (le défaut tvOS — mono-scène par
design, DESIGN.md §15.2 — et tout app mono-fenêtre), la capability `requestWindow` est
annoncée `Unsupported(RequestWindow)` et toute demande rend au requérant une requête DÉJÀ
terminale `Rejected(Unsupported(RequestWindow))` AVANT toute réservation : aucun provider
invoqué (compteur), aucune scène activée, aucune corrélation, état du manager intact, et
une seconde demande est refusée pareil (rien n'a été consommé par la première). La
sentinelle `uikit-window-request-tvos-refusal-faithful` (mutation : la capability
null-provider annonce `Supported({OpenedInNewSession})` — le refus ne dit plus la vérité)
est tuée par `testWindowRequestWithoutProviderIsRejectedUnsupportedWithNoNativeEffect` sur
les DEUX familles (même binaire Kotlin, même couture `sceneWindowCapability`) ; le refus
reste par ailleurs pur : toute réservation ou route provider sur ce chemin échoue aux
asserts de pureté du même test (compteur provider, corrélations, résolutions, primaire).

La ligne BCK-021 se lit : sur iPad, `requestWindow` sur un requérant portant le provider
app-owned reste `Pending` jusqu'à la connexion de la NOUVELLE scène corrélée à la demande
(activation `requestSceneSessionActivation` côté app, corrélation par identité — jamais
par index), puis l'enfant s'attache par le chemin ordinaire et l'outcome est
`OpenedInNewSession(childId)` : la chaîne requestId ↔ scène ↔ session enfant est prouvée
deux à deux distincte sur une paire concurrente, le requérant reste unique-fenêtre
(l'enfant n'entre JAMAIS dans sa liste), l'arrêt et le `close()` du requérant détachent
l'enfant du destin du requérant — l'enfant déjà résolu SURVIT (attaché, app vivante,
surface intacte ; « detach » = séparé du sort du requérant, jamais « enfant terminé ») —,
une seconde demande repart du même requérant (slot libéré), et une UIWindow overlay de la
scène requérante survit intacte (visible, toujours key, jamais une fenêtre Kadre).
`uikit-window-request-cancel-before-connect` prouve le cancel avant connexion :
`CancelledBeforeCommit`, demande terminale `Cancelled` pour toujours, et la résolution
tardive de la scène (si FrontBoard l'honore) est un no-op — AUCUN enfant ; le
renforcement de revue (Task 4) fait échouer le scénario quand la gate relâchée ne produit
NI résolution NI refus d'activation explicite (plus de passe vacue). Deux sentinelles :
`uikit-window-request-correlation-crossed` (mutation : `claim` ignore le registre — il
rend la DERNIÈRE entrée pendante au lieu de l'entrée de l'identité demandée) est tuée par
`testConcurrentWindowRequestsCorrelateEachSceneToItsOwnRequest`, seul scénario à porter
deux demandes pendantes EN MÊME TEMPS (les scénarios mono-demande ne voient jamais la
différence) ; `uikit-window-request-cancel-terminal-faithful` (mutation : le `cancel` ne
publie plus l'outcome terminal — le CAS de terminaison est court-circuité, l'annulation
répond `CancelledBeforeCommit` sans jamais terminer la requête) est tuée par
`testWindowRequestCancelledBeforeTheSceneConnectsStaysTerminalWithNoChild` (l'`await` de
l'outcome ne délivre jamais — seul scénario du driver à annuler). La résurrection d'une
demande annulée par la résolution tardive est défendue à DEUX coutures (retrait de
l'entrée au cancel + garde de terminalité au `claimResolve` — chaque couture prouvée en
uikitTest kernel) ; aucune mutation à couture unique ne peut la traverser, et la borne de
pending (`maxPendingWindowRequests`) est du ressort kernel (uikitTest), sans test de borne
au driver.

La ligne BCK-022 se lit : l'attach pose LE recognizer de touch Kadre (`UIKitTouchRecognizer`,
sous-classe Kotlin de `UIGestureRecognizer` exportée `@ExportObjCClass` + `@ObjCAction`) sur
la vue du host — ADDITIVEMENT (`cancelsTouchesInView == false`, la décision tracée : le host
garde ses touches ; les SEULS recognizers ajoutés sont ceux de Kadre) — publie
`InputObservationChanged(touchInstalled = true)` après l'installation, puis révoque tout au
close (recognizer retiré, annonce all-false, zéro résiduel). Le fait POINTEUR est prouvé PAR
FAMILLE dans le scénario partagé (`uikit-input-pointer-source-per-family`) : le survol
s'installe LÀ OÙ LE KLIB LE PORTE (iOS : `hoverInstallCount` bouge ; tvOS : la classe est
absente du klib — `pointerInstalled` reste faux sans faux écrit) et suit le même retrait. La
LIVRAISON réelle d'un touch reste un charter manuel (ruling pré-vol : pas synthétisable
in-process ; `touchEventCount == 0` et `touchStimulusCount == 0` sont des invariants PROUVÉS
du driver, jamais un silence). Deux sentinelles : `uikit-input-touch-additive-policy`
(mutation : le recognizer Kadre pose `cancelsTouchesInView = true` — le host perd ses
touches) est tuée par `testTouchRecognizerInstalledAdditivelyWithHonestAnnouncement`
(XCTAssertFalse « the traced additive policy: the host view keeps its touches », ligne 170) ;
LE MÊME mutant tue `testGestureAndKeyboardSourcesInstalledAdditivelyPerFamily` (le prédicat
`cancelsTouchesInViewAll` couvre le même recognizer — rayon DEUX tests, couture partagée,
preuve citée sur tvOS, 22 exécutés / 2 échecs, même chemin binaire uikitMain sur iOS) ;
`uikit-input-announcement-revoked-at-close` (mutation : le teardown du touch ne publie plus
la révocation — l'annonce de morte ne part jamais) est tuée par
`testTouchRecognizerRemovedAndAnnouncementRevokedAtClose` SEUL (XCTAssertGreaterThanOrEqual
« ("11") is less than ("12") - the all-false revocation announcement must have been
published », ligne 290 — les compteurs d'annonce sont PAR binding : les révocations
clavier/gestes du même teardown ne masquent pas le trou ; preuve citée sur tvOS, 22
exécutés / 1 échec).

La ligne BCK-023 se lit : le binding clavier pose D'ABORD les observers de connexion
(`GCKeyboardDidConnect/DidDisconnect`), puis scanne `GCKeyboard.coalescedKeyboard` — sur un
simulateur qui appareille un clavier physique (« Connect Hardware Keyboard »), l'attach à ce
VRAI objet est un fait vrai, jamais une fabrication (`keyboardConnectCount` ne décroît
jamais, peut légitimement croître) ; l'ensemble EXACT des recognizers de geste par famille
(iOS tap/pan/pinch/rotation, tvOS tap/pan — le klib tvOS ne porte ni pinch ni rotation, ni
le survol) est sur la vue, chaque recognizer `cancelsTouchesInView == false`, et
`gestureKinds` PORTE cet ensemble exact (jamais un de plus). Les LIVRAISONS réelles restent
des charters manuels : `keyboardEventCount == 0`, `keyboardStimulusCount == 0` et
`gestureStimulusCount == 0` sont des invariants PROUVÉS du driver — et la payload d'une
future livraison est bornée par le kernel : `LogicalKey` toujours `Unidentified(null)` et
modifiers toujours vides sur la route GC (pas de `characters`, pas de FlagsChanged — le
texte attend la route UIKeyInput, phase 6), le mapping code inconnu/hors-domaine →
`Unidentified(null)` est prouvé en uikitTest (`UIKitKeyboardHoverMappingTest`) HORS du
pipeline driver (aucune livraison automatisable — aucune sentinelle driver n'est donc
enregistrée pour ce fait, choix documenté), de même que la perte explicite des phases
`Cancelled`/`Failed` de geste (`UIKitGestureStatePhaseTest` — aucun état de geste retenu au
runtime ; la perte est documentée au DESIGN §15.2, Task 5). Deux sentinelles :
`uikit-input-keyboard-binding-live` (mutation : l'`install()` du clavier retourne immédiatement
— binding mort, ni observers, ni scan, ni annonce) est tuée par
`testGestureAndKeyboardSourcesInstalledAdditivelyPerFamily` SEUL (XCTAssertGreaterThan
« ("0") is not greater than ("0") - the keyboard binding install counter must have moved »,
ligne 223 ; le compteur de teardown, indépendant, bouge encore — le test de révocation reste
vert ; preuve citée sur tvOS, 22 exécutés / 1 échec) ; `uikit-input-gesture-set-exact`
(mutation : l'install pose tous les recognizers SAUF le pan — l'ensemble de la vue dérive de
l'ensemble annoncé) est tuée par `testGestureAndKeyboardSourcesInstalledAdditivelyPerFamily`
(XCTAssertEqual — « UIPanGestureRecognizer » absent de l'ensemble de la vue, ligne 213) ;
le MÊME mutant tue les DEUX tests touch (compte total de la vue : 2 ≠ 3, ligne 173 ; retour
au baseline : 0 ≠ −1, ligne 286) — rayon TROIS tests, physiquement inévitable et honnête :
l'inventaire des recognizers de la vue est UNE couture portée par les trois assertions
(miroir du rayon de la sentinelle rotation, phase 2) ; preuve citée sur tvOS, 22 exécutés /
3 échecs.

La ligne BCK-024 se lit : l'open texte est EXCLUSIF et le refus nomme la vérité — le
second `openTextInput` sur une surface vivante répond `AlreadyInUse(TextInputSession)`
(l'admission du runtime refuse AVANT le port ; le CAS `liveOwner` du port reste la défense
en profondeur — deux coutures qu'aucune mutation unique ne traverse depuis la route
publique du driver), sans installer de second receiver, la première session restant
`Active` à sa révision. Le `Replace` est mappé depuis la PLAGE qu'UIKit nomme (le hook
utilisateur `shouldChangeCharactersInRange` résout son range contre l'ombre AU MOMENT du
hook ; la suppression d'un emoji porte la PAIRE surrogate ENTIÈRE (2,4)+"" — la frontière
de graphème calculée par UIKit, jamais recomposée par Kadre) et l'observation est timbrée
de la révision que le runtime accepte encore (`replaceText` ne mint PAS de révision). Le
write-back est la direction Kotlin → champ (le champ d'abord, l'ombre ensuite) et un
write-back à révision PÉRIMÉE répond `StaleRevision(expected, received)` typé sans toucher
ni le champ ni le document. Le close pendant une composition vivante termine la session
(`Closed`), retire le receiver de la hiérarchie (plus aucun receiver Kadre sous la vue du
host) et rend le CAS — une ré-ouverture est admise, un receiver NEUF sans marqué reporté.
Trois sentinelles : `uikit-text-open-exclusive-refusal-faithful` (mutation : l'admission
refuse avec `AlreadyInUse(Host)` — le refus ne dit plus la vérité sur la ressource occupée)
est tuée par `testUikitTextOpenExclusiveRejectsSecond` SEUL (l'identité
`already.resource === KadreResourceKind.textinputsession` échoue — « the refused resource
is the text input session, got Host » ; preuve citée sur tvOS, 8 exécutés / 1 échec, la
classe texte entière — même binaire Kotlin sur iOS) ;
`uikit-text-anti-stale-revision` (mutation : le refus `StaleRevision` INVERSE sa payload —
`expected` porte la révision reçue et `received` la révision acceptée, le refus accuse le
mauvais état) est tuée par `testUikitTextStaleRevisionRefused` SEUL
(XCTAssertEqual « ("3") is not equal to ("5") - the expected revision is the accepted
one » ; preuve citée sur tvOS, 8 exécutés / 1 échec, la classe texte entière) — un mutant qui ACCEPTerait le
write-back périmé ne peut pas traverser une couture unique (le runtime refuse d'abord, la
garde `Stale` de l'ombre couvre ensuite : défense en profondeur documentée) ;
`uikit-text-close-terminal-faithful` (mutation : le close ne publie plus l'état terminal
`Closed` — la session ferme son owner et rend son admission mais ferme « invisiblement »,
l'état reste `Active` pour toujours) est tuée par
`testUikitTextCloseNoOrphanComposition` SEUL (l'attente « session Closed » n'aboutit pas —
« session Closed not reached in 5.0s — last=Optional(Active(...)) » ; preuve citée sur
tvOS, 8 exécutés / 1 échec, la classe texte entière) — le retrait natif du receiver, lui, est porté
par la couture `safeCloseOwner` que l'open-exclusive asserte aussi en fin de scénario
(rayon deux tests si on la mute : c'est le motif documenté du choix de la couture
terminale). Les limites délivrées, portées par la ligne : le couplage
focus→Suspend n'existe PAS côté UIKit (le snapshot reste `Unfocused` à vie, les hooks de
focus ne comptent pas plus que compter — les jambes `Suspended`/resume sont le charter du
vrai clavier) ; les édits programmatiques purs sont inobservés par délivrance (verdict
`Nothing` du routeur pur — la route champ→Kotlin est routée pour la saisie utilisateur
seule, dont la preuve sans conduite attend le charter du vrai clavier/IME) ; le mapping
Email/Password est prouvé sur les brutes du champ contre les constantes UIKit VIVANTES de
la plateforme de test, la table complète (tous purposes/actions/capitalisations) étant
épinglée hors appareil en uikitTest.

La ligne BCK-026 (iOS) se lit : l'attach pose EXACTEMENT UNE `UIDropInteraction` Kadre sur
la vue du host (`uikit-drop-canhandle-types` compte l'installation ; le teardown la retire —
zéro résiduel, `uikit-drop-no-residual-interaction`), la capacité `handlerInteractions`
annonce EXACTEMENT `AcceptDrop` (lue sur le snapshot exporté — `uikit-drop-tvos-absent`,
branche contrôle iOS), et le flux est conduit par le VRAI delegate : `canHandle` répond sur
la sonde à types enregistrés SEULE (un provider hors table `public.data` est refusé, une
session vide aussi — la table fermée perd le type, elle ne l'approxime jamais) ;
l'entrée présente l'offre au consumer par le dispatch synchrone du runtime
(`InteractionEvent.DropEntered` DANS la callback, position logique 42×17 traversée telle
quelle, `AcceptDrop` commis par `context.request` — le chemin `Now` du port UIKit,
`uikit-drop-offer-presented`) ; la proposition native suit l'état TENU (`.copy` si une offre
vit, `.cancel` sinon — JAMAIS `.move` ; le mutant « toujours Copy » est tué par
`uikit-drop-no-fabricated-admission` sur l'assertion propre de `uikit-drop-rejected`, un
drop refusé qui proposerait Copie serait une admission fabriquée — second site de kill :
`uikit-drop-performed`, la vérité locale « plus rien n'est tenu » du sélecteur `void`) ;
l'offre refusée est terminée `Rejected` (la décision est celle du consumer — canHandle
continue de répondre vrai, le refus n'est pas celui de la sonde) ; la sortie et
`sessionDidEnd` terminent `LeftSurface` (le claim de l'ancienne offre répond alors
`Closed(DropTransfer)`), une ré-entrée ouvre un id NEUF, et une entrée qui REMPLACE une
offre encore active termine la précédente `LeftSurface` à la présentation — au plus une
offre active (`uikit-drop-exit-reenter`) ; le drop performé rend le transfert claimable
(`TransferAvailable` créé dans le même réducteur qui publie `InputEvent.Dropped`), un
second perform ne republie rien (`uikit-drop-performed`) ; le close EN PLEIN offre termine
`OwnerClosed` et toute livraison TARDIVE sur le delegate détaché est un no-op compté ZÉRO
(`uikit-drop-teardown-mid-offer`).

La ligne BCK-026 (transfert) se lit : le claim est à gagnant UNIQUE (le second répond
`AlreadyInUse(DropTransfer)`), la lecture bornée SOUS la payload répond
`ResourceLimitExceeded(DropItem, maxBytes)` sans rien livrer au-delà, la lecture à taille
inconnue streame à l'EOF (`Replayable` — "hello" réassemblé), le descripteur Uri lit
`public.url` et rend la chaîne ABSOLUE de l'URL (le contenu que `text/uri-list` annonce —
le byte-read mort-né d'un NSData non-URL est évité par la route NSURL épinglée), le
descripteur File lit `public.file-url` et rend la CHAÎNE de l'URL fichier — le contenu
disque security-scoped reste l'exit roadmap, divergence AppKit documentée — et le
chargement du provider est PARESSEUX (la première lecture suspend sur la completion de
`loadItemForTypeIdentifier`, l'item entier est mis en cache, les relectures ne rechargent
pas) sous les budgets de la policy : au plus `maxConcurrentDropTransfers` transferts
revendiqués, tranches ≤ `maxDropChunkBytes`, une offre sans claim meurt au
`dropTransferClaimTimeout`, la session retient au plus `maxRetainedPayloadBytesPerSession`
(`uikit-drop-accepted-bounded`).

La ligne BCK-026 (tvOS) se lit : l'ABSENCE est structurelle — `UIDropInteraction`,
`UIDropInteractionDelegateProtocol`, `UIDropSessionProtocol` et `UIDragDropSessionProtocol`
sont `API_UNAVAILABLE(tvos)` (UIDragSession.h:19 ; 0 symbole dans le klib tvOS 2.4.20,
vérifié au compile ET dans le linkdata), l'actual `tvosMain` répond `null`/ensemble vide,
la capacité garde son refus `Unsupported(InstallInteractionHandler)`, et le driver prouve
les zéros (aucune interaction drop sur la vue avant/après attach et close, compteurs
`dropInstallCount`/`dropTeardownCount`/`dropEventCount` figés, zéro livraison après pompe)
— la sonde est STRUCTURALE (comptage par NOM de classe, jamais une référence au type) et le
CONTRÔLE iOS du même test prouve qu'elle n'est pas toujours-verte ; la compilation de la
source partagée est elle-même la garde (le type est innommable sur tvOS). Le drag CROSS-APP
physique (depuis Safari/Photos vers l'app) reste le charter manuel : aucune API de drag
système programmable n'existe sur Simulator (note `project.yml` du driver) — la preuve
automatisée est in-process, delegate-driven, jamais un événement fabriqué. Les deux
sentinelles : `uikit-drop-no-fabricated-admission` (mutation : la proposition `Copy`
inconditionnelle) est tuée par `test_uikit_drop_rejected` SEUL sur iOS (l'assert
`.cancel` — « a rejected drag proposes Cancel — never a fabricated Copy » ; preuve citée
sur tvOS par la classe d'absence entière, même binaire Kotlin) ;
`uikit-drop-no-residual-interaction` (mutation : le teardown saute
`view.removeInteraction`) est tué par `test_uikit_drop_teardown_mid_offer` SEUL (l'assert
zéro-résiduel — « ("1") is not equal to ("0") - the teardown removes the drop interaction
— nothing residual » ; sur la classe entière, le rayon CASCADE : chaque attach suivant
hérite de l'interaction fuie devant la sienne, delegates détachés qui refusent tout en
comptant zéro ; preuve citée sur tvOS, 32 exécutés / les mêmes coutures portées par la
classe d'absence).

La ligne BCK-028 « capture » se lit PAR CIBLE — trois lignes de registre, trois décisions.
La cible `Surface` est la SEULE ouvrable publiquement et porte la preuve de bout en bout
sur Simulator : les scénarios de flux (`uikit-capture-surface-stream-bounded`,
`uikit-capture-stop-concurrent`, `uikit-capture-late-callback-after-close`,
`uikit-capture-frames-release-bounded`) attachent la session RÉELLE, ouvrent
`KadreScope.capture.open(Surface(primary.id))` et collectent de VRAIES frames — LE
STIMULUS DE CONTENU fait partie de la preuve (la vue passe `backgroundColor = .red` +
`layoutIfNeeded` avant l'open) : MESURÉ au premier run rouge, une vue VIDE produit des
rasters MENTEURS (bitmapInfo `0x1` — une étiquette rgba8 dans la table — portée par
`bytesPerRow = 2·w`, une rangée qui ne tient pas un pixel complet du contrat prétendu) que
le kernel d'honnêteté refuse du terminal
`Failed(PlatformFailure(UIKit, "capture-stream", "frame-format-out-of-table"))` ; avec du
contenu rendu, la voie de repli `layer.render(in:)` livre le raster cohérent
(`capture readback: path=layer.render width=640 height=480 bitmapInfo=0x1`, rgba8, les
DEUX familles). Le format publié est CELUI LU — `liveRendererReadback` relit les MÊMES
deux voies que le pump et décode avec LA MÊME table que `uikitSurfaceBitmapInfoFormatOf`,
jamais l'inverse. La sonde de fuite (`uikit-capture-frames-release-bounded`) enchaîne
trois cycles open → 4 frames → stop sur la même vue : les asserts au repos DÉTERMINISTES
portent le kill (le slot unique de session de capture rendu — le `open` suivant réussit,
une réservation fuie échouerait exactement là —, `captureSurfaceReservationsInFlight == 0`,
journal d'attach à la baseline, compteur de frames livrées += exactement les frames
collectées) ; la proxy mémoire tracée (résidence mach avant/après) est un garde-débat
HONNÊTE (croissance < 256 Mo — le bruit GC des copies transitoires mesuré 21-39 Mo pour
~14,7 Mo streamés), jamais un seuil serré inventé.

La cible `HostChoice` se lit en DEUX faits disjoints, tous deux prouvés. (1) Le PORT
ReplayKit est prêt et availability-gated : `RPScreenRecorder.isAvailable` LU VIVANT
(`true` MESURÉ sur les deux simulateurs 27.0 au moment du gate — jamais
« Simulator ⇒ indisponible » codé en dur), `requestPermission` ne prompte JAMAIS (aucun
dialogue de consentement n'existe pour la capture in-app — la permission écran est la
disponibilité elle-même, `Granted` / `Unavailable(TemporarilyUnavailable(retryable =
true))`, et les journaux de seam ne portent QUE des lectures de disponibilité : scenario
`uikit-capture-permission-never-prompts`). (2) Le chemin est ACTUELLEMENT INJOIGNABLE PAR
TOUT TARGET PUBLIC : `hostPicker` est structurellement `Unsupported` (les broadcast
extensions sont un process séparé dont les frames n'atteignent jamais l'app — hors du
contrat fermé ; aucun lifecycle de picker in-process n'existe sur iOS/tvOS) et l'admission
runtime d'un open `HostChoice` exige cette availability — `open(HostChoice)` répond
`Unsupported(CaptureOpen)` AVANT tout fait de port, delta de réservation zéro, zéro frame
(scenario `uikit-capture-hostchoice-unreachable-honest` ; la sentinelle
`uikit-capture-no-picker-promise` est tuée par les scénarios 1 ET 7 ensemble — l'assert qui
NOMME la sentinelle, plus le refus d'admission qui répondrait un vrai
`RuntimeCaptureSession` derrière le mutant). La cellule `screen` reste
`Supported(...)` availability-gated : elle décrit le PORT (la vérité du primitif
ReplayKit), l'absence vit dans la cellule `hostPicker` où elle appartient (ruling T5,
porté par la colonne risque de la ligne de contrat elle-même ; la variation minimale si la
relecture finale la désavouait : la disponibilité retombe
`Unavailable(Unsupported(CaptureOpen))`). Les frames ReplayKit, de leur côté, ne sont
JAMAIS livrées sur Simulator (fait MESURÉ — la complétion de `startCaptureWithHandler` ne
vient pas sous bornes, l'arrêt répond `-5829`
`RPRecordingErrorAttemptToStopNonRecording` : il n'y a jamais eu d'enregistrement) : la
preuve de frames est le charter manuel sur device, et AUCUN scénario ne prétend des
frames HostChoice sur Simulator. La dépréciation SDK 27 des deux formes au profit de
ScreenCaptureKit (`RPScreenRecorder.h:80,85`) est SURFACÉE : les formes compilent et
restent appelables des 4 cibles, la migration SCStream est une question de ROADMAP, pas de
phase. Sur tvOS, les absences s'étendent AU KLIB : `cameraEnabled`/`setCameraEnabled` ne
figurent pas dans le knm tvOS (0 hit, strings knm) et `RPSystemBroadcastPickerView` en est
absent — interdit PARTOUT par ruling — vérifiés au spike T1 : aucun chemin tvOS de caméra
ou de picker broadcast n'existe même à l'état de symbole.

Les absences `Source`/fenêtre/énumération se lisent comme le web les siennes, en plus
strict : aucun navigateur n'énumère — ici aucune primitive NATIONALE non plus. `window`
`Unsupported(CaptureOpen)`, `sourceEnumeration` `Unsupported(CaptureRefreshSources)`,
inventaire `Unavailable(même échec)` — JAMAIS `HostPickerOnly` (le web l'annonce parce
qu'un VRAI picker existe derrière un consentement ; ici aucun parcours conforme n'existe,
et l'inventer serait le même mensonge qu'une permission fabriquée) ; `refreshSources`
répond l'inventaire sans toucher le seam ; depuis le consumer, `open(Source)` répond
`InvalidRequest("request.target")` (aucune source énumérée — la forme CONSUMER-VISIBLE que
la ligne de contrat cite, le port gardant son `Unsupported(CaptureOpen)` en profondeur).

La ligne porte AUSSI le fix runtime SANCTIONNÉ que les scénarios driver ont TROUVÉ : les
quatre scénarios de flux ont crashé le runner
(`IllegalStateException: capture queued-byte accounting underflow` depuis
`RuntimeCaptureSession.offerFrameLocked`) — un collector unconfined (le pont Swift K/N)
reprend INLINE DANS le `trySend`, décrémente AVANT l'incrémentation (le verrou est
récursif) et l'invariant sous-flue process-fatalement. Fix (runtime, commonMain —
sanctionné et revu) : incrémenter AVANT le send, undo explicite sur chaque voie de sortie,
filet deterministic commonTest
(`unconfinedCollectorReceivesTheInlineDeliveryWithoutAccountingUnderflow` — le mutant
« ancien ordre » échoue au MÊME check/message que le crash Swift), prédicat de budget revenu
à sa baseline algébrique (`queued + leased > maxBufferedBytesPerSession` — pins de borne :
la frame exactement au budget est livrée, un octet de plus termine
`Failed(ResourceLimitExceeded(CaptureBuffer, max))`). Le fix concerne TOUT collector
unconfined FUTUR — UIKit n'en est que la manifestation actuelle (les collectors
web/android/appkit sont interceptés avant).

Les DEUX sentinelles de la ligne : `uikit-capture-no-fabricated-frames` (mutation :
retirer le budget-avant-copie) est tuée PAR LA COUCHE UNIT — les sentinelles uikitTest
oversize, la SURFACE
(`oversizeFirstSnapshotFailsTheStartWithResourceLimitExceededAndClosedToken`) et son
jumeau HOST
(`oversizeFirstFrameFailsTheStartWithResourceLimitExceededAndClosedBuffer`),
échouent sur leurs propres asserts, le compteur `copyCount` du fake épinglant l'ordre (le
driver, lui, ne peut pas honnêtement produire une frame oversize contre le budget policy de
128 Mo — mesuré, enregistré, la preuve de kill citée sur les DEUX familles) ;
`uikit-capture-no-picker-promise` (mutation : `hostPicker` annoncé disponible) est tuée par
les scénarios 1 ET 7, décrits ci-dessus.

## 3. Audit de l'en-tête framework (phase 3 — clôture de la divergence F1)

La phase 2 avait enregistré une divergence (F1) : la règle Kotlin-only d'INTEROP-EXPORTS
§7 était tenue par la façade promise, mais l'en-tête généré du framework
(`KadreUikit.framework/Headers/KadreUikit.h`) exportait physiquement
`UIKitSurfaceAccessKt.withUIKitView(_:block:completionHandler:)` — symbole porteur de
`UIView*`. L'audit façade de la phase 3 la clôt PAR SUPPRESSION : `@HiddenFromObjC`
(`kotlin.native`, opt-in `ExperimentalObjCRefinement` — le mécanisme stable qu'INTEROP §2
attendait pour appliquer l'annotation aux actuals) masque la fonction des exports
Obj-C/Swift. La preuve phase 2 ne passe PAS par cet export (vérifié par grep des sources
Swift driver et consumer avant édition : zéro appel — les suites pilotent la sonde Kotlin
`KadreUikitSurfaceDriver.leaseProbe`/`closedLeaseProbe`), donc aucun gate n'est affaibli.
INTEROP §7 est amendé dans le même changement : la règle Kotlin-only vaut pour la façade
ET pour l'en-tête framework.

Inventaire d'audit de l'en-tête régénéré (grep du 2026-10-11, cible
`iosSimulatorArm64/debugFramework` ; tvOS identique) — 7197 lignes, 483 `@interface`,
101 `@protocol` (contre 7228 / 484 / 101 AVANT l'audit) :

- **Contrat — la surface promise §5, visible et supportée** : `KadreIos.attach`
  (`KadreUikitKadreIos`, `attachWindowScene:window:surfaceView:applicationFactory:policy:completionHandler:`),
  la factory (`@protocol KadreUikitKadreApplicationFactory` — référence passée par le
  module applicatif Kotlin), la session (`@protocol KadreUikitKadreSession`), les types de
  policy (`KadrePolicy` et builders) et le value model session/état/outcome + failures
  (famille `KadreResult`/`KadreFailure`, exportée avec foundation pour que la session
  rendue par `attach` soit utilisable depuis Swift).
- **Scaffolding driver — visible VOLONTAIREMENT** : les sondes de conduite
  (`KadreUikitProbe`, `KadreUikitObservation`, `KadreUikitWindowMembership`,
  `KadreUikitSurfaceDriver` et ses résultats `KadreUikitLeaseProbe` /
  `KadreUikitClosedLeaseProbe` / `KadreUikitSurfaceEventCapture`), la sonde de
  reconnaissance du touch (`touchRecognitionProbe` — résultat portant
  `recognizerAttached`/`recognizerOverridesTouchesBeganImp`/`recognizerObjCClassName`/
  `cancelsTouchesInView`/`recognizerEnabled`/`totalGestureRecognizers`, phase 5) et la
  sonde des recognizers de geste (`gestureRecognitionProbe` — `recognizerClassNames` +
  `cancelsTouchesInViewAll`, phase 5) et la sonde texte de la phase 6
  (`KadreUikitTextInputProbe` — les faits copiés du receiver découvert — et
  `KadreUikitTextInputEventCapture`, plus les conduites
  `openTextInputProbe`/`updateSurroundingTextProbe`/`captureTextInputEvents`, les stimuli
  programmatiques `receiver*` et les compteurs texte `textInstallCount`/`textTeardownCount`/
  `textObservationCount`/`textFocusGainedCount`/`textFocusLostCount`), plus les compteurs d'entrée du driver
  (`KadreUikitSurfaceDriver` : touch `touchInstallCount`/`touchTeardownCount`/
  `touchEventCount`/`touchStimulusCount`/`touchAnnouncementCount` ; clavier
  `keyboardInstallCount`/`keyboardTeardownCount`/`keyboardConnectCount`/
  `keyboardEventCount`/`keyboardStimulusCount`/`keyboardAnnouncementCount` ; gestes et
  survol `gestureInstallCount`/`gestureTeardownCount`/`hoverInstallCount`/
  `hoverTeardownCount`/`gestureStimulusCount`/`gestureAnnouncementCount`) —
  instrumentation des suites XCTest (phases 0-6), hors de la surface promise §5, opt-in
  `@KadrePlatformApi`, documentées comme telles dans leur KDoc. Les masquer casserait les
  gates phase 2/3/5 (les tests Swift les pilotent directement) — elles restent donc
  listées ici, pas cachées.
- **Masqué (`@HiddenFromObjC`)** : `withUIKitView` — grep d'audit `withUIKitView` = **0**
  sur les deux en-têtes simulateur post-édition (la classe porteuse
  `KadreUikitUIKitSurfaceAccessKt` a disparu de l'en-tête) : aucun symbole surface-access
  n'apparaît plus dans l'en-tête, la règle INTEROP §7 y est désormais vraie.
- **Détail généré toléré** : tout le reste — l'export transitif de foundation (les 483
  interfaces/101 protocoles incluent les types window/surface/input/capture non promis) —
  statut INTEROP §1 (« détail généré ») et §2 (« les symboles non promis peuvent
  physiquement apparaître dans un bytecode/header généré tant que le toolchain ne permet
  pas de les cacher ») ; les tests interop les ignorent, aucun sample/document ne les
  utilise.

La distribution XCFramework statique (Task 1) porte le même en-tête audité : ses quatre
slices (`ios-arm64`, `ios-arm64-simulator`, `tvos-arm64`, `tvos-arm64-simulator`, listés
dans `AvailableLibraries` du `Info.plist`) sont rassemblées depuis le même binaire
`KadreUikit` — le grep = 0 vaut pour les slices du xcframework réassemblé.
