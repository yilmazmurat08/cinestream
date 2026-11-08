# Çok platform planı (Samsung, LG, Windows)

Kararlar 8 Kasım 2026'da kullanıcıyla konuşularak alındı.

## Sıra

1. 1.1.8 Play'de yayına girer.
2. Android'deki planlı sürüm biter: alt bar, Oyunlar (PRO), Firebase, PRO'da 7 günlük deneme, kesintisiz izleme.
3. Samsung (Tizen), LG (webOS) ve Windows (Microsoft Store, PWA) sürümleri yazılır.
4. Bundan sonraki güncellemeler bütün platformlara birlikte çıkar. Mağaza inceleme süreleri farklı olduğu için hedef "aynı gün" değil, "aynı özellikler, yakın tarih".

## Teknik

- Tizen ve webOS Android uygulaması çalıştırmaz. Tek bir web uygulaması (HTML/JS) yazılır, üç platforma uyarlanır.
- Oynatıcı: Samsung'da AVPlay, LG'de webOS medya oynatıcısı, Windows'ta tarayıcı oynatıcısı (HLS için hls.js).
- Android kodundan mantık taşınır (M3U/Xtream okuma, TMDB eşleştirmesi, EPG), kod yeniden yazılır. TMDB anahtar, adres ve eşleştirme kuralları aynen korunur.
- Test cihazı: kullanıcının Samsung TV'si (model sorulacak).

## PRO ve ödeme

- **Android'de hiçbir şey değişmez:** Play Billing kalır. PRO Google hesabına bağlıdır, aynı hesapla telefon ve Android TV'de birlikte açılır.
- **Samsung, LG, Windows: PRO cihaz başına.** Bir cihazda alınan PRO başka cihazda geçmez. Hesap sistemi ve platformlar arası eşitleme yok.
- **Ödeme mağazada değil, bizim sitemizde:**
  - Uygulamalar mağazaya ücretsiz girer.
  - TV ekranında kısa cihaz kodu ve QR görünür. Kullanıcı telefondan siteye girip öder.
  - Cihaz kimliği MAC adresi DEĞİL: Wi-Fi ve kablolu MAC farklıdır, bağlantı değişince PRO kaybolur. Samsung'da DUID (`webapis.productinfo.getDuid()`), LG'de cihaz kimliği, Windows'ta uygulamanın ürettiği kalıcı kimlik kullanılır.
  - Küçük bir sunucu hangi cihazın PRO olduğunu tutar (aday: Supabase; kullanıcının hesabı bağlı).
  - Ödeme sağlayıcı: **Paddle** gibi Merchant of Record (faturayı ve ülke KDV'sini sağlayıcı keser). Payı yaklaşık %5 + küçük sabit ücret; başlarken güncel fiyat doğrulanır.
  - Gizlilik metnine eklenir: lisans için cihaz kimliği saklanır.
- RevenueCat bu modelde gerekmez (sunum: https://claude.ai/artifact/GeubteGbQb3rXPbPbYHV1x, mağaza ödemesi senaryosu için hazırlanmıştı).

## Başlarken ilk kontroller (kod yazmadan önce)

1. Samsung Seller Office'te bireysel hesapla yayın yapılabiliyor mu?
2. Samsung ve LG mağaza kuralları uygulama dışı (web) ödemeye ve cihaz kodu ile etkinleştirmeye izin veriyor mu? Resmi kural metni okunur.
3. Microsoft Store kuralları: uygulama dışı ödeme (oyun dışı uygulamalarda serbest olduğu biliniyor; doğrulanır).
4. Paddle bireysel satıcıyı (şirketsiz) kabul ediyor mu? Etmiyorsa şahıs şirketi gerekir; kullanıcıya sorulur.
5. Samsung Checkout Türkiye'de var (resmi tabloda TR/TRY, 96 ülke); mağaza ödemesine dönülürse bilgi olarak.

## Güvenlik

- Paddle, Supabase ve mağaza hesaplarının anahtarları sohbete ve depoya girmez; ortam değişkeni ya da ilgili panelde tutulur.
- Sunucu, PRO kaydını yalnızca ödeme sağlayıcının imzalı bildirimiyle (webhook) açar; uygulama kendi kendine PRO açamaz.
