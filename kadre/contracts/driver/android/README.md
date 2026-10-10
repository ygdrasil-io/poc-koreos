# Android emulator contract driver

This non-published JVM project runs the public Android host-attachment proof of
phases 0 and 1 on a real emulator. It is a contract driver, not an
implementation API: it forks the instrumented consumer build, executes its
scenarios on the attached device, and turns the JUnit output into the canonical
`AND-001` and `AND-002` evidence with the validator's own generator. English
prose, like the Web driver README it mirrors; everything else in this phase is
documented in French in the sources.

## What the driver proves

Two contracts, both active in the registry
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

The two sentinels carry demonstrated blast radius: under the recorded
mutations (late failure that skips the reservation rollback; ownership registry
keyed on a single global key) each kills **exactly one** of the nine tests —
its guard — and leaves the other eight green. A first mutation candidate
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
Android contract evidence validation against **`AND-001` and `AND-002`** —
one canonical JSON per contract, both from the same instrumented emulator run.

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
`AND-001` and `AND-002`), each pinned to the current HEAD commit.

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

Outputs: `build/contract-evidence/android/<engine>/AND-{001,002}.json`
(canonical), `build/test-results/android/<engine>/TEST-*.xml` (the JUnit copy
the validator cross-checks), `build/contract-evidence/android/engines.txt` (the
engines this run actually produced), and the gate copies
`build/contract-evidence/AND-{001,002}.json` that `validateContractEvidence`
reads.

## `androidContractsCheck` is deliberately not in `:kadre:check`

`:kadre:check` does **not** depend on `androidContractsCheck`: there is no
emulator on the current CI runners, and the aggregate requires a booted device
(the smoke re-runs the consumer instrumentation). Wiring the mandatory CI job
is phase 10 of the roadmap
(`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 10`).

## Phase 1 limits (deferred guarantees)

The phase 1 public surface is exactly the four `DESIGN.md` §15.1 overloads
`attachKadre` returning `KadreResult<KadreSession>` (two `ComponentActivity`
receivers requiring an explicit `surfaceView`, two `View` receivers requiring
an explicit `lifecycleOwner`), the admission ladder, the atomic
dual-identity reservation, the lifecycle observation with terminal detach, the
two window topologies and the rejected `requestWindow` — the full public
surface the normative matrix assigns to Android, with no phase-0-style opt-in
left (the phase 0 `@OptIn(DelicateKadreApi::class)` minimal attach existed only
to mark the interim surface and is gone with it). Everything else is deferred,
never silently absent — the
authoritative list with the owning phase lives in
[capabilities/android.md](../../../capabilities/android.md) §4:

- **Phase 2** — surface metrics/redraw, the `withAndroidView` lease (the
  surface metrics snapshot is frozen at attach; only attachment/visibility/
  focus are kept current by the lifecycle reducer);
- **Phase 3** — keyboard, pointer, touch (no listener is installed on the View);
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

The thread contract of `attachKadre` is a phase 0 decision, not a deferral:
main thread only, non-suspending, no blocking or relocation — and, since
phase 1, the **only** throwing path of the public surface; every other refused
admission is a `KadreResult.Failure` naming the field, in the ladder order
`parentScope` → `view`/`surfaceView` → destroyed owner → identity conflict
(`capabilities/android.md` §3).
