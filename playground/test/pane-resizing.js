/**
 * Browser checks for the playground's existing draggable pane divider.
 * Covers pointer dragging/capture, minimum widths, viewport resizing,
 * keyboard controls, reset, saved splits, hover cleanup, and stacked mobile layout.
 *
 * From the repository root, start `cd playground && bb dev` in one terminal,
 * then run `node playground/test/pane-resizing.js` in another. From playground/
 * run `node test/pane-resizing.js`. Requires the repo's npm dependencies and
 * Playwright Chromium or installed Google Chrome. PLAYGROUND_URL overrides
 * http://localhost:5173/; PLAYWRIGHT_CHANNEL can select a browser explicitly.
 * Runs in a fresh headless browser session and exits nonzero on failure.
 */
const assert = require('node:assert/strict');
const { existsSync } = require('node:fs');
const { chromium } = require('playwright');

async function main() {
  const channel =
    process.env.PLAYWRIGHT_CHANNEL ||
    (existsSync(chromium.executablePath()) ? 'chromium' : 'chrome');
  const browser = await chromium.launch({ headless: true, channel });
  const page = await browser.newPage({
    viewport: { width: 1200, height: 800 },
  });
  const dimensions = () =>
    page.evaluate(() => {
      const bounds = (id) =>
        document.getElementById(id).getBoundingClientRect();
      const main = bounds('main'),
        editor = bounds('editor'),
        sidebar = bounds('sidebar');
      return {
        minimum: parseFloat(
          getComputedStyle(document.getElementById('main')).getPropertyValue(
            '--pane-min-width'
          )
        ),
        width: main.width,
        left: main.left,
        source: editor.width,
        sidebar: sidebar.width,
        ratio: editor.width / main.width,
        stacked: sidebar.top >= editor.bottom,
        overflow: document.documentElement.scrollWidth > window.innerWidth,
      };
    });
  const close = (actual, expected, message) =>
    assert.ok(
      Math.abs(actual - expected) < 1,
      `${message}: ${actual} versus ${expected}`
    );
  const dragTo = async (x) => {
    const bounds = await page.locator('#draggable-divider').boundingBox();
    await page.mouse.move(bounds.x + 3, bounds.y + 80);
    await page.mouse.down();
    await page.mouse.move(x, bounds.y + 80, { steps: 5 });
    return bounds;
  };

  try {
    const url = new URL(process.env.PLAYGROUND_URL || 'http://localhost:5173/');
    url.searchParams.set('src', Buffer.from('(+ 1 2 3)').toString('base64'));
    await page.goto(url.href, { waitUntil: 'domcontentloaded' });
    await page.locator('#compiled-code .cm-content').waitFor();
    let layout = await dimensions();
    close(layout.ratio * 100, 50, 'Default split is equal');

    await dragTo(layout.left + layout.width * 0.65 + 3);
    layout = await dimensions();
    close(layout.ratio * 100, 65, 'Dragging the divider changes the split');
    assert.equal(
      await page
        .locator('#main')
        .evaluate((node) => node.classList.contains('pane-resizing')),
      true
    );
    assert.equal(
      await page
        .locator('#draggable-divider-line')
        .evaluate((node) => getComputedStyle(node).width),
      '1px'
    );
    await page.mouse.up();
    await page.waitForFunction(
      () => !document.querySelector('#main').classList.contains('pane-resizing')
    );
    console.log('PASS pointer dragging, capture, 1px line, and cleanup');

    await page.mouse.move(layout.left + 50, 80);
    await page.waitForTimeout(250); // Allow the divider's 200ms color transition.
    assert.equal(
      await page.locator('#draggable-divider-line').evaluate((node) => {
        const style = getComputedStyle(node);
        return (
          style.getPropertyValue('--c1').trim() ===
          style.getPropertyValue('--c2').trim()
        );
      }),
      true,
      'Divider hover styling clears after dragging and leaving the hit area'
    );
    console.log('PASS divider hover styling clears on pointer exit');

    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('#compiled-code .cm-content').waitFor();
    layout = await dimensions();
    close(layout.ratio * 100, 65, 'Split survives reload');
    await page.setViewportSize({ width: 1000, height: 800 });
    layout = await dimensions();
    close(
      layout.ratio * 100,
      65,
      'Split keeps its ratio when the window resizes'
    );
    console.log('PASS saved split and proportional window resizing');

    await page.locator('#draggable-divider').focus();
    await page.keyboard.press('ArrowLeft');
    close((await dimensions()).ratio * 100, 63, 'Arrow moves by 2%');
    await page.keyboard.press('Shift+ArrowRight');
    layout = await dimensions();
    close(
      layout.ratio * 100,
      Math.min(73, 100 - (layout.minimum / layout.width) * 100),
      'Shift+arrow moves by 10%, within the configured minimum'
    );
    await page.keyboard.press('Home');
    layout = await dimensions();
    close(layout.source, layout.minimum, 'Source minimum width');
    assert.equal(layout.overflow, false);
    await page.keyboard.press('ArrowRight');
    assert.ok(
      (await dimensions()).source > layout.minimum,
      'Arrow moves immediately away from the minimum'
    );
    await page.keyboard.press('End');
    layout = await dimensions();
    close(layout.sidebar, layout.minimum, 'Sidebar minimum width');
    assert.equal(layout.overflow, false);
    assert.equal(
      await page.evaluate(() => {
        const sidebar = document
          .querySelector('#sidebar')
          .getBoundingClientRect();
        return [...document.querySelectorAll('#sidebar-controls button')].every(
          (button) => {
            const bounds = button.getBoundingClientRect();
            return (
              bounds.width === 0 ||
              (bounds.left >= sidebar.left && bounds.right <= sidebar.right)
            );
          }
        );
      }),
      true,
      'Toolbar buttons remain inside the minimum-width sidebar'
    );
    console.log('PASS keyboard controls and usable minimum widths');

    await page
      .locator('#draggable-divider')
      .dblclick({ position: { x: 3, y: 80 } });
    close((await dimensions()).ratio * 100, 50, 'Double click resets split');
    assert.equal(
      await page.locator('#draggable-divider').getAttribute('aria-valuenow'),
      '50'
    );
    console.log('PASS double click reset and separator accessibility');

    await dragTo(50);
    layout = await dimensions();
    close(layout.source, layout.minimum, 'Drag clamps at source minimum');
    await page.waitForTimeout(250);
    assert.equal(
      await page.locator('#draggable-divider-line').evaluate((node) => {
        const style = getComputedStyle(node);
        return (
          style.getPropertyValue('--c1').trim() ===
          style.getPropertyValue('--c2').trim()
        );
      }),
      true,
      'Divider hover styling clears when the pointer leaves during a captured drag'
    );
    await page.mouse.move(1500, 100, { steps: 5 });
    close(
      (await dimensions()).sidebar,
      layout.minimum,
      'Capture keeps dragging outside the viewport'
    );
    await page.mouse.up();
    await page.waitForFunction(
      () => !document.querySelector('#main').classList.contains('pane-resizing')
    );
    console.log('PASS pointer capture across panes and outside the viewport');

    await page.setViewportSize({ width: 600, height: 800 });
    layout = await dimensions();
    assert.equal(layout.stacked, true);
    assert.equal(await page.locator('#draggable-divider').isVisible(), false);
    assert.equal(layout.overflow, false);
    await page.setViewportSize({ width: 1200, height: 800 });
    assert.equal(await page.locator('#draggable-divider').isVisible(), true);
    layout = await dimensions();
    assert.ok(layout.sidebar >= layout.minimum - 1);
    console.log('PASS stacked mobile layout and return to desktop');
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
