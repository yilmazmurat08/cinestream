# CineStream — TV Modu: kök nedenler ve plan

Tasarım görselleri (`tasarim/tv-ana-sayfa.jpg`, `tasarim/tv-canli.jpg`) depoda bulunamadı; ekranlar talimattaki
yazılı tarife göre yapıldı. Görseller eklenince birebir karşılaştırılıp inceltilecek.

## 7 sorunun kök nedenleri (koddan doğrulandı)

1. **Film/dizi kartlarında aşağı inilemiyor.** Klasör ızgarası (`FolderGridScreen` → `LazyVerticalGrid`) ana
   sayfanın içinde, ekranın altına *binen* yüzen alt navigasyon çubuğunun arkasında duruyor. Kumandada Aşağı'ya
   basınca odak arama algoritması geometrik olarak en yakın aday olan alt çubuğu seçiyor; odak ızgaradan çıkıp
   alt çubuğa atlıyor, ızgara bir sonraki satıra kaymıyor. Ayrıca kartın içindeki kalp ikonu ayrı bir odak durağı.
2. **Dizi posterleri çok büyük.** TV'de sütun sayısı telefonla aynı formülle (ekran genişliğinden) hesaplanıyor
   (`WindowSizeUtils`), TV için ayrı bir poster ızgarası yok; dizi kartları büyük yatay kart olarak çiziliyor.
3. **Ana sayfa carousel'i titriyor.** Öne Çıkan alanı bir `HorizontalPager`; sayfalarında odaklanabilen
   "Oynat/Detay" düğmeleri var ve yan sayfalar da (beyondViewportPageCount=1) çiziliyor. Kumandayla odak bu
   düğmelere girince: otomatik kaydırma sayfayı değiştiriyor → odaklı düğme görünümden çıkıyor → odak sistemi
   onu geri getirmek için sayfayı geri kaydırıyor (bring-into-view) → sayfa değiştiği için zamanlayıcı yeniden
   başlıyor; iki kaydırma birbirini tetikliyor. **Telefonu etkilemez:** dokunmatik ekranda odak olmadığı için
   döngü oluşmaz. TV modunda kayan carousel hiç kullanılmayacak.
4. **Seçili poster belli olmuyor.** Film kartı `combinedClickable`, dizi kartı düz `clickable` kullanıyor;
   TV odak göstergesi (`TvFocusIndication`) bağlı değil. Kartın büyümesi odağa değil fare "hover"ına bağlı.
5. **Aşağı inerken aşağı çek-yenile devreye giriyor.** Tüm ana sayfa bir `PullToRefreshBox` içinde. Odak
   değişince liste odaklı öğeyi göstermek için kendini kaydırıyor (bring-into-view); en üste yakın kaydırmalarda
   artan hareket iç içe kaydırma (nested scroll) üzerinden yenileme göstergesine "çekme" olarak gidiyor.
   TV'de dokunma olmadığı için bu bileşene gerek yok.
6. **Ayarlar kart düzeninde.** `SettingsScreen` bölümleri kart/ızgara ve açılır menüler halinde; kumanda için
   tek sütunlu, sırayla gezilen bir liste değil.
7. **Canlı yayında kanal değiştirilemiyor, alt ikonlara erişilemiyor.** Oynatıcıda kanal değiştirme sadece
   CH+/CH− (ve PageUp/Down) tuşlarına bağlı; Mi TV kumandası gibi çoğu kumandada bu tuşlar yok, Yukarı/Aşağı
   kanal değiştirmiyor. OK ile kontroller açılınca odak kontrol düğmelerine taşınmıyor, oynatıcının kök
   kutusunda kalıyor; yön tuşları düğmelere ulaşamıyor.

## Mimari

- Ayar: `SettingsRepository`'de `view_mode` (`PHONE` / `TV`, boşsa sorulur). Mevcut DataStore yöntemi.
- `MainActivity`: açılış animasyonundan sonra mod seçilmemişse `ui/tv/ModeSelectionScreen`; TV modunda ana
  ekran yerine `ui/tv/TvApp` (kendi geri yığınıyla TV ekranları). ViewModel, depo, Room ve oynatıcı mantığı ortak.
- TV ekranları `ui/tv/` altında ayrı dosyalar; `androidx.tv:tv-material` bileşenleri; cam/renkler mevcut
  temadan (`DeepPurpleBg`, `MidPurpleBg`, `AccentNeonPurple`, telefondaki çubuğun cam değerleri), eşleşmeyen
  tonlar `ui/tv/TvTheme.kt`'de TV'ye özel değer.
- Telefon arayüzündeki tek değişiklik: Ayarlar'daki "Görünüm modu" satırı.

## Aşamalar

1. Mod seçimi (ilk açılış, otomatik algılama, kayıt, Ayarlar satırı, manifest).
2. TV ana sayfası (üst bar, Top Shelf hero, 5 cam kart, İzlemeye Devam Et, çift Geri ile çıkış).
3. Gemini asistanı (TV sohbet, hazır sorular, mikrofon, öneri açma).
4. Filmler/Diziler (kategori listesi + 6 sütun poster ızgarası, sayfalı yükleme) ve detay.
5. Canlı TV ve oynatıcı (kanal paneli, kontrol çubuğu, kanal bilgisi, tuş eşlemeleri, ses).
6. Ayarlar (alt alta liste).

## Özellik eşitliği: telefondaki her özelliğin TV karşılığı

Kural: telefondaki hiçbir özellik TV'de eksik kalmaz; yalnızca arayüz kumandaya uyarlanır. Veri, ViewModel ve
iş mantığı ortaktır (aynı fonksiyonlar çağrılır, yeniden yazılmaz). Durum: ✅ yapıldı · ⏳ planlandı (aşama) · ❓ onay gerekiyor.

### Açılış ve hesap
| Telefon | TV karşılığı | Durum |
|---|---|---|
| Açılış animasyonu | Aynısı | ✅ |
| Görünüm modu seçimi | Aynı ekran, TV kartı hazır odaklı | ✅ Aşama 1 |
| Giriş / kurulum, liste ekleme (M3U, Xtream) | Aynı ekran + telefonla QR giriş (mevcut) | ✅ (mevcut) |
| Hata / çökme raporu ekranı | Aynısı | ✅ (mevcut) |

### Ana sayfa (telefon: alt çubuk + raflar)
| Telefon | TV karşılığı | Durum |
|---|---|---|
| Alt çubuk: Ana sayfa, Filmler, Diziler, Canlı TV, Listem, Profil | 5 cam kart (Canlı TV, Filmler, Diziler, Kaydedilenler, Profil/Ayarlar) | ✅ Aşama 2 |
| Öne çıkan carousel | Hero (Top Shelf), odaklanan karta göre | ✅ Aşama 2 |
| İzlemeye devam et, Sıradaki bölüm | Alttaki "İzlemeye Devam Et" çubuğu | ✅ Aşama 2 |
| Arama + AI arama | Üst bardaki arama (aynı arama penceresi) | ✅ Aşama 2 |
| Arama geçmişi rafı | Telefonda ana sayfada; TV arama penceresinde yok | ❓ |
| ✨ AI asistan kartı | Üst bardaki ✨ ve "Asistana sor" | ✅ Aşama 2–3 |
| Top 10 filmler / diziler | Filmler / Diziler kategori listesinin en üstünde "Top 10" | ✅ Aşama 4 |
| En yüksek puanlılar (Efsaneler) | Filmler / Diziler kategori listesinde özel bölüm | ✅ Aşama 4 |
| Tür rafları (Korku, Komedi, Dram…) | Filmler kategori listesinde özel bölümler | ❓ (sağlayıcı kategorileri zaten listede) |
| Favori rafı / İzleme Listem | Kaydedilenler: sekmeler + 6 sütun ızgara; OK aç, basılı tut kaldır | ✅ Aşama 4 |
| Top 10 kanallar, Şimdi yayında | Canlı TV kanal panelinde EPG "şimdi" satırı; kategori listesi | ❓ (ayrı bölüm gerekirse) |
| Radyo yayınları | Canlı TV kategori listesinde "Radyo" bölümü | ❓ |
| Canlı kanalı kaydetme | Kontrol çubuğunda "Kaydet" | ✅ Aşama 5 |
| Fragman kutusu | Detay ekranında "Fragman" düğmesi + Filmler'de özel bölüm | ❓ |
| Günün seçimi (film düellosu) | Filmler kategori listesinde özel bölüm (iki film yan yana) | ❓ |
| AI Sinema Bülteni | Filmler kategori listesinde özel bölüm | ❓ |
| Çoklu ekran (MultiScreen) | Canlı TV kontrol çubuğunda "Çoklu ekran" düğmesi | ❓ |

### Asistan
| Telefon | TV karşılığı | Durum |
|---|---|---|
| Sohbet (aynı Gemini) | Büyük yazılı cam ekran, kaydırılabilir mesajlar | ✅ Aşama 3 |
| Sahneden film bulma + kütüphane eşleştirme | Aynı mantık; ana yol sesli giriş ("Sahnesini anlatayım, filmi bul"); kart + otomatik odak; yoksa "kütüphanende yok" | ✅ Aşama 3 |
| Yazarak sorma | Ekran klavyesi + sesle sor düğmesi (ses tanıma yoksa gizli) | ✅ Aşama 3 |
| Geçmiş sekmesi (aç, tekrar sor, sil, tümünü temizle) | Sol paneldeki "Geçmiş" | ✅ Aşama 3 |
| Sohbeti temizle | Sol panelde | ✅ Aşama 3 |
| Anahtar yoksa | Telefonla QR giriş veya Ayarlar | ✅ Aşama 3 |

### Filmler / Diziler / Detay / Kişi
| Telefon | TV karşılığı | Durum |
|---|---|---|
| Klasör ızgarası, klasör içinde arama | Solda kategori listesi + 6 sütun poster ızgarası (veritabanından sayfa sayfa); arama üst bardan | ✅ Aşama 4 |
| Kilitli (yetişkin) klasörler, ebeveyn PIN'i | Kilit simgeli kategori; aynı PIN penceresi | ✅ Aşama 4 |
| Favoriye ekle/çıkar | Detay ekranındaki düğme (ızgarada kalp odak durağı yok) | ✅ Aşama 4 |
| Film detayı: izle, fragman, konu, puan, tarih, AI özet (spoilersız), benzer yapımlar | TV detay ekranı; ilk odak "İzle" | ✅ Aşama 4 |
| Dizi detayı: sezonlar, bölümler, izlendi işareti, önceki bölümlerin AI özeti | Sezon/bölüm satırları kumandayla; "Devam et" | ✅ Aşama 4 |
| Oyuncular ve yönetmen, kişi detayı, filmografi + kütüphane eşleşmesi | Yuvarlak fotoğraf satırı (yönetmen başta); kişi sayfası 6 sütun, kütüphanedekiler başta; eşleştirme veritabanında sorguyla | ✅ Aşama 4 |

### Canlı TV ve oynatıcı
| Telefon | TV karşılığı | Durum |
|---|---|---|
| Kanal listesi (aynı klasör), önceki/sonraki kanal | Sol cam kanal paneli (Sol: kategoriler), Yukarı/Aşağı, CH+/CH−, numara tuşları | ✅ Aşama 5 |
| EPG (şimdi/sonra) | Kanal bilgisi kartı + ilerleme çubuğu; panelde şimdiki program | ✅ Aşama 5 (tam EPG ızgarası ❓) |
| Oynat/duraklat, ±10 sn, baştan başlat, sonraki/önceki bölüm | Alt cam kontrol çubuğu; Sol/Sağ ±10 sn; medya tuşları | ✅ Aşama 5 |
| Ses ve altyazı seçimi, görüntü oranı | Kontrol çubuğunda | ✅ Aşama 5 |
| Ses seviyesi | Ses çubuğu (STREAM_MUSIC; sabit seslilerde gizli) | ✅ Aşama 5 |
| Kaldığın yerden devam sorusu (AI özetle), yayın hatası ekranı | Aynısı (canlıda sorulmaz) | ✅ Aşama 5 |
| Tam ekran / küçült, PiP | TV zaten tam ekran; PiP destekleyen TV'de aynı | ✅ Aşama 5 |
| Parlaklık kaydırma, ekran kilidi | Dokunmatik özelliği; TV'de karşılığı yok | ❓ |

### Ayarlar
| Telefon | TV karşılığı | Durum |
|---|---|---|
| Profil, PRO, listeler, EPG, dil, görünüm modu, tema, oynatıcı, veri/bellek (önbellek, çökme günlükleri, hakkında), altyazı, güvenlik (PIN), Gemini anahtarı | Dikey liste (ikon, başlık, sağda değer) + sağda telefondaki kartın aynısı | ✅ Aşama 6 |
| Ekran yönü | Listede var (telefondaki kart); TV'de anlamsız (hep yatay) | ❓ gizlensin mi |
| PRO | Üst bardaki PRO (Google Play ödemesi "Paywall kur" denince) | ✅ düğme |

### ❓ Onay gerekenler (sana sorulacak)
1. Radyo, Fragman kutusu, Günün seçimi, AI Sinema Bülteni: önerilen yerler yukarıda (kategori listelerinde özel bölüm).
2. Çoklu ekran: Canlı TV kontrol çubuğunda düğme olarak.
3. Parlaklık kaydırma ve ekran kilidi: TV'de gizlensin mi (dokunmatik özelliği)?
4. Ekran yönü ayarı: TV'de gizlensin mi?
