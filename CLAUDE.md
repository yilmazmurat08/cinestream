# CineStream — Claude için proje notları

Android IPTV oynatıcısı: Kotlin, Jetpack Compose, Media3, Room (şema v10), DataStore, Play Billing. Telefon ve Android TV.
Mağaza adı: **CineStream IPTV Player**. Paket adı: `com.cinestream.iptv`.

## Kullanıcı hakkında

- Kodlamaya yeni başlıyor. Her zaman **Türkçe**, kısa ve sade yaz.
- Kararları kullanıcı verir. Önerini nedeniyle birlikte söyle.
- Test cihazı (TV): **Xiaomi Mi TV Stick** (Full HD, Android TV 9 / API 28, **1 GB RAM**), onvo televizyonda. Düşük bellek ve büyük kanal listeleri ayrıca test edilmeli.
- Test cihazı (telefon): **realme C55** (RMX3710, realme UI 6.0, **Android 15 / API 35**, 6,72" 1080×2400, ekranda kamera deliği). Android 15'te ekran kenardan kenara (edge-to-edge) zorunlu: üst/alt boşluklar kontrol edilmeli. realme UI arka plandaki uygulamaları sıkı kapatır: arka planda oynatma, resim içinde resim ve otomatik liste güncelleme ayrıca test edilmeli.

## Kurallar (kullanıcının kendi kuralları)

- Yalnızca istenen soruna odaklan. Başka alana dokunma, yeni sorun açma.
- Uygulamanın bozulmasına asla izin verme; en önemli kriter budur.
- Önce test: Paketten önce tüm testleri ve lint'i çalıştır.
  - Komut: `./gradlew testDebugUnitTest lintRelease`.
- **APK ya da AAB'yi sormadan üretme.**
- **Sürümü sormadan yükseltme.** Play aynı sürüm kodunu iki kez kabul etmez; gerekirse sor.
- Telefon tasarımını istenmeden değiştirme. TMDB anahtarlarına, adreslerine ve eşleştirmeye dokunma.
- Kullanıcı verisini asla silme.
- Room şeması değişirse göç (migration) yaz.
- Yeni metinler hem Türkçe hem İngilizce olsun (`values` ve `values-en`). TV'de aynı özellikler bulunsun.
- PR'ı yalnızca kullanıcı "PR aç ve main'e birleştir" deyince aç ve birleştir.

## Güvenlik

- Anahtar, şifre ve keystore asla depoya, kayıtlara ya da sohbete girmez.
- Kullanıcıdan gizli bilgiyi sohbete yazmasını isteme.
- İmzalı AAB: `tools/build_release_aab.sh`.
  - Anahtar ortam değişkenlerinden okunur: `CINESTREAM_KEYSTORE_BASE64` ve `CINESTREAM_KEYSTORE_PASSWORD`.
- Test kullanıcılarının e-posta adresleri yalnızca Play Console'da durur.

## Yol haritası (13 Ekim 2026 kararları)

Test süreci bitip uygulama yayına girince **ilk güncellemede** yapılacaklar:

1. **Ölçüm:** Firebase Analytics ve Crashlytics.
   - Kullanıcı Firebase projesini açar ve `google-services.json` dosyasını verir.
   - Gizlilik metni ve Play Veri Güvenliği formu güncellenir.
2. **PRO'da 7 günlük ücretsiz deneme.** Deneme Play Console'da abonelik teklifi olarak tanımlanır.
3. **TV'de yayın akışı tablosu:** kanallar satır, saatler sütun. Herkese açık.
4. **Hızlı kanal değiştirme:** numarayla kanal açma ve önceki kanala dönme. Herkese açık.
5. **PRO 1. güncelleme** (kullanıcı kararı, 14 Ekim 2026). Bugün PRO yalnızca günlük 60 dakikalık sınırı kaldırıyor; bu zayıf. Ücretsiz özellikler PRO'ya taşınmaz.
   - **Çoklu liste — YAPILDI (14 Ekim 2026, henüz paketlenmedi):** 1 liste ücretsiz, 2. ve 3. liste PRO. Bütün liste işlemleri tek yerde: Ayarlar → **Liste Yönetimi** modülü (telefon ve TV; `PlaylistManagerModule`). Üç yuva: aç, yenile, düzenle, sil (onaylı), ekle; ücretsizde 2. ve 3. yuva "PRO ile aç". Ana ekranda ya da başka yerde liste kontrolü konmaz (kullanıcı kararı). Aynı anda tek liste ekranda; diğerleri `iptv_items_parked` tablosunda kayıpsız bekler (Room v11).
   - **Catch-up:** kaçırılan yayını izleme. Xtream `tv_archive` alanı kullanılır; yalnızca arşivi olan kanallarda görünür.
   - **Kesintisiz izleme:** Yayın donunca aynı kanalın yedek sürümüne (HD/FHD/yedek) otomatik geçilir.
   - **Otomatik sonraki bölüm:** 10 saniyelik sayaçla. **Ücretsiz.**
   - **Arka planda çalma (PRO, 16 Ekim 2026 kararı; özellik havuzundan):** radyo ve podcast için.
     - Ayarlar'da iki anahtar: "Kulaklıkla çalmaya devam et" ve "Ekran kapalıyken çalmaya devam et". Ücretsizde kilitli, dokununca PRO ekranı.
     - Kilit ekranı ve bildirimde kontroller (oynat/durdur, önceki/sonraki kanal); kulaklık ve Bluetooth tuşları çalışır (Media3 MediaSession).
     - Arka plana geçince yalnızca ses çalar (görüntü kapanır, pil/veri tasarrufu); dönünce görüntü geri gelir.
     - Kulaklık modu: kulaklık çıkınca ses hoparlörden devam etmez, durur.
     - Ücretsiz günlük 60 dakika sınırı arka planda da sayılır (açık kalmasın).
     - realme için ayarın yanında "Pil kısıtlamasını kaldır" yönlendirmesi.
     - Play Console'da ön plan hizmeti (mediaPlayback) beyan formu gerekebilir; kullanıcıya adım adım anlatılır.
     - TV: ana ekrana dönünce radyo çalmaya devam eder.
     - Bugün: video oynatıcı arka planda durur (`PlayerScreen` ON_STOP); radyo (`RadioPlayerManager`) arka planda korunmuyor. `PlaybackForegroundService` ve FOREGROUND_SERVICE_MEDIA_PLAYBACK izni zaten var.
6. **PRO 2. güncelleme:**
   - **TV'de canlı kanal önizleme:** Listede gezerken seçili kanal küçük pencerede oynar.
   - **Kendi kanal grupları:** grup oluşturma, kanalları sıralama, kategorileri gizleme.
   - **Jenerik atlama:** Dizide giriş bir kez işaretlenir, sonraki bölümlerde "Jeneriği geç" düğmesi çıkar.
   - **Dakikaya git (TV, kullanıcı fikri, 17 Ekim 2026):** Oynatıcının ilerleme çubuğunda küçük bir alan; kumandayla seçilir, rakamla dakika yazılır (ör. 65) ve yapım doğrudan o dakikaya gider. Ücretsizde çubukla Sol/Sağ sarma zaten var.
   - Paywall metni PRO özelliklerine göre yeniden yazılır.

Zaten var olan, ücretsiz özellikler (rakipte PRO; tanıtımda öne çıkarılabilir):
- Kaldığın yerden devam et.
- Otomatik liste güncelleme.

Sonraya kalanlar:
- Canlı yayını durdurma ve geri sarma.
- Yapay zekâ "Şu an ne izlesem?".
- Program hatırlatma.
- İzlerken açılan mini kanal listesi.
- Profiller ve cihazlar arası eşitleme.

**Spor modülü:** Yazıldı ve test edildi ama bu depoda değil. Yayından sonra **PRO özellik** olarak eklenecek (kullanıcı kararı, 16 Ekim 2026). Tüm kaynak kod ve ayrıntılı not (`kaynak/HAFIZA.md`) şu sayfada:
https://claude.ai/artifact/3v9FABLB6e6763tUZwYTPd

**Özellik havuzu (16 Ekim 2026, kullanıcı kararı; sırası henüz belli değil):**
Yayındaki uygulama bozulmasın diye ayrı dalda, ayrı modül olarak ve açma/kapama anahtarıyla yapılır; kullanıcı test edip onaylayınca birleştirilir. Önce yukarıdaki yol haritası önerilir.
**Onaylı görsel taslaklar (16 Ekim 2026, kullanıcı "gayet güzel" dedi; yapılırken bunlara uyulur):** https://claude.ai/artifact/9x5LwnPhPkWVzWcXSTZZnk — kaynak dosyalar `docs/tasarim/ozellik-taslaklari/` (Main: alt bar + profil menüsü + başlık stili; Kaydirma; Cark; Yarisma; ArkaPlan). Renkler: zemin #0E071A, vurgu #D946EF, altın etiket #D4A853, buzlu bar rgba(42,40,56,0.62) + 1 px rgba(255,255,255,0.16) çerçeve. Yazı: başlık DM Serif Display eğik, gövde Manrope (ikisi de OFL). Posterler taslakta yer tutucu; uygulamada kullanıcının listesinden gelir. TV görünümleri henüz çizilmedi.
- **Oyunlar (PRO).** Hepsi kullanıcının kendi listesinden beslenir; TV'de kumandayla oynanır.
  1. *Kaydırma oyunu:* Ekranda film ya da dizi posteri; üzerinde tür, konu, IMDb puanı (TMDB'den). Sola kaydırma: geç. Sağa kaydırma: Listeme (favori) ekle. TV'de sol/sağ tuşları.
  2. *Çark:* Listedeki kategorilerden (korku, savaş vb.) oluşan çark; ortadaki "Çevir" ile rastgele döner ve durur. Denk gelen kategoriden 3 rastgele film posteri çıkar; seçilenin kartı açılır, istemezse çark yeniden çevrilir.
  3. *Dizi bilgi yarışması (yapay zekâ):* Listeden bir dizi seçilir; posteri solda bulanık durur. Dizi hakkında soru ve iki cevap şıkkı. Doğru cevap: dizi kartı açılır. Yanlış: yeni soru. Üç yanlışta hoş bir mesajla dizi klasörlerine gidilir. Sorular dizinin gerçek bilgilerinden (yıl, oyuncu, tür, konu) üretilir, yapay zekâ yalnızca cümleye çevirir; sürpriz bozan soru yok.
     - **Soru üretimi (kullanıcı kararı, 17 Ekim 2026):** Elle yazılmış soru yok, sorunun tamamını yapay zekâ da yazmaz. Uygulama dizinin TMDB bilgisini (mevcut eşleştirme kullanılır; TMDB anahtar/adres/eşleştirmeye dokunulmaz) hazır kalıplara koyar: yayın yılı, başrol oyuncusu, tür, sezon sayısı, geçtiği yer. Doğru cevap her zaman bu gerçek bilgidir; yanlış şık listedeki başka bir diziden alınır.
     - Yapay zekâ isteğe bağlı, yalnızca cümleyi güzelleştirir, cevaba dokunmaz. İnternet yoksa ya da kota dolarsa düz kalıp kullanılır; oyun yine çalışır.
     - Üretilen sorular cihazda saklanır, aynı dizi için tekrar istek atılmaz. Konunun sonunu içeren bilgi kullanılmaz (spoiler yok).
- **Spor (PRO).** Yukarıdaki Spor modülü.
- **Yeni alt bar (telefon).** Ana Sayfa, Filmler, Diziler, **Canlı TV kalır**, en sağda kullanıcının profil resmi (resim yoksa insan figürü). Bar **saydam/buzlu** görünümde (kullanıcının örnek görseli: koyu, yarı saydam, ince açık çerçeveli, seçili sekme hafif vurgulu). Profil düğmesi barın üstünde açılan buzlu menü açar: Ayarlar, Profil (Ayarlar'daki profil buraya taşınır), Oyunlar, Spor, Listem.
- **Başlık yazı stili.** Bölüm başlıkları eğik, şık bir yazı tipiyle (ör. "Kaldığın yerden devam et"); üstünde küçük, harf aralıklı altın renkli etiket ("DEVAM ET"), altında gri açıklama. Türkçe harfleri destekleyen ücretsiz (OFL) yazı tipi. Başka uygulamaların adı, logosu, görseli kopyalanmaz.

Bilerek yapılmayacaklar:
- Uygulamaya hazır kanal listesi ya da içerik koymak. Play'den kaldırılma riski var.
- Yayın kaydetme.

**Hatırlatma kuralı:** Kullanıcı "ek özellik", "farklı özellik", "yeni özellik", "ne ekleyelim" gibi şeyler söylediğinde bu yol haritasını hatırlat ve önce bunları öner.
