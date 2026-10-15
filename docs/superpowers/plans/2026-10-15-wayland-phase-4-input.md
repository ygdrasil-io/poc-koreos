# Wayland Phase 4 — Seats, Keyboard, Pointer & Scroll — Execution Plan

Design: `kadre/WAYLAND-PHASE-4-INPUT-DESIGN.md`. Branches: kadre
`wayland-phase-4`, kffi `feat/wayland-seat-input`. Local-first: kffi via
mavenLocal, ONE consolidated kffi PR at the end.

## Task 1: kffi — managed wl_seat (capabilities + name)
- [ ] RED: sway integration — bind wl_seat, capabilities delivered, name v2+.
- [ ] impl: WaylandSeat (revocable listener, payload copies, release v5).
      getPointer/getKeyboard factories via array-flags marshals.
- [ ] GREEN local (sway, arm64) → commit.

## Task 2: kffi — managed wl_pointer (v9 events)
- [ ] RED: swaymsg cursor move/press → enter/motion/button/frame observed.
- [ ] impl: all events incl. axis_value120, axis_stop, frame; copies in
      upcalls; surface proxy addresses passed through.
- [ ] GREEN → commit.

## Task 3: kffi — managed wl_keyboard (raw, no XKB yet)
- [ ] RED: swaymsg type → enter/leave/key/modifiers observed; keymap fd+size.
- [ ] impl: keymap fd captured (NOT consumed), keys array copied at enter,
      key/modifiers/repeat_info; release v3.
- [ ] GREEN → commit + publishToMavenLocal.

## Task 4: kadre — seat wiring + surface routing (O2 fake bridge)
- [ ] RED: capabilities → sub-objects created; events routed by surface
      proxy address; SN-1/SN-3 proofs with fakes.
- [ ] impl: WaylandSeatDriver (driver-owned), event batching per frame
      (D2), neutralization on leave/capability loss.
- [ ] GREEN host → commit.

## Task 5: kadre — O3 sway injection suite
- [ ] WaylandInputLinuxTest: P-1 (two windows focus alternation), P-2
      (pointer batches), P-3 (keyboard type), P-4 (scroll units), SN-2
      (capabilities change via swaymsg).
- [ ] Local arm64 gate → commit.

## Task 6: contracts + closure
- [ ] Registry INP-004/005/006 (verify free), evidence, validator ids,
      capabilities rows, gate script evidence list.
- [ ] Consolidated kffi PR → merge + publish → drop mavenLocal → dispatch
      `kadre-wayland-contracts` → green → plan ticks + closure commit.
