# Wayland — Phase 9 : services Linux, devices, gamepads et raw input

Design de la phase 9 (roadmap §Phase 9). Prérequis : phases 1 et 4.

## Décisions fermées (tranche 1 : observations honnêtes + inventaire seats)

### D1 — Observations système : uniquement des sources définies
- **Apparence/thème/contraste** : AUCUNE source protocolaire Wayland
  (pas de settings protocol avant phase ultérieure) ⇒ les capacités
  restent `Unknown`/absentes, aucune synthèse. Documenté.
- **Pression mémoire** : aucune source système liée à Wayland ; le
  suivi Linux global (pressure PSI/procfs) exigerait une source dédiée
  avec permissions définies — hors tranche 1 ⇒ `memoryPressure` reste
  l'indisponibilité prévue du runtime par défaut.
- La présence de Wayland n'active RIEN implicitement (roadmap D5).

### D2 — Inventaire de périphériques : les seats observables, honnêtement
- `InputDevicePort` wayland tranche 1 expose l'inventaire des **seats
  observables** (`wl_seat` globals + capabilities réelles), PAS un
  inventaire complet de devices physiques — le périmètre est
  explicitement documenté comme « projection logique seats » ;
  l'inventaire physique complet (libinput/udev) exigera bindings
  dédiés et permissions (tranche suivante, gap kffi ouvert).
- Hotplug seat : ajout/retrait du global ⇒ InventoryChanged ;
  changement de capabilities ⇒ l'apparence du device change avec un
  InventoryChanged (re-read). Les clés sont les noms de globals —
  jamais réutilisés après retrait dans la même session.

### D3 — Gamepad et effets : refus honnête
- `GamepadPort` : aucun protocole Wayland ne fournit les gamepads ;
  le port wayland tranche 1 expose un inventaire **vide avec échec
  d'énumération explicite** (`inventoryFailure` = Unsupported
  documentant la source manquante) — pas d'inventaire silencieusement
  vide qui laisserait croire « aucun gamepad branché ».
- Effets (rumble) : `Unsupported` — aucune source.

### D4 — Raw input : pointeur relatif évalué, non activé
- Le pointeur relatif (pointer-lock/unstable) est évalué mais NON
  activé en tranche 1 : son activation exige les extensions pointer
  constraints de phase 5 + la définition des unités et du contexte
  focus/lock. La source globale hors focus reste hors périmètre —
  permissions et bindings propres requis. `RawInputPort` reste
  Unsupported wayland.

## Tables de pannes

| ID | Panne | Garde-fou |
|---|---|---|
| F1-1 | seat retiré pendant re-read | snapshot cohérent : le device disparaît au InventoryChanged suivant |
| F1-2 | capabilities changées | apparence mise à jour + InventoryChanged (re-read) |
| F2-1 | inventaire lu après close | liste vide, plus d'événements |
| F2-2 | clé réutilisée | noms de globals uniques par connexion |

## Scénarios de gate

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | seats annoncés → inventaire honnête (nom + capabilities) | O2 |
| P-2 | retrait/ajout de seat → InventoryChanged | O2 |
| SN-1 | gamepad inventoryFailure explicite (pas d'inventaire vide muet) | O2 |
| SN-2 | observations absentes restent absentes (Unknown, pas de synthèse) | O2 |

## Allocation de contrats prévue
`INP-007` (inventaire seats honnête + hotplug), `GMP-002` (refus
gamepad documenté). Numéros à revérifier au registre.

## Hors périmètre (tranche 1)
libinput/udev (bindings kffi + permissions), pressure PSI, xdg-settings
portal, pointer-lock relatif, effets gamepad, observation globale.
