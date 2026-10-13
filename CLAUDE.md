# CineStream — Claude için proje notları

Android IPTV oynatıcısı: Kotlin, Jetpack Compose, Media3, Room (şema v10), DataStore, Play Billing. Telefon ve Android TV.
Mağaza adı: **CineStream IPTV Player**. Paket adı: `com.cinestream.iptv`.

## Kullanıcı hakkında

- Kodlamaya yeni başlıyor. Her zaman **Türkçe**, kısa ve sade yaz.
- Kararları kullanıcı verir. Önerini nedeniyle birlikte söyle.

## Kurallar (kullanıcının kendi kuralları)

- Yalnızca istenen soruna odaklan. Başka alana dokunma, yeni sorun açma.
- Uygulamanın bozulmasına asla izin verme; en önemli kriter budur.
- Önce test: Paketten önce tüm testleri ve lint'i çalıştır.
  - Komut: `./gradlew testDebugUnitTest lintRelease`.
- **APK ya da AAB'yi sormadan üretme.**
- **Sürümü sormadan yükseltme.** Play aynı sürüm kodunu iki kez kabul etmez; gerekirse sor.
- Telefon tasarımını istenmeden değiştirme. TMDB anahtarlarına, adreslerine ve eşleştirmeye dokunma.
- Kullanıcı verisini asla silme.
- Room şeması değişirse göç (migration) yaz.
- Yeni metinler hem Türkçe hem İngilizce olsun (`values` ve `values-en`). TV'de aynı özellikler bulunsun.
- PR'ı yalnızca kullanıcı "PR aç ve main'e birleştir" deyince aç ve birleştir.

## Güvenlik

- Anahtar, şifre ve keystore asla depoya, kayıtlara ya da sohbete girmez.
- Kullanıcıdan gizli bilgiyi sohbete yazmasını isteme.
- İmzalı AAB: `tools/build_release_aab.sh`.
  - Anahtar ortam değişkenlerinden okunur: `CINESTREAM_KEYSTORE_BASE64` ve `CINESTREAM_KEYSTORE_PASSWORD`.
- Test kullanıcılarının e-posta adresleri yalnızca Play Console'da durur.

## Yol haritası (13 Ekim 2026 kararları)

Test süreci bitip uygulama yayına girince **ilk güncellemede** yapılacaklar:

1. **Ölçüm:** Firebase Analytics ve Crashlytics.
   - Kullanıcı Firebase projesini açar ve `google-services.json` dosyasını verir.
   - Gizlilik metni ve Play Veri Güvenliği formu güncellenir.
2. **PRO'da 7 günlük ücretsiz deneme.** Deneme Play Console'da abonelik teklifi olarak tanımlanır.
3. **TV'de yayın akışı tablosu:** kanallar satır, saatler sütun. Herkese açık.
4. **Hızlı kanal değiştirme:** numarayla kanal açma ve önceki kanala dönme. Herkese açık.
5. **İlk PRO turu:** Bugün PRO yalnızca günlük 60 dakikalık sınırı kaldırıyor; bu zayıf. Ücretsiz özellikler PRO'ya taşınmaz. Eklenecek PRO özellikleri:
   - **Catch-up:** kaçırılan yayını izleme. Xtream `tv_archive` alanı kullanılır; yalnızca arşivi olan kanallarda görünür.
   - **Kesintisiz izleme:** Yayın donunca aynı kanalın yedek sürümüne (HD/FHD/yedek) otomatik geçilir.
   - **TV'de canlı kanal önizleme:** Listede gezerken seçili kanal küçük pencerede oynar.
   - **Kendi kanal grupları:** grup oluşturma, kanalları sıralama, kategorileri gizleme.
   - Paywall metni bu özelliklere göre yeniden yazılır.

Sonraya kalanlar:
- Canlı yayını durdurma ve geri sarma.
- Yapay zekâ "Şu an ne izlesem?".
- Program hatırlatma.
- İzlerken açılan mini kanal listesi.
- Profiller ve cihazlar arası eşitleme.

**Spor modülü:** Yazıldı ve test edildi ama bu depoda değil. Yayından sonra ek özellik olarak eklenecek. Tüm kaynak kod ve ayrıntılı not (`kaynak/HAFIZA.md`) şu sayfada:
https://claude.ai/artifact/3v9FABLB6e6763tUZwYTPd

Bilerek yapılmayacaklar:
- Uygulamaya hazır kanal listesi ya da içerik koymak. Play'den kaldırılma riski var.
- Yayın kaydetme.

**Hatırlatma kuralı:** Kullanıcı "ek özellik", "farklı özellik", "yeni özellik", "ne ekleyelim" gibi şeyler söylediğinde bu yol haritasını hatırlat ve önce bunları öner.
