import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

async function loadScenario(page, scenario) {
  await page.goto(`${fixtureUrl}?scenario=${scenario}`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
}

/** Dispatches one of the fixture's command events; every command is driven from the browser side. */
async function command(page, name) {
  await page.evaluate((event) => document.dispatchEvent(new Event(event)), name);
}

/** Waits for one real animation frame of the page. */
async function nextFrame(page) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => resolve())));
}

/**
 * Settles the page without touching its animation-frame counter: the quiet sentinels count every
 * registration the page makes — whoever asks — so the waits that must not register one ride
 * `setTimeout` instead of the `nextFrame` probe.
 */
async function settleQuietly(page) {
  for (let round = 0; round < 2; round += 1) {
    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 100)));
  }
}

/** The exact manager cell the attached inventory must publish, at the manager revision named. */
function managerCell(revision) {
  return `rev=${revision}:enumerated:primary=0:displays=1:enumeration=supported`;
}

/** The journal after one more entry: the journal a spec reads may be empty before its first entry. */
function journalGrown(before, entry) {
  return before ? `${before};${entry}` : entry;
}

/** Reads the page's DOM node count through the fixture's own counter, waiting for the read. */
async function domCount(page, host) {
  const reads = Number(await host.getAttribute('data-kadre-display-dom-reads'));
  await command(page, 'kadre-display-dom-count');
  await expect(host).toHaveAttribute('data-kadre-display-dom-reads', String(reads + 1));
  return host.getAttribute('data-kadre-display-dom-count');
}

/**
 * The display-state cell the viewport's own measurement demands right now: the CSS viewport scaled
 * by the browsing context's device pixel ratio and rounded, one mode of the very same physical
 * size. The display revision is left open — it counts the display's own publications.
 */
async function displayCell(page) {
  const metrics = await page.evaluate(() => ({
    width: window.innerWidth,
    height: window.innerHeight,
    dpr: window.devicePixelRatio,
    colorDepth: window.screen.colorDepth,
  }));
  const width = Math.round(metrics.width * metrics.dpr);
  const height = Math.round(metrics.height * metrics.dpr);
  const pattern = new RegExp(
    '^type=HostViewport:connection=connected:name=none' +
      `:bounds=0,0,${width},${height}:workArea=0,0,${width},${height}` +
      `:scale=${metrics.dpr}:modes=1:current=0` +
      `:mode=${width}x${height}:none:${metrics.colorDepth}:rev=\\d+$`,
  );
  return { metrics, pattern };
}

test('web-display-initial-hostviewport', async ({ page }) => {
  await loadScenario(page, 'display');
  const host = page.locator('[data-kadre-host="display"]');

  // The gate's observable, for a plain attached session: nobody asked the manager for anything and
  // no browser event has fired — the install's own publication is already the exact fallback
  // shape: enumerated, one display, the primary among them, enumeration declared. Never
  // Unavailable, never empty, never a second display.
  await expect(host).toHaveAttribute('data-kadre-display-manager', managerCell(1));

  // The primary display is the browsing context's own viewport, measured now.
  const cell = await displayCell(page);
  await expect.poll(async () => host.getAttribute('data-kadre-display-display')).toMatch(cell.pattern);

  // The initial publication is a state fact, not a late event: the page's own journal is empty.
  await expect(host).toHaveAttribute('data-kadre-display-events', '');

  // The public admission round-trips and is idempotent: the identical snapshot is answered at the
  // revision already published, and nothing is republished.
  await command(page, 'kadre-display-request');
  await expect(host).toHaveAttribute('data-kadre-display-request', 'success@1');
  await expect(host).toHaveAttribute('data-kadre-display-manager', managerCell(1));
  await expect(host).toHaveAttribute('data-kadre-display-events', '');
});

test('web-display-resize-propagation', async ({ page }) => {
  await loadScenario(page, 'display');
  const host = page.locator('[data-kadre-host="display"]');
  await expect(host).toHaveAttribute('data-kadre-display-manager', managerCell(1));
  await expect(host).toHaveAttribute('data-kadre-display-events', '');
  await settleQuietly(page);
  const journal = await host.getAttribute('data-kadre-display-events');

  // A real browser-delivered resize: the manager republishes exactly once per resize — the
  // revision bumps by one and the journal grows by exactly one Changed — and the physical bounds
  // follow the new CSS size at the browsing context's own ratio. A second resize event that
  // measured the same viewport publishes nothing.
  await page.setViewportSize({ width: 800, height: 600 });
  const first = await displayCell(page);
  await expect.poll(async () => host.getAttribute('data-kadre-display-display')).toMatch(first.pattern);
  await expect(host).toHaveAttribute('data-kadre-display-manager', managerCell(2));
  await expect(host).toHaveAttribute('data-kadre-display-events', journalGrown(journal, 'changed@2'));

  await page.setViewportSize({ width: 1024, height: 768 });
  const second = await displayCell(page);
  await expect.poll(async () => host.getAttribute('data-kadre-display-display')).toMatch(second.pattern);
  await expect(host).toHaveAttribute('data-kadre-display-manager', managerCell(3));
  await expect(host).toHaveAttribute(
    'data-kadre-display-events',
    journalGrown(journalGrown(journal, 'changed@2'), 'changed@3'),
  );
});

test('web-display-dpr-scale-factor', async ({ browser }) => {
  const context = await browser.newContext();
  try {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await loadScenario(page, 'display');
    const host = page.locator('[data-kadre-host="display"]');
    await expect(host).toHaveAttribute('data-kadre-display-manager', managerCell(1));
    await expect(host).toHaveAttribute('data-kadre-display-events', '');
    await settleQuietly(page);
    const journal = await host.getAttribute('data-kadre-display-events');

    // A real device pixel ratio change: the browsing context's ratio is replaced through the
    // emulation at the very same CSS size, so the only fact that moved is the ratio — and the
    // inventory still republishes, because the scale factor and the physical bounds are its own
    // facts. A ratio the browser never reported would leave the snapshot frozen.
    const viewport = page.viewportSize();
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: viewport.width,
      height: viewport.height,
      deviceScaleFactor: 2,
      mobile: false,
    });
    const doubled = await displayCell(page);
    expect(doubled.metrics.width).toBe(viewport.width);
    expect(doubled.metrics.height).toBe(viewport.height);
    expect(doubled.metrics.dpr).toBe(2);
    await expect.poll(async () => host.getAttribute('data-kadre-display-display')).toMatch(doubled.pattern);
    await expect(host).toHaveAttribute('data-kadre-display-manager', managerCell(2));
    await expect(host).toHaveAttribute('data-kadre-display-events', journalGrown(journal, 'changed@2'));

    // And a second ratio change republishes again: the snapshot follows the browser's own fact,
    // not a one-shot observation.
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: viewport.width,
      height: viewport.height,
      deviceScaleFactor: 3,
      mobile: false,
    });
    const tripled = await displayCell(page);
    expect(tripled.metrics.dpr).toBe(3);
    await expect.poll(async () => host.getAttribute('data-kadre-display-display')).toMatch(tripled.pattern);
    await expect(host).toHaveAttribute('data-kadre-display-manager', managerCell(3));
    await expect(host).toHaveAttribute(
      'data-kadre-display-events',
      journalGrown(journalGrown(journal, 'changed@2'), 'changed@3'),
    );
  } finally {
    await context.close();
  }
});

test('web-display-teardown-quiet', async ({ browser }) => {
  const context = await browser.newContext();
  try {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await loadScenario(page, 'display');
    const host = page.locator('[data-kadre-host="display"]');
    const body = page.locator('body');
    await expect(host).toHaveAttribute('data-kadre-display-manager', managerCell(1));
    await expect(host).toHaveAttribute('data-kadre-display-events', '');

    // A real close of the session: the manager dies with it, and the terminal state is the
    // barrier every later read is measured from.
    await command(page, 'kadre-stop-display');
    await expect(body).toHaveAttribute('data-kadre-display-session', 'terminated');
    await nextFrame(page);

    // The quiet window: the journal, the snapshot, the page's animation-frame counter and its DOM
    // node count are read before the pokes, so whatever the pokes move is named by the tail.
    const journal = await host.getAttribute('data-kadre-display-events');
    const manager = await host.getAttribute('data-kadre-display-manager');
    const frames = await host.getAttribute('data-kadre-display-raf');
    const nodes = await domCount(page, host);

    await page.setViewportSize({ width: 800, height: 600 });
    await page.setViewportSize({ width: 1024, height: 768 });
    const viewport = page.viewportSize();
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: viewport.width,
      height: viewport.height,
      deviceScaleFactor: 2,
      mobile: false,
    });
    await settleQuietly(page);

    // The session is gone: no further display event, no republished snapshot, no node created for
    // the pokes, and not one animation frame registered — the inventory is event-driven, and the
    // events it was driven by died with the session that owned them.
    await expect(host).toHaveAttribute('data-kadre-display-events', journal);
    await expect(host).toHaveAttribute('data-kadre-display-manager', manager);
    await expect(host).toHaveAttribute('data-kadre-display-raf', frames);
    await expect(host).toHaveAttribute('data-kadre-display-dom-count', nodes);
  } finally {
    await context.close();
  }
});
