# Android emulator contract driver

This non-published JVM project runs the public Android host-attachment proof of
phases 0 through 5 on a real emulator. It is a contract driver, not an
implementation API: it forks the instrumented consumer build, executes its
scenarios on the attached device, and turns the JUnit output into the canonical
`AND-001` through `AND-006` evidence with the validator's own
generator. English
prose, like the Web driver README it mirrors; everything else in this phase is
documented in French in the sources.

## What the driver proves

Six contracts, all active in the registry
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

**`AND-004`** — the phase 3 essential input observation and delivery proof
(source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 3 — Clavier, pointeur, touch
et stylet`). Seven scenarios and three sentinels, all in the instrumented test
class `org.graphiks.kadre.consumer.KadreConsumerInputContractTest`. The
injection boundary is the real Android dispatch: events enter through
`activity.window.callback.dispatchKeyEvent(...)` /
`dispatchTouchEvent(MotionEvent.obtain(...))` / `onWindowFocusChanged(false)` on
the activity's real window — whose callback IS the chained Kadre wrapper after
install — so every event crosses the whole production path (primitive copy at
dispatch, observation into the delivery, intact forward to the wrapped
callback). The `KeyEvent` fields the wrapper extracts (`getUnicodeChar()`,
`repeatCount`, …) are only observable on an emulator; this is exactly where that
path is proven. All forms are Activity-form: the View form has no
`Window.Callback` to chain (`View.getWindow()` is `@hide`), its input
capabilities stay honestly `Unsupported` — the declared phase limit, recorded in
[capabilities/android.md](../../../capabilities/android.md) §2 and §4 with the
deferred owner-routing option:

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-input-host-untouched` | scenario | a host listener (key AND touch, installed on the attached View BEFORE the attach) still receives its events after the wrapper installs — each exactly once, the chaining does not duplicate; the session observes the same events in parallel (observation is not interception) |
| `android-input-keys-mapped` | scenario | keys injected at the window callback are mapped through the HID table (usage, case, repeat, modifiers, press/release state); a key outside the table is forwarded without any invented stimulus |
| `android-input-multi-touch-stable-ids` | scenario | multi-touch contacts keep stable identities across a gesture and retire at up/cancel; a DOWN outside the View bounds mints nothing (its whole gesture is silent), a DOWN inside delivers the whole gesture |
| `android-input-focus-loss-resets` | scenario | window focus loss delivers the in-band `StateReset(FocusLost)` after forgetting every active contact without emitting; the next DOWN mints fresh identities |
| `android-input-default-behavior-suppress` | scenario | committing `inputDefaultBehavior = SuppressWhenPossible` consumes the document scroll keys — press AND release — for the host while the session still observes them (delivered-then-suppressed, exactly); under `HostDefault` nothing is consumed |
| `android-input-two-sessions-isolation` | scenario | two attached activities: a window-A event never crosses into session B (and the reverse), each delivery exactly once; the wrapper ownership identity is exercised — a foreign host callback chained on top stays in the chain and is never overwritten at close; a re-attached session delivers exactly once again |
| `android-input-stylus-mouse-boundary` | scenario | a stylus contact (`TOOL_TYPE_STYLUS` DOWN/MOVE/UP through the touch dispatch) rides the pointer lane — `PointerButtonChanged(Pen, Pressed/Released)` carrying pen state, `PointerMoved(Pen)` — with the published `touches` staying EMPTY; a mouse hover (`ACTION_HOVER_ENTER`/`MOVE` through the generic dispatch) delivers `PointerEntered(Mouse)`/`PointerMoved(Mouse)` scoped to the View bounds and a relative-axis `ACTION_SCROLL` delivers `Scrolled(Logical)`; injection is in window coordinates (the dispatch space) at the view-local center computed through the public `getLocationOnScreen` pair, and delivered positions are asserted view-local |
| `android-input-suppression-exact` | sentinel | a policy that consumes EVERY key (not exactly the nine scroll keys) kills `androidInputDefaultBehaviorSuppress` alone |
| `android-input-no-double-delivery` | sentinel | a wrapper that no longer recognizes its own callback (ownership-identity damage) overwrites the foreign callback at close — the host callback is orphaned and loses its events after re-attach. The demonstrated kill is precisely this ownership-identity damage, the mutation class that enables duplicate delivery: no emulator scenario can inject the same event twice through the real dispatch path, so the sentinel pins the identity guard that makes double delivery impossible rather than a duplicate observed in the act |
| `android-input-contacts-reset` | sentinel | a tracker that no longer clears on focus loss kills `androidInputFocusLossResets` alone |

The AND-004 sentinel blast radius is recorded the same way: each of the three
instrumented mutations (suppression widened to every key; wrapper ownership
identity dropped; contact reset on focus loss removed) kills **exactly one** of
the ten phase-3 tests — its guard — and leaves the other nine green.

**`AND-005`** — the phase 4 window commands and transient interactions proof
(source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 4 — Fenêtre Activity et
interactions transitoires`). Five scenarios and three sentinels, all in the
instrumented test class
`org.graphiks.kadre.consumer.KadreConsumerWindowInteractionContractTest`, on
the Activity form at the real boundary (the same `window.callback` dispatch as
phase 3, plus the real `Activity`/`Window` the commands commit through). The
View form appears inside the evidence as the distinct-capabilities pin: it has
neither a window manager (`primary == null`) nor input observation (the phase 3
structural limit).

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-window-commits-with-readback` | scenario | the three supported fields commit with effective readback — `title` read back on BOTH sides (`Activity.getTitle` and the window state), `contentProtection` through `FLAG_SECURE` set/cleared and read back on the layout flags, `fullscreen` `Borderless` committed through the readback-driven retry (`PartiallyApplied(TemporarilyUnavailable(retryable = true))` replayed until the insets readback confirms the bars hidden); `PropertiesChanged` events carry exactly the changed properties; then the View-form pin on a second own-task Activity attached View-form: `primary == null` and the keyboard/pointer/touch input capabilities `Unsupported` |
| `android-window-refused-fields-inert` | scenario | `resizable`/`level`/`decorations` answer `PartiallyApplied` with per-field `Unsupported(UpdateWindow)` and call NO primitive: the Activity snapshot (title, `FLAG_SECURE`, system bars), the window state (revision included) and the event flow are untouched |
| `android-window-back-host-owned` | scenario | `KEYCODE_BACK` down/up at the window callback: the Activity finishes itself (`onBackPressed`), Kadre observes nothing (Back is unmapped — no stimulus, no `CloseRequested`, the window stays `Open`), the session terminates only `Stopped(HostDetached)`; then `window.close()` answers `Failure(TemporarilyUnavailable(retryable = false))` and `session.close()` terminates `Stopped(HostRequested)` with the Activity still alive and not finishing |
| `android-surface-cursor-and-capture` | scenario | `System(Text)` reaches `view.pointerIcon === PointerIcon.getSystemIcon(context, TYPE_TEXT)` (identity readback) and `Default` restores; `apply(Confined)`'s response carries `pointerCapture == None` — the attempt is NOT the confirmation — and the real `Window.Callback.onPointerCaptureChanged` listener then publishes `Confined` at a later revision; `Locked` is refused field-by-field; `None` commits in-pass and the state returns |
| `android-interaction-token-authority` | scenario | the capability announces exactly `{EnterFullscreen, ExitFullscreen, LockPointer, UnlockPointer}` `Available`; a second handler answers `AlreadyInUse(Interaction)`; key `a` injected at the window callback reaches the handler as `KeyPressed` BEFORE the ordinary stimulus (deterministic, same thread); the script: `OpenWindow` → `Unsupported(Interaction)` (no token consumed, no outcome published), `LockPointer(Locked)` → admitted (`Committed` outcome, state `Confined` via the listener), `UnlockPointer` on the SAME token → `InteractionRequired(Consumed)`; key `b`: `UnlockPointer` on a fresh token releases, state `None` via the same listener |
| `android-window-native-gate` | sentinel | a refused field that calls its native primitive anyway is caught — the mutation (`native?.setTitle` inside the refused-field branch) kills `androidWindowRefusedFieldsInert` alone |
| `android-capture-listener-confirmation` | sentinel | committing the capture at `request()` return without the listener kills `androidSurfaceCursorAndCapture` alone (the two-step assertion) |
| `android-interaction-unadvertised-refused` | sentinel | advertising `OpenWindow` kills `androidInteractionTokenAuthority` alone (the exact-advertised-set assertion) |

The AND-005 sentinel blast radius is recorded the same way: each of the three
instrumented mutations (a refused field reaching its native primitive; the
capture committed at request return without the listener; `OpenWindow`
advertised) kills **exactly one** of the twenty-seven phase-0–4 tests — its
guard — and leaves the other twenty-six green (revert + green demonstrated for
each during task 5).

One environment finding is documented in the scenario itself: on the headless
`Kadre_API_35` emulator, `InsetsController.show(systemBars())` never flips the
dispatched insets' visible state back, so the `Windowed` restore commit is NOT
asserted — the window honestly answers
`TemporarilyUnavailable(retryable = true)` throughout the readback-driven retry
loop (the on-row word working — a commit that did not happen is never
published) and the bar restore is a best-effort SDK cleanup. A graphical
emulator can extend the scenario if a future registry row ever demands the
reverse direction.

**`AND-006`** — the phase 5 text-input channel qualification and IME
non-deduction proof (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 5 —
Text input, IME et gestures`). Two scenarios and one sentinel, all in the
instrumented test class `org.graphiks.kadre.consumer.KadreConsumerTextContractTest`,
injection at the same real boundary as phases 3–4
(`activity.window.callback.dispatchKeyEvent(...)`):

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-text-input-channel-absent` | scenario | on the consumer's attached View, `view.onCreateInputConnection(EditorInfo())` returns **null** and `view.onCheckIsTextEditor()` returns **false** (the base-View SDK boundary, proven at the boundary itself); `capabilities.textInput` is `Capability.Unsupported(TextInput)` and `openTextInput(TextInputConfig())` is refused on-row — the channel an IME would bind to does not exist on the delivered attach form |
| `android-text-input-ime-key-deduplication` | scenario | keys and an IME-shaped text carrier injected at the window callback: the mapped keys deliver their key facts in exact order (`Character("a")` press/release — the `getUnicodeChar()` fact — then `Named(Backspace)` press/release), the text-carrying `KeyEvent` delivers **nothing**, `capabilities.textInput` is unchanged after the injections, and `openTextInput` is still refused — the structural zero `TextInputEvent`: the only channel a text event could ride is an open session, and the refusal proves none ever existed during the exercise (DESIGN §10.3: composed text comes only from a `TextInputSession`, never deduced from key events) |
| `android-text-input-no-synthesis` | sentinel | a wrapper that synthesizes a table identity from the string the text carrier holds kills `androidTextInputImeKeyDeduplication` alone (28 of 29 stay green — no other scenario injects a characters-carrying event) |

**The SDK fact the scenario is built on.** The only public `KeyEvent`
constructor that carries characters is
`KeyEvent(long time, String characters, int deviceId, int flags)`; it FORCES
`action = ACTION_MULTIPLE` and `keyCode = KEYCODE_UNKNOWN` — the canonical
IME shape. The carrier is therefore out of the 65-key mapper table
(`hidUsage(KEYCODE_UNKNOWN) == null`) and the delivery gate
(`AndroidInputDelivery.kt:209`) returns before any stimulus derivation.

**The ACTION_MULTIPLE rule (normative, written in phase 5).** A text-carrying
`KeyEvent` is out of the mapper table and delivers NO observation; it is
never deduced into a text observation. Read the code as a two-layer path: the
wrapper DOES publish a primitive `Pressed` observation for it when the view is
focused (the KDoc at `AndroidInputWindowCallback.kt` now says so in one line),
and the DELIVERY layer then drops it before any stimulus.

**The text-input decision: a recorded structural limit, and the normative
proposal parked for the user.** Phase 5 delivers the IME bridge as internal
machinery — `AndroidTextInputPort` (capability always
`Unsupported(TextInput)`) and the pure mapper `AndroidImeEventMapper`,
transposed from the kadre-old `BaseInputConnection` bridge and proven against
the real `RuntimeTextInputSession` in the host suite `AndroidTextInputPortTest`
(multi-step composition rebase, Unicode surrogate pairs, stale revision,
focus suspend/resume, computed `deleteSurrounding`, silent finish, on-row
refusal) — with NO public activation path: `textInput` stays
`Capability.Unsupported(TextInput)` on both attach forms, a **recorded limit,
not a deferral** (the web precedent: `capabilities/web.md` §2's `gestures`
row, authorized by `BACKEND-CAPABILITIES.md` §4). The causal chain is
structural: `onCreateInputConnection`/`onCheckIsTextEditor` require a View
SUBCLASS; Kadre never owns or subclasses the host's View (DESIGN §15.1);
`View.getWindow()` is `@hide`; and the SDK has no registration API. The
normative proposal is PARKED for the user — stated, not implemented:
option A, a host-delegated helper (the host's own View subclass overrides
`onCreateInputConnection`/`onCheckIsTextEditor` and delegates to a
Kadre-supplied `InputConnection` factory — reachable through the existing
`withAndroidView` lease channel or as new public API); option B, a new attach
form. Each with its implications, written in
[capabilities/android.md](../../../capabilities/android.md) §2 and §4.
Gestures stay `Unsupported(GestureInput)` for the same recorded-limit reason:
a recognizer is host-owned or a normative hook, never deduced from the touch
contacts.

**The real-system complement.** The roadmap's phase 5 gate requires « L'IME
système réel et les gestures matérielles ont une procédure manuelle ciblée en
complément des tests automatisés » — that is the targeted manual procedure
[manual/phase-5-text-input.md](manual/phase-5-text-input.md): the real Gboard
composition, suggestion-strip commit and mid-composition blur on a qualified
host (and what today's attach honestly does on a real device), plus the
hardware-gesture pass and why the automation cannot stage either.

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
Android contract evidence validation against **`AND-001` through `AND-006`** —
one canonical JSON per contract, all from the same instrumented emulator run
(29 instrumented tests).

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

### Exit gate of roadmap phase 3

Same commands, one contract more. From a cold start (kill any running emulator,
`adb emu kill`, wait for zero devices, then let the script boot the AVD itself):
publish (1), consumer compile (2), emulator smoke + evidence + validator (the
script — **22** scenarios green, `AND-001.json` + `AND-002.json` +
`AND-003.json` + `AND-004.json`), the same transitive-resolution purity check
(grep exit 1, redone cold), and the no-device sanity tasks above. The roadmap
checklist is carried by the evidence itself:

- events injected at the real Android boundary, assertions on the public state
  and events — `android-input-host-untouched`, `android-input-keys-mapped`,
  `android-input-multi-touch-stable-ids` (the whole production path crossed
  from `activity.window.callback`);
- multi-touch, stylus, mouse, modifiers, focus loss, overflow —
  `android-input-multi-touch-stable-ids`, `android-input-focus-loss-resets`,
  `android-input-stylus-mouse-boundary` (a stylus contact rides the pointer
  lane with no touch state; a mouse hover and wheel scroll cross the same
  boundary through the generic dispatch), the mapped-key/modifier assertions
  of `android-input-keys-mapped`, and the
  host record `AndroidInputDeliveryTest.overflowIsTerminalFromTheRuntime`
  (the ingress overflow terminalizes the runtime's OWN source,
  `SourceOverflow(InputSource)` — the adapter has nothing to do);
- no double delivery, no key/contact left active after reset, no cross-session
  leak — `android-input-host-untouched` (each event exactly once through the
  chain), `android-input-two-sessions-isolation` (a window-A event never
  crosses into session B), `android-input-focus-loss-resets` (contacts
  forgotten, fresh identities after), and the three sentinels
  `android-input-suppression-exact`, `android-input-no-double-delivery`
  (ownership-identity damage: the mutation class that enables duplicates),
  `android-input-contacts-reset`;
- consumption respects the policy and the host — `android-input-host-untouched`
  (the host listener keeps every event it is entitled to) and
  `android-input-default-behavior-suppress` (delivered-then-suppressed, press
  and release, exactly the nine document scroll keys, nothing under
  `HostDefault`, touch/pointer never consumed); the consumption lives under
  the same focus gate as the observation — an unfocused scroll key is never
  consumed nor observed
  (`AndroidInputWindowCallbackTest.scrollKeyConsumptionLivesUnderTheFocusGate`,
  the host record the recorder cannot express);
- `AND-001` + `AND-002` + `AND-003` + `AND-004` together — 22 instrumented
  tests, one canonical JSON per contract, all from the same instrumented
  emulator run.

### Exit gate of roadmap phase 4

Same commands, one contract more. From a cold start (kill any running emulator,
`adb emu kill`, wait for zero devices, then let the script boot the AVD itself):
publish (1), consumer compile (2), emulator smoke + evidence + validator (the
script — **27** scenarios green, `AND-001.json` + `AND-002.json` +
`AND-003.json` + `AND-004.json` + `AND-005.json`), the same
transitive-resolution purity check (grep exit 1, redone cold), and the
no-device sanity tasks above. The roadmap checklist is carried by the evidence
itself:

- Activity and View have distinct capabilities — the View form has neither a
  window manager (`windows.state.value.primary == null`, empty window list) nor
  input observation (the phase 3 structural limit), asserted in the same run
  that proves the Activity-form commits:
  `android-window-commits-with-readback` carries the View-form pin, and the
  host records `AndroidWindowTopologyTest.viewFormHasNullPrimaryAndEmptyWindows`
  and `AndroidInputDeliveryTest.unwiredInstallKeepsInputCapabilitiesUnsupported`
  pin both forms without a device;
- refused fields are inert — `android-window-refused-fields-inert` (the
  Activity snapshot, the window state including its revision, and the event
  flow are untouched after a refused-only update), with the sentinel
  `android-window-native-gate` guarding exactly that inertness and the host
  record `AndroidActivityWindowTest.refusedFieldsCallNoPrimitive`;
- accepted effects are correlated to their observation or outcome —
  `android-window-commits-with-readback` (title read back on both sides,
  `FLAG_SECURE` read back on the layout flags, the Borderless commit replayed
  until the insets readback confirms, `PropertiesChanged` carrying exactly the
  changed properties), the cursor identity readback and the two-step
  pointer-capture commit of `android-surface-cursor-and-capture` (the apply
  response is not the confirmation — the SDK listener is; sentinel
  `android-capture-listener-confirmation` guards that seam), and the host
  records `AndroidActivityWindowTest.titleCommitsWithReadbackAndEvent`,
  `.contentProtectionCommitsViaFlagReadback`,
  `.borderlessFullscreenCommitsAndExclusiveIsPerFieldRejected`;
- revocation, late callback, destruction during a command, cancellation
  before/after commit — the host records: the capture revocation paths
  (`AndroidPointerCaptureTest.lostListenerRevertsToNone`,
  `.focusLossBridgesToLost`), the late/failed handler
  (`AndroidInteractionEngineTest.handlerExceptionFailsSessionThroughTheRuntime`),
  the lifecycle teardown (a detached owner is terminal and absorbs late
  publications — `AndroidLifecycleReducerTest.detachIsTerminalEvenWithSurvivingScope`,
  `AndroidHostLifecyclePropagationTest.publicationAfterDetachIsAbsorbedWithoutResurrection`)
  and the cancellation ladder (`AndroidViewLeaseTest.callerCancelledBeforeTheLeaseNeverStartsTheBlock`,
  `.startedCallbackCompletesOnHostThreadDespiteCancellation`; the window
  command's stale guard `AndroidActivityWindowTest.staleAndEmptyShapesPreserved`);
- `AND-001` + `AND-002` + `AND-003` + `AND-004` + `AND-005` together — 27
  instrumented tests, one canonical JSON per contract, all from the same
  instrumented emulator run.

### Exit gate of roadmap phase 5

Same commands, one contract more. From a cold start (kill any running emulator,
`adb emu kill`, wait for zero devices, then let the script boot the AVD itself):
publish (1), consumer compile (2), emulator smoke + evidence + validator (the
script — **29** scenarios green, `AND-001.json` + `AND-002.json` +
`AND-003.json` + `AND-004.json` + `AND-005.json` + `AND-006.json`), the same
transitive-resolution purity check (grep exit 1, redone cold), and the
no-device sanity tasks above. The roadmap checklist is carried by the
evidence itself:

- deterministic session outcomes — composition multi-step, Unicode, selection,
  stale revision and focus change — the host record `AndroidTextInputPortTest`
  proves the mapping against the REAL `RuntimeTextInputSession` (rebase
  strictly checked against the accepted snapshot, surrogate-pair-exact UTF-16
  offsets, `StaleRevision`/`InvalidRequest("text")`, suspend/resume around a
  preserved composition, silent finish), and
  `android-text-input-ime-key-deduplication` pins the same non-deduction at
  the real boundary;
- the channel absent on the current attach form —
  `android-text-input-channel-absent` (`onCreateInputConnection` → null,
  `onCheckIsTextEditor` → false, `textInput` = `Unsupported(TextInput)`, the
  on-row refusal); no editor or callback survives teardown because the bridge
  is internal machinery with no public activation path — nothing is ever
  installed;
- no IME commit also published as deduced key input — the exact-order silence
  of `android-text-input-ime-key-deduplication` for the text-carrying
  `KeyEvent` (`ACTION_MULTIPLE` + `KEYCODE_UNKNOWN`, out of table, dropped
  before any stimulus), guarded by the sentinel
  `android-text-input-no-synthesis`;
- the real-system IME and hardware gestures — the targeted manual procedure
  [manual/phase-5-text-input.md](manual/phase-5-text-input.md) (real Gboard
  composition, suggestion strip, mid-composition blur, hardware gestures; the
  complement the automation cannot stage);
- `AND-001` + `AND-002` + `AND-003` + `AND-004` + `AND-005` + `AND-006`
  together — 29 instrumented tests, one canonical JSON per contract, all from
  the same instrumented emulator run.

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
`AND-001` through `AND-006`), each pinned to the current HEAD
commit.

## Evidence mapping

The producer reuses the validator's canonical generator
(`GenerateContractEvidenceKt.main`, the same classes, main class and eight
arguments as the runtime/AppKit producers — no format reimplementation) with:

- the registry [contracts.tsv](../../registry/contracts.tsv) and the mapping
  [evidence.tsv](contracts/evidence.tsv) — the mapping is what binds each
  scenario/sentinel `evidenceId` to `KadreConsumerSessionTest` (`AND-001`),
  `KadreConsumerAttachContractTest` (`AND-002`),
  `KadreConsumerSurfaceContractTest` (`AND-003`),
  `KadreConsumerInputContractTest` (`AND-004`),
  `KadreConsumerWindowInteractionContractTest` (`AND-005`) or
  `KadreConsumerTextContractTest` (`AND-006`);
- the JUnit XML the consumer produced under
  `kadre/consumers/android/build/outputs/androidTest-results/connected/`;
- the current HEAD commit (the same `git rev-parse HEAD` rule the validator
  uses — a document pinned to a commit is stale by definition once HEAD moves).

Outputs: `build/contract-evidence/android/<engine>/AND-{001,002,003,004,005,006}.json`
(canonical), `build/test-results/android/<engine>/TEST-*.xml` (the JUnit copy
the validator cross-checks), `build/contract-evidence/android/engines.txt` (the
engines this run actually produced), and the gate copies
`build/contract-evidence/AND-{001,002,003,004,005,006}.json` that
`validateContractEvidence` reads.

## `androidContractsCheck` is deliberately not in `:kadre:check`

`:kadre:check` does **not** depend on `androidContractsCheck`: there is no
emulator on the current CI runners, and the aggregate requires a booted device
(the smoke re-runs the consumer instrumentation). Wiring the mandatory CI job
is phase 10 of the roadmap
(`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 10`).

## Phase limits (deferred guarantees)

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
admitted per field on the exact P1 on-row shapes, the `withAndroidView` lease
behind the double opt-in `@KadrePlatformApi` + `@DelicateKadreApi` (single
in-flight lease, host thread, early cancellation rejected before anything is
invoked, application exception propagated as-is), and `platformAccess`
`Supported` on the surface — while `WindowCapabilities.platformAccess` stays
`Unsupported(PlatformWindowAccess)`: no public member ever exposes a generic
`android.view.Window`.

Since phase 3, the surface also carries essential input observation and
delivery, in the **Activity form only**: a chainable `Window.Callback` wrapper
(the host callback is wrapped, never replaced, never reached through
reflection; every method forwards as-is, four input-side entry points deviate),
the 65-key HID key mapping (a key outside the table is forwarded without any
invented stimulus; keyboard observation lives only under focus, and so does
its consumption — an unfocused scroll key is never consumed nor observed), the
motion translation (contacts keyed on the native `getPointerId`, bounds gate on
DOWN
with gesture continuity — a declared limit: targeting is the bounds gate, not
host hit-testing, so overlapping siblings are attributed by bounds —, the lane
follows the tool type, stylus and eraser riding the single pointer lane per
D11/D12, hover scoped to the View bounds — a mouse elsewhere in the window is
not the surface's pointer — and dispatch coordinates converted to view-local
at the observation seam through the public `getLocationOnScreen` pair of the
View and the window's decorView, sampled at every dispatch), delivery through
the runtime's `RuntimeSurfaceInput` (buffered until
installed, `InputObservationChanged` carrying the facts actually wired, focus
loss → tracker reset without emitting + in-band `StateReset(FocusLost)` + text
session suspension, ingress overflow terminal from the runtime itself), and
`inputDefaultBehavior` committable `Supported({HostDefault,
SuppressWhenPossible})` with exact suppression of the nine document scroll keys,
press AND release (a consumed press without its release would leave the host an
orphaned release — the web suppresses the keydown alone because
`preventDefault` never removes events from the flow). Touch and pointer are
never consumed; `gestures` is decided `Unsupported(GestureInput)`; the IME and
raw input are not inferred from keyboard/pointer events (the text-input port
refuses `Unsupported(TextInput)`).

Since phase 4, the Activity primary window carries its command table with
effective readback — `title` → `Activity.setTitle`, `contentProtection` →
`FLAG_SECURE`, `fullscreen` `Borderless` → `WindowInsetsController` (API 30+;
an unconfirmed readback answers `Temporary(retryable = true)` field by field,
and under API 30 it cannot confirm so it answers that honestly; the `Windowed`
restore is a best-effort SDK cleanup on the headless proof emulator, where
`InsetsController.show` never flips the dispatched state back) — the whole
commit pass dispatched to the host thread (`AndroidMainThreadDispatch`, inline
when the caller is already there), mutation and readback atomic on it, one
bumped revision plus one stamped `PropertiesChanged` per state that really
moved; the eleven other fields are refused per field with their reason
(host-imposed: `outerPosition`, `contentSize`, `minimumSize`, `maximumSize`,
`resizable`, `decorations`, `systemButtons`, `level`, `transparency`,
`blurBehind`; no primitive: `icon`, `Exclusive` fullscreen) — an update
carrying only refused fields is `PartiallyApplied` with no revision and no
event. The surface commits `cursor` (the total `AndroidCursorIcons` table: 20
mapped TYPE_* constants, three named-unmappable with reasons, `Hidden` → the
transparent `TYPE_NULL` icon — never `pointerIcon = null` —, `Custom` refused
without a native call; both attach forms), `pointerCapture` in two steps
(API 26+; the primitive fired at admission, the state committed ONLY by the SDK
state listener — the View form refuses honestly: no listener channel exists
there), and installs the transient interaction engine (`handlerInteractions`
announcing exactly `{EnterFullscreen, ExitFullscreen}` plus
`{LockPointer, UnlockPointer}` at API 26+; synchronous interactions dispatched
BEFORE the ordinary stimulus of the same fact; single-use tokens;
`OpenWindow`/`BeginWindowMove`/`BeginWindowResize`/`AcceptDrop` never
advertised and refused before any native call; `armedInteractions` stays
`Unsupported(ArmInteraction)` — no platform arms).

Since phase 5, the IME bridge exists as internal machinery with no public
activation path: `AndroidTextInputPort` (capability always
`Unsupported(TextInput)` — the reducer consults the capability before `open`
and refuses on-row) and the pure mapper `AndroidImeEventMapper`, whose rules
(commit → Replace, three-fact composition with strict rebase, Replace-then-
terminal only for a differing final string, computed and bounded
`deleteSurrounding`, per-touche deletes never mapped) are proven against the
real `RuntimeTextInputSession` in the host suite `AndroidTextInputPortTest`,
and whose channel absence is pinned at the boundary by `AND-006`.
`textInput` stays `Capability.Unsupported(TextInput)` on both attach forms —
a recorded structural limit, not a deferral: the IME entry points
(`onCreateInputConnection`/`onCheckIsTextEditor`) require a View subclass,
Kadre never owns or subclasses the host's View (DESIGN §15.1),
`View.getWindow()` is `@hide`, and the SDK has no registration API. The
normative proposal — a host-delegated `InputConnection` factory through the
host's own View subclass (option A, via the existing `withAndroidView` lease
channel or new public API) or a new attach form (option B) — is parked for
the user, stated not implemented
([capabilities/android.md](../../../capabilities/android.md) §2 and §4). The
ACTION_MULTIPLE rule is written: a text-carrying `KeyEvent` (the only public
characters-bearing constructor forces `ACTION_MULTIPLE` + `KEYCODE_UNKNOWN`)
is out of the mapper table and delivers no observation — the wrapper's
primitive `Pressed` (focused) is dropped by the delivery gate before any
stimulus — never deduced into text. Gestures stay
`Unsupported(GestureInput)` — the same recorded limit: a recognizer is
host-owned or a normative hook, never deduced from the touch contacts. The
real-system complement is the manual charter
[manual/phase-5-text-input.md](manual/phase-5-text-input.md).

**The View form has no input observation — a structural, declared limit.**
`View.getWindow()` is `@hide`: a View-form attach has no `Window.Callback` to
chain, so no observation path exists. The input capabilities there stay
honestly `Unsupported` (an unwired install submits the "nothing is wired"
stimulus, which the reducer absorbs — never an empty promise). Routing through
the owner `ComponentActivity`'s window is the deferred option, but its
ownership is undesigned — in a multi-window world, which window carries which
session — and the decision belongs to the phases owning windows and the View
form ([capabilities/android.md](../../../capabilities/android.md) §4).

Everything else is deferred, never silently absent — the
authoritative list with the owning phase lives in
[capabilities/android.md](../../../capabilities/android.md) §4:

- **Later phases** — armed interactions (`armInteraction` stays
  `Unsupported(ArmInteraction)`: no platform implements the armed path, the
  handler token is the only interaction authority — the web holds the same
  refusal); `hitTesting` (still without a commit path, rejected per field);
  the multi-window/dialog refinement of the "interactive window" conjunct of
  `Active` (phase 4 delivered the primary-window commands without opening
  multi-window; `Active` ⇔ `RESUMED` still); View-form input observation AND
  window via owner routing (ownership multi-window undesigned — the phase 4
  close left the option untaken);
- **The `Window.close` decision is closed; the normative question is parked
  for the user.** `close` answers `Temporary(retryable = false)`, the interim
  on-row response for a structurally non-closable host window (the row admits
  only `Temporary`/`Platform`); Back is host-owned — never intercepted, no
  `finish()` from the Kadre side, no Back callback, no `CloseRequested` ever
  published (`respondToCloseRequest` therefore answers `Invalid("requestId")`),
  proven end-to-end by `android-window-back-host-owned`. What remains is a
  NORMATIVE question only the user can decide: whether `OPERATION-CONTRACTS`'s
  `Window.close` row gains `Unsupported(CloseWindow)` for structurally
  non-closable windows — that is an edit to the normative operation contracts,
  never taken in passing;
- **Phase 5 — decided, with the normative question parked for the user.**
  Text input/IME is no longer deferred: the channel is a recorded structural
  limit (`Unsupported(TextInput)`, the internal bridge proven, no public
  activation path) and the normative proposal — the host-delegated helper or
  the new attach form — is parked for the user (see the phase 5 paragraph
  above); gestures stay the decided `Unsupported(GestureInput)` recorded
  limit, a recognizer being host-owned or a normative hook;
  **Phase 6** — drag-and-drop;
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
