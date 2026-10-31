#!/usr/bin/env bash
# Tanıtım özeti (yaklaşık 40 sn), dikey ve yatay: logo açılışı + ekran kaydından bulanık sahneler + QR ile giriş
# sahnesi (uygulamanın kendi koduyla çizilir) + üretilmiş müzik.
# Kullanım: ./uret.sh <ekran-kaydı.mp4>
# Ekran kaydı depoya girmez (içinde telifli yayınlar var); kesme yerleri sahneler.sh içinde, o kayda göre.
set -euo pipefail
[ $# -eq 1 ] || { echo "Kullanım: $0 <ekran-kaydı.mp4>"; exit 1; }
KAYIT=$(realpath "$1")
cd "$(dirname "$0")"
KOK=$(git rev-parse --show-toplevel)
LV=../logo-video
TEST="$KOK/app/src/test/java/com/example/TvQrPromoShotTmp.kt"

# 1) Logo açılışları (10 sn): dikeyde "ŞİMDİ YAYINDA" kutulu, yatayda kutusuz
[ -f "$LV/CineStream-durum-yayinda.mp4" ] || (cd "$LV" && ./uret.sh)
if [ ! -f logo-yatay-10.mp4 ]; then
  DUR=10 node "$LV/render.js" 1920 1080 kareler_logo 30
  ffmpeg -v error -y -framerate 30 -i kareler_logo/f%04d.png -c:v libx264 -preset slow -crf 17 -pix_fmt yuv420p logo-yatay-10.mp4
  rm -rf kareler_logo
fi

# 2) QR sahnesi: TV penceresi ve telefon sayfası (geçici test, iş bitince silinir)
trap 'rm -f "$TEST"' EXIT
cp qr-cekim.kt.txt "$TEST"
(cd "$KOK" && CINESTREAM_QR_OUT="$PWD/play-store/tanitim/ozet-video" ./gradlew -q :app:testDebugUnitTest \
  --tests 'com.example.TvQrPromoShotTmp' -Proborazzi.test.record=true --rerun)
rm -f "$TEST"
node telefon.js telefon-sayfa.html telefon-sayfa.png

# 3) Ekran kaydından sahneler, tur kareleri
./sahneler.sh "$KAYIT"
cp "$KOK/app/src/main/res/drawable-nodpi/img_app_logo_1782414357652.jpg" logo.jpg
rm -rf kareler_dikey kareler_yatay
node ozet-render.js 1080 1920 kareler_dikey
node ozet-render.js 1920 1080 kareler_yatay

# 4) Müzik ve logo efektleri (alt tonsuz; müzikle çakışmasın)
python3 ../durum-tanitim/muzik.py muzik.wav ozet
PAD=0 python3 "$LV/synth.py" efekt_dikey.wav 10 cta
PAD=0 python3 "$LV/synth.py" efekt_yatay.wav 10

# 5) Birleştirme: logo (10 sn) -> 9,5'te 0,5 sn geçiş -> tur (30,1 sn) = 39,6 sn
birlestir() { # logo kareler efekt çıkış
  ffmpeg -v error -y -i "$1" -framerate 30 -i "$2/f%04d.png" -i muzik.wav -i "$3" -filter_complex "
[0:v]fps=30,format=yuv420p,settb=AVTB[v0];[1:v]fps=30,format=yuv420p,settb=AVTB[v1];
[v0][v1]xfade=transition=fade:duration=0.5:offset=9.5,trim=0:39.6,setpts=PTS-STARTPTS[v];
[2:a]volume=0.70[m];[3:a]volume=0.85,apad[e];[m][e]amix=inputs=2:normalize=0:duration=first[mx];
[mx]volume=1.22,alimiter=limit=0.84:attack=5:release=60:level=disabled,afade=t=out:st=39.0:d=0.6[a]" \
    -map "[v]" -map "[a]" -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p -profile:v high -c:a aac -b:a 192k \
    -movflags +faststart -t 39.6 "$4"
}
birlestir "$LV/CineStream-durum-yayinda.mp4" kareler_dikey efekt_dikey.wav CineStream-tanitim-dikey.mp4
birlestir logo-yatay-10.mp4 kareler_yatay efekt_yatay.wav CineStream-tanitim-yatay.mp4
rm -rf s1 s2 s3 s5 s6 s7 kareler_dikey kareler_yatay muzik.wav efekt_dikey.wav efekt_yatay.wav
echo "Hazır: CineStream-tanitim-dikey.mp4, CineStream-tanitim-yatay.mp4"
