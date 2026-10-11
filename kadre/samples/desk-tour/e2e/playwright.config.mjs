/*
 * La config Playwright du filet e2e du demo desk-tour (cible web).
 *
 * Le contrat du filet : il ne construit RIEN. Il sert une distribution déjà produite par
 * `wasmJsBrowserDistribution` — par défaut la sortie du sample elle-même
 * (`../build/dist/wasmJs/productionExecutable`), ou le dossier pointé par
 * `KADRE_DEMO_DIST`. Sans distribution, la config refuse de démarrer avec le chemin de
 * construction : le test échoue honnêtement, il ne compile pas à la place de quelqu'un.
 *
 * Le serveur est le petit statique de ce dossier (`serve.mjs`) — la méthode de la sonde
 * (PROBE-NOTES.md, Q4 : un statique bête suffit, aucun en-tête spécial).
 *
 * Versions et navigateurs : `@playwright/test` est ÉPINGLÉ sur la même version que le
 * driver de contrats (`kadre/contracts/driver/web/package.json`, 1.60.0) — les deux
 * projets partagent donc la MÊME révision de navigateur dans le cache Playwright par
 * défaut de la machine (aucun `PLAYWRIGHT_BROWSERS_PATH` à poser, aucun second
 * téléchargement ; seul un `npx playwright install chromium` est requis sur une machine
 * qui n'a jamais couru le driver). Portée : chromium seulement — c'est le filet de
 * régression d'un sample, pas le driver de contrats ; firefox/webkit hors périmètre.
 *
 * Sorties : `playwright-output/` (gitignoré) — captures du spec dans `screenshots/`,
 * artefacts d'échec de Playwright (traces, `screenshot: only-on-failure`) à la racine.
 * Le spec commis EST le filet ; ses artifacts ne le sont pas.
 */

import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig } from '@playwright/test';

const dist =
  process.env.KADRE_DEMO_DIST ??
  fileURLToPath(new URL('../build/dist/wasmJs/productionExecutable/', import.meta.url));

if (!existsSync(dist)) {
  throw new Error(
    `Distribution du demo introuvable : ${dist}\n` +
      'Construis-la d abord : ./gradlew :kadre:samples:desk-tour:wasmJsBrowserDistribution\n' +
      '(ou pointe KADRE_DEMO_DIST vers une distribution existante)',
  );
}

const port = Number(process.env.KADRE_DEMO_PORT ?? 4173);

export default defineConfig({
  testDir: '.',
  testMatch: 'desk-tour-web.spec.mjs',
  // Filet grossier, séquentiel, sans retente : un vert douteux est un mensonge —
  // la barre du task est DEUX verts consécutifs, pas une retente qui les fabrique.
  timeout: 60_000,
  retries: 0,
  workers: 1,
  outputDir: process.env.KADRE_E2E_OUTPUT_DIR ?? 'playwright-output',
  reporter: [['list']],
  use: {
    headless: true,
    baseURL: `http://127.0.0.1:${port}`,
    viewport: { width: 1400, height: 1000 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'node serve.mjs',
    cwd: fileURLToPath(new URL('.', import.meta.url)),
    port,
    env: { KADRE_DEMO_DIST: dist, KADRE_DEMO_PORT: String(port) },
    reuseExistingServer: !process.env.CI,
    timeout: 15_000,
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
});
