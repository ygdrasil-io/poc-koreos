const { expect } = require('@playwright/test');

const CONTENT_WIDTH = 800;
const CONTENT_HEIGHT = 600;
const BORDER_WIDTH = 2;
const FRAME_COUNT = 5;

async function expectStablePhysicalCanvas(page, logs) {
  const samples = await page.locator('#kadre-canvas').evaluate(
    (canvas, frameCount) => new Promise((resolve) => {
      const values = [];
      const capture = () => {
        const rect = canvas.getBoundingClientRect();
        values.push({
          rectWidth: rect.width,
          rectHeight: rect.height,
          clientWidth: canvas.clientWidth,
          clientHeight: canvas.clientHeight,
          backingWidth: canvas.width,
          backingHeight: canvas.height,
          dpr: window.devicePixelRatio,
        });
        if (values.length === frameCount) {
          resolve(values);
        } else {
          requestAnimationFrame(capture);
        }
      };
      requestAnimationFrame(capture);
    }),
    FRAME_COUNT,
  );

  expect(samples).toHaveLength(FRAME_COUNT);
  expect(samples.every((sample) => JSON.stringify(sample) === JSON.stringify(samples[0])),
    `canvas geometry did not converge within ${FRAME_COUNT} frames: ${JSON.stringify(samples)}`,
  ).toBe(true);

  const stable = samples[0];
  expect(stable.dpr).toBe(2);
  expect(stable.clientWidth).toBe(CONTENT_WIDTH);
  expect(stable.clientHeight).toBe(CONTENT_HEIGHT);
  expect(stable.rectWidth).toBe(CONTENT_WIDTH + 2 * BORDER_WIDTH);
  expect(stable.rectHeight).toBe(CONTENT_HEIGHT + 2 * BORDER_WIDTH);
  // Kadre's physical inner-size contract uses the client/rendering area and
  // excludes the border. The sample must accept that size once.
  expect(stable.backingWidth).toBe(stable.clientWidth * stable.dpr);
  expect(stable.backingHeight).toBe(stable.clientHeight * stable.dpr);

  expect(
    logs.some((line) => line.includes('Resized') &&
      line.includes(`${stable.backingWidth}×${stable.backingHeight}`) &&
      line.includes('physical px')),
    `missing physical resize acknowledgement in logs: ${logs.join(' | ')}`,
  ).toBe(true);

  return stable;
}

module.exports = { expectStablePhysicalCanvas };
