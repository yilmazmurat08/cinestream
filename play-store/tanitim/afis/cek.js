// Kullanım: node cek.js afis.html cikis.png [genişlik=1080] [yükseklik=1920]
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const [, , page_, out, w = '1080', h = '1920'] = process.argv;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: +w, height: +h }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.resolve(page_));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  await page.screenshot({ path: out, type: 'png' });
  await browser.close();
})();
