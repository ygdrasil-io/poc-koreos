# AppKit phases 8 and 9 design

**Status:** approved design; implementation has not started.  
**Target:** JVM 25 / macOS, AppKit backend through generated KFFI bindings.  
**Scope:** phase 8 (raw input and permission) and phase 9 (display inventory,
system observations, outer geometry, and exclusive fullscreen).

## 1. Goal and non-goals

Kadre exposes raw global input only when macOS has granted the corresponding
Input Monitoring permission.  It exposes an honest, coherent display
inventory, outer-window geometry, and exclusive fullscreen when native
facilities permit them.  The implementation must preserve Kadre's
embeddable-first session model: macOS process resources are shared only by
specialized brokers, while public objects, identities, flows, and budgets are
owned by a Kadre session.

This work does not implement rendering, widgets, synthetic input injection,
device/gamepad management, or capture.  It does not claim a reliable memory
pressure signal where macOS has no reliable primitive available through KFFI.
It also does not turn a manual hardware or permission check into an automated
success.

## 2. Public API and policy changes

### 2.1 Raw input dispatch

`SurfaceInput.requestRawInput()` becomes an `@DelicateKadreApi` member of
`SurfaceInput` with this signature:

```kotlin
@DelicateKadreApi
public suspend fun requestRawInput(): KadreResult<RawInputAccess>
```

The common extension with that name is removed.  This is an intentional ABI
break during incubation: the same source call is now dynamically dispatched to
the owning backend rather than unconditionally returning `Unsupported`.

`RawInputAccess` retains its existing API.  It is an independent opt-in lease,
not an alias of ordinary input and not a focus grant.  It receives only raw
events through `RawInputAccess.events`; no raw event may be injected into
`SurfaceInput.events`, pointer state, focus state, or the normal responder
pipeline.  A `SurfaceInput` accepts zero or more accesses.  Every access is a
child owner of the requesting surface: detaching that surface, closing its
window, or tearing down its session closes all of its accesses normally.  The
same events may therefore be observed by several accesses on one surface and
by accesses owned by other surfaces or sessions, but no access outlives its
requesting surface.

### 2.2 Per-access delivery budget

`InputDeliveryPolicy` gains a dedicated field rather than reusing
`EventDeliveryPolicy` whose `CloseSource` and `FailSession` semantics would be
incorrect for a shared global source:

```kotlin
public data class RawInputDeliveryPolicy(
    public val capacity: Int,
    public val onOverflow: RawInputOverflowAction,
)

public enum class RawInputOverflowAction {
    DropOldestAndReport,
    DropLatestAndReport,
    CloseAccess,
}

public data class InputDeliveryPolicy(
    /* existing fields */,
    public val rawInput: RawInputDeliveryPolicy,
)
```

The capacity applies separately to every `RawInputAccess`.  Each dropped event
emits `KadreDiagnostic.EventLoss(count = 1, resource = RawInputAccess,
subsystem = Input)`.  `CloseAccess` additionally publishes `Closed` and ends
only that access's flow with `KadreException(SourceOverflow(RawInputAccess))`.
It never closes the process-wide event tap, another access, or an unrelated
session.  The existing collector limits still apply to each public flow.

`ResourceBudgetPolicy` gains `maxConcurrentRawInputAccesses`.  It is scoped to
one Kadre session and checked before a permission request or native-source
admission.  `KadreResourceKind` gains `RawInputAccess`; exhaustion returns
`Failure(ResourceLimitExceeded(RawInputAccess, limit))` from
`requestRawInput` and emits `KadreDiagnostic.ResourceLimitHit` with the same
resource and limit.  The direct failure domain in `OPERATION-CONTRACTS.md` is
expanded accordingly.  The built-in profiles use these values:

| Profile | access limit | raw capacity | overflow |
|---|---:|---:|---|
| Default | 16 | 256 | `DropOldestAndReport` |
| Realtime | 8 | 64 | `DropOldestAndReport` |
| Recording | 64 | 8192 | `CloseAccess` |

`POLICY-PROFILES.md`, policy validation, the public API catalog, and the ABI
consumer fixtures change in the same PR as these public types.

### 2.3 Capability and permission contract

On AppKit, `InputCapabilities.rawInput` is always structurally
`Capability.Supported(Unit, availability)` when the required native bridge is
available.  Its availability is:

| Native condition | Public availability | Request result |
|---|---|---|
| Input Monitoring already granted | `Available` | may create an access |
| Permission can be requested | `RequiresPermission(InputMonitoring)` | joins the shared request |
| Permission cannot currently be granted | `Unavailable(PermissionDenied(RawInput))` | `Failure(PermissionDenied(RawInput))` |
| Native bridge absent on this macOS/KFFI combination | `Capability.Unsupported(Unsupported(RawInputAccess))` | `Failure(Unsupported(RawInputAccess))` |
| Recoverable native source loss | `Unavailable(TemporarilyUnavailable(true))` | `Failure(TemporarilyUnavailable(true))` |

The direct failure domain of `requestRawInput` is
`Unsupported(RawInputAccess)`, `PermissionDenied(RawInput)`, the existing
interaction/busy/closed/temporary/platform variants, and the new
`ResourceLimitExceeded(RawInputAccess, limit)`.  It never directly returns
`PermissionDenied(InputMonitoring)`.  The latter is an observation of a live
access after permission has been revoked and is valid only in
`RawInputState.Suspended`.

All callers waiting on a permission decision share one native request.  A
caller cancelled before the access handoff is simply removed from that waiter
set; its cancellation does not dismiss the system prompt or affect other
waiters.  A cancelled caller after handoff owns an access exactly as any other
owner does.

## 3. Raw-input architecture and lifecycle

### 3.1 Components

`AppKitPermissionBroker` is process-wide and owns only the permission probe
and a single in-flight request.  `AppKitRawInputBroker` is process-wide and
owns at most one native global source.  Neither broker owns a
`KadreSession`, a `SurfaceInput`, a public collector, or an application.

The portable runtime owns a `RawInputCoordinator` per session.  It validates
the policy and budget, creates public `RawInputAccess` instances, serializes
their states, enforces their independent queues, and disconnects them during
session teardown.  Its native port exchanges immutable raw-event stimuli and
permission/source transitions; it never exposes AppKit or KFFI types.

The broker keeps a registration for each active access.  A single raw native
event is copied once at the native boundary and fanned out to every registration
still active at dispatch time.  Registration order has no observable priority.
Each access has an independent queue and can lose or close only its own event
stream according to its `RawInputDeliveryPolicy`.  The CoreGraphics delta
fields are integer movement counts with no documented logical- or
backing-pixel scale.  AppKit therefore publishes them unchanged as
`RawInputUnit.DeviceCount`, never as either pixel unit.  The event-tap bridge
does not establish a stable physical-device identity, so every AppKit
`RawInputEvent` has `deviceId = null`.

### 3.2 Native permission and source contract

The AppKit port uses `CGPreflightListenEventAccess` to observe TCC state and
`CGRequestListenEventAccess` for the single shared request.  It creates the
global source only with `CGEventTapCreate` at `kCGSessionEventTap`, head
placement, and `kCGEventTapOptionListenOnly`.  Its mask is restricted to mouse
movement and drag event kinds from which `kCGMouseEventDeltaX` and
`kCGMouseEventDeltaY` are read.  The callback copies only the values required
for `RawInputEvent` and returns the original native event unchanged.  It does
not observe keyboard data, synthesize input, suppress input, or use an
Accessibility-only monitor as a fallback.

The generated KFFI bridge owns the event-tap, its run-loop source, and their
closeable callback owner.  `tapDisabledByTimeout` is a source interruption,
not proof of a TCC revocation: the broker publishes a temporary suspension,
re-enables the tap on its owner thread, and returns accesses to `Active` only
after that succeeds.  `tapDisabledByUserInput` or a source creation failure
causes a new preflight.  A false preflight produces the permission-revoked
sequence below; a true preflight produces `Suspended(TemporarilyUnavailable(true))`
and a fresh source attempt.

macOS does not provide Kadre with a fabricated instantaneous revocation event.
Kadre therefore promises suspension on an *observed* revocation, detected by
the source-loss re-probe, a host activation re-probe, or a new access request;
it does not claim to know a TCC change before macOS exposes it.  A discovered
revocation emits `KadreDiagnostic.PermissionRevoked(InputMonitoring)` once per
affected session transition.

### 3.3 State transitions and teardown

The normal path is:

```text
requestRawInput
  -> runtime budget/admission
  -> shared permission decision if needed
  -> broker creates or reuses its one native source
  -> runtime creates RawInputAccess(Active)
```

Closing an access publishes `Closed`, completes its event flow, unregisters
only that access, and is idempotent.  Closing the last registration stops and
releases the native source.  A later request may create a new source.

When the broker observes a permission revocation or a recoverable loss, the
sequence is mandatory:

1. publish the new `InputCapabilities.rawInput` availability to affected
   session inputs;
2. publish `RawInputState.Suspended(reason)` for every live access;
3. stop the native source;
4. retain the accesses so that a later successful permission/source recovery
   can return them to `Active` and attach them to a new source.

For a non-recoverable source failure, each access instead publishes `Closed`
and completes with the exact allowed failure.  Surface detach and normal
window/session closure publish `Closed` then complete normally; they are not
reported as source failure.  No native callback may begin after its
registration or broker owner has been revoked.  Session teardown first closes
admission, then all session registrations, and only then allows the broker to
release the global source if no other session owns it.

## 4. Display inventory and system observations

### 4.1 Snapshot model

`AppKitDisplayBroker` owns the macOS display observation and produces complete
immutable native snapshots.  A session-local `DisplayCoordinator` projects one
snapshot into `DisplayManagerState`, allocates opaque `DisplayId` values, owns
the public `Display` handles, revisions, events, and teardown.  Native display
identifiers never appear in public APIs or logs.

`DisplayMode` gains an opaque `DisplayModeId` as its first property:

```kotlin
public class DisplayModeId internal constructor(private val value: Long) {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}

public data class DisplayMode(
    public val id: DisplayModeId,
    public val physicalSize: PhysicalSize,
    public val refreshRateHz: Double?,
    public val bitDepth: Int?,
)
```

The coordinator derives a full native fingerprint for each mode (I/O mode ID,
I/O flags, logical and pixel dimensions, refresh rate, and pixel encoding),
retains the matching native mode only inside the broker, and keeps the same
`DisplayModeId` for an unchanged fingerprint throughout the connection of its
`DisplayId`.  It allocates an ID only for an added fingerprint and retires an
ID only when that mode disappears or the display disconnects.  The ID is
unforgeable and never reused.  Two native modes with the same public
dimensions, refresh rate, and depth therefore remain distinct public values.
`FullscreenMode.Exclusive` may only consume a mode obtained from the current
`DisplayState.modes` of its matching display; it cannot guess or recreate a
native mode from scalar fields.

`DisplayInventory.Enumerated` is published only when all values are known from
the same native snapshot: primary display, connected displays, physical
bounds, work area when macOS supplies it, scale factor, current mode when
known, and the complete mode list when mode enumeration is available.  A
missing optional platform value remains `null`; a partially assembled list is
never advertised as `Enumerated`.

An initial or recovered snapshot publishes the `DisplayManagerState` before
any associated `Added`/`Changed` event.  A removal uses this fixed order:

1. the existing display handle receives a final `DisplayState` with
   `connection = Disconnected`;
2. the manager publishes the new inventory without that handle and increments
   its revision;
3. the manager emits `DisplayEvent.Removed` containing that terminal state and
   the new manager revision.

The retired public ID is never reused.  Reconnection of the same physical
screen creates a new public `DisplayId` and a new public handle.

### 4.2 Capability and permission semantics

`DisplayCapabilities.enumeration` is exactly
`Capability.Supported(Unit, Available)` only when the coordinator can maintain
an honest inventory.  A permission-gated implementation uses
`Capability.Supported(Unit, RequiresPermission(DisplayEnumeration))`; a
structurally absent one uses
`Capability.Unsupported(Unsupported(DisplayAccess))`; and a durable native
failure uses `Capability.Supported(Unit, Unavailable(failure))`.  A request
that needs permission follows the existing `DisplayManager.requestAccess`
contract: denial is returned as
`Success(DisplayManagerState(inventory = PermissionDenied(...)))`, never as a
direct `PermissionDenied` failure.  A durable non-permission failure first
publishes `DisplayInventory.Unavailable` with the same allowed failure.

If display modes cannot be read, the display inventory is not published as a
claim of complete mode support.  The capability is made unavailable and
exclusive fullscreen remains unavailable; Kadre does not invent a mode list
from `NSScreen` dimensions.

### 4.3 Surface appearance, scale, and memory pressure

Contrast is public rather than silently discarded.  `SurfaceState` gains
`contrast: SurfaceContrast`, `SurfaceContrast` has the closed values
`Standard`, `Increased`, and `Unknown`, and `SurfaceEvent.ThemeChanged` is
renamed `AppearanceChanged`.  The new event means a change to the atomic
`(theme, contrast)` pair; all official backends and consumer fixtures are
updated in the same intentional incubation break.

Appearance notifications and native surface readback feed the existing surface
stimulus pipeline.  On AppKit the reduction is fixed: Aqua/light ->
`(Light, Standard)`; Dark Aqua -> `(Dark, Standard)`; accessibility high
contrast Aqua -> `(Light, Increased)`; accessibility high contrast Dark Aqua
-> `(Dark, Increased)`; any unrecognized native appearance ->
`(Unknown, Unknown)`.  The state pair is published before one
`AppearanceChanged` event.  No revision or event is published if a native
notification reduces to the already published pair.  A display-scale change
updates every affected `SurfaceState` through its normal state-then-event
ordering.

`LifecycleCapabilities.memoryPressure` remains `Unsupported` unless Kadre can
both own a dependable native source and exercise it through a non-synthetic O3
proof.  Its callback, when that proof exists, becomes the existing
`HostSignal.MemoryPressure` stimulus and cannot be invoked by a test-only
public API.  A manual observation alone is insufficient to turn the capability
on; it complements but never replaces the proof gate.  Until then, O2 and O3
assert the honest unsupported capability.  `RUN-008` alone uses the
deterministic unsupported port to assert that it emits no memory-pressure
stimulus; `APK-018` observes only the public AppKit `Unsupported` capability
and never attempts to prove silence by waiting.

## 5. Outer geometry and exclusive fullscreen

### 5.1 Coordinate system

`WindowSpec.outerPosition`, `WindowUpdate.outerPosition`, and
`WindowState.outerBounds` use global physical coordinates in the exact space
represented by `CGDisplayBounds`: the origin is the upper-left corner of the
main display, coordinates may be negative, and the unit is one backing pixel.
`DisplayState.bounds` is copied from that same space.  The backend converts
AppKit's lower-left screen-point frame at its port boundary and never exposes
an AppKit point.  Consumers can therefore place a window relative to a display
by using that display's public physical bounds; scale is not silently applied a
second time.

For readback, `outerBounds` is non-null only when the full `NSWindow.frame`
lies in exactly one `NSScreen.frame`.  The backend uses that screen's native
backing conversion (`NSScreen.convertRectToBacking`) rather than multiplying
by `backingScaleFactor`.  It converts both `NSWindow.frame` and
`NSScreen.frame`, subtracts the latter backing origin from the former to form
a display-local backing rect, then offsets that local rect with the matching
`CGDisplayBounds` origin and inverts y against that display's physical height.
This normalization is required because `convertRectToBacking` preserves an
off-main-screen backing origin; adding the CoreGraphics origin without it
would double-offset a secondary display.  Origins use
`PixelRounding.NearestTiesToEven` and sizes use `PixelRounding.Ceil` before
constructing `PhysicalRect`.
`outerPosition` denotes this physical rectangle's top-left: its target display
is the display whose half-open `DisplayState.bounds` interval
`[left, right) × [top, bottom)` contains that point; a point in a gap is
`InvalidRequest("outerPosition")`.  The inverse first removes the
`CGDisplayBounds` origin, reverses the y axis within that display, restores
the backing origin of `NSScreen.convertRectToBacking(NSScreen.frame)`, then
uses `NSScreen.convertRectFromBacking` to supply `NSWindow.setFrameOrigin`
with the matching lower-left point.

A window spanning two screens, including screens at different scales, publishes
`outerBounds = null`; Kadre does not fabricate a piecewise physical rectangle
that `PhysicalRect` cannot represent.  When the frame becomes wholly contained
again, a fresh native conversion republishes its non-null bounds through the
ordinary geometry state/event even if the user did not issue a move.  This
contained/straddling transition, HiDPI-to-standard migration, negative
coordinates, gap rejection, and the no-double-scale rule are required
scenarios of `WIN-007` and `APK-015`.

Initial placement and mutation use native readback.  `Window.apply` succeeds
only after the effective outer bounds were read back and published by the
ordinary window state/event reducer.  Externally initiated moves use the same
readback route and may not fabricate an operation ID.

### 5.2 Exclusive display lease

`FullscreenMode.Exclusive(displayId, mode)` is enabled only if the selected
display is currently connected, that exact `DisplayModeId` belongs to its
current public mode list, and a session-local projection can obtain the
corresponding process-wide display lease.  There is one exclusive lease per
native display.  A mode owned by another display is `InvalidRequest("fullscreen")`.
An unknown/disconnected display, a mode that has become stale, or an already
held lease rejects only the `Fullscreen` field as
`TemporarilyUnavailable(retryable = true)` in
`WindowUpdateOutcome.PartiallyApplied`; it never preempts or transfers
ownership.  Structural absence of the bridge similarly rejects that field with
`Unsupported(UpdateWindow)`.  These are field outcomes rather than outer
failures because a combined `WindowUpdate` may still apply its other fields.

`WindowCapabilities.fullscreen` is
`Supported({Borderless}, Available)` whenever only the already delivered
borderless path is admitted, including an unavailable display inventory, a
quarantined exclusive lease, or a missing exclusive bridge.  It becomes
`Supported({Borderless, Exclusive}, Available)` only once the exact display
inventory, generated bridge, and exclusive lease protocol are active.
`FeatureAvailability.Unavailable` is reserved for a failure that affects both
kinds; an exclusive-only interruption never disables `Borderless` or claims
that `Exclusive` remains admissible.

`WindowSpec(fullscreen = Exclusive(...))` is supported by the same transaction
as `Window.apply`: the request reserves the target lease after request
admission, creates an unpublished native peer, then publishes its window only
after the native exclusive transition has reached a terminal state.  The three
cancellation mechanisms have distinct authority:

| Boundary | cancellation of `requestWindow` before handoff | `WindowRequest.cancel()` | cancellation of `WindowRequest.await()` |
|---|---|---|---|
| before reservation or unpublished-peer creation | closes the unhanded request; no handle escapes | `CancelledBeforeCommit`, release reservation, outcome `Cancelled` | cancels only its waiter |
| reservation/unpublished peer, before `CGDisplayCapture` | closes the unhanded request and releases both resources | returns `CancelledBeforeCommit` if native cancellation is already acknowledged; otherwise `CancellationRequested`; the serialized native race then terminalizes the request as `Cancelled`, `OpenedHere`, or `Rejected` with the exact allowed failure, and later calls return `AlreadyTerminated` | cancels only its waiter |
| after `CGDisplayCapture` | runtime auto-closes its provisional result before publication and terminalizes the internal request as `RequesterDetached`; no handle escapes | `TooLate`; it does not roll back a committed native transition | cancels only its waiter |

After handoff, cancellation of the original `requestWindow` coroutine is no
longer possible; the caller owns the `WindowRequest`.  `WindowRequest.close()`
uses the same pre-capture cancellation path, but after capture it terminalizes
only its handle as `RequesterDetached`; the resulting window remains owned by
the session.  `CancellationRequested` never predicts the terminal outcome: a
native commit that wins the race completes normally as `OpenedHere`; only an
acknowledged native cancellation produces `Cancelled`; a concurrent native
failure produces its normal `Rejected` outcome.  A failure before commit is
`WindowRequestOutcome.Rejected` with
the exact allowed failure and closes the unpublished peer.  When exclusive
support is structurally unavailable, the request is instead immediately
`Rejected(Unsupported(RequestWindow))`; an unavailable/stale target uses its
exact temporary or invalid `WindowRequestOutcome.Rejected` failure.

The AppKit port performs the exclusive sequence as one operation: acquire
lease, capture the display, change mode, configure/read back the window, then
publish its terminal outcome.  `Window.apply` waits for that completion; it
does not return `Accepted` merely because a native transition began.

Applying the same exclusive `(displayId, DisplayModeId)` from its current
owner is a no-op: it has no native call, revision, or event.  A same-owner mode
change keeps the lease and restores the previous mode if the new native mode
cannot be committed.  A move to another display reserves the target lease
first, then retains the previous lease until target capture and mode commit
succeed.  If the target commit fails, Kadre restores the old display/mode and
keeps the old lease.  If that restoration fails, the affected broker lease is
quarantined rather than made acquirable: Kadre independently attempts mode
restoration, display-capture release, and a fresh native mode readback.  Only a
confirmed release plus reconciliation to the recorded pre-transition mode
returns the lease to the acquirable pool.  Otherwise the display remains
exclusive-unavailable, every window reports the borderless-only capability,
and the cleanup failure is emitted; another session cannot acquire a display
whose native state Kadre has not reconciled.

Every exit path restores the captured mode and releases the lease: explicit
return to `Windowed`, another successful fullscreen change, window close,
display removal, source failure, session teardown, or a committed operation
failure.  When the exclusive display disappears, the broker retires its handle
in the display order defined above, releases the lease best-effort, reads back
the surviving AppKit window, and publishes `fullscreen = Windowed` with its
effective outer bounds (or `outerBounds = null` if no readback is possible)
before an uncorrelated `WindowEvent.PropertiesChanged`.  A pending operation
then completes with `TemporarilyUnavailable(true)`.  If restoration itself
fails, the lease follows the quarantine and reconciliation protocol above; the
runtime publishes the effective state first and returns/reports the exact
failure through the existing committed-operation and cleanup-diagnostic rules.
It never reports an `Applied` result for an un-restored display.

## 6. Kextract and KFFI boundary

Kadre contains no handwritten FFI declaration, generated-binding patch, raw
memory segment, native callback, or local FFI wrapper.  The first implementation
task audits the generated surface for the needed primitives:

- `CGPreflightListenEventAccess`, `CGRequestListenEventAccess`, a listen-only
  `CGEventTapCreate`, tap enablement, run-loop attachment, invalidation, tap
  disabled event kinds, and mouse-delta field extraction;
- `NSScreen` snapshot data and a native display reconfiguration callback;
- display mode enumeration including every discriminator retained by
  `DisplayModeId`, capture/release, and mode setting for exclusive fullscreen;
- a real memory-pressure observation primitive only when it can be safely
  owned *and* exercised by a non-synthetic O3 scenario.

Any missing primitive is implemented first in Kextract with its own tests.  A
stacked KFFI branch then consumes that Kextract branch, regenerates bindings,
and proves the generated surface.  Kadre consumes a locally published KFFI
artifact only for the dependent stacked PRs.  After Kextract and KFFI are
merged and published, the Kadre dependency is moved to their published master
artifact and all dependent tests are rerun.  A missing primitive leaves the
Kadre capability unavailable; it is never replaced by handwritten binding
code.

## 7. Contracts and proof strategy

The implementation reserves these contracts.  Their registry entries become
`active` only in the final activation PR that contains every named scenario,
sentinel, contract-evidence mapping, and mandatory CI gate.

| Contract | Oracle | Subject |
|---|---|---|
| `INP-002` | O2 | portable raw-input admission, budget failure, same-surface/session fan-out, per-access overflow, surface detach, shared permission waiter, suspension/recovery, and teardown |
| `APK-013` | O3 | AppKit Listen Event permission/source bridge, listen-only unchanged callback, no ordinary-input injection, owner revocation, and public capability activation |
| `DSP-001` | O2 | complete snapshot projection, opaque mode identity, revision/event ordering, retirement/reconnection identity, and affected-surface scale propagation |
| `APK-014` | O3 | `NSScreen`/CoreGraphics snapshot and reconfiguration bridge, capability readback, and callback teardown |
| `WIN-007` | O2 | global physical outer geometry, readback ordering, and invalid/stale/closed boundaries |
| `APK-015` | O3 | AppKit outer-frame conversion, public readback, external move, and teardown |
| `WIN-008` | O2 | display lease arbitration, initial exclusive request, owner no-op/mode/display change, cancellation after capture, rollback, display loss, and teardown restoration |
| `APK-016` | O3 | AppKit exclusive capability activation, generated native bridge ownership, and non-disruptive readback path |
| `RUN-007` | O2 | atomic theme/contrast reduction, state-before-appearance-event ordering, and unchanged-pair suppression |
| `APK-017` | O3 | AppKit effective-appearance bridge, public appearance readback, and callback teardown |
| `RUN-008` | O2 | honest memory-pressure availability, signal ordering, and no-signal unsupported path |
| `APK-018` | O3 | AppKit's public unsupported memory-pressure capability |

O2 uses deterministic native ports and independent trace oracles; it does not
assert implementation-private calls.  O3 crosses the public Kadre API into a
real AppKit/KFFI boundary and observes state, events, capabilities, outcomes,
and teardown through the public contract.  `APK-018` has exactly one O3
observation: the attached AppKit runtime exposes the public
`Unsupported` memory-pressure capability.  It makes no timed assertion about
the absence of a callback; `RUN-008` covers the deterministic unsupported
runtime behavior.

An automated runner cannot reliably grant Input Monitoring permission, connect
another display, alter a physical display mode, or induce memory pressure.
The CI therefore verifies the safe native bridge, denied/unavailable state,
and deterministic runtime behavior, but never declares those physical effects
tested when they did not occur.  Versioned manual protocols cover permission
grant/revocation, multiple displays and HiDPI migration, exclusive entry/exit
and restoration, and genuine memory-pressure observation.  They supplement,
but do not replace, active O2/O3 contracts.  A future positive memory-pressure
bridge receives a new planned contract only after a non-synthetic O3 scenario
exists; it is outside this phase's activation.

## 8. Stacked delivery order

The work is deliberately split into independently reviewable, stacked PRs:

1. public raw-input member, raw per-access policy/budget, contract registry,
   and deterministic API/policy tests;
2. portable raw coordinator and its O2 evidence;
3. Kextract and generated KFFI raw-input/permission support when the audit
   finds it missing;
4. AppKit raw broker, O3 evidence, capability activation, and manual protocol;
5. portable display coordinator, coherent inventory, system stimuli, and O2
   evidence;
6. Kextract and generated KFFI display/mode/appearance support when missing;
7. AppKit display broker, surface scale/appearance routing, outer geometry,
   O3 evidence, and manual display protocol;
8. portable exclusive lease/rollback coordinator with O2 evidence;
9. AppKit exclusive activation, O3 evidence, manual restoration protocol, and
   final capability/contract activation; memory pressure remains explicitly
   unsupported and is proved as such by `RUN-008`/`APK-018`.

The Kextract/KFFI PRs may be stacked and KFFI may be published locally to
unblock Kadre validation.  No downstream Kadre PR is merged with a temporary
artifact coordinate.  Public capability activation is deferred until all
dependent generated bindings, O2/O3 evidence, contract mappings, and docs are
present.

## 9. Documentation changes required by activation

The activating PRs update `DESIGN.md`, `PUBLIC-API-CATALOG.md`,
`INTEROP-EXPORTS.md`,
`OPERATION-CONTRACTS.md`, `BACKEND-CAPABILITIES.md`, `POLICY-PROFILES.md`,
`KFFI-REQUIREMENTS.md`, the AppKit roadmap, `TEST-STRATEGY.md` mappings, the
contract registry, fake-host documentation/fixtures, and the AppKit manual
protocols.  Completed temporary implementation plans and superpowers design
artifacts are removed before their implementation PRs are ready for final
review, following the repository convention.
