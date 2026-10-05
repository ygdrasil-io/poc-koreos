import { defineConfig } from '@playwright/test';

const junitOutput = process.env.KADRE_JUNIT_OUTPUT;
const outputDir = process.env.KADRE_PLAYWRIGHT_OUTPUT_DIR;

if (!junitOutput || !outputDir) {
  throw new Error('KADRE_JUNIT_OUTPUT and KADRE_PLAYWRIGHT_OUTPUT_DIR are required');
}

export default defineConfig({
  testDir: '.',
  testMatch: [
    'web-phase0.spec.mjs',
    'web-lifecycle.spec.mjs',
    'web-surface.spec.mjs',
    'web-input.spec.mjs',
    'web-interaction.spec.mjs',
    'web-window-provider.spec.mjs',
    'web-host-facade.spec.mjs',
    'web-typescript.spec.mjs',
    'web-touch.spec.mjs',
    'web-drop.spec.mjs',
    'web-text-input.spec.mjs',
    'web-display.spec.mjs',
    'web-devices.spec.mjs',
    'web-gamepad-effects.spec.mjs',
  ],
  timeout: 30_000,
  retries: 0,
  workers: 1,
  reporter: [['junit', { outputFile: junitOutput }]],
  outputDir,
  use: {
    headless: true,
    trace: 'retain-on-failure',
  },
  // Two projects, one invocation: the regular suite in the browser the smokes have always run, and
  // the insecure-context scenario alone in a browser whose host resolution maps the fixture's
  // insecure name onto the same local server, so the page is served over plain http on a
  // non-localhost host and the browsing context itself is not a secure context. The split keeps the
  // regular projects untouched: no other test runs with the argument, and the insecure scenario
  // never runs without it (its navigation could not resolve anywhere else).
  projects: [
    {
      name: 'chromium',
      grepInvert: [/web-gamepad-effect-insecure-context/],
    },
    {
      name: 'chromium-insecure',
      testMatch: ['web-gamepad-effects.spec.mjs'],
      grep: [/web-gamepad-effect-insecure-context/],
      use: {
        launchOptions: {
          args: ['--host-resolver-rules=MAP insecure.kadre.invalid 127.0.0.1'],
        },
      },
    },
  ],
});
