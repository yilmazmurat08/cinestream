#!/usr/bin/env bash
# Ekran kaydından özet sahneleri: her sahne ayrı klasörde 30 kare/sn PNG (900x480; üst durum satırı ve alt görev
# çubuğu atılır). Kanal logoları, afişler, film/dizi adları ve özetleri, oyuncu fotoğrafları, fragman ve yayın
# görüntüleri bulanık; ses yok. Kesme yerleri ve bölgeler bu kayda göredir.
# Kullanım: ./sahneler.sh <ekran-kaydı.mp4>
set -euo pipefail
V=$(realpath "$1")
cd "$(dirname "$0")"
B="gblur=sigma=16:steps=3"

sahne() { # ad başlangıç süre kare "x,y,w,h;x,y,w,h;..."
  local ad=$1 bas=$2 sure=$3 kare=$4
  local IFS=';'
  local -a rr=($5)
  local n=${#rr[@]}
  local f="fps=30,crop=900:480:0:16,format=yuv420p,split=$((n + 1))[b0]"
  local i x y w h
  for ((i = 1; i <= n; i++)); do f+="[r$i]"; done
  f+=";"
  for ((i = 1; i <= n; i++)); do
    IFS=',' read -r x y w h <<< "${rr[$((i - 1))]}"
    f+="[r$i]crop=$w:$h:$x:$y,$B[q$i];[b$((i - 1))][q$i]overlay=$x:$y[b$i];"
  done
  f+="[b$n]tpad=stop_mode=clone:stop_duration=4[out]"
  rm -rf "$ad" && mkdir "$ad"
  ffmpeg -v error -y -ss "$bas" -t "$sure" -i "$V" -filter_complex "$f" -map "[out]" -frames:v "$kare" "$ad/k%04d.png"
  echo "$ad: $(ls "$ad" | wc -l) kare"
}

sahne s1 0.20 3.40 160 "645,80,200,110;20,161,235,46;32,406,186,58"
sahne s2 4.40 3.50 132 "200,16,690,464;148,120,44,368"
sahne s3 8.05 4.40 132 "16,14,506,140;440,0,460,394;15,239,885,125;15,404,885,76"
sahne s5 140.20 2.50 132 "18,18,560,118;655,14,230,310;12,362,880,118"
sahne s6 112.80 4.40 132 "0,0,900,394;30,400,85,34"
sahne s7 164.40 3.18 96 "252,0,648,394;12,14,44,466;288,400,74,34"
