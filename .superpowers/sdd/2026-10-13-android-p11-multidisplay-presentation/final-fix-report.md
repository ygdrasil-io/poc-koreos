# Final review wave — fix report (android P11, multi-display via Presentation)

Worktree `/Users/chaos/.zcode/worktrees/poc-koreos/sess-26b8e920`, branch `zcode/sess-26b8e920`,
base HEAD `778ad262`. One dispatch, seven findings, all fixed. Two commits on top:
`fix(android): keep the media watcher alive through an input chain failure` (hardening + tests +
self-test + this report) and `docs(android): tell the eleven contracts and the v1 metrics limit in the register`
(register counts + audit row + v1 metrics limit).

## Canonical counts derived from the artifacts (never guessed)

| artefact | lecture | compte |
|---|---|---|
| `kadre/contracts/driver/android/contracts/evidence.tsv` | 77 lignes = 1 en-tête + 76 rows | **54 scénarios + 22 sentinelles = 76 lignes** ; par contrat 3+2 / 6+2 / 6+3 / 7+3 / 5+3 / 2+1 / 3+1 / 6+2 / 6+2 / 3+2 / **7+1** (AND-011 : 7 scénarios + la sentinelle `android-secondary-display-no-window-claim`, comptés par `awk -F'\t' '{print $1}' evidence.tsv \| sort \| uniq -c`) |
| `kadre/contracts/registry/contracts.tsv` | `grep -n "^AND-"` | **onze lignes `active`**, aux lignes `66,68,69,73,76,77,92,97,98,101,102` |
| `kadre/contracts/driver/android/build/contract-evidence/AND-001.json … AND-011.json` | champ `tests` de CHAQUE JSON | **`tests: 65, failures: 0, errors: 0`** dans les onze ; AND-011 : 7 scénarios `Passed` + 1 sentinelle `Killed` |
| sources du run consumer | `@Test` comptés | 51 (Kotlin, `kadre/consumers/android/src/androidTest/kotlin/...`) + 2 (Java `JavaAndroidFacadeConsumerTest`) + 1 + 2 + 7 (AAR interne `platform/android-devicetest` : capture-device, drop-transfer ×2, leak-cycles ×7) = **63 testcases consumer**, + 2 du sample compose-showcase (seconde APK forkée) = **65 exécutés** — le même compte que le JSON et que le README du driver (« 63 consumer testcases including the eight AND-011 ones … 65 executed », « 54 mapped scenarios and 22 sentinels ») |
| `docs/superpowers/plans/2026-10-13-android-p11-multidisplay-presentation.md` (ledger) | §mutations AND-011 | **3 mutations ciblées** (a : `requestWindow` menteur ; b : input routé au port main ; c : displayId non libéré), « consignées au ledger, pas dans le JSON » |

## Finding 1 — sentinel-count overclaim (phase-11 intro)

- `kadre/capabilities/android.md:270-271` : « 8 scénarios device, 2 sentinelles mutées » →
  « 7 scénarios + 1 sentinelle, 8 lignes d'evidence — les 3 mutations consignées au ledger de
  phase, pas au JSON — ». Dérivé de evidence.tsv (rows 70-77) et du ledger de phase.
- `kadre/capabilities/android.md:544` (§4, ligne multi-écran) : « le contrat AND-011 (8 scénarios +
  sentinelles…) » → « (8 lignes d'evidence — 7 scénarios + 1 sentinelle —) ».

## Finding 2 — comptes phase 10 périmés (onze contrats)

Tous les agrégats recalés aux artefacts ci-dessus :

- `:251-253` (intro phase 10) : « DIX contrats / dix JSON / 48 testcases consumer » → « ONZE
  contrats / onze JSON / 63 testcases consumer, les huit AND-011 compris, + les 2 du sample ».
- `:286-287` (Portée de lecture) : « les dix JSON AND-001.json à AND-010.json » → « les onze JSON
  AND-001.json à AND-011.json ».
- `:313-314` (colonne runtime gate §1) : « phases 1 à 9 / AND-001 à AND-010 » → « phases 0 à 11 /
  AND-001 à AND-011 » (les onze contrats sont device-bound).
- `:317-319` : « les dix JSON… 48 testcases consumer… 50 tests » → « les onze JSON… 63 testcases
  consumer (les huit AND-011 compris)… 65 tests ».
- §5 audit `:557-567` : titre AND-001 à AND-011 (clôtures 10 et 11), « 33 lignes » → « 34 lignes »
  de capability §2 (comptées : 34 rows dans la table §2), « dix lignes active » → « onze »,
  « 47 scénarios + 21 sentinelles » → « 54 scénarios + 22 sentinelles », la chaîne par contrat
  étendue de « 3+2 », « les dix JSON… (50 testcases) » → « les onze JSON… (65 testcases :
  63 consumer + les 2 du sample) ».
- Nouvelle ligne audit `:591` (§5) : `AND-011` — colonnes au format de la table, cite les 7
  evidenceIds scénarios (`android-secondary-display-opens-session`, `-input-via-presentation`,
  `-display-removal-terminal`, `-attached-view-denied`, `-same-display-twice-denied`,
  `-coexists-with-main-session`, `-activity-stop-hides`) + la sentinelle
  `android-secondary-display-no-window-claim`, les classes hôtes
  (`AndroidPresentationStateMachineTest`, `AndroidSecondaryDisplayAttachTest`,
  `AndroidPresentationInputTest`) et les limites.
- `:601-602` : « 10/10 contrats couverts » → « 11/11 », lignes contracts.tsv recalées
  `66,68,69,73,76,77,92,97,98,101,102`.
- `:713` (pied, lien driver README) : « dix JSON, 50 testcases instrumentés » → « onze JSON,
  65 testcases instrumentés ».
- Cohérence §1 (non demandé, rendu nécessaire par les comptes) : l'énumération des contrats dans la
  colonne tests s'arrêtait à AND-010 — clause `AND-011` sur `KadreConsumerSecondaryDisplayTest`
  ajoutée (`:358-360`), et les trois classes hôtes présentation ajoutées à la liste `androidHostTest`
  (`:385-389`).
- Vérif : `grep -nE 'dix JSON|DIX contrats|10/10|50 test|48 test|dix lignes|47 scénarios|21
  sentinelles|8 scénarios' android.md` → plus aucune occurrence.

## Finding 3 — exception dans la fabrique/install de la chaîne d'entrée tue le collecteur

- `kadre/platform/android/src/androidMain/kotlin/org/graphiks/kadre/platform/android/KadreAndroidAttach.kt:937-955`
  (`installInputChainOnce`) : fabrique + pose sous
  `runCatching { … }.onFailure { AndroidAttachDiagnostics.report(it) }` ; le drapeau `wired` n'est
  levé que si la chaîne est RÉELLEMENT posée, et `surface.observationInstalled(inputWired = wired)`
  est TOUJOURS appelé — sur échec, `inputWired = false`, la delivery n'est pas laissée en limbe.
  Le `windowToken()` (appel SDK) est aussi sous le garde.
- Nouvelle contre-épreuve hôte :
  `AndroidPresentationInputTest.`« une fabrique de chaine lancante ne tue pas le collecteur du media »
  (`:133-149`) — fabrique qui jette au premier `Shown`, puis `simulateDisplayRemoved()` ; asserte :
  RIEN de posé (`originalWindowCallback` null), l'échec SIGNALÉ (`AndroidAttachDiagnostics.failures()`
  contient l'instance exacte), session `SessionState.Terminated`, registre vide (réservations
  libérées). Mutation de contrôle : sans le `runCatching` (fix temporairement retiré), le test
  ÉCHOUE (« java.lang.AssertionError: Expected value to be true. ») — le terminal n'est plus
  observé ; fix restauré, test vert.

## Finding 4 — ruling : documenter la limite v1, ne PAS implémenter

- `kadre/capabilities/android.md:396` (§2, ligne présentation), inséré après « la surface naît aux
  métriques RÉELLES de l'écran (…) » : « **métriques de construction** : elles sont établies UNE
  FOIS à l'attach depuis les faits de l'écran — le changement de mode/densité d'un écran secondaire
  VIVANT n'est pas propagé en v1 (aucun `AndroidSurfaceObservationBinding` pour cette forme : posé
  tel quel sur le contentView non attaché, il posterait une lecture initiale dégénérée à taille 0
  qui écraserait les faits — candidat phase suivante) ». Aucune implémentation ajoutée (ruling
  respecté) ; la ligne audit AND-011 reprend la limite.

## Finding 5 — clauses KDoc périmées (level-triggered)

- `KadreAndroidAttach.kt:756-758` : « CACHÉE jusqu'au premier START » → « CACHÉE jusqu'au premier
  show : dès l'attach si l'hôte est déjà ≥ STARTED, sinon au premier START ».
- `KadreAndroidAttach.kt:1244-1246` (`presentationSurfaceState`) : « le premier START la rend
  Visible » → « le premier show la rend Visible : dès l'attach si l'hôte est déjà ≥ STARTED, sinon
  au premier START ». (Les KDoc `:145-146` et `:1330-1333` énonçaient déjà le bon doctrine — non
  touchés.)

## Finding 6 — self-test : le fake adb n'assertait que le verbe

- `scripts/test-kadre-android-device-selftest.sh:93-101` : pour `put`, le fake journalise
  désormais `"$5 ${8:?…}"` — le verbe ET la spécification (`$8`), avec échec explicite si la spec
  manque.
- `:454` : l'attente du cas de provisioning devient `delete / put 1280x720/160 / delete` — la
  valeur exacte passée à `settings put global overlay_display_devices` est assertée (défaut de
  `KADRE_ANDROID_OVERLAY_DISPLAY_SPEC` dans `scripts/test-kadre-android-device.sh:189`, lu à la
  ligne 198).

## Finding 7 — nom de helper trompeur

- `AndroidPresentationInputTest.kt` : `PresentationInputHost.attachKadre` renommé
  `attachSurrogateMainSession` (définition `:418`, unique site d'appel `:68` mis à jour) — le
  helper attache une SECONDE session présentation comme surrogate de la session main (la forme
  Activity n'est pas conduisible sur hôte) ; KDoc d'origine conservé.

## Tests et vérifications

```
./gradlew :kadre:platform:android:testAndroidHostTest \
  --tests "org.graphiks.kadre.platform.android.AndroidPresentationInputTest" \
  --tests "org.graphiks.kadre.platform.android.AndroidSecondaryDisplayAttachTest" \
  --tests "org.graphiks.kadre.platform.android.AndroidPresentationStateMachineTest" \
  --console=plain
# BUILD SUCCESSFUL — AndroidPresentationInputTest tests=5 failures=0 errors=0
#   (dont « une fabrique de chaine lancante ne tue pas le collecteur du media »)
#   AndroidSecondaryDisplayAttachTest tests=15 failures=0 errors=0
#   AndroidPresentationStateMachineTest tests=4 failures=0 errors=0

./gradlew :kadre:platform:android:testAndroidHostTest --rerun-tasks --console=plain
# BUILD SUCCESSFUL — agrégat JUnit XML : tests=246 failures=0 errors=0

./gradlew :kadre:platform:android:check --console=plain
# BUILD SUCCESSFUL (30 actionable tasks) — compile + ABI baseline + suite hôte + lint

bash scripts/test-kadre-android-device-selftest.sh
# PASS: provisioning (put-delete-put-delete) — put/delete sequence exact (verb + overlay spec)
# … PASS: android device selection + portable watchdog contract holds (22 scenario checks)
```

## Écarts et notes

- Le premier lancement de `:kadre:platform:android:check` après l'édition a échoué une fois en 2 s
  (flake de rebuild incrémental immédiatement après la restauration du fix) ; trois relances
  consécutives (check, `--rerun-tasks`, check) sont vertes et aucun XML JUnit ne porte d'échec —
  aucune trace d'un échec réel.
- `evidence.tsv`, `contracts.tsv`, les onze JSON générés et le README du driver étaient déjà
  corrects (sept scénarios + une sentinelle) ; seul le registre `capabilities/android.md`
  surachetait.
- Le fichier report est gitignoré par `.superpowers/sdd/.gitignore` (`*`) ; il est commité avec
  `git add -f` selon le précédent `final-sol-fix-report.md` (2026-08-19).
