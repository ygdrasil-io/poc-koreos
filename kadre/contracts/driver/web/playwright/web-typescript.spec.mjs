import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

/** The eight `@kadre/host` bindings of `kadre/INTEROP-EXPORTS.md` section 6. */
const hostBindingNames = [
  'kadreWebAttach',
  'kadreWebSessionId',
  'kadreWebSessionState',
  'kadreWebSubscribeState',
  'kadreWebSubscribeTermination',
  'kadreWebUnsubscribeState',
  'kadreWebRequestStop',
  'kadreWebClose',
];

/**
 * The published `@kadre/host` shim, driven by the TypeScript consumer in the page.
 *
 * The page resolves the bare specifier `@kadre/host` through its import map to the shim served
 * straight from the published package, and the page's Kotlin application publishes the bindings of its
 * own module instance and the opaque factory key. The consumer is the same source that
 * `kadre/consumers/typescript` type-checks: it attaches, observes the session state, unsubscribes,
 * stops the session and awaits the terminal outcome, writing `"passed"` only when every step held.
 */
test('web-typescript-consumer', async ({ page }) => {
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));

  await page.goto(`${fixtureUrl}?scenario=typescript-consumer`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
  await expect(page.locator('body')).toHaveAttribute(
    'data-kadre-typescript-consumer',
    'passed',
    { timeout: 15_000 },
  );

  // The application published all eight bindings into the shared registry the shim resolves from,
  // and it is the instance that owns the session the consumer just drove. Read the binding shapes
  // inside the page: function values do not survive serialization.
  const registry = await page.evaluate((names) => {
    const published = globalThis['org.graphiks.kadre:web'] ?? null;
    return published === null
      ? null
      : Object.fromEntries(names.map((name) => [name, typeof published[name]]));
  }, hostBindingNames);
  expect(registry).not.toBeNull();
  for (const name of hostBindingNames) {
    expect(registry[name]).toBe('function');
  }
  expect(pageErrors).toEqual([]);
});
