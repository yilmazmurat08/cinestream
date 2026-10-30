#!/usr/bin/env bash
# Tanıtım afişini (1080x1920) uygulamanın güncel ekranlarıyla baştan üretir.
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
rm -rf "$EKRAN"
echo "Hazır: CineStream-afis-1080x1920.png"
