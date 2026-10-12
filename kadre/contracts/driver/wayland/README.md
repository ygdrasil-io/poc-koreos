# Driver de contrats Wayland

Stimuli externes, fixtures compositor et observations natives pour la gate
Wayland (topologie §4 de la roadmap). Ce module n'importe aucune API Kadre :
il vit de l'autre côté de la frontière, comme le driver Web vis-à-vis du
navigateur.

## Contenu

- `WaylandCompositorController` — démarre un Weston headless isolé (baseline
  B-5 de `WAYLAND-PHASE-0-KFFI-AUDIT.md`) ou réutilise le compositor fourni
  par l'environnement (`WAYLAND_DISPLAY`/`XDG_RUNTIME_DIR` déjà définis, mode
  script CI). Le journal weston est conservé pour la collecte de diagnostics.

## Scénarios couverts côté tests (backend `:kadre:backend:wayland`)

- cycles start/stop sur compositor réel, comptage de fds, callbacks tardives (P-1) ;
- wakes répétés sans fenêtre (P-2) ;
- arrêt depuis un autre thread pendant le poll (SN-5) ;
- serveur absent (SN-1) et `WAYLAND_SOCKET` refusé tant que `KFFI-WAYLAND-001`
  est ouvert (SN-3 variante F1-4).

Les scénarios SN-4 (compositor amputé d'un global requis) et SN-8 (version
annoncée sous le minimal) sont prouvés en O2 sur les fakes et attendent un
compositor amputé pour une preuve O3 — procédure documentée, jamais un skip
silencieux.
