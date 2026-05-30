// Configuration Playwright pour le test E2E scénarisé de samples/pong (Web).
//
// Sert le bundle de production JS (produit par `jsBrowserDistribution`) via
// http-server, puis lance Chromium headless avec WebGPU SwiftShader (idem
// hello-triangle-web). La fenêtre est portée à 1024×768 pour englober le
// canvas Pong 800×600 + le HUD du sample (titre, contrôles, zone de log).
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
    // Viewport assez grand pour le canvas 800×600 + HUD du sample.
    viewport: { width: 1024, height: 768 },
    // Vidéo systématique — c'est la valeur ajoutée du test E2E scénarisé
    // (vs le smoke single-frame de hello-triangle-web).
    video: 'on',
    // Trace Playwright archivée pour debugging (ouvrable via `npx playwright show-trace`).
    trace: 'on',
    launchOptions: {
      // WebGPU logiciel en headless : Dawn via SwiftShader, sans GPU physique.
      // --enable-unsafe-webgpu est nécessaire pour exposer un adapter en headless.
      args: [
        '--enable-unsafe-webgpu',
        '--enable-unsafe-swiftshader',
        '--use-angle=swiftshader',
        '--enable-features=Vulkan,WebGPU',
      ],
    },
  },
  webServer: {
    command: `npx http-server ${DIST} -p 8080 -a 127.0.0.1 -s`,
    url: 'http://127.0.0.1:8080',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
