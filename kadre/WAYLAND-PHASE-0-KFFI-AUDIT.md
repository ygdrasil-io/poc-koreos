# Wayland Phase 0 — Audit des bindings KFFI publiés

Mandat : inventorier les APIs réellement publiées par les snapshots KFFI consommables par Kadre (`org.graphiks:kffi-wayland-jvm` et `org.graphiks:kffi-posix-jvm`), verdict par domaine contre les besoins de `WAYLAND-IMPLEMENTATION-ROADMAP.md` §3.2, et n'ouvrir que les gaps confirmés dans `KFFI-REQUIREMENTS.md`. Cette phase n'ajoute aucune dépendance au build : l'audit porte sur les artifacts publiés, téléchargés et pignés ci-dessous.

## Artifacts pignés

Résolus depuis Central Portal Snapshots (`settings.gradle.kts`, référentiel `central.sonatype.com/repository/maven-snapshots`), publication du 2026-10-11 16:29 :

| Artifact | Version horodatée | sha256 (jar) | sha256 (sources) | Classes |
|---|---|---|---|---|
| `org.graphiks:kffi-wayland-jvm` | `1.0.0-20261011.162917-59` | `5da489c35c3304046080f1df015df0cfc6714c9ff16d65b9bd6dd3523780b14b` | `e73cccc2155456aebcf7472cca51fc91acc61641802e2b98de96f70fa8c52676` | 150 |
| `org.graphiks:kffi-posix-jvm` | `1.0.0-20261011.162917-58` | `38dad47ebe2a09ea16084fd8b81908e4b86940302e7e49ad5b26f02b32623916` | `8af0659da0efda79b8b3742f2309422c01fa4b46361e26f5c6b681b8cc7c68fe` | 37 |

Sources audit : jars `-sources` de ces versions exactes (`Wayland_h.kt`, `WaylandInterfaces.kt`, `PosixSymbols.kt`, `PosixWakeup.kt`, `generated/*`). Le checkout local `/Users/chaos/workspace/graphiks-kffi` existe mais n'est pas l'autorité : seuls les artifacts publiés le sont.

## Verdicts par domaine

| # | Domaine (roadmap §3.2) | Besoin minimal tranche 1 | Présent dans kffi ? | Verdict | Gap ouvert | Bloque la tranche |
|---|---|---|---|---|---|---|
| 1 | Connexion, registry, proxies | connect / get_registry / bind / dispatch / destroy | `wlDisplayConnect`, `wlDisplayDisconnect`, `wlDisplayGetRegistry`, `wlRegistryAddListener`, `wlRegistryBind`, `wlProxyDestroy`, `wlProxyGetVersion`, `wlProxyAddListener`, `wlProxyMarshalNewId` + ~25 wrappers typés `wlProxyMarshalFlags*` (sources `Wayland_h.kt`) | usable, **sauf connexion par fd** (ligne 12) | KFFI-WAYLAND-001 | tranche 1 (support `WAYLAND_SOCKET` uniquement) |
| 2 | Pompe `libwayland-client` | prepare_read / read_events / cancel_read / dispatch / flush | `wlDisplayPrepareRead`, `wlDisplayReadEvents`, `wlDisplayCancelRead`, `wlDisplayDispatch`, `wlDisplayDispatchPending`, `wlDisplayFlush`, `wlDisplayGetFd`, `wlDisplayRoundtrip`, `WaylandNativePollResult` (errno capturée via `Linker.Option.captureCallState`) | usable | — | — |
| 3 | Listeners, payloads, exceptions | add_listener révocable, copie des payloads | `wlProxyAddListener` / `wlRegistryAddListener` + `upcallStub(handle, descriptor, arena)` : primitives brutes présentes ; pas d'owner révocable managé côté kffi | usable — l'owner (quiescence, copie, isolation d'exception) est une responsabilité Kadre, sur primitives existantes | — | — |
| 4 | POSIX glibc/musl | wake réarmable, poll, symboles, fermeture | `PosixSymbols` (loader process d'abord, puis `libc.so.6`/`libc.so` ordonnés, lazy) ; `PosixWakeup` (eventfd `EFD_NONBLOCK\|EFD_CLOEXEC` → repli `pipe2` → repli `pipe`, `O_NONBLOCK`, retry `EINTR`, `signal()`/`close()` idempotents sur `AtomicBoolean`) ; `PollFd`, `LinuxErrno`, `OwnedPosixWakeup` ; dans kffi-wayland : `poll/read/write/close/memfd_create/shm_open/mmap/ftruncate` (buffers) | usable | — | — |
| 5 | Chargement paresseux | aucun chargement natif à l'init de classe | tous les handles KFFI sont `by lazy` (`libwayland-client.so.0`, `libxkbcommon.so.0`, `libwayland-cursor.so.0`, symboles POSIX) ; aucun downcall résolu au clinit dans les sources auditées | usable | — | — |
| 6 | XKB | compilation keymap, état, translation keysym | présents : `xkb_context_new/unref`, `xkb_keymap_new_from_string`, `xkb_state_new`, `xkb_keymap_unref`, `xkb_state_unref`, compose table/state (`new_from_locale`, `new`, `reset`, `unref`). **Absents : toute translation touches→keysyms** (`xkb_state_key_get_one_sym`, `xkb_state_key_get_syms`, `xkb_state_update_mask`, mods, repeat) | **partiel — gap** | KFFI-WAYLAND-002 | phase 4 (input), pas la tranche 1 |
| 7 | Cœur fenêtrage | wl_compositor, xdg_wm_base, surface typée | interfaces générées `wl_compositor`, `xdg_wm_base`, `xdg_surface`, `xdg_toplevel`, `xdg_popup`, `xdg_positioner` ; `wlCompositorCreateSurface/CreateRegion`, `wlProxyMarshalFlagsGetXdgSurface` ; enums `xdg_toplevel_state/error/wm_capabilities/resize_edge`, `xdg_surface_error`, `xdg_wm_base_error` ; versions max bindées : `WL_COMPOSITOR_VERSION = 4`, `XDG_WM_BASE_VERSION = 2` | usable | — | — |
| 8 | Input de base (hors translation clavier) | seat, pointeurs, curseurs | `wlSeatGetPointer/GetKeyboard/GetTouch` ; `wl_cursor_theme_load/get_cursor/get_buffer/destroy` (`libwayland-cursor.so.0`) ; interfaces + enums `wp_cursor_shape_device_v1` ; enums `wl_seat/wl_pointer/wl_keyboard/wl_touch` ; pointer constraints (`zwp_pointer_constraints_v1`, `zwp_relative_pointer_v1`) ; tablet v2 ; text-input v3 | usable | — | — |
| 9 | Metrics, outputs, scale, redraw | outputs, viewporter, fractional, presentation | interfaces + enums `wl_output` (`OutputDataCollector`, `OutputNameCollector`), `wp_viewporter`, `wp_fractional_scale_manager_v1`, `wp_presentation` + `wp_presentation_feedback` | usable | — | — |
| 10 | Décorations | négociation serveur `System` | interface générée `zxdg_decoration_manager_v1` + enums `zxdg_toplevel_decoration_v1_mode/error` ; pas de wrapper typé `set_mode` — marshal générique `wl_proxy_marshal_flags` suffisant (le wrapper typé serait un confort, pas un besoin) | usable | — | — |
| 11 | Activation, icônes | tokens d'activation, icônes de toplevel | interfaces + enums `xdg_activation_v1`, `xdg_activation_token_v1`, `xdg_toplevel_icon_manager_v1`, `xdg_toplevel_icon_v1` (marshal générique) | usable | — | — |
| 12 | Capture (phases 10+) | D-Bus async + transfert de fd, PipeWire/SPA | D-Bus, portal, PipeWire : **absents** (`grep dbus\|portal\|pipewire` : zéro hit). Partie compositor présente : `zwlr_screencopy_manager_v1` + `ScreencopyFrameCollector`, `zwp_linux_dmabuf_v1` + enums | **gap** | KFFI-WAYLAND-003 | phase 10 (capture) uniquement |

## Conclusion

**Bindings minimaux requis pour la tranche 1 (liste fermée)** : domaines 2, 3, 4, 5, 7 ci-dessus — tous **vérifiés présents** dans `kffi-wayland-jvm:1.0.0-20261011.162917-59` et `kffi-posix-jvm:1.0.0-20261011.162917-58`.

**Blocages précis** :

1. `KFFI-WAYLAND-001` — `wl_display_connect_to_fd` absent (seul `wl_display_connect` par nom de socket existe). Sans lui, le contrat `WAYLAND_SOCKET` du probe (réutiliser puis fermer le fd au run) n'est pas implémentable, et tout downcall local est interdit (roadmap §3.2). **Bloque le support `WAYLAND_SOCKET` de la tranche 1** ; la connexion par nom de socket et le reste de la tranche 1 restent indépendants et non bloqués.
2. `KFFI-WAYLAND-002` — translation XKB absente. Bloque la phase 4 (clavier), pas la tranche 1.
3. `KFFI-WAYLAND-003` — D-Bus/PipeWire absents. Bloque la phase 10 (capture), pas la tranche 1.

Aucun autre domaine n'est bloquant ; les domaines 3 et 10 sont « usable » avec une responsabilité Kadre explicite (owner de listeners ; marshal générique), documentée comme telle.

## Évidence

Commandes exécutées le 2026-10-11 (macOS, curl/unzip/shasum) :

```bash
BASE=https://central.sonatype.com/repository/maven-snapshots/org/graphiks
curl -fsSL "$BASE/kffi-wayland-jvm/1.0.0-SNAPSHOT/maven-metadata.xml"   # → 1.0.0-20261011.162917-59
curl -fsSL "$BASE/kffi-posix-jvm/1.0.0-SNAPSHOT/maven-metadata.xml"     # → 1.0.0-20261011.162917-58
curl -fsSL -o kffi-wayland-jvm.jar   "$BASE/kffi-wayland-jvm/1.0.0-SNAPSHOT/kffi-wayland-jvm-1.0.0-20261011.162917-59.jar"
curl -fsSL -o kffi-posix-jvm.jar     "$BASE/kffi-posix-jvm/1.0.0-SNAPSHOT/kffi-posix-jvm-1.0.0-20261011.162917-58.jar"
# (+ jars -sources homologues) puis : shasum -a 256, unzip -l, extraction des sources et grep par domaine
```

Inventaires détaillés : `wl_display_connect_to_fd` absent de `Wayland_h.kt` (grep `connect` : `wl_display_connect` ligne 97, `wl_display_disconnect` ligne 107, rien d'autre) ; handles XKB = 11 (`xkbContextNew` … `xkbComposeStateUnref`, aucun `*key_get*`/`*update_mask*`) ; handles POSIX wake = `eventfd`/`pipe2`/`pipe` avec `EFD_NONBLOCK|EFD_CLOEXEC`, `O_NONBLOCK`, retry `EINTR` (`PosixWakeup.kt:24-85,121-183`) ; `PosixSymbols.kt:8-27` = loader d'abord, candidats SONAME ensuite.
