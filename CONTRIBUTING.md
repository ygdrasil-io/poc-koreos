# 🤝 Guide de Contribution - Kadre

Merci de votre intérêt pour contribuer à **Kadre**! Ce guide vous expliquera comment participer efficacement au projet.

---

## 🌟 **Comment Contribuer**

Il existe plusieurs façons de contribuer à Kadre:

1. **🐛 Signaler des bugs** - Ouvrir des issues pour les problèmes rencontrés
2. **💡 Proposer des fonctionnalités** - Suggérer de nouvelles idées
3. **📝 Améliorer la documentation** - Corriger ou compléter la documentation
4. **🔧 Contribuer au code** - Implémenter des fonctionnalités ou corriger des bugs
5. **🧪 Écrire des tests** - Ajouter des tests pour améliorer la couverture
6. **📊 Participer aux discussions** - Aider à prendre des décisions d'architecture

---

## 📋 **Processus de Contribution**

### 1. **Trouver une Tâche**

Consultez les ressources suivantes pour trouver une tâche adaptée à vos compétences:

- **[SPRINT_TRACKING.md](SPRINT_TRACKING.md)** - Tableau de bord des sprints en cours
- **[Issues GitHub](https://github.com/ygdrasil-io/poc-koreos/issues)** - Toutes les issues ouvertes
- **Labels utiles**:
  - [`good first issue`](https://github.com/ygdrasil-io/poc-koreos/labels/good%20first%20issue) - Bon pour les nouveaux contributeurs
  - [`help wanted`](https://github.com/ygdrasil-io/poc-koreos/labels/help%20wanted) - Besoin d'aide
  - [`priority: P0`](https://github.com/ygdrasil-io/poc-koreos/labels/priority%3A%20P0) - Tâches critiques
  - [`priority: P1`](https://github.com/ygdrasil-io/poc-koreos/labels/priority%3A%20P1) - Tâches importantes

### 2. **S'assigner une Tâche**

1. **Commenter sur l'issue**: Indiquez votre intention de travailler sur cette tâche
   ```
   Je m'occupe de cette issue. Effort estimé: X jours.
   ```

2. **Vous assigner**: Si vous avez les permissions, assignez-vous à l'issue
   ```bash
   gh issue edit <number> --add-assignee @votre-utilisateur
   ```

3. **Changer le statut**: Passez l'issue à `status: in-progress`
   ```bash
   gh issue edit <number> --add-label "status: in-progress" --remove-label "status: backlog"
   ```

### 3. **Configurer l'Environnement de Développement**

Voir [DEVELOPMENT.md](DEVELOPMENT.md) pour les instructions détaillées.

**Résumé rapide**:
```bash
# Cloner le dépôt
git clone https://github.com/ygdrasil-io/poc-koreos.git
cd poc-koreos

# Configurer les sous-modules (si applicable)
git submodule update --init --recursive

# Installer les dépendances
./gradlew dependencies

# Construire le projet
./gradlew build
```

### 4. **Créer une Branche**

Utilisez la convention de nommage suivante pour les branches:

| Type de contribution | Format de la branche | Exemple |
|----------------------|---------------------|---------|
| Nouvelle fonctionnalité | `feature/[sprint-X]-[ticket-id]-description` | `feature/sprint-1-259-ime-capabilities` |
| Correction de bug | `fix/[sprint-X]-[ticket-id]-description` | `fix/sprint-1-261-showcursor-mismatch` |
| Documentation | `docs/[description]` | `docs/update-keyboard-api` |
| Refactoring | `refactor/[description]` | `refactor/wayland-protocols` |
| Hotfix | `hotfix/[description]` | `hotfix/critical-security-issue` |

**Exemple**:
```bash
# Pour l'issue #259 (Sprint 1)
git checkout -b feature/sprint-1-259-ime-capabilities
```

### 5. **Travailler sur la Tâche**

1. **Suivre la checklist**: Chaque issue contient une checklist détaillée
2. **Respecter les conventions**: Voir [CONVENTIONS.md](CONVENTIONS.md)
3. **Écrire des tests**: Toujours ajouter des tests pour le nouveau code
4. **Documenter**: Mettre à jour la documentation si nécessaire

### 6. **Soumettre une Pull Request (PR)**

1. **Pousser votre branche**:
   ```bash
   git push -u origin feature/sprint-1-259-ime-capabilities
   ```

2. **Créer la PR**:
   ```bash
   gh pr create --title "feat(core): implement Window.ime_capabilities()" \
               --body "Fixes #259" \
               --base develop \
               --head feature/sprint-1-259-ime-capabilities
   ```

3. **Lier à l'issue**: Dans la description de la PR, ajoutez `Fixes #XXX` ou `Closes #XXX`

4. **Ajouter des labels**:
   - `priority: PX` (même que l'issue)
   - `category: X` (même que l'issue)
   - `backend: X` (si applicable)

5. **Ajouter des reviewers**:
   ```bash
   gh pr edit <pr-number> --add-reviewer @reviewer1,@reviewer2
   ```

### 7. **Processus de Revue**

1. **Attendre les commentaires**: Les reviewers feront des commentaires sur votre PR
2. **Corriger les problèmes**: Répondez aux commentaires et corrigez le code
3. **Pousser les corrections**:
   ```bash
   git add .
   git commit -m "fix: address review comments"
   git push
   ```
4. **Attendre l'approbation**: Au moins 1 approbation est requise

### 8. **Fusion (Merge)**

Une fois la PR approuvée:

1. **Vérifier que tous les checks CI passent**
2. **Squash & Merge** (si la branche a plusieurs commits)
3. **Supprimer la branche** (optionnel mais recommandé)
4. **Mettre à jour l'issue**:
   - Passer à `status: done`
   - Ajouter un commentaire avec le lien vers la PR fusionnée

---

## 📜 **Conventions de Code**

### 1. **Conventions de Commit**

Utilisez [Conventional Commits](https://www.conventionalcommits.org/):

| Type | Utilisation | Exemple |
|------|-------------|---------|
| `feat` | Nouvelle fonctionnalité | `feat(core): add ImeCapabilities type` |
| `fix` | Correction de bug | `fix(win32): correct ShowCursor counter` |
| `docs` | Documentation | `docs: update keyboard API documentation` |
| `style` | Changements de style | `style: format code with ktfmt` |
| `refactor` | Refactoring | `refactor(wayland): extract protocol detection` |
| `perf` | Optimisation de performance | `perf: optimize event processing` |
| `test` | Tests | `test: add unit tests for ImeCapabilities` |
| `chore` | Tâches diverses | `chore: update dependencies` |
| `build` | Changements de build | `build: update Gradle configuration` |
| `ci` | Configuration CI | `ci: add GitHub Actions workflow` |

**Format**:
```
type(scope): description

[body optionnel]

[footer optionnel]
```

### 2. **Conventions de Nommage**

#### Classes/Interfaces
- **PascalCase** pour les noms de classes et interfaces
- **Descriptif et clair**
- **Préfixe** avec le nom du module si applicable

**Exemples**:
```kotlin
// Bon
class AppKitWindow
interface Window
class WaylandEventLoop

// À éviter
class AKW  // Trop court
class WindowImpl  // Pas descriptif
```

#### Variables/Fonctions
- **camelCase** pour les variables et fonctions
- **Verbes** pour les fonctions
- **Noms** pour les variables

**Exemples**:
```kotlin
// Bon
fun requestRedraw()
val windowTitle: String
fun setFullscreen(mode: Fullscreen)

// À éviter
fun redraw()  // Pas clair
val title  // Manque de type
fun fullscreen(mode: Fullscreen)  // Pas un verbe
```

#### Constantes
- **UPPER_SNAKE_CASE** pour les constantes

**Exemples**:
```kotlin
// Bon
const val MAX_WINDOW_SIZE = 8192
val DEFAULT_SCALE_FACTOR = 1.0

// À éviter
const val maxWindowSize = 8192
val defaultScaleFactor = 1.0
```

### 3. **Organisation du Code**

- **1 classe par fichier** (sauf pour les classes très petites et liées)
- **Ordre des imports**: Kotlin, Java, Autres (par ordre alphabétique)
- **Ordre dans les classes**:
  1. Properties (val/var)
  2. Constructor
  3. Functions
  4. Companion object

### 4. **Documentation**

- **Toujours documenter** les classes et fonctions publiques
- **Utiliser KDoc** pour la documentation Kotlin
- **Exemples** dans la documentation

**Exemple**:
```kotlin
/**
 * Represents a native window managed by kadre.
 *
 * This interface provides platform-agnostic window operations.
 * Concrete implementations are provided by platform modules.
 *
 * @since 1.0.0
 * @see WindowAttributes
 */
interface Window {
    /**
     * Unique identifier of the window.
     *
     * This ID is guaranteed to be unique within the event loop.
     */
    val id: WindowId
    
    /**
     * Sets the title of the window.
     *
     * @param title New title to display in the window's title bar
     */
    fun setTitle(title: String)
}
```

---

## 🧪 **Tests**

### 1. **Types de Tests**

| Type | Dossier | Exemple | Quand l'utiliser |
|------|---------|---------|------------------|
| Tests unitaires | `src/test/kotlin` | `WindowTest.kt` | Tester une seule fonction/classe |
| Tests d'intégration | `src/integrationTest/kotlin` | `WaylandIntegrationTest.kt` | Tester l'interaction entre composants |
| Tests de performance | `benchmarks/` | `PongBenchmarks.kt` | Mesurer les performances |

### 2. **Conventions de Test**

- **Noms de classes**: `ClassNameTest` ou `ClassNameSpec`
- **Noms de méthodes**: `shouldDoSomething_whenCondition` ou `testFunctionName`
- **Organisation**: Un fichier par classe testée

**Exemple**:
```kotlin
class WindowTest {
    @Test
    fun `setTitle should update window title`() {
        val window = createTestWindow()
        window.setTitle("New Title")
        assertEquals("New Title", window.title)
    }
    
    @Test
    fun `requestRedraw should trigger redraw event`() {
        val window = createTestWindow()
        var redrawCalled = false
        window.onRedrawRequested = { redrawCalled = true }
        
        window.requestRedraw()
        
        assertTrue(redrawCalled)
    }
}
```

### 3. **Couverture de Test**

- **Cible**: > 90% de couverture pour le code nouveau
- **Outils**: Utilisez JaCoCo ou Kotest pour mesurer la couverture
- **Commande**:
  ```bash
  ./gradlew testCoverageReport
  ```

---

## 📊 **Gestion des Issues et PRs**

### 1. **Labels**

Utilisez les labels suivants pour organiser les issues et PRs:

| Catégorie | Labels | Description |
|----------|--------|-------------|
| **Priorité** | `priority: P0`, `priority: P1`, `priority: P2`, `priority: P3` | Niveau de priorité |
| **Catégorie** | `category: window-api`, `category: events`, etc. | Type de fonctionnalité |
| **Backend** | `backend: appkit`, `backend: win32`, etc. | Backend concerné |
| **Sprint** | `sprint: 1`, `sprint: 2`, etc. | Sprint actuel |
| **Phase** | `phase: 1`, `phase: 2`, `phase: 3` | Phase du plan de rémédiation |
| **Statut** | `status: backlog`, `status: ready`, `status: in-progress`, etc. | Statut actuel |

### 2. **Milestones**

Les milestones sont utilisés pour regrouper les issues par version:
- **v1.3.0** - Phase 1: Fondations Critiques
- **v2.0.0** - Phase 2: Parité Complète
- **v2.1.0** - Phase 3: Excellence

### 3. **Projects**

Un projet GitHub est utilisé pour visualiser le progrès:
- **Kadre Remediation Plan** - Tableau Kanban des sprints

---

## 🤝 **Code de Conduite**

Nous nous engageons à maintenir un environnement de contribution **ouvert, accueillant et respectueux**. En participant, vous acceptez de respecter notre [Code de Conduite](CODE_OF_CONDUCT.md).

### **Comportements Attendus**
- ✅ **Respect**: Traitez tout le monde avec respect
- ✅ **Collaboration**: Travaillez ensemble pour résoudre les problèmes
- ✅ **Patience**: Soyez patient avec les nouveaux contributeurs
- ✅ **Constructif**: Faites des commentaires constructifs
- ✅ **Inclusif**: Encouragez la participation de tous

### **Comportements Inacceptables**
- ❌ **Harcèlement**: Tout comportement de harcèlement
- ❌ **Discrimination**: Discrimination de quelque forme que ce soit
- ❌ **Troll**: Commentaires provocateurs ou hors-sujet
- ❌ **Plagiat**: Copier le travail des autres sans attribution
- ❌ **Spam**: Messages non pertinents ou publicitaires

---

## 📚 **Ressources**

### **Documentation**
- [README.md](README.md) - Présentation du projet
- [GAP_ANALYSIS.md](GAP_ANALYSIS.md) - Analyse des écarts avec winit
- [REMEDIATION_PLAN.md](REMEDIATION_PLAN.md) - Plan de rémédiation détaillé
- [SPRINT_TRACKING.md](SPRINT_TRACKING.md) - Suivi des sprints en cours
- [DEVELOPMENT.md](DEVELOPMENT.md) - Guide de développement
- [ARCHITECTURE.md](docs/features/architecture.md) - Architecture du projet

### **Outils**
- [GitHub Issues](https://github.com/ygdrasil-io/poc-koreos/issues) - Suivi des bugs et fonctionnalités
- [GitHub Discussions](https://github.com/ygdrasil-io/poc-koreos/discussions) - Discussions générales
- [GitHub Actions](https://github.com/ygdrasil-io/poc-koreos/actions) - CI/CD
- [Dokka Documentation](https://ygdrasil-io.github.io/poc-koreos/) - Documentation API générée

### **Communauté**
- **Slack/Discord**: [Lien à ajouter]
- **Forum**: [Lien à ajouter]
- **Mailing List**: [Lien à ajouter]

---

## 🎁 **Reconnaissance**

Nous apprécions toutes les contributions, grandes ou petites! Voici comment nous reconnaissons les contributions:

1. **🏆 Contributeurs Actifs**: Ajoutés au fichier [CONTRIBUTORS.md](CONTRIBUTORS.md)
2. **🌟 Contributions Majeures**: Mentionnées dans le CHANGELOG
3. **🏅 Contributions Exceptionnelles**: Badges GitHub (ex: "Arctic Code Vault Contributor")
4. **💰 Programme de Récompenses**: Pour les contributions significatives (à discuter)

---

## 📞 **Contact**

Pour toute question ou préoccupation:

- **Issues techniques**: Ouvrez une [issue GitHub](https://github.com/ygdrasil-io/poc-koreos/issues)
- **Questions générales**: Utilisez [GitHub Discussions](https://github.com/ygdrasil-io/poc-koreos/discussions)
- **Problèmes de sécurité**: Contactez [security@ygdrasil.io](mailto:security@ygdrasil.io)
- **Collaboration**: Contactez [collab@ygdrasil.io](mailto:collab@ygdrasil.io)

---

## 📝 **Licence**

En contribuant à ce projet, vous acceptez que vos contributions soient licenciées sous la [licence du projet](LICENSE).

---

**Merci de contribuer à Kadre!** 🎉

*Dernière mise à jour: 2026-06-26*
