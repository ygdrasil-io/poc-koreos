# AppKit Phase 5 — Géométrie et contraintes de fenêtre

## But et frontière de la tranche

Cette première tranche de la phase 5 définit l'activation des mutations AppKit
qui ont une preuve native directe, sans étendre la surface publique : `contentSize`,
`minimumSize`, `maximumSize` et `resizable`.

Elle couvre aussi les valeurs correspondantes de `WindowSpec` lors de la
création. Une fenêtre ne publie donc jamais comme effectif un minimum ou un
maximum qu'AppKit n'a pas reçu avant sa présentation.

`outerPosition` est délibérément hors tranche. L'API publique l'exprime en
coordonnées physiques et impose que les bounds externes soient observables.
AppKit manipule des points dans un repère multi-écran qui ne peut pas être
figé honnêtement avant l'inventaire Display de la phase 9. La capability reste
donc `Unsupported`, `WindowState.outerBounds` reste `null`, et aucun fallback
vers les coordonnées AppKit n'est introduit.

Restent également `Unsupported` : titre, fullscreen, décorations, boutons
système, level, transparence, blur, icône, content protection et attention.
Ils font partie de tranches ultérieures de la phase 5, avec leurs propres
frontières d'autorité et preuves.

## Précondition KFFI

Le snapshot KFFI publié fournit déjà les bindings générés nécessaires :
`NSWindow.setContentSize`, `setContentMinSize`, `setContentMaxSize`, les
getters min/max, ainsi que `styleMask` et `setStyleMask`. AppKit ne déclare pas
de selector `contentSize` : le snapshot effectif de taille de contenu est donc
dérivé, dans le port natif privé, par `contentRectForFrameRect(frame()).size`,
à partir de deux APIs `NSWindow` elles aussi générées. Ce `frame` ne devient
jamais un `outerBounds` public. Kadre appelle uniquement ces APIs typées
générées ; cette tranche ne contient ni `ObjCRuntime.msgSend` local, ni binding
écrit à la main, ni changement Kextract/KFFI.

Si une résolution fraîche de la dépendance révélait une divergence de binding,
la correction suit obligatoirement la chaîne Kextract, puis la régénération et
la publication KFFI, avant toute consommation Kadre. Il n'existe pas de
workaround FFI local.

## Autorités et trajet

```text
Window.apply
    -> admission et file sérialisée par RuntimeWindow
    -> WindowCommandPort
    -> AppKitWindowCommandQueue session-local
    -> AppKitWindowPeer / AppKitNativeWindowPort
    -> NSWindow via binding KFFI généré
    -> snapshot natif effectif corrélé
    -> WindowState atomique, puis Window.events
```

Le runtime possède l'état public, les révisions, les `WindowOperationId`, les
stamps et la policy de livraison. Le peer AppKit ne conserve que les ressources
natives, les valeurs par défaut natives nécessaires à `Clear`, et des snapshots
temporaires de commande. Il ne maintient jamais un second `WindowState` public.

Le port reste asynchrone : il admet une commande et reçoit sa completion par un
stimulus corrélé. Une callback AppKit ne bloque jamais le runtime ni un
collector public.

## Admission, sérialisation et cancellation

Chaque `RuntimeWindow` possède une file d'opérations. Un `WindowOperationId`
est attribué à l'admission, puis les commandes sont exécutées dans cet ordre
pour cette fenêtre. Une autre fenêtre conserve sa propre file ; l'AppKit
command queue reste l'autorité de sérialisation des appels natifs.

La vérification de `expectedRevision` et le calcul de l'état cible ont lieu
lorsqu'une commande atteint la tête de sa file. Une opération qui devient stale
pendant l'attente retourne `KadreFailure.StaleRevision` sans appel natif.

Avant le point de commit natif, l'annulation retire la commande lorsque le
backend le permet. Après le premier setter natif, elle ne rollback jamais : la
completion corrélée continue de mettre à jour `Window.state` et `Window.events`
même si le coroutine appelant a été annulé. `close()` ferme l'admission et fait
échouer les commandes non committées par `Closed(Window)` ; une commande déjà
committée conserve son résultat observable conformément à
`OPERATION-CONTRACTS.md`.

## Sémantique de `WindowUpdate`

Avant toute application partielle, le runtime applique tous les `Set` et
`Clear` à une copie de l'état courant puis vérifie la relation :

```text
minimumSize <= contentSize <= maximumSize
```

par dimension lorsque les bornes existent. Une contradiction retourne
`InvalidRequest("sizeConstraints")` comme failure externe et ne modifie aucun
champ, même si une partie des champs de l'update serait par ailleurs
`Unsupported`.

Après cette validation, les quatre champs activés deviennent une commande
interne. Les champs hors tranche sont listés dans `PartiallyApplied.rejected`
avec `Unsupported(UpdateWindow)` et ne sont jamais transmis à AppKit.

Le backend retourne un résultat explicite pour chaque champ admis et les valeurs
effectives lues après la mutation. Le runtime publie uniquement ces valeurs,
pas une demande supposée réalisée. Un échec avant tout setter est une failure
externe ; après une mutation partielle, les champs déjà observés sont conservés
et les champs refusés figurent dans `PartiallyApplied` avec leur failure.

Un no-op valide garde le contrat public existant : nouvel `WindowOperationId`,
`Applied` avec l'état courant, sans appel natif, événement ni incrément de
`WindowRevision`. Cette tranche n'emploie pas `Accepted` : les setters AppKit
visés sont synchrones. `Accepted` reste réservé aux transitions visuellement
asynchrones, notamment fullscreen, qui ne sont pas activées ici.

## Application AppKit

Les limites min/max et `resizable` du `WindowSpec` sont configurés avant la
première présentation. Pour un update, le peer applique les contraintes en
trois étapes : relâcher les bornes qui empêcheraient la taille cible, appliquer
la taille de contenu, puis installer les bornes finales. Toutes les valeurs
intermédiaires restent cohérentes avec la cible déjà validée.

`Clear` de `minimumSize` ou `maximumSize` restaure la valeur native par défaut
capturée pour ce peer lors de sa création. Cette valeur est une représentation
native interne ; le résultat public de `Clear` est `null`.

`resizable` ne modifie que le bit `NSWindowStyleMaskResizable` du style mask
effectif. Les bits qui décrivent les décorations et boutons non encore gérés
par cette tranche sont préservés. Après chaque commande, le peer relit la
taille effective et le style mask avant de compléter l'opération.

## Observations externes, état et événements

`NSWindowDidResizeNotification` alimente deux observations indépendantes : les
métriques de `Window.surface`, déjà prises en charge par la phase 3, et le
`contentSize` de `WindowState`.

Pendant une commande Kadre, le peer absorbe les notifications réentrantes
émises par ses setters et retourne un seul snapshot final attaché à
l'`WindowOperationId`. Il n'existe ainsi ni doublon, ni événement externe
fictif. Un resize utilisateur ou une mutation native extérieure est observé
comme un changement de géométrie avec `operationId = null`.

Toute publication met à jour le `StateFlow` atomiquement avant les événements.
Une même révision publie au plus un `GeometryChanged`, puis au plus un
`PropertiesChanged`; le premier couvre taille/min/max et le second `Resizable`.
Les deux événements possèdent des stamps distincts, ordonnés, et la révision
du snapshot commun. Les deux flows public `Window.events` et
`HostSurface.events` n'ont pas de promesse d'ordre total entre eux.

La tranche met aussi en œuvre pour `Window.events` la `WindowDeliveryPolicy`
existante : transitions discrètes FIFO, géométrie continue coalescée ou bornée
selon le profil, et barrières discrètes qui scellent les agrégats. Un scheduler
interne dédié reprend ces sémantiques sans refactorer le scheduler Surface déjà
validé par la phase 3.

## Capabilities

Dans la PR finale qui active `APK-006`, les capabilities suivantes deviennent,
sur une fenêtre AppKit ouverte,
`Supported(..., Available)` :

- `WindowCapabilities.contentSize`, avec `LogicalSizeRange(null, null, null)` ;
- `WindowCapabilities.minimumSize`, avec le même domaine ;
- `WindowCapabilities.maximumSize`, avec le même domaine ;
- `WindowCapabilities.resizable`.

Avant cette activation, elles restent `Unsupported`. Ensuite, cette
disponibilité décrit la possibilité structurelle de demander la
mutation. Les contraintes instantanées effectives restent dans `WindowState`
et sont revalidées pour chaque update. Toutes les autres capabilities de
mutation restent explicitement `Unsupported`.

## Preuves et contrat

`WIN-001` est réservé comme contrat O2 `planned` pour le runtime portable et
`APK-006` comme contrat O3 `planned` pour la session publique AppKit. Les deux
doivent être activés ensemble : aucun mapping d'evidence ni gate CI n'est
ajouté avant que leurs preuves existent.

`WIN-001` réserve les scénarios suivants :

- `runtime-window-geometry-validation` ;
- `runtime-window-geometry-serialization` ;
- `runtime-window-geometry-cancellation-close` ;
- `runtime-window-geometry-event-policy`.

Ses sentinelles sont :

- `runtime-window-geometry-invalid-precommit` ;
- `runtime-window-geometry-stale-dispatch` ;
- `runtime-window-geometry-event-before-state` ;
- `runtime-window-geometry-operation-correlation` ;
- `runtime-window-geometry-post-close-command` ;
- `runtime-window-geometry-policy-bypass`.

`APK-006` réserve les scénarios suivants :

- `appkit-window-geometry-public-activation` ;
- `appkit-window-geometry-initial-constraints` ;
- `appkit-window-geometry-native-update` ;
- `appkit-window-geometry-external-resize` ;
- `appkit-window-geometry-policy`.

Ses sentinelles sont :

- `appkit-window-geometry-invalid-precommit` ;
- `appkit-window-geometry-cancellation-boundary` ;
- `appkit-window-geometry-operation-correlation` ;
- `appkit-window-geometry-style-mask-preservation` ;
- `appkit-window-geometry-clear-constraint` ;
- `appkit-window-geometry-post-close` ;
- `appkit-window-geometry-cross-window` ;
- `appkit-window-geometry-policy-bypass`.

Les tests O2 couvrent le calcul de cible, les capabilities, la sérialisation,
les révisions stale, cancellation/close, l'ordre state/événement et les
policies Default, Realtime et Recording. Les tests O3 créent une vraie
`NSWindow`, vérifient les contraintes initiales et mises à jour via les getters
générés KFFI, préservent le style mask, observent un resize externe et prouvent
l'isolation de deux fenêtres. Ces comportements sont lisibles de manière
automatique : aucun cahier manuel supplémentaire n'est requis pour cette
tranche.

## Découpage de la stack

1. Cette PR réserve `WIN-001` et `APK-006`, ajoute ce design et référence la tranche dans la
   roadmap.
2. La PR fille introduit le pipeline runtime portable et ses preuves O2, sans
   activer de capability AppKit.
3. La PR suivante relie le peer et le port AppKit aux APIs KFFI générées, puis
   apporte les preuves O3.
4. La dernière PR active `WIN-001` et `APK-006`, ajoute les mappings d'evidence
   et les gates CI obligatoires dans la même modification que l'activation
   publique.
