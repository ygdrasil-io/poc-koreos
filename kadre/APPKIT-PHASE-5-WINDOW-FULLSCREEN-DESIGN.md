# AppKit Phase 5 — Fullscreen natif terminal et corrélé

## But et frontière

Cette sous-tranche rend mutable `WindowUpdate.fullscreen` pour le fullscreen
natif géré par macOS. Elle ne simule ni un redimensionnement sans bordure, ni
une prise de contrôle exclusive d'écran. C'est une transition visuelle
asynchrone, mais `Window.apply` attend son résultat terminal : Kadre ne rend ni
`Applied`, ni une failure, avant `DidEnter`, `DidExit`, un callback d'échec ou
une erreur synchrone du selector.

Le périmètre public est fermé :

| Valeur | Implémentation AppKit | Statut |
| --- | --- | --- |
| `FullscreenMode.Windowed` | sortie du fullscreen natif | supportée |
| `FullscreenMode.Borderless` | `NSWindow.toggleFullScreen` | supportée |
| `FullscreenMode.Exclusive` | display et mode exclusifs | update : `Unsupported(UpdateWindow)` ; création : `Rejected(Unsupported(RequestWindow))` |

`Borderless` désigne le fullscreen géré par l'espace macOS. Kadre ne modifie
pas `collectionBehavior`, ne choisit pas d'écran, ne personnalise pas
l'animation et ne touche pas aux presentation options process-wide.
`Exclusive` reste hors scope jusqu'à la phase 9, qui fournira l'inventaire de
displays, les modes et la restauration nécessaires.

`WindowSpec(fullscreen = Borderless)` échoue immédiatement avec
`KadreFailure.InvalidRequest("fullscreen")`, avant la création du peer. Une
requête de fenêtre ne porte pas de `WindowOperationId`, alors que la transition
fullscreen doit être corrélée à sa fin native ; exposer une fenêtre
provisoirement `Windowed` ou annoncer `Borderless` avant `DidEnter` serait un
faux état. L'appelant ouvre donc une fenêtre `Windowed`, puis demande
`WindowUpdate(fullscreen = Set(Borderless))`.

`WindowSpec(fullscreen = Exclusive(...))` produit au contraire une
`WindowRequest` dont `await()` termine par
`WindowRequestOutcome.Rejected(KadreFailure.Unsupported(RequestWindow))` ;
aucun peer n'est créé. `Exclusive` est une valeur bien formée mais hors
capability, tandis que `Borderless` initial est invalide car la création ne
dispose pas de l'identifiant de transition nécessaire.

Les propriétés non encore activées — position, transparence, blur, icône,
content protection, attention et move/resize système — restent `Unsupported`.
Cette tranche ne modifie pas la fermeture, le chrome, la géométrie ou le level
hors de leur coordination explicitement définie ci-dessous.

## Précondition KFFI et disponibilité

Le snapshot KFFI publié expose les bindings générés nécessaires, avec leurs
annotations de disponibilité macOS 10.7 :

- `NSWindow.toggleFullScreen(sender)` ;
- `NSWindowWillEnterFullScreenNotification`,
  `NSWindowDidEnterFullScreenNotification`,
  `NSWindowWillExitFullScreenNotification` et
  `NSWindowDidExitFullScreenNotification` ;
- les callbacks optionnels `windowDidFailToEnterFullScreen:` et
  `windowDidFailToExitFullScreen:` de `NSWindowDelegate`.

Le port utilise uniquement ces bindings et les observations Objective-C managed
de KFFI. Kadre ne construit ni selector Panama, ni downcall, ni wrapper FFI
local. Une lacune de génération remonte à Kextract, puis à une régénération et
une publication KFFI, avant toute modification Kadre.

`AppKitFullscreenAvailability` est une dépendance interne injectable. Elle
compare la version système à `10.7.0`, sans charger de symbole Objective-C ni
installer d'observer avant ce guard. La comparaison est lexicographique sur les
composantes numériques non négatives : `11.0` et `26.0` sont donc postérieures
à `10.7.0`. Les tests injectent au moins `10.6.8`, `10.7.0` et une version
majeure contemporaine ; ils n'emploient pas la version de la machine de CI.

## Modèle de transition

`WindowState.fullscreen` décrit uniquement le dernier état complet observé :

- avant `DidEnter`, il reste `Windowed` ;
- après `DidEnter`, il devient `Borderless` ;
- avant `DidExit`, il reste `Borderless` ;
- après `DidExit`, il devient `Windowed`.

Le runtime maintient, hors API publique, une unique barrière de fullscreen par
fenêtre. Une barrière locale contient l'`WindowOperationId`, la cible et une
phase `BeforeSelector` ou `AfterSelector`; une barrière externe ne porte aucun
operation ID Kadre. Elle interdit un second `toggleFullScreen` jusqu'à une
notification terminale, un échec natif ou le teardown.

Une mutation fullscreen locale conserve sa propre `PendingWindowUpdate` jusqu'à
sa terminaison. Il n'existe ni déduplication ni partage d'`Accepted` entre deux
callers : les updates ultérieurs restent dans la file sérialisée ordinaire.
Après la transition, un même mode devient donc un no-op `Applied`, et le mode
opposé déclenche une nouvelle transition. Cette règle rend l'annulation d'un
caller indépendante de tout autre caller.

Une barrière externe naît à `WillEnter` ou `WillExit`. Après validation de
`expectedRevision`, une demande locale qui modifie `fullscreen` pendant cette
barrière échoue immédiatement avec
`KadreFailure.TemporarilyUnavailable(retryable = true)` : aucun operation ID
local ne peut honnêtement être associé à la transition externe. Les mutations
sans fullscreen restent dans la file sérialisée et ne franchissent pas le port
avant la libération de la barrière, afin que leur résultat ne précède jamais le
snapshot fullscreen effectif.

`PropertyChange.Clear` sur `fullscreen` échoue avec
`KadreFailure.InvalidRequest("fullscreen")` avant admission native.
`Set(Exclusive(...))` est rejeté avec `KadreFailure.Unsupported(UpdateWindow)`.
Un `Set` vers le dernier état effectif est un no-op : pas de selector, pas
de révision, pas d'événement.

Un update qui modifie `fullscreen` et un autre champ persistant est rejeté
avec `KadreFailure.InvalidRequest("fullscreen")`. Cette frontière interdit
une `PartiallyApplied` ambiguë pendant une transition native : l'appelant
sépare la transition fullscreen des mutations synchrones.

`expectedRevision` est vérifiée avant la détection d'une barrière, puis une
seconde fois au moment où la commande quitte la file. Une révision incorrecte
retourne donc toujours `StaleRevision`, y compris si une transition externe est
en cours ou si une transition locale précédente vient de modifier l'état.

## Commit, completion et échecs

Le port arme l'observation du peer, vérifie une dernière fois l'annulation,
puis appelle `toggleFullScreen` sur le thread AppKit. Ce selector est la
frontière de commit :

- une cancellation avant le selector retire la commande et ne produit aucune
  transition ;
- après le selector, elle détache seulement le waiter ; Kadre ne tente aucun
  toggle compensatoire ;
- `Window.apply` reste suspendue après le selector ;
- `DidEnter` ou `DidExit` publie l'état et l'événement ; si la restauration du
  level réussit, le caller encore actif reçoit
  `WindowUpdateOutcome.Applied(operationId, state)` ;
- `WindowUpdateOutcome.Accepted` n'est jamais le résultat d'un update
  fullscreen AppKit.

La notification `Did` appariée est l'autorité du mode fullscreen. Le runtime
construit l'état terminal à partir de l'état Kadre courant en ne remplaçant que
`fullscreen`, puis le publie avant son unique
`WindowEvent.PropertiesChanged({ Fullscreen })`. Il ne fait pas dépendre la
terminaison de cette transition d'un readback complet d'autres propriétés.

Le `WindowLevel` persistant reste inchangé dans cet état. Après la publication
fullscreen, le port tente de le réappliquer ; une erreur de restauration est
signalée à l'appel local encore actif par
`KadreFailure.PlatformFailure(AppKit, "fullscreen", "level-restore-failed")`.
L'état et l'événement fullscreen déjà publiés restent l'autorité de ce qui est
effectivement arrivé. Si le caller s'est détaché, ou si la transition était
externe, la même erreur est rapportée en diagnostic de session. Dans tous les
cas, un `finally` libère la barrière et relance la file : une erreur de
restauration ou de publication ne peut jamais laisser la fenêtre bloquée en
transition.

`windowDidFailToEnterFullScreen:` et `windowDidFailToExitFullScreen:` terminent
une barrière locale par, respectivement,
`KadreResult.Failure(PlatformFailure(AppKit, "fullscreen", "enter-failed"))`
ou `"exit-failed"`. Ils conservent le dernier état effectif et ne publient pas
de faux `PropertiesChanged`. Pour une barrière externe, ils libèrent seulement
la barrière et reportent la failure en diagnostic, puisqu'aucun appel Kadre ne
peut être corrélé à cette tentative.

Une exception synchrone de `toggleFullScreen` termine l'appel local avec
`PlatformFailure(AppKit, "fullscreen", "selector-threw")`; une exception de
traitement après un callback terminal utilise le code `"completion-failed"`.
Les deux chemins libèrent la barrière dans le même `finally`. Un teardown ou
une fermeture native révoque les observers, libère la barrière et termine une
commande locale encore pendante par `Closed(Window)` ; un callback ultérieur,
d'un peer fermé ou d'une autre fenêtre est ignoré.

## État, événements et stimuli externes

Après `DidEnter` ou `DidExit`, le runtime publie d'abord le
`WindowState` effectif avec une unique révision supplémentaire, puis un
`WindowEvent.PropertiesChanged` où
`changed == { WindowProperty.Fullscreen }`. L'événement porte
l'`operationId` de la barrière lorsqu'il vient de Kadre, et `null`
lorsqu'un menu, un raccourci macOS ou un autre stimulus natif a initié la
transition.

`WillEnter` et `WillExit` ne changent pas l'état public. Ils arment ou
confirment seulement la barrière. Le type de notification `Did` externe établit
le mode effectif ; Kadre ne le déduit jamais d'une intention locale. Une failure
externe ne laisse aucune barrière active.

Une mutation de level n'est jamais injectée au milieu de l'animation. AppKit
peut employer un z-order transitoire privé ; ce niveau n'entre jamais dans
`WindowState` et ne transforme ni `Floating` ni `Modal` en faux `Normal`.

## Capability publique

Après activation publique, lorsque `AppKitFullscreenAvailability` est vraie,
AppKit expose :

```text
WindowCapabilities.fullscreen =
  Capability.Supported({ Borderless }, FeatureAvailability.Available)
```

Sur une version antérieure à macOS 10.7.0, la même capability conserve son
domaine public mais devient :

```text
Capability.Supported(
  { Borderless },
  FeatureAvailability.Unavailable(
    PlatformFailure(AppKit, "fullscreen", "os-version-unavailable"),
  ),
)
```

Kadre n'installe alors ni observer fullscreen ni delegate callback et ne tente
jamais le selector. L'appel fonctionnel est revalidé à l'admission et retourne
la même `PlatformFailure`. `Exclusive` n'est jamais annoncé ; les autres
capabilities actives ne changent pas.

## Preuves et contrats

`WIN-005` réserve le contrat O2 de la machine à états runtime fullscreen.
`APK-010` réserve le contrat O3 de l'activation AppKit. Ils restent
`planned` jusqu'à la dernière PR de la stack ; cette carte ne modifie ni
capability active, ni evidence, ni gate.

`WIN-005` couvrira :

- validation de `Clear`, `Exclusive`, création non corrélable et no-op ;
- suspension jusqu'à `Did`, succès `Applied` et échec natif terminal corrélé ;
- révision stale avant barrière puis au dispatch, et sérialisation sans
  déduplication de caller ;
- cancellation avant/après `toggleFullScreen`, échec synchrone et fermeture ;
- état avant `PropertiesChanged`, corrélation et policy discrète.

Ses sentinelles couvrent l'absence de toggle avant commit, l'absence de double
toggle, le stale callback, la libération de barrière dans chaque chemin
terminal, l'isolation inter-fenêtres et le non-contournement de policy.

`APK-010` couvrira :

- binding KFFI, observations `Will`/`Did` et callbacks d'échec ;
- entrée, sortie, notification terminale et level conservé ;
- activation publique, seuil 10.7 injectable, indisponibilité de version et
  `Exclusive` refusé ;
- cancellation, teardown, callbacks tardifs et transitions externes ;
- chemin de policy et isolation entre peers.

Les tests déterministes prouveront la machine à états, la barrière et les
callbacks tardifs. Les tests macOS réels prouveront selector, notifications,
completion, conservation du level et guard de disponibilité. Un harness manuel
relèvera l'animation visible et le comportement de Space plein écran : il ne
bloque pas la CI et ne remplace aucune preuve O2/O3.

## Découpage de la stack

1. Cette PR ajoute ce design, réserve `WIN-005` et `APK-010`, et actualise
   la roadmap sans activer de capability.
2. Une PR fille introduit la barrière runtime, l'attente terminale, les
   callbacks abstraits et les preuves O2, sans exposition AppKit publique.
3. Une PR fille raccorde peer, port déterministe et KFFI aux notifications
   fullscreen et aux callbacks d'échec, avec preuves macOS privées.
4. La dernière PR active capability, contrats et evidence CI ; elle ajoute le
   harness manuel non bloquant d'observation interactive.
