#!/usr/bin/env bash
# Tanıtım afişlerini uygulamanın güncel ekranlarıyla baştan üretir: genel afiş, durum afişleri (dikey) ve forum afişleri (yatay).
# Gerekenler: JDK (Gradle), node + playwright (Chromium), ffmpeg.
set -euo pipefail
cd "$(dirname "$0")"
KOK=$(git rev-parse --show-toplevel)
EKRAN=$(mktemp -d)
# Gerçek ekran kodu hayali içerikle çizilir (PhonePromoShotsTest).
(cd "$KOK" && CINESTREAM_SHOTS="$EKRAN" ./gradlew -q :app:testDebugUnitTest \
  --tests 'com.example.PhonePromoShotsTest' --rerun -Proborazzi.test.record=true)
cp "$EKRAN/p14-film-klasorleri.png" filmler.png
cp "$EKRAN/p16-dizi-klasorleri.png" diziler.png
# Canlı Yayın ekranındaki yetişkin klasörü afişte gösterilmez (Play kuralları): başlık + HABER'den sonrası.
# Ekran düzeni değişirse kesme yerleri (372 ve 796. satırlar) yeniden ayarlanmalı.
ffmpeg -v error -y -i "$EKRAN/p17-canli-klasorler.png" -filter_complex \
  "[0]crop=1080:372:0:0[a];[0]crop=1080:1544:0:796[b];[a][b]vstack,pad=1080:2340:0:0:color=0x0B0814" canli.png
cp "$KOK/app/src/main/res/drawable-nodpi/img_app_logo_1782414357652.jpg" logo.jpg
node cek.js afis.html CineStream-afis-1080x1920.png
# Durumlar (WhatsApp/Instagram, dikey): üst ve alt kenar boş, çağrı kutulu. Forum: yatay, JPG.
node cek.js afis-durum.html CineStream-durum-afis-yakinda.png
node cek.js "afis-durum.html?cta=yayinda" CineStream-durum-afis-yayinda.png
node cek.js afis-forum.html forum.png 1920 1080
ffmpeg -v error -y -i forum.png -q:v 2 CineStream-forum-yakinda.jpg
node cek.js "afis-forum.html?cta=yayinda" forum.png 1920 1080
ffmpeg -v error -y -i forum.png -q:v 2 CineStream-forum-yayinda.jpg
rm -f forum.png
rm -rf "$EKRAN"
echo "Hazır: afiş, durum afişleri ve forum afişleri."
