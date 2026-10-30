#!/usr/bin/env bash
# CineStream logo açılış videosunu baştan üretir: yatay (1920x1080) ve dikey (1080x1920), 8 sn, 30 kare/sn, sesli.
# Gerekenler: node + playwright (Chromium), python3, ffmpeg. Süre: yaklaşık 4 dakika.
set -euo pipefail
cd "$(dirname "$0")"
python3 synth.py ses.wav
for boy in "yatay 1920 1080" "dikey 1080 1920"; do
  set -- $boy
  rm -rf "kareler_$1"
  node render.js "$2" "$3" "kareler_$1" 30
  ffmpeg -v error -y -framerate 30 -i "kareler_$1/f%04d.png" -i ses.wav -c:v libx264 -preset slow -crf 17 \
    -pix_fmt yuv420p -profile:v high -c:a aac -b:a 192k -movflags +faststart -shortest "CineStream-logo-$1.mp4"
  rm -rf "kareler_$1"
done
rm -f ses.wav
echo "Hazır: CineStream-logo-yatay.mp4, CineStream-logo-dikey.mp4"
