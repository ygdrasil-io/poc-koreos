# Wayland Phase 7 — Drag-and-Drop & Transfers — Execution Plan

Design: `kadre/WAYLAND-PHASE-7-DND-DESIGN.md`. Branches: kadre
`wayland-phase-7`, kffi `feat/wayland-data-device`. Local-first via
`kadreKffiLocal`; ONE consolidated kffi PR at the end.

## Task 1: kffi — managed wl_data_device/offer
- [ ] RED: sway proof — device events delivered (offer/enter/leave),
      receive returns an owned fd.
- [ ] impl: WaylandDataDevice (get_data_device via array marshal,
      events fanned out, copies), WaylandDataOffer (offer events,
      accept/receive/finish/destroy marshals).
- [ ] GREEN local → commit.

## Task 2: kadre — drop presentation + transfer source
- [ ] O2: enter→presentDrop immuable, leave→LeftSurface, drop sans
      accept→refus (F1-2), mime non annoncé rejeté (F2-3).
- [ ] impl: WaylandDropPresenter branché au router; WaylandDropTransfer
      (fd non bloquant, poll borné, chunks, budget, terminaisons).
- [ ] O2 transfert: interruption EOF (SN-1), budget (SN-2), teardown
      pendant lecture (SN-3).
- [ ] GREEN host → commit.

## Task 3: contracts + closure
- [ ] Registry DRP-002/003 (verify), evidence, validator, gate list,
      capabilities tranche 7, plan ticks.
- [ ] kffi PR → merge + publish → drop local flag → dispatch gate →
      green → PR kadre.
