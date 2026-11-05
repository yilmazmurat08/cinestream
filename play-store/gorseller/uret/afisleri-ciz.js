// Hayali afiş ve kanal logolarını çizer, test kaynaklarına yazar (tanıtım ekranları bunları kullanır).
// Kullanım: node afisleri-ciz.js <çıkış klasörü>   (varsayılan: app/src/test/resources/promo)
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const slug = (s) => s.toLocaleLowerCase('tr').replace(/ç/g, 'c').replace(/ğ/g, 'g').replace(/ı/g, 'i').replace(/ö/g, 'o')
  .replace(/ş/g, 's').replace(/ü/g, 'u').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

(async () => {
  const out = path.resolve(process.argv[2] || path.join(__dirname, '../../../app/src/test/resources/promo'));
  fs.mkdirSync(path.join(out, 'afis'), { recursive: true });
  fs.mkdirSync(path.join(out, 'kanal'), { recursive: true });
  const sayfa = 'file://' + path.join(__dirname, 'afis-uretici.html');
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 480, height: 720 }, deviceScaleFactor: 1 });
  await page.goto(sayfa);
  await page.evaluate(() => document.fonts.ready);
  const liste = await page.evaluate(() => window.LISTE);
  for (const [tur, adlar, w, h] of [['afis', liste.afis, 480, 720], ['kanal', liste.kanal, 320, 200]]) {
    await page.setViewportSize({ width: w, height: h });
    for (let i = 0; i < adlar.length; i++) {
      await page.goto(`${sayfa}?tur=${tur}&i=${i}`);
      await page.evaluate(() => document.fonts.ready);
      await page.waitForTimeout(150);
      const dosya = path.join(out, tur, `${tur === 'afis' ? adlar[i] : slug(adlar[i])}.jpg`);
      await page.screenshot({ path: dosya, type: 'jpeg', quality: 88 });
    }
  }
  await browser.close();
  console.log('Hazır:', out);
})();
