const playwrightArguments = ['test', '--config', 'playwright/playwright.config.mjs'];

/**
 * The Playwright projects one engine's smoke runs.
 *
 * The config holds every engine's projects, so a plain `playwright test` would run them all: the
 * launch selects the engine's own projects explicitly. Chromium names exactly the three projects
 * a no-argument invocation ran before the other engines existed — the same three, in the config's
 * order — so the PR gate's routing is unchanged. Firefox and WebKit are each split per Kotlin
 * target because each carries wasmJs-only exclusions (the TypeScript publication race on Firefox;
 * the duplicate-attach admission race on both); the js run keeps those tests through the `-js`
 * project.
 */
function engineProjects(engine, target) {
  switch (engine) {
    case 'chromium':
      return ['chromium', 'chromium-capture', 'chromium-insecure'];
    case 'firefox':
      return [target === 'wasmJs' ? 'firefox-wasm' : 'firefox-js'];
    case 'webkit':
      return [target === 'wasmJs' ? 'webkit-wasm' : 'webkit-js'];
    default:
      throw new Error(`unsupported browser engine: ${engine} (expected one of chromium, firefox or webkit)`);
  }
}

export function createPlaywrightLaunch(platform, baseOptions, engine, target) {
  const windows = platform === 'win32';
  return {
    command: windows ? 'node_modules/.bin/playwright.cmd' : 'node_modules/.bin/playwright',
    argumentsList: [
      ...playwrightArguments,
      ...engineProjects(engine, target).map((project) => `--project=${project}`),
    ],
    options: {
      ...baseOptions,
      detached: !windows,
      ...(windows ? { shell: true } : {}),
    },
  };
}
