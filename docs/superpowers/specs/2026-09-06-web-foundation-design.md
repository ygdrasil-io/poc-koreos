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
3. Livrer d'abord le registre WEB, le driver de preuve navigateur et les
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

Les invariants à rendre exécutables sont :

- `StopWhenDetached` rejette un élément initialement déconnecté ; `Manual`
  l'accepte et publie `Attached + Background + Inactive`.
- Le test de détachement a lieu après le batch du `MutationObserver`. Un
  reparentage achevé avant cette livraison conserve la session ; un élément
  encore déconnecté la termine et sa réinsertion requiert une nouvelle
  session.
- Kadre ne crée ni canvas, ni div, ni popup. Sans provider,
  `requestWindow` rend explicitement `Unsupported`.
- Un provider est synchrone, uniquement disponible sur l'overload factory,
  et un nouveau browsing context produit une nouvelle `KadreSession`, jamais
  une fenêtre dans la session originelle.
- `pagehide` ferme l'admission et libère seulement les bridges synchrones
  best-effort. Il n'autorise aucune promesse sur la complétion d'un teardown
  suspendu après destruction du runtime browser.
- `withWebElement` est l'unique escape hatch Kotlin destiné à un renderer
  externe : son callback est borné par la durée de la lease et ne promet pas
  la validité de l'élément ensuite.

Les interactions soumises à la transient user activation (fullscreen,
pointer lock, drop et popup) ne sont pas implémentées dans cette tranche, mais
leur admission sera toujours vérifiée via `InteractionContext`, jamais par un
token conservé.

## Registre et scénarios

Le registre ajoute un contrat `WEB-001` au statut `planned`, référencé par
`DESIGN.md#15.3`, avec les risques : confusion surface/fenêtre, lifecycle DOM
incorrect, session croisée et faux succès d'une capability indisponible.

Ses identifiants de scénario stables sont :

| Scenario ID | Stimulus navigateur réel | Oracle requis |
|---|---|---|
| `web-attach-connected` | attache un élément connecté | session vivante, primary surface présente, aucune Window |
| `web-attach-detached-rejected` | attache `StopWhenDetached` sur élément déconnecté | `InvalidRequest("element")`, aucune session |
| `web-attach-manual-detached` | attache `Manual` sur élément déconnecté | lifecycle `Attached + Background + Inactive` |
| `web-detach-reparent-batch` | retire puis réinsère avant le batch `MutationObserver` | même session encore vivante |
| `web-detach-terminal` | retire et laisse l'élément déconnecté après le batch | session terminée, aucune resurrection |
| `web-multi-session-isolation` | attache deux éléments différents | identities, lifecycle et fermeture indépendants |
| `web-no-implicit-window` | demande une fenêtre sans provider | `Unsupported`, aucun DOM créé |
| `web-pagehide-admission-close` | émet `pagehide` avant une nouvelle opération | admission fermée, cleanup uniquement best-effort |
| `web-element-lease` | exécute `withWebElement` pendant puis après fermeture | callback admis puis `Closed(Surface)` |

`WEB-001` ne devient `active` que dans la pull request qui fournit ces
scénarios dans un vrai navigateur pour JS **et** Wasm, leurs lignes
`evidence.tsv`, et le rapport `contract-evidence/WEB-001.json` conforme à
`TEST-STRATEGY.md`.

## Harness navigateur

Le driver Web entrera dans le build au même moment que le premier attach DOM
fonctionnel, sous `kadre/contracts/driver/web/`. Il ne dépendra pas de la
production et ne créera pas de target KMP vide. Il recevra les bundles JS et
Wasm de cette tranche et les exécutera dans de vraies pages locales.

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

La cible minimale de la première activation est Chromium pour JS et Wasm ;
Firefox et WebKit sont ajoutés à la matrice dès qu'ils supportent chacune des
deux distributions livrées. Une absence de support déclarée ne transforme
jamais un scénario attendu en succès silencieux : elle apparaît explicitement
comme capability/target non couvert dans le rapport CI.

## Dépendances et ordre d'implémentation

1. Ajouter `WEB-001` planifié, les scénarios et le protocole de preuve Web,
   sans modifier le runtime, créer de module vide, ou installer de runner
   navigateur.
2. Rendre le noyau de session utilisable par les targets Web dans une tranche
   séparée, avec ses tests communs : aucun SDK DOM dans `runtime`.
3. Créer `platform:web` avec les targets JS/Wasm uniquement avec un attach DOM
   réel, `MutationObserver`, lifecycle, le driver Playwright et les preuves
   WEB-001. Cette tranche active le contrat.
4. Ajouter, chacun dans une tranche verticale, métriques/redraw, input et
   IME, touch/gestures, drag-and-drop, puis les operations nécessitant une
   interaction utilisateur.

Le travail AppKit de migration de `SurfaceInput.openTextInput` vers une méthode
membre est une dépendance explicite de l'IME Web, pas de la fondation Web ni
de l'attach/lifecycle DOM.

## Tests et critères d'acceptation

- Le validateur accepte la ligne `WEB-001` en `planned` sans exiger de preuve
  target-specific ; il refusera son passage à `active` sans preuve JS et Wasm.
- Le protocole de driver associe sans ambiguïté un scénario, un bundle, un
  navigateur et un `contractId`.
- Aucun fichier de l'ancien `kadre-old` n'est importé ni dépendance de
  production ; il peut seulement servir à retrouver des stimuli à couvrir.
- Aucun type DOM, Playwright ou Node n'apparaît dans `foundation` ni dans
  l'API commune.
- La première pull request fonctionnelle ne peut pas déclarer l'attach Web
  livré si `scripts/test-web-browsers.sh` ne vérifie pas les neuf scénarios
  ci-dessus sur les deux targets.
