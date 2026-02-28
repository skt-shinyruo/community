#!/usr/bin/env bash
set -euo pipefail

# Kafka DLQ 回放脚本（本地/演练用）
#
# 约定：
# - DLQ topic: <topic>.dlq
# - DLQ 消息默认是 KafkaDlqRecord 的 JSON（包含 originalTopic/key/payload 等字段）
# - 回放时将 dlq.payload 作为原始消息 value 回推到原 topic（或指定 target topic）
#
# ⚠️ 风险提示：
# - 回放会触发消费者再次执行副作用（通知/索引更新等），务必在演练环境或受控窗口执行
# - 请使用 MAX_MESSAGES / SLEEP_MS 进行限量与限速，避免“无限回放”或压垮下游

COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.yml}"

DLQ_TOPIC="${1:-${DLQ_TOPIC:-}}"
TARGET_TOPIC="${2:-${TARGET_TOPIC:-}}"

MAX_MESSAGES="${MAX_MESSAGES:-50}"
SLEEP_MS="${SLEEP_MS:-0}"
DRY_RUN="${DRY_RUN:-true}"

ALLOWED_PREFIX="${ALLOWED_PREFIX:-community.event.}"

if [[ -z "${DLQ_TOPIC}" ]]; then
  echo "[kafka-replay-dlq] usage: $0 <dlq_topic> [target_topic]" >&2
  echo "[kafka-replay-dlq] env: COMPOSE_FILE, MAX_MESSAGES, SLEEP_MS, DRY_RUN, ALLOWED_PREFIX" >&2
  exit 1
fi

if [[ "${DLQ_TOPIC}" != "${ALLOWED_PREFIX}"*".dlq" ]]; then
  echo "[kafka-replay-dlq] refused: DLQ_TOPIC must start with '${ALLOWED_PREFIX}' and end with '.dlq' (got=${DLQ_TOPIC})" >&2
  exit 1
fi

if [[ -z "${TARGET_TOPIC}" ]]; then
  TARGET_TOPIC="${DLQ_TOPIC%.dlq}"
fi

echo "[kafka-replay-dlq] dlq_topic=${DLQ_TOPIC} target_topic=${TARGET_TOPIC} max=${MAX_MESSAGES} sleep_ms=${SLEEP_MS} dry_run=${DRY_RUN}" >&2

consumer_cmd=(
  docker compose -f "${COMPOSE_FILE}" exec -T kafka
  kafka-console-consumer
  --bootstrap-server kafka:9092
  --topic "${DLQ_TOPIC}"
  --from-beginning
  --max-messages "${MAX_MESSAGES}"
  --property print.key=true
  --property key.separator=$'\t'
)

producer_cmd=(
  docker compose -f "${COMPOSE_FILE}" exec -T kafka
  kafka-console-producer
  --bootstrap-server kafka:9092
  --topic "${TARGET_TOPIC}"
  --property parse.key=true
  --property key.separator=$'\t'
)

if [[ "${DRY_RUN}" == "true" ]]; then
  "${consumer_cmd[@]}" | python3 - <<'PY'
import json
import os
import sys
import time

sleep_ms = int(os.getenv("SLEEP_MS", "0"))
count = 0
bad = 0

for line in sys.stdin:
    line = line.rstrip("\n")
    if not line:
        continue
    count += 1
    key, sep, value = line.partition("\t")
    try:
        obj = json.loads(value)
        payload = obj.get("payload")
        if isinstance(payload, str):
            out_len = len(payload)
        else:
            out_len = len(value)
        # 仅输出最小信息，避免在 dry-run 中泄露内容
        print(f"[dry-run] #{count} key={key} payload_len={out_len}")
    except Exception:
        bad += 1
        print(f"[dry-run] #{count} key={key} payload_len={len(value)} (unparsed)")
    if sleep_ms > 0:
        time.sleep(sleep_ms / 1000.0)

print(f"[dry-run] total={count} unparsed={bad}", file=sys.stderr)
PY
  exit 0
fi

# 非 dry-run：将 DLQ 记录转为 "key<TAB>payload" 并回推到目标 topic
"${consumer_cmd[@]}" | python3 - <<'PY' | "${producer_cmd[@]}"
import json
import os
import sys
import time

sleep_ms = int(os.getenv("SLEEP_MS", "0"))

for line in sys.stdin:
    line = line.rstrip("\n")
    if not line:
        continue
    key, sep, value = line.partition("\t")
    out_key = key if key != "null" else ""
    out_value = value

    try:
        obj = json.loads(value)
        if isinstance(obj, dict):
            # 优先使用 DLQ record 内的 key/payload
            if isinstance(obj.get("key"), str):
                out_key = obj.get("key") or ""
            if isinstance(obj.get("payload"), str):
                out_value = obj.get("payload") or ""
    except Exception:
        # 无法解析则按原样回推（降级）
        pass

    sys.stdout.write(out_key + "\t" + out_value + "\n")
    sys.stdout.flush()
    if sleep_ms > 0:
        time.sleep(sleep_ms / 1000.0)
PY

echo "[kafka-replay-dlq] done." >&2

