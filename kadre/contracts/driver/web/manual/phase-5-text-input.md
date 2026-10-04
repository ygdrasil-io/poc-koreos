# Web Phase 5 — Text input, IME, touch and drag-and-drop manual charter

This is a short manual complement to the deterministic Phase 5 browser tests.
Its procedures cover the browser frontiers headless automation cannot honestly
produce: a real operating-system IME driving candidates and commit over the
delivered session, the mid-composition cancellation a real `Esc` performs, a
real multi-layout keyboard, the `touch-action` of the host element (the one
boundary that decides whether a native scroll cancels a touch contact), a real
OS drag-and-drop into the element, and the `contenteditable` element the v1
text-input contract deliberately does not address. It is informative; it
creates no validator evidence, it does not change any contract status, and it
is not wired into the contract validator. It never replaces an automated
proof, and nothing in it may be read as the capability register: the register
of this adapter's input features is
[capabilities/web.md](../../../../capabilities/web.md) (§2, §3.9-3.11), and it
declares the one engine the automated smoke really runs.

## Scope and prerequisites

Phase 5 covers the touch contacts, the text-input sessions and the incoming
drops of an attached host-owned element. The delivered boundaries are
normative in `DESIGN.md` §10.3 (IME), §10.4 (drag-and-drop), the phase-5
rewrites of §15.3, and in [the driver's Phase 5
limits](../README.md#phase-5-touch-text-input-and-drag-and-drop-limits); the
observations below exercise their edges against real devices, real IMEs and
real files. Kadre still creates no DOM node, still writes no style —
`touch-action` and `contenteditable` included — and the element the text
session observes is still one the host prepared and made editable.

Every procedure uses the existing public Web driver fixture selected by
`?scenario=`, except procedure 6, which stages an element no fixture scenario
prepares (and says so). Build both distributions and serve the static host of
[phase-1-lifecycle.md](phase-1-lifecycle.md) before starting:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserDistribution :kadre:contracts:driver:web:wasmJsBrowserDistribution
# then, from the temporary static host of the Phase 1 charter:
#   http://127.0.0.1:8080/js/index.html?scenario=text-input
```

Run both target smokes before the manual pass
(`jsBrowserSmoke` and `wasmJsBrowserSmoke`, `--rerun-tasks`). Their suites
cover the deterministic contract: `web-touch.spec.mjs` drives real touch
contacts through CDP in a touch-declaring browsing context,
`web-drop.spec.mjs` drives a real `DragEvent` of a store the fixture's host
prepared, and `web-text-input.spec.mjs` drives a real Chromium composition
with CDP `Input.imeSetComposition` plus the synthetic branches Chromium
cannot be scripted into (the accepted precedent of the phase-3 synthetic
`WheelEvent`). The procedures below may reveal an engine-specific issue, but
may not be used to fabricate contract evidence or to claim that an automated
branch has passed.

| Target | Automated prerequisite |
| --- | --- |
| Kotlin/JS IR | `rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke --rerun-tasks` |
| Kotlin/Wasm-JS | `rtk ./gradlew :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks` |

The observation surface is the same for every scenario. The fixture attaches
a host through the public API and republishes what the streams publish as
attributes of that host, so an attribute a procedure reads is a fact a
consumer of this API can read:

| Attribute | Scenario | What it carries |
| --- | --- | --- |
| `data-kadre-input-caps` | all | the capability cell, e.g. `keyboard=available pointer=available touch=available gestures=unsupported:gestureinput dragAndDrop=available textInput=supported rawInput=unsupported:rawinputaccess` |
| `data-kadre-input-events` | `touch`, `drop` | every `InputEvent` published, in order (`touch:started@(50,40):rev=N;touch:ended@...`) |
| `data-kadre-drop-offer-*` | `drop`, `drop-reject` | the states of the offer the surface presented, in order |
| `data-kadre-drop-default` | `drop`, `drop-reject` | the browser's own answer about each drag step's default (`enter=kept;over=prevented;...`) — the D-D3 claim made observable |
| `data-kadre-drop-read`, `data-kadre-drop-read-bounded` | `drop` | the bytes a claimed transfer delivered, chunk by chunk |
| `data-kadre-text-open`, `data-kadre-text-open-second` | `text-input`, `text-area` | the answer of the surface's `openTextInput` (`success`, `failure:alreadyInUse:textinputsession`) |
| `data-kadre-text-state` | `text-input`, `text-area` | the session state: `active:rev=0:composing=none`, `suspended:...`, `closed` |
| `data-kadre-text-events` | `text-input`, `text-area` | every `TextInputEvent` published, in order (`replace:(0,0)="X":rev=0`, `composition:(0,0)="きょう":sel=(3,3):rev=0`, `composition:end:rev=0`, `action:send:rev=0`) |
| `data-kadre-text-writeback-current`, `-stale` | `text-input`, `text-area` | the result of the fixture's own `updateSurroundingText` (`applied`, or the encoded failure) |

In the procedures below, `host` is the scenario's own element,
`document.querySelector('[data-kadre-host="<scenario>"]')` — for `text-input`
the element *is* the `<input>` the fixture prepared, and for `text-area` the
`<textarea>` — and a fixture command is dispatched with
`document.dispatchEvent(new Event("<command>"))` on `document`.

## Procedures

### 1. A real OS IME: activation, candidates, commit

Open
`http://127.0.0.1:8080/js/index.html?scenario=text-input` (replace `js` with
`wasm` for the Wasm-JS bundle) in a **non-headless** browser, wait for
`data-kadre-ready`, and open a session the way the fixture does:

```js
const host = document.querySelector('[data-kadre-host="text-input"]');
document.dispatchEvent(new Event("kadre-text-open"));
host.getAttribute("data-kadre-text-open");    // success
host.getAttribute("data-kadre-text-state");   // active:rev=0:composing=none
```

Switch the operating system to a real IME (Japanese Romaji, Chinese Pinyin,
Korean 2-Set — record which), focus the input with a real click, and type a
romaji or pinyin sequence. What the session publishes is the composition
lifecycle the IME itself performs, observed through the revision contract:

```js
host.getAttribute("data-kadre-text-events");
// composition:(5,5)="" :sel=(0,0):rev=0      — compositionstart at the caret
// composition:(5,5)="konnnitiha":sel=(9,9):rev=0   — every compositionupdate
host.getAttribute("data-kadre-text-state");   // active:rev=0:composing=(5,14)
```

Commit a candidate (space, then Enter, or the candidate window's own
selection) and read the two facts the commit produces — the browser's final
string differing from the composed text is the correction `Replace` the port
reports before the terminal end, and the element and the session state agree:

```js
host.getAttribute("data-kadre-text-events");  // ...;replace:(5,14)="こんにちは":rev=0;composition:end:rev=0
host.getAttribute("data-kadre-text-state");   // active:rev=0:composing=none
host.value;                                   // kadreこんにちは — the element shows the commit
```

The offsets are UTF-16 code units of the document the application accepted —
the same rule `DESIGN.md` §10.3 states — so a commit into a document
containing astral characters (emoji before the caret) is the reading to check
by hand: the ranges must count the surrogate pair as two. Record the IME, the
OS, the layout and the exact sequence. The composition the automated suite
drives through CDP (`Input.imeSetComposition`) is Chromium's own pipeline and
proves the same contract; what this pass adds is the IME's candidate window,
its conversion dictionary and its commit keys, which no automation stages.

### 2. Cancelling mid-composition with Échap — and what a real blur does

Keep the session of procedure 1 open with the IME active and composing. Press
**Échap** (`Esc`) and read:

```js
host.getAttribute("data-kadre-text-events");  // ends with composition:end:rev=0
host.getAttribute("data-kadre-text-state");   // active:rev=0:composing=none
host.value;                                   // the composed text is gone — the browser withdrew it
```

Two shapes exist, and the delivered port answers both without fabricating a
text fact. When the end event carries no data at all, the cancellation is the
terminal observation and nothing else
(`WebTextInputSurfaceTest.aCancelledCompositionEndsWithoutACommitLeavesNoCompositionBehind`).
A real browser, however, cannot deliver a null `CompositionEvent.data` — Web
IDL stringifies it — so the end carries the empty string, and the port reports
the removal the browser performed: a `Replace` the runtime refuses by its own
range check (the application never accepted the composition), followed by the
same terminal end
(`WebTextInputSurfaceTest.aCancellationTheBrowserPerformedReportsTheRemovalTheRuntimeRefusesAndStillEndsClean`,
scenario `web-text-composition-cancelled`). What the manual pass records is
which shape this browser actually sent and that the session survived it:
`data-kadre-text-state` reads `active` with no composition, and a
`kadre-text-writeback-current` command still applies and reaches the element.

A second reading belongs here because the plan deferred it to this charter:
**Chromium cancels its composition on a real blur.** Compose again and click
outside the element (or focus `[data-kadre-focus-outside]`): the browser
withdraws its own composed text from the element before the session's
suspension lands, so the element may already show the plain document while
`data-kadre-text-state` reads `suspended:...:composing=(...)`. The
composition-preservation arm of the suspension — the state's `composingRange`
surviving untouched across the round trip — is the Kotlin surface tests'
(`WebTextInputSurfaceTest.aFocusLossSuspendsWithTheCompositionPreservedAndARegainedFocusResumesIt`)
and the scenario `web-text-focus-suspends` in its no-composition arm; what a
real blur does to the *element's* text is the browser's own doing, and this
procedure is where it is observed and recorded. Refocus the element and press
a key: the resumed session serves it.

### 3. The host's `touch-action`: the boundary that cancels a contact

Open `?scenario=touch`, wait for readiness, and read what Kadre did *not*
write — the contract is that Kadre never styles the element:

```js
const host = document.querySelector('[data-kadre-host="touch"]');
getComputedStyle(host).touchAction;   // "auto" — the browser's own default; Kadre wrote nothing
```

With the host default in place, perform a real touch scroll over the element
(a finger, or the device emulator of the browser's own DevTools — the same
hardware trust the automated suite gets from its touch-declaring context):
the browser starts a native scroll and revokes the contact. What the surface
publishes is exactly that revocation, as reported and never compensated:

```js
host.getAttribute("data-kadre-input-events");  // ...touch:started@(...):rev=N;touch:cancelled@(...):rev=N
host.getAttribute("data-kadre-input-state");   // touches=0 — the reducer retired the contact
```

Then take the boundary back and repeat: from the console, give the *host* the
`touch-action: none` the host was always free to give, refresh the page (the
style is the page's, not Kadre's), and drive the same gesture — the contact
is no longer cancelled; its `pointermove` stream continues and ends with the
finger's real `pointerup`. The pair of readings is the whole recorded limit:
`touch-action` decides whether a native scroll may revoke a contact, Kadre
never writes it, and a cancellation the host let through arrives as
`TouchPhase.Cancelled` (`WebInputMapping.kt:345-347`,
`WebInputStimulus.kt:100-104`). Record the device, the browser and the
gesture.

### 4. A real multi-layout keyboard over the text session

Phase 3's procedure 4 proved the physical/logical split of the *key* path on
a non-US layout. This pass proves the same independence for the *text* path:
open `?scenario=text-input`, open a session, switch the OS layout (AZERTY,
QWERTZ, a Cyrillic layout — record which), and type. The `beforeinput` the
browser fires carries the layout's own characters, and the session's
observation is the edit they describe against the accepted revision:

```js
host.getAttribute("data-kadre-text-events");  // replace:(0,0)="физкультура":rev=0 — what this layout produced
host.value;                                   // the element shows the same characters
```

The typed letters are never re-keyed through the HID table — the text fact is
the browser's `beforeinput` payload, not a reconstruction from `keydown` —
which is why a layout that prints Cyrillic where QWERTY prints Latin still
produces an observation whose text is the Cyrillic, at the offsets the
document defines. What no pass may read into it is a composition claim:
without an active IME, a plain layout's keystrokes are `insertText`
`beforeinput`s, and the automated suite proves that shape on its own
(`web-text-replace-event`; note also that Playwright's `keyboard.insertText`
fires exactly that event — the plan's note that it does not is corrected by
the delivered spec).

### 5. A real OS drag into the element, outside headless

The automated suite drives a `DragEvent` of a `DataTransfer` the fixture's
host built — a real event object, but not a real OS drag. This procedure is
for a machine where one can be performed. Open
`?scenario=drop` (the accepting variant) in a non-headless browser, wait for
`data-kadre-drop-armed`, and drag a real file from the desktop (Finder,
Explorer, Files — record the OS) over the host:

```js
host.getAttribute("data-kadre-drop-offer-0");   // presented — the descriptors the OS reported, snapshotted at the entry
host.getAttribute("data-kadre-drop-default");   // enter=kept;over=prevented — the target activated only while the offer is held
```

Continue the drag into the host and release: the drop performs, the offer
becomes claimable, and the fixture's claim command reads the payload the OS
delivered — the first read of a real file through the bounded, copied
`collectBytes` path. Then record the boundaries the headless suite cannot
stage: a drag that *leaves* the element ends the offer (`dragleave`,
`data-kadre-drop-offer-*` closing on `exited`); a drag of plain text from
another browser tab arrives as the `Text` item its store named; a drop the
handler rejects (`?scenario=drop-reject`) leaves the browser its own default —
a file dragged onto a rejecting host is *navigated to* by the browser, the
visible proof that `preventDefault` is only called while an offer is held
(D-D3). Record the OS, the browser, the source application and the item
descriptors the entry snapshotted, including what the OS protected until the
drop (`sizeBytes` unknown at entry is the honest snapshot, not a failure).

### 6. `contenteditable` is out of the v1 contract, and what that means on a real page

The v1 text-input contract addresses `<input>` and `<textarea>` only (D-X3),
and no fixture scenario prepares a `contenteditable` host — the fixture
prepares the two element kinds the contract addresses
(`WebDriverFixture.kt:1530-1540`). The automated pins are
`WebTextInputSurfaceTest.anElementThatIsNotAnInputOrTextareaOpensASessionThatObservesNothing`
(a live session on a `<div>` that observes nothing) and
`.aWriteBackOnAnElementThatCannotCarryItAnswersClosedWithoutTouchingTheElement`
(the write-back refused `Closed(TextInputSession)`, the element untouched).
To observe the same boundary on a real page, stage a host of your own: take
any page that calls the public attach API, make its attached element
`contenteditable`, call `openTextInput` from the console of that page, and
read:

- the open **succeeds** — the capability is structural (D-X2); editability is
  the host's boundary, and the contract's refusal is never about the element
  kind at open time;
- typing into the element produces **no observation** — the port installs no
  listener on a non-addressable element
  (`WebTextInputPort.kt:189`): the session is active and silent;
- a `session.updateSurroundingText(...)` **closes the session** with
  `Closed(TextInputSession)` and answers the same failure from then on — the
  write-back *is* the contract, and nothing licenses a write of
  `textContent` or a selection the DOM does not address deterministically
  (`WebTextInputPort.kt:238-241`);
- the element's own DOM is untouched by all of it: no attribute, no style, no
  selection written by Kadre.

Record what the element kind you staged observed, and treat any observation
that appears as a finding, not a feature: silence on `contenteditable` is the
recorded v1 limit, and a future phase that widens the scope must reopen the
contract, not the silence.

### 7. Gestures: the recorded limit, and what a host could observe natively

`InputCapabilities.gestures` stays `Unsupported(GestureInput)`: no recognizer
exists in the runtime, the foundation or this adapter, and the browser offers
no recognition primitive whose fidelity Kadre could declare honestly (D-T2,
blessed by `BACKEND-CAPABILITIES.md` §5). There is no Kadre API to call here,
and a manual pass cannot create one; what this charter can record is the
native material a *host* — or a future adapter — would start from, so the
limit is documented rather than mysterious:

- Chromium exposes a pinch as a `wheel` event whose `ctrlKey` is true (the
  zoom gesture); a two-finger pan arrives as plain `wheel`s, and the long-press
  context gesture never reaches the page as a named event;
- WebKit exposes its own `gesturestart`/`gesturechange`/`gestureend` events,
  outside the standard and outside Chromium;
- the touch contacts themselves *are* delivered (§3.9 of the register), so
  the raw material of a gesture recognizer is in the model already — what is
  missing is the recognizer and the closed `GestureInput` contract of its
  output, not the input.

Any observation a pass makes with the browser's own gesture events is an
observation of the page, not of the surface: Kadre publishes no gesture state,
and `web-touch-no-gesture-claim` pins exactly that.

## Limits and diagnostic record

Record failures with the browser name, version and engine, the target and
bundle (JS IR or Wasm-JS), the operating system, the keyboard layout and IME
in use, the pointer/pen/touch hardware, the exact reproduction steps, the
readbacks above, and all console errors. Include for each composition
observation the IME, the composed string and the committed string, for each
cancellation whether the end event carried data and what shape the session
published, for each touch pass whether `touch-action` was the host's default
or the host's own `none`, and for each drag the source application and the
descriptors the entry snapshotted.

Do not infer support from one manual run, do not edit generated evidence, do
not add this charter or its observations to the validator, and do not extend
the declared engine of the register from one manual reading. Report the
observation alongside the deterministic target smoke result so that it can be
reproduced and turned into an automated test when the browser permits it.

## References

- [Capability register of this adapter](../../../../capabilities/web.md) — §2, §3.9-3.11
- [Web browser driver](../README.md) — Phase 5 limits and published availability
- [Web Phase 1 lifecycle charter](phase-1-lifecycle.md) — the static host and the target commands
- [Web Phase 3 input charter](phase-3-input.md) — the non-US layout procedure this charter complements
- [Web Phase 4 interactions charter](phase-4-interactions.md)
- [Kadre design](../../../../DESIGN.md) — §10.3, §10.4, §15.3
- [Operation contracts](../../../../OPERATION-CONTRACTS.md) — §1.1, §7
- [Web implementation roadmap](../../../../WEB-IMPLEMENTATION-ROADMAP.md)
