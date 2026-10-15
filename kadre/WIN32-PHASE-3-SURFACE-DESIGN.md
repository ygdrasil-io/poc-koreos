# Win32 Phase 3 — Surface, redraw et DPI

## But

Design ciblé de la troisième tranche Windows (`WIN32-IMPLEMENTATION-ROADMAP.md` §5, Phase 3) : chaque fenêtre expose une `HostSurface` dont les métriques (taille client, taille physique, scale) sont **relues** auprès du natif (`GetClientRect` + `GetDpiForWindow`) avant publication — jamais la taille demandée ; `WM_SIZE`, `WM_DPICHANGED` et les événements de fenêtre deviennent des observations ; `requestRedraw` est implémenté par invalidation coalescée (`InvalidateRect`) drainée à `WM_PAINT`. Exclusions : input (phase 4), fenêtres avancées et mutations (phase 5), accès graphique natif (`platformAccess = Unsupported(PlatformSurfaceAccess)` conformément à Desktop).

## Préconditions KFFI

Satisfaites par le snapshot build 56 (kffi #123) : `InvalidateRect` et `ValidateRect` générés ; `GetClientRect`, `GetDpiForWindow`, `SetProcessDpiAwarenessContext` déjà générés ; les constantes de messages (`WM_SIZE` = 5, `WM_PAINT` = 0x000F, `WM_DPICHANGED` = 0x02E0, `WM_ERASEBKGND` = 0x0014) sont des constantes locales du driver, pas des bindings.

## Décisions de la tranche

- **D1 — Métriques relues, jamais supposées.** Après `WM_SIZE`, `WM_DPICHANGED` et à l'ouverture, le driver relit `GetClientRect(hwnd)` (taille physique en pixels) et `GetDpiForWindow(hwnd)` (scale = dpi/96.0) puis publie la snapshot effective : `logical = physical / scale`. `requestWindow` ne publie jamais la taille demandée comme effective.
- **D2 — DPI Per-Monitor V2.** Le contexte `SetProcessDpiAwarenessContext(PER_MONITOR_AWARE_V2)` est posé une fois à la création du host **si le processus n'en a pas déjà un** (lecture du contexte courant, jamais d'écrasement d'un host existant). `WM_DPICHANGED` applique le rect suggéré par Windows (redimensionnement natif) puis relit les métriques effectives et republie le scale.
- **D3 — Redraw coalescé borné.** `SurfaceCommandPort.requestRedraw(command)` valide la fenêtre vivante puis `InvalidateRect(hwnd, NULL, FALSE)` (coalescing natif : les demandes répétées fusionnent) ; à `WM_PAINT` le driver draine (`ValidateRect` sur la région entière — Kadre ne peint rien, pas de `BeginPaint`/GDI) et marque la génération consommée. Aucune boucle de paint au repos : sans invalidation, aucun `WM_PAINT`.
- **D4 — Observations de surface.** `WM_ACTIVATE` → focus ; `WM_SIZE` (SIZE_MINIMIZED / SIZE_RESTORED / SIZE_MAXIMIZED) → occlusion/état ; `WM_SHOWWINDOW` → visibilité — chaque observation relit l'état effectif natif avant publication, `Unknown` lorsque non observable.

## Architecture de la tranche

`KffiWin32HostPort` gagne `invalidateRect(hwnd)`, `validateRect(hwnd)`, `readClientRect(hwnd): PhysicalSize`, `readScaleFactor(hwnd): Double`. Le driver construit la snapshot de surface réelle à l'ouverture (métriques relues après création) et route les nouveaux messages vers les observations (géométrie via `manager.acceptWindowGeometryObservation`, surface via le stimulus sink existant). `SurfaceCommandPort` passe de `TemporarilyUnavailable` à l'implémentation redraw. `platformAccess` reste `Unsupported(PlatformSurfaceAccess)`.

## Ordres et contrats observables

Ouverture : métriques effectives publiées à l'admission (relues, pas la demande). Redraw : `requestRedrow` → invalidation → `WM_PAINT` → génération consommée, borné (coalescing natif, aucune file non bornée). DPI : changement → rect suggéré appliqué → métriques relues → événement après l'état. Fermeture : `Closing` → surface détachée → `Closed` inchangé (phase 2).

## Scénarios

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | resize natif → métriques physiques/logiques relues publiées après `WM_SIZE` | O3 |
| P-2 | `requestRedraw` → InvalidateRect → WM_PAINT → génération consommée, coalescing (N demandes → 1 drain) | O2/O3 |
| P-3 | scale relu via GetDpiForWindow cohérent avec les métriques | O3 |
| SN-1 | taille demandée ≠ taille effective (fenêtre OS-limitée) → l'effective publiée | O2/O3 |
| SN-2 | `WM_PAINT` sans invalidation au repos → aucun drain | O2/O3 |
| SN-3 | `requestRedraw` sur fenêtre fermée → échec exact | O2 |

## Allocation prévue de contrats

`W32-007` (métriques + DPI relus, O2/O3), `W32-008` (redraw coalescé borné, O2/O3) — vérification du registre à l'écriture du plan ; gate `win32-jvm` inchangé.
