// Özet turunun karelerini çıkarır: node ozet-render.js <genişlik> <yükseklik> <çıkış> [yalnızca şu saniyeler]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const SURE = 30.1;
(async () => {
  const [, , w, h, out, only] = process.argv;
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: +w, height: +h }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.join(__dirname, 'ozet.html'));
  await page.evaluate(() => document.fonts.ready);
  const times = only ? only.split(',').map(Number) : [...Array(Math.round(SURE * 30)).keys()].map(k => k / 30);
  let k = 0;
  for (const t of times) {
    await page.evaluate(async t => { await window.setFrame(t); }, t);
    const name = only ? `t${t.toFixed(2)}.png` : `f${String(++k).padStart(4, '0')}.png`;
    await page.screenshot({ path: path.join(out, name) });
  }
  await browser.close();
  console.log('kare:', times.length);
})();
