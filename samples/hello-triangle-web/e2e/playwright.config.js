// Configuration Playwright pour le smoke E2E de hello-triangle-web (Redmine #22).
//
// Sert le bundle de production JS (produit par `jsBrowserDistribution`, toolchain
// activée par #91) via http-server, puis lance **Chrome stable** (canal `chrome`,
// installé par Playwright sur les runners macOS/Windows) avec WebGPU activé.
//
// Pourquoi Chrome stable plutôt que Chromium + SwiftShader ?
// - WebGPU est encore expérimental sur Linux/SwiftShader (pas forcément exposé,
//   rendu logiciel pouvant différer de l'impl GPU réelle Metal/D3D12).
// - Chrome stable sur macOS (Metal) et Windows (D3D12) reflète mieux l'usage
//   réel des end-users et a WebGPU stable depuis Chrome 113.
// - Les baselines de diff visuel sont alignées sur du rendu GPU réel.
const { defineConfig } = require('@playwright/test');

const DIST = '../build/dist/js/productionExecutable';

module.exports = defineConfig({
  testDir: './tests',
  timeout: 90_000,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:8080',
    headless: true,
    // Channel `chrome` = Chrome stable installé par Playwright (vs Chromium
    // open-source qui n'a pas WebGPU activé par défaut). Fonctionne sur les
    // runners macos-latest et windows-latest.
    channel: 'chrome',
    launchOptions: {
      // Requis pour exposer un adapter WebGPU en mode headless (policy Chrome).
      // Pas de flag SwiftShader/ANGLE : on utilise le GPU réel du runner
      // (Metal sur macOS, D3D12 sur Windows).
      args: [
        '--enable-unsafe-webgpu',
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
