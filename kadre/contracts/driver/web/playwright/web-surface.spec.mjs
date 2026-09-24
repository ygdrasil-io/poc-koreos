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

test('web-surface-metrics-resize', async ({ page }) => {
  await loadScenario(page, 'surface-metrics');
  const host = page.locator('[data-kadre-host="surface-metrics"]');
  await expect(host).toHaveAttribute('data-kadre-surface-metrics', '320x180@1#0');

  await command(page, 'kadre-resize-surface');
  await nextFrame(page);

  // The browser delivered the new layout box, the surface published it once, and no further revision
  // was added: the revision in the readback is the whole "exactly one metrics change" claim.
  await expect(host).toHaveAttribute('data-kadre-surface-metrics', '640x180@1#1');
  await expect(host).toHaveAttribute('data-kadre-surface-physical', '640x180');
});

test('web-surface-metrics-scale', async ({ page }) => {
  await loadScenario(page, 'surface-metrics');
  const host = page.locator('[data-kadre-host="surface-metrics"]');
  await expect(host).toHaveAttribute('data-kadre-surface-metrics', '320x180@1#0');

  // A real device pixel ratio change cannot be produced from inside a page (it comes from zoom or a
  // monitor change), so the browsing context's ratio is replaced here and the surface then reads it
  // back on the next browser-delivered observation.
  await page.evaluate(() => {
    Object.defineProperty(window, 'devicePixelRatio', { configurable: true, value: 2 });
  });
  await command(page, 'kadre-resize-surface');
  await nextFrame(page);

  await expect(host).toHaveAttribute('data-kadre-surface-metrics', '640x180@2#1');
  await expect(host).toHaveAttribute('data-kadre-surface-physical', '1280x360');
});

test('web-surface-redraw-coalesced', async ({ page }) => {
  await loadScenario(page, 'surface-redraw');
  const host = page.locator('[data-kadre-host="surface-redraw"]');

  // Three requests issued inside one task are one pending request: the frame after them admits
  // exactly one event, and a second frame admits nothing more.
  await command(page, 'kadre-request-redraw');
  await expect(host).toHaveAttribute('data-kadre-redraw-admission', 'success,success,success');
  await expect(host).toHaveAttribute('data-kadre-redraw-count', '1');
  await nextFrame(page);
  await expect(host).toHaveAttribute('data-kadre-redraw-count', '1');

  // Each later request is separated by a real frame, so each one is admitted in its own frame.
  for (const count of ['2', '3']) {
    await command(page, 'kadre-request-redraw');
    await nextFrame(page);
    await expect(host).toHaveAttribute('data-kadre-redraw-count', count);
  }
});

test('web-surface-redraw-detached-rejected', async ({ page }) => {
  await loadScenario(page, 'surface-redraw');
  const host = page.locator('[data-kadre-host="surface-redraw"]');

  const body = page.locator('body');
  await command(page, 'kadre-remove-host');
  // The browser delivered the detach and the session terminated before the request is issued; the
  // element itself is gone, so the readback lands on the body.
  await expect(body).toHaveAttribute('data-kadre-redraw-session', 'terminated');

  await command(page, 'kadre-request-redraw-detached');
  await expect(body).toHaveAttribute('data-kadre-surface-attachment', 'detached');
  await expect(body).toHaveAttribute('data-kadre-redraw-detached', 'closed:surface');
});

test('web-element-lease', async ({ page }) => {
  await loadScenario(page, 'element-lease');
  const host = page.locator('[data-kadre-host="element-lease"]');

  await command(page, 'kadre-lease');

  // The callback ran with the attached element: the attribute it wrote is on that element.
  await expect(host).toHaveAttribute('data-kadre-lease-result', 'granted');
  await expect(host).toHaveAttribute('data-kadre-lease', 'seen');
});

test('web-element-lease-concurrent-close', async ({ page }) => {
  await loadScenario(page, 'element-lease-close');
  const host = page.locator('[data-kadre-host="element-lease-close"]');

  await command(page, 'kadre-lease-concurrent-close');

  await expect(host).toHaveAttribute('data-kadre-lease-result', 'granted');
  await expect(host).toHaveAttribute('data-kadre-lease', 'seen');
  // A lease started while one is in flight is refused as retryable, and once the redraw overflow
  // closed the surface every later lease reports the closed surface instead.
  await expect(host).toHaveAttribute('data-kadre-lease-concurrent', 'temporarilyUnavailable:true');
  await expect(host).toHaveAttribute('data-kadre-lease-closed', 'closed:surface');
  await expect(host).toHaveAttribute('data-kadre-surface-attachment', 'detached');
  // The overflow named the surface as its owner, so the session is still running.
  await expect(page.locator('body')).toHaveAttribute('data-kadre-lease-close-session', 'running');
});

test('web-surface-no-renderer', async ({ page }) => {
  await loadScenario(page, 'surface-no-renderer');
  const host = page.locator('[data-kadre-host="surface-no-renderer"]');
  const body = page.locator('body');
  const baseline = await body.getAttribute('data-kadre-dom-baseline');
  expect(baseline).not.toBeNull();

  await command(page, 'kadre-surface-activity');

  // The metrics change really was observed and the redraw really was admitted...
  await expect(host).toHaveAttribute('data-kadre-observed-revision', '1');
  await expect(host).toHaveAttribute('data-kadre-observed-redraw', 'true');
  // ...and neither created a node: the count read after them is the pre-attach baseline, and no
  // primary window was exposed.
  await expect(body).toHaveAttribute('data-kadre-dom-count', baseline);
  await expect(body).toHaveAttribute('data-kadre-window-primary', 'null');
});
