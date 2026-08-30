# AppKit Phase 5 — Fullscreen natif corrélé

## But et frontière

Cette sous-tranche rend mutable `WindowUpdate.fullscreen` pour le fullscreen
natif géré par macOS. Elle ne simule ni un redimensionnement sans bordure, ni
une prise de contrôle exclusive d'écran. C'est une transition visuelle
asynchrone : Kadre ne la présente pas comme appliquée avant la notification
native qui établit l'état effectif.

Le périmètre public est fermé :

| Valeur | Implémentation AppKit | Statut |
| --- | --- | --- |
| `FullscreenMode.Windowed` | sortie du fullscreen natif | supportée |
| `FullscreenMode.Borderless` | `NSWindow.toggleFullScreen` | supportée |
| `FullscreenMode.Exclusive` | display et mode exclusifs | `Unsupported(UpdateWindow)` |

`Borderless` désigne le fullscreen géré par l'espace macOS. Kadre ne modifie
pas `collectionBehavior`, ne choisit pas d'écran, ne personnalise pas
l'animation et ne touche pas aux presentation options process-wide.
`Exclusive` reste hors scope jusqu'à la phase 9, qui fournira l'inventaire de
displays, les modes et la restauration nécessaires.

`WindowSpec(fullscreen = Borderless)` échoue avec
`KadreFailure.InvalidRequest("fullscreen")` avant la création du peer. Une
requête de fenêtre ne porte pas de `WindowOperationId`, alors que la
transition fullscreen doit être corrélée à sa fin native ; exposer une fenêtre
provisoirement `Windowed` ou annoncer `Borderless` avant `DidEnter`
serait un faux état. L'appelant ouvre donc une fenêtre `Windowed`, puis
demande `WindowUpdate(fullscreen = Set(Borderless))`.

Les propriétés non encore activées — position, transparence, blur, icône,
content protection, attention et move/resize système — restent `Unsupported`.
Cette tranche ne modifie pas la fermeture, le chrome, la géométrie ou le level
hors de leur coordination explicitement définie ci-dessous.

## Précondition KFFI

Le snapshot KFFI publié expose les bindings générés nécessaires, avec leurs
annotations de disponibilité macOS :

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

## Modèle de transition

`WindowState.fullscreen` décrit uniquement le dernier état complet observé :

- avant `DidEnter`, il reste `Windowed` ;
- après `DidEnter`, il devient `Borderless` ;
- avant `DidExit`, il reste `Borderless` ;
- après `DidExit`, il devient `Windowed`.

Le runtime maintient, hors API publique, une transition par fenêtre : son
`WindowOperationId`, sa cible et le fait que l'invocation native a franchi
la frontière de commit. La barrière reste présente après
`Accepted(operationId)`. Elle interdit une seconde invocation de
`toggleFullScreen` avant `DidEnter`, `DidExit` ou le callback d'échec.

Une demande répétée vers la même cible retourne le même
`Accepted(operationId)`. Une demande vers la cible opposée retourne
`KadreFailure.TemporarilyUnavailable(retryable = true)` : elle n'inverse pas
implicitement une animation AppKit en cours. Les autres mutations attendent
aussi cette barrière afin que leur résultat ne soit jamais publié avant le
snapshot fullscreen effectif.

`PropertyChange.Clear` sur `fullscreen` échoue avec
`KadreFailure.InvalidRequest("fullscreen")` avant admission native.
`Set(Exclusive(...))` est rejeté avec `KadreFailure.Unsupported(UpdateWindow)`.
Un `Set` vers le dernier état effectif est un no-op : pas de selector, pas
de révision, pas d'événement.

Un update qui modifie `fullscreen` et un autre champ persistant est rejeté
avec `KadreFailure.InvalidRequest("fullscreen")`. Cette frontière interdit
une `PartiallyApplied` impossible à corréler pendant une transition native :
l'appelant sépare la transition fullscreen des mutations synchrones.

## Commit, completion et échecs

Le port arme l'observation du peer, vérifie une dernière fois l'annulation,
puis appelle `toggleFullScreen` sur le thread AppKit. Ce selector est la
frontière de commit :

- une cancellation avant le selector retire la commande et ne produit aucune
  transition ;
- après le selector, elle détache seulement le waiter ; Kadre ne tente aucun
  toggle compensatoire ;
- le caller reçoit `WindowUpdateOutcome.Accepted(operationId)` dès que le
  selector a été admis ;
- `DidEnter` ou `DidExit` relit l'état effectif, lève la barrière et conclut
  la transition par un état et un événement corrélés.

`windowDidFailToEnterFullScreen:` et `windowDidFailToExitFullScreen:`
lèvent la barrière, signalent respectivement
`PlatformFailure(AppKit, "fullscreen", "enter-failed")` ou `"exit-failed"`
au rapporteur de session, conservent le dernier état effectif et ne publient
aucun faux `PropertiesChanged`. Le résultat `Accepted` n'est pas réécrit :
sa completion observable est l'événement corrélé ou ce diagnostic de failure.

Une fermeture native ou un teardown révoque les observers, libère la barrière
et empêche toute publication fullscreen tardive. Un callback d'un peer fermé
ou d'une autre fenêtre est ignoré.

## État, événements et stimuli externes

Après `DidEnter` ou `DidExit`, le runtime publie d'abord le
`WindowState` effectif avec une unique révision supplémentaire, puis un
`WindowEvent.PropertiesChanged` où
`changed == { WindowProperty.Fullscreen }`. L'événement porte
l'`operationId` de la barrière lorsqu'il vient de Kadre, et `null`
lorsqu'un menu, un raccourci macOS ou un autre stimulus natif a initié la
transition.

`WillEnter` et `WillExit` ne changent pas l'état public. Ils confirment
seulement qu'un peer est en transition et permettent de refuser honnêtement les
demandes concurrentes. Les notifications `Did` externes relisent toujours
l'état ; elles ne déduisent pas le mode depuis une intention locale.

Le `WindowLevel` persistant est indépendant du fullscreen. AppKit peut
employer un z-order transitoire pendant l'animation, mais ce niveau privé
n'entre jamais dans `WindowState`. Après chaque `DidEnter` ou `DidExit`,
le port réapplique le niveau Kadre effectif, puis relit le snapshot complet.
Ainsi le fullscreen ne transforme ni `Floating` ni `Modal` en faux
`Normal`, et une mutation de level n'est jamais injectée au milieu de
l'animation.

## Capability publique

Après activation publique et seulement quand l'API macOS est disponible :

```text
WindowCapabilities.fullscreen = Supported({ Borderless })
```

`Exclusive` n'est pas annoncé. Une API indisponible sur l'OS courant garde
la capability `Unavailable` et Kadre ne tente pas le selector. Les autres
capabilities actives ne changent pas.

## Preuves et contrats

`WIN-005` réserve le contrat O2 de la machine à états runtime fullscreen.
`APK-010` réserve le contrat O3 de l'activation AppKit. Ils restent
`planned` jusqu'à la dernière PR de la stack ; cette carte ne modifie ni
capability active, ni evidence, ni gate.

`WIN-005` couvrira :

- validation de `Clear`, `Exclusive`, création non corrélable et no-op ;
- admission d'`Accepted`, barrière et déduplication de la même cible ;
- cancellation avant/après `toggleFullScreen`, échec natif et fermeture ;
- état avant `PropertiesChanged`, corrélation et policy discrète.

Ses sentinelles couvriront l'absence de toggle avant commit, l'absence de
double toggle, le stale callback, la barrière de transition, l'isolation
inter-fenêtres et le non-contournement de policy.

`APK-010` couvrira :

- binding KFFI, observations `Will`/`Did` et callbacks d'échec ;
- entrée, sortie, readback effectif et level conservé ;
- activation publique, indisponibilité de version et `Exclusive` refusé ;
- cancellation, teardown et transitions externes ;
- chemin de policy et isolation entre peers.

Les tests déterministes prouveront la machine à états, la barrière et les
callbacks tardifs. Les tests macOS réels prouveront selector, notifications,
readback et conservation du level. Un harness manuel relèvera l'animation
visible et le comportement de Space plein écran : il ne bloque pas la CI et ne
remplace aucune preuve O2/O3.

## Découpage de la stack

1. Cette PR ajoute ce design, réserve `WIN-005` et `APK-010`, et actualise
   la roadmap sans activer de capability.
2. Une PR fille introduit la barrière runtime, `Accepted`, les callbacks
   abstraits et les preuves O2, sans exposition AppKit publique.
3. Une PR fille raccorde peer, port déterministe et KFFI aux notifications
   fullscreen et aux callbacks d'échec, avec preuves macOS privées.
4. La dernière PR active capability, contrats et evidence CI ; elle ajoute le
   harness manuel non bloquant d'observation interactive.
