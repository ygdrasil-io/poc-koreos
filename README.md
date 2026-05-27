# 🚀 Project Template - Kotlin Multiplatform (KMP)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-purple?logo=kotlin)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.5.0-blue?logo=gradle)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.0.0-green?logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![Java](https://img.shields.io/badge/Java-25-red?logo=openjdk)](https://openjdk.org)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-blue?logo=github-actions)](https://github.com/features/actions)

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