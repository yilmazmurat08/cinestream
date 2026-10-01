# CineStream – Play Console yükleme rehberi

Bu rehber, uygulamayı Google Play'e ilk kez yüklerken Play Console'un sorduğu her bölüm için ne yazman / seçmen gerektiğini sırayla anlatır.

## 1. Hazır malzemeler

| Malzeme | Dosya | Play'in istediği |
|---|---|---|
| Uygulama paketi | CineStream-1.0.aab | İmzalı AAB |
| Uygulama simgesi | uygulama-simgesi-512.png | 512×512, 32 bit PNG |
| Öne çıkan görsel | one-cikan-gorsel-1024x500.jpg | 1024×500, JPG veya 24 bit PNG |
| Telefon ekran görüntüleri | telefon/01…07 (7 adet) | 2–8 adet, 9:16 |
| Android TV ekran görüntüleri | tv/02, 03, 06, 08, 10 (5 adet) | En az 1, 16:9 |
| Android TV banner'ı | tv/tv-banner-1280x720.png | 1280×720 |
| Mağaza metinleri | magaza-metinleri.md | Ad, kısa ve tam açıklama |
| İncelemeci için deneme listesi | demo-playlist.m3u (depoda) | Uygulama erişimi talimatı |
| Gizlilik politikası adresi | Google Dokümanlar "Web'de yayınla" linki | Herkese açık adres |

## 2. Uygulamayı oluştur

**Tüm uygulamalar > Uygulama oluştur**

- Uygulama adı: **CineStream: IPTV Oynatıcı**
- Varsayılan dil: **Türkçe – tr-TR**
- Uygulama mı, oyun mu: **Uygulama**
- Ücretsiz mi, ücretli mi: **Ücretsiz** (uygulama içi satın alma olsa da indirmesi ücretsiz)
- Beyanları onayla.

## 3. Ana mağaza girişi

**Büyüme > Mağazadaki varlık > Ana mağaza girişi**

- Uygulama adı, kısa açıklama, tam açıklama: `magaza-metinleri.md` dosyasındaki Türkçe metinler.
- İngilizce eklemek istersen: **Çeviri ekle > English (United States) – en-US**, İngilizce metinleri yapıştır.
- Uygulama simgesi: `uygulama-simgesi-512.png`
- Öne çıkan görsel: `one-cikan-gorsel-1024x500.jpg`
- Telefon ekran görüntüleri: `telefon` klasöründeki 7 görsel (sırasıyla).
- Tablet ekran görüntüleri: isteğe bağlı; boş bırakabilirsin.
- Android TV: banner olarak `tv-banner-1280x720.png`, ekran görüntüleri olarak `tv` klasöründeki 5 görsel.

**Mağaza ayarları**

- Uygulama kategorisi: **Video Oynatıcılar ve Düzenleyiciler**
- E-posta: **yilmazmurat08@gmail.com**
- Web sitesi ve telefon: isteğe bağlı, boş bırakabilirsin.

## 4. Uygulama içeriği (Politika ve programlar > Uygulama içeriği)

### Gizlilik politikası
Google Dokümanlar'da **Web'de yayınla** ile aldığın Gizlilik Politikası linkini yapıştır.

### Uygulama erişimi
Uygulama bir yayın listesi olmadan içerik göstermez; incelemecinin uygulamayı deneyebilmesi için talimat ver.

- Seçenek: **Bazı işlevlere erişim kısıtlanmıştır** (veya "Tüm işlevler veya bazı işlevler kısıtlanmıştır").
- **Talimat ekle**:
  - Ad: `Deneme yayın listesi`
  - Kullanıcı adı / şifre: boş bırak (gerekmiyor).
  - Diğer bilgiler (aşağıdakini olduğu gibi yapıştır):

```
CineStream is a media player and does not provide any content. To test it, use this demo playlist of freely licensed test streams and Blender Foundation open movies:

1. Open the app and choose "Phone" (or "TV" on Android TV).
2. On the setup screen, paste this link into the "M3U Playlist or Stream Link" field:
   https://raw.githubusercontent.com/yilmazmurat08/cinestream/main/play-store/demo-playlist.m3u
3. The Gemini API key field is optional; leave it empty.
4. Tap "Start the AI-Powered Experience". Live test streams appear under Live TV and three open movies under Movies.

The free version allows 60 minutes of viewing per day; PRO (Google Play subscription or one-time purchase) removes this limit.
```

### Reklamlar
**Hayır, uygulamam reklam içermiyor.**

### İçerik derecelendirmesi
Ankete başla, e-posta: yilmazmurat08@gmail.com, kategori: **"Yukarıdakilerin hiçbiri" / Diğer tüm uygulama türleri**.

Sorulara dürüst cevaplar:
- Şiddet, cinsellik, kumar, uyuşturucu vb. **uygulamanın kendi içeriğinde**: **Hayır** (uygulama kendi içeriğini sunmaz).
- Kullanıcılar birbiriyle iletişim kurabiliyor mu / içerik paylaşabiliyor mu: **Hayır**.
- Konum paylaşımı: **Hayır**.
- Dijital ürün satın alma: **Evet** (CineStream PRO).
- Kullanıcıların sınırsız internet içeriğine erişimi (tarayıcı benzeri) sorusu çıkarsa: **Evet** – kullanıcılar kendi ekledikleri listelerdeki içeriği izleyebilir.

Not: Son soruya "Evet" demek derecelendirmeyi yükseltebilir; bu normaldir ve dürüst cevap budur.

### Hedef kitle ve içerik
- Hedef yaş grupları: **18 ve üzeri** seçmeni öneririm. Uygulama, kullanıcının eklediği listelere göre yetişkin içerik gösterebildiği için 18 yaş altı gruplar seçilirse Google ek inceleme ister. (Hizmet Şartları'ndaki "en az 13 yaş" kuralıyla çelişmez; bu bölüm uygulamanın kime yönelik olduğunu sorar.)
- Uygulama çocukların ilgisini çekebilir mi: **Hayır**.

### Haber uygulaması
**Hayır.**

### Veri güvenliği
Genel sorular:
- Uygulamanız, gerekli kullanıcı veri türlerinden herhangi birini topluyor veya paylaşıyor mu: **Evet**
- Toplanan tüm kullanıcı verileri aktarım sırasında şifreleniyor mu: **Evet** (yapay zekâ istekleri HTTPS ile gider)
- Kullanıcıların verilerinin silinmesini isteyebileceği bir yol sunuyor musunuz: **Evet** (uygulama içinden ve uygulama verileri temizlenerek; ayrıca e-posta)
- Hesap oluşturma: **Uygulamam hesap oluşturmaya izin vermiyor**

Veri türleri – yalnızca şunu işaretle:
- **Uygulama etkinliği > Diğer kullanıcı tarafından oluşturulan içerik**
  - Toplanıyor: **Evet** · Paylaşılıyor: **Hayır**
  - Geçici olarak işleniyor mu: **Hayır**
  - Zorunlu mu: **İsteğe bağlı** (yalnızca yapay zekâ asistanı kullanılırsa)
  - Amaç: **Uygulama işlevselliği**
  - Açıklama (iç not): Yapay zekâ asistanına yazılan mesajlar, kullanıcının kendi Gemini API anahtarıyla Google Gemini'ye gönderilir.

İşaretleme gerekmeyenler: Konum, kişisel bilgiler (ad/e-posta yalnızca cihazda kalır, gönderilmez), finansal bilgiler (ödemeyi Google Play işler), cihaz kimlikleri, ses (konuşma tanımayı cihazın kendi hizmeti yapar), fotoğraflar, rehber, uygulama bilgisi ve performansı (hata kayıtları cihazdan çıkmaz).

### Devlet uygulaması / finansal özellikler / sağlık
Hepsine **Hayır** / **Uygulamam bu özelliklerin hiçbirini içermiyor**.

## 5. Dahili test ve ödeme ürünleri

1. **Test > Dahili test > Yeni sürüm oluştur**
2. **Play Uygulama İmzalama** sorulursa **Google tarafından oluşturulan anahtarı kullan** seçeneğini kabul et.
3. `CineStream-1.0.aab` dosyasını yükle, sürüm notu: `İlk sürüm`.
4. **Test kullanıcıları** sekmesinde bir e-posta listesi oluştur, kendi adresini ekle, katılım linkiyle uygulamayı kur.
5. **Para kazanma > Abonelikler**:
   - `cinestream_monthly`: temel plan, aylık otomatik yenilenen, **₺80**
   - `cinestream_yearly`: temel plan, yıllık otomatik yenilenen, **₺800**
6. **Para kazanma > Uygulama içi ürünler**: `cinestream_lifetime`, tek seferlik, **₺1500**. Üç ürünü de **Etkinleştir**.
7. **Ayarlar > Lisans testi** bölümüne kendi Gmail adresini ekle (test satın almaları gerçek para çekmez).

## 6. Android TV'de yayınlamak

**Test ve yayınla > Gelişmiş ayarlar > Form faktörleri > Android TV ekle**. TV görselleri yüklendikten sonra Google ayrıca TV kalite incelemesi yapar; bu birkaç gün sürebilir.

## 7. Yayına alma

Dahili testte her şey çalışınca: **Üretim > Yeni sürüm oluştur**, aynı AAB'yi seç ve incelemeye gönder. İlk inceleme genellikle birkaç gün sürer.

> Yeni geliştirici hesaplarında Google bazen üretime geçmeden önce **kapalı test** şartı koşar (örneğin en az 12 test kullanıcısının 14 gün boyunca uygulamayı denemesi). Play Console bu şartı gösterirse önce **Kapalı test** kanalı açman gerekir.
