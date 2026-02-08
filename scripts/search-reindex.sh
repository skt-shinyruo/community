#!/usr/bin/env bash
set -euo pipefail

# 说明：本脚本直接调用 search-service 的 internal 入口触发重建索引。
# 风险：该接口成本较高，请确保目标地址仅在内网可达，避免误暴露。
SEARCH_SERVICE_URL="${SEARCH_SERVICE_URL:-http://localhost:8083}"

echo "[reindex] POST ${SEARCH_SERVICE_URL}/internal/search/reindex"
curl -fsS -X POST "${SEARCH_SERVICE_URL}/internal/search/reindex" \
  -H "Content-Type: application/json" \
  -d "{}"

echo ""
echo "[reindex] OK"
