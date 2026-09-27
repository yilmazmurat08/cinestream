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
