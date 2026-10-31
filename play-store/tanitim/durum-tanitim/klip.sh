#!/usr/bin/env bash
# Ekran kaydından üç kısa sahne: kanal logoları, afişler, dizi adı/özeti ve bölüm görselleri bulanık; ses yok.
# Kırpma: üstteki durum satırı (16 px) ve alttaki görev çubuğu atılır -> 900x480.
set -euo pipefail
cd "$(dirname "$0")"
V="$1"
B="gblur=sigma=16:steps=3"
rm -rf tur_klip && mkdir tur_klip
ffmpeg -v error -y \
  -ss 0.20 -t 3.40 -i "$V" \
  -ss 4.40 -t 2.50 -i "$V" \
  -ss 140.20 -t 2.50 -i "$V" \
  -filter_complex "
[0:v]fps=30,crop=900:480:0:16,setpts=PTS-STARTPTS,format=yuv420p,split=4[a][a1][a2][a3];
[a1]crop=200:110:645:80,$B[b1];[a2]crop=235:46:20:161,$B[b2];[a3]crop=186:58:32:406,$B[b3];
[a][b1]overlay=645:80[a4];[a4][b2]overlay=20:161[a5];[a5][b3]overlay=32:406,settb=AVTB[s1];
[1:v]fps=30,crop=900:480:0:16,setpts=PTS-STARTPTS,format=yuv420p,split=3[c][c1][c2];
[c1]crop=690:464:200:16,$B[d1];[c2]crop=44:368:148:120,$B[d2];
[c][d1]overlay=200:16[c3];[c3][d2]overlay=148:120,settb=AVTB[s2];
[2:v]fps=30,crop=900:480:0:16,setpts=PTS-STARTPTS,format=yuv420p,split=4[e][e1][e2][e3];
[e1]crop=560:118:18:18,$B[f1];[e2]crop=230:310:655:14,$B[f2];[e3]crop=880:118:12:362,$B[f3];
[e][f1]overlay=18:18[e4];[e4][f2]overlay=655:14[e5];[e5][f3]overlay=12:362,settb=AVTB[s3];
[s1][s2]xfade=transition=fade:duration=0.35:offset=3.05[x12];
[x12][s3]xfade=transition=fade:duration=0.35:offset=5.20,tpad=stop_mode=clone:stop_duration=0.4[out]" \
  -map "[out]" -an tur_klip/k%04d.png
ls tur_klip | wc -l
