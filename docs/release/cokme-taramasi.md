# Yayın öncesi çökme taraması — 1.0.5 / 1.0.6

Tarih: 7–8 Ekim 2026 · Tetikleyen: 1.0.3'te görülen çökme (`SQLiteBlobTooBigException`, `IPTVDao.getAllItemsFlow`)

Bu belge, yayın öncesinde uygulamanın tamamında yapılan çökme taramasını özetler: neye bakıldı, ne bulundu,
nasıl düzeltildi ve nasıl doğrulandı. Her bulgu için **önce eski kodda hatayı yeniden üreten** bir test yazıldı,
sonra düzeltme yapıldı ve testin geçtiği gösterildi.

## 1. Çökmenin kendisi (1.0.5)

| | |
|---|---|
| Belirti | Açılışta ya da yenilemeden sonra "Uygulama beklenmedik şekilde kapandı": `Row too big to fit into CursorWindow requiredPos=6742, totalRows=1187` |
| Gerçek neden | Mesaj yanıltıcı: satır büyük değil. Ana sayfa tüm öğeleri okurken (birden çok 2 MB'lık okuma penceresi) Xtream yenilemesi filmleri **önce silip sonra ekliyordu**. Android sorguyu yeniden çalıştırdığında tablo küçülmüş oluyor ve okuma çöküyordu. |
| Ek sorun | Aynı yenileme **favorileri sıfırlıyordu**. Açılışta çalışan geçici bir test rutini de her seferinde tüm filmleri silip yeniden yazıyordu. |
| Düzeltme | Yenileme tek işlemde ve favorileri koruyarak yapılır. Büyük listeler tek tutarlı anlık görüntüden okunur. Okuma akışlarına güvenlik ağı eklendi. Geçici test rutini kaldırıldı. |
| Kanıt | `RefreshDuringReadTest`: eski kodda aynı istisna (`… totalRows=700`), yarım tablo okumaları ve favori kaybı; yeni kodda 3 turda da temiz. |

## 2. Taranan çökme türleri

| Tür | Kapsam | Sonuç |
|---|---|---|
| Tekrar eden liste anahtarı ("Key was already used") | 59 Lazy liste | 3 risk bulundu ve düzeltildi |
| Kaydedilemeyen ekran durumu (arka plana geçişte çökme) | 12 `rememberSaveable` | Güvenli |
| Yakalanmayan hata (ekran coroutine'leri, `LaunchedEffect`) | 19 `scope.launch` + suspend çağrılar | 5 TV okuması düzeltildi |
| Dış uygulama açma (`ActivityNotFoundException`) | Tüm `startActivity` ve sonuç başlatıcıları | 1 risk (fotoğraf seçici) düzeltildi |
| WebView iç işlem çökmesi | Fragman oynatıcı | 1 risk düzeltildi |
| PiP, ön plan hizmeti (Android 12/14), PendingIntent, alıcılar, alarmlar | Tümü | Güvenli |
| Arka plan iş parçacığından Toast, DataStore çoklu örnek, ana iş parçacığında veritabanı | Tümü | Güvenli |
| `!!`, boş listede `first()/[0]`, metinden sayıya çevirme | 5 + 57 + tümü | Güvenli |
| Oynatıcı parça seçimi, satın alma akışı | Tümü | Güvenli |

## 3. Bulgular ve düzeltmeler (1.0.6)

| # | Nerede | Ne oluyordu | Düzeltme | Test |
|---|---|---|---|---|
| 1 | Detay ekranı, oyuncu listesi | Aynı oyuncu adı iki kez gelince ekran çöküyordu | Adlar tekilleştirildi | `CrashAuditTest`, `PhoneSmokeTest` |
| 2 | Ayarlar > Profil > Fotoğraf (TV'de de) | Cihazda fotoğraf seçici yoksa (Android TV) uygulama kapanıyordu | Hata yakalanır, "Bu cihazda fotoğraf seçici bulunmuyor" uyarısı | `CrashAuditTest` |
| 3 | Fragman (WebView) | Bellek azalınca WebView'in iç işlemi kapanırsa Android tüm uygulamayı kapatıyordu | Olay karşılanır, ölü WebView kaldırılır, kapak görseli kalır | `CrashAuditTest` |
| 4 | TV: Filmler/Diziler, detay, Canlı TV girişi | Veritabanı okuma hatası (ör. TV kutusunda depolama doldu) ekranı çökertiyordu | Hata yakalanır, boş sonuçla devam | `CrashAuditTest` |
| 5 | Xtream dizileri: oynatıcı bölüm paneli, TV detay | Sağlayıcı aynı bölümü iki sezonda listeleyince çöküyordu | Bölüm bir kez eklenir (normal sezon öncelikli) | `CrashAuditTest` |
| 6 | Çoklu Ekran kanal seçici | Sağlayıcıda "Tümü" adlı kategori olunca çöküyordu | O kategori ayrıca listelenmez | `CrashAuditTest` |
| 7 | Vizyondaki filmler | Aynı film iki kez gelirse çökebilirdi (tedbir) | Tekilleştirildi | — |

Bilinçli olarak değiştirilmeyen: TV "benzer diziler" anahtarı. Kod izlendi, tekrarın bu yolda oluşamadığı görüldü.
Oluşamayan bir durum için değişiklik yapmak gereksiz risk olurdu.

## 4. Duman testi

`PhoneSmokeTest`, telefonun ana ekranlarını zorlayıcı veriyle açar ve her ekranda kaydırır, uygulamayı arka plana
alıp geri getirir: tekrar eden adlar, "Tümü" adlı kategori, çok uzun adlar, boş kategori, süresi 0 olan izleme kaydı.

- Ana ekranın 8 sekmesi (Tümü, Canlı, Filmler, Diziler, EPG, Radyo, Listem, Ayarlar)
- Film ve dizi detay ekranları
- Çoklu Ekran ve kanal seçici

Eski kodda detay ekranı çökmesini yakaladığı doğrulandı.

## 5. Sınırlar (dürüst not)

- Testler bilgisayarda Robolectric ile çalışır; **gerçek cihazda doğrulanmadı.** Kapalı testteki cihazlar asıl
  doğrulamadır.
- Play Console > Kalite > Android vitals > Çökmeler ve ANR'ler: testçilerde çökme olursa burada **okunabilir** hata
  kaydıyla görünür (paket, kod eşleme dosyasını içeriyor). Ekran fotoğrafı gerekmez.
- Robolectric'te pencere (Dialog) içindeki metin kutusu test ortamını hiç "durgun" saymıyor; bu uygulamadan bağımsız
  (yalın Compose ile kanıtlandı). Kanal seçici testinde kareler elle ilerletiliyor.
