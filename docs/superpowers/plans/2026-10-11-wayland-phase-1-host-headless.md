# Wayland Phase 1 — Host, standalone headless et première preuve Linux — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer la première tranche Wayland de la nouvelle architecture : provider paresseux découvert par le SPI existant, session standalone headless réelle sur Linux (connexion par nom, registry durable, négociation, pompe `libwayland-client`, wake réarmable, arrêt multi-thread), preuve O3 sur Weston headless — sans fenêtre ni input.

**Architecture:** Un module `:kadre:backend:wayland` (plugin ServiceLoader, `runtimeOnly` de `:kadre:platform:desktop`) contient le provider et son host : `WaylandProbe` (R1-R8 pur, injecté) → `WaylandHostSession` (connexion/registry/négociation via un pont KFFI injectable) → `WaylandEventPump` (machine à états préparée/lu/annulée, wake `PosixWakeup`). Les tests O2 (modèle, macOS-exécutables) pilotent la logique ; les tests O3 (Linux-only, `waylandNativeTests`) tournent contre Weston headless en Docker glibc localement et sur `ubuntu-latest` en CI. Preuves : contrats `BCK-015`/`SES-001`/`SUR-001`, `evidence.tsv`, tâches validateur, workflow + scripts.

**Tech Stack:** Kotlin/JVM 25, KMP module `jvm()`, `explicitApi()`, kffi-wayland + kffi-posix `1.0.0-SNAPSHOT` (snapshots pignés dans `WAYLAND-PHASE-0-KFFI-AUDIT.md`), FFM, Weston headless pixman (Docker `eclipse-temurin:25-jdk-noble` + apt `weston` localement ; ubuntu-latest en CI), kotlin.test, Gradle 9.8.

**Spec:** `kadre/WAYLAND-PHASE-1-HOST-DESIGN.md` (scénarios P-1..P-3/SN-1..SN-9, tables F1/F2/F3, allocation BCK/SES/SUR) + `kadre/WAYLAND-PHASE-0-DECISIONS.md` (probe R1-R8, D2/D3) + `kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md` §Baseline (B-1..B-6) + `kadre/WAYLAND-IMPLEMENTATION-ROADMAP.md` §5 Phase 1.

## Global Constraints

- Aucune fenêtre, aucun input, aucune surface rendue : `requestWindow` = `KadreFailure.Unsupported(KadreOperation.RequestWindow)` ; le livrable reste headless (roadmap Phase 1).
- `attach()` (embedded) = `KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))` en tranche 1 (embedded = phase 8) ; `supportedIntegrations = emptySet()`.
- Aucune charge native pendant la discovery (probe R1) : `isAvailable()` ne touche aucun binding ; les handles KFFI restent lazy (audit domaine 5).
- `WAYLAND_SOCKET` : le probe ne consomme jamais le fd (R3) ; un run avec fd transmis échoue avant session tant que `KFFI-WAYLAND-001` est ouvert, sans repli par nom (F1-4, R7).
- Négociation : version bindée = `min(annoncée, max kffi)` ; annoncée < minimal requis ⇒ failure avant session, jamais un fonctionnement dégradé (B-4). Globals requis tranche 1 : `wl_compositor` ≥ 4, `xdg_wm_base` ≥ 1.
- Teardown F3 ordonné : quiescence des listeners ⇒ destruction proxies ⇒ flush final ⇒ fermeture fds, chaque owner idempotent ; callback tardive après retour de `run()` interdite ; wake réarmable après drain.
- Lifecycle headless : `Attached + Background + Inactive` initial (D3), pas d'arrêt faute de fenêtre ; arrêt = `SessionStopReason.HostRequested` via `sessionStopHandler` ; panne après admission ⇒ `controller.fail(PlatformFailure(Wayland, …))` puis `SessionOutcome.Failed`, jamais de reconnexion ni de repli X11 (F2).
- Échecs avant session dans le vocabulaire normalisé par `DesktopHostFacade` : `Unsupported(HostAttach)`, `InvalidRequest("options")`, `AlreadyInUse(Host)`, `PlatformFailure(platform == Wayland)`.
- Un seul host par processus (lease `AlreadyInUse(Host)`), miroir du broker AppKit.
- Sources de vérité pour les symboles kffi : `kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md` + jars sources pignés (handles `wlDisplay*`, `wlRegistry*`, `wlProxy*`, `PosixSymbols`, `PosixWakeup`, `PollFd`). Le pont KFFI est le seul endroit qui les touche.
- Tous les nouveaux modules/docs en français, conventions `explicitApi()` ; préfixer les commandes shell de `./gradlew --console=plain` ; commit après chaque tâche verte.

## Review Focus

1. Stop pendant `poll` depuis un autre thread — le wake doit réveiller le poll, l'arrêt doit être honoré sans callback tardive (SN-5). Test : `WaylandEventPumpTest.stopDuringPollExitsAndDrains`.
2. Wake perdu ou non réarmé après drain — plusieurs wakes sans événement compositor doivent chacun réarmer la pompe (P-2). Test : `WaylandEventPumpTest.repeatedWakeRearmsAfterDrain`.
3. `WAYLAND_SOCKET` non numérique ou valide au run — parse pur, aucun syscall, échec avant session sans consommation (R3/F1-4). Tests : `WaylandProbeTest.*`, `WaylandBackendProviderTest.runWithWaylandSocketFailsBeforeSession`.
4. Global requis absent ou version trop ancienne — admission refusée avant session avec interface et versions en diagnostic (F1-3/F1-5). Tests : `WaylandNegotiationTest.*`, `WaylandHostSessionTest.missingGlobalFailsBeforeSession`.
5. Fuite de fd ou de proxy au teardown — comptage `/proc/self/fd` avant/après sur Linux, owners idempotents (P-1/F3). Test : `WaylandNativeSessionLinuxTest.threeCyclesLeaveNoFdBehind` (Linux-only).

---

### Task 1: Module `:kadre:backend:wayland`, SPI et câblage

**Files:**
- Modify: `settings.gradle.kts` (include `:kadre:backend:wayland`, après `:kadre:backend:appkit`)
- Create: `kadre/backend/wayland/build.gradle.kts`
- Create: `kadre/backend/wayland/src/jvmMain/resources/META-INF/services/org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider`
- Create: `kadre/backend/wayland/src/jvmMain/kotlin/org/graphiks/kadre/internal/wayland/WaylandBackendProvider.kt` (squelette minimal : `backend = DesktopBackendKind.Wayland`, `supportedIntegrations = emptySet()`, `isAvailable()` délégué à `WaylandProbe` du Task 2 — en attendant, lambda injectable `availability`, `attach()`/`run()` retournent `KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach))`)
- Create: `kadre/backend/wayland/src/jvmTest/kotlin/org/graphiks/kadre/internal/wayland/WaylandBackendProviderTest.kt` (premiers cas)
- Modify: `kadre/platform/desktop/build.gradle.kts` — `runtimeOnly(project(":kadre:backend:wayland"))` à côté d'appkit
- Modify: `kadre/build.gradle.kts` — `check` dépend de `:kadre:backend:wayland:check` ; `publishContractArtifacts` publie le module

**Interfaces:**
- Produces: module compilable, provider découvrable ; `WaylandBackendProvider.forTesting(availability: () -> Boolean)` (seam de test, sera étendu Task 5) ; tâche Gradle `waylandNativeTests` enregistrée mais exclue hors Linux (`enabled = isLinux`, miroir inversé du bloc macOS d'appkit, avec `jvmArgs("--enable-native-access=ALL-UNNAMED")`, résultats JUnit dans `build/test-results/waylandNativeTests`).

- [ ] **Step 1: Test RED — discovery**

```kotlin
class WaylandBackendProviderTest {
    @Test
    fun discoveryFindsExactlyOneWaylandProvider() {
        val providers = ServiceLoader.load(DesktopBackendProvider::class.java).toList()
        val provider = providers.single { it.backend == DesktopBackendKind.Wayland }
        assertEquals(emptySet(), provider.supportedIntegrations)
        assertEquals(isLinux(), provider.isAvailable())
    }

    @Test
    fun embeddedAttachIsUnsupportedInTrancheOne() {
        val provider = WaylandBackendProvider.forTesting { true }
        val result = provider.attach(request = embeddedRequest()) // parentScope + factory + policy
        assertEquals(KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)), result)
    }
}
```

- [ ] **Step 2: Vérifier RED** — `./gradlew :kadre:backend:wayland:jvmTest --console=plain` échoue (module/classes inexistantes).
- [ ] **Step 3: Scaffold** module + provider squelette + SPI + câblage (build calqué sur appkit : `kotlin("multiplatform")`, `maven-publish`, `jvmToolchain(25)`, `jvm()`, `explicitApi()`, `api(project(":kadre:runtime"))`, `implementation(libs.kffi.wayland)`, `implementation(libs.kffi.posix)`, tests `kotlin("test")` + `project(":kadre:platform:desktop")`, publication repo `contractTest`) ; bloc Linux-only pour `waylandNativeTests` (vide à ce stade, la tâche existe).
- [ ] **Step 4: GREEN** — même commande : PASS (2 tests). Puis `./gradlew :kadre:platform:desktop:check --console=plain` PASS.
- [ ] **Step 5: Commit** — `feat(wayland): add the backend module and lazy provider skeleton (phase 1)`.

### Task 2: Probe `isAvailable()` — règles R1-R8 (pur, injecté)

**Files:**
- Create: `…/wayland/WaylandProbe.kt` (+ `WaylandAvailabilityReason.kt` : enum `Platform, WaylandSocketInvalid, RuntimeDirAbsent, SocketAbsent`)
- Create: `…/wayland/src/jvmTest/…/WaylandProbeTest.kt`
- Modify: `WaylandBackendProvider.kt` — `isAvailable() = WaylandProbe.check(WaylandProbe.Environment.fromSystem()).available` (aucune charge native)

**Interfaces:**
- Produces: `WaylandProbe.check(env: Environment): ProbeResult` avec `Environment(isLinux: Boolean, waylandSocket: String?, xdgRuntimeDir: String?, waylandDisplay: String?, socketExists: (String) -> Boolean)` et `ProbeResult(available: Boolean, reason: WaylandAvailabilityReason?)` — signatures exactes réutilisées par les tests et le provider.

- [ ] **Step 1: Tests RED** — couvrir : non-Linux ⇒ false `Platform` ; `WAYLAND_SOCKET="7"` ⇒ true (aucun syscall — le fs-injecté n'est jamais appelé) ; `WAYLAND_SOCKET="pas-un-fd"` et `"99999999999999999999"` ⇒ false `WaylandSocketInvalid` ; `WAYLAND_SOCKET=""` ⇒ traité absent ; `WAYLAND_DISPLAY="/run/user/1000/custom"` sans `XDG_RUNTIME_DIR` ⇒ true (chemin absolu, R4) ; `XDG_RUNTIME_DIR` + `WAYLAND_DISPLAY="wayland-1"` + socketExists ⇒ true ; `WAYLAND_DISPLAY` absent ⇒ `wayland-0` ; `XDG_RUNTIME_DIR` absent sans absolu ⇒ false `RuntimeDirAbsent` ; socket inexistant ⇒ false `SocketAbsent`.
- [ ] **Step 2: Vérifier RED**, **Step 3: implémenter** R1-R6/R8 (pur, jamais de throw), **Step 4: GREEN** (11+ cas) et `WaylandBackendProviderTest.discoveryFindsExactlyOneWaylandProvider` toujours vert.
- [ ] **Step 5: Commit** — `feat(wayland): implement the isAvailable probe contract (phase 1)`.

### Task 3: Pont KFFI, registry durable, négociation et owners de listeners

**Files:**
- Create: `…/wayland/WaylandNativeBridge.kt` — interface pont (seule couche touchant kffi) :

```kotlin
internal interface WaylandNativeBridge : AutoCloseable {
    val displayFd: Int
    fun registryGlobals(): List<WaylandGlobal>        // name, interface name, advertised version
    fun bind(global: WaylandGlobal, version: Int): MemorySegment
    fun addListener(proxy: MemorySegment, owner: WaylandListenerOwner)
    fun roundtrip(): Boolean                          // false = erreur display
    fun dispatchPending(): Boolean
    fun prepareRead(): Int                            // >=0 ok, -1 again (EAGAIN sémantique libwayland)
    fun readEvents(): Boolean
    fun cancelRead()
    fun flush(): WaylandFlushOutcome                  // Done / Eagain / Errno(Int)
    fun displayError(): String?
    override fun close()                              // wl_display_disconnect, idempotent
}
internal fun kffiWaylandBridge(name: String): WaylandNativeBridge   // implémentation KffiWaylandBridge
```

- Create: `…/wayland/WaylandNegotiation.kt` — fonction pure `resolveBinding(advertised: Int, kffiMax: Int): Int = minOf(advertised, kffiMax)` + `admissionFailure(interfaceName, advertised, required): KadreFailure` (`PlatformFailure(Wayland, "negotiation", "$interfaceName advertised=$advertised required>=$required")`).
- Create: `…/wayland/WaylandListenerOwner.kt` — owner révocable : `admit()`, `revoke()` idempotent, copie des payloads dans la callback, exceptions reportées via `RuntimeFailureReporter`-style hook injecté, jamais déroulées dans l'upcall.
- Create: `…/wayland/KffiWaylandBridge.kt` — implémentation sur les handles `wlDisplay*`, `wlRegistry*`, `wlProxy*` (audit domaine 1-2) ; versions max depuis `WaylandInterfaces.kt` (`WL_COMPOSITOR_VERSION=4`, `XDG_WM_BASE_VERSION=2`).
- Tests: `WaylandNegotiationTest.kt` (pur), `WaylandListenerOwnerTest.kt` (pur), `KffiWaylandBridgeLinuxTest.kt` (Linux-only : connect/roundtrip/registry sur Weston — Task 9 les exécute).

**Interfaces:**
- Produces: `WaylandNativeBridge` (fake en O2), `WaylandGlobal(name: String, version: Int)`, owner révocable réutilisé par le bridge ; minimaux requis = constantes `REQUIRED_GLOBALS` (`wl_compositor` ≥ 4, `xdg_wm_base` ≥ 1, B-4).

- [ ] **Step 1: Tests RED (pur)** — négociation : advertised=6,kffiMax=4 ⇒ bind 4 ; advertised=1 compositor ⇒ failure ; advertised=3 wm_base ≥1 ⇒ bind 3. Owner : revoke puis callback ⇒ non admis (quiescence) ; exception dans callback ⇒ reportée, jamais propagée ; double revoke ⇒ idempotent.
- [ ] **Step 2: GREEN** sur les trois classes pures. **Step 3: implémenter `KffiWaylandBridge`** (glue kffi mécanique, lazy,ownership : le bridge est l'unique owner du `MemorySegment` display ; `close()` annule toute lecture préparée puis disconnect). **Step 4: commit** — `feat(wayland): add the native bridge, negotiation and listener owners (phase 1)`.

### Task 4: Pompe — machine à états injectable (O2)

**Files:**
- Create: `…/wayland/WaylandEventPump.kt` :

```kotlin
internal interface PumpOperations {
    fun prepareRead(): Int
    fun readEvents(): Boolean
    fun cancelRead()
    fun dispatchPending(): Boolean
    fun flush(): WaylandFlushOutcome
    fun poll(displayFd: Int, wakeFd: Int, timeoutMs: Int): Int   // >0 fds prêts, 0 timeout, <0 errno EINTR
}
internal interface WaylandWakeup : AutoCloseable {             // impl KffiPosixWakeup sur kffi-posix
    val wakeFd: Int
    fun signal(): Boolean
}
internal class WaylandEventPump(private val ops: PumpOperations, private val wake: WaylandWakeup) {
    fun runOnce(): PumpStep        // Idle→Prepared→Poll→Read→Dispatch, réarmé après drain
    fun requestStop()              // thread-safe, wake()
    fun stopRequested(): Boolean
}
```

- Create: `…/wayland/src/jvmTest/…/WaylandEventPumpTest.kt` (fakes déterministes).

**Interfaces:**
- Produces: pompe conforme §3.3 (ordre : dispatchPending → prepareRead → flush → poll(display,wake) → readEvents **ou** cancelRead ; jamais de roundtrip pendant une lecture préparée — `runOnce` refuse et annule proprement) ; stop réveillé par wake ; `EINTR` relance le poll ; flush `Eagain` ⇒ surveille l'écriture (poll POLLOUT) puis retente.

- [ ] **Step 1: Tests RED** — P-2 : 3 wakes sans événement ⇒ 3 `runOnce` réarmés ; SN-5 : `requestStop()` depuis un autre thread pendant poll bloquant (fake qui attend un latch) ⇒ sortie prompte, cancelRead appelé si lecture préparée ; SN-6 : flush EAGAIN ⇒ poll inclut POLLOUT, aucun payload perdu ; EINTR : poll renvoie -1/EINTR 2 fois ⇒ nouvelle tentative, pas d'abandon ; roundtrip pendant préparation ⇒ refusé + lecture annulée.
- [ ] **Step 2: GREEN**, **Step 3: commit** — `feat(wayland): implement the libwayland-conformant event pump (phase 1)`.

### Task 5: `WaylandHostSession`, provider `run()` et chemins d'échec (O2)

**Files:**
- Create: `…/wayland/WaylandHostSession.kt` — séquence `start()` : wake ouvert (F1-6) → bridge connect par nom (F1-1/F1-2 ; `WAYLAND_SOCKET` ⇒ échec F1-4, gap KFFI-WAYLAND-001) → registry + négociation (F1-3/F1-5) → `RuntimeHostController.withComponents(platform = KadrePlatform.Wayland, componentsFactory = { RuntimeSessionComponents(windows = WaylandHeadlessWindowManager()) })`, `initialLifecycleState = LifecycleState(Attached, Background, Inactive)`, `sessionStopHandler` → `pump.requestStop()` + `host.detach()` ⇒ `SessionStopReason.HostRequested` ; boucle `runOnce` jusqu'à stop ; panne display ⇒ `controller.fail(PlatformFailure(Wayland, "display-error", …))` (F2) ; `teardown()` = F3 (quiescence → proxies → flush → close fds, owners idempotents, compteur de fds pour les tests Linux).
- Create: `…/wayland/WaylandHeadlessWindowManager.kt` — `WindowManager` minimal : `state = WindowManagerState(primary = null, windows = emptyList(), capabilities = WindowManagerCapabilities(requestWindow = Unsupported(RequestWindow)), revision)` ; `requestWindow` ⇒ `KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindow))`.
- Modify: `WaylandBackendProvider.kt` — `run()` : garde disponibilité ⇒ `Unsupported(HostAttach)` ; lease processus `AtomicBoolean` ⇒ `AlreadyInUse(Host)` ; scope `SupervisorJob() + Dispatchers.Default` ; `host.controller.attach(parentScope, request.applicationFactory, request.policy)` (factory JAMAIS invoquée avant admission) ; boucle pompe sur le thread appelant ; retour `KadreResult.Success(runBlocking { session.awaitTermination() })` ; `finally` : cancel scope + libération lease. `forTesting(availability, bridgeFactory: (String) -> WaylandNativeBridge, env: WaylandProbe.Environment)` étendu.
- Tests: `WaylandHostSessionTest.kt`, extension de `WaylandBackendProviderTest.kt`.

**Interfaces:**
- Produces: `forTesting(availability: () -> Boolean = ::isLinux, bridgeFactory: (String) -> WaylandNativeBridge = ::kffiWaylandBridge, env: WaylandProbe.Environment = Environment.fromSystem())` — seams exacts des tests.

- [ ] **Step 1: Tests RED** — serveur absent (bridgeFactory lève/renvoie échec) ⇒ `PlatformFailure(Wayland, "connect", …)`, `factoryInvoked == false`, zéro ressource ; `env.waylandSocket = "7"` ⇒ échec F1-4 avant session, factory non invoquée ; global manquant (fake registry sans `xdg_wm_base`) ⇒ F1-3 ; version trop ancienne ⇒ F1-5 ; deux `run()` simultanés (latch) ⇒ second = `AlreadyInUse(Host)` ; stop demandé depuis un thread annexe pendant la boucle ⇒ retour `Stopped(HostRequested)` et `factory`/`attach` déjà passés ; **annulation** (SN-9) : cancel du scope parent pendant la boucle ⇒ `Stopped(ParentCancelled)`, teardown F3 complet ; déconnexion display après admission (fake qui bascule une erreur au 2ᵉ dispatch) ⇒ outcome `Failed` (F2), pas de reconnexion.
- [ ] **Step 2: GREEN** — `./gradlew :kadre:backend:wayland:jvmTest --console=plain` tous verts (inclut Tasks 2-4).
- [ ] **Step 3: Test consommateur** — `./gradlew :kadre:samples:desk-tour:check --console=plain` PASS (runtimeOnly ne casse rien sur macOS).
- [ ] **Step 4: Commit** — `feat(wayland): run real headless sessions with ordered teardown (phase 1)`.

### Task 6: Driver compositor, contrats registre et producteur de preuves

**Files:**
- Create: `kadre/contracts/driver/wayland/build.gradle.kts` (jvm module minimal, publication contractTest) + `README.md` (rôle : stimuli externes, fixtures compositor, observations natives — topologie §4)
- Create: `kadre/contracts/driver/wayland/src/jvmMain/kotlin/org/graphiks/kadre/contracts/driver/wayland/WaylandCompositorController.kt` — démarre Weston headless selon B-5 (`weston --backend=headless-backend.so --renderer=pixman --width=800 --height=600 --socket=$name --idle-time=0`, `XDG_RUNTIME_DIR` dédié chmod 0700, attente socket ≤ 60 s, log fichier, arrêt + attente), consommé par les tests Linux et le script CI.
- Modify: `settings.gradle.kts` (include driver)
- Modify: `kadre/contracts/registry/contracts.tsv` — 3 lignes `active`, miroir du format `AND-001` (source = `WAYLAND-PHASE-1-HOST-DESIGN.md#…`) :
  - `BCK-015` — probe/discovery Wayland, oracle O2, scenarios `wayland-provider-discovery,wayland-probe-socket-parse,wayland-probe-runtime-dir-absent,wayland-probe-socket-absent`, targets `jvm`, capabilities `-`, sentinels `wayland-no-native-load-at-discovery`.
  - `SES-001` — lifecycle headless start/stop + panne après admission, oracle `O2` (allocation du design ; les scénarios `wayland-session-start-stop-cycles,wayland-stop-from-other-thread,wayland-cancellation,wayland-server-absent-fails-before-session,wayland-wayland-socket-unsupported-fails,wayland-disconnect-after-admission` sont prouvés O2 et consolidés par les O3 de Task 7), targets `jvm`, sentinels `wayland-stop-no-resurrection,wayland-failure-no-reconnect`.
  - `SUR-001` — pompe, O3, scénarios `wayland-pump-wake-rearm,wayland-pump-stop-during-poll,wayland-pump-flush-eagain,wayland-pump-eintr`, targets `jvm`, sentinels `wayland-pump-no-prepared-roundtrip,wayland-pump-no-late-callback`.
- Create: `kadre/backend/wayland/contracts/evidence.tsv` — entête `contractId target kind evidenceId testClass testName` + les mappings des tests O2 (BCK-015/SES-001) et O3 (SUR-001, tests `WaylandNativeSessionLinuxTest`, classe du Task 9).
- Modify: `kadre/contracts/validator/build.gradle.kts` — bloc wayland calqué appkit : `waylandContractIds = listOf("BCK-015","SES-001","SUR-001")`, generate JavaExec par id (dependsOn `:kadre:backend:wayland:waylandNativeTests`, junit dirs = `waylandNativeTests` + `jvmTest`, adapter `"wayland-jvm"`), `validateWaylandContractEvidence`, agrégat `generateWaylandContractEvidence`. Root `check` NE dépend PAS du gate wayland (Linux-only, piloté par le script CI — miroir appkit).

**Interfaces:**
- Produces: ids actifs BCK-015/SES-001/SUR-001 ; tâches `:kadre:contracts:validator:generateWayland{Bck015,Ses001,Sur001}ContractEvidence`, `validateWaylandContractEvidence`, `generateWaylandContractEvidence` ; `WaylandCompositorController` consommé par Task 9 et le script CI.

- [ ] **Step 1: RED** — `./gradlew :kadre:contracts:validator:validateContractRegistry --console=plain` échoue tant que les ids du mapping/validateur n'existent pas (producteur manquant).
- [ ] **Step 2: Ajouter** lignes TSV + tâches validateur + driver ; **GREEN** : validateContractRegistry PASS.
- [ ] **Step 3: Commit** — `feat(wayland): register the phase 1 contracts, driver and evidence producer (phase 1)`.

### Task 7: Tests natifs O3 Linux (`WaylandNativeSessionLinuxTest`)

**Files:**
- Create: `kadre/backend/wayland/src/jvmTest/kotlin/org/graphiks/kadre/internal/wayland/WaylandNativeSessionLinuxTest.kt` — kotlin.test, `Assume.assumeTrue(isLinux() && compositor disponible)` ; consomme `WaylandCompositorController` :
  - `threeCyclesLeaveNoFdBehind` (P-1) : 3× start/stop, `/proc/self/fd` compté avant/après ± 0, zéro callback tardive (flag après retour de `run()`) ;
  - `repeatedWakeKeepsThePumpResponsive` (P-2/O3) : wakes répétés sans fenêtre, session vivante puis `Stopped` ;
  - `absentServerFailsBeforeSession` (SN-1) : socket inexistant ⇒ PlatformFailure, zéro fd fui ;
  - `stopFromAnotherThreadDuringPoll` (SN-5/O3) : arrêt pendant poll, `Stopped(HostRequested)` ;
  - `missingGlobalFails` (SN-4) : env pointer vers socket d'un second Weston avec `XD...` — sinon : Weston standard possède les globals ; SN-4 s'exerce en O2 (Task 5) et la variante O3 reste documentée « exécutée quand un compositor amputé est disponible » — l'inscrire dans le README du driver, pas comme test silencieusement ignoré ;
  - `realWaylandSocketFdFailsUntilKffi001` (SN-3 variante provisoire F1-4) : run avec `WAYLAND_SOCKET=<fd valide>` ⇒ échec avant session « wayland-socket non supporté », fd non consommé (toujours ouvert) ;
  - `negotiationRejectsOldCompositor` (SN-8) : si Weston annonce ≥ minimaux ⇒ P-1 vert suffit ; SN-8 reste couvert en O2 — même traitement que SN-4 (documenté).
- Modify: `kadre/backend/wayland/build.gradle.kts` — `waylandNativeTests` (Test task dédié, `forkEvery = 1`, XML vers `build/test-results/waylandNativeTests`, include ce filtre, enabled = Linux).

**Interfaces:**
- Consomme: Tasks 3-5 (bridge réel kffi, session, pompe) + driver Task 6.
- Produces: résultats JUnit O3 consommés par le producteur de preuves (Task 6) sur Linux.

- [ ] **Step 1: Écrire les tests** (échec attendu hors Linux : exclusions `enabled` par garde OS — jamais `skip` silencieux ; hors Linux la tâche est désactivée, sur Linux un compositor absent fait ÉCHOUER le test, pas le sauter — watchdog via script Task 8).
- [ ] **Step 2: Commit** — `test(wayland): add the linux o3 session scenarios against weston (phase 1)` (l'exécution réelle est la Task 9).

### Task 8: CI — workflow, scripts, watchdog

**Files:**
- Create: `scripts/ci-wayland-runtime.sh` (B-5) : `start <socket>` lance Weston headless (env `XDG_RUNTIME_DIR=/tmp/kadre-wayland-runtime` 0700, log `build/wayland/weston.log`), attend le socket (60×250 ms), exporte `WAYLAND_DISPLAY`/`XDG_RUNTIME_DIR` (fichier `env.sh` sourçable) ; `stop` tue proprement ; trap de nettoyage.
- Create: `scripts/test-kadre-wayland-contracts.sh` (miroir `test-kadre-appkit-contracts.sh`) : garde Linux ; watchdog `scripts/lib/process-watchdog.sh` ; phase `tests` = `./gradlew :kadre:backend:wayland:waylandNativeTests --refresh-dependencies --rerun-tasks --no-daemon` (timeout 900 s) ; phase `evidence` = `./gradlew :kadre:contracts:validator:generateWaylandContractEvidence -PkadreContractCommit=$COMMIT` ; assertion : les 3 JSON `build/contract-evidence/*.json` non vides ; journalise `weston --version` dans la preuve (B-3).
- Create: `.github/workflows/kadle-wayland-contracts.yml` — `ubuntu-latest`, timeout 25 min (contrainte roadmap ≤ 25), setup-java temurin 25, `sudo apt-get install -y weston`, étapes : checkout, gate script, re-fail `if: always()`, upload artifacts (`build/contract-evidence/`, `build/test-results/`, `build/wayland/`).
- Modify: `kadre/capabilities/wayland.md` — registre 7 colonnes (format android.md §1) tranche 1 : discovery/provider (BCK-015), session standalone headless (SES-001), pompe (SUR-001), `requestWindow` = `Unsupported(RequestWindow)` état absent documenté, `memoryPressure` = `Unsupported` (aucune source protocolaire, D3), lifecycle axes = valeurs fixes Background+Inactive (D3).

- [ ] **Step 1: Écrire** scripts + workflow + capabilities. **Step 2: Vérifier** `bash -n scripts/ci-wayland-runtime.sh scripts/test-kadre-wayland-contracts.sh` ; workflow YAML lu/revu (pas d'exécutable local). **Step 3: Commit** — `ci(wayland): add the weston-backed contract gate and evidence workflow (phase 1)`.

### Task 9: Preuve O3 locale (Docker glibc) et clôture M1

**Files:**
- Modify: `kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md` §Baseline B-3 — inscrire la version weston réellement utilisée + digest de l'image.
- Artefacts (git-ignorés) : `build/contract-evidence/*.json`, `build/test-results/waylandNativeTests/*.xml`.

- [ ] **Step 1: Lancer la preuve locale** — `docker pull eclipse-temurin:25-jdk-noble` (pigner le digest), conteneur avec `apt-get install -y weston`, montage du worktree + `~/.gradle`, exécuter `scripts/test-kadre-wayland-contracts.sh` (tests O3 contre Weston puis génération des 3 preuves). Attendu : tests verts, 3 JSON non vides. Les échecs environnementaux (réseau/apt) se règlent, on ne les contourne pas par `continue-on-error`.
- [ ] **Step 2: Vérifier les preuves** — `./gradlew :kadre:contracts:validator:validateWaylandContractEvidence` (dans le conteneur) PASS ; suites complètes `:kadre:backend:wayland:jvmTest` PASS.
- [ ] **Step 3: Mettre à jour** la baseline (weston version + digest) et cocher les cases Phase 1 de la roadmap. **Step 4: Commit** — `docs(wayland): record the weston proof and close phase 1 (M1)`.

## Hors périmètre (rappel)

- Fenêtres/surface/interop renderer (phase 2), input (phase 4, bloqué par `KFFI-WAYLAND-002`), embedded (phase 8), capture (phase 10, `KFFI-WAYLAND-003`).
- Support `WAYLAND_SOCKET` au run (bloqué par `KFFI-WAYLAND-001` ; échec F1-4 explicite en attendant).
- Publication Maven au-delà du repo `contractTest`, échantillon démo Wayland.
