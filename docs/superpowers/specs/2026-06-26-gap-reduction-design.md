# Gap Reduction — Kadre 100% winit Parity

> Status: Design approved  
> Target: Kadre v1.2.0  
> Date: 2026-06-26

## 1. Objectif

Atteindre 100% de parité winit v0.30.13 sur les 7 backends Kadre, en fermant ~84 gaps documentés dans `docs/features/gaps.md`.

**Définition de "100%" :** Chaque API winit est présente sur chaque backend Kadre, avec l'un des trois statuts :
- **REAL** — implémentée avec l'effet natif attendu
- **NO-OP documenté** — accepte l'appel, retourne un résultat valide, pas d'effet car la plateforme ne le supporte pas. Documenté en KDoc + gaps.md
- **Unsupported** — retourne `WindowRequestResult.Failure(Unsupported)` (ex: `CursorGrab.Confined` sur AppKit, parité winit)

## 2. Approche : Pipeline hybride (3 vagues)

```
V1: C1 (enums) + C2 (Web DOM) en //
     ↓
V2: C3 (Keyboard) + C4 (Window API) + C5 (Events) + C6 (Mobile no-op) en //
     ↓
V3: C7 (ABI + tests) + C8 (Docs + CHANGELOG) en //
```

## 3. Architecture des agents

### Vague 1 — Indépendants

| Agent | Scope | Modules | Gaps |
|-------|-------|---------|:----:|
| **C1 — Enums** | Compléter KeyCode, NamedKey, NativeKeyCode : touches IME/Asian, pavé numérique étendu, media/TV | kadre-core | ~25 |
| **C2 — Web DOM** | Implémenter `JsWebDomBridge` et `WasmJsWebDomBridge` : Pointer Lock API, CSS `pointer-events`, cursor grab | kadre-web-common, kadre-js, kadre-wasm | 4 |

### Vague 2 — Dépendent de C1

| Agent | Scope | Modules | Gaps |
|-------|-------|---------|:----:|
| **C3 — Keyboard runtime** | X11: `XLookupString` → `text`; Wayland: `xkb_state_key_get_utf8` → `text`; AppKit: layout non-QWERTY; Win32: scancode > VK; `ModifierKeys` left/right sur tous | kadre-x11, kadre-wayland, kadre-appkit, kadre-win32 | ~8 |
| **C4 — Window API** | `ownedDisplayHandle()` non-null; finaliser `dragWindow`/`dragResizeWindow`/`showWindowMenu`; appearance manquante (blur X11/Win32, icon Wayland, attention Wayland, contentProtected X11/Wayland) | kadre-core, kadre-appkit, kadre-win32, kadre-x11, kadre-wayland | ~12 |
| **C5 — Events** | `ThemeChanged`: X11, Web, Android, UIKit; `Occluded`: Win32, Wayland, Android; DnD: finaliser AppKit; Gestures: X11, Wayland, Web, Android; `TouchpadPressure`: autres backends | kadre-x11, kadre-wayland, kadre-web-common, kadre-android, kadre-uikit, kadre-win32 | ~15 |
| **C6 — Mobile no-op** | UIKit + Android: implémenter toutes les APIs cursor/window-state/theme/fullscreen en no-op documenté avec `WindowRequestResult` + test unitaire | kadre-uikit, kadre-android | ~20 |

### Vague 3 — Intégration

| Agent | Scope | Modules |
|-------|-------|---------|
| **C7 — ABI + Tests** | Régénérer ABI dumps (`*.api`, `*.klib.api`); linter; tests unitaires pour nouvelles APIs no-op; `./gradlew build` complet | Tous |
| **C8 — Docs** | Mettre à jour `gaps.md` → 100%; CHANGELOG.md section Added; docs/features/*.md; MkDocs | docs/ |

## 4. Critères de succès

1. `./gradlew updateKotlinAbi` passe sans diff sur tous les modules
2. Tous les tests existants passent : `./gradlew :kadre-core:jvmTest :kadre-appkit:jvmTest :kadre:jvmTest :kadre:iosSimulatorArm64Test`
3. Chaque nouvelle API no-op a un test unitaire prouvant qu'elle ne crashe pas
4. `docs/features/gaps.md` montre 0 gap réel, avec les no-op documentés listés comme résolus
5. CHANGELOG.md contient une entrée `## [1.2.0]` listant tous les gaps fermés

## 5. Workflow d'exécution

1. **Pre-check** : `git pull --rebase`, CI verte sur master
2. **Dispatch** : tous les agents de la vague lancés simultanément
3. **Collecte** : chaque agent travaille sur une branche `gap/<agent-id>`
4. **Gate** : merge → `./gradlew build` → CI check → vague suivante
5. **Final** : tag `v1.2.0`, publication Maven Central

## 6. Non couvert

- Environnements qui ne tournent pas en CI : Wayland headless weston (smoke build seulement), Windows (compilation vérifiée, pas d'exécution), Android/iOS (compilation vérifiée, exécution manuelle)
- Nouveaux tests d'intégration qui nécessitent un GPU
- Backport JDK < 25

## 7. Risques

| Risque | Probabilité | Mitigation |
|--------|:----------:|-----------|
| Conflit git entre agents V2 touchant kadre-core | Faible | Chaque agent ajoute des `actual` dans des fichiers différents ; C7 résout les conflits |
| XLookupString / xkb_state_key_get_utf8 non disponible dans kextract | Moyen | Fallback: implémenter le binding FFM manuellement dans le backend |
| Protocoles Wayland manquants (xdg_activation_v1, icon_manager) | Moyen | Fallback: NO-OP documenté avec commentaire "nécessite protocole optionnel" |
| CI instable sur Linux | Moyen | Smoke build seulement ; test manuel sur Ubuntu 24.04 |
