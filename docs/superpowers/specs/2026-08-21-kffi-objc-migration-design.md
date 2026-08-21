# Migration du binding ObjC vers kffi-objc

## Objectif

Remplacer le binding Objective-C généré et maintenu dans `ffi/objc` par le
module externe `org.graphiks:kffi-objc:1.0.0-SNAPSHOT`, sur le même modèle que
les migrations récentes de Wayland et X11 vers les artefacts KFFI dédiés.

## Contexte et décision

Le backend `kadre-appkit` dépend actuellement de `project(":ffi:objc")`.
Les sources locales exposent déjà le package `org.graphiks.kffi.objc`, qui est
également le namespace public du module `kffi-objc`. Le remplacement peut donc
être réalisé au niveau de la dependency Gradle (dépendance Gradle), sans
réécriture préventive des imports AppKit.

La migration utilisera l’alias versionné `libs.kffi.objc`, avec la version
`1.0.0-SNAPSHOT` et le coordinateur Maven `org.graphiks:kffi-objc`. Le
snapshot repository déjà déclaré dans `settings.gradle.kts` restera la source
de résolution des snapshots `org.graphiks`.

## Périmètre

### Modifications prévues

- Ajouter la version et l’alias de bibliothèque `kffi-objc` dans
  `gradle/libs.versions.toml`.
- Remplacer `api(project(":ffi:objc"))` dans
  `kadre-appkit/build.gradle.kts` par `api(libs.kffi.objc)`.
- Retirer `include(":ffi:objc")` de `settings.gradle.kts`.
- Supprimer le module local `ffi/objc`, y compris ses sources générées.
- Supprimer le script et le workflow dédiés à la régénération locale ObjC,
  puisqu’ils ne produisent plus de source consommée par Kadre.
- Mettre à jour les commentaires ou tests qui décrivent explicitement le
  binding local comme étant généré dans ce dépôt.

### Hors périmètre

- Aucun changement de comportement du backend AppKit.
- Aucun changement des imports publics `org.graphiks.kffi.objc` sauf si la
  compilation démontre une incompatibilité de l’artefact.
- Aucun changement des bindings Win32, X11, Wayland ou POSIX.
- Aucun changement de la toolchain JVM 25 ni du runtime Panama FFM.

## Architecture et flux de dépendances

Le flux cible est :

```text
kadre-appkit:jvmMain
        |
        +--> org.graphiks:kffi-objc:1.0.0-SNAPSHOT
        |          |
        |          +--> runtime KFFI requis par le binding
        |
        +--> kadre-core
```

Le code AppKit continue d’appeler les wrappers `NSWindow`, `NSView`,
`NSApplication`, `ObjCRuntime`, `ObjCSubclassing` et les types associés via
leur namespace existant. La génération des sources et la sélection du SDK
Apple deviennent la responsabilité du projet `kffi` amont.

## Compatibilité et gestion des écarts

La première vérification sera une compilation JVM du module AppKit avec la
dépendance externe. Si un symbole consommé localement n’est pas présent ou si
une signature diffère, l’adaptation sera limitée au code AppKit concerné et
sera couverte par un test de compilation ou un test comportemental existant.
Une compatibilité artificielle dans `ffi/objc` ne sera pas conservée, afin que
le dépôt ne maintienne pas deux sources concurrentes du binding.

La résolution de l’artefact doit rester explicite et reproductible : la
version restera centralisée dans `gradle/libs.versions.toml` et le snapshot
repository existant ne sera pas dupliqué dans un build file de module.

## Validation

La validation minimale sera effectuée dans cet ordre :

1. vérifier que le projet ne référence plus `:ffi:objc`, le script de
   régénération ou le workflow supprimés ;
2. compiler `:kadre-appkit:compileKotlinJvm` pour vérifier la résolution Maven
   et l’ABI source des wrappers ;
3. exécuter les tests JVM AppKit disponibles, notamment les tests de smoke et
   de fenêtre ;
4. exécuter la vérification globale adaptée au changement si la compilation
   ciblée révèle une propagation à d’autres modules ;
5. contrôler le diff et l’état Git final pour confirmer que seules les
   ressources du binding ObjC et les consommateurs AppKit concernés ont changé.

## Critères d’acceptation

- `kadre-appkit` compile avec `org.graphiks:kffi-objc` et sans `:ffi:objc`.
- Les sources AppKit et les tests continuent d’utiliser le namespace
  `org.graphiks.kffi.objc` sans duplication locale.
- Le module `ffi/objc` et ses mécanismes de régénération ne sont plus présents
  dans le projet consommateur.
- Les tests ciblés AppKit passent, ou tout échec restant est documenté comme
  dépendant de l’environnement macOS et séparé d’une erreur de compilation ou
  de résolution de dépendance.
