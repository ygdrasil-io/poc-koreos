# Wayland Phase 5 — Interactions & Advanced Windows — Execution Plan

Design: `kadre/WAYLAND-PHASE-5-INTERACTIONS-DESIGN.md`. Branches: kadre
`wayland-phase-5`, kffi `feat/wayland-toplevel-ops`. Local-first: kffi via
`kadreKffiLocal` opt-in; ONE consolidated kffi PR at the end.

## Task 1: kffi — toplevel ops (DONE on branch, sway proof running)
- [x] move/resize with seat + serial; min/max size; fullscreen/unset.
- [x] Integration proof: ops round-trip + fabricated-serial refusal.

## Task 2: kadre — serial ledger + move/resize port
- [ ] O2: requestMove/Resize without eligible serial → InvalidRequest, no
      primitive; with serial → marshalled once, serial consumed.
- [ ] impl: serial ledger in the input router (pointer button/key/enter),
      `WaylandWindowCommandPort.requestInteractionActions` for
      BeginWindowMove/Resize from interaction callbacks.

## Task 3: kadre — requestUpdate conformant fields
- [ ] O2: title/min/max/fullscreen-borderless applied + state published;
      min>max rejected pre-primitive (SN-1); exclusive rejected (SN-2);
      resizable as min=max constraint semantics documented.
- [ ] impl: extend the driver's update path (was: total rejection).

## Task 4: O3 sway + contracts
- [ ] WaylandAdvancedLinuxTest: P-1 (min/max/title → configure), P-2
      (fullscreen borderless on/off), move refusal path.
- [ ] Registry WIN-013/INT-005 (verify), evidence, validator, gate list,
      capabilities tranche 5, plan ticks.

## Task 5: closure
- [ ] Consolidated kffi PR → merge + publish → drop local flag → dispatch
      gate → green → PR kadre merge.
