# Guide — Migrer depuis le kadre-old (UIKit)

**Sources de vérité :** [`kadre/API-MIGRATION.md`](../API-MIGRATION.md) (le registre de
migration, decisions `keep/move` / `replace` / `internalize` / `remove`) et
[`kadre/MIGRATION-AUDIT.md`](../MIGRATION-AUDIT.md) (la revue humaine nominative — 13
entrées `kadre-uikit`) ; l'état livré se lit dans
[`capabilities/uikit.md`](../capabilities/uikit.md). Ce guide traduit les lignes UIKit de
ces registres en chemins de migration concrets. Registre : des décisions, pas des
promesses.

## Le compte audité

La baseline `497a2c2e…` audite **13 propriétaires sémantiques top-level** pour
`kadre-uikit` iOS, fonctions UIKit comprises (MIGRATION-AUDIT §1/§2). Les 281 noms de la
baseline ont tous un match nominal dans API-MIGRATION.md ; aucune règle résiduelle ne
couvre un symbole UIKit. La revue §4 « UIKit » résume la décision : delegate, registry,
Metal view et capture concrète internalisés ou remplacés par attach/manager ; status bar,
home indicator et orientations supprimés (ownership host controller) ;
`startKadreApplication` remplacé par l'attachement de scène.

## Le mapping, ligne par ligne

| kadre-old | Décision | Cible kadre-new (livrée) |
|---|---|---|
| `startKadreApplication` | replace | **`KadreIos.attach(windowScene, window, surfaceView, applicationFactory, policy)`** — l'attach de scène normatif (API-MIGRATION.md:136 ; résidu audit #21). Plus de lancement global : le host pose la scène, la fenêtre et la vue ([`uikit.md`](uikit.md)) |
| `KadreAppDelegate`, `KadreRegistry` UIKit | replace/internalize | `KadreIos.attach` + le bridge Swift minimal (`KadreHost` : `KadreIosHost`, `KadreHostViewController`, `KadreSessionHandle` — [`swiftui.md`](swiftui.md)). Plus de registry process-global : une session par `UIWindowScene`, le host garde la factory |
| `KadreMetalView` | internalize | AUCUN renderer Metal public. Kadre ne rend rien ; la `UIView` du host reste la vôtre (le bail Kotlin-only `withUIKitView` est la seule fenêtre sur la vue, hors exports Swift) |
| `StatusBarStyle`, `ValidOrientations`, `setPreferredStatusBarStyle`, `setPrefersHomeIndicatorHidden`, `setPrefersStatusBarHidden` | remove | Le chrome et les orientations appartiennent au host controller, pas à une fenêtre Kadre (résidus audit #18-20). Configurez-les côté UIViewController/UIWindow — Kadre n'en a plus aucune API |
| `UiKitWindowAttributes` | replace | `WindowSpec` immuable + options plateforme typées (API-MIGRATION.md:56) — et sur UIKit la « fenêtre » primaire est imposée par le host, pas construite par des attributs |
| `contentRect` (Android/UIKit) | replace | `HostSurface.state` : métriques atomiques et conversions explicites (API-MIGRATION.md:133). Les observations vivantes (`bounds`, `displayScale`, `safeAreaInsets`) sont publiées au déclencheur SDK (BCK-015) |
| Les vues UIKit (`UIView` passée au host) | — (règle) | Une vue UIKit matche toujours la ligne `HostSurface`, JAMAIS la ligne `Window` (API-MIGRATION.md:70) : les implémentations concrètes deviennent internes |
| `ScreenCapturer` | replace | `CaptureManager` attaché à la session ; entrée unique `KadreScope.capture` (API-MIGRATION.md:113, 121). Sur UIKit : cible `Surface` LIVRÉE (pompe de snapshots de la vue du host), `HostChoice` availability-gated mais injoignable via tout target public, `Source`/énumération absences exactes (BCK-028) |
| `UIKitScreenCapturer`, `UIKitCaptureSession` | internalize | Derrière `CaptureManager` (API-MIGRATION.md:119) — aucune classe backend concrète publique |
| `GamepadController`, `Gamepad`, `GamepadState`, `GamepadEvent`, `Axis`, `Button`, `PowerInfo` | replace | `DeviceManager` : snapshots immuables, routing `Routed`/`Suspended`, événements estampillés (API-MIGRATION.md:104). Sur UIKit : miroir GameController au moment de lecture, seuls les profils `extendedGamepad` deviennent des `Gamepad`, raw input refusé (BCK-027) |
| `WindowEvent` input / `DeviceEvent` | replace | `SurfaceInput.events` + `DeviceManagerState` (API-MIGRATION.md:76). Touch par recognizer additif (`cancelsTouchesInView == false`), gestes = ensemble exact **double-tap/pan/pinch/rotation** (iOS) et **double-tap/pan** (tvOS), clavier physique par `GCKeyboard.coalescedKeyboard` — livraisons réelles = charters manuels (BCK-022/023) |
| `ImePurpose`, `ImeCapability`, `WindowEvent.Ime*` | replace | `TextInputSession` / `TextInputState` / `TextInputEvent`, offsets UTF-16, `TextDocumentRevision` anti-stale (API-MIGRATION.md:84). Session texte livrée : receiver natif caché, open exclusif, write-back conduit, `StaleRevision` typé (BCK-024) |

La table ci-dessus cite les lignes UIKit de `API-MIGRATION.md` ; les familles communes
(keyboard/pointer value model, IDs opaques, `KadreResult`…) suivent les règles générales
du même registre et ne sont pas répétées ici.

## Ce qui ne reviendra pas

À lire comme des décisions fermées, pas des retards :

- **aucune session globale, aucun singleton Kadre** — ni overload `UIApplication`, ni
  sélection de key window (`BACKEND-CAPABILITIES.md` §6.2) ;
- **aucun chrome public** — status bar, home indicator, orientations sont du host ;
- **aucun renderer Metal public** ;
- **une fenêtre enfant n'est jamais une `Window` Kadre** — `Window.apply`/`attention`/
  `close` hors périmètre UIKit ([`multi-scene.md`](multi-scene.md)) ;
- **le bail natif est Kotlin-only** — `withUIKitView` masqué des exports Obj-C/Swift
  (`@HiddenFromObjC`, règle INTEROP §7 valant pour la façade ET l'en-tête) ;
- **la façade Swift reste une façade host** — attach, arrêt, observation de session et
  outcome terminal uniquement ; ni managers, ni flows, ni implémentation Swift de
  `KadreApplication` v1 (INTEROP §5 : la factory est pass-through).

## Chemin de migration type

1. Remplacez le lancement `startKadreApplication` par un `UIWindowSceneDelegate` qui
   crée fenêtre + vue de contenu et appelle `KadreIos.attach` (ou, côté Swift,
   `KadreHostViewController`) — [`uikit.md`](uikit.md) / [`swiftui.md`](swiftui.md).
2. Réécrivez la config de fenêtre en `WindowSpec`/`HostSurface` — et déplacez le chrome
   (status bar, orientations) dans votre controller hôte.
3. Branchez l'input sur `SurfaceInput`/`DeviceManager` (les snapshots et flows communs),
   la capture sur `KadreScope.capture`, le texte sur `openTextInput` — en lisant les
   disponibilités par famille dans [`capabilities/uikit.md`](../capabilities/uikit.md)
   §2 et [`conditional-capabilities.md`](conditional-capabilities.md).
4. tvOS : les mêmes noms, la topologie mono-scène — [`tvos.md`](tvos.md).
5. Consommez la distribution conformément à [`installation.md`](installation.md).
