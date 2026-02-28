#!/usr/bin/env bash
set -euo pipefail

# 说明：本脚本通过 gateway 的 ops 入口触发重建索引（gateway -> Dubbo -> search-service）。
# 注意：该接口成本较高，且需要管理员权限（JWT + ADMIN role）。
GATEWAY_URL="${GATEWAY_URL:-http://localhost:12882}"
OPS_ACCESS_TOKEN="${OPS_ACCESS_TOKEN:-${ACCESS_TOKEN:-}}"

if [[ -z "${OPS_ACCESS_TOKEN}" ]]; then
  echo "[reindex] missing OPS_ACCESS_TOKEN (admin JWT)" >&2
  echo "[reindex] hint: login via gateway -> bootstrap admin -> export OPS_ACCESS_TOKEN" >&2
  exit 1
fi

echo "[reindex] POST ${GATEWAY_URL}/api/ops/search/reindex"
curl -fsS -X POST "${GATEWAY_URL}/api/ops/search/reindex" \
  -H "Authorization: Bearer ${OPS_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{}"

echo ""
echo "[reindex] OK"
