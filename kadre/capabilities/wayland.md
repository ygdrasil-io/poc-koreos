# Registre versionné d'implémentation — adapter Wayland (JVM 25, glibc et musl)

**Mandat.** Ce registre applique le §8 de `BACKEND-CAPABILITIES.md` : une ligne
par feature de la matrice §4, l'état livré par phase, avec le minimum testé,
les gates compile/runtime, l'état absent exact et les tests. La source des
planchers d'environnement est la baseline B-1..B-6 de
`WAYLAND-PHASE-0-KFFI-AUDIT.md` ; les décisions D1-D3 et le probe R1-R8 sont
posés dans `WAYLAND-PHASE-0-DECISIONS.md` ; la tranche livrée est décrite par
`WAYLAND-PHASE-1-HOST-DESIGN.md`.

## 1. Lecture des colonnes

| Colonne | Sens |
|---|---|
| feature | membre public tel que déclaré |
| target | `jvm` (Linux glibc/musl, JVM 25) |
| minimum déclaré | environnement réellement exercé : JVM 25, glibc 2.39 (Noble) pour la preuve compositor, musl ≥ 1.2.5 pour les symboles POSIX |
| compile gate | tâche Gradle / symbole compilé |
| runtime gate | contrat + scenario de preuve exigés (contrats actifs BCK-017/SES-001/SUR-001, gate Linux `kadre-wayland-contracts`) |
| état absent | valeur exacte observée du refus, sans paraphrase |
| tests | ids de scénarios + classes de test |

## 2. Registre — tranche 1 (headless standalone)

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| discovery du provider (`DesktopBackend.Wayland`, SPI `DesktopBackendProvider`) | `jvm` | JVM 25 ; probe R1-R2 (aucune charge native) | `:kadre:backend:wayland:jvmTest` | `BCK-017` — `wayland-provider-discovery` | `isAvailable()` = `false` hors Linux, raison `platform` | `WaylandBackendProviderTest.discoveryFindsExactlyOneWaylandProvider`, `WaylandProbeTest.nonLinuxIsUnavailableRegardlessOfEndpoint` |
| probe `isAvailable()` — endpoint `WAYLAND_SOCKET` / socket nommé | `jvm` | R3-R6 ; fd jamais consommé | `:kadre:backend:wayland:jvmTest` | `BCK-017` — `wayland-probe-socket-parse`, `wayland-probe-socket-invalid`, `wayland-probe-runtime-dir-absent`, `wayland-probe-socket-absent`, `wayland-probe-absolute-display` | fd non entier = `false` raison `wayland-socket-invalid` ; `XDG_RUNTIME_DIR` absent = `false` raison `runtime-dir-absent` | `WaylandProbeTest` (13 cas) |
| session standalone headless (`runKadreApplication`, `primarySurface = null`) | `jvm` | compositor réel pour la preuve O3 (Weston headless, B-5) | `:kadre:backend:wayland:waylandNativeTests` | `SES-001` — `wayland-session-completes-headless`, `wayland-session-cycles-on-weston`, `wayland-stop-from-other-thread` | échec avant session = `PlatformFailure(Wayland, "start", <code>)` ; embedded = `Unsupported(HostAttach)` | `WaylandNativeSessionLinuxTest` (Linux), `WaylandBackendProviderTest` (O2) |
| pompe `libwayland-client` (prepare/read/cancel/flush, wake réarmable) | `jvm` | eventfd ou pipe2/pipe (kffi-posix, audit domaine 4) | `:kadre:backend:wayland:jvmTest` + `waylandNativeTests` | `SUR-001` — `wayland-pump-wake-rearm`, `wayland-pump-stop-during-poll`, `wayland-pump-flush-eagain-watches-write`, `wayland-pump-eintr-keeps-prepared-read` | perte de connexion = `PlatformFailure(Wayland, "display", "connection-lost")` puis `SessionOutcome.Failed` | `WaylandEventPumpTest`, `WaylandNativeSessionLinuxTest.repeatedWakeKeepsThePumpResponsive` |
| lifecycle à trois axes (D3) | `jvm` | sources protocolaires : `xdg_toplevel activated`, `wl_surface enter/leave` — aucune observable en headless | `:kadre:backend:wayland:jvmTest` | `SES-001` — la session headless reste `Background + Inactive` fixe | aucune transition publiée en headless ; aucune visibilité synthétisée | `WaylandBackendProviderTest.completingApplicationStopsTheLoopWithCompletedOutcome` |
| `WindowManager.requestWindow` | `jvm` | — (aucune fenêtre en tranche 1) | `:kadre:backend:wayland:jvmTest` | — | `Unsupported(RequestWindow)` ; `WindowManagerCapabilities.requestWindow` = `Capability.Unsupported` | `WaylandHeadlessWindowManager` (état initial testé via session) |
| `LifecycleCapabilities.memoryPressure` | `jvm` | — (aucune source protocolaire Wayland ; réévaluation possible en phase 9 via une source système) | `:kadre:backend:wayland:jvmTest` | — | `Unsupported` | documenté D3 |
| `WindowDecorations.System` (D1) | `jvm` | — (négociation xdg-decoration attendue phase 5) | — | — | demande `System` refusée exactement `Unsupported(Decorations)`, jamais de repli `Borderless` | documenté D1 |
| capture (phase 10) | `jvm` | — (`KFFI-WAYLAND-003` ouvert) | — | — | `Unsupported(Capture)` tant que le gap kffi est ouvert | documenté `KFFI-REQUIREMENTS.md` |

## 3. Limites de la tranche 1

- Le support `WAYLAND_SOCKET` au run est refusé avant session
  (`PlatformFailure(Wayland, "start", "wayland-socket-unsupported")`) tant que
  `KFFI-WAYLAND-001` (`wl_display_connect_to_fd`) est ouvert ; le probe le
  déclare néanmoins disponible (R3) — contrat R7 en attente du binding.
- SN-4 (compositor amputé d'un global) et SN-8 (version annoncée sous le
  minimal B-4) sont prouvés en O2 sur fakes ; une preuve O3 exige un compositor
  amputé dédié (procédure documentée dans le README du driver).
- Images de test pignées par digest au premier job CI (B-3) ; la version weston
  réelle est journalisée par `scripts/ci-wayland-runtime.sh version`.

## 4. Références

- `WAYLAND-PHASE-1-HOST-DESIGN.md` — scénarios P/SN et tables F1-F3.
- `WAYLAND-PHASE-0-KFFI-AUDIT.md` §Baseline, §verdicts.
- `BACKEND-CAPABILITIES.md` §4 (colonne Wayland), §8 (mandat du registre).
