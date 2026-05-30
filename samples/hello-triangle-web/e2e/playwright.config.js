// Configuration Playwright pour le smoke E2E de hello-triangle-web (Redmine #22).
//
// Sert le bundle de production JS (produit par `jsBrowserDistribution`, toolchain
// activée par #91) via http-server, puis lance **Chrome stable** (canal `chrome`,
// installé par Playwright sur les runners macOS/Windows) avec WebGPU activé.
//
// ## GPU réel vs SwiftShader — choix par OS
//
// Les runners GitHub Actions n'exposent pas tous le même backend graphique :
// - `macos-latest` : Mac mini physique avec **Metal** (GPU hardware) → WebGPU réel
// - `windows-latest` : VM Hyper-V **sans GPU passthrough** → SwiftShader requis
//
// Sur macOS on cible le GPU réel pour valider la chaîne Metal/wgpu4k bout-en-bout.
// Sur Windows on bascule sur SwiftShader (rendu CPU) car aucun adapter WebGPU réel
// n'est disponible — sinon `requestAdapter()` retourne null et le test timeout.
// Les flags SwiftShader sont safe à ignorer sur macOS (Chrome préfère le GPU réel
// quand il est dispo), mais on reste explicite pour la lisibilité.
const { defineConfig, devices } = require('@playwright/test');

const DIST = '../build/dist/js/productionExecutable';

// Détection plateforme — process.platform reflète l'OS du runner CI.
const isWindows = process.platform === 'win32';

const launchArgs = [
  // Requis pour exposer un adapter WebGPU en mode headless (policy Chrome).
  '--enable-unsafe-webgpu',
];

if (isWindows) {
  // Fallback SwiftShader (rendu logiciel) — les runners Windows GitHub Actions
  // n'ont pas de GPU accessible. Sans ces flags, Chrome stable ne trouve aucun
  // adapter WebGPU exploitable et `requestAdapter()` renvoie null.
  launchArgs.push(
    '--enable-unsafe-swiftshader',
    '--use-angle=swiftshader',
    '--enable-features=Vulkan,WebGPU',
  );
}

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
      args: launchArgs,
    },
  },
  webServer: {
    command: `npx http-server ${DIST} -p 8080 -a 127.0.0.1 -s`,
    url: 'http://127.0.0.1:8080',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
