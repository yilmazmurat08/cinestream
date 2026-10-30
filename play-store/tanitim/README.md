# Tanıtım malzemeleri

Kaynaktan üretilir; ara dosyalar (kareler, ekran görüntüleri, ses) depoya girmez.

## Logo açılış videosu (`logo-video/`)

Neon ışık şeritleri ve lazerler gelir, ışık CineStream logosunu çizer, kıvılcım patlaması olur, cam simge
çerçevesi belirir, "CineStream · IPTV PLAYER" yazısı çıkar. 8 saniye, sesli.

- `anim.html`: animasyon (her kare zamana göre çizilir; aynı kare her seferinde aynı çıkar).
- `synth.py`: ses tamamen burada üretilir. Hazır müzik ya da ses dosyası kullanılmadı, telif sorunu yok.
- `render.js`: kareleri PNG olarak çıkarır (Chromium).
- `uret.sh`: hepsini çalıştırır, yatay (1920x1080) ve dikey (1080x1920) MP4 üretir.

Zamanlama: şeritler 0–1,8 sn · lazerler 0,95/1,10 · logo çizimi 1,55–2,5 · patlama 2,62 · çerçeve 3,1–3,9 ·
yazı 4,0 · parlama 4,85. Görüntü ile ses aynı zamanları kullanır; biri değişirse öbürü de değişmeli.

Tek kareye bakmak için: `node render.js 1920 1080 onizleme 30 2.2,6.0`

## Tanıtım afişi (`afis/`)

Örnek kolaj düzeninde dikey afiş (1080x1920): simge ve yazı, başlık, 5 özellik, uygulamanın gerçek ekranlarıyla
üç telefon. Play mağaza ekran görüntüsü olarak da kullanılabilir (en-boy oranı uygun).

- `afis.html`: afişin düzeni ve metinleri.
- `hazirla.sh`: güncel ekranları çeker (PhonePromoShotsTest), afişi baştan üretir.
- `CineStream-afis-1080x1920.png`: son çıktı.

Play kuralları gereği: hazır içerik vaadi yok ("binlerce kanal" gibi), "Kendi M3U / Xtream listenle" notu var,
yetişkin klasörü gösterilmez, ünlü yüz ya da marka logosu kullanılmaz.
