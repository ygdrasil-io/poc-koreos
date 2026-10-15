# Guide — Multi-fenêtre : `requestWindow`, le provider app-owned, le refus tvOS

**Vérité par feature :** [`kadre/capabilities/uikit.md`](../capabilities/uikit.md) —
lignes BCK-020 (refus sans provider) et BCK-021 (corrélation multi-scènes iPadOS) ;
narratif `BACKEND-CAPABILITIES.md` §6.2 phase 4. Registre : des décisions, pas des
promesses.

## La demande et ses issues

`requestWindow` vit sur le `WindowManager` de la session
(`kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/window/Window.kt:24`) :

```kotlin
public suspend fun requestWindow(spec: WindowSpec = WindowSpec()): KadreResult<WindowRequest>
```

Le `WindowRequest` rendu porte `id`, `state` (`Pending` / `Terminated(outcome)`,
`Window.kt:49-60`), `cancel()` et `await()`. Les outcomes (`WindowRequestOutcome`) :

- `OpenedInNewSession(childSessionId)` — l'issue livrée sur UIKit avec provider ;
- `Rejected(failure)` — dont `Unsupported(RequestWindow)`, l'issue livrée SANS provider ;
- `Cancelled`, `RequesterDetached` — l'abandon app-side et la mort du requérant.

(`OpenedHere` existe dans le modèle commun ; sur UIKit, aucune demande n'ouvre jamais une
fenêtre DANS la session du requérant — le requérant reste unique-fenêtre.)

## Sans provider : le refus AVANT toute réservation

C'est le DÉFAUT (`windowSceneProvider = null`) — le chemin tvOS et celui de toute app
mono-fenêtre iOS. La capability `requestWindow` est annoncée
`Unsupported(RequestWindow)` et toute demande rend une requête DÉJÀ terminale
`Rejected(Unsupported(RequestWindow))` avant toute réservation : aucun provider invoqué,
aucune scène activée, aucune corrélation, rien de consommé — une seconde demande est
refusée pareil (BCK-020, sentinelle `uikit-window-request-tvos-refusal-faithful`).

Ce refus est une vérité des DEUX familles : tvOS est mono-scène PAR DESIGN
(`DESIGN.md` §15.2) et n'a jamais de provider à passer ; sur iOS, c'est le contrat d'une
app qui ne déclare pas le multi-fenêtre. Voir [`tvos.md`](tvos.md).

## Avec provider : l'app active la scène, Kadre corrèle

Le provider est **app-owned** — l'app, seule à connaître sa topologie de scènes
(`UIKitWindowSceneProvider.kt:27`) :

```kotlin
public fun interface UIKitWindowSceneProvider {
    public fun open(requestId: WindowRequestId, spec: WindowSpec, resolver: UIKitWindowSceneRequestResolver)
}
```

Séquence livrée (BCK-021) :

1. Votre app passe le provider à l'attach primaire
   (`KadreIos.attach(..., windowSceneProvider = ...)`). La capability `requestWindow`
   devient `OpenedInNewSession`.
2. À chaque demande admise, `open` est appelé sur le thread principal avec l'identité de
   la demande ET le `resolver` ÉMIS À L'ADMISSION — aucun registre, aucune seconde voie.
3. Votre app active la nouvelle scène (`UIApplication.requestSceneSessionActivation`) et
   corrèle la scène au `requestId` **par identité — jamais par index** (l'identité voyage
   dans l'`userActivity`/les options d'activation).
4. À la connexion de cette scène, votre app crée la `UIWindow` et la `UIView` de contenu
   puis résout : `resolver.resolve(windowScene:window:surfaceView:)` — ou déclare
   l'échec : `resolver.abandon(reason)` (scène qui ne connectera jamais). Les appels
   tardifs sur une demande déjà terminale sont des no-op.
5. La session enfant s'attache PAR LE CHEMIN ORDINAIRE (sa propre scène, sa propre
   surface, son propre lifecycle) et la demande termine `OpenedInNewSession(childId)`.

La fonction `open` ne rend RIEN : l'ouverture d'une scène est réellement asynchrone
(FrontBoard), sans timeout synthétique — la demande reste `Pending` jusqu'à résolution,
abandon app-side ou annulation. Un **throw** de `open` est capturé et devient
`Rejected(PlatformFailure(UIKit, "UIKitWindowSceneProvider", "callback-exception"))`,
jamais une failure de l'appel `requestWindow`.

`cancel()` avant connexion répond `CancelledBeforeCommit` : la demande est terminale
pour toujours et une résolution tardive (si FrontBoard l'honore) est un no-op — AUCUN
enfant (scénario `uikit-window-request-cancel-before-connect`).

## Ce qu'une fenêtre enfant n'est PAS

Décision fermée, à lire deux fois : **une fenêtre enfant n'est jamais une `Window`
Kadre.** La session enfant est une session complète et indépendante — `Window.apply`,
`Window.attention` et `Window.close` sur une fenêtre enfant, et toute opération qui
présupposerait une `Window`, restent hors périmètre. Trois propriétés prouvées :

- le requérant reste unique-fenêtre (l'enfant n'entre JAMAIS dans sa liste de fenêtres) ;
- l'arrêt et le `close()` du requérant détachent l'enfant du destin du requérant —
  l'enfant déjà résolu SURVIT (attaché, app vivante, surface intacte ; « detach » =
  séparé du sort du requérant, jamais « enfant terminé ») ;
- une `UIWindow` overlay de la scène requérante survit intacte — visible, toujours key,
  jamais une fenêtre Kadre.

Les demandes concurrentes se corrèlent deux à deux (requestId ↔ scène ↔ session enfant
tous distincts — sentinelle `uikit-window-request-correlation-crossed`) ; une seconde
demande repart du même requérant une fois la première résolue (slot libéré).

## Portée par famille et par appareil

- **tvOS** : mono-scène par design — le refus BCK-020 PORTE le contrat. Ne passez pas de
  provider sur tvOS.
- **iOS/iPadOS** : la preuve multi-scènes court sur iPad — l'idiome téléphone refuse
  plusieurs scènes (« The current device does not support multiple scenes »). Votre
  topologie de scènes reste déclarée par VOTRE `Info.plist`
  (`UIApplicationSceneManifest`, `UIApplicationSupportsMultipleScenes`) : Kadre n'écrit
  jamais dans votre manifeste.
- La mémoire process (pression) et l'inventaire controllers sont des faits PROCESS
  partagés par toutes les scènes attachées — un seul observer, fan-out vers tous les
  hosts (registre §2, ligne BCK-027).
