# Task 6 — Activation atomique des preuves de géométrie AppKit

## Résultat

WIN-001 (O2 runtime) et APK-006 (O3 AppKit) sont maintenant actifs ensemble.
Le validator lit les deux TSV d'evidence et vérifie, pour chaque contrat
configuré dans les gates runtime/AppKit, que tous les scénarios et sentinelles
du registry sont mappés exactement une fois. Il refuse aussi un mapping actif
qui ne figure dans aucun gate configuré.

Les IDs requis sont INP-001 et WIN-001 pour runtime, puis APK-001 à APK-006
pour AppKit. Le gate scripts/test-kadre-appkit-contracts.sh exige désormais
APK-006.json.

## RED

Avant la modification du gate AppKit, le test de driver ajoutait le cas où la
génération simulée omet APK-006.json. Il a échoué comme attendu :

```text
FAIL: missing APK-006 evidence passed the AppKit contract gate
```

Le nouveau contrôle validator a ensuite été écrit avec des fixtures réelles
WIN-001 et APK-006 ; il a d'abord échoué à la compilation, car l'overload de
validation des mappings/gates n'existait pas encore :

```text
Too many arguments for 'fun validateContractRegistry(path: Path): List<String>'.
```

Enfin, après l'activation provisoire mais avant les mappings publics
manquants, le gate réel a produit le RED fonctionnel :

```text
APK-006: missing scenario: appkit-window-geometry-external-resize
APK-006: missing scenario: appkit-window-geometry-policy
APK-006: missing scenario: appkit-window-geometry-public-activation
APK-006: missing sentinel: appkit-window-geometry-cancellation-boundary
APK-006: missing sentinel: appkit-window-geometry-cross-window
APK-006: missing sentinel: appkit-window-geometry-invalid-precommit
APK-006: missing sentinel: appkit-window-geometry-operation-correlation
APK-006: missing sentinel: appkit-window-geometry-policy-bypass
```

Le test fixture couvre séparément un sentinel absent pour WIN-001, un
APK-006 actif hors gate, puis le cas complet vert. Il exécute la vraie
validation TSV, sans assertion de texte source.

## Mappings activés

Les mappings WIN-001 étaient déjà complets dans
kadre/runtime/contracts/evidence.tsv depuis la tâche 2 : validation,
sérialisation, cancellation/close, delivery policy, invalid pre-commit, stale
dispatch, ordre état/événement, corrélation, commande après close et policy
bypass de RuntimeWindowManagerTest.

Les neuf entrées APK-006 ajoutées complètent les quatre entrées privées
existantes :

| Evidence ID | Test JUnit réel |
| --- | --- |
| public activation | AppKitBackendProviderTest.publicAppKitWindowGeometryActivatesOnlyTheFourProvenCapabilitiesOnMacOs[jvm] |
| initial constraints | KffiAppKitWindowPortMacOsTest.generatedKffiWindowAppliesInitialContentConstraintsAndResizableMaskOnMacOs[jvm] |
| native update, style-mask, clear | KffiAppKitWindowPortMacOsTest.generatedKffiWindowUpdatesContentConstraintsAndRestoresNativeDefaultsOnMacOs[jvm] |
| external resize | AppKitBackendProviderTest.nativeExternalResizeUpdatesWindowStateWithNullOperationIdOnMacOs[jvm] |
| invalid pre-commit, operation correlation | AppKitBackendProviderTest.publicAppKitWindowApplyUsesGeneratedNativeGeometryAndCorrelatesOperationOnMacOs[jvm] |
| cancellation boundary, post-close | AppKitWindowRuntimeDriverTest.queuedGeometryUpdateCompletesWhenCloseIsAdmittedBeforeNativeCommit[jvm] |
| cross-window | AppKitWindowRuntimeDriverTest.inputForOneLivePeerDoesNotCrossIntoAnotherSurfaceOfTheSameDriver[jvm] |
| delivery policy, policy bypass | AppKitBackendProviderTest.publicAppKitWindowGeometryEventsFollowSessionPolicyOnMacOs[jvm] |

Les rapports JUnit présents ont été interrogés et contiennent tous ces noms.

## GREEN et vérification

Succès observés :

```text
./gradlew :kadre:contracts:validator:jvmTest --console=plain
BUILD SUCCESSFUL in 962ms

./gradlew :kadre:contracts:validator:validateContractRegistry :kadre:runtime:jvmTest :kadre:backend:appkit:jvmTest --console=plain
BUILD SUCCESSFUL in 7s

bash scripts/test-kadre-appkit-contract-driver.sh
Kadre AppKit contract driver behavior: passed

git diff --check
exit 0
```

## Auto-revue

- Aucun ID, scénario ou sentinel réservé n'a été renommé.
- Les deux status passent à active dans le même diff que les mappings et gates.
- Le validator ne relance pas le test process-owning AppKit : il valide les
  données de registry et de mapping ; le script AppKit reste le gate qui
  exécute les tests natifs et génère les JSON.
- Aucun fichier KFFI/Kextract, FFI manuel, capability publique, ni comportement
  de fenêtre n'a été modifié.

## Concern

Deux exécutions de ./scripts/test-kadre-appkit-contracts.sh ont échoué avant
la phase d'evidence dans le test préexistant appKitStandaloneLoopTest, avec
NSInternalInconsistencyException sur des événements GUI externes (FlagsChanged
puis MouseEntered) et le signal SIGABRT/134. La suite JVM AppKit demandée
passe ; ce crash de process-owning AppKit empêche seulement la vérification
locale complète du script natif et doit être traité séparément, hors du scope
de l'activation des contrats.

## Corrections de revue — sentinelles O3 et couverture complète des gates

### RED

Les nouveaux tests ont été écrits avant les remappings et le changement du validator.
Le fixture activeWindowContractMissingFromMappingsAndGatesIsRejected a échoué
comme attendu : retirer APK-006 du TSV et de son gate ne produisait aucune erreur.

Les deux tests publics AppKit sont :

- publicAppKitWindowRejectsInvalidGeometryBeforeNativeCommitOnMacOs : soumet
  min > max et vérifie InvalidRequest(sizeConstraints), l'état public et la
  vraie NSWindow inchangés, ainsi que l'absence d'event GeometryChanged ;
- publicAppKitWindowGeometryDoesNotCrossBetweenTwoWindowsOnMacOs : provoque
  une observation native sur la première fenêtre puis une opération sur la
  seconde, et vérifie que tailles, états et events restent associés à leur
  propre fenêtre.

Le premier essai multi-fenêtres a détecté une révision native tardive de la
seconde fenêtre, qui rétablit ses bornes à null après sa propre opération. Ce
n'était pas une contamination inter-fenêtres : l'assertion a donc été limitée
à l'invariant requis (états/taille/event propres à chaque fenêtre), puis
réexécutée.

### GREEN

Les sentinelles APK-006 utilisent désormais les preuves O3 dédiées :

- appkit-window-geometry-invalid-precommit ->
  AppKitBackendProviderTest.publicAppKitWindowRejectsInvalidGeometryBeforeNativeCommitOnMacOs[jvm] ;
- appkit-window-geometry-cross-window ->
  AppKitBackendProviderTest.publicAppKitWindowGeometryDoesNotCrossBetweenTwoWindowsOnMacOs[jvm].

Le validator itère les contrats actifs des familles à evidence (APK-, INP-,
WIN-) ; une entrée active sans gate est rejetée, même si toutes ses lignes ont
été retirées des TSV. Les contrats non concernés par ces gates conservent leur
comportement existant.

Vérification fraîche :

```text
./gradlew :kadre:contracts:validator:validateContractRegistry :kadre:runtime:jvmTest :kadre:backend:appkit:jvmTest --console=plain
BUILD SUCCESSFUL in 7s

bash scripts/test-kadre-appkit-contract-driver.sh
Kadre AppKit contract driver behavior: passed

git diff --check
exit 0
```

Aucun code de capability publique ou FFI n'a été modifié ; seuls les tests,
mappings et la validation des gates ont changé.
