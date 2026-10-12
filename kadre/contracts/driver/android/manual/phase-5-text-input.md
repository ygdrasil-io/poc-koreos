# Android Phase 5 — Text input and gestures manual charter

This is a short manual complement to the deterministic Phase 5 emulator
evidence (`AND-006`). Its procedures cover the frontiers a headless emulator
run cannot honestly stage: a real operating-system IME (Gboard) with its
candidate window, suggestion strip and glide-typing physics, a real blur in
the middle of a composition, and real multi-finger gestures performed on
hardware. It is informative; it creates no validator evidence, it does not
change any contract status, and it is not wired into the contract validator.
It never replaces an automated proof, and nothing in it may be read as the
capability register: the register of this adapter is
[capabilities/android.md](../../../../capabilities/android.md) (§2, §4).

**The decision this charter operates under.** `textInput` stays
`Capability.Unsupported(TextInput)` on both attach forms — a **recorded
structural limit, not a deferral**. The IME entry points
(`onCreateInputConnection`/`onCheckIsTextEditor`) require a View subclass,
Kadre never owns or subclasses the host's View (DESIGN §15.1),
`View.getWindow()` is `@hide`, and the SDK has no registration API. The
internal bridge (`AndroidTextInputPort` + `AndroidImeEventMapper`) is proven
against the real `RuntimeTextInputSession` in the host suite
`AndroidTextInputPortTest`, but no public activation path exists; the
normative proposal (a host-delegated helper through the `withAndroidView`
lease channel or new public API — option A —, or a new attach form — option
B) is parked for the user. `gestures` stays `Unsupported(GestureInput)` for
the same recorded-limit reason. Procedure 1 is therefore what is true
**today**, on the delivered attach; procedure 2 is the procedure to run on a
**qualified host** once the user takes that decision; procedure 3 records the
gesture limit.

## Scope and prerequisites

Phase 5 covers the text channel and the gestures of the attached host View,
Activity form (the View form has no `Window.Callback` to chain — the phase 3
structural limit — so every procedure below runs on an Activity-form attach).
The rules the procedures check are normative in `DESIGN.md` §10.3 and
`OPERATION-CONTRACTS.md` §7 and are the ones the host suite and `AND-006`
already pin; what this charter adds is the physics no emulator stages.

| Prerequisite | Why |
| --- | --- |
| A real device (record manufacturer, model, Android version) or a **graphical** AVD | the proof engine `Kadre_API_35` runs headless with no IME worth driving by hand |
| A host app calling the public `attachKadre` (Activity form) — the contract consumer `kadre/consumers/android` is the ready-made one | every reading below is a fact a consumer of the public API can read |
| A debug dump of the public state — log `surface.input.state.value.capabilities`, the answer of `openTextInput`, and every item of the input and (if any) text event flows | the observation surface; Kadre never prints on its own |
| The gate green first: `scripts/test-kadre-android-contracts.sh` (29 instrumented tests, six JSONs, `AND-001`…`AND-006`) | the deterministic contract this charter complements |

Run the gate before the manual pass. The procedures may reveal an
engine-specific issue, but may not be used to fabricate contract evidence or
to claim that an automated branch has passed.

## Procedures

### 1. The recorded limit on the delivered attach — what is true today

1. Attach (Activity form) and read the capability:
   `surface.input.state.value.capabilities.textInput` must read
   `Capability.Unsupported(Unsupported(TextInput))` — before any tap, in every
   state (the value never moves; that immobility IS the recorded limit).
2. Call `openTextInput(TextInputConfig())`: the answer is the on-row refusal
   `Failure(Unsupported(TextInput))`, and no session exists.
3. Tap the attached View. Raising the soft keyboard is the **host's** call
   (Kadre never calls `showSoftInput`); on the base View
   (`onCheckIsTextEditor() == false`, `onCreateInputConnection == null` — the
   boundary facts `android-text-input-channel-absent` pins on the emulator)
   the framework has nothing to bind an IME to. Record what the device's IME
   actually does: never opens, flashes and closes, or (some OEM IMEs) opens
   with nowhere to deliver — all three are the same fact, the channel is
   absent. Any text the soft keyboard produces reaches **no** Kadre
   observation: the only channel a text event could ride is an open
   `TextInputSession`, and the refusal proves none exists.
4. Focus the attached View and type on a **hardware** keyboard (a physical
   keyboard, or an emulator with keyboard passthrough): keys arrive as key
   facts only, through the 65-key HID table, under the focus gate; a key
   outside the table is forwarded to the host without any invented stimulus.
   A text-carrying `KeyEvent` (`ACTION_MULTIPLE`, the only public
   characters-bearing constructor forces it plus `KEYCODE_UNKNOWN`) delivers
   no observation at all — never a deduced text fact.
5. Record: device, Android version, IME name and version, whether the
   keyboard opened, the capability cell, the exact refusal word, and the
   exact event list. Expectation: zero `TextInputEvent`, key facts only.

Any text observation appearing in this procedure on today's attach is a
**finding, not a feature** — file it against the decision, do not read
support into it.

### 2. The real system IME on a qualified host (post-decision procedure)

No host qualifies today: the qualification requires the View subclass the
parked decision would introduce (option A: the host's own View subclass
overrides `onCreateInputConnection`/`onCheckIsTextEditor` and delegates to a
Kadre-supplied `InputConnection` factory — reachable through the existing
`withAndroidView` lease channel or as new public API; option B: a new attach
form). Once the user takes the decision, run this pass on the qualified host;
the exact open command is whatever the decided API delivers, but the rules
below are the ones already proven against the real session — this pass checks
them against real IME physics:

1. **Activation.** Open the session the decided way; the `textInput`
   capability turns honest (the refuser's word is replaced, never a silent
   promise). Read the state: `Active` with the accepted document revision.
2. **Multi-step composition (Gboard glide typing, or a CJK pinyin layout —
   record the IME and version).** Focus the View, type: the session must
   publish the composition lifecycle — the start is an empty substitution at
   the selection, every update carries the composed span, the selection is
   the end of the composed text (never a fabricated document selection), and
   each event's `baseRevision` is the revision the application accepted.
3. **The suggestion strip.** Let Gboard auto-correct (the committed string
   differs from the composed one): the expectation is a
   `Replace` of the composition range by the final string, then the terminal
   composition end. Commit the composed text as-is (space on an
   uncorrected word): terminal end **only** — no Replace is fabricated for a
   string that changed nothing.
4. **Backspace in and out of composition.** During an active composition a
   delete (`deleteSurroundingText`) is silent — the composition is the
   observation, never an edit of its own. Outside composition it is computed
   from the current document against grapheme-cluster bounds (per-touche
   keycodes are never re-segmented — the web's honesty).
5. **Real blur mid-composition.** Press Home / open recents / tap another
   app while composing: the session suspends (`Suspended`) with the
   composition preserved — the composition-preservation arm DESIGN §10.3
   states, which the host suite pins. Refocus and type: the session resumes.
6. **Unicode adjacency.** Compose next to an emoji (astral plane): all
   ranges and selections are UTF-16 code units — a surrogate pair counts as
   two. Read the offsets by hand against the document the app accepted.
7. **Stale revision.** Hold the application's acceptance back (do not apply
   the accepted revision) and let the IME commit: the answer follows §7 —
   `StaleRevision` or `InvalidRequest("text")` — never a silent rewrite.
8. **Hardware coexistence.** With the session open, type on a hardware
   keyboard: key facts only; the text channel stays the IME's. A
   text-carrying `KeyEvent` delivers no text observation — the
   ACTION_MULTIPLE rule is normative and does not move with the decision.
9. Record everything: IME, version, the exact `TextInputEvent` sequence with
   revisions and ranges, and the session states between steps.

### 3. Hardware gestures — the recorded limit

`gestures` stays `Unsupported(GestureInput)`: no recognizer exists in the
runtime, the foundation or this adapter, and Android's
`GestureDetector`/`ScaleGestureDetector` are passive consumers that must be
fed from a View the host owns (kadre-old fed them from its own
Activity-owned View; today's Kadre owns no View — a recognizer is host-owned,
or a normative hook; the web holds the same recorded limit,
`capabilities/web.md` §2's `gestures` row). There is no Kadre API to call
here, and a manual pass cannot create one; what this procedure records is the
limit, not a claim:

1. On a real touchscreen, perform: a two-finger pinch, a two-finger pan, a
   long-press drag, and (with a stylus) a palm rest.
2. What Kadre publishes is the **raw material only**: the touch contacts
   with their stable per-gesture identities (`AND-004` pins exactly that) —
   no gesture state is ever published, in any state, and the host receives
   every event forwarded untouched, free to run its own recognizers on them.
3. Why automation does not cover it: the emulator instruments contacts, not
   recognizers — a recognizer's arbitration is host-side physics no emulator
   scenario stages, and the contacts themselves are already pinned by
   `android-input-multi-touch-stable-ids`. What is missing is the recognizer
   and the closed `GestureInput` contract of its output, not the input.
4. Record: device, gesture, the contact events observed, and the absence of
   any gesture-typed publication. Any gesture observation is a **finding,
   not a feature**.

## Limits and diagnostic record

Record failures with the device manufacturer and model, the Android version,
the IME name and version (procedures 1 and 2), the keyboard hardware, the
exact reproduction steps, the readbacks above, and all logcat errors from the
host app. Include for each composition observation the composed string and
the committed string, for each blur the session state before and after, and
for each gesture the full contact list. Do not infer support from one manual
run, do not edit generated evidence, do not add this charter or its
observations to the validator, and do not extend the declared engine of the
register (`emulator-api-35-arm64-v8a`, read from the attached device — never
hardcoded) from one manual reading. Report the observation alongside the
deterministic gate result (`scripts/test-kadre-android-contracts.sh`) so that
it can be reproduced and turned into an automated test when the platform
permits it.

## References

- [Capability register of this adapter](../../../../capabilities/android.md) — §2 (the `textInput` decision row), §4 (the parked normative proposal)
- [Android emulator contract driver](../README.md) — `AND-006`, the gate, the phase 5 limits
- [Web Phase 5 text input charter](../../web/manual/phase-5-text-input.md) — the mirrored precedent (real IME, real blur, gestures as a recorded limit)
- [Kadre design](../../../../DESIGN.md) — §10.3 (text sessions), §15.1 (the Android attach forms)
- [Operation contracts](../../../../OPERATION-CONTRACTS.md) — §7 (text input operations)
- [Android implementation roadmap](../../../../ANDROID-IMPLEMENTATION-ROADMAP.md) — Phase 5 (gate: « L'IME système réel et les gestures matérielles ont une procédure manuelle ciblée en complément des tests automatisés »)
