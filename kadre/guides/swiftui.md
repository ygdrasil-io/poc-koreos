# Guide — SwiftUI : le wrapper `UIViewControllerRepresentable`

**Vérité par feature :** [`kadre/capabilities/uikit.md`](../capabilities/uikit.md) —
lignes façade Swift `KadreHost` et `KadreHostViewController` (phase 3, consumer gate des
deux familles) ; contrat `INTEROP-EXPORTS.md` §5 et §9. Registre : des décisions, pas
des promesses.

## La décision d'abord : le wrapper est APP-OWNED

Kadre ne livre PAS de vue SwiftUI. La ligne SwiftUI d'INTEROP §5 l.139 : le
`UIViewControllerRepresentable` « reste une dizaine de lignes possédées par l'application
SwiftUI autour de ce controller » — il ne vit JAMAIS dans le module `KadreHost`. Ce que
Kadre livre, c'est le `UIViewController` hôte ; le representable qui l'emporte dans une
hiérarchie SwiftUI est votre code (dans le consumer de ce dépôt, il s'appelle
`KadreHostView` — `kadre/consumers/swift/consumer-app/apps/Shared/ConsumerRepresentable.swift`,
le seul exemple d'intégration travaillée).

## `KadreHostViewController` — le controller livré

`KadreHostViewController` (`kadre/platform/uikit/swift/Sources/KadreHost/KadreHostViewController.swift:42`)
attache EXACTEMENT UNE FOIS, au premier moment valide :

- le hook est `didMoveToWindow` de SA propre view (la view EST la `surfaceView` — elle ne
  la remplace pas, ne pose aucune contrainte, aucun frame, aucune subview : zéro layout
  Kadre, promesse §5). Le moment est valide quand la fenêtre qui la porte a une
  `UIWindowScene` ;
- si le premier essai répond `InvalidRequest("window")` — la scène était encore
  `Unattached` (iPadOS 27 au `willConnectTo`) — le controller arme UN réessai à
  l'activation de la scène, one-shot. C'est le seul réessai : tout autre refus
  (`AlreadyInUse(Host)`…) est DÉFINITIF, et après le succès, après un échec définitif ou
  après `close()`, aucun re-add à une hiérarchie ne ré-attache jamais ;
- `kadreSession` (optionnel, `public private(set)`) porte le `KadreSessionHandle` dès
  l'attach réussi — un handle clos reste lisible (son `state` dit terminated) ;
- la session se ferme par `sceneDidDisconnect` de LA scène attachée, par `deinit`, ou par
  votre `close()` explicite (idempotent). Un retrait temporaire de la hiérarchie ne ferme
  rien.

Le handle rendu expose `id`, `state`, `observeState` (`@MainActor`, émet immédiatement le
snapshot courant, sérialisé, muet après `close()`), `requestStop()`, `close()` et
`awaitTermination() async throws` (cancellation de la `Task` = `CancellationError`, la
session continue) — contrat §5, implémentation `KadreSessionHandle.swift`.

## Le wrapper — et son `updateUIViewController` CONTRACTUELLEMENT VIDE

La forme attendue (INTEROP §9 « wrapper `UIViewControllerRepresentable`
possédant l'host controller, sans layout Kadre »), reprise du consumer —
`ConsumerRepresentable.swift`, l'instrumentation de test du store en moins :

```swift
struct KadreHostView: UIViewControllerRepresentable {
    let applicationFactory: any KadreApplicationFactory
    let policy: KadrePolicyProfile

    func makeUIViewController(context: Context) -> KadreHostViewController {
        KadreHostViewController(applicationFactory: applicationFactory, policy: policy)
    }

    func updateUIViewController(_ uiViewController: KadreHostViewController, context: Context) {
        // CONTRACTUELLEMENT VIDE : ni close, ni re-attach, ni layout — rien.
    }
}
```

Pourquoi ce vide est un contrat et pas un manque :

- `makeUIViewController` CRÉE et POSSÈDE le controller ; SwiftUI le retient dans sa
  hiérarchie. L'attach lui-même est controller-driven (le `didMoveToWindow` décrit
  ci-dessus), pas SwiftUI-driven.
- Une réévaluation du body passe par `updateUIViewController` — qui ne fait RIEN : elle
  ne re-attache pas et ne recrée pas. Le MÊME controller survit aux re-renders ; la
  machine exactement-une-fois du controller (`waiting` → `retryArmed` → `settled`) est le
  second garde-fou. La preuve consumer (`uikit-swiftui-consumer-attaches-once`) force des
  réévaluations réelles du body (état observable + 1) et asserte l'identité `===` du
  controller ET un compteur `makeUIViewController == 1`.
- Ne mettez AUCUN effet dans `updateUIViewController` : ni `close()`, ni configuration
  tardive, ni layout — tout effet de ce type est hors contrat.

Règle liée : tout ce qui entre dans le wrapper doit rester stable. L'exemple consumer ne
fait jamais varier `applicationFactory` ni `policy` après le premier make — la factory
est capturée à l'init et consommée par l'unique attach.

## La factory : une référence Kotlin, jamais une implémentation Swift

`applicationFactory` est le type de référence exporté par le framework Kotlin. Swift
reçoit et repasse une instance produite par votre module applicatif Kotlin ; l'initialisation
ou la conformance Swift directe n'est pas supportée v1 (INTEROP §5). Le module applicatif
lie la distribution (voir [`installation.md`](installation.md)), exporte sa factory au
header Obj-C, et le compilateur Swift n'accepte le passage que si les deux fragments
liés exposent le même type Obj-C — l'identité de types par préfixe `KadreUikit` + lien
statique (exemple : `ConsumerApplicationFactory` dans
`kadre/consumers/swift/consumer-kmp/src/commonMain/kotlin/org/graphiks/kadre/consumer/ConsumerApplicationFactory.kt`).

## Le gate : prouver les deux chemins sans les mélanger

Le consumer prouve le chemin SwiftUI par un gate de LANCEMENT —
`KADRE_CONSUMER_SWIFTUI` (variable d'environnement, transmise par `xcodebuild test` avec
le préfixe `TEST_RUNNER_`) ou l'argument `-KADRE_CONSUMER_SWIFTUI`
(`ConsumerSwiftUIScene.swift:31-34`) :

- gate actif : la fenêtre primaire devient un `UIHostingController` racine hébergeant
  `KadreHostView` — l'attach est controller-driven et le chemin UIKit NE DOIT JAMAIS
  doubler (une seule session par scène ; un second attach répondrait
  `AlreadyInUse(Host)` de façon DÉFINITIVE) ;
- gate inactif : le chemin UIKit ordinaire s'attache au `sceneDidBecomeActive`.

Le gate se lit UNE fois au lancement (`ProcessInfo` ne change pas en cours de process).
Côté gate, la preuve d'intégration runnable est `./gradlew :kadre:validateSwiftConsumer`
(quatre runs xcodebuild — standard et launch gated SwiftUI, iOS et tvOS) — voir
[`installation.md`](installation.md).

## Ce qui n'est pas promis

Ni managers, ni flows/frames, ni `Flow`/`Continuation`/`Throwable` dans la façade (règle
INTEROP §9, épinglée par les tests consumer) ; pas de module `integration:swiftui`
livré ; pas de layout Kadre dans la view du controller. Les features (gestes —
double-tap, pan, pinch, rotation sur iOS ; double-tap, pan sur tvOS —, text input,
drop…) se lisent dans [`capabilities/uikit.md`](../capabilities/uikit.md) §2.
