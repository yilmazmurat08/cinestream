#!/usr/bin/env bash
# TV afişini üretir: app/src/main/res/drawable-xhdpi/tv_banner.png (320x180, saydamlıksız).
# Gerekenler: node + playwright (Chromium), ffmpeg, Inter yazı tipi.
set -euo pipefail
cd "$(dirname "$0")"
OUT=../../app/src/main/res/drawable-xhdpi/tv_banner.png
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
node cek.js "$TMP/afis-4x.png"
mkdir -p "$(dirname "$OUT")"
ffmpeg -v error -y -i "$TMP/afis-4x.png" -vf "scale=320:180:flags=lanczos,format=rgb24" -frames:v 1 "$OUT"
echo "Hazır: $OUT"
