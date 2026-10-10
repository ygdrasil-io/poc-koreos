import { createHash } from 'node:crypto';
import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;
/** The Kotlin target this smoke runs, named by the runner the way `KADRE_FIXTURE_URL` is. */
const smokeTarget = process.env.KADRE_SMOKE_TARGET;
/** The fixture serves the published `@kadre/host` package and the compiled consumer under `/host`. */
const hostPrefix = '/host';
const hostRoot = new URL(`${hostPrefix}/`, fixtureUrl);

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

/** The SHA-256 of the served bytes, compared against the digests provenance.json records. */
function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

/** One response served by the fixture under `/host`; the assertion pins the 404 failure cause. */
async function servedHostResponse(path) {
  const response = await fetch(new URL(path, hostRoot));
  expect(response.ok, `the fixture must serve ${hostPrefix}/${path}`).toBe(true);
  return response;
}

/**
 * The published `@kadre/host` shim, driven by the TypeScript consumer in the page.
 *
 * The page resolves the bare specifier `@kadre/host` through its import map to the shim served
 * straight from the published package, and the page's Kotlin application publishes the bindings of its
 * own module instance and the opaque factory key. The consumer is the same source that
 * `kadre/consumers/typescript` type-checks: it attaches, observes the session state, unsubscribes,
 * stops the session and awaits the terminal outcome, writing `"passed"` only when every step held.
 *
 * Provenance: the served package must BE the publication's output, not a look-alike. The host-page
 * task reads the published archive itself — its Maven coordinates, the `.sha256` sidecar the
 * publication wrote, and one SHA-256 per zip entry — and records all of it in `provenance.json`
 * beside the served files. This spec serves that record through the same fixture server, then
 * verifies every served file byte-for-byte against it, so a served tree mutated after publication
 * (or a fixture still wired to the build-tree package, which carries no record) fails here.
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

  // The import map the page actually served: `@kadre/host` must resolve inside the `/host` root,
  // and the entry it names is one of the digested archive files verified below — so the module the
  // consumer executed is provably the published shim.
  const importMap = await page.evaluate(() => {
    const element = document.querySelector('script[type="importmap"]');
    return element === null ? null : JSON.parse(element.textContent);
  });
  expect(importMap?.imports?.['@kadre/host']).toBe(`${hostPrefix}/index.mjs`);

  // Provenance: coordinates first (the runner names the Kotlin target of this run), then every
  // served byte against the publication's own digests.
  expect(smokeTarget, 'the runner must name the Kotlin target of this smoke').toMatch(/^(js|wasmJs)$/);
  const provenance = JSON.parse(await (await servedHostResponse('provenance.json')).text());
  expect(provenance.group).toBe('org.graphiks.kadre');
  expect(provenance.artifact).toBe('web-host-package');
  expect(provenance.classifier).toBe(smokeTarget);
  expect(provenance.version, 'the record must identify a version').toMatch(/^[\w.\-]+$/);
  expect(provenance.files['index.mjs'], 'the import map\'s target must be a digested entry').toBeDefined();
  expect(provenance.files['index.d.ts'], 'the curated declaration must be a digested entry').toBeDefined();

  const manifest = JSON.parse(await (await servedHostResponse('package.json')).text());
  expect(manifest.name).toBe('@kadre/host');
  expect(manifest.version).toBe(provenance.version);

  for (const [name, digest] of Object.entries(provenance.files)) {
    const served = new Uint8Array(await (await servedHostResponse(name)).arrayBuffer());
    expect(sha256(served), `served ${name} must match the published digest`).toBe(digest);
  }

  expect(pageErrors).toEqual([]);
});
