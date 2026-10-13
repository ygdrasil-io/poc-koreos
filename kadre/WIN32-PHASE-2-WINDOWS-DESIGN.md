# Win32 Phase 2 — Fenêtres, fermeture et handle emprunté

## But

Design ciblé de la deuxième tranche Windows (`WIN32-IMPLEMENTATION-ROADMAP.md` §5, Phase 2) : `requestWindow`/`OpenedHere` réels sur Windows — plusieurs fenêtres top-level dans la même session, `WM_CLOSE` traduit en décision de fermeture, transitions terminales ordonnées, `RuntimeDesktopNativeWindowHandle.Win32` sous lease, règle de dernière fenêtre. Exclusions : métriques/redraw/DPI et `platformAccess` (phase 3, états `Unsupported` exacts), input (phase 4).

## Préconditions KFFI

Une seule extension : le bridge `Win32WndProcBridge` passe d'une route unique au **routage multi-fenêtres** (D1) — contexte de création par thread + table permanente HWND→route. Aucun changement du moteur kffi core ; downcalls `CreateWindowExW`/`ShowWindow`/`DestroyWindow`/`IsWindow` déjà générés.

## Décisions de la tranche

- **D1 — Routage multi-fenêtres (pattern toolkit classique).** `CreateWindowExW` s'exécute sur le thread UI ; `WM_NCCREATE`/`WM_CREATE` arrivent **pendant** l'appel, avant le retour du HWND. Le bridge pose la route dans un slot par thread avant l'appel : les messages de création routent via ce slot ; après le retour, la table permanente HWND→route prend le relais. Le retrait retire l'entrée de table **avant** `DestroyWindow` — une adresse réutilisée ne route jamais vers l'ancien owner (décisions phase 0 D2).
- **D2 — Surface minimale native.** Chaque fenêtre expose une `HostSurface` HWND-backed dès l'ouverture : le gate « `Closing` → surface détachée → `Closed` » est prouvable tel quel. `metrics`, `redraw`, `cursor`, `platformAccess` restent `Unsupported(...)` exacts (phase 3).
- **D3 — Décision de fermeture.** `WM_CLOSE` (natif) et `window.close()` (applicatif) convergent vers la décision runtime : interception applicable (`Accepted` → closing effectif), refus (fermeture native refusée, fenêtre vivante), destruction forcée (teardown). Le refus applicatif d'un `WM_CLOSE` natif exige `closeRequestRejected` — la fenêtre reste ouverte.
- **D4 — Dernière fenêtre.** Armement de la règle après admission de la première fenêtre (`stopWhenLastWindowClosed`) ; fermeture des requêtes pending à l'arrêt ; la fenêtre de contrôle du host n'est jamais confondue avec une fenêtre publique.

## Architecture de la tranche

`Win32WindowRuntimeDriver` implémente `WindowCommandPort` (miroir du driver AppKit, tranche limitée) : `requestOpen` → création native sur le thread UI (`CreateWindowExW` + `ShowWindow`) → peer propriétaire par fenêtre → commit runtime (`OpenedHere`, primaire = première admise) ; `requestOpenedClose` → `DestroyWindow` après décision ; `requestPendingCancellation` → destruction de toute fenêtre créée non remise. Adjonctions runtime : `RuntimeDesktopNativeWindowHandle.Win32(hwnd)` + mapping public sous lease `withDesktopHandle` ; budgets `maxWindowsPerSession` ; événements `Closing`/`CloseRequested` publiés via le flux d'événements existant. La pompe, la session et le provider de la phase 1 sont inchangés — les messages de fenêtre sont dispatchés par `DispatchMessageW` vers la `WNDPROC` de leur classe, routés par le bridge étendu.

## F1 — Failures

| # | Condition | Résultat |
|---|---|---|
| 1 | budget `maxWindowsPerSession` atteint | `requestWindow` → outcome refusé exact (`WindowBudgetExhausted`), zéro création |
| 2 | création native en échec (`CreateWindowExW` NULL) | annulation avant commit, fenêtre détruite si créée, request terminé `Failed` |
| 3 | admission annulée pendant la création | fenêtre créée non remise → détruite, zéro événement public |
| 4 | fermeture native refusée par l'application | fenêtre reste ouverte, aucun état terminal |

## Ordres observables

Ouverture : `OpenedHere` publié après commit, primaire suit l'ordre d'admission. Fermeture : `Closing` → surface détachée → `Closed` → retrait du manager. Handle : emprunt borné pendant le callback, handle fermé inutilisable, réutilisation d'adresse sans livraison à l'ancien owner. Dernière fenêtre : arrêt demandé (standalone) après fermeture de la dernière, jamais avant.

## Scénarios

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | trois fenêtres top-level ouvertes/fermées dans une session, ordres observés | O3 |
| P-2 | budgets et primaire suivent l'admission | O2 |
| P-3 | `withDesktopHandle` emprunte le HWND vivant sous lease | O2/O3 |
| SN-1 | budget épuisé → refus exact sans création | O2 |
| SN-2 | création échouée → annulation propre, zéro fenêtre fantôme | O2 |
| SN-3 | `WM_CLOSE` intercepté refusé → fenêtre vivante ; accepté → ordre terminal | O2/O3 |
| SN-4 | destruction forcée pendant closing pending → un seul terminal | O2 |
| SN-5 | fermeture d'une secondaire n'arrête pas la pompe ; dernière fenêtre → arrêt | O3 |
| SN-6 | HWND réutilisé → aucun événement à l'ancien owner | O3 |

## Allocation prévue de contrats

`W32-004` (ouverture/fermeture, ordres terminaux, O2/O3), `W32-005` (décision de fermeture WM_CLOSE, refus/force, O2/O3), `W32-006` (handle lease, dernière fenêtre, réutilisation d'adresse, O2/O3) — vérification du registre à l'écriture du plan ; gate `win32-jvm` inchangé.
