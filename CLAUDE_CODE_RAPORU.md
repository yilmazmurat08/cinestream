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

Sonraki PR'lar: VLC'nin kaldırılması, test videosu yerine hata penceresi, mikrofon izninin kaldırılması (bkz. Bölüm 5) ve beşinci PR:

```
3b8f18f Never delete a corrupted database: back it up first; disk I/O errors are not corruption
b2df7db Network security: HTTPS-only and system CAs for the app's own API hosts
d2adaac Localize error messages, notifications and common dialogs (TR + EN)
dc03dd5 Localize home screen and folder screen texts (TR + EN)
c9ed2aa Localize watchlist, series detail, add-playlist and login texts (TR + EN)
7e5ff49 Localize EPG guide and multi-screen texts (TR + EN)
0d73bfc Localize settings screen texts (TR + EN)
0371ff4 Restore the open screen after process death
```

Her commit mesajında gerekçe ayrıntılı yazılı.

---

## 4. Test sonuçları

Komut: `./gradlew assembleDebug testDebugUnitTest lintDebug assembleRelease`

| | Başlangıç | Son |
|---|---|---|
| Unit + Robolectric testleri | 37 (36 geçti, 1 başarısız) | **120 (120 geçti, 0 başarısız) + 7 stres testi** |
| lintDebug | 26 hata / 268 uyarı | 0 hata / 159 uyarı (artış, yeni çeviri metinlerindeki "…" ve çoğul kalıbı gibi yazım önerilerinden; hata yok) |
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
| `DatabaseCorruptionBackupTest` | 3 | Bozuk veritabanı silinmeden yedekleniyor (elle kurtarma ve SQLite açılışı); sağlam veritabanında yedek alınmıyor |
| `IptvCertificateTest` | 3 | IPTV istemcisi kendinden imzalı sertifikayı kabul etmiyor; sertifika hatasında aynı adres http ile deneniyor; diğer hatalarda denenmiyor |
| `CursorWindowReproTest` | 1 | Kullanıcının çökmesi: 3 MB'lık satırla ham okuma düşüyor, uygulama katmanı kurtarıyor |
| `ChannelRingTest` | 3 | Kanal listesi: izlenen klasör, yetişkin kanal karışmaz, boş klasörde tüm uygun kanallar |
| `OversizedRowTrimTest` | 1 | 3 MB'lık base64 logo ve 50.000 karakterlik açıklama kırpılıyor, normal satır değişmiyor |
| `TmdbCacheTrimTest` | 1 | Oyuncu/yönetmen önbelleği 200 kayda indiriliyor |
| `FeaturedLegacyCacheTest` | 1 | Eski öne çıkan film kaydı siliniyor |
| `FeaturedHeroCarouselTest` | 3 | Öne Çıkan alanı 5 sn'de kendiliğinden geçiyor, sola kaydırınca sonraki film geliyor; yetişkin içerik listeye girmiyor, aynı başlık bir kez |
| `PictureInPictureTest` | 5 | Dil ayarlı bağlamda da PiP: Android 8–11'de ana ekrana çıkınca PiP'e geçiş, duraklatılmışken geçmeme, Android 12+ otomatik geçiş hazır, TV'de kapalı; ağ ayarında `image.tmdb.org` HTTP'ye açık ve kullanıcı sertifikalarına güveniliyor |
| `PipAspectRatioTest` | 3 | PiP pencere oranı: normal video, bilinmeyen boyut (16:9), aşırı oranların sınırlanması |
| `ActiveScreenSaverTest` | 3 | Açık ekran ve oynatıcıdaki içerik süreç ölümünden sonra geri yükleniyor; bilinmeyen değerde ana sayfaya dönülüyor |

Mevcut testlerin hiçbiri gevşetilmedi; başarısız olan test kod düzeltilerek geçti.


### Stres testleri

Komut: `./gradlew testDebugUnitTest -Pstress=true --tests 'com.example.stress.*'` (normal test turunda atlanır). Robolectric ile, gerçek SQLite ve Compose üzerinde çalışır; bu ortamda emülatör (KVM) olmadığı için cihaz üzerinde monkey testi yapılamadı (aşağıdaki komutla telefonda çalıştırılabilir).

| Senaryo | Sonuç |
|---|---|
| 200.000 öğeli M3U (canlı/film/dizi, yetişkin ve bozuk satırlar karışık) içe aktarma | 10,2 sn, geçti |
| 200.000 öğeden türe göre okuma (119.800 canlı / 40.000 film / 40.000 dizi) | 2,4 / 0,5 / 0,5 sn, geçti |
| Okuma sürerken listeyi 5 kez yenileme (20.000 öğe) — favori korunuyor mu | 4,6 sn, favori korundu, geçti |
| Okuma sürerken 300 favori değişikliği | 0,5 sn, geçti |
| 60.000 kanalda yetişkin filtresi | 0,4 sn (1.202 yetişkin kanal yakalandı), geçti |
| 60.000 kanalda 5.000 kanal değiştirme — klasör dışına ve yetişkin kanala hiç geçmiyor | 8,2 sn, geçti |
| Öne Çıkan alanında 400 kaydırma + otomatik geçiş + kaydırma sırasında listenin 0–8 arasında değişmesi | 6,9 sn, çökme yok, geçti |
| Kullanıcının çökmesinin yeniden üretimi: 3 MB'lık tek satır (ham okuma `Row too big to fit into CursorWindow` ile düşüyor) | Uygulama katmanı kırpıp listeyi veriyor, geçti (bu test normal turda da çalışıyor) |

Telefonda monkey testi (USB hata ayıklama açık, bilgisayarda adb kurulu): `adb shell monkey -p com.cinestream.iptv --throttle 100 --pct-syskeys 0 -v 20000` — bitince "Monkey finished" yazmalı; "CRASH" görünürse günlük gönderilmeli.

---

## 5. Onay gerekiyor (kod değiştirilmedi, öneri)

1. ~~**Tüm sertifikalara güvenen IPTV istemcisi**~~ — **Yapıldı (onayınızla, on birinci PR):** Her sertifikaya güvenen `X509TrustManager` ve her zaman `true` dönen `HostnameVerifier` tamamen kaldırıldı (Google Play bunları reddediyor). IPTV listeleri, EPG ve yayınlar artık Android'in standart doğrulamasını kullanıyor (sistem + kullanıcı sertifikaları). Sertifikası bozuk (süresi dolmuş, kendinden imzalı, yanlış adlı) sunucularda https isteği bir kez aynı adresle http üzerinden deneniyor; IPTV sunucularının çoğu aynı içeriği http ile de sunduğu için bu sunucular çalışmaya devam eder. Sadece https sunan ve sertifikası bozuk bir sunucu artık açılmaz. `IptvCertificateTest` ile test ediliyor (kendinden imzalı sunucu reddediliyor, sertifika hatasında http'ye geçiliyor, zaman aşımı gibi diğer hatalarda geçilmiyor).
   *Öneri:* Önce sistemin güven deposunu deneyen, başarısız olursa yalnızca kullanıcının o liste için onayladığı sunucunun sertifika parmak izini (ilk kullanımda sor, sonra sabitle) kabul eden bir `X509TrustManager`; `hostnameVerifier` varsayılan kalsın. Bu istemci TMDB/Gemini'de zaten kullanılmıyor.
2. ~~**applicationId** `com.aistudio.cinestreamiptv.gkrwpy`~~ — **Yapıldı (onayınızla, yedinci PR):** paket adı `com.cinestream.iptv` oldu (Play'de ilk yayından sonra değiştirilemez). Kod paketi (`com.example`) değişmedi. Not: eski paket adıyla kurulmuş test uygulaması ayrı bir uygulama sayılır; yeni sürüm yanına kurulur ve eski listeler ona taşınmaz.

   **Test sürümünde 60 dakika sınırı kaldırıldı (onayınızla, yedinci PR):** `FREE_WATCH_LIMIT` ayarı debug ve yeni `qa` (test) sürümünde kapalı, mağaza (release) sürümünde açık; PRO/ödeme ekranı değişmedi. Test APK'sı: `./gradlew assembleQa` → `app/build/outputs/apk/qa/app-qa.apk` (release gibi küçültülmüş, debug anahtarıyla imzalı).
3. **Cleartext (HTTP) trafiği açık** — **Kısmen yapıldı (beşinci PR), sekizinci PR'da düzeltildi:** uygulamanın kendi API servislerinde (TMDB API, Gemini, Google API) HTTP kapalı. Beşinci PR'da bu adreslerde yalnızca sistem sertifikalarına güveniliyordu ve `image.tmdb.org` da listedeydi; bu, film/dizi afişlerini ve oyuncu/yönetmen fotoğraflarını bozdu (sağlayıcılar afişleri çoğunlukla `http://image.tmdb.org/...` ile veriyor; reklam engelleyici/VPN uygulamaları kendi sertifikalarını kullanıcı sertifikası olarak yüklüyor). Sekizinci PR'da `image.tmdb.org` listeden çıkarıldı ve bu adreslerde kullanıcı sertifikalarına yeniden güveniliyor (önceki davranış). IPTV için genel kural değişmedi:  (`usesCleartextTraffic="true"`, `cleartextTrafficPermitted="true"`): IPTV/Xtream sunucularının çoğu ve yayın adresleri `http://` kullanır; liste adreslerini kullanıcı girdiği için alan adları önceden bilinemez, bu yüzden gerekli.
4. ~~**VLC oynatıcı hiç açılmıyor**~~ — **Yapıldı (onayınızla, ikinci PR):** `libvlc-all` bağımlılığı, `VideoPlayerScreen.kt`, `PlayerViewModel.kt` ve VLC ProGuard kuralları kaldırıldı; `PlayerScreen` doğrudan (zaten her zaman açılan) ExoPlayer oynatıcısını gösteriyor. Debug APK 230 → 31 MB, imzasız release APK 206 → 6,9 MB. APK'da kalan native kütüphaneler (AndroidX) 16 KB hizalı.
5. ~~**Yayın açılamazsa ilgisiz bir test videosu oynatılıyor**~~ — **Yapıldı (onayınızla, üçüncü PR):** Tüm denemeler bitince artık okyanus/test videosu açılmıyor; oynatma durduruluyor ve "Kaldığın yerden devam" penceresiyle aynı görünümde **"Yayın açılamadı"** penceresi çıkıyor (açıklama + "Geri" / "Tekrar Deneyin"). "Tekrar Deneyin" deneme sayacını sıfırlayıp yayını baştan dener. TV'de pencere açılınca odak "Tekrar Deneyin" düğmesinde; kumanda tuşları pencereye gidiyor. Yeni metinler TR+EN eklendi, eski "Yedek akış başlatıldı" metni kaldırıldı; hata loguna yazılan adres maskeleniyor. Not: ExoPlayer'ın oynatma iş parçacığı Robolectric'te ilerlemediği için bu akış otomatik testle doğrulanamadı; elle kontrol listesine eklendi.
6. **Firebase AI ve App Check bağımlılıkları kullanılmıyor.** Gemini, REST API ile ve anahtar adres içinde (`?key=`) gönderilerek çağrılıyor.
   *Öneri:* Kullanılmayan bağımlılıkları kaldırmak ya da anahtarı `x-goog-api-key` başlığıyla göndermek. Gemini davranışına dokunmamam istendiği için değiştirmedim.
7. ~~**`RECORD_AUDIO` izni ve ses tanıma `queries` tanımlı ama kodda sesli arama yok**~~ — **Yapıldı (onayınızla, dördüncü PR):** `RECORD_AUDIO` izni, sadece bu izin yüzünden eklenen `android.hardware.microphone` özellik satırı ve ses tanıma `queries` bloğu manifest'ten kaldırıldı. Birleştirilmiş son manifest'te de izin yok (hiçbir kütüphane geri eklemiyor). Uygulama artık mikrofon izni istemiyor; Play'de hassas izin gerekçesi gerekmez.
8. ~~**`supportsPictureInPicture="true"` ama PiP'e geçiren kod yok**~~ — **Yapıldı (onayınızla, altıncı PR; sekizinci PR'da çalışır hâle getirildi):** Altıncı PR'daki kod Activity'yi Compose'un `LocalContext`'inden arıyordu; uygulama dil desteği için buraya Activity olmayan bir bağlam verdiği için PiP hiç açılmıyordu ve PiP'e geçerken video duraklatılıyordu. Artık Activity Compose görünümünden (`LocalView`) alınıyor; bu durum `PictureInPictureTest` ile test ediliyor. Oynatıcıda video oynarken ana ekran tuşuna basılınca video küçük pencerede (resim içinde resim) oynamaya devam ediyor. Android 12+'da sistemin otomatik geçişi (`setAutoEnterEnabled`), Android 8–11'de `onUserLeaveHint` kullanılıyor. Pencere oranı videoya göre ayarlanıyor (Android sınırı 2,39:1). PiP'teyken kontroller, paneller ve pencereler gizleniyor, sadece video görünüyor; büyütünce geri geliyor. PiP penceresi kapatılınca oynatma duruyor ve bildirim kalkıyor (konum korunuyor). Video duraklatılmışsa, yayın açılamadıysa, Android TV'de ve PiP desteklemeyen cihazlarda PiP'e geçilmiyor. Görünüme yeni düğme eklenmedi.
9. ~~**Bozuk veritabanı kurtarma veriyi siliyor**~~ — **Yapıldı (beşinci PR):** Disk G/Ç hataları (ör. disk dolu) artık bozulma sayılmıyor. Gerçek bozulmada veritabanı silinmek yerine `cinestream_database.corrupt-<tarih>` adıyla (yan dosyalarıyla) yedekleniyor; SQLite'ın kendi "bozuk dosyayı sil" adımından önce de kopya alınıyor. Eski durum: `AppDatabase` `SQLiteDiskIOException`'ı da (ör. disk doluyken) "bozulma" sayıp veritabanını silebiliyor; `CrashRecoveryManager` üç açılış çökmesinden sonra da siliyor.
   *Öneri:* Silmek yerine dosyayı `.corrupt-<tarih>` diye yedeğe almak ve yalnızca gerçek `SQLiteDatabaseCorruptException`'da yapmak.
10. **Depo herkese açık (public) ve gömülü TMDB kimlik bilgileri:** `TmdbAuth.kt` ve `TMDBRepository.kt` içinde XOR+Base64 ile gizlenmiş TMDB okuma jetonu ve v3 anahtarı var (kolayca çözülebilir; rapordaki düz metin anahtarla aynı). Bu mantığa dokunmamam istendi.
    *Öneri:* Push'tan önce depoyu private yapmak ya da anahtarı TMDB'de yenilemek.
11. ~~**Kodda sabit Türkçe metinler**~~ — **Yapıldı (beşinci PR):** Hata bandı, bildirimler, ana sayfa, klasörler, izleme listesi, dizi detayı, EPG rehberi, çoklu ekran, liste ekleme, giriş, ayarlar, oynatıcı ve ortak pencerelerdeki ~330 metin TR+EN kaynaklara taşındı. Mantığın bağlı olduğu Türkçe anahtarlar ("Tümü", EPG saat dilimleri, tampon/altyazı rengi değerleri, tür adları, "Yönetmen") değiştirilmedi; ekranda sadece seçili dildeki karşılığı gösteriliyor. PRO/abonelik ekranı kural gereği çevrilmedi. Eski durum: hata bandı mesajları (`ErrorHandlingManager`), "listenizde bulunamadı" uyarısı, VLC ekranındaki "Tekrar Deneyin / Yedek Oynatıcı / CANLI YAYIN", splash sloganı vb. İngilizce kullanıcıya Türkçe görünüyor.
    *Öneri:* `strings.xml`'e TR+EN olarak taşımak (metin değişikliği olduğu için onayınız gerekiyor).
12. **Room veritabanı buluta yedekleniyor:** listeler (Xtream adreslerinde kullanıcı adı/şifre) yedeğe giriyor. Hariç tutulursa kullanıcı yeni cihazda listelerini kaybeder.
13. **`rememberAppAdaptiveLayout()` her yerde kullanılmıyor** (örn. `HomeScreen` satırlarında sabit 24 dp, yön için `LocalConfiguration`). Tamamını dönüştürmek geniş kapsamlı ve riskli; adım adım yapılmasını öneririm.
14. **Compose BOM 2024.09.00 (Compose 1.7)**: 1.8+ sürümü OK tuşuyla uzun basışı ve odak geri yüklemeyi yerleşik destekliyor. İleride güncelleme önerilir (TvSupport'taki geçici çözümler sadeleşir).
15. ~~**Süreç ölümünde açık ekran kaybolur**~~ — **Yapıldı (beşinci PR):** açık ekran (oynatıcıdaki içerik dahil) `rememberSaveable` ile saklanıyor; Android uygulamayı arka planda kapatıp geri açtığında kullanıcı kaldığı ekrana dönüyor. Eski durum: `MainActivity.currentScreen` `remember` ile tutuluyor; sistem uygulamayı öldürüp geri getirince ana sayfaya dönülür (çökme yok). Oynatıcı durumunu saklamak için `IPTVItem` kaydedilebilir yapılabilir.
16. ~~**Oynatıcı Activity'yi bulamıyor (AI Studio'dan gelen hata)**~~ — **Yapıldı (onayınızla, sekizinci PR):** MainActivity dil desteği için Compose'a Activity olmayan bir bağlam verdiği için oynatıcıda tam ekran (durum çubuğunu gizleme), yatay ekrana geçiş ve parmakla parlaklık ayarı hiç çalışmıyordu. Oynatıcı artık Activity'yi Compose görünümünden (`LocalView`) alıyor. Oynatıcı tam ekran ve yatay açılıyor; alt satıra **Tam ekran / Küçült** düğmesi eklendi (küçült: dikey ekran, durum çubuğu görünür; TV'de düğme yok, TV hep tam ekran). Parmakla ayarlanan parlaklık sadece oynatıcıda geçerli; oynatıcıdan çıkınca sistem parlaklığına dönülüyor. Yeni metinler TR+EN eklendi.

17. **RevenueCat kaldırıldı (onayınızla, sekizinci PR):** PRO ödemeleri Google Play ile kurulacağı için RevenueCat kütüphanesi, başlatma kodu, ProGuard kuralı ve ağ ayarı kaldırıldı. `SubscriptionManager` artık sadece ürün adlarını tutuyor; satın alma/geri yükleme, ödeme altyapısı kurulana kadar önceki gibi "servis kullanılabilir değil" diyor. PRO ekranının görünümü değişmedi.
18. **Öne Çıkan alanı kaydırmalı oldu (onayınızla, dokuzuncu PR):** Ana sayfadaki Öne Çıkan kartı artık öne çıkan film + kütüphaneden rastgele seçilen 7 filmi (afişi olan, oynatılabilir) gösteriyor. 5 saniyede bir kendiliğinden ya da parmakla sola/sağa kaydırınca değişiyor, sonsuz dönüyor; kaydırınca 5 sn baştan başlıyor. Kartın görünümü ve boyutu aynı. Yetişkin içerik hem film seçiminde hem gösterilecek listede ayrıca filtreleniyor (eski öne çıkan film seçiminde bu filtre yoktu, eklendi). Kütüphane güncellendiğinde (ör. favori ekleme) kartlar karışmıyor. TV'de odak kartın içindeyken otomatik geçiş duruyor. İçerikler kendiliğinden değiştiği için eski manuel "yenile" düğmesi kaldırıldı (onuncu PR). `FeaturedHeroCarouselTest` ile test ediliyor.
19. **Öne Çıkan içerikleri her girişte değişiyor, eski veriler temizleniyor (onayınızla, dokuzuncu PR):** Öne çıkan film artık 12 saatliğine telefona kaydedilmiyor; uygulama her açıldığında ve arka plandan her dönüşte (PiP ve ekran döndürme hariç) öne çıkan film ve kaydırmalı alandaki filmler yeniden seçiliyor. Eski sürümün kaydı (`featured_movie_prefs`) siliniyor. Ayrıca TMDB bilgi önbelleğinin 30 günü dolan kayıtları hiç silinmiyordu ve veritabanı sürekli büyüyordu; artık her açılışta süresi dolanlar siliniyor; oyuncu/yönetmen önbelleğinin 200 kayıt sınırını bazı kayıt yolları atlıyordu, o da her açılışta 200 kayda indiriliyor (listeler, favoriler ve izleme geçmişi etkilenmez). Afiş resimleri zaten boyut sınırlı önbellekte (cihaz belleğine göre 100–250 MB, 150 MB'ı aşınca eski dosyalar siliniyor).
20. **Liste okurken çökme düzeltildi (onuncu PR):** Kullanıcı günlüğünde `IllegalStateException: Couldn't read row …, col 0 from CursorWindow` (IPTVDao.getItemsByTypeFlow) vardı. Bu hata tek bir satır 2 MB'lık okuma penceresine sığmadığında çıkar (ör. logosu base64 olarak gömülü M3U satırı, çok uzun açıklama). Alan uzunluğu hiç sınırlanmıyordu ve hata yakalanmadığı için uygulama kapanıyordu. Artık aşırı büyük alanlar liste her kaydedildiğinde ve her açılışta kırpılıyor (4 KB'tan uzun logo/fragman adresi boşaltılıyor, açıklama 8000, oyuncular 4000, ad 500 karakterle sınırlanıyor). Okuma hatasında önce kırpılıp 3 kez yeniden deneniyor, yine olmazsa hata kaydediliyor ve ekrandaki liste korunuyor. `OversizedRowTrimTest` ile test ediliyor.
21. **Yetişkin içerik ana sayfaya ve oynatıcıya karışmıyor (onuncu PR):** "Bugün Trend Filmler", "Popüler Diziler" ve "Popüler Kanallar" listeleri yetişkin filtresinden geçmiyordu; artık geçiyor. Filtre, birçok sağlayıcının yetişkin kanallara koyduğu "XX:" önekini de tanıyor ("XXL" gibi adları yakalamıyor).
22. **Canlı yayın oynatıcısı (onuncu PR):** Canlı yayında 10 sn geri/ileri düğmelerinin yerine **önceki kanal / sonraki kanal** düğmeleri var; film ve dizide 10 sn düğmeleri aynı. Oynatıcıdaki kanal listesi ve kanal değiştirme (düğmeler ve CH+/CH-) artık tüm kanallar yerine izlenen kanalın klasöründeki kanalları kullanıyor; yetişkin olmayan bir kanal izlenirken yetişkin kanallar hiç gelmiyor. Klasörde başka kanal yoksa tüm uygun kanallar kullanılıyor. Film önerilerinden de yetişkin içerik çıkarıldı. `ChannelRingTest` ile test ediliyor. Yeni metinler TR+EN.

---

## 6. Çalıştırılamayan testler ve nedenleri

- **`connectedDebugAndroidTest` (telefon ve TV emülatörü) ve `adb shell monkey`**: Bu bulut ortamında `/dev/kvm` yok ve işlemci sanallaştırma (VT-x/AMD-V) sunmuyor; Android emülatörü bu koşulda çalışamaz. Bağlı bir cihaz da yok. Cihaz testleri derleniyor (`assembleDebugAndroidTest`) ama çalıştırılamadı. Bunların yerine Robolectric ile TV odak, Geri tuşu, döndürme/yeniden oluşturma testleri eklendi.
- **Gerçek 16 KB sayfalı cihazda çalıştırma**: yapılamadı; hizalama statik olarak (ELF + `zipalign -P 16`) doğrulandı.
- **İmzalı release**: keystore olmadığı için sadece imzasız release derlendi.
- Kendi bilgisayarınızda şunları çalıştırabilirsiniz:
  ```
  ./gradlew connectedDebugAndroidTest
  adb shell monkey -p com.cinestream.iptv --throttle 100 --pct-syskeys 0 -v 20000
  ```

---

## 7. Elle kontrol listesi

### Telefon
1. Uygulamayı açın: açılış animasyonu ~3,5 sn'de kendiliğinden bitmeli (internet kapalıyken de).
2. Telefonu yatay çevirin: ana sayfa içeriği yandaki gezinme çubuğunun / kamera çentiğinin altında kalmamalı.
3. Telefonun kendi Ayarlar → Ekran → Yazı boyutu seçeneğini en büyüğe alın: açılış başlığı taşmamalı; Liste ekle, Profil düzenle, PIN pencereleri kaydırılabilmeli, düğmeler görünür olmalı.
4. Uçak modunda aşağı çekip yenileyin: önbellekte liste yoksa üstte "İnternet bağlantısı…" bandı görünmeli.
5. Bir liste yenilenirken Wi-Fi'ı kapatın: mevcut kanallarınız silinmemeli.
5b. Çalışmayan (kapalı) bir kanal/film açın: test videosu yerine "Yayın açılamadı" penceresi çıkmalı; "Tekrar Deneyin" ve "Geri" çalışmalı. Dili İngilizce yapınca pencere İngilizce olmalı.
6. Bir film açın, ana ekran tuşuna basın, 1 dk sonra geri dönün: oynatıcı aynı yerde, duraklatılmış olmalı; bildirim arka plandayken kaybolmalı.
7. Uygulamayı "son uygulamalar"dan kapatın: "Şu an oynatılıyor" bildirimi kalmamalı.
8. Dili İngilizceye çevirin: Ayarlar, ana sayfa bölüm başlıkları, klasör kartları, EPG rehberi, çoklu ekran ve hata bandı İngilizce olmalı; EPG'de "Morning (06-12)" gibi filtreler yine doğru kanalları süzmeli.
9. Bir film açıkken telefonun Geliştirici seçenekleri → "Etkinlikleri tutma" açıkken ana ekrana çıkıp geri dönün: oynatıcı ekranı (aynı içerik) açık olmalı.
10. Bir film oynarken ana ekran tuşuna basın: video küçük pencerede devam etmeli, pencerede düğme görünmemeli. Pencereye dokunup büyütün: kontroller geri gelmeli. Pencereyi aşağı sürükleyip kapatın: ses durmalı, "Şu an oynatılıyor" bildirimi kalkmalı. Videoyu duraklatıp ana ekrana çıkın: PiP açılmamalı.
11. Bir film açın: ekran yatay ve tam ekran (durum çubuğu gizli) olmalı. Kontrollerdeki Tam ekran / Küçült düğmesine basın: dikeye dönmeli ve durum çubuğu görünmeli; tekrar basınca tam ekrana dönmeli. Sol yarıda parmağı yukarı/aşağı kaydırın: ekran parlaklığı değişmeli; oynatıcıdan çıkınca normal parlaklığa dönmeli.
12. Ana sayfada Öne Çıkan kartını izleyin: 5 sn'de bir film değişmeli. Parmakla sola kaydırın: hemen sonraki film gelmeli. Yetişkin içerik hiç görünmemeli. Ana ekrana çıkıp uygulamaya geri dönün: Öne Çıkan filmler değişmiş olmalı.
13. Spor klasöründen bir kanal açın: kontrollerde önceki/sonraki kanal düğmeleri olmalı ve sadece Spor kanalları arasında geçmeli; kanal listesinde yalnızca Spor kanalları görünmeli. Bir film açın: 10 sn geri/ileri düğmeleri olmalı. Ana sayfadaki Trend/Popüler listelerde yetişkin içerik görünmemeli.

### Android TV / Google TV (kumanda)
1. Açılışta OK'ye basın: açılış atlanmalı.
2. Ana sayfada yön tuşlarıyla kartlar ve alt sekmeler arasında gezinin: her odaklı öğede mor çerçeve görünmeli, kenarlarda boşluk olmalı.
3. Bir karta OK'ye basın → detay paneli: yön tuşlarıyla panelin dışına çıkılamamalı. Geri'ye basın: panel kapanmalı ve odak açtığınız karta dönmeli.
4. Radyo satırında bir kartta OK'yi basılı tutun: favoriye eklenmeli; kısa basış radyoyu çalmalı.
5. Canlı bir kanal açın: CH+ / CH- ile sonraki/önceki kanala geçmeli.
5a. Çalışmayan bir kanal açın: birkaç saniye sonra test videosu değil, "Yayın açılamadı" penceresi çıkmalı; odak "Tekrar Deneyin"de olmalı, OK ile yeniden denenmeli, "Geri" ile oynatıcıdan çıkılmalı.
6. Oynatıcıda kanal listesini açın, Geri'ye basın: önce liste kapanmalı; tekrar Geri → kontroller gizlenmeli; tekrar Geri → oynatıcıdan çıkılmalı.
7. Oynatıcıda ekranı kilitleyin, sonra OK'ye basın: "Kilidi aç" düğmesi görünmeli ve odakta olmalı; OK ile kilit açılmalı.
8. Arama / PIN / profil alanına gelip OK'ye basın: ekran klavyesi açılmalı.
9. Ayarlar → "Telefonla gir": QR kod ekranı açılmalı, telefondan gönderilen liste kaydedilmeli.
