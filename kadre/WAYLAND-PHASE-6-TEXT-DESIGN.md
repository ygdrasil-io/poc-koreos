# Wayland — Phase 6 : text input et IME (text-input-v3)

Design de la tranche 1 de la phase 6 (roadmap §Phase 6). Prérequis :
phase 4 (#475 — seats), phase 5 (#477 — interactions). Touch/gestures/pen
sont des sous-tranches indépendantes ultérieures.

## Décisions fermées

### D1 — IME = text-input-v3 uniquement, par surface
- Le port kadre (`TextInputPort`) s'ouvre par surface ; l'objet
  `zwp_text_input_v3` est créé via le manager (`get_text_input(seat)` —
  un seat réel est requis). `enable`/`disable`/`commit` suivent la
  discipline du protocole : les requêtes d'état (surrounding, content
  type, cursor rect) sont posées PUIS scellées par `commit`.
- `enter`/`leave` pilotent le focus IME ; `leave` purge la composition
  courante (publication `CompositionChanged(range=null)`) et désarme les
  callbacks — jamais de preedit résiduel après blur.

### D2 — Offsets UTF-8 ↔ indices UTF-16 (conversion explicite)
- Les payloads du protocole sont en **octets UTF-8** (preedit cursor,
  delete_surrounding before/after, surrounding anchor/cursor). Le modèle
  Kadre est en **indices UTF-16** (String). Une classe de conversion
  dédiée (`Utf8Utf16`) fait le pont : bornes de payload respectées,
- jamais couper un code point (les limites tombent sur des limites de
  code point après conversion) ; emoji (4 octets/surrogate pair),
  accents combinés et suppression autour du curseur sont testés en O2
  pur (sans compositor).

### D3 — Batch `done`, pas de double commit
- Les événements IME (preedit/commit_string/delete_surrounding) sont
  accumulés puis publiés en UNE observation atomique à chaque `done(serial)`
  (batching protocolaire). Le `serial` du done sert de base de révision
  observable — un batch obsolète (revision dépassée) est ignoré, pas
  appliqué partiellement.
- Le chemin clavier brut (phase 4) et l'IME ne doublent pas : un
  `commit_string` IME est une observation `Replace` ; les keys brutes
  restent des keys brutes — la fusion relève du runtime, pas du backend.

### D4 — Sous-sets honnêtes
- `capability` = Supported uniquement si le manager v3 est annoncé ; sinon
  Unsupported — l'absence d'IME ne désactive ni clavier ni pointeur.
- Touch/gestures/pen : hors tranche 1 (protocoles propres, sous-tranches
  indépendantes).

## Tables de pannes

| ID | Panne | Garde-fou |
|---|---|---|
| F1-1 | leave IME avec composition active | composition purgée, publication range=null |
| F1-2 | done obsolète (serial dépassé) | batch ignoré intégralement |
| F1-3 | enter sans surface enregistrée | ignoré, trace |
| F2-1 | offset UTF-8 au milieu d'un code point | clamp à la limite de code point la plus proche |
| F2-2 | payload immensément grand | limites bornées, troncature honnête documentée |
| F2-3 | delete_surrounding au-delà des bornes | clamp aux limites du document |
| F3-1 | commit IME + key simultanés | publication séquentielle, ordre d'arrivée |

## Scénarios de gate

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | cycle enable/disable/focus sur sway avec IME | O3 |
| P-2 | preedit → commit → done en un batch | O2/O3 |
| SN-1 | conversion UTF-8/UTF-16 : emoji, combinés, suppression | O2 |
| SN-2 | leave purged, done obsolète ignoré | O2 |

## Allocation de contrats prévue
`TXT-002` (IME v3 batching + conversion), `TXT-003` (focus/purge).
Numéros à revérifier au registre.

## Hors périmètre (tranche 1)
Touch, gestures, pen, input-method-v2 (côté serveur), xdg-decoration
(résiduel phase 5), XKB (tranche 2 clavier).
