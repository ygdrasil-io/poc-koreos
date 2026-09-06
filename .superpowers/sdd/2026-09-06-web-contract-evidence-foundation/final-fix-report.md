# Correctif final — corrélation JUnit des preuves de contrat

## Statut

Les quatre findings de la revue finale sont corrigés à partir de `efb9b9736b9ef3b9b377483a05d4662f695095d9`. Le validateur n’accepte plus un JSON auto-déclaré sans rapport JUnit corrélé, O4 est refusé à la lecture d’artifact, les tableaux canoniques sont ordonnés et la règle de reparenting inter-document est explicite.

Aucun backend ou module Web, source set JS/Wasm, runner browser, artifact browser réel ni test Gradle/TestKit de wiring browser n’a été ajouté.

## Protocole implémenté

- `validateContractEvidence` reçoit désormais un huitième argument CLI : une liste de chemins JUnit relatifs aux racines d’artifacts.
- La recherche du JSON retourne aussi la racine exacte qui le contient. Le JUnit est résolu exclusivement depuis cette même racine, ce qui empêche un JSON d’un job de se satisfaire d’un rapport situé dans un autre job.
- Les exécutions JVM utilisent les rapports réels déjà produits :
  - runtime : `test-results/jvmTest` ;
  - AppKit : `test-results/jvmTest` et `test-results/appKitStandaloneLoopTest`.
- Les validateurs browser réservés utilisent `test-results/browser/{engine}` sous leur racine déjà target-scopée. Le chemin complet réservé est donc `kadre/contracts/driver/web/build/contract-evidence/<target>/test-results/browser/<engine>/TEST-*.xml`, apparié au JSON `.../<target>/contract-evidence/browser/<engine>/<contractId>.json`.
- Pour chaque contrat `active`, le validateur exige que tous les couples `testClass/testName` du mapping du target existent et soient `Passed` dans le JUnit. Les suites avec skip, failure ou error sont rejetées.
- `tests`, `skipped`, `failures`, `errors` et `durationMillis` du JSON doivent être exactement égaux au résumé recalculé depuis le XML.
- Le SHA attendu, le target, l’engine browser, le nom de bundle et son SHA-256 restent obligatoires et strictement validés.
- Les contrats `planned` sont filtrés avant la recherche de JSON ou la lecture JUnit ; leurs répertoires absents restent acceptés. Une activation synthétique échoue successivement sur mapping, JSON ou JUnit manquant.

## Frontières du schéma

- `ContractEvidence.readAndValidate` applique la même borne O1/O2/O3 que le générateur et refuse O4 avec `requires differential evidence` tant qu’aucun protocole différentiel réel n’existe.
- Les IDs de `scenarios` doivent être triés lexicalement par `scenarioId`.
- Les IDs de `sentinels` doivent être triés lexicalement par `sentinelId`.
- La documentation précise que le JSON ne constitue jamais seul une preuve et documente les chemins JUnit browser réservés.
- `DESIGN.md` dit désormais qu’un reparenting conserve la session uniquement dans le document initial et que tout transfert inter-document la termine.

## TDD — RED

Commande ciblée exécutée après ajout des tests et avant l’implémentation :

```text
rtk ./gradlew :kadre:contracts:validator:jvmTest \
  --tests 'org.graphiks.kadre.contracts.ValidateContractEvidenceTest.activeJvmContractRejectsSelfDeclaredJsonWithoutAssociatedJunit' \
  --tests 'org.graphiks.kadre.contracts.ValidateContractEvidenceTest.activeBrowserContractRejectsSelfDeclaredJsonWithoutEngineJunit' \
  --tests 'org.graphiks.kadre.contracts.ValidateContractEvidenceTest.mappedJunitCasesMustExistAndPassForTheValidatedExecution' \
  --tests 'org.graphiks.kadre.contracts.ValidateContractEvidenceTest.jsonSummaryAndDurationMustEqualAssociatedJunit' \
  --tests 'org.graphiks.kadre.contracts.ValidateContractEvidenceTest.artifactReaderRejectsO4UntilDifferentialEvidenceExists' \
  --tests 'org.graphiks.kadre.contracts.ValidateContractEvidenceTest.scenarioAndSentinelArraysMustUseCanonicalIdOrder' \
  --no-daemon
```

Résultat RED : 6 tests exécutés, 6 échecs attendus. Dans chaque cas, l’ancien validateur acceptait la mutation : JSON JVM seul, JSON browser seul, testcase mappé absent/échoué, résumé ou durée falsifiés, artifact O4, ordre de tableaux permuté.

## GREEN et vérifications

- Baseline avant modification : `:kadre:contracts:validator:jvmTest --no-daemon` — succès.
- GREEN ciblé puis suite complète : `:kadre:contracts:validator:jvmTest --no-daemon` — succès, 65 tests.
- `:kadre:contracts:validator:check --rerun-tasks --no-daemon` — succès, 35 tâches exécutées. Cette commande a exercé les 65 tests, la génération/validation runtime, le registre et les deux validateurs browser planifiés avec leurs répertoires absents.
- `:kadre:contracts:validator:validateAppKitContractEvidence --rerun-tasks --no-daemon` — succès, 40 tâches exécutées. Les tests AppKit natifs, les 12 générations d’evidence et leur relecture corrélée aux deux répertoires JUnit ont réussi.
- Vérification finale fraîche : `:kadre:contracts:validator:check :kadre:contracts:validator:validateAppKitContractEvidence --rerun-tasks --no-daemon` — succès, 58 tâches exécutées sur l’arbre final.
- `git diff --check` — succès.

## Self-review

- Mutation « ignorer complètement JUnit » : tuée par les tests JSON-only JVM et browser.
- Mutation « utiliser un rapport sans le testcase mappé » : tuée par le test de consumer erroné.
- Mutation « accepter un testcase terminal non passé » : tuée par le test JUnit `Failed`.
- Mutation « croire les compteurs ou la durée JSON » : tuée par les permutations `tests=2` et `durationMillis=11` face au XML littéral `1` et `10`.
- Mutation « réautoriser O4 dans le lecteur » : tuée par le test d’artifact O4.
- Mutation « traiter les tableaux comme des sets » : tuée séparément pour scenarios et sentinels.
- La racine d’artifact sélectionnée est conservée avec le chemin JSON ; les rapports relatifs ne sont pas cherchés globalement.
- Les chemins de rapports doivent être relatifs, ne peuvent pas sortir de la racine par `..`, et les chemins browser doivent contenir `{engine}`.
- Les gates browser restent strictement planifiés : aucune tâche productrice, dépendance Playwright, source JS/Wasm ou sortie browser n’apparaît dans le diff.
- La CLI de génération à huit arguments n’a pas été modifiée et aucun test demandé hors scope n’a été ajouté.

## Fichiers modifiés

- `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ContractEvidence.kt`
- `kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ValidateContractEvidence.kt`
- `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ValidateContractEvidenceTest.kt`
- `kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ContractRegistryTest.kt`
- `kadre/contracts/validator/build.gradle.kts`
- `kadre/TEST-STRATEGY.md`
- `kadre/DESIGN.md`
- `.superpowers/sdd/2026-09-06-web-contract-evidence-foundation/final-fix-report.md`

## Préoccupations

Les chemins browser restent réservés et volontairement vides tant que les quatre contrats Web sont `planned`. Leur future activation devra produire, dans chaque racine target et pour chaque engine déclaré, à la fois le JSON, le JUnit corrélé et les mappings exacts. Aucun risque bloquant connu ne subsiste dans le périmètre de ce correctif.
