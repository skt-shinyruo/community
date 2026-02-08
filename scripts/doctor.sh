#!/usr/bin/env bash
set -euo pipefail

# 配置自检（doctor）
#
# 目标：
# - 快速发现“关键配置缺失/过短/误配”，降低启动后排障成本
# - 不输出任何敏感值（只输出是否存在、长度、建议）
#
# 用法：
#   bash scripts/doctor.sh
#
# 可选：
#   ENV_FILE="deploy/.env" bash scripts/doctor.sh

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

ENV_FILE="${ENV_FILE:-}"
if [[ -z "${ENV_FILE}" ]]; then
  if [[ -f "deploy/.env" ]]; then
    ENV_FILE="deploy/.env"
  else
    ENV_FILE="deploy/.env.example"
  fi
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[doctor] ENV_FILE not found: ${ENV_FILE}"
  exit 2
fi

read_var() {
  local name="$1"
  local v=""

  # 1) 优先使用当前 shell 环境变量
  v="${!name-}"
  if [[ -n "${v}" ]]; then
    echo "${v}"
    return
  fi

  # 2) 其次从 env 文件读取（仅解析 KEY=VALUE，不执行任何内容）
  v="$(grep -E "^${name}=" "${ENV_FILE}" | tail -n 1 | sed -E 's/^[^=]*=//')"
  # 去掉首尾空白与可选引号（不支持复杂转义，足够用于本项目 .env 约定）
  v="$(echo "${v}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  if [[ "${v}" == \"*\" && "${v}" == *\" ]]; then
    v="${v:1:${#v}-2}"
  fi
  if [[ "${v}" == \'*\' && "${v}" == *\' ]]; then
    v="${v:1:${#v}-2}"
  fi
  echo "${v}"
}

is_true() {
  local v="$1"
  [[ "${v}" == "1" || "${v}" == "true" || "${v}" == "TRUE" || "${v}" == "True" ]]
}

len() {
  local v="$1"
  # 不输出内容，仅输出长度（按字节/字符近似）
  echo -n "${v}" | wc -c | tr -d ' '
}

errors=0
warns=0

echo "[doctor] env file: ${ENV_FILE}"

profiles="$(read_var "SPRING_PROFILES_ACTIVE")"
if [[ -z "${profiles}" ]]; then
  profiles="dev"
fi
echo "[doctor] SPRING_PROFILES_ACTIVE=${profiles}"

echo ""
echo "[doctor] 1) JWT/HMAC secret"

gateway_secret="$(read_var "GATEWAY_JWT_HMAC_SECRET")"
jwt_secret="$(read_var "JWT_HMAC_SECRET")"

chosen_secret="${gateway_secret:-${jwt_secret}}"
chosen_from="GATEWAY_JWT_HMAC_SECRET"
if [[ -z "${gateway_secret}" ]]; then
  chosen_from="JWT_HMAC_SECRET"
fi

if [[ -z "${chosen_secret}" ]]; then
  echo "[ERR] JWT secret missing: set GATEWAY_JWT_HMAC_SECRET or JWT_HMAC_SECRET (>= 32 bytes)"
  errors=$((errors + 1))
else
  secret_len="$(len "${chosen_secret}")"
  if [[ "${secret_len}" -lt 32 ]]; then
    echo "[ERR] JWT secret too short (len=${secret_len}): ${chosen_from} should be >= 32 bytes"
    errors=$((errors + 1))
  else
    echo "[OK] JWT secret configured via ${chosen_from} (len=${secret_len})"
  fi
fi

echo ""
echo "[doctor] 2) Internal endpoints (/internal/**)"

echo "[INFO] 当前实现不再使用 X-Internal-Token / X-Ops-Token。"
echo "[INFO] 请确保：gateway 显式拒绝 /internal/**，且下游服务端口不对外暴露（网络隔离）。"

warn_if_present() {
  local name="$1"
  local v
  v="$(read_var "${name}")"
  if [[ -n "${v}" ]]; then
    echo "[WARN] ${name} is set but no longer used (len=$(len "${v}"))"
    warns=$((warns + 1))
  else
    echo "[OK] ${name} not set"
  fi
}

warn_if_present "USER_INTERNAL_TOKEN"
warn_if_present "CONTENT_INTERNAL_TOKEN"
warn_if_present "SOCIAL_INTERNAL_TOKEN"
warn_if_present "SEARCH_INTERNAL_TOKEN"
warn_if_present "ANALYTICS_INTERNAL_TOKEN"
warn_if_present "USER_OPS_INTERNAL_TOKEN"
warn_if_present "USER_OPS_INTERNAL_TOKEN_PREVIOUS"
warn_if_present "OPS_CONTENT_TOKEN"
warn_if_present "OPS_CONTENT_TOKEN_PREVIOUS"
warn_if_present "OPS_SOCIAL_TOKEN"
warn_if_present "OPS_SOCIAL_TOKEN_PREVIOUS"
warn_if_present "OPS_USERS_TOKEN"
warn_if_present "OPS_USERS_TOKEN_PREVIOUS"
warn_if_present "OPS_SEARCH_TOKEN"
warn_if_present "OPS_SEARCH_TOKEN_PREVIOUS"
warn_if_present "OPS_OUTBOX_REPLAY_ENABLED"
warn_if_present "OPS_OUTBOX_REPLAY_ALLOWLIST"
warn_if_present "OPS_SEARCH_REINDEX_ENABLED"
warn_if_present "OPS_SEARCH_REINDEX_ALLOWLIST"
warn_if_present "OPS_RATE_WINDOW_SECONDS"
warn_if_present "OPS_RATE_MAX"
warn_if_present "OPS_LOCK_TTL_SECONDS"

echo ""
echo "[doctor] 3) Idempotency TTL (optional overrides)"
processing_ttl="$(read_var "HTTP_IDEMPOTENCY_PROCESSING_TTL")"
success_ttl="$(read_var "HTTP_IDEMPOTENCY_SUCCESS_TTL")"
if [[ -n "${processing_ttl}" ]]; then
  echo "[OK] HTTP_IDEMPOTENCY_PROCESSING_TTL=${processing_ttl}"
else
  echo "[OK] HTTP_IDEMPOTENCY_PROCESSING_TTL not set (default 30s)"
fi
if [[ -n "${success_ttl}" ]]; then
  echo "[OK] HTTP_IDEMPOTENCY_SUCCESS_TTL=${success_ttl}"
else
  echo "[OK] HTTP_IDEMPOTENCY_SUCCESS_TTL not set (default 24h)"
fi

echo ""
echo "[doctor] 4) Schema drift (manual)"
echo "[INFO] 若你复用/升级已有 MySQL 数据卷，建议在低峰窗口执行："
echo "       mysql ... < scripts/mysql-migrate-ops-harden-schema.sql"

check_schema_snippet() {
  local file="$1"
  local needle="$2"
  local hint="$3"
  if grep -Fq "${needle}" "${file}"; then
    echo "[OK] ${hint}"
  else
    echo "[ERR] ${hint} (missing snippet: ${file})"
    errors=$((errors + 1))
  fi
}

check_schema_snippet "deploy/mysql-init/020_schema_content.sql" "create index idx_outbox_status_next on outbox_event(status, next_retry_at, id)" "content outbox 索引声明存在（status,next_retry_at,id）"
check_schema_snippet "deploy/mysql-init/030_schema_message.sql" "create index idx_consumed_event_at on consumed_event(consumed_at, id)" "message consumed_event 清理索引声明存在（consumed_at,id）"
check_schema_snippet "deploy/mysql-init/040_schema_search.sql" "create index idx_search_consumed_at on search_consumed_event(consumed_at, id)" "search consumed_event 清理索引声明存在（consumed_at,id）"

echo ""
echo "[doctor] 5) prod profile sanity"
if [[ "${profiles}" == *"prod"* ]]; then
  nacos_addr="$(read_var "NACOS_SERVER_ADDR")"
  if [[ -z "${nacos_addr}" ]]; then
    echo "[WARN] prod profile active but NACOS_SERVER_ADDR missing (prod 下 config.import 可能 fail-fast)"
    warns=$((warns + 1))
  else
    echo "[OK] NACOS_SERVER_ADDR present"
  fi
else
  echo "[OK] prod profile not active (dev/local)"
fi

echo ""
if [[ "${errors}" -gt 0 ]]; then
  echo "[doctor] FAILED: errors=${errors}, warns=${warns}"
  exit 1
fi
echo "[doctor] OK: errors=0, warns=${warns}"
