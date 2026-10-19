# Linux Evdev Gamepad — Execution Plan

Design: `kadre/LINUX-EVDEV-GAMEPAD-DESIGN.md`. Branches: kadre
`linux-evdev-gamepad`, kffi `feat/evdev-gamepad`. Local-first via
`kadreKffiLocal`; ONE consolidated kffi PR at the end.

## Task 1: kffi-evdev module
- [ ] Module skeleton (settings include, build mirror kffi-wayland,
      Linux-only integration task).
- [ ] Bindings: libc open/ioctl; _IOC helper; evdev ioctl constants
      (EVIOCG*, EVIOCSFF, EVIORMFF); input_event / input_id /
      input_absinfo / ff_effect layouts; libudev enumerate + monitor.
- [ ] Integration proof on Docker: enumerate real nodes, read one
      input_event from uinput, FF upload/play cycle.

## Task 2: kadre broker
- [ ] `:kadre:backend:linux` module: EvdevGamepadBroker (worker poll,
      stable keys, udev hotplug), per-session EvdevGamepadPort.
- [ ] O2 (fakes): detection par capabilities, clé stable, EACCES,
      FF sans support, routing deux sessions, close idempotent.

## Task 3: O3 uinput + contracts
- [ ] Docker proofs P-1..P-4 via /dev/uinput (module noyau chargé).
- [ ] Contracts GMP-003/GMP-004, evidence, validator, gate list,
      capabilities, wiring provider Wayland (remplace GMP-002 wiring),
      PRs + gate CI.
