#!/usr/bin/env bash
# CineStream logo açılış videolarını baştan üretir (30 kare/sn, sesli):
#   CineStream-logo-yatay.mp4 / -dikey.mp4   8 sn, yalın logo açılışı
#   CineStream-durum-yakinda.mp4             10 sn, dikey, "ÇOK YAKINDA / Google Play'de" kutusuyla
#   CineStream-durum-yayinda.mp4             10 sn, dikey, "ŞİMDİ YAYINDA / Google Play'de" (onaydan sonra)
#   CineStream-logo-640.gif / -480.gif       forumlar için (MP4 yüklenemeyen yerler)
# Gerekenler: node + playwright (Chromium), python3, ffmpeg. Süre: yaklaşık 10 dakika.
set -euo pipefail
cd "$(dirname "$0")"

video() { # ad genişlik yükseklik ses [ortam değişkenleri...]
  local ad=$1 w=$2 h=$3 ses=$4; shift 4
  rm -rf "kareler_$ad"
  env "$@" node render.js "$w" "$h" "kareler_$ad" 30
  ffmpeg -v error -y -framerate 30 -i "kareler_$ad/f%04d.png" -i "$ses" -c:v libx264 -preset slow -crf 17 \
    -pix_fmt yuv420p -profile:v high -c:a aac -b:a 192k -movflags +faststart -shortest "CineStream-$ad.mp4"
  rm -rf "kareler_$ad"
}

python3 synth.py ses8.wav 8
python3 synth.py ses10.wav 10 cta
video logo-yatay 1920 1080 ses8.wav DUR=8
video logo-dikey 1080 1920 ses8.wav DUR=8
video durum-yakinda 1080 1920 ses10.wav DUR=10 "CTA_TOP=ÇOK YAKINDA" "CTA_MAIN=Google Play'de"
video durum-yayinda 1080 1920 ses10.wav DUR=10 "CTA_TOP=ŞİMDİ YAYINDA" "CTA_MAIN=Google Play'de"
rm -f ses8.wav ses10.wav

for g in "640 12 192" "480 10 160"; do
  set -- $g
  ffmpeg -v error -y -i CineStream-logo-yatay.mp4 -vf "fps=$2,scale=$1:-1:flags=lanczos,palettegen=max_colors=$3:stats_mode=diff" palet.png
  ffmpeg -v error -y -i CineStream-logo-yatay.mp4 -i palet.png \
    -lavfi "fps=$2,scale=$1:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" "CineStream-logo-$1.gif"
done
rm -f palet.png
echo "Hazır."
