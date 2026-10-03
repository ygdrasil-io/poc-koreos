import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

async function loadScenario(page, scenario) {
  await page.goto(`${fixtureUrl}?scenario=${scenario}`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
}

/** Waits for one real animation frame of the page. */
async function nextFrame(page) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => resolve())));
}

/**
 * The capability snapshot every touch test opens with, kept strictly apart from the flow assertions
 * that follow it: the structural claim of this phase is that touch observation exists — and that
 * gestures stay unsupported, since no recognizer exists anywhere (D-T2) — and a test that fails
 * below must never be readable as "the capability was missing". The whole public cell is asserted,
 * so a regression in any member of it is named by this one line.
 */
async function awaitTouchCapability(host) {
  await expect(host).toHaveAttribute(
    'data-kadre-input-caps',
    'keyboard=available pointer=available touch=available gestures=unsupported:gestureinput '
      + 'dragAndDrop=available textInput=supported rawInput=unsupported:rawinputaccess',
  );
}

/**
 * One real touch contact: a trusted event of the browser's own input pipeline, dispatched through
 * CDP in a browsing context that declares touch, so every DOM event the element receives is a real
 * one. CDP (rather than `page.touchscreen`) because a contact has to be held across assertions —
 * `touchscreen` offers only whole taps.
 */
function touchStart(cdp, x, y) {
  return cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y, id: 11 }] });
}

function touchEnd(cdp) {
  return cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
}

/**
 * Records the `pointerId` of the next real touch press, so a later dispatch can name the very
 * contact the element observed. This is spec-side instrumentation of the element's DOM — the same
 * spirit as the release-call counter of `web-input-pointer-capture` — never a Kadre surface.
 */
async function recordNextTouchPointerId(page, host) {
  await page.evaluate((selector) => {
    window.__kadreTouchPointerIds = [];
    document.querySelector(selector).addEventListener('pointerdown', (event) => {
      window.__kadreTouchPointerIds.push(event.pointerId);
    });
  }, `[data-kadre-host="${host}"]`);
}

async function recordedTouchPointerId(page) {
  return page.evaluate(() => window.__kadreTouchPointerIds.at(-1));
}

test('web-touch-capability-structural', async ({ page }) => {
  await loadScenario(page, 'touch');
  const host = page.locator('[data-kadre-host="touch"]');
  await awaitTouchCapability(host);

  // The structural claim, before any input was driven: the capability cell says touch is observed,
  // gestures stay unsupported, and no contact, pointer or event was invented to fill the state.
  await expect(host).toHaveAttribute('data-kadre-input-state', /keys=\[\] mods=\[\] pointers=\[\] touches=0/);
  await expect(host).toHaveAttribute('data-kadre-input-events', '');
  await expect(host).toHaveAttribute('data-kadre-input-count', '0');
  await expect(host).toHaveAttribute('data-kadre-input-resets', '0:');
});

test('web-touch-started', async ({ browser }) => {
  // A browsing context that declares touch: the contact is real, like the touch-deferred test's.
  const context = await browser.newContext({ hasTouch: true });
  try {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await loadScenario(page, 'touch');
    const host = page.locator('[data-kadre-host="touch"]');
    await awaitTouchCapability(host);
    const box = await host.boundingBox();

    // One real contact, held. The reducer opens a contact of its own for it: a `TouchChanged` of the
    // Started phase at the surface position the touch named.
    await touchStart(cdp, box.x + 60, box.y + 40);
    await expect(host).toHaveAttribute('data-kadre-input-events', /^touch:started@\(60,40\):rev=\d+$/);
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=1/);

    await touchEnd(cdp);
    await expect(host).toHaveAttribute(
      'data-kadre-input-events',
      /^touch:started@\(60,40\):rev=\d+;touch:ended@\(60,40\):rev=\d+$/,
    );
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=0/);
  } finally {
    await context.close();
  }
});

test('web-touch-ended', async ({ browser }) => {
  const context = await browser.newContext({ hasTouch: true });
  try {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await loadScenario(page, 'touch');
    const host = page.locator('[data-kadre-host="touch"]');
    await awaitTouchCapability(host);
    const box = await host.boundingBox();
    await recordNextTouchPointerId(page, 'touch');

    await touchStart(cdp, box.x + 60, box.y + 40);
    await expect(host).toHaveAttribute('data-kadre-input-state', /touches=1/);
    await touchEnd(cdp);
    await expect(host).toHaveAttribute('data-kadre-input-events', /touch:ended@\(60,40\):rev=\d+$/);
    await expect(host).toHaveAttribute('data-kadre-input-state', /touches=0/);
    const frozen = await host.getAttribute('data-kadre-input-events');
    const count = await host.getAttribute('data-kadre-input-count');

    // The end retired the contact: a late event of the same DOM pointer — the one the element really
    // observed — answers nothing, exactly as the reducer refuses a move of a contact it retired.
    // **Synthetic, and why**: no real contact exists any more for the browser to move; the event is
    // the probe of the retirement, not a stand-in for an input the browser would deliver.
    const pointerId = await recordedTouchPointerId(page);
    await page.evaluate(({ id, x, y }) => {
      document.querySelector('[data-kadre-host="touch"]').dispatchEvent(new PointerEvent('pointermove', {
        pointerId: id,
        pointerType: 'touch',
        isPrimary: true,
        button: -1,
        buttons: 0,
        clientX: x,
        clientY: y,
        bubbles: true,
      }));
    }, { id: pointerId, x: box.x + 80, y: box.y + 50 });
    await nextFrame(page);
    await expect(host).toHaveAttribute('data-kadre-input-events', frozen);
    await expect(host).toHaveAttribute('data-kadre-input-count', count);
    await expect(host).toHaveAttribute('data-kadre-input-state', /touches=0/);
  } finally {
    await context.close();
  }
});

test('web-touch-cancelled', async ({ browser }) => {
  const context = await browser.newContext({ hasTouch: true });
  try {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await loadScenario(page, 'touch');
    const host = page.locator('[data-kadre-host="touch"]');
    await awaitTouchCapability(host);
    const box = await host.boundingBox();
    await recordNextTouchPointerId(page, 'touch');

    await touchStart(cdp, box.x + 60, box.y + 40);
    await expect(host).toHaveAttribute('data-kadre-input-state', /touches=1/);
    const pointerId = await recordedTouchPointerId(page);

    // **Synthetic, and why**: a browser cancels a touch contact only when it takes the gesture over
    // itself — a scroll the host's own `touch-action` allows — and a contact held on a fixed element
    // of a scripted page never produces one; Playwright cannot revoke a contact at all. The
    // revocation is the browser's own fact (the same shape the phase-3 spec dispatched for a mouse),
    // so it is dispatched by hand with the pointerId the element really observed.
    await page.evaluate(({ id, x, y }) => {
      document.querySelector('[data-kadre-host="touch"]').dispatchEvent(new PointerEvent('pointercancel', {
        pointerId: id,
        pointerType: 'touch',
        isPrimary: true,
        button: -1,
        buttons: 1,
        clientX: x,
        clientY: y,
        bubbles: true,
      }));
    }, { id: pointerId, x: box.x + 60, y: box.y + 40 });

    // The cancellation is one exit: the contact is dropped with everything it held, and nothing is
    // left behind — the release the browser would then report cannot resurrect it.
    await expect(host).toHaveAttribute('data-kadre-input-events', /^touch:started@\(60,40\):rev=\d+;touch:cancelled@\(60,40\):rev=\d+$/);
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=0/);
  } finally {
    await context.close();
  }
});

test('web-touch-multi-contact', async ({ browser }) => {
  const context = await browser.newContext({ hasTouch: true });
  try {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await loadScenario(page, 'touch');
    const host = page.locator('[data-kadre-host="touch"]');
    await awaitTouchCapability(host);
    const box = await host.boundingBox();

    // **Synthetic, and why**: two simultaneous touch contacts is the one shape a single input
    // pipeline cannot produce — CDP drives one touch stream, `page.touchscreen` one tap at a time.
    // The DOM dispatches the second contact by hand with the pointerType and a distinct pointerId
    // real contacts carry, while the first is a real held contact of the pipeline.
    await recordNextTouchPointerId(page, 'touch');
    await touchStart(cdp, box.x + 30, box.y + 40);
    await expect(host).toHaveAttribute('data-kadre-input-state', /touches=1/);
    const pointerId = await recordedTouchPointerId(page);

    await page.evaluate(({ syntheticId, x, y }) => {
      const host = document.querySelector('[data-kadre-host="touch"]');
      host.dispatchEvent(new PointerEvent('pointerdown', {
        pointerId: syntheticId,
        pointerType: 'touch',
        isPrimary: false,
        button: 0,
        buttons: 1,
        clientX: x,
        clientY: y,
        bubbles: true,
      }));
    }, { syntheticId: 4242, x: box.x + 110, y: box.y + 70 });

    // Two contacts, two reductions: the state carries both, and a move of one leaves the other.
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=2/);
    await page.evaluate(({ syntheticId, x, y }) => {
      document.querySelector('[data-kadre-host="touch"]').dispatchEvent(new PointerEvent('pointermove', {
        pointerId: syntheticId,
        pointerType: 'touch',
        isPrimary: false,
        button: -1,
        buttons: 1,
        clientX: x,
        clientY: y,
        bubbles: true,
      }));
    }, { syntheticId: 4242, x: box.x + 120, y: box.y + 80 });
    await expect(host).toHaveAttribute('data-kadre-input-events', /;touch:moved@\(120,80\):rev=\d+$/);
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=2/);

    // Each contact ends on its own identity, and the last one leaves nothing behind.
    await page.evaluate(({ syntheticId, x, y }) => {
      document.querySelector('[data-kadre-host="touch"]').dispatchEvent(new PointerEvent('pointerup', {
        pointerId: syntheticId,
        pointerType: 'touch',
        isPrimary: false,
        button: 0,
        buttons: 0,
        clientX: x,
        clientY: y,
        bubbles: true,
      }));
    }, { syntheticId: 4242, x: box.x + 120, y: box.y + 80 });
    await expect(host).toHaveAttribute('data-kadre-input-events', /;touch:ended@\(120,80\):rev=\d+$/);
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=1/);
    await touchEnd(cdp);
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=0/);
  } finally {
    await context.close();
  }
});

test('web-touch-interaction-trigger', async ({ browser }) => {
  const context = await browser.newContext({ hasTouch: true });
  try {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await loadScenario(page, 'touch-interaction');
    const host = page.locator('[data-kadre-host="touch-interaction"]');
    await awaitTouchCapability(host);

    // The armed flag is the installation barrier: the handler exists, and the next touch press is
    // dispatched into it through the port's own listener.
    await expect(host).toHaveAttribute('data-kadre-interaction-armed', 'true');
    const box = await host.boundingBox();

    // One real touch press. The port dispatches the `TouchStarted` trigger inside the very callback
    // of the press, *before* the ordinary stimulus of the same event is admitted — and the record
    // names that moment: at dispatch the published state still carried no contact. The trigger's
    // position is the surface position of the press.
    await touchStart(cdp, box.x + 60, box.y + 40);
    await expect(host).toHaveAttribute('data-kadre-touch-interaction', 'started@(60,40):touchesAtDispatch=0');

    // And the ordinary path still delivered the touch: the trigger replaced nothing.
    await expect(host).toHaveAttribute('data-kadre-input-events', /^touch:started@\(60,40\):rev=\d+$/);
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=1/);
    await touchEnd(cdp);
    await expect(host).toHaveAttribute('data-kadre-input-state', /touches=0/);
  } finally {
    await context.close();
  }
});

test('web-touch-focus-loss-clears', async ({ browser }) => {
  const context = await browser.newContext({ hasTouch: true });
  try {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await loadScenario(page, 'touch-focus');
    const host = page.locator('[data-kadre-host="touch-focus"]');
    await awaitTouchCapability(host);
    const box = await host.boundingBox();

    await host.focus();
    await touchStart(cdp, box.x + 60, box.y + 40);
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=1/);

    // A real loss of activation: the page moves the focus out of the element's subtree, and the
    // lifecycle reduction — not a synthetic input event — is what observes it. The held contact is
    // cleared with everything else, and the reset is the only thing that cleared it.
    await page.locator('[data-kadre-focus-outside]').focus();
    await expect(host).toHaveAttribute('data-kadre-input-resets', '1:focusLost');
    await expect(host).toHaveAttribute('data-kadre-input-state', /keys=\[\] mods=\[\] pointers=\[\] touches=0/);
    const frozen = await host.getAttribute('data-kadre-input-events');

    // The contact the browser still holds can deliver nothing after the reset: the identity the
    // reducer kept is gone, so the release the browser then reports is not a resurrection of one.
    await touchEnd(cdp);
    await nextFrame(page);
    await expect(host).toHaveAttribute('data-kadre-input-events', frozen);
    await expect(host).toHaveAttribute('data-kadre-input-state', /touches=0/);
  } finally {
    await context.close();
  }
});

test('web-touch-no-pointer-alias', async ({ browser }) => {
  const context = await browser.newContext({ hasTouch: true });
  try {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await loadScenario(page, 'touch');
    const host = page.locator('[data-kadre-host="touch"]');
    await awaitTouchCapability(host);
    const box = await host.boundingBox();

    // A held real touch: the model has no touch member in `PointerKind`, and a contact never becomes
    // a pointer — the state carries the contact in `touches` and nothing at all in `pointers`.
    await touchStart(cdp, box.x + 60, box.y + 40);
    await touchEnd(cdp);
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=0/);
    const events = await host.getAttribute('data-kadre-input-events');

    // Every event the contact produced is a touch observation: no `enter`, no `move`, no `button`
    // entry exists for it, and the pressure the pointer path would have carried was never published
    // as a pointer fact. The whole journal is the two phases of one contact.
    expect(events).toMatch(/^touch:started@\(60,40\):rev=\d+;touch:ended@\(60,40\):rev=\d+$/);
  } finally {
    await context.close();
  }
});
