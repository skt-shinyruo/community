#!/usr/bin/env bash
set -euo pipefail

# 写接口幂等调用示例：Create Post
#
# 目标：
# - 让脚本/第三方客户端更容易正确使用 Idempotency-Key
# - 重试时复用同一个 key，避免重复副作用
#
# 用法：
#   TOKEN="<access_token>" bash scripts/curl-idempotent-post.sh
#
# 可选：
#   BASE_URL="http://localhost:12882"
#   IDEMPOTENCY_KEY="your-key"
#   TITLE="hello"
#   CONTENT="world"

BASE_URL="${BASE_URL:-http://localhost:12882}"
TOKEN="${TOKEN:-}"
TITLE="${TITLE:-幂等发帖示例}"
CONTENT="${CONTENT:-这是一条通过脚本发送的帖子。可重复执行并复用同一个 Idempotency-Key 进行重试。}"
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-}"

if [[ -z "${TOKEN}" ]]; then
  echo "[curl-idempotent-post] 缺少 TOKEN（access token）"
  echo "示例：TOKEN=\"<access_token>\" bash scripts/curl-idempotent-post.sh"
  exit 2
fi

generate_key() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen
    return
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
    return
  fi
  echo "$(date +%s)-$RANDOM-$RANDOM"
}

if [[ -z "${IDEMPOTENCY_KEY}" ]]; then
  IDEMPOTENCY_KEY="$(generate_key)"
fi

echo "[curl-idempotent-post] POST ${BASE_URL}/api/posts"
echo "[curl-idempotent-post] Idempotency-Key: ${IDEMPOTENCY_KEY}"

curl -sS -X POST "${BASE_URL}/api/posts" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "{\"title\":\"${TITLE}\",\"content\":\"${CONTENT}\"}" \
  | cat

echo ""
echo "[curl-idempotent-post] 提示：重试同一个请求时，请复用相同的 Idempotency-Key。"

