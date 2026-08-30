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
| `FullscreenMode.Exclusive` | display et mode exclusifs | update : `PartiallyApplied(Fullscreen = Unsupported(UpdateWindow))` ; création : `Rejected(Unsupported(RequestWindow))` |

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
fenêtre, exclusivement mutée sur l'executor AppKit. Une barrière locale contient
l'`WindowOperationId`, la cible et l'une des phases atomiques suivantes :

| Phase | Sens | Règle sur une notification `Will` |
| --- | --- | --- |
| `PreparedLocal` | commande admise, selector pas encore invoqué | un `Will` devient externe : il termine la commande par `TemporarilyUnavailable` sans selector |
| `InvokingSelector` | appel de `toggleFullScreen` actif, callbacks réentrants possibles | un `Will` de la cible est enregistré mais aucun terminal ne peut encore drainer la file |
| `AwaitingLocal` | le selector est revenu ; ses callbacks enregistrés peuvent être consommés | seul `Did`/`DidFail` correspondant peut terminer l'opération |
| `External` | AppKit a commencé une transition sans opération Kadre | aucune opération locale fullscreen n'est corrélable |

`InvokingSelector` conserve `selectorCallActive = true`. Un `Did` ou `DidFail`
réentrant est mémorisé mais ne publie ni état ni événement, ne libère pas la
barrière et ne draine aucune commande avant le retour du selector. Au retour,
le runtime passe à `AwaitingLocal`, consomme un terminal mémorisé exactement une
fois, puis seulement alors conclut et draine. Si le selector lève après un
`Will` ou terminal local mémorisé, cette observation établit la frontière de
commit et gagne ; `selector-threw` n'est produit qu'en son absence.

Chaque peer conserve aussi une tombstone terminale `{ cible, kind }`, effacée
par le prochain `Will` ou le prochain selector local. Elle supprime les
callbacks terminaux dupliqués : AppKit ne fournit pas d'identifiant permettant
de distinguer un doublon d'un nouvel échec externe identique sans `Will`, et la
spécification choisit alors le diagnostic au plus une fois.

La matrice de callbacks est fermée. `cible` désigne `Borderless` pour enter et
`Windowed` pour exit ; un `Did` égal au state courant est toujours un doublon :

| Phase | `Will` | `Did` égal au state | `Did` différent du state | `DidFail` |
| --- | --- | --- | --- | --- |
| aucune / tombstone | arme `External` et efface la tombstone | ignoré | completion externe, état/événement avec `operationId = null` | diagnostic une fois, puis tombstone |
| `PreparedLocal` | arme `External`, retourne `TemporarilyUnavailable`, sans selector | ignoré | même règle externe, puis échec local `TemporarilyUnavailable` | diagnostic une fois ; la commande locale reste préparée |
| `InvokingSelector` | cible locale : mémorise ; cible opposée : mémorise un conflit externe | ignoré | cible locale : mémorise le succès ; cible opposée : mémorise le conflit externe | cible locale : mémorise l'échec ; cible opposée : diagnostic une fois |
| `AwaitingLocal` | cible locale : idempotent ; cible opposée : conflit externe | ignoré | cible locale : succès local ; cible opposée : completion externe et `unexpected-transition` local | cible locale : `enter-failed`/`exit-failed` ; cible opposée : diagnostic une fois |
| `External` | reste externe, dernière cible observée | ignoré | completion externe et libération de barrière | cible attendue : diagnostic et libération ; cible opposée : diagnostic une fois |

Un conflit externe après invocation locale termine la commande par
`PlatformFailure(AppKit, "fullscreen", "unexpected-transition")`, mais publie
l'observation externe avec `operationId = null`. La règle interdit un second
toggle et garantit que chaque callback ne possède qu'un seul chemin terminal.

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
`Set(Exclusive(...))` est un rejet de champ : il retourne
`WindowUpdateOutcome.PartiallyApplied` avec
`RejectedWindowField(Fullscreen, Unsupported(UpdateWindow))`, sans selector,
révision ni événement.
Un `Set` vers le dernier état effectif est un no-op : pas de selector, pas
de révision, pas d'événement.

Un update qui modifie `fullscreen` et un autre champ persistant est rejeté
avec `KadreFailure.InvalidRequest("fullscreen")`. Cette frontière interdit
une `PartiallyApplied` ambiguë pendant une transition native : l'appelant
sépare la transition fullscreen des mutations synchrones.

La précédence d'admission est totale : fenêtre fermée, forme structurelle du
payload (`Clear`, update mixte ou candidat impossible), `expectedRevision`,
domaine de valeur, disponibilité de feature, barrière, puis canonisation/no-op.
Le domaine de valeur traite `Exclusive` comme rejet de champ avant toute
disponibilité : `Exclusive + barrière externe` et `Exclusive + OS indisponible`
retournent donc `PartiallyApplied`. Pour `Borderless` ou `Windowed`, une feature
indisponible retourne `os-version-unavailable` avant une barrière ; ainsi
`OS indisponible + barrière externe` retourne cette `PlatformFailure`.
`stale + Clear` reste `InvalidRequest`, alors que `stale + Exclusive`,
`stale + barrière externe` et `stale + OS indisponible` retournent
`StaleRevision`. `expectedRevision` est vérifiée une seconde fois au moment où
la commande quitte la file : une transition locale précédente peut donc la
rendre stale avant le selector.

## Commit, completion et échecs

Le port arme l'observation du peer, vérifie une dernière fois l'annulation, puis
passe atomiquement de `PreparedLocal` à `InvokingSelector` avant d'appeler
`toggleFullScreen` sur le thread AppKit. Cette invocation, ou un `Will`
réentrant apparié, est la frontière de commit :

- une cancellation avant `InvokingSelector` retire uniquement cette commande ;
- dès `InvokingSelector`, la cancellation détache seulement le waiter ; Kadre
  ne demande aucun retrait au port et ne tente aucun toggle compensatoire ;
- `Window.apply` reste suspendue jusqu'à une terminaison native ;
- `WindowUpdateOutcome.Accepted` n'est jamais le résultat d'un update
  fullscreen AppKit.

Chaque peer conserve un `desiredLevel` interne. Il est initialisé par le
`WindowSpec` effectif et ne change qu'après le readback autoritaire d'un
`Set(level)` Kadre réussi. Une observation native, une failure committée ou un
readback divergent ne l'écrase jamais ; `WindowState.level` reste, lui, toujours
la valeur native effective.

Une notification `Did` locale appariée établit le mode fullscreen effectif.
Avant toute publication, le port réapplique `desiredLevel` puis lit son niveau
effectif. Le snapshot terminal remplace donc `fullscreen` et, si nécessaire,
`level` par leurs valeurs natives observées :

- si le level relu est la valeur persistante demandée, le runtime publie un
  unique état, puis un `PropertiesChanged({ Fullscreen })`, et complète par
  `WindowUpdateOutcome.Applied(operationId, state)` ;
- si le level est lisible mais diffère, le runtime publie un unique état et un
  événement `PropertiesChanged({ Fullscreen, Level })`, puis complète l'appel
  par `KadreResult.Failure(PlatformFailure(AppKit, "fullscreen",
  "level-restore-failed"))` ; le snapshot publié reste donc effectif ;
- si le level ne peut pas être relu ou représenté, Kadre n'expose jamais un
  `WindowState.Open` inventé. Il terminalise la fenêtre par le chemin natif de
  fermeture, puis complète par `PlatformFailure(AppKit, "fullscreen",
  "level-readback-failed")`.

Les deux dernières branches conservent l'autorité de `Window.state` et de
`Window.events` malgré la failure de l'appel : un commit natif n'est jamais
présenté comme rollbacké. Une erreur de publication suit aussi la fermeture
terminale plutôt que de laisser une fenêtre ouverte avec un snapshot faux.

Le runtime introduit pour cela deux stimuli **internes** à
`WindowUpdateCommandStimulus`, sans changement de l'API publique :

- `Failed(operationId, KadreFailure)`, pour une failure terminale sans snapshot
  committé ;
- `CommittedFailure(operationId, effectiveState, publicationOperationId,
  KadreFailure)`, pour publier état puis événement avant de compléter
  `KadreResult.Failure`. `publicationOperationId` vaut l'ID local, ou `null`
  lorsqu'une observation externe a terminé l'opération locale en conflit.

`CommittedFailure` détache l'opération, publie dans l'ordre état puis événement
avec `publicationOperationId`, complète le waiter encore actif avec la failure
exacte et ne draine la commande suivante qu'ensuite. Un `Failed` ou
`CommittedFailure` post-commit dont le waiter s'est détaché est rapporté
exactement une fois au diagnostic de session, avec sa `KadreFailure` exacte.

`windowDidFailToEnterFullScreen:` et `windowDidFailToExitFullScreen:` émettent
respectivement `Failed(PlatformFailure(AppKit, "fullscreen", "enter-failed"))`
et `Failed(..., "exit-failed")`. Ils conservent le dernier état effectif et ne
publient pas de faux `PropertiesChanged`. Pour une barrière externe, ils
libèrent seulement la barrière et reportent la failure en diagnostic, puisqu'il
n'existe aucun appel Kadre à corréler.

Une exception synchrone de `toggleFullScreen` sans `Will` apparié émet
`Failed(PlatformFailure(AppKit, "fullscreen", "selector-threw"))`. Une erreur
de traitement après un callback terminal utilise le code `"completion-failed"`
et ferme la fenêtre si aucun snapshot effectif ne peut être publié. Tous les
chemins terminaux libèrent la barrière dans le même `finally`, puis relancent la
file. Un teardown ou une fermeture native révoque les observers, libère la
barrière et termine une commande locale encore pendante par `Closed(Window)` ;
un callback ultérieur, d'un peer fermé ou d'une autre fenêtre est ignoré.

## État, événements et stimuli externes

Après `DidEnter` ou `DidExit`, le runtime publie d'abord un `WindowState`
effectif avec une unique révision supplémentaire, puis un
`WindowEvent.PropertiesChanged`. `changed` contient toujours
`WindowProperty.Fullscreen` et contient aussi `WindowProperty.Level` si le
readback a observé un level différent. L'événement porte l'`operationId` de la
barrière lorsqu'il vient de Kadre, et `null` lorsqu'un menu, un raccourci macOS
ou un autre stimulus natif a initié la transition.

`WillEnter` et `WillExit` ne changent pas l'état public. Ils arment ou
confirment seulement la barrière. Le type de notification `Did` externe établit
le mode effectif ; Kadre ne le déduit jamais d'une intention locale. Une failure
externe ne laisse aucune barrière active.

La même réconciliation de level s'applique à un `Did` externe. Si le niveau
relisible diffère de `desiredLevel`, l'état et l'événement externe
(`operationId = null`) portent aussi `WindowProperty.Level`, puis la failure exacte est
rapportée une fois au diagnostic. Un level illisible terminalise la fenêtre ;
aucun `WindowState.Open` ne conserve une valeur de niveau supposée.

Une mutation de level n'est jamais injectée au milieu de l'animation. AppKit
peut employer un z-order transitoire privé ; ce niveau n'entre jamais dans
`WindowState` et ne transforme ni `Floating` ni `Modal` en faux `Normal`.

Kadre ne modifie aucune presentation option process-wide. Au teardown, il ferme
la `NSWindow` et laisse AppKit restaurer l'espace fullscreen qu'il possède ; il
n'appelle jamais un toggle compensatoire. La CI prouve l'absence de barrière,
d'observer ou de commande résiduelle ; le harness manuel constate séparément le
retour visible de l'espace macOS.

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

- validation de `Clear`, `Exclusive` à l'update et à la création, update mixte
  et no-op ;
- matrice de précédence `InvalidRequest`/`StaleRevision`/capability/barrière ;
- `PreparedLocal`, `InvokingSelector`, `AwaitingLocal` et `External`, dont
  `Will` externe pré-selector, `Will` réentrant, direction opposée et absence
  de double toggle ;
- `Will + Did + return` et `Will + Did + throw` réentrants, sans publication ni
  drainage avant le retour du selector ;
- matrice `Did`/`DidFail` de chaque phase, callback opposé externe sans
  `operationId`, terminal sans `Will`, tombstone et diagnostic au plus une fois ;
- intersections `Exclusive + barrière`, `Exclusive + OS indisponible` et
  `OS indisponible + barrière externe` ;
- cancellation avant/après commit, failure exacte et diagnostic unique après
  détachement du waiter ;
- `CommittedFailure`, état avant événement, level restauré, level différent
  lisible, level illisible, fermeture terminale, puis `desiredLevel` préservé
  pour la transition locale et externe suivante ;
- corrélation et policy discrète.

Ses sentinelles couvrent l'absence de toggle avant commit, l'absence de double
toggle, le stale callback, la libération de barrière dans chaque chemin
terminal, l'état effectif après failure committée, l'isolation inter-fenêtres
et le non-contournement de policy.

`APK-010` couvrira :

- binding KFFI, observations `Will`/`Did` et callbacks d'échec ;
- entrée, sortie, callback réentrant/externe et terminal, puis level effectif
  relu dans les deux origines ;
- activation publique, seuil 10.7 injectable, indisponibilité de version et
  `Exclusive` rejeté dans le bon canal ;
- cancellation, teardown, callbacks tardifs/dupliqués et transitions externes ;
- absence de presentation option process-wide, de peer ou d'observer résiduel ;
- chemin de policy et isolation entre peers.

Les tests déterministes prouveront la machine à états, les stimuli internes et
les callbacks tardifs. Les tests macOS réels prouveront selector, notifications,
completion, readback effectif du level et guard de disponibilité. Un harness
manuel relèvera l'animation visible et le comportement de Space plein écran :
il ne bloque pas la CI et ne remplace aucune preuve O2/O3.

## Découpage de la stack

1. Cette PR ajoute ce design, réserve `WIN-005` et `APK-010`, et actualise
   la roadmap sans activer de capability.
2. Une PR fille introduit la barrière runtime, les stimuli internes `Failed` /
   `CommittedFailure`, la tombstone, `desiredLevel`, l'attente terminale, les
   callbacks abstraits et les preuves O2, sans exposition AppKit publique.
3. Une PR fille raccorde peer, port déterministe et KFFI aux notifications
   fullscreen, aux callbacks d'échec et au readback de level, avec preuves
   macOS privées.
4. La dernière PR active capability, contrats et evidence CI ; elle ajoute le
   harness manuel non bloquant d'observation interactive.
