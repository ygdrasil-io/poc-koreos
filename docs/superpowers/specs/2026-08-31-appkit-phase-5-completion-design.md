# AppKit phase 5 completion design

## Status and purpose

This document closes the remaining AppKit scope of phase 5 after the merged
geometry, title, chrome, level, and correlated borderless-fullscreen slices.
It activates the remaining AppKit features that are meaningful within Kadre's
window-and-device boundary, without turning Kadre into a renderer or a widget
toolkit.

The public common model remains capability-driven. A field or interaction being
present in the common API does not promise that every backend implements it.
AppKit must publish an honest subset and must never silently accept a request
that it cannot make effective.

## Scope

### Supported by AppKit in this phase

- `WindowUpdate.transparency` and the equivalent initial `WindowSpec` value;
- `WindowUpdate.contentProtection` and the equivalent initial `WindowSpec`
  value;
- `Window.requestAttention` in standalone sessions;
- `Window.requestAttention` in embedded sessions only when the host has opted
  in explicitly;
- `InteractionAction.BeginWindowMove` from an AppKit native pointer-down
  callback.

### Explicitly unsupported by AppKit

- `blurBehind`: implementing it would require Kadre to install and own an
  `NSVisualEffectView` in application content;
- `Window.icon`: AppKit's icon is an application/Dock property, not a window
  property;
- `InteractionAction.BeginWindowResize`: no verified public AppKit path is
  advertised for beginning a resize at a requested edge;
- `armInteraction`: this slice implements only the synchronous handler path
  needed for a native AppKit move.

`outerPosition` and exclusive fullscreen remain outside phase 5 as already
recorded by the AppKit roadmap.

## Public contract

No window-feature API is added. The existing public fields and capabilities are
the contract.

`DesktopHostOptions.Embedded` gains:

```kotlin
public val allowUserAttention: Boolean = false
```

The default preserves host ownership of process-wide AppKit behavior. Standalone
AppKit exposes attention. Embedded AppKit exposes it only when this option is
true. The option is ignored by a backend that cannot implement AppKit attention
and does not cause a fallback or a global side effect.

For a live AppKit `Window`, capabilities are:

| Capability | AppKit value |
|---|---|
| `transparency` | `Supported(Unit, Available)` |
| `contentProtection` | `Supported(Unit, Available)` |
| `blurBehind` | `Unsupported(UpdateWindow)` |
| `icon` | `Unsupported(UpdateWindow)` |
| `attention` standalone | `Supported({None, Informational, Critical}, Available)` |
| `attention` embedded without opt-in | `Unsupported(RequestWindowAttention)` |
| `handlerInteractions` | `Supported({BeginWindowMove}, Available)` |
| `armedInteractions` | `Unsupported(ArmInteraction)` |

The interaction capability is a surface capability. An action outside its
advertised set is rejected before the native call.

## Creation and update semantics

Every AppKit-supported initial property is applied and read back while the peer
is prepared, before that peer is committed to `WindowManagerState`.

A non-default initial value for an unsupported AppKit property makes the
`WindowRequest` terminal with `WindowRequestOutcome.Rejected(Unsupported)`; no
native peer is created. This includes blur, icon, requested outer position, and
exclusive fullscreen. A creation request never succeeds with a state that
differs silently from its `WindowSpec`.

For `Window.apply`, Kadre retains its existing non-transactional rule. A batch
may apply the supported fields and report unsupported or native-failed fields in
`PartiallyApplied.rejected`. Each applied field originates from a native
readback; the requested value is not used as an effective snapshot.

### Transparency

`transparent = true` sets the native window to non-opaque with a transparent
background. `transparent = false` sets it opaque and restores
`NSColor.windowBackgroundColor`. Kadre never inserts, replaces, wraps, or
configures the application content view. Consequently, an application must draw
alpha in its own renderer to obtain a visually transparent result.

The native mutation target and snapshot gain a transparency member. The AppKit
port performs set-and-readback on the host thread. A readback failure or a value
that cannot be made effective rejects only `WindowProperty.Transparency`.

### Content protection

`contentProtection = true` maps to `NSWindowSharingNone`; `false` maps to
`NSWindowSharingReadWrite`. The AppKit port reads the sharing type after every
write and projects the effective boolean into `WindowState`.

This feature limits the sharing of window content with other processes. It is
not documented or represented as a guarantee against every screenshot,
recording, camera, privileged process, or physical observation. Kadre owns this
specific property for the windows it creates.

## Attention broker

Attention is not a window-state property. It is a process-wide AppKit operation
implemented by an extension of `AppKitProcessBroker` and reached through a
narrow runtime attention port.

The broker tracks at most one native attention request for each `WindowId`:

- an informational or critical request replaces and cancels the request already
  owned by the same window before registering the new one;
- `None` cancels only that window's current request and succeeds if no request
  exists;
- window close and session teardown release the window's request;
- an AppKit failure maps to `KadreFailure.PlatformFailure` and creates no
  durable window state;
- AppKit's own activation and presentation behavior remains authoritative.

The runtime validates the window phase and capability before calling the port.
It keeps `Success(Unit)` as admission only: the OS need not visibly notify the
user for that result to be correct.

The embedded opt-in is propagated from `DesktopHostOptions.Embedded`, through
the desktop facade and backend provider, to the driver/runtime capability and
attention port. It never changes process state merely by attaching a session.

## Native move interaction

The phase introduces the smallest common runtime implementation of the existing
handler interaction contract.

During an AppKit pointer-down callback, the peer asks the runtime whether a
handler is installed for the surface. If present, the runtime invokes it before
the ordinary pointer input is delivered. Its `InteractionContext` carries a
single-use token valid only for this callback. The only advertised action is
`BeginWindowMove`.

Consuming that token causes the AppKit peer to invoke the native system drag
with the same native event. The terminal `InteractionActionOutcome` is then
published. Handler return is mandatory before normal input delivery continues;
the handler cannot suspend or retain the token. Invalid, duplicated, expired,
wrong-surface, and unavailable requests receive their existing typed failures.
An interaction failure does not suppress the ordinary input event. A handler
exception follows the existing session-failure policy.

## Native composition escape hatch

Kadre does not implement blur, but a Kotlin AppKit consumer can compose it
itself. `Window.withDesktopHandle` already supplies the `NSWindow` and content
view addresses on the host thread inside a bounded lifetime lease. A consumer
with KFFI may construct an `NSVisualEffectView` and install it in that callback.

Kadre owns neither the installed view nor its renderer configuration. The
consumer must respect the callback lifetime rule and use ordinary AppKit view
ownership after the callback returns. The phase documents this recipe and adds
a lease-focused test; it does not add a typed AppKit handle API to Kadre.

## Kextract and KFFI

Before Kadre consumes an absent AppKit declaration, Kextract receives the
generator change and a focused fixture/test. KFFI then points to that Kextract
branch, regenerates its bindings, and proves the generated symbols by compiling
and executing focused tests. No generated KFFI file is edited manually.

The Kextract and KFFI pull requests may be stacked. KFFI may be published to a
local Maven repository for Kadre consumer tests while the stack is open. The
remote Kextract/KFFI merge and snapshot publication happen only after the phase
is complete; Kadre is then refreshed against the published snapshot before its
final gate.

Expected bindings include only what the selected native mapping needs: window
opacity/background and sharing-type accessors, AppKit attention request/cancel,
and the native window drag entry point. The binding audit determines the exact
Kextract additions rather than assuming the declarations already exist.

## Verification strategy

The phase uses layered proofs.

1. Runtime tests cover capability publication, strict creation rejection,
   partial update outcomes, state/event ordering, attention idempotence, window
   close, session teardown, and interaction token validity.
2. Deterministic AppKit port tests cover command ordering, native readback
   divergence, broker ownership, embedded opt-in propagation, and pointer-down
   ordering.
3. macOS tests compile and call every generated KFFI binding and read the real
   native state for transparency and sharing type. They do not claim visual
   transparency, visible attention, or user drag behavior from headless CI.
4. A non-blocking manual harness records real transparency, attention, and
   system move observations. Its result is not CI evidence.

The contract registry receives a dedicated active AppKit phase-5 completion
entry only after its automated evidence is present. Manual observations remain
separate from contract activation.

## Non-goals

- Adding a renderer, widget tree, or automatic visual-effect view;
- representing an application icon as an effective per-window property;
- using private AppKit APIs to force system resize;
- promising capture-proof security;
- changing the deferred scope of display-dependent positioning or exclusive
  fullscreen.

## Acceptance criteria

- Every phase-5 window field is either effectively supported by AppKit or
  rejected explicitly with its correct capability and failure.
- No unsupported initial value creates a peer with a substituted state.
- AppKit attention cannot affect an embedded host without explicit opt-in and
  never survives its owning window or session.
- A native system move is possible only through a valid synchronous interaction
  token and preserves ordinary input delivery.
- Every KFFI declaration used by Kadre is generator-produced and has a focused
  Kextract/KFFI proof.
- CI verifies semantic contracts; the manual harness contains only visual or
  user-gesture observations that cannot be proven automatically.
