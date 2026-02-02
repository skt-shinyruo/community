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
echo "[doctor] 2) Internal tokens (/internal/**)"

check_token() {
  local name="$1"
  local v
  v="$(read_var "${name}")"
  if [[ -z "${v}" ]]; then
    echo "[ERR] missing ${name} (internal calls may fail-closed)"
    errors=$((errors + 1))
    return
  fi
  echo "[OK] ${name} present (len=$(len "${v}"))"
}

check_token "USER_INTERNAL_TOKEN"
check_token "CONTENT_INTERNAL_TOKEN"
check_token "SOCIAL_INTERNAL_TOKEN"
check_token "SEARCH_INTERNAL_TOKEN"
check_token "ANALYTICS_INTERNAL_TOKEN"

echo ""
echo "[doctor] 3) User ops internal token (/internal/users/*/password|moderation)"
user_ops_token="$(read_var "USER_OPS_INTERNAL_TOKEN")"
if [[ -z "${user_ops_token}" ]]; then
  echo "[WARN] USER_OPS_INTERNAL_TOKEN missing (only required for user-service 高权限 internal 写入口)"
  warns=$((warns + 1))
else
  echo "[OK] USER_OPS_INTERNAL_TOKEN present (len=$(len "${user_ops_token}"))"
fi

echo ""
echo "[doctor] 4) OPS guard (break-glass)"

ops_outbox_enabled="$(read_var "OPS_OUTBOX_REPLAY_ENABLED")"
ops_search_enabled="$(read_var "OPS_SEARCH_REINDEX_ENABLED")"

check_ops_guard() {
  local op_name="$1"
  local enabled="$2"
  local token_var="$3"
  local allowlist_var="$4"

  if ! is_true "${enabled}"; then
    echo "[OK] ${op_name} disabled"
    return
  fi

  local t a
  t="$(read_var "${token_var}")"
  a="$(read_var "${allowlist_var}")"
  if [[ -z "${t}" ]]; then
    echo "[ERR] ${op_name} enabled but ${token_var} missing"
    errors=$((errors + 1))
  else
    echo "[OK] ${op_name} token present (${token_var}, len=$(len "${t}"))"
  fi
  if [[ -z "${a}" ]]; then
    echo "[ERR] ${op_name} enabled but ${allowlist_var} missing"
    errors=$((errors + 1))
  else
    echo "[OK] ${op_name} allowlist present (${allowlist_var})"
  fi
}

if is_true "${ops_outbox_enabled}"; then
  allowlist="$(read_var "OPS_OUTBOX_REPLAY_ALLOWLIST")"
  if [[ -z "${allowlist}" ]]; then
    echo "[ERR] OPS_OUTBOX_REPLAY enabled but OPS_OUTBOX_REPLAY_ALLOWLIST missing"
    errors=$((errors + 1))
  else
    echo "[OK] OPS_OUTBOX_REPLAY allowlist present (OPS_OUTBOX_REPLAY_ALLOWLIST)"
  fi

  ops_content_token="$(read_var "OPS_CONTENT_TOKEN")"
  ops_social_token="$(read_var "OPS_SOCIAL_TOKEN")"
  if [[ -z "${ops_content_token}" && -z "${ops_social_token}" ]]; then
    echo "[ERR] OPS_OUTBOX_REPLAY enabled but both OPS_CONTENT_TOKEN / OPS_SOCIAL_TOKEN are missing"
    errors=$((errors + 1))
  else
    if [[ -n "${ops_content_token}" ]]; then
      echo "[OK] OPS_OUTBOX_REPLAY token present for content (OPS_CONTENT_TOKEN, len=$(len "${ops_content_token}"))"
    else
      echo "[WARN] OPS_OUTBOX_REPLAY enabled but OPS_CONTENT_TOKEN missing (only required when calling /internal/content/**)"
      warns=$((warns + 1))
    fi
    if [[ -n "${ops_social_token}" ]]; then
      echo "[OK] OPS_OUTBOX_REPLAY token present for social (OPS_SOCIAL_TOKEN, len=$(len "${ops_social_token}"))"
    else
      echo "[WARN] OPS_OUTBOX_REPLAY enabled but OPS_SOCIAL_TOKEN missing (only required when calling /internal/social/**)"
      warns=$((warns + 1))
    fi
  fi
else
  echo "[OK] OPS_OUTBOX_REPLAY disabled"
fi

check_ops_guard "OPS_SEARCH_REINDEX" "${ops_search_enabled}" "OPS_SEARCH_TOKEN" "OPS_SEARCH_REINDEX_ALLOWLIST"

echo ""
echo "[doctor] 5) Idempotency TTL (optional overrides)"
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
echo "[doctor] 6) prod profile sanity"
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
