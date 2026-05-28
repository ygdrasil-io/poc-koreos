# poc-koreos — Kotlin Native Window + wgpu4k (AppKit/macOS)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-purple?logo=kotlin)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.5.0-blue?logo=gradle)](https://gradle.org)
[![Java](https://img.shields.io/badge/Java-25-red?logo=openjdk)](https://openjdk.org)
[![CI](https://github.com/ygdrasil-io/poc-koreos/actions/workflows/ci.yml/badge.svg)](https://github.com/ygdrasil-io/poc-koreos/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.ygdrasil.koreos/koreos)](https://central.sonatype.com/artifact/io.ygdrasil.koreos/koreos)
[![M2 POC: Validated](https://img.shields.io/badge/M2%20POC-Validated%20%E2%9C%85-brightgreen?style=for-the-badge)](docs/koreos/postmortem-m2.md)

<!-- ==========================================
     BADGES DE STATUT DE PROJET PERSONNALISABLES
     Décommentez/copiez simplement le badge correspondant au statut actuel de votre projet.
     ========================================== -->

<!-- STATUT : EN PLANIFICATION (PLANNING) -->
<!-- [![Projet: Planning](https://img.shields.io/badge/Statut-Planning-blue?style=for-the-badge)](https://github.com) -->

<!-- STATUT : INCUBATION / EN DÉVELOPPEMENT (INCUBATING) -->
[![Projet: Incubating](https://img.shields.io/badge/Statut-Incubating-orange?style=for-the-badge)](https://github.com)

<!-- STATUT : STABLE / PRÊT PRODUCTION (STABLE) -->
<!-- [![Projet: Stable](https://img.shields.io/badge/Statut-Stable-green?style=for-the-badge)](https://github.com) -->

<!-- STATUT : DEPRÉCIÉ (DEPRECATED) -->
<!-- [![Projet: Deprecated](https://img.shields.io/badge/Statut-Deprecated-red?style=for-the-badge)](https://github.com) -->

<!-- STATUT : ARCHIVÉ (ARCHIVED) -->
<!-- [![Projet: Archived](https://img.shields.io/badge/Statut-Archived-lightgrey?style=for-the-badge)](https://github.com) -->

---

## Koreos — POC M2 validé

**Koreos** est un moteur de fenêtrage Kotlin natif pour macOS (AppKit) utilisant exclusivement **Panama FFM** (zéro JNA/Rococoa) pour les bindings natifs, et **wgpu4k** pour le rendu GPU via Metal.

### Démo rapide

```bash
# Prérequis : macOS + JDK 25
./gradlew :samples:hello-triangle:run
```

→ Ouvre une fenêtre avec un triangle RGB tournant à ~60 fps. Redimensionnable. Fermeture propre via ⌘W.

### Stack technique

| Couche | Technologie |
|--------|------------|
| Fenêtrage | AppKit (NSWindow, NSView) via Panama FFM |
| Event loop | CFRunLoop (kCFRunLoopBeforeWaiting observer) |
| GPU | wgpu4k 0.1.1 (Metal backend) |
| Shaders | WGSL inline |
| JDK | 25 (`--enable-native-access=ALL-UNNAMED`) |

### Modules

- **`:koreos-core`** — interfaces pures Kotlin (`EventLoop`, `Window`, `ApplicationHandler`, `WindowEvent`)
- **`:koreos-appkit`** — implémentation AppKit/macOS via Panama FFM
- **`:koreos`** — façade publique (`expect`/`actual`)
- **`:samples:hello-triangle`** — démo M2 : triangle RGB + resize

### Documentation

- [Specs](docs/koreos/specs.md) — contrats d'interface
- [Plan de développement](docs/koreos/plan.md) — jalons M1→M3
- [Post-mortem M2](docs/koreos/postmortem-m2.md) — bilan technique, apprentissages, métriques

---

---

## ⚡ CI/CD

Le pipeline GitHub Actions (`.github/workflows/ci.yml`) utilise une stratégie à deux niveaux :

| Job | Runner | Déclencheur | Tâches |
|-----|--------|-------------|--------|
| `build-and-test` | macos-15 | toutes branches | Fast-Track: JVM tests ; Deep: + iosSimulatorArm64 |
| `macos-build` | macos-latest | toutes branches | AppKit + samples macOS |
| `ios-build` | macos-15 | master / PR→master | Compilation + tests iOS simulator |
| `android-build` | ubuntu-latest | master / PR→master | Modules Android + APK samples |

---

## 🛠️ Commandes utiles

```bash
# Tests rapides (JVM, ~3 min)
./gradlew :koreos-core:jvmTest :koreos-appkit:jvmTest :koreos:jvmTest

# Tous les tests (JVM + iOS simulator, ~10 min)
./gradlew :koreos-core:jvmTest :koreos-core:iosSimulatorArm64Test \
          :koreos-appkit:jvmTest :koreos:jvmTest :koreos:iosSimulatorArm64Test

# Démo macOS
./gradlew :samples:hello-triangle:run
./gradlew :samples:hello-window:run
```