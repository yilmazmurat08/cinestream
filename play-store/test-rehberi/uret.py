#!/usr/bin/env python3
"""
Kapalı test katılım rehberi görselleri (WhatsApp için 1080x1920 PNG).

Kullanım:  python3 play-store/test-rehberi/uret.py
Gerekenler: Chromium headless_shell (varsayılan: /opt/pw-browsers/chromium_headless_shell-1194/...; CHROME ortam değişkeniyle değişir).

Görseller gerçek ekran görüntüsü değil, çizimdir: telefona göre yazılar küçük farklar gösterebilir.
Katılım bağlantısı görsellere yazılmaz (grupta paylaşılır).
"""
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
ICON = HERE.parent / "gorseller" / "uygulama-simgesi-512.png"
CHROME = os.environ.get("CHROME", "/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell")
ADMIN = "Yönetici"  # gruptaki yöneticinin adı (görsellerde mesaj sahibi olarak yazar)
W, H = 1080, 1920

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:1080px;height:1920px}
body{font-family:"Liberation Sans","DejaVu Sans",Arial,sans-serif;color:#fff;position:relative;overflow:hidden;
 background:radial-gradient(900px 760px at 6% 0%,#4b2192 0%,rgba(75,33,146,0) 70%),
            radial-gradient(900px 800px at 100% 100%,#7a3510 0%,rgba(122,53,16,0) 65%),
            linear-gradient(180deg,#0c0816,#07050d)}
.top{display:flex;align-items:center;justify-content:space-between;padding:52px 64px 0}
.brand{display:flex;align-items:center;gap:20px}
.brand img{width:92px;height:92px;border-radius:24px;box-shadow:0 0 0 2px rgba(190,120,255,.55)}
.brand b{font-size:50px;letter-spacing:-1px}
.brand b span{background:linear-gradient(90deg,#c58bff,#ff8a3d);-webkit-background-clip:text;color:transparent}
.pill{padding:14px 28px;border-radius:40px;border:2px solid rgba(255,255,255,.3);background:rgba(255,255,255,.08);font-size:32px;font-weight:700}
.hero{padding:40px 64px 0}
.herorow{display:flex;align-items:center;gap:28px}
.badge{flex:none;width:118px;height:118px;border-radius:59px;display:flex;align-items:center;justify-content:center;
 font-size:74px;font-weight:800;background:linear-gradient(135deg,#a855f7,#ff7a1a);box-shadow:0 10px 40px rgba(168,85,247,.45)}
h1{font-size:68px;line-height:1.08;font-weight:800;letter-spacing:-1px}
.sub{font-size:40px;line-height:1.3;color:#e0d5ff;margin-top:22px}
.sub b{color:#ffb27a}
.foot{position:absolute;left:0;right:0;bottom:0;padding:34px 64px 42px;font-size:30px;line-height:1.35;color:#cdbcf5;text-align:center;
 background:linear-gradient(180deg,rgba(0,0,0,0),rgba(0,0,0,.6))}
.center{display:flex;justify-content:center;align-items:flex-start;gap:36px;margin-top:34px}

/* telefon çizimi (600x1120; çiftlerde zoom ile küçülür) */
.phone{width:600px;height:1120px;border-radius:76px;background:#0b0b10;padding:16px;border:3px solid #3a3a48;
 box-shadow:0 30px 80px rgba(0,0,0,.6),0 0 90px rgba(168,85,247,.25);position:relative;flex:none}
.screen{width:100%;height:100%;border-radius:60px;background:#fff;color:#202124;overflow:hidden;position:relative}
.sb{display:flex;justify-content:space-between;padding:24px 40px 0;font-size:22px;font-weight:700}
.cap{text-align:center;font-size:34px;font-weight:800;margin-bottom:16px;color:#fff}
.cap i{font-style:normal;display:inline-block;width:52px;height:52px;line-height:52px;border-radius:26px;background:#ff7a1a;margin-right:12px}

/* dokunulacak yeri işaretleyen turuncu halka (sarmalayıcıya hizalı) ve etiket */
.hl{position:relative}
.hl::after{content:"";position:absolute;inset:-12px;border:7px solid #ff7a1a;border-radius:44px;box-shadow:0 0 0 14px rgba(255,122,26,.28);pointer-events:none}
.hl.round::after{border-radius:50%}
.ring{position:absolute;border:7px solid #ff7a1a;border-radius:26px;box-shadow:0 0 0 14px rgba(255,122,26,.28)}
.call{position:absolute;background:#ff7a1a;color:#fff;font-weight:800;font-size:30px;line-height:1;padding:13px 22px;border-radius:30px;
 box-shadow:0 8px 24px rgba(0,0,0,.35);white-space:nowrap}
.finger{position:absolute;font-size:96px;line-height:1;filter:drop-shadow(0 6px 10px rgba(0,0,0,.45))}


.mk{position:absolute;border:6px solid #ff7a1a;border-radius:26px;box-shadow:0 0 0 12px rgba(255,122,26,.30)}
.mk.no{border-color:#e02020;box-shadow:0 0 0 12px rgba(224,32,32,.30)}
.mk.ok{border-color:#22c55e;box-shadow:0 0 0 12px rgba(34,197,94,.30)}
.lab{position:absolute;color:#fff;font-weight:800;font-size:28px;line-height:1;padding:12px 20px;border-radius:26px;background:#ff7a1a;white-space:nowrap;box-shadow:0 8px 24px rgba(0,0,0,.45)}
.lab.no{background:#e02020}
.lab.ok{background:#16a34a}

/* play düğmeleri */
.gbtn{background:#01875f;color:#fff;font-weight:800;text-align:center;border-radius:30px}
.gout{border:3px solid #01875f;color:#01875f;font-weight:800;text-align:center;border-radius:30px;background:#fff}
.appicon{border-radius:34px;box-shadow:0 8px 24px rgba(0,0,0,.25)}

/* kartlar */
.card{margin:0 64px;border-radius:44px;background:rgba(255,255,255,.08);border:2px solid rgba(255,255,255,.18);padding:34px 38px}
.card h2{font-size:44px;line-height:1.15;font-weight:800;margin-bottom:12px}
.card p{font-size:36px;line-height:1.3;color:#e8defc}
.card p b{color:#ffb27a}
.warn{margin:34px 64px 0;border-radius:36px;background:rgba(255,122,26,.16);border:2px solid rgba(255,122,26,.65);padding:26px 34px;
 font-size:36px;line-height:1.3;color:#fff}
.warn b{color:#ffb27a}
"""


def page(step, title, sub, body, foot="Telefonuna göre yazılar küçük farklar gösterebilir.<br>Takılırsan ekran görüntüsünü gruba at 💬"):
    badge = f'<div class="badge">{step}</div>' if step else ""
    pill = f'<div class="pill">Adım {step}/6</div>' if step else '<div class="pill">Rehber</div>'
    return f"""<!doctype html><html lang="tr"><head><meta charset="utf-8"><style>{CSS}</style></head><body>
<div class="top"><div class="brand"><img src="icon.png"><b>Cine<span>Stream</span></b></div>{pill}</div>
<div class="hero"><div class="herorow">{badge}<h1>{title}</h1></div><div class="sub">{sub}</div></div>
{body}
<div class="foot">{foot}</div></body></html>"""


def status(dark=False):
    return '<div class="sb"><span>12:30</span><span>▂▄▆ 🔋</span></div>'


# ---------------------------------------------------------------- gerçek ekran görüntüsü yardımcıları
SHOTS = HERE / "ekranlar"
SRC_W, SRC_H = 900, 2000  # işaret koordinatları bu ölçekte verilir (görüntüler 1080x2400, aynı oran)


def shot(name, y0, y1, width, marks, gutter=0):
    """Gerçek ekran görüntüsünün y0..y1 bandı. İşaretler çerçevenin içinde, etiketler dışında (yazıların üstüne binmez).
    marks: (tür, x0, y0, x1, y1, etiket, yer) ; yer: 'right' (sağ yan) ya da 'below' (altta)."""
    s = width / SRC_W
    h = (y1 - y0) * s
    col = {"tap": "#ff7a1a", "no": "#e02020", "ok": "#16a34a"}
    pad = 10
    frame = [f'<div style="position:relative;width:{width}px;height:{h:.0f}px;border-radius:40px;overflow:hidden;'
             f'border:3px solid #3a3a48;background:#000;box-shadow:0 30px 80px rgba(0,0,0,.6),0 0 90px rgba(168,85,247,.25)">'
             f'<img src="file://{SHOTS / name}" style="position:absolute;left:0;top:{-y0 * s:.1f}px;width:{width}px">']
    outside = []
    for kind, x0, ym0, x1, ym1, label, where in marks:
        left, top = (x0 - pad) * s, (ym0 - y0 - pad) * s
        w, hh = (x1 - x0 + 2 * pad) * s, (ym1 - ym0 + 2 * pad) * s
        frame.append(f'<div class="mk {kind}" style="left:{left:.0f}px;top:{top:.0f}px;width:{w:.0f}px;height:{hh:.0f}px"></div>')
        c = col[kind]
        if where == "right":
            cy = top + hh / 2
            x_from = left + w
            x_to = width + 30
            outside.append(f'<div style="position:absolute;left:{x_from:.0f}px;top:{cy - 3:.0f}px;width:{x_to - x_from:.0f}px;height:6px;background:{c};border-radius:3px"></div>')
            outside.append(f'<div class="lab {kind}" style="left:{x_to:.0f}px;top:{cy - 30:.0f}px">{label}</div>')
        else:
            cx = left + w / 2
            y_from = top + hh
            y_to = h + 34
            outside.append(f'<div style="position:absolute;left:{cx - 3:.0f}px;top:{y_from:.0f}px;width:6px;height:{y_to - y_from:.0f}px;background:{c};border-radius:3px"></div>')
            outside.append(f'<div class="lab {kind}" style="left:{cx:.0f}px;top:{y_to:.0f}px;transform:translateX(-50%)">{label}</div>')
    frame.append("</div>")
    total_h = h + (110 if any(m[6] == "below" for m in marks) else 0)
    return (f'<div style="position:relative;width:{width + gutter}px;height:{total_h:.0f}px;flex:none">'
            + "".join(frame) + "".join(outside) + "</div>")


def legend():
    chip = ('<div style="display:flex;align-items:center;gap:14px;font-size:32px;font-weight:700">'
            '<div style="width:34px;height:34px;border-radius:10px;background:{c}"></div>{t}</div>')
    return ('<div style="display:flex;justify-content:center;gap:34px;margin-top:26px;flex-wrap:wrap">'
            + chip.format(c="#ff7a1a", t="Buna bas")
            + chip.format(c="#e02020", t="Basma")
            + chip.format(c="#22c55e", t="Doğru yerdesin")
            + "</div>")


# ---------------------------------------------------------------- 0. kapak
def s0():
    rows = [
        ("1", "Gmail adresini gruba yaz", "✉️"),
        ("2", "Gelen bağlantıya dokun", "👆"),
        ("3", "“Test uzmanı olun” düğmesine bas", "✅"),
        ("4", "“Google Play'den indirin”e bas", "⬇️"),
        ("5", "“Yükle”ye bas, bitince “Aç”", "📲"),
        ("6", "14 gün boyunca silme, kullan", "📅"),
    ]
    items = "".join(
        f'<div style="display:flex;align-items:center;gap:24px;margin:0 64px 20px;padding:20px 30px;border-radius:40px;'
        f'background:rgba(255,255,255,.09);border:2px solid rgba(255,255,255,.18)">'
        f'<div style="flex:none;width:82px;height:82px;border-radius:41px;background:linear-gradient(135deg,#a855f7,#ff7a1a);'
        f'display:flex;align-items:center;justify-content:center;font-size:52px;font-weight:800">{n}</div>'
        f'<div style="font-size:42px;font-weight:800;line-height:1.15;flex:1">{t}</div>'
        f'<div style="font-size:60px">{e}</div></div>'
        for n, t, e in rows
    )
    info = (
        '<div class="warn" style="margin-top:10px">📱 <b>Android</b> telefon veya tablet gerekir (iPhone’da olmaz).<br>'
        '🗓️ Test <b>14 gün</b> sürer, bu yüzden uygulamayı silme.</div>'
    )
    return page(
        None,
        "CineStream<br>test katılım rehberi",
        "Yalnızca <b>6 kolay adım</b>, yaklaşık 2 dakika.",
        f'<div style="margin-top:34px">{items}</div>{info}',
        "Her adımın görseli ayrı gelecek. Sırayla yap 👇<br>Takılırsan ekran görüntüsünü gruba at 💬",
    )


# ---------------------------------------------------------------- 1. gmail
def s1():
    a = f"""
<div class="card" style="margin-top:36px">
  <h2>Hangi Gmail adresi?</h2>
  <p>Telefonundaki <b>Play Store</b>’da kullandığın adres. Bulmak için: Play Store’u aç, <b>sağ üstteki yuvarlak resme</b> dokun.</p>
  <div style="position:relative;margin-top:26px;height:350px;border-radius:34px;background:#fff;color:#202124;overflow:hidden">
    <div style="display:flex;align-items:center;gap:16px;padding:26px 44px 0 28px">
      <div style="flex:1;height:76px;border-radius:38px;background:#eef0f4;display:flex;align-items:center;padding-left:30px;font-size:30px;color:#5f6368">🔍&nbsp; Uygulama ve oyun ara</div>
      <div class="hl round" style="width:76px;height:76px;border-radius:38px;background:linear-gradient(135deg,#a855f7,#ff7a1a);display:flex;align-items:center;justify-content:center;font-size:36px;font-weight:800;color:#fff;flex:none">A</div>
    </div>
    <div style="position:absolute;right:44px;top:130px;width:560px;border-radius:28px;background:#fff;box-shadow:0 14px 40px rgba(0,0,0,.28);padding:22px 26px;border:1px solid #e2e4e8">
      <div style="display:flex;align-items:center;gap:20px">
        <div style="width:78px;height:78px;border-radius:39px;background:linear-gradient(135deg,#a855f7,#ff7a1a);display:flex;align-items:center;justify-content:center;font-size:38px;font-weight:800;color:#fff;flex:none">A</div>
        <div><div style="font-size:30px;font-weight:700">Adın Soyadın</div>
        <div style="font-size:28px;color:#1a73e8;font-weight:800;background:#fff3e0;border-radius:10px;padding:2px 8px;margin-top:4px">ornek.adin@gmail.com</div></div>
      </div>
    </div>
    <div class="call" style="right:44px;top:262px;font-size:32px">☝ Adresin burada yazar</div>
  </div>
</div>
<div class="card" style="margin-top:30px">
  <h2>Sonra gruba yaz</h2>
  <div style="margin-top:14px;border-radius:30px;background:#efeae2;padding:26px 24px">
    <div style="margin-left:auto;max-width:760px;background:#d9fdd3;color:#111;border-radius:24px 24px 4px 24px;padding:20px 26px;font-size:36px;line-height:1.3;box-shadow:0 2px 6px rgba(0,0,0,.15)">
      Merhaba, Gmail adresim:<br><b>ornek.adin@gmail.com</b><div style="text-align:right;font-size:24px;color:#667781;margin-top:6px">12:31 ✓✓</div></div>
  </div>
  <p style="margin-top:16px">__ADMIN__ seni listeye ekleyince <b>“Eklendin”</b> yazar. Ondan sonra 2. adıma geç.</p>
</div>""".replace("__ADMIN__", ADMIN)
    return page(1, "Gmail adresini<br>gruba yaz", "Seni teste ekleyebilmem için adresin lazım.", a)


# ---------------------------------------------------------------- 2. bağlantı
def s2():
    phone = f"""
<div class="phone" style="height:1040px"><div class="screen" style="background:#efeae2">
  {status()}
  <div style="margin-top:14px;background:#075e54;color:#fff;padding:22px 28px;display:flex;align-items:center;gap:20px">
    <div style="font-size:38px">←</div>
    <div style="width:68px;height:68px;border-radius:34px;background:linear-gradient(135deg,#a855f7,#ff7a1a);display:flex;align-items:center;justify-content:center;font-size:34px;font-weight:800;flex:none">C</div>
    <div><div style="font-size:31px;font-weight:700">CineStream Test Grubu</div><div style="font-size:22px;opacity:.85">{ADMIN}, Ayşe, Mehmet, ...</div></div>
  </div>
  <div style="padding:34px 30px">
    <div style="max-width:540px;background:#fff;border-radius:4px 24px 24px 24px;padding:18px 24px;font-size:30px;line-height:1.3;box-shadow:0 2px 6px rgba(0,0,0,.15)">
      <div style="font-size:24px;font-weight:700;color:#d6336c;margin-bottom:6px">{ADMIN}</div>
      Adreslerinizi ekledim ✅<br>Bağlantıya dokunun 👇
    </div>
    <div class="hl" style="margin:44px 14px 0 8px;max-width:520px;background:#fff;border-radius:4px 24px 24px 24px;padding:12px;box-shadow:0 2px 6px rgba(0,0,0,.15)">
      <div style="height:150px;border-radius:16px;background:linear-gradient(135deg,#4b2192,#ff7a1a);display:flex;align-items:center;justify-content:center;font-size:72px">▶</div>
      <div style="padding:12px 8px 6px;font-size:27px;font-weight:700">CineStream – Test</div>
      <div style="padding:0 8px 8px;font-size:26px;color:#0a66c2;text-decoration:underline;word-break:break-all">https://play.google.com/…</div>
      <div class="finger" style="right:18px;bottom:-78px">👆</div>
    </div>
    <div class="call" style="left:36px;top:722px">☝ Bu bağlantıya dokun</div>
  </div>
</div></div>"""
    note = f'<div class="warn">⚠️ <b>“Eklendin”</b> mesajı gelmeden dokunma. Henüz eklenmediysen sayfa açılmaz.</div>'
    return page(2, "Gelen bağlantıya<br>dokun", "Grupta __ADMIN__ bağlantıyı paylaşacak.".replace("__ADMIN__", ADMIN.lower() if ADMIN == "Yönetici" else ADMIN),
                f'<div class="center">{phone}</div>{note}')


# ---------------------------------------------------------------- 3. test uzmanı olun (gerçek ekran)
def s3():
    img = shot("1-davet.jpg", 740, 1960, 600, [
        ("tap", 50, 1768, 473, 1882, "☝ Bu mavi düğmeye bas", "right"),
    ], gutter=360)
    note = ('<div class="warn">📜 Sayfa uzundur: <b>en aşağı kaydır</b>, mavi düğme en altta.<br>'
            '🔑 Telefonda <b>Gmail’inle giriş yapılmış</b> olmalı.</div>')
    return page(3, "“Test uzmanı olun”<br>düğmesine bas", "Gruptaki bağlantıyı açınca bu sayfa gelir.",
                f'{legend()}<div class="center" style="margin-top:26px">{img}</div>{note}')


# ---------------------------------------------------------------- 4. Google Play'den indirin (gerçek ekran)
def s4():
    img = shot("2-uzman.jpg", 100, 1900, 480, [
        ("ok", 208, 137, 652, 229, "✔ Bunu görmelisin", "right"),
        ("tap", 366, 515, 788, 562, "☝ Buna bas", "right"),
        ("no", 28, 1707, 458, 1860, "✖ BASMA!", "right"),
    ], gutter=400)
    note = ('<div class="warn" style="margin-top:22px">⚠️ En alttaki <b>“Programdan ayrıl”</b> düğmesine <b>basma</b>, testten çıkarsın.<br>'
            '✅ Yeşil “Siz bir test uzmanısınız” yazısı: <b>katıldın</b>, tamam.</div>')
    return page(4, "“Google Play’den<br>indirin”e bas", "Katıldıktan sonra bu ekran gelir.",
                f'{legend()}<div class="center" style="margin-top:22px">{img}</div>{note}')


# ---------------------------------------------------------------- 5. Yükle / Aç (gerçek ekran)
def s5():
    img = shot("3-play.jpg", 250, 1000, 640, [
        ("no", 34, 868, 432, 980, "✖ BASMA!", "below"),
        ("tap", 466, 868, 866, 980, "☝ Yüklenince buna bas", "below"),
    ])
    steps = ('<div class="card" style="margin-top:34px"><p style="font-size:38px;line-height:1.4">'
             '1️⃣ İlk önce burada mavi <b>“Yükle”</b> düğmesi olur. Ona bas.<br>'
             '2️⃣ Yükleme bitince düğme <b>“Aç”</b> olur. Ona bas.<br>'
             '3️⃣ <b>“Kaldır”</b> uygulamayı siler, <b>basma</b>.</p></div>')
    return page(5, "“Yükle”ye bas,<br>bitince “Aç”a bas", "Adı “Erken Erişim” yazar, bu normaldir.",
                f'{legend()}<div class="center" style="margin-top:26px">{img}</div>{steps}')


# ---------------------------------------------------------------- 6. 14 gün
def s6():
    days = "".join(
        f'<div style="width:124px;height:124px;border-radius:30px;background:rgba(255,255,255,.1);border:2px solid rgba(255,255,255,.25);'
        f'display:flex;flex-direction:column;align-items:center;justify-content:center;font-size:38px;font-weight:800">'
        f'<div style="font-size:26px;color:#cdbcf5;font-weight:700">Gün</div>{i}</div>'
        for i in range(1, 15)
    )
    cal = f'<div style="display:flex;flex-wrap:wrap;gap:18px;justify-content:center;margin:36px 52px 0;width:976px">{days}</div>'

    def rule(t):
        return f'<div style="display:flex;gap:18px;align-items:flex-start;margin:10px 0"><div>✅</div><div>{t}</div></div>'

    rules = (
        '<div class="card" style="margin-top:40px"><div style="font-size:42px;line-height:1.3;color:#e8defc">'
        + rule("<b style='color:#ffb27a'>Uygulamayı silme</b>")
        + rule("Play Store’da <b style='color:#ffb27a'>“Programdan ayrıl”a basma</b>")
        + rule("Arada aç: canlı TV, film, dizi <b style='color:#ffb27a'>dene</b>, favori ekle")
        + rule("Hata görürsen <b style='color:#ffb27a'>ekran görüntüsünü gruba at</b>")
        + '</div></div>'
        '<div class="warn" style="margin-top:30px">📅 Test <b>14 gün kesintisiz</b> sürmeli. Biri testten çıkarsa test uzar, hepimiz bekleriz 🙏</div>'
        '<div style="margin:34px 64px 0;border-radius:44px;padding:34px 40px;text-align:center;font-size:46px;font-weight:800;line-height:1.25;'
        'background:linear-gradient(135deg,rgba(168,85,247,.55),rgba(255,122,26,.5));border:2px solid rgba(255,255,255,.3)">'
        '🎉 Hepsi bu kadar!<br><span style="font-size:38px;font-weight:700">Katkın için çok teşekkürler 💜</span></div>'
    )
    return page(6, "14 gün boyunca<br>silme, kullan", "Başka bir şey yapman gerekmiyor.", cal + rules)


# ---------------------------------------------------------------- 7. sorun giderme
def s7():
    def card(n, t, d):
        return f'<div class="card" style="margin-top:26px"><h2>{n} {t}</h2><p>{d}</p></div>'
    body = (
        card("😕", "Sayfa açılmıyor ya da “katılamazsınız” diyor",
             "Yanlış hesapla açıyor olabilirsin. Bağlantıyı açan uygulamada <b>sağ üstteki yuvarlak resme</b> dokunup <b>gruba yazdığın Gmail’i</b> seç. Hâlâ olmuyorsa gruba Gmail adresini tekrar yaz.")
        + card("🔍", "Play Store’da uygulamayı bulamıyorum",
               "Arayınca çıkmaz, normal. <b>Gruptaki bağlantıdan</b> gir. Katıldıktan sonra çıkması birkaç saat sürebilir.")
        + card("🔄", "Güncelleme geldi",
               "Play Store ➜ <b>sağ üstte yuvarlak resim</b> ➜ <b>Uygulamaları ve cihazı yönet</b> ➜ <b>CineStream AI TV</b> yanındaki <b>Güncelle</b>.")
        + card("📱", "iPhone kullanıyorum",
               "Üzgünüz, uygulama <b>yalnızca Android</b>’de çalışır. Android telefonu olan bir yakınından yardım isteyebilirsin.")
    )
    return page(None, "Olmadı mı?<br>Çözümler burada", "En sık takılınan 4 yer.", body,
                "Yine olmadıysa gruba <b>ekran görüntüsü</b> at 💬<br>Birlikte çözeriz.")


SLIDES = [
    ("00-baslangic.png", s0),
    ("01-gmail-adresi.png", s1),
    ("02-baglantiya-dokun.png", s2),
    ("03-test-uzmani-olun.png", s3),
    ("04-google-play-indirin.png", s4),
    ("05-yukle-ve-ac.png", s5),
    ("06-14-gun-kullan.png", s6),
    ("07-sorun-cozumleri.png", s7),
]

def main():
    only = set(sys.argv[1:])
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="rehber-"))
    shutil.copy(ICON, tmp / "icon.png")
    try:
        for name, fn in SLIDES:
            if only and name not in only and name[:2] not in only:
                continue
            html = tmp / (name.replace(".png", ".html"))
            html.write_text(fn(), encoding="utf-8")
            out = HERE / name
            subprocess.run(
                [CHROME, "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
                 f"--window-size={W},{H}", "--force-device-scale-factor=1", f"--screenshot={out}", f"file://{html}"],
                check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
            print("yazıldı:", out.relative_to(HERE.parent.parent))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
