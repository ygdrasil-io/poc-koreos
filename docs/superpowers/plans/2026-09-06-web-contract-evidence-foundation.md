# Web Contract Evidence Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduire les contrats Web planifiés et rendre la chaîne de preuves contractuelles indépendante du target, afin que la future tranche DOM ne puisse activer un contrat Web sans preuve JS et Wasm réelle.

**Architecture:** Le registre reste l’autorité sur le statut, les scénarios, les sentinelles et les targets requis. Les mappings décrivent maintenant une preuve pour un couple `(contractId, target)` ; le validateur vérifie le produit de chaque contrat activé par un gate explicite. Chaque job génère son JSON canonique à partir de JUnit, puis un validateur d’artifact refuse une preuve absente, mal formée, attribuée au mauvais target ou non corrélée au mapping. Aucun module KMP Web, SDK DOM, runner Node ou harness navigateur n’est créé dans cette tranche.

**Tech Stack:** Kotlin Multiplatform JVM, Gradle Kotlin DSL, JUnit XML, kotlinx.serialization JSON, TSV.

**Spec:** `docs/superpowers/specs/2026-09-06-web-foundation-design.md`, `kadre/DESIGN.md#15.3`, `kadre/TEST-STRATEGY.md`, `kadre/INTEROP-EXPORTS.md#6-7`.

## Global Constraints

- Les quatre contrats Web restent `planned` : cette tranche n’introduit ni `platform:web`, ni artifact JS/Wasm, ni Playwright, ni test simulant le DOM.
- Une preuve est valide uniquement pour le target Kotlin qu’elle déclare. Le chemin d’un job reste `contract-evidence/<contractId>.json`, mais l’artifact CI est isolé et étiqueté par target ; l’agrégateur l’indexe donc par `(target, contractId)`.
- Un contrat O1 est admissible lorsque son consumer compile produit un rapport JUnit canonique ; les O2/O3 exigent toujours scénarios et sentinelles complets.
- Les contrats existants continuent de produire exactement une preuve JVM. La migration du format TSV est atomique : parser, fixtures, mappings et tâches Gradle évoluent ensemble.
- Ne pas étendre le scope à la migration historique des identifiants `APK-*`, ni aux targets JS/Wasm de `foundation` ou `runtime`.
- Le document de conception et ce plan sont temporaires : les retirer avant la première PR fonctionnelle qui ajoute du code Web de production.

---

## Découpage de review

La branche peut être présentée en deux PRs stackées :

1. **Contract evidence target-aware** — tâches 1 à 3 ; migration compatible de la preuve JVM et validation d’artifact.
2. **Web planned contract registry** — tâches 4 et 5 ; contrats Web normatifs, gate déclaré mais inactif, et documentation de l’exploitation CI future.

Les deux PRs restent entièrement JVM/outil de contrat. La seconde dépend de la première parce que son gate `js,wasmJs` doit être interprété par le validateur target-aware.

## Task 1: Rendre les mappings target-aware, en TDD

**Files:**

- Modify: `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ContractEvidenceMapping.kt`
- Modify: `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ContractEvidenceMappingTest.kt`
- Modify: `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ContractRegistryTest.kt`

- [ ] Écrire d’abord les cas rouges dans `ContractEvidenceMappingTest` pour :
  - un contrat `active` `requiredTargets = ["js", "wasmJs"]` couvert intégralement par target ;
  - un scénario ou une sentinelle absent sur un seul target ;
  - un target de mapping qui n’est pas déclaré par le contrat ;
  - un doublon `(target, kind, evidenceId)` ;
  - la compatibilité de l’erreur `unknown contractId`.

  Employer des fixtures minimales, par exemple :

  ```kotlin
  val contract = webContract(requiredTargets = listOf("js", "wasmJs"))
  val errors = validateMappings(contract, jsOnlyMappings)
  assertContains(errors.joinToString(), "BCK-001[js]: missing scenario: web-attach-connected")
  ```

- [ ] Étendre `EvidenceMapping` avec `target: String` et remplacer l’en-tête TSV par :

  ```text
  contractId\ttarget\tkind\tevidenceId\ttestClass\ttestName
  ```

  Le parser refuse six colonnes incomplètes, un target blanc et les kinds inconnus. Il ne déduit jamais le target à partir du nom de test.

- [ ] Remplacer la validation mono-target par deux niveaux explicites :
  - `validateMappings(contract, mappings)` vérifie chaque `requiredTarget` et chaque scénario/sentinelle exactement une fois ;
  - `validateTargetMappings(contract, target, mappings)` vérifie le sous-ensemble nécessaire à la génération d’une preuve de ce target.

  Les messages doivent contenir contract et target (`BCK-001[wasmJs]`) afin que l’échec CI soit actionnable. Un mapping vers un target non requis est une erreur ; un contrat `planned` ne requiert aucun mapping tant qu’il n’est pas activé par un gate.

- [ ] Mettre à jour les fixtures de `ContractRegistryTest` pour le nouvel en-tête et ajouter un test de non-régression : un contrat `planned` dont les targets Web sont déclarés passe la validation structurelle sans mapping.

- [ ] Exécuter :

  ```bash
  rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ContractEvidenceMappingTest --console=plain
  rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ContractRegistryTest --console=plain
  ```

- [ ] Commit :

  ```text
  test(contracts): require target-specific evidence mappings
  ```

## Task 2: Produire un JSON canonique pour le target réellement testé

**Files:**

- Modify: `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ContractEvidence.kt`
- Modify: `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/GenerateContractEvidence.kt`
- Modify: `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ContractEvidenceTest.kt`
- Modify: `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/GenerateContractEvidenceTest.kt`

- [ ] Ajouter les tests rouges qui prouvent que :
  - le JSON porte le target explicite reçu (`jvm`, puis une fixture `js`) au lieu de `jvm` codé en dur ;
  - une demande pour un target absent de `requiredTargets` échoue ;
  - seules les entrées de mapping du target courant sont exigées dans le JUnit lu par le job ;
  - un contrat O1 actif peut produire une preuve si le consumer JUnit mappé passe ;
  - un commit vide ou qui ne correspond pas à un SHA Git hexadécimal est rejeté.

- [ ] Ajouter `target` au contrat de `ContractEvidence.create` et aux deux overloads de `generateContractEvidence`. Valider `target in contract.requiredTargets`, appeler `validateTargetMappings`, filtrer les test cases par target, et écrire la valeur validée dans `target` du JSON.

- [ ] Faire passer la CLI de sept à huit arguments :

  ```text
  registry, mapping, JUnit directories, output, commit, contractId, target, adapter
  ```

  Mettre à jour le message d’usage, toutes les fixtures et les appels directs. Ne pas conserver un défaut JVM silencieux.

- [ ] Autoriser O1, O2 et O3 dans `ContractEvidence.create`, car le rapport JUnit est l’oracle canonique de la preuve ; continuer à refuser un rapport sans test, skipped, failure ou error.

- [ ] Garder l’écriture atomique et vérifier par test que tout échec de validation conserve l’ancien fichier intact.

- [ ] Exécuter :

  ```bash
  rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ContractEvidenceTest --console=plain
  rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.GenerateContractEvidenceTest --console=plain
  ```

- [ ] Commit :

  ```text
  feat(contracts): emit target-specific contract evidence
  ```

## Task 3: Valider les artifacts de preuve avant agrégation

**Files:**

- Add: `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ValidateContractEvidence.kt`
- Modify: `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ContractEvidence.kt`
- Add: `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ValidateContractEvidenceTest.kt`

- [ ] Commencer par une fixture JSON canonique et les cas rouges suivants : schemaVersion incorrect, commit invalide, target différent de celui du job, contract absent/mauvais dans les scénarios, sentinelle manquante, compteurs non nuls, fichier absent, et deux artifacts pour le même `(target, contractId)`.

- [ ] Extraire depuis `ContractEvidence` un lecteur/validateur strict du schema 1. Il vérifie les champs produits par le générateur, le commit, l’environnement, les comptes JUnit, et l’égalité exacte des ensembles scénario/sentinelle avec `validateTargetMappings`. Aucun champ inconnu ne remplace une valeur obligatoire ; une preuve ne se déclare pas elle-même réussie sans JUnit canonique associé par mapping.

- [ ] Créer `validateContractEvidence(...)` et sa CLI. Elle reçoit le registre, les mappings, un target, la liste des contrats du gate, et un ou plusieurs répertoires d’artifacts de job. Pour chaque contrat **active** du gate, elle exige exactement un `contract-evidence/<contractId>.json` portant ce target. Les contrats `planned` du même gate sont ignorés ; leur activation future rend immédiatement l’absence de fichier bloquante.

- [ ] Exposer un index en mémoire dont la clé est `target to contractId`. Ainsi, l’agrégateur futur fusionnera les artifacts JS et Wasm sans collision de chemin. Cette tranche ne copie ni ne fusionne encore d’artifacts de jobs distants.

- [ ] Tester l’oracle complet : un contrat Web synthétique `active` avec `js,wasmJs` échoue tant que l’un des deux artifacts/mappings/sentinelles manque, puis passe seulement avec une preuve valide pour chaque target. Vérifier aussi qu’O1 ne passe qu’avec le rapport consumer attendu.

- [ ] Exécuter :

  ```bash
  rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ValidateContractEvidenceTest --console=plain
  rtk ./gradlew :kadre:contracts:validator:check --console=plain
  ```

- [ ] Commit :

  ```text
  feat(contracts): validate target-scoped evidence artifacts
  ```

## Task 4: Migrer les preuves JVM et les tâches Gradle sans relâcher le gate existant

**Files:**

- Modify: `kadre/runtime/contracts/evidence.tsv`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`
- Modify: `kadre/contracts/validator/build.gradle.kts`
- Modify: `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ContractRegistryTest.kt`

- [ ] Mettre à jour les deux TSV existants vers l’en-tête à six colonnes et renseigner `jvm` pour chaque ligne. Ne modifier ni `contractId`, ni `evidenceId`, ni le test associé.

- [ ] Introduire une liste unique de gate IDs dans `build.gradle.kts`, incluant les contrats runtime et AppKit déjà générés. Le validateur de registre ne déduit plus un gate d’un préfixe (`APK-`, `INP-`, `WIN-`) : il valide seulement les IDs explicitement configurés. Une ligne de mapping active hors gate est toujours rejetée.

- [ ] Passer `jvm` comme septième donnée de domaine (huitième argument CLI) aux tâches `generateRuntime…ContractEvidence` et `generateAppKit…ContractEvidence`. Conserver le chemin d’output `build/contract-evidence/<contractId>.json`, car le target est désormais le domaine isolant de l’artifact CI, pas un suffixe local arbitraire.

- [ ] Après les tâches génératrices, ajouter les tâches d’agrégation/validation JVM correspondantes. Elles consomment les JSON produits et échouent si le commit, le target ou la couverture ne correspond plus. Employer par défaut le SHA réel de `git rev-parse HEAD`, avec une propriété Gradle qui ne peut le remplacer que par un SHA valide ; ne plus utiliser `local` comme valeur de preuve.

- [ ] Préserver les contraintes de plateforme : le `check` portable vérifie le registre et les preuves runtime JVM ; la génération AppKit demeure sous son job macOS dédié mais exécute sa propre validation d’artifact.

- [ ] Exécuter :

  ```bash
  rtk ./gradlew :kadre:contracts:validator:check --console=plain
  rtk ./gradlew :kadre:contracts:validator:generateRuntimeContractEvidence --console=plain
  rtk ./gradlew :kadre:contracts:validator:generateAppKitContractEvidence --console=plain
  ```

  Sur une machine sans environnement AppKit, ne présenter le dernier appel comme réussi que s’il l’est réellement ; sinon exécuter les tests JVM du validateur, documenter la limite macOS et laisser le job AppKit CI être la preuve finale.

- [ ] Commit :

  ```text
  build(contracts): gate existing evidence by target
  ```

## Task 5: Enregistrer la frontière Web sans activer un backend inexistant

**Files:**

- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/contracts/validator/build.gradle.kts`
- Modify: `kadre/TEST-STRATEGY.md`
- Modify: `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ContractRegistryTest.kt`

- [ ] Écrire les tests structurels rouges qui chargent le vrai registre et recherchent exactement `BCK-001`, `INT-002`, `INT-003` et `INT-004` avec : statut `planned`, oracle, liste ordonnée des targets `js,wasmJs`, scénarios et sentinelles de la spécification. Vérifier que les quatre IDs sont déclarés dans le gate Gradle mais que leur statut `planned` n’exige ni mapping ni JSON.

- [ ] Ajouter les quatre lignes TSV normatives, mot pour mot depuis la section « Registre et scénarios » de la spec. Insérer les lignes dans les familles `BCK` et `INT` existantes sans renommer les contrats historiques.

- [ ] Ajouter les quatre IDs à la configuration de gate Gradle. La règle attendue est alors : `planned` passe sans artifact ; faire évoluer la ligne vers `active` sans les deux preuves `js` et `wasmJs` échoue au validateur avant toute publication.

- [ ] Relire `DESIGN.md`, `INTEROP-EXPORTS.md`, `BACKEND-CAPABILITIES.md` et `OPERATION-CONTRACTS.md` déjà mis à jour sur cette branche contre les scénarios TSV. Ils doivent couvrir : pas de DOM implicite, `primary == null`, `Manual` et `StopWhenDetached`, observer `Document`/`ShadowRoot`, changement de document terminal, `pagehide` terminal, provider dans un nouveau browsing context, et `withWebElement` borné par une lease. Corriger uniquement une incohérence constatée ; ne pas ajouter d’API, d’exemple compilable qui masquerait l’absence d’implementation, ou de promesse de support Web prématurée.

- [ ] Ajouter à `TEST-STRATEGY.md` le protocole CI : la fixture publique JS et Wasm, JUnit/JSON par target, artefact étiqueté, gate Chromium de PR, navigateur complets nightly, et interdiction de skipped/NotApplicable pour une combinaison déclarée supportée. Conserver explicitement le choix : le driver Playwright arrivera avec le premier attach DOM réel, pas maintenant.

- [ ] Exécuter la vérification complète portable :

  ```bash
  rtk ./gradlew :kadre:contracts:validator:check --console=plain
  rtk git diff --check
  rtk git status --short
  ```

- [ ] Relire le diff contre `origin/master` pour vérifier que ni module Web, ni source set JS/Wasm, ni dépendance browser n’a été introduit. Retirer `docs/superpowers/specs/2026-09-06-web-foundation-design.md` et ce plan avant d’ouvrir la première PR qui contient une implementation Web fonctionnelle ; les documents normatifs sous `kadre/` restent.

- [ ] Commit :

  ```text
  docs(web): register planned browser contract gates
  ```

## Verification finale

- [ ] Exécuter `rtk ./gradlew :kadre:contracts:validator:check --console=plain` depuis un checkout propre.
- [ ] Exécuter les tests unitaires ciblés de toutes les classes modifiées, dont la matrice synthétique JS/Wasm, et vérifier qu’aucun test n’est ignored ou skipped.
- [ ] Vérifier que chaque evidence TSV a le nouvel en-tête, que toutes les lignes existantes déclarent `jvm`, et que tous les JSON générés portent un SHA Git et `target = "jvm"`.
- [ ] Vérifier par test de mutation de fixture qu’un passage artificiel de `BCK-001` à `active` sans preuve JS ou Wasm échoue.
- [ ] Exécuter `rtk git diff --check` et `rtk git status --short` ; ne créer une PR stackée qu’après ces preuves.
