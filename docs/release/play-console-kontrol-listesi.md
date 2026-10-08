# Play Console Kontrol Listesi (senin yapacakların)

Sürüm: **1.0.8 (versionCode 9)** · Dosya: `CineStream-1.0.8.aab`

## Bugün (dahili/kapalı test için)

- [ ] **Mağaza adı:** Ana mağaza girişinde adı **CineStream AI TV** yap; kısa ve tam açıklamayı `play-store/magaza-metinleri.md`'den yeniden yapıştır (sorumluluk cümlesi eklendi).
- [ ] **Gizlilik politikası:** Google Dokümanlar'daki belgeyi yeni metinle güncelle (`gizlilik-politikasi.html` / uygulama içi metinle aynı; link değişmez).
- [ ] **Uygulama içeriği** (✅ = tamamlandı):
  - [x] Gizlilik politikası URL'si
  - [x] Uygulama erişimi (`uygulama-erisimi.md`)
  - [x] Reklamlar: Hayır
  - [x] İçerik derecelendirmesi (`icerik-derecelendirme.md`)
  - [x] Hedef kitle: 18+, çocuklara yönelik değil
  - [x] Veri güvenliği (`data-safety.md`)
  - [x] Finansal özellikler: Yok
  - [ ] Reklam kimliği: **Hayır**
  - [ ] Sağlık: **Uygulamamda sağlık özelliği yok**
  - [ ] Ön plan hizmeti: **Medya oynatma**, video: `https://youtube.com/shorts/3RqElCEW86M`
  - [ ] Haber uygulaması: Hayır · Devlet uygulaması: Hayır
- [ ] **Dahili test > Yeni sürüm:** `CineStream-1.0.2.aab`, sürüm notu `surum-notlari.md`'den.
- [ ] **Deneme listesi:** İncelemeye göndermeden önce bu dal main'e birleştirilmeli (link main'den okunuyor).

## Satın alma (kapalı test sırasında)

- [ ] Ödeme profili
- [ ] Abonelikler: `cinestream_monthly` (temel plan `aylik`, ₺80), `cinestream_yearly` (temel plan `yillik`, ₺800) → Etkinleştir
- [ ] Uygulama içi ürün: `cinestream_lifetime` (₺1500) → Etkinleştir
- [ ] Ayarlar > Lisans testi: kendi Gmail'in ve testçilerin
- [ ] Dahili testte test kartıyla satın alma ve iptal denemesi

## Yapay zekâ "Bildir" için Google Formu ✅ (1.0.7)

- [x] "Cinestream Ai bildirimleri" formu herkese açık (oturum açma istemez); bildirimler uygulamadan çıkmadan gönderilir.
- [x] Deneme gönderimi 8 Ekim 2026'da yapıldı ("Your response has been recorded"); formun Yanıtlar sekmesindeki "TEST" kaydı silinebilir.
- [ ] Google Dokümanlar'daki gizlilik politikası: yapay zekâ bildirimleri paragrafını `gizlilik-politikasi.html` / `privacy-policy.html` ile aynı yap (Google Formlar cümlesi eklendi, tarih 8 Ekim 2026).
- Not: Formun ayarlarını değiştirme ("1 yanıtla sınırla", "E-posta adreslerini topla: Doğrulandı" ya da yanıt verenleri kısıtlamak oturum açmayı zorunlu kılar ve bildirimler yeniden e-postaya düşer).

## Android TV

- [ ] Test edin ve yayınlayın > Gelişmiş ayarlar > Form faktörleri > **Android TV ekle**
- [ ] TV banner (1280×720) ve 5 TV ekran görüntüsü (`play-store/gorseller/tv/`)
- [ ] TV kalite incelemesi ayrıca yapılır (birkaç gün)

## Üretime geçiş

- [ ] Kişisel hesap (13 Kasım 2023 sonrası): **en az 12 testçi, 14 gün kesintisiz kapalı test**, sonra üretim erişimi başvurusu
- [ ] Ön lansman raporunu (pre-launch report) incele; çökme/erişilebilirlik uyarılarını bana ilet
- [ ] Keystore (`cinestream-upload.jks`) ve şifresini Google Drive'da "Keystore Dosyaları/CineStream" klasöründe yedekle
