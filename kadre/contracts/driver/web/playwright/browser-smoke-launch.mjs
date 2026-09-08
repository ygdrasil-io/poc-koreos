const playwrightArguments = ['test', '--config', 'playwright/playwright.config.mjs'];

export function createPlaywrightLaunch(platform, baseOptions) {
  const windows = platform === 'win32';
  return {
    command: windows ? 'node_modules/.bin/playwright.cmd' : 'node_modules/.bin/playwright',
    argumentsList: [...playwrightArguments],
    options: {
      ...baseOptions,
      detached: !windows,
      ...(windows ? { shell: true } : {}),
    },
  };
}
