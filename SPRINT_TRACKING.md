# 📊 Suivi des Sprints - Kadre Remediation Plan

## 🎯 **Objectif Global**
Atteindre **100% de parité avec winit v0.30.13** en suivant le [REMEDIATION_PLAN.md](REMEDIATION_PLAN.md).

---

## 📅 **Calendrier des Sprints**

| Sprint | Période | Phase | Objectif | Statut |
|--------|---------|-------|----------|--------|
| **Sprint 1** | Juillet 2026 | Phase 1 | Écarts Bloquants & IME | ⏳ En cours |
| **Sprint 2** | Août 2026 | Phase 1 | Qualité Clavier & Modifiers | ⏳ Prêt |
| **Sprint 3** | Septembre 2026 | Phase 1 | Protocoles Wayland & Validation | ⏳ Prêt |
| Sprint 4 | Octobre 2026 | Phase 2 | Complétion des Enums | 📋 Planifié |
| Sprint 5 | Novembre 2026 | Phase 2 | Thème & Apparence | 📋 Planifié |
| Sprint 6 | Décembre 2026 | Phase 2 | Optimisation & Tests | 📋 Planifié |

---

## 📈 **Statistiques Globales**

### **Tickets par Sprint**
| Sprint | Total | P0 | P1 | P2 | P3 | Terminés | % Complet |
|--------|-------|----|----|----|----|----------|------------|
| Sprint 1 | 6 | 3 | 3 | 0 | 0 | 0 | 0% |
| Sprint 2 | 5 | 0 | 3 | 2 | 0 | 0 | 0% |
| Sprint 3 | 6 | 0 | 3 | 3 | 0 | 0 | 0% |
| **Total Phase 1** | **17** | **3** | **9** | **5** | **0** | **0** | **0%** |

### **Tickets par Catégorie**
| Catégorie | Total | Terminés | % Complet |
|-----------|-------|----------|------------|
| Window API | 2 | 0 | 0% |
| Events | 0 | 0 | - |
| Keyboard | 6 | 0 | 0% |
| Cursor | 1 | 0 | 0% |
| Theme | 5 | 0 | 0% |
| IME | 1 | 0 | 0% |
| Backend | 2 | 0 | 0% |
| Tests | 1 | 0 | 0% |
| Documentation | 0 | 0 | - |

### **Tickets par Backend**
| Backend | Total | Terminés | % Complet |
|---------|-------|----------|------------|
| Core | 2 | 0 | 0% |
| AppKit | 2 | 0 | 0% |
| Win32 | 3 | 0 | 0% |
| Wayland | 7 | 0 | 0% |
| X11 | 0 | 0 | - |
| Web | 0 | 0 | - |
| Android | 0 | 0 | - |
| UIKit | 0 | 0 | - |

---

## 📋 **Sprint 1 - Juillet 2026**
**Phase**: Phase 1 - Fondations Critiques  
**Objectif**: Résoudre les écarts bloquants et critiques  
**Statut**: ⏳ En cours  
**Progrès**: 0/6 tickets (0%)

### **Tickets P0 (Critique/Blocant)**

| # | Titre | Priorité | Catégorie | Backend | Effort | Statut | Assigné à | DoD |
|---|-------|----------|----------|---------|--------|--------|-----------|-----|
| [#259](https://github.com/ygdrasil-io/poc-koreos/issues/259) | Implémenter Window.ime_capabilities() | P0 | IME | Core | 16 j/h | ⏳ Backlog | - | ❌ |
| [#260](https://github.com/ygdrasil-io/poc-koreos/issues/260) | Implémenter Fullscreen.Exclusive sur Win32 | P0 | Window API | Win32 | 24 j/h | ⏳ Backlog | - | ❌ |
| [#261](https://github.com/ygdrasil-io/poc-koreos/issues/261) | Corriger ShowCursor counter mismatch sur Win32 | P0 | Cursor | Win32 | 8 j/h | ⏳ Backlog | - | ❌ |

### **Tickets P1 (Haute)**

| # | Titre | Priorité | Catégorie | Backend | Effort | Statut | Assigné à | DoD |
|---|-------|----------|----------|---------|--------|--------|-----------|-----|
| [#262](https://github.com/ygdrasil-io/poc-koreos/issues/262) | Implémenter xdg_activation_v1 pour Wayland | P1 | Theme | Wayland | 32 j/h | ⏳ Backlog | - | ❌ |
| [#263](https://github.com/ygdrasil-io/poc-koreos/issues/263) | Implémenter xdg_toplevel_icon_manager_v1 pour Wayland | P1 | Theme | Wayland | 24 j/h | ⏳ Backlog | - | ❌ |
| [#264](https://github.com/ygdrasil-io/poc-koreos/issues/264) | Ajouter support ext_background_effect pour Wayland | P1 | Theme | Wayland | 40 j/h | ⏳ Backlog | - | ❌ |

### **Burndown Chart Sprint 1**
```
100% │
    │
 75% │
    │
 50% │
    │
 25% │
    │
  0% └─────────────────────────────────────▶
     Juillet 1   Juillet 8   Juillet 15  Juillet 22  Juillet 29
```

**Tâches restantes**: 6/6  
**Effort restant**: 140 j/h  
**Vitesse actuelle**: 0 j/h/jour

---

## 📋 **Sprint 2 - Août 2026**
**Phase**: Phase 1 - Fondations Critiques  
**Objectif**: Améliorer la qualité du clavier sur tous les backends  
**Statut**: ⏳ Prêt  
**Progrès**: 0/5 tickets (0%)

### **Tickets P1 (Haute)**

| # | Titre | Priorité | Catégorie | Backend | Effort | Statut | Assigné à | DoD |
|---|-------|----------|----------|---------|--------|--------|-----------|-----|
| [#265](https://github.com/ygdrasil-io/poc-koreos/issues/265) | Implémenter textWithAllModifiers sur AppKit | P1 | Keyboard | AppKit | 16 j/h | ⏳ Backlog | - | ❌ |
| [#266](https://github.com/ygdrasil-io/poc-koreos/issues/266) | Implémenter textWithAllModifiers sur Win32 | P1 | Keyboard | Win32 | 20 j/h | ⏳ Backlog | - | ❌ |
| [#267](https://github.com/ygdrasil-io/poc-koreos/issues/267) | Implémenter keyWithoutModifiers sur tous les backends | P1 | Keyboard | Core | 24 j/h | ⏳ Backlog | - | ❌ |

### **Tickets P2 (Moyenne)**

| # | Titre | Priorité | Catégorie | Backend | Effort | Statut | Assigné à | DoD |
|---|-------|----------|----------|---------|--------|--------|-----------|-----|
| [#268](https://github.com/ygdrasil-io/poc-koreos/issues/268) | Corriger le tracking left/right modifiers sur AppKit | P2 | Keyboard | AppKit | 12 j/h | ⏳ Backlog | - | ❌ |
| [#269](https://github.com/ygdrasil-io/poc-koreos/issues/269) | Corriger le tracking left/right modifiers sur Win32 | P2 | Keyboard | Win32 | 16 j/h | ⏳ Backlog | - | ❌ |

### **Burndown Chart Sprint 2**
```
100% │
    │
 75% │
    │
 50% │
    │
 25% │
    │
  0% └─────────────────────────────────────▶
     Août 1     Août 8    Août 15   Août 22   Août 29
```

**Tâches restantes**: 5/5  
**Effort restant**: 88 j/h  
**Vitesse actuelle**: 0 j/h/jour

---

## 📋 **Sprint 3 - Septembre 2026**
**Phase**: Phase 1 - Fondations Critiques  
**Objectif**: Finaliser le support des protocoles Wayland et valider la Phase 1  
**Statut**: ⏳ Prêt  
**Progrès**: 0/6 tickets (0%)

### **Tickets P1 (Haute)**

| # | Titre | Priorité | Catégorie | Backend | Effort | Statut | Assigné à | DoD |
|---|-------|----------|----------|---------|--------|--------|-----------|-----|
| [#270](https://github.com/ygdrasil-io/poc-koreos/issues/270) | Intégrer ext_background_effect avec KWin | P1 | Theme | Wayland | 32 j/h | ⏳ Backlog | - | ❌ |
| [#271](https://github.com/ygdrasil-io/poc-koreos/issues/271) | Implémenter la détection dynamique de protocoles Wayland | P1 | Backend | Wayland | 16 j/h | ⏳ Backlog | - | ❌ |
| [#272](https://github.com/ygdrasil-io/poc-koreos/issues/272) | Corriger la géométrie des moniteurs sur Wayland | P1 | Window API | Wayland | 20 j/h | ⏳ Backlog | - | ❌ |

### **Tickets P2 (Moyenne)**

| # | Titre | Priorité | Catégorie | Backend | Effort | Statut | Assigné à | DoD |
|---|-------|----------|----------|---------|--------|--------|-----------|-----|
| [#273](https://github.com/ygdrasil-io/poc-koreos/issues/273) | Optimiser les performances Wayland | P2 | Backend | Wayland | 16 j/h | ⏳ Backlog | - | ❌ |
| [#274](https://github.com/ygdrasil-io/poc-koreos/issues/274) | Ajouter des tests d'intégration Wayland | P2 | Tests | Wayland | 20 j/h | ⏳ Backlog | - | ❌ |
| [#275](https://github.com/ygdrasil-io/poc-koreos/issues/275) | Documenter les limitations Wayland | P2 | Documentation | Wayland | 8 j/h | ⏳ Backlog | - | ❌ |

### **Burndown Chart Sprint 3**
```
100% │
    │
 75% │
    │
 50% │
    │
 25% │
    │
  0% └─────────────────────────────────────▶
     Sept 1   Sept 8   Sept 15  Sept 22  Sept 29
```

**Tâches restantes**: 6/6  
**Effort restant**: 112 j/h  
**Vitesse actuelle**: 0 j/h/jour

---

## 📊 **Tableau de Bord par Priorité**

### **P0 - Critique/Blocant (3 tickets)**
| # | Titre | Sprint | Backend | Effort | Statut |
|---|-------|--------|---------|--------|--------|
| [#259](https://github.com/ygdrasil-io/poc-koreos/issues/259) | Implémenter Window.ime_capabilities() | 1 | Core | 16 j/h | ⏳ Backlog |
| [#260](https://github.com/ygdrasil-io/poc-koreos/issues/260) | Implémenter Fullscreen.Exclusive sur Win32 | 1 | Win32 | 24 j/h | ⏳ Backlog |
| [#261](https://github.com/ygdrasil-io/poc-koreos/issues/261) | Corriger ShowCursor counter mismatch sur Win32 | 1 | Win32 | 8 j/h | ⏳ Backlog |

**Progrès P0**: 0/3 (0%)  
**Effort restant P0**: 48 j/h

---

### **P1 - Haute (9 tickets)**
| # | Titre | Sprint | Backend | Effort | Statut |
|---|-------|--------|---------|--------|--------|
| [#262](https://github.com/ygdrasil-io/poc-koreos/issues/262) | Implémenter xdg_activation_v1 pour Wayland | 1 | Wayland | 32 j/h | ⏳ Backlog |
| [#263](https://github.com/ygdrasil-io/poc-koreos/issues/263) | Implémenter xdg_toplevel_icon_manager_v1 pour Wayland | 1 | Wayland | 24 j/h | ⏳ Backlog |
| [#264](https://github.com/ygdrasil-io/poc-koreos/issues/264) | Ajouter support ext_background_effect pour Wayland | 1 | Wayland | 40 j/h | ⏳ Backlog |
| [#265](https://github.com/ygdrasil-io/poc-koreos/issues/265) | Implémenter textWithAllModifiers sur AppKit | 2 | AppKit | 16 j/h | ⏳ Backlog |
| [#266](https://github.com/ygdrasil-io/poc-koreos/issues/266) | Implémenter textWithAllModifiers sur Win32 | 2 | Win32 | 20 j/h | ⏳ Backlog |
| [#267](https://github.com/ygdrasil-io/poc-koreos/issues/267) | Implémenter keyWithoutModifiers sur tous les backends | 2 | Core | 24 j/h | ⏳ Backlog |
| [#270](https://github.com/ygdrasil-io/poc-koreos/issues/270) | Intégrer ext_background_effect avec KWin | 3 | Wayland | 32 j/h | ⏳ Backlog |
| [#271](https://github.com/ygdrasil-io/poc-koreos/issues/271) | Implémenter la détection dynamique de protocoles Wayland | 3 | Wayland | 16 j/h | ⏳ Backlog |
| [#272](https://github.com/ygdrasil-io/poc-koreos/issues/272) | Corriger la géométrie des moniteurs sur Wayland | 3 | Wayland | 20 j/h | ⏳ Backlog |

**Progrès P1**: 0/9 (0%)  
**Effort restant P1**: 224 j/h

---

### **P2 - Moyenne (5 tickets)**
| # | Titre | Sprint | Backend | Effort | Statut |
|---|-------|--------|---------|--------|--------|
| [#268](https://github.com/ygdrasil-io/poc-koreos/issues/268) | Corriger le tracking left/right modifiers sur AppKit | 2 | AppKit | 12 j/h | ⏳ Backlog |
| [#269](https://github.com/ygdrasil-io/poc-koreos/issues/269) | Corriger le tracking left/right modifiers sur Win32 | 2 | Win32 | 16 j/h | ⏳ Backlog |
| [#273](https://github.com/ygdrasil-io/poc-koreos/issues/273) | Optimiser les performances Wayland | 3 | Wayland | 16 j/h | ⏳ Backlog |
| [#274](https://github.com/ygdrasil-io/poc-koreos/issues/274) | Ajouter des tests d'intégration Wayland | 3 | Wayland | 20 j/h | ⏳ Backlog |
| [#275](https://github.com/ygdrasil-io/poc-koreos/issues/275) | Documenter les limitations Wayland | 3 | Wayland | 8 j/h | ⏳ Backlog |

**Progrès P2**: 0/5 (0%)  
**Effort restant P2**: 72 j/h

---

## 🎯 **Objectifs par Phase**

### **Phase 1: Fondations Critiques (Sprints 1-3)**
- **Objectif**: Résoudre tous les écarts P0 et 95% des écarts P1
- **Tickets**: 17 (3 P0 + 9 P1 + 5 P2)
- **Effort total**: 444 j/h
- **Progrès**: 0/17 (0%)
- **Effort restant**: 444 j/h

### **Phase 2: Parité Complète (Sprints 4-6)**
- **Objectif**: Atteindre 100% de parité avec winit
- **Tickets**: À planifier (voir REMEDIATION_PLAN.md)
- **Effort estimé**: ~300 j/h
- **Statut**: 📋 Planifié

### **Phase 3: Excellence (Sprints 7-9)**
- **Objectif**: Fonctionnalités avancées et optimisation
- **Tickets**: À planifier (voir REMEDIATION_PLAN.md)
- **Effort estimé**: ~200 j/h
- **Statut**: 📋 Planifié

---

## 📈 **Métriques Clés**

### **Vitesse de l'Équipe**
- **Vitesse actuelle**: 0 j/h/jour
- **Vitesse cible**: 20 j/h/jour (pour 10 développeurs)
- **Capacité mensuelle**: ~400 j/h/mois

### **Prévisions**
- **Phase 1 (3 mois)**: 444 j/h nécessaires, capacité: 1200 j/h → **Sous capacité** ✅
- **Phase 2 (3 mois)**: ~300 j/h nécessaires, capacité: 1200 j/h → **Sous capacité** ✅
- **Phase 3 (3 mois)**: ~200 j/h nécessaires, capacité: 1200 j/h → **Sous capacité** ✅

### **Risques**
- **Risque principal**: Complexité sous-estimée des protocoles Wayland
- **Atténuation**: Prioriser les tickets Wayland, engager un expert si nécessaire
- **Autres risques**: Voir [REMEDIATION_PLAN.md#risques--atténuation](REMEDIATION_PLAN.md#risques--atténuation)

---

## 🛠️ **Comment Utiliser Ce Tableau de Bord**

### **Pour les Développeurs**
1. **Choisir un ticket**: Sélectionnez un ticket avec le label `status: ready`
2. **S'assigner**: Ajoutez-vous comme assigné au ticket
3. **Changer le statut**: Passez le ticket à `status: in-progress`
4. **Travailler**: Suivez les tâches dans la checklist
5. **Créer une PR**: Liez la PR au ticket avec `Fixes #XXX`
6. **Revue**: Passez le ticket à `status: review` pendant la revue
7. **Terminer**: Passez le ticket à `status: done` après fusion

### **Pour les Chefs de Projet**
1. **Mettre à jour le tableau**: Après chaque standup, mettez à jour les statuts
2. **Suivre le burndown**: Vérifiez que le burndown suit la tendance attendue
3. **Identifier les blocages**: Repérez les tickets bloqués (`status: blocked`)
4. **Réallouer les ressources**: Si nécessaire, réallouez les développeurs
5. **Préparer les rétrospectives**: Utilisez les données pour les rétrospectives de sprint

### **Pour les Stakeholders**
1. **Voir le progrès**: Consultez le % de complétion global
2. **Comprendre les priorités**: Voir les tickets P0/P1 en cours
3. **Estimer les délais**: Utilisez les métriques pour estimer les dates de livraison

---

## 📅 **Calendrier des Réunions**

| Réunion | Fréquence | Jour/Heure | Participants | Objectif |
|---------|-----------|------------|--------------|----------|
| **Standup Quotidien** | Quotidien | 10:00 | Équipe Dev | Mise à jour du statut des tickets |
| **Revue de Sprint** | Hebdomadaire | Vendredi 14:00 | Équipe + PO | Revue des tickets terminés |
| **Planification Sprint** | Mensuelle | Lundi avant le sprint | Équipe + PO | Planifier le sprint suivant |
| **Rétrospective** | Mensuelle | Vendredi après le sprint | Équipe | Amélioration des processus |
| **Revue avec Stakeholders** | Mensuelle | Dernier jeudi du mois | Équipe + Stakeholders | Revue du progrès global |

---

## 📚 **Ressources**

- [GAP_ANALYSIS.md](GAP_ANALYSIS.md) - Analyse complète des écarts
- [REMEDIATION_PLAN.md](REMEDIATION_PLAN.md) - Plan de rémédiation détaillé
- [Labels GitHub](https://github.com/ygdrasil-io/poc-koreos/labels) - Tous les labels utilisés
- [Issues GitHub](https://github.com/ygdrasil-io/poc-koreos/issues) - Tous les tickets

---

## 🔄 **Mise à Jour du Tableau de Bord**

Ce fichier doit être mis à jour **quotidiennement** par le chef de projet ou un développeur désigné.

### **Checklist de Mise à Jour**
- [ ] Vérifier les nouveaux tickets créés
- [ ] Mettre à jour les statuts des tickets
- [ ] Mettre à jour les assignees
- [ ] Mettre à jour les burndown charts
- [ ] Mettre à jour les métriques
- [ ] Vérifier les dépendances entre tickets
- [ ] Identifier les blocages

### **Automatisation Future**
Pour automatiser ce suivi, nous pourrions:
1. Créer un script qui génère ce fichier à partir des issues GitHub
2. Utiliser GitHub Projects (quand disponible)
3. Intégrer avec un outil de gestion de projet (Jira, etc.)

---

*Dernière mise à jour: 2026-06-26*  
*Prochaine mise à jour: Après le standup du 2026-07-01*  
*Responsable: Chef de Projet Kadre*
