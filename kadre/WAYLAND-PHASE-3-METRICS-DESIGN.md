# Wayland — Phase 3 : métriques, outputs, scale et redraw

Design de la phase 3 (roadmap `WAYLAND-IMPLEMENTATION-ROADMAP.md` §Phase 3).
Prérequis : phase 2 livrée (#460 — fenêtres, close, leases) et kffi consolidé
(#124 — marshals array-flags obligatoires, voir §Leçons).

## Décisions fermées

### D1 — Inventaire outputs → `DisplayPortSnapshot`
- Un `wl_output` lié = un `DisplayPortDisplay` : `key` = nom numérique du
  global (corrélation uniquement), `bounds` = `PhysicalRect(x, y, mode.w,
  mode.h)` (géometry + mode courant), `scaleFactor` = `wl_output.scale`
  (entier — l'honnêteté prime : le scale fractionnaire négocié via
  fractional-scale-v1 est un **candidat buffer scale** par surface, pas une
  géometry d'output).
- `workArea = null` toujours (aucune source protocolaire Wayland pour la
  zone de travail — pas d'invention).
- `primaryKey` = l'output sur lequel la surface primaire a `enter` ; `null`
  sinon. Pas d'identité terminale conservée : la déconnexion retire la
  display et le `done` rebatche tout le snapshot (all-or-nothing).
- Modes : `wl_output.mode` par output, `current` ⇒ `currentModeKey`.

### D2 — Redraw = frame callback comme pacing, jamais comme horloge
- `SurfaceCommandPort.requestRedraw` (kadar side) marque la surface « sale »
  et arme un `wl_surface.frame` si aucun n'est armé (coalescence : N demandes
  pendant une frame = un commit).
- Au callback : damage(0,0,w,h) + attach(buffer courant) + commit. Une
  surface sans frame callback (invisible) ne commit pas — la demande reste
  sale et sera satisfaite au premier `frame` après re-entrée.
- Le callback `frame` n'est jamais traité comme preuve de présentation ni
  comme horloge (roadmap) : aucune timestamp publiée.

### D3 — Scale : logique (runtime) vs buffer (wl_shm)
- Taille logique = le `WindowSpec` du runtime (inchangée, phase 2).
- Taille buffer = logique × `bufferScale` où `bufferScale` =
  `preferred_buffer_scale` (surface ≥ v6) sinon `wl_output.scale` du dernier
  output entré, sinon 1. Arrondi à l'entier supérieur pour le fractionnaire
  (fractional-scale-v1 donne une valeur ×120 ; le buffer est arrondi up, la
  composition downscale — jamais de flou d'upscale).
- `wp_viewporter.set_destination` n'est PAS utilisé en tranche 3 (le
  renderer n'existe pas encore) : coordination viewport/renderer différée,
  comme prévu par la roadmap.
- Les changements de scale (enter d'un autre output, preferred_scale) ⇒
  réallocation du buffer à la taille logique × nouveau scale + un redraw.

### D4 — Leçons phase 2 appliquées
- Tout nouveau marshal passe par `wl_proxy_marshal_array_flags` (le
  variadique perd/décale des arguments sur certaines JVM) — indices de
  signature complets, new_id en slot 0.
- errno via `__errno_location()`, pas de stubs capture-state.
- Handles de downcall cachés par (symbole, descripteur, options).

## Tables de pannes

| ID | Panne | Garde-fou |
|---|---|---|
| F1-1 | output retiré en cours de snapshot | batch all-or-nothing au `done` ; inventaire partiel jamais publié |
| F1-2 | primaire retiré | `primaryKey = null` honnête, pas de substitution |
| F1-3 | `scale = 0` (output sans mode courant) | output exclu de l'inventaire avec trace, jamais `scaleFactor = 0` publié |
| F2-1 | display error pendant l'énumération | `KadreResult.Failure` → retrait de l'inventaire exposé |
| F2-2 | frame callback armé deux fois | ticket unique : le second demand coalesce |
| F2-3 | redraw après close | ticket refusé (`Closed(Window)`) |
| F3-1 | scale change pendant un redraw | le redraw en cours finit à l'ancien buffer ; le nouveau scale arme son propre redraw |
| F3-2 | enter/leave en rafale | dernier état gagne, un seul repaint |

## Scénarios de gate

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | inventaire sway headless (1 output 640×480 scale 1) | O3 — snapshot complet, primaryKey observé |
| P-2 | `swaymsg output scale 2` en cours de session | O3 — scale publié 2.0, buffer réalloué, redraw sans resize logique |
| P-3 | retrait/re-ajout d'output | O3 — inventaire vidé puis repeuplé, pas d'inventaire périmé |
| P-4 | redraw répété sans resize | O3 — N requestRedraw ⇒ ≤ N commits, frames coalescées |
| SN-1 | demande de redraw après fermeture | O2 — `Closed(Window)` |
| SN-2 | output scale 0 / mode absent | O2 — output exclu, jamais scale 0 publié |

## Allocation de contrats prévue

| Contrat | Sujet | Oracle |
|---|---|---|
| `DSP-002` | inventaire outputs wayland (P-1, P-3, F1-*, F2-1) | O3 |
| `SUR-003` | redraw coalescent + pacing frame (P-4, SN-1, F2-*) | O2/O3 |
| `DSP-003` | scale : 1×, 2×, changement (P-2, F3-*) | O3 |

Numéros à revérifier au registre avant enregistrement.

## Hors périmètre (tranche 3)
Viewport+renderer (coordination renderer, phase renderer), rotation
d'output (transform), modes dynamiques, sous-surfaces, cursor.
