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

Ce projet est un **Starter Pack de pointe pour Kotlin Multiplatform (KMP)** ciblant **Android**, **iOS** et **Desktop (JVM)**. Il intègre les dernières technologies de l'écosystème (Kotlin 2.3.21, Gradle 9.5.0, AGP 9.0, Java 25) et applique rigoureusement les principes de la **Clean Architecture** et du **Domain-Driven Design (DDD)**.

---

## 🏗️ Architecture du Projet

Le module partagé `:shared` est organisé en couches distinctes pour maximiser la testabilité, la maintenabilité et le découplage :

```mermaid
graph TD
    UI[Couche Présentation: Compose Multiplatform / ViewModel] --> Domain[Couche Domaine: Use Cases / Models / Repository Interfaces]
    Data[Couche Données: Repository Impls / Ktor / Local SQL] --> Domain
    Data --> Platform[Code Spécifique Plateforme: expect/actual]
```

### Couches de Conception (`shared/src/commonMain`)
*   **Domaine (Domain)** : Contient les règles métiers pures sans dépendances de framework (Cas d'utilisation avec opérateur `invoke`, modèles auto-validés, interfaces de dépôt).
*   **Données (Data)** : Implémentations concrètes des dépôts, communication réseau et base de données.
*   **Présentation (Presentation)** : Modélisation d'état d'UI (`UiState`) immuable et ViewModels utilisant des `StateFlow` asynchrones.
*   **Injection de Dépendances (DI)** : Configuration centralisée multiplateforme via **Koin**.

---

## ⚡ Workflow CI/CD (Intégration Continue)

Le pipeline GitHub Actions ([ci.yml](file:///.github/workflows/ci.yml)) implémente un système de double-vitesse optimisé pour la bande passante et le temps de calcul :

- **Fast-Track (Branches secondaires)** : Ne compile et ne teste que la cible JVM locale (`./gradlew :shared:jvmTest`). Exécution instantanée en moins de 10 secondes.
- **Deep-Testing (Branches / Pull Requests vers `master`)** : Exécute l'ensemble des tests (`./gradlew allTests`) sur tous les simulateurs et plateformes cibles pour valider la qualité du code avant la mise en production.

---

## 🛠️ Commandes Utiles de Développement

### Exécuter les tests locaux (Fast-Track JVM)
```bash
./gradlew :shared:jvmTest
```

### Exécuter tous les tests (Toutes cibles)
```bash
./gradlew allTests
```

### Générer le Gradle Wrapper
```bash
gradle wrapper
```