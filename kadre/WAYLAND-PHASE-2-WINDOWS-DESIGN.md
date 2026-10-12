# Wayland Phase 2 — Fenêtres, surface minimale et interop renderer

## But

Livrer la deuxième tranche (`WAYLAND-IMPLEMENTATION-ROADMAP.md` §5, Phase 2) : `WindowCommandPort` réel (création, configuration initiale, `OpenedHere`, annulations, destruction partielle), surface minimale mappée avec metrics et `withDesktopHandle`, propriétés initiales conformes uniquement (rejet normatif du reste), titre, fermeture native non-interceptable, promotion de `primary` et `stopWhenLastWindowClosed` armé après la première fenêtre.

Exclusions : metrics d'outputs et scale (phase 3), input clavier/pointeur (phase 4), interactions move/resize (phase 5), décoration serveur effective (gap `KFFI-WAYLAND-002` voisin xdg-decoration évalué en phase 5).

## Préconditions KFFI

- `KFFI-WAYLAND-004` (binding managé de base) : `closed` — PR Graphiks-org/kffi#115, snapshot `1.0.0-20261012.001313-60` publié.
- **`KFFI-WAYLAND-005` (nouveau)** — couche fenêtrage managée : `WaylandSurface` (create/destroy, attach/damage/commit, frame callback révocable, listeners enter/leave copiant les payloads), `WaylandXdgSurface` (listener configure par serial + `ackConfigure`), `WaylandToplevel` (listeners configure(width, height, states copiés)/close, `setTitle`/`setAppId`, destroy), pong `xdg_wm_base` géré par kffi. **Tout via le marshal générique** `wl_proxy_marshal_flags` + `wl_proxy_add_listener` : la totalité des helpers de protocole (`wl_compositor_create_surface`, `xdg_wm_base_get_xdg_surface`, `xdg_surface_get_toplevel`, `ack_configure`, `set_title`, `wl_surface_commit`, `frame`, `wl_shm_create_pool`…) est `static inline` dans les en-têtes — aucun de ces symboles n'existe dans la librairie (constat M1, vérifié `nm -D`).
- **`KFFI-WAYLAND-006` (nouveau)** — buffers shm managés : pool depuis une zone mémoire consommateur, buffer dimensionné + listener release révocable, destroy déterministe.

## Architecture kadre

Miroir AppKit (référence) avec la frontière kffi :

- `WaylandWindowRuntimeDriver` (composition root) construit `RuntimeWindowManager(commandPort, surfaceCommandPort, platform = Wayland, publicWindowCapabilities = true, onLastWindowClosed = bridge)` — le runtime reste l'unique autorité d'état (`WindowState`, révisions, appartenance, succession `primary` par ordre d'admission, arming de `stopWhenLastWindowClosed` après première fenêtre). Kadre ne tient aucun second snapshot.
- `WaylandWindowCommandPort : WindowCommandPort, SurfaceCommandPort` — marshal vers le thread pompe ; compléments exclusivement via les stimuli capturés (`commit/fail/nativeClosed/closeRequested`).
- `WaylandWindowPeer` — préparation native : création surface + xdg_surface + toplevel, `setTitle(spec.title)`, handshake xdg (`commit` initial sans buffer → attente configure → `ackConfigure(serial)` → attach du buffer contrôlé → commit → mappée), puis `WindowOpenCommand.commit(owner, effectiveSpec, initialSurfaceSnapshot)`.
- **Buffer contrôlé** : la peer attache un buffer shm dimensionné `contentSize` fourni par un `WaylandSurfaceBufferSource` injecté (interface interne du backend). En tranche 2, la source par défaut remplit un motif déterministe — véhicule de preuve du mapping ; le contenu appartient au renderer externe (D2), aucun renderer n'entre dans Kadre. Preuve de mapping = `wl_surface.enter` + configure consommés, jamais une capture de pixels (oracle interdit).
- **Propriétés initiales conformes uniquement** (D1) : `decorations = System` ⇒ `Unsupported(Decorations)` avant toute primitive ; `outerPosition` ⇒ refus (position non observable, D3) ; `fullscreen Exclusive` ⇒ hors périmètre phase 5 ; `level`, `systemButtons`, `blur`, `icon`, `contentProtection`, `transparency` ⇒ refus normatifs champ par champ (champs non supportés rejetés par le runtime via capabilities, vérifié au `effectiveSpec`).
- **Fermeture** : `xdg_toplevel.close` n'est ni différable ni rejetable — jamais de `CloseRequested(canReject = true)` : passage direct à `Closing` à raison forcée (`WindowCloseReason.System`), ordre terminal DESIGN L918 ; `closeInterception` = `Unsupported` ; la fermeture programmatique (`requestOpenedClose`) émet le destroy ordonné (quiescence → destroy toplevel/xdg/surface/buffer → retrait), `NativeCloseAlreadyCommitted` en cas de course.
- **`withDesktopHandle`** : lease par fenêtre (compteur + admission refusée après `closeAdmitted`), `RuntimeDesktopNativeWindowHandle.Wayland(displayAddress, surfaceAddress)` — variante ajoutée au sealed runtime — adresses valides uniquement pendant l'appel, thread pompe ; invalidation du lease **avant** destruction des proxies (D2).
- **Provider** : la session standalone branche le driver réel à la place de `WaylandHeadlessWindowManager` ; `stopWhenLastWindowClosed` passe par le bridge → `sessionStopHandler` ; une session sans fenêtre reste headless (`Background + Inactive`) et ne s'arrête pas.

## F1 — Failures et refus avant/pendant création

| # | Condition | Résultat |
|---|---|---|
| 1 | `decorations = System` (défaut du catalogue) | `Unsupported(Decorations)` sur la requête, avant toute primitive ; jamais de repli `Borderless` |
| 2 | `outerPosition` non nul | `Unsupported(OuterPosition)` |
| 3 | `level`/`systemButtons`/`blur`/`icon`/`contentProtection`/`transparency`/`fullscreen Exclusive` | rejet normatif par champ (`partiallyApplied`/`rejected` énumérés) |
| 4 | configure `width == 0 && height == 0` (taille non imposée) | la fenêtre garde la taille du `WindowSpec` ; jamais une surface de taille nulle |
| 5 | configure ancien (serial déjà acquitté) | pas de second `ackConfigure` ; les états obsolètes sont ignorés |
| 6 | échec de création native (proxy null, libwayland erreur) | `WindowOpenCommand.fail` avant `commit` — destruction partielle : proxies créés détruits en ordre inverse, zéro résidu |

## F2 — Terminaison

Ordre terminal verbatim DESIGN L918 : `Closing` → détachement terminal de la surface → `Closed` → retrait atomique de `windows`/`primary` → fin de `events`. Course fermeture concurrente : la première décision gagne ; `requestOpenedClose` après commit natif ⇒ `NativeCloseAlreadyCommitted`. Callback tardive interdite après destruction (quiescence kffi).

## Scénarios de gate (positifs/négatifs/races)

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | deux fenêtres réellement mappées et indépendantes sur Weston, destruction de l'une sans perte de l'autre, `primary` successif déterministe | O3 |
| P-2 | propriétés initiales conformes : titre effectif, taille conservée sur configure non-imposé (F1-4) | O3 |
| P-3 | `stopWhenLastWindowClosed` : armé après la première fenêtre, transition non-vide → vide ⇒ arrêt ; session headless sans fenêtre ne s'arrête pas (négatif) | O2 |
| SN-1 | `decorations = System` ⇒ `Unsupported(Decorations)`, aucune primitive émise | O2 |
| SN-2 | `outerPosition` ⇒ refus normatif | O2 |
| SN-3 | champ non supporté dans `WindowSpec` ⇒ rejet par champ, sans application partielle silencieuse | O2 |
| SN-4 | configure ancien ignoré (F1-5), pas de double ack | O3 |
| SN-5 | callback tardive après destroy : jamais invoquée (quiescence) | O3 |
| SN-6 | fermeture concurrente : close programmatique + close compositor en course, une seule terminaison, `NativeCloseAlreadyCommitted` observable | O3 |
| SN-7 | échec de création native ⇒ destruction partielle, zéro résidu | O2 |
| SN-8 | handle lease pendant `close` : admission refusée, block jamais exécuté après `closeAdmitted` | O2/O3 |

## Allocation prévue de contrats

| Famille | Sujet | Oracle |
|---|---|---|
| `WIN-010` | création/admission, propriétés initiales conformes, refus normatifs (SN-1..SN-3, F1-6) | O2 |
| `WIN-011` | fermeture non-interceptable, ordre terminal, course concurrente (SN-5, SN-6) | O3 |
| `WIN-012` | succession `primary`, arming `stopWhenLastWindowClosed` (P-3) | O2 |
| `SUR-002` | surface minimale mappée, metrics initiaux, lease `withDesktopHandle` (P-1, P-2, SN-4, SN-8) | O3 |

Numéros vérifiés contre le registre au moment du plan (prochains libres au 2026-10-12 : `WIN-010+`, `SUR-002`) ; aucune preuve AppKit `jvm` ne satisfait un contrat Wayland (target `wayland-jvm`, adapter distinct, libc/compositor distingués).

## Hors périmètre

Redraw coalescent et `SurfaceCommandPort.requestRedraw` effectif (phase 3 — le port est implémenté en no-op `Unsupported` assumé), scale/viewport, input, move/resize, décoration serveur négociée, capture.
