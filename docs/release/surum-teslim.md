# Sürüm Teslimi — 1.1.9 (20)

| Bilgi | Değer |
|---|---|
| Dosya | `CineStream-1.1.9-20.aab` (13 Kasım 2026'da sohbette gönderildi; depoya eklenmez) |
| Boyut | 12.868.336 bayt (~12,3 MB) |
| versionCode / versionName | 20 / 1.1.9 (kullanıcı onayı: "1.1.9 hazırla") |
| Paket adı | `com.cinestream.iptv` |
| SHA-256 | `cb722502ebc523ede69c23feca3f8691e3ad7c3d454578be5a7a0c97ca95d008` |
| İmza | Yükleme anahtarı `B2:3C:DE:7D:…:79:7B:B3` (Play'e kayıtlı); `jarsigner -verify`: jar verified |
| TV afişi | `drawable-xhdpi/tv_banner.png` pakette |
| Testler | 495 birim testi, 41 bilerek atlandı (görsel/stres), 0 düştü; lintRelease 0 hata |
| Kaynak | `claude/cinestream-stability-compat-cleanup-xlv565`, kod commit'i `6aa2d01` |

Bu sürümde yeni (1.1.8'e göre) yalnızca iki TV çökme düzeltmesi; yeni özellik, izin ya da şema değişikliği yok:

- TV Filmler/Diziler: kategori listesinde hızla inip Sağ, sonra Sol → "FocusRequester is not initialized". Test `TvBrowseDetailTest.fastNavigation_*`.
- TV Ayarlar: hızlı gezinmede panelden Sol/Geri → aynı çökme. Test `TvSettingsTest.fast*`.
- İki test de eski kodda bu hatayla düşüyor, yeni kodda geçiyor (13 Kasım'da doğrulandı).

Play Console sürüm notları (TR ve EN): `docs/release/surum-notlari.md`.

---

# Sürüm Teslimi — 1.1.8 (19)

| Bilgi | Değer |
|---|---|
| Dosya | `CineStream-1.1.8-19.aab` (3 Kasım 2026'da sohbette gönderildi; depoya eklenmez) |
| Boyut | 12.881.218 bayt (~12,3 MB; Play'de indirme boyutu cihaza göre daha küçük) |
| versionCode / versionName | 19 / 1.1.8 (18 = 1.1.7, Play'de TV afişi/simgesi yüzünden reddedildi; bu sürüm onun yerine geçer) |
| Paket adı | `com.cinestream.iptv` (paketteki manifestten okundu) |
| SHA-256 | `dfe1ed11dfef030f95beafea1bf2ab75744aa42c89b857a0fa6f8fae9a30239d` |
| İmza | Yükleme anahtarı, SHA-256 parmak izi `B2:3C:DE:7D:…:79:7B:B3` (Play'e kayıtlı anahtar); `jarsigner -verify`: jar verified |
| minSdk / targetSdk | 26 / 36; TV (leanback) ve dokunmatik ekran zorunlu değil |
| İzinler | 1.1.7'ye göre yeni izin yok |
| TV afişi / simgesi | `drawable-xhdpi/tv_banner.png` 320x180, saydamlıksız (pakette doğrulandı); TV'ye özel simge `mipmap-television-anydpi-v26` |
| Günlük ücretsiz süre | 3600 sn (60 dk); Play lisans anahtarı pakette (ortamdaki anahtarla aynı; değeri yazılmaz) |
| ABI | arm64-v8a, armeabi-v7a (Mi TV Stick), x86, x86_64 |
| 16 KB | 64 bit .so dosyalarının LOAD hizalaması 16384 |
| Yerel semboller | Pakette sembol tablosu yok: derleme ortamında NDK kurulu değildi. Tek yerel kod AndroidX'in iki küçük kütüphanesi (toplam ~60 KB). Play Console "hata ayıklama sembolleri" uyarısı gösterebilir; yayını engellemez |
| Testler | 493 birim testi: 452 geçti, 41 bilerek atlandı (yalnızca istenince çalışan görsel ve stres testleri), 0 düştü. `AiReportSenderTest` ilk turda Robolectric'in Maven Central'dan dosya indirememesi yüzünden hiç çalışamadı; tek başına yeniden çalıştırıldı, 3/3 geçti. `TvLauncherArtTest` ve yeni `TvNoBrowserTest` geçti. lintRelease 0 hata |
| Kaynak | `claude/cinestream-stability-compat-cleanup-xlv565`, kod commit'i `30e2bcd` (sonrakiler yalnızca belge) |

Bu sürümde yeni (1.1.7'ye göre):

- Play reddinin düzeltmesi: TV ana ekran afişi (320x180, uygulama adıyla) ve alanı dolduran TV simgesi.
- TV kalite denetimi (3 Kasım): TV'de tarayıcı açılmaz (TV-WB), TV'de uygulama arka plana geçince radyo durur (TV-NP).
- Akıcı oynatma: donan yayın kendiliğinden yeniden bağlanır, akıllı tampon, kısa bağlantı zaman aşımı.
- Kaydedilenler'de "Nerede kalmıştık", kategori görselleri, yenilenen Ayarlar ve "Fragmanı otomatik oynat".
- Uygulamada oynatılamayan fragman YouTube'da açılır; otomatik sonraki bölüm (10 sn sayaç).
- Daha az bellek, daha hızlı açılış; tanılama ekranı gizli (sürüm satırına 7 dokunuş); hata düzeltmeleri.

Play Console sürüm notları (TR ve EN): `docs/release/surum-notlari.md`.

Cihaz/emülatör üzerinde gerçek kurulum testi bu ortamda yapılamadı (emülatör yok). Telefon (realme C55 ölçüleri,
API 35, dikey ve yatay) ve TV (Mi TV Stick ölçüleri, API 28) Robolectric cihaz profili testleriyle denendi
(`ReleaseSmokeTest`, `ReleaseSmokeLandscapeTest`, `ReleaseSmokeTvTest`).
