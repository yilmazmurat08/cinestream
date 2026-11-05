#!/usr/bin/env bash
# Play mağazası görsellerini baştan üretir: hayali afişler -> uygulamanın gerçek ekranları -> mağaza çerçeveleri.
# Gerekenler: JDK + Android SDK (Gradle), node + playwright (Chromium), ffmpeg, python3 + Pillow.
# Çıktı: ../telefon (1080x1920, 8 adet) ve ../tv (1920x1080, uygulamanın değiştirilmemiş TV ekranları).
set -euo pipefail
cd "$(dirname "$0")"
KOK=$(git rev-parse --show-toplevel)
EKRAN=$(mktemp -d)
trap 'rm -rf "$EKRAN" ekranlar' EXIT

# 1) Hayali afiş ve kanal logoları (test kaynaklarına).
node afisleri-ciz.js
mkdir -p afisler && cp "$KOK"/app/src/test/resources/promo/afis/*.jpg afisler/
cp "$KOK/app/src/main/res/drawable-nodpi/img_app_logo_1782414357652.jpg" logo.jpg

# 2) Uygulamanın gerçek ekranları hayali içerikle. İnternet KAPALI: uygulama TMDB'den gerçek film görseli
#    (gerçek kişiler, telifli afişler) çekemesin; yalnızca hayali görseller görünür.
JTO="${JAVA_TOOL_OPTIONS:-}"
JTO=$(echo "$JTO" | sed -E 's/-Dhttps\.proxyPort=[0-9]+//')
(cd "$KOK" && ./gradlew --stop >/dev/null 2>&1 || true)
(cd "$KOK" && JAVA_TOOL_OPTIONS="$JTO -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9" \
  CINESTREAM_SHOTS="$EKRAN" ./gradlew --offline -q :app:testDebugUnitTest \
  --tests 'com.example.PhonePromoShotsTest' --tests 'com.example.TvShowcaseTest' --rerun -Proborazzi.test.record=true)
(cd "$KOK" && ./gradlew --stop >/dev/null 2>&1 || true)

mkdir -p ekranlar
cp "$EKRAN"/p01-ana-sayfa.png "$EKRAN"/p02-film-detayi.png "$EKRAN"/p04-asistan.png "$EKRAN"/p14-film-klasorleri.png ekranlar/
cp "$EKRAN"/03-ana-sayfa-filmler.png ekranlar/tv.png
# Canlı Yayın ekranındaki yetişkin klasörü mağazada gösterilmez: başlık + HABER'den sonrası.
# Ekran düzeni değişirse kesme yerleri (372 ve 796. satırlar) yeniden ayarlanmalı.
ffmpeg -v error -y -i "$EKRAN/p17-canli-klasorler.png" -filter_complex \
  "[0]crop=1080:372:0:0[a];[0]crop=1080:1544:0:796[b];[a][b]vstack,pad=1080:2340:0:0:color=0x0B0814" ekranlar/canli.png

# 3) Telefon görselleri (Play: 2-8 adet, 9:16).
T=../telefon
rm -f "$T"/*.png
cek() { node ../../tanitim/afis/cek.js "$1" "$T/$2"; }
cek "cerceve.html?tip=acilis&alt=Film geceniz artık bambaşka" 01-acilis.png
cek "cerceve.html?tip=telefon&ekran=ekranlar/p01-ana-sayfa.png&b1=Canlı TV, film ve dizi&b2=tek uygulamada&alt=Kendi M3U / Xtream listenle" 02-ana-sayfa.png
cek "cerceve.html?tip=telefon&ekran=ekranlar/p02-film-detayi.png&b1=Afiş, özet, puan&b2=hepsi bir arada&alt=Film ve dizi bilgileri kendiliğinden gelir" 03-film-detayi.png
cek "cerceve.html?tip=telefon&ekran=ekranlar/p14-film-klasorleri.png&b1=Klasörlerin&b2=türüne göre&alt=Aradığını bir bakışta bul" 04-film-klasorleri.png
cek "cerceve.html?tip=telefon&ekran=ekranlar/canli.png&b1=Canlı kanallar&b2=düzenli ve hızlı&alt=Kategoriler, yayın akışı, hatırlatma" 05-canli-tv.png
cek "cerceve.html?tip=telefon&ekran=ekranlar/p04-asistan.png&b1=Sahneyi anlat,&b2=yapay zekâ bulsun&alt=Adını unuttuğun film, kütüphanende" 06-yapay-zeka-asistani.png
cek "cerceve.html?tip=tv&ekran=ekranlar/tv.png&b1=Televizyonda da&b2=aynı keyif&alt=Kumandayla kolay, sesle arama" 07-tv-modu.png
cek "cerceve.html?tip=kapanis&alt=Telefonda ve TV'de" 08-kapanis.png

# 4) TV görselleri: uygulamanın değiştirilmemiş TV ekranları (Play TV-G4). Banner (tv-banner-1280x720.png) korunur.
V=../tv
find "$V" -name '*.png' ! -name 'tv-banner-1280x720.png' -delete
for n in 02-ana-sayfa-canli-tv 03-ana-sayfa-filmler 04-filmler 06-film-detayi 08-asistan 09-kaydedilenler; do
  cp "$EKRAN/$n.png" "$V/$n.png"
done
echo "Hazır: $T ve $V"
