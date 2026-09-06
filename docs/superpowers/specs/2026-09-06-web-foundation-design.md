# Web Foundation — design de la première tranche

**Statut :** proposition révisable avant implémentation. Ce document est un
outil de conception temporaire : il sera retiré de la branche avant la
première pull request fonctionnelle, conformément à la convention du projet.

## But

Rendre le chantier Web exécutable sans reproduire l'architecture historique :
établir une preuve navigateur réelle et le contrat qui permettra ensuite à
`platform:web` d'attacher une `KadreSession` à un élément DOM préexistant, en
JS et Wasm avec la même sémantique publique.

Cette tranche ne crée ni renderer, ni widget, ni `Window` artificielle, ni
élément DOM ou popup implicite. Elle ne fait pas non plus dépendre le Web de
la boucle native AppKit.

## Décision de découpage

Trois séquences ont été considérées :

1. Créer immédiatement un module `platform:web` vide et ses targets. Cela
   semblerait faire avancer le chantier, mais enfreindrait l'architecture :
   un projet réservé ne doit pas entrer dans le build sans responsabilité et
   preuve réelles.
2. Porter le runtime JVM vers `commonMain`, puis livrer un attach DOM complet
   dans la même pull request. Cette voie est trop large et ferait dépendre une
   première preuve Web d'une refonte de session non encore séparée.
3. Livrer d'abord le registre WEB, le protocole du futur driver et les
   scénarios normatifs ; introduire chaque morceau de production au moyen
   d'une tranche verticale prouvée. **Cette voie est retenue.**

La première pull request Web ne créera donc pas un adapter factice. Elle
ajoutera uniquement les contrats `planned` et la convention de preuves. Le
harness navigateur n'entre dans le build qu'avec un artifact JS/Wasm qui peut
réellement l'exercer. La première tranche fonctionnelle activera `BCK-001`
dans le même commit que l'attach DOM réellement testé et son driver ; les
façades host possèdent leurs propres gates `INT-002` et `INT-003`.

## Contrat cible, inchangé

L'API de production future est celle de `DESIGN.md` section 15.3 et de
`BACKEND-CAPABILITIES.md` section 6.3 :

```kotlin
public fun HTMLElement.attachKadre(
    parentScope: CoroutineScope,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
    windowProvider: WebWindowProvider? = null,
): KadreResult<KadreSession>
```

Le même overload existe avec une `KadreApplication` placée en dernier, sans
`WebWindowProvider`, et ne peut donc créer qu'une session. L'élément fourni
est la `primarySurface`, jamais une `Window`; `WindowManager.state.value.primary`
reste `null`.

### Façades JS et Wasm

Une signature unique ne peut pas être placée dans un source set Web partagé :
`HTMLElement` est `org.w3c.dom.HTMLElement` en JS et `web.html.HTMLElement` en
Wasm. `WebAttachmentPolicy` et les valeurs sans type DOM peuvent être partagés
dans `webMain`; les deux overloads `attachKadre`, `WebWindowHost`,
`WebWindowProvider` et `withWebElement` sont déclarés séparément dans
`jsMain` et `wasmJsMain` avec leur type SDK exact. Un noyau d'attachement
interne, sans type DOM public, porte la sémantique commune. Aucun `typealias`
public ne masque cette différence de SDK.

Les invariants à rendre exécutables sont :

- `StopWhenDetached` rejette un élément initialement déconnecté ; `Manual`
  l'accepte et publie `Attached + Background + Inactive`.
- Le test de détachement a lieu après le batch du `MutationObserver`, observé
  sur le `Document` ou le `ShadowRoot` contenant l'élément. Après chaque batch,
  le host recalcule son root et réinstalle l'observer lorsqu'il a changé. Un
  reparentage achevé avant cette livraison conserve la session uniquement si
  `ownerDocument` reste le document initial ; un transfert vers un autre
  document termine la session, même si l'élément est déjà reconnecté. Un
  élément encore déconnecté la termine et sa réinsertion requiert une nouvelle
  session.
- `visibilitychange` pilote `Foreground` seulement si le document est visible
  **et** l'élément reste connecté. `focus`/`blur` du browsing context et
  `focusin`/`focusout` de l'élément ou de son subtree pilotent `Active` : une
  surface n'est active que si elle est foreground et possède l'input. Un
  document background, une surface déconnectée ou un autre host ayant le focus
  publient `Inactive`. `Manual` conserve la session lors d'un detach/reinsert,
  mais son lifecycle reste `Background + Inactive` tant que l'élément n'est
  pas reconnecté ; il ne devient terminal que par `requestStop` ou un motif
  externe.
- Lorsqu'un host `Manual` est initialement ou reste déconnecté après livraison
  d'un batch, Kadre arme un watcher de connexion par `requestAnimationFrame`
  sur son browsing context d'origine. Il vérifie `isConnected`, `ownerDocument`
  et `getRootNode` à chaque tick jusqu'à reconnexion ou terminaison, puis
  bascule de nouveau vers l'observer `Document`/`ShadowRoot`. Une reconnexion
  tardive dans un autre `ShadowRoot` du même document restaure donc le
  lifecycle ; un autre document termine la session avec `HostDetached`.
- L'admission sérialise attach et détachement : un detach admis avant la
  création échoue sans session ; après admission, la session termine
  `Stopped(HostDetached)`, la factory tardive est ignorée et `run` ne démarre
  jamais après ce motif terminal.
- Kadre ne crée ni canvas, ni div, ni popup. Sans provider,
  `requestWindow` rend explicitement `Unsupported`.
- Un provider est synchrone, uniquement disponible sur l'overload factory,
  et un nouveau browsing context produit une nouvelle `KadreSession`, jamais
  une fenêtre dans la session originelle.
- Tout `pagehide`, y compris `persisted == true`, ferme l'admission et termine
  la session sans attente. `pageshow` ne ressuscite jamais cette session ; un
  host de retour doit s'attacher à nouveau. Le traitement libère seulement les
  bridges synchrones best-effort et n'autorise aucune promesse sur la
  complétion d'un teardown suspendu après destruction du runtime browser.
- `withWebElement` est l'unique escape hatch Kotlin destiné à un renderer
  externe : son callback est borné par la durée de la lease. Une fermeture ou
  un detach admis pendant le callback attend cette lease non suspendue ; après
  son retour, un nouvel appel retourne `Closed(Surface)` et aucune validité de
  l'élément conservé n'est promise.

Les interactions soumises à la transient user activation (fullscreen,
pointer lock, drop et popup) ne sont pas implémentées dans cette tranche, mais
leur admission sera toujours vérifiée via `InteractionContext`, jamais par un
token conservé.

## Registre et scénarios

Le registre ajoute les trois contrats suivants au statut `planned`, avec les
familles fermées de `TEST-STRATEGY.md` :

```tsv
BCK-001	planned	DESIGN.md#15.3	DOM host attach and lifecycle	surface/window confusion, lifecycle loss, cross-session leak or false capability	O3	web-attach-connected,web-attach-detached-rejected,web-attach-manual-detached,web-manual-initial-shadow-reinsert,web-detach-reparent-batch,web-shadow-root-detach-reparent,web-shadow-root-late-reinsert,web-detach-cross-document-terminal,web-manual-detach-and-stop,web-visibility-focus,web-focus-transfer-between-hosts,web-attach-detach-admission-race,web-detach-terminal,web-multi-session-isolation,web-no-implicit-window,web-window-provider-new-session,web-window-provider-same-context,web-window-provider-no-context,web-window-provider-invalid-element,web-window-provider-invalid-scope,web-window-provider-callback-failure,web-pagehide-admission-close,web-pagehide-navigation,web-pagehide-no-resurrection	js,wasmJs	WindowManagerCapabilities.requestWindow	web-surface-never-window,web-no-implicit-dom,web-cross-session-isolation,web-detach-no-resurrection,web-shadow-root-observation,web-provider-no-same-document-window,web-active-gate-requires-js-and-wasm	-
INT-002	planned	INTEROP-EXPORTS.md#6	JS and Wasm host facade structural exports	foreign API drift or leaked coroutine types	O1	web-typescript-consumer-js,web-typescript-consumer-wasm	js,wasmJs	-	web-host-no-coroutine-leak,web-host-identical-dts	-
INT-003	planned	INTEROP-EXPORTS.md#6	JS and Wasm host facade runtime	incorrect host outcome, notification ordering or ownership	O3	web-host-attach-failure,web-host-state-subscription,web-host-observer-exception,web-host-stop-close-outcome,web-host-provider	js,wasmJs	-	web-host-microtask-order,web-host-callback-isolation,web-host-outcome-rejection	-
INT-004	planned	INTEROP-EXPORTS.md#7	Web element escape hatch	invalid retained element access or lease/teardown race	O3	web-element-lease,web-element-lease-concurrent-close	js,wasmJs	SurfaceCapabilities.platformAccess	web-element-lease-boundary,web-element-lease-close-order	-
```

`BCK-001` porte l'adapter Kotlin. `INT-002` prouve structurellement
`@kadre/host` et le même consumer TypeScript pour JS et Wasm. `INT-003` prouve
à la frontière browser `KadreWeb.attach`, les `KadreHostError`, un `MainScope`
par session attachée — y compris une session ouverte par provider —, la
notification d'état initiale puis en microtask, l'exception d'observer isolée,
`requestStop`/`close`, `awaitTermination` et le provider TypeScript. Chaque
scope est annulée après l'échec d'attach ou la terminaison de sa propre
session ; fermer le requester n'annule jamais une session déjà
`OpenedInNewSession`. L'activation de `BCK-001` seule ne qualifie jamais
`platform:web` comme adapter supporté ou publiable. `INT-004` porte séparément
la capability `platformAccess` et l'escape hatch `withWebElement`.

Ses identifiants de scénario stables sont :

| Scenario ID | Stimulus navigateur réel | Oracle requis |
|---|---|---|
| `web-attach-connected` | attache un élément connecté | session vivante, primary surface présente, aucune Window |
| `web-attach-detached-rejected` | attache `StopWhenDetached` sur élément déconnecté | `InvalidRequest("element")`, aucune session |
| `web-attach-manual-detached` | attache `Manual` sur élément déconnecté | lifecycle `Attached + Background + Inactive` |
| `web-manual-initial-shadow-reinsert` | attache `Manual` hors DOM, puis insère l'élément dans un `ShadowRoot` | watcher initial détecte la reconnexion, lifecycle restauré ; detach terminal ultérieur détecté par l'observer réinstallé |
| `web-detach-reparent-batch` | retire puis réinsère avant le batch `MutationObserver` | même session encore vivante |
| `web-shadow-root-detach-reparent` | retire/réinsère l'élément depuis un `ShadowRoot`, puis le change de root dans le même document | même session vivante et observer réinstallé ; detach terminal ensuite détecté |
| `web-shadow-root-late-reinsert` | sous `Manual`, livre le batch de retrait puis réinsère plus tard dans un autre `ShadowRoot` du même document | watcher de connexion détecte la reconnexion, lifecycle restauré et observer réinstallé |
| `web-detach-cross-document-terminal` | transfère l'élément vers un autre `ownerDocument` avant le batch | `Stopped(HostDetached)`, aucune session dans le nouveau document |
| `web-manual-detach-and-stop` | détache/réinsère un host `Manual`, puis demande l'arrêt | aucune terminaison au detach ; terminaison seulement par l'arrêt explicite |
| `web-visibility-focus` | Playwright bascule entre deux pages/tabs réels et le focus du browsing context | `Foreground` seulement connecté+visible ; snapshots `Foreground/Background` et `Active/Inactive` valides |
| `web-focus-transfer-between-hosts` | transfère l'input entre deux hosts de la même page | un seul host actif ; l'autre publie `Inactive` |
| `web-attach-detach-admission-race` | détache à chacune des frontières d'admission | aucun `create`/`run` après motif terminal ; session admise termine `HostDetached` |
| `web-detach-terminal` | retire et laisse l'élément déconnecté après le batch | session terminée, aucune resurrection |
| `web-multi-session-isolation` | attache deux éléments différents | identities, lifecycle et fermeture indépendants |
| `web-no-implicit-window` | demande une fenêtre sans provider | `Success(WindowRequest)` déjà terminal en `Rejected(Unsupported(RequestWindow))`, aucun DOM créé |
| `web-window-provider-new-session` | provider synchrone retourne un host dans un nouveau browsing context | `OpenedInNewSession`, même factory, `AdditionalHostRequested` et `originatingRequestId` corrélés |
| `web-window-provider-same-context` | provider retourne un élément du browsing context origine | `Rejected(InvalidRequest("element.ownerDocument"))`, aucune session synthétique |
| `web-window-provider-no-context` | provider retourne un élément dont `ownerDocument.defaultView == null` | `Rejected(InvalidRequest("element.ownerDocument"))`, aucune session synthétique |
| `web-window-provider-invalid-element` | provider retourne un élément invalide ou déconnecté sous `StopWhenDetached` | `Rejected(InvalidRequest("element"))`, aucune session synthétique |
| `web-window-provider-invalid-scope` | provider retourne une scope sans `Job` ou déjà inactive | `Rejected(InvalidRequest("parentScope"))` ou `Rejected(ParentScopeCancelled)`, aucune session synthétique |
| `web-window-provider-callback-failure` | provider lève ou retourne une failure hors domaine | `Rejected(PlatformFailure(Web, "WebWindowProvider", "callback-exception"|"invalid-failure"))` terminale |
| `web-pagehide-admission-close` | émet `pagehide` déterministe puis appelle `requestWindow` | `Stopped(HostDetached)` et `Failure(Closed(Host))`, sans requête ni provider ; bridges synchrones libérés sans attendre la terminaison |
| `web-pagehide-navigation` | Playwright navigue réellement après instrumentation externe de la page | `Stopped(HostDetached)` observé avant destruction et `requestWindow` refusé par `Failure(Closed(Host))`; aucun `awaitTermination` exigé |
| `web-pagehide-no-resurrection` | injecte le chemin `pagehide` persisted puis `pageshow`, en complément de la navigation réelle | ancienne session reste terminale ; un nouvel attach est requis |
| `web-element-lease` | exécute `withWebElement` pendant puis après fermeture | callback admis puis `Closed(Surface)` |
| `web-element-lease-concurrent-close` | detach/close pendant une lease active | commit attend le callback ; aucune nouvelle lease ensuite |

Avant l'activation, le validateur devient target-aware :

- `EvidenceMapping` porte le target Kotlin ; `validateMappings` vérifie le
  produit `contract × requiredTargets × scenarios/sentinels`, plutôt qu'une
  allowlist de préfixes JVM ;
- chaque job target écrit le chemin canonique
  `contract-evidence/<contractId>.json`. L'archive du job est étiquetée par
  target (`js` ou `wasmJs`) et l'aggregate indexe donc `(target, contractId)` :
  deux jobs ne s'écrasent pas et une preuve d'un autre target est refusée ;
- `ContractEvidence` reçoit le target réel, le vérifie contre la ligne TSV,
  ne code plus `jvm`, et accepte `O1` lorsqu'un consumer compile produit le
  JUnit canonique exigé par la stratégie. Il conserve commit, schema, target,
  adapter, environnement, compteurs et listes de scénario/sentinelle ;
- les tests du validateur prouvent qu'un contrat `planned` est admis, tandis
  qu'un contrat `active` sans mapping, sentinelle, preuve JSON, commit/schema
  valide ou target requis échoue.

`BCK-001` ne devient `active` que dans la pull request qui fournit tous ses
scénarios dans un vrai navigateur pour JS **et** Wasm, leurs mappings,
sentinelles et preuves valides. `INT-002` devient actif avec les deux consumers
TypeScript ; `INT-003` suit le même gate O3 pour les comportements runtime de
la façade.

`INT-002` compile le même source TypeScript contre les deux `.d.ts` et exige
les unions exhaustives, `bigint`, le wrapper opaque de factory et l'absence de
`Flow`, `CoroutineScope`, `Continuation`, `Throwable`, `Any` ou adresse native
dans les signatures. `INT-003` observe dans une vraie page :

- un échec d'attach levé comme `KadreHostError` portant la `KadreFailure`
  exacte ;
- l'état initial synchronement, les mises à jour ensuite dans la microtask
  queue, et une exception d'observer qui le désinscrit sans terminer Kadre ;
- `requestStop` puis `close`, et l'unique `SessionOutcome` renvoyé par
  `awaitTermination`, sans rejection Promise pour une failure fonctionnelle ;
- le provider TypeScript synchrone, son DTO copié et ses failures fermées :
  une failure autorisée devient le `Rejected(failure)` exact de la
  `WindowRequest`, une exception devient
  `Rejected(PlatformFailure(Web, "WebWindowProvider", "callback-exception"))`
  et une failure hors domaine devient le même domain avec
  `"invalid-failure"`. Ces outcomes résolvent la Promise ; ils ne deviennent
  jamais un `KadreHostError`.

## Harness navigateur

Le driver Web entrera dans le build au même moment que le premier attach DOM
fonctionnel, sous `kadre/contracts/driver/web/`. Il ne dépendra d'aucun
internal de production et ne créera pas de target KMP vide. Il pilotera une
fixture consommatrice liée aux artifacts publics JS et Wasm, recevra leurs
bundles et les exécutera dans de vraies pages locales.

Le runner retenu est Playwright : une unique définition de scénarios pilote
Chromium, Firefox et WebKit lorsque chaque target est supportée. Il sert les
bundles construits par Gradle, injecte les stimuli DOM (notamment le batch
`MutationObserver` et `pagehide`), puis lit uniquement les observations
publiques exportées par la fixture. Il ne simule pas le DOM et n'appelle pas
un mapper Kotlin isolé.

La commande normative sera :

```bash
rtk scripts/test-web-browsers.sh
```

Elle devra imposer un timeout par navigateur, rendre les résultats JUnit et
la preuve contractuelle, puis échouer pour scénario absent, ignored ou skipped.
Les traces, captures et logs sont archivés uniquement en cas d'échec. La
commande exécute le même identifiant de scénario JS et Wasm ; une différence
de glue ne crée jamais un second contrat.

Le gate PR exécute Playwright Chromium pour JS et Wasm ; aucune des deux
combinaisons ne peut être skipped. Le nightly exécute Chromium, Firefox et
WebKit pour chaque combinaison déclarée supportée. Le lockfile introduit dans
la même tranche fixe le runner et ses binaries ; le rapport de preuve porte
leur identité. Une combinaison déclarée supportée qui ne s'exécute pas échoue,
elle ne devient jamais un succès silencieux ou un pseudo-`NotApplicable`.

## Dépendances et ordre d'implémentation

1. Ajouter `BCK-001`, `INT-002`, `INT-003` et `INT-004` planifiés, les
   scénarios, les sentinelles et le protocole de preuve Web, sans modifier le
   runtime, créer de module vide, ou installer de runner navigateur.
2. Porter les variantes JS/Wasm de `foundation`, remplacer dans `runtime` les
   locks, atomiques, horloge, identité, collections et affinité de thread JVM
   par des abstractions multiplateformes, puis prouver le noyau commun sur les
   deux targets. Aucun SDK DOM n'entre dans `runtime`.
3. Ajouter les variantes JS/Wasm de l'umbrella `kadre` et l'agrégation de leurs
   composants, avec publication et consumer Kotlin de chaque target.
4. Créer `platform:web` uniquement avec un attach DOM réel, `MutationObserver`,
   lifecycle, `withWebElement`, driver Playwright et les preuves `BCK-001` et
   `INT-004`. Cette tranche est un adapter d'incubation prouvé, mais pas encore
   un adapter Web supporté.
5. Livrer métriques/redraw, les cinq managers initiaux, le registre de
   capabilities et la façade `@kadre/host` avec `INT-002` et `INT-003`. C'est
   le premier jalon qui peut annoncer un adapter Web supporté.
6. Ajouter, chacun dans une tranche verticale, input et IME, touch/gestures,
   drag-and-drop, puis les operations nécessitant une interaction utilisateur.

Le travail AppKit de migration de `SurfaceInput.openTextInput` vers une méthode
membre est une dépendance explicite de l'IME Web, pas de la fondation Web ni
de l'attach/lifecycle DOM.

## Tests et critères d'acceptation

- Le validateur accepte `BCK-001`, `INT-002`, `INT-003` et `INT-004` en
  `planned` sans exiger de preuve target-specific ; il refuse leur passage à
  `active` sans preuve et sentinelle pour JS **et** Wasm.
- Le protocole de driver associe sans ambiguïté un scénario, un target Kotlin,
  un moteur browser, un bundle et un `contractId`.
- Aucun fichier de l'ancien `kadre-old` n'est importé ni dépendance de
  production ; il peut seulement servir à retrouver des stimuli à couvrir.
- Aucun type DOM, Playwright ou Node n'apparaît dans `foundation` ni dans
  l'API commune.
- La première pull request fonctionnelle ne peut pas activer `BCK-001` si
  `scripts/test-web-browsers.sh` ne vérifie pas tous les scénarios ci-dessus
  sur les deux targets ; elle ne peut pas annoncer `platform:web` supporté
  avant l'activation de `INT-002`, `INT-003`, `INT-004` et les garanties
  initiales de capabilities.
