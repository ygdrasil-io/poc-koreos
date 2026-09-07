import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

test('existing host attaches through the public Kadre API', async ({ page }) => {
  await page.goto(fixtureUrl);

  const host = page.locator('[data-kadre-state="running"]');
  await expect(host).toHaveCount(1);
  const baseline = await host.getAttribute('data-kadre-dom-baseline');
  expect(baseline).not.toBeNull();
  await expect(host).toHaveAttribute('data-kadre-dom-count', baseline);

  await host.dispatchEvent('kadre-phase0-stop');
  await expect(page.locator('[data-kadre-state="stopped"]')).toHaveCount(1);
});
