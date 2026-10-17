# Sürüm Teslimi — 1.1.6 (17)

| Bilgi | Değer |
|---|---|
| Dosya | `CineStream-1.1.6-yayin.aab` (sohbette gönderildi; depoya eklenmez) |
| Boyut | 11.931.260 bayt (~11,4 MB; Play'de indirme boyutu cihaza göre daha küçük) |
| versionCode / versionName | 17 / 1.1.6 |
| SHA-256 | `95ebc89a40da0eed21cd5c287ab91f5a32b34dbefe7c1a89e53dcda529fc5604` |
| İmza | Yükleme anahtarı, SHA-256 parmak izi `B2:3C:DE:7D:…:79:7B:B3` (Play'e kayıtlı anahtar); `jarsigner -verify` başarılı |
| minSdk / targetSdk | 26 / 36; TV (leanback) ve dokunmatik ekran zorunlu değil |
| Günlük ücretsiz süre | 3600 sn (60 dk); Play lisans anahtarı pakette (satın alma imzası doğrulanır) |
| ABI | arm64-v8a, armeabi-v7a (Mi TV Stick), x86, x86_64 |
| 16 KB | 64 bit .so dosyalarının LOAD hizalaması 16384 |
| Testler | 391 birim testi, temiz derlemeyle 1 tur + 2 tur daha baştan: hepsi geçti. Yük testleri (200 bin içerik, 60 bin kanal, 5 bin kanal değişimi) geçti. lintRelease 0 hata |
| Dal | `claude/cinestream-stability-compat-cleanup-xlv565` |

Cihaz/emülatör üzerinde gerçek kurulum testi bu ortamda yapılamadı (emülatör yok); telefon (realme C55 ölçüleri, API 35) ve TV (Mi TV Stick ölçüleri, API 28, büyük liste) Robolectric cihaz profili testleriyle denendi.
