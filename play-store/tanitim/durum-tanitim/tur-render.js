// Uygulama turunun karelerini çıkarır: node tur-render.js <çıkış klasörü> [yalnızca şu kareler: 1,120,...]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const [, , out, only] = process.argv;
  fs.mkdirSync(out, { recursive: true });
  const n = fs.readdirSync(path.join(__dirname, 'tur_klip')).filter(f => f.endsWith('.png')).length;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.join(__dirname, 'tur.html'));
  await page.evaluate(() => document.fonts.ready);
  const frames = only ? only.split(',').map(Number) : [...Array(n).keys()].map(k => k + 1);
  for (const i of frames) {
    await page.evaluate(async ([i, t]) => { await window.setFrame(i, t); }, [i, (i - 1) / 30]);
    await page.screenshot({ path: path.join(out, `f${String(i).padStart(4, '0')}.png`) });
  }
  await browser.close();
  console.log('tur kareleri:', frames.length);
})();
