# Web manual charter index — the host-owned frontiers

The Phase 8 gate requires that « le cahier manuel couvre les frontières
host-owned : navigation/bfcache, focus réel, activation utilisateur, popup
préparé par host, permissions et matériel »
([WEB-IMPLEMENTATION-ROADMAP.md](../../../../WEB-IMPLEMENTATION-ROADMAP.md),
Phase 8 gate). This index consolidates the seven phase charters of this
directory by those six frontiers: for each frontier, the charter procedures
that cover it (cited by their exact titles), how the procedures compose when a
frontier is split across charters, and what each procedure proves. Every cited
section exists verbatim in the cited charter; this index adds no procedures of
its own, because the verification found no uncovered frontier — each of the six
has at least one real, host-staged procedure.

## Coverage table

| Frontier | Charter(s) | Procedures | Count |
| --- | --- | --- | --- |
| navigation/bfcache | [phase-1-lifecycle.md](phase-1-lifecycle.md), [phase-2-surface.md](phase-2-surface.md) | P1§1, P2§2 (adjacent: P1§5) | 2 |
| focus réel | [phase-1-lifecycle.md](phase-1-lifecycle.md), [phase-3-input.md](phase-3-input.md), [phase-4-interactions.md](phase-4-interactions.md), [phase-5-text-input.md](phase-5-text-input.md) | P1§2, P3§1, P4§4 (focus cell), P5§2 | 4 |
| activation utilisateur | [phase-4-interactions.md](phase-4-interactions.md), [phase-3-input.md](phase-3-input.md) | P4§1, P4§2, P4§3 (+ the deferral note P3§6, discharged by P4§2) | 3 (+1) |
| popup préparé par host | [phase-4-interactions.md](phase-4-interactions.md) (composer: [phase-2-surface.md](phase-2-surface.md)) | P4§4 (composer: P2§3) | 1 |
| permissions | [phase-6-displays-devices.md](phase-6-displays-devices.md), [phase-7-capture.md](phase-7-capture.md) | P6§1, P6§3, P6§4, P7§1, P7§3, P7§4, P7§5 | 7 |
| matériel | [phase-6-displays-devices.md](phase-6-displays-devices.md) (input hardware: [phase-3-input.md](phase-3-input.md), [phase-5-text-input.md](phase-5-text-input.md)) | P6§1, P6§2, P6§3, P6§5 (input hardware: P3§3, P3§4, P3§5, P5§1, P5§4) | 4 (+5) |

`P<n>§<k>` is procedure *k* of the charter named in the same row; the exact
titles follow in the per-frontier sections.

## navigation/bfcache

| Charter | Procedure | What it proves |
| --- | --- | --- |
| phase-1-lifecycle.md | §1 "Page lifecycle and bfcache" | a real `pagehide` — persisted or not — terminates the session `Stopped(HostDetached)`, and a bfcache return never resurrects it (the restored page's `pageshow` belongs to the preserved document, a fresh return's to a fresh one). |
| phase-2-surface.md | §2 "Real bfcache traversal with a pending redraw" | the same traversal with a redraw request still waiting for its frame: the restored surface admits no `RedrawRequested` and schedules no frame on the restored document. |

**Composition.** Phase 1 owns the lifecycle contract of the traversal (the
`pagehide` is terminal, whatever the browser does with the document
afterwards); phase 2 re-runs that traversal with state in flight — a pending
redraw — to prove the restored document is a terminated surface that admits
nothing, including the non-persisted comparison the phase-1 procedure
established. The adjacent case P1§5
("Cross-document adoption through a host-created iframe") covers the other
way an element can leave its document:
adoption into a host-created iframe terminates the origin session with the
same `Stopped(HostDetached)`.

## focus réel

| Charter | Procedure | What it proves |
| --- | --- | --- |
| phase-1-lifecycle.md | §2 "Browser/tab focus and document visibility" | only the focused host in a visible, focused document is `Attached + Foreground + Active`; a blurred browsing context or hidden document degrades the lifecycle honestly, and returning focus cannot resurrect a terminated session. |
| phase-3-input.md | §1 "A host element that is not focusable, and why the host must provide it" | focusability belongs to the page (Kadre writes no `tabindex`): without it the keyboard observation is the honest silence of a boundary the host owns, while the capability still truthfully reports `keyboard=available`. |
| phase-4-interactions.md | §4 "The real popup: a child session in a window the host owns" (focus cell) | the child session in the popup has a live lifecycle of its own, `foreground-active while the popup is focused` — focus authority follows the real window, not the opener's claim. |
| phase-5-text-input.md | §2 "Cancelling mid-composition with Échap — and what a real blur does" | a real blur makes the browser withdraw its own composed text before the session's suspension lands, and refocusing resumes the session — the observed focus loss and the Kotlin-proved suspension are two halves of the same fact. |

**Composition.** The frontier is split by what owns the focus fact: phase 1
owns the lifecycle reduction the browser's focus and visibility impose;
phase 3 owns the page's own prerequisite (a focusable host element) and the
honest silence when it is missing; phase 4 observes focus *across* browsing
contexts through the popup's own focus; phase 5 observes what a real blur
does to a live composing session on the element. In phase 4 the focus
observation is one cell of the popup procedure, not a standalone procedure —
the standalone focus procedures are P1§2 and P3§1.

## activation utilisateur

| Charter | Procedure | What it proves |
| --- | --- | --- |
| phase-4-interactions.md | §1 "Real fullscreen with a real user activation, and the browser's own exit" | a real click's transient activation is the entire authority — `committed` is published only at the browser's own `fullscreenchange`, never at the moment of the request, and a browser refusal is delivered as the one honest `rejected:platformFailure:web:fullscreen:refused`. |
| phase-4-interactions.md | §2 "Real pointer lock, confined movement, and `Esc`" | the pointer-lock primitive needs the click's still-held activation; the lock is the page's, and the surface's published capture stays `none` throughout. |
| phase-4-interactions.md | §3 "The token rules a page console can observe — and the one it cannot" | an untrusted synthetic dispatch carries no transient activation, so the browser itself refuses (Kadre pre-refused nothing — it read no `navigator.userActivation`); one token, one request, one terminal outcome; and the `Expired` rule is honestly declared out of a page console's reach, proven at the Kotlin seam instead. |
| phase-3-input.md | §6 "Pointer lock, which this phase leaves to a later one" (deferral, discharged) | documents that pointer lock needs a transient user activation and claims nothing, then discharges the real-screen proof to P4§2 — the deferral itself is part of the frontier's coverage. |

**Composition.** Phase 4 is the substance: the trusted click as authority
(§1), a primitive whose activation window is narrow enough that the procedure
must tell the reader to run the console command *immediately after clicking*
(§2), and the admission/token rules a page can and cannot observe (§3).
Phase 3's procedure 6 is the recorded deferral of the same requirement,
explicitly discharged by phase 4. A cousin gate lives in phase 6: procedure 1's
privacy gate also turns on real user interaction with the page (a pad button
or stick movement) before the browser exposes anything — the same
host/user-owned authority, recorded under permissions/matériel below.

## popup préparé par host

| Charter | Procedure | What it proves |
| --- | --- | --- |
| phase-4-interactions.md | §4 "The real popup: a child session in a window the host owns" | the popup window exists **before** any Kadre call (the host prepared it), the child session runs in the host-prepared element with the very session hash the opener's outcome named (`AdditionalHostRequested`, launched through the ordinary attach path — no second lifecycle, no shared scope), and a second window request for the same element is refused `rejected:alreadyInUse:host` across contexts. |

**Composition.** One procedure owns this frontier outright; it also records
what a real window *cannot* stage (the requester's scope closure), leaving
that independence to the automated proof. The composer P2§3
("A host-owned element moved between browsing contexts while a lease is held")
is the same host-prepared-context family from the surface side: a leased
element moved into a host-created iframe while the lease is held.

## permissions

| Charter | Procedure | What it proves |
| --- | --- | --- |
| phase-6-displays-devices.md | §1 "A real hardware gamepad: the OS connect/disconnect and the privacy gate" | Chromium's privacy gate keeps real pads invisible to `getGamepads()` until the user interacts with the page — the poll-driven inventory states the gate's answer, whatever it is, and invents nothing in either direction. |
| phase-6-displays-devices.md | §3 "Real multi-display, and the Window Management API documented as unsupported" | `navigator.getScreenDetails` is deliberately never called — its prompt-forbidding permission gate is never triggered (no prompt appears on any move), and the one-display fallback is the documented contract. |
| phase-6-displays-devices.md | §4 "The insecure-context matrix beyond the fake-hostname trick" | across real deployments (LAN IP, `localhost`, `127.0.0.1`, `file://`) the inventory stays `Enumerated` — empty and complete where the browser exposes nothing, never `Unavailable` dressed up — and no permission prompt is ever triggered by enumeration or readback. |
| phase-7-capture.md | §1 "The real picker: the dialog, the thumbnails, the choice" | the browser's own picker is the only consent machinery — no frame exists before the choice, and the stream starts only after it: the consent boundary the control plane never crosses on its own. |
| phase-7-capture.md | §3 "macOS screen-recording permission interplay" | the OS Screen Recording grant is browser-scoped and appears nowhere in `CapturePermissionState` — the readback the model publishes is the browser's `display-capture` word, and the OS gate is the user's, outside any page. |
| phase-7-capture.md | §4 "Revocation: the "Stop sharing" bar, the tab, the end of the track" | revocation has exactly one shape on the web — the granted track ends (browser bar, captured-tab navigation, window close, or the app's own stop) — and each closed outcome is terminal exactly once, with the browser's own indicator taken down. |
| phase-7-capture.md | §5 "Firefox/WebKit: the processor, the consent, the pixels" | each engine's picker is its own (Safari's splits into the OS system picker), the page-side sequence is the delivered contract — one `getDisplayMedia` per open — and the permission readback is answered or honestly `unavailable`, always mirrored. |

**Composition.** Phase 7 owns the positive consent flows (the picker, the OS
interplay, revocation, the engine variants); phase 6 owns the negative space
that is just as much the contract: the permission machinery Kadre deliberately
never starts (Window Management, insecure-context enumeration) plus the one
privacy gate it cannot avoid (the gamepad interaction gate). Together they
prove both halves of the permission frontier: what the browser asks on
Kadre's only consent path, and the absence of any other prompt.

## matériel

| Charter | Procedure | What it proves |
| --- | --- | --- |
| phase-6-displays-devices.md | §1 "A real hardware gamepad: the OS connect/disconnect and the privacy gate" | the OS-level connect/disconnect (unplug, power off — not a DOM mutation) publishes `GamepadAdded`/`GamepadRemoved` at fresh manager revisions, and a reconnect is a fresh `GamepadId` — the model never resurrects a handle that reached its terminal state. |
| phase-6-displays-devices.md | §2 "Actual rumble on real hardware" | the physical fact the automated suite cannot prove: the pad actually rumbles with the requested strong/weak magnitudes and duration, stops on the stop command, ends by itself when the duration elapses, and the real actuator's `effects` list — the one fact the synthetic source substitutes — is recorded. |
| phase-6-displays-devices.md | §3 "Real multi-display, and the Window Management API documented as unsupported" | on a real multi-display machine the inventory stays exactly one display and follows the browsing context (the window's own viewport), never the desk — the fallback's real-screen reading. |
| phase-6-displays-devices.md | §5 "Firefox/WebKit `vibrationActuator` behaviour" | the guarded actuator extraction answers `null` on engines without the primitive — the honest `Unsupported` of a missing primitive, not a guessed one — and the connection-event and dpr-query guards are read on real engines. |

**Composition, and where the input hardware lives.** The device half of
matériel — gamepads, actuators, displays — is phase 6. The input half is real
hardware too, and it lives in the input charters: phase 3's procedure 3
"Trackpad momentum" (a real fling the page-mode wheel cannot produce),
procedure 4 "A real keyboard layout that is not US" (what a physical layout
delivers), and procedure 5 "Real pen hardware" (`pointerType` is the browser's
own word; pen members carried only inside their declared domains); phase 5's
procedure 1 "A real OS IME: activation, candidates, commit" and procedure 4
"A real multi-layout keyboard over the text session". The pre-mapping of this
frontier to phase 6 alone was therefore correct for the device frontier but
incomplete as a statement of hardware coverage — the index records the input
composers above so the gate's « matériel » is answered in full.

## The charters and the automated suites

These charters are manual complements, and their epistemics are the ones each
charter states in its scope: phase 1 opens with « it is never a substitute
for automated JS and Wasm evidence, and it is not wired into the contract
validator »; phase 2 closes its scope with « The observations below may
reveal a browser-specific issue, but they may not be used to fabricate
contract evidence or to claim that an automated branch has passed »; phase 7
records that « the only browser consent machinery this adapter ever starts
is the explicit open of the application ». In the same spirit:

- a manual pass records what a real browser, on a real machine, actually did
  (browser, OS, hardware model, the exact cells observed);
- it never replaces the automated JS and Wasm smokes, never makes an
  automated branch pass, and never fabricates `contract-evidence`;
- where a rule is out of a page's reach (phase 4's `Expired` token rule,
  phase 5's composition preservation), the charter says so and names the
  automated proof that owns it;
- a second engine becomes a declared minimum only through its own evidence,
  never through a manual reading (phases 6 and 7, Firefox/WebKit procedures);
- any observation outside the guarded paths is a finding to report, not a
  feature.

The deterministic contract under each frontier — the lifecycle state machine,
the interaction register, the capture outcome sets — is the automated
suites' to prove; the charters above are where the six host-owned frontiers
are observed once, honestly, on a real screen.
