import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

/**
 * The runtime scenarios of the published `@kadre/host` facade (`INT-003`), each titled with the
 * evidence id it carries.
 *
 * The fixture publishes the factory keys of its own Kotlin module instance — the instance that owns
 * the sessions, exactly as `kadre/INTEROP-EXPORTS.md` section 6 describes — and, for the provider
 * scenario, prepares the second browsing context before any Kadre call. The specs drive the shim
 * that the published package actually ships (`/host/index.mjs`), never a copy: `KadreWeb.attach`
 * resolves the eight bindings from the shared registry at call time.
 *
 * Everything a test asserts is a value the consumer sees: the refused attachments' closed failures,
 * the subscription order, the reported observer exception, the terminal outcome and the session the
 * provider's window request opened.
 */

async function loadScenario(page, scenario) {
  await page.goto(`${fixtureUrl}?scenario=${scenario}`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
}

test('web-host-attach-failure', async ({ page }) => {
  await loadScenario(page, 'host-facade');
  const refusals = await page.evaluate(async () => {
    const { KadreWeb, KadreHostError } = await import('/host/index.mjs');
    const attempt = (attach) => {
      try {
        attach();
        return null;
      } catch (error) {
        return error;
      }
    };
    const element = document.querySelector('[data-kadre-host="host-facade"]');
    const factory = globalThis.kadreApplicationFactory;
    const failures = [];
    failures.push(attempt(() => KadreWeb.attach(element, 'not-a-factory')));
    failures.push(attempt(() => KadreWeb.attach(element, factory, { policy: 'turbo' })));
    failures.push(attempt(() => KadreWeb.attach(element, factory, { attachmentPolicy: 'sometimes' })));
    failures.push(attempt(() => KadreWeb.attach(element, factory, { windowProvider: { notOpen: true } })));
    failures.push(attempt(() => KadreWeb.attach(document.createElement('div'), factory)));
    // Every refusal above is a KadreHostError carrying the closed failure, and none of them left the
    // host element unusable: the page's own attach still works afterwards.
    const handle = KadreWeb.attach(element, factory);
    return {
      reported: failures.map((error) => ({
        isHostError: error instanceof KadreHostError,
        field: error?.failure?.field ?? null,
      })),
      attached: handle.state.kind,
    };
  });
  expect(refusals.reported).toEqual([
    { isHostError: true, field: 'factoryKey' },
    { isHostError: true, field: 'options.policy' },
    { isHostError: true, field: 'options.attachmentPolicy' },
    { isHostError: true, field: 'options.windowProvider' },
    { isHostError: true, field: 'element' },
  ]);
  expect(['starting', 'running']).toContain(refusals.attached);
});

test('web-host-state-subscription', async ({ page }) => {
  await loadScenario(page, 'host-facade');
  const result = await page.evaluate(async () => {
    const { KadreWeb } = await import('/host/index.mjs');
    const element = document.querySelector('[data-kadre-host="host-facade"]');
    const handle = KadreWeb.attach(element, globalThis.kadreApplicationFactory);
    const kept = [];
    const cancelled = [];
    const unsubscribeKept = handle.subscribeState((state) => kept.push(state.kind));
    // The first delivery is synchronous: by the time subscribeState returns, the observer has heard
    // the current snapshot.
    const synchronous = kept.length === 1 ? kept[0] : null;
    const unsubscribeCancelled = handle.subscribeState((state) => cancelled.push(state.kind));
    unsubscribeCancelled();
    handle.requestStop();
    const outcome = await handle.awaitTermination();
    return { synchronous, kept, cancelled, outcome };
  });
  // The current snapshot arrived synchronously, the terminal snapshot was delivered in order to the
  // observer that stayed subscribed, and the cancelled subscription heard nothing more.
  expect(result.synchronous).not.toBeNull();
  expect(result.kept[0]).toBe(result.synchronous);
  expect(result.kept.at(-1)).toBe('terminated');
  expect(result.cancelled).toEqual([result.synchronous]);
  expect(result.outcome).toEqual({ kind: 'stopped', reason: 'hostRequested' });
});

test('web-host-observer-exception', async ({ page }) => {
  // The report is out of band by contract: an uncaught exception the page observes — a page error or
  // a console error — that never reached the session. Which channel the coroutine runtime of the
  // target uses is its own choice; the assertion accepts the closed set of the two.
  const reports = [];
  page.on('pageerror', (error) => reports.push(String(error?.message || error)));
  page.on('console', (message) => {
    if (message.type() === 'error') reports.push(message.text());
  });
  await loadScenario(page, 'host-facade');
  const result = await page.evaluate(async () => {
    const { KadreWeb } = await import('/host/index.mjs');
    const element = document.querySelector('[data-kadre-host="host-facade"]');
    const handle = KadreWeb.attach(element, globalThis.kadreApplicationFactory);
    handle.subscribeState(() => {
      throw new Error('the observer exploded');
    });
    const healthy = [];
    handle.subscribeState((state) => healthy.push(state.kind));
    // The exception is reported out of band, never into the session: the healthy observer keeps
    // receiving, and the session still reaches its terminal outcome.
    await new Promise((resolve) => setTimeout(resolve, 50));
    handle.requestStop();
    const outcome = await handle.awaitTermination();
    return { healthy, outcome };
  });
  expect(result.healthy.at(-1)).toBe('terminated');
  expect(result.outcome).toEqual({ kind: 'stopped', reason: 'hostRequested' });
  await expect
    .poll(() => reports.join(' '), { timeout: 5_000 })
    .toContain('the observer exploded');
});

test('web-host-stop-close-outcome', async ({ page }) => {
  await loadScenario(page, 'host-facade');
  const result = await page.evaluate(async () => {
    const { KadreWeb } = await import('/host/index.mjs');
    const element = document.querySelector('[data-kadre-host="host-facade"]');
    const handle = KadreWeb.attach(element, globalThis.kadreApplicationFactory);
    handle.requestStop();
    const outcome = await handle.awaitTermination();
    const terminalState = handle.state;
    // A close after the release is a no-op that must not throw:
    handle.close();
    // A subscription that arrives after the termination still hears the terminal snapshot, and a
    // late awaiter is answered from the outcome the handle kept.
    const late = [];
    handle.subscribeState((state) => late.push(state.kind));
    const lateOutcome = await handle.awaitTermination();
    return { outcome, terminalState: terminalState.kind, late, lateOutcome };
  });
  expect(result.outcome).toEqual({ kind: 'stopped', reason: 'hostRequested' });
  expect(result.terminalState).toBe('terminated');
  expect(result.late).toEqual(['terminated']);
  expect(result.lateOutcome).toEqual({ kind: 'stopped', reason: 'hostRequested' });
});

test('web-host-provider', async ({ page }) => {
  const popupPromise = page.waitForEvent('popup');
  await loadScenario(page, 'host-provider');
  const popup = await popupPromise;

  const result = await page.evaluate(async () => {
    const { KadreWeb } = await import('/host/index.mjs');
    const element = document.querySelector('[data-kadre-host="host-provider"]');
    // The host prepared the second browsing context before any Kadre call; the provider only hands
    // over the element it already owns.
    const providerHost = globalThis.kadreProviderHost;
    if (!providerHost) throw new Error('the host did not publish the prepared provider element');
    const handle = KadreWeb.attach(element, globalThis.kadreWindowRequestFactory, {
      windowProvider: {
        open: (requestId, spec) => ({
          kind: 'opened',
          host: { element: providerHost, attachmentPolicy: 'stopWhenDetached' },
        }),
      },
    });
    const running = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('the facade session never ran')), 5_000);
      const unsubscribe = handle.subscribeState((state) => {
        if (state.kind === 'running') {
          clearTimeout(timer);
          unsubscribe();
          resolve(true);
        }
      });
    });
    // The application behind the facade asks its own window manager, which consults the provider
    // the option installed:
    document.dispatchEvent(new Event('kadre-facade-request-window'));
    let outcome = null;
    for (let i = 0; i < 100 && outcome === null; i += 1) {
      outcome = document.body.getAttribute('data-kadre-facade-window');
      if (outcome === null) await new Promise((resolve) => setTimeout(resolve, 50));
    }
    if (outcome === null) throw new Error('no facade window outcome was published');
    return { running, outcome };
  });

  expect(result.running).toBe(true);
  const childHash = /^opened-in-new-session:(\d+)$/.exec(result.outcome)?.[1];
  expect(childHash, `outcome was ${result.outcome}`).toBeTruthy();
  // The child session the provider's offer became really runs in the popup, on the element the host
  // prepared, launched by the request that caused it:
  const childElement = popup.locator('[data-kadre-provider-host]');
  await expect(childElement).toHaveAttribute('data-kadre-session-hash', childHash, { timeout: 5_000 });
  await expect(childElement).toHaveAttribute('data-kadre-session-reason', 'additionalHostRequested');
  await expect(childElement).toHaveAttribute('data-kadre-session-lifecycle', /^attached-/);
});
