# 📁 Scripts de Gestion de Projet

Ce dossier contient des scripts utiles pour la gestion du projet Kadre, particulièrement pour le suivi des sprints et des tickets.

---

## 📜 **Fichiers Disponibles**

### 1. [`update_sprint_tracking.sh`](update_sprint_tracking.sh)
**Description**: Script pour générer un rapport de progrès des sprints à partir des issues GitHub.

**Utilisation**:
```bash
./update_sprint_tracking.sh
```

**Fonctionnalités**:
- Récupère les issues par sprint depuis GitHub
- Calcule les statistiques de progrès (tickets terminés, % de complétion)
- Génère un rapport Markdown détaillé
- Affiche les tickets par sprint avec leur statut

**Sortie**:
- Affiche le rapport dans la console
- Sauvegarde aussi dans `/tmp/sprint_report.md`

**Exemple de sortie**:
```
Sprint 1: 2/6 tickets (33%) - P0:3, P1:3, P2:0, P3:0
Sprint 2: 0/5 tickets (0%) - P0:0, P1:3, P2:2, P3:0
Sprint 3: 0/6 tickets (0%) - P0:0, P1:3, P2:3, P3:0
```

**Dépendances**:
- `gh` CLI (GitHub CLI) installé et authentifié
- Accès en lecture au dépôt ygdrasil-io/poc-koreos

---

## 🛠️ **Configuration Requise**

### 1. Installer GitHub CLI
```bash
# Sur macOS
brew install gh

# Sur Linux (Debian/Ubuntu)
sudo apt install gh

# Sur Windows (via Chocolatey)
choco install gh
```

### 2. Authentification
```bash
gh auth login
```

Suivez les instructions pour vous authentifier avec votre compte GitHub.

### 3. Vérifier l'accès
```bash
gh auth status
```

---

## 🚀 **Automatisation**

### Mise à jour quotidienne automatique
Pour mettre à jour automatiquement le tableau de bord chaque jour:

1. **Créer un cron job** (Linux/macOS):
```bash
# Éditer la crontab
crontab -e

# Ajouter cette ligne pour exécuter à 9h chaque jour
0 9 * * * cd /chemin/vers/poc-koreos && ./scripts/update_sprint_tracking.sh >> /tmp/sprint_daily_log.txt 2>&1
```

2. **Créer un GitHub Action** (pour mise à jour automatique dans le dépôt):
Créez un fichier `.github/workflows/update-sprint-tracking.yml`:

```yaml
name: Update Sprint Tracking

on:
  schedule:
    - cron: '0 9 * * *'  # Tous les jours à 9h UTC
  workflow_dispatch:     # Permet de déclencher manuellement

jobs:
  update-tracking:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup GitHub CLI
        run: |
          sudo apt update
          sudo apt install -y gh
      
      - name: Authenticate GitHub CLI
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | gh auth login --with-token
      
      - name: Run update script
        run: |
          chmod +x scripts/update_sprint_tracking.sh
          ./scripts/update_sprint_tracking.sh > /tmp/sprint_report.md
      
      - name: Commit changes
        run: |
          git config --global user.name "github-actions"
          git config --global user.email "actions@github.com"
          git add SPRINT_TRACKING.md
          git commit -m "chore: update sprint tracking [skip ci]" || echo "No changes to commit"
          git push
```

---

## 📊 **Intégration avec d'autres outils**

### 1. **GitHub Projects**
Quand GitHub Projects sera disponible via l'API CLI:
```bash
# Créer un projet
gh project create --title "Kadre Remediation" --owner ygdrasil-io

# Ajouter des issues au projet
gh project item add --project "Kadre Remediation" --issue 259
```

### 2. **Jira**
Pour une intégration avec Jira:
1. Utiliser l'intégration GitHub-Jira
2. Synchroniser automatiquement les issues
3. Utiliser les labels pour mapper les priorités

### 3. **Tableaux Kanban**
Des outils comme:
- [ZenHub](https://www.zenhub.com/)
- [Waffle](https://waffle.io/)
- [Project Board](https://github.com/features/project) (natif GitHub)

peuvent être utilisés pour visualiser les sprints.

---

## 🎯 **Bonnes Pratiques**

### 1. **Gestion des Tickets**
- Toujours utiliser les labels appropriés (`priority: PX`, `sprint: X`, etc.)
- Mettre à jour le statut régulièrement (`status: ready`, `status: in-progress`, etc.)
- Lier les PRs aux tickets avec `Fixes #XXX`

### 2. **Mise à jour du Tableau de Bord**
- Mettre à jour manuellement après chaque standup
- Exécuter le script de mise à jour au moins une fois par jour
- Vérifier les dépendances entre tickets

### 3. **Revue de Sprint**
- À la fin de chaque sprint, faire une revue complète
- Identifier les tickets non terminés et les déplacer au sprint suivant
- Mettre à jour les métriques de vitesse

---

## 🐛 **Dépannage**

### Problème: `gh: command not found`
**Solution**: Installer GitHub CLI (voir section Configuration Requise)

### Problème: `Authentication Error`
**Solution**: Exécuter `gh auth login` et suivre les instructions

### Problème: `Resource not accessible by integration`
**Solution**: 
- Vérifier que vous avez les permissions sur le dépôt
- Utiliser un token personnel avec les bonnes permissions
- Pour les GitHub Actions, utiliser `secrets.GITHUB_TOKEN`

### Problème: Les labels n'existent pas
**Solution**: Créer les labels manquants avec:
```bash
# Exemple pour créer un label
gh label create "priority: P0" --color "#FF0000" --description "Critique / Blocant"
```

Voir [`create_labels.sh`](../create_labels.sh) pour la liste complète des labels.

---

## 📚 **Documentation Connexe**

- [SPRINT_TRACKING.md](../SPRINT_TRACKING.md) - Tableau de bord principal
- [REMEDIATION_PLAN.md](../REMEDIATION_PLAN.md) - Plan de rémédiation détaillé
- [GAP_ANALYSIS.md](../GAP_ANALYSIS.md) - Analyse des écarts

---

## 🔄 **Historique des Versions**

| Version | Date | Auteur | Changements |
|---------|------|--------|------------|
| 1.0 | 2026-06-26 | - | Création initiale |

---

*Dernière mise à jour: 2026-06-26*
