# Wayland Phase 1 — Host, standalone headless et pompe

## But

Design ciblé de la première tranche Wayland (`WAYLAND-IMPLEMENTATION-ROADMAP.md` §5, Phase 1) : provider paresseux, session standalone headless réelle sur Linux (Weston headless, baseline B-5), connexion/registry/négociation/dispatch/wake/arrêt conformes à `libwayland-client`, et première preuve Linux. Le livrable reste explicitement limité au headless : ni fenêtre, ni input, ni surface rendue — la garantie Desktop complète de `requestWindow` n'est pas revendiquée avant la phase 2.

Exclusions de la tranche : fenêtres et interop renderer (phase 2, cadré par D2), input (phase 4, bloqué par `KFFI-WAYLAND-002`), capture (phase 10, bloquée par `KFFI-WAYLAND-003`).

## Préconditions KFFI

- `KFFI-WAYLAND-001` (`wl_display_connect_to_fd`, statut `pending review`) : **conditionne uniquement le support `WAYLAND_SOCKET` au run** — règles R3/R7 de `WAYLAND-PHASE-0-DECISIONS.md`. Tant qu'il est ouvert, un run avec `WAYLAND_SOCKET` échoue avant session (table F1, ligne 4) ; le reste de la tranche 1 (connexion par nom de socket) est indépendant et non bloqué — règle « un manque bloque sa tranche, pas les domaines indépendants ».
- Bindings déjà suffisants pour le reste de la tranche (audit `WAYLAND-PHASE-0-KFFI-AUDIT.md` §Conclusion, snapshots pignés) : pompe complète, registry/bind/proxies, listeners bruts + upcallStub, `PosixSymbols`/`PosixWakeup`/`PollFd`, `wl_compositor`/`xdg_wm_base` générés. Aucun autre gap n'ouvre cette tranche.
- Owner de listeners (quiescence avant destruction, copie des payloads, isolation d'exception) : responsabilité Kadre sur les primitives existantes — composant de la tranche, pas une précondition KFFI.

## Architecture de la tranche

`WaylandBackendProvider` (SPI `DesktopBackendProvider`, service descriptor enregistré en ressources, aucune charge native pendant la discovery sur macOS/Windows — probe R1) → `WaylandHostSession` (connexion dédiée possédée par Kadre, registry durable, négociation des versions selon baseline B-4) → pompe (ordre contractuel `libwayland-client` : vider les pending, `prepare_read`, flush, poll sur fds display+wake, puis `read_events`/`cancel_read` ; `EAGAIN` au flush surveille aussi l'écriture ; `EINTR`, réveil applicatif, arrêt et erreurs de fd ont des chemins distincts) → ingress runtime (`SessionRuntime`, ports existants). Branchement `runKadreApplication` avec `primarySurface = null` et managers explicites ; session initialement headless = `Background + Inactive` (D3) et ne s'arrête pas parce qu'elle n'a jamais eu de fenêtre. Wake réarmable `PosixWakeup` (audit domaine 4). Les composants restent dans `org.graphiks.kadre.internal.*` (topologie §4).

## F1 — Failures avant session

Aucune ressource créée, aucun callback armé, zéro fd/zéro proxy fui après retour :

| # | Condition | Résultat de `run()`/`attach()` |
|---|---|---|
| 1 | socket absent/inconnectable (`wl_display_connect` échoue) | failure avant session, vocabulaire `OPERATION-CONTRACTS.md` (`InvalidRequest`/`PlatformFailure` selon la frontière), diagnostic portant la raison R4 du probe |
| 2 | `XDG_RUNTIME_DIR` absent au run (endpoint R4 non résolu) | failure avant session, raison `runtime-dir-absent` |
| 3 | global requis absent du registry (`wl_compositor`, `xdg_wm_base` — baseline B-4) | failure avant session « protocole requis absent », nom du global manquant en diagnostic |
| 4 | `WAYLAND_SOCKET` défini au run et `KFFI-WAYLAND-001` ouvert | failure avant session « wayland-socket non supporté », **sans** repli par nom de socket ni consommation du fd |
| 5 | version annoncée d'un global < minimal requis (B-4) | failure avant session de négociation, interface et versions (annoncée/requise) en diagnostic |
| 6 | primitive de wake absente (ni eventfd ni pipe2/pipe — audit domaine 4) | failure avant session « primitive de réveil indisponible » ; un host sans wake n'admets pas |

## F2 — Panne après admission

Erreur `wl_display` ou déconnexion du compositor après admission : terminaison ordonnée des ressources possédées avec la failure autorisée, puis `SessionOutcome.Failed` (`Application.kt:66`). Interdits verbatim (roadmap §3.3) : pas de reconnexion transparente, pas de migration vers X11, pas de réutilisation d'IDs ; toute nouvelle connexion exige un nouveau host. L'ingress ne publie plus rien après l'outcome terminal ; les listeners sont admis-clos avant la destruction des proxies.

## F3 — Fermeture

`stop` idempotent (retours multiples sûrs). Ordre de teardown : (1) plus aucun callback éligible — admission fermée, dernière callback admise attendue ; (2) destruction des proxies dans l'ordre d'ownership ; (3) flush final ; (4) fermeture des fds (socket ou fd `WAYLAND_SOCKET` — R7, réutilisé puis fermé exactement une fois — et fds du wake, chacun un owner idempotent `PosixWakeup`). Callback tardive après le retour de `stop` : interdite (quiescence en (1) la rend impossible). Arrêt depuis un autre thread et annulation : couverts par SN-5/SN-7 ; le wake sert de réveil du poll.

## Scénarios positifs/négatifs

Exécutés sur Weston headless (baseline B-3/B-5) ; l'observation passe uniquement par l'API Kadre ; l'oracle est au niveau exigé par `TEST-STRATEGY.md` §2.1 :

| ID | Scénario | Observation attendue | Oracle |
|---|---|---|---|
| P-1 | start/stop headless ×3 cycles sur Weston | session démarrée/arrêtée, zéro fd fui (`/proc/self/fd` compté), zéro callback tardive | O3 |
| P-2 | wake répété sans fenêtre ni événement compositor | pompe réarmée à chaque wake (roadmap §3.3) | O2 |
| P-3 | discovery sur macOS/Windows (provider présent) | aucune charge native, `isAvailable()` conforme R1-R2, sélection `Auto` Linux intacte | O2 |
| SN-1 | compositor absent (socket inexistant) | failure avant session F1-1, zéro ressource | O3 |
| SN-2 | `WAYLAND_SOCKET="pas-un-fd"` puis run valide | run utilise l'endpoint normal ; le fd fictif n'a jamais été touché (R3) | O3 |
| SN-3 | `WAYLAND_SOCKET=<fd valide>` au run | fd réutilisé puis fermé exactement une fois (R7) — **activé dès que `KFFI-WAYLAND-001` ferme** | O3 |
| SN-4 | global `xdg_wm_base` absent (compositor amputé) | failure avant session F1-3 « protocole requis absent » | O3 |
| SN-5 | stop pendant `poll` depuis un autre thread | réveil par wake, teardown complet, `SessionOutcome.Stopped` | O2 |
| SN-6 | flush saturé (`EAGAIN`) puis drain | surveillance de l'écriture, aucun blocage, aucun payload perdu | O2 |
| SN-7 | déconnexion du compositor après admission | teardown ordonné + `SessionOutcome.Failed`, pas de reconnexion (F2) | O3 |
| SN-8 | version annoncée < minimal requis (B-4) | failure avant session F1-5 avec versions en diagnostic | O3 |

Couverture des risques §7 de la roadmap (tranche 1) : lecture préparée abandonnée et stop pendant poll (SN-5), flush saturé (SN-6), wake perdu/répété (P-2), fds fermés (P-1, SN-3).

## Allocation prévue de contrats

Sans ID réservé ni écrit dans `contracts.tsv` (contrainte Phase 0) ; les numéros seront alloués au plan exécutable de Phase 1 en vérifiant le registre :

| Famille (`TEST-STRATEGY.md` §3) | Sujet prévu | Oracle visé | Distinction de target exigée |
|---|---|---|---|
| BCK | probe `isAvailable()` et discovery Wayland (R1-R8, `Auto → [Wayland, X11]`) | O2 | target = adapter wayland, libc (glibc/musl), compositor+version, protocoles testés |
| SES | lifecycle start/stop headless, panne après admission, arrêt multi-thread | O2 | idem |
| SUR | pompe : prepare/read/cancel/flush, wake réarmable, quiescence des listeners | O3 | idem |

Règle §7 de la roadmap, applicable dès l'écriture du producteur de preuves : le gate Wayland distingue adapter, libc, compositor/version et protocoles testés — une preuve AppKit `jvm` ne satisfait jamais un contrat Wayland ; le schéma/agrégateur du validateur évolue **avant** activation si nécessaire (décision à porter par le plan de Phase 1, hors périmètre Phase 0). Tout test absent, ignoré, ou compositor requis indisponible fait échouer le gate ; un `Unsupported` attendu est un test réussi explicite, pas un skip.
