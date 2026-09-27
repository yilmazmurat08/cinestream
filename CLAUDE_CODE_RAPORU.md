# CineStream — Kararlılık, Uyumluluk ve Temizlik Raporu

Dal: `claude/cinestream-stability-compat-cleanup-xlv565` (ana dala birleştirilmedi)
Tarih: 27 Eylül 2026

---

## 1. Özet

AI Studio'dan gelen proje depoya olduğu gibi eklendi (ilk commit), ardından her konu ayrı, küçük commit'lerle düzeltildi. Her commit'ten sonra `assembleDebug` ve `testDebugUnitTest` çalıştırıldı ve geçti (bunu otomatik yapan bir betik kullandım; derleme ya da test geçmezse commit engellendi).

| Ölçüt | Başlangıç | Son |
|---|---|---|
| `assembleDebug` | Gradle wrapper yoktu; wrapper eklenince geçti | Geçiyor |
| `testDebugUnitTest` | 37 test: 36 geçti, **1 gerçek hata** | **93 test, hepsi geçiyor** |
| `lintDebug` | **26 hata**, 268 uyarı | **0 hata**, 120 uyarı |
| `assembleRelease` (R8) | Geçiyordu ama **TMDB JSON modellerini siliyordu** (release'te bozuk çalışırdı) | Geçiyor, R8 uyarısı yok, modeller korunuyor |
| 16 KB sayfa hizası | libvlc'nin **tüm** 64-bit `.so` dosyaları 4 KB hizalı | Tüm 64-bit `.so` dosyaları 16 KB hizalı (`zipalign -P 16` doğrulandı) |

Bulduğum en önemli gerçek hatalar:

1. **Liste indirmesi yarıda koparsa kullanıcının tam listesi yarım listeyle değişiyordu.** (Mevcut bir test tam bunu yakalıyordu ve başarısızdı.)
2. **Yedekleme kuralları çalışmıyordu:** `domain="datastore"` Android'de geçersiz; Gemini anahtarı ve ebeveyn PIN'inin tutulduğu DataStore dosyaları buluta yedekleniyordu.
3. **Release derlemesinde TMDB modelleri R8 tarafından siliniyordu** (`MetadataEnricher$TMDBResponse` vb.); Gemini'nin kişi araması gibi çağrılar release'te çalışmazdı.
4. **Room:** `fallbackToDestructiveMigration()` her sürüm için açıktı; bir sonraki şema değişikliğinde listeler, favoriler, izleme geçmişi sessizce silinecekti.
5. **Tanılama dosyaları** her derlemede yazılıyordu; biri TMDB anahtarlarını düz metin olarak dosyaya yazıyordu, diğerleri Xtream kullanıcı adı/şifresi içeren adresleri.
6. **TV'de sıkışmalar:** kilitli oynatıcı kumandayla açılamıyordu, uzun basışla yapılan işlem (radyo favorisi) kumandayla yapılamıyordu, detay paneli kapanınca odak kayboluyordu.
7. Ön plan servisi `START_STICKY` olduğu için süreç öldürülünce **oynatıcısız, hayalet bir "oynatılıyor" bildirimi** kalabiliyordu.

Tasarım, renkler, yazı tipleri, ikonlar ve animasyonların görünüşü değişmedi; hiçbir özellik kaldırılmadı ya da eklenmedi. TMDB/YouTube anahtar ve eşleştirme mantığına, Gemini/Firebase davranışına ve PRO/paywall koduna dokunulmadı.

---

## 2. Ortam ve başlangıç sonuçları

- JDK 21 (OpenJDK 21.0.10), Android SDK: platform 36 ve 36.1, build-tools 36.0.0 (komut satırı araçlarıyla kuruldu).
- Gradle wrapper: **9.3.1** (AGP 9.1.1'in istediği en düşük sürüm). `gradlew`, `gradlew.bat`, `gradle/wrapper/*` eklendi.
- `local.properties` (SDK yolu) ve derleme sırasında üretilen `.env` commit'lenmedi (`.gitignore`'da).
- Ortam notları (projeye değil, bu bulut ortamına ait):
  - `dl.google.com` başta ağ politikasınca engelliydi; siz izin verince açıldı.
  - Maven Central zaman zaman "429 Too Many Requests" döndürdü. Sadece bu makinede, commit'lenmeyen bir Gradle init betiğiyle Google'ın Maven Central aynası eklendi.
  - Robolectric'in ilk denemesi aynı hız sınırı yüzünden bir test sınıfında başarısız oldu; sonraki çalıştırmalarda geçti (kod hatası değil).

**Başlangıç komutu:** `./gradlew assembleDebug testDebugUnitTest lintDebug`

- `assembleDebug`: geçti. Debug APK **219 MB** (AI Studio raporundaki "~87 MB" doğru değil; farkın büyük kısmı VLC'nin 4 işlemci mimarisi için native kütüphaneleri, ~189 MB sıkıştırılmamış).
- `testDebugUnitTest`: 37 testten 36'sı geçti; `testStagingTableRollback_WhenStreamFailsMidway_OldDataPreserved` gerçek bir hata yüzünden başarısızdı (bkz. 3.1).
- `lintDebug`: 26 hata (22 MissingTranslation, 3 FullBackupContent, 1 UnsafeOptInUsageError), 268 uyarı (137 UnusedResources, 3 Aligned16KB, …).

### AI Studio raporlarının doğrulanması

Raporlar okundu ve kodla karşılaştırıldı. Doğru olmayan ya da eksik iddialar:

| İddia | Gerçek durum |
|---|---|
| "minSdk 24" | `minSdk = 26` |
| "KSP 2.3.5" | `2.3.7` |
| "14 test geçti, kalan problem yok" | 37 test vardı, biri gerçek bir veri kaybı hatası yüzünden başarısızdı |
| "Bağlantı koparsa staging geri alınır, aktif veri %100 korunur" | Yanlıştı: hata yutuluyor ve yarım liste aktif tabloya taşınıyordu (düzeltildi) |
| "Moshi/Retrofit/Room/Media3 keep kuralları doğrulandı" | Room kuralı var olmayan pakete bakıyordu; `data.api` altındaki TMDB modelleri R8 tarafından siliniyordu (düzeltildi) |
| `HeroCarousel.kt`, `LiveTvScreen.kt`, `MediaCardPreviewDialog` | Bu dosyalar projede yok |
| Uyarlanabilir düzen tablosu (kart genişliği 135/170/210 dp vb.) | Koddaki değerler farklı (100/115/140/165 dp) |
| "Hardcoded TMDB anahtarı kaldırıldı" | `build.gradle.kts`'ten kaldırılmış, ama aynı anahtar `TMDBRepository.kt` içinde XOR+Base64 ile gömülü duruyor ve **raporun kendisinde düz metin olarak yazıyordu** (rapordan maskelendi, bkz. 3.4) |
| "Debug APK ~87 MB" | 219 MB |

Doğru bulunanlar: Coil güvenli OkHttp istemcisini kullanıyor; M3U satır satır ve 250'lik gruplarla işleniyor; 200 MB indirme sınırı var; WAL açık; oynatıcıda yaşam döngüsü gözlemcileri var.

### Plan (uygulanan sıra)

1. Kurulum ve başlangıç ölçümü → 2. Veri kaybı ve güvenlik hataları (M3U, yedekleme, sırlar, ölü kod) → 3. Room politikası → 4. R8/release → 5. Kararlılık (ana iş parçacığı, coroutine, oynatıcı yaşam döngüsü) → 6. Ölçek/insets/splash → 7. TV kumandası → 8. Testler → 9. Lint ve kaynak temizliği → 10. Rapor.

---

## 3. Konu konu yapılan değişiklikler

### 3.1 Veri kaybı: yarıda kopan liste indirmesi
- `data/repository/IPTVRepository.kt`: `parseAndSaveM3UFromReader` okuma hatasını yutup yarım "staging" tablosunu aktif tabloya taşıyordu. Artık staging temizlenip hata yeniden fırlatılıyor; `syncPlaylist` bu hatayı zaten yakalayıp eski kanalları koruyor. Boyut sınırı (200 MB) aşımı da aynı şekilde eski listeyi koruyor.

### 3.2 Yedekleme kuralları (F1)
- `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`: geçersiz `domain="datastore"` yerine `domain="file" path="datastore/"`. Gemini anahtarı, PIN ve diğer ayarlar artık gerçekten yedeklenmiyor; `crash_logs/` de hariç.

### 3.3 Ölü kod (E)
- Silindi: `IPTVViewModel.getOtherWorksByPersonName` → `TMDBRepository.findOtherWorksViaTmdb`; `IPTVViewModel.fetchPersonImageDirect` → `MetadataEnricher.fetchPersonImageUrlDirect` (anahtarları düz metin dosyaya yazıyordu); `IPTVViewModel.setTmdbApiKey` ve onun tek çağırdığı `SettingsRepository.setTmdbApiKey` (arayüzde hiçbir yerden çağrılmıyor; kayıtlı değer `tmdbApiKeyFlow` ile okunmaya devam ediyor).
- `tmp/update_metadata_enricher.py` silindi (tek seferlik AI Studio betiği).

### 3.4 Tanılama dosyaları, sırlar ve loglar (E, F1, F3)
- Yeni `util/DiagnosticLog.kt`: 16 tanılama yazısı (xtream_cover_diag, person_tmdb_diag, series_grouping_diag, …) artık **sadece debug derlemede** yazılıyor ve yazmadan önce `api_key/key/token/password/username` parametreleri, `Bearer` jetonları, JWT'ler, `AIza…` anahtarları ve Xtream `/live|movie|series/kullanıcı/şifre/` yolları maskeleniyor.
- Gerçek çökme kayıtları (`CrashRecoveryManager`, `crash_*.txt`) ve Ayarlar → "Çökme Günlükleri" ekranı korundu; içerikleri aynı şekilde maskeleniyor.
- `NetworkModule.kt`: debug'daki HTTP günlüğü `Authorization` başlığını ve `api_key` değerini maskeliyor.
- `proguard-rules.pro`: release'te `Log.d` ve `Log.v` çağrıları derlemeden çıkarılıyor (release dex'inde debug metinlerinin kalmadığı, `Log.e` metinlerinin kaldığı doğrulandı).
- `.gitignore`: `.env`, `local.properties`, `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `keystore.properties`, `google-services.json`.
- `FINAL_STABILITY_REPORT.md`: raporda düz metin yazan TMDB anahtarı maskelendi. Bu dal henüz hiç push'lanmamıştı; ilk commit dahil geçmiş yeniden yazıldı, böylece düz metin anahtar hiçbir commit'te yer almıyor (tek fark bu satır, doğrulandı).

### 3.5 Room (A)
- `data/db/AppDatabase.kt`: `DATABASE_VERSION = 10` temel alındı, `exportSchema = true` (şema: `app/schemas/.../10.json`). `applyMigrationPolicy()`: yıkıcı geçiş yalnızca geliştirme sürümleri 1–9 için (`fallbackToDestructiveMigrationFrom(true, 1..9)`); 10'dan sonraki her şema değişikliği `MIGRATIONS` listesine bir Migration gerektiriyor, eksikse Room sessizce silmek yerine hata veriyor. 3→4 ve 4→5 migration'ları 10'a ulaşan bir yol oluşturmadığı için kaldırıldı (Room aynı sürüm için hem migration hem yıkıcı geçişe izin vermiyor).
- `app/build.gradle.kts`: KSP `room.schemaLocation`, şemalar debug/androidTest assets'ine eklendi (release APK etkilenmez), `room-testing` test bağımlılığı.
- Yeni test: `AppDatabaseMigrationTest` (bkz. 4).

### 3.6 Release / R8 (F2)
- `proguard-rules.pro`: tüm `@JsonClass` modelleri ve codegen adapter'ları korunuyor, `kotlin.Metadata` korunuyor, Room kuralındaki yanlış paket (`data.database` → `data.db`) düzeltildi, Log kuralı eklendi. Mapping dosyasıyla doğrulandı: 17 `MetadataEnricher$TMDB*` modelinin hepsi ve 37 JSON adapter'ı korunuyor. `assembleRelease` R8 uyarısı olmadan geçiyor.
- ZXing yansıma kullanmıyor; Firebase, Retrofit, Room, Media3 kendi consumer kurallarını getiriyor.
- Keystore olmadığı için release, imzasız APK olarak derlendi (R8 doğrulaması için imza gerekmez; geçici bir imza değişikliği yapılmadı).

### 3.7 16 KB sayfa boyutu ve 64-bit (B)
- *Güncelleme:* VLC daha sonra onayınızla tamamen kaldırıldı (Bölüm 5, madde 4); aşağıdaki libvlc güncellemesi bu nedenle artık geçerli değil.
- `libvlc-all` 3.6.0 → **3.6.5** (aynı 3.6 çizgisinde yama sürümü). 3.6.0'da `libvlc.so`, `libvlcjni.so`, `libc++_shared.so` (arm64 ve x86_64) 4 KB hizalıydı; 3.6.5'te hepsi 16 KB. APK'daki tüm `.so` dosyaları ELF başlıklarından ve `zipalign -c -P 16` ile doğrulandı. APK'da 64-bit (arm64-v8a, x86_64) kütüphaneler mevcut.

### 3.8 Ana iş parçacığı, StrictMode, coroutine (A)
- `IPTVApplication.kt`: yalnızca debug'da StrictMode (disk/ağ/yavaş çağrı; sızan Closeable/SQLite/Activity) `penaltyLog` ile.
- `AppDatabase.kt`: ViewModel fabrikası veritabanını ana iş parçacığında erkenden açıyordu; artık ana iş parçacığında açılmıyor (Room ilk sorguda IO'da açıyor).
- Yeni `ui/components/OffMainThread.kt` (`rememberComputedOffMain`): 2.000 öğeden küçük listeler aynı karede hesaplanıyor (davranış aynı), büyük listeler `Dispatchers.Default`'ta. Uygulandığı yerler: klasördeki dizi gruplama (`FolderGridScreen`), oynatıcı açılırken kardeş bölüm listesi (`MainActivity`), Çoklu Ekran kanal seçici (`MultiScreen`).
- `PlayerScreen.kt`: dinleyici "sonraki bölüm"ü `rememberUpdatedState` ile okuyor (önceden ilk anki değeri yakalıyordu).
- `IPTVApplication.applicationScope` ve `TMDBRepository.repositoryScope`'a `CoroutineExceptionHandler` eklendi; son `!!` kaldırıldı. (Taramada kalan `first()`/`[0]` kullanımlarının hepsi korumalı çıktı.)
- Singleton'larda Activity/Context tutan alan bulunmadı.

### 3.9 Oynatıcı yaşam döngüsü ve ağ (A)
- `PlayerScreen.kt`: ekran kapanınca / arka planda (PiP değilken) ExoPlayer duraklatılıp `stop()` ile kod çözücü, tampon ve ağ akışı serbest bırakılıyor; konum korunuyor, dönüşte yeniden hazırlanıyor (duraklatılmış halde). Arka plandayken "oynatılıyor" servisi durduruluyor.
- `PlaybackForegroundService.kt`: `START_STICKY` → `START_NOT_STICKY` (hayalet bildirim).
- `IPTVViewModel.refreshPlaylists`: internet yok / liste bozuk ve önbellekte kanal yokken hata sessizce yutuluyordu; artık uygulamanın mevcut hata bandı gösteriliyor.
- Açılışta DataStore okumaları süre sınırlı (`withTimeoutOrNull`), yükleme göstergesi takılı kalamaz.

### 3.10 Kaydırma (A)
- `key` verilmemiş 5 `items(` çağrısı düzeltildi (+ `contentType`): `AdaptivePreviews.kt`, `DetailComponents.kt` (2), `VerticalEPGComponent.kt`, `HomeScreen.kt`.
- Coil: `AsyncImage` ve `rememberAsyncImagePainter` boyut verilmediğinde görseli gösterilen boyuta göre istiyor; değişiklik gerekmedi.

### 3.11 Ölçek, kenar boşlukları, açılış ekranı (B, D)
- `IntroSplashScreen.kt`: 140/100/140 dp ölçüler ekranın kısa kenarına göre ölçekleniyor (400 dp = 1.0, 0.7–1.35 aralığı); başlık 320 dp ekranda 2.0 yazı boyutunda bile sığıyor; slogan en fazla 2 satır. Splash ağ ve izinden bağımsız 3,4 sn'de bitiyor (testle doğrulandı). TV'de OK tuşu da splash'i atlıyor.
- `MainActivity.kt`: ana sayfaya yatay güvenli boşluk (yandaki gezinme çubuğu ve kamera çentiği). Oynatıcı tam ekran kalıyor.
- `DetailScreen.kt`: kaydırılan içeriğin sonu gezinme çubuğunun altında kalmıyor.
- `theme/WindowSizeUtils.kt`: `rememberAppAdaptiveLayout()`'a `isTv` eklendi; TV'de içerik kenar boşlukları en az 48 dp yatay / 27 dp dikey (Google TV güvenli alanı), arka planlar kenara kadar uzanmaya devam ediyor.
- 8 pencere kaydırılabilir yapıldı: Ebeveyn PIN'i, TMDB bilgi, Telefonla gir (QR), Liste ekle, Liste düzenle, Profil düzenle, EPG program detayı, AI özet paneli.
- Compose diyalogları sistem çubuklarının içinde kalıyor (`decorFitsSystemWindows = true`); Çoklu Ekran, Film Bulucu ve Giriş zaten insets kullanıyordu.

### 3.12 Android TV kumandası (C)
- `ui/tv/TvSupport.kt` (mevcut altyapının üzerine): `tvFocusIndicationOrNull`, `tvInitialFocus`, `tvChannelKeys`, `tvLongPressKeys`, `TvFocusMemory` + `tvRestorableFocus`. Hepsi telefonda etkisiz.
- `indication = null` olan 4 yer: alt gezinme sekmeleri ve klasör kartları artık TV'de mor odak çerçevesini gösteriyor (telefonda görünüm aynı). Splash ve (kullanılmayan) VLC ekranının tam ekran dokunma alanlarına çerçeve çizmek görünümü bozacağı için bilerek dokunulmadı; splash OK ile atlanıyor.
- Detay paneli kapanınca odak paneli açan karta dönüyor (Compose 1.7'nin `saveFocusedChild()`'ı yalnızca bir seviye hatırladığı için kartlar TV'de kendi odak isteğini kaydediyor). Uygulandığı kartlar: MediaCard, Top10, Sinemada Bu Hafta, İzlemeye Devam, Sonraki Bölüm, Efsaneler, Fragman, Düello, Radyo, klasör ve İzleme Listesi öğeleri.
- Oynatıcı: canlı yayında CH+/CH- (ve PageUp/PageDown) oynatıcının kanal listesinde sonraki/önceki kanala geçiyor; kanal listesi açıkken Geri önce listeyi kapatıyor (sonra kontrolleri gizliyor, sonra çıkıyor); ekran kilidi kumandayla açılabiliyor (kilitliyken OK/yön tuşu kilit açma düğmesini gösterip odağı ona veriyor).
- Uzun basış: Compose 1.7'de `combinedClickable` OK tuşunu basılı tutmayı algılamıyor. Anlamlı tek uzun basış işlevi olan radyo kartında (favoriye ekle) basılı tutma artık çalışıyor.
- Yazı alanları: Compose 1.7 metin alanı kumandada OK ile ekran klavyesini açıyor ve yön tuşlarında odağı dışarı taşıyor (kütüphane kodundan doğrulandı); değişiklik gerekmedi.
- Manifest doğrulandı: `leanback` ve `touchscreen` `required="false"`, `android:banner` (320×180), `LEANBACK_LAUNCHER` mevcut.

### 3.13 Lint ve kaynaklar (E, F4)
- 22 eksik çeviriden 5'i gerçekten kullanılan tema metinleriydi → İngilizceleri eklendi; 17'si hiç kullanılmayan "Dizi Takvimi" kalıntısıydı → silindi.
- `PlayerScreen.kt`: Media3 `UnstableApi` için `androidx.annotation.OptIn` (lint hatası).
- `bundle { language { enableSplit = false } }`: uygulama içinden TR/EN değiştirilebildiği için Play'in dil bölmesi İngilizce metinleri eksik bırakırdı.
- Kullanılmayan kaynaklar silindi: 86 metin (TR+EN), tüm 41 `dimen` + 1 `integer` (sw360/600/720 dahil), 7 şablon rengi, 2 kullanılmayan JPEG (~1,4 MB). Kodda isimle kaynak arama (`getIdentifier`) yok.
- Kalan 120 uyarı çoğunlukla stil/sürüm önerisi (GradleDependency, NewerVersionAvailable, UseKtx, TypographyEllipsis, ObsoleteSdkInt, DefaultLocale) ya da aşağıdaki "Onay gerekiyor" maddeleri (TrustAllX509TrustManager, AcceptsUserCertificates, InsecureBaseConfiguration).

### 3.14 Testler ve test için yapılan küçük kod eklemeleri
- `PhoneEntryServer.start()`'a varsayılan değeri aynı kalan `hostAddress` parametresi (test 127.0.0.1'e bağlanabilsin diye).
- `TvDevice.overrideForTest` (`@VisibleForTesting`): Robolectric'te TV/telefon modunu zorlamak için.

### Commit listesi (eskiden yeniye)

```
a0a6ece Import CineStream IPTV project from AI Studio export (unchanged)
9f1ea29 Add Gradle 9.3.1 wrapper and ignore secrets (.env, keystores, local.properties)
c4001cf Keep existing playlist when M3U download breaks midway
27b6ffc Update libvlc-all 3.6.0 -> 3.6.5 for 16 KB page size support
58fca79 Fix backup rules so DataStore settings (Gemini key, PIN) are really excluded
c64c1f7 Remove dead code: unused person lookups, TMDB key setter, tmp/ script
8aa5888 Write diagnostic files only in debug builds and never write secrets
a84f014 Room: make v10 the baseline, export schema, keep destructive fallback for dev versions only
6d69305 R8: keep JSON models used only through Retrofit/Moshi, strip Log.d/Log.v in release
019500c Enable StrictMode in debug builds and stop opening the DB on the main thread
640006a TV: visible remote focus on custom clickables, OK skips the intro
a45d0c0 Move large-list work off the main thread (series grouping, player siblings, multiscreen)
88acf28 Give the five key-less lazy items() calls stable keys and content types
3a653f2 Add exception handlers to app-wide coroutine scopes, drop the last `!!`
eb5a626 Player: free ExoPlayer resources in background, no ghost playback notification
36749b8 Intro splash: scale logo for small phones, landscape and TV; never hang on start
1f7cb62 Edge-to-edge: horizontal safe insets on Home, nav-bar padding in detail panel, TV safe area
d8f6315 TV player: CH+/CH- switch channels, Back closes the channel tray, remote can unlock
c119b27 TV: return focus to the card after closing the detail panel; OK-hold = long press
af83bbe Make eight dialogs scrollable so nothing is cut off on small/landscape screens
9d583d0 Add unit tests: M3U/Xtream edge cases, PersonWorksMatcher, TmdbAuth, PhoneEntryServer, country, log redaction
e0a7c25 Add Robolectric Compose tests for TV remote focus and activity recreation
044ace6 Fix lint errors: missing English theme strings, Media3 opt-in, language splits
07ed442 Remove unused resources reported by lint (UnusedResources)
11d7d0d Show the existing error banner when pull-to-refresh fails with no cached list
```

Her commit mesajında gerekçe ayrıntılı yazılı.

---

## 4. Test sonuçları

Komut: `./gradlew assembleDebug testDebugUnitTest lintDebug assembleRelease`

| | Başlangıç | Son |
|---|---|---|
| Unit + Robolectric testleri | 37 (36 geçti, 1 başarısız) | **93 (93 geçti, 0 başarısız)** |
| lintDebug | 26 hata / 268 uyarı | 0 hata / 120 uyarı |
| assembleRelease | geçti (ama modeller siliniyordu) | geçti, R8 uyarısı yok |
| APK boyutu | debug 219 MB | VLC kaldırıldıktan sonra debug 31 MB, imzasız release 6,9 MB (bkz. Bölüm 5, madde 4) |

Son kontrol `./gradlew clean` sonrası, derleme önbelleği kapalı (`--no-build-cache`) çalıştırıldı: **BUILD SUCCESSFUL**. Cihaz testleri de derleniyor (`assembleDebugAndroidTest`).

Eklenen testler:

| Test sınıfı | Sayı | Kapsam |
|---|---|---|
| `M3uXtreamEdgeCaseTest` | 8 | Boş liste, HTML hata sayfası, bozuk satırlar, CRLF+BOM, **50.000 öğe** (~2 sn), boyut sınırı aşımı; bozuk güncellemede eski liste korunuyor; Xtream adres ayrıştırma (geçerli/geçersiz) |
| `PersonWorksMatcherTest` | 10 | Türkçe harf katlama (ı/İ/ş/ğ/ü), sağlayıcı etiketleri, çift dilli adlar, film için ±1 yıl, dizide yıl serbest, "Red"→"Predator" yanlış eşleşmesi yok |
| `TmdbAuthTest` | 7 | Link anahtar reddi, jeton `Authorization: Bearer` ile, v3 anahtar `api_key` ile, 401'de yedek kimlikle bir kez yeniden deneme, döngü yok, TMDB dışı adreslere dokunulmuyor (MockWebServer) |
| `PhoneEntryServerTest` | 9 | Jeton yok/yanlış → 403, form sayfası, Türkçe ve özel karakterli form alanlarının doğru ayrıştırılması, hata mesajının tekrar gösterilmesi, 400, 404 (gerçek yerel soket) |
| `NowPlayingCountryTest` | 5 | Şebeke > SIM > sistem bölgesi > TR, geçersiz kodlar, ülke adı dili |
| `AppDatabaseMigrationTest` | 4 | v10 şeması dışa aktarılmış; v10'daki liste/favori/izleme geçmişi korunuyor; 10'dan sonraki her adımda migration var; v9 geliştirme DB'si çökmeden yeniden oluşuyor |
| `TvRemoteFocusTest` | 4 | Yön tuşlarıyla kartlar arası odak; panelde odak hapsi; Geri paneli kapatıyor; odak karta dönüyor; telefonda etkisiz |
| `MainActivityRecreateTest` | 5 | Telefon dikey, 320 dp + 2.0 yazı boyutu, yatay→dikey, tablet, TV: `recreate()` sonrası çökme yok, splash tekrar oynamıyor |
| `DiagnosticLogTest` | 4 | Anahtar/jeton/şifre maskeleme |

Mevcut testlerin hiçbiri gevşetilmedi; başarısız olan test kod düzeltilerek geçti.

---

## 5. Onay gerekiyor (kod değiştirilmedi, öneri)

1. **Tüm sertifikalara güvenen IPTV istemcisi** (`NetworkModule.provideUnsafeOkHttpClient`, her şeye güvenen `X509TrustManager` + her zaman `true` dönen `hostnameVerifier`, `configureUnsafeSslForConnection`). Google Play bunu işaretleyebilir. Ayrıca `network_security_config.xml` kullanıcı sertifikalarına da güveniyor.
   *Öneri:* Önce sistemin güven deposunu deneyen, başarısız olursa yalnızca kullanıcının o liste için onayladığı sunucunun sertifika parmak izini (ilk kullanımda sor, sonra sabitle) kabul eden bir `X509TrustManager`; `hostnameVerifier` varsayılan kalsın. Bu istemci TMDB/Gemini'de zaten kullanılmıyor.
2. **applicationId** `com.aistudio.cinestreamiptv.gkrwpy`: Play'de ilk yayından sonra değiştirilemez. Yayından önce kalıcı bir ad seçmenizi öneririm.
3. **Cleartext (HTTP) trafiği açık** (`usesCleartextTraffic="true"`, `cleartextTrafficPermitted="true"`): IPTV/Xtream sunucularının çoğu ve yayın adresleri `http://` kullanır; liste adreslerini kullanıcı girdiği için alan adları önceden bilinemez, bu yüzden gerekli.
   *Öneri:* `network_security_config.xml`'e `api.themoviedb.org`, `generativelanguage.googleapis.com`, `api.revenuecat.com` için `cleartextTrafficPermitted="false"` alan adı kuralları eklenebilir.
4. ~~**VLC oynatıcı hiç açılmıyor**~~ — **Yapıldı (onayınızla, ikinci PR):** `libvlc-all` bağımlılığı, `VideoPlayerScreen.kt`, `PlayerViewModel.kt` ve VLC ProGuard kuralları kaldırıldı; `PlayerScreen` doğrudan (zaten her zaman açılan) ExoPlayer oynatıcısını gösteriyor. Debug APK 230 → 31 MB, imzasız release APK 206 → 6,9 MB. APK'da kalan native kütüphaneler (AndroidX) 16 KB hizalı.
5. **Yayın açılamazsa ilgisiz bir test videosu oynatılıyor:** `PlayerScreen` birkaç denemeden sonra `https://vjs.zencdn.net/v/oceans.mp4` (film/dizi) veya `https://test-streams.mux.dev/...` (canlı) açıyor. Kullanıcı kendi kanalı yerine okyanus videosu görüyor.
   *Öneri:* Bunun yerine "Yayın açılamadı" mesajı + "Tekrar dene" düğmesi.
6. **Firebase AI ve App Check bağımlılıkları kullanılmıyor.** Gemini, REST API ile ve anahtar adres içinde (`?key=`) gönderilerek çağrılıyor.
   *Öneri:* Kullanılmayan bağımlılıkları kaldırmak ya da anahtarı `x-goog-api-key` başlığıyla göndermek. Gemini davranışına dokunmamam istendiği için değiştirmedim.
7. **`RECORD_AUDIO` izni ve ses tanıma `queries` tanımlı ama kodda sesli arama yok.** Play hassas izinler için gerekçe ister.
   *Öneri:* İzni ve `queries` bloğunu kaldırmak.
8. **`supportsPictureInPicture="true"` ama PiP kodu yok** (oynatıcı sadece PiP'te olup olmadığını kontrol ediyor). PiP eklemek yeni özellik olur; yapılmayacaksa bayrağı kaldırmanızı öneririm.
9. **Bozuk veritabanı kurtarma veriyi siliyor:** `AppDatabase` `SQLiteDiskIOException`'ı da (ör. disk doluyken) "bozulma" sayıp veritabanını silebiliyor; `CrashRecoveryManager` üç açılış çökmesinden sonra da siliyor.
   *Öneri:* Silmek yerine dosyayı `.corrupt-<tarih>` diye yedeğe almak ve yalnızca gerçek `SQLiteDatabaseCorruptException`'da yapmak.
10. **Depo herkese açık (public) ve gömülü TMDB kimlik bilgileri:** `TmdbAuth.kt` ve `TMDBRepository.kt` içinde XOR+Base64 ile gizlenmiş TMDB okuma jetonu ve v3 anahtarı var (kolayca çözülebilir; rapordaki düz metin anahtarla aynı). Bu mantığa dokunmamam istendi.
    *Öneri:* Push'tan önce depoyu private yapmak ya da anahtarı TMDB'de yenilemek.
11. **Kodda sabit Türkçe metinler:** hata bandı mesajları (`ErrorHandlingManager`), "listenizde bulunamadı" uyarısı, VLC ekranındaki "Tekrar Deneyin / Yedek Oynatıcı / CANLI YAYIN", splash sloganı vb. İngilizce kullanıcıya Türkçe görünüyor.
    *Öneri:* `strings.xml`'e TR+EN olarak taşımak (metin değişikliği olduğu için onayınız gerekiyor).
12. **Room veritabanı buluta yedekleniyor:** listeler (Xtream adreslerinde kullanıcı adı/şifre) yedeğe giriyor. Hariç tutulursa kullanıcı yeni cihazda listelerini kaybeder.
13. **`rememberAppAdaptiveLayout()` her yerde kullanılmıyor** (örn. `HomeScreen` satırlarında sabit 24 dp, yön için `LocalConfiguration`). Tamamını dönüştürmek geniş kapsamlı ve riskli; adım adım yapılmasını öneririm.
14. **Compose BOM 2024.09.00 (Compose 1.7)**: 1.8+ sürümü OK tuşuyla uzun basışı ve odak geri yüklemeyi yerleşik destekliyor. İleride güncelleme önerilir (TvSupport'taki geçici çözümler sadeleşir).
15. **Süreç ölümünde açık ekran kaybolur:** `MainActivity.currentScreen` `remember` ile tutuluyor; sistem uygulamayı öldürüp geri getirince ana sayfaya dönülür (çökme yok). Oynatıcı durumunu saklamak için `IPTVItem` kaydedilebilir yapılabilir.

---

## 6. Çalıştırılamayan testler ve nedenleri

- **`connectedDebugAndroidTest` (telefon ve TV emülatörü) ve `adb shell monkey`**: Bu bulut ortamında `/dev/kvm` yok ve işlemci sanallaştırma (VT-x/AMD-V) sunmuyor; Android emülatörü bu koşulda çalışamaz. Bağlı bir cihaz da yok. Cihaz testleri derleniyor (`assembleDebugAndroidTest`) ama çalıştırılamadı. Bunların yerine Robolectric ile TV odak, Geri tuşu, döndürme/yeniden oluşturma testleri eklendi.
- **Gerçek 16 KB sayfalı cihazda çalıştırma**: yapılamadı; hizalama statik olarak (ELF + `zipalign -P 16`) doğrulandı.
- **İmzalı release**: keystore olmadığı için sadece imzasız release derlendi.
- Kendi bilgisayarınızda şunları çalıştırabilirsiniz:
  ```
  ./gradlew connectedDebugAndroidTest
  adb shell monkey -p com.aistudio.cinestreamiptv.gkrwpy --throttle 100 -v 20000
  ```

---

## 7. Elle kontrol listesi

### Telefon
1. Uygulamayı açın: açılış animasyonu ~3,5 sn'de kendiliğinden bitmeli (internet kapalıyken de).
2. Telefonu yatay çevirin: ana sayfa içeriği yandaki gezinme çubuğunun / kamera çentiğinin altında kalmamalı.
3. Telefonun kendi Ayarlar → Ekran → Yazı boyutu seçeneğini en büyüğe alın: açılış başlığı taşmamalı; Liste ekle, Profil düzenle, PIN pencereleri kaydırılabilmeli, düğmeler görünür olmalı.
4. Uçak modunda aşağı çekip yenileyin: önbellekte liste yoksa üstte "İnternet bağlantısı…" bandı görünmeli.
5. Bir liste yenilenirken Wi-Fi'ı kapatın: mevcut kanallarınız silinmemeli.
6. Bir film açın, ana ekran tuşuna basın, 1 dk sonra geri dönün: oynatıcı aynı yerde, duraklatılmış olmalı; bildirim arka plandayken kaybolmalı.
7. Uygulamayı "son uygulamalar"dan kapatın: "Şu an oynatılıyor" bildirimi kalmamalı.
8. Dili İngilizceye çevirin: Ayarlar'daki Tema bölümü İngilizce olmalı.

### Android TV / Google TV (kumanda)
1. Açılışta OK'ye basın: açılış atlanmalı.
2. Ana sayfada yön tuşlarıyla kartlar ve alt sekmeler arasında gezinin: her odaklı öğede mor çerçeve görünmeli, kenarlarda boşluk olmalı.
3. Bir karta OK'ye basın → detay paneli: yön tuşlarıyla panelin dışına çıkılamamalı. Geri'ye basın: panel kapanmalı ve odak açtığınız karta dönmeli.
4. Radyo satırında bir kartta OK'yi basılı tutun: favoriye eklenmeli; kısa basış radyoyu çalmalı.
5. Canlı bir kanal açın: CH+ / CH- ile sonraki/önceki kanala geçmeli.
6. Oynatıcıda kanal listesini açın, Geri'ye basın: önce liste kapanmalı; tekrar Geri → kontroller gizlenmeli; tekrar Geri → oynatıcıdan çıkılmalı.
7. Oynatıcıda ekranı kilitleyin, sonra OK'ye basın: "Kilidi aç" düğmesi görünmeli ve odakta olmalı; OK ile kilit açılmalı.
8. Arama / PIN / profil alanına gelip OK'ye basın: ekran klavyesi açılmalı.
9. Ayarlar → "Telefonla gir": QR kod ekranı açılmalı, telefondan gönderilen liste kaydedilmeli.
