# Veri Güvenliği (Data Safety) Formu — Cevaplar

Sürüm 1.0.2 (3) için. Play Console > Politika ve programlar > Uygulama içeriği > Veri güvenliği.

## Genel sorular

| Soru | Cevap | Gerekçe |
|---|---|---|
| Uygulama, açıklanması zorunlu kullanıcı verisi topluyor veya paylaşıyor mu? | **Evet** | Yapay zekâ mesajları Google'a gider; AI bildirimleri geliştiriciye gelir. |
| Toplanan veriler aktarım sırasında şifreleniyor mu? | **Evet** | Gemini, TMDB, Google Form ve Play istekleri HTTPS. (IPTV sağlayıcısına giden istekler kullanıcının kendi hizmetidir; bizim toplamamız değildir.) |
| Hesap oluşturma | **Uygulamam hesap oluşturmaya izin vermiyor** | Profil adı/e-posta yalnızca cihazda. |
| Uygulama dışı hesaplarla giriş | **Hayır** | |
| Veri silme yolu | **Evet** — URL: gizlilik politikası linki | Ayarlar > Yasal > "Tüm verileri sil" ve bölüm 7. |

## Veri türleri

Yalnızca şunu işaretle: **Uygulama etkinliği > Kullanıcı tarafından oluşturulan diğer içerikler**

| Alan | Cevap |
|---|---|
| Toplandı | ✅ |
| Paylaşıldı | ❌ (Gemini'ye gönderim, kullanıcının kendi anahtarıyla ve kendi isteğiyle yapılır; Play tanımında "hizmet sağlayıcıya aktarım" paylaşım sayılmaz) |
| Kısa süreli işleniyor mu | **Hayır** |
| Zorunlu mu | **Kullanıcılar seçebilir** (yapay zekâ ve bildirim isteğe bağlı) |
| Amaç | **Uygulama işlevselliği** (bildirimler için ayrıca **Sahtekarlık önleme, güvenlik ve kanunlara uygunluk** eklenebilir) |

İşaretlenmeyenler ve nedeni:

- **Konum, kişisel bilgiler, finansal bilgiler, fotoğraf/video, ses, rehber, takvim, sağlık:** Toplanmıyor. Ödemeyi Google Play işler. Sesli arama cihazın kendi konuşma tanıma hizmetini kullanır; ses kaydı uygulamada tutulmaz.
- **Uygulama bilgileri ve performansı (çökme kayıtları):** Cihazda kalır, gönderilmez. Bildirimde yalnızca uygulama sürümü gider.
- **Cihaz veya diğer kimlikler:** Toplanmıyor (reklam kimliği yok).

## Karar vermen gereken sorular

1. **Bildirim gerekçesi için ek amaç:** "Sahtekarlık önleme, güvenlik ve kanunlara uygunluk" işaretlensin mi? Önerim: **Evet**. Bildirimler zararlı içerik denetimi için kullanılıyor.
2. **Google Formu kurduğunda:** Form yanıtları Google'da (senin hesabında) saklanır. Bu, "geliştiriciye aktarım" olarak zaten "Toplandı" kapsamında. Ek bir değişiklik gerekmez.
