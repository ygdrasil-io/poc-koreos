# Win32 Phase 1 — Provider, standalone headless et pompe

## But

Design ciblé de la première tranche Windows (`WIN32-IMPLEMENTATION-ROADMAP.md` §5, Phase 1) : provider paresseux, session standalone headless réelle sur Windows — thread UI possédé par le host, fenêtre de contrôle élevée en endpoint de messages, pompe `GetMessageW` avec wake `PostMessageW` — et lifecycle/outcomes conformes à `DESIGN.md`. Le livrable reste explicitement limité au headless : aucune `Window` publique, pas d'input, pas de surface rendue — la garantie Desktop de `requestWindow` n'est pas revendiquée avant la phase 2.

Exclusions de la tranche : fenêtres publiques et interop renderer (phase 2), input (phase 4), IME/texte (phase 6), drop (phase 7), raw input (phase 8). `supportedIntegrations` reste vide : l'embedded n'est pas annoncé.

## Préconditions KFFI

Aucun gap nouveau. La surface messages/fenêtres consommée ici est celle de la phase 0 (audit `WIN32-PHASE-0-KFFI-AUDIT.md`) : downcalls `GetMessageW`/`PeekMessageW`/`PostMessageW`/`TranslateMessage`/`DispatchMessageW`/`MsgWaitForMultipleObjectsEx`/`PostQuitMessage`/`IsWindow`/`DestroyWindow` générés et initialisés paresseusement, `WNDPROC` managée révocable (`managed/Win32WndProcBridge` + `managed/Win32ManagedFunctions`), layouts `WNDCLASSEXW`/`MSG`, `Win32ControlWindow` élevé en endpoint du host. Kadre ne porte aucun stub ni descriptor FFM.

## Décisions de la tranche

- **D1 — Thread UI.** Le thread appelant de `run()` devient le thread UI du host, enregistré par la session. La vérifiabilité du prérequis (roadmap §2.2 : sans prendre arbitrairement « le premier thread appelant » pour le main thread) vient de trois mécanismes possédés : l'exclusivité process-wide du host standalone (lease, `AlreadyInUse` — miroir Wayland), le marshalling de toute commande vers ce thread enregistré, et le refus explicite d'un second host. Aucune API Windows de « thread GUI » n'est consultée.
- **D2 — Endpoint de messages.** La fenêtre de contrôle de la phase 0 (`managed/Win32ControlWindow`, popup jamais affichée) est élevée en endpoint de messages du host. Le réveil de la pompe est un `PostMessageW(WM_APP + 2)` vers cette fenêtre ; la route `WNDPROC` unique du bridge sert au host. Après fermeture, le lease de la route est libéré et la réutilisation séquentielle réinstalle la sienne.
- **D3 — Pompe.** Boucle `GetMessageW` sur le thread UI, les trois résultats traités distinctement : message disponible (`TranslateMessage`/`DispatchMessageW`), `WM_QUIT`, erreur `-1`. Les commandes (arrêt explicite) sont des messages privés du endpoint ; l'arrêt de la pompe est réservé à son owner. `GetMessageW == -1` est prouvé par un port contrôlé — jamais présenté comme une preuve OS.

## Architecture de la tranche

`Win32BackendProvider` (SPI `DesktopBackendProvider`, service descriptor en ressources, aucune charge native pendant la discovery hors Windows — analogue R1 wayland ; `isAvailable()` = OS Windows sans toucher à `init()`) → `Win32HostSession` (lease process-wide exclusif ; thread UI enregistré ; fenêtre de contrôle possédée = endpoint + wake) → pompe (D3) → ingress runtime (`SessionRuntime`, cinq managers explicites, `primarySurface = null`, `stopWhenLastWindowClosed` sans effet headless : la session ne s'arrête pas parce qu'elle n'a jamais eu de fenêtre). Branchement `runKadreApplication` et `attachKadreDesktop` ; `attach` reste `Unsupported(HostAttach)` en tranche 1. Les domaines non livrés restent dans leur état non supporté exact. Composants dans `org.graphiks.kadre.internal.win32` (topologie §4) ; publication agrégée par `platform:desktop` (`runtimeOnly`).

## F1 — Failures avant session

Aucune ressource créée, aucun callback armé, zéro DLL chargée après retour :

| # | Condition | Résultat de `run()` |
|---|---|---|
| 1 | OS non-Windows | failure avant session actionnable, zéro chargement natif (règle phase 0 R1) |
| 2 | host standalone déjà admis (lease process-wide actif) | failure `AlreadyInUse`, le host existant reste propriétaire |
| 3 | `init()` générée en échec (surface native absente) | failure avant session, raison native en diagnostic |
| 4 | création de la fenêtre de contrôle impossible (`RegisterClassExW`/`CreateWindowExW`) | failure avant session, raison native en diagnostic |

## F2 — Panne après admission

Erreur native de pompe (`GetMessageW == -1`) ou failure applicative après admission : terminaison ordonnée des ressources possédées avec la failure autorisée, puis `SessionOutcome.Failed`. Interdits verbatim (roadmap §3.3) : pas de reconnexion, pas de repli vers un autre backend, pas de réutilisation d'IDs. L'ingress ne publie plus rien après l'outcome terminal.

## F3 — Fermeture

`stop` idempotent. Ordre de teardown : (1) admission fermée, dernière commande admise traitée ; (2) quiescence de la `WNDPROC` (révocation avant libération — décisions phase 0 D2) ; (3) fenêtre de contrôle détruite, lease de route libéré ; (4) pompe arrêtée par son owner, `WM_QUIT`/sortie de boucle constatés ; lease process-wide libéré en dernier. Stop depuis un autre thread : `PostMessageW` de wake réveille `GetMessageW`, teardown complet sur le thread UI, `SessionOutcome.Stopped`. Callback tardive après le retour de `stop` : impossible (quiescence en 2).

## Scénarios positifs/négatifs

Observation uniquement par l'API Kadre ; oracle `TEST-STRATEGY.md` §2.1. O2 = port contrôlé injectable (même discipline que le port déterministe AppKit), exécution portable partout ; O3 = runner Windows (`windows-contracts`, gate phase 0).

| ID | Scénario | Observation attendue | Oracle |
|---|---|---|---|
| P-1 | start/stop headless ×3 cycles sur runner Windows | session démarrée/arrêtée, fenêtre de contrôle détruite à chaque cycle, zéro thread restant | O3 |
| P-2 | wake répété (`WM_APP + 2`) sans fenêtre ni événement | pompe réarmée à chaque wake, aucun arrêt implicite | O2 |
| P-3 | discovery macOS/Linux et Windows : provider présent | aucune charge native hors run, `isAvailable()` exact, sélection `Auto` des autres OS intacte | O2 |
| SN-1 | run hors Windows | failure avant session F1-1 actionnable, zéro ressource | O2 |
| SN-2 | second host concurrent pendant une session admise | failure `AlreadyInUse`, le premier host continue | O2 |
| SN-3 | stop pendant `GetMessageW` depuis un autre thread | réveil par wake, teardown F3, `SessionOutcome.Stopped` | O3 |
| SN-4 | `GetMessageW == -1` (port contrôlé) | chemin d'erreur distinct, teardown F2, `SessionOutcome.Failed` — pas une preuve OS | O2 |
| SN-5 | annulation applicative pendant la session headless | teardown F3, outcome `Stopped` | O2 |
| SN-6 | deux exécutions successives | aucun job, thread, lease ni fenêtre partagés | O2/O3 |

Couverture des risques §7 de la roadmap (tranche 1) : boucle bloquée et arrêt (SN-3), `-1` de `GetMessageW` (SN-4), wake perdu/répété (P-2), annulation (SN-5), réutilisation séquentielle (SN-6).

## Allocation prévue de contrats

À écrire dans `contracts.tsv` par le plan exécutable, contre vérification du registre :

| Famille | Sujet | Oracle visé |
|---|---|---|
| BCK-018 | probe `isAvailable()` et discovery Win32 (`Auto → [AppKit, Win32]` sur Windows, refus hors Windows) | O2 |
| SES-002 | lifecycle start/stop headless, panne après admission, arrêt multi-thread | O2/O3 |
| PMP (nouvelle famille ou `SES-003`) | pompe : boucle `GetMessageW`, wake réarmable, arrêt owner-only, `-1` via port contrôlé | O2/O3 |

Le gate `windows-contracts` distingue l'adapter `win32-jvm` (phase 0) : une preuve AppKit ne satisfait jamais un contrat Win32. Tout scénario absent fait échouer le gate ; un `Unsupported` attendu est un test réussi explicite.
