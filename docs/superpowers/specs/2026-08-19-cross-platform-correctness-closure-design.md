# Cross-platform correctness closure design

Date: 2026-08-19  
Status: approved for implementation  
Supersedes: the unfinished closure items in `2026-07-17-cross-platform-correctness-design.md`  
Scope: Android, Linux X11/Wayland, AppKit, UIKit, Web JS/Wasm, samples, build, CI, and release documentation  
Out of scope: Windows and Win32-specific behavior

## Purpose

The initial cross-platform correctness campaign delivered a substantial part of
the remediation, but an independent review found that several stated contracts
and deterministic CI gates remain incomplete. This document closes only those
gaps. It keeps the five compatibility changes already approved in the original
design. One additional documented behavioural change is approved by the user for
this closure: after the final known Wayland output is removed,
`availableMonitors()` returns an empty list rather than a synthetic monitor.

## Required outcomes

1. `Window.close()` is terminal when it returns on X11 and Wayland: it makes the
   owner non-current, removes or suppresses queued callbacks *for that window*,
   and permits only its single required `Destroyed` notification thereafter.
   Other windows and the event loop continue normally.
2. UIKit follows the canonical callback traces below. It uses the same startup
   ordering as the established X11, Wayland, AppKit, and Web implementations;
   focus, occlusion, and termination are dispatched inside documented
   iterations; termination runs `destroySurfaces`, window destruction,
   `suspended`, and exit exactly once.
3. `WaitUntil(deadline)` never reports `ResumeTimeReached` before the observed
   clock reaches `deadline`. Web rearms a premature timer; X11 and Wayland cap
   a platform wait only as an implementation detail and retain/rearm the
   original deadline.
4. The common conformance assertion proves a complete iteration, not merely
   its first and last matching callbacks: exactly one `newEvents`, dispatch only
   between it and one final `aboutToWait`, and no application callback after
   `aboutToWait`. Android, X11, Wayland, AppKit, UIKit, Web JS, and Web Wasm
   each supply and run an adapter exercising this invariant.
5. Linux backend selection attempts a usable native connection before invoking
   application callbacks. Auto mode falls back from a failed preferred Wayland
   probe to a usable X11 probe; an explicit backend override is terminal and
   never falls back. If all eligible candidates fail, the raised error names the
   attempted backends, allowlisted environment values, and unwrapped native
   causes.
6. Wayland output removal is faithfully observable: after the final known live
   output is removed, monitor enumeration is empty rather than synthesizing a
   stale monitor. Initial discovery before any output is known remains a
   distinct state.
7. Pull-request CI contains blocking deterministic gates for the required
   host, browser, iOS simulator, Android emulator, Linux glibc/musl, and
   visual/capture validations. A deterministic step must not use
   `continue-on-error`, `|| true`, or convert a missing required environment
   into success. These jobs run on every pull request without path filters and
   feed the required `cross-platform-correctness` aggregate status. Hardware-only
   diagnostics may be optional only when plainly separated from the required gate.
8. Documentation records the six approved compatibility changes, current Web
   event names, Linux runtime requirements, and connection-based routing. A
   closure report at `docs/kadre/cross-platform-correctness-report.md` maps each
   of the unique findings 1 through 19 to a specific test or native validation.

## Canonical lifecycle traces

The following sequence is normative for all backends where the named platform
callback occurs. `aboutToWait` is the final `ApplicationHandler` callback in an
iteration. `exit()` follows that callback only for the terminal sequence and
does not begin a native wait. A repeated or out-of-order platform notification
that does not change the lifecycle state emits no application callback.

| Transition | Exact observable trace |
| --- | --- |
| Initial startup | `resumed` → `newEvents(Init)` → `canCreateSurfaces` → `aboutToWait` |
| Reactivation | `resumed` → `newEvents(WaitCancelled)` → `Focused(true)` → optional theme update → `aboutToWait` |
| Resign active | `newEvents(WaitCancelled)` → `Focused(false)` → `suspended` → `aboutToWait` |
| Enter background | `newEvents(WaitCancelled)` → `Occluded(true)` → `destroySurfaces` → `aboutToWait` |
| Enter foreground | `newEvents(WaitCancelled)` → `Occluded(false)` → `canCreateSurfaces` → `aboutToWait` |
| Termination | `newEvents(WaitCancelled)` → `destroySurfaces` → one `Destroyed` per live window → `suspended` → `aboutToWait` → `exit()` |

The startup order deliberately preserves the approved order already used by the
non-UIKit backends. UIKit is the backend brought into alignment; the table
replaces any conflicting lifecycle prose in plans 04 through 07.

## Design decisions

### Terminal close

X11 and Wayland use the window-local state machine
`OPEN → TOMBSTONED → NATIVE_CLOSED → DESTROYED_DELIVERED`. `close()` atomically
transitions `OPEN` to `TOMBSTONED`, purges/cancels queued work for that owner,
and publishes exactly one native-close command. It never rolls back to `OPEN`.
The event-loop thread performs native destruction and emits the single
`Destroyed` callback; queue delivery rejects all other callbacks for a tombstoned
owner. This gives callers the documented postcondition without performing
thread-confined native operations on the wrong thread.

If wake-up publication or native cleanup fails, the owner remains tombstoned.
The failure is retained and propagated at the loop's Kotlin-safe boundary; it
does not restore callbacks, handles, or registration. Tests cover close during
a batch, repeated close, native-destroy/close races, wake failure, and the fact
that unrelated windows still receive events.

### UIKit iteration coordinator

The UIKit lifecycle coordinator delegates each platform notification to an
iteration helper on `UIKitActiveEventLoop` that implements the matching row of
the canonical trace table. The startup row intentionally calls `resumed` before
`newEvents(Init)`, matching the existing non-UIKit contract. Terminal state
guards make repeated delegate callbacks idempotent. `exit()` is called only
after the terminal iteration's `aboutToWait`.

### Deadline scheduling

All three backends compare an observed epoch with the immutable requested
deadline immediately before producing `ResumeTimeReached`. An early callback
does not produce an application iteration; it computes the remaining delay and
rearms. Platform delay clamping is isolated in the timer request, never in the
deadline comparison. Web, X11, and Wayland each test an early callback; X11 and
Wayland additionally test a deadline whose remaining delay exceeds
`Int.MAX_VALUE` milliseconds.

### Linux connection selection

`KADRE_LINUX_BACKEND=wayland` or `=x11` selects only that backend and fails
descriptively if its classpath, probe, or launch step fails. In auto mode, each
candidate first verifies classpath availability, then attempts a native probe,
and only then launches the selected event loop. Reflection unwraps
`InvocationTargetException`; the final descriptive failure preserves the primary
native cause and attaches the other candidate failures as suppressed causes.
Only `WAYLAND_DISPLAY`, `DISPLAY`, and `KADRE_LINUX_BACKEND` may be quoted in
the error message.

### Wayland output state

Wayland distinguishes `DiscoveryPending` (no registry conclusion yet) from
`KnownOutputs(empty)` (the final known output was removed). A synthetic monitor
is allowed only for the former compatibility state. Once the registry has
reported and removed all live outputs, `KnownOutputs(empty)` is returned as an
empty monitor list. KDoc, release notes, and the compatibility-change list
record this sixth approved behaviour.

### Evidence and CI

CI invokes repository scripts that own their success criteria and emit test
result summaries. Workflow YAML only orchestrates those scripts. The required
matrix is the original plan-08 host, Web browser, iOS simulator, Android
emulator, glibc X11/Wayland, musl X11/Wayland, and deterministic-capture matrix;
each job has a timeout of at most 25 minutes and the critical path remains below
30 minutes. Required jobs have no pull-request path filter, are consumed by the
`cross-platform-correctness` aggregate, and fail when the test count is zero, a
deterministic test is skipped, or a required PNG is missing, undecodable, empty,
or all background. A workflow-contract test rejects masking constructs and a
missing required job. The closure report validator requires exactly nineteen
unique rows with a finding number, test/command, environment, result, and proof.

## Validation criteria

- Every behavioural change starts with a focused failing regression test and is
  implemented minimally until it passes.
- The backend test suites cover close-during-batch, premature deadline callback,
  repeated UIKit lifecycle callbacks, final Wayland output removal, and Linux
  fallback/failure diagnostics.
- The common conformance harness has a negative test for a callback after
  `aboutToWait`, checks one `newEvents` and one final `aboutToWait`, and runs
  positive adapters for Android, X11, Wayland, AppKit, UIKit, Web JS, and Web Wasm.
- The workflow contract test fails on a deterministic `continue-on-error`, a
  path-filtered or missing required job, zero results, a deterministic skip, or
  forbidden success masking.
- The final matrix runs the commands required by the original plan where the
  current host supports them. Any unavailable Apple, Android, compositor, or
  container environment is reported as a limitation rather than treated as a
  passing result.

## Completion boundary

The branch is ready to merge only when all required outcomes have matching
implementation, regression test, and CI/closure evidence; the independent
post-change review reports no Critical or Important unresolved issue; and the
fresh available validation matrix exits successfully.
