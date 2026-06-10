#!/usr/bin/env bash
# Build a release: backend portal.jar + frontend dist + this deploy bundle.
# Located at cim-portal-server/deploy/; cim-portal-client must be a sibling repo.
# Output: <workspace>/dist/release/ and <workspace>/dist/cim-portal-release.tar.gz
set -euo pipefail
SERVER="$(cd "$(dirname "$0")/.." && pwd)"      # cim-portal-server
WS="$(cd "$SERVER/.." && pwd)"                   # workspace containing both repos
CLIENT="$WS/cim-portal-client"
REL="$WS/dist/release"

[[ -d "$CLIENT" ]] || { echo "frontend repo not found at $CLIENT" >&2; exit 1; }
rm -rf "$REL"; mkdir -p "$REL/web"

echo "== build backend (cim-portal-server) =="
( cd "$SERVER" && mvn -q -DskipTests clean package )
cp "$SERVER/target/portal.jar" "$REL/portal.jar"

echo "== build frontend (cim-portal-client) =="
# VITE_API_BASE_URL left empty → SPA calls the API same-origin (Nginx proxies /api).
( cd "$CLIENT" && npm ci && npm run build )
cp -r "$CLIENT/dist/." "$REL/web/"

cp -r "$SERVER/deploy" "$REL/deploy"
( cd "$WS/dist" && tar czf cim-portal-release.tar.gz release )
echo
echo "Release ready: $WS/dist/cim-portal-release.tar.gz"
echo "  release/portal.jar  →  /opt/cim-portal/portal.jar"
echo "  release/web/        →  /var/www/cim-portal/"
echo "  release/deploy/     →  systemd unit, nginx conf, key-gen, examples"
