# AppKit phases 8 and 9 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the AppKit raw-input, display, outer-geometry, appearance, and exclusive-fullscreen capabilities with truthful availability, generated KFFI bindings, and complete O2/O3 evidence.

**Architecture:** Keep all public state machines, identities, budgets, reducers, policies, and event ordering in `kadre:runtime`; AppKit supplies immutable stimuli and executes already-admitted commands. Process-wide macOS facilities are owned by specialised AppKit brokers; a session-local coordinator projects them into public handles and flows. Kextract is the sole source of binding changes and KFFI is regenerated from it; Kadre contains no handwritten FFI.

**Tech Stack:** Kotlin Multiplatform, kotlinx.coroutines `Flow`/`StateFlow`, JVM 25, AppKit/CoreGraphics through `org.graphiks:kffi-objc`, Gradle, Kextract generated Objective-C/CoreGraphics bindings, macOS AppKit native tests.

**Spec:** `docs/superpowers/specs/2026-09-07-appkit-phases-8-9-design.md`

## Global Constraints

- Read the spec above, `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`, and the relevant normative document before changing each surface.
- This is incubation: change API and ABI together where the spec requires it; do not retain compatibility shims that make a capability ambiguous.
- `runtime` must not import AppKit, CoreGraphics, KFFI, `MemorySegment`, Objective-C selectors, or generated native handles.
- Kadre must not add a handwritten FFI declaration, callback, raw-memory access, selector dispatch, or generated-source patch. Missing bindings are added to Kextract, then regenerated and tested in KFFI.
- A public capability is `Supported` only with the complete implementation and active contract evidence. Unsupported and unavailable paths remain executable and tested.
- `RawInputAccess` is explicit fan-out: one access never owns or suppresses another access, and no raw event enters ordinary input state or `SurfaceInput.events`.
- Keep state-before-event ordering, independent per-access budgets, and structured ownership: surface detach/window close/session teardown closes only its children.
- Keep `LifecycleCapabilities.memoryPressure` unsupported in this phase. `APK-018` observes the public `Unsupported` capability only; no test proves silence by waiting.
- Kextract and KFFI PRs may stack and KFFI may be published locally for dependent Kadre tests. No Kadre PR merges with a temporary artifact coordinate.
- Each new active contract row has every ID in **Contract evidence allocation** mapped in `evidence.tsv`, and `:kadre:contracts:validator:check` remains green.
- The plans and design docs under `docs/superpowers/` are removed in the final implementation-cleanup PR before its final review, not before their implementation tasks have an approved replacement.

---

## Delivery topology and file map

| Stack | Deliverable | Primary files | Proof |
|---:|---|---|---|
| 1 | Common raw-input API, policy, budgets and docs | `foundation/.../input/{SurfaceInput,TextDropRaw}.kt`, `policy/{DeliveryPolicies,KadrePolicy,KadrePolicies}.kt`, `diagnostics/Results.kt`, foundation tests and normative docs | API/O1 + policy O2; `INP-002` remains planned |
| 2 | Portable raw coordinator | new `runtime/.../RawInputCoordinator.kt`, new `RawInputPort.kt`, `RuntimeSessionComponents.kt`, runtime tests | `INP-002` O2 |
| 3–4 | Generated raw bridge | Kextract request + generated KFFI source/tests; `kadre/KFFI-REQUIREMENTS.md` | Kextract/KFFI generation and macOS binding proof |
| 5 | AppKit raw broker and permission lifecycle | new AppKit raw/permission broker files, AppKit factory/peer wiring, native tests/manual protocol | `APK-013` O3 |
| 6 | Common display/mode/appearance API | `display/{Display,Identity}.kt`, `surface/Surface.kt`, all consumers and documents | API/O1 + values O2 |
| 7 | Portable display coordinator and surface routing | new runtime display port/coordinator, session wiring, runtime tests | `DSP-001`, `RUN-007`, `RUN-008` O2 |
| 8–9 | Generated display/mode/appearance bridge | Kextract request + generated KFFI source/tests; `KFFI-REQUIREMENTS.md` | Kextract/KFFI generation and macOS binding proof |
| 10 | AppKit display, appearance and outer geometry | new AppKit display broker, window port/peer/driver, native tests/manual protocol | `APK-014`, `APK-015`, `APK-017` O3 |
| 11 | Portable exclusive lease coordinator | new runtime exclusive lease port/coordinator, window-manager integration and tests | `WIN-008` O2 |
| 12 | AppKit exclusive activation and phase closure | AppKit exclusive port/broker/driver, O3/manual docs, registry and public docs | `APK-016`, `APK-018` O3 and final gate |

The tasks below produce twelve reviewable, stacked PRs in this exact order. A Kextract/KFFI stack may be merged after its dependent Kadre PR is proven locally, but every downstream Kadre branch is rebased onto the published KFFI artifact before it is merged.

## Contract evidence allocation

These are the exact scenario and sentinel IDs to add to `contracts.tsv` and
map in the named `evidence.tsv` files. The registry is kept `planned` until
Task 12, when a missing mapping or non-executed test prevents activation.

| Contract | Scenarios | Sentinels |
|---|---|---|
| `INP-002` | `runtime-raw-admission`, `runtime-raw-fanout`, `runtime-raw-overflow`, `runtime-raw-permission-waiter`, `runtime-raw-recovery`, `runtime-raw-owner-close` | `runtime-raw-budget-before-native`, `runtime-raw-no-ordinary-injection`, `runtime-raw-cross-access-isolation`, `runtime-raw-revocation-order`, `runtime-raw-late-callback` |
| `APK-013` | `appkit-raw-generated-listen-only`, `appkit-raw-permission-readback`, `appkit-raw-public-activation`, `appkit-raw-tap-recovery`, `appkit-raw-owner-revocation` | `appkit-raw-unmodified-event`, `appkit-raw-no-keyboard-mask`, `appkit-raw-no-ordinary-injection`, `appkit-raw-closed-owner-callback` |
| `DSP-001` | `runtime-display-snapshot`, `runtime-display-mode-identity`, `runtime-display-removal`, `runtime-display-reconnect`, `runtime-display-scale-propagation` | `runtime-display-partial-inventory`, `runtime-display-id-reuse`, `runtime-display-event-before-state`, `runtime-display-mode-alias` |
| `APK-014` | `appkit-display-generated-snapshot`, `appkit-display-reconfiguration`, `appkit-display-capability-readback` | `appkit-display-callback-after-close`, `appkit-display-partial-mode-list` |
| `WIN-007` | `runtime-global-outer-geometry`, `runtime-outer-position-gap`, `runtime-geometry-straddling-readback` | `runtime-geometry-double-scale`, `runtime-geometry-event-before-state`, `runtime-geometry-straddling-fabricated` |
| `APK-015` | `appkit-geometry-secondary-display`, `appkit-geometry-external-move`, `appkit-geometry-native-readback` | `appkit-geometry-secondary-double-offset`, `appkit-geometry-backscale-multiply`, `appkit-geometry-late-callback` |
| `WIN-008` | `runtime-exclusive-lease`, `runtime-exclusive-initial-request`, `runtime-exclusive-cancellation`, `runtime-exclusive-rollback`, `runtime-exclusive-display-loss`, `runtime-exclusive-teardown` | `runtime-exclusive-borderless-domain-lost`, `runtime-exclusive-published-orphan`, `runtime-exclusive-lease-reuse-before-reconcile`, `runtime-exclusive-accepted-before-terminal` |
| `APK-016` | `appkit-exclusive-generated-bridge`, `appkit-exclusive-public-activation`, `appkit-exclusive-readback` | `appkit-exclusive-handwritten-ffi`, `appkit-exclusive-mode-change-without-capture`, `appkit-exclusive-release-omitted` |
| `RUN-007` | `runtime-appearance-pair`, `runtime-appearance-order` | `runtime-appearance-split-event`, `runtime-appearance-unchanged-event` |
| `APK-017` | `appkit-appearance-effective-readback`, `appkit-appearance-notification`, `appkit-appearance-owner-close` | `appkit-appearance-before-state`, `appkit-appearance-late-callback` |
| `RUN-008` | `runtime-memory-pressure-unsupported` | `runtime-memory-pressure-synthetic-signal` |
| `APK-018` | `appkit-memory-pressure-unsupported` | `appkit-memory-pressure-capability-lie` |

### Task 1: Public raw-input contract, policy, budgets, and ABI fixtures

**Files:**
- Modify: `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/input/SurfaceInput.kt`
- Modify: `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/input/TextDropRaw.kt`
- Modify: `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/policy/{DeliveryPolicies,KadrePolicy,KadrePolicies}.kt`
- Modify: `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/diagnostics/Results.kt`
- Modify: `kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/{input/InputValuesTest,policy/KadrePoliciesTest,policy/PolicyValidationTest}.kt`
- Modify: `kadre/consumers/kotlin/src/main/kotlin/Consumer.kt`, `kadre/consumers/java/src/main/java/Consumer.java`
- Modify: `kadre/{DESIGN,PUBLIC-API-CATALOG,OPERATION-CONTRACTS,POLICY-PROFILES,BACKEND-CAPABILITIES,INTEROP-EXPORTS,KFFI-REQUIREMENTS}.md`

**Consumes:** Existing `RawInputAccess`, `InputDeliveryPolicy`, `ResourceBudgetPolicy`, `KadreResult`, and consumer compile checks.

**Produces:**

```kotlin
public interface SurfaceInput {
    @DelicateKadreApi
    public suspend fun requestRawInput(): KadreResult<RawInputAccess>
}

public data class RawInputDeliveryPolicy(
    public val capacity: Int,
    public val onOverflow: RawInputOverflowAction,
)

public enum class RawInputOverflowAction {
    DropOldestAndReport,
    DropLatestAndReport,
    CloseAccess,
}
```

`InputDeliveryPolicy.rawInput`, `ResourceBudgetPolicy.maxConcurrentRawInputAccesses`, and `KadreResourceKind.RawInputAccess` are available to the runtime tasks.

- [ ] **Step 1: Write the API and policy tests before changing production declarations.**

  Add tests that compile and exercise the member call, validate a non-positive raw capacity and raw access limit, assert `RawInputAccess` is the resource in `ResourceLimitExceeded`/`SourceOverflow`, and assert the three exact profile values:

  ```kotlin
  assertEquals(RawInputDeliveryPolicy(256, DropOldestAndReport), KadrePolicies.Default.input.rawInput)
  assertEquals(RawInputDeliveryPolicy(64, DropOldestAndReport), KadrePolicies.Realtime.input.rawInput)
  assertEquals(RawInputDeliveryPolicy(8192, CloseAccess), KadrePolicies.Recording.input.rawInput)
  ```

- [ ] **Step 2: Run the focused foundation and consumer compilation checks; record the expected compilation failures.**

  Run: `rtk ./gradlew :kadre:foundation:jvmTest :kadre:validateKotlinConsumer :kadre:validateJavaConsumer`

  Expected: the new member/policy references do not compile until the declarations and every consumer fixture are updated.

- [ ] **Step 3: Implement the closed public model.**

  Move `requestRawInput()` from the extension in `TextDropRaw.kt` to an `@DelicateKadreApi` member of `SurfaceInput`; remove the extension completely. Add `RawInputDeliveryPolicy` and its closed action enum to `DeliveryPolicies.kt`, thread it through the `InputDeliveryPolicy` constructor and all three profile factories, and add the raw-access session limit to `ResourceBudgetPolicy` and all profiles. Add `RawInputAccess` to `KadreResourceKind`; do not add a process-wide input-source limit because the global source is shared. Update all normative documents and Java/Kotlin consumers in the same breaking change.

- [ ] **Step 4: Make the tests pass and validate the complete public artifact.**

  Run: `rtk ./gradlew :kadre:foundation:check :kadre:validateKotlinConsumer :kadre:validateJavaConsumer`

  Expected: PASS. Confirm source-level `SurfaceInput.requestRawInput()` is the only public declaration with that name and no consumer relies on an extension import.

- [ ] **Step 5: Commit the first stack base.**

  ```bash
  git add kadre/foundation kadre/consumers kadre/{DESIGN,PUBLIC-API-CATALOG,OPERATION-CONTRACTS,POLICY-PROFILES,BACKEND-CAPABILITIES,INTEROP-EXPORTS,KFFI-REQUIREMENTS}.md
  git commit -m "feat(raw-input): define common access policy"
  ```

### Task 2: Portable raw-input coordinator and O2 proof

**Files:**
- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RawInputPort.kt`
- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RawInputCoordinator.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/{RuntimeSessionComponents,MinimalWindowSurface,RuntimeWindowManager,RuntimeDiagnostics}.kt`
- Create: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RawInputCoordinatorTest.kt`
- Modify: `kadre/runtime/contracts/evidence.tsv`, `kadre/contracts/registry/contracts.tsv`, `kadre/TEST-STRATEGY.md`

**Consumes:** Task 1 declarations and existing runtime collector admission/diagnostics facilities.

**Produces:** An internal port with immutable stimuli and a session-local `RawInputCoordinator` that owns all public accesses for one session.

```kotlin
internal interface RawInputPort : AutoCloseable {
    public val events: Flow<RawInputPortEvent>
    suspend fun requestAccess(): KadreResult<RawInputPortLease>
    override fun close()
}

internal sealed interface RawInputPortEvent {
    public data class Input(public val event: RawInputEvent) : RawInputPortEvent
    public data class Availability(public val availability: FeatureAvailability) : RawInputPortEvent
    public data class Terminal(public val failure: KadreFailure) : RawInputPortEvent
}

internal interface RawInputPortLease : AutoCloseable {
    override fun close()
}
```

`RawInputPort`, `RawInputPortLease`, and `RawInputCoordinator` are the exact internal names for this phase. The port exposes only Kadre values and callback registration, never AppKit/KFFI types.

- [ ] **Step 1: Write independent O2 trace tests.**

  Create a deterministic fake port and model traces covering: two accesses on one surface and two surfaces receive the same copied event; each access has a separate capacity; drop-oldest/drop-latest produce one `EventLoss`; `CloseAccess` closes only its own flow with `KadreException(SourceOverflow(RawInputAccess))`; budget exhaustion happens before permission/native admission; permission waiter cancellation only removes that waiter; revocation orders capability then all suspended accesses then source stop; recovery returns those accesses to `Active`; detach/window/session close normally closes descendants.

- [ ] **Step 2: Run the new O2 test class and verify it fails before coordinator implementation.**

  Run: `rtk ./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RawInputCoordinatorTest`

  Expected: FAIL because the coordinator and fake port do not exist.

- [ ] **Step 3: Implement admission, lifecycle, and per-access queues.**

  Give every access a private queue constructed from `policy.input.rawInput`, keep the session counter under `maxConcurrentRawInputAccesses`, and make the port registration fan out copied `RawInputEvent` values. A slow or closed access is removed independently. Surface lifecycle ownership, rather than focus, determines closure. `SurfaceInput.events` and ordinary input reducers must never receive raw stimuli.

- [ ] **Step 4: Register the complete planned O2 contract without activating it.**

  Add `INP-002` with `status=planned`, the six scenarios and five sentinels from the allocation table, and the matching `runtime` mappings after the tests exist. Leave raw input capability unsupported until Task 5 completes its O3 bridge and Task 12 changes the registry state.

- [ ] **Step 5: Verify and commit.**

  Run: `rtk ./gradlew :kadre:runtime:jvmTest :kadre:contracts:validator:check`

  Expected: PASS.

  ```bash
  git add kadre/runtime kadre/contracts kadre/TEST-STRATEGY.md
  git commit -m "feat(raw-input): add session coordinator"
  ```

### Task 3: Audit the generated raw-input surface and change Kextract

**Files:**
- Modify: `kadre/KFFI-REQUIREMENTS.md`
- External Kextract checkout: `/Users/chaos/workspace/wgpu4k-native/kextract`
- External KFFI checkout: `/Users/chaos/workspace/graphiks-kffi/kffi`

**Consumes:** The raw bridge requirements in spec section 3.2.

**Produces:** A checked, generated availability matrix for `CGPreflightListenEventAccess`, `CGRequestListenEventAccess`, listen-only `CGEventTapCreate`, event-mask constants, tap enablement, run-loop attach/remove, invalidation/release, disabled-tap event kinds, `kCGMouseEventDeltaX/Y`, and revocable callback ownership.

- [ ] **Step 1: Write the KFFI compile/runtime binding contract test first.**

  In KFFI, write a macOS test that imports each required generated declaration, creates only a listen-only mouse-motion event-tap configuration, reads the two delta fields, closes its callback owner, and proves a callback cannot start after close. The test must not use `ObjCRuntime.msgSend`, handwritten selectors, or a Kadre helper.

- [ ] **Step 2: Run the KFFI test against the published base artifact.**

  Run from the KFFI checkout: `rtk ./gradlew jvmTest --tests '*RawInput*'`

  Expected: either PASS with all declarations generated or a compilation failure naming each absent generated declaration.

- [ ] **Step 3: Apply the exact generator change for every failed declaration.**

  Read Kextract's root contribution guide before any edit. Add each missing C/CoreGraphics function, constant, enum case, struct layout, and ownership annotation to Kextract's source model and generator tests; regenerate its golden/generated output. Do not add a KFFI handwritten wrapper as a substitute. If Step 2 passes entirely, commit only the audited `KFFI-REQUIREMENTS.md` table marking each symbol `generated` and do not open an empty Kextract PR.

- [ ] **Step 4: Run Kextract's mandated generation and test commands, then stack its PR.**

  Run the exact commands required by Kextract's contribution guide, including its generation verification. Commit the generator source and generated expectations together; open a PR whose scope is only the raw-input declarations.

- [ ] **Step 5: Record the precise generation result in Kadre.**

  Update `KFFI-REQUIREMENTS.md` with the Kextract PR/commit, every required raw symbol, and the statement that Kadre consumes only generated KFFI. Commit: `docs(kffi): record raw input generated surface`.

### Task 4: Regenerate, validate, and locally publish KFFI raw-input bindings

**Files:**
- External KFFI checkout: `/Users/chaos/workspace/graphiks-kffi/kffi`
- Modify in KFFI: generated binding output and its generated-source macOS tests only
- Modify: `kadre/build.gradle.kts` only while resolving the local KFFI artifact for dependent stack validation

**Consumes:** Task 3's Kextract branch or merged main.

**Produces:** A KFFI artifact containing the generated raw input declarations and an O3-safe ownership test.

- [ ] **Step 1: Point KFFI at the approved Kextract branch and regenerate.**

  Follow KFFI's root contribution guide. Regenerate; never edit the resulting Objective-C/CoreGraphics binding source manually. Assert the diff contains the Task 3 declarations and their availability metadata.

- [ ] **Step 2: Run KFFI generation and native tests.**

  Run the guide-mandated generation verification plus `rtk ./gradlew jvmTest --tests '*RawInput*'`. Expected: the binding test proves callback owner revocation and does not assert Kadre behavior.

- [ ] **Step 3: Publish a local snapshot and make Kadre consume it only for this stack.**

  Publish KFFI to the local test repository configured by KFFI. In the Kadre branch, use the local coordinate exclusively for validation, run `rtk ./gradlew :kadre:backend:appkit:compileKotlin`, and restore the published coordinate before any Kadre PR merge.

- [ ] **Step 4: Open and verify the KFFI PR.**

  Its review diff must consist of generator output, dependency pin, and tests. After Kextract merges, retarget KFFI to Kextract `master`, regenerate again, and rerun the same tests before merge.

### Task 5: AppKit raw broker, true permission lifecycle, and `APK-013` O3 proof

**Files:**
- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/{AppKitPermissionBroker,AppKitRawInputBroker,AppKitRawInputPort}.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/{AppKitBackendProvider,AppKitProcessBroker,AppKitWindowPeer}.kt`
- Create: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitRawInputBrokerTest.kt`
- Create: `kadre/backend/appkit/manual/{Phase8RawInputHarness.kt,phase-8-raw-input.md}`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`, `kadre/KFFI-REQUIREMENTS.md`, `kadre/BACKEND-CAPABILITIES.md`

**Consumes:** Task 2 portable port and the published KFFI binding from Task 4.

**Produces:** A process-wide permission broker and one process-wide listen-only event-tap broker with session-owned registrations.

- [ ] **Step 1: Write the O3 tests from the public boundary.**

  Test generated KFFI preflight readback, a denied request returning `PermissionDenied(RawInput)`, one shared native permission request for concurrent session waiters, an unchanged listen-only callback, `DeviceCount` plus `deviceId = null`, no normal input event/state mutation, owner revocation, timeout disable/re-enable, user-input disable/re-probe, and close after the last registration. Observe only public access state/events/capability and an external generated callback fixture.

- [ ] **Step 2: Run the new AppKit test class before implementation.**

  Run: `rtk ./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitRawInputBrokerTest`

  Expected: FAIL because AppKit raw broker wiring is absent.

- [ ] **Step 3: Implement the two brokers with strict ownership.**

  `AppKitPermissionBroker` owns preflight plus one in-flight request only. `AppKitRawInputBroker` owns one `kCGSessionEventTap`, head placement, `kCGEventTapOptionListenOnly`, its run-loop source and its revocable KFFI callback owner. Restrict the mask to mouse move/drag kinds, copy only deltas, return the exact native event, and marshal immutable stimuli to Task 2's port. Do not read keyboard data, synthesize/suppress events, or use an accessibility fallback.

- [ ] **Step 4: Add the manual protocol without elevating it to CI evidence.**

  The manual guide contains exact setup, permission grant/revoke, two-access fan-out, timeout recovery, expected public states, and cleanup steps. The harness prints capability/state/event traces but never determines an automated pass.

- [ ] **Step 5: Map, verify, and commit.**

  Map every `APK-013` scenario/sentinel in AppKit `evidence.tsv` but retain it `planned` until Task 12's activation sweep. Run: `rtk ./gradlew :kadre:backend:appkit:check :kadre:contracts:validator:check`.

  Commit: `feat(appkit): bridge raw input through generated kffi`.

### Task 6: Common display identities, mode identity, and atomic surface appearance

**Files:**
- Modify: `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/display/{Display,Identity}.kt`
- Modify: `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/surface/Surface.kt`
- Modify: `kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/{surface/GeometryTest,window/WindowValuesTest}.kt`
- Modify: `kadre/consumers/{kotlin/src/main/kotlin/Consumer.kt,java/src/main/java/Consumer.java}`
- Modify: `kadre/{DESIGN,PUBLIC-API-CATALOG,OPERATION-CONTRACTS,BACKEND-CAPABILITIES,INTEROP-EXPORTS,API-MIGRATION}.md`

**Consumes:** Existing display and surface API.

**Produces:**

```kotlin
public class DisplayModeId internal constructor(private val value: Long)

public data class DisplayMode(
    public val id: DisplayModeId,
    public val physicalSize: PhysicalSize,
    public val refreshRateHz: Double?,
    public val bitDepth: Int?,
)

public enum class SurfaceContrast { Standard, Increased, Unknown }
```

`SurfaceState.contrast` is atomic with theme and `SurfaceEvent.ThemeChanged` is replaced by `SurfaceEvent.AppearanceChanged`.

- [ ] **Step 1: Write failing API/value tests and compile consumers.**

  Test unforgeable/opaque `DisplayModeId`, distinct modes with identical scalar descriptions, display removal requiring terminal `Disconnected`, `SurfaceContrast` closed values, and one appearance event for a changed `(theme, contrast)` pair. Update consumer code to require the new `DisplayMode.id` and `AppearanceChanged`.

- [ ] **Step 2: Run the focused checks before implementation.**

  Run: `rtk ./gradlew :kadre:foundation:jvmTest :kadre:validateKotlinConsumer :kadre:validateJavaConsumer`

  Expected: FAIL until every construction site and exported consumer is migrated.

- [ ] **Step 3: Implement the intentional ABI break everywhere.**

  Add `DisplayModeId` only with an internal constructor; add `id` to `DisplayMode`; add `contrast` to `SurfaceState`; replace the event variant everywhere instead of maintaining an alias. Update interop exports and `API-MIGRATION.md` so Java/Swift/JS consumers see the exact renamed shape.

- [ ] **Step 4: Verify public API and commit.**

  Run: `rtk ./gradlew :kadre:foundation:check :kadre:validateKotlinConsumer :kadre:validateJavaConsumer`

  Commit: `feat(display): add mode and surface appearance identities`.

### Task 7: Portable display coordinator, appearance routing, and O2 contracts

**Files:**
- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/{DisplayPort,DisplayCoordinator}.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/{RuntimeSessionComponents,RuntimeWindowManager,MinimalWindowSurface,UnsupportedManagers}.kt`
- Create: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/DisplayCoordinatorTest.kt`
- Modify: `kadre/runtime/contracts/evidence.tsv`, `kadre/contracts/registry/contracts.tsv`, `kadre/{TEST-STRATEGY,OPERATION-CONTRACTS}.md`

**Consumes:** Task 6 public model.

**Produces:** A session display manager that projects full native snapshots, allocates opaque IDs, propagates scale/appearance stimuli, and remains capability-honest.

```kotlin
internal interface DisplayPort : AutoCloseable {
    public val snapshots: Flow<DisplayPortSnapshot>
    override fun close()
}

internal data class DisplayPortSnapshot(
    public val displays: List<DisplayPortDisplay>,
    public val primaryNativeKey: Long?,
    public val availability: FeatureAvailability,
)
```

`DisplayPortDisplay` is an internal immutable value containing the native key,
global physical bounds, scale, optional work area/name/current mode, and the
complete fingerprinted mode list. It is never public and is consumed only by
`DisplayCoordinator`.

- [ ] **Step 1: Write model-based O2 traces.**

  Cover coherent complete snapshot publication, only-added mode fingerprint IDs, unchanged-fingerprint stability, terminal `Disconnected → inventory without handle → Removed` ordering, a reconnection receiving a new `DisplayId`, invalid/unknown inventory behavior, no mode-list fabrication, half-open physical point ownership, negative coordinates, and surface state-before-appearance-event/no-change suppression. Add deterministic unsupported-memory tests to `RUN-008` only.

- [ ] **Step 2: Run the test class and confirm it fails.**

  Run: `rtk ./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.DisplayCoordinatorTest`

  Expected: FAIL because the port/coordinator and stimuli do not exist.

- [ ] **Step 3: Implement projection with no native leakage.**

  Feed immutable snapshots into the coordinator. It owns `DisplayId`, `DisplayModeId`, handles and revisions; native fingerprints/mode handles stay in the port. Publish `DisplayManager.state` before display events and preserve final handles. Route scale and `(theme, contrast)` together through the existing surface stimulus path. Leave memory pressure structurally unsupported.

- [ ] **Step 4: Register all common contract evidence.**

  Add planned rows and mappings for `DSP-001`, `RUN-007`, `RUN-008`, and `WIN-007` using every scenario and sentinel in the allocation table. Do not activate a capability or contract in this task.

- [ ] **Step 5: Verify and commit.**

  Run: `rtk ./gradlew :kadre:runtime:check :kadre:contracts:validator:check`

  Commit: `feat(display): add runtime display coordinator`.

### Task 8: Audit and extend Kextract for display, appearance, and exclusive primitives

**Files:**
- Modify: `kadre/KFFI-REQUIREMENTS.md`
- External Kextract checkout: `/Users/chaos/workspace/wgpu4k-native/kextract`
- External KFFI checkout: `/Users/chaos/workspace/graphiks-kffi/kffi`

**Consumes:** Spec sections 4–5 and Task 7 ports.

**Produces:** Generated support for `NSScreen` snapshots/backing conversion, display reconfiguration callbacks, display mode fingerprint enumeration, capture/release, mode switching/restoration, and effective appearance observation.

- [ ] **Step 1: Add KFFI binding tests that enumerate the required generated API.**

  The tests instantiate the generated observer/callback owners, read screen frames and backing conversions, enumerate mode discriminator fields, and compile capture/release/mode calls. They assert generated ownership and availability metadata; they do not change a user display mode in CI.

- [ ] **Step 2: Run the binding tests before Kextract changes.**

  Run from KFFI: `rtk ./gradlew jvmTest --tests '*Display*' --tests '*Appearance*'`

  Expected: compile failures enumerate any missing generated primitive.

- [ ] **Step 3: Implement only missing declarations in Kextract and regenerate.**

  Read Kextract `CONTRIBUTING` first. Model ownership, callback lifetime, value/struct layouts, constants, and macOS availability in the generator. Add generator tests and regenerated expected outputs. A binding already generated is documented as such in Kadre, not reimplemented.

- [ ] **Step 4: Verify Kextract and stack its PR.**

  Run the contribution-guide test and generation commands exactly. Commit generator source plus generated expectations, and open a PR limited to display/appearance/exclusive declarations.

- [ ] **Step 5: Update the Kadre requirements table.**

  Record every primitive, owning generated API, availability, Kextract commit/PR, and KFFI test class in `KFFI-REQUIREMENTS.md`. Commit: `docs(kffi): record display generated surface`.

### Task 9: Regenerate, validate, and locally publish KFFI display bindings

**Files:**
- External KFFI checkout: `/Users/chaos/workspace/graphiks-kffi/kffi`
- Modify in KFFI: generator output, generated-source macOS tests, and Kextract dependency pin
- Modify: `kadre/build.gradle.kts` only for temporary local test resolution

**Consumes:** Task 8 Kextract branch.

**Produces:** A locally publishable and later published KFFI artifact that Kadre can consume without FFI escape hatches.

- [ ] **Step 1: Regenerate KFFI against the Task 8 branch and inspect generated-only changes.**

  Follow the root contribution guide; reject a diff that contains a handwritten KFFI wrapper or changed generated source without corresponding Kextract input.

- [ ] **Step 2: Run KFFI native binding tests and local publish.**

  Run the guide-mandated generation check and `rtk ./gradlew jvmTest --tests '*Display*' --tests '*Appearance*'`, then publish the local snapshot.

- [ ] **Step 3: Compile Kadre against that exact local snapshot.**

  Run: `rtk ./gradlew :kadre:backend:appkit:compileKotlin :kadre:backend:appkit:testClasses`. Expected: PASS with no Kadre FFI additions.

- [ ] **Step 4: Rebase the KFFI PR to Kextract `master` after merge, regenerate, retest, and merge/publish it.**

  Restore Kadre to the published Maven coordinate and run `rtk ./gradlew --refresh-dependencies :kadre:backend:appkit:compileKotlin` before starting Task 10.

### Task 10: AppKit display broker, appearance, and global outer geometry

**Files:**
- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/{AppKitDisplayBroker,AppKitDisplayPort}.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/{AppKitBackendProvider,AppKitWindowPeer,AppKitWindowRuntimeDriver,AppKitNativeWindowPort,KffiAppKitWindowPort}.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/{AppKitBackendProviderTest,AppKitWindowRuntimeDriverTest,KffiAppKitWindowPortMacOsTest}.kt`
- Create: `kadre/backend/appkit/manual/{Phase9DisplayHarness.kt,phase-9-displays.md}`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`, `kadre/{BACKEND-CAPABILITIES,KFFI-REQUIREMENTS}.md`

**Consumes:** Tasks 6–9.

**Produces:** A process-wide native display broker, session display port, AppKit appearance mapping, and physical global outer geometry readback/mutation.

- [ ] **Step 1: Write O3 tests using an AppKit window and generated bridge.**

  Cover public inventory/capability readback and callback teardown; native effective appearance maps to the exact pair before one `AppearanceChanged`; no pair change produces no event; external move/resize produces state then uncorrelated event; one-screen frames convert to global physical coordinates; negative secondary-display coordinates work; a straddling frame produces `outerBounds = null`; a physical point in a gap rejects `outerPosition`; and a contained transition republishes bounds.

- [ ] **Step 2: Run the focused native and driver checks; verify failure.**

  Run: `rtk ./gradlew :kadre:backend:appkit:jvmTest --tests '*Display*' --tests '*Geometry*' --tests '*Appearance*'`

  Expected: FAIL until the AppKit port and broker are wired.

- [ ] **Step 3: Implement broker snapshots and the coordinate transform exactly.**

  `AppKitDisplayBroker` owns only native display observation and full immutable snapshots; the session port feeds Task 7. For bounds, require the whole `NSWindow.frame` to lie in one `NSScreen.frame`; convert both the window frame and screen frame with `convertRectToBacking`, subtract the screen backing origin, add the matching `CGDisplayBounds` origin, and invert y. For mutation, reverse those exact operations and use `convertRectFromBacking`. Never derive physical coordinates by multiplying `backingScaleFactor`; return `outerBounds = null` for straddling.

- [ ] **Step 4: Add the manual display protocol.**

  Cover hot-plug/reconnect, differing scale factors, negative coordinates, window straddling, external move/resize, and unavailable display/mode inventory. The protocol reports observation only and does not manufacture CI success.

- [ ] **Step 5: Map, verify, and commit.**

  Map every `APK-014`, `APK-015`, and `APK-017` scenario and sentinel in the allocation table. Run: `rtk ./gradlew :kadre:backend:appkit:check :kadre:contracts:validator:check`.

  Commit: `feat(appkit): project displays and physical window geometry`.

### Task 11: Portable exclusive lease coordinator and O2 proof

**Files:**
- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/{ExclusiveDisplayPort,ExclusiveDisplayLeaseCoordinator}.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/{RuntimeWindowManager,WindowCommandPort,RuntimeSessionComponents}.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`
- Modify: `kadre/runtime/contracts/evidence.tsv`, `kadre/contracts/registry/contracts.tsv`, `kadre/{DESIGN,OPERATION-CONTRACTS,BACKEND-CAPABILITIES,TEST-STRATEGY}.md`

**Consumes:** Task 7 display handles/mode IDs and the existing borderless fullscreen barrier.

**Produces:** A process-wide logical lease protocol and portable window-request/update rules for exclusive mode.

```kotlin
internal interface ExclusiveDisplayPort {
    public suspend fun enter(request: ExclusiveDisplayRequest): ExclusiveDisplayCompletion
    public suspend fun restore(lease: ExclusiveDisplayLease): ExclusiveDisplayCompletion
    public suspend fun readback(nativeDisplayKey: Long): ExclusiveDisplayReadback
}
```

`ExclusiveDisplayRequest`, `ExclusiveDisplayLease`, `ExclusiveDisplayCompletion`,
and `ExclusiveDisplayReadback` are internal immutable runtime values. They use
only the internal display fingerprint/native key and never expose a KFFI handle.

- [ ] **Step 1: Write model-based exclusive traces.**

  Test capability domain `{Borderless}` while exclusive is absent/quarantined and `{Borderless, Exclusive}` only when fully admitted; initial exclusive request reserves before native peer publication; invalid/stale mode is rejected exactly; same owner/same mode is no-op; same owner mode change rolls back; cross-display reservation retains old lease until target commit; pre-capture cancellation outcomes; post-capture request coroutine cancellation ends internally as `RequesterDetached` with no published orphan; `await()` cancellation does not cancel; display loss publishes windowed effective state then an uncorrelated event; restore failure quarantines the display and excludes every session.

- [ ] **Step 2: Run focused tests and verify failure.**

  Run: `rtk ./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest`

  Expected: exclusive scenarios fail because no lease port exists.

- [ ] **Step 3: Implement serialised lease and cancellation authority.**

  Model only commands/readbacks in the runtime port. The coordinator owns no `DisplayId` allocation or native mode handle; it serializes process-wide display leases, retains a lease through target commit, and quarantines a lease until mode restoration, capture release, and fresh readback all reconcile. `Window.apply` waits for terminal native authority; it cannot return `Accepted` for an unfinished exclusive transition.

- [ ] **Step 4: Complete the O2 mappings.**

  Add every `WIN-008` scenario and sentinel from the allocation table and retain `WIN-008` planned. Add `runtime-geometry-straddling-readback` and `runtime-geometry-straddling-fabricated` to `WIN-007` mappings.

- [ ] **Step 5: Verify and commit.**

  Run: `rtk ./gradlew :kadre:runtime:check :kadre:contracts:validator:check`

  Commit: `feat(window): add exclusive display lease coordinator`.

### Task 12: AppKit exclusive fullscreen activation, contract activation, and phase closure

**Files:**
- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitExclusiveDisplayPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/{AppKitDisplayBroker,AppKitWindowRuntimeDriver,AppKitNativeWindowPort,KffiAppKitWindowPort,AppKitBackendProvider}.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/{AppKitBackendProviderTest,AppKitWindowRuntimeDriverTest,KffiAppKitWindowPortMacOsTest}.kt`
- Create: `kadre/backend/appkit/manual/{Phase9ExclusiveHarness.kt,phase-9-exclusive-fullscreen.md}`
- Modify: `kadre/{DESIGN,PUBLIC-API-CATALOG,OPERATION-CONTRACTS,BACKEND-CAPABILITIES,POLICY-PROFILES,KFFI-REQUIREMENTS,INTEROP-EXPORTS,APPKIT-IMPLEMENTATION-ROADMAP,TEST-STRATEGY}.md`
- Modify: `kadre/{runtime,backend/appkit}/contracts/evidence.tsv`, `kadre/contracts/registry/contracts.tsv`
- Delete before final review: `docs/superpowers/specs/2026-09-07-appkit-phases-8-9-design.md`, `docs/superpowers/plans/2026-09-07-appkit-phases-8-9.md`

**Consumes:** Tasks 5, 10, and 11; the published KFFI artifact.

**Produces:** The generated AppKit capture/mode-switch path, truthful per-kind fullscreen capability, active phase contracts, final docs, and manual exclusive protocol.

- [ ] **Step 1: Write O3 tests at the public AppKit boundary.**

  Verify the generated capture/release/mode APIs are used through the port; no missing bridge leaves `Borderless` unsupported; initial exclusive windows stay unpublished until terminal; a user cancellation after capture does not roll back the native transition; released/removed display yields the effective `Windowed` state before failure/event; a failed restoration quarantines; subsequent sessions cannot acquire it; and normal exit restores mode/releases capture. Include `APK-018` only as direct public observation of unsupported memory pressure.

- [ ] **Step 2: Run focused AppKit tests and prove they fail first.**

  Run: `rtk ./gradlew :kadre:backend:appkit:jvmTest --tests '*Exclusive*' --tests '*Fullscreen*'`

  Expected: FAIL until generated bridge ownership and AppKit port execution are present.

- [ ] **Step 3: Implement the AppKit port without leaking KFFI.**

  The port performs capture, native mode change, window configuration/readback, restoration, and release on its native owner thread. It reports immutable completion/readback stimuli only. On any restoration failure it reports cleanup failure and lets Task 11 preserve quarantine. It must not make a test-only mode change, expose native identifiers, or write any FFI code outside generated KFFI calls.

- [ ] **Step 4: Finalize manual protocol and all activation documentation.**

  The manual protocol covers real multi-display mode entry/exit, mode restoration after app close, display removal during exclusive, permission denial/recovery for raw input, and expected diagnostics. Update every normative document in the file list, including `INTEROP-EXPORTS.md`, to match the final public ABI. Mark `INP-002`, `APK-013`, `DSP-001`, `APK-014`, `WIN-007`, `APK-015`, `WIN-008`, `APK-016`, `RUN-007`, `APK-017`, `RUN-008`, and `APK-018` active only if every allocation-table mapping is executable.

- [ ] **Step 5: Run the complete gate against the published KFFI artifact.**

  Run:

  ```bash
  rtk ./gradlew --refresh-dependencies :kadre:check
  rtk ./gradlew :kadre:backend:appkit:appKitNativeTests
  rtk ./gradlew :kadre:contracts:validator:check
  ```

  Expected: PASS with no temporary KFFI repository, no skipped active evidence, and no retry-based success.

- [ ] **Step 6: Remove temporary planning artifacts, re-run the final gate, and commit the closure PR.**

  Remove only the two files named in this task after their implementation and docs are final. Run the three commands from Step 5 again. Commit:

  ```bash
  git add -A kadre docs/superpowers
  git commit -m "feat(appkit): complete raw input display and exclusive support"
  ```

## Verification matrix

| PR stack | Required commands before review | Native/manual boundary |
|---:|---|---|
| 1, 6 | `rtk ./gradlew :kadre:foundation:check :kadre:validateKotlinConsumer :kadre:validateJavaConsumer` | none |
| 2, 7, 11 | `rtk ./gradlew :kadre:runtime:check :kadre:contracts:validator:check` | deterministic O2 only |
| 3, 8 | Kextract contribution-guide generation/test commands | no Kadre behavior test |
| 4, 9 | KFFI guide generation/test commands plus local Kadre compilation | local snapshot only |
| 5, 10, 12 | `rtk ./gradlew :kadre:backend:appkit:check :kadre:contracts:validator:check` | O3 plus versioned manual protocol |
| 12 closure | `rtk ./gradlew --refresh-dependencies :kadre:check` and `rtk ./gradlew :kadre:backend:appkit:appKitNativeTests` | manual observation supplements, never replaces, O2/O3 |

## Self-review

### Spec coverage

- Raw member API, independent fan-out/budgets, truthful permission/revocation/source lifecycle, `DeviceCount`, and manual protocol are covered by Tasks 1–5.
- Complete display snapshots, opaque mode IDs, stable/retired identities, capability honesty, contrast, scale propagation, and unsupported memory pressure are covered by Tasks 6–10 and Task 12.
- Global physical geometry, contained/straddling readback, gaps, rounding, and inverse conversion are covered by Tasks 7 and 10.
- Initial/update exclusive operations, cancellation boundaries, leases, quarantine/reconciliation, display loss, and per-kind capability domains are covered by Tasks 11–12.
- Kextract-first generation, KFFI regeneration/local publication/published-artifact validation, docs, consumers, registry evidence, O2/O3 gates, and manual protocols are explicitly assigned.

### Placeholder scan

The plan contains no unbounded implementation step. The two generator audit tasks have a closed outcome: missing declarations are changed in Kextract with generation tests; already generated declarations are recorded and validated without creating an empty PR.

### Type consistency

The public signatures reproduce the approved spec: `SurfaceInput.requestRawInput(): KadreResult<RawInputAccess>`, `RawInputDeliveryPolicy`, `RawInputOverflowAction`, `DisplayModeId`, `DisplayMode.id`, `SurfaceContrast`, and `SurfaceEvent.AppearanceChanged`. Runtime/AppKit ports remain internal and Kotlin-only; no public API consumes a native/KFFI type.
