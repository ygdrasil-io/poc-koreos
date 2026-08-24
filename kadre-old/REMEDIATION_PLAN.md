# Plan de Rémédiation Kadre - Roadmap 2026

## 🎯 Objectif Global

Atteindre **100% de parité avec winit v0.30.13** et résoudre tous les écarts identifiés dans le [Gap Analysis](GAP_ANALYSIS.md), tout en maintenant la stabilité et la compatibilité multiplateforme.

---

## 📅 Timeline & Phases

```
Q3 2026 (Juillet - Septembre)   → Phase 1: Fondations Critiques
├── Sprint 1 (Juillet)          → Résolution des écarts bloquants
├── Sprint 2 (Août)             → Amélioration de la qualité clavier
└── Sprint 3 (Septembre)        → Protocoles Wayland & IME

Q4 2026 (Octobre - Décembre)   → Phase 2: Parité Complète
├── Sprint 4 (Octobre)          → Complétion des enums
├── Sprint 5 (Novembre)         → Optimisation backends
└── Sprint 6 (Décembre)         → Tests & Documentation

Q1 2027 (Janvier - Mars)       → Phase 3: Excellence
├── Sprint 7 (Janvier)          → Fonctionnalités avancées
├── Sprint 8 (Février)          → Performances
└── Sprint 9 (Mars)             → Validation finale
```

---

## 🚀 Phase 1: Fondations Critiques (Q3 2026)

### Sprint 1 - Juillet 2026: **Écarts Bloquants & IME**

**Objectif**: Résoudre les écarts critiques qui bloquent l'adoption en production.

#### 🎯 Priorité P0 (Bloquants)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P0-001** | Implémenter `Window.ime_capabilities()` | Moyenne | 16 | Backend Team | ⏳ | Aucune |
| **P0-002** | Implémenter `Fullscreen.Exclusive` sur Win32 | Élevée | 24 | Win32 Expert | ⏳ | `ChangeDisplaySettingsExW` |
| **P0-003** | Corriger `ShowCursor` counter mismatch sur Win32 | Faible | 8 | Win32 Expert | ✅ | PR #274 |

#### 🎯 Priorité P1 (Haute)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P1-001** | Implémenter `xdg_activation_v1` pour Wayland | Élevée | 32 | Wayland Expert | ⏳ | libwayland >= 1.21 |
| **P1-002** | Implémenter `xdg_toplevel_icon_manager_v1` pour Wayland | Moyenne | 24 | Wayland Expert | ⏳ | libwayland >= 1.21 |
| **P1-003** | Ajouter support `ext_background_effect` pour Wayland | Élevée | 40 | Wayland Expert | ⏳ | Compositor support |

#### 📊 Livrables Sprint 1
- [ ] `ime_capabilities()` fonctionnel sur tous les backends
- [ ] `Fullscreen.Exclusive` fonctionnel sur Win32
- [ ] Protocoles Wayland de base implémentés
- [ ] Tests unitaires pour toutes les nouvelles fonctionnalités
- [ ] Documentation mise à jour

#### ✅ Critères d'Acceptation
- [ ] Tous les tests P0 passent
- [ ] Couverture de code > 90% pour les nouvelles fonctionnalités
- [ ] Documentation API générée (Dokka)
- [ ] Changelog mis à jour

---

### Sprint 2 - Août 2026: **Qualité Clavier & Modifiers**

**Objectif**: Améliorer la qualité de la gestion du clavier sur tous les backends.

#### 🎯 Priorité P1 (Haute)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P1-004** | Implémenter `textWithAllModifiers` proprement sur AppKit | Moyenne | 16 | AppKit Expert | ✅ | NSTextInputClient |
| **P1-005** | Implémenter `textWithAllModifiers` sur Win32 | Moyenne | 20 | Win32 Expert | ✅ | ToUnicodeEx + HKL |
| **P1-006** | Implémenter `keyWithoutModifiers` sur tous les backends | Moyenne | 24 | Keyboard Team | ✅ | P1-004, P1-005 |

#### 🎯 Priorité P2 (Moyenne)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P2-001** | Corriger le tracking left/right modifiers sur AppKit | Moyenne | 12 | AppKit Expert | ✅ | Aucune |
| **P2-002** | Corriger le tracking left/right modifiers sur Win32 | Moyenne | 16 | Win32 Expert | ✅ | Aucune |
| **P2-003** | Améliorer la détection des dead keys sur X11 | Faible | 8 | X11 Expert | ⏳ | XLookupString |
| **P2-004** | Améliorer la détection des dead keys sur Wayland | Faible | 8 | Wayland Expert | ⏳ | xkb_state |

#### 📊 Livrables Sprint 2
- [x] `textWithAllModifiers` implémenté sur AppKit et Win32
- [x] `keyWithoutModifiers` implémenté sur tous les backends (AppKit/Win32/UIKit natifs, autres fallback)
- [x] Tracking left/right modifiers corrigé sur AppKit et Win32
- [ ] Suite de tests étendue pour le clavier
- [ ] Benchmarks de performance clavier

#### ✅ Critères d'Acceptation
- [ ] Tous les tests de clavier passent
- [ ] Performance clavier < 1ms par événement
- [ ] Compatibilité avec tous les layouts clavier majeurs

---

### Sprint 3 - Septembre 2026: **Protocoles Wayland & Validation**

**Objectif**: Finaliser le support des protocoles Wayland et valider la Phase 1.

#### 🎯 Priorité P1 (Haute)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P1-007** | Intégrer `ext_background_effect` avec KWin | Élevée | 32 | Wayland Expert | ⏳ | P1-003 |
| **P1-008** | Implémenter la détection dynamique de protocoles Wayland | Moyenne | 16 | Wayland Expert | ⏳ | libwayland |
| **P1-009** | Corriger la géométrie des moniteurs sur Wayland | Moyenne | 20 | Wayland Expert | ⏳ | wl_output |

#### 🎯 Priorité P2 (Moyenne)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P2-005** | Optimiser les performances Wayland | Moyenne | 16 | Wayland Expert | ⏳ | P1-007, P1-008 |
| **P2-006** | Ajouter des tests d'intégration Wayland | Moyenne | 20 | QA Team | ⏳ | P1-007, P1-008 |
| **P2-007** | Documenter les limitations Wayland | Faible | 8 | Tech Writer | ⏳ | P1-007, P1-008 |

#### 📊 Livrables Sprint 3
- [ ] Tous les protocoles Wayland critiques implémentés
- [ ] Détection dynamique des capacités Wayland
- [ ] Suite de tests Wayland complète
- [ ] Documentation Wayland mise à jour
- [ ] Rapport de validation Phase 1

#### ✅ Critères d'Acceptation Phase 1
- [ ] Tous les écarts P0 résolus
- [ ] 95% des écarts P1 résolus
- [ ] Couverture de test > 95% pour les nouvelles fonctionnalités
- [ ] Documentation complète
- [ ] Pas de régression sur les fonctionnalités existantes

---

## 🎯 Phase 2: Parité Complète (Q4 2026)

### Sprint 4 - Octobre 2026: **Complétion des Enums & Curseur**

**Objectif**: Compléter les enums manquants et améliorer le support du curseur.

#### 🎯 Priorité P2 (Moyenne)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P2-008** | Ajouter les KeyCode manquants (7 valeurs) | Faible | 4 | Core Team | ⏳ | Aucune |
| **P2-009** | Ajouter les NamedKey manquants (4 valeurs) | Faible | 4 | Core Team | ⏳ | Aucune |
| **P2-010** | Implémenter `setCursorPosition` sur Wayland | Élevée | 24 | Wayland Expert | ⏳ | zwp_pointer_constraints_v1 |
| **P2-011** | Implémenter `setCursorGrab(Confined)` sur Wayland | Élevée | 24 | Wayland Expert | ⏳ | zwp_pointer_constraints_v1 |

#### 🎯 Priorité P3 (Basse)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P3-001** | Améliorer `setCursorPosition` sur Web (Pointer Lock) | Moyenne | 16 | Web Expert | ⏳ | Browser APIs |
| **P3-002** | Documenter les limitations du curseur par plateforme | Faible | 8 | Tech Writer | ⏳ | P2-010, P2-011 |

#### 📊 Livrables Sprint 4
- [ ] Tous les enums KeyCode et NamedKey complets
- [ ] Support du curseur amélioré sur Wayland
- [ ] Documentation des limitations du curseur
- [ ] Tests pour les nouvelles valeurs d'enum

---

### Sprint 5 - Novembre 2026: **Thème & Apparence**

**Objectif**: Résoudre les écarts liés au thème et à l'apparence.

#### 🎯 Priorité P2 (Moyenne)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P2-012** | Implémenter `setWindowIcon` sur Wayland | Moyenne | 20 | Wayland Expert | ⏳ | P1-002 |
| **P2-013** | Implémenter `setBlur` sur X11 | Élevée | 24 | X11 Expert | ⏳ | Compositor specific |
| **P2-014** | Implémenter `requestUserAttention` sur Wayland | Moyenne | 16 | Wayland Expert | ⏳ | P1-001 |
| **P2-015** | Implémenter `systemTheme()` sur X11 | Moyenne | 16 | X11 Expert | ⏳ | _GTK_THEME_VARIANT |

#### 🎯 Priorité P3 (Basse)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P3-003** | Améliorer `setTransparent` sur Web | Moyenne | 12 | Web Expert | ⏳ | CSS/Canvas |
| **P3-004** | Documenter les limitations thème par plateforme | Faible | 8 | Tech Writer | ⏳ | P2-012, P2-013 |

#### 📊 Livrables Sprint 5
- [ ] `setWindowIcon` fonctionnel sur Wayland
- [ ] `setBlur` fonctionnel sur X11
- [ ] `requestUserAttention` fonctionnel sur Wayland
- [ ] `systemTheme()` fonctionnel sur X11
- [ ] Documentation thème mise à jour

---

### Sprint 6 - Décembre 2026: **Optimisation & Tests**

**Objectif**: Optimiser les performances et finaliser les tests pour la Phase 2.

#### 🎯 Priorité P2 (Moyenne)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P2-016** | Optimiser les performances X11 | Moyenne | 16 | X11 Expert | ⏳ | Aucune |
| **P2-017** | Optimiser les performances Win32 | Moyenne | 16 | Win32 Expert | ⏳ | Aucune |
| **P2-018** | Ajouter des benchmarks de performance | Moyenne | 20 | Perf Team | ⏳ | Aucune |

#### 🎯 Priorité P3 (Basse)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P3-005** | Compléter la couverture de tests | Moyenne | 24 | QA Team | ⏳ | Aucune |
| **P3-006** | Mettre à jour toute la documentation | Moyenne | 20 | Tech Writer | ⏳ | Aucune |
| **P3-007** | Préparer la release v2.0.0 | Moyenne | 16 | Release Manager | ⏳ | P2-016, P2-017 |

#### 📊 Livrables Sprint 6
- [ ] Toutes les optimisations de performance appliquées
- [ ] Suite de benchmarks complète
- [ ] Couverture de test à 100% pour les fonctionnalités critiques
- [ ] Documentation complète et à jour
- [ ] Release v2.0.0 prête

#### ✅ Critères d'Acceptation Phase 2
- [ ] Tous les écarts P1 résolus
- [ ] 90% des écarts P2 résolus
- [ ] Couverture de test > 98%
- [ ] Performances optimisées sur tous les backends
- [ ] Documentation complète et précise
- [ ] Release v2.0.0 publiée

---

## 🌟 Phase 3: Excellence (Q1 2027)

### Sprint 7 - Janvier 2027: **Fonctionnalités Avancées**

**Objectif**: Implémenter des fonctionnalités avancées et expérimentales.

#### 🎯 Priorité P3 (Basse)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P3-008** | Ajouter le support gamepad sur AppKit | Élevée | 32 | AppKit Expert | ⏳ | Game Controller |
| **P3-009** | Ajouter le support gamepad sur Win32 | Élevée | 32 | Win32 Expert | ⏳ | XInput/DInput |
| **P3-010** | Ajouter le support gamepad sur Linux | Élevée | 40 | Linux Expert | ⏳ | libevdev |
| **P3-011** | Implémenter les gestes sur X11 | Élevée | 24 | X11 Expert | ⏳ | Touchpad drivers |

#### 📊 Livrables Sprint 7
- [ ] Support gamepad sur les plateformes desktop
- [ ] Support des gestes sur X11
- [ ] Documentation des nouvelles fonctionnalités

---

### Sprint 8 - Février 2027: **Performances & Évolutivité**

**Objectif**: Optimiser pour les applications haute performance.

#### 🎯 Priorité P3 (Basse)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P3-012** | Optimiser le traitement des événements | Élevée | 24 | Core Team | ⏳ | Aucune |
| **P3-013** | Réduire la latence d'entrée | Élevée | 32 | Perf Team | ⏳ | P3-012 |
| **P3-014** | Améliorer la gestion mémoire | Moyenne | 16 | Core Team | ⏳ | Aucune |
| **P3-015** | Ajouter le support multi-fenêtres optimisé | Élevée | 24 | Core Team | ⏳ | P3-012 |

#### 📊 Livrables Sprint 8
- [ ] Latence d'entrée < 16ms sur tous les backends
- [ ] Utilisation mémoire optimisée
- [ ] Support multi-fenêtres haute performance
- [ ] Benchmarks mis à jour

---

### Sprint 9 - Mars 2027: **Validation Finale**

**Objectif**: Valider la Phase 3 et préparer la release finale.

#### 🎯 Priorité P3 (Basse)

| ID | Tâche | Complexité | Effort (j/h) | Responsable | Statut | Dépendances |
|----|-------|------------|--------------|-------------|--------|--------------|
| **P3-016** | Tests d'intégration complets | Moyenne | 24 | QA Team | ⏳ | Aucune |
| **P3-017** | Tests de compatibilité multiplateforme | Moyenne | 20 | QA Team | ⏳ | Aucune |
| **P3-018** | Audit de sécurité | Élevée | 32 | Security Team | ⏳ | Aucune |
| **P3-019** | Préparer la release v2.1.0 | Moyenne | 16 | Release Manager | ⏳ | P3-016, P3-017 |

#### 📊 Livrables Sprint 9
- [ ] Tous les tests d'intégration passent
- [ ] Audit de sécurité complet
- [ ] Documentation finale
- [ ] Release v2.1.0 prête

#### ✅ Critères d'Acceptation Phase 3
- [ ] Tous les écarts P2 résolus
- [ ] 100% des écarts P3 résolus
- [ ] Couverture de test à 100%
- [ ] Performances optimales sur tous les backends
- [ ] Documentation complète et précise
- [ ] Release v2.1.0 publiée

---

## 📊 Ressources & Budget

### Équipe Recommandée

| Rôle | Nombre | Compétences Requises |
|------|--------|---------------------|
| **Tech Lead / Architecte** | 1 | Kotlin, Multiplateforme, Architecture |
| **Backend Experts** | 4 | AppKit, Win32, X11/Wayland, Web |
| **Core Team** | 2 | Kotlin, API Design, Events |
| **QA / Test Engineers** | 2 | Testing, Automation, CI/CD |
| **Technical Writers** | 1 | Documentation, API Docs |
| **Performance Engineers** | 1 | Profiling, Optimization |
| **Release Manager** | 1 | CI/CD, Release Management |

**Total**: 12 personnes (équivalent temps plein)

### Budget Estimé

| Catégorie | Coût (€) | Détails |
|----------|----------|---------|
| **Salaires (12 mois)** | 1,200,000 | 12 personnes × 80k€/an |
| **Infrastructure** | 50,000 | CI/CD, Cloud, Hardware |
| **Outils & Licences** | 20,000 | IDEs, Software, Services |
| **Formation** | 30,000 | Workshops, Conférences |
| **Contingence (10%)** | 130,000 | Imprévus |
| **Total** | **1,430,000** | |

---

## 🎯 Metriques de Succès

### KPIs Techniques

| Métrique | Cible Phase 1 | Cible Phase 2 | Cible Phase 3 |
|----------|---------------|---------------|---------------|
| Couverture winit API | 95% | 98% | 100% |
| Couverture de test | 90% | 95% | 98% |
| Latence événement moyenne | < 5ms | < 3ms | < 1ms |
| Utilisation mémoire | < 50MB | < 40MB | < 30MB |
| Temps de build CI | < 15min | < 10min | < 5min |

### KPIs Qualité

| Métrique | Cible |
|----------|-------|
| Taux de régression | < 1% |
| Temps moyen de résolution de bug | < 2 jours |
| Satisfaction développeurs (enquête) | > 4.5/5 |
| Nombre de contributions externes | > 50/mois |

---

## 📋 Plan de Communication

### Internes

| Audience | Fréquence | Canal | Contenu |
|----------|-----------|-------|---------|
| Équipe Core | Hebdomadaire | Réunions | Statut des sprints, blocages |
| Toute l'équipe | Bi-hebdomadaire | Slack/Teams | Mises à jour, annonces |
| Management | Mensuelle | Rapport | Statut global, budget, risques |

### Externes

| Audience | Fréquence | Canal | Contenu |
|----------|-----------|-------|---------|
| Communauté | Mensuelle | Blog, GitHub | Roadmap, releases, contributions |
| Utilisateurs | Trimestrielle | Newsletter | Nouvelles fonctionnalités, tutoriels |
| Partenaires | Trimestrielle | Appels | Collaboration, intégrations |

---

## ⚠️ Risques & Atténuation

### Risques Techniques

| Risque | Probabilité | Impact | Atténuation |
|--------|-------------|--------|--------------|
| Complexité Wayland sous-estimée | Moyenne | Élevé | Engager expert Wayland externe |
| Problèmes de compatibilité multiplateforme | Élevée | Moyen | Tests précoces et fréquents |
| Régressions de performance | Moyenne | Élevé | Benchmarks automatiques, CI |
| Retards dans les dépendances externes | Faible | Moyen | Suivi actif des dépendances |

### Risques Organisationnels

| Risque | Probabilité | Impact | Atténuation |
|--------|-------------|--------|--------------|
| Turnover de l'équipe | Moyenne | Élevé | Documentation complète, knowledge sharing |
| Priorités changeantes | Élevée | Moyen | Roadmap flexible, revues régulières |
| Budget insuffisant | Faible | Élevé | Plan de contingence, priorisation |
| Concurrence (autres bibliothèques) | Moyenne | Moyen | Différenciation, communauté active |

---

## 📚 Livrables Finaux

### Phase 1 (Q3 2026)
- [ ] Kadre v1.3.0 avec écarts P0 résolus
- [ ] Support Wayland amélioré
- [ ] Qualité clavier améliorée
- [ ] Documentation mise à jour
- [ ] Rapport de validation

### Phase 2 (Q4 2026)
- [ ] Kadre v2.0.0 avec parité complète
- [ ] Tous les enums complets
- [ ] Support curseur et thème amélioré
- [ ] Performances optimisées
- [ ] Documentation complète

### Phase 3 (Q1 2027)
- [ ] Kadre v2.1.0 avec fonctionnalités avancées
- [ ] Support gamepad multiplateforme
- [ ] Performances optimales
- [ ] Validation complète
- [ ] Documentation finale

---

## 🎉 Conclusion

Ce plan de rémédiation structuré permet à Kadre d'atteindre **100% de parité avec winit** en **9 mois** (3 phases), tout en améliorant la qualité, les performances et la documentation. 

**Prochaines Étapes**:
1. Valider le plan avec l'équipe et les stakeholders
2. Constituer les équipes et attribuer les responsabilités
3. Configurer les outils de suivi (Jira, GitHub Projects)
4. Lancer le Sprint 1 le 1er Juillet 2026

---

*Document version: 1.0*
*Last updated: 2026-06-26*
*Owner: Kadre Core Team*
*Status: Draft (Awaiting approval)*
