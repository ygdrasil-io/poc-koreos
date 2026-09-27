# Registre versionné d’implémentation — adapter Web (`org.graphiks.kadre.platform.web`)

**Mandat.** `BACKEND-CAPABILITIES.md` §8 impose à chaque adapter de produire, avec son
implémentation, un fichier public `capabilities/<adapter>.md` contenant exactement une ligne par
feature de la matrice de sa section 4, avec les colonnes `feature`, `target`, `minimum déclaré`,
`compile gate`, `runtime gate`, `état absent` et `tests`.

**Première édition, et premier registre de ce dépôt.** Aucun adapter n’avait produit ce fichier
avant la phase 3 Web ; celui-ci est donc le premier du genre, et il n’existe pas de registre
antérieur à citer comme modèle. Il documente les features du **domaine input** que cette phase
active, borne ou rejette : `keyboard`, `pointer`, `touch`, `gestures`, `drag-and-drop`,
`IME`/`text input`, `raw input`, et les quatre champs de `SurfaceUpdate` que la même phase active ou
borne (`cursor`, `pointer capture`, `hit testing`, `inputDefaultBehavior`).

Deux remarques de périmètre, pour que la lecture des lignes soit exacte. Les trois premiers
features, `drag-and-drop`, `IME`/`text input` et `raw input` sont des lignes de la matrice de
section 4 ; les quatre champs de `SurfaceUpdate` n’en sont pas — la section 4 décrit les *cibles* de
capture (`capture target HostChoice`/`Source`/`Surface`) et `platformAccess`, pas les champs de mise
à jour de surface. Ces quatre champs figurent donc ici sous le format de la section 8 parce que
cette phase en committe ou en refuse chacun, et que la colonne `état absent` est précisément ce qui
doit être exact à leur sujet ; le registre ne prétend pas qu’ils sont des lignes de la matrice.

**Portée de lecture.** Les deux targets Web publient la même sémantique sous deux toolchains : le
target Kotlin/JS IR (`js`) et le target Kotlin/Wasm-JS (`wasmJs`), tous deux avec le type SDK
`org.w3c.dom.HTMLElement`. Le moteur de la preuve est le Chromium épinglé par le paquet Playwright
local, **148.0.7778.96** (`@playwright/test` **1.60.0**, relevé `execution.version` des documents
`kadre/contracts/driver/web/build/contract-evidence/<target>/contract-evidence/browser/chromium/BCK-003.json`).
Aucun autre moteur n’est déclaré, et un moteur non relevé ne se déduit pas d’un passage manuel
(`manual/phase-3-input.md`). Cette passe de preuve a été produite sur **macOS arm64**, qui est le
contexte d’exécution de la preuve et non un minimum : la gate CI obligatoire exécute les mêmes smoke
sur `ubuntu-latest` (`.github/workflows/kadre-web-contracts.yml:15`). Le seul minimum déclaré par ce
registre est donc la révision du navigateur ; l’OS n’y entre pas.

## 1. Lecture des colonnes

- **feature** — le nom du membre public, tel que le catalogue fermé le déclare
  (`InputCapabilities` de `SurfaceInput.kt:229`, `SurfaceCapabilities` de `Surface.kt:129`).
- **target** — `js` et `wasmJs`, c’est-à-dire le nom du champ `target` des documents de preuve ; les
  deux targets partagent le noyau de règle, la seule part distante étant la lecture DOM
  (`JsWebInputEvents.kt` / `WasmWebInputEvents.kt`).
- **minimum déclaré** — la version de navigateur réellement exercée : la révision Chromium épinglée
  ci-dessus par `@playwright/test` **1.60.0**, et rien d’autre. Kadre ne déclare aucune version de
  navigateur au-delà, et l’OS de la passe de preuve (macOS arm64) appartient à la « Portée de
  lecture » ci-dessus, pas à ce minimum.
- **compile gate** — le symbole ou le SDK dont la compilation dépend. `none` signifie qu’aucun
  symbole conditionnel n’est requis : le code compile sur les deux targets dès lors que les
  déclarations DOM de la toolchain existent.
- **runtime gate** — la permission, le protocole, le matériel, le secure context ou le focus dont
  dépend la *publication de la capability*. `none` signifie qu’aucun de ces éléments n’est requis :
  la capability décrit une installation structurelle, pas une éligibilité transitoire.
- **état absent** — la valeur exacte publiée quand la feature manque, sans paraphrase. Pour une
  feature **structurellement absente** (cet adapter ne l’implémente pas du tout et n’en implémentera
  pas dans cette phase), la colonne nomme la valeur que le code publie réellement — un
  `FeatureAvailability.Unsupported`, un `Capability.Unsupported(failure)` ou un rejet
  `Unsupported(UpdateSurface)` selon le type du champ — et jamais un état hypothétique : c’est une
  valeur lisible sur un snapshot vivant, pas une projection de ce que l’adapter ferait s’il
  l’implémentait (`BACKEND-CAPABILITIES.md` §2, `:27` : un champ passif typé `FeatureAvailability`
  utilise `FeatureAvailability.Unsupported` pour l’absence structurelle, et
  `FeatureAvailability.Unavailable` pour une disponibilité pilotée à runtime — sur le Web, ce second
  état n’est publié que par le bras **terminal** d’overflow d’ingress de la section 2.1, jamais par
  une indisponibilité transitoire). Le snapshot **terminal** de `SurfaceCapabilities`
  est le snapshot tout-`Unsupported` partagé (`unsupportedSurfaceCapabilities()`,
  `SurfaceAdmission.kt:20`), donc un champ cette phase active redevient `Unsupported(UpdateSurface)`
  dès que la surface cesse d’admettre. Les quatre capabilities d’**input** passives du registre
  (`keyboard`, `pointer`, `touch`, `dragAndDrop`) ont, elles, **deux** états absents : l’absence
  structurelle d’avant installation et l’indisponibilité du bras terminal d’overflow décrit en
  section 2.1 ; les trois autres lignes d’input (`gestures`, `textInput`, `rawInput`) n’en ont qu’un.
  Les deux valeurs sont écrites dans les lignes concernées plutôt
  que résumées par une seule, parce que §8 demande la valeur exacte et que « `Unsupported` » seul
  serait faux sur ce bras.
- **tests** — les identifiants de tests qui portent la ligne, en deux familles : les titres de
  scénarios de `kadre/contracts/driver/web/contracts/evidence.tsv`, c’est-à-dire des entrées réelles
  pilotées par Playwright sur le Chromium épinglé ; puis des classes hébergées par Karma
  (`jsBrowserTest` / `wasmJsBrowserTest`) — des tests unitaires exécutés *dans* le navigateur, et non
  des classes de preuve sans navigateur : `JsWebInputTest` patche par exemple
  `Element.prototype.releasePointerCapture` sur un élément Chromium vivant. Un identifiant de scénario
  est le titre du test Playwright qui le porte (`playwright/web-input.spec.mjs`). La gate CI
  obligatoire (`.github/workflows/kadre-web-contracts.yml:43`) n’exécute que
  `browserSmokeRunnerTest`, `jsBrowserSmoke`, `wasmJsBrowserSmoke` et `validator:check` : elle
  exécute donc les scénarios Playwright, tandis que les classes Karma ne sont exécutées que par
  `:kadre:check`, jamais par cette gate.

## 2. Registre

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| `InputCapabilities.keyboard` | `js`, `wasmJs` | Chromium 148.0.7778.96, `@playwright/test` 1.60.0 | `js` : `org.w3c.dom.events.KeyboardEvent` des déclarations DOM de la stdlib-js ; `wasmJs` : `org.w3c.dom.HTMLElement` des déclarations DOM Kotlin/Wasm plus `external interface` et le test `instanceof` par `@JsFun` | `none` — l’installation est structurelle ; recevoir une frappe dépend en plus du focus de l’élément, responsabilité du host (D7) | `FeatureAvailability.Unsupported` (snapshot d’avant installation, `RuntimeSurfaceInput.kt:73` et `:1455-1467`) ; `FeatureAvailability.Unavailable(SourceOverflow(InputSource))` sur le bras terminal d’overflow (section 2.1) | `web-input-key-state-before-event`, `web-input-key-modifiers`, sentinelle `web-input-no-parallel-reducer` ; `JsWebInputTest`/`WasmWebInputTest`, `WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation`, `WebInputMappingTest` |
| `InputCapabilities.pointer` | `js`, `wasmJs` | idem | `js` : `org.w3c.dom.pointerevents.PointerEvent` ; `wasmJs` : `WasmPointerEvent` en `external interface JsAny` et `instanceof` par `@JsFun` | `none` — listeners posés sur l’élément attaché à l’installation ; la position publiée est relative à la surface | `FeatureAvailability.Unsupported` (même snapshot d’avant installation) ; `FeatureAvailability.Unavailable(SourceOverflow(InputSource))` sur le bras terminal d’overflow (section 2.1) | `web-input-pointer-primary`, `web-input-pointer-multi`, `web-input-pointer-cancel`, sentinelle `web-input-no-stuck-button` ; `JsWebInputTest`/`WasmWebInputTest`, `WebInputTrackingTest` |
| `InputCapabilities.touch` | `js`, `wasmJs` | idem | `org.w3c.dom.pointerevents.PointerEvent` — le port refuse `pointerType == "touch"` avant toute lecture, sans second chemin | `none` — aucun observer de touch n’est installé (`touchInstalled = false`, `WebHostSession.kt:615`) | `FeatureAvailability.Unsupported` ; `FeatureAvailability.Unavailable(SourceOverflow(InputSource))` sur le bras terminal d’overflow (section 2.1) — jamais `Available` | `web-input-touch-deferred`, sentinelle `web-input-no-touch-claim` ; `JsWebInputTest.aTouchPointerProducesNoStimulusAtAll`, `WebInputMappingTest.theDeliveredPointerKindsAreTheMouseAndThePenAndTouchIsRefused` |
| `InputCapabilities.gestures` | `js`, `wasmJs` | idem | `none` | `none` — `gestureKinds = emptySet()` (`WebHostSession.kt:616`) | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GestureInput))`, y compris sur le bras terminal d’overflow, qui ne transforme pas un `Capability.Unsupported` en `Unavailable` (`RuntimeSurfaceInput.kt:1476-1479`) | `web-input-touch-deferred` (l’attribut `data-kadre-input-caps` vaut `gestures=unsupported:gestureinput`, assertion partagée par les douze scénarios) ; `WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation` |
| `InputCapabilities.dragAndDrop` | `js`, `wasmJs` | idem | `none` | `none` — aucun listener de drag n’existe dans les deux ports | `FeatureAvailability.Unsupported` (`dragAndDropAvailable = false`, `WebHostSession.kt:585`) ; `FeatureAvailability.Unavailable(SourceOverflow(InputSource))` sur le bras terminal d’overflow (section 2.1) | `WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation` (seul porteur : aucun scénario navigateur ne l’observe dans cette phase) |
| `InputCapabilities.textInput` | `js`, `wasmJs` | idem | `none` | `none` — `UnsupportedTextInputPort` est la seule implémentation câblée (`WebHostSession.kt:580`) | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput))` (`TextInputPort.kt:91`), inchangé sur le bras terminal d’overflow (`RuntimeSurfaceInput.kt:1481`) | `WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation` |
| `InputCapabilities.rawInput` | `js`, `wasmJs` | idem | `none` | `none` — `rawInputCoordinator = null` et la capability sont posés au même endroit (`WebHostSession.kt:583-584`) | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess))`, inchangé sur le bras terminal d’overflow (`RuntimeSurfaceInput.kt:1482`) | `WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation` |
| `SurfaceCapabilities.cursor` et `SurfaceCapabilities.customCursor` | `js`, `wasmJs` | idem | `none` | `none` — aucun chemin de commit n’existe et `clear` est refusé avant admission (`WebHostSession.kt:1053`) | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))` : snapshot attaché par `webSurfaceCapabilities()` (`WebHostSession.kt:1273`, `:1274`), snapshot terminal par `unsupportedSurfaceCapabilities()` (`SurfaceAdmission.kt:21-22`, publié à `WebHostSession.kt:1139`) | `WebInputSurfaceTest.theInputDefaultBehaviorCapabilityIsTheWholeEnumAndTheOtherFieldsStayUnsupported` (snapshot attaché, `:595-597`) ; le porteur du snapshot terminal est `unsupportedSurfaceCapabilities()` (`SurfaceAdmission.kt:20-29`), dont la même règle est asservie pour un autre champ par le même test (`:606-610`) |
| `SurfaceCapabilities.pointerCapture` | `js`, `wasmJs` | idem | `none` | `none` — `Confined` exige en plus un pointeur que la surface détient déjà, sinon rejet `InteractionRequired(Missing)` | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))` au snapshot terminal (la surface attachée publie `Supported({None, Confined}, Available)`) | `web-input-pointer-capture` ; `JsWebPointerCaptureTest`/`WasmWebPointerCaptureTest`, `WebInputSurfaceTest.thePointerCaptureCapabilityIsNoneAndConfinedAndLockedIsProvablyOutside` |
| `SurfaceCapabilities.hitTesting` | `js`, `wasmJs` | idem | `none` | `none` — aucun chemin de commit | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))` | `WebInputSurfaceTest.theInputDefaultBehaviorCapabilityIsTheWholeEnumAndTheOtherFieldsStayUnsupported` |
| `SurfaceCapabilities.inputDefaultBehavior` | `js`, `wasmJs` | idem | `none` | `none` — les deux membres sont honorés dès l’installation ; la réponse est relue à chaque événement | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))` au snapshot terminal (la surface attachée publie `Supported({HostDefault, SuppressWhenPossible}, Available)`) | `web-input-default-behavior`, sentinelle `web-input-no-default-suppression` ; `JsWebInputDefaultBehaviorTest`/`WasmWebInputDefaultBehaviorTest`, `WebInputSurfaceTest.hostDefaultSuppressesNoCategoryAtAll`, `.suppressWhenPossibleSuppressesExactlyTheClosedSet`, `.theSurfaceAnswersSuppressionForTheEventItWasJustHanded` |

Les deux availabilities d’entrée sont déclarées par une seule transition structurelle, à la fin de
`installSessionConfiguration` (`WebHostSession.kt:610-618`) : `keyboardInstalled = true`,
`pointerInstalled = true`, `touchInstalled = false`, `gestureKinds = emptySet()`. Rien de tout cela
n’est déclaré plus tôt, et aucun stimulus d’entrée ne peut déclarer une capability — l’union
`WebInputStimulus` n’a pas de membre pour le dire, ce que
`WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation` vérifie
après avoir livré une frappe et une entrée de pointeur.

Aux quatre sentinelles rattachées à une ligne ci-dessus s’ajoute la cinquième du contrat,
`web-input-post-terminal-stimulus`, qui garde la lane d’une surface terminale : l’input réel livré
après la fermeture ne publie ni état, ni événement, ni reset (`web-input-terminal-closed`).

### 2.1 Le bras terminal d’overflow : la seconde valeur d’absence des capabilities passives

Les quatre capabilities passives du registre (`keyboard`, `pointer`, `touch`, `dragAndDrop`) ne
valent pas `Unsupported` dans tous les états. Sur le **bras terminal d’overflow d’ingress**, le
reducer publie `FeatureAvailability.Unavailable(SourceOverflow(InputSource))` — une indisponibilité
observée, pas une absence structurelle, exactement la distinction que `BACKEND-CAPABILITIES.md` §2
(`:27`) fait entre les deux formes. Le bras est atteignable sous `KadrePolicies.Default` :

1. `KadrePolicies.Default` donne `discreteCapacity = 256` et `eventIngress =
   IngressOverflowAction.CloseSource` (`KadrePolicies.kt:6-29`), et le constructeur de profil en
   forme l’`EventDeliveryPolicy` des événements discrets, celui que `InputDeliveryPolicy` reçoit
   aussi (`KadrePolicies.kt:108-120`) ;
2. le dépassement de cette capacité produit `QueueOfferResult.DiscreteOverflow`, que
   `enqueuePublicationLocked` termine par `terminaliseLocked(failure =
   KadreFailure.SourceOverflow(KadreResourceKind.InputSource), failSession =
   deliveryPolicy.discreteEvents.ingressOverflow == IngressOverflowAction.FailSession)`
   (`RuntimeSurfaceInput.kt:840-842`) — avec `CloseSource`, `failSession` vaut `false` : la **lane
   d’input** est terminalisée, la session ne l’est pas ;
3. `terminaliseLocked` publie le snapshot neutre dont la composition de capabilities est
   `unavailableInputCapabilities(currentState.capabilities, failure)`
   (`RuntimeSurfaceInput.kt:862-878`), et cette fonction pose `keyboard = pointer = touch =
   dragAndDrop = FeatureAvailability.Unavailable(failure)` (`RuntimeSurfaceInput.kt:1472-1475`,
   `:1480`) ;
4. le même chemin est atteint par un profil à livraison continue `Buffered(…, CloseSource)`, qui
   produit `ContinuousOverflowAction.CloseSource` avec le même `SourceOverflow(InputSource)`
   (`RuntimeSurfaceInput.kt:849-852`).

Deux conséquences à ne pas confondre, et qui sont la raison pour laquelle la colonne `état absent`
porte deux valeurs : `gestures` **ne** devient pas `Unavailable` sur ce bras — un
`Capability.Unsupported` y reste inchangé (`RuntimeSurfaceInput.kt:1476-1479`) — et `textInput` et
`rawInput` y sont re-posés en `Capability.Unsupported` (`:1481-1482`), donc leurs lignes n’ont
qu’une valeur. L’adapter Web est concerné par ce bras comme tout hôte acceptant
`KadrePolicies.Default`, et le contrat `BCK-003` ne le prouve pas en navigateur : il est décrit ici
parce que §8 demande la valeur exacte, et sa réserve est celle de la section 4.

Ce bras terminalise la **lane** d’input, jamais la surface : `terminaliseLocked(failSession = false)`
laisse la session et la surface vivantes, et le champ `SurfaceCapabilities.inputDefaultBehavior`
qu’elles publient reste celui du snapshot attaché — `Supported({HostDefault, SuppressWhenPossible},
Available)`. `WebHostSession.suppressDefaultFor` ne lit en effet que la fermeture d’admission et ce
champ (`WebHostSession.kt:1039-1041`) : la suppression peut donc encore être accordée pour une lane
qui ne livre plus rien, jusqu’à ce que la surface elle-même devienne terminale.

La colonne `runtime gate` est à `none` pour toutes les lignes : aucune feature ne dépend d’une
permission, d’un secure context, d’un protocole ou d’un matériel, et ce qui dépend d’un élément
focusable est le *focus*, une responsabilité du host (section 3.1 ci-dessous) et non un gate que
Kadre évaluerait. La colonne `compile gate` n’est renseignée que là où un symbole DOM est
effectivement lu — `keyboard`, `pointer` et `touch` — et vaut `none` pour les features dont le code
ne lit aucun type DOM.

## 3. Faits établis par cette phase, qui ne vivaient que dans le code ou dans `DESIGN.md` §15.3

### 3.1 Responsabilité du host : les listeners sont sur l’élément, et lui seul

Les dix listeners (`keydown`, `keyup`, `pointerenter`, `pointermove`, `pointerdown`, `pointerup`,
`pointerleave`, `pointercancel`, `lostpointercapture`, `wheel`) sont posés sur l’élément attaché et
sur aucun `Document`, aucune `Window` et aucun ancêtre (`JsWebDomPort.kt:255-264`,
`WasmWebDomPort.kt:256-265`). Rendre l’élément focusable est la responsabilité du host : Kadre
n’écrit aucun `tabindex` et ne modifie jamais le DOM du host. Le fixture de preuve fournit
lui-même `host.tabIndex = 0` (`WebDriverFixture.kt:630`). **Un host qui n’a pas rendu son élément
focusable reçoit `keyboard = available` et aucune frappe** : la capability décrit l’installation des
listeners, pas l’arrivée d’un événement (`DESIGN.md` §15.3, D7).

### 3.2 L’ensemble fermé des catégories supprimées

Sous `InputDefaultBehavior.SuppressWhenPossible`, l’ensemble des défauts navigateur supprimés est
**fermé** et écrit membre par membre (`WebInputTranslation.kt:130-133`) :

```kotlin
internal val SUPPRESSED_INPUT_DEFAULTS: Set<WebInputCategory> = setOf(
    WebInputCategory.Wheel,
    WebInputCategory.ScrollingKey,
)
```

`WebInputCategory.ScrollingKey` est exactement la **pression** (`KeyState.Pressed`) d’une touche de
la liste fermée des clés de défilement documentaire (`WebInputTranslation.kt:161-171`) :

```kotlin
private val WEB_DOCUMENT_SCROLL_KEYS: Set<NamedKey> = setOf(
    NamedKey.ArrowUp,
    NamedKey.ArrowDown,
    NamedKey.ArrowLeft,
    NamedKey.ArrowRight,
    NamedKey.PageUp,
    NamedKey.PageDown,
    NamedKey.Home,
    NamedKey.End,
    NamedKey.Space,
)
```

soit le wheel plus les **neuf** touches de défilement documentaire : les quatre flèches, les deux
touches de page, `Home`, `End` et la barre d’espace.

**Le test d’appartenance porte sur la clé logique, pas sur la clé physique.** La dérivation de
catégorie exige `stimulus.keyState == KeyState.Pressed && stimulus.logicalKey is LogicalKey.Named &&
stimulus.logicalKey.value in WEB_DOCUMENT_SCROLL_KEYS` (`WebInputTranslation.kt:189-193`) : c’est le
`NamedKey` que le layout a produit qui est comparé, jamais le `PhysicalKey` ni le `code` du DOM. Le
code le dit et en donne la raison (`WebInputTranslation.kt:141-144`) : « the browser’s default is
defined by the key the layout produced, not by where the key sits: the same physical key is not a
document-scroll key on every layout, and the logical key is what the browser itself acts on ». La
suppression suit donc le défaut du navigateur là où il se définit — sur la clé logique —, ce qui est
aussi ce qui la rend indépendante du mapping `code`→HID : `webPhysicalKey` lit le `code`
(`WebInputMapping.kt:52-55`, ce que la procédure 4 de la charte manuelle vérifie sur un layout
non-US), tandis que la suppression lit `event.key`. Un adapter qui déciderait de supprimer d’après la
clé physique supprimerait le défaut d’une autre touche sur un layout non-US.

Aucune autre catégorie n’est supprimée : les
défauts de `Tab`, `Enter`, `Escape`, `Backspace`, des touches de caractère, des touches de fonction,
des combinaisons de chrome navigateur et du pointeur restent ceux du navigateur. Sous
`HostDefault`, la réponse est `false` pour toute catégorie, y compris les deux ci-dessus
(`WebInputTranslation.kt:222-226`). `webTest` épingle l’ensemble contre l’énumération complète des
catégories dans les deux sens (`WebInputSurfaceTest.suppressWhenPossibleSuppressesExactlyTheClosedSet`,
`.aKeyPressIsAScrollingKeyOnlyForTheClosedListOfDocumentScrollKeys`). La suppression n’est jamais
globale : `preventDefault` n’est appelé que dans le callback de l’événement en main et sur lui
(`DESIGN.md` §15.3).

### 3.3 Divergence de capture sur le bras « perte d’activation », reprise mot pour mot

Le commentaire de `WebHostSession.kt:1013-1016` enregistre la divergence sous la forme citable
suivante, reprise ici **verbatim** :

> after a loss of activation the web surface publishes `SurfaceState.pointerCapture = None` and does not call `releasePointerCapture`; Chromium may still hold the mouse capture until the next press, so `None` must be read as "this surface claims no capture", never as "the browser holds none".

Les trois autres bras d’un pointeur — un relâchement de bouton, un `pointerleave`, un
`pointercancel` — voient le navigateur terminer la capture implicitement avec le pointeur auquel
elle appartenait, donc le DOM et l’état publié s’accordent (`WebHostSession.kt:1004-1009`). La
divergence est unidirectionnelle et sûre : la surface réclame *moins* que le navigateur, la
confinement qu’elle cesse d’annoncer ne peut pas être repris sans une nouvelle pression (l’ownership
est parti avec l’activation), et aucune opération n’est autorisée par la différence. Les deux moitiés
sont asservies par le scénario `web-input-pointer-capture`.

### 3.4 Frontière de coalescence par frame, et la raison de sa substitution à la phase native

La conception de référence fixe la frontière de coalescence d’un scroll à la **phase native** et au
**momentum** : deux phases distinctes ne fusionnent jamais, et un scroll de momentum ne fusionne
jamais avec un scroll qui ne l’est pas
(`APPKIT-PHASE-4-INPUT-DESIGN.md:93-100`). Le DOM n’expose ni phase ni momentum ; la règle Web
substitue donc à ces deux faits l’unité de livraison réelle du navigateur, la **frame d’animation**
(`WebInputTracking.kt:6-23`) :

```kotlin
fun advance(deltaMode: Int, buttons: Int): Long {
    val opens = frameIsNew || this.deltaMode != deltaMode || this.buttons != buttons
    ...
}
```

La frontière s’ouvre donc (a) pour le premier wheel qu’une nouvelle animation frame livre
(`frameOpened()` est appelé par le marqueur de frame du port, `JsWebDomPort.kt:74`,
`WasmWebDomPort.kt:76`), (b) quand le `deltaMode` change — l’unité de mesure du navigateur — et
(c) quand l’état des boutons du pointeur change. Tout autre wheel réutilise la frontière du wheel
précédent. Deux scrolls de frontière égale peuvent donc fusionner (le runtime additionne leurs
deltas), deux frontières distinctes ne fusionnent **jamais**, et aucune séparation livrée par le
navigateur n’est perdue silencieusement. La frontière est un fait de coalescence : elle n’est jamais
publique et n’apparaît pas dans `SurfaceInput.state`. Elle est vérifiée sans navigateur
(`WebInputTrackingTest.theWheelsOfOneFrameShareAFrontierAndTheFirstWheelOfTheNextOneOpensAnother`,
`.aChangeOfUnitOrOfButtonStateOpensAFrontierOfItsOwn`,
`.aFrontierIsNeverHandedOutTwiceWhateverThePortObservedInBetween`) et avec un vrai événement
(`JsWebInputTest.theScrollFrontierSeparatesFramesUnitsAndButtonStates`, idem Wasm).

### 3.5 Statut différé de `cursor`, `customCursor` et `hitTesting`

Ces trois champs restent `Unsupported(UpdateSurface)` **après** cette phase, exactement comme avant :
`webSurfaceCapabilities()` publie `unsupportedSurfaceCapability(KadreOperation.UpdateSurface)` pour
`cursor`, `customCursor` et `hitTesting` (`WebHostSession.kt:1273`, `:1274`, `:1279`), et la phase
n’active que `pointerCapture` et `inputDefaultBehavior`. Un champ déclaré `Unsupported` est refusé
avant tout commit (`admitField`), et tout champ committable arrivé sans chemin de commit ferait
échouer une vérification plutôt que d’être acquitté
(`WebHostSession.kt:1333-1337`).

### 3.6 Deux nuances de la réconciliation

1. **Une capture perdue déplace la révision sans publier de `SurfaceEvent`.**
`reconcilePointerCapture` écrit un `SurfaceState` dont `pointerCapture` vaut `None` et dont la
révision est incrémentée de 1 (`WebHostSession.kt:1018-1025`) ; il ne publie aucun événement, et le
modèle n’en a pas pour un champ committé — `SurfaceEvent` ne déclare que `MetricsChanged`,
`FocusChanged`, `VisibilityChanged`, `AppearanceChanged` et `RedrawRequested`
(`Surface.kt:77-100`). Une capture perdue s’observe donc sur `SurfaceState` (et sur sa révision),
jamais dans le flux d’événements de surface.
2. **`Locked` reste l’affaire de la couche interaction.** Le Pointer Lock API exige une *transient
   user activation* et appartient à `InteractionAction.LockPointer` (`DESIGN.md` §9.6) : aucun membre
   de cette cible ne verrouille un pointeur. Une demande `PointerCaptureMode.Locked` est refusée
   comme champ rejeté `Unsupported(UpdateSurface)` — elle ne devient jamais une valeur committée de
   `SurfaceUpdate` — et le fixture le constate (`kadre-capture-locked`,
   `WebInputSurfaceTest.lockedIsRefusedAsAFieldAndNeverReachesThePort`). La règle est écrite comme
   une fonction exhaustive sur l’énumération (`webPointerCaptureIsHonourable`,
   `WebInputTranslation.kt:243-246`), épinglée contre `PointerCaptureMode.entries` dans les deux sens.

## 4. Portée restante au regard de §8

Ce registre couvre les features du domaine input que la phase 3 borne ou active, plus les quatre
champs de `SurfaceUpdate`. Les autres lignes de la matrice de `BACKEND-CAPABILITIES.md` §4 —
`gamepad observation`, `effets gamepad`, les trois cibles de capture (`HostChoice`, `Source`,
`Surface`) et les deux lignes `platformAccess` — ne sont pas produites ici : cette phase n’en active
aucune et rien dans ce document ne les modifie. §8 rappelle qu’« une ligne manquante empêche
l’adapter d’être déclaré supporté » : ces lignes restent donc à produire avant toute déclaration
« supported » de l’adapter Web, sans que cette phase les rouvre.

## 5. Références

- [Contrats des adapters et matrice de capabilities](../BACKEND-CAPABILITIES.md) — mandat §8, matrice §4, points d’attachement Web §6.3
- [Design Kadre](../DESIGN.md) — §15.3 (input ordinaire Web), §9.6 (interactions transitoires)
- [Registre des contrats](../contracts/registry/contracts.tsv) — ligne `BCK-003` (source `DESIGN.md#15.3`, preuves `js`/`wasmJs`)
- [Charte manuelle Web Phase 3](../contracts/driver/web/manual/phase-3-input.md) — frontières non déterministes que ce registre ne prétend pas couvrir
- [Driver navigateur Web](../contracts/driver/web/README.md) — limites de la phase 3 et table de disponibilité publiée
