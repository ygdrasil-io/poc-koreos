import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

async function loadScenario(page, scenario) {
  await page.goto(`${fixtureUrl}?scenario=${scenario}`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
}

test('StopWhenDetached rejects an initially disconnected host', async ({ page }) => {
  await loadScenario(page, 'initial-disconnected');

  await expect(page.locator('body')).toHaveAttribute('data-kadre-attach', 'invalid-element');
});

test('a durable detach terminates the session', async ({ page }) => {
  await loadScenario(page, 'durable-detach');

  await page.locator('[data-kadre-host="durable"]').evaluate((host) => host.remove());

  await expect(page.locator('body')).toHaveAttribute('data-kadre-durable-session', 'terminated');
  await expect(page.locator('body')).toHaveAttribute('data-kadre-durable-outcome', 'host-detached');
});

test('a same-document detach and reinsert before observer delivery keeps the session', async ({ page }) => {
  await loadScenario(page, 'detach-reinsert');

  await page.locator('[data-kadre-host="reinsert"]').evaluate((host) => {
    const parent = host.parentNode;
    host.remove();
    parent.appendChild(host);
  });
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => resolve())));

  await expect(page.locator('body')).toHaveAttribute('data-kadre-reinsert-session', 'running');
});

test('moving into a same-document ShadowRoot reinstalls observation on that root', async ({ page }) => {
  await loadScenario(page, 'shadow-root');

  await page.locator('[data-kadre-host="shadow"]').evaluate((host) => {
    const shadowHost = document.createElement('section');
    document.body.appendChild(shadowHost);
    shadowHost.attachShadow({ mode: 'open' }).appendChild(host);
  });
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => resolve())));
  await page.locator('[data-kadre-host="shadow"]').evaluate((host) => host.remove());

  await expect(page.locator('body')).toHaveAttribute('data-kadre-shadow-session', 'terminated');
});

test('an inter-document transfer terminates the session', async ({ page }) => {
  await loadScenario(page, 'inter-document');

  await page.locator('[data-kadre-host="transfer"]').evaluate((host) => {
    const frame = document.createElement('iframe');
    document.body.appendChild(frame);
    const foreignDocument = frame.contentDocument;
    foreignDocument.body.appendChild(foreignDocument.adoptNode(host));
  });

  await expect(page.locator('body')).toHaveAttribute('data-kadre-transfer-session', 'terminated');
  await expect(page.locator('body')).toHaveAttribute('data-kadre-transfer-outcome', 'host-detached');
});

test('Manual accepts an initial detach and reconnects through the origin context', async ({ page }) => {
  await loadScenario(page, 'manual-reconnect');

  await expect(page.locator('body')).toHaveAttribute(
    'data-kadre-manual-lifecycle',
    'attached-background-inactive',
  );
  await page.evaluate(() => document.dispatchEvent(new Event('kadre-connect-manual')));
  const host = page.locator('[data-kadre-host="manual"]');
  await expect(host).toHaveCount(1);
  await expect(page.locator('body')).toHaveAttribute(
    'data-kadre-manual-lifecycle',
    'attached-foreground-inactive',
  );

  await host.focus();
  await expect(page.locator('body')).toHaveAttribute(
    'data-kadre-manual-lifecycle',
    'attached-foreground-active',
  );
});

test('Manual stays attached through a delivered detach and reconnects later', async ({ page }) => {
  await loadScenario(page, 'manual-detach-reconnect');

  await page.locator('[data-kadre-host="manual-detach"]').evaluate((host) => host.remove());
  const body = page.locator('body');
  await expect(body).toHaveAttribute('data-kadre-manual-detach-session', 'running');
  await expect(body).toHaveAttribute(
    'data-kadre-manual-detach-lifecycle',
    'attached-background-inactive',
  );

  await page.evaluate(() => document.dispatchEvent(new Event('kadre-reconnect-manual')));
  await expect(page.locator('[data-kadre-host="manual-detach"]')).toHaveAttribute(
    'data-kadre-lifecycle',
    'attached-foreground-inactive',
  );
});

test('independent hosts terminate independently', async ({ page }) => {
  await loadScenario(page, 'independent');

  await page.locator('[data-kadre-host="independent-a"]').evaluate((host) => host.remove());

  await expect(page.locator('body')).toHaveAttribute('data-kadre-independent-a-session', 'terminated');
  await expect(page.locator('body')).toHaveAttribute('data-kadre-independent-b-session', 'running');
});

test('a duplicate attach is rejected without another DOM observer or listener', async ({ page }) => {
  await page.addInitScript(() => {
    const NativeMutationObserver = window.MutationObserver;
    window.__kadreMutationObserverCount = 0;
    window.MutationObserver = class extends NativeMutationObserver {
      constructor(callback) {
        super(callback);
        window.__kadreMutationObserverCount += 1;
      }
    };
    const nativeAddEventListener = EventTarget.prototype.addEventListener;
    window.__kadreFocusInTargets = [];
    EventTarget.prototype.addEventListener = function (type, callback, options) {
      if (type === 'focusin') window.__kadreFocusInTargets.push(this);
      return nativeAddEventListener.call(this, type, callback, options);
    };
  });
  await page.goto(`${fixtureUrl}?scenario=duplicate`);
  const installationCounts = await page.evaluate(() => {
    const host = document.querySelector('[data-kadre-host="duplicate"]');
    return {
      mutationObservers: window.__kadreMutationObserverCount,
      hostFocusInListeners: window.__kadreFocusInTargets.filter((target) => target === host).length,
    };
  });
  expect(installationCounts).toEqual({ mutationObservers: 1, hostFocusInListeners: 1 });
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true');
  await expect(page.locator('body')).toHaveAttribute('data-kadre-duplicate-attach', 'already-in-use-host');
});

test('visibility and focus keep only a foreground focused host active', async ({ page }) => {
  await loadScenario(page, 'focus');
  const first = page.locator('[data-kadre-host="focus-a"]');
  const second = page.locator('[data-kadre-host="focus-b"]');

  await first.focus();
  await expect(first).toHaveAttribute('data-kadre-lifecycle', 'attached-foreground-active');
  await expect(second).toHaveAttribute('data-kadre-lifecycle', 'attached-foreground-inactive');

  await second.focus();
  await expect(first).toHaveAttribute('data-kadre-lifecycle', 'attached-foreground-inactive');
  await expect(second).toHaveAttribute('data-kadre-lifecycle', 'attached-foreground-active');

  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await expect(first).toHaveAttribute('data-kadre-lifecycle', 'attached-background-inactive');
  await expect(second).toHaveAttribute('data-kadre-lifecycle', 'attached-background-inactive');
});

test('persisted pagehide terminates immediately and pageshow cannot resurrect', async ({ page }) => {
  await loadScenario(page, 'pagehide');

  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true })));
  await expect(page.locator('body')).toHaveAttribute('data-kadre-pagehide-session', 'terminated');
  await expect(page.locator('body')).toHaveAttribute('data-kadre-pagehide-outcome', 'host-detached');

  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true })));
  await expect(page.locator('body')).toHaveAttribute('data-kadre-pagehide-session', 'terminated');
});

test('attach creates no DOM and exposes no primary Window', async ({ page }) => {
  await loadScenario(page, 'host-owned');
  const body = page.locator('body');

  await expect(body).toHaveAttribute('data-kadre-dom-after', await body.getAttribute('data-kadre-dom-before'));
  await expect(body).toHaveAttribute('data-kadre-window-primary', 'null');
  await expect(body).toHaveAttribute('data-kadre-window-count', '0');
  await expect(body).toHaveAttribute('data-kadre-request-window', 'unsupported');
});
