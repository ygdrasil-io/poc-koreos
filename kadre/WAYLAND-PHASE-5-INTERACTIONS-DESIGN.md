# Wayland — Phase 5 : interactions et fenêtres avancées

Design de la phase 5 (roadmap §Phase 5). Prérequis : phase 4 (#475) et kffi
consolidé (#129, snapshot ≥ 74 ; toplevel ops en branche
`feat/wayland-toplevel-ops`).

## Décisions fermées (tranche 1)

### D1 — Move/resize : serials réellement éligibles uniquement
- `xdg_toplevel.move(seat, serial)` / `resize(seat, serial, edges)` ne
  sont émis que depuis une **callback native d'interaction** (pointer
  press), avec le serial de CET événement et le seat qui l'a émis. Le
  driver suit le dernier serial valide par seat (pointer button/keyboard
  key/enter) — jamais de serial fabriqué (le serveur refuse, erreur fatale
  côté kffi preuve).
- `BeginWindowMove`/`BeginWindowResize` dans `InteractionContext.request`
  routent vers ces marshals ; hors callback native (pas de serial
  éligible) ⇒ rejet `InvalidRequest` déterministe, pas d'émission.

### D2 — Champs de fenêtre conformes (requestUpdate)
- Tranche 1 : `title`, `minimumSize`, `maximumSize` (set_min/max_size,
  effet au commit suivant), `fullscreen = Borderless` (set_fullscreen /
  unset_fullscreen — le mode Borderless est le seul conforme Wayland :
  windowed↔borderless ; `Exclusive` reste hors domaine comme AppKit).
- Corrélation : chaque `WindowUpdateCommand` publie l'état effectif via
  `applied(state)` — l'état reflects les contraintes posées, la géométrie
  effective restant autoritaire au configure suivant (F1-4 phase 2).
- `resizable` : pas de primitive directe ; une fenêtre non-resizable =
  min=max=taille courante à la prochaine création de contrainte —
  implémenté ainsi et documenté, jamais un champ muet.

### D3 — Évaluation séparée des protocoles voisins
- `xdg-decoration` : NÉGOCIATION TRANCHHE 2 (exige bindings kffi
  zxdg_decoration_v1) — `decorations=System` reste refusé (D1 phase 2).
- Activation/attention : aucune promesse de focus ni d'effet visible pour
  une demande — `requestAttention` reste `Unsupported` tant qu'aucun
  protocole conforme (xdg-activation) n'est lié. Icône : sans primitive
  Wayland → reste `Unsupported`.
- Pointer constraints/relative pointer : évalués, non activés (phase 6+).

### D4 — Leçons 2-4
- Marshals array-flags / signatures exactes ; serials réels ; aucun état
  global ; rejets déterministes avec codes stables.

## Tables de pannes

| ID | Panne | Garde-fou |
|---|---|---|
| F1-1 | move/resize hors callback native | rejet InvalidRequest, aucune émission |
| F1-2 | serial périmé | le serveur refuse (erreur protocole) — documenté, test kffi |
| F1-3 | update pendant configure en vol | l'état effectif publié au configure suivant |
| F2-1 | min > max | validation runtime AVANT émission, rejet champ |
| F2-2 | fullscreen exclusive demandé | hors domaine : rejet normatif champ |
| F2-3 | double consommation serial | un serial consommé par action, jamais réutilisé |

## Scénarios de gate

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | title/min/max via requestUpdate → configure effectif | O3 |
| P-2 | fullscreen borderless on/off → états publiés | O3 |
| P-3 | move refusé hors callback (O2) / émis avec vrai serial (kffi proof) | O2 |
| SN-1 | min>max rejeté avant toute primitive | O2 |
| SN-2 | exclusive fullscreen rejeté champ | O2 |

## Allocation de contrats prévue
`WIN-013` (updates conformes + refus champ), `INT-005` (move/resize
serials). Numéros à revérifier au registre.

## Hors périmètre (tranche 1)
xdg-decoration (T2), xdg-activation, icône, pointer constraints, relative
pointer, show_window_menu, parentage, minimized/maximized publics.
