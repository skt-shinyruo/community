#!/usr/bin/env bash
set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX="${INDEX:-community_posts}"

echo "[es-reset] deleting index: ${INDEX}"
curl -fsS -X DELETE "${ES_URL}/${INDEX}" >/dev/null 2>&1 || true

echo "[es-reset] creating index: ${INDEX}"
curl -fsS -X PUT "${ES_URL}/${INDEX}" \
  -H "Content-Type: application/json" \
  -d '{"settings":{"number_of_shards":1,"number_of_replicas":0}}' >/dev/null

echo "[es-reset] OK"

