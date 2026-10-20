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

## 3 ter. Registre — tranche 4 (seats, pointeur, clavier brut)

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| seat + routage par surface (`WaylandInputRouter`) | `jvm` | kffi WaylandSeat (capabilities gated, get_pointer/get_keyboard après annonce) | `:kadre:backend:wayland:jvmTest` | `INP-004` | touche hors focus jamais routée ni substituée ; leave ⇒ relâchements synthétiques exactement pour les retenues | `WaylandInputRouterO2Test` (8 preuves) |
| pointeur : batches par `wl_pointer.frame` | `jvm` | coalescence position (dernière gagne), boutons, scroll value120/continu, `axis_stop` préservé | `:kadre:backend:wayland:jvmTest` | `INP-005` | rien publié avant la frame (v5+) ; sans frame : un lot par événement | `WaylandInputRouterO2Test` |
| clavier brut (keymap fd, keys, modifiers, repeat_info) | `jvm` | bindings kffi livrés (tranche 2 XKB pour le texte) | `:kadre:backend:wayland:jvmTest` | `INP-006` | blur publié même sans touches ; unregistration neutralise | `WaylandInputRouterO2Test` |

Résiduel tranche 4 : l'oracle O3 d'injection — sway headless n'accorde pas le
focus pointeur au client (trail complet dans la PR kffi, test parké) ; les
preuves INP-004..006 sont O2, l'O3 attend un compositor qui livre (weston,
uinput) ou la tranche 2 XKB.

## 3 quater. Registre — tranche 5 (interactions et fenêtres avancées)

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| updates conformes (titre, contraintes min/max, fullscreen borderless) | `jvm` | kffi toplevel ops (marshals array-signature) | `:kadre:backend:wayland:jvmTest` | `WIN-013` | min>max rejeté avant primitive ; exclusive hors domaine ; géométrie effective au configure suivant | `WaylandAdvancedWindowO2Test` (4 preuves) |
| move/resize interactifs à serials éligibles | `jvm` | serial d'événement natif consommé une fois | `:kadre:backend:wayland:jvmTest` | `INT-005` | hors callback native ⇒ `InvalidRequest`, zéro émission ; validation serial dépendante du compositor (sway : surface configurée) — l'éligibilité est une discipline consommateur | `WaylandAdvancedWindowO2Test.serialLedgerConsumesEligibleSerialsOnce` |

Résiduel tranche 5 : xdg-decoration (T2, bindings kffi requis),
xdg-activation/icône (`Unsupported` documenté), pointer constraints
(phase 6+), O3 injection (voir résiduel tranche 4).

## 3 quinques. Registre — tranche 6 (text input, IME)

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| port text-input (`zwp_text_input_v3` managé) | `jvm` | kffi text-input bindings (batching done) | `:kadre:backend:wayland:jvmTest` | `TXT-002`, `TXT-003` | offsets octets convertis en UTF-16 sans couper un code point ; done obsolète ignoré intégralement ; leave purge la composition | `WaylandTextInputO2Test` (4), `Utf8Utf16Test` (4) |
| conversion UTF-8 ↔ UTF-16 | `jvm` | pure, sans compositor | `:kadre:backend:wayland:jvmTest` | `TXT-002` — delete-bytes | clamp au début du code point pour un offset mi-séquence | `Utf8Utf16Test` |

Résiduel tranche 6 : l'enter IME exige une surface focusée (résiduel
injection T4) ; touch/gestures/pen hors tranche 1 (protocoles propres).

## 3 septies. Registre — tranche 7 (drag-and-drop, destinataire)

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| présentateur drop (offres immuables, négociation) | `jvm` | kffi WaylandDataDevice/Offer (mavenLocal en cours de phase) | `:kadre:backend:wayland:jvmTest` | `DRP-002` | MIME gelés à l'announce ; drop sans accept ⇒ refus déterministe sans receive ; mime non annoncé refusé avant tout fd | `WaylandDropO2Test` (6 preuves) |
| lifetimes des offres et terminaisons | `jvm` | terminaisons observables uniquement protocolaires | `:kadre:backend:wayland:jvmTest` | `DRP-003` | leave ⇒ LeftSurface avec destroy ; offre détruite ne ressert jamais ; drag source et clipboard absents du catalogue (aucune extension implicite) | `WaylandDropO2Test` |

Résiduel tranche 7 : l'oracle O3 d'un drop extern (P-1/P-2) exige une
source de drag externe ; transferts interrompus/budget/teardown SN-1..3
à couvrir avec le harnais fd.

## 3 octies. Registre — tranche 8 (embedded et lifecycle du host)

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| attach AWT embedded (`AwtEventDispatchThread`) | `jvm` | thread EDT vérifié à l'attach (pas de relocation) | `:kadre:backend:wayland:jvmTest` | `SES-003` | hors EDT ⇒ `InvalidRequest("options")` ; JavaFX ⇒ `Unsupported(HostAttach)` ; fermer la session ne tue pas le scope hôte | `WaylandEmbeddedO2Test` (4 preuves) |
| pompe non bloquante embarquée | `jvm` | poll sur worker IO, ticks bornés sur le thread hôte | `:kadre:backend:wayland:jvmTest` | `SUR-004` | aucun poll sur le thread hôte ; pas de vol de queue toolkit ; ticks ignorés après close | `WaylandEmbeddedO2Test.pumpTickIsBoundedAndClosedPumpRejectsTicks` |

Résiduel tranche 8 : les preuves O3 AWT externes (attach réel, input,
non-blocage UI, teardown) et `supportedIntegrations` announce — JavaFX
exige une intégration toolkit distincte.

## 3 novies. Registre — tranche 9 (services Linux, devices, gamepads)

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| inventaire devices (projection seats) | `jvm` | seats observables + capabilities réelles — PAS un inventaire physique complet (libinput/udev : bindings kffi requis) | `:kadre:backend:wayland:jvmTest` | `INP-007` | kind dérivé des capabilities ; retrait → Disconnected ; clé jamais réutilisée ; lecture post-close vide | `WaylandDevicesO2Test` (4 preuves) |
| gamepads | `jvm` | service evdev process-wide (`:kadre:backend:linux`), cf. tranche 9 bis ci-dessous — remplace le refus honnête GMP-002 (retiré) | `:kadre:backend:linux:jvmTest` | `GMP-003`/`GMP-004` | inventaire evdev honnête (EACCES ⇒ `PermissionDenied`), jamais un vide muet | `EvdevGamepadBrokerTest` + `EvdevGamepadLinuxTest` |
| apparence/thème, pression mémoire | `jvm` | — (aucune source définie en tranche 1) | — | — | `Unknown`/indisponibilité prévue, aucune synthèse | documenté design D1 |
| raw input (pointeur relatif) | `jvm` | évalué, non activé (exige extensions phase 5 + unités/contexte lock) | — | — | `RawInputPort` Unsupported | documenté design D4 |

## 3 decies. Registre — tranche 9 bis (gamepads evdev, service :kadre:backend:linux)

Le service gamepad vit dans `:kadre:backend:linux` (evdev/udev via
`kffi-evdev`) et sert les sessions Wayland : broker process-wide
(identity stable D2, worker IO D3), vues par session, FF exclusif (D4).

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| inventaire + hotplug + identité evdev | `jvm` | détection par capacités kernel (jamais les props udev), clé stable `(bustype,vendor,product,uniq)` au rebranchement | `:kadre:backend:linux:jvmTest` | `GMP-003` | nœud non-gamepad exclu ; EACCES ⇒ `PermissionDenied(InputMonitoring)` honnête | `EvdevGamepadBrokerTest` (6 preuves O2) + `EvdevGamepadLinuxTest` P-1/P-2 (O3 uinput) |
| état + force feedback evdev | `jvm` | projection standard-map canonique (dpad/gâchettes boutons → axes), cycle FF upload→play→stop→erase avec ownership exclusif | `:kadre:backend:linux:jvmTest` | `GMP-004` | état strictement conforme au descripteur ; sans FF ⇒ `Unsupported(GamepadEffect)` | `EvdevGamepadBrokerTest` (5 preuves O2) + `EvdevGamepadLinuxTest` P-3/P-4 (O3 uinput) |

Résiduel tranche 9 bis : les preuves O3 (oracle uinput D6) exigent un
conteneur privilégié (`/dev/uinput`, mknod du nœud) et ne tournent pas
sur le runner de gate ; les contrats GMP-003/GMP-004 s'appuient donc sur
les preuves O2 portables, l'oracle uinput restant la vérité Linux locale
(documenté dans `kadre/LINUX-EVDEV-GAMEPAD-DESIGN.md`). Le hotplug udev
exige un démon udev vivant — le filet de sécurité est la réconciliation
seconde des nœuds énumérés. GMP-002 (refus honnête wayland) est retiré
du registre (`retirementRef` → `GMP-003`) : le service evdev le remplace.

## 3 undecies. Registre — tranche 10 (capture portal, contrôle)

Le plan de contrôle capture vit dans `:kadre:backend:linux` : D-Bus en
pur protocole (D2, sockets `kffi-posix`), disponibilité observée (D1),
`HostPickerOnly` tant que le pipeline PipeWire n'est pas prouvé (D4).

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| codec + connexion D-Bus | `jvm` | wire format v1 (alignements, variantes, dict entries), SASL EXTERNAL (uid hex ASCII), serials/corrélations, fds SCM_RIGHTS, refus uint64 | `:kadre:backend:linux:jvmTest` | `CAP-003` | round-trip byte-pour-byte ; sériels croisés jamais répondus ; refus picker explicite ; indice `'h'` hors domaine ⇒ fd refusé | `DbusCodecTest` (6) + `DbusConnectionTest` (8) |
| contrôle portal ScreenCast | `jvm` | CreateSession/SelectSources/Start corrélés par le `handle_token` du corps (une seule vérité), OpenPipeWireRemote → fd réel | `:kadre:backend:linux:jvmTest` | `CAP-002` | auth échouée/portail absent ⇒ `Unavailable` explicite ; refus picker ⇒ `PermissionDenied` ; cibles non-HostChoice refusées | `LinuxCapturePortTest` (9) + `LinuxCapturePortalLinuxTest` (O3 dbus-daemon réel) |
| stream PipeWire (SHM/CPU) | `jvm` | bindings `kffi-pipewire` (kextract), codec SPA POD pur Kotlin, négociation par états, frames copiées avant réenfilage, livraison bornée sur thread dédié | `:kadre:backend:linux:jvmTest` | `CAP-004` | format hors sous-ensemble portable ⇒ terminaison honnête ; budget dépassé ⇒ `ResourceLimitExceeded` ; layout divergent ⇒ refus ; pas de frame après close | `SpaPodTest` (9) + `PipeWireStreamClientTest` (10) + O3 conteneur (daemon pipewire réel) |

Résiduel tranche 10 : la livraison de frames jusqu'au bout est prouvée
en O2 sur seam et le chemin O3 réel est tracé jusqu'au stream PAUSED
(portal complet + fd PipeWire réel via SCM_RIGHTS, connexion du core,
événements natifs) — la complétion de la négociation bloque sur la
policy de linkage vidéo du conteneur (un consommateur de référence en C
pur contre le même node `videotestsrc` converge exactement : code kadre
fidèle, gap environnemental, harnais O3 livré dans
`LinuxCaptureStreamLinuxTest`). Restent ensuite : variantes portal
réelles GNOME/KDE/wlroots (gate de sortie phase 10), DMA-BUF (preuve de
mapping), `CaptureTarget.Source`/`Surface` (chemin exact non prouvé).
Les sockets abstract D-Bus et `t` (uint64) sont refusés explicitement.

## 3 duodecies. Registre — tranche 11 (compatibilité, distribution, fermeture)

Phase de clôture (design `WAYLAND-PHASE-11-CLOSURE-DESIGN.md`) : la gate
bloquante reste Sway (wlroots) ; la matrice de compositors et le stress
tournent en acceptation dédiée (`kadre-wayland-compositor-matrix.yml`,
dispatch + nocturne), chaque famille publiant sa version réelle (B-3).

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| matrice de compositors (sway, weston) | `jvm` | `ci-wayland-runtime.sh` sélecteur `KADRE_WAYLAND_COMPOSITOR` ; sway 1.9 / weston 13 (noble) validés sur les preuves O3 session/fenêtres/metrics | `waylandNativeTests` par famille | matrice (acceptation) | famille inconnue ⇒ refus explicite du provisionneur (exit 64) ; mutter/kwin : résiduel documenté | `WaylandNativeSessionLinuxTest`, `WaylandWindowsLinuxTest`, `WaylandMetricsLinuxTest` sous chaque famille |
| stress : rafales de fenêtres, interruption du compositor, teardown répété | `jvm` | 6×8 fenêtres, SIGKILL du compositor dédié ⇒ `Failed(PlatformFailure(Wayland,"display","connection-lost"))`, 12 cycles < 90 s, fds stables (`/proc/self/fd`) | `waylandStressTests` (Linux) | acceptation | aucune fuite tolérée au-delà de +2 fds (sockets de rapport) ; jamais de callback après terminaison | `WaylandStressLinuxTest` (3 preuves O3) |
| libc : glibc qualifié, musl refusé | `jvm` | glibc 2.39 (gate + matrice) ; musl 1.2.5 : session verte puis SIGSEGV déterministes dans `libwayland-client` (metrics, fenêtres) | — | — | musl NON publié comme supporté — résiduel d'investigation, pas un non-support définitif | hs_err pinés dans le design (D2) |
| consumers Kotlin/Java contre l'artefact publié | `jvm` | compilation transitive depuis le repository contractTest (`validateKotlinConsumer`, `validateJavaConsumer`) | job `consumers` de la matrice | — | exécution headless du consumer : résiduel (graphe runtime non couvert par le repo contractTest, `win32:1.0.0` sans snapshot) | tâches GradleBuild |

Résiduel tranche 11 : mutter et kwin headless en conteneur (familles GNOME/
KDE), la qualification musl (crash libwayland-client à investiguer côté
kffi-wayland), la preuve d'exécution des consumers, les contrats CLS-001/
CLS-002 (enregistrement après couverture matrice), et le passage ligne par
ligne de la checklist §9 de la roadmap.

## 4. Limites des tranches 1 à 4

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
