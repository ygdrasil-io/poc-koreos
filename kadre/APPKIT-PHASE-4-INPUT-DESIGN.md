# AppKit Phase 4 — Input essentiel

## But

Activer l’input interactif d’une `NSView` AppKit existante sans ajouter de renderer,
de widget, de `DeviceManager`, d’IME, de gesture, de touch, de raw input ni de
couche FFI Kadre. La surface publique reste celle du catalogue fermé :
`HostSurface.input` expose déjà un `SurfaceInput` formé d’un `StateFlow` et d’un
`Flow` unique d’événements.

La phase active uniquement clavier, pointeur souris et scroll. Tous les autres
champs de `InputCapabilities` restent explicitement `Unsupported`.

## Préconditions KFFI

`KFFI-OBJC-004` est nécessaire avant toute activation clavier : la sous-classe
`NSView` doit pouvoir retourner `true` pour `acceptsFirstResponder` par une route
managée `BOOL(id, SEL)`. `KFFI-OBJC-005` est nécessaire avant la preuve O3 de
scroll : Kadre doit injecter un événement scroll réel dans la file AppKit, sans
construire d’appel natif local. Les deux changements sont livrés et publiés par
KFFI avant l’activation contractuelle Kadre.

Les callbacks `void(id, SEL, id)` existants servent à `keyDown:`, `keyUp:`,
`flagsChanged:`, aux événements souris et à `scrollWheel:`. Leur `NSEvent` est
borrowed : le peer AppKit le mappe immédiatement vers un stimulus immuable sans
adresse native, puis le route vers le serializer de session.

La voie O3 de scroll est bornée à `CGEventCreateScrollWheelEvent2`, à ses
setters de champs et à `NSEvent.eventWithCGEvent:` suivi de
`NSApplication.postEvent_atStart`. La façade KFFI prend des unités et phases
CoreGraphics (`CGScrollEventUnit`, `CGScrollPhase`,
`CGMomentumScrollPhase`), jamais des valeurs `NSEventPhase` : leurs valeurs
binaires ne sont pas interchangeables. Elle conserve le `CGEvent` créé,
convertit dans un autorelease pool, le relâche après la conversion retenue par
`NSEvent`, puis poste l'événement sans exposer de `MemorySegment` ou de
`CGEventRef` libérable à Kadre. L'O3 KFFI doit retirer l'événement réellement
posté de la file et observer son `NSEvent` converti — type, FIFO, précision,
`deltaX`/`deltaY`, phase et momentum — car le SDK ne documente pas assez la
conversion de tous les champs pour qu'un simple test de construction soit une
preuve suffisante.

Cette primitive ne cible pas une fenêtre AppKit : `eventWithCGEvent:` produit
un événement avec `windowNumber == 0`. Même avec une fenêtre front/key dont la
vue couvre sa position, `NSApplication.sendEvent:` ne le livre pas à
`scrollWheel:`. KFFI ne contourne pas cette règle par `NSWindow.sendEvent:`,
appel direct de callback, KVC ou API privée. La livraison effective à un
responder reste une preuve manuelle Phase 4 avec un périphérique réel ; la CI
prouve séparément la file et la conversion native.

Les fields CoreGraphics fixed-point conservent les `Double` dans
`NSEvent.deltaX`/`deltaY`. En revanche AppKit calcule
`scrollingDeltaX`/`scrollingDeltaY` depuis les fields PointDelta entiers créés
par `CGEventCreateScrollWheelEvent2`. Les scénarios CI qui vérifient les
`scrollingDelta` précis emploient donc des valeurs intégrales ; le harness
manuel vérifie les fractions d'un trackpad réel. Cette limite n'autorise jamais
un arrondi silencieux dans le mapper des événements physiques reçus par Kadre.

## Autorités et trajet

```text
NSView managed override / tracking area
    -> AppKitInputStimulus immutable, peer-local
    -> AppKitWindowCommandQueue session-local
    -> SurfaceInputStimulus runtime
    -> reducer + scheduler runtime
    -> SurfaceInput.state, puis SurfaceInput.events
```

AppKit ne crée jamais un `InputEvent`, un `EventStamp`, une `InputStateRevision`
ni un identifiant public. Le runtime alloue ces valeurs et publie l’état avant
l’événement. Les stimuli admis avant la disponibilité de la surface runtime
suivent le même FIFO pré-ready que les stimuli de surface Phase 3. Une callback
Objective-C ne bloque pas sur le runtime et n’invoque aucun collector.

## Contrat activé

- `InputCapabilities.keyboard` et `InputCapabilities.pointer` passent à
  `Available` uniquement lorsque l’observation native de la vue est installée.
  Perdre le focus neutralise l’état mais ne rend pas ces capacités indisponibles :
  la capacité décrit l’installation structurelle, pas l’éligibilité transitoire
  de la fenêtre.
- Clavier : `InputEvent.Key`, avec touche physique HID lorsque le mapping est
  connu, logical key conservée ou `Unidentified`, localisation, repeat et
  modifiers effectifs. Une release n’a jamais `repeat = true`. Kadre ne produit
  aucun texte composé depuis ces événements.
- Pointeur : une souris possède un `PointerId` runtime-local stable tant que la
  surface est attachée. Entrée, sortie, mouvement, drag et boutons mettent à
  jour le snapshot. `PointerLeft` retire la souris du snapshot après avoir
  publié l’événement qui porte sa dernière position. Les boutons AppKit inconnus
  deviennent `PointerButton.Other`; aucune valeur n’est inventée.
- Scroll : `ScrollDelta.Lines` représente la source discrète et
  `ScrollDelta.Logical` la source précise. Phase et momentum ne sont pas
  exposés par le catalogue public fermé ; le runtime les conserve seulement comme
  frontières de coalescence. Il ne fusionne jamais deux phases natives distinctes
  ni un scroll de momentum avec un scroll non-momentum. Cette règle évite de les
  perdre silencieusement sans agrandir l’API publique dans cette phase.
- `pointerCapture`, cursor, hit testing et `inputDefaultBehavior` restent hors
  scope et `Unsupported`; aucun `SurfaceCommandPort.apply` AppKit n’est activé.

## Réduction, reset et terminalisation

Une touche ou un bouton natif modifie le snapshot, incrémente la révision, puis
publie l’événement avec cette révision. Un repeat qui ne change ni keys ni
modifiers conserve la révision courante mais reste un événement ordonné.
Un scroll ne modifie pas le snapshot et référence donc la révision courante.

Après `SurfaceFocus.Unfocused`, le runtime publie atomiquement un état neutre
(`pressedKeys`, modifiers et boutons vides), incrémente une unique révision puis
publie exactement un `InputEvent.StateReset(FocusLost)`. Il ne synthétise aucune
release. Un nouveau focus ne réactive aucun ancien état.

La lane input applique `KadrePolicy.input` : événements discrets FIFO, mouvement
coalescé par pointeur et scroll coalescé à l’intérieur d’une même frontière de
phase/momentum. Les agrégats additionnent les deltas et conservent la dernière
position/stamp. La saturation d’ingress est terminale : snapshot neutre avec
capabilities `Unavailable(SourceOverflow)`, diagnostic, puis terminaison du
flow sans tenter un reset qui ne pourrait plus être inséré. Après detach ou
révocation native, tout stimulus tardif est ignoré et le flow input est fermé.

## Preuves

`INP-001` couvre au niveau O2 le reducer, l’ordre état/événement, unknown keys,
repeat, reset FocusLost, coalescing/barrières, overflow et fermeture. `APK-005`
active la traversée O3 avec une vraie fenêtre/vie AppKit : acquisition du first
responder, key down/up/repeat/modifiers, mouse enter/exit/move/button, et
injection scroll discret/précis dans la file native avec conversion observée.
La livraison de ce scroll synthétique à `scrollWheel:` est explicitement hors
de la preuve CI car AppKit ne lui associe aucune fenêtre ; reset sur perte de
focus et absence de callback après fermeture restent des preuves O3 des
callbacks réellement reçus.

Le harness manuel Phase 4 est distinct de cette CI. Il enregistre focus clavier,
modifiers/repeat, mouvement/boutons, scroll souris et trackpad, fractions de
scroll précis, momentum, perte de focus pendant une touche ou un bouton
maintenu et fermeture pendant input. Une observation humaine ne remplace jamais
les preuves O2/O3 ; elle couvre seulement le routing responder que cette
primitive synthétique ne sait pas cibler.
