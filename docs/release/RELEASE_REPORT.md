# CineStream — Yayın Öncesi Denetim Raporu

Tarih: 6 Ekim 2026 · Dal: `release/play-audit` (başlangıç etiketi `pre-audit`) · Sürüm: **1.0.2 (3)**

## 1. Sonuç

**Dahili ve kapalı test için yüklemeye hazır: Evet.**
**Üretim (herkese açık) için: Henüz değil.** Kalan engeller:

1. Yeni kişisel hesap şartı: 12 testçi ile 14 gün kapalı test (Google'ın kuralı).
2. Yapay zekâ "Bildir" özelliğinin uygulama içinden gönderimi için Google Formu kurulmalı. Şu an e-posta taslağıyla gidiyor; politika, uygulamadan çıkmadan gönderim istiyor.
3. Play Console'da ödeme ürünleri oluşturulup test kartıyla denenmeli.
4. Cihazda yapılması gereken manuel testler (bölüm 4); burada otomatik doğrulanamadı.

## 2. Bulgular ve yapılanlar

Bulguların ayrıntısı: `01-bulgular.md`.

| ID | Öncelik | Sorun | Yapılan | Commit | Doğrulama |
|---|---|---|---|---|---|
| P0-01 | P0 | Klasörlerde Netflix/Disney+/BluTV/Exxen/Prime/Apple etiketleri | Nötr, türe göre etiketler; 32 marka metni silindi | 257d5a6 | Derleme + test ✅ |
| P0-02 | P0 | Giriş sloganında Apple ve Netflix | Markasız slogan (TR/EN) | 257d5a6 | ✅ |
| P0-03 | P0 | "Sinemada Bu Hafta"da listedeki vizyon filmi oynatılıyordu | Satır yalnızca bilgi ve fragman; rozet ve oynatma kaldırıldı | 257d5a6 | ✅ |
| P0-04 | P0 | Örnek olarak gerçek kanal listesi adresi | `example.com` yer tutucusu | 257d5a6 | ✅ |
| P0-05 | P0 | AI yanıtlarında "Bildir" yok | Telefon/TV sohbeti, dizi özeti, detay özeti, oynatıcı bülteni: AI notu + Bildir (gerekçe + not), bildirilen yanıt gizlenir | cd27776 | Test ✅; cihazda **doğrulanmadı** |
| P0-06 | P0 | Gemini'de güvenlik ayarı ve konu sınırı yok | `GeminiApi`: safetySettings (4 kategori), film/dizi sistem talimatı | cd27776 | Test ✅; yanıt kalitesi cihazda **doğrulanmadı** |
| P0-07 | P0 | "İçerik sağlamaz" uyarısı görünmüyordu | Giriş ekranı + Ayarlar > Yasal (TR/EN) | 257d5a6 | ✅ |
| P0-08 | P0 | Başka IPTV uygulamalarının kimliğiyle istek | Tek kimlik `CineStream/<sürüm>`, yedek genel tarayıcı; sahte Chrome başlıkları kaldırıldı | 257d5a6 | Test ✅; **senin sağlayıcınla doğrulanmadı** |
| P0-09 | P0 | Film adları kanal/marka adına çevriliyordu | Ada göre yeniden adlandırma kaldırıldı | 257d5a6 | Test ✅ |
| P0-10 | P0 | 16 KB hizalaması kanıtlanmamıştı | `zipalign -c -P 16` başarılı; tüm .so LOAD hizalaması 16384 | — | ✅ |
| P1-02 | P1 | Model adları dağınık, 1.5 kullanımdan kalkmış | `gemini-2.5-flash` + yedek `gemini-3.5-flash` (ai.google.dev'de kararlı, kapanış tarihi yok) | cd27776 | ✅ |
| P1-03 | P1 | Anahtar URL'de | `x-goog-api-key` başlığı | cd27776 | Test ✅ |
| P1-04 | P1 | Gemini anahtarı şifresiz | Android Keystore AES-GCM; eski değerler okunur; "Anahtarı sil" | e900db4 | Test ✅ (Keystore'un kendisi cihazda **doğrulanmadı**) |
| P1-05 | P1 | Veritabanı buluta yedekleniyordu | Yedek ve cihaz aktarımından çıkarıldı | d728d48 | ✅ |
| P1-06 | P1 | Varsayılan avatar gerçek bir kişinin fotoğrafı | Paket içi avatar; eski kayıtlar da çevrilir | d728d48 | ✅ |
| P1-07 | P1 | Kullanılmayan Firebase | Kaldırıldı; `READ_GSERVICES` izni gitti | d728d48 | ✅ |
| P1-08 | P1 | Süre dolunca oynayan içerik kesiliyordu | Yalnızca yeni içerikte kontrol; son 15 dk rozeti | d728d48 | Cihazda **doğrulanmadı** |
| P1-09 | P1 | Yetişkin klasörleri varsayılan açık | Varsayılan kilitli; varsayılan PIN ipucu | d728d48 | ✅ |
| P1-10/11 | P1 | "Sınırsız eğlence" sloganı, sahte Google düğmesi, PRO metni | Nötr metinler | 257d5a6 | ✅ |
| P1-12 | P1 | Canlı pencere hatası | `seekToDefaultPosition()+prepare()` | d728d48 | Cihazda **doğrulanmadı** |
| — | P1 | Sonsuz "yükleniyor" | 20 sn içinde başlamazsa hata + Yeniden dene | d728d48 | Cihazda **doğrulanmadı** |
| P1-15 | P1 | Lint hataları derlemeyi durdurmuyordu | `abortOnError` ve `checkReleaseBuilds` açık | e900db4 | ✅ |
| P2-02/03 | P2 | YouTube şartları, sürüm, tüm verileri sil | Ayarlar > Yasal kartına eklendi | 257d5a6 | ✅ |

### Ertelenenler (bu sürümde yapılmadı)

Bu maddeler büyük ve riskli değişiklikler. Bugünkü yüklemeyi tehlikeye atmamak için sonraki sürüme bırakıldı:

| ID | Konu | Neden ertelendi |
|---|---|---|
| P1-01 | Telefonda tüm dizi satırlarının belleğe alınması (sayfalama) | Telefonun ana veri akışı; aşamalı yeniden yazım ve cihaz testi gerekiyor. Mevcut "uzun alanları kırp ve tekrar dene" koruması çökmeyi önlüyor. TV modu zaten sayfalı. |
| P1-12 (kısmi) | Media3 1.4.1 → 1.8.x ve MediaSession (kilit ekranı, medya tuşları) | Oynatıcı çekirdeği; geniş test gerekiyor. |
| P1-13 | TextureView → SurfaceView | Zoom/görüntü oranı ve PiP davranışını değiştirebilir. |
| P1-14 | Yerel M3U dosyası ekleme | Yeni özellik. |
| P1-16 | `collectAsStateWithLifecycle` geçişi (168 yer) | Mekanik ama arka planda oynatmayı etkileyebilir. |
| — | M3U `#EXTVLCOPT` / `http-user-agent` desteği | Ayrıştırıcıda yeni alan ve veritabanı değişikliği gerekiyor. |
| P2-01 | Kullanıcı sertifikalarına güven | Reklam engelleyici/VPN kullanıcıları için bilinçli karar; dosyada gerekçeli. |

## 3. Ölçümler

| Ölçüm | Önce (`pre-audit`) | Sonra |
|---|---|---|
| Birim testleri | 179 geçti | **185 geçti, 0 hata** (+6 denetim testi) |
| lintRelease | 0 hata, 168 uyarı (hatalar derlemeyi durdurmuyordu) | **0 hata**, 171 uyarı (hatalar artık derlemeyi durdurur) |
| İzinler | `READ_GSERVICES` dahil | `READ_GSERVICES` kaldırıldı |
| 16 KB | doğrulanmamış | ✅ |
| AAB boyutu, SHA-256 | — | `surum-teslim.md` |
| Soğuk açılış, içe aktarma süresi | ölçülemedi (emülatör yok) | **doğrulanmadı** |

## 4. Senin yapman gerekenler

1. `play-console-kontrol-listesi.md`'deki maddeler: mağaza adı, kalan üç beyan, sürüm yükleme, ürünler, TV form faktörü.
2. Google Dokümanlar'daki gizlilik politikasını yeni metinle güncelle (link değişmez).
3. Google Formu oluştur ve linkini bana gönder ("Bildir" uygulama içinden gitsin).
4. Bu dalı main'e birleştirmem için onay ver ("PR aç ve main'e birleştir"); deneme listesi linki main'den okunuyor.
5. **Cihazda manuel test (doğrulanmadı):**
   - Kendi listenle canlı TV, film ve dizi oynat. Yeni uygulama kimliğiyle yayınlar açılmalı; açılmazsa hemen bana yaz.
   - Bir AI yanıtında Bildir → gerekçe seç → Gönder. Yanıt gizlenmeli, e-posta taslağı açılmalı.
   - Yetişkin klasörüne gir. PIN istemeli; varsayılan 0000 ipucu görünmeli.
   - Ayarlar > Yasal > Tüm verileri sil. Onaydan sonra uygulama kapanıp sıfırlanmalı.
   - Canlı yayını 5 dk duraklat, devam et. Kendi kendine canlı noktaya gelmeli.
   - Ücretsiz sürede (sınırı test etmek için `qa` değil mağaza sürümü) son 15 dk rozeti görünmeli.
   - TV'de yalnızca kumandayla: Bildir düğmesine odak gelmeli ve pencere kumandayla kullanılabilmeli.
6. Keystore ve şifresini Drive'da yedekle.

**Önemli uyarı:** Yayın sürümünü ve sonraki değişiklikleri bu depodan sürdür. Proje AI Studio'ya geri taşınırsa, eski önbellekten üretilen dosyalar bu düzeltmeleri sessizce geri alabilir.

## 5. Riskler ve yol haritası

- **Sağlayıcı uyumu (P0-08):** Bazı IPTV sağlayıcıları yalnızca bilinen oynatıcı kimliklerine izin veriyor olabilir. Sorun çıkarsa M3U'daki `http-user-agent` desteği öncelikli eklenmeli (kullanıcı kendi sağlayıcısının istediği kimliği belirler).
- **Bellek (P1-01):** Çok büyük dizi kataloğu olan hesaplarda eski telefonlarda yavaşlık görülebilir.
- **Yol haritası:** Catch-up/timeshift, tam ekran EPG ızgarası, Chromecast, MediaSession, yerel dosya, Baseline Profile + Macrobenchmark.
