# AppKit Phase 5 — Chrome de fenêtre mutable

## But et frontière

Cette sous-tranche rend mutables `WindowUpdate.decorations` et
`WindowUpdate.systemButtons` sur AppKit. Elle prolonge le même pipeline
corrélé que le titre et la géométrie : une commande porte tous les changements
supportés, les applique sur le thread AppKit, relit le snapshot effectif, puis
publie un seul état et des événements corrélés.

Le *chrome* désigne ici uniquement la title bar native et ses trois boutons
standard : close, miniaturize et zoom. Il ne couvre pas fullscreen, toolbar,
position, level, transparence, blur, icône, attention ou content protection.
Ces propriétés restent `Unsupported` jusqu'à leur propre spécification.

Les notifications externes qui modifieraient le style d'une `NSWindow` hors de
Kadre ne sont pas observées dans cette tranche. La source native existante de
géométrie reste limitée à la géométrie ; les mutations Kadre reçoivent en
revanche un readback autoritaire.

## Précondition KFFI

Le snapshot KFFI publié expose déjà les bindings générés :

- `NSWindow.styleMask()` et `NSWindow.setStyleMask(NSWindowStyleMask)` ;
- `NSWindow.standardWindowButton(NSWindowButton)` ;
- `NSButton.isHidden()` et `NSButton.setHidden(Boolean)` par héritage de
  `NSView`.

Kadre les utilise uniquement sur le thread propriétaire AppKit et ne fabrique
ni selector ni `ObjCRuntime.msgSend`. Si l'un de ces bindings cesse d'être
disponible dans une résolution fraîche, la correction doit partir de Kextract,
puis régénérer et publier KFFI avant toute modification Kadre.

## Modèle effectif et canonisation

`WindowDecorations.System` représente une title bar AppKit native.
`WindowDecorations.Borderless` représente l'absence de title bar native. Le
modèle public conserve les trois choix de `WindowSystemButtons` : `All`,
`CloseOnly` et `None`.

La paire est toutefois canonisée avant la création et avant tout appel au
port :

| Décorations effectives | Boutons demandés | Boutons effectifs |
| --- | --- | --- |
| `System` | `All` | close + miniaturize + zoom quand la fenêtre est resizable |
| `System` | `CloseOnly` | close seulement |
| `System` | `None` | aucun bouton, title bar conservée |
| `Borderless` | toute valeur | `None` |

Le zoom est masqué quand `resizable == false`, même si l'état logique des
boutons vaut `All`. `resizable` reste donc l'autorité de l'affordance de zoom ;
le state ne ment pas en disant que le mode de boutons a changé.

`Borderless` ne peut pas exposer de bouton système : un `WindowSpec` ou un
update qui demande `Borderless` canonise donc `systemButtons` à `None` dans le
snapshot effectif. Le retour de `Borderless` vers `System` avec
`systemButtons = Unchanged` restaure `All`, car `None` était une conséquence
du mode borderless et non une préférence mémorisée cachée. L'appelant qui veut
`CloseOnly` ou `None` combine explicitement les deux changements dans une
seule commande.

Un update de `systemButtons = All` ou `CloseOnly` alors que les décorations
effectives restent `Borderless` échoue avec
`KadreFailure.InvalidRequest("systemButtons")` avant toute admission native.
`Set(None)` sous `Borderless` est un no-op. Ces règles évitent à la fois un
faux succès et un état contenant des boutons invisibles mais annoncés actifs.

## Style AppKit et readback

Le port dérive le style mask sans effacer les bits non possédés par Kadre :

- `System` conserve `NSWindowStyleMaskTitled` ;
- `Borderless` retire les bits de title bar et tous les boutons standards ;
- le bit `Closable` représente l'autorisation native de fermeture ;
- le bit `Miniaturizable` représente le bouton de miniaturisation ;
- le bit `Resizable` continue d'être gouverné par `resizable` ;
- la visibilité réelle des boutons close, miniaturize et zoom est appliquée et
  relue individuellement via `standardWindowButton`.

`WindowSystemButtons.None` avec `System` ne devient jamais un style
`Borderless` : il conserve la title bar et masque les trois boutons. Cette
distinction corrige l'ambiguïté du bootstrap qui utilisait le mask borderless
pour cette combinaison.

Le snapshot privé de mutation devient un snapshot de fenêtre complet : titre,
géométrie, décorations et boutons effectifs. Après un setter commencé, y
compris après une exception, le port relit les quatre dimensions. Les boutons
absents sont traités comme masqués seulement dans le mode `Borderless`; leur
absence en mode `System` est une failure de readback, jamais une réussite
inventée.

## Commandes, cancellation et événements

`decorations`, `systemButtons`, `title`, tailles, contraintes et `resizable`
peuvent partager une même commande `WindowUpdate`. Le runtime construit un
candidat complet, canonise le chrome, valide les combinaisons interdites et
réutilise un unique `WindowOperationId`.

Le commit boundary reste le dernier contrôle juste avant le premier setter
AppKit — style mask, visibilité d'un bouton, titre ou géométrie. Une
annulation avant ce point retire la commande sans setter ; après ce point elle
détache seulement le waiter et ne déclenche aucun rollback.

L'état effectif est publié avant les événements. Une révision émet au plus un
`GeometryChanged` pour la géométrie et un `PropertiesChanged` discret pour
`title`, `resizable`, `decorations` et `systemButtons`. Les changements de
chrome forcés par la canonisation figurent dans le même
`PropertiesChanged.changed` que le champ explicitement demandé. Cette voie
discrète reste soumise à `WindowDeliveryPolicy` ; aucune callback AppKit ne
publie directement dans un flow public.

## Création et capabilities

La création suit la même canonisation que l'update. Le peer natif reçoit le
`WindowSpec` effectif et le runtime commite ce même spec : une fenêtre initiale
borderless expose donc `systemButtons == None` dans son premier `WindowState`.

Après activation publique, une fenêtre AppKit expose :

```text
WindowCapabilities.decorations = Supported({ System, Borderless })
WindowCapabilities.systemButtons = Supported({ All, CloseOnly, None })
```

Les capabilities existantes de titre et de géométrie ne changent pas. Toutes
les autres mutations restent `Unsupported(UpdateWindow)`.

## Preuves et contrats

`WIN-003` est le contrat O2 actif du pipeline runtime de chrome. `APK-008`
est le contrat O3 actif de son activation AppKit. La dernière PR de la stack
active simultanément les capabilities, mappings, evidence et gates.

`WIN-003` couvre :

- validation et canonisation du chrome ;
- composition chrome + titre + géométrie ;
- cancellation avant et après le commit boundary ;
- état avant `PropertiesChanged` et policy discrète.

Ses sentinelles couvriront les boutons incompatibles sous `Borderless`, le
no-op canonique, la revalidation de révision, la corrélation d'opération et le
non-contournement de policy.

`APK-008` couvre :

- création effective avec chaque combinaison système ;
- update KFFI et readback du style et des boutons ;
- activation publique et mutation combinée ;
- chemin de policy et harness manuel AppKit.

Ses sentinelles couvriront la prévalidation, le commit boundary, la
préservation des bits de style non possédés, l'isolation de deux fenêtres, la
canonisation borderless et l'absence de contournement de policy.

Les tests JVM déterministes prouvent file, cancellation, state et événements.
Les tests macOS réels prouvent le binding généré et le readback de
`NSWindow`/`NSButton`. Un petit harness manuel est requis pour relever, sur une
session visible, les quatre combinaisons de title bar et l'apparence effective
des boutons ; ce contrôle visuel ne bloque pas la CI et ne remplace aucune
preuve automatisée.

## Découpage de la stack

1. Cette PR documente la sémantique, réserve `WIN-003` et `APK-008`, et
   actualise la roadmap.
2. Une PR fille étend le candidat, le snapshot et les événements runtime, sans
   activer de capability publique.
3. Une PR fille raccorde le peer, le port déterministe et KFFI au style mask,
   aux boutons et au readback, avec preuves privées macOS.
4. La dernière PR active les capabilities et contrats, produit l'evidence CI
   et ajoute le harness manuel de chrome.
