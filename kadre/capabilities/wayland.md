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

## 3. Registre — tranche 2 (fenêtres réelles, driver `RuntimeWindowManager`)

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| `WindowManager.requestWindow` (borderless, création réelle surface+xdg+toplevel+shm) | `jvm` | compositor réel pour la preuve O3 (Weston headless) ; handshake xdg 10 s | `:kadre:backend:wayland:jvmTest` + `waylandNativeTests` | `WIN-010`, `SUR-002` | `decorations=System`, `outerPosition`, fullscreen, level, boutons, transparence, blur, icône, content-protection refusés normativement avant toute primitive (`PlatformFailure(Wayland,"window-create",<code>)`) ; échec native ⇒ zéro résidu | `WaylandWindowO2Test` (O2), `WaylandWindowsLinuxTest` (O3) |
| fermeture non-interceptable (L914) : close compositor et close programmatique | `jvm` | stimulus `xdg_toplevel.close` → `nativeClosed` ; destroy ordonné toplevel→xdg→surface→buffer | `:kadre:backend:wayland:waylandNativeTests` | `WIN-011` | pas de `CloseRequested` sur close compositor ; double terminaison inoffensive | `WaylandWindowsLinuxTest.secondCloseAfterTerminalCloseKeepsTheSessionHealthy`, `twoWindowsAreMappedAndIndependentWithDeterministicSuccession` |
| succession `primary` + arming `stopWhenLastWindowClosed` (P-3) | `jvm` | armé après la première admission ; transition non-vide → vide ⇒ arrêt | `:kadre:backend:wayland:jvmTest` | `WIN-012` | aucun arrêt tant qu'une fenêtre survit (sentinelle `WIN-012`) | `WaylandWindowO2Test.closingWindowsStopsTheSessionOnlyWhenTheLastOneCloses` |
| lease `withDesktopHandle` (adresses `wl_display`/`wl_surface` réelles) | `jvm` | adresses valides uniquement pendant le block ; refus `Closed(Window)` après fermeture admise (SN-8) | `:kadre:backend:wayland:jvmTest` + `waylandNativeTests` | `SUR-002` — `wayland-surface-handle-lease-addresses`, sentinelle `wayland-surface-lease-refused-after-close` | block jamais exécuté après fermeture admise | `WaylandWindowO2Test.handleLeaseIsHonoredWhileActiveAndRefusedAfterClose`, `WaylandWindowsLinuxTest` (adresses non nulles) |
| mises à jour de fenêtre (`WindowUpdate`) | `jvm` | — (tranche 2 sans update natif) | `:kadre:backend:wayland:jvmTest` | — | toute demande ⇒ `WindowUpdateCommand.rejected` (phase 3 : redraw coalescent) | couvert par le port (rejet explicite) |

## 3 bis. Registre — tranche 3 (métriques, outputs, scale, redraw)

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| inventaire outputs (`DisplayPort` via `wl_output` managé) | `jvm` | sway headless ; batch all-or-nothing au `done` | `waylandNativeTests` | `DSP-002` (P-1, P-3 ajout) | `workArea = null` honnête ; outputs sans mode courant exclus, jamais scale 0 ; primaire retiré non substitué | `WaylandMetricsLinuxTest` (O3), `WaylandMetricsO2Test` (O2) |
| scale de surface (`set_buffer_scale` v6) | `jvm` | sway `output scale 2` en cours de session | `waylandNativeTests` | `DSP-003` (P-2) | scale publié = `wl_output.scale` (entier) ; buffer = logique × scale ; pas d'upscale flou | `WaylandMetricsLinuxTest.scaleChangeMidSessionRepublishesTheInventory` |
| redraw coalescent (`wl_surface.frame` pacing) | `jvm` | N demandes ⇒ 1 commit ; jamais horloge ni preuve de présentation | `:kadre:backend:wayland:jvmTest` | `SUR-003` (F2-2, SN-1) | surface sans frame : demande reste sale | `WaylandMetricsO2Test.redrawRequestsCoalesceIntoOneFrameCommit` |

## 4. Limites des tranches 1, 2 et 3

- Le support `WAYLAND_SOCKET` au run est refusé avant session
  (`PlatformFailure(Wayland, "start", "wayland-socket-unsupported")`) tant que
  `KFFI-WAYLAND-001` (`wl_display_connect_to_fd`) est ouvert ; le probe le
  déclare néanmoins disponible (R3) — contrat R7 en attente du binding.
- SN-4 (compositor amputé d'un global) et SN-8 (version annoncée sous le
  minimal B-4) sont prouvés en O2 sur fakes ; une preuve O3 exige un compositor
  amputé dédié (procédure documentée dans le README du driver). Le configure
  ancien (F1-5, pas de double ack) est implémenté dans l'adaptateur kffi ; sa
  preuve O3 dédiée attend le même harnais.
- `configure` non-imposé (F1-4) : la taille du spec est conservée — aucune
  observation géométrique n'est publiée en tranche 2 (les readbacks
  géométriques suivent en phase 3 avec le redraw).
- Le diagnostic `wl_surface.enter` non observé sur certains runners x86 reste
  ouvert côté kffi (la preuve de mapping est le presentation frame, pas
  `enter`).
- Images de test pignées par digest au premier job CI (B-3) ; la version weston
  réelle est journalisée par `scripts/ci-wayland-runtime.sh version`.

## 5. Références

- `WAYLAND-PHASE-1-HOST-DESIGN.md` — scénarios P/SN et tables F1-F3.
- `WAYLAND-PHASE-0-KFFI-AUDIT.md` §Baseline, §verdicts.
- `BACKEND-CAPABILITIES.md` §4 (colonne Wayland), §8 (mandat du registre).
