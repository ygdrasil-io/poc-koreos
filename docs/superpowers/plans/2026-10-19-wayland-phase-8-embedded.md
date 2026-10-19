# Wayland Phase 8 — Embedded Host Lifecycle — Execution Plan

Design: `kadre/WAYLAND-PHASE-8-EMBEDDED-DESIGN.md`. Branch:
kadre `wayland-phase-8` (pas de delta kffi requis — la pompe non
bloquante réutilise les primitives existantes).

## Task 1: pompe non bloquante
- [ ] O2: runOnce en mode drain (dispatch → actions → flush) sans poll
      bloquant ; worker de poll avec réveil borné postant un tick.
- [ ] impl: WaylandEmbeddedPump (worker IO + ticks EDT pilotables),
      tests sur fake EDT executor.

## Task 2: attach/detach provider
- [ ] O2: attach EDT accepté (session créée, detach propre, EDT vivant
      — SN-3), attach hors EDT → InvalidRequest (SN-1), JavaFX →
      Unsupported(HostAttach) (SN-2), double attach → AlreadyInUse
      (F3-2), session close pendant tick (F3-1).
- [ ] impl: WaylandBackendProvider.attach sur le chemin embarqué avec
      lease par session.

## Task 3: contracts + closure
- [ ] Registry SES-003/SUR-004 (verify), evidence, validator, gate
      list, capabilities tranche 8, plan ticks, PR kadre, gate CI.
