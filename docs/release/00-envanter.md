# CineStream — Teknik ve Özellik Envanteri (Faz 0)

Tarih: 6 Ekim 2026 · Dal: `release/play-audit` · Başlangıç etiketi: `pre-audit` (commit `239af26`)

Bu belge kod değiştirilmeden, depo ve son release derlemesinin birleşik (merged) manifest'i incelenerek hazırlandı.

## 1. Proje ve araç zinciri

| Öğe | Değer |
|---|---|
| Modüller | Tek modül: `:app` |
| applicationId | `com.cinestream.iptv` (kalıcı, geçici değil) |
| namespace | `com.example` (yalnızca kod paketi; Play'i etkilemez) |
| versionCode / versionName | 2 / 1.0.1 (Play dahili testte **1 / 1.0** yüklü) |
| Gradle | 9.3.1 |
| AGP | 9.1.1 |
| Kotlin | 2.2.10 (KSP 2.3.7) |
| compileSdk / targetSdk / minSdk | 36.1 / **36** / 26 |
| Java hedefi | 11 |
| R8 | `isMinifyEnabled = true`, `isShrinkResources = true` (release) |
| Lint | `abortOnError = false`, `checkReleaseBuilds = false` (hatalar derlemeyi durdurmuyor) |
| Derleme türleri | `debug` (sınır kapalı), `release` (sınır açık), `qa` (release benzeri, debug imzalı, sınır kapalı) |
| İmzalama | Ortam değişkenlerinden (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`); `tools/build_release_aab.sh` parmak izi doğrular. Yükleme anahtarı mevcut, Play App Signing etkin. |
| AAB dil bölmesi | Kapalı (uygulama içi TR/EN geçişi için bilinçli) |

## 2. Bağımlılıklar

| Alan | Kütüphane | Sürüm | Not |
|---|---|---|---|
| UI | Compose BOM | 2024.09.00 | Eski; minor güncelleme adayı |
| UI | Compose for TV (`tv-material`) | 1.0.0 | |
| UI | activity-compose | 1.10.1 | |
| Yaşam döngüsü | lifecycle-* | 2.8.7 | `collectAsStateWithLifecycle` mevcut ama hiç kullanılmıyor |
| Oynatıcı | Media3 exoplayer, hls, ui, datasource-okhttp | **1.4.1** | DASH modülü yok; `media3-session` yok |
| Veritabanı | Room | 2.7.0 | Şema dosyaları `app/schemas/` |
| Ayarlar | DataStore Preferences | 1.1.7 | Şifreleme yok |
| Görsel | Coil (compose) | 2.7.0 | |
| Ağ | OkHttp / logging-interceptor | 4.10.0 | Logging yalnızca debug |
| Ağ | Retrofit + Moshi | 2.12.0 / 1.15.2 | TMDB |
| Arka plan | WorkManager | 2.9.1 | |
| Ödeme | Play Billing (`billing-ktx`) | **9.1.0** | Play'in 8+ şartını karşılıyor |
| Değerlendirme | Play In-App Review | 2.0.2 | |
| Yapay zekâ | Gemini — **doğrudan REST** (`generativelanguage.googleapis.com/v1beta`) | — | SDK kullanılmıyor |
| Yapay zekâ | `firebase-ai`, `firebase-appcheck-recaptcha` (BOM 34.15.0) | — | **Kodda hiç kullanılmıyor**; manifest'e Firebase servisleri ve `datatransport` telemetri bileşenleri ekliyor |
| QR | ZXing core | 3.5.3 | TV "telefondan gir" |
| Yerel sunucu | Yok (kendi `ServerSocket` uygulaması, `PhoneEntry.kt`) | — | Tek kullanımlık token var |
| Test | Robolectric 4.16.1, Roborazzi 1.59.0, MockWebServer | — | |
| Eklentiler | secrets-gradle-plugin 2.0.1, google-services 4.5.0 | — | `google-services.json` yok (WARN) |

## 3. Native kütüphaneler (AAB içinde)

| Dosya | Kaynak | ABI |
|---|---|---|
| `libandroidx.graphics.path.so` | androidx.graphics (Compose) | arm64-v8a, armeabi-v7a, x86, x86_64 |
| `libdatastore_shared_counter.so` | androidx.datastore | arm64-v8a, armeabi-v7a, x86, x86_64 |

İkisi de AndroidX kaynaklı ve küçük. 16 KB hizalaması Faz 2'de `zipalign -c -P 16` ile **doğrulanacak** (henüz doğrulanmadı).

## 4. Birleşik manifest

### İzinler

| İzin | Kaynak | Kullanım |
|---|---|---|
| INTERNET, ACCESS_NETWORK_STATE | Uygulama | Yayın, TMDB, Gemini |
| POST_NOTIFICATIONS | Uygulama | Oynatma bildirimi, hatırlatma |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK | Uygulama | Arka planda oynatma |
| RECORD_AUDIO | Uygulama | TV'de sesli arama (`TvVoiceInput.kt`) — `uses-feature microphone required=false` |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED | WorkManager | Uygulamada BOOT alıcısı yok |
| com.android.vending.BILLING | Billing | PRO |
| READ_GSERVICES | Firebase | Kullanılmayan Firebase'den |
| DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | AndroidX core | Standart |

Yasaklı/hassas izin yok: MANAGE/READ/WRITE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES, REQUEST_INSTALL_PACKAGES, SYSTEM_ALERT_WINDOW, exact alarm, konum, kamera, READ_PHONE_STATE bulunmuyor.

### uses-feature

`android.software.leanback`, `android.hardware.touchscreen`, `android.hardware.microphone`: hepsi `required="false"` ✅

### Bileşenler (dışa açık olanlar)

| Bileşen | exported | Not |
|---|---|---|
| `MainActivity` | true | LAUNCHER + LEANBACK_LAUNCHER, `android:banner` var |
| WorkManager `SystemJobService`, `DiagnosticsReceiver` | true | Kütüphane, izinle korunuyor |
| `ProfileInstallReceiver` | true | Kütüphane, izinle korunuyor |
| `PlaybackForegroundService` | false | `foregroundServiceType="mediaPlayback"` ✅ |
| Diğer tüm bileşenler | false | |

### Uygulama özellikleri

`allowBackup=true`, `dataExtractionRules` + `fullBackupContent` (DataStore, SharedPreferences ve çökme kayıtları hariç; **Room veritabanı hariç değil**), `usesCleartextTraffic=true` + `network_security_config` (gerekçeli; kullanıcı sertifikalarına güveniyor), `supportsPictureInPicture=true`.

## 5. Özellik envanteri

| Özellik | Telefon | TV | Not |
|---|---|---|---|
| Liste ekleme (M3U URL, Xtream) | ✅ | ✅ (+ QR ile telefondan) | Yerel M3U dosyası seçimi bulunamadı |
| Canlı TV | ✅ | ✅ | |
| Film | ✅ | ✅ | |
| Dizi | ✅ | ✅ | Telefonda tüm dizi satırları belleğe alınıyor (bkz. bulgular) |
| Radyo/müzik | ✅ | ? | `RadioPlayerManager` ayrı ExoPlayer |
| EPG (XMLTV, .gz) | ✅ | ✅ | `RealEpgProvider` XmlPullParser + GZIP |
| Arama | ✅ | ✅ | |
| Favoriler | ✅ | ✅ | |
| AI film asistanı (Gemini) | ✅ | ✅ (`TvAssistantScreen`) | Bildir düğmesi ve AI uyarısı yok |
| Ruh hali önerileri / AI özetler | ✅ | kısmi | |
| Oyuncu/yönetmen filmografisi | ✅ | ✅ | `PersonWorksMatcher` |
| "Sinemada Bu Hafta" | ✅ | ? | Kütüphanedeki vizyon filmi dokununca oynuyor |
| Uygulama içi fragman (YouTube IFrame) | ✅ | ✅ | |
| Paywall / Play Billing | ✅ | ✅ | Günlük **60 dk** sınır |
| Ebeveyn kilidi (PIN) | ✅ | ✅ | Varsayılan **kapalı** |
| PiP | ✅ uygulanmış | — | `PictureInPicture.kt`, `setAutoEnterEnabled` |
| Arka planda oynatma | ✅ | — | Özel ön plan hizmeti, MediaSession yok |
| Ayarlar > Çökme Günlükleri | ✅ | ✅ | Release'de ayrıntılı tanılama kapalı |
| Hukuki metinler (uygulama içi) | ✅ | ✅ | Gizlilik + Hizmet Şartları |
| Catch-up / timeshift | ❌ | ❌ | Yol haritası |
| Tam ekran EPG ızgarası | ❌ | ❌ | Yol haritası |
| Chromecast | ❌ | ❌ | Yol haritası |

## 6. Geçmiş düzeltmelerin doğrulaması

| Kontrol | Sonuç |
|---|---|
| `SubcomposeAsyncImage` hiç yok | ✅ Doğrulandı (0 kullanım) |
| Lazy listelerde stable key (`RankedTop10Section` dahil) | Faz 3'te dosya dosya doğrulanacak |
| "Sinema Salonu" kaldırılmış | ✅ Doğrulandı |
| Sabit/sahte puanlar (6.8, 8.0) | Faz 3'te doğrulanacak |
| "Reklamsız" vaadi | ✅ Arayüzde yok; yalnızca gizlilik politikasında doğru bir beyan ("reklam yok") var |
| Giriş indirmesinin döngüye girmemesi | `syncMutex` var; cihazda **doğrulanmadı** |
| 63.000+ dizi satırının tek seferde sorgulanmaması | ❌ **Telefonda hâlâ tek sorgu** (`getItemsByTypeFlow("SERIES")` tüm sütunlarla); TV kataloğu sayfalı |
| Release'de tanılama dosyası/ölü kod | `DiagnosticLog` release'de kapalı ✅; ölü kod taraması Faz 7 |

## 7. Bekleyen işler

| Konu | Durum |
|---|---|
| Telefonda PiP | Uygulanmış (Android 12+ otomatik giriş) |
| TV catch-up/timeshift, EPG ızgarası, Chromecast | Yok — yol haritası |
| TV'de "telefondan gir" QR (Gemini, M3U, Xtream) | Var; tek kullanımlık rastgele token, 15 sn soket zaman aşımı |
| PRO paywall / Play Billing | Uygulanmış (9.1.0); Play Console'da ürünler henüz oluşturulmadı |
