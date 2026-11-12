# Tanıtım malzemeleri

Kaynaktan üretilir; ara dosyalar (kareler, ekran görüntüleri, ses) depoya girmez.

## Logo açılış videosu (`logo-video/`)

Neon ışık şeritleri ve lazerler gelir, ışık CineStream logosunu çizer, kıvılcım patlaması olur, cam simge
çerçevesi belirir, "CineStream · IPTV PLAYER" yazısı ve altında slogan ("İzlemenin daha akıllı hali", açılış
ekranındaki gibi mor-pembe çizgiyle) çıkar. 8 saniye, sesli.

- `anim.html`: animasyon (her kare zamana göre çizilir; aynı kare her seferinde aynı çıkar).
- `synth.py`: ses tamamen burada üretilir. Hazır müzik ya da ses dosyası kullanılmadı, telif sorunu yok.
- `render.js`: kareleri PNG olarak çıkarır (Chromium).
- `uret.sh`: hepsini çalıştırır. Üretilenler:
  - `CineStream-logo-yatay.mp4` / `-dikey.mp4`: 8 sn, yalın logo açılışı.
  - `CineStream-durum-yakinda.mp4`: 10 sn, dikey, sonunda "ÇOK YAKINDA / Google Play'de" kutusu (durumlar için).
  - `CineStream-durum-yayinda.mp4`: aynısı, "ŞİMDİ YAYINDA / Google Play'de" (uygulama yayına girince).
  - `CineStream-logo-640.gif` / `-480.gif`: forumlar için (MP4 yüklenemeyen yerlerde GIF oynar).

Zamanlama: şeritler 0–1,8 sn · lazerler 0,95/1,10 · logo çizimi 1,55–2,5 · patlama 2,62 · çerçeve 3,1–3,9 ·
yazı 4,0 · parlama 4,85 · slogan 5,0 · çağrı kutusu 5,9. Görüntü ile ses aynı zamanları kullanır; biri
değişirse öbürü de değişmeli. Slogan uygulamadaki `intro_tagline` metnidir; o değişirse burada da değişmeli.

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
- **İngilizce afiş (13 Kasım 2026, Facebook grupları için):** `afis-en.html` (aynı düzen, İngilizce metin, "NOW ON Google Play" kutusu, altta "No channels or content included" notu; `?fmt=fb` ile 1080x1350). Ekranlar İngilizce arayüzle ve İngilizce adlı hayali içerikle çekilir (`CINESTREAM_SHOTS_LANG=en`, görseller `app/src/test/resources/promo/en`). `hazirla-en.sh` hepsini üretir. Çıktılar: `CineStream-poster-en-1080x1920.png`, `CineStream-poster-en-1080x1350.png`. Yalnızca yayındaki sürümün (1.1.8) özellikleri yazılır.

Play kuralları gereği: hazır içerik vaadi yok ("binlerce kanal" gibi), "Kendi M3U / Xtream listenle" notu var,
yetişkin klasörü gösterilmez, ünlü yüz ya da marka logosu kullanılmaz.

## Durum tanıtım videosu (`durum-tanitim/`)

Dikey, 17,6 sn: logo açılışı (`CineStream-durum-yayinda`), ardından TV çerçevesinde uygulama turu (ana ekran, film
klasörleri, yapay zekâ özeti) ve "ŞİMDİ YAYINDA / Google Play'de" kutusu; altında üretilmiş müzik.

- `klip.sh`: kullanıcının ekran kaydından üç kısa sahne keser; kanal logoları, afişler, dizi adı/özeti ve bölüm
  görselleri bulanık, kayıttaki ses atılır. Kesme yerleri ve bölgeler o kayda göredir (kayıt depoda değil).
- `tur.html` + `tur-render.js`: dikey düzen (TV çerçevesi, sahne açıklamaları, çağrı kutusu).
- `muzik.py`: sinematik elektronik müzik (120 BPM, La minör); vuruş logodaki patlamada (2,62 sn) girer,
  turun başında zil (10,62), son vuruş 16,62. Logo efektleri müzikle çakışmasın diye `PAD=0` ile alt tonsuz üretilir.
- `uret.sh <kayıt.mp4>`: hepsini çalıştırır.


## Tanıtım özeti (`ozet-video/`)

Yaklaşık 40 sn, iki biçim: dikey 1080x1920 (durumlar, Shorts) ve yatay 1920x1080 (YouTube, forum). Logo açılışı
(dikeyde `CineStream-durum-yayinda`, yatayda kutusuz 10 sn'lik sürüm), ardından ekran kaydından bulanık sahneler ve
"ÖNE ÇIKAN" etiketli **QR ile giriş** sahnesi: TV'deki "Telefonla gir" penceresi ve telefonda açılan sayfa yan
yana. Sonda "ŞİMDİ YAYINDA / Google Play'de" kutusu ve "Uygulama içerik sağlamaz; kendi listeni eklersin." notu.

- `sahneler.sh`: ekran kaydından altı kısa sahne keser (ana ekran, tür klasörleri, fragman, yapay zekâ özeti,
  oynatıcı, bölüm listesi). Kanal logoları, afişler, film/dizi adları ve özetleri, oyuncu fotoğrafları, fragman ve
  yayın görüntüleri bulanık; kayıttaki ses atılır. Kesme yerleri o kayda göredir (kayıt depoda değil).
- `qr-cekim.kt.txt`: QR penceresini uygulamanın kendi koduyla (Robolectric) çizer ve telefon sayfasını gerçek
  `PhoneEntryServer`'dan alır. Geçici test olarak kopyalanıp çalıştırılır, sonra silinir (depodaki testlere girmez).
  Pencerenin düzeni değişirse bu kopya da güncellenmeli.
- `telefon.js`: telefon sayfasını telefon boyutunda resme çevirir.
- `ozet.html` + `ozet-render.js`: tek sayfa, iki düzen (TV çerçevesi, sahne başlıkları, telefon, çağrı kutusu).
- Müzik: `../durum-tanitim/muzik.py muzik.wav ozet` (39,6 sn). Sahne geçişleri ölçü başlarında; QR sahnesinde
  davul susar, 28,62'de yeniden girer; son vuruş 38,62.
- `uret.sh <kayıt.mp4>`: hepsini çalıştırır, `CineStream-tanitim-dikey.mp4` ve `-yatay.mp4` üretir. Yazı tipi
  Inter (yüklü değilse benzer bir yazı tipiyle çıkar).

## İngilizce özellik görselleri (`ozellikler/en/`, 13 Kasım 2026)

Facebook için 4:5 (1080x1350), 8 adet; Türkçe serinin (`ozellik-tek.html`) tasarımı. Kaynak `../gorseller/uret/ozellik-en.html?n=0..7`; ekranlar `tek-ekran/en/` (PhonePromoShotsTest, `CINESTREAM_SHOTS_LANG=en`), afiş/kanallar `app/src/test/resources/promo/en`. Çekim: `node afis/cek.js "../gorseller/uret/ozellik-en.html?n=0" cikis.png 1080 1350`. Yalnızca yayındaki sürümün özellikleri.
