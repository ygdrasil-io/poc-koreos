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
réellement l'exercer. La première tranche fonctionnelle activera les contrats
dans le même commit que l'attach DOM réellement testé et son driver.

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
- Le test de détachement a lieu après le batch du `MutationObserver`. Un
  reparentage achevé avant cette livraison conserve la session uniquement si
  `ownerDocument` reste le document initial ; un transfert vers un autre
  document termine la session, même si l'élément est déjà reconnecté. Un
  élément encore déconnecté la termine et sa réinsertion requiert une nouvelle
  session.
- `visibilitychange` pilote `Foreground` ou `Background` depuis le document ;
  `focus` et `blur` du browsing context pilotent `Active` ou `Inactive`. Un
  document background publie toujours `Inactive`, et un document foreground
  ne devient `Active` que lorsqu'il possède le focus. `Manual` conserve la
  session lors d'un detach/reinsert et ne devient terminal que par
  `requestStop` ou un motif externe.
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

Le registre ajoute les deux contrats suivants au statut `planned` :

```tsv
WEB-001	planned	DESIGN.md#15.3	DOM host attach and lifecycle	surface/window confusion, lifecycle loss, cross-session leak or false capability	O3	web-attach-connected,web-attach-detached-rejected,web-attach-manual-detached,web-detach-reparent-batch,web-detach-cross-document-terminal,web-manual-detach-and-stop,web-visibility-focus,web-attach-detach-admission-race,web-detach-terminal,web-multi-session-isolation,web-no-implicit-window,web-window-provider-new-session,web-window-provider-invalid-host,web-window-provider-callback-failure,web-pagehide-admission-close,web-pagehide-navigation,web-pagehide-no-resurrection,web-element-lease,web-element-lease-concurrent-close	js,wasmJs	SurfaceCapabilities.platformAccess,WindowManagerCapabilities.requestWindow	web-surface-never-window,web-no-implicit-dom,web-cross-session-isolation,web-detach-no-resurrection,web-provider-no-same-document-window,web-active-gate-requires-js-and-wasm	-
WEB-002	planned	INTEROP-EXPORTS.md#6	JS and Wasm host facade	foreign API drift, leaked coroutine types or divergent declarations	O1	web-typescript-consumer-js,web-typescript-consumer-wasm,web-host-failure-mapping,web-host-state-subscription	js,wasmJs	-	web-host-no-coroutine-leak,web-host-identical-dts	-
```

`WEB-001` porte l'adapter Kotlin ; `WEB-002` porte `@kadre/host`, son
`KadreWeb.attach`, les `KadreHostError`, un `MainScope` par handle, les
notifications d'état sérialisées en microtask et le même consumer TypeScript
pour JS et Wasm. L'activation de `WEB-001` seule ne qualifie jamais
`platform:web` comme adapter supporté ou publiable.

Ses identifiants de scénario stables sont :

| Scenario ID | Stimulus navigateur réel | Oracle requis |
|---|---|---|
| `web-attach-connected` | attache un élément connecté | session vivante, primary surface présente, aucune Window |
| `web-attach-detached-rejected` | attache `StopWhenDetached` sur élément déconnecté | `InvalidRequest("element")`, aucune session |
| `web-attach-manual-detached` | attache `Manual` sur élément déconnecté | lifecycle `Attached + Background + Inactive` |
| `web-detach-reparent-batch` | retire puis réinsère avant le batch `MutationObserver` | même session encore vivante |
| `web-detach-cross-document-terminal` | transfère l'élément vers un autre `ownerDocument` avant le batch | `Stopped(HostDetached)`, aucune session dans le nouveau document |
| `web-manual-detach-and-stop` | détache/réinsère un host `Manual`, puis demande l'arrêt | aucune terminaison au detach ; terminaison seulement par l'arrêt explicite |
| `web-visibility-focus` | Playwright bascule entre deux pages/tabs réels et le focus du browsing context | snapshots ordonnés `Foreground/Background` et `Active/Inactive` valides |
| `web-attach-detach-admission-race` | détache à chacune des frontières d'admission | aucun `create`/`run` après motif terminal ; session admise termine `HostDetached` |
| `web-detach-terminal` | retire et laisse l'élément déconnecté après le batch | session terminée, aucune resurrection |
| `web-multi-session-isolation` | attache deux éléments différents | identities, lifecycle et fermeture indépendants |
| `web-no-implicit-window` | demande une fenêtre sans provider | `Unsupported`, aucun DOM créé |
| `web-window-provider-new-session` | provider synchrone retourne un host dans un nouveau browsing context | `OpenedInNewSession`, même factory, `AdditionalHostRequested` et `originatingRequestId` corrélés |
| `web-window-provider-invalid-host` | provider retourne même document, élément invalide/déconnecté, ou scope absente/inactive | `Rejected` avec la failure fermée exacte, aucune session synthétique |
| `web-window-provider-callback-failure` | provider lève ou retourne une failure hors domaine | `Rejected(PlatformFailure(Web, "WebWindowProvider", "callback-exception"|"invalid-failure"))` terminale |
| `web-pagehide-admission-close` | émet `pagehide` déterministe avant une nouvelle opération | admission fermée et bridges synchrones libérés, sans attendre la terminaison |
| `web-pagehide-navigation` | Playwright navigue réellement après instrumentation externe de la page | fermeture synchrone observée avant destruction ; aucun `awaitTermination` exigé |
| `web-pagehide-no-resurrection` | injecte le chemin `pagehide` persisted puis `pageshow`, en complément de la navigation réelle | ancienne session reste terminale ; un nouvel attach est requis |
| `web-element-lease` | exécute `withWebElement` pendant puis après fermeture | callback admis puis `Closed(Surface)` |
| `web-element-lease-concurrent-close` | detach/close pendant une lease active | commit attend le callback ; aucune nouvelle lease ensuite |

Avant l'activation, le validateur de registre est généralisé : il déduit le
gate de `requiredTargets`, au lieu d'une allowlist de préfixes JVM, et ses
tests prouvent qu'un contrat `planned` est admis mais qu'un contrat `active`
sans mapping ni preuve pour chacun de ses targets échoue. Le format de preuve
devient lui aussi target-neutral : il ne code ni `jvm` ni un JUnit JVM. Chaque
target Kotlin produit un fichier distinct,
`contract-evidence/js/WEB-001.json` et
`contract-evidence/wasmJs/WEB-001.json`; le moteur, sa version et les
capabilities observées restent dans l'environnement de la preuve, pas dans le
champ `target`.

`WEB-001` ne devient `active` que dans la pull request qui fournit tous ses
scénarios dans un vrai navigateur pour JS **et** Wasm, leurs mappings,
sentinelles et deux preuves valides. `WEB-002` suit le même principe avec les
deux compilations TypeScript et les tests runtime de façade.

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

1. Ajouter `WEB-001` et `WEB-002` planifiés, les scénarios, les sentinelles et
   le protocole de preuve Web, sans modifier le runtime, créer de module vide,
   ou installer de runner navigateur.
2. Porter les variantes JS/Wasm de `foundation`, remplacer dans `runtime` les
   locks, atomiques, horloge, identité, collections et affinité de thread JVM
   par des abstractions multiplateformes, puis prouver le noyau commun sur les
   deux targets. Aucun SDK DOM n'entre dans `runtime`.
3. Ajouter les variantes JS/Wasm de l'umbrella `kadre` et l'agrégation de leurs
   composants, avec publication et consumer Kotlin de chaque target.
4. Créer `platform:web` uniquement avec un attach DOM réel, `MutationObserver`,
   lifecycle, `withWebElement`, driver Playwright et les preuves `WEB-001`.
   Cette tranche est un adapter d'incubation prouvé, mais pas encore un adapter
   Web supporté.
5. Livrer métriques/redraw, les cinq managers initiaux, le registre de
   capabilities et la façade `@kadre/host` avec `WEB-002`. C'est le premier
   jalon qui peut annoncer un adapter Web supporté.
6. Ajouter, chacun dans une tranche verticale, input et IME, touch/gestures,
   drag-and-drop, puis les operations nécessitant une interaction utilisateur.

Le travail AppKit de migration de `SurfaceInput.openTextInput` vers une méthode
membre est une dépendance explicite de l'IME Web, pas de la fondation Web ni
de l'attach/lifecycle DOM.

## Tests et critères d'acceptation

- Le validateur accepte `WEB-001` et `WEB-002` en `planned` sans exiger de
  preuve target-specific ; il refuse leur passage à `active` sans preuve et
  sentinelle pour JS **et** Wasm.
- Le protocole de driver associe sans ambiguïté un scénario, un target Kotlin,
  un moteur browser, un bundle et un `contractId`.
- Aucun fichier de l'ancien `kadre-old` n'est importé ni dépendance de
  production ; il peut seulement servir à retrouver des stimuli à couvrir.
- Aucun type DOM, Playwright ou Node n'apparaît dans `foundation` ni dans
  l'API commune.
- La première pull request fonctionnelle ne peut pas activer `WEB-001` si
  `scripts/test-web-browsers.sh` ne vérifie pas tous les scénarios ci-dessus
  sur les deux targets ; elle ne peut pas annoncer `platform:web` supporté
  avant l'activation de `WEB-002` et les garanties initiales de capabilities.
