# Livraison conjointe Win32 phase 4

Kadre : base `09a29b0a`, branche `codex/win32-phase-4-input`.
KFFI : base `67bb7b1`, branche `feat/win32-phase-4-input`, commits
`a6af6f1` et `5ad1fee`. Le checkout KFFI utilisé ici est
`.worktrees/kffi-phase4` (ignoré par Git Kadre).

## Reproduire la validation JVM Windows

JDK 25 et bureau interactif Windows requis. Dans le checkout KFFI :

```powershell
$env:KFFI_WIN32_INTEGRATION='1'
.\gradlew.bat :kffi:jvmTest :kffi-win32:jvmTest --configure-on-demand --console=plain
.\scripts\publish-win32-local.ps1
```

Dans le checkout Kadre :

```powershell
.\gradlew.bat :kadre:backend:win32:win32ContractTests :kadre:runtime:jvmTest :kadre:platform:desktop:jvmTest :kadre:contracts:validator:jvmTest :kadre:contracts:validator:generateWin32ContractEvidence '-PkadreKffiLocal=true' '-PkadreKffiWin32Version=1.0.0-win32-phase4-local-SNAPSHOT' --console=plain
.\gradlew.bat :kadre:backend:win32:dependencyInsight --dependency kffi-win32 --configuration jvmRuntimeClasspath '-PkadreKffiLocal=true' '-PkadreKffiWin32Version=1.0.0-win32-phase4-local-SNAPSHOT'
```

Gate avec watchdog sous Git Bash (mêmes propriétés pour tests et preuves) :

```bash
scripts/test-kadre-win32-contracts.sh -PkadreKffiLocal=true -PkadreKffiWin32Version=1.0.0-win32-phase4-local-SNAPSHOT
```

`dependencyInsight` résout explicitement `kffi-win32` et `kffi-win32-jvm`
à `1.0.0-win32-phase4-local-SNAPSHOT`, puis `kffi-jvm` de cette même version.
Le repository local est filtré sur ces quatre modules ; la résolution par
défaut reste celle du catalogue versionné.

## Preuves et portée

Les suites complètes Windows, runtime, Desktop et validateur passent.
KFFI : 110 tests core et 22 Win32, sans échec ni skip, avec l'intégration native
activée. Les preuves Kadre sont générées pour W32-001 à W32-011, BCK-025,
SES-002 et PMP-001 : **14 JSON non vides**.

Le test public `publicDesktopApplicationOpensFromAWorkerAndObservesNativeInput`
utilise le vrai provider découvert par Desktop, ouvre depuis les coroutines
applicatives, poste un message natif, observe `surface.input`, puis ferme la
dernière fenêtre. Il a reproduit et permis de corriger un deadlock
request-open/manager et un timeout de shutdown. Les commandes d'ouverture,
d'annulation et de cleanup rendent la main avant leur exécution UI ; les
stimuli natifs terminent l'opération.

Une seconde preuve ouvre une fenêtre dans le callback natif. Le runtime
révoque son input ordinaire en attente si une perte de focus survient dans
ce callback, même si le focus revient avant son retour. Cela évite de
réintroduire une touche après le reset.

La cohérence DPI est testée aussi après `WM_DPICHANGED` : taille physique,
scale et coordonnées d'input sont relus par fenêtre. Un NumLock étendu reste
une touche du pavé. Les tests portables couvrent les frontières de coalescence
wheel et l'annulation avant prise en charge d'une ouverture en queue.

Le générateur Win32 épinglé a produit les bindings puis `--check` a retourné
succès, sans différence de sortie. Limite de l'hôte : Clang signale l'absence
du header MSVC `excpt.h` ; les 81 fonctions demandées sont générées et les
nouveaux appels sont exercés nativement. Une exécution sans ce diagnostic
reste à effectuer sur un hôte disposant des headers Visual Studio complets.

Le [harness et cahier manuel](WIN32-PHASE-4-MANUAL.md) sont fournis ; les
scénarios physiques US/non-US, AltGr/dead keys, DPI multi-écrans et menus
système ne sont pas déclarés exécutés.

## Décisions prises pendant l'exécution

- KFFI JVM : `--configure-on-demand` évite les projets Android étrangers à
  cette preuve. Coût si cette frontière change : ajuster la commande JVM,
  sans prétendre avoir validé Android.
- Génération : utiliser le commit Kextract épinglé par le script Win32,
  puis restaurer le gitlink global du dépôt. Coût si incorrect : échec du
  check de génération ; aucun binding manuel de substitution.
- Terminal/reset : le contrat canonique du runtime prévaut ; fermeture
  gèle le snapshot, capture reste à sa phase dédiée. Coût si insuffisant :
  décision d'API future sur la capture, pas un faux `FocusLost`.
- Maven local : publication JVM isolée avec POM alias et version
  `-local-SNAPSHOT` distincte. Coût : les métadonnées KMP et Dokka de
  production ne sont pas validées par ce chemin local.
- Ownership : la session libère son control HWND ; le provider ferme son
  port. Coût : un appelant direct qui possède le port doit le fermer.
- Interaction : enrichir le payload interne existant pour garder le
  caractère et les modifiers dans l'unique événement ordinaire après
  callback. Coût : maintenir les valeurs par défaut des autres adapters ;
  couvert par les tests runtime, sans extension de l'API foundation.
- Fenêtre interactive : publier les métriques natives initiales et montrer
  après admission de la surface. Coût : la taille effective peut différer
  de la demande ; elle est désormais exposée honnêtement.

## Publication distante

La publication Maven locale demandée est effectuée. Aucune publication
Central ni fusion automatique n'est incluse. La PR Kadre dépend de la PR
KFFI et reste en draft tant qu'un snapshot distant contenant ces wrappers
n'est pas publié puis épinglé dans le catalogue Kadre. Les jobs distants
qui utilisent encore l'ancien snapshot ne constituent pas une validation
de cette paire locale.
