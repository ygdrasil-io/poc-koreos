import { expect, test } from '@playwright/test';
import { STANDARD_AXIS_CODES, STANDARD_BUTTON_CODES, axis, button, installGamepadStub, nativePad, standardPad } from './gamepad-stub.mjs';

/**
 * BCK-008 — the web device/gamepad inventory and routing contract.
 *
 * The pads these scenarios see are the synthetic source `gamepad-stub.mjs` installs (the D10
 * precedent: Chromium cannot inject a real gamepad, so the harness patches the navigator's poll
 * the hub reads every animation frame). What the specs prove is what the runtime publishes about
 * whatever that poll reports — never a fabricated pad, never an invented descriptor, never a
 * state a suspended or closed session should have stopped publishing. The real-device privacy
 * gate (pads invisible until user activation) is manual-charter material and is claimed nowhere
 * here.
 */

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

async function loadScenario(page, scenario) {
  await page.goto(`${fixtureUrl}?scenario=${scenario}`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
}

/** Dispatches one of the fixture's command events; every command is driven from the browser side. */
async function command(page, name) {
  await page.evaluate((event) => document.dispatchEvent(new Event(event)), name);
}

/** Replaces the synthetic source's pad array, holes and all, the way a browser's poll would move. */
async function setPads(page, specs) {
  await page.evaluate((pads) => window.__kadreGamepadStub.setPads(pads), specs);
}

/** Rewrites one button of the pad at the DOM index named; the string `'nan'` is the hostile reading. */
async function setButton(page, index, buttonIndex, value) {
  await mutate(page, index, { buttons: [[buttonIndex, value]] });
}

/** Rewrites one axis of the pad at the DOM index named; the same `'nan'` sentinel as `setButton`. */
async function setAxis(page, index, axisIndex, value) {
  await mutate(page, index, { axes: [[axisIndex, value]] });
}

/**
 * Rewrites several readings of one pad in one mutation, so one poll diffs the whole change: the
 * events it publishes are the events of one frame, stamped with one state revision.
 */
async function mutate(page, index, changes) {
  await page.evaluate(([padIndex, next]) => window.__kadreGamepadStub.mutate(padIndex, next), [index, changes]);
}

/** Waits for one real animation frame of the page. */
async function nextFrame(page) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => resolve())));
}

/**
 * Settles the page without touching its animation-frame counter: the teardown sentinel counts every
 * registration the page makes — whoever asks — so the waits that must not register one ride
 * `setTimeout` instead of the `nextFrame` probe.
 */
async function settleQuietly(page) {
  for (let round = 0; round < 2; round += 1) {
    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 100)));
  }
}

/** The journal after one more entry: the journal a spec reads may be empty before its first entry. */
function grown(before, entry) {
  return before ? `${before};${entry}` : entry;
}

/**
 * The exact snapshot cell one standard-mapping pad must publish: the descriptor is the standard
 * layout the pad's own mapping word named — the seventeen buttons and four axes in DOM order — the
 * pad's id is the name, and the connection, routing, controls, effects capability and revision are
 * the facts the pad carries at the moment the spec reads it.
 */
function standardPadCell({
  name = 'Kadre Standard Pad',
  connection = 'connected',
  routing = 'routed',
  buttons = STANDARD_BUTTON_CODES.map((code) => button(code, 0)),
  axes = STANDARD_AXIS_CODES.map((code) => axis(code, 0)),
  effects = 'supported[kinds=DualRumble]',
  revision,
} = {}) {
  return (
    `descriptor=name=${name}:mapping=standard` +
    `:buttons=[${STANDARD_BUTTON_CODES.join(',')}]` +
    ':axes=[leftX,leftY,rightX,rightY]' +
    `:connection=${connection}:routing=${routing}` +
    `:controls=buttons=[${buttons.join(',')}]:axes=[${axes.join(',')}]` +
    `:effects=${effects}:rev=${revision}`
  );
}

test('web-gamepad-empty-inventory-honest', async ({ page }) => {
  await installGamepadStub(page);
  await loadScenario(page, 'devices');
  const host = page.locator('[data-kadre-host="devices"]');

  // The gate's observable, for a page whose poll reports nothing: the manager states the browser's
  // own inventory — enumerated, honest, empty. Never a fabricated device, never a fabricated pad,
  // never an Unavailable dressed up as an answer.
  await expect(host).toHaveAttribute('data-kadre-devices-manager', 'rev=0:enumerated:devices=0:gamepads=0');
  await expect(host).toHaveAttribute('data-kadre-devices-events', '');

  // The empty answer is held over time: whatever polls pass over a page with no pads, nothing is
  // invented for it — the inventory stays exactly what the browser reported, at revision 0.
  await settleQuietly(page);
  await expect(host).toHaveAttribute('data-kadre-devices-manager', 'rev=0:enumerated:devices=0:gamepads=0');
  await expect(host).toHaveAttribute('data-kadre-devices-events', '');
});

test('web-gamepad-connect-added', async ({ page }) => {
  await installGamepadStub(page);
  await loadScenario(page, 'devices');
  const host = page.locator('[data-kadre-host="devices"]');
  await host.focus();
  await expect(host).toHaveAttribute('data-kadre-devices-manager', 'rev=0:enumerated:devices=0:gamepads=0');
  await expect(host).toHaveAttribute('data-kadre-devices-events', '');

  // A real connection of the page's own pad: one GamepadAdded at the next manager revision, and
  // the descriptor is exactly the standard layout the pad's mapping word named — seventeen buttons
  // and four axes in DOM order, with the pad's id as the display name. The controls are the
  // reading the connection's own poll reported, and the effects capability is the frozen word of
  // the actuator that connection offered.
  await setPads(page, [standardPad()]);
  await expect(host).toHaveAttribute('data-kadre-devices-manager', 'rev=1:enumerated:devices=0:gamepads=1');
  await expect(host).toHaveAttribute('data-kadre-devices-events', 'added:g0@1');
  await expect(host).toHaveAttribute('data-kadre-gamepad-0', standardPadCell({ revision: 0 }));

  // A second pad the browser promises nothing about: the descriptor is the pad's own shape — every
  // control a native code, at whatever count the pad reports — and the connection publishes
  // without a crash, exactly one event at exactly one more revision.
  await setPads(page, [standardPad(), nativePad()]);
  await expect(host).toHaveAttribute('data-kadre-devices-manager', 'rev=2:enumerated:devices=0:gamepads=2');
  await expect(host).toHaveAttribute(
    'data-kadre-devices-events',
    'added:g0@1;added:g1@2',
  );
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-1',
    'descriptor=name=Vendor Pad:mapping=native' +
      ':buttons=[other:button-0,other:button-1,other:button-2]' +
      ':axes=[other:axis-0,other:axis-1]' +
      ':connection=connected:routing=routed' +
      ':controls=buttons=[other:button-0=0,other:button-1=0,other:button-2=0]' +
      ':axes=[other:axis-0=0,other:axis-1=0]' +
      ':effects=supported[kinds=DualRumble]:rev=0',
  );
});

test('web-gamepad-state-poll', async ({ page }) => {
  await installGamepadStub(page);
  await loadScenario(page, 'devices');
  const host = page.locator('[data-kadre-host="devices"]');
  await host.focus();
  await setPads(page, [standardPad()]);
  await expect(host).toHaveAttribute('data-kadre-devices-events', 'added:g0@1');
  await expect(host).toHaveAttribute('data-kadre-gamepad-0-events', '');
  await settleQuietly(page);
  const journal = await host.getAttribute('data-kadre-gamepad-0-events');

  // A hostile poll that changes nothing canonically publishes nothing: readings the
  // canonicalization maps onto the already-published state — a non-finite button, a non-finite
  // axis, both canonical to the neutral zero the snapshot already carries — are no-ops, whatever
  // the raw readings did between the polls.
  await mutate(page, 0, { buttons: [[5, 'nan']], axes: [[3, 'nan']] });
  await settleQuietly(page);
  await expect(host).toHaveAttribute('data-kadre-gamepad-0-events', journal);

  // One real mutation of one button and one axis, in one frame: exactly one ButtonChanged and
  // exactly one AxisChanged after the next poll, both stamped with the one state revision the
  // change published — and the snapshot cell carries the changed reading at that revision, no more.
  await mutate(page, 0, { buttons: [[2, 0.75]], axes: [[1, 0.5]] });
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-0-events',
    grown(journal, 'button:west=0.75*:rev=1;axis:leftY=0.5:rev=1'),
  );
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-0',
    standardPadCell({
      buttons: STANDARD_BUTTON_CODES.map((code, control) => button(code, control === 2 ? 0.75 : 0, control === 2)),
      axes: STANDARD_AXIS_CODES.map((code, control) => axis(code, control === 1 ? 0.5 : 0)),
      revision: 1,
    }),
  );
});

test('web-gamepad-disconnect-neutral', async ({ page }) => {
  await installGamepadStub(page);
  await loadScenario(page, 'devices');
  const host = page.locator('[data-kadre-host="devices"]');
  await host.focus();
  await setPads(page, [
    standardPad({
      buttons: STANDARD_BUTTON_CODES.map((code, control) => (control === 0 ? 1 : 0)),
      axes: [-0.5, 0, 0, 0],
    }),
  ]);
  await expect(host).toHaveAttribute('data-kadre-devices-events', 'added:g0@1');
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-0',
    standardPadCell({
      buttons: STANDARD_BUTTON_CODES.map((code, control) => button(code, control === 0 ? 1 : 0, control === 0)),
      axes: STANDARD_AXIS_CODES.map((code, control) => axis(code, control === 0 ? -0.5 : 0)),
      revision: 0,
    }),
  );

  // The pad vanishes from the browser's own poll: one GamepadRemoved at the next manager
  // revision, the inventory shrinks to what the browser still reports — and the pad's own
  // snapshot dies honestly: disconnected, suspended, and the neutral reading of the descriptor it
  // kept, whatever the last reading was.
  await setPads(page, [null]);
  await expect(host).toHaveAttribute('data-kadre-devices-manager', 'rev=2:enumerated:devices=0:gamepads=0');
  await expect(host).toHaveAttribute('data-kadre-devices-events', 'added:g0@1;removed:g0@2');
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-0',
    standardPadCell({
      connection: 'disconnected',
      routing: 'suspended',
      revision: 1,
    }),
  );
});

test('web-gamepad-routing-suspended-neutral', async ({ page }) => {
  await installGamepadStub(page);
  await loadScenario(page, 'devices');
  const host = page.locator('[data-kadre-host="devices"]');
  await host.focus();
  await setPads(page, [
    standardPad({
      buttons: STANDARD_BUTTON_CODES.map((code, control) => (control === 0 ? 1 : 0)),
      axes: [-0.5, 0, 0, 0],
    }),
  ]);
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-0',
    standardPadCell({
      buttons: STANDARD_BUTTON_CODES.map((code, control) => button(code, control === 0 ? 1 : 0, control === 0)),
      axes: STANDARD_AXIS_CODES.map((code, control) => axis(code, control === 0 ? -0.5 : 0)),
      revision: 0,
    }),
  );
  await settleQuietly(page);

  // The page leaves the foreground — the browsing context's own visibility, observed the way the
  // lifecycle observes it — and the routing follows: the pad stays connected, but its projection
  // is suspended and its controls go neutral, one RoutingSuspended at the state revision the
  // suspension published. The manager inventory does not move: routing is the pad's own fact.
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await expect(host).toHaveAttribute('data-kadre-gamepad-0-events', 'suspended:rev=1');
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-0',
    standardPadCell({ routing: 'suspended', revision: 1 }),
  );
  await expect(host).toHaveAttribute('data-kadre-devices-manager', 'rev=1:enumerated:devices=0:gamepads=1');

  // The arbitration is real: while the projection is suspended, even a real reading change is
  // recorded for later and published to nobody — the suspended session never sees a control its
  // routing refuses to route.
  await setAxis(page, 0, 1, 0.5);
  await settleQuietly(page);
  await expect(host).toHaveAttribute('data-kadre-gamepad-0-events', 'suspended:rev=1');
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-0',
    standardPadCell({ routing: 'suspended', revision: 1 }),
  );

  // The page returns: one RoutingResumed, and the projection carries the reading the pad reads
  // now — the values recorded while nobody was watching, not a replay of the suspended past.
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await expect(host).toHaveAttribute('data-kadre-gamepad-0-events', 'suspended:rev=1;resumed:rev=2');
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-0',
    standardPadCell({
      buttons: STANDARD_BUTTON_CODES.map((code, control) => button(code, control === 0 ? 1 : 0, control === 0)),
      axes: STANDARD_AXIS_CODES.map((code, control) => axis(code, control === 0 ? -0.5 : control === 1 ? 0.5 : 0)),
      revision: 2,
    }),
  );
});

test('web-gamepad-session-close-quiet', async ({ page }) => {
  await installGamepadStub(page);
  await loadScenario(page, 'devices');
  const host = page.locator('[data-kadre-host="devices"]');
  const body = page.locator('body');
  await host.focus();
  await setPads(page, [standardPad()]);
  await expect(host).toHaveAttribute('data-kadre-devices-events', 'added:g0@1');

  // A real close of the session: the manager dies with it, and the terminal state is the barrier
  // every later read is measured from.
  await command(page, 'kadre-stop-devices');
  await expect(body).toHaveAttribute('data-kadre-devices-session', 'terminated');
  await nextFrame(page);

  // The quiet window: the journal, the inventory and the page's animation-frame counter are read
  // before the pokes, so whatever the pokes move is named by the tail.
  const journal = await host.getAttribute('data-kadre-devices-events');
  const manager = await host.getAttribute('data-kadre-devices-manager');
  const frames = await host.getAttribute('data-kadre-devices-raf');

  await setPads(page, [null, nativePad()]);
  await setButton(page, 1, 1, 1);
  await setAxis(page, 1, 0, -1);
  await settleQuietly(page);

  // The session is gone: no further lifecycle event, no republished inventory, and not one more
  // animation frame registered — the hub's poll died with the last port it served, and a page
  // that keeps mutating its pads is polling a broker that is no longer listening.
  await expect(host).toHaveAttribute('data-kadre-devices-events', journal);
  await expect(host).toHaveAttribute('data-kadre-devices-manager', manager);
  await expect(host).toHaveAttribute('data-kadre-devices-raf', frames);
});

test('web-gamepad-no-phantom', async ({ page }) => {
  await installGamepadStub(page);
  await loadScenario(page, 'devices');
  const host = page.locator('[data-kadre-host="devices"]');
  await host.focus();

  // The browser's own poll shape: holes where pads are absent. The pad that exists is enumerated
  // at its own index with its own facts; the hole invents nothing — no phantom pad for the absent
  // index, no renumbering of the pad that survived it.
  await setPads(page, [null, standardPad({ index: 1, id: 'Hole Companion Pad' })]);
  await expect(host).toHaveAttribute('data-kadre-devices-manager', 'rev=1:enumerated:devices=0:gamepads=1');
  await expect(host).toHaveAttribute('data-kadre-devices-events', 'added:g0@1');
  await expect(host).toHaveAttribute('data-kadre-gamepad-0', standardPadCell({ name: 'Hole Companion Pad', revision: 0 }));
});
