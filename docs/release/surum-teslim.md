# Sürüm Teslimi — 1.1.7 (18)

| Bilgi | Değer |
|---|---|
| Dosya | `CineStream-1.1.7-18.aab` (sohbette gönderildi; depoya eklenmez) |
| Boyut | 11.970.836 bayt (~11,4 MB; Play'de indirme boyutu cihaza göre daha küçük) |
| versionCode / versionName | 18 / 1.1.7 (Play'de en son 17 var) |
| SHA-256 | `2213fea5ebd1e2d831ceadf17dfbd6317c2bef1285d469252eec81810c1dc7d0` |
| İmza | Yükleme anahtarı, SHA-256 parmak izi `B2:3C:DE:7D:…:79:7B:B3` (Play'e kayıtlı anahtar) |
| minSdk / targetSdk | 26 / 36; TV (leanback) ve dokunmatik ekran zorunlu değil |
| Yeni izin | `RECEIVE_BOOT_COMPLETED`: program hatırlatmaları telefon yeniden başlayınca yeniden kurulur |
| Günlük ücretsiz süre | 3600 sn (60 dk); Play lisans anahtarı pakette (satın alma imzası doğrulanır) |
| ABI | arm64-v8a, armeabi-v7a (Mi TV Stick), x86, x86_64 |
| 16 KB | 64 bit .so dosyalarının LOAD hizalaması 16384 |
| Testler | 427 birim testi, iki tam tur (ikincisi baştan derlemeyle): hepsi geçti. lintRelease 0 hata. R8 küçültmede yeni sınıflar (hatırlatma alıcısı, fragman modelleri) korunuyor |
| Dal | `claude/cinestream-stability-compat-cleanup-xlv565` |

Bu sürümde yeni: kanal kartında yayın akışı ve program hatırlatma, oynatıcıda dar kanal paneli, TMDB puanları,
otomatik fragman (önce TMDB, yoksa YouTube), çocuk kilidi düzeltmesi, günde bir liste güncellemesi, TV'de yerinde
arama ve çubukla sarma.

Cihaz/emülatör üzerinde gerçek kurulum testi bu ortamda yapılamadı (emülatör yok). Telefon (realme C55 ölçüleri,
API 35, dikey ve yatay) ve TV (Mi TV Stick ölçüleri, API 28) Robolectric cihaz profili testleriyle denendi
(`ReleaseSmokeTest`, `ReleaseSmokeLandscapeTest`, `ReleaseSmokeTvTest`).
