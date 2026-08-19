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
design; it introduces no further public API break.

## Required outcomes

1. `Window.close()` is terminal when it returns on X11 and Wayland: it makes the
   owner non-current, removes or suppresses queued events for that owner, and
   prevents every later application callback except the single required
   `Destroyed` notification.
2. UIKit callbacks use the shared iteration contract. Startup is an
   `Init` iteration before `resumed` and `canCreateSurfaces`; focus, occlusion,
   and termination are dispatched inside iterations; termination runs
   `destroySurfaces`, window destruction, `suspended`, then exit exactly once.
3. `WaitUntil(deadline)` never reports `ResumeTimeReached` before the observed
   clock reaches `deadline`. Web rearms a premature timer; X11 and Wayland cap
   a platform wait only as an implementation detail and retain/rearm the
   original deadline.
4. The common conformance assertion proves a complete iteration, not merely
   its first and last matching callbacks: exactly one `newEvents`, dispatch only
   between it and one final `aboutToWait`, and no application callback after
   `aboutToWait`. Each in-scope backend supplies an adapter or an explicit
   platform-native equivalent exercising this invariant.
5. Linux backend selection attempts a usable native connection. If a preferred
   Wayland connection fails and X11 is usable, it falls back to X11. If all
   candidates fail, the raised error names the attempted backends, relevant
   environment values, and unwrapped native causes.
6. Wayland output removal is faithfully observable: after the final live
   output is removed, monitor enumeration is empty rather than synthesizing a
   stale monitor.
7. Pull-request CI contains blocking deterministic gates for the required
   host, browser, iOS simulator, Android emulator, Linux glibc/musl, and
   visual/capture validations. A deterministic step must not use
   `continue-on-error`, `|| true`, or convert a missing required environment
   into success. Hardware-only diagnostics may be optional only when plainly
   separated from the required gate.
8. Documentation records the five approved compatibility changes, current Web
   event names, Linux runtime requirements, and connection-based routing. A
   closure report maps findings 1 through 19 to a specific test or native
   validation.

## Design decisions

### Terminal close

X11 and Wayland will claim terminal ownership synchronously in `close()` before
their native close command is queued. The queued command performs native
destruction and emits the one terminal callback, but queue delivery filters on
the synchronously invalidated owner. This gives callers the documented
postcondition without performing thread-confined native operations on the
wrong thread.

### UIKit iteration coordinator

The UIKit lifecycle coordinator will delegate all observable work to a single
iteration helper on `UIKitActiveEventLoop`. That helper emits `newEvents`, runs
the requested lifecycle/window callbacks, and emits `aboutToWait`. Startup,
activation, backgrounding, foregrounding, and termination each select their
documented `StartCause`; terminal state guards make repeated delegate callbacks
idempotent.

### Deadline scheduling

All three backends compare an observed epoch with the immutable requested
deadline immediately before producing `ResumeTimeReached`. An early callback
does not produce an application iteration; it computes the remaining delay and
rearms. Platform delay clamping is isolated in the timer request, never in the
deadline comparison.

### Evidence and CI

CI invokes repository scripts that own their success criteria and emit test
result summaries. Workflow YAML only orchestrates those scripts. Required
platform jobs are blocking; unavailable platform prerequisites fail with an
actionable diagnostic. The closure report is checked as part of the workflow
contract so the 19/19 trace cannot silently disappear.

## Validation criteria

- Every behavioural change starts with a focused failing regression test and is
  implemented minimally until it passes.
- The backend test suites cover close-during-batch, premature deadline callback,
  repeated UIKit lifecycle callbacks, final Wayland output removal, and Linux
  fallback/failure diagnostics.
- The common conformance harness has a negative test for a callback after
  `aboutToWait` and positive adapters for each in-scope backend.
- The workflow contract test fails on a deterministic `continue-on-error`,
  missing required job, or forbidden success masking.
- The final matrix runs the commands required by the original plan where the
  current host supports them. Any unavailable Apple, Android, compositor, or
  container environment is reported as a limitation rather than treated as a
  passing result.

## Completion boundary

The branch is ready to merge only when all required outcomes have matching
implementation, regression test, and CI/closure evidence; the independent
post-change review reports no Critical or Important unresolved issue; and the
fresh available validation matrix exits successfully.
