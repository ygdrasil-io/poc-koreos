# Koreos

POC de fenêtrage cross-platform en **Kotlin pur**, inspiré de [winit](https://github.com/rust-windowing/winit).

Objectif : exposer des handles natifs (`NSView`, `UIView`, `android.view.Surface`) consommables par un renderer 3D (Metal, Vulkan, [wgpu4k](https://github.com/wgpu4k/wgpu4k)) **sans dépendance AWT/Swing**.

## Tutoriels

- [Intégrer Koreos dans une application Windows](./tutorials/windows-app.md) — fenêtre Win32, événements, DPI PerMonitorV2

## Documents

- [Plan projet](./plan.md) — vision, jalons, risques, timeline
- [Sprint Review v0.1](./sprint-review-v0.1.md) — métriques, livrables, écarts, rétro v0.1.0
- [Spécifications techniques](./specs.md) — architecture, API, diagrammes

## Jalons

| Jalon | Livrable |
|-------|----------|
| **M1 — POC** | NSWindow macOS avec contentView layer-backed prêt pour Metal |
| **M2 — Démo wgpu4k** | Scène 3D simple rendue via wgpu4k consommant le raw handle |
| **M3 — Lib cible** | Lib KMP publiable, backends AppKit + UIKit + Android complets |

## Statut

**Draft pour relecture** — voir la PR de validation des specs.
