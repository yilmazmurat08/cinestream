# Tanıtım malzemeleri

Kaynaktan üretilir; ara dosyalar (kareler, ekran görüntüleri, ses) depoya girmez.

## Logo açılış videosu (`logo-video/`)

Neon ışık şeritleri ve lazerler gelir, ışık CineStream logosunu çizer, kıvılcım patlaması olur, cam simge
çerçevesi belirir, "CineStream · IPTV PLAYER" yazısı çıkar. 8 saniye, sesli.

- `anim.html`: animasyon (her kare zamana göre çizilir; aynı kare her seferinde aynı çıkar).
- `synth.py`: ses tamamen burada üretilir. Hazır müzik ya da ses dosyası kullanılmadı, telif sorunu yok.
- `render.js`: kareleri PNG olarak çıkarır (Chromium).
- `uret.sh`: hepsini çalıştırır. Üretilenler:
  - `CineStream-logo-yatay.mp4` / `-dikey.mp4`: 8 sn, yalın logo açılışı.
  - `CineStream-durum-yakinda.mp4`: 10 sn, dikey, sonunda "ÇOK YAKINDA / Google Play'de" kutusu (durumlar için).
  - `CineStream-durum-yayinda.mp4`: aynısı, "ŞİMDİ YAYINDA / Google Play'de" (uygulama yayına girince).
  - `CineStream-logo-640.gif` / `-480.gif`: forumlar için (MP4 yüklenemeyen yerlerde GIF oynar).

Zamanlama: şeritler 0–1,8 sn · lazerler 0,95/1,10 · logo çizimi 1,55–2,5 · patlama 2,62 · çerçeve 3,1–3,9 ·
yazı 4,0 · parlama 4,85. Görüntü ile ses aynı zamanları kullanır; biri değişirse öbürü de değişmeli.

Tek kareye bakmak için: `node render.js 1920 1080 onizleme 30 2.2,6.0`
(çağrı kutusuyla: `DUR=10 CTA_TOP="ÇOK YAKINDA" CTA_MAIN="Google Play'de" node render.js 1080 1920 onizleme 30 6.2`)

## Tanıtım afişi (`afis/`)

Örnek kolaj düzeninde dikey afiş (1080x1920): simge ve yazı, başlık, 5 özellik, uygulamanın gerçek ekranlarıyla
üç telefon. Play mağaza ekran görüntüsü olarak da kullanılabilir (en-boy oranı uygun).

- `afis.html`: afişin düzeni ve metinleri.
- `afis-durum.html`: durumlar için dikey afiş. Üstte isim/saat, altta "Yanıtla" çubuğu görüntüye bindiği için
  önemli yazılar bu bölgelerden uzak; "ÇOK YAKINDA / Google Play'de" kutusu var (`?cta=yayinda` ile "ŞİMDİ YAYINDA").
- `afis-forum.html`: forumlar için yatay afiş (1920x1080, JPG).
- `hazirla.sh`: güncel ekranları çeker (PhonePromoShotsTest), bütün afişleri baştan üretir.
- `CineStream-afis-1080x1920.png`: genel afiş (son çıktı).

Play kuralları gereği: hazır içerik vaadi yok ("binlerce kanal" gibi), "Kendi M3U / Xtream listenle" notu var,
yetişkin klasörü gösterilmez, ünlü yüz ya da marka logosu kullanılmaz.
