import { defineConfig } from '@playwright/test';

const junitOutput = process.env.KADRE_JUNIT_OUTPUT;
const outputDir = process.env.KADRE_PLAYWRIGHT_OUTPUT_DIR;

if (!junitOutput || !outputDir) {
  throw new Error('KADRE_JUNIT_OUTPUT and KADRE_PLAYWRIGHT_OUTPUT_DIR are required');
}

// Three projects, one invocation: the regular suite in the browser the smokes have always run; the
// insecure-context scenario alone in a browser whose host resolution maps the fixture's insecure
// name onto the same local server, so the page is served over plain http on a non-localhost host
// and the browsing context itself is not a secure context; and the BCK-011 capture session
// scenarios, whose consent flow needs the browser's own sanctioned capture-test arguments (the
// probe and the posture it decided are recorded in the web-capture spec header): the fake UI
// stands in for the interactive picker, the auto-select answer picks the screen, and Chromium's
// synthetic screen source stands in for the compositor — a real headless compositor capture is
// refused on this platform, and Playwright 1.60 no longer maps `display-capture` for permission
// grants. The arguments live on the capture project alone: no other test runs with them, and the
// session scenarios never run without them.
const captureSessionScenarios =
  /web-capture-(hostchoice-stream|configuration-before-frame|frames-bounded|stop-exactly-once|surface-canvas-stream)/;

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
    'web-capture.spec.mjs',
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
  // The split keeps every project honest: the regular suite runs without any special argument, the
  // insecure scenario never runs without its host resolution (its navigation could not resolve
  // anywhere else), and the session scenarios never run without the consent arguments above.
  projects: [
    {
      name: 'chromium',
      grepInvert: [
        /web-gamepad-effect-insecure-context/,
        /web-capture-insecure-unsupported/,
        captureSessionScenarios,
      ],
    },
    {
      name: 'chromium-capture',
      testMatch: ['web-capture.spec.mjs'],
      grep: [captureSessionScenarios],
      use: {
        launchOptions: {
          args: [
            '--auto-select-desktop-capture-source=screen',
            '--use-fake-ui-for-media-stream',
            '--use-fake-device-for-media-stream',
          ],
        },
      },
    },
    {
      name: 'chromium-insecure',
      testMatch: ['web-gamepad-effects.spec.mjs', 'web-capture.spec.mjs'],
      grep: [/web-gamepad-effect-insecure-context/, /web-capture-insecure-unsupported/],
      use: {
        launchOptions: {
          args: ['--host-resolver-rules=MAP insecure.kadre.invalid 127.0.0.1'],
        },
      },
    },
  ],
});
