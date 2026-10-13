# Win32 Phase 0 — Audit KFFI

Modèle : `WAYLAND-PHASE-0-KFFI-AUDIT.md`. Ce document consigne l'artifact KFFI réellement consommé par la phase 0, les verdicts par domaine et les gaps restants. Il ne modifie ni l'API publique, ni le statut des capabilities.

## Artifact consommé

| Élément | Valeur au moment de la phase 0 |
|---|---|
| Dépôt KFFI | `kffi` (fork `ygdrasil-io/kffi`, amont `Graphiks-org/kffi`) |
| Branche | `feat/win32-managed-window-frontier` (depuis `origin/master`, linéaire, PR `Graphiks-org/kffi#118`) |
| Révision livrée | `759c9f5` — couche managée complète (bridge ABI `WNDPROC` 4 arguments, structs, fenêtre de contrôle) |
| Publication locale | `org.graphiks:kffi-win32-jvm:1.0.0-SNAPSHOT` dans `~/.m2` (12 octobre 2026, 19:40) |
| Résolution Kadre | `./gradlew :kadre:backend:win32:dependencyInsight --configuration jvmRuntimeClasspath --dependency kffi-win32-jvm` |
| Dépôts de résolution | Central Portal Snapshots (settings hermétiques) + `mavenLocal` **uniquement en dev local** via init-script (voir ruling B1/B2 du registre d'exécution) ; après merge de la branche, le job `snapshot` de `publish.yml` publie `1.0.0-SNAPSHOT` sur Central et la résolution CI redevient exclusivement distante |

Attention : le snapshot Central #49 (12 octobre 15:36) ne contient PAS la couche managée ; toute exécution Kadre de la phase 0 avant le merge résout donc `mavenLocal`. La gate CI `windows-contracts` doit rester inactive (workflow non ajouté ou PR non ouverte) tant que le snapshot Central n'embarque pas la couche managée.

## Verdicts par domaine

| Domaine | État avant la phase 0 | Verdict après la phase 0 |
|---|---|---|
| Constantes/enums Windows | généré (`org.graphiks.kffi.win32.generated`), présent | inchangé, inchangé |
| Downcalls `user32`/`kernel32`/`gdi32`/`dwmapi` | générés, init paresseuse `init()` + `check(_initialized)` | inchangé ; consommés par la couche managée |
| Layouts `WNDCLASSEXW` / `MSG` | absents | fermé côté code, testé portable — `managed/Win32Structs`, offsets Win64 (sizeof 80 / 48) ; validation comportementale Windows en attente d'exécution |
| Upcall `WNDPROC` managé révocable | absent | fermé côté code — `managed/Win32WndProcBridge` (trampoline process-lifetime ABI `WNDPROC` native 4 arguments `(JIJJ)J`, route unique) + `managed/Win32ManagedFunctions.wndProc` (registration révocable, quiescence, exceptions confinées) ; **preuve O3 Windows en attente d'exécution** |
| Fenêtre de contrôle interne | absente | fermée côté code — `managed/Win32ControlWindow` (popup jamais affichée, `DestroyWindow` + close idempotents, échec actionnable hors Windows) ; **preuve O3 Windows en attente d'exécution** |
| Dépendance au cœur `kffi` | absente | fermée — `api(project(":kffi"))` dans `kffi-win32/build.gradle.kts` |
| Preuve native Windows | tests d'intégration gated `KFFI_WIN32_INTEGRATION` | étendus — `Win32ControlWindowIntegrationTest` (inject → poll → dispatch → close) |

## Gaps restants (non bloquants pour la phase 0)

- Layouts de structs au-delà de `WNDCLASSEXW`/`MSG` (DPI, moniteurs, IMM/TSF, OLE, raw input) — phases 3 à 8 de `WIN32-IMPLEMENTATION-ROADMAP.md`.
- **Preuve O3 Windows** : aucun verdict « fermé » de ce document ne vaut preuve d'exécution ; l'intégration KFFI (`Win32ControlWindowIntegrationTest`) et les preuves contractuelles Kadre (`W32-001..003`) s'exécutent exclusivement sur runner/machine Windows et conditionnent l'activation de la gate.
- Vtables COM et références WinRT (OLE drop, capture) — phases 7 et 11 ; l'audit de `KFFI-REQUIREMENTS.md` sera complété à ce moment.
- Extension du générateur kextract aux structs/typedefs de callbacks — suivre la voie manuscrite tant qu'elle tient, documenter toute divergence.
- Preuve `O3` Windows : l'intégration KFFI (`Win32ControlWindowIntegrationTest`) et les preuves contractuelles Kadre (`W32-001..003`) s'exécutent exclusivement sur runner/machine Windows.

## Commande de vérification

```bash
./gradlew :kadre:backend:win32:dependencyInsight --configuration jvmRuntimeClasspath --dependency kffi-win32-jvm
```

Après merge KFFI et publication snapshot, la même commande sans `mavenLocal` doit résoudre un snapshot Central postérieur au commit `11e1ab0`.
