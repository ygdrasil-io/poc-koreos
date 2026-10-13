# Android emulator contract driver

This non-published JVM project runs the public Android host-attachment proof of
phases 0 through 9 on a real emulator. It is a contract driver, not an
implementation API: it forks the instrumented consumer build, executes its
scenarios on the attached device, and turns the JUnit output into the canonical
`AND-001` through `AND-010` evidence with the validator's own
generator. English
prose, like the Web driver README it mirrors; everything else in this phase is
documented in French in the sources.

## What the driver proves

Ten contracts, all active in the registry
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

**`AND-007`** — the phase 6 drop transfer machinery and channel qualification
proof (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 6 — Drag-and-drop et
ownership des transferts`). Three scenarios and one sentinel. The public
boundary scenario lives in the instrumented test class
`org.graphiks.kadre.consumer.KadreConsumerDropContractTest`; the two on-device
transfer scenarios live in
`org.graphiks.kadre.platform.android.AndroidDropDeviceContractTest`, carried by
the internal proof module `platform/android-devicetest` (see below), whose
testcase classes ride the consumer's own androidTest APK and report through the
same connected-run JUnit XML.

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-drop-channel-absent` | scenario | the drop RECEPTION channel does not exist on the delivered attach form, pinned three ways at the public boundary: `capabilities.dragAndDrop` stays `FeatureAvailability.Unsupported` (the drop does not even get a terminal `Capability` — the production surface builds with `dragAndDropAvailable = false`); the P4 interaction engine announces `handlerInteractions` `Available` WITHOUT `InteractionKind.AcceptDrop` (refused `Unsupported(Interaction)` before any native call — the AND-005 mechanics, re-confirmed here); and the silence proof — a REAL drag started by the app on its own View (`View.startDragAndDrop`, API 24+, the system drag session genuinely lives in the window: the window manager requires the touch-focus transfer, so the scenario plants a real injected touch — the `INJECT_EVENTS` harness path — and holds it during the listening window) delivers ZERO drop observations (`DropEntered`/`DropMoved`/`DropExited`/`Dropped`) in a bounded listening window; the capability is re-asserted unchanged after the drag |
| `android-drop-real-transfer-bounded` | scenario | the COMPLETE drop path over REAL content on the device: a file written into the app's own dir, its `file://` uri in a COPIED clip snapshot (same-app, a real `ContentResolver` reads a file uri of the app's own dir on API 24+ without grant or provider — cross-app would need a `FileProvider`; the test itself is the probe: every byte flows through the real resolver, an inadequate uri would fail the scenario), the REAL Task-1 source opened by the PRODUCTION resolver opener, the Task-2 reducer construction (`dragAndDropAvailable = true` — the parked normative decision), and the exact chain the future glue will deliver: `presentDrop` → `acceptDrop` → `DropPerformed` → `claimTransfer` → `collectBytes`; the FULL read returns bytes IDENTICAL to the file (chunks ≤ `maxDropChunkBytes`), the BOUNDED read returns the exact prefix and fails exactly `ResourceLimitExceeded(DropItem, maxBytes)` (an unknown-size read may deliver a prefix before failing, DESIGN §10.4), through a FRESH offer on the same real file — uri items are single-use |
| `android-drop-stream-released-on-close` | scenario | the gate's resource sentinel: a stream OPEN with its first chunk read, the collector suspended mid-read → `close()` on the owner → the stream is closed EXACTLY ONCE (behavioral proof through the opener's wrapper — the Task-1 host-side close counter does not exist here) and a subsequent read answers exactly `Closed(DropItem)` |
| `android-drop-no-public-channel` | sentinel | a build that advertises or wires a public drop reception channel (capability flipped without the normative decision, `AcceptDrop` advertised, or a drag observed through the silence) kills `androidDropChannelAbsent` alone |

**The drop decision: a recorded structural limit, and the normative proposal
parked for the user.** Phase 6 delivers the CONTENT side as internal machinery
and records the RECEPTION channel as a structural limit — the same shape as the
phase 5 text-input decision, a recorded limit and NOT a deferral (the web
precedent, authorized by `BACKEND-CAPABILITIES.md` §4). The causal chain is
structural: `View.setOnDragListener` is a SINGLE silent-replacement slot (never
used — the phase 3 discipline for single-slot listeners), `View.onDragEvent`
is `protected` (subclass-only, and Kadre never owns or subclasses the host's
View, DESIGN §15.1), and `Window.Callback` has no drag hook (probed against
android-35). The silence is proven at the real boundary by
`android-drop-channel-absent`. The content side is proven:
`AndroidDropTransferSource` translates a COPIED clip snapshot into common
offers (no `DragEvent`/`ClipData`/`ContentResolver` crosses publicly; mimes
canonicalized lowercase-or-dropped; an item with no readable content dropped
whole; uri items `SingleUse`, pure-text items `Replayable`; sizes always
unknown), bounded reads honor `maxBytes` (exact
`ResourceLimitExceeded(DropItem, maxBytes)`), a resolver refusal — all THREE
shapes: `null`, a throw, a read `IOException` — honestly becomes the persistent
`PlatformFailure(Android, "drop", "resolver-refused")`, so a missing permission
NEVER becomes a fake readable drop, and streams are closed exactly once,
released with the owner. The drop contract itself runs through the REAL
reducer in test construction (`RuntimeSurfaceInput` built with the exact
`installSessionConfiguration` arguments, only `dragAndDropAvailable` flipped):
single-active presentation (a second `presentDrop` terminates the first offer
`LeftSurface`, its source closed with it), `acceptDrop` through the P4
synchronous interaction shape, single-winner claim (`AlreadyInUse(DropTransfer)`),
the `maxConcurrentDropTransfers` budget (`ResourceLimitExceeded(DropTransfer, n)`
until close frees it), `ClaimTimedOut` on the policy timeout (source closed,
budget freed), and owner teardown → `OwnerClosed` with transfers kept closed
(`Closed(DropTransfer)` on re-read). Production stays honest: the flag is
`false`, pinned by the anti-regression host record
`AndroidDropContractTest.productionStaysUnsupported`.

**The normative proposal is PARKED for the user — stated, not implemented.**
Option A: a host-delegated `OnDragListener` (the HOST sets the listener on its
own view and delegates into Kadre — reachable through the existing
`withAndroidView` lease channel or as new public API, mirroring the text-input
option A). Option B: a new attach form. Each with its implications, written in
[capabilities/android.md](../../../capabilities/android.md) §2 and §4. Related,
and parked in the SAME future decision: arming `AcceptDrop` requires a
delivered `DropOfferId` — the id is opaque with an internal constructor, it can
only come from a delivered offer, and no offer can be delivered without the
reception channel — so the arming design belongs to that decision too.

**The devicetest module.** The two on-device scenarios need a REAL
`ContentResolver` — a JVM host test has none — and the runtime-friend
construction (the Task-1 source + the Task-2 reducer) cannot live in the
consumer, which compiles against the PUBLISHED artifacts where those internals
are invisible. The internal module `platform/android-devicetest`
(`org.graphiks.kadre.internal` group, `androidTestImplementation`-only, never
consumed by production) is the friend compilation of the three concerned
modules — the exact mirror of the `androidHostTest` friend wiring — and
publishes an AAR the consumer consumes `androidTestImplementation`: the
scenario classes travel INSIDE the consumer's androidTest APK and run under ITS
connected runner, so the JUnit XML of the evidence chain stays the consumer's
and the driver is unchanged.

**The real-system complement.** The real transfer is already automated
(`android-drop-real-transfer-bounded` runs on-device through the real
resolver), so the phase gate needs no manual charter. A real CROSS-APP drag is
what a manual pass would add, if one is ever needed, and it cannot be staged by
single-app instrumentation: it requires two apps — a sender exercising
`startDragAndDrop` with real content shared through a `FileProvider`
(`FLAG_GRANT_READ_URI_PERMISSION`: cross-app, the same-app `file://` uri of the
automated scenario no longer suffices), a second app as the drop target, and
the system drag UI across activities. Until the parked reception decision
lands, the expected Kadre-side observation of such a drag remains the recorded
silence — the manual pass would qualify grant propagation and the sender side,
not a Kadre reception.

**`AND-008`** — the phase 7 display, device, gamepad inventory and memory
pressure proof (source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 7 — Displays,
devices, gamepads et pression mémoire`). Six scenarios and two sentinels, all
in the instrumented test class
`org.graphiks.kadre.consumer.KadreConsumerDisplaysContractTest`; every
scenario reads PUBLIC state only (`KadreScope.displays` / `.devices` /
`.lifecycle` / the primary surface's input), so the class compiles against the
PUBLISHED artifacts like the rest of the consumer's androidTest sources.

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-display-inventory-real` | scenario | the published `DisplayManager.state` reflects the REAL emulator display, fact-by-fact re-pinned against the scenario's own SDK read of `DisplayManager.getDisplay(DEFAULT_DISPLAY)`: type `Physical`, real bounds at the origin, `workArea` NULL (Android has no desktop — the never-invent-geometry pin), the SDK's own modes and current mode, the observed scale factor; `requestAccess` carries the same honest inventory |
| `android-devices-inventory-and-kinds` | scenario | the published `DeviceManager.state` carries one device per REAL `InputDevice.getDeviceIds()` entry, the SDK's exact names, and the kinds of the normative sources table (identity bits, keyboard → mouse → touchscreen → touchpad → stylus order, gamepad/joystick carved out to `Other`); the emulator's virtual keyboard (`Virtual`, sources KEYBOARD\|DPAD) is a `Keyboard` — its NEGATIVE SDK deviceId (`VIRTUAL_KEYBOARD = -1`) rides the unsigned-image runtime key (`inputDeviceKey`), pinned host-side by `AndroidInputDevicePortTest.negativeVirtualDeviceIdTranslatesToNonNegativeKey` |
| `android-gamepad-empty-inventory-honest` | scenario | with no gamepad attached, the inventory states `gamepads = 0` and HOLDS it over a bounded observation window (zero `GamepadAdded`), the web `web-gamepad-empty-inventory-honest` precedent; the emptiness is verified honest against the SDK itself — no current device id carries the gamepad/joystick sources — and since effects capabilities live only INSIDE a published Gamepad snapshot, zero gamepads means zero effects announcements |
| `android-memory-pressure-real-mapping` | scenario | the REAL trim delivery: `UiAutomation.executeShellCommand("am send-trim-memory <package> RUNNING_MODERATE")` — the instrumentation's shell identity forces the system_server decision (`setProcessMemoryTrimLevel` → `scheduleTrimMemory` → `ActivityThread.handleTrimMemory` → the registered callbacks) — maps exactly `Moderate` on the session's `KadreLifecycle.signals`, then `RUNNING_CRITICAL` exactly `Critical`; the sends ride the command's own escalation constraint (a lower-or-equal re-send is refused by the system_server), so the levels are spent 5 → 15 once each, in order |
| `android-memory-background-never-maps` | scenario | the background-never rule pinned two ways with the real commands the tool accepts: `am send-trim-memory <package> COMPLETE` and `HIDDEN` (the two background-level names of the AOSP switch) go out with the instrumentation's shell identity — the system_server decision, never the test's. On the API 35 emulator the command ACCEPTS a background name on the foreground app (empty merged output; the "Unable to set a background trim level on a foreground process" refusal of older AOSP trees is gone there — what can still refuse is the escalation constraint, a lower-level re-send), and THE CONTRACT IS THE SILENCE AFTER EACH SEND: a background level accepted and really delivered crosses `ActivityThread.handleTrimMemory` and the kadre source, whose mapping never speaks it. The receiving chain is proven alive by a positive control (`TRIM_MEMORY_RUNNING_MODERATE` through `Application.onTrimMemory`, the exact Application leg `ActivityThread.handleTrimMemory` dispatches to), and all four SDK background constants (UI_HIDDEN, BACKGROUND, MODERATE, COMPLETE) traverse that same leg in TOTAL silence — visibility loss is not pressure |
| `android-raw-input-noncall` | scenario | the P3 non-call proof re-pinned: `requestRawInput` answers `Failure(Unsupported(RawInputAccess))` — the taxonomy names the operation, no native primitive is reached — and the published `capabilities.rawInput` refuses the same way BEFORE the call |
| `android-display-no-invented-workarea` | sentinel | a build that publishes bounds as workArea (invented desktop geometry) kills `androidDisplayInventoryReal` alone |
| `android-memory-no-background-signal` | sentinel | a mapping that turns a background trim level into a signal kills `androidMemoryBackgroundNeverMaps` alone (the shell path may really deliver a background level and the Application leg replays all four SDK constants — only the receiving chain could ever speak, and the mapping never does) |

**`AND-009`** — the phase 8 capture, consent and frames proof (source
`ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 8 — Capture, consentement et frames`).
Six scenarios and two sentinels. Five scenarios live in the instrumented test
class `org.graphiks.kadre.consumer.KadreConsumerCaptureContractTest` (public
state only: `KadreScope.capture`, the primary surface); the sixth
(`android-capture-source-refused`) names a port internal and lives in the
friend module class `org.graphiks.kadre.platform.android.
AndroidCaptureDeviceContractTest` (the AND-007 precedent). THE CONSENT IS REAL:
the system MediaProjection dialog is automated with the instrumentation's
`UiAutomation` shell identity. The PRIMARY path is accessibility: the
`UiAutomation` reads the INTERACTIVE windows
(`FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, set once — the systemui dialog is another
process's window) and performs `ACTION_CLICK` on the button's node, CLICKABLE
nodes first (the API 35 dialog title "Start recording or casting with…" matches
the positive pattern, and clicking a title does nothing); when the label sits
on a non-clickable TextView (the API 35 app-selector line "Camera") the click
carries to the clickable ANCESTOR (the row). Progress is verified after every
click — the result arrives or the node disappears (the API 35 flow continues
through the app-selector screen before the projection starts). When a click
does nothing, the FALLBACK presses the SAME button with a shell `input tap` at
the node's SCREEN bounds — the shell identity is the only one that injects a
touch INTO the systemui dialog. The interactive-window dump is the scenario's
oracle: a button that is never pressed fails the scenario quoting the attempted
labels and the current hierarchy. One consent re-attempt is made when the
pressed state still reads `Denied` (a click can miss under load — a real
automation difficulty fails the scenario's own assertion, never masked); the
HOME window is rendered after every scenario (the gate's focus-sensitive
classes must not inherit the projected app in the foreground), and
`POST_NOTIFICATIONS` is granted by shell in the FGS scenario (the documented
notification exists otherwise silently). The Kadre-owned foreground
service (`mediaProjection`) is the documented ruling P8-0 decision: the
library declares it, starts it before `getMediaProjection` (the API 34+ order
consent dialog → FGS startForeground → projection), and stops it at the
session terminal — the capture notification appears during HostChoice sessions.

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-capture-surface-stream-bounded` | scenario | the REAL `PixelCopy` of the attached SurfaceView (content actually rendered by the app: a red canvas posted into the holder): the full configuration (revision 0, the view's REAL size) is published as `Streaming` BEFORE the first frame; frames flow bounded, each carrying the drawn content (pixel (5,5) is the drawn red); `requestStop` → `Stopped(Requested)` and ZERO frames follow the terminal |
| `android-capture-consent-denied-stateful` | scenario | the REAL dialog, "Don't allow" pressed by the UiAutomation → `requestPermission` answers a STATEFUL success (`Denied(canRequestAgain = true)` on BOTH scopes, CONTRACTS :64 — never a failure); a following `open` answers the permission refusal without a new prompt |
| `android-capture-token-single-use` | scenario | consent granted (real "Start now") → the session streams real MediaProjection frames; the state readback (`refreshSources`) NEVER prompts (guarded by the sentinel below); a second `open` without fresh consent answers `PermissionDenied(CaptureScreen)` (the grant was consumed); a NEW consent (real dialog again) arms a second live session |
| `android-capture-fgs-lifecycle` | scenario | the merged manifest declares the library's capture service (the host declared NOTHING — the responsibility fact); the session is first driven to `Streaming` (the pump started; when the FGS start is refused — the projected app in the foreground — the open answers the row's `Temporary(retryable = true)` and the scenario replays ONE fresh consent into a new session, the contractual retry), the service RUNS at that moment (`ActivityManager.getRunningServices` on the own process — the honest read; the P8-0 order guarantees `startForeground` preceded the first frame) plus the minimal "Screen capture" notification posted; and it is STOPPED at the terminal (service gone, notification withdrawn) |
| `android-capture-source-refused` | scenario | the REAL production port at the device's real API level answers `reserve(Source(...))` → `Unsupported(CaptureOpen)` BEFORE any seam (zero seams wired — a structural refusal proven on device); sources stay `HostPickerOnly`, `refreshSources` is the honest no-op, a consent request without its seam answers the nominal `Unsupported(CapturePermission)` |
| `android-capture-late-attach-consent` | scenario | the activity is RESUMED BEFORE the attach; consent through the direct registry (no LifecycleOwner) works on the REAL androidx registry — the kadre-old anti-pattern (`ComponentActivity.registerForActivityResult` THROWS after STARTED) is corrected on device, not only host-side |
| `android-capture-no-prompt-on-readback` | sentinel | a build whose state readback launches the consent dialog kills `androidCaptureTokenSingleUse` alone (the mutant dialog wedges the port's consent path, so the scenario's second consent answers the consumed interaction instead of a fresh grant) |
| `android-capture-fgs-stopped-after-terminal` | sentinel | a build whose terminal skips the FGS stop kills `androidCaptureFgsLifecycle` alone (the service-outlives-session assertion) |

**The API 34+ consent order is load-bearing.** The grant returned by the
system dialog is NOT consumed at consent time: `getMediaProjection` legally
runs only AFTER the `mediaProjection` foreground service has called
`startForeground` (the official order dialog → FGS → projection); the port
stores the raw grant and the stream start consumes it right after the FGS-ready
signal. Closing the port during the dialog fails the pending request
`Closed(Host)` immediately; closing between reserve and start fails the start
before any live projection exists. Concurrent sessions share the single
service through a process-wide ownership refcount (the terminal of the first
session must not kill the FGS under the second's live projection).

**The emulator limits the run honestly records.** Three facts are bound to the
API 35 proof engine and are documented, never hidden:

- **The consent mode delivered by the emulator is "A single app."** The API 35
  dialog opens in single-app mode (its whole-display spinner is not reachable
  through the accessibility tree), the flow continues through the
  app-selector screen ("Camera" — an independent, continuous-viewfinder
  source), and the projected frames stream THAT task. The contractual facts —
  real system consent, single-use token, real frames — do not depend on the
  mode; a host expecting a WHOLE-DISPLAY mirror must drive the spinner, which
  is a [verify-device] item for real hardware (the manual charter
  [manual/phase-8-capture.md](manual/phase-8-capture.md) carries the
  procedure). The instrumentation itself is the projected app in scenarios 1
  and 3's second consent — the self-record shape the mode implies.
- **The `createVirtualDisplay` fallback is NPE-shaped.** The production seam
  tries the historical `AUTO_MIRROR` form first (the whole-display mirror of
  the official sample); an APP-CONTENT projection — what the "A single app"
  consent yields — refuses that flag with a coarse framework NPE, and the
  catch is deliberately BROAD: ANY `NullPointerException` thrown by the
  mirrored form downgrades the same call to the FLAGLESS form (the framework
  then poses its own `OWN_CONTENT_ONLY` for the session). A `null` return from
  either form fails the start (`PlatformFailure`); the fallback covers ONLY
  this NPE failure mode — a real device in entire-screen mode takes the
  historical `AUTO_MIRROR` path untouched.
- **The FGS start refusal is the row's `Temporary`.** An `open` whose
  foreground start is refused (`startForegroundService` throwing, or the
  service never announcing `startForeground` within the 5 s budget — the
  projected app being in the foreground is the observed shape) answers
  `TemporarilyUnavailable(retryable = true)`: the grant of that open was
  consumed by its reservation, so the contractual retry is a NEW consent into
  a NEW session — the FGS scenario replays it once; the token-single-use
  scenario instead relies on the background grace (2/2 full runs green).

**The honest-availability transitions the run carries.** The four capabilities the phase
delivers move from the matrix's C (deferred) to their real published values after a real
install, read in the same run: the display `enumerationCapability` is
`Supported(Unit, Available)` (the `DisplayManager` is structurally observable — multi-cast
listener, no permission, no gesture); the device inventory publishes the system's real
`InputManager` enumeration and the gamepad inventory publishes `gamepads = 0` and HOLDS it
(the emulator has no gamepad — the emptiness is verified honest against the SDK itself, not
vacuous); `lifecycle.capabilities.memoryPressure` reads `Available` once the process-wide
source is installed by `Application.registerComponentCallbacks` (the transition pinned
host-side by `AndroidMemoryPressureTest.installPublishesAvailableTransition`, the install
failure `Unavailable(PlatformFailure(Android, "memory-pressure", "source-exception"))` by
`.installFailureIsUnavailable`); `rawInput` stays `Unsupported(RawInputAccess)` — decided
separately and refused before and at the call.

**The hardware limits the emulator cannot stage — the manual charter.** The proof engine has
exactly one `Physical` display and no gamepad: everything a REAL device adds is outside the
deterministic evidence by construction, and the register states it honestly (`gamepads = 0`
held, one display). The hardware guarantees — a real USB/BT gamepad (hot-plug, real rumble
motors, disconnect during an effect), a second real display (the Developer options
"Simulate secondary displays" Overlay), and what the emulator cannot prove at all — have
their targeted manual procedures in
[manual/phase-7-displays-devices.md](manual/phase-7-displays-devices.md), and per
`TEST-STRATEGY.md` the material guarantees pass on the DECLARED device pool
(« le pool de devices déclaré supporté ») when one is qualified. The phase 7 gate needs
no manual pass: the roadmap checklist items are carried by the evidence itself.

## Running the gate locally

Prerequisites: an Android SDK with the **AVD `Kadre_API_35`** (system image
arm64-v8a, API 35) and `ANDROID_HOME` set (or `sdk.dir` in the root
`local.properties`). The driver needs **no Android plugin** — it is a pure JVM
module that shells out to `adb` and forks `gradlew`.

Cold start — kill any running emulator first (`adb emu kill`), then let the
script boot the AVD itself (headless: `-no-window -no-audio -no-boot-anim -gpu
swiftshader_indirect`, log at `/tmp/kadre-emulator.log`) and wait for
`sys.boot_completed == 1` before running anything. Boot can take minutes; the
wait is bounded (`KADRE_ANDROID_BOOT_TIMEOUT_S`, 300 s by default) and a device
that never completes boot fails the gate instead of hanging it:

```shell
scripts/test-kadre-android-contracts.sh
```

If a device is already attached, the script skips the boot and uses that
device instead; pass a serial to pin it
(`scripts/test-kadre-android-contracts.sh emulator-5554`, or export
`ANDROID_SERIAL`). The selection itself is validated first
(`scripts/test-kadre-android-device.sh`): an explicit serial must report the
exact adb state `device` (offline/unauthorized are refused before Gradle), and
without a serial exactly one eligible — booted — device must be attached, any
ambiguity being refused. The Gradle run inside is bounded by a portable 900 s
watchdog (`run_with_watchdog`, GNU `timeout` is absent on stock macOS): the
client runs in its own process group (`setsid`, or a backgrounded subshell
plus a `pgrep -P` walk where `setsid` is unavailable) and receives `SIGTERM`
at expiry with exit code 124, escalated to `SIGKILL` for survivors — the
Gradle daemon and its descendants no longer outlive the killed client (the
phase-0 "daemon survives" note is obsolete). The gate task is
`:kadre:contracts:validator:androidContractsCheck`: registry validation plus
Android contract evidence validation against **`AND-001` through `AND-010`** —
one canonical JSON per contract, all from the same instrumented emulator gate
(48 consumer instrumented tests plus the compose-showcase sample's two — 50
total; the consumer count grew independently of the mapped scenarios:
`KotlinFacadeConsumerTest` and `JavaAndroidFacadeConsumerTest` each carry one
extra unmapped testcase beside the one evidence.tsv maps).

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

### Exit gate of roadmap phase 6

Same commands, one contract more. From a cold start (kill any running emulator,
`adb emu kill`, wait for zero devices, then let the script boot the AVD itself):
publish (1), consumer compile (2), emulator smoke + evidence + validator (the
script — **32** instrumented tests green, `AND-001.json` + `AND-002.json` +
`AND-003.json` + `AND-004.json` + `AND-005.json` + `AND-006.json` +
`AND-007.json`), the same transitive-resolution purity check (grep exit 1,
redone cold), and the no-device sanity tasks above. The roadmap checklist is
carried by the evidence itself:

- drag events translated to common offers, only copied snapshots, no public
  `DragEvent`/`ClipData` — the host record `AndroidDropTransferSourceTest`
  (the clip→offer translation rules: canonicalized mimes, items without
  readable content dropped whole, uri items single-use, text items replayable,
  sizes always unknown) and `android-drop-channel-absent` (the published state
  stays `Unsupported`, nothing public crosses, the real drag is a silence);
- single claim / budget / teardown — the host record `AndroidDropContractTest`
  (`presentedSingleActiveAndReplacedWithLeftSurface`,
  `acceptSynchronousThroughTheInteractionShape`, `claimSingleWinnerAndBudget`,
  `performedClaimTimedOut`, `teardownClosesOwnerClosed`,
  `productionStaysUnsupported`) and `android-drop-real-transfer-bounded`
  (claim, then bounded `collectBytes` on the real chain);
- permissions acquired at the right moment, bounded reads, resources released
  exactly once — `AndroidDropTransferSourceTest` (the three resolver-refusal
  shapes honestified to the persistent `resolver-refused` failure,
  close-exactly-once, reads off the lock), `android-drop-real-transfer-bounded`
  (the real read bounded by `maxBytes`, exact `ResourceLimitExceeded(DropItem)`),
  and the sentinel `android-drop-stream-released-on-close` (a stream kept after
  close is detected, exactly-once behavioral proof);
- a missing permission on a host View never becomes a fake readable drop —
  `AndroidDropTransferSourceTest` (`resolver-refused`, never a partial offer)
  and `android-drop-channel-absent` (the absent channel);
- two claimants never read the same offer — the single-winner claim record
  (`AndroidDropContractTest.claimSingleWinnerAndBudget`) plus the
  already-claimed guard of `AndroidDropTransferSourceTest`
  (`AlreadyInUse(DropTransfer)`);
- `AND-001` through `AND-007` together — 32 instrumented tests, one canonical
  JSON per contract, all from the same instrumented emulator run (the 29
  previous testcases plus the three drop ones: `androidDropChannelAbsent` in
  the consumer, `androidDropRealTransferBounded` and
  `androidDropStreamReleasedOnClose` from the `platform/android-devicetest`
  AAR riding the same androidTest APK).

### Exit gate of roadmap phase 7

Same commands, one contract more. From a cold start (kill any running emulator,
`adb emu kill`, wait for zero devices, then let the script boot the AVD itself):
publish (1), consumer compile (2), emulator smoke + evidence + validator (the
script — **38** instrumented tests green, `AND-001.json` through
`AND-008.json` — eight canonical JSONs), the same transitive-resolution purity
check (grep exit 1, redone cold), and the no-device sanity tasks above. The
roadmap checklist is carried by the evidence itself:

- the display inventory is the emulator's REAL display, never invented desktop
  geometry — `android-display-inventory-real` (fact-by-fact against the
  scenario's own SDK read; `workArea` null is the pinned refusal), guarded by
  the sentinel `android-display-no-invented-workarea`;
- the device inventory is the system's REAL devices with the normative kinds —
  `android-devices-inventory-and-kinds` (the virtual keyboard's negative SDK
  deviceId rides its unsigned-image runtime key, host-pinned by
  `AndroidInputDevicePortTest.negativeVirtualDeviceIdTranslatesToNonNegativeKey`);
- no gamepad is invented where the system reports none, and no effects
  capability is announced outside a real pad — `android-gamepad-empty-inventory-honest`
  (the `web-gamepad-empty-inventory-honest` precedent);
- memory pressure arrives from the system's own trim decision, mapped
  normatively — `android-memory-pressure-real-mapping` (MODERATE → `Moderate`,
  CRITICAL → `Critical`, exactly once each, in escalation order the
  system_server itself enforces);
- background levels NEVER map — `android-memory-background-never-maps` (the
  real `COMPLETE`/`HIDDEN` commands go out with shell identity and the rule is
  the silence after each send — on the API 35 emulator the command accepts the
  background name on a foreground app, and a delivered background level
  crosses `handleTrimMemory` without ever speaking; the receiving chain is
  proven alive by a positive control and stays silent for all four SDK
  background constants), guarded by the sentinel
  `android-memory-no-background-signal`;
- raw input stays a refused non-call — `android-raw-input-noncall`
  (`Unsupported(RawInputAccess)` before and at the call);
- `AND-001` through `AND-008` together — 38 instrumented tests, one canonical
  JSON per contract, all from the same instrumented emulator run (the 32
  previous testcases plus the six display/device/gamepad/memory ones of
  `KadreConsumerDisplaysContractTest`).

### Exit gate of roadmap phase 8

Same commands, one contract more. From a cold start (kill any running emulator,
`adb emu kill`, wait for zero devices, then let the script boot the AVD itself):
publish (1), consumer compile (2), emulator smoke + evidence + validator (the
script — **44** instrumented tests green, `AND-001.json` through `AND-009.json`
— nine canonical JSONs), the same transitive-resolution purity check (grep exit
1, redone cold), and the no-device sanity tasks above. The roadmap checklist is
carried by the evidence itself:

- refusal produces the exact state — `android-capture-consent-denied-stateful`
  (the real dialog, "Don't allow" pressed by the automation →
  `Success(Denied(canRequestAgain = true))` on BOTH scopes, never a failure;
  a following `open` answers the refusal without a new prompt);
- no new consent from a state readback — the `refreshSources` no-op is pinned
  host-side (`AndroidCapturePortTest.tokenConsumedOnceNoPromptOnReadback`) and
  guarded on device by the sentinel `android-capture-no-prompt-on-readback`
  (a readback that launches the dialog kills `androidCaptureTokenSingleUse`
  alone);
- the token is consumed once — `android-capture-token-single-use` (a second
  `open` without fresh consent answers `PermissionDenied(CaptureScreen)`, a
  NEW consent arms a second live session);
- zero frames after the stop — `android-capture-surface-stream-bounded`
  (`Stopped(Requested)`, then nothing follows); the buffer never outlives its
  lease is the host record `AndroidCapturePortTest.surfaceStreamReleasedExactlyOnce`
  (release exactly once across app stop, PixelCopy failure and collector
  cancellation; a start on the closed stream refused `Closed(CaptureSession)`),
  with `configurationPrecedesFrameAndNeverCopies` pinning the never-copy
  invariant and `surfaceDestroyedTerminatesFailedAndReleasesOnce` pinning the
  surface-destroyed terminal (`Failed(surface-size-unavailable)`, released once);
- the consent dialog is closed, not leaked — the host records
  `closeDuringInFlightConsentFailsTheRequestAndAbsorbsTheLateGrant`
  (immediate `Closed(Host)`, the late dialog result absorbed) and
  `closeBetweenReserveAndStartNeverCreatesTheProjection`;
- the system revocation is the exact terminal — the host records
  `AndroidCapturePortTest.serviceStartedBeforeVirtualDisplayAndStoppedAtTerminal`
  (the `onStop` system revoke → `Stopped(PermissionRevoked)`, FGS stopped) and
  `revocationDuringFirstFrameWaitFailsTheStartWithoutTouchingTheListener`
  (`PermissionDenied(CaptureScreen)`, the listener untouched); the REAL
  mid-stream revoke (the system cast stop) and a REAL rotation mid-stream are
  device physics the emulator cannot stage deterministically — the manual
  charter [manual/phase-8-capture.md](manual/phase-8-capture.md) carries both
  procedures, and the resize-reconfigure machinery they exercise is pinned by
  `resizeRepublishesReconfigureBeforeFrame`;
- the service responsibility is proven, not promised —
  `android-capture-fgs-lifecycle` (merged manifest, service up at `Streaming`,
  minimal notification, down at the terminal), guarded by the sentinel
  `android-capture-fgs-stopped-after-terminal`;
- the late-attach consent is real — `android-capture-late-attach-consent`
  (consent after RESUMED through the direct registry on the REAL androidx
  registry); the `Source` refusal before any seam —
  `android-capture-source-refused` (the friend-module scenario on the real
  production port);
- `AND-001` through `AND-009` together — 44 instrumented tests, one canonical
  JSON per contract, all from the same instrumented emulator run (the 38
  previous testcases plus the six capture/consent/frames ones of
  `KadreConsumerCaptureContractTest` and the friend-module
  `AndroidCaptureDeviceContractTest`);
- `AND-010` through the same gate — the phase 9 samples migration (below): the
  consumer run carries the facade-consumer scenarios, and a SECOND forked run
  executes the migrated compose-showcase sample's own instrumented APK
  (`androidComposeShowcaseSingleSession`, `androidLegacyTypesAbsent`); the
  producer globs BOTH APKs' XML into the same per-engine JUnit copy.

**`AND-010`** — the phase 9 samples-migration and negative-consumer proof
(source `ANDROID-IMPLEMENTATION-ROADMAP.md#Phase 9 — Façade Java, Compose et
migration des samples`). Three scenarios and two sentinels:

| evidenceId | kind | what it exercises |
|---|---|---|
| `android-facade-consumers-both-forms` | scenario | the Task-2 facade consumers run on the published umbrella against the real device — pinned by `KotlinFacadeConsumerTest.kotlinAttachKadreBothFormsExhaustiveOutcome`, which drives BOTH public `attachKadre` forms (Activity + View) to their exhaustive terminal outcome |
| `android-compose-showcase-single-session` | scenario | the migrated compose sample (`kadre/samples/android-compose-showcase`, against the PUBLISHED compose binding AAR): recomposition ×3 records ONE session identity; leaving the composition terminates it (a real window detaches the view before the dispose — the runtime's host stop, `Stopped(HostDetached)`, wins the race over the dispose's `requestStop`; both are the machine's documented host stops); re-composing recreates a NEW session that reaches `Running` |
| `android-legacy-types-absent` | scenario | the dex of the sample's executed APK (where the published `platform-android` AAR classes are merged) contains neither `AndroidKadreRuntime` nor the kadre-old `EventLoop` (exact FQNs; the kotlinx-coroutines `EventLoop` is not a false positive) — bounded by two coverage guards (the scan sees the real `platform.android`/`host.android` classes) |
| `android-facade-no-leak` | sentinel | a facade that leaks coroutine types into the Java surface kills `javaFacadeAttachObserveStopAwait` alone |
| `android-compose-no-double-attach` | sentinel | a binding that creates more than one session per host (recomposition or re-entry) kills `androidComposeShowcaseSingleSession` alone (the recorded identity set) |

The migrated sample modules themselves are `kadre/samples/android-hello-window`
(Activity-form attach on a plain View + a window-lifecycle console),
`kadre/samples/android-hello-touch` (the same plus `SurfaceInput` touch
observation) and `kadre/samples/android-compose-showcase` (the phase 9 binding
plus touch). They resolve the PUBLISHED umbrella (`org.graphiks.kadre:
kadre-android`) and the PUBLISHED binding (`org.graphiks.kadre.internal:
compose-android`) from the contract repository declared in the root settings —
never a project dependency — and grep zero kadre-old build references. Each
plain sample attaches ONCE with no retry on refusal (programming-error
semantics for a sample — the refusal is logged, never replayed). The old GPU
sample (`hello-triangle-android-capture`) is NOT migrated: the wgpu4k
renderer is sample responsibility, deferred with the phase 8 `CapturePort` as
the future primitive.

### Exit gate of roadmap phase 9

Same commands, one contract more and a second forked run. From a cold start
(kill any running emulator, `adb emu kill`, wait for zero devices, then let
the script boot the AVD itself): publish (1), consumer compile (2), emulator
evidence + validator (the script — **50** instrumented tests green — the 48
consumer testcases of the forked consumer run plus the compose-showcase
sample's two from the SECOND forked run — `AND-001.json` through
`AND-010.json`, ten canonical JSONs), the same transitive-resolution purity
check (grep exit 1, redone cold), and the no-device sanity tasks above. The
roadmap checklist is carried by the evidence itself:

- Kotlin AND Java compile against the PUBLISHED artifacts and drive both host
  forms — `android-facade-consumers-both-forms` (`KotlinFacadeConsumerTest`
  drives both public `attachKadre` forms to their exhaustive terminal
  outcome) and the INTEROP §9 "Java Android" consumer row, landed:
  `JavaAndroidFacadeConsumerTest` (attach, observer state, request stop, await
  the `CompletionStage`, inspect failure — the admission refusal surfaces as
  `KadreException`), guarded by the sentinel `android-facade-no-leak` (a
  facade leaking coroutine types into the Java surface kills it alone);
- Compose proves no double attach and no leak after dispose/recreation —
  `android-compose-showcase-single-session` (recomposition ×3 records ONE
  session identity; leaving the composition terminates the session — one of
  the machine's two documented host stops, `Stopped(HostRequested)` via
  onDispose or `Stopped(HostDetached)` when the real window detaches the view
  first; re-composing recreates a NEW session that reaches `Running`),
  guarded by the sentinel `android-compose-no-double-attach`;
- every historical Android usage migrated or its removal explicitly
  documented, no hidden `kadre-old` dependency — the three migrated sample
  modules resolve only the PUBLISHED artifacts and grep zero kadre-old
  references; `hello-triangle-android-capture` is the documented removal (see
  above); the negative consumer `android-legacy-types-absent` scans the dex
  of the EXECUTED sample APK (no `AndroidKadreRuntime`, no kadre-old
  `EventLoop` — exact FQNs, the kotlinx-coroutines `EventLoop` not a false
  positive), bounded by the two coverage guards;
- `AND-001` through `AND-010` together — 50 instrumented tests, one canonical
  JSON per contract, all from the same gate invocation (47 mapped scenarios
  and 21 sentinels across the ten contracts; the executed testcase count grew
  independently of the mapped scenarios — the facade-consumer classes carry
  two unmapped extras).

**The quick-reattach race the binding documents.** After a terminal detach,
a re-attach attempted WHILE the P1 reservation is still held (before the
effective termination releases it) is refused (`AlreadyInUse(Host)`) — and
the refusal leaves the binding's slot open with NO further attach event
incoming (the view is already in the window): the host stays session-less
until the NEXT detach/attach cycle, the only thing that re-triggers the
create. The binding recovers only on that next cycle; a machine-level retry
(a deferred re-arm) is a documented deferred refinement, and the samples add
no retry policy of their own.

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
`AND-001` through `AND-010`), each pinned to the current HEAD
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
  `KadreConsumerWindowInteractionContractTest` (`AND-005`),
  `KadreConsumerTextContractTest` (`AND-006`),
  `KadreConsumerDropContractTest` (`AND-007`),
  `KadreConsumerDisplaysContractTest` (`AND-008`) or
  `KadreConsumerCaptureContractTest` (`AND-009`) — the friend-module
  scenarios (`androidDropRealTransferBounded`, `androidDropStreamReleasedOnClose`
  for AND-007; `androidCaptureSourceRefused` for AND-009) are mapped to
  `AndroidDropDeviceContractTest` / `AndroidCaptureDeviceContractTest`, whose
  classes arrive in the same XML from the `platform/android-devicetest` AAR
  consumed `androidTestImplementation`; `AND-010` maps to
  `KotlinFacadeConsumerTest` and `JavaAndroidFacadeConsumerTest` (the
  facade-consumer scenario and its no-leak sentinel, in the consumer's own
  androidTest sources) and to `ComposeShowcaseContractTest` (the migrated
  compose sample's OWN instrumented APK — the SECOND forked run, whose XML
  joins the same per-engine JUnit copy);
- the JUnit XML the consumer produced under
  `kadre/consumers/android/build/outputs/androidTest-results/connected/`;
- the current HEAD commit (the same `git rev-parse HEAD` rule the validator
  uses — a document pinned to a commit is stale by definition once HEAD moves).

Outputs: `build/contract-evidence/android/<engine>/AND-{001,002,003,004,005,006,007,008,009,010}.json`
(canonical), `build/test-results/android/<engine>/TEST-*.xml` (the JUnit copy
the validator cross-checks), `build/contract-evidence/android/engines.txt` (the
engines this run actually produced), and the gate copies
`build/contract-evidence/AND-{001,002,003,004,005,006,007,008,009,010}.json` that
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

Since phase 6, the drop CONTENT side exists as internal machinery with no
public activation path: `AndroidDropTransferSource` translates a COPIED clip
snapshot into common drop offers (canonicalized mimes, uri items single-use,
pure-text items replayable, an item without readable content dropped whole,
sizes always unknown), reads are bounded (`collectBytes` honors `maxBytes`,
exact `ResourceLimitExceeded(DropItem, maxBytes)`, chunks ≤ `maxDropChunkBytes`),
the resolver refusal — `null`, a throw, a read `IOException` — is the persistent
`PlatformFailure(Android, "drop", "resolver-refused")`, never a fake readable
drop, and streams are closed exactly once, released with the owner. The drop
contract is proven through the real reducer in test construction only (single
active presentation with `LeftSurface` replacement, P4-shaped `acceptDrop`,
single-winner claim, the `maxConcurrentDropTransfers` budget, `ClaimTimedOut`,
owner teardown `OwnerClosed`); production builds with
`dragAndDropAvailable = false`, pinned by
`AndroidDropContractTest.productionStaysUnsupported`. The RECEPTION channel is
a recorded structural limit, not a deferral: `setOnDragListener` is a single
silent-replacement slot (never used), `onDragEvent` is `protected`, and
`Window.Callback` has no drag hook — a real `startDragAndDrop` drag on the
host's own view delivers zero observations, proven at the boundary by
`android-drop-channel-absent` (`AND-007`). The normative proposal is parked
for the user: a host-delegated `OnDragListener` (option A, via the existing
`withAndroidView` lease channel or new public API) or a new attach form
(option B); arming `AcceptDrop` requires a delivered opaque `DropOfferId`, so
its design belongs to the same future decision
([capabilities/android.md](../../../capabilities/android.md) §2 and §4).

Since phase 7, the display, device and gamepad inventories and the memory-pressure source
are live machinery, scoped to the session (the memory source is process-wide): the
`AndroidDisplayPort` publishes the COMPLETE `DisplayManager` inventory — the initial
snapshot published synchronously at install, every fire re-reading the whole inventory
(never a delta), types `Physical`/`Virtual` (the `FLAG_PRESENTATION`/`FLAG_PRIVATE` flags),
bounds = the real size at the ORIGIN, `workArea` `null` ALWAYS (Android has no desktop — the
geometry is never invented), the SDK's modes, correlation keys = the displayIds — and an
unmeasurable read answers `TemporarilyUnavailable(retryable = true)` which WITHDRAWS the
previous inventory, never a partial snapshot (the admitted unavailability state; the
`HostViewport` fallback was not needed). The `AndroidInputDevicePort` publishes the real
`InputManager` enumeration with kinds from the sources' identity bits (class bits stripped —
`SOURCE_TOUCHSCREEN` and `SOURCE_MOUSE` share `SOURCE_CLASS_POINTER`), the gamepad/joystick
carve-out to `Other`, and correlation keys = the unsigned image of the deviceId (negative
virtual ids included — the emulator's `VIRTUAL_KEYBOARD = -1` is a `Keyboard` keyed
non-negatively). The `AndroidGamepadPort` carries the kadre-old predicate CORRECTED to
identity bits (the literal old table flags every keyboard as a gamepad through the shared
`SOURCE_CLASS_BUTTON` bit; the consequence is assumed and documented: a DPAD-only device —
the TV remote — is no longer a gamepad, kadre-old flagged it only by accident), the state
ROUTED out of the ordinary key path (gamepad/joystick-source events leave `KeyChanged` the
moment a port is attached — the gamepad state never enters `SurfaceInput`; without a port
the ordinary path stays whole), descriptor and effects capability FROZEN at connection
(reconnection is a new connection: fresh facts, neutral state), disconnect neutralizing,
and effects per REAL device — API 31+ the device's `VibratorManager` managed rumble, API
26–30 the device's one-shot, below or without observable motors `Unsupported(GamepadEffect)`
pinned; the phone vibrator NEVER claims a gamepad's motors, `DualRumble` is the only offered
kind (the SDK names no per-role motors), a native refusal is
`PlatformFailure(Android, "gamepad-effect", "refused")`, and an effect never outlives the
session that asked. The View form has NO gamepad port: no `Window.Callback`, therefore no
state channel — the generic inventory stays honest there (a pad would read `Other`).
Memory pressure follows the AppKit model: ONE process-wide source installed through
`Application.registerComponentCallbacks(ComponentCallbacks2)` at the first session, reused
by later ones, NEVER closed by a session close, closed by the last living host's
termination; the normative mapping 5 → `Moderate`, 10/15 → `Critical`, background levels
(20/40/60/80) and off-table values NEVER speak (visibility loss is not pressure), the native
callback never runs host code in its own stack (the relay is posted to the main looper), and
an attach that cannot reach an `Application` attempts nothing — the default runtime
capability, never an invented `PlatformFailure`. Raw input stays the decided
`Unsupported(RawInputAccess)` with its non-call proof re-pinned at the boundary
(`android-raw-input-noncall`). The hardware itself — a real USB/BT gamepad, a second real
display — does not exist on the proof emulator; the honest inventory states it, the manual
charter [manual/phase-7-displays-devices.md](manual/phase-7-displays-devices.md) carries the
procedures, and per `TEST-STRATEGY.md` the material guarantees pass on the declared device
pool ([capabilities/android.md](../../../capabilities/android.md) §2 and §4).

Since phase 8, the capture targets are live machinery: `screen`/`window`
(`HostChoice`) is the REAL MediaProjection path — system consent per session
(the grant stored raw at consent, consumed ONCE by the stream start after the
FGS-ready signal), the stateful refusal `Success(Denied(canRequestAgain =
true))` never a failure, the system revocation the terminal
`Stopped(PermissionRevoked)`; `surface` is the REAL `PixelCopy` of the
attached SurfaceView (API 26+, the app's own rendered content, `FLAG_SECURE`
content redacted by the SDK — a limit, not a choice); `Source` and
`sourceEnumeration` are the structural refusals (nothing on Android enumerates
capturable surfaces before a consent — `Source` refused
`Unsupported(CaptureOpen)` before any seam, sources `HostPickerOnly`,
`refreshSources` the honest no-op; the web precedent). The SERVICE
RESPONSIBILITY DECISION IS CLOSED (the ruling P8-0): the `mediaProjection`
foreground service is KADRE'S responsibility — the library declares it in its
own manifest (merged), starts it before `getMediaProjection`, stops it at the
session terminal, and posts the minimal "Screen capture" notification — the
HOST DECLARES NOTHING, and the documented consequence is that the capture
notification appears during HostChoice sessions. The engine-bound limits are
the ones §AND-009 records: the "A single app" consent mode of the API 35
emulator (self-record shape — [verify-device] for a whole-display mirror on
real hardware, manual charter
[manual/phase-8-capture.md](manual/phase-8-capture.md)), the broad-NPE
`createVirtualDisplay` fallback (any NPE from the `AUTO_MIRROR` form
downgrades that call to flagless), and the FGS `Temporary(retryable = true)`
retry as a fresh consent into a fresh session; the real multi-session FGS
sharing on a physical device and a real mid-stream revocation/rotation remain
material [verify-device] items — the process-wide refcount and the
reconfigure machinery are proven host-side.

Since phase 9, the Java facade and the Compose binding are live wrappers over
the unchanged Kotlin surface: `KadreAndroid.attach` — the two INTEROP §4
statics verbatim (Activity form with an explicit `surfaceView`, View form with
an explicit `lifecycleOwner`), the `KadreResult.Failure` translated to
`KadreException` (the failure intact, never null) — and the
`KadreSessionHandle`: the opaque facade-allocated id (never the Kotlin
`SessionId`, the web model), the Java-safe snapshot, immediate-then-sequential
observers in registration order (a throwing consumer unregisters ITSELF and
reaches the host dispatcher's uncaught handler without touching the session),
THE stable terminal `CompletionStage` whose derived waiters cancel without
stopping the session and which survives `close()`, the motivated stop, the
idempotent close — with the zero-leak scan pinning that no coroutine type
crosses the facade, and the Java policy helpers
`KadrePolicies.defaultPolicy()/realtimePolicy()/recordingPolicy()` landed with
it (INTEROP §4's policy-letter sentence is true). The Compose binding
`KadreComposeHost` keeps ONE session per host: the attach waits for the
surface view's real attachment (the view itself is the trigger — Android
dispatches attach events top-down BEFORE attaching children), recomposition is
inert (creation captures held in unkeyed `remember`), composition exit closes
(`onDispose` → the host stop request), a terminal detach recreates on the next
attach (the runtime owns that stop, never doubled). The binding's documented
limit is the quick-reattach race (see the phase 9 exit gate above): after a
terminal detach, a re-attach while the P1 reservation is still held is refused
and NO further attach event fires — the host stays session-less until the next
detach/attach cycle; a machine-level retry is a deferred refinement. The
samples migration retires the kadre-old Android usages: three modules rebuilt
on the public surface and the PUBLISHED artifacts (`android-hello-window`,
`android-hello-touch`, `android-compose-showcase`), each plain sample
attach-once with no retry on refusal (programming-error semantics for a
sample), and the negative consumer proving the executed APK carries no
`AndroidKadreRuntime` and no kadre-old `EventLoop`; the GPU sample
`hello-triangle-android-capture` is NOT migrated — the wgpu4k renderer is
sample responsibility, documented as deferred with the phase 8 `CapturePort`
as the future primitive.

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
- **Phase 6 — decided, with the normative question parked for the user.**
  Drag-and-drop is no longer deferred: the content side is delivered internal
  machinery (proven on-device through the real resolver by `AND-007`) and the
  reception channel is the recorded structural limit; the normative proposal —
  the host-delegated `OnDragListener` (option A) or the new attach form
  (option B), plus the `AcceptDrop` arming design that requires a delivered
  opaque `DropOfferId` — is parked for the user (see the phase 6 paragraph
  above); a real cross-app drag (two apps, `FileProvider` + grant, system drag
  UI) is documented in the AND-007 section as what a manual pass would add;
- **Phase 7 — delivered; what remains is hardware, not a deferral of code.**
  The display inventory, the device/gamepad observation, the per-device effects,
  the memory-pressure source and the raw-input decision are live and proven by
  `AND-008` (see the phase 7 paragraph above). The remaining items are MATERIAL:
  a real USB/BT gamepad (hot-plug, real rumble, disconnect during an effect) and
  a second real display do not exist on the emulator — the honest inventory states
  it, the manual charter
  [manual/phase-7-displays-devices.md](manual/phase-7-displays-devices.md)
  carries the procedures, and per `TEST-STRATEGY.md` the material guarantees pass
  on the declared device pool (« le pool de devices déclaré supporté ») when one
  is qualified; a View-form gamepad state channel (no P3 wrapper there — the
  generic inventory is the honest shape) and raw input (decided
  `Unsupported(RawInputAccess)`, proven non-call) are recorded limits, not
  deferrals;
- **Phase 8 — delivered; what remains is consent-mode hardware, not a deferral
  of code.** The three capture targets are decided and live: `HostChoice`
  (`screen`/`window`) and `Surface` are `Supported` with their real paths
  (MediaProjection consent, PixelCopy), `Source`/`sourceEnumeration` are the
  recorded structural refusals, and the service responsibility is the closed
  ruling P8-0 (Kadre-owned FGS + minimal notification, the host declares
  nothing — see the phase 8 paragraph above). The remaining items are
  MATERIAL: the whole-display consent spinner and a real entire-screen mirror,
  a real mid-stream system revocation and rotation, and the real multi-session
  FGS sharing do not exist on the proof emulator in exercisable form — the
  manual charter
  [manual/phase-8-capture.md](manual/phase-8-capture.md)
  carries the procedures, and per `TEST-STRATEGY.md` they pass on the declared
  device pool when one is qualified; editor actions through the IME (phase 5
  text-input decision) stay parked with that decision;
- **Phase 9 — delivered; what remains are refinements, not the facade or the
  binding.** The Java facade, the Compose binding and the samples migration
  are live and proven by `AND-010` (see the phase 9 paragraph above). The
  remaining items: the quick-reattach race refinement (a machine-level retry
  after a refused re-attach — today the binding recovers on the next
  detach/attach cycle only), the migrated GPU sample's renderer
  (`hello-triangle-android-capture` stays unmigrated — wgpu4k is sample
  responsibility, the phase 8 `CapturePort` is the future primitive), real
  gamepad hardware (the phase 7 material item, unchanged), and the editor
  actions through the IME (parked with the phase 5 text-input decision,
  unchanged);
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
