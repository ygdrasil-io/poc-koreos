# AppKit Phase 5 — Titre de fenêtre mutable

## But et frontière de la tranche

Cette sous-tranche active une seule mutation top-level supplémentaire sur
AppKit : `WindowUpdate.title = PropertyChange.Set(value)`. Elle prolonge le
pipeline de géométrie déjà actif sans créer une voie spéciale de mutation. Une
completion porte toujours le snapshot effectivement lu depuis la fenêtre
native ; ni le runtime ni l'appelant ne déduisent le succès de la seule
admission de la commande.

La valeur initiale de `WindowSpec.title` est déjà installée avant présentation
par la phase 2. Cette tranche rend ce même champ mutable après ouverture. Elle
ne modifie pas le vocabulaire public, ne change pas le contrat de création et
ne promet pas l'observation des mutations de titre faites en dehors de Kadre.

Les décorations, les boutons système, fullscreen, level, transparence, blur,
icône, content protection et attention restent explicitement `Unsupported`.
`outerPosition` et `outerBounds` restent hors phase jusqu'à l'inventaire
Display de phase 9. En particulier, cette tranche ne choisit pas de sémantique
pour la combinaison `Borderless` et `systemButtons` : elle sera définie avec
son propre snapshot, ses propres capabilities et ses preuves.

## Précondition KFFI

Le snapshot KFFI publié expose déjà les bindings générés
`NSWindow.setTitle(String)` et `NSWindow.titleAsString()`. Kadre n'ajoute ni
selector, ni `ObjCRuntime.msgSend`, ni binding écrit à la main. Le peer appelle
ces APIs typées générées uniquement sur le thread propriétaire AppKit.

Si cette dépendance devenait indisponible ou divergeait lors d'une résolution
fraîche, la correction doit remonter vers Kextract, puis une régénération et une
publication KFFI. Aucun contournement FFI local n'est autorisé dans Kadre.

## Modèle de commande et autorités

La commande corrélée existante reste le seul trajet pour toute combinaison de
champs supportés :

```text
Window.apply(WindowUpdate)
    -> RuntimeWindow : candidat complet, file par fenêtre, OperationId
    -> WindowCommandPort : une commande corrélée
    -> AppKitWindowCommandQueue : sérialisation de la session
    -> AppKitWindowPeer / AppKitNativeWindowPort : setters et readback
    -> WindowState effectif, StateFlow, puis Window.events
```

Le runtime est l'autorité de `WindowState`, des révisions, de la policy de
livraison et des outcomes. Le peer conserve seulement les owners natifs et les
snapshots privés de commande. Il ne possède jamais un second état public.

Le nom interne `enabledWindowGeometryCapabilities` devient
`enabledWindowUpdateCapabilities`, car la même configuration porte désormais
les quatre propriétés de géométrie déjà actives et `WindowProperty.Title`. Ce
set contrôle l'annonce de capability et la normalisation de `WindowSpec` par
le backend ; il ne crée pas de nouvelle API publique.

## Sémantique de WindowUpdate

`Set(title)` remplace le titre courant. `Clear(title)` est invalide parce que
`WindowState.title` est non nullable : il retourne
`KadreFailure.InvalidRequest("title")` avant toute admission au port, sans
setter, événement, révision ni modification d'un autre champ de l'update.

Le runtime construit un candidat en appliquant `title`, `contentSize`,
`minimumSize`, `maximumSize` et `resizable` ensemble. Il conserve la validation
de géométrie `minimumSize <= contentSize <= maximumSize`, par dimension, avant
tout appel natif. Un update qui combine titre et géométrie est donc une seule
commande et une seule completion corrélée ; aucune propriété supportée n'est
artificiellement rejetée parce qu'une autre propriété du même update est
présente.

Un `Set` vers le titre déjà effectif, éventuellement combiné à des changements
de géométrie eux aussi nuls, est un no-op : nouvel `WindowOperationId`, outcome
`Applied` portant l'état courant, aucune mutation native, aucun événement et
aucun incrément de révision. Toute propriété hors sous-tranche continue de
figurer dans `PartiallyApplied.rejected` avec
`Unsupported(UpdateWindow)` et n'atteint jamais le port natif.

## Commit, cancellation et erreur native

La commande attend sa tête de file, revalide `expectedRevision`, puis passe au
port AppKit. Le token privé devient committé exactement juste avant le premier
setter réel, qu'il soit `setTitle` ou le premier setter de géométrie. Avant ce
point, l'annulation retire la commande et interdit tout setter. Après ce point,
l'annulation détache seulement le waiter : il n'y a aucun rollback artificiel.

Le port applique le titre avant les setters de géométrie, puis relit en une
seule opération le titre et le snapshot de géométrie. Une exception après le
point de commit déclenche le même readback ; l'état effectivement lu est publié
et l'échec est porté par `PartiallyApplied.rejected` pour les champs dont la
valeur demandée ne s'est pas matérialisée. Une exception avant le premier
setter reste une failure externe de l'opération. Cette règle est identique au
contrat de géométrie déjà actif.

`NSWindow` ne fournit pas, dans cette tranche, une observation de titre
étranger à Kadre. `observeNativeUpdate` reste donc limité à la géométrie : une
modification de titre via un handle natif échappe volontairement au
`WindowState` jusqu'à ce qu'une source de notification fiable soit spécifiée et
prouvée. Les setters Kadre, eux, sont toujours suivis du readback autoritaire.

## Snapshot et événements

Le seam AppKit évolue de paires `AppKitWindowGeometryTarget` /
`AppKitWindowGeometrySnapshot` isolées vers une cible de mutation qui contient
un changement de titre et la cible de géométrie existante, et un snapshot qui
contient le titre effectif et le snapshot de géométrie. Les valeurs restent
privées et sans adresse native.

Après une completion, `Window.state` reçoit le snapshot effectif avant toute
publication. Une révision peut publier au plus :

1. `WindowEvent.GeometryChanged` si taille ou contraintes ont changé ;
2. `WindowEvent.PropertiesChanged` si le titre ou `resizable` a changé.

Lorsque titre et `resizable` changent à la même révision, ils sont réunis dans
un seul `PropertiesChanged.changed`. L'événement conserve l'`OperationId` de la
commande ; une observation géométrique externe garde `operationId = null`.
Les règles existantes de `WindowDeliveryPolicy` restent inchangées : la
géométrie peut être coalescée, les propriétés sont discrètes FIFO, et une
barrière discrète scelle un agrégat de géométrie antérieur.

## Capabilities

À l'activation publique, `WindowCapabilities.title` devient
`Capability.Supported(Unit, FeatureAvailability.Available)` pour une fenêtre
AppKit ouverte. Les quatre capabilities de géométrie restent actives et
inchangées. Toutes les autres capabilities de mutation restent
`Unsupported(UpdateWindow)`.

Avant l'activation finale, les capacités publiques ne changent pas. Les étapes
runtime et AppKit privées exercent le pipeline et les preuves sans annoncer une
capability que la session publique ne peut pas encore honorer.

## Preuves et contrats

`WIN-002` est réservé comme contrat O2 `planned` pour le pipeline runtime de
titre, et `APK-007` comme contrat O3 `planned` pour son activation AppKit. Ils
deviennent `active` dans la même PR que `WindowCapabilities.title`, les
mappings d'evidence et les gates CI. Aucun contrat actif ne peut précéder ses
preuves exécutables.

`WIN-002` couvre :

- `runtime-window-title-validation` ;
- `runtime-window-title-composition` ;
- `runtime-window-title-cancellation` ;
- `runtime-window-title-event-order`.

Ses sentinelles sont :

- `runtime-window-title-clear-precommit` ;
- `runtime-window-title-noop` ;
- `runtime-window-title-stale-dispatch` ;
- `runtime-window-title-operation-correlation` ;
- `runtime-window-title-policy-bypass`.

`APK-007` couvre :

- `appkit-window-title-public-activation` ;
- `appkit-window-title-native-update` ;
- `appkit-window-title-combined-update` ;
- `appkit-window-title-policy`.

Ses sentinelles sont :

- `appkit-window-title-generated-binding` ;
- `appkit-window-title-cancellation-boundary` ;
- `appkit-window-title-effective-readback` ;
- `appkit-window-title-cross-window` ;
- `appkit-window-title-policy-bypass`.

Les preuves O2 sont des tests JVM déterministes de file, validation, snapshot
et policy. Les preuves O3 combinent le port déterministe pour les courses et
une vraie `NSWindow` sur macOS pour démontrer le binding généré et le readback.
Aucun cahier de test manuel n'est requis : le titre est une mutation
déterministe sans phénomène visuel ou matériel inaccessible à ces oracles.

## Découpage de la stack

1. Cette PR ajoute ce design, réserve `WIN-002` et `APK-007` au registre, et
   inscrit la sous-tranche dans la roadmap.
2. La PR fille généralise le runtime à une mutation titre + géométrie et ajoute
   les preuves O2, sans activer `WindowCapabilities.title`.
3. La PR suivante raccorde le peer, le port déterministe et
   `KffiAppKitWindowPort` au readback de mutation, avec les preuves O3 privées.
4. La dernière PR active les deux contrats, la capability publique, les
   mappings d'evidence et la gate AppKit dans une même modification.
