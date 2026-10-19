# Wayland Phase 9 — Linux Services, Devices, Gamepads, Raw Input — Plan

Design: `kadre/WAYLAND-PHASE-9-SERVICES-DESIGN.md`. Branch:
kadre `wayland-phase-9` (pas de delta kffi requis en tranche 1).

## Task 1: seat inventory port
- [ ] O2: seats annoncés → devices honnêtes (nom, kind des
      capabilities) ; retrait/ajout → InventoryChanged ; lecture après
      close → vide (F1/F2).
- [ ] impl: WaylandInputDevicePort branché au registry du bridge.

## Task 2: gamepad honest refusal
- [ ] O2: inventaire vide + inventoryFailure explicite Unsupported
      (SN-1) ; effets Unsupported.
- [ ] impl: WaylandGamepadPort.

## Task 3: provider wiring + contracts
- [ ] Components wiring (devices, gamepads) ; observations absentes
      restent absentes (SN-2 — test provider).
- [ ] Registry INP-007/GMP-002 (verify), evidence, validator, gate,
      capabilities tranche 9, plan ticks, PR kadre, gate CI.
