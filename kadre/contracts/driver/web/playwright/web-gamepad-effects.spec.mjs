import { expect, test } from '@playwright/test';
import { installGamepadStub, standardPad } from './gamepad-stub.mjs';

/**
 * BCK-009 — the web gamepad effects and preconditions contract.
 *
 * The pads these scenarios see are the synthetic source `gamepad-stub.mjs` installs (the D10
 * precedent: Chromium cannot inject a real gamepad, so the harness patches the navigator's poll the
 * hub reads every animation frame, and the pad's `vibrationActuator` records every `playEffect`/
 * `reset` call it receives, mirroring the WebIDL dictionary shape the real Chromium reads). What the
 * specs prove is what the runtime admits onto that actuator and what it refuses before any actuator
 * call — the browser's own primitive is the boundary of every claim. The real-device privacy gate
 * (pads invisible until user activation) is manual-charter material and is claimed nowhere here.
 *
 * The insecure-context scenario runs only in the `chromium-insecure` project, whose browser is
 * launched with `--host-resolver-rules=MAP insecure.kadre.invalid 127.0.0.1` and navigates to the
 * same served page over plain http on a non-localhost name: the browsing context itself is then not
 * a secure context, which is the precondition the effects capability is probed against.
 */

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

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

/** Replaces the synthetic source's pad array, holes and all, the way a browser's poll would move. */
async function setPads(page, specs) {
  await page.evaluate((pads) => window.__kadreGamepadStub.setPads(pads), specs);
}

/** The actuator calls the pads' `vibrationActuator` received so far, in order. */
async function actuatorCalls(page) {
  return page.evaluate(() => window.__kadreGamepadStub.calls);
}

/** The prompting APIs the page touched so far — the record the no-implicit-prompt sentinel reads. */
async function prompts(page) {
  return page.evaluate(() => window.__kadreGamepadStub.prompts);
}

/**
 * Attaches the effects scenario with one standard-mapping pad already discovered and routed: the
 * host is focused, so the lifecycle is foreground-active and the projection is the pad's own.
 */
async function loadWithStandardPad(page) {
  await installGamepadStub(page);
  await loadScenario(page, 'gamepad-effects');
  const host = page.locator('[data-kadre-host="gamepad-effects"]');
  await host.focus();
  await setPads(page, [standardPad()]);
  await expect(host).toHaveAttribute('data-kadre-devices-events', 'added:g0@1');
  await expect(host).toHaveAttribute(
    'data-kadre-gamepad-0',
    /:effects=supported\[kinds=DualRumble\]:rev=0$/,
  );
  return host;
}

test('web-gamepad-effect-dual-rumble', async ({ page }) => {
  const host = await loadWithStandardPad(page);
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect', 'none');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect-result', 'none');

  // The advertised kind is admitted: the launch is the pad's own actuator receiving the mapped
  // effect as the browser reads it — the `dual-rumble` type with the `effectParameters` dictionary
  // carrying the requested duration and magnitudes, and no member a dual-rumble does not state.
  await command(page, 'kadre-gamepad-play-dual-rumble');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect-result', 'success');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect', 'playing');
  expect(await actuatorCalls(page)).toEqual([
    {
      kind: 'playEffect',
      index: 0,
      type: 'dual-rumble',
      duration: 5000,
      strongMagnitude: 0.75,
      weakMagnitude: 0.25,
      keys: ['duration', 'strongMagnitude', 'weakMagnitude'],
    },
  ]);
});

test('web-gamepad-effect-stop', async ({ page }) => {
  const host = await loadWithStandardPad(page);
  await command(page, 'kadre-gamepad-play-dual-rumble');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect-result', 'success');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect', 'playing');

  // The stop is the consumer's own: the session is asked once, the actuator is reset exactly once,
  // and the terminal outcome is the one the stop requested. No relaunch, no second reset.
  await command(page, 'kadre-gamepad-stop-effect');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect', 'terminated:stopped:requested');
  expect(await actuatorCalls(page)).toEqual([
    expect.objectContaining({ kind: 'playEffect', type: 'dual-rumble' }),
    { kind: 'reset', index: 0 },
  ]);

  // The pad then leaves the browser's own poll: its projection ends suspended, and an effect on it
  // is refused before anything reaches the actuator — a pad whose routing is gone starts nothing,
  // and the refused launch leaves the actuator exactly as the stop left it.
  await setPads(page, [null]);
  await expect(host).toHaveAttribute('data-kadre-gamepad-0', /:connection=disconnected:routing=suspended/);
  await command(page, 'kadre-gamepad-play-dual-rumble');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect-result', 'failure:closed:gamepad');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect', 'terminated:stopped:requested');
  expect(await actuatorCalls(page)).toEqual([
    expect.objectContaining({ kind: 'playEffect', type: 'dual-rumble' }),
    { kind: 'reset', index: 0 },
  ]);
});

test('web-gamepad-effect-unsupported-kind', async ({ page }) => {
  const host = await loadWithStandardPad(page);

  // The kinds the actuator never advertised are refused at admission, before any actuator call:
  // `LocalizedHaptic` has no browser primitive and is never advertised by this backend, and a
  // `trigger-rumble` the actuator's own declaration did not name is refused exactly the same way.
  await command(page, 'kadre-gamepad-play-localized');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect-result', 'failure:invalidRequest:effect');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect', 'none');
  await command(page, 'kadre-gamepad-play-trigger');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect-result', 'failure:invalidRequest:effect');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect', 'none');

  // The actuator heard none of it: two refusals, zero calls of any kind.
  expect(await actuatorCalls(page)).toEqual([]);
});

test('web-gamepad-effect-insecure-context', async ({ page }) => {
  await installGamepadStub(page);
  await page.goto(insecureScenarioUrl('gamepad-effects'));
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
  // Fixture sanity: this page really is the insecure browsing context the scenario names.
  expect(await page.evaluate(() => window.isSecureContext)).toBe(false);
  const host = page.locator('[data-kadre-host="gamepad-effects"]');
  await host.focus();
  await setPads(page, [standardPad()]);

  // The inventory needs no secure context: the pad is discovered, enumerated, and described
  // exactly as it would be anywhere — one GamepadAdded, the standard descriptor.
  await expect(host).toHaveAttribute('data-kadre-devices-manager', 'rev=1:enumerated:devices=0:gamepads=1');
  await expect(host).toHaveAttribute('data-kadre-devices-events', 'added:g0@1');

  // The effects capability is the honest unsupported of this context: no actuator is probed, no
  // effect is admitted — the GamepadEffect operation failure is the whole public claim.
  await expect(host).toHaveAttribute('data-kadre-gamepad-0', /:effects=unsupported:gamepadeffect:/);
  await command(page, 'kadre-gamepad-play-dual-rumble');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect-result', 'failure:unsupported:gamepadeffect');
  await expect(host).toHaveAttribute('data-kadre-gamepad-effect', 'none');

  // The refusal is structural, not a dialog the page dodged: no prompting API was ever touched,
  // and the actuator heard nothing.
  expect(await prompts(page)).toEqual([]);
  expect(await actuatorCalls(page)).toEqual([]);
});

test('web-gamepad-raw-input-unsupported', async ({ page }) => {
  await installGamepadStub(page);
  await loadScenario(page, 'gamepad-effects');
  const host = page.locator('[data-kadre-host="gamepad-effects"]');
  await host.focus();

  // The capability the session publishes says what this target can give: raw input is not the
  // browser's to hand over, and the readback names the operation that is refused.
  await expect(host).toHaveAttribute('data-kadre-input-caps', /rawInput=unsupported:rawinputaccess/);

  // The refused request registers nothing: the page's own listener counter — every EventTarget
  // registration, whoever asks — is read before and after the request, and the delta is zero. A
  // raw channel that installed itself on request would move it.
  const listenersBefore = await page.evaluate(() => window.__kadreGamepadStub.listeners);
  await command(page, 'kadre-gamepad-raw-input');
  await expect(host).toHaveAttribute('data-kadre-gamepad-raw', 'unsupported:rawinputaccess');
  expect(await page.evaluate(() => window.__kadreGamepadStub.listeners)).toBe(listenersBefore);
});
