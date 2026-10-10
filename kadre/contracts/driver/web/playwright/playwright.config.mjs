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

//
// The nightly engines of the roadmap's section 3.8 — Firefox and WebKit. Every exclusion below is
// a recorded engine fact of the probe matrix in the README's "Engine coverage" section (the
// 2026-10-10 probe, pinned Playwright 1.60.0: Firefox 150.0.2, WebKit 26.4), stated with its
// cause; nothing is excluded "temporarily", no runtime skip exists anywhere, and a future engine
// revision that ships the primitive restores a scenario by deleting its grep. The two macOS-WebKit
// input divergences the probe catalogued (`web-input-pointer-multi`,
// `web-input-default-behavior`) are deliberately NOT excluded: they pass on Linux WebKit, which is
// what the nightly runs.
//

// CDP-instrumented, both engines: the scenario drives the browser through
// `context.newCDPSession`, which Playwright answers `CDP session is only available in Chromium`
// on Firefox and WebKit (probed) — the seven touch scenarios staged through
// `Input.dispatchTouchEvent`, the two display scenarios through
// `Emulation.setDeviceMetricsOverride`, and the text-input composition lifecycle through
// `Input.imeSetComposition`. The CDP-free scenarios of the same suites
// (`web-touch-capability-structural`, `web-display-initial-hostviewport`,
// `web-display-resize-propagation`, the other eight text-input scenarios on WebKit) run.
const cdpTouchScenarios =
  /web-touch-(started|ended|cancelled|multi-contact|interaction-trigger|focus-loss-clears|no-pointer-alias)/;
const cdpDisplayScenarios = /web-display-(dpr-scale-factor|teardown-quiet)/;
const cdpTextInputScenarios = /web-text-composition-lifecycle/;

// Engine-missing-primitive, both engines: the capture frame pump is
// `MediaStreamTrackProcessor`-only and neither engine ships it (probed `typeof` on both), the
// permission readback cannot settle (`permissions.query` rejects the `display-capture` name on
// both) and the Chromium consent-test flag trio is accepted-but-inert — the whole suite, control
// plane and sessions alike.
const captureSuite = /web-capture/;

// Other-named, Firefox only: `keyboard.insertText` routes through the Firefox composition
// pipeline, so the five scenarios that stage one text arrival without a composition receive
// `composition:…` journals where Chromium and WebKit publish `replace:…` journals (probed) — an
// honest engine divergence in the stimulus, not a runtime defect. The three scenarios the suite
// keeps (cancelled, stale-revision, action-submit) stage no such arrival.
const firefoxCompositionPipelineScenarios =
  /web-text-(open-single-session|replace-event|focus-suspends|writeback-sync|teardown-closes)/;

// Other-named, Firefox wasmJs only: the publication race — Firefox evaluates the deferred
// consumer module before the asynchronously instantiated Wasm bundle has published its bindings,
// and the shim honestly reports `missing-application-factory` (three of four probe runs; js, both
// WebKit targets and Chromium are unaffected). A Playwright project cannot grep by Kotlin target,
// so the exclusion lives on the `firefox-wasm` project alone and the js run keeps the suite
// through `firefox-js`: the launch selects the project per engine AND target, which is the exact
// shape of the finding — engine firefox, target wasmJs, never the whole engine.
const firefoxWasmPublicationRace = /web-typescript-consumer/;

// Other-named, Firefox wasmJs only: the duplicate-attach admission-race scenario reads the
// fixture's MutationObserver/focusin counters immediately after goto, before the asynchronously
// instantiated wasm fixture attaches on Firefox — failed 3/3 in-suite in the Task-2 container
// runs while passing isolated and on js, the same Firefox-deferred-instantiation family as the
// publication race above. The follow-up is a data-kadre-ready wait before counting; until then
// the exclusion is recorded at the contract level in
// contracts/engine-exclusions/firefox.tsv (BCK-001/wasmJs) and this grep is its test-level
// mirror — one test carrying three BCK-001 rows (web-attach-detach-admission-race,
// web-attach-same-element-busy and the web-host-single-owner sentinel).
const firefoxWasmDuplicateAttachRace = /a duplicate attach is rejected/;

// Chromium-flags-dependent, WebKit only: the insecure-context scenario needs the fixture's
// insecure name mapped onto the local server, and WebKit has neither a launch argument nor a
// preference system for that (probed; Firefox uses `network.dns.localDomains`, below).
const webkitNoInsecureRoute = /web-gamepad-effect-insecure-context/;

// The Firefox route for the insecure name. The preference names a host no other scenario
// navigates, so the regular suite observes nothing from it — and the insecure-context scenario
// RUNS on Firefox (probed on both Kotlin targets), it is not excluded. It must sit inside
// `use.launchOptions.firefoxUserPrefs`: Playwright 1.60 silently ignores a `use`-level
// `firefoxUserPrefs` (probed twice — only the nested placement reaches the browser).
const firefoxLaunch = {
  browserName: 'firefox',
  launchOptions: {
    firefoxUserPrefs: { 'network.dns.localDomains': 'insecure.kadre.invalid' },
  },
};

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
    // The Firefox projects, one per Kotlin target — the wasmJs-only TypeScript exclusion above is
    // the single difference between them. Nothing here is launched by the PR gate: the launch
    // script selects `firefox-js` or `firefox-wasm` only for a run whose engine is firefox.
    {
      name: 'firefox-js',
      use: { ...firefoxLaunch },
      grepInvert: [
        cdpTouchScenarios,
        cdpDisplayScenarios,
        cdpTextInputScenarios,
        firefoxCompositionPipelineScenarios,
        captureSuite,
      ],
    },
    {
      name: 'firefox-wasm',
      use: { ...firefoxLaunch },
      grepInvert: [
        cdpTouchScenarios,
        cdpDisplayScenarios,
        cdpTextInputScenarios,
        firefoxCompositionPipelineScenarios,
        captureSuite,
        firefoxWasmPublicationRace,
        firefoxWasmDuplicateAttachRace,
      ],
    },
    {
      name: 'webkit',
      use: { browserName: 'webkit' },
      grepInvert: [
        cdpTouchScenarios,
        cdpDisplayScenarios,
        cdpTextInputScenarios,
        captureSuite,
        webkitNoInsecureRoute,
      ],
    },
  ],
});
