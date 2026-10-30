// Kullanım: node render.js <genişlik> <yükseklik> <çıkış klasörü> [fps=30] [yalnızca şu saniyeler: 0.5,1.2,...]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const LOGO = process.env.LOGO || path.resolve(__dirname, "../../../app/src/main/res/drawable-nodpi/img_app_logo_1782414357652.jpg");

(async () => {
  const [, , w, h, outDir, fpsArg, only] = process.argv;
  const W = +w, H = +h, fps = +(fpsArg || 30);
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
  page.on('console', m => console.log('[sayfa]', m.text()));
  page.on('pageerror', e => console.log('[sayfa hatası]', e.message));
  await page.goto('file://' + path.resolve(__dirname, 'anim.html'));
  const logo = 'data:image/jpeg;base64,' + fs.readFileSync(LOGO).toString('base64');
  await page.evaluate(async ([a, b, src]) => { await init(a, b, src); }, [W, H, logo]);
  const dur = await page.evaluate(() => DUR);
  const times = only ? only.split(',').map(Number) : [...Array(Math.round(dur * fps)).keys()].map(i => i / fps);
  const t0 = Date.now();
  for (let k = 0; k < times.length; k++) {
    const t = times[k];
    const b64 = await page.evaluate(t => { render(t); return document.getElementById('c').toDataURL('image/png').slice(22); }, t);
    const name = only ? `t${t.toFixed(2)}.png` : `f${String(k).padStart(4, '0')}.png`;
    fs.writeFileSync(path.join(outDir, name), Buffer.from(b64, 'base64'));
    if (k % 30 === 0) console.log(`${k + 1}/${times.length} kare, ${((Date.now() - t0) / 1000).toFixed(1)} sn`);
  }
  await browser.close();
})();
