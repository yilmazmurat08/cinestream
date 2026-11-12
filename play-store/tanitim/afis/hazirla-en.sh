#!/usr/bin/env bash
# İngilizce tanıtım afişi (Facebook vb.): uygulamanın gerçek ekranları İngilizce arayüz ve İngilizce adlı hayali
# içerikle çekilir, sonra iki boyut üretilir: 1080x1920 (dikey) ve 1080x1350 (Facebook akışı, 4:5).
# Hayali afişler İngilizce adlarla: AFIS_LANG=en node ../../gorseller/uret/afisleri-ciz.js <kök>/app/src/test/resources/promo/en
set -euo pipefail
cd "$(dirname "$0")"
KOK=$(git rev-parse --show-toplevel)
EKRAN=$(mktemp -d)
(cd "$KOK" && CINESTREAM_SHOTS="$EKRAN" CINESTREAM_SHOTS_LANG=en ./gradlew -q :app:testDebugUnitTest \
  --tests 'com.example.PhonePromoShotsTest.p14_movieFolders' --tests 'com.example.PhonePromoShotsTest.p16_seriesFolders' \
  --tests 'com.example.PhonePromoShotsTest.p17_liveFolders' --rerun -Proborazzi.test.record=true)
cp "$EKRAN/p14-film-klasorleri.png" en-filmler.png
cp "$EKRAN/p16-dizi-klasorleri.png" en-diziler.png
# Yetişkin klasörü gösterilmez (Play kuralları): başlık + NEWS'ten sonrası (Türkçe afişle aynı kesme yerleri).
ffmpeg -v error -y -i "$EKRAN/p17-canli-klasorler.png" -filter_complex \
  "[0]crop=1080:372:0:0[a];[0]crop=1080:1544:0:796[b];[a][b]vstack,pad=1080:2340:0:0:color=0x0B0814" en-canli.png
cp "$KOK/app/src/main/res/drawable-nodpi/img_app_logo_1782414357652.jpg" logo.jpg
node cek.js afis-en.html CineStream-poster-en-1080x1920.png
node cek.js "afis-en.html?fmt=fb" CineStream-poster-en-1080x1350.png 1080 1350
rm -f en-filmler.png en-diziler.png en-canli.png
rm -rf "$EKRAN"
echo "Hazır: İngilizce afiş (1080x1920 ve 1080x1350)."
