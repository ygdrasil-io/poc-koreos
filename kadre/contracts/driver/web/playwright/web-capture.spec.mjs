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
 *
 * BCK-011 — the session scenarios — run only in the `chromium-capture` project, whose launch
 * arguments answer the consent the browser's own sanctioned test way. The probe that decided this
 * posture, on this exact pinned Playwright and its Chromium 148 headless build:
 *
 * - `context.grantPermissions(['display-capture'])` is unavailable — Playwright 1.60 dropped the
 *   permission name from its protocol mapping.
 * - `--auto-select-desktop-capture-source` alone is refused `NotSupportedError`: the historical
 *   picker bypass no longer answers on its own.
 * - A real compositor capture is refused in headless (`NotSupportedError` / `NotReadableError`
 *   on macOS arm64 unless the fake device is in play).
 * - With `--auto-select-desktop-capture-source=screen`, `--use-fake-ui-for-media-stream` and
 *   `--use-fake-device-for-media-stream`, the browser itself answers `getDisplayMedia`: the fake
 *   UI replaces the interactive picker (the same substitution the flag has always made for the
 *   camera and microphone prompts) and Chromium's sanctioned synthetic screen source stands in
 *   for the compositor. Everything else is the browser's real machinery: the consent call, the
 *   granted `MediaStreamTrack`, the `MediaStreamTrackProcessor` pipe, the delivered `VideoFrame`s
 *   and the track stop.
 *
 * So the session evidence's posture is the real consent path and the real frame pipe, with the
 * browser's own test source where real pixels would be; the real picker UI and the real
 * compositor belong to the manual charter, exactly the phase-6 gamepad precedent restated for the
 * consent flow. The open is still activation-shaped — each scenario drives a real `page.click()`
 * on the fixture's own button, the gesture a real consent asks for — though under the fake UI the
 * activation is not load-bearing (the browser answers without it; probed). The canaries stay
 * armed: the record's exactly-one `getDisplayMedia` entry IS the proof that the consent was
 * explicit, and the Surface scenario's empty record proves the canvas stream needs none.
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

/**
 * BCK-011 — the capture session scenarios (the `chromium-capture` project only; see the header for
 * the probe and the posture it decided). The fixture is the `capture-session` scenario: a canvas
 * attach element the fixture paints on a timer, one open command per target (the host choice
 * behind a real button, the Surface behind a plain command), one collector, one stop.
 *
 * Every frame assertion is structural — sizes, formats, configuration revisions, counts, terminal
 * outcomes — never pixel content, which is not a contract. The track facts are the page's own:
 * `capture-stub.mjs` enrolls every granted track, counts the `stop()` calls it forwards and reads
 * the browser's own `readyState` back, so a capture whose track was never stopped, and a stream
 * that leaked, are facts a spec can fail on.
 */

/**
 * Loads a capture session scenario and waits for the attach to have succeeded: the manager and the
 * surface the commands drive exist, and every command listener was installed before readiness.
 */
async function sessionScenario(page, scenario = 'capture-session') {
  await installCaptureStub(page);
  await loadScenario(page, scenario);
  const host = page.locator('[data-kadre-host="capture-session"]');
  await expect(host).toHaveAttribute('data-kadre-attach', 'success');
  return host;
}

/** The granted tracks the stub enrolled: source, forwarded stop count, the browser's own state. */
async function trackFacts(page) {
  return page.evaluate(() => window.__kadreCaptureStub.trackFacts());
}

/** Waits until the frame counter has reached [count] — the stream really is delivering. */
async function expectFramesAtLeast(host, count) {
  await expect
    .poll(() => host.getAttribute('data-kadre-capture-frames').then(Number), { timeout: 5_000 })
    .toBeGreaterThanOrEqual(count);
}

/** The configuration one journal entry publishes: `streaming-started@rev=N:size=WxH:format=F`. */
function parseConfiguration(entry) {
  const match = /^streaming-started@rev=(\d+):size=(\d+x\d+):format=([A-Za-z0-9]+)$/.exec(entry);
  expect(match, `not a streaming-started journal entry: ${entry}`).not.toBeNull();
  return { rev: match[1], size: match[2], format: match[3] };
}

test('web-capture-hostchoice-stream', async ({ page }) => {
  const host = await sessionScenario(page);

  // Before the gesture: no consent machinery of any name has been touched, and no session exists.
  expect(await prompts(page)).toEqual([]);
  expect(await host.getAttribute('data-kadre-capture-open')).toBeNull();

  // The open is the click: a real user activation drives the one explicit request path, and the
  // canary record holds exactly that one consent call — the picker and nothing else.
  await page.click('[data-kadre-capture-open-button="host-choice"]');
  await expect(host).toHaveAttribute('data-kadre-capture-open', 'success');
  expect(await prompts(page)).toEqual(['getDisplayMedia']);

  // The consented stream streams: the collector is admitted, the session publishes Streaming at
  // configuration revision 0, and real frames of the real pipe arrive.
  await command(page, 'kadre-capture-collect');
  await expect(host).toHaveAttribute('data-kadre-capture-session-state', 'streaming:rev=0');
  await expectFramesAtLeast(host, 2);

  // The frames agree with the published configuration, structurally: every delivered frame names
  // the configuration's own size and format at the revision the journal published. Nothing about
  // the pixels is asserted — the source is the browser's, the structure is the contract's.
  const journal = (await host.getAttribute('data-kadre-capture-session-events')).split(';');
  const configuration = parseConfiguration(journal[0]);
  for (const fact of (await host.getAttribute('data-kadre-capture-frame-facts')).split(';')) {
    expect(fact).toBe(`${configuration.size}:${configuration.format}:rev=${configuration.rev}`);
  }

  // The consent was spent once: the record has not moved since the open.
  expect(await prompts(page)).toEqual(['getDisplayMedia']);
});

test('web-capture-configuration-before-frame', async ({ page }) => {
  const host = await sessionScenario(page);

  await page.click('[data-kadre-capture-open-button="host-choice"]');
  await expect(host).toHaveAttribute('data-kadre-capture-open', 'success');
  await command(page, 'kadre-capture-collect');
  await expectFramesAtLeast(host, 1);

  // The ordering, as an observation: the state cell the fixture read at the moment frame one was
  // delivered already carried the streaming configuration — a session whose configuration trailed
  // its frames would have recorded `ready` there or nothing at all. The journal's first entry is
  // the StreamingStarted of that very configuration, and every frame fact names it.
  await expect(host).toHaveAttribute('data-kadre-capture-first-frame', 'streaming:rev=0');
  const journal = (await host.getAttribute('data-kadre-capture-session-events')).split(';');
  const configuration = parseConfiguration(journal[0]);
  expect(configuration.rev).toBe('0');
  const facts = (await host.getAttribute('data-kadre-capture-frame-facts')).split(';');
  expect(facts[0]).toBe(`${configuration.size}:${configuration.format}:rev=0`);

  // The source is stable, so the configuration is too: no reconfiguration event trails the first,
  // and the state stays exactly where the first frame found it.
  await expectFramesAtLeast(host, 3);
  await expect(host).toHaveAttribute('data-kadre-capture-session-events', journal.join(';'));
  await expect(host).toHaveAttribute('data-kadre-capture-session-state', 'streaming:rev=0');
});

test('web-capture-frames-bounded', async ({ page }) => {
  // The session bound is the fixture's own policy: 1024 bytes, far below any frame the granted
  // source delivers — the very first frame cannot fit, which is the oversized-frame terminal.
  const host = await sessionScenario(page, 'capture-bounded');

  await page.click('[data-kadre-capture-open-button="host-choice"]');
  await expect(host).toHaveAttribute('data-kadre-capture-open', 'success');
  expect(await prompts(page)).toEqual(['getDisplayMedia']);

  // The terminal: the bound is read before any buffer exists, so the start fails with the exact
  // limit the policy named, the session terminates Failed with the same failure, and nothing was
  // ever delivered — no frame counter moved, no configuration was ever published.
  await command(page, 'kadre-capture-collect');
  await expect(host).toHaveAttribute(
    'data-kadre-capture-collect',
    'failure:resourceLimitExceeded:capturebuffer:1024',
  );
  await expect(host).toHaveAttribute(
    'data-kadre-capture-session-state',
    'terminated:failed:resourceLimitExceeded:capturebuffer:1024',
  );
  await expect(host).toHaveAttribute(
    'data-kadre-capture-outcome',
    'failed:resourceLimitExceeded:capturebuffer:1024',
  );
  await expect(host).toHaveAttribute('data-kadre-capture-frames', '0');
  await expect(host).toHaveAttribute('data-kadre-capture-frame-facts', '');
  await expect(host).toHaveAttribute('data-kadre-capture-session-events', '');

  // A failed start never reserves frames it cannot pump: the pick the browser granted was released
  // by the very failure — the track stopped once and the browser says it ended.
  expect(await trackFacts(page)).toEqual([
    { source: 'display-capture', stopCalls: 1, readyState: 'ended' },
  ]);
});

test('web-capture-stop-exactly-once', async ({ page }) => {
  const host = await sessionScenario(page);

  await page.click('[data-kadre-capture-open-button="host-choice"]');
  await expect(host).toHaveAttribute('data-kadre-capture-open', 'success');
  await command(page, 'kadre-capture-collect');
  await expectFramesAtLeast(host, 2);

  // The requested stop is the terminal: Stopped(Requested), published once, and the collector's
  // own request answers success with exactly that outcome.
  await command(page, 'kadre-capture-stop');
  await expect(host).toHaveAttribute('data-kadre-capture-outcome', 'stopped:requested');
  await expect(host).toHaveAttribute('data-kadre-capture-session-state', 'terminated:stopped:requested');
  await expect(host).toHaveAttribute('data-kadre-capture-collect', 'success');

  // Track-stop-immediate: the browser's own track ended by the time the outcome was published —
  // read straight away, with no settling, because the stop path releases before it terminates.
  expect(await trackFacts(page)).toEqual([
    { source: 'display-capture', stopCalls: 1, readyState: 'ended' },
  ]);

  // No-frame-after-stop: the counters and the journal the terminal left behind are frozen across a
  // quiet window — no late delivery, no re-emission, no second configuration.
  const framesAtStop = await host.getAttribute('data-kadre-capture-frames');
  const factsAtStop = await host.getAttribute('data-kadre-capture-frame-facts');
  const journalAtStop = await host.getAttribute('data-kadre-capture-session-events');
  await settleQuietly(page);
  await expect(host).toHaveAttribute('data-kadre-capture-frames', framesAtStop);
  await expect(host).toHaveAttribute('data-kadre-capture-frame-facts', factsAtStop);
  await expect(host).toHaveAttribute('data-kadre-capture-session-events', journalAtStop);
  expect(await trackFacts(page)).toEqual([
    { source: 'display-capture', stopCalls: 1, readyState: 'ended' },
  ]);

  // Exactly once: a second stop is inert. The terminal outcome is already published, the collector
  // has its one answer, and no consent machinery ever ran again.
  await command(page, 'kadre-capture-stop');
  await settleQuietly(page);
  await expect(host).toHaveAttribute('data-kadre-capture-outcome', 'stopped:requested');
  await expect(host).toHaveAttribute('data-kadre-capture-session-state', 'terminated:stopped:requested');
  await expect(host).toHaveAttribute('data-kadre-capture-collect', 'success');
  expect(await prompts(page)).toEqual(['getDisplayMedia']);

  // No-stream-leak: zero live handles — every track the page was granted has ended.
  for (const fact of await trackFacts(page)) {
    expect(fact.readyState).toBe('ended');
  }
});

test('web-capture-surface-canvas-stream', async ({ page }) => {
  const host = await sessionScenario(page);

  // The host is the painted canvas itself: the page's own paint counter proves frames have a
  // reason to exist (the first tick lands 33ms in, so the read waits for it), and no consent
  // machinery has been touched — a canvas stream needs no picker.
  await expect
    .poll(() => host.getAttribute('data-kadre-capture-paints').then(Number), { timeout: 5_000 })
    .toBeGreaterThan(0);
  expect(await prompts(page)).toEqual([]);

  // The Surface open: the session's own registered primary surface, carrying the region only a
  // surface may carry. The record stays empty — the open is no consent, and nothing invented one.
  await command(page, 'kadre-capture-open-surface');
  await expect(host).toHaveAttribute('data-kadre-capture-open-surface', 'success');
  expect(await prompts(page)).toEqual([]);

  // The canvas's own stream streams, and the crop is the configuration's own: the region the
  // request carried is the size the configuration publishes and every delivered frame keeps.
  await command(page, 'kadre-capture-collect');
  await expect(host).toHaveAttribute('data-kadre-capture-session-state', 'streaming:rev=0');
  await expect(host).toHaveAttribute('data-kadre-capture-first-frame', 'streaming:rev=0');
  await expectFramesAtLeast(host, 1);
  const journal = (await host.getAttribute('data-kadre-capture-session-events')).split(';');
  const configuration = parseConfiguration(journal[0]);
  expect(configuration).toEqual({ rev: '0', size: '32x32', format: 'Rgba8' });
  for (const fact of (await host.getAttribute('data-kadre-capture-frame-facts')).split(';')) {
    expect(fact).toBe('32x32:Rgba8:rev=0');
  }

  // And the teardown is the same exactly-once stop, with the canvas track released like any other.
  await command(page, 'kadre-capture-stop');
  await expect(host).toHaveAttribute('data-kadre-capture-outcome', 'stopped:requested');
  expect(await trackFacts(page)).toEqual([
    { source: 'canvas-capture-stream', stopCalls: 1, readyState: 'ended' },
  ]);
  expect(await prompts(page)).toEqual([]);
});
