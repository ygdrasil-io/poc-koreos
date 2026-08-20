// Deterministic scripted Pong E2E test (Web).
//
// The blocking contract observes the state published by the renderer only
// after CanvasSurface.present(). Canvas screenshots, video, trace, and pixel
// diffs remain useful diagnostics, but a screenshot can sample a partial WebGPU
// frame and is therefore not an animation oracle.
const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');
const { PNG } = require('pngjs');
const pixelmatch = require('pixelmatch').default || require('pixelmatch');

const PRESENTED_FRAME_ATTRIBUTE = 'data-kadre-pong-presented-frame';
const MIN_FRAME_ADVANCE = 5;
const WEB_TARGET = process.env.PONG_WEB_TARGET || 'js';
const RESULTS = path.join(__dirname, '..', 'test-results', WEB_TARGET);

function pixelDiffRatio(pngBuf1, pngBuf2) {
  const a = PNG.sync.read(pngBuf1);
  const b = PNG.sync.read(pngBuf2);
  if (a.width !== b.width || a.height !== b.height) return 1;
  const diff = new PNG({ width: a.width, height: a.height });
  const n = pixelmatch(a.data, b.data, diff.data, a.width, a.height, { threshold: 0.1 });
  return n / (a.width * a.height);
}

function hasKeyLog(logs, keyCode, state) {
  return logs.some(
    (line) =>
      line.includes(`key ${keyCode} ${state}`) ||
      line.includes(`key Code(code=${keyCode}) ${state}`),
  );
}

async function readPresentedFrame(canvas) {
  const raw = await canvas.getAttribute(PRESENTED_FRAME_ATTRIBUTE);
  return raw === null ? null : JSON.parse(raw);
}

test(`Pong Web (${WEB_TARGET}) — presented animation + keyboard + visual diagnostics`, async ({ page }) => {
  fs.mkdirSync(RESULTS, { recursive: true });

  const logs = [];
  const errors = [];
  page.on('console', (message) => logs.push(message.text()));
  page.on('pageerror', (error) => errors.push(error.message));

  await page.goto('/');
  const canvas = page.locator('#kadre-canvas');
  await expect(canvas).toBeVisible();
  await expect
    .poll(() => logs.some((line) => line.includes('Pipeline ready')), { timeout: 60_000 })
    .toBe(true);

  const acquisitionFailure = logs.find((line) => line.includes('Acquisition failed'));
  expect(acquisitionFailure, `WebGPU acquisition failure: ${acquisitionFailure}`).toBeUndefined();

  // A valid snapshot proves that at least one complete renderer submission was
  // presented. The baseline bundle intentionally fails here during the TDD RED.
  await expect
    .poll(() => readPresentedFrame(canvas), {
      message: `Expected #kadre-canvas to expose ${PRESENTED_FRAME_ATTRIBUTE} after present()`,
    })
    .not.toBeNull();

  const animationStart = await readPresentedFrame(canvas);
  expect(animationStart).toMatchObject({
    frame: expect.any(Number),
    ballX: expect.any(Number),
    ballY: expect.any(Number),
    playerY: expect.any(Number),
    aiY: expect.any(Number),
  });
  const frame1 = await canvas.screenshot();
  fs.writeFileSync(path.join(RESULTS, 'frame1-animation-start.png'), frame1);

  await expect
    .poll(async () => {
      const current = await readPresentedFrame(canvas);
      return current !== null &&
        current.frame >= animationStart.frame + MIN_FRAME_ADVANCE &&
        current.ballX !== animationStart.ballX &&
        current.ballY !== animationStart.ballY;
    }, { message: `Expected ${MIN_FRAME_ADVANCE} newer presented frames with a moving ball` })
    .toBe(true);

  const animationEnd = await readPresentedFrame(canvas);
  const frame2 = await canvas.screenshot();
  fs.writeFileSync(path.join(RESULTS, 'frame2-animation-end.png'), frame2);
  const animationDiff = pixelDiffRatio(frame1, frame2);
  console.log(`[scenario] diagnostic animation pixel diff = ${(animationDiff * 100).toFixed(2)}%`);

  await canvas.click();

  const downStart = await readPresentedFrame(canvas);
  await page.keyboard.down('ArrowDown');
  await expect
    .poll(async () => {
      const current = await readPresentedFrame(canvas);
      return current !== null &&
        current.frame > downStart.frame &&
        current.playerY > downStart.playerY;
    }, { message: 'Expected ArrowDown to increase playerY in a presented frame' })
    .toBe(true);
  await page.keyboard.up('ArrowDown');

  await expect
    .poll(() => hasKeyLog(logs, 'ArrowDown', 'Pressed'))
    .toBe(true);
  await expect
    .poll(() => hasKeyLog(logs, 'ArrowDown', 'Released'))
    .toBe(true);

  const downEnd = await readPresentedFrame(canvas);
  const frame3 = await canvas.screenshot();
  fs.writeFileSync(path.join(RESULTS, 'frame3-after-arrowdown.png'), frame3);

  await page.keyboard.down('ArrowUp');
  await expect
    .poll(async () => {
      const current = await readPresentedFrame(canvas);
      return current !== null &&
        current.frame > downEnd.frame &&
        current.playerY < downEnd.playerY;
    }, { message: 'Expected ArrowUp to decrease playerY in a presented frame' })
    .toBe(true);
  await page.keyboard.up('ArrowUp');

  await expect
    .poll(() => hasKeyLog(logs, 'ArrowUp', 'Pressed'))
    .toBe(true);
  await expect
    .poll(() => hasKeyLog(logs, 'ArrowUp', 'Released'))
    .toBe(true);

  const upEnd = await readPresentedFrame(canvas);
  const frame4 = await canvas.screenshot();
  fs.writeFileSync(path.join(RESULTS, 'frame4-after-arrowup.png'), frame4);
  const inputDiff = pixelDiffRatio(frame3, frame4);
  console.log(`[scenario] diagnostic keyboard pixel diff = ${(inputDiff * 100).toFixed(2)}%`);

  expect(errors, `JS errors during scenario: ${errors.join(' | ')}`).toEqual([]);

  const keyLogs = logs.filter((line) => line.includes('[pong-web] key')).length;
  const summary = [
    `### Pong E2E (${WEB_TARGET}) — deterministic presented-frame scenario`,
    '',
    '- ✅ wgpu4k Web pipeline initialized',
    `- ✅ Presented frame counter advanced from ${animationStart.frame} to ${animationEnd.frame}`,
    `- ✅ Presented ball state changed (${animationStart.ballX}, ${animationStart.ballY}) → (${animationEnd.ballX}, ${animationEnd.ballY})`,
    `- ✅ ArrowDown increased presented playerY: ${downStart.playerY} → ${downEnd.playerY}`,
    `- ✅ ArrowUp decreased presented playerY: ${downEnd.playerY} → ${upEnd.playerY}`,
    `- ✅ Keyboard inputs routed to handler (${keyLogs} \`[pong-web] key …\` events received)`,
    '- ✅ No JS errors',
    '',
    `Diagnostics only: animation pixel diff **${(animationDiff * 100).toFixed(2)}%**; keyboard pixel diff **${(inputDiff * 100).toFixed(2)}%**.`,
    '',
    '_Frames + .webm video + Playwright trace in run artifacts._',
    '',
  ].join('\n');
  fs.writeFileSync(path.join(RESULTS, 'scenario-summary.md'), summary);
});
