# Task 5 — Registre et gates browser Web planifiés

## Statut

Implémentation terminée dans le périmètre de la tâche 5. Les contrats `BCK-001`, `INT-002`, `INT-003` et `INT-004` sont enregistrés exactement au statut `planned`, avec leurs oracles, targets ordonnés `js,wasmJs`, scénarios et sentinelles normatifs. Le gate réserve deux validations browser, JS et Wasm, sans créer de backend Web, module, source set, driver, fichier Node, test DOM ou artifact browser.

Les validateurs attendent Chromium par défaut et acceptent les répertoires réservés absents tant que les contrats restent `planned`. Le profil `-PkadreBrowserEngines=chromium,firefox,webkit` étend la même validation au nightly. Une mutation vers `active` échoue sans mappings complets sur chaque target, puis échoue sans JSON browser pour chaque moteur attendu.

## Changements

### Registre et complétude du gate

- Ajout mot pour mot des quatre lignes de la section « Registre et scénarios » de la specification temporaire.
- Conservation du scenario target-neutre unique `web-typescript-consumer` pour `INT-002` sur JS et Wasm.
- Extension du gate explicite Gradle avec les quatre IDs Web.
- Classification séparée de `webContractIds` afin qu’aucun contrat Web ne soit routé vers un producteur JVM.
- Validation target-based : tout contrat non retiré déclarant `js` ou `wasmJs` doit appartenir au gate explicite. Aucun préfixe de contrat n’est utilisé pour inférer le gate.

### Validateurs browser réservés

- Ajout de `validateJsBrowserContractEvidence` et `validateWasmJsBrowserContractEvidence` comme dépendances de `check`.
- Répertoires de jobs réservés :
  - `kadre/contracts/driver/web/build/contract-evidence/js`
  - `kadre/contracts/driver/web/build/contract-evidence/wasmJs`
- Résolution stricte des preuves futures par le validateur sous `contract-evidence/browser/<engine>/<contractId>.json`, soit le chemin complet `kadre/contracts/driver/web/build/contract-evidence/<target>/contract-evidence/browser/<engine>/<contractId>.json`.
- Chromium par défaut ; override nightly explicite `chromium,firefox,webkit`.
- Déclaration Gradle des artifacts par `fileTree`, qui reste vide quand le répertoire planifié n’existe pas et suit les fichiers dès qu’un producteur apparaît.

### Documentation normative

- Audit de `DESIGN.md`, `INTEROP-EXPORTS.md`, `BACKEND-CAPABILITIES.md` et `OPERATION-CONTRACTS.md` contre les scénarios du registre.
- Les documents couvrent : absence de DOM implicite, `WindowManager.primary == null`, owner exclusif d’un `HTMLElement`, `Busy(Host)` sur attach/provider concurrent, policies `Manual` et `StopWhenDetached`, observation `Document`/`ShadowRoot`, changement de document et `pagehide` terminaux, provider adoptant un host précréé dans un browsing context distinct, et `withWebElement` borné par une lease.
- Correction d’une seule lacune constatée dans `DESIGN.md` : `InteractionAction.OpenWindow` est désormais explicitement `Unsupported` sur Web ; popup, iframe et browsing context restent host-owned.
- Ajout à `TEST-STRATEGY.md` du protocole public JS/Wasm : même fixture TypeScript, JUnit et JSON par target, SHA Git exact, artifact CI étiqueté, identité moteur/version et bundle/SHA-256, Chromium en PR, Chromium/Firefox/WebKit en nightly, et interdiction de `skipped`/`ignored`/`NotApplicable` pour une combinaison supportée.
- Le driver Playwright reste reporté au premier attach DOM réel.

## TDD — RED / GREEN

### Cycle 1 — registre réel

- RED : `ContractRegistryTest` chargeait le vrai TSV ; 19 tests exécutés, 5 échecs attendus dus aux quatre contrats absents.
- GREEN partiel après ajout exact des lignes TSV : 18 tests passaient ; seul le test de complétude du gate restait rouge.

### Cycle 2 — gate explicite

- RED : le validateur ne signalait pas `INT-004` retiré artificiellement du set de gate.
- GREEN : ajout de la règle browser target-based ; 19 tests passaient.
- RED d’intégration : `validateContractRegistry` réel rejetait alors les quatre IDs absents de la configuration Gradle.
- GREEN : ajout des quatre IDs et classification Web séparée.

### Cycle 3 — tâches browser planifiées

- RED d’intégration : `validateJsBrowserContractEvidence` était absente.
- Premier RED de wiring : `inputs.dir` rendait le futur répertoire obligatoire avant l’exécution Java. `optional()` ne convenait pas, car une valeur de chemin était toujours renseignée.
- GREEN : `fileTree(artifactDirectory)` représente un input vide quand le répertoire est absent, sans le créer, et suit récursivement ses futurs artifacts.
- GREEN : les tâches JS et Wasm passent sans répertoire en mode PR et avec la liste nightly explicite.

### Matrice d’activation synthétique

- `BCK-001` muté de `planned` à `active` sans mapping : rejet sur JS et Wasm avec le contractId, le target et le premier scénario absent.
- `INT-002` muté à `active` avec mapping complet target-neutre mais sans JSON : rejet sur JS et Wasm pour l’artifact Chromium absent.
- Tous les tests utilisent le registre/validateur réels et des fichiers temporaires, sans mock.

## Vérifications

- `rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ContractRegistryTest --rerun-tasks --console=plain`
  - succès ; 19 tests, 0 skipped, 0 failure, 0 error.
- `rtk ./gradlew :kadre:contracts:validator:check --console=plain`
  - succès ; 59 tests du validateur, 0 skipped, 0 failure, 0 error ; génération/validation des huit preuves runtime ; validation du registre ; validateurs browser JS et Wasm exécutés.
- `rtk ./gradlew :kadre:contracts:validator:validateJsBrowserContractEvidence :kadre:contracts:validator:validateWasmJsBrowserContractEvidence -PkadreBrowserEngines=chromium,firefox,webkit --console=plain`
  - succès avec les contrats `planned` et les répertoires absents.
- Les deux TSV de mapping ont l’en-tête six colonnes et toutes leurs lignes déclarent `jvm`.
- Les huit JSON runtime générés portent exactement le SHA de HEAD utilisé par la vérification et `target = "jvm"`.
- Le diff de branche contre `origin/master` n’ajoute aucun chemin de module Web, source set `jsMain`/`wasmJsMain`, driver Web, fichier Node, test DOM ou artifact browser. Aucun changement de dépendance browser n’est présent.
- Les documents temporaires de design et de plan sont conservés comme demandé ; ils devront être retirés seulement avant la première PR Web fonctionnelle.

## Self-review

- Les lignes TSV et les attentes de test utilisent des valeurs littérales indépendantes.
- `INT-002` ne possède aucun ID suffixé par target.
- Les gates browser partagent la liste exacte des quatre IDs, mais restent exclus des producteurs JVM.
- Le chemin transmis à la CLI est le parent target-specific attendu par la résolution interne `contract-evidence/browser/...`.
- Les répertoires absents ne sont ni créés ni déclarés comme outputs.
- L’activation est bloquée d’abord par le mapping manquant, puis par chaque artifact moteur manquant lorsque le mapping existe.
- Aucun document temporaire n’a été supprimé et aucune promesse de support Web n’a été activée.

## Préoccupations restantes

Aucune préoccupation bloquante. La première tranche DOM réelle devra ajouter le mapping Web aux inputs des deux validateurs et produire les artifacts aux chemins déjà réservés ; cette tranche devra aussi introduire Playwright, puis seulement passer les contrats concernés à `active`.

## Commit

`docs(web): register planned browser contract gates`
