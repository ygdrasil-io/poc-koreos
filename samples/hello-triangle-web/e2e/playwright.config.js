// Configuration Playwright pour le smoke E2E de hello-triangle-web (Redmine #22).
//
// Sert le bundle de production JS (produit par `jsBrowserDistribution`, toolchain
// activée par #91) via http-server, puis lance Chromium headless avec WebGPU activé
// (SwiftShader logiciel — pas de GPU requis sur les runners CI).
const { defineConfig } = require('@playwright/test');

const DIST = '../build/dist/js/productionExecutable';

module.exports = defineConfig({
  testDir: './tests',
  timeout: 90_000,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:8080',
    headless: true,
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
