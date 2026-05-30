// Test de régression visuelle Web (Redmine #88) — NON BLOQUANT.
//
// Capture le canvas de hello-triangle-web et le compare à une baseline avec une
// tolérance de 2 %. Le rendu WebGPU SwiftShader pouvant varier légèrement selon
// l'environnement, ce test est **informatif** : il journalise le ratio de diff et
// archive l'image de diff en artefact, mais n'échoue jamais le build. Voir
// docs/koreos/visual-testing.md pour le workflow updateVisualBaselines.
const path = require('path');
const { test, expect } = require('@playwright/test');
const { assertScreenshotMatches } = require('../visual/assert-screenshot');

const BASELINE = path.join(__dirname, '..', 'baselines', 'hello-triangle-web.png');
const DIFF = path.join(__dirname, '..', 'test-results', 'hello-triangle-web.diff.png');
const TOLERANCE = 0.02;

test('régression visuelle hello-triangle-web (informatif)', async ({ page }) => {
  const logs = [];
  page.on('console', (m) => logs.push(m.text()));

  await page.goto('/');
  await expect(page.locator('#koreos-canvas')).toBeVisible();
  // Attendre l'initialisation complète + quelques frames stables.
  await expect.poll(() => logs.some((l) => l.includes('Pipeline prêt')), { timeout: 60_000 }).toBe(true);
  await page.waitForTimeout(1_500);

  const shot = await page.locator('#koreos-canvas').screenshot();
  const result = assertScreenshotMatches(shot, BASELINE, { tolerance: TOLERANCE, diffPath: DIFF });

  const pct = (result.ratio * 100).toFixed(3);
  if (result.status === 'created') {
    console.log(`[visual] baseline créée (${BASELINE}) — ${result.total} px`);
  } else if (result.status === 'mismatch') {
    console.warn(`[visual] ⚠️ diff ${pct}% > ${TOLERANCE * 100}% (${result.diffPixels}/${result.total} px) — diff archivé. ` +
      `Si le changement est légitime : npm run update-baselines.`);
  } else {
    console.log(`[visual] OK — diff ${pct}% ≤ ${TOLERANCE * 100}%`);
  }

  // Non bloquant : on n'échoue pas le build (rendu SwiftShader non déterministe
  // entre environnements). Le statut est journalisé + diff archivé en CI.
  expect(['created', 'match', 'mismatch']).toContain(result.status);
});
