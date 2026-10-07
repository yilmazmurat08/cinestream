# Play Console Kontrol Listesi (senin yapacakların)

Sürüm: **1.0.3 (versionCode 4)** · Dosya: `CineStream-1.0.3.aab`

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

## Yapay zekâ "Bildir" için Google Formu (yayından önce)

Şu an bildirimler, form tanımlı olmadığı için hazır doldurulmuş e-posta ile gönderiliyor. Politika uygulamadan çıkmadan gönderim istediği için üretime geçmeden önce:

1. forms.google.com'da "CineStream AI bildirimleri" adında bir form aç.
2. Dört adet **kısa yanıt** sorusu ekle: Gerekçe, Not, Yanıt, Sürüm.
3. Formun "Önceden doldurulmuş bağlantı al" seçeneğiyle linki bana gönder. Alan kimliklerini çıkarıp uygulamaya koyarım; sonraki sürümle bildirim uygulama içinden gider.

## Android TV

- [ ] Test edin ve yayınlayın > Gelişmiş ayarlar > Form faktörleri > **Android TV ekle**
- [ ] TV banner (1280×720) ve 5 TV ekran görüntüsü (`play-store/gorseller/tv/`)
- [ ] TV kalite incelemesi ayrıca yapılır (birkaç gün)

## Üretime geçiş

- [ ] Kişisel hesap (13 Kasım 2023 sonrası): **en az 12 testçi, 14 gün kesintisiz kapalı test**, sonra üretim erişimi başvurusu
- [ ] Ön lansman raporunu (pre-launch report) incele; çökme/erişilebilirlik uyarılarını bana ilet
- [ ] Keystore (`cinestream-upload.jks`) ve şifresini Google Drive'da "Keystore Dosyaları/CineStream" klasöründe yedekle
