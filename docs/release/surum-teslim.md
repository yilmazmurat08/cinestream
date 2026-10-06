# Sürüm Teslimi — 1.0.2 (3)

| Bilgi | Değer |
|---|---|
| Dosya | `CineStream-1.0.2.aab` (sohbette gönderildi; depoya eklenmez) |
| Boyut | 11.723.798 bayt (~11,2 MB; Play'de indirme boyutu cihaza göre daha küçük) |
| versionCode / versionName | 3 / 1.0.2 |
| SHA-256 | `25fb07c6d91b994ba2f42e4931a61f7b756c74f42cf1afcacc3f5982ea254f30` |
| İmza | Yükleme anahtarı, SHA-256 parmak izi `B2:3C:DE:7D:…:79:7B:B3` (Play'e kayıtlı anahtar) |
| Kaynak | `release/play-audit` dalı |
| ABI | arm64-v8a, armeabi-v7a, x86, x86_64 |
| 16 KB | Release benzeri APK'da `zipalign -c -P 16` başarılı; tüm .so LOAD hizalaması 16384 |
| Testler | 185 birim testi geçti; lintRelease 0 hata |

bundletool bu ortamda yüklü olmadığından AAB'den cihaza özel APK seti çıkarılamadı. 16 KB kontrolü aynı kodla üretilen `qa` APK'sı üzerinde yapıldı. Cihaz/emülatör duman testi **doğrulanmadı** (bkz. RELEASE_REPORT.md bölüm 4).
