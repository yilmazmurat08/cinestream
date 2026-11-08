# CineStream — Claude için proje notları

Android IPTV oynatıcısı: Kotlin, Jetpack Compose, Media3, Room (şema v10), DataStore, Play Billing. Telefon ve Android TV.
Mağaza adı: **CineStream IPTV Player**. Paket adı: `com.cinestream.iptv`.

## Kullanıcı hakkında

- Kodlamaya yeni başlıyor. Her zaman **Türkçe**, kısa ve sade yaz.
- Kararları kullanıcı verir. Önerini nedeniyle birlikte söyle.
- Öneri istendiğinde kullanıcıyı memnun etmeye ya da eski konuşmalara uymaya çalışma; gerçek bilgiye ve veriye dayanarak doğru olanı söyle, katılmıyorsan nedeniyle açıkça belirt (kullanıcı isteği, 4 Kasım 2026).
- Test cihazı (TV): **Xiaomi Mi TV Stick** (Full HD, Android TV 9 / API 28, **1 GB RAM**), onvo televizyonda. Düşük bellek ve büyük kanal listeleri ayrıca test edilmeli.
- Test cihazı (telefon): **realme C55** (RMX3710, realme UI 6.0, **Android 15 / API 35**, 6,72" 1080×2400, ekranda kamera deliği). Android 15'te ekran kenardan kenara (edge-to-edge) zorunlu: üst/alt boşluklar kontrol edilmeli. realme UI arka plandaki uygulamaları sıkı kapatır: arka planda oynatma, resim içinde resim ve otomatik liste güncelleme ayrıca test edilmeli.

## Kurallar (kullanıcının kendi kuralları)

- Yalnızca istenen soruna odaklan. Başka alana dokunma, yeni sorun açma.
- Uygulamanın bozulmasına asla izin verme; en önemli kriter budur.
- Önce test: Paketten önce tüm testleri ve lint'i çalıştır.
  - Komut: `./gradlew testDebugUnitTest lintRelease`.
  - Bulut ortamında Android SDK yoksa: ortamın Setup script'i SDK'yı `/opt/android-sdk`'ya kurar, `local.properties` yazar ve Maven Central "429 Too Many Requests" verdiğinde önce Google'ın aynası denensin diye Gradle ayarı ekler (Gradle ve Robolectric). Betik 3 Kasım 2026'da kullanıcıya verildi (ortam menüsü → Edit → Setup script). İlk turda Robolectric her Android sürümü için ~130 MB indirir; tur ~25 dk sürer.
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
  - Anahtar ortam değişkenlerinden okunur: `CINESTREAM_KEYSTORE_BASE64`, `CINESTREAM_KEYSTORE_PASSWORD` ve `CINESTREAM_PLAY_LICENSE_KEY` (Play lisans anahtarı; yoksa betik paket üretmez).
  - İmza değişkenleri 'Default' ortamının (3 Eylül 2026'da oluşturulan; aynı adlı iki ortam daha var) ayarlarında kalıcı (2 Kasım 2026'da eklendi); scratchpad'e değil oraya güvenilir. Eksik görünürse kullanıcıya sor, kendin başka yerden alma.
- Test kullanıcılarının e-posta adresleri yalnızca Play Console'da durur.

## Play politika takvimi (29 Ekim 2026 duyurusundan)

- **Japonya yetkilendirmesi, son gün 1 Aralık 2026 (kullanıcı yapar):** Japonya'ya satış yapan (uygulama 177 ülkede, PRO satılıyor) geliştirici Play Console / Payments Center'daki daveti açıp onaylamalı; yapılmazsa bütün ödemeler dünya genelinde bekletilir. **Durum (7 Kasım 2026):** kullanıcının masaüstü Claude oturumu politika metnini okudu: davet yalnızca Japonya'da satış olunca gelir; bugün davet yok. Japonya'dan ilk satıştan sonra e-posta ve Play Console bildirimleri izlenir.
- **Satıcı (tüccar) durumu beyanı, son gün 1 Şubat 2027 (kullanıcı yapar; Google maili 4 Kasım 2026):** PRO satıldığı için "satıcı". Hesabı yönet > Hesap ayrıntıları > Hakkınızda > Satıcı durumu; bireysel satıcıda herkese açık telefon doğrulanır, telefon ve adres Play'de herkese açık görünür. Sonra her uygulama için Politika ve programlar > Uygulama içeriği'nde onay (Hizmet Şartları bağlantısı isteğe bağlı; şartlar bugün yalnızca uygulama içinde, herkese açık adres yok). Yapılmazsa güncelleme gönderilemez ve uygulamalar dünya genelinde kaldırılır. **Durum (7 Kasım 2026):** beyan dolduruldu ama incelemeye GÖNDERİLMEDİ (Yayınlama genel bakışı'nda bekliyor); kullanıcı kararı: 1.1.8 incelemesi uzamasın diye onaydan sonra gönderilecek.
- **Şubat 2027, kalite eşikleri:** bellek (Anonymous RSS + Swap, Bitmap) ve DEX kod optimizasyonu, Android vitals'ta. R8 açık; yayından sonra vitals izlenir.
- **Hedef API 37'ye (Android 17) geçerken:** TV'deki "Telefondan kolay kurulum" (`PhoneEntryServer`, yerel Wi-Fi'de küçük sunucu) için yerel ağ izni ve Play Console beyanı gerekecek. Bugün hedef 36, etkilenmiyor.
- Etkilemeyenler: konum, kişiler, SMS/arama kaydı, tüm dosyalara erişim, erişilebilirlik izinleri kullanılmıyor; öğrenci gibi koşullu indirimli abonelik yok.

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

**Kanal kartında yayın akışı ve program hatırlatma — YAPILDI (19 Ekim 2026, henüz paketlenmedi; onaylı taslak: https://claude.ai/artifact/PzarsAnRMP1daxnfuTy1cm):**
- Ayrı EPG ekranı ve "Şu an ekranda" bandı kaldırıldı. Canlı kanal kartında Oynat + EPG düğmesi (EPG yalnızca gerçek yayın akışı olan kanalda). EPG kartın altında 12 saatlik akışı açar.
- Programa dokununca "Hatırlat (5 dk önce)"; **ücretsiz**. Bildirime dokununca kanal açılır (`ProgramReminders`, `ProgramReminderReceiver`; yeniden başlatmada alarmlar yeniden kurulur).
- Oynatıcı (telefon, yatay): sağdan açılan dar kanal paneli; dikeyde alttaki liste sistem tuşlarının üstünde.
- TV: kanal panelinde Sağ tuşu akışı yanda açar, OK hatırlatma kurar, Sol kapatır.

**1.1.8 (sürüm kodu 19) — sürüm 2 Kasım 2026'da yükseltildi (kullanıcı onayı); imzalı AAB 3 Kasım 2026'da üretildi ve kullanıcıya gönderildi (boyut, SHA-256, test sonucu: `docs/release/surum-teslim.md`). 3 Kasım akşamı kullanıcı Play'e yükledi: Üretim (177 ülke + dünyanın geri kalanı, tam kullanıma sunma) ve Kapalı test - Alpha'da 19; 18 iki kanalda da "Dahil değil"; incelemeye gönderildi. 19 artık kullanılmış sayılır, yeni AAB gerekirse sürüm kodu sorulur. Paketten önce `./gradlew testDebugUnitTest lintRelease`; sürüm notları `docs/release/surum-notlari.md`. 1.1.7 Play'de TV afişi/simgesi yüzünden reddedildi (aşağıda). İçindekiler:**
- Ayarlar'daki "Çökme Günlükleri (Tanılama)" gizli; Ayarlar altındaki sürüm satırına 7 kez dokununca açılır (`DiagnosticsUnlock`). Çökme sonrası hata ekranı her zaman görünür.
- Oynatıcıdaki ücretsiz "kalan süre" sayacı sistem gezinme tuşlarının altında kalmıyor (tablette yatayken görünmüyordu).
- Kapsamlı denetim (20 Ekim 2026): açılışta tüm liste belleğe yüklenmiyor (yalnızca sayım); EPG belleği küçüldü (26 saatlik pencere, açıklama saklanmaz); dizi ayrıştırma önbelleği sınırlı; önbellek temizliği resim önbelleğini korur; ağır liste/grup hesapları ana iş parçacığı dışında (Mi TV Stick'te donma/ANR riski azalır).
- Fragman (20 Ekim 2026): sahibi gömülü oynatmaya izin vermeyen YouTube fragmanı Fragman düğmesiyle YouTube uygulamasında açılır; kendiliğinden başladıysa sessizce durur; cihazda hatırlanır (`TrailerEmbedBlocklist`). Telefon kartları, ana sayfa fragman kutusu, TV kartı.
- Çökme raporu ekranı bir kez gösterilir (Geri tuşuyla kapatılsa da tekrar çıkmaz); uygulama güncellendiyse eski sürümün raporu gösterilmez.
- Güvenlik ve sürüm hazırlığı (25–26 Ekim): fragman WebView'ında dosya/içerik erişimi kapalı; AAB'ye yerel sembol tablosu; "1 season" çoğul düzeltmesi.
- Fragman ve kayıtlar (28 Ekim): Ayarlar'da "Fragmanı otomatik oynat" anahtarı (telefon ve TV), "Fragmanı kapat" düğmesi kaldırıldı. Kaydedilenler'de "Nerede kalmıştık": yarım kalan son 5 film/dizi (`ResumeShelf`; izleme geçmişi silinmez, yalnızca süzülür). TV detayda başlık kaybolmuyor.
- Görünüm (28 Ekim): Ayarlar gruplu bölüm menüsü; telefon vitrini afiş üst barın arkasına uzanıp alta doğru zemine erir, afiş yoksa logo; TV vitrin kenarları yumuşak. Giriş ekranı orantılı (kısa ikonlu alanlar, Gemini anahtarı notu).
- Kategori görselleri (28 Ekim; `CategoryArt`, `res/drawable-nodpi/genre_*`, `live_*`, `adult_neon`): film, dizi ve canlı klasörleri adındaki türe göre görsel alır (telefon kartları, TV kategori satırları); eşleşmeyen ve platform klasörleri genel görsel. Yetişkin klasörleri neon "18+" tabelası (insan yok: Play riski). Görseller kullanıcının Gemini'de ürettikleri; ünlü yüz ya da marka logosu içeren görsel kullanılmaz.
- Denetim (28 Ekim): "Nerede kalmıştık" şeridinde aynı itemId'li iki kayıtta çökme giderildi (anahtar kaydın `id`'si); logo `drawable-nodpi`'de (TV'de 16 MB yerine 4 MB); kullanılmayan kod ve metinler temizlendi (ana sayfada boşuna çalışan kategori sorgusu, sonsuz Oynat animasyonu vb.). TV duman testleri `StandardTestDispatcher` ile (Compose test ortamının bekletmeyen dağıtıcısı ara sıra "Index 2, size 2" veriyordu; cihazda olmaz).
- Deneme APK'sı: `./gradlew assemblePreview` → "CineStream Test" (`com.cinestream.iptv.test`), Play sürümünün yanına kurulur. Hata ayıklama imzası çalışma ortamına göre değişir: yeni ortamın ilk APK'sında eski test uygulaması kaldırılmalı.
- **TV afişi (2 Kasım 2026, Play reddi):** 1.1.7 (sürüm kodu 18) Android TV kalite yönergeleri yüzünden reddedildi: "Tam boyutlu uygulama banner'ı ve/veya simgesi yok", "simge alanın tamamını kaplamıyor", TV-BN. İncelemecinin ekran görüntüsünde afiş değil simge vardı: logo sabit 72dp olduğundan büyük çizilen simgede etrafında koyu kırmızı zemin çerçeve gibi kalıyordu. Düzeltme:
  - Afiş: koyu zeminli vektör yerine `drawable-xhdpi/tv_banner.png` (320x180, saydamlıksız, belirgin mor zemin, logo + "CineStream" + "IPTV PLAYER"; kaynak `tools/tv-banner/`). Uygulamaya ek olarak `MainActivity`'de de `android:banner`.
  - Yalnızca TV'de simge: `mipmap-television-anydpi-v26` → logo yüzdeyle (%10 pay) büyür, arka plan logonun kenar renginde. Telefondaki simge değişmedi.
  - Test `TvLauncherArtTest`. Ret yalnızca afiş/simgeyle ilgili; testçiler ve 14 günlük test sıfırlanmaz.
  - Bilinen: eski `mipmap-*/ic_launcher*.webp` dosyaları bozuk (lint IconDipSize uyarısı); minSdk 26 olduğu için hiç kullanılmıyor (simge `mipmap-anydpi-v26`).
- **TV kalite denetimi (3 Kasım 2026, ret sonrası; kullanıcı "artık red yemek istemiyorum" dedi):** Play'in Android TV kalite listesi koda karşı tarandı; ret mailindeki dört madde (320x180 afiş, 512x512 simge, simge alanı doldurur, afişte ad) zaten karşılanıyordu. Ek olarak düzeltilen iki ret riski (telefonda davranış aynı):
  - TV-WB (TV'de tarayıcı açılmaz): YouTube koşulları, Gemini anahtar sayfası ve Play abonelik yönetimi TV'de adres/yol mesajı gösterir (`ExternalLink`); fragman TV'de yalnızca YouTube uygulamasında açılır.
  - TV-NP (arka planda medya kontrolü olmadan ses yok): TV'de uygulama arka plana geçince radyo duraklar (`RadioPlayerManager.pauseForBackground`). Arka planda çalma (PRO) yapılırken TV'de MediaSession bildirimi/kartı eklenince bu duraklatma kaldırılmalı.
  - Test `TvNoBrowserTest`. Play Console'da kullanıcı kontrol eder: Uygulama erişimi'nde deneme listesi talimatı (TV-G5) ve güncel TV ekran görüntüleri (TV-G4).

**`claude/akici-oynatma` (23 Ekim 2026; takılma ve donmaları azaltma; 25 Ekim'de 1.1.8 dalına birleştirildi). Herkese açık:**
- Donma bekçisi (`PlaybackStallGuard` + `StallWatchdog`): yayın 8 sn hiç ilerlemezse (veri gelmiyor, süre durdu ya da görüntü dondu) sessizce yeniden bağlanır; canlı en güncel noktadan, film/dizi kaldığı yerden. Veri yavaş da olsa geliyorsa karışmaz. Sarma ve yeniden bağlanmadan sonra 15 sn bekler. 3 denemede düzelmezse sonsuz dönen daire yerine "Yeniden dene" çıkar.
- Akıllı tampon (`AdaptiveLoadControl`): takılan yayında oynatma biraz daha fazla veriyle yeniden başlar (her takılmada +2,5 sn, en çok +7,5 sn; 3 dk sorunsuzsa başa döner).
- Bağlantı zaman aşımı 30 → 10 sn, okuma 30 → 15 sn. Tampon bellek sınırı uygulama payının 1/4'ü (16–64 MB; Mi TV Stick'te bellek taşmasına karşı).
- Gerçek oynatıcıyla stres testleri: `PlaybackResilienceTest`, `PlayerStallRecoveryTest` (test videosu `app/src/test/resources/player/`, sahte IPTV sunucusu `FakeStreamServer.kt`).

Zaten var olan, ücretsiz özellikler (rakipte PRO; tanıtımda öne çıkarılabilir):
- Kaldığın yerden devam et.
- Otomatik liste güncelleme.

Sonraya kalanlar:
- Canlı yayını durdurma ve geri sarma.
- Yapay zekâ "Şu an ne izlesem?".
- İzlerken açılan mini kanal listesi.
- Profiller ve cihazlar arası eşitleme.

**Spor modülü:** Yazıldı ve test edildi ama bu depoda değil. Yayından sonra **PRO özellik** olarak eklenecek (kullanıcı kararı, 16 Ekim 2026). Tüm kaynak kod ve ayrıntılı not (`kaynak/HAFIZA.md`) şu sayfada:
https://claude.ai/artifact/3v9FABLB6e6763tUZwYTPd

**Özellik havuzu (16 Ekim 2026, kullanıcı kararı; sırası henüz belli değil):**
Yayındaki uygulama bozulmasın diye ayrı dalda, ayrı modül olarak ve açma/kapama anahtarıyla yapılır; kullanıcı test edip onaylayınca birleştirilir. Önce yukarıdaki yol haritası önerilir.
**Sıra kararı (4 Kasım 2026, kullanıcı):** 1.1.8'den sonraki sürüme **yeni alt bar (telefon)** ve **Oyunlar (PRO)** girer. **Spor bekler** (büyük iş, sonraki sürümlere). Aynı sürüme ayrıca **Firebase (Analytics + Crashlytics)** ve **PRO'da 7 günlük ücretsiz deneme** girer (kullanıcı onayı, 4 Kasım). TV yayın akışı tablosu ve hızlı kanal değiştirme sonraki sürümü bekler. **Kesintisiz izleme de bu sürüme girer (kullanıcı kararı, 5 Kasım 2026; "çoğu IPTV kullanıcısı ister"):** donma bekçisi aynı yayına 3 denemede bağlanamazsa aynı kanalın başka sürümüne (HD/FHD/SD/yedek; `channelBaseName` mantığı) kendiliğinden geçer; Ayarlar'da açma/kapama anahtarı (varsayılan açık; telefon ve TV). Bugün bu geçiş YOK (yalnızca aynı yayına yeniden bağlanma). **Ücretsiz** (kullanıcı kararı, 5 Kasım: "her şeyi ücretli yapıp kullanıcıyı kaçırmayalım"; donma en büyük şikâyet, puan riski). **Başlama koşulu:** 1.1.8 Play'de onaylanınca, ayrı dalda (incelemedeki sürüme dokunulmaz). Firebase için kullanıcıdan `google-services.json` istenir; deneme Play Console'da abonelik teklifi olarak tanımlanır. **Ücret modeli (4 Kasım, kullanıcı kararı):** ücretsiz günlük 60 dk kalır; 7 günlük deneme yalnızca PRO aboneliğinde (aylık/yıllık). "60 dk'yı kaldırıp herkese 7 gün deneme, sonra tamamen ücretli" modeli değerlendirilip reddedildi (Play denemesi kart ister, ücretsiz rakipler, yeni uygulamada puan riski); 4–6 haftalık Firebase verisinden sonra yeniden bakılabilir.
**Çok platform kararı (8 Kasım 2026, kullanıcı):** 1.1.8 Play'de yayına girince **Samsung TV (Tizen)** sürümüne başlanır; sonra güncellemeler iki platforma birlikte verilir; zamanla Microsoft Store vb. Tizen Android çalıştırmaz: ayrı web uygulaması (HTML/JS, oynatıcı AVPlay); aynı web kodu LG webOS'a ve Microsoft Store'a (PWA) da uyarlanabilir. Samsung Checkout Türkiye'yi destekler (resmi tabloda TR/TRY, 96 ülke). Platformlar arası tek PRO için hesap sistemi + RevenueCat düşünülüyor (sunum: https://claude.ai/artifact/GeubteGbQb3rXPbPbYHV1x); RevenueCat'in Samsung Checkout için hazır desteği doğrulanmadı. Başlarken ilk iş: Samsung Seller Office'te bireysel hesapla yayın yapılabildiğini doğrula. Test cihazı: kullanıcının Samsung TV'si (model sorulacak). **Sıra (8 Kasım, kullanıcı):** önce Android'deki planlı sürüm (alt bar, Oyunlar, Firebase, deneme, kesintisiz izleme) biter; sonra Samsung, LG ve Windows'a (Microsoft Store) geçilir; ondan sonraki güncellemeler bütün platformlara birlikte. **Android ödeme sistemi (Play Billing) değişmez**; RevenueCat yalnızca Samsung/LG/Windows için. **PRO ve ödeme (kullanıcı kararı, 8 Kasım):** Android'de Play Billing değişmez (PRO Google hesabına bağlı, telefon + Android TV birlikte). Samsung, LG ve Windows'ta PRO **cihaz başına**; ödeme mağazada değil **bizim web sitemizde** (Paddle gibi Merchant of Record), TV'de cihaz kodu + QR, küçük sunucu (aday Supabase). Cihaz kimliği MAC değil (Wi-Fi/kablo farklı): Samsung DUID, LG cihaz kimliği. Hesap sistemi ve platformlar arası eşitleme yok. Ayrıntı ve başlamadan önceki kontroller: `docs/platformlar/cok-platform-plani.md`.
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
