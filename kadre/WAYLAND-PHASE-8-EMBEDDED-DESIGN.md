# Wayland — Phase 8 : embedded et lifecycle du host

Design de la phase 8 (roadmap §Phase 8). Prérequis : phases 2 et 4
stabilisées (#460/#475).

## Décisions fermées (tranche 1 : AWT embedded)

### D1 — Attach AWT uniquement, vérification du thread réel
- `attach(DesktopEmbeddedRequest)` accepte uniquement
  `AwtEventDispatchThread` et exige que le thread appelant SOIT l'EDT
  (`EventQueue.isDispatchThread()`) — pas de relocation cachée : un
  attach depuis un autre thread suit `InvalidRequest("options")`, tout
  autre intégration suit `Unsupported(HostAttach)`.
- La session embarquée ne démarre PAS de boucle bloquante : la pompe
  tourne en mode non bloquant, pilotée par l'EDT.

### D2 — Pompe non bloquante sans vol de queue native
- Attente des fd sur un worker dédié (`Dispatchers.IO`) : le worker
  poll (display + wake) et poste un réveil borné vers l'EDT
  (`EventQueue.invokeLater`), qui exécute UN cycle
  `dispatchPending → actions → flush` par tick. La queue AWT reste
  propriété du toolkit ; Kadre ne pompe jamais les événements AWT et
  ne bloque jamais l'EDT dans un poll.
- `requestStop()` réveille le worker, la boucle EDT se termine
  proprement ; le worker est annulé au teardown.

### D3 — Conformité des interactions et leases
- Les commandes fenêtre restent marshalées vers l'EDT (la discipline
  « thread pompe » devient « thread EDT ») ; les leases
  `withDesktopHandle` s'exécutent sur l'EDT pendant le tick — bornées
  au tick, jamais à travers un poll.
- `supportedIntegrations` n'annonce `AwtEventDispatchThread` QUE si la
  conformité est démontrée (gate O3) — jusqu'ici l'ensemble reste
  vide côté découverte, l'attach tente et refuse normativement.

### D4 — Lifecycle multi-sessions
- Fermer la session Kadre détache le contrôleur, arrête le worker et
  draine les ressources — la boucle AWT vit toujours, d'autres
  sessions peuvent s'attacher (lease par session, pas global host).
- Le standalone (`run`) garde son lease host exclusif actuel.

## Tables de pannes

| ID | Panne | Garde-fou |
|---|---|---|
| F1-1 | attach hors EDT | InvalidRequest("options") avant toute connexion |
| F2-1 | poll bloquant l'EDT | le poll vit sur le worker, l'EDT ne fait que des ticks bornés |
| F2-2 | queue AWT volée | Kadre ne pompe jamais AWT ; invokeListé uniquement |
| F3-1 | session fermée pendant un tick | ticks suivants ignorés, teardown idempotent |
| F3-2 | double attach | lease par session AlreadyInUse(Host) |

## Scénarios de gate

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | attach EDT → session → detach, la boucle AWT survit | O2 (fake EDT) / O3 |
| P-2 | input fenêtre pendant l'embed, sans blocage UI | O3 |
| SN-1 | attach hors EDT refusé | O2 |
| SN-2 | JavaFX / intégration inconnue → Unsupported(HostAttach) | O2 |
| SN-3 | fermer Kadre ne tue pas l'EDT | O2 |

## Allocation de contrats prévue
`SES-003` (attach/detach embedded lifecycle), `SUR-004` (pompe non
bloquante sans vol de queue). Numéros à revérifier au registre.

## Hors périmètre (tranche 1)
JavaFX (`JavaFxApplicationThread` — l'Application Thread exige une
intégration toolkit distincte), Swing replumbing, multi-display AWT.
