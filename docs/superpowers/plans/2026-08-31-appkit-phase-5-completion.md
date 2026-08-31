# AppKit Phase 5 Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the remaining meaningful AppKit window scope: effective transparency, brokered user attention, and a synchronous native window-move interaction, while keeping unsupported features explicit and leaving Kadre outside rendering and widgets.

**Architecture:** Keep the common public model capability-driven. `RuntimeWindowManager` owns validation, capability publication, effective state, and teardown; narrow ports carry the two process/native effects that do not belong in `WindowUpdate`. The AppKit driver maps those ports to AppKit on the main thread. `AppKitProcessBroker` owns process-wide attention requests. The existing desktop-handle lease remains the deliberately low-level composition escape hatch for `NSVisualEffectView`.

**Tech Stack:** Kotlin Multiplatform/JVM, kotlinx.coroutines, AppKit, Java FFM, KFFI generated Objective-C bindings, Gradle, macOS integration tests.

**Spec:** `docs/superpowers/specs/2026-08-31-appkit-phase-5-completion-design.md`

## Global constraints and delivery order

- Kadre owns windows, lifecycle and peripherals only. Do not add a renderer, widget API, or a typed blur/composition API.
- `blurBehind`, `contentProtection`, `Window.icon`, `BeginWindowResize`, armed interactions, outer position and exclusive fullscreen stay explicitly unsupported by AppKit in this phase. `NSWindowSharingNone` is legacy/unused according to current AppKit documentation and must not be presented as content protection.
- An unsupported non-default `WindowSpec` field rejects creation before a native peer exists. `Window.apply` remains non-transactional and reports rejected fields through `PartiallyApplied`.
- Never hand-edit generated KFFI bindings. If a required Objective-C declaration is genuinely absent, make and test the Kextract generator change first, regenerate KFFI from it, and only then consume the generated symbol in Kadre.
- The checked-in KFFI audit already finds generated APIs for `NSWindow.setOpaque`, `backgroundColor`/`setBackgroundColor`, `sharingType`/`setSharingType`, `NSApplication.requestUserAttention`/`cancelUserAttentionRequest`, and `NSWindow.performWindowDragWithEvent`. Therefore this plan starts with KFFI consumer proof, not an invented Kextract/KFFI change.
- If that audit becomes false against the final published snapshot, use the bounded contingency in Task 2; do not write a handwritten FFM selector in KFFI or Kadre.
- Work in reviewable stacked slices. Kextract/KFFI branches may remain stacked and KFFI may be published to Maven Local for Kadre integration; merge/publish remotely only after every Kadre slice is green.
- Remove this implementation-plan file before opening the final Kadre delivery PR, as agreed for completed Superpowers implementation plans. The accepted design document remains the durable rationale.

## Planned stack

| Slice | Branch | Base | Deliverable |
|---|---|---|---|
| A | `codex/appkit-phase5-runtime-contracts` | `origin/master` | generic capability, strict creation, desktop embedded opt-in |
| B | `feat/appkit-phase5-bindings` in Kextract, only if Task 2 proves a declaration absent | Kextract `master` | generator fix plus focused generator test |
| C | `feat/appkit-phase5-bindings` in KFFI, only if B exists | KFFI `master` + B revision | regenerated bindings and macOS proof; local publication |
| D | `codex/appkit-phase5-appearance` | A | AppKit transparency and content-protection port |
| E | `codex/appkit-phase5-attention` | D | brokered attention with embedded opt-in |
| F | `codex/appkit-phase5-system-move` | E | handler interaction runtime and AppKit native move |
| G | `codex/appkit-phase5-contracts-docs` | F | docs, manual harness, contract evidence and final gates |

`B` and `C` are explicitly conditional on an absent generated binding. They are not opened merely to make the stack look uniform. If the initial macOS compile proof passes, skip them and record that fact in the Kadre PR description.

---

## Task 1: Establish the generic runtime contract and embedded opt-in

**Files:**

- Modify: `kadre/platform/desktop/src/jvmMain/kotlin/org/graphiks/kadre/platform/desktop/DesktopHost.kt`
- Modify: `kadre/platform/desktop/src/jvmMain/kotlin/org/graphiks/kadre/platform/desktop/DesktopHostFacade.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/desktop/DesktopBackendProvider.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt`
- Modify: `kadre/platform/desktop/src/jvmTest/kotlin/org/graphiks/kadre/platform/desktop/DesktopHostTest.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`

**Interfaces:**

```kotlin
public data class DesktopHostOptions.Embedded(
    public val integration: DesktopIntegration,
    public val backend: DesktopBackend = DesktopBackend.Auto,
    public val allowUserAttention: Boolean = false,
) : DesktopHostOptions

public class DesktopEmbeddedRequest(
    public val parentScope: CoroutineScope,
    public val applicationFactory: KadreApplicationFactory,
    public val integration: DesktopIntegrationKind,
    public val policy: KadrePolicy,
    public val allowUserAttention: Boolean,
)

internal interface WindowAttentionPort : AutoCloseable {
    suspend fun request(windowId: WindowId, attention: WindowAttention): KadreResult<Unit>
    fun release(windowId: WindowId)
    override fun close()
}
```

`WindowAttentionPort` is an internal runtime seam. A null port means the structural capability is `Unsupported(RequestWindowAttention)`; a non-null port exposes the exact `Set<WindowAttention>` it accepts. `RuntimeWindowManager.requestAttention` must receive and forward the caller's `attention` argument rather than discard it.

- [ ] Write failing `DesktopHostTest` cases first: the default embedded request transports `false`; an explicit `allowUserAttention = true` transports `true`; standalone requests do not acquire this option.
- [ ] Write failing `RuntimeWindowManagerTest` cases for all generic semantics:
  - a configured attention port publishes `Supported({None, Informational, Critical}, Available)` and receives the window ID and requested level;
  - a null port publishes and returns `Unsupported(RequestWindowAttention)`;
  - a closed window returns `Closed(Window)` without touching the port;
  - `WindowProperty.Transparency` and `WindowProperty.ContentProtection` become supported only when present in `enabledWindowUpdateCapabilities`; blur and icon remain unsupported;
  - every non-default initial field whose matching capability is unsupported terminates the request as `Rejected(Unsupported(RequestWindow))` before `WindowCommandPort.requestOpen` is called. Cover blur, icon, outer position and exclusive fullscreen separately enough to prove the mapping, then use one table-driven test for the remaining feature-to-property mapping.
- [ ] Add `allowUserAttention` with default `false` to `DesktopHostOptions.Embedded`; thread it intact through `DesktopHostFacade.attach` into `DesktopEmbeddedRequest` without affecting provider selection or standalone behavior.
- [ ] Add `WindowAttentionPort` beside the existing window runtime ports. Make its lifecycle explicit: release a window when it leaves committed ownership and close the port once the manager is closed; both paths must be idempotent and must run outside the manager lock.
- [ ] Extend `RuntimeWindowManager` construction with `attentionPort: WindowAttentionPort?` and a constrained accepted-attention set. Derive `WindowCapabilities.attention`, transparency and content-protection capabilities from these configuration values rather than from hard-coded AppKit assumptions.
- [ ] Replace the current unconditional attention rejection with phase/capability validation and `attentionPort.request(window.id, attention)`. Convert a throwing port boundary to the existing typed platform failure/reporting route; do not manufacture window state or events for attention.
- [ ] Centralize strict initial-spec validation before reservation/`requestOpen`. It must inspect the requested values rather than the AppKit-effective copy, so an unsupported request cannot be silently clamped. Preserve the current request cancellation and resource-budget behavior for a valid spec.
- [ ] Run `./gradlew :kadre:platform:desktop:jvmTest :kadre:runtime:jvmTest` and fix only failures caused by the new contract.
- [ ] Commit slice A with a conventional Kadre commit, then open the first Kadre PR from `codex/appkit-phase5-runtime-contracts`.

## Task 2: Prove generated binding availability, with a bounded regeneration contingency

**Files:**

- Modify only if a declaration is absent: `/Volumes/Cache/kextract/src/main/kotlin/org/graphiks/kextract/kotlin/KotlinGenerator.kt` and the smallest applicable builder under `/Volumes/Cache/kextract/src/main/kotlin/org/graphiks/kextract/kotlin/builders/`
- Modify only if a declaration is absent: `/Volumes/Cache/kextract/src/test/kotlin/org/graphiks/kextract/ObjCGeneratorTest.kt` or `/Volumes/Cache/kextract/src/test/kotlin/org/graphiks/kextract/integration/ObjCGeneratorIntegrationTest.kt`
- Modify only if a declaration is absent: `/Users/chaos/.codex/worktrees/cf31/kffi/third_party/kextract` (submodule revision), generated Objective-C source output, and the smallest KFFI macOS test
- Add in Kadre: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitBindingAvailabilityMacOsTest.kt`

**Interfaces to prove:**

```kotlin
NSWindow.setOpaque(Boolean)
NSWindow.backgroundColor()
NSWindow.setBackgroundColor(MemorySegment)
NSWindow.sharingType()
NSWindow.setSharingType(NSWindowSharingType)
NSApplication.requestUserAttention(NSRequestUserAttentionType)
NSApplication.cancelUserAttentionRequest(Long)
NSWindow.performWindowDragWithEvent(MemorySegment)
```

- [ ] Add the Kadre macOS availability test before using these APIs. It must compile references to every listed generated method and execute only safe read/set/read checks on a temporary `NSWindow`; it must cancel any attention request it creates. It may skip when not running on macOS, but must not suppress linkage failures on macOS.
- [ ] Run `./gradlew :kadre:backend:appkit:jvmTest --tests '*KffiAppKitBindingAvailabilityMacOsTest*'` on this macOS ARM64 host. Record the exact KFFI Maven coordinate resolved by Gradle in the PR evidence.
- [ ] If the test compiles and executes, keep Kextract and KFFI unchanged. The passing availability test is the anti-regression proof and unblocks Tasks 3–6.
- [ ] If one symbol is absent or generated with an unusable signature, create Kextract branch `feat/appkit-phase5-bindings` from its clean `master`; first add a minimal Objective-C fixture and a focused assertion for the emitted signature/availability annotation.
- [ ] Implement the smallest generator correction that makes the fixture pass. Run the full Kextract test suite required by its repository instructions. Open, but do not merge, the Kextract PR.
- [ ] Create KFFI branch `feat/appkit-phase5-bindings` based on KFFI `master`, point its Kextract submodule at the Kextract PR revision, run `scripts/gen-kffi-objc.sh`, and review the generated diff as output only. Do not edit any generated Kotlin file by hand.
- [ ] Add a KFFI macOS test that calls the corrected generated declaration and run the KFFI contributor-required `./gradlew :kffi:jvmTest`, the Objective-C generation check, and the focused macOS test. Open the KFFI PR stacked on the Kextract branch.
- [ ] Publish that KFFI branch to Maven Local only for Kadre testing, refresh Kadre dependencies, and rerun the availability test. Do not merge or publish either upstream PR until Task 8.

## Task 3: Implement AppKit transparency and content-protection readback

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverFactory.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`

**Interfaces:**

```kotlin
internal data class AppKitWindowAppearanceTarget(
    val transparency: PropertyChange<Boolean>,
)

internal data class AppKitWindowAppearanceSnapshot(
    val transparency: Boolean,
)

internal data class AppKitWindowMutationTarget(
    val title: PropertyChange<String>,
    val geometry: AppKitWindowGeometryTarget,
    val chrome: AppKitWindowChromeTarget,
    val level: AppKitWindowLevelTarget,
    val appearance: AppKitWindowAppearanceTarget,
)

internal data class AppKitWindowMutationSnapshot(
    val title: String,
    val geometry: AppKitWindowGeometrySnapshot,
    val chrome: AppKitWindowChromeSnapshot,
    val level: WindowLevel,
    val appearance: AppKitWindowAppearanceSnapshot,
)
```

- [ ] Add deterministic driver tests before production changes:
  - a valid initial `transparent = true` reaches the native target before commit and the committed `WindowState` is native readback;
  - setting transparency succeeds only when readback agrees;
  - a divergent transparency readback rejects only `Transparency` and the other fields in the same update remain effective through `PartiallyApplied`;
  - a native setter/readback exception maps to the existing platform failure route without leaving a peer or mutation pending;
  - the AppKit provider exposes exactly the transparency capability; content protection, blur and icon remain unsupported.
- [ ] Extend the mutation target and snapshot with `appearance`; make unchanged transparency no-op at the port boundary and include its effective value in all snapshots used for commit, update, external observation, and failure recovery.
- [ ] Change `appKitEffectiveSpec` only for supported initial fields: preserve requested transparency when its capability is enabled. Do not use it to erase unsupported values; Task 1 must have rejected those before peer creation.
- [ ] Map `WindowUpdate.transparency` into the AppKit target and add its comparison to `rejectedMutationFields`. Maintain the existing property ordering and single native commit boundary.
- [ ] Complete the generic mutation plumbing for these already capability-advertised fields: retain both changes in `supportedMutationOnly`, resolve them in `candidateFor`, and include them in `mutationChanged`. Add a focused runtime regression test that proves enabled appearance fields reach the `WindowCommandPort`; this is shared contract plumbing, not an AppKit-specific fallback.
- [ ] In `KffiAppKitWindowPort`, execute the following on the AppKit main thread:
  - `true` transparency: `NSWindow.setOpaque(false)` then set a transparent `NSColor` background;
  - `false` transparency: `NSWindow.setOpaque(true)` then restore `NSColor.windowBackgroundColor()`;
  Read `isOpaque` after the setter and project it to the boolean. Do not add,
  remove, replace, wrap, or configure the content view.
- [ ] Extend `APPKIT_PUBLIC_WINDOW_UPDATE_CAPABILITIES` with `Transparency` only. Content protection remains unsupported because AppKit's legacy sharing type cannot make the common field effective.
- [ ] Extend the real macOS port test to read actual opacity after initial creation and after each update. It proves native state, not visible transparency or screenshot prevention.
- [ ] Run `./gradlew :kadre:backend:appkit:jvmTest :kadre:runtime:jvmTest --refresh-dependencies`; commit and open slice D stacked on A.

## Task 4: Broker AppKit user attention and enforce embedded host ownership

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeApplication.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitProcessBroker.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverFactory.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitProcessBrokerTest.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt`

**Interfaces:**

```kotlin
internal interface AppKitNativeApplication {
    fun requestUserAttention(attention: WindowAttention): Long
    fun cancelUserAttentionRequest(token: Long)
}

internal interface AppKitWindowAttentionPort : WindowAttentionPort
```

The broker owns `WindowId -> native attention token`; the runtime owns only window-phase and capability validation.

`WindowAttentionPort` is a public type in the `org.graphiks.kadre.internal.runtime`
backend SPI, like `WindowCommandPort`: AppKit is a distinct Gradle module and
cannot implement a Kotlin `internal` type. It is not a foundation/application
API and does not create a new public application entry point.

- [ ] Add broker tests first, using a recording native application:
  - informational/critical attention creates one token for the requesting window;
  - a second non-`None` request for the same window cancels the old token before obtaining the replacement;
  - `None` cancels that window only and succeeds when no token exists;
  - closing a window, releasing an embedded registration, releasing the standalone lease, and host termination release every token belonging to their owner exactly once;
  - a native exception becomes `PlatformFailure(AppKit, "user-attention", ...)` and no token is recorded;
  - registrations from separate embedded sessions cannot cancel one another's attention.
- [ ] Add provider/driver tests first: standalone exposes `{None, Informational, Critical}`; embedded default exposes `Unsupported(RequestWindowAttention)` and never invokes the broker; embedded with `allowUserAttention = true` exposes the same supported set and routes through the broker.
- [ ] Extend `AppKitNativeApplication` with the two narrow calls. `KffiAppKitNativeApplication` obtains the shared `NSApplication` only on the main thread, maps `Informational` and `Critical` to the generated `NSRequestUserAttentionType` values, and maps `None` to the broker cancellation path rather than inventing a native request.
- [ ] Add an `AppKitProcessBroker` attention service with its own lock-protected token map. Integrate its cleanup with existing standalone/embedded leases and `HostTerminated`; never retain a session or `Window` object in the process broker.
- [ ] Give each AppKit driver a session-scoped `WindowAttentionPort` backed by the broker. Its suspending request uses the existing AppKit command queue to marshal the broker/native call to the main thread and returns only after native admission or typed failure; `release(windowId)` schedules cleanup on that same queue. Release must run on peer close and driver/session closure, even if the native close was externally initiated.
- [ ] Propagate `DesktopEmbeddedRequest.allowUserAttention` through `AppKitBackendProvider` into the factory configuration. Attaching with the default option must be observationally identical to the current embedded host.
- [ ] Add a real macOS test that creates an attention request then cancels it. It verifies generated binding execution and cleanup only; it never asserts Dock bounce visibility.
- [ ] Run `./gradlew :kadre:backend:appkit:jvmTest :kadre:platform:desktop:jvmTest --refresh-dependencies`; commit and open slice E stacked on D.

## Task 5: Install the minimal common synchronous interaction runtime

**Files:**

- Modify: `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/surface/Surface.kt`
- Modify: `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/interaction/Interaction.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/MinimalWindowSurface.kt`
- Add: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeInteractionHandler.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowSurfaceTest.kt`
- Add: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeInteractionHandlerTest.kt`

**Interfaces:**

```kotlin
public interface HostSurface {
    @DelicateKadreApi
    public fun installInteractionHandler(
        handler: InteractionHandler,
    ): KadreResult<InteractionRegistration> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler))

    public suspend fun armInteraction(
        action: InteractionAction,
        options: InteractionArmOptions,
    ): KadreResult<ArmedInteraction> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.ArmInteraction))
}

internal fun RuntimeWindowSurface.dispatchSynchronousInteraction(
    event: InteractionEvent,
    supported: Set<InteractionKind>,
    invokeNative: (InteractionAction) -> KadreResult<Unit>,
)
```

Delete the two permanent-failure top-level extensions from `Interaction.kt`; the identically named `HostSurface` members preserve the Kotlin call form while allowing a backend implementation. No public API beyond the already specified interaction model is introduced.

- [ ] Write failing runtime tests first for the normative contract in `DESIGN.md` section 9.6:
  - exactly one handler can be active; a second registration returns the typed busy/interaction failure;
  - a valid pointer event calls the handler before ordinary input publication with the same preassigned stamp;
  - `BeginWindowMove` consumes the token once, invokes the native callback exactly once before handler return, and emits one committed outcome after the callback;
  - duplicate, retained-after-return, wrong-surface, unsupported, and closed requests get the documented typed failure and never call native code;
  - `InteractionRegistration.close()` stops future callbacks but does not roll back an already committed action;
  - handler exceptions are captured, reported through the existing session-failure handler, and do not permit an exception to cross a native callback boundary;
  - `armInteraction` remains unsupported in this phase.
- [ ] Move the foundation API from permanently failing extensions to default `HostSurface` members, retaining the same signatures and default failures for all unimplemented backends. Update catalog signatures and operation-contract wording only if the declaration form changes.
- [ ] Implement `RuntimeInteractionHandler` as a per-surface owner with a single active registration, bounded outcome flow using the existing window discrete-event policy, and a callback-confined `InteractionContext`/token. Do not invoke user code while a runtime monitor is held.
- [ ] Add a synchronous dispatch hook to `RuntimeWindowSurface`. It must allocate the event stamp before invoking the handler, serialize nested callbacks, execute at most one action, invalidate the token before returning, and then admit the ordinary input stimulus even on a rejected interaction.
- [ ] Advertise handler capability only when a backend injects a non-empty supported interaction set. The generic default stays `Unsupported(InstallInteractionHandler)` and `ArmInteraction` remains unsupported.
- [ ] Ensure surface detach and session shutdown close the registration, terminate outcomes consistently, and prevent later native callbacks from reaching application code.
- [ ] Run `./gradlew :kadre:foundation:jvmTest :kadre:runtime:jvmTest` before wiring AppKit.

## Task 6: Wire AppKit pointer-down to native system move

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverFactory.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeerTest.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt`

**Interfaces:**

```kotlin
internal class AppKitInputCallbacks(
    val input: (AppKitInput) -> Unit,
    val pointerDown: (
        input: AppKitInput.PointerButtonChanged,
        performWindowMove: () -> KadreResult<Unit>,
    ) -> Unit,
)

internal interface AppKitNativeWindowPort {
    fun beginWindowMove(
        window: AppKitNativeWindowOwner,
        nativeEvent: MemorySegment,
    ): KadreResult<Unit>
}
```

The native event is a private, callback-scoped KFFI value. It never becomes a public Kadre type, is never queued, and is never retained after the pointer-down callback returns.

- [ ] Add deterministic peer/driver tests first:
  - the pointer-down callback dispatches the interaction handler before converting/delivering ordinary `PointerButtonChanged` input;
  - an accepted `BeginWindowMove` calls the native move operation with that same callback event exactly once;
  - no handler, a rejected action, an exception, or a disabled capability still delivers ordinary input and never starts a move;
  - `BeginWindowResize` and every non-move interaction stay rejected before native code;
  - close/teardown revokes callbacks before native event ownership is released.
- [ ] Extend `AppKitInputCallbacks` with the synchronous pointer-down route. Keep the existing immutable `AppKitInput` mapping for regular input; do not overload it with a raw event address.
- [ ] In the KFFI observer/subclass callback, call the runtime pointer-down dispatcher while the original `NSEvent` is still valid. On an admitted action, call the already-generated `NSWindow.performWindowDragWithEvent(event)` on the AppKit owner thread. Catch and convert native exceptions to `KadreResult.Failure(PlatformFailure)`.
- [ ] Inject the surface interaction dispatcher from the AppKit driver/peer and configure `SurfaceCapabilities.handlerInteractions` to exactly `Supported({BeginWindowMove}, Available)`. Keep `armedInteractions` unsupported.
- [ ] Add a real macOS test that validates the method can be invoked from the generated binding without retaining the event. It must not try to prove an interactive drag under CI.
- [ ] Run `./gradlew :kadre:backend:appkit:jvmTest :kadre:runtime:jvmTest --refresh-dependencies`; commit and open slice F stacked on E.

## Task 7: Document the boundary and add non-blocking manual evidence

**Files:**

- Add: `kadre/backend/appkit/manual/Phase5AdvancedWindowHarness.kt`
- Add: `kadre/backend/appkit/manual/phase-5-advanced-window.md`
- Modify: `kadre/backend/appkit/build.gradle.kts`
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`
- Modify: `kadre/DESIGN.md`
- Modify: `kadre/PUBLIC-API-CATALOG.md`
- Modify: `kadre/OPERATION-CONTRACTS.md`
- Modify: `kadre/INTEROP-EXPORTS.md`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeerTest.kt`

- [ ] Reuse the active `APK-003` desktop-handle lease proof; do not add a duplicate test. Link the new composition guidance to its established guarantees: non-zero AppKit addresses only inside the admitted callback, close waiting for an admitted lease, and post-close access returning `Closed(Window)`.
- [ ] Add the advanced manual harness with commands for transparent/opaque, informational/critical/none attention, and a pointer-down window move. Reuse the repository's existing TSV metadata, `pass|fail|not-applicable`, and controlled-close conventions.
- [ ] Write the manual checklist to explicitly state:
  - transparency requires application-drawn alpha and is not proof of compositing by Kadre;
  - AppKit content protection remains unsupported: `NSWindowSharingNone` is a legacy sharing value, not an anti-capture mechanism;
  - attention visibility is host/user-policy dependent;
  - system move must be observed through a real pointer gesture;
  - a Kotlin AppKit application may install and own `NSVisualEffectView` through `withDesktopHandle` plus KFFI, with no Kadre ownership transfer.
- [ ] Update the roadmap to mark only the delivered Phase 5 capabilities supported. Keep blur, icon, resize, outer position, exclusive fullscreen and armed interactions in their explicit deferred/unsupported states.
- [ ] Align `DESIGN.md`, the public catalog and operation contracts with the exact semantics implemented in Tasks 1–6: initial rejection, partial updates/readback, standalone-vs-embedded attention, attention teardown, handler-before-input ordering, single-use token, and `withDesktopHandle` composition boundary.
- [ ] Reserve `WIN-006` for runtime appearance/strict-initial/attention semantics, `APK-011` for their public AppKit activation, `INT-001` for the common synchronous interaction runtime, and `APK-012` for AppKit native system move. Activate each row only after its named automatic runtime, driver and macOS evidence exists; keep manual checklist results outside the CI proof column.
- [ ] Run the manual harness only on a visible macOS session. If no such session is available, record `not-applicable`; do not block CI or mark the observation as passed.
- [ ] Commit and open slice G stacked on F.

## Task 8: Integrate the stack, publish dependencies only when needed, and finalise evidence

**Files:**

- Modify if Kextract/KFFI contingency was used: their Kextract revision/submodule pointer and generated KFFI output only through `scripts/gen-kffi-objc.sh`
- Modify when the KFFI contingency produced a snapshot: `gradle/libs.versions.toml`
- Delete before the final Kadre delivery PR: `docs/superpowers/plans/2026-08-31-appkit-phase-5-completion.md`

- [ ] Rebase each Kadre slice onto its declared predecessor, run `git diff --check`, and ensure no slice includes unrelated user-owned untracked material from `.superpowers/` or `kadre/implementation-plans/`.
- [ ] If Task 2 required Kextract/KFFI changes, merge Kextract first. Then update the KFFI branch to the merged Kextract `master`, regenerate `kffi-objc` from that revision, rerun the generation check and all required KFFI tests, and merge/publish the KFFI snapshot. Never merge KFFI against a temporary Kextract branch.
- [ ] Refresh Kadre dependencies against the final published KFFI snapshot with `--refresh-dependencies`; rerun the macOS binding availability and AppKit port tests to prove Kadre consumes the published artifact rather than Maven Local residue.
- [ ] Execute the targeted final gate:

```bash
./gradlew \
  :kadre:foundation:jvmTest \
  :kadre:runtime:jvmTest \
  :kadre:platform:desktop:jvmTest \
  :kadre:backend:appkit:jvmTest \
  --refresh-dependencies
```

- [ ] Execute the repository's full required build/check gate from a clean working tree. Capture Gradle task names and the resolved KFFI snapshot in the final PR evidence.
- [ ] Inspect the public API diff and the capability tables. Confirm no API claims a typed blur, icon, resize, renderer, widget, visual transparency guarantee, or anti-capture guarantee.
- [ ] Request an independent code review of the full stacked diff, resolve findings with focused regression tests, and rerun the affected gates.
- [ ] Delete this implementation plan before preparing the final Kadre delivery PR; retain the accepted design spec and update it only if implementation uncovers a contract-level discrepancy.
- [ ] Prepare PR descriptions in stack order with: scope, base PR, exact behavior enabled, excluded features, automated evidence, manual evidence status, and (if applicable) the Kextract → KFFI publication dependency. Do not merge a Kadre slice before its stated base and its required dependency snapshot are available.

## Final acceptance checklist

- [ ] AppKit advertises and effectively supports transparency through native readback; content protection remains explicit `Unsupported(UpdateWindow)`.
- [ ] Unsupported initial fields reject before native peer creation; unsupported update fields are explicit partial rejections.
- [ ] AppKit user attention is process-brokered, window-owned, released on all terminal paths, and opt-in for embedded hosts.
- [ ] A valid synchronous handler can start a native AppKit window move with the original pointer-down event while ordinary input delivery remains intact.
- [ ] KFFI usage is generated and tested; no handwritten generated binding exists. Kextract/KFFI changes are absent unless the availability proof required them.
- [ ] CI validates runtime and native state semantics; visual and human-gesture observations remain non-blocking manual evidence.
- [ ] The final Kadre delivery PR contains no completed Superpowers implementation plan file.
