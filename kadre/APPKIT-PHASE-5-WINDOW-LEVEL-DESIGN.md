# AppKit Phase 5 — Niveau de fenêtre mutable

## But et frontière

Cette sous-tranche rend mutable WindowUpdate.level sur AppKit. Elle emprunte le
pipeline corrélé déjà actif pour le titre, la géométrie et le chrome : une
commande contient tous les champs supportés, s'exécute sur le thread AppKit,
relit le snapshot effectif, puis publie un état et des événements corrélés.

Le périmètre porte seulement sur les trois valeurs publiques déjà fermées :
WindowLevel.Normal, WindowLevel.Floating et WindowLevel.Modal. Il ne couvre ni
modalité applicative, ni sheet, ni boucle modale, ni fullscreen, ni position,
transparence, blur, icône, attention ou content protection. Ces capabilities
restent Unsupported jusqu'à leurs propres tranches.

WindowLevel.Modal ne signifie donc pas « rendre la fenêtre modale ». Il désigne
exclusivement le niveau de z-order macOS kCGModalPanelWindowLevel. Kadre ne
bloque pas l'input d'une autre fenêtre, ne possède pas de boucle modale et ne
change aucun ownership de session.

Les changements de niveau effectués hors de Kadre ne sont pas observés dans
cette sous-tranche. Ils n'engendrent donc pas de publication avec
operationId = null ; les mutations Kadre disposent en revanche d'un readback
autoritaire.

## Précondition KFFI

Le snapshot KFFI publié expose les bindings générés nécessaires et charge le
framework CoreGraphics pour les downcalls de niveau :

- NSWindow.level() et NSWindow.setLevel(NSWindowLevel) ;
- CGWindowLevelForKey(CGWindowLevelKey) ;
- kCGNormalWindowLevelKey, kCGFloatingWindowLevelKey et
  kCGModalPanelWindowLevelKey.

Le port convertit les valeurs avec CGWindowLevelForKey au lieu de recopier des
constantes numériques. Kadre appelle seulement ces bindings générés sur le
thread propriétaire AppKit ; il ne construit ni selector, ni downcall Panama,
ni wrapper FFI local. La génération Kextract/KFFI doit donc conserver le lookup
CoreGraphics partagé par les sources Objective-C séparées ; Kadre ne compense
jamais un binding incomplet par une implémentation FFI locale.

## Modèle effectif

| Niveau Kadre | Niveau AppKit effectif |
| --- | --- |
| Normal | CGWindowLevelForKey(kCGNormalWindowLevelKey) |
| Floating | CGWindowLevelForKey(kCGFloatingWindowLevelKey) |
| Modal | CGWindowLevelForKey(kCGModalPanelWindowLevelKey) |

La création applique le niveau effectif avant la présentation de la fenêtre ;
le WindowState initial utilise ce même WindowSpec effectif. Une mutation
Set(level) applique le niveau demandé et le relit avant toute publication.
Clear est invalide pour ce champ persistant obligatoire et échoue avec
KadreFailure.InvalidRequest("level") avant admission native.

Une valeur native qui n'est égale à aucune des trois valeurs calculées est un
échec de readback. Kadre ne la clamp jamais et ne la présente jamais comme
Normal. Si un setter a déjà commencé, la failure est traitée par la frontière
de commit existante ; aucun succès ni snapshot effectif inventé n'est publié.

Le niveau est indépendant du style mask et du chrome : modifier level ne
réécrit pas les décorations, les boutons ou les contraintes. Le fullscreen est
encore publicement impossible sur AppKit ; sa future spécification définira
explicitement l'autorité entre son niveau temporaire éventuel et le niveau
persistant demandé.

## Commandes, cancellation et événements

Level peut être combiné avec title, la géométrie et le chrome dans un unique
WindowUpdate et un unique WindowOperationId. Le runtime construit un candidat
complet et valide Clear avant de soumettre la commande au port.

La frontière de commit est le dernier contrôle juste avant le premier setter
AppKit, quel qu'il soit. Une annulation avant ce point retire la commande sans
appeler setLevel ; après ce point, elle détache seulement le waiter et ne
déclenche aucun rollback.

Après readback, le runtime publie l'état effectif avant les événements. Si le
niveau a changé, une unique WindowEvent.PropertiesChanged contient
WindowProperty.Level, avec le même operation ID que les autres champs de la
commande. La publication reste soumise à WindowDeliveryPolicy ; aucune callback
AppKit ne publie directement dans un flow public.

Un Set égal au niveau effectif et à `desiredLevel` est un no-op : aucun setter,
aucune nouvelle révision et aucun événement ne sont produits. Lorsque le
fullscreen maintient une intention interne `desiredLevel` qui diffère de cette
valeur effective, le même Set réaligne néanmoins cette intention : il retourne
`Applied` avec un nouvel `operationId` et l'état inchangé, sans setter ni
publication. Un Set dont
le champ level réussit dans un `PartiallyApplied` réaligne aussi `desiredLevel` ;
un rejet de level ou un readback divergent ne le fait pas.

## Création et capability

La création avec WindowSpec(level = ...) applique et relit le niveau avant que
la fenêtre soit exposée. Après activation publique, AppKit expose :

    WindowCapabilities.level = Supported({ Normal, Floating, Modal })

Cette capability ne dépend pas d'une version macOS récente ni d'une permission.
Toutes les autres mutations hors périmètre restent Unsupported(UpdateWindow) et
Window.requestAttention reste Unsupported(RequestWindowAttention).

## Preuves et contrats

WIN-004 est le contrat O2 du pipeline runtime de niveau. APK-009 est le
contrat O3 de l'activation publique AppKit. Ils sont activés avec la capability,
les mappings, l'evidence et les gates de la dernière carte de la stack.

WIN-004 couvrira :

- validation de Clear, no-op et revalidation de révision ;
- composition niveau + titre + géométrie + chrome ;
- cancellation avant et après la frontière de commit ;
- ordre état avant PropertiesChanged et policy discrète.
- réalignement de `desiredLevel` égal au niveau effectif, sans setter, révision
  ni événement, et réussite du champ level dans `PartiallyApplied`.

Ses sentinelles couvrent le clear pré-commit, le no-op, la révision stale, la
corrélation d'opération et l'absence de contournement de policy.

APK-009 couvrira :

- création initiale avec les trois niveaux ;
- update et readback avec les bindings KFFI générés ;
- activation publique et mutation combinée ;
- cancellation avant le premier setter et isolation de deux peers ;
- chemin de policy.

Ses sentinelles couvrent le binding généré, la frontière de commit, le
readback effectif, l'isolation inter-fenêtres et la policy. Aucun harness
manuel n'est requis : le contrat promet une valeur de niveau native lisible,
pas une apparence de renderer ni une hiérarchie d'applications tierces que la
CI ou un harness isolé pourraient évaluer honnêtement.

## Découpage de la stack

1. Cette PR ajoute ce design, réserve WIN-004 et APK-009, et met à jour la
   roadmap sans activer de capability.
2. Une PR fille étend le candidat runtime, les événements et les tests O2,
   sans activation publique.
3. Une PR fille raccorde le peer, le port déterministe et KFFI au niveau et au
   readback, avec preuves privées macOS.
4. La dernière PR active la capability, les contrats et les evidence CI.
