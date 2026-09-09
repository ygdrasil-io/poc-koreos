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
visibles : seul le couple `DisplayId` / `DisplayMode` issu du même inventaire
de session désigne la cible ; son `DisplayModeId` reste opaque.

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
avant toute nouvelle entrée exclusive. Un lease déjà actif est alors
terminalisé selon la section « Reconfiguration » ; sa sortie vers `Windowed`,
son nettoyage et sa fermeture restent toujours admis. `FeatureAvailability` ne
certifie donc pas que le display demandé est libre ; l'arbitrage est fait par
l'opération.

Le broker désactive d'abord l'admission exclusive sous son lock, publie ensuite
la capability sans `Exclusive` pour toutes les fenêtres vivantes sur l'executor
AppKit, puis traite les leases actifs. Une commande déjà `Reserved` mais pas
encore capturée passe atomiquement à `Released`, libère sa réservation si
`{ displayKey, token }` correspond toujours, puis complète son
`PendingWindowUpdate` par `TemporarilyUnavailable(retryable = true)`. Si le
bridge a produit une failure stable, cette completion est sa
`PlatformFailure` exacte. Une commande `Committing` ou `Active` est menée
jusqu'à son terminal. La phase 5 conserve sa propre availability pour
`Borderless` : perdre la primitive exclusive ne retire jamais ce mode.

## Frontières de responsabilité

### Runtime et identité publique

`RuntimeDisplayManager` reste l'unique endroit qui transforme les clés natives
de `DisplayPort` en `DisplayId` et `DisplayModeId` publics. Les deux compteurs
sont process-wide, et non plus locaux à un manager : un handle d'une autre
session ne peut donc jamais égaler par accident un handle local. Il expose en
interne une résolution éphémère :

```kotlin
internal data class ExclusiveDisplayTarget(
    val displayKey: Long,
    val modeKey: Long,
)
```

Cette résolution ne réussit que si l'inventaire courant est `Enumerated`, que
le `DisplayId` appartient à ce manager et est `Connected`, et que le
`DisplayMode` complet est égal au mode actuellement enregistré sous son
`DisplayModeId` pour ce display. Cette dernière comparaison rejette un mode
provenant d'un autre display, d'une autre session ou modifié par copie. Les
`displayKey` et `modeKey` ne franchissent jamais l'API publique et ne sont ni
des pointeurs ni des descriptions de mode.

Un ID inconnu, déconnecté, étranger, un mode retiré ou divergent est un
`InvalidRequest("fullscreen")` avant toute réservation. Une énumération
indisponible ou en cours de renouvellement est
`TemporarilyUnavailable(retryable = true)`. Ces deux résultats évitent à la
fois de prétendre qu'un handle terminal est réutilisable et de confondre un
problème temporaire d'inventaire avec une feature absente.

### Broker de processus

`AppKitProcessBroker` possède un unique `AppKitExclusiveDisplayBroker` pour le
processus. Son registre est indexé par la clé native du display ; son owner est
la paire de session et de fenêtre, jamais une coroutine appelante. Chaque
réservation alloue en plus un token monotone unique. Le broker ne garde ni
`Window`, ni session après leur fermeture.

Le broker réserve la clé avant le premier appel natif. Une réservation active,
y compris depuis une autre session embarquée, retourne
`TemporarilyUnavailable(retryable = true)` sans effet de bord. Une fenêtre ne
peut détenir qu'une seule réservation, ce qui rend impossible une bascule
directe `Exclusive(A) → Exclusive(B)`.

Le cycle de vie fermé est `Reserved → Committing → Active → Releasing →
Released`, ou `Quarantined` si CoreGraphics ne confirme pas la libération. Tous
les callbacks et nettoyages portent `{ displayKey, token }` ; ils sont ignorés
s'ils ne désignent plus l'entrée courante. Cette règle évite qu'un callback ou
un cleanup tardif libère le lease réattribué au même display. Une reconfiguration
provoquée par `capture`, `set mode`, restauration ou `release` est mémorisée
pendant `Committing`/`Releasing`, puis réconciliée une fois le readback terminal
effectué ; elle ne peut pas terminer la transaction réentrante.

Le même broker observe les reconfigurations CoreGraphics et notifie le seul
owner concerné avec son token. Le routage vers le runtime est sérialisé sur
l'executor AppKit afin de préserver l'ordre state, event, completion. Le
callback est révoqué avant le teardown de son owner. Ainsi deux sessions ne
peuvent jamais capturer simultanément le même display, même si elles ont leur
propre `DisplayManager`.

### KFFI et CoreGraphics

Kadre ne manipule ni `MemorySegment`, ni `CGDisplayModeRef`, ni callback FFI.
KFFI fournit des résultats pointer-free, closeables et idempotents,
conceptuellement :

```kotlin
interface ExclusiveDisplayLease : AutoCloseable {
    val displayId: Int
    fun readback(): ExclusiveDisplayReadback
    fun release(): ExclusiveDisplayReleaseResult
    override fun close() { release() }
}

sealed interface ExclusiveDisplayLeaseOpenResult {
    data class Opened(val lease: ExclusiveDisplayLease) : ExclusiveDisplayLeaseOpenResult
    data class FailedBeforeCapture(val failure: ExclusiveDisplayNativeFailure) : ExclusiveDisplayLeaseOpenResult
    data class FailedAfterCapture(
        val terminal: ExclusiveDisplayTerminal,
        val cleanup: ExclusiveDisplayReleaseResult,
        val recovery: ExclusiveDisplayLease?,
        val failure: ExclusiveDisplayNativeFailure,
    ) : ExclusiveDisplayLeaseOpenResult
}

fun AppKitDisplayServices.openExclusiveLease(
    displayId: Int,
    modeIdentity: Long,
): ExclusiveDisplayLeaseOpenResult
```

Son ouverture copie et détient le mode initial, résout le mode cible à partir
de son identité I/O stable dans le snapshot frais, capture le display, applique
le mode cible,
puis conserve les références nécessaires jusqu'à `close()`. Toute erreur après
la capture tente, dans cet ordre, restauration du mode initial, libération de
la capture et libération de toutes les références. `release()` suit exactement
le même ordre et agrège les erreurs sans abandonner les nettoyages suivants.
`ExclusiveDisplayTerminal` est fermé : `Captured(modeIdentity)`,
`Released(modeIdentity?)` ou `Unknown`. `Unknown` représente explicitement un
readback qui ne peut pas certifier le mode ni la capture ; il n'invente aucune
valeur. `ExclusiveDisplayReadback` porte le mode certifié et si la capture est
encore retenue ; `ExclusiveDisplayReleaseResult` porte le même terminal et
toutes les erreurs de cleanup. Kadre peut donc distinguer sans heuristique un
refus pré-commit d'une failure après capture et publier l'état effectif avant sa
failure. Lorsque la capture est toujours retenue ou inconnue, `recovery` est
l'unique owner qui peut retenter `release()` ; il ne peut servir ni à entrer une
nouvelle exclusive, ni à changer de mode. Il est obligatoirement non nul pour
`Captured` et `Unknown`, et obligatoirement nul pour `Released`.

`CGDisplayModeGetIODisplayModeID`, déjà généré, fournit l'identité I/O du mode.
KFFI la normalise en clé `Long` non négative et l'exige unique dans chaque liste
de modes. Une valeur absente ou dupliquée rend le snapshot invalide ; Kadre ne
publie alors pas un inventaire partiel. L'ordinal de liste ne peut servir qu'à
l'itération interne et ne traverse jamais `DisplayPort`. Le helper vérifie le
mode actif par cette identité et l'égalité CoreFoundation correspondante, jamais
par largeur, hauteur, taux de rafraîchissement ou flags. Si les bindings générés
nécessaires manquent, l'évolution commence dans Kextract, est régénérée et
validée dans KFFI, puis Kadre consomme le snapshot publié. Le helper KFFI peut
encapsuler une ressource durable ; aucun binding généré n'est écrit à la main
dans KFFI et aucun wrapper FFI n'est créé dans Kadre.

### Fenêtre AppKit

KFFI possède un second lease géré, indépendant du lease CoreGraphics :
`ExclusiveWindowPresentationLease`. Son ouverture reçoit uniquement une
`NSWindow` KFFI typée, prend une snapshot détachée
`(styleMask.rawValue, frame, displayId?, level)` et réserve cette fenêtre contre
une seconde présentation exclusive, sans la muter. Après l'ouverture du lease
CoreGraphics, `present(displayId)` résout le `NSScreen` dont
`CGDirectDisplayID` correspond au display capturé, rend la fenêtre borderless,
applique sans animation le frame courant de cet écran et le niveau
`CGShieldingWindowLevel`. Kadre ne transmet ni `MemorySegment`, ni selector,
ni pointeur. AppKit ne fournit pas de setter `screen` : le screen est donc une
conséquence du frame et le readback certifie le `displayId?` de `window.screen`,
il ne prétend jamais assigner un écran.

Le lease n'est disponible que sur macOS 26 ou ultérieur, comme
`NSScreen.CGDirectDisplayID`; sous cette version KFFI ne fabrique aucun fallback
heuristique et Kadre ne publie pas `Exclusive`. Toutes ses opérations sont
confinées au thread principal et retournent une failure fermée `WrongThread`
plutôt que d'effectuer un dispatch synchrone implicite. Ses snapshots et
résultats sont entièrement détachés : ils ne contiennent jamais un `NSScreen`,
une `NSWindow`, un `MemorySegment` ou une référence native. Le lease protège
aussi une même fenêtre contre une double ouverture et détecte une divergence de
présentation externe au lieu de l'écraser silencieusement.

À l'entrée, la snapshot de présentation est prise avant toute capture; après
l'ouverture du lease CoreGraphics et le changement de mode, KFFI appelle
`present(displayId)`, résout alors le frame courant de l'écran et relit les deux
leases. À la sortie et sur rollback, le broker termine d'abord le lease
CoreGraphics (restauration du mode puis libération de capture), puis KFFI tente
la restauration AppKit sous la topologie ainsi revenue. Une capture non
confirmée ne bloque pas cet essai, mais le résultat fermé distingue
`Restored`, `PartiallyRestored`, `TargetUnavailable` et `WindowGone`; chaque
appel idempotent retente uniquement les composants non restaurés. Les failures
de CoreGraphics et de présentation sont agrégées sans masquer la cause initiale.
`close()` ne rend jamais un échec de restauration inobservable : le dernier
résultat reste consultable via le lease. Cette présentation n'est pas un
`toggleFullScreen` et ne produit pas les callbacks AppKit de la phase 5.

Les propriétés publiques persistantes (`decorations`, tailles, `level`) restent
leurs valeurs configurées pendant l'exclusive, comme pendant `Borderless`.
`WindowState.fullscreen` est le seul état de présentation fullscreen ; la
snapshot privée garantit que la fenêtre normale est rétablie sans prétendre que
la géométrie publique AppKit est déjà portable sur les écrans mixed-scale.

## Admission, commit et succès

La précédence d'admission d'un update exclusif est :

1. fenêtre ouverte ;
2. forme valide (`Clear`, update mixte et toute transition impliquant
   `Exclusive` autre que `Windowed → Exclusive` ou `Exclusive → Windowed`
   rejetée) ;
3. `expectedRevision` ;
4. pour une entrée `Windowed → Exclusive`, présence de `Exclusive` dans la
   capability ; une sortie `Exclusive → Windowed` ne dépend jamais de cette
   capability ;
5. pour une entrée seulement, résolution fraîche du couple display/mode ; une
   sortie utilise son lease interne et ne dépend pas de l'inventaire public ;
6. absence de barrière fullscreen ou de fermeture ;
7. pour une entrée seulement, réservation process-wide ;
8. canonisation/no-op permis seulement pour les transitions non exclusives de
   la phase 5.

Toute demande `Set(Exclusive(...))` alors que l'état courant est déjà
`Exclusive` est rejetée à l'étape 2, avant résolution et réservation, même si
la cible est identique. Elle ne peut donc ni devenir un no-op ni réinstaller le
mode. La réconciliation de perte ramène préalablement l'état à `Windowed`.

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
caller. Aucun `Accepted` n'est renvoyé pour une mutation exclusive. Le résultat
KFFI `FailedAfterCapture` suit le même chemin qu'une failure du port de fenêtre
après commit : readback du state effectif, publication state puis event si ce
snapshot diffère, puis completion `PlatformFailure`.

Un échec avant capture libère la réservation et retourne la failure native.
Un échec après capture termine d'abord le lease CoreGraphics, puis tente de
restaurer la présentation AppKit et publie le snapshot réellement obtenu avant
de compléter par `PlatformFailure`. Si KFFI ne
confirme pas positivement la libération de capture, y compris avec un terminal
`Unknown`, le broker conserve l'entrée en `Quarantined`, retire `Exclusive` de
toutes les capabilities et refuse toute nouvelle entrée. Seul le même executor
AppKit peut retenter `release()` avec le `recovery` du token concerné, lors d'une
reconfiguration ultérieure ou du teardown process-wide. Une confirmation de
release fait passer l'entrée à `Released`; elle ne réactive `Exclusive` que si
l'observer et l'inventaire sont à nouveau certifiés. L'absence de succès ne
signifie jamais rollback implicite ; l'état et les événements restent la source
d'autorité.

## Sortie, fermeture et reconfiguration

`Windowed` depuis `Exclusive` est une transition terminale du lease : KFFI
tente d'abord de restaurer le mode initial puis de libérer la capture; le port
restaure ensuite la fenêtre sous la topologie obtenue et le broker libère la
réservation. Ensuite seulement Kadre publie le snapshot window autoritaire et
son événement corrélé. Un échec de restauration du mode display n'empêche pas
`fullscreen = Windowed` si la présentation AppKit a été lue et restaurée :
Kadre ne possède plus d'exclusive, et l'appel retourne la `PlatformFailure` de
nettoyage.

Un échec de restauration de présentation AppKit est traité séparément. Le port
relit style mask, system buttons, level et géométrie : les divergences publiques
représentables sont publiées avec `fullscreen = Windowed` et tous les
`WindowProperty` effectivement modifiés dans un seul événement. Si aucun
snapshot public honnête ne peut être relu, Kadre terminalise la fenêtre par le
chemin de fermeture native de phase 5 et complète par `PlatformFailure` ; il ne
publie jamais un `WindowState.Open` inventé. Si la capture n'est pas confirmée
libérée, le state de fenêtre résulte de ce même readback de présentation, mais
le broker est `Quarantined` et aucune autre fenêtre ne peut réclamer ce display.

La fermeture d'une fenêtre et le teardown de session utilisent le même chemin,
sans waiter à compléter. Les erreurs sont rapportées comme diagnostics. Aucun
peer ni session ne retient une capture après sa destruction ; si sa libération
n'est pas confirmée, seul le broker process-wide conserve le `recovery`
quarantiné du token jusqu'à confirmation de release ou terminaison du processus.

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
| O2 runtime | résolution opaque avec collision inter-session et mode complet divergent, précédence des failures, règle `Windowed` intermédiaire, retrait dynamique de capability et terminal d'un `Reserved`, state avant event avant completion, cancellation avant/après commit et perte de display. |
| O2 KFFI/AppKit fake | ordre capture → set mode → présentation ; restauration → release ; erreurs agrégées, readback `Unknown` et refs libérées ; identité I/O de mode et réordonnancement ; token stale/ABA ; reconfiguration auto-induite ; retrait de capability pendant un lease ; restauration AppKit divergente ou illisible ; arbitrage inter-session du broker. |
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
