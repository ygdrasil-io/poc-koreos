import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

test('existing host attaches through the public Kadre API', async ({ page }) => {
  await page.goto(fixtureUrl);

  await expect(page.locator('[data-kadre-state="running"]')).toHaveCount(1);
  await expect(page.locator('[data-kadre-created]')).toHaveCount(0);

  await page.locator('[data-kadre-state="running"]').dispatchEvent('kadre-phase0-stop');
  await expect(page.locator('[data-kadre-state="stopped"]')).toHaveCount(1);
});
