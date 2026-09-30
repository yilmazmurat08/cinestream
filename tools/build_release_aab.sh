#!/usr/bin/env bash
# Google Play'e yüklenecek imzalı AAB'yi üretir.
#
# İmza anahtarı depoda DEĞİLDİR; çalışma ortamının gizli değişkenlerinden okunur:
#   CINESTREAM_KEYSTORE_BASE64    .jks dosyasının base64 hâli
#   CINESTREAM_KEYSTORE_PASSWORD  anahtar şifresi (anahtar deposu ve anahtar için aynı)
# Anahtar geçici bir dosyaya açılır, derlemeden sonra silinir. Yanlış anahtarla imzalamayı önlemek için
# sertifika parmak izi Play'e ilk yüklenen yükleme anahtarıyla karşılaştırılır.
#
# Kullanım: tools/build_release_aab.sh
# Çıktı:    app/build/outputs/bundle/release/app-release.aab
set -euo pipefail

EXPECTED_SHA256="B2:3C:DE:7D:86:34:FD:77:A3:FB:20:78:03:1D:A9:D9:BC:DC:CD:40:C9:47:0C:B9:52:BD:43:3B:A8:79:7B:B3"

if [[ -z "${CINESTREAM_KEYSTORE_BASE64:-}" || -z "${CINESTREAM_KEYSTORE_PASSWORD:-}" ]]; then
  echo "HATA: CINESTREAM_KEYSTORE_BASE64 ve CINESTREAM_KEYSTORE_PASSWORD ortam değişkenleri tanımlı değil." >&2
  exit 1
fi

cd "$(dirname "$0")/.."
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
KS="$WORK/upload.jks"
printf '%s' "$CINESTREAM_KEYSTORE_BASE64" | tr -d ' \n\r' | base64 -d > "$KS"

ACTUAL_SHA256=$(keytool -list -v -keystore "$KS" -storepass "$CINESTREAM_KEYSTORE_PASSWORD" -alias upload 2>/dev/null \
  | awk '/SHA256:/ {print $2; exit}' || true)
if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
  echo "HATA: Şifre yanlış veya anahtar, Play'e kayıtlı yükleme anahtarı değil (parmak izi eşleşmiyor)." >&2
  exit 1
fi

KEYSTORE_PATH="$KS" STORE_PASSWORD="$CINESTREAM_KEYSTORE_PASSWORD" KEY_PASSWORD="$CINESTREAM_KEYSTORE_PASSWORD" KEY_ALIAS=upload \
  ./gradlew --no-daemon bundleRelease

AAB=app/build/outputs/bundle/release/app-release.aab
jarsigner -verify "$AAB" >/dev/null
echo "Hazır: $AAB"
