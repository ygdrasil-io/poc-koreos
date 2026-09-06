# New Kadre — Stratégie normative de test et de preuve CI

**Statut :** stratégie fermée pour l’implémentation de la nouvelle API.  
**Portée :** API commune, artifact `test`, adapters officiels (adaptateurs), façades Java/Swift/JavaScript et gates CI (contrôles bloquants).  
**Objectif CI initial :** p95 du gate bloquant d’une PR inférieur ou égal à 10 minutes, ajustable uniquement par une modification revue de cette stratégie.  
**Principe :** maximiser les comportements prouvés, pas le nombre de tests ni le pourcentage de lignes exécutées.

Ce document définit ce qui constitue une preuve acceptable des contrats de `DESIGN.md`, `PROJECT-ARCHITECTURE.md`, `PUBLIC-API-CATALOG.md`, `OPERATION-CONTRACTS.md`, `POLICY-PROFILES.md`, `BACKEND-CAPABILITIES.md` et `INTEROP-EXPORTS.md`. Un test absent de cette stratégie peut rester utile localement, mais il ne peut pas être compté comme preuve de conformité sans satisfaire les règles ci-dessous.

## 1. Objectifs et non-objectifs

La stratégie doit détecter en CI :

- une transition d’état, `Failure` (échec), cancellation (annulation) ou règle d’ownership (propriété) incorrecte ;
- une capability (capacité) qui ment sur le comportement effectif de l’adapter ;
- une divergence entre backends (implémentations de plateforme) sur un contrat portable ;
- une fuite entre sessions ou une ressource survivant à son owner ;
- une perte, un réordonnancement, une conflation ou un overflow non conforme à la policy ;
- une API source/ABI/exportée différente du catalogue approuvé ;
- un test déclaré mais non exécuté, ignoré, masqué ou rendu vert par retry (nouvelle tentative automatique).

La stratégie ne cherche pas à :

- atteindre un pourcentage arbitraire de line/branch coverage (couverture des lignes/branches) ;
- tester Kotlin, kotlinx.coroutines ou un SDK à leur place ;
- figer les classes internes, le nombre d’allocations ou l’ordre d’appels privés ;
- utiliser une capture d’écran d’un renderer comme oracle de la gestion de fenêtres ;
- dupliquer le même scénario dans chaque module sans nouvelle frontière ni nouveau risque ;
- rendre le gate PR dépendant d’un matériel absent des runners ordinaires.

Kadre ne rend ni widget ni contenu graphique. Les tests visuels de samples restent des preuves d’intégration de renderers externes, pas des preuves normatives de Kadre. La capture étant dans le périmètre de Kadre, ses tests de pixels utilisent une source synthétique connue ou une surface contrôlée et vérifient format, orientation, région, cadence et lifetime (durée de vie) ; ils ne comparent pas l’apparence d’une UI à une golden image (image de référence).

## 2. Admission d’un test comportemental

Chaque test comportemental obligatoire satisfait simultanément ces sept critères :

1. il cite au moins un `contractId` stable du registre de section 3 ;
2. son stimulus franchit une frontière publique, host ou SDK pertinente ;
3. son oracle provient d’un invariant, d’un modèle de référence, d’une fixture revue ou d’une observation externe indépendante ;
4. il n’appelle aucun helper de production pour calculer la valeur attendue qu’il compare à ce même helper ;
5. il observe un résultat public : state, event, outcome, failure, cleanup, export consommable ou effet host ;
6. il nomme au moins une mutation plausible qu’il doit détecter ;
7. il est déterministe dans le gate PR et produit une preuve d’exécution suffisante pour être rejoué localement.

Un test qui échoue à l’un de ces critères est supprimé, déplacé vers une vérification structurelle explicitement nommée ou réécrit. Ajouter une assertion sans nouvelle mutation détectable ne justifie pas un nouveau test.

### 2.1 Niveaux d’indépendance de l’oracle

| Niveau | Exemple | Valeur de preuve |
|---|---|---|
| `O0 — miroir` | reconstruire l’expected avec le mapper de production testé | rejeté |
| `O1 — trivial` | construire une data class puis relire exactement ses propriétés | utile seulement comme consumer compile test, jamais comme preuve comportementale |
| `O2 — invariant/modèle` | comparer une trace publique à une machine à états indépendante | preuve commune admise |
| `O3 — frontière externe` | injecter un événement SDK/DOM et observer uniquement l’API Kadre | preuve adapter admise |
| `O4 — différentiel` | normaliser puis comparer la même trace sur plusieurs adapters | preuve portable forte |

Une preuve comportementale du common code exige au minimum `O2`. Une preuve qu’un adapter traduit réellement son host exige `O3`. `O4` complète mais ne remplace pas l’oracle de référence : deux backends peuvent partager le même bug.

### 2.2 Vérifications structurelles admises

Les vérifications suivantes ne prétendent pas prouver un comportement runtime, mais restent bloquantes car elles protègent une frontière consommatrice :

- consumer compile tests Kotlin, Java, Swift et TypeScript ;
- dumps ABI et headers/`.d.ts` comparés au catalogue approuvé ;
- couverture nominative de `API-MIGRATION.md` ;
- parité des enums, sealed variants, nullabilité et opt-ins exportés ;
- compilation de chaque target et link des bridges natifs.

Elles utilisent un consumer externe minimal, jamais une assertion interne du type « la déclaration existe parce que la réflexion la trouve dans le module qui la déclare » lorsque le consumer compile test peut le prouver directement.

## 3. Registre de contrats et traçabilité

L’implémentation matérialise un registre lisible par machine, suivi dans le dépôt et séparé du code de production. Une entrée possède exactement :

| Champ | Contenu |
|---|---|
| `contractId` | identifiant stable et unique |
| `status` | `planned`, `active` ou `retired` ; ce statut décrit l’activation de la preuve, jamais une version |
| `source` | document et section normative |
| `subject` | type, opération, flow ou frontière interop |
| `risk` | violation observable visée |
| `oracle` | `O1`, `O2`, `O3` ou `O4` |
| `scenarios` | IDs des scénarios positifs, négatifs et de race |
| `requiredTargets` | targets sur lesquels la preuve est obligatoire |
| `conditionalCapabilities` | capabilities qui sélectionnent Supported/Unsupported sans skip |
| `sentinels` | mutations fautives que ces scénarios doivent tuer |
| `retirementRef` | référence obligatoire uniquement pour `retired`, `null` autrement |

Un contrat `planned` appartient au design fermé mais n’est pas encore promis par le livrable courant. Il ne peut correspondre à aucune capability annoncée `Supported` ; l’opération reste explicitement `Unsupported` ou l’artifact concerné n’est pas publié. Un contrat `active` possède toutes les preuves imposées par cette stratégie et entre dans chaque gate applicable. Un contrat `retired` ne possède plus de scénario exécutable et conserve une `retirementRef` non vide vers la décision de suppression. Le passage `planned → active` et `active → retired` est revu dans le même changement que le registre et les preuves concernés.

Les familles d’identifiants sont fermées :

| Préfixe | Domaine |
|---|---|
| `API` | forme publique, ABI, annotations et migration |
| `SES` | application, session, lifecycle et structured concurrency |
| `POL` | policies, queues, budgets et diagnostics |
| `SUR` | surfaces et interactions |
| `DSP` | displays |
| `WIN` | fenêtres et requêtes de fenêtre |
| `INP` | clavier, pointer, touch, gestes, IME, drop et raw input |
| `GPD` | gamepads, routing et effets |
| `CAP` | permission, sources, sessions, frames et colorimétrie de capture |
| `BCK` | topology, capabilities et adapters natifs |
| `INT` | façades Java, Swift, JS/Wasm et escape hatches |

Un ID n’est jamais réutilisé pour un autre contrat. Un contrat supprimé reste marqué `retired` avec la référence du breaking change ; son test est supprimé au lieu d’être conservé comme inertie historique.

### 3.1 Gate de complétude

Le registre actif est complet uniquement si chaque contrat dont `status == active` satisfait les règles suivantes. Les contrats `planned` restent visibles dans l’audit mais ne produisent aucune preuve factice, aucun skip et aucune capability `Supported`. Les contrats `retired` sont exclus de l’ensemble exécutable et exigent leur référence de retrait :

- chaque ligne de `OPERATION-CONTRACTS.md` possède un scénario de succès ou résultat attendu, chaque family de failure directe, la cancellation avant admission et l’autorité après commit/handoff applicable ;
- chaque domaine fermé de failure dans un state ou outcome possède un cas admis et un cas hors domaine rejeté par le fake/test fixture ;
- chaque `StateFlow` possède au moins une transition et son snapshot terminal ;
- chaque `Flow` possède température, absence de replay, ordering, fermeture normale et fermeture par failure ;
- chaque champ de policy et chaque profil possède une preuve de valeur et au moins une preuve d’effet pour chaque action d’overflow distincte ;
- chaque ligne `G` possède son chemin positif ; chaque ligne `C` possède les branches runtime atteignables imposées par sa promesse ; chaque ligne `N(op)` possède son résultat `Unsupported` exact ; les préconditions permission/interaction/unavailable sont couvertes lorsqu’elles ont un sens ;
- chaque ligne `G`, `C`, `N(op)`, `S` ou `H` de `BACKEND-CAPABILITIES.md` est reliée à une preuve target-specific ;
- chaque façade promise de `INTEROP-EXPORTS.md` compile depuis son langage consommateur et observe attach, state, stop et outcome.

Le nombre de tests n’est pas un seuil. Le gate compare l’ensemble des `contractId/scenarioId/target` actifs requis aux preuves réellement exécutées ; un scénario dupliqué ne masque jamais un ID actif absent.

## 4. Architecture de la contract suite

La contract suite est black-box du point de vue de Kadre. Elle se compose de quatre éléments séparés :

1. un scénario portable et son oracle de référence ;
2. un `ContractDriver` test-only qui stimule le host ou le fake ;
3. l’application de test, qui utilise exclusivement l’API publique consommateur ;
4. un recorder de preuve, qui capture les observations publiques et l’environnement.

`ContractDriver` n’entre jamais dans l’artifact consommateur. Il peut piloter un controller virtuel, une API DOM, un simulateur ou un protocole natif, mais il ne peut pas lire les registries, queues, revisions ou jobs internes pour décider que le test passe.

### 4.1 Fake et modèle de référence

`FakeKadreHost` constitue le moteur déterministe de preuve du contrat commun, pas l’oracle. L’oracle est une machine à états plus petite et indépendante qui reçoit les actions du scénario et calcule les observations légalement possibles. Le test compare ensuite la trace publique du fake à ce modèle.

Les contrôleurs virtuels servent uniquement de stimulus. Un test ne valide pas `VirtualWindowController.openHere` en vérifiant qu’il a exécuté sa propre branche ; il vérifie que `WindowRequest.state`, `await`, `WindowManager.state`, revisions et events suivent le contrat après ce stimulus.

### 4.2 Adapters réels

Chaque adapter officiel exécute :

- le noyau portable de la contract suite ;
- les scénarios conditionnels correspondant à ses capabilities publiées ;
- des tests de mapping propres au SDK uniquement lorsque la traduction ne peut pas être prouvée par la suite portable.

Le driver réel provoque l’événement depuis la frontière opposée à Kadre : DOM event dispatch/browser API, lifecycle/simulateur Android, `UIScene`/UIKit, AppKit, Win32 message queue, X11 server ou compositor Wayland. Appeler directement le mapper de production avec une valeur puis comparer son résultat à une table calculée par ce mapper est interdit.

Une capability `Unsupported` ne saute pas le scénario : le scénario appelle l’opération et exige la failure exacte. Une capability `Supported` exécute le chemin correspondant. Une capability dépendant du runtime enregistre son snapshot dans la preuve et prend l’une de ces deux branches fermées.

Le snapshot observé sur un runner ne peut pas réduire la promesse versionnée de `BACKEND-CAPABILITIES.md`. Une ligne `G` doit toujours prouver le chemin positif. Une ligne `C` doit prouver en PR la branche réellement observable, puis au moins un chemin `Supported` et un chemin `Unsupported` sur l’ensemble nightly/release lorsque les deux sont réalisables. Si aucun environnement contrôlé ne peut exercer le chemin positif promis, l’adapter n’est pas déclarable supporté pour une release.

### 4.3 Tests différentiels

Les traces portables sont normalisées uniquement sur les valeurs explicitement non portables : IDs opaques, timestamps absolus de session, noms décoratifs et codes de plateforme. Les revisions relatives, séquences, outcomes, failures, relations d’ordre et ownership ne sont jamais normalisés.

Une trace fake sert de référence fonctionnelle, puis la même famille de scénario est comparée aux adapters réels. Une différence autorisée doit être expliquée par une capability ou une variante publique ; aucune allowlist de backend cachée n’est admise.

## 5. Portefeuille de preuves communes

### 5.1 Session et lifecycle

La suite couvre au minimum :

- attach sans `Job`, avec job annulé, host occupé et policy unsupported ;
- ordre `Starting → Running → Stopping → Terminated` ;
- factory/run jamais appelés après admission d’un detach terminal ;
- retour normal avec enfants structurés encore actifs ;
- exception applicative, cancellation du job applicatif, parent cancellation et host detach ;
- priorité entre stop non fatal, failure primaire et cleanup failure ;
- timeout de shutdown avec scheduler disponible et absence de fausse promesse lorsque le dispatcher est monopolisé ;
- deux sessions concurrentes sans échange d’IDs, windows, devices, jobs ou failures.

### 5.2 State, events et operations

Pour toute mutation révisionnée, un scénario commun prouve :

1. validation et absence d’effet avant admission ;
2. publication du snapshot composé ;
3. publication éventuelle de l’événement avec la même révision ;
4. résultat/outcome portant l’autorité documentée ;
5. comportement d’une cancellation avant admission, avant commit réversible et après commit ;
6. fermeture/teardown sans réintroduction tardive de ressource.

Les no-op, partial apply, stale revision, capability change concurrente et fermeture concurrente sont des scénarios distincts uniquement parce qu’ils tuent des mutations différentes.

### 5.3 Delivery, backpressure (contre-pression) et diagnostics

Les profils `Default`, `Realtime` et `Recording` sont testés avec horloge et scheduler virtuels. La suite prouve :

- FIFO discret et barrières entre lanes discrètes/continues ;
- réduction exacte de `Latest` et `Coalesced` par clé logique ;
- aplatissement ingress puis collector de `EventDeliverySpan` ;
- isolation de deux collectors de vitesses différentes ;
- actions `CancelSlowCollector`, `CloseSource`, `FailSession`, `DropOldestAndReport` et `DropLatestAndReport` ;
- limites par flow/session et absence de budget Kadre sur la collection d’un `StateFlow` ;
- saturation des compteurs à `Long.MAX_VALUE` et absence de diagnostic récursif ;
- fermeture normale ou avec la même failure stable, sans dernier événement inventé pendant teardown.

### 5.4 Fenêtres, input, devices et capture

Le registre répartit les scénarios au moins sur ces risques :

| Domaine | Risques obligatoires |
|---|---|
| surface | state effectif, custom cursor copié, pointer capture, hit testing, redraw coalescé, detach terminal |
| display | inventaire complet, permission, viewport honnête, retrait terminal, limite sans inventaire partiel |
| window | request/cancel/close races, nouvelle session, partial update, close interception, primary stable |
| interaction | transient activation non réutilisable, expiration, wrong surface, callback non réentrant |
| clavier/pointer/touch | snapshot avant event, unknown conservé, reset sans releases synthétiques, deltas finis |
| IME | offsets UTF-16, selection valide, anti-stale revision, fermeture de composition |
| drop | offre unique, claim single-winner, timeout, single-use/replayable, chunks copiés et bornés |
| raw input | permission, suspension récupérable, overflow terminal, owner fermé |
| gamepad | routing multi-session, neutralisation, reconnexion avec nouvel ID, ownership des effets |
| capture | permission/source completeness, stale source, picker, single collector, lease frame, reconfigure et budget octets |

Les formats de pixels utilisent de petits buffers hand-authored dont les bytes attendus sont calculés depuis la spec de layout, jamais par le code de copie de production. Les tests de région/orientation utilisent des motifs synthétiques asymétriques afin qu’une inversion, rotation ou crop incorrect ne puisse pas passer.

## 6. Tests par propriétés, tests par modèle et concurrence

### 6.1 Gate PR déterministe

Les propriétés utilisent des cas limites explicites puis 32 cas générés par propriété. La seed (graine aléatoire) est dérivée de façon stable de `contractId + scenarioId`; aucune horloge, ordre de découverte ou source aléatoire globale ne la modifie. Un échec publie la seed, l’index, l’entrée minimale réduite et la trace publique.

Les propriétés adaptées sont notamment :

- géométrie, rounding et overflow arithmétique ;
- bornes de texte, collections, images et payloads ;
- relations `PixelPlaneLayout`/format ;
- séquences de lifecycle, request/cancel/close et ownership ;
- combinaison de delivery spans ;
- invariants de snapshots après une suite d’actions valide.

Un générateur qui ne produit que des valeurs déjà acceptées par le constructeur testé est insuffisant : il génère aussi les frontières invalides et vérifie l’erreur publique ou de construction exacte.

### 6.2 Exploration des races

Le fake expose des points de scheduling test-only correspondant aux frontières publiques d’admission, commit, handoff, publication et cleanup. La suite explore toutes les interleavings de deux actions concurrentes pour les races fermées suivantes :

- attach/detach ;
- stop/application failure/parent cancellation ;
- requestWindow/cancel/requester close/new host connect ;
- apply/capability change/owner close ;
- close request/respond/forced close/deadline ;
- permission waiter cancellation/prompt completion ;
- drop claim/timeout/owner close ;
- capture open cancellation/handoff/source loss ;
- collectFrames/stop/reconfigure/collector failure ;
- gamepad routing/effect stop/disconnect.

Le test vérifie une histoire linéarisable contre le modèle, pas un délai ou un ordre de threads. Les stress tests sur threads réels complètent cette preuve en nightly mais ne la remplacent pas.

### 6.3 Nightly aléatoire

Le nightly exécute au minimum 16 seeds indépendantes et 256 cas par propriété concernée, ainsi que 100 répétitions des scénarios de race sur dispatchers réels. Toute failure devient une fixture de reproduction versionnée avant correction. Un test flaky (instable) n’est jamais rendu vert par un retry automatique.

## 7. Sentinelles de sensibilité

La contract suite contient des implémentations test-only volontairement fautives. Le gate exige que chaque sentinelle fasse échouer au moins un scénario attendu et que toutes soient tuées :

1. event publié avant le state ;
2. revision ou sequence non incrémentée ;
3. `Unsupported` transformé en succès/no-op ;
4. cancellation après commit provoquant un rollback ;
5. owner non fermé avant un handoff annulé ;
6. ressource réintroduite après teardown ;
7. overflow silencieux sans compteur/span ;
8. failure hors du domaine fermé de l’opération/outcome ;
9. ID ou handle visible depuis une autre session ;
10. frame/drop buffer réutilisé ou mutable après le callback ;
11. collector lent bloquant l’ingress ou un autre collector ;
12. capability publiée après l’état qui en dépend.

Une nouvelle famille de contrat ajoute une sentinelle seulement si aucune sentinelle existante ne représente sa mutation dominante. Le nombre de mutants n’est pas maximisé ; leur catalogue protège les fautes architecturales les plus coûteuses.

Un outil général de mutation testing (test par mutations) peut tourner en nightly sur le common code JVM et produire un rapport informatif. Aucun score global de mutation n’est un gate : le gate utile est 100 % des sentinelles architecturales tuées et zéro `contractId` obligatoire sans scénario.

## 8. Interop et API publique

Les consumer compile tests utilisent de petits projets externes à la source testée :

- Kotlin KMP importe uniquement les packages du catalogue et compile sur chaque target publiée : Android, iOS, JS, Wasm et JVM desktop couvrant macOS, Linux et Windows ;
- un consumer négatif prouve que l’ancienne API et ses bindings ne sont plus importables, et qu’aucun type `org.graphiks.kadre.internal.*` ne fuit dans une signature contractuelle ; il ne prétend pas rendre inimportables les symboles de liaison techniquement publics d’un artifact transitif interne ;
- Java attache, observe, stoppe et attend une session sans signature `Continuation` publique promise ;
- Swift compile le host UIKit/SwiftUI, observe le state et vérifie la cancellation d’un waiter ;
- TypeScript compile exactement le `.d.ts` promis, y compris `bigint`, discriminated unions et wrapper opaque de factory ;
- les quatre escape hatches sont accessibles uniquement depuis le target Kotlin et exigent les deux opt-ins exacts.

Les fixtures positives et négatives sont versionnées. Un simple diff de fichier généré ne remplace pas leur compilation avec le toolchain supporté.

## 9. Matrice CI de pull request

Toutes les PR exécutent le gate sans `paths-ignore`. Les jobs partent en parallèle et convergent vers un unique statut de branch protection `kadre-pr-contracts`.

| Job logique | Preuve obligatoire | Cible d’exécution p95 |
|---|---|---:|
| `spec-api-interop` | registre, migration, ABI, exports et consumers négatifs/positifs | 2 min |
| `common-contracts` | modèle fake, policies, properties PR et sentinelles | 5 min |
| `web-contracts` | JS et Wasm dans un vrai browser, même IDs de scénario | 8 min |
| `android-contracts` | host tests + émulateur, lifecycle/view/input minimal réel | 8 min |
| `uikit-contracts` | Kotlin/Native + simulateur booté, scene/lifecycle/surface | 8 min |
| `appkit-contracts` | attach/run-loop/window sur runner macOS | 8 min |
| `linux-contracts` | X11 et Wayland sur glibc et musl | 8 min |
| `windows-contracts` | Win32 message loop/window/input sur runner Windows | 8 min |

Les jobs backend n’exécutent pas une seconde fois toute la suite common pure ; ils exécutent le noyau portable via leur driver, les capabilities de leur environnement et leurs traductions SDK. Une commande ne mélange pas compilation générale, samples visuels et preuve contractuelle si cela rend l’échec impossible à attribuer.

### 9.1 Budget et mesure

- SLO initial : p95 du temps mural `workflow created → aggregate completed` sur les 20 derniers gates PR réussis inférieur ou égal à 10 minutes. Les gates échoués restent comptabilisés séparément dans la fiabilité et ne peuvent jamais être retirés du rapport ; leur arrêt précoce ne réduit pas artificiellement la mesure de durée.
- SLO d’exécution : p95 de chaque job, hors attente de runner, inférieur ou égal à 8 minutes.
- Timeout hard initial : 15 minutes par job et timeout externe de chaque processus enfant.
- Le rapport CI publie temps mural, attente de runner, durée d’exécution, cache hit/miss et job critique.
- Une attente GitHub exceptionnelle reste visible dans le temps mural mais est séparée de la durée de test pour guider l’action.

Le dépassement du SLO ne rend pas automatiquement un test non bloquant et n’autorise aucun skip. L’ordre de correction est : retirer les doublons, supprimer `--refresh-dependencies` des exécutions ordinaires, préinstaller/pinner les dépendances, améliorer les caches, répartir les scénarios indépendants, puis déplacer seulement une preuve statistique redondante vers nightly. Modifier 10 ou 15 minutes exige une modification revue de ce document et une justification fondée sur les 20 exécutions précédentes.

### 9.2 Intégrité du gate

Pour pouvoir être accepté comme `success`, chaque job obligatoire :

- produit au moins un testcase et un fichier `contract-evidence/<contractId>.json` par contrat actif de la target ;
- refuse test skipped/ignored, failure, error, XML incohérent ou scénario attendu absent ;
- interdit `continue-on-error`, `|| true`, retry automatique et condition `if` masquant la preuve ;
- possède un watchdog externe qui tue aussi les processus enfants ;
- archive traces, seeds, capability snapshot et logs uniquement en cas d’échec, sauf petits rapports de synthèse ;
- n’utilise pas `--refresh-dependencies` dans le chemin normal ;
- n’accepte aucune baseline régénérée automatiquement.

L’aggregate utilise `always()` uniquement pour inspecter tous les résultats, puis échoue si un job requis n’est pas `success`. `fail-fast` reste désactivé dans les matrices afin de conserver toutes les preuves d’un même commit.

## 10. Preuve d’exécution produite

Chaque target produit un `contract-evidence/<contractId>.json` par contrat actif, à structure canonique et listes triées. Chaque fichier n’est pas comparé octet par octet à une baseline, car sa durée varie ; son schéma versionné et sa sémantique sont validés. Il contient au minimum :

- `schemaVersion` ;
- commit, target, OS/runtime/toolchain et adapter ;
- durée de la suite en millisecondes ;
- capability snapshot initial et transitions provoquées ;
- liste triée de `{ contractId, scenarioId, result, oracle }` ;
- seed et replay fixture lorsqu’applicables ;
- liste des sentinelles tuées ;
- compteur de tests/skips/failures/errors cohérent avec JUnit.

`result` est un domaine fermé `Passed | Failed` ; il n’existe ni `Skipped`, ni `Ignored`, ni `NotApplicable`. Le validateur aggregate recalcule les scénarios actifs requis depuis le registre, la promesse documentée et l’environnement déclaré. Il ne fait pas confiance à un simple compteur fourni par le test runner. Une preuve inconnue, dupliquée avec deux résultats, absente ou associée à une mauvaise target fait échouer le gate.

### 10.1 Protocole des preuves browser Web

La fixture consommatrice publique est compilée et exécutée séparément pour JS et Wasm. Les deux targets utilisent le même consumer TypeScript et le même scenario target-neutre `web-typescript-consumer` ; aucun ID suffixé par target ne peut remplacer cette preuve commune. Chaque job produit son propre rapport JUnit et ses JSON canoniques, étiquetés par target, commit Git et moteur dans l'artifact CI. Le validateur reçoit le SHA Git attendu et exige son égalité exacte avec chaque JSON.

Une preuve browser est réservée au chemin `kadre/contracts/driver/web/build/contract-evidence/<target>/contract-evidence/browser/<engine>/<contractId>.json`. Son descripteur d'exécution identifie le moteur et sa version, ainsi que le nom et le SHA-256 du bundle public réellement chargé. Les artifacts JS et Wasm restent séparés ; ni leur nom CI, ni leur répertoire, ni l'index agrégé ne permet à l'un d'écraser l'autre.

Le gate de PR exige Chromium pour JS et Wasm. Le profil nightly passe explicitement `-PkadreBrowserEngines=chromium,firefox,webkit` aux mêmes validateurs et exige les trois moteurs pour chaque target. Toute combinaison target/moteur déclarée supportée doit produire une exécution réelle : `skipped`, `ignored` et `NotApplicable` sont interdits et ne peuvent pas être remplacés par une preuve d'un autre target ou moteur.

Les contrats Web restent `planned` tant que ces producteurs n'existent pas : `check` exécute les validateurs JS et Wasm Chromium, mais admet les répertoires absents sans fabriquer mapping, JSON ou résultat de test. Le passage d'un contrat à `active` rend immédiatement obligatoires son mapping et chaque artifact attendu. Le driver Playwright sera ajouté avec le premier attach DOM réel, pas dans cette tranche de fondation.

## 11. Nightly et release

### 11.1 Nightly obligatoire

Le nightly exécute en plus :

- property/model tests élargis et stress threads réels ;
- Chrome, Firefox et WebKit lorsqu’ils supportent la target concernée ;
- versions minimum et courante d’Android/iOS disponibles ;
- variantes de compositor/protocole Linux et modes de fallback ;
- détection de leaks d’owners, handles, buffers et subscriptions natives ;
- mutation testing informatif du common code ;
- benchmarks et régressions de temps/mémoire avec intervalles, hors verdict fonctionnel ;
- scénarios nécessitant permissions, secure context ou matériel accessible au pool nightly.

Un nightly rouge ouvre un incident visible et bloque une release, mais ne réécrit pas rétroactivement le résultat d’une PR déjà mergée.

### 11.2 Gate de release

Une release d’incubation exige :

- dernier gate PR vert sur le commit exact ;
- dernier nightly complet vert depuis moins de 24 heures ;
- consumer compile tests sur tous les artefacts publiés ;
- aucun `contractId` actif obligatoire absent, aucune sentinelle active survivante et aucun test quarantined ;
- registres de capabilities complets pour les environnements déclarés supportés ;
- validation sur device physique pour toute capability officiellement promise mais non exerçable sur simulateur, ou retrait explicite de cette promesse.

## 12. Flakiness, skips et quarantaine

Un test obligatoire flaky est un bug du produit, du driver ou du test. Il n’est jamais retry pour obtenir un vert. Sa correction conserve la première trace fautive et ajoute, si possible, un scénario déterministe reproduisant la race.

Un skip n’est admis que dans une suite non bloquante d’exploration. Dans le gate PR, une absence fonctionnelle s’exprime par la capability et l’outcome `Unsupported`/`Unavailable` attendu ; une infrastructure incapable d’exécuter le job fait échouer le job.

La quarantaine ne consiste pas à ignorer un testcase. Elle exige soit de retirer temporairement l’adapter/capability de la liste supportée, soit de déplacer une preuve purement statistique vers nightly tout en conservant sa preuve déterministe PR. Toute quarantaine possède owner, issue et échéance, et bloque la release tant qu’elle existe.

## 13. Coverage et maintenance

Les métriques bloquantes sont :

- 100 % des `contractId/scenarioId/target` actifs obligatoires exécutés ;
- 100 % des sentinelles architecturales actives tuées ;
- 100 % des surfaces interop promises compilées depuis un consumer ;
- zéro skip, failure masquée, residual de migration ou export public inattendu.

Line, branch et mutation coverage général restent informatifs et servent à découvrir un risque non enregistré. Ils ne déclenchent pas l’ajout mécanique de tests. Un code non couvert mène d’abord à la question « quel contrat ou risque manque ? » ; sans réponse, il peut s’agir de code mort à supprimer plutôt que d’un test à écrire.

Lorsqu’un contrat change pendant l’incubation :

1. modifier la spec normative ;
2. modifier le registre et ses sentinelles ;
3. écrire/adapter le scénario qui échoue ;
4. modifier l’implémentation ;
5. supprimer les tests de l’ancien contrat devenus sans objet ;
6. mettre à jour les fixtures consommatrices et les preuves attendues.

## 14. Gate de fermeture de la stratégie

- [x] Les tests tautologiques sont distingués des preuves structurelles utiles.
- [x] Chaque comportement public possède une route vers un oracle indépendant.
- [x] Fake, adapters réels et interop ont des responsabilités distinctes.
- [x] Races, budgets, backpressure, capabilities et failures fermées sont couverts.
- [x] Les tests visuels de renderer ne sont pas confondus avec le rôle de Kadre.
- [x] Le gate PR possède un SLO initial mesurable de 10 minutes et un timeout ajustable revu.
- [x] Nightly et release couvrent ce qui ne peut pas rester déterministe ou disponible en PR.
- [x] Skips, retries et coverage arbitraire ne peuvent pas fabriquer un vert.
- [x] La preuve CI indique quels contrats ont réellement été exécutés.
