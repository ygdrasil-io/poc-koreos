# Wayland Phase 2 — Fenêtres, surface minimale et interop renderer — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Des fenêtres Wayland réelles : handshake xdg complet, propriétés initiales conformes uniquement, fermeture non-interceptable à ordre terminal respecté, deux fenêtres indépendantes mappées sur Weston avec `withDesktopHandle` borné — via un incrément kffi (fenêtrage managé + buffers shm) pour que Kadre ne porte aucun FFM.

**Architecture:** Incrément kffi d'abord (PR #2 : `WaylandSurface`/`WaylandXdgSurface`/`WaylandToplevel`/buffers shm managés, listeners révocables, marshal générique exclusivement), publié puis consommé. Côté kadre : `WaylandWindowRuntimeDriver` compose `RuntimeWindowManager` (autorité unique d'état : primary, révisions, arming `stopWhenLastWindowClosed`) derrière `WaylandWindowCommandPort`/`WaylandWindowPeer` ; le buffer contrôlé vient d'un `WaylandSurfaceBufferSource` injecté. Tests O2 (fakes, macOS) + O3 (Weston, Linux) ; contrats `WIN-010/011/012` + `SUR-002` (numéros à revérifier), gate Docker en clôture.

**Tech Stack:** Kotlin/JVM 25, FFM via kffi uniquement, Weston headless 13.0.0 (Docker glibc / ubuntu CI), kotlin.test, Gradle 9.8.

**Spec:** `kadre/WAYLAND-PHASE-2-WINDOWS-DESIGN.md` (tables F1/F2, scénarios P/SN, allocation) + `kadre/WAYLAND-IMPLEMENTATION-ROADMAP.md` §5 Phase 2 + D1-D3 de `WAYLAND-PHASE-0-DECISIONS.md` + baseline B-1..B-6 de `WAYLAND-PHASE-0-KFFI-AUDIT.md`.

## Global Constraints

- **Aucun FFM dans Kadre** : tout marshal/listener/struct vit dans kffi ; le pont kadre est un adaptateur typé (règle partenaire, gap KFFI-WAYLAND-005/006).
- Handshake xdg obligatoire : commit initial sans buffer → configure → `ackConfigure(serial)` → attach → commit. Jamais d'`ackConfigure` sur un serial déjà acquitté (F1-5). Tailles nulles du configure ≠ surface nulle (F1-4 : taille du spec conservée).
- Propriétés initiales conformes uniquement : `decorations = System` ⇒ `Unsupported(Decorations)` AVANT toute primitive ; `outerPosition` ⇒ refus ; jamais de repli silencieux du `WindowSpec` (D1).
- Fermeture : `xdg_toplevel.close` non-interceptable ⇒ aucun `CloseRequested(canReject)` ; `Closing` à raison forcée ; ordre terminal DESIGN L918 (`Closing` → détachement surface → `Closed` → retrait membership → fin `events`) ; course close compositor/close programmatique ⇒ une seule terminaison, `NativeCloseAlreadyCommitted` observable.
- Runtime autorité unique : `RuntimeWindowManager` possède `WindowState`/révisions/membership/primary ; le backend n'a pas de second snapshot et rapporte l'effectif uniquement via les stimuli.
- Lease `withDesktopHandle` : adresses valides uniquement dans l'appel, thread pompe ; admission refusée après `closeAdmitted` ; leases attendus avant destruction (D2).
- Sécurité native : jamais d'exception Kotlin à travers un upcall ; quiescence avant destruction ; destroy ordonné (toplevel → xdg_surface → surface → buffer).
- `waylandContractTests` (agrégat O2+O3) alimente la gate ; un compositor requis absent fait échouer le gate, jamais un skip.

## Review Focus

1. Handshake xdg : commit initial sans buffer, ack exactement par serial reçu, taille nulle conservée au spec (P-2/SN-4).
2. Course close compositor × close programmatique : une seule terminaison, `NativeCloseAlreadyCommitted` observable, zéro callback tardive (SN-6/SN-5).
3. Succession `primary` déterministe à la destruction de la première fenêtre, avec requêtes pendantes pendant la transition (P-1/P-3).
4. Refus normatifs avant toute primitive : `System`, `outerPosition`, champs non supportés (SN-1..SN-3).
5. Lease `withDesktopHandle` : refus après `closeAdmitted`, adresses invalidées avant destruction (SN-8).

---

### Task 1: kffi — couche fenêtrage managée (`WaylandSurface`/`XdgSurface`/`Toplevel`)

**Files (kffi, `/Users/chaos/workspace/graphiks-kffi`, branche `feat/wayland-managed-windows`):**
- Create: `kffi-wayland/src/jvmMain/kotlin/org/graphiks/kffi/wayland/managed/WaylandWindowing.kt`
- Modify: `kffi-wayland/src/jvmTest/kotlin/org/graphiks/kffi/wayland/WaylandClientIntegrationTest.kt` (cas fenêtres)
- Modify: `CHANGELOG.md`

**Interfaces (publiques, kffi):**
```kotlin
public class WaylandSurface internal constructor(...) : AutoCloseable {
    public fun commit()
    public fun attachShmBuffer(buffer: WaylandShmBuffer, x: Int = 0, y: Int = 0)   // + damage implicite pleine surface
    public fun addFrameListener(onFrame: (callbackSerial: Int) -> Unit, onFailure: (Throwable) -> Unit = {}): WaylandFrameCallback
    public fun addEnterLeaveListener(onEnter: (outputName: String) -> Unit, onLeave: (outputName: String) -> Unit, onFailure: (Throwable) -> Unit = {}): WaylandSurfaceListener
    override fun close()
}
public class WaylandXdgSurface internal constructor(...) {
    public fun addConfigureListener(onConfigure: (serial: Int) -> Unit, onFailure: (Throwable) -> Unit = {}): WaylandSurfaceListener
    public fun ackConfigure(serial: Int)
}
public class WaylandToplevel internal constructor(...) : AutoCloseable {
    public fun addEventsListener(onConfigure: (width: Int, height: Int, states: List<Int>) -> Unit, onClose: () -> Unit, onFailure: (Throwable) -> Unit = {}): WaylandSurfaceListener
    public fun setTitle(title: String)
    public fun setAppId(appId: String)
    override fun close()
}
// création, sur WaylandClient:
public fun createSurface(): WaylandSurface            // wl_compositor.create_surface via marshal
public fun createXdgSurface(surface: WaylandSurface): WaylandXdgSurface   // xdg_wm_base.get_xdg_surface via marshal
public fun createToplevel(xdgSurface: WaylandXdgSurface): WaylandToplevel // xdg_surface.get_toplevel via marshal
// kffi pongue automatiquement xdg_wm_base.ping (listener interne, non exposé)
```
Sémantique : mêmes garanties que le listener registry (#115) — quiescence après revoke, copie des payloads (états = liste d'Int copiée, chaînes), exceptions isolées vers le reporter, destroy ordonné (toplevel → xdg_surface → surface), `close` idempotents.

- [ ] **Step 1: RED — tests d'intégration Weston** (dans `WaylandClientIntegrationTest` ou nouvelle classe) : create surface + xdg + toplevel, `setTitle`, commit initial sans buffer, configure reçu (serial > 0), `ackConfigure`, attach d'un buffer shm 64×64 rempli, commit → (mapping constaté par un second configure/enter — pas de capture d'écran) ; close ordonné sans crash ; callback frame tuée après revoke.
- [ ] **Step 2: implémentation** via `wlProxyMarshalFlags*` existants (opcodes : compositor.create_surface=0, wm_base.get_xdg_surface=1, xdg_surface.get_toplevel=1, toplevel.set_title=2, set_app_id=3, surface.commit=6, attach=1, damage=2, frame=3, xdg_surface.ack_configure=4) — vérifier chaque opcode dans les constantes générées kffi avant usage.
- [ ] **Step 3: GREEN local + integration Weston** (Docker `kffi-clean-gradle`, `KFFI_WAYLAND_INTEGRATION=1`).
- [ ] **Step 4: Commit** `feat(wayland): add the managed windowing layer (surface, xdg, toplevel)` + **PR kffi #2, publication du snapshot**, mise à jour `KFFI-WAYLAND-005` → `closed` + version pignée côté kadre.

### Task 2: kffi — buffers shm managés

**Files:** `…/managed/WaylandShm.kt`, tests, CHANGELOG.

**Interfaces:**
```kotlin
public class WaylandShmBuffer internal constructor(...) : AutoCloseable {
    public val width: Int
    public val height: Int
    public fun pixels(): java.nio.ByteBuffer   // vue directe, possession consommateur du CONTENU
    public fun addReleaseListener(onRelease: () -> Unit): WaylandSurfaceListener
    override fun close()
}
// sur WaylandClient:
public fun createShmBuffer(width: Int, height: Int): WaylandShmBuffer  // memfd/ftruncate via kffi-posix + wl_shm.create_pool + wl_shm_pool.create_buffer via marshal
```
- [ ] RED (intégration Weston : create → fill → commit via Task 1 → release listener après un frame) → GREEN → commit `feat(wayland): add managed shm buffers` (même PR #2) → publication → `KFFI-WAYLAND-006` → `closed`.

### Task 3: kadre — variante handle runtime + rewiring provider

**Files:**
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/desktop/…` ou `WindowCommandPort.kt:379-384` — ajouter `RuntimeDesktopNativeWindowHandle.Wayland(displayAddress: ULong, surfaceAddress: ULong)` au sealed.
- Modify: `WaylandBackendProvider.kt` — la session branche `WaylandWindowRuntimeDriver` (délègue `driver.manager` dans `RuntimeSessionComponents.windows`, `closeAction = driver::close`, bridge `stopWhenLastWindowClosed` → `lastWindowStop::request`); headless uniquement tant qu'aucune fenêtre.
- Modify: `WaylandHostSession.kt` — la pompe héberge le dispatch des événements fenêtres (les stimuli fenêtres sont émis depuis le thread pompe).
- Tests: le provider headless existant reste vert (régression).

- [ ] RED: provider branchant le driver réel ⇒ `RuntimeWindowManager` présent dans les composants (state `primary = null` tant que zéro fenêtre) → GREEN → commit `feat(wayland): wire the window driver into the standalone session (phase 2)`.

### Task 4: kadre — driver, ports et peer de création

**Files:**
- Create: `…/wayland/WaylandWindowRuntimeDriver.kt`, `…/WaylandWindowCommandPort.kt`, `…/WaylandWindowPeer.kt`, `…/WaylandSurfaceBufferSource.kt`
- Modify: `WaylandWindowRuntimeDriver` construit `RuntimeWindowManager` (miroir AppKit L126-144).

**Interfaces:** `WaylandWindowPeer.prepare(spec, admission: () -> Boolean): PreparedPeer` — créé via la facade kffi, `setTitle`, handshake complet (F1-4/F1-5 gérés dans la machine à états configure de la peer), retourne `owner: WindowPeerOwner + RuntimeDesktopWindowHandleAccess` (lease : compteur `activeHandleLeases` + `admitCallback`), `initialSurfaceSnapshot` (metrics effectifs). `WaylandSurfaceBufferSource(buffer: WaylandShmBuffer)` remplit le contenu (motif déterministe par défaut ; injectable par le driver de contrats).
- [ ] RED (O2, fake kffi seam `WaylandWindowing` injectable): création admissible ⇒ `commit(owner, effectiveSpec, snapshot)` appelé exactement une fois, `primary` promu ; `decorations = System` ⇒ `fail(Unsupported(Decorations))` sans primitive (SN-1) ; `outerPosition` ⇒ SN-2 ; création native échouée ⇒ `fail` + destruction partielle (SN-7) → GREEN → commit `feat(wayland): add the window driver, ports and creation peer (phase 2)`.

### Task 5: kadre — configure, titre, propriétés, close, succession

**Files:** `…/WaylandWindowPeer.kt` (machine configure/serials, close), `WaylandWindowRuntimeDriver.kt` (observations → stimuli), tests O2.

- [ ] RED: configure (w,h) non nul ⇒ observation géométrie ; (0,0) ⇒ taille spec conservée (F1-4) ; serial ancien ignoré (SN-5 O2) ; `toplevel.close` ⇒ `Closing` raison forcée sans `CloseRequested` (L914) ; close programmatique ⇒ destroy ordonné, `NativeCloseAlreadyCommitted` en course (SN-6 O2) ; destruction fenêtre A ⇒ fenêtre B intacte, `primary` = B (P-1 O2) ; `stopWhenLastWindowClosed` armé après première admission, transition vide ⇒ callback, headless sans fenêtre jamais (P-3 O2) → GREEN → commit `feat(wayland): configure handling, close path and primary succession (phase 2)`.

### Task 6: kadre — lease `withDesktopHandle`

**Files:** `…/WaylandWindowPeer.kt` (lease), `RuntimeDesktopNativeWindowHandle.Wayland` retourné, tests.

- [ ] RED: block exécuté sur le thread pompe avec le handle Wayland ; admission refusée après `closeAdmitted` (SN-8 O2) ; leases attendus avant destroy → GREEN → commit `feat(wayland): bound desktop-handle leases on window peers (phase 2)`.

### Task 7: kadre — contrats, preuves et capabilities

**Files:** `contracts.tsv` (`WIN-010`, `WIN-011`, `WIN-012`, `SUR-002` — **numéros à revérifier**), `kadre/backend/wayland/contracts/evidence.tsv` (mappings O2 + O3), bloc validateur wayland étendu, `kadre/capabilities/wayland.md` (lignes phase 2), design/roadmap références.

- [ ] RED: validateContractRegistry (producteurs manquants) → lignes + mappings → GREEN → commit `feat(wayland): register the phase 2 window contracts and evidence (phase 2)`.

### Task 8: kadre — tests O3 Linux fenêtres

**Files:** `WaylandNativeSessionLinuxTest` (nouvelle classe `WaylandWindowsLinuxTest`).

- [ ] Scénarios P-1 (deux fenêtres indépendantes, destruction A sans perte de B, primary successif), P-2, SN-4/SN-5/SN-6 sur Weston réel → commit `test(wayland): prove the two-window gate on weston (phase 2)`.

### Task 9: gate Docker sur la pointe + clôture

- [ ] Gate machine propre (conteneur glibc, cache frais) verte ; preuves `WIN-010/011/012`+`SUR-002` inspectées (scénarios Passed, sentinelles Killed, commit pigné) ; baseline/capabilities à jour ; cases Phase 2 cochées ; commit `docs(wayland): record the phase 2 proof and close the milestone`.

## Hors périmètre

Redraw effectif (`requestRedraw` en `Unsupported` assumé — phase 3), scale/viewport, input, move/resize, décoration serveur, capture ; `fullscreen Exclusive` (phase 5) ; toute API `present` publique.
