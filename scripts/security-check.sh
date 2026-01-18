#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

echo "[security-check] 1) secret scan"
bash scripts/secret-scan.sh

echo "[security-check] 2) docker compose config (best-effort)"
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null

echo "[security-check] 3) backend tests"
mvn -q test

if [[ "${SKIP_FRONTEND:-false}" != "true" ]]; then
  echo "[security-check] 4) frontend unit test + build"
  npm -C frontend ci
  npm -C frontend test
  npm -C frontend run build
fi

echo "[security-check] OK"

