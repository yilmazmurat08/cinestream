# CineStream — Önceliklendirilmiş Bulgular (Faz 0)

Tarih: 6 Ekim 2026 · Dal: `release/play-audit` · Kod henüz değiştirilmedi.

Öncelikler:
- **P0:** Play reddi veya askıya alma, çökme, ANR ya da veri kaybı → mutlaka düzeltilir.
- **P1:** Belirgin takılma, kötü deneyim, profesyonellik eksiği → düzeltilir.
- **P2:** İyileştirme → raporlanır; küçük ve risksizse uygulanır.

"Onay" sütunu: ✋ = uygulamadan önce senin kararın gerekiyor.

## P0

| ID | Alan | Sorun | Dosya:satır | Önerilen çözüm | Risk | Onay |
|---|---|---|---|---|---|---|
| P0-01 | Politika / Fikri mülkiyet | Klasör kartlarında **sabit marka etiketleri**: "Netflix Özel Sinema Kuşağı", "Disney+ Orijinal Dizileri", "BluTV Özel Dizileri", "Exxen Spor & Canlı Yayınlar" (TR+EN, 20 metin). Kategori adında "netflix" geçen klasöre uygulama bu yazıyı kendisi ekliyor. Korsan platform içeriği sunuyormuş izlenimi verir. | `res/values/strings.xml:312-331`, `values-en/strings.xml:313-332`, `ui/screens/FolderGridScreen.kt:533-553` | Marka etiketlerini kaldır; tüm klasörlerde içerik türüne göre nötr etiket ("Canlı yayınlar", "Filmler", "Diziler"). | Düşük; görsel tasarım değişmez, yalnızca alt yazı. | |
| P0-02 | Politika / Marka | Giriş ekranı sloganı "Apple zarafeti ile Netflix dinamizminin harmanlandığı…" iki marka adı içeriyor. | `res/values/strings.xml:55`, `values-en/strings.xml:50`, `LoginScreen.kt:212` | Markasız slogan (öneri aşağıda). | Düşük | ✋ metin |
| P0-03 | Politika / Fikri mülkiyet | "Sinemada Bu Hafta": vizyondaki bir film kullanıcının listesinde varsa "kütüphanende, dokununca oynar" deniyor ve dokununca **oynatılıyor**. Vizyondaki filmin IPTV listesinde olması korsanlık işaretidir; uygulama bunu öne çıkarıyor. | `ui/components/NowPlayingSection.kt:59-69, 141-146, 241-260`; `HomeScreen.kt:515` | Satırdan oynatma kaldırılır; her film yalnızca bilgi ve fragman sayfasını açar. Rozet konusunda iki seçenek (aşağıda). | Düşük | ✋ rozet |
| P0-04 | Politika / İçerik kaynağı | Liste ekleme penceresinde örnek olarak gerçek bir kanal listesi adresi gösteriliyor: `https://iptv-org.github.io/iptv/countries/tr.m3u`. | `ui/components/PlaylistAddDialog.kt:181` | Diğer ekranlardaki gibi `http://example.com/playlist.m3u` yer tutucusu. | Yok | |
| P0-05 | Politika / Yapay zekâ | Gemini yanıtlarında **uygulama içi "Bildir/İşaretle"** seçeneği yok (AI-Generated Content politikası zorunlu kılıyor). | `MovieFinderChatScreen.kt`, `TvAssistantScreen`, detay/özet ekranları | Her AI yanıtına "Bildir" eylemi: gerekçe + isteğe bağlı not. Gönderim yöntemi için seçenekler aşağıda. | Orta | ✋ yöntem |
| P0-06 | Politika / Yapay zekâ | Gemini isteklerinde **güvenlik ayarı (`safetySettings`) ve sistem talimatı yok**; asistan film/dizi dışı konulara da yanıt verebilir; "yapay zekâ tarafından üretildi, hatalı olabilir" notu yok. | `data/api/MetadataEnricher.kt:917, 1234, 2159, 2301`; `IPTVViewModel.kt:618` | Tek bir Gemini istemcisi: `systemInstruction` (yalnızca film/dizi), `safetySettings` (taciz, nefret, cinsel, tehlikeli: BLOCK_MEDIUM_AND_ABOVE), ret mesajı; yanıtların altında küçük TR/EN AI notu. | Orta; yanıt kalitesi testle doğrulanacak | |
| P0-07 | Politika / Sorumluluk reddi | "CineStream bir medya oynatıcısıdır; içerik, kanal veya abonelik sağlamaz…" uyarısı ilk açılışta ve Ayarlar > Hakkında'da yok (yalnızca hukuki metinlerin içinde). | `LoginScreen.kt`, `SettingsScreen.kt`, `TvSettingsScreen.kt` | TR/EN uyarı metni: liste ekleme ekranının altında ve Ayarlar'da "Hakkında" bölümünde. | Düşük | |
| P0-08 | Politika / Yanıltıcı kimlik | Akış istekleri sırayla **başka uygulamaların kimliğiyle** gönderiliyor (TiviMate, IPTV Smarters Pro, OTT Navigator, VLC, iPad). Sağlayıcı engellerini aşmak için taklit gibi okunur. | `data/repository/IPTVRepository.kt:176-185`; `player/ExoPlayerConfigurator.kt:182`; `player/RadioPlayerManager.kt:66` | Uygulamanın kendi tutarlı User-Agent'ı (`CineStream/<sürüm> (Linux; Android)`) + M3U'daki `http-user-agent`/`#EXTVLCOPT` değerine uyma. | **Orta**: bazı sağlayıcılar yalnızca bilinen oynatıcılara izin veriyor olabilir; senin listende denenmeli. | ✋ |
| P0-09 | Veri bütünlüğü | Kanal adı "düzeltme" işlevi **film ve dizi adlarına da** uygulanıyor ve `contains` ile çalışıyor: "Now You See Me", "Snowden" → "NOW"; "Again", "Bargain" → "Gain"; adında "sinema" geçen film → "Sinema TV"; "atv/ntv/trt" geçen her ad kanal adına dönüşüyor. M3U listelerinde film/dizi adları bozuluyor. | `data/repository/IPTVRepository.kt:831, 958-1030` | Marka/kanal yeniden adlandırmasını tamamen kaldır; yalnızca köşeli parantez, ülke öneki ve kalite eki temizliği kalsın. | Düşük; eski kayıtlar bir sonraki senkronizasyonda düzelir | |
| P0-10 | 16 KB sayfa boyutu | İki AndroidX native kütüphanesinin hizalaması henüz kanıtlanmadı. | AAB `base/lib/*` | `zipalign -c -P 16 -v 4` ve bundletool APK'ları üzerinde kontrol; gerekirse ilgili AndroidX sürümünü minor seviyede yükselt. | Düşük | |

## P1

| ID | Alan | Sorun | Dosya:satır | Önerilen çözüm | Risk | Onay |
|---|---|---|---|---|---|---|
| P1-01 | Performans / Bellek | Telefon modunda **her tür için tüm satırlar tüm sütunlarıyla** belleğe alınıyor (`SELECT * … WHERE type = :type`); 63.000+ dizi satırı her veritabanı değişikliğinde yeniden okunuyor ve regex ile yeniden gruplanıyor. CursorWindow hatasına karşı "uzun alanları kırp ve tekrar dene" yaması var. 1–2 GB RAM'li cihazlarda donma/ANR riski. | `data/db/IPTVDao.kt:52`; `IPTVViewModel.kt:60, 948, 994`; `IPTVRepository.kt:92-104` | Liste ekranları için yalnızca gereken sütunları seçen hafif sorgular + sorgu seviyesinde gruplama (dizi adı/sezon) ve sayfalama (TV kataloğundaki `LIMIT/OFFSET` yapısıyla aynı yaklaşım). Arayüz değişmez. | **Yüksek**: telefonun ana veri akışı; aşamalı yapılacak, her adımda test. | ✋ kapsam |
| P1-02 | Yapay zekâ | Model adları 5 yere dağılmış; `gemini-1.5-flash` kullanımdan kalkmış, `gemini-3.5-flash`/`flash-latest` sabit değil. | `MetadataEnricher.kt:917, 1234, 2159, 2301`; `IPTVViewModel.kt:618` | Tek `GeminiModels` sabiti; güncel kararlı model internetten doğrulanarak seçilir. | Düşük | |
| P1-03 | Güvenlik | Gemini anahtarı URL'de (`?key=`) gönderiliyor; ara katmanlarda veya hata mesajlarında görünebilir. | Aynı satırlar | `x-goog-api-key` başlığı. | Düşük | |
| P1-04 | Güvenlik | Gemini anahtarı, Xtream kullanıcı adı/şifresi ve liste adresleri **şifresiz** saklanıyor (DataStore + Room). | `SettingsRepository.kt:26, 196`; Room `playlists`, `iptv_items.streamUrl` | Anahtar ve Xtream bilgileri için Android Keystore tabanlı şifreleme (Tink; güncel durumu doğrulanacak). Room'daki akış adreslerinin hepsini şifrelemek büyük iş → yedekleme dışı bırakma (P1-05) ile birlikte ele alınır. | Orta; mevcut verinin şifreliye **taşınması** (migration) gerekir, veri kaybı olmamalı | ✋ kapsam |
| P1-05 | Güvenlik / Yedekleme | Room veritabanı (Xtream bilgileri içeren adresler dahil) bulut yedeğine ve cihaz aktarımına **dahil**. | `res/xml/data_extraction_rules.xml`, `backup_rules.xml` | `database` alanını yedeklemeden çıkar. | Yok (yeni cihazda liste yeniden eklenir) | |
| P1-06 | Gizlilik | Profil için varsayılan avatar, Unsplash'tan **gerçek bir kişinin fotoğrafı**; ayrıca gizlilik politikasında yer almayan bir üçüncü tarafa istek. | `IPTVViewModel.kt:1957` | Paket içi nötr avatar (baş harf veya ikon). | Yok | |
| P1-07 | Gereksiz SDK | `firebase-ai` ve `firebase-appcheck-recaptcha` kodda **hiç kullanılmıyor**; manifest'e Firebase ve `datatransport` telemetri bileşenleri ile `READ_GSERVICES` izni ekliyor. Veri güvenliği beyanını karmaşıklaştırır. | `app/build.gradle.kts` (firebase satırları, google-services eklentisi) | Kaldır. | Düşük | |
| P1-08 | Ücretsiz katman | Prompttaki tasarımla **çelişki**: uygulama, mağaza metni, Hizmet Şartları ve gizlilik politikası **günlük 60 dk** diyor; prompt **2 saat** diyor. Ayrıca sınır dolunca **oynayan içerik durduruluyor** (prompt: kesilmesin, yalnızca yeni başlatma engellensin) ve canlı TV için son 15 dk geri sayım yok. | `IPTVViewModel.kt:46, 432-449`; `PlayerScreen.kt:252-257` | Kararına göre: süre, kesme davranışı ve geri sayım. Süre değişirse hukuki metinler + mağaza metni + Google Dokümanlar da güncellenir. | Orta | ✋ |
| P1-09 | Çocuk/yetişkin | Yetişkin kategorileri ana sayfa ve önerilerden gizleniyor ama klasör listesinde **varsayılan olarak açık**; ebeveyn kilidi varsayılan kapalı. | `SettingsRepository.kt:182`; `FolderGridScreen.kt:491` | Yetişkin klasörleri varsayılan gizli; PIN belirlenip girilince görünür. | Düşük-orta (mevcut kullanıcıların görünümü değişir) | ✋ |
| P1-10 | Politika / Metin | "Sınırsız Eğlence…" açılış sloganı, PRO'nun içerik sattığı izlenimine yakın; "Google ile Giriş Yap" sahte Google düğmesi görseli (yardım ekranında, gerçek giriş değil). | `strings.xml:245, 420`; `LoginScreen.kt:1043-1063` | Nötr slogan; sahte "G" düğmesi yerine düz talimat metni. | Düşük | ✋ metin |
| P1-11 | Politika / Paywall | PRO maddeleri zaten "günlük 60 dk sınırı olmadan sınırsız izleme" diyor ama "kendi listelerinde" vurgusu yok. | `strings.xml:625, 648, 651` | "Kendi listelerinde sınırsız izleme süresi" (öneri aşağıda). | Düşük | ✋ metin |
| P1-12 | Oynatıcı | Canlı yayında `BehindLiveWindowException` özel olarak ele alınmıyor; Media3 1.4.1 eski; MediaSession yok (kilit ekranı, kulaklık/medya tuşları, ses odağı yalnızca kısmen). | `PlayerScreen.kt:577-600`; `PlaybackForegroundService` | Canlı pencere hatasında `seekToDefaultPosition()+prepare()`; Media3'ü 1.x içinde güncelleme ve `media3-session` ile MediaSession. | Orta: oynatıcı çekirdeği; ayrıntılı test gerekir | ✋ Media3 sürümü |
| P1-13 | Oynatıcı | Video yüzeyi `TextureView`; SurfaceView daha verimli (pil, HDR, düşük donanım). | `res/layout/player_view_texture.xml:7`; `PlayerScreen.kt:1063` | `surface_view`'e geçiş; görüntü oranı/zoom ve PiP ile test. | Orta: zoom/döndürme davranışı değişebilir | |
| P1-14 | Yerel dosya | Yerel M3U dosyası ekleme yok. | — | Storage Access Framework (`ACTION_OPEN_DOCUMENT`); izin gerektirmez. | Düşük | ✋ (yeni özellik) |
| P1-15 | Lint | `abortOnError=false`, `checkReleaseBuilds=false`: çökmeye yol açabilecek lint hataları derlemeyi durdurmuyor. | `app/build.gradle.kts` (lint bloğu) | Mevcut `lintRelease`: **0 hata, 168 uyarı** (çoğu yazım/biçim: TypographyEllipsis 42, UseKtx 28, sürüm uyarıları 41). Çökme riskli kural (NewApi, MissingPermission, UnspecifiedRegisterReceiverFlag) **yok**. Hatalar derlemeyi durduracak şekilde kontrol açılır; anlamlı uyarılar temizlenir. | Düşük | |
| P1-16 | Compose | `collectAsState()` 168 yerde; `collectAsStateWithLifecycle` hiç yok (arka planda gereksiz toplama). | Çeşitli | Kademeli geçiş; ekran ekran. | Düşük | |

## P2

| ID | Alan | Sorun | Önerilen çözüm |
|---|---|---|---|
| P2-01 | Ağ güvenliği | Kullanıcı sertifikalarına release'de de güveniliyor (reklam engelleyici/VPN kullanıcıları için bilinçli karar, dosyada gerekçeli). Prompt "güvenme" diyor. | Kararına göre; ✋ |
| P2-02 | Hakkında | Ayarlar'da sürüm, TMDB logosu+notu var; YouTube Hizmet Şartları bağlantısı ve açık kaynak lisansları yok. | Hakkında bölümüne ekle |
| P2-03 | Veri silme | "Tüm verileri sil" seçeneği uygulamada yok (Android ayarlarından yapılıyor). | Ayarlar'a onaylı "Tüm verileri sil" |
| P2-04 | Splash | Splash Screen API kullanılmıyor (özel açılış ekranı var). | Android 12+ sistem splash'ı ile çakışma kontrolü |
| P2-05 | Kod boyutu | `IPTVViewModel` 2.953, `HomeScreen` 3.055, `PlayerScreen` 2.859, `MetadataEnricher` 2.452 satır. | Yalnızca performans sorunu çözülürken bölünür |
| P2-06 | Ölçüm | Macrobenchmark / Baseline Profile modülü yok. | Faz 8'de eklenir |
| P2-07 | Oynatıcı | DASH modülü yok; TV'de kanal tuşu basılı tutma debounce'u doğrulanacak. | Faz 4 |

## Prompt ile mevcut durum arasındaki çelişkiler (karar gerekiyor)

| Konu | Prompt | Mevcut durum | Önerim |
|---|---|---|---|
| Mağaza adı | "CineStream AI TV" | Play Console'a "CineStream: IPTV Oynatıcı" kaydedildi | Değiştirilebilir (ad sonradan değişebilir). "IPTV" kelimesi bazı incelemecilerde dikkat çeker; "CineStream AI TV" daha nötr. Senin kararın. |
| Ücretsiz süre | Günlük 2 saat, oynayan kesilmez | 60 dk, oynayan durdurulur | P1-08 |
| versionCode | İlk sürüm 1 / "1.0.0" | Play'de 1 / "1.0" zaten yüklü; 2 / "1.0.1" hazır | Sonraki yükleme ≥ 2 olmalı; versionName "1.0.1" kalabilir |
| Keystore | Yoksa yeni oluştur, Drive'a taşı | Mevcut ve Play'e kayıtlı | **Değiştirilmeyecek**. Drive'a yedeklemeyi sen yapacaksın. |
| Gizlilik politikası | Taslak hazırla | Google Dokümanlar'da yayında ve uygulamadakiyle eşit | Değişiklik olursa ikisi birlikte güncellenir |
| Dal | `release/play-audit` | Oturum dalı `claude/cinestream-stability-compat-cleanup-xlv565` | `release/play-audit` yerelde açıldı; GitHub'a göndermek için onayın gerekiyor |

## Önerilen metinler (onayına)

- **P0-02 giriş sloganı** — TR: "Kendi listelerin için yapay zekâ destekli, hızlı ve şık bir oynatıcı." · EN: "A fast, elegant, AI-assisted player for your own playlists."
- **P1-10 açılış sloganı** — TR: "İzlemenin daha akıllı hali" · EN: "A smarter way to watch"
- **P1-11 PRO maddesi** — TR: "Kendi listelerinde günlük süre sınırı olmadan izleme" · EN: "Watch your own playlists with no daily time limit"
- **P0-07 sorumluluk reddi** — TR: "CineStream bir medya oynatıcısıdır; herhangi bir içerik, kanal veya abonelik sağlamaz ya da satmaz. Yalnızca erişim hakkına sahip olduğunuz kaynakları ekleyin." · EN: "CineStream is a media player; it does not provide or sell any content, channels or subscriptions. Only add sources you have the right to access."

## P0-03 rozet seçenekleri

1. **Rozeti tamamen kaldır** (önerim): satır yalnızca vizyondaki filmleri bilgi ve fragmanla gösterir. Korsanlık izlenimi tamamen ortadan kalkar.
2. Promptta önerildiği gibi nötr "Kütüphanende" rozeti, oynatma yok. Vizyondaki filmle IPTV listesini eşleştirmek yine dikkat çekebilir.

## P0-05 "Bildir" gönderim seçenekleri

Politika, bildirimin **uygulamadan çıkmadan** yapılabilmesini istiyor. Bu yüzden yalnızca e-posta uygulamasını açmak yeterli değil.

1. **Google Form (önerim):** Uygulama içinde kendi tasarımımızla bir bildirim penceresi açılır (gerekçe + not). "Gönder"e basınca bilgiler arka planda senin Google Formuna iletilir, yanıtlar Google E-Tablolar'da toplanır. Sunucu gerekmez. Formu sen oluşturursun; adımlarını ben veririm.
2. **Kendi sunucun** (Firebase vb.): Aynı pencere, gönderim kendi altyapına. Yeni altyapı, maliyet ve gizlilik politikası güncellemesi gerekir.
3. **Yedek olarak e-posta:** İnternet yoksa veya form ulaşılamazsa e-posta taslağı açılır. Tek başına yeterli değil.

Her üçünde de bildirim cihazda ayrıca kaydedilir ve o yanıt kullanıcıya gizlenir.
