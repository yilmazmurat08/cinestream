# Mağaza görselleri üretimi

`./uret.sh` hepsini baştan üretir (~25 dk; Robolectric ilk turda dosya indirir).

- `afis-uretici.html` + `afisleri-ciz.js`: kodla çizilen **hayali** film/dizi afişleri ve kanal logoları
  (`app/src/test/resources/promo/`). Gerçek film, kanal, marka, logo ya da kişi yoktur.
- Ekranlar uygulamanın gerçek kodundan çekilir (`PhonePromoShotsTest`, `TvShowcaseTest`; `PromoArt.kt` hayali görselleri verir).
  Çekim sırasında **internet kapalıdır**: aksi halde uygulama TMDB'den gerçek film görselleri (gerçek kişiler) çekiyor.
- `cerceve.html`: telefon görsellerinin başlık + telefon/TV çerçevesi; açılış ve kapanışta hayali afiş duvarı.
- Canlı Yayın ekranındaki yetişkin klasörü kesilir; TV görselleri değiştirilmeden kullanılır (Play TV-G4).

Kurallar: gerçek kanal adı (beIN, D-Smart vb.), platform adı (Netflix, Amazon vb.), telifli afiş, kulüp/marka logosu,
ünlü yüz ve "binlerce kanal" gibi hazır içerik vaadi kullanılmaz. "Kendi M3U / Xtream listenle" notu kalır.
