# Wayland Phase 3 — Metrics, Outputs, Scale & Redraw — Execution Plan

Design: `kadre/WAYLAND-PHASE-3-METRICS-DESIGN.md`. Branch: `wayland-phase-3`.
Local-first workflow: kffi consolidated fixes go to mavenLocal for kadre
verification; ONE consolidated kffi PR at the end of the phase.

## Task 1: kffi — managed wl_output (events + done batching)
- [x] **Step 1: RED** — integration test (sway): bind wl_output via the managed
      facade, receive geometry/mode/scale/name, batched at `done`.
- [x] **Step 2: impl** — `WaylandOutput` managed object: revocable listener,
      payload copies, done-batched snapshot; bind through registry
      (`wl_output` global), all marshals array-flags, new_id at slot 0.
- [x] **Step 3: GREEN local (sway container, arm64)** → commit
      `feat(wayland): add the managed output bindings`.

## Task 2: kffi — surface scale surface area
- [x] **Step 1: RED** — preferred_buffer_scale / preferred_buffer_transform
      events on wl_surface ≥ v6; set_buffer_scale via array-flags (opcode
      checked against the generated interfaces).
- [x] **Step 2: impl** — `WaylandSurface.setBufferScale(int)` +
      `onPreferredScale` listener; fractional-scale-v1 optional binding
      (get_fractional_scale → preferred_scale ×120).
- [x] **Step 3: GREEN local** → commit
      `feat(wayland): expose surface buffer scale and preferred scale`.

## Task 3: kffi — publish to mavenLocal
- [x] `publishToMavenLocal`, bump-verify kadre resolves the local artifacts.

## Task 4: kadre — WaylandDisplayPort (displays)
- [x] **RED** — O2 with a fake bridge: snapshot assembly from outputs
      (bounds/scale/modes), done-batching, removal → snapshot with the
      display withdrawn, primaryKey semantics (P-1/P-3, F1-*).
- [x] **impl** — `WaylandDisplayPort : DisplayPort` +
      `RuntimeSessionComponents(displays = …)` wiring in the provider.
- [x] **GREEN host** → commit
      `feat(wayland): deliver the display inventory port (phase 3)`.

## Task 5: kadre — surface scale + output membership
- [x] **RED** — O2: preferred scale / output scale change ⇒ buffer
      reallocated to logical × scale, redraw armed (P-2, F3-*).
- [x] **impl** — peer wiring: enter/leave set membership, scale selection,
      WaylandWindowAdapter buffer resize + set_buffer_scale.
- [x] **GREEN host + arm64 sway container** → commit
      `feat(wayland): apply surface buffer scale from outputs (phase 3)`.

## Task 6: kadre — SurfaceCommandPort.requestRedraw
- [x] **RED** — O2: N requests coalesce to one armed frame; frame fires ⇒
      damage+attach+commit exactly once; request after close ⇒ Closed (SN-1,
      F2-2/3, P-4 logic).
- [x] **impl** — `WaylandSurfaceCommandPort` + peer redraw ticketing on
      `WaylandSurface.onNextFrame`.
- [x] **GREEN host** → commit
      `feat(wayland): wire the coalesced redraw command port (phase 3)`.

## Task 7: O3 sway — dynamic outputs and redraw pacing
- [x] New `WaylandMetricsLinuxTest`: P-2 (`swaymsg output <name> scale 2`
      mid-session via the compositor controller), P-3 (output disable/enable),
      P-4 (repeated requestRedraw pacing counts), plus the scale traces
      (1×/2×) from the exit gate.
- [x] Gate locally (arm64 sway container) → commit
      `test(wayland): prove the metrics, scale and redraw gates on sway`.

## Task 8: contracts + closure
- [x] Registry rows DSP-002/DSP-003/SUR-003 (verify free numbers), evidence
      mappings, validator ids, capabilities/wayland.md phase 3 rows.
- [x] ONE consolidated kffi PR (Tasks 1-2) → user merges+publishes →
      remove any mavenLocal override → dispatch `kadre-wayland-contracts`
      → green → plan ticks + closure commit.
