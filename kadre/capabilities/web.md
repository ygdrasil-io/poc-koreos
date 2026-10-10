# Registre versionné d’implémentation — adapter Web (`org.graphiks.kadre.platform.web`)

**Mandat.** `BACKEND-CAPABILITIES.md` §8 impose à chaque adapter de produire, avec son
implémentation, un fichier public `capabilities/<adapter>.md` contenant exactement une ligne par
feature de la matrice de sa section 4, avec les colonnes `feature`, `target`, `minimum déclaré`,
`compile gate`, `runtime gate`, `état absent` et `tests`.

**Première édition, et premier registre de ce dépôt.** Aucun adapter n’avait produit ce fichier
avant la phase 3 Web ; celui-ci est donc le premier du genre, et il n’existe pas de registre
antérieur à citer comme modèle. Sa première édition documentait les features du **domaine input**
que la phase 3 active, borne ou rejette : `keyboard`, `pointer`, `touch`, `gestures`,
`drag-and-drop`, `IME`/`text input`, `raw input`, et les quatre champs de `SurfaceUpdate` que la
même phase active ou borne (`cursor`, `pointer capture`, `hit testing`, `inputDefaultBehavior`).
La phase 4 étend le registre aux features d’**interaction** et de **fenêtre** qu’elle livre :
les deux capabilities d’interaction de `SurfaceCapabilities` (`handlerInteractions`,
`armedInteractions`), la capability de fenêtre `WindowManagerCapabilities.requestWindow` et
l’échelle d’admission de `InteractionContext.request` (les quatre lignes du bas du §2).
La phase 5 réécrit les trois lignes d’input que ses contrats livrent — `touch` sur les pointer
events (`BCK-004`), `textInput` sur un port à document-shadow (`BCK-005`), `dragAndDrop` sur le
seam de drop synchrone (`BCK-006`) — et réécrit la ligne `gestures`, dont l’absence de
recognizer devient une limite enregistrée plutôt qu’un différé de phase (§2, §3.9-3.11).
La phase 6 ajoute les quatre lignes du bas que ses contrats livrent : l’inventaire d’affichage
dont le fallback `HostViewport` est la forme exacte et inconditionnelle (`BCK-007`),
l’inventaire devices/gamepads sondé depuis le poll du navigateur (`BCK-008`), les effets
gamepad et leurs préconditions honnêtes (`BCK-009`), et la ligne `platformAccess` de surface,
dont la lease `withWebElement` de la phase 2 est la preuve (§2, quatre lignes du bas ;
§3.12-3.14) — la ligne `rawInput`, déjà présente, gagne son scénario navigateur de non-appel.
La phase 7 ajoute les cinq lignes de **capture** que ses contrats livrent : les cibles
`HostChoice` (le picker du navigateur derrière un consentement explicite) et `Surface` (le
canvas de la surface primaire, sans consentement), la cible `Source` refusée structurellement
avant tout picker, et les deux capabilities d’inventaire qui disent ce que ce browsing context
sait — `sourceEnumeration` inconditionnelle sur un inventaire toujours `HostPickerOnly`,
`hostPicker` pilotée par le secure context et la présence de la primitive (`BCK-010`/`BCK-011` ;
§2, cinq lignes du bas ; §3.15-3.16).

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
| `InputCapabilities.keyboard` | `js`, `wasmJs` | Chromium 148.0.7778.96, `@playwright/test` 1.60.0 | `js` : `org.w3c.dom.events.KeyboardEvent` des déclarations DOM de la stdlib-js ; `wasmJs` : `org.w3c.dom.HTMLElement` des déclarations DOM Kotlin/Wasm plus `external interface` et le test `instanceof` par `@JsFun` | `none` — l’installation est structurelle ; recevoir une frappe dépend en plus du focus de l’élément, responsabilité du host (D7) | `FeatureAvailability.Unsupported` (snapshot d’avant installation, `RuntimeSurfaceInput.kt:71-75` et `:1455-1469`) ; `FeatureAvailability.Unavailable(SourceOverflow(InputSource))` sur le bras terminal d’overflow (section 2.1) | `web-input-key-state-before-event`, `web-input-key-modifiers`, sentinelle `web-input-no-parallel-reducer` ; `JsWebInputTest`/`WasmWebInputTest`, `WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation`, `WebInputMappingTest` |
| `InputCapabilities.pointer` | `js`, `wasmJs` | idem | `js` : `org.w3c.dom.pointerevents.PointerEvent` ; `wasmJs` : `WasmPointerEvent` en `external interface JsAny` et `instanceof` par `@JsFun` | `none` — listeners posés sur l’élément attaché à l’installation ; la position publiée est relative à la surface | `FeatureAvailability.Unsupported` (même snapshot d’avant installation) ; `FeatureAvailability.Unavailable(SourceOverflow(InputSource))` sur le bras terminal d’overflow (section 2.1) | `web-input-pointer-primary`, `web-input-pointer-multi`, `web-input-pointer-cancel`, sentinelle `web-input-no-stuck-button` ; `JsWebInputTest`/`WasmWebInputTest`, `WebInputTrackingTest` |
| `InputCapabilities.touch` | `js`, `wasmJs` | idem | `js` : `org.w3c.dom.pointerevents.PointerEvent` ; `wasmJs` : `WasmPointerEvent` en `external interface JsAny` — la même lecture que le pointeur, un contact étant livré par les tout mêmes listeners | `none` — les contacts tactiles empruntent les listeners `pointerdown`/`pointermove`/`pointerup`/`pointercancel` posés à l’attach ; aucun listener `touchstart`/`touchmove`/`touchend` n’existe, et le routage est celui de `webTouchPhase` (`WebInputMapping.kt:355-361`) | `FeatureAvailability.Unsupported` (snapshot d’avant installation, `RuntimeSurfaceInput.kt:1455-1469`) ; `FeatureAvailability.Unavailable(SourceOverflow(InputSource))` sur le bras terminal d’overflow (section 2.1) | `web-touch-started`, `web-touch-multi-contact`, `web-touch-ended`, `web-touch-cancelled`, `web-touch-focus-loss-clears`, `web-touch-no-pointer-alias`, sentinelle `web-touch-identity-stable` ; `JsWebInputTest`/`WasmWebInputTest` (touch livré), `WebInputMappingTest` |
| `InputCapabilities.gestures` | `js`, `wasmJs` | idem | `none` | `none` — `gestureKinds = emptySet()` dans l’observation même qui déclare le touch (`WebHostSession.kt:912-925`) ; aucun recognizer n’existe sur ce target (D-T2), limite enregistrée et non un différé | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GestureInput))`, y compris sur le bras terminal d’overflow, qui ne transforme pas un `Capability.Unsupported` en `Unavailable` (`RuntimeSurfaceInput.kt:1478-1482`) | `web-touch-capability-structural` (l’attribut `data-kadre-input-caps` vaut `gestures=unsupported:gestureinput`, cellule publique entière réassertée par chaque scénario de `web-touch.spec.mjs`, `web-drop.spec.mjs` et `web-text-input.spec.mjs`), sentinelle `web-touch-no-gesture-claim` ; `WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation` |
| `InputCapabilities.dragAndDrop` | `js`, `wasmJs` | idem | `none` | `none` — les listeners `dragenter`/`dragover`/`dragleave`/`drop` sont posés sur l’élément attaché à l’installation (`JsWebDomPort.kt:402-405`, `WasmWebDomPort.kt:412-415`), et l’offre qu’un `dragenter` présente l’est par le seam synchrone `onDropEntered` (§3.11) | `FeatureAvailability.Unsupported` (snapshot d’avant installation, `RuntimeSurfaceInput.kt:1455-1469` — `dragAndDropAvailable = true` est passé au reducer à la même installation structurelle, `WebHostSession.kt:873`) ; `FeatureAvailability.Unavailable(SourceOverflow(InputSource))` sur le bras terminal d’overflow (section 2.1) | `web-drop-presented-single-active`, `web-drop-accept-synchronous`, `web-drop-rejected-without-handler`, `web-drop-moved-after-accept`, `web-drop-exit-terminates`, `web-drop-performed-claimable`, `web-drop-claim-single-winner`, `web-drop-read-bounded-copy`, `web-drop-teardown-closes`, `web-drop-no-data-transfer-leak`, sentinelles `web-drop-no-partial-offer`, `web-drop-no-fabricated-mime`, `web-drop-no-prevent-default-without-offer` ; `WebDropSurfaceTest`, `WebDropMappingTest`, `JsWebDropTest`/`WasmWebDropTest` |
| `InputCapabilities.textInput` | `js`, `wasmJs` | idem | `none` | `none` — `WebTextInputPort` est construit par la session configuration (`WebHostSession.kt:865`) et sa capability est structurelle (`Capability.Supported`, `WebTextInputPort.kt:114`, D-X2) : l’éditabilité de l’élément est la frontière du host, et un élément hors du périmètre v1 (`<input>`/`<textarea>`, D-X3) ouvre des sessions qui n’observent rien (§3.10) | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput))` : l’état par défaut d’avant installation (`TextInputPort.kt:91`, composé par `unsupportedInputState`, `RuntimeSurfaceInput.kt:1442-1453` — l’`input` du surface n’est lisible qu’à partir de la construction du reducer, `WebHostSession.kt:856-885`) et la re-position sur le bras terminal d’overflow (`RuntimeSurfaceInput.kt:1481`) ; une seconde ouverture pendant une session vivante est `AlreadyInUse(TextInputSession)` (`RuntimeSurfaceInput.kt:119-134`, `WebTextInputPort.kt:120`) | `web-text-open-single-session`, `web-text-replace-event`, `web-text-composition-lifecycle`, `web-text-composition-cancelled`, `web-text-focus-suspends`, `web-text-stale-revision`, `web-text-writeback-sync`, `web-text-action-submit`, `web-text-teardown-closes`, sentinelles `web-text-no-dom-leak`, `web-text-anti-stale-revision`, `web-text-no-content-synthesis`, `web-text-no-implicit-session` ; `WebTextInputSurfaceTest`, `JsWebTextInputTest`/`WasmWebTextInputTest` |
| `InputCapabilities.rawInput` | `js`, `wasmJs` | idem | `none` | `none` — `rawInputCoordinator = null` et la capability sont posés au même endroit (`WebHostSession.kt:868-869`) ; la requête `requestRawInput` est refusée avant d’installer le moindre listener — le scénario navigateur compte les registrations `EventTarget` de la page autour de la requête et le delta est zéro | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess))`, inchangé sur le bras terminal d’overflow (`RuntimeSurfaceInput.kt:1482`) | `web-gamepad-raw-input-unsupported`, sentinelle `web-gamepad-raw-no-listener` (BCK-009) ; `WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation` |
| `SurfaceCapabilities.cursor` et `SurfaceCapabilities.customCursor` | `js`, `wasmJs` | idem | `none` | `none` — aucun chemin de commit n’existe et `clear` est refusé avant admission (`WebHostSession.kt:1714-1721`) | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))` : snapshot attaché par `webSurfaceCapabilities()` (`WebHostSession.kt:1984`, `:1985`), snapshot terminal par `unsupportedSurfaceCapabilities()` (`SurfaceAdmission.kt:21-22`, publié à `WebHostSession.kt:1772` et `:1810`) | `WebInputSurfaceTest.theInputDefaultBehaviorCapabilityIsTheWholeEnumAndTheOtherFieldsStayUnsupported` (snapshot attaché, `:595-597`) ; le porteur du snapshot terminal est `unsupportedSurfaceCapabilities()` (`SurfaceAdmission.kt:20-29`), dont la même règle est asservie pour un autre champ par le même test (`:606-610`) |
| `SurfaceCapabilities.pointerCapture` | `js`, `wasmJs` | idem | `none` | `none` — `Confined` exige en plus un pointeur que la surface détient déjà, sinon rejet `InteractionRequired(Missing)` | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))` au snapshot terminal (la surface attachée publie `Supported({None, Confined}, Available)`) | `web-input-pointer-capture` ; `JsWebPointerCaptureTest`/`WasmWebPointerCaptureTest`, `WebInputSurfaceTest.thePointerCaptureCapabilityIsNoneAndConfinedAndLockedIsProvablyOutside` |
| `SurfaceCapabilities.hitTesting` | `js`, `wasmJs` | idem | `none` | `none` — aucun chemin de commit | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))` | `WebInputSurfaceTest.theInputDefaultBehaviorCapabilityIsTheWholeEnumAndTheOtherFieldsStayUnsupported` |
| `SurfaceCapabilities.inputDefaultBehavior` | `js`, `wasmJs` | idem | `none` | `none` — les deux membres sont honorés dès l’installation ; la réponse est relue à chaque événement | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))` au snapshot terminal (la surface attachée publie `Supported({HostDefault, SuppressWhenPossible}, Available)`) | `web-input-default-behavior`, sentinelle `web-input-no-default-suppression` ; `JsWebInputDefaultBehaviorTest`/`WasmWebInputDefaultBehaviorTest`, `WebInputSurfaceTest.hostDefaultSuppressesNoCategoryAtAll`, `.suppressWhenPossibleSuppressesExactlyTheClosedSet`, `.theSurfaceAnswersSuppressionForTheEventItWasJustHanded` |
| `SurfaceCapabilities.handlerInteractions` | `js`, `wasmJs` | idem | `none` pour la capability elle-même, décidée en `webMain` (`interactionActionsForWeb()`, `WebInteractionStimulus.kt:32-38`) ; les symboles DOM que les quatre actions à primitive conduisent sont les membres fullscreen des déclarations DOM de la toolchain et la lacune pointer-lock que chaque port déclare lui-même (`JsPointerLockRequester`/`JsDocumentPointerLock`, `JsWebDomPort.kt:1112-1127` ; `@JsFun` `WasmWebDomPort.kt:1147-1152`) | `none` — la publication est structurelle, au même moment structural que le reducer d’input (`WebHostSession.kt:926-933`) ; *réussir* une action dépend en plus de l’activation transitoire que l’événement porte, autorité du navigateur (section 3.7), jamais gate de la publication. Le set publié est `{EnterFullscreen, ExitFullscreen, LockPointer, UnlockPointer, AcceptDrop}` depuis la phase 5, dont l’action de drop est celle qui n’a aucune primitive derrière elle (§3.11) | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler))` — la même valeur avant l’installation (`preInstallSurfaceCapabilities()`, `WebHostSession.kt:2005-2007`) et au snapshot terminal (`SurfaceAdmission.kt:26`, publié à `WebHostSession.kt:1772` et `:1810`) ; la surface attachée publie `Supported({EnterFullscreen, ExitFullscreen, LockPointer, UnlockPointer, AcceptDrop}, Available)` | `web-interaction-fullscreen` (smoke hors contrat : l’ensemble fermé des outcomes honnêtes, cliqué réellement) ; `WebInteractionSurfaceTest.handlerInteractionsIsTheFiveWebActionsOnceStructurallyInstalled`, `.thePreInstallSnapshotClaimsNoInteractionAtAll` |
| `SurfaceCapabilities.armedInteractions` | `js`, `wasmJs` | idem | `none` | `none` | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.ArmInteraction))` dans **tous** les snapshots, attaché comme terminal (`WebHostSession.kt:1996`, `SurfaceAdmission.kt:27`) — aucune plateforme n’implémente le chemin armé et le token du handler est la seule autorité d’interaction de ce target | `WebInteractionSurfaceTest.armedInteractionsRemainsUnsupported` |
| `WindowManagerCapabilities.requestWindow` | `js`, `wasmJs` | idem | `none` | `none` — la capability est décidée une fois à la construction du manager, par la seule présence d’un provider (`WebHostWindowManager.kt:207-214`) | sans provider, la session garde le `UnsupportedWindowManager` à l’identique : `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow))` (`UnsupportedManagers.kt:57`) et toute requête est un `WindowRequest` déjà terminal `Rejected(Unsupported(RequestWindow))` (`UnsupportedManagers.kt:63-71`) ; avec provider, `Supported({OpenedInNewSession}, Available)` — `OpenedHere` n’est jamais promis, Web n’exerce aucun chemin de commit | `web-no-implicit-window` (sans provider, inchangé) ; `web-window-provider-new-session`, `web-window-provider-same-context`, `web-window-provider-no-context`, `web-window-provider-invalid-element`, `web-window-provider-invalid-scope`, `web-window-provider-owned-element`, `web-window-provider-callback-failure`, sentinelles `web-provider-no-same-document-window`, `web-provider-owned-element-rejected` ; `WebHostWindowManagerTest` |
| `InteractionContext.request` (échelle d’admission) | `js`, `wasmJs` | idem | `none` | `none` — l’échelle est celle du handler commun, identique sur les trois cibles (`RuntimeInteractionHandler.kt:214-222`) | hors frame valide : `InteractionRequired(Expired)` ; frame d’une autre surface : `InteractionRequired(WrongSurface)` ; token déjà consommé : `InteractionRequired(Consumed)` ; action hors ensemble publié : `Unsupported(KadreOperation.Interaction)` — refusée *avant* `invokeNative`, donc sans aucun appel navigateur (l’admission du moteur commun, `RuntimeInteractionHandler.kt:214-222` ; la branche exhaustive de secours de `invokeNative`, `WebHostSession.kt:1123-1128`) ; registration fermée pendant l’appel natif : `Closed(KadreResourceKind.Interaction)` (`:225-227` et `:252-256`/`:283-285`, garde partagée des chemins `Now` et `Deferred`) ; budget différé épuisé : `ResourceLimitExceeded(KadreResourceKind.Interaction, maxPendingInteractionRequests)` (`:286-306`) ; les champs admis sont exactement ceux d’`OPERATION-CONTRACTS.md` §1.1, `LockPointer` exigeant `PointerCaptureMode.Locked`, tout autre mode valant `InvalidRequest("action.mode")` (`WebInteractionStimulus.kt:53-58`) ; un `AcceptDrop` dont l’`offerId` n’est pas l’offre que le seam a présentée est `InvalidRequest("offerId")` (`WebHostSession.kt:1051-1052`) | `WebInteractionSurfaceTest.aRetainedContextIsRefusedExpiredAfterTheHandlerReturns`, `.lockPointerRefusesEveryModeButLockedAsInvalidActionMode`, `.anUnsupportedActionNeverTouchesTheBrowser`, `.pendingRequestsRespectMaxPendingInteractionRequests`, `.terminationAbandonsDeferredRequestsWithClosedInteraction` ; `RuntimeInteractionHandlerCommonTest.requestRefusesWithClosedWhenTheRegistrationClosesDuringTheNativeCall`, `.deferredAdmissionRefusesWithClosedWhenTheRegistrationClosesDuringTheNativeCall`, `.pendingBudgetExceededRefusesWithResourceLimit`, `.duplicateRetainedExpiredAndUnsupportedRequestsFailWithoutCallingNativeCode`, `.retainedContextIsWrongSurfaceDuringAnotherSurfaceCallback` |
| `DisplayManager.state` (inventaire d’affichage) | `js`, `wasmJs` | idem | `js` : `Window.matchMedia`, `MediaQueryList` et `window.screen.colorDepth` des déclarations DOM de la stdlib-js ; `wasmJs` : les mêmes membres de `kotlinx-browser`, qui les déclare tous | `none` — le port est construit et observé à l’installation de la session (`WebHostSession.kt:593-598`, observateur installé par le runtime, `RuntimeDisplayManager.kt:65-66`) et l’installation pousse l’inventaire exact une première fois, sans attendre un événement navigateur (`WebDisplayPort.kt:100-128`) ; les feux suivants sont le `resize` de la fenêtre et la requête de résolution du dpr (`JsWebDisplaySource.kt:54-93`, miroir Wasm) | `DisplayInventory.Unavailable(TemporarilyUnavailable(retryable = true))` quand le viewport est inmesurable (dpr non fini ou non positif, tailles non positives), retiré au premier feu mesurable — jamais un inventaire partiel, jamais vide, jamais un second display : le fallback publié est exactement `Enumerated(primary = viewport, displays = listOf(viewport))` avec `DisplayType.HostViewport` (`WebDisplayPort.kt:170-196`) | `web-display-initial-hostviewport`, `web-display-resize-propagation`, `web-display-dpr-scale-factor`, `web-display-teardown-quiet`, sentinelles `web-display-exact-fallback`, `web-display-single-display`, `web-display-no-dom-creation`, `web-display-no-polling` (BCK-007) ; `WebDisplayPortTest` |
| `DeviceManager.state` (inventaire devices et gamepads, observation) | `js`, `wasmJs` | idem | `js` : les externals `JsGamepad`/`JsGamepadNavigator` que `JsWebGamepadDom.kt:19-43` déclare lui-même — aucune liaison SDK n’existe pour la Gamepad API ; `wasmJs` : les mêmes externals en `JsAny`/`@JsFun` (`WasmWebGamepadDom.kt:11-13` — kotlinx-browser 0.5.0 ne déclare rien de la Gamepad API) | `none` pour l’inventaire : `navigator.getGamepads()` est la seule source d’état, lue par le hub partagé de la page à la cadence des animation frames tant qu’au moins un port de session est ouvert (`WebGamepadHub.kt:82-96`, `:304-329`), et l’inventaire publié est toujours `Enumerated` — énuméré et vide là où le navigateur n’expose rien (un page insecure réelle n’expose aucun pad, `getGamepads` étant secure-context-only) ; les événements `gamepadconnected`/`gamepaddisconnected` ne déclenchent qu’une re-lecture immédiate | `DeviceInventory.Enumerated(devices = [], gamepads = …)` — la liste `devices` reste vide (le navigateur n’offre aucune primitive d’inventaire de périphériques génériques, rien n’est fabriqué) et la liste `gamepads` est celle que le poll rapporte, trous compris sans pad fantôme ni renumérotation (`WebGamepadHub.kt:183-234`) ; il n’existe pas d’état `Unsupported` de ce manager sur ce target | `web-gamepad-empty-inventory-honest`, `web-gamepad-connect-added`, `web-gamepad-state-poll`, `web-gamepad-disconnect-neutral`, `web-gamepad-routing-suspended-neutral`, `web-gamepad-session-close-quiet`, sentinelles `web-gamepad-no-phantom`, `web-gamepad-descriptor-exact`, `web-gamepad-no-fabricated-devices`, `web-gamepad-teardown-quiet` (BCK-008) ; `WebGamepadHubTest`, `WebGamepadMappingTest` |
| `Gamepad.playEffect` (effets gamepad) | `js`, `wasmJs` | idem | `js` : les externals `JsGamepadHapticActuator`/`JsGamepadEffectParameters` du même fichier (`JsWebGamepadDom.kt:87-119`) — le dictionnaire WebIDL `effectParameters` est construit par le target ; `wasmJs` : les `@JsFun` gardés du même seam (`WasmWebGamepadDom.kt:47-80`) | la capability du pad est gelée par connexion depuis trois préconditions (`WebGamepadEffects.kt:63-80`) : le secure context du browsing context (`window.isSecureContext`), l’actuateur `vibrationActuator` que cette connexion offre (`null` là où le navigateur n’en a pas — Firefox/WebKit), et la liste `effects` que l’actuateur déclare elle-même, sondée par un dual-rumble de durée nulle quand elle ne l’est pas | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect))` sur chacune des trois préconditions manquantes (contexte insecure, actuateur absent, aucun genre annoncé ou sonde refusée) ; un genre non annoncé — `LocalizedHaptic` toujours — est refusé `InvalidRequest("effect")` avant tout appel d’actuateur ; un pad déconnecté refuse `Closed(Gamepad)` avant tout appel ; un lancement que le navigateur refuse est `PlatformFailure(Web, "gamepad-effect", "refused")` ; `maximumDuration = null` — le navigateur serre la borne (`WebGamepadEffects.kt:74-79`, `:95-131`) | `web-gamepad-effect-dual-rumble`, `web-gamepad-effect-stop`, `web-gamepad-effect-unsupported-kind`, `web-gamepad-effect-insecure-context`, sentinelles `web-gamepad-no-implicit-prompt`, `web-gamepad-no-localized-haptic`, `web-gamepad-effect-once` (BCK-009) ; `WebGamepadEffectTest` |
| `SurfaceCapabilities.platformAccess` | `js`, `wasmJs` | idem | `none` | `none` — la capability est structurelle : la surface attachée la publie `Supported` (`WebHostSession.kt:2019`) et la lease `withWebElement` est le contrat de l’élément — bornée, non réentrante, admise tant que la surface admet (§6.3 de `BACKEND-CAPABILITIES.md`, ligne 239) | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))` au snapshot terminal (`SurfaceAdmission.kt:28`) ; dans la fenêtre de révocation d’ownership une lease est déjà refusée tandis que la capability lit encore `Supported` (limite de la phase 2, enregistrée au README du driver) | `web-element-lease`, `web-element-lease-boundary`, `web-element-lease-close-order`, `web-element-lease-concurrent-close` (`INT-004`, `web-surface.spec.mjs`) ; `WebElementLeaseTest`, `JsWebElementLeaseTest`/`WasmWebElementLeaseTest` |
| `CaptureCapabilities.screen` et `CaptureCapabilities.window` (cible `HostChoice`) | `js`, `wasmJs` | idem | `js` : `MediaStream`/`MediaStreamTrack` des déclarations kotlinx-browser 0.5.0, le reste de la chaîne — Permissions API, `getDisplayMedia`, `MediaStreamTrackProcessor` — déclaré par la réalisation elle-même, snippets `js()` à garde `typeof` (`JsWebCaptureDom.kt:33-40`, `:422-434`, `:483-485`) ; `wasmJs` : les mêmes lectures en `@JsFun` (`WasmWebCaptureDom.kt:17-46`) | les trois préconditions sondées une fois à la construction et gelées dans la capability — le secure context, la présence de `getDisplayMedia`, celle du `MediaStreamTrackProcessor` (`WebCapturePort.kt:387-409`) ; le consentement lui-même n’est demandé que par l’open explicite de l’application, jamais par la publication de la capability | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))` sur chacune des trois préconditions manquantes (`WebCapturePort.kt:391-394`) ; la cellule `window` est le miroir exact de la cellule `screen` (`:429-433`) — un consentement navigateur unique gouverne ce que son picker offre, il n’existe aucune garantie de fenêtre distincte | `web-capture-initial-honest`, `web-capture-insecure-unsupported` (BCK-010) ; les scénarios de session `web-capture-hostchoice-stream`, `web-capture-configuration-before-frame`, `web-capture-frames-bounded`, `web-capture-stop-exactly-once` (BCK-011, projet `chromium-capture`) ; `WebCapturePortTest.insecureContextUnsupportedEverywhereWithSecureContextCauseOnThePicker`, `.missingGetDisplayMediaUnsupportedWithNoGetDisplayMediaCauseOnThePicker`, `.missingTrackProcessorUnsupportedWhileThePickerStaysAvailable`, `WebCaptureStreamTest.hostChoiceReserveRoutesToThePickedSourceAndStreams` |
| `CaptureCapabilities.surface` (cible `Surface`) | `js`, `wasmJs` | idem | le même seam que la cible `HostChoice`, plus la lecture `instanceof HTMLCanvasElement` de l’élément attaché (`JsWebCaptureDom.kt:505-508`, miroir `@JsFun` Wasm) | les trois mêmes préconditions que `HostChoice` plus le genre canvas de l’élément attaché, sondés à la même construction (`WebCapturePort.kt:410-427`) ; l’open lui-même est sans consentement — `captureStream()` sur le canvas est l’unique effet navigateur (`:222-230`) | `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))` quand une précondition manque, le non-canvas compris (`:414-415` — le même code que la capability gelée, jamais un autre) ; un id étranger à la surface primaire enregistrée est refusé `InvalidRequest("request.target")` avant tout appel navigateur (`:216-219`) | `web-capture-surface-canvas-stream` (BCK-011, projet `chromium-capture`) ; `WebCaptureSurfaceTest.canvasSurfaceReservesStreamsAndAnswersPremultipliedAlphaOnTheRgba8Path`, `.foreignSurfaceIdIsRefusedInTheRuntimeOwnUnresolvableTargetFormWithoutTouchingTheSeam`, `.nonCanvasSurfaceRefusesWithTheFrozenCapabilityCauseAsTheConsistentBackstop`, `WebCapturePortTest.reserveOfSurfaceRefusesAnUnregisteredIdWithZeroSeamInteraction` |
| `CaptureCapabilities.sourceEnumeration` | `js`, `wasmJs` | idem | `none` | `none` — la capability est inconditionnelle : elle dit ce que le navigateur sait, pas ce qu’il cache, et l’état du browsing context ne la retire pas | il n’y en a pas : `Capability.Supported(Unit, FeatureAvailability.Available)` dans tous les états, contexte insecure compris (`WebCapturePort.kt:437`) ; les sources publiées sont toujours `CaptureSources.HostPickerOnly` (`:459-463`) — aucun navigateur n’énumère les surfaces capturables avant un consentement, rien n’est inventé à la place — et `refreshSources` est le no-op honnête qui rend l’instantané courant sans un seul appel seam (`:161-166`), donc jamais de prompt | `web-capture-initial-honest` (cellules `data-kadre-capture-sources` = `hostPickerOnly` et `data-kadre-capture-enumeration` = `supported`) ; sentinelles `web-capture-no-fabricated-sources`, `web-capture-no-implicit-prompt` ; `WebCapturePortTest.refreshSourcesReturnsTheSameSnapshotWithoutTouchingThePicker` |
| `CaptureCapabilities.hostPicker` | `js`, `wasmJs` | idem | `none` | le secure context, puis la présence de `getDisplayMedia` — la forme `FeatureAvailability.Unavailable` porte la cause en code de failure (`PlatformFailure(Web, "capture-capability", …)`, causes `"secure-context"` puis `"no-get-display-media"`, `WebCapturePort.kt:438-448`) | `FeatureAvailability.Unavailable(KadreFailure.PlatformFailure(KadrePlatform.Web, "capture-capability", <cause>))` sur ces deux états du browsing context — jamais `Unsupported` : un picker absent est un état du contexte, pas une absence structurelle de l’adapter — et `FeatureAvailability.Available` sinon | `web-capture-insecure-unsupported` (cellule `unavailable:platformFailure:web:capture-capability:secure-context`) ; `WebCapturePortTest.insecureContextUnsupportedEverywhereWithSecureContextCauseOnThePicker`, `.missingGetDisplayMediaUnsupportedWithNoGetDisplayMediaCauseOnThePicker` |
| `CaptureTarget.Source` (cible `Source` de la matrice) | `js`, `wasmJs` | idem | `none` | `none` — le refus est structurel et ne dépend d’aucun état du browsing context | `KadreFailure.Unsupported(KadreOperation.CaptureOpen)` au reserve du port, avant toute interaction seam — zéro appel navigateur, rien réservé, rien demandé (`WebCapturePort.kt:174-176`, `WebCapturePortTest.reserveOfAnInventorySourceIsRefusedWithZeroSeamInteraction`) ; depuis le driver, la demande est de toute façon inconstruisible — le constructeur de `CaptureSourceId` est interne à la foundation et aucun inventaire `Enumerated` n’existe sur ce target, l’admission du runtime répondant `InvalidRequest("request.target")` (`RuntimeCaptureManager.kt:287-307`) | sentinelle `web-capture-no-fabricated-sources` (aucun inventaire inventé ne rend une cible `Source` résolvable) ; scénario `web-capture-source-refused-before-picker` (le même entonnoir d’admission, région comprise, refusé avant tout picker) ; `WebCapturePortTest.reserveOfAnInventorySourceIsRefusedWithZeroSeamInteraction` |

Les availabilities d’entrée sont déclarées par une seule transition structurelle, à la fin de
`installSessionConfiguration` (`WebHostSession.kt:912-925`) : `keyboardInstalled = true`,
`pointerInstalled = true`, `touchInstalled = true`, `gestureKinds = emptySet()`. Les contacts
tactiles empruntent les tout mêmes listeners `pointer*` que le pointeur, si bien qu’il n’y a pas
de seconde installation à attendre ; les gestures restent exclues de la même observation qui
déclare le touch. Le text input et le drag-and-drop sont déclarés par la même installation, un
peu en amont : la session configuration construit le reducer avec le port text
(`textInputPort = WebTextInputPort(...)`, `WebHostSession.kt:865`, D-X2) et avec
`dragAndDropAvailable = true` (`:873`), et les listeners de drag font partie de
l’installation d’observation de l’élément (`JsWebDomPort.kt:402-405`,
`WasmWebDomPort.kt:412-415`). Rien de tout cela n’est déclaré plus tôt, et aucun stimulus
d’entrée ne peut déclarer une capability — l’union `WebInputStimulus` n’a pas de membre pour le
dire, ce que
`WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation` vérifie
après avoir livré une frappe et une entrée de pointeur.

Aux quatre sentinelles rattachées à une ligne ci-dessus s’ajoute la cinquième du contrat,
`web-input-post-terminal-stimulus`, qui garde la lane d’une surface terminale : l’input réel livré
après la fermeture ne publie ni état, ni événement, ni reset (`web-input-terminal-closed`).

Les lignes d’interaction et de fenêtre du registre sont celles de la phase 4. La première
particularité de lecture : le scénario navigateur de l’interaction, `web-interaction-fullscreen`,
**n’est pas** une preuve de contrat — il ne figure dans aucun `evidenceId` de `BCK-001` ; la suite
`web-interaction.spec.mjs` clique réellement le host et n’asserte que l’ensemble fermé des
outcomes honnêtes — `committed`, ou `rejected:platformFailure:web:fullscreen:refused` — en
enregistrant celui que ce Chromium a observé dans son propre journal d’exécution
(`web-interaction.spec.mjs:37-44`). Les tests qui asservissent les lignes sont les classes
`webTest`/`commonTest` citées, exécutées par `:kadre:check` et non par la gate CI. La seconde
particularité : `WindowManagerCapabilities.requestWindow` n’est pas une capability de surface mais
de manager, décidée à la construction de la session — c’est la seule ligne du registre dont l’état
dépend d’un choix du host fait *avant* toute installation (`WebHostSession.kt:415-423`).

Les quatre lignes du bas du registre sont celles de la phase 6, et leur lecture partage deux
faits. D’abord, elles sont portées par des managers que toute session possède (`Application.displays`,
`Application.devices`), et leur publication est structurelle au même titre que celle de l’input :
le port d’affichage est construit et observé à l’installation de la session
(`WebHostSession.kt:593-598`), le port gamepad est ouvert au même endroit (`:607`) et son hub
partagé démarre son poll à cette ouverture, et `platformAccess` est publiée `Supported` par la
surface attachée (`:2019`). Ensuite, aucune de ces lignes ne dépend d’une permission : la
colonne `runtime gate` ne vaut pas `none` pour `Gamepad.playEffect` que parce que le secure
context, l’actuateur et la déclaration de l’actuateur sont des préconditions **par pad**, gelées
dans la capability que le manager publie — jamais une gate que Kadre évaluerait en dehors de
l’inventaire. Le scénario insecure-context de `BCK-009` court dans le second projet Playwright
(`--host-resolver-rules=MAP insecure.kadre.invalid 127.0.0.1`, page servie en http simple sur un
nom non-localhost) et n’asserte que des observables : `isSecureContext` faux, le pad découvert,
les effets `Unsupported`, zéro prompt — l’article §3.14 porte le détail.

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
   (`RuntimeSurfaceInput.kt:840-843`) — avec `CloseSource`, `failSession` vaut `false` : la **lane
   d’input** est terminalisée, la session ne l’est pas ;
3. `terminaliseLocked` publie le snapshot neutre dont la composition de capabilities est
   `unavailableInputCapabilities(currentState.capabilities, failure)`
   (`RuntimeSurfaceInput.kt:864-882`), et cette fonction pose `keyboard = pointer = touch =
   dragAndDrop = FeatureAvailability.Unavailable(failure)` (`RuntimeSurfaceInput.kt:1469-1483`) ;
4. le même chemin est atteint par un profil à livraison continue `Buffered(…, CloseSource)`, qui
   produit `ContinuousOverflowAction.CloseSource` avec le même `SourceOverflow(InputSource)`
   (`RuntimeSurfaceInput.kt:849-852`).

Deux conséquences à ne pas confondre, et qui sont la raison pour laquelle la colonne `état absent`
porte deux valeurs : `gestures` **ne** devient pas `Unavailable` sur ce bras — un
`Capability.Unsupported` y reste inchangé (`RuntimeSurfaceInput.kt:1478-1482`) — et `textInput` et
`rawInput` y sont re-posés en `Capability.Unsupported` (`:1481-1482`), donc leurs lignes n’ont
qu’une valeur. L’adapter Web est concerné par ce bras comme tout hôte acceptant
`KadrePolicies.Default`, et les contrats `BCK-003`/`BCK-004`/`BCK-005`/`BCK-006` ne le prouvent
pas en navigateur : il est décrit ici
parce que §8 demande la valeur exacte, et sa réserve est celle de la section 4.

Ce bras terminalise la **lane** d’input, jamais la surface : `terminaliseLocked(failSession = false)`
laisse la session et la surface vivantes, et le champ `SurfaceCapabilities.inputDefaultBehavior`
qu’elles publient reste celui du snapshot attaché — `Supported({HostDefault, SuppressWhenPossible},
Available)`. `WebHostSession.suppressDefaultFor` ne lit en effet que la fermeture d’admission et ce
champ (`WebHostSession.kt:1669-1674`) : la suppression peut donc encore être accordée pour une lane
qui ne livre plus rien, jusqu’à ce que la surface elle-même devienne terminale.

La colonne `runtime gate` est à `none` pour toutes les lignes : aucune feature ne dépend d’une
permission, d’un secure context, d’un protocole ou d’un matériel, et ce qui dépend d’un élément
focusable est le *focus*, une responsabilité du host (section 3.1 ci-dessous) et non un gate que
Kadre évaluerait. La colonne `compile gate` n’est renseignée que là où un symbole DOM est
effectivement lu — `keyboard`, `pointer` et `touch` — et vaut `none` pour les features dont le code
ne lit aucun type DOM.

## 3. Faits établis par cette phase, qui ne vivaient que dans le code ou dans `DESIGN.md` §15.3

### 3.1 Responsabilité du host : les listeners sont sur l’élément, et lui seul

Les quatorze listeners d’input — les dix de la phase 3 (`keydown`, `keyup`, `pointerenter`,
`pointermove`, `pointerdown`, `pointerup`, `pointerleave`, `pointercancel`, `lostpointercapture`,
`wheel`) et les quatre de drag que la phase 5 ajoute (`dragenter`, `dragover`, `dragleave`,
`drop`) — sont posés sur l’élément attaché et sur aucun `Document`, aucune `Window` et aucun
ancêtre (`JsWebDomPort.kt:392-405`, `WasmWebDomPort.kt:402-415`). Les listeners de text input
(`beforeinput`, `compositionstart`, `compositionupdate`, `compositionend` et le `keydown` de
soumission) ne sont posés que par une session ouverte, sur l’élément que le port sert, et vont
avec sa fermeture (`JsWebTextInputEvents.kt:50-76` ; `WasmWebTextInputEvents.kt`, miroir membre à
membre). Rendre l’élément focusable et éditable est la responsabilité du host : Kadre n’écrit
aucun `tabindex` et ne modifie jamais le DOM du host. Le fixture de preuve fournit lui-même
`host.tabIndex = 0` (`WebDriverFixture.kt:985`) et prépare l’élément éditable des scénarios text
avant tout appel Kadre (`WebDriverFixture.kt:1530-1540`). **Un host qui n’a pas rendu son élément
focusable reçoit `keyboard = available` et aucune frappe** : la capability décrit l’installation des
listeners, pas l’arrivée d’un événement (`DESIGN.md` §15.3, D7).

### 3.2 L’ensemble fermé des catégories supprimées

Sous `InputDefaultBehavior.SuppressWhenPossible`, l’ensemble des défauts navigateur supprimés est
**fermé** et écrit membre par membre (`WebInputTranslation.kt:155-158`) :

```kotlin
internal val SUPPRESSED_INPUT_DEFAULTS: Set<WebInputCategory> = setOf(
    WebInputCategory.Wheel,
    WebInputCategory.ScrollingKey,
)
```

`WebInputCategory.ScrollingKey` est exactement la **pression** (`KeyState.Pressed`) d’une touche de
la liste fermée des clés de défilement documentaire (`WebInputTranslation.kt:186-196`) :

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
stimulus.logicalKey.value in WEB_DOCUMENT_SCROLL_KEYS` (`WebInputTranslation.kt:212-219`) : c’est le
`NamedKey` que le layout a produit qui est comparé, jamais le `PhysicalKey` ni le `code` du DOM. Le
code le dit et en donne la raison (`WebInputTranslation.kt:164-169`) : « the browser’s default is
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
(`WebInputTranslation.kt:257-262`). `webTest` épingle l’ensemble contre l’énumération complète des
catégories dans les deux sens (`WebInputSurfaceTest.suppressWhenPossibleSuppressesExactlyTheClosedSet`,
`.aKeyPressIsAScrollingKeyOnlyForTheClosedListOfDocumentScrollKeys`). La suppression n’est jamais
globale : `preventDefault` n’est appelé que dans le callback de l’événement en main et sur lui
(`DESIGN.md` §15.3).

### 3.3 Divergence de capture sur le bras « perte d’activation », reprise mot pour mot

Le commentaire de `WebHostSession.kt:1634-1644` enregistre la divergence sous la forme citable
suivante, reprise ici **verbatim** :

> after a loss of activation the web surface publishes `SurfaceState.pointerCapture = None` and does not call `releasePointerCapture`; Chromium may still hold the mouse capture until the next press, so `None` must be read as "this surface claims no capture", never as "the browser holds none".

Les trois autres bras d’un pointeur — un relâchement de bouton, un `pointerleave`, un
`pointercancel` — voient le navigateur terminer la capture implicitement avec le pointeur auquel
elle appartenait, donc le DOM et l’état publié s’accordent (`WebHostSession.kt:1634-1639`). La
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
(`WebInputTracking.kt:8-26`) :

```kotlin
fun advance(deltaMode: Int, buttons: Int): Long {
    val opens = frameIsNew || this.deltaMode != deltaMode || this.buttons != buttons
    ...
}
```

La frontière s’ouvre donc (a) pour le premier wheel qu’une nouvelle animation frame livre
(`frameOpened()` est appelé par le marqueur de frame du port, `JsWebDomPort.kt:128`,
`WasmWebDomPort.kt:129`), (b) quand le `deltaMode` change — l’unité de mesure du navigateur — et
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
`cursor`, `customCursor` et `hitTesting` (`WebHostSession.kt:1984`, `:1985`, `:1990`), et la phase 5
n’active que les trois lignes d’input de ses contrats. Un champ déclaré `Unsupported` est refusé
avant tout commit (`admitField`), et tout champ committable arrivé sans chemin de commit ferait
échouer une vérification plutôt que d’être acquitté
(`WebHostSession.kt:1489-1493` et `requireRefused`, `:2050-2056`).

### 3.6 Deux nuances de la réconciliation

1. **Une capture perdue déplace la révision sans publier de `SurfaceEvent`.**
`reconcilePointerCapture` écrit un `SurfaceState` dont `pointerCapture` vaut `None` et dont la
révision est incrémentée de 1 (`WebHostSession.kt:1648-1656`) ; il ne publie aucun événement, et le
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
   `WebInputTranslation.kt:276-279`), épinglée contre `PointerCaptureMode.entries` dans les deux sens.

### 3.7 Le modèle de token d’interaction sur Web : la frame est le callback DOM

La mécanique est celle du runtime, liftée en `commonMain` et partagée par les trois cibles —
`RuntimeInteractionHandler` ne fork ni token-machine ni sérialiseur Web
(`kadre/runtime/src/commonMain/.../RuntimeInteractionHandler.kt`) — avec exactement deux seams par
target : le **frame d’appel** (`InteractionCallFrame`, expect/actual — un `ThreadLocal<SurfaceId?>`
en JVM, un simple champ en js/wasmJs, `InteractionCallFrame.kt:23-33`) et la **forme de l’outcome
natif** (`NativeInteractionOutcome`, `NativeInteractionOutcome.kt:20-24`). Les faits que ce target
en fait :

1. **Le dispatch part des listeners `pointerdown`/`keydown` de la phase 3, synchronement, avant
   l’admission du stimulus ordinaire** (`JsWebDomPort.kt:179-215`, `WasmWebDomPort.kt:189-225` ;
   l’ordre AppKit de `DESIGN.md:983-989`), et la surface ne dispatche rien dès qu’elle a cessé
   d’admettre (`WebHostSession.kt:982`). Le handler s’exécute dans le callback DOM même : c’est la
   frame dont la transient activation est l’autorité que le modèle préserve.
2. **Le token est single-use et expire au retour du callback.** Un second `request` dans le même
   callback vaut `InteractionRequired(Consumed)` (`RuntimeInteractionHandler.kt:218`, consommation
   atomique avec l’allocation à `:228`) ; un contexte retenu et réutilisé après le retour vaut
   `InteractionRequired(Expired)` (`:216`, invalidation dans le `finally` du dispatch à `:113-115` et
   `:312-314`) ; un contexte d’une autre surface vaut `WrongSurface` (`:217`). L’ensemble est
   asservi par `RuntimeInteractionHandlerCommonTest` et `WebInteractionSurfaceTest`.
3. **L’outcome des primitives du navigateur est différé.** `requestFullscreen`/`requestPointerLock`
   sont émis synchronement dans la frame du handler (`WebHostSession.kt:1101-1119`) ; l’outcome
   terminal est publié au callback du navigateur — `fullscreenchange`/`pointerlockchange` confirmés
   → `Committed` (`JsWebDomPort.kt:489-493`, `:551-553`), `fullscreenerror`/`pointerlockerror`/promesse
   rejetée → `Rejected(PlatformFailure(Web, "fullscreen"|"pointer-lock", "refused"))`
   (`WebInteractionStimulus.kt:64-97`). Le DOM n’expose aucune raison — un refus navigateur a un
   seul code honnête, et un échec d’émission répond exactement le même (`JsWebDomPort.kt:486`,
   `:493-497`, `:548`, `:553-557`). Chaque canal terminal est one-shot
   (`WebPrimitiveEmission`, `WebInteractionStimulus.kt:113-150`) : le premier terminal gagne, les
   listeners s’en vont avec lui, aucune seconde réponse ne peut corrompre le verdict.
4. **Un pending différé occupe le budget `maxPendingInteractionRequests` jusqu’à son outcome
   terminal** (`RuntimeInteractionHandler.kt:274-307` ; 16 sous `KadrePolicies.Default`,
   `KadrePolicies.kt:26`) ; au-delà, la requête est refusée
   `ResourceLimitExceeded(KadreResourceKind.Interaction, …)` (`:286-306`). **Aucun timeout
   synthétique** ne la termine : si le callback terminal n’arrive jamais, le pending tient son
   budget jusqu’à la terminalisation de la surface, qui abandonne chaque pending avec
   `Closed(KadreResourceKind.Interaction)` (`abandonPendingRequests`, `:167-178` ; appelé à
   `WebHostSession.kt:1798-1803`) — un `Rejected` par pending, puis la fin du flow.
5. **Une fermeture pendant l’appel natif est refusée `Closed(KadreResourceKind.Interaction)`** sur
   les deux chemins — `Now` (`:252-256`) comme `Deferred` (`:283-285`) — la garde étant atomique
   avec l’insertion du pending sous le même lock, si bien qu’un sweep de fermeture ne peut manquer
   aucun pending (`:381-395`).
6. **Kadre ne lit pas `navigator.userActivation`** — zéro occurrence dans les sources des deux
   targets, du runtime et du consumer. La règle (D4) : les listeners `pointerdown`/`keydown`
   appariés sont déjà des événements *trusted* porteurs d’activation ; `hasBeenActive` est collant
   et ne distingue pas « cette frame » ; et un second-guess de Kadre transformerait un refus
   navigateur légitime en refus Kadre prématuré. La perte d’activation réelle se manifeste au refus
   de la primitive elle-même, livré comme outcome `Rejected` ci-dessus : **le navigateur est
   l’arbitre de ses primitives**. Enregistré comme limite documentée, pas comme trou.
7. **Les sorties d’état nul sont des zéros appels navigateur.** Un `ExitFullscreen` dont
   `document.fullscreenElement` est déjà nul et un `UnlockPointer` dont
   `document.pointerLockElement` n’est pas cet élément répondent `Committed` synchronement, sans
   appeler le navigateur — un appel sans décision derrière lui n’est pas posé
   (`JsWebDomPort.kt:512-522`, `:570-580` ; miroirs `WasmWebDomPort.kt:522-532`, `:582-592`).


### 3.8 Le provider de fenêtres : l’échelle de validation, la session enfant, le budget

1. **`OpenWindow` reste `Unsupported` sur Web même avec provider** (`DESIGN.md:1933`) : l’action est
   hors de l’ensemble publié (`WebInteractionStimulus.kt:32-38`), refusée à l’admission
   `Unsupported(KadreOperation.Interaction)` et n’atteint aucune API navigateur
   (`WebHostSession.kt:1123-1128`). L’ouverture passe exclusivement par
   `WindowManager.requestWindow` + provider.
2. **L’échelle de validation d’un host offert** (`validateWindowHostChecks`,
   `WebHostWindowManager.kt:113-122`) produit exactement les codes d’`OPERATION-CONTRACTS.md` §4 :
   `InvalidRequest("element")` (élément déconnecté sous `StopWhenDetached`),
   `InvalidRequest("element.ownerDocument")` (`defaultView` nul **ou** égal au contexte d’origine —
   les deux lectures pliées en un booléen par la sonde par target, `WebHostWindowManager.kt:64-75`),
   `InvalidRequest("parentScope")`, `ParentScopeCancelled` — ce dernier ne décrivant que la scope du
   nouveau host, jamais celle du requester. **Ordre enregistré** : l’échelle livrée évalue le rung
   `element` **avant** `element.ownerDocument` (`:117-118`) — l’énumération de D7 — alors que la
   prose du contrat nomme `ownerDocument` d’abord (`OPERATION-CONTRACTS.md:104`) ; l’ordre choisi
   est celui des codes du §1.1, où `"element"` précède `"element.ownerDocument"`, et il est épinglé
   par `WebHostWindowManagerTest` (`:394-402`) : tous rungs en échec, la seule réponse est
   `InvalidRequest("element")`. Les lectures DOM (`isConnected`, `defaultView`, état du `Job`) sont
   faites par target (`JsWebAttach.kt:110-120`), la décision est la fonction pure `webMain`.
3. **Les exceptions du provider sont capturées** : un throw du callback devient
   `PlatformFailure(Web, "WebWindowProvider", "callback-exception")` (`WebHostWindowManager.kt:274-278`,
   `:298-299`) et une failure retournée hors de l’ensemble fermé de
   `WindowRequestOutcome.Rejected` devient le même domain avec le code `"invalid-failure"`
   (`:290-296`, ensemble à `:132-150`). Ce sont des **outcomes de la requête admise**, jamais des
   failures de l’appel `requestWindow` extérieur — l’appel répond toujours
   `Success(WindowRequest)`.
4. **La session enfant passe par le chemin d’attach ordinaire** (`WebChildSessionFactory`,
   `WebHostWindowManager.kt:94-96`) : le même `WebHostSession.attach`, le même registre global
   d’ownership `WebHostRegistry` — un élément possédé par une session vivante produit
   `AlreadyInUse(Host)` (le `Busy(Host)` du contrat, `WebHostRegistry.kt:18`) à l’attach enfant,
   donc aucun duplicate owner n’est possible entre contexts — et un launch context threadé :
   `KadreLaunchInfo(AdditionalHostRequested, originatingRequestId)` (`JsWebAttach.kt:93-104`)
   publié à l’application comme `KadreLaunchContext` (`SessionRuntime.kt:196-201` ;
   `KadreLaunchReason.AdditionalHostRequested`, `Application.kt:34`). La fermeture du requester
   n’atteint jamais l’enfant : pour la façade, la scope de l’enfant est un `MainScope()` Kadre
   frais, créé par host ouvert (`JsWebHostProviderBridge.kt:80-83`), jamais un enfant de la scope
   du requester.
5. **Comptage du budget, divergence enregistrée : une requête terminale tient son créneau
   `maxPendingWindowRequests` jusqu’à `close()`.** La référence évict le pending de son budget au
   handoff (`RuntimeWindowManager.kt:522-526`, `finishOpenDispatchLocked` après resume) ; le manager
   Web le garde jusqu’à ce que le requester appelle `WindowRequest.close()` (`WebHostWindowManager.kt:314-316`,
   `:259-261`) ou que la session termine (`:249-256`). C’est la seule lecture qui rend
   `Limit(WindowRequest)` exécutable pour un provider synchrone : l’outcome est terminal **avant**
   que le caller voie la requête (`:237-242`), un evict au handoff viderait le budget à chaque
   requête et la limite ne limiterait rien ; tenir le créneau jusqu’au `close()` du requester en
   fait une borne réelle sur les requêtes que les callers possèdent. Les sémantiques
   `cancel()`/`await()`/`close()` produites restent celles de la référence, dégénérées honnêtement
   parce que l’outcome est déjà connu : `AlreadyTerminated`, outcome terminal, libération seule
   (`:301-340`).
6. **Le provider ne voit qu’une copie DTO** : `WindowSpec.dtoCopyForProvider()` re-copie chaque
   champ et re-copie les octets de l’icône, le seul champ porteur d’un tableau mutable
   (`WebHostWindowManager.kt:343-352`) ; la façade livre de même un objet frais au provider JS —
   `JSON.parse` du DTO sérialisé, `Uint8Array` reconstruite (`JsWebHostProviderBridge.kt:108-146`).

### 3.9 Le touch sur les pointer events : une identité par contact, jamais un pointeur

1. **Le routage est un prédicat, pas un refus.** `webPointerKind("touch")` répond `null`
   (`WebInputMapping.kt:332-337`) et la fonction le dit : « a contact that reached this function
   must produce no pointer stimulus of any kind » — les ports lisent ce `null` comme le prédicat de
   routage qu’il est et remettent l’événement au chemin du touch (`WebInputMapping.kt:322-326`),
   où `webTouchPhase` traduit le type de l’événement lui-même : `pointerdown`→`Started`,
   `pointermove`→`Moved`, `pointerup`→`Ended`, `pointercancel`→`Cancelled`
   (`WebInputMapping.kt:355-361`), les deux autres événements d’un contact (`pointerenter`,
   `pointerleave`) ne portant aucun fait que le modèle du touch ait une phase pour. Les lectures
   `pointers` et `touches` de `SurfaceInputState` restent disjointes — le reducer commun alloue un
   `TouchId` opaque par identité native et ne laisse jamais un contact devenir un `PointerState`
   (`RuntimeSurfaceInput.kt:713-743`), épinglé par `web-touch-no-pointer-alias` et par
   `WebInputSurfaceTest`.
2. **L’identité d’un contact est un objet stable par geste, pas l’entier du `pointerId`.** Le
   stimulus du touch porte un `nativeIdentity: Any` que le reducer clé par référence
   (`IdentityKeyedMap`) ; la table `WebTouchContacts` frappe un objet par contact à son
   `pointerdown` et rend *la même* référence pour chaque événement ultérieur du `pointerId`,
   retire l’identité au `pointerup`/`pointercancel`, et fait taire un `down` dupliqué, un move
   sans down et tout événement postérieur à la fin
   (`WebInputTracking.kt:109-157`, lecture cible `JsWebDomPort.kt:843-860`,
   `WasmWebDomPort.kt:869-886`). La règle entière repose sur le fait du DOM cité dans la table :
   **un `pointerId` est stable pour la durée de vie de son contact** pour un pointeur tactile.
3. **Le touch déclenche les interactions (D-T3).** Un `pointerdown` tactile dispatch
   `RuntimeSynchronousInteraction.TouchStarted` au handler installé — d’abord, dans le callback
   DOM même, avant le stimulus ordinaire du même événement (`JsWebDomPort.kt:955-965`,
   `WasmWebDomPort.kt:967-975` ; l’ordre AppKit de `DESIGN.md:983-989`) — et la surface le route
   comme les triggers du pointeur et de la clé (`WebHostSession.kt:997-1001`). Le `TouchId` du
   trigger est alloué monotoniquement par la lane d’interaction (`WebTouchInteractions`,
   `WebInputTracking.kt:159-185`) : deux allocations du même type opaque dans deux lanes, dont
   rien ne prétend qu’elles coïncident — le trigger promet l’unicité dans sa lane, rien de plus.
   Seul un down qui *commence* un contact déclenche : un down dupliqué dispatche ni trigger ni
   stimulus (`JsWebDomPort.kt:960-964`).
4. **La perte de focus vide les contacts, côté reducer.** `focusLost` publie le snapshot neutre
   dont `touches` est vide et vide les deux maps d’identités natives
   (`RuntimeSurfaceInput.kt:406-424`), le bras que le scénario `web-touch-focus-loss-clears`
   pilote avec une vraie perte d’activation.
5. **`touch-action` est au host ; une annulation navigateur est livrée telle quelle.** Kadre
   n’écrit aucun style et ne pose jamais de `touch-action` ; un contact que le navigateur révoque
   — pour un scroll natif que le host a laissé passer — arrive comme `pointercancel` et est livré
   `TouchPhase.Cancelled`, « as reported and never compensated »
   (`WebInputMapping.kt:345-347`, `WebInputStimulus.kt:100-104`). Le cahier manuel
   (`manual/phase-5-text-input.md`, procédure 3) en fait une procédure : c’est le cas où la
   responsabilité du host devient observable.
6. **Les gestures restent `Unsupported(GestureInput)`, et c’est une limite enregistrée.** Aucun
   recognizer n’existe dans le runtime, la foundation ou ce target, et le navigateur n’offre
   aucune primitive de reconnaissance ; `gestureKinds` reste vide dans l’observation qui déclare
   le touch (`WebHostSession.kt:912-925`), et `BACKEND-CAPABILITIES.md:133` autorise
   explicitement pointer/touch `Available` + gestures `Unsupported`. Un Pan/Pinch reconnu maison
   serait une approximation cachée, l’espèce même de D9.

### 3.10 Le text input : un shadow port-internal contre le document du host

1. **La capability est structurelle ; l’éditabilité est la frontière du host (D-X2).**
   `WebTextInputPort.capability` vaut `Capability.Supported(Unit, Available)` à la construction
   (`WebTextInputPort.kt:114`), publiée par le reducer que la session configuration construit
   (`WebHostSession.kt:865`). Un élément que le contrat v1 n’adresse pas — tout sauf `<input>` et
   `<textarea>` (`WebTextInputPort.kt:30-34`, `:175`) — ouvre une session qui n’installe aucun
   listener et n’observe rien (`:189`), et le fixture prouve l’ouverture d’une session vivante
   sur un `<div>` (`WebTextInputSurfaceTest.anElementThatIsNotAnInputOrTextareaOpensASessionThatObservesNothing`).
2. **L’écriture retour est le contrat (D-X3), et elle est sondée par le `instanceof` du
   navigateur.** `updateSurroundingText` écrit `value` + `setSelectionRange` sur l’élément —
   l’élément d’abord, le shadow ensuite (`WebTextInputPort.kt:233-255`) — et le sondage JS lit
   `element instanceof HTMLInputElement` / `HTMLTextAreaElement` plutôt qu’un cast Kotlin, « so a
   non-addressable element would … take an expando `value` write, and only then meet the
   `setSelectionRange` refusal — a host mutation outside the contract »
   (`JsWebTextInputEvents.kt:80-107`, `:139-152` ; miroir Wasm). Un élément non adressable refuse
   l’écriture-retour en fermant la session (`Closed(TextInputSession)`, `:238-241`) sans toucher
   l’élément, et une écriture que l’élément refuse est la failure de seam
   `PlatformFailure(Web, "text-input", "write-back-failed")` (`:242-246`).
3. **Le shadow est la seule source d’offsets (D-X1).** Le document de travail du port — texte,
   sélection, révision acceptée, composition — est initialisé depuis la config sans écrire
   l’élément et avancé par les observations et les snapshots acceptés
   (`WebTextInputShadow`, `WebTextInputPort.kt:424-496`) ; toute observation est stampée de la
   révision acceptée, ce qui rend la requête du runtime vérifiable, et une révision plus haute
   rebase la composition, une non réconciliable fermant la session
   (`WebTextInputSurfaceTest.aHigherRevisionRebasesTheCompositionAndANonReconciliableCommitClosesTheSession`).
4. **`updateCursor` n’a aucun effet navigateur — limite enregistrée.** Le rect est accepté à la
   révision exacte (`StaleRevision` sinon) et stocké dans le shadow ; c’est le navigateur qui
   dessine son propre caret (`WebTextInputPort.kt:208-216`, `:434-436`, `:446-450`).
5. **Les types delete non computables ne produisent aucune observation — limite enregistrée.** Un
   delete retire un *grapheme cluster* et le DOM ne nomme aucune frontière de cluster sans la
   Segmentation API que Kadre n’embarque pas ; l’edit d’une unité de code rapporterait la moitié
   d’un astral. `deleteContentBackward`/`deleteContentForward` et les autres types non mappés
   produisent donc rien (`WebTextInputPort.kt:257-271`, `:316`) — y compris sur une sélection non
   effondrée, où l’edit serait pourtant calculable exactement ; la réouverture sur le raisonnement
   de `deleteByCut` (ci-dessous) est possible, la limite est conservatrice. `deleteByCut` reste
   mappé, exactement à l’étendue de la sélection (`:308-314`), et le test du port epingle les deux
   bras (`WebTextInputSurfaceTest.aDeleteTheShadowCannotComputePublishesNothingAtAll`).
6. **La soumission est une `Action`, jamais du texte** (`DESIGN.md:1219`). `Enter` sur un élément
   mono-ligne — le navigateur ne tire pas de `beforeinput` pour lui — publie l’action de la config
   (`WebTextInputPort.kt:382-387`) ; sur un `<textarea>` c’est un line break que le `beforeinput`
   porte (`:288-301`, `insertLineBreak`/`insertParagraph` sur un mono-ligne ne produisant rien :
   un edit que le navigateur ne peut pas avoir exécuté serait un mensonge sur le document), et
   `insertTab` suit le mapping AppKit vers `Next` (`:303-306`).
7. **La forme de la cancellation.** Une fin de composition sans commit — Échap — publie
   l’observation terminale `CompositionChanged(null, "", null)` et rien d’autre quand l’événement
   ne porte pas de donnée ; le navigateur réel, lui, porte la chaîne vide finale (un
   `CompositionEvent` réel ne peut pas porter `null` : Web IDL le chaîne en `""`), si bien que le
   port rapporte le retrait que le navigateur a exécuté — un `Replace` que le runtime refuse par
   son propre test d’étendue, sans perdre la terminale
   (`WebTextInputPort.kt:362-374` ; `RuntimeTextInputSession.kt:185-192` ;
   `WebTextInputSurfaceTest.aCancelledCompositionEndsWithoutACommitLeavesNoCompositionBehind`,
   `.aCancellationTheBrowserPerformedReportsTheRemovalTheRuntimeRefusesAndStillEndsClean` ; le
   scénario navigateur `web-text-composition-cancelled`).
8. **La suspension de focus préserve la composition.** La perte d’activation suspend la session
   (`WebHostSession.kt:1244-1247`, `RuntimeSurfaceInput.kt:436-439`), la reprise la réactive à la
   même révision (`WebHostSession.kt:1274-1278`), et l’état suspendu porte le range de
   composition intact (`RuntimeTextInputSession.kt:243-260`).
   **Bras Chromium à connaître** : un blur réel fait annuler sa composition par Chromium (l’IME
   retire le texte composé de l’élément avant la suspension) — le bras « composition préservée »
   est asservi par les tests de surface Kotlin (le point 8) et par le scénario
   `web-text-focus-suspends` dans son arm sans composition active ; la procédure du cahier
   manuel (`manual/phase-5-text-input.md`, procédure 2) observe ce que le vrai blur fait.
9. **Les exceptions d’un listener ferment le *port owner*, pas la session runtime — limite
   enregistrée.** Le containment du port ferme l’owner (les listeners sont retirés, le shadow
   n’est plus digne de confiance) et laisse le session runtime dans son état courant jusqu’à ce
   que l’application la ferme ; chaque opération du port répond ensuite `Closed(TextInputSession)`
   (`WebTextInputPort.kt:395-403`, `:140-155` ;
   `WebTextInputSurfaceTest.aThrowingObservationCallbackClosesTheOwnerAndNeverEscapesTheListener`).
   La session ne devient pas `Closed` d’elle-même : le contrat « app-closable » est la forme
   livrée, et ce document l’enregistre comme telle. Une installation qui échoue est, elle, la
   failure de seam `install-listeners-failed` sans session derrière (`:119-138`).
10. **`keyboard.insertText` tire un `beforeinput`.** Le plan avait noté l’inverse (« ne dispatch
    que `input` ») ; le scénario `web-text-replace-event` prouve le fait livré — l’insertion de
    texte de Playwright arrive en `beforeinput` `insertText` et le port publie le `Replace`
    correspondant (`playwright/web-text-input.spec.mjs:88-108`). La limite honnête reste celle du
    keydown : `insertText` ne produit ni `keydown` ni événement de composition, donc ni action de
    soumission ni observation de composition.

### 3.11 Le drag-and-drop : une offre présentée par un appel, activée par un handler

1. **L’offre est présentée par le seam, jamais par un stimulus.** `dragenter` snapshotte le
   magasin du navigateur — descripteurs (`DropItemKind`, mimes canonisés, `sizeBytes`,
   `displayName` lisibles à l’entrée) — et appelle `onDropEntered(source, position)` dans le
   callback même ; la surface présente l’offre au reducer (`presentDrop`,
   `RuntimeSurfaceInput.kt:217-267`) et, si une offre est présentée, dispatch
   `InteractionEvent.DropEntered` dans la frame DOM (`WebHostSession.kt:1027-1065`), le miroir
   exact de `MinimalWindowSurface.dispatchSynchronousDrop` (`:398-441`). Le reducer commun
   termine l’offre précédente `LeftSurface`, si bien qu’il n’y a jamais qu’une offre active
   (`web-drop-presented-single-active`).
2. **L’acceptation est un `AcceptDrop` à token `Now` ; sans handler, le défaut du navigateur
   tient.** Le handler demande `AcceptDrop(offerId)` sur son token single-use et la surface
   répond par le reducer (`acceptDrop`, `WebHostSession.kt:1044-1062`) ; un handler absent, un
   refus, un autre action ou un autre `offerId` laissent l’offre terminale rejetée
   (`rejectDrop`, `:1064`) et le navigateur non empêché (`web-drop-rejected-without-handler`).
   L’action est dans l’ensemble publié (`WebInteractionStimulus.kt:32-38`) et sa validation est
   celle du seam (`:1046-1052`) ou, hors seam, le reducer (`:1121`).
3. **`dragover` sans offre re-snapshotte et re-présente.** La règle D-D1 dans sa parenthèse :
   le bubbling DOM dépense des offres dans le dos de l’élément — un move sur un enfant tire le
   `dragenter` de l’enfant avant le `dragleave` du parent, et une offre rejetée laisse le même
   état — donc un `dragover` sans offre active reconstruit le snapshot et re-présente par le
   même seam, et le handler décide encore pendant que ce `dragover` est l’événement en main
   (`JsWebDomPort.kt:878-907`, `WasmWebDomPort.kt:905-953`) ; sous un handler qui refuse, chaque
   `dragover` sans offre re-dispatch donc une présentation — la sémantique D-D1 du modèle,
   enregistrée ici. Avec une offre, le `dragover` est `DropMoved` (`WebHostSession.kt:1675-1683`),
   le `dragleave` un `DropExited` qui dépense l’offre (`:1687-1694`), le `drop` un
   `DropPerformed` qui la rend claimable (`:1698-1706`, re-lecture du magasin contre le snapshot
   d’entrée, `JsWebDropEvents.kt:126-156`).
4. **`preventDefault` uniquement pendant qu’une offre est tenue (D-D3), trois sites par port.**
   La question `holdsActiveDropOffer()` — l’offre acceptée que la surface tient, `false` sur une
   surface qui n’admet plus (`WebHostSession.kt:1076`) — est posée dans le callback du `dragover`
   et du `drop`, et c’est la réponse qui décide d’appeler `preventDefault` : sans offre, le
   navigateur garde le sien, quelle que soit la policy, et l’ensemble fermé
   `SUPPRESSED_INPUT_DEFAULTS` n’est pas étendu (`JsWebDomPort.kt:900-907`, `:914-927`, `:986-1006`
   — « these are the only three `preventDefault` sites of this file » ; miroirs Wasm). La
   sentinelle `web-drop-no-prevent-default-without-offer` épingle le bras.
5. **Les mimes non canoniques sont ignorés, jamais fabriqués ; les `File`/`Blob` restent
   internes.** La canonnicité est demandée au constructeur de `DropItemDescriptor` lui-même, règle
   unique de la foundation (`WebDropStimulus.kt:67-99`) ; un `kind` inconnu devient `Binary` avec
   ses mimes réels (`:51-56`), et les handles de fichiers restent dans les closures des payloads
   (`JsWebDropEvents.kt:166-175`), dont `collectBytes` — l’unique lecture publique — livre des
   copies `ByteArray` bornées (`WebDropStimulus.kt:152-176`). Une lecture dont le navigateur n’a
   jamais laissé attacher le payload est `PlatformFailure(Web, "drop", "payload-unavailable")`
   (`:158`), jamais une exception.
6. **Limite enregistrée : une lecture bornée d’un item dont le `sizeBytes` est inconnu répond
   `PlatformFailure(Web, "drop", "byte-read")` au lieu de la failure de limite.** Le runtime
   n’arrive à borner le total d’un item de taille inconnue qu’en cours de lecture, en levant son
   exception de limite *depuis le collecteur qu’il passe à la source*
   (`RuntimeDropTransfer.kt:338-355`) ; or le containment du port Web enveloppe l’appel du
   collecteur et convertit tout throw en la failure de seam (`WebDropStimulus.kt:161-175`), qui
   revient au runtime comme résultat — sa propre branche `ResourceLimitExceeded` n’est donc pas
   atteinte sur ce bras. Un item à taille connue garde la vérification d’amont
   (`RuntimeDropTransfer.kt:314`, `:330-332`). La lecture échoue fermée dans les deux cas ; la
   forme de la failure diffère, et ce document enregistre la différence plutôt que de la corriger
   au prix d’un changement de runtime.
7. **La session reste owner de secours ; sa fermeture ferme l’offre et les transfers.** La
   terminaison de la lane d’input — fermée par la terminaison de la surface, jamais par un simple
   overflow — libère l’offre active et ferme les transfers actifs (`RuntimeSurfaceInput.kt:864-882`,
   `RuntimeDropTransfer` close-once à lecteur unique), le bras que `web-drop-teardown-closes` et
   `web-drop-close-exactly-once` asservissent. Kadre n’est jamais la *source* d’un drag : seuls
   les drops entrants sont reçus.

### 3.12 L’inventaire d’affichage : le fallback exact, et ce que le navigateur ne donne pas

1. **L’énumération multi-display n’est jamais tentée — le fallback est inconditionnel.** Aucun
   navigateur n’énumère les écrans derrière sa fenêtre, et la primitive qui le promet — la Window
   Management API (`navigator.getScreenDetails`) — n’est appelée **aucune fois** dans les sources
   des deux targets, du runtime et du consumer : elle exige une permission à prompt (que le
   registre ne déclenche jamais implicitement), ne vit qu’au top-level du browsing context et
   reste inégale d’un moteur à l’autre. La gate de la roadmap n’admet donc aucune improvisation :
   l’inventaire d’une session attachée est exactement
   `DisplayInventory.Enumerated(primary = viewport, displays = listOf(viewport))` avec
   `DisplayType.HostViewport` (`WebDisplayPort.kt:170-196`) — jamais un inventaire vide, jamais un
   `Unavailable` générique, jamais un second display. La source synthétique des contrats
   empoisonne d’ailleurs `navigator.getScreenDetails` pour enregistrer tout appel (`gamepad-stub.mjs:122-127`),
   et la sentinelle `web-gamepad-no-implicit-prompt` asserte que son registre reste vide.
2. **Le snapshot est poussé à l’installation de l’observateur.** `installSnapshotObserver`
   délivre la réponse courante exactement une fois, hors du lock, au moment de l’install
   (`WebDisplayPort.kt:111-114`) — « so the initial inventory never waits for an event the
   browsing context may never fire » — et les feux suivants de la source re-publient. Une session
   qui s’est attachée sans rien demander ni redimensionner a donc déjà l’inventaire exact à la
   révision 1 du manager et un journal d’événements vide : c’est ce que `web-display-initial-hostviewport`
   asserte (`data-kadre-display-manager` = `rev=1:enumerated:primary=0:displays=1:enumeration=supported`,
   journal vide, `requestAccess` rejoué idempotent à la même révision). Un second install
   remplace le premier — le port retire l’abonnement créé pour l’ancien et pousse la réponse
   courante au nouvel observateur — et un handle d’install remplacé reste appelable en no-op
   (`WebDisplayPort.kt:100-128`, épinglé par `WebDisplayPortTest`).
3. **La mesure, et ses unités.** La source lit les quatre faits du browsing context :
   `innerWidth`/`innerHeight` (le viewport CSS), `devicePixelRatio` et `screen.colorDepth`
   (`JsWebDisplaySource.kt:25-36`, miroir Wasm). Le port en tire : `bounds` et `workArea` — la
   même boîte, le layout viewport en pixels physiques `round(w·dpr) × round(h·dpr)`
   (`WebDisplayPort.kt:171-183`), `scaleFactor` = `devicePixelRatio`, `bitDepth` =
   `screen.colorDepth`, et **un seul mode** de la même taille physique dont
   `refreshRateHz` vaut `null` : le navigateur n’offre aucune primitive de refresh rate que le
   modèle publie honnêtement, et la valeur n’est jamais devinée.
4. **L’observation d’un dpr seul passe par une requête de résolution ré-enregistrée à chaque
   feu.** Un listener `MediaQueryList` `(resolution: <dpr>dppx)` ne survit qu’à un changement de
   ratio ; la source retire la requête qui a feu, ré-enregistre pour le nouveau ratio *avant* de
   livrer l’observation, puis notifie (`JsWebDisplaySource.kt:70-83`). Le dpr est en outre relu à
   chaque mesure (`:25-36`), la posture de la phase 2 : un navigateur qui ne tirerait jamais la
   requête échantillonnerait le ratio au prochain `resize`. Le scénario
   `web-display-dpr-scale-factor` pilote deux changements de ratio par émulation CDP à taille CSS
   constante et asserte une re-publication exacte par changement.
5. **Un viewport inmesurable est un échec, pas une approximation.** Un dpr non fini ou non
   positif, des tailles non positives — filtrées par la source, re-filtrées par le port — répondent
   `TemporarilyUnavailable(retryable = true)`, de `requestSnapshot` comme par l’observateur, et le
   runtime retire l’inventaire échoué (`RuntimeDisplayManager.publishUnavailableLocked`) : « the
   honest state of a viewport nobody can measure right now », jamais un partiel.
6. **Le teardown meurt avec la session.** La fermeture du port désabonne la source et la ferme ;
   `web-display-teardown-quiet` asserte, après un vrai `requestStop`, qu’aucun resize ni
   changement d’émulation suivant ne publie plus rien — pas d’événement, pas de snapshot
   re-publié, pas un seul enregistrement d’animation frame (sentinelle `web-display-no-polling`)
   et pas un nœud créé (sentinelle `web-display-no-dom-creation`).

### 3.13 Devices et gamepads : le poll est la seule source d’état

1. **La découverte est poll-driven.** `navigator.getGamepads()` est la seule source d’état que la
   spec donne ; les événements DOM `gamepadconnected`/`gamepaddisconnected` ne portent aucune
   donnée de pad et signifient une chose — relire maintenant. Le hub partagé de la page
   (`WebGamepadHub`, le frère Web du broker AppKit) enregistre les deux listeners **au niveau de
   la fenêtre partout, et au niveau du navigator seulement là où le navigateur l’accepte** : le
   navigator de Chromium n’est pas une event target (relevé 2026-10-05,
   `navigator.addEventListener` y est `undefined` tandis que `getGamepads` est une fonction,
   `JsWebGamepadDom.kt:36-59`), et un target qui refuse l’écoute n’est pas une erreur — un indice
   manqué coûte une frame de latence, pas le poll.
2. **La boucle de poll ne tourne que tant qu’un port de session est ouvert.** La première
   ouverture de port démarre la boucle et lit une première fois, immédiatement — un pad que le
   navigateur rapporte déjà est dans le tout premier snapshot du runtime, sans événement — et la
   dernière fermeture annule la frame en attente et retire les listeners
   (`WebGamepadHub.kt:82-96`, `:321-329`). Exactement une animation frame est en attente à la
   fois ; un page caché ne tire pas de frames et ne lit donc pas du tout.
3. **Le diff est par index DOM, contre le dernier état canonique observé.** Un nouvel index est
   une connexion dont le descripteur est gelé depuis ce très poll (une reconnexion est une
   nouvelle connexion et gèle un descripteur frais) ; une lecture qui change l’état canonique est
   un `StateChanged` ; un index disparu une déconnexion. Les trous du poll n’inventent rien —
   `web-gamepad-no-phantom` asserte qu’un index absent ne produit ni pad fantôme ni
   renumérotation — et un pad hostile (NaN persistants, valeurs hors fenêtre, zéros signés) ne
   publie que les événements que ses états canoniques diffèrent, jamais un flot de no-ops
   (`web-gamepad-state-poll`).
4. **Le cycle de vie va à tous les ports, l’état aux seuls routés.** Connexion, déconnexion et
   routage sont diffusés à chaque port ouvert, suspendus compris (le précédent AppKit : le cycle
   de vie est toujours diffusé) ; un changement d’état ne va qu’aux ports routés, parce qu’une projection
   suspendue publie des contrôles neutres et qu’une vraie lecture contredirait le snapshot
   qu’elle porte (`WebGamepadHub.kt:203-233`). Le routage est celui du broker AppKit verbatim :
   foreground-actif + policy (`AllForegroundSessions`, ou l’arbitrage `ActiveSessionOnly`), une
   suspension publie le neutre et enregistre les vraies lectures pour la reprise, qui livre ce
   que le pad lit maintenant (`web-gamepad-routing-suspended-neutral`).
5. **Le descripteur est le mot du navigateur.** `mapping == "standard"` nomme les 17 boutons et
   4 axes du layout standard en ordre DOM ; tout autre mot — chaîne vide, mot vendor, membre
   absent — ne promet rien, et chaque contrôle devient un code natif au compte que le pad rapporte
   (`WebGamepadMapping.kt:55-63`). L’état s’apparie positionnellement au descripteur et est
   canonisé vers les fenêtres du modèle — une lecture hors fenêtre est bornée, une non-finie lit
   neutre, la pression est décidée depuis la lecture brute, avant tout bornage (`:71-79`) — la règle
   asservie par `WebGamepadMappingTest` et `web-gamepad-descriptor-exact`.
6. **Les devices génériques : une liste vide honnête.** Le navigateur n’offre aucune primitive
   d’inventaire de périphériques d’entrée génériques ; `DeviceInventory.Enumerated(devices = [],
   gamepads = …)` est donc honnête avec sa liste `devices` vide, et rien n’est fabriqué pour la
   remplir — l’inventaire complet et vide est la forme exacte de « rien à énumérer »
   (`BACKEND-CAPABILITIES.md` §5).
7. **Le secure context, et la branche défensive par pad.** `getGamepads` n’est exposé qu’en
   secure context : un page insecure réelle n’expose aucun pad et l’inventaire reste énuméré,
   vide et complet. Le port d’effets garde une branche par pad — un pad qui atteindrait un poll
   insecure (la source synthétique du projet `chromium-insecure`, précisément) publie
   `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect))` pour ses
   effets (`WebGamepadEffects.kt:63-66`) — branche défensive, non observable sur du vrai
   matériel, dont le scénario asserte les observables (§3.14, item 8).
8. **Le teardown.** La fermeture du port révoque les owners d’effets qu’il tient, re-arbitre les
   survivants avant que quiconque voie le trou, et la dernière fermeture arrête le poll —
   `web-gamepad-session-close-quiet` asserte qu’aucun événement, aucune re-publication d’inventaire
   et pas une seule animation frame ne suivent un `requestStop` de la session, quelles que soient
   les mutations de pads que le page continue d’effectuer (sentinelle `web-gamepad-teardown-quiet`).

### 3.14 Les effets gamepad : la primitive du navigateur, et les préconditions honnêtes

1. **La primitive est `vibrationActuator.playEffect`, sur la forme du dictionnaire WebIDL.** Le
   lancement construit le dictionnaire `effectParameters` que le `playEffect(type,
   effectParameters)` du navigateur lit — `duration`, `strongMagnitude`, `weakMagnitude`
   toujours, et les membres `leftTriggerMagnitude`/`rightTriggerMagnitude` du trigger-rumble
   seulement là où le modèle en a un : un membre omis est littéralement absent (le spread ne
   l’écrit pas), jamais un zéro qui masquerait une magnitude énoncée
   (`JsWebGamepadDom.kt:104-119` ; miroir Wasm). La valeur résolue de la promesse n’est jamais
   lue — le verdict synchrone est tout le contrat.
2. **Les genres annoncés sont le mot de l’actuateur.** La liste `effects` que l’actuateur déclare
   (`GamepadHapticActuator.effects` du Chromium récent, p.ex. `["dual-rumble"]`) nomme ce qui est
   annoncé ; un actuateur qui ne déclare rien est sondé **une fois par connexion** par un
   dual-rumble de durée nulle et de magnitude nulle — sans effet de bord selon la spec — dont la
   réponse est gelée dans la capability du pad, exactement comme le descripteur
   (`WebGamepadEffects.kt:134-151`). Une sonde qui refuse — le coin épinglé par
   `WebGamepadEffectTest` — ne laisse rien annoncer : la capability est `Unsupported`.
3. **`LocalizedHaptic` n’est jamais annoncé, jamais lancé.** Le navigateur n’a aucune primitive
   pour les haptics localisés ; le genre est refusé `InvalidRequest("effect")` avant que tout
   actuateur soit consulté (`WebGamepadEffects.kt:98`), et la sentinelle
   `web-gamepad-no-localized-haptic` asserte que la cellule reste `Unsupported` sur ce genre.
   **`TriggerRumble` passe quand le navigateur le déclare**, ses magnitudes
   `leftTrigger`/`rightTrigger` portées par le seam jusque dans le dictionnaire
   (`:99-112`, le fix `d12a62be`) ; un `trigger-rumble` que l’actuateur n’a jamais déclaré est
   refusé exactement comme un genre non annoncé (`web-gamepad-effect-unsupported-kind`).
4. **`maximumDuration` reste `null`** : le navigateur serre lui-même la borne, et la contrainte
   n’enregistre aucune limite qu’elle n’a pas mesurée (`WebGamepadEffects.kt:76`). La durée
   transmise est celle que le navigateur lit : millisecondes entières, au moins une, au plus un
   `Int` (`:176-177`).
5. **Le verdict synchrone est tout le contrat, et le rejet asynchrone est rapporté.** Un
   `Accepted` signifie que l’appel a atteint le navigateur et lui a remis sa promesse ; un
   `Refused` signifie que le navigateur a refusé avant toute promesse, porté
   `PlatformFailure(Web, "gamepad-effect", "refused")` pour `playEffect` et par le mot du
   navigateur pour un `reset` (`:153-173`, `WebGamepadEffect.requestStop`). Une promesse qui
   rejette *ensuite* n’a aucun outcome synchrone honnête : chaque promesse porte un `catch` qui
   la rapporte sur le reporter de failures du wiring (`WebGamepadEffectReporting`,
   `JsWebGamepadDom.kt:187-194`) — aucun changement d’état, aucune failure mappée sur un outcome
   déjà répondu. Limite enregistrée : le holder du reporter est global au page — le dernier
   wiring installé possède les rapports du page — parce qu’un rejet atterrit dans la réalisation
   target qui a fait l’appel, où aucune référence par session n’atteint.
6. **Un pad déconnecté refuse avant tout appel d’actuateur ; un pad suspendu lance.** Le hub
   résout le pad et refuse `Closed(Gamepad)` avant de toucher l’actuateur quand le port ou le pad
   n’est plus là (`WebGamepadHub.kt:270-281`) — `web-gamepad-effect-stop` asserte le refus et
   zéro appel après une vraie disparition du pad. Un pad **connecté mais suspendu** (routing
   suspendu) lance son effet : la sémantique livrée est celle d’AppKit — un effet est une action
   initiée par l’application, pas une livraison d’input — et aucun précondition de routage
   n’existe ni chez AppKit ni ici (décision de la phase, enregistrée).
7. **Un owner par lancement, un stop, exactement une fois.** L’owner créé à l’`Accepted` doit au
   runtime un reset unique ; tout stop, close ou révocation ultérieur est un no-op silencieux qui
   ne touche plus l’actuateur (`WebGamepadEffect.kt:216-252`), et la sentinelle
   `web-gamepad-effect-once` asserte qu’un stop ne relance ni ne re-reset. Un port fermé pendant
   qu’un effet tourne révoque ses owners — le reset unique de la révocation rapporte un refus au
   reporter, son caller étant le teardown. Limite enregistrée : le navigateur arrête lui-même un
   effet dont la durée s’est écoulée ou dont le pad s’est déconnecté ; un stop tardif est le
   reset d’un actuateur déjà silencieux, inoffensif selon la spec.
8. **Les préconditions manquantes produisent l’état prévu, jamais un prompt.** Le scénario
   insecure-context (`chromium-insecure`, `--host-resolver-rules=MAP insecure.kadre.invalid
   127.0.0.1`) asserte les observables : `isSecureContext` faux, le pad découvert et décrit
   normalement, sa capability d’effets `unsupported:gamepadeffect`, la requête répondue
   `failure:unsupported:gamepadeffect`, et le registre des APIs à prompt de la source synthétique
   — `permissions.request`, `getScreenDetails`, `requestMIDIAccess`, `share`,
   `Notification.requestPermission` — vide (sentinelle `web-gamepad-no-implicit-prompt`), comme
   les appels d’actuateur.

Les cinq lignes de capture du registre sont celles de la phase 7, et leur lecture partage
trois faits. D’abord, la sonde est **fondée sur la présence et figée une fois** : le secure
context, la présence de `getDisplayMedia` et celle du `MediaStreamTrackProcessor`, et le genre
canvas de l’élément attaché sont lus à la construction du port (`WebHostSession.kt:615-622`)
et gèlent les capabilities du snapshot — un browsing context qui n’offre pas une primitive
publie l’`Unsupported` exact de cette absence, jamais une capacité devinée. Ensuite, la
**lecture de permission est le seul fait de construction qui s’installe tard** : le query
promise-based du navigateur n’a pas de forme synchrone, si bien que l’instantané initial porte
la cellule transitoire `Unavailable(Unsupported(CapturePermission))` des deux scopes jusqu’à
ce que la réponse installée republish le snapshot — une seule republication, le runtime
dédoublonnant les instantanés identiques (`WebCapturePort.kt:102-111`, `:369-374`). Enfin, les
**effets navigateur du plan de contrôle tiennent dans deux appels explicites** — le pick du
consentement et le `captureStream()` du canvas — : les canaris de la source synthétique
(`capture-stub.mjs`) enregistrent toute autre API à prompt, et les scénarios de `BCK-010`
assertent le registre vide autour de la lecture, du refresh et du refus. La preuve de session
de `BCK-011` court dans le troisième projet Playwright (`chromium-capture`), dont les
arguments de lancement sont le trio sanctionné de Chromium pour les tests de capture — la
posture exacte est enregistrée dans l’entête de `web-capture.spec.mjs` et reprise au §3.16 ;
le vrai picker et les vrais pixels sont l’affaire du cahier manuel
(`manual/phase-7-capture.md`).

### 3.15 Le plan de contrôle de capture : l’inventaire honnête, la lecture de permission et les refus

1. **Aucune énumération de sources, jamais.** Aucun navigateur n’expose d’inventaire des
   surfaces capturables avant un consentement, et l’adapter n’en fabrique aucun : les sources
   publiées sont **toujours** `CaptureSources.HostPickerOnly` (`WebCapturePort.kt:459-463`),
   la capability `sourceEnumeration` le dit inconditionnellement (`:437`), et
   `refreshSources` est le no-op honnête qui rend l’objet instantané courant — readback et
   capabilities compris — sans atteindre la moindre mécanique de consentement (`:161-166`).
   La Window Management API (`getScreenDetails`), déjà écartée par l’inventaire d’affichage
   (§3.12, item 1), n’est pas davantage appelée ici : l’inventaire de capture n’a pas de
   forme pré-consentement à imiter. La mutation que le contrat tue est un prompt pendant la
   lecture ou le refresh : un seul pick hors du chemin de requête explicite est un échec de
   scénario (`web-capture-readback-no-prompt`, sentinelles `web-capture-no-implicit-prompt`
   et `web-capture-no-picker-at-readback`).
2. **La sonde est fondée sur la présence, et le secure context ferme tout.** Le secure
   context est le premier rung de chaque chaîne de capabilities : un contexte insecure publie
   `Capability.Unsupported(Unsupported(CaptureOpen))` sur les trois cibles et
   `Unavailable(PlatformFailure(Web, "capture-capability", "secure-context"))` sur le picker
   (`WebCapturePort.kt:391-394`, `:411-415`, `:439-441`) — l’état prévu, jamais un prompt,
   le scénario `web-capture-insecure-unsupported` n’assertant que des observables. Les deux
   autres rungs sont la présence de `getDisplayMedia` (cause `"no-get-display-media"`) et
   celle du `MediaStreamTrackProcessor` (`WebCapturePortTest`
   `.missingGetDisplayMediaUnsupportedWithNoGetDisplayMediaCauseOnThePicker`,
   `.missingTrackProcessorUnsupportedWhileThePickerStaysAvailable` — le picker, lui, reste
   disponible sans pompe : c’est la pompe qui manque, pas le consentement).
3. **La lecture de permission est la seule, et elle ne prompte jamais.** L’état de permission
   vient d’un unique `permissions.query({ name: "display-capture" })` — une requête qui par
   spec ne prompte pas (`JsWebCaptureDom.kt:436-438`, miroir Wasm) ; les trois mots de
   `PermissionStatus.state` (`granted`, `denied`, `prompt`) se mappent structurellement, et
   toute autre réponse — API absente, nom inconnu, query rejetée — vaut
   `PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission))`
   (`WebCapturePort.kt:476-481`). **L’état de fenêtre reflète l’état d’écran** : la scope
   `window` n’a aucun fait navigateur propre — un consentement unique gouverne ce que le
   picker a offert — donc les deux cellules sont toujours le miroir l’une de l’autre
   (`CapturePermissionState(permissions, permissions)`, `:459-463` ; sentinelles
   `web-capture-window-mirrors-screen`), et aucune garantie distincte de fenêtre n’est
   publiée. L’instantané initial porte la cellule transitoire
   `Unavailable(Unsupported(CapturePermission))` jusqu’au règlement de la promesse, puis
   **une** republication porte la réponse installée — le runtime dédoublonne les
   instantanés identiques, et un observateur installé après coup reçoit la réponse courante
   (`WebCapturePort.kt:108-111`, `:334-345`, `WebCapturePortTest`
   `.observerInstalledAfterTheReadbackSeesTheCurrentSnapshot`).
4. **`requestPermission` est le seul chemin de consentement explicite, et les deux scopes
   courent le même flow.** Le navigateur confond consentement et choix de source : une
   requête de permission est donc un `getDisplayMedia` dont le flux ramassé est arrêté
   immédiatement — le verdict seul est gardé, jamais le stream (`WebCapturePort.kt:129-159`).
   Le ramassage tenu répond `Granted` aux deux scopes ; `NotAllowedError`/`AbortError`
   répondent `Denied(canRequestAgain = true)` ; `NotFoundError` est la
   `TemporarilyUnavailable(retryable)` d’un navigateur sans source à offrir ; tout autre code
   est une `PlatformFailure(Web, "capture-permission", <nom du navigateur>)` — et ces lignes
   de failure **répondent une failure** plutôt qu’un snapshot : le runtime refuse de son côté
   une permission « réussie » qui laisserait la permission visée non résolue. Les mots de
   prévisualisation du picker ne sont la promesse de personne : le flux abandonné ne state
   aucun hint (`:131-136`).
5. **La cible `Source` est refusée structurellement, et l’id est opaque.** Le reserve du port
   répond `Unsupported(CaptureOpen)` à un `CapturePortTarget.Source` avant quoi que ce soit
   de navigateur-facing — zéro interaction seam, rien de réservé, rien de demandé
   (`WebCapturePort.kt:174-176`, `WebCapturePortTest.reserveOfAnInventorySourceIsRefusedWithZeroSeamInteraction`)
   — et la ligne est permanente : il n’existe jamais de source à cibler, puisqu’il n’existe
   jamais d’inventaire. `CaptureSourceId` est opaque au runtime et inconstruisible depuis le
   driver (constructeur interne à la foundation) ; l’admission du runtime, elle, répond
   `InvalidRequest("request.target")` à un id que l’inventaire ne nomme pas
   (`RuntimeCaptureManager.kt:287-307`). Le scénario navigateur
   `web-capture-source-refused-before-picker` prouve le même entonnoir d’admission avec la
   demande refusée que ce driver peut construire — une open à région — et la sentinelle
   `web-capture-no-fabricated-sources` épie tout inventaire qui rendrait une cible
   résolvable.
6. **L’admission `HostChoice` refuse la région avant tout picker, puis passe les hints au
   navigateur.** Le navigateur choisit ses propres bornes : une requête à région est refusée
   `Unsupported(CaptureOpen)` — la forme d’admission AppKit — avant que le picker ne soit
   lancé (`WebCapturePort.kt:279-281`). Le picker emporte les hints de la requête : le mode
   curseur en mot `cursor` du navigateur (`never`/`always`/`motion`,
   `WebCaptureMapping.kt:150-154`) et l’intervalle minimal de frame en plafond `frameRate`
   réciproque (`:160-161`) — des **hints que le navigateur peut ignorer**, jamais des
   promesses, chaque hint omis étant littéralement absent du dictionnaire
   (`JsWebCaptureDom.kt:453-460`). Le refus du picker se mappe au reserve : la picker
   congédié (ou l’activation manquante — le navigateur répond les deux `NotAllowedError`)
   est l’annulation utilisateur `UserCancelled(CaptureOpen)` ; `NotFoundError` la
   temporaire retryable ; tout autre code une `PlatformFailure(Web, "capture-permission",
   <nom>)` portant le nom d’erreur du navigateur verbatim (`:319-330`,
   `WebCaptureStreamTest.hostChoicePickerRefusalsMapAtReserve`, `.pickerCancelFailsReserve`).
7. **La cible `Surface` résout l’id contre la seule surface enregistrée, et son open est
   sans consentement.** La session enregistre sa surface primaire à sa création — le
   registre est unique par port (`WebHostSession.kt:637`, `:665`,
   `WebCapturePort.kt:120-127`) — et un id étranger est refusé
   `InvalidRequest("request.target")`, la forme du runtime pour une cible irrésolvable,
   sans une interaction seam (`:216-219`). L’élément attaché est relu vivant à chaque
   sonde et le genre canvas est lû par le `instanceof` du navigateur (`JsWebCaptureDom.kt:505-508`)
   ; le backstop d’un élément non-canvas répond le même `Unsupported(CaptureOpen)` que la
   capability gelée (`WebCapturePort.kt:220-221`,
   `WebCaptureSurfaceTest.nonCanvasSurfaceRefusesWithTheFrozenCapabilityCauseAsTheConsistentBackstop`).
   `captureStream()` est l’unique effet navigateur (`:223`) ; un port sans pompe libère le
   flux que le canvas vient de démarrer plutôt que de réserver des frames qu’il ne peut pas
   produire (`:231-238`), et le curseur effectif est `Hidden` — un canvas ne composite
   aucun curseur, ce que l’hôte a dessiné dans les pixels voyage avec eux (`:254`).
8. **La caméra est hors de la v1.** Il n’existe aucun chemin `getUserMedia` : la caméra est
   une sémantique de surface distincte que le même flow navigateur ne fournit pas, et la
   fonction reste canarisée — la source synthétique enregistre tout appel, et aucun
   scénario n’y touche sous aucun nom (`capture-stub.mjs`, zéro occurrence de `getUserMedia`
   dans les sources des deux targets). Ce hors-périmètre est une décision enregistrée, pas
   une absence provisoire.

### 3.16 Les sessions et les frames : la pompe `MediaStreamTrackProcessor`, la borne et les terminaisons

1. **La pompe n’a qu’une primitive, et elle ne crée aucun DOM.** Le lecteur de frames est
   construit depuis le `MediaStreamTrackProcessor` du track accordé — forme dictionnaire de
   la spec d’abord, forme positionnelle des premiers Chromium derrière (`JsWebCaptureDom.kt:491-496`)
   — et sa `readable` est lue un read à la fois (`:183-229`). Aucun élément vidéo, aucun
   canvas de traitement, aucun nœud : le seam ne crée rien, il lit le pipe que le navigateur
   pose. Les moteurs qui n’ont pas la primitive, ou qui la déclarent autrement, publient la
   capability `Unsupported(CaptureOpen)` correspondante (§3.15, item 2) ; leur comportement
   réel est le matériel du nightly et du cahier manuel
   (`manual/phase-7-capture.md`, procédure 5), jamais une déclaration de ce registre.
2. **La lecture des layouts tolère la divergence livrée de Chromium.** La spec de
   `VideoFrame.copyTo` nomme les entrées de PlaneLayout `{destinationOffset, copyBytes}` ; le
   Chromium épinglé (148) livre `{offset, stride}` — relevé sur ce navigateur, que les
   lecteurs tolérants du seam essaient les mots de la spec d’abord, puis le mot livré
   (`JsWebCaptureDom.kt:304-335`) ; le row stride rapporté est pris pour vérité, le stride
   serré par défaut n’ayant valeur que d’absence (`:295-301`). Le nom des dimensions visibles
   d’une frame est `VideoFrame.displayWidth`/`displayHeight` — il n’existe pas de
   `visibleWidth`/`visibleHeight` sur une frame, et le contrat navigateur a pris un lecteur
   de ces mots absents : le fix livré lit les vrais (`:341-344`).
3. **La configuration précède les frames, et la cadence reste `Unknown`.** Le navigateur ne
   dit rien d’honnête sur la taille ou le format avant une frame : le start lit la première,
   en dérive la configuration complète (révision 0) et la publie **avant** tout frame ; une
   frame ultérieure de taille ou de format livré différents publie
   `CaptureEvent.Reconfigured` une révision en avance — la discipline du runtime — avant la
   frame qui la porte (`WebCaptureStream.kt:134-196`, `:253-262`,
   `WebCaptureStreamTest.configurationPrecedesFramesAndReconfigurationPrecedesItsFrame`).
   `CaptureCadence.Unknown` est la seule valeur honnête : le navigateur n’offre aucune
   primitive de cadence, aucun équivalent de `refreshRateHz` n’est promis, et
   `minimumFrameInterval` ne voyage que comme le hint `frameRate` de l’admission (§3.15,
   item 6), jamais comme une garantie de rythme. Les timestamps suivent le mot du navigateur,
   les silences compris (`WebCaptureMapping.kt:167-172`).
4. **La borne se lit avant toute copie, et elle est terminale.** Le `allocationSize` du
   navigateur pour les mêmes options est lu avant qu’un buffer existe ; une frame au-delà de
   `maxFrameBytes` est fermée sans copie — la première fait échouer le start, une frame
   courante termine le flux — avec
   `ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes)`
   (`WebCaptureStream.kt:170-185`, `:274-283`) ; l’invariant never-copy est structurel,
   l’appel de copie étant derrière la vérification sur chaque chemin
   (`WebCaptureStreamTest.oversizedFrameFailsBeforeCopy`,
   `.oversizedFirstFrameFailsStartBeforeCopy`). Les refus du pipe — allocation, read, copie,
   crop — portent le **nom d’erreur du navigateur verbatim** dans
   `PlatformFailure(Web, "capture-stream", <code>)` (`:446-447`,
   `WebCapturePipeException`, `WebCaptureDom.kt:141`), le containment normalisant tout
   `Throwable` de seam en cette forme (`db1f9d2d`).
5. **Les terminaisons sont exactement une fois, et chacune a son outcome fermé.** Le stop
   demandé par l’application — le `close()` du stream inclus — arrête reader puis track et
   répond `Stopped(Requested)` ; la fin externe du track — le « Stop sharing » du navigateur,
   que le DOM ne signale que par l’événement `ended`, enregistré avant le premier read —
   répond `SourceCompleted` ; un refus du pipe répond
   `Failed(PlatformFailure(Web, "capture-stream", <code>))` ; le premier fait gagne et
   chaque chemin de libération funne dans une release idempotente — reader, track stop,
   handle seam (`WebCaptureStream.kt:199-201`, `:220-222`, `:408-410`, `:415-434`). Une
   suspension annulée pendant le premier read libère l’état de flux exactement une fois
   avant de surface (`:148-152`), et un start échoué ne dit rien au listener (`:441-444`).
6. **La révocation est une fin de track, rien de plus distinct.** Les navigateurs signalent
   « Stop sharing » uniquement comme la fin du track accordé : `CaptureOutcome.SourceCompleted`
   est l’outcome fermé de la révocation, et le stop-reason `PermissionRevoked` du modèle
   n’a aucun déclencheur navigateur distinct — la limite est documentée ici, jamais
   contournée par un synonyme inventé (`WebCaptureDom.kt:45-51`,
   `WebCaptureStreamTest.trackEndedExternallyCompletesSource`,
   `.trackEndedExternallyCompletesTheSurfaceSource`).
7. **Rien ne survit à la session, et rien n’est réémis après le stop.** Un read déjà en
   vol quand le stop atterrit est libéré, jamais délivré — la frame dont personne ne
   voulait est le handle du navigateur à fermer, pas une fuite Kotlin (`:234-237`,
   `:299-301`) ; le journal et les compteurs gèlent à la terminaison
   (`web-capture-no-frame-after-stop`), et aucun objet stream, track, reader ou frame ne
   survit à la fin de session — le comptage du stub le lit dans le `readyState` du
   navigateur lui-même (`web-capture-stop-exactly-once`, sentinelle
   `web-capture-no-stream-leak`, `web-capture-track-stop-immediate` ; la fermeture du port
   va avec les composants de session, `WebHostSession.kt:721-742`).
8. **La conversion de format est enregistrée sur la surface de diagnostics du Web, pas au
   niveau session.** Un mot natif hors des trois promis (`Rgba8`, `I420`, `Nv12`,
   `WebCaptureMapping.kt:46`) est converti par l’option `format` de `copyTo` vers la première
   préférence promise, sinon `Rgba8` (`:70-75`), et la conversion est enregistrée
   `CaptureDiagnostic.BackendFallback` **avant** la frame qu’elle concerne, une fois par
   paire distincte (`WebCaptureStream.kt:264`, `:378-394`). Le SPI du stream ne porte aucun
   canal de diagnostics du backend vers la session : le journal est observable sur la
   propriété `WebCapturePump.diagnostics` — la couche Web qui a fait la conversion, premier
   producteur de `BackendFallback` du dépôt — et les diagnostics de session ne portent que le
   `FrameDropped` synthétisé par le runtime (`RuntimeCaptureSession.kt:177`). C’est une
   limite enregistrée de forme livrée, pas un trou : la conversion est bornée, annoncée dans
   la configuration effective, et nul ne prétend l’avoir livrée au format demandé
   (`WebCaptureStreamTest.conversionRequestRecordsBackendFallbackBeforeTheFrame`).
9. **Le chemin Surface ajoute deux rulings au même pipe.** Le Rgba8 d’une source canvas
   répond `Premultiplied` — le compositing propre du canvas, là où le silence du navigateur
   sur une frame display reste `Unknown` (`WebCaptureMapping.kt:143-147`) — et la région
   demandée est le crop de décision 8 : `new VideoFrame(frame, { visibleRect })` est produit
   entre le reader et la pompe, si bien que la forme lue, la borne et la copie portent toutes
   sur la frame rognetée et que la configuration publie la taille rognetée et la région
   elle-même (`WebCaptureSurface.kt:54-74`, `WebCaptureStream.kt:520-522`, `:371-375`). Les
   deux handles ne se chevauchent jamais en vol : le constructeur du crop clone, l’original
   est libéré dès que le crop existe — avant toute copie, exactement une fois, sur le succès
   comme sur le refus du navigateur (`WebCaptureSurfaceTest.regionRequestStagesTheVisibleRectAndClosesTheCropFrameExactlyOnceAfterItsCopy`).

## 4. Portée restante au regard de §8

Ce registre couvre les features du domaine input que les phases 3 et 5 bornent ou activent, les
quatre champs de `SurfaceUpdate`, les features d’interaction et de fenêtre que la phase 4
livre (§2, lignes interaction/fenêtres ; §3.7-3.8), puis les features displays, devices,
gamepads et `platformAccess` que la phase 6 livre (§2, quatre lignes du bas ; §3.12-3.14). Le
**verrouillage de pointeur quitte la portée
restante de l’input** : `InteractionAction.LockPointer` est désormais une action publiée et
prouvée de ce target (§3.7), et la réserve D13 de la phase 3 ne vaut plus que pour le champ
`SurfaceUpdate.pointerCapture`, où `Locked` reste refusé `Unsupported(UpdateSurface)` — le verrou
passe exclusivement par l’action. Les **lignes touch, gestures, drag-and-drop et IME/text input
quittent à leur tour la portée restante** : la phase 5 les produit (§2, §3.9-3.11), avec les
limites enregistrées que ses contrats portent — gestures `Unsupported(GestureInput)` sans
recognizer (D-T2, que `BACKEND-CAPABILITIES.md:133` autorise explicitement), text input borné à
`<input>`/`<textarea>` en v1 (D-X3), `updateCursor` sans effet navigateur, lectures de drop sans
faille de limite sur les items de taille inconnue (§3.10-3.11).

La **phase 6 vide à son tour le reste de sa liste** : l’inventaire d’affichage est le fallback
exact `HostViewport`, inconditionnel et poussé à l’install (§2, §3.12) ; l’observation
devices/gamepads est le poll `getGamepads()`, l’inventaire toujours énuméré et la liste
`devices` honnêtement vide (§2, §3.13) ; les effets gamepad sont gouvernés par la capability
gelée par connexion (§2, §3.14) ; `rawInput` — déjà une ligne du §2 — gagne son scénario
navigateur de non-appel, `web-gamepad-raw-input-unsupported`, qui lit la cellule de capability et
compte zéro registration d’`EventTarget` autour de la requête. Les **deux lignes `platformAccess`
quittent aussi la liste, vérifiées à leur état livré** : la ligne de surface vaut
`G, withWebElement` dans la matrice normative (`BACKEND-CAPABILITIES.md` §4, ligne 79) et dans
les deux façades — la lease est prouvée par les quatre scénarios `web-element-lease-*` de
`INT-004` depuis la phase 2 (§2) — et la ligne de fenêtre vaut « — (aucune `Window`) » : le host
Web n’ouvre jamais de `Window` (§3 de `BACKEND-CAPABILITIES.md`, `WindowManagerState.windows`
reste vide), la cellule normative elle-même enregistre qu’il n’y a rien à produire, et la phase 4
ne l’a pas changée.

La **phase 7 produit à son tour les trois dernières lignes de capture que la phase 6 laissait à
cette phase** : la cible `HostChoice` est le picker du navigateur derrière un consentement
explicite, sous les trois préconditions sondées (§2, §3.15) ; la cible `Source` est
structurellement `N(CaptureOpen)`, refusée avant tout picker, l’id étant opaque et
inconstruisible sur ce target (§2, §3.15 item 5) ; la cible `Surface` est le canvas de la
surface primaire, sans consentement, région-croppable (§2, §3.16 item 9). Les lignes restantes
de la matrice de `BACKEND-CAPABILITIES.md` §4 sont dès lors toutes produites ou vérifiées à leur
état livré pour ce target, à une seule exception vérifiée : le **signal de pression mémoire**
(cellule Web `C`), qu’aucune phase de cet adapter n’a livré — zéro occurrence dans les sources
des deux targets — et dont l’audit de la phase 8 (« auditer chaque API du catalogue applicable
à Web et documenter `Supported`, `Unavailable` ou `Unsupported` avec son contrat et sa preuve »)
est le propriétaire déclaré. Les lignes que les phases de ce registre couvrent — input,
interactions et fenêtre, inventaires, gamepads, `platformAccess` et les trois cibles de
capture — portent chacune leur ligne de registre ou leur statement de phase ci-dessus, et les
lignes livrées avant la création du registre (`lifecycle`, `surface metrics`) portent les
statements de leurs propres phases de la roadmap ; §8 rappelle qu’« une ligne manquante empêche
l’adapter d’être déclaré supporté » : le signal de pression mémoire reste donc à produire avant
toute déclaration « supported » de l’adapter Web, sans que cette phase le rouvre.

## 5. Références

- [Contrats des adapters et matrice de capabilities](../BACKEND-CAPABILITIES.md) — mandat §8, matrice §4, points d’attachement Web §6.3
- [Design Kadre](../DESIGN.md) — §15.3 (input ordinaire Web), §10.3 (IME), §10.4 (drag-and-drop), §9.6 (interactions transitoires)
- [Registre des contrats](../contracts/registry/contracts.tsv) — lignes `BCK-003`/`BCK-004`/`BCK-006` (sources `DESIGN.md#15.3`/`#10.4`) et `BCK-001`/`BCK-005`/`INT-003` (sources `DESIGN.md#15.3`, `#10.3` et `INTEROP-EXPORTS.md#6`, preuves `js`/`wasmJs`) ; lignes `BCK-007`/`BCK-008`/`BCK-009` de la phase 6 (source `WEB-IMPLEMENTATION-ROADMAP.md#Phase 6`, preuves `js`/`wasmJs`) ; lignes `BCK-010`/`BCK-011` de la phase 7 (source `WEB-IMPLEMENTATION-ROADMAP.md#Phase 7`, preuves `js`/`wasmJs`)
- [Charte manuelle Web Phase 3](../contracts/driver/web/manual/phase-3-input.md) — frontières non déterministes que ce registre ne prétend pas couvrir
- [Charte manuelle Web Phase 4](../contracts/driver/web/manual/phase-4-interactions.md) — preuves plein écran et pointer lock sur vrai écran, popup réelle
- [Charte manuelle Web Phase 5](../contracts/driver/web/manual/phase-5-text-input.md) — IME OS réel, annulation de composition, `touch-action` du host, contenteditable, drop hors headless
- [Charte manuelle Web Phase 6](../contracts/driver/web/manual/phase-6-displays-devices.md) — gamepad réel (connexion OS, rumble réel, porte de confidentialité de Chromium), multi-display réel, matrice insecure-context au-delà du faux hostname, `vibrationActuator` sur Firefox/WebKit
- [Charte manuelle Web Phase 7](../contracts/driver/web/manual/phase-7-capture.md) — picker réel et vignettes de sources, pixels réels écran/fenêtre/onglet et comportement du curseur, interplay permission d’enregistrement macOS/consentement navigateur, barre « Stop sharing », comportement Firefox/WebKit, confirmation caméra hors v1
- [Driver navigateur Web](../contracts/driver/web/README.md) — limites des phases 3 à 7 et tables de disponibilité publiées
