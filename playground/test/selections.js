/**
 * Browser regression checks for cursor selections in the Squint playground.
 * Drives the source and compiled CodeMirror panes with mouse and keyboard,
 * checks selection colors in light/dark mode and the shared CSS variables,
 * verifies selections clear on outside clicks/focus changes, and checks that
 * compiled code can be selected/copied while remaining read only.
 *
 * Run with the repo's npm dependencies installed and the playground running.
 * From the repository root, use two terminals:
 *   1. cd playground && bb dev
 *   2. node playground/test/selections.js
 * From playground/, the test command is: node test/selections.js
 *
 * The default target is http://localhost:5173/. For another port or server:
 *   PLAYGROUND_URL=http://localhost:5174/ node playground/test/selections.js
 * It uses headless Playwright Chromium when installed, otherwise Google Chrome.
 * Install Chromium from the repo root with: npx playwright install chromium
 * Choose a browser channel explicitly with PLAYWRIGHT_CHANNEL=chrome.
 *
 * Each run uses a fresh browser session and loads a small code fixture through
 * the URL's src parameter. PASS/FAIL lines identify each check; failures exit
 * with a nonzero status. Run this after changes to selection CSS or editor setup.
 */
const assert = require('node:assert/strict');
const { existsSync } = require('node:fs');
const { chromium } = require('playwright');

async function main() {
  const channel =
    process.env.PLAYWRIGHT_CHANNEL ||
    (existsSync(chromium.executablePath()) ? 'chromium' : 'chrome');
  const browser = await chromium.launch({ headless: true, channel });
  const page = await browser.newPage();
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
  const failures = [];
  const check = (name, actual, expected) => {
    try {
      assert.deepEqual(actual, expected);
      console.log(`PASS ${name}`);
    } catch {
      failures.push(name);
      console.error(
        `FAIL ${name}: ${JSON.stringify(actual)}; expected ${JSON.stringify(
          expected
        )}`
      );
    }
  };

  try {
    const code = '(def sample-value "selection check")\n(+ 1 2 3)';
    const url = new URL(process.env.PLAYGROUND_URL || 'http://localhost:5173/');
    url.searchParams.set('src', Buffer.from(code).toString('base64'));
    await page.goto(url.href, { waitUntil: 'domcontentloaded' });
    await page.locator('#compiled-code .cm-content').waitFor();

    await page.locator('#editor .cm-line').first().click();
    await page.keyboard.press('Home');
    await page.keyboard.press('Shift+End');
    await page.waitForFunction(() =>
      document.querySelector('#editor .cm-selectionBackground')
    );
    const sourceColor = await page
      .locator('#editor .cm-selectionBackground')
      .first()
      .evaluate((node) => getComputedStyle(node).backgroundColor);
    const lightForeground = await page
      .locator('#editor .cm-line')
      .first()
      .evaluate((node) => getComputedStyle(node, '::selection').color);
    check(
      'light mode uses dark selected text',
      lightForeground,
      'rgb(34, 34, 34)'
    );

    await page.locator('#compiled-code .cm-line').first().dblclick();
    const compiledColor = await page
      .locator('#compiled-code .cm-line')
      .first()
      .evaluate((node) => {
        const drawn = document.querySelector(
          '#compiled-code .cm-selectionBackground'
        );
        return drawn
          ? getComputedStyle(drawn).backgroundColor
          : getComputedStyle(node, '::selection').backgroundColor;
      });
    check(
      'both panes use the same selection color',
      sourceColor,
      compiledColor
    );
    check(
      'selecting compiled code clears the source selection',
      await page.evaluate(() =>
        globalThis.editor.state.selection.ranges.every((range) => range.empty)
      ),
      true
    );

    await page.locator('#repl-mode-desc').click();
    await page.evaluate(
      () =>
        new Promise((resolve) =>
          requestAnimationFrame(() => requestAnimationFrame(resolve))
        )
    );
    check(
      'clicking outside the editors clears the visible selection',
      await page.evaluate(
        () =>
          !document.querySelector('.cm-selectionBackground') &&
          !window.getSelection().toString()
      ),
      true
    );

    await page.evaluate(() => {
      document.documentElement.dataset.theme = 'dark';
    });
    await page.locator('#compiled-code .cm-line').first().dblclick();
    await page.waitForFunction(() =>
      document.querySelector('#compiled-code .cm-selectionBackground')
    );
    const darkBackground = await page
      .locator('#compiled-code .cm-selectionBackground')
      .first()
      .evaluate((node) => getComputedStyle(node).backgroundColor);
    check(
      'dark mode uses light selected text',
      await page
        .locator('#compiled-code .cm-line')
        .first()
        .evaluate((node) => getComputedStyle(node, '::selection').color),
      'rgb(255, 255, 255)'
    );
    const copiedText = await page.evaluate(() => {
      const view = globalThis.editor.constructor.findFromDOM(
        document.querySelector('#compiled-code .cm-editor')
      );
      return view.state.sliceDoc(
        view.state.selection.main.from,
        view.state.selection.main.to
      );
    });
    assert.ok(copiedText, 'Compiled code must have a real selection');
    await page.keyboard.press(
      process.platform === 'darwin' ? 'Meta+c' : 'Control+c'
    );
    check(
      'compiled selections can still be copied',
      await page.evaluate(() => navigator.clipboard.readText()),
      copiedText
    );
    const compiledText = await page
      .locator('#compiled-code .cm-content')
      .textContent();
    await page.keyboard.press('Delete');
    check(
      'compiled code remains read only',
      await page.locator('#compiled-code .cm-content').textContent(),
      compiledText
    );

    await page.locator('#editor .cm-line').first().click();
    await page.keyboard.press('Home');
    await page.keyboard.press('Shift+End');
    await page.waitForFunction(() =>
      document.querySelector('#editor .cm-selectionBackground')
    );
    check(
      'both panes use the same dark selection background',
      await page
        .locator('#editor .cm-selectionBackground')
        .first()
        .evaluate((node) => getComputedStyle(node).backgroundColor),
      darkBackground
    );
    check(
      'source selection text is light in dark mode',
      await page
        .locator('#editor .cm-line')
        .first()
        .evaluate((node) => getComputedStyle(node, '::selection').color),
      'rgb(255, 255, 255)'
    );
    check(
      'selecting source clears the compiled selection',
      await page.evaluate(() => {
        const view = globalThis.editor.constructor.findFromDOM(
          document.querySelector('#compiled-code .cm-editor')
        );
        return view.state.selection.ranges.every((range) => range.empty);
      }),
      true
    );
    await page.locator('#replCheckBox').focus();
    check(
      'moving focus outside collapses the source selection',
      await page.evaluate(() =>
        globalThis.editor.state.selection.ranges.every((range) => range.empty)
      ),
      true
    );

    await page.evaluate(() => {
      document.documentElement.style.setProperty(
        '--selection-background',
        '#123456'
      );
      document.documentElement.style.setProperty(
        '--selection-color',
        '#fedcba'
      );
    });
    await page.locator('#editor .cm-line').first().click();
    await page.keyboard.press('Home');
    await page.keyboard.press('Shift+End');
    await page.waitForFunction(() =>
      document.querySelector('#editor .cm-selectionBackground')
    );
    check(
      'the CSS variable controls the source selection',
      await page
        .locator('#editor .cm-selectionBackground')
        .first()
        .evaluate((node) => getComputedStyle(node).backgroundColor),
      'rgb(18, 52, 86)'
    );
    check(
      'the CSS variable controls source selection text',
      await page
        .locator('#editor .cm-line')
        .first()
        .evaluate((node) => getComputedStyle(node, '::selection').color),
      'rgb(254, 220, 186)'
    );
    await page.locator('#compiled-code .cm-line').first().dblclick();
    await page.waitForFunction(() =>
      document.querySelector('#compiled-code .cm-selectionBackground')
    );
    check(
      'the CSS variable controls the compiled selection',
      await page
        .locator('#compiled-code .cm-selectionBackground')
        .first()
        .evaluate((node) => getComputedStyle(node).backgroundColor),
      'rgb(18, 52, 86)'
    );
    check(
      'the CSS variable controls compiled selection text',
      await page
        .locator('#compiled-code .cm-line')
        .first()
        .evaluate((node) => getComputedStyle(node, '::selection').color),
      'rgb(254, 220, 186)'
    );
    check(
      'native page selections share the CSS variable',
      await page
        .locator('#repl-mode-desc')
        .evaluate(
          (node) => getComputedStyle(node, '::selection').backgroundColor
        ),
      'rgb(18, 52, 86)'
    );
    check(
      'native page selection text shares the CSS variable',
      await page
        .locator('#repl-mode-desc')
        .evaluate((node) => getComputedStyle(node, '::selection').color),
      'rgb(254, 220, 186)'
    );

    assert.equal(
      failures.length,
      0,
      `Selection checks failed: ${failures.join(', ')}`
    );
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
