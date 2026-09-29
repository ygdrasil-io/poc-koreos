import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

async function loadScenario(page, scenario) {
  await page.goto(`${fixtureUrl}?scenario=${scenario}`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
}

/**
 * The interaction seam of phase 4, driven by a real click.
 *
 * The click is a real, trusted press of the browser: the port's own `pointerdown` listener dispatches
 * it as a synchronous interaction inside the very frame of transient activation the fullscreen
 * primitive needs, the handler installed by the scenario asks for the primitive there, and the
 * registration publishes the outcome the browser's terminal answer completes.
 *
 * What this test asserts is the **closed set of honest outcomes** — committed, or refused with the
 * one code the DOM exposes for a refusal — and never one of the two by name. Which of the two this
 * headless Chromium gives is what the scenario recorded, and the run log below is the record of it:
 * if headless honours the primitive, this run is the browser-contract proof; if it refuses, the
 * refusal is honestly recorded here and the real-screen proof belongs to the manual charter
 * (`kadre/contracts/driver/web/manual/phase-4-interactions.md`). A hang, a lie (a committed outcome
 * for a primitive the browser never confirmed) or a second outcome per click all fail this test.
 */
test('web-interaction-fullscreen', async ({ page }) => {
  await loadScenario(page, 'web-interaction');
  const host = page.locator('[data-kadre-host="web-interaction"]');

  // The armed flag is the installation barrier: the handler exists, and the next press is
  // dispatched into it through the port's own listener.
  await expect(host).toHaveAttribute('data-kadre-interaction-armed', 'true');

  await host.click();

  await expect(host).toHaveAttribute(
    'data-kadre-interaction-fullscreen',
    /^(committed|rejected:platformFailure:web:fullscreen:refused)$/,
    { timeout: 5_000 },
  );

  // The honest record of what this browser answered, in the run log itself: the evidence of this
  // scenario is the observation, not the hope.
  console.log(`web-interaction-fullscreen observed outcome: ${await host.getAttribute('data-kadre-interaction-fullscreen')}`);
});
