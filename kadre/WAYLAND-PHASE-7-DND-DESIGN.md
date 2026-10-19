# Wayland — Phase 7 : drag-and-drop et transferts

Design de la phase 7 (roadmap §Phase 7). Prérequis : phase 4 (#475 —
seats), phase 5 (#477), phase 6 (#478).

## Décisions fermées (tranche 1 : destinataire de drop)

### D1 — Destinataire uniquement, modèle drop public immuable
- Le backend câble `wl_data_device_manager.get_data_device(seat)` et
  traduit les événements du device (`data_offer`, `enter`, `motion`,
  `drop`, `leave`) en `presentDrop`/publications du runtime : les offres
  sont immuables (MIME collectés à l'announce), l'acceptation passe par
  `wl_data_offer.accept(serial, mime)` lors de la négociation
  (`source_actions`/`action` suivis sans état public intermédiaire), la
  terminalisation par `finish` (v3) au drop accepté.
- Drag **source** et clipboard : hors tranche 1 — aucune extension
  implicite (roadmap). `start_drag`/`set_selection` ne sont jamais émis.

### D2 — Lecture hors callback, fd non bloquant
- `receive(mime)` rend un fd possédé par le destinataire : lecture sur un
  thread dédié au transfert (jamais dans la callback native), en
  non-bloquant via poll du fd avec timeout borné, chunks ≤ maxChunkBytes,
  budget total du transfer respecté (`DropItemReadMode.SourceOwned`).
- Terminaisons observables distinctes : refus (mime non accepté),
  abandon utilisateur (leave sans drop), erreur de transport (EPIPE/EOF
  prématuré), source disparue (offer destroy), destinataire fermé
  (cancel de la lecture + close du fd) — uniquement ce que le protocole
  permet d'observer.

### D3 — Lifetimes bornés, corrélation stricte
- Une offre détruite ne ressert jamais : l'identité `DropOfferId` est
  assignée par le runtime, le proxy kffi est libéré (`destroy`) dès la
  terminalisation ; les fds ouverts sont fermés idempotemment ; un
  résultat tardif (lecture finissant après terminalisation) est corrélé
  à l'owner et annulé silencieusement côté publication.

### D4 — Leçons 2-6
- Array-flags/signatures exactes, aucun état global, listeners un par
  proxy avec copies, rejets déterministes codes stables.

## Tables de pannes

| ID | Panne | Garde-fou |
|---|---|---|
| F1-1 | enter avec offer nulle | offre ignorée, trace |
| F1-2 | drop sans accept préalable | terminaison refus, pas de receive |
| F1-3 | leave pendant lecture | lecture annulée, fd fermé, offer destroy |
| F2-1 | données > budget | troncature refusée : erreur budget, transfert terminal |
| F2-2 | fd bloquant sans données | poll borné, timeout = erreur transport |
| F2-3 | mime non annoncé demandé | rejet avant receive |
| F3-1 | receive puis destroy immédiat | corrélation owner, publication annulée |

## Scénarios de gate

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | drop accepté bout-à-bout (client externe → surface) | O3 |
| P-2 | drop refusé (mime non supporté) | O3 |
| SN-1 | transfert interrompu (EOF prématuré) | O2 |
| SN-2 | budget dépassé | O2 |
| SN-3 | teardown pendant lecture | O2 |

L'oracle O3 P-1/P-2 exige une source externe de drop (harnais
`wl_data_source` côté test ou outil CLI) — le résiduel d'injection
pointer s'applique au drag inter-client, une source propre au harnais
reste injectable sans seat focus.

## Allocation de contrats prévue
`DRP-002` (offres immuables + négociation + terminalisation),
`DRP-003` (transferts : budget, interruption, lifetimes). Numéros à
revérifier au registre.

## Hors périmètre (tranche 1)
Drag source, clipboard (`set_selection`), actions compositor custom,
per-surface mime dynamique pendant le survol.
