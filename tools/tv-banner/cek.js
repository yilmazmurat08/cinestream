// banner.html'i 4 kat çözünürlükte çeker: node cek.js <çıkış.png>
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 320, height: 180 }, deviceScaleFactor: 4 });
  await p.goto('file://' + path.join(__dirname, 'banner.html'));
  await p.evaluate(() => document.fonts.ready);
  await p.evaluate(() => Promise.all([...document.images].map(i => i.decode())));
  await p.screenshot({ path: process.argv[2] });
  await b.close();
})();
