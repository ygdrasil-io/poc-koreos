# Task 4 — Migration et gate des preuves JVM

## Statut

Implémentation terminée dans le périmètre de la tâche 4. Les mappings runtime et AppKit réels utilisent désormais le schéma à six colonnes avec `target=jvm`. Les tâches Gradle génèrent puis valident leurs artifacts contre le target, le SHA Git attendu et la couverture du gate explicite. Le `check` portable, la génération runtime et la génération AppKit sont verts sur cette machine macOS.

Aucun module Web, source set JS/Wasm, contrat Web, runner Playwright, tâche browser ou artifact browser n’a été ajouté.

## Changements

### Mappings JVM

- Migration de `kadre/runtime/contracts/evidence.tsv` et `kadre/backend/appkit/contracts/evidence.tsv` vers `contractId, target, kind, evidenceId, testClass, testName`.
- Insertion de `jvm` sur chaque ligne existante.
- Vérification par hash après suppression de la nouvelle colonne : les cinq colonnes historiques sont strictement identiques à `HEAD` avant migration.
- Toutes les lignes des deux fichiers ont exactement six colonnes.

### Gate explicite du registre

- Suppression de l’inférence par préfixe `APK-`, `INP-` ou `WIN-` dans `ValidateContractRegistry`.
- Un contrat `active` explicitement présent dans le gate exige ses mappings complets.
- Un contrat `active` hors gate est admis s’il ne possède aucun mapping ; toute ligne de mapping qui lui est attachée est rejetée comme hors gate.
- Un contrat `planned` explicitement présent dans le gate est admis sans mapping.
- Le même contrat passé à `active` devient immédiatement bloquant si ses scénarios ou sentinelles ne sont pas couverts.
- Un gate inconnu reste rejeté et un gate pointant vers un contrat `retired` reste invalide.

### Gradle et artifacts JVM

- Centralisation des 20 IDs JVM déjà produits dans `contractEvidenceGateIds`, utilisée par la validation du registre puis partitionnée pour les producteurs runtime et AppKit.
- Remplacement de `local` par le résultat réel de `git rev-parse HEAD` comme SHA par défaut.
- Conservation de `-PkadreContractCommit=...` comme override, avec rejet immédiat de toute valeur qui n’est pas un SHA Git de 40 ou 64 caractères hexadécimaux.
- Utilisation du même provider de SHA par la génération et la validation.
- Passage de `jvm` comme septième donnée de domaine et huitième argument CLI à tous les générateurs runtime et AppKit.
- Conservation de `build/contract-evidence/<contractId>.json`.
- Ajout de `validateRuntimeContractEvidence` et `validateAppKitContractEvidence`, exécutées après leurs générateurs et configurées pour `target=jvm`, `execution=junit`, leurs IDs de gate et leur répertoire d’artifacts respectif.
- `generateRuntimeContractEvidence` et `generateAppKitContractEvidence` agrègent désormais génération puis validation.
- `check` conserve le gate portable du registre et des preuves runtime ; AppKit reste uniquement derrière sa commande dédiée et ses tests natifs macOS.

## TDD — RED / GREEN

### Cycle 1 — configuration explicite du gate

- RED observé sur quatre tests de `ContractRegistryTest` :
  - un contrat `APK-*` actif sans mapping était encore implicitement exigé dans le gate ;
  - un mapping actif `SES-*` hors gate était ignoré ;
  - un contrat `planned` déclaré dans le gate était rejeté ;
  - son passage à `active` sans mapping n’était pas bloquant.
- Commande RED : `rtk ./gradlew :kadre:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ContractRegistryTest --console=plain`.
- Résultat RED : 14 tests exécutés, 4 échecs attendus.
- GREEN après suppression de l’inférence et validation par appartenance explicite : 14 tests exécutés, 0 échec.
- Un test historique fondé sur l’ancienne inférence a été renommé et aligné sur la nouvelle règle explicite.

### Cycle 2 — appel réel du générateur

- Après migration des TSV mais avant correction Gradle, `generateRuntimeContractEvidence` a atteint `GenerateContractEvidenceKt` et échoué avec `expected registry, mapping, JUnit directories, output, commit, contractId, target and adapter arguments` : le build ne fournissait que sept arguments.
- GREEN après câblage du target et de la validation : les huit artifacts runtime ont été générés, puis `validateRuntimeContractEvidence` a réussi.

Les tests utilisent le validateur réel et des fichiers temporaires, sans mock.

## Vérifications

- `rtk ./gradlew :kadre:contracts:validator:check --console=plain`
  - succès ; tests du validateur, registre, génération des huit preuves runtime et validation de l’agrégat runtime.
- `rtk ./gradlew :kadre:contracts:validator:generateRuntimeContractEvidence --console=plain`
  - succès ; `validateRuntimeContractEvidence` est exécutée.
- `rtk ./gradlew :kadre:contracts:validator:generateAppKitContractEvidence --console=plain`
  - succès sur macOS ; tests natifs AppKit, douze générations et `validateAppKitContractEvidence`.
- `rtk ./gradlew :kadre:contracts:validator:generateRuntimeContractEvidence -PkadreContractCommit=0123456789abcdef0123456789abcdef01234567 --console=plain`
  - succès ; l’artifact vérifié porte exactement cette override et `target=jvm`, ce qui prouve sa transmission à la génération puis à la validation.
- `rtk ./gradlew :kadre:contracts:validator:tasks -PkadreContractCommit=local --console=plain`
  - échec attendu avant exécution des générateurs avec `kadreContractCommit must be a 40- or 64-character Git SHA`.
- Inspection de `INP-001.json` après génération par défaut : son commit est exactement le résultat de `git rev-parse HEAD`, son target est `jvm` et son exécution est `junit`.
- `rtk git diff --check`
  - succès, aucune erreur whitespace.

## Self-review

- Le gate est défini par une seule liste explicite ; les préfixes ne décident plus si un contrat est gated dans le validateur.
- Les partitions `APK-*` et runtime JVM ne servent qu’à router des IDs déjà explicitement présents vers leur producteur ; elles ne peuvent introduire un contrat dans le gate. Une garde de configuration refuse tout ID explicite sans producteur assigné.
- Les validateurs consomment les répertoires parents de `contract-evidence`, conformément à la résolution stricte `contract-evidence/<contractId>.json`.
- Le SHA et le target sont des inputs Gradle de chaque générateur et validateur, empêchant la réutilisation silencieuse d’un artifact produit pour un autre commit ou target.
- Les tâches AppKit ne sont pas ajoutées au `check` portable.
- Les artifacts de build ne sont pas suivis par Git.
- La recherche de portée ne révèle aucun ajout JS, Wasm, Playwright ou browser dans les fichiers modifiés ; le seul fixture Web visible dans `ContractRegistryTest` préexistait à cette tâche.

## Préoccupations restantes

Aucune préoccupation bloquante pour la tâche 4. La tâche 5 devra étendre la liste centrale et classifier explicitement ses gates Web avec leurs validations réservées.

## Commit prévu

`build(contracts): gate existing evidence by target`
