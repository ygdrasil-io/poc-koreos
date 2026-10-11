# Android emulator contract driver

This non-published JVM project runs the public Android host-attachment proof of
phases 0, 1 and 2 on a real emulator. It is a contract driver, not an
implementation API: it forks the instrumented consumer build, executes its
scenarios on the attached device, and turns the JUnit output into the canonical
`AND-001`, `AND-002` and `AND-003` evidence with the validator's own generator.
English
prose, like the Web driver README it mirrors; everything else in this phase is
documented in French in the sources.

## What the driver proves

Three contracts, all active in the registry
([contracts.tsv](../../registry/contracts.tsv)), oracle `O3`, mapped in
[evidence.tsv](contracts/evidence.tsv):

**`AND-001`** — the phase 0 minimal Android `View` host session (source
`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 0`). The three scenario names are
unchanged since phase 0; the mechanism migrated to the phase 1 public
overloads when the throwing API was removed. All three live in the instrumented
test class `org.graphiks.kadre.consumer.KadreConsumerSessionTest`:

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-consumer-session-start` | scenario | `attachKadre(view, scope)` on an attached View returns a started session (`Starting`/`Running`) |
| `android-consumer-session-stop-cleanup` | scenario | `stop()` terminates cooperatively (`Terminated(Stopped(HostRequested))`), is idempotent |
| `android-consumer-double-attach-rejected` | scenario | a second `attachKadre` on the same View returns `Failure(AlreadyInUse(Host))` without exception; after `stop()`, re-attach succeeds |
| `android-stop-no-resurrection` | sentinel | post-terminal `release` revives nothing; teardown leaves zero residue |
| `android-double-attach-single-owner` | sentinel | one View, one owner: the process-wide registry admits a single claim |

**`AND-002`** — the phase 1 admission, lifecycle and isolation proof (source
`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 1`). Six scenarios and two sentinels,
all in `org.graphiks.kadre.consumer.KadreConsumerAttachContractTest`:

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-attach-decor-mismatch` | scenario | a View outside the decor is refused `InvalidRequest("surfaceView")` and reserves nothing — a nominal attach on the same Activity succeeds right after |
| `android-attach-cross-form-conflict` | scenario | Activity-form attach succeeds; a View-form attach on the SAME View returns `Failure(AlreadyInUse(Host))` with no ownership transfer, first session untouched |
| `android-attach-parent-cancelled` | scenario | cancelled scope → `Failure(ParentScopeCancelled)`; scope without a Job → `Failure(InvalidRequest("parentScope"))`; then a nominal attach succeeds on the same View |
| `android-attach-surviving-scope` | scenario | lifecycle publications reach the primary surface while an independent scope keeps the session alive: RESUMED → `Visible+Focused+Attached`, paused/stopped → `Hidden+Unfocused+Attached`; owner destroy → `Terminated(Stopped(HostDetached))`; the surviving scope is never cancelled |
| `android-attach-detach-reparent` | scenario | first `removeView` is terminal `Stopped(HostDetached)` and frees ownership; the same View reparented into another Activity's decor takes a NEW session with a different `SessionId` |
| `android-attach-recreation-isolation` | scenario | two Activities, two sessions with distinct ids; `recreate()` of the foreground Activity terminates only its session (`Stopped(HostDetached)`) — the background session keeps its id, lifecycle and jobs |
| `android-attach-rollback-restores-state` | sentinel | a late attach failure restores the reservation — guarded by `androidAttachParentCancelled` |
| `android-attach-isolation-per-identity` | sentinel | ownership keys on native identities, never a global lock — guarded by `androidAttachRecreationIsolation` |

**`AND-003`** — the phase 2 observable surface, metrics, redraw and native-access
proof (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 2 — Surface, metrics,
redraw et accès natif`). Six scenarios and three sentinels, all in the
instrumented test class `org.graphiks.kadre.consumer.KadreConsumerSurfaceContractTest`:

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-surface-metrics-match-view` | scenario | the published state (physical/logical size, scale, insets) matches the real View values (`view.width/height`, `resources.displayMetrics.density`, `rootWindowInsets`) |
| `android-surface-resize-propagates` | scenario | changing the View's layout params publishes the new size; the surface instance and its revision discipline survive |
| `android-surface-redraw-coalesced` | scenario | 5 `requestRedraw()` in one frame answer `Success(Unit)` each and produce EXACTLY one `RedrawRequested` |
| `android-surface-redraw-after-close` | scenario | after `session.close()`, `requestRedraw()` answers `Failure(Closed(Surface))` and no event follows |
| `android-surface-lease-boundaries` | scenario | `withAndroidView` hands the attached View; a second in-flight lease is `TemporarilyUnavailable(retryable = true)`; after close every lease is `Closed(Surface)` without invoking |
| `android-surface-no-window-handle` | scenario | the window's `platformAccess` is `Unsupported(PlatformWindowAccess)` and no public surface member exposes `android.view.Window` |
| `android-metrics-generation-stale` | sentinel | a stale-generation observation never publishes — host-pinned: the kill is demonstrated by the recorded mutation run against `AndroidSurfaceMetricsTest.staleGenerationCallbackRejected` (no emulator scenario can genuinely produce a stale callback); the mapping anchors on the metrics scenario |
| `android-redraw-single-per-frame` | sentinel | removing the coalescing (one event per request) kills `androidSurfaceRedrawCoalesced` alone |
| `android-lease-closed-after-terminal` | sentinel | a lease that skips its admission check hands out the View of a closed surface — kills `androidSurfaceLeaseBoundaries` alone |

The AND-003 sentinel blast radius is recorded the same way: the two instrumented
mutations (redraw without coalescing; a lease skipping its admission check) each
kill **exactly one** of the fifteen tests — its guard — and leave the other
fourteen green. The third, `android-metrics-generation-stale`, is host-pinned:
the binding labels every observation with the reducer's current generation and
its View is never replaced mid-session (detach is terminal), so no emulator
scenario can genuinely produce a stale callback — the kill lives in the host
suite, against `AndroidSurfaceMetricsTest.staleGenerationCallbackRejected`, with
the metrics scenario as the mapping anchor.

The two AND-002 sentinels carry demonstrated blast radius: under the recorded
mutations (late failure that skips the reservation rollback; ownership registry
keyed on a single global key) each kills **exactly one** of the nine phase-1
tests — its guard — and leaves the other eight green. A first mutation candidate
(reordering the reservation ahead of the admission ladder) killed 9/9 and was
rejected as proving nothing.

## Running the gate locally

Prerequisites: an Android SDK with the **AVD `Kadre_API_35`** (system image
arm64-v8a, API 35) and `ANDROID_HOME` set (or `sdk.dir` in the root
`local.properties`). The driver needs **no Android plugin** — it is a pure JVM
module that shells out to `adb` and forks `gradlew`.

Cold start — kill any running emulator first (`adb emu kill`), then let the
script boot the AVD itself (headless: `-no-window -no-audio -no-boot-anim -gpu
swiftshader_indirect`, log at `/tmp/kadre-emulator.log`) and wait for
`sys.boot_completed == 1` before running anything. Boot can take minutes:

```shell
scripts/test-kadre-android-contracts.sh
```

If a device is already attached, the script skips the boot and waits for that
device instead; pass a serial to pin it (`scripts/test-kadre-android-contracts.sh
emulator-5554`). The Gradle run inside is bounded by a portable 900 s watchdog
(GNU `timeout` is absent on stock macOS — background Gradle, bounded wait,
`SIGTERM` at expiry with exit code 124; commit `76229d58`). The gate task is
`:kadre:contracts:validator:androidContractsCheck`: registry validation plus
Android contract evidence validation against **`AND-001`, `AND-002` and
`AND-003`** — one canonical JSON per contract, all from the same instrumented
emulator run (15 instrumented tests).

The wrapper chains two steps, each of which can also be run on its own — one
Gradle invocation per shell command:

1. Publish the HEAD artifacts to the contract repository:

   ```shell
   ./gradlew :kadre:publishContractArtifacts
   ```

2. Compile the consumer against the published umbrella (the same invocation
   the forked run uses):

   ```shell
   ./gradlew -p kadre/consumers/android --no-daemon compileDebugKotlin \
     -PkadreRepository="$(pwd)/build/kadre-contract-repository" \
     -PkadreVersion=1.0.0
   ```

The consumer resolves `org.graphiks.kadre:kadre` from
`-PkadreRepository` only — never the included build — and runs
`connectedDebugAndroidTest` with `--refresh-dependencies` so a stale cached AAR
can never stand in for HEAD (a release-version cache would otherwise prove an
artifact that is no longer HEAD, the trap documented in the build file).

### Exit gate of roadmap phase 1

The gate the roadmap requires, in order, from a cold start: publish (1),
consumer compile (2), emulator smoke + evidence + validator (the script — 9
scenarios green, `AND-001.json` + `AND-002.json`), the transitive-resolution
purity check (no Desktop, no KFFI — Task 5 Step 3 redone cold):

```shell
./gradlew -p kadre/consumers/android :dependencies --configuration debugRuntimeClasspath \
  -PkadreRepository="$(pwd)/build/kadre-contract-repository" \
  -PkadreVersion=1.0.0 --console=plain | grep -i "kffi\|desktop"
```

`grep` must find **nothing** (exit 1) — the `androidJvm` variants of the
published umbrella delegate through `kadre-android` and must not drag any
Desktop backend or KFFI artifact in. The same invariant is pinned without a
device by `AndroidVariantPurityTest` in the umbrella's `jvmTest`; the
existing web/desktop tasks stay green via
`./gradlew :kadre:foundation:jvmTest :kadre:runtime:jvmTest :kadre:platform:web:compileKotlinJs`.

### Exit gate of roadmap phase 2

Same commands, one contract more. From a cold start (kill any running emulator,
`adb emu kill`, wait for zero devices, then let the script boot the AVD itself):
publish (1), consumer compile (2), emulator smoke + evidence + validator (the
script — **15** scenarios green, `AND-001.json` + `AND-002.json` +
`AND-003.json`), the same transitive-resolution purity check (grep exit 1,
redone cold), and the no-device sanity tasks above. The roadmap checklist is
carried by the evidence itself:

- metrics compared against the real View — `android-surface-metrics-match-view`,
  resize propagated without surface replacement — `android-surface-resize-propagates`
  (the surface identity and its revision discipline survive);
- bounded redraw without zombie callbacks — `android-surface-redraw-coalesced`
  (five requests, exactly one `RedrawRequested`) and
  `android-surface-redraw-after-close` (`Closed(Surface)`, no event follows),
  plus the host record `AndroidRedrawSchedulerTest.redrawAfterTerminalEmitsNothing`;
- zero SDK access after close — the lease answers `Closed(Surface)` without
  invoking after the terminal (`android-surface-lease-boundaries`), the view
  source is cleared at teardown, and the generation rejection kills stale
  callbacks (`android-metrics-generation-stale`, host-pinned);
- the consumer needs both opt-ins and obtains no generic
  `android.view.Window` — `android-surface-no-window-handle` (`platformAccess`
  stays `Unsupported(PlatformWindowAccess)` on the window);
- `AND-001` + `AND-002` + `AND-003` together — 15 instrumented tests, one
  canonical JSON per contract, all from the same instrumented emulator run.

## Engine labeling

The engine label is **never hardcoded**. The evidence producer
([AndroidContractEvidence.kt](src/jvmMain/kotlin/org/graphiks/kadre/contracts/android/AndroidContractEvidence.kt))
reads the attached device's identity from the system properties
`ro.build.version.sdk` and `ro.product.cpu.abi` via `adb shell getprop` and
writes `emulator-api-<sdk>-<abi>`. On the phase 0 AVD this is
`emulator-api-35-arm64-v8a`. `ANDROID_SERIAL` selects the device when several
are attached. Every run resets the per-engine JUnit copy directory first, so a
stale XML from an earlier run can never inflate the evidence identity. The
producer writes one canonical JSON per active android contract (currently
`AND-001`, `AND-002` and `AND-003`), each pinned to the current HEAD commit.

## Evidence mapping

The producer reuses the validator's canonical generator
(`GenerateContractEvidenceKt.main`, the same classes, main class and eight
arguments as the runtime/AppKit producers — no format reimplementation) with:

- the registry [contracts.tsv](../../registry/contracts.tsv) and the mapping
  [evidence.tsv](contracts/evidence.tsv) — the mapping is what binds each
  scenario/sentinel `evidenceId` to `KadreConsumerSessionTest` (`AND-001`) or
  `KadreConsumerAttachContractTest` (`AND-002`);
- the JUnit XML the consumer produced under
  `kadre/consumers/android/build/outputs/androidTest-results/connected/`;
- the current HEAD commit (the same `git rev-parse HEAD` rule the validator
  uses — a document pinned to a commit is stale by definition once HEAD moves).

Outputs: `build/contract-evidence/android/<engine>/AND-{001,002,003}.json`
(canonical), `build/test-results/android/<engine>/TEST-*.xml` (the JUnit copy
the validator cross-checks), `build/contract-evidence/android/engines.txt` (the
engines this run actually produced), and the gate copies
`build/contract-evidence/AND-{001,002,003}.json` that `validateContractEvidence`
reads.

## `androidContractsCheck` is deliberately not in `:kadre:check`

`:kadre:check` does **not** depend on `androidContractsCheck`: there is no
emulator on the current CI runners, and the aggregate requires a booted device
(the smoke re-runs the consumer instrumentation). Wiring the mandatory CI job
is phase 10 of the roadmap
(`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 10`).

## Phase 2 limits (deferred guarantees)

The public surface is the four `DESIGN.md` §15.1 overloads `attachKadre`
returning `KadreResult<KadreSession>` (two `ComponentActivity` receivers
requiring an explicit `surfaceView`, two `View` receivers requiring an explicit
`lifecycleOwner`), the admission ladder, the atomic dual-identity reservation,
the lifecycle observation with terminal detach, the two window topologies and
the rejected `requestWindow` — plus, since phase 2, the observable
`AndroidHostSurface`: metrics kept current from SDK observations (dimensions,
density, insets, appearance — a missing observation keeps its field `Unknown`,
never invented; focus and visibility stay lifecycle-owned), causes distinguished
(rotation / resize / density) with generation-based rejection of stale
callbacks, `requestRedraw` coalesced to one `RedrawRequested` per frame with a
`Closed(Surface)` answer and zero zombie callbacks after the terminal, `apply`
admitted per field on the exact P1 on-row shapes (every posed field rejected
`RejectedSurfaceField(Unsupported(UpdateSurface))` — no commit path yet), the
`withAndroidView` lease behind the double opt-in
`@KadrePlatformApi` + `@DelicateKadreApi` (single in-flight lease, host thread,
early cancellation rejected before anything is invoked, application exception
propagated as-is), and `platformAccess` `Supported` on the surface — while
`WindowCapabilities.platformAccess` stays `Unsupported(PlatformWindowAccess)`:
no public member ever exposes a generic `android.view.Window`.

Everything else is deferred, never silently absent — the
authoritative list with the owning phase lives in
[capabilities/android.md](../../../capabilities/android.md) §4:

- **Phase 3** — keyboard, pointer, touch (no listener is installed on the
  View); the four input-delivery `SurfaceUpdate` fields (`cursor`,
  `pointerCapture`, `hitTesting`, `inputDefaultBehavior`), rejected per field
  since phase 2 without calling any primitive; the redraw overflow actions
  driven by the runtime delivery policies (phase 2 coalescing emits at most one
  event per frame and bounded-flow overflow stays a documented drop);
- **Phase 4** — real window commands and transient interactions (the Activity primary window
  exists as a topology; its commands are answered on the exact contract-row shapes of
  `OPERATION-CONTRACTS` §4 — no real commands: `apply` rejects every requested field under
  `PartiallyApplied`, `requestAttention` stays `Unsupported`, `respondToCloseRequest` answers
  `Invalid("requestId")` because no `CloseRequested` is ever published — and a View host has
  no `Window` by the normative matrix); `close` answers `Temporary(retryable = false)`, the
  interim on-row response for a structurally non-closable host window (the row admits only
  `Temporary`/`Platform`; the normative decision — `Unsupported(CloseWindow)` would need an
  `OPERATION-CONTRACTS` edit — is parked for phase 4); the multi-window/dialog refinement of
  the "interactive window" conjunct of `Active` (derived from the lifecycle alone in phase 1:
  `Active` ⇔ `RESUMED`);
- **Phase 5** — text input/IME, gestures; **Phase 6** — drag-and-drop;
- **Phase 7** — displays, devices, gamepads, memory pressure, raw-input
  decision; **Phase 8** — capture; **Phase 9** — Java facade, Compose;
- **Phase 10** — the mandatory `android-contracts` CI job.

Two boundary lines are not deferrals at all. **Rendering itself** is out of
scope, permanently: phase 2 delivers an observable surface without owning its
rendering, and no phase will add a renderer, a render surface or a public event
loop. And whether focus/visibility observation ever moves from the lifecycle to
real SDK events is a decision the later phases own if a need is demonstrated —
until then the metrics binding keeps its observation fields `null` and never
competes with the lifecycle.

The thread contract of `attachKadre` is a phase 0 decision, not a deferral:
main thread only, non-suspending, no blocking or relocation — and, since
phase 1, the **only** throwing path of the public surface; every other refused
admission is a `KadreResult.Failure` naming the field, in the ladder order
`parentScope` → `view`/`surfaceView` → destroyed owner → identity conflict
(`capabilities/android.md` §3).
