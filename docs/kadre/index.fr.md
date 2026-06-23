# Kadre

POC de fenêtrage cross-platform en **Kotlin pur**, inspiré de [winit](https://github.com/rust-windowing/winit).

Objectif : exposer des handles natifs (`NSView`, `UIView`, `android.view.Surface`) consommables par un renderer 3D (Metal, Vulkan, [wgpu4k](https://github.com/wgpu4k/wgpu4k)) **sans dépendance AWT/Swing**.

## Tutoriels

- [Intégrer Kadre dans une application Windows](./tutorials/windows-app.md) — fenêtre Win32, événements, DPI PerMonitorV2
- [Intégrer Kadre dans une application Linux](./tutorials/linux-app.md) — X11 et Wayland, auto-détection backend, DPI, CI headless
- [Intégrer Kadre dans une page web](./tutorials/web-embed.md) — canvas HTML, Kotlin/JS + Kotlin/Wasm, boucle RAF

## Blog

- [Kadre v1.0.0 — 6 plateformes, Pong cross-platform, Demo en direct](./blog/v1.0.0-release.md) — la release 1.0.0 : macOS, iOS, Android, Win32, Web (JS+Wasm), Linux (X11+Wayland), Pong

## Documents

- [Plan projet](./plan.md) — vision, périmètre, risques
- [Sprint Review](./sprint-review.md) — métriques, livrables, rétrospective
- [Spécifications techniques](./specs.md) — architecture, API, diagrammes

## Fonctionnalités

- **État & géométrie fenêtre** — contraintes de taille, min/max, redimensionnable, minimisé, maximisé, décorations, position extérieure, pre-present notify
- **Énumération des moniteurs** — `availableMonitors()` / `primaryMonitor()` avec données `VideoMode`
- **Plein écran** — `Fullscreen.Borderless` (tous backends) et `Fullscreen.Exclusive` (desktop ; fallback borderless sur Wayland/Web/mobile)
- **Curseur** — 25 formes `CursorIcon`, visibilité, grab (Confined/Locked), warp, hit-testing ; curseurs custom RGBA (`CursorImage` / `CustomCursor`) sur desktop/Web, no-op documenté sur mobile
- **Thème & apparence** — `Theme` (Light/Dark), override par fenêtre, événement `ThemeChanged`, `WindowLevel`, transparence, flou, icône de fenêtre
- **Richesse clavier** — `PhysicalKey`, `LogicalKey`, `text`, `textWithAllModifiers`, `keyWithoutModifiers`, `KeyLocation`, `repeat`, `synthetic` ; `ModifiersChanged` ; reset dead-key
- **Événements device** — `DeviceEvent.MouseWheel` ; filtre via `listenDeviceEvents(DeviceEvents.Always/WhenFocused/Never)`
- **IME** — `setImeAllowed`, `setImeCursorArea`, `setImePurpose(ImePurpose)` ; cycle de vie `ImeEvent` complet (Enabled/Preedit/Commit/DeleteSurrounding/Disabled) câblé par les backends actuels, avec capacités détaillées encore dépendantes des plateformes
- **Drag & drop** — événements `DragEntered/Moved/Dropped/Left` câblés sur desktop/Web/mobile, avec fidélité de payload variable selon le backend
- **Gestes** — Pinch, Pan, Rotation, DoubleTap, TouchpadPressure — AppKit câblé ; recognizers UIKit opt-in ; autres backends partiels ou unsupported
- **Occluded** — événement de changement de visibilité câblé sur AppKit, X11, Web, Android et UIKit
- **Fenêtre divers** — les APIs attention utilisateur, protection contenu, menu fenêtre et drag/resize-window restent dépendantes des backends et retournent des `WindowRequestResult` typés (cf. [DEFERRED.md](https://github.com/ygdrasil-io/poc-koreos/blob/master/DEFERRED.md))

## Matrice de capacités par plateforme

| Fonctionnalité | macOS (appkit) | Windows (win32) | Linux X11 | Linux Wayland | Web (JS/Wasm) | Android | iOS (uikit) |
|----------------|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Créer fenêtre / titre / taille | réel | réel | réel | réel | réel | réel | réel |
| Énumération moniteurs | réel | réel | réel | réel | synthétique | synthétique | synthétique |
| Plein écran Borderless | réel | réel | réel | réel | réel | réel | réel |
| Plein écran Exclusive | réel | partiel | réel | no-op | no-op | no-op | no-op |
| CursorIcon | réel | réel | réel | réel* | réel (CSS) | no-op | no-op |
| CursorGrab Confined/Locked | réel | réel | réel | partiel* | partiel* | no-op | no-op |
| CursorVisible | réel | partiel* | no-op* | no-op | réel | no-op | no-op |
| CursorPosition (warp) | partiel* | réel | réel | no-op | no-op | no-op | no-op |
| systemTheme() | réel | réel | null | null | réel | réel | réel |
| setTheme() par fenêtre | réel | réel | réel (_GTK_THEME_VARIANT) | no-op | no-op | no-op | no-op |
| Événement ThemeChanged | réel | réel | — | — | — | — | — |
| setBlur() | réel | no-op runtime | no-op | deferred protocole optionnel | no-op | no-op | no-op |
| setWindowIcon() | no-op | réel | réel | deferred protocole optionnel | no-op | no-op | no-op |
| Événement ModifiersChanged | réel | réel | réel* | réel* | réel | réel | réel |
| IME (setImeAllowed etc.) | réel | réel | réel | réel* | réel | réel | réel |
| Événements DnD | partiel* | partiel* | réel | réel | réel* | réel | partiel* |
| Événements gestes | réel | partiel* | — | — | partiel* | — | opt-in |
| Événement Occluded | réel | — | réel | — | réel | réel | réel |
| Curseurs custom | réel | réel | réel | réel* | réel | no-op | no-op |

`réel` = implémenté. `partiel*` / `no-op*` = partiel ou no-op documenté, cf. [DEFERRED.md](https://github.com/ygdrasil-io/poc-koreos/blob/master/DEFERRED.md). `—` = non applicable sur cette plateforme. Sous Linux, `ModifiersChanged` est câblé pour les transitions de touches et le reset/réhydratation de focus ; la sémantique XKB locked/latched reste future.

## Plateformes

| Plateforme | Backend |
|------------|---------|
| **macOS** | NSWindow avec contentView layer-backed prêt pour Metal |
| **iOS** | UIKit (UIWindow + UIView + CAMetalLayer) |
| **Android** | SurfaceView + Choreographer |
| **Windows** | Win32 (RegisterClassExW + CreateWindowExW) |
| **Linux** | X11 + Wayland, auto-détectés |
| **Web** | canvas HTML (Kotlin/JS + Kotlin/Wasm) |

## Statut

**Publié** — `org.graphiks.kadre:kadre:1.0.0` sur Maven Central.

Les éléments résiduels reportés relèvent surtout des capacités et de la fidélité backend : reporting IME détaillé, payload DnD, couverture gestes hors Apple, protocoles Wayland optionnels, Pointer Lock/hit-testing Web, et no-ops desktop sur mobile. Liste complète : [DEFERRED.md](https://github.com/ygdrasil-io/poc-koreos/blob/master/DEFERRED.md).
