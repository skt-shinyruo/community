#!/usr/bin/env bash
set -euo pipefail

# 推荐：通过 gateway 触发（/api/ops/**），由 gateway 代持下游 internal-token；
# reindex 属于高成本运维操作，必须通过 break-glass（ops guard）：
# - search-service: OPS_SEARCH_REINDEX_ENABLED=true + allowlist + ops.search.token
# - 调用方：提供 X-Ops-Token（OPS_SEARCH_TOKEN）
GATEWAY_URL="${GATEWAY_URL:-http://localhost:12882}"
OPS_TOKEN="${OPS_TOKEN:-${OPS_SEARCH_TOKEN:-}}"

if [[ -z "${OPS_TOKEN}" ]]; then
  echo "[reindex] missing ops token: set OPS_SEARCH_TOKEN (or OPS_TOKEN)" >&2
  exit 1
fi

echo "[reindex] POST ${GATEWAY_URL}/api/ops/search/reindex"
echo "[reindex] note: ensure search-service ops guard enabled + allowlist configured (OPS_SEARCH_REINDEX_ENABLED/OPS_SEARCH_REINDEX_ALLOWLIST)"
curl -fsS -X POST "${GATEWAY_URL}/api/ops/search/reindex" \
  -H "X-Ops-Token: ${OPS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{}"

echo ""
echo "[reindex] OK"
