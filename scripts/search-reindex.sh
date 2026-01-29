#!/usr/bin/env bash
set -euo pipefail

SEARCH_BASE_URL="${SEARCH_BASE_URL:-http://localhost:8083}"
SEARCH_INTERNAL_TOKEN="${SEARCH_INTERNAL_TOKEN:-}"

if [[ -z "${SEARCH_INTERNAL_TOKEN}" ]]; then
  echo "[reindex] missing token: set SEARCH_INTERNAL_TOKEN" >&2
  exit 1
fi

echo "[reindex] POST ${SEARCH_BASE_URL}/internal/search/reindex"
curl -fsS -X POST "${SEARCH_BASE_URL}/internal/search/reindex" \
  -H "X-Internal-Token: ${SEARCH_INTERNAL_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{}"

echo ""
echo "[reindex] OK"
