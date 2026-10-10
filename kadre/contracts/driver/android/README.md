# Android emulator contract driver

This non-published JVM project runs the public Android host-attachment proof of
phase 0 on a real emulator. It is a contract driver, not an implementation API:
it forks the instrumented consumer build, executes its smoke on the attached
device, and turns the JUnit output into the canonical `AND-001` evidence with
the validator's own generator. English prose, like the Web driver README it
mirrors; everything else in this phase is documented in French in the sources.

## What phase 0 proves

One contract, `AND-001` (registry line
[contracts.tsv](../../registry/contracts.tsv), source
`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 0`): a minimal Android `View` host
session. Three scenarios, two sentinels, oracle `O3`, mapped in
[evidence.tsv](contracts/evidence.tsv) to the single instrumented test class
`org.graphiks.kadre.consumer.KadreConsumerSessionTest`:

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-consumer-session-start` | scenario | `attachKadre(view, scope)` on an attached View returns a started session (`Starting`/`Running`) |
| `android-consumer-session-stop-cleanup` | scenario | `stop()` terminates cooperatively (`Terminated(Stopped(HostRequested))`), is idempotent |
| `android-consumer-double-attach-rejected` | scenario | a second `attachKadre` on the same View throws before any state mutation; after `stop()`, re-attach succeeds |
| `android-stop-no-resurrection` | sentinel | post-terminal `release` revives nothing; teardown leaves zero residue |
| `android-double-attach-single-owner` | sentinel | one View, one owner: the process-wide registry admits a single claim |

## Running the gate locally

Prerequisites: an Android SDK with the **AVD `Kadre_API_35`** (system image
arm64-v8a, API 35) and `ANDROID_HOME` set (or `sdk.dir` in the root
`local.properties`). The driver needs **no Android plugin** — it is a pure JVM
module that shells out to `adb` and forks `gradlew`.

Cold start — no emulator running; the script boots the AVD itself
(headless: `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`,
log at `/tmp/kadre-emulator.log`) and waits for
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
Android contract evidence validation against `AND-001`.

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

### Exit gate of roadmap phase 0

The gate the roadmap requires, in order, from a cold start: publish (1),
consumer compile (2), emulator smoke + evidence + validator (the script), the
transitive-resolution purity check (no Desktop, no KFFI — Task 5 Step 3 redone
cold):

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
stale XML from an earlier run can never inflate the evidence identity.

## Evidence mapping

The producer reuses the validator's canonical generator
(`GenerateContractEvidenceKt.main`, the same classes, main class and eight
arguments as the runtime/AppKit producers — no format reimplementation) with:

- the registry [contracts.tsv](../../registry/contracts.tsv) and the mapping
  [evidence.tsv](contracts/evidence.tsv) — the mapping is what binds each
  scenario/sentinel `evidenceId` to `KadreConsumerSessionTest`;
- the JUnit XML the consumer produced under
  `kadre/consumers/android/build/outputs/androidTest-results/connected/`;
- the current HEAD commit (the same `git rev-parse HEAD` rule the validator
  uses — a document pinned to a commit is stale by definition once HEAD moves).

Outputs: `build/contract-evidence/android/<engine>/AND-001.json` (canonical),
`build/test-results/android/<engine>/TEST-*.xml` (the JUnit copy the validator
cross-checks), `build/contract-evidence/android/engines.txt` (the engines this
run actually produced), and the gate copy `build/contract-evidence/AND-001.json`
that `validateContractEvidence` reads.

## `androidContractsCheck` is deliberately not in `:kadre:check`

`:kadre:check` does **not** depend on `androidContractsCheck`: there is no
emulator on the current CI runners, and the aggregate requires a booted device
(the smoke re-runs the consumer instrumentation). Wiring the mandatory CI job
is phase 10 of the roadmap
(`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 10`).

## Phase 0 limits (deferred guarantees)

The phase 0 public surface is exactly `attachKadre(view, parentScope)` +
`KadreAndroidViewSession` (`stop()`/`close()`/`session`), opt-in via
`@DelicateKadreApi`, and phase-0-only by its own KDoc. Everything else is
deferred, never silently absent — the authoritative list with the owning phase
lives in [capabilities/android.md](../../../capabilities/android.md) §4:

- **Phase 1** — the four `DESIGN.md` §15.1 overloads, the admission failure
  taxonomy (`InvalidRequest("parentScope")`, `ParentScopeCancelled`,
  `AlreadyInUse(Host)`; phase 0 rejects by `IllegalStateException` naming the
  field, order `mainThread` → `view` → ownership), lifecycle observation;
- **Phase 2** — surface metrics/redraw, the `withAndroidView` lease (the phase 0
  surface snapshot is frozen at attach);
- **Phase 3** — keyboard, pointer, touch (no listener is installed on the View);
- **Phase 4** — Activity window and transient interactions (a View host has no
  `Window` by the normative matrix);
- **Phase 5** — text input/IME, gestures; **Phase 6** — drag-and-drop;
- **Phase 7** — displays, devices, gamepads, memory pressure, raw-input
  decision; **Phase 8** — capture; **Phase 9** — Java facade, Compose;
- **Phase 10** — the mandatory `android-contracts` CI job.

The thread contract of `attachKadre` is a phase 0 decision, not a deferral:
main thread only, non-suspending, no blocking or relocation, rejections before
any state mutation (`capabilities/android.md` §3).
