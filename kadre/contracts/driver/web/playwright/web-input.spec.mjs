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
 * The installation of the fixture's input observation, and the surface's own capability snapshot.
 *
 * Reading this attribute is the barrier every input test opens with: the observation exists (its event
 * subscription is registered before this attribute can be published), the session published the input
 * capabilities structurally, and the tests below can therefore correlate the input they drive with what
 * the surface observed of it. The whole public cell is asserted, so a regression in any member of it —
 * including the members this surface now delivers, touch, drag-and-drop and the text session, and the
 * gesture half that stays unsupported (D-T2) — is named by this one line.
 */
async function awaitInputObservation(host) {
  await expect(host).toHaveAttribute(
    'data-kadre-input-caps',
    'keyboard=available pointer=available touch=available gestures=unsupported:gestureinput '
      + 'dragAndDrop=available textInput=supported rawInput=unsupported:rawinputaccess',
  );
}

/** The scroll payloads the surface published, in order, without their revisions. */
function scrollDeltas(events) {
  return events
    .split(';')
    .filter((entry) => entry.startsWith('scroll:'))
    .map((entry) => entry.slice('scroll:'.length).split(':rev=')[0]);
}

/** The input revisions of the published events, in publication order. */
function revisions(events) {
  return [...events.matchAll(/:rev=(\d+)/g)].map((match) => Number(match[1]));
}

/**
 * The page's scroll position, once it stopped moving.
 *
 * Chromium animates a keyboard scroll over a few frames, so the position a test reads the moment it
 * asks is not the position the default produced. Waiting for the value to hold still separates the
 * browser's own motion from the reading of it; the state of that observation is dropped first, so a
 * call made while nothing has moved yet cannot be answered by the call before it.
 */
async function settledScrollY(page) {
  await page.evaluate(() => {
    delete window.__kadreSettledValue;
    delete window.__kadreSettledAt;
  });
  await page.waitForFunction(() => {
    const now = performance.now();
    const current = window.scrollY;
    if (window.__kadreSettledValue === current) return now - window.__kadreSettledAt > 200;
    window.__kadreSettledValue = current;
    window.__kadreSettledAt = now;
    return false;
  });
  return page.evaluate(() => window.scrollY);
}

/** The page's scroll offset, polled until the browser's own motion brought it somewhere. */
function scrollY(page) {
  return expect.poll(() => page.evaluate(() => window.scrollY), { timeout: 3_000 });
}

test('web-input-key-state-before-event', async ({ page }) => {
  await loadScenario(page, 'input-key');
  const host = page.locator('[data-kadre-host="input-key"]');
  await awaitInputObservation(host);
  await host.focus();

  // One real key press. The surface publishes the observation and the state that observation produced
  // in the same admission, so the event names the revision the state already carries: a consumer that
  // reads `input.state` on the event reads the state *of* that event, never the one before it. The
  // pressed physical key is the HID usage of `KeyA` (page 0x07, usage 0x04).
  await page.keyboard.down('a');
  await expect(host).toHaveAttribute('data-kadre-input-events', /^key:a:pressed:mods\[\]:rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-input-order', 'key:a:pressed:mods[]:synced');
  await expect(host).toHaveAttribute('data-kadre-input-state', /keys=\[code:7:4\]/);

  // The release is the same claim in the other direction, and it leaves no key pressed.
  await page.keyboard.up('a');
  await expect(host).toHaveAttribute(
    'data-kadre-input-events',
    /^key:a:pressed:mods\[\]:rev=\d+;key:a:released:mods\[\]:rev=\d+$/,
  );
  await expect(host).toHaveAttribute(
    'data-kadre-input-order',
    'key:a:pressed:mods[]:synced;key:a:released:mods[]:synced',
  );
  await expect(host).toHaveAttribute('data-kadre-input-state', /keys=\[\] mods=\[\]/);
});

test('web-input-key-modifiers', async ({ page }) => {
  await loadScenario(page, 'input-key');
  const host = page.locator('[data-kadre-host="input-key"]');
  await awaitInputObservation(host);
  await host.focus();

  // A real shift press: the modifier the browser reports is carried by the observation and by the
  // published state, both of them the browser's own flags rather than a tracked copy of them.
  await page.keyboard.down('Shift');
  await expect(host).toHaveAttribute('data-kadre-input-events', /^key:Shift:pressed:mods\[Shift\]:rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-input-state', /mods=\[Shift\]/);

  await page.keyboard.down('a');
  await expect(host).toHaveAttribute('data-kadre-input-events', /;key:a:pressed:mods\[Shift\]:rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-input-state', /mods=\[Shift\]/);

  // Releasing the letter keeps the modifier held; releasing the modifier leaves nothing stuck, and the
  // state the surface publishes when its event is observed already says so.
  await page.keyboard.up('a');
  await expect(host).toHaveAttribute('data-kadre-input-state', /mods=\[Shift\]/);
  await page.keyboard.up('Shift');
  await expect(host).toHaveAttribute('data-kadre-input-events', /;key:Shift:released:mods\[\]:rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-input-state', /keys=\[\] mods=\[\] pointers=\[\] touches=0/);
  await expect(host).toHaveAttribute('data-kadre-input-order', /key:Shift:released:mods\[\]:synced$/);
});

test('web-input-pointer-primary', async ({ page }) => {
  await loadScenario(page, 'input-pointer');
  const host = page.locator('[data-kadre-host="input-pointer"]');
  await awaitInputObservation(host);
  const box = await host.boundingBox();

  // A real mouse: entry, a press, a motion and the release. The positions are the surface's own logical
  // space, which is the element's box, so a motion is the real movement of the pointer.
  await page.mouse.move(box.x + 40, box.y + 30);
  await expect(host).toHaveAttribute('data-kadre-input-events', /^enter:mouse@\(40,30\):rev=\d+/);

  await page.mouse.down();
  await expect(host).toHaveAttribute('data-kadre-input-events', /;button:primary:pressed@\(40,30\):rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[mouse#primary@\(40,30\)\]/);

  await page.mouse.move(box.x + 70, box.y + 30);
  await expect(host).toHaveAttribute('data-kadre-input-events', /;move:mouse@\(70,30\):d=\(30,0\):rev=\d+$/);

  await page.mouse.up();
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[mouse#@\(70,30\)\]/);
  await expect(host).toHaveAttribute('data-kadre-input-order', /button:primary:pressed@\(40,30\):synced/);
});

test('web-input-pointer-multi', async ({ page }) => {
  await loadScenario(page, 'input-pointer-multi');
  const host = page.locator('[data-kadre-host="input-pointer-multi"]');
  await awaitInputObservation(host);
  const box = await host.boundingBox();

  // Real multi-button input: a primary press, then a secondary one. Chromium reports the second button
  // as a `pointermove` whose `buttons` is 3 rather than as a second `pointerdown`, and the release of
  // either button arrives on its own — so both buttons really went down and came up.
  await page.mouse.move(box.x + 60, box.y + 40);
  await page.mouse.down();
  await page.mouse.down({ button: 'right' });
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[mouse#primary@\(60,40\)\]/);
  await page.mouse.up({ button: 'right' });
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[mouse#primary@\(60,40\)\]/);

  // **Synthetic, and why**: the runtime keeps one pointer identity per surface (D11), so two DOM
  // pointers at once is exactly the case Chromium cannot produce through its input pipeline — a second
  // pointer of another kind has to be dispatched by hand. A pen is the other delivered kind.
  await page.evaluate(({ x, y }) => {
    const host = document.querySelector('[data-kadre-host="input-pointer-multi"]');
    host.dispatchEvent(new PointerEvent('pointerdown', {
      pointerId: 42,
      pointerType: 'pen',
      isPrimary: true,
      button: 0,
      buttons: 1,
      clientX: x,
      clientY: y,
      tiltX: 30,
      tiltY: -20,
      twist: 90,
      tangentialPressure: 0.25,
      bubbles: true,
    }));
  }, { x: box.x + 60, y: box.y + 40 });
  // One pointer entry, not two: the pen merged onto the mouse's identity and carries its own kind and
  // its own pen state, and the primary button the mouse still held is not duplicated.
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[pen#primary@\(60,40\):pen\]/);

  await page.evaluate(({ x, y }) => {
    const host = document.querySelector('[data-kadre-host="input-pointer-multi"]');
    host.dispatchEvent(new PointerEvent('pointerup', {
      pointerId: 42,
      pointerType: 'pen',
      isPrimary: true,
      button: 0,
      buttons: 0,
      clientX: x,
      clientY: y,
      bubbles: true,
    }));
  }, { x: box.x + 60, y: box.y + 40 });
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[pen#@\(60,40\):pen\]/);

  // The mouse releases the button it held; the entry keeps no pressed button, and it carries the kind
  // of the event in hand — the mouse's own, since that is the release the browser reported. The
  // revisions of everything published are monotone: one reducer, one sequence, whatever the DOM pointer.
  await page.mouse.up();
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[mouse#@\(60,40\)\]/);
  const events = await host.getAttribute('data-kadre-input-events');
  const published = revisions(events);
  expect(published.length).toBeGreaterThan(0);
  expect(published).toEqual([...published].sort((left, right) => left - right));
});

test('web-input-pointer-cancel', async ({ page }) => {
  await loadScenario(page, 'input-pointer-cancel');
  const host = page.locator('[data-kadre-host="input-pointer-cancel"]');
  await awaitInputObservation(host);
  const box = await host.boundingBox();

  await page.mouse.move(box.x + 50, box.y + 30);
  await page.mouse.down();
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[mouse#primary@\(50,30\)\]/);

  // **Synthetic, and why**: Chromium never cancels a mouse contact — `pointercancel` is the event a
  // browser emits for a contact *it* revoked (a touch, or a stylus leaving range), and Playwright cannot
  // revoke a mouse contact at all. The cancellation is therefore dispatched by hand, with the pointer
  // the element really observed pressed.
  await page.evaluate(({ x, y }) => {
    const host = document.querySelector('[data-kadre-host="input-pointer-cancel"]');
    host.dispatchEvent(new PointerEvent('pointercancel', {
      pointerId: 1,
      pointerType: 'mouse',
      isPrimary: true,
      button: 0,
      buttons: 1,
      clientX: x,
      clientY: y,
      bubbles: true,
    }));
  }, { x: box.x + 50, y: box.y + 30 });

  // The cancellation is one exit: the pointer is dropped with everything it held, and no button is left
  // behind — the release the browser then reports cannot resurrect a stuck one.
  await expect(host).toHaveAttribute('data-kadre-input-events', /;leave:mouse:rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\]/);
  await page.mouse.up();
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[mouse#@\(50,30\)\]/);
});

test('web-input-wheel-pixel', async ({ page }) => {
  await loadScenario(page, 'input-wheel');
  const host = page.locator('[data-kadre-host="input-wheel"]');
  await awaitInputObservation(host);
  const box = await host.boundingBox();

  // A real wheel. Chromium reports a pixel delta (`deltaMode == 0`), so the model carries a logical
  // scroll, and every wheel the pipeline delivers in its own frontier is one observation: three
  // wheels, three exact payloads, nothing merged away and nothing approximated.
  await page.mouse.move(box.x + 100, box.y + 60);
  await page.mouse.wheel(0, 120);
  await expect.poll(async () => scrollDeltas(await host.getAttribute('data-kadre-input-events')))
    .toEqual(['logical(0,120)']);

  // A real frame between two wheels, so each one opens a frontier of its own: the Web frontier opens
  // for the first wheel a new animation frame delivers (`WebScrollBoundary.advance`), and two wheels
  // the browser delivers in one frame may legitimately merge into one summed scroll. Waiting for the
  // frame is what makes "one wheel, one observation" what this test asserts rather than a bet on the
  // frame the previous CDP command happened to land in.
  await nextFrame(page);
  await page.mouse.wheel(0, 120);
  await expect.poll(async () => scrollDeltas(await host.getAttribute('data-kadre-input-events')))
    .toEqual(['logical(0,120)', 'logical(0,120)']);

  await nextFrame(page);
  await page.mouse.wheel(-40, 0);
  await expect.poll(async () => scrollDeltas(await host.getAttribute('data-kadre-input-events')))
    .toEqual(['logical(0,120)', 'logical(0,120)', 'logical(-40,0)']);

  // A scroll changes no input state, so its event carries the revision in effect and the state cell
  // agrees with it all the same.
  await expect(host).toHaveAttribute('data-kadre-input-order', /scroll:logical\(-40,0\):synced$/);
});

test('web-input-wheel-lines', async ({ page }) => {
  await loadScenario(page, 'input-wheel');
  const host = page.locator('[data-kadre-host="input-wheel"]');
  await awaitInputObservation(host);

  // **Synthetic, and why**: every wheel Chromium's input pipeline delivers is `deltaMode == 0`, so the
  // line-mode variant the model must carry cannot be produced by a real wheel here; it is dispatched by
  // hand. It is mapped, not approximated: the payload stays a count of lines.
  await page.evaluate(() => {
    const host = document.querySelector('[data-kadre-host="input-wheel"]');
    host.dispatchEvent(new WheelEvent('wheel', { deltaMode: 1, deltaX: 0, deltaY: 3, bubbles: true }));
  });
  await expect.poll(async () => scrollDeltas(await host.getAttribute('data-kadre-input-events')))
    .toEqual(['lines(0,3)']);
  const afterLines = await host.getAttribute('data-kadre-input-count');

  // The page-mode variant (D9) is not delivered at all: it is named as a non-normalisable variant and
  // dropped, never converted into lines or pixels Kadre would have to invent.
  await page.evaluate(() => {
    const host = document.querySelector('[data-kadre-host="input-wheel"]');
    host.dispatchEvent(new WheelEvent('wheel', { deltaMode: 2, deltaX: 0, deltaY: 2, bubbles: true }));
  });
  await nextFrame(page);
  await expect(host).toHaveAttribute('data-kadre-input-count', afterLines);
  await expect.poll(async () => scrollDeltas(await host.getAttribute('data-kadre-input-events')))
    .toEqual(['lines(0,3)']);
});

test('web-input-focus-reset', async ({ page }) => {
  await loadScenario(page, 'input-focus');
  const host = page.locator('[data-kadre-host="input-focus"]');
  await awaitInputObservation(host);
  const box = await host.boundingBox();
  await host.focus();

  // A key and a button held, both really: the key from the keyboard, the button from the mouse.
  await page.keyboard.down('a');
  await page.mouse.move(box.x + 50, box.y + 40);
  await page.mouse.down();
  await expect(host).toHaveAttribute(
    'data-kadre-input-state',
    /keys=\[code:7:4\] mods=\[\] pointers=\[mouse#primary@\(50,40\)\]/,
  );

  // A real loss of activation: the page moves the focus out of the element's subtree, and the lifecycle
  // reduction — not a synthetic input event — is what observes it.
  await page.locator('[data-kadre-focus-outside]').focus();

  // One loss, one neutral snapshot and exactly one reset, and the reset is the *only* thing that
  // neutralised anything: no key release and no button release was invented for it.
  await expect(host).toHaveAttribute('data-kadre-input-resets', '1:focusLost');
  await expect(host).toHaveAttribute('data-kadre-input-state', /keys=\[\] mods=\[\] pointers=\[\] touches=0/);
  const events = await host.getAttribute('data-kadre-input-events');
  expect(events).not.toMatch(/key:a:released/);
  expect(events).not.toMatch(/button:primary:released/);
  await expect(host).toHaveAttribute('data-kadre-input-order', /key:a:pressed:mods\[\]:synced/);
  await expect(host).toHaveAttribute('data-kadre-input-order', /reset:focusLost:synced$/);

  // The button the browser still reports as held is delivered after the reset, and it cannot leave a
  // stuck button behind: the release empties it.
  await page.mouse.up();
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[mouse#@\(50,40\)\]/);
});

test('web-input-terminal-closed', async ({ page }) => {
  await loadScenario(page, 'input-terminal');
  const host = page.locator('[data-kadre-host="input-terminal"]');
  await awaitInputObservation(host);
  await host.focus();

  // A key held when the session is stopped. The close is the session's own (the spec asks for it),
  // which is the terminal transition a host-driven detach reaches too.
  await page.keyboard.down('a');
  await expect(host).toHaveAttribute('data-kadre-input-state', /keys=\[code:7:4\]/);
  const frozen = await host.getAttribute('data-kadre-input-state');
  const published = await host.getAttribute('data-kadre-input-events');

  await command(page, 'kadre-stop-input');
  await expect(page.locator('body')).toHaveAttribute('data-kadre-input-terminal-session', 'terminated');
  // The input lane is closed: the surface's events flow completes, as a consumer of it observes.
  await expect(host).toHaveAttribute('data-kadre-input-flow-closed', 'true');

  // The close does not neutralise: the held key stays visible in the frozen snapshot, and the close
  // publishes no reset of its own. Only a loss of activation neutralises (see web-input-focus-reset).
  await expect(host).toHaveAttribute('data-kadre-input-state', frozen);
  await expect(host).toHaveAttribute('data-kadre-input-events', published);
  await expect(host).toHaveAttribute('data-kadre-input-resets', '0:');

  // Real input after the terminal transition produces nothing: no new state, no new event, no reset.
  await page.keyboard.press('b');
  await page.keyboard.press('ArrowDown');
  const box = await host.boundingBox();
  await page.mouse.move(box.x + 30, box.y + 30);
  await page.mouse.down();
  await page.mouse.up();
  await nextFrame(page);
  await expect(host).toHaveAttribute('data-kadre-input-state', frozen);
  await expect(host).toHaveAttribute('data-kadre-input-events', published);
  await expect(host).toHaveAttribute('data-kadre-input-resets', '0:');
});

test('web-input-default-behavior', async ({ page }) => {
  await loadScenario(page, 'input-default-behavior');
  const host = page.locator('[data-kadre-host="input-default-behavior"]');
  await awaitInputObservation(host);
  const box = await host.boundingBox();
  await expect(host).toHaveAttribute('data-kadre-surface-state', /^attached\|hostdefault\|none\|rev=\d+$/);

  // Under `HostDefault` — the state a surface starts in — the browser keeps its own behaviour for both
  // defaults Kadre could suppress: the real wheel and the real arrow key really move the document.
  await host.focus();
  await page.mouse.move(box.x + 100, box.y + 60);
  await page.mouse.wheel(0, 120);
  await scrollY(page).toBeGreaterThan(60);
  expect(await settledScrollY(page)).toBeGreaterThan(60);

  await page.evaluate(() => window.scrollTo(0, 0));
  expect(await settledScrollY(page)).toBe(0);
  await host.focus();
  await page.keyboard.press('ArrowDown');
  await scrollY(page).toBeGreaterThan(20);
  expect(await settledScrollY(page)).toBeGreaterThan(20);

  // Under `SuppressWhenPossible` the same two inputs are still delivered to the surface, and the
  // browser's defaults are dropped instead: nothing moves.
  await page.evaluate(() => window.scrollTo(0, 0));
  expect(await settledScrollY(page)).toBe(0);
  await command(page, 'kadre-behavior-suppress');
  await expect(host).toHaveAttribute('data-kadre-behavior-suppress', 'applied');
  await expect(host).toHaveAttribute('data-kadre-surface-state', /^attached\|suppresswhenpossible\|none\|rev=\d+$/);
  const beforeWheel = await host.getAttribute('data-kadre-input-count');

  await page.mouse.move(box.x + 100, box.y + 60);
  await page.mouse.wheel(0, 120);
  await expect(host).toHaveAttribute('data-kadre-input-events', /scroll:logical\(0,120\):rev=\d+$/);
  await expect.poll(async () => Number(await host.getAttribute('data-kadre-input-count')))
    .toBeGreaterThan(Number(beforeWheel));
  expect(await settledScrollY(page)).toBe(0);

  await host.focus();
  await page.keyboard.press('ArrowDown');
  expect(await settledScrollY(page)).toBe(0);

  // The suppression is bounded to the closed set of suppressed categories: `Tab`'s default is not a
  // document scroll, so it is kept — the focus really moves to the next stop of the page's tab order,
  // which the fixture owns and which is not inside the surface.
  const activeHost = () => page.evaluate(() => document.activeElement?.getAttribute('data-kadre-host') ?? null);
  expect(await activeHost()).toBe('input-default-behavior');
  await page.keyboard.press('Tab');
  await expect(page.locator('[data-kadre-focus-outside]')).toBeFocused();

  // And it follows the committed state rather than latching: asking for the default back gives the page
  // its own behaviour again.
  await command(page, 'kadre-behavior-host-default');
  await expect(host).toHaveAttribute('data-kadre-behavior-host-default', 'applied');
  await page.evaluate(() => window.scrollTo(0, 0));
  expect(await settledScrollY(page)).toBe(0);
  await host.focus();
  await page.mouse.move(box.x + 100, box.y + 60);
  await page.mouse.wheel(0, 120);
  await scrollY(page).toBeGreaterThan(60);
});

test('web-input-pointer-capture', async ({ page }) => {
  await loadScenario(page, 'input-pointer-capture');
  const host = page.locator('[data-kadre-host="input-pointer-capture"]');
  await awaitInputObservation(host);
  const box = await host.boundingBox();

  // A capture without an owned pointer is refused before any browser call, and `Locked` stays outside the
  // promise of this phase: no pointer lock, no synthetic success, state untouched.
  await command(page, 'kadre-capture-unowned');
  await expect(host).toHaveAttribute(
    'data-kadre-capture-unowned',
    'partiallyApplied[pointerCapture=interactionRequired:missing]',
  );
  await expect(host).toHaveAttribute('data-kadre-surface-state', /^attached\|hostdefault\|none\|rev=\d+$/);

  await command(page, 'kadre-capture-locked');
  await expect(host).toHaveAttribute(
    'data-kadre-capture-locked',
    'partiallyApplied[pointerCapture=unsupported:updatesurface]',
  );
  await expect(host).toHaveAttribute('data-kadre-surface-state', /^attached\|hostdefault\|none\|rev=\d+$/);

  // The capture itself is asked for with a real pointer: Chromium refuses `setPointerCapture` for a
  // pointer it does not consider active, so a synthetic press could not prove that the effect was really
  // taken. The release calls are counted to tell a real capture from a published label.
  await page.evaluate(() => {
    window.__kadreReleaseCalls = 0;
    const original = Element.prototype.releasePointerCapture;
    Element.prototype.releasePointerCapture = function (...args) {
      window.__kadreReleaseCalls += 1;
      return original.apply(this, args);
    };
  });

  await page.mouse.move(box.x + 60, box.y + 40);
  await page.mouse.down();
  await command(page, 'kadre-capture-confined');
  await expect(host).toHaveAttribute('data-kadre-capture-confined', 'applied');
  await expect(host).toHaveAttribute('data-kadre-surface-state', /^attached\|hostdefault\|confined\|rev=\d+$/);

  // The confinement is the browser's, not a label: Chromium routes the captured pointer's events to the
  // element, so a motion far outside the element's box still reaches the surface.
  await page.mouse.move(box.x + 600, box.y + 500);
  await expect(host).toHaveAttribute('data-kadre-pointer-motion', '(600,500)');
  await expect(host).toHaveAttribute('data-kadre-pointer-outside', 'true');
  await expect(host).toHaveAttribute('data-kadre-surface-state', /confined\|rev=\d+$/);

  // The release ends the capture with the pointer, and the surface reconciles the claim it published.
  await page.mouse.up();
  await expect(host).toHaveAttribute('data-kadre-surface-state', /^attached\|hostdefault\|none\|rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-input-events', /;leave:mouse:rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\]/);

  // A second capture, then a real loss of activation. Chromium keeps the mouse capture across a focus
  // move, so this arm is the recorded divergence: the surface publishes `None` without calling
  // `releasePointerCapture`, while the browser still confines the pointer to the element — the motion
  // after the loss is delivered by a capture the surface no longer claims.
  await page.mouse.move(box.x + 60, box.y + 40);
  await page.mouse.down();
  await command(page, 'kadre-capture-confined');
  await expect(host).toHaveAttribute('data-kadre-surface-state', /^attached\|hostdefault\|confined\|rev=\d+$/);
  await page.locator('[data-kadre-focus-outside]').focus();
  await expect(host).toHaveAttribute('data-kadre-surface-state', /^attached\|hostdefault\|none\|rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-input-resets', '1:focusLost');
  await page.mouse.move(box.x + 620, box.y + 520);
  await expect(host).toHaveAttribute('data-kadre-pointer-motion', '(620,520)');
  expect(await page.evaluate(() => window.__kadreReleaseCalls)).toBe(0);

  await page.mouse.up();
});

test('web-input-touch-delivered', async ({ browser }) => {
  // A browsing context that declares touch: the tap the element receives is a real one, which is the
  // only honest way to prove the input lane's touch delivery in this file's own terms.
  const context = await browser.newContext({ hasTouch: true });
  try {
    const page = await context.newPage();
    await loadScenario(page, 'input-touch-deferred');
    const host = page.locator('[data-kadre-host="input-touch-deferred"]');
    await awaitInputObservation(host);
    const box = await host.boundingBox();

    await page.touchscreen.tap(box.x + 50, box.y + 40);
    await nextFrame(page);

    // The contact is delivered through the same input observation every input of this file rides: the
    // tap's two phases are the journal's only entries — no pointer fact of any kind exists for it —
    // and the state keeps the contact out of the pointers, with nothing stuck after it. What stays
    // deferred is the gesture half of the boundary (D-T2): the capability cell this file opens every
    // test with still promises no recognizer, and the touch delivery contract itself is BCK-004's.
    await expect(host).toHaveAttribute(
      'data-kadre-input-events',
      /^touch:started@\(50,40\):rev=\d+;touch:ended@\(50,40\):rev=\d+$/,
    );
    await expect(host).toHaveAttribute('data-kadre-input-count', '2');
    await expect(host).toHaveAttribute('data-kadre-input-state', /pointers=\[\] touches=0/);
    await awaitInputObservation(host);
  } finally {
    await context.close();
  }
});
