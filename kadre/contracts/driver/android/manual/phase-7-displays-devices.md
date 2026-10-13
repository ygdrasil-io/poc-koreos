# Android Phase 7 — displays, devices and gamepads manual charter

This is a short manual complement to the deterministic Phase 7 emulator evidence
(`AND-008`). Its procedures cover the frontiers a headless emulator run cannot
honestly stage: a real USB/BT gamepad with its hot-plug, its real rumble motors
and a physical mid-effect disconnect, and a second real display (the Developer
options "Simulate secondary displays" Overlay). It is informative; it creates no
validator evidence, it does not change any contract status, and it is not wired
into the contract validator. It never replaces an automated proof, and nothing in
it may be read as the capability register: the register of this adapter is
[capabilities/android.md](../../../../capabilities/android.md) (§2, §4).

**The decisions this charter operates under.** The display inventory, the generic
device inventory and the gamepad observation are **Supported** — the
`DisplayManager`/`InputManager` multi-cast listeners are structurally observable,
and the delivered ports read them. The gamepad EFFECTS are **Supported per real
device under API conditions** (API 31+ the device's `VibratorManager`, API 26–30
the device's one-shot, otherwise `Unsupported(GamepadEffect)`), and the phone's
vibrator is never a gamepad's motors. Raw input is the decided
`Unsupported(RawInputAccess)`, proven non-call — procedure 4 records that limit.
What the emulator cannot stage is the HARDWARE: the proof engine `Kadre_API_35`
has exactly one `Physical` display and no gamepad at all, so the deterministic
evidence reads an honestly empty gamepad inventory (`android-gamepad-empty-inventory-honest`,
the `web-gamepad-empty-inventory-honest` precedent) and one display
(`android-display-inventory-real`). Per the phase 7 gate wording, the material
guarantees pass on **the declared device pool** (« le pool de devices déclaré
supporté » — `TEST-STRATEGY.md` nightly pool, physical-device validation for any
promised capability not exercable on the emulator) once one is qualified —
until then, these procedures are the record
of what to run, and of what a run adds beyond the automated evidence.

## Scope and prerequisites

Phase 7 covers the display manager, the device manager and the gamepad manager of
an attached session — both attach forms observe displays and devices; the gamepad
STATE lives only on the Activity form (the View form has no `Window.Callback` to
chain, therefore no gamepad state channel — its generic inventory stays honest, a
pad would read `Other` there). The rules the procedures check are the ones the
host suites (`AndroidDisplayPortTest`, `AndroidInputDevicePortTest`,
`AndroidGamepadPortTest`) and `AND-008` already pin; what this charter adds is
the hardware physics no emulator stages.

| Prerequisite | Why |
| --- | --- |
| A real device (record manufacturer, model, Android version) or a **graphical** AVD for the display procedure | the proof engine `Kadre_API_35` runs headless; the Overlay display option needs a screen |
| At least one real gamepad, USB or Bluetooth (record name, sources, whether it rumbles) | the emulator has no gamepad; hot-plug, rumble and disconnect are the points |
| A host app calling the public `attachKadre` — the contract consumer `kadre/consumers/android` is the ready-made one | every reading below is a fact a consumer of the public API can read |
| A debug dump of the public state — log `scope.displays.state.value`, `scope.devices.state.value.inventory` (its `devices` AND `gamepads`), each gamepad's `state.value` and `capabilities.effects`, and the device/gamepad event flows | the observation surface; Kadre never prints on its own |
| The gate green first: `scripts/test-kadre-android-contracts.sh` (38 instrumented tests, eight JSONs, `AND-001`…`AND-008`) | the deterministic contract this charter complements |

Run the gate before the manual pass. The procedures may reveal an
engine-specific or device-specific issue, but may not be used to fabricate
contract evidence or to claim that an automated branch has passed.

## Procedures

### 1. A real gamepad — hot-plug, state, effects, mid-effect disconnect

1. Attach (Activity form) and read the baseline: with no gamepad connected,
   `scope.devices.state.value.inventory.gamepads` must be EMPTY and stay empty
   over a bounded observation window (zero `GamepadAdded`) — the
   `android-gamepad-empty-inventory-honest` behavior, re-observed on real
   hardware. The generic inventory lists the pad-to-be as nothing yet.
2. Connect the pad (USB into the device/OTG, or pair over Bluetooth). Expect one
   `GamepadAdded`: the descriptor frozen at connection — the 17-button standard
   table, the axes from the pad's `getMotionRanges()` through the ported table,
   `mapping == Standard` — and the effects capability frozen at the SAME moment:
   a pad with observable motors announces `Supported({DualRumble}, Available)`
   (API 31+: the pad's `VibratorManager` ids; API 26–30: the pad's own vibrator),
   a pad without motors announces `Unsupported(GamepadEffect)`. Record which.
3. Press buttons and move sticks: the state must move ONLY in the gamepad port —
   the pad's A/B/X/Y, shoulders, triggers, sticks, D-pad map through the ported
   tables; a pad keycode outside the table delivers nothing (no slot, never an
   invented button); stick/trigger axes arrive canonical in [-1, 1]. Crucially,
   the SAME physical presses must produce NO key observation on the surface:
   gamepad/joystick-source events leave the ordinary `KeyChanged` path the moment
   a gamepad port is attached. Record any leak — a `KeyChanged` for a pad press
   is a **finding, not a feature**.
4. Rumble: `playEffect(GamepadEffect.DualRumble(strong, weak, duration))` on the
   pad. The MOTORS IN THE PAD must spin — never the phone's vibrator: hold the
   phone in the other hand; if the phone vibrates while the pad stays silent,
   that is a **finding, not a feature** (the phone vibrator never claims gamepad
   motors). On API 26–30 devices the rumble is a one-shot at the dominant
   magnitude — a single-motor hum is the expected shape, not a bug. A pad that
   refuses natively answers `Failed(PlatformFailure(Android, "gamepad-effect",
   "refused"))` — never an exception.
5. Mid-effect disconnect — the phase gate's own case: start a long rumble and
   unplug/switch off the pad while it runs. Expect: the effect terminates with
   the disconnect outcome (no effect outlives the pad), the pad leaves the
   inventory with its state (a re-connection is a NEW connection: fresh frozen
   facts, neutral state, never the dead state of the previous connection), and
   no subscription survives. Verify nothing is left pressed: no phantom button
   may remain held in any published state after the disconnect.
6. Suspend routing mid-flight: send the app to the background while holding a
   stick, come back and read — the suspended projection is the NEUTRAL state,
   and the return delivers the fresh reading (state recorded even while
   suspended, `StateChanged` events only while routed).
7. Record: device, Android version, pad name and transport (USB/BT), the
   `GamepadAdded` descriptor and effects cell, the exact event sequences, the
   rumble observation (which motors, which device), and the disconnect outcomes.

### 2. A second real display — the Overlay dev option

1. On a real device or a graphical AVD, enable Developer options → "Simulate
   secondary displays" → Overlay. The system creates a real secondary display.
2. Read `scope.displays.state.value`: the inventory must now carry BOTH displays,
   each typed `Physical` or `Virtual` from its own flags, keys = the stable
   displayIds, bounds = each display's real size AT THE ORIGIN (never a composed
   desktop position), and `workArea` **null on every display** — Android has no
   desktop, an invented rectangle is the exact mutation the sentinel
   `android-display-no-invented-workarea` kills.
3. Fire the SDK's own changes: change the Overlay's size/density, then disable
   it. Every fire republishes the WHOLE inventory re-read (never a delta); the
   removal withdraws the display; a display whose read stops being measurable
   (non-positive dimensions, invalid scale, no modes) withdraws the whole
   inventory behind `TemporarilyUnavailable(retryable = true)` — a partial
   snapshot is never published.
4. Record: device, the overlay configuration, the exact inventory before/during/
   after, and every withdrawal observed.

### 3. The DPAD-only device — the corrected predicate, on hardware

The kadre-old gamepad predicate was CORRECTED to identity bits (the
`SOURCE_CLASS_MASK` stripped): a `SOURCE_DPAD`-only device — a TV remote, a
car navigator pad — is NOT a gamepad; the literal old table flagged it only by
accident through the shared class bit. This procedure records the correction on
real hardware, where such devices actually exist:

1. Connect the DPAD-only device (a TV remote over Bluetooth is the common case).
2. Read the generic inventory: the device must appear as a GENERIC device (kind
   `Other` or `Keyboard` per its sources — never a fabricated `Gamepad`), the
   gamepad inventory must stay EMPTY, and its D-pad presses must travel the
   ordinary key path (the D-pad keycodes are in the 65-key HID table) — the
   exact behavior the correction chose.
3. Record: device, sources (readable via the SDK), the kind published, and the
   path its keys took.

### 4. Raw input — the recorded refusal, re-read on hardware

`rawInput` stays `Unsupported(RawInputAccess)`: the View's events are never
presented as a global observation. On a real device with several input devices
attached, read `capabilities.rawInput` (before any call) and call
`requestRawInput()`: both must answer the SAME refusal, and no global event flow
may exist anywhere on the session — the keyboard/pointer/touch observations of
the surface remain the only input facts Kadre publishes. Any global raw stream
is a **finding, not a feature**.

## Limits and diagnostic record

Record failures with the device manufacturer and model, the Android version, the
peripheral name, transport and (when readable) its SDK `sources`, the exact
reproduction steps, the readbacks above, and all logcat errors from the host
app. Include for each effect which device vibrated and for how long, and for
each disconnect the full event list with outcomes. Do not infer support from one
manual run, do not edit generated evidence, do not add this charter or its
observations to the validator, and do not extend the declared engine of the
register (`emulator-api-35-arm64-v8a`, read from the attached device — never
hardcoded) from one manual reading. Report the observation alongside the
deterministic gate result (`scripts/test-kadre-android-contracts.sh`) so that it
can be reproduced and turned into an automated test when the platform permits it
— the long-term home of these guarantees is the declared device pool of
`TEST-STRATEGY.md`, not a one-off pass.

## References

- [Capability register of this adapter](../../../../capabilities/android.md) — §2 (the phase 7 rows: display, devices, gamepads, raw input, memory pressure), §4 (the hardware deferred to the device pool)
- [Android emulator contract driver](../README.md) — `AND-008`, the gate, the phase 7 limits
- [Android Phase 5 text input charter](phase-5-text-input.md) — the mirrored precedent (a manual complement the automation cannot stage)
- [Web Phase 6 displays/devices charter](../../web/manual/phase-6-displays-devices.md) — the web mirror of the same frontiers
- [Test strategy](../../../../TEST-STRATEGY.md) — the nightly device pool and the physical-device validation rule (a promised capability not exercable on the simulator/emulator is validated on a physical device or the promise is explicitly withdrawn)
- [Kadre design](../../../../DESIGN.md) — §15.1 (the Android attach forms)
- [Android implementation roadmap](../../../../ANDROID-IMPLEMENTATION-ROADMAP.md) — Phase 7 (gate: hot-plug, déconnexion pendant effet, changement de display et fermeture concurrente ne laissent ni état pressé, ni effet, ni subscription orpheline ; « les garanties matérielles passent sur le pool de devices déclaré supporté »)
