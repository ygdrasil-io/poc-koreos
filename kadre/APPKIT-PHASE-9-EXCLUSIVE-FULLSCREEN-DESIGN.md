# AppKit phase 9 — Fullscreen `Exclusive`

## But et décision de produit

Cette tranche active `FullscreenMode.Exclusive(displayId, mode)` sur AppKit.
Elle complète le fullscreen géré par les espaces macOS (`Borderless`) défini
dans [APPKIT-PHASE-5-WINDOW-FULLSCREEN-DESIGN.md](APPKIT-PHASE-5-WINDOW-FULLSCREEN-DESIGN.md) :
elle capture un display CoreGraphics, lui applique un mode précis et place la
fenêtre sur ce display, sans demander à AppKit de créer un espace fullscreen.

Le contrat reste volontairement étroit :

- `WindowSpec(fullscreen = Exclusive(...))` reste rejeté. Une création ne
  possède pas de `WindowOperationId`, donc ne peut pas corréler ni restaurer
  honnêtement une prise de contrôle de display.
- `Window.apply(WindowUpdate(fullscreen = Set(Exclusive(...))))` est le seul
  point d'entrée.
- l'entrée dans `Exclusive` n'est admise que depuis `Windowed`. Un passage de
  `Borderless` ou d'un `Exclusive` vers `Exclusive`, même sur le même display,
  est rejeté par `InvalidRequest("fullscreen")`. L'appelant doit d'abord
  demander `Windowed`, puis le nouvel `Exclusive`.
- `Exclusive → Borderless` est également rejeté ; la sortie exclusive est
  toujours `Windowed` avant une éventuelle entrée `Borderless`.
- `Exclusive` ne compose pas avec une autre propriété de `WindowUpdate` ; la
  règle d'update fullscreen mono-propriété de la phase 5 demeure.

La transition intermédiaire `Windowed` évite une fausse atomicité entre deux
displays : chaque opération possède ainsi une restauration définie, un seul
lease natif et un état public terminal non ambigu.

Cette tranche ne fournit ni renderer, ni widget system, ni changement de
résolution automatique. Elle ne déduit jamais un mode depuis ses métriques
visibles : seul le couple opaque `DisplayId` / `DisplayModeId` désigne la cible.

## Disponibilité et capability

La disponibilité requiert simultanément :

1. macOS 26 ou ultérieur, car `NSScreen.CGDirectDisplayID` est la première
   identité publique permettant de relier sans heuristique `NSScreen` et
   CoreGraphics ;
2. une énumération complète et courante de `DisplayManager` ;
3. le helper KFFI managed de capture/restauration décrit ci-dessous ;
4. l'installation réussie de l'observation CoreGraphics de reconfiguration.

Avant ces quatre conditions, AppKit conserve
`Capability.Supported({ Borderless })` pour le fullscreen de phase 5 et ne
publie pas `Exclusive` dans `WindowCapabilities.fullscreen`. Une demande
`Exclusive` retourne alors le rejet de champ usuel
`PartiallyApplied(Fullscreen = Unsupported(UpdateWindow))`. Il ne faut pas
masquer le support de `Borderless` sous prétexte que la sous-capability
exclusive est indisponible.

Une fois prête, la capability devient
`Capability.Supported({ Borderless, Exclusive }, Available)`. Si le bridge de
reconfiguration cesse d'être fiable, `Exclusive` est retiré de cet ensemble
avant toute nouvelle admission. Un lease déjà actif est alors terminalisé selon
la section « Reconfiguration ». `FeatureAvailability` ne certifie donc pas que
le display demandé est libre ; l'arbitrage est fait par l'opération.

## Frontières de responsabilité

### Runtime et identité publique

`RuntimeDisplayManager` reste l'unique endroit qui transforme les clés natives
de `DisplayPort` en `DisplayId` et `DisplayModeId` publics. Il expose en interne
une résolution éphémère :

```kotlin
internal data class ExclusiveDisplayTarget(
    val displayKey: Long,
    val modeKey: Long,
)
```

Cette résolution ne réussit que si l'inventaire courant est `Enumerated`, que
le display appartient encore à cette session et est `Connected`, et que le mode
est encore présent dans son snapshot. `displayKey` et `modeKey` ne franchissent
jamais l'API publique et ne sont ni des pointeurs ni des descriptions de mode.

Un ID inconnu, déconnecté ou un mode retiré est un
`InvalidRequest("fullscreen")` avant toute réservation. Une énumération
indisponible ou en cours de renouvellement est
`TemporarilyUnavailable(retryable = true)`. Ces deux résultats évitent à la
fois de prétendre qu'un handle terminal est réutilisable et de confondre un
problème temporaire d'inventaire avec une feature absente.

### Broker de processus

`AppKitProcessBroker` possède un unique `AppKitExclusiveDisplayBroker` pour le
processus. Son registre est indexé par la clé native du display ; son owner est
la paire de session et de fenêtre, jamais une coroutine appelante. Il ne garde
ni `Window`, ni session après leur fermeture.

Le broker réserve la clé avant le premier appel natif. Une réservation active,
y compris depuis une autre session embarquée, retourne
`TemporarilyUnavailable(retryable = true)` sans effet de bord. Une fenêtre ne
peut détenir qu'une seule réservation, ce qui rend impossible une bascule
directe `Exclusive(A) → Exclusive(B)`.

Le même broker observe les reconfigurations CoreGraphics et notifie le seul
owner concerné. Le routage vers le runtime est sérialisé sur l'executor AppKit
afin de préserver l'ordre state, event, completion. Ainsi deux sessions ne
peuvent jamais capturer simultanément le même display, même si elles ont leur
propre `DisplayManager`.

### KFFI et CoreGraphics

Kadre ne manipule ni `MemorySegment`, ni `CGDisplayModeRef`, ni callback FFI.
KFFI fournit un owner pointer-free, closeable et idempotent, conceptuellement :

```kotlin
interface ExclusiveDisplayLease : AutoCloseable {
    val displayId: Int
    fun targetStillEffective(): Boolean
    override fun close()
}

fun AppKitDisplayServices.openExclusiveLease(
    displayId: Int,
    modeOrdinal: Int,
): ExclusiveDisplayLease
```

Son ouverture copie et détient le mode initial, résout le mode cible à partir
de l'ordinal du snapshot frais, capture le display, applique le mode cible,
puis conserve les références nécessaires jusqu'à `close()`. Toute erreur après
la capture tente, dans cet ordre, restauration du mode initial, libération de
la capture et libération de toutes les références. `close()` suit exactement
le même ordre et agrège les erreurs sans abandonner les nettoyages suivants.

L'égalité du mode actif est faite par identité CoreGraphics ou API d'égalité
native correspondante, jamais par largeur, hauteur, taux de rafraîchissement ou
flags. Si les bindings générés nécessaires manquent, l'évolution commence dans
Kextract, est régénérée et validée dans KFFI, puis Kadre consomme le snapshot
publié. Le helper KFFI peut encapsuler une ressource durable ; aucun binding
généré n'est écrit à la main dans KFFI et aucun wrapper FFI n'est créé dans
Kadre.

### Fenêtre AppKit

Le port de fenêtre possède une snapshot privée de présentation exclusive :
style mask, frame, écran et niveau effectifs. À l'entrée il rend la fenêtre
borderless, la place sans animation sur le frame de l'`NSScreen` correspondant
au display capturé et conserve cette snapshot. À la sortie il restaure cette
snapshot avant de relâcher le lease CoreGraphics. Cette présentation n'est pas
un `toggleFullScreen` et ne produit pas les callbacks AppKit de la phase 5.

Les propriétés publiques persistantes (`decorations`, tailles, `level`) restent
leurs valeurs configurées pendant l'exclusive, comme pendant `Borderless`.
`WindowState.fullscreen` est le seul état de présentation fullscreen ; la
snapshot privée garantit que la fenêtre normale est rétablie sans prétendre que
la géométrie publique AppKit est déjà portable sur les écrans mixed-scale.

## Admission, commit et succès

La précédence d'admission d'un update exclusif est :

1. fenêtre ouverte ;
2. forme valide (`Clear`, update mixte et toute transition fullscreen autre que
   `Windowed → Exclusive` ou `Exclusive → Windowed` rejetée) ;
3. `expectedRevision` ;
4. presence de `Exclusive` dans la capability ;
5. résolution fraîche du couple display/mode ;
6. absence de barrière fullscreen ou de fermeture ;
7. réservation process-wide ;
8. canonisation/no-op.

Une demande identique à un lease réellement actif est un no-op `Applied` : elle
ne réinstalle pas le mode et ne crée ni révision ni événement. Une égalité de
valeur sans lease actif n'est jamais un no-op ; la réconciliation de perte aura
préalablement ramené l'état à `Windowed`.

Après la réservation, la séquence est :

1. le runtime revalide la révision et l'annulation avant le commit ;
2. KFFI ouvre le lease et applique le mode cible ;
3. le port AppKit entre dans la présentation exclusive ;
4. le port relit la présentation et KFFI relit le mode actif ;
5. le runtime publie `WindowState(fullscreen = Exclusive(...))`, puis
   `PropertiesChanged({ Fullscreen })`, puis
   `WindowUpdateOutcome.Applied` avec le même `WindowOperationId`.

La capture réussie est la frontière de commit. Avant elle, une cancellation
retire la commande et libère seulement sa réservation. Après elle, la
cancellation détache le waiter : la transaction atteint quand même un état
terminal et le diagnostic de session porte une failure qui n'aurait plus de
caller. Aucun `Accepted` n'est renvoyé pour une mutation exclusive.

Un échec avant capture libère la réservation et retourne la failure native.
Un échec après capture restaure d'abord la fenêtre et le lease, publie le
snapshot réellement obtenu puis complète par `PlatformFailure`. L'absence de
succès ne signifie jamais rollback implicite ; l'état et les événements restent
la source d'autorité.

## Sortie, fermeture et reconfiguration

`Windowed` depuis `Exclusive` est une transition terminale du lease : le port
restaure la fenêtre, KFFI tente de restaurer le mode initial puis libère la
capture, et le broker libère la réservation. Ensuite seulement Kadre publie
`Windowed` et l'événement corrélé. Si une étape de restauration échoue, la
capture et la réservation sont néanmoins libérées ; `WindowState` devient
`Windowed` car Kadre ne possède plus d'exclusive, et l'appel retourne la
`PlatformFailure` de nettoyage.

La fermeture d'une fenêtre, le teardown de session et la terminaison du broker
utilisent le même chemin, sans waiter à compléter. Les erreurs sont rapportées
comme diagnostics et ne retiennent jamais une capture après la destruction du
peer.

Lors d'une reconfiguration, le broker lit un snapshot frais. Une modification
sans rapport avec le display détenu laisse le lease actif. En revanche, ces
conditions sont terminales :

- le display cible n'est plus actif ;
- son mode effectif n'est plus exactement le mode détenu ;
- la reconfiguration ou le readback ne peut pas certifier l'état de la cible ;
- le port AppKit ne peut plus retrouver le `NSScreen` correspondant.

Dans ces cas Kadre tente la restauration et la libération en best effort, garde
la fenêtre ouverte, publie `Windowed` avec `operationId = null`, puis
`PropertiesChanged({ Fullscreen })`. Un `Window.apply` qui attendait encore
cette transition termine ensuite par
`PlatformFailure(AppKit, "exclusive-fullscreen", "display-lost")`. S'il n'y a
plus de waiter, la même failure est un diagnostic. Une perte de display ne
ferme donc jamais arbitrairement la fenêtre et ne laisse jamais l'API annoncer
un lease exclusif qui n'existe plus.

## Tests et preuves

Les preuves sont séparées par ce qu'elles peuvent réellement établir :

| Niveau | Preuves requises |
| --- | --- |
| O1 runtime | résolution opaque, précédence des failures, règle `Windowed` intermédiaire, state avant event avant completion, cancellation avant/après commit et perte de display. |
| O2 KFFI/AppKit fake | ordre capture → set mode → présentation ; restauration → release ; erreurs agrégées ; refs libérées ; identité de mode non déduite des métriques ; arbitrage inter-session du broker. |
| O3 macOS | smoke pointer-free de disponibilité et readback non disruptif ; aucun runner CI ne change un mode physique ni ne capture l'écran principal. |
| Manuel | sur un display de test distinct : entrée, vérification du mode et de la fenêtre, sortie, restauration du mode/frame/style ; retrait du display ou changement de mode ; contrôle qu'aucune capture ne survit. |

Le protocole manuel est une preuve matérielle, pas un remplacement déguisé par
un fake. Le contrat AppKit exclusif n'est activé dans le registre de contrats
qu'après l'existence de ce cahier de test durable et l'exécution tracée sur un
display dédié. La CI reste stricte sur les invariants O1/O2/O3 qu'elle peut
observer sans perturber le runner géré.

## Documentation et hors scope

L'implémentation mettra à jour `DESIGN.md`, `OPERATION-CONTRACTS.md`,
`BACKEND-CAPABILITIES.md`, `PUBLIC-API-CATALOG.md`, le registre de contrats et
le cahier de test manuel AppKit. La documentation de phase 5 remplacera son
renvoi « hors scope » par un lien vers ce document.

Cette tranche ne rend pas `outerPosition` AppKit disponible, ne choisit pas de
mode par défaut, ne change pas les presentation options process-wide et ne
supporte pas encore le passage direct entre deux exclusives. Ces limites sont
des `Unsupported` ou `InvalidRequest` explicites, jamais des succès simulés.
