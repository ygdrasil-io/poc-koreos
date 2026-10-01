import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

/**
 * The window-provider scenarios of `BCK-001`, each titled with the evidence id it carries.
 *
 * The fixture is the host: before any Kadre call it opens a same-origin `about:blank` popup, puts a
 * focusable host element inside it, and exposes the elements the failing provider modes answer with.
 * No Kadre code creates a browsing context, an element or a popup; this suite reaches the popup
 * through `page.on("popup")` and asserts on what the child session wrote onto the element the host
 * prepared.
 *
 * Every session the fixture's application factory creates — the requester and each child — records
 * its own identity on the element it is attached to: the hash of its `SessionId` (identities never
 * print; they do compare) and its launch reason. A child session in the popup is therefore proven by
 * its element carrying the very hash the `WindowRequestOutcome` named, on the element the host
 * prepared, with the launch reason the child path owes.
 */

/** Loads the provider scenario and returns the requester's host and the popup it will open. */
async function loadProviderScenario(page) {
  const popupPromise = page.waitForEvent('popup');
  await page.goto(`${fixtureUrl}?scenario=window-provider`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
  const popup = await popupPromise;
  const host = page.locator('[data-kadre-host="window-provider"]');
  await expect(host).toHaveAttribute('data-kadre-attach', 'success');
  await expect(host).toHaveAttribute('data-kadre-session-reason', 'initialHostAttachment');
  return { popup, host };
}

/** Selects the provider behaviour, asks for one window and reads back the encoded outcome. */
async function requestWindow(page, host, command) {
  if (command !== undefined) {
    await page.evaluate((name) => document.dispatchEvent(new Event(name)), command);
    await expect(host).toHaveAttribute(
      'data-kadre-provider-mode',
      command.replace('kadre-provider-', ''),
      { timeout: 2_000 },
    );
  }
  await page.evaluate(() => document.dispatchEvent(new Event('kadre-request-window')));
  await expect(host).toHaveAttribute('data-kadre-window-outcome', /.+/, { timeout: 5_000 });
  return {
    caps: await host.getAttribute('data-kadre-window-caps'),
    outcome: await host.getAttribute('data-kadre-window-outcome'),
  };
}

test('web-window-provider-new-session', async ({ page }) => {
  const { popup, host } = await loadProviderScenario(page);
  const requesterHash = await host.getAttribute('data-kadre-session-hash');

  const { caps, outcome } = await requestWindow(page, host);

  // The manager's capability names exactly what a provider arms, and the request opened a session
  // that is not the requester's:
  expect(caps).toBe('supported[OpenedInNewSession]');
  const childHash = /^opened-in-new-session:(\d+)$/.exec(outcome)?.[1];
  expect(childHash, `outcome was ${outcome}`).toBeTruthy();
  expect(childHash).not.toBe(requesterHash);

  // The child session really runs in the popup, on the element the host prepared, launched by the
  // request that caused it:
  const childElement = popup.locator('[data-kadre-provider-host]');
  await expect(childElement).toHaveAttribute('data-kadre-session-hash', childHash, { timeout: 5_000 });
  await expect(childElement).toHaveAttribute('data-kadre-session-reason', 'additionalHostRequested');
  await expect(childElement).toHaveAttribute('data-kadre-session-lifecycle', /^attached-/);
});

test('web-window-provider-same-context', async ({ page }) => {
  const { host } = await loadProviderScenario(page);

  // The provider answers with an element of the requester's own browsing context: the ladder refuses
  // it before any child session exists.
  const { outcome } = await requestWindow(page, host, 'kadre-provider-same-document');
  expect(outcome).toBe('rejected:invalidRequest:element.ownerDocument');
  await expect(page.locator('[data-kadre-provider-same-document]')).not.toHaveAttribute('data-kadre-session-hash', /.+/);
});

test('web-window-provider-no-context', async ({ page }) => {
  const { host } = await loadProviderScenario(page);

  // The provider answers with an element of an inert document: connected to its own node tree, with
  // no browsing context at all — the folded half of the same rung.
  const { outcome } = await requestWindow(page, host, 'kadre-provider-no-context');
  expect(outcome).toBe('rejected:invalidRequest:element.ownerDocument');
});

test('web-window-provider-invalid-element', async ({ page }) => {
  const { host } = await loadProviderScenario(page);

  // The provider answers with an element it never inserted into the popup: under StopWhenDetached
  // the first rung of the ladder refuses it.
  const { outcome } = await requestWindow(page, host, 'kadre-provider-disconnected');
  expect(outcome).toBe('rejected:invalidRequest:element');
});

test('web-window-provider-invalid-scope', async ({ page }) => {
  const { host } = await loadProviderScenario(page);

  // The provider answers with a valid element and a scope that carries no Job: the scope rung.
  const { outcome } = await requestWindow(page, host, 'kadre-provider-invalid-scope');
  expect(outcome).toBe('rejected:invalidRequest:parentScope');
});

test('web-window-provider-owned-element', async ({ page }) => {
  const { host } = await loadProviderScenario(page);

  // The first request opens the child session on the prepared element and takes its ownership; the
  // second request, answered with the same element, must not create a second owner for it.
  const first = await requestWindow(page, host);
  expect(first.outcome).toMatch(/^opened-in-new-session:/);
  const second = await requestWindow(page, host);
  expect(second.outcome).toBe('rejected:alreadyInUse:host');
});

test('web-window-provider-callback-failure', async ({ page }) => {
  const { host } = await loadProviderScenario(page);

  // The provider throws inside its own callback: the manager captures it and answers the closed
  // failure of the provider's domain, and nothing escapes to the caller.
  const { outcome } = await requestWindow(page, host, 'kadre-provider-throwing');
  expect(outcome).toBe('rejected:platformFailure:web:WebWindowProvider:callback-exception');
});
