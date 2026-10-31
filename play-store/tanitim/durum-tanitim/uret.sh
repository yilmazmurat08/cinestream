#!/usr/bin/env bash
# Durum tanıtım videosu (dikey, 17,6 sn): logo açılışı (CineStream-durum-yayinda) + ekran kaydından bulanık uygulama
# turu + üretilmiş müzik. Kullanım: ./uret.sh <ekran-kaydı.mp4>
# Ekran kaydı depoya girmez (içinde telifli yayınlar var). Kesme yerleri ve bulanık bölgeler klip.sh içinde, o kayda göre.
set -euo pipefail
cd "$(dirname "$0")"
KOK=$(git rev-parse --show-toplevel)
LOGO=../logo-video/CineStream-durum-yayinda.mp4
[ -f "$LOGO" ] || (cd ../logo-video && ./uret.sh)
./klip.sh "$1"
cp "$KOK/app/src/main/res/drawable-nodpi/img_app_logo_1782414357652.jpg" logo.jpg
rm -rf tur_kareler && node tur-render.js tur_kareler
python3 muzik.py muzik.wav
PAD=0 python3 ../logo-video/synth.py efekt.wav 10 cta
ffmpeg -v error -y -i "$LOGO" -framerate 30 -i tur_kareler/f%04d.png -i muzik.wav -i efekt.wav -filter_complex "
[0:v]fps=30,format=yuv420p,settb=AVTB[v0];[1:v]fps=30,format=yuv420p,settb=AVTB[v1];
[v0][v1]xfade=transition=fade:duration=0.5:offset=9.5,trim=0:17.6,setpts=PTS-STARTPTS[v];
[2:a]volume=0.70[m];[3:a]volume=0.85,apad[e];[m][e]amix=inputs=2:normalize=0:duration=first[mx];
[mx]volume=1.22,alimiter=limit=0.84:attack=5:release=60:level=disabled,afade=t=out:st=17.0:d=0.6[a]" \
  -map "[v]" -map "[a]" -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p -profile:v high -c:a aac -b:a 192k \
  -movflags +faststart -t 17.6 CineStream-durum-tanitim.mp4
rm -rf tur_klip tur_kareler muzik.wav efekt.wav
echo "Hazır: CineStream-durum-tanitim.mp4"
