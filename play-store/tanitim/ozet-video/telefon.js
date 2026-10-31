const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  await p.goto('file://' + path.resolve(process.argv[2]));
  await p.waitForTimeout(300);
  await p.screenshot({ path: process.argv[3] });
  await b.close();
})();
