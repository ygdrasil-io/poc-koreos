# iOS/tvOS Phase 4 — Demandes de scènes et opérations de fenêtres — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer les demandes de fenêtres UIKit/iPadOS — `requestWindow` corrélé jusqu'à `OpenedInNewSession` via l'activation d'une nouvelle scène (provider app-owned), les refus `Rejected(Unsupported(RequestWindow))` mono-scène tvOS sans effet natif, la cancellation/discard et la préservation des overlays — jalon B de la roadmap.

**Architecture:** Le modèle WEB adapté (pas le modèle AppKit same-session) : les scènes enfants ont **leur propre session complète** (leur propre `UIKitWindowRuntime`, leur propre manager/primary — « une scène déjà ouverte possède sa propre session », roadmap l.241), jamais ajoutées à la liste de la session demandeuse. Un `UIKitWindowSceneManager` host-side (miroir `WebHostWindowManager`, ~150 lignes de pending+provider SANS reducer de fenêtre) supplante `requestWindow` de la session demandeuse : capabilities `Supported({OpenedInNewSession})`, pending async borné par `maxPendingWindowRequests`, provider app-owned `UIKitWindowSceneProvider`, corrélation par `WindowRequestId` porté dans la `userActivity` d'activation, child attach par le chemin ordinaire avec `KadreLaunchInfo(AdditionalHostRequested, requestId)` (param optionnel déjà présent sur `RuntimeHostController.attach`). Le manager runtime existant continue de porter la fenêtre primaire (phases 1-3 intactes) ; le scene-manager délègue tout le reste. tvOS : pas de provider → chemin null-provider = `Rejected(Unsupported(RequestWindow))` sans aucun appel de primitive de scène.

**Tech Stack:** Kotlin 2.4.20 (uikitMain + friend-modules), `UIApplication.requestSceneSessionActivation` (côté app Swift provider), xcodegen, XCTest sur iPad Pro 11-inch (M5) + Apple TV 4K, pipeline evidence (gabarit BCK-013/015).

**Spec:** `kadre/IOS-IMPLEMENTATION-ROADMAP.md` §5 Phase 4 (l.232–248) ; `kadre/BACKEND-CAPABILITIES.md` l.39 (row topologie : UIKit activation nouvelle scène = `C + S`) + l.53 (corrélation, aucun timeout implicite) ; `kadre/OPERATION-CONTRACTS.md` §4 (outcomes fenêtre) ; précédent Web Phase 4 (`WebHostWindowManager.kt`).

## Décisions fermées par ce plan

| Décision | Résultat fermé ici |
|---|---|
| Modèle | Web-shape adapté : scènes enfants = sessions complètes indépendantes (leur propre `UIKitWindowRuntime`) ; JAMAIS dans la liste de la session demandeuse. Le pending est réellement async (FrontBoard) — « garder Pending en l'absence de signal terminal ; aucun timeout synthétique » (roadmap l.240). |
| Le manager demandeur | `UIKitWindowSceneManager : WindowManager by runtimeManager` — override `requestWindow` (route provider/pending) + override capabilities (`Supported({OpenedInNewSession}, Available)` quand provider présent) ; TOUT le reste délègue (state/membership de la primaire = runtime). Câblé à la place du manager nu dans `RuntimeSessionComponents.windows` (miroir `RuntimeDeferredAdmissionWindowManager`). |
| Provider | `public fun interface UIKitWindowSceneProvider { public fun open(requestId: WindowRequestId, spec: WindowSpec) }` (app-owned) — l'app active la scène (`requestSceneSessionActivation`), crée fenêtre+vue à la connexion (corrélation par requestId remis au provider via un registre app-side), puis résout via `UIKitWindowSceneRequestResolver.resolve(requestId, windowScene, window, surfaceView)`. L'adaptateur n'appelle AUCUNE API d'activation (roadmap l.237 : « isoler dans iosMain toute API d'activation » = rien à isoler, le provider est app-side). |
| Child attach | Par le chemin ordinaire : `UIKitWindowSceneManager` crée le `UIKitWindowRuntime` de l'enfant depuis (windowScene, window, surfaceView) et attache avec `KadreLaunchInfo(AdditionalHostRequested, requestId)` + la factory et la policy DU REQUÉRANT (miroir web l.612-614). |
| tvOS | Le paramètre provider est disponible sur les deux familles mais les apps tvOS n'en passent pas (topologie mono-scène, DESIGN §15.2) ; null-provider → `Rejected(Unsupported(RequestWindow))` avant TOUTE réservation (chemin web l.265-266 verbatim). La preuve tvOS : aucun appel d'activation (l'app n'en fait pas) + outcome Rejected. |
| Connexion tardive / cancellation | Pending non terminal : `cancel()` avant résolution → `Cancelled` + release du slot ; résolution arrivant après cancel/close du requester → l'enfant est DÉTACHÉ proprement (sa session fermée si elle avait attaché, sinon la scène est abandonnée sans session) et l'outcome reste le terminal d'origine (jamais résolu deux fois). |
| Window.apply / attention / close | Hors périmètre phase 4 (les fenêtres enfants ne sont pas des `Window` Kadre — roadmap l.241 « sans cacher ou détruire arbitrairement une fenêtre du host ») ; `requestOpenedClose` du port reste la posture phase 1 pour la primaire. |
| Contracts | `BCK-020` « UIKit window requests via scene activation » (iOS+tvOS : les scénarios de refus/reprise tournent sur les deux) + `BCK-021` « UIKit iPadOS scene-correlated window requests » (iOS-only : corrélation, indépendance, overlays). |

## Global Constraints

- Aucune duplication du reducer de fenêtre : le scene-manager ne fait PAS d'admission de fenêtre (pas de géométrie/close) — il route provider↔child-session et tient le pending de demandes.
- `OpenedInNewSession` suit TOUJOURS une connexion réelle corrélée (l'enfant attaché) ; jamais émis sur une simple intention.
- Une nouvelle fenêtre n'est JAMAIS ajoutée à la liste de la session demandeuse (roadmap l.247).
- Aucun timeout synthétique ; pending borné par `maxPendingWindowRequests` ; slots libérés à la terminaison de la requête (consommateur du budget : le requester via `request.close()` ou la terminaison de session).
- Identity UIKit = `isEqual` ; la factory et la policy du REQUÉRANT sont réutilisées pour l'enfant (roadmap l.238).
- Défaillances provider fermées (miroir web `isAdmittedWindowRequestFailure`) : tout autre throw/failure → `PlatformFailure(UIKit, "UIKitWindowSceneProvider", ...)`.
- Preuves séparées iOS/tvOS ; gate hors `:kadre:check` ; commandes Gradle < 8 min, longues en background+polling.

## Review Focus

1. **Double-résolution du pending** — corrélation tardive après cancel/close, deux résolutions concurrentes (provider + terminaison session), pending jamais retiré (fuite de slot). Test : cancel puis résolution → l'outcome reste terminal d'origine, l'enfant (si attaché) détaché, le slot libéré, re-request possible.
2. **Fuite de la factory/policy du requérant vers l'enfant** — l'enfant doit utiliser la factory du REQUÉRANT (roadmap l.238), pas une nouvelle ; et son runtime doit être fermé à sa terminaison (pas de fuite du runtime enfant). Test : l'app de l'enfant s'exécute (identité prouvée par le fixture), et après déconnexion de la scène enfant le runtime enfant est clos.
3. **Requêtes concurrentes** — deux `requestWindow` simultanés → deux scènes, deux corrélations non croisées (requestId ↔ scene par `isEqual`/registre). Test : deux demandes, deux enfants, chaque requestId corrélé à la BONNE scène.
4. **Le tvOS n'appelle jamais l'activation** — la preuve tvOS doit montrer l'outcome Rejected ET l'absence de tout effet (aucune réservation, aucun provider invoqué). Test tvOS dédié.
5. **Overlays préservés** — les UIWindows overlays de la scène requérante ne deviennent pas des fenêtres Kadre et survivent à l'opération. Test : fenêtre overlay posée avant requestWindow → inchangée après.

---

### Task 1: `UIKitWindowSceneManager` — pending, provider, corrélations, child attach

**Files:**
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitWindowSceneManager.kt`
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/UIKitWindowSceneProvider.kt` (l'interface publique app-owned + `UIKitWindowSceneRequestResolver`)
- Test: `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitWindowSceneManagerTest.kt`

**Interfaces:**
- Consumes: `WindowManager`/`WindowRequestOutcome`/`WindowSpec` (foundation) ; `KadreLaunchInfo` + `RuntimeHostController.attach(..., launch)` (runtime, l.101-106) ; `UIKitWindowRuntime` (phase 1) ; `maxPendingWindowRequests` (policy).
- Produces: `internal class UIKitWindowSceneManager(private val runtimeManager: WindowManager, private val provider: UIKitWindowSceneProvider?, private val policy: KadrePolicy, private val applicationFactory: KadreApplicationFactory, private val failureReporter: RuntimeFailureReporter, private val parentScope: CoroutineScope) : WindowManager by runtimeManager` — overrides : `requestWindow(spec)`, `capabilities` (propriété déléguée surchargée : `Supported({OpenedInNewSession}, Available)` si provider, sinon `Unsupported(RequestWindow)`) ; `internal fun resolve(requestId: WindowRequestId, windowScene: UIWindowScene, window: UIWindow, surfaceView: UIView)` (appelé par l'app quand la scène corrélée connecte) ; `internal fun abandon(requestId: WindowRequestId)` ; `close()` délègue + termine tout pending `RequesterDetached` + détache les enfants vivants.

- [ ] **Step 1: L'interface provider publique** (`UIKitWindowSceneProvider.kt`) :

```kotlin
public fun interface UIKitWindowSceneProvider {
    /** Appelé sur le thread principal à chaque `requestWindow` admis. L'app active une
     *  nouvelle scène et la corréle à [requestId] (userActivity/options) ; à la connexion
     *  de cette scène, l'app crée fenêtre + surfaceView et appelle le resolver. */
    public fun open(requestId: WindowRequestId, spec: WindowSpec)
}

/** Résolution app-side : la scène corrélée a connecté avec ses objets host. */
public class UIKitWindowSceneRequestResolver private constructor(internal val id: WindowRequestId) {
    public fun resolve(windowScene: platform.UIKit.UIWindowScene, window: platform.UIKit.UIWindow, surfaceView: platform.UIKit.UIView) { ... }
    public fun abandon(reason: String) { ... }
    public companion object { internal fun issue(id: WindowRequestId): UIKitWindowSceneRequestResolver }
}
```

- [ ] **Step 2: Les tests du manager d'abord (kernel avec provider faux)** — pending bound (max → `ResourceLimitExceeded`), null-provider → `Rejected(Unsupported(RequestWindow))` AVANT réservation, provider failure fermée (Set hors ensemble → `PlatformFailure(UIKit, provider, invalid-failure)`), provider throw → `callback-exception`, `resolve` → child attach (faux childSessionFactory injecté pour le test) → `OpenedInNewSession(childId)`, resolve après cancel → outcome reste `Cancelled` + enfant détaché, double resolve → no-op, close manager → pending `RequesterDetached`. Le child-attach est injectable : le constructeur du manager prend `childSessionFactory: (UIKitWindowSceneHost) -> KadreResult<KadreSession>` (internal ; la production Task 2 fournit la vraie).

- [ ] **Step 3: Implémentation** — miroir `WebHostWindowManager` (l.185-341) en async : `outstanding: LinkedHashMap<WindowRequestId, PendingSceneRequest>` sous RuntimeLock, `PendingSceneRequest { id, spec, resolver, terminal? }` ; `requestWindow` : capabilities refus si pas de provider, sinon admission + `provider.open(id, spec)` (try/catch → PlatformFailure callback-exception) + retour `Success(request)` (l'outcome viendra à `resolve`) ; `resolve()` : guard pending existant + non-terminal → construire le `UIKitWindowRuntime` enfant, attacher via `RuntimeHostController.withComponents(...).attach(parentScope-enfant, factory, policy, KadreLaunchInfo(AdditionalHostRequested, requestId))` → `terminate(OpenedInNewSession(child.id))` ; tout failure attach → `Rejected(failure fermée)`. `cancelRequest`/`detachRequest` overrides : terminent le pending (`Cancelled`/`RequesterDetached`) + `abandon` du resolver.
- [ ] **Step 4: Vert** — `./gradlew :kadre:platform:uikit:iosSimulatorArm64Test :kadre:platform:uikit:tvosSimulatorArm64Test`.
- [ ] **Step 5: Commit** — `feat(uikit): add the scene-request window manager with pending correlation`.

---

### Task 2: Câblage attach + provider optionnel + rejet tvOS

**Files:**
- Modify: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreIos.kt` (param `windowSceneProvider: UIKitWindowSceneProvider? = null` ; câble le scene-manager dans componentsFactory)
- Modify: `UIKitWindowRuntime.kt` si le câblage exige (non attendu)
- Test: `kadre/consumers/swift` ou uikitTest — le câblage est prouvé par driver (Task 3) ; uikitTest : null-provider → capabilities Unsupported.

**Interfaces:**
- Consumes: Task 1 ; `KadreIos.attach` existant.
- Produces: `KadreIos.attach(..., windowSceneProvider: UIKitWindowSceneProvider? = null)` — default null (tvOS et apps mono-fenêtre inchangées) ; `windows.capabilities` annonce `OpenedInNewSession` ssi provider.

- [ ] **Step 1: Câbler** — `KadreIos.attach` construit `UIKitWindowSceneManager(runtime.manager, provider, policy, applicationFactory, failureReporter, scope)` et le passe comme `windows` (à la place du manager nu, APRÈS le deferred-admission delegate — ordre : runtime.manager → deferred admission → scene manager par-dessus ? Non : le scene-manager enveloppe le manager à qui la délégation arrive déjà avec la primaire admise ; vérifier l'ordre des deux wrappers au compile+test).
- [ ] **Step 2: Test uikitTest null-provider** : capabilities.requestWindow == `Unsupported(RequestWindow)` (régression tvOS verbatim).
- [ ] **Step 3: Compiles 4 targets + suites vertes. Commit** — `feat(uikit): wire the optional scene provider into the uikit attach`.

---

### Task 3: Driver — le parcours corrélé complet (iOS) et le refus (tvOS)

**Files:**
- Modify: driver apps (le provider Swift : `requestSceneSessionActivation` avec correlation token + création fenêtre/vue à la connexion corrélée + resolve) ; env-gated comme phase 1
- Test: `tests/Ios/KadreUikitWindowRequestTests.swift` (noms figés) + `tests/Shared/` pour le tvOS refusal
- Modify: project.yml + pbxproj regénérés

**Interfaces:**
- Consumes: Tasks 1-2 ; `DriverSceneRequest` (activation gate + sérialisation) ; fixtures par scène.
- Produces: noms figés pour Task 4 — `uikit-window-request-opens-correlated-scene`, `uikit-window-request-child-session-independent`, `uikit-window-request-requester-close-detaches-child`, `uikit-window-request-concurrent-pair`, `uikit-window-request-overlays-preserved`, `uikit-window-request-cancel-before-connect`, `uikit-window-request-tvos-rejected` (tvOS).

- [ ] **Step 1: Le provider Swift** — implémente `UIKitWindowSceneProvider` : `open(requestId, spec)` → (gated activation) `requestSceneSessionActivation` avec un token de corrélation remis via `DriverSceneRequest` ; le delegate de la scène connectée consulte le registre des tokens en attente → crée fenêtre+vue → `resolver.resolve(...)`. Traçage complet (le token dans chaque trace).
- [ ] **Step 2: Les scénarios** : (1) opens-correlated-scene — requestWindow → scène 2 connectée avec le token → OpenedInNewSession(childId), la fenêtre enfant = celle créée par le delegate (isEqual via fixture) ; (2) child-session-independent — arrêter la session enfant n'affecte pas le requester, et vice versa ; (3) requester-close-detaches-child — close du requester avec enfant vivant → enfant intact (sessions indépendantes) ou détaché selon le contract testé (roadmap l.240 : l'enfant est indépendant — assert non-affected) ; (4) concurrent-pair — deux requestWindow → deux scènes corrélées chacune à son requestId ; (5) overlays-preserved — une UIWindow overlay sur la scène 1 avant requestWindow → inchangée après ; (6) cancel-before-connect — requestWindow puis abandon (close du requester) avant la connexion → la scène demandée (si connectée) n'attache pas de session, l'outcome reste Cancelled ; (7) tvOS-rejected (tests/Shared, tourne sur Apple TV) — requestWindow → `Rejected(Unsupported(RequestWindow))`, aucune réservation, aucun provider invoqué (compteur).
- [ ] **Step 3: Vert ×2 destinations** (aggregator, background+poll).
- [ ] **Step 4: Commit** — `test(uikit): prove correlated scene window requests on iPadOS and the tvOS refusal`.

---

### Task 4: Contrats BCK-020 + BCK-021 + evidence

**Files:** registry (+2), evidence.tsv, validator (uikitContractIds), script asserts, capabilities/uikit.md (+2 rows).
- [ ] Rows : `BCK-020 active ... UIKit window request lifecycle (refusal, pending, cancellation)  ... tvOS+iOS` (scénarios : window-request-tvos-rejected, window-request-cancel-before-connect, + sentinelles cancellation/double-resolve) ; `BCK-021 active ... UIKit iPadOS scene-correlated window requests ... iosSimulatorArm64` (opens-correlated-scene, child-independent, requester-close, concurrent-pair, overlays-preserved + sentinelle corrélation-croisée).
- [ ] Can-fail : corrélation croisée (mutation = resolve ignore le registre → concurrent-pair échoue) ; pending jamais libéré (mutation = terminate sans release → re-request dépasse max → échec) ; refus tvOS (mutation = provider invoqué sur tvOS → le test tvOS échoue). Cités, revertés.
- [ ] Gate vert + **UN commit d'activation** — `feat(uikit): activate BCK-020 and BCK-021 with window-request evidence`.

---

### Task 5: Docs closure

- DESIGN §15.2 (window requests livrés phase 4, scène = session), BACKEND-CAPABILITIES §6.2 (row livré + requestWindow tvOS Rejected), PUBLIC-API-CATALOG l.30 (provider), capabilities rows déjà en Task 4.
- Commit : `docs: close the phase 4 UIKit window-request perimeter`.

---

### Task 6: Sweep jalon B + PR

- Sweep à froid : runtime jvmTest, natives ×2 sims, appkit, gate uikit `all`, consumers ×2 destinations.
- Checklist jalon B (roadmap l.247) : OpenedInNewSession suit toujours une connexion corrélée ; jamais dans la liste du requester ; parcours iPad multi-scène positif ; tvOS prouve le refus sans effet natif.
- Rapport + push + PR (contrôleur).

---

## Séquencement et estimation

Tasks 1→2→3→4→5→6. T1 = le cœur (pending/provider/corrélation/child-attach, kernel testé) ; T2 = câblage mince ; T3 = la preuve driver (risque : la mécanique d'activation à la demande vs launch-gated — le gate d'activation phase 1 est l'acquis) ; T4-T5 = preuves/docs. Budget attendu : ~35-45M tokens.
