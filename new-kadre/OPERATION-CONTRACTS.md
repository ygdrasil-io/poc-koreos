# New Kadre — Contrats fermés des opérations publiques

**Statut :** matrice normative fermée.  
**Portée :** toutes les fonctions publiques qui observent, mutent, attendent ou transfèrent un owner.

## 1. Règles communes

1. Une failure absente de la ligne d’une opération ne peut pas être retournée directement par cette opération. Une implémentation doit la traduire vers une failure admise, un outcome admis ou une erreur de programmation documentée.
2. `CancellationException` n’est jamais encapsulée. Les colonnes de cancellation ci-dessous s’appliquent avant toute conversion en `KadreResult`.
3. `IllegalArgumentException` est réservé aux constructeurs/builders de valeurs localement invalides. Une valeur valide mais incompatible avec l’état ou les capabilities courantes produit un `KadreResult.Failure`.
4. `IllegalStateException` est réservé aux deadlocks détectables (`awaitTermination` depuis son propre enfant/collector) et à l’accès au contenu d’une lease déjà invalidée (`CaptureFrame.copyPlanes`).
5. `PlatformFailure` n’est admis que sur les lignes qui franchissent réellement un SDK ou un host. Un backend fake doit utiliser la failure injectée, pas fabriquer un `PlatformFailure`.
6. Un changement concurrent de capability ne crée aucun nouveau type d’échec : la capability est publiée d’abord, puis l’opération utilise l’une des failures admises par sa ligne.
7. Après le point de commit irréversible, annuler le waiter ne rollback jamais l’opération. L’autorité tardive indiquée dans la matrice doit rester observable.
8. Avant le handoff d’un owner, Kadre le ferme si le caller est annulé. Après handoff, seul `close()`/`requestStop()` de l’owner ou le teardown parent le ferme.

Abréviations utilisées dans les tables :

| Abréviation | Variante exacte |
|---|---|
| `Unsupported(op)` | `KadreFailure.Unsupported(KadreOperation.op)` |
| `Invalid(field)` | `KadreFailure.InvalidRequest(field)` |
| `Denied(permission)` | `KadreFailure.PermissionDenied(permission)` |
| `Cancelled(op)` | `KadreFailure.UserCancelled(KadreOperation.op)` |
| `Temporary` | `KadreFailure.TemporarilyUnavailable(retryable)` |
| `Busy(resource)` | `KadreFailure.AlreadyInUse(resource)` |
| `Closed(resource)` | `KadreFailure.Closed(resource)` |
| `Limit(resource)` | `KadreFailure.ResourceLimitExceeded(resource, limit)` |
| `Overflow(resource)` | `KadreFailure.SourceOverflow(resource)` |
| `Stale` | `KadreFailure.StaleRevision(expected, received)` |
| `Interaction(reason)` | `KadreFailure.InteractionRequired(reason)` |
| `Lost(source)` | `KadreFailure.SourceLost(source)` |
| `Platform` | `KadreFailure.PlatformFailure(platform, domain, code)` |

Le booléen `retryable` de `Temporary` est fixé par le backend selon qu’une nouvelle tentative sans changement d’entrée ou permission peut raisonnablement réussir. Il ne déclenche jamais un retry automatique.

Dans une cellule, la notation `X(A|B)` est uniquement une abréviation de l’ensemble fermé `{ X(A), X(B) }`; elle ne désigne ni une valeur composite ni une nouvelle variante publique.

### 1.1 Registre de `InvalidRequest.field`

Pour les opérations officielles, `InvalidRequest.field` est toujours non null et appartient exactement à la ligne suivante. Les paths utilisent lower camel case, sont stables et désignent la valeur refusée, jamais un message humain.

| Opération | Fields admis exactement |
|---|---|
| attach Android/UIKit/Web/Desktop | `"parentScope"`, `"surfaceView"`, `"view"`, `"window"`, `"element"`, `"options"` |
| `HostSurface.apply` | `"cursor"`, `"pointerCapture"`, `"hitTesting"`, `"inputDefaultBehavior"` |
| `WindowManager.requestWindow` et validation de `WebWindowProvider` | `"title"`, `"contentSize"`, `"minimumSize"`, `"maximumSize"`, `"sizeConstraints"`, `"fullscreen"`, `"icon"`, `"element"`, `"element.ownerDocument"`, `"parentScope"` |
| `Window.apply` | `"title"`, `"outerPosition"`, `"contentSize"`, `"minimumSize"`, `"maximumSize"`, `"sizeConstraints"`, `"resizable"`, `"fullscreen"`, `"decorations"`, `"systemButtons"`, `"level"`, `"transparency"`, `"blurBehind"`, `"icon"`, `"contentProtection"` |
| `Window.requestAttention` | `"attention"` |
| `Window.respondToCloseRequest` | `"requestId"` |
| `HostSurface.armInteraction` | `"action"`, `"action.mode"`, `"action.edge"`, `"action.offerId"`, `"action.spec"`, `"options.trigger"`, `"options.expiresAfter"` |
| `InteractionContext.request` | `"action"`, `"action.mode"`, `"action.edge"`, `"action.offerId"`, `"action.spec"` |
| `Gamepad.playEffect` | `"effect"`, `"effect.duration"`, `"effect.intensity"` |
| `SurfaceInput.openTextInput` | `"config.surroundingText"`, `"config.selection"` |
| `TextInputSession.updateCursor` | `"rect"` |
| `TextInputSession.updateSurroundingText` | `"text"`, `"selection"` |
| `DroppedItem.collectBytes` | `"maxBytes"` |
| `CaptureManager.open` | `"request.target"`, `"request.preferredSize"`, `"request.preferredFormats"`, `"request.region"`, `"request.cursorMode"`, `"request.minimumFrameInterval"` |

Les autres opérations officielles n’admettent pas `InvalidRequest`. Une révision attendue incorrecte utilise `StaleRevision`, une contrainte valide mais non supportée utilise `Unsupported` ou l’outcome documenté, et un payload valide hors budget utilise `ResourceLimitExceeded`. Un `KadreHost` tiers peut ajouter un field ASCII stable à son attach uniquement s’il le documente dans son propre contrat ; lui seul peut employer `field = null` lorsque l’entrée fautive ne peut pas être attribuée plus précisément.

### 1.2 Registre de `PermissionDenied`

Une opération fonctionnelle admet `PermissionDenied` uniquement dans ces couples : `SurfaceInput.requestRawInput → RawInput`, et les opérations de capture `refreshSources/open/collectFrames → CaptureScreen|CaptureWindow`. `requestPermission` retourne un refus utilisateur comme state `Denied`, jamais comme failure. `DisplayManager.requestAccess` suit de même `DisplayInventory.PermissionDenied`. `PermissionDenied(InputMonitoring)` peut apparaître dans `DeviceInventory.Unavailable` ou `RawInputState.Suspended`, pas comme failure directe d’une autre opération. Aucun backend ne réutilise arbitrairement une permission existante pour encoder un refus sans rapport.

## 2. Attachement et session

| Opération | Succès / résultat | Failures directes admises | Cancellation et autorité |
|---|---|---|---|
| `KadreHost.attach` et overloads de plateforme | `Success(KadreSession)` en `Starting` | `Unsupported(HostAttach)`, `Invalid(field d’attachement)`, `Busy(Host)`, `Closed(Host)`, `ParentScopeCancelled`, `UnsupportedPolicy(component)`, `Temporary`, `Platform` | non suspendue ; aucune session en cas d’échec. Après admission, la session et son `state` font autorité. `Default` exclut `UnsupportedPolicy`, mais pas un backend explicitement demandé et absent. |
| `KadreScope.requestStop` | `Unit` | aucune | non bloquante ; propose `ApplicationRequested`, première proposition terminale gagnante. |
| `KadreSession.requestStop` / `close` | `Unit` | aucune | non bloquante ; propose `HostRequested`, première proposition terminale gagnante. |
| `KadreSession.awaitTermination` | `SessionOutcome` | aucune failure directe | annuler le waiter ne stoppe pas la session ; `state.value` fait autorité. Depuis un enfant de la session : `IllegalStateException`. |
| `runKadreApplication` | `SessionOutcome` si une session a été créée | avant création : `KadreException` portant `Unsupported(HostAttach)`, `Invalid("options")`, `Busy(Host)`, `UnsupportedPolicy(component)`, `Temporary` ou `Platform` | bloquante desktop ; une interruption/cancellation après création est traduite en arrêt de session, pas en faux succès. Les erreurs de programmation de l’invocation restent `IllegalArgumentException` ou `IllegalStateException`. |

Les callbacks `KadreApplicationFactory.create` et `KadreApplication.run` ne retournent pas de résultat fonctionnel. Toute exception non-cancellation observée avant `Terminated` devient la failure primaire `ApplicationFailure`; une `CancellationException` suit le motif d’arrêt déjà admis. La lambda `WindowManager.requestWindow(configure)` s’exécute avant toute admission : son exception est propagée telle quelle et aucune requête n’est créée.

Les fields d’attach suivent le registre de section 1.1. `runKadreApplication` lève `KadreException` uniquement lorsque l’attach standalone échoue avant de produire une session ; il ne fabrique jamais un `SessionOutcome` pour ce cas.

`SessionOutcome.Failed` admet exactement `ApplicationFailure`, `ShutdownTimedOut`, `ResourceLimitExceeded`, `SourceOverflow` et `PlatformFailure`. `UnsupportedPolicy` et `ParentScopeCancelled` sont synchrones à `attach` et ne peuvent pas appartenir à une session qui n’a pas été créée. Une failure fonctionnelle isolée de fenêtre, périphérique ou capture ne devient pas un outcome de session sauf `FailSession`, teardown fatal ou exception applicative non gérée.

## 3. Surface et display

| Opération | Succès / résultat | Failures directes admises | Cancellation et autorité |
|---|---|---|---|
| `HostSurface.requestRedraw` | `Success(Unit)` après admission | `Closed(Surface)`, `Temporary`, `Platform` | non suspendue ; un succès promet uniquement l’admission. `SurfaceEvent.RedrawRequested` peut être coalescé. Tous les adapters officiels garantissent cette admission lorsqu’ils sont attachés. |
| `HostSurface.apply` | `Applied` ou `PartiallyApplied` | outer : `Invalid(field)`, `Closed(Surface)`, `Stale`, `Limit(ImageResource|RetainedPayload)`, `Temporary`, `Platform` ; champ rejeté : `Unsupported(UpdateSurface)`, `Interaction(reason)`, `Invalid(field)`, `Limit(ImageResource|RetainedPayload)`, `Temporary`, `Platform` | avant admission : aucun effet ; avant commit réversible : retrait ; après commit : `SurfaceState.revision` et l’outcome/événement tardif font autorité. |
| `DisplayManager.requestAccess` | `Success(state)` uniquement avec `Enumerated` ou `PermissionDenied` | `Unsupported(DisplayAccess)`, `Cancelled(DisplayAccess)`, `Interaction(reason)`, `Closed(Display)`, `Limit(Display)`, `Temporary`, `Platform` | une failure persistante admise par le domaine durable de la section 8.1 publie d’abord `Unavailable` avec la même reason ; `Cancelled` et `Interaction` conservent le state précédent. Annuler un waiter ne ferme pas un prompt partagé ; le prochain `DisplayManagerState` publié fait autorité. Un refus utilisateur est un succès stateful, pas `Denied`. |

## 4. Fenêtres

| Opération | Succès / résultat | Failures directes admises | Cancellation et autorité |
|---|---|---|---|
| `WindowManager.requestWindow` | `Success(WindowRequest)` | `Invalid(field)`, `Closed(Host)`, `Limit(WindowRequest|Window|ImageResource|RetainedPayload)`, `Temporary`, `Platform` | avant handoff, Kadre ferme la requête si le caller est annulé ; après handoff, le caller la possède. L’absence de multi-window est un `WindowRequest` déjà `Rejected(Unsupported(RequestWindow))`. |
| `WindowRequest.cancel` | `WindowCancellationOutcome` | aucune | l’appel exprime l’ownership ; cancellation du caller ne modifie pas à elle seule la requête. `state` et l’outcome terminal font autorité. |
| `WindowRequest.await` | `WindowRequestOutcome` | aucune | annuler le waiter ne cancel pas la requête. |
| `WindowRequest.close` | `Unit` | aucune | abandon non bloquant ; `RequesterDetached` est terminal lorsque le commit ne peut plus être annulé. |
| `Window.apply` | `Applied`, `PartiallyApplied` ou `Accepted` | outer : `Invalid(field)`, `Closed(Window)`, `Stale`, `Limit(ImageResource|RetainedPayload)`, `Temporary`, `Platform` ; champ rejeté : `Unsupported(UpdateWindow)`, `Interaction(reason)`, `Invalid(field)`, `Limit(ImageResource|RetainedPayload)`, `Temporary`, `Platform` | avant commit : retrait si possible ; après commit : `WindowOperationId`, `Window.state` et `Window.events` font autorité. |
| `Window.requestAttention` | `Success(Unit)` après admission | `Unsupported(RequestWindowAttention)`, `Invalid("attention")`, `Interaction(reason)`, `Closed(Window)`, `Temporary`, `Platform` | avant commit : retrait ; après commit : cancellation du waiter sans rollback. Le succès ne promet aucun effet visible ni état persistant. |
| `Window.close` | `Closed` ou `Accepted(operationId)` | `Temporary`, `Platform` | idempotente. Après commit, cancellation détache le waiter ; `Window.state` fait autorité. Pendant le teardown, Kadre peut fermer logiquement la fenêtre et reporter la failure native en diagnostic. |
| `Window.respondToCloseRequest` | `KeptOpen`, `Closing`, `TooLate` ou `AlreadyResolved` | `Invalid("requestId")`, `Closed(Window)`, `Temporary`, `Platform` | la première décision admise gagne ; après commit, le snapshot et l’outcome portant l’operation ID font autorité. |
| `WebWindowProvider.open` (callback host) | `Success(WebWindowHost)` ou failure transformée en `WindowRequestOutcome.Rejected` | exactement le set de `WindowRequestOutcome.Rejected` | synchrone, pendant l’activation native ; aucun owner n’est transféré avant validation du host retourné. Une exception devient `PlatformFailure(Web, "WebWindowProvider", "callback-exception")`; une failure hors set devient le même domain avec code `"invalid-failure"`. |

`WindowRequestOutcome.Rejected.failure` admet exactement `Unsupported(RequestWindow)`, `Invalid(field)`, `Interaction(reason)`, `Closed(Host)`, `ParentScopeCancelled`, `Limit(Window)`, `Temporary` et `Platform`. `ParentScopeCancelled` est admis uniquement lors de la validation du nouveau host retourné par `WebWindowProvider`; il ne décrit pas la scope du requester. `RequesterDetached` et `Cancelled` restent des outcomes dédiés et ne sont pas dupliqués comme failures ; `UserCancelled(RequestWindow)` n’est donc pas admis ici.

La signature normative de `WindowRequest.cancel` devient :

```kotlin
public suspend fun cancel(): WindowCancellationOutcome
```

Le `KadreResult` externe est supprimé parce que le protocole possède déjà une variante pour chaque état attendu et qu’aucune failure directe supplémentaire n’est admise.

## 5. Interactions transitoires

| Opération | Succès / résultat | Failures directes admises | Cancellation et autorité |
|---|---|---|---|
| `HostSurface.installInteractionHandler` | `Success(InteractionRegistration)` | `Unsupported(InstallInteractionHandler)`, `Busy(Interaction)`, `Closed(Surface)`, `Platform` | non suspendue ; handoff au retour. Fermer la registration empêche les prochains callbacks. |
| `HostSurface.armInteraction` | `Success(ArmedInteraction)` | `Unsupported(ArmInteraction)`, `Invalid(field)`, `Busy(Interaction)`, `Closed(Surface)`, `Limit(Interaction|ImageResource|RetainedPayload)`, `Temporary`, `Platform` | avant handoff, désarmement automatique ; après handoff, cancellation du caller ne désarme pas. |
| `InteractionContext.request` | `Success(InteractionRequestId)` | `Unsupported(Interaction)`, `Invalid(field)`, `Interaction(reason)`, `Closed(Interaction)`, `Limit(Interaction|ImageResource|RetainedPayload)`, `Temporary`, `Platform` | non suspendue dans le callback natif ; commit ou rejet avant retour. `InteractionRegistration.outcomes` fait autorité. |
| `ArmedInteraction.await` | `InteractionActionOutcome` | aucune | annuler le waiter ne désarme pas ; `ArmedInteraction.state` fait autorité. |
| `InteractionRegistration.close` / `ArmedInteraction.close` | `Unit` | aucune | idempotente, non bloquante ; les actions déjà committées ne sont pas rollback. |

`InteractionActionOutcome.Rejected.failure` utilise exactement le set de `InteractionContext.request`, auquel s’ajoute `Cancelled(Interaction)` lorsque le host annule une action déjà admise. `Expired` et `OwnerClosed` restent des outcomes dédiés.

## 6. Devices et gamepad

| Opération | Succès / résultat | Failures directes admises | Cancellation et autorité |
|---|---|---|---|
| `DeviceManager.device` / `gamepad` | handle ou `null` | aucune | lookup atomique de `state.value`; aucun I/O. |
| `Gamepad.playEffect` | `Success(GamepadEffectSession)` | `Unsupported(GamepadEffect)`, `Invalid(field)`, `Busy(GamepadEffect)`, `Closed(Gamepad)`, `Limit(GamepadEffect)`, `Temporary`, `Platform` | avant handoff l’effet est stoppé ; après handoff l’owner le contrôle. |
| `Gamepad.stopEffects` | `Success(Unit)` | `Closed(Gamepad)`, `Temporary`, `Platform` | stoppe seulement les effets de cette projection ; après commit chaque `GamepadEffectSession.state` fait autorité. |
| `GamepadEffectSession.requestStop` / `close` | `Unit` | aucune | non bloquante et idempotente. |
| `GamepadEffectSession.awaitTermination` | `GamepadEffectOutcome` | aucune | annuler le waiter ne stoppe pas l’effet. |

`GamepadEffectOutcome.Failed.failure` admet `Busy(GamepadEffect)`, `Closed(Gamepad)`, `Temporary` et `Platform`. Une déconnexion ou perte d’ownership connue utilise de préférence `Stopped(DeviceDisconnected|OwnershipLost)`.

## 7. IME, drop et raw input

| Opération | Succès / résultat | Failures directes admises | Cancellation et autorité |
|---|---|---|---|
| `SurfaceInput.openTextInput` | `Success(TextInputSession)` | `Unsupported(TextInput)`, `Invalid(field)`, `Interaction(reason)`, `Busy(TextInputSession)`, `Closed(InputSource)`, `Limit(RetainedPayload)`, `Temporary`, `Platform` | owner fermé avant handoff si cancellation ; ensuite le caller le possède. |
| `TextInputSession.updateCursor` | `Success(Unit)` | `Invalid(field)`, `Stale`, `Closed(TextInputSession)`, `Temporary`, `Platform` | après commit, `TextInputState` est l’autorité. |
| `TextInputSession.updateSurroundingText` | `Success(Unit)` | `Invalid(field)`, `Stale`, `Closed(TextInputSession)`, `Limit(RetainedPayload)`, `Temporary`, `Platform` | après commit, la révision du state fait autorité. |
| `TextInputSession.close` | `Unit` | aucune | fermeture non bloquante ; composition terminale publiée si le host l’impose. |
| `DropOffer.claimTransfer` | `Success(DropTransfer)` | `Busy(DropTransfer)`, `Closed(DropTransfer)`, `Temporary`, `Platform` | exactement un handoff gagnant ; cancellation d’un waiter ne claim pas. Le budget a déjà été réservé avant `TransferAvailable`. |
| `DroppedItem.collectBytes` | `Success(Unit)` | `Invalid("maxBytes")`, `Busy(DropTransfer)`, `Closed(DropTransfer|DropItem)`, `Limit(DropItem)`, `Temporary`, `Platform` | le caller possède le streaming de cette lecture : sa cancellation l’arrête. Les chunks déjà livrés restent app-owned, mais un échec rend le préfixe sémantiquement invalide. |
| `DropTransfer.close` | `Unit` | aucune | ferme l’admission ; la lecture active termine avec `Closed(DropTransfer)` après le callback courant. |
| `SurfaceInput.requestRawInput` | `Success(RawInputAccess)` | `Unsupported(RawInputAccess)`, `Denied(RawInput)`, `Interaction(reason)`, `Busy(InputSource)`, `Closed(InputSource)`, `Temporary`, `Platform` | owner fermé avant handoff ; ensuite le caller le possède. |
| `RawInputAccess.close` | `Unit` | aucune | fermeture non bloquante ; `state = Closed` puis terminaison du flow. |

## 7.1 Escape hatches plateforme

| Opération | Succès / résultat | Failures directes admises | Cancellation et autorité |
|---|---|---|---|
| `HostSurface.withAndroidView`, `withUIKitView`, `withWebElement` | `Success(R)` du callback | `Unsupported(PlatformSurfaceAccess)`, `Closed(Surface)`, `Temporary`, `Platform` | avant début du callback : aucune invocation ; après début, callback non suspendu achevé sur le thread host, puis résultat remis seulement au waiter encore actif. Une exception callback est propagée telle quelle. |
| `Window.withDesktopHandle` | `Success(R)` du callback | `Unsupported(PlatformWindowAccess)`, `Closed(Window)`, `Temporary`, `Platform` | même règle ; aucune validité du handle n’est promise après le retour du callback. |

## 8. Capture

| Opération | Succès / résultat | Failures directes admises | Cancellation et autorité |
|---|---|---|---|
| `CaptureManager.requestPermission(scope)` | `Success(state)` uniquement avec permission ciblée `Granted`, `Denied` ou `Restricted` | `Unsupported(CapturePermission)`, `Cancelled(CapturePermission)`, `Interaction(reason)`, `Closed(Host)`, `Temporary`, `Platform` | une failure persistante admise par le domaine durable de la section 8.1 publie la permission ciblée `Unavailable` avant retour ; `Cancelled` et `Interaction` conservent son state précédent. Annuler un waiter ne ferme pas un prompt partagé ; `CaptureManagerState.permissions` fait autorité. |
| `CaptureManager.refreshSources` | `Success(state)` uniquement avec `Enumerated` complet ou `HostPickerOnly` | `Unsupported(CaptureRefreshSources)`, `Denied(CaptureScreen|CaptureWindow)`, `Interaction(reason)`, `Closed(Host)`, `Limit(CaptureSource|RetainedPayload)`, `Temporary`, `Platform` | permission absente conserve/publie `PermissionRequired`; une autre failure persistante admise par le domaine durable de la section 8.1 publie `Unavailable`. `Interaction` conserve le state précédent. Après commit, `CaptureManager.state` et sa révision font autorité. Aucun inventaire partiel. |
| `CaptureManager.open` | `Success(CaptureSession)` | `Unsupported(CaptureOpen)`, `Invalid(field)`, `Denied(CaptureScreen|CaptureWindow)`, `Cancelled(CaptureOpen)`, `Interaction(reason)`, `Busy(CaptureSource)`, `Closed(Host)`, `Limit(CaptureSession|CaptureBuffer)`, `Stale`, `Lost(source)`, `Temporary`, `Platform` | Kadre possède toute session avant handoff et la ferme si le caller est annulé. Un picker partagé n’est pas fermé par un seul waiter. |
| `CaptureSession.collectFrames` | `Success(Unit)` pour completion/stop normal | `Busy(CaptureCollector)`, `Closed(CaptureSession)`, `Denied(CaptureScreen|CaptureWindow)`, `Limit(CaptureBuffer)`, `Overflow(CaptureBuffer)`, `Lost(source)`, `Temporary`, `Platform` | le caller possède ce streaming unique : sa cancellation stoppe la capture avec `CollectorCancelled`. L’exception du collector est propagée telle quelle. |
| `CaptureSession.requestStop` / `close` | `Unit` | aucune | non bloquante et idempotente. |
| `CaptureSession.awaitTermination` | `CaptureOutcome` | aucune | annuler le waiter ne stoppe pas la capture. Depuis son collector actif : `IllegalStateException`. |
| `CaptureFrame.copyPlanes` | copies app-owned | aucune failure typée | après la fin du callback ou `close`, lève `IllegalStateException`. |
| `CaptureFrame.close` | `Unit` | aucune | idempotente ; n’étend jamais la lease. |

`CaptureOutcome.Failed.failure` admet `Limit(CaptureBuffer)`, `Overflow(CaptureBuffer)`, `Lost(source)`, `Temporary` et `Platform`. Les arrêts demandés, cancellation/exception du collector, teardown parent et révocation de permission connue utilisent `CaptureOutcome.Stopped`; `collectFrames` traduit ce dernier cas en `Failure(Denied(CaptureScreen|CaptureWindow))` sans modifier l’outcome.

### 8.1 Failures conservées dans les states et diagnostics

Les payloads suivants possèdent un domaine fermé indépendamment de l’opération qui a provoqué leur publication :

| Payload observable | Valeurs non nulles admises exactement | Règle terminale / reprise |
|---|---|---|
| `DisplayInventory.Unavailable.failure` | `Unsupported(DisplayAccess)`, `Closed(Display)`, `Limit(Display)`, `Temporary`, `Platform` | `Limit(Display)` ferme l’inventaire et `DisplayManager.events`; les autres causes persistantes peuvent être remplacées seulement par un nouvel appel réussi à `requestAccess`. |
| `DeviceInventory.Unavailable.failure` | `Denied(InputMonitoring)`, `Closed(InputSource)`, `Limit(InputDevice|Gamepad|RetainedPayload)`, `Overflow(InputSource)`, `Temporary`, `Platform` | terminal pour cette session : handles neutralisés, puis `DeviceManager.events` terminé avec la même failure. L’absence structurelle utilise `DeviceInventory.Unsupported`. |
| `PermissionState.Unavailable.failure` dans `CaptureManagerState` | `Unsupported(CapturePermission)`, `Closed(Host)`, `Temporary`, `Platform` | une nouvelle requête admise peut remplacer cet état ; refus et restriction utilisent respectivement `Denied` et `Restricted`. |
| `CaptureSources.Unavailable.failure` | `Unsupported(CaptureRefreshSources)`, `Closed(Host)`, `Limit(CaptureSource|RetainedPayload)`, `Temporary`, `Platform` | un `refreshSources` ultérieur peut publier un inventaire complet ; permission manquante utilise `PermissionRequired`. |
| `DropOfferTerminationReason.Failed.failure` | `Temporary`, `Platform` | terminal ; `claimTransfer` retourne la même failure. Rejet, départ, expiration, timeout et fermeture de l’owner utilisent leurs variantes dédiées. |
| `RawInputState.Suspended.reason` | `Denied(InputMonitoring|RawInput)`, `Interaction(reason)`, `Busy(InputSource)`, `Temporary` avec `retryable = true`, `Platform` déclaré récupérable par le backend | l’accès reste ouvert et peut revenir à `Active`; une cause non récupérable publie `Closed` et termine `events` avec sa failure au lieu de rester suspendue. |
| `CaptureEvent.Paused.reason` | `null`, `Busy(CaptureSource)`, `Temporary` avec `retryable = true`, `Platform` déclaré récupérable par le backend | `null` signifie une pause host normale. Révocation, source perdue, overflow et failure non récupérable terminalisent la capture selon `CaptureOutcome`; seule `Resumed` sort de la pause. |
| `KadreDiagnostic.SessionFailure.failure` | exactement le set de `SessionOutcome.Failed.failure` de la section 2 | diagnostic primaire fatal de la session. |
| `KadreDiagnostic.CleanupFailure.failure` | `Temporary`, `ShutdownTimedOut`, `Platform` | failure secondaire de libération ; elle ne remplace ni ne rouvre l’outcome primaire. |

`FeatureAvailability.Unavailable`, les outcomes de window/interaction/gamepad/capture et les champs rejetés restent gouvernés par leurs sets fermés respectifs déjà indiqués dans `DESIGN.md` et les sections précédentes. Une valeur hors de ces domaines est un bug d’adapter et ne doit jamais atteindre le consumer.

## 9. Flows et callbacks

| Action consumer | Échec admis | Effet sur l’owner |
|---|---|---|
| collecter un `StateFlow` | uniquement cancellation du contexte consumer | aucun ; le flow ne termine pas et Kadre ne rejette pas le collector |
| démarrer un collector d’événements dans le budget | cancellation, terminaison normale ou `KadreException` de la source | aucun ownership implicite |
| dépasser `maxEventCollectorsPerFlow/session` | collector terminé immédiatement par `KadreException(ResourceLimitExceeded(EventCollector, limit))` | source inchangée |
| collector trop lent avec `CancelSlowCollector` | `SlowCollectorCancellationException` | seul ce collector est annulé |
| callback de frame ou de bytes lève `CancellationException` | la même exception | streaming owner arrêté selon sa ligne |
| callback de frame ou de bytes lève une autre exception | la même exception | frame/chunk libéré ; outcome owner défini par sa ligne |
| `InteractionHandler` lève | aucune exception ne traverse le SDK | diagnostic fatal puis `SessionOutcome.Failed(ApplicationFailure)` |

## 9.1 Contrôleurs de `kadre-test`

Les méthodes `Virtual*Controller`, `VirtualKadreClock.advanceBy` et constructeurs `Fake*` configurent un backend de test ; elles ne sont pas des opérations fonctionnelles de l’application et ne retournent pas `KadreResult`. Elles réussissent avec la valeur indiquée ou lèvent `IllegalArgumentException` pour owner/ID étranger, ordre de transition impossible, capability incohérente, payload hors budget ou valeur numérique invalide. Toute failure injectée par `fail*`, `reject` ou `enqueueOpenFailure` doit appartenir au domaine fermé de l’opération/outcome ciblé, y compris les registres de field et permission des sections 1.1/1.2 ; sinon la méthode lève `IllegalArgumentException` sans transition. Elles ne lèvent aucune `KadreException` et n’utilisent aucune failure native. Les opérations déclenchées ensuite sur les vrais handles fake suivent toutes les lignes ordinaires de ce document.

| Owner de test | Méthodes couvertes exactement | Autorité / suspension |
|---|---|---|
| `VirtualKadreClock` | `advanceBy`, `runCurrent` | `now` et la file virtuelle drainée font autorité ; aucune suspension |
| `FakeKadreHost` | `detach` | premier appel publie le detach terminal ; appels suivants idempotents |
| `VirtualLifecycleController` | `setCapabilities`, `setVisibility`, `setActivation`, `memoryPressure` | snapshot lifecycle/capability mis à jour avant événement ou diagnostic ; signal memory-pressure admis sous `hostSignals` |
| `VirtualSurfaceController` | `setMetrics`, `setFocus`, `setVisibility`, `setTheme`, `setCapabilities`, `requestRedraw`, `detach` | snapshot/révision généré puis événement correspondant |
| `VirtualWindowController` | `openHere`, `openInNewSession`, `reject`, `cancel`, `externalApply`, `setCapabilities`, `requestClose`, `forceClose` | state de la request ou fenêtre ciblée ; aucune identité n’est forgée par le caller |
| `VirtualDisplayController` | `setCapabilities`, `requirePermission`, `denyPermission`, `fail`, `connect`, `update`, `disconnect` | `DisplayManagerState` composé et IDs générés |
| `VirtualInputController` | `setCapabilities`, `connectDevice`, `disconnectDevice`, `key`, `pointerEnter`, `pointerMove`, `pointerButton`, `pointerLeave`, `scroll`, `touchStart`, `touchMove`, `touchEnd`, `gesture`, `reset`, `offerDrop`, `moveDrop`, `leaveDrop`, `performDrop`, `rawDelta` | snapshot input mis à jour avant l’événement ; factories d’ID indiquées par le catalogue |
| `VirtualGamepadController` | `connect`, `button`, `axis`, `suspendRouting`, `resumeRouting`, `completeEffect`, `failEffect`, `disconnect` | snapshot/routing/effect outcome ciblé |
| `VirtualCaptureController` | `setCapabilities`, `setPermission`, `setSources`, `requirePermission`, `useHostPicker`, `failInventory`, `enqueueOpen`, `enqueueOpenFailure`, `reconfigure`, `emitFrame`, `pause`, `resume`, `complete`, `fail` | manager/session state ciblé ; `emitFrame` est le seul appel suspendu |

`emitFrame` ne crée aucun owner public : avant admission, sa cancellation garantit qu’aucune frame n’est injectée ; après admission, la configuration révisionnée, la delivery policy et le collector de la `CaptureSession` font autorité. Un appel sans collector streaming actif ou avec une session étrangère lève `IllegalArgumentException` avant admission.

## 9.2 Helpers purs et wrappers host

- les quatre conversions `toLogical`/`toPhysical` sont pures et lèvent uniquement `IllegalArgumentException` pour scale/résultat numérique invalide ;
- `KadreResult.isSuccess`, `isFailure`, `getOrNull`, `failureOrNull`, `map`, `flatMap` et `fold` sont purs ; une exception des lambdas `transform`/`fold` est propagée telle quelle, sans conversion. `getOrThrow` retourne la value ou lève `KadreException` avec la failure inchangée ;
- `InteractionHandler.onInteraction` suit la ligne callback de la section 9 et ne peut jamais laisser une exception traverser la frontière native ;
- `KadreApplicationFactory.asHostRef()` est pur, sans état global et ne peut échouer ;
- les overloads `attachKadre`/`attachKadreDesktop` délèguent exactement à la ligne `KadreHost.attach` ;
- les observers Java/Swift/JS de la façade host suivent les règles de callback de `INTEROP-EXPORTS.md` et ne possèdent jamais la session.

## 10. Enum `KadreOperation` fermé

L’enum public est exactement :

```kotlin
public enum class KadreOperation {
    HostAttach,
    RequestRedraw,
    DisplayAccess,
    RequestWindow,
    UpdateWindow,
    RequestWindowAttention,
    CloseWindow,
    RespondToCloseRequest,
    UpdateSurface,
    InstallInteractionHandler,
    ArmInteraction,
    Interaction,
    GamepadEffect,
    StopGamepadEffects,
    TextInput,
    UpdateTextInput,
    ClaimDropTransfer,
    ReadDropItem,
    CapturePermission,
    CaptureRefreshSources,
    CaptureOpen,
    CaptureCollectFrames,
    RawInputAccess,
    PlatformSurfaceAccess,
    PlatformWindowAccess,
}
```

`PermissionRequest` disparaît : chaque permission publique possède désormais l’opération fonctionnelle exacte qui l’a demandée. Une nouvelle méthode publique retournant `KadreResult` ou un outcome terminal doit ajouter simultanément une ligne à cette matrice et, si elle peut produire `Unsupported`/`UserCancelled`, une entrée à `KadreOperation`.

## 11. Audit de totalité

- [x] Chaque fonction publique non triviale du catalogue apparaît dans une ligne.
- [x] Chaque owner retourné possède un point de handoff et une autorité après cancellation.
- [x] Chaque `await` précise que le waiter n’est pas owner, sauf les deux collectors structurés explicitement.
- [x] Chaque outcome contenant une failure possède un set fermé distinct des failures directes.
- [x] Les refus de permission stateful et les annulations de picker ne sont plus confondus.
- [x] Aucune opération unsupported ne peut réussir par no-op.
