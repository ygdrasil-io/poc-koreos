// Configuration Playwright pour le test E2E scénarisé de samples/pong (Web).
//
// Sert le bundle de production JS (produit par `jsBrowserDistribution`) via
// http-server, puis lance **Chrome stable** headless avec WebGPU réel (GPU
// hôte). Choix `channel: 'chrome'` + macOS / Windows runners en CI : le
// scénario Pong est plus exigeant que le smoke single-frame de hello-triangle
// (animation multi-seconde + assertions de diff pixel) → Chromium + SwiftShader
// (rendu logiciel sur Linux) donne des frames plus lentes et moins
// déterministes, mauvais terrain pour ces seuils. Cf. `feat/web-e2e-chrome-mac-win`.
//
// Trace + vidéo activés (`on`) pour produire systématiquement :
//   - un .webm de l'exécution complète (preuve visuelle / com)
//   - un trace Playwright (debugging interactif via `npx playwright show-trace`)
const { defineConfig } = require('@playwright/test');

const DIST = '../build/dist/js/productionExecutable';

module.exports = defineConfig({
  testDir: './tests',
  // Le scénario simule plusieurs secondes d'interaction (boot wgpu4k + inputs
  // scriptés + frames d'observation) — laisser de la marge sur runners chargés.
  timeout: 180_000,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:8080',
    headless: true,
    // Chrome stable (canal release) avec accélération GPU native. Évite
    // SwiftShader (CPU) qui ralentit l'animation et brouille les seuils
    // de diff pixel sur lesquels les assertions reposent.
    channel: 'chrome',
    // Viewport assez grand pour le canvas 800×600 + HUD du sample.
    viewport: { width: 1024, height: 768 },
    // Vidéo systématique — c'est la valeur ajoutée du test E2E scénarisé
    // (vs le smoke single-frame de hello-triangle-web).
    video: 'on',
    // Trace Playwright archivée pour debugging (ouvrable via `npx playwright show-trace`).
    trace: 'on',
    launchOptions: {
      // --enable-unsafe-webgpu reste nécessaire en headless pour exposer
      // un adapter WebGPU même avec un GPU physique présent.
      args: ['--enable-unsafe-webgpu'],
    },
  },
  webServer: {
    command: `npx http-server ${DIST} -p 8080 -a 127.0.0.1 -s`,
    url: 'http://127.0.0.1:8080',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
