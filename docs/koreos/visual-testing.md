# Tests de régression visuelle (screenshot diff)

Redmine #88 — équivalent Roborazzi, cross-plateforme. Compare une capture d'écran
d'un sample à une **baseline** committée, avec une tolérance en % de pixels
différents. Complète le smoke E2E (#22, « au moins une frame présentée ») en
détectant les régressions visuelles subtiles (couleur, position, antialiasing).

## État par plateforme

| Plateforme | Capture | Automatisé en CI |
|------------|---------|------------------|
| **Web** (JS) | Playwright `page.screenshot()` | ✅ oui (informatif, non bloquant) |
| macOS | `CGWindowListCreateImage` (ou `screencapture`) | 🟡 manuel — GPU réel requis |
| iOS sim | `xcrun simctl io booted screenshot` | 🟡 manuel — simulateur requis |
| Android emu | `adb exec-out screencap -png` | 🟡 manuel — émulateur requis |
| Windows | PowerShell `CopyFromScreen` | 🟡 manuel |
| Linux X11 | ImageMagick `import -window root` | 🟡 manuel |
| Linux Wayland | `grim` | 🟡 manuel (hors scope v1) |

> Seule la plateforme **Web** est exécutée en CI : les autres nécessitent le rendu
> GPU réel des samples sur chaque OS/émulateur (même contrainte que les validations
> hardware #7/#34/#70). Les commandes de capture ci-dessus servent de base pour une
> infra de runners dédiés ultérieure.

## Tranche Web (implémentée)

Le test `samples/hello-triangle-web/e2e/tests/visual.spec.js` capture le canvas
WebGPU et le compare à `e2e/baselines/hello-triangle-web.png` via
[pixelmatch](https://github.com/mapbox/pixelmatch) (helper
`visual/assert-screenshot.js`, `assertScreenshotMatches(actualPng, baselinePath,
{ tolerance })`, défaut **2 %**).

### Non bloquant

Le rendu WebGPU **SwiftShader** headless peut varier légèrement entre
environnements ; le test est donc **informatif** : il journalise le ratio de diff et
archive l'image de diff en artefact CI (`hello-triangle-web-visual-diff`), mais
**n'échoue jamais le build**. Cela évite un gate flaky tout en rendant les
régressions visibles en revue.

### Mettre à jour la baseline

Quand un changement visuel est **légitime** (humain only — jamais auto) :

```bash
cd samples/hello-triangle-web/e2e
npm run update-baselines          # supprime + régénère baselines/*.png
git add baselines/*.png           # commiter la nouvelle baseline
```

## Ajouter un sample / une plateforme

1. Réutiliser `assertScreenshotMatches(actualPng, baselinePath, { tolerance, diffPath })`.
2. Fournir un provider de capture pour la plateforme (cf. tableau).
3. Stocker la baseline sous `baselines/<plateforme>/<sample>.png` (git-lfs si > 5 Mo cumulés).
