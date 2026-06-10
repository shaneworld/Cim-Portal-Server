#!/usr/bin/env bash
# Generate the stable portal-internal RSA signing key pair (PKCS#8 private + X.509 public).
# Usage: ./gen-portal-jwt-key.sh [output-dir]   (default /etc/cim-portal)
set -euo pipefail
OUT="${1:-/etc/cim-portal}"
install -d -m 700 "$OUT"
if [[ -f "$OUT/portal-jwt-private.pem" ]]; then
  echo "Refusing to overwrite existing $OUT/portal-jwt-private.pem" >&2; exit 1
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$OUT/portal-jwt-private.pem"
openssl rsa -in "$OUT/portal-jwt-private.pem" -pubout -out "$OUT/portal-jwt-public.pem"
chmod 600 "$OUT/portal-jwt-private.pem"
chmod 644 "$OUT/portal-jwt-public.pem"
echo "Wrote:"
echo "  $OUT/portal-jwt-private.pem  (keep secret, chmod 600)"
echo "  $OUT/portal-jwt-public.pem"
echo "Point application-prod.yml at these via app.security.portal-jwt.{private,public}-key-location."
