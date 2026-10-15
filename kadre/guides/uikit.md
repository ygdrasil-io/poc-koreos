# Guide — Attacher une session sur UIKit/tvOS

**Vérité par feature :** [`kadre/capabilities/uikit.md`](../capabilities/uikit.md) —
lignes BCK-013 (attach, session, lifecycle), BCK-014 (multi-scène iPadOS), WIN-009
(fenêtre primaire et identité), BCK-015 (observations dynamiques). Ce guide décrit le
chemin d'intégration livré, pas des promesses au-delà des lignes du registre.

## Le point d'entrée

`KadreIos.attach` — l'attach normatif (`DESIGN.md` §15.2, livré phase 1,
`kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreIos.kt:154`) :

```kotlin
public object KadreIos {
    public fun attach(
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
        windowSceneProvider: UIKitWindowSceneProvider? = null,
    ): KadreResult<KadreSession>
}
```

Le chemin SwiftUI équivalent (`KadreIosHost.attach`, qui jette `KadreError` au lieu de
rendre un `KadreResult`) est décrit par [`swiftui.md`](swiftui.md) ; le paramètre
`windowSceneProvider`, par [`multi-scene.md`](multi-scene.md).

**Contrat de thread : l'appel DOIT être sur le thread principal.** `KadreIos.attach` le
vérifie à l'entrée (`NSThread.isMainThread()`, sinon `IllegalStateException` —
KadreIos.kt:189) : l'installation `NSNotificationCenter`, le snapshot initial de la vue
et le commit de la fenêtre primaire sont synchrones sur ce fil. Le scope de session est
posé sur `Dispatchers.Main`.

## Ce que le host choisit — et ce que Kadre ne touchera jamais

Le host choisit explicitement la `UIWindow` primaire et la `UIView` de contenu. Kadre ne
détruit jamais la hiérarchie native : le teardown ne relâche que les ressources Kadre
(observers, réservation de scène, runtime). Aucune sélection implicite dans
`UIWindowScene.windows`, aucune key window choisie pour vous, aucune session globale.

L'attach valide les arguments AVANT toute installation (KadreIos.kt:195-206) et rend
`KadreResult.Failure` sans session si :

- `window.windowScene` est nul, ou n'est pas `isEqual` à la `windowScene` fournie →
  `InvalidRequest("window")` ;
- la scène est sans `session` ou encore `UISceneActivationStateUnattached` →
  `InvalidRequest("window")` — c'est l'état normal d'une scène iPadOS 27 au
  `willConnectTo` : attachez au `sceneDidBecomeActive` (ou laissez le
  `KadreHostViewController` armer son unique réessai, voir [`swiftui.md`](swiftui.md)) ;
- `surfaceView.window` est nul ou n'est pas `isEqual` à `window` →
  `InvalidRequest("surfaceView")`.

Note d'identité : `===` ne traverse PAS le pont Kotlin/Obj-C. Toute comparaison
d'identité passe par `NSObject.isEqual` — c'est la règle que l'attach implémente et que
le registre §« Règle d'identité du pont Obj-C » documente.

La scène est ensuite réservée AVANT toute installation : un second attach sur la même
`UIWindowScene` répond `AlreadyInUse(Host)` et laisse la première session intacte
(KadreIos.kt:210 ; sentinelle `uikit-scene-single-owner`). Une scène = une session, à
vie de la scène.

## La session rendue

En succès, `attach` rend `KadreResult.Success<KadreSession>`. La surface
(`kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/application/Application.kt:50`) :

```kotlin
public interface KadreSession : AutoCloseable {
    public val id: SessionId
    public val state: StateFlow<SessionState>   // Starting / Running / Stopping / Terminated(outcome)
    override fun close()
    public fun requestStop()
    public suspend fun awaitTermination(): SessionOutcome
}
```

- `close()` demande l'arrêt host (`Stopped(HostRequested)`), no-op sur session déjà
  terminée ; `requestStop()` la variante motivée explicite. Les cinq `SessionStopReason`
  (`ApplicationRequested`, `HostRequested`, `ApplicationCancelled`, `ParentCancelled`,
  `HostDetached`) sont dans `Application.kt:72`.
- `awaitTermination()` suspend jusqu'à l'outcome terminal, exactement une fois.

## Possession de la surface

La `surfaceView` fournie DEVIENT la surface primaire — même `SurfaceId`, identité prouvée
par `isEqual` à travers le pont (ligne WIN-009 du registre) et métriques initiales égales
à la lecture Swift des `bounds`. Les conséquences :

- La fenêtre primaire est imposée par le host : aucune autre fenêtre/surface Kadre n'est
  livrée. Les `UIWindow` overlay de votre scène restent intégralement host-owned — Kadre
  ne les voit pas.
- Une fenêtre enfant d'une demande de fenêtre (`requestWindow` avec provider) n'est
  JAMAIS une `Window` Kadre : `Window.apply`, `attention` et `close` restent hors
  périmètre (voir [`multi-scene.md`](multi-scene.md)).
- Le bail natif `withUIKitView` (Kotlin-only strict, `@HiddenFromObjC` — aucun symbole
  dans l'en-tête du framework, audit mécanisé `scripts/audit-uikit-facade-header.sh`)
  prête la `UIView` le temps d'un block : valide au retour uniquement, jamais retenue ;
  après close, toute demande répond `Unsupported(PlatformSurfaceAccess)`
  (`UIKitSurfaceAccess.kt`, INTEROP §7).

## Cycle de vie

Le lifecycle est conduit par les notifications de scène RÉELLES
(`UIKitLifecycleSource` → réducteur normatif `UIKitLifecycleReducer` → `LifecycleState`
`visibility`/`activation`), pas par un timer ni par votre code. L'état initial se lit
sur la scène AVANT l'installation des observers. En pratique :

- foreground/background et activation/inactivation de la scène se reflètent dans
  `session.state` et dans les observations de surface (un changement entre deux
  déclencheurs SDK est observé au déclencheur suivant — BCK-015) ;
- la déconnexion de la scène (`UIScene.didDisconnectNotification`) termine LA session de
  CETTE scène — les autres sessions du process (multi-scènes iPadOS) continuent ;
- les callbacks natifs post-terminaison sont neutralisés en profondeur (tokens retirés,
  routage tardif inerte) : rien ne se publie après le terminal ;
- l'ordre de terminaison est non négociable : observers relâchés puis réservation rendue
  (KadreIos.kt:379-396).

Le clamp honnête : sur Simulator, `requestSceneSessionDestruction` est un no-op
silencieux (note « environnement simulateur » du registre) — la preuve de destruction
réelle par FrontBoard est un charter manuel sur matériel. Un retrait TEMPORAIRE de la
vue de sa fenêtre ne poste pas `didDisconnect` et ne ferme rien.

## Ce qui n'est pas dans ce guide

- Les gestes, le touch, le clavier, la session texte, le drop, la capture, les displays
  et la pression mémoire : leurs états exacts par famille se lisent dans
  [`capabilities/uikit.md`](../capabilities/uikit.md) §2 (BCK-022 à BCK-028) et la
  lecture des disponibilités dans [`conditional-capabilities.md`](conditional-capabilities.md).
- tvOS a les mêmes contrats d'attach, avec une topologie mono-scène —
  [`tvos.md`](tvos.md).
