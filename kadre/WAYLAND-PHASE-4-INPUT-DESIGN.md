# Wayland — Phase 4 : seats, clavier, pointeur et scroll

Design de la phase 4 (roadmap §Phase 4). Prérequis : phase 3 livrée (#473)
et kffi consolidé (#125, snapshot ≥ 71).

## Décisions fermées (tranche 1 : seats + pointeur + clavier brut)

### D1 — Seats sans état global
- Un `wl_seat` lié = un `WaylandSeat` kadre ; les capabilities (`pointer`
  `keyboard`, bitmask) déterminent quels sous-objets sont demandés
  (`get_pointer` opcode 0, `get_keyboard` opcode 1 — jamais `get_touch`,
  hors périmètre).
- Aucun seat ni focus « courant » global : chaque événement pointeur/clavier
  porte la surface propriétaire (le proxy object de `enter`/`leave`) ; le
  routage kadre associe l'adresse du proxy à la `SurfaceId` connue du driver.
- Changement de capabilities en cours de session ⇒ re-création des
  sous-objets concernés ; disparition du seat ⇒ neutralisation des
  touches/boutons retenus (reducer commun), jamais de focus synthétisé.

### D2 — Pointeur : agrégation par frame, fins de séquence préservées
- `wl_pointer` v9 : les événements sont regroupés par `wl_pointer.frame`
  (v5+). Le binding kffi livre les événements bruts ; kadre accumule
  enter/leave/motion/button/axis/axis_source/axis_stop/axis_discrete/
  axis_value120 jusqu'au `frame`, puis publie UN batch stimulus
  (state-before-event respecté : l'état publié précède l'event).
- Scroll : `axis_value120` (v8+) est la source préférée (unités ×120) ;
  `axis_discrete` (v5) en repli ; `axis` seul = continu. `axis_stop`
  termine une séquence — jamais avalé, même dans un batch saturé.
- Le curseur (`set_cursor`) est différé : aucune surface cursor en tranche 1.

### D3 — Clavier : brut d'abord, XKB ensuite
- Tranche 1 : keymap (fd + taille capturés, consommateur mmap),
  enter (liste de touches pressées copiée), leave, key (physical keycode,
  état pressed/released), modifiers (groupe déprimé + sérialisés),
  repeat_info (rate négocié, delay). Aucune traduction texte —
  KFFI-WAYLAND-002 (xkbcommon) reste ouvert pour la tranche 2.
- `repeat_info` avec rate ≤ 0 ⇒ répétition désactivée (publiée honnêtement).
- Perte de focus (leave) ou retrait du clavier avec touche enfoncée ⇒
  événements de relâchement synthétiques neutralisent les touches retenues,
  conformément au reducer commun ; un clavier présent n'est jamais un focus
  acquis.

### D4 — Leçons des phases 2-3 (toujours)
- Marshals en array-flags à arité fixe, indices de signature complets,
  new_id en slot 0 ; handles de downcall cachés ; errno via
  `__errno_location()` ; upcalls : copie immédiate des payloads (y compris
  le tableau `a` de keyboard.enter et les fd du keymap).

## Tables de pannes

| ID | Panne | Garde-fou |
|---|---|---|
| F1-1 | capabilities change pendant un cycle | sous-objets recréés, anciens libérés (release) |
| F1-2 | seat retiré | touches/boutons neutralisés, aucun stimulus après |
| F2-1 | frame perdue (v<5) | sans frame, chaque événement est son propre batch |
| F2-2 | axis sans frame suivant | timeout de publication = la frame suivante ou leave |
| F2-3 | enter avec 0 surface proxy | ignoré, trace |
| F3-1 | keymap fd non mappable | clavier publié sans layout, touches brutes seulement |
| F3-2 | key avant enter | compté comme pressé sur la surface focus inconnue → rejeté |
| F3-3 | leave avec touche enfoncée | relâchements synthétiques exactement pour les retenues |

## Scénarios de gate (tranche 1)

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | deux fenêtres, focus clavier/pointeur alterné via swaymsg | O3 |
| P-2 | `swaymsg seat seat0 cursor move/press` → batches pointeur complets | O3 |
| P-3 | `swaymsg seat seat0 type "ab"` → key press/release + modifiers | O3 |
| P-4 | scroll continu/discret (axis + axis_value120) via wheel simulé | O3 |
| SN-1 | touche enfoncée + leave → relâchements synthétiques | O2/O3 |
| SN-2 | capabilities change (keyboard off/on) | O3 |
| SN-3 | key hors focus rejetée | O2 |

L'oracle d'injection est **swaymsg côté compositor** — le mapper de
production n'est jamais son propre oracle (roadmap).

## Allocation de contrats prévue (tranche 1)

| Contrat | Sujet | Oracle |
|---|---|---|
| `INP-004` | seats wayland : capabilities, routage surface, neutralisation | O3 |
| `INP-005` | pointeur wayland : batches frame, scroll unités, fins de séquence | O3 |
| `INP-006` | clavier brut wayland : keymap fd, enter/leave, keys, repeat | O3 |

Numéros à revérifier au registre avant enregistrement.

## Hors périmètre (tranche 1)
XKB (texte, touches mortes, layouts — tranche 2, KFFI-WAYLAND-002),
touch, curseur set_cursor, drag-and-drop, gestes, input-method.
