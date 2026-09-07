import { defineConfig } from '@playwright/test';

const junitOutput = process.env.KADRE_JUNIT_OUTPUT;
const outputDir = process.env.KADRE_PLAYWRIGHT_OUTPUT_DIR;

if (!junitOutput || !outputDir) {
  throw new Error('KADRE_JUNIT_OUTPUT and KADRE_PLAYWRIGHT_OUTPUT_DIR are required');
}

export default defineConfig({
  testDir: '.',
  testMatch: ['web-phase0.spec.mjs', 'web-lifecycle.spec.mjs'],
  timeout: 30_000,
  retries: 0,
  workers: 1,
  reporter: [['junit', { outputFile: junitOutput }]],
  outputDir,
  use: {
    headless: true,
    trace: 'retain-on-failure',
  },
});
