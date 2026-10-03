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
 * The capability snapshot every text test opens with, kept strictly apart from the flow assertions
 * that follow it: the structural claim of this phase is that a text session can be opened — the
 * editability of the element stays the host's own boundary (D-X2) — and a test that fails below
 * must never be readable as "the capability was missing". The whole public cell is asserted, so a
 * regression in any member of it is named by this one line.
 */
async function awaitTextCapability(host) {
  await expect(host).toHaveAttribute(
    'data-kadre-input-caps',
    'keyboard=available pointer=available touch=available gestures=unsupported:gestureinput '
      + 'dragAndDrop=available textInput=supported rawInput=unsupported:rawinputaccess',
  );
}

/** Opens the fixture's session and waits for the answer the surface gave. */
async function openSession(page, host) {
  await command(page, 'kadre-text-open');
  await expect(host).toHaveAttribute('data-kadre-text-open', 'success', { timeout: 5_000 });
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=none', { timeout: 5_000 });
}

/** The element's own document facts, read the way a consumer of the element reads them. */
async function elementDocument(page, selector) {
  return page.evaluate((query) => {
    const element = document.querySelector(query);
    return {
      value: element.value,
      selectionStart: element.selectionStart,
      selectionEnd: element.selectionEnd,
    };
  }, selector);
}

/**
 * One real composition step of the browser's own IME pipeline: CDP `Input.imeSetComposition` drives
 * the composition Chromium itself owns — the events the element receives are the real ones (D-X4:
 * the CDP proof is the real-Chromium composition; the OS keyboard's own IME is the manual charter).
 */
function setComposition(cdp, text, caret) {
  return cdp.send('Input.imeSetComposition', { text, selectionStart: caret, selectionEnd: caret });
}

test('web-text-open-single-session', async ({ page }) => {
  await loadScenario(page, 'text-input');
  const host = page.locator('[data-kadre-host="text-input"]');
  await awaitTextCapability(host);
  await openSession(page, host);

  // One surface, one session: the second open while the first lives is the closed failure the
  // runtime promised, and the refused open left the first session exactly as it was.
  await command(page, 'kadre-text-open-second');
  await expect(host).toHaveAttribute('data-kadre-text-open-second', 'failure:alreadyInUse:textinputsession');
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=none');

  await host.focus();
  await page.keyboard.insertText('X');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'replace:(0,0)="X":rev=0');

  // The close frees the one slot: the reopen is admitted, and the new session observes nothing yet.
  await command(page, 'kadre-text-close');
  await expect(host).toHaveAttribute('data-kadre-text-state', 'closed');
  await command(page, 'kadre-text-open');
  await expect(host).toHaveAttribute('data-kadre-text-open', 'success', { timeout: 5_000 });
  await expect(host).toHaveAttribute('data-kadre-text-events', '');
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=none');
});

test('web-text-replace-event', async ({ page }) => {
  await loadScenario(page, 'text-input');
  const host = page.locator('[data-kadre-host="text-input"]');
  await awaitTextCapability(host);
  await openSession(page, host);
  await host.focus();

  // One real text arrival without a composition: the browser inserts the text at the caret, the
  // `beforeinput` it fires is the edit's own fact, and the port publishes it as the Replace the
  // model carries — the range the shadow's selection held, stamped at the accepted revision.
  await page.keyboard.insertText('X');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'replace:(0,0)="X":rev=0');
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=none');

  // The element and the model stayed the same document: the edit the observation describes is the
  // edit the element shows.
  expect(await elementDocument(page, '[data-kadre-host="text-input"]')).toEqual({
    value: 'Xkadre',
    selectionStart: 1,
    selectionEnd: 1,
  });
});

test('web-text-composition-lifecycle', async ({ page }) => {
  await loadScenario(page, 'text-input');
  const host = page.locator('[data-kadre-host="text-input"]');
  await awaitTextCapability(host);
  await openSession(page, host);
  await host.focus();
  const cdp = await page.context().newCDPSession(page);

  // Start: the composition opens at the selection the session started from, with no text of its own
  // yet, and the session's state carries the span the composition now owns.
  await setComposition(cdp, 'こん', 2);
  await expect(host).toHaveAttribute(
    'data-kadre-text-events',
    'composition:(0,0)="":sel=(0,0):rev=0;composition:(0,0)="こん":sel=(2,2):rev=0',
  );
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=(0,0)');

  // Update: the composed text replaced the composition's previous span — the observation names the
  // range the new text *replaces*, with the selection inside the new text — never a selection of
  // the document.
  await setComposition(cdp, 'こんん', 3);
  await expect(host).toHaveAttribute(
    'data-kadre-text-events',
    'composition:(0,0)="":sel=(0,0):rev=0;composition:(0,0)="こん":sel=(2,2):rev=0'
      + ';composition:(0,2)="こんん":sel=(3,3):rev=0',
  );
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=(0,2)');

  // Commit: the browser's own commit sequence — a final composition update carrying the string it
  // commits, then the end — is published as the final update of the span and the terminal
  // observation that closes it. The element shows the committed document.
  await cdp.send('Input.insertText', { text: 'こんん' });
  await expect(host).toHaveAttribute(
    'data-kadre-text-events',
    'composition:(0,0)="":sel=(0,0):rev=0;composition:(0,0)="こん":sel=(2,2):rev=0'
      + ';composition:(0,2)="こんん":sel=(3,3):rev=0;composition:(0,3)="こんん":sel=(3,3):rev=0'
      + ';composition:end:rev=0',
  );
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=none');
  expect(await elementDocument(page, '[data-kadre-host="text-input"]')).toEqual({
    value: 'こんんkadre',
    selectionStart: 3,
    selectionEnd: 3,
  });
});

test('web-text-composition-cancelled', async ({ page }) => {
  await loadScenario(page, 'text-input');
  const host = page.locator('[data-kadre-host="text-input"]');
  await awaitTextCapability(host);
  await openSession(page, host);
  await host.focus();

  // **Synthetic, and why**: the cancellation branches a scripted Chromium cannot be driven into are
  // dispatched by hand, the accepted precedent of the phase-3 synthetic WheelEvent. The payload is
  // the honest one a real cancellation carries — the empty final string, since a real
  // `CompositionEvent` cannot carry null data (Web IDL stringifies it).
  await page.evaluate(() => {
    const host = document.querySelector('[data-kadre-host="text-input"]');
    host.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }));
    host.dispatchEvent(new CompositionEvent('compositionupdate', { data: 'きょう', bubbles: true }));
    host.dispatchEvent(new CompositionEvent('compositionend', { data: '', bubbles: true }));
  });

  // The end without a commit is the removal the browser performed plus the terminal observation:
  // the composed span is withdrawn, no composition is left in the session state, and no session was
  // closed or left composing.
  await expect(host).toHaveAttribute(
    'data-kadre-text-events',
    'composition:(0,0)="":sel=(0,0):rev=0;composition:(0,0)="きょう":sel=(3,3):rev=0'
      + ';replace:(0,3)="":rev=0;composition:end:rev=0',
  );
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=none');
  expect(await elementDocument(page, '[data-kadre-host="text-input"]')).toEqual({
    value: 'kadre',
    selectionStart: 0,
    selectionEnd: 0,
  });
});

test('web-text-focus-suspends', async ({ page }) => {
  await loadScenario(page, 'text-input');
  const host = page.locator('[data-kadre-host="text-input"]');
  await awaitTextCapability(host);
  await openSession(page, host);
  await host.focus();

  // A session serving a real edit, then a real loss of activation: the page moves the focus out of
  // the element's subtree, and the lifecycle reduction — not a synthetic event — suspends the
  // session. The suspension itself published nothing: the observation journal is exactly the edit
  // the session served before it.
  await page.keyboard.insertText('X');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'replace:(0,0)="X":rev=0');
  await page.locator('[data-kadre-focus-outside]').focus();
  await expect(host).toHaveAttribute('data-kadre-text-state', 'suspended:rev=0:composing=none');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'replace:(0,0)="X":rev=0');

  // The focus the page regained resumes the session — Active at the same revision, with no
  // synthetic observation for the round trip.
  await host.focus();
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=none');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'replace:(0,0)="X":rev=0');

  // And the resumed session still serves: a real edit reaches it, stamped at the revision it holds.
  await page.keyboard.insertText('!');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'replace:(0,0)="X":rev=0;replace:(1,1)="!":rev=0');
});

test('web-text-stale-revision', async ({ page }) => {
  await loadScenario(page, 'text-input');
  const host = page.locator('[data-kadre-host="text-input"]');
  await awaitTextCapability(host);
  await openSession(page, host);

  // The write-back of the accepted snapshot, one revision ahead: applied.
  await command(page, 'kadre-text-writeback-current');
  await expect(host).toHaveAttribute('data-kadre-text-writeback-current', 'applied');

  // A snapshot behind the accepted revision is the stale failure the runtime names with the
  // revision it holds and the one it was handed — and the refused write touched nothing: the
  // element still shows the snapshot that was accepted, no observation was disturbed, and no
  // session was closed.
  await command(page, 'kadre-text-writeback-stale');
  await expect(host).toHaveAttribute('data-kadre-text-writeback-stale', 'staleRevision:1:0');
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=1:composing=none');
  await expect(host).toHaveAttribute('data-kadre-text-events', '');
  expect(await elementDocument(page, '[data-kadre-host="text-input"]')).toEqual({
    value: 'kadre-written',
    selectionStart: 13,
    selectionEnd: 13,
  });
});

test('web-text-writeback-sync', async ({ page }) => {
  await loadScenario(page, 'text-input');
  const host = page.locator('[data-kadre-host="text-input"]');
  await awaitTextCapability(host);
  await openSession(page, host);

  // The write-back is the contract's own write (D-X3): the accepted snapshot is applied to the
  // element — value and selection — at the revision the runtime accepted it with.
  await command(page, 'kadre-text-writeback-current');
  await expect(host).toHaveAttribute('data-kadre-text-writeback-current', 'applied');
  expect(await elementDocument(page, '[data-kadre-host="text-input"]')).toEqual({
    value: 'kadre-written',
    selectionStart: 13,
    selectionEnd: 13,
  });

  // And the agreement holds both ways: a real edit at the caret the write-back placed is the
  // observation the shadow computes, stamped at the revision the write-back accepted.
  await host.focus();
  await page.keyboard.insertText('!');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'replace:(13,13)="!":rev=1');
  expect(await elementDocument(page, '[data-kadre-host="text-input"]')).toEqual({
    value: 'kadre-written!',
    selectionStart: 14,
    selectionEnd: 14,
  });

  // The same contract on the other element kind the v1 scope addresses: a textarea takes the same
  // write-back, its document and selection written like the input's.
  await loadScenario(page, 'text-area');
  const area = page.locator('[data-kadre-host="text-area"]');
  await awaitTextCapability(area);
  await openSession(page, area);
  await command(page, 'kadre-text-writeback-current');
  await expect(area).toHaveAttribute('data-kadre-text-writeback-current', 'applied');
  expect(await elementDocument(page, '[data-kadre-host="text-area"]')).toEqual({
    value: 'kadre-written',
    selectionStart: 13,
    selectionEnd: 13,
  });
});

test('web-text-action-submit', async ({ page }) => {
  await loadScenario(page, 'text-input');
  const host = page.locator('[data-kadre-host="text-input"]');
  await awaitTextCapability(host);
  await openSession(page, host);
  await host.focus();

  // The submission key of the element is the one fact a keydown adds: the action of the config, and
  // nothing else — no text was synthesized from the keyboard (the input lane's own key events are
  // the reducer's business, never the session's text).
  await page.keyboard.press('Enter');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'action:send:rev=0');
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=none');
  expect(await elementDocument(page, '[data-kadre-host="text-input"]')).toEqual({
    value: 'kadre',
    selectionStart: 0,
    selectionEnd: 0,
  });
});

test('web-text-teardown-closes', async ({ page }) => {
  await loadScenario(page, 'text-input');
  const host = page.locator('[data-kadre-host="text-input"]');
  await awaitTextCapability(host);
  await openSession(page, host);
  await host.focus();

  // A session that observed a real edit, then the close that is the session's own: it leaves no
  // active session, and the state the runtime published is the Closed one.
  await page.keyboard.insertText('X');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'replace:(0,0)="X":rev=0');
  await command(page, 'kadre-text-close');
  await expect(host).toHaveAttribute('data-kadre-text-state', 'closed');
  const frozen = await host.getAttribute('data-kadre-text-events');

  // A late DOM observation after the close produces nothing: the listeners went with the session —
  // both the composition step a closing browser might still deliver and a hand-made edit event.
  await page.evaluate(() => {
    const host = document.querySelector('[data-kadre-host="text-input"]');
    host.dispatchEvent(new CompositionEvent('compositionupdate', { data: 'こん', bubbles: true }));
    host.dispatchEvent(new InputEvent('beforeinput', {
      inputType: 'insertText',
      data: 'Y',
      bubbles: true,
      cancelable: true,
    }));
  });
  await nextFrame(page);
  await expect(host).toHaveAttribute('data-kadre-text-events', frozen);

  // And the closed session does not haunt the surface: the reopen is admitted over the document the
  // element now shows, the new session's journal starts empty, and a real edit reaches it — at the
  // caret the document the element shows carries.
  await command(page, 'kadre-text-open');
  await expect(host).toHaveAttribute('data-kadre-text-open', 'success', { timeout: 5_000 });
  await expect(host).toHaveAttribute('data-kadre-text-events', '');
  await expect(host).toHaveAttribute('data-kadre-text-state', 'active:rev=0:composing=none');
  await page.keyboard.insertText('X');
  await expect(host).toHaveAttribute('data-kadre-text-events', 'replace:(1,1)="X":rev=0');
});
