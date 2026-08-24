# Archivage du code legacy dans `kadre-old`

## Objectif

Déprécier l’implémentation historique de Kadre en la retirant de la racine active du dépôt, sans promouvoir ni modifier `new-kadre/`.

## État cible

La racine reste le build actif minimal de `:kadre-new`. Elle conserve uniquement les fichiers Gradle communs requis par ce build :

- `settings.gradle.kts`, limité aux inclusions de `:kadre-new` et de ses sous-projets ;
- `buildSrc/`, `gradle/`, `gradle.properties`, `gradlew` et `gradlew.bat` ;
- `.gitignore`, `.gitmodules` et `LICENSE` comme métadonnées du dépôt ;
- `new-kadre/`, sans déplacement ni modification de son contenu.

Le fichier `build.gradle.kts`, les projets legacy, leurs outils, leur documentation et leur CI sont archivés sous `kadre-old/`.

## Contenu archivé

Les éléments suivants sont déplacés avec `git mv` afin de préserver l’historique de renommage :

- modules et dépendances legacy : `kadre/`, `kadre-android/`, `kadre-appkit/`, `kadre-core/`, `kadre-coroutines/`, `kadre-js/`, `kadre-test/`, `kadre-uikit/`, `kadre-wasm/`, `kadre-wayland/`, `kadre-web-common/`, `kadre-win32/`, `kadre-x11/`, `benchmarks/`, `ffi/`, `gilrs/` et `kotlin-js-store/` ;
- exemples, tests et outils legacy : `samples/`, `tests/`, `scripts/`, `docker/` et `third_party/` ;
- CI legacy : `.github/` devient `kadre-old/.github/`, désactivant les workflows GitHub Actions sans les supprimer ;
- documentation et suivi legacy : les contenus suivis de `docs/`, `mkdocs.yml`, `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `DEFERRED.md`, `GAP_ANALYSIS.md`, `REMEDIATION_PLAN.md` et `SPRINT_TRACKING.md` ;
- le build root legacy : `build.gradle.kts`.

Les répertoires ignorés `docs/superpowers/` ne sont pas déplacés afin de ne pas toucher à des artefacts locaux non suivis.

## Comportement et limites

Le `settings.gradle.kts` actif ne configure plus aucun module legacy ; il ne référence que les projets sous `new-kadre/`. Aucun workflow de remplacement n’est créé pour le nouveau code. L’archive `kadre-old/` est une conservation de sources et de CI, pas un build autonome : son exécution reste hors périmètre et l’infrastructure Gradle partagée demeure à la racine.

## Vérification

Après déplacement :

1. `git diff --summary` doit présenter les déplacements comme des renommages.
2. Les seules inclusions de projets dans `settings.gradle.kts` doivent concerner `:kadre-new`.
3. `.github/workflows/` ne doit plus exister à la racine et les fichiers doivent être sous `kadre-old/.github/workflows/`.
4. `./gradlew :kadre-new:check --no-daemon --console=plain` doit réussir.
