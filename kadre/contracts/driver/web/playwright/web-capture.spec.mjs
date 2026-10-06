import { expect, test } from '@playwright/test';
import { installCaptureStub } from './capture-stub.mjs';

/**
 * BCK-010 — the web capture control-plane contract.
 *
 * The control plane these scenarios prove is the honest snapshot a real Chromium freezes at
 * attach: the sources are the host picker and nothing else (no browser enumerates capturable
 * screens or windows before a consent, so there is no inventory to publish and none is invented),
 * the enumeration capability states that unconditionally, the screen/window/surface capabilities
 * are the frozen truth table of the port's presence probes on a canvas attach element, and the
 * permissions are the settled `display-capture` readback mirrored across both scopes. What the
 * specs prove is what the runtime publishes about the real browsing context — never a fabricated
 * inventory, never a capability the browser did not offer, never a prompting API touched outside
 * an explicit request.
 *
 * The canaries are `capture-stub.mjs`: every prompting API of the page is patched to
 * record-then-forward before any script runs, so a spec asserting an empty record has caught every
 * attempt there was, and the `permissions.query` spy counts the one call the port's own readback
 * is allowed. A `getDisplayMedia` call during readback, settlement or refresh would forward to the
 * real picker AND land in the record — either way the spec fails.
 *
 * The refused-open scenario stages the structurally refused request: a region-carrying open, which
 * the port refuses `Unsupported(CaptureOpen)` before any picker (the browser picks its own
 * bounds). The plan's literal drive — a `CaptureTarget.Source` request — cannot be constructed
 * from this driver (the capture source id's constructor is internal to the foundation module), and
 * the runtime's own admission refuses source targets before the port is ever reached; the port's
 * Source row stays pinned by the platform's unit tests on both targets. The refused-before-any-
 * picker gate this scenario proves is the same admission funnel, with the same pinned failure
 * cell, and the sentinel that rides it (an inventory invented to make an open resolvable) reads
 * the same sources cell the initial scenario pins.
 *
 * The insecure-context scenario runs only in the `chromium-insecure` project (see
 * web-gamepad-effects.spec.mjs): the same served page over plain http on a non-localhost name,
 * where the browsing context itself is not a secure context and every capture capability is the
 * honest unsupported with the secure-context cause on the picker.
 */

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

/**
 * The settled permission cells: one honest mapped answer of the readback, mirrored across both
 * scopes. The transient pre-settlement state (`unavailable:unsupported:capturepermission`) is
 * exactly what this pattern refuses to accept, so waiting on it is waiting for the readback.
 */
const mirroredSettledPermissions =
  /screen=(granted|notDetermined|denied:canRequestAgain=true):window=\1/;

/**
 * The permission cells of a browsing context whose readback may or may not be answerable: the
 * same honest mapped answers, plus the unresolved mapping for the browser that cannot answer —
 * always mirrored, never divergent.
 */
const mirroredPermissionCells =
  /screen=(granted|notDetermined|denied:canRequestAgain=true|unavailable:unsupported:capturepermission):window=\1/;

async function loadScenario(page, scenario) {
  await page.goto(`${fixtureUrl}?scenario=${scenario}`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
}

/** The same served page, through the insecure name the insecure project's launch argument maps. */
function insecureScenarioUrl(scenario) {
  const url = new URL(fixtureUrl);
  url.hostname = 'insecure.kadre.invalid';
  url.search = `?scenario=${scenario}`;
  return url.toString();
}

/** Dispatches one of the fixture's command events; every command is driven from the browser side. */
async function command(page, name) {
  await page.evaluate((event) => document.dispatchEvent(new Event(event)), name);
}

/** The prompting APIs the page touched so far — the record the no-implicit-prompt sentinel reads. */
async function prompts(page) {
  return page.evaluate(() => window.__kadreCaptureStub.prompts);
}

/** The readback queries the page made so far — the spy the no-picker-at-readback sentinel reads. */
async function permissionQueries(page) {
  return page.evaluate(() => window.__kadreCaptureStub.permissionQueries);
}

/**
 * Loads the capture scenario and waits for the readback to settle: the mirrored permission cells
 * name an honest mapped answer, which only the settled snapshot carries.
 */
async function settledSnapshot(page) {
  await installCaptureStub(page);
  await loadScenario(page, 'capture');
  const host = page.locator('[data-kadre-host="capture"]');
  await expect(host).toHaveAttribute('data-kadre-capture-permissions', mirroredSettledPermissions);
  return host;
}

/**
 * Settles the page without touching anything it counts: the canary record and the manager
 * revision are read before and after, so waits must not register as page activity.
 */
async function settleQuietly(page) {
  for (let round = 0; round < 2; round += 1) {
    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 100)));
  }
}

/** The capability cells a secure canvas attach element freezes, per the port's truth table. */
const screenCell =
  'supported[formats=I420+Nv12+Rgba8:cursor=Embedded+EmbeddedWhenAvailable+Hidden:region=unsupported]:available';

test('web-capture-initial-honest', async ({ page }) => {
  const host = await settledSnapshot(page);

  // The settled readback published exactly once: the manager's revision moved from the transient
  // initial state to the settled answer, and never further.
  await expect(host).toHaveAttribute('data-kadre-capture-revision', '1');

  // The inventory is the host picker and nothing else — the browser's own consent boundary, never
  // an enumerated list invented for it — and the enumeration capability says so unconditionally.
  await expect(host).toHaveAttribute('data-kadre-capture-sources', 'hostPickerOnly');
  await expect(host).toHaveAttribute('data-kadre-capture-enumeration', 'supported');

  // The frozen truth table of this browsing context: screen and window name the constraint sets
  // the present primitives promise (the browser picks its own bounds, so no region), the window
  // cell mirrors the screen cell word for word — one browser consent governs whatever its picker
  // offers — and the canvas attach element's surface row is the real region-croppable one.
  await expect(host).toHaveAttribute('data-kadre-capture-capabilities-screen', screenCell);
  await expect(host).toHaveAttribute('data-kadre-capture-capabilities-window', screenCell);
  await expect(host).toHaveAttribute(
    'data-kadre-capture-capabilities-surface',
    'supported[formats=Rgba8:cursor=Hidden:region=available]:available',
  );
  await expect(host).toHaveAttribute('data-kadre-capture-host-picker', 'available');

  // The honest answer is held: whatever microtasks settle over a page that does nothing, the
  // snapshot stays exactly what the browser froze — no second publication, no drift.
  const revision = await host.getAttribute('data-kadre-capture-revision');
  await settleQuietly(page);
  await expect(host).toHaveAttribute('data-kadre-capture-revision', revision);
  await expect(host).toHaveAttribute('data-kadre-capture-permissions', mirroredSettledPermissions);
  await expect(host).toHaveAttribute('data-kadre-capture-sources', 'hostPickerOnly');
});

test('web-capture-readback-no-prompt', async ({ page }) => {
  const host = await settledSnapshot(page);

  // The readback rode the honest mechanism and only it: exactly one `permissions.query` — a query
  // never prompts — and the prompting record is empty. Attach and settlement touched no picker,
  // no camera, no permission request of any name.
  expect(await permissionQueries(page)).toBe(1);
  expect(await prompts(page)).toEqual([]);

  // The honest refresh: the manager re-answers with its current state at the current revision —
  // the same revision, published anew — and nothing is asked of the browser. A refresh that
  // enumerated, re-queried or prompted would move the spy, the record or the revision.
  const revision = await host.getAttribute('data-kadre-capture-revision');
  await command(page, 'kadre-capture-refresh');
  await expect(host).toHaveAttribute('data-kadre-capture-refresh', `success@${revision}`);
  await expect(host).toHaveAttribute('data-kadre-capture-revision', revision);
  expect(await permissionQueries(page)).toBe(1);
  expect(await prompts(page)).toEqual([]);

  // And the quiet window after it: no late consent machinery, no deferred picker.
  await settleQuietly(page);
  expect(await permissionQueries(page)).toBe(1);
  expect(await prompts(page)).toEqual([]);
});

test('web-capture-source-refused-before-picker', async ({ page }) => {
  const host = await settledSnapshot(page);
  const revision = await host.getAttribute('data-kadre-capture-revision');

  // The structurally refused open: a request that carries a region is refused
  // `Unsupported(CaptureOpen)` — the browser picks its own bounds, so this open is refused before
  // anything browser-facing. The canary proves the gate: zero prompting calls across the refusal.
  await command(page, 'kadre-capture-open-region');
  await expect(host).toHaveAttribute('data-kadre-capture-open', 'failure:unsupported:captureopen');
  expect(await prompts(page)).toEqual([]);

  // No source was invented to make the open resolvable: the inventory is exactly what the browser
  // consented to — the host picker and nothing else — and the refusal published nothing: no
  // revision, no republished snapshot, then or later.
  await expect(host).toHaveAttribute('data-kadre-capture-sources', 'hostPickerOnly');
  await settleQuietly(page);
  await expect(host).toHaveAttribute('data-kadre-capture-revision', revision);
  await expect(host).toHaveAttribute('data-kadre-capture-sources', 'hostPickerOnly');
  await expect(host).toHaveAttribute('data-kadre-capture-open', 'failure:unsupported:captureopen');
  expect(await prompts(page)).toEqual([]);
});

test('web-capture-insecure-unsupported', async ({ page }) => {
  await installCaptureStub(page);
  await page.goto(insecureScenarioUrl('capture'));
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
  // Fixture sanity: this page really is the insecure browsing context the scenario names.
  expect(await page.evaluate(() => window.isSecureContext)).toBe(false);
  const host = page.locator('[data-kadre-host="capture"]');

  // The honest unsupported of this context: every capture capability refuses with the one
  // operation it cannot run, and the picker's availability carries the secure-context cause.
  await expect(host).toHaveAttribute('data-kadre-capture-capabilities-screen', 'unsupported:captureopen');
  await expect(host).toHaveAttribute('data-kadre-capture-capabilities-window', 'unsupported:captureopen');
  await expect(host).toHaveAttribute('data-kadre-capture-capabilities-surface', 'unsupported:captureopen');
  await expect(host).toHaveAttribute(
    'data-kadre-capture-host-picker',
    'unavailable:platformFailure:web:capture-capability:secure-context',
  );

  // The pipeline is still honest where the context permits it: the inventory stays the host
  // picker and the enumeration capability states it unconditionally.
  await expect(host).toHaveAttribute('data-kadre-capture-sources', 'hostPickerOnly');
  await expect(host).toHaveAttribute('data-kadre-capture-enumeration', 'supported');

  // The permission cells carry the same honest mapping as anywhere — the settled readback answer
  // this browser gives (it answers the query even here), or the unresolved mapping where it
  // cannot — mirrored across both scopes. Which of the closed set a browsing context answers is
  // an observation, not a claim; that the two scopes never diverge is the claim.
  await expect(host).toHaveAttribute('data-kadre-capture-permissions', mirroredPermissionCells);

  // The refusal is structural, not a dialog the page dodged: no prompting API was ever touched,
  // and nothing drifts afterwards — no republished snapshot, no re-queried readback.
  const permissions = await host.getAttribute('data-kadre-capture-permissions');
  const revision = await host.getAttribute('data-kadre-capture-revision');
  await settleQuietly(page);
  expect(await prompts(page)).toEqual([]);
  await expect(host).toHaveAttribute('data-kadre-capture-permissions', permissions);
  await expect(host).toHaveAttribute('data-kadre-capture-revision', revision);
});
