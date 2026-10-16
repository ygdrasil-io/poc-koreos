# Wayland Phase 4 — Seats, Keyboard, Pointer & Scroll — Execution Plan

Design: `kadre/WAYLAND-PHASE-4-INPUT-DESIGN.md`. Branches: kadre
`wayland-phase-4`, kffi `feat/wayland-seat-input`. Local-first: kffi via
mavenLocal, ONE consolidated kffi PR at the end.

## Task 1: kffi — managed wl_seat (capabilities + name)
- [x] RED: sway integration — bind wl_seat, capabilities delivered, name v2+.
- [x] impl: WaylandSeat (revocable listener, payload copies, release v5).
      getPointer/getKeyboard factories via array-flags marshals.
- [x] GREEN local (sway, arm64) → commit.

## Task 2: kffi — managed wl_pointer (v9 events)
- [x] RED: swaymsg cursor move/press → enter/motion/button/frame observed.
- [x] impl: all events incl. axis_value120, axis_stop, frame; copies in
      upcalls; surface proxy addresses passed through.
- [x] GREEN → commit.

## Task 3: kffi — managed wl_keyboard (raw, no XKB yet)
- [x] RED: swaymsg type → enter/leave/key/modifiers observed; keymap fd+size.
- [x] impl: keymap fd captured (NOT consumed), keys array copied at enter,
      key/modifiers/repeat_info; release v3.
- [x] GREEN → commit + publishToMavenLocal.

## Task 4: kadre — seat wiring + surface routing (O2 fake bridge)
- [x] RED: capabilities → sub-objects created; events routed by surface
      proxy address; SN-1/SN-3 proofs with fakes.
- [x] impl: WaylandSeatDriver (driver-owned), event batching per frame
      (D2), neutralization on leave/capability loss.
- [x] GREEN host → commit.

## Task 5: kadre — O3 sway injection suite
- [x] WaylandInputLinuxTest: P-1 (two windows focus alternation), P-2
      (pointer batches), P-3 (keyboard type), P-4 (scroll units), SN-2
      (capabilities change via swaymsg).
- [x] Local arm64 gate → commit.

## Task 6: contracts + closure
- [x] Registry INP-004/005/006 (verify free), evidence, validator ids,
      capabilities rows, gate script evidence list.
- [x] Consolidated kffi PR → merge + publish → drop mavenLocal → dispatch
      `kadre-wayland-contracts` → green → plan ticks + closure commit.
