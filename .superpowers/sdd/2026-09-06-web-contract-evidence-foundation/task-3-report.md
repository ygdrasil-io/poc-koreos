# Task 3 — Validation des artifacts de preuve avant agrégation

## Statut

Implémentation terminée dans le périmètre de la tâche 3. Le lecteur strict, le validateur d’artifacts, la CLI et les index JVM/browser sont couverts par tests. Aucun module Web, source set JS/Wasm, runner browser ou producteur d’artifact browser n’a été ajouté.

La vérification `:kadre:contracts:validator:check` reste bloquée après les tests par les appels Gradle existants à sept arguments de `GenerateContractEvidence`. Leur migration vers la CLI à huit arguments et vers un SHA Git réel est explicitement assignée à la tâche 4.

## Changements

### Schéma canonique 1

- Normalisation de la preuve JVM de `execution: "junit"` vers `execution: { "kind": "junit" }`.
- Réservation et validation stricte de la forme browser :

  ```json
  {
    "kind": "browser",
    "engine": "chromium",
    "version": "140.0",
    "bundleName": "kadre-js.js",
    "bundleSha256": "<64 hex>"
  }
  ```

- Lecture stricte du JSON : champs obligatoires et inconnus, types primitifs, `schemaVersion == 1`, environnement non vide, durée non négative, forme des capabilities, scénarios, sentinelles et compteurs JUnit.
- Validation exacte du SHA Git attendu, y compris sa syntaxe, sans accepter `local` ni un SHA valide mais obsolète.
- Validation des résultats `Passed`, des sentinelles `Killed`, de l’oracle du contrat et de l’égalité exacte des ensembles de scénarios/sentinelles via `validateTargetMappings`.
- Conservation du JSON validé avec les métadonnées typées dans `ValidatedContractEvidence` pour l’agrégation future.

### Validation et indexation

- Ajout de `validateContractEvidence(...)` avec : registre, liste de mappings, SHA attendu, target du job, exécution attendue, IDs du gate et répertoires d’artifacts.
- Un contrat `active` du gate et du target exige exactement un artifact :
  - JVM/JUnit : `contract-evidence/<contractId>.json` ;
  - browser : `contract-evidence/browser/<engine>/<contractId>.json` pour chaque engine attendu.
- Un contrat `planned` du même gate ne requiert ni mapping target ni artifact ; son passage futur à `active` rend immédiatement leur absence bloquante.
- Les doublons sont refusés par `(target, contractId)` en JVM et `(target, contractId, engine)` en browser, y compris lorsque leurs bundles ont des SHA-256 différents.
- Index JVM : `target to contractId`.
- Index browser : `target to contractId to engine to bundleSha256`.
- Ajout d’une CLI à sept arguments de validation : registre, mappings, SHA attendu, target, `junit` ou liste d’engines, IDs du gate, répertoires d’artifacts.

## TDD — RED / GREEN

### Cycle 1 — lecteur et validation d’artifacts

- RED observé avec `ValidateContractEvidenceTest` : compilation impossible car `ExpectedContractExecutions`, `ContractEvidenceIndex` et `validateContractEvidence(...)` n’existaient pas.
- Après correction de deux erreurs purement locales de fixture, le RED propre ne contenait plus que les références à l’API de production manquante.
- GREEN : 12 tests du premier oracle complet passaient après l’implémentation minimale.

### Cycle 2 — frontière CLI

- RED observé : compilation impossible sur `validateContractEvidenceCli`, volontairement demandé par le nouveau test de frontière.
- GREEN : ajout du wrapper CLI et délégation du `main`; 13 tests passaient.

### Cycle 3 — correctif de review sur le target du gate

- Finding critique vérifié : le filtre combiné `status == Active && target in requiredTargets` supprimait silencieusement un contrat actif lorsque le job fournissait le mauvais target, et renvoyait un index vide.
- RED observé avec `activeGateRejectsAJobTargetNotRequiredByTheContract` : le validateur terminait sans exception avec `ContractEvidenceIndex(junit={}, browser={})` pour `INT-002[js]`, alors que le contrat ne requiert que `jvm`.
- GREEN : le filtre conserve désormais tous les contrats `active`, puis rejette explicitement `${contractId}[${target}]: target is not required` avant toute sélection de mapping, recherche d’artifact ou validation d’exécution.
- La règle distincte des contrats `planned` reste inchangée : ils sont toujours ignorés sans exiger d’artifact.

Les tests exercent des objets réels et des fichiers temporaires, sans mock. Les valeurs attendues du JSON et des TSV sont des fixtures littérales indépendantes du lecteur.

## Couverture de l’oracle

- fixture JVM canonique et index `(jvm, INT-002)` ;
- schéma incorrect, ancienne forme scalaire de `execution` et champ inconnu ;
- commit d’artifact invalide, commit différent du SHA attendu et SHA attendu invalide ;
- target différent de celui du job ;
- target d’invocation du gate absent des `requiredTargets` d’un contrat actif ;
- `contractId` de scénario incorrect, scénario absent/inconnu, sentinelle absente/inconnue ;
- `tests == 0`, skipped, failures et errors non nuls ;
- artifact JVM absent ;
- doublon browser pour `(target, contractId, engine)` ;
- Chromium attendu absent, engine déclaré différent et descripteur bundle incomplet ;
- contrat Web synthétique `active` couvrant `js` et `wasmJs`, mappings/sentinelles par target, Chromium et Firefox ;
- index browser par target, contrat, engine et SHA-256 du bundle ;
- contrat `planned` accepté sans artifact ;
- O1 produit uniquement depuis le testcase du consumer mappé ; un rapport non associé est rejeté avant émission de la preuve ;
- CLI validée pour `junit` et une liste `chromium,firefox`.

## Vérifications

- `rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ValidateContractEvidenceTest --console=plain`
  - succès après correctif ; 14 tests, 0 skipped, 0 failure, 0 error.
- `rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ContractEvidenceTest --console=plain`
  - succès ; 12 tests.
- `rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.GenerateContractEvidenceTest --console=plain`
  - succès ; 6 tests.
- `rtk ./gradlew :kadre:contracts:validator:jvmTest --rerun-tasks --console=plain`
  - succès après correctif ; 50 tests au total, 0 skipped, 0 failure, 0 error, 14 tâches exécutées.
- `rtk ./gradlew :kadre:contracts:validator:check --console=plain`
  - les tests du validateur passent ; échec ensuite dans `generateRuntimeINP001ContractEvidence` avec le message de la CLI de génération exigeant huit arguments.
- `rtk git diff --check`
  - succès, aucune erreur whitespace.

## Self-review

- Chaque fichier est résolu uniquement sous le chemin JVM ou browser imposé ; aucun scan permissif ne peut substituer un artifact mal placé.
- Le commit est vérifié syntaxiquement puis comparé par égalité exacte au SHA attendu.
- Un engine browser ne peut pas être remplacé par la métadonnée interne de l’artifact : le chemin attendu et le champ `execution.engine` doivent tous deux correspondre.
- Un descripteur browser incomplet ou un `bundleSha256` non SHA-256 est rejeté avant indexation.
- Les scénarios/sentinelles déclarés par le JSON et les mappings du target sont chacun validés contre le même contrat, ce qui empêche une preuve auto-déclarée de remplacer le mapping canonique.
- La preuve O1 reste adossée au testcase consumer canonique au moment de la génération ; le validateur d’artifact exige ensuite le schéma JUnit sans skipped/failure/error et la couverture mappée exacte.
- Les contrats `planned` ne provoquent aucune fabrication de mapping ou d’artifact.
- Le diff ne contient ni changement Gradle, ni source set JS/Wasm, ni dépendance ou runner browser.

## Préoccupation restante

La commande `check` ne pourra être entièrement verte avant la tâche 4 : `kadre/contracts/validator/build.gradle.kts` fournit encore sept arguments aux tâches `GenerateContractEvidence` et conserve le commit par défaut `local`. Ce fichier est volontairement hors du périmètre de la tâche 3 et le ledger assigne précisément ces deux migrations à la tâche suivante.

Finding mineur différé lors de la ronde de review : le lecteur vérifie l’égalité exacte des ensembles et rejette les doublons, mais n’impose pas encore l’ordre canonique des tableaux JSON `scenarios` et `sentinels`. Aucun changement n’est apporté à cet ordre dans ce correctif ciblé.

## Commit prévu

`feat(contracts): validate target-scoped evidence artifacts`
