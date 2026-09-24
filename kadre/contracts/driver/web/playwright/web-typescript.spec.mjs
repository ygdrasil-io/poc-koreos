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
 * The published `@kadre/host` package, running the TypeScript consumer in the page.
 *
 * The page resolves the bare specifier `@kadre/host` through its import map, so the consumer is the
 * same source that `kadre/consumers/typescript` type-checks: it attaches through the published
 * `index.mjs` shim, observes the session state, unsubscribes, stops the session and awaits the
 * terminal outcome, writing `"passed"` only when every step held.
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

  // The published module loaded in the same page, and it really declares the bindings the shim
  // loads. The application's own bindings drive the shim, so this is the check that the shipped
  // Kotlin module is present and complete in the served package.
  // Read the binding shapes inside the page: function values do not survive serialization.
  const published = await page.evaluate((names) => {
    const bindings = globalThis['kadre-published-host-bindings'] ?? null;
    return bindings === null
      ? null
      : Object.fromEntries(names.map((name) => [name, typeof bindings[name]]));
  }, hostBindingNames);
  expect(published).not.toBeNull();
  for (const name of hostBindingNames) {
    expect(published[name]).toBe('function');
  }
  expect(pageErrors).toEqual([]);
});
