// Configuration Playwright pour le smoke E2E de hello-triangle-web (Redmine #22).
//
// Sert le bundle de production JS (produit par `jsBrowserDistribution`, toolchain
// activée par #91) via http-server, puis lance un navigateur Chromium-family
// avec WebGPU activé.
//
// ## Choix du moteur par OS (runners GitHub Actions)
//
// | OS                | Runner GPU              | Browser           | Backend WebGPU |
// |-------------------|-------------------------|-------------------|----------------|
// | `macos-latest`    | Mac mini (Metal réel)   | Chrome stable     | Metal (HW)     |
// | `windows-latest`  | VM Hyper-V (no GPU)     | Chromium + flags  | SwiftShader    |
//
// Pourquoi pas `channel: 'chrome'` partout ?
// - Chrome stable ne bundle PAS SwiftShader, contrairement à Chromium open-source.
//   Sur le runner Windows sans GPU, Chrome stable ne trouve aucun adapter WebGPU
//   exploitable → `requestAdapter()` retourne null → timeout du test (cf. PR #131,
//   premier run windows-latest).
// - Chromium open-source embarque SwiftShader et expose WebGPU avec les flags
//   `--enable-unsafe-webgpu --use-angle=swiftshader --enable-features=Vulkan,WebGPU`.
//
// Cf. PR #131 pour l'historique du débogage. Le test Windows valide essentiellement
// que la chaîne JS + wgpu4k Web tourne ; la couverture GPU « réelle » Windows reste
// à faire sur un runner avec GPU passthrough (ou en local).
const { defineConfig } = require('@playwright/test');

const DIST = '../build/dist/js/productionExecutable';
const isWindows = process.platform === 'win32';

module.exports = defineConfig({
  testDir: './tests',
  timeout: 90_000,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:8080',
    headless: true,
    // - macOS : Chrome stable (canal release) avec GPU Metal réel.
    // - Windows : Chromium open-source de Playwright (default, sans `channel`)
    //   qui inclut SwiftShader pour le fallback CPU.
    channel: isWindows ? undefined : 'chrome',
    launchOptions: {
      args: isWindows
        ? [
            // Fallback CPU complet pour le runner Windows sans GPU.
            '--enable-unsafe-webgpu',
            '--enable-unsafe-swiftshader',
            '--use-angle=swiftshader',
            '--enable-features=Vulkan,WebGPU',
          ]
        : [
            // macOS : GPU réel Metal, juste le flag d'autorisation headless.
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
